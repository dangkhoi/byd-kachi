package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.system.inputd.InputDaemonClient
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Sân khấu workspace: đặt tối đa 4 "ô" theo [WorkspaceLayout] cho [WorkspaceState.preset].
 * P1: mỗi ô là placeholder (chạm → callback [onSlotTap], nối drawer ở P2). Thuần code → gọn, không đụng layout seal.
 */
class WorkspaceView(context: Context) : ViewGroup(context) {

    var onSlotTap: ((Int) -> Unit)? = null
    var onSlotClear: ((Int) -> Unit)? = null
    var onSlotSwap: ((Int, Int) -> Unit)? = null
    var onAppOpen: ((Int) -> Unit)? = null

    /**
     * T4 — dựng view cho ô widget bên thứ ba. `null` (chưa gắn, hoặc trả `null`) ⇒ ô hiện thẻ *"widget không còn"*.
     *
     * Là hàm tiêm vào chứ không phải `AppWidgetHost` nằm ngay đây: view này chỉ **là bề mặt vẽ**, còn vòng đời
     * nghe-cập-nhật + thu hồi id phải do một chủ duy nhất giữ ([AppWidgetSlotHost]). Đặt host vào view thì mỗi lần
     * view bị dựng lại là một host mới ⇒ id rò và nhịp nghe nhân đôi.
     */
    var appWidgetView: ((SlotContent.AppWidget) -> View?)? = null

    /**
     * T4 — tên nhà cung cấp của một ô widget bên thứ ba, dùng cho thẻ *"widget không còn"* ([deadWidgetCard]).
     *
     * ⚠ **KHÔNG** phải nhãn hiện ở dải đầu ô: [slotHead] chỉ vẽ nút ⇄ ([SlotSwapButton]) và **không nhận** tên
     * nữa; [OverlayHeads] (đường freeform) cũng chỉ vẽ đúng nút đó. Không còn nhãn/✕ ở dải đầu ô trên bất kỳ
     * đường nào (owner 2026-09-12/13).
     */
    var appWidgetName: ((SlotContent.AppWidget) -> String)? = null
    // UDF: trạng thái xe LIVE đến từ HomeUiState.carStatus (KHÔNG đọc port trong view). Off-car mọi field null ⇒ "—".
    var carStatus: CarStatus = CarStatus()
    var mediaProvider: () -> MediaSnapshot? = { null }   // đọc nhạc live (Bitmap ở :app → ngoài state :core)
    var onMedia: (String) -> Unit = {}                    // transport: play/pause/next/prev
    // RW0: ô giữa màn nay đặt được cả HÀNH ĐỘNG (R2) ⇒ cần đường ra xe. Port (không phải state) nên nằm ở view như
    // mediaProvider; off-car [NoCar] ⇒ bấm no-op.
    var control: CarControlPort = NoCar
    /**
     * RW0/R11: lựa chọn ĐƠN VỊ của người dùng, dùng khi dựng ô ĐỌC. Đặt qua [setUnitPrefs] (không phải gán trực
     * tiếp) vì đổi đơn vị BẮT BUỘC phải dựng lại ô — chuỗi số nằm trong View đã dựng, không tự đổi theo.
     */
    private var unitPrefs: UnitPrefs = UnitPrefs.DEFAULT

