package com.byd.clusternav.launcher

/**
 * NHÓM trong màn Cài đặt (S1) — thứ tự khai = thứ tự hiện trên rail bên trái.
 *
 * ## Vì sao chia THẾ NÀY
 * Nhóm theo **thứ người dùng đang nghĩ tới**, không theo tệp mã và cũng không theo lớp lưu trữ. Đây là điểm dễ làm
 * sai nhất: nếu chia theo nơi lưu thì [HOME] và [DISPLAY] sẽ dính làm một (cùng nằm trong `WorkspacePrefs`), còn
 * [CAR] lại bị đẩy ra ngoài (nó ở `Prefs` của ClusterNav). Người ngồi trong xe không biết và không cần biết điều đó —
 * họ chỉ nghĩ *"màn chính trông thế nào"* hay *"xe tự làm gì khi nổ máy"*.
 *
 * Hai đường biên đáng nói:
 *  - **[HOME] vs [DISPLAY]**: bố cục/hình nền/chip trả lời *"màn chính trông thế nào"*; đơn vị và sáng/tối là *cách
 *    trình bày số và màu*, đúng ở mọi bố cục ⇒ tách ra để đổi đơn vị không phải đi qua phần bố cục.
 *  - **[PROFILES] đứng riêng dù nó "thuộc" mọi nhóm trên**: hồ sơ quyết định [HOME] và thanh nút, nên nó phải là một
 *    nhóm thấy được — không thể là một dòng chìm trong [HOME], vì khi đó người dùng đổi bố cục mà không biết mình
 *    đang đổi cho hồ sơ nào.
 *
 * @property id mã ổn định (nhật ký/test/lưu chỗ đang chọn). KHÔNG đổi khi sửa [label].
 * @property label tên hiện cho người đọc.
 * @property sub câu phụ nói **nội dung** nhóm — rail phải tự giải thích được, vì đây là lần đầu owner thấy toàn bộ
 *   bản đồ cài đặt và mục đích của S1 là chứng minh *không còn gì nằm ngoài*.
 * @property labelEn nhãn tiếng Anh (U5 · T2) · @property subEn câu phụ tiếng Anh. Bắt buộc cho cả 7 nhóm — rail là
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
        "home", "Màn hình chính", "Bố cục, hình nền, chip thanh trạng thái và thanh nút xe",
        "Home screen", "Layout, wallpaper, status-bar chips and the car button bar",
    ),
    DISPLAY(
        "display", "Hiển thị & đơn vị",
        "Đơn vị đo và giao diện sáng/tối — cách trình bày, không phụ thuộc bố cục",
        "Display & units", "Units of measure and light/dark theme — presentation, independent of layout",
    ),
    PROFILES(
        "profiles", "Hồ sơ tài xế", "Hồ sơ đang dùng, thêm và xoá hồ sơ — mỗi hồ sơ giữ bố cục riêng",
        "Driver profiles", "Active profile, add and remove profiles — each profile keeps its own layout",
    ),
    CAR(
        "car", "Tiện nghi xe", "Việc Kachi tự làm với XE khi nổ máy, không phải với màn hình",
        "Car comfort", "What Kachi does to the CAR on engine start, not to the screen",
    ),
    SYSTEM(
        "system", "Hệ thống & quyền", "Điều kiện để launcher chạy đúng: quyền còn thiếu và tự mở khi nổ máy",
        "System & permissions", "What the launcher needs to run: missing permissions and auto-start on engine start",
    ),
    CLUSTERNAV(
        "clusternav", "Dẫn đường · Cụm · Phím", "Mở màn ClusterNav — dẫn đường, chiếu cụm, phím vô-lăng",
        "Navigation · Cluster · Keys", "Open the ClusterNav screen — navigation, cluster casting, steering-wheel keys",
    ),
    ABOUT(
        "about", "Giới thiệu", "Phiên bản, tên gói và giấy phép",
        "About", "Version, package name and licence",
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
 * phần bất biến, còn tiền tố thì đổi theo hồ sơ đang dùng. Khoá của mục [SettingsGroup.CAR] nằm ở `Prefs` của
 * ClusterNav nên nó **không** có tiền tố hồ sơ — khai đúng tên phẳng.
 *
 * `null` là trạng thái hợp lệ và có thật: hàng quyền, dòng mở màn ClusterNav, dòng phiên bản và nút mở bảng vẽ bố cục
 * đều là **việc làm** hoặc **thông tin**, không phải giá trị lưu bền. Nếu bắt mọi mục phải có khoá thì bốn thứ đó sẽ
 * bị đẩy ra ngoài danh mục — và ra ngoài danh mục nghĩa là ra ngoài tầm kiểm của bài test phủ khoá.
 */
