package com.byd.clusternav.launcher

import android.graphics.Rect
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
 * ═══ 3.1.6 R29 (làm lại) — MỰC THÍCH ỨNG theo MẶT: mỗi bề mặt MỘT họ mực, mọi chữ/icon trên nó đổi CÙNG NHAU ═══════════════
 *
 * Owner 14/10: *"các text, màu nét icon cần phải đo được độ tương phản với nền… để luôn luôn nhìn thấy được"*. Owner 15/10 (bác
 * bản 3.1.5 vá đệm nền dưới từng chữ): *"phải làm đổi màu chữ tự động, đồng đều, chứ không vá thế này được em, rất tởm"*. Spec
 * `kachi-301-cast-steps-dialog-ia.html` §R29 (Pass 12) · §4.7. Tệp này KHÔNG đặt nền nào lên phần tử nào.
 *
 * ## Ba vai
 *  • **Mặt** ([surface]) — view có nền riêng khai các lớp nó vẽ. Phạm vi ([Scope]): [Scope.CARD] (thẻ · thanh trên · thanh nút —
 *    lồng trong một thẻ khác thì theo thẻ ngoài: ô con, đĩa kính ⇄), [Scope.OWN] (luôn tự chọn họ: ô nút, lớp phủ xe 3D),
 *    [Scope.LAYER] (chỉ là một lớp nền dưới phần tử: chip, pill, nút DIM, đĩa ghế, vùng nhãn 3D, nút đĩa). Mặt GỐC (OWN gần nhất,
 *    không có thì CARD ngoài cùng) chọn MỘT họ ([ContrastInk.choose]) cho mọi phần tử trong nó.
 *  • **Mực** ([text] / [icon]) — chỗ tô gọi đây THAY `setTextColor(c(hex))`: mã màu cho biết vai ([InkKey.of]), view cho biết loại
 *    ([InkModel.role]). Mực tô = [InkModel.ink] (vai, họ của mặt gốc); nền để ĐO = nền của mặt gần nhất (gồm lớp chip/đĩa dưới nó).
 *  • **Bộ vẽ tự tô** ([probe]: nút đĩa [KachiButtonInks], chấm 3D [Car3dDotsLayer]) — góp điểm của chính nét nó vào phép chọn họ
 *    và nhận họ đã chọn ⇒ cũng đổi cùng mặt.
 *
 * Không họ nào đạt (ảnh rất rối) ⇒ họ tốt nhất, CẢ mặt vẫn đồng đều, `Log.w` qua [LogLineThrottle] + [ContrastLedger]. Không nâng
 * độ đục thẻ / mức làm tối (lựa chọn của người dùng — owner 04/10 *"tùy user chọn"*); lớp nhuộm cả thẻ là OQ7' cho owner.
 *
 * ## Chi phí (perf-every-fix) — chỉ khi SỰ KIỆN đổi
 * Nền tính khi mặt đổi chỗ/cỡ, khi ảnh / chủ đề / độ đục đổi ([fresh]). Chọn họ chỉ khi một nền đổi, có phần tử MỚI, hoặc bộ vẽ
 * tự tô xin ([rescore]); nhịp xe 1 Hz tô lại chữ chỉ tra kết quả đã lưu. `onDraw` không gọi gì ở đây. 0 luồng · 0 nhịp. Nội dung
 * ĐANG DỜI (xoay 3D, cuộn) ⇒ lượt sự kiện GIỮ, chọn lại một lần khi dừng ([InkMotion], perf §8.1) — một hẹn, không nhịp lặp.
 */
object AdaptiveInk {

    private const val TAG = "KachiContrast"
    private val throttle = LogLineThrottle()

    /** Phạm vi chọn họ của một mặt — xem KDoc tệp. */
    enum class Scope { LAYER, CARD, OWN }

    /**
     * Bộ vẽ tự tô góp vào phép chọn họ: [prepare] một lần mỗi lượt chọn · [score] = min(tỉ số/sàn) của nét nó dưới họ, với mặt đã
     * thiết kế thứ [alt] (0 until [alts]) · [apply]. Bộ vẽ cùng [group] trên một mặt gốc dùng CHUNG một [alt] (mọi nút đĩa của mặt
     * cùng một kiểu đĩa — không nút nào tự chọn đĩa riêng); [alt] chọn cho nhóm = kiểu để điểm MIN của cả nhóm cao nhất.
     */
    interface Probe {
        fun prepare() {}
        val alts: Int get() = 1
        val group: Any? get() = null
        fun score(f: InkFamily, alt: Int): Double
        fun apply(f: InkFamily, alt: Int)
    }

