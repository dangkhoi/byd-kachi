package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ PROFILE-IMPORT-TYPES — kiểu KHAI SẴN của hậu tố launcher phải là kiểu mã THẬT ghi/đọc, và mọi lượt đọc phải an toàn ═
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §12.4.4. Khuôn `ClusterProfileScopeCoverageTest` (VC-R8), áp cho
 * phía launcher: [ProfileScopeLauncher.DECLARED_TYPES] (`:core`) là thứ lượt nhập/xuất dùng để BỎ giá trị sai kiểu, nên
 *  • khai SAI kiểu ⇒ lượt nhập bỏ đúng giá trị HỢP LỆ của người gửi (mất cấu hình im lặng);
 *  • đọc khoá theo hồ sơ bằng `sp.getString`/`sp.getBoolean` trần ⇒ một giá trị sai kiểu đã nằm trên đĩa (tệp nhập ≤ 2.84)
 *    làm `load()` NÉM ở mọi lần mở.
 *
 * Quét MÃ (đã bỏ chú thích, [SourceRoots.codeOf]) của năm tệp `WorkspacePrefs*.kt` — mọi chỗ chạm `kachi_workspace`:
 *  1. mọi `put<Kiểu>(key(…)|keyOf(…, …))` và mọi lượt đọc có kiểu (`stringOrNull`/`booleanOrNull`/`profileString`/
 *     `profileBoolean`) ⇒ kiểu == bảng khai; hậu tố/hằng không giải được ⇒ đỏ (bài không được nhìn nửa sự thật);
 *  2. MỌI hậu tố khai (trừ ảnh chụp `__cn__*`, có cửa đọc riêng `storedSnapshot`) phải có ít nhất một lượt ghi VÀ một
 *     lượt đọc mà bài thấy được ⇒ hậu tố mới quên nối/khai ⇒ đỏ;
 *  3. `sp.get<Kiểu>(` trần chỉ còn được dùng cho khoá theo XE (hằng `K_*` mà [ProfileScope.scopeOf] ≠ PROFILE).
 */
class LauncherProfileTypesCoverageTest {

    private val files = listOf(
        "WorkspacePrefs.kt", "WorkspacePrefsLang.kt", "WorkspacePrefsProfile.kt", "WorkspacePrefsSnapshot.kt",
        "WorkspacePrefsMigrations.kt",
        // F1 (2026-10-02) — `app_shortcuts` đọc/ghi ở tệp riêng (WorkspacePrefs.kt 499/500 dòng).
        "WorkspacePrefsShortcuts.kt",
        // F2/F3 (2026-10-02, nhóm C) — `ignition_apps` + `ignition_music` đọc/ghi ở tệp riêng (cùng lẽ).
        "WorkspacePrefsTrip.kt",
        // 2.87 · R-AH3 (2026-10-03) — `swap_button_autohide` đọc/ghi ở tệp riêng (cùng lẽ: WorkspacePrefs.kt 499/500 dòng).
        "WorkspacePrefsSlotHead.kt",
        // 2.89 · B3 DOCK-SCALE — `dock_scale` đọc/ghi ở tệp riêng (cùng lẽ: WorkspacePrefs.kt 499/500 dòng).
        "WorkspacePrefsDockScale.kt",
    )

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")

    private val src by lazy { files.joinToString("\n") { code(it) } }

