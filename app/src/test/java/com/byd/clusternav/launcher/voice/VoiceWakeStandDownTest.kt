package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceWakeStandDown.Decision
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
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
 */
class VoiceWakeStandDownTest {

    // ── Quyết định thuần đứng xuống ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `wake BAT thi KEEP - vong doi thuong so huu service, du phien dang o pha nao`() {
        VoiceTurnPhase.values().forEach { ph ->
            assertEquals(Decision.KEEP, VoiceWakeStandDown.decide(wakeEnabled = true, sessionPhase = ph, waitedMs = 0))
            assertEquals(Decision.KEEP, VoiceWakeStandDown.decide(wakeEnabled = true, sessionPhase = ph, waitedMs = 999_999))
        }
    }

    @Test
    fun `wake TAT va phien da IDLE thi STAND_DOWN ngay`() {
        assertEquals(Decision.STAND_DOWN, VoiceWakeStandDown.decide(false, VoiceTurnPhase.IDLE, waitedMs = 0))
    }

    @Test
    fun `wake TAT va phien con chay thi WAIT - khong cat giua cau nguoi lai`() {
        listOf(VoiceTurnPhase.LISTENING, VoiceTurnPhase.DECODING, VoiceTurnPhase.EXECUTING).forEach { ph ->
            assertEquals(Decision.WAIT, VoiceWakeStandDown.decide(false, ph, waitedMs = 60_000), ph.name)
        }
    }

    @Test
    fun `phien ket qua tran thi van dung xuong - FGS treo mai khong phai cach che loi`() {
        val max = VoiceWakeStandDown.MAX_WAIT_MS
        assertEquals(Decision.WAIT, VoiceWakeStandDown.decide(false, VoiceTurnPhase.LISTENING, waitedMs = max - 1))
        assertEquals(Decision.STAND_DOWN, VoiceWakeStandDown.decide(false, VoiceTurnPhase.LISTENING, waitedMs = max))
    }

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
