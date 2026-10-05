package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.launcher.ProfileScopeCluster
import com.byd.clusternav.modules.clustercast.simplified.ThemeLedger
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CLUSTER-THEME-SAFE B1a (spec `docs/specs/kachi-289-field-fixes.html` §B1a) — DÂY NỐI phía `:app` (CLAUDE.md §8: hàm mới phải
 * có call site; `:app` không có Robolectric ⇒ canh MÃ đã bỏ chú thích). Phần quyết thuần chạy thật ở `:core`
 * (`ClusterStylePlanTest`, `ThemeLedgerTest`, `ThemeGatePreviewTest`, `ProjectionManagerThemeGateTest`).
 *
 * Khoá: (1) runtime tiêm sổ theme BỀN + đồng hồ thật + dò `car.type` qua dadb — quên một cái là sổ chỉ ở RAM (chết theo tiến
 * trình, CLAUDE.md §5) hoặc RECT không bao giờ hiện trên xe không đọc được prop; (2) "Cổng theme (chỉ đọc)" đi qua kênh CHỈ
 * ĐỌC và không dựng coordinator; (3) khoá prefs mới khai phạm vi XE; (4) op 39 không còn gõ cứng tên service.
 */
class ClusterThemeB1aWiringContractTest {

    private val runtime by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt") }
    private val diag by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/ClusterDiag.kt") }
    private val diagUi by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/DiagActivity.kt") }
    private val lane by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/ClusterNavLaneWidget.kt") }
    private val ops by lazy { SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/SimpleCastCoordinatorOps.kt") }

    private fun order(src: String, vararg parts: String) {
        var at = -1
        parts.forEach { p ->
            val i = src.indexOf(p, at + 1)
            assertTrue(i > at, "thứ tự sai / thiếu '$p' trong: ${src.take(1500)}")
            at = i
        }
    }

    @Test
    fun `runtime tiem so ben, dong ho that, do car type qua dadb vao coordinator`() {
        val create = SourceRoots.body(runtime, "private fun create(app: Context): SimpleCastCoordinator {")
        order(create, "val coordinator = SimpleCastCoordinator(", "themeLedger = themeLedgerStore(app)", "themeClock = themeClock(app)",
            "recipeProbe = { sh ->", "ClusterProfile.refineByShell(app)", "sh.execute(cmd)", ".projectionRecipe()", "return coordinator")
        assertEquals(1, Regex(Regex.escape("ClusterProfile.refineByShell(")).findAll(runtime).count(), "một call site production")
    }

    @Test
    fun `so theme - tep clustercast (pham vi XE), khoa ThemeLedger KEY, commit dong bo`() {
        val store = SourceRoots.body(runtime, "fun themeLedgerStore(context: Context): ThemeLedger.Store = object : ThemeLedger.Store {")
        assertTrue(store.contains("ClusterProfile.PREF"), store)
        assertTrue(store.contains("putString(ThemeLedger.KEY, value).commit()"), "ghi TRƯỚC khi gửi phải chạm đĩa: $store")
        assertFalse(store.contains(".apply()"), "apply() bất đồng bộ — tiến trình chết là mất 'pending'")
        val clock = SourceRoots.body(runtime, "fun themeClock(context: Context): ThemeLedger.Clock {")
        listOf("SystemClock.elapsedRealtime()", "Settings.Global.BOOT_COUNT", "Process.getStartElapsedRealtime()")
            .forEach { assertTrue(clock.contains(it), "đồng hồ thiếu `$it`") }
    }

    @Test
    fun `khoa prefs moi khai pham vi XE`() {
        assertTrue(ThemeLedger.KEY in ProfileScopeCluster.DEVICE_KEYS)
        assertTrue(ClusterProfile.KEY_CAR_TYPE in ProfileScopeCluster.DEVICE_KEYS)
        assertEquals("clustercast", ClusterProfile.PREF)
    }

    @Test
    fun `Cong theme chi doc - kenh ReadOnlyShell, khong dung coordinator, co call site o capture va man Chan doan`() {
        val fn = SourceRoots.body(diag, "private fun themeGateWith(app: Context, run: (String) -> LocalShellText): String {")
        order(fn, "ThemeGatePreview.ReadOnlyShell(raw)", "ro.execute(ClusterCarType.CMD)", "ThemeGatePreview.report(", "ro,")
        assertFalse(fn.contains("SimpleCastRuntime.coordinator(") || fn.contains(".open(") || fn.contains("raw.execute"),
            "chỉ đọc: không dựng coordinator, không mở chiếu, không chạy lệnh qua kênh thô")
        assertTrue(SourceRoots.body(diag, "fun capture(ctx: Context, pkg: String, vd: Int, stamp: String): Pair<String, String> {")
            .contains("themeGateWith(app, shell)"), "capture phải có mục Cổng theme")
        assertTrue(diagUi.contains("setOnClickListener { runThemeGate() }"))
        order(SourceRoots.body(diagUi, "private fun runThemeGate() {"), "Thread({", "ClusterDiag.themeGate(applicationContext)", "runOnUiThread")
    }

    @Test
    fun `luot mo - do cong thuc truoc, kieu nguoi lai doc MOT lan, roi moi open`() {
        val body = SourceRoots.body(ops, "internal fun SimpleCastCoordinator.openProjectionBody() {")
        order(body, "projection.resetState(false)", "probeRecipeOnce()", "themeGuard.beginOpen()",
            "projection.open(preOpenId, themeGuard, desiredStyleOnce())")
    }

    /**
     * Review 2.89 Pass 2 · whole-r1-4 (ĐỔI GHIM có lý do): op 39 đi ĐÚNG lệnh cũ trên MỌI đời xe (hằng `SVC_DILINK3`, không
     * chuỗi `AutoContainer`) — bản B1a dựng từ hồ sơ làm DL5 gửi op 39 tới `auto_container` lần đầu, chưa đo (CLAUDE.md §14).
     * Thử ĐỎ: trả về `ClusterProfile.resolveCached(appCtx).svcCall(OP_SIMPLE_NAV)`.
     */
    @Test
    fun `op 39 giu dung lenh cu tren moi doi xe, khong go cung ten service`() {
        assertTrue(lane.contains("ProjectionRecipe.svcCall(ProjectionRecipe.SVC_DILINK3, OP_SIMPLE_NAV)"))
        assertFalse(lane.contains(".svcCall(OP_SIMPLE_NAV)"), "op 39 không theo service của hồ sơ cho tới khi đo trên DL5")
        assertTrue(lane.contains("executeShell(cmd())"))
        assertFalse(lane.contains("AutoContainer"))
        assertEquals("service call AutoContainer 2 i32 1000 i32 39 s16 \"\"",
            com.byd.clusternav.modules.clustercast.simplified.ProjectionRecipe.svcCall(
                com.byd.clusternav.modules.clustercast.simplified.ProjectionRecipe.SVC_DILINK3, ClusterNavLaneWidget.OP_SIMPLE_NAV))
    }

    /**
     * Review 2.89 Pass 2 · cluster-r1-6 / whole-r1-3 — "Cổng theme (chỉ đọc)" quyết với ĐÚNG kiểu người lái chọn (`cast_style` của
     * hồ sơ), không gõ cứng Bo tròn. Thử ĐỎ: truyền lại `CastStyle.CURVED` vào `ThemeGatePreview.report`.
     */
    @Test
    fun `Cong theme chi doc - kieu muon doc cast_style cua ho so, khong go cung Bo tron`() {
        val fn = SourceRoots.body(diag, "private fun themeGateWith(app: Context, run: (String) -> LocalShellText): String {")
        assertFalse(diag.contains("CastStyle.CURVED"), "ClusterDiag không được gõ cứng Bo tròn")
        order(fn, "val desired = desiredStyle(app)", "ThemeGatePreview.report(", "desired, header,")
        val read = SourceRoots.body(diag, "private fun desiredStyle(app: Context): CastStyle")
        assertTrue(read.contains("ProfileScopeCluster.SIMPLE_CAST_FILE") && read.contains("CAST_STYLE_KEY"), read)
        assertTrue(diag.contains("private const val CAST_STYLE_KEY = \"cast_style\""))
        assertTrue(runtime.contains("sp.all[\"cast_style\"] as? String"), "cùng khoá với SharedPrefsSimpleCastPrefs.castStyle()")
        assertEquals("simple_cast_prefs", ProfileScopeCluster.SIMPLE_CAST_FILE)
        assertTrue(runtime.contains("getSharedPreferences(\"simple_cast_prefs\""))
    }
}
