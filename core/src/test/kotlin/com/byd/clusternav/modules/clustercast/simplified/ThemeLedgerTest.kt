package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CLUSTER-THEME-SAFE B1a · test C.3 mục 2 (ThemeIntervalTest) + R6 — sổ theme bền.
 *
 * Khoá: (a) hai lần đổi theme cách nhau ≥ 15 s trong CÙNG một lần khởi động [ĐO source `cluster-rect-seal-2026-10-05.md` §4],
 * khác boot thì được; (b) sổ chỉ SUY kiểu khi mục `ok` do CHÍNH tiến trình này ghi ([ĐO 29/09] BYD giết Kachi mỗi lần tắt
 * máy; [ĐO-gv 05/10] nổ máy lại ⇒ cụm về theme gốc); (c) `pending` (tiến trình chết giữa chừng) ⇒ UNKNOWN; (d) đọc chặt.
 */
class ThemeLedgerTest {

    private fun e(op: Int, at: Long, boot: Int, state: ThemeLedger.State = ThemeLedger.State.OK) = ThemeLedger.Entry(op, state, at, boot)
    private fun now(at: Long, boot: Int, start: Long = 0) = ThemeLedger.Now(at, boot, start)

    @Test
    fun `dang op-pending-ok-elapsed-boot, round-trip, doc chat`() {
        val x = e(31, 123_456, 57, ThemeLedger.State.PENDING)
        assertEquals("31;pending;123456;57", ThemeLedger.encode(x))
        assertEquals(x, ThemeLedger.decode("31;pending;123456;57"))
        assertEquals(e(30, 5, -1), ThemeLedger.decode(" 30;ok;5;-1 "))
        for (bad in listOf(null, "", "30;ok;5", "30;done;5;1", "x;ok;5;1", "300;ok;5;1", "30;ok;-5;1", "30;ok;5;1;9")) {
            assertNull(ThemeLedger.decode(bad), "sai dạng phải là sổ trống: $bad")
        }
    }

    @Test
    fun `15 s - cung boot chua du thi chan, du thi cho`() {
        assertEquals(15_000L - 4_000L, ThemeLedger.remainingGapMs(e(30, 10_000, 7), now(14_000, 7)))
        assertNull(ThemeLedger.remainingGapMs(e(30, 10_000, 7), now(25_000, 7)), "đủ 15 s")
        assertNull(ThemeLedger.remainingGapMs(null, now(1, 7)), "sổ trống ⇒ không chặn")
        assertEquals(15_000L, ThemeLedger.remainingGapMs(e(30, 10_000, 7, ThemeLedger.State.PENDING), now(10_000, 7)), "pending cũng tính")
    }

    @Test
    fun `15 s - khac boot (BOOT_COUNT khac, hoac elapsedRealtime lui) thi khong chan`() {
        assertNull(ThemeLedger.remainingGapMs(e(30, 10_000, 7), now(12_000, 8)))
        assertNull(ThemeLedger.remainingGapMs(e(30, 900_000, -1), now(3_000, -1)), "đồng hồ lùi = đã khởi động lại")
        assertEquals(13_000L, ThemeLedger.remainingGapMs(e(30, 10_000, -1), now(12_000, -1)), "không biết boot ⇒ coi CÙNG (bỏ thêm một lượt)")
    }

    @Test
    fun `cung tien trinh - chi khi boot da biet bang nhau va moc trong doi tien trinh`() {
        assertTrue(ThemeLedger.sameProcess(e(30, 150, 2), now(200, 2, start = 100)))
        assertFalse(ThemeLedger.sameProcess(e(30, 50, 2), now(200, 2, start = 100)), "tiến trình trước (lần nổ máy trước)")
        assertFalse(ThemeLedger.sameProcess(e(30, 150, 1), now(200, 2, start = 100)), "boot khác")
        assertFalse(ThemeLedger.sameProcess(e(30, 150, -1), now(200, -1, start = 100)), "boot không biết ⇒ không dám")
        assertFalse(ThemeLedger.sameProcess(e(30, 250, 2), now(200, 2, start = 100)), "mốc ở tương lai = hỏng")
    }

    @Test
    fun `kieu suy tu so - ok cung tien trinh thi theo opcode, con lai UNKNOWN`() {
        val r = ProjectionRecipe.SEAL_DL3
        val n = now(200, 2, start = 100)
        assertEquals(BelievedStyle.CURVED, ThemeLedger.believed(e(30, 150, 2), r, n))
        assertEquals(BelievedStyle.RECT, ThemeLedger.believed(e(31, 150, 2), r, n))
        assertEquals(BelievedStyle.UNKNOWN, ThemeLedger.believed(e(30, 150, 2, ThemeLedger.State.PENDING), r, n), "R6: pending ⇒ UNKNOWN")
        assertEquals(BelievedStyle.UNKNOWN, ThemeLedger.believed(e(30, 50, 2), r, n), "tiến trình trước ⇒ UNKNOWN")
        assertEquals(BelievedStyle.UNKNOWN, ThemeLedger.believed(e(29, 150, 2), r, n), "opcode không phải kiểu của hồ sơ")
        assertEquals(BelievedStyle.UNKNOWN, ThemeLedger.believed(null, r, n))
    }

    /**
     * Review 2.89 Pass 3 · cluster-r2-3 — ĐỔI GHIM có lý do: trên xe có kiểu GỐC biết được (Seal 10.25" `car.type=138` ⇒ Chữ nhật),
     * `31;ok` của tiến trình / lần khởi động TRƯỚC vẫn chứng minh Chữ nhật (opcode gốc còn hiệu lực, hoặc nổ máy đã về gốc [ĐO-gv
     * F3]) — đúng giả định luật 3 của `ClusterStylePlan.wanted`. `30;ok` của tiến trình trước vẫn UNKNOWN (mơ hồ, B1b-OQ6); xe
     * chưa biết kiểu gốc (`SEAL_DL3` trơn) giữ luật cũ ở bài trên.
     */
    @Test
    fun `Pass 3 - opcode GOC ok chung minh kieu goc bat ke tien trinh, opcode khac van can cung tien trinh`() {
        val r = ProjectionRecipe.SEAL_DL3.copy(nativeStyle = CastStyle.RECT)
        val n = now(200, 2, start = 100)
        assertEquals(BelievedStyle.RECT, ThemeLedger.believed(e(31, 50, 2), r, n), "tiến trình trước")
        assertEquals(BelievedStyle.RECT, ThemeLedger.believed(e(31, 50, 1), r, n), "lần khởi động trước")
        assertEquals(BelievedStyle.UNKNOWN, ThemeLedger.believed(e(30, 50, 2), r, n), "30 của tiến trình trước: mơ hồ")
        assertEquals(BelievedStyle.CURVED, ThemeLedger.believed(e(30, 150, 2), r, n), "30 cùng tiến trình: như cũ")
        assertEquals(BelievedStyle.UNKNOWN, ThemeLedger.believed(e(31, 50, 2, ThemeLedger.State.PENDING), r, n), "pending ⇒ UNKNOWN")
        assertEquals(BelievedStyle.UNKNOWN, ThemeLedger.believed(e(31, 50, 2), ProjectionRecipe.SEAL_DL3, n), "kiểu gốc chưa biết")
    }

    @Test
    fun `Now UNKNOWN - khong bao gio la cung tien trinh`() {
        assertFalse(ThemeLedger.sameProcess(e(30, 0, -1), ThemeLedger.Now.UNKNOWN))
        assertFalse(ThemeLedger.sameProcess(e(30, 0, 0), ThemeLedger.Now.UNKNOWN))
    }
}
