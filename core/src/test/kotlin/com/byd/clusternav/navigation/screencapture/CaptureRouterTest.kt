package com.byd.clusternav.navigation.screencapture

import com.byd.clusternav.navigation.ArrayPixelFrame
import com.byd.clusternav.navigation.ManeuverRegistry
import com.byd.clusternav.navigation.ManeuverSignature
import com.byd.clusternav.navigation.PixelFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá logic THUẦN của [CaptureRouter] off-car (V-unit, spec §4.3/§4.4): chọn 1/4 case + tính crop bounds
 * theo 3 tầng, gồm gate đóng và offset nửa Case-2. Không cần thiết bị.
 */
class CaptureRouterTest {

    private val geom = DisplayGeometry(displayW = 1920, displayH = 720)   // main default; cluster id = 1

    private fun loc(
        pkg: String = "com.waze",
        displayId: Int = 0,
        fullscreen: Boolean = true,
        slot: CaptureSlotSide? = null,
        leftPercent: Int = 50,
        foreground: Boolean = true,
        navFresh: Boolean = true,
    ) = AppLocation(pkg, displayId, fullscreen, slot, leftPercent, foreground, navFresh)

    // ── (a) Chọn case — 4 case + biên ────────────────────────────────────────────────

    @Test
    fun `Case 1 — full man chinh`() {
        assertEquals(CaptureCase.FULL_MAIN, CaptureRouter.selectCase(loc(displayId = 0, fullscreen = true), geom))
    }

    @Test
    fun `Case 2 — nua man chinh split`() {
        assertEquals(
            CaptureCase.HALF_MAIN_SPLIT,
            CaptureRouter.selectCase(loc(displayId = 0, fullscreen = false, slot = CaptureSlotSide.LEFT), geom),
        )
    }

    @Test
    fun `Case 3 — ben cum (display 1)`() {
        assertEquals(CaptureCase.CLUSTER_CAST, CaptureRouter.selectCase(loc(displayId = 1), geom))
    }

    @Test
    fun `Case 4 — nav tuoi nhung app khong foreground`() {
        assertEquals(CaptureCase.NOT_ACTIVE, CaptureRouter.selectCase(loc(foreground = false), geom))
    }

    @Test
    fun `bien — foreground tren display phu khac cum coi nhu cast`() {
        assertEquals(CaptureCase.CLUSTER_CAST, CaptureRouter.selectCase(loc(displayId = 2), geom))
    }

    @Test
    fun `foreground=false thang the tren MOI displayId — luon NOT_ACTIVE`() {
        assertEquals(CaptureCase.NOT_ACTIVE, CaptureRouter.selectCase(loc(displayId = 1, foreground = false), geom))
        assertEquals(CaptureCase.NOT_ACTIVE, CaptureRouter.selectCase(loc(displayId = 0, foreground = false), geom))
    }

    // ── GATE (V-gate) ────────────────────────────────────────────────────────────────

    @Test
    fun `gate dong — navFresh=false thi route tra null (KHONG capture)`() {
        assertNull(CaptureRouter.route(loc(navFresh = false), geom))
    }

    @Test
    fun `gate mo — navFresh=true thi route KHONG null`() {
        assertNotNull(CaptureRouter.route(loc(navFresh = true), geom))
    }

    // ── (b) Bounds 3 tầng ──────────────────────────────────────────────────────────────

    @Test
    fun `bounds tang 2 — a11y null thi dung rect co dinh (Waze arrow OpenBYD)`() {
        val plan = CaptureRouter.route(loc(pkg = "com.waze", fullscreen = true), geom, a11y = null)!!
        assertEquals(BoundsSource.FIXED_CALIBRATED, plan.boundsSource)
        assertEquals(CaptureCalibration.WAZE_ARROW_BANNER_D240, plan.bounds)
        assertEquals(CaptureTarget.ARROW, plan.target)
    }

    @Test
    fun `bounds tang 1 — a11y tuoi thi thang rect co dinh`() {
        val a11yRect = CropRect(100, 100, 200, 180)
        val plan = CaptureRouter.route(
            loc(fullscreen = true), geom,
            a11y = CaptureBounds(a11yRect, capturedAtMs = 1_000L, target = CaptureTarget.ARROW),
            now = 1_200L, freshMs = 1500L,
        )!!
        assertEquals(BoundsSource.A11Y_DYNAMIC, plan.boundsSource)
        assertEquals(a11yRect, plan.bounds)
    }

