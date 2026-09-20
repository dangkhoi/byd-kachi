package com.byd.clusternav.launcher.automation

import com.byd.clusternav.launcher.SavedPlace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ LUẬT DẪN ĐƯỜNG THEO LỊCH · MÔ HÌNH + MÃ HOÁ ══════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R2.2. Thuần ⇒ chạy off-car.
 *
 * ## Bài này khoá lại BA bài học
 *  1. **Ký tự ngăn trong chữ người dùng** — [ĐO] 2026-09-12 `SlotCodec.SEP`: một `|` lọt vào làm **mất nguyên một
 *     bản ghi** trong im lặng. `placeId` ở đây đến từ tên nơi người dùng gõ ⇒ đúng chỗ nguy hiểm đó.
 *  2. **Dòng hỏng không được làm sập và không được làm mất cả sổ** — chuỗi đọc từ đĩa, sửa tay được.
 *  3. **Luật không-bao-giờ-chạy bị TỪ CHỐI, không lưu im lặng** — không ngày nào / khung giờ ngược là thứ người
 *     dùng tưởng đã đặt xong mà nó nằm chết (cùng họ "nút chết" mà dự án cấm).
 */
class ScheduledNavRuleTest {

    /**
     * Luật PHẢI dựng được ở ca này.
     *
     * Không dùng `Assertions.assertNotNull` vì bản JUnit 5 của nó trả `void` (không phải giá trị như
     * `kotlin.test`), nên nó không dùng được làm phép mở `null` — viết `!!` thay thì lúc đỏ chỉ có một
     * `NullPointerException` không nói gì.
     */
    private fun req(r: ScheduledNavRule?): ScheduledNavRule =
        r ?: error("luật phải hợp lệ ở ca này nhưng của() trả null")

    private fun rule(
        id: String = "r1",
        enabled: Boolean = true,
        startMin: Int = 7 * 60 + 30,
        endMin: Int = 9 * 60,
        days: Set<Int> = ScheduledNavRules.WEEKDAYS,
        requireGps: Boolean = true,
        placeId: String = "công ty",
        navApp: String = "gmaps",
    ) = ScheduledNavRules.of(id, enabled, startMin, endMin, days, requireGps, placeId, navApp)

