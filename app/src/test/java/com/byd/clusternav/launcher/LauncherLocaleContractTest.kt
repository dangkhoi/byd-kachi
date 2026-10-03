package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ U5 · T3 — DÂY NỐI NGÔN NGỮ (khác với NỘI DUNG chữ) ═══════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-i18n-and-light-theme.html` §3.1 (R1).
 *
 * ## Vì sao tách khỏi [LauncherI18nContractTest]
 * Tệp kia vượt **trần 500 dòng** sau khi lượt soát độc lập 2026-09-12 thêm ba bài. Đường cắt theo **chủ đề**, không
 * phải cắt bừa cho vừa số dòng:
 *  • tệp kia canh **NỘI DUNG chữ** — chuỗi nào phải đi qua tài nguyên, hai tệp dịch có khớp nhau, bản Anh có còn dấu
 *    tiếng Việt;
 *  • tệp NÀY canh **DÂY NỐI** — ai được ghi ngôn ngữ, nó được lưu ở đâu, và `Locale` nào được dùng để định dạng.
 *
 * Hai chủ đề đó hỏng theo hai cách khác nhau: nhóm trên hỏng thành *"câu này chưa dịch"*, nhóm dưới hỏng thành *"đã
 * dịch hết mà màn vẫn ra thứ tiếng cũ"* — và nhóm dưới thì **không có chuỗi nào trong mã** để bộ quét nội dung thấy.
 *
 * ⚠ Vài hàm quét nhỏ (`code`/`launcherSources`/`allAppSources`) trùng với tệp kia. Đây là quy ước ĐANG DÙNG của repo
 * (`private fun body(...)` có ở 6 tệp test khác nhau): chúng là hàm THUẦN vài dòng, và cho chúng vào `SourceRoots`
 * dùng chung sẽ bắt mọi module test khác chịu bán kính thay đổi của một thứ chỉ hai tệp cần.
 */
class LauncherLocaleContractTest {

    // ══ (1) MỘT NƠI GHI · MỘT NƠI LƯU ══════════════════════════════════════════════════════════════════════

    /**
     * `Strings.current` là TRẠNG THÁI DÙNG CHUNG (`@Volatile var` toàn cục). Hai nơi ghi thì chúng sẽ lệch nhau đúng
     * lúc ai đó sửa một chỗ — cùng luật một-nơi-ghi-duy-nhất mà dự án đã áp cho `FreeformSeedPolicy` và cho
     * `KachiTheme.applyTheme` (T1), và đã trả giá bốn lần khi không áp (`unitPrefs` từng có 4 bản sao).
     */
    @Test
    fun `dung MOT cho ghi Strings current`() {
        // ⚠ `=(?!=)` — không có lookahead thì `Strings.current == Lang.EN` (phép SO trong `LangHost.locale`) bị đọc
        // thành phép GÁN và bài này đỏ oan. Dự án đã trả giá đúng lỗi này một lần khi viết helper `SourceRoots.body`.
        val writers = allAppSources().flatMap { f ->
            Regex("""Strings\.current\s*=(?!=)""").findAll(code(f)).map { f.fileName.toString() }
        }
        assertEquals(
            listOf("LangHost.kt"), writers,
            "đúng MỘT chỗ được ghi `Strings.current` (LangHost.wrap, gọi từ attachBaseContext). Chỗ thứ hai = hai " +
                "nguồn sự thật cho một câu hỏi",
        )
    }

    /**
     * Áp ngôn ngữ phải làm **CẢ HAI** việc trong cùng một hàm: ghi `Strings.current` (cho nhãn `:core`) và đặt locale
     * của `Context` (cho `R.string`). Thiếu một nửa là ra đúng cái nửa-vời tệ nhất — xem KDoc [LangHost].
     */
    @Test
    fun `LangHost ap ca hai kenh trong cung mot ham`() {
        val body = SourceRoots.body(host, "fun wrap(base: Context): Context")
        assertTrue(body.contains("Strings.current = lang"), "phải ghi `Strings.current` (kênh nhãn dữ liệu của :core)")
        // [kachi-i18n-zh-th-ms T1b] Phần đặt locale tách thành `localized(base, lang)` để màn phụ dùng lại (R9) — nên
        // bài canh đòi `wrap` đưa ĐÚNG biến `lang` vừa ghi vào hàm đó: hai kênh vẫn đọc một giá trị trong một lời gọi.
        assertTrue(body.contains("localized(base, lang)"), "phải đặt locale của Context bằng CHÍNH ngôn ngữ vừa ghi")
        val apply = SourceRoots.body(host, "fun localized(base: Context, lang: Lang): Context")
        assertTrue(apply.contains("setLocale("), "phải đặt locale của Context (kênh tài nguyên `R.string`)")
        assertTrue(apply.contains("createConfigurationContext"), "và trả về Context đã ép cấu hình")
        assertFalse(apply.contains("Strings.current"), "đường đặt locale KHÔNG được ghi `Strings.current` (màn phụ dùng nó)")
        assertFalse(
            Regex("""AUTO\s*(->|==)[^\n]*return base""").containsMatchIn(body),
            "ca \"Theo xe\" KHÔNG được trả `base` trần: `values-en/` chỉ khớp locale `en*`, nên trên xe locale khác " +
                "(ja/th/zh) nhãn :core sẽ tiếng Anh mà chữ trên màn vẫn tiếng Việt",
        )
    }

    /**
     * R9 (spec `kachi-i18n-zh-th-ms.html`) — MỌI Activity của `:app` phải áp ngôn ngữ người dùng ở `attachBaseContext`
     * (`LangHost.wrap` cho màn chính, `LangHost.localized` cho màn phụ). Trước đây bốn màn phụ chỉ áp
     * `ThemeMode.wrap` ⇒ tài nguyên theo locale MÁY; có `values-zh-rCN/-th/-ms` rồi thì xe đặt tiếng Trung + người dùng
     * chọn English ra màn phụ tiếng Trung, cạnh chữ `Lang.t` tiếng Anh trên CÙNG màn.
     */
    @Test
    fun `moi Activity ap ngon ngu nguoi dung o attachBaseContext`() {
        val sites = allAppSources().filter { "override fun attachBaseContext(" in code(it) }
        assertTrue(sites.size >= 5, "chỉ thấy ${sites.size} chỗ attachBaseContext — bộ quét hỏng?")
        val bad = sites.filterNot { f ->
            Regex("""LangHost\.(wrap|localized)\(""").containsMatchIn(SourceRoots.body(code(f), "override fun attachBaseContext("))
        }.map { it.fileName.toString() }
        assertEquals(emptyList<String>(), bad, "attachBaseContext không qua LangHost ⇒ màn đó theo locale MÁY")
    }

    /**
     * R9 (soát 2.87 · P2) — bài trên chỉ thấy Activity. Mã tiến trình chính ở `launcher/` (kể cả `camera/`) mà tra
     * chuỗi qua **Context ứng dụng** cũng theo locale MÁY: nhãn camera, hai toast. Bộ quét + ba ca thật: [AppCtxResScan].
     * Tra qua `LangHost.localized(app)` (Context ứng dụng giữ nguyên cho WindowManager/GL/Toast) là đường đúng.
     */
    @Test
    fun `ma tien trinh chinh khong tra chuoi qua Context ung dung`() {
        val files = launcherSources().associate { it.toString().substringAfter("launcher/") to code(it) }
        assertTrue(files.size >= 100, "chỉ quét ${files.size} tệp — bộ quét hỏng?")
        val hits = AppCtxResScan.offenders(files)
        assertEquals(
            emptyList<String>(), hits.filterNot { it in APP_CTX_RES_ALLOW },
            "tra chuỗi qua Context ỨNG DỤNG ⇒ theo locale MÁY (R9). Dùng `LangHost.localized(app).getString(…)`, " +
                "hoặc khai APP_CTX_RES_ALLOW kèm lý do",
        )
        assertEquals(emptyList<String>(), APP_CTX_RES_ALLOW.keys.filterNot { it in hits }, "mục APP_CTX_RES_ALLOW chết")
    }

    /** Thử-phá bộ quét bằng đúng ba ca hỏng thật (trước bản vá) + bản đã vá của chúng. */
    @Test
    fun `bo quet Context ung dung bat du ba ca that`() {
        val broken = mapOf(
            "camera/Mask.kt" to "internal fun labelFor(ctx: Context, side: Side?): TextView? {\n    text = ctx.getString(res)\n}\n",
            "camera/View.kt" to "class V(private val appCtx: Context) {\n    fun show() {\n        val ctx = appCtx\n        labelFor(ctx, side)?.let { }\n    }\n}\n",
            "Slots.kt" to "fun e() {\n    Toast.makeText(app, app.getString(R.string.kachi_sc_place_failed, x), Toast.LENGTH_SHORT).show()\n}\n",
            "Card.kt" to "fun allow(ctx: Context) { main.post { prompt(ctx.applicationContext) } }\n" +
                "private fun prompt(ctx: Context?) {\n    val c = ctx ?: return\n    Toast.makeText(c, R.string.kachi_access_feature_blocked, 1).show()\n}\n",
        )
        assertEquals(
            listOf(
                "Card.kt: prompt(ctx.applicationContext)",
                "Slots.kt: app.getString(",
                "camera/View.kt: labelFor(ctx,",
            ),
            AppCtxResScan.offenders(broken),
        )
        val fixed = mapOf(
            "camera/Mask.kt" to "internal fun labelFor(ctx: Context, side: Side?): TextView? {\n    text = LangHost.localized(ctx).getString(res)\n}\n",
            "camera/View.kt" to broken.getValue("camera/View.kt"),
            "Slots.kt" to "fun e() {\n    Toast.makeText(app, LangHost.localized(app).getString(R.string.kachi_sc_place_failed, x), 0).show()\n}\n",
            "Card.kt" to "fun allow(ctx: Context) { main.post { prompt(ctx.applicationContext) } }\n" +
                "private fun prompt(ctx: Context?) {\n    val c = ctx ?: return\n    Toast.makeText(c, LangHost.localized(c).getText(R.string.x), 1).show()\n}\n",
        )
        assertEquals(emptyList<String>(), AppCtxResScan.offenders(fixed), "đường sửa (LangHost.localized) phải đi qua")
    }

    /**
     * ═══ MỘT NGUỒN SỰ THẬT cho ngôn ngữ — **bài đảo chiều ở S4 · R3(a)**, giữ nguyên lịch sử ═══════════════════
     *
     * **Chiều CŨ (U5 · T3)**: ngôn ngữ CHUNG cả máy, chỗ lưu là `clusternav_lang` của ClusterNav, và bài này **cấm**
     * mở khoá `lang` thứ hai trong `kachi_workspace`. Lý do: hai công tắc cho một câu hỏi *"người ngồi đây đọc thứ
     * tiếng nào"* sẽ biểu hiện thành *chọn English ở Cài đặt Kachi rồi mở màn ClusterNav thì màn đó vẫn tiếng Việt*.
     *
     * **Chiều MỚI (S4 · R3a)**: hồ sơ giữ TẤT CẢ, và ngôn ngữ là *lựa chọn của một người lái* chứ không phải của
     * chiếc xe ⇒ nguồn sự thật chuyển về `<hồ sơ>__lang`. Nhưng **bệnh cũ không được phép quay lại**, nên luật đổi
     * hình chứ không nới: hai chỗ lưu được phép tồn tại **chỉ khi** chúng là *nguồn* và *bản phát*, một chiều, và
     * chiều đó đi qua **đúng một hàm**.
     *
     * Ba điều bài này chốt, và cả ba đều là thứ làm bệnh cũ sống lại nếu thiếu:
     *  1. `ClusterNavLang.setChoice` chỉ được gọi ở **một** chỗ (`broadcastLang`) — hai chỗ ghi là hai đường phát,
     *     và đường nào quên gọi thì màn ClusterNav lệch ngôn ngữ với launcher;
     *  2. lượt **đổi hồ sơ** phải phát lại (`PrefsWorkspaceRepository.switchProfile` gọi `broadcastLang`) — thiếu nó
     *     thì đúng triệu chứng cũ quay lại, chỉ đổi cách kích hoạt (đổi hồ sơ thay vì chọn ngôn ngữ);
     *  3. chiều ĐỌC không bao giờ ngược: `ClusterNavLang.choice` chỉ được đọc trong `langMode()` và chỉ để **lùi một
     *     lần** cho máy đã chạy bản cũ (nếu không, người đang dùng English mất lựa chọn khi cập nhật).
     */
    @Test
    fun `mot nguon su that cho ngon ngu, clusternav_lang chi la ban phat`() {
        // ⚠ [SOÁT Pass 1 · 2026-09-16] Quét **CẢ HAI** tệp: ba hàm ngôn ngữ đã tách sang `WorkspacePrefsLang.kt`
        // (trần 500 dòng, CLAUDE.md §4.1). Nối chuỗi rồi mới đếm ⇒ phép "đúng MỘT chỗ ghi" nay phủ cả hai tệp,
        // tức bài canh MẠNH hơn trước chứ không phải được nới để đi qua lượt tách.
        val prefs = code(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/WorkspacePrefs.kt")) +
            "\n" + code(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/WorkspacePrefsLang.kt"))
        val repo = code(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/PrefsWorkspaceRepository.kt"))
        assertEquals(
            1, Regex("""ClusterNavLang\.setChoice\(""").findAll(prefs).count(),
            "đường PHÁT sang `clusternav_lang` phải có đúng MỘT chỗ ghi (`broadcastLang`)",
        )
        assertTrue(
            SourceRoots.body(prefs, "internal fun WorkspacePrefs.broadcastLang(").contains("ClusterNavLang.setChoice("),
            "và chỗ ghi đó phải chính là `broadcastLang` — tên hàm nói ra nó là bản phát, không phải chỗ nhớ",
        )
        assertEquals(
            1, Regex("""ClusterNavLang\.choice\(""").findAll(prefs).count(),
            "chiều ĐỌC ngược chỉ được có đúng một chỗ: lượt lùi MỘT LẦN trong `langMode()` cho máy đã chạy bản cũ",
        )
        assertTrue(
            SourceRoots.body(repo, "override fun switchProfile(name: String): HomeUiState")
                .contains("prefs.broadcastLang()"),
            "đổi hồ sơ PHẢI phát lại ngôn ngữ — thiếu nó thì hồ sơ dùng English mà màn ClusterNav vẫn tiếng Việt " +
                "(đúng triệu chứng U5 đã chữa, chỉ đổi cách kích hoạt)",
        )
        assertTrue(
            Regex("""putString\(key\((?:WorkspacePrefs\.)?K_LANG\)""").containsMatchIn(prefs),
            "nguồn sự thật phải là khoá THEO HỒ SƠ `<hồ sơ>__lang` (S4 · R3a)",
        )
    }

    /**
     * Khoá thật của chỗ lưu đó phải là khoá mà [SettingsCatalog] khai — cùng khuôn với bài
     * `khoa lay gio trong khai dung ten that trong Prefs` (khoá nằm ngoài `WorkspacePrefs` thì canh bằng bài riêng,
     * vì bộ quét chung cố ý không đọc tệp của ClusterNav).
     */
    @Test
    fun `khoa ngon ngu khai dung ten that trong Lang cua ClusterNav`() {
        val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/Lang.kt")
        val key = Regex("""val K\s*=\s*"([^"]+)"""").find(src)?.groupValues?.get(1)
        assertNotNull(key, "không đọc được hằng khoá trong Lang.kt — bài test đang quét vùng không tồn tại")
        assertEquals(
            SettingsGroup.DISPLAY, SettingsCatalog.groupOf(key!!),
            "khoá ngôn ngữ ('$key') phải thuộc nhóm Hiển thị của màn Cài đặt (R2: không cấu hình nào nằm lẻ tẻ)",
        )
        // Định dạng trên đĩa dùng CHUNG: lệch một ký tự thì một trong hai màn im lặng rơi về mặc định.
        // [kachi-i18n-zh-th-ms T1b] Đọc THÂN `enum class Choice` với mọi mã chữ thường — bản cũ khớp cứng
        // `(auto|vi|en)` nên một mã mới thiếu ở `Choice` (đúng ca zh/th/ms) không bao giờ hiện ra ở vế phải.
        val choiceBody = SourceRoots.body(src, "enum class Choice(val code: String) {")
        val choices = Regex("""(\w+)\("([a-z]+)"\)""").findAll(choiceBody).associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(
            LangMode.entries.associate { it.name to it.code }, choices,
            "mã lưu (và tên) của LangMode (:core) phải khớp TỪNG KÝ TỰ với Lang.Choice của ClusterNav — chúng đọc/ghi " +
                "CÙNG một giá trị trên đĩa, và `of()` lùi về AUTO chứ không ném nên lệch là sai IM LẶNG",
        )
        // Và cùng điều đó LÚC CHẠY (không phụ thuộc định dạng mã nguồn).
        assertEquals(
            LangMode.entries.map { it.name to it.code }, com.byd.clusternav.Lang.Choice.entries.map { it.name to it.code },
            "Lang.Choice phải có đúng các mục của LangMode, cùng tên + mã + thứ tự",
        )
    }

    /**
     * [ĐO] P0 2026-10-03 — `broadcastLang` từng viết `Choice.entries.first { … }`: `:core` thêm ZH/TH/MS mà `Choice`
     * chưa có thì `NoSuchElementException` ở MỌI lần chọn tiếng và MỌI lần đổi hồ sơ. Bài này khoá hai điều: ánh xạ
     * KHỚP ĐÚNG mã cho mọi [LangMode] (không ca nào phải đi đường lùi), và đường lùi là TỔNG (mã lạ ⇒ AUTO, không ném).
     */
    @Test
    fun `anh xa LangMode sang Lang Choice la tong va khop ma`() {
        LangMode.entries.forEach { m -> assertEquals(m.code, choiceFor(m).code, "$m: ánh xạ rơi vào đường lùi") }
        assertEquals(com.byd.clusternav.Lang.Choice.AUTO, com.byd.clusternav.Lang.Choice.of("xx"), "mã lạ ⇒ AUTO")
        assertEquals(com.byd.clusternav.Lang.Choice.AUTO, com.byd.clusternav.Lang.Choice.of(null), "null ⇒ AUTO")
        val prefsLang = code(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/WorkspacePrefsLang.kt"))
        assertFalse(Regex("""Choice\.entries\.first\s*\{""").containsMatchIn(prefsLang), "`first {}` ném khi thiếu mã")
        assertTrue(SourceRoots.body(prefsLang, "internal fun WorkspacePrefs.broadcastLang(").contains("choiceFor(mode)"))
    }

    // ══ (2) LOCALE ĐỊNH DẠNG — một chỗ map ngôn ngữ → Locale ═══════════════════════════════════════════════

    /**
     * `Locale` cho tiếng Việt/Anh chỉ được dựng ở [LangHost] — một chỗ map ngôn ngữ → `Locale`.
     *
     * Không có luật này thì mỗi chỗ định dạng lại tự chọn locale, và ca sai sẽ **im lặng**: chuỗi kết quả do
     * `SimpleDateFormat` sinh ra nên không có chuỗi tiếng Việt nào trong mã để bất kỳ bộ quét nào bắt được.
     */
    @Test
    fun `chi LangHost duoc dung Locale tieng Viet`() {
        // [kachi-i18n-zh-th-ms T1b] Mở rộng cho ba tiếng mới: một chỗ tự dựng `Locale("th", "TH")` là lịch Phật giáo
        // trên JVM test, còn `Locale.CHINESE` (không vùng) không khớp `values-zh-rCN` như `SIMPLIFIED_CHINESE`.
        val offenders = launcherSources().filter { f ->
            f.fileName.toString() != "LangHost.kt" &&
                Regex("""Locale\(\s*"(vi|zh|th|ms)"|forLanguageTag\(\s*"(vi|zh|th|ms)|Locale\.(SIMPLIFIED_CHINESE|CHINESE|CHINA|PRC)\b""")
                    .containsMatchIn(code(f))
        }
        assertEquals(
            emptyList<String>(), offenders.map { it.fileName.toString() },
            "viết cứng locale tiếng Việt ngoài LangHost ⇒ chỗ đó ĐỨNG NGUYÊN tiếng Việt ở bản English (tên thứ trong " +
                "tuần), mà không có chuỗi nào trong mã để bộ quét thấy. Dùng `LangHost.locale()`",
        )
    }

    /**
     * ⚠⚠ [SOÁT ĐỘC LẬP 2026-09-12 · P3] Và cũng KHÔNG được dùng `Locale.getDefault()` để định dạng.
     *
     * `getDefault()` là locale của **MÁY**, không phải ngôn ngữ người dùng đã chọn cho launcher — hai thứ khác nhau
     * ngay khi [LangMode] không phải AUTO. Hậu quả không nằm ở chữ mà ở **CHỮ SỐ**: [ĐO] bằng JDK 17 thật,
     * `SimpleDateFormat("HH:mm", …)` cho `10:30` với `vi`/`en` nhưng **`١٠:٣٠`** với `ar-EG`, **`۱۰:۳۰`** với `fa-IR`,
     * **`၁၀:၃၀`** với `my-MM`, **`১০:৩০`** với `bn-BD`.
     *
     * Vì [LangMode.resolve] đưa **mọi** locale không phải `vi` về tiếng Anh, một xe cài locale Ả-Rập sẽ ra: đồng hồ
     * chữ số Ả-Rập **nằm cạnh** ngày chữ số La-tinh (dòng ngày đã dùng `LangHost.locale()` từ trước) — hai hệ chữ số
     * trên cùng một thanh. Bản trước có đúng lỗi đó ở **3 chỗ**, và nó lệch **ngay trong cùng một hàm**: dòng đồng hồ
     * dùng `getDefault()` còn dòng ngày ngay dưới dùng `LangHost.locale()`.
     *
     * Lý do cũ ghi trong danh sách loại trừ (*"HH:mm không phụ thuộc ngôn ngữ"*) đúng về **mẫu** nhưng sai về **tham
     * số locale** — mẫu không chứa chữ, nhưng chữ số thì do locale quyết định.
     */
    @Test
    fun `khong dinh dang bang Locale cua may`() {
        val offenders = launcherSources().filter { code(it).contains("Locale.getDefault()") }
        assertEquals(
            emptyList<String>(), offenders.map { it.fileName.toString() },
            "dùng `Locale.getDefault()` = định dạng theo locale MÁY, không theo ngôn ngữ người dùng chọn ⇒ chữ số có " +
                "thể ra hệ khác (ar/fa/my/bn) ngay cạnh chữ số La-tinh. Dùng `LangHost.locale()`",
        )
        // [kachi-i18n-zh-th-ms T1b · R7] `DateFormat.getTimeInstance(DateFormat.SHORT)` cũng là locale MÁY — chỉ là
        // không viết chữ `getDefault()` ra nên bài trên mù [ĐO: SettingsSectionsTrip.kt:179]. Xe đặt `ms`/`en_US` ⇒ giờ
        // 12h + AM/PM cạnh đồng hồ 24h. Mọi `DateFormat.get…Instance(…)` phải truyền một `Locale`.
        val noLocale = Regex("""DateFormat\.get(?:Date|Time|DateTime)?Instance\(([^()]*(?:\([^()]*\)[^()]*)*)\)""")
        val bad = launcherSources().flatMap { f ->
            noLocale.findAll(code(f)).filterNot { it.groupValues[1].contains("ocale") }.map { "${f.fileName}: ${it.value}" }
        }
        assertEquals(emptyList<String>(), bad, "DateFormat.get…Instance không truyền Locale ⇒ theo locale MÁY. Dùng LangHost.locale()")
    }

    // ── Hạ tầng ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Ca HỢP LỆ của [AppCtxResScan] — `"đường/tương đối/từ launcher: đoạn khớp"` → lý do. Rỗng = mọi chỗ tra chuỗi bằng
     * Context ứng dụng ở tiến trình chính đã đi qua ngôn ngữ người dùng.
     */
    private val APP_CTX_RES_ALLOW: Map<String, String> = emptyMap()

    private val host by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/LangHost.kt") }

    private fun launcherSources(): List<Path> =
        ktFiles(SourceRoots.path("src/main/java/com/byd/clusternav/launcher"))

    /** Cả cây `:app` — chỗ ghi thứ hai có thể nằm ngoài `launcher/`. */
    private fun allAppSources(): List<Path> = ktFiles(SourceRoots.path("src/main/java/com/byd/clusternav"))

    private fun ktFiles(root: Path): List<Path> = Files.walk(root).use { s ->
        s.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.sorted().toList()
    }

    /** MÃ đã bỏ chú thích — KDoc của dự án viết bằng tiếng Việt nên quét thô sẽ báo sai gần như mọi tệp. */
    private fun code(f: Path): String = f.toFile().readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { line ->
            var i = line.indexOf("//")
            while (i >= 0 && line.take(i).count { it == '"' } % 2 != 0) i = line.indexOf("//", i + 1)
            if (i >= 0) line.take(i) else line
        }
}
