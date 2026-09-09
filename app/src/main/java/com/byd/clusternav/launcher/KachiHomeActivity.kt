package com.byd.clusternav.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.byd.clusternav.MainActivity
import com.byd.clusternav.R
import com.byd.clusternav.system.WindowCommandDispatcher
import com.byd.clusternav.launcher.KachiTheme.c
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Màn hình chính Kachi (HOME) — nền wall gradient + thanh trạng thái (segmented bố cục + pill) + workspace (widget/ô)
 * + thanh điều khiển 4 viền. Landscape. Thuần code, bám prototype kachi-workspace.html. Dữ liệu = [DemoCarData].
 */
class KachiHomeActivity : Activity() {

    private lateinit var workspace: WorkspaceView
    private lateinit var dock: ControlDockView
    private lateinit var clock: TextView
    private lateinit var prefs: WorkspacePrefs
    private lateinit var mainArea: LinearLayout
    private lateinit var rootFrame: FrameLayout
    private var drawer: AppDrawer? = null
    private var drawerAsOverlay = false
    // Cửa sổ app: XE + EMULATOR đều dùng ShellAppLauncher (am --windowingMode 5 + am task resize) qua dadb loopback —
    // giống hệt cast. Chưa có dadb (emulator chưa `adb reverse`) → fallback IntentAppLauncher (chỉ mở, không reflow được).
    @Volatile private var appLauncher: AppLauncher = IntentAppLauncher(this)
    private var shell: ((String) -> String)? = null
    private var winDispatcher: WindowCommandDispatcher? = null   // B2b: cổng validate sở hữu display + registry vị trí app
    private val embedding get() = shell != null || SlotAppHost.embeddingUsable(this)   // dadb → app render lên VirtualDisplay trong ô (display phụ, KHÔNG caption) kiểu Dudu; hoặc ROM xe platform-signed → ActivityView. Cả 2 đều bỏ freeform + bỏ overlay header.
    private val winExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var dockConfig = ControlRegistry.defaultDock()
    private val presetCells = HashMap<LayoutPreset, ImageView>()
    private lateinit var dateText: TextView
    private lateinit var chipRow: LinearLayout
    private lateinit var profileAvatarView: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { updateClock(); handler.postDelayed(this, 10_000) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        prefs = WorkspacePrefs(this)
        dockConfig = prefs.loadDock()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        content.addView(buildTopStrip(), LinearLayout.LayoutParams(MATCH, WRAP))

        val ws = initialState()
        workspace = WorkspaceView(this).apply {
            carData = DemoCarData; setState(ws)
            onSlotTap = { idx -> openDrawer(idx) }
            onSlotClear = { idx -> clearSlot(idx) }
            onSlotSwap = { a, b -> swapSlots(a, b) }
            onAppOpen = { idx -> reopenApp(idx) }
        }
        selectPreset(ws.preset)
        dock = ControlDockView(this).apply { control = NoCar; setConfig(dockConfig) }

        mainArea = LinearLayout(this)
        layoutMainArea()
        content.addView(mainArea, LinearLayout.LayoutParams(MATCH, 0, 1f).also { it.topMargin = dp(12) })

        rootFrame = FrameLayout(this)
        rootFrame.addView(WallView(this), FrameLayout.LayoutParams(MATCH, MATCH))
        rootFrame.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(rootFrame)

        // Nối shell dadb (localhost:5555) trên nền — nối được thì dùng ShellAppLauncher để reflow THẬT như xe.
        // B2b: MỌI lệnh cửa sổ của launcher đi qua WindowCommandDispatcher — validate sở hữu display TRƯỚC dispatch
        // (launcher chỉ chạm display 0 + VD ô của nó; cụm bị chặn về mặt cấu trúc). Registry vị trí app luôn-bật.
        val dadb = DadbShell(this)
        val dispatcher = WindowCommandDispatcher.get(this)
        winDispatcher = dispatcher
        seedLocations()
        val seam = dispatcher.launcherSeam()
        winExec.execute {
            if (dadb.probe()) {
                shell = seam; appLauncher = ShellAppLauncher(seam)
                runCatching { seam("appops set com.byd.launcher SYSTEM_ALERT_WINDOW allow") }  // để vẽ dải header nổi lên app freeform
                runOnUiThread {
                    workspace.registerVd = dispatcher::registerLauncherVirtualDisplay        // VD ô thuộc LAUNCHER → cổng ownership cho phép
                    workspace.unregisterVd = dispatcher::unregisterLauncherVirtualDisplay
                    workspace.shell = seam; workspace.setState(workspace.currentState())      // bật render app lên VirtualDisplay trong ô (kiểu Dudu, không caption)
                }
            }
        }
    }

