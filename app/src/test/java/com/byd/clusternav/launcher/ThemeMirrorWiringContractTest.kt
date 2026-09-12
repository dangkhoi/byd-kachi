package com.byd.clusternav.launcher

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * IA v2 · R3 (docs/specs/kachi-settings-ia-v2.html) — **một công tắc chủ đề cho cả hai màn**.
 *
 * [ĐO 2026-09-12] Trước đợt này có hai store độc lập: `theme_mode` (kachi_workspace, launcher) và `theme_choice`
 * (clusternav_theme, màn ClusterNav cũ đọc ở `attachBaseContext`). Bài này khoá: đường lưu bền `persist()` của
 * launcher phải gương lựa chọn sang store kia qua `ThemeMode.setChoice` + phép ánh xạ thuần `themeChoiceCode`
 * (:core, có test riêng). Đặt ở tầng lưu bền chứ không ở UI ⇒ mọi đường đổi chủ đề đều gương.
 */
class ThemeMirrorWiringContractTest {

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }

    private val repo by lazy {
        app("src/main/java/com/byd/clusternav/launcher/PrefsWorkspaceRepository.kt").toFile().readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }
    }

    @Test
    fun `persist guong chu de sang store cua man ClusterNav cu`() {
        val persist = repo.substring(repo.indexOf("override fun persist("))
            .let { it.substring(0, it.indexOf("override fun", 10).coerceAtLeast(1)) }
        assertTrue(persist.contains("prefs.setThemeMode(state.themeMode)"), "vẫn lưu store launcher")
        assertTrue(persist.contains("ThemeMode.setChoice("), "phải gương sang clusternav_theme qua ThemeMode.setChoice")
        assertTrue(
            persist.contains("ClusterNavSettingsModel.themeChoiceCode(state.themeMode)"),
            "phép ánh xạ phải là hàm thuần :core (ClusterNavSettingsModel.themeChoiceCode), không viết tay tại chỗ",
        )
    }

    @Test
    fun `khong con noi 'man ClusterNav co lua chon rieng' trong chuoi giao dien`() {
        // Câu này đúng khi còn hai công tắc; sau R3 nó nói SAI với người dùng ⇒ phải biến mất khỏi mọi tệp chuỗi.
        listOf("src/main/res/values/strings_kachi.xml", "src/main/res/values-en/strings_kachi.xml").forEach { rel ->
            val xml = app(rel).toFile().readText()
            assertTrue(
                !xml.contains("name=\"kachi_theme_note_clusternav\""),
                "$rel còn chuỗi kachi_theme_note_clusternav (màn ClusterNav 'có lựa chọn riêng') — sai sau R3, xoá cùng chỗ dùng",
            )
        }
    }
}
