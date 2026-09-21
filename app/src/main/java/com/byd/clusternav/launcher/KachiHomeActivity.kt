package com.byd.clusternav.launcher

import android.util.Log
import android.app.Activity
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
    private lateinit var workspace: WorkspaceView
    private lateinit var dock: ControlDockView
    private lateinit var viewModel: HomeViewModel
    private lateinit var windows: LauncherWindows
    private lateinit var topStrip: KachiTopStrip

    /** Cầu sang cấu hình/hành động của ClusterNav (IA v2 · §4.2) — dựng MỘT lần, xem [clusterNavBridge]. */
    private val bridge: ClusterNavBridge by lazy { clusterNavBridge() }

    /** Hai bảng phủ toàn màn (màn Cài đặt + bảng vẽ bố cục) — khối nối dây ở [homePanels] (trần 500 dòng). */
    private val panels: HomePanels by lazy {
        homePanels(
            activity = this, rootFrame = rootFrame, viewModel = viewModel, bridge = bridge,
            openDockPicker = { sel, apply -> drawerController.openDockPicker(sel, apply) },
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
            assignAppToSlot = { idx, pkg -> slots.assignApp(idx, pkg); true },
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
            onArtChanged = { KachiGlass.refresh(rootFrame); if (ThemeHost.sync(viewModel.uiState.value)) recreate() },
        )
    }
    private lateinit var drawerController: DrawerController
    /** S4 · R7 — bộ chọn hồ sơ sau cú chạm chip hồ sơ (thay `ProfileBar`: hết xoay vòng, hết hộp thoại tạo thứ hai). */
    private lateinit var profileChip: ProfileChip
    private lateinit var mainArea: LinearLayout
    private lateinit var rootFrame: FrameLayout
    private val media by lazy { MediaBridge(this) }        // đọc nhạc live cho w_media + transport
    /** T4 — chủ DUY NHẤT của widget Android bên thứ ba (host + id + bind-grant). Xem `AppWidgetSlotHost`. */
    private val appWidgets by lazy { AppWidgetSlotHost(this, { shell }, { submitBg(it) }, { drawerController.say(it) }) }
    private val appOpener by lazy { AppOpener(this) }      // U3: mở app toàn màn (đường "mở app kiểu thường")

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
        )
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
            // V1.1 — CÙNG đường mà ngăn kéo dùng khi người ta chọn app cho một ô.
            assignAppToSlot = { idx, pkg -> slots.assignApp(idx, pkg); true },
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
    private val unitPrefs: UnitPrefs get() = viewModel.uiState.value.unitPrefs
    private val customLayout: GridLayout? get() = viewModel.uiState.value.customLayout
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
    private var shownState: HomeUiState? = null   // view-side diff cache của collector (KHÔNG phải nguồn sự thật)

    private lateinit var wall: WallView
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
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
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
                    shell = s; appLauncher = ShellAppLauncher(s)
                }
            },
            // Chưa có kênh: VẪN kiểm quyền (đọc trạng thái KHÔNG cần shell — ràng buộc C4) để người dùng biết vì sao
            // app không vào được ô. `awaiting` = hệ thống đang hỏi ⇒ dải nhắc nói, toast im (xem `runAndReport`).
            onReport = { awaiting -> PermissionPreflight.runAndReport(this, false, null, awaitingApproval = awaiting) },
        )
        shellGate.arm()

        // S3 — hai việc chuyển từ màn cũ (đã gỡ 2026-09-13); thân hàm ở [KachiHomeWiring].
        maybeShowDisclaimer()
        openSettingsGroup(intent, panels)
        startVoiceIfRequested(intent, voice)
        // T-BRIDGE — móc cho cầu kiểm thử qua adb; lượt tháo tự nối theo vòng đời (xem KDoc `attachTestBridge`).
        // Gắn móc KHÔNG mở cửa nào: mọi lệnh vẫn bị chặn bởi công tắc ở Cài đặt (`KachiTestBridge`).
        attachTestBridge(viewModel, { slots }, { voice }, { drawerController }, { panels }, { shell }, container.carControl)
    }

    /** `singleTask` ⇒ lời gọi thứ hai về ĐÂY, không phải [onCreate] (bấm bong bóng khi Kachi đang mở sẵn). */
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        openSettingsGroup(intent, panels)
        startVoiceIfRequested(intent, voice)
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
        // R1/R2 (quality-review 2026-09-15): registry vị-trí-app là PROJECTION của state — reconcile MỖI render ở
        // ĐÚNG MỘT chỗ, thay các lệnh d.place/d.remove sửa tay ở handler (nguồn drift "3 nguồn sự-thật"). Đọc-vẽ,
        // không đổi state. `WorkspaceView.render` phía trên đã lo VdAppHost theo-ô; đây lo registry + evict app rời ô.
        windows.reconcileLocations(state.workspace.slots)
        // WP4 — thứ tự vật trên thanh trên đổi ⇒ ĐẶT LẠI CHỖ (không dựng lại view — `KachiTopStrip.setLayout`).
        if (prev?.header != state.header) topStrip.setLayout(state.header)
        // ⚠ xét CẢ `topStrip`: thiếu nó thì đổi danh sách chip mà màn hình không đổi gì (off-car trạng thái xe gần như không đổi).
        if (prev == null || prev.carStatus != state.carStatus || prev.topStrip != state.topStrip) {
            topStrip.refreshChips(state.carStatus, unitPrefs, state.topStrip)
            // RW0/Đ4: thanh nút cũng cần trạng thái xe để ô ĐỌC sống được ở đó. CHỈ đổ lại số của ô đọc — KHÔNG
            // dựng lại thanh (C5: dựng lại mỗi nhịp 1/giây sẽ nháy + mất trạng thái ô vừa bấm).
            dock.setCarStatus(state.carStatus, unitPrefs)
            workspace.setUnitPrefs(unitPrefs)   // R11: ô giữa màn cũng theo lựa chọn đơn vị (tự bỏ qua nếu không đổi)
        }
        // ⚠ S4 · R7 — KHÔNG còn dải nút bố cục trên thanh trên nên ở đây không còn gì để tô sáng. Ô đang sáng của
        // bố cục sẵn nay chỉ nằm trong Cài đặt › Màn hình chính, và trang đó tự dựng lại khi state đổi.
        if (prev?.dock != state.dock) {
            // Dựng lại cây bố cục khi ĐỔI VIỀN hoặc ĐỔI cờ ẩn/hiện (S1b): cả hai đều đổi vị trí/việc gắn của
            // thanh nút trong `mainArea`, mà `dock.setConfig` chỉ đổi nút BÊN TRONG thanh, không gắn/tháo thanh.
            // Thiếu nhánh `visible` thì bật/tắt "Hiện thanh nút" không có tác dụng tới khi đổi viền/dựng lại màn.
            val layoutChanged = prev != null &&
                (prev.dock.edge != state.dock.edge || prev.dock.visible != state.dock.visible)
            dock.setConfig(state.dock)
            if (layoutChanged) DockAreaLayout.apply(mainArea, workspace, dock, state.dock, resources.displayMetrics.density)
        }
        // Đổi/thêm/xoá hồ sơ nạp lại TOÀN BỘ state ⇒ trang đã dựng của màn Cài đặt (nếu đang mở) trở nên cũ. Đi theo
        // đường một chiều: state đổi → render → bảng dựng lại. ⚠ phải xét CẢ `profiles`: xoá một hồ sơ KHÔNG phải hồ
        // sơ đang dùng thì `activeProfile` không đổi, và nếu chỉ xét nó thì danh sách trên màn vẫn còn hồ sơ vừa xoá.
        if (prev == null || prev.activeProfile != state.activeProfile || prev.profiles != state.profiles) {
            topStrip.setProfile(state.activeProfile)
            panels.invalidateSettings()
        }
        // [SOÁT P1-1 kiến trúc] Bố cục tự vẽ đẩy xuống view ở ĐÚNG MỘT CHỖ: theo state, khi state đổi. Trước đây chỗ
        // này tự đọc lại repository khi đổi hồ sơ (đường đọc bền nằm trong tầng UI) còn việc đẩy xuống view thì ở hàm
        // khác ⇒ hai đường song song. Nay `load()`/`switchProfile()` đã nạp bố cục vào state nên ca đó tự đúng.
        // S4 · R6: hồ sơ lúc nổ máy đổi ⇒ chip "Gần nhất/<tên>" phải vẽ lại (trang Cài đặt được nhớ nên không tự
        // dựng lại; thiếu dòng này thì chọn hồ sơ nổ máy là "màn hình không đổi gì" — họ lỗi nút bố cục sẵn ở P9).
        if (prev != null && prev.bootProfile != state.bootProfile) panels.invalidateSettings()
        if (prev?.customLayout != state.customLayout) {
            workspace.setCustomLayout(state.customLayout)
            windows.reflow()
        }
        // Ẩn/hiện thanh (S1b) cũng đổi KÍCH THƯỚC vùng ô (ẩn ⇒ ô lấp trọn màn), nên cửa sổ app đặt trong ô phải đặt
        // lại theo khung mới — cùng lý do đổi viền/bố cục.
        if (prev != null &&
            (prev.preset != state.preset || prev.dock.edge != state.dock.edge || prev.dock.visible != state.dock.visible)
        ) {
            windows.reflow()
        }
        shownState = state
        windows.updateOverlayHeads()
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

    override fun onStart() {
        super.onStart(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START); appWidgets.startListening()
        SlotLiveProbe.resume()   // H2·2 — màn hiện lại thì đo tiếp (xem [onStop])
        shellGate.onShown()      // F4 — màn hiện lại thì vòng dò kênh shell chạy tiếp
    }

    override fun onResume() {
        super.onResume(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        goImmersive(); topStrip.updateClock(); wallpaper.reload(); handler.post(tick); ensureCastBubble(bridge)
        topStrip.refreshVoicePill()   // V1 pha NGHE: mô hình có thể vừa được tải/gỡ ở một màn khác
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

    override fun onDestroy() {
        super.onDestroy(); lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (isFinishing) vmStore.clear()
        // [SOÁT P2-4] Thứ tự QUAN TRỌNG: đánh dấu đã huỷ + gỡ mọi lượt đã hẹn TRƯỚC khi tắt thread nền. Làm ngược
        // lại thì một lượt đã hẹn có thể chen vào giữa và nộp việc cho executor vừa tắt (RejectedExecutionException,
        // không ai bắt) hoặc dựng cửa sổ overlay bằng WindowManager của activity đã chết.
        destroyed = true
        shellGate.onHidden()   // F4 — gỡ lượt dò đã hẹn TRƯỚC khi tắt thread nền (cùng lý do khối ngay trên)
        workspace.removeCallbacks(overlayHeadsKick)
        handler.removeCallbacksAndMessages(null)
        windows.cancelPending()
        // Ngăn kéo có thể được gắn như CỬA SỔ RIÊNG (TYPE_APPLICATION_OVERLAY qua WindowManager) → nó KHÔNG chết
        // cùng activity. Không đóng ở đây thì cửa sổ đó sống tiếp (rò rỉ view + giữ activity), và một cái chạm vào
        // nó sẽ chạy vào `winExec` ĐÃ shutdown (RejectedExecutionException) hoặc mở activity từ activity đã huỷ.
        drawerController.close()
        // [SOÁT Pass 2 · P1] Tấm chữ của phiên nghe là **cùng loại cửa sổ** với ngăn kéo ngay trên
        // (`TYPE_APPLICATION_OVERLAY`) ⇒ cùng lý do: không đóng tay thì nó sống tiếp sau khi màn chết, ăn mọi cú
        // chạm toàn màn, và giữ cả micro đang mở lẫn một `VoiceDispatcher` trỏ vào activity đã huỷ.
        // Hỏi `isInitialized` để không DỰNG một phiên nghe ngay lúc đang huỷ màn (xem KDoc [voiceLazy]).
        if (voiceLazy.isInitialized()) voice.stop()
        // [SOÁT S1 · P3] `HomePanels.closeAll()` tự nhận là "gọi lúc huỷ màn (lớp phủ giữ view là giữ activity)"
        // nhưng [ĐO] nó KHÔNG có chỗ gọi nào — mã chết + một câu KDoc nói sai. Nối vào đây: màn Cài đặt giữ 7 trang
        // đã dựng (trang "Màn hình chính" một mình là 187 ô) nên nhả sớm là việc đúng, và từ nay câu KDoc thành thật.
        panels.closeAll()
        wallpaper.release()   // U4: nhả ảnh nền, không để giữ bộ nhớ sau khi màn đã huỷ
        // H2·1 [ĐO 2026-09-14]: `dumpsys display` có 4 `kachi-slot-*` cho 2 ô vì màn Kachi đời trước mang cờ "đang
        // kết thúc" mà view chưa tháo ⇒ màn ảo của nó sống tiếp. Nhả TƯỜNG MINH ở đây thay vì chờ `onDetachedFromWindow`
        // — vòng đời tài nguyên hệ thống không được treo vào một sự kiện mà hệ điều hành có quyền hoãn.
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
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
