package com.byd.clusternav.navigation.screencapture

/**
 * Bảng rect CỐ ĐỊNH đã hiệu chỉnh (tầng 2 của §4.4) — keyed theo (app target, geometry). Seed từ giá trị
 * OpenBYD proven; giá trị THẬT phải hiệu chỉnh trên xe (OQ2). Toạ độ trong không gian display FULL (origin
 * góc trên-trái); router tự offset cho Case-2 nửa phải.
 *
 * ⚠ Đây là HẰNG SỐ HIỆU CHỈNH, không phải chân lý: OpenBYD's `getArrowBounds()=Rect(26,218,208,298)` là cho
 * geometry của HỌ. Ta giữ làm seed để tinh chỉnh; khi có bảng theo (app,WxH) thật thì thêm vào [TABLE].
 */
object CaptureCalibration {

    /**
     * Rect mũi tên Waze của OpenBYD (`WazeArrowCaptureService.getArrowBounds`) — seed gốc.
     * Left=26, Top=218, Right=208, Bottom=298 (182×80) trong không gian màn của HỌ. GIỮ để tham chiếu.
     */
    val WAZE_ARROW_OPENBYD = CropRect(26, 218, 208, 298)

    /**
     * ĐO THẬT trên emulator WazeMod @960×720 (2026-08-20, Waze đang dẫn): **CROP SÁT glyph mũi tên**
     * (không lấy cả banner) để mũi tên LẤP ĐẦY lưới 15×15 của ManeuverSignature → chữ ký dày, khớp được
     * (crop cả banner 182×80 làm mũi tên nét-mảnh biến mất khi hạ mẫu → chữ ký ~toàn 0). Glyph ở top-left
     * banner, ~82×72. ⚠ Xác nhận màn chính XE khớp layout WazeMod 960×720 này; khác → thêm entry (app,WxH).
     */
    val WAZE_ARROW_WAZEMOD_960x720 = CropRect(38, 38, 120, 110)

    /**
     * **Khung vẽ mũi tên của banner Waze** — đo THẬT trên emulator ở density 240, hai lần, HAI kích thước màn:
     * cụm **1920×720** và màn chính **1920×1080** (2026-08-21/22, WazeMod đang dẫn tới Cửa Nam). CẢ HAI ra
     * CÙNG một rect và cùng khớp `maneuver_turn_normal_right` (Hamming 18 ≤ 18) ⇒ banner Waze neo theo **dp
     * (density)**, KHÔNG theo chiều cao màn — nên một hằng số phục vụ cả hai geometry.
     *
     * Đây là **KHUNG VẼ**, KHÔNG phải bbox mực tight: đo cả 38 mục [ManeuverRegistry] thì mực glyph luôn kết
     * thúc ở hàng 13/15 (`row1=13` cho 38/38) ⇒ template có LỀ. Crop tight (B3.9) làm glyph lấp đầy 15×15 ⇒
     * Hamming 44 > 18 ⇒ classify null. Nguồn khung ở OpenBYD = a11y `<wazePkg>:id/navBarDirection`
     * (`BydAccessibilityService.java:257`) — bản WazeMod dựng Compose không phơi view-id đó nên ta đi rect.
     *
     * ⚠ MỨC BẰNG CHỨNG (§2): "nhiều khả năng". Đúng trên HAI geometry nhưng vẫn CÙNG một maneuver (rẽ phải)
     * và Hamming 18 = **sát trần**. Cần thêm maneuver khác (trái/thẳng/quay-đầu) mới lên "đã chứng minh".
     * Density khác 240 CHƯA đo (rect 960×720 ở trên đo lúc density 160 và theo quy ước tight cũ ⇒ không so được).
     */
    val WAZE_ARROW_BANNER_D240 = CropRect(78, 50, 158, 163)

    /**
     * Seed icon camera VietMap — CHƯA có template/rect thật (OQ4). Đặt tạm ở góc trên-phải vùng chỉ đường
     * (nơi VietMap hay vẽ cảnh báo). Giá trị phải hiệu chỉnh trên xe; ở đây chỉ để pipeline có bounds hợp lệ.
     */
    val VIETMAP_CAMERA_SEED = CropRect(0, 120, 160, 280)

