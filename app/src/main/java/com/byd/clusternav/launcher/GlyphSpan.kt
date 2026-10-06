package com.byd.clusternav.launcher

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * ═══ 2.93 `CLOCK-SUN-DETACHED` — icon NẰM TRONG dòng chữ, cỡ theo cỡ chữ ═══════════════════════════════════════════════
 *
 * Bệnh [ĐO máy ảo 06/10, soát `SHORTCUT-WIDGET-292`]: dòng *"— · ngoài xe"* của widget đồng hồ mang ☀ bằng drawable ĐẦU DÒNG
 * (`setCompoundDrawablesRelative`) trên một `TextView` bề ngang `MATCH_PARENT`, chữ căn giữa ⇒ icon dính mép TRÁI của ô còn
 * chữ ở giữa — ở khung rộng/to hai thứ cách nhau cả trăm px. Không đổi sang `WRAP` (luật `FitRules.lp`: chữ `WRAP` đổi
 * chữ ⇒ `requestLayout` mỗi lần đổi — `TextView.java:9641-9692` r47).
 *
 * Cách làm: icon là MỘT KÝ TỰ của chính dòng chữ (khoảng trắng không ngắt, thay bằng span này) ⇒ cả cụm "☀ 26°C · ngoài xe"
 * căn giữa như một khối, `TextView` giữ bề ngang tĩnh. Cạnh icon + khe tính theo `textSize` LÚC ĐO ⇒ `FitScale` nhân cỡ chữ
 * × k là icon to/nhỏ theo, không cần nhánh riêng cho drawable kèm chữ. Dòng được nới (chỉ khi icon cao hơn phông) cho icon
 * không bị cắt mép trên/dưới.
 */
internal class GlyphSpan(
    private val glyph: Drawable,
    /** Cạnh icon theo em của chữ (giữ đúng tỉ lệ cũ: icon 16 dp cạnh chữ 13 sp). */
    private val sideEm: Float,
    /** Khe icon–chữ theo em (cũ: `compoundDrawablePadding` 8 dp cạnh chữ 13 sp). */
    private val gapEm: Float,
) : ReplacementSpan() {

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val side = side(paint)
        if (fm != null) {
            paint.getFontMetricsInt(fm)
            val extra = side - (fm.descent - fm.ascent)
            if (extra > 0) {
                val up = ceil(extra / 2.0).toInt()
                fm.ascent -= up; fm.descent += extra - up
                fm.top = minOf(fm.top, fm.ascent); fm.bottom = maxOf(fm.bottom, fm.descent)
            }
        }
        return side + gap(paint)
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val side = side(paint)
        val fm = paint.fontMetrics
        // Tâm DỌC của chữ (giữa ascent và descent quanh baseline [y]) — icon nằm ngang tầm chữ, không ngang đỉnh dòng.
        val t = (y + (fm.ascent + fm.descent) / 2f - side / 2f).roundToInt()
        val l = x.roundToInt()
        glyph.setBounds(l, t, l + side, t + side)
        glyph.draw(canvas)
    }

    private fun side(p: Paint): Int = (p.textSize * sideEm).roundToInt().coerceAtLeast(1)
    private fun gap(p: Paint): Int = (p.textSize * gapEm).roundToInt().coerceAtLeast(0)

    companion object {
        /** Ký tự mang icon: khoảng trắng KHÔNG NGẮT ⇒ icon không bao giờ bị tách khỏi chữ đầu khi dòng xuống hàng. */
        const val HOLDER = " "

        /** [text] có icon [span] đứng đầu (một ký tự [HOLDER]). */
        fun lead(span: GlyphSpan, text: CharSequence): CharSequence =
            SpannableString(HOLDER + text).apply { setSpan(span, 0, HOLDER.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }
}
