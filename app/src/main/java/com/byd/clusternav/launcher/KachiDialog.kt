package com.byd.clusternav.launcher

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiDialogSupport.anyEdit
import com.byd.clusternav.launcher.KachiDialogSupport.cleanTitle
import com.byd.clusternav.launcher.KachiDialogSupport.controlFill
import com.byd.clusternav.launcher.KachiDialogSupport.followHostBars
import com.byd.clusternav.launcher.KachiDialogSupport.themeEdits
import com.byd.clusternav.launcher.KachiDialogSupport.watchEdits
import com.byd.clusternav.launcher.KachiSpace as Sp

/** Việc của một nút chân hộp — chạy với [KachiDialog.Handle] làm receiver để tự đóng/giữ hộp khi cần. */
internal typealias DialogAction = KachiDialog.Handle.() -> Unit

/**
 * ═══ KACHI-301 R8 — KHUNG HỘP THOẠI DUY NHẤT của launcher ════════════════════════════════════════════════════
 *
 * ## Vì sao không dùng tiếp `AlertDialog.Builder`
 * [ĐO] 22 hộp thoại sống đều dựng bằng `AlertDialog.Builder`; khung của nó lấy theme Activity
 * (`Theme.DeviceDefault.NoActionBar`, AndroidManifest.xml:153) ⇒ ra **khung BYD**: nền, bo góc, chữ nút theo ROM,
 * không theo bảng [KachiTheme], không đổi Sáng/Tối. Ruột hộp thì đã theo [KachiType]/[KachiSpace] ⇒ một hộp hai
 * ngôn ngữ thiết kế. Tệp này là MỘT khung: `android.app.Dialog` cửa sổ trong suốt, nội dung là thẻ
 * [KachiTheme.surface] bo [Sp.RADIUS_XXL] vẽ theo bảng **đang dùng** lúc mở hộp.
 *
 * ## Bố cục (spec §4.4)
 * Đầu = ô icon (gradient; đỏ nhạt khi [Tone.DESTRUCTIVE]) + tiêu đề [KachiType.TITLE] + dòng phụ [KachiType.CAPTION].
 * Thân = cuộn được, trần chiều cao [MAX_H_FRACTION] màn ⇒ danh sách dài không đẩy chân hộp ra ngoài màn.
 * Vùng GHIM ([pinned]) = giữa đầu và thân, KHÔNG cuộn (ô tìm, chip lọc đứng yên khi danh sách cuộn).
 * [fullScreen] = trang: cửa sổ phủ cả màn, thẻ chừa lề [Sp.L], thân lấp hết chiều cao còn lại (#14 Dạy tên).
 * Chân CỐ ĐỊNH = [nguy hiểm / liên kết (trái)] … [Huỷ] [Chính (gradient)]; mọi nút ≥ [Sp.TOUCH].
 * Phân tách chân/thân CHỈ bằng màu fill (WP1 · R1.1 — không viền, không gạch).
 *
 * ## An toàn (R8)
 * Activity đang đóng/đã huỷ ⇒ KHÔNG show, trả `null`. `BadTokenException` (token cửa sổ đã chết giữa chừng — ca
 * hộp mở từ callback trễ) bị bắt và ghi log, không văng launcher. Không luồng, không nhịp, không listener nền:
 * mọi `TextWatcher` gắn vào ô của CHÍNH hộp, chết cùng hộp.
 *
 * Bốn khuôn (Chọn · Biểu mẫu · Xác nhận · Thông báo) dựng trên lớp này ở [KachiDialogs]; chỗ gọi nên dùng khuôn,
 * chỉ hộp thật sự đặc biệt mới dựng [KachiDialog] trực tiếp.
 */
internal class KachiDialog(private val context: Context) {

    /** Sắc của ô icon đầu hộp + nút chính. [DESTRUCTIVE] = việc không hoàn lại được (xoá, dọn sạch). */
    enum class Tone { ACCENT, DESTRUCTIVE }

    private class Action(val text: CharSequence, val autoDismiss: Boolean, val run: DialogAction?)

