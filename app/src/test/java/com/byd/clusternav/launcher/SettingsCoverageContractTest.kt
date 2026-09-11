package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * PHÉP KIỂM CỦA **R2** (S1 · §4.3) — *"không cấu hình nào nằm lẻ tẻ"*: đối chiếu [SettingsCatalog] với **nơi lưu
 * THẬT**, không với chính nó.
 *
 * ## ⚠⚠ Vì sao các bài này PHẢI nằm ở `:app`, không ở `:core`
 * Chúng quét mã nguồn của `:app`. [ĐO] lượt soát 2026-09-11: khi chúng còn ở `:core`, Gradle **không có đầu vào nào**
 * nối `:core:test` với nguồn `:app`, nên:
 *
 *  1. đóng mốc `./gradlew :core:test` (xanh);
 *  2. thêm một hằng khoá mới vào `app/.../WorkspacePrefs.kt` (đúng ca mà bài này sinh ra để bắt);
 *  3. chạy lại `./gradlew :core:test` ⇒ **`Task :core:test UP-TO-DATE`, BUILD SUCCESSFUL**.
 *
 * Nghĩa là phép kiểm quan trọng nhất của S1 **im lặng không chạy** ở đúng tình huống nó bảo vệ. Ở `:app` thì nguồn
 * `:app` là đầu vào biên dịch thật của `:app:testDebugUnitTest` ⇒ đổi nó là task chạy lại. Một bài test không chạy còn
 * tệ hơn không có bài test: nó cho một dấu xanh sai.
 *
 * ## ⚠⚠ Vì sao phạm vi quét KHÔNG còn là một tệp
 * [ĐO] lượt soát 2026-09-11 (vòng 2): bản đầu chỉ quét `WorkspacePrefs.kt`, trong khi phía launcher lưu bền ở **ba**
 * chỗ — thêm `system/FreeformSeedStore.kt` (tệp prefs **thứ hai** `clusternav_state`) và bốn khoá
 * `Settings.Global`/`Settings.Secure` do vòng gieo cờ + vòng kiểm quyền ghi. Một bộ quét một-tệp trả lời được câu
 * *"có ai thêm khoá vào tệp CHÍNH mà quên gom không"* nhưng **không** trả lời được câu quan trọng hơn: *"có ai vừa mở
 * một tệp prefs thứ ba không"* — và đó chính là cách một cấu hình lẻ tẻ mới sẽ xuất hiện, im lặng và xanh.
 *
 * Nên bộ quét nay đi theo **bề mặt lưu bền**, không theo tên tệp: mọi tệp trong ba gốc ở [scanRoots], mọi
 * `getSharedPreferences(…)` · `put*(…)` · `Settings.*.put*(…)` · lệnh shell `settings put|delete`. Mỗi khoá tìm được
 * phải HOẶC thuộc một nhóm, HOẶC nằm trong [SettingsCatalog.NOT_SETTINGS] kèm lý do; mỗi **tệp prefs** tìm được phải
 * nằm trong [SettingsCatalog.PREFS_FILES] kèm lý do.
 */
class SettingsCoverageContractTest {

    // ── Bộ quét ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Ba gốc quét, kèm lý do — đây là **toàn bộ** bề mặt lưu bền của phía launcher [ĐO] 2026-09-11.
     *
     * ⚠ `FreeformSeedStore` nằm ở `system/` **chứ không** ở `launcher/`, và cờ `Settings.Global` thì được ghi từ
     * `:core system/FreeformSeedPolicy.kt`. Nếu chỉ quét thư mục `launcher/` như bản nháp đầu thì hai dòng
     * [SettingsCatalog.NOT_SETTINGS] khai cho chúng sẽ là **trang trí**: không có gì quét tới, nên không có gì để tha.
     * Phạm vi phải bám nơi lưu THẬT, không bám tên gói.
     */
    private fun scanRoots(): List<Pair<String, Path>> = listOf(
        // Tầng launcher: WorkspacePrefs (tệp chính) + PermissionPreflight (Settings.Secure qua shell).
        "app launcher/" to SourceRoots.path("src/main/java/com/byd/clusternav/launcher"),
        // Hạ tầng cửa sổ phía :app: FreeformSeedStore = tệp prefs THỨ HAI.
        "app system/" to SourceRoots.path("src/main/java/com/byd/clusternav/system"),
        // Nơi ghi DUY NHẤT của hai cờ boot (luật một-nơi-ghi-duy-nhất cho trạng thái bền).
        "core FreeformSeedPolicy" to SourceRoots.path("src/main/kotlin/com/byd/clusternav/system/FreeformSeedPolicy.kt"),
    )

    private fun ktFiles(root: Path): List<Path> =
        if (Files.isRegularFile(root)) listOf(root)
        else Files.walk(root).use { s ->
            s.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.sorted().toList()
        }

