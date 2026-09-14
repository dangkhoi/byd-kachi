package com.byd.clusternav.launcher

/**
 * ═══ S5 — LỆNH đặt/đọc **màn hình chính** (thuần JVM, test off-car) ═══════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-settings-ia-v2.html` §9 (S5). Một chỗ DUY NHẤT dựng ba chuỗi này, để đường **màn Cài đặt**
 * (`ClusterNavBridge.setDefaultHome` → `LocalDeviceShell.setHomeActivity`) và đường **khởi động nguội**
 * ([com.byd.clusternav.KachiAutostart.ensureHomeActivity]) không bao giờ lệch một byte — cùng lẽ DRY mà
 * `FreeformLaunch` đã theo cho lệnh cửa sổ tự do.
 *
 * ## [ĐO] xe DiLink3.0 2026-09-14 — vì sao là ĐÚNG hai lệnh này
 * `cmd package set-home-activity com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity` chạy từ shell
 * uid 2000 ⇒ `Success`; sau đó `cmd package resolve-activity … category.HOME` trả `packageName=com.byd.launcher`,
 * bấm Home → Kachi lên. ROM BYD **không hiện hộp chọn HOME** khi bấm nút Home, nên đây là đường đặt được duy nhất.
 * Kênh dadb loopback của app chạy lệnh dưới **cùng shell uid 2000** ⇒ đường này dùng được như OTA/pm grant.
 *
 * [RESOLVE] giữ `--brief` (đúng chuỗi mà `KachiAutostart` đã chạy tốt trước S5 — CLAUDE.md §6): nó trả về **đúng
 * component** (`pkg/cls`, một dòng, không khoảng trắng), đúng dạng mà [FreeformLaunch.parseComponent] bắt.
 */
object HomeActivityCmd {

    /** Đọc component đang là màn hình chính. `--brief` ⇒ chỉ in component, hợp với [FreeformLaunch.parseComponent]. */
    const val RESOLVE =
        "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"

    /** Đặt [component] ("pkg/cls") làm màn hình chính. Cần shell/root — chạy qua dadb uid-shell (ON-CAR). */
    fun set(component: String): String = "cmd package set-home-activity $component"

    /** Output của [RESOLVE] có đang trỏ về [component] không. Thuần ⇒ khoá được off-car. */
    fun isHome(resolveOutput: String, component: String): Boolean =
        FreeformLaunch.parseComponent(resolveOutput) == component
}
