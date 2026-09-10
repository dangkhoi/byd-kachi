# W1 Stage 3 (W1c + W1e) — DONE off-car — Handoff

> Spec: `docs/specs/kachi-w1-real-data.html` (§9 Nhật ký Stage 3 · §10 Reviewer Log Pass 3) · Reads: `w1-stage1-done.md` + `w1-stage2-done.md`
> Stage 3 = UI render generic theo shape/kind + wire `AppContainer→HomeViewModel→View` (UDF) + màn Tuỳ biến. **DONE off-car** 2026-09-10.
> Đọc trước khi làm **Stage 4** (senior review + security scan + docs sync).

## 1. Files tạo / sửa

| File | Module | Trạng thái | Nội dung |
|---|---|---|---|
| `launcher/TelemetryReadout.kt` | **:core** | tạo (162) | `TelemetryView` + `object TelemetryReadout.of(id, CarStatus)` — biên CarStatus→hiển thị: null⇒"—", `needsBadge` theo tier, `displayWithUnit()` |
| `launcher/LauncherCatalog.kt` | **:core** | tạo (75) | `WidgetPick` + `WidgetCatalog` (curated∪telemetry-theo-domain) + `ControlPanels.byDomain()` + `ControlTileLogic` (badge/nextSelectIndex/selectLabel) |
| `launcher/HomeUiState.kt` | :core | sửa | +`carStatus: CarStatus = CarStatus()` (live, runtime) |
| `launcher/LauncherPorts.kt` | :core | sửa | `CarControlPort` +`cover/select/press`; `NoCar` impl (off-car false) |
| `launcher/CarControlAdapter.kt` | :core | sửa | `cover/select/press` = `override` (đã có sẵn thân + test) |
| `launcher/WidgetViews.kt` | **:app** | sửa (339) | render từ `CarStatus`+`WidgetData`; generic telemetry theo `WidgetShape`; tier badge "chưa kiểm"+mờ null; media từ `MediaSnapshot` |
| `launcher/ControlDockView.kt` | :app | sửa (187) | 5 kind TOGGLE/STEP/COVER/SELECT/BUTTON → `carControl`; chấm amber tier; icon fallback theo domain |
| `launcher/WorkspaceView.kt` | :app | sửa | bỏ `carData` port → `carStatus` state + `mediaProvider`/`onMedia`; `render(ws, carStatus)` làm mới widget-slot (không đụng app-slot) |
| `launcher/CustomizePanel.kt` | **:app** | tạo (142) | overlay Tuỳ biến: nút dock gom theo `Domain`, ADAS/drive/HUD panel riêng, toggle→`viewModel.toggleDock` |
| `launcher/AppDrawer.kt` | :app | sửa | widget picker + mục telemetry gom domain (`WidgetCatalog`) |
| `launcher/KachiHomeActivity.kt` | :app | sửa | collect `carStatusRepository.status`(repeatOnLifecycle)→`setCarStatus`; `dock.control=carControl`; mở Tuỳ biến; media transport |
| `launcher/KachiTopStrip.kt` | :app | sửa | chip từ `CarStatus`; +pill "Tuỳ biến" (`onCustomizeDock`) |
| `launcher/HomeViewModel.kt` | :app | sửa | +`setCarStatus` (runtime, KHÔNG persist) |
| `AppContainer.kt` | :app | sửa | +`carData`/`carControl`/`carStatusRepository` (qua `carGatewayInit`+`carScope`); build()=`BydHalGateway(app)` |
| `launcher/TelemetryReadoutTest.kt` | :core test | tạo | 11 test (shape/display/badge/null/bool/double/list/unknown/full-registry) |
| `launcher/LauncherCatalogTest.kt` | :core test | tạo | 7 test (curated/telemetry-domain/pick/panels ADAS-riêng/select-cycle/badge/NoCar) |
| `AppContainerTest.kt` | :app test | sửa | +2 (car layer off-car "—"+no-op · lazy memoize) + `NullGateway` fake + `carGatewayInit` |
| `launcher/HomeViewModelTest.kt` | :app test | sửa | +1 (`setCarStatus` runtime, no persist) |

