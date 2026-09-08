# Removal Manifest — Waze‑Mod nav source + VietMap/Waze speed‑limit SIGNAL (ClusterNav 1.22)

> **Type:** RESEARCH ONLY — no code was modified to produce this. Every entry is backed by grep/read
> evidence with `file:line` citations. This is the implementation plan for the removal.
>
> **Owner decision:** Remove the **Waze‑Mod‑as‑nav‑source** and the **VietMap/Waze SPEED‑LIMIT SIGNAL**
> features entirely (they don't work; the speed ports are `Noop` = do‑nothing; `WazeHudSource` polls
> logcat via the dadb shell every 900 ms ≈ 4000×/hr, draining the head unit). **Keep only what works.**
>
> **Author:** Đăng Khôi · `dangkhoi` — 2026‑08‑15

---

## 0. Evidence method

- Fuzzy file discovery: `glob **/*{ietmap,azehud,peedSign,peedLimit,...}` → 33 seed files.
- Symbol tracing (both directions) with `grep` on:
  `WazeHudSource · NavigationSpeedSignOwner · SpeedSignLifecycleCoordinator · SpeedSignPorts ·
  CarExecSpeedSignCatalog · SpeedLimitFrame · SpeedLimitEnums · VietMapWidget · vietmapwidget ·
  VietmapBubbleExperiment · SpeedLimitSource · FreshnessState · MonotonicFreshness · SpeedSignOutput ·
  NoopSpeedSignPort · hlpTurnToManeuver`.
- Full reads of every EDIT target and every ambiguous file.

**Two corrections to the task brief (evidence‑based):**
1. **`hlpTurnToManeuver` is NOT in `Maneuver.kt`.** It is defined in `WazeHudSource.kt` companion
   (`app/.../modules/wazehud/WazeHudSource.kt:52`) and is used only inside that file's `toNavState`.
   It dies with `WazeHudSource.kt`. **`Maneuver.kt` needs NO edit** (it is core GMaps‑path code).
2. **`README.md` has no current‑feature copy for these features.** `grep` on the root `README.md`
   returns only T10‑probe/status lines (`README.md:4`, `README.md:55`), not Waze‑nav‑source or
   speed‑limit feature descriptions. Only an optional 1.22 changelog note is needed (see §2).

---

## 1. DELETE — whole files (29 files: 19 main + 10 test)

For each group I confirmed (grep) that no KEEP file `import`s the symbol except the ones listed in §2/§4.

### 1a. Waze‑Mod nav source (`modules/wazehud`)
| File | Evidence it's isolated |
|---|---|
| `app/src/main/java/com/byd/clusternav/modules/wazehud/WazeHudSource.kt` | Only importers: `NavNotificationListener.kt:20‑21` (→ §2 EDIT). Contains `hlpTurnToManeuver` + `WazeHudState` + `WazeHudAvailability`. |
| `app/src/test/java/com/byd/clusternav/modules/wazehud/WazeHudSourceTest.kt` | Self‑contained (`WazeHudSourceTest.kt:21` `WazeHudSource { null }`). |

*(After deletion the `modules/wazehud/` dir is empty.)*

### 1b. VietMap widget speed path — app (`vietmapwidget`, 8 main + 3 test)
| File |
|---|
| `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetBridge.kt` |
| `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetExtraction.kt` |
| `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetDiagActivity.kt` |
| `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetSlotExt.kt` |
| `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetClearStateMachine.kt` |
| `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetBindModels.kt` |
| `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetPrefs.kt` |
| `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapAppWidgetHost.kt` |
| `app/src/test/java/com/byd/clusternav/vietmapwidget/VietMapWidgetBridgeLifecycleTest.kt` |
| `app/src/test/java/com/byd/clusternav/vietmapwidget/VietMapWidgetClearStateMachineTest.kt` |
| `app/src/test/java/com/byd/clusternav/vietmapwidget/VietMapWidgetGenerationBindingTest.kt` |

Sole external importer of app `vietmapwidget` is `NavNotificationListener.kt:17‑19`
(`VietMapWidgetBridge`, `VietMapWidgetFreshness`, `VietMapWidgetOwner`, `VietMapWidgetSnapshot`) → §2 EDIT.
`VietMapWidgetDiagActivity` is also referenced by `MainActivity.kt:4` + `AndroidManifest.xml` → §2 EDIT.

