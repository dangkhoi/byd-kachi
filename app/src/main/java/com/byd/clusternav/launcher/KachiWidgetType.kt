package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.widget.ImageView
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ VAI CHỮ + ĐIỂM NHẤN BÊN TRONG WIDGET MÀN NHÀ (3.01 R22 — owner 13/10 *"nhìn thô lắm"*) ═══════════════════
 *
 * [KachiType] sở hữu **cỡ** (5 bậc của giao diện chung); tệp này sở hữu **nét + mực** của nội dung widget — tức thứ
 * làm số liệu đọc ra "sang" hay "thô". Một nguồn cho mọi bộ dựng widget (ô nhóm [GroupTileView], ô telemetry
 * [WidgetTelemetry], ô đọc [ReadTile]) ⇒ không widget nào tự chọn nét riêng.
 *
 * ## [ĐO ảnh 13/10] vì sao "thô"
 * Mọi số đều **đậm** (`DEFAULT_BOLD`) — số chính 34sp, số phụ 19sp, cả đơn vị dính trong chuỗi số (`18.2 kWh` đậm cả
 * `kWh`) ⇒ không có chỗ cho mắt dừng; ô con là phiến tối riêng ⇒ lưới hộp chồng hộp; đầu ô là icon xám 16dp + chữ
 * xám ⇒ không có danh tính. Hướng owner chỉ (Kim Launcher, 13/10): nét MẢNH cho số lớn, nét VỪA cho số phụ, đơn vị
 * nhỏ + lùi màu, nhãn nhỏ mờ, nhiều khoảng thở, ít màu.
 *
 * ## Vai
 *  • [hero]   số chính lớn — nét **light**, chữ số đều bề rộng (`tnum`) để số nhảy 1 Hz không làm cụm rung ngang.
 *  • [value]  số phụ — nét **medium** + `tnum`; đơn vị trong chuỗi lùi bằng [withUnit].
 *  • [unit]   đơn vị đứng riêng cạnh số chính — nét thường, [KachiTheme.MUT].
 *  • [caption] nhãn dưới số / nhãn ô con — nét thường, [KachiTheme.MUT], giãn chữ nhẹ.
 *  • [title]  tiêu đề ô — nét **medium**, [KachiTheme.INK2] (trước: `MUT2` 12sp — đầu ô chìm hơn cả nội dung).
 *
 * ## Chi phí
 * Ba `Typeface` tạo MỘT lần (lazy, dùng chung — `Typeface` bất biến nên chia sẻ an toàn). [withUnit] chỉ cấp phát khi
 * chỗ gọi đổi chữ (chỗ gọi so chuỗi trước — xem [CellBinder]); 0 vẽ thêm, 0 luồng, 0 nhịp.
 */
object KachiWidgetType {

    private val LIGHT: Typeface by lazy { Typeface.create("sans-serif-light", Typeface.NORMAL) }
    private val MEDIUM: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    /** Nét số chính cho chữ vẽ Canvas (vòng đo [RingView]) — cùng nét với [hero] của TextView. */
    val heroFace: Typeface get() = LIGHT

    /** Chữ số đều bề rộng (OpenType `tnum`, Roboto có) — số đổi mỗi giây không làm cụm số/đơn vị nhảy ngang. */
    const val TABULAR = "tnum"

    /**
     * Tỉ lệ cỡ đơn vị so với số trong cùng chuỗi ([withUnit]). Brief Kim gợi ý 0,55× — ở số phụ 15sp thế là 8sp, dưới sàn
     * đọc-được-lúc-lái (chú thích nhỏ nhất của thang là 12sp) ⇒ 0,72× (≈ 11sp). Đơn vị của SỐ CHÍNH thì đứng riêng ở
     * [UNIT_SP] (≈ 0,35× số chính).
     */
    private const val UNIT_SCALE = 0.72f

    // ── Cỡ (sp) — brief Kim §2.4: số lớn light 44–52 · giá trị medium 14–16 · nhãn regular 13 · tiêu đề medium 14–15 ──
    /**
     * Số chính của thẻ (nét LIGHT). Brief gợi ý 44–52 cho thẻ 324 dp của Kim; ô 2×3 của Kachi chỉ ~200 dp ⇒ 40 (46 [ĐO] đẩy
     * mục Odo của Chuyến đi ra ngoài). [FitScale] vẫn thu nhỏ khi ô chật — đây là cỡ GỐC.
     */
    const val HERO_SP = 40f
    /** Đơn vị đứng cạnh số chính. */
    const val UNIT_SP = 16f
    /** Số phụ (hàng nhãn · số). */
    const val VALUE_SP = 16f
    /** Nhãn hàng / nhãn ô con. */
    const val LABEL_SP = 13f
    /** Tiêu đề ô (cạnh chip icon). */
    const val TITLE_SP = 14.5f

