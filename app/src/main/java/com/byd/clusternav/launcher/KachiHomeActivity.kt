package com.byd.clusternav.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.byd.clusternav.AppContainer
import com.byd.clusternav.MainActivity
import kotlinx.coroutines.launch

/**
 * Màn hình chính Kachi (HOME) — wall gradient + thanh trạng thái + workspace (widget/ô) + thanh điều khiển 4 viền.
 * Landscape, thuần code, bám prototype kachi-workspace.html. Dữ liệu xe LIVE = [AppContainer.carStatusRepository]
 * (`StateFlow<CarStatus>`) thu qua `repeatOnLifecycle` → [HomeViewModel.setCarStatus] → state → render (off-car "—").
 *
 * B5a: [HomeViewModel] giữ `StateFlow<HomeUiState>` là NGUỒN SỰ THẬT DUY NHẤT; Activity thu
 * (`repeatOnLifecycle(STARTED)`) → [render] áp state lên view; user event → INTENT (một chiều).
 *
 * B5b: composition-root MỎNG. Đồ thị phụ thuộc + VM lấy từ [AppContainer]. Dựng-view tách thành đơn vị cohesive:
 * [KachiTopStrip] · [DrawerController] · [ProfileBar] · [DockAreaLayout] · [LauncherWindows]. Activity còn: lấy VM +
 * collect → [render] + glue lifecycle + glue intent theo-ô. Tự quản [LifecycleOwner] + [ViewModelStoreOwner] vì kế
 * thừa `android.app.Activity` (không có androidx `ComponentActivity`/`by viewModels()` — thêm sẽ là phụ thuộc mới).
 */
