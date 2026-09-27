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
 * ⇒ dải vẽ được: `y ∈ [132, 560)`, `x ∈ [140, 1780)`. **Lề từng mép, đúng như đã đo** (bản trước viết *"lề mỗi phía
 * ≥ 15 px"* cho cả bốn mép — sai ở hai mép dọc, [P2] soát 27/09): trái **+19** (kính 121) · phải **+18** (cột icon
 * 1798) · dưới **+7** (chữ thanh dưới 567; §9 còn ngỏ 560–567) · trên **+1** (đáy đường kẻ thanh hệ thống ở `131`,
 * đo ở ảnh sai số nhỏ nhất — doc §13). ⚠ 2.78 còn lấy `136` = *"hàng đầu tiên nhìn thấy trọn"* (mép trên thanh tìm
 * kiếm gmaps), tức một **mốc nội dung**, không phải mép thanh; 2.79 đo thẳng mép thanh và hạ xuống **132** theo lời
 * owner *"vẫn còn dư phía trên top"* ⇒ dải cao **428**. Cả bộ số ở mức [ĐO ±8 px] nên một con "15" là trong sai số.
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
 * Mép PHẢI: 2.77 để tường thẳng vì *"cột icon hệ thống (biển 30 / ADAS) từ `x ≈ 1798`"* (F7) chặn trước kính. [ĐO
 * xe 27/09 tối] owner bác: *"bên phải không bám, còn thừa 1 khoảng"* — và log cùng lượt cho thấy vì sao, `cong=1462/
 * 1462/1462`: ba mẫu BẰNG NHAU, tức bên phải không có mô hình cong nào. 2.78 đo mép phải bằng **đúng** phép của mép
 * trái (doc §12): kính phải `x ≈ 1833` (đỉnh dải) → xa nhất `≈ 1876` (`y ≈ 240`) → **`1800`** (đáy dải) ⇒
 * [ClusterBandSpec.rightEdge]. Điểm TRONG CÙNG của bảng là `1800`, tức trùng cột icon `1798` **trong sai số ±8 px**
 * của chính phép đo ⇒ cửa sổ cao trọn dải nới ra được `20 px` mà vẫn không thật sự leo vào cột icon; hàng nào hệ
 * thống có vẽ biển 30 lên đó thì nó **đè lên** camera y như mũi tên xi-nhan (D6), không phải ngược lại.
 *
 * ⚠ **Hướng làm tròn của hai mép NGƯỢC nhau**, và cả hai cùng một lý do (*"không để mất video"*): bên trái an toàn là
 * `x` LỚN hơn (vào trong), bên phải an toàn là `x` NHỎ hơn. Vì thế bảng trái gộp nhiều ảnh bằng `min`, bảng phải cũng
 * bằng `min` — **cùng phép, khác nghĩa**: bên trái `min` là ước lượng NGOÀI cùng, bên phải `min` là TRONG cùng.
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
        /** Cửa sổ đứng ở đầu TRÁI dải? Quyết mép nào là mép **TRONG** (mép quay vào giữa cụm) — xem [fadePx]. */
        val atLeft: Boolean = true,
        /** Mép ngoài cong ĐÃ co giãn về display (xem [leftEdge]); rỗng ⇒ tường thẳng `band.x0` (hành vi 2.76). */
        val leftEdge: List<Int> = emptyList(),
        /** Mép ngoài cong bên PHẢI đã co giãn (xem [rightEdge]); rỗng ⇒ tường thẳng `band.x1`. Cửa sổ chỉ mang MỘT
         *  trong hai bảng — bảng của bên nó đứng — nên `:app` dựng mặt nạ không cần biết nó ở bên nào. */
        val rightEdge: List<Int> = emptyList(),
        /** Hình user đã chọn ([CameraSignalPolicy.SHAPES]) — quyết mặt nạ/dải mờ/phép nới, xem [place]. */
        val shape: String = SHAPE_CLUSTER,
        /** Cỡ **LỚP VIDEO** bên trong cửa sổ (≥ cửa sổ ở cả hai trục, đúng tỉ lệ ảnh — [CameraOverlayFrame.cover]).
         *  Bằng đúng cửa sổ ⇒ không phóng. Lớn hơn ⇒ phần dư do chính cửa sổ cắt, **không** kéo giãn ảnh. */
        val layerW: Int = w,
        val layerH: Int = h,
    ) {
        val rect: Rect get() = Rect(x, y, x + w, y + h)

        /** Một dòng cho log buổi xe: cỡ cửa sổ · cỡ lớp video · hệ số phóng · phần tầm nhìn mất (CLAUDE.md §11). */
        fun describe(): String =
            "hình=$shape cửa=${w}x$h tại=$x,$y lớp=${layerW}x$layerH phóng=${zoomPct(this)}% mất=${lossPct(this)}%"
    }

    fun isCluster(shape: String): Boolean = shape == SHAPE_CLUSTER

    /**
     * Hình khung THẬT SỰ dùng để vẽ. Đúng **MỘT** phép quy, và nó theo display THẬT chứ không theo pref: *"theo cụm"*
     * trên màn chính ⇒ **chữ nhật** ([CameraSignalPolicy.SHAPE_RECT] = cửa sổ 2.73), vì màn chính không có dải nào để
     * ôm; không quy sang tròn (tròn đổi cả crop ⇒ owner sẽ tưởng pref hình bị đổi).
     *
     * ⚠ **KHÔNG ép hình trên cụm** — [ĐO owner 27/09 tối]: *"khi chiếu camera lên cụm, user vẫn có thể chọn chữ
     * nhật/tròn/theo cụm nhé, không ép"*. 2.77 có nhánh **thăng** `RECT → CLUSTER` khi ở trên cụm (lý lẽ: owner chê
     * nhánh chữ nhật *"bé tý… không hề theo hình cụm"*). Nhánh ấy **đã gỡ**: chữ nhật trên cụm ra chữ nhật, tròn ra
     * tròn, theo cụm ra theo cụm — ba lựa chọn của user còn nguyên ba. Thứ chữa lời chê *"bé tý"* nay là phép kẹp
     * [place] (cả ba hình đều CAO TRỌN DẢI, cân đối hai bên — 2.79) chứ không phải đổi hình sau lưng user. Đây là
     * quyết định của owner, không phải quên.
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

    /**
     * Mép NGOÀI cong bên PHẢI, đã co giãn về display — **đối xứng** với [leftEdge], đảo đúng một thứ: hướng kẹp.
     * Mẫu nằm trong `band.x1..displayW` ⇒ mép phải chỉ được nới RA (sang phải), không bao giờ ăn vào trong dải.
     * Bảng rỗng (đời cụm chưa đo mép phải) ⇒ rỗng ⇒ mọi chỗ gọi rơi về tường thẳng `band.x1` (hành vi 2.77).
     */
    fun rightEdge(band: Rect, spec: ClusterBandSpec, displayW: Int): List<Int> {
        if (spec.rightEdge.isEmpty()) return emptyList()
        val sx = if (displayW > 0) displayW.toDouble() / spec.refW else 1.0
        val cap = (if (displayW > 0) displayW else spec.refW).coerceAtLeast(band.x1)
        return spec.rightEdge.map { (it * sx).roundToInt().coerceIn(band.x1, cap) }
    }

    /** MỘT phép nội suy cho cả hai mép (bảng rỗng ⇒ [fallback] = tường thẳng của bên ấy). */
    private fun edgeAt(band: Rect, edge: List<Int>, y: Int, fallback: Int): Int {
        if (edge.isEmpty()) return fallback
        if (edge.size == 1) return edge[0]
        val last = (edge.size - 1).toDouble()
        val t = ((y - band.y0).toDouble() / band.h.coerceAtLeast(1) * last).coerceIn(0.0, last)
        val i = t.toInt().coerceAtMost(edge.size - 2)
        return (edge[i] + (t - i) * (edge[i + 1] - edge[i])).roundToInt()
    }

    /** `x` ngoài cùng vẽ được ở hàng [y] — nội suy tuyến tính trên [edge]; bảng rỗng ⇒ tường thẳng `band.x0`. */
    fun leftEdgeAt(band: Rect, edge: List<Int>, y: Int): Int = edgeAt(band, edge, y, band.x0)

    /** Như [leftEdgeAt] nhưng cho mép PHẢI: bảng rỗng ⇒ tường thẳng `band.x1`. */
    fun rightEdgeAt(band: Rect, edge: List<Int>, y: Int): Int = edgeAt(band, edge, y, band.x1)

    /**
     * Mép trái THẬT SỰ có mực ở hàng [y] sau mặt nạ (`:app` cắt mọi thứ bên trái đường cong). Đây là con số mà bài
     * test đo — không phải `p.x`, vì cửa sổ cố tình rộng hơn phần nhìn thấy để mặt nạ có chỗ cắt.
     */
    fun maskLeftAt(p: Placement, y: Int): Int = maxOf(p.x, leftEdgeAt(p.band, p.leftEdge, y))

    /** Mép PHẢI thật sự có mực ở hàng [y] sau mặt nạ — đối xứng với [maskLeftAt] (ở đây mặt nạ cắt về phía `x` nhỏ). */
    fun maskRightAt(p: Placement, y: Int): Int = minOf(p.x + p.w, rightEdgeAt(p.band, p.rightEdge, y))

    /**
     * Mép có mực ở phía NGOÀI của cửa sổ (trái nếu nó đứng đầu trái, phải nếu đứng đầu phải) — một cửa duy nhất cho
     * dòng log `overlay show … cong=` để `:app` không phải tự đoán bên. Cửa sổ mang bảng nào thì đọc bảng ấy.
     */
    fun outerInkAt(p: Placement, y: Int): Int =
        if (p.rightEdge.isNotEmpty()) maskRightAt(p, y) else maskLeftAt(p, y)

    /**
     * Điểm **TRONG CÙNG** của mép kính cong trên dải hàng `[y, y + h)` — tức `x` nhỏ nhất mà cửa sổ đặt vào đó thì
     * [maskLeftAt] **không cắt** một pixel nào của video ở bất kỳ hàng nào nó chiếm.
     *
     * Chỉ xét đúng những hàng cửa sổ thật sự phủ: cửa sổ thấp hơn dải (nguồn có tỉ lệ dọc) thì nó nằm giữa dải, nơi
     * kính rộng nhất — bắt nó lùi theo hàng đáy mà nó không hề chiếm là mất chỗ vô cớ.
     *
     * Rỗng (đời cụm chưa đo) ⇒ tường thẳng [Rect.x0], đúng hành vi 2.76.
     */
    fun innermostLeft(band: Rect, edge: List<Int>, y: Int, h: Int): Int {
        if (edge.isEmpty()) return band.x0
        var x = leftEdgeAt(band, edge, y)
        val bottom = y + h - 1
        x = maxOf(x, leftEdgeAt(band, edge, bottom))
        // Mẫu nào rơi vào trong dải hàng thì cũng phải xét: đỉnh của đường cong có thể nằm giữa hai đầu.
        val last = (edge.size - 1).coerceAtLeast(1)
        for (i in edge.indices) {
            val sy = band.y0 + band.h * i / last
            if (sy in y..bottom) x = maxOf(x, edge[i])
        }
        return x
    }

    /**
     * Điểm **TRONG CÙNG** của mép kính PHẢI trên dải hàng `[y, y + h)` — tức `x` LỚN NHẤT mà cửa sổ có mép phải đặt
     * vào đó thì [maskRightAt] **không cắt** một pixel nào ở bất kỳ hàng nào nó chiếm. Đối xứng [innermostLeft]:
     * bên trái trong cùng là `max` của đường cong, bên phải là `min`.
     *
     * Rỗng (chưa đo mép phải) ⇒ tường thẳng [Rect.x1], đúng hành vi 2.77.
     */
    fun innermostRight(band: Rect, edge: List<Int>, y: Int, h: Int): Int {
        if (edge.isEmpty()) return band.x1
        var x = rightEdgeAt(band, edge, y)
        val bottom = y + h - 1
        x = minOf(x, rightEdgeAt(band, edge, bottom))
        val last = (edge.size - 1).coerceAtLeast(1)
        for (i in edge.indices) {
            val sy = band.y0 + band.h * i / last
            if (sy in y..bottom) x = minOf(x, edge[i])
        }
        return x
    }

    /**
     * ═══ Dải MỜ ở mép TRONG của cửa sổ *theo cụm* (2.78) ══════════════════════════════════════════════════════════
     *
     * [ĐO owner 27/09 tối]: *"phần cạnh bên phải thêm tý blur ra ngoài cho nó smooth, ko là 1 vạch thẳng nhìn nó như
     * sẹo, ngược lại cho bên phải cũng thế"*. Mép NGOÀI ôm kính (mặt nạ cắt cứng — ở đó bên ngoài là viền đục, cắt
     * cứng không ai thấy); mép **TRONG** (mép quay vào giữa cụm, `x + w` với cửa sổ bên trái) thì nằm giữa vùng sáng
     * nên một đường cắt thẳng đứng đọc ra như vết sẹo ⇒ chuyển alpha dần về 0 trên một dải hẹp.
     *
     * Bề rộng = **đúng [radiusPx] của hồ sơ** (đã co theo display, đã kẹp ≤ nửa cạnh ngắn) — không thêm một hằng
     * mới: bo góc và dải mờ là cùng một cỡ "mềm mép" nên chúng khớp nhau ở hai góc trong, và 24 px ở cỡ tham chiếu
     * = 4–8 % bề rộng cửa sổ (565 / 318 px), đúng khoảng owner mô tả (*"tý"*). Kẹp thêm `≤ w/4` để cửa sổ hẹp bất
     * thường không bị mờ mất một phần tư ảnh; `w` quá nhỏ ⇒ 0 (không mờ, thà cứng còn hơn mất ảnh).
     */
    fun fadePx(p: Placement): Int =
        if (!isCluster(p.shape) || p.w < 8) 0 else p.radiusPx.coerceIn(0, p.w / 4)

    /**
     * Hệ số phóng của phép COVER: `1.0` = lớp video bằng đúng cửa sổ (không phóng, không mất gì). Lớn hơn 1 ⇒ ảnh
     * được phóng cho lấp kín cửa sổ rộng hơn, và **đúng** `1 − 1/hệ số` phần tầm nhìn bị chính cửa sổ cắt mất
     * (một trục phóng khít, trục kia dư ⇒ diện tích thấy được = `1/hệ số`).
     */
    fun zoom(p: Placement): Double = maxOf(
        p.layerW.toDouble() / p.w.coerceAtLeast(1),
        p.layerH.toDouble() / p.h.coerceAtLeast(1),
    ).coerceAtLeast(1.0)

    /** [zoom] theo phần trăm — `100` = không phóng. Số nguyên để dòng log không phụ thuộc `Locale`. */
    fun zoomPct(p: Placement): Int = (zoom(p) * 100).roundToInt()

    /** Phần tầm nhìn MẤT vì phóng, phần trăm (`0` khi không phóng). */
    fun lossPct(p: Placement): Int = ((1.0 - 1.0 / zoom(p)) * 100).roundToInt()

    /**
     * Bất biến an toàn của cửa sổ trên cụm — thứ mà [P1] của 2.76 mua được, 2.77 **không được** làm rữa:
     * cửa sổ không bao giờ chạm thanh trên/dưới của hệ thống, không bao giờ vượt mép TRONG của dải, và mép ngoài
     * không bao giờ ra xa hơn đường kính đã đo (bảng rỗng ⇒ đúng tường thẳng `band.x0`/`band.x1` của 2.76).
     */
    fun insideBand(p: Placement): Boolean {
        val b = p.band
        val outerLeft = p.leftEdge.minOrNull() ?: b.x0
        val outerRight = p.rightEdge.maxOrNull() ?: b.x1
        return p.x >= outerLeft && p.x >= 0 && p.y >= b.y0 && p.x + p.w <= outerRight && p.y + p.h <= b.y1
    }

    /** Bán kính bo của cửa sổ trên display này: co theo chiều cao, và không bao giờ quá nửa cạnh ngắn của cửa sổ. */
    fun radius(spec: ClusterBandSpec, displayH: Int, w: Int, h: Int): Int {
        val scaled = if (displayH > 0) (spec.radiusPx * displayH.toDouble() / spec.refH).roundToInt() else spec.radiusPx
        return scaled.coerceIn(0, (minOf(w, h) / 2).coerceAtLeast(0))
    }
    /**
     * ═══ ĐẶT cửa sổ camera trên DẢI CỤM — **ba hình, ba đánh đổi**, user tự chọn (2.79) ═══════════════════════════
     *
     * [ĐO owner 27/09 tối] ba câu, mỗi câu một hình: *"1, chữ nhật chỉnh lại cho cao bằng khoảng của cụm, đang bé.
     * 2, theo cụm chấp nhận mất 20%, làm bo theo cụm cho đẹp. 3. tròn: đường kính bằng chìu cao tối đa chiếu lên
     * cụm, đang thu ngắn quá --> 3 options, có cái được cái mất, làm tối đa, user thích chọn gì thì chọn"*.
     *
     * Ba nhánh dưới đây KHÁC nhau đúng **hai** chỗ (bề rộng vùng + có nới ra kính hay không); mọi thứ còn lại dùng
     * chung, nên không có bản sao công thức nào:
     *
     * | Hình | Vùng | Chỗ đứng | Mặt nạ cong | Phóng (cover) | Mất tầm nhìn (gương 4:3 · xoay ±90) |
     * |---|---|---|---|---|---|
     * | [CameraSignalPolicy.SHAPE_RECT] | nửa dải × **trọn cao** | dính mép dải, **cân đối hai bên** | không | chỉ khi ảnh rộng hơn nửa dải | 0 % · 0 % |
     * | [SHAPE_CLUSTER] | như trên, rồi **nới ra tới kính** | mép NGOÀI = điểm ngoài cùng đường cong | có | luôn (đó là giá của việc bám cong) | ~11 % · ~18 % |
     * | [CameraSignalPolicy.SHAPE_ROUND] | **vuông** cạnh = trọn cao | dính mép dải, cân đối hai bên | không | khi crop không vuông | 0 % (crop tròn đã vuông) |
     *
     * **Chiều cao LUÔN trọn dải** ở cả ba hình và ở mọi góc xoay ([CameraOverlayFrame.tall], owner: *"cả xoay và
     * không xoay, thì chiều cao cần tối đa nhé"*) — tới 2.78 chữ nhật/tròn còn đi qua một ô vuông đo bằng **% chiều
     * cao display** (360 px) nên thấp hơn dải 68 px và hẹp hơn tới 2,5 lần; ô ấy đã hết chủ.
     *
     * ## Vì sao *theo cụm* phải NỚI RA rồi PHÓNG, chứ không đứng ở điểm trong cùng như 2.78
     * 2.78 đặt cửa sổ ở điểm **TRONG CÙNG** của kính để mặt nạ không cắt một pixel video nào. Hệ quả hình học:
     * `maskLeftAt` bằng đúng `p.x` ở **mọi** hàng ⇒ mép trái hiện ra là một **đường thẳng đứng**. [ĐO owner 27/09
     * tối] nhìn tận mắt: *"viền trái đâu có bám theo cụm hả, chỉ là 1 đường thẳng thôi mà, khác gì chữ nhật đâu"*.
     * Không có cách nào vừa bám cong vừa không mất pixel: muốn mép là đường cong thì cửa sổ **phải** phủ tới điểm
     * ngoài cùng của đường cong, và phần giữa hai điểm (70 px bên trái · 76 px bên phải trên bộ số Seal) chỉ có thể
     * lấp bằng **ảnh phóng thêm**. Owner đã chốt cái giá: *"chấp nhận mất 20%"*.
     *
     * Phóng ở đây là **COVER, không phải kéo giãn**: lớp video lấy cỡ [CameraOverlayFrame.cover] (≥ cửa sổ ở cả hai
     * trục, **đúng tỉ lệ ảnh**) rồi chính cửa sổ cắt phần dư — xem [Placement.layerW]. Nếu thay vào đó cứ căng vùng
     * crop vào một cửa sổ rộng hơn thì bước 1 của [CameraOverlayTransform.matrix] thành phép co giãn không đẳng
     * hướng = ảnh méo, đúng thứ `fit` sinh ra để tránh.
     *
     * *"Mép phía xe hướng vào trong"* [ĐO khung thô 27/09: thân xe ở mép PHẢI ô gương trái, mép TRÁI ô gương phải] tự
     * thoả bởi chính phép đặt này: gương trái ở đầu trái ⇒ mép phải (thân xe) hướng vào giữa; gương phải đối xứng.
     *
     * @param shape hình user chọn; **chỉ** [SHAPE_CLUSTER] mới nới ra kính + mang bảng mép cong đi tiếp.
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
        shape: String = SHAPE_CLUSTER,
        leftEdge: List<Int> = emptyList(),
        rightEdge: List<Int> = emptyList(),
    ): Placement {
        val cluster = isCluster(shape)
        val areaH = band.h.coerceAtLeast(1)
        // TRÒN: vùng VUÔNG cạnh = trọn chiều cao dải ⇒ đường kính đúng bằng chiều cao dải (owner: *"đường kính bằng
        // chìu cao tối đa chiếu lên cụm"*). Hai hình kia: nửa dải, để hai bên không bao giờ chạm nhau.
        val areaW = (if (shape == CameraSignalPolicy.SHAPE_ROUND) areaH else band.w / 2).coerceAtLeast(1)
        val f = CameraOverlayFrame.tall(streamW, streamH, crop, rotationDeg, areaW, areaH)
        val h = f.h.coerceIn(1, areaH)
        // TRÒN: cửa sổ **luôn VUÔNG** — `Outline.setOval` trên một cửa sổ chữ nhật vẽ ra hình ELIP, và một nguồn cao
        // (vd nguyên khung pano xoay 90 ⇒ 0,19:1) sẽ cho cạnh 80 px thay vì 428. Phần ảnh dư do [cover] cắt.
        // Chưa biết cỡ nguồn ⇒ ô VUÔNG cạnh = chiều cao dải (đúng nghĩa "ô vuông 2.72"), không phải cả nửa dải.
        val fitW = if (shape == CameraSignalPolicy.SHAPE_ROUND) h
        else (if (f.streamKnown) f.w else minOf(areaW, areaH)).coerceIn(1, areaW)
        val y = band.y0 + (band.h - h) / 2
        // Mép TRONG (mép quay vào giữa cụm) giữ NGUYÊN chỗ của 2.78 — điểm trong cùng của kính ± bề rộng đúng tỉ lệ;
        // chỉ mép NGOÀI trượt ra tới điểm ngoài cùng của đường cong, và cửa sổ rộng thêm đúng phần lấn ấy.
        // Bảng rỗng (đời cụm chưa đo) hoặc hình khác *theo cụm* ⇒ tường thẳng mép dải, cân đối hai bên.
        // ⚠ `streamKnown` là điều kiện THỨ BA, cùng hạng với hai điều kiện kia: nới ra rồi lấp lại chỉ đúng khi biết
        // **tỉ lệ ảnh** (phép cover cần nó). Chưa đo cỡ nguồn mà vẫn nới thì bước 1 của ma trận sẽ căng ảnh ra cho
        // đầy cửa sổ rộng hơn ⇒ KÉO GIÃN — đúng thứ đang tránh. Chưa biết ⇒ tường thẳng, y như đời cụm chưa đo.
        val useLeft = cluster && atLeft && leftEdge.isNotEmpty() && f.streamKnown
        val useRight = cluster && !atLeft && rightEdge.isNotEmpty() && f.streamKnown
        val x = when {
            useLeft -> leftEdge.minOrNull() ?: band.x0
            useRight -> innermostRight(band, rightEdge, y, h) - fitW
            atLeft -> band.x0
            else -> band.x1 - fitW
        }
        val w = when {
            useLeft -> innermostLeft(band, leftEdge, y, h) + fitW - x
            useRight -> (rightEdge.maxOrNull() ?: band.x1) - x
            else -> fitW
        }
        val p = placed(x, y, w, h, spec, displayH, band, f.streamKnown, atLeft, shape, streamW, streamH, crop, rotationDeg,
            leftEdge = if (useLeft) leftEdge else emptyList(), rightEdge = if (useRight) rightEdge else emptyList())
        // GUARD CỨNG ở tầng THI HÀNH (CLAUDE.md §5): một hồ sơ/cỡ display lạ mà đẩy cửa sổ ra khỏi dải thì **rơi về
        // tường thẳng 2.76** ngay tại đây, chứ không để tầng vẽ tin lời tầng cấu hình. Rơi về = mất đường cong và
        // mất phép phóng, KHÔNG mất camera.
        if (insideBand(p)) return p
        val fx = if (atLeft) band.x0 else band.x1 - fitW
        return placed(fx, y, fitW, h, spec, displayH, band, f.streamKnown, atLeft, shape, streamW, streamH, crop, rotationDeg)
    }

    /** Dựng [Placement] + đo cỡ lớp video phủ kín — một cửa duy nhất, để nhánh guard không quên phép nào. */
    @Suppress("LongParameterList")
    private fun placed(
        x: Int, y: Int, w: Int, h: Int, spec: ClusterBandSpec, displayH: Int, band: Rect, streamKnown: Boolean,
        atLeft: Boolean, shape: String, streamW: Int, streamH: Int, crop: FloatArray?, rotationDeg: Int,
        leftEdge: List<Int> = emptyList(), rightEdge: List<Int> = emptyList(),
    ): Placement {
        val cov = CameraOverlayFrame.cover(streamW, streamH, crop, rotationDeg, w, h)
        return Placement(
            x, y, w, h, radius(spec, displayH, w, h), band, streamKnown, atLeft = atLeft,
            leftEdge = leftEdge, rightEdge = rightEdge, shape = shape,
            layerW = maxOf(cov.w, w), layerH = maxOf(cov.h, h),
        )
    }
}
