package com.byd.clusternav.launcher.camera

import kotlin.math.roundToInt

/**
 * ═══ DẢI GIỮA của CỤM — số đo Seal DL3, đứng sau một hồ sơ, THUẦN ══════════════════════════════════════════════
 *
 * Owner 2026-09-27 (3 ảnh cụm + framebuffer display 1): *"header top và bottom là của hệ thống, không vẽ vào được,
 * chỉ vẽ được khúc giữa như gmaps đang hiện"*. Đây là **số đo** của khúc giữa ấy, theo toạ độ của display chiếu
 * (`fission_bg_xdjaVirtualSurface` [ĐO logcat 27/09 09:57:24] `1920×720`, `FLAG_PRESENTATION`).
 *
 * ## Số Seal đến từ đâu — `docs/diagnostics/offcar-2026-09-27/camera-cluster-band.md` §2 [ĐO ±8 px]
 * Homography ảnh-chụp → framebuffer (8 điểm neo, sai số ≤ 4 px ở ảnh phải): mép dưới thanh trên **≈ 130–140**, chữ
 * thanh dưới bắt đầu **≈ 567–578**, nội dung tới **559** vẫn thấy trọn; viền kính cong: trái **≈ 115–125** (đáy dải)
 * / **68** (đỉnh dải), phải **≈ 1819** (đáy) / **1849** (đỉnh); cột icon hệ thống (biển 30, ADAS) từ **x ≈ 1798**.
 * ⇒ dải vẽ được: `y ∈ [136, 560)`, `x ∈ [140, 1780)`. **Lề từng mép, đúng như đã đo** (bản trước viết *"lề mỗi phía
 * ≥ 15 px"* cho cả bốn mép — sai ở hai mép dọc, [P2] soát 27/09): trái **+19** (kính 121) · phải **+18** (cột icon
 * 1798) · dưới **+7** (chữ thanh dưới 567; §9 còn ngỏ 560–567) · trên **≈ +5 theo chuẩn 130–131, và 0 nếu lấy biên
 * trên 140** — tức `136` KHÔNG phải một lề: nó là **hàng đầu tiên nhìn thấy trọn**, đo trực tiếp (mép trên thanh tìm
 * kiếm của gmaps ở framebuffer `y = 136` hiện đủ, §2). Cả bộ số ở mức [ĐO ±8 px] nên một con "15" là trong sai số.
 *
 * ## Vì sao là một HỒ SƠ, không phải hằng
 * Đời xe khác (SL6 cụm `1920×800`) có dải khác — CLAUDE.md §7: khác biệt đời xe nằm trong `ClusterProfile`, không
 * rải trong code. Lớp `:app` nhận [ClusterBandSpec] từ hồ sơ; [ClusterBandSpec.SEAL_DL3] là mặc định để `:core`
 * tự đứng và test được (yêu cầu chéo làn: `ClusterProfile.band`). Display thật khác cỡ tham chiếu ⇒ [band] **co giãn
 * theo tỉ lệ** rồi kẹp vào display — [SUY] tốt hơn một rect tuyệt đối rơi ra ngoài màn.
 *
 * ## Vì sao KHÔNG mô phỏng viền kính cong
 * Mép trái/phải của cụm là đường **xiên** (68 → 121 px từ đỉnh xuống đáy), không phải cung tròn ⇒ không có "bán kính
 * đo được". Cách đúng là **lùi vào** ([ClusterBandSpec.left]/`right`) và bo góc nhẹ ([ClusterBandSpec.radiusPx],
 * [SUY]) cho cửa sổ không lộ góc nhọn sát viền.
 */
object CameraClusterBand {

    /** Mã hình khung *"theo cụm"* — MỘT nguồn: [CameraSignalPolicy.SHAPE_CLUSTER] (nằm trong `SHAPES` ⇒ pref/chip nhận). */
    const val SHAPE_CLUSTER = CameraSignalPolicy.SHAPE_CLUSTER

    /** Hình chữ nhật px `[x0, x1) × [y0, y1)`. */
    data class Rect(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val w: Int get() = x1 - x0
        val h: Int get() = y1 - y0
        fun contains(o: Rect): Boolean = o.x0 >= x0 && o.y0 >= y0 && o.x1 <= x1 && o.y1 <= y1
    }

