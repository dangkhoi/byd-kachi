package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * ═══ FIX286 · R-PI — DÂY NỐI phía `:app` của lượt nhập hồ sơ giữ cấu hình chiếu cụm (spec `kachi-286-field-fixes.html`) ═
 *
 * Phép thuần (merge · tóm tắt · log · quyết định "Dùng ngay" · mốc chốt) có bài chạy thật ở `:core`
 * (`ClusterImportMergeTest`). Bài ở đây hỏi điều `:core` không hỏi được: mã Android có THẬT SỰ đi qua chúng, và merge có
 * đúng là CHỈ ở lượt nhập không (nới refute C4 có chủ ý — lan sang lượt đổi hồ sơ là khung của A tràn sang B vĩnh viễn).
 *
 * Quét source đã bỏ chú thích ([SourceRoots.codeOf]), cắt vùng bằng [SourceRoots.body] (nổ nếu mốc vắng).
 */
class ProfileImportFix286WiringContractTest {

    private fun launcher(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")

    private val profileIo by lazy { launcher("WorkspacePrefsProfile.kt") }
    private val snapshot by lazy { launcher("WorkspacePrefsSnapshot.kt") }
    private val section by lazy { launcher("SettingsSectionsProfiles.kt") }
    private val cast by lazy { launcher("SettingsSectionsCast.kt") }
    private val bridgeCast by lazy { launcher("ClusterNavBridgeCast.kt") }
    private val repo by lazy { launcher("PrefsWorkspaceRepository.kt") }
    private val runtime by lazy {
        SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt")
    }

    private val importSig = "internal fun WorkspacePrefs.importProfile(data: String, name: String? = null): ProfileImportReport?"
    private val mergeSig = "internal fun WorkspacePrefs.mergeImportedCast(suffix: String, clean: Any?): Pair<String, ClusterImportSummary>?"

    // ── PI1/PI2 — merge ở lượt NHẬP, và CHỈ ở đó ───────────────────────────────────────────────────────

    @Test
    fun `nhap lam sach TRUOC roi moi merge, ghi ket qua merge vao ho so moi`() {
        val imp = SourceRoots.body(profileIo, importSig)
        val clean = imp.indexOf("val clean = cleanImportedSnapshot(suffix, v)")
        val merge = imp.indexOf("mergeImportedCast(suffix, clean)")
        assertTrue(clean >= 0 && merge > clean, "giá trị của tệp phải qua lớp làm sạch TRƯỚC merge (VC-R8)")
        assertTrue(imp.contains("copyValue(e, keyOf(plan.target, suffix), value)"), "chỉ ghi khoá của hồ sơ MỚI")
        assertTrue(imp.contains("ClusterImportSummary.NONE") && imp.contains(".also(::logImported)"), "log PI5 lúc nhập")
        val m = SourceRoots.body(snapshot, mergeSig)
        assertTrue(m.contains("if (suffix != ProfileScope.snapshotSuffix(file)) return null"), "merge chỉ cho ảnh simple_cast_prefs")
        assertTrue(m.contains("ClusterSnapshotPlan.mergeImport(") && m.contains("clusterNavPrefs(file).all"))
        assertTrue(m.contains("ProfileScopeCluster.DEFERRED") && m.contains("ProfileScopeCluster.familiesOf(file)"))
    }

    /**
     * Cơ chế phân biệt "nhập" với "đổi hồ sơ thường": merge có ĐÚNG MỘT chỗ gọi (lượt nhập), và lượt áp/chụp/nhân bản/di trú
     * không bao giờ gọi nó. Thêm một chỗ gọi mới ⇒ đỏ ⇒ phải soát lại xem có làm refute C4 mất hẳn không.
     */
    @Test
    fun `merge co dung mot cho goi la luot nhap, luot ap ho so khong cham`() {
        val all = SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        }.joinToString("\n") { KotlinSource.stripComments(it.toFile().readText()) }
        assertEquals(2, Regex("""\bmergeImportedCast\(""").findAll(all).count(), "định nghĩa + ĐÚNG một chỗ gọi")
        assertEquals(2, Regex("""ClusterSnapshotPlan\.mergeImport\(|fun mergeImport\(""").findAll(all).count(),
            "phép thuần: định nghĩa + một chỗ gọi (mergeImportedCast)")
        val apply = SourceRoots.body(snapshot, "internal fun WorkspacePrefs.applyClusterNav(profile: String)")
        assertFalse(apply.contains("mergeImport"), "lượt ĐỔI HỒ SƠ không được merge — refute C4 phải còn nguyên")
        assertTrue(apply.contains("ClusterSnapshotPlan.apply("))
    }

