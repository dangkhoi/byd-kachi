package com.byd.clusternav.system

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.89 · B4 DISPLAY-OWNER-DYNAMIC (spec `docs/specs/kachi-289-field-fixes.html` §B4) — DÂY NỐI phía `:app` (CLAUDE.md §8: hàm
 * mới phải có call site; `SimpleCastRuntime`/`WindowCommandDispatcher.get` cần Android ⇒ canh MÃ đã bỏ chú thích). Hành vi
 * thuần chạy thật ở `:core` (`DisplayOwnershipRegistryTest`, `CastDisplayOwnershipWiringTest`) và ở
 * `WindowCommandDispatcherTest` (hồi quy dựng từ dòng log xe 15/09 · máy ảo 05/10).
 *
 * Khoá: (1) runtime nối `onCastDisplay` của coordinator vào `WindowCommandDispatcher.setCastDisplay` của tiến trình — quên là
 * id cụm không bao giờ vào cổng sở hữu (display cụm lạ ⇒ vẫn từ chối, nhưng `isCastable` mù); (2) coordinator báo ở MỌI lượt
 * dò + sau mỗi lượt đóng chiếu; (3) không còn hằng `CAST_DISPLAY` nào trong mã chạy; (4) màn chẩn đoán in cổng sở hữu
 * (CLAUDE.md §11 — anh em chụp màn hình là đủ).
 */
class DisplayOwnerDynamicWiringContractTest {

    private val runtime by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt") }
    private val coordinator by lazy { SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/SimpleCastCoordinator.kt") }
    private val ops by lazy { SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/SimpleCastCoordinatorOps.kt") }
    private val dispatcher by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/system/WindowCommandDispatcher.kt") }
    private val diag by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/ClusterDiag.kt") }

    private fun order(src: String, vararg parts: String) {
        var at = -1
        parts.forEach { p ->
            val i = src.indexOf(p, at + 1)
            assertTrue(i > at, "thứ tự sai / thiếu '$p' trong: ${src.take(1500)}")
            at = i
        }
    }

    @Test
    fun `runtime wires the coordinator cast-display sink to the process dispatcher`() {
        val create = SourceRoots.body(runtime, "private fun create(app: Context): SimpleCastCoordinator {")
        order(create, "val coordinator = SimpleCastCoordinator(", "onCastDisplay = { id ->",
            "WindowCommandDispatcher.get(app).setCastDisplay(id)", "return coordinator")
        assertEquals(1, Regex(Regex.escape(".setCastDisplay(")).findAll(runtime).count(), "đúng một call site production")
    }

    @Test
    fun `coordinator publishes on every detection and after every successful close`() {
        val detect = SourceRoots.body(coordinator, "internal fun detectClusterDisplay(awaitAfterOpen: Boolean = false, exclude: Int = -1): Int {")
        order(detect, "liveDisplayId = resolved", "publishCastDisplay(resolved)", "return resolved")
        val close = SourceRoots.body(coordinator, "fun closeProjection() {")
        order(close, "projection.close(displayId, themeGuard)", "if (ok) publishCastDisplay(-1)")
        val closeSync = SourceRoots.body(coordinator, "private fun closeProjectionSync() {")
        order(closeSync, "projection.close(displayId, themeGuard)", "if (ok) publishCastDisplay(-1)")
        val orphan = SourceRoots.body(ops, "internal fun SimpleCastCoordinator.closeOrphanProjectionBody() {")
        order(orphan, "projection.close(vd, themeGuard)", "publishCastDisplay(-1)")
        val publish = SourceRoots.body(ops, "internal fun SimpleCastCoordinator.publishCastDisplay(id: Int) {")
        assertTrue(publish.contains("onCastDisplay(id.takeIf { it >= 1 })"), "id < 1 ⇒ null, không bao giờ seed: $publish")
        assertTrue(publish.contains("catch (e: RuntimeException)"), "bên nhận hỏng không được gãy đường chiếu: $publish")
    }

    @Test
    fun `dispatcher exposes setCastDisplay, feeds isCastable from ownership, logs collisions`() {
        assertTrue(dispatcher.contains("fun setCastDisplay(id: Int?) = ownership.setCastDisplay(id)"), dispatcher.take(800))
        assertTrue(dispatcher.contains("AppLocationRegistry(ownership::isCastDisplay)"), "isCastable phải đọc id cụm dò live")
        val owned = SourceRoots.body(dispatcher, "internal fun createOwned(transport: ShellTransport): WindowCommandDispatcher =")
        assertTrue(owned.contains("ownership = DisplayOwnershipRegistry(log ="), "ca trùng id phải log ra logcat: $owned")
    }

    @Test
    fun `no CAST_DISPLAY constant survives in production sources`() {
        val offenders = SourceRoots.moduleSourceRoots().filter { Files.exists(it) }.flatMap { root ->
            Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        }.filter { "CAST_DISPLAY" in SourceRoots.codeOf(it.toString()) }
        assertTrue(offenders.isEmpty(), "hằng 'cụm = 1' đã bỏ (B4) — còn ở: $offenders")
    }

    @Test
    fun `cluster diag prints the ownership gate (launcher VDs and detected cast id)`() {
        order(diag, "WindowCommandDispatcher.get(app).ownership", "registeredVirtualDisplays()", "castDisplay()")
    }
}