    /** Giãn chữ của nhãn/caption (em) — nhẹ, để chữ nhỏ thoáng mà không thành "chữ rải". */
    const val CAPTION_TRACKING = 0.03f

    fun hero(tv: TextView, color: String = KachiTheme.INK) {
        tv.typeface = LIGHT; tv.fontFeatureSettings = TABULAR; tv.setTextColor(c(color))
    }

    /** Số phụ nét LIGHT + `tnum` (R22·B — số `%` cạnh hình xe: "light numerals" như Kim). Màu do chỗ gọi. */
    fun light(tv: TextView) {
        tv.typeface = LIGHT; tv.fontFeatureSettings = TABULAR
    }

    fun value(tv: TextView) {
        tv.typeface = MEDIUM; tv.fontFeatureSettings = TABULAR
    }

    fun unit(tv: TextView) {
        tv.typeface = Typeface.DEFAULT; tv.setTextColor(c(KachiTheme.MUT))
    }

    fun caption(tv: TextView) {
        tv.typeface = Typeface.DEFAULT; tv.setTextColor(c(KachiTheme.MUT)); tv.letterSpacing = CAPTION_TRACKING
    }

    fun title(tv: TextView) {
        tv.typeface = MEDIUM; tv.setTextColor(c(KachiTheme.INK2)); tv.letterSpacing = 0f
    }

    /**
     * `số đơn-vị` với đơn vị nhỏ hơn ([UNIT_SCALE]) và lùi màu [KachiTheme.MUT]. Không đơn vị ⇒ trả nguyên [number].
     *
     * ⚠ Đo bề rộng ([GroupCellGrid] gọi `paint.measureText(text)`) bỏ qua span ⇒ đơn vị được đo ở cỡ ĐẦY ⇒ chỗ chừa
     * cho cột số chỉ có thể **dư**, không bao giờ thiếu (số không bao giờ bị cắt — R20 giữ nguyên).
     */
    fun withUnit(number: String, unit: String): CharSequence {
        if (unit.isEmpty()) return number
        val s = SpannableString("$number $unit")
        val from = number.length
        s.setSpan(RelativeSizeSpan(UNIT_SCALE), from, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(c(KachiTheme.MUT)), from, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    /**
     * Mực điểm nhấn của một lĩnh vực (ARGB đục) — lấy từ CHÍNH sắc lĩnh vực của bảng ([KachiTheme.domainTint], cửa tra
     * duy nhất), bỏ kênh trong suốt. Bảng TỐI: màu ngữ nghĩa sáng (`green`/`cyan`…), bảng SÁNG: bản sẫm ⇒ mỗi bảng tự
     * đúng chiều tương phản, không mở bảng màu thứ hai. `null`/không sắc ⇒ [KachiTheme.ACCENT_INK].
     */
    fun accentOf(domain: Domain?): Int {
        val tint = KachiTheme.domainTint(domain)
        val base = if (tint == KachiTheme.CLEAR) KachiTheme.ACCENT_INK else tint
        return ColorMath.withAlpha(c(base), 0xff)
    }

    /** Nền chip icon đầu ô: mực nhấn ở [CHIP_ALPHA] — một vệt màu nhỏ thay cho cả thẻ bị nhuộm (brief Kim §2.3). */
    fun chip(ctx: Context, accent: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = Sp.dpf(ctx, Sp.RADIUS_S)
        setColor(ColorMath.withAlpha(accent, CHIP_ALPHA))
    }

    /** Icon trong chip: tô mực nhấn, lề trong [Sp.XS]. */
    fun chipIcon(iv: ImageView, accent: Int) {
        iv.setColorFilter(accent)
        val p = KachiTheme.dpi(iv.context, Sp.HEAD_CHIP_PAD); iv.setPadding(p, p, p, p)
        iv.background = chip(iv.context, accent)
    }

    /**
     * 3.01 R22 · B — MỘT luật cho chữ GIỮ CHỖ (`—` chưa đọc được): tô bằng vai [KachiTheme.MUT], **không bao giờ hạ
     * `alpha`**. [ĐO] `widget-visual-301` §6: mực đầy × alpha 0,5 ra **3,15:1** (sáng) / 4,72 (tối) — dưới sàn 4,5; còn
     * `MUT` là vai nhãn phụ mà `SurfaceContrastContractTest` đã khoá ≥ 4,5:1 trên mặt thẻ ở CẢ hai bảng. "Mờ" nói bằng
     * vai màu đã đo, không bằng phép nhân độ đục chưa ai đo. [ink] = màu khi đã có số.
     */
    fun pending(tv: TextView, available: Boolean, ink: String = KachiTheme.INK) {
        tv.alpha = 1f
        tv.setTextColor(c(if (available) ink else KachiTheme.MUT))
    }

    /** Alpha nền chip (`0x26` = 15 %). Đồ hoạ (icon) trên chip phải ≥ 3:1 — đo ở `widget-visual-301`. */
    private const val CHIP_ALPHA = 0x26
}
