package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ B1b · CLUSTER-RECT-OPTION — HÌNH HỌC của cụm Chữ nhật (theme2 FULL, Seal 10.25") — thuần, px CỤM ═══════════════════
 *
 * 2.90 · R4 — KHÔNG CHỪA GÌ. Owner 06/10: *"bị giới hạn cả cao thấp trái phải, kỳ lắm, mở bung ra cho người ta tự set size"* rồi
 * *"cứ để full resolution nhé, nó over thì user họ tự chỉnh được vị trí, kích thước, DPI mà, nên mình không cần tính gì, chừa gì
 * đâu"*. Bản 2.89 tính vùng trống `FREE_AREA` (50,128,1285,555) né nền ADAS / chữ "m/h" lạc / hộp km/h và kẹp mọi khung vào đó —
 * đã GỠ cùng mọi hằng vùng chừa. Khung mặc định = trọn cụm [WIDTH]×[HEIGHT] (một nửa = nửa của trọn cụm theo tỉ lệ), bộ chỉnh
 * −/+ kẹp vào 0..W × 0..H; người lái tự chỉnh vị trí/kích thước/DPI. Bo tròn và Chữ nhật nay cùng một hình học — khác nhau ở
 * khoá lưu (`__RECT`) và cấu hình 1:1 ([oneToOne]).
 */
object ClusterRectLayout {

    /** Kích cụm (px) — khung mặc định của Chữ nhật. */
    const val WIDTH: Int = 1920
    const val HEIGHT: Int = 720

    /** Khung trọn cụm — mặc định của Chữ nhật (bản lưu chưa có khung). */
    val FULL: CastBounds = CastBounds(0, 0, WIDTH, HEIGHT)

    /**
     * Khung mặc định của MỘT nửa: chia trọn cụm [width]×[height] theo [leftPercent] (cùng phép `fitToCluster`/
     * `applySplitRatioLive`). [leftPercent] ngoài [CastProfile.SPLIT_PERCENTS] ⇒ chuẩn hoá như mọi chỗ khác.
     */
    fun slotFrame(side: ClusterSlotSide, leftPercent: Int, width: Int = WIDTH, height: Int = HEIGHT): CastBounds {
        val boundary = width * CastProfile.normalizePercent(leftPercent) / 100
        return when (side) {
            ClusterSlotSide.LEFT -> CastBounds(0, 0, boundary, height)
            ClusterSlotSide.RIGHT -> CastBounds(boundary, 0, width, height)
        }
    }

    /**
     * Hai nửa khi đổi tỉ lệ sống (`applySplitRatioLiveBody`) — ĐÚNG phép cũ của Bo tròn từng số (`0..W × 0..H`, CLAUDE.md §6)
     * cho mọi kiểu (2.90 · R4: Chữ nhật không còn vùng chừa). [style] giữ cho chữ ký ổn định.
     */
    @Suppress("UNUSED_PARAMETER")
    fun splitFrames(style: CastStyle, leftPercent: Int, width: Int, height: Int): Pair<CastBounds, CastBounds> {
        val boundary = width * leftPercent / 100
        return CastBounds(0, 0, boundary, height) to CastBounds(boundary, 0, width, height)
    }

    /**
     * KHUNG GỐC của bộ chỉnh −/+ / "Đặt lại" ở Cài đặt › Chiếu cụm cho một ô ([side] `null` = toàn cụm): dải X, dải Y để kẹp và
     * khung "Đặt lại". 2.90 · R4: mọi kiểu = ĐÚNG phép cũ của `ClusterNavBridgeGeometry` (tỉ lệ thô, không chuẩn hoá; `0..H`).
     */
    @Suppress("UNUSED_PARAMETER")
    fun editFrame(style: CastStyle, side: ClusterSlotSide?, leftPercent: Int, width: Int, height: Int): CastBounds {
        val split = (width * leftPercent / 100).coerceIn(1, (width - 1).coerceAtLeast(1))
        return when (side) {
            null -> CastBounds(0, 0, width, height)
            ClusterSlotSide.LEFT -> CastBounds(0, 0, split, height)
            ClusterSlotSide.RIGHT -> CastBounds(split, 0, width, height)
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
     * Review 2.89 Pass 2 · cluster-r1-2/r1-3 — Chữ nhật dùng toạ độ cụm 1:1: `wm size` / overscan LUÔN là của
     * [DisplayConfig.NORMAL_DEFAULT], bất kể bản ghi nói gì (DPI + khung giữ nguyên) — khung người lái chỉnh bằng −/+ là px cụm
     * thật. Một bản `__RECT` bị đường lùi `wm size` cũ làm bẩn, hay bản Bo tròn có `wmSize` riêng, không đi được vào cụm Chữ nhật.
     */
    fun oneToOne(c: DisplayConfig): DisplayConfig =
        c.copy(wmSize = DisplayConfig.NORMAL_DEFAULT.wmSize, overscan = DisplayConfig.NORMAL_DEFAULT.overscan)
}

/**
 * ═══ B1b — KIỂU CỤM của MỘT phiên chiếu, ghim MỘT lần lúc mở ([ProjectionManager.open]), xoá lúc đóng ════════════════════
 *
 * Mẫu VC-R6 (`CastSessionPin.kt`): lựa chọn của hồ sơ (`cast_style`) đọc MỘT lần đầu lượt mở; đổi lựa chọn giữa phiên chỉ đổi
 * prefs — 0 lệnh AutoContainer, khung/khoá lưu vẫn theo phiên.
 *
 * @param desired kiểu người lái chọn lúc mở.
 * @param believed kiểu Kachi tin cụm đang hiện sau lượt mở ([ClusterStylePlan.Plan.believed]) — 2.90 · R2: CHỈ là kiểu Kachi đã
 *   gửi và thấy thành công trong đời tiến trình này; còn lại UNKNOWN.
 * @param frame kiểu dùng cho KHUNG app và khoá lưu (`__RECT`): kiểu tin nếu biết (cụm là sự thật, không phải lựa chọn);
 *   UNKNOWN ⇒ kiểu người lái CHỌN ([ClusterStylePlan.effective]; đời xe không cho chọn ⇒ Bo tròn, đường cũ y nguyên). 2.92 ·
 *   CLUSTER-FRAME-CHOSEN thay luật "trọn cụm + khoá Bo tròn" của 2.90 · R2: [ĐO log xe 06/10] BYD giết Kachi mỗi lần tắt máy và
 *   cổng hay phải bỏ opcode (bóng nổi, chưa đủ 15 s) ⇒ phần lớn phiên là UNKNOWN ⇒ khung đã lưu không bao giờ dùng, chỉnh tay lưu
 *   nhầm ô Bo tròn. Cài đặt vẫn nói "chưa rõ kiểu" + mời "Áp ngay" ([CastStyleApply.offer]).
 */
data class CastSessionStyle(
    val desired: CastStyle,
    val believed: BelievedStyle,
    val frame: CastStyle,
) {
    companion object {
        fun of(recipe: ProjectionRecipe, desired: CastStyle, plan: ClusterStylePlan.Plan?): CastSessionStyle {
            val believed = plan?.believed ?: BelievedStyle.UNKNOWN
            return when (believed) {
                BelievedStyle.CURVED -> CastSessionStyle(desired, believed, CastStyle.CURVED)
                BelievedStyle.RECT -> CastSessionStyle(desired, believed, CastStyle.RECT)
                // 2.92 · CLUSTER-FRAME-CHOSEN — chưa xác nhận ⇒ kiểu người lái chọn: ĐÚNG đường của phiên đã xác nhận kiểu ấy (khung
                // Chữ nhật đã lưu chạy trên xe 14:18 06/10 [ĐO]). Đời xe chỉ có Bo tròn / DiLink 5 ⇒ `effective` ra Bo tròn / `null`.
                BelievedStyle.UNKNOWN ->
                    CastSessionStyle(desired, believed, ClusterStylePlan.effective(recipe, desired) ?: CastStyle.CURVED)
            }
        }
    }
}

/**
 * ═══ B1b — nút "Áp ngay" của lựa chọn Bo tròn / Chữ nhật (R5): luật THUẦN ═════════════════════════════════════════════════
 *
 * "Áp ngay" = `restoreCluster` (dừng → đóng chiếu → mở lại sau 2 s) — lượt mở đi qua CỔNG theme như mọi lượt mở khác: mức B
 * (2.95: mọi đời `AutoContainer`) ⇒ màn ảo cụm trống (đã gỡ ClusterBlack lúc đóng, không bóng nổi) thì gửi theme; còn cửa sổ lạ ⇒ bỏ, nói lý do.
 * Theme KHÔNG BAO GIỜ gửi từ màn Cài đặt trực tiếp hay khi đang chiếu.
 */
object CastStyleApply {

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
