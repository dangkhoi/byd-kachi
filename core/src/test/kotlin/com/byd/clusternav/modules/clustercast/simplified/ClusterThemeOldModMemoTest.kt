package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ═══ 2.93 wave 2A · VM-BUBBLE-OLDMOD-MEMO — cổng theme nhớ "bản mod ĐANG CÀI không ẩn bóng" để bỏ 6 lượt đọc vô ích ═══════════
 *
 * Khoá [ĐO log xe 06/10 13:49 · 15:13, mod VietMap v1]: mỗi lượt mở, "dọn cụm" chờ bóng ẩn 6 lượt (~4,5–5 s) mà bóng không bao giờ
 * ẩn ⇒ bỏ theme. Nay: lượt dọn TRỌN chứng minh ⇒ ghi sổ theo dấu cài đặt; lượt sau cùng bản cài ⇒ bỏ dọn (0 broadcast, 0 lượt
 * đọc lại), quyết BUBBLE ⇒ KHÔNG gửi (rào "lớp còn trên màn ảo cụm ⇒ không bao giờ gửi opcode theme" không đổi); tin tối đa
 * [BubbleOldModMemo.SKIPS_MAX] lượt rồi thử thật lại; bản cài khác (cập nhật / cài lại) ⇒ thử thật; lượt bị hạn cắt (CAST-OPEN-
 * TIMEOUT) không ghi sổ. Bố cục cụm = Seal 138 (màn ảo 4, mức B) như `ClusterThemeBudgetTest`.
 */
class ClusterThemeOldModMemoTest {

    private val self = "com.byd.clusternav"
    private val vm = "vn.vietmap.live"

    /** Nơi lưu sổ (tệp `clustercast` giả) — sống qua "tiến trình" (nhiều thực thể cổng dùng chung). */
    private class Disk { var memo: String? = null; var writes = 0 }

    /**
     * Lớp phủ giả: [modHides] = mod v2 (gỡ bóng khi nhận lệnh ẩn); [token] = dấu cài đặt hiện tại của VietMap; [diskOk] = `false` ⇒
     * mọi lượt ghi sổ hỏng (thẻ đầy / prefs hỏng — review Pass 1).
     */
    private class Layers(
        val shell: FakeShell,
        val self: String,
        var modHides: Boolean,
        var token: String?,
        val disk: Disk,
        val diskOk: Boolean = true,
    ) : ClusterLayerPort {
        val calls = CopyOnWriteArrayList<String>()
        override fun pauseOwn() { calls += "pauseOwn"; shell.overlayWindows.removeAll { it == self } }
        override fun resumeOwn(clusterId: Int) { calls += "resumeOwn" }
        override fun bubbleInstalled() = true
        override fun bubbleHiddenByUser() = false
        override fun sendBubble(show: Boolean) {
            calls += "bubble($show)"
            if (!show && modHides) shell.overlayWindows.removeAll { it == "vn.vietmap.live" }
        }
        override fun bubbleInstallToken(): String? = token
        override fun oldModMemo(): String? = disk.memo
        override fun writeOldModMemo(value: String?): Boolean {
            if (!diskOk) return false
            disk.memo = value; disk.writes++; return true
        }
    }

    private fun seal(): FakeShell = FakeShell().apply { clusterDisplayId = 4; overlayWindows += listOf(self, vm, vm, vm) }

    private fun guard(shell: FakeShell, layers: ClusterLayerPort, budget: () -> Long? = { null }) =
        ClusterThemeGuard(shell, self, sleepMs = {}, vacantVdAllowed = { true }, layers = layers, budgetLeftMs = budget)

    private fun windowReads(shell: FakeShell) = shell.history.count { it == ClusterThemeGuard.WINDOWS_CMD }

    /** Một lượt mở (beginOpen → admit → resumeLayers) — đúng vòng đời `openProjectionGuarded`. */
    private fun open(g: ClusterThemeGuard): ThemeVerdict = g.also { it.beginOpen() }.admit(31).also { g.resumeLayers(4) }

    private val v1 = BubbleOldModMemo.token(1_042L, 1_759_700_000_000L)!!