    /** Cửa sổ camera đã đặt trong dải: vị trí tuyệt đối trên display + bán kính bo + dải đã co giãn. */
    data class Placement(val x: Int, val y: Int, val w: Int, val h: Int, val radiusPx: Int, val band: Rect, val streamKnown: Boolean) {
        val rect: Rect get() = Rect(x, y, x + w, y + h)
    }

    fun isCluster(shape: String): Boolean = shape == SHAPE_CLUSTER

    /**
     * Hình khung THẬT SỰ dùng để vẽ: *"theo cụm"* chỉ có nghĩa trên cụm; trên màn chính nó **rơi về chữ nhật**
     * ([CameraSignalPolicy.SHAPE_RECT] = cửa sổ 2.73), không phải tròn — tròn đổi cả crop (ô vuông) và sẽ làm owner
     * tưởng pref hình bị đổi. Hình khác giữ nguyên.
     */
    fun effectiveShape(shape: String, onCluster: Boolean): String =
        if (isCluster(shape) && !onCluster) CameraSignalPolicy.SHAPE_RECT else shape

    /**
     * Dải vẽ được trên display [displayW]×[displayH]. Cỡ display ≤ 0 (chưa đo) ⇒ đúng số tham chiếu của [spec].
     * Khác cỡ tham chiếu ⇒ co giãn theo từng trục rồi kẹp vào display; dải suy biến (≤ 0) không bao giờ trả về.
     */
    fun band(displayW: Int, displayH: Int, spec: ClusterBandSpec): Rect {
        if (displayW <= 0 || displayH <= 0) return Rect(spec.left, spec.top, spec.right, spec.bottom)
        val sx = displayW.toDouble() / spec.refW
        val sy = displayH.toDouble() / spec.refH
        val x0 = (spec.left * sx).roundToInt().coerceIn(0, displayW - 1)
        val x1 = (spec.right * sx).roundToInt().coerceIn(x0 + 1, displayW)
        val y0 = (spec.top * sy).roundToInt().coerceIn(0, displayH - 1)
        val y1 = (spec.bottom * sy).roundToInt().coerceIn(y0 + 1, displayH)
        return Rect(x0, y0, x1, y1)
    }

    /**
     * ═══ Vùng cho phép của đường 2.73 (ô VUÔNG ở góc trên) khi cửa sổ nằm TRÊN CỤM — kẹp vào dải ═══════════════════
     *
     * [P1 · SOÁT Opus 2026-09-27] Hình *theo cụm* ([place]) không phải hình duy nhất chạy trên cụm: `camera_shape` mặc
     * định là **chữ nhật** ([CameraSignalPolicy.defaultShape]) và *Hiện lên cụm* đang bật trên xe owner ([ĐO logcat
     * 27/09 10:50:42] `hình=RECT … cluster=true`), nên đường vùng-vuông của 2.73 là đường THẬT của xe. Vùng ấy đo
     * bằng % chiều cao display — tức **không biết gì về dải**: trên cụm 1920×720 nó bắt đầu ở `y = 43` trong khi
     * thanh trên hệ thống phủ tới `y ≈ 136` ([ĐO] F6) ⇒ **93/360 px bị che**, đúng triệu chứng owner báo (2.75 che
     * 77/495 — bản 2.76 làm nặng hơn vì đo đúng metrics của cụm thay vì của màn chính).
     *
     * Ở đây vùng **vẫn là ô vuông** (ngữ nghĩa 2.73: cạnh = % chiều cao, chỉ bị dải cắt bớt), chỉ **gốc và trần** lấy
     * từ dải: `y0 = band.y0`, dính đầu trái/phải dải, cạnh ≤ cạnh ngắn của dải. Nhờ vậy khung mà [CameraOverlayFrame.fit]
     * cắt trong vùng này **luôn** nằm trong dải (bài `CameraClusterBandTest`), mà hình *theo cụm* vẫn khác hẳn: nó cao
     * TRỌN dải và rộng tới NỬA dải, còn ô này vuông và chỉ cao bằng nửa chiều cao display.
     *
     * @return vùng ở **toạ độ tuyệt đối** của display. Chỗ gọi (`CameraOverlayView.box`) đổi sang độ lệch của
     *   `WindowManager.LayoutParams.x` — đó là khoảng cách kể từ góc mà `gravity` chọn, nên góc PHẢI phải lấy
     *   `displayW − x1`.
     */
    fun boxIn(band: Rect, side: Int, atLeft: Boolean): Rect {
        val s = side.coerceAtMost(minOf(band.w, band.h)).coerceAtLeast(1)
        val x0 = if (atLeft) band.x0 else band.x1 - s
        return Rect(x0, band.y0, x0 + s, band.y0 + s)
    }