    /**
     * Đổi lựa chọn đơn vị. Dựng lại CHỈ KHI lựa chọn thật sự khác, và khi đó **chỉ dựng lại ô WIDGET**.
     *
     * ⚠ Bản đầu gọi `rebuild()` (dựng lại TẤT CẢ) — nghĩa là đổi chữ "bar"→"psi" sẽ tháo cả ô App: `VdAppHost` bị
     * nhả, màn ảo mới được tạo, app trong ô phải mở lại. Đơn vị chỉ ảnh hưởng ô widget, nên ràng buộc C5 áp ở đây
     * đúng như [WorkspaceRenderPlanner] đã áp cho nhịp trạng thái xe: ô App KHÔNG bị chạm tới.
     */
    fun setUnitPrefs(prefs: UnitPrefs) {
        if (prefs == unitPrefs) return
        unitPrefs = prefs
        rebuildWidgetSlots()
    }
    // ── KÊNH NHÚNG (gói 1, P-bug2): 4 thứ dưới đây PHẢI được gắn CÙNG LÚC qua [applyEmbedSeam] ─────────────────
    // Vì sao private: `makeSlot` đọc chúng LÚC DỰNG VIEW. Nếu để công khai cho bên ngoài gán rời từng cái thì ai
    // gán sai THỨ TỰ (vd dựng lại ô trước khi gắn kênh chạm) sẽ ra bộ chiếu thiếu kênh chạm — chạy đường bơm chạm
    // chậm hơn mà KHÔNG có gì báo lỗi. Đóng private ⇒ sai thứ tự trở thành KHÔNG THỂ.
    private var shell: ((String) -> String)? = null   // dadb uid-shell → nhúng app lên VirtualDisplay (display phụ, không caption) + bơm chạm
    // B2b: đăng ký/gỡ display của VD ô với DisplayOwnershipRegistry (qua WindowCommandDispatcher). Mặc định no-op.
    private var registerVd: (Int) -> Unit = {}
    private var unregisterVd: (Int) -> Unit = {}
    // B4: MỘT input-daemon THƯỜNG TRÚ dùng chung cho MỌI ô (mỗi khung tự mang displayId). B5b: KHÔNG tự dựng nữa —
    // do AppContainer sở hữu và TIÊM vào (activity gắn khi dadb nối). null → VdAppHost fallback `input -d` (cũ).
    private var inputClient: InputDaemonClient? = null
    var slotDensityDpi = 200                   // mật độ cho VirtualDisplay của ô (Dudu ~200; chỉnh để app hiện vừa mắt)

    private val gapPx = dp(Sp.SLOT_GAP)
    // View-transient ONLY: bản sao khung hình ĐANG hiển thị, dùng để DIFF khi [render] để không dựng lại ô không đổi.
    // KHÔNG phải nguồn sự thật — nguồn sự thật là HomeViewModel.uiState; không code ngoài nào đọc field này.
    private var displayed = WorkspaceState()
    private var displayedStatus = CarStatus()
    private val slotViews = ArrayList<View>()

    init { rebuild() }

    /**
     * Áp trạng thái [s] lên view (was `setState`). PURE VIEW: chỉ RENDER — KHÔNG giữ nguồn sự thật.
     * Cập nhật TĂNG DẦN: cùng preset → chỉ dựng lại ô có nội dung ĐỔI (so với khung đang hiển thị [displayed]);
     * giữ nguyên View (và VdAppHost) của các ô khác → thêm app vào ô mới KHÔNG relaunch/nháy app đang chạy ở ô khác,
     * launcher đứng yên. Đổi preset/số ô → dựng lại cả. Nguồn sự thật do HomeViewModel giữ; đây chỉ phản chiếu.
     */
    fun render(s: WorkspaceState, status: CarStatus = carStatus) = renderInternal(s, status, embedChanged = false)

    /**
     * Gắn NGUYÊN KHỐI kênh nhúng (dadb shell + kênh chạm + đăng ký/gỡ màn ảo) rồi tự áp [state] lại MỘT LẦN.
     *
     * **Đây là bản vá P-bug2.** Trước đây activity gán rời 4 field rồi gọi [render]; nhưng [render] so sánh theo
     * NỘI DUNG ô nên ô App "không đổi" ⇒ không dựng lại ⇒ `VdAppHost` không bao giờ được gắn ⇒ app trong ô chỉ
     * hiện sau khi người dùng đổi bố cục (khi đó mới đi nhánh dựng-lại-tất-cả). Nay việc kênh-vừa-có được khai
     * báo tường minh cho bộ quyết định, và vì hàm này gắn đủ 4 thứ TRƯỚC khi dựng lại nên không thể sai thứ tự.
     */
    fun applyEmbedSeam(
        shell: (String) -> String,
        inputClient: InputDaemonClient?,
        registerVd: (Int) -> Unit,
        unregisterVd: (Int) -> Unit,
        state: WorkspaceState,
        status: CarStatus,
    ) {
        val had = this.shell != null
        this.registerVd = registerVd
        this.unregisterVd = unregisterVd
        this.inputClient = inputClient
        this.shell = shell
        renderInternal(state, status, embedChanged = !had)
    }

