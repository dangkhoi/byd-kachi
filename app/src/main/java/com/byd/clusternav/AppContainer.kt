package com.byd.clusternav

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.byd.clusternav.launcher.BydHalGateway
import com.byd.clusternav.launcher.CarControlAdapter
import com.byd.clusternav.launcher.CarControlPort
import com.byd.clusternav.launcher.CarDataAdapter
import com.byd.clusternav.launcher.CarDataPort
import com.byd.clusternav.launcher.CarStatusRepository
import com.byd.clusternav.launcher.HalBindingTable
import com.byd.clusternav.launcher.HalGateway
import com.byd.clusternav.launcher.HomeViewModelFactory
import com.byd.clusternav.launcher.PrefsWorkspaceRepository
import com.byd.clusternav.launcher.WorkspaceRepository
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.system.ShellTransport
import com.byd.clusternav.system.WindowCommandDispatcher
import com.byd.clusternav.system.inputd.InputDaemonClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * ĐỒ THỊ DI thủ công DUY NHẤT cho tiến trình launcher (Kachi) — B5 (part 2). Sở hữu/giữ (lazy) các process-singleton
 * PHÍA LAUNCHER thành MỘT đồ thị mạch lạc do [KachiApplication] khởi tạo:
 *  • [shellTransport] — chủ DUY NHẤT của kết nối dadb localhost:5555 (mọi lệnh cửa sổ/cast).
 *  • [windowDispatcher] — cổng ownership display (kèm `DisplayOwnershipRegistry` + `AppLocationRegistry` bên trong)
 *    chạy TRÊN [shellTransport].
 *  • [workspaceRepository] — tầng-dữ-liệu HOME (bọc `WorkspacePrefs`).
 *  • [inputDaemonClient] — client input-daemon dùng chung mọi ô; seam khởi động = [windowDispatcher] launcher-seam.
 *
 * `ShellTransport.get(ctx)` / `WindowCommandDispatcher.get(ctx)` NAY UỶ QUYỀN về đây (một chủ thật sự) — mọi caller cũ
 * (cast, `DadbShell`, `FreeformSeed`…) chạy y nguyên, chỉ khác là instance đến TỪ đồ thị này (thay các singleton
 * `X.get(ctx)` rải rác).
 *
 * CAST FOLD-BY-REFERENCE: [castRuntime] TRẢ VỀ [SimpleCastRuntime] hiện có (process-singleton object) — KHÔNG dựng/không
 * sở hữu coordinator cast ở đây (coordinator vẫn do `SimpleCastRuntime.coordinator(ctx)` tạo lười, gọi bởi các caller
 * cast KHÔNG ĐỔI). Nhờ vậy cast là "một phần đồ thị" mà KHÔNG phải đụng bất kỳ caller/logic cast nào (cast chưa
 * verify E2E phiên này).
 *
 * ── Vì sao init-lambda? ──────────────────────────────────────────────────────────────────────────────────────
 * Container dựng qua các init-lambda `by lazy` → nhánh KHÔNG cần Android (repository giả / [castRuntime] /
 * [homeViewModelFactory]) test JVM được off-device mà không phải chạm [shellTransport]/[windowDispatcher] (chỉ dựng
 * khi được TRUY CẬP). Đồ thị Android-đầy-đủ verify bằng `:app:assembleDebug` (compile + wire).
 */
