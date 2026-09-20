package com.byd.clusternav.launcher.automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [NavAutomationFired] — sổ ĐÃ-DẪN (spec `kachi-automation.html` R2.4, stage 3).
 *
 * Mỗi ca dưới đây là một cách hỏng **im lặng** nếu codec sai, và tất cả đều dẫn tới cùng một hậu quả nhìn từ ghế
 * lái: app dẫn đường mở lại lần thứ hai trong cùng một khung giờ, hoặc một luật không bao giờ chạy.
 */
class NavAutomationFiredTest {

    @Test
    fun `vong tron encode-decode giu nguyen tung cap`() {
        val fired = mapOf("r1" to "2026-09-20", "r2" to "2026-09-19")
        assertEquals(fired, NavAutomationFired.decode(NavAutomationFired.encode(fired)))
    }

    @Test
    fun `so rong doc ra rong chu khong nem`() {
        assertEquals(NavAutomationFired.EMPTY, NavAutomationFired.decode(null))
        assertEquals(NavAutomationFired.EMPTY, NavAutomationFired.decode(""))
        assertEquals(NavAutomationFired.EMPTY, NavAutomationFired.decode("   \n  "))
        assertEquals("", NavAutomationFired.encode(NavAutomationFired.EMPTY))
    }

    /**
     * Ba dạng dòng hỏng có THẬT (sửa tay tệp prefs · bản cũ ghi khác dạng): thiếu dấu `=`, id rỗng, ngày rỗng.
     * Bỏ đúng dòng đó, **giữ** dòng lành — bỏ cả sổ thì mọi luật tưởng là chưa dẫn ⇒ nổ lại hết.
     */
    @Test
    fun `dong hong bi bo dung mot dong, dong lanh giu nguyen`() {
        val raw = "r1=2026-09-20\nrac-khong-co-dau-bang\n=2026-09-20\nr3=\nr4=2026-09-18"
        assertEquals(mapOf("r1" to "2026-09-20", "r4" to "2026-09-18"), NavAutomationFired.decode(raw))
    }

    /** Trùng id ⇒ giữ dòng ĐẦU (cùng luật [NavAutomationBook.decode]) — chuỗi trên đĩa phải đọc ra một kết quả. */
    @Test
    fun `dong trung id giu dong dau`() {
        assertEquals(
            mapOf("r1" to "2026-09-20"),
            NavAutomationFired.decode("r1=2026-09-20\nr1=2026-09-01"),
        )
    }

    @Test
    fun `khoang trang quanh id va ngay bi cat`() {
        assertEquals(mapOf("r1" to "2026-09-20"), NavAutomationFired.decode("  r1  =  2026-09-20  "))
    }

    @Test
    fun `put dong dau ngay moi va ghi de dau cu cua cung luat`() {
        val once = NavAutomationFired.put(NavAutomationFired.EMPTY, "r1", "2026-09-20")
        assertEquals(mapOf("r1" to "2026-09-20"), once)
        assertEquals(mapOf("r1" to "2026-09-21"), NavAutomationFired.put(once, "r1", "2026-09-21"))
    }

    /**
     * ⚠ Ngày RỖNG phải bị từ chối. Cho qua thì `lastFiredDay == today` thành đúng ở ca
     * [NavSkipReason.NO_DAY_KEY] (`today` rỗng) ⇒ luật bị chặn **vĩnh viễn**, không một lời nào.
     */
    @Test
    fun `put tu choi id rong va ngay rong`() {
        val base = mapOf("r1" to "2026-09-20")
        assertSame(base, NavAutomationFired.put(base, "r2", ""))
        assertSame(base, NavAutomationFired.put(base, "", "2026-09-20"))
        assertSame(base, NavAutomationFired.put(base, "  ", "  "))
    }

    /** [NavAutomationFired.encode] cũng phải lọc cặp rỗng — nếu không nó ghi ra dòng mà `decode` sẽ bỏ. */
    @Test
    fun `encode bo cap rong thay vi ghi ra dong hong`() {
        val out = NavAutomationFired.encode(mapOf("r1" to "2026-09-20", "" to "x", "r2" to ""))
        assertEquals("r1=2026-09-20", out)
    }

    /**
     * ⚠ Ca mà [NavAutomationFired.prune] sinh ra để chặn: xoá `r1` rồi thêm luật mới ⇒
     * [NavAutomationBook.newId] cấp lại `r1`; dấu mồ côi `r1=<hôm nay>` sẽ đóng dấu cho luật MỚI ⇒ luật vừa tạo
     * không chạy hôm nay, im lặng.
     */
    @Test
    fun `prune bo dau cua luat khong con ton tai`() {
        val fired = mapOf("r1" to "2026-09-20", "r2" to "2026-09-20")
        assertEquals(mapOf("r2" to "2026-09-20"), NavAutomationFired.prune(fired, setOf("r2")))
        assertEquals(NavAutomationFired.EMPTY, NavAutomationFired.prune(fired, emptySet()))
        assertEquals(fired, NavAutomationFired.prune(fired, setOf("r1", "r2", "r3")))
    }

    /**
     * Nối hai nửa lại: sổ đã-dẫn phải nuôi được [ScheduledNavPolicy.firstToLaunch] — tức dạng chuỗi đọc ra đúng
     * kiểu `Map<String, String>` mà luật kia nhận, và luật *"1 lần/ngày"* thật sự chặn ở nhịp sau.
     */
    @Test
    fun `so da-dan chan lan thu hai trong cung ngay`() {
        val rule = ScheduledNavRules.of(
            id = "r1", enabled = true, startMin = 7 * 60, endMin = 9 * 60,
            days = ScheduledNavRules.ALL_DAYS, requireGps = false, placeId = "công ty", navApp = "gmaps",
        )!!
        val today = "2026-09-20"
        val before = NavAutomationFired.decode("")
        assertSame(
            rule,
            ScheduledNavPolicy.firstToLaunch(listOf(rule), 8 * 60, ScheduledNavRules.MON, false, before, today),
        )
        val after = NavAutomationFired.decode(
            NavAutomationFired.encode(NavAutomationFired.put(before, rule.id, today)),
        )
        assertTrue(
            ScheduledNavPolicy.firstToLaunch(
                listOf(rule), 8 * 60 + 1, ScheduledNavRules.MON, false, after, today,
            ) == null,
            "đã dẫn hôm nay ⇒ nhịp sau KHÔNG được dẫn lại",
        )
        // Sang NGÀY MỚI thì dấu tự hết hiệu lực — không cần ai xoá lúc nửa đêm (xem KDoc ScheduledNavPolicy).
        assertSame(
            rule,
            ScheduledNavPolicy.firstToLaunch(
                listOf(rule), 8 * 60, ScheduledNavRules.TUE, false, after, "2026-09-21",
            ),
        )
    }
}