    /** Hằng chuỗi literal của năm tệp (`K_*`, `LEGACY_*`) — bài nói về GIÁ TRỊ khoá thật, không về tên Kotlin. */
    private val consts by lazy {
        Regex("""const val (\w+)\s*=\s*"([^"$]+)"""").findAll(src).associate { it.groupValues[1] to it.groupValues[2] }
    }

    /** Biến cục bộ dựng bằng `ProfileScope.snapshotSuffix(…)` (lượt di trú ghi lại ảnh) — giải thành mọi hậu tố ảnh chụp. */
    private val snapshotVars by lazy {
        Regex("""val (\w+)\s*=\s*ProfileScope\.snapshotSuffix\(""").findAll(src).mapTo(HashSet()) { it.groupValues[1] }
    }

    private val declared = ProfileScopeLauncher.DECLARED_TYPES

    private val typeOf = mapOf(
        "Boolean" to PrefType.BOOLEAN, "Int" to PrefType.INT, "Long" to PrefType.LONG, "Float" to PrefType.FLOAT,
        "String" to PrefType.STRING, "StringSet" to PrefType.STRING_SET,
    )

    /** Đối số hậu tố bên trong `key(…)`/`keyOf(<hồ sơ>, …)`: literal (có thể dựng `slot_$i`) hoặc hằng (có thể có tiền tố lớp). */
    private val keyArg = """key(?:Of)?\(\s*(?:[\w.]+\s*,\s*)?("[^"]*"|[\w.]+)\s*\)"""

    private class Seen(val suffix: String, val type: PrefType, val at: String)

    /** Giải đối số thành hậu tố; `null` = không giải được (bài đỏ). `"slot_$i"` ⇒ mọi hậu tố khai mang tiền tố đó. */
    private fun resolve(arg: String): List<String>? = when {
        arg.startsWith("\"") && '$' in arg -> {
            val pre = arg.trim('"').split('$').first()
            declared.keys.filter { pre.isNotEmpty() && it.startsWith(pre) }.takeIf { it.isNotEmpty() }
        }
        arg.startsWith("\"") -> listOf(arg.trim('"'))
        arg in snapshotVars -> ProfileScope.SNAPSHOT_SUFFIXES
        else -> consts[arg.split('.').last()]?.let(::listOf)
    }

    private fun scan(regex: Regex, type: (MatchResult) -> PrefType, arg: (MatchResult) -> String, unresolved: MutableList<String>) =
        regex.findAll(src).flatMap { m ->
            val keys = resolve(arg(m))
            if (keys == null) { unresolved += m.value; emptySequence() } else keys.asSequence().map { Seen(it, type(m), m.value) }
        }.toList()

    private fun writes(unresolved: MutableList<String>) = scan(
        Regex("""\bput(Boolean|Int|Long|Float|StringSet|String)\(\s*$keyArg"""), { typeOf.getValue(it.groupValues[1]) },
        { it.groupValues[2] }, unresolved,
    )

    private fun reads(unresolved: MutableList<String>) = scan(
        Regex("""\b(stringOrNull|booleanOrNull)\(\s*$keyArg"""),
        { if (it.groupValues[1] == "stringOrNull") PrefType.STRING else PrefType.BOOLEAN }, { it.groupValues[2] }, unresolved,
    ) + scan(
        // Hai cửa lùi-khoá-chung của S4 nhận HẬU TỐ trần rồi tự gọi `key(suffix)`; bỏ chính dòng khai `fun profileX(`.
        Regex("""(?<!fun )\b(profileString|profileBoolean)\(\s*([\w.]+)"""),
        { if (it.groupValues[1] == "profileString") PrefType.STRING else PrefType.BOOLEAN }, { it.groupValues[2] }, unresolved,
    )

    /** Khoá ngoài bảng khai chỉ được là khoá TẠM đời cũ (`scenes`/`boot_scene` — chỉ đọc để di trú). */
    private fun outOfTable(seen: List<Seen>) =
        seen.filter { it.suffix !in declared && ProfileScope.scopeOf(it.suffix) != ProfileScope.Scope.TRANSIENT }.map { it.at }

    @Test
    fun `kieu ghi va kieu doc khop bang khai san`() {
        val unresolved = mutableListOf<String>()
        val w = writes(unresolved)
        val r = reads(unresolved)
        assertEquals(emptyList<String>(), unresolved, "hậu tố/hằng không giải được — bài đang nhìn nửa sự thật")
        assertTrue(w.size >= 25 && r.size >= 20, "bộ quét phải thấy đủ (ghi ${w.size}, đọc ${r.size}) — ít hơn là nó hỏng")
        assertEquals(emptyList<String>(), outOfTable(w + r), "khoá theo hồ sơ chưa có trong ProfileScopeLauncher.DECLARED_TYPES")
        val wrong = (w + r).filter { it.suffix in declared && it.type != declared.getValue(it.suffix) }
            .map { "${it.at} ⇒ ${it.suffix}: mã ${it.type}, khai ${declared[it.suffix]}" }
        assertEquals(emptyList<String>(), wrong, "kiểu khai ≠ kiểu mã ⇒ lượt nhập bỏ giá trị HỢP LỆ, hoặc lượt đọc lấy mặc định oan")
    }

    @Test
    fun `moi hau to khai san deu co luot ghi va luot doc co kieu`() {
        val unresolved = mutableListOf<String>()
        val w = writes(unresolved).mapTo(HashSet()) { it.suffix }
        val r = reads(unresolved).mapTo(HashSet()) { it.suffix }
        val plain = declared.keys - ProfileScope.SNAPSHOT_SUFFIXES.toSet()
        assertEquals(emptySet<String>(), plain - w, "hậu tố khai mà bài không thấy lượt GHI có kiểu")
        assertEquals(emptySet<String>(), plain - r, "hậu tố khai mà bài không thấy lượt ĐỌC an toàn kiểu")
        // Ảnh chụp: ghi bằng `putString` ở đúng một chỗ, đọc qua `storedSnapshot` (bài `ClusterProfileScopeCoverageTest`).
        assertTrue(ProfileScope.SNAPSHOT_SUFFIXES.all { declared[it] == PrefType.STRING })
        val snap = SourceRoots.body(code("WorkspacePrefsSnapshot.kt"), "internal fun WorkspacePrefs.snapshotClusterNav(profile: String)")
        assertTrue(snap.contains("e.putString(keyOf(profile, ProfileScope.snapshotSuffix(file)), PrefSnapshot.encode("))
    }

    @Test
    fun `khong con sp get tran tren khoa theo ho so`() {
        val raw = Regex("""\bsp\.get(String|Boolean|Int|Long|Float|StringSet)\(\s*([^,)]+)""").findAll(src).toList()
        assertTrue(raw.size >= 6, "bộ quét phải thấy các lượt đọc khoá theo XE (đang thấy ${raw.size})")
        val bad = raw.filter { m ->
            val key = consts[m.groupValues[2].trim().split('.').last()]
            key == null || ProfileScope.scopeOf(key) == ProfileScope.Scope.PROFILE || ProfileScope.scopeOf(key) == ProfileScope.Scope.UNKNOWN
        }.map { it.value }
        assertEquals(
            emptyList<String>(), bad,
            "đọc trần khoá theo hồ sơ NÉM ClassCastException khi tệp nhập ≤ 2.84 để giá trị sai kiểu — dùng stringOrNull/booleanOrNull",
        )
    }

    @Test
    fun `cua doc chi bat ClassCastException va lop nhap ghi log dropped`() {
        val helper = code("PrefsTypedRead.kt")
        listOf(
            "internal fun SharedPreferences.stringOrNull(key: String, onMistyped: (String) -> Unit = ::reportMistyped): String?",
            "internal fun SharedPreferences.booleanOrNull(key: String, onMistyped: (String) -> Unit = ::reportMistyped): Boolean?",
        ).forEach { sig ->
            val body = SourceRoots.body(helper, sig)
            assertTrue(Regex("""catch\s*\(\s*e\s*:\s*ClassCastException\s*\)""").containsMatchIn(body), "$sig phải bắt ClassCastException")
        }
        assertFalse(Regex("""catch\s*\(\s*\w+\s*:\s*(Exception|Throwable|RuntimeException)\s*\)|runCatching""").containsMatchIn(helper),
            "chỉ bắt đúng thứ AOSP ném — lỗi khác phải nổi lên")
        assertFalse(Regex("""\.edit\(\)""").containsMatchIn(helper), "cửa đọc không ghi")
        val imp = SourceRoots.body(code("WorkspacePrefsProfile.kt"), "internal fun WorkspacePrefs.importProfile(data: String, name: String? = null): ProfileImportReport?")
        assertTrue(imp.contains("logDropped(\"import launcher\", plan.dropped)"), "hậu tố sai kiểu khi nhập phải để lại vết")
    }
}