    private val overlayHeads by lazy { OverlayHeads(this) }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Dựng lại dải header NỔI (overlay) che caption freeform + hiện ⇄/✕ cho mỗi ô app đang hiện. Nhúng → không cần. */
    private val overlayUpdate = Runnable {
        if (embedding || drawer != null) { overlayHeads.clear(); return@Runnable }
        val st = workspace.currentState(); val n = st.preset.slotCount
        val heads = ArrayList<OverlayHeads.Head>()
        for (i in 0 until n) {
            (st.slots.getOrNull(i) as? SlotContent.App)?.let { app ->
                absoluteSlotRect(i)?.let { r ->
                    val a = appRect(r)
                    heads.add(OverlayHeads.Head(a.left, r.top + dp(3), a.width, a.height, appLabel(app.pkg), "#4c7dff",
                        onSwap = { openDrawer(i) }, onClose = { clearSlot(i) }))
                }
            }
        }
        overlayHeads.show(heads)
    }

    private fun updateOverlayHeads() {
        workspace.removeCallbacks(overlayUpdate)                       // debounce: gọi dồn → chỉ chạy 1 lần (tránh chồng overlay)
        if (embedding || drawer != null) { overlayHeads.clear(); return }
        workspace.postDelayed(overlayUpdate, 350)
    }

    private fun initialState(): WorkspaceState {
        val loaded = prefs.load()
        return if (loaded.slots.all { it is SlotContent.Empty }) {
            WorkspaceState(
                LayoutPreset.THREE,
                listOf(SlotContent.Widget("w_board"), SlotContent.Widget("w_energy"), SlotContent.Widget("w_pm25"), SlotContent.Empty),
            )
        } else loaded
    }