    /** Bán kính bo của cửa sổ trên display này: co theo chiều cao, và không bao giờ quá nửa cạnh ngắn của cửa sổ. */
    fun radius(spec: ClusterBandSpec, displayH: Int, w: Int, h: Int): Int {
        val scaled = if (displayH > 0) (spec.radiusPx * displayH.toDouble() / spec.refH).roundToInt() else spec.radiusPx
        return scaled.coerceIn(0, (minOf(w, h) / 2).coerceAtLeast(0))
    }

    /**
     * Đặt cửa sổ camera ở **đầu trái** ([atLeft]) hay **đầu phải** của dải, cao trọn dải, đúng tỉ lệ vùng crop sau xoay
     * ([CameraOverlayFrame.fit] — cùng phép với màn chính, không có bản sao công thức thứ hai).
     *
     * Bề rộng trần = nửa dải, để hai bên không bao giờ chạm nhau và bản đồ ở giữa còn chỗ. Cửa sổ căn giữa theo
     * chiều dọc trong dải (thường bằng đúng dải, vì chiều cao quyết định).
     *
     * *"Mép phía xe hướng vào trong"* [ĐO khung thô 27/09: thân xe ở mép PHẢI ô gương trái, mép TRÁI ô gương phải] tự
     * thoả bởi chính phép đặt này: gương trái ở đầu trái ⇒ mép phải (thân xe) hướng vào giữa; gương phải đối xứng.
     * Không cần lật ảnh.
     */
    fun place(
        band: Rect,
        atLeft: Boolean,
        streamW: Int,
        streamH: Int,
        crop: FloatArray?,
        rotationDeg: Int,
        spec: ClusterBandSpec,
        displayH: Int,
    ): Placement {
        val areaW = (band.w / 2).coerceAtLeast(1)
        val areaH = band.h.coerceAtLeast(1)
        val f = CameraOverlayFrame.fit(streamW, streamH, crop, rotationDeg, areaW, areaH)
        // Chưa biết cỡ nguồn ⇒ ô VUÔNG cạnh = chiều cao dải (đúng nghĩa "ô vuông 2.72"), không phải cả nửa dải.
        val w = (if (f.streamKnown) f.w else minOf(areaW, areaH)).coerceIn(1, areaW)
        val h = f.h.coerceIn(1, areaH)
        val x = if (atLeft) band.x0 else band.x1 - w
        val y = band.y0 + (band.h - h) / 2
        return Placement(x, y, w, h, radius(spec, displayH, w, h), band, f.streamKnown)
    }
}

/**
 * Số đo dải giữa của MỘT đời cụm, theo toạ độ display chiếu cỡ [refW]×[refH]. Trường của hồ sơ xe (`ClusterProfile`).
 *
 * @property left mép trái vẽ được (px, bao gồm) · @property right mép phải (px, không bao gồm) — tương tự [top]/[bottom].
 * @property radiusPx bán kính bo góc cửa sổ ở cỡ tham chiếu [SUY — chọn, không đo được cung tròn nào trên kính].
 */
data class ClusterBandSpec(
    val refW: Int,
    val refH: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val radiusPx: Int,
) {
    init {
        require(refW > 0 && refH > 0) { "cỡ tham chiếu phải dương" }
        require(left in 0 until right && right <= refW) { "dải ngang phải nằm trong 0..$refW: $left..$right" }
        require(top in 0 until bottom && bottom <= refH) { "dải dọc phải nằm trong 0..$refH: $top..$bottom" }
        require(radiusPx >= 0)
    }

    companion object {
        /** Seal DL3 [ĐO 27/09 ±8 px] — xem KDoc [CameraClusterBand]. Dải `1640×424`. */
        val SEAL_DL3 = ClusterBandSpec(refW = 1920, refH = 720, left = 140, top = 136, right = 1780, bottom = 560, radiusPx = 24)
    }
}
