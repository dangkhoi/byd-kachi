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
        assertTrue(body.contains("Strings.current ="), "phải ghi `Strings.current` (kênh nhãn dữ liệu của :core)")
        assertTrue(body.contains("setLocale("), "phải đặt locale của Context (kênh tài nguyên `R.string`)")
        assertTrue(body.contains("createConfigurationContext"), "và trả về Context đã ép cấu hình")
        assertFalse(
            Regex("""AUTO\s*(->|==)[^\n]*return base""").containsMatchIn(body),
            "ca \"Theo xe\" KHÔNG được trả `base` trần: `values-en/` chỉ khớp locale `en*`, nên trên xe locale khác " +
                "(ja/th/zh) nhãn :core sẽ tiếng Anh mà chữ trên màn vẫn tiếng Việt",
        )
    }

    /**
     * Một chỗ lưu ngôn ngữ cho CẢ APK: `WorkspacePrefs` uỷ quyền sang `com.byd.clusternav.Lang` (tệp
     * `clusternav_lang`), KHÔNG mở khoá `lang` thứ hai trong `kachi_workspace`.
     *
     * Hai công tắc cho một câu hỏi *"người ngồi đây đọc thứ tiếng nào"* sẽ biểu hiện thành: chọn English ở Cài đặt
     * Kachi rồi mở màn ClusterNav thì màn đó **vẫn tiếng Việt**.
     *
     * [ĐO] máy ảo 2026-09-12 (soát độc lập): chọn "Tiếng Việt" trong Cài đặt Kachi ⇒ tệp **`clusternav_lang.xml`**
     * (của ClusterNav, không phải `kachi_workspace.xml`) nhận `<string name="lang">vi</string>` ⇒ chỗ lưu dùng chung
     * là thật, không chỉ là ý định trong KDoc.
     */
    @Test
    fun `mot cho luu ngon ngu cho ca APK`() {
        val prefs = code(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/WorkspacePrefs.kt"))
        assertTrue(
            prefs.contains("ClusterNavLang.choice(") && prefs.contains("ClusterNavLang.setChoice("),
            "`WorkspacePrefs.langMode/setLangMode` phải UỶ QUYỀN sang chỗ lưu ngôn ngữ của ClusterNav",
        )
        assertFalse(
            Regex("""K_LANG|putString\(\s*"lang"""").containsMatchIn(prefs),
            "KHÔNG được mở khoá `lang` thứ hai trong `kachi_workspace` — đó là bẫy hai-bản-sao mà dự án đã trả giá " +
                "bốn lần (customLayout · unitPrefs ×4 · wallpaper · themeMode)",
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
        val choices = Regex("""(\w+)\("(auto|vi|en)"\)""").findAll(src).map { it.groupValues[2] }.toSet()
        assertEquals(
            LangMode.entries.map { it.code }.toSet(), choices,
            "ba mã lưu của LangMode (:core) phải khớp TỪNG KÝ TỰ với Lang.Choice của ClusterNav — chúng đọc/ghi " +
                "CÙNG một giá trị trên đĩa, và `of()` lùi về AUTO chứ không ném nên lệch là sai IM LẶNG",
        )
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
        val offenders = launcherSources().filter { f ->
            f.fileName.toString() != "LangHost.kt" &&
                Regex("""Locale\(\s*"vi"|forLanguageTag\(\s*"vi"""").containsMatchIn(code(f))
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
    }

    // ── Hạ tầng ──────────────────────────────────────────────────────────────────────────────────

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