### 1c. VietMap widget speed path — core (`vietmapwidget`, 3 main + 2 test)
| File | Notes |
|---|---|
| `core/src/main/kotlin/com/byd/clusternav/vietmapwidget/VietMapWidgetOwner.kt` | enum `NAVIGATION` owner token |
| `core/src/main/kotlin/com/byd/clusternav/vietmapwidget/VietMapWidgetModels.kt` | also defines `SpeedSignClearState` / `SpeedSignClearTrigger` (used only by the deleted app `VietMapWidgetClearStateMachine`) + `VietMapWidgetSnapshot`/`VietMapWidgetFreshness` |
| `core/src/main/kotlin/com/byd/clusternav/vietmapwidget/VietMapWidgetTextParser.kt` | `parseSpeedLimit`, `parseCurrentSpeed` — only vietmapwidget callers |
| `core/src/test/kotlin/com/byd/clusternav/vietmapwidget/VietMapWidgetTextParserTest.kt` | |
| `core/src/test/kotlin/com/byd/clusternav/vietmapwidget/VietMapWidgetProviderIndependenceTest.kt` | |

### 1d. Speed‑sign runtime — app + core
| File | Notes |
|---|---|
| `app/src/main/java/com/byd/clusternav/NavigationSpeedSignOwner.kt` | Android facade; importers: `NavNotificationListener.kt:52`, `MainActivity.kt:40` → §2 EDIT |
| `app/src/test/java/com/byd/clusternav/SpeedSignSourceLifecycleTest.kt` | pins `NavigationSpeedSignOwner.kt` source text |
| `core/src/main/kotlin/com/byd/clusternav/navigation/SpeedSignLifecycleCoordinator.kt` | + fun‑ifaces `SpeedSignScheduler`/`SpeedSignScheduledTask` + `SpeedSignLifecycleSnapshot` (all local) |
| `core/src/main/kotlin/com/byd/clusternav/navigation/SpeedSignPorts.kt` | `SpeedSignOutput · SpeedSignPort · RecordingSpeedSignPort · NoopSpeedSignPort · SpeedSignSubmission` |
| `core/src/test/kotlin/com/byd/clusternav/navigation/SpeedSignLifecycleCoordinatorTest.kt` | |
| `core/src/test/kotlin/com/byd/clusternav/navigation/NavigationSpeedSignRaceTest.kt` | |

> ⚠️ **`SpeedSignOutput` is imported by two KEEP files** — `MainActivity.kt:24` and (transitively) the
> mixed test `NavigationOutputIsolationTest.kt`. Both are handled in §2. Do **not** delete `SpeedSignPorts.kt`
> before those edits, or the build breaks.

### 1e. Speed‑sign catalog — core carexec (T10 probe research)
| File | Notes |
|---|---|
| `core/src/main/kotlin/com/byd/clusternav/carexec/CarExecSpeedSignCatalog.kt` | `internal object`; aggregated by `CarExecCatalog.kt:43` → §2 EDIT. Defines steps `sign-inventory`, `sign-watch-live`, `sign-consumer`, `sign-source-vietmap`, `sign-mute-camera`, `sign-inject`, `sign-stale-guard` (+ candidates). |

*No dedicated test file — its ids are asserted in `CarExecCatalogTest.kt:85‑109` → §2 EDIT.*
**This is part of the separate T10/T11 research catalog — see §4 (Cascade C) and §5 for the risk.**

### 1f. Neutral speed contracts — `vehicle-contracts`
| File | Notes |
|---|---|
| `vehicle-contracts/src/main/kotlin/com/byd/clusternav/contracts/SpeedLimitFrame.kt` | `MonotonicFreshness` + `SpeedLimitFrame` |
| `vehicle-contracts/src/main/kotlin/com/byd/clusternav/contracts/SpeedLimitEnums.kt` | `SpeedSignType · SpeedLimitType · SpeedUnit · SpeedLimitSource · FreshnessState · SpeedLimitClearReason` |
| `vehicle-contracts/src/test/kotlin/com/byd/clusternav/contracts/SpeedLimitFrameTest.kt` | |

