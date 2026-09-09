# Arch Stage 2b (B2 · part 2) — DONE

> UNCOMMITTED (local). Build XANH (`:app:assembleDebug`); **:core 910 + :app 501 unit tests XANH, 0 fail**.
> ALL cast suites + launcher golden GREEN. Cast command strings + BoundedCastExecutor logic **byte-untouched**.

## Đã làm (wire B2a policy vào runtime)

- **`ShellTransport` (:app) giờ ƯU-TIÊN.** Thay `Executors.newSingleThreadExecutor` (FIFO) bằng
  **`PrioritySerialExecutor`** — vẫn one-at-a-time nhưng rút STOP/RESCUE trước NORMAL. `exec`/`run` thêm
  `priority: MutationPriority = NORMAL` (**default NORMAL ⇒ mọi caller cũ byte/behaviour-identical**). conn/
  retry/withConnection/close/seam/probe **KHÔNG đổi**.
- **`PrioritySerialExecutor` (:core, THUẦN).** `ThreadPoolExecutor(1,1)` + `PriorityBlockingQueue` +
  `newTaskFor`→`PriorityTask` (Comparable theo rank rồi seq → FIFO trong-mức) + `prestartCoreThread`. Đặt ở
  **:core** vì `LayeringRulesTest.pureFilesStillInApp == 0` cấm file thuần trong :app; ShellTransport (:app)
  delegate xuống. Test ưu-tiên nằm ở `:core:test` (`PrioritySerialExecutorTest`, 5).
- **`WindowCommandDispatcher` (:app/system, NEW).** Giữ process-singleton `DisplayOwnershipRegistry` +
  `AppLocationRegistry` + transport. `dispatch(mutation, issuer)`: validate ownership TRƯỚC → REJECT thì log +
  KHÔNG chạm transport; ALLOW thì `transport.run(render, priority)` + cập nhật location. `launcherSeam()`: suy
  `--display N` từ chuỗi thô → `Raw(cmd, target)` → dispatch LAUNCHER (chuỗi byte-KHÔNG-đổi ⇒ golden giữ).
- **Route nhánh LAUNCHER qua dispatcher.** `KachiHomeActivity`: `ShellAppLauncher(dispatcher.launcherSeam())`
  + `workspace.shell = seam` + `seam(...)` cho HOME_FRONT/force-stop/appops. `VdAppHost`: đăng ký displayId
  của VD (`registerLauncherVirtualDisplay`) NGAY sau `createVirtualDisplay` (TRƯỚC `maybeLaunch` dispatch), gỡ
  ở `onDetachedFromWindow`; lệnh `am start --display <vdId>` của nó qua launcherSeam ⇒ ALLOW **chỉ khi** VD đã
  đăng ký. Cổng làm cho op launcher **không thể** nhắm display ≥ 1 (trừ VD của chính nó).
- **AppLocationRegistry wire phía LAUNCHER (luôn-bật, không phụ thuộc dadb):** `assignApp`→place(0,index) (gỡ
  app cũ nếu ô đổi), `clearSlot`→remove, `swapSlots`→re-place, `seedLocations()` lúc mở app. Bất biến MỘT-VỊ-TRÍ
  có thật ở phía launcher runtime.

## Bất biến còn giữ (Stage sau PHẢI giữ)
- **Cast preserved:** CastSwap 15 · CastStress 5 · CastFlow 15 · SinkGuard 5 · SimpleCastCoordinator 31 = 0 fail.
  Cast command STRINGS + BoundedCastExecutor logic byte-untouched (grep: `DadbSimpleCastShell.execute` vẫn
  `exec(command)` = NORMAL; `ClusterCast.withConnection` không đổi).
- **Golden byte-stable:** LauncherCommandGoldenTest 11 + LauncherWindowingGuardTest 5 = 0 fail (VdAppHost vẫn
  chứa `FreeformLaunch.launchOnDisplayCmd(comp, displayId, windowingMode = 1)`; không có inline `am start
  --display $displayId`; không file /launcher/ nào có literal `--display [1-9]`).
- **Layering:** LayeringRulesTest 9 = 0 fail (PrioritySerialExecutor thuần→:core; dispatcher android→:app;
  pureFilesStillInApp==0).

## ⚠ DEFERRED (item 4 & cast-side item 5) — follow-up cho Stage sau

- **Item 4 — cast STOP/RESCUE priority trên hàng đợi CHUNG: DEFERRED, cast vẫn NORMAL.** Lý do: tín hiệu
  "đây là stop" chỉ phát sinh ở `SimpleCastCoordinator.dispatch` (submitStop) hoặc `BoundedCastExecutor
  .submitStop` — cả hai đều bị chỉ thị **"do NOT change operation logic"** cấm sửa; cast KHÔNG E2E-verify được
  phiên này (emulator dadb loopback down) nên unit test là cổng an toàn duy nhất. Không sửa scheduler đã proven
  cho một tối ưu thứ-tự không kiểm được. **Hạ tầng đã sẵn**: `ShellTransport.exec/run(cmd, priority)` +
  `PrioritySerialExecutor` STOP-preempt-NORMAL. Cast STOP vẫn preempt TRONG executor của nó (submitStop huỷ
  active + purge pending); chỉ hàng đợi CHUNG coi lệnh cast-stop là NORMAL (có thể chờ sau 1 lệnh launcher
  NORMAL). **Recipe follow-up (nhỏ):** thêm `CastShellPriority` (thread-local :core, default NORMAL) + `Bounded
  CastExecutor.submitStop` bọc block bằng `CastShellPriority.withStop { }` + `DadbSimpleCastShell.execute` đọc
  thread-local → `ShellTransport.exec(cmd, priority)`. Đặt sau khi cast E2E-verify được trên xe.
- **Item 5 — cast-side location (place on cast / remove on stop): DEFERRED.** Cùng lý do (chạm coordinator đã
  proven, không verify được). Phía LAUNCHER đã wire đầy đủ. Bất biến CROSS-track (cast↔ô) chỉ thành thật khi
  cast-side cũng gọi `place(pkg, castDisplayId)` / `remove` — làm cùng follow-up item 4.

## On-car pending
- Emulator dadb loopback down → dispatcher/VD-register/reflow **không E2E-verify được** phiên này (giống B1).
  Đã chứng minh off-car bằng unit test (ownership REJECT/ALLOW, location, priority). Re-verify trên xe / khi
  loopback lên: (a) VD ô đăng ký → app render trong ô; (b) không op launcher nào lọt display 1; (c) location
  đúng khi assign/clear/swap.

## Files
NEW `core/.../system/PrioritySerialExecutor.kt` (+ test) · NEW `app/.../system/WindowCommandDispatcher.kt`
(+ test) · MOD `app/.../system/ShellTransport.kt` · `app/.../launcher/{KachiHomeActivity,WorkspaceView,VdAppHost}.kt`.
Cast: **0 file chạm.**
