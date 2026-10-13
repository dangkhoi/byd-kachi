package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ 3.01 R22·B — BẢNG ICON của ô nhóm ([GroupLayout.ICONS]; owner 13/10: *"lưới nút chữ lặp nhìn rối"*) ═══════════
 *
 * Mỗi vật MỘT ô icon ([GroupIconBoard] gộp ô đọc + nút cùng hình): CHỈ icon nét mảnh (R22·R owner 13/10 "giảm text"),
 * tên ở `contentDescription`. **Đang bật** = nền nhấn MỀM (15 %) + icon/chữ màu nhấn — một
 * màu nhấn duy nhất cho "bật" (Kim); cảnh báo dùng màu cảnh báo. Ô CÓ nút: nền mực rất nhạt (8 %, R22·R — không viền) cho biết chạm
 * được + sóng chạm; chạm = `performClick()` ô nút THẬT ([MatrixKit.tile] → [ControlTileFactory], cùng đường ghi HAL, cùng
 * chốt chống-bấm-kép). Ô chỉ-xem: không nền, không sóng.
 *
 * Lưới do `:core` chọn ([GroupIconBoard.grid]: ô vuông nhất mà cạnh ngắn ≥ đích chạm); không chia được ⇒ báo cao tối
 * thiểu để khung ngoài lùi ô tóm tắt. Không `if` theo mã nhóm/đèn. Chi phí: dựng một lần; [refresh] chỉ đổi màu khi
 * trạng thái ĐỔI.
 */
@SuppressLint("ViewConstructor")
internal class IconBoardView(context: Context, private val tiles: List<IconTile>, data: WidgetData) : ViewGroup(context), GroupBody {

    override val consumed: Set<String> = tiles.mapNotNull { it.cell?.id }.toSet()

    private val kit = MatrixKit(context, data)
    private val touch = kit.px(Sp.TOUCH)
    private val gap = kit.px(Sp.XS)
    private val accent = c(KachiTheme.ACCENT_INK)

    private inner class Tile(val t: IconTile) {
        var cell: GroupCell? = t.cell
        val hidden: View? = t.action?.let { a -> kit.tile(a, null, icon = false).also { it.visibility = View.GONE } }
        val icon = ImageView(context).apply {
            val r = KachiTheme.iconRes(t.icon); if (r != 0) setImageResource(r)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val caption = TextView(context).apply {
            text = t.name; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiWidgetType.LABEL_SP - 1f)
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(gap, 0, gap, 0)
            addView(icon, LinearLayout.LayoutParams(kit.px(Sp.ICON_M), kit.px(Sp.ICON_M)))
            addView(caption, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also { it.topMargin = gap })
            contentDescription = t.name
            if (hidden != null) {
                isClickable = true
                setOnClickListener { hidden.performClick(); show() }
            }
        }
        private var shown: Int? = null

        /** Sáng khi ô đọc khác NEUTRAL; ô chỉ-có-nút sáng theo lệnh cuối ([ControlTileState]). Chỉ vẽ lại khi ĐỔI. */
        fun show() {
            val tone = cell?.tone
            val lit = if (cell != null) tone != GroupTone.NEUTRAL else t.action?.let { ControlTileState.shared.isOn(it.id) } == true
            val ink = when {
                tone == GroupTone.WARN || tone == GroupTone.ALERT -> c(GroupTileView.tintOf(tone))
                lit -> accent
                else -> 0
            }
            val key = if (lit) ink else 0
            if (key == shown) return
            shown = key
            val r = Sp.dpf(context, Sp.RADIUS_M)
            val base = GradientDrawable().apply {
                cornerRadius = r
                // R22·R: ô bấm được = nền mực rất nhạt, KHÔNG viền tóc (ZeroBorder — owner 20/09 "không còn viền ở bất cứ đâu").
                setColor(
                    when {
                        lit -> ColorMath.withAlpha(ink, GLOW_ALPHA)
                        t.action != null -> ColorMath.withAlpha(c(KachiTheme.ICON), WASH_ALPHA)
                        else -> Color.TRANSPARENT
                    },
                )
            }
            root.background = if (t.action == null) base else {
                val mask = GradientDrawable().apply { cornerRadius = r; setColor(c(KachiTheme.INK)) }   // mặt nạ: chỉ alpha có nghĩa
                RippleDrawable(ColorStateList.valueOf(ColorMath.withAlpha(accent, RIPPLE_ALPHA)), LayerDrawable(arrayOf(base)), mask)
            }
            icon.setColorFilter(if (lit) ink else c(KachiTheme.ICON))
            // Chữ ô sáng = MỰC (không màu nhấn): [ĐO] bảng sáng, mực nhấn trên nền nhấn 15 % chỉ 3,44–3,77:1. "Bật" đã nói
            // bằng nền + icon nhấn; chữ chỉ cần đọc được.
            caption.setTextColor(c(if (lit) KachiTheme.INK else KachiTheme.MUT))
        }
    }

