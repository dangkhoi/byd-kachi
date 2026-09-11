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
import android.widget.Toast
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
import com.byd.clusternav.Prefs
import com.byd.clusternav.comfort.RecircApplier
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
    private val appOpener by lazy { AppOpener(this) }      // U3: mở app toàn màn (đường "mở app kiểu thường")
    private var customizePanel: CustomizePanel? = null     // overlay Tuỳ biến thanh điều khiển
    /**
     * Lựa chọn ĐƠN VỊ của người dùng (R11–R13) — đọc MỘT LẦN từ tầng dữ liệu lúc mở màn. CỐ Ý không nằm trong
     * [HomeUiState]: nó chỉ đổi khi người dùng vào chọn, nên đưa vào state là bắt cả HOME so-sánh-lại mỗi nhịp
     * trạng thái xe (2/giây) mà không được gì. Đổi lựa chọn ⇒ gán lại field này rồi gọi `dock.setCarStatus(...)`.
     */
    private var unitPrefs: UnitPrefs = UnitPrefs.DEFAULT
    // Cửa sổ app: dadb (xe+emulator) → ShellAppLauncher (am --windowingMode 5 + am task resize); chưa có dadb → IntentAppLauncher.
    @Volatile private var appLauncher: AppLauncher = IntentAppLauncher(this)
    // @Volatile: GHI trên thread nền `winExec` (nhánh dò dadb) nhưng ĐỌC trên thread CHÍNH (openAppFullscreen —
    // lưới an toàn U3; reflow/placeApp cũng đọc trên main). Không có nó thì main có thể thấy mãi `null` ⇒ đường
    // shell "biến mất" một cách im lặng. Cùng lý do với `appLauncher` ngay trên.
    @Volatile private var shell: ((String) -> String)? = null
    // dadb → app render lên VirtualDisplay trong ô (Dudu) hoặc ROM platform-signed → ActivityView; cả 2 bỏ freeform + overlay header.
    private val embedding get() = shell != null || SlotAppHost.embeddingUsable(this)
    private val winExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var shownState: HomeUiState? = null   // view-side diff cache của collector (KHÔNG phải nguồn sự thật)

    private lateinit var wall: WallView
    // U4: trạng thái trình chiếu (ảnh nào, đổi lần cuối lúc nào). Ảnh đang vẽ giữ riêng để giải phóng ĐÚNG LÚC —
    // giải phóng trước khi View vẽ xong sẽ dùng ảnh đã thu hồi và sập.
    private var slide = SlideshowState()
    private var wallPrefs = WallpaperPrefs.DEFAULT
    private var wallImages: List<String> = emptyList()
    private var wallBitmap: android.graphics.Bitmap? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            topStrip.updateClock()
            stepWallpaper()   // U4: dùng LẠI nhịp có sẵn thay vì dựng thêm một vòng đếm riêng
            handler.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        container = AppContainer.get(this)
        unitPrefs = container.workspaceRepository.unitPrefs()   // đơn vị do người dùng chọn (chung mọi hồ sơ)
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
            onOpenAppList = { drawerController.openAppList() },   // U3: mở app toàn màn (không gắn ô)
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
            state = { viewModel.uiState.value }, embedding = { embedding }, drawerOpen = { drawerController.isOpen() },
            shell = { shell }, appLauncher = { appLauncher }, dispatcher = { container.windowDispatcher },
            onSlotSwap = { drawerController.open(it) }, onSlotClose = { clearSlot(it) },
        )
        dock = ControlDockView(this).apply { control = container.carControl }

        mainArea = LinearLayout(this)
        DockAreaLayout.apply(mainArea, workspace, dock, viewModel.uiState.value.dock, resources.displayMetrics.density)
        content.addView(mainArea, LinearLayout.LayoutParams(MATCH, 0, 1f).also { it.topMargin = dp(12) })

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
                runPreflight(shellUsable = true, sh = seam)
            } else {
                // Không có kênh shell: VẪN kiểm quyền (đọc trạng thái KHÔNG cần shell — ràng buộc C4) để người dùng
                // biết vì sao app không vào được ô, thay vì ngồi đoán.
                runPreflight(shellUsable = false, sh = null)
            }
        }
    }

    /**
     * P8 — vòng kiểm quyền. Chạy trên [winExec] (thread nền) vì phần tự cấp có mở kênh shell.
     *
     * Ba luật: **đủ thì im lặng** · **tự xin lại** cái tự xin được (không hỏi người dùng) · **KHÔNG chặn launcher**
     * dù thiếu gì — đây là màn hình chính của xe.
     */
    private fun runPreflight(shellUsable: Boolean, sh: ((String) -> String)?) {
        val before = PermissionPreflight.check(this, shellUsable)
        Log.i("Preflight", before.logLine())

        // Tự cấp: chỉ khi CÓ kênh shell và thật sự đang thiếu (đọc thì không cần shell, cấp thì cần).
        if (sh != null && before.selfFixable.isNotEmpty()) {
            runCatching { PermissionPreflight.selfGrant(before, sh) }
            // Trợ năng phải ĐỌC-SỬA-GHI (append, không ghi đè — ghi đè sẽ tắt trợ năng của app khác).
            if (before.selfFixable.any { it.id == LauncherRequirements.ACCESSIBILITY.id }) {
                runCatching {
                    val cur = sh(PermissionPreflight.READ_ACCESSIBILITY_CMD).trim().takeIf { it != "null" }
                    val flagOn = sh(PermissionPreflight.READ_ACCESSIBILITY_FLAG_CMD).trim() == "1"
                    PermissionPreflight.accessibilityGrantCommands(cur, flagOn).forEach { sh(it) }
                }
            }
            Log.i("Preflight", "sau khi tự cấp: " + PermissionPreflight.check(this, shellUsable).logLine())
        }

        // Chỉ NÓI khi thiếu thứ làm mất TÍNH NĂNG LÕI (app vào ô). Thiếu mục nhỏ mà báo mỗi lần mở là nhiễu —
        // đúng thứ việc này đi dọn. Danh sách đầy đủ nằm trong bảng Tuỳ biến.
        val after = PermissionPreflight.check(this, shellUsable)
        val core = after.missingCore
        if (core.isNotEmpty()) {
            val msg = core.joinToString(" · ") { "${it.label}: ${it.losesWhatIfMissing}" }
            runOnUiThread { runCatching { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() } }
        }
    }

    /**
     * Áp [state] lên VIEW (duy nhất một chỗ, do collector gọi) — chỉ đọc-vẽ, KHÔNG đổi state. Diff so với [shownState]
     * để chỉ làm việc khi phần liên quan đổi. Side-effect cửa sổ theo-ô ở handler; ở đây chỉ reflow khi preset/viền đổi.
     */
    private fun render(state: HomeUiState) {
        val prev = shownState
        workspace.render(state.workspace, state.carStatus)
        if (prev == null || prev.carStatus != state.carStatus) {
            topStrip.refreshChips(state.carStatus, unitPrefs)
            // RW0/Đ4: thanh nút cũng cần trạng thái xe để ô ĐỌC sống được ở đó. CHỈ đổ lại số của ô đọc — KHÔNG
            // dựng lại thanh (C5: dựng lại mỗi nhịp 2/giây sẽ nháy + mất trạng thái ô vừa bấm).
            dock.setCarStatus(state.carStatus, unitPrefs)
            workspace.setUnitPrefs(unitPrefs)   // R11: ô giữa màn cũng theo lựa chọn đơn vị (tự bỏ qua nếu không đổi)
        }
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
        winExec.execute { appOpener.openByShell(pkg, sh) }
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

    // ── U4 · hình nền + trình chiếu ──────────────────────────────────────────────────────────────
    /**
     * Nạp lại lựa chọn + danh sách ảnh rồi vẽ ngay. Gọi lúc mở màn và mỗi khi người dùng đổi lựa chọn.
     *
     * TẮT (mặc định) ⇒ nhả ảnh và để [WallView] vẽ nền gradient như trước ⇒ người không dùng tính năng này
     * **không thấy gì khác**.
     */
    private fun reloadWallpaper() {
        wallPrefs = container.workspaceRepository.wallpaperPrefs()
        // Tạo thư mục ảnh NGAY, kể cả khi tính năng đang tắt. [ĐO] máy ảo 2026-09-11: nếu chỉ tạo lúc bật thì người
        // dùng gặp vòng lặp chết — muốn thấy ảnh phải bật, muốn bật có nghĩa phải bỏ ảnh vào trước, mà thư mục lại
        // chưa tồn tại để mà bỏ vào.
        WallpaperStore.folder(this)
        if (!wallPrefs.enabled) {
            wall.setPhoto(null)
            releaseWallBitmap()
            wallImages = emptyList()
            return
        }
        wallImages = WallpaperStore.images(this)
        slide = SlideshowState()          // đổi lựa chọn ⇒ bắt đầu lại từ ảnh đầu
        stepWallpaper(force = true)
    }

    /**
     * Một nhịp trình chiếu. Chạy trên nhịp 10 giây có sẵn của thanh trên — cố ý KHÔNG dựng thêm vòng đếm riêng
     * (thêm một vòng nữa là thêm một thứ phải nhớ dừng lúc huỷ màn).
     *
     * Nhịp 10 giây với chu kỳ ngắn nhất 15 giây ⇒ sai số tối đa 10 giây. Với trình chiếu ảnh thì đó là **không ai
     * thấy**; đổi lấy việc không có vòng đếm thứ hai là đáng.
     */
    private fun stepWallpaper(force: Boolean = false) {
        if (!wallPrefs.enabled) return
        if (wallImages.isEmpty()) {
            // Bật mà chưa có ảnh: KHÔNG im lặng — nói chỗ bỏ ảnh vào, vì người dùng không có cách nào tự đoán.
            if (force) {
                Log.i("Wallpaper", "bật nhưng chưa có ảnh; bỏ ảnh vào: ${WallpaperStore.folderHint(this)}")
                runCatching {
                    Toast.makeText(this, "Chưa có ảnh. Bỏ ảnh vào:\n${WallpaperStore.folderHint(this)}", Toast.LENGTH_LONG).show()
                }
            }
            wall.setPhoto(null)
            return
        }
        val before = slide
        slide = Slideshow.next(slide, wallImages.size, System.currentTimeMillis(), wallPrefs.intervalSec)
        if (!force && slide.index == before.index && wall.hasPhoto()) return   // chưa tới hạn ⇒ không nạp lại
        val path = Slideshow.pick(wallImages, slide.index) ?: return
        val next = WallpaperStore.loadScaled(path, wall.width.coerceAtLeast(1), wall.height.coerceAtLeast(1))
        if (next == null) {
            Log.w("Wallpaper", "ảnh không giải mã được, giữ nền hiện tại: $path")
            return
        }
        val old = wallBitmap
        wallBitmap = next
        wall.setPhoto(next, wallPrefs.fit, wallPrefs.dim)
        // Nhả ảnh CŨ sau khi đã đưa ảnh mới vào View — nhả trước thì lần vẽ kế tiếp dùng ảnh đã thu hồi và sập.
        old?.recycle()
    }

    private fun releaseWallBitmap() {
        wallBitmap?.recycle()
        wallBitmap = null
    }

    // ── Màn Tuỳ biến (chọn nút cho thanh điều khiển) — overlay trên rootFrame, một chiều qua VM ──
    private fun openCustomize() {
        if (customizePanel != null) return
        val panel = CustomizePanel(
            this,
            enabledIds = viewModel.uiState.value.dock.enabled,
            onToggle = { id, on -> viewModel.toggleDock(id, on) },   // state+persist → collector: dock.setConfig
            onClose = { closeCustomize() },
            // W3: ô tick tự lấy gió trong. Bật ⇒ áp NGAY (không chờ lần nổ máy sau); tắt ⇒ CHỈ đặt lại cờ, KHÔNG
            // tắt chế độ đang bật trên xe (người dùng có thể đang muốn dùng, chỉ là không muốn tự bật nữa).
            recircOnStart = Prefs.recircOnStartEnabled(this),
            onRecircOnStart = { on ->
                Prefs.setRecircOnStartEnabled(this, on)
                if (on) RecircApplier.applyNowAsync(this)
            },
            // R11: đổi đơn vị ⇒ lưu bền + áp lại NGAY cho cả thanh nút và ô giữa màn (không cần mở lại app).
            unitPrefs = unitPrefs,
            // P8: bảng Tuỳ biến là chỗ xem ĐỦ bức tranh quyền (thông báo chỉ nói mục ảnh hưởng tính năng lõi).
            permissions = PermissionPreflight.check(this, shellUsable = shell != null),
            // U4: nói CHỖ bỏ ảnh vào — người dùng không có cách nào tự đoán, và màn chọn tệp của hệ thống bị khoá trên xe.
            wallpaper = wallPrefs,
            wallpaperFolderHint = WallpaperStore.folderHint(this),
            onWallpaper = { p ->
                container.workspaceRepository.setWallpaperPrefs(p)
                reloadWallpaper()
            },
            onUnitPrefs = { prefs ->
                unitPrefs = prefs
                container.workspaceRepository.setUnitPrefs(prefs)
                dock.setCarStatus(viewModel.uiState.value.carStatus, prefs)
                workspace.setUnitPrefs(prefs)
                topStrip.refreshChips(viewModel.uiState.value.carStatus, prefs)
            },
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
        goImmersive(); topStrip.updateClock(); reloadWallpaper(); handler.post(tick); workspace.postDelayed({ windows.updateOverlayHeads() }, 600)
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
        // Ngăn kéo có thể được gắn như CỬA SỔ RIÊNG (TYPE_APPLICATION_OVERLAY qua WindowManager) → nó KHÔNG chết
        // cùng activity. Không đóng ở đây thì cửa sổ đó sống tiếp (rò rỉ view + giữ activity), và một cái chạm vào
        // nó sẽ chạy vào `winExec` ĐÃ shutdown (RejectedExecutionException) hoặc mở activity từ activity đã huỷ.
        drawerController.close()
        releaseWallBitmap()   // U4: nhả ảnh nền, không để giữ bộ nhớ sau khi màn đã huỷ
        winExec.shutdownNow(); windows.clearOverlays()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
