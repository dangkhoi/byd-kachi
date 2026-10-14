package com.byd.clusternav.launcher

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.ContrastBackdrop.Layer
import com.byd.clusternav.launcher.KachiTheme.c

/**
 * ═══ 3.1.5 R29 pha 2 — MỰC THÍCH ỨNG trên view: chọn mực theo NỀN GHÉP ĐO ĐƯỢC, không theo một hằng ═══════════════════════
 *
 * Owner 14/10: *"các text, màu nét icon cần phải đo được độ tương phản với nền, với màn sáng, tối, để luôn luôn nhìn thấy
 * được, hiện tại hên xui"*. Spec `kachi-301-cast-steps-dialog-ia.html` §R29 · §4.7 · §5.1 T29-4/5/7/9.
 *
 * ## Hai vai
 *  • **Mặt** ([surface]) — một view có nền riêng (thẻ kính, thanh trên, thanh nút, ô nút): khai các lớp nó vẽ (biến thể theo
 *    vùng màn). Nền dưới một mặt = ảnh sắc dưới CHÍNH vùng của mặt ([WallArt.backdropAt]: ảnh + làm tối) × dải che đỉnh/đáy ×
 *    lớp của mọi mặt tổ tiên × lớp của chính nó ([ContrastStack.compose]).
 *  • **Mực** ([text] / [icon]) — chỗ tô chữ/nét gọi đây THAY `setTextColor(c(hex))`: mã màu cho biết vai ([InkKey.of]),
 *    view cho biết loại (chữ / chữ lớn / nét — [InkModel.role]); mực tô = [ContrastInk.resolve] trên nền của mặt gần nhất,
 *    ứng viên từ bảng màu SỐNG ([InkModel.candidates]). Thiếu thì lớp đỡ cục bộ ([InkHalo], nền của CHÍNH phần tử — không
 *    nâng độ đục cả thẻ: owner 04/10 *"tùy user chọn"*, OQ7).
 *
 * ## Chi phí (perf-every-fix) — chỉ khi SỰ KIỆN đổi
 * Nền tính khi mặt ĐỔI CHỖ/CỠ (cổng vị trí+cỡ của [WallWindowDrawable.relocate] · layout của mặt thường), khi ảnh / chủ đề /
 * độ đục đổi ([KachiGlass.refresh] ← `onArtChanged`/`applyThemeInPlace`; [fresh] so danh tính bảng màu · ảnh · độ đục).
 * Kết quả giải lưu trên mặt theo (vai, loại) — nhịp xe 1 Hz tô lại chữ chỉ tra bảng, không giải; `onDraw` không gọi gì ở đây.
 * 0 luồng · 0 nhịp · listener: chỉ một `OnLayoutChangeListener` trên mặt THƯỜNG (mặt kính dùng lại listener sẵn có).
 *
 * ## Pha 2b (T29-6 · nút đĩa · ⇄ · icon ô nút · đoạn màu · ô con cảnh báo · nút DIM)
 *  • **Mặt dò** ([surface] `onUnder`) — mặt tự quyết MÀU MẶT của chính nó từ nền ĐO dưới nó (đĩa nút [KachiButtonInks],
 *    chấm vẽ canvas [Car3dDotsLayer]): nhận nền dưới vùng của nó KHÔNG gồm lớp của chính nó, chỉ khi nền đó đổi.
 *  • **Người đi kèm** ([ride]) — nét vẽ không nằm TRONG mặt của nó (icon ⇄ là anh em của đĩa kính): giải trên mặt kia.
 *  • **Đoạn màu** ([InkSpan]) — đoạn chữ khai vai riêng; giải cùng lượt với chữ chứa nó, trên cùng nền (+ lớp đỡ của chữ).
 *  • Lớp đỡ khi phần tử ĐÃ có nền riêng (pill, nút DIM, đĩa ghế): [HaloLayer] = nền cũ + lớp đỡ CÙNG HÌNH phía trên, dưới nét.
 *  • [backdropAt] — nền ghép dưới một vùng bất kỳ (bộ vẽ canvas), cùng phép [backdropOf].
 *
 * ## Fail-loud
 * `ok = false` (không mực nào, kể cả có lớp đỡ, đạt sàn) hoặc lớp đỡ không đặt được (phần tử đã có nền riêng) ⇒ `Log.w`
 * qua [LogLineThrottle] + [ContrastLedger] (in cùng dòng `KachiPerf` mỗi phút).
 */
