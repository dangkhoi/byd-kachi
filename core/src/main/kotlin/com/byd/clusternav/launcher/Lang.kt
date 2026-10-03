package com.byd.clusternav.launcher

/**
 * ═══ U5 · T2 — NGÔN NGỮ CHO NHÃN Ở `:core` ════════════════════════════════════════════════════════════════════
 *
 * Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm được off-car. Spec `docs/specs/kachi-i18n-and-light-theme.html` §3.1.
 *
 * ## Vì sao `:core` KHÔNG dùng `R.string` như phần còn lại của Android
 * [ĐO] spec §1: **644/989 chuỗi tiếng Việt của launcher nằm ở `:core`**, và chúng là **DỮ LIỆU** — nhãn gắn vào từng
 * mã khả năng ([TelemetrySpec] · [ControlDef] · [CapabilityGroup] · [SettingsEntry] · [WidgetDef] · [ActionMacro]),
 * không phải chữ trang trí trên màn. Tra `R.string` đòi `Context`, tức phá tính thuần của `:core` và bắt **1300+ bài
 * test của `:core`** phải có Android. Nên tiếng Anh của chúng cũng là **dữ liệu nằm cạnh**, đúng khuôn
 * [TelemetrySpec.short] của RW0.
 *
 * ## Hai cơ chế, mỗi cơ chế cho một loại chuỗi — cố ý, không phải bất nhất
 *  1. **Nhãn là DỮ LIỆU của một dòng registry** ⇒ tham số `labelEn`/`subEn`/`shortEn` **mặc định `null`** cạnh nhãn
 *     Việt, đọc qua [Localized.displayLabel]. Không thể viết `when` cho 123 dòng dữ liệu.
 *  2. **Chuỗi GHÉP LÚC CHẠY** (`"2 cảnh báo"`, `"3 bánh non"`, nhãn của enum sinh ra bằng `when`) ⇒ gọi thẳng
 *     [Strings.t] tại chỗ. Không có "dòng" nào để treo field vào, và bản dịch nằm ngay cạnh bản gốc thì đọc code là
 *     thấy cả hai thứ tiếng — cùng triết lý với `Lang.kt` của ClusterNav ở `:app` (*"không phải nhảy sang file khác"*).
 *
 * ## ⚠ Tên `Lang` trùng với `com.byd.clusternav.Lang` của ClusterNav (`:app`) — KHÁC package, và khác vai
 * Bên đó là một `object` biết `Context` (đọc/ghi prefs, đoán theo locale máy). Bên này là **enum thuần** + một bảng
 * chọn. Chúng KHÔNG thay thế nhau: `:app` giữ vai đọc lựa chọn của người dùng rồi **ghi xuống** [Strings.current].
 * Tệp nào ở `:app` cần cả hai thì phải import có tên rõ ràng (`as`); đó là lý do lối gọi ở đây là `Strings.t(…)` chứ
 * không phải `Lang.t(…)` — hai lối gọi khác nhau thì không lẫn được.
 */

/**
 * Ngôn ngữ launcher hỗ trợ. Tiếng Việt là **gốc** (mọi nhãn `label` trong `:core` viết bằng tiếng Việt).
 *
 * VI/EN có bản dịch NGAY TẠI CHỖ GỌI (cặp chữ trong mã). ZH/TH/MS (spec `kachi-i18n-zh-th-ms.html` §4.2) tra
 * [I18nCatalog] theo CẶP `(vi, en)` — thiếu dòng ⇒ lùi về **tiếng Anh**, không bao giờ về tiếng Việt.
 *
 * [code] = tên tệp bảng dịch `/i18n/<code>.tsv` (chữ thường, ISO-639-1). Không phải định dạng đĩa — đó là [LangMode.code].
 */
enum class Lang(val code: String) {
    VI("vi"), EN("en"), ZH("zh"), TH("th"), MS("ms");

    /** Ngôn ngữ của GIỌNG NÓI khi giao diện là ngôn ngữ này — xem [voiceLangOf]. */
    val voice: Lang get() = voiceLangOf(this)
}