    // ── PI5 — log đổi hồ sơ ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `luot ap ho so log cast va khung ghi xoa TRUOC loi thoat khong co gi de ghi`() {
        val apply = SourceRoots.body(snapshot, "internal fun WorkspacePrefs.applyClusterNav(profile: String)")
        val log = apply.indexOf("ClusterSnapshotPlan.describe(plan, families, ProfileScopeCluster.DEFERRED)")
        val exit = apply.indexOf("if (plan.writes.isEmpty()) return@forEach")
        assertTrue(log in 0 until exit, "ClearPending lúc không có bản chờ không ghi gì — dòng log vẫn phải có")
    }

    // ── PI3 — hộp thoại + "Dùng hồ sơ này ngay" ──────────────────────────────────────────────────────────

    @Test
    fun `nhap xong mo hop thoai noi that, Dung ngay doi ho so roi moi ap chieu cum khi ranh`() {
        val one = SourceRoots.body(section, "private fun importFile(entry: ProfileFiles.Entry)")
        assertTrue(one.contains("SettingsDialogs.offer("), "hộp thoại hai lựa chọn — không còn toast im lặng về phần cụm")
        assertTrue(one.contains("importMessage(created.cluster)") && one.contains("{ useImported(created.name) }"))
        assertTrue(one.contains("R.string.kachi_profile_import_use_now") && one.contains("R.string.kachi_profile_import_later"))
        assertTrue(one.contains("R.string.kachi_profile_import_fail, entry.fileName"), "tệp hỏng vẫn nói tên tệp")
        val msg = SourceRoots.body(section, "private fun importMessage(c: ClusterImportSummary): String")
        listOf(
            "c.hasClusterPart", "c.castFromFile", "c.fileRecords, c.replacingRecords", "c.keptRecords",
            "c.castOn != deps.bridge.castEnabled()",
        ).forEach { assertTrue(msg.contains(it), "hộp thoại thiếu `$it`") }
        // [Senior review FIX286 Pass 1 · P3 — E2E F4] tệp không thay khung nào ⇒ không in "thay 0 khung khác".
        assertTrue(msg.contains("if (c.replacingRecords > 0)") && msg.contains("R.string.kachi_profile_import_geom_file_new, c.fileRecords"),
            "0 khung bị thay ⇒ câu riêng, không in số 0")
        assertFalse(Regex("""c\.(fileApps|replacingApps|keptApps)""").containsMatchIn(msg),
            "hộp thoại đếm theo KHUNG — đếm theo app nói sai khi tệp có app nhưng thiếu biến thể nửa cụm [ĐO máy ảo 02/10]")
        val use = SourceRoots.body(section, "private fun useImported(name: String)")
        val sw = use.indexOf("deps.onSwitchProfile(name)")
        val ap = use.indexOf("deps.bridge.applyCastPendingIfIdle()")
        assertTrue(sw >= 0 && ap > sw, "đổi hồ sơ (ghi bản chờ) TRƯỚC, rồi mới áp bản chờ")
        assertTrue(use.contains("R.string.kachi_profile_import_cast_deferred"), "đang chiếu ⇒ nói ra là để lần nổ máy sau")
        assertFalse(Regex("""setCastEnabled|openProjection|closeProjection""").containsMatchIn(section),
            "màn hồ sơ không tự gọi đường chiếu — chỉ qua applyCastPendingIfIdle (cùng đường Áp ngay)")
    }

    @Test
    fun `ap ban cho khi ranh di qua quyet dinh thuan va dung duong Ap ngay`() {
        val f = SourceRoots.body(bridgeCast, "fun ClusterNavBridge.applyCastPendingIfIdle(): Boolean")
        assertTrue(f.contains("CastEnableDeferral.applyOnUse(castEnabledPending(), castState()) ?: return false"))
        assertTrue(f.contains("setCastEnabled(on)"), "cùng đường thật của công tắc / Áp ngay")
        assertTrue(repo.contains("prefs.importProfile(data)?.let { report -> ProfileImported(load(), report) }"))
    }

    // ── PI5 — mốc bền lượt chốt lúc khởi động ────────────────────────────────────────────────────────────

    @Test
    fun `moc chot ghi CUNG luot commit voi khoa song va man chieu cum doc`() {
        val commit = SourceRoots.body(runtime, "override fun commitCastEnabledPending(): CastEnableDeferral.AtStart")
        val branch = commit.indexOf("is CastEnableDeferral.AtStart.Commit ->")
        val mark = commit.indexOf("CastEnableDeferral.COMMIT_MARK_KEY", branch)
        val done = commit.indexOf(".commit()", branch)
        val discard = commit.indexOf("is CastEnableDeferral.AtStart.Discard")
        assertTrue(branch >= 0 && mark in branch until done, "mốc phải nằm trong CÙNG chuỗi Editor của nhánh Commit")
        assertTrue(done in branch until discard, "nhánh Commit phải tự kết bằng commit() ĐỒNG BỘ — không mượn commit() của nhánh Discard")
        assertTrue(commit.substring(branch, done).contains("putBoolean(\"cast_enabled\", decision.on)"))
        assertTrue(runtime.contains("CastEnableDeferral.CommitMark.decode(sp.all[CastEnableDeferral.COMMIT_MARK_KEY])"))
        val master = SourceRoots.body(cast, "private fun rebuildMaster()")
        assertTrue(master.contains("bridge.castCommitMark()?.let") && master.contains("R.string.kachi_cast_commit_mark"))
    }

    // ── Chuỗi vi/en ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `chuoi moi co du hai ngon ngu`() {
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        listOf(
            "kachi_profile_import_no_cluster", "kachi_profile_import_cast_file", "kachi_profile_import_cast_kept",
            "kachi_profile_import_geom_file", "kachi_profile_import_geom_file_new",
            "kachi_profile_import_geom_kept", "kachi_profile_import_geom_none",
            "kachi_profile_import_use_cast", "kachi_profile_import_use_now", "kachi_profile_import_later",
            "kachi_profile_import_cast_deferred", "kachi_cast_commit_mark",
        ).forEach { key ->
            assertTrue(vi.contains("name=\"$key\""), "VI thiếu $key")
            assertTrue(en.contains("name=\"$key\""), "EN thiếu $key")
        }
    }
}
