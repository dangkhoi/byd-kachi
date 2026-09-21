package com.byd.clusternav.launcher

/**
 * MÔ HÌNH NĂNG LỰC (capability) — thuần Kotlin (:core), dùng chung cho [TelemetryRegistry] + [ControlRegistry] +
 * [CarStatus] + [CarCapabilities]. KHÔNG chạm Android (khoá bởi `CoreIsolationTest` + `LayeringRulesTest`).
 *
 * Đây là lớp DỮ LIỆU cho kiến trúc **registry-driven** (spec `docs/specs/kachi-w1-real-data.html` §4): thêm 1 thông
 * tin/nút xe = thêm 1 dòng registry, KHÔNG viết code mới. Binding thật tới HAL (named-method / feature-id số) do
 * Stage 2 (`HalBindingTable` ở :app) map theo [TelemetrySpec.bindingKey] / [ControlDef.bindingKey].
 */

/**
 * NHÓM chức năng — CHỈ để GOM NHÓM panel/HOME. Owner CHỐT 2026-09-10: **KHÔNG gate an toàn** (đã bỏ cơ chế gate,
 * bỏ chặn theo tốc độ/số). `domain` tuyệt đối KHÔNG mang ngữ nghĩa an toàn — chỉ là nhãn nhóm hiển thị.
 *
 * 8 domain đầu = telemetry catalog §A (xem [TELEMETRY]); [INFOTAINMENT] chỉ dùng cho control (giải trí/cụm/HUD).
 */
enum class Domain(override val label: String, override val labelEn: String) : Localized {
    ENERGY("Năng lượng & sạc", "Energy & charging"),
    DRIVETRAIN("Động lực & tốc độ", "Drivetrain & speed"),
    CLIMATE("Khí hậu & không khí", "Climate & air"),
    TYRES("Lốp", "Tyres"),
    BODY("Thân xe · cửa · kính", "Body · doors · windows"),
    LIGHTS("Đèn", "Lights"),
    // ⚠ 2026-09-16 — owner gỡ TOÀN BỘ ADAS/an toàn chủ động khỏi launcher (*"người lái muốn chỉnh mấy cái này thì
    // vào setting thật của xe"*). Domain `SAFETY` vì thế **không còn tồn tại**: mọi datum/nút của nó đã xoá, ba mục
    // điện 12V (`volt_12v` · `volt_12v_level`) chuyển sang [ENERGY] vì ắc-quy không phải hệ an toàn
    // lái. Đừng dựng lại enum này để "gom tạm" thứ gì — nó là cổng duy nhất khiến ADAS không mọc lại.
    IDENTITY("Danh tính · khoá", "Identity · keys"),
    INFOTAINMENT("Giải trí · cụm · HUD", "Media · cluster · HUD");

    companion object {
        /**
         * 7 domain của telemetry catalog §A (kachi-capability-catalog). [INFOTAINMENT] KHÔNG nằm ở đây vì nó là
         * nhóm control (không có datum đọc riêng). Test `TelemetryRegistry` phủ đủ 7 domain này.
         *
         * Trước 2026-09-16 có 8 — `SAFETY` là cái thứ 8, đã gỡ cùng toàn bộ ADAS (xem chú thích ở enum).
         */
        val TELEMETRY: List<Domain> = listOf(ENERGY, DRIVETRAIN, CLIMATE, TYRES, BODY, LIGHTS, IDENTITY)
    }
}

/**
 * Gợi ý HÌNH render cho widget/telemetry (Stage 3 map sang view thật: vòng/đồng-hồ/thẻ/bảng/dải…).
 *
 * ⚠ TÁCH KHỎI [WidgetKind] (`WidgetRegistry.kt` = {LOCAL, CAR, BOARD} phân loại NGUỒN dữ liệu, bị `WidgetRegistryTest`
 * khoá — KHÔNG được đổi). [WidgetShape] là HÌNH render, khái niệm khác. Đặt tên khác để tránh redeclaration + giữ
 * test cũ xanh (quyết định ghi ở spec §9).
 */