/**
 * Ngôn ngữ của GIỌNG NÓI (câu trả lời đọc ra, giọng đọc, geocoder) suy THUẦN từ ngôn ngữ giao diện [ui]:
 * `EN → EN`, mọi thứ khác → `VI` (spec `kachi-i18n-zh-th-ms.html` R6, §4.4).
 *
 * ## Vì sao không theo luôn giao diện
 * [ĐO] Bộ nhận dạng chỉ có một gói tiếng Việt (`SherpaModelCatalog`), giọng đọc offline duy nhất là Piper tiếng Việt,
 * và `VoiceFeedbackPhrase` đếm từ bằng dấu cách (vô nghĩa với chữ Hán/Thái). Giao diện ZH/TH/MS mà câu trả lời theo
 * giao diện thì giọng Việt phải đọc chữ Hán/Thái. Nên giọng nói chỉ có hai thứ tiếng: tiếng Anh cho người đã chọn
 * English (giữ hành vi cũ), còn lại tiếng Việt — đúng ngôn ngữ duy nhất Kachi nghe hiểu.
 *
 * Hàm thuần, không đọc [Strings.current]: chỗ gọi (đường nói ở `:app`, phiên `:wake`) truyền ngôn ngữ giao diện đã
 * giải nghĩa vào, nên không có thêm trạng thái toàn cục nào.
 */
fun voiceLangOf(ui: Lang): Lang = if (ui == Lang.EN) Lang.EN else Lang.VI

/**
 * LỰA CHỌN của người dùng — [AUTO] cộng một mục cho MỖI [Lang]. Đây là thứ được LƯU BỀN; [Lang] là thứ được *giải
 * nghĩa* ra từ nó.
 *
 * ## Vì sao phải có [AUTO] chứ không lưu thẳng [Lang]
 * Lưu thẳng `VI`/`EN` thì "theo xe" **không tồn tại được**: lần đầu chạy phải chốt cứng một thứ tiếng, và sau đó chủ
 * xe đổi ngôn ngữ hệ thống thì launcher không đi theo. Ba giá trị giữ được sự khác nhau giữa *"tôi chọn tiếng Anh"* và
 * *"cho tôi thứ tiếng của máy"* — hai ý định khác nhau mà một giá trị đã giải nghĩa không phân biệt nổi. Cùng hình
 * dạng với [ThemeMode] (DAY · NIGHT · **AUTO**) và với `com.byd.clusternav.Lang.Choice` của ClusterNav.
 *
 * ## ⚠⚠ [code] là ĐỊNH DẠNG TRÊN ĐĨA, dùng CHUNG với ClusterNav — không đổi tuỳ tiện
 * Launcher và màn ClusterNav dùng **một** chỗ lưu ngôn ngữ (xem KDoc `WorkspacePrefs.langMode`), nên MỌI chuỗi
 * (`"auto"`/`"vi"`/`"en"`, từ 2026-10-03 thêm `"zh"`/`"th"`/`"ms"`) phải khớp từng ký tự với
 * `com.byd.clusternav.Lang.Choice.code`. Lệch một ký tự thì lựa chọn của người dùng **im lặng** rơi về mặc định ở một
 * trong hai màn — mà `of` lùi về [AUTO] chứ không ném, nên sẽ không có gì báo lỗi; THIẾU hẳn một mã ở `Choice` thì
 * `broadcastLang` (`WorkspacePrefsLang.kt`) **ném** khi người dùng chọn tiếng đó. `LauncherLocaleContractTest` đối chiếu
 * hai bộ mã này bằng máy.
 *
 * Dùng `name` làm định dạng đĩa (`AUTO`/`VI`/`EN`) thì sạch hơn về mã, nhưng sẽ **phá dữ liệu đã có trên xe** của
 * người đang dùng màn ClusterNav — đó là lý do định dạng chữ thường thắng.
 */
enum class LangMode(val code: String) {
    AUTO("auto"), VI("vi"), EN("en"), ZH("zh"), TH("th"), MS("ms");

