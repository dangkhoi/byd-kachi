package com.byd.clusternav.launcher

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
     * MỌI mục cài đặt. Thứ tự trong nhóm = thứ tự hiện ra; thân dữ liệu ở [SettingsCatalogEntries] (trần 500 dòng).
     *
     * Trong nhóm [SettingsGroup.HOME] thứ tự đi từ **cả bộ** (cảnh) ra **từng phần**, rồi từ **khung** ra **nội
     * dung**: chọn bố cục trước thì các lựa chọn sau mới có nghĩa.
     */
    val ENTRIES: List<SettingsEntry> = SettingsCatalogEntries.ALL

    /**
     * Khoá của ClusterNav có mặt trên UI Kachi → **tệp prefs** chứa nó (IA v2 · §4.3).
     *
     * Đây là lưới canh cho phần mà `SettingsCoverageContractTest` cố ý không quét (xem KDoc
     * [SettingsCatalogClusterNav]): `ClusterNavKeysContractTest` ở `:app` đòi mỗi khoá ở đây có mặt **nguyên văn**
     * trong tệp nguồn khai nó ⇒ gõ sai một ký tự là đỏ, thay vì lặng lẽ ghi vào một khoá không runtime nào đọc.
     */
    val CLUSTERNAV_KEYS: Map<String, String> = SettingsCatalogClusterNav.KEYS

    /** Tệp prefs của phía ClusterNav → lý do nó tồn tại riêng. Giá trị của [CLUSTERNAV_KEYS] phải nằm trong đây. */
    val CLUSTERNAV_PREFS_FILES: Map<String, String> = SettingsCatalogClusterNav.PREFS_FILES

    /** Khoá ĐI KÈM (một điều khiển ghi hai khoá) → mã mục đặt nó. Xem [SettingsCatalogClusterNav.COMPANION_KEYS]. */
    val CLUSTERNAV_COMPANION_KEYS: Map<String, String> = SettingsCatalogClusterNav.COMPANION_KEYS

    /** Khoá của ClusterNav **cố ý không lên UI** → lý do. Cùng vai trò [NOT_SETTINGS], cho phía ClusterNav. */
    val CLUSTERNAV_HIDDEN_KEYS: Map<String, String> = SettingsCatalogClusterNav.HIDDEN_KEYS

    /** Tiền tố khoá dựng động phía ClusterNav → lý do (nay chỉ có `seat_level_`). */
    val CLUSTERNAV_DYNAMIC_KEY_PREFIXES: Map<String, String> = SettingsCatalogClusterNav.DYNAMIC_KEY_PREFIXES

    /**
     * Trần ký tự cho [SettingsGroup.sub]/[SettingsGroup.subEn] — **ràng buộc HÌNH, không phải văn phong**.
     *
     * [ĐO ảnh 2026-09-12] ô rail cho câu phụ đúng **2 dòng**; 2/7 câu của IA v1 bị cắt cụt bằng "…" (R-UI b). Ghim
     * số ở đây để bản sau viết dài ra thì đỏ ngay, chứ không đợi ai chụp lại màn hình mới thấy.
     */
    const val GROUP_SUB_MAX = 60

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
        // 2026-09-15 (HOME-alias): marker "người dùng ĐÃ bấm Đặt-làm-màn-hình-chính thành công" — lối vào HOME là
        // activity-alias tắt sẵn (để BYD GUI-install không chặn), KachiAutostart đọc marker để bật alias + set-home
        // lại sau nâng cấp. Là lựa chọn ĐÃ BÀY TỎ được ghi lại, không phải một công tắc để bật/tắt trong Cài đặt
        // (công tắc thật là nút "Đặt làm màn hình chính" ở nhóm Hệ thống; `keep_home_on_boot` mới là cấu hình).
        put(
            "home_chosen",
            "dấu vết lựa chọn đã bày tỏ (đã bấm Đặt làm màn hình chính), không phải cấu hình — dùng để khôi phục " +
                "HOME sau nâng cấp; lên UI thì chỉ có một ô không có gì để chọn",
        )
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
        put(
            "captest_results",
            "trạng thái ĐO, không phải cấu hình — nhật ký công cụ 'kiểm tra từng nút' (`CapTestStore`, tệp " +
                "`kachi_captest`): id nút → OK/Không OK + thời điểm. Là kết quả soát của CHIẾC XE này, người dùng " +
                "không 'đặt' nó như một lựa chọn; bày ra như một dòng cài đặt thì vô nghĩa. Theo máy như OTA/chẩn đoán",
        )
        put(
            "sherpa_model_id",
            "trạng thái THEO-MÁY, không phải cấu hình theo hồ sơ — là mã mô hình ASR đã TẢI VỀ máy NÀY (`VoiceModelStore`, " +
                "tệp `kachi_voice`). Việc chọn/tải mô hình làm TRỰC TIẾP trên màn Cài đặt giọng nói (`VoiceModelSettings`, " +
                "có nút tải + tiến độ), không phải một dòng bật/tắt trong nhóm cài đặt; bày nó ra như một dòng thường thì " +
                "người dùng đổi được sang mã mô hình chưa tải về và làm hỏng đường nhận giọng. Theo máy như OTA/chẩn đoán, " +
                "KHÔNG đi theo hồ sơ (chép hồ sơ sang xe khác không mang theo tệp mô hình 78 MB)",
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
        "kachi_test_bridge" to
            "T-BRIDGE — chỉ chứa `test_bridge_until`, công tắc PHIÊN của cầu kiểm thử qua adb. Cố ý ĐỂ RIÊNG khỏi " +
                "`kachi_workspace`: tệp đó đi theo hồ sơ (chụp–áp, nhân bản, xoá hồ sơ) còn cái này thì **không " +
                "được** đi đâu cả — một cửa mở-60-phút mà bị chép sang hồ sơ khác, hoặc sống lại qua một lượt áp " +
                "ảnh chụp, là đúng thứ mà cửa sổ thời gian sinh ra để chặn (`TestBridgeWindow`)",
        "kachi_captest" to
            "Kiểm tra từng nút (owner 2026-09-15) — chỉ chứa `captest_results`, nhật ký OK/Không OK khi soát cạn " +
                "trên xe. Cố ý ĐỂ RIÊNG khỏi `kachi_workspace`: nó là trạng thái ĐO của chiếc xe này, KHÔNG đi theo " +
                "hồ sơ (chép hồ sơ sang xe khác không mang theo kết quả soát). Cùng lẽ với `kachi_test_bridge`/`kachi_voice`",
        "kachi_voice" to
            "Giọng nói — chỉ chứa `sherpa_model_id` (mã mô hình ASR đã tải về máy NÀY, xem `VoiceModelStore`). Cố ý ĐỂ " +
                "RIÊNG khỏi `kachi_workspace`: nó là trạng thái THEO-MÁY (mô hình 78 MB nằm trên đĩa máy này), KHÔNG đi " +
                "theo hồ sơ — chép/áp một hồ sơ sang xe khác không được kéo theo lựa chọn mô hình vì tệp mô hình có thể " +
                "chưa tải ở xe kia. Cùng lẽ với `kachi_test_bridge`: tệp theo-máy thì không nằm trong tệp theo-hồ-sơ",
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
     * Còn **đúng một** khoá, và nó không phải để cấu hình:
     *  • `active_profile` — chip hồ sơ trả lời *"đang ở hồ sơ nào"*, thông tin phải thấy **liên tục**; chạm để mở bộ
     *    chọn là hệ quả của việc đã hiện nó ra. Việc *tạo/xoá* hồ sơ thì đã chuyển hẳn vào Cài đặt.
     *
     * ## ⚠ S4 · R7 — `preset` đã RỜI khỏi đây (2026-09-14)
     * Trước S4 thanh trên có 5 nút bố cục sẵn, và lý do giữ chúng là *"đổi bố cục hằng ngày"*. Owner bỏ chúng vì từ
     * S4 một **hồ sơ** đã mang cả bố cục (R3) ⇒ đổi bố cục hằng ngày = đổi hồ sơ, và hai bề mặt cùng làm một việc là
     * đúng thứ IA v2 đang dọn. Đường chọn bố cục sẵn nay chỉ còn ở *Cài đặt › Màn hình chính*.
     *
     * `dock_edge` **không** ở đây: nó là lựa chọn đặt-một-lần, và xoay vòng 4 viền còn là hình dạng sai (bấm ba lần
     * mới tới viền mình muốn, ô đang sáng thì không nói gì). Cài đặt → Màn hình chính đặt thẳng từng viền.
     */
    val TOP_STRIP_ALLOWED_KEYS: Set<String> = setOf("active_profile")

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
        // ── IA v2 · bảng khoá của ClusterNav ─────────────────────────────────────────────────────
        // Câu phụ dài hơn ô rail thì bị cắt cụt bằng "…" — mà phần bị cắt chính là phần "nhóm này chứa gì". Chốt
        // lúc nạp lớp vì đây là lỗi chỉ thấy được bằng MẮT trên máy thật; bài test và trình biên dịch đều im.
        val subTooLong = GROUPS.filter { it.sub.length > GROUP_SUB_MAX || it.subEn.length > GROUP_SUB_MAX }
        require(subTooLong.isEmpty()) {
            "câu phụ rail dài quá $GROUP_SUB_MAX ký tự ⇒ bị cắt '…': " +
                subTooLong.map { "${it.id}(${it.sub.length}/${it.subEn.length})" }
        }
        val unknownFile = CLUSTERNAV_KEYS.filterValues { it !in CLUSTERNAV_PREFS_FILES }
        require(unknownFile.isEmpty()) {
            "khoá ClusterNav trỏ vào tệp prefs chưa khai lý do: $unknownFile"
        }
        // Mỗi khoá ClusterNav phải có CHỦ: hoặc là khoá của một mục, hoặc là khoá ĐI KÈM của một mục có thật. Không
        // có phép này thì thêm một khoá vào bảng mà quên dựng mục là chuyện xảy ra im lặng — đúng bệnh mà cả danh
        // mục này sinh ra để chữa, chỉ là ở phía ClusterNav.
        val ownerless = CLUSTERNAV_KEYS.keys.filter { key ->
            if (groupOf(key) != null) return@filter false
            val ownerId = CLUSTERNAV_COMPANION_KEYS[key]
            ownerId == null || ENTRIES.none { it.id == ownerId }
        }
        require(ownerless.isEmpty()) {
            "khoá ClusterNav không mục nào nhận (và cũng không khai là khoá đi kèm của một mục có thật): $ownerless"
        }
        // Khoá đi kèm KHÔNG được đồng thời là khoá chính của một mục — hai câu trả lời trái nhau cho "ai ghi nó".
        val companionAlsoOwned = CLUSTERNAV_COMPANION_KEYS.keys.filter { groupOf(it) != null }
        require(companionAlsoOwned.isEmpty()) {
            "khoá vừa là khoá chính vừa là khoá đi kèm: $companionAlsoOwned"
        }
        val hiddenAlsoShown = CLUSTERNAV_HIDDEN_KEYS.keys.filter { it in CLUSTERNAV_KEYS || groupOf(it) != null }
        require(hiddenAlsoShown.isEmpty()) {
            "khoá không thể vừa có UI vừa 'cố ý không có UI': $hiddenAlsoShown"
        }
        require(CLUSTERNAV_HIDDEN_KEYS.values.all { it.isNotBlank() }) {
            "mỗi khoá ẩn phải kèm LÝ DO — không thì danh sách này thành chỗ làm im bài test"
        }
        require(CLUSTERNAV_PREFS_FILES.values.all { it.isNotBlank() }) {
            "mỗi tệp prefs của ClusterNav phải kèm lý do nó tồn tại riêng"
        }
    }
}
