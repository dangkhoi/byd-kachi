package com.byd.clusternav.navigation

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * BẢNG VÀNG cho đường **Google Maps** — bằng chứng CHÍNH của "không hồi quy" xuyên suốt cả ba bước F4
 * (spec `docs/specs/nav-input-output-architecture.html`).
 *
 * VÌ SAO CẦN: owner dặn *"đường đã chạy tốt ngoài hiện trường thì không chỉnh"* (CLAUDE.md §6). Bước 1 dời
 * biểu thức dựng khung từ `NavRepository.ingest` sang [NavContentBuilder.fromNotification]. Bảng vàng này
 * được ĐÔNG CỨNG ĐÚNG LÚC ĐÓ — tức nó *bằng cấu tạo* chính là hành vi đang chạy ngoài hiện trường. Bước 2 và
 * bước 3 (nơi rủi ro thật nằm: thêm trường vào khung, xoá cửa thứ hai) phải giữ nó **không đổi một byte**.
 *
 * CÁCH ĐỌC KẾT QUẢ KHI ĐỎ: bảng vàng đổi = ĐƯỜNG GMAPS HỒI QUY. Dừng lại, tìm nguyên nhân.
 * **KHÔNG được sửa file vàng "cho xanh"** — đó là xoá đúng cái máy đo mình vừa dựng.
 *
 * NGUỒN HÀNG: các dạng chuỗi notification đã được `NotificationParserTest` (`:app`) và `NavParseTest`
 * (`:core`) khoá từ trước — cự ly m/km, dấu phẩy thập phân, ETA đủ 3 phần / thiếu phần / chỉ giờ / chỉ phút,
 * giờ không hợp lệ, vòng xuyến có số lối ra, tên đường rỗng, maneuver có sẵn thắng mã AMAP. Mỗi hàng in ĐỦ
 * 9 trường của [NavigationFrameContent] nên một thay đổi ở BẤT KỲ trường nào cũng lộ ra.
 */
class GmapsContentGoldenTest {

    /** Một hàng notification GMaps: đúng 6 trường `NavState` mà khung cần. */
    private data class Row(
        val name: String,
        val icon: Int,
        val text: String = "",
        val dist: String = "",
        val road: String = "",
        val eta: String = "",
        val maneuver: Maneuver? = null,
    )

