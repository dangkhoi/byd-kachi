package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.camera.CameraSettingsIa
import com.byd.clusternav.modules.clustercast.simplified.CastEnableDeferral
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * ═══ V-CLUSTER (owner 2026-09-30: *"Phần cụm lưu hết thành profile nhé"*) — DÂY NỐI phía `:app` của lớp lưu trữ ═════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §11.3 VC-R1 · VC-R3 · VC-R4 · VC-R7 · VC-R8. Phép tính thuần đã có
 * bài chạy thật ở `:core` (`ClusterSnapshotPlanTest`, `ProfileScopeMigrationTest`, `CastGeometryGuardTest`,
 * `CastEnableDeferralTest`). Bài này hỏi câu mà `:core` không hỏi được: *mã Android có THẬT SỰ đi qua các phép đó chưa*.
 *
 * ## VC-R1 — đóng lỗ "bài canh chỉ quét khoá ĐÃ khai" (`ProfileScopeTest` chỉ nhìn danh mục)
 * Quét **mã nguồn thật** (khoá literal ở `get*`/`put*`/`remove`/`contains`) của hai tệp prefs cụm/chiếu và đòi mỗi khoá
 * có phạm vi ≠ UNKNOWN. Thêm một khoá mới mà quên xếp loại ⇒ đỏ tại đây, không đợi người lái đổi hồ sơ trên xe.
 *
 * Mọi phép cắt vùng dùng [SourceRoots.body] (đếm ngoặc, nổ nếu mốc vắng) — không `substringAfter/Before`.
 */
class ClusterProfileScopeCoverageTest {

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$file")

    private val runtime by lazy { code("modules/clustercast/simplified/SimpleCastRuntime.kt") }
    private val catalog by lazy { code("cast/platform/CastAppCatalog.kt") }
    private val snapshot by lazy { code("launcher/WorkspacePrefsSnapshot.kt") }
    private val migrations by lazy { code("launcher/WorkspacePrefsMigrations.kt") }
    private val profileIo by lazy { code("launcher/WorkspacePrefsProfile.kt") }
    private val repo by lazy { code("launcher/PrefsWorkspaceRepository.kt") }