    // ══ (1) Mã hoá ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `ma hoa roi giai ma tra lai dung luat`() {
        val r = req(rule())
        assertEquals(r, ScheduledNavRules.decodeLine(ScheduledNavRules.encodeLine(r)))
    }

    /** Dạng trên đĩa phải ĐỌC ĐƯỢC BẰNG MẮT (cứu tay qua `run-as … cat`) — khoá nguyên văn một dòng. */
    @Test
    fun `dang luu doc duoc bang mat`() {
        val r = req(rule(requireGps = true))
        assertEquals("r1|1|450|540|1,2,3,4,5|1|công ty|gmaps", ScheduledNavRules.encodeLine(r))
    }

    @Test
    fun `co bat va co gps ghi thanh 1 0 doc lai dung`() {
        val off = req(rule(enabled = false, requireGps = false))
        val line = ScheduledNavRules.encodeLine(off)
        assertEquals("r1|0|450|540|1,2,3,4,5|0|công ty|gmaps", line)
        val back = req(ScheduledNavRules.decodeLine(line))
        assertFalse(back.enabled)
        assertFalse(back.requireGps)
    }

    /** Tập thứ luôn ghi đã SẮP XẾP ⇒ cùng một luật cho cùng một chuỗi, bất kể thứ tự người dùng tick. */
    @Test
    fun `tap thu ghi da sap xep nen chuoi on dinh`() {
        val a = req(rule(days = setOf(5, 1, 3)))
        val b = req(rule(days = setOf(3, 5, 1)))
        assertEquals("r1|1|450|540|1,3,5|1|công ty|gmaps", ScheduledNavRules.encodeLine(a))
        assertEquals(ScheduledNavRules.encodeLine(a), ScheduledNavRules.encodeLine(b))
        assertEquals(a, b)
    }

    // ══ (2) Khử ký tự ngăn ở cửa VÀO ═════════════════════════════════════════════════════════════════════

    /**
     * Chữ người dùng mang `|` hoặc `,` ⇒ khử ngay lúc dựng, nên chuỗi trên đĩa **không thể** có dòng sai số trường.
     */
    @Test
    fun `ky tu ngan trong chu nguoi dung bi khu o cua vao`() {
        val r = req(rule(placeId = "công ty|chi nhánh,2"))
        assertFalse(r.placeId.contains('|'), "còn `|` là còn đường làm mất cả bản ghi")
        assertFalse(r.placeId.contains(','))
        // Và vòng mã hoá vẫn tròn (đúng số trường).
        assertEquals(r, ScheduledNavRules.decodeLine(ScheduledNavRules.encodeLine(r)))
    }

    /** Xuống dòng bị khử — một `\n` lọt vào sẽ tách một luật thành hai dòng rác. */
    @Test
    fun `xuong dong bi khu`() {
        val r = req(rule(placeId = "công\nty"))
        assertFalse(r.placeId.contains('\n'))
        assertEquals(1, ScheduledNavRules.encodeLine(r).split('\n').size)
    }

    /** `placeId` chuẩn hoá bằng CHÍNH phép của sổ địa chỉ ⇒ hoa/thường không làm luật trỏ lệch. */
    @Test
    fun `placeId chuan hoa theo dung phep cua so dia chi`() {
        val r = req(rule(placeId = "  Công Ty  "))
        assertEquals("công ty", r.placeId)
        assertEquals(r.placeId, ScheduledNavRules.placeIdOf(SavedPlace("Công Ty", "Keangnam Landmark 72")))
    }

    // ══ (3) Từ chối dữ liệu không dùng được ══════════════════════════════════════════════════════════════

    @Test
    fun `khong ngay nao thi tu choi`() {
        assertNull(rule(days = emptySet()), "một luật không ngày nào là luật không bao giờ chạy")
    }

    @Test
    fun `thu ngoai dai bi tu choi`() {
        assertNull(rule(days = setOf(0, 1)))
        assertNull(rule(days = setOf(8)))
    }

    @Test
    fun `khung gio ngoai dai bi tu choi`() {
        assertNull(rule(startMin = -1))
        assertNull(rule(endMin = ScheduledNavRules.MAX_MIN + 1))
        assertEquals(24 * 60 - 1, ScheduledNavRules.MAX_MIN)
    }

    /** Khung QUA ĐÊM bị từ chối có chủ ý — xem KDoc [ScheduledNavRules.inWindow] (sổ đã-dẫn mất nghĩa). */
    @Test
    fun `khung qua dem bi tu choi thay vi doan`() {
        assertNull(rule(startMin = 22 * 60, endMin = 2 * 60))
    }

    @Test
    fun `khung mot phut van hop le`() {
        val r = req(rule(startMin = 450, endMin = 450))
        assertTrue(ScheduledNavRules.inWindow(r, 450))
    }

    @Test
    fun `truong rong thi tu choi`() {
        assertNull(rule(id = "   "))
        assertNull(rule(placeId = ""))
        assertNull(rule(navApp = " "))
        assertNull(rule(placeId = "|"), "chỉ toàn ký tự ngăn ⇒ khử xong là rỗng")
    }

    // ══ (4) Dòng hỏng khi giải mã ════════════════════════════════════════════════════════════════════════

    @Test
    fun `dong hong ra null khong nem`() {
        assertNull(ScheduledNavRules.decodeLine("r1|1|450|540|1,2|1|công ty"), "thiếu trường")
        assertNull(ScheduledNavRules.decodeLine("r1|1|450|540|1,2|1|công ty|gmaps|thừa"), "thừa trường")
        assertNull(ScheduledNavRules.decodeLine("r1|1|bảy giờ|540|1,2|1|công ty|gmaps"), "giờ không phải số")
        assertNull(ScheduledNavRules.decodeLine("r1|1|450|540||1|công ty|gmaps"), "tập thứ rỗng")
        assertNull(ScheduledNavRules.decodeLine(""), "dòng rỗng")
        assertNull(ScheduledNavRules.decodeLine("|||||||"), "toàn trường rỗng")
    }

    /** Thứ rác lẫn thứ thật: giữ phần đọc được thay vì bỏ cả luật — nhưng rác không được thành một ngày. */
    @Test
    fun `thu rac bi bo, thu that giu lai`() {
        val r = req(ScheduledNavRules.decodeLine("r1|1|450|540|1,x,3|1|công ty|gmaps"))
        assertEquals(setOf(1, 3), r.days)
    }

    // ══ (5) Khung giờ — hai đầu đều tính (spec R2.3) ═════════════════════════════════════════════════════

    @Test
    fun `trong khung tinh ca hai dau`() {
        val r = req(rule(startMin = 450, endMin = 540))
        assertTrue(ScheduledNavRules.inWindow(r, 450), "đúng phút bắt đầu")
        assertTrue(ScheduledNavRules.inWindow(r, 540), "đúng phút kết thúc")
        assertTrue(ScheduledNavRules.inWindow(r, 500))
        assertFalse(ScheduledNavRules.inWindow(r, 449))
        assertFalse(ScheduledNavRules.inWindow(r, 541))
    }

    /** Hằng thứ khớp ISO-8601 (`java.time.DayOfWeek.value`) — `:app` không phải dựng bảng đổi thứ hai. */
    @Test
    fun `hang thu khop ISO-8601`() {
        assertEquals(1, ScheduledNavRules.MON)
        assertEquals(7, ScheduledNavRules.SUN)
        assertEquals(java.time.DayOfWeek.MONDAY.value, ScheduledNavRules.MON)
        assertEquals(java.time.DayOfWeek.SUNDAY.value, ScheduledNavRules.SUN)
        assertEquals(setOf(1, 2, 3, 4, 5), ScheduledNavRules.WEEKDAYS)
        assertEquals((1..7).toSet(), ScheduledNavRules.ALL_DAYS)
    }
}
