package com.byd.clusternav.navigation

/**
 * DỰNG [NavigationFrameContent] — **một chỗ duy nhất** biến tín hiệu thô của MỘT nguồn dẫn đường thành khung
 * chuẩn đi vào cửa chính (`NavRepository.ingest*` → [NavigationSessionCoordinator] → làn cụm + cụm-centre + HUD).
 *
 * VÌ SAO CÓ FILE NÀY (F4, spec `docs/specs/nav-input-output-architecture.html`): kiến trúc duyệt 08-24 là
 * *"nhiều cách nhận tín hiệu → MỘT cửa vào → nhiều cửa ra"*. Trước đó đường THÔNG BÁO dựng khung ngay trong
 * `NavRepository.ingest` còn đường ẢNH tự ghi thẳng HAL ở một owner khác ⇒ hai cửa, và cửa thứ hai không mở
 * được chốt phiên nên VietMap/Waze không lên HUD. Tách phép dựng khung ra đây để CẢ HAI đường dùng CHUNG một
 * phép biến đổi, và để khoá nó bằng unit-test off-car (`:core`).
 *
 * THUẦN: không Android, không I/O, không đọc holder toàn cục — mọi thứ đi qua tham số. Gọi được từ luồng nào
 * cũng được.
 *
 * ⚠ [fromNotification] là **PHÉP DỜI CHỖ NGUYÊN VĂN** của biểu thức từng nằm trong `NavRepository.ingest`
 * (đường Google Maps đã proven ngoài hiện trường — CLAUDE.md §6). Không sửa một nhánh nào ở đây mà không có
 * bằng chứng: `GmapsContentGoldenTest` đông cứng đầu ra của nó theo bảng vàng.
 */
object NavContentBuilder {

    /** Không đọc được cự-ly. Cùng quy ước với `ARROW_DISTANCE_UNKNOWN` của đường ảnh và `-1` của HAL. */
    const val DISTANCE_UNKNOWN = -1

    /**
     * Khung dựng từ một frame THÔNG BÁO (Google Maps hôm nay — xem `NavApps.NOTIFICATION`).
     *
     * Tham số là đúng 6 trường của `NavState` mà khung cần; phần còn lại của `NavState` (bitmap mũi tên,
     * `active`, `updatedAt`) là việc của UI, không vào khung.
     *
     * Biểu thức bên dưới GIỮ NGUYÊN VĂN bản đang chạy ngoài hiện trường — kể cả hai lời gọi
     * [NavParse.parseEta] tách rời (không gộp thành một biến): gộp là "viết lại", mà việc này chỉ được phép
     * "dời chỗ". Khoá bằng `NavContentBuilderTest` (quét source) + `GmapsContentGoldenTest` (bảng vàng).
     */
    fun fromNotification(
        maneuverIcon: Int,
        maneuverText: String,
        distance: String,
        road: String,
        eta: String,
        maneuver: Maneuver?,
    ): NavigationFrameContent = NavigationFrameContent(
        maneuverCode = maneuverIcon.takeIf { it >= 0 },
        maneuverText = maneuverText.takeIf(String::isNotBlank),
        distanceMeters = NavParse.parseMeters(distance).takeIf { it >= 0 },
        roadName = road.takeIf(String::isNotBlank),
        etaEpochMs = null,
        routeRemainingMeters = NavParse.parseEta(eta).first.takeIf { it >= 0 },
        routeRemainingSeconds = NavParse.parseEta(eta).second.takeIf { it >= 0 },
        arrivalClock = NavParse.extractArrivalClock(eta),
        // Chốt maneuver TRUNG LẬP MỘT LẦN tại đây (biên đầu vào): ưu tiên maneuver đã có sẵn
        // (nguồn trực tiếp), nếu không thì bắc cầu từ mã AMAP đã phân loại. Cả hai đầu ra encode từ đây.
        maneuver = maneuver ?: Maneuver.fromAmapIcon(maneuverIcon),
    )
}