    /** Khoá bảng: target + (tuỳ chọn) geometry cụ thể. null geometry = mặc định cho mọi WxH của target đó. */
    private data class Key(val target: CaptureTarget, val displayW: Int?, val displayH: Int?)

    private val TABLE: Map<Key, CropRect> = mapOf(
        // Geometry-specific: đo thật trên WazeMod @960×720 (banner ở top-left). geom = kích thước bitmap chụp.
        Key(CaptureTarget.ARROW, 960, 720) to WAZE_ARROW_WAZEMOD_960x720,
        // CỤM trên xe = 1920×720 (fission). Không có entry này thì rơi về seed OpenBYD (182×80) → chữ ký ~4 bit
        // → dưới MIN_SIG_BITS → kênh mũi tên CÂM trên xe (đo 2026-08-21).
        Key(CaptureTarget.ARROW, 1920, 720) to WAZE_ARROW_BANNER_D240,
        // Màn CHÍNH xe = 1920×1080. Đo thật 08-22: CÙNG rect với cụm (banner Waze neo theo dp) → CASE 1/2
        // hết câm. Trước đó rơi seed OpenBYD 182×80 ⇒ chữ ký ~4 bit ⇒ null.
        Key(CaptureTarget.ARROW, 1920, 1080) to WAZE_ARROW_BANNER_D240,
        // Default (geometry khác): seed OpenBYD — vẫn cần calibrate trên xe.
        Key(CaptureTarget.ARROW, null, null) to WAZE_ARROW_OPENBYD,
        Key(CaptureTarget.CAMERA, null, null) to VIETMAP_CAMERA_SEED,
    )

    /**
     * Rect cố định cho ([target], [geom]) — ưu tiên khớp geometry cụ thể, rồi mới mặc định target. null nếu
     * không có seed nào (target lạ) → caller coi như không có tầng 2.
     */
    fun fixedBounds(target: CaptureTarget, geom: DisplayGeometry): CropRect? =
        TABLE[Key(target, geom.displayW, geom.displayH)]
            ?: TABLE[Key(target, null, null)]
}

/**
 * Trọng tài THUẦN cho nguồn screen-capture (T1 spec §4.3/§4.4). Hai việc:
 *   (a) [selectCase] — app dẫn đang ở 1 trong 4 [CaptureCase] (dựa trên [AppLocation] + [DisplayGeometry]).
 *   (b) [computeBounds] — vùng crop theo 3 tầng ưu tiên: a11y-động (nếu tươi) → cố-định-hiệu-chỉnh →
 *       (lane-seg: HOÃN, chưa vòng này).
 *
 * KHÔNG Android, KHÔNG I/O, KHÔNG thời gian ngầm — mọi thứ là hàm của tham số (kể cả [now]) nên unit-test
 * off-car khoá được toàn bộ quyết định (V-unit). Lớp :app chỉ lo capture transport + a11y read.
 */
object CaptureRouter {

    /**
     * Bounds a11y coi là "tươi" trong bao lâu (tầng 1). Nhịp capture ~2–4 Hz nên bound cũ hơn ~1.5 s là lỗi
     * thời — nhường tầng cố định. Tham số hoá ở [route] để tune; đây chỉ là mặc định.
     */
    const val A11Y_FRESH_MS = 1500L

    /**
     * Định tuyến 1 frame → MỘT [CapturePlan] cho target ĐƠN "chính" ([CaptureTarget.forPackage]). Trả:
     *   - null  ⇒ GATE ĐÓNG (navFresh=false) — KHÔNG capture (V-gate). Đây là cửa duy nhất trả null.
     *   - [CapturePlan] ⇒ case + target + crop bounds + tầng bounds. bounds.isEmpty() ⇒ caller bỏ frame.
     *
     * BACK-COMPAT: giữ nguyên cho caller đơn-target (test resolver). Đường ĐA-target (B3.8, VietMap = mũi tên +
     * camera) dùng [routePlans].
     *
     * @param loc       app dẫn ở đâu + trạng thái.
     * @param geom      kích thước + phân loại display app đang ở.
     * @param a11y      bounds động do a11y publish (null = không có).
     * @param now       đồng hồ đơn điệu (ms) để chấm tươi a11y. Mặc định 0 (khi caller không có a11y).
     * @param freshMs   ngưỡng tươi a11y (mặc định [A11Y_FRESH_MS]).
     */
    fun route(
        loc: AppLocation,
        geom: DisplayGeometry,
        a11y: CaptureBounds? = null,
        now: Long = 0L,
        freshMs: Long = A11Y_FRESH_MS,
    ): CapturePlan? {
        if (!loc.navFresh) return null                       // gate đóng → không capture
        val case = selectCase(loc, geom)
        return planFor(case, loc, geom, CaptureTarget.forPackage(loc.pkg), a11y, now, freshMs)
    }

