package com.byd.clusternav.launcher

/**
 * ═══ VISUAL-REFRESH P1b · §4.10 mục (2)+(5) — LỚP CHE CỦA THẺ KÍNH, CHỌN THEO ĐỘ CHÓI ĐO ĐƯỢC ══════════════════
 *
 * Owner 2026-09-16 (ảnh chụp trên xe): *"cái màu đen, xám của mình, khi nhét thêm hình nền vào, nó lại không đẹp
 * nữa"*. Thẻ P1b là **cửa sổ nhìn xuống ảnh đã làm mờ** (§4.10), và [ĐO] ở 80 % đục thì nhãn phụ `mut` chỉ còn
 * 3.15:1 trên ảnh trắng — nên §4.10 mục (5) ghi **bắt buộc**: chọn theo độ chói *đo được* của vùng ảnh dưới
 * từng thẻ, không theo chủ đề.
 *
 * ## Quyết định: đo → **đậm lớp che** (không đổi mực)
 * Mục (5) viết *"chọn MỰC theo độ chói"*, AC8.5 cho phép *"tự đậm hoá nền hoặc đổi mực"*. P1b chọn vế **nền**:
 *  • mực của một thẻ là hằng lúc dựng view (mọi `TextView` nhận `KachiTheme.MUT` khi dựng); đổi mực theo từng
 *    thẻ nghĩa là mọi view phải hỏi lại nền của mình sau layout — hàng chục chỗ, và mỗi lượt trình chiếu đổi ảnh
 *    là một lượt dựng lại. Một lớp che thì nằm **trong chính drawable** của thẻ: đo lúc layout, vẽ lúc draw,
 *    không dựng lại gì;
 *  • kết quả đọc được là **như nhau**: bài `GlassVeilTest` + `ColorChoiceContractTest` chứng minh với mọi độ
 *    chói 0..1 của ảnh, lớp che tìm được luôn đưa mực **tệ nhất** (`mut`) lên ≥ 4.5:1 trong trần [MAX].
 *
 * ## Vì sao dải 35–90 % chứ không 35–50 % như §4.10 mục (2)
 * 35–50 % là con số cho ảnh **đã làm tối 45 %** (mặc định của hình nền). Người dùng đặt "làm tối 0 %" và chọn
 * ảnh trắng ⇒ [ĐO] cần ~82 % mới giữ được `mut`. Trần 90 % để thẻ **không bao giờ** đục hẳn (ảnh vẫn lọt 10 %),
 * còn đáy 35 % để thẻ trên ảnh tối không mất chất kính. Ảnh thường (tông cát, đã làm tối) rơi vào 35–50 %.
 *
 * Thuần số học: nhận màu dưới dạng `Int` ARGB, trả alpha (0..1). Không Android.
 */
object GlassVeil {

    const val MIN = 0.35
    const val MAX = 0.90
    const val STEP = 0.05

    /**
     * Alpha nhỏ nhất trong [[MIN], [MAX]] (bước [STEP]) để **mọi** mực trong [inks] đạt [floor] trên **mọi** bề mặt
     * trong [surfaces] khi bề mặt ấy nằm trên `veil@alpha` nằm trên vùng ảnh có độ chói [artLuminance].
     *
     * Vùng ảnh được mô hình bằng **xám cùng độ chói** ([ColorMath.grayOfLuminance]) — ảnh đã làm mờ nên sắc
     * của nó gần như không còn ảnh hưởng tới tương phản chữ; thứ còn lại là độ chói, và độ chói là thứ đo được.
     *
     * @param veil màu lớp che, ĐỤC (nền màn của chủ đề — kéo nền thẻ về phía nền mà mọi mực đã được chỉnh cho).
     * @param surfaces các vai bề mặt **có alpha** (`surf*OverArt`) đặt lên trên lớp che.
     * @return alpha 0..1; [MAX] nếu tới trần vẫn chưa đạt (bài canh chấm ❌ ở ca đó).
     */
    fun alphaFor(
        artLuminance: Double,
        veil: Int,
        surfaces: IntArray,
        inks: IntArray,
        floor: Double = 4.5,
        min: Double = MIN,
        max: Double = MAX,
    ): Double {
        require(surfaces.isNotEmpty() && inks.isNotEmpty())
        val art = ColorMath.grayOfLuminance(artLuminance)
        var a = min
        while (a <= max + 1e-9) {
            if (worstRatio(a, art, veil, surfaces, inks) >= floor) return a
            a += STEP
        }
        return max
    }

    /** Tỉ số **thấp nhất** (mọi mực × mọi bề mặt) ở một alpha — con số ghi vào bảng đo. */
    fun worstRatio(alpha: Double, art: Int, veil: Int, surfaces: IntArray, inks: IntArray): Double {
        val veiled = ColorMath.over(ColorMath.withAlpha(veil, (alpha.coerceIn(0.0, 1.0) * 255).toInt()), art)
        var worst = Double.MAX_VALUE
        for (s in surfaces) {
            val ground = ColorMath.over(s, veiled)
            for (ink in inks) worst = minOf(worst, ColorMath.ratio(ink, ground))
        }
        return worst
    }
}