    /**
     * Đoạn chữ mang vai màu RIÊNG trong một chữ đã nối (đơn vị `withUnit` · số chip lốp) — thay `ForegroundColorSpan`. Màu ban
     * đầu = đúng mã [hex]; lượt tô của chữ chứa nó tô lại theo HỌ của mặt (cùng họ với chữ).
     */
    class InkSpan(val hex: String) : CharacterStyle(), UpdateAppearance {
        var color: Int = c(hex)
        override fun updateDrawState(tp: TextPaint) { tp.color = color }
    }

    /**
     * Khai [view] là một MẶT phạm vi [scope]. [listen] = gắn `OnLayoutChangeListener` (mặt thường); mặt kính `false` vì
     * `WallWindowDrawable.relocate` gọi [update]. Cùng [variant] (≠ `null`) ⇒ giữ nguyên. Họ đang hiện của mặt cũ được GIỮ (trễ).
     * [under] = đo thêm nền dưới mặt không gồm lớp của nó (nút đĩa).
     */
    fun surface(
        view: View, listen: Boolean, scope: Scope, variant: Any? = null, under: Boolean = false, groups: (Rect) -> List<List<List<Layer>>>,
    ) {
        val old = view.getTag(R.id.kachi_ink_surface) as? InkSurface
        if (old != null && variant != null && old.variant == variant && old.listen == listen && old.scope == scope) return
        clear(view)
        val s = InkSurface(variant, listen, scope, under, groups).also { it.family = old?.family ?: -1 }
        view.setTag(R.id.kachi_ink_surface, s)
        if (listen) { view.addOnLayoutChangeListener(s); update(view, force = true) }
    }

    /** Mặt thường chỉ tô màu nền [fill] (bảng SỐNG, chưa nhân độ đục; [fades] = nhân [KachiChrome.fraction]). */
    fun fill(
        view: View, scope: Scope = Scope.CARD, fades: Boolean = true, variant: Any? = "fill", extra: () -> List<Layer> = { emptyList() },
        fill: () -> IntArray,
    ) = surface(view, listen = true, scope = scope, variant = variant) {
        val f = if (fades) KachiChrome.fraction else 1.0
        val pair = fill().map { ColorMath.withAlpha(it, ChromeOpacity.drawnAlpha(ColorMath.alpha(it), f)) }.toIntArray()
        listOf(ContrastStack.surfaceVariants(pair).map { it + extra() })
    }

    /** R29 T29-7 — Ô NÚT: TẮT = bề mặt trung tính × độ đục chung; BẬT = `tonalOn`. Ô có mặt riêng ⇒ tự chọn họ ([Scope.OWN]). */
    fun tile(view: View, active: Boolean) = fill(view, Scope.OWN, fades = !active, variant = if (active) "tile-on" else "tile-off",
        extra = { if (active) listOf(Layer.Flat(c(KachiTheme.TILE_ON_FROM))) else emptyList() }) {
        KachiTheme.surfacePair(SurfaceTone.NEUTRAL, overArtwork = false)
    }

    /**
     * MẶT ĐÃ THIẾT KẾ đi theo họ (pill thanh trên · nút −/+ DIM): nền của [view] tô bằng vai [fill] của BẢNG của họ đang hiện
     * (không phải của chủ đề) ⇒ đổi họ thì mặt + nét trên nó đổi cùng nhau, không mặt tối nào mắc kẹt dưới mực tối. Mặt lớp
     * ([Scope.LAYER], đo cả nền dưới nó) + bộ vẽ tự tô: góp điểm của nét [hex] trên mặt của từng họ; [repaint] tô màu mặt mới.
     * Cả mặt đổi màu ĐỀU (không phải lớp vá thêm) — đúng màu mặt cùng vai của bảng kia.
     */
    fun face(view: View, hex: String, fill: (KachiPalette) -> Int, repaint: (Int) -> Unit) {
        faces.remove(view)
        fun pal() = faces[view] ?: KachiTheme.palette
        surface(view, listen = true, scope = Scope.LAYER, variant = "face:$hex:${fill(KachiTheme.palette)}", under = true) {
            listOf(listOf(listOf(Layer.Flat(fill(pal())))))
        }
        probe(view, object : Probe {
            override fun score(f: InkFamily, alt: Int): Double {
                val u = under(view) ?: return Double.MAX_VALUE
                val key = InkKey.of(hex, KachiTheme.palette) ?: return Double.MAX_VALUE
                val r = role(view, key)
                return u.with(Layer.Flat(fill(f.palette))).worst(InkModel.ink(key, f)) / r.floor
            }
            override fun apply(f: InkFamily, alt: Int) {
                if (pal() === f.palette) return
                faces[view] = f.palette
                repaint(fill(f.palette))
            }
        })
    }