`contracts/` holds **only** these two source files. The `:vehicle-contracts` module **stays** (its
`vehicle/t10/` package is independent — verified it does **not** import `contracts.SpeedLimit*`).
`SpeedLimitSource` is imported by KEEP files `Prefs.kt:4` and `NavNotificationListener.kt:15` → §2 EDIT.

### 1g. Orphaned VietMap cast experiment
| File | Notes |
|---|---|
| `app/src/main/java/com/byd/clusternav/modules/clustercast/VietmapBubbleExperiment.kt` | **Verified ORPHANED** — the only occurrence of the symbol in `app/src` is its own declaration (`VietmapBubbleExperiment.kt:22`). `bind()`/`runOnAppStart()`/`trigger()` are **never called** anywhere; no test. It is the VietMap *experiment* probe (opens `vn.vietmap.live`, casts it, backgrounds it — separate `SharedPreferences`, "TÁCH BIỆT HOÀN TOÀN khỏi CastAutomation"), **not** core cast. Clean delete, **no edits elsewhere**. |

---

## 2. EDIT — remove refs, keep the file

### 2.1 `app/src/main/java/com/byd/clusternav/NavNotificationListener.kt`  *(major — KEEP the GMaps nav path)*
Remove **imports**:
- `:15` `import com.byd.clusternav.contracts.SpeedLimitSource`
- `:17` `import ...vietmapwidget.VietMapWidgetBridge`
- `:18` `import ...vietmapwidget.VietMapWidgetFreshness`
- `:19` `import ...vietmapwidget.VietMapWidgetOwner`
- `:20` `import ...modules.wazehud.WazeHudSource`
- `:21` `import ...modules.wazehud.WazeHudAvailability`
- `:13` `import android.os.SystemClock` — **remove only if** no other reference remains (it is used solely by the speed‑sign/waze blocks being deleted).

Remove **fields / members**:
- `:52` `private val speedSignOwner by lazy { NavigationSpeedSignOwner.get(...) }`
- `private val speedLimitPusher: (VietMapWidgetSnapshot) -> Unit = { ... }` (whole lambda)
- `private var wazeHudSource: WazeHudSource? = null`
- `private fun startWazeHudSource() { ... }` (whole function — contains the `SimpleCastRuntime...executeShell` poll wiring, the `com.chisadin.wazemod` arbiter branch, `ClusterBroadcaster.selectSource/emitLane/emitHud` for Waze)
- `private fun stopWazeHudSource(...) { ... }` (whole function)

Remove **statements inside lifecycle callbacks** (keep the rest of each callback):
- `onListenerDisconnected()`: `:63` `onProviderDisconnected(VIETMAP)`, `:64` `onProviderDisconnected(WAZE)`, `stopWazeHudSource(...)`, `bridge.stop(...)`, `:68` `bridge.removeListener(speedLimitPusher)`. **Keep** the `NavRepository.setPermission(UNKNOWN)` + `requestRebind` lines.
- `onListenerConnected()`: `speedSignOwner.syncFromPrefs()`, `bridge.start(...)`, `bridge.addListener(speedLimitPusher)`, `startWazeHudSource()`. **Keep** `connected=true`, the `Prefs.enabled` gate, `SourceArbiter.clear()`, `setPermission(GRANTED)`, and the active‑notification scan.
- `onDestroy()`: `:101` `onSourceStopped(VIETMAP)`, `onSourceStopped(WAZE)`, `stopWazeHudSource(...)`, `bridge.stop(...)`, `bridge.removeListener(...)`.
- `ensureBridgeStarted()`: remove `bridge.*` + `startWazeHudSource()` → the method collapses to `if (connected) return; connected = true` (or inline it; the safety‑net is now just the connected flag).

Remove from **`MAPS_PACKAGES`** companion set (`:~44‑48`):
- `"com.chisadin.wazemod"`, `"com.waze"` (Waze nav source — in scope).
- `"vn.vietmap.live"` — **DECISION (see §6 Open Q1).** Recommended: **remove** for a clean GMaps‑only "keep only what works" (VietMap turn‑by‑turn *notification* nav was never offered in the nav‑source spinner). Keep only `com.google.android.apps.maps` + `app.revanced.android.apps.maps`.

