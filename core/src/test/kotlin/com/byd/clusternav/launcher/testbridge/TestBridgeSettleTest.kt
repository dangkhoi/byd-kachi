package com.byd.clusternav.launcher.testbridge

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ CẦU KIỂM THỬ · KHOÁ LUẬT CHỜ CỦA `say` ══════════════════════════════════════════════════════════════════
 *
 * Bài canh dựng từ đúng bốn ca ĐÃ ĐO trong `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 **L4** — nơi
 * hằng `GRACE_MS = 700` làm hai nhánh chạy nền trả về `replies: []`:
 *  • gói lệnh `đóng hết kính` / `rời xe` — `MacroRunner` ngủ giữa các bước (ms=703 mà chưa có dòng nào);
 *  • dẫn đường cần toạ độ — chỉ kịp dòng TẠM *"… đang tra điểm đến…"*, câu CUỐI về sau vài giây.
 * Và hai ca phải **không** được chậm đi: lệnh thường (trả lời ngay) và câu ghép hai vế.
 */
class TestBridgeSettleTest {

    private fun done(
        elapsed: Long,
        sinceChange: Long,
        answered: Int,
        expected: Int,
        interim: Boolean = false,
    ) = TestBridgeSettle.done(elapsed, sinceChange, answered, expected, interim)

    @Test
    fun `lenh thuong chot ngay sau khoang lang, khong cho het tran`() {
        // Một ý định, một dòng trả lời về sau 40 ms ⇒ chốt ở ~340 ms, KHÔNG phải 700 ms như bản cũ.
        assertFalse(done(elapsed = 100, sinceChange = 60, answered = 1, expected = 1))
        assertTrue(done(elapsed = 340, sinceChange = TestBridgeSettle.QUIET_MS, answered = 1, expected = 1))
    }

    @Test
    fun `chua du so dong thi khong chot`() {
        // Câu ghép hai vế: mới có một dòng ⇒ còn vế nữa đang chạy, dù đã lặng lâu hơn QUIET_MS.
        assertFalse(done(elapsed = 2_000, sinceChange = 1_500, answered = 1, expected = 2))
        assertTrue(done(elapsed = 2_000, sinceChange = 1_500, answered = 2, expected = 2))
    }

    @Test
    fun `goi lenh chay nen vuot qua nhip 700ms cu`() {
        // [ĐO] `đóng hết kính` ms=703 mà `replies: []` — luật mới phải còn chờ ở mốc đó.
        assertFalse(done(elapsed = 703, sinceChange = 703, answered = 0, expected = 1))
        // Dòng về ở giây thứ 2 ⇒ chốt sau khoảng lặng, vẫn dưới trần.
        assertTrue(done(elapsed = 2_300, sinceChange = 300, answered = 1, expected = 1))
    }

    @Test
    fun `dong TAM giu luot cho toi khi cau cuoi ve`() {
        // Dòng "… đang tra điểm đến…" ⇒ còn việc chạy: không chốt dù đã đủ số dòng và đã lặng.
        assertFalse(done(elapsed = 4_000, sinceChange = 3_000, answered = 1, expected = 1, interim = true))
        // Sau khi câu CUỐI thay chỗ (không còn dấu `…`) thì chốt như thường.
        assertTrue(done(elapsed = 4_500, sinceChange = 400, answered = 2, expected = 1))
    }

    @Test
    fun `tran chan moi ca, va tran cua dong TAM van duoi tran luot 20s`() {
        assertTrue(done(elapsed = TestBridgeSettle.MAX_MS, sinceChange = 0, answered = 0, expected = 1))
        assertTrue(
            done(elapsed = TestBridgeSettle.MAX_PENDING_MS, sinceChange = 0, answered = 1, expected = 1, interim = true),
        )
        assertTrue(
            TestBridgeSettle.MAX_PENDING_MS < 20_000L,
            "trần chờ phải thấp hơn trần lượt (KachiTestBridge.CAP_MS) ⇒ hết giờ là LỜI ĐÁP, không phải timeout",
        )
    }

    @Test
    fun `khong co y dinh nao thi khong treo lai`() {
        assertTrue(done(elapsed = 300, sinceChange = 300, answered = 0, expected = 0))
    }

    @Test
    fun `nhan dung dong TAM`() {
        assertTrue(TestBridgeSettle.interim("Dẫn đường tới Bitexco — đang tra điểm đến…"))
        assertTrue(TestBridgeSettle.interim("đang tra điểm đến… "))
        assertFalse(TestBridgeSettle.interim("✓ Mở ứng dụng YouTube"))
        assertFalse(TestBridgeSettle.interim(""))
    }
}
