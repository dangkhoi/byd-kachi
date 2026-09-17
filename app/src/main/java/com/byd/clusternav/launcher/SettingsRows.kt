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
 * xem KDoc lịch sử RW0 / `CapabilityGridSection` — lưới đó đã rời khỏi Settings ở R-UI (m), tệp bị xoá).
 */
class SettingsRows(internal val context: Context) {

    /**
     * Lề STACK chuẩn cho một phần tử trong cột dọc của Settings. Gap giữa hàng = [KachiSpace.S]. [topGap] là lề
     * TRÊN (0 = không có) để một tiêu đề tách khỏi khối phía trên — [KachiSpace.L] cho tiêu đề nhóm ([sectionLabel]),
     * [KachiSpace.M] cho tiêu đề cấp hai ([subHeader]), tức **ranh giới càng lớn thì lề càng lớn**. [wrapWidth] cho
     * phần tử gói theo nội dung (nút) **và cho phần tử tự kẹp bề rộng bằng `maxWidth`** ([note] — xem cảnh báo ở
     * đó: `maxWidth` không có tác dụng dưới spec `EXACTLY` của `MATCH_PARENT`) — vẫn lấy lề từ ĐÂY, để khe stack
     * chỉ khai một chỗ.
     *
     * ⚠ Mọi bề mặt Settings là `LinearLayout` dọc ⇒ dùng `LinearLayout.LayoutParams` an toàn. Chỗ gọi KHÔNG được
     * truyền lp riêng khi `addView` (sẽ ghi đè lề này) — `SettingsStackMarginContractTest` canh đúng điều đó ở cả
     * hai chiều: mọi hàm dựng công khai ở đây phải tự đặt `layoutParams`, và không chỗ gọi nào được truyền lp.
     */
    internal fun stackLp(topGap: Int = 0, wrapWidth: Boolean = false) =
        LinearLayout.LayoutParams(
            if (wrapWidth) ViewGroup.LayoutParams.WRAP_CONTENT else LinearLayout.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).also {
            if (topGap > 0) it.topMargin = dpi(context, topGap)
            it.bottomMargin = dpi(context, Sp.S)
        }