**Untouched in this file:** the entire `handle()` GMaps path, `NavArrivalGuard`, `NavParse`, `NavFormat`,
`SourceArbiter`, `TurnDistanceInterpolator`, `ClusterBroadcaster`, `ClusterNavLaneWidget`, `NavRepository`,
`NotificationParser`, arrival/route‑end/distance‑regression guards.

### 2.2 `app/src/main/java/com/byd/clusternav/MainActivity.kt`
- `:4` remove `import ...vietmapwidget.VietMapWidgetDiagActivity`
- `:24` remove `import com.byd.clusternav.navigation.SpeedSignOutput`
- `:40` remove `private val speedSign by lazy { NavigationSpeedSignOwner.get(...) }`
- `:65` remove `speedSign.syncFromPrefs()`
- `:70` remove `speedSign.onMasterEnabled(enabled)`
- `:75` remove `speedSign.onOutputEnabled(SpeedSignOutput.CLUSTER, true)` (inside the enable branch)
- `:~108` remove `speedSign.onOutputEnabled(SpeedSignOutput.CLUSTER, true)` (the startup re‑assert block; **keep** the adjacent `NavRepository.setOutputEnabled(..., CLUSTER_LANE, true)`)
- `:~117` remove `speedSign.onOutputEnabled(SpeedSignOutput.HUD, false)` (**keep** `Prefs.setHud(this,false)` + `NavRepository.setOutputEnabled(..., HUD, false)`)
- **Speed‑source spinner block** (anchor `// Speed + Alert source selector`, ~`:133‑152`): remove the entire block — `findViewById(R.id.spinner_speed_source)`, `speedSources`, `speedSourceModes` (`SPEED_VIETMAP`/`SPEED_WAZE`), adapter, listener, and `speedSign.onSourceSelected(...)`.
- **Nav‑source spinner block** (anchor `// Navigation source selector`, ~`:120‑131`): drop the Waze option — change `navSources = arrayOf("Tự động...", "Google Maps", "Waze Mod")` → `arrayOf("Tự động (app dẫn trước)", "Google Maps")` and `navSourceModes = intArrayOf(Prefs.AUTO, Prefs.PREFER_GMAPS, Prefs.PREFER_WAZE)` → `intArrayOf(Prefs.AUTO, Prefs.PREFER_GMAPS)`. *(Or make GMaps‑only if Open Q1 = GMaps‑only.)*
- **`btn_vietmap_widget_diag` click** (anchor `findViewById<Button>(R.id.btn_vietmap_widget_diag)`, ~`:285`): remove the listener + the `startActivity(Intent(this, VietMapWidgetDiagActivity::class.java))`.

**Untouched:** `spinner_cluster_mode` (nav display mode — KEEP), voice‑key controls, cast controller,
headless‑autostart, marquee, reconnect/stop buttons.

### 2.3 `app/src/main/java/com/byd/clusternav/Prefs.kt`
- `:4` remove `import com.byd.clusternav.contracts.SpeedLimitSource`
- `:~11` remove `const val PREFER_WAZE = ...NavSourceMode.PREFER_WAZE` and `:~12` `const val PREFER_VIETMAP = ...NavSourceMode.PREFER_VIETMAP`
- `K_SPEED_SOURCE` const, `speedSource(ctx)`, `setSpeedSource(ctx,v)` (`:~28`, `:~34‑35`)
- `speedLimitSource(ctx): SpeedLimitSource { ... }` (`:36‑39`) — remove entirely.

**Keep:** `AUTO`, `PREFER_GMAPS`, `sourceMode`/`setSourceMode`, and everything else (marquee, hud, lane,
interpolate, accBooster, bubbleAuto, animOpt, headlessAutostart, nav‑screen‑mode, voice‑key).

### 2.4 `core/src/main/kotlin/com/byd/clusternav/navigation/NavSourceMode.kt`
- Remove `PREFER_WAZE = 3`, `PREFER_VIETMAP = 4`, and the "Speed + Alert source" block `SPEED_VIETMAP = 0` / `SPEED_WAZE = 1`.
- **Keep** `AUTO = 0`, `PREFER_GMAPS = 2`. ✅ `NavSourceModeTest.kt` only asserts `AUTO==0` & `PREFER_GMAPS==2` (`NavSourceModeTest.kt:15‑16`) → **test stays green, no edit**.

