# RE Track 1 — `ICarHudService` / vision HUD API (app-callable?) — findings + handoff

> **Scope:** read-only reverse engineering. No code changed, nothing built/committed.
> **Global goal:** find every way to make the **ZIN / OEM windshield HUD** render **navigation** (today it shows only speed / speed-limit / ADAS / call — no nav).
> **This track:** the `com.byd.car.feature.vision.ICarHudService` / `ICarHudManager` DiCar-SPI service (the "vision HUD" API) — is it the nav-on-HUD lever, and can a 3rd-party app reach it?
> **WORKING DIR:** `<repo>` = `<project-root>-wt-speed-limit-badge-hal-hud` (git worktree của repo này)
> **RE cache:** `<re>` = `<user-cache>/clusternav-re`

## TL;DR verdict

- **`ICarHudService` is a HUD *settings/config* service, NOT a nav-content channel.** It can toggle HUD switch / mode / brightness / angle / height / theme and **enable-flags** for driving-fusion, safe-driving, **navigation-fusion (bit 1024)**, **navigation-map (bit 2048)**, **dynamic-navigation (bit 256)**. It has **no method that pushes a turn icon / distance / road name** — so it cannot, by itself, make the HUD *draw* nav. It can only turn the HUD's nav *feature* on; the content must still arrive over the instrument guide-info HAL.
- **Not reachable by `service call`** (it is not a `servicemanager`-registered binder). It is handed out by a **ContentProvider** `content://com.byd.car.server.provider.CarServiceProvider/sync_binder` in the system app `com.byd.car.server`, after an `ICarService.connect(packageName, version)` handshake. The server (`DiCarServer`) then **permission-gates every feature-id read/write** by `context.checkPermission(perm, caller.pid, caller.uid)` → `SecurityException`. ⇒ **designed for system/OEM-signed apps**; a 3rd-party app or `dadb` uid-2000 shell is expected to be blocked. Exact permission strings / provider `exported` not in the available artifacts (host `com.byd.car.server` not decompiled; the extracted `DiCarServer` build is version-skewed and has no vision `Stub`) → **confirm on-car**.
- **Even if callable, it hits the same wall the HAL track already hit.** `ICarHudService` routes to the same `DiCarServer → BYDAuto HAL → MCU` feature-ids that a prior on-car session already exercised (see `<repo>/docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` §19): HUD **mode/color** toggles worked, but nav **content** never rendered and the HUD **nav-map is coding-gated** at `0x38B00030` (not provisioned on this VN trim). So `setNavigationMapEnabled(true)` would hit the un-provisioned coding wall, and there is still no content push.
- **Net: Track 1 adds no new capability** for nav-on-ZIN-HUD on this trim; it is the typed wrapper over feature-ids already proven blocked. **One cheap on-car probe is still worth it** (diagnostic only): `getHudSupportedModes()` + `getHudConfig(NAVIGATION_MAP|NAVIGATION_FUSION|DYNAMIC_NAVIGATION)` to see whether the HUD even *advertises* nav support.

---

## Artifacts read (identities)

| Hash / path | What it is |
|---|---|
| `<re>/decoded/1d109…e16e4/` (`apkFileName: L3_new.apk`) | **DiLink launcher / system app** — bundles the BYD Car SDK (`com.byd.car`, incl. the `feature.vision` HUD package). SDK **client** only. |
| `<re>/decoded/e23be…e3862/` (Hud file header: `carsettings-apk/dex/classes.dex`) | **CarSettings (CCS, `com.byd.ccs`)** — hosts `Hud00600401300000` (HUD on/off entity). SDK **client** only. |
| `<re>/openbyd-2.3/` | **OpenBYD** community app — reference implementation of nav-on-cluster/HUD. |
| `<re>/sysimg/jadx-DiCarServer/` | **`DiCarServer` (`com.byd.car.server`)** from extracted `system.img` — the DiCar **server** (permission model + HAL routing). Older build → **no vision `ICarHudService.Stub`** (version skew vs L3_new SDK). |

---

## 1) API surface — which methods/bits = NAV

