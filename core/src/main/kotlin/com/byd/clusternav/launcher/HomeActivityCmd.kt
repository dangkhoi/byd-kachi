package com.byd.clusternav.launcher

/**
 * ═══ S5 — LỆNH đặt/đọc **màn hình chính** (thuần JVM, test off-car) ═══════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-settings-ia-v2.html` §9 (S5). Một chỗ DUY NHẤT dựng các chuỗi lệnh màn hình chính, để đường **màn Cài đặt**
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

    /**
     * Đưa **màn hình chính mặc định của hệ thống** ra trước trên display 0 — tương đương bấm phím Home.
     *
     * Chuỗi này đã chạy ngoài hiện trường từ trước (đường "trả VietMap về nền lúc boot" của `VietMapAutostart`);
     * gom về đây để mọi đường dùng CHUNG một byte (DRY, CLAUDE.md global §4.1). [ĐO source android-10.0.0_r47]
     * vì sao đúng hình dạng này, không thêm không bớt:
     *  • ĐÚNG MỘT `-c` và KHÔNG `-n`: `ActivityRecord.isHomeIntent` (`ActivityRecord.java:1237-1243`) đòi
     *    `getCategories().size() == 1`; `setActivityType` (`:1283`) chỉ gán loại HOME khi KHÔNG chỉ định component
     *    — hoặc khi người gọi là system/root/recents (`canLaunchHomeActivity`, `:1254-1266`), mà uid shell 2000
     *    không thuộc diện đó. Thêm `-n` hay `-c DEFAULT` là activity mở ra thành loại `standard`, KHÔNG vào stack
     *    home — [ĐO xe 29/09 12:11, `am stack list`] một lượt mở CHỈ ĐỊNH component `…KachiHomeActivity` (người
     *    gọi chưa rõ, nhưng không phải system) nằm ở stack 41 riêng, `mActivityType=standard`, tách khỏi stack home 0.
     *  • Không `--display`: `ActivityStarter.java:1484-1486` ⇒ `mPreferredDisplayId = DEFAULT_DISPLAY` (0).
     *  • Không bị chặn "mở activity từ nền": uid shell giữ `START_ACTIVITIES_FROM_BACKGROUND`
     *    (`packages/Shell/AndroidManifest.xml:140`) ⇒ `ActivityStarter.java:993-996` không huỷ lượt mở.
     */
    const val GO_HOME = "am start -a android.intent.action.MAIN -c android.intent.category.HOME"
}
