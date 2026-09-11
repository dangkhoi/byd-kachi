package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi

/**
 * BỘ DỰNG DÒNG dùng chung cho mọi bề mặt cấu hình của launcher (S1 · T2).
 *
 * Chuyển **nguyên văn** từ bảng "Tuỳ biến" cũ (đã xoá ở S1·T5) — cùng số đo, cùng màu, cùng cỡ
 * chữ, cùng câu chữ. Lý do tách: màn Cài đặt (S1) cần đúng những dòng này ở nhiều nhóm khác nhau; để lại trong bảng
 * cũ thì hoặc phải gọi ngang vào một `FrameLayout` (kéo theo cả bảng chỉ để mượn một hàng), hoặc phải chép lại — mà
 * chép lại chính là chỗ sinh ra **ngưỡng lốp thứ ba** và **hai bảng màu** mà dự án vừa phải đi dọn.
 *
 * ## Lớp này KHÔNG giữ trạng thái dùng chung
 * Mỗi hàng tự giữ trạng thái hiển thị của RIÊNG nó trong closure (`state` của ô tick, `chosen` của dãy chip) và báo
 * ra ngoài bằng lambda. Không có bảng tra `id → view` nào ở đây — cố ý.
 *
 * ## ⚠ Bẫy đã trả giá: ô lưới khả năng KHÔNG thuộc về đây
 * Ô của lưới khả năng có **bảng tra `tiles[id]`** để tô lại nền khi bật/tắt, nên nó bị ràng buộc
 * *"một lưới = một bảng tiles"* và nằm ở [CapabilityGridSection] chứ không ở lớp này. [ĐO] 2026-09-11: phiên trước
 * dùng lại hàm dựng-ô của bảng "Tuỳ biến" cũ cho lưới chọn chip và sinh **ba lỗi cùng lúc** — sự kiện bấm bắn vào
 * thanh nút,
 * `tiles[id]` bị ghi đè vì cùng một mã có ở hai lưới, hai chỗ tô nền tranh nhau (xem [TopStripPicker.section]).
 * Đừng thêm hàm dựng-ô-có-bảng-tra vào lớp này: hàm ở đây phải **không khoá** để dùng lại ở đâu cũng an toàn.
 */
class SettingsRows(private val context: Context) {