**`HudFeature.FeatureMask` bits** — `<re>/decoded/1d109…e16e4/jadx-auto/sources/com/byd/car/feature/vision/HudFeature.java`:
```
W_HUD_SYSTEM = 1        AR_HUD_SYSTEM = 2       HEIGHT_ADJUSTMENT = 4
BRIGHTNESS_ADJUSTMENT=8 ANGLE_ADJUSTMENT = 16   THEME_SELECTION = 32
MODE_SELECTION = 64     DRIVING_FUSION = 128     ← nav-adjacent
DYNAMIC_NAVIGATION = 256   ← NAV                 SAFE_DRIVING = 512
NAVIGATION_FUSION = 1024   ← NAV                 NAVIGATION_MAP = 2048  ← NAV
ADAPTIVE_ADJUSTMENT = 4096 LINKAGE_DRIVER_SEAT_ADJUSTMENT = 8192
STEERING_WHEEL_CONTROL_ADJUSTMENT = 16384
```
(`HudFeature.java:6-20`; `@interface FeatureMask` at `:22-24`.)

**NAV-related methods** — `.../vision/ICarHudManager.java` (mirrored in `ICarHudService.java`):
| Method | Meaning | line (`ICarHudManager.java`) |
|---|---|---|
| `Status setNavigationMapEnabled(boolean)` | enable HUD **navigation map** (bit 2048) | `:47` |
| `Status setNavigationFusionEnabled(boolean)` | enable **navigation fusion** (bit 1024) | `:46` |
| `Status setDynamicNavigationEnabled(boolean)` | enable **dynamic navigation** (bit 256) | `:39` |
| `Status setDrivingFusionEnabled(boolean)` | enable driving fusion (bit 128) | `:38` |
| `Status setHudMode(HudMode)` | SIMPLE/STANDARD/OFF_ROAD | `:44` |
| `Status setHudSwitchEnabled(boolean)` | master HUD on/off | `:45` |
| `Result<Boolean> isNavigation{Map,Fusion}Enabled()`, `isDynamicNavigationEnabled()` | read state | `:24,26,20` |
| `Result<List<HudMode>> getHudSupportedModes()` / `Result<Integer> getHudConfig(int featureMask)` | **capability read** | `:14,12` |

**`HudMode`** = `SIMPLE(1), STANDARD(2), OFF_ROAD(3)` (`HudMode.java:5-8`). **`HudTheme`** = `CLASSIC(1), SNOW(2)`. **`HudAngle`** = −2.0…+2.0 in 0.4 steps.

