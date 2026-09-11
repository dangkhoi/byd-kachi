package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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
import com.byd.clusternav.system.inputd.InputDaemonClient

/**
 * Sân khấu workspace: đặt tối đa 4 "ô" theo [WorkspaceLayout] cho [WorkspaceState.preset].
 * P1: mỗi ô là placeholder (chạm → callback [onSlotTap], nối drawer ở P2). Thuần code → gọn, không đụng layout seal.
 */
class WorkspaceView(context: Context) : ViewGroup(context) {

    var onSlotTap: ((Int) -> Unit)? = null
    var onSlotClear: ((Int) -> Unit)? = null
    var onSlotSwap: ((Int, Int) -> Unit)? = null
    var onAppOpen: ((Int) -> Unit)? = null
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

    private val gapPx = dp(10)
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
        val old = displayed
        val oldStatus = displayedStatus
        displayed = s; displayedStatus = status; carStatus = status
        // Luật "ô nào cần dựng lại" nằm ở :core (WorkspaceRenderPlanner) → test được off-car, kể cả ca P-bug2.
        when (val plan = WorkspaceRenderPlanner.decide(old, s, slotViews.size, status != oldStatus, embedChanged)) {
            WorkspaceRenderPlan.RebuildAll -> { rebuild(); return }
            is WorkspaceRenderPlan.PerSlot -> plan.rebuild.forEach { i ->
                removeView(slotViews[i])
                val v = makeSlot(i, s.slots.getOrElse(i) { SlotContent.Empty })
                addView(v); slotViews[i] = v
            }
        }
        requestLayout(); invalidate()
    }

    /** Gói dữ liệu render widget hiện tại (trạng thái xe + nhạc live + cổng ra lệnh cho ô hành động + đơn vị). */
    private fun widgetData() =
        WidgetData(carStatus, mediaProvider(), onMedia, control, unitPrefs, photoProvider(), photoIntervalSec)

    /**
     * U4(b) — nguồn ảnh cho widget trình chiếu. Là HÀM (không phải danh sách) để chỗ gọi quyết định khi nào đọc thư
     * mục: đọc thư mục là I/O, không nên chạy mỗi lần dựng ô.
     */
    private var photoProvider: () -> List<String> = { emptyList() }
    private var photoIntervalSec: Int = Slideshow.DEFAULT_INTERVAL_SEC
    private var photoCount = -1

    /**
     * U4(b) — đặt nguồn ảnh cho widget trình chiếu. Dựng lại **chỉ ô widget** khi nguồn thật sự đổi.
     *
     * Cùng lối với [setUnitPrefs]: phải dựng lại vì ảnh nằm trong View đã dựng, nhưng **KHÔNG** dựng lại ô đang
     * chiếu app (làm thế là ngắt kênh chạm — ràng buộc C5 của gói 2). Gọi lại với cùng nguồn thì không làm gì.
     */
    fun setPhotoSource(paths: List<String>, intervalSec: Int) {
        val changed = paths.size != photoCount || intervalSec != photoIntervalSec
        photoProvider = { paths }
        photoIntervalSec = intervalSec
        photoCount = paths.size
        if (changed) rebuildWidgetSlots()
    }

    private fun rebuild() {
        removeAllViews(); slotViews.clear()
        for (i in 0 until displayed.preset.slotCount) {
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
            cornerRadius = dp(16).toFloat()
            setColor(Color.parseColor("#171A20"))                 // nền card nhấc nhẹ khỏi nền (prototype --k-card)
            setStroke(dp(1), Color.parseColor("#26FFFFFF"))       // viền HAIRLINE SÁNG mảnh (prototype, không phải viền tối)
        }
        fl.clipToOutline = true                                    // clip nội dung theo góc bo (như overflow:hidden của prototype)
        val mm = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        when (content) {
            is SlotContent.Widget -> {
                val body = WidgetViews.buildGrid(context, content.ids, widgetData())
                body.setPadding(body.paddingLeft, body.paddingTop + dp(30), body.paddingRight, body.paddingBottom)  // đẩy content XUỐNG DƯỚI header (hết đè)
                fl.addView(body, mm)
                val first = content.ids.firstOrNull() ?: ""
                fl.addView(slotHead(index, widgetName(first), widgetAccent(first)), headLp())
                fl.setOnClickListener { onSlotTap?.invoke(index) }
                fl.setOnLongClickListener { startSlotDrag(index, fl); true }
            }
            is SlotContent.App -> {
                fl.addView(appCard(content.pkg), mm)                       // fallback phía sau (hiện nếu nhúng lỗi)
                val sh = shell
                if (sh != null) {
                    val host = VdAppHost(context, slotDensityDpi, registerVd, unregisterVd, inputClient)  // sideload: app render lên VirtualDisplay (display phụ → KHÔNG caption) qua dadb — kiểu Dudu, SurfaceView cho đỡ lag; chạm qua input-daemon (fallback `input -d`)
                    fl.addView(host, mm); host.bind(content.pkg, sh)
                } else if (SlotAppHost.embeddingUsable(context)) {
                    val host = SlotAppHost(context, dp(16).toFloat())
                    if (host.available()) { fl.addView(host, mm); host.embed(content.pkg) }   // ROM xe (platform-signed): ActivityView, không lag/caption
                }
                fl.addView(slotHead(index, appName(content.pkg), "#4c7dff"), headLp())   // ⇄/✕ đè lên trên cùng
                fl.setOnClickListener { onAppOpen?.invoke(index) }
                fl.setOnLongClickListener { startSlotDrag(index, fl); true }
            }
            SlotContent.Empty -> {
                fl.background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#0b0f16"))
                    setStroke(dp(2), Color.parseColor("#42506a"), dp(7).toFloat(), dp(5).toFloat())
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

    private fun headLp() = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(34), Gravity.TOP)

    private fun appName(pkg: String): String = runCatching {
        val pm = context.packageManager; pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun widgetName(id: String): String = WidgetRegistry.ALL.firstOrNull { it.id == id }?.label ?: id

    /** Header ô — owner: CHỈ 1 nút đổi app, canh GIỮA trên cùng, KHÔNG thanh nền, KHÔNG nút ✕. */
    private fun slotHead(index: Int, name: String, dotColor: String): View {
        val wrap = FrameLayout(context)   // trong suốt, không nền
        val btn = ImageView(context).apply {
            val r = KachiTheme.iconRes("ic-swap"); if (r != 0) { setImageResource(r); setColorFilter(Color.WHITE) }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            // scrim tròn mờ RẤT nhẹ chỉ để icon còn thấy trên app nền sáng (không phải thanh nền)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#4D000000")) }
            setOnClickListener { onSlotTap?.invoke(index) }
        }
        wrap.addView(btn, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP or Gravity.CENTER_HORIZONTAL).also { it.topMargin = dp(4) })
        return wrap
    }

    /** Màu chấm slot-head theo widget (khớp accent của widget); app dùng accent xanh. */
    private fun widgetAccent(id: String): String = when (id) {
        "w_energy" -> "#34d399"; "w_pm25" -> "#29d3ee"; "w_board" -> "#7b5cff"
        "w_media" -> "#f59e0b"; "w_speed" -> "#fb7185"; "w_tire" -> "#94a3b8"
        else -> "#4c7dff"
    }

    private fun headBtnLp() = LinearLayout.LayoutParams(dp(24), dp(24)).also { it.marginStart = dp(6) }

    private fun headBtn(icon: String, onClick: () -> Unit): View = ImageView(context).apply {
        val r = KachiTheme.iconRes(icon); if (r != 0) { setImageResource(r); setColorFilter(Color.WHITE) }
        setPadding(dp(5), dp(5), dp(5), dp(5))
        background = GradientDrawable().apply { cornerRadius = dp(7).toFloat(); setColor(Color.parseColor("#1fffffff")) }
        setOnClickListener { onClick() }
    }

    private fun placeholder(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(Color.parseColor("#93a0b4"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
    }

    /** Ô trống: viền đứt + dấu ＋ to + nhãn — rõ là "chỗ thêm app". */
    private fun emptyAdd(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        addView(TextView(context).apply {
            text = "＋"; setTextColor(Color.parseColor("#8fa0bd")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f); gravity = Gravity.CENTER
        })
        addView(TextView(context).apply {
            text = "Mở ứng dụng"; setTextColor(Color.parseColor("#93a0b4")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0)
        })
    }

    /** Thẻ app trong ô: icon + tên thật (PackageManager). Trên xe app THẬT mở freeform vào ô; off-car hiện thẻ này. */
    private fun appCard(pkg: String): View {
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val pm = context.packageManager
        try {
            col.addView(ImageView(context).apply {
                setImageDrawable(pm.getApplicationIcon(pkg))
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            })
            col.addView(TextView(context).apply {
                text = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
                setTextColor(Color.parseColor("#eaf0f8")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
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
            val rects = WorkspaceLayout.slots(displayed.preset, w, h, gapPx)
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
        val rects = WorkspaceLayout.slots(displayed.preset, w, h, gapPx)
        for (i in slotViews.indices) {
            val rect = rects.getOrNull(i) ?: continue
            slotViews[i].layout(rect.left, rect.top, rect.right, rect.bottom)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