    @Test
    fun `mod v1 - luot don TRON ghi so, luot sau cung ban cai BO don (0 broadcast, 0 luot doc lai), van KHONG gui`() {
        val disk = Disk()
        val shell = seal()
        val layers = Layers(shell, self, modHides = false, token = v1, disk = disk)
        val g = guard(shell, layers)
        assertNotEquals(ThemeVerdict.SEND, open(g))
        assertEquals(1 + ClusterThemeGuard.PAUSE_READS + 1, windowReads(shell), "lượt đầu dọn thật: ${shell.history}")
        assertEquals(BubbleOldModMemo.encode(BubbleOldModMemo.Entry(v1, 0)), disk.memo, "đủ 6 lượt, bóng còn ⇒ ghi sổ")
        assertTrue(g.lastBubbleOldMod)

        // "Tiến trình kế" (BYD giết Kachi lúc tắt máy): cổng MỚI, bóng của Kachi gắn lại, sổ trên đĩa.
        val shell2 = seal()
        val layers2 = Layers(shell2, self, modHides = false, token = v1, disk = disk)
        val g2 = guard(shell2, layers2)
        assertNotEquals(ThemeVerdict.SEND, open(g2), "sổ chỉ có thể ra KHÔNG gửi")
        assertEquals(1, windowReads(shell2), "chỉ bản đọc đầu — không dọn, không đọc lại: ${shell2.history}")
        assertEquals(emptyList<String>(), layers2.calls.toList(), "không gỡ badge, không VM_BUBBLE_VIS, không trả cụm")
        assertTrue(g2.lastBubbleOldMod, "Cài đặt vẫn nói 'bản mod VietMap cũ … tắt VietMap rồi Áp ngay'")
        assertEquals(listOf("VietMap"), g2.lastBlockers)
        assertTrue(g2.lastVerdict!!.contains("BUBBLE"), g2.lastVerdict)
        assertEquals(1, BubbleOldModMemo.decode(disk.memo)!!.skips)
    }

    @Test
    fun `tin co han - het SKIPS_MAX luot thi thu THAT lai, van mod cu thi ghi lai so tu 0`() {
        val disk = Disk().apply { memo = BubbleOldModMemo.encode(BubbleOldModMemo.Entry(v1, BubbleOldModMemo.SKIPS_MAX - 1)) }
        val shell = seal()
        val layers = Layers(shell, self, modHides = false, token = v1, disk = disk)
        val g = guard(shell, layers)
        open(g)
        assertEquals(1, windowReads(shell), "lượt bỏ cuối cùng còn trong hạn tin")
        assertEquals(BubbleOldModMemo.SKIPS_MAX, BubbleOldModMemo.decode(disk.memo)!!.skips)
        open(g)
        assertEquals(1 + 1 + ClusterThemeGuard.PAUSE_READS + 1, windowReads(shell), "hết hạn tin ⇒ dọn thật: ${shell.history}")
        assertEquals(listOf("pauseOwn", "bubble(false)", "resumeOwn", "bubble(true)"), layers.calls.toList())
        assertEquals(0, BubbleOldModMemo.decode(disk.memo)!!.skips, "chứng minh lại ⇒ đếm lại từ 0")
    }

    @Test
    fun `cai lai hay cap nhat ban mod (dau cai khac) - thu that, mod v2 an duoc thi GUI va xoa so`() {
        val disk = Disk().apply { memo = BubbleOldModMemo.encode(BubbleOldModMemo.Entry(v1, 0)) }
        val shell = seal()
        val v2SameVersionCode = BubbleOldModMemo.token(1_042L, 1_759_800_000_000L)!!   // người vá giữ versionCode gốc
        val layers = Layers(shell, self, modHides = true, token = v2SameVersionCode, disk = disk)
        val g = guard(shell, layers)
        assertEquals(ThemeVerdict.SEND, open(g), "bản cài khác ⇒ không tin sổ ⇒ dọn ⇒ sạch ⇒ gửi: ${shell.history}")
        assertNull(disk.memo, "bóng ẩn theo VM_BUBBLE_VIS ⇒ xoá sổ")
        assertFalse(g.lastBubbleOldMod)
    }