    private var title: CharSequence = ""
    private var subtitle: CharSequence? = null
    private var iconRes = 0
    private var glyph: String? = null
    private var tone = Tone.ACCENT
    private val bodyViews = mutableListOf<View>()
    private val pinnedViews = mutableListOf<View>()
    private var fullScreen = false
    private var primary: Action? = null
    private var cancel: Action? = Action("", autoDismiss = true, run = null)
    private var danger: Action? = null
    private var link: Action? = null
    private var cancelable = true
    private var focus: EditText? = null
    private var keyboard = false
    private var validate: (() -> Boolean)? = null
    private var onDismiss: (() -> Unit)? = null
    private var onBack: (() -> Boolean)? = null
    private var form = false
    private val parts = Parts()

    fun title(text: CharSequence) = apply { title = cleanTitle(text) }
    fun subtitle(text: CharSequence?) = apply { subtitle = text }

    /** Icon đầu hộp là một vector drawable (tô [KachiTheme.ON_ACCENT]); `0` = không icon. */
    fun icon(res: Int) = apply { iconRes = res }

    /** Icon đầu hộp là một KÝ TỰ (chữ cái đầu, "!") — thay cho [icon] khi không có drawable hợp vai. */
    fun glyph(text: String?) = apply { glyph = text }
    fun tone(t: Tone) = apply { tone = t }

    /** Thêm một view vào thân CUỘN được (theo thứ tự gọi, khe [Sp.S]). */
    fun body(view: View) = apply { bodyViews += view }

    /** Thêm một view vào vùng GHIM giữa đầu hộp và thân cuộn (theo thứ tự gọi, khe [Sp.S]) — không cuộn theo thân. */
    fun pinned(view: View) = apply { pinnedViews += view }

    /**
     * Chế độ TRANG: cửa sổ `MATCH_PARENT × MATCH_PARENT`, thẻ chừa lề [Sp.L] quanh (chạm lề = chạm ngoài thẻ), thân cuộn
     * lấp hết chiều cao còn lại giữa đầu/vùng ghim và chân — không trần [MAX_H_FRACTION], không trần bề rộng [Sp.NOTE_MAX_W].
     */
    fun fullScreen() = apply { fullScreen = true }

    /** Một đoạn chữ thân hộp ([KachiType.BODY], mực [KachiTheme.INK]). */
    fun message(text: CharSequence) = apply { bodyViews += messageView(context, text) }

    /** Nút CHÍNH (phải cùng). [autoDismiss] = false ⇒ hộp đứng lại, chỗ gọi tự [Handle.dismiss] (vd lưu hỏng). */
    fun primary(label: CharSequence, autoDismiss: Boolean = true, run: DialogAction) =
        apply { primary = Action(label, autoDismiss, run) }

    /** Nút huỷ — mặc định CÓ, nhãn "Huỷ". [label] `null` ⇒ nhãn mặc định. */
    fun cancel(label: CharSequence? = null, run: DialogAction? = null) =
        apply { cancel = Action(label ?: "", autoDismiss = true, run = run) }
    fun noCancel() = apply { cancel = null }

    /** Nút NGUY HIỂM (trái, nền [KachiTheme.RED_SOFT] chữ [KachiTheme.RED]) — vd *Xoá* trong hộp sửa. */
    fun danger(label: CharSequence, run: DialogAction) = apply { danger = Action(label, autoDismiss = true, run = run) }

    /** Lối tắt dạng LIÊN KẾT ở chân trái (vd *"Quản lý hồ sơ…"*): không nền, chữ [KachiTheme.ACCENT_INK]. */
    fun link(label: CharSequence, autoDismiss: Boolean = true, run: DialogAction) =
        apply { link = Action(label, autoDismiss, run) }
    fun cancelable(on: Boolean) = apply { cancelable = on }

    /**
     * QA-301 (P3) — hộp là BIỂU MẪU: chạm ngoài thẻ KHÔNG đóng (lỡ tay chạm nền là mất chữ đang gõ dở); Back và nút
     * Huỷ vẫn huỷ. Chọn · Xác nhận · Thông báo giữ chạm-ngoài-đóng (không có gì để mất).
     */
    fun form() = apply { form = true }

    /** Phím Back: [handler] trả `true` = đã xử lý (vd lùi một bước), `false` = Back thường (huỷ hộp). */
    fun onBack(handler: () -> Boolean) = apply { onBack = handler }

    /**
     * Kiểm hợp lệ của biểu mẫu: chạy lúc mở và sau MỖI lần chữ đổi trong mọi [EditText] của thân ⇒ nút chính bật/
     * khoá theo kết quả. Hàm được phép đặt lỗi tại chỗ (vd [KachiDialogs.Field.error]).
     */
    fun validate(check: () -> Boolean) = apply { validate = check }