    @Test
    fun `bounds — a11y CU (qua freshMs) thi roi ve tang 2 co dinh`() {
        val plan = CaptureRouter.route(
            loc(pkg = "com.waze", fullscreen = true), geom,
            a11y = CaptureBounds(CropRect(100, 100, 200, 180), capturedAtMs = 0L, target = CaptureTarget.ARROW),
            now = 5_000L, freshMs = 1500L,
        )!!
        assertEquals(BoundsSource.FIXED_CALIBRATED, plan.boundsSource)
        assertEquals(CaptureCalibration.WAZE_ARROW_BANNER_D240, plan.bounds)
    }

    @Test
    fun `target — VietMap ra CAMERA, con lai ra ARROW`() {
        assertEquals(CaptureTarget.CAMERA, CaptureRouter.route(loc(pkg = "vn.vietmap.live"), geom)!!.target)
        assertEquals(CaptureTarget.ARROW, CaptureRouter.route(loc(pkg = "com.waze"), geom)!!.target)
    }

    @Test
    fun `CaptureTarget — forPackage DON (a11y) vs targetsForPackage DA (routing)`() {
        // forPackage GIỮ ĐƠN (đường a11y NavAccessibilityService/CaptureBoundsHeuristic — agent khác sở hữu).
        assertEquals(CaptureTarget.CAMERA, CaptureTarget.forPackage("vn.vietmap.live"))
        assertEquals(CaptureTarget.ARROW, CaptureTarget.forPackage("com.waze"))
        // targetsForPackage = ĐA target cho routing (B3.8).
        assertEquals(listOf(CaptureTarget.ARROW, CaptureTarget.CAMERA), CaptureTarget.targetsForPackage("vn.vietmap.live"))
        assertEquals(listOf(CaptureTarget.ARROW), CaptureTarget.targetsForPackage("com.waze"))
        assertEquals(listOf(CaptureTarget.ARROW), CaptureTarget.targetsForPackage("com.google.android.apps.maps"))
    }

    // ── B3.8: routePlans — MỘT plan MỖI target (VietMap = arrow + camera; còn lại = arrow) ─────────────

    @Test
    fun `routePlans — VietMap ra HAI plan ARROW va CAMERA, moi cai bounds rieng`() {
        val plans = CaptureRouter.routePlans(loc(pkg = "vn.vietmap.live", fullscreen = true), geom)
        assertEquals(2, plans.size)
        val targets = plans.map { it.target }.toSet()
        assertEquals(setOf(CaptureTarget.ARROW, CaptureTarget.CAMERA), targets)
        // ARROW dùng rect mũi tên hiệu chỉnh; CAMERA dùng seed camera → hai vùng KHÁC nhau.
        val arrow = plans.first { it.target == CaptureTarget.ARROW }
        val camera = plans.first { it.target == CaptureTarget.CAMERA }
        assertEquals(CaptureCalibration.WAZE_ARROW_BANNER_D240, arrow.bounds)
        assertEquals(CaptureCalibration.VIETMAP_CAMERA_SEED, camera.bounds)
        assertEquals(BoundsSource.FIXED_CALIBRATED, arrow.boundsSource)
        assertEquals(BoundsSource.FIXED_CALIBRATED, camera.boundsSource)
    }

    @Test
    fun `routePlans — Waze ra MOT plan ARROW (tuong duong route cu)`() {
        val plans = CaptureRouter.routePlans(loc(pkg = "com.waze", fullscreen = true), geom)
        assertEquals(1, plans.size)
        assertEquals(CaptureTarget.ARROW, plans.single().target)
        assertEquals(CaptureCalibration.WAZE_ARROW_BANNER_D240, plans.single().bounds)
    }

    @Test
    fun `routePlans — GMaps ra MOT plan ARROW`() {
        val plans = CaptureRouter.routePlans(loc(pkg = "com.google.android.apps.maps"), geom)
        assertEquals(listOf(CaptureTarget.ARROW), plans.map { it.target })
    }

    @Test
    fun `routePlans — gate dong (navFresh=false) tra RONG (KHONG capture)`() {
        assertTrue(CaptureRouter.routePlans(loc(pkg = "vn.vietmap.live", navFresh = false), geom).isEmpty())
        assertTrue(CaptureRouter.routePlans(loc(pkg = "com.waze", navFresh = false), geom).isEmpty())
    }