class AppContainer internal constructor(
    private val shellTransportInit: () -> ShellTransport,
    private val windowDispatcherInit: (ShellTransport) -> WindowCommandDispatcher,
    private val workspaceRepositoryInit: () -> WorkspaceRepository,
    private val inputDaemonClientInit: (WindowCommandDispatcher) -> InputDaemonClient?,
    private val carGatewayInit: () -> HalGateway,
    // Poll trạng thái xe = HAL binder reflection (IPC CHẶN) → chạy trên Dispatchers.IO (đúng pool cho blocking I/O),
    // KHÔNG phải Default (pool CPU) — tránh chiếm luồng CPU khi đọc HAL trên xe. Off-car (gateway null) vô hại.
    private val carScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    /** Chủ DUY NHẤT của kết nối dadb window/cast — [ShellTransport.get] uỷ quyền về đây. */
    val shellTransport: ShellTransport by lazy { shellTransportInit() }

    /** Cổng ownership display chạy trên [shellTransport] — [WindowCommandDispatcher.get] uỷ quyền về đây. */
    val windowDispatcher: WindowCommandDispatcher by lazy { windowDispatcherInit(shellTransport) }

    /** Tầng-dữ-liệu HOME (bọc `WorkspacePrefs`), nguồn cho [HomeViewModel]. */
    val workspaceRepository: WorkspaceRepository by lazy { workspaceRepositoryInit() }

    /** Client input-daemon dùng chung mọi ô (null nếu không đọc được đường APK). Seam = [windowDispatcher]. */
    val inputDaemonClient: InputDaemonClient? by lazy { inputDaemonClientInit(windowDispatcher) }

    // ── Lớp DỮ LIỆU + ĐIỀU KHIỂN XE (W1) — registry-driven, off-car trả null ⇒ UI "—" (OQ1: KHÔNG demo) ──
    /** Bảng nối HAL dùng CHUNG (1 gateway) cho cả đọc telemetry lẫn ghi control. */
    private val halBindingTable: HalBindingTable by lazy { HalBindingTable(carGatewayInit()) }

    /**
     * H1 (PERF 2026-09-16) — **nhu cầu dữ liệu của màn hình đang hiện**, cầu một chiều `state → poll`.
     *
     * Màn chính ghi ([collectHome] mỗi lượt state, [KachiHomeActivity.onStop] xoá); vòng poll đọc. `null` (mặc
     * định, và sau khi màn khuất) = đọc hết như mọi bản trước 1.67 — xem KDoc [CarDataDemand].
     */
    val carDemand = com.byd.clusternav.launcher.CarDataDemand.Holder()

    /** Adapter đọc xe: [CarDataPort] (widget cũ) + `CarStatusReader` (build [com.byd.clusternav.launcher.CarStatus]). */
    private val carDataAdapter: CarDataAdapter by lazy { CarDataAdapter(halBindingTable, carDemand::get) }

    /** Cổng đọc xe LIVE cho widget/thanh trạng thái — off-car mọi field null ⇒ "—". */
    val carData: CarDataPort get() = carDataAdapter

    /** Cổng điều khiển xe (toggle/step/cover/select/press) — **KHÔNG gate**; off-car no-op (false). */
    val carControl: CarControlPort by lazy { CarControlAdapter(halBindingTable) }

    /**
     * Đọc MỘT datum theo id → chuỗi hiển thị kèm đơn vị (cho công cụ kiểm tra từng nút). `null` = off-car / chưa map.
     * Đi qua đúng [HalBindingTable] mà widget/thanh trạng thái dùng — không mở đường đọc thứ hai.
     */
    fun telemetryText(id: String): String? {
        val raw = halBindingTable.readString(id) ?: return null
        val unit = com.byd.clusternav.launcher.TelemetryRegistry.byId(id)?.unit ?: ""
        return if (unit.isBlank()) raw else "$raw $unit"
    }

    /** Repo trạng thái xe LIVE: poll 2 nhịp → `StateFlow<CarStatus>` (nguồn cho UDF HOME; Activity collect qua repeatOnLifecycle). */
    val carStatusRepository: CarStatusRepository by lazy { CarStatusRepository(carDataAdapter, carScope) }

    /**
     * H1 — màn chính rời tiền cảnh: quên **nhu cầu** và quên **kết luận "xe không có datum ấy"**.
     *
     * Hai việc, một chỗ gọi, có chủ ý: chúng cùng hết hiệu lực vào đúng một thời điểm (lần mở sau bắt đầu bằng
     * một lượt đọc ĐỦ), và tách ra hai lời gọi là mời một trong hai bị quên — đúng họ lỗi CLAUDE.md §8.
     */
    fun forgetCarDemand() {
        carDemand.clear()
        carDataAdapter.forgetAbsent()
    }

    /**
     * [SOÁT P1-1 · 2026-09-16] Đọc **TƯƠI** một datum cho câu hỏi bằng giọng; `null` = *"dùng ảnh chụp sẵn có"*.
     *
     * Trả `null` ở hai ca — và cả hai đều là *"ảnh chụp ĐÃ tươi rồi"*, không phải bỏ cuộc:
     *  • nhu cầu chưa tính được (`null`) ⇒ vòng poll đang đọc **hết** mọi datum mỗi 10 s;
     *  • datum đã nằm trong nhu cầu ⇒ nó đang được đọc mỗi nhịp.
     * Nhờ hai lối ra này, phần việc đồng bộ có trần cứng: **đúng một** datum ngoài màn, cộng vài datum của màn.
     */
    fun refreshForRead(id: String): com.byd.clusternav.launcher.CarStatus? {
        val want = carDemand.get() ?: return null
        if (id in want) return null
        return carDemand.withExtra(setOf(id)) { carStatusRepository.refreshNow() }
    }

    /** Cast folded BY REFERENCE — process-singleton object hiện có; KHÔNG sở hữu/không dựng coordinator ở đây. */
    val castRuntime: SimpleCastRuntime get() = SimpleCastRuntime

    /** Factory chuẩn AndroidX cấp [HomeViewModel] nối [workspaceRepository] + cờ [embedded] runtime. */
    fun homeViewModelFactory(embedded: Boolean): ViewModelProvider.Factory =
        HomeViewModelFactory(workspaceRepository, embedded)

    companion object {
        @Volatile private var instance: AppContainer? = null

        /** Container tiến-trình DUY NHẤT (tạo lười, thread-safe). [KachiApplication] gọi sớm để đồ thị sẵn sàng. */
        fun get(context: Context): AppContainer {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: build(app).also { instance = it }
            }
        }

        private fun build(app: Context): AppContainer = AppContainer(
            shellTransportInit = { ShellTransport.createOwned(app) },
            windowDispatcherInit = { transport -> WindowCommandDispatcher.createOwned(transport) },
            workspaceRepositoryInit = { PrefsWorkspaceRepository(app) },
            inputDaemonClientInit = { dispatcher -> buildInputDaemonClient(app, dispatcher) },
            carGatewayInit = { BydHalGateway(app) },
        )

        private fun buildInputDaemonClient(app: Context, dispatcher: WindowCommandDispatcher): InputDaemonClient? {
            val apk = runCatching { app.applicationInfo.sourceDir }.getOrNull()
            return if (apk.isNullOrEmpty()) null else InputDaemonClient(apk, dispatcher.launcherSeam())
        }
    }
}