    /** Ô nhận focus lúc mở; [showKeyboard] = true ⇒ bật bàn phím luôn (mặc định KHÔNG — xe không cần bàn phím che màn). */
    fun focus(edit: EditText, showKeyboard: Boolean = false) = apply { focus = edit; keyboard = showKeyboard }
    fun onDismiss(run: () -> Unit) = apply { onDismiss = run }

    /** Tay cầm của một hộp ĐANG HIỆN — đóng hộp, bật/khoá nút chính, đổi dòng phụ tại chỗ. */
    /** View con của hộp đang hiện mà [Handle] cần chạm tới (đặt lúc dựng). */
    private class Parts {
        var primary: TextView? = null
        var cancel: TextView? = null
        var link: TextView? = null
        var body: LinearLayout? = null
        var scroll: ScrollView? = null
        lateinit var title: TextView
        lateinit var subtitle: TextView
    }

    inner class Handle internal constructor(private val dialog: Dialog) {
        val isShowing: Boolean get() = dialog.isShowing
        fun dismiss() = dialog.dismiss()

        /** Đổi dòng phụ đầu hộp đang hiện (vd tóm tắt sống); `null`/trống ⇒ ẩn dòng. */
        fun setSubtitle(text: CharSequence?) = bindSubtitle(parts.subtitle, text)

        /** Đổi tiêu đề hộp đang hiện (hộp nhiều bước — [KachiDialogs.steps]). */
        fun setTitle(text: CharSequence) { parts.title.text = cleanTitle(text) }

        /** Thay TOÀN BỘ thân cuộn (hộp nhiều bước đổi ruột tại chỗ, không mở hộp thứ hai) và cuộn về đầu. */
        fun setBody(views: List<View>) {
            val col = parts.body ?: return
            col.removeAllViews()
            fill(col, views)
            themeEdits(views)
            parts.scroll?.scrollTo(0, 0)
        }

        /** Hiện/ẩn nút liên kết chân trái (vd *"← Quay lại"* chỉ có từ bước 2). */
        fun setLinkVisible(on: Boolean) { parts.link?.visibility = if (on) View.VISIBLE else View.GONE }

        /** Đổi nhãn nút huỷ (vd *Huỷ* → *Đóng* sau khi đã lưu — không còn gì để huỷ). */
        fun setCancelLabel(text: CharSequence) { parts.cancel?.text = text }

        /** Bật/khoá nút chính (khoá = mờ [DISABLED_ALPHA] + không nhận chạm). */
        fun setPrimaryEnabled(on: Boolean) {
            val v = parts.primary ?: return
            v.isEnabled = on
            v.alpha = if (on) 1f else DISABLED_ALPHA
        }

        /** Chạy lại [validate] (chỗ gọi đổi trạng thái không qua ô chữ — vd chọn chip) rồi bật/khoá nút chính. */
        fun revalidate(): Boolean = (validate?.invoke() ?: true).also { setPrimaryEnabled(it) }
    }