    fun px(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.MUT2)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        letterSpacing = 0.05f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dpi(context, 12), 0, dpi(context, 6))
    }

    // ── Hàng dùng chung: ô tick + dãy chip ────────────────────────────────────────────────────────
    /** Ô tick + tiêu đề + dòng phụ. Rút ra dùng chung cho hình nền và tiện nghi (trước đó chỉ có một chỗ dựng tay). */
    fun checkRow(on: Boolean, title: String, sub: String, onChange: (Boolean) -> Unit): View {
        val box = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpi(context, 26), dpi(context, 26))
        }
        var state = on
        fun paint() {
            box.text = if (state) "✓" else ""
            box.setTextColor(Color.WHITE)
            box.background = GradientDrawable().apply {
                cornerRadius = dpi(context, 7).toFloat()
                if (state) setColor(c(KachiTheme.ACCENT))
                else { setColor(c("#00000000")); setStroke(dpi(context, 2), c(KachiTheme.MUT2)) }
            }
        }
        paint()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = KachiTheme.card(context, 14f, "#161b24")
            val p = dpi(context, 12); setPadding(p, p, p, p)
            addView(box)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpi(context, 12), 0, 0, 0)
                addView(TextView(context).apply {
                    text = title; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                })
                addView(TextView(context).apply {
                    text = sub; setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { state = !state; paint(); onChange(state) }
        }
    }

    /** Một hàng: nhãn bên trái + dãy chip chọn bên phải. Dùng chung cho đơn vị và hình nền. */
    fun chipRow(label: String, options: List<Pair<String, String>>, current: String,
                onPick: (String) -> Unit): View {
        val chips = HashMap<String, TextView>()
        var chosen = current
        fun paint() = chips.forEach { (code, tv) ->
            val on = code == chosen
            tv.setTextColor(if (on) Color.WHITE else c(KachiTheme.MUT))
            tv.background = if (on) KachiTheme.gradient(context, 999f) else KachiTheme.card(context, 999f, "#1a1f29")
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpi(context, 5), 0, dpi(context, 5))
            addView(TextView(context).apply {
                text = label; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            options.forEach { (code, text) ->
                val tv = TextView(context).apply {
                    this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f); typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(dpi(context, 12), dpi(context, 6), dpi(context, 12), dpi(context, 6))
                    setOnClickListener { chosen = code; paint(); onPick(code) }
                }
                chips[code] = tv
                addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .also { it.marginStart = dpi(context, 6) })
            }
            paint()
        }
    }

    // ── P8 · một hàng cho mỗi quyền còn thiếu ─────────────────────────────────────────────────────────
    /**
     * Nói rõ **thiếu cái gì** và **mất gì**, kèm việc cần làm nếu người dùng phải tự làm.
     * KHÔNG chỉ tới màn cài đặt hệ thống — [ĐO] màn đó bị khoá trên xe.
     */
    fun permissionRow(req: LauncherRequirement, rep: PermissionReport): View {
        val hint = when {
            req in rep.selfFixable -> "Kachi đang tự xin lại — không cần làm gì"
            req.userAction != null -> req.userAction
            req in rep.environment -> "Hạn chế của môi trường, không phải lỗi của app"
            else -> null
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = KachiTheme.card(context, 14f, "#161b24")
            val p = dpi(context, 12); setPadding(p, p, p, p)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dpi(context, 6); layoutParams = lp
            addView(TextView(context).apply {
                text = if (req.coreFeature) "${req.label} — ảnh hưởng tính năng chính" else req.label
                setTextColor(c(if (req.coreFeature) KachiTheme.AMBER else KachiTheme.INK))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            })
            addView(TextView(context).apply {
                text = "Thiếu thì: ${req.losesWhatIfMissing}"
                setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
            if (hint != null) addView(TextView(context).apply {
                text = hint; setTextColor(c(KachiTheme.MUT2)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
        }
    }

    // ── R11–R13 · chọn ĐƠN VỊ theo LOẠI đại lượng ─────────────────────────────────────────────────────
    /**
     * Một hàng cho mỗi loại đại lượng ĐANG DÙNG ([UnitFormat.quantitiesInUse] — không bày loại không có mục nào),
     * mỗi hàng là dãy lựa chọn bấm chọn. Chọn theo LOẠI (7 hàng) chứ không theo từng mục (61 mục) — R11.
     */
    fun unitRow(q: Quantity, current: String, onPick: (String) -> Unit): View {
        val chips = HashMap<String, TextView>()
        var chosen = current
        fun paint() = chips.forEach { (code, tv) ->
            val on = code == chosen
            tv.setTextColor(if (on) Color.WHITE else c(KachiTheme.MUT))
            tv.background = if (on) KachiTheme.gradient(context, 999f)
            else KachiTheme.card(context, 999f, "#1a1f29")
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpi(context, 5), 0, dpi(context, 5))
            addView(TextView(context).apply {
                text = q.label; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            Units.options(q).forEach { opt ->
                val tv = TextView(context).apply {
                    text = opt.code; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f); typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(dpi(context, 14), dpi(context, 6), dpi(context, 14), dpi(context, 6))
                    setOnClickListener { chosen = opt.code; paint(); onPick(opt.code) }
                }
                chips[opt.code] = tv
                addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .also { it.marginStart = dpi(context, 6) })
            }
            paint()
        }
    }

    // ── Hàng dùng chung: dòng chữ + nút bấm ───────────────────────────────────────────────────────
    /**
     * Dòng CHỮ mờ — nói một sự thật, **không phải điều khiển**: không bấm được, không hứa gì.
     *
     * Dùng ở 5 chỗ (câu mô tả bố cục đang dùng · hiện trạng giao diện sáng/tối · "đủ quyền" · lời dẫn sang màn
     * ClusterNav · phần Giới thiệu) nên rút về đây thay vì dựng `TextView` tại 5 chỗ với 5 bộ số đo hơi khác nhau —
     * đúng lỗi đã sinh ra "hai bảng màu" và "ngưỡng lốp thứ ba".
     */
    fun note(text: String) = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        setLineSpacing(0f, 1.15f)
        setPadding(0, 0, 0, dpi(context, 8))
    }

    /** Nút bấm dạng viên thuốc viền mảnh — số đo chuyển **nguyên văn** từ nút "Vẽ bố cục riêng…" của bảng cũ. */
    fun button(text: String, onClick: () -> Unit): View = TextView(context).apply {
        this.text = text
        setTextColor(c(KachiTheme.INK))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(px(14), px(9), px(14), px(9))
        background = GradientDrawable().apply {
            cornerRadius = px(20).toFloat()
            setColor(c(KachiTheme.CARD2))
            setStroke(px(1), c(KachiTheme.LINE))
        }
        setOnClickListener { onClick() }
    }
}