data class SettingsEntry(
    val id: String,
    val group: SettingsGroup,
    override val label: String,
    val prefKey: String? = null,
    /** Nhãn tiếng Anh (U5 · T2) — tham số mặc định ở CUỐI để [prefKey] giữ vị trí thứ 4 dạng positional. */
    override val labelEn: String? = null,
) : Localized

/**
 * NGUỒN DUY NHẤT cho *"cấu hình nào thuộc nhóm nào"* (S1 · §4.3). Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm
 * off-car.
 *
 * ## Bệnh nó chữa
 * Yêu cầu của owner — *"không có cấu hình, cài đặt nào nằm lẻ tẻ khắp nơi"* — là một phát biểu **kiểm được**, nhưng
 * chỉ khi có danh sách đầy đủ để đối chiếu. Trước S1 thì không có, và [ĐO] kiểm kê ở §2 của spec tìm ra **hai khoá
 * lưu bền không có đường nào tới từ tay người dùng** (`theme_mode`, `launcher_autostart`): cả hai đều có enum ở
 * `:core`, có đường lưu bền, có test — nhưng không ai chạm tới được. Đúng họ lỗi RW0 vừa vá, và là bằng chứng thứ hai
 * cho luật **"vẽ được" ≠ "đặt được"**: nếu chỉ đếm mã và test thì hai khoá đó trông như đã xong.
 *
 * Vì vậy lớp này không phải bảng tra cho tầng UI, mà là **chỗ để máy kiểm thay cho mắt người**: [orphans] trả lời
 * *"có khoá nào chưa ai nhận không"*, [duplicatedKeys] trả lời *"có khoá nào hai nhóm cùng nhận không"*. Lần sau ai
 * thêm một cấu hình mà quên gom, bộ test nói ngay — không đợi owner phát hiện trên xe.
 *
 * ## Vì sao cần [NOT_SETTINGS]
 * Không có nó thì bài test chỉ biết *"khoá này không thuộc nhóm nào"* mà **không phân biệt được** *"quên gom"* với
 * *"cố ý không gom"*. Danh sách này bắt phải viết **lý do tại chỗ** cho từng khoá bị loại — tức là một quyết định
 * được ghi lại, không phải một chỗ để làm im bài test.
 */
object SettingsCatalog {

    /**
     * Tiền tố khoá nội dung ô (`slot_0`, `slot_1`, …).
     *
     * ⚠ [ĐO] spec §2 tự nhận là kiểm kê *"mọi khoá lưu bền của launcher"* nhưng **thiếu họ khoá này** —
     * `WorkspacePrefs.save` ghi `key("slot_$it")` cho `0 until WorkspaceState.SLOT_CAP`. Nó lọt khỏi kiểm kê vì đây là
     * khoá **dựng động** trong một vòng lặp, không phải hằng `K_*` như các khoá khác. Đó cũng là lý do [orphans] tha
     * theo **tiền tố** thay vì chỉ theo tên đủ: bộ quét mã nguồn có thể trích ra dạng chưa nội suy (`slot_$it`), và
     * số ô còn nới thêm được ([WorkspaceState.SLOT_CAP] đã đi 4 → 6) — bắt cứng danh sách tên thì bản sau lại đỏ oan.
     */
    const val SLOT_KEY_PREFIX = "slot_"

    /** Thứ tự hiện ra = thứ tự khai trong [SettingsGroup]. */
    val GROUPS: List<SettingsGroup> = SettingsGroup.values().toList()