    /**
     * Định tuyến 1 frame → MỘT [CapturePlan] cho MỖI target cần thử ([CaptureTarget.targetsForPackage]) — B3.8.
     * VietMap → 2 plan (ARROW + CAMERA, mỗi cái bounds RIÊNG: ARROW = arrow rect hiệu chỉnh; CAMERA = seed/a11y);
     * Waze/WazeMod/GMaps → 1 plan (ARROW) — tương đương hành vi [route] cũ. Trả:
     *   - RỖNG   ⇒ GATE ĐÓNG (navFresh=false) — KHÔNG capture (V-gate). Đây là cửa duy nhất trả rỗng.
     *   - danh sách [CapturePlan] (1 mỗi target). plan.bounds.isEmpty() ⇒ caller bỏ frame CHO TARGET ĐÓ.
     *
     * Cùng tham số như [route]. Caller (`ScreenCaptureNavSource.tick`) chụp display MỘT lần rồi crop+classify
     * theo từng plan, mỗi target bọc runCatching riêng (degrade-safe: một target lỗi không rớt target kia).
     */
    fun routePlans(
        loc: AppLocation,
        geom: DisplayGeometry,
        a11y: CaptureBounds? = null,
        now: Long = 0L,
        freshMs: Long = A11Y_FRESH_MS,
    ): List<CapturePlan> {
        if (!loc.navFresh) return emptyList()                // gate đóng → không capture
        val case = selectCase(loc, geom)
        return CaptureTarget.targetsForPackage(loc.pkg).map { target ->
            planFor(case, loc, geom, target, a11y, now, freshMs)
        }
    }

    /**
     * Dựng [CapturePlan] cho MỘT [target] cụ thể (case đã chọn). Tách riêng để [route] (đơn) và [routePlans]
     * (đa) dùng CHUNG logic tính bounds — DRY, cùng 3 tầng của [computeBounds] (§4.4).
     */
    fun planFor(
        case: CaptureCase,
        loc: AppLocation,
        geom: DisplayGeometry,
        target: CaptureTarget,
        a11y: CaptureBounds?,
        now: Long,
        freshMs: Long,
    ): CapturePlan {
        val (bounds, src) = computeBounds(case, loc, geom, target, a11y, now, freshMs)
        return CapturePlan(case, target, bounds, src)
    }

    /**
     * (a) Chọn case theo §4.3:
     *   - nav tươi nhưng KHÔNG foreground ở đâu → [CaptureCase.NOT_ACTIVE] (Case 4).
     *   - foreground trên display CỤM → [CaptureCase.CLUSTER_CAST] (Case 3).
     *   - foreground trên màn chính, fullscreen → [CaptureCase.FULL_MAIN] (Case 1); split → [HALF_MAIN_SPLIT] (Case 2).
     *   - foreground trên display khác (không chính, không cụm) → coi như Case 3 cast (chụp display đó).
     *
     * (Giả định gate đã mở — caller vào đây sau khi navFresh=true.)
     */
    fun selectCase(loc: AppLocation, geom: DisplayGeometry): CaptureCase {
        if (!loc.foreground) return CaptureCase.NOT_ACTIVE
        return when {
            loc.displayId == geom.clusterDisplayId -> CaptureCase.CLUSTER_CAST
            loc.displayId == geom.mainDisplayId ->
                if (loc.isFullscreen) CaptureCase.FULL_MAIN else CaptureCase.HALF_MAIN_SPLIT
            // Foreground trên một display phụ khác cụm chính: chụp thẳng display đó như một biến thể cast.
            else -> CaptureCase.CLUSTER_CAST
        }
    }

