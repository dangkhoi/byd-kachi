package com.byd.clusternav.navigation

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * F4 (spec `docs/specs/nav-input-output-architecture.html`) — khoá [NavContentBuilder] bằng GIÁ TRỊ, off-car.
 *
 * ⚠ 2026-08-28: đường ẢNH (VietMap/Waze) đã bị GỠ — [NavContentBuilder] chỉ còn [NavContentBuilder.fromNotification]
 * (Google Maps). Các test cũ của `fromImage`/`fromKeepAlive` đã xoá theo. Hai nhóm còn lại:
 *  1. **HÀNH VI** — `fromNotification` dựng khung đúng.
 *  2. **DỜI CHỖ, KHÔNG VIẾT LẠI** — quét source `fromNotification` để chắc rằng biểu thức của đường Google Maps
 *     đi qua đúng những mảnh cũ, không có nhánh mới lén vào (CLAUDE.md §6). Bảng vàng ở
 *     `GmapsContentGoldenTest` khoá phần giá trị.
 */
class NavContentBuilderTest {

    // ── 1. fromNotification — hành vi tối thiểu (bảng vàng lo phần còn lại) ───────────────────────────

    @Test fun `fromNotification - khung GMaps dien hinh`() {
        val c = NavContentBuilder.fromNotification(
            maneuverIcon = 3, maneuverText = "", distance = "250 m", road = "Nguyễn Huệ",
            eta = "10:32 · 5.2 km · 8 phút", maneuver = null,
        )
        assertEquals(3, c.maneuverCode)
        assertEquals(250, c.distanceMeters)
        assertEquals("Nguyễn Huệ", c.roadName)
        assertEquals("10:32", c.arrivalClock)
        assertEquals(5_200, c.routeRemainingMeters)
        assertEquals(8 * 60, c.routeRemainingSeconds)
        assertEquals(Maneuver.TURN_RIGHT, c.maneuver)
    }

    @Test fun `fromNotification - maneuver co san thang ma AMAP`() {
        val c = NavContentBuilder.fromNotification(
            maneuverIcon = 11, maneuverText = "lối ra thứ 2", distance = "300 m", road = "Điện Biên Phủ",
            eta = "", maneuver = Maneuver.ROUNDABOUT_RIGHT,
        )
        assertEquals(Maneuver.ROUNDABOUT_RIGHT, c.maneuver, "maneuver của nguồn thắng fromAmapIcon(11)")
        assertEquals("lối ra thứ 2", c.maneuverText)
    }

    // ── 2. DỜI CHỖ, KHÔNG VIẾT LẠI (quét source) ─────────────────────────────────────────────────────

    private val src by lazy {
        SourceRoots.text("src/main/kotlin/com/byd/clusternav/navigation/NavContentBuilder.kt")
    }

    /** Thân `fun fromNotification(...)` (dừng ở dấu `)` đóng ở cột 0 của biểu thức `= NavigationFrameContent(`). */
    private fun fromNotificationBody(): String {
        val start = src.indexOf("fun fromNotification(")
        assertTrue(start >= 0, "không tìm thấy fun fromNotification")
        val rest = src.substring(start)
        val end = rest.indexOf("\n    )\n")   // dấu đóng của biểu thức, KHÔNG phải của danh sách tham số
        assertTrue(end > 0, "không tìm thấy dấu đóng của fromNotification")
        return rest.substring(0, end)
    }

    /**
     * KHOÁ §6 — đường Google Maps là phép DỜI CHỖ. Mỗi mảnh dưới đây là nguyên văn của bản đang chạy ngoài
     * hiện trường (`NavRepository.ingest` trước 08-24). Thiếu một mảnh = có người đã viết lại một nhánh.
     */
    @Test fun `fromNotification giu NGUYEN VAN cac manh cua ban cu`() {
        val body = fromNotificationBody()
        listOf(
            "maneuverIcon.takeIf { it >= 0 }",
            "maneuverText.takeIf(String::isNotBlank)",
            "NavParse.parseMeters(distance).takeIf { it >= 0 }",
            "road.takeIf(String::isNotBlank)",
            "etaEpochMs = null",
            "NavParse.parseEta(eta).first.takeIf { it >= 0 }",
            "NavParse.parseEta(eta).second.takeIf { it >= 0 }",
            "NavParse.extractArrivalClock(eta)",
            "maneuver = maneuver ?: Maneuver.fromAmapIcon(maneuverIcon)",
        ).forEach { piece ->
            assertTrue(body.contains(piece), "fromNotification phải giữ nguyên mảnh cũ: $piece")
        }
    }

    /** Không nhánh mới: đường notification không được rẽ theo tên gói hay theo bất kỳ điều kiện nào. */
    @Test fun `fromNotification khong co nhanh dieu kien nao`() {
        val body = fromNotificationBody()
        assertFalse(body.contains("if ("), "fromNotification phải là MỘT biểu thức, không nhánh")
        assertFalse(body.contains("when ("), "fromNotification phải là MỘT biểu thức, không nhánh")
        assertFalse(src.contains("import android"), ":core là JVM thuần")
    }
}
