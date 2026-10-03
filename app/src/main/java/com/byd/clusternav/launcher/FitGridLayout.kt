package com.byd.clusternav.launcher

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.byd.clusternav.launcher.KachiTheme.dpi
import java.util.IdentityHashMap
import java.util.Locale
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ L5 WIDGET-FIT-ALL — khung đặt nội dung widget theo phép khớp chung [GridFit] ═══════════════════════════════════
 *
 * Thay các `LinearLayout` lồng của `WidgetViews.buildGrid` (hàng chia theo SỐ MỤC, ô cỡ cố định — nguyên nhân ảnh
 * 03/10: 6 nút trong khung dẹt ra 2×3, nhãn mất nửa dưới) và bọc cả ô ĐƠN (một nút to/đồng hồ/tốc độ/bảng/nhạc/datum)
 * để nó CO trong khung nhỏ và GIÃN trong khung to. Owner: *"nhiều thì bé lại, to thì giãn ra cho cân đối trong widget
 * là đẹp, đồng size, khoảng cách đều nhau"*.
 *
 * Mỗi lượt khớp (chỉ khi khung, số ô hoặc nội dung ô đổi — xem dưới):
 *  1. ô TỰ VẼ theo khung ([selfFitting]: vòng đo, bảng lốp, ảnh, hình xe, lưới lối tắt, ô nhóm) chỉ nhận một ô, không
 *     co — chúng đã tự lấp khung từ trước;
 *  2. ô còn lại: [FitProbe] đo hộp tự nhiên ở thang 1 cho mỗi dạng (dọc-2 dòng · dọc-1 dòng · ngang · chỉ-icon), gộp
 *     theo ô CẦN NHIỀU NHẤT (mọi ô cùng cỡ, cùng `k` ⇒ "đồng size");
 *  3. [GridFit.fit] chọn cột × hàng × dạng × `k` (`:core`, test thuần); [FitScale] áp `k` + dạng lên cây view có sẵn
 *     (không dựng lại view — không mất cú bấm);
 *  4. đo mọi ô EXACTLY đúng cỡ ô; KIỂM LẠI bằng bố cục thật — còn chữ bị cắt (làm tròn px/hinting) thì hạ `k` một bậc,
 *     tối đa [VERIFY_STEPS] lần.
 *
 * ## Không giật bố cục ([ĐO AOSP r47] `View.java:24450-24474`, `:21952`)
 * Khớp trong `onMeasure` (không `onSizeChanged` — chạy trong lượt LAYOUT, đổi view ở đó là "requestLayout during
 * layout"), giống `ShortcutGridLayout`. Setter của [FitScale] gọi `requestLayout` trên ô con; nó chỉ lan lên khi cha
 * CHƯA cờ `FORCE_LAYOUT`, và cờ đó xoá ở `layout()` cùng lượt ⇒ nhiều nhất MỘT lượt duyệt thừa cho mỗi lần đổi khung;
 * lượt thừa đó gặp khoá (rộng, cao, số ô) trùng ⇒ không đo dò, không áp ⇒ hội tụ.
 *
 * ## Chữ đổi giữa chuyến (nhịp 1 Hz) — soát vòng 1 + 2 (2.87)
 * Đường đổ số tại chỗ KHÔNG đo lại ô (`TextView` bề rộng tĩnh đổi chữ không `requestLayout`, `TextView.java:9641-9692`
 * r47), nên chỗ đổ gọi [contentChanged]. Ô có dấu chữ ([FitProbe.signature]) khác lúc đo ⇒ [FitRules.Cell] quyết:
 * chữ MỚI bị cắt mà lưới đọc được ⇒ đo dò lại ngay nhịp kế; còn lại (chữ vừa — có thể ngắn đi; lưới không đọc được;
 * lượt trước không chữa được) ⇒ thưa, [FitRules.RECHECK_MS]. Số đo mới nhận theo [FitRules.settle] (cắt ⇒ nhận; không
 * cắt ⇒ chỉ khi nhỏ đi rõ) ⇒ cỡ cả lưới không nhảy theo từng con số. Chỉ khi đến lượt mới xin MỘT lượt đo.
 *
 * Chữ bề rộng `WRAP` (số trong `AxisRow`, nhãn viên thuốc) thì khác: đổi chữ là BỎ bố cục + tự xin lượt đo
 * (`TextView.java:9686-9691` r47) ⇒ lúc [contentChanged] chạy trạng thái cắt CHƯA BIẾT ⇒ không quyết gì; lượt đo kế
 * ([grew], sau `measureAll`) quyết trên bố cục thật (soát vòng 2, P1). Lượt thưa mà để ô còn cắt ⇒ nhận ngay số đo
 * thật ([FitRules.Cell.fitted]) thay vì ghi dấu của chữ đang cắt rồi không bao giờ xét lại.
 *
 * Đường `refreshRead` dựng-lại (lùi) thay một ô con ⇒ [onViewAdded] xoá kết quả cũ ⇒ ô mới được đo + áp ngay lượt
 * sau, nên ô dựng lại vẫn đúng cỡ. `WorkspaceView.setCustomLayout` đổi hình khung không dựng lại ⇒ chỉ khoá đổi ⇒
 * chỉ chạy lại [GridFit] (rẻ) + áp.
 *
 * Kiểm được không cần đoán (QA, CLAUDE.md §15): mỗi lượt khớp ghi một dòng `adb logcat -s WidgetFit` (khung, bố cục,
 * dạng, `k`, đọc được?, đích chạm?, sức chứa); ô chỉ-icon mang nhãn trong `content-desc` (thấy trong `uiautomator dump`).
 */
internal class FitGridLayout private constructor(
    context: Context,
    private val single: Boolean,
    private val iconOnlyAllowed: Boolean,
) : ViewGroup(context) {

    private class Item(val fs: FitScale?) {
        var need: FitProbe.Need? = null

        /** Số đo THẬT của lượt đo dò gần nhất khi [settled] giữ số cũ ([FitRules.Cell.kept]) — nhận khi ô còn cắt. */
        var fresh: FitProbe.Need? = null

        /** Nhịp đo dò lại của ô (`:core`, test thuần cả trình tự): đến lượt? lượt nhận số mới? kẹt? lúc nào đo? */
        val cell = FitRules.Cell()
    }

    private val items = IdentityHashMap<View, Item>()
    private var fit: GridFit.Fit? = null

    /**
     * Lưới ở tầng đọc được ở lượt khớp GẦN NHẤT — KHÔNG xoá khi một ô con bị thay ([onViewAdded] xoá [fit]): soát vòng
     * 3 (P2) — ô con thay view làm [fit] `null` ⇒ ô số đứng TRƯỚC nó trong lượt đổ đọc "không đọc được" ⇒ nhịp nở 1 s
     * thành chờ 30 s ([FitRules.reprobe]).
     */
    private var legible = false

    /** Bố cục của dòng nhật ký gần nhất — không xoá theo ô con, để dòng `WidgetFit` chỉ ghi khi bố cục ĐỔI thật. */
    private var shown: GridFit.Fit? = null
    private var shapes: List<GridFit.Shape> = emptyList()
    private var options: List<FitProbe.Option> = emptyList()
    private var keyW = -1
    private var keyH = -1
    private var keyN = -1
    private var refits = 0

    private fun item(v: View): Item = items.getOrPut(v) { Item(if (selfFitting(v)) null else FitScale(v)) }

    private fun kids(): List<View> = (0 until childCount).map { getChildAt(it) }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        fit = null
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        items.remove(child)
        fit = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) 0 else MeasureSpec.getSize(widthMeasureSpec)
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) 0 else MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val iw = w - paddingLeft - paddingRight
        val ih = h - paddingTop - paddingBottom
        if (childCount == 0) return
        if (iw <= 0 || ih <= 0) { measureAll(0, 0, force = false); return }
        val f = fit
        if (f == null || keyW != iw || keyH != ih || keyN != childCount) { refit(iw, ih); return }
        measureAll(f.cellW, f.cellH, force = false)
        if (grew()) refit(iw, ih)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val f = fit ?: return
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val x = paddingLeft + f.left(i)
            val y = paddingTop + f.top(i)
            c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
        }
    }

    /** Lượt khớp đầy đủ cho khung trong [w]×[h] (KDoc lớp, bước 1–4). */
    private fun refit(w: Int, h: Int) {
        val t0 = SystemClock.elapsedRealtimeNanos()
        val kids = kids()
        val floors = FitProbe.Floors.of(context)
        var probed = 0
        kids.forEach { v ->
            val it = item(v); val fs = it.fs
            if (fs != null && (it.need == null || it.cell.stale)) {
                val raw = FitProbe.need(v, fs, floors)
                val got = settled(it.need, raw, it.cell.grow)
                it.need = got; it.fresh = raw.takeIf { got !== raw }
                it.cell.probed(SystemClock.elapsedRealtime(), kept = got !== raw)
                probed++
            }
        }
        var f: GridFit.Fit
        var spec: GridFit.Spec
        var steps: Int
        while (true) {
            combine(kids)
            spec = spec(kids)
            f = GridFit.fit(kids.size, w, h, shapes, spec)
            applyAll(kids, f)
            measureAll(f.cellW, f.cellH, force = true)
            steps = 0
            while (f.legible && steps < VERIFY_STEPS && kids.any { v -> item(v).fs?.let { FitProbe.clipped(it) } == true }) {
                val k = f.scale - QUANTUM
                if (k + 1e-9 < (f.shape?.minScale ?: 0.0)) break
                f = f.copy(scale = k)
                applyAll(kids, f)
                measureAll(f.cellW, f.cellH, force = true)
                steps++
            }
            // Soát vòng 2 (P1): ô còn cắt mà số đang dùng là số CŨ do settle giữ (lượt thưa) ⇒ nhận số đo thật đã có
            // (không đo dò thêm) rồi khớp lại. Mỗi vòng xoá cờ `kept` của ít nhất một ô ⇒ dừng sau tối đa số ô vòng.
            // Soát vòng 3 (P3): "kẹt" chỉ chốt khi chữ đang hiện là chữ đã đo dò ([probed]) — lượt khớp do ô khác chạy.
            val adopt = kids.filter { v ->
                val it = item(v)
                it.cell.fitted(it.fs?.let { fs -> FitProbe.clipped(fs) } == true, probedContent = probed(it))
            }
            if (adopt.isEmpty()) break
            adopt.forEach { v -> val it = item(v); it.fresh?.let { n -> it.need = n }; it.fresh = null }
        }
        val same = f == shown && keyW == w && keyH == h && keyN == kids.size
        fit = f; shown = f; legible = f.legible; keyW = w; keyH = h; keyN = kids.size; refits++
        val ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
        // Lượt khớp vì chữ đổi mà ra đúng bố cục cũ ⇒ không ghi dòng bố cục, nhưng vẫn ghi CHI PHÍ đo dò lại (soát vòng 2,
        // P2: lượt đo dò theo nội dung trước đây vô hình với QA) — tối đa một dòng mỗi lượt đo dò thật, không theo nhịp.
        if (!same) report(f, spec, w, h, steps, ms)
        else if (probed > 0) Log.i(TAG, String.format(Locale.US, "reprobe cells=%d same-layout refit#%d %.1fms", probed, refits, ms))
    }

    /** Chữ đang hiện của ô là chữ lúc đo dò (dấu trùng) — ô không co giãn/chưa đo ⇒ `false`. */
    private fun probed(it: Item): Boolean {
        val fs = it.fs ?: return false
        return FitProbe.signature(fs) == it.need?.sig
    }

    /**
     * Gộp số đo mới [fresh] với số cũ [old] theo từng dạng ([FitRules.settle]); dạng giữ số cũ thì giữ cả cờ dùng được.
     * Trả CHÍNH [fresh] khi mọi dạng nhận số mới — chỗ gọi so `!==` để biết có dạng nào giữ số cũ ([FitRules.Cell.kept]).
     */
    private fun settled(old: FitProbe.Need?, fresh: FitProbe.Need, grow: Boolean): FitProbe.Need {
        if (old == null) return fresh
        val shapes = fresh.shapes.indices.map { i -> FitRules.settle(old.shapes[i], fresh.shapes[i], grow) }
        if (shapes.indices.all { shapes[it] === fresh.shapes[it] }) return fresh
        val usable = shapes.indices.map { i -> if (shapes[i] === fresh.shapes[i]) fresh.usable[i] else old.usable[i] }
        return FitProbe.Need(shapes, usable, fresh.sig)
    }

    /**
     * Gộp nhu cầu của mọi ô co giãn theo từng dạng: hộp = MAX (ô cần nhiều nhất quyết cỡ chung), sàn = MAX. Chỉ-icon chỉ
     * khi được phép ([IconRepeat.ofIds] ở chỗ dựng) VÀ có ít nhất một ô có cả nhãn lẫn icon (không thì nó ≡ dọc).
     */
    private fun combine(kids: List<View>) {
        val needs = kids.mapNotNull { item(it).need }
        if (needs.isEmpty()) { shapes = emptyList(); options = emptyList(); return }
        val iconOk = iconOnlyAllowed && kids.any { v -> item(v).fs?.let { it.labels.isNotEmpty() && it.hasIcon } == true }
        val cand = FitProbe.OPTIONS.indices.filter { FitProbe.OPTIONS[it].form != GridFit.Form.ICON_ONLY || iconOk }
        // Dạng mà một ô không bao giờ vẽ trọn (soát vòng 1 P1: hàng ngang có con rộng 0) không làm ứng viên.
        val idx = FitRules.usable(cand) { i -> needs.all { it.usable[i] } }
        options = idx.map { FitProbe.OPTIONS[it] }
        shapes = idx.map { i ->
            val s = needs.map { it.shapes[i] }
            GridFit.Shape(
                FitProbe.OPTIONS[i].form, s.maxOf { it.widthPx }, s.maxOf { it.heightPx }, s.maxOf { it.minScale },
                FitProbe.OPTIONS[i].lines, fallback = FitProbe.OPTIONS[i].form == GridFit.Form.ICON_ONLY,
                reserve = FitProbe.OPTIONS[i].reserve,
            )
        }
    }

    private fun spec(kids: List<View>): GridFit.Spec {
        val touch = !single && kids.any { item(it).fs?.clickable == true }
        return GridFit.Spec(
            gapPx = if (single) 0 else dpi(context, Sp.S),
            slackPx = SLACK_PX,
            maxScale = if (single) MAX_SCALE_SINGLE else MAX_SCALE_GRID,
            minCellPx = if (touch) dpi(context, Sp.TOUCH) else 0,
            quantum = QUANTUM,
        )
    }

    /**
     * Áp dạng + `k` đã chọn lên mọi ô co giãn (ô tự vẽ không đụng). Kèm cỡ ô: icon cỡ cố định không bao giờ to hơn ô
     * ([FitRules.iconScale] — QA 04/10, icon bị khung lề cắt thành dải hẹp khi lưới giữ sàn).
     */
    private fun applyAll(kids: List<View>, f: GridFit.Fit) {
        val at = shapes.indexOfFirst { it === f.shape }
        val opt = options.getOrNull(at) ?: return
        kids.forEach { v -> item(v).fs?.apply(f.scale, opt.form, opt.lines, f.cellW, f.cellH) }
    }

    private fun measureAll(cw: Int, ch: Int, force: Boolean) {
        val ws = MeasureSpec.makeMeasureSpec(cw.coerceAtLeast(0), MeasureSpec.EXACTLY)
        val hs = MeasureSpec.makeMeasureSpec(ch.coerceAtLeast(0), MeasureSpec.EXACTLY)
        kids().forEach { v -> if (force) item(v).fs?.forceAll() ?: v.forceLayout(); v.measure(ws, hs) }
    }

    /** Đường cache (lượt đo không đổi khung): có ô nào đến lượt đo dò lại ([due]) ⇒ khớp lại. Xét MỌI ô (không dừng sớm). */
    private fun grew(): Boolean = kids().fold(false) { any, v -> due(v, measured = true) == FitRules.Verdict.DUE || any }

    /**
     * Ô [v] có phải đo dò lại không — quyết ở [FitRules.Cell.check] (`:core`): chữ đã khác lúc đo ([FitProbe.signature])
     * VÀ đến lượt theo [FitRules.reprobe] (cắt + đọc được + chưa kẹt ⇒ sau [FitRules.GROW_GAP_MS]; còn lại ⇒ sau
     * [FitRules.RECHECK_MS]); trạng thái cắt CHƯA BIẾT ([FitProbe.clip] — chữ `WRAP` vừa đổi, chưa có bố cục) ⇒ không
     * quyết ([FitRules.Verdict.WAIT]); ô đã đến lượt mà lần xét sau thấy cắt ⇒ thành lượt nhận số mới. Chỉ đọc bố cục
     * chữ + số đo đã có, không đo. Chữ không đổi mà vẫn cắt (khung vốn quá nhỏ) ⇒ không làm gì (không vòng lặp).
     * [measured] = xét ngay sau `measureAll` ([grew]): khi đó chữ không bố cục là chữ không được vẽ ([FitRules.known]).
     */
    private fun due(v: View, measured: Boolean): FitRules.Verdict {
        val it = items[v] ?: return FitRules.Verdict.NONE
        val fs = it.fs ?: return FitRules.Verdict.NONE
        val need = it.need ?: return FitRules.Verdict.DUE
        val same = FitProbe.signature(fs) == need.sig
        val now = SystemClock.elapsedRealtime()
        return it.cell.check(same, legible, now) { FitRules.known(FitProbe.clip(fs), measured) }
    }

    /**
     * Đường đổ số TẠI CHỖ vừa đổi chữ của ô [child] mà không qua lượt đo (KDoc lớp, "Chữ đổi giữa chuyến") ⇒ đến lượt
     * đo dò lại thì xin MỘT lượt đo; chưa đến lượt ⇒ không làm gì (không đo lại cả màn theo nhịp 1 Hz — 09-25). Chưa
     * biết (chữ `WRAP` đã bỏ bố cục) ⇒ chính `TextView` đã xin lượt đo, và [grew] xét lại ở đó trên bố cục thật; chỉ
     * xin thêm khi chưa có lượt đo nào chờ (không thì ô không bao giờ được xét).
     */
    private fun onContentChanged(child: View) {
        when (due(child, measured = false)) {
            FitRules.Verdict.DUE -> requestLayout()
            FitRules.Verdict.WAIT -> if (!isLayoutRequested) requestLayout()
            FitRules.Verdict.NONE -> Unit
        }
    }

    /**
     * Một dòng nhật ký cho QA — chỉ ở lượt khớp (đổi khung/ô), không theo nhịp vẽ. Kèm thời gian lượt khớp ([ms]): chi
     * phí đo dò ở thiết kế là [ĐOÁN] 2–5ms, con số thật phải đọc từ dòng này trên máy ảo/xe.
     */
    private fun report(f: GridFit.Fit, spec: GridFit.Spec, w: Int, h: Int, steps: Int, ms: Double) {
        val cap = if (single || shapes.isEmpty()) -1 else GridFit.capacity(w, h, shapes, spec, MAX_ITEMS)
        val form = f.shape?.let { "${it.form}/${it.lines}" } ?: "FILL"
        Log.i(
            TAG,
            String.format(
                Locale.US, "n=%d frame=%dx%d -> %dx%d cell=%dx%d %s k=%.3f raw=%.3f legible=%b touch=%b cap=%d verify=%d refit#%d %.1fms",
                f.count, w, h, f.cols, f.rows, f.cellW, f.cellH, form, f.scale, f.rawScale, f.legible, f.touchOk, cap,
                steps, refits, ms,
            ),
        )
    }

    companion object {
        private const val TAG = "WidgetFit"

        /** Số mục tối đa của một ô widget (`AppDrawer.MAX`, `WidgetViews.buildGrid` `take(8)`) — trần của [GridFit.capacity]. */
        private const val MAX_ITEMS = 8

        /**
         * Trần `k` của LƯỚI (≥ 2 mục, ô nén cỡ `TileSize.DOCK`): ×2 ⇒ icon 40dp, nhãn 23sp ≈ ô to `BIG` × 1,5 — khung to
         * với ít mục thì ô giãn tới cỡ ô đơn lớn nhất, không thành chữ khổng lồ. [ĐỀ XUẤT, owner chốt].
         */
        const val MAX_SCALE_GRID = 2.0

        /** Trần `k` của Ô ĐƠN (cỡ gốc `BIG`/widget dựng tay): ×1,5 ⇒ nhãn 22,5sp — cùng trần chữ với [MAX_SCALE_GRID]. */
        const val MAX_SCALE_SINGLE = 1.5

        /** Bước của `k` (1/32 ≈ 3 %): đổi khung vài px không áp lại cỡ chữ ⇒ không lượt đo thừa. Nhị phân đúng. */
        const val QUANTUM = 1.0 / 32

        /** Chừa (px) trong mỗi ô cho sai số làm tròn `dp→px`/hinting chữ khi nhân `k` (đo ở thang 1, nhân tuyến tính). */
        const val SLACK_PX = 2

        /** Số bậc `k` được hạ khi bố cục thật vẫn cắt chữ sau khi áp (lưới an toàn cho phép nhân tuyến tính). */
        const val VERIFY_STEPS = 2

        /**
         * Ô con [child] vừa được đổ chữ TẠI CHỖ (`WidgetViews.refreshRead`) — xem [onContentChanged]. Ô không nằm trong
         * một lưới khớp (ô đơn tự vẽ trả nguyên view) ⇒ bỏ qua.
         */
        fun contentChanged(child: View) {
            (child.parent as? FitGridLayout)?.onContentChanged(child)
        }

        /** Lưới 2..8 mục. [iconOnly] = icon các mục phân biệt được ([IconRepeat.ofIds]) ⇒ được phép rơi về chỉ-icon. */
        fun grid(ctx: Context, iconOnly: Boolean): FitGridLayout = FitGridLayout(ctx, single = false, iconOnlyAllowed = iconOnly)

        /**
         * Ô ĐƠN: nội dung tự vẽ theo khung ⇒ trả NGUYÊN view (y như 2.86); còn lại ⇒ bọc một [FitGridLayout] một ô để
         * nội dung co/giãn theo khung.
         */
        fun single(ctx: Context, child: View): View =
            if (selfFitting(child)) child else FitGridLayout(ctx, single = true, iconOnlyAllowed = false).apply { addView(child) }

        /**
         * Ô TỰ lấp khung (vẽ Canvas theo cỡ được cho, hoặc tự khớp bên trong) — KHÔNG co bằng [FitScale]: co nó là co
         * hai lần. Xét theo LOẠI view trong cây ô, không theo mã widget (CLAUDE.md §7).
         */
        fun selfFitting(v: View): Boolean = when (v) {
            is RingView, is TyreBoardView, is PhotoWidgetView, is CarMiniView, is DoorBoardView,
            is ShortcutIconsView, is ShortcutGridLayout, is GroupTileView, is FitGridLayout -> true
            is ViewGroup -> (0 until v.childCount).any { selfFitting(v.getChildAt(it)) }
            else -> false
        }
    }
}
