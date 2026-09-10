# W1 Stage 2 (W1b + W1d) — DONE off-car — Handoff

> Spec: `docs/specs/kachi-w1-real-data.html` (§9 Nhật ký · §10 Reviewer Log Pass 2) · Reads: `w1-stage1-done.md`
> Stage 2 = HAL binding + data flow (`StateFlow<CarStatus>`) + media bridge. **DONE off-car** 2026-09-10.
> Đọc file này trước khi làm **Stage 3** (UI render theo kind/tier + wire AppContainer→ViewModel + Tuỳ biến).

## 1. Files tạo / sửa

| File | Module | LOC | Nội dung |
|---|---|---|---|
| `launcher/HalBindingTable.kt` | **:core** | 259 | `HalGateway` (interface) · `sealed BindingRoute` · `routeOf`/`deviceFqn`/`coerce*` (thuần) · read/write dispatch + sentinel + per-index arg |
| `launcher/CarDataAdapter.kt` | **:core** | 132 | `CarDataPort` (6 method cũ) + `CarStatusReader` (readFast/readSlow build `CarStatus`) |
| `launcher/CarControlAdapter.kt` | **:core** | 39 | `CarControlPort` (toggle/step) + `cover/select/press/act` (route theo kind, KHÔNG gate) |
| `launcher/CarStatusRepository.kt` | **:core** | 74 | `CarStatusReader` (interface) + `CarStatusRepository` (poll 2 nhịp → `StateFlow<CarStatus>`) |
| `launcher/BydHalGateway.kt` | **:app** | 80 | `HalGateway` THẬT — bọc `BydHal` reflection + `AudioManager` (degrade-safe) |
| `launcher/MediaBridge.kt` | **:app** | 100 | `MediaSessionManager` → `MediaSnapshot` + transport (play/pause/next/prev) |
| `core/build.gradle.kts` | :core | — | +`api kotlinx-coroutines-core:1.10.2` + `testImplementation kotlinx-coroutines-test:1.10.2` |
| `launcher/{HalBindingTable,CarControlAdapter,CarDataAdapter,CarStatusRepository}Test.kt` + `FakeHalGateway.kt` | **:core test** | — | 37 test (19/9/5/4) + gateway giả |
| `launcher/MediaBridgeTest.kt` | **:app test** | — | 4 test (pick/toSnapshot) |

> **⚠ DEVIATION vị trí module** (spec §9): spec/plan ghi 4 file logic là `:app`, nhưng `LayeringRulesTest`
> khoá `pureFilesStillInApp == 0` — file `:app` không chạm Android bị coi là logic đặt sai chuồng (Q1) ⇒ FAIL.
> `HalBindingTable`/`CarDataAdapter`/`CarControlAdapter`/`CarStatusRepository` viết theo cổng-&-adapter (phụ thuộc
> interface `HalGateway`, test off-car) nên THUẦN ⇒ đúng luật phải ở **:core**. Chỉ `BydHalGateway` (BydHal
> reflection + Context + AudioManager) + `MediaBridge` (MediaSessionManager + Bitmap) chạm Android ⇒ **:app**.
> KHÔNG game guard. Package giữ `com.byd.clusternav.launcher` (chung tên qua module → import không đổi).

**KHÔNG đụng:** seal T11 · MainActivity · appId · logic cluster-cast (`SimpleCastRuntime` — AutoContainer KHÔNG wire) ·
input-daemon · registry :core Stage 1 (chỉ ĐỌC).

## 2. CONTRACT — `StateFlow<CarStatus>` (Stage 3 collect + so khớp shape)

- **Nguồn:** `CarStatusRepository(reader: CarStatusReader, scope: CoroutineScope, fastMs=1000, slowMs=10000)`.
  - `val status: StateFlow<CarStatus>` — nguồn sự thật LIVE cho HOME (Stage 3 collect qua `repeatOnLifecycle`).
  - `fun start()` — khởi 2 vòng poll (đọc NGAY 1 lần rồi delay → HOME có dữ liệu tức thì). `fun stop()` — huỷ sạch (idempotent).
