package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ═══ 2.93 · CAST-OPEN-TIMEOUT — lượt chờ của cổng theme (dọn cụm · đọc lại sau gỡ ClusterBlack) BỊ CHẶN bởi phần hạn còn lại ═══
 *
 * Khoá lỗi [ĐO log xe 06/10 13:49 · 15:13, xe 2.91, mod VietMap v1]: "dọn cụm" chờ bóng ẩn ~4,5–5 s (6 lượt đọc lại — bóng v1
 * không bao giờ ẩn) ⇒ `bỏ theme (BUBBLE)` ⇒ 16/35 … ⇒ `TIMEOUT: openProjection`. Nay: lượt đọc lại thứ hai trở đi chỉ chạy khi
 * lượt mở còn > [ClusterThemeGuard.OPEN_TAIL_RESERVE_MS]; không đủ cho cả MỘT lượt ⇒ không dọn / không gỡ. Rào an toàn KHÔNG đổi:
 * cửa sổ phủ còn trên màn ảo cụm ⇒ KHÔNG BAO GIỜ gửi opcode theme.
 *
 * Đồng hồ giả: mỗi lượt ngủ [ClusterThemeGuard.SETTLE_STEP_MS] trừ hạn thêm [CAR_POLL_MS] = 250 ms ngủ + hai lệnh đọc ≈ 0,67 s
 * ([SUY log xe 06/10]: 12 lần đọc ≈ 3,3 s).
 */
class ClusterThemeBudgetTest {

    private val self = "com.byd.clusternav"
    private val vm = "vn.vietmap.live"
    private val standardConfig =
        " configuration={1.0 winConfig={ mBounds=Rect(0, 0 - 1920, 720) mWindowingMode=freeform mDisplayWindowingMode=fullscreen mActivityType=standard mAlwaysOnTop=undefined mRotation=ROTATION_0} s.3}"

    /** Lớp phủ giả: mod v1 ([modHides] = false) không gỡ bóng khi nhận lệnh ẩn. */
    private class Layers(val shell: FakeShell, val self: String, val modHides: Boolean) : ClusterLayerPort {
        val calls = CopyOnWriteArrayList<String>()
        override fun pauseOwn() { calls += "pauseOwn"; shell.overlayWindows.removeAll { it == self } }
        override fun resumeOwn(clusterId: Int) { calls += "resumeOwn($clusterId)" }
        override fun bubbleInstalled() = true
        override fun bubbleHiddenByUser() = false
        override fun sendBubble(show: Boolean) {
            calls += "bubble($show)"
            if (!show && modHides) shell.overlayWindows.removeAll { it == "vn.vietmap.live" }
        }
    }

    /** Phần hạn còn lại giả của lượt mở: mỗi lượt ngủ của cổng = một lượt đọc lại trên xe. `null` ⇒ ngoài executor. */
    private class Budget(var leftMs: Long?) {
        val sleep: (Long) -> Unit = { leftMs = leftMs?.minus(CAR_POLL_MS) }
    }

    private fun seal(): FakeShell = FakeShell().apply { clusterDisplayId = 4; overlayWindows += listOf(self, vm, vm, vm) }

    private fun guard(shell: FakeShell, layers: ClusterLayerPort, budget: Budget) = ClusterThemeGuard(
        shell, self, sleepMs = budget.sleep, vacantVdAllowed = { true }, layers = layers, budgetLeftMs = { budget.leftMs },
    )

    private fun windowReads(shell: FakeShell) = shell.history.count { it == ClusterThemeGuard.WINDOWS_CMD }