    /** Bảng của họ đang tô mỗi [face] (khoá yếu — view gỡ thì rơi). CHỈ luồng chính. */
    private val faces = java.util.WeakHashMap<View, KachiPalette>()

    /** Gắn bộ vẽ tự tô [probe] lên [view] (`null` = gỡ); mặt gốc chứa [view] tính lại họ. */
    fun probe(view: View, probe: Probe?) {
        view.setTag(R.id.kachi_ink_probe, probe)
        surfaceOf(view)?.let { update(it.first) }
    }

    /** Nền dưới mặt [view] không gồm lớp của nó (mặt khai `under = true`); `null` = chưa đo. */
    fun under(view: View): ContrastBackdrop? = (view.getTag(R.id.kachi_ink_surface) as? InkSurface)?.under

    /** Bỏ vai mặt của [view] (thẻ đổi sang nền mang thông tin — `KachiGlass.plain`) ⇒ chữ trong nó theo mặt cha. */
    fun clear(view: View) {
        (view.getTag(R.id.kachi_ink_surface) as? InkSurface)?.let { view.removeOnLayoutChangeListener(it) }
        view.setTag(R.id.kachi_ink_surface, null)
    }

    /** `KachiGlass.refresh` đi qua mọi view: mặt THƯỜNG (không kính) tính lại ở đây; mặt kính tính trong `paint`/`relocate`. */
    fun onRefresh(view: View) {
        if (view.getTag(R.id.kachi_glass_spec) == null && view.getTag(R.id.kachi_ink_surface) is InkSurface) update(view, force = true)
    }

    /**
     * Tính lại nền của mặt [view] (vùng / ảnh / chủ đề / độ đục đổi — [force] bỏ qua phép so vùng; [cascade] = lớp riêng của mặt đổi
     * mà vùng giữ) rồi chọn lại họ của mặt gốc chứa nó nếu có gì đổi, và tô lại. Chưa gắn cửa sổ / chưa đo ⇒ thôi.
     */
    fun update(view: View, force: Boolean = false, cascade: Boolean = false) {
        try { updateUnsafe(view, force, cascade, rescore = false) } catch (e: RuntimeException) { logW("lỗi giải mặt ${view.javaClass.simpleName}: $e") }
    }

    /** Bộ vẽ tự tô của mặt [view] đổi chỗ (xoay xe 3D dừng) ⇒ chọn lại họ dù nền các mặt không đổi. */
    fun rescore(view: View) {
        try { updateUnsafe(view, force = false, cascade = false, rescore = true) } catch (e: RuntimeException) { logW("lỗi chấm lại ${view.javaClass.simpleName}: $e") }
    }

    private fun updateUnsafe(view: View, force: Boolean, cascade: Boolean, rescore: Boolean) {
        val s = view.getTag(R.id.kachi_ink_surface) as? InkSurface ?: return
        val root = rootOf(view) ?: return
        if (root.first === view) { refresh(view, s, force, cascade, rescore); return }
        if (!force && !cascade && !rescore && root.second.choice != null && s.backdrop != null && fresh(s) && moved(view, s.rect)) { hold(root.first, root.second); return }
        if (!measure(view, s, force || cascade)) return
        refresh(root.first, root.second, false, false, rescore || s.changed, view.takeIf { s.changed || cascade })
    }

    /** Nền ghép dưới vùng [r] (toạ độ cửa sổ) cho bộ vẽ CANVAS trong [view] (chấm trên xe 3D) — cùng phép [backdropOf]. */
    fun backdropAt(view: View, r: Rect): ContrastBackdrop = backdropOf(view, r)

