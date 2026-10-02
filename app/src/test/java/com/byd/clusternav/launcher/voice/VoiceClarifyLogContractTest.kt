package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ Nhật ký lượt nói của lượt **HỎI LẠI** / **BỎ CUỘC** không được câm ═══════════════════════════════════════════
 *
 * [ĐO máy ảo 02/10 — E2E của VOICE-WAKE-SLOTCOUNT, mục `20261002-172417-715` · `20261002-172621-101`] logcat có
 * *"hỏi lại (lượt 1)"* mà tệp JSON của lượt ấy có `decision` rỗng, `replies` rỗng. Gốc [ĐO nguồn]: `VoiceSession.execute`
 * thoát ở nhánh hỏi lại (và ở nhánh bỏ cuộc `clarifyGaveUp`) TRƯỚC `settle()` — chỗ DUY NHẤT gọi `logDone`; lượt trả lời
 * (nếu có chữ) tự mở mốc mới ở `logHeard` ⇒ mốc cũ không bao giờ có nửa sau.
 *
 * Bài này canh ba dây (quét nguồn, cắt thân bằng [SourceRoots.body]):
 *  1. nhánh hỏi lại ghi nửa sau (câu hỏi) TRƯỚC khi mở lượt nghe — `askAgain` mở mic trên luồng nền, và lượt nghe ấy
 *     đặt mốc MỚI; ghi sau nó là ghi nhầm mục;
 *  2. nhánh bỏ cuộc ghi đúng câu bỏ cuộc;
 *  3. cờ `clarify` của hai lượt ấy là `true` tường minh (nhánh bỏ cuộc đã đặt `clarifyRound = 0` trước khi ghi).
 */
class VoiceClarifyLogContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    private val session by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt") }
    private val turns by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt") }
    private val log by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionLog.kt") }

    @Test
    fun `nhanh hoi lai ghi nhat ky TRUOC khi mo luot nghe`() {
        val exec = SourceRoots.body(session, "internal fun execute(")
        val ask = exec.indexOf("clarifyAsk(intents)?.let { ask ->")
        assertTrue(ask >= 0, "mốc nhánh hỏi lại đổi — xem lại bài này")
        val logged = exec.indexOf("logAsked(intents, ask.question)", ask)
        val again = exec.indexOf("askAgain(ask, my)", ask)
        assertTrue(logged > ask, "nhánh hỏi lại phải ghi nửa sau của lượt (câu hỏi) — không thì mục nhật ký câm")
        assertTrue(logged < again, "ghi TRƯỚC askAgain: lượt nghe nối đặt mốc MỚI trên luồng nền, ghi sau là ghi nhầm mục")
    }

    @Test
    fun `nhanh bo cuoc ghi dung cau bo cuoc`() {
        val g = SourceRoots.body(turns, "internal fun VoiceSession.clarifyGaveUp(")
        val line = g.indexOf("val line = VoiceClarify.giveUp()")
        val logged = g.indexOf("logAsked(intents, line)")
        assertTrue(line >= 0 && logged > line, "nhánh bỏ cuộc phải ghi đúng câu bỏ cuộc vào nhật ký")
        assertTrue(logged < g.indexOf("return true"), "ghi trước khi thoát")
    }

    @Test
    fun `luot hoi lai va bo cuoc mang co clarify true, logDone van la mot duong ghi`() {
        val asked = SourceRoots.body(log, "internal fun VoiceSession.logAsked(intents: List<VoiceIntent>, line: String)")
        assertTrue(asked.contains("logDone(intents, listOf(line), clarify = true)"),
            "đi CHÍNH logDone (một đường ghi, mốc vẫn bị xoá), cờ clarify tường minh")
        val done = SourceRoots.body(log, "internal fun VoiceSession.logDone(")
        assertTrue(done.contains("clarify = clarify,"), "cờ clarify phải lấy từ tham số, không đọc lại clarifyRound")
        assertTrue(done.contains("utteranceStamp = null"), "mốc vẫn bị xoá sau khi dùng (lượt trả lời là mục riêng)")
    }
}
