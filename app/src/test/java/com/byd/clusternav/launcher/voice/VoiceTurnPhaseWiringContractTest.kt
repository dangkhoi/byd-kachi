package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * B1 (1.70) — máy trạng thái [VoiceTurnPhase] phải được NỐI vào phiên, không chỉ tồn tại (CLAUDE.md §8:
 * compile xanh ≠ đã chạy). Bài này quét nguồn để mỗi mốc vòng đời thật sự ghi pha qua `go(...)`.
 *
 * Luật thuần của máy đã khoá ở `VoiceTurnMachineTest`; bài này khoá phần DÂY NỐI ở `:app`.
 */
class VoiceTurnPhaseWiringContractTest {
    private fun code(rel: String) = SourceRoots.codeOf(rel)
    private val session by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt") }
    private val turns by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt") }

    @Test
    fun `phien co dung MOT AtomicReference pha, chuyen qua may core`() {
        assertTrue(session.contains("AtomicReference(VoiceTurnPhase.IDLE)"), "phải giữ pha bằng một AtomicReference")
        assertTrue(turns.contains("VoiceTurnMachine.next("), "chuyển pha phải đi qua máy `:core`, không tự set bừa")
        assertTrue(turns.contains("fun VoiceSession.go(to: VoiceTurnPhase)"), "phải có cổng chuyển pha duy nhất `go`")
    }

    @Test
    fun `cac moc vong doi deu ghi pha`() {
        // start → LISTENING · execute → DECODING+EXECUTING · close → CLOSING+IDLE
        assertTrue(session.contains("go(VoiceTurnPhase.LISTENING)"), "start phải chuyển sang LISTENING")
        assertTrue(session.contains("go(VoiceTurnPhase.DECODING)"), "execute phải qua DECODING")
        assertTrue(session.contains("go(VoiceTurnPhase.EXECUTING)"), "execute phải sang EXECUTING")
        assertTrue(session.contains("go(VoiceTurnPhase.CLOSING)"), "close phải chuyển sang CLOSING")
        assertTrue(session.contains("go(VoiceTurnPhase.IDLE)"), "close phải kết ở IDLE")
    }

    @Test
    fun `ba pha noi va xac nhan deu ghi pha`() {
        assertTrue(turns.contains("go(VoiceTurnPhase.CLARIFYING)"), "hỏi-lại phải ghi CLARIFYING")
        assertTrue(turns.contains("go(VoiceTurnPhase.FOLLOW_UP)"), "hội-thoại phải ghi FOLLOW_UP")
        assertTrue(turns.contains("go(VoiceTurnPhase.CONFIRMING)"), "xác-nhận phải ghi CONFIRMING")
        // Ba pha nối đều phải chuyển VỀ LISTENING trước khi mở mic (bất biến chống-loop OQ5).
        assertTrue(turns.contains("go(VoiceTurnPhase.LISTENING)"), "lượt nối phải về LISTENING trước khi mở mic")
    }
}
