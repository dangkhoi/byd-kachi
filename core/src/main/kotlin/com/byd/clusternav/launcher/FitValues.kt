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
     * Khe [GAP_EM] (px) giữa giá trị và chú thích cỡ [captionPx]. 2.93 `FIT-GRAVITY`: khe là LỀ NGOÀI có chủ của giá trị (phía
     * chú thích — `FitValueRow.gapMargin`), KHÔNG còn dành trong bề rộng giá trị. Bản QA4 dành ⌈2 × 0,25 em⌉ trong bề rộng
     * vì chữ giá trị căn GIỮA ⇒ nửa phần dành nằm phía NGOÀI (phí ≈ 0,25 em mỗi hàng ở khung chật) và khe nhìn thấy phụ thuộc
     * cách căn của bộ dựng (căn trái/phải ⇒ 0…2 × khe). Lề ngoài ⇒ khe nhìn thấy ≥ một khe với mọi cách căn.
     */
    fun gapPx(captionPx: Float): Int = ceil((GAP_EM * captionPx).toDouble()).toInt()

    /** Chia một hàng NGANG — [share]. [valueW] = bề rộng TĨNH của giá trị (px); [captions] = chú thích còn hiện không. */
    data class Share(val valueW: Int, val captions: Boolean)

    /**
     * Chia phần hàng co giãn [flexPx] (hàng trừ lề + icon + con cỡ cố định) giữa GIÁ TRỊ (cần [valueNeed]) và chú thích (cần
     * tổng [captionNeed]; [captionMin] = ít nhất để còn hiện — [captionMinPx]; ô không có chú thích đang hiện ⇒ cả hai = 0).
     * Soát QA4: khe [gapPx] (= `gapPx(cỡ chú thích)`) được giữ TRƯỚC khi chú thích nhận chỗ — không bao giờ đặt chú thích sát giá
     * trị (chú thích không có chữ ⇒ không giữ khe). 2.93 `FIT-GRAVITY`: khe nằm NGOÀI [Share.valueW] (lề ngoài có chủ, giữa giá
     * trị và chú thích) ⇒ chú thích nhận `flex − valueW − khe`:
     *  - đủ chỗ cho cả hai + khe ⇒ giá trị = nhu cầu + NỬA phần dư (chữ cách mép đều như bản chia đôi cũ khi ô rộng);
     *  - chú thích còn ≥ [captionMin] sau khe ⇒ giá trị = đúng nhu cầu, chú thích nhận phần còn lại (`…`);
     *  - còn lại (kể cả giá trị cần hơn cả hàng) ⇒ chú thích ẨN (không khe), giá trị nhận cả hàng.
     */
    fun share(flexPx: Int, valueNeed: Int, captionNeed: Int, captionMin: Int, gapPx: Int): Share {
        val flex = flexPx.coerceAtLeast(0)
        val gap = if (captionNeed > 0) gapPx.coerceAtLeast(0) else 0
        val rest = flex - valueNeed - gap
        return when {
            rest >= 0 && rest >= captionNeed -> Share(valueNeed + (rest - captionNeed) / 2, captions = true)
            rest >= 0 && rest >= captionMin -> Share(valueNeed, captions = true)
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

    /**
     * 2.93 `FIT-REGROW` — đường LỚN LẠI của luật "nhịp chỉ co" ([holds] · [wouldClip]): ô bị CO ở một nhịp đổ tại chỗ lúc
     * [shrunkAtMs] (`null` = không) ⇒ sau [waitMs] ([FitRules.RECHECK_MS]) chạy MỘT lượt luật giá trị không-phải-nhịp (tick =
     * false) trên chữ đang hiện. [ĐO mã, soát 7] tốc độ dò ở `99` → nhịp `100` co cỡ → về `99` (dấu chữ trùng dấu đã dò ⇒
     * không lượt dò lại) ⇒ `99` đứng mãi ở cỡ của `100`. Không cần hẹn giờ: chỗ gọi hỏi ở nhịp đổ 1 Hz sẵn có; lượt khớp đầy
     * đủ cũng là một lượt lớn lại ⇒ đặt dấu theo [regrowNext]. Chỉ mỹ quan — giá trị không bao giờ bị cắt ở cả hai nhánh.
     */
    fun regrowDue(shrunkAtMs: Long?, nowMs: Long, waitMs: Long = FitRules.RECHECK_MS): Boolean =
        shrunkAtMs != null && nowMs - shrunkAtMs >= waitMs

    /**
     * 2.93 wave 2A · FIT-REGROW — dấu "đã co ở nhịp" SAU một lượt luật giá trị KHÔNG-phải-nhịp (lượt lớn lại [regrowDue], hoặc
     * lượt khớp đủ): ô đã về ĐẦY ([full] — `FitScale.full`: mọi chữ ở cỡ LƯỚI gốc × k, hàng ngang không chú thích nào nhường —
     * [roomy]) ⇒ `null` (xong); CHƯA ⇒ hẹn lại từ [nowMs]. Nhịp đổ tại chỗ chỉ CO ([holds] · [wouldClip]) ⇒ ô chưa đầy mà xoá dấu
     * là chữ hẹp đến sau đứng mãi ở cỡ của chữ rộng.
     *  - review WIDGET Pass 1 [P3]: tới hạn đúng lúc chữ lại là `100` ⇒ lượt ấy không đổi gì — bản đầu XOÁ dấu;
     *  - review wave 2A Pass 1 ghi chú (b) [P3]: lượt ấy lớn MỘT PHẦN (co ở `1000`, tới hạn gặp `100`) — bản "đổi ⇒ xong" vẫn
     *    XOÁ dấu ⇒ `99` đến sau đứng ở cỡ của `100`. Nay hỏi "đã về cỡ ĐẦY chưa", không hỏi "có đổi không".
     * Giá: ô không bao giờ đầy (khung chật, lưới không đọc được) ⇒ một lượt luật giá trị (không lượt đo view; không đổi gì ⇒
     * không `requestLayout`) mỗi [FitRules.RECHECK_MS] ở nhịp 1 Hz sẵn có — không hẹn giờ mới. Chỉ mỹ quan.
     */
    fun regrowNext(full: Boolean, nowMs: Long): Long? = if (full) null else nowMs

    /**
     * 2.93 wave 2A · FIT-REGROW lớn MỘT PHẦN — phân chia [s] của một hàng NGANG (kết quả [share], hoặc chỗ đang GIỮ sau nhịp —
     * [wouldClip]) để chú thích đang hiện nhận TRỌN nhu cầu [captionNeed] sau giá trị + khe [gapPx] (= nhánh 1 của [share]); không
     * chú thích có nhu cầu ⇒ `true`. `false` = chú thích đang nhường (ẩn, hoặc chỉ còn chỗ cho `…`) ⇒ giá trị hẹp hơn đến sau còn
     * trả chỗ lại được, mà nhịp không chia lại khi chữ ngắn đi ⇒ dấu lớn lại phải giữ ([regrowNext]).
     */
    fun roomy(s: Share, flexPx: Int, captionNeed: Int, gapPx: Int): Boolean =
        captionNeed <= 0 || (s.captions && flexPx.coerceAtLeast(0) - s.valueW - gapPx.coerceAtLeast(0) >= captionNeed)

    /**
     * 2.93 `FIT-WIDEST-CACHE` — nhớ CHỮ SỐ RỘNG NHẤT ([widestDigit]) theo khoá bút [K] (phông · cỡ · giãn chữ · ngôn ngữ…):
     * [ĐO mã, soát 6] mỗi lần đo bề rộng một giá trị tốn 10 `measureText` một ký tự + 10 chuỗi chỉ để biết chữ số nào rộng
     * nhất — kết quả CHỈ phụ thuộc bút, không phụ thuộc chữ. LRU [capacity] khoá (cỡ chữ của một lưới chỉ có vài bậc 1/32).
     * Không đa luồng (tầng vẽ gọi trên luồng chính). [misses] = số lần thật sự phải đo — bài kiểm đếm.
     */
    class WidestMemo<K>(private val capacity: Int = 64) {
        private val map = object : LinkedHashMap<K, Char>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Char>?): Boolean = size > capacity
        }
        var misses = 0
            private set

        fun get(key: K, width: (Char) -> Float): Char = map[key] ?: widestDigit(width).also { map[key] = it; misses++ }
    }

    /**
     * 2.93 `FIT-WIDEST-CACHE` — chuỗi đệm [headroom] của lần gọi GẦN NHẤT: một lượt [valuePx] đo cùng chữ ở ≤ 5 cỡ thử ⇒ dựng
     * chuỗi đệm một lần (chữ số rộng nhất thường trùng giữa các bậc cỡ của cùng phông). So nội dung, không so tham chiếu.
     */
    class HeadroomMemo {
        private var text: String? = null
        private var widest = ' '
        private var out = ""

        fun of(t: CharSequence, w: Char): String {
            val s = t.toString()
            if (s != text || w != widest) { text = s; widest = w; out = headroom(s, w) }
            return out
        }
    }
}