object AdaptiveInk {

    private const val TAG = "KachiContrast"
    private val throttle = LogLineThrottle()

    /** Một mặt: các nhóm biến thể lớp nó vẽ dưới vùng màn `r` ([ContrastStack.compose]) + kết quả giải đã lưu. */
    internal class Surface(
        val variant: Any?, val listen: Boolean, val onUnder: ((ContrastBackdrop) -> Unit)?, val groups: (Rect) -> List<List<List<Layer>>>,
    ) : View.OnLayoutChangeListener {
        val rect = Rect()
        var backdrop: ContrastBackdrop? = null
        /** Nền dưới vùng KHÔNG gồm lớp của chính mặt — chỉ mặt dò ([onUnder]) dùng. */
        var under: ContrastBackdrop? = null
        var palette: KachiPalette? = null
        var fraction = -1.0
        var art: WallArt? = null
        val solved = HashMap<Int, Resolution>()
        /** Kết quả giải trên nền + một lớp đỡ (đoạn màu / chữ chứa đoạn cần đỡ): khoá = (ô vai) × màu lớp đỡ. */
        val solvedOver = HashMap<Long, Resolution>()
        override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) { if (v != null) update(v) }
    }

    /**
     * Dấu trên một phần tử mực: vai màu + có tô cả drawable kép (icon dẫn đầu của chip) không. [after] = việc của chỗ gọi sau mỗi
     * lần tô ([onInk] — vd độ mờ TẮT của icon ô nút tính trên mực + nền THẬT); [last]/[ground] = lần tô cuối.
     */
    internal class Mark(val key: InkKey, val compound: Boolean, var after: ((View, Resolution, ContrastBackdrop) -> Unit)? = null) {
        var last: Resolution? = null
        var ground: ContrastBackdrop? = null
    }

    /** Lớp đỡ cục bộ — đệm bo mềm (chữ) / đĩa (icon) màu [Resolution.haloColor], là NỀN của chính phần tử (không đổi bố cục). */
    internal class InkHalo(radius: Float) : GradientDrawable() { init { cornerRadius = radius } }

    /** Phần tử ĐÃ có nền riêng ([base]: pill · nút DIM · đĩa ghế) ⇒ lớp đỡ CÙNG HÌNH nằm trên nền đó, dưới nét. */
    internal class HaloLayer(val base: Drawable, val halo: InkHalo) : LayerDrawable(arrayOf(base, halo))

    /**
     * Đoạn chữ mang vai màu RIÊNG trong một chữ đã nối (đơn vị `withUnit` · số chip lốp) — thay `ForegroundColorSpan` (màu đóng
     * băng lúc dựng chuỗi). Màu ban đầu = đúng mã [hex] (chưa đo thì không đoán); lượt tô của chữ chứa nó giải lại ([spans]).
     */
    class InkSpan(val hex: String) : CharacterStyle(), UpdateAppearance {
        var color: Int = c(hex)
        override fun updateDrawState(tp: TextPaint) { tp.color = color }
    }

    /**
     * Khai [view] là một MẶT. [listen] = gắn `OnLayoutChangeListener` (mặt thường); mặt kính để `false` vì
     * [WallWindowDrawable.relocate] đã gọi [update] đúng lúc vùng của nó đổi. Đã là mặt cùng [variant] (cùng kiểu nền — vd ô nút
     * vẫn TẮT) ⇒ giữ nguyên kết quả đã giải (chỗ dựng nền gọi lại mỗi lần trạng thái đổi; không giải lại khi nền không đổi).
     * `null` = luôn thay (thẻ kính: mỗi lần dựng nền là một cửa sổ mới).
     */
    fun surface(
        view: View, listen: Boolean, variant: Any? = null, onUnder: ((ContrastBackdrop) -> Unit)? = null,
        groups: (Rect) -> List<List<List<Layer>>>,
    ) {
        val old = view.getTag(R.id.kachi_ink_surface) as? Surface
        if (old != null && variant != null && old.variant == variant && old.listen == listen) return
        clear(view)
        val s = Surface(variant, listen, onUnder, groups)
        view.setTag(R.id.kachi_ink_surface, s)
        if (listen) { view.addOnLayoutChangeListener(s); update(view, force = true) }   // mặt kính: `relocate` gọi
    }

    /** Mặt thường chỉ tô màu nền [fill] (đọc bảng SỐNG, chưa nhân độ đục; [fades] = nhân [KachiChrome.fraction]); [variant] như [surface]. */
    fun fill(view: View, fades: Boolean = true, variant: Any? = "fill", extra: () -> List<Layer> = { emptyList() }, fill: () -> IntArray) =
        surface(view, listen = true, variant = variant) {
            val f = if (fades) KachiChrome.fraction else 1.0
            val pair = fill().map { ColorMath.withAlpha(it, ChromeOpacity.drawnAlpha(ColorMath.alpha(it), f)) }.toIntArray()
            listOf(ContrastStack.surfaceVariants(pair).map { it + extra() })
        }

    /**
     * R29 T29-7 — mặt của một Ô NÚT (`ControlTileFactory.applyBg`): TẮT = bề mặt trung tính × độ đục chung; BẬT = `tonalOn`
     * (bề mặt trung tính đục + lớp `TILE_ON_FROM`). Cùng hai nhánh của nơi dựng nền.
     */
    fun tile(view: View, active: Boolean) = fill(view, fades = !active, variant = if (active) "tile-on" else "tile-off",
        extra = { if (active) listOf(Layer.Flat(c(KachiTheme.TILE_ON_FROM))) else emptyList() }) {
        KachiTheme.surfacePair(SurfaceTone.NEUTRAL, overArtwork = false)
    }

    /** Bỏ vai mặt của [view] (thẻ đổi sang nền mang thông tin — `KachiGlass.plain`) ⇒ chữ trong nó theo mặt cha. */
    fun clear(view: View) {
        (view.getTag(R.id.kachi_ink_surface) as? Surface)?.let { view.removeOnLayoutChangeListener(it) }
        view.setTag(R.id.kachi_ink_surface, null)
    }

    /** `KachiGlass.refresh` đi qua mọi view: mặt THƯỜNG (không kính) tính lại ở đây; mặt kính tính trong `paint`/`relocate`. */
    fun onRefresh(view: View) {
        if (view.getTag(R.id.kachi_glass_spec) == null && view.getTag(R.id.kachi_ink_surface) is Surface) update(view, force = true)
    }

    /**
     * Tính lại nền của mặt [view] (khi vùng / ảnh / chủ đề / độ đục đổi — [force] bỏ qua phép so vùng) rồi tô lại mọi mực dưới
     * nó. Chưa gắn cửa sổ / chưa đo ⇒ thôi (vùng 0×0 là vùng giả): lượt layout đầu sẽ gọi lại. Vùng đo bằng
     * `getLocationInWindow` (gồm `translation` — nhãn/đĩa trên xe 3D dời bằng translation, không layout).
     *
     * Nền của mặt ĐỔI (hoặc [cascade] — nguồn lớp riêng của mặt đổi mà vùng giữ: ảnh nền xe 3D, màu sơn) ⇒ mọi mặt con tính lại
     * luôn (nền của chúng ghép lớp của mặt này). Mặt dò ([Surface.onUnder]) nhận nền dưới nó TRƯỚC khi nền của chính nó được tính
     * (lớp của nó có thể đọc màu mặt vừa chọn).
     */
    fun update(view: View, force: Boolean = false, cascade: Boolean = false) {
        try { updateUnsafe(view, force, cascade) } catch (e: RuntimeException) { logW("lỗi giải mặt ${view.javaClass.simpleName}: $e") }
    }

    private fun updateUnsafe(view: View, force: Boolean, cascade: Boolean) {
        val s = view.getTag(R.id.kachi_ink_surface) as? Surface ?: return
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return
        val loc = IntArray(2).also { view.getLocationInWindow(it) }
        val same = s.rect.left == loc[0] && s.rect.top == loc[1] && s.rect.width() == view.width && s.rect.height() == view.height
        if (!force && !cascade && same && fresh(s)) { paintTree(view, s, false); return }
        s.rect.set(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height)
        val stale = !fresh(s)
        s.onUnder?.let { cb ->
            val u = backdropOf(view, s.rect, self = false)
            if (stale || cascade || !sameBackdrop(u, s.under)) { s.under = u; cb(u) }
        }
        val next = backdropOf(view, s.rect)
        val changed = stale || !sameBackdrop(next, s.backdrop)
        if (changed) { s.solved.clear(); s.solvedOver.clear() }
        s.backdrop = next; s.palette = KachiTheme.palette; s.fraction = KachiChrome.fraction; s.art = WallArtStore.current
        paintTree(view, s, cascade || changed)
    }

    /**
     * Nền ghép dưới vùng [r] (toạ độ cửa sổ) cho bộ vẽ CANVAS nằm trong [view] (chấm trên xe 3D): cùng phép [backdropOf] — ảnh
     * dưới vùng → làm tối → dải che → lớp mọi mặt bao [view]. Chỉ gọi theo SỰ KIỆN (bên gọi lưu kết quả), không trong `onDraw`.
     */
    fun backdropAt(view: View, r: Rect): ContrastBackdrop = backdropOf(view, r)

    /**
     * Nét [rider] vẽ CẠNH một mặt chứ không nằm trong nó (icon ⇄ đặt lên đĩa kính anh em — `SlotSwapButton.centered`) ⇒ giải mực
     * của [rider] trên mặt [host]; mặt [host] tính lại thì tô lại [rider]. Mặt [host] bị gỡ ⇒ [rider] theo mặt tổ tiên như cũ.
     */
    fun ride(rider: View, host: View) {
        host.setTag(R.id.kachi_ink_rider, rider)
        rider.setTag(R.id.kachi_ink_host, host)
    }

    /**
     * Việc của chỗ gọi sau mỗi lần tô mực của [v] (đã gọi [text]/[icon]); đã tô rồi ⇒ chạy ngay một lần. Chưa có dấu (mã không
     * phải vai) ⇒ không làm gì — chỗ gọi tự đi đường cũ.
     */
    fun onInk(v: View, after: (View, Resolution, ContrastBackdrop) -> Unit): Boolean {
        val m = v.getTag(R.id.kachi_ink_mark) as? Mark ?: return false
        m.after = after
        val r = m.last; val g = m.ground
        if (r != null && g != null) after(v, r, g)
        return true
    }

    /** Gỡ vai mực khỏi [v] (chỗ tô chuyển sang bộ lọc màu riêng — icon mặt lớn chủ đề tối) + gỡ lớp đỡ đã đặt. */
    fun release(v: View) {
        v.setTag(R.id.kachi_ink_mark, null)
        dropHalo(v)
    }

    /** Nền GỐC của [v] (bỏ lớp đỡ [HaloLayer] nếu có) — chỗ gọi đổi màu mặt của chính nó (đĩa ghế) đọc qua đây. */
    fun base(v: View): Drawable? = (v.background as? HaloLayer)?.base ?: v.background

    /** Tô lại mực dưới mặt [view] bằng kết quả đã lưu (layout đổi mà vùng giữ: con mới dựng / dựng lại). */
    fun reapply(view: View) {
        val s = view.getTag(R.id.kachi_ink_surface) as? Surface ?: return
        if (s.backdrop == null || !fresh(s)) update(view, force = true)
        else try { paintTree(view, s, false) } catch (e: RuntimeException) { logW("lỗi tô lại mặt ${view.javaClass.simpleName}: $e") }
    }

    /**
     * Tô chữ [tv] bằng mực vai [hex] (một mã của bảng đang dùng, vd `KachiTheme.MUT`) giải trên nền của mặt gần nhất. Trả mực
     * đã tô. Mã không phải vai nào / chưa nằm trong mặt nào ⇒ tô đúng [hex] (chưa đo thì không đoán); mặt tính xong sẽ tô lại.
     * [compound] = icon dẫn đầu (`compoundDrawables`) mang cùng mực.
     */
    fun text(tv: TextView, hex: String, compound: Boolean = false): Int = ink(tv, hex, compound)

    /** Như [text] cho nét icon ([ImageView.setColorFilter]); vai WCAG 1.4.11 (3:1). */
    fun icon(iv: ImageView, hex: String): Int = ink(iv, hex, false)

    /** Ranh giới fail-soft (P0 14/10: NPE trong lớp đỡ đẩy widget 3D về 2D) — mực thích ứng KHÔNG BAO GIỜ ném vào chỗ gọi. */
    private fun ink(v: View, hex: String, compound: Boolean): Int = try { inkUnsafe(v, hex, compound) } catch (e: RuntimeException) {
        logW("lỗi mực thích ứng ${v.javaClass.simpleName}/$hex: $e — tô mã thô")
        c(hex).also { runCatching { paintRaw(v, it, compound) } }
    }

    private fun inkUnsafe(v: View, hex: String, compound: Boolean): Int {
        val key = InkKey.of(hex, KachiTheme.palette)
        val prev = v.getTag(R.id.kachi_ink_mark) as? Mark
        if (key == null) {
            v.setTag(R.id.kachi_ink_mark, null)
            return c(hex).also { paintRaw(v, it, compound) }
        }
        val mark = Mark(key, compound, prev?.after)
        v.setTag(R.id.kachi_ink_mark, mark)
        val host = surfaceOf(v)
        // Bảng màu / ảnh / độ đục đã đổi mà mặt chưa tính lại (chỗ tô chạy trước `KachiGlass.refresh`) ⇒ tính ngay một lượt.
        if (host != null && host.second.backdrop != null && !fresh(host.second)) update(host.first, force = true)
        val s = host?.second?.takeIf { it.backdrop != null && fresh(it) }
        val r = s?.let { solve(v, mark, it) } ?: return c(hex).also { paintRaw(v, it, compound) }
        return paint(v, mark, r, s)
    }

    /**
     * Chữ [tv] vừa ĐỔI CHUỖI (chuỗi mới mang [InkSpan] màu thô) ⇒ tô lại đoạn màu bằng kết quả đã lưu của mặt, không chờ nhịp
     * tô kế (`applyChipText` đặt chữ SAU lượt tô mặt chip). Chưa có dấu / mặt chưa tính ⇒ không làm gì.
     */
    fun spans(tv: TextView) {
        val m = tv.getTag(R.id.kachi_ink_mark) as? Mark ?: return
        val s = surfaceOf(tv)?.second?.takeIf { it.backdrop != null && fresh(it) } ?: return
        try { solve(tv, m, s)?.let { paint(tv, m, it, s) } } catch (e: RuntimeException) { logW("lỗi đoạn màu: $e") }
    }

    private fun paintRaw(v: View, color: Int, compound: Boolean) {
        when (v) {
            is TextView -> { v.setTextColor(color); if (compound) v.compoundDrawablesRelative.forEach { it?.setTint(color) } }
            is ImageView -> v.setColorFilter(color)
        }
        dropHalo(v)
    }

    private fun dropHalo(v: View) {
        when (val bg = v.background) {
            is InkHalo -> v.background = null
            is HaloLayer -> v.background = bg.base
        }
    }

    /** Mặt gần nhất bao [v] (kể cả chính nó; [ride] đứng trước) — đi ngược cây cha; `null` khi chưa gắn vào mặt nào. */
    private fun surfaceOf(v: View): Pair<View, Surface>? {
        (v.getTag(R.id.kachi_ink_host) as? View)?.let { h -> (h.getTag(R.id.kachi_ink_surface) as? Surface)?.let { return h to it } }
        var p: View? = v
        while (p != null) {
            (p.getTag(R.id.kachi_ink_surface) as? Surface)?.let { return p!! to it }
            p = p.parent as? View
        }
        return null
    }

    private fun fresh(s: Surface) =
        s.palette === KachiTheme.palette && s.fraction == KachiChrome.fraction && s.art === WallArtStore.current

    /** Hai nền ghép như nhau (hộp gốc + chồng lớp) — so để KHÔNG giải lại khi sự kiện không đổi nền. */
    internal fun sameBackdrop(a: ContrastBackdrop?, b: ContrastBackdrop?) =
        a != null && b != null && a.base.lo == b.base.lo && a.base.hi == b.base.hi && a.layers == b.layers

    /**
     * Nền ghép dưới vùng [r] của mặt [view]: ảnh (hoặc nền vẽ) → làm tối → dải che → lớp mọi mặt từ ngoài vào trong. [self] =
     * `false` ⇒ bỏ lớp của chính [view] (nền DƯỚI mặt dò).
     */
    private fun backdropOf(view: View, r: Rect, self: Boolean = true): ContrastBackdrop {
        val p = KachiTheme.palette
        val art = WallArtStore.current
        val chain = ArrayList<Surface>()
        var v: View? = if (self) view else view.parent as? View
        while (v != null) { (v.getTag(R.id.kachi_ink_surface) as? Surface)?.let { chain.add(0, it) }; v = v.parent as? View }
        val groups = ArrayList<List<List<Layer>>>()
        val base: ToneBox
        if (art != null) {
            val wall = art.backdropAt(r.left, r.top, r.right, r.bottom, p)
            base = wall.base
            groups += listOf(wall.layers)
            groups += InkModel.bandVariants(p, KachiChrome.fraction, r.top, r.bottom, art.screenH, WallView.BAND_FRACTION)
        } else {
            base = InkModel.plainWall(p)
        }
        chain.forEach { groups += it.groups(r) }
        return ContrastStack.compose(base, groups)
    }

    private fun solve(v: View, mark: Mark, s: Surface): Resolution? {
        val b = s.backdrop ?: return null
        val role = InkModel.role(mark.key, icon = v is ImageView, large = v is TextView && large(v))
        val slot = mark.key.ordinal * 8 + role.ordinal
        return s.solved[slot] ?: ContrastInk.resolve(role, b, InkModel.candidates(mark.key, KachiTheme.palette)).also {
            s.solved[slot] = it
            if (!it.ok) logW("dưới sàn: ${v.javaClass.simpleName}/${mark.key} ${it.describe()} nền ${b.ground().let { g -> ColorMath.hex(g.lo) + "…" + ColorMath.hex(g.hi) }}")
        }
    }

    private fun large(tv: TextView): Boolean {
        val sp = tv.textSize / tv.resources.displayMetrics.density
        val bold = tv.typeface?.isBold == true || tv.paint.isFakeBoldText
        return sp >= InkModel.LARGE_SP || (bold && sp >= InkModel.LARGE_BOLD_SP)
    }

    /**
     * Đi cây dưới mặt [root]: tô mọi phần tử có [Mark]; gặp mặt con ⇒ để mặt con tự tính (nền của nó khác; [cascade] ⇒ ép tính
     * lại vì lớp của mặt này đã đổi). Người đi kèm ([ride]) của [root] tô cùng lượt.
     */
    private fun paintTree(root: View, s: Surface, cascade: Boolean) {
        fun walk(v: View) {
            // Người đi kèm của một mặt KHÁC (còn sống) ⇒ mặt đó tô nó, không phải mặt tổ tiên này.
            val rides = (v.getTag(R.id.kachi_ink_host) as? View)?.takeIf { it !== root && it.getTag(R.id.kachi_ink_surface) is Surface }
            if (rides == null) (v.getTag(R.id.kachi_ink_mark) as? Mark)?.let { m -> solve(v, m, s)?.let { paint(v, m, it, s) } }
            if (v is ViewGroup) for (i in 0 until v.childCount) {
                val ch = v.getChildAt(i)
                if (ch.getTag(R.id.kachi_ink_surface) is Surface) update(ch, force = cascade) else walk(ch)
            }
        }
        walk(root)
        (root.getTag(R.id.kachi_ink_rider) as? View)?.takeIf { it.getTag(R.id.kachi_ink_host) === root }?.let { walk(it) }
    }

    /**
     * Tô [v]: mực [r] (+ đoạn màu [InkSpan]); MỘT lớp đỡ dưới phần tử = lớp đỡ của chữ, hoặc (chữ đạt mà một đoạn cần đỡ) lớp đỡ
     * của đoạn đó — khi ấy chữ và mọi đoạn giải lại TRÊN lớp đỡ ấy. Đoạn vẫn cần thêm lớp ⇒ log (không có lớp thứ hai).
     */
    private fun paint(v: View, m: Mark, r: Resolution, s: Surface): Int {
        val b = s.backdrop!!
        var main = r
        var halo = if (r.reinforced) r.haloColor() else 0
        val spans = ((v as? TextView)?.text as? Spanned)?.let { it.getSpans(0, it.length, InkSpan::class.java) }.orEmpty()
        if (halo == 0) spans.firstNotNullOfOrNull { spanSolve(v as TextView, it, s, 0)?.takeIf { x -> x.reinforced } }?.let {
            halo = it.haloColor()
            main = over(v, m, s, halo, InkModel.role(m.key, icon = v is ImageView, large = v is TextView && large(v)))
        }
        var dirty = false
        for (sp in spans) spanSolve(v as TextView, sp, s, halo)?.let { sr ->
            if (sr.reinforced) logW("đoạn màu cần lớp đỡ thứ hai: ${sp.hex} ${sr.describe()}")
            if (sp.color != sr.ink) { sp.color = sr.ink; dirty = true }
        }
        when (v) {
            is TextView -> { v.setTextColor(main.ink); if (m.compound) v.compoundDrawablesRelative.forEach { it?.setTint(main.ink) } }
            is ImageView -> v.setColorFilter(main.ink)
        }
        if (dirty) v.invalidate()
        if (halo != 0) placeHalo(v, halo) else dropHalo(v)
        m.last = main; m.ground = if (halo != 0) b.with(Layer.Flat(halo)) else b
        m.after?.invoke(v, main, m.ground!!)
        return main.ink
    }

    /** Đặt lớp đỡ — ranh giới fail-soft riêng: hỏng ⇒ log (qua [LogLineThrottle]) và để phần tử không lớp đỡ, không ném. */
    private fun placeHalo(v: View, color: Int) = try { placeHaloUnsafe(v, color) } catch (e: RuntimeException) {
        logW("không đặt được lớp đỡ ${v.javaClass.simpleName}: $e")
    }

    private fun placeHaloUnsafe(v: View, color: Int) {
        when (val bg = v.background) {
            is InkHalo -> bg.setColor(color)
            is HaloLayer -> bg.halo.setColor(color)
            null -> v.background = InkHalo(KachiSpace.dpf(v.context, KachiSpace.RADIUS_S)).apply { setColor(color) }
            else -> v.background = HaloLayer(bg, haloLike(v, bg).apply { setColor(color) })
        }
    }

    /** Lớp đỡ CÙNG HÌNH nền sẵn có (bầu dục / bo góc của chính nó); nền không phải hình khối ⇒ bo [KachiSpace.RADIUS_S]. */
    private fun haloLike(v: View, bg: Drawable): InkHalo = InkHalo(KachiSpace.dpf(v.context, KachiSpace.RADIUS_S)).apply {
        val g = bg as? GradientDrawable ?: return@apply
        shape = g.shape
        // `getCornerRadii()` ném NPE (`float[].clone()` trên mảng null) khi nền chỉ đặt `cornerRadius` [ĐO máy ảo 14/10, AOSP 10] ⇒ an toàn.
        val radii = runCatching { g.cornerRadii }.getOrNull()
        if (radii != null) cornerRadii = radii else cornerRadius = runCatching { g.cornerRadius }.getOrDefault(cornerRadius)
    }

    /** Mực vai của [m] trên nền + lớp đỡ [halo] (lưu theo (ô vai, màu lớp đỡ)). */
    private fun over(v: View, m: Mark, s: Surface, halo: Int, role: InkRole): Resolution {
        val id = ((m.key.ordinal * 8 + role.ordinal).toLong() shl 32) or (halo.toLong() and 0xffffffffL)
        return s.solvedOver.getOrPut(id) {
            ContrastInk.resolve(role, s.backdrop!!.with(Layer.Flat(halo)), InkModel.candidates(m.key, KachiTheme.palette)).also {
                if (!it.ok || it.reinforced) logW("dưới sàn trên lớp đỡ: ${v.javaClass.simpleName}/${m.key} ${it.describe()}")
            }
        }
    }

    /** Mực của đoạn [sp] (vai từ mã của đoạn; đoạn nhỏ hơn chữ ⇒ luôn vai chữ thường) trên nền (+ [halo] nếu ≠ 0). */
    private fun spanSolve(tv: TextView, sp: InkSpan, s: Surface, halo: Int): Resolution? {
        val key = InkKey.of(sp.hex, KachiTheme.palette) ?: return null
        val role = InkModel.role(key, icon = false, large = false)
        if (halo != 0) return over(tv, Mark(key, false), s, halo, role)
        val slot = key.ordinal * 8 + role.ordinal
        return s.solved[slot] ?: ContrastInk.resolve(role, s.backdrop!!, InkModel.candidates(key, KachiTheme.palette)).also {
            s.solved[slot] = it
            if (!it.ok) logW("dưới sàn: đoạn ${sp.hex} ${it.describe()}")
        }
    }

    private fun logW(line: String) {
        throttle.suppressedBefore(line, SystemClock.elapsedRealtime())?.let { Log.w(TAG, line + LogLineThrottle.repeatSuffix(it)) }
    }
}