**KHÔNG đụng:** seal T11 · `MainActivity` · `appId` · logic cluster-cast (`SimpleCastRuntime`) · input-daemon · **XML layout** (UI thuần code → `LayoutVariantIdParityTest` XANH). `WidgetRegistry`/`WidgetRegistryTest` giữ nguyên (curated 8 widget).

## 2. Wiring `AppContainer → HomeViewModel → View` (UDF một chiều)

```
BydHalGateway(app)  ─(off-car reflection→null)
   │
   ▼
HalBindingTable ──┬── CarDataAdapter ──► CarStatusRepository.poll(2 nhịp) ──► StateFlow<CarStatus>
                  │        (carData)                                              │
                  └── CarControlAdapter (carControl)                              │
                                                                                  ▼
KachiHomeActivity: lifecycleScope.launch { repeatOnLifecycle(STARTED) {           │
    carStatusRepository.start()                                                   │
    status.collect { viewModel.setCarStatus(it) }  ◄──────────────────────────────┘
    finally { stop() } } }
   │
   ▼
HomeViewModel._uiState.copy(carStatus=…)  →  StateFlow<HomeUiState>
   │
   ▼
Activity.render(state): workspace.render(state.workspace, state.carStatus) · topStrip.refreshChips(state.carStatus) · dock.setConfig(state.dock)
   │
   ▼
WorkspaceView → WidgetViews.buildGrid(ids, WidgetData(carStatus, media, onMedia)) → TelemetryReadout.of(id,carStatus) → view theo shape
```

- **dock.control** = `AppContainer.carControl` (CarControlAdapter). Tile bấm → `carControl.toggle/step/cover/select/press` → HalBindingTable.write (KHÔNG gate; off-car false).
- **Media** đọc live ở :app (Bitmap ngoài state :core): `workspace.mediaProvider = { MediaBridge(this).read() }`, transport `onMedia`→`MediaBridge`.
- **Off-car**: gateway trả null ⇒ `CarStatus()` mọi field null ⇒ widget/chip "—" (OQ1, KHÔNG demo). `DemoCarData` GIỮ class nhưng KHÔNG trên wire.

## 3. Widget render theo `WidgetShape` (generic telemetry) + curated

- **Generic** (`id` telemetry, không phải `w_*`): `TelemetryReadout.of(id, carStatus)` → `TelemetryView` → render:
  - `RING` → `RingView` (số giữa + nhãn) · `DIAL`/`GAUGE`/`VALUE`/`CARD`/`BOARD`/`MEDIA` → số lớn + đơn vị + nhãn (`valueShape`) · `BADGE` → pill nhãn+giá trị · `STRIP` → nhãn + trạng thái.
  - null/off-car ⇒ `"—"` + `alpha 0.5`; tier `OVERDRIVE`/`DASHCAST` ⇒ badge nhỏ "chưa kiểm trên xe"; `PROVEN` ⇒ không badge.
- **Curated** (`w_*`, mặc định HOME): `w_energy`(ring soc/range) · `w_pm25`(ring µg/level) · `w_speed` · `w_tire`(kPa→bar 4 lốp) · `w_clock`(+nhiệt ngoài) · `w_media`(art/title/artist/progress/transport) · `w_car`(sơ đồ + cửa/cốp) · `w_board`(gộp) — tất cả đọc từ `CarStatus`, off-car "—".

## 4. Control tile render theo `ControlKind`

| kind | render | action |
|---|---|---|
| TOGGLE | icon+nhãn, bật=gradient | `carControl.toggle(id, on)` |
| STEP | icon + "− giá trị +" | `carControl.step(id, value)` (clamp min..max) |
| COVER | icon+nhãn + 2 nút Đóng/Mở (từ `args`) | `carControl.cover(id, open)` |
| SELECT | icon+nhãn + lựa chọn hiện tại, chạm=xoay vòng | `carControl.select(id, index)` (`ControlTileLogic.nextSelectIndex`) |
| BUTTON | icon+nhãn, nháy sáng momentary | `carControl.press(id)` |