    private fun renderInternal(s: WorkspaceState, status: CarStatus, embedChanged: Boolean) {
        mediaCache = null      // lượt mới ⇒ đọc lại nhạc đúng MỘT lần cho cả lượt
        val old = displayed
        val oldStatus = displayedStatus
        displayed = s; displayedStatus = status; carStatus = status
        // Luật "ô nào cần dựng lại" nằm ở :core (WorkspaceRenderPlanner) → test được off-car, kể cả ca P-bug2.
        when (val plan = WorkspaceRenderPlanner.decide(old, s, slotViews.size, status != oldStatus, embedChanged,
            // P9: số ô THỰC TẾ (bố cục tự vẽ có thể khác bố cục sẵn). Đọc từ bố cục sẵn ở đây sẽ
            // làm bộ quyết định thấy 'số view lệch số ô' mọi lần render ⇒ dựng lại TẤT CẢ liên tục.
            slotCount = EffectiveLayout.slotCount(displayed.preset, customLayout),
        )) {
            WorkspaceRenderPlan.RebuildAll -> { rebuild(); return }
            is WorkspaceRenderPlan.PerSlot -> plan.rebuild.forEach { i ->
                val nc = s.slots.getOrElse(i) { SlotContent.Empty }
                val oc = old.slots.getOrElse(i) { SlotContent.Empty }
                // [SOÁT P1-1] Ô chỉ cần LÀM MỚI SỐ (nội dung không đổi, năng lực nhúng không đổi) ⇒ đổi tại chỗ
                // đúng những ô con là mục ĐỌC. Giữ nguyên view của nút và của widget trình chiếu ⇒ không mất cú bấm,
                // không đặt lại vòng quay ảnh. Không thay được con nào ⇒ lùi về dựng lại cả ô (an toàn).
                val onlyValues = !embedChanged && WorkspaceRenderPlanner.sameContent(oc, nc)
                if (onlyValues && nc is SlotContent.Widget &&
                    WidgetViews.refreshRead(slotViews[i], widgetData()) > 0
                ) return@forEach
                removeView(slotViews[i])
                val v = makeSlot(i, nc)
                addView(v); slotViews[i] = v
            }
        }
        requestLayout(); invalidate()
    }

    /** Gói dữ liệu render widget hiện tại (trạng thái xe + nhạc live + cổng ra lệnh cho ô hành động + đơn vị). */
    private fun widgetData(): WidgetData {
        // [SOÁT P2-8] Đọc nhạc là một lời gọi LIÊN TIẾN TRÌNH (`getActiveSessions`). Bản trước gọi nó trong hàm này,
        // mà hàm này được gọi **mỗi Ô** (tới 6 ô) và mỗi nhịp trạng thái xe (1 giây) ⇒ tới 6 lời gọi/giây trên thread
        // chính cho một dữ liệu y hệt nhau. Nay đọc MỘT LẦN cho mỗi lượt render và dùng lại trong lượt đó.
        val media = mediaCache ?: mediaProvider().also { mediaCache = it }
        return WidgetData(carStatus, media, onMedia, control, unitPrefs, photoProvider(), photoIntervalSec)
    }

    /** Ảnh chụp nhạc dùng cho LƯỢT render hiện tại (xoá ở đầu mỗi lượt) — xem KDoc widgetData. */
    private var mediaCache: MediaSnapshot? = null

    /**
     * U4(b) — nguồn ảnh cho widget trình chiếu. Là HÀM (không phải danh sách) để chỗ gọi quyết định khi nào đọc thư
     * mục: đọc thư mục là I/O, không nên chạy mỗi lần dựng ô.
     */
    private var photoProvider: () -> List<String> = { emptyList() }
    private var photoIntervalSec: Int = Slideshow.DEFAULT_INTERVAL_SEC
    private var photoPathsShown: List<String>? = null

    /**
     * Bố cục TỰ VẼ (P9 bước 2). Để ở kênh riêng, KHÔNG nhét vào `WorkspaceState`: bộ quyết-định-dựng-lại đang bị
     * test tương-đương-hành-vi hơn 1000 tổ hợp khoá, thêm field vào state là xáo trộn đúng chỗ đó.
     */
    private var customLayout: GridLayout? = null