    /** Tiêu đề nhóm (cấp 1) — bậc [KachiType.SECTION] đậm + màu sáng [KachiTheme.INK] để NỔI hơn nội dung (BODY). */
    fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.SECTION, bold = true)
        letterSpacing = 0.06f
        layoutParams = stackLp(topGap = Sp.L)
    }

    /**
     * Tiêu đề CẤP HAI trong một nhóm (IA v2 · R6) — **khác HÌNH, không khác CỠ**.
     *
     * ## Vì sao không mở một bậc chữ thứ sáu
     * [ĐO] ảnh Settings 2026-09-12: nhóm dài có **ba** cấp tiêu đề nhưng chỉ **hai** cách trình bày, nên cấp 2 và
     * cấp 3 dùng chung [KachiType.SECTION] ⇒ đọc ra một cấp phẳng. Cách chữa quen tay là thêm một bậc (SECTION2 ≈
     * 14.5) — đúng con đường đã sinh ra *"18 cỡ chữ"* mà [KachiType] lập ra để chống, và [TypeScaleContractTest]
     * `type scale co dung 5 bac` sẽ đỏ.
     *
     * Thứ bậc ở đây dựng bằng **hình**: cùng cỡ [KachiType.BODY] nhưng đậm + VIẾT HOA + giãn chữ 0.08 + màu
     * [KachiTheme.MUT]. Viết hoa cho khối chữ một hình chữ nhật đều (không có nét lên/xuống) nên mắt đọc ra "nhãn
     * phân đoạn" chứ không phải một câu; giãn chữ giữ chữ hoa không bị dính. Đặt sau [sectionLabel] (SECTION 16,
     * sáng) thì nó **thấp hơn** một cách rõ ràng dù nhỏ hơn chỉ 2.5sp. Đóng OQ4 của design system.
     *
     * Lề trên [KachiSpace.M] — nhỏ hơn [KachiSpace.L] của [sectionLabel]: ranh giới cấp 2 nông hơn ranh giới nhóm,
     * và khoảng cách chính là thứ nói ra điều đó.
     *
     * ⚠ Dùng `setAllCaps` (phép biến hình lúc VẼ) chứ không `text.uppercase()`: chuỗi gốc giữ nguyên cho trình đọc
     * màn hình và cho `text` đọc lại được, và không phụ thuộc locale mặc định của tiến trình.
     */
    fun subHeader(text: String) = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.BODY, bold = true)
        setAllCaps(true)
        letterSpacing = 0.08f
        layoutParams = stackLp(topGap = Sp.M)
    }

    // ── Hàng dùng chung: ô tick + dãy chip ────────────────────────────────────────────────────────

    /**
     * NHÃN của một hàng "nhãn trái · điều khiển phải" — một chỗ duy nhất cho [chipRow] và [unitRow].
     * `minWidth` [KachiSpace.LABEL_COL] (không `weight`) — lý do ở KDoc hằng đó.
     */
    internal fun rowLabel(text: String): TextView = TextView(context).apply {
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
            background = KachiTheme.surface(context, Sp.RADIUS_L)
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
            // ⚠ [SOÁT ẢNH 2026-09-12 · vòng 3] minWidth [KachiSpace.TOUCH] (48) KHÔNG đủ: nó thoả *đích chạm*
            // nhưng không thoả *dáng*. [ĐO] chip "m"/"ft"/"°C" ra **72×66px = tỉ lệ 1.09** ⇒ vẫn đọc ra hình
            // TRÒN, lạc khỏi họ viên thuốc của chip dài cùng hàng. Nay lấy [KachiSpace.CHIP_MIN_W] (66 = 1.5 ×
            // chiều cao chip) — con số suy từ chiều cao chứ không tự chọn, xem KDoc hằng đó.
            // minHeight [KachiSpace.ICON_XL] (44): chip CHỌN (nền gradient) và chip TẮT (nền card) cùng chiều
            // cao — trước đây lệch ~4px làm mép hàng răng cưa, vì hai drawable nền đo khác nhau. 44 là số OWNER
            // ĐÃ CHỐT bằng ảnh (design system §10 OQ3) ⇒ KHÔNG nâng lên 48 cùng họ nút; `ControlHeightContractTest`
            // ghim đúng điều đó ở cả hai chiều (nút = TOUCH · chip = ICON_XL).
            minWidth = dpi(context, Sp.CHIP_MIN_W)
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
            background = KachiTheme.surface(context, Sp.RADIUS_L)
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
    /**
     * Dòng CHÚ THÍCH mờ ([KachiType.CAPTION]) — nói một sự thật, KHÔNG phải điều khiển (không bấm, không hứa).
     *
     * `maxWidth` [KachiSpace.NOTE_MAX_W]: khung nội dung rộng ~950dp mà chú thích là `MATCH_PARENT` ⇒ [ĐO] một
     * dòng trải **1358px ≈ 150 ký tự**, quá xa khoảng đọc được 45–90 ⇒ mắt trượt dòng khi xuống hàng. Xem KDoc hằng.
     *
     * ## ⚠⚠ `maxWidth` CHỈ ăn khi bề rộng là `WRAP_CONTENT` — nên hàng này phải `wrapWidth = true`
     * [ĐO] soát ảnh Settings v2 2026-09-13: dòng chú thích vẫn trải **1377px** dù `maxWidth` đã khai từ pha 2.
     * `MATCH_PARENT` cho [TextView] một spec `EXACTLY`, và `TextView.onMeasure` chỉ kẹp theo `maxWidth` ở nhánh
     * `AT_MOST`/`UNSPECIFIED` — spec `EXACTLY` thì bề rộng do cha quyết, `maxWidth` không có cửa vào. Tức bản vá
     * pha 2 khai đúng hằng mà **không đổi một pixel nào**: đúng họ lỗi "compile xanh ≠ code chạy" (CLAUDE.md §8).
     */
    fun note(text: String) = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
        setLineSpacing(0f, 1.15f)
        maxWidth = dpi(context, Sp.NOTE_MAX_W)
        layoutParams = stackLp(wrapWidth = true)
    }

    /**
     * HÌNH DẠNG CHUẨN của một nút phụ — **một chỗ duy nhất** cho [button], nút hành động của [listRow] và hai nút
     * −/+ của [stepperRow].
     *
     * ## Vì sao tách ra khỏi [button]
     * [ĐO] soát ảnh 2026-09-12 đếm **4 chiều cao cho 3 vai**: 35dp (Xong/FAB/Apps/Settings) · 43dp (pill bảng vẽ
     * bố cục) · 44dp (chip) · 48dp (nút Settings). Nguyên nhân không phải ai đó chọn sai số, mà là **mỗi bề mặt
     * tự dựng nút bằng `GradientDrawable` riêng** ⇒ không có chỗ nào để sửa một lần. Gom đệm + đích chạm + nền vào
     * đây thì ba nút trong lớp này không thể lệch nhau nữa; hai nút ngoài lớp ([SettingsPanel] "Xong",
     * [LayoutEditorPanel.pill]) chép đúng ba dòng này và bị `ControlHeightContractTest` ghim.
     *
     * ⚠ `gravity = CENTER`: [minHeight] chỉ NỚI ô chứ không căn nội dung, nên nút 48dp với chữ ~19px mà không căn
     * giữa sẽ để chữ dính mép trên và hở 13px dưới — đúng lỗi mà mắt đọc ra là "nút lệch" dù số đo đã đạt.
     */
    private fun paintButton(tv: TextView, radius: Int = Sp.RADIUS_XL) {
        tv.setTextColor(c(KachiTheme.INK)); KachiType.apply(tv, KachiType.BODY)
        tv.gravity = Gravity.CENTER
        tv.setPadding(dpi(context, Sp.L), dpi(context, Sp.S), dpi(context, Sp.L), dpi(context, Sp.S))
        tv.minHeight = dpi(context, Sp.TOUCH)
        tv.background = KachiTheme.surface(context, radius)
    }

    /** Nút PHỤ — viên thuốc viền mảnh, chữ [KachiType.BODY], đích chạm ≥ [KachiSpace.TOUCH] (xem [paintButton]). */
    fun button(text: String, onClick: () -> Unit): View = TextView(context).apply {
        this.text = text
        paintButton(this)
        // Lề ngoài để nút không dính hàng trên; bề rộng gói theo chữ (không kéo dài hết hàng). Lề lấy từ [stackLp]
        // ⇒ khe stack của Settings khai ĐÚNG MỘT chỗ (trước đây hàm này chép lại `bottomMargin = Sp.S` lần thứ hai).
        layoutParams = stackLp(wrapWidth = true)
        setOnClickListener { onClick() }
    }

    // ── IA v2 §4.4 · component cho các nhóm mới (nav · cast · keys · car) ─────────────────────────

    /**
     * Thẻ trả về của [statusRow] — giữ hai view con để **cập nhật tại chỗ**, không dựng lại hàng.
     *
     * ## Vì sao phải là holder chứ không phải `View` trần
     * Dòng trạng thái (nguồn dẫn đường · dịch vụ phím · cast) đổi theo thời gian thực. Nếu section phải dựng lại
     * cả hàng để đổi chữ thì nó cần giữ tham chiếu tới cha + vị trí chèn, tức mỗi section lại tự phát minh một
     * bảng tra `id → view` — đúng thứ mà KDoc lớp này (và lịch sử RW0) nói là KHÔNG có ở đây. Trả về holder là
     * cách đưa đúng **hai** tham chiếu cần thiết cho chỗ gọi, không hơn.
     */
    inner class StatusRow internal constructor(
        /** Thẻ để `addView(...)` — lề stack đã nằm sẵn trên nó, chỗ gọi KHÔNG truyền lp. */
        val view: View,
        private val dot: View,
        private val label: TextView,
    ) {
        /** Đổi màu chấm + chữ. An toàn gọi nhiều lần; không chạm bố cục nên không kéo theo lượt `requestLayout`. */
        fun update(color: String, text: String) {
            dot.background = GradientDrawable().apply {
                setShape(GradientDrawable.OVAL)
                setColor(c(color))
            }
            label.text = text
        }
    }

    /**
     * Dòng TRẠNG THÁI: chấm tròn [KachiSpace.DOT] + một câu [KachiType.BODY]. **Không nền** — nó là một sự thật
     * đang đọc được, không phải một thẻ bấm được; cho nó nền thẻ là hứa hẹn một hành động không tồn tại.
     *
     * @param color mã màu vai trò của [KachiTheme] (`GREEN` đang chạy · `AMBER` chờ/thiếu · `RED` hỏng · `MUT2` tắt).
     */
    fun statusRow(color: String, text: String): StatusRow {
        val dot = View(context)
        val label = TextView(context).apply {
            setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = stackLp()
            addView(
                dot,
                LinearLayout.LayoutParams(dpi(context, Sp.DOT), dpi(context, Sp.DOT))
                    .also { it.marginEnd = dpi(context, Sp.S) },
            )
            addView(label)
        }
        return StatusRow(row, dot, label).also { it.update(color, text) }
    }

    /** Thẻ trả về của [stepperRow] — cùng lẽ với [StatusRow]: đổi GIÁ TRỊ mà không dựng lại hàng. */
    inner class Stepper internal constructor(
        /** Thẻ để `addView(...)` — lề stack đã nằm sẵn trên nó. */
        val view: View,
        private val value: TextView,
        private val minus: View,
        private val plus: View,
    ) {
        fun setValue(text: String) { value.text = text }

        /**
         * KHOÁ/MỞ hàng — đặt cờ lên cả [view] **và** hai nút −/+.
         *
         * ## ⚠⚠ Vì sao chỗ gọi KHÔNG được tự viết `stepper.view.isEnabled = false`
         * [ĐO] AOSP `android-10.0.0_r47`:
         *  • `core/java/android/view/View.java:10873–10876` — `setEnabled` chỉ `setFlags(… ENABLED_MASK)` cho
         *    CHÍNH view đó, không hề đệ quy xuống con;
         *  • `core/java/android/view/ViewGroup.java` **không** override `setEnabled`, và `dispatchTouchEvent` của
         *    nó không đọc cờ enabled lần nào ⇒ cha bị tắt vẫn phát chạm xuống con;
         *  • `View.java:14764–14771` — nhánh `DISABLED` nằm trong `onTouchEvent` của **view bị tắt**, nên chỉ
         *    khoá được đúng view đó.
         *
         * ⇒ tắt mỗi hàng `LinearLayout` là **khoá giả**: nhìn mờ đi (alpha lan xuống con lúc VẼ) nhưng hai nút
         * con vẫn nhận click và vẫn ghi prefs. Guard phải đặt ở tầng THI HÀNH — đúng CLAUDE.md §5.
         */
        var isEnabled: Boolean
            get() = view.isEnabled
            set(on) { view.isEnabled = on; minus.isEnabled = on; plus.isEnabled = on }
    }

    /**
     * Hàng TĂNG/GIẢM: `nhãn trái · [−] giá trị [+]`. Dùng cho cỡ biển báo, tỉ lệ chia cast, mức ghế…
     *
     * ## Nút −/+ là ô VUÔNG [KachiSpace.TOUCH]×[KachiSpace.TOUCH], không phải glyph trần
     * Đây là điều khiển **bấm nhiều lần liên tiếp** trong lúc xe có thể đang lăn bánh: ngón tay không quay lại
     * đúng một điểm, nên đích chạm nhỏ biến "giảm 2 nấc" thành "trượt ra ngoài, không có gì xảy ra". [ĐO] design
     * system §10 [P1] đã ghi đúng bệnh này ở nút −/+ của thanh nút xe (rộng 14–22dp). Ở đây không có trần vật lý
     * nào ép nhỏ (khác [KachiSpace.TOUCH_TIGHT]) ⇒ lấy đủ 48.
     *
     * Giá trị nằm GIỮA hai nút và **đậm** — nó là thứ người dùng nhìn khi bấm; nhãn chỉ nói đang chỉnh cái gì.
     */
    fun stepperRow(label: String, valueText: String, onMinus: () -> Unit, onPlus: () -> Unit): Stepper {
        val value = TextView(context).apply {
            text = valueText
            setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY, bold = true)
            gravity = Gravity.CENTER
            minWidth = dpi(context, Sp.TOUCH)
            setPadding(dpi(context, Sp.S), 0, dpi(context, Sp.S), 0)
        }
        // Hai nút giữ lại làm tham chiếu (không dựng thẳng trong `addView`) vì [Stepper.isEnabled] phải khoá được
        // ĐÚNG hai view này — xem KDoc ở đó về việc cờ enabled của cha không lan xuống con.
        val minus = squareButton("−", onMinus)
        val plus = squareButton("+", onPlus)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = stackLp()
            addView(rowLabel(label))
            addView(minus)
            addView(value)
            addView(plus)
        }
        return Stepper(row, value, minus, plus)
    }

    /** Ô nút vuông [KachiSpace.TOUCH]² của [stepperRow] — cùng nền/đích chạm với [button], chỉ bo tròn hết cỡ. */
    private fun squareButton(glyph: String, onTap: () -> Unit): TextView = TextView(context).apply {
        text = glyph
        paintButton(this, Sp.RADIUS_PILL)
        // Đệm ngang về 0: bề rộng đã CỐ ĐỊNH bằng đích chạm, giữ đệm [KachiSpace.L] hai bên thì ô vuông 48dp chỉ
        // còn 16dp cho glyph ⇒ dấu "−"/"+" bị cắt. Đệm dọc giữ nguyên (không ảnh hưởng, minHeight đã lớn hơn).
        setPadding(0, dpi(context, Sp.S), 0, dpi(context, Sp.S))
        minWidth = dpi(context, Sp.TOUCH)
        layoutParams = LinearLayout.LayoutParams(dpi(context, Sp.TOUCH), dpi(context, Sp.TOUCH))
            .also { it.marginStart = dpi(context, Sp.S) }
        setOnClickListener { onTap() }
    }

    /**
     * Hàng DANH SÁCH: thẻ `tiêu đề + dòng phụ` (như [checkRow]) nhưng bên phải là một **nút hành động** thật —
     * dùng cho danh sách gán phím vô-lăng ("Xoá"), hồ sơ, app đã chọn.
     *
     * ## Vì sao nút, không phải một chữ bấm được
     * Hành động ở đây **không hoàn lại được** (xoá một gán phím). Một chữ `✕` mờ ở mép phải vừa không đạt đích
     * chạm vừa không nói ra là mình bấm được — và bấm nhầm thì người dùng phải gán lại từ đầu. Nút đi qua
     * [paintButton] nên nó cao [KachiSpace.TOUCH] như mọi nút khác của Settings.
     *
     * Nhãn hành động do chỗ gọi cấp (không hardcode "Xoá") để hàng này còn dùng được cho "Đổi"/"Chọn" — luật
     * *generic, không case-by-case* của CLAUDE.md §7.
     */
    fun listRow(title: String, sub: String, actionLabel: String, onAction: () -> Unit): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = KachiTheme.surface(context, Sp.RADIUS_L)
            val p = dpi(context, Sp.M); setPadding(p, p, p, p)
            layoutParams = stackLp()
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = title; setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY)
                })
                addView(TextView(context).apply {
                    text = sub; setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = actionLabel
                paintButton(this, Sp.RADIUS_PILL)
                setOnClickListener { onAction() }
            })
        }
    }

    /**
     * Bọc một view TỰ VẼ (sơ đồ ghế · đồng hồ PM2.5 · kéo-thả vị trí biển báo/bong bóng) vào thẻ nền
     * [KachiTheme.CELL] với **đúng lề stack** của Settings.
     *
     * ## Vì sao phải đi qua đây chứ không `addView` thẳng
     * View tự vẽ đến từ bề mặt ClusterNav cũ: nó không biết gì về lề stack, không có nền, và cao bao nhiêu là do
     * chính nó quyết (thường `WRAP_CONTENT` = 0 khi chưa đo được). Thả thẳng vào cột Settings thì nó **dính** hàng
     * trên (đúng bệnh design system §2 sinh ra để chữa) và có thể cao 0px mà không báo lỗi gì. Thẻ này cấp cả ba
     * thứ còn thiếu: nền, lề ngoài, và một chiều cao TƯỜNG MINH do chỗ gọi chọn.
     *
     * @param heightDp chiều cao CỐ ĐỊNH của view con, dp. Phải là số dp thật (không phải px) — nó đi qua
     *   [KachiTheme.dpi] tại đây.
     * @throws IllegalStateException (từ Android) nếu [view] đã có cha — mỗi view chỉ nhúng được một chỗ.
     */
    fun embed(view: View, heightDp: Int): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = KachiTheme.surface(context, Sp.RADIUS_L)
        val p = dpi(context, Sp.M); setPadding(p, p, p, p)
        layoutParams = stackLp()
        addView(
            view,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpi(context, heightDp)),
        )
    }
}