    @Test
    fun `routePlans — moi plan giu case da chon (VietMap split RIGHT)`() {
        val plans = CaptureRouter.routePlans(
            loc(pkg = "vn.vietmap.live", fullscreen = false, slot = CaptureSlotSide.RIGHT, leftPercent = 50), geom,
        )
        assertEquals(2, plans.size)
        assertTrue(plans.all { it.case == CaptureCase.HALF_MAIN_SPLIT })
        // ARROW nửa phải: (26,218,208,298) +960 → (986,218,1168,298).
        val arrow = plans.first { it.target == CaptureTarget.ARROW }
        assertEquals(CropRect(1038, 50, 1118, 163), arrow.bounds)
    }

    /**
     * KHOÁ [P1] B3.53 vòng review (08-23) — **rect a11y đo cho MỘT target KHÔNG được áp cho target kia**.
     *
     * Bản trước của chính test này khẳng định điều NGƯỢC LẠI ("cùng holder a11y → cả hai target tầng-1 dùng
     * nó") và vì thế đã **khoá luôn cái lỗi**. [ĐO] 08-23 trên `CaptureRouter` thật:
     * ```
     * PLAN target=ARROW  bounds=CropRect(1500,300,1620,420) src=A11Y_DYNAMIC   ← rect của node CAMERA
     * PLAN target=CAMERA bounds=CropRect(1500,300,1620,420) src=A11Y_DYNAMIC
     * ```
     * Với VietMap, producer a11y chọn node bằng [CaptureTarget.forPackage] = [CaptureTarget.CAMERA] (xem
     * `NavAccessibilityService.maybePublishCaptureBounds`), nên rect trong holder LUÔN là icon camera; plan
     * ARROW nhận y hệt rect đó rồi đi vào tier [BoundsSource.A11Y_DYNAMIC] của
     * `ScreenCaptureNavSource.handleArrow` — tier dùng khớp **MỀM** (`classify` = Hamming ?: NCC 0.45). [ĐO]
     * crop 87 khung VietMap qua [CaptureCalibration.VIETMAP_CAMERA_SEED]: khớp mềm ra mã **1** khung
     * (`arrive_straight` → amap 12, đúng phải 9), khớp cứng ra **0**. Cùng lớp lỗi mà B3.53 vá ở tier
     * rect-cố-định — đây là nửa cửa còn lại.
     *
     * Nay tầng-1 đòi [CaptureBounds.target] khớp target của plan; ARROW rơi xuống tầng-2 (rect cố định →
     * `classifyStrict`, đã khoá ở `FixedRectSoftMatchTest`). Waze/GMaps không đổi — xem test kế bên.
     */
    @Test
    fun `routePlans — rect a11y do cho CAMERA KHONG duoc ap cho plan ARROW (B3_53 review)`() {
        val a11yRect = CropRect(100, 100, 220, 200)
        val plans = CaptureRouter.routePlans(
            loc(pkg = "vn.vietmap.live", fullscreen = true), geom,
            a11y = CaptureBounds(a11yRect, capturedAtMs = 1_000L, pkg = "vn.vietmap.live", target = CaptureTarget.CAMERA),
            now = 1_100L, freshMs = 1500L,
        )
        assertEquals(2, plans.size)
        val camera = plans.first { it.target == CaptureTarget.CAMERA }
        assertEquals(BoundsSource.A11Y_DYNAMIC, camera.boundsSource, "target ĐÚNG vẫn phải dùng tầng-1")
        assertEquals(a11yRect, camera.bounds)

        val arrow = plans.first { it.target == CaptureTarget.ARROW }
        assertEquals(
            BoundsSource.FIXED_CALIBRATED, arrow.boundsSource,
            "rect đo cho node CAMERA bị áp cho plan ARROW ⇒ crop sai chỗ đi vào khớp MỀM ⇒ SAI HƯỚNG",
        )
        assertFalse(arrow.bounds == a11yRect, "plan ARROW không được mang rect của node CAMERA")
    }