    private val rows = listOf(
        Row("met-co-ban", 3, dist = "250 m", road = "Nguyễn Huệ", eta = "10:32 · 5.2 km · 8 phút"),
        Row("km-cham", 2, dist = "1.2 km", road = "Xa lộ Hà Nội", eta = "10:45 · 12.4 km · 21 phút"),
        Row("km-phay-thap-phan", 3, dist = "1,2 km", road = "Võ Văn Kiệt", eta = "11:07 · 12,4 km · 21 phút"),
        Row("khung-rong", -1),
        Row("di-thang", 9, text = "Đi thẳng", dist = "80 m", road = "Lê Lợi", eta = "7:05 · 3.1 km · 6 phút"),
        Row(
            "vong-xuyen-co-so-loi-ra", 11, text = "Đi vào vòng xuyến, lối ra thứ 2", dist = "300 m",
            road = "Điện Biên Phủ", eta = "18:21 · 9.8 km · 15 phút", maneuver = Maneuver.ROUNDABOUT_RIGHT,
        ),
        Row("eta-gio-cong-phut-khong-co-dong-ho", 11, text = "lối ra thứ 3", dist = "500 m", eta = "2 giờ 5 phút"),
        Row("eta-chi-co-km", 4, dist = "45 m", road = "Ngõ 12", eta = "5.2 km"),
        Row("eta-nua-dem", -1, dist = "2 km", road = "QL1A", eta = "0:04 · 1,0 km · 2 phút"),
        Row("gio-khong-hop-le-va-duong-rong", 6, dist = "120 m", road = "  ", eta = "25:99 sai giờ · 4 km · 5 phút"),
        Row("u-turn", 8, text = "Quay đầu", dist = "30 m", road = "Trường Chinh", eta = "23:59 · 0.4 km · 1 phút"),
        Row("km-le", 15, dist = "1.05 km", road = "Cầu Sài Gòn", eta = "12:00 · 30 km · 45 phút"),
        Row("icon-0-khong-map-duoc", 0, dist = "600 m", road = "Nguyễn Văn Linh", eta = "9:15 · 7 km · 11 phút"),
        Row("icon-ngoai-bang", 13, text = "Trạm dừng nghỉ", dist = "700 m", road = "Vành Đai 3"),
        Row("maneuver-co-san-icon-am", -1, dist = "90 m", road = "Pasteur", maneuver = Maneuver.TURN_LEFT),
        Row("maneuver-co-san-thang-icon", 3, dist = "90 m", road = "Pasteur", maneuver = Maneuver.STRAIGHT),
        Row("cu-ly-0m", 12, dist = "0 m", road = "Hàm Nghi", eta = "8:00 · 0.1 km · 1 phút"),
        Row("eta-chi-co-phut", 5, dist = "150 m", road = "Cách Mạng Tháng 8", eta = "8 phút"),
        Row("eta-chi-co-dong-ho", 7, dist = "150 m", road = "Nam Kỳ Khởi Nghĩa", eta = "10:32"),
        Row("cu-ly-khong-doc-duoc", 2, dist = "không rõ", road = "Lý Thường Kiệt", eta = "6:30 · 2 km · 4 phút"),
        Row("duong-co-khoang-trang-hai-dau", 3, dist = "70 m", road = "  Nguyễn Trãi  ", eta = "6:31 · 2 km · 4 phút"),
        Row("maneuver-text-toan-khoang-trang", 9, text = "   ", dist = "70 m", road = "Trần Hưng Đạo"),
    )

    private fun render(row: Row): String {
        val c = NavContentBuilder.fromNotification(
            maneuverIcon = row.icon,
            maneuverText = row.text,
            distance = row.dist,
            road = row.road,
            eta = row.eta,
            maneuver = row.maneuver,
        )
        return listOf(
            row.name,
            "code=${c.maneuverCode}",
            "text=${c.maneuverText}",
            "dist=${c.distanceMeters}",
            "road=${c.roadName}",
            "etaMs=${c.etaEpochMs}",
            "rMeters=${c.routeRemainingMeters}",
            "rSeconds=${c.routeRemainingSeconds}",
            "clock=${c.arrivalClock}",
            "man=${c.maneuver}",
        ).joinToString(" | ")
    }

    @Test fun `bang vang duong GMaps KHONG duoc doi mot byte`() {
        val actual = rows.joinToString("\n") { render(it) } + "\n"
        val expected = javaClass.getResourceAsStream("/$GOLDEN")?.bufferedReader()?.readText()
        if (expected == null || expected != actual) {
            // Ghi bản THẬT ra build/ để người sửa so được — nhưng KHÔNG tự ghi đè file vàng.
            runCatching {
                File("build").mkdirs()
                File("build/$GOLDEN.actual").writeText(actual)
            }
        }
        assertTrue(expected != null, "thiếu file vàng core/src/test/resources/$GOLDEN")
        assertEquals(
            expected, actual,
            "BẢNG VÀNG GMAPS ĐỔI = HỒI QUY đường đã proven ngoài hiện trường. ĐỪNG sửa file vàng cho xanh; " +
                "so với build/$GOLDEN.actual rồi tìm nguyên nhân (CLAUDE.md §6).",
        )
    }

    /** Bảng vàng chỉ có giá trị nếu nó đủ rộng — đừng để ai rút hàng cho dễ xanh. */
    @Test fun `bang vang phai du rong`() {
        assertTrue(rows.size >= 20, "bảng vàng phải có ít nhất 20 hàng, đang có ${rows.size}")
        assertEquals(rows.size, rows.map { it.name }.toSet().size, "tên hàng phải duy nhất")
    }

    private companion object {
        const val GOLDEN = "gmaps-content-golden.txt"
    }
}