    /**
     * Đặt bố cục tự vẽ. Số ô đổi ⇒ phải dựng lại; **số ô giữ nguyên mà chỉ đổi hình dạng ⇒ CHỈ đặt lại chỗ**, không
     * dựng lại — nhờ vậy app đang chiếu trong ô **không bị nhả/gắn lại** (C5), chỉ đổi cỡ khung.
     */
    fun setCustomLayout(layout: GridLayout?) {
        val before = EffectiveLayout.slotCount(displayed.preset, customLayout)
        customLayout = layout
        val after = EffectiveLayout.slotCount(displayed.preset, customLayout)
        if (before != after) rebuild() else { requestLayout(); invalidate() }
    }

    /** Khung pixel đang hiệu lực — mọi chỗ trong view PHẢI đi qua đây (xem `EffectiveLayout`). */
    private fun effectiveRects(w: Int, h: Int) =
        EffectiveLayout.rects(displayed.preset, customLayout, w, h, gapPx)

    /**
     * U4(b) — đặt nguồn ảnh cho widget trình chiếu. Dựng lại **chỉ ô widget** khi nguồn thật sự đổi.
     *
     * Cùng lối với [setUnitPrefs]: phải dựng lại vì ảnh nằm trong View đã dựng, nhưng **KHÔNG** dựng lại ô đang
     * chiếu app (làm thế là ngắt kênh chạm — ràng buộc C5 của gói 2). Gọi lại với cùng nguồn thì không làm gì.
     */
    fun setPhotoSource(paths: List<String>, intervalSec: Int) {
        // [SOÁT] So theo SỐ LƯỢNG là sai: xoá 1 ảnh rồi thêm 1 ảnh khác ⇒ số lượng y nguyên ⇒ coi như "không đổi"
        // ⇒ widget giữ danh sách CŨ, ảnh vừa xoá vẫn hiện và ảnh mới không bao giờ tới. So theo NỘI DUNG.
        val changed = paths != photoPathsShown || intervalSec != photoIntervalSec
        photoProvider = { paths }
        photoIntervalSec = intervalSec
        photoPathsShown = paths
        if (changed) rebuildWidgetSlots()
    }

    private fun rebuild() {
        mediaCache = null      // lượt dựng lại cũng là một lượt mới ⇒ đọc nhạc lại đúng một lần
        removeAllViews(); slotViews.clear()
        for (i in 0 until EffectiveLayout.slotCount(displayed.preset, customLayout)) {
            val content = displayed.slots.getOrElse(i) { SlotContent.Empty }
            val v = makeSlot(i, content)
            addView(v); slotViews.add(v)
        }
        requestLayout(); invalidate()
    }

    /**
     * Dựng lại CHỈ những ô đang là widget — dùng cho việc đổi thứ mà chỉ widget đọc (hiện tại: lựa chọn đơn vị).
     * Ô App và ô trống giữ nguyên view ⇒ bộ chiếu app trong ô không bị nhả/gắn lại (C5).
     */
    private fun rebuildWidgetSlots() {
        for (i in slotViews.indices) {
            val content = displayed.slots.getOrElse(i) { SlotContent.Empty }
            if (content !is SlotContent.Widget) continue
            removeView(slotViews[i])
            val v = makeSlot(i, content)
            addView(v); slotViews[i] = v
        }
        requestLayout(); invalidate()
    }

