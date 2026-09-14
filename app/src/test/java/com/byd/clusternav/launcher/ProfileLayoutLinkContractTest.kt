package com.byd.clusternav.launcher

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Owner 2026-09-14: *"chưa thấy hồ sơ nó gắn với bố cục chỗ nào?"*.
 *
 * [ĐO] cơ chế có thật (`WorkspacePrefs.key` = `<hồ sơ đang dùng>__<khoá>`) nhưng giao diện không nói. Bài này khoá ba
 * chỗ NÓI RA mối gắn: (1) nhóm Màn hình chính và (2) nhóm Thanh trạng thái & thanh nút mở đầu bằng câu "lưu cho hồ sơ
 * «X»"; (3) thẻ hồ sơ mang tóm tắt bố cục của chính nó (đọc theo TÊN hồ sơ, không phải hồ sơ đang dùng).
 */
class ProfileLayoutLinkContractTest {

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }

    private fun code(name: String): String =
        app("src/main/java/com/byd/clusternav/launcher/$name").toFile().readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }

    @Test
    fun `hai nhom luu theo ho so mo dau bang cau noi ro ho so nao`() {
        listOf("SettingsSectionsHome.kt", "SettingsSectionsBars.kt").forEach { f ->
            val build = code(f).let { it.substring(it.indexOf("fun build(")) }.let { it.substring(0, it.indexOf("\n    }")) }
            assertTrue(build.contains("kachi_home_profile_note") && build.contains("activeProfile"), "$f: build() phải nói đang lưu cho hồ sơ nào")
        }
    }

    @Test
    fun `the ho so mang tom tat bo cuc cua chinh no`() {
        val row = code("SettingsSections.kt").let { it.substring(it.indexOf("private fun profileRow(")) }
        assertTrue(row.contains("deps.profileSummary(name)"), "thẻ hồ sơ phải hỏi tóm tắt theo TÊN hồ sơ")
        assertTrue(row.contains("kachi_profile_sub_active") && row.contains("kachi_profile_sub_switch"))
        val prefs = profileLayoutBody()
        // S4 · T2 — phép ghép tiền tố gom về `keyOf(profile, suffix)` (ĐÚNG MỘT chỗ dựng chuỗi `<hồ sơ>__<hậu tố>`).
        // Luật thì không đổi: hàm này phải đọc theo TÊN được truyền vào, tuyệt đối không qua `key()` (hồ sơ ĐANG dùng)
        // — nếu không, thẻ của hồ sơ B sẽ nói ra bố cục của hồ sơ A, và cả ba thẻ hiện cùng một câu.
        assertTrue(!prefs.contains("key(") && prefs.contains("keyOf(name,"), "đọc theo tên hồ sơ, không qua key() của hồ sơ đang dùng")
    }

    private fun profileLayoutBody(): String =
        code("WorkspacePrefs.kt").let { it.substring(it.indexOf("fun profileLayout(")) }.let { it.substring(0, it.indexOf("\n    }")) }

    /**
     * ⚠⚠ Tóm tắt phải nói đúng thứ màn hình **THẬT SỰ VẼ**.
     *
     * Bản đầu trả "Tự vẽ" ngay khi `<tên>__grid_layout` còn khung. Nhưng bố cục tự vẽ **lưu rồi mà không dùng được**
     * (đè nhau · nhiều khung hơn trần ô — ca có thật khi hạ cấp bản) bị màn hình LÙI về bố cục sẵn, nên thẻ hồ sơ và
     * màn hình sẽ nói hai chuyện khác nhau. Cùng luật (và cùng nguồn) với dải chip bố cục:
     * [EffectiveLayout.highlightedPreset]. Số ô cũng đếm theo [EffectiveLayout.slotCount], không theo trần ô — nội
     * dung sót ở ô thứ 5 của một bố cục 2 ô không hiện ra ở đâu cả.
     */
    @Test
    fun `tom tat theo bo cuc DANG HIEU LUC, khong phai co khung la tu ve`() {
        val body = profileLayoutBody()
        assertTrue(
            body.contains("EffectiveLayout.highlightedPreset("),
            "'Tự vẽ' phải do EffectiveLayout quyết (nguồn duy nhất), không phải 'frames rỗng hay không'",
        )
        assertTrue(body.contains("EffectiveLayout.slotCount("), "số ô đếm theo bố cục đang hiệu lực, không theo trần ô")
    }

    /**
     * ⚠ R6 — **tầng UI 0 lần chạm nơi lưu**, kể cả lượt ĐỌC.
     *
     * Bố cục của một hồ sơ KHÁC không nằm trong `HomeUiState` (state chỉ mang hồ sơ đang dùng), nên chỗ vẽ rất dễ tự
     * mở một `WorkspacePrefs` thứ hai. [ĐO] đó đúng là đường mà [SOÁT P1-1] đã phải dọn một lần (*"chỗ này tự đọc lại
     * repository khi đổi hồ sơ ⇒ hai đường song song"*), và `SettingsScreenWiringContractTest` chỉ canh nhóm tệp
     * `Settings*` nên `HomePanels` lọt lưới. Bài này khoá nốt đường đó.
     */
    @Test
    fun `duong doc di qua ViewModel, khong mo cua prefs thu hai o tang UI`() {
        val panels = code("HomePanels.kt")
        assertFalse(
            panels.contains("WorkspacePrefs") || panels.contains("getSharedPreferences"),
            "HomePanels phải nhận tóm tắt qua lambda cổng vào — mở WorkspacePrefs ở đây là cửa đọc bền thứ hai",
        )
        assertTrue(panels.contains("profileSummary = profileSummary"), "chuyển thẳng lambda xuống SettingsDeps")
        val wiring = code("KachiHomeWiring.kt")
        assertTrue(
            wiring.contains("profileSummary = { name -> viewModel.profileSummary(name) }"),
            "nối dây qua ViewModel như mọi đường khác",
        )
        val vm = code("HomeViewModel.kt").let { it.substring(it.indexOf("fun profileSummary(")) }
        assertTrue(
            vm.contains("repository.profileLayout(") && vm.contains("ProfileNames.summary("),
            "ViewModel đọc qua cổng dữ liệu và gấp câu chữ bằng `:core`",
        )
    }

    @Test
    fun `chuoi co ca hai ngon ngu`() {
        listOf("src/main/res/values/strings_kachi.xml", "src/main/res/values-en/strings_kachi.xml").forEach { f ->
            val t = app(f).toFile().readText()
            listOf("kachi_home_profile_note", "kachi_profile_sub_active", "kachi_profile_sub_switch").forEach {
                assertTrue(t.contains("name=\"$it\""), "$f thiếu $it")
            }
        }
    }
}
