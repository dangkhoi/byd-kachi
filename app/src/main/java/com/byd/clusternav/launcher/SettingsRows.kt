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
import com.byd.clusternav.launcher.KachiSpace as Sp

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

    // ⚠ [SOÁT G1] `fun px(v: Int)` đã XOÁ ở đây: nó là một hàm đổi dp **thứ hai** mang tên khác, nên bốn số trần
    // truyền vào nó (14 · 9 · 20 · 1) lọt qua `SpacingScaleContractTest` — bài đó chỉ soi `dp(`/`dpi(`. Một thang
    // thì phải có một cửa vào; mọi chỗ nay dùng `KachiTheme.dpi` / `KachiSpace.dpf`.

    fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.MUT2)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        letterSpacing = 0.05f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dpi(context, Sp.M), 0, dpi(context, Sp.S))
    }

    // ── Hàng dùng chung: ô tick + dãy chip ────────────────────────────────────────────────────────

    /**
     * NHÃN của một hàng "nhãn trái · điều khiển phải" — **một chỗ duy nhất** cho cả [chipRow] và [unitRow].
     *
     * Rút ra thay vì dựng `TextView` ở hai chỗ với hai bộ số đo: hai bản sao là cách bề rộng cột nhãn lệch nhau
     * giữa trang "Hiển thị & đơn vị" và trang "Màn hình chính", và mắt đọc ra sự lệch đó ngay.
     *
     * `minWidth` (không phải `weight`, không phải bề rộng cố định) — lý do đầy đủ ở KDoc [KachiSpace.LABEL_COL].
     */
    private fun rowLabel(text: String): TextView = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        minWidth = dpi(context, Sp.LABEL_COL)
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    /** Ô tick + tiêu đề + dòng phụ. Rút ra dùng chung cho hình nền và tiện nghi (trước đó chỉ có một chỗ dựng tay). */
    fun checkRow(on: Boolean, title: String, sub: String, onChange: (Boolean) -> Unit): View {
        val box = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpi(context, Sp.ICON_M), dpi(context, Sp.ICON_M))
        }
        var state = on
        fun paint() {
            box.text = if (state) "✓" else ""
            box.setTextColor(Color.WHITE)
            box.background = GradientDrawable().apply {
                cornerRadius = dpi(context, Sp.RADIUS_S).toFloat()
                if (state) setColor(c(KachiTheme.ACCENT))
                else { setColor(c("#00000000")); setStroke(dpi(context, Sp.STROKE), c(KachiTheme.MUT2)) }
            }
        }
        paint()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = KachiTheme.card(context, Sp.RADIUS_L, "#161b24")
            val p = dpi(context, Sp.M); setPadding(p, p, p, p)
            addView(box)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpi(context, Sp.M), 0, 0, 0)
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

    /**
     * Một hàng: nhãn bên trái + dãy chip chọn bên phải. Dùng chung cho đơn vị và hình nền.
     *
     * ⚠ Nhãn dùng **[KachiSpace.LABEL_COL] làm bề rộng tối thiểu**, KHÔNG dùng `weight = 1f`. Xem KDoc của hằng đó:
     * `weight` làm nhãn ăn hết chỗ trống và đẩy chip sang mép phải, [ĐO] cách nhau tới **1064px** trên khung nội
     * dung ~950dp ⇒ không đọc ra điều khiển nào thuộc nhãn nào.
     */
    fun chipRow(label: String, options: List<Pair<String, String>>, current: String,
                onPick: (String) -> Unit): View {
        val chips = HashMap<String, TextView>()
        var chosen = current
        fun paint() = chips.forEach { (code, tv) ->
            val on = code == chosen
            tv.setTextColor(if (on) Color.WHITE else c(KachiTheme.MUT))
            tv.background = if (on) KachiTheme.gradient(context, Sp.RADIUS_PILL) else KachiTheme.card(context, Sp.RADIUS_PILL, "#1a1f29")
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpi(context, Sp.S), 0, dpi(context, Sp.S))
            addView(rowLabel(label))
            options.forEach { (code, text) ->
                val tv = TextView(context).apply {
                    this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f); typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S))
                    setOnClickListener { chosen = code; paint(); onPick(code) }
                }
                chips[code] = tv
                addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .also { it.marginStart = dpi(context, Sp.S) })
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
            background = KachiTheme.card(context, Sp.RADIUS_L, "#161b24")
            val p = dpi(context, Sp.M); setPadding(p, p, p, p)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dpi(context, Sp.S); layoutParams = lp
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
            tv.background = if (on) KachiTheme.gradient(context, Sp.RADIUS_PILL)
            else KachiTheme.card(context, Sp.RADIUS_PILL, "#1a1f29")
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpi(context, Sp.S), 0, dpi(context, Sp.S))
            addView(rowLabel(q.label))
            Units.options(q).forEach { opt ->
                val tv = TextView(context).apply {
                    text = opt.code; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f); typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(dpi(context, Sp.L), dpi(context, Sp.S), dpi(context, Sp.L), dpi(context, Sp.S))
                    setOnClickListener { chosen = opt.code; paint(); onPick(opt.code) }
                }
                chips[opt.code] = tv
                addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .also { it.marginStart = dpi(context, Sp.S) })
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
        setPadding(0, 0, 0, dpi(context, Sp.S))
    }

    /**
     * Nút bấm dạng viên thuốc viền mảnh.
     *
     * ⚠ [SOÁT G1] Trước lượt soát này, hàm dùng một helper RIÊNG `px(...)` với bốn số trần (14 · 9 · 20 · 1) — và
     * `SpacingScaleContractTest` **không thấy** chúng, vì nó chỉ soi lời gọi tên `dp(`/`dpi(`. Tức R4 (*"mọi số dp
     * đi qua KachiSpace"*) bị lách bằng cách đặt tên khác cho hàm đổi dp. Nay đi qua thang, `px()` đã XOÁ, và bài
     * canh đã được nới để bắt **mọi** hàm đổi dp (xem `dpHelperNames`).
     */
    fun button(text: String, onClick: () -> Unit): View = TextView(context).apply {
        this.text = text
        setTextColor(c(KachiTheme.INK))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(dpi(context, Sp.L), dpi(context, Sp.S), dpi(context, Sp.L), dpi(context, Sp.S))
        // Nút này mở bảng vẽ bố cục — một đích chạm thật, nên phải đạt mức tối thiểu.
        minHeight = dpi(context, Sp.TOUCH)
        background = GradientDrawable().apply {
            cornerRadius = Sp.dpf(context, Sp.RADIUS_XL)
            setColor(c(KachiTheme.CARD2))
            setStroke(dpi(context, Sp.HAIRLINE), c(KachiTheme.LINE))
        }
        setOnClickListener { onClick() }
    }
}
