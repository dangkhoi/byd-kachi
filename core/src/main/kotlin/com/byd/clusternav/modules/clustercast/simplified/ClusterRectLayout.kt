package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ B1b · CLUSTER-RECT-OPTION — HÌNH HỌC của cụm Chữ nhật (theme2 FULL, Seal 10.25") — thuần, px CỤM ═══════════════════
 *
 * Mọi số ở đây là px của màn ảo cụm 1920×720 ở cấu hình [DisplayConfig.NORMAL_DEFAULT] (`wm size 1920x720`, overscan 0 ⇒
 * toạ độ cửa sổ = toạ độ cụm 1:1). Chữ nhật chỉ được chọn trên Seal `car.type=138` ([ProjectionRecipe.offers]) — đúng cụm mà
 * các số dưới đây được đo.
 *
 * Nguồn (bảng F6–F9 của nghiên cứu 05/10, `cluster-rect-seal-2026-10-05.md` §2):
 *  • [ĐO QML fw 2602030] theme2 FULL hiện nền ADAS LỚN khi `adasInterfaceDisplay !== 0` (`cluster.qml:47-48`) ⇒ ở Chữ nhật
 *    khung trắng bên phải LUÔN hiện, kể cả khi ADAS đang nhỏ; phím menu chỉ đổi ADAS sang nhỏ, khung vẫn còn
 *    ([ĐO owner trên xe 05/10] "bấm nãy giờ chưa được"). Không có đòn bẩy phía app (12/13, 32/33, 47/48 vô tác dụng; 41 ghi
 *    bền + CAN ⇒ cấm; phím 309 cúp cuộc gọi BT) [ĐO].
 *  • [ĐO QML + PNG] phần đục ADAS [ADAS_PANEL]; chữ ADAS bật lên [ADAS_POPUP_TEXT]; ô ADAS nhỏ [ADAS_SMALL]; chữ "m/h" lạc
 *    [STRAY_MPH] (nằm trên nền đục z ≥ 0 ⇒ không che được). Vùng trống còn lại: [FREE_AREA].
 */
object ClusterRectLayout {

    /** Kích cụm mà các số đo dưới đây thuộc về. */
    const val WIDTH: Int = 1920
    const val HEIGHT: Int = 720

    /** Vùng KHÔNG bị nền ADAS lớn che — khung app mặc định ở Chữ nhật (D2, owner chốt 05/10). */
    val FREE_AREA: CastBounds = CastBounds(50, 128, 1285, 555)

    /** Phần đục của nền ADAS lớn (luôn hiện ở theme2 FULL). */
    val ADAS_PANEL: CastBounds = CastBounds(1285, 243, 1885, 593)

    /** Dải chữ ADAS bật lên (x 551..1369 × y 528..570). */
    val ADAS_POPUP_TEXT: CastBounds = CastBounds(551, 528, 1369, 570)

    /** Ô ADAS nhỏ. */
    val ADAS_SMALL: CastBounds = CastBounds(1324, 447, 1513, 567)

    /** Chữ "m/h" lạc của theme2 FULL — lộ ra khi cụm mất số km/h gốc (F9). */
    val STRAY_MPH: CastBounds = CastBounds(0, 0, 82, 37)

    /**
     * Hộp số km/h do Kachi vẽ (B.7): 240×120 ở góc dưới trái vùng trống. Cỡ là [ĐOÁN thẩm mỹ] so với số gốc theme1 cao ~66 px
     * (F6) — chỉnh trên xe. Bài `SpeedReadoutPolicyTest` khoá: nằm trong [FREE_AREA], không giao bốn vùng còn lại.
     */
    val SPEED_BOX: CastBounds = CastBounds(70, 415, 310, 535)

    /**
     * Khung mặc định của MỘT nửa ở Chữ nhật: chia [FREE_AREA] theo [leftPercent] (cùng phép `fitToCluster`/`applySplitRatioLive`
     * dùng trên cả cụm, chỉ đổi dải). [leftPercent] ngoài [CastProfile.SPLIT_PERCENTS] ⇒ chuẩn hoá như mọi chỗ khác.
     */
    fun slotFrame(side: ClusterSlotSide, leftPercent: Int): CastBounds {
        val a = FREE_AREA
        val boundary = a.left + a.width * CastProfile.normalizePercent(leftPercent) / 100
        return when (side) {
            ClusterSlotSide.LEFT -> CastBounds(a.left, a.top, boundary, a.bottom)
            ClusterSlotSide.RIGHT -> CastBounds(boundary, a.top, a.right, a.bottom)
        }
    }

    /**
     * Hai nửa khi đổi tỉ lệ sống (`applySplitRatioLiveBody`). Bo tròn = ĐÚNG phép cũ từng số (`0..W × 0..H`, CLAUDE.md §6);
     * Chữ nhật = chia [FREE_AREA].
     */
    fun splitFrames(style: CastStyle, leftPercent: Int, width: Int, height: Int): Pair<CastBounds, CastBounds> =
        when (style) {
            CastStyle.CURVED -> {
                val boundary = width * leftPercent / 100
                CastBounds(0, 0, boundary, height) to CastBounds(boundary, 0, width, height)
            }
            CastStyle.RECT -> slotFrame(ClusterSlotSide.LEFT, leftPercent) to slotFrame(ClusterSlotSide.RIGHT, leftPercent)
        }

    /**
     * Review 2.89 Pass 3 · cluster-r2-1 — KHUNG GỐC của bộ chỉnh −/+ / "Đặt lại" ở Cài đặt › Chiếu cụm cho một ô ([side] `null` =
     * toàn cụm): dải X để kẹp, dải Y để kẹp, và khung "Đặt lại" / mặc định khi chưa ghim. Chữ nhật = đúng khung mà phiên GHIM
     * ([FREE_AREA] · [slotFrame]) — kẹp theo cả cụm thì hai nửa chồng nhau (dải mất tác dụng) và "Đặt lại" đặt app dưới nền ADAS
     * rồi lưu vào khoá `__RECT` cho mọi phiên sau [ĐO mã]. Bo tròn = ĐÚNG phép cũ của `ClusterNavBridgeGeometry` từng số (tỉ lệ
     * thô, không chuẩn hoá; `0..H`) — CLAUDE.md §6.
     */
    fun editFrame(style: CastStyle, side: ClusterSlotSide?, leftPercent: Int, width: Int, height: Int): CastBounds = when (style) {
        CastStyle.RECT -> side?.let { slotFrame(it, leftPercent) } ?: FREE_AREA
        CastStyle.CURVED -> {
            val split = (width * leftPercent / 100).coerceIn(1, (width - 1).coerceAtLeast(1))
            when (side) {
                null -> CastBounds(0, 0, width, height)
                ClusterSlotSide.LEFT -> CastBounds(0, 0, split, height)
                ClusterSlotSide.RIGHT -> CastBounds(split, 0, width, height)
            }
        }
    }

    /**
     * Bản ghim cho một ô ở Chữ nhật: bản đã lưu (khoá `__RECT`) thắng; bản lưu THIẾU khung (vd chỉ đổi DPI — `CastDensityControl`
     * lưu không khung) hoặc chưa lưu gì ⇒ khung mặc định [frame]. Chưa lưu gì ⇒ DPI `reset` (DPI do lượt mở đặt; repin không chạm
     * DPI — cùng lẽ `slotBase` của `CastSessionPin.kt`).
     */
    fun pin(saved: DisplayConfig?, frame: CastBounds): DisplayConfig = when {
        saved == null -> DisplayConfig.NORMAL_DEFAULT.copy(density = CastGeometryGuard.DENSITY_RESET, bounds = frame)
        saved.bounds == null -> oneToOne(saved).copy(bounds = frame)
        else -> oneToOne(saved)
    }

    /**
     * Review 2.89 Pass 2 · cluster-r1-2/r1-3 — Chữ nhật cần toạ độ cụm 1:1 (khung [FREE_AREA] + hộp km/h đo bằng px cụm): `wm size`
     * / overscan LUÔN là của [DisplayConfig.NORMAL_DEFAULT], bất kể bản ghi nói gì (DPI + khung giữ nguyên). Một bản `__RECT` bị
     * đường lùi `wm size` cũ làm bẩn, hay bản Bo tròn (khoá cũ) có `wmSize` riêng, không còn đi được vào cụm Chữ nhật — nếu đi
     * được thì khung trượt dưới nền ADAS và `SpeedReadoutPolicy.oneToOne` tắt số km/h cả phiên (cụm Chữ nhật không có km/h gốc).
     */
    fun oneToOne(c: DisplayConfig): DisplayConfig =
        c.copy(wmSize = DisplayConfig.NORMAL_DEFAULT.wmSize, overscan = DisplayConfig.NORMAL_DEFAULT.overscan)

    /** Hai hình chữ nhật (nửa mở `[l, r) × [t, b)`) có phần chung không. */
    fun intersects(a: CastBounds, b: CastBounds): Boolean =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    /** [inner] nằm trọn trong [outer]. */
    fun contains(outer: CastBounds, inner: CastBounds): Boolean =
        inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom
}

/**
 * ═══ B1b — KIỂU CỤM của MỘT phiên chiếu, ghim MỘT lần lúc mở ([ProjectionManager.open]), xoá lúc đóng ════════════════════
 *
 * Mẫu VC-R6 (`CastSessionPin.kt`): lựa chọn của hồ sơ (`cast_style`) đọc MỘT lần đầu lượt mở; đổi lựa chọn giữa phiên chỉ đổi
 * prefs — 0 lệnh AutoContainer, khung/khoá lưu/km/h vẫn theo phiên. Kiểu mới áp ở lượt mở kế (D4: khi CHƯA có màn ảo cụm —
 * lần nổ máy sau, hoặc sau khi tắt chiếu nếu màn ảo mất).
 *
 * @param desired kiểu người lái chọn lúc mở.
 * @param believed kiểu Kachi tin cụm đang hiện sau lượt mở ([ClusterStylePlan.Plan.believed]).
 * @param frame kiểu dùng cho KHUNG app, khoá lưu (`__RECT`) và km/h: kiểu tin nếu biết (cụm là sự thật, không phải lựa chọn
 *   — vd chọn Chữ nhật mà cổng bỏ 31 vì màn ảo cụm còn ⇒ cụm vẫn Bo tròn ⇒ khung Bo tròn); [BelievedStyle.UNKNOWN] ⇒ kiểu áp
 *   được của lựa chọn ([ClusterStylePlan.effective]); đời xe không có opcode kiểu ⇒ Bo tròn.
 */
data class CastSessionStyle(val desired: CastStyle, val believed: BelievedStyle, val frame: CastStyle) {
    companion object {
        fun of(recipe: ProjectionRecipe, desired: CastStyle, plan: ClusterStylePlan.Plan?): CastSessionStyle {
            val believed = plan?.believed ?: BelievedStyle.UNKNOWN
            val frame = when (believed) {
                BelievedStyle.CURVED -> CastStyle.CURVED
                BelievedStyle.RECT -> CastStyle.RECT
                BelievedStyle.UNKNOWN -> ClusterStylePlan.effective(recipe, desired) ?: CastStyle.CURVED
            }
            return CastSessionStyle(desired, believed, frame)
        }
    }
}

/**
 * ═══ B1b — nút "Áp ngay" của lựa chọn Bo tròn / Chữ nhật (R5): luật THUẦN ═════════════════════════════════════════════════
 *
 * "Áp ngay" = `restoreCluster` (dừng → đóng chiếu → mở lại sau 2 s) — lượt mở đi qua CỔNG theme như mọi lượt mở khác: màn ảo
 * cụm còn ⇒ `VD_PRESENT`, 0 lệnh theme, cụm giữ kiểu cũ (D4). Theme KHÔNG BAO GIỜ gửi từ màn Cài đặt hay khi đang chiếu.
 */
object CastStyleApply {

    /**
     * Review 2.89 Pass 3 · cluster-r2-5 — kiểu đưa vào lượt mở chiếu: Chữ nhật (31 ⇒ cụm MẤT km/h gốc) CHỈ khi Kachi vẽ được số km/h
     * của chính nó ([canDrawReadout] = quyền vẽ trên ứng dụng khác — lớp km/h là `TYPE_APPLICATION_OVERLAY`, dựng ở dịch vụ nút nổi
     * SAU chốt quyền). Không vẽ được ⇒ Bo tròn (D3 — cụm còn km/h gốc). D1 chỉ chấp nhận mất km/h khi Kachi CHẾT, không phải khi
     * lớp số không vẽ được [ĐO mã].
     */
    fun withReadout(chosen: CastStyle, canDrawReadout: Boolean): CastStyle =
        if (chosen == CastStyle.RECT && !canDrawReadout) CastStyle.CURVED else chosen

    /** Không có app nào đang chiếu — chỉ Idle / Off (CLAUDE.md §5: kiểm lại NGAY lúc chạy, không tin trạng thái lúc vẽ nút). */
    fun stateAllows(state: SimpleCastState): Boolean = state == SimpleCastState.Idle || state == SimpleCastState.Off

    /**
     * Có hiện nút không: Cast đang bật, trạng thái cho phép, và lựa chọn chưa phải kiểu đang hiện (không có phiên, kiểu khung
     * của phiên khác lựa chọn, hoặc cụm chưa rõ kiểu).
     */
    fun offer(chosen: CastStyle, session: CastSessionStyle?, state: SimpleCastState, castEnabled: Boolean): Boolean =
        castEnabled && stateAllows(state) &&
            (session == null || session.frame != chosen || session.believed == BelievedStyle.UNKNOWN)
}
