package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 2.90 · R9 — cổng theme + coordinator với "dọn cụm / trả cụm" (spec `kachi-290-cluster-rect-fix.html` §4.4).
 *
 * Kịch bản dựng từ buổi xe 06/10 (`docs/diagnostics/oncar-2026-10-06-cluster-rect.md`): màn ảo cụm display 4 có từ lúc khởi động
 * (F1), bóng nổi mod VietMap giữ 3 cửa sổ phủ trên đó (F5), theme gửi khi trống ⇒ màn ảo dựng lại thành 9 (F4). Thêm badge tốc độ
 * của Kachi (cửa sổ phủ, tên = gói trần). Luật gửi KHÔNG đổi: chỉ gửi khi 0 task + 0 cửa sổ.
 */
class ClusterThemeLayerPauseTest {

    private val self = "com.byd.clusternav"   // selfPackage mặc định của coordinator trong JVM
    private val vm = "vn.vietmap.live"
    private val sealB = ProjectionRecipe.SEAL_DL3.copy(nativeStyle = CastStyle.RECT, themeOnVacantVd = true)

    /** Bộ thi hành giả: ghi lại lượt gọi; gỡ cửa sổ của Kachi / của mod (khi mod "hỗ trợ") khỏi bản đọc của [shell]. */
    private class Layers(val shell: FakeShell, val self: String, val modHides: Boolean, val installed: Boolean = true, val hidden: Boolean = false) :
        ClusterLayerPort {
        val calls = CopyOnWriteArrayList<String>()
        override fun pauseOwn() { calls += "pauseOwn"; shell.overlayWindows.removeAll { it == self } }
        override fun resumeOwn(clusterId: Int) { calls += "resumeOwn($clusterId)" }
        override fun bubbleInstalled() = installed
        override fun bubbleHiddenByUser() = hidden
        override fun sendBubble(show: Boolean) {
            calls += "bubble($show)"
            if (!show && modHides) shell.overlayWindows.removeAll { it == "vn.vietmap.live" }
        }
    }

    private fun seal(): FakeShell = FakeShell().apply {
        clusterDisplayId = 4
        vdAfterTheme = 9
        overlayWindows += listOf(self, vm, vm, vm)
    }

    private fun coordinator(shell: FakeShell, layers: ClusterLayerPort) = SimpleCastCoordinator(
        ProjectionManager(shell, sleepMs = {}, recipe = sealB),
        DisplayConfigurator(shell), AppMover(shell, sleepMs = {}), FakePrefs(), shell,
        displayId = 4, detectSleepMs = {}, desiredStyle = { CastStyle.RECT }, clusterLayers = layers,
    )

    private fun ops(h: List<String>) =
        h.filter { it.contains(" 2 i32 1000 i32 ") }.map { it.substringAfter("i32 1000 i32 ").substringBefore(" ").toInt() }

    private fun awaitIdle(c: SimpleCastCoordinator, shell: FakeShell) {
        val deadline = System.currentTimeMillis() + 8000
        while (c.state !is SimpleCastState.Idle && c.state !is SimpleCastState.Error && System.currentTimeMillis() < deadline) Thread.sleep(10)
        Thread.sleep(50)
        assertTrue(c.state is SimpleCastState.Idle, "state=${c.state} history=${shell.history}")
    }

    @Test
    fun `mod moi - don cum xong man ao trong - GUI 31, tra cum tren id MOI 9, show theo cong tac`() {
        val shell = seal()
        val layers = Layers(shell, self, modHides = true)
        val c = coordinator(shell, layers)
        c.openProjection()
        awaitIdle(c, shell)
        assertEquals(listOf(31, 16, 35), ops(shell.history), "dọn xong ⇒ 0 task + 0 cửa sổ ⇒ gửi theme: ${shell.history}")
        assertEquals(listOf("pauseOwn", "bubble(false)", "resumeOwn(9)", "bubble(true)"), layers.calls.toList())
        assertEquals(9, c.liveDisplayId)
        assertFalse(c.themeBubbleOldMod)
        assertEquals(emptyList<String>(), c.themeBlockers)
    }

    @Test
    fun `cong tac bong TAT - tra cum gui show=false (giu an)`() {
        val shell = seal()
        val layers = Layers(shell, self, modHides = true, hidden = true)
        val c = coordinator(shell, layers)
        c.openProjection()
        awaitIdle(c, shell)
        assertEquals(listOf("pauseOwn", "bubble(false)", "resumeOwn(9)", "bubble(false)"), layers.calls.toList())
    }