    /** Khoá literal ở lời gọi prefs; `$x` (khoá dựng động) thay bằng một tên gói mẫu để hỏi phạm vi theo tiền tố. */
    private fun literalKeys(src: String): Set<String> =
        Regex("""\b(?:get(?:String|Boolean|Int|Long|Float|StringSet)|put(?:String|Boolean|Int|Long|Float|StringSet)|remove|contains|immutableCsv)\("([^"]+)"""")
            .findAll(src).map { it.groupValues[1].replace(Regex("""\$\w+"""), "a.b") }.toSet()

    // ── VC-R1 · phủ khoá ───────────────────────────────────────────────────────────────────────

    @Test
    fun `moi khoa literal cua prefs chieu cum deu da xep loai`() {
        val keys = literalKeys(SourceRoots.body(runtime, "private class SharedPrefsSimpleCastPrefs"))
        assertTrue(keys.size >= 14, "bộ quét phải thấy đủ khoá (đang thấy ${keys.size}: $keys) — ít hơn là nó hỏng")
        assertTrue("config_bounds_a.b" in keys && "cast_bubble_visible" in keys, "mẫu quét phải phủ cả khoá dựng động: $keys")
        assertEquals(emptySet<String>(), ProfileScope.unclassified(keys), "khoá chiếu cụm CHƯA xếp loại")
    }

    @Test
    fun `moi khoa literal cua CastAppCatalog deu da xep loai`() {
        val keys = literalKeys(catalog)
        assertTrue(keys.size >= 15, "bộ quét phải thấy đủ khoá (đang thấy ${keys.size}: $keys)")
        assertEquals(emptySet<String>(), ProfileScope.unclassified(keys), "khoá CastAppCatalog CHƯA xếp loại")
        assertEquals(ProfileScope.Scope.PROFILE, ProfileScope.scopeOf("bubbleX"))
        assertEquals(ProfileScope.Scope.DEVICE, ProfileScope.scopeOf("scale-dpi:a.b"))
    }

    @Test
    fun `moi khoa camera trong ma deu da xep loai va dung danh sach IA`() {
        val src = code("PrefsAutomation.kt") + code("PrefsCameraDewarp.kt")
        val keys = Regex(""""(camera_[a-z_]+)"""").findAll(src).map { it.groupValues[1] }.toSet()
        assertTrue(keys.size >= 29, "bộ quét camera thấy ${keys.size}")
        assertEquals(emptySet<String>(), ProfileScope.unclassified(keys))
        assertEquals(emptySet<String>(), keys - (CameraSettingsIa.USER_KEYS + CameraSettingsIa.NO_UI_KEYS).toSet() - "camera_rotation",
            "khoá camera ngoài danh sách IA (trừ khoá di trú đời 2.67)")
    }

    /** Khoá theo hồ sơ KHÔNG có mục Cài đặt phải có mặt NGUYÊN VĂN ở tệp khai nó (chặt ngang `ClusterNavKeysContractTest`). */
    @Test
    fun `khoa them theo ho so co nguyen van o tep khai`() {
        assertTrue(catalog.contains("\"${ProfileScopeCluster.CAST_CATALOG_FILE}\""), "tên tệp ảnh chụp phải khớp tệp thật")
        listOf("bubbleX", "bubbleY").forEach { assertTrue(catalog.contains("\"$it\""), it) }
        val cam = code("PrefsAutomation.kt") + code("PrefsCameraDewarp.kt")
        ProfileScopeCluster.CAMERA_PROFILE_KEYS.keys.forEach { assertTrue(cam.contains("\"$it\""), it) }
        assertTrue(code("PrefsAutomation.kt").contains("getSharedPreferences(\"${ProfileScopeCluster.CLUSTERNAV_FILE}\""))
    }

    // ── VC-R3 · di trú một lần ─────────────────────────────────────────────────────────────────

    @Test
    fun `di tru cum chay o init, SAU lich dan duong, TRUOC load`() {
        val init = SourceRoots.body(repo, "    init {")
        val scenes = init.indexOf("prefs.migrateScenesOnce()")
        val nav = init.indexOf("prefs.migrateNavScheduleOnce()")
        val cluster = init.indexOf("prefs.migrateClusterProfileOnce()")
        assertTrue(scenes in 0 until nav && nav < cluster, "thứ tự init phải là cảnh → lịch → cụm: $scenes/$nav/$cluster")
        assertTrue(repo.indexOf("init {") < repo.indexOf("override fun load()"))
    }

    @Test
    fun `di tru cum mot Editor, mot dau, di qua phep thuan`() {
        val fn = SourceRoots.body(migrations, "internal fun WorkspacePrefs.migrateClusterProfileOnce()")
        assertTrue(fn.contains("if (sp.getBoolean(K_MIGRATED_CLUSTER, false)) return"), "chạy ĐÚNG MỘT LẦN")
        assertTrue(fn.contains("e.putBoolean(K_MIGRATED_CLUSTER, true).apply()"), "dấu trên CHÍNH Editor đã ghi dữ liệu")
        assertEquals(1, Regex("""\bsp\.edit\(\)""").findAll(fn).count(), "một Editor duy nhất")
        assertTrue(fn.contains("ProfileScopeMigration") && fn.contains(".rotDown("), "phép rót ở :core (có test chạy thật)")
        assertTrue(fn.contains("ProfileScopeCluster.MOVED_TO_PROFILE") && fn.contains("ProfileScopeCluster.DEFERRED"))
        assertTrue(migrations.contains("K_MIGRATED_CLUSTER = \"${ProfileScopeCluster.MIGRATED_KEY}\""), "literal phải BẰNG hằng :core")
    }

    /**
     * 2.92 · PROFILE-NEW-KEYS — khoá vào phạm vi hồ sơ ở bản SAU (QA máy ảo 2.92 [ĐO]: kiểu hình/thu phóng camera của hồ
     * sơ vừa rời rò sang hồ sơ lưu bằng 2.91). Lượt rót theo sổ phải chạy ở `init`, SAU lượt cụm, TRƯỚC `load()`.
     */
    @Test
    fun `khoa moi vao pham vi ho so duoc rot theo so, o init, mot Editor`() {
        val init = SourceRoots.body(repo, "    init {")
        val cluster = init.indexOf("prefs.migrateClusterProfileOnce()")
        val fill = init.indexOf("prefs.fillNewProfileKeysOnce()")
        assertTrue(cluster in 0 until fill, "thứ tự init: cụm → khoá mới ($cluster/$fill)")
        val fn = SourceRoots.body(migrations, "internal fun WorkspacePrefs.fillNewProfileKeysOnce()")
        assertEquals(1, Regex("""\bsp\.edit\(\)""").findAll(fn).count(), "một Editor duy nhất")
        assertTrue(fn.contains("ProfileScopeMigration.pendingKeys(ProfileScope.CLUSTERNAV_KEYS, done)"), "sổ duyệt ĐÚNG bảng ảnh chụp")
        assertTrue(fn.contains(".fillNewKeys(") && fn.contains("ProfileScopeCluster.DEFERRED"), "phép rót ở :core (có test chạy thật)")
        assertTrue(fn.contains("storedSnapshot(stored,"), "ảnh sai kiểu không được làm init ném")
        assertFalse(fn.contains("getStringSet("), "đọc sổ bằng ép kiểu an toàn, không getStringSet (ném ClassCastException ở init)")
        assertTrue(
            fn.contains("e.putStringSet(K_PROFILE_KEYS_FILLED, ProfileScopeMigration.ledgerOf(ProfileScope.CLUSTERNAV_KEYS)).apply()"),
            "sổ ghi trên CHÍNH Editor đã ghi dữ liệu",
        )
        assertTrue(
            migrations.contains("K_PROFILE_KEYS_FILLED = \"${ProfileScopeMigration.FILLED_LEDGER_KEY}\""),
            "literal phải BẰNG hằng :core",
        )
    }

    /**
     * 2.92 · PROFILE-IMPORT-GAP-KEYS — NHẬP tệp xuất từ bản cũ (thiếu khoá theo hồ sơ mới): mọi ảnh ClusterNav của hồ sơ nhập đi qua
     * CÙNG phép điền chỗ trống của lượt nâng cấp, SAU lớp làm sạch + merge (giá trị của xe cũng đã qua bộ kiểm). Thử ĐỎ: ghi `merged`.
     */
    @Test
    fun `nhap tep cu - anh ClusterNav dien cho trong bang gia tri song, sau lam sach va merge`() {
        val importFn = SourceRoots.body(profileIo, "internal fun WorkspacePrefs.importProfile(data: String, name: String? = null)")
        val clean = importFn.indexOf("cleanImportedSnapshot(suffix, v)")
        val merge = importFn.indexOf("mergeImportedCast(suffix, clean)")
        val fill = importFn.indexOf("val value = fillImportedSnapshot(suffix, merged)")
        assertTrue(clean in 0 until merge && merge < fill, "thứ tự: làm sạch → merge → điền ($clean/$merge/$fill)")
        val fn = SourceRoots.body(snapshot, "internal fun WorkspacePrefs.fillImportedSnapshot(suffix: String, value: Any?)")
        assertTrue(fn.contains("ProfileScopeMigration.fillNewKeys(") && fn.contains("ProfileScope.CLUSTERNAV_KEYS"), "cùng phép :core")
        assertTrue(fn.contains("ProfileScopeCluster.DEFERRED"), "khoá hoãn đi đúng đường bản chờ")
    }

    // ── VC-R2/R7/R8 · chụp–áp–nhập đi qua phép thuần ──────────────────────────────────────────

    @Test
    fun `chup va ap di qua ClusterSnapshotPlan voi ho, khoa hoan va kieu khai san`() {
        val snap = SourceRoots.body(snapshot, "internal fun WorkspacePrefs.snapshotClusterNav(profile: String)")
        assertTrue(snap.contains("ClusterSnapshotPlan.snapshot(") && snap.contains("ProfileScopeCluster.DEFERRED"))
        assertTrue(snap.contains("ProfileScopeCluster.familiesOf(file)"), "họ tiền tố phải vào ảnh")
        val apply = SourceRoots.body(snapshot, "internal fun WorkspacePrefs.applyClusterNav(profile: String)")
        listOf("ClusterSnapshotPlan.apply(", "ProfileScopeCluster.DECLARED_TYPES", "ProfileScopeCluster.DEFERRED", "target.all")
            .forEach { assertTrue(apply.contains(it), "lượt áp thiếu `$it`") }
        assertFalse(Regex("""put\w*\(\s*"cast_enabled"""").containsMatchIn(apply), "lượt áp KHÔNG được ghi thẳng khoá sống")
    }

    @Test
    fun `nhap ho so lam sach anh chup truoc khi ghi`() {
        val fn = SourceRoots.body(profileIo, "internal fun WorkspacePrefs.importProfile(data: String, name: String? = null): ProfileImportReport?")
        assertTrue(fn.contains("cleanImportedSnapshot(suffix, v)"), "mọi hậu tố nhập phải qua lớp làm sạch")
        val clean = SourceRoots.body(snapshot, "internal fun cleanImportedSnapshot(suffix: String, value: Any?): Any?")
        assertTrue(clean.contains("ClusterSnapshotPlan.sanitize(") && clean.contains("ProfileScopeCluster.DECLARED_TYPES"))
    }

    /**
     * Senior review Pass 3 — ảnh chụp ĐÃ LƯU ở `kachi_workspace` có thể sai kiểu từ TRƯỚC bản này: [ĐO code b5c0e87]
     * lượt nhập tới 2.83 chép thẳng giá trị đã giải mã (`copyValue`, chưa làm sạch) ⇒ `<hồ sơ>__cn__<tệp>` có thể là
     * Boolean/Int. `sp.getString` trên nó NÉM: ở lượt di trú (trong `init`, trước màn nhà) là launcher sập MỌI lần mở và
     * dấu chạy-một-lần không bao giờ được ghi; ở lượt áp là sập đúng lúc chạm chip hồ sơ. Hai chỗ đọc phải đi qua
     * `storedSnapshot` (vắng HOẶC sai kiểu ⇒ `null` + log), và lượt di trú rót ảnh sạch đè lên ⇒ tự chữa.
     */
    @Test
    fun `doc anh chup da luu khong nem khi sai kieu`() {
        listOf(
            "migrateClusterProfileOnce" to SourceRoots.body(migrations, "internal fun WorkspacePrefs.migrateClusterProfileOnce()"),
            "applyClusterNav" to SourceRoots.body(snapshot, "internal fun WorkspacePrefs.applyClusterNav(profile: String)"),
            // Senior review bản gộp 2.84: lượt di trú THỨ HAI trong `init` cùng đọc ảnh — máy nâng thẳng từ ≤ 2.81 (lượt
            // này chưa từng chạy) với ảnh sai kiểu từ tệp nhập là HOME sập mỗi lần mở, y hệt ca Pass 3 đã vá.
            "migrateNavScheduleOnce" to SourceRoots.body(migrations, "internal fun WorkspacePrefs.migrateNavScheduleOnce()"),
        ).forEach { (name, body) ->
            assertFalse(body.contains("sp.getString("), "$name: getString NÉM ClassCastException khi ảnh đã lưu sai kiểu")
            assertTrue(body.contains("storedSnapshot(stored, keyOf("), "$name phải đọc ảnh qua storedSnapshot")
            assertTrue(body.contains("val stored = sp.all"), "$name: một lượt chụp `sp.all`, không đọc có kiểu")
        }
        val helper = SourceRoots.body(snapshot, "internal fun storedSnapshot(stored: Map<String, *>, key: String): String?")
        assertTrue(helper.contains("if (v is String) return v"), "chỉ chuỗi mới là ảnh chụp")
        assertTrue(helper.contains("Log.w("), "ảnh sai kiểu phải để lại vết, không im lặng")
        assertFalse(Regex("""\bget(String|Boolean|Int|Long|Float|StringSet)\(""").containsMatchIn(helper))
    }

    // ── VC-R4/R7 · SimpleCastRuntime đọc–ghi ───────────────────────────────────────────────────

    @Test
    fun `doc cau hinh qua bo kiem, khong con toInt tran`() {
        val read = SourceRoots.body(runtime, "override fun displayConfigFor(pkg: String, profile: CastProfile): DisplayConfig?")
        assertTrue(read.contains("CastGeometryGuard.readConfig("), "lớp 3: đọc qua bộ kiểm")
        assertFalse(read.contains(".toInt()"), "toInt() trần ném NumberFormatException trong dịch vụ đang chạy")
        assertFalse(read.contains("getString("), "getString ném ClassCastException khi giá trị sai kiểu")
        val save = SourceRoots.body(runtime, "override fun saveDisplayConfig(pkg: String, profile: CastProfile, config: DisplayConfig)")
        assertTrue(save.contains("CastGeometryGuard.sanitizeForSave(config)"), "chỉ lưu thứ lượt đọc sau không phải bỏ")
    }

    @Test
    fun `bat tat tuong minh xoa ban cho trong CUNG luot ghi`() {
        val set = SourceRoots.body(runtime, "override fun setCastEnabled(enabled: Boolean)")
        assertTrue(
            Regex("""putBoolean\("cast_enabled", enabled\)\s*\.remove\(CastEnableDeferral\.PENDING_KEY\)\s*\.apply\(\)""")
                .containsMatchIn(set),
            "ý người lái vừa bấm phải thắng bản chờ, trong CÙNG một Editor",
        )
        val commit = SourceRoots.body(runtime, "override fun commitCastEnabledPending(): CastEnableDeferral.AtStart")
        assertTrue(commit.contains("CastEnableDeferral.onProcessStart("), "quyết định chốt ở :core")
        assertTrue(commit.contains(".remove(CastEnableDeferral.PENDING_KEY).commit()"), "chốt + xoá chờ trong MỘT commit đồng bộ")
        assertEquals("cast_enabled_pending", CastEnableDeferral.PENDING_KEY)
    }

    // ── VC-R8 · kiểu KHAI SẴN phải là kiểu mã THẬT ghi (senior review Pass 2) ────────────────────

    /**
     * Khoá mà mã ghi qua một hàm trung gian (tên khoá là BIẾN ở lời `put*`) — bộ quét không nối được tên, nên mỗi khoá
     * mang kiểu + tệp + đoạn mã ghi thật làm bằng chứng. Bài dưới đòi bảng này khớp ĐÚNG tập khoá quét hụt (không mục).
     */
    private val indirect: Map<String, Pair<PrefType, List<Pair<String, String>>>> = mapOf(
        "camera_pos_left" to (PrefType.STRING to listOf("PrefsAutomation.kt" to "putString(cameraPosKey(left), v)")),
        "camera_pos_right" to (PrefType.STRING to listOf("PrefsAutomation.kt" to "putString(cameraPosKey(left), v)")),
        "camera_dewarp_amount" to (
            PrefType.INT to listOf(
                "PrefsCameraDewarp.kt" to "put(ctx, K_DEWARP_AMOUNT, v)",
                "PrefsCameraDewarp.kt" to "private fun Prefs.put(ctx: Context, key: String, v: Int) = autoPrefs(ctx).edit().putInt(key, v)",
            )
        ),
        // 2.92 · CAMERA-FULL-VIEW — thu phóng ghi qua cùng hàm `put` riêng của PrefsCameraDewarp (Int).
        "camera_zoom" to (
            PrefType.INT to listOf(
                "PrefsCameraDewarp.kt" to "put(ctx, K_ZOOM, v)",
                "PrefsCameraDewarp.kt" to "private fun Prefs.put(ctx: Context, key: String, v: Int) = autoPrefs(ctx).edit().putInt(key, v)",
            )
        ),
        "voicekey_bindings" to (
            PrefType.STRING to listOf(
                "Prefs.kt" to "VoiceKeyBindingStore.write(p, K_VK_BINDINGS",
                "modules/voicekey/VoiceKeyBindingStore.kt" to "sp.edit().putString(key, encode(bindings))",
            )
        ),
    )

    /**
     * Khai SAI kiểu thì lượt đổi hồ sơ bỏ đúng giá trị HỢP LỆ của người lái ở mọi lượt — mất cấu hình im lặng, đắt
     * ngang lỗi bảng kiểu sinh ra để chữa (`ClassCastException` trong dịch vụ). Quét mọi lời `put*(<khoá>` của `:app`
     * (khoá literal, hằng `const val` cùng tệp, hoặc mẫu `"seat_level_$…"`) và đòi tập kiểu mã ghi == kiểu khai.
     */
    @Test
    fun `kieu khai san khop luot ghi that trong ma`() {
        val declared = ProfileScopeCluster.DECLARED_TYPES
        val root = SourceRoots.moduleSourceRoots().first { it.endsWith(Paths.get("app", "src", "main", "java")) }
        val typeOf = mapOf(
            "Boolean" to PrefType.BOOLEAN, "Int" to PrefType.INT, "Long" to PrefType.LONG, "Float" to PrefType.FLOAT,
            "String" to PrefType.STRING, "StringSet" to PrefType.STRING_SET,
        )
        val put = Regex("""\bput(Boolean|Int|Long|Float|StringSet|String)\(\s*(\w+|"[^"]*")""")
        val constant = Regex("""const val (\w+)\s*=\s*"([^"$]+)"""")
        val seen = HashMap<String, MutableSet<PrefType>>()
        val files = Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        assertTrue(files.size > 100, "bộ quét phải thấy cả cây :app (đang thấy ${files.size})")
        files.forEach { p ->
            val src = SourceRoots.codeOf("src/main/java/" + root.relativize(p).joinToString("/"))
            val consts = constant.findAll(src).associate { it.groupValues[1] to it.groupValues[2] }
            put.findAll(src).forEach { m ->
                val arg = m.groupValues[2]
                val keys = when {
                    arg.startsWith("\"") && '$' in arg ->
                        arg.trim('"').split('$').first().let { pre -> declared.keys.filter { pre.isNotEmpty() && it.startsWith(pre) } }
                    arg.startsWith("\"") -> listOf(arg.trim('"'))
                    else -> listOfNotNull(consts[arg])
                }
                keys.filter { it in declared }.forEach { seen.getOrPut(it) { mutableSetOf() } += typeOf.getValue(m.groupValues[1]) }
            }
        }
        assertEquals(
            emptyMap<String, Set<PrefType>>(), seen.filter { (k, types) -> types != setOf(declared.getValue(k)) },
            "kiểu khai ≠ kiểu mã ghi ⇒ đổi hồ sơ bỏ giá trị hợp lệ",
        )
        assertEquals(indirect.keys, declared.keys - seen.keys, "khoá quét hụt phải nằm ở `indirect` (và chỉ chúng)")
        indirect.forEach { (key, ev) ->
            val (type, evidence) = ev
            assertEquals(type, declared.getValue(key), key)
            evidence.forEach { (file, snippet) ->
                assertTrue(code(file).contains(snippet), "$key: không còn thấy lượt ghi `$snippet` ở $file")
            }
        }
    }
}