    /**
     * Giải nghĩa ra ngôn ngữ THẬT. [systemLanguage] = mã ISO-639 của locale máy (`Locale.getLanguage()`), `null` khi
     * không đọc được.
     *
     * Lựa chọn tường minh ánh xạ 1:1. [AUTO]: `"vi"` → tiếng Việt, **mọi thứ khác** (kể cả không đọc được, kể cả máy
     * đặt `zh`/`th`/`ms`) → tiếng Anh. Cố ý bất đối xứng: tiếng Anh là lựa chọn dễ đọc hơn cho một người không nói
     * tiếng Việt, còn người nói tiếng Việt thì đang ở đúng nhánh `"vi"`.
     *
     * ⚠ [AUTO] KHÔNG tự chọn ZH/TH/MS (spec `kachi-i18n-zh-th-ms.html` OQ1, owner chốt 2026-10-03): xe nhập đang để
     * tiếng Trung không được tự đổi giao diện của người đang dùng sau một lần cập nhật. Ba tiếng mới chỉ có khi người
     * dùng tự chọn.
     */
    fun resolve(systemLanguage: String?): Lang = when (this) {
        VI -> Lang.VI
        EN -> Lang.EN
        ZH -> Lang.ZH
        TH -> Lang.TH
        MS -> Lang.MS
        AUTO -> if (systemLanguage == "vi") Lang.VI else Lang.EN
    }

    /**
     * Nhãn cho bộ chọn.
     *
     * ⚠ **Tên của một ngôn ngữ giữ nguyên ngữ của chính nó** ("Tiếng Việt" không thành "Vietnamese"): người đang thấy
     * màn tiếng Anh mà muốn chuyển sang tiếng Việt phải nhận ra được dòng đó — dịch nó là làm mất chính chức năng của
     * bộ chọn. Đây là quy ước chung của mọi bộ chọn ngôn ngữ, và là lý do hai giá trị này nằm trong danh sách loại trừ
     * của `LauncherI18nContractTest` kèm lý do.
     */
    fun label(): String = when (this) {
        AUTO -> Strings.t("Theo xe", "By car")
        VI -> "Tiếng Việt"
        EN -> "English"
        ZH -> "简体中文"
        TH -> "ไทย"
        MS -> "Bahasa Melayu"
    }

    companion object {
        /** Đọc từ đĩa — mã lạ/`null` ⇒ [AUTO] (tương thích ngược, và là mặc định theo §6 OQ1). */
        fun of(code: String?): LangMode = entries.firstOrNull { it.code == code } ?: AUTO
    }
}

/**
 * BẢNG CHỌN NGÔN ngữ cho `:core`.
 *
 * ## ⚠⚠ [current] là TRẠNG THÁI DÙNG CHUNG — ai được ghi
 * **Đúng MỘT chỗ ghi**: đường đổi cấu hình ở `:app` (T3 — `HomeViewModel` khi nạp/đổi lựa chọn ngôn ngữ, cùng khuôn
 * `topStrip`/`autostart`). Mọi chỗ khác **chỉ đọc**. Đây là cùng luật *một-nơi-ghi-duy-nhất* mà dự án đã áp cho
 * trạng thái bền (`FreeformSeedPolicy`) và cho `unitPrefs` (từng có **4 bản sao**): hai nơi ghi một giá trị thì chúng
 * sẽ lệch nhau đúng lúc ai đó sửa một chỗ.
 *
 * ## Vì sao KHÔNG truyền ngôn ngữ qua tham số ở mọi chỗ
 * Vì nhãn nằm trong **dữ liệu**: muốn truyền tham số thì phải luồn nó qua [TelemetryRegistry] → [CapabilityCatalog] →
 * [GroupBoard] → [TopStripChips] → tầng vẽ, tức sửa hàng trăm chỗ gọi để nói một thứ **không đổi trong suốt một lần
 * dựng màn**. Churn đó không mua thêm tính đúng nào.
 *
 * ## Nhưng vẫn KHÔNG bắt ai phải mutate toàn cục để kiểm
 * Mọi hàm ở đây nhận `lang` là **tham số có mặc định** `= current`, và [Localized.labelIn] là phép đọc **thuần** theo
 * ngôn ngữ truyền vào. Nhờ vậy bài test kiểm được cả hai thứ tiếng **mà không chạm** [current] — quan trọng vì
 * `var` toàn cục rò từ bài này sang bài khác là một họ lỗi rất khó lần ra (bài chạy một mình thì xanh, chạy cả gói
 * thì đỏ). `LangCoverageTest` vẫn tự dọn [current] sau mỗi bài để chốt hai lớp.
 *
 * ## `@Volatile` không phải trang trí
 * [ĐO] `CarStatusRepository` bơm trạng thái xe trên **thread nền** (nhịp 1 giây) và tầng vẽ đọc nhãn trên **thread
 * chính**; đường đổi cấu hình thì ghi từ thread chính. Không có `@Volatile` thì một thread có thể đọc giá trị cũ
 * **vô hạn** — biểu hiện sẽ là *"đổi sang English mà mấy ô dữ liệu xe vẫn tiếng Việt"*, đúng loại lỗi trông như lỗi
 * giao diện nên sẽ bị đi tìm ở chỗ khác.
 */
