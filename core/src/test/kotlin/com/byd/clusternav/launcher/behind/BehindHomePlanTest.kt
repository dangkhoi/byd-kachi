package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.behind.BehindHomePlan.Evict
import com.byd.clusternav.launcher.behind.BehindHomePlan.Why
import com.byd.clusternav.modules.clustercast.StackParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * C4 (spec shortcuts-autostart R0.1–R0.4) — quyết định BEHIND-HOME trên `am stack list` NGUYÊN VĂN của máy ảo A10.
 *
 * Fixture (`core/src/test/resources/diagnostics/`):
 *  - `am-stack-list-emulator-2026-10-02-behind-06-*` / `-10-*` — trích nguyên văn từ
 *    `docs/diagnostics/behind-home-emulator-2026-10-01/06-p3-steps3-7.txt` và `10-p4-placeholder-totop.txt` (dump ĐÃ
 *    LỌC `grep -E 'Stack id|taskId'` ⇒ không có dòng `configuration` ⇒ loại stack đọc ra RỖNG). Trong `06`: A = Đồng hồ
 *    (task 2081), B = VietMap (2080), màn ảo 44, S = stack 68 (giữ chỗ là Settings, gọi bằng uid 2000).
 *  - `am-stack-list-emulator-2026-10-02-tm1-*` — T-M1, bản ĐẦY ĐỦ (có `configuration`), giữ chỗ là activity của
 *    chính Kachi (`BehindAnchorActivity`) — xem `BehindHomePlanTm1Test`.
 */
class BehindHomePlanTest {

    private fun fx(name: String) = StackParse.parse(
        javaClass.getResourceAsStream("/diagnostics/am-stack-list-emulator-2026-10-02-$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture am-stack-list-emulator-2026-10-02-$name.txt"),
    )

    private val clock = "com.google.android.deskclock"
    private val vm = "vn.vietmap.live"
    private val self = "com.byd.launcher"