    /** Đường proven (CLAUDE.md §6): producer khai ARROW ⇒ plan ARROW vẫn dùng tầng-1 y như trước bản vá. */
    @Test
    fun `routePlans — rect a11y do cho ARROW van phuc vu plan ARROW (khong hoi quy Waze)`() {
        val a11yRect = CropRect(100, 100, 220, 200)
        val plans = CaptureRouter.routePlans(
            loc(pkg = "com.waze", fullscreen = true), geom,
            a11y = CaptureBounds(a11yRect, capturedAtMs = 1_000L, pkg = "com.waze", target = CaptureTarget.ARROW),
            now = 1_100L, freshMs = 1500L,
        )
        assertEquals(listOf(CaptureTarget.ARROW), plans.map { it.target })
        assertEquals(BoundsSource.A11Y_DYNAMIC, plans[0].boundsSource)
        assertEquals(a11yRect, plans[0].bounds)
    }

    /** Rect KHÔNG khai mục tiêu = không rõ đo cho cái gì ⇒ bỏ tầng-1 (cùng nguyên tắc an toàn với `pkg` null). */
    @Test
    fun `bounds tang 1 — rect KHONG khai target thi bi bo qua (roi ve rect co dinh)`() {
        val plan = CaptureRouter.route(
            loc(pkg = "com.waze", fullscreen = true), geom,
            a11y = CaptureBounds(CropRect(100, 100, 200, 180), capturedAtMs = 1_000L),
            now = 1_100L, freshMs = 1500L,
        )!!
        assertEquals(BoundsSource.FIXED_CALIBRATED, plan.boundsSource)
        assertEquals(CaptureCalibration.WAZE_ARROW_BANNER_D240, plan.bounds)
    }

    // ── Case-2 offset nửa L/R ──────────────────────────────────────────────────────────

    @Test
    fun `Case 2 offset — nua PHAI dich rect co dinh +W_leftPercent_100`() {
        val plan = CaptureRouter.route(
            loc(pkg = "com.waze", fullscreen = false, slot = CaptureSlotSide.RIGHT, leftPercent = 50), geom,
        )!!
        assertEquals(CaptureCase.HALF_MAIN_SPLIT, plan.case)
        // divider = 1920*50/100 = 960; rect (26,218,208,298) -> (986,218,1168,298), clamp vao [960,1920) giu nguyen.
        assertEquals(CropRect(1038, 50, 1118, 163), plan.bounds)
        assertEquals(BoundsSource.FIXED_CALIBRATED, plan.boundsSource)
    }

    @Test
    fun `Case 2 offset — nua TRAI khong dich, clamp vao 0 toi divider`() {
        val plan = CaptureRouter.route(
            loc(pkg = "com.waze", fullscreen = false, slot = CaptureSlotSide.LEFT, leftPercent = 50), geom,
        )!!
        assertEquals(CropRect(78, 50, 158, 163), plan.bounds)   // trong [0,960) → giữ nguyên
    }

    @Test
    fun `Case 2 clamp — a11y tran sang nua kia bi cat ve nua cua app (phai)`() {
        // RIGHT slot, divider 960; a11y rect (900,200,1100,300) tràn sang nửa trái → clamp về [960,1100).
        val plan = CaptureRouter.route(
            loc(fullscreen = false, slot = CaptureSlotSide.RIGHT, leftPercent = 50), geom,
            a11y = CaptureBounds(CropRect(900, 200, 1100, 300), capturedAtMs = 10L, target = CaptureTarget.ARROW),
            now = 20L,
        )!!
        assertEquals(BoundsSource.A11Y_DYNAMIC, plan.boundsSource)
        assertEquals(CropRect(960, 200, 1100, 300), plan.bounds)
    }

    @Test
    fun `halfOffsetX — chi ap cho nua PHAI`() {
        assertEquals(0, CaptureRouter.halfOffsetX(CaptureCase.HALF_MAIN_SPLIT, loc(slot = CaptureSlotSide.LEFT, leftPercent = 40), geom))
        assertEquals(768, CaptureRouter.halfOffsetX(CaptureCase.HALF_MAIN_SPLIT, loc(slot = CaptureSlotSide.RIGHT, leftPercent = 40), geom))
        assertEquals(0, CaptureRouter.halfOffsetX(CaptureCase.FULL_MAIN, loc(slot = CaptureSlotSide.RIGHT, leftPercent = 40), geom))
    }

    // ── B3.58: chọn màn chụp theo case (FIX 2b — CLUSTER_CAST phải chụp CỤM, không phải màn chính) ──────