object Strings {

    /** Ngôn ngữ đang dùng. Xem KDoc lớp về **ai được ghi** (đúng một chỗ ở `:app`). */
    @Volatile
    var current: Lang = Lang.VI

    /**
     * Chọn giữa hai bản dịch **có sẵn cả hai** — dùng cho chuỗi ghép lúc chạy.
     *
     * Cả hai tham số bắt buộc: chỗ gọi luôn biết cả hai câu (nó tự viết ra), nên không có ca "thiếu bản Anh".
     *
     * ZH/TH/MS: tra [I18nCatalog] theo CẶP `(vi, en)`; thiếu dòng ⇒ **tiếng Anh** (spec `kachi-i18n-zh-th-ms.html`
     * R3 — người không đọc được tiếng Việt thì tiếng Anh vẫn hơn). VI/EN không chạm bảng dịch: kết quả y từng byte.
     *
     * ⚠ [vi]/[en] phải là chữ CỐ ĐỊNH (khoá bảng dịch). Câu có biến dùng [f]/[fIn] — `I18nSourceGuardTest` đỏ nếu thấy
     * `$` trong chữ truyền vào đây.
     */
    fun t(vi: String, en: String, lang: Lang = current): String = when (lang) {
        Lang.VI -> vi
        Lang.EN -> en
        else -> I18nCatalog.lookup(lang, vi, en) ?: en
    }

    /**
     * Chọn nhãn cho một dòng dữ liệu, **tự lùi về tiếng Việt** nếu bản Anh chưa có.
     *
     * Lùi (chứ không ném) vì một mã mới thêm mà quên dịch thì hậu quả đúng phải là *"hiện tiếng Việt"* — chứ không
     * phải launcher sập trên xe. `LangCoverageTest` đếm tuyệt đối từng registry nên chuyện quên dịch **đỏ off-car**
     * trước khi kịp tới xe.
     *
     * Chuỗi rỗng/trắng cũng bị coi là **chưa có**: một nhãn `""` sẽ ra ô không chữ, tệ hơn hẳn ô còn tiếng Việt.
     *
     * ZH/TH/MS: không có bản Anh ⇒ tiếng Việt (như EN); có bản Anh ⇒ bảng dịch theo cặp, thiếu dòng ⇒ bản Anh.
     */
    fun pick(vi: String, en: String?, lang: Lang = current): String = when {
        lang == Lang.VI || en.isNullOrBlank() -> vi
        lang == Lang.EN -> en
        else -> I18nCatalog.lookup(lang, vi, en) ?: en
    }