    @Test
    fun `mod v1, han du luc dau - doc lai cho toi khi chi con du tru, roi bo theme (khong gui)`() {
        val shell = seal()
        val layers = Layers(shell, self, modHides = false)
        val budget = Budget(14_000L)
        val g = guard(shell, layers, budget)
        g.beginOpen()
        val v = g.admit(31)
        assertNotEquals(ThemeVerdict.SEND, v, "bóng còn ⇒ KHÔNG gửi")
        // 14 000 → 13 080 → 12 160 → 11 240: lượt 2 và 3 còn > 12 000 trước khi chạy, lượt 4 thì không.
        assertEquals(1 + 3 + 1, windowReads(shell), "đọc đầu + 3 lượt đọc lại + đọc-quyết lại (bản cũ: 1 + 6 + 1): ${shell.history}")
        assertTrue(g.themeBubbleOldModOf(), "đã dọn + đọc lại mà bóng còn ⇒ báo mod cũ (đúng)")
        assertEquals(listOf("pauseOwn", "bubble(false)"), layers.calls.toList())
        g.resumeLayers(4)
        assertEquals("bubble(true)", layers.calls.last(), "TRẢ vẫn chạy")
    }

    @Test
    fun `khong du han cho du MOT luot doc lai - khong don, khong broadcast, khong bao mod cu oan`() {
        val shell = seal().apply { overlayWindows.removeAll { it == self } }   // chỉ bóng VietMap ⇒ quyết định đầu là BUBBLE
        val layers = Layers(shell, self, modHides = true)
        val g = guard(shell, layers, Budget(ClusterThemeGuard.OPEN_TAIL_RESERVE_MS))
        g.beginOpen()
        val v = g.admit(31)
        assertNotEquals(ThemeVerdict.SEND, v)
        assertEquals(emptyList<String>(), layers.calls.toList(), "không ẩn bóng của người lái khi không kịp đọc lại")
        assertFalse(g.lastBubbleOldMod, "chưa thử thì không được nói 'mod cũ'")
        assertEquals(listOf("VietMap"), g.lastBlockers, "Cài đặt vẫn nói lý do bóng nổi")
        assertEquals(1, windowReads(shell))
        g.resumeLayers(4)
        assertEquals(emptyList<String>(), layers.calls.toList(), "chưa dọn ⇒ TRẢ không làm gì")
    }

    @Test
    fun `mod v2, han du - bong an o lan doc lai dau - van GUI theme nhu ban 2-90`() {
        val shell = seal()
        val layers = Layers(shell, self, modHides = true)
        val g = guard(shell, layers, Budget(20_000L))
        g.beginOpen()
        assertEquals(ThemeVerdict.SEND, g.admit(31), "dọn xong ⇒ 0 task + 0 cửa sổ ⇒ gửi: ${shell.history}")
        assertEquals(1 + 1 + 1, windowReads(shell), "sạch ngay lượt đọc lại đầu")
    }

    @Test
    fun `ngoai executor (han null) - hanh vi cu, du 6 luot doc lai`() {
        val shell = seal()
        val g = guard(shell, Layers(shell, self, modHides = false), Budget(null))
        g.beginOpen()
        g.admit(31)
        assertEquals(1 + ClusterThemeGuard.PAUSE_READS + 1, windowReads(shell))
    }

    @Test
    fun `chi con ClusterBlack ma khong du han go roi doc lai - KHONG go (0 lenh ghi), khong gui`() {
        val shell = seal().apply { overlayWindows.clear(); clusterStackConfig = standardConfig }
        shell.execute("am start --display 4 --windowingMode 5 -n '$self/com.byd.clusternav.modules.clustercast.ClusterBlackActivity'")
        val g = guard(shell, Layers(shell, self, modHides = true), Budget(ClusterThemeGuard.OPEN_TAIL_RESERVE_MS - 1))
        g.beginOpen()
        val v = g.admit(31)
        assertNotEquals(ThemeVerdict.SEND, v)
        assertFalse(shell.history.any { it.startsWith("am stack remove") }, "không gỡ khi không kịp xác nhận trống: ${shell.history}")
        assertTrue(g.lastVerdict!!.contains("NOT_REMOVABLE") && g.lastVerdict!!.contains("không còn đủ hạn"), g.lastVerdict)
    }

