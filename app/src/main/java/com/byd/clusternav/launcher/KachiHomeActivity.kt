package com.byd.clusternav.launcher

import android.util.Log
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
import com.byd.clusternav.launcher.KachiSpace as Sp

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

    /** Hai bảng phủ toàn màn (màn Cài đặt + bảng vẽ bố cục) — xem [HomePanels]. */
    private val panels: HomePanels by lazy {
        HomePanels(
            activity = this,
            rootFrame = rootFrame,
            state = { viewModel.uiState.value },
            onToggleDock = { id, on -> viewModel.toggleDock(id, on) },
            onApplyLayout = { l -> applyCustomLayout(l) },
            onPreset = { p -> selectPreset(p) },          // CÙNG đường với 5 nút bố cục ở thanh trên (§4.5)
            onDockEdge = { e -> viewModel.setDockEdge(e) },
            onTopStrip = { id, on -> viewModel.toggleTopStrip(id, on) },
            onWallpaper = { p ->
                viewModel.setWallpaperPrefs(p); wallpaper.reload()   // state + lưu bền; reload đọc lại từ state
            },
            onUnitPrefs = { prefs ->
                viewModel.setUnitPrefs(prefs)      // state + lưu bền trong MỘT lượt
                dock.setCarStatus(viewModel.uiState.value.carStatus, prefs)
                workspace.setUnitPrefs(prefs)
                topStrip.refreshChips(viewModel.uiState.value.carStatus, prefs, viewModel.uiState.value.topStrip)
            },
            onThemeMode = { m -> viewModel.setThemeMode(m) },   // T1 — intent có sẵn từ S1; đọc-để-vẽ ở [ThemeHost]
            onLangMode = { m -> viewModel.setLangMode(m) },     // U5·T3 — đọc-để-vẽ ở [LangHost.wrap]
            onAutostart = { on -> viewModel.setAutostart(on) },
            onSwitchProfile = { name -> viewModel.switchProfile(name) },
            onAddProfile = { profileBar.addDialog() },    // dùng LẠI hộp thoại có sẵn, không dựng bản thứ hai
            onDeleteProfile = { name -> viewModel.deleteProfile(name) },
            onOpenClusterNav = { startActivity(Intent(this, MainActivity::class.java)) },
            scenes = sceneController,                     // P7/P6 — lưu/gọi/nổ-máy/đổi-tên/xoá cảnh
            shellUsable = { shell != null },
            goImmersive = { goImmersive() },
        )
    }

    /**
     * Hình nền + trình chiếu (U4) — tách khỏi Activity để Activity còn là composition-root (xem
     * [WallpaperController]). Lười dựng: cần `wall` đã có mặt.
     */
    private val wallpaper: WallpaperController by lazy {
        WallpaperController(
            ctx = this,
            wall = wall,
            prefs = { viewModel.uiState.value.wallpaper },
            submitIo = { block -> submitIo(block) },
            onUi = { block -> runOnUiThread(block) },
            gone = { destroyed || isFinishing || isDestroyed },
            onPhotoSource = { paths, sec -> workspace.setPhotoSource(paths, sec) },
        )
    }
    private lateinit var drawerController: DrawerController
    private lateinit var profileBar: ProfileBar
    private val sceneController by lazy { SceneController(this, viewModel) }   // P7/P6 (xem [SceneActions])
    private lateinit var mainArea: LinearLayout
    private lateinit var rootFrame: FrameLayout
    private val media by lazy { MediaBridge(this) }        // đọc nhạc live cho w_media + transport
    /** T4 — chủ DUY NHẤT của widget Android bên thứ ba (host + id + bind-grant). Xem `AppWidgetSlotHost`. */
    private val appWidgets by lazy { AppWidgetSlotHost(this, { shell }, { submitBg(it) }, { drawerController.say(it) }) }
    private val appOpener by lazy { AppOpener(this) }      // U3: mở app toàn màn (đường "mở app kiểu thường")
    /**
     * Lựa chọn đang hiệu lực — **đọc từ nguồn sự thật duy nhất** ([HomeViewModel.uiState]), KHÔNG giữ bản sao.
     *
     * ⚠ [SOÁT P1-1 kiến trúc] Ba nhóm này (đơn vị · hình nền · bố cục tự vẽ) trước đây là field riêng của màn chính
     * (và của cả `WorkspaceView`/`ControlDockView`/bảng "Tuỳ biến" cũ), đồng bộ bằng lời gọi tay. Lý do cũ ghi trong
     * KDoc là "đưa vào state thì mỗi nhịp trạng thái xe phải so lại" — nhưng `data class` so bằng tham chiếu cho
     * field không đổi nên phép so đó gần như miễn phí, còn giá của việc giữ nhiều bản sao thì đã trả bằng một lỗi
     * thật (xoá bố cục mà màn hình vẫn hiện 6 khung).
     */
    private val unitPrefs: UnitPrefs get() = viewModel.uiState.value.unitPrefs
    private val customLayout: GridLayout? get() = viewModel.uiState.value.customLayout
    // Cửa sổ app: dadb (xe+emulator) → ShellAppLauncher (am --windowingMode 5 + am task resize); chưa có dadb → IntentAppLauncher.
    @Volatile private var appLauncher: AppLauncher = IntentAppLauncher(this)
    // @Volatile (cùng lý do `appLauncher` ngay trên): GHI ở thread nền `winExec` (dò dadb), ĐỌC ở thread CHÍNH
    // (openAppFullscreen · reflow · placeApp). Không có nó thì main có thể thấy mãi `null` ⇒ đường shell im lặng mất.
    @Volatile private var shell: ((String) -> String)? = null
    // dadb → app render lên VirtualDisplay trong ô (Dudu) hoặc ROM platform-signed → ActivityView; cả 2 bỏ freeform + overlay header.
    private val embedding get() = shell != null || SlotAppHost.embeddingUsable(this)
    private val winExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    /** Thread nền RIÊNG cho I/O ảnh (xem submitIo) — không để I/O ảnh chặn lệnh cửa sổ và ngược lại. */
    private val ioExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var shownState: HomeUiState? = null   // view-side diff cache của collector (KHÔNG phải nguồn sự thật)

    private lateinit var wall: WallView
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            topStrip.updateClock()
            wallpaper.step()   // U4: dùng LẠI nhịp có sẵn thay vì dựng thêm một vòng đếm riêng
            handler.postDelayed(this, 10_000)
        }
    }

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(LangHost.wrap(base))

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
        ThemeHost.sync(viewModel.uiState.value)   // T1 — bảng màu phải có TRƯỚC khi dựng view (xem [ThemeHost])
        topStrip = KachiTopStrip(
            this,
            onSelectPreset = { selectPreset(it) },
            // ⚠ KHÔNG thêm cổng cấu hình nào nữa vào đây: thanh trên chỉ được chạm `preset` + `active_profile`
            // (§4.5 / [SettingsCatalog.TOP_STRIP_ALLOWED_KEYS]). Pill "Thanh" (xoay vòng viền thanh nút, ghi bền
            // `dock_edge`) đã BỎ — Cài đặt → Màn hình chính đặt THẲNG từng viền.
            onOpenSettings = { panels.openSettings() },   // S1: MỘT cửa vào cấu hình (gộp pill "Tuỳ biến" cũ)
            onProfileTap = { profileBar.cycle() },
            onOpenAppList = { drawerController.openAppList() },   // U3: mở app toàn màn (không gắn ô)
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Sp.L), dp(Sp.XS), dp(Sp.L), dp(Sp.M))
        }
        content.addView(topStrip.view, LinearLayout.LayoutParams(MATCH, WRAP))
        topStrip.setProfileInitial(viewModel.uiState.value.activeProfile)   // chữ đầu avatar ban đầu (parity onCreate cũ)

        workspace = WorkspaceView(this).apply {
            mediaProvider = { media.read() }                          // nhạc live (Bitmap ở :app, ngoài state :core)
            onMedia = { media.handle(it) }
            control = container.carControl                             // RW0: ô giữa màn đặt được cả HÀNH ĐỘNG (R2)
            // Đơn vị đặt TRƯỚC lượt render đầu: nếu để lượt render đầu chạy với mặc định rồi mới đặt, thì người dùng
            // đã chọn (vd psi) sẽ phải chịu thêm một lượt dựng lại ô widget mỗi lần mở HOME mà không được gì.
            setUnitPrefs(unitPrefs)
            onSlotTap = { drawerController.open(it) }
            onSlotClear = { clearSlot(it) }
            onSlotSwap = { a, b -> swapSlots(a, b) }
            onAppOpen = { reopenApp(it) }
        }
        windows = LauncherWindows(
            this, workspace, winExec,
            state = { viewModel.uiState.value },
            custom = { customLayout }, embedding = { embedding }, drawerOpen = { drawerController.isOpen() },
            shell = { shell }, appLauncher = { appLauncher }, dispatcher = { container.windowDispatcher },
            onSlotSwap = { drawerController.open(it) }, onSlotClose = { clearSlot(it) },
        )
        dock = ControlDockView(this).apply { control = container.carControl }

        mainArea = LinearLayout(this)
        DockAreaLayout.apply(mainArea, workspace, dock, viewModel.uiState.value.dock, resources.displayMetrics.density)
        content.addView(mainArea, LinearLayout.LayoutParams(MATCH, 0, 1f).also { it.topMargin = dp(Sp.M) })

        rootFrame = FrameLayout(this)
        wall = WallView(this)
        rootFrame.addView(wall, FrameLayout.LayoutParams(MATCH, MATCH))
        rootFrame.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(rootFrame)

        drawerController = DrawerController(
            this, rootFrame,
            currentWidgets = { (viewModel.uiState.value.slots.getOrNull(it) as? SlotContent.Widget)?.ids ?: emptyList() },
            onClearOverlays = { windows.clearOverlays() },
            onOverlayHeads = { windows.updateOverlayHeads() },
            onPickApp = { idx, pkg -> assignApp(idx, pkg) },
            onPickWidgets = { idx, ids -> assignWidgets(idx, ids) },
            onOpenApp = { pkg -> openAppFullscreen(pkg) },                        // U3
            recentApps = { container.workspaceRepository.recentApps() },
            appWidgetPicks = { idx ->        // T4: ràng buộc xong mới ghi vào ô; thất bại ⇒ bảng tự nói, ô không đổi
                appWidgets.picks { i -> appWidgets.bind(i) { c -> c?.let { drawerController.close(); viewModel.assignAppWidget(idx, it) } } }
            },
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
        // P9: nạp bố cục tự vẽ TRƯỚC khi sắp cửa sổ, để lần dựng đầu đã đúng khung (không nháy từ bố cục sẵn sang).
        // Bố cục tự vẽ đã được nạp vào state ở `repository.load()` ⇒ ở đây chỉ ĐẨY xuống view.
        workspace.appWidgetView = { appWidgets.createView(it) }
        workspace.appWidgetName = { appWidgets.deadLabel(it) }
        appWidgets.sweep(viewModel.uiState.value)   // SAU load(): xem KDoc sweep (thứ tự là bắt buộc)
        workspace.setCustomLayout(customLayout)
        windows.seedLocations()
        val seam = dispatcher.launcherSeam()
        submitBg {
            if (dadb.probe()) {
                shell = seam; appLauncher = ShellAppLauncher(seam)
                runCatching { seam("appops set com.byd.launcher SYSTEM_ALERT_WINDOW allow") }  // để vẽ dải header nổi lên app freeform
                runOnUiThread {
                    // GẮN NGUYÊN KHỐI (P-bug2): 1 lời gọi mang đủ kênh shell + kênh chạm + đăng ký/gỡ màn ảo, rồi
                    // WorkspaceView tự dựng lại các ô App MỘT LẦN để gắn bộ chiếu. Trước đây đoạn này gán rời 4
                    // field xong gọi `workspace.render(...)`, nhưng render so theo NỘI DUNG nên ô App "không đổi"
                    // ⇒ không dựng lại ⇒ app trong ô chỉ hiện sau khi người dùng đổi bố cục.
                    workspace.applyEmbedSeam(
                        shell = seam,
                        inputClient = container.inputDaemonClient,   // daemon do AppContainer sở hữu, tiêm vào
                        registerVd = dispatcher::registerLauncherVirtualDisplay,   // VD ô thuộc LAUNCHER → ownership cho phép
                        unregisterVd = dispatcher::unregisterLauncherVirtualDisplay,
                        state = viewModel.uiState.value.workspace,
                        status = viewModel.uiState.value.carStatus,
                    )
                    viewModel.setEmbedded(true)                                               // dadb nối được → nhúng (giữ embedded khớp getter)
                }
                PermissionPreflight.runAndReport(this, shellUsable = true, sh = seam)
            } else {
                // Không có kênh shell: VẪN kiểm quyền (đọc trạng thái KHÔNG cần shell — ràng buộc C4) để người dùng
                // biết vì sao app không vào được ô, thay vì ngồi đoán.
                PermissionPreflight.runAndReport(this, shellUsable = false, sh = null)
            }
        }
    }

    /**
     * Áp [state] lên VIEW (duy nhất một chỗ, do collector gọi) — chỉ đọc-vẽ, KHÔNG đổi state. Diff so với [shownState]
     * để chỉ làm việc khi phần liên quan đổi. Side-effect cửa sổ theo-ô ở handler; ở đây chỉ reflow khi preset/viền đổi.
     */
    private fun render(state: HomeUiState) {
        val prev = shownState
        // T1/T3 — bảng màu HOẶC ngôn ngữ đổi ⇒ dựng lại màn; `or` KHÔNG ngắn mạch vì `sync` là chỗ ÁP bảng màu.
        if ((ThemeHost.sync(state) or LangHost.changed(prev, state)) && prev != null) { recreate(); return }
        // T4: thu hồi id ở ĐÚNG chỗ diff này ⇒ mọi đường đổi đều qua đây. CẢ state, vì "còn dùng" tính cả sổ cảnh.
        prev?.let { appWidgets.reclaim(it, state) }
        workspace.render(state.workspace, state.carStatus)
        // ⚠ xét CẢ `topStrip`: thiếu nó thì đổi danh sách chip mà màn hình không đổi gì (off-car trạng thái xe gần như không đổi).
        if (prev == null || prev.carStatus != state.carStatus || prev.topStrip != state.topStrip) {
            topStrip.refreshChips(state.carStatus, unitPrefs, state.topStrip)
            // RW0/Đ4: thanh nút cũng cần trạng thái xe để ô ĐỌC sống được ở đó. CHỈ đổ lại số của ô đọc — KHÔNG
            // dựng lại thanh (C5: dựng lại mỗi nhịp 1/giây sẽ nháy + mất trạng thái ô vừa bấm).
            dock.setCarStatus(state.carStatus, unitPrefs)
            workspace.setUnitPrefs(unitPrefs)   // R11: ô giữa màn cũng theo lựa chọn đơn vị (tự bỏ qua nếu không đổi)
        }
        if (prev?.preset != state.preset) topStrip.selectPreset(state.preset)
        if (prev?.dock != state.dock) {
            val edgeChanged = prev != null && prev.dock.edge != state.dock.edge
            dock.setConfig(state.dock)
            if (edgeChanged) DockAreaLayout.apply(mainArea, workspace, dock, state.dock, resources.displayMetrics.density)
        }
        // Đổi/thêm/xoá hồ sơ nạp lại TOÀN BỘ state ⇒ trang đã dựng của màn Cài đặt (nếu đang mở) trở nên cũ. Đi theo
        // đường một chiều: state đổi → render → bảng dựng lại. ⚠ phải xét CẢ `profiles`: xoá một hồ sơ KHÔNG phải hồ
        // sơ đang dùng thì `activeProfile` không đổi, và nếu chỉ xét nó thì danh sách trên màn vẫn còn hồ sơ vừa xoá.
        if (prev == null || prev.activeProfile != state.activeProfile || prev.profiles != state.profiles) {
            topStrip.setProfileInitial(state.activeProfile)
            panels.invalidateSettings()
        }
        // [SOÁT P1-1 kiến trúc] Bố cục tự vẽ đẩy xuống view ở ĐÚNG MỘT CHỖ: theo state, khi state đổi. Trước đây chỗ
        // này tự đọc lại repository khi đổi hồ sơ (đường đọc bền nằm trong tầng UI) còn việc đẩy xuống view thì ở hàm
        // khác ⇒ hai đường song song. Nay `load()`/`switchProfile()` đã nạp bố cục vào state nên ca đó tự đúng.
        // P7/P6: sổ cảnh đổi ⇒ danh sách cảnh phải vẽ lại (trang Cài đặt được nhớ nên không tự dựng lại; thiếu dòng
        // này thì lưu/xoá một cảnh là "màn hình không đổi gì" — họ lỗi của nút bố cục sẵn ở P9).
        if (prev != null && prev.scenes != state.scenes) panels.invalidateSettings()
        if (prev?.customLayout != state.customLayout) {
            workspace.setCustomLayout(state.customLayout)
            windows.reflow()
        }
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

    /**
     * U3 — mở [pkg] **toàn màn** (đường "mở app kiểu thường"): KHÔNG ghi vào ô, KHÔNG đổi bố cục đã lưu, KHÔNG ghi
     * sổ vị trí ô. Bấm HOME là về Kachi (Kachi là HOME).
     *
     * Thứ tự do SỐ ĐO quyết định (xem bảng ở [AppOpener]): thử **đường API** trên thread chính trước (đo được là
     * tốt bằng-hoặc-hơn); chỉ khi nó thất bại mới dùng **đường shell** trên thread nền (dadb chặn).
     * Ghi nhận "gần đây" trước để lần mở ngăn kéo sau đã thấy.
     */
    private fun openAppFullscreen(pkg: String) {
        drawerController.close()
        runCatching { container.workspaceRepository.touchRecentApp(pkg) }
        if (appOpener.openByIntent(pkg)) return
        val sh = shell ?: return
        submitBg { appOpener.openByShell(pkg, sh) }
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
        // [SOÁT P3] Bảng vẽ bố cục từng bị bỏ sót ở đây: mở nó ra rồi bấm Back là **không có gì xảy ra** (Back của
        // HOME vốn không làm gì), người dùng tưởng bảng bị treo. Thứ tự: lớp phủ trên cùng đóng trước.
        when {
            panels.layoutOpen() -> panels.closeLayoutEditor()
            panels.settingsOpen() -> panels.closeSettings()
            drawerController.isOpen() -> drawerController.close()
        }
    }

    /**
     * Chọn một bố cục sẵn — **đường DUY NHẤT**, dùng cho cả 5 nút ở thanh trên lẫn dãy chip trong màn Cài đặt (§4.5).
     *
     * [ĐO] P9: bấm bố cục sẵn trong khi đang dùng bố cục tự vẽ thì trước đây **màn hình không đổi gì** (bố cục tự vẽ
     * vẫn thắng) nhưng vẫn **dựng lại TOÀN BỘ ô** — người dùng tưởng nút hỏng, còn app trong ô thì bị nhả/gắn vô ích.
     * Hành động tường minh của người dùng phải có tác dụng ⇒ chọn bố cục sẵn = BỎ bố cục tự vẽ. Đây cũng là đường quay
     * về bố cục sẵn mà không phải mở bảng vẽ.
     *
     * Phải đi qua [applyCustomLayout]: [ĐO] xoá riêng biến ở đây thì khung vẽ vẫn giữ BẢN SAO của nó ⇒ cấu hình đã xoá
     * mà màn hình vẫn hiện bố cục tự vẽ. Một đường duy nhất, có test canh.
     */
    private fun selectPreset(preset: LayoutPreset) {
        if (customLayout != null) applyCustomLayout(null)
        viewModel.setPreset(preset)
    }

    /**
     * Áp bố cục tự vẽ = **ghi vào nguồn sự thật, hết**. Việc đẩy xuống màn hình + sắp lại cửa sổ app do `render()`
     * làm khi state đổi (một chiều). Trước đây hàm này tự gán field riêng + tự ghi bền + tự đẩy xuống view, tức
     * ba việc ở một chỗ và không ai bảo đảm ba việc đó thấy cùng một giá trị.
     */
    private fun applyCustomLayout(layout: GridLayout?) = viewModel.setCustomLayout(layout)

    override fun onStart() { super.onStart(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START); appWidgets.startListening() }

    override fun onResume() {
        super.onResume(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        goImmersive(); topStrip.updateClock(); wallpaper.reload(); handler.post(tick)
        // [SOÁT P2-4] Runnable CÓ TÊN để `onDestroy` gỡ được. Trước đây là lambda vô danh nên không có cách nào
        // huỷ, mà nó lại dựng cửa sổ overlay ⇒ chạy sau khi màn chết là giữ view + giữ activity.
        workspace.removeCallbacks(overlayHeadsKick)
        workspace.postDelayed(overlayHeadsKick, 600)
    }

    /** Dựng dải header nổi sau khi cây view đã có kích thước thật (mở màn xong). */
    private val overlayHeadsKick = Runnable { if (!destroyed) windows.updateOverlayHeads() }

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

    override fun onStop() { super.onStop(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP); appWidgets.stopListening() }

    override fun onDestroy() {
        super.onDestroy(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (isFinishing) vmStore.clear()
        // [SOÁT P2-4] Thứ tự QUAN TRỌNG: đánh dấu đã huỷ + gỡ mọi lượt đã hẹn TRƯỚC khi tắt thread nền. Làm ngược
        // lại thì một lượt đã hẹn có thể chen vào giữa và nộp việc cho executor vừa tắt (RejectedExecutionException,
        // không ai bắt) hoặc dựng cửa sổ overlay bằng WindowManager của activity đã chết.
        destroyed = true
        workspace.removeCallbacks(overlayHeadsKick)
        handler.removeCallbacksAndMessages(null)
        windows.cancelPending()
        // Ngăn kéo có thể được gắn như CỬA SỔ RIÊNG (TYPE_APPLICATION_OVERLAY qua WindowManager) → nó KHÔNG chết
        // cùng activity. Không đóng ở đây thì cửa sổ đó sống tiếp (rò rỉ view + giữ activity), và một cái chạm vào
        // nó sẽ chạy vào `winExec` ĐÃ shutdown (RejectedExecutionException) hoặc mở activity từ activity đã huỷ.
        drawerController.close()
        // [SOÁT S1 · P3] `HomePanels.closeAll()` tự nhận là "gọi lúc huỷ màn (lớp phủ giữ view là giữ activity)"
        // nhưng [ĐO] nó KHÔNG có chỗ gọi nào — mã chết + một câu KDoc nói sai. Nối vào đây: màn Cài đặt giữ 7 trang
        // đã dựng (trang "Màn hình chính" một mình là 187 ô) nên nhả sớm là việc đúng, và từ nay câu KDoc thành thật.
        panels.closeAll()
        wallpaper.release()   // U4: nhả ảnh nền, không để giữ bộ nhớ sau khi màn đã huỷ
        winExec.shutdownNow(); ioExec.shutdownNow(); windows.clearOverlays()
    }

    /** Màn đã huỷ ⇒ mọi lượt đã hẹn / callback về muộn phải im. */
    @Volatile private var destroyed = false

    /**
     * MỘT cửa duy nhất để đẩy việc xuống thread nền của màn chính.
     *
     * [SOÁT P2-4] Trước đây 4 chỗ gọi thẳng `winExec.execute`; sau `onDestroy` (đã `shutdownNow`) mỗi chỗ đó là một
     * `RejectedExecutionException` không ai bắt. Gom về đây để chỗ gọi không phải nhớ, và để chỉ có MỘT nơi biết
     * luật "đã huỷ thì thôi".
     */
    private fun submitBg(block: () -> Unit): Boolean = submitOn(winExec, block)

    /**
     * Việc I/O ẢNH (quét thư mục, giải mã) — thread nền **RIÊNG**, không dùng chung với lệnh cửa sổ.
     *
     * [SOÁT P2-7] `winExec` còn chạy lệnh dadb **chặn tới ~3 giây** (poll khi đặt app vào ô). Trộn I/O ảnh vào đó là
     * hai việc chờ nhau: đặt app vào ô phải đợi lượt giải mã ảnh xong, và ngược lại ảnh nền đổi trễ vì đang đặt app.
     */
    private fun submitIo(block: () -> Unit): Boolean = submitOn(ioExec, block)

    // ⚠ [SOÁT P3-2] Trả `Boolean` = việc có được NHẬN (vì sao: KDoc `AppWidgetSlotHost.background` + `sweep`).
    private fun submitOn(exec: java.util.concurrent.ExecutorService, block: () -> Unit): Boolean =
        !destroyed && runCatching { exec.execute { if (!destroyed) block() } }
            .onFailure { Log.w("Kachi", "bỏ việc nền vì màn đã huỷ: ${it.javaClass.simpleName}") }
            .isSuccess

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
