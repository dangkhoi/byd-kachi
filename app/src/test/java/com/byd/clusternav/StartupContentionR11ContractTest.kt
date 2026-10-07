package com.byd.clusternav

import com.byd.clusternav.launcher.voice.WakeKeywordsSync
import com.byd.clusternav.launcher.voice.WakeModelCatalog
import com.byd.clusternav.modules.clustercast.BubblePipGuard
import com.byd.clusternav.testsupport.SourceRoots
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.96 R11 — khởi động sau nổ máy: dây nối `:app` của ba bản vá (luật thuần ở `:core`) ═══════════════════════════════════
 *
 * Số đo + dòng thời gian: `docs/diagnostics/startup-timeline-2026-10-07.md`. Mỗi bài khoá một lỗi [ĐO log xe 07/10]:
 *  1. TAT-MAY-CAST-HOLD — tiến trình bật lúc màn tắt chiếu trọn rồi mới bị lượt chữa phím giết ⇒ tiến trình con gỡ + chiếu lại.
 *  2. WAKE-RECOPY-LOOP — mỗi lần nổ máy xoá + chép 5 MB + nạp lại bộ nghe "Hey Kachi".
 *  3. PIP-QUERY-INSTALLED — 5 `appops get` trên kênh shell chung lúc lượt tự chiếu mở, gồm gói chưa cài (`exit=-1`).
 */
class StartupContentionR11ContractTest {

    private val heal = SourceRoots.codeOf("src/main/java/com/byd/clusternav/A11yLifecycleHeal.kt")
    private val autostart = SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/BubbleAutostart.kt")
    private val hold = SourceRoots.codeOf("src/main/java/com/byd/clusternav/TatMayCastHold.kt")
    private val wake = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeWake.kt")
    private val pip = SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/BubblePipGuard.kt")
    private val service = SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/FloatingBubbleService.kt")

    private fun order(src: String, vararg tokens: String) {
        val at = tokens.map { t -> src.indexOf(t).also { assertTrue(it >= 0, "thiếu '$t'") } }
        assertEquals(at.sorted(), at, "thứ tự sai: ${tokens.joinToString(" → ")}")
    }

    // ─── 1 · TAT-MAY-CAST-HOLD ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `lop 1 giu tu chieu TRUOC khi xep luot va nha trong finally`() {
        val install = SourceRoots.body(heal, "fun install(ctx: Context) {")
        order(
            install,
            "val interactive = interactive(app)",
            "TatMayCastHold.arm(interactive, startedAt)",
            "try { onProcessStart(app, interactive, startedAt) } finally { TatMayCastHold.release() }",
        )
    }

    @Test
    fun `tu chieu hoi giu TRUOC openProjection, hen lai bang chinh Runnable, khong tinh luot thu`() {
        val run = SourceRoots.body(autostart, "override fun run() {")
        order(run, "if (isDestroyed() || dispatched.get()) return", "if (holdForTatMayHeal()) return", "coordinator.openProjection()")
        val fn = SourceRoots.body(autostart, "private fun holdForTatMayHeal(): Boolean {")
        assertTrue("handler.postDelayed(open, hold)" in fn)
        assertFalse("openAttempts" in fn, "lượt giữ không được ăn vào 5 lượt thử mở")
        assertTrue("KachiReadyLog.line(" in fn, "buổi xe phải đo được lượt giữ (dòng cast-hold vào usage log)")
    }

    @Test
    fun `tien trinh sap chet doc moc leo BEN, khong co RAM`() {
        assertTrue("Prefs.a11yEscalatedAt(app)" in SourceRoots.body(hold, "fun holdMs(app: Context): Long ="))
    }

    @Test
    fun `A11yLifecycleHeal van duoi tran 500 dong`() {
        val n = SourceRoots.text("src/main/java/com/byd/clusternav/A11yLifecycleHeal.kt").lines().size
        assertTrue(n <= 500, "A11yLifecycleHeal.kt dài $n dòng")
    }

    // ─── 2 · WAKE-RECOPY-LOOP ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `ensure so dia voi ban APK, khong voi ban ghim`() {
        val ensure = SourceRoots.body(wake, "fun ensure(app: Context) {")
        assertTrue("if (ready && !keywordsNeedRecopy(app)) return" in ensure)
        assertFalse("keywordsMatchPin(app)) return" in ensure, "so ghim là gốc vòng chép-lại mỗi lần nổ máy")
        val fn = SourceRoots.body(wake, "private fun keywordsNeedRecopy(app: Context): Boolean {")
        assertTrue("app.assets.open(\"\${WakeModelCatalog.ASSET_DIR}/\${WakeModelCatalog.KEYWORDS}\")" in fn)
        assertTrue("WakeKeywordsSync.needsRecopy(disk, shipped) { keywordsMatchPin(app) }" in fn)
    }

    /** Lần bật thứ HAI sau khi chép (đĩa = bản APK thật) KHÔNG được chép lại — lỗi 07/10 là luôn chép lại. */
    @Test
    fun `ban APK that - sau mot lan chep thi lan bat ke khong chep lai`() {
        val asset = File(SourceRoots.path("src/main/assets/${WakeModelCatalog.ASSET_DIR}/${WakeModelCatalog.KEYWORDS}").toString())
        assertTrue(asset.isFile, "thiếu ${asset.path}")
        val bytes = asset.readBytes()
        assertFalse(WakeKeywordsSync.needsRecopy(bytes.copyOf(), bytes) { false })
    }

    // ─── 3 · PIP-QUERY-INSTALLED ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `chi hoi PiP cua goi da cai, giu thu tu, hoi hong thi coi nhu co cai`() {
        val installed = setOf("com.google.android.apps.maps", "com.google.android.youtube")
        assertEquals(listOf("com.google.android.apps.maps", "com.google.android.youtube"), BubblePipGuard.targets { it in installed })
        assertEquals(5, BubblePipGuard.targets { error("PackageManager hỏng") }.size, "fail-open về hành vi cũ")
        assertEquals(5, BubblePipGuard.targets { true }.size)
    }

    @Test
    fun `block di qua targets, dich vu tiem PackageManager, restore khong loc`() {
        assertTrue("targets(installed).forEach" in SourceRoots.body(pip, "fun block(coordinator: SimpleCastCoordinator) {"))
        assertFalse("targets(" in SourceRoots.body(pip, "fun restore(coordinator: SimpleCastCoordinator) {"))
        assertTrue("BubblePipGuard { pkg -> PackageQueries.packageInfo(packageManager, pkg) != null }" in service)
        assertTrue("pipGuard.block(coordinator)" in service && "pipGuard.restore(coordinator)" in service)
    }
}