    /**
     * MỌI mục cài đặt của launcher. Thứ tự trong nhóm = thứ tự hiện ra.
     *
     * Trong nhóm [SettingsGroup.HOME] thứ tự đi từ **khung** ra **nội dung** (bố cục → hình nền → chip → thanh nút):
     * chọn bố cục trước thì các lựa chọn sau mới có nghĩa.
     */
    val ENTRIES: List<SettingsEntry> = listOf(
        // ── Màn hình chính ──
        SettingsEntry("home_preset", SettingsGroup.HOME, "Bố cục sẵn", "preset", "Preset layout"),
        SettingsEntry("home_grid", SettingsGroup.HOME, "Bố cục tự vẽ", "grid_layout", "Custom layout"),
        // Không lưu gì: đây là NÚT mở bảng vẽ. Bố cục vẽ ra thì lưu ở "home_grid" phía trên — một khoá, một chủ.
        SettingsEntry("home_grid_editor", SettingsGroup.HOME, "Vẽ bố cục riêng…", labelEn = "Draw your own layout…"),
        SettingsEntry("home_wallpaper", SettingsGroup.HOME, "Hình nền & trình chiếu", "wallpaper_prefs", "Wallpaper & slideshow"),
        SettingsEntry("home_top_strip", SettingsGroup.HOME, "Chip thanh trạng thái", "top_strip", "Status-bar chips"),
        // Viền TRƯỚC danh sách nút: thứ tự khai ở đây LÀ thứ tự hiện ra, và mục "nút trên thanh" là lưới 187 ô. Khai
        // ngược lại thì muốn đổi viền phải cuộn qua hết 187 ô — thứ tự danh mục phải là thứ tự dùng được, không chỉ
        // là thứ tự nghe hợp lý khi đọc danh sách.
        SettingsEntry("home_dock_edge", SettingsGroup.HOME, "Viền đặt thanh nút", "dock_edge", "Button bar edge"),
        SettingsEntry("home_dock_items", SettingsGroup.HOME, "Nút trên thanh nút xe", "dock_enabled", "Buttons on the car bar"),

        // ── Hiển thị & đơn vị ──
        SettingsEntry("display_units", SettingsGroup.DISPLAY, "Đơn vị hiển thị", "unit_prefs", "Display units"),
        // [ĐO] §2: khoá này lưu bền, có enum + có đường ghi, nhưng TRƯỚC S1 không có nút nào chạm tới.
        SettingsEntry("display_theme", SettingsGroup.DISPLAY, "Giao diện sáng/tối", "theme_mode", "Light / dark theme"),
        // U5·T3 — NGÔN NGỮ. ⚠ Khoá `lang` KHÔNG nằm trong tệp `kachi_workspace` mà trong tệp lưu ngôn ngữ đã có của
        // ClusterNav (`clusternav_lang`, `com.byd.clusternav.Lang`) — cố ý, để một APK chỉ có MỘT công tắc ngôn ngữ
        // thay vì hai cái lệch nhau; lập luận đầy đủ ở KDoc `WorkspacePrefs.langMode`.
        //
        // Hệ quả về phép kiểm: bộ quét của `SettingsCoverageContractTest` KHÔNG thấy khoá này (gốc quét cố ý không
        // gồm tệp của ClusterNav — kéo vào là biến bài R2 thành bài kiểm ClusterNav). Nên nó được canh bằng một bài
        // RIÊNG đọc thẳng hằng trong `Lang.kt` (`LauncherI18nContractTest`), đúng khuôn đã dùng cho
        // `recirc_on_start_enabled` ở `Prefs.kt` — cùng tình huống: khoá thật, nằm ngoài tệp chính.
        SettingsEntry("display_lang", SettingsGroup.DISPLAY, "Ngôn ngữ", "lang", "Language"),

        // ── Hồ sơ tài xế ──
        SettingsEntry("profiles_list", SettingsGroup.PROFILES, "Danh sách hồ sơ", "profiles", "Profile list"),
        SettingsEntry("profiles_active", SettingsGroup.PROFILES, "Hồ sơ đang dùng", "active_profile", "Active profile"),

        // ── Tiện nghi xe ──
        // ⚠ Tên khoá THẬT là "recirc_on_start_enabled" (Prefs.K_RECIRC_ON_START), KHÁC tên "recirc_on_start" mà spec
        // §2 ghi. Lấy theo mã nguồn, vì bài test phủ khoá đối chiếu với mã chứ không với spec.
        SettingsEntry(
            "car_recirc_on_start", SettingsGroup.CAR, "Tự lấy gió trong khi nổ máy", "recirc_on_start_enabled",
            "Recirculation on engine start",
        ),

        // ── Hệ thống & quyền ──
        // Không lưu gì: hàng quyền chỉ ĐỌC trạng thái thật rồi tự xin lại (xem [LauncherRequirements]).
        SettingsEntry("system_permissions", SettingsGroup.SYSTEM, "Quyền còn thiếu", labelEn = "Missing permissions"),
        // [ĐO] §2: khoá thứ hai không có đường tới trước S1 — chỉ được đọc/ghi trong mã.
        SettingsEntry("system_autostart", SettingsGroup.SYSTEM, "Tự mở khi nổ máy", "launcher_autostart", "Auto-start on engine start"),

        // ── Dẫn đường · Cụm · Phím ──
        // R5: KHÔNG gom cấu hình của ClusterNav vào đây, chỉ dẫn sang màn cũ (màn đó đang niêm phong).
        SettingsEntry("clusternav_open", SettingsGroup.CLUSTERNAV, "Mở màn ClusterNav", labelEn = "Open ClusterNav"),

        // ── Giới thiệu ──
        SettingsEntry("about_version", SettingsGroup.ABOUT, "Phiên bản và giấy phép", labelEn = "Version and licence"),
    )

