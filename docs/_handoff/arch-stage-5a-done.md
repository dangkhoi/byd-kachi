# Arch Stage 5a (B5, part 1) — DONE (HomeViewModel · StateFlow single source of truth)

> Local (chưa push). Build XANH (`:app:assembleDebug` + `test`/`testVehicleTestUnitTest`). Full 5-module:
> **1621 unit test, 0 fail** (core 937 · car-integration 44 · offcar-planner 99 · vehicle-contracts 22 · app 519).
> Golden (LauncherCommandGoldenTest 11) + guards (LauncherWindowing 5 · PersistentWriter 4 · **LayeringRules 9**)
> + cast (17 file) XANH. THUẦN CODE, KHÔNG đụng seal/MainActivity/appId.

## Mục tiêu B5a
Đưa state launcher RA KHỎI `WorkspaceView` (trước GIỮ qua `currentState()`) → **`HomeViewModel` phát
`StateFlow<HomeUiState>` là NGUỒN SỰ THẬT DUY NHẤT**. Luồng MỘT CHIỀU: user event → intent VM → state+persist →
`uiState` phát → Activity thu (`repeatOnLifecycle(STARTED)`) → render. (KHÔNG đụng cast.)

## Context7 (W1) — version + API đã verify (2026-09-10)
- **kotlinx-coroutines 1.10.2** (`-android` impl + `-test` testImpl) — README master = 1.11; 1.10.2 = dải stable đã cài xanh.
- **androidx.lifecycle 2.9.0** (`lifecycle-viewmodel-ktx` + `lifecycle-runtime-ktx`) — ktx artifacts cấp `viewModelScope`
  / `repeatOnLifecycle` / `lifecycleScope` / `LifecycleRegistry` (transitively về base module).
- **Turbine 1.2.1** (`app.cash.turbine`, testImpl) — latest stable (Maven Central).
- API HIỆN HÀNH (không deprecated): `MutableStateFlow` + `asStateFlow()`, `_state.update{}` / `updateAndGet{}`,
  `runTest{}`, `Turbine.test{ awaitItem() }`. `Dispatchers.setMain` KHÔNG cần (VM ghi ĐỒNG BỘ, không viewModelScope.launch).

## Thành phần
### :core (PURE — giữ LayeringRules `pureFilesStillInApp=0` xanh)
- **`launcher/HomeUiState.kt`** — data class immutable: `workspace: WorkspaceState` (+ `preset`/`slots` uỷ quyền),
  `dock: DockConfig`, `activeProfile`, `profiles`, `themeMode`, `embedded` (runtime host flag, không bền). `DEFAULT_PROFILE`.
- **`launcher/WorkspaceRepository.kt`** — interface seam: `load()/persist(state)/switchProfile/addProfile/deleteProfile`.
- **`launcher/WorkspaceState.kt`** — +`swap(a,b)` (pure, reuse ở intent swapSlots) + 2 test.

### :app (chạm android/Context → KHÔNG phải file thuần)
- **`launcher/HomeViewModel.kt`** (androidx.lifecycle.ViewModel, 93 LOC) — `private val _uiState = MutableStateFlow(repo.load())`,
  `val uiState = _uiState.asStateFlow()`. Intent: `setPreset · assignApp · assignWidgets · clearSlot · swapSlots ·
  setDockEdge/cycleDockEdge · toggleDock · setThemeMode · switchProfile/addProfile/deleteProfile · setEmbedded`.
  `mutate{}` = `updateAndGet` + `repo.persist(next)` (ĐỒNG BỘ). Profile ops = `reload{}` (giữ `embedded`). `factory(context)` TẠM.
- **`launcher/PrefsWorkspaceRepository.kt`** (62 LOC) — bọc `WorkspacePrefs`; `defaultIfEmpty` (bố cục 3 widget khi trống,
  chuyển từ `initialState()` cũ của Activity vào tầng dữ liệu).
