package com.byd.clusternav.launcher

/**
 * Adapter [AppLauncher] cho XE khi có kênh shell (`sh` chạy 1 lệnh qua dadb uid-shell, như các module ClusterNav). Chưa có
 * kênh → HOME dùng [NoCar]; có kênh → `ShellAppLauncher(dadbShell)` (`KachiHomeActivity` `onSeam`).
 *
 * 2.93 · SLOT-DEAD-OPENSLOT (spec `kachi-293-wave2a.html` §4.4): `openInSlot`/`moveToSlot` (mở cửa sổ freeform vào khung ô bằng
 * `--windowingMode 5` + `am task resize`, cùng `FreeformLaunch.launchCmd/parseTaskId` + vòng chờ task) GỠ — [ĐO grep 06/10]
 * 0 chỗ gọi sản phẩm (main · vehicleTest). 2.93 wave 2C · SLOT-DEAD-FREEFORM-REST: `isFreeformAvailable` (lệnh `settings get
 * global enable_freeform_support`) gỡ theo — [ĐO grep 07/10] 0 chỗ gọi sản phẩm. Còn lại đúng một việc hợp đồng [AppLauncher]
 * đòi ([closeSlot]); mẫu tên gói [PKG] dùng chung.
 *
 * Thuần JVM (không android.*) → nằm ở :core; lệnh test off-car ở `LauncherCommandGoldenTest` / `ShellAppLauncherTest`.
 */
class ShellAppLauncher(
    private val sh: (String) -> String,
) : AppLauncher {

    /**
     * ⚠ [pkg] đi THẲNG vào một chuỗi lệnh shell ([FreeformLaunch.resolveCmd] nội suy `$pkg` không có dấu nháy),
     * và nó **không phải** lúc nào cũng đến từ danh sách app đã cài: ô workspace lưu bền dưới dạng `app:<gói>`
     * (`SlotCodec`), mà KDoc của chính lớp đó ghi rõ *"chuỗi này đến từ đĩa và có thể bị sửa tay"*. Một tên gói
     * có `;` hoặc `$(…)` vì thế chạy được lệnh tuỳ ý dưới shell uid 2000 — CLAUDE.md §4.1 (*user input → lệnh
     * phải được làm sạch*). Tên gói Android hợp lệ chỉ gồm chữ/số/`_`/`.`, nên lọc theo đúng bộ ký tự ấy không
     * từ chối một gói thật nào và giữ NGUYÊN byte của mọi chuỗi lệnh (golden test không đổi).
     */
    private fun safe(pkg: String): Boolean = pkg.matches(PKG)

    internal companion object {
        /**
         * Tên gói Android hợp lệ — xem [safe]. `internal` (không còn `private`) để [FloatingWindowLedger] lọc tên gói
         * bằng ĐÚNG mẫu này (PROFILE-SWITCH-SLOTS R-B4) — một mẫu, không chép.
         */
        val PKG = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*")
    }

    /**
     * ⚠ Màn nhà KHÔNG gọi tới hàm này (PROFILE-SWITCH-SLOTS E10 · R-B5): bộ mở này chỉ được gán khi có kênh shell
     * (`KachiHomeActivity` `onSeam`), mà khi có kênh thì `LauncherWindows.closeApp` thoát sớm vì `embedding = true`.
     * Và lệnh của nó là [FreeformLaunch.fullscreenCmd] — đưa app lên TOÀN MÀN đè nhà, KHÔNG phải đóng cửa sổ. Việc
     * đóng cửa sổ nổi Kachi đã mở thuộc `LauncherWindows.sweepFloating` (`am stack remove <id>`, [FloatingOrphanPlan]).
     */
    override fun closeSlot(pkg: String) {
        if (!safe(pkg)) return
        val comp = FreeformLaunch.parseComponent(sh(FreeformLaunch.resolveCmd(pkg))) ?: return
        sh(FreeformLaunch.fullscreenCmd(comp))
    }
}
