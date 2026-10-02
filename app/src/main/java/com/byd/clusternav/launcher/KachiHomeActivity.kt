package com.byd.clusternav.launcher
import android.annotation.SuppressLint
import android.util.Log
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.byd.clusternav.AppContainer
import com.byd.clusternav.DiagStorageCap
import com.byd.clusternav.Prefs
import com.byd.clusternav.launcher.testbridge.attachTestBridge
import com.byd.clusternav.launcher.voice.VoiceModelStore
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
 * [KachiTopStrip] · [DrawerController] · [ProfileChip] · [DockAreaLayout] · [LauncherWindows]. Activity còn: lấy VM +
 * collect → [render] + glue lifecycle + glue intent theo-ô. Tự quản [LifecycleOwner] + [ViewModelStoreOwner] vì kế
 * thừa `android.app.Activity` (không có androidx `ComponentActivity`/`by viewModels()` — thêm sẽ là phụ thuộc mới).
 */
class KachiHomeActivity : Activity(), LifecycleOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val vmStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = vmStore

    private lateinit var container: AppContainer
    internal lateinit var workspace: WorkspaceView
    internal lateinit var dock: ControlDockView
    internal lateinit var viewModel: HomeViewModel
    internal lateinit var windows: LauncherWindows
    internal lateinit var topStrip: KachiTopStrip

    /** Cầu sang cấu hình/hành động của ClusterNav (IA v2 · §4.2) — dựng MỘT lần, xem [clusterNavBridge]. */
    private val bridge: ClusterNavBridge by lazy { clusterNavBridge() }

    /** Hai bảng phủ toàn màn (màn Cài đặt + bảng vẽ bố cục) — khối nối dây ở [homePanels] (trần 500 dòng). */
    internal val panels: HomePanels by lazy {
        homePanels(
            activity = this, rootFrame = rootFrame, viewModel = viewModel, bridge = bridge,
            openDockPicker = { sel, apply -> drawerController.openDockPicker(sel, apply) },
            shortcuts = shortcuts,   // F1 · U2 — trang Cài đặt lối tắt
            trip = trip,   // F2/F3 — chủ chuyến lên xe (TripHub) + trang Cài đặt; dựng cùng `panels` ở onCreate
            onApplyLayout = { l -> applyCustomLayout(l) },
            // S4 · R7 gỡ 5 nút bố cục khỏi thanh trên ⇒ đây là bề mặt DUY NHẤT chọn bố cục sẵn (intent giữ nguyên).
            onPreset = { p -> selectPreset(p) },
            onWallpaperChanged = { p ->
                viewModel.setWallpaperPrefs(p); wallpaper.reload()   // state + lưu bền; reload đọc lại từ state
            },
            onUnitsChanged = { prefs ->
                viewModel.setUnitPrefs(prefs)      // state + lưu bền trong MỘT lượt
                dock.setCarStatus(viewModel.uiState.value.carStatus, prefs)
                workspace.setUnitPrefs(prefs)
                topStrip.refreshChips(viewModel.uiState.value.carStatus, prefs, viewModel.uiState.value.topStrip)
            },
            shellUsable = { shell != null },
            shellAwaiting = { shellGate.awaitingApproval },   // F4 — hàng quyền nói ĐÚNG ai sửa được
            goImmersive = { goImmersive() },
            // V1 · R6 — hai đường mà đường thử lệnh bằng chữ dùng; CÙNG lambda với thanh nút và ngăn kéo.
            openAppList = { drawerController.openAppList() },
            openAppByPackage = { pkg -> appOpener.openByIntent(pkg) },
            assignAppToSlot = { idx, pkg -> slots.placeTemporary(idx, pkg) },   // owner 01/10: giọng nói đặt TẠM
            // Kiểm tra từng nút (owner 2026-09-15): chạy hành động qua cùng adapter điều khiển xe (`actByKind` định
            // tuyến đúng cửa theo kind); đọc datum qua cùng bảng HAL mà widget dùng — không mở đường thứ hai.
            runAction = { id, arg -> container.carControl.actByKind(id, arg) },
            readInfo = { id -> container.telemetryText(id) },
            // Lớp phủ đóng/mở ⇒ nút ⇄ nổi ẩn đi, và nút mic soi lại điều kiện ([KachiTopStrip.refreshVoicePill]:
            // mô hình có thể vừa tải xong / công tắc vừa gạt, ngay trong màn Cài đặt vừa đóng).
            onPanelsChanged = { windows.updateOverlayHeads(); topStrip.refreshVoicePill() },
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
            // P1b: ảnh mờ đổi ⇒ dựng lại nền KÍNH của các thẻ tại chỗ (không recreate); bảng màu chỉ đổi khi người
            // dùng chọn màu nhấn *theo ảnh nền* — khi đó đi đúng đường của nút chủ đề (`render` → recreate).
            onArtChanged = { if (ThemeHost.sync(viewModel.uiState.value)) applyThemeInPlace() else KachiGlass.refresh(rootFrame) },
        )
    }
    private lateinit var drawerController: DrawerController
    /** S4 · R7 — bộ chọn hồ sơ sau cú chạm chip hồ sơ (thay `ProfileBar`: hết xoay vòng, hết hộp thoại tạo thứ hai). */
    private lateinit var profileChip: ProfileChip
    internal lateinit var mainArea: LinearLayout
    internal lateinit var rootFrame: FrameLayout
    private val media by lazy { MediaBridge(this) }        // đọc nhạc live cho w_media + transport
    /** T4 — chủ DUY NHẤT của widget Android bên thứ ba (host + id + bind-grant). Xem `AppWidgetSlotHost`. */
    internal val appWidgets by lazy { AppWidgetSlotHost(this, { shell }, { submitBg(it) }, { drawerController.say(it) }) }
    private val appOpener by lazy { AppOpener(this) }      // U3: mở app toàn màn (đường "mở app kiểu thường")
    internal val cameraSignal by lazy { container.cameraSignal }   // BG-15: MỘT controller cả tiến trình (AppContainer)

    /**
     * Glue intent theo-ô (gắn app/widget · mở · xoá · đổi chỗ) — thân ở [KachiHomeSlots] (trần 500 dòng). Nhận
     * `windows`/`drawerController` qua lambda: chúng `lateinit`, chỉ có sau khi `onCreate` dựng xong.
     */
    private val slots: KachiHomeSlots by lazy {
        KachiHomeSlots(
            viewModel = viewModel,
            container = container,
            windows = { windows },
            drawer = { drawerController },
            appOpener = appOpener,
            shell = { shell },
            submitBg = { block -> submitBg(block) },
            workspace = { workspace }, app = applicationContext,
        )
    }

    /** F2/F3 — chuyến lên xe (chủ của `TripHub` + trang Cài đặt): thân ở [KachiHomeTrip]. */
    private val trip: KachiHomeTrip by lazy { KachiHomeTrip(this, viewModel, { slots }, { workspace }, { drawerController }) { shell } }

    /** F1 — lối tắt ứng dụng (khối thanh nút + widget `w_apps` + trang Cài đặt): thân ở [KachiHomeShortcuts]. */
    internal val shortcuts: KachiHomeShortcuts by lazy {
        KachiHomeShortcuts(this, viewModel, { slots }, { workspace }, { drawerController }) { panels.openSettings(SettingsGroup.BARS) }
    }

    /**
     * V1 pha NGHE — MỘT phiên nghe cho cả ba lối vào; khối nối dây ở [voiceSession] (trần 500 dòng).
     *
     * Giữ chính `Lazy` (không chỉ giá trị) để [onDestroy] hỏi được `isInitialized()`: chạm vào `voice` ở đó khi
     * chưa ai mở phiên nào sẽ **dựng** một phiên ngay lúc màn đang chết — thứ chỉ để rồi vứt đi.
     */
    private val voiceLazy = lazy {
        voiceSession(
            state = { viewModel.uiState.value },
            openAppList = { drawerController.openAppList() },
            openSettings = { panels.openSettings() },
            onSwitchProfile = { name -> viewModel.switchProfile(name) },
            openPermissions = { panels.openSettings(SettingsGroup.SYSTEM) },
            // V1.1 + đính chính owner 01/10: "mở X vào ô n" là đặt TẠM (không ghi slot_n); ngăn kéo vẫn LƯU.
            assignAppToSlot = { idx, pkg -> slots.placeTemporary(idx, pkg) },
            // L7 — CÙNG đường mà chip bố cục ở Cài đặt dùng (nó còn bỏ bố cục tự vẽ trước, xem `selectPreset`).
            onLayout = { preset -> selectPreset(preset); true },
        )
    }
    private val voice: com.byd.clusternav.launcher.voice.VoiceSession by voiceLazy

    /**
     * Lựa chọn đang hiệu lực — **đọc từ nguồn sự thật duy nhất** ([HomeViewModel.uiState]), KHÔNG giữ bản sao.
     *
     * ⚠ [SOÁT P1-1 kiến trúc] Ba nhóm này (đơn vị · hình nền · bố cục tự vẽ) trước đây là field riêng của màn chính
     * (và của cả `WorkspaceView`/`ControlDockView`/bảng "Tuỳ biến" cũ), đồng bộ bằng lời gọi tay. Lý do cũ ghi trong
     * KDoc là "đưa vào state thì mỗi nhịp trạng thái xe phải so lại" — nhưng `data class` so bằng tham chiếu cho
     * field không đổi nên phép so đó gần như miễn phí, còn giá của việc giữ nhiều bản sao thì đã trả bằng một lỗi
     * thật (xoá bố cục mà màn hình vẫn hiện 6 khung).
     */
    internal val unitPrefs: UnitPrefs get() = viewModel.uiState.value.unitPrefs
    internal val customLayout: GridLayout? get() = viewModel.uiState.value.customLayout
    // Cửa sổ app: dadb (xe+emulator) → ShellAppLauncher (am --windowingMode 5 + am task resize); chưa có dadb → IntentAppLauncher.
    @Volatile private var appLauncher: AppLauncher = IntentAppLauncher(this)
    // @Volatile (cùng lý do `appLauncher` ngay trên): GHI ở thread nền `winExec` (dò dadb), ĐỌC ở thread CHÍNH
    // (openAppFullscreen · reflow · placeApp). Không có nó thì main có thể thấy mãi `null` ⇒ đường shell im lặng mất.
    @Volatile private var shell: ((String) -> String)? = null
    /** F4 — cổng lần dò kênh shell đầu tiên (hoãn · thử lại · dải nhắc). Dựng ở [onCreate], xem [ShellChannelGate]. */
    private lateinit var shellGate: ShellChannelGate
    // dadb → app render lên VirtualDisplay trong ô (Dudu) hoặc ROM platform-signed → ActivityView; cả 2 bỏ freeform + overlay header.
    private val embedding get() = shell != null || SlotAppHost.embeddingUsable(this)
    private val winExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    /** Thread nền RIÊNG cho I/O ảnh (xem submitIo) — không để I/O ảnh chặn lệnh cửa sổ và ngược lại. */
    private val ioExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    internal var shownState: HomeUiState? = null   // view-side diff cache của collector (KHÔNG phải nguồn sự thật)

    internal lateinit var wall: WallView
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            topStrip.updateClock()
            wallpaper.step()   // U4: dùng LẠI nhịp có sẵn thay vì dựng thêm một vòng đếm riêng
            // PERF — báo cáo tải mỗi phút ([KachiPerf]); dùng LẠI nhịp này vì nó chạy đúng lúc vòng poll HAL chạy.
            // ⚠ `elapsedRealtime`, KHÔNG phải giờ tường: [ĐO] xe 14/09 giờ tường của đầu xe bị chỉnh nhảy >5 s giữa
            // phiên (đúng lỗi đã làm hỏng cửa sổ 60 phút của cầu kiểm thử) ⇒ một cú nhảy là một dòng số bịa.
            KachiPerf.dueLine(android.os.SystemClock.elapsedRealtime())?.let { Log.i("KachiPerf", it) }
            handler.postDelayed(this, 10_000)
        }
    }

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(LangHost.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        PredictiveBack.attach(this) { onBackPressed() }   // API 33+ (xe API 29: no-op), lý do ở KDoc [PredictiveBack]
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        // #10 (owner 2026-09-21 · [ĐO xe] taskbar ROM lòi lúc launcher start → đẩy layout, phải nhấn Home): áp
        // immersive NGAY ở onCreate (trước lượt bố trí đầu), rồi re-apply vài nhịp đầu vì ROM DiLink dựng taskbar
        // của nó SAU khi Kachi lên ⇒ một lần gọi lúc onResume không đủ, taskbar hiện lại trong ~1-2s đầu.
        goImmersive()
        window.decorView.post { goImmersive() }
        handler.postDelayed({ if (!destroyed) goImmersive() }, 800)
        handler.postDelayed({ if (!destroyed) goImmersive() }, 2000)
        container = AppContainer.get(this)
        // Owner 2026-09-15: ghi logcat của app ra THẺ suốt phiên (nhẹ head unit, lấy về bằng adb pull) — để có ngữ
        // cảnh khi patch lỗi trên xe. Idempotent + luồng nền daemon; DiagStorageCap dọn không cho phình.
        runCatching { KachiLog.startCapture(this) }
        // Backstop THẺ luôn-bật: prune cây external về CAP ngay lúc mở launcher (force). usage-<ts>.log dồn theo MỖI
        // lần app khởi động; nếu chỉ dựa NavNotificationListener/verbose thì khi chưa cấp quyền nghe thông báo, log
        // của launcher có thể phình không giới hạn. Off-thread + runCatching sẵn trong DiagStorageCap ⇒ an toàn.
        runCatching { DiagStorageCap.enforce(this, force = true) }
        // VM = nguồn sự thật (nạp từ repository qua factory AppContainer). embedded ban đầu = khả năng ActivityView; this là ViewModelStoreOwner.
        viewModel = ViewModelProvider(
            this, container.homeViewModelFactory(embedded = SlotAppHost.embeddingUsable(this)),
        )[HomeViewModel::class.java]
        profileChip = ProfileChip(this, viewModel)
        ThemeHost.sync(viewModel.uiState.value)   // T1 — bảng màu phải có TRƯỚC khi dựng view (xem [ThemeHost])
        topStrip = KachiTopStrip(
            this,
            // ⚠ KHÔNG thêm cổng cấu hình nào vào đây: thanh trên chỉ còn được chạm `active_profile`
            // ([SettingsCatalog.TOP_STRIP_ALLOWED_KEYS]). Pill "Thanh" (xoay vòng viền thanh nút) đã bỏ ở S1, và
            // S4 · R7 bỏ nốt hàng 5 nút bố cục — Cài đặt → Màn hình chính là bề mặt duy nhất của cả hai.
            onOpenSettings = { panels.openSettings() },   // S1: MỘT cửa vào cấu hình (gộp pill "Tuỳ biến" cũ)
            // Chạm chip = MỞ BỘ CHỌN (không xoay vòng — xem KDoc [ProfileChip]). Mục cuối của bộ chọn dẫn sang
            // Cài đặt › Hồ sơ tài xế bằng đúng đường mở Cài đặt đã có, không mở đường thứ hai.
            onProfileTap = { profileChip.picker { panels.openSettings(SettingsGroup.PROFILES) } },
            onOpenAppList = { drawerController.openAppList() },   // U3: mở app toàn màn (không gắn ô)
            onVoice = { voice.start() },                         // V1 pha NGHE — cùng lambda với ô *Nói với xe*
            voicePillEnabled = { Prefs.voiceMicPill(this) && VoiceModelStore.isReady(this) },
            // WP4 — thứ tự vật trên thanh; lượt ĐỔI đi qua `topStrip.setLayout` ở render.
            header = { viewModel.uiState.value.header },
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // S1b — owner 2026-09-14: lề ngoài **bằng nhau ở cả 4 cạnh màn** (trước: trái/phải 16, trên 4, dưới 12 —
            // lệch nhau). Một giá trị [Sp.L] cho cả bốn cạnh ⇒ khung nội dung cách đều mọi mép.
            setPadding(dp(Sp.L), dp(Sp.L), dp(Sp.L), dp(Sp.L))
        }
        // WP5 · R5.2 — bề cao thanh trên khai TƯỜNG MINH (75 % của 56dp); vì sao không `WRAP_CONTENT`: KDoc [KachiBars.HEADER_H].
        content.addView(topStrip.view, LinearLayout.LayoutParams(MATCH, dp(KachiBars.HEADER_H)))
        topStrip.setProfile(viewModel.uiState.value.activeProfile)   // tên + chữ cái của chip, ngay từ lượt dựng

        workspace = WorkspaceView(this).apply {
            mediaProvider = { media.read() }                          // nhạc live (Bitmap ở :app, ngoài state :core)
            onMedia = { media.handle(it) }
            control = container.carControl                             // RW0: ô giữa màn đặt được cả HÀNH ĐỘNG (R2)
            // Đơn vị đặt TRƯỚC lượt render đầu: nếu để lượt render đầu chạy với mặc định rồi mới đặt, thì người dùng
            // đã chọn (vd psi) sẽ phải chịu thêm một lượt dựng lại ô widget mỗi lần mở HOME mà không được gì.
            setUnitPrefs(unitPrefs)
            onSlotTap = { drawerController.open(it) }
            onSlotClear = { slots.clearSlot(it) }
            onSlotSwap = { a, b -> slots.swapSlots(a, b) }
            onAppOpen = { slots.reopenApp(it) }
            onAppSwapped = { i, vd, a, b -> slots.evictBehind(i, vd, a, b) }   // đặt TẠM: app cũ ra sau màn nhà (R0.1)
        }
        windows = LauncherWindows(
            this, workspace, winExec,
            state = { viewModel.uiState.value },
            custom = { customLayout }, embedding = { embedding }, drawerOpen = { drawerController.isOpen() || panels.settingsOpen() || panels.layoutOpen() },
            shell = { shell }, appLauncher = { appLauncher }, dispatcher = { container.windowDispatcher },
            onSlotSwap = { drawerController.open(it) },
        )
        // S4 · R12 — ô loại LAUNCHER trên thanh nút đi ĐÚNG hai đường của thanh trên (xem [Activity.controlDock]).
        dock = controlDock(
            container.carControl,
            { drawerController.openAppList() },
            { panels.openSettings() },
            { voice.start() },
        )

        mainArea = LinearLayout(this)
        DockAreaLayout.apply(mainArea, workspace, dock, viewModel.uiState.value.dock, resources.displayMetrics.density)
        // Khe strip ↔ lưới ô = khe giữa các ô ([Sp.SLOT_GAP], nay 9) để nhịp trong màn nhất quán sau khi owner kéo về 75%.
        content.addView(mainArea, LinearLayout.LayoutParams(MATCH, 0, 1f).also { it.topMargin = dp(Sp.SLOT_GAP) })

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
            onPickApp = { idx, pkg -> slots.assignApp(idx, pkg) },
            onPickWidgets = { idx, ids -> slots.assignWidgets(idx, ids) },
            onOpenApp = { pkg -> slots.openAppFullscreen(pkg) },                        // U3
            recentApps = { container.workspaceRepository.recentApps() },
            appWidgetPicks = { idx ->        // T4: ràng buộc xong mới ghi vào ô; thất bại ⇒ bảng tự nói, ô không đổi
                appWidgets.picks { i -> appWidgets.bind(i) { c -> c?.let { drawerController.close(); viewModel.assignAppWidget(idx, it) } } }
            },
        )

        // Hai vòng thu (state của VM + trạng thái xe LIVE) — thân ở [collectHome] (trần 500 dòng).
        collectHome(this, viewModel, container) { render(it) }

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
        // F4 — lần dò dadb ĐẦU TIÊN đi qua cổng [ShellChannelGate]: hoãn tới khi khung đầu đã vẽ + yên, rồi thử lại
        // đều đặn trong lúc màn còn hiện. Khối `if (dadb.probe())` bên dưới là NGUYÊN đường cũ, không sửa gì.
        shellGate = ShellChannelGate(
            activity = this, host = rootFrame, handler = handler, submitBg = { block -> submitBg(block) },
            // Thân ở [Activity.bringUpShellChannel] (trần 500 dòng) — nguyên đường cũ, không sửa một bước nào.
            onChannelUp = {
                bringUpShellChannel(dadb, seam, workspace, viewModel, container) { s ->
                    shell = s; appLauncher = ShellAppLauncher(s); windows.sweepFloating("shell-up")   // R-B3: dọn cửa sổ nổi Kachi mở lúc chưa có kênh
                }
                if (shell == null) shellGate.wiringFailed()   // READY-AT-HOME lượt 3: nối dây hỏng ⇒ nhả cờ, F4 thử lại
            },
            // Chưa có kênh: VẪN kiểm quyền (đọc trạng thái KHÔNG cần shell — ràng buộc C4) để người dùng biết vì sao
            // app không vào được ô. `awaiting` = hệ thống đang hỏi ⇒ dải nhắc nói, toast im (xem `runAndReport`).
            onReport = { awaiting -> PermissionPreflight.runAndReport(this, false, null, awaitingApproval = awaiting) },
        )
        shellGate.arm()
        wireReadyAtHome(this, shellGate, rootFrame, handler)   // READY-AT-HOME: nhận kênh đã sẵn + thẻ xin quyền (R1/R2)

        // S3 — hai việc chuyển từ màn cũ (đã gỡ 2026-09-13); thân hàm ở [KachiHomeWiring].
        maybeShowDisclaimer()
        openSettingsGroup(intent, panels)
        startVoiceIfRequested(intent, voice)
        // T-BRIDGE — móc cho cầu kiểm thử qua adb; lượt tháo tự nối theo vòng đời (xem KDoc `attachTestBridge`).
        // Gắn móc KHÔNG mở cửa nào: mọi lệnh vẫn bị chặn bởi công tắc ở Cài đặt (`KachiTestBridge`).
        attachTestBridge(viewModel, { slots }, { voice }, { drawerController }, { panels }, { shell }, container.carControl) { l, r -> cameraSignal.tick(l, r) }
    }

    /** `singleTask` ⇒ lời gọi thứ hai về ĐÂY, không phải [onCreate] (bấm bong bóng khi Kachi đang mở sẵn). */
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        openSettingsGroup(intent, panels)
        startVoiceIfRequested(intent, voice)
    }

    // `applyThemeInPlace` · `render` · `selectPreset` · `applyCustomLayout` → `KachiHomeRender.kt` (tách THUẦN theo trần 500
    // dòng, L6-debt 2026-09-27): hàm mở rộng `internal` cùng package, thân giữ nguyên byte; các field chúng chạm là `internal`.

    /**
     * Back — API 29–32 (xe) vào đây từ nền tảng; API 33+ vào từ [PredictiveBack] (đăng ký ở [onCreate]). Kế thừa
     * `android.app.Activity` thuần (không `ComponentActivity`, xem KDoc lớp) nên không có `onBackPressedDispatcher`
     * của AndroidX — lint `GestureBackNavigation` không biết ca đăng ký thủ công này ⇒ tắt tại đúng hàm.
     */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        // [SOÁT P3] Bảng vẽ bố cục từng bị bỏ sót ở đây: mở nó ra rồi bấm Back là **không có gì xảy ra** (Back của
        // HOME vốn không làm gì), người dùng tưởng bảng bị treo. Thứ tự: lớp phủ trên cùng đóng trước.
        when {
            panels.layoutOpen() -> panels.closeLayoutEditor()
            panels.settingsOpen() -> panels.closeSettings()
            drawerController.isOpen() -> drawerController.close()
        }
    }

    override fun onStart() {
        super.onStart(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START); appWidgets.startListening()
        SlotLiveProbe.resume()   // H2·2 — màn hiện lại thì đo tiếp (xem [onStop])
        workspace.returnDetached()   // F1 dòng 9 — app ô đang mở toàn màn (K7) về lại ô (K8), spec shortcuts-autostart
        shellGate.onShown()      // F4 — màn hiện lại thì vòng dò kênh shell chạy tiếp
    }

    override fun onResume() {
        super.onResume(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        goImmersive(); topStrip.updateClock(); wallpaper.reload(); handler.post(tick); ensureCastBubble(bridge)
        runCatching { bridge.autoUpdateOnceIfEnabled() }   // V8 (owner 2026-09-25) tự cập nhật 1 lần/tiến trình (cổng trong cầu)
        topStrip.refreshVoicePill()   // V1 pha NGHE: mô hình có thể vừa được tải/gỡ ở một màn khác
        // "Hey Kachi": bộ nghe FGS chết theo tiến trình (app bị kill/cài lại) và KHÔNG có gì dựng lại ngoài boot
        // thật / gạt công tắc. [ĐO xe 2026-09-21] sau reinstall service = 0 ⇒ "thử một loạt không lên". Mở màn
        // chính ⇒ đồng bộ lại FGS nếu công tắc đang bật (sync no-op khi đã chạy / khi tắt).
        runCatching { com.byd.clusternav.launcher.voice.VoiceWakeService.sync(this) }
        // [SOÁT P2-4] Runnable CÓ TÊN để `onDestroy` gỡ được. Trước đây là lambda vô danh nên không có cách nào
        // huỷ, mà nó lại dựng cửa sổ overlay ⇒ chạy sau khi màn chết là giữ view + giữ activity.
        workspace.removeCallbacks(overlayHeadsKick)
        workspace.postDelayed(overlayHeadsKick, 600)
    }

    /** Dựng dải header nổi sau khi cây view đã có kích thước thật (mở màn xong). */
    private val overlayHeadsKick = Runnable { if (!destroyed) windows.updateOverlayHeads() }

    /** Toàn màn "dính" — cờ cửa sổ nằm ở [goImmersiveWindow] (trần 500 dòng; xem KDoc ở đó). */
    private fun goImmersive() = goImmersiveWindow()

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus); if (hasFocus) goImmersive()
        shellGate.onFocus(hasFocus)   // F4 — mất tiêu điểm = hộp thoại hệ thống đang ở trên ⇒ không dò chồng lên
    }

    override fun onPause() { super.onPause(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); handler.removeCallbacks(tick) }

    /**
     * [SOÁT Pass H2 · P2] Màn khuất ⇒ **ngưng nhịp đo ô**: mở một app toàn màn thì màn chính KHÔNG chết (view
     * còn gắn, ô còn đăng ký) ⇒ không ngưng là đốt một lượt dadb mỗi 5 giây suốt chuyến, xếp hàng trên CÙNG chủ
     * `ShellTransport` với lệnh đặt cửa sổ. Danh sách ô giữ nguyên — xem KDoc [SlotLiveProbe.pause].
     */
    override fun onStop() {
        super.onStop(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP); appWidgets.stopListening()
        SlotLiveProbe.pause()
        // H1 — quên nhu cầu VÀ quên kết luận "xe không có datum ấy" ⇒ lần mở sau bắt đầu bằng một lượt đọc ĐỦ
        container.forgetCarDemand()
        shellGate.onHidden()     // F4 — màn khuất ⇒ dừng vòng dò (không dựng hộp thoại lên app người lái đang dùng)
    }

    /**
     * Huỷ màn — thứ tự QUAN TRỌNG, mỗi bước có lý do riêng:
     *  • [SOÁT P2-4] đánh dấu đã huỷ + gỡ mọi lượt đã hẹn TRƯỚC khi tắt thread nền; làm ngược lại thì một lượt đã hẹn
     *    chen vào giữa và nộp việc cho executor vừa tắt (RejectedExecutionException, không ai bắt) hoặc dựng cửa sổ
     *    overlay bằng WindowManager của activity đã chết.
     *  • Ngăn kéo có thể được gắn như CỬA SỔ RIÊNG (TYPE_APPLICATION_OVERLAY) → KHÔNG chết cùng activity; không đóng
     *    thì rò view + giữ activity, và một cú chạm vào nó chạy vào `winExec` ĐÃ shutdown.
     *  • [SOÁT Pass 2 · P1] tấm chữ phiên nghe cùng loại cửa sổ ⇒ cùng lý do; hỏi `isInitialized` để không DỰNG phiên
     *    nghe ngay lúc huỷ (KDoc [voiceLazy]).
     *  • [SOÁT S1 · P3] `HomePanels.closeAll()` từng là mã chết; nối vào đây vì màn Cài đặt giữ 7 trang đã dựng.
     *  • H2·1 [ĐO 2026-09-14]: `dumpsys display` có 4 `kachi-slot-*` cho 2 ô vì màn đời trước mang cờ "đang kết thúc"
     *    mà view chưa tháo ⇒ nhả màn ảo TƯỜNG MINH, không treo vòng đời tài nguyên hệ thống vào `onDetachedFromWindow`.
     */
    override fun onDestroy() {
        PredictiveBack.detach(this)
        super.onDestroy(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (isFinishing) vmStore.clear()
        destroyed = true
        shellGate.onHidden()   // F4 — gỡ lượt dò đã hẹn TRƯỚC khi tắt thread nền
        workspace.removeCallbacks(overlayHeadsKick)
        handler.removeCallbacksAndMessages(null)
        windows.cancelPending()
        drawerController.close()
        if (voiceLazy.isInitialized()) voice.stop()
        panels.closeAll()
        wallpaper.release()   // U4: nhả ảnh nền, không để giữ bộ nhớ sau khi màn đã huỷ
        workspace.releaseAppHosts()
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
    }
}