    private fun makeSlot(index: Int, content: SlotContent): View {
        val fl = FrameLayout(context)
        fl.background = GradientDrawable().apply {
            cornerRadius = dp(Sp.RADIUS_L).toFloat()
            setColor(Color.parseColor(KachiTheme.SLOT))                 // nền card nhấc nhẹ khỏi nền (prototype --k-card)
            setStroke(dp(Sp.HAIRLINE), Color.parseColor(KachiTheme.LINE_STRONG))       // viền HAIRLINE SÁNG mảnh (prototype, không phải viền tối)
        }
        fl.clipToOutline = true                                    // clip nội dung theo góc bo (như overflow:hidden của prototype)
        val mm = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        when (content) {
            is SlotContent.Widget -> {
                val body = WidgetViews.buildGrid(context, content.ids, widgetData())
                // ⚠ [SOÁT UI 2026-09-12] Widget FULL khung như ô App: nút ⇄ chỉ NỔI đè ở đầu ô (overlay), KHÔNG
                // đẩy nội dung. Trước đây `setPadding(top += SLOT_HEAD_CLEAR)` đẩy cả nội dung widget xuống ⇒ mất một
                // khúc TO ở đỉnh (owner báo: "widget bị che mất top 1 khúc lớn"), trong khi ô App không hề bị vì app
                // render MATCH_PARENT còn nút chỉ nổi trên. Nay hai loại ô đồng nhất: nút nổi, khung giữ nguyên cỡ.
                fl.addView(body, mm)
                val first = content.ids.firstOrNull() ?: ""
                fl.addView(slotHead(index), headLp())
                fl.setOnClickListener { onSlotTap?.invoke(index) }
                fl.setOnLongClickListener { startSlotDrag(index, fl); true }
            }
            // ── T4: widget Android của APP KHÁC ──
            //
            // Nhà cung cấp tự vẽ nội dung (đẩy RemoteViews sang), nên ở đây chỉ có hai việc: xin view, và **nói ra**
            // khi không xin được. `null` = id đã chết (app bị gỡ/vô hiệu) ⇒ hiện thẻ nói rõ app nào, chạm để chọn
            // lại. Cố ý KHÔNG để ô trống: một ô trống ở đây là "widget của tôi biến mất không lý do".
            is SlotContent.AppWidget -> {
                val host = appWidgetView?.invoke(content)
                if (host != null) {
                    // ⚠ [SOÁT UI 2026-09-12] FULL khung như ô App/Widget: nút ⇄ chỉ NỔI đè, KHÔNG đẩy host xuống
                    // (trước đây topMargin = SLOT_HEAD_CLEAR đẩy widget bên thứ ba xuống, mất khúc top).
                    val hostLp = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                    // ⚠⚠ Nền TỐI CỐ ĐỊNH phía sau widget — không theo chủ đề, cùng lý do nút ⇄ (`scrimBtn`).
                    //
                    // Nội dung ô này là RemoteViews do app KHÁC vẽ, và quy ước widget Android là "nền tối" nên phần
                    // lớn widget dùng chữ TRẮNG. [ĐO] bảng SÁNG + widget đồng hồ: ô chỉ còn **0.15%** điểm mực tối,
                    // chữ giờ gần như biến mất. Launcher không sửa được màu RemoteViews của app khác ⇒ chỗ duy nhất
                    // chữa được là nền. Widget nào tự vẽ nền đục thì lớp này bị che, nên nó không làm hại ca nào.
                    fl.addView(appWidgetBacking(), hostLp)
                    fl.addView(host, hostLp)
                    // Không đặt `setOnClickListener` cho CẢ ô: widget bên thứ ba có nút bấm riêng bên trong nó
                    // (next/prev của widget nhạc…). Bắt chạm ở ô cha sẽ ăn mất cú bấm của widget. Đổi/xoá widget đi
                    // qua nút ⇄/✕ ở dải đầu ô — đường mà mọi loại ô khác cũng dùng.
                } else {
                    fl.addView(deadWidgetCard(content), mm)
                    fl.setOnClickListener { onSlotTap?.invoke(index) }
                }
                fl.addView(slotHead(index), headLp())
                fl.setOnLongClickListener { startSlotDrag(index, fl); true }
            }
            is SlotContent.App -> {
                fl.addView(appCard(content.pkg), mm)                       // fallback phía sau (hiện nếu nhúng lỗi)
                val sh = shell
                if (sh != null) {
                    val host = VdAppHost(context, slotDensityDpi, registerVd, unregisterVd, inputClient)  // sideload: app render lên VirtualDisplay (display phụ → KHÔNG caption) qua dadb — kiểu Dudu, SurfaceView cho đỡ lag; chạm qua input-daemon (fallback `input -d`)
                    fl.addView(host, mm); host.bind(content.pkg, sh)
                } else if (SlotAppHost.embeddingUsable(context)) {
                    val host = SlotAppHost(context, dp(Sp.RADIUS_L).toFloat())
                    if (host.available()) { fl.addView(host, mm); host.embed(content.pkg) }   // ROM xe (platform-signed): ActivityView, không lag/caption
                }
                fl.addView(slotHead(index), headLp())                                             // ⇄ nổi đè lên trên cùng
                fl.setOnClickListener { onAppOpen?.invoke(index) }
                fl.setOnLongClickListener { startSlotDrag(index, fl); true }
            }
            SlotContent.Empty -> {
                fl.background = GradientDrawable().apply {
                    cornerRadius = dp(Sp.RADIUS_L).toFloat(); setColor(Color.parseColor(KachiTheme.EMPTY_FILL))
                    setStroke(dp(Sp.STROKE), Color.parseColor(KachiTheme.EMPTY_LINE), dp(Sp.DASH_ON).toFloat(), dp(Sp.DASH_OFF).toFloat())
                }
                fl.addView(emptyAdd(), mm)
                fl.setOnClickListener { onSlotTap?.invoke(index) }
            }
        }
        // Mọi ô là điểm THẢ: kéo 1 ô rồi thả lên ô khác → đổi chỗ nội dung.
        fl.setOnDragListener { _, e ->
            when (e.action) {
                DragEvent.ACTION_DROP -> {
                    (e.localState as? Int)?.let { from -> if (from != index) onSlotSwap?.invoke(from, index) }; true
                }
                else -> true
            }
        }
        return fl
    }

