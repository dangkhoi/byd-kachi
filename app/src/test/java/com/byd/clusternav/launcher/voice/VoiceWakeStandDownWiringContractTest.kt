package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ BG-20 + [P2#6] + loadavg (2026-09-25 · wake) — bài canh cho ba lỗi có số đo ═══════════════════════════════
 *
 *  • BG-20 ([SUY] inventory `perf-inventory-2026-09-25.md:46`): `ACTION_LISTEN_NOW` khi "Hey Kachi" TẮT mở phiên
 *    trong `:wake` (recognizer 74 MB) rồi **không** `stopSelf` ⇒ FGS + mô hình treo tới khi mở lại màn Kachi.
 *  • [P2#6]: FGS duy nhất không có `startForegroundOnce`; `sync()` gọi `startForegroundService` trần.
 *  • loadavg ([ĐO máy ảo 2.65] `avc: denied` 1 dòng/giây): vòng nghe đọc `/proc/loadavg` mỗi giây, SELinux chặn,
 *    `getOrDefault(0.0)` ⇒ guard mù.
 *
 * Phần quyết định thuần (`VoiceWakeStandDown.decide`) đã về `core/.../VoiceWakeStandDownTest` (2026-09-26, CLOSE-3).
 */
class VoiceWakeStandDownWiringContractTest {

    // ── Dây trong VoiceWakeService (contract đọc source) ────────────────────────────────────────────────────

    private val service by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeService.kt") }
    private val listener by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeListener.kt") }
    private val engine by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceRecognizer.kt") }

    @Test
    fun `LISTEN_NOW khi wake OFF phai len lich dung xuong, va nhanh dung xuong phai nha recognizer + stopSelf`() {
        val cmd = SourceRoots.body(service, "override fun onStartCommand(")
        val listenNow = cmd.indexOf("ACTION_LISTEN_NOW")
        val sched = cmd.indexOf("scheduleStandDown()")
        assertTrue(listenNow >= 0 && sched > listenNow, "nhánh LISTEN_NOW phải gọi scheduleStandDown() (BG-20)")
        val task = SourceRoots.body(service, "private val standDownTask = object : Runnable {")
        assertTrue(task.contains("VoiceWakeStandDown.decide("), "quyết định phải đi qua hàm thuần đã test")
        assertTrue(task.contains("VoiceEngine.release()"), "đứng xuống mà không nhả recognizer là để 74 MB treo")
        assertTrue(task.contains("stopSelf(lastStartId)"), "đứng xuống phải stopSelf(id của lượt start gần nhất) — start tới sau mốc quyết định không bị stop nhầm")
        // Wake BẬT ⇒ lượt chờ đứng xuống còn treo phải bị bỏ trước khi đi vào vòng đời thường.
        assertTrue(cmd.contains("main.removeCallbacks(standDownTask)"), "wake ON phải huỷ lượt chờ đứng xuống")
    }

    /**
     * SOÁT 2.68 · Pass 2 · P2 — nhánh đứng xuống gọi `VoiceSession.stop()`, và `stop()` là **một chiều** (nhả hẳn
     * TTS `speaker.shutdown()` + từ Pass 1 khoá vĩnh viễn mọi `start()` bằng cờ `stopped`). Một `ACTION_LISTEN_NOW`
     * tới **sau** `stopSelf(lastStartId)` mà **trước** `onDestroy` được giao cho CHÍNH instance service này và huỷ
     * lượt stop ⇒ nếu phiên còn là `lazy` một-lần thì `start()` trả về ngay, service vẫn `VoiceEntry.ack` ⇒ tiến
     * trình chính không lùi in-process ⇒ **gọi mà không ra gì, im lặng**. Bài này khoá: (a) stand-down BỎ tham chiếu
     * phiên sau khi stop; (b) đường lấy phiên tự **dựng lại** khi tham chiếu rỗng (không `lazy`).
     */
    @Test
    fun `dung xuong bo tham chieu phien va duong lay phien dung lai duoc - khong lazy mot lan`() {
        val task = SourceRoots.body(service, "private val standDownTask = object : Runnable {")
        val stop = task.indexOf(".stop() }")
        val clear = task.indexOf("session = null")
        assertTrue(stop >= 0, "nhánh đứng xuống vẫn phải stop() phiên kẹt (overlay/loa)")
        assertTrue(clear > stop, "phải BỎ tham chiếu SAU khi stop — `stop()` một chiều, lượt gọi sau phải dựng phiên mới")
        assertFalse(service.contains("lazy { buildSession() }"), "phiên `:wake` không được là lazy một-lần (xác đã stop sống mãi trong instance service)")
        assertTrue(service.contains("session ?: buildSession().also { session = it }"), "đường lấy phiên phải tự dựng lại khi rỗng")
    }

    /**
     * SOÁT 2.68 · Pass 3 · P2 — `onDestroy` là đường THỨ HAI mà phiên `:wake` mất chủ (đường thứ nhất là stand-down):
     * service chết thì không ai gọi `stop()` nữa, mà `VoiceSession` **chỉ** nhả `TextToSpeech` trong `stop()`
     * (`speaker.shutdown()` — KDoc ở đó: TTS chưa shutdown giữ kết nối dịch vụ + tiêu điểm âm thanh sống lâu hơn thứ nó
     * phục vụ). Nên: phiên đã IDLE ⇒ `onDestroy` stop + bỏ tham chiếu; phiên CÒN CHẠY ⇒ KHÔNG cắt (owner: nói nốt),
     * để lượt chờ đứng xuống lo. Và `onDestroy` phải đọc trường `session`, KHÔNG qua getter dựng lại — dựng một phiên
     * mới trong lúc service đang chết là một overlay + một TTS không còn ai đóng.
     */
    @Test
    fun `onDestroy stop phien IDLE va bo tham chieu, khong dung lai phien, khong cat phien dang chay`() {
        val d = SourceRoots.body(service, "override fun onDestroy() {")
        val idle = d.indexOf("if (!sessionActive) {")
        val other = d.indexOf("} else {")
        val stop = d.indexOf("session?.let { s -> runCatching { s.stop() }; session = null }")
        assertTrue(idle >= 0 && other > idle, "onDestroy phải rẽ theo phiên còn chạy hay không")
        assertTrue(stop in (idle + 1) until other, "nhánh phiên IDLE phải stop() + bỏ tham chiếu — TTS chỉ nhả trong stop()")
        assertFalse(d.contains("voiceSession"), "onDestroy không được đi qua getter dựng lại: service đang chết")
        assertTrue(d.substring(other).contains("scheduleStandDown()"), "phiên đang chạy: không cắt, để lượt chờ đứng xuống stop() khi nó xong")
    }

    /**
     * Getter `session ?: buildSession()` KHÔNG có khoá ⇒ hai luồng cùng gọi là **hai** phiên (hai overlay, hai TTS,
     * hai lượt xin mic). An toàn của nó là tính chất *"chỉ luồng CHÍNH chạm"* — bài này khoá đúng tính chất đó.
     */
    @Test
    fun `moi cho dung voiceSession phai nam trong main post - getter dung lai khong co khoa`() {
        val uses = service.lines().filter { it.contains("voiceSession.") }
        assertTrue(uses.size >= 2, "phải còn ít nhất 2 chỗ mở phiên (LISTEN_NOW + fireWake), thấy ${uses.size}")
        uses.forEach { assertTrue(it.contains("main.post {"), "dùng voiceSession ngoài luồng chính: ${it.trim()}") }
    }

    @Test
    fun `P2-6 - FGS di qua startForegroundOnce nhu 7 FGS khac, sync boc startForegroundService`() {
        val cmd = SourceRoots.body(service, "override fun onStartCommand(")
        assertTrue(cmd.contains("if (!startForegroundOnce()) { stopSelf(startId); return START_NOT_STICKY }"))
        assertFalse(cmd.contains("startForeground(NOTIF_ID"), "onStartCommand không được gọi startForeground trần")
        val sync = SourceRoots.body(service, "fun sync(ctx: Context, reloadModel: Boolean = false)")
        val call = sync.indexOf("ctx.startForegroundService(i)")
        val wrap = sync.lastIndexOf("runCatching {", call)
        assertTrue(call >= 0 && wrap >= 0, "sync() phải bọc startForegroundService trong runCatching")
        assertTrue(sync.contains(".onFailure { Log.w("), "bọc mà nuốt im là mất dấu vết")
    }

    @Test
    fun `nha recognizer chi an toan duoi khoa dung-nha - decode giu read, release giu write`() {
        // Không có khoá này, stand-down / gỡ gói có thể `release()` dưới chân một `decode` đang chạy ⇒ SIGSEGV.
        val decode = SourceRoots.body(engine, "private fun decode(pcm: ShortArray, length: Int): String")
        assertTrue(decode.contains("VoiceEngine.withUse(recognizer)"), "decode phải chạy trong withUse (khoá đọc)")
        val release = SourceRoots.body(engine, "fun release() = synchronized(this) {")
        assertTrue(release.contains("useLock.write"), "release phải giữ khoá ghi (chờ decode xong)")
        val withUse = SourceRoots.body(engine, "internal fun withUse(rec: OfflineRecognizer, block: () -> String): String? = useLock.read {")
        assertTrue(withUse.contains("recognizer !== rec"), "bản đã nhả không được chạm native")
    }

    @Test
    fun `preload chi de MOT luong tai mot thoi diem va rut lui khi wake BAT`() {
        val pre = SourceRoots.body(engine, "fun preload(ctx: Context, delayMs: Long = PRELOAD_DELAY_MS)")
        assertTrue(pre.contains("preloading.compareAndSet(false, true)"), "thiếu cờ idempotent")
        assertTrue(pre.contains("finally { preloading.set(false) }"), "cờ phải nhả khi luồng kết")
        assertTrue(pre.contains("VoicePreloadPolicy.shouldPreloadInMain("), "một mô hình cho cả máy: hỏi policy thuần")
    }

    // ── loadavg: không còn đọc mỗi giây + getOrDefault(0.0) ────────────────────────────────────────────────

    @Test
    fun `vong nghe khong duoc doc proc loadavg moi giay voi getOrDefault 0`() {
        assertFalse(listener.contains("readLoad1"), "đường cũ readLoad1 (getOrDefault(0.0) = guard mù) phải biến mất")
        assertFalse(listener.contains(".getOrDefault(0.0)"), "không được nuốt lỗi đọc tải thành 0.0")
        val outer = SourceRoots.body(listener, "private fun runOuter()")
        assertTrue(outer.contains("loadSource.probe()"), "dò nguồn tải MỘT lần ở đầu vòng ngoài")
        val inner = SourceRoots.body(listener, "private fun inner(kws: WakeEngine?): Inner")
        assertTrue(inner.contains("loadSource.read(now)"), "vòng trong đọc tải qua LoadSource")
        assertFalse(inner.contains("/proc/loadavg"), "vòng trong không được đọc thẳng /proc/loadavg")
        val src = SourceRoots.body(listener, "private class LoadSource {")
        assertTrue(src.contains("Process.getElapsedCpuTime()"), "nguồn thay thế = CPU của chính tiến trình (syscall)")
        assertTrue(src.contains("VoiceLoadGuard.forSelfCpu(nproc)"), "guard phải đổi thang theo nguồn")
        assertTrue(src.contains("VoiceSelfCpuMeter"), "đổi ms→lõi bằng lớp thuần đã test")
    }
}
