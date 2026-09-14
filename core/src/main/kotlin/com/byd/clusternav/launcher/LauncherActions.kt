package com.byd.clusternav.launcher

/**
 * ═══ S4 · R12 — HÀNH ĐỘNG CỦA CHÍNH LAUNCHER, ĐẶT ĐƯỢC NHƯ MỘT KHẢ NĂNG ══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` **R12 (b)**. Owner 2026-09-14: *"thêm cho chọn Ứng Dụng ở
 * chỗ chọn nút cho Thanh"*.
 *
 * ## Vì sao là một BỘ ĐĂNG KÝ THỨ SÁU chứ không phải một nút trong [ControlRegistry]
 * Mọi mã trong [ControlRegistry] đều **bắn lệnh xuống xe** qua `CarControlPort` (`toggle`/`step`/`press`…) và mang
 * một [EvidenceTier] nói *"lệnh này đã chạy thật trên xe chưa"*. *Ứng dụng* và *Cài đặt* không chạm vào xe một
 * chút nào — chúng mở ngăn kéo và mở màn Cài đặt của chính launcher. Nhét chúng vào [ControlRegistry] sẽ:
 *  • cho chúng một `bindingKey` HAL không tồn tại (bảng ràng buộc HAL có bài canh — nó sẽ đỏ, đúng),
 *  • và làm `ControlDockView` bắn `control().press("launcher_apps")` xuống cổng xe — một lệnh vô nghĩa gửi tới
 *    phần cứng, đúng loại "nối chéo âm thầm" mà [CapabilityCatalog] dựng ra để chặn.
 * Nên đây là **loại khả năng thứ ba** ([CapabilityKind.LAUNCHER]), không phải một biến thể của nút.
 *
 * ## Ba tính chất chốt ở đây (test khoá từng cái)
 *  1. **Tier [EvidenceTier.PROVEN] ⇒ KHÔNG chấm "chưa kiểm"**: đường mở ngăn kéo / mở Cài đặt là đường mà thanh
 *     trên đã dùng hằng ngày; treo dấu chưa-kiểm lên nó là nói sai, và làm dấu đó mất giá trị ở chỗ nó đúng.
 *  2. **KHÔNG chippable**: [TopStripConfig.isChippable] chỉ nhận mục ĐỌC, nên loại này bị từ chối **do cấu tạo** —
 *     chip 24dp là chỗ HIỂN THỊ, không phải chỗ bấm (xem KDoc [TopStripConfig]).
 *  3. **`domain = null`**: chúng không thuộc lĩnh vực nào của xe. Hệ quả cố ý: [CapabilityCatalog.byDomain] không
 *     bày chúng ⇒ bộ chọn nút phải có **khối riêng** ([CapabilityPicker.launcherPicks], nơi ghi vì sao khối ấy
 *     đứng đầu), và ngăn kéo gán-ô KHÔNG bày — một ô giữa màn chỉ để mở ngăn kéo là đổi chỗ đắt lấy việc rẻ.
 */

/**
 * Một hành động của launcher.
 *
 * @property id mã — cùng không gian mã PHẲNG với datum/nút/widget/nhóm/gói lệnh (xem [CapabilityCatalog]); tiền tố
 *   `launcher_` để đọc mã là biết ngay nó không chạm vào xe, và để bài canh xung đột chỉ đích danh được.
 * @property icon tên icon (bảng tra ở `:app` `KachiTheme.iconRes`) — **dùng lại đúng hình** mà thanh trên đang
 *   dùng cho cùng việc đó: hai bề mặt cùng một việc thì phải cùng một hình (luật U6).
 */
data class LauncherActionDef(
    val id: String,
    override val label: String,
    val icon: String,
    override val labelEn: String? = null,
) : Localized

/** Bộ đăng ký hành động launcher — thuần Kotlin, kiểm off-car. */
object LauncherActions {

    /** Mở NGĂN KÉO ứng dụng (chế độ mở-thường, U3) — cùng đường với nút *Ứng dụng* ở thanh trên. */
    const val APPS = "launcher_apps"

    /** Mở màn **Cài đặt** của launcher — cùng đường với nút *Cài đặt* ở thanh trên (S1: MỘT cửa vào cấu hình). */
    const val SETTINGS = "launcher_settings"

    val ALL: List<LauncherActionDef> = listOf(
        LauncherActionDef(APPS, "Ứng dụng", "ic-apps", labelEn = "Apps"),
        LauncherActionDef(SETTINGS, "Cài đặt", "ic-settings", labelEn = "Settings"),
    )

    fun byId(id: String): LauncherActionDef? = ALL.firstOrNull { it.id == id }
}