### 2.5 `core/src/main/kotlin/com/byd/clusternav/navigation/SourceArbiter.kt`
- Remove `WAZE_PKGS` + `VIETMAP_PKGS` (`:~13‑14`) and the `PREFER_WAZE`/`PREFER_VIETMAP` `when` branches (`:~25‑26`).
- **Keep** `AUTO` + `PREFER_GMAPS` branches, `GMAPS_PKGS`, release/clear/isFresh. ✅ `SourceArbiterTest.kt` tests only AUTO + PREFER_GMAPS (uses a generic `"vn.vietmap.app"` string, not matched against the removed groups) → **test stays green, no edit**.

### 2.6 `app/src/main/AndroidManifest.xml`
- Remove `<uses-permission android:name="android.permission.READ_LOGS" />` (`:~7`) — comment says "read WazeMod HLP/1"; only `WazeHudSource` uses it (also its `pm grant ... READ_LOGS`). No other user.
- Remove `<uses-permission android:name="android.permission.BIND_APPWIDGET" .../>` (`:~22‑24`) — only `vietmapwidget` (`VietMapAppWidgetHost`/`VietMapWidgetBridge`/`VietMapWidgetExtraction`) uses AppWidget APIs. Confirmed no other main‑source user.
- Remove the `<activity android:name=".vietmapwidget.VietMapWidgetDiagActivity" .../>` declaration.
- **Keep** `QUERY_ALL_PACKAGES` (cast picker), all cast/nav/voice services & receivers.

### 2.7 Layouts — `res/layout/activity_main.xml` **and** `res/layout-w960dp/activity_main.xml`
- Remove `Spinner @+id/spinner_speed_source` (`layout:78`, `layout-w960dp:104`) + its label/row.
- Remove `Button @+id/btn_vietmap_widget_diag` (`layout:89`, `layout-w960dp:118`) + its "Dữ liệu VietMap" label.
- `Spinner @+id/spinner_nav_source` (`layout:57`, `layout-w960dp:83`) — **keep the widget**, options are set in code (§2.2). If Open Q1 = GMaps‑only, this spinner may be removed entirely (then also drop its `findViewById` in `MainActivity`).
- **Keep** `spinner_cluster_mode` (nav display mode) and the `cb_hud`/`txt_hud_status` gone‑but‑present ids (a KEEP contract test asserts they remain — see §2.11).

### 2.8 `core/src/main/kotlin/com/byd/clusternav/carexec/CarExecCatalog.kt`
- `:43` remove `addAll(CarExecSpeedSignCatalog.steps)`.

### 2.9 `core/src/main/kotlin/com/byd/clusternav/carexec/CarExecScenarios.kt`
- Remove the three `CarFeature.SPEED_SIGN` scenarios (`:~347‑381`): `id = "sign.discover-chain"`,
  `"sign.source-from-vietmap"`, `"sign.inject-and-guard"` — they reference the now‑deleted `sign-*`
  steps. **Keep** `cast.reissue-policy` and all `NAVIGATION`/`CLUSTER_CAST` scenarios.

### 2.10 `core/src/test/kotlin/com/byd/clusternav/carexec/CarExecCatalogTest.kt`
- Remove the `sign-*` step ids from the expected‑id lists (`:85‑86` — drop `sign-inventory`,
  `sign-watch-live`, `sign-consumer`, `sign-source-vietmap`, `sign-mute-camera`, `sign-inject`,
  `sign-stale-guard`; **keep** `reissue-policy`) and the `sign-*` candidate ids (`:103‑109`).
- **Verify** `CarExecScenariosTest.kt` / `RunScenarioTest.kt` do not assert the removed scenario ids
  (grep found no `sign.`/`sign-` references there — likely no edit, confirm during execution).

### 2.11 `app/src/test/java/com/byd/clusternav/HudOutputHiddenContractTest.kt`
- Remove the single assertion block (`:~40‑43`):
  `assertTrue(main.contains("speedSign.onOutputEnabled(SpeedSignOutput.HUD, false)"), ...)`.