    @Test
    fun `luot don bi han luot mo cat (CAST-OPEN-TIMEOUT) - KHONG ghi so du bao mod cu`() {
        val disk = Disk()
        val shell = seal()
        val layers = Layers(shell, self, modHides = false, token = v1, disk = disk)
        var left: Long? = ClusterThemeGuard.OPEN_TAIL_RESERVE_MS + 2_000L
        val g = ClusterThemeGuard(
            shell, self, sleepMs = { left = left?.minus(920L) }, vacantVdAllowed = { true }, layers = layers, budgetLeftMs = { left },
        )
        g.beginOpen()
        assertNotEquals(ThemeVerdict.SEND, g.admit(31))
        assertTrue(windowReads(shell) < 1 + ClusterThemeGuard.PAUSE_READS + 1, "vòng đọc lại bị hạn cắt: ${shell.history}")
        assertTrue(g.lastBubbleOldMod, "lượt này vẫn báo mod cũ (hành vi 2.93 R2)")
        assertNull(disk.memo, "nhưng KHÔNG khoá điều có thể oan vào sổ (review CAST Pass 1 [P3])")
        assertEquals(0, disk.writes)
    }

    @Test
    fun `ngoai luot mo, hoac cum sach - so khong duoc hoi, duong cu nguyen ven`() {
        val disk = Disk().apply { memo = BubbleOldModMemo.encode(BubbleOldModMemo.Entry(v1, 0)) }
        // Ngoài lượt mở (lượt TẮT) ⇒ luật cũ: BUBBLE ⇒ bỏ, không dọn, không chạm sổ.
        val shell = seal()
        val layers = Layers(shell, self, modHides = false, token = v1, disk = disk)
        assertNotEquals(ThemeVerdict.SEND, guard(shell, layers).admit(31))
        assertEquals(0, disk.writes, "không ở lượt mở ⇒ không đếm lượt bỏ")
        // Cụm sạch (bóng đã tắt — người lái làm theo câu "tắt VietMap rồi Áp ngay") ⇒ gửi như thường dù sổ còn.
        val clean = seal().apply { overlayWindows.clear() }
        val g = guard(clean, Layers(clean, self, modHides = false, token = v1, disk = disk))
        assertEquals(ThemeVerdict.SEND, open(g), clean.history.toString())
        assertEquals(0, disk.writes)
    }

    @Test
    fun `khong doc duoc dau cai dat - khong dung so (don that nhu 2_90), khong ghi`() {
        val disk = Disk().apply { memo = BubbleOldModMemo.encode(BubbleOldModMemo.Entry(v1, 0)) }
        val shell = seal()
        val layers = Layers(shell, self, modHides = false, token = null, disk = disk)
        open(guard(shell, layers))
        assertEquals(1 + ClusterThemeGuard.PAUSE_READS + 1, windowReads(shell))
        assertEquals(0, disk.writes, "không có dấu ⇒ không ghi sổ")
    }

    /**
     * Senior review wave 2A Pass 1 [P2] — VietMap KHÔNG chạy: trên cụm chỉ còn lớp phủ của Kachi (badge) ⇒ dọn lớp Kachi, lệnh ẩn
     * vẫn đi (bản mod có cài), cụm sạch ngay ⇒ GỬI. Không có bóng nào để ẩn ⇒ lượt này không chứng minh gì về bản mod ⇒ sổ mod cũ
     * GIỮ NGUYÊN. Bản trước coi là "bóng đã ẩn" ⇒ XOÁ sổ ⇒ người lái mod v1 không luôn chạy VietMap mất tác dụng sổ (~5 s mỗi
     * lượt mở có bóng kế đó).
     */
    @Test
    fun `don chi lop cua Kachi (khong co bong) - sach, gui, KHONG xoa so mod cu`() {
        val memo = BubbleOldModMemo.encode(BubbleOldModMemo.Entry(v1, 1))
        val disk = Disk().apply { this.memo = memo }
        val shell = FakeShell().apply { clusterDisplayId = 4; overlayWindows += self }
        val layers = Layers(shell, self, modHides = false, token = v1, disk = disk)
        assertEquals(ThemeVerdict.SEND, open(guard(shell, layers)), shell.history.toString())
        assertTrue("bubble(false)" in layers.calls, "lệnh ẩn vẫn đi như 2.90: ${layers.calls}")
        assertEquals(memo, disk.memo, "không có bóng để ẩn ⇒ không phải phép thử bản mod ⇒ sổ giữ nguyên")
        assertEquals(0, disk.writes)
    }