**⚠️ There is NO content-push method.** The full interface (`ICarHudService.java:? — the `Status enableHudAdaptive()` … `setSteeringWheelControlAdjustmentEnabled(boolean)` block) exposes only switch/mode/brightness/angle/height/theme + the enable-flags above. **No `setTurnIcon`, `setDistance`, `setRoadName`, `sendGuidance`.** ⇒ enabling `NAVIGATION_MAP/FUSION/DYNAMIC` only flips the HUD *feature*; the turn-by-turn/​map content must be fed by the OEM nav via the instrument HAL (see §5).

**Vision events** (`event/VisionEventIds.java:36-51`): `HudEvents` includes `NAVIGATION_MAP_ENABLED_CHANGED`, `NAVIGATION_FUSION_ENABLED_CHANGED`, `DYNAMIC_NAVIGATION_ENABLED_CHANGED`, `HUD_MODE_CHANGED`, `SUPPORTED_MODES_CHANGED` — i.e. these are *config-changed* notifications, reinforcing that this is a settings surface.

---

## 2) Who may call — permission / signature / uid

**SDK stub has no gate of its own.** `ICarHudService.Stub.onTransact` only does `data.enforceInterface(DESCRIPTOR)` for every case (1–29) and calls straight through — no `checkCallingPermission` / uid / signature (`ICarHudService.java`, `onTransact` switch). DESCRIPTOR = `"com.byd.car.feature.vision.ICarHudService"`. (That is expected: the AIDL skeleton is client-side; enforcement is server-side.)

**Transport = a ContentProvider, not `servicemanager`.**
- Manager `j2` (= `CarHudManager`, `@ServiceImpl(service=ICarHudManager.class)`) resolves the binder for **every** call via `Spi.getService(ctx, ICarHudService.class)` — `car/j2.java:34-…` (e.g. `setNavigationMapEnabled` → `((ICarHudService) Spi.getService(this.a, ICarHudService.class)).setNavigationMapEnabled(enabled)`).
- `RemoteServiceManager.queryRemoteBinder` does `resolver.query(contentUri, null, null, new String[]{serviceCanonicalName}, null)` and reads the live `IBinder` out of the cursor extras, then `ICarHudService.Stub.asInterface(binder)` — `com/byd/spi/ipc/RemoteServiceManager.java:36,66-84,88-96`.
- The provider URI is fixed: `content://com.byd.car.server.provider.CarServiceProvider/sync_binder` (single-OS) or `content://0@com.byd.car.server.provider.CarServiceProvider/sync_binder` (multi-display user) — `car/n2.java:29-30`, authority string `:26`. The provider itself is a `com.byd.spi.ipc.provider.BinderProvider` whose `query()` returns `Spi.getService(...)` as an `IBinder` (`com/byd/spi/ipc/provider/BinderProvider.java:42-53`).
- Init also performs a **handshake**: `n2.a()` → `f()` (`Spi.init` with the content URI + `ServiceManager.init()`) → `a()` calls `ICarService.connect(this.f.context.getPackageName(), BuildConfig.DICAR_VERSION)` (`car/n2.java:` `a()` method) and every proxy call is wrapped with `(DICAR_VERSION, serverVersion, callerPackageName, target)` via `q2` (`car/n2.java` `a(Class,Object)`), so the **caller package identity is carried to the server**. Client init is trivial: `DiCar.init(new DiCarConfig.Builder(ctx).build())` (`com/android/launcher3/LauncherApplication.java:34`), only a `Context` needed (`DiCarConfig.java`).

**Server-side gate (authoritative) — `DiCarServer`.** Every feature-id read/write is permission-checked against the caller pid/uid:
- `HalFeatureController.setProperties(ctx, caller, …)` calls `PermissionUtils.checkCallingPermissions(context, 2, caller, configs)` **before** touching the HAL device; `getProperties(...)` uses access `1` — `com/byd/auto/controller/HalFeatureController.java` (`setProperties` and `getProperties` bodies).
- `PermissionUtils.checkCallingPermissions` reads each `CarPropertyConfig`'s read/write (and optional *dangerous*) permission and calls `context.checkPermission(permission, caller.pid, caller.uid)`; **throws `SecurityException` if not `== 0`** — `com/byd/car/utils/PermissionUtils.java:11-27`, `checkCallingPermission:29-31`.

⇒ **Who can call:** system / OEM-signed apps that (a) can query `CarServiceProvider`, (b) pass `ICarService.connect`, and (c) hold the per-feature-id BYD permissions (`signature|privileged`-class). CarSettings and the launcher qualify. **A generic 3rd-party app or `dadb` uid-2000 shell is expected to fail** at the provider export/permission and/or the per-feature permission. The **exact** HUD-nav permission names and the provider's `android:exported`/`protectionLevel` are **not** in these artifacts (the host `com.byd.car.server` isn't decompiled; the extracted `DiCarServer` predates the vision `Stub`) → **must be verified on-car** (`dumpsys package providers | grep CarServiceProvider`; then a probe query).

---

## 3) Reachable from an app (dadb uid-shell / service call / binder)?

