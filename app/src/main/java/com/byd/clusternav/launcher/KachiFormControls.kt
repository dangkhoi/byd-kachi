package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ KACHI-301 R11/R12 — điều khiển CHỌN trong thân hộp Biểu mẫu (chip · chip tròn · thẻ chọn · công tắc) ═════════
 *
 * Hai hộp *Địa chỉ* và *Lịch dẫn đường* cùng cần chip bật/tắt, thẻ chọn một và hàng công tắc — trong **thân hộp
 * thoại** chứ không trong trang Cài đặt. [SettingsRows.chipRow]/[SettingsRows.checkRow] không dùng được: chúng mang
 * lề stack + cột nhãn của trang (sai ngữ cảnh trong hộp). Tệp này là bản gọn cho hộp, lấy **đúng** nền của chip
 * trang Cài đặt (`gradient` khi bật) và của ô tick (`GRAD_FROM` / `FIELD_SUNKEN`) — không mở một ngôn ngữ hình thứ hai.
 * Nền TẮT (chip · thẻ · hàng công tắc) = [KachiDialogSupport.controlFill] (QA-301: đo tách khỏi thẻ hộp ở cả hai bảng;
 * `CHIP_OFF` trùng đỉnh thẻ hộp ở bảng Tối, `card()` mặc định trùng thẻ hộp ở bảng Sáng).
 * Đích chạm ≥ [Sp.TOUCH] (R8). Không viền (WP1 · R1.1): bật/tắt phân biệt bằng FILL.
 */
internal object KachiFormControls {

    /** Một chip bật/tắt: [paint] tô lại theo trạng thái; nội dung + việc chạm do chỗ gọi đặt. */
    class Chip(val view: TextView) {
        var on: Boolean = false
            set(value) { field = value; paint() }

        fun paint() {
            val ctx = view.context
            view.setTextColor(c(if (on) KachiTheme.ON_ACCENT else KachiTheme.INK))
            view.background = if (on) KachiTheme.gradient(ctx, Sp.RADIUS_PILL)
            else KachiTheme.card(ctx, Sp.RADIUS_PILL, KachiDialogSupport.controlFill())
        }
    }