    private fun buildTopStrip(): View {
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = KachiTheme.card(context, 14f, "#990a0d13", "#26ffffff")   // thanh mờ bo góc + viền rõ (prototype topstrip)
            setPadding(dp(14), dp(4), dp(14), dp(4))
        }
        clock = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.02f
        }
        dateText = TextView(this).apply { setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setPadding(dp(10), 0, 0, 0) }
        strip.addView(clock); strip.addView(dateText)
        strip.addView(buildSegmented(), LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(16) })
        strip.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        strip.addView(chipRow)
        strip.addView(pill("Thanh", false) { cycleDockEdge() }, pillLp())
        strip.addView(pill("Cài đặt", true) { startActivity(Intent(this, MainActivity::class.java)) }, pillLp())
        strip.addView(profileAvatar(), LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(10) })
        refreshChips()
        return strip
    }

    private fun pillLp() = LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(8) }

    private fun pill(text: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setPadding(dp(14), dp(6), dp(14), dp(6))
        if (primary) { background = KachiTheme.gradient(context, 999f); setTextColor(Color.WHITE) }
        else { background = KachiTheme.pill(context); setTextColor(c(KachiTheme.INK)) }
        setOnClickListener { onClick() }
    }

    private fun buildSegmented(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = KachiTheme.pill(context); val p = dp(3); setPadding(p, p, p, p)
        }
        presetCells.clear()
        listOf(
            R.drawable.ic_layout_1 to LayoutPreset.ONE,
            R.drawable.ic_layout_2c to LayoutPreset.TWO_COL,
            R.drawable.ic_layout_2r to LayoutPreset.TWO_ROW,
            R.drawable.ic_layout_3 to LayoutPreset.THREE,
            R.drawable.ic_layout_4 to LayoutPreset.QUAD,
        ).forEach { (icon, p) ->
            val cell = ImageView(this).apply {
                setImageResource(icon)
                setPadding(dp(10), dp(7), dp(10), dp(7))
                setOnClickListener { switchPreset(p) }
            }
            presetCells[p] = cell
            bar.addView(cell, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        return bar
    }

    private fun selectPreset(sel: LayoutPreset) {
        presetCells.forEach { (p, cell) ->
            if (p == sel) { cell.background = KachiTheme.gradient(this, 999f); cell.setColorFilter(Color.WHITE) }
            else { cell.background = null; cell.setColorFilter(c(KachiTheme.MUT)) }
        }
    }

    private fun layoutMainArea() {
        (workspace.parent as? ViewGroup)?.removeView(workspace)
        (dock.parent as? ViewGroup)?.removeView(dock)
        mainArea.removeAllViews()
        val vertical = !dockConfig.isVertical()
        mainArea.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        val wsLp = if (vertical) LinearLayout.LayoutParams(MATCH, 0, 1f) else LinearLayout.LayoutParams(0, MATCH, 1f)
        val dockLp = if (vertical) LinearLayout.LayoutParams(MATCH, dp(116)) else LinearLayout.LayoutParams(dp(124), MATCH)
        val gap = dp(10)
        when (dockConfig.edge) {
            DockEdge.BOTTOM -> { mainArea.addView(workspace, wsLp); dockLp.topMargin = gap; mainArea.addView(dock, dockLp) }
            DockEdge.TOP -> { dockLp.bottomMargin = gap; mainArea.addView(dock, dockLp); mainArea.addView(workspace, wsLp) }
            DockEdge.LEFT -> { dockLp.marginEnd = gap; mainArea.addView(dock, dockLp); mainArea.addView(workspace, wsLp) }
            DockEdge.RIGHT -> { mainArea.addView(workspace, wsLp); dockLp.marginStart = gap; mainArea.addView(dock, dockLp) }
        }
    }

    private fun cycleDockEdge() {
        val order = listOf(DockEdge.BOTTOM, DockEdge.LEFT, DockEdge.RIGHT, DockEdge.TOP)
        dockConfig = dockConfig.withEdge(order[(order.indexOf(dockConfig.edge) + 1) % order.size])
        dock.setConfig(dockConfig); prefs.saveDock(dockConfig); layoutMainArea()
        reflowWindows()
    }

    // ── Hồ sơ tài xế: pill hiện tên, chạm = đổi hồ sơ, giữ = tạo mới ──
    private fun chipLp() = LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(8) }

    private fun chip(text: String, iconName: String?, color: String): TextView = TextView(this).apply {
        this.text = text; setTextColor(c(color)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f); gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), 0, dp(4), 0)   // KHÔNG viền pill — chip prototype chỉ icon + chữ
        if (iconName != null) {
            val r = KachiTheme.iconRes(iconName)
            if (r != 0) {
                val d = resources.getDrawable(r, theme).apply { setBounds(0, 0, dp(16), dp(16)); setTint(c(color)) }
                setCompoundDrawablesRelative(d, null, null, null); compoundDrawablePadding = dp(6)
            }
        }
    }

    private fun refreshChips() {
        chipRow.removeAllViews()
        val d = DemoCarData
        val pm = d.pm25Level()?.let { if (it <= 2) "Tốt" else if (it <= 4) "TB" else "Kém" } ?: "—"
        chipRow.addView(chip("PM2.5 · $pm", "ic-leaf", "#c3cee0"), chipLp())
        chipRow.addView(chip("${d.outsideTempC() ?: "—"}°C ngoài", null, "#c3cee0"), chipLp())
        chipRow.addView(chip("${d.batteryPercent() ?: "—"}% · ${d.rangeKm() ?: "—"} km", "ic-bolt", KachiTheme.GREEN), chipLp())
    }

    private fun profileAvatar(): TextView {
        profileAvatarView = TextView(this).apply {
            text = prefs.activeProfile().take(1).uppercase(); setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            val s = dp(30); width = s; height = s; background = KachiTheme.gradient(this@KachiHomeActivity, 999f)
            setOnClickListener { cycleProfile() }
            setOnLongClickListener { addProfileDialog(); true }
        }
        return profileAvatarView
    }

    private fun cycleProfile() {
        val list = prefs.profiles()
        if (list.size <= 1) { addProfileDialog(); return }
        val i = (list.indexOf(prefs.activeProfile()) + 1) % list.size
        prefs.setActiveProfile(list[i]); applyProfile(); toast("Hồ sơ: ${list[i]}")
    }

    private fun addProfileDialog() {
        val input = EditText(this).apply { hint = "Tên hồ sơ (vd: Đường trường)" }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Hồ sơ mới")
            .setView(input)
            .setPositiveButton("Tạo") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) { prefs.addProfile(n); applyProfile() }
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    /** Nạp lại workspace + dock của hồ sơ đang chọn và vẽ lại. */
    private fun applyProfile() {
        dockConfig = prefs.loadDock()
        val ws = initialState()
        workspace.setState(ws)
        dock.setConfig(dockConfig)
        selectPreset(ws.preset)
        layoutMainArea()
        profileAvatarView.text = prefs.activeProfile().take(1).uppercase()
    }

    private fun switchPreset(p: LayoutPreset) {
        val s = workspace.currentState().withPreset(p)
        workspace.setState(s); prefs.save(s); selectPreset(p)
        reflowWindows()
    }

    /**
     * Sau khi đổi bố cục/viền: sắp lại cửa sổ app ĐANG mở theo THỨ TỰ ô (KHÔNG reset, app vẫn chạy).
     * App ở ô hiện (index < số ô) → freeform đúng khung; app tràn (index ≥ số ô) → fullscreen chạy nền, ẩn sau launcher.
     * Thứ tự lệnh cho z-order đúng: overflow→fullscreen trước, kéo launcher lên (che overflow), rồi mở lại app hiện (nổi trên launcher).
     */
    private fun reflowWindows() {
        if (embedding) return   // nhúng: ô đổi kích thước theo layout view → app tự reflow, không cần am task resize
        workspace.post {
            val st = workspace.currentState()
            val n = st.preset.slotCount
            val visible = ArrayList<Pair<String, SlotRect>>()
            val overflow = ArrayList<String>()
            for (i in 0..3) {
                val c = st.slots.getOrNull(i)
                if (c is SlotContent.App) {
                    if (i < n) absoluteSlotRect(i)?.let { visible.add(c.pkg to appRect(it)) } else overflow.add(c.pkg)
                }
            }
            if (visible.isEmpty() && overflow.isEmpty()) return@post
            val s = shell
            winExec.execute {
                overflow.forEach { appLauncher.closeSlot(it) }                                   // tràn → fullscreen chạy nền
                if (overflow.isNotEmpty() && s != null) { s(HOME_FRONT); Thread.sleep(250) }      // kéo launcher lên che overflow
                visible.forEach { (pkg, rect) -> appLauncher.openInSlot(pkg, rect) }              // ô hiện → freeform + đưa LÊN TRƯỚC launcher (cửa sổ hiện, không phải thẻ)
                runOnUiThread { updateOverlayHeads() }
            }
        }
    }

    private fun openDrawer(index: Int) {
        if (drawer != null) return
        val current = (workspace.currentState().slots.getOrNull(index) as? SlotContent.Widget)?.ids ?: emptyList()
        val d = AppDrawer(
            this, WidgetRegistry.ALL, current,
            onPickApp = { pkg -> assignApp(index, pkg) },
            onPickWidgets = { ids -> assignWidgets(index, ids) },
            onClose = { closeDrawer() },
        )
        drawer = d
        overlayHeads.clear()
        // Drawer NỔI như overlay (TYPE_APPLICATION_OVERLAY) → trên cả cửa sổ app freeform. Nếu để trong cửa sổ launcher
        // (đáy z-order) sẽ bị app freeform đè lên (lỗi "app đè popup chọn app"). Chưa có quyền overlay → fallback vào launcher.
        drawerAsOverlay = android.provider.Settings.canDrawOverlays(this) && runCatching {
            d.isFocusableInTouchMode = true
            d.setOnKeyListener { _, code, ev ->
                if (code == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) { closeDrawer(); true } else false
            }
            windowManager.addView(
                d,
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ),
            )
            d.requestFocus()
            true
        }.getOrDefault(false)
        if (!drawerAsOverlay) rootFrame.addView(d, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun assignWidgets(index: Int, ids: List<String>) {
        closeDrawer()
        val content: SlotContent = if (ids.isEmpty()) SlotContent.Empty else SlotContent.Widget(ids)
        val s = workspace.currentState().withSlot(index, content)
        workspace.setState(s); prefs.save(s)
    }

    private fun closeDrawer() {
        drawer?.let { if (drawerAsOverlay) runCatching { windowManager.removeViewImmediate(it) } else rootFrame.removeView(it) }
        drawer = null; drawerAsOverlay = false; updateOverlayHeads()
    }

    private fun assignApp(index: Int, pkg: String) {
        closeDrawer()
        val prev = workspace.currentState().slots.getOrNull(index) as? SlotContent.App
        val s = workspace.currentState().withSlot(index, SlotContent.App(pkg))
        workspace.setState(s); prefs.save(s)
        if (prev != null && prev.pkg != pkg) winDispatcher?.remove(prev.pkg)   // B2b: ô thay app khác → gỡ app cũ khỏi registry
        winDispatcher?.place(pkg, 0, index)                                    // B2b: app mới chiếm ô index trên display 0
        placeAppWindow(pkg, index, fresh = true)
    }

    private fun reopenApp(index: Int) {
        (workspace.currentState().slots.getOrNull(index) as? SlotContent.App)?.let { placeAppWindow(it.pkg, index) }
    }

    /** Mở/đặt cửa sổ app THẬT vào ô [index] (freeform + resize) trên thread nền (dadb blocking). */
    private fun placeAppWindow(pkg: String, index: Int, fresh: Boolean = false) {
        if (embedding) return   // WorkspaceView nhúng app bằng ActivityView → không cần freeform
        val rect = absoluteSlotRect(index) ?: return
        val s = shell
        winExec.execute {
            // Đặt MỚI: force-stop trước để app mở TƯƠI dạng freeform, KHÔNG tái dùng task fullscreen cũ
            // (gốc lỗi "chọn gmaps mở fullscreen đè hết mọi thứ" — task cũ fullscreen bị `am start` tái dùng).
            if (fresh && s != null) runCatching { s("am force-stop $pkg") }
            appLauncher.openInSlot(pkg, appRect(rect))
            runOnUiThread { updateOverlayHeads() }
        }
    }

    private fun clearSlot(index: Int) {
        val cur = workspace.currentState()
        (cur.slots.getOrNull(index) as? SlotContent.App)?.let { app ->
            if (!embedding) winExec.execute { appLauncher.closeSlot(app.pkg) }
            winDispatcher?.remove(app.pkg)   // B2b: ô đóng → gỡ vị trí (bất biến MỘT-VỊ-TRÍ)
        }
        val s = cur.clearSlot(index); workspace.setState(s); prefs.save(s); updateOverlayHeads()
    }

    /** Kéo-thả đổi chỗ 2 ô (widget/app). */
    private fun swapSlots(a: Int, b: Int) {
        val cur = workspace.currentState()
        if (a !in cur.slots.indices || b !in cur.slots.indices) return
        val slots = cur.slots.toMutableList()
        val t = slots[a]; slots[a] = slots[b]; slots[b] = t
        val ns = cur.copy(slots = slots); workspace.setState(ns); prefs.save(ns)
        // B2b: 2 ô đổi chỗ → cập nhật lại index vị trí của app (nếu có) ở mỗi ô.
        (slots.getOrNull(a) as? SlotContent.App)?.let { winDispatcher?.place(it.pkg, 0, a) }
        (slots.getOrNull(b) as? SlotContent.App)?.let { winDispatcher?.place(it.pkg, 0, b) }
    }

    /** B2b: ghi vị trí ban đầu của các ô App vào registry → bất biến MỘT-VỊ-TRÍ có mặt ngay khi mở app. */
    private fun seedLocations() {
        workspace.currentState().slots.forEachIndexed { i, c ->
            if (c is SlotContent.App) winDispatcher?.place(c.pkg, 0, i)
        }
    }

    /** Khung ô ở toạ độ MÀN HÌNH (cho freeform on-car): offset vị trí workspace + Rect ô. */
    private fun absoluteSlotRect(index: Int): SlotRect? {
        if (workspace.width <= 0 || workspace.height <= 0) return null
        val rects = WorkspaceLayout.slots(workspace.currentState().preset, workspace.width, workspace.height, dp(10))
        val r = rects.getOrNull(index) ?: return null
        val loc = IntArray(2); workspace.getLocationOnScreen(loc)
        return SlotRect(index, loc[0] + r.left, loc[1] + r.top, loc[0] + r.right, loc[1] + r.bottom)
    }

    /**
     * Khung CỬA SỔ app = LẤP ĐẦY ô (không bezel to như trước). Bo góc lo bằng: dải header đục (bo góc TRÊN + che caption)
     * + 2 mặt nạ góc DƯỚI ([OverlayHeads]). Nhờ vậy margin ~0 giống prototype mà góc vẫn tròn.
     */
    private fun appRect(s: SlotRect): SlotRect {
        val m = dp(10)        // margin trái/phải/dưới — nhiều hơn tý (yêu cầu owner)
        val topCap = dp(24)   // thụt TRÊN thêm để caption freeform (~36px) lọt trong ô → hết "lòi đầu"
        return SlotRect(s.index, s.left + m, s.top + topCap, s.right - m, s.bottom - m)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() { if (drawer != null) closeDrawer() }

    private fun updateClock() {
        clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        dateText.text = SimpleDateFormat("EEEE, dd/MM", Locale.forLanguageTag("vi")).format(Date())
    }

    override fun onResume() { super.onResume(); goImmersive(); updateClock(); handler.post(tick); workspace.postDelayed({ updateOverlayHeads() }, 600) }

    @Suppress("DEPRECATION")
    private fun goImmersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus); if (hasFocus) goImmersive()
    }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tick) }
    override fun onDestroy() { super.onDestroy(); winExec.shutdownNow(); overlayHeads.clear() }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val HOME_FRONT = "am start -n com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity"
    }
}
