# Arch Stage 5b (B5, part 2) — DONE (AppContainer manual-DI graph · god-activity decompose)

> Local (chưa push). Build XANH (`:app:assembleDebug`). Full 5-module: **1625 unit test, 0 fail**
> (core 937 · car-integration 44 · offcar-planner 99 · vehicle-contracts 22 · app 523 = 5a 1621 **+4** AppContainerTest).
> Golden (LauncherCommandGolden 11 · GmapsContentGolden 2) · guards (LauncherWindowingGuard 5 · PersistentWindowStateWriter 4 ·
> **LayeringRules 9**) · cast (48 file · 557 test) đều XANH. **0 file cast bị đụng** (git). THUẦN CODE, KHÔNG đụng seal/MainActivity/appId.

## Mục tiêu B5b
Gộp các process-singleton PHÍA LAUNCHER thành **MỘT đồ thị DI thủ công `AppContainer`** (do một `Application` giữ) + cấp
`HomeViewModel` qua **viewModelFactory**, và **decompose god-activity** `KachiHomeActivity` (dựng-view → đơn vị cohesive).
AN TOÀN: cast KHÔNG verify E2E phiên này → **fold cast BY REFERENCE** (lộ singleton hiện có), KHÔNG rewire caller/logic cast.

## (a) AppContainer graph — `app/.../AppContainer.kt` (84 LOC, `com.byd.clusternav`)
- Sở hữu/giữ (lazy) đồ thị launcher: **`shellTransport`** (ShellTransport) · **`windowDispatcher`** (WindowCommandDispatcher +
  `DisplayOwnershipRegistry`/`AppLocationRegistry` nội bộ, chạy trên shellTransport) · **`workspaceRepository`** (PrefsWorkspaceRepository) ·
  **`inputDaemonClient`** (InputDaemonClient?, seam = `windowDispatcher.launcherSeam()`, null nếu không đọc được apk path).
- **Uỷ quyền thật sự**: `ShellTransport.get(ctx)` = `AppContainer.get(ctx).shellTransport`; `WindowCommandDispatcher.get(ctx)` =
  `AppContainer.get(ctx).windowDispatcher`. Đã GỠ `@Volatile instance` riêng ở cả hai; thêm `createOwned(...)` (internal factory
  companion) để AppContainer dựng chúng. Mọi caller cũ (cast `ShellTransport.get(app).exec/withConnection`, `DadbShell`,
  `FreeformSeed`, KachiHomeActivity) chạy y nguyên — chỉ khác instance đến TỪ container (thật sự một chủ).
- **Cast fold BY REFERENCE**: `val castRuntime: SimpleCastRuntime get() = SimpleCastRuntime` — trả process-singleton object hiện có,
  KHÔNG dựng/không sở hữu coordinator (coordinator vẫn do `SimpleCastRuntime.coordinator(ctx)` tạo lười, gọi bởi caller cast KHÔNG ĐỔI).
- Ctor `internal` nhận init-lambda (`by lazy`) → nhánh không-Android (repository giả / castRuntime / homeViewModelFactory) test JVM
  được mà KHÔNG chạm shellTransport/windowDispatcher; đồ thị Android-đầy-đủ verify bằng `:app:assembleDebug`.

## (b) Application wiring — MỚI `KachiApplication` (17 LOC) + manifest
- Chưa có custom Application → thêm `com.byd.clusternav.KachiApplication` (chỉ gọi `AppContainer.get(this)` trong onCreate; các field
  lazy → KHÔNG chạm mạng/dadb). Đăng ký `android:name=".KachiApplication"` ở `<application>` (AndroidManifest.xml). Container tự-init
  như process-singleton nên `.get(ctx)` vẫn chạy kể cả trước/không có Application (degrade-safe).

## (c) viewModelFactory + activity base — GIỮ `android.app.Activity`
- `HomeViewModelFactory` (30 LOC): `ViewModelProvider.Factory` nhận `WorkspaceRepository` + `embedded`, `create` → `HomeViewModel(repo, embedded)`.
  `AppContainer.homeViewModelFactory(embedded)` = `HomeViewModelFactory(workspaceRepository, embedded)`. Đã GỠ `HomeViewModel.factory(context)` TẠM.