    /** Chip viên thuốc (nhãn nhanh · mốc giờ · app dẫn đường). [icon] = icon app (có màu riêng, không tô). */
    fun chip(context: Context, text: CharSequence, icon: Drawable? = null, onTap: () -> Unit): Chip {
        val tv = TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            maxLines = 1
            KachiType.apply(this, KachiType.BODY, bold = true)
            minHeight = dpi(context, Sp.TOUCH)
            minWidth = dpi(context, Sp.CHIP_MIN_W)
            setPadding(dpi(context, Sp.L), dpi(context, Sp.XS), dpi(context, Sp.L), dpi(context, Sp.XS))
            icon?.let {
                val s = dpi(context, Sp.ICON_S)
                it.setBounds(0, 0, s, s)
                setCompoundDrawablesRelative(it, null, null, null)
                compoundDrawablePadding = dpi(context, Sp.S)
            }
            contentDescription = text
            setOnClickListener { onTap() }
        }
        return Chip(tv).also { it.paint() }
    }

    /** Chip TRÒN [Sp.TOUCH]² (thứ trong tuần T2…CN). */
    fun roundChip(context: Context, text: CharSequence, onTap: () -> Unit): Chip {
        val tv = TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            maxLines = 1
            KachiType.apply(this, KachiType.CAPTION, bold = true)
            // Cỡ bằng min* (không layoutParams cố định): [flow] đặt WRAP_CONTENT cho mọi con — vuông TOUCH² + nền
            // viên thuốc ⇒ hình tròn.
            minWidth = dpi(context, Sp.TOUCH)
            minHeight = dpi(context, Sp.TOUCH)
            setPadding(0, 0, 0, 0)
            contentDescription = text
            setOnClickListener { onTap() }
        }
        return Chip(tv).also { it.paint() }
    }

    /** Dãy chip tự xuống dòng khi tràn ([ChipFlowLayout], khe [Sp.S]). */
    fun flow(context: Context, chips: List<Chip>): View = ChipFlowLayout(context, dpi(context, Sp.S)).apply {
        chips.forEach {
            addView(it.view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** Nhãn mục trong thân hộp (chú thích đậm, [KachiTheme.MUT]) — "nhãn TRÊN ô" của khuôn Biểu mẫu (R9). */
    fun heading(context: Context, text: CharSequence): TextView = KachiDialogs.caption(context, text, bold = true)

    /** Thẻ CHỌN MỘT (điểm đến của lịch): tiêu đề + dòng phụ; [SelectCard.on] ⇒ bề mặt ACTIVE + dấu ✓. */
    class SelectCard(val view: LinearLayout, private val check: TextView) {
        var on: Boolean = false
            set(value) {
                field = value
                view.background = if (value) KachiTheme.surface(view.context, Sp.RADIUS_L, SurfaceTone.ACTIVE)
                else KachiTheme.card(view.context, Sp.RADIUS_L, KachiDialogSupport.controlFill())
                check.visibility = if (value) View.VISIBLE else View.INVISIBLE
            }
    }

    fun selectCard(context: Context, title: CharSequence, sub: CharSequence?, onTap: () -> Unit): SelectCard {
        val check = TextView(context).apply {
            text = GLYPH_CHECK
            setTextColor(c(KachiTheme.ACCENT_INK))
            KachiType.apply(this, KachiType.BODY, bold = true)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dpi(context, Sp.TOUCH)
            setPadding(dpi(context, Sp.L), dpi(context, Sp.S), dpi(context, Sp.L), dpi(context, Sp.S))
            isClickable = true
            contentDescription = title
            setOnClickListener { onTap() }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = title
                    maxLines = 1
                    setTextColor(c(KachiTheme.INK))
                    KachiType.apply(this, KachiType.BODY, bold = true)
                })
                sub?.takeIf { it.isNotBlank() }?.let {
                    addView(KachiDialogs.caption(context, it).apply { maxLines = 1 })
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(check)
        }
        return SelectCard(row, check).also { it.on = false }
    }

    /** Hàng CÔNG TẮC (ô tick vuông + chữ) — cùng nền ô tick của [SettingsRows.checkRow]. */
    class Toggle(val view: View, private val box: TextView) {
        var on: Boolean = false
            set(value) {
                field = value
                box.text = if (value) GLYPH_CHECK else ""
                box.background = GradientDrawable().apply {
                    cornerRadius = dpi(box.context, Sp.RADIUS_S).toFloat()
                    setColor(c(if (value) KachiTheme.GRAD_FROM else dialogBoxOff(KachiTheme.palette)))
                }
            }
    }

    fun toggle(context: Context, title: CharSequence, initial: Boolean, onChange: () -> Unit): Toggle {
        val box = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(c(KachiTheme.ON_ACCENT))
            KachiType.apply(this, KachiType.BODY, bold = true)
        }
        lateinit var t: Toggle
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dpi(context, Sp.TOUCH)
            setPadding(dpi(context, Sp.M), dpi(context, Sp.XS), dpi(context, Sp.M), dpi(context, Sp.XS))
            background = KachiTheme.card(context, Sp.RADIUS_L, KachiDialogSupport.controlFill())
            isClickable = true
            contentDescription = title
            addView(box, LinearLayout.LayoutParams(dpi(context, Sp.ICON_M), dpi(context, Sp.ICON_M)))
            addView(TextView(context).apply {
                text = title
                setTextColor(c(KachiTheme.INK))
                KachiType.apply(this, KachiType.BODY)
                setPadding(dpi(context, Sp.M), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { t.on = !t.on; onChange() }
        }
        t = Toggle(row, box).also { it.on = initial }
        return t
    }

    /** Dấu "đang chọn" — ký hiệu, không phải chữ cần dịch. */
    private const val GLYPH_CHECK = "✓"
}