- **Nhịp NHANH (~1s):** `drivetrain` (đầy đủ) + `energy.motorPowerKw` + `safety.{speedLimitWarning,bsdLeftLevel,bsdRightLevel}`.
- **Nhịp CHẬM (~10s):** `energy` (soc/range/sạc/pin/soh — GIỮ motorPowerKw của nhịp nhanh) + `climate` + `tyres` +
  `body` + `lights` + `safety` (belts/child/radar/esp/mcu/volt — GIỮ alert của nhịp nhanh) + `identity`.
- **`CarStatus`** (:core, bất biến, MỌI field nullable — `null` = chưa đọc/off-car/NEEDS_CAR ⇒ UI **"—"**). Shape ĐÚNG như
  `w1-stage1-done.md §2` (8 nested: energy/drivetrain/climate/tyres/body/lights/safety/identity). Ví dụ field đã build:
  - `energy`: soc:Int? · evRangeKm:Int? · fuelRangeKm:Int? · odometerKm:Int? · motorPowerKw:Int? · isCharging:Boolean? ·
    chargePowerKw:Double? · chargingPct:Int? · chargingEtaMin:Int? · chargedKwh:Double? · battTempC:Int? · sohPct:Int? · targetSoc:Int?
  - `drivetrain`: speedKmh:Int? · accelPct:Int? · brakePct:Int? · motorFrontRpm:Int? · steeringDeg:Int? · slopeDeg:Int? · gear:String? · opMode:String? · energyMode:String?
  - `climate`: pm25Level:Int? · pm25ValueUgm3:Int? · pm25Online:Boolean? · cabinTempC:Int? · outsideTempC:Int? · acOn:Boolean? · fanLevel:Int? · recircOn:Boolean? · anionOn:Boolean?
  - `tyres`: pFlKpa/pFrKpa/pRlKpa/pRrKpa:Double? (**kPa thô**) · tFlC/tFrC/tRlC/tRrC:Int?
  - `body`: windowLfPct/RfPct/LrPct/RrPct:Int? · doorLfOpen/RfOpen/LrOpen/RrOpen:Boolean? · tailgateOpen:Boolean? · sunroofPct:Int? · sunshadePct:Int? · mirrorFolded:Boolean? · powerLevel:Int? · vehicleType:String?
  - `lights`: lowBeam/highBeam/frontFog/drl:Boolean? · headlightMode:Int? · ambientOn:Boolean? · ambientColorIndex:Int? · ambientBrightness:Int?
  - `safety`: seatbeltDriver/Passenger:Boolean? · childPresence:Boolean? · speedLimitWarning:Boolean? · bsdLeftLevel/RightLevel:Int? · radarZones:List<Int>? · espOn:Boolean? · mcuStatus:Int? · volt12v:Double?
  - `identity`: vin:String? · keyState:String? · engineCode:String? · oilLevelPct:Int? · gpsLat/gpsLon:Double?
- **Off-car:** adapter thật đọc null ⇒ mọi field null ⇒ "—" (R3/OQ1, KHÔNG demo). ⚠ `w_energy`/`w_pm25`/`w_speed`/`w_tire`
  hiện đang đọc từ **`CarDataPort`** (6 method cũ — `DemoCarData`/`NoCar`); Stage 3 chuyển UI sang collect `carStatusFlow`
  (hoặc để `CarDataAdapter` cấp `CarDataPort`). Legacy `tirePressuresBar()` = **BAR** (đã ÷100 từ kPa).

## 3. CONTRACT — Media (`MediaBridge`, :app)

- `MediaBridge(context).read(): MediaSnapshot?` — null nếu off-car/không quyền.
- **`MediaSnapshot`**(`:app`): `title:String?` · `artist:String?` · `albumArt:Bitmap?` · `positionMs:Long` · `durationMs:Long` · `playing:Boolean`.
- **Transport (callback):** `play()` · `pause()` · `next()` · `prev()` — bám phiên `read()` chọn gần nhất; no-op nếu chưa có.
- Pure test-hook: `MediaBridge.pick(List<MediaLike>)` (ưu tiên đang-phát) + `MediaBridge.toSnapshot(MediaLike)`.
- Component: `MediaSessionManager.getActiveSessions(ComponentName(app, NavNotificationListener::class.java))`.