- **KHÔNG migrate ComponentActivity** (rủi ro + phụ thuộc mới): :app KHÔNG có `androidx.activity` → `by viewModels()` cần phụ thuộc mới
  (vi phạm "no new external dependency"); + custom LifecycleRegistry/theming/immersive/singleTask-HOME. → **giữ `android.app.Activity`**,
  cho nó làm **`ViewModelStoreOwner`** (thêm `ViewModelStore`), lấy VM qua `ViewModelProvider(this, factory)[HomeViewModel::class.java]`
  (chỉ dùng `androidx.lifecycle`, KHÔNG phụ thuộc mới). Clear store khi `isFinishing`. configChanges đã chặn recreate → không lo config-survival.

## (d) God-activity decompose — KachiHomeActivity 425 → **250 LOC**
Đơn vị tách (đều chạm android = hợp lệ LayeringRules, KHÔNG phải file thuần):
- **`KachiTopStrip`** (160) — thanh trên: đồng hồ/ngày/segmented preset/chip xe/pill/avatar; callback một chiều; `selectPreset/setProfileInitial/refreshChips/updateClock`.
- **`DrawerController`** (71) — App Drawer overlay/in-frame + phím BACK.
- **`ProfileBar`** (43) — hồ sơ tài xế (cycle + dialog tạo mới).
- **`DockAreaLayout`** (35) — sắp workspace+dock theo viền (was `layoutMainArea`).
- (`LauncherWindows` freeform/overlay đã tách ở 5a.)
Activity còn: lấy VM + `collect` state → **`render(uiState)` MỘT chỗ áp** + glue lifecycle + **glue intent theo-ô GIỮ NGUYÊN** (assignApp/
assignWidgets/clearSlot/swapSlots/reopenApp — inline, dùng `container.windowDispatcher`). B2 dispatcher/reflow + B4 daemon (nay TIÊM từ
container) + B5a StateFlow collect: GIỮ. Mỗi đơn vị ≤ 250.
> Ghi chú: `SlotCommands` (glue intent thuần) từng thử tách nhưng LayeringRules chặn (file THUẦN phải ở :core, mà nó nối type :app) →
> giữ glue trong composition-root (đúng ý "keep intent callbacks intact"); tách VIEW-SETUP (chạm android) thay thế.

## (e) Tests
- **`AppContainerTest`** (4, :app, JVM thuần): castRuntime === SimpleCastRuntime (by reference) · workspaceRepository một-instance (lazy
  memoize) · homeViewModelFactory cấp HomeViewModel nối repo + cờ embedded · factory từ chối ViewModel khác. Nhánh Android (shell/dispatcher/
  daemon) = init-lambda `error` KHÔNG bị chạm.
- **HomeViewModelTest** (13) GIỮ XANH (VM dựng trực tiếp với fake repo; đường factory phủ bởi AppContainerTest).

## (f) Build + FULL test
`:app:assembleDebug` XANH (chỉ warning `FLAG_FULLSCREEN` deprecated — kế thừa, byte-giữ). Full 5-module `--rerun-tasks`:
**1625/0** (core 937 · car-integration 44 · offcar-planner 99 · vehicle-contracts 22 · app 523).
Golden LauncherCommand **11/0** · Gmaps **2/0**; guards LauncherWindowing **5/0** · PersistentWindowStateWriter **4/0** · **LayeringRules 9/0**
(pureFilesStillInApp vẫn **0**); cast **48 file / 557 test / 0 fail**.

## (g) Cast files + deferred
- **0 file cast bị đụng** (git): file đổi = AndroidManifest.xml · HomeViewModel.kt · KachiHomeActivity.kt · WorkspaceView.kt ·
  system/ShellTransport.kt · system/WindowCommandDispatcher.kt. File mới = AppContainer · KachiApplication · HomeViewModelFactory ·
  KachiTopStrip · DrawerController · ProfileBar · DockAreaLayout · AppContainerTest. KHÔNG file nào trong `modules/clustercast/`; KHÔNG đụng
  caller cast (MainActivity/FloatingBubbleService/MainActivityCastController/DiagActivity/RebindReceiver/BadgePlacementController/
  ClusterNavLaneWidget/CastAutostart/CastDeepRescueAction/SimpleCastRuntime).
- **Deferred / device-only (không verify E2E phiên này)**: (1) cast on-car qua `ShellTransport.get` mới-uỷ-quyền (cùng một instance,
  thay đổi thuần cấu trúc) — cast tests off-car 557/0 nhưng dadb thật là device-only; (2) daemon-input trên xe (WorkspaceView.inputClient
  tiêm từ container, seam = dispatcher.launcherSeam giống cũ) — device-only; (3) DecorCaptionView freeform — verify trên xe (như 5a).
- CHƯA commit (chờ owner + security scan). CHƯA sync `PROJECT-BACKLOG.md`/`project-context.md` (làm ở consolidate).