    /**
     * Khoá lưu bền **cố ý KHÔNG phải cấu hình**, kèm lý do tại chỗ → lý do.
     *
     * Có danh sách này thì bài test mới phân biệt được *"quên gom"* với *"cố ý không gom"*. Mỗi dòng ở đây là một
     * quyết định phải giải thích được, nên lý do là bắt buộc chứ không phải chú thích cho đẹp.
     *
     * ## ⚠⚠ Hai họ khoá dưới đây là TRẠNG THÁI MÁY, và trước 2026-09-11 chúng không được khai ở đâu cả
     * [ĐO] lượt soát: launcher lưu bền ở **ba** chỗ, không phải một — ngoài `WorkspacePrefs` (tệp `kachi_workspace`)
     * còn `FreeformSeedStore` (tệp **thứ hai** `clusternav_state`, khoá `freeform_state`) và bốn khoá của
     * `Settings.Global`/`Settings.Secure` do vòng gieo cờ + vòng kiểm quyền ghi. Chúng đúng là **không** phải dòng
     * cài đặt, nhưng vì **không ai khai lý do** nên phép kiểm phủ khoá chỉ soi đúng một tệp ⇒ tính năng sau lưu vào
     * tệp prefs **thứ ba** sẽ xanh im lặng. Khai ra ở đây là để bộ quét mở rộng được mà không đỏ oan, và để lần sau
     * người thêm khoá phải trả lời câu *"đây là cấu hình hay là trạng thái máy"* bằng chữ.
     */
    val NOT_SETTINGS: Map<String, String> = buildMap {
        put(
            "recent_apps",
            "trạng thái dùng, không phải cấu hình — là lịch sử mở app, người dùng không đặt và không sửa; " +
                "hiện nó ra như một dòng cài đặt thì chỉ thêm thứ để đọc mà không có gì để chọn",
        )
        // Nội dung từng ô: đặt bằng cách chạm thẳng vào ô trên màn chính (ngăn kéo / kéo-thả). Đây là thao tác TRỰC
        // TIẾP trên vật đang thấy, không phải một dòng trong Cài đặt — bắt người dùng vào Cài đặt để chọn "ô số 3
        // chứa gì" thì tệ hơn hẳn cách đang có.
        for (i in 0 until WorkspaceState.SLOT_CAP) {
            put(
                "$SLOT_KEY_PREFIX$i",
                "nội dung ô ${i + 1} — đặt trực tiếp trên màn chính bằng ngăn kéo/kéo-thả, không phải một dòng cài đặt",
            )
        }
        put(
            "freeform_state",
            "trạng thái máy, không phải cấu hình — dấu mốc của vòng gieo cờ cửa sổ tự do (một-nơi-ghi-duy-nhất, " +
                "`FreeformSeedPolicy`). Nó ghi nhớ *đã gieo tới đâu* và *người dùng đã chủ động gỡ chưa* để launcher " +
                "không âm thầm gieo lại; dùng CHUNG với đường chiếu-cụm nên nó là điểm phối hợp giữa hai nhánh, " +
                "không phải một lựa chọn của người dùng. Bày nó ra như một dòng cài đặt thì người dùng sửa được một " +
                "con số mà họ không có cách nào hiểu, và sửa sai thì mất đường app-vào-ô",
        )
        put(
            "enable_freeform_support",
            "trạng thái máy (`Settings.Global`), không phải cấu hình — cờ boot của hệ thống, điều kiện để app vào " +
                "được ô. Hàng 'Quyền còn thiếu' đã ĐỌC và BÁO nó (`LauncherRequirements.FREEFORM`), còn việc bật thì " +
                "do đường khởi động làm, vì trạng thái bền chỉ được có MỘT nơi ghi",
        )
        put(
            "force_resizable_activities",
            "trạng thái máy (`Settings.Global`), không phải cấu hình — gieo CẶP với khoá trên trong cùng một lượt " +
                "`FreeformSeedPolicy.SEED_CMDS` (thứ tự có ý nghĩa). Tách ra thành lựa chọn riêng thì bật một nửa là " +
                "một trạng thái vô nghĩa mà người dùng dựng được",
        )
        put(
            "enabled_accessibility_services",
            "trạng thái máy (`Settings.Secure`) và là danh sách DÙNG CHUNG với mọi app khác — vòng kiểm quyền chỉ " +
                "**thêm** component của Kachi vào (đọc-sửa-ghi), không bao giờ ghi đè. Cho người dùng đặt giá trị " +
                "này từ Cài đặt của Kachi là mở đường tắt trợ năng của app khác, kể cả của người khuyết tật đang dùng",
        )
        put(
            "accessibility_enabled",
            "trạng thái máy (`Settings.Secure`) — cờ trợ năng toàn hệ thống, và [ĐO] 2026-09-11 chính hệ thống đưa " +
                "nó về 0 khi tiến trình chết. Một lựa chọn mà máy tự đổi sau lưng thì không phải cấu hình; vòng kiểm " +
                "quyền tự bật lại mỗi lần mở launcher",
        )
    }