## 4. Binding table shape (Stage 3/reviewer tham chiếu)

- `HalBindingTable(gateway: HalGateway)` — đọc: `readInt/readDouble/readBool/readString/readIntList(id): T?` ·
  ghi: `write(id, primary): Long?` (rc; null=unavailable). `id` tra `TelemetryRegistry`/`ControlRegistry` (:core).
- `routeOf(bindingKey): BindingRoute` (thuần): `NamedMethod(fqn,method)` (BYDAuto…) · `Feature(id)` (decimal) ·
  `Setting(key)` (lowercase snake) · `Local(target,method)` (AudioManager/AutoContainer) · `None` (command-wrapper/GPS/UPPER_SNAKE → unavailable).
- `HalGateway` (interface :core) — impl thật `BydHalGateway` (:app). Sentinel `-2147482648`/`-2147482645` → unavailable.
- Control kind → args (write): TOGGLE 1/0 · STEP value · COVER 1/0 · SELECT index · BUTTON 1; named-method nhiều-arg
  (ghế `[seatId,state]`, kính `[window,state]`) tính trong `writeArgs`. `CarControlAdapter.act(id,arg)` = 1 điểm route theo kind.
- **KHÔNG gate** (write path không đọc speed/gear/permit) · feature-id chọn device theo `Domain` (best-effort, grab-list §9) ·
  car-setting + AutoContainer(cast) = null/no-op (grab-list / cast-owned).

## 5. [ĐO] verify (lệnh thật)
```
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :core:test              → BUILD SUCCESSFUL · 1015/0/0 (+37 Stage 2)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest  → BUILD SUCCESSFUL · 542/0/0 (+4 MediaBridge)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug      → BUILD SUCCESSFUL (sạch)
grep no-gate (speed/gear/permit) trong write path                        → chỉ CarDataAdapter ĐỌC (telemetry), write path SẠCH
```
- Test Stage 2: HalBindingTable **19** · CarControlAdapter **9** · CarDataAdapter **5** · CarStatusRepository **4** (:core) · MediaBridge **4** (:app) = **41**.
- LayeringRulesTest XANH (`pureFilesStillInApp=0`). Mỗi file ≤ 500 LOC (lớn nhất HalBindingTable 259).

## 6. Cho Stage 3 (gợi ý)
- **Wire AppContainer:** thêm `carData: CarDataPort = CarDataAdapter(HalBindingTable(BydHalGateway(app)))` ·
  `carControl: CarControlPort = CarControlAdapter(<cùng table>)` · `carStatusRepository = CarStatusRepository(CarDataAdapter(...), scope)`
  → phơi `carStatusFlow: StateFlow<CarStatus>`. **BỎ `DemoCarData`** (OQ1 — off-car "—"). Off-car vẫn dùng adapter thật (đọc null).
- **ViewModel/Activity:** collect `carStatusFlow` (repeatOnLifecycle) vào `HomeUiState`; `dock.control = CarControlAdapter`;
  `workspace.carData = CarDataAdapter` (hoặc render trực tiếp từ CarStatus).
- **Renderer:** widget theo `TelemetrySpec.widgetKind` + tile theo `ControlKind` (COVER/SELECT/BUTTON mới — hiện ControlDockView
  coi non-TOGGLE = STEP, cần render đúng kind + gọi `CarControlAdapter.cover/select/press`); badge tier OVERDRIVE/DASHCAST
  "chưa kiểm trên xe"; null/off-car → "—" + mờ.
- **Media:** feed `w_media` từ `MediaBridge.read()` + nút transport.
- 🚗 On-car (grab-list §9): dump jar → feature-id/method đúng trim · probe id ADAS/AC/sunroof/window-% · TPMS nhiệt lốp.
