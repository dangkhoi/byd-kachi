# Arch Stage 1 (B1) — DONE

> Commit `055c5ae` (local, chưa push). Build XANH; :core 875 + :app 494 unit tests XANH (golden + ALL cast suites green). Exactly 1 `Dadb.create` for window ops.

## Đã làm
- **NEW `app/src/main/java/com/byd/clusternav/system/ShellTransport.kt`** = chủ DUY NHẤT của kết nối dadb `localhost:5555` cho lệnh cửa sổ/display. Serialize MỌI lệnh trên **1 single-thread ExecutorService** (`kachi-window-shell`), reconnect-retry-once. **KHÔNG thêm dep** (executor, không coroutine — coroutine chưa có trong :app). API: `exec(cmd):Response` · `run(cmd):String` · `seam:(String)->String` · `probe()` · `withConnection{adb->}` (escape hatch cho ClusterCast dead) · process-singleton `get(ctx)`.
- **Route 4 consumer về 1 instance**: `DadbShell` (façade mỏng, giữ API run/probe/close/seam) · cast `DadbSimpleCastShell` (→ `exec`) · legacy `ClusterCast` (DEAD/unreachable — route byte-identical qua `withConnection`) · `CastShell` (đã sink-based, không đổi).
- **`FreeformLaunch.launchOnDisplayCmd(comp, displayId, windowingMode, withLauncherCategory)`** (:core, pure) — VdAppHost + SlotAppHost giờ dựng lệnh qua đây (byte-identical, golden test lock).

## Bất biến còn giữ (Stage sau PHẢI giữ)
- Connection code **byte-identical** pre-B1: `Dadb.create("localhost",5555,AdbKeys.ensure(ctx))`. `KachiHomeActivity` (quyết định probe→host) **KHÔNG đổi**.
- Golden strings byte-stable (`LauncherCommandGoldenTest` 11 + `LauncherWindowingGuardTest` 5). Cast suites (CastSwapTest/CastStressTest/CastFlowTest/SinkGuardTest/SimpleCastCoordinatorTest…) XANH = hành vi cast giữ.

## ⚠ Verify môi trường (pending, KHÔNG phải lỗi B1)
- **Emulator E2E hosting KHÔNG tái hiện được phiên này**: probe dadb trả false → launcher ở chế độ app-card (NoCar); 0 VD + 0 task freeform (kiểm `dumpsys display` + `am stack`). Đã thử 3 cách (launch, relaunch+chờ, reset adb-server). Loopback `localhost:5555` không nối được phiên này (môi trường). B1 KHÔNG thể là nguyên nhân (connection byte-identical + KachiHomeActivity không đổi + đường transport tuần tự = không deadlock). **Re-verify khi loopback lên / trên xe.**

## HAND-OFF cho Stage 2 (B2 — command model + ownership)
- **`ShellTransport` = MECHANISM (dispatch thô).** B2 thêm **policy/queue LÊN TRÊN**: `:core` `WindowMutation` (sealed) + `DisplayOwnershipRegistry` + `AppLocationRegistry` (thuần, test JVM). Validate TRƯỚC dispatch (launcher→display 0 + VD của nó; cast→display 1; cross-boundary → reject + log).
- **Gộp semantics `BoundedCastExecutor`** (priority-stop STOP/RESCUE trước NORMAL · latest-wins intent gộp · `withTimeout`/idempotent · preempt = đóng stream in-flight) vào queue hợp nhất — **KHÔNG hạ về FIFO** (FIFO để STOP kẹt sau cast treo = regress an toàn). Tìm `BoundedCastExecutor` trong `modules/clustercast/**` (+ test CastStressTest) để giữ y semantics.
- Guard B0 `PersistentWindowStateWriterGuardTest` vẫn liệt kê multiple writers — B3 mới siết (đừng đụng ở B2).
- Nếu B2 cần preempt (đóng stream giữa chừng): ExecutorService hiện chạy 1 task tới xong; preempt cần giữ handle stream in-flight + close. Cân nhắc nâng ShellTransport (giữ byte-stable + test).