    /**
     * Tệp SharedPreferences mà **phía launcher** được phép ghi → lý do.
     *
     * [ĐO] lượt soát 2026-09-11: bộ quét phủ khoá chỉ đọc `WorkspacePrefs.kt`, nên tệp prefs **thứ hai**
     * (`clusternav_state`) đã tồn tại nhiều phiên mà không phép kiểm nào biết. Danh sách này là chỗ để bộ quét trả
     * lời câu hỏi *"có tệp prefs thứ ba nào vừa xuất hiện không"* — câu hỏi mà đọc mắt không trả lời được, vì tên tệp
     * nằm rải trong nhiều lớp và thường là một hằng chứ không phải chuỗi ngay tại chỗ gọi.
     */
    val PREFS_FILES: Map<String, String> = mapOf(
        "kachi_workspace" to
            "tệp cấu hình CHÍNH của launcher — mọi khoá trong nó phải thuộc đúng một nhóm của màn Cài đặt",
        "clusternav_state" to
            "dấu mốc gieo cờ cửa sổ tự do, dùng CHUNG với đường chiếu-cụm (cố ý không đổi tên: nó là trạng thái " +
                "đã nằm trên đĩa của máy đang chạy). Chỉ chứa `freeform_state`",
    )

    /**
     * Khoá lưu bền mà **thanh trên** được phép chạm — §4.5, sau chỉ thị của owner *"không để cấu hình nằm lỉ tỉ"*.
     *
     * ## Vì sao danh sách này ở `:core` chứ không là một chú thích trong `KachiTopStrip`
     * [ĐO] lượt soát 2026-09-11 tìm ra bề mặt cấu hình **thứ ba** trên thanh trên: pill **"Thanh"** →
     * `HomeViewModel.cycleDockEdge()` → ghi bền `dock_edge`. Nó tồn tại nhiều phiên mà không bài test nào phản đối,
     * vì luật *"thanh trên chỉ được có một cửa vào cấu hình"* chỉ nằm trong văn xuôi của spec. Văn xuôi không chặn
     * được ai. Đưa giới hạn thành **dữ liệu** thì `TopStripSurfaceContractTest` kiểm được bằng máy, và ai muốn nới
     * phải sửa `:core` — tức phải viết lý do ở đây, cạnh hai lý do dưới.
     *
     * Chỉ hai khoá, và cả hai đều **không phải để cấu hình**:
     *  • `preset` — 5 nút bố cục là cách đổi bố cục **hằng ngày**; bắt mở Cài đặt để đổi là làm launcher tệ hơn, và
     *    nó là bề mặt owner đã duyệt từ prototype. Cả hai bề mặt đi **cùng một** intent `setPreset`.
     *  • `active_profile` — avatar trả lời *"đang ở hồ sơ nào"*, thông tin phải thấy **liên tục**; chạm để đổi là hệ
     *    quả của việc đã hiện nó ra. Việc *tạo/xoá* hồ sơ thì đã chuyển hẳn vào Cài đặt.
     *
     * `dock_edge` **không** ở đây: nó là lựa chọn đặt-một-lần, và xoay vòng 4 viền còn là hình dạng sai (bấm ba lần
     * mới tới viền mình muốn, ô đang sáng thì không nói gì). Cài đặt → Màn hình chính đặt thẳng từng viền.
     */
    val TOP_STRIP_ALLOWED_KEYS: Set<String> = setOf("preset", "active_profile")

