package com.byd.clusternav.modules.navaccess

import com.byd.clusternav.modules.navaccess.TatMayCastHoldPlan.Verdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.96 R11 · TAT-MAY-CAST-HOLD — khoá lỗi [ĐO log xe 07/10]: tiến trình bật lúc màn tắt chạy trọn lượt tự chiếu (~22 s) rồi
 * mới bị lượt chữa phím force-stop (`keys-defer … OP_IN_FLIGHT waited=17969/18224`), tiến trình con phải gỡ cụm và mở lại.
 * Số trong bài là mốc thật của log (ms kể từ lúc tiến trình bật).
 */
class TatMayCastHoldPlanTest {

    private val armed = 1_000_000L // elapsedRealtime lúc tiến trình bật (màn tắt)

    @Test
    fun `tien trinh bat luc man SANG khong bao gio bi giu`() {
        assertEquals(Verdict.GO_NOT_ARMED, TatMayCastHoldPlan.verdict(-1L, verdictPending = true, escalatedAt = -1L, now = armed))
        assertEquals(0L, TatMayCastHoldPlan.holdMs(-1L, true, -1L, armed))
    }

    @Test
    fun `lop 1 chua ket luan thi giu, hoi lai theo nhip`() {
        val now = armed + 300L
        assertEquals(Verdict.HOLD_PENDING, TatMayCastHoldPlan.verdict(armed, true, -1L, now))
        assertEquals(TatMayCastHoldPlan.POLL_MS, TatMayCastHoldPlan.holdMs(armed, true, -1L, now))
    }

    @Test
    fun `lop 1 ket luan khong ket thi tha ngay`() {
        assertEquals(Verdict.GO_RELEASED, TatMayCastHoldPlan.verdict(armed, false, -1L, armed + 1_400L))
        assertEquals(0L, TatMayCastHoldPlan.holdMs(armed, false, -1L, armed + 1_400L))
    }

    /** [ĐO log 06/10] ESCALATE t=5808 → RESTARTING t=6475: mốc leo ghi trước lệnh tách rời ⇒ tiến trình sắp chết ⇒ không mở chiếu. */
    @Test
    fun `lop 1 da ban force-stop thi giu tiep du co da nha`() {
        val escalated = armed + 6_475L
        assertEquals(Verdict.HOLD_DYING, TatMayCastHoldPlan.verdict(armed, false, escalated, armed + 6_600L))
        assertTrue(TatMayCastHoldPlan.holdMs(armed, false, escalated, armed + 6_600L) > 0L)
    }

    @Test
    fun `moc leo cua tien trinh TRUOC khong tinh`() {
        assertEquals(Verdict.GO_RELEASED, TatMayCastHoldPlan.verdict(armed, false, armed - 30_000L, armed + 2_000L))
    }

    /** `elapsedRealtime` về 0 khi khởi động lại máy ⇒ mốc leo cũ có thể LỚN hơn now — không phải "sắp chết". */
    @Test
    fun `moc leo cua lan khoi dong may truoc khong tinh`() {
        assertEquals(Verdict.GO_RELEASED, TatMayCastHoldPlan.verdict(armed, false, armed + 2_300_000L, armed + 2_000L))
    }

    @Test
    fun `tran cung - qua 12 s thi mo chieu nhu cu du lop 1 chua xong`() {
        val now = armed + TatMayCastHoldPlan.CAP_MS
        assertEquals(Verdict.GO_CAP, TatMayCastHoldPlan.verdict(armed, true, -1L, now))
        assertEquals(Verdict.GO_CAP, TatMayCastHoldPlan.verdict(armed, false, armed + 6_000L, now + 1L))
        assertEquals(0L, TatMayCastHoldPlan.holdMs(armed, true, -1L, now))
    }

    @Test
    fun `nhip cuoi khong vuot tran`() {
        val now = armed + TatMayCastHoldPlan.CAP_MS - 40L
        assertEquals(40L, TatMayCastHoldPlan.holdMs(armed, true, -1L, now))
    }

    /** Trần phải phủ đường leo thật (≈ 10,5 s, KDoc lớp) nhưng không dài hơn lượt chờ cũ (17,9–18,2 s) — không thì vô nghĩa. */
    @Test
    fun `tran nam giua duong leo that va luot cho cu`() {
        assertTrue(TatMayCastHoldPlan.CAP_MS in 10_500L..17_969L)
    }
}
