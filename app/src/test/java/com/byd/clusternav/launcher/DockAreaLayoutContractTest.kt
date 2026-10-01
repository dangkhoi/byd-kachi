package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ PROFILE-SWITCH-SLOTS · R-A1 — HỢP ĐỒNG NỐI DÂY: đổi cạnh/ẩn-hiện thanh nút KHÔNG tháo vùng ô ═══════════════════
 *
 * Lỗi A [ĐO máy ảo 2026-10-01]: `DockAreaLayout.apply` (gọi ở `KachiHomeRender.kt` khi hồ sơ mới khác cạnh thanh nút)
 * `removeView(workspace)` + `removeAllViews()` ⇒ `WorkspaceView.onDetachedFromWindow` → `VdAppHost.release()` ⇒ ô đen
 * mãi. Luật thuần + 64 cặp chuyển ở `:core` (`DockAreaPlanTest`); bài này khoá rằng `:app` THẬT SỰ đi qua luật đó và
 * ánh xạ từng bước đúng nghĩa mà bộ chứa giả bên `:core` giả định.
 *
 * Quét **mã đã bỏ chú thích** ([SourceRoots.codeOf]) + cắt thân bằng [SourceRoots.body] (đếm ngoặc, nổ khi mốc vắng).
 */
class DockAreaLayoutContractTest {

    private val layout by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/DockAreaLayout.kt") }
    private val apply by lazy { SourceRoots.body(layout, "fun apply(") }

    @Test
    fun `apply khong thao vung o — khong removeAllViews, khong removeView(workspace)`() {
        listOf(
            "removeAllViews(", "removeAllViewsInLayout(", "removeView(workspace)", "removeViewInLayout(",
            "removeViews(", "removeViewsInLayout(", "detachViewFromParent(", "detachAllViewsFromParent(",
        ).forEach { banned ->
            assertFalse(banned in apply, "thân apply có `$banned` ⇒ vùng ô rời cửa sổ ⇒ VdAppHost.release ⇒ ô đen (lỗi A)")
        }
        // Lối tháo DUY NHẤT trên `mainArea` là bước Remove của kế hoạch — chỉ số do DockAreaPlan quyết.
        assertEquals(1, Regex("""mainArea\.remove""").findAll(apply).count(),
            "chỉ được một lối tháo con của mainArea (bước Remove của DockAreaPlan): $apply")
    }

    @Test
    fun `apply di qua DockAreaPlan va anh xa tung buoc dung nghia bo chua gia o core`() {
        assertTrue("DockAreaPlan.target(cfg)" in apply, "đích phải lấy từ luật thuần, không tự viết lại `when (edge)` thứ tự")
        assertTrue("DockAreaPlan.steps(current, target)" in apply, "các bước phải lấy từ luật thuần đã test 64 cặp")
        assertTrue(Regex("""mainArea\.getChildAt\(it\)\s*===\s*workspace\)\s*DockAreaChild\.WORKSPACE""").containsMatchIn(apply),
            "nhận diện vùng ô phải bằng danh tính (===) — nhận nhầm là kế hoạch tháo nó")
        assertTrue("is DockAreaStep.Remove -> mainArea.removeViewAt(step.index)" in apply, "Remove(i) = removeViewAt(i)")
        assertTrue("DockAreaStep.AddWorkspace -> mainArea.addView(workspace, wsLp)" in apply, "AddWorkspace = gắn vào CUỐI")
        assertTrue("if (step.atStart) 0 else mainArea.childCount" in apply, "AddDock(atStart) = chỉ số 0 hoặc cuối")
        assertTrue("workspace.layoutParams = wsLp" in apply,
            "vùng ô đã ở sẵn ⇒ chỉ đổi tham số bố trí (setLayoutParams không tháo — AOSP View.java:17268-17278)")
    }

    /** Đóng cửa lách: không tệp nào khác của màn nhà được tháo vùng ô hay dọn sạch `mainArea`. */
    @Test
    fun `khong tep nao cua man nha thao vung o hay don sach mainArea`() {
        val offenders = launcherSources().filter { f ->
            val src = code(f)
            "removeView(workspace)" in src || "mainArea.removeAllViews" in src || "mainArea.removeView(" in src
        }.map { it.fileName.toString() }
        assertEquals(emptyList<String>(), offenders, "tháo vùng ô ⇒ mọi ô app nhả màn ảo (lỗi A)")
    }

    /** §8 — hàm có call site thật: lần dựng đầu (onCreate) + lượt render khi viền/ẩn-hiện đổi. */
    @Test
    fun `apply duoc goi o onCreate va o render SAU workspace render`() {
        val activity = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt")
        assertTrue("DockAreaLayout.apply(mainArea, workspace, dock," in activity, "lần dựng đầu phải đi qua cùng một hàm")
        val render = SourceRoots.body(
            SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeRender.kt"),
            "internal fun KachiHomeActivity.render(",
        )
        val ws = render.indexOf("workspace.render(")
        val dockAt = render.indexOf("if (layoutChanged) DockAreaLayout.apply(mainArea, workspace, dock, state.dock,")
        assertTrue(ws >= 0 && dockAt > ws,
            "render phải dựng ô TRƯỚC rồi mới đặt lại thanh nút — đúng thứ tự đã đo của lỗi A (ô vừa dựng phải sống sót)")
    }

    private fun launcherSources(): List<Path> =
        Files.walk(SourceRoots.path("src/main/java/com/byd/clusternav/launcher")).use { s ->
            s.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.sorted().toList()
        }.also { assertTrue(it.size > 10, "quét được quá ít tệp — đường dẫn sai thì bài này là test giả") }

    /** MÃ đã bỏ chú thích (KDoc nhắc chính chuỗi bị cấm). */
    private fun code(f: Path): String = f.toFile().readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { line -> Regex("//.*$").replace(line, "") }
}
