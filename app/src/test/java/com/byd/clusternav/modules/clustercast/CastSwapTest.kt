package com.byd.clusternav.modules.clustercast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * v0.7x CP/AA-CORRECT hot-swap — test chuỗi lệnh + bất biến của [CastShell.swapOnVd] / [CastShell.returnAppToMain]
 * / [CastShell.evictVd] chạy off-xe (FakeShell). Bám recipe DashCast + review doc §3/§5.
 *
 * ⚠ Off-car KHÔNG tái hiện WM NPE (chỉ xe làm được). Các test này chốt CHUỖI LỆNH + BẤT BIẾN:
 *   (a) target THƯỜNG → FRESH-LAUNCH (`am force-stop` + `--activity-clear-task`, recipe #4 — hết trắng/ADAS-đen);
 *   (b) target SINK (CP/AA) → RESUME `--wm5` (KHÔNG force-stop, KHÔNG clear-task — giữ phiên chiếu điện thoại);
 *   (c) OCCLUDE-VERIFY: app cũ chỉ được gentle-return SAU khi `isVisible==false` (né change-transition → orphan v0.67);
 *   (d) SINK cũ cưỡng lại occlude → ĐỂ YÊN trên VD (KHÔNG force-stop, KHÔNG move-stack, 2-on-VD, KHÔNG crash);
 *   (e) TUYỆT ĐỐI KHÔNG `am display move-stack …0` trên switch path (né NPE B); VD không rỗng (F1/F4 → né NPE A).
 * Ràng buộc: F1 (oldApp==target), F4 (bounce), R11 (occlude-before-gentle), R9 (fresh-launch/resume split).
 */
class CastSwapTest {

    private val silent: (String) -> Unit = {}
    private val VIETMAP = "vn.vietmap.live"
    private val MAPS = "app.revanced.android.apps.maps"
    private val WAZE = "com.waze"
    private val CARPLAY = "com.byd.carplay.ui"

    private fun app(id: Int, task: Int, disp: Int, pkg: String, mode: String = "fullscreen") =
        FakeStack(id, disp, task, "$pkg/.MainActivity", mode = mode)
    private fun sink(id: Int, task: Int, disp: Int, mode: String = "fullscreen") =
        FakeStack(id, disp, task, "$CARPLAY/com.byd.carplay.ui.VideoActivity", mode = mode)

    /** KHÔNG có `am display move-stack <sid> 0` (bê KHỎI VD về d0 = primitive NPE B) ở BẤT KỲ đâu trên switch path. */
    private fun noMoveToZero(dev: FakeDevice) =
        dev.commands.none { Regex("am display move-stack \\d+ 0(\\s|$)").containsMatchIn(it) }

    /** pkg app-thường đang bám VD. */
    private fun onVd(dev: FakeDevice, vd: Int) =
        dev.stacks.filter { it.displayId == vd && it.activityType == "standard" && it.mode != "pinned" }
            .map { it.comp.substringBefore('/') }

    // ───────── (1) NORMAL target → FRESH-LAUNCH (force-stop + --activity-clear-task) — recipe #4 (R9) ─────────
    @Test
    fun `normal target - FRESH-LAUNCH emits force-stop + activity-clear-task`() {
        val dev = FakeDevice(vd = 1)
            .add(app(3, 5, 0, "com.android.launcher3"))
            .add(app(44, 50, 1, VIETMAP, mode = "freeform"))        // Vietmap ĐANG chiếu trên VD (old)
        val sh = fakeShell(dev)

        val res = CastShell.swapOnVd(sh, MAPS, "$MAPS/.MapsActivity", oldApp = VIETMAP, vd = 1, log = silent)

        assertNotNull(res.target, "Maps phải bám VD sau fresh-launch")
        assertTrue(dev.forceStoppedPkgs.contains(MAPS), "R9: target THƯỜNG → FRESH-LAUNCH phải `am force-stop $MAPS`")
        assertTrue(dev.commands.any { it.startsWith("am start") && it.contains("--activity-clear-task") && it.contains(MAPS) },
            "R9: fresh-launch phải `am start … --activity-clear-task` cho target thường (composite full-VD, recipe #4)")
        assertEquals(listOf(MAPS), onVd(dev, 1), "VD chỉ còn ĐÚNG Maps (1 app)")
        assertFalse(dev.forceStoppedPkgs.contains(VIETMAP), "old Vietmap occluded → GENTLE về d0 (GIỮ state), KHÔNG force-stop")
        val vm = dev.stacks.first { it.comp.substringBefore('/') == VIETMAP }
        assertEquals(0, vm.displayId, "Vietmap RỜI VD, về d0"); assertEquals("fullscreen", vm.mode, "Vietmap FULLSCREEN d0 (H4)")
        assertTrue(noMoveToZero(dev), "né NPE B: KHÔNG `am display move-stack …0`")
    }

    // ───────── (2) SINK target CarPlay → RESUME (NO clear-task, NO force-stop of sink) — R9 ─────────
    @Test
    fun `sink target CarPlay - RESUME no clear-task no force-stop of sink`() {
        val dev = FakeDevice(vd = 1)
            .add(app(3, 5, 0, "com.android.launcher3"))
            .add(app(44, 50, 1, VIETMAP, mode = "freeform"))        // old = Vietmap (thường) trên VD
        val sh = fakeShell(dev)

        val res = CastShell.swapOnVd(sh, CARPLAY, "$CARPLAY/com.byd.carplay.ui.VideoActivity", oldApp = VIETMAP, vd = 1, log = silent)

        assertNotNull(res.target, "CarPlay bám VD (resume)")
        assertFalse(dev.forceStoppedPkgs.contains(CARPLAY),
            "R9: SINK target (isPhoneProjection) TUYỆT ĐỐI KHÔNG force-stop — giữ phiên chiếu điện thoại")
        assertTrue(dev.commands.none { it.contains("--activity-clear-task") && it.contains(CARPLAY) },
            "R9: SINK target RESUME — KHÔNG `--activity-clear-task`")
        assertTrue(dev.commands.any { it.startsWith("am start") && it.contains("--windowingMode 5") && it.contains(CARPLAY) },
            "SINK target đặt bằng `am start --wm5` (resume)")
        assertEquals(listOf(CARPLAY), onVd(dev, 1), "VD chỉ còn CarPlay (old Vietmap gentle về d0)")
        assertTrue(noMoveToZero(dev))
    }

    // ───────── (3) OCCLUDE-VERIFY: old chỉ được gentle-return SAU khi isVisible==false (R11) ─────────
    @Test
    fun `occlude-verify - old gentle-returned only AFTER not-visible`() {
        val dev = FakeDevice(vd = 1)
            .add(app(3, 5, 0, "com.android.launcher3"))
            .add(app(44, 50, 1, VIETMAP, mode = "freeform"))        // old
        val sh = fakeShell(dev)

        CastShell.swapOnVd(sh, MAPS, "$MAPS/.MapsActivity", oldApp = VIETMAP, vd = 1, log = silent)

        // OCCLUDE-VERIFY (`dumpsys window displays`) PHẢI xảy ra TRƯỚC gentle-return old (`am start --display 0 … Vietmap`).
        val occludeIdx = dev.commands.indexOfFirst { it.contains("dumpsys window displays") }
        val gentleIdx = dev.commands.indexOfFirst { it.startsWith("am start --display 0") && it.contains(VIETMAP) }
        assertTrue(occludeIdx >= 0, "phải có bước occlude-verify (dumpsys window displays)")
        assertTrue(gentleIdx >= 0, "old phải được gentle-return về d0")
        assertTrue(occludeIdx < gentleIdx, "R11: occlude-verify PHẢI trước gentle-return (né change-transition → orphan)")
        val vm = dev.stacks.first { it.comp.substringBefore('/') == VIETMAP }
        assertEquals(0, vm.displayId, "old occluded → gentle-returned về d0")
        assertFalse(dev.forceStoppedPkgs.contains(VIETMAP), "occluded → GENTLE (GIỮ state), KHÔNG force-stop")
    }

    // ───────── (4) SINK old CƯỠNG LẠI occlude → LEFT on VD, KHÔNG force-stop, KHÔNG move-stack (R11/OQ3) ─────────
    @Test
    fun `sink old resists occlude - LEFT on VD not force-stopped no move-stack no crash`() {
        val dev = FakeDevice(vd = 1, resistReturnPkgs = mutableSetOf(CARPLAY))  // CarPlay size-compat: luôn visible, cưỡng lại
            .add(app(3, 5, 0, "com.android.launcher3"))
            .add(sink(66, 71, 1, mode = "freeform"))                // CarPlay đang chiếu trên VD (old = sink)
        val sh = fakeShell(dev)

        val res = CastShell.swapOnVd(sh, VIETMAP, "$VIETMAP/.MainActivity", oldApp = CARPLAY, vd = 1, log = silent)

        assertNotNull(res.target, "Vietmap (target thường) vẫn bám VD (fresh-launch)")
        assertTrue(onVd(dev, 1).contains(CARPLAY), "R11/OQ3: SINK cũ cưỡng lại → ĐỂ YÊN trên VD (2-on-VD chấp nhận)")
        assertTrue(onVd(dev, 1).contains(VIETMAP), "target Vietmap trên VD")
        assertFalse(dev.forceStoppedPkgs.contains(CARPLAY), "R11: SINK cũ cưỡng lại → KHÔNG force-stop (giữ phiên)")
        assertTrue(noMoveToZero(dev), "R11: KHÔNG `move-stack …0` (kể cả cho sink cưỡng lại)")
    }

    // ───────── (5) CHIẾU LẠI CHÍNH APP (oldApp == target) — F1 (P0): KHÔNG force-stop, VD không rỗng ─────────
    @Test
    fun `re-cast same app - F1 no force-stop target VD not empty`() {
        val dev = FakeDevice(vd = 1)
            .add(app(3, 5, 0, "com.android.launcher3"))
            .add(app(44, 50, 1, MAPS, mode = "freeform"))           // Maps đang trên VD, lastCastApp == Maps
        val sh = fakeShell(dev)

        val res = CastShell.swapOnVd(sh, MAPS, "$MAPS/.MapsActivity", oldApp = MAPS, vd = 1, log = silent)

        assertNotNull(res.target, "Maps vẫn trên VD")
        assertTrue(dev.forceStoppedPkgs.isEmpty(),
            "F1 (P0): re-cast chính Maps → RESUME, KHÔNG fresh-launch/force-stop (force-stop app duy nhất → VD RỖNG → NPE A)")
        assertEquals(listOf(MAPS), onVd(dev, 1), "VD vẫn có Maps (KHÔNG rỗng)")
        assertTrue(noMoveToZero(dev))
    }

    // ───────── (6) B BOUNCE sau landed, TRƯỚC bước bê app cũ — F4 (TOCTOU): abort, giữ old ─────────
    @Test
    fun `B bounce after landed - F4 abort keep old not touch old`() {
        val dev = FakeDevice(vd = 1)
            .add(app(3, 5, 0, "com.android.launcher3"))
            .add(app(44, 50, 1, VIETMAP, mode = "freeform"))        // old = Vietmap trên VD
        val sh = fakeShell(dev)
        // ★ Maps (GL) bounce: NGAY SAU khi landedOn thấy Maps trên VD (render đầu tiên), đẩy Maps về d0.
        dev.afterStackList = {
            dev.stacks.filter { it.comp.substringBefore('/') == MAPS }.forEach { it.displayId = 0 }
            dev.afterStackList = null   // one-shot
        }

        val res = CastShell.swapOnVd(sh, MAPS, "$MAPS/.MapsActivity", oldApp = VIETMAP, vd = 1, log = silent)

        assertNull(res.target, "F4: Maps bounce khỏi VD TRƯỚC bước bê old → swap PHẢI abort")
        assertFalse(dev.forceStoppedPkgs.contains(VIETMAP), "F4: KHÔNG được đụng app CŨ khi abort (giữ old → VD không rỗng)")
        assertTrue(onVd(dev, 1).contains(VIETMAP), "giữ app cũ (Vietmap) trên cụm (né NPE A)")
        assertTrue(noMoveToZero(dev))
    }

    // ───────── (7) R2 FALLBACK: am start --display hụt → leo `move-stack … <vd>` (KHÔNG BAO GIỜ …0) ─────────
    @Test
    fun `am start display hut - swapOnVd leo R2 move-stack LEN VD (khong bao gio ra d0)`() {
        // Maps ở d0, `am start --display 1` bị ActivityStarter redirect về d0 (startNoLandPkgs) → buộc leo R2.
        val dev = FakeDevice(vd = 1, startNoLandPkgs = mutableSetOf(MAPS))
            .add(app(3, 5, 0, "com.android.launcher3"))
            .add(app(44, 50, 1, VIETMAP, mode = "freeform"))        // old đang chiếu trên VD
            .add(app(60, 61, 0, MAPS, mode = "fullscreen"))         // Maps ĐANG ở d0
        val sh = fakeShell(dev)

        val res = CastShell.swapOnVd(sh, MAPS, "$MAPS/.MapsActivity", oldApp = VIETMAP, vd = 1, log = silent)

        assertNotNull(res.target, "R2: sau move-stack LÊN VD, Maps phải bám VD")
        assertTrue(dev.moveCount > 0, "phải dùng R2 `am display move-stack` (am start --display hụt)")
        assertEquals(listOf(MAPS), onVd(dev, 1), "cụm chỉ còn Maps (1 app)")
        assertTrue(noMoveToZero(dev),
            "né NPE B: R2 chỉ move-stack LÊN VD ($MAPS→d1), KHÔNG `move-stack …0`")
        assertTrue(dev.commands.any { Regex("am display move-stack \\d+ 1(\\s|$)").containsMatchIn(it) }, "R2 phải phát `move-stack <sid> 1`")
        assertFalse(dev.forceStoppedPkgs.contains(VIETMAP), "old Vietmap occluded → gentle (KHÔNG force-stop)")
    }

    // ───────── (8) returnAppToMain — OCCLUDED app thường → GENTLE về d0, GIỮ state (KHÔNG force-stop) ─────────
    @Test
    fun `returnAppToMain occluded normal - GENTLE d0 fullscreen true no force-stop`() {
        val dev = FakeDevice(vd = 1)
            .add(app(3, 5, 0, "com.android.launcher3"))
            .add(app(44, 50, 1, VIETMAP, mode = "freeform"))
        val sh = fakeShell(dev)

        val ok = CastShell.returnAppToMain(sh, VIETMAP, vd = 1, oldOccluded = true, log = silent)

        assertTrue(ok, "occluded → gentle → app rời VD + d0 + fullscreen → true")
        assertTrue(dev.forceStoppedPkgs.isEmpty(), "GENTLE (occluded) → KHÔNG force-stop (GIỮ state)")
        val vm = dev.stacks.first { it.comp.substringBefore('/') == VIETMAP }
        assertEquals(0, vm.displayId); assertEquals("fullscreen", vm.mode)
        assertTrue(noMoveToZero(dev))
    }

    // ───────── (9) returnAppToMain — NOT occluded app thường → KHÔNG gentle, PHAO force-stop (an toàn) ─────────
    @Test
    fun `returnAppToMain not-occluded normal - no gentle force-stop fallback (R11)`() {
        val dev = FakeDevice(vd = 1)
            .add(app(3, 5, 0, "com.android.launcher3"))
            .add(app(44, 50, 1, VIETMAP, mode = "freeform"))
        val sh = fakeShell(dev)

        val ok = CastShell.returnAppToMain(sh, VIETMAP, vd = 1, oldOccluded = false, log = silent)

        assertTrue(ok, "app thường chưa occlude → force-stop + relaunch d0 fullscreen → true")
        assertTrue(dev.forceStoppedPkgs.contains(VIETMAP),
            "R11: chưa occlude → KHÔNG gentle-move task visible; app thường → PHAO force-stop (process death an toàn)")
        // KHÔNG có gentle `am start --display 0` TRƯỚC force-stop (không gentle-move task visible).
        val fsIdx = dev.commands.indexOfFirst { it.startsWith("am force-stop") && it.contains(VIETMAP) }
        val gentleBefore = dev.commands.take(fsIdx.coerceAtLeast(0)).any { it.startsWith("am start --display 0") && it.contains(VIETMAP) }
        assertFalse(gentleBefore, "R11: KHÔNG gentle-move task CÒN VISIBLE trước force-stop (đúng đường tránh orphan v0.67)")
        val vm = dev.stacks.first { it.comp.substringBefore('/') == VIETMAP }
        assertEquals(0, vm.displayId); assertEquals("fullscreen", vm.mode)
        assertTrue(noMoveToZero(dev))
    }

    // ───────── (10) returnAppToMain — OCCLUDED gentle hụt (gentleStay) → PHAO force-stop (F3) ─────────
    @Test
    fun `returnAppToMain occluded gentle-hut - PHAO force-stop fallback`() {
        val dev = FakeDevice(vd = 1, gentleStayOnVdPkgs = mutableSetOf(VIETMAP))
            .add(app(3, 5, 0, "com.android.launcher3"))
            .add(app(44, 50, 1, VIETMAP, mode = "freeform"))
        val sh = fakeShell(dev)

        val ok = CastShell.returnAppToMain(sh, VIETMAP, vd = 1, oldOccluded = true, log = silent)

        assertTrue(ok, "gentle hụt → phao force-stop + relaunch → d0 fullscreen → true")
        assertTrue(dev.forceStoppedPkgs.contains(VIETMAP), "gentle hụt (app thường) → PHAO force-stop")
        val vm = dev.stacks.first { it.comp.substringBefore('/') == VIETMAP }
        assertEquals(0, vm.displayId); assertEquals("fullscreen", vm.mode)
        assertTrue(noMoveToZero(dev))
    }

    // ───────── (11) returnAppToMain — kẹt freeform-bé trên d0 → trả false (F3) ─────────
    @Test
    fun `returnAppToMain stuck freeform on d0 - false (F3)`() {
        val dev = FakeDevice(vd = 1, stuckFreeformPkgs = mutableSetOf(VIETMAP))  // Vietmap phớt lờ windowingMode 1
            .add(app(3, 5, 0, "com.android.launcher3"))
            .add(app(44, 50, 1, VIETMAP, mode = "freeform"))
        val sh = fakeShell(dev)

        val ok = CastShell.returnAppToMain(sh, VIETMAP, vd = 1, oldOccluded = true, log = silent)

        assertFalse(ok, "F3: còn freeform-bé trên d0 (chưa fullscreen) → PHẢI trả false")
        val forcedFs = dev.commands.count { it.startsWith("am start") && it.contains("--windowingMode 1") && it.contains(VIETMAP) }
        assertTrue(forcedFs >= 2, "phải thử ép fullscreen ≥2 lần (gentle + re-force/phao) — thấy $forcedFs")
    }

    // ───────── (12) returnAppToMain — OCCLUDED sink → GENTLE off VD (giữ phiên), KHÔNG force-stop (F2) ─────────
    @Test
    fun `returnAppToMain occluded sink - gentle off VD no force-stop (F2)`() {
        val dev = FakeDevice(vd = 1)
            .add(app(3, 5, 0, "com.android.launcher3"))
            .add(sink(66, 71, 1, mode = "freeform"))
        val sh = fakeShell(dev)

        val ok = CastShell.returnAppToMain(sh, CARPLAY, vd = 1, oldOccluded = true, log = silent)

        assertTrue(ok, "sink occluded → gentle am start d0 → rời VD → true")
        assertTrue(dev.forceStoppedPkgs.isEmpty(), "F2: sink KHÔNG BAO GIỜ bị force-stop (isPhoneProjection che)")
        assertTrue(noMoveToZero(dev))
    }

    // ───────── (13) returnAppToMain — NOT occluded sink → LEAVE (KHÔNG force-stop, KHÔNG move-stack), false (R11) ─────────
    @Test
    fun `returnAppToMain not-occluded sink - LEAVE no force-stop no move-stack false (R11)`() {
        val dev = FakeDevice(vd = 1)
            .add(app(3, 5, 0, "com.android.launcher3"))
            .add(sink(66, 71, 1, mode = "freeform"))
        val sh = fakeShell(dev)

        val ok = CastShell.returnAppToMain(sh, CARPLAY, vd = 1, oldOccluded = false, log = silent)

        assertFalse(ok, "R11: sink chưa occlude → ĐỂ YÊN → còn trên VD → false")
        assertTrue(dev.forceStoppedPkgs.isEmpty(), "R11: sink chưa occlude → KHÔNG force-stop (giữ phiên)")
        assertTrue(dev.commands.none { it.startsWith("am start --display 0") && it.contains(CARPLAY) },
            "R11: KHÔNG gentle-move sink CÒN VISIBLE (đúng đường tránh orphan v0.67)")
        assertTrue(dev.stacks.any { it.comp.substringBefore('/') == CARPLAY && it.displayId == 1 }, "sink CarPlay vẫn trên VD (leave-in-place)")
        assertTrue(noMoveToZero(dev))
    }

    // ───────── (14) evictVd — app lạ occluded về d0 (gentle), sink lạ KHÔNG force-stop, keepPkg giữ (F6) ─────────
    @Test
    fun `evictVd occluded stray gentle - sink stray not force-stopped keepPkg kept (F6)`() {
        val dev = FakeDevice(vd = 1)
            .add(app(3, 5, 0, "com.android.launcher3"))
            .add(app(10, 11, 1, WAZE, mode = "freeform"))           // app lạ thường trên VD
            .add(sink(66, 71, 1, mode = "freeform"))                // sink lạ trên VD
            .add(app(20, 21, 1, MAPS, mode = "freeform"))           // app đích (keep) — added last → topmost → phủ strays
        val sh = fakeShell(dev)

        CastShell.evictVd(sh, vd = 1, keepPkg = MAPS, log = silent)

        assertFalse(dev.forceStoppedPkgs.contains(CARPLAY), "F6: sink lạ → KHÔNG force-stop (thừa hưởng miễn-trừ isPhoneProjection)")
        assertFalse(dev.forceStoppedPkgs.contains(MAPS), "keepPkg (đích) KHÔNG bị đụng")
        assertTrue(onVd(dev, 1).contains(MAPS), "app đích vẫn trên VD")
        assertFalse(onVd(dev, 1).contains(WAZE), "app lạ occluded đã rời VD (gentle)")
        assertTrue(noMoveToZero(dev), "F6: evict KHÔNG dùng move-stack …0")
    }

    @Test
    fun `evictVd vd khong hop le thi no-op`() {
        val dev = FakeDevice(vd = 1).add(app(10, 11, 1, WAZE))
        val sh = fakeShell(dev)
        CastShell.evictVd(sh, vd = 0, keepPkg = MAPS, log = silent)
        assertTrue(dev.forceStoppedPkgs.isEmpty(), "vd<1 → không đụng gì")
    }
}