    @Test
    fun `captureDisplayForCase — CLUSTER_CAST chup CUM (B3_58 FIX 2b)`() {
        assertEquals(CaptureDisplayTarget.CLUSTER, captureDisplayForCase(CaptureCase.CLUSTER_CAST))
    }

    @Test
    fun `captureDisplayForCase — man chinh cho FULL_MAIN va HALF_MAIN_SPLIT`() {
        assertEquals(CaptureDisplayTarget.MAIN, captureDisplayForCase(CaptureCase.FULL_MAIN))
        assertEquals(CaptureDisplayTarget.MAIN, captureDisplayForCase(CaptureCase.HALF_MAIN_SPLIT))
    }

    @Test
    fun `captureDisplayForCase — NOT_ACTIVE di offscreen (khong fission)`() {
        assertEquals(CaptureDisplayTarget.OFFSCREEN, captureDisplayForCase(CaptureCase.NOT_ACTIVE))
    }

    /**
     * BOUNDARY (FIX 2b): app dẫn cast lên cụm (displayId = clusterDisplayId) → case CLUSTER_CAST → chụp CỤM.
     * Trace nguyên chuỗi thuần: loc.displayId=1 → selectCase → CLUSTER_CAST → captureDisplayForCase → CLUSTER.
     */
    @Test
    fun `trace — app tren display cum di het chuoi ra CLUSTER`() {
        val case = CaptureRouter.selectCase(loc(pkg = "vn.vietmap.live", displayId = 1), geom)
        assertEquals(CaptureCase.CLUSTER_CAST, case)
        assertEquals(CaptureDisplayTarget.CLUSTER, captureDisplayForCase(case))
    }

    // ── crop → PixelFrame → classify (tái dùng ManeuverSignature, KHÔNG viết lại) ──────

    /**
     * Xác nhận CHUỖI THUẦN: full frame → route bounds (a11y trỏ đúng glyph) → [PixelFrameOps.crop] →
     * [ManeuverSignature.classify]. Nhúng glyph 15×15 của "turn_normal_left" vào frame lớn, a11y bounds trỏ
     * đúng ô đó → classify phải ra 2 (rẽ trái AMAP), y như [ManeuverSignatureTest].
     */
    @Test
    fun `crop-classify — arrow Waze qua router-crop van ra dung ma (reuse ManeuverSignature)`() {
        val bits = ManeuverRegistry.RAW.first { it.second == "maneuver_turn_normal_left" }.first
        val g = 15
        val ox = 26
        val oy = 218
        val screenW = 400
        val screenH = 480
        val screen: PixelFrame = ArrayPixelFrame(screenW, screenH, IntArray(screenW * screenH) { idx ->
            val x = idx % screenW
            val y = idx / screenW
            val cx = x - ox
            val cy = y - oy
            if (cx in 0 until g && cy in 0 until g && bits[cy * g + cx] == '1') 0xFFFFFFFF.toInt() else 0x00000000
        })
        val plan = CaptureRouter.route(
            loc(pkg = "com.waze", fullscreen = true),
            DisplayGeometry(screenW, screenH),
            a11y = CaptureBounds(CropRect(ox, oy, ox + g, oy + g), capturedAtMs = 5L, target = CaptureTarget.ARROW),
            now = 10L,
        )!!
        assertEquals(BoundsSource.A11Y_DYNAMIC, plan.boundsSource)
        val crop = PixelFrameOps.crop(screen, plan.bounds)
        assertNotNull(crop)
        assertEquals(g, crop!!.width)
        assertEquals(g, crop.height)
        assertEquals(2, ManeuverSignature.classify(crop))   // 2 = rẽ trái (khớp ManeuverSignatureTest)
    }

    @Test
    fun `crop — rect ngoai khung tra null`() {
        val f: PixelFrame = ArrayPixelFrame(10, 10, IntArray(100))
        assertNull(PixelFrameOps.crop(f, CropRect(20, 20, 30, 30)))
    }

    // ── CropRect helpers ────────────────────────────────────────────────────────────

    @Test
    fun `CropRect — clampTo giao rong tra isEmpty`() {
        assertTrue(CropRect(0, 0, 10, 10).clampTo(CropRect(50, 50, 60, 60)).isEmpty())
        assertFalse(CropRect(0, 0, 10, 10).clampTo(CropRect(5, 5, 60, 60)).isEmpty())
    }
}