    /** Nét [rider] vẽ CẠNH mặt [host] (icon ⇄ trên đĩa kính anh em) ⇒ đo trên mặt [host], cùng họ với mặt gốc của [host]. */
    fun ride(rider: View, host: View) {
        host.setTag(R.id.kachi_ink_rider, rider)
        rider.setTag(R.id.kachi_ink_host, host)
    }

    /** Việc của chỗ gọi sau mỗi lần tô mực của [v]; đã tô rồi ⇒ chạy ngay một lần. Chưa có dấu ⇒ `false`. */
    fun onInk(v: View, after: (View, Resolution, ContrastBackdrop) -> Unit): Boolean {
        val m = v.getTag(R.id.kachi_ink_mark) as? InkMark ?: return false
        m.after = after
        val r = m.last; val g = m.ground
        if (r != null && g != null) after(v, r, g)
        return true
    }

    /** Gỡ vai mực khỏi [v] (chỗ tô chuyển sang bộ lọc màu riêng — icon mặt lớn chủ đề tối). */
    fun release(v: View) { v.setTag(R.id.kachi_ink_mark, null) }

    /** Layout đổi mà vùng giữ (con mới dựng / dựng lại) ⇒ tô lại bằng kết quả đã lưu; phần tử mới ⇒ chọn lại họ. */
    fun reapply(view: View) {
        try { updateUnsafe(view, force = false, cascade = false, rescore = false) } catch (e: RuntimeException) { logW("lỗi tô lại mặt ${view.javaClass.simpleName}: $e") }
    }

    /** Tô chữ [tv] bằng vai [hex] (một mã của bảng đang dùng) theo họ của mặt chứa nó. Mã không phải vai / ngoài mọi mặt ⇒ đúng [hex]. */
    fun text(tv: TextView, hex: String, compound: Boolean = false): Int = ink(tv, hex, compound)

    /** Như [text] cho nét icon ([ImageView.setColorFilter]); vai WCAG 1.4.11 (3:1). */
    fun icon(iv: ImageView, hex: String): Int = ink(iv, hex, false)

    /** Ranh giới fail-soft (P0 14/10) — mực thích ứng KHÔNG BAO GIỜ ném vào chỗ gọi. */
    private fun ink(v: View, hex: String, compound: Boolean): Int = try { inkUnsafe(v, hex, compound) } catch (e: RuntimeException) {
        logW("lỗi mực thích ứng ${v.javaClass.simpleName}/$hex: $e — tô mã thô")
        c(hex).also { runCatching { paintRaw(v, it, compound) } }
    }

    private fun inkUnsafe(v: View, hex: String, compound: Boolean): Int {
        val key = InkKey.of(hex, KachiTheme.palette)
        val prev = v.getTag(R.id.kachi_ink_mark) as? InkMark
        if (key == null) { v.setTag(R.id.kachi_ink_mark, null); return c(hex).also { paintRaw(v, it, compound) } }
        val mark = prev?.takeIf { it.key == key && it.compound == compound } ?: InkMark(key, compound, prev?.after)
        v.setTag(R.id.kachi_ink_mark, mark)
        val host = surfaceOf(v) ?: return c(hex).also { paintRaw(v, it, compound) }
        val root = rootOf(host.first) ?: return c(hex).also { paintRaw(v, it, compound) }
        // Bảng màu / ảnh / độ đục đổi mà mặt chưa tính lại, phần tử chưa đo, hoặc phần tử MỚI (chưa có trong lượt chọn họ) ⇒ một lượt.
        val stale = host.second.backdrop != null && !fresh(host.second)
        val g = mark.ground
        if (stale || g == null || root.second.family < 0 || slot(v, key, role(v, key)) !in root.second.seen) {
            refresh(root.first, root.second, force = stale, cascade = false, rescore = true)
            return mark.last?.ink ?: c(hex).also { if (mark.last == null) paintRaw(v, it, compound) }
        }
        val f = root.second.family
        return paint(v, mark, resolve(g, key, role(v, key), f, InkModel.families(KachiTheme.palette)[f]), InkModel.families(KachiTheme.palette)[f])
    }

    /** Chữ [tv] vừa ĐỔI CHUỖI (chuỗi mới mang [InkSpan] màu thô) ⇒ tô lại đoạn màu theo họ đang hiện của mặt. */
    fun spans(tv: TextView) {
        val m = tv.getTag(R.id.kachi_ink_mark) as? InkMark ?: return
        val r = m.last ?: return
        try { paint(tv, m, r, InkModel.families(KachiTheme.palette)[r.family]) } catch (e: RuntimeException) { logW("lỗi đoạn màu: $e") }
    }