- **Keep** all other assertions (`Prefs.setHud(this,false)`, `NavigationOutputTarget.HUD` force‑disable,
  cluster‑lane‑follows‑master, cb_hud/txt_hud_status gone‑but‑present). This file **stays** (KEEP).

### 2.12 `app/src/test/java/com/byd/clusternav/navigation/NavigationOutputIsolationTest.kt`  *(mixed — KEEP file, strip speed method)*
- Remove imports `:4` `SpeedLimitClearReason`, `:5` `SpeedLimitSource`.
- Remove the whole test method `` `speed sign ports are independent from each other and canonical Amap navigation` `` (`:~66‑95`) — it uses `RecordingSpeedSignPort`, `SpeedSignLifecycleCoordinator`, and reads the deleted `NavigationSpeedSignOwner.kt` source text.
- **Keep** the three navigation‑output isolation tests (block/throw/saturation, display‑verified, "no Cast import") — those are core KEEP nav contracts.

### 2.13 `README.md`  *(optional)*
- No current‑feature copy for the removed features exists (grep: only `README.md:4`, `:55` = T10‑probe
  status). Optional: add a 1.22 changelog line noting the removal. No functional edit required.

### 2.14 Optional cleanup (harmless if skipped)
- `core/.../carexec/CarExecModels.kt:19` `CarFeature.SPEED_SIGN` enum value — becomes unused after §2.9;
  safe to leave (enum values don't break the build) or remove.
- `core/.../navigation/NavigationModels.kt:60` `NavigationOutputTarget { CLUSTER_LANE, HUD, SPEED_SIGN }`
  — `SPEED_SIGN` is only thrown‑on in `NavigationSessionCoordinator.kt:211` ("has no port"); it is **not**
  the speed‑limit feature. **Leave as‑is** (removing it changes an unrelated nav enum + coordinator).
- `res/values/strings.xml` — check for orphaned speed/vietmap strings (optional).

---

## 3. KEEP / boundary — cast "VietMap‑as‑target" (DO NOT TOUCH)

These reference VietMap as a **cast TARGET APP** (pkg `vn.vietmap.live`) — the Cluster Cast feature.
They are **not** the speed‑limit signal and **must stay**:

| File | What the VietMap/vietmap match is |
|---|---|
| `app/src/main/java/com/byd/clusternav/modules/clustercast/CastShell.kt` | cast shell — VietMap as swap/occupant target |
| `app/src/main/java/com/byd/clusternav/modules/clustercast/ClusterCast.kt` | installed‑app/cast target handling |
| `app/src/test/java/com/byd/clusternav/modules/clustercast/CastSwapTest.kt` | `VIETMAP = "vn.vietmap.live"` cast‑swap target (`:25`) |
| `core/src/test/kotlin/com/byd/clusternav/modules/clustercast/v2/CastClusterSlotTest.kt` | VietMap as a cluster slot occupant (`:37`) |
| `app/src/test/java/com/byd/clusternav/modules/clustercast/WmParseTest.kt` | WM‑dump parser recognizing `vn.vietmap.live` occupant (`:30`,`:53`,`:77`) |

Also KEEP (works / shared, verified untouched by this removal):
- **GMaps→cluster nav:** `NavRepository`, `AmapFrameBuilder`, `ClusterBroadcaster`, `NavParse`,
  `Maneuver.kt` (**no edit** — `hlpTurnToManeuver` is in `WazeHudSource`, not here),
  `ManeuverSignature`, `TurnDistanceInterpolator`, `NavArrivalGuard`, `IconResource`, `NotificationParser`, `NavState`.
- **Vehicle speedometer (not speed‑limit):** `app/.../SpeedProvider.kt` + `core/.../navigation/SpeedReading.kt`
  (BYD HAL `getCurrentSpeed()` for interpolation) — **KEEP**.
- `SimpleCastRuntime.executeShell` (shared; `WazeHudSource` *used* it, but it stays for cast), voice‑key,
  `modules/navaccess` (accessibility), `BootSetupService`, headless autostart, `RebindReceiver`, distance/HUD keep‑alive.

---

## 4. Coupling risks (both directions) — every KEEP→DELETE edge

**Direction A — KEEP code importing a DELETE symbol** (each has an explicit §2 edit):

| KEEP file | Imports/uses (DELETE symbol) | Edit |
|---|---|---|
| `NavNotificationListener.kt` | `SpeedLimitSource` (`:15`), `VietMapWidget*` (`:17‑19`), `WazeHud*` (`:20‑21`), `NavigationSpeedSignOwner` (`:52`) | §2.1 |
| `MainActivity.kt` | `VietMapWidgetDiagActivity` (`:4`), `SpeedSignOutput` (`:24`), `NavigationSpeedSignOwner` (`:40`) | §2.2 |
| `Prefs.kt` | `SpeedLimitSource` (`:4`) | §2.3 |
| `SourceArbiter.kt` | `NavSourceMode.PREFER_WAZE/PREFER_VIETMAP` (branches) | §2.5 |
| `CarExecCatalog.kt` | `CarExecSpeedSignCatalog` (`:43`) | §2.8 |
| `CarExecScenarios.kt` | `CarFeature.SPEED_SIGN` scenarios reference `sign-*` steps | §2.9 |
| `CarExecCatalogTest.kt` | `sign-*` step/candidate ids (`:85‑109`) | §2.10 |
| `HudOutputHiddenContractTest.kt` | asserts `speedSign.onOutputEnabled(...HUD,false)` (`:40`) | §2.11 |
| `NavigationOutputIsolationTest.kt` | `SpeedLimitSource`/`ClearReason` + `RecordingSpeedSignPort` + coordinator | §2.12 |

**Direction B — DELETE code importing KEEP symbols (safe; only the DELETE side goes):**
- `WazeHudSource` uses `Maneuver`, `NavState`, `SimpleCastRuntime.executeShell`, `ClusterBroadcaster`,
  `ClusterNavLaneWidget` — all KEEP, only the call‑sites (in the deleted file / removed listener) disappear.
- `NavigationSpeedSignOwner` uses `SpeedSignLifecycleCoordinator` + `NoopSpeedSignPort` (both DELETE) — self‑contained.

**Cascade C — the T10/T11 RESEARCH catalog (higher‑risk; confirm scope — see §6 Open Q2):**
Deleting `CarExecSpeedSignCatalog.kt` + `contracts/SpeedLimit*` touches the *separate* T10 probe/candidate
research pipeline (README: "its own distinct gate"), not just the runtime. Verified **decoupled / safe**:
- `vehicleTest` probe harness (`HudSignProbeActivity.kt`, `HudSignProbeReceiver.kt`,
  `VehicleTestSurfaceContractTest.kt`) does **NOT** import `contracts.SpeedLimit*` or the coordinator
  (grep clean) — it uses its own `TEST_ADAS_*`/`TEST_SPEED_LIMIT` HAL constants. **No edit.**
- `offcar-planner` (`CandidateScenarioGenerator.kt`) references the deleted **test files only as string
  paths** in `TraceabilityCatalog` (`:237`,`:251`,`:305`) and uses its **own** `SurfaceKind.CLUSTER_SPEED_SIGN`/
  `HUD_SPEED_SIGN` enums (not `contracts.*`). Compiles fine after deletion; the string paths become stale
  (low‑risk, optional to update). Its speed *candidate plans* (`OFFCAR-S1/S4/S5`, `M4-*`) are research
  candidates for the T10/T11 gate, **not** the runtime signal — **leave intact** unless full excision is chosen.
- `T10SessionEngine.kt:271‑272` (`T10RequiredSurface.CLUSTER_SPEED_SIGN`/`HUD_SPEED_SIGN`,
  `T10ResultIdentityId.D_M3/D_M4`) are T10‑harness enums, independent of `contracts.SpeedLimit*`. **No edit.**
- `offcar-planner ExpansionTransportFenceTest.kt:484` lists **future** T11 paths under
  `app/.../vehicle/*.kt` — those files **do not exist** (glob empty); harmless string fence.

---

## 5. Order of operations + risk notes

Do edits **before** deletes on shared boundaries so the tree never fails to compile mid‑way.

1. **Edit consumers first (make them stop referencing DELETE symbols):**
   `NavNotificationListener.kt` (§2.1) → `MainActivity.kt` (§2.2) → `Prefs.kt` (§2.3) →
   `SourceArbiter.kt` (§2.5) → `NavSourceMode.kt` (§2.4) → layouts (§2.7) → `AndroidManifest.xml` (§2.6).
2. **Edit carexec + tests:** `CarExecCatalog.kt` (§2.8) → `CarExecScenarios.kt` (§2.9) →
   `CarExecCatalogTest.kt` (§2.10) → `HudOutputHiddenContractTest.kt` (§2.11) →
   `NavigationOutputIsolationTest.kt` (§2.12).
3. **Delete files** (§1) in this order (leaves → roots):
   app tests → app `vietmapwidget`/`wazehud`/`NavigationSpeedSignOwner`/`VietmapBubbleExperiment` →
   core tests → core `SpeedSignLifecycleCoordinator`/`SpeedSignPorts`/`CarExecSpeedSignCatalog`/`vietmapwidget` →
   `vehicle-contracts` `contracts/SpeedLimit*` (+ test) **last** (most‑depended‑upon).
4. **Build gates:** `./gradlew :core:test :app:testDebugUnitTest :vehicle-contracts:test :offcar-planner:test`,
   then `./gradlew :app:assembleRelease`. Also build **`assembleVehicleTest`** (the T10 probe harness lives
   there) to prove Cascade C stayed intact. Confirm `aapt2` shows no `TEST_*` surface in the release APK (unchanged invariant).

**Risk notes**
- **R‑P1 build order:** `SpeedSignOutput`/`SpeedLimitSource` are imported by KEEP files — never delete
  `SpeedSignPorts.kt`/`SpeedLimitEnums.kt` before steps 1‑2. (Handled by the order above.)
- **R‑P1 arbiter/nav mode:** `NavSourceModeTest` and `SourceArbiterTest` were read — they only exercise
  `AUTO`/`PREFER_GMAPS`, so they stay green **without** edits. Do not "fix" them.
- **R‑P2 Cascade C scope:** removing `CarExecSpeedSignCatalog` + `contracts/SpeedLimit*` reaches into the
  T10/T11 research gate. Verified compile‑safe, but this is the item most likely to surprise. If the intent
  is *only* to stop the head‑unit drain, Cascade C could be deferred (Open Q2).
- **R‑P2 VietMap notification nav:** dropping `vn.vietmap.live` from `MAPS_PACKAGES` removes VietMap
  turn‑by‑turn *notification* nav (never spinner‑selectable). Reversible; confirm intent (Open Q1).
- **R‑P3 dead prefs keys:** persisted keys `speed_source`, `source_mode==3/4` on users' devices become
  orphaned (harmless — reads fall back to defaults).

---

## 6. Open questions (need owner decision — do not guess)

1. **`vn.vietmap.live` in `NavNotificationListener.MAPS_PACKAGES` (VietMap *notification* nav, not the
   widget).** Keep (harmless; `NotificationParser` handles VietMap's field‑inverted format) **or** remove
   for strict GMaps‑only nav? → also decides whether the nav‑source spinner becomes GMaps‑only or
   Auto+GMaps. *Recommend: remove → GMaps‑only, matching "keep only what works."*
2. **Cascade C scope.** Fully excise the T10 research speed catalog now (`CarExecSpeedSignCatalog` +
   `contracts/SpeedLimit*` + carexec scenarios/tests as in §2.8‑2.10) — the owner listed these — **or**
   keep the T10/T11 research gate untouched and remove only the runtime signal (§1a‑1d, 1g)? The owner's
   explicit list includes Cascade C, so the plan above **includes** it; flagging because it touches the
   separate exact‑source gate.

---

## Appendix — file counts
- **DELETE:** 29 files (19 main + 10 test).
- **EDIT:** 14 files (8 main src, 2 layouts, 1 manifest, 3 tests) + 3 optional cleanups.
- **KEEP/boundary (must‑not‑touch):** 5 cast files/tests + all GMaps‑nav + vehicle‑speedometer + cast/voice/accessibility infra.