    /** Mục của một nhóm, theo thứ tự khai. */
    fun entriesOf(group: SettingsGroup): List<SettingsEntry> = ENTRIES.filter { it.group == group }

    /** Nhóm sở hữu [prefKey], hoặc `null` nếu chưa ai nhận (xem [orphans] để biết đó là lỗi hay cố ý). */
    fun groupOf(prefKey: String): SettingsGroup? = ENTRIES.firstOrNull { it.prefKey == prefKey }?.group

    /** Mục sở hữu [prefKey], hoặc `null`. */
    fun entryOf(prefKey: String): SettingsEntry? = ENTRIES.firstOrNull { it.prefKey == prefKey }

    /**
     * Khoá **mồ côi**: có trong [keys] mà không nhóm nào nhận và cũng không được [NOT_SETTINGS] tha ⇒ *"quên gom"*.
     *
     * Đây là phép kiểm của R2. Họ khoá `slot_*` được tha theo tiền tố — lý do ở [SLOT_KEY_PREFIX].
     */
    fun orphans(keys: Set<String>): Set<String> =
        keys.filterTo(mutableSetOf()) { key ->
            groupOf(key) == null && key !in NOT_SETTINGS && !key.startsWith(SLOT_KEY_PREFIX)
        }

    /** Khoá bị **hai mục trở lên** cùng nhận ⇒ hai nơi sửa một giá trị (bẫy hai-bản-sao). Rỗng là đúng. */
    fun duplicatedKeys(): List<String> =
        ENTRIES.mapNotNull { it.prefKey }
            .groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys.sorted()