    @Test
    fun `mod CU - bong van con sau lenh an - bo theme (BUBBLE), bao mod cu, VAN tra cum`() {
        val shell = seal()
        val layers = Layers(shell, self, modHides = false)
        val c = coordinator(shell, layers)
        c.openProjection()
        awaitIdle(c, shell)
        assertEquals(listOf(16, 35), ops(shell.history), "bóng còn ⇒ không gửi theme, 16/35 đi tiếp (màn ảo có từ trước): ${shell.history}")
        assertTrue(c.themeBubbleOldMod, "Cài đặt phải nói 'bản mod VietMap cũ'")
        assertEquals(listOf("VietMap"), c.themeBlockers)
        assertEquals(listOf("pauseOwn", "bubble(false)", "resumeOwn(4)", "bubble(true)"), layers.calls.toList(), "TRẢ chạy cả khi bỏ theme")
    }

    @Test
    fun `VietMap khong cai, chi badge Kachi - khong gui broadcast, van gui theme`() {
        val shell = seal().apply { overlayWindows.removeAll { it == vm } }
        val layers = Layers(shell, self, modHides = true, installed = false)
        val c = coordinator(shell, layers)
        c.openProjection()
        awaitIdle(c, shell)
        assertEquals(listOf(31, 16, 35), ops(shell.history))
        assertEquals(listOf("pauseOwn", "resumeOwn(9)"), layers.calls.toList())
    }

    @Test
    fun `cua so app LA tren cum - khong don, khong broadcast, khong theme`() {
        val shell = seal().apply { overlayWindows += "com.example.other" }
        val layers = Layers(shell, self, modHides = true)
        val c = coordinator(shell, layers)
        c.openProjection()
        awaitIdle(c, shell)
        assertEquals(listOf(16, 35), ops(shell.history))
        assertEquals(emptyList<String>(), layers.calls.toList(), "luật cũ FOREIGN — không đụng lớp nào")
    }

    @Test
    fun `cum da trong tu dau - khong don, khong tra`() {
        val shell = seal().apply { overlayWindows.clear() }
        val layers = Layers(shell, self, modHides = true)
        val c = coordinator(shell, layers)
        c.openProjection()
        awaitIdle(c, shell)
        assertEquals(listOf(31, 16, 35), ops(shell.history))
        assertEquals(emptyList<String>(), layers.calls.toList())
    }

    @Test
    fun `resumeLayers khong don thi khong lam gi (idempotent)`() {
        val shell = seal()
        val layers = Layers(shell, self, modHides = true)
        val g = ClusterThemeGuard(shell, self, sleepMs = {}, vacantVdAllowed = { true }, layers = layers)
        g.resumeLayers(9)
        assertEquals(emptyList<String>(), layers.calls.toList())
    }
    /** Review Pass 3 — ném ở lệnh chiếu 16 (SAU khi theme đã gửi) để khoá "TRẢ chạy cả khi lượt mở ném". */
    private class ThrowOnCast(val inner: FakeShell) : SimpleCastShell {
        override fun execute(command: String): ShellResult =
            if (command.contains(" i32 1000 i32 16 ")) throw IllegalStateException("shell gãy giữa chừng") else inner.execute(command)
    }

    @Test
    fun `luot mo NEM giua chung sau khi da don - VAN tra cum (finally)`() {
        val shell = seal()
        val layers = Layers(shell, self, modHides = true)
        val c = SimpleCastCoordinator(
            ProjectionManager(ThrowOnCast(shell), sleepMs = {}, recipe = sealB),
            DisplayConfigurator(shell), AppMover(shell, sleepMs = {}), FakePrefs(), shell,
            displayId = 4, detectSleepMs = {}, desiredStyle = { CastStyle.RECT }, clusterLayers = layers,
        )
        c.openProjection()
        val deadline = System.currentTimeMillis() + 8000
        while (!layers.calls.any { it.startsWith("resumeOwn") } && System.currentTimeMillis() < deadline) Thread.sleep(10)
        Thread.sleep(50)
        assertEquals("pauseOwn", layers.calls.firstOrNull(), "đã dọn trước theme: ${layers.calls}")
        assertTrue(layers.calls.any { it.startsWith("resumeOwn(") }, "TRẢ phải chạy dù lượt mở ném: ${layers.calls}")
        assertEquals("bubble(true)", layers.calls.last(), "bóng trả theo công tắc BẬT")
    }

    @Test
    fun `cong theme ngoai luot MO (vd luot tat con opcode theme) - khong don, khong broadcast`() {
        val shell = seal()
        val layers = Layers(shell, self, modHides = true)
        val g = ClusterThemeGuard(shell, self, sleepMs = {}, vacantVdAllowed = { true }, layers = layers)
        val v = g.admit(31)
        assertTrue(v != ThemeVerdict.SEND, "luật cũ: cửa sổ phủ còn ⇒ không gửi ($v)")
        assertEquals(emptyList<String>(), layers.calls.toList(), "không có finally TRẢ ở lượt tắt ⇒ không được dọn")
        g.beginOpen()
        g.admit(31)
        assertEquals("pauseOwn", layers.calls.firstOrNull(), "trong lượt mở thì dọn như thường")
        g.resumeLayers(9)
        assertEquals("resumeOwn(9)", layers.calls[layers.calls.size - 2])
    }
}
