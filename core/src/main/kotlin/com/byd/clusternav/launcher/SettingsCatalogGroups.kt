package com.byd.clusternav.launcher

/**
 * NHÓM trong màn Cài đặt — thứ tự khai = thứ tự hiện trên rail bên trái.
 *
 * ## Vì sao chia THẾ NÀY (IA v2 · `docs/specs/kachi-settings-ia-v2.html` §4.1)
 * Nhóm theo **thứ người dùng đang nghĩ tới**, không theo tệp mã và cũng không theo lớp lưu trữ. Đây là điểm dễ làm
 * sai nhất: nếu chia theo nơi lưu thì [HOME] và [DISPLAY] sẽ dính làm một (cùng nằm trong `WorkspacePrefs`), còn
 * [CAR] lại bị đẩy ra ngoài (nó ở `Prefs` của ClusterNav). Người ngồi trong xe không biết và không cần biết điều đó —
 * họ chỉ nghĩ *"màn chính trông thế nào"* hay *"xe tự làm gì khi nổ máy"*.
 *
 * Bốn đường biên đáng nói:
 *  - **[HOME] vs [BARS]**: [ĐO ảnh 2026-09-12] nhóm "Màn hình chính" dài **5 màn cuộn** vì ôm cả lưới 123 ô chọn nút
 *    thanh xe. Chip thanh trạng thái + thanh nút là **khung cố định quanh** màn chính, không phải nội dung của nó ⇒
 *    tách ra (R-UI a) để mỗi nhóm còn ≤ 2 màn cuộn (R4).
 *  - **[HOME] vs [DISPLAY]**: bố cục/hình nền trả lời *"màn chính trông thế nào"*; đơn vị, sáng/tối và ngôn ngữ là
 *    *cách trình bày*, đúng ở mọi bố cục ⇒ tách ra để đổi đơn vị không phải đi qua phần bố cục.
 *  - **[NAV] vs [CAST]**: cả hai đều "đưa thứ gì đó lên cụm đồng hồ", nhưng [NAV] là **nội dung do Kachi vẽ** (chỉ
 *    đường, biển báo) còn [CAST] là **cửa sổ của app khác** bị dời sang cụm. Hai cơ chế khác hẳn nhau, hỏng theo hai
 *    kiểu khác nhau, và người dùng bật/tắt chúng vì hai lý do khác nhau.
 *  - **[PROFILES] đứng riêng dù nó "thuộc" mọi nhóm trên**: hồ sơ quyết định [HOME] và thanh nút, nên nó phải là một
 *    nhóm thấy được — không thể là một dòng chìm trong [HOME], vì khi đó người dùng đổi bố cục mà không biết mình
 *    đang đổi cho hồ sơ nào.
 *
 * ## ⚠ Câu phụ phải NGẮN — đây là ràng buộc HÌNH, không phải văn phong
 * [ĐO ảnh 2026-09-12] 2/7 câu phụ của bản v1 bị rail cắt cụt bằng "…" (R-UI b). Ô rail chỉ cho **2 dòng**, nên câu
 * phụ dài hơn ~55 ký tự là mất chữ — mà mất chữ ở đây thì đúng phần "nhóm này chứa gì" biến mất, tức rail thôi tự
 * giải thích được. `SettingsCatalogTest` ghim trần ký tự để bản sau không lặng lẽ viết dài ra.
 *
 * @property id mã ổn định (nhật ký/test/lưu chỗ đang chọn). KHÔNG đổi khi sửa [label].
 * @property label tên hiện cho người đọc.
 * @property sub câu phụ nói **nội dung** nhóm — rail phải tự giải thích được, vì đây là lần đầu owner thấy toàn bộ
 *   bản đồ cài đặt và mục đích của màn này là chứng minh *không còn gì nằm ngoài*.
 * @property labelEn nhãn tiếng Anh (U5 · T2) · @property subEn câu phụ tiếng Anh. Bắt buộc cho cả 10 nhóm — rail là
 *   thứ **đầu tiên** người dùng thấy khi mở Cài đặt, một dòng tiếng Việt lọt vào đây là lỗi nhìn thấy ngay.
 */
