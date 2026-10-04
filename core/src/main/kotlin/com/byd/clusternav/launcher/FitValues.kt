package com.byd.clusternav.launcher

import kotlin.math.ceil

/**
 * ═══ QA3 (2.87, 04/10) — GIÁ TRỊ ưu tiên hơn CHÚ THÍCH trong một ô widget (thuần, `:core`, đơn vị px) ═══════════════════
 *
 * Bệnh [ĐO máy ảo QA3, nhật ký `WidgetFit` + `uiautomator dump`]:
 *  - khung 3×1 có dock [tốc độ, lốp, đồng hồ] — `cell=136x99 HORIZONTAL/1 k=0.952 legible=false`: giờ hiện `06:…` ở cả 5 tiếng
 *    (TextView 39×21), ngày `04/10` thì trọn. HỒI QUY so 2.86 (bản ấy hiện trọn `07:13`, ngày co còn 6px);
 *  - khung 2×1 có dock [đồng hồ, PM2.5, tốc độ] — `cell=84x99 HORIZONTAL/1`: mỗi chữ còn 13–14px ⇒ MỌI giá trị thành `…`.
 *
 * Gốc [ĐO mã]: dạng NGANG lật con `MATCH_PARENT` của khối dọc thành `0 + weight 1` ([FitRules.lp]) ⇒ số to và chú thích CHIA
 * ĐỀU phần hàng sau icon (78px ⇒ 39 + 39). Số 17sp co tới sàn 10sp vẫn cần > 39px (QA3: `06:58` ở 15px vẫn `…` trên máy ảo),
 * trong khi ngày bên cạnh thừa chỗ. Luật giá trị của J1 (`FitRules.valuePx`, đã gỡ) co chữ theo chỗ CỦA RIÊNG nó — nửa hàng — nên không chữa
 * được, lại so `cần ≤ chỗ + 1px` nên chữ thiếu dưới 1px vẫn tính là vừa.
 *
 * Luật (quyết định điều phối, QA3):
 *  1. GIÁ TRỊ (số, giờ, số đo) KHÔNG BAO GIỜ `…` và ưu tiên hơn CHÚ THÍCH của nó: ô chật ⇒ chú thích nhường TRƯỚC (`…`, rồi ẩn),
 *     giá trị giữ trọn chữ ([share], [stackYields]);
 *  2. chỗ của giá trị = bề rộng chữ HIỆN TẠI đã chừa chữ số ([headroom] — mọi chữ số đo như chữ số RỘNG nhất ⇒ 09→10, 59→00
 *     không cắt), đo bằng chính `Paint` của nó, làm tròn LÊN ([needPx]); bề rộng TĨNH, không `WRAP` (R-WF7: `TextView` `WRAP`
 *     đổi chữ mỗi nhịp = `requestLayout` mỗi nhịp). Đổ tại chỗ chỉ chia lại khi giá trị SẼ bị cắt ([wouldClip] — 99 → 100);
 *  3. ngay cả MỘT MÌNH ở sàn 10sp giá trị vẫn không vừa ⇒ bộ giải chọn bố cục khác (ít cột / nhiều hàng / xếp DỌC số trên chú
 *     thích) thay vì cắt giá trị — tiêu chí "giá trị trọn" của [GridFit] trên hộp [GridFit.Shape.wholeWidthPx].
 *
 * Tầng vẽ (`FitValueRow` ở :app) đo chữ thật bằng `Paint` rồi hỏi các hàm ở đây; ở đây chỉ có LUẬT (`FitValuesTest`).
 */
object FitValues {

    /** Bậc co của một giá trị — cùng bậc `k` của lưới (1/32): số dài thêm vài px không đổi cỡ mỗi nhịp. */
    const val STEPS = 32

    /**
     * Bề rộng tối thiểu (em của chính nó) để chú thích bị `…` còn đọc ra được gì: dưới 2 em chỉ còn `0…` / `…` ⇒ ẩn hẳn
     * (2.86 để ngày co còn 6px — vô nghĩa). [ĐỀ XUẤT, owner chốt].
     */
    const val CAPTION_MIN_EM = 2f

    /** Chữ số rộng nhất theo [width] (bề rộng một ký tự ở cỡ chữ đang dùng); hoà ⇒ chữ số nhỏ hơn. */
    fun widestDigit(width: (Char) -> Float): Char = ('0'..'9').maxByOrNull(width) ?: '0'

