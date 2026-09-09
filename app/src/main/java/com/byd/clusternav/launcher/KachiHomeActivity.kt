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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.byd.clusternav.MainActivity
import com.byd.clusternav.R
import com.byd.clusternav.system.WindowCommandDispatcher
import com.byd.clusternav.launcher.KachiTheme.c
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Màn hình chính Kachi (HOME) — wall gradient + thanh trạng thái + workspace (widget/ô) + thanh điều khiển 4 viền.
 * Landscape, thuần code, bám prototype kachi-workspace.html. Dữ liệu = [DemoCarData].
 *
 * B5a: state launcher KHÔNG còn ở [WorkspaceView] — [HomeViewModel] giữ `StateFlow<HomeUiState>` là NGUỒN SỰ THẬT
 * DUY NHẤT. Activity thu (`repeatOnLifecycle(STARTED)`) → [render] áp state lên view; user event → INTENT (một chiều).
 * Tự quản [LifecycleOwner] (LifecycleRegistry) vì kế thừa `android.app.Activity` → dùng được `lifecycleScope`.
 * Orchestration cửa sổ freeform + overlay caption tách sang [LauncherWindows]. (Decompose view-component: B5b.)
 */
class KachiHomeActivity : Activity(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var workspace: WorkspaceView
    private lateinit var dock: ControlDockView
    private lateinit var clock: TextView
    private lateinit var viewModel: HomeViewModel
    private lateinit var windows: LauncherWindows
    private lateinit var mainArea: LinearLayout
    private lateinit var rootFrame: FrameLayout
    private var drawer: AppDrawer? = null
    private var drawerAsOverlay = false
    // Cửa sổ app: XE + EMULATOR dùng ShellAppLauncher (am --windowingMode 5 + am task resize) qua dadb loopback (như cast).
    // Chưa có dadb (emulator chưa `adb reverse`) → fallback IntentAppLauncher (chỉ mở, không reflow).
    @Volatile private var appLauncher: AppLauncher = IntentAppLauncher(this)
    private var shell: ((String) -> String)? = null
    private var winDispatcher: WindowCommandDispatcher? = null   // B2b: cổng validate sở hữu display + registry vị trí app
    // dadb → app render lên VirtualDisplay trong ô kiểu Dudu; hoặc ROM platform-signed → ActivityView. Cả 2 bỏ freeform +
    // overlay header. HomeUiState.embedded phản chiếu cờ này (đồng bộ ở probe).
    private val embedding get() = shell != null || SlotAppHost.embeddingUsable(this)
    private val winExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val presetCells = HashMap<LayoutPreset, ImageView>()
    private lateinit var dateText: TextView
    private lateinit var chipRow: LinearLayout
    private lateinit var profileAvatarView: TextView
    private var shownState: HomeUiState? = null   // view-side diff cache của collector (KHÔNG phải nguồn sự thật)

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { updateClock(); handler.postDelayed(this, 10_000) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        // ViewModel = nguồn sự thật; nạp initial từ repository (WorkspacePrefs). embedded ban đầu = khả năng ActivityView.
        viewModel = HomeViewModel.factory(this, embedded = SlotAppHost.embeddingUsable(this))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        content.addView(buildTopStrip(), LinearLayout.LayoutParams(MATCH, WRAP))

        workspace = WorkspaceView(this).apply {
            carData = DemoCarData
            onSlotTap = { idx -> openDrawer(idx) }
            onSlotClear = { idx -> clearSlot(idx) }
            onSlotSwap = { a, b -> swapSlots(a, b) }
            onAppOpen = { idx -> reopenApp(idx) }
        }
        windows = LauncherWindows(
            this, workspace, winExec,
            state = { viewModel.uiState.value }, embedding = { embedding }, drawerOpen = { drawer != null },
            shell = { shell }, appLauncher = { appLauncher }, dispatcher = { winDispatcher },
            onSlotSwap = { idx -> openDrawer(idx) }, onSlotClose = { idx -> clearSlot(idx) },
        )
        dock = ControlDockView(this).apply { control = NoCar }

        mainArea = LinearLayout(this)
        layoutMainArea()
        content.addView(mainArea, LinearLayout.LayoutParams(MATCH, 0, 1f).also { it.topMargin = dp(12) })

        rootFrame = FrameLayout(this)
        rootFrame.addView(WallView(this), FrameLayout.LayoutParams(MATCH, MATCH))
        rootFrame.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(rootFrame)

        // Thu NGUỒN SỰ THẬT: mọi thay đổi state → render (view-only). repeatOnLifecycle huỷ khi < STARTED.
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }

        // Nối shell dadb (localhost:5555) trên nền → ShellAppLauncher reflow THẬT như xe. B2b: mọi lệnh cửa sổ đi qua
        // WindowCommandDispatcher (validate sở hữu display: launcher chỉ chạm display 0 + VD ô của nó). Registry luôn-bật.
        val dadb = DadbShell(this)
        val dispatcher = WindowCommandDispatcher.get(this)
        winDispatcher = dispatcher
        windows.seedLocations()
        val seam = dispatcher.launcherSeam()
        winExec.execute {
            if (dadb.probe()) {
                shell = seam; appLauncher = ShellAppLauncher(seam)
                runCatching { seam("appops set com.byd.launcher SYSTEM_ALERT_WINDOW allow") }  // để vẽ dải header nổi lên app freeform
                runOnUiThread {
                    workspace.registerVd = dispatcher::registerLauncherVirtualDisplay        // VD ô thuộc LAUNCHER → ownership cho phép
                    workspace.unregisterVd = dispatcher::unregisterLauncherVirtualDisplay
                    workspace.shell = seam; workspace.render(viewModel.uiState.value.workspace)  // bật render app lên VirtualDisplay trong ô
                    viewModel.setEmbedded(true)                                                // dadb nối được → nhúng (giữ embedded khớp getter)
                }
            }
        }
    }

    /**
     * Áp [state] lên VIEW (duy nhất một chỗ, do collector gọi) — chỉ đọc-vẽ, KHÔNG đổi state. Diff so với [shownState]
     * để chỉ làm việc khi phần liên quan đổi. Side-effect cửa sổ theo-ô ở handler; ở đây chỉ reflow khi preset/viền đổi.
     */
    private fun render(state: HomeUiState) {
        val prev = shownState
        workspace.render(state.workspace)
        if (prev?.preset != state.preset) selectPreset(state.preset)
        if (prev?.dock != state.dock) {
            val edgeChanged = prev != null && prev.dock.edge != state.dock.edge
            dock.setConfig(state.dock)
            if (edgeChanged) layoutMainArea()
        }
        if (prev == null || prev.activeProfile != state.activeProfile) {
            profileAvatarView.text = state.activeProfile.take(1).uppercase()
        }
        if (prev != null && (prev.preset != state.preset || prev.dock.edge != state.dock.edge)) windows.reflow()
        shownState = state
        windows.updateOverlayHeads()
    }

    private fun buildTopStrip(): View {
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = KachiTheme.card(context, 14f, "#990a0d13", "#26ffffff")   // thanh mờ bo góc + viền rõ (prototype)
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
        val cfg = viewModel.uiState.value.dock
        val vertical = !cfg.isVertical()
        mainArea.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        val wsLp = if (vertical) LinearLayout.LayoutParams(MATCH, 0, 1f) else LinearLayout.LayoutParams(0, MATCH, 1f)
        val dockLp = if (vertical) LinearLayout.LayoutParams(MATCH, dp(116)) else LinearLayout.LayoutParams(dp(124), MATCH)
        val gap = dp(10)
        when (cfg.edge) {
            DockEdge.BOTTOM -> { mainArea.addView(workspace, wsLp); dockLp.topMargin = gap; mainArea.addView(dock, dockLp) }
            DockEdge.TOP -> { dockLp.bottomMargin = gap; mainArea.addView(dock, dockLp); mainArea.addView(workspace, wsLp) }
            DockEdge.LEFT -> { dockLp.marginEnd = gap; mainArea.addView(dock, dockLp); mainArea.addView(workspace, wsLp) }
            DockEdge.RIGHT -> { mainArea.addView(workspace, wsLp); dockLp.marginStart = gap; mainArea.addView(dock, dockLp) }
        }
    }

    private fun cycleDockEdge() = viewModel.cycleDockEdge()   // state+persist → collector: dock.setConfig + layoutMainArea + reflow

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

    // ── Hồ sơ tài xế: pill hiện tên, chạm = đổi hồ sơ, giữ = tạo mới ──
    private fun profileAvatar(): TextView {
        profileAvatarView = TextView(this).apply {
            text = viewModel.uiState.value.activeProfile.take(1).uppercase(); setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            val s = dp(30); width = s; height = s; background = KachiTheme.gradient(this@KachiHomeActivity, 999f)
            setOnClickListener { cycleProfile() }
            setOnLongClickListener { addProfileDialog(); true }
        }
        return profileAvatarView
    }

    private fun cycleProfile() {
        val s = viewModel.uiState.value
        val list = s.profiles
        if (list.size <= 1) { addProfileDialog(); return }
        val i = (list.indexOf(s.activeProfile) + 1) % list.size
        viewModel.switchProfile(list[i]); toast("Hồ sơ: ${list[i]}")   // collector nạp lại workspace/dock/preset/avatar
    }

    private fun addProfileDialog() {
        val input = EditText(this).apply { hint = "Tên hồ sơ (vd: Đường trường)" }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Hồ sơ mới")
            .setView(input)
            .setPositiveButton("Tạo") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) viewModel.addProfile(n)   // collector nạp lại (hồ sơ mới = bố cục mặc định)
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun switchPreset(p: LayoutPreset) = viewModel.setPreset(p)   // state+persist → collector: render + selectPreset + reflow

    private fun openDrawer(index: Int) {
        if (drawer != null) return
        val current = (viewModel.uiState.value.slots.getOrNull(index) as? SlotContent.Widget)?.ids ?: emptyList()
        val d = AppDrawer(
            this, WidgetRegistry.ALL, current,
            onPickApp = { pkg -> assignApp(index, pkg) },
            onPickWidgets = { ids -> assignWidgets(index, ids) },
            onClose = { closeDrawer() },
        )
        drawer = d
        windows.clearOverlays()
        // Drawer NỔI như overlay (TYPE_APPLICATION_OVERLAY) → trên cả cửa sổ app freeform (tránh app đè popup).
        // Chưa có quyền overlay → fallback vào cửa sổ launcher.
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
        viewModel.assignWidgets(index, ids)   // state+persist → collector: workspace.render
    }

    private fun closeDrawer() {
        drawer?.let { if (drawerAsOverlay) runCatching { windowManager.removeViewImmediate(it) } else rootFrame.removeView(it) }
        drawer = null; drawerAsOverlay = false; windows.updateOverlayHeads()
    }

    private fun assignApp(index: Int, pkg: String) {
        closeDrawer()
        val prev = viewModel.uiState.value.slots.getOrNull(index) as? SlotContent.App
        viewModel.assignApp(index, pkg)                                       // state+persist → collector: workspace.render
        if (prev != null && prev.pkg != pkg) winDispatcher?.remove(prev.pkg)  // B2b: ô thay app khác → gỡ app cũ khỏi registry
        winDispatcher?.place(pkg, 0, index)                                   // B2b: app mới chiếm ô index trên display 0
        windows.placeApp(pkg, index, fresh = true)
    }

    private fun reopenApp(index: Int) {
        (viewModel.uiState.value.slots.getOrNull(index) as? SlotContent.App)?.let { windows.placeApp(it.pkg, index) }
    }

    private fun clearSlot(index: Int) {
        val cur = viewModel.uiState.value
        (cur.slots.getOrNull(index) as? SlotContent.App)?.let { app ->
            windows.closeApp(app.pkg)
            winDispatcher?.remove(app.pkg)   // B2b: ô đóng → gỡ vị trí (bất biến MỘT-VỊ-TRÍ)
        }
        viewModel.clearSlot(index)   // state+persist → collector: workspace.render + updateOverlayHeads
    }

    /** Kéo-thả đổi chỗ 2 ô (widget/app). */
    private fun swapSlots(a: Int, b: Int) {
        val cur = viewModel.uiState.value
        if (a !in cur.slots.indices || b !in cur.slots.indices) return
        viewModel.swapSlots(a, b)   // state+persist → collector: workspace.render
        val ns = viewModel.uiState.value
        // B2b: 2 ô đổi chỗ → cập nhật lại index vị trí của app (nếu có) ở mỗi ô.
        (ns.slots.getOrNull(a) as? SlotContent.App)?.let { winDispatcher?.place(it.pkg, 0, a) }
        (ns.slots.getOrNull(b) as? SlotContent.App)?.let { winDispatcher?.place(it.pkg, 0, b) }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() { if (drawer != null) closeDrawer() }

    private fun updateClock() {
        clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        dateText.text = SimpleDateFormat("EEEE, dd/MM", Locale.forLanguageTag("vi")).format(Date())
    }

    override fun onStart() { super.onStart(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START) }

    override fun onResume() {
        super.onResume(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        goImmersive(); updateClock(); handler.post(tick); workspace.postDelayed({ windows.updateOverlayHeads() }, 600)
    }

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

    override fun onPause() { super.onPause(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); handler.removeCallbacks(tick) }

    override fun onStop() { super.onStop(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }

    override fun onDestroy() {
        super.onDestroy(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        winExec.shutdownNow(); windows.clearOverlays()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