    private fun paintRaw(v: View, color: Int, compound: Boolean) {
        when (v) {
            is TextView -> { v.setTextColor(color); if (compound) v.compoundDrawablesRelative.forEach { it?.setTint(color) } }
            is ImageView -> v.setColorFilter(color)
        }
    }

    /** Mặt gần nhất bao [v] (kể cả chính nó; [ride] đứng trước) — đi ngược cây cha; `null` khi chưa gắn vào mặt nào. */
    private fun surfaceOf(v: View): Pair<View, InkSurface>? {
        (v.getTag(R.id.kachi_ink_host) as? View)?.let { h -> (h.getTag(R.id.kachi_ink_surface) as? InkSurface)?.let { return h to it } }
        var p: View? = v
        while (p != null) {
            (p.getTag(R.id.kachi_ink_surface) as? InkSurface)?.let { return p!! to it }
            p = p.parent as? View
        }
        return null
    }

    /** Mặt GỐC chọn họ cho mặt [view]: [Scope.OWN] gần nhất (kể cả chính nó), không có thì [Scope.CARD] ngoài cùng / mặt ngoài cùng. */
    private fun rootOf(view: View): Pair<View, InkSurface>? {
        var outer: Pair<View, InkSurface>? = null
        var card: Pair<View, InkSurface>? = null
        var p: View? = view
        while (p != null) {
            (p.getTag(R.id.kachi_ink_surface) as? InkSurface)?.let { s ->
                if (s.scope == Scope.OWN) return p!! to s
                if (s.scope == Scope.CARD) card = p!! to s
                outer = p!! to s
            }
            p = p.parent as? View
        }
        return card ?: outer
    }

    private fun fresh(s: InkSurface) =
        s.palette === KachiTheme.palette && s.fraction == KachiChrome.fraction && s.art === WallArtStore.current

    /** Hai nền ghép như nhau (hộp gốc + chồng lớp) — so để KHÔNG chọn lại khi sự kiện không đổi nền. */
    internal fun sameBackdrop(a: ContrastBackdrop?, b: ContrastBackdrop?) =
        a != null && b != null && a.base.lo == b.base.lo && a.base.hi == b.base.hi && a.layers == b.layers

    /** Đo nền của mặt [view] (vùng bằng `getLocationInWindow`, gồm translation). `false` = chưa đo được (chưa gắn / 0×0). */
    private fun measure(view: View, s: InkSurface, force: Boolean): Boolean {
        s.changed = false
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return false
        if (!force && !moved(view, s.rect) && fresh(s) && s.backdrop != null) return true
        s.rect.set(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height)
        val stale = !fresh(s)
        if (s.wantsUnder) s.under = backdropOf(view, s.rect, self = false)
        val next = backdropOf(view, s.rect)
        s.changed = stale || !sameBackdrop(next, s.backdrop)
        s.backdrop = next; s.palette = KachiTheme.palette; s.fraction = KachiChrome.fraction; s.art = WallArtStore.current
        return true
    }