    /** [text] với mọi chữ số thay bằng [widest] — đo chuỗi này thay cho chữ hiện tại ⇒ đổi chữ số không cần thêm chỗ. */
    fun headroom(text: CharSequence, widest: Char): String =
        buildString(text.length) { text.forEach { append(if (it in '0'..'9') widest else it) } }

    /**
     * Bề rộng (px nguyên) phải dành cho một chữ đo được [textPx] (thực): làm tròn LÊN + 1px. `BoringLayout.isBoring` làm tròn
     * lên bề rộng dòng rồi so `≤` chỗ (`TextView.makeSingleLayout`, r47 `TextView.java:9091-9166`) — so `cần ≤ chỗ + 1` như bản
     * J1 thì chữ thiếu dưới 1px vẫn "vừa" mà `Layout` vẫn `…`; +1px cho sai số hinting giữa `measureText` và bố cục.
     */
    fun needPx(textPx: Float): Int = if (textPx <= 0f) 0 else ceil(textPx.toDouble()).toInt() + 1

    /**
     * Cỡ chữ (px) cho một GIÁ TRỊ một dòng khi lưới KHÔNG đọc được: [fitPx] = cỡ lưới áp (gốc × k), [floorPx] = sàn đọc được
     * (R-WF3), [availPx] = chỗ dành được cho nó, [needAt] = bề rộng phải dành ([needPx]) của chữ (đã chừa chữ số) ĐO LẠI ở
     * chính cỡ thử — không nội suy tuyến tính: hinting ở cỡ nhỏ lệch ~1px (QA3 — ước lượng PIL "15,2px ≥ 15px" của J1 sai).
     *
     * Vừa ở [fitPx] ⇒ [fitPx] (lớn lại khi chữ ngắn đi). Không ⇒ bậc 1/[STEPS] của [fitPx] lớn nhất còn vừa, không dưới sàn.
     * Ở sàn vẫn không vừa ⇒ [floorPx] (`…` là dấu cuối cùng — bộ giải đã tránh bố cục này nếu còn bố cục khác, luật 3 KDoc lớp).
     * [needAt] phải đơn điệu theo cỡ (chữ to hơn không hẹp hơn) — tìm nhị phân, ≤ 5 lần đo.
     */
    fun valuePx(fitPx: Float, floorPx: Float, availPx: Int, needAt: (Float) -> Int): Float {
        if (fitPx <= floorPx || needAt(fitPx) <= availPx) return fitPx
        var lo = ceil(floorPx / fitPx * STEPS).toInt().coerceIn(1, STEPS)
        var hi = STEPS - 1
        var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (needAt(fitPx * mid / STEPS) <= availPx) { best = mid; lo = mid + 1 } else hi = mid - 1
        }
        return if (best < 0) floorPx else fitPx * best / STEPS
    }

    /** Bề rộng tối thiểu (px) của chú thích cỡ [textPx] mà còn hiện (`…`) — dưới đó ẩn ([CAPTION_MIN_EM]). */
    fun captionMinPx(textPx: Float): Int = ceil((CAPTION_MIN_EM * textPx).toDouble()).toInt()

    /**
     * Soát QA4 (P3) — khe NHÌN THẤY tối thiểu giữa giá trị và chú thích trên hàng ngang, theo em của chú thích (≈ một dấu cách).
     * [ĐO máy ảo QA4] hàng vừa khít ⇒ `08:3104/10`, `—km/h` (chú thích bắt đầu ngay pixel kế giá trị). [ĐỀ XUẤT, owner chốt].
     */
    const val GAP_EM = 0.25f

    /**
     * Phần dành THÊM trong bề rộng giá trị cho khe [GAP_EM] (px) của chú thích cỡ [captionPx]: chữ giá trị căn GIỮA ô của nó
     * (`WidgetViews.tv` — `Gravity.CENTER`) ⇒ chỉ NỬA phần dành nằm phía chú thích ⇒ dành gấp đôi khe.
     */
    fun gapPx(captionPx: Float): Int = ceil((2 * GAP_EM * captionPx).toDouble()).toInt()

    /** Chia một hàng NGANG — [share]. [valueW] = bề rộng TĨNH của giá trị (px); [captions] = chú thích còn hiện không. */
    data class Share(val valueW: Int, val captions: Boolean)

    /**
     * Chia phần hàng co giãn [flexPx] (hàng trừ lề + icon + con cỡ cố định) giữa GIÁ TRỊ (cần [valueNeed]) và chú thích (cần
     * tổng [captionNeed]; [captionMin] = ít nhất để còn hiện — [captionMinPx]; ô không có chú thích đang hiện ⇒ cả hai = 0).
     * Soát QA4: khe [gapPx] (= `gapPx(cỡ chú thích)`) được giữ TRONG bề rộng giá trị TRƯỚC khi chú thích nhận chỗ — không bao giờ
     * đặt chú thích sát giá trị (chú thích không có chữ ⇒ không giữ khe):
     *  - đủ chỗ cho cả hai + khe ⇒ giá trị = nhu cầu + khe + NỬA phần dư (chữ cách mép đều như bản chia đôi cũ khi ô rộng);
     *  - chú thích còn ≥ [captionMin] sau khe ⇒ giá trị = nhu cầu + khe, chú thích nhận phần còn lại (`…`);
     *  - còn lại (kể cả giá trị cần hơn cả hàng) ⇒ chú thích ẨN, giá trị nhận cả hàng.
     */
    fun share(flexPx: Int, valueNeed: Int, captionNeed: Int, captionMin: Int, gapPx: Int): Share {
        val flex = flexPx.coerceAtLeast(0)
        val gap = if (captionNeed > 0) gapPx.coerceAtLeast(0) else 0
        val rest = flex - valueNeed - gap
        return when {
            rest >= 0 && rest >= captionNeed -> Share(valueNeed + gap + (rest - captionNeed) / 2, captions = true)
            rest >= 0 && rest >= captionMin -> Share(valueNeed + gap, captions = true)
            else -> Share(flex, captions = false)
        }
    }

    /**
     * Soát vòng 6 (P3) — đổ tại chỗ (nhịp xe/đồng hồ) của một giá trị KHÔNG có bề rộng tĩnh (khối DỌC, lưới không đọc được):
     * chữ mới còn vừa ở cỡ đang có [nowPx] trong [availPx] ⇒ GIỮ cỡ (`true`) — ngắn đi KHÔNG lớn lại, cùng luật [wouldClip] của
     * hàng ngang (hết nhảy cỡ 99 ↔ 100 km/h mỗi nhịp); lượt khớp kế (không phải nhịp) mới lớn lại. Không vừa ⇒ `false` ⇒ co
     * theo [valuePx].
     */
    fun holds(nowPx: Float, availPx: Int, needAt: (Float) -> Int): Boolean = nowPx > 0f && needAt(nowPx) <= availPx

    /**
     * Soát vòng 6 (P3) — chữ trợ năng của GIÁ TRỊ khi chú thích của nó NHƯỜNG (view 0×0 ⇒ `isVisibleToUser = false` — TalkBack và
     * `uiautomator` bỏ qua nút ấy, chỉ còn đọc `100` / `—`): TÊN datum ([names] — `Fan level`) trước, rồi [value], rồi đơn vị
     * ([units] — `km/h`), cách nhau một dấu cách; phần rỗng bỏ qua.
     */
    fun spoken(value: CharSequence, units: List<CharSequence>, names: List<CharSequence>): String =
        (names + value + units).map { it.toString().trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    /**
     * Đổ tại chỗ (nhịp xe/đồng hồ): giá trị mới cần [need] (đã chừa chữ số) mà chỗ đang dành là [allocated] ⇒ sẽ bị cắt ⇒ chia
     * lại hàng (MỘT lượt đo). `allocated < 0` = giá trị không có bề rộng tĩnh (khối dọc) ⇒ không áp. Ngắn đi thì KHÔNG chia lại
     * (giữ chỗ — hết nhảy chữ qua lại), lượt khớp kế tự thu.
     */
    fun wouldClip(allocated: Int, need: Int): Boolean = allocated in 0 until need

    /**
     * Khối DỌC (giá trị trên chú thích): tổng chiều cao nội dung [contentPx] (tính cả chú thích, mọi lề) vượt khung [boxPx]
     * ⇒ chú thích nhường (ẩn) — giá trị ở giữa giữ trọn thay vì cả khối tràn và bị khung cắt cả icon lẫn chú thích. Sai số 1px
     * như mọi phép kiểm tràn ([FitRules.spills]).
     */
    fun stackYields(contentPx: Int, boxPx: Int): Boolean = contentPx > boxPx + 1
}