- **`service call` — NO.** `ICarHudService` is not registered in the global `servicemanager`; it is vended by the `CarServiceProvider` ContentProvider. `service list` will not show a "hud" service, so `service call hud <code> …` cannot target it. (Transaction codes exist — `setHudSwitchEnabled=4`, `setHudMode=14`, `setSafeDrivingEnabled=16`, `setDrivingFusionEnabled=18`, `setDynamicNavigationEnabled=20`, `setNavigationFusionEnabled=22`, `setNavigationMapEnabled=24`, `enableHudAdaptive=25`, per `ICarHudService.java` `TRANSACTION_*` — but they are only usable on a binder you already hold.)
- **ContentProvider path — theoretically, but gated.** A shell/app could try `content query --uri content://com.byd.car.server.provider.CarServiceProvider/sync_binder --bind …` / `--selection-arg com.byd.car.feature.vision.ICarHudService` to fetch the binder. But (a) `CarServiceProvider` almost certainly enforces `exported`/permission, and (b) even holding the binder, each setter re-enters the server-side per-feature-id `checkPermission(caller.uid)` (§2). `dadb` uid = shell (2000), which does **not** hold BYD signature permissions.
- **Empirical ceiling (decisive) — prior on-car, `<repo>/docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` §19:** the SAME underlying HAL feature-ids were driven on-car from a privileged `app_process` context (`navopen`): HUD **mode/color** changed (`SET_HUD_MODE 0x4C10E025`), but **nav content never rendered** on the HUD (`sendSimpleGuidanceInfo` rc=0, HUD unchanged), and the HUD **nav-map is coding-gated** (`0x38B00030` not provisioned → `set 0x32B1102E` rejected). Because `ICarHudService` terminates in these same `DiCarServer → HAL` feature-ids, it **inherits this ceiling**: callable-or-not, `setNavigationMapEnabled(true)` still meets the un-provisioned coding wall, and there is no content-push method to add.

---

## 4) `Hud00600401300000` (CCS / CarSettings) — how it drives the HUD

File: `<re>/decoded/e23be…e3862/jadx-auto/sources/com/byd/ccs/impl/server/hud/Hud00600401300000.java`.

- It is the CarSettings **HUD master on/off** entity, driven purely by the **HAL property layer** (`DiCarGetter`/`DiCarSetter` + `ICarPropertyManager`) — **not** `ICarHudService`/`ICarHudManager`:
  - `getState()` → `DiCarGetter.get("0x38B0002E")`, HUD "on" when `== 2`.
  - `setState(bool)` → `DiCarSetter.set("0x32B1102E", on?2:1)` (write id differs from read id).
  - `readSelfLearnState()` → `DiCarGetter.get("0x38B00030")`, true when `== 1` — the **HUD nav-map provisioning / self-learn (present) flag**.
  - `registerListener()` → `ICarPropertyManager.registerValueCallback(…, "0x38B0002E")`.
- **No nav-content and no nav-specific gate here** — it is just the HUD power switch + the `0x38B00030` provisioning flag. The `0x38B00030` "self-learn" flag is exactly the coding gate called out in `<repo>/README.md` ("windshield HUD needs vehicle coding `0x38B00030=1`"). Nav *content* is not routed through this entity at all.
- Cross-ref server feature-ids (`<re>/sysimg/jadx-DiCarServer/.../instrument/Instrument.java`): `INSTRUMENT_GUIDE_INFO_SIMPLE_SET = 0x43F01010` (`:515`, the turn-by-turn content push), `INSTRUMENT_DYNAMIC_NAVI_FUNCTION = 0x38B0002A` (`:457`), `INSTRUMENT_ARHUD_NAVIGATION_MAP_RESOLUTION_STATUS = 0x34C0000B` (`:183`), `INSTRUMENT_ATOM_HUD_SWITCH = 0x0780E026` (`:184`).

---

## 5) OpenBYD `HudController` / `HudSetupHelper` — how they set the HUD

Files: `<re>/openbyd-2.3/sources/com/sr/openbyd/services/{HudController,HudSetupHelper}.java`.

- **`HudController` does NOT call `ICarHudService`/`ICarHudManager` at all.** It pushes nav **content** through `BYDAutoInstrumentDevice` via its `ICarControl` proxy (`ProxyManager.INSTANCE.getCarControl()`):
  - `turnOnNavi()` / `turnOffNavi()` / `getNaviStatus()` (`NAVI_STATUS_ACTIVE = 2`) — `ensureHudActive()`, `closeNavigation()`.
  - `sendSimpleGuidanceInfo(iconId, distance)` — the turn icon + distance push (`updateNavigation()`), plus `sendSecondaryGuidanceInfo`, `sendNextPathName(str)`, `sendRestRouteInfo(h,m,mileage)`.
  - imports `android.hardware.bydauto.instrument.BYDAutoInstrumentDevice` (uses `REST_MILEAGE_MAX`) — i.e. the **`INSTRUMENT_GUIDE_INFO_SIMPLE_SET (0x43F01010)`** HAL path.
  - Secondary path: an AMAP standard broadcast `AUTONAVI_STANDARD_BROADCAST_SEND` (`sendStandardAmapBroadcast()`), gated by pref `hud_amap_broadcast_enabled`.
