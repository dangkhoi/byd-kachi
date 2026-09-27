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
 * ## Viền kính cong — đo được, và nó là một BẢNG, không phải cung tròn (2.77)
 * Owner 27/09 chiều, nhìn camera trên cụm: *"này nhìn OK, nhưng shape nó không theo cạnh trái cong của cụm"*.
 * [ĐO] bộ đệm cụm là chữ nhật `1920×720` phẳng (`cum-d1.png`) ⇒ đường cong KHÔNG nằm trong framebuffer, nó là
 * **vùng sáng vật lý** của kính, chỉ đo được từ ẢNH CHỤP. Dò biên sáng/tối trong chính không gian framebuffer
 * (quét qua homography ngược của §2 doc) cho mép trái là đường **"<"**: `x ≈ 42` ở `y = 136`, ra xa nhất `x ≈ 16`
 * quanh `y ≈ 210`, rồi vào lại `x ≈ 123` ở `y = 560` — **không** phải cung tròn, **không** phải parabol (khớp
 * parabol sai 73 px). Mô hình rẻ nhất mà số liệu đỡ được: **bảng 9 mẫu chia đều** + nội suy tuyến tính
 * ([ClusterBandSpec.leftEdge], sai số nội suy **5,5 px** so với phép dò từng hàng).
 *
 * Mép PHẢI vẫn là **tường thẳng** — và đó cũng là một phép đo, không phải lười: kính phải ở `x ≈ 1802…1906` trong
 * cả dải, nhưng **cột icon hệ thống (biển 30 / ADAS) bắt đầu từ `x ≈ 1798`** (F7) ⇒ thứ chặn mép phải là cột icon
 * chứ không phải kính, mà cột icon thì thẳng. Nới mép phải ra kính là **đè lên biển báo tốc độ** — không đổi.
 *
 * Bo góc nhẹ ([ClusterBandSpec.radiusPx], [SUY]) vẫn giữ: nó lo hai góc bên TRONG, còn mép ngoài do bảng lo.
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
    data class Placement(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        val radiusPx: Int,
        val band: Rect,
        val streamKnown: Boolean,
        /** Mép ngoài cong ĐÃ co giãn về display (xem [leftEdge]); rỗng ⇒ tường thẳng `band.x0` (hành vi 2.76). */
        val leftEdge: List<Int> = emptyList(),
    ) {
        val rect: Rect get() = Rect(x, y, x + w, y + h)
    }

    fun isCluster(shape: String): Boolean = shape == SHAPE_CLUSTER

    /**
     * Hình khung THẬT SỰ dùng để vẽ. Hai phép quy, **cả hai đều theo display THẬT**, không theo pref:
     *  1. *"Theo cụm"* trên màn chính ⇒ **chữ nhật** ([CameraSignalPolicy.SHAPE_RECT] = cửa sổ 2.73), không phải
     *     tròn — tròn đổi cả crop (ô vuông) và sẽ làm owner tưởng pref hình bị đổi.
     *  2. **Chữ nhật trên CỤM ⇒ *theo cụm*** (2.77). Owner 27/09 chiều, nhìn đúng nhánh chữ nhật trên cụm:
     *     *"bé tý, bo các góc tròn, không hề theo hình cụm gì cả"*; cùng buổi, nhánh *theo cụm*: *"này nhìn OK"*.
     *     Hai hình này **cùng một crop** ([CameraPanoCrop.cropFor] quy `CLUSTER → RECT`) nên phép quy đổi **cửa sổ**
     *     chứ không đổi một điểm ảnh nào của nội dung — đúng thứ owner chê, không kèm bất ngờ nào khác. Và nó khép
     *     nợ 2.76 [P1]: đường chữ nhật trên cụm là đường mặc định ([CameraSignalPolicy.defaultShape]) nên nó phải là
     *     đường TỐT, không phải đường được vá cho khỏi tràn.
     *
     * TRÒN giữ nguyên trên cụm (đi [boxIn]): *"tròn"* là một lựa chọn cố ý về NỘI DUNG (crop ô vuông), quy nó sang
     * hình dải là đổi cả ảnh — đúng cái bẫy mà phép quy (1) tránh.
     */
    fun effectiveShape(shape: String, onCluster: Boolean): String = when {
        isCluster(shape) && !onCluster -> CameraSignalPolicy.SHAPE_RECT
        onCluster && shape == CameraSignalPolicy.SHAPE_RECT -> SHAPE_CLUSTER
        else -> shape
    }

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
     * ═══ MÉP NGOÀI CONG bên TRÁI, đã co giãn về display ════════════════════════════════════════════════════════════
     *
     * Bảng [ClusterBandSpec.leftEdge] ở toạ độ tham chiếu ⇒ nhân theo trục ngang rồi **kẹp vào `0..band.x0`**: mép
     * cong chỉ được phép nới RA (sang trái) so với tường thẳng 2.76, không bao giờ ăn vào trong. Bảng rỗng (đời cụm
     * chưa đo) ⇒ trả rỗng ⇒ mọi chỗ gọi rơi về tường thẳng — hành vi 2.76 y nguyên.
     *
     * Mẫu thứ `i` ứng với hàng `band.y0 + i × band.h / (n − 1)`, tức mẫu đầu ở đỉnh dải và mẫu cuối ở `band.y1`.
     */
    fun leftEdge(band: Rect, spec: ClusterBandSpec, displayW: Int): List<Int> {
        if (spec.leftEdge.isEmpty()) return emptyList()
        val sx = if (displayW > 0) displayW.toDouble() / spec.refW else 1.0
        return spec.leftEdge.map { (it * sx).roundToInt().coerceIn(0, band.x0) }
    }

    /** `x` ngoài cùng vẽ được ở hàng [y] — nội suy tuyến tính trên [edge]; bảng rỗng ⇒ tường thẳng `band.x0`. */
    fun leftEdgeAt(band: Rect, edge: List<Int>, y: Int): Int {
        if (edge.isEmpty()) return band.x0
        if (edge.size == 1) return edge[0]
        val last = (edge.size - 1).toDouble()
        val t = ((y - band.y0).toDouble() / band.h.coerceAtLeast(1) * last).coerceIn(0.0, last)
        val i = t.toInt().coerceAtMost(edge.size - 2)
        return (edge[i] + (t - i) * (edge[i + 1] - edge[i])).roundToInt()
    }

    /**
     * Mép trái THẬT SỰ có mực ở hàng [y] sau mặt nạ (`:app` cắt mọi thứ bên trái đường cong). Đây là con số mà bài
     * test đo — không phải `p.x`, vì cửa sổ cố tình rộng hơn phần nhìn thấy để mặt nạ có chỗ cắt.
     */
    fun maskLeftAt(p: Placement, y: Int): Int = maxOf(p.x, leftEdgeAt(p.band, p.leftEdge, y))

    /**
     * Bất biến an toàn của cửa sổ trên cụm — thứ mà [P1] của 2.76 mua được, 2.77 **không được** làm rữa:
     * cửa sổ không bao giờ chạm thanh trên/dưới của hệ thống, không bao giờ vượt mép TRONG của dải, và mép ngoài
     * không bao giờ ra xa hơn đường kính đã đo (bảng rỗng ⇒ đúng tường thẳng `band.x0` của 2.76).
     */
    fun insideBand(p: Placement): Boolean {
        val b = p.band
        val outer = p.leftEdge.minOrNull() ?: b.x0
        return p.x >= outer && p.x >= 0 && p.y >= b.y0 && p.x + p.w <= b.x1 && p.y + p.h <= b.y1
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
        leftEdge: List<Int> = emptyList(),
    ): Placement {
        val areaW = (band.w / 2).coerceAtLeast(1)
        val areaH = band.h.coerceAtLeast(1)
        val f = CameraOverlayFrame.fit(streamW, streamH, crop, rotationDeg, areaW, areaH)
        // Chưa biết cỡ nguồn ⇒ ô VUÔNG cạnh = chiều cao dải (đúng nghĩa "ô vuông 2.72"), không phải cả nửa dải.
        val w = (if (f.streamKnown) f.w else minOf(areaW, areaH)).coerceIn(1, areaW)
        val h = f.h.coerceIn(1, areaH)
        // Đầu TRÁI: cửa sổ trượt ra tới điểm xa nhất của mép cong (`:app` cắt phần thừa bằng mặt nạ) ⇒ ảnh chạm
        // đúng viền kính thay vì dừng ở một đường dọc thẳng cách viền tới 122 px (owner 27/09). Bề RỘNG không đổi
        // một px nào — nới bề rộng là kéo giãn ảnh, đúng thứ `CameraOverlayFrame.fit` sinh ra để tránh.
        val x = if (atLeft) (leftEdge.minOrNull() ?: band.x0) else band.x1 - w
        val y = band.y0 + (band.h - h) / 2
        val p = Placement(x, y, w, h, radius(spec, displayH, w, h), band, f.streamKnown, if (atLeft) leftEdge else emptyList())
        // GUARD CỨNG ở tầng THI HÀNH (CLAUDE.md §5): một hồ sơ/cỡ display lạ mà đẩy cửa sổ ra khỏi dải thì **rơi về
        // tường thẳng 2.76** ngay tại đây, chứ không để tầng vẽ tin lời tầng cấu hình. Rơi về = mất đường cong, KHÔNG
        // mất camera.
        return if (insideBand(p)) p else p.copy(x = if (atLeft) band.x0 else band.x1 - w, leftEdge = emptyList())
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
    /**
     * Mép NGOÀI cong bên trái: `n ≥ 2` mẫu `x` chia đều từ [top] tới [bottom] (mẫu `i` ở `top + i × (bottom−top)/(n−1)`),
     * mỗi mẫu trong `0..left`. Rỗng = **chưa đo đời cụm này** ⇒ tường thẳng [left] (hành vi 2.76, không thoái hoá).
     */
    val leftEdge: List<Int> = emptyList(),
) {
    init {
        require(refW > 0 && refH > 0) { "cỡ tham chiếu phải dương" }
        require(left in 0 until right && right <= refW) { "dải ngang phải nằm trong 0..$refW: $left..$right" }
        require(top in 0 until bottom && bottom <= refH) { "dải dọc phải nằm trong 0..$refH: $top..$bottom" }
        require(radiusPx >= 0)
        require(leftEdge.isEmpty() || leftEdge.size >= 2) { "bảng mép cong phải có ≥ 2 mẫu (hoặc rỗng = chưa đo)" }
        require(leftEdge.all { it in 0..left }) { "mép cong chỉ được nới RA ngoài tường thẳng $left: $leftEdge" }
    }

    companion object {
        /** Seal DL3 [ĐO 27/09 ±8 px] — xem KDoc [CameraClusterBand]. Dải `1640×424`. */
        val SEAL_DL3 = ClusterBandSpec(
            refW = 1920, refH = 720, left = 140, top = 136, right = 1780, bottom = 560, radiusPx = 24,
            // [ĐO 2026-09-27 chiều] biên vùng sáng vật lý, dò trong không gian framebuffer qua homography ngược của
            // 3 ảnh cụm; lấy ước lượng NGOÀI CÙNG của `cum-0`/`cum-1` ở mỗi hàng (lệch giữa hai ảnh tới 46 px, và
            // lệch theo hướng "ra ngoài" chỉ làm mất vài px ảnh SAU viền — còn lệch vào trong thì để lại đúng khe
            // đen mà owner đang chê). 9 mẫu, `y = 136, 189, …, 560`; sai số nội suy 5,5 px. Doc §11.
            leftEdge = listOf(42, 18, 19, 31, 41, 50, 61, 74, 89),
        )

        /**
         * [SEAL_DL3] **không mang** đường cong đã đo — mặc định cho đời cụm CHƯA ĐO ⇒ tường thẳng [left], tức đúng
         * hành vi 2.76 (`CameraClusterBand.leftEdge` trả rỗng, mặt nạ `glassMask` trả `null`).
         *
         * Vì sao phải có bản này thay vì cho mọi đời dùng chung [SEAL_DL3]: dải (`left/top/right/bottom`) chỉ ĐẶT cửa
         * sổ, đặt lệch thì nhìn thấy ngay; còn bảng `leftEdge` **CẮT điểm ảnh** (tới 122 px bên trái) theo đúng miếng
         * kính của MỘT đời cụm. Áp đường cong của Seal lên một cụm hình khác là xén ảnh mà không ai biết vì sao —
         * đúng loại lỗi CLAUDE.md §7 cấm (khác biệt đời xe phải nằm trong hồ sơ, không nằm trong một mặc định dùng
         * chung). Đo được đời nào thì khai [ClusterBandSpec] riêng cho đời ấy, y như [ClusterProfile.cameraFor].
         */
        val SEAL_DL3_NO_CURVE = SEAL_DL3.copy(leftEdge = emptyList())
    }
}
