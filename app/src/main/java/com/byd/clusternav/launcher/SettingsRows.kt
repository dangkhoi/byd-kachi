package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ KachiUi (qua SettingsRows) — BỘ DỰNG COMPONENT CHUẨN của launcher ══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-design-system.html`. **Một nơi duy nhất** dựng mọi phần tử giao diện của Settings:
 * tiêu đề nhóm · ô tick · hàng chip/đơn vị · dòng chú thích · nút · hàng quyền. Ba thứ nó thi hành:
 *  1. **Type scale** — mọi cỡ chữ qua [KachiType] (không `setTextSize` số tay), nên chữ theo bậc, hết "lộn xộn".
 *  2. **Lề STACK** — mỗi component tự mang lề ngoài qua `layoutParams` ([stackLp]); chỗ gọi chỉ `addView(...)`,
 *     KHÔNG tự chèn khoảng cách. Đây là cái chữa "các hàng/thẻ DÍNH vào nhau": trước đây [checkRow] là thẻ có
 *     nền nhưng không có lề ngoài ⇒ hai thẻ sát 0px; [permissionRow] lại có lề ⇒ không nhất quán. Nay một luật.
 *  3. **Style nhất quán** — checkbox/nút/chip có MỘT hình dạng, chỉ đổi tô/màu theo trạng thái (không đổi hình).
 *
 * ## Lớp này KHÔNG giữ trạng thái dùng chung
 * Mỗi hàng tự giữ trạng thái hiển thị trong closure và báo ra bằng lambda. Không có bảng tra `id → view` (cố ý —
 * xem KDoc lịch sử RW0/[CapabilityGridSection]).
 */
class SettingsRows(private val context: Context) {

    /**
     * Lề STACK chuẩn cho một phần tử trong cột dọc của Settings. Gap giữa hàng = [KachiSpace.S]. [topGap] thêm
     * lề trên [KachiSpace.L] cho tiêu đề nhóm (ranh giới) để nó tách khỏi nhóm phía trên. [wrapWidth] cho phần tử
     * gói theo nội dung (nút) — vẫn lấy lề từ ĐÂY, để khe stack chỉ khai một chỗ.
     *
     * ⚠ Mọi bề mặt Settings là `LinearLayout` dọc ⇒ dùng `LinearLayout.LayoutParams` an toàn. Chỗ gọi KHÔNG được
     * truyền lp riêng khi `addView` (sẽ ghi đè lề này) — `SettingsStackMarginContractTest` canh đúng điều đó ở cả
     * hai chiều: mọi hàm dựng công khai ở đây phải tự đặt `layoutParams`, và không chỗ gọi nào được truyền lp.
     */
    private fun stackLp(topGap: Boolean = false, wrapWidth: Boolean = false) =
        LinearLayout.LayoutParams(
            if (wrapWidth) ViewGroup.LayoutParams.WRAP_CONTENT else LinearLayout.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).also {
            if (topGap) it.topMargin = dpi(context, Sp.L)
            it.bottomMargin = dpi(context, Sp.S)
        }

    /** Tiêu đề nhóm — bậc [KachiType.SECTION] đậm + màu sáng [KachiTheme.INK] để NỔI hơn nội dung (bậc BODY). */
    fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.SECTION, bold = true)
        letterSpacing = 0.06f
        layoutParams = stackLp(topGap = true)
    }

    // ── Hàng dùng chung: ô tick + dãy chip ────────────────────────────────────────────────────────

    /**
     * NHÃN của một hàng "nhãn trái · điều khiển phải" — một chỗ duy nhất cho [chipRow] và [unitRow].
     * `minWidth` [KachiSpace.LABEL_COL] (không `weight`) — lý do ở KDoc hằng đó.
     */
    private fun rowLabel(text: String): TextView = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY)
        minWidth = dpi(context, Sp.LABEL_COL)
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    /** Ô tick + tiêu đề + dòng phụ. Checkbox: hình vuông bo [KachiSpace.RADIUS_S] CỐ ĐỊNH — bật = tô, tắt = viền. */
    fun checkRow(on: Boolean, title: String, sub: String, onChange: (Boolean) -> Unit): View {
        val box = TextView(context).apply {
            KachiType.apply(this, KachiType.BODY, bold = true); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpi(context, Sp.ICON_M), dpi(context, Sp.ICON_M))
        }
        var state = on
        fun paint() {
            box.text = if (state) "✓" else ""
            box.setTextColor(c(KachiTheme.ON_ACCENT))
            box.background = GradientDrawable().apply {
                cornerRadius = dpi(context, Sp.RADIUS_S).toFloat()
                if (state) setColor(c(KachiTheme.GRAD_FROM))
                else { setColor(c(KachiTheme.CLEAR)); setStroke(dpi(context, Sp.STROKE), c(KachiTheme.MUT2)) }
            }
        }
        paint()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = KachiTheme.card(context, Sp.RADIUS_L, KachiTheme.FIELD)
            val p = dpi(context, Sp.M); setPadding(p, p, p, p)
            layoutParams = stackLp()
            addView(box)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpi(context, Sp.M), 0, 0, 0)
                addView(TextView(context).apply {
                    text = title; setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY)
                })
                addView(TextView(context).apply {
                    text = sub; setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { state = !state; paint(); onChange(state) }
        }
    }

    /**
     * Một hàng: nhãn trái + dãy chip chọn phải. Dùng chung cho hình nền + các lựa chọn segmented.
     * Nhãn dùng [KachiSpace.LABEL_COL] làm bề rộng tối thiểu (không `weight` — xem KDoc hằng).
     */
    fun chipRow(label: String, options: List<Pair<String, String>>, current: String,
                onPick: (String) -> Unit): View {
        val chips = HashMap<String, TextView>()
        var chosen = current
        fun paint() = chips.forEach { (code, tv) ->
            val on = code == chosen
            tv.setTextColor(c(if (on) KachiTheme.ON_ACCENT else KachiTheme.MUT))
            tv.background = if (on) KachiTheme.gradient(context, Sp.RADIUS_PILL) else KachiTheme.card(context, Sp.RADIUS_PILL, KachiTheme.CHIP_OFF)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = stackLp()
            addView(rowLabel(label))
            options.forEach { (code, text) -> chips[code] = addChip(this, text) { chosen = code; paint(); onPick(code) } }
            paint()
        }
    }

    /**
     * Một chip trong dãy segmented — hình viên thuốc [KachiSpace.RADIUS_PILL] CỐ ĐỊNH, chữ [KachiType.CAPTION]
     * đậm. Rút ra dùng chung cho [chipRow] và [unitRow] để chip hai chỗ **giống hệt** (trước đây hai chỗ dựng
     * `TextView` riêng với padding lệch nhau).
     */
    private fun addChip(row: LinearLayout, text: String, onTap: () -> Unit): TextView {
        val tv = TextView(context).apply {
            this.text = text; KachiType.apply(this, KachiType.CAPTION, bold = true); gravity = Gravity.CENTER
            setPadding(dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S))
            // ⚠ [SOÁT UI 2026-09-12] Bề rộng tối thiểu = đích chạm: chip nhãn 1-2 ký tự ("m"/"ft"/"°C") không có
            // minWidth thì bo-tròn-tuyệt-đối biến nó thành HÌNH TRÒN, lạc khỏi họ pill của các chip dài. minWidth
            // [KachiSpace.TOUCH] vừa giữ dáng viên thuốc vừa đạt đích chạm tối thiểu.
            // minWidth [KachiSpace.TOUCH]: chip 1-2 ký tự không thành hình tròn. minHeight [KachiSpace.ICON_XL]:
            // chip CHỌN (nền gradient) và chip TẮT (nền card) cùng chiều cao — trước đây lệch ~4px làm mép hàng
            // răng cưa, vì hai drawable nền đo khác nhau; ép cùng minHeight thì đồng cao bất kể nền.
            minWidth = dpi(context, Sp.TOUCH)
            minHeight = dpi(context, Sp.ICON_XL)
            setOnClickListener { onTap() }
        }
        row.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .also { it.marginStart = dpi(context, Sp.S) })
        return tv
    }

    // ── P8 · một hàng cho mỗi quyền còn thiếu ─────────────────────────────────────────────────────────
    /** Nói rõ **thiếu cái gì** và **mất gì**. KHÔNG chỉ tới màn cài đặt hệ thống ([ĐO] khoá trên xe). */
    fun permissionRow(req: LauncherRequirement, rep: PermissionReport): View {
        val hint = when {
            req in rep.selfFixable -> context.getString(R.string.kachi_perm_self_fixing)
            req.displayUserAction != null -> req.displayUserAction
            req in rep.environment -> context.getString(R.string.kachi_perm_environment)
            else -> null
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = KachiTheme.card(context, Sp.RADIUS_L, KachiTheme.FIELD)
            val p = dpi(context, Sp.M); setPadding(p, p, p, p)
            layoutParams = stackLp()
            addView(TextView(context).apply {
                text = if (req.coreFeature) context.getString(R.string.kachi_perm_core, req.displayLabel)
                    else req.displayLabel
                setTextColor(c(if (req.coreFeature) KachiTheme.AMBER else KachiTheme.INK))
                KachiType.apply(this, KachiType.BODY)
            })
            addView(TextView(context).apply {
                text = context.getString(R.string.kachi_perm_loses, req.displayLoses)
                setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
            })
            if (hint != null) addView(TextView(context).apply {
                text = hint; setTextColor(c(KachiTheme.MUT2)); KachiType.apply(this, KachiType.CAPTION)
            })
        }
    }

    // ── R11–R13 · chọn ĐƠN VỊ theo LOẠI đại lượng ─────────────────────────────────────────────────────
    /** Một hàng cho mỗi loại đại lượng ĐANG DÙNG; chip dùng chung [addChip] với [chipRow]. */
    fun unitRow(q: Quantity, current: String, onPick: (String) -> Unit): View {
        val chips = HashMap<String, TextView>()
        var chosen = current
        fun paint() = chips.forEach { (code, tv) ->
            val on = code == chosen
            tv.setTextColor(c(if (on) KachiTheme.ON_ACCENT else KachiTheme.MUT))
            tv.background = if (on) KachiTheme.gradient(context, Sp.RADIUS_PILL)
            else KachiTheme.card(context, Sp.RADIUS_PILL, KachiTheme.CHIP_OFF)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = stackLp()
            addView(rowLabel(q.displayLabel))
            Units.options(q).forEach { opt -> chips[opt.code] = addChip(this, opt.code) { chosen = opt.code; paint(); onPick(opt.code) } }
            paint()
        }
    }

    // ── Hàng dùng chung: dòng chữ + nút bấm ───────────────────────────────────────────────────────
    /** Dòng CHÚ THÍCH mờ ([KachiType.CAPTION]) — nói một sự thật, KHÔNG phải điều khiển (không bấm, không hứa). */
    fun note(text: String) = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
        setLineSpacing(0f, 1.15f)
        layoutParams = stackLp()
    }

    /** Nút PHỤ — viên thuốc viền mảnh, chữ [KachiType.BODY], đích chạm ≥ [KachiSpace.TOUCH]. */
    fun button(text: String, onClick: () -> Unit): View = TextView(context).apply {
        this.text = text
        setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY)
        setPadding(dpi(context, Sp.L), dpi(context, Sp.S), dpi(context, Sp.L), dpi(context, Sp.S))
        minHeight = dpi(context, Sp.TOUCH)
        background = GradientDrawable().apply {
            cornerRadius = Sp.dpf(context, Sp.RADIUS_XL)
            setColor(c(KachiTheme.CARD2))
            setStroke(dpi(context, Sp.HAIRLINE), c(KachiTheme.LINE))
        }
        // Lề ngoài để nút không dính hàng trên; bề rộng gói theo chữ (không kéo dài hết hàng). Lề lấy từ [stackLp]
        // ⇒ khe stack của Settings khai ĐÚNG MỘT chỗ (trước đây hàm này chép lại `bottomMargin = Sp.S` lần thứ hai).
        layoutParams = stackLp(wrapWidth = true)
        setOnClickListener { onClick() }
    }
}