    /**
     * Dựng và hiện hộp. Trả `null` khi KHÔNG hiện được (Activity đang đóng/đã huỷ, token chết) — chỗ gọi không
     * cần kiểm gì thêm, hộp đơn giản là không mở.
     */
    fun show(): Handle? {
        val act = activityOf(context)
        if (act != null && (act.isFinishing || act.isDestroyed)) return null
        val theme = if (KachiTheme.night) android.R.style.Theme_Material_Dialog_NoActionBar
        else android.R.style.Theme_Material_Light_Dialog_NoActionBar
        val dialog = Win(context, theme)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val ctx = dialog.context
        val footer = footer(ctx)
        parts.title = TextView(ctx)
        parts.subtitle = TextView(ctx)
        val handle = Handle(dialog)
        bindFooter(footer, handle)
        val card = frame(ctx, header(ctx, parts.title, parts.subtitle), footer)
        dialog.setContentView(if (fullScreen) page(ctx, card, dialog) else card)
        dialog.setCancelable(cancelable)
        dialog.setCanceledOnTouchOutside(cancelable && !form)
        onDismiss?.let { run -> dialog.setOnDismissListener { run() } }
        themeEdits(pinnedViews + bodyViews)
        // QA-301 (P3) — hộp KHÔNG có ô chữ ⇒ `ALWAYS_HIDDEN`: bàn phím của hộp con vừa đóng không được nằm lại/hiện lại
        // trên nó (`STATE_HIDDEN` chỉ áp khi điều hướng TỚI, không áp khi cửa sổ lấy lại tiêu điểm).
        val hasEdit = anyEdit(pinnedViews + bodyViews)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(DIM)
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or when {
                    keyboard -> WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    hasEdit -> WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                    else -> WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                },
            )
            act?.window?.let { host -> followHostBars(this, host) }
        }
        if (validate != null) {
            watchEdits(bodyViews) { handle.revalidate() }
            handle.revalidate()
        }
        focus?.requestFocus()
        try {
            dialog.show()
        } catch (e: WindowManager.BadTokenException) {
            Log.w(TAG, "dialog not shown, window token gone: ${e.message}")
            return null
        }
        // Bề rộng TỐI ĐA = [Sp.NOTE_MAX_W] (600 dp, cùng lý do "dòng đọc được"): màn xe 1920×1080 @240dpi = 1280 dp
        // ngang ⇒ ~nửa màn — đủ hai cột thẻ (R10) mà mắt không phải quét cả bề ngang khi đang lái.
        // [fullScreen]: cửa sổ phủ màn (lề nằm trong [page]); `setLayout` — đúng đường của `Window`, không nới decor tay.
        val dm = ctx.resources.displayMetrics
        if (fullScreen) dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        else dialog.window?.setLayout(
            minOf(dpi(ctx, Sp.NOTE_MAX_W), dm.widthPixels - 2 * dpi(ctx, Sp.XXL)), ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        return handle
    }

    // ── dựng ─────────────────────────────────────────────────────────────────────────────────────────────

    private fun frame(ctx: Context, header: View, footer: View): View =
        (if (fullScreen) LinearLayout(ctx) else KachiDialogSupport.MaxHeightColumn(ctx)).apply {
            orientation = LinearLayout.VERTICAL
            background = KachiTheme.surface(ctx, Sp.RADIUS_XXL)
            addView(header)
            if (pinnedViews.isNotEmpty()) {
                addView(column(ctx, pinnedViews, bottom = Sp.S))
            }
            if (bodyViews.isNotEmpty()) {
                // Trang: thân cao 0 + trọng số 1 = lấp phần còn lại; hộp: WRAP + trọng số 1 = co khi chạm trần.
                val h = if (fullScreen) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
                val col = column(ctx, bodyViews, bottom = Sp.M).also { parts.body = it }
                addView(
                    ScrollView(ctx).apply { isFillViewport = true; addView(col) }.also { parts.scroll = it },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h, 1f),
                )
            } else if (fullScreen) {
                addView(View(ctx), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            }
            addView(footer)
        }

    /** Cột dọc lề ngang [Sp.XL], các view cách nhau [Sp.S] — dùng cho cả vùng ghim và thân cuộn. */
    private fun column(ctx: Context, views: List<View>, bottom: Int): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpi(ctx, Sp.XL), 0, dpi(ctx, Sp.XL), dpi(ctx, bottom))
        fill(this, views)
    }

    private fun fill(col: LinearLayout, views: List<View>) = views.forEachIndexed { i, v ->
        (v.parent as? ViewGroup)?.removeView(v)
        col.addView(v, stackLp(col.context, top = if (i == 0) 0 else Sp.S))
    }

    /**
     * Cửa sổ hộp. `dismiss` GỠ bàn phím trước khi cửa sổ biến mất (QA-301: đóng hộp có ô chữ ⇒ bàn phím rò sang hộp/màn
     * bên dưới); Back đi qua [onBack] trước (hộp nhiều bước lùi một bước thay vì đóng cả chuỗi).
     */
    private inner class Win(ctx: Context, theme: Int) : Dialog(ctx, theme) {
        private var keyboardAsked = false

        override fun onBackPressed() {
            if (onBack?.invoke() != true) super.onBackPressed()
        }

        /**
         * [focus] + `showKeyboard` ⇒ gọi bàn phím khi cửa sổ THẬT SỰ có tiêu điểm (một lần). [ĐO máy ảo 13/10] chỉ đặt
         * `SOFT_INPUT_STATE_VISIBLE` thì hộp chồng lên hộp khác (*"Gõ tên…"* trên hộp Dạy) vẫn `mInputShown=false`.
         */
        override fun onWindowFocusChanged(hasFocus: Boolean) {
            super.onWindowFocusChanged(hasFocus)
            // Bàn phím/hộp con làm hệ thống hạ cờ ẩn thanh (cùng lý do màn chính gọi lại `goImmersive` khi lấy lại tiêu
            // điểm) ⇒ mỗi lần hộp lấy lại tiêu điểm đặt lại ([ĐO Re-QA]: sau hộp "Gõ tên…" cờ còn `0x1000`, thanh lộ).
            val host = activityOf(context)?.window
            if (hasFocus && host != null) window?.let { followHostBars(it, host) }
            val edit = focus ?: return
            if (!hasFocus || !keyboard || keyboardAsked) return
            keyboardAsked = true
            // `post`: lượt kế của vòng lặp — IMM gắn vào cửa sổ (startInput) SAU lượt báo tiêu điểm.
            edit.post { context.getSystemService(InputMethodManager::class.java)?.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT) }
        }

        override fun dismiss() {
            currentFocus?.let { v ->
                context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
            }
            super.dismiss()
        }
    }

    /**
     * Nền trang [fullScreen]: lớp trong suốt chừa lề [Sp.L] quanh [card]. Lề nằm TRONG cửa sổ nên chạm lề không phải
     * "chạm ngoài" với hệ thống ⇒ tự đóng khi [cancelable] (cùng hành vi `setCanceledOnTouchOutside`).
     */
    private fun page(ctx: Context, card: View, dialog: Dialog): View = FrameLayout(ctx).apply {
        val m = dpi(ctx, Sp.L)
        setPadding(m, m, m, m)
        if (cancelable && !form) setOnClickListener { dialog.cancel() }
        card.isClickable = true   // chạm trong thẻ không lọt xuống lớp nền
        addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun header(ctx: Context, titleView: TextView, subtitleView: TextView): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpi(ctx, Sp.XL), dpi(ctx, Sp.L), dpi(ctx, Sp.XL), dpi(ctx, Sp.M))
        iconTile(ctx)?.let { tile ->
            addView(tile, LinearLayout.LayoutParams(dpi(ctx, Sp.ICON_XL), dpi(ctx, Sp.ICON_XL)).apply {
                marginEnd = dpi(ctx, Sp.M)
            })
        }
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleView.apply {
                text = title
                maxLines = 2
                setTextColor(c(KachiTheme.INK))
                KachiType.apply(this, KachiType.TITLE, bold = true)
            })
            // Dòng phụ LUÔN có trong cây (ẩn khi trống) ⇒ [Handle.setSubtitle] hiện được cả khi lúc mở chưa có.
            addView(subtitleView.apply {
                setTextColor(c(KachiTheme.MUT))
                KachiType.apply(this, KachiType.CAPTION)
                bindSubtitle(this, subtitle)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    /** Ô icon đầu hộp: gradient + mực [KachiTheme.ON_ACCENT]; [Tone.DESTRUCTIVE] ⇒ nền đỏ nhạt + mực đỏ. */
    private fun iconTile(ctx: Context): View? {
        if (iconRes == 0 && glyph.isNullOrEmpty()) return null
        val red = tone == Tone.DESTRUCTIVE
        val bg = if (red) KachiTheme.card(ctx, Sp.RADIUS_M, KachiTheme.RED_SOFT) else KachiTheme.gradient(ctx, Sp.RADIUS_M)
        val ink = c(if (red) KachiTheme.RED else KachiTheme.ON_ACCENT)
        return if (iconRes != 0) ImageView(ctx).apply {
            setImageResource(iconRes)
            setColorFilter(ink)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val p = (dpi(ctx, Sp.ICON_XL) - dpi(ctx, Sp.ICON_M)) / 2
            setPadding(p, p, p, p)
            background = bg
        } else TextView(ctx).apply {
            text = glyph
            gravity = Gravity.CENTER
            setTextColor(ink)
            KachiType.apply(this, KachiType.TITLE, bold = true)
            background = bg
        }
    }

    /** Chân hộp — nền tô [KachiTheme.CARD_FILL] bo hai góc dưới, không viền. Nút nào cũng ghi vào [parts]. */
    private fun footer(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpi(ctx, Sp.XL), dpi(ctx, Sp.M), dpi(ctx, Sp.XL), dpi(ctx, Sp.M))
        background = GradientDrawable().apply {
            val r = Sp.dpf(ctx, Sp.RADIUS_XXL)
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            setColor(c(KachiTheme.CARD_FILL))
        }
        danger?.let { addView(button(ctx, it.text, Style.DANGER).apply { tag = it }, gapLp(ctx, end = Sp.S)) }
        link?.let { addView(button(ctx, it.text, Style.LINK).apply { tag = it; parts.link = this }, gapLp(ctx, end = Sp.S)) }
        addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        cancel?.let { a ->
            val label = a.text.ifEmpty { ctx.getString(R.string.kdlg_cancel) }
            addView(button(ctx, label, Style.SECONDARY).apply { tag = a; parts.cancel = this }, gapLp(ctx, start = Sp.S))
        }
        primary?.let { a ->
            val style = if (tone == Tone.DESTRUCTIVE) Style.DANGER else Style.PRIMARY
            addView(button(ctx, a.text, style).apply { tag = a; parts.primary = this }, gapLp(ctx, start = Sp.S))
        }
    }

    /** Gắn việc của từng nút (đã giấu trong `tag`) với [handle] — sau khi tay cầm có. */
    private fun bindFooter(footer: LinearLayout, handle: Handle) {
        for (i in 0 until footer.childCount) {
            val v = footer.getChildAt(i)
            val a = v.tag as? Action ?: continue
            v.setOnClickListener {
                if (a === primary && validate != null && !handle.revalidate()) return@setOnClickListener
                if (a.autoDismiss) handle.dismiss()
                a.run?.invoke(handle)
            }
        }
    }

    private enum class Style { PRIMARY, SECONDARY, DANGER, LINK }

    private fun button(ctx: Context, label: CharSequence, style: Style): TextView = TextView(ctx).apply {
        text = label
        gravity = Gravity.CENTER
        maxLines = 1
        minHeight = dpi(ctx, Sp.TOUCH)
        minWidth = dpi(ctx, Sp.CHIP_MIN_W)
        setPadding(dpi(ctx, Sp.L), dpi(ctx, Sp.S), dpi(ctx, Sp.L), dpi(ctx, Sp.S))
        KachiType.apply(this, KachiType.BODY, bold = style != Style.SECONDARY)
        when (style) {
            Style.PRIMARY -> { background = KachiTheme.gradient(ctx, Sp.RADIUS_PILL); setTextColor(c(KachiTheme.ON_ACCENT)) }
            // QA-301 (P3): nút phụ tô [controlFill] — `surface` NEUTRAL trùng màu thẻ hộp ⇒ ở bảng Sáng nút mất hình.
            Style.SECONDARY -> { background = KachiTheme.pill(ctx, controlFill()); setTextColor(c(KachiTheme.INK)) }
            Style.DANGER -> { background = KachiTheme.pill(ctx, KachiTheme.RED_SOFT); setTextColor(c(KachiTheme.RED)) }
            Style.LINK -> setTextColor(c(KachiTheme.ACCENT_INK))
        }
    }

    companion object {
        private const val TAG = "KachiDialog"

        /** Trần chiều cao hộp theo tỉ lệ màn — chừa lề trên/dưới để thấy hộp là một lớp NỔI trên màn cũ. */
        const val MAX_H_FRACTION = 0.86f

        /** Độ tối lớp che sau hộp (cửa sổ `FLAG_DIM_BEHIND` — hệ thống vẽ, 0 lượt vẽ thêm của app). */
        const val DIM = 0.55f

        /** Nút chính khi biểu mẫu chưa hợp lệ. */
        const val DISABLED_ALPHA = 0.45f

        /** Activity chủ của [ctx] (bóc mọi lớp `ContextWrapper`: LangHost, theme, DockScale…); `null` nếu không có. */
        fun activityOf(ctx: Context): Activity? {
            var c: Context? = ctx
            while (c is ContextWrapper) {
                if (c is Activity) return c
                c = c.baseContext
            }
            return null
        }

        private fun bindSubtitle(view: TextView, text: CharSequence?) {
            view.text = text
            view.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        }

        internal fun messageView(ctx: Context, text: CharSequence) = TextView(ctx).apply {
            this.text = text
            setTextColor(c(KachiTheme.INK))
            KachiType.apply(this, KachiType.BODY)
        }

        internal fun stackLp(ctx: Context, top: Int) =
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(ctx, top)
            }

        private fun gapLp(ctx: Context, start: Int = 0, end: Int = 0) =
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dpi(ctx, start)
                marginEnd = dpi(ctx, end)
            }
    }
}