    @Test
    fun `chi con ClusterBlack, han du - go roi gui nhu ban cu`() {
        val shell = seal().apply { overlayWindows.clear(); clusterStackConfig = standardConfig }
        shell.execute("am start --display 4 --windowingMode 5 -n '$self/com.byd.clusternav.modules.clustercast.ClusterBlackActivity'")
        val g = guard(shell, Layers(shell, self, modHides = true), Budget(20_000L))
        g.beginOpen()
        assertEquals(ThemeVerdict.SEND, g.admit(31), shell.history.toString())
        assertTrue(shell.history.any { it.startsWith("am stack remove") })
    }

    /**
     * Review 2.93 Pass 1 [P2] — khoá nhánh còn thiếu: lệnh gỡ ĐÃ gửi (đủ hạn lúc gỡ), hệ gỡ chậm, hạn chạm dự trữ giữa các lượt
     * đọc lại ⇒ vòng đọc dừng sớm, quyết cuối từ bản đọc lại CUỐI (placeholder còn) ⇒ STILL_OCCUPIED, KHÔNG gửi. Rào "lớp còn trên
     * màn ảo cụm ⇒ không bao giờ gửi opcode theme" phải đứng cả khi vòng đọc lại bị hạn cắt.
     */
    @Test
    fun `go ClusterBlack xong ma he go cham, han cham du tru giua luot doc lai - KHONG gui`() {
        val fake = seal().apply { overlayWindows.clear(); clusterStackConfig = standardConfig }
        fake.execute("am start --display 4 --windowingMode 5 -n '$self/com.byd.clusternav.modules.clustercast.ClusterBlackActivity'")
        val removes = CopyOnWriteArrayList<String>()
        // Shell nhận lệnh gỡ (exit 0) nhưng ClusterBlack CHƯA đi — mọi bản đọc lại vẫn thấy nó.
        val slowRemoval = object : SimpleCastShell {
            override fun execute(command: String): ShellResult =
                if (command.startsWith("am stack remove")) { removes += command; ShellResult(0, "", "") } else fake.execute(command)
        }
        // 12 900 > dự trữ ⇒ được gỡ; một lượt đọc lại (−920) ⇒ 11 980 ≤ dự trữ ⇒ lượt đọc lại thứ hai không chạy.
        val budget = Budget(ClusterThemeGuard.OPEN_TAIL_RESERVE_MS + 900L)
        val g = ClusterThemeGuard(
            slowRemoval, self, sleepMs = budget.sleep, vacantVdAllowed = { true }, layers = Layers(fake, self, modHides = true),
            budgetLeftMs = { budget.leftMs },
        )
        g.beginOpen()
        val v = g.admit(31)
        assertNotEquals(ThemeVerdict.SEND, v, "placeholder còn trên màn ảo cụm ⇒ KHÔNG gửi: ${fake.history}")
        assertEquals(listOf("am stack remove 2"), removes.toList(), "đủ hạn lúc gỡ ⇒ gỡ đúng stack placeholder một lần")
        assertEquals(1 + 1, windowReads(fake), "đọc đầu + MỘT lượt đọc lại (lượt hai bị hạn cắt): ${fake.history}")
        assertTrue(g.lastVerdict!!.contains("STILL_OCCUPIED"), g.lastVerdict)
    }

    @Test
    fun `luot TAT (ngoai luot mo) - han khong chen vao duong cu`() {
        val shell = seal().apply { overlayWindows.clear(); clusterStackConfig = standardConfig }
        shell.execute("am start --display 4 --windowingMode 5 -n '$self/com.byd.clusternav.modules.clustercast.ClusterBlackActivity'")
        val g = guard(shell, Layers(shell, self, modHides = true), Budget(0L))
        assertEquals(ThemeVerdict.SEND, g.admit(31), "không beginOpen ⇒ luật hạn không áp: ${shell.history}")
    }

    /** Đọc qua thuộc tính công khai của cổng (cùng thứ `themeBubbleOldMod` của coordinator trả). */
    private fun ClusterThemeGuard.themeBubbleOldModOf(): Boolean = lastBubbleOldMod

    private companion object {
        /** 250 ms ngủ + `am stack list` + `dumpsys window windows` ≈ 0,92 s một lượt trên xe [SUY log 06/10]. */
        const val CAR_POLL_MS = 920L
    }
}