    /**
     * MÃ đã bỏ chú thích — chặn kiểu "đạt test" bằng cách viết khoá vào chú thích, và chặn cả báo-sai từ văn xuôi
     * (KDoc của `FreeformSeedPolicy` có nhắc nguyên văn `settings put|delete global enable_freeform_support`).
     *
     * Đọc THẲNG từ [Path] thay vì qua `SourceRoots.codeOf`: hàm đó nhận đường dẫn **tương đối theo module** rồi tự dò
     * gốc, nên đưa một đường đã dò rồi vào lại là dò hai lần — chạy được nhưng phụ thuộc thứ tự ứng viên.
     */
    private fun code(f: Path): String = f.toFile().readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { it.substringBefore("//") }

    /** Hằng chuỗi khai trong CHÍNH tệp đó: `const val K_X = "x"` / `private val PREF = "y"`. */
    private fun consts(src: String): Map<String, String> =
        Regex("""\bval\s+(\w+)\s*(?::\s*String\s*)?=\s*"([^"]*)"""").findAll(src)
            .associate { it.groupValues[1] to it.groupValues[2] }

    /**
     * Biểu thức khoá → chuỗi thật. Nhận cả `"literal"`, `K_HANG`, và `key("literal")`/`key(K_HANG)` (bọc theo hồ sơ —
     * tiền tố `"<hồ sơ>__"` đổi theo hồ sơ nên phần bất biến là **hậu tố**, đúng như [SettingsCatalog] khai).
     */
    private fun resolveKeys(expr: String, consts: Map<String, String>): Set<String> = buildSet {
        Regex(""""([^"$]*)"""").findAll(expr).forEach { if (it.groupValues[1].isNotBlank()) add(it.groupValues[1]) }
        Regex("""\b([A-Z][A-Z0-9_]{1,})\b""").findAll(expr).forEach { m -> consts[m.groupValues[1]]?.let(::add) }
    }

    /** Đối số đầu tiên của lời gọi bắt đầu tại [openParen] (đếm ngoặc, không cắt bằng dấu phẩy đầu tiên). */
    private fun firstArg(src: String, openParen: Int): String {
        var depth = 0
        for (i in openParen until src.length) {
            when (src[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return src.substring(openParen + 1, i)
                ',' -> if (depth == 1) return src.substring(openParen + 1, i)
            }
        }
        return ""
    }

    private fun argsAfter(src: String, openParen: Int): String {
        var depth = 0
        for (i in openParen until src.length) {
            when (src[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return src.substring(openParen + 1, i)
            }
        }
        return ""
    }

    /** Kết quả một lượt quét: khoá lưu bền + tên tệp prefs + số tệp đã đọc. */
    private data class Scan(val keys: Set<String>, val prefsFiles: Set<String>, val files: Int)

    private fun scan(): Scan {
        val keys = sortedSetOf<String>()
        val files = sortedSetOf<String>()
        var count = 0
        scanRoots().forEach { (_, root) ->
            ktFiles(root).forEach { f ->
                count++
                val src = code(f)
                val c = consts(src)

                // (a) TỆP prefs — câu hỏi "có tệp thứ ba nào vừa xuất hiện không".
                Regex("""getSharedPreferences\(""").findAll(src).forEach { m ->
                    val at = m.range.last
                    files += resolveKeys(firstArg(src, at), c)
                }
                // (b) KHOÁ trong tệp prefs. CHỈ xét tệp thật sự mở prefs: `putString` trong một tệp không bao giờ
                // chạm SharedPreferences là API khác (Bundle/Intent), không phải lưu bền cấu hình.
                if (src.contains("getSharedPreferences(")) {
                    Regex("""\bput(?:String|Boolean|Int|Long|Float)\(""").findAll(src).forEach { m ->
                        keys += resolveKeys(firstArg(src, m.range.last), c)
                    }
                }
                // (c) Settings.Global/Secure/System qua API.
                Regex("""Settings\.(?:Global|Secure|System)\.put\w*\(""").findAll(src).forEach { m ->
                    keys += resolveKeys(argsAfter(src, m.range.last), c) - SETTINGS_API_NOISE
                }
                // (d) Settings.* qua LỆNH SHELL — dạng thật đang dùng (`settings put secure <khoá> …`).
                Regex("""settings\s+(?:put|delete)\s+(?:global|secure|system)\s+([a-z_][a-z0-9_]*)""")
                    .findAll(src).forEach { keys += it.groupValues[1] }
            }
        }
        return Scan(keys, files, count)
    }

    // ── R2 · phủ mọi khoá lưu bền của PHÍA LAUNCHER (không chỉ một tệp) ──────────────────────────

    @Test
    fun `phu du moi khoa luu ben cua phia launcher`() {
        val s = scan()

        // Chốt chống bộ quét hỏng mà vẫn xanh: quét rỗng thì "0 mồ côi" là câu nói vô nghĩa.
        assertTrue(s.files >= 20, "bộ quét chỉ đọc ${s.files} tệp — nghi chính bộ quét hỏng (sai gốc quét?)")
        assertTrue(s.keys.size >= 14, "bộ quét chỉ thấy ${s.keys.size} khoá — nghi chính bộ quét hỏng: ${s.keys}")
        // Mỗi mốc dưới đây đại diện cho MỘT nhánh của bộ quét. Thiếu một mốc = nhánh đó đã chết mà bài vẫn xanh.
        mapOf(
            "preset" to "khoá theo hồ sơ, dạng key(\"…\")",
            "grid_layout" to "khoá theo hồ sơ, dạng key(HẰNG)",
            "theme_mode" to "khoá phẳng, dạng HẰNG",
            "launcher_autostart" to "putBoolean (không chỉ putString)",
            "freeform_state" to "TỆP PREFS THỨ HAI — putInt ở system/FreeformSeedStore",
            "accessibility_enabled" to "Settings.Secure qua lệnh shell (PermissionPreflight)",
            "enable_freeform_support" to "Settings.Global qua lệnh shell (core FreeformSeedPolicy)",
        ).forEach { (anchor, branch) ->
            assertTrue(anchor in s.keys, "bộ quét không thấy '$anchor' ⇒ nhánh '$branch' đã chết: ${s.keys}")
        }

        val orphans = SettingsCatalog.orphans(s.keys)
        assertTrue(
            orphans.isEmpty(),
            "khoá lưu bền chưa được gom vào nhóm nào và cũng không có lý do loại trong SettingsCatalog.NOT_SETTINGS: " +
                "$orphans",
        )
    }

    /**
     * Câu hỏi mà bản một-tệp không hỏi được: **có tệp prefs thứ ba nào vừa xuất hiện không.**
     *
     * So HAI CHIỀU: tệp lạ ⇒ đỏ (ai đó vừa mở chỗ lưu mới mà không khai lý do); tệp đã khai mà quét không thấy ⇒ cũng
     * đỏ (hoặc nó đã bị xoá và danh sách đang rữa, hoặc bộ quét hỏng — cả hai đều phải biết).
     */
    @Test
    fun `khong co tep prefs thu ba nao xuat hien im lang`() {
        val s = scan()
        assertEquals(
            SettingsCatalog.PREFS_FILES.keys.sorted(), s.prefsFiles.sorted(),
            "danh sách tệp prefs của phía launcher đã lệch với mã nguồn — khai lý do trong " +
                "SettingsCatalog.PREFS_FILES, hoặc dùng lại tệp đang có",
        )
    }

    // ── Khoá nằm ngoài WorkspacePrefs ────────────────────────────────────────────────────────────

    /**
     * Khoá lấy gió trong nằm ở `Prefs` của ClusterNav, không ở `WorkspacePrefs`, nên bài trên không phủ nó (gốc quét
     * cố ý KHÔNG gồm cả `Prefs.kt`: đó là tệp của ClusterNav với hàng chục khoá của tính năng cũ, kéo vào sẽ biến bài
     * R2 thành bài kiểm ClusterNav).
     *
     * ⚠ [ĐO] spec §2 ghi tên khoá là `recirc_on_start`, còn mã thật là `recirc_on_start_enabled`
     * (`Prefs.K_RECIRC_ON_START`). Lấy theo MÃ; bài này đọc thẳng hằng đó nên nếu ai đổi tên khoá mà quên sửa danh
     * mục thì đỏ.
     */
    @Test
    fun `khoa lay gio trong khai dung ten that trong Prefs`() {
        val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/Prefs.kt")
        val real = Regex("""K_RECIRC_ON_START\s*=\s*"([^"]+)"""").find(src)?.groupValues?.get(1)
        assertNotNull(real, "không đọc được hằng K_RECIRC_ON_START — bài test đang quét vùng không tồn tại")
        assertEquals(
            SettingsGroup.CAR, SettingsCatalog.groupOf(real!!),
            "khoá lấy gió trong ('$real') phải thuộc nhóm Tiện nghi xe",
        )
        assertNull(
            SettingsCatalog.groupOf("recirc_on_start"),
            "tên trong spec §2 bị thiếu hậu tố _enabled — danh mục không được dùng tên đó",
        )
    }

    private companion object {
        /**
         * `Settings.Secure.putInt(resolver, key, value)` — đối số đầu là content-resolver, không phải khoá. Bộ quét
         * lấy CẢ danh sách đối số cho dạng này (khoá ở giữa) nên phải bỏ các mảnh không phải khoá.
         */
        val SETTINGS_API_NOISE = setOf("contentResolver")
    }
}