    /**
     * (b) 3 tầng bounds (§4.4). Trả (rect, tầng):
     *   1. a11y động nếu có & còn tươi (now - capturedAt ≤ freshMs) **& đo ĐÚNG CHO [target] này** → clamp
     *      vào NỬA app (Case 2/3 split) để loại nhiễu app kia. Toạ độ a11y đã tuyệt đối nên tự đúng, chỉ
     *      cần clamp. Về điều kiện "đúng target" xem KDoc [CaptureBounds.target] — rect đo cho node CAMERA
     *      từng được áp nguyên xi cho plan ARROW của cùng app (lỗi có thật, [ĐO] 08-23).
     *   2. rect cố định hiệu chỉnh theo (target, geom); Case-2 nửa PHẢI thì offset +W*leftPercent/100.
     *   3. lane-seg: HOÃN (chưa vòng này) → nếu cả 1&2 trượt, trả EMPTY + [BoundsSource.NONE].
     */
    fun computeBounds(
        case: CaptureCase,
        loc: AppLocation,
        geom: DisplayGeometry,
        target: CaptureTarget,
        a11y: CaptureBounds?,
        now: Long,
        freshMs: Long,
    ): Pair<CropRect, BoundsSource> {
        // Tầng 1 — a11y động, còn tươi, VÀ đo đúng cho target này (xem KDoc CaptureBounds.target: rect của
        // node CAMERA từng được áp cho plan ARROW ⇒ crop sai chỗ đi vào khớp MỀM ⇒ có thể ra SAI HƯỚNG).
        if (a11y != null && a11y.target == target && !a11y.rect.isEmpty() && now - a11y.capturedAtMs <= freshMs) {
            val clamped = a11y.rect.clampTo(appRegion(case, loc, geom))
            if (!clamped.isEmpty()) return clamped to BoundsSource.A11Y_DYNAMIC
        }
        // Tầng 2 — cố định hiệu chỉnh.
        val fixed = CaptureCalibration.fixedBounds(target, geom)
        if (fixed != null && !fixed.isEmpty()) {
            val offset = halfOffsetX(case, loc, geom)
            val shifted = if (offset != 0) fixed.offsetX(offset) else fixed
            val clamped = shifted.clampTo(appRegion(case, loc, geom))
            if (!clamped.isEmpty()) return clamped to BoundsSource.FIXED_CALIBRATED
        }
        // Tầng 3 — lane-seg HOÃN.
        return CropRect.EMPTY to BoundsSource.NONE
    }

    /**
     * Offset ngang cho Case-2 (split màn chính) nửa PHẢI = displayW * leftPercent / 100 (mép trái nửa phải).
     * Nửa trái / không split / case khác → 0. (Case 3 cast split cũng dùng cùng công thức nếu chia đôi cụm.)
     */
    fun halfOffsetX(case: CaptureCase, loc: AppLocation, geom: DisplayGeometry): Int =
        if ((case == CaptureCase.HALF_MAIN_SPLIT || case == CaptureCase.CLUSTER_CAST) &&
            loc.slotSide == CaptureSlotSide.RIGHT
        ) {
            geom.displayW * loc.leftPercent / 100
        } else {
            0
        }

    /**
     * Vùng của app dẫn trong không gian display (để clamp, loại nhiễu app kia khi split):
     *   - split LEFT  → [0, W*lp/100)
     *   - split RIGHT → [W*lp/100, W)
     *   - còn lại     → toàn display.
     * (Nghĩa leftPercent khớp `AppMover.fitToCluster`.)
     */
    fun appRegion(case: CaptureCase, loc: AppLocation, geom: DisplayGeometry): CropRect {
        val split = case == CaptureCase.HALF_MAIN_SPLIT || case == CaptureCase.CLUSTER_CAST
        val side = loc.slotSide
        if (!split || side == null) return geom.fullRect
        val divider = geom.displayW * loc.leftPercent / 100
        return when (side) {
            CaptureSlotSide.LEFT -> CropRect(0, 0, divider, geom.displayH)
            CaptureSlotSide.RIGHT -> CropRect(divider, 0, geom.displayW, geom.displayH)
        }
    }
}