- **`launcher/WorkspaceView.kt`** — VIEW THUẦN: `setState`→**`render(state)`**; `currentState()` **XOÁ**; field `state`→
  `displayed` (view-transient diff cache, private, KHÔNG ai đọc ngoài). GIỮ VdAppHost/inputClient (B4) nguyên.
- **`launcher/KachiHomeActivity.kt`** (425 LOC) — `LifecycleOwner` qua `LifecycleRegistry` (drive ON_CREATE..ON_DESTROY);
  `lifecycleScope.launch { lifecycle.repeatOnLifecycle(STARTED){ uiState.collect{ render(it) } } }`. `render()` = MỘT chỗ áp
  state→view (diff `shownState`): `workspace.render` + `selectPreset` + `dock.setConfig`(+layoutMainArea khi đổi viền) +
  avatar + reflow (khi preset/viền đổi). Handler chỉ gọi intent + side-effect cửa sổ theo-ô (dispatcher registry + windows).
- **`launcher/LauncherWindows.kt`** (132 LOC, MỚI) — tách orchestration freeform + overlay caption khỏi Activity (để <500 LOC;
  KHÔNG phải view-component B5b). `reflow/placeApp/closeApp/updateOverlayHeads/seedLocations`; đọc state/embedding/shell/
  appLauncher/dispatcher qua provider (runtime đổi khi dadb nối). Logic freeform BYTE-GIỮ so với `reflowWindows/placeAppWindow` cũ.

## Bằng chứng "WorkspaceView hết giữ state" (grep app/src/main)
- `currentState` → **0**; `.setState(` → **0**; WorkspaceView `private var` chỉ còn `displayed`(diff) + `inputClient`(B4);
  `KachiHomeActivity` `prefs.`/`WorkspacePrefs` → chỉ trong 1 comment (mọi state qua VM). Public API = `fun render(WorkspaceState)`.

## Test (:app HomeViewModelTest 13 · Turbine + coroutines-test + Fake in-memory repo)
initial-load · initialEmbedded · assignApp(+persist) · setPreset · clearSlot · swapSlots · assignWidgets(multi + xoá) ·
switchProfile(reload) · addProfile · setThemeMode · setEmbedded(không persist) · cycleDockEdge · toggleDock. (+`WorkspaceStateTest` swap ×2.)

## Bảo toàn hành vi (đã đối chiếu từng handler)
render chạy trước posted-reflow (StateFlow phát trên Main.immediate; reflow qua `workspace.post` defer sau layout) =
tương đương ordering cũ. `embedding` getter GIỮ NGUYÊN (`shell!=null || SlotAppHost.embeddingUsable`); `HomeUiState.embedded`
mirror qua `setEmbedded` (init + probe) cho model đầy đủ. First render (prev==null) KHÔNG reflow (khớp onCreate cũ).

## HAND-OFF cho B5b (AppContainer DI + decompose god-activity)
- **`HomeViewModel.factory(context)` TẠM** → gộp vào **AppContainer** (cùng `SimpleCastRuntime`/`ShellTransport`/
  `WindowCommandDispatcher`/`InputDaemonClient` — xem stage-4 handoff) + `viewModelFactory{}` + `APPLICATION_KEY`.
  KachiHomeActivity chưa có ViewModelStore (Activity framework) → factory dựng trực tiếp, giữ field. B5b có thể chuyển
  `ComponentActivity` (androidx) để dùng `by viewModels()` + config-change survival — hoặc giữ LifecycleRegistry hiện tại.
- **Decompose view-component** (TopStrip/Dock/Drawer) khỏi KachiHomeActivity (hiện 425 LOC): buildTopStrip/buildSegmented/
  pill/chip/refreshChips/profileAvatar → `KachiTopStripView`; route callback → intent VM (đã sẵn seam một-chiều).
- `LauncherWindows` đã tách sẵn (freeform/overlay) — B5b có thể đưa vào object-graph nếu cần.
- Golden + cast + guard + LayeringRules PHẢI vẫn xanh; :core PHẢI vẫn pure (đừng để file thuần rơi vào :app).