    private fun startSlotDrag(index: Int, v: View) {
        v.startDragAndDrop(null, View.DragShadowBuilder(v), index, 0)
    }

    /**
     * Khung trong suốt bọc nút ⇄ **nổi** ở đầu ô.
     *
     * Cao [Sp.SLOT_HEAD_CLEAR]: nó phải chứa nổi `XS + ICON_L + XS`. Trước T5 khung cao 34dp (hằng `HEAD_BAR`
     * của thanh đầu ô cũ, nay đã xoá cùng thanh đó — D2a) trong khi nút chiếm 34dp ⇒ **không còn chỗ thở**, và
     * độ hở của nội dung widget lại là 30dp ⇒ nội dung bị đè. Hai con số ở hai tệp khác nhau, nên không bài test
     * nào bắt được.
     *
     * ⚠ **Đích chạm 32dp — DƯỚI mức [Sp.TOUCH], có lý do**: nút này **nổi ĐÈ lên nội dung** ô (khác nút của
     * [OverlayHeads] nằm trong thanh riêng). Nới lên 48dp buộc độ hở lên 56dp, tức mọi ô widget mất 56dp chiều
     * cao (≈11% ô trong bố cục 2×2) cho một nút **hiếm dùng** và bấm nhầm thì chỉ mở bảng chọn (hoàn lại được).
     * Đổi lấy 24dp nội dung thật cho một đích chạm rộng hơn là đánh đổi sai ở màn hình xe.
     */
    private fun headLp() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, dp(Sp.SLOT_HEAD_CLEAR), Gravity.TOP,
    )

    /**
     * Header ô — owner: CHỈ 1 nút đổi app, canh GIỮA trên cùng, KHÔNG thanh nền, KHÔNG nút ✕.
     *
     * Hình + hình học ở [SlotSwapButton] — dùng CHUNG với [OverlayHeads] (đường freeform) để hai đường không lệch nhau
     * ("lúc 1 icon lúc 2 icon", 2026-09-13).
     *
     * ⚠ [SOÁT SENIOR 2026-09-13] Hai tham số `name`/`dotColor` đã **BỎ**, cùng ba hàm chỉ sống để nuôi chúng
     * (`appName` · `widgetName` · `widgetAccent`). Bản trước giữ chữ ký "cho chỗ gọi khỏi đổi" và đánh dấu
     * `@Suppress("UNUSED_PARAMETER")` — nhưng chỗ gọi vẫn phải TÍNH hai giá trị đó mỗi lượt `render()`, và
     * `appName` là một lượt `getApplicationInfo` + `getApplicationLabel` của `PackageManager` **cho mỗi ô App**
     * để rồi vứt đi. Đúng lối dọn mà chính tệp này vừa làm với `headBtnLp()`/`headBtn()` ở T5 (CLAUDE.md §8).
     */
    private fun slotHead(index: Int): View =
        SlotSwapButton.centered(context) { onSlotTap?.invoke(index) }

    // ⚠ T5 đã XOÁ `headBtnLp()` + `headBtn()` ở đây: [ĐO] chúng chỉ được KHAI, không chỗ nào gọi (thanh đầu ô
    // nay do [OverlayHeads] dựng, và ô widget chỉ có một nút ⇄ trong [slotHead]). Giữ lại thì T5 phải quyết cỡ
    // đích chạm cho hai hàm mà người dùng không bao giờ chạm tới được — cùng lối dọn với `cycleDockEdge` ở S1.

    private fun placeholder(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(Color.parseColor(KachiTheme.MUT))
        KachiType.apply(this, KachiType.BODY)
        gravity = Gravity.CENTER
    }

    /** Ô trống: viền đứt + dấu ＋ to + nhãn — rõ là "chỗ thêm app". */
    private fun emptyAdd(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        addView(TextView(context).apply {
            // [type scale] ngoại lệ: `＋` là KÝ HIỆU trang trí (dấu "thêm vào đây"), không phải chữ — cỡ của nó là
            // hình học của ô trống, không phải một bậc chữ. Trần 32f = DISPLAY(28) sẽ nhỏ đi thấy rõ.
            text = "＋"; setTextColor(Color.parseColor(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f); gravity = Gravity.CENTER   // [type scale] glyph trang trí, ngoài 5 bậc
        })
        addView(TextView(context).apply {
            text = context.getString(R.string.kachi_slot_open_app); setTextColor(Color.parseColor(KachiTheme.MUT)); KachiType.apply(this, KachiType.BODY)
            gravity = Gravity.CENTER; setPadding(0, dp(Sp.XS), 0, 0)
        })
    }

    /** Thẻ app trong ô: icon + tên thật (PackageManager). Trên xe app THẬT mở freeform vào ô; off-car hiện thẻ này. */
    /**
     * T4 — nền tối cố định phía sau widget bên thứ ba. Xem KDoc `KachiPalette.widgetBacking` về **vì sao không theo
     * chủ đề**; ở đây chỉ là một lớp tô, cố ý KHÔNG có viền (viền của ô đã do chính ô vẽ).
     */
    private fun appWidgetBacking(): View = View(context).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(Sp.RADIUS_L).toFloat()
            setColor(Color.parseColor(KachiTheme.WIDGET_BACKING))
        }
    }

    /**
     * T4 — thẻ hiện khi id widget đã CHẾT (app cung cấp bị gỡ / bị tắt).
     *
     * Bắt buộc phải có: `AppWidgetHost.createView` với id đã chết trả về một view **rỗng không báo lỗi**, nên nếu
     * không chặn thì ô đó thành ô trống y như chưa gán gì — người dùng chỉ thấy widget của mình biến mất. Thẻ này nói
     * **app nào** (nhờ provider được lưu cùng id) và chạm được để chọn lại.
     */
    private fun deadWidgetCard(content: SlotContent.AppWidget): View =
        TextView(context).apply {
            text = context.getString(R.string.kachi_appwidget_dead, appWidgetName?.invoke(content) ?: content.provider)
            setTextColor(Color.parseColor(KachiTheme.MUT)); KachiType.apply(this, KachiType.BODY)
            gravity = Gravity.CENTER
            setPadding(dp(Sp.L), dp(Sp.SLOT_HEAD_CLEAR), dp(Sp.L), dp(Sp.L))
        }

    private fun appCard(pkg: String): View {
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val pm = context.packageManager
        try {
            col.addView(ImageView(context).apply {
                setImageDrawable(pm.getApplicationIcon(pkg))
                layoutParams = LinearLayout.LayoutParams(dp(Sp.ICON_XXL), dp(Sp.ICON_XXL))
            })
            col.addView(TextView(context).apply {
                text = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
                setTextColor(Color.parseColor(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY)
                gravity = Gravity.CENTER; setPadding(0, dp(Sp.S), 0, 0)
            })
        } catch (e: Exception) {
            col.addView(placeholder("▣  $pkg"))
        }
        return col
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        if (w > 0 && h > 0) {
            val rects = effectiveRects(w, h)
            for (i in slotViews.indices) {
                val r = rects.getOrNull(i) ?: continue
                slotViews[i].measure(
                    MeasureSpec.makeMeasureSpec(r.width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(r.height, MeasureSpec.EXACTLY),
                )
            }
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l; val h = b - t
        if (w <= 0 || h <= 0) return
        val rects = effectiveRects(w, h)
        for (i in slotViews.indices) {
            val rect = rects.getOrNull(i) ?: continue
            slotViews[i].layout(rect.left, rect.top, rect.right, rect.bottom)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