enum class WidgetShape {
    /** Vòng tròn tiến trình (SOC, PM2.5). */ RING,
    /** Đồng hồ cung (công suất, ga/phanh). */ GAUGE,
    /** Kim quay (tốc độ, vô-lăng). */ DIAL,
    /** Thẻ nhiều dòng (sạc, sức khoẻ pin). */ CARD,
    /** Bảng lưới nhiều ô (4 lốp · cửa & khoang). */ BOARD,
    /** Dải icon trạng thái (cửa/kính/đèn). */ STRIP,
    /** Thẻ nhạc + transport. */ MEDIA,
    /** Một con số + đơn vị. */ VALUE,
    /** Huy hiệu bật/tắt/enum ngắn. */ BADGE,
}

/**
 * MỨC BẰNG CHỨNG — UI hiện badge/mờ theo đây (spec R3, KHÔNG bịa số).
 *  • [PROVEN]    — đã chạy trên xe owner trong ClusterNav (tin nhất) → hiện bình thường.
 *  • [OVERDRIVE] — đọc từ mã Overdrive (MIT), chưa kiểm trim này → NỐI + hiện + badge "chưa kiểm trên xe".
 *  • [DASHCAST]  — đọc từ mã byd-dashcast (MIT, kỹ thuật CAN/cụm) → NỐI + badge.
 *  • [NEEDS_CAR] — chỉ xác nhận được trên xe thật (method/id số chưa chắc, hoặc cần TPMS phát…) → coi như CHƯA nối
 *                  (UI "—" + mờ) tới khi đóng grab-list ở spec §9.
 */
enum class EvidenceTier {
    // ⚠ GIAO KÈO: THỨ TỰ KHAI Ở ĐÂY LÀ ĐỘ TIN CẬY **GIẢM DẦN** (mạnh → yếu), và **code phụ thuộc vào nó**:
    // `ActionMacro.tier()` xếp hạng bằng `ordinal` để lấy mức YẾU NHẤT trong các bước của một gói lệnh.
    // ⇒ Thêm mức mới thì phải QUYẾT ĐỊNH nó đứng ở đâu trong thang này, không được chèn bừa vào giữa.
    // Có test khoá (`ActionMacrosTest.thu tu khai cua EvidenceTier LA thu hang tin cay giam dan`): nó khẳng định
    // NGUYÊN danh sách có thứ tự, nên đảo chỗ hoặc chèn giữa đều làm test đỏ — đúng ý muốn, để buộc xem lại.
    PROVEN,
    OVERDRIVE,
    DASHCAST,
    NEEDS_CAR;

    /** Có nối binding không (mọi tier trừ [NEEDS_CAR] đều đã có đường HAL để thử). */
    val wired: Boolean get() = this != NEEDS_CAR

    /**
     * ⚠ 2026-09-21 · OWNER CHỐT BỎ HẲN CHẤM "chưa kiểm trên xe" — **luôn `false`**.
     *
     * Owner: *"thôi bỏ hẳn hết đi em, chiều nay lên xe test lại 1 vòng cái nào không được thì sửa luôn, chấm gì
     * nữa"*. Chấm badge từng có ý nghĩa khi chưa ai lên xe kiểm; nay lượt test-xe cuối cùng sẽ đi hết một vòng và
     * sửa tại chỗ, nên dấu "chưa kiểm" không còn phục vụ gì — và một chấm hiện trên gần hết ô đọc như trang toàn lỗi.
     *
     * GIỮ [EvidenceTier] làm **dữ liệu** (thứ tự tin cậy + [wired] vẫn dùng cho `ActionMacro.tier()` và cho việc
     * NEEDS_CAR tự lộ bằng "—"): chỉ **UI KHÔNG VẼ CHẤM** nữa. Đây là cổng DUY NHẤT mọi bề mặt badge đi qua
     * (`CapabilityPick`/`ReadTile`/`ControlDef`/`GroupBoard` đều đọc `tier.needsBadge`), nên tắt ở đây là tắt hết.
     */
    val needsBadge: Boolean get() = false
}