class KachiHomeActivity : Activity(), LifecycleOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val vmStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = vmStore

    private lateinit var container: AppContainer
    private lateinit var workspace: WorkspaceView
    private lateinit var dock: ControlDockView
    private lateinit var viewModel: HomeViewModel
    private lateinit var windows: LauncherWindows
    private lateinit var topStrip: KachiTopStrip
    private lateinit var drawerController: DrawerController
    private lateinit var profileBar: ProfileBar
    private lateinit var mainArea: LinearLayout
    private lateinit var rootFrame: FrameLayout
    private val media by lazy { MediaBridge(this) }        // đọc nhạc live cho w_media + transport
    private var customizePanel: CustomizePanel? = null     // overlay Tuỳ biến thanh điều khiển
    // Cửa sổ app: dadb (xe+emulator) → ShellAppLauncher (am --windowingMode 5 + am task resize); chưa có dadb → IntentAppLauncher.
    @Volatile private var appLauncher: AppLauncher = IntentAppLauncher(this)
    private var shell: ((String) -> String)? = null
    // dadb → app render lên VirtualDisplay trong ô (Dudu) hoặc ROM platform-signed → ActivityView; cả 2 bỏ freeform + overlay header.
    private val embedding get() = shell != null || SlotAppHost.embeddingUsable(this)
    private val winExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var shownState: HomeUiState? = null   // view-side diff cache của collector (KHÔNG phải nguồn sự thật)

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { topStrip.updateClock(); handler.postDelayed(this, 10_000) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        container = AppContainer.get(this)
        // VM = nguồn sự thật (nạp từ repository qua factory AppContainer). embedded ban đầu = khả năng ActivityView; this là ViewModelStoreOwner.
        viewModel = ViewModelProvider(
            this, container.homeViewModelFactory(embedded = SlotAppHost.embeddingUsable(this)),
        )[HomeViewModel::class.java]
        profileBar = ProfileBar(this, viewModel)

        topStrip = KachiTopStrip(
            this,
            onSelectPreset = { viewModel.setPreset(it) },
            onCycleDock = { viewModel.cycleDockEdge() },
            onCustomizeDock = { openCustomize() },
            onOpenSettings = { startActivity(Intent(this, MainActivity::class.java)) },
            onProfileTap = { profileBar.cycle() },
            onProfileLongPress = { profileBar.addDialog() },
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        content.addView(topStrip.view, LinearLayout.LayoutParams(MATCH, WRAP))
        topStrip.setProfileInitial(viewModel.uiState.value.activeProfile)   // chữ đầu avatar ban đầu (parity onCreate cũ)

        workspace = WorkspaceView(this).apply {
            mediaProvider = { media.read() }                          // nhạc live (Bitmap ở :app, ngoài state :core)
            onMedia = { handleMedia(it) }
            onSlotTap = { drawerController.open(it) }
            onSlotClear = { clearSlot(it) }
            onSlotSwap = { a, b -> swapSlots(a, b) }
            onAppOpen = { reopenApp(it) }
        }
        windows = LauncherWindows(
            this, workspace, winExec,
            state = { viewModel.uiState.value }, embedding = { embedding }, drawerOpen = { drawerController.isOpen() },
            shell = { shell }, appLauncher = { appLauncher }, dispatcher = { container.windowDispatcher },
            onSlotSwap = { drawerController.open(it) }, onSlotClose = { clearSlot(it) },
        )
        dock = ControlDockView(this).apply { control = container.carControl }

        mainArea = LinearLayout(this)
        DockAreaLayout.apply(mainArea, workspace, dock, viewModel.uiState.value.dock, resources.displayMetrics.density)
        content.addView(mainArea, LinearLayout.LayoutParams(MATCH, 0, 1f).also { it.topMargin = dp(12) })

        rootFrame = FrameLayout(this)
        rootFrame.addView(WallView(this), FrameLayout.LayoutParams(MATCH, MATCH))
        rootFrame.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(rootFrame)

        drawerController = DrawerController(
            this, rootFrame,
            currentWidgets = { (viewModel.uiState.value.slots.getOrNull(it) as? SlotContent.Widget)?.ids ?: emptyList() },
            onClearOverlays = { windows.clearOverlays() },
            onOverlayHeads = { windows.updateOverlayHeads() },
            onPickApp = { idx, pkg -> assignApp(idx, pkg) },
            onPickWidgets = { idx, ids -> assignWidgets(idx, ids) },
        )

        // Thu NGUỒN SỰ THẬT: mọi thay đổi state → render (view-only). repeatOnLifecycle huỷ khi < STARTED.
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }

        // Thu TRẠNG THÁI XE LIVE: poll 2 nhịp (start khi STARTED, stop khi < STARTED) → bơm vào VM (một chiều) →
        // uiState.carStatus đổi → render → widget/chip cập nhật. Off-car mọi field null ⇒ "—".
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.carStatusRepository.start()
                try {
                    container.carStatusRepository.status.collect { viewModel.setCarStatus(it) }
                } finally {
                    container.carStatusRepository.stop()
                }
            }
        }

        // Nối shell dadb (localhost:5555) nền → ShellAppLauncher reflow như xe; dispatcher + ShellTransport + daemon do AppContainer sở hữu.
        val dadb = DadbShell(this)
        val dispatcher = container.windowDispatcher
        windows.seedLocations()
        val seam = dispatcher.launcherSeam()
        winExec.execute {
            if (dadb.probe()) {
                shell = seam; appLauncher = ShellAppLauncher(seam)
                runCatching { seam("appops set com.byd.launcher SYSTEM_ALERT_WINDOW allow") }  // để vẽ dải header nổi lên app freeform
                runOnUiThread {
                    workspace.registerVd = dispatcher::registerLauncherVirtualDisplay        // VD ô thuộc LAUNCHER → ownership cho phép
                    workspace.unregisterVd = dispatcher::unregisterLauncherVirtualDisplay
                    workspace.shell = seam
                    workspace.inputClient = container.inputDaemonClient                       // daemon do AppContainer sở hữu, tiêm vào
                    workspace.render(viewModel.uiState.value.workspace, viewModel.uiState.value.carStatus)   // bật render app lên VirtualDisplay trong ô
                    viewModel.setEmbedded(true)                                               // dadb nối được → nhúng (giữ embedded khớp getter)
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
        workspace.render(state.workspace, state.carStatus)
        if (prev == null || prev.carStatus != state.carStatus) topStrip.refreshChips(state.carStatus)
        if (prev?.preset != state.preset) topStrip.selectPreset(state.preset)
        if (prev?.dock != state.dock) {
            val edgeChanged = prev != null && prev.dock.edge != state.dock.edge
            dock.setConfig(state.dock)
            if (edgeChanged) DockAreaLayout.apply(mainArea, workspace, dock, state.dock, resources.displayMetrics.density)
        }
        if (prev == null || prev.activeProfile != state.activeProfile) topStrip.setProfileInitial(state.activeProfile)
        if (prev != null && (prev.preset != state.preset || prev.dock.edge != state.dock.edge)) windows.reflow()
        shownState = state
        windows.updateOverlayHeads()
    }

    // ── Glue intent theo-ô: intent VM (state+persist, một chiều) + side-effect cửa sổ (registry + windows) ──
    private fun assignApp(index: Int, pkg: String) {
        drawerController.close()
        val prev = viewModel.uiState.value.slots.getOrNull(index) as? SlotContent.App
        viewModel.assignApp(index, pkg)                                       // state+persist → collector: workspace.render
        val d = container.windowDispatcher
        if (prev != null && prev.pkg != pkg) d.remove(prev.pkg)               // ô thay app khác → gỡ app cũ khỏi registry
        d.place(pkg, 0, index)                                                // app mới chiếm ô index trên display 0
        windows.placeApp(pkg, index, fresh = true)
    }

    private fun assignWidgets(index: Int, ids: List<String>) {
        drawerController.close()
        viewModel.assignWidgets(index, ids)   // state+persist → collector: workspace.render
    }

    private fun reopenApp(index: Int) {
        (viewModel.uiState.value.slots.getOrNull(index) as? SlotContent.App)?.let { windows.placeApp(it.pkg, index) }
    }

    private fun clearSlot(index: Int) {
        val cur = viewModel.uiState.value
        (cur.slots.getOrNull(index) as? SlotContent.App)?.let { app ->
            windows.closeApp(app.pkg)
            container.windowDispatcher.remove(app.pkg)   // ô đóng → gỡ vị trí (bất biến MỘT-VỊ-TRÍ)
        }
        viewModel.clearSlot(index)   // state+persist → collector: workspace.render + updateOverlayHeads
    }

    /** Kéo-thả đổi chỗ 2 ô (widget/app). */
    private fun swapSlots(a: Int, b: Int) {
        val cur = viewModel.uiState.value
        if (a !in cur.slots.indices || b !in cur.slots.indices) return
        viewModel.swapSlots(a, b)   // state+persist → collector: workspace.render
        val ns = viewModel.uiState.value
        val d = container.windowDispatcher   // 2 ô đổi chỗ → cập nhật lại index vị trí của app (nếu có) ở mỗi ô
        (ns.slots.getOrNull(a) as? SlotContent.App)?.let { d.place(it.pkg, 0, a) }
        (ns.slots.getOrNull(b) as? SlotContent.App)?.let { d.place(it.pkg, 0, b) }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        when {
            customizePanel != null -> closeCustomize()
            drawerController.isOpen() -> drawerController.close()
        }
    }

    // ── Màn Tuỳ biến (chọn nút cho thanh điều khiển) — overlay trên rootFrame, một chiều qua VM ──
    private fun openCustomize() {
        if (customizePanel != null) return
        val panel = CustomizePanel(
            this,
            enabledIds = viewModel.uiState.value.dock.enabled,
            onToggle = { id, on -> viewModel.toggleDock(id, on) },   // state+persist → collector: dock.setConfig
            onClose = { closeCustomize() },
        )
        customizePanel = panel
        rootFrame.addView(panel, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun closeCustomize() {
        customizePanel?.let { rootFrame.removeView(it) }
        customizePanel = null
    }

    /** Transport nhạc từ widget w_media → [MediaBridge] (no-op nếu off-car/không quyền). */
    private fun handleMedia(action: String) {
        when (action) {
            "play" -> media.play()
            "pause" -> media.pause()
            "next" -> media.next()
            "prev" -> media.prev()
        }
    }

    override fun onStart() { super.onStart(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START) }

    override fun onResume() {
        super.onResume(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        goImmersive(); topStrip.updateClock(); handler.post(tick); workspace.postDelayed({ windows.updateOverlayHeads() }, 600)
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
        if (isFinishing) vmStore.clear()
        winExec.shutdownNow(); windows.clearOverlays()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