enum class SettingsGroup(
    val id: String,
    override val label: String,
    val sub: String,
    override val labelEn: String,
    val subEn: String,
) : Localized {
    HOME(
        "home", "Màn hình chính", "Bố cục và hình nền",
        "Home screen", "Layout and wallpaper",
    ),
    BARS(
        "bars", "Thanh trạng thái & thanh nút", "Chip trên đỉnh và thanh nút xe",
        "Status bar & button bar", "Top chips and the car button bar",
    ),
    DISPLAY(
        "display", "Hiển thị & đơn vị", "Đơn vị đo, sáng/tối và ngôn ngữ",
        "Display & units", "Units, light/dark theme and language",
    ),
    PROFILES(
        "profiles", "Hồ sơ tài xế", "Mỗi hồ sơ giữ bố cục riêng",
        "Driver profiles", "Each profile keeps its own layout",
    ),
    NAV(
        "nav", "Dẫn đường & cụm đồng hồ", "Chỉ đường, biển báo và bong bóng",
        "Navigation & cluster", "Turn-by-turn, speed badge and bubble",
    ),
    CAST(
        "cast", "Chiếu màn lên cụm", "Đưa app lên cụm, chia đôi, tự chiếu",
        "Cluster cast", "Apps on the cluster, split view, autostart",
    ),
    KEYS(
        "keys", "Phím vô-lăng", "Gán nút vật lý cho app hoặc trợ lý",
        "Steering-wheel keys", "Bind physical buttons to apps",
    ),
    CAR(
        "car", "Tiện nghi xe", "Lấy gió trong, ghế, lọc bụi mịn",
        "Car comfort", "Recirculation, seats, air purifier",
    ),
    SYSTEM(
        "system", "Hệ thống & quyền", "Quyền, khởi động, bảo trì, nâng cao",
        "System & permissions", "Permissions, startup, maintenance",
    ),
    ABOUT(
        "about", "Giới thiệu", "Phiên bản, giấy phép và miễn trừ",
        "About", "Version, licence and disclaimer",
    );

    /** [sub] theo [Strings.current] — tự lùi về tiếng Việt nếu bản Anh trống. */
    val displaySub: String get() = Strings.pick(sub, subEn)
}

/**
 * Một MỤC trong màn Cài đặt.
 *
 * @property id mã ổn định của mục.
 * @property group nhóm chứa nó — **đúng một** nhóm (xem [SettingsCatalog]).
 * @property label nhãn cho người đọc.
 * @property prefKey khoá lưu bền mà mục này **sở hữu**, hoặc `null` nếu mục không lưu gì.
 *
 * ⚠ `prefKey` là **tên hậu tố THẬT** đúng như trong mã lưu trữ, không phải tên đẹp. Với khoá theo hồ sơ,
 * `WorkspacePrefs` ghi thành `"<tên hồ sơ>__<hậu tố>"`; ở đây khai **hậu tố** (`"preset"`, `"top_strip"`, …) vì đó là
 * phần bất biến, còn tiền tố thì đổi theo hồ sơ đang dùng. Khoá của ClusterNav ([SettingsGroup.NAV], [CAST], [KEYS],
 * phần lớn [CAR]) nằm ở `Prefs`/`SimpleCastRuntime` nên chúng **không** có tiền tố hồ sơ — khai đúng tên phẳng, và
 * [SettingsCatalog.CLUSTERNAV_KEYS] nói mỗi khoá đó nằm ở **tệp prefs nào**.
 *
 * `null` là trạng thái hợp lệ và có thật: hàng quyền, các nút hành động (chiếu ngay, cứu hộ, lọc ngay, kiểm tra cập
 * nhật…), dòng phiên bản và nút mở bảng vẽ bố cục đều là **việc làm** hoặc **thông tin**, không phải giá trị lưu bền.
 * Nếu bắt mọi mục phải có khoá thì chúng sẽ bị đẩy ra ngoài danh mục — và ra ngoài danh mục nghĩa là ra ngoài tầm
 * kiểm của bài test phủ khoá.
 */
data class SettingsEntry(
    val id: String,
    val group: SettingsGroup,
    override val label: String,
    val prefKey: String? = null,
    /** Nhãn tiếng Anh (U5 · T2) — tham số mặc định ở CUỐI để [prefKey] giữ vị trí thứ 4 dạng positional. */
    override val labelEn: String? = null,
) : Localized