    /**
     * Senior review wave 2A Pass 1 [P3] — lượt BỎ dọn phải đếm vào sổ TRƯỚC khi bỏ: ghi hỏng mà vẫn bỏ thì số lượt bỏ đứng yên ⇒ sổ
     * được tin mãi, vượt trần [BubbleOldModMemo.SKIPS_MAX] (D2). Ghi hỏng ⇒ dọn thật như 2.90 (vẫn KHÔNG gửi với mod v1).
     */
    @Test
    fun `khong ghi duoc luot bo vao so - DON THAT, khong tin so ngoai tran SKIPS_MAX`() {
        val memo = BubbleOldModMemo.encode(BubbleOldModMemo.Entry(v1, 0))
        val disk = Disk().apply { this.memo = memo }
        val shell = seal()
        val layers = Layers(shell, self, modHides = false, token = v1, disk = disk, diskOk = false)
        assertNotEquals(ThemeVerdict.SEND, open(guard(shell, layers)))
        assertEquals(1 + ClusterThemeGuard.PAUSE_READS + 1, windowReads(shell), "lượt bỏ không chạm đĩa ⇒ dọn thật: ${shell.history}")
        assertEquals(memo, disk.memo, "đĩa hỏng ⇒ sổ không đổi")
    }

    // ── Luật thuần ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `dau cai dat, ma hoa, giai ma chat`() {
        assertNull(BubbleOldModMemo.token(-1L, 1L))
        assertNull(BubbleOldModMemo.token(5L, 0L), "lần cài 0 = không đọc được")
        val e = BubbleOldModMemo.Entry(v1, 3)
        assertEquals(e, BubbleOldModMemo.decode(BubbleOldModMemo.encode(e)))
        listOf(null, "", "v2|1@2|0", "v1|1@2", "v1|abc|0", "v1|1@2|-1", "v1|1@2|${BubbleOldModMemo.SKIPS_MAX + 1}", "v1|1@2|x").forEach {
            assertNull(BubbleOldModMemo.decode(it), "phải từ chối «$it»")
        }
    }

    @Test
    fun `bang ket cuc - chi luot TRON moi ghi, an la xoa`() {
        assertEquals(BubbleOldModMemo.Outcome.PROVEN_OLD, BubbleOldModMemo.outcome(true, true, false, true))
        assertEquals(BubbleOldModMemo.Outcome.HID, BubbleOldModMemo.outcome(true, false, true, false))
        assertEquals(BubbleOldModMemo.Outcome.INCONCLUSIVE, BubbleOldModMemo.outcome(true, false, false, true), "bị hạn cắt")
        assertEquals(BubbleOldModMemo.Outcome.INCONCLUSIVE, BubbleOldModMemo.outcome(false, true, false, true), "chưa gửi lệnh ẩn")
        assertEquals(BubbleOldModMemo.Outcome.INCONCLUSIVE, BubbleOldModMemo.outcome(true, true, false, false), "còn thứ khác, không phải bóng")
        val m = BubbleOldModMemo.Entry(v1, 0)
        assertEquals(1, BubbleOldModMemo.onGate(m, v1)!!.skips)
        assertNull(BubbleOldModMemo.onGate(m, "1043@1"), "bản cài khác")
        assertNull(BubbleOldModMemo.onGate(m, null))
        assertNull(BubbleOldModMemo.onGate(null, v1))
        assertNull(BubbleOldModMemo.onGate(m.copy(skips = BubbleOldModMemo.SKIPS_MAX), v1), "hết hạn tin ⇒ thử thật")
    }
}
