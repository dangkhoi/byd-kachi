package com.byd.clusternav

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.byd.clusternav.launcher.HomeViewModelFactory
import com.byd.clusternav.launcher.PrefsWorkspaceRepository
import com.byd.clusternav.launcher.WorkspaceRepository
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.system.ShellTransport
import com.byd.clusternav.system.WindowCommandDispatcher
import com.byd.clusternav.system.inputd.InputDaemonClient

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
) {
    /** Chủ DUY NHẤT của kết nối dadb window/cast — [ShellTransport.get] uỷ quyền về đây. */
    val shellTransport: ShellTransport by lazy { shellTransportInit() }

    /** Cổng ownership display chạy trên [shellTransport] — [WindowCommandDispatcher.get] uỷ quyền về đây. */
    val windowDispatcher: WindowCommandDispatcher by lazy { windowDispatcherInit(shellTransport) }

    /** Tầng-dữ-liệu HOME (bọc `WorkspacePrefs`), nguồn cho [HomeViewModel]. */
    val workspaceRepository: WorkspaceRepository by lazy { workspaceRepositoryInit() }

    /** Client input-daemon dùng chung mọi ô (null nếu không đọc được đường APK). Seam = [windowDispatcher]. */
    val inputDaemonClient: InputDaemonClient? by lazy { inputDaemonClientInit(windowDispatcher) }

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
        )

        private fun buildInputDaemonClient(app: Context, dispatcher: WindowCommandDispatcher): InputDaemonClient? {
            val apk = runCatching { app.applicationInfo.sourceDir }.getOrNull()
            return if (apk.isNullOrEmpty()) null else InputDaemonClient(apk, dispatcher.launcherSeam())
        }
    }
}
