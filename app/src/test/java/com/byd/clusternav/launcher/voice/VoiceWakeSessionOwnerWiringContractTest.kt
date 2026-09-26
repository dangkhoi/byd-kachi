package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VOICE-WAKE-SESSION-OWNER (2.69) — tiến trình `:wake` chỉ có MỘT phiên, và chủ của nó không phải service ═══════
 *
 * [SUY, review Pass 3 2.68] nhánh "nói nốt" của `onDestroy` + `listenNow` ngay sau ⇒ instance service mới với trường
 * `session` riêng ⇒ hai phiên chồng (chồng tiếng, hai tấm chữ). Máy trạng thái thuần ở `:core`
 * (`VoiceSessionOwnerTest`); bài này canh DÂY: service không giữ phiên, mọi đường đi qua `VoiceWakeSessions` (object),
 * phím-thoại cắt phiên đang bận, "Hey Kachi" thì không.
 */
class VoiceWakeSessionOwnerWiringContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    private val service by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeService.kt") }
    private val sessions by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeSessions.kt") }

    @Test
    fun `service khong giu phien - moi doc-ghi phien di qua VoiceWakeSessions`() {
        assertFalse(Regex("""\bvar session\b""").containsMatchIn(service), "trường phiên trong instance service là nguồn của hai-phiên-chồng")
        assertFalse(service.contains("VoiceSession?"), "service không được giữ tham chiếu phiên (nullable) nào")
        assertTrue(sessions.contains("internal object VoiceWakeSessions"), "chủ sở hữu phải là object — sống theo TIẾN TRÌNH, không theo instance service")
        assertTrue(sessions.contains("VoiceSessionOwner<VoiceSession>("), "quyết định thuần ở :core, đã test off-device")
        assertTrue(service.contains("private val voiceSession: VoiceSession get() = VoiceWakeSessions.acquire { buildSession() }"))
    }

    @Test
    fun `LISTEN_NOW cat phien dang ban (preempt), Hey Kachi thi khong (acquire)`() {
        val cmd = SourceRoots.body(service, "override fun onStartCommand(")
        assertTrue(cmd.contains("main.post { VoiceWakeSessions.preempt { buildSession() }.start() }"), "phím-thoại/nút mic = muốn nói NGAY ⇒ preempt")
        val fire = SourceRoots.body(service, "private fun fireWake()")
        assertTrue(fire.contains("main.post { voiceSession.start() }"), "\"Hey Kachi\" đi acquire (không cắt — có thể là false-accept giữa câu trả lời)")
        assertFalse(fire.contains("preempt"), "fireWake không được cắt phiên đang chạy")
        val preempt = SourceRoots.body(sessions, "fun preempt(build: () -> VoiceSession): VoiceSession {")
        assertTrue(preempt.contains("owner.acquire(preempt = true, build)"))
        assertTrue(SourceRoots.body(sessions, "fun acquire(build: () -> VoiceSession): VoiceSession").contains("preempt = false"))
    }

    /** Pha đo từ CHÍNH phiên (`micOpen()` · `phase`), không cờ RAM của owner (CLAUDE.md §5). */
    @Test
    fun `phan loai pha do tu phien that - mic mo la LISTENING, pha khac IDLE la BUSY`() {
        val classify = sessions.substringAfter("classify = { s ->").substringBefore("stop = ")
        val mic = classify.indexOf("s.micOpen() -> VoiceSessionOwner.Phase.LISTENING")
        val busy = classify.indexOf("s.phase.get() != VoiceTurnPhase.IDLE -> VoiceSessionOwner.Phase.BUSY")
        assertTrue(mic >= 0 && busy > mic, "mic mở phải xét TRƯỚC pha bận — đang nghe thì bấm thêm không cắt")
        assertTrue(classify.contains("else -> VoiceSessionOwner.Phase.IDLE"))
        assertTrue(sessions.contains("stop = { s -> runCatching { s.stop() }"), "cắt phiên = VoiceSession.stop() (một chiều), bọc runCatching")
    }

    @Test
    fun `dung xuong va onDestroy nha phien qua owner - khong con session null`() {
        assertTrue(SourceRoots.body(service, "private val standDownTask = object : Runnable {").contains("VoiceWakeSessions.release()"))
        assertTrue(SourceRoots.body(service, "override fun onDestroy() {").contains("VoiceWakeSessions.release()"))
        assertFalse(service.contains("session = null"), "không còn tự tay bỏ tham chiếu — owner làm việc đó trong release()")
        assertTrue(service.contains("val phase = VoiceWakeSessions.phase()"), "quyết định đứng xuống đọc pha qua owner")
    }

    /**
     * ═══ [SOÁT 2.69 · P2] Hẹn đứng xuống của một instance service ĐÃ CHẾT không được cắt phiên MỚI ═══════════════
     *
     * Ca thật (wake OFF): phím-thoại ⇒ instance A + phiên; mở Kachi ⇒ `sync()` thấy wake OFF ⇒ `stopService` ⇒
     * `A.onDestroy` giữa phiên ⇒ A giữ lượt chờ đứng xuống (mốc `T0`, owner duyệt nhánh "nói nốt"). Phím-thoại lần
     * nữa ⇒ instance B + phiên MỚI, nhưng `main.removeCallbacks(standDownTask)` của B chỉ gỡ Runnable **của B**.
     * Từ 2.69 `VoiceWakeSessions.release()` chạm phiên của cả TIẾN TRÌNH ⇒ hẹn của A, khi `waited` vượt
     * `MAX_WAIT_MS` (3 phút), **cắt đúng phiên người lái đang nói** + nhả recognizer 85 MB. Trước 2.69 nó chỉ chạm
     * phiên của chính A nên bệnh này là do lượt đổi chủ sở hữu sinh ra.
     *
     * Vá: số hiệu phiên ở mức tiến trình ([VoiceWakeSessions.epoch], tăng khi **dựng mới**, không tăng khi dùng
     * lại) + lượt chờ đếm LẠI từ đầu khi số hiệu đổi — đúng nghĩa `MAX_WAIT_MS` = trần cho MỘT phiên về IDLE.
     */
    @Test
    fun `han cho dung xuong dem lai tu dau khi phien doi - hen cu khong cat phien moi`() {
        assertTrue(sessions.contains("@Volatile private var epoch = 0") && sessions.contains("fun epoch(): Int = epoch"),
            "số hiệu phiên phải ở mức TIẾN TRÌNH (object), không ở instance service")
        assertTrue(sessions.contains("if (!a.reused) epoch++"),
            "chỉ phiên MỚI mới tăng số hiệu — dùng lại phiên đang giữ không phải một phiên khác")
        listOf("mark(owner.acquire(preempt = false, build))", "mark(owner.acquire(preempt = true, build))").forEach {
            assertTrue(sessions.contains(it), "mọi đường dựng phiên phải đi qua `mark` — thiếu một đường là số hiệu đứng im")
        }
        val task = SourceRoots.body(service, "private val standDownTask = object : Runnable {")
        val read = task.indexOf("val epoch = VoiceWakeSessions.epoch()")
        val reset = task.indexOf("standDownSince = SystemClock.elapsedRealtime()")
        val waited = task.indexOf("val waited = if (standDownSince == 0L)")
        assertTrue(read in 0 until reset, "phải đọc số hiệu rồi mới đặt lại mốc")
        assertTrue(reset < waited, "mốc phải được đặt lại TRƯỚC khi tính `waited` — không thì lượt này vẫn đo từ mốc cũ")
        assertTrue(task.contains("if (epoch != standDownEpoch) {"), "chỉ đếm lại khi phiên ĐỔI, không đếm lại mỗi nhịp (sẽ không bao giờ đứng xuống)")
        assertTrue(service.contains("private var standDownEpoch = -1"))
    }
}