    private val views = tiles.map { Tile(it) }
    private var cols = 1
    private var rows = 1

    init {
        views.forEach { v -> v.hidden?.let { addView(it) }; addView(v.root); v.show() }
    }

    override fun refresh(m: GroupBoardModel) {
        val byId = m.cells.associateBy { it.id }
        views.forEach { v -> v.cell?.id?.let { id -> byId[id]?.let { v.cell = it } }; v.show() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val unbounded = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
        val h = if (unbounded) Int.MAX_VALUE / 2 else MeasureSpec.getSize(heightMeasureSpec)
        val g = GroupIconBoard.grid(views.size, w + gap, h + gap, touch + gap)
        // Không chia được ⇒ báo cao tối thiểu (một cột ô đích chạm) để khung ngoài thấy tràn và lùi ô tóm tắt.
        cols = g?.first ?: maxOf(1, (w + gap) / (touch + gap))
        rows = g?.second ?: ((views.size + cols - 1) / cols)
        val tw = (w - (cols - 1) * gap) / cols
        val th = if (g != null) (h - (rows - 1) * gap) / rows else touch
        val cellH = minOf(th, maxOf(touch, tw))   // ô không cao quá bề ngang (vuông vừa mắt); dư thì canh giữa ở onLayout
        // R22·R (owner 13/10: *"giảm text đi em, làm các icon … chữ nó làm xấu lắm"*): CHỈ icon ở mọi cỡ — mỗi đèn đã có
        // hình riêng (U7); tên đầy đủ ở `contentDescription` (trợ năng). Bản R22·B (1–2 dòng chữ khi đủ chỗ) đã thôi.
        // Chỉ icon ⇒ icon lớn theo ô (42 % cạnh, kẹp 24–44 dp) — ô to không còn là hộp rỗng với icon tí hon.
        val iconPx = (minOf(tw, cellH) * ICON_OF_CELL).toInt().coerceIn(kit.px(Sp.ICON_M), kit.px(Sp.ICON_XL))
        views.forEach { v ->
            v.caption.visibility = View.GONE
            v.icon.layoutParams.let { lp -> if (lp.width != iconPx) { lp.width = iconPx; lp.height = iconPx } }
            v.root.measure(MeasureSpec.makeMeasureSpec(tw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY))
        }
        val need = rows * cellH + (rows - 1) * gap
        setMeasuredDimension(w, if (unbounded || g == null) need else h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l; val h = b - t
        val tw = views.first().root.measuredWidth; val th = views.first().root.measuredHeight
        val y0 = ((h - (rows * th + (rows - 1) * gap)) / 2).coerceAtLeast(0)
        views.forEachIndexed { i, v ->
            val row = i / cols; val col = i % cols
            // Hàng cuối ngắn ⇒ canh giữa (không để ô trống lẻ bám trái — cùng luật lưới ô con R22).
            val inRow = if (row == rows - 1) views.size - row * cols else cols
            val x0 = (w - (inRow * tw + (inRow - 1) * gap)) / 2
            val x = x0 + col * (tw + gap); val y = y0 + row * (th + gap)
            v.root.layout(x, y, x + tw, y + th)
        }
    }

    private companion object {
        /** Nền "đang bật": mực nhấn 15 % — vệt sáng mềm, không phiến đặc. */
        const val GLOW_ALPHA = 0x26
        /** Nền ô bấm được: mực icon 8 % (thay viền tóc 20 % — ZeroBorder). */
        const val WASH_ALPHA = 0x14
        const val ICON_OF_CELL = 0.42f
        const val RIPPLE_ALPHA = 0x33
    }
}