    // ── R0.2 — CẤM O2-sai ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `06 after-3 - B o dinh man ao, A nam duoi - cho day A`() {
        assertEquals(Evict.Go(2081), BehindHomePlan.checkEvict(fx("behind-06-after-3"), 44, clock, vm, self, aIsSystem = false))
    }

    @Test
    fun `06 after-5 - A o DINH man ao - tu choi (O2-sai da do che HOME)`() {
        assertEquals(Evict.Stop(Why.A_ON_TOP), BehindHomePlan.checkEvict(fx("behind-06-after-5"), 44, clock, vm, self, false))
    }

    @Test
    fun `06 start - A o dinh, B nam duoi - tu choi, chua dao thu tu`() {
        assertEquals(Evict.Stop(Why.A_ON_TOP), BehindHomePlan.checkEvict(fx("behind-06-start"), 44, clock, vm, self, false))
    }

    @Test
    fun `06 after-4 - A da ra khoi man ao thi khong con gi de day`() {
        assertEquals(Evict.Stop(Why.A_NOT_ON_VD), BehindHomePlan.checkEvict(fx("behind-06-after-4"), 44, clock, vm, self, false))
    }

    @Test
    fun `cac rao chung R0-6 - doc hong, vd sai, chinh minh, app he thong`() {
        val e = fx("behind-06-after-3")
        assertEquals(Evict.Stop(Why.NO_READ), BehindHomePlan.checkEvict(emptyList(), 44, clock, vm, self, false))
        assertEquals(Evict.Stop(Why.BAD_VD), BehindHomePlan.checkEvict(e, 0, clock, vm, self, false))
        assertEquals(Evict.Stop(Why.SELF), BehindHomePlan.checkEvict(e, 44, self, vm, self, false))
        assertEquals(Evict.Stop(Why.SYSTEM_APP), BehindHomePlan.checkEvict(e, 44, clock, vm, self, aIsSystem = true))
        assertEquals(Evict.Stop(Why.B_NOT_ON_TOP), BehindHomePlan.checkEvict(e, 44, clock, "com.other", self, false))
    }

    @Test
    fun `A dang co task tren display khac (chieu cum) thi khong day - dan xuat tu 06 after-3`() {
        // DẪN XUẤT: thêm một stack của Đồng hồ trên display 2 (cụm) vào bản đọc thật.
        val e = fx("behind-06-after-3") + StackParse.parse(
            "Stack id=90 displayId=2 userId=0\n  taskId=2099: com.google.android.deskclock/com.android.deskclock.DeskClock userId=0 visible=true",
        )
        assertEquals(Evict.Stop(Why.CAST), BehindHomePlan.checkEvict(e, 44, clock, vm, self, false))
    }

    // ── S chặt (R0.4) ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `S loai RONG (dump da loc) khong bao gio duoc chon - 10 before-move`() {
        // Stack 76 (giữ chỗ Settings) mới có trong 10, vắng ở 06 start ⇒ là stack giữ chỗ VỪA tạo, nhưng loại không đọc
        // được bằng chữ ⇒ NotStrict, không Ok. Đây đúng là lý do T-M1 phải chụp bản ĐẦY ĐỦ.
        val pick = BehindHomePlan.pickAnchor(fx("behind-06-start"), fx("behind-10-before-move"), "com.android.settings/com.android.settings.Settings")
        assertEquals(BehindHomePlan.Anchor.NotStrict(76), pick)
    }

    @Test
    fun `stack giu cho o DINH display 0 hoac dang hien la InFront (ROM bo qua khoa) - 06 after-6`() {
        // [ĐO] 06 bước 6: S (68) lên trên HOME, visible=true. Lấy before = after-5 (68 có rồi) ⇒ không phải stack mới.
        assertEquals(BehindHomePlan.Anchor.Missing, BehindHomePlan.pickAnchor(fx("behind-06-after-5"), fx("behind-06-after-6"), "x/y"))
        // DẪN XUẤT: coi 68 là stack mới (before rỗng) ⇒ nó chứa Đồng hồ ⇒ không phải mọi task là giữ chỗ ⇒ Missing.
        assertEquals(BehindHomePlan.Anchor.Missing, BehindHomePlan.pickAnchor(emptyList(), fx("behind-06-after-6"),
            "com.android.settings/com.android.settings.Settings"))
        // DẪN XUẤT: stack giữ chỗ ĐƠN ở đỉnh display 0.
        val top = StackParse.parse(
            "Stack id=81 displayId=0 userId=0\n  taskId=3000: com.byd.launcher/a.Anchor userId=0 visible=true\n" +
                "Stack id=0 displayId=0 userId=0\n  taskId=2079: com.byd.launcher/com.byd.clusternav.launcher.KachiHome userId=0 visible=false",
        )
        assertEquals(BehindHomePlan.Anchor.InFront(81), BehindHomePlan.pickAnchor(emptyList(), top, "com.byd.launcher/a.Anchor"))
    }

    // ── Đọc lại sau move-task ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `10 after-move-true - A trong S, HOME van dinh - OK`() {
        val before = fx("behind-10-before-move")
        val top0 = BehindHomePlan.topStackId(before, 0)
        assertEquals(0, top0)
        assertEquals(BehindHomePlan.Moved.OK, BehindHomePlan.verifyMoved(fx("behind-10-after-move-true"), 2091, 76, top0))
        assertTrue(BehindHomePlan.homeOnTop(fx("behind-10-after-move-true"), listOf("com.byd.launcher/com.byd.clusternav.launcher.KachiHome")))
    }

    @Test
    fun `06 after-6 - S len truoc HOME - FRONT_CHANGED (bat buoc K12)`() {
        val top0 = BehindHomePlan.topStackId(fx("behind-06-after-5"), 0)
        assertEquals(BehindHomePlan.Moved.FRONT_CHANGED, BehindHomePlan.verifyMoved(fx("behind-06-after-6"), 2081, 68, top0))
        assertFalse(BehindHomePlan.homeOnTop(fx("behind-06-after-6"), listOf("com.byd.launcher/com.byd.clusternav.launcher.KachiHome")))
    }

    /**
     * [ĐO E2E máy ảo 02/10 `c5a-trip-generic`, fixture NGUYÊN VĂN] màn nhà là task `…KachiHomeActivity` trong stack
     * `standard` (stack `home` rỗng): chỉ đọc loại `home` thì "HOME đang ở đỉnh" = sai ⇒ lượt S lên trước (K12) không
     * được đưa màn nhà lên lại. Nhận dạng activity thật qua `DefaultHome.shownComponents` ⇒ đúng; app khác không lẫn.
     */
    @Test
    fun `HOME o dinh - man nha la KachiHomeActivity trong stack standard`() {
        val e = fx("e2e-standard-home")
        val homes = listOf("com.byd.launcher/com.byd.clusternav.launcher.KachiHome", "com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity")
        assertFalse(BehindHomePlan.homeOnTop(e, emptyList()), "loại `home` rỗng — chỉ đọc loại thì không thấy màn nhà")
        assertTrue(BehindHomePlan.homeOnTop(e, homes))
        assertFalse(BehindHomePlan.homeOnTop(fx("behind-06-after-6"), homes), "S lên trước ⇒ vẫn là không")
    }

    @Test
    fun `move-task voi task cu la no-op - doc lai thay chua di la NOT_MOVED`() {
        assertEquals(BehindHomePlan.Moved.NOT_MOVED, BehindHomePlan.verifyMoved(fx("behind-06-after-3"), 2081, 68, 0))
    }

    // ── Lệnh ─────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `lenh golden`() {
        assertEquals("am stack move-task 2081 68 true", BehindHomePlan.moveTaskCmd(2081, 68))
        assertEquals("am start --display 44 -n 'vn.vietmap.live/vn.vietmap.live.MainActivity'",
            BehindHomePlan.bringToFrontCmd(44, "vn.vietmap.live/vn.vietmap.live.MainActivity"))
        assertEquals(
            "am start --display 44 --windowingMode 1 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER" +
                " -n 'com.google.android.youtube/com.google.android.youtube.app.honeycomb.Shell\$HomeActivity'",
            BehindHomePlan.stageCmd(44, "com.google.android.youtube/com.google.android.youtube.app.honeycomb.Shell\$HomeActivity"),
        )
    }

    @Test
    fun `lenh tu choi display 0 va component co the chen shell`() {
        assertThrows(IllegalArgumentException::class.java) { BehindHomePlan.bringToFrontCmd(0, "a/b") }
        assertThrows(IllegalArgumentException::class.java) { BehindHomePlan.stageCmd(0, "a/b") }
        assertThrows(IllegalArgumentException::class.java) { BehindHomePlan.bringToFrontCmd(44, "a/b' ; rm -rf / '") }
        assertFalse(BehindHomePlan.safeComponent("a/b c"))
        assertTrue(BehindHomePlan.safeComponent("com.google.android.youtube/com.google.android.youtube.app.honeycomb.Shell\$HomeActivity"))
    }

    // ── R0.3 — chỗ dàn dựng ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `cho dan dung - o song nho nhat, app khac X, co man ao`() {
        val s = listOf(
            BehindHomePlan.Stage(0, 44, vm, area = 900_000, alive = true),
            BehindHomePlan.Stage(1, 45, "com.y", area = 300_000, alive = true),
            BehindHomePlan.Stage(2, 46, "com.z", area = 100_000, alive = false),
            BehindHomePlan.Stage(3, 0, "com.w", area = 50_000, alive = true),
        )
        assertEquals(1, BehindHomePlan.stagingSlot(s, clock)?.slot)
        assertEquals(0, BehindHomePlan.stagingSlot(s, "com.y")?.slot, "ô đang là X thì không dàn dựng X ở đó")
        assertNull(BehindHomePlan.stagingSlot(s.filter { it.slot >= 2 }, clock), "không ô sống ⇒ NO_STAGE")
    }

    @Test
    fun `R1-8 - app sau man nha theo nghia chat chi nhan khi doc duoc loai bang chu`() {
        // 10 after-move-true: Đồng hồ ở S (76) cùng giữ chỗ Settings, loại rỗng ⇒ không phải "sau màn nhà" theo nghĩa chặt.
        assertNull(BehindHomePlan.behindTaskOf(fx("behind-10-after-move-true"), clock))
    }
}