- Tier OVERDRIVE/DASHCAST ⇒ chấm amber góc trên-phải. **KHÔNG gate** (mọi tile bấm được). Icon chưa map (ic-adas/ic-drive/ic-mirror) → fallback icon domain.

## 5. Bộ MẶC ĐỊNH (§4.2) + màn Tuỳ biến

- **Dock mặc định** = `ControlRegistry.defaultEnabledIds()` (8 nút: lock/window/trunk/readl/pm25/seatc/temp/fan = khí-hậu/ghế/kính/cốp/đèn-đọc). GIỮ nguyên (không đổi).
- **HOME widget mặc định** = `PrefsWorkspaceRepository.DEFAULT_WORKSPACE` (w_board/w_energy/w_pm25) — GIỮ.
- **Mở màn Tuỳ biến**: thanh trên (`KachiTopStrip`) có pill **"Tuỳ biến"** → `KachiHomeActivity.openCustomize()` → `CustomizePanel` overlay. Liệt kê MỌI nút gom theo `Domain` (ADAS=SAFETY / chế-độ-lái=DRIVETRAIN / HUD=INFOTAINMENT là **panel riêng có tiêu đề, KHÔNG ẩn** — OQ3); chạm nút = thêm/bớt khỏi dock → `viewModel.toggleDock` → `WorkspacePrefs` (bền). Nút "Xong"/chạm nền = đóng.
- **Thêm widget telemetry**: chạm ô (hoặc nút ⇄) → `AppDrawer` → cuộn tới mục domain (Năng lượng/Động lực/…) → chọn datum → đặt vào ô (`SlotContent.Widget`), render generic.

## 6. [ĐO] verify (lệnh thật)
```
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test :app:assembleDebug   → BUILD SUCCESSFUL (2m4s)
  → tổng 2299 test / 0 fail / 0 error (5 module)
JAVA_HOME=… ./gradlew :app:testDebugUnitTest --tests "*.LayeringRulesTest" → XANH (pureFilesStillInApp=0: 2 file pure mới ở :core)
grep -rn DemoCarData app/src/main core/src/main  → CHỈ dòng định nghĩa WidgetViews.kt (ngoài wire)
grep gate (speed/gear/permit) write path         → chỉ comment "KHÔNG gate" (0 logic gate)
git status: 0 file .xml/layout đổi                → LayoutVariantIdParityTest không ảnh hưởng (XANH trong full run)
```
- Test mới: `TelemetryReadoutTest` 11 · `LauncherCatalogTest` 7 (:core) · `AppContainerTest` +2 · `HomeViewModelTest` +1 (:app).
- Emulator render: CHƯA chạy phiên này (off-car burn); verify hình trên emulator/xe = việc sau (Stage 4 / on-car).

## 7. Cho Stage 4 (gợi ý)
- **Senior review**: boundary `HalBindingTable→StateFlow<CarStatus>→HomeViewModel→WidgetViews`(shape/tier) đã trace bằng `TelemetryReadoutTest`; `ControlRegistry(kind)↔ControlDockView↔CarControlAdapter` khớp; verify KHÔNG gate + off-car "—".
- **Security scan**: diff W1 (feature-id số + CAN opcode = FACTS, không flag). Chú ý PII trong fixture/ảnh (không thêm ảnh phiên này).
- **Docs sync**: `docs/README.md` INDEX + `PROJECT-BACKLOG.md` (#6 W1) + `.kiro/steering/project-context.md` + `CREDITS.md` (Overdrive/dashcast MIT).
- 🚗 On-car (grab-list spec §9): dump jar → feature-id/method đúng trim; probe id ADAS/AC/sunroof/window-%; TPMS nhiệt lốp.