    /** Nền ghép dưới vùng [r] của mặt [view]: ảnh (hoặc nền vẽ) → làm tối → dải che → lớp mọi mặt; [self] = `false` ⇒ bỏ lớp của [view]. */
    private fun backdropOf(view: View, r: Rect, self: Boolean = true): ContrastBackdrop {
        val p = KachiTheme.palette
        val art = WallArtStore.current
        val chain = ArrayList<InkSurface>()
        var v: View? = if (self) view else view.parent as? View
        while (v != null) { (v.getTag(R.id.kachi_ink_surface) as? InkSurface)?.let { chain.add(0, it) }; v = v.parent as? View }
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

    private fun role(v: View, key: InkKey) = InkModel.role(key, icon = v is ImageView, large = v is TextView && large(v))

    private fun large(tv: TextView): Boolean {
        val sp = tv.textSize / tv.resources.displayMetrics.density
        val bold = tv.typeface?.isBold == true || tv.paint.isFakeBoldText
        return sp >= InkModel.LARGE_SP || (bold && sp >= InkModel.LARGE_BOLD_SP)
    }

    /** Ô (phần tử, vai, loại) — một phần tử của phép chọn họ; view mới hoặc vai mới ⇒ phần tử mới ⇒ chọn lại họ. */
    private fun slot(v: View, key: InkKey, role: InkRole): Long =
        (System.identityHashCode(v).toLong() shl 16) or (key.ordinal * 8 + role.ordinal).toLong()

    /** Một phần tử thu được khi đi cây của mặt gốc: view + dấu + có thuộc bộ vẽ tự tô không. */
    private class Item(val view: View, val mark: InkMark, val probed: Boolean)

    /**
     * Đo nền tệ nhất dưới CHÍNH vùng của phần tử [v] (cùng phép [backdropOf]: ảnh dưới vùng → làm tối → dải che → lớp mọi mặt bao
     * [via] — chính [v], hoặc mặt chủ của người đi kèm). Chỉ đo khi vùng đổi hoặc [force] (mặt chứa nó vừa đổi). `true` = nền đổi.
     */
    private fun groundOf(v: View, via: View, m: InkMark, force: Boolean): Boolean {
        if (!v.isAttachedToWindow || v.width <= 0 || v.height <= 0) return false
        if (!force && !moved(v, m.rect) && m.ground != null) return false
        m.rect.set(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
        val next = backdropOf(via, m.rect)
        val changed = !sameBackdrop(next, m.ground)
        m.ground = next
        return changed
    }

    /**
     * Một lượt của mặt gốc [root]: đo các mặt con (đo lại khi [force] hoặc mặt cha của nó vừa đổi) → nếu có gì đổi ([force] ·
     * [rescore] · nền đổi · phần tử mới) thì CHỌN HỌ trên mọi phần tử + bộ vẽ tự tô → tô tất cả bằng họ đó → mặt gốc con ([Scope.OWN]).
     */
    private fun refresh(root: View, rs: InkSurface, force: Boolean, cascade: Boolean, rescore: Boolean, changedBelow: View? = null) {
        // Lượt SỰ KIỆN (không ép, không xin chọn lại): phần tử đã đo mà DỜI ⇒ giữ, chọn lại một lần khi dừng ([InkMotion]).
        val event = !force && !cascade && !rescore && !settling && rs.choice != null
        if (!event) rs.motion?.solving()
        if (event && rs.backdrop != null && fresh(rs) && moved(root, rs.rect)) { hold(root, rs); return }
        if (!measure(root, rs, force || cascade)) return
        val items = ArrayList<Item>(); val probes = ArrayList<Probe>(); val owns = ArrayList<View>(); val probed = ArrayList<View>()
        var dirty = force || cascade || rescore || rs.changed || rs.choice == null; var held = false
        fun walk(v: View, host: View, hostProbed: Boolean, forceBelow: Boolean) {
            var h = host; var hp = hostProbed; var fb = forceBelow || v === changedBelow
            val s = if (v !== root) v.getTag(R.id.kachi_ink_surface) as? InkSurface else null
            if (s != null) {
                if (s.scope == Scope.OWN) { owns += v; return }
                if (event && !fb && s.backdrop != null && fresh(s) && moved(v, s.rect)) held = true
                else if (measure(v, s, fb)) { if (s.changed) { dirty = true; fb = true }; h = v; hp = v.getTag(R.id.kachi_ink_probe) != null }
                if (hp && h === v) probed += v
            }
            (v.getTag(R.id.kachi_ink_probe) as? Probe)?.let { probes += it }
            // Người đi kèm của một mặt còn sống ⇒ mặt đó tô nó (đi qua từ chủ — [rideNow]), không phải chỗ nó đứng trong cây.
            val elsewhere = v !== rideNow && (v.getTag(R.id.kachi_ink_host) as? View)?.getTag(R.id.kachi_ink_surface) is InkSurface
            if (!elsewhere) (v.getTag(R.id.kachi_ink_mark) as? InkMark)?.let { m ->
                if (event && !fb && m.ground != null && moved(v, m.rect)) held = true
                else if (groundOf(v, if (v === rideNow) h else v, m, fb)) dirty = true
                if (m.ground != null) items += Item(v, m, hp)
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), h, hp, fb)
            if (s != null || v === root) (v.getTag(R.id.kachi_ink_rider) as? View)?.takeIf { it.getTag(R.id.kachi_ink_host) === v }?.let {
                rideNow = it; walk(it, h, hp, fb); rideNow = null
            }
        }
        walk(root, root, root.getTag(R.id.kachi_ink_probe) != null, force || cascade || rs.changed)
        if (held) { hold(root, rs); return }   // [ĐO máy ảo 15/10] xoay 3D: ~53 lượt chọn/đợt → chỉ lượt dừng
        val fams = InkModel.families(KachiTheme.palette)
        val seen = HashSet<Long>(items.size * 2)
        for (it in items) seen += slot(it.view, it.mark.key, role(it.view, it.mark.key))
        if (seen != rs.seen) dirty = true
        if (dirty) {
            decide(root, rs, items, probes, fams, seen)
            // Bộ vẽ tự tô vừa đổi công thức (đĩa nút) ⇒ lớp của mặt nó đổi ⇒ đo lại để nét bên trong (icon lấy gió) đo đúng đĩa mới.
            for (v in probed) (v.getTag(R.id.kachi_ink_surface) as? InkSurface)?.let { measure(v, it, true) }
            for (it in items) if (it.probed) groundOf(it.view, it.view, it.mark, true)
        }
        val f = rs.family.coerceAtLeast(0)
        for (it in items) paint(it.view, it.mark, resolve(it.mark.ground!!, it.mark.key, role(it.view, it.mark.key), f, fams[f]), fams[f])
        for (o in owns) (o.getTag(R.id.kachi_ink_surface) as? InkSurface)?.let { refresh(o, it, force || cascade || rs.changed, cascade, rescore) }
    }

    /** Người đi kèm đang được đi qua từ mặt chủ của nó (không bị bỏ qua như khi gặp nó ở chỗ đứng thật trong cây). */
    private var rideNow: View? = null

    /** Vùng đã đo [r] của [v] (đã gắn, khác 0×0) khác chỗ/cỡ hiện tại (toạ độ cửa sổ, gồm translation). CHỈ luồng chính. */
    private fun moved(v: View, r: Rect): Boolean {
        if (!v.isAttachedToWindow || v.width <= 0 || v.height <= 0) return false
        v.getLocationInWindow(loc); return r.left != loc[0] || r.top != loc[1] || r.width() != v.width || r.height() != v.height
    }
    private val loc = IntArray(2)

    /**
     * Nội dung mặt gốc [root] đang dời ⇒ hẹn (lại) MỘT lượt khi lặng [InkMotion.QUIET_MS]: lượt THƯỜNG không giữ ([settling]) — đo cái
     * đã dời, chỉ chọn lại họ khi nền thật sự đổi (như trước khi giữ; [ĐO máy ảo 15/10] ép chọn mỗi lần dừng: +0,11 s
     * luồng chính 30 s đầu khởi động). Bộ vẽ tự tô dời theo chỗ chiếu tự xin [rescore] (`Car3dInk.settle`). Fail-soft qua [update].
     */
    private fun hold(root: View, rs: InkSurface) =
        (rs.motion ?: InkMotion(InkMotion.QUIET_MS, { r, ms -> root.postDelayed(r, ms) }, { root.removeCallbacks(it) }) {
            settling = true; try { update(root) } finally { settling = false }
        }.also { rs.motion = it }).hold()
    private var settling = false

    /** Chọn MỘT họ cho mặt gốc [root] từ mọi phần tử (trừ phần tử của bộ vẽ tự tô — bộ vẽ tự chấm nét của nó) + bộ vẽ tự tô. */
    private fun decide(root: View, rs: InkSurface, items: List<Item>, probes: List<Probe>, fams: List<InkFamily>, seen: Set<Long>) {
        val needs = ArrayList<InkNeed>(items.size); val owners = ArrayList<String>(items.size)
        for (it in items) {
            if (it.probed) continue
            val g = it.mark.ground ?: continue
            needs += InkNeed(role(it.view, it.mark.key), g, IntArray(fams.size) { f -> InkModel.ink(it.mark.key, fams[f]) })
            owners += "${it.view.javaClass.simpleName}/${it.mark.key}"
            ((it.view as? TextView)?.text as? Spanned)?.let { sp -> sp.getSpans(0, sp.length, InkSpan::class.java) }?.forEach { sp ->
                val k = InkKey.of(sp.hex, KachiTheme.palette) ?: return@forEach
                needs += InkNeed(InkModel.role(k, icon = false, large = false), g, IntArray(fams.size) { f -> InkModel.ink(k, fams[f]) })
                owners += "span/$k"
            }
        }
        probes.forEach { it.prepare() }
        // Mỗi họ × mỗi nhóm bộ vẽ: MỘT kiểu mặt cho cả nhóm (điểm MIN của nhóm cao nhất), rồi điểm từng bộ vẽ theo kiểu đó.
        val groups = probes.indices.groupBy { probes[it].group ?: probes[it] }.values
        val alt = Array(fams.size) { IntArray(probes.size) }
        val probeScores = Array(fams.size) { f ->
            val out = DoubleArray(probes.size)
            for (g in groups) {
                val n = probes[g[0]].alts
                val best = (0 until n).maxByOrNull { a -> g.minOf { probes[it].score(fams[f], a) } } ?: 0
                for (i in g) { alt[f][i] = best; out[i] = probes[i].score(fams[f], best) }
            }
            out
        }
        val scores = DoubleArray(fams.size) { f -> minOf(ContrastInk.score(needs, f), probeScores[f].minOrNull() ?: Double.MAX_VALUE) }
        val pass = ContrastInk.passes(needs, fams.size).also { p -> for (f in p.indices) p[f] += probeScores[f].count { it >= 1.0 } }
        val prev = rs.choice
        val choice = ContrastInk.choose(scores, rs.family, pass)
        rs.family = choice.family; rs.choice = choice; rs.seen = seen
        probes.forEachIndexed { i, p -> p.apply(fams[choice.family], alt[choice.family][i]) }
        val name = root.javaClass.simpleName
        if (prev == null || prev.family != choice.family || prev.ok != choice.ok) {   // perf §8.1: chỉ khi họ / đạt-sàn đổi
            Log.d(TAG, "R29 họ: $name ${choice.describe()} · ${needs.size} nét + ${probes.size} bộ vẽ · điểm ${scores.joinToString("/") { if (it > 1e6) "∞" else "%.2f".format(it) }} · đạt ${pass.joinToString("/")}/${needs.size + probes.size}")
        }
        if (!choice.ok) {
            // Mỗi họ: phần tử tệ nhất + hộp nền của nó — đủ để chốt bằng log vì sao không họ nào đạt (không đoán).
            val why = fams.indices.joinToString(" | ") { f ->
                val w = needs.indices.minByOrNull { ContrastInk.margin(needs[it], f) } ?: return@joinToString "f#$f -"
                val g = needs[w].ground.ground()
                "f#$f ${owners[w]} %.2f bg ${ColorMath.hex(g.lo)}..${ColorMath.hex(g.hi)}".format(ContrastInk.margin(needs[w], f) * needs[w].role.floor)
            }
            logW("dưới sàn: mặt $name ${choice.describe()} · $why", key = "dưới sàn:$name#${choice.family}")   // nền đổi từng px ⇒ khoá theo mặt+họ
        }
    }

    /** Mực vai [key] dưới họ [f] (mực đục ⇒ 2 phép đo trên nền [ground] của chính phần tử). */
    private fun resolve(ground: ContrastBackdrop, key: InkKey, role: InkRole, f: Int, fam: InkFamily): Resolution {
        val ink = InkModel.ink(key, fam)
        val w = ground.worst(ink)
        return Resolution(role, ink, f, w, w >= role.floor - 1e-9)
    }

    /** Tô [v] bằng [r] (+ mọi đoạn màu [InkSpan] theo CÙNG họ [fam]). Không đặt nền nào. */
    private fun paint(v: View, m: InkMark, r: Resolution, fam: InkFamily): Int {
        var dirty = false
        ((v as? TextView)?.text as? Spanned)?.let { s -> s.getSpans(0, s.length, InkSpan::class.java) }?.forEach { sp ->
            val k = InkKey.of(sp.hex, KachiTheme.palette) ?: return@forEach
            val ink = InkModel.ink(k, fam)
            if (sp.color != ink) { sp.color = ink; dirty = true }
        }
        when (v) {
            is TextView -> { v.setTextColor(r.ink); if (m.compound) v.compoundDrawablesRelative.forEach { it?.setTint(r.ink) } }
            is ImageView -> v.setColorFilter(r.ink)
        }
        if (dirty) v.invalidate()
        m.last = r
        m.ground?.let { g -> m.after?.invoke(v, r, g) }
        return r.ink
    }

    private fun logW(line: String, key: String = line) {
        throttle.suppressedBefore(key, SystemClock.elapsedRealtime())?.let { Log.w(TAG, line + LogLineThrottle.repeatSuffix(it)) }
    }
}
