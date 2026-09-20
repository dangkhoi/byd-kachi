package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
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

    /**
     * ═══ [ĐO xe 2026-09-20 §5] Máy trạng thái phải CHO PHÉP đúng chuỗi mà `:app` đi ═════════════════════════
     *
     * Bài này là nửa còn lại của `VoiceTurnMachineTest`: bên kia khoá **luật**, bên này khoá rằng luật ấy phủ
     * đúng **những cạnh mà mã sản phẩm thật sự xin**. Gốc lỗi *"phiên thoại biến mất"* là một cạnh có trong sơ
     * đồ + trong chú thích chỗ gọi mà **không** có trong bảng dữ liệu — tức hai bài chỉ-đọc-một-bên đều xanh.
     *
     * Cách đo: lấy pha ghi trong chú thích `// B1: X → Y` ngay tại mỗi chỗ gọi `go(...)` rồi hỏi chính máy `:core`.
     * Chú thích ở đây **là tài liệu chịu lực**, không phải trang trí — nó là thứ duy nhất nói pha xuất phát. (Vì
     * vậy bài này đọc `SourceRoots.text` thô, không `codeOf`: `codeOf` cố ý bỏ chú thích.)
     */
    @Test
    fun `moi canh ma app xin deu hop le o may core`() {
        val raw = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt") + "\n" +
            SourceRoots.text("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt")
        val edges = Regex("""//\s*B1:\s*([A-Z_]+)\s*→\s*([A-Z_]+)""").findAll(raw)
            .map { it.groupValues[1] to it.groupValues[2] }
            .filter { (a, b) -> a != "IDLE" || b != "LISTENING" }   // start() tự `set` IDLE trước, không qua go()
            .toList()
        assertTrue(edges.size >= 5, "chỉ đọc được ${edges.size} chú thích B1 — bài canh đang quét vùng không tồn tại")
        edges.forEach { (from, to) ->
            val f = VoiceTurnPhase.valueOf(from)
            val t = VoiceTurnPhase.valueOf(to)
            assertTrue(VoiceTurnMachine.canGo(f, t), "mã sản phẩm xin $from ⇒ $to mà máy `:core` từ chối")
        }
        // Cạnh cụ thể đã gây lỗi trên xe — nêu đích danh để lần sau đỏ là đọc ra ngay.
        assertTrue(
            VoiceTurnMachine.canGo(VoiceTurnPhase.EXECUTING, VoiceTurnPhase.CLARIFYING),
            "EXECUTING ⇒ CLARIFYING: cổng hỏi-lại chạy SAU go(EXECUTING) trong execute() nên đây là cạnh thật",
        )
    }

    /**
     * ═══ [ĐO xe 2026-09-20 §5] Lượt nghe HỎI-LẠI phải mở ĐÚNG MỘT LẦN ═══════════════════════════════════════
     *
     * Hợp đồng [VoiceSpeaker.speak] là *"luôn gọi onDone, KỂ CẢ khi trả false"*. Máy đọc chưa nối được
     * (`TextToSpeech: not bound to TTS engine` trong nhật ký xe) ⇒ onDone chạy **và** `speak` trả `false`, nên
     * `askAgain` có HAI đường tới lượt nghe. Không có chốt thì lượt thứ hai bị `VoiceSingleFlight` chối mic, trả
     * chuỗi rỗng, rơi vào `endsConversation` và **đóng tấm chữ giữa lượt nghe thứ nhất** — đúng lỗi owner báo.
     *
     * `askAloudThenListen` đã có chốt ấy từ đầu (`listening.compareAndSet`); bài này ép `askAgain` cũng phải có,
     * và ép nó **không** còn gọi `listenAgain` trực tiếp ở hai nhánh nữa.
     */
    @Test
    fun `askAgain mo luot nghe dung MOT lan du may doc hong`() {
        val body = turns.substringAfter("fun VoiceSession.askAgain(").substringBefore("\nprivate fun VoiceSession.listenAgain(")
        assertTrue(body.isNotBlank() && body.length < turns.length, "không cắt được thân askAgain — bài canh mù")
        assertTrue(body.contains("compareAndSet(false, true)"), "phải có chốt một-lượt cho lượt nghe hỏi lại")
        assertTrue(body.contains("!micOpen()"), "mic đang mở thì không được mở lượt thứ hai")
        assertEquals(
            1,
            Regex("""listenAgain\(""").findAll(body).count(),
            "askAgain chỉ được có MỘT chỗ gọi listenAgain (trong chốt); hai nhánh speak/!spoke phải qua cùng cổng",
        )
        // Cả hai đường (onDone của máy đọc · nhánh không đọc được) phải đi qua cổng `open()`.
        assertTrue(body.contains("post { open() }"), "mốc đọc-xong phải đi qua cổng")
        assertTrue(body.contains("if (!spoke) open()"), "không đọc được vẫn PHẢI mở mic qua cổng")
    }
}
