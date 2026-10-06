package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * ═══ 2.93 `WF-MEDIA-SMALL` — khung của widget NHẠC đơn: bỏ phần phụ trước, giữ ba nút cỡ chạm ═══════════════════════════
 *
 * Quyết định ở `:core` [MediaFit] (test thuần: mức nội dung × cách xếp × `k`); lớp này đo + đặt các con ĐÃ DỰNG MỘT LẦN
 * (không tháo/gắn view — nút không bao giờ mất cú bấm giữa chừng, bài học SOÁT P1-1/C5). Áp kế hoạch = ẩn phần bị bỏ
 * (`GONE`) + đặt cỡ chữ/lề nút theo `k` — chỉ ghi khi đổi thật. Hàm đổ nhạc (`fillMedia`) không đụng hiện/ẩn hay cỡ.
 *
 * Ô tự lấp khung ⇒ `FitGridLayout.selfFitting` để nguyên (thay cho `FitScale`, vốn co cả khối chung một `k` rồi mất nút ở
 * khung nhỏ — [ĐO máy ảo QA3 04/10]). Kế hoạch đang áp ghi `adb logcat -s WidgetFit` một dòng khi đổi (CLAUDE.md §15).
 */
@SuppressLint("ViewConstructor")   // chỉ dựng bằng mã (MediaWidgetView), không từ XML
internal class MediaFitLayout(
    context: Context,
    private val art: View,
    private val title: TextView,
    private val artist: TextView,
    private val prog: View,
    private val prev: View,
    private val play: View,
    private val next: View,
    private val box: MediaFit.Box,
) : ViewGroup(context) {

    private val baseTitlePx = title.textSize
    private val baseArtistPx = artist.textSize
    private val basePad = intArrayOf(play.paddingLeft, play.paddingTop, play.paddingRight, play.paddingBottom)

    /** Kế hoạch đang áp (cho QA/bài canh). */
    var plan: MediaFit.Plan? = null
        private set
    private var key = -1 to -1

    init {
        listOf(art, title, artist, prog, prev, play, next).forEach { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        if (w <= 0 || h <= 0) return
        val p = if (key == (w to h)) plan!! else MediaFit.plan(w, h, box).also { apply(it, w, h) }
        val k = p.k
        val s = (box.btn * k).roundToInt()
        listOf(prev, play, next).forEach { it.measure(exactly(s), exactly(s)) }
        val a = (box.art * k).roundToInt()
        art.measure(exactly(a), exactly(a))
        val textW = when (p.arrange) {
            MediaFit.Arrange.STACK -> innerW(w, p)
            MediaFit.Arrange.ROW -> innerW(w, p) - rowFixed(p)
        }.coerceAtLeast(0)
        title.measure(exactly(textW), exactly((box.titleH * k).roundToInt()))
        artist.measure(exactly(textW), exactly((box.artistH * k).roundToInt()))
        val pw = if (p.arrange == MediaFit.Arrange.STACK) minOf((box.progW * k).roundToInt(), textW) else textW
        prog.measure(exactly(pw), exactly((box.progH * k).roundToInt().coerceAtLeast(1)))
    }

    /** Bề ngang dành cho nội dung: lề trong chỉ nhận phần dư (≤ [MediaFit.Box.pad] mỗi phía — luật 4 của [MediaFit]). */
    private fun innerW(w: Int, p: MediaFit.Plan): Int {
        val nat = MediaFit.natural(p.parts, p.arrange, box).first * p.k
        return (w - 2 * padOf(w, nat)).coerceAtLeast(0)
    }

    private fun padOf(size: Int, natural: Double): Int = ((size - natural) / 2).toInt().coerceIn(0, box.pad)

    /** Phần CỐ ĐỊNH của cách xếp ROW (ảnh + khe + hàng nút + khe) — cột chữ nhận phần còn lại. */
    private fun rowFixed(p: MediaFit.Plan): Int {
        val k = p.k
        val art = if (MediaFit.Part.ART in p.parts) ((box.art + box.gap) * k).roundToInt() else 0
        return art + (buttonsW(p) + box.gap * k).roundToInt()
    }

    private fun buttonsW(p: MediaFit.Plan): Double =
        if (MediaFit.Part.PREV_NEXT in p.parts) (3.0 * box.btn + 2.0 * box.btnGap) * p.k else box.btn * p.k

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val p = plan ?: return
        val w = r - l; val h = b - t; val k = p.k
        val (nw, nh) = MediaFit.natural(p.parts, p.arrange, box).let { it.first * k to it.second * k }
        val padX = padOf(w, nw); val padY = padOf(h, nh)
        fun place(v: View, x: Int, y: Int) { if (v.visibility != GONE) v.layout(x, y, x + v.measuredWidth, y + v.measuredHeight) }
        val btns = listOf(prev, play, next).filter { it.visibility != GONE }
        val gapB = (box.btnGap * k).roundToInt()
        val rowW = btns.sumOf { it.measuredWidth } + gapB * (btns.size - 1).coerceAtLeast(0)
        fun buttonsAt(x0: Int, y: Int) { var x = x0; btns.forEach { place(it, x, y); x += it.measuredWidth + gapB } }
        when (p.arrange) {
            MediaFit.Arrange.STACK -> {
                val top = ((h - nh) / 2).roundToInt().coerceAtLeast(padY)
                var y = top
                val cx = w / 2
                if (art.visibility != GONE) { place(art, cx - art.measuredWidth / 2, y); y += art.measuredHeight + (box.gap * k).roundToInt() }
                listOf(title, artist).filter { it.visibility != GONE }.forEach { place(it, padX, y); y += it.measuredHeight }
                if (prog.visibility != GONE) {
                    y += (box.progGap * k).roundToInt(); place(prog, cx - prog.measuredWidth / 2, y); y += prog.measuredHeight
                }
                if (y > top) y += (box.rowGap * k).roundToInt()   // có nội dung phía trên ⇒ khe trên hàng nút
                buttonsAt(cx - rowW / 2, y)
            }
            MediaFit.Arrange.ROW -> {
                var x = padX
                val cy = h / 2
                if (art.visibility != GONE) { place(art, x, cy - art.measuredHeight / 2); x += art.measuredWidth + (box.gap * k).roundToInt() }
                val col = listOf(title, artist, prog).filter { it.visibility != GONE }
                val colH = col.sumOf { it.measuredHeight } + if (prog.visibility != GONE) (box.progGap * k).roundToInt() else 0
                var y = cy - colH / 2
                col.forEach { v -> if (v === prog) y += (box.progGap * k).roundToInt(); place(v, x, y); y += v.measuredHeight }
                buttonsAt(w - padX - rowW, cy - (btns.firstOrNull()?.measuredHeight ?: 0) / 2)
            }
        }
    }

    /** Áp kế hoạch [p]: hiện/ẩn phần + cỡ chữ + lề nút theo `k`. Chỉ ghi khi đổi thật (setter nào cũng `requestLayout`). */
    private fun apply(p: MediaFit.Plan, w: Int, h: Int) {
        val old = plan
        plan = p; key = w to h
        show(art, MediaFit.Part.ART in p.parts)
        show(title, MediaFit.Part.TITLE in p.parts)
        show(artist, MediaFit.Part.ARTIST in p.parts)
        show(prog, MediaFit.Part.PROGRESS in p.parts)
        show(prev, MediaFit.Part.PREV_NEXT in p.parts); show(next, MediaFit.Part.PREV_NEXT in p.parts)
        textPx(title, baseTitlePx * p.k); textPx(artist, baseArtistPx * p.k)
        listOf(prev, play, next).forEach { b ->
            val q = basePad.map { (it * p.k).roundToInt() }
            if (b.paddingLeft != q[0] || b.paddingTop != q[1]) b.setPadding(q[0], q[1], q[2], q[3])
        }
        if (old?.parts != p.parts || old.arrange != p.arrange || abs(old.k - p.k) > 1e-3) {
            val parts = p.parts.joinToString("+") { it.name.lowercase() }
            val k = "%.3f".format(java.util.Locale.US, p.k)
            android.util.Log.i("WidgetFit", "media frame=${w}x$h -> ${p.arrange} k=$k parts=$parts fits=${p.fits}")
        }
    }

    private fun show(v: View, on: Boolean) {
        val want = if (on) VISIBLE else GONE
        if (v.visibility != want) v.visibility = want
    }

    private fun textPx(tv: TextView, px: Double) {
        if (abs(tv.textSize - px) > 0.01) tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px.toFloat())
    }

    private fun exactly(px: Int) = MeasureSpec.makeMeasureSpec(px.coerceAtLeast(0), MeasureSpec.EXACTLY)

    companion object {
        /** Cỡ thang 1 của khối nhạc (px) từ các hằng dp/sp của widget + đo dòng chữ thật bằng `Paint` của chính nó. */
        fun box(title: TextView, artist: TextView, dp: (Int) -> Int): MediaFit.Box {
            fun line(tv: TextView) = tv.paint.getFontMetricsInt(null) + tv.compoundPaddingTop + tv.compoundPaddingBottom
            return MediaFit.Box(
                pad = dp(KachiSpace.S), art = dp(KachiSpace.ART), gap = dp(KachiSpace.S),
                titleH = line(title), artistH = line(artist),
                textMinW = ceil(FitRules.FREE_TEXT_EM * title.textSize).toInt(),
                progW = dp(KachiSpace.PROGRESS_W), progH = dp(KachiSpace.BAR_THIN), progGap = dp(KachiSpace.SLOT_GAP),
                btn = dp(KachiSpace.TOUCH), btnGap = dp(KachiSpace.S), rowGap = dp(KachiSpace.M),
            )
        }
    }
}