    /**
     * Câu CÓ BIẾN theo [current] — xem [fIn].
     *
     * `Strings.f("{0} nấc", "by {0}", n)` thay cho `Strings.t("$n nấc", "by $n")`: khoá bảng dịch phải là chữ tĩnh
     * (mẫu), không phải câu đã ghép (mỗi giá trị [args] một khoá ⇒ không bao giờ dịch được).
     */
    fun f(vi: String, en: String, vararg args: Any?): String = fIn(current, vi, en, *args)

    /**
     * Câu CÓ BIẾN theo một ngôn ngữ CỤ THỂ: chọn MẪU qua [t] (khoá = cặp mẫu), rồi thay `{0}`…`{9}` bằng
     * `args[n].toString()` — đúng phép Kotlin dùng cho `"$x"`, nên VI/EN ra y từng byte như chuỗi mẫu `$` cũ.
     *
     * Bản dịch được phép **đổi thứ tự** chỗ trống (`"{1} {0}"`): tiếng Trung/Thái đặt số/tên ở vị trí khác tiếng Anh.
     * Thay MỘT LƯỢT trái→phải (không thay lặp) ⇒ một đối số tình cờ chứa `{1}` không bị thay tiếp.
     */
    fun fIn(lang: Lang, vi: String, en: String, vararg args: Any?): String = fill(t(vi, en, lang), args)

    /**
     * Thay `{n}` (n = 0..9, có trong [args]) bằng `args[n].toString()`; mọi `{…}` khác giữ nguyên. Thuần, công khai
     * để test khoá đúng luật thay (đổi thứ tự, đối số chứa `{0}`).
     */
    fun fill(template: String, args: Array<out Any?>): String {
        if (args.isEmpty() || template.indexOf('{') < 0) return template
        val out = StringBuilder(template.length + 16)
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '{' && i + 2 < template.length && template[i + 2] == '}' && template[i + 1] in '0'..'9') {
                val n = template[i + 1] - '0'
                if (n < args.size) {
                    out.append(args[n].toString())
                    i += 3
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}

/**
 * Hợp đồng chung cho **mọi dòng registry có nhãn**: nhãn Việt (gốc) + nhãn Anh (có thể thiếu).
 *
 * ## Vì sao là interface chứ không chép hai thuộc tính vào sáu lớp
 * Sáu bộ đăng ký ([TelemetrySpec] · [ControlDef] · [CapabilityGroup] · [SettingsEntry] · [WidgetDef] ·
 * [ActionMacro]) + ba enum ([Domain] · [Quantity] · [SettingsGroup]) cần **cùng một** phép "chọn nhãn theo ngôn
 * ngữ". Chép chín bản là chín chỗ có thể lệch. Quan trọng hơn: nhờ có kiểu chung, `LangCoverageTest` **quét được cả
 * chín bộ bằng một vòng lặp** thay vì chín đoạn gần giống nhau — mà chín đoạn gần giống nhau chính là chỗ dễ bỏ sót
 * một bộ, tức đúng lỗ hổng bài canh sinh ra để bịt.
 *
 * ⚠ KHÔNG đổi nghĩa của [label]: **hàng trăm bài test đang assert chuỗi tiếng Việt** qua nó, và cấu hình người dùng
 * cùng nhật ký đã ghi theo nó. [label] vẫn là tiếng Việt, luôn luôn; phần đổi theo ngôn ngữ là [displayLabel].
 */
interface Localized {

    /** Nhãn GỐC — luôn tiếng Việt. Đây là thứ test/nhật ký đối chiếu, KHÔNG đổi theo ngôn ngữ. */
    val label: String

    /** Nhãn tiếng Anh; `null` = chưa dịch ⇒ [displayLabel] lùi về [label]. */
    val labelEn: String?

    /** Nhãn để HIỆN cho người dùng, theo [Strings.current]. */
    val displayLabel: String get() = Strings.pick(label, labelEn)

    /** Nhãn theo một ngôn ngữ CỤ THỂ — phép đọc thuần, không phụ thuộc [Strings.current] (dùng cho test). */
    fun labelIn(lang: Lang): String = Strings.pick(label, labelEn, lang)
}
