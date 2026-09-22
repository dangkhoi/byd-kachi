package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.byd.clusternav.launcher.CapabilityDots

/**
 * ═══ ICON DATUM CÓ TRẠNG THÁI — dùng CHUNG header chip · widget · picker (owner 2026-09-22) ══════════════════════
 *
 * Vẽ MỘT icon `ic-*` + (nếu có mức) một hàng CHẤM dưới, coi như một phần của icon: icon thu nhỏ + đẩy lên chừa
 * chỗ cho chấm. Owner: *"cái nào có mức 1-2 thì chấm dưới, đẹp hơn số"* (ghế mát/sưởi off/mức1/mức2, kính 50%=1
 * mức, mở-all=2 mức).
 *
 * Trạng thái (owner):
 *  • [maxLevel] = 0 · [active] on/off ⇒ chỉ icon, TÔ MÀU active (accent) / inactive (mờ) — không chấm.
 *  • [maxLevel] ≥ 1 ⇒ icon + [maxLevel] chấm; [level] chấm đầu TÔ ĐẦY (active), còn lại rỗng (viền). `level=0` =
 *    tắt ⇒ icon mờ + mọi chấm rỗng.
 *
 * Màu lấy từ [KachiTheme] (theo palette) ⇒ tương phản đúng ở CẢ light lẫn dark (R3), không hardcode hex.
 */
class DatumIconView(context: Context) : View(context) {

    private var icon: Drawable? = null
    private var maxLevel = 0
    private var level = 0
    private var active = false

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /**
     * @param iconName tên `ic-*` (một nguồn — [CapabilityDots.iconOverride] đã áp trước khi truyền vào).
     * @param maxLevel số chấm (0 = không mức). @param level mức hiện tại (0..maxLevel). @param active on/off khi maxLevel=0.
     */
    fun set(iconName: String, maxLevel: Int, level: Int, active: Boolean) {
        val res = KachiTheme.iconRes(iconName)
        icon = if (res != 0) ContextCompat.getDrawable(context, res)?.mutate() else null
        this.maxLevel = maxLevel.coerceAtLeast(0)
        this.level = level.coerceIn(0, this.maxLevel)
        this.active = active || this.level > 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val d = icon ?: return
        val w = width; val h = height
        // Có mức ⇒ chừa DẢI CHẤM ở đáy (≈22% chiều cao), icon đẩy lên phần trên. Không mức ⇒ icon giữa.
        val dotBand = if (maxLevel >= 1) (h * 0.24f).toInt() else 0
        val iconH = h - dotBand
        val side = minOf(w, iconH)
        val left = (w - side) / 2
        val top = (iconH - side) / 2
        // ⚠ Owner 2026-09-22: active/inactive phải "nhìn phát biết ngay". Nên chênh lệch MẠNH, không chỉ đổi alpha:
        //  • ACTIVE   = nền TRÒN accent mờ (glow) SAU icon + icon tô ACCENT đậm (alpha 255).
        //  • INACTIVE = KHÔNG nền + icon tô MUT2 alpha THẤP (mờ hẳn) ⇒ tương phản rõ ở cả light lẫn dark.
        if (active) {
            bgPaint.color = KachiTheme.c(KachiTheme.ACCENT); bgPaint.alpha = 46   // ~18% — vệt accent đủ thấy, không chói
            canvas.drawCircle((left + side / 2f), (top + side / 2f), side * 0.62f, bgPaint)
        }
        DrawableCompat.setTint(d, KachiTheme.c(if (active) KachiTheme.ACCENT else KachiTheme.MUT2))
        d.alpha = if (active) 255 else 90
        d.setBounds(left, top, left + side, top + side)
        d.draw(canvas)
        if (maxLevel < 1) return
        // Hàng chấm dưới: maxLevel chấm, `level` chấm đầu tô đầy (accent), còn lại viền mờ.
        val r = (dotBand * 0.28f).coerceAtMost(side * 0.13f)
        val gap = r * 1.6f
        val totalW = maxLevel * (2 * r) + (maxLevel - 1) * gap
        var cx = (w - totalW) / 2f + r
        val cy = iconH + dotBand / 2f
        val accent = KachiTheme.c(KachiTheme.ACCENT); val mut = KachiTheme.c(KachiTheme.MUT2)
        for (i in 0 until maxLevel) {
            if (i < level) {
                dotPaint.color = accent; dotPaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, r, dotPaint)
            } else {
                ringPaint.color = mut; ringPaint.strokeWidth = r * 0.5f
                canvas.drawCircle(cx, cy, r * 0.85f, ringPaint)
            }
            cx += 2 * r + gap
        }
    }
}