    /**
     * ⚠⚠ **KHỐI NÀY PHẢI NẰM CUỐI THÂN `object`.** Thân `object` chạy **theo thứ tự khai**: `init` đặt phía trên
     * [ENTRIES] thì lúc `require` đọc [ENTRIES] nó còn `null`, và cả gói test nổ `ExceptionInInitializerError` thay vì
     * đỏ ở một bài. Dự án đã trả giá đúng chỗ này một lần — xem KDoc `TopStripConfig.BUILT_IN` ([ĐO] 27 bài đỏ vì
     * `DEFAULT` dựng trước khi `BUILT_IN` có giá trị). [SLOT_KEY_PREFIX] là `const` nên nó miễn nhiễm, các `val` thì
     * không.
     *
     * Chốt ngay lúc nạp lớp thay vì chỉ dựa vào bài test: một danh mục tự mâu thuẫn (khoá hai chủ, nhóm rỗng) sẽ làm
     * màn Cài đặt hiện sai hoặc hiện thiếu, và cái sai đó **im lặng**.
     */
    init {
        require(duplicatedKeys().isEmpty()) {
            "một khoá chỉ được thuộc ĐÚNG một mục — khoá hai chủ: ${duplicatedKeys()}"
        }
        val emptyGroups = GROUPS.filter { entriesOf(it).isEmpty() }
        require(emptyGroups.isEmpty()) {
            "nhóm rỗng thì rail hiện ra một trang trắng — nhóm rỗng: ${emptyGroups.map { it.id }}"
        }
        val dupEntryIds = ENTRIES.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(dupEntryIds.isEmpty()) { "mã mục bị trùng: $dupEntryIds" }
        val dupGroupIds = GROUPS.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(dupGroupIds.isEmpty()) { "mã nhóm bị trùng: $dupGroupIds" }
        require(ENTRIES.all { it.id.isNotBlank() && it.label.isNotBlank() }) {
            "mọi mục phải có mã và nhãn: ${ENTRIES.filter { it.id.isBlank() || it.label.isBlank() }}"
        }
        require(ENTRIES.all { it.prefKey == null || it.prefKey.isNotBlank() }) {
            "khoá lưu bền rỗng thì không phân biệt được với 'không lưu gì' — dùng null"
        }
        require(GROUPS.all { it.id.isNotBlank() && it.label.isNotBlank() && it.sub.isNotBlank() }) {
            "mọi nhóm phải có mã, nhãn và câu phụ (rail phải tự giải thích được)"
        }
        // U5 · T2 — nhãn tiếng Anh là bắt buộc cho CẢ nhóm lẫn mục. Chốt lúc nạp lớp vì thiếu nó thì màn Cài đặt
        // tiếng Anh có một dòng tiếng Việt: sai **im lặng**, và chỉ người dùng English gặp.
        require(GROUPS.all { it.labelEn.isNotBlank() && it.subEn.isNotBlank() }) {
            "mọi nhóm phải có nhãn + câu phụ tiếng Anh: " +
                GROUPS.filter { it.labelEn.isBlank() || it.subEn.isBlank() }.map { it.id }
        }
        val entriesNoEn = ENTRIES.filter { it.labelEn.isNullOrBlank() }.map { it.id }
        require(entriesNoEn.isEmpty()) { "mọi mục phải có nhãn tiếng Anh (labelEn): $entriesNoEn" }
        // Một khoá vừa có chủ vừa nằm trong danh sách loại = hai câu trả lời trái nhau cho cùng câu hỏi.
        val bothWays = NOT_SETTINGS.keys.filter { groupOf(it) != null }
        require(bothWays.isEmpty()) {
            "khoá không thể vừa thuộc một nhóm vừa bị loại khỏi cài đặt: $bothWays"
        }
        require(NOT_SETTINGS.values.all { it.isNotBlank() }) {
            "mỗi khoá bị loại phải kèm LÝ DO — không thì danh sách này thành chỗ làm im bài test"
        }
        // Danh sách "thanh trên được chạm khoá nào" phải trỏ vào khoá THẬT: đổi tên khoá mà quên sửa ở đây thì bài
        // canh thanh trên tiếp tục xanh trong khi nó đang cho phép một khoá không còn tồn tại.
        val unknownTopStrip = TOP_STRIP_ALLOWED_KEYS.filter { entryOf(it) == null }
        require(unknownTopStrip.isEmpty()) {
            "khoá cho phép ở thanh trên không thuộc mục nào của danh mục: $unknownTopStrip"
        }
        require(PREFS_FILES.values.all { it.isNotBlank() }) {
            "mỗi tệp prefs phải kèm LÝ DO — đây là chỗ trả lời 'tệp thứ ba này ở đâu ra'"
        }
    }
}