- **`HudSetupHelper`** only toggles the notification listener via a shell command — `ShellCommandExecutor.execute("cmd notification allow_listener com.sr.openbyd/…MapNotificationListenerService")` (and `disallow_listener` to reset) — to scrape nav from GMaps/Yandex notifications. Nothing HUD-service related.

⇒ A working community app on the same platform confirms the **content path is the instrument-guide HAL**, and it deliberately does **not** touch the vision HUD service. This matches the CN app's own approach (README: `Maneuver.toHudIcon()` → `INSTRUMENT_GUIDE_INFO_SIMPLE_SET`).

---

## Answers to the 5 questions (compact)

1. **Nav on/off methods & bit:** `setNavigationMapEnabled` (bit `NAVIGATION_MAP=2048`), `setNavigationFusionEnabled` (`1024`), `setDynamicNavigationEnabled` (`256`), plus `setHudMode`/`setHudSwitchEnabled`. **Enable/config only — no content push.**
2. **Who can call:** system/OEM-signed apps via `CarServiceProvider` + `ICarService.connect` + per-feature `context.checkPermission(caller.uid)` in `DiCarServer` (`PermissionUtils.java:11-31`). **3rd-party/​shell expected to be blocked** (exact perm/exported = confirm on-car).
3. **App/dadb reach:** **not** via `service call` (not a servicemanager binder); only via the gated ContentProvider + gated feature-ids. dadb uid-2000 lacks the BYD permissions. And the underlying feature-ids were already proven inert-for-content / coding-gated on-car ⇒ **effectively NO** for making the HUD show nav.
4. **`Hud00600401300000`:** CarSettings HUD **master on/off** over HAL props `0x38B0002E`(status)/`0x32B1102E`(set)/`0x38B00030`(provisioning self-learn). No nav content, no nav gate — pure power switch + the coding flag.
5. **OpenBYD:** does **not** use `ICarHudService`; drives nav via `BYDAutoInstrumentDevice.sendSimpleGuidanceInfo` (= `INSTRUMENT_GUIDE_INFO_SIMPLE_SET 0x43F01010`) + AMAP broadcast.

## App-callable? **CÓ/KHÔNG**

- **KHÔNG (practically), for making the ZIN HUD show nav.** Reasons, in order of certainty: (a) no content-push method exists → the service can only enable a feature; (b) it is not a `service call` target and its provider + feature-ids are permission-gated to system/OEM callers; (c) the nav-map it would enable is coding-gated (`0x38B00030`) and proven un-provisioned on this trim, and guide-info content proven not to render on the HUD on-car.
- **Conditions that would flip it to CÓ (all off-car / privileged):** the app is **system/OEM-signed and holds the HUD-nav BYD permissions** AND `CarServiceProvider` is reachable **AND** the HUD is **provisioned** (`0x38B00030=1` via dealer/UDS coding) **AND** a nav source feeds `INSTRUMENT_GUIDE_INFO_SIMPLE_SET`. Missing any one → no nav on HUD.

## Suggested on-car probe (diagnostic only, if a privileged context is available)

Via a privileged `app_process`/DiCar client (e.g. navopen-style): resolve `ICarHudManager` and read **capabilities** — `getHudSupportedModes()`, `getHudConfig(NAVIGATION_MAP)`, `getHudConfig(NAVIGATION_FUSION)`, `getHudConfig(DYNAMIC_NAVIGATION)`, `isNavigationMapEnabled()`. If the HUD reports **no** nav support/modes → confirms the vision path is a dead end on this trim (aligns with the `0x38B00030` coding wall). Also `dumpsys package providers | grep -i CarServiceProvider` to read the provider's `exported`/permission. **No writes** needed for this determination.

## Cross-references
- `<repo>/docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` (§10, §16, §19 — the HAL feature-id family, the `0x38B00030` coding gate, and the on-car result that content does not render on the HUD).
- `<repo>/README.md` — "windshield HUD needs a vehicle coding flag `0x38B00030=1`, not an app change".
