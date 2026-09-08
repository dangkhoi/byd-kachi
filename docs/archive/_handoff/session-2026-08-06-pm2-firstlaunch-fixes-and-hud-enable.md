# Session handoff — 2026-08-06 PM #2 (first-launch crash fixes + HUD/speed-limit HAL probe)

> Continues [`session-2026-08-06-pm-v1.04-release-baseline.md`](session-2026-08-06-pm-v1.04-release-baseline.md).
> One on-car session (parked, `adb connect <vehicle-ip>:5555` over Wi-Fi). Owner left the car mid-probe.
> Env: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ; export ANDROID_HOME=~/Library/Android/sdk` before gradle.
> `adb` at `~/Library/Android/sdk/platform-tools/adb`. Repo/remote: `dangkhoi/byd-cluster` (main), HEAD `d85b9f2`.

## TL;DR
1. **v1.04 release candidate crashed on first launch** (clean install, overlay perm not yet granted). Root-caused and fixed **3 bugs** in `FloatingBubbleService`; verified off-car (963 tests) **and** on-car (no crash, bubble renders, single overlay prompt).
2. **Fixes are UNCOMMITTED.** The pushed baseline `d85b9f2` and its release APK `apk/ClusterNav-1.04-v104-527589f2d16a-release.apk` STILL crash. A **commit + release-candidate respin + push** is pending owner authorization.
3. **Speed-limit + HUD probe** (added two debug adb hooks, no app logic changed elsewhere): established that **`rc=0` from a HAL write ≠ the display renders**. The speed-limit sign could not be forced on a parked car. **Cluster nav already works (long-proven).**
4. **THE ACTUAL NEXT TASK (owner, this session):** *"cluster làm được lâu nay rồi, cái cần là enable cái HUD kìa"* — the gap is **enabling the windshield HUD** so `writeNavFrame` output (and later speed limit) actually shows on the HUD. See [§Next task](#next-task--enable-the-windshield-hud).

---

## Working-tree state (UNCOMMITTED — 4 files)
```
 M app/src/main/AndroidManifest.xml                                            (+2 lines: 2 debug actions)
 M app/src/main/java/com/byd/clusternav/RebindReceiver.kt                      (+57: TEST_HAL_WRITE + TEST_HUD_NAV + debug gate)
 M app/src/main/java/com/byd/clusternav/modules/clustercast/FloatingBubbleService.kt   (+29/-6: 3 first-launch fixes)
 M app/src/test/java/com/byd/clusternav/modules/clustercast/CastUILifecycleSafetyTest.kt (+57: 3 regression tests)
?? docs/_handoff/v1.04-exact-source.json   (pre-existing generated manifest from prior session; identity-excluded)
```
`RebindReceiver.kt` 308 LOC, `FloatingBubbleService.kt` 463 LOC — both under the 500 LOC guardrail.

---

## Part A — First-launch crash fixes (`FloatingBubbleService.kt`) — DONE, verified, uncommitted

The v1.04 **release** candidate was installed on the car and **crashed on the very first launch**. On a clean install the overlay permission is not yet granted, and the projection-first bubble service starts on launch. Three bugs on that path:

1. **[P1] `UninitializedPropertyAccessException` in `onDestroy` (line ~171).** `onCreate()` did `if (!requestOverlayIfMissing()) { stopSelf(); return }` **before** initializing the `lateinit` `renderer`/`gestureHandler`. `stopSelf()` then runs `onDestroy()`, which called `gestureHandler.shutdown()` / `renderer.clearViews()` on uninitialized lateinits → crash on every first launch.
   **Fix:** guard with `if (::gestureHandler.isInitialized)` / `if (::renderer.isInitialized)`.
2. **[P1] `RemoteServiceException: did not then call startForeground()`.** Service is started via `startForegroundService()`, so Android requires `startForeground()` within ~5s. But `onCreate`/`onStartCommand` gated on the overlay permission and bailed **before** going foreground → process killed.
   **Fix:** call `startForegroundOnce()` **first**, then gate on overlay (a `stopSelf()` after `startForeground()` is legal). Reordered in **both** `onCreate` and `onStartCommand`.
3. **[P2] Overlay permission prompted TWICE.** `onCreate` **and** `onStartCommand` both called `CastBubbleControl.requestOverlay()` (~100ms apart on a `startForegroundService` start, before the user can grant) → the settings screen opened twice; granting the front one revealed the second ("asks again right after allowing").
   **Fix:** one-shot `overlayRequested` instance flag → the settings screen launches at most once per service start.

**Regression tests:** added 3 pure-JVM tests to `CastUILifecycleSafetyTest` (mirrors the guarded-teardown contract). Suite: **963 tests, 0 failures** (was 960).

**On-car verification (<vehicle-ip>:5555):**
- Fresh install, overlay NOT granted → **1** overlay prompt (was 2), **no crash**.
- Overlay granted (`appops set com.byd.clusternav SYSTEM_ALERT_WINDOW allow`) → **0** prompts, `FloatingBubbleService` `isForeground=true foregroundId=1042`, system `AlertWindowNotification` posted, bubble `LinearLayout` drawn → **bubble renders, no crash**.

### Team build (send to team to install + confirm OK on car)
- **`apk/ClusterNav-1.04-debug.apk`** — SHA-256 `ac13467f2ca74e99ee7aa43667477a1b4907ff54f460bb814e4dc5310cb7218d`, versionCode 104, **DEBUG** (this is the 3-fixes build; it does NOT contain the Part-B probe hooks — clean for team acceptance).
- Signed with the **standard Android debug key** (`CN=Android Debug`, cert SHA-256 `dc521dad5c8c7c79da421ccb196fd6a52df932e3c17ead8dbbe5e4b0a5a74e00`) — **same key as `ClusterNav-1.03-debug.apk`** → team can `adb install -r` over 1.03-debug **without uninstall** (keeps Cast state). Coming from a **release** build → signature mismatch → must `adb uninstall com.byd.clusternav` first (clears Cast state).
- Distribution is out-of-band (debug APKs are `.gitignore`d via `apk/*.apk`; only `apk/ClusterNav-*-release.apk` are tracked, and `ClusterNav-1.03-debug.apk` is not in git history).
- NOTE: the build currently **on the car** is the newer Part-B probe build (`app/build/outputs/apk/debug/app-debug.apk`, built 14:39, same debug key), which additionally has the `TEST_HAL_WRITE`/`TEST_HUD_NAV` hooks.

---

## Part B — Speed-limit + HUD HAL probe (debug tooling added; investigation, NOT resolved)

Added two **debug-only** adb broadcast hooks to `RebindReceiver` and rebuilt the debug APK. All `TEST_*` hooks are now gated by `isDebugBuild()` (checks `ApplicationInfo.FLAG_DEBUGGABLE`) so they **no-op in release** — a partial WARN-1 mitigation. Manifest gained 2 actions.

### New adb hooks (debug build only)
```bash
ADB="adb -s <vehicle-ip>:5555"
# Surgical single-feature HAL write to ANY device (find the right feature/device one-by-one).
$ADB shell "am broadcast -a com.byd.clusternav.TEST_HAL_WRITE --es dev <instrument|adas|setting> --es name <FEATURE_NAME> --ei val 50 -f 0x01000000"
$ADB shell "am broadcast -a com.byd.clusternav.TEST_HAL_WRITE --es dev instrument --ei id <rawFeatureId> --ei val 50 -f 0x01000000"
# Inject a full nav frame to the HUD/cluster via BydHal.writeNavFrame (no GMaps route needed).
# icon: 1=left 2=right 3/5=slight 7/8=sharp 9/10=Uturn 11=straight 15=roundabout 48=dest 49=tunnel; icon<0 = clear
$ADB shell "am broadcast -a com.byd.clusternav.TEST_HUD_NAV --ei icon 1 --ei seg 200 --es road 'Nguyen Van Linh' -f 0x01000000"
```
> **adb quoting gotcha:** values with spaces (road names) MUST be single-quoted **inside** a double-quoted `am` command as above, or `am` drops the trailing words and silently fails to broadcast (no receiver `trigger` log appears). Space-free extras work either way.
> Read results: `$ADB logcat -d -s NavRebind` (tags `HAL_WRITE[...]`, `HUD_NAV ...`).

### Findings (all verified on-car this session)
- **`rc=0` (HAL accepted) ≠ the display rendered.** This is the central lesson. Every write below returned `rc=0` yet **nothing changed** on cluster or HUD (owner confirmed: *"nothing happen, cả cụm lẫn HUD đều giữ nguyên"*).
- **Old `writeSpeedLimit` path is broken:** it targets ADAS feature `ADAS_TRAFFIC_LIMIT_SPEED_STATUS_PROMPT` (hardcoded `1329602589`). That field **does not exist** in `BYDAutoFeatureIds` on this ROM (probe never lists it) → writes an invalid id → `rc=-2147482648` (`0x800003E8`, error).
- **ADAS device rejects ALL writes** (`rc=-2147482648`): `ADAS_SLA_STATE_SET` (944767010), `setSLAState(50)`, `INSTRUMENT_TRAFFIC_SIGN_RECOGNITION_SYSTEM` (117335), etc. `getSLAState()` returns `1` (a state flag, not a km/h value).
- **SETTING device ACCEPTS (rc=0)** — but no visible render on a parked car:
  - `SETTING_SPEED_LIMIT_SET` = `1285554240` → rc=0
  - `SET_SPEED_REMINDER_SET`  = `1043333160` → rc=0
  - These are BYD's **overspeed-reminder setpoint** (a warning threshold, NOT a hard governor). No on-cluster "50" appeared. Likely gated by driving state / reminder-enabled state.
- **`writeNavFrame` (INSTRUMENT device) → all rc=0** (`INSTRUMENT_SEND_NAVI_STATUS_SET`, `INSTRUMENT_GUIDE_INFO_SIMPLE_SET`, `INSTRUMENT_FRONT_CROSSING_DISTANCE_SET`, `PATHNAME`) but **nothing rendered on the HUD** → the windshield HUD is almost certainly **OFF / not enabled** (see Next task).
- **Cluster nav lane already works** and is a *separate* mechanism: the AutoNavi broadcast `AUTONAVI_STANDARD_BROADCAST_SEND` (`AmapFrameBuilder`, `KEY_TYPE` 10001 guidance / 10019 state). It can be driven **directly from adb with no rebuild** (recipe below). Owner confirms the cluster has worked for a long time — not the gap.

### Fire the cluster nav lane directly from adb (no rebuild — reference only, already proven)
```bash
A=AUTONAVI_STANDARD_BROADCAST_SEND
# reset stuck flag (10019 STATE=9: IS_BYD_MAP true then false)
$ADB shell "am broadcast -a $A --ei KEY_TYPE 10019 --ei EXTRA_STATE 9 --ez IS_BYD_MAP true  -f 0x01000000"
$ADB shell "am broadcast -a $A --ei KEY_TYPE 10019 --ei EXTRA_STATE 9 --ez IS_BYD_MAP false -f 0x01000000"
# guidance (10001): NEW_ICON amap-index (2=left on cluster), SEG_REMAIN_DIS meters, NEXT_ROAD_NAME
$ADB shell "am broadcast -a $A --ei KEY_TYPE 10001 --ei TYPE 1 --ei EXTRA_STATE 1 --ei EXTRA_IS_FOREGROUND 0 --ez IS_BYD_MAP false --ez IS_BYD_BAIDU_MAP false --ei NEW_ICON 2 --ei SEG_REMAIN_DIS 200 --es SEG_REMAIN_DIS_AUTO '200m' --es NEXT_ROAD_NAME 'Nguyen Van Linh' --ei ROUTE_REMAIN_DIS 5000 --ei ROUTE_REMAIN_TIME 600 -f 0x01000000"
# re-feed every ~2s to hold it; the frame carries NO speed-limit field (nav only).
```

### State left on the car
- Speed-reminder values **reset to 0** (`SET_SPEED_REMINDER_SET=0`, `SETTING_SPEED_LIMIT_SET=0`, rc=0). If the overspeed reminder behaves oddly, verify/adjust in the car's own Settings > speed reminder.
- A cluster guidance frame ("rẽ trái / Nguyen Van Linh / 200m") was last fired ~15:00; re-feed stopped → it clears on the next ignition cycle (or send the 10019 reset recipe to idle it).

---

## NEXT TASK — enable the windshield HUD

Owner's actual goal: **the cluster nav is already solved; the missing piece is the windshield HUD.** `writeNavFrame` writes succeed (rc=0) but the HUD shows nothing → the HUD display is almost certainly **not enabled**. Plan for next on-car session:

1. **Find the HUD enable feature.** The current `TEST_ADAS_PROBE` only searches speed/limit/sign terms. Add a HUD search (extend `featureIdsMatching(...)` or add a `TEST_HAL_PROBE --es q HUD` hook) and enumerate ids whose names match `HUD`, `HEAD_UP`, `WHUD`, `HUD_SWITCH`, `HUD_HEIGHT`, `HUD_DISPLAY`, `HUD_SET`. Likely on the **SETTING** device (the device that accepted the speed-reminder writes) or INSTRUMENT.
2. **Enable it:** `TEST_HAL_WRITE --es dev <dev> --es name <HUD_SWITCH_SET> --ei val 1` (expect rc=0), then re-fire `TEST_HUD_NAV` and watch the **windshield HUD** for the arrow/road.
3. **Sanity:** confirm the HUD is physically present + not disabled in the car's Settings; `writeNavFrame` only renders when the HUD is on (and likely in nav mode).
4. If a HUD-enable feature is found and works, wire it into the app (a HUD on/off that the nav pipeline sets), and replace the broken ADAS `writeSpeedLimit` path — the SETTING-device speed-reminder (`SETTING_SPEED_LIMIT_SET`, rc=0) is the only accepted speed-limit write found so far, pending proof it renders while driving.

Honest open items (unchanged from prior handoff):
- **HUD arrow (nav) on the windshield HUD: unverified** — blocked on HUD-enable.
- **Speed-limit sign: not forceable on a parked car** via any feature tried; ADAS rejects writes, SETTING accepts but no render. Matches the "open probe" status of `speed-limit-sign-oncar-plan.md`.

---

## Pending release work (owner-gated)
1. **Commit** the 4 changed files (first-launch fixes + tests + debug probe hooks) — run the mandatory pre-commit security scan first.
2. **Respin the release candidate** from a clean tree: `gen-exact-source.py` → `collectAuthorizedApk ... -PclusterNavVariant=release` → new `apk/ClusterNav-1.04-<id>-release.apk` + `vehicle-candidate.json` (the current pushed candidate `527589f2d16a` crashes on first launch and is NOT shippable).
3. **Push `origin/main`** (rewrites the shared baseline — needs explicit owner OK).
4. WARN-1: the exported `RebindReceiver` test hooks are now debug-gated at runtime; for a true public release, move the whole `TEST_*` harness to `app/src/debug/`.

## Verify command
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && export ANDROID_HOME=~/Library/Android/sdk && \
./gradlew :core:test :app:testDebugUnitTest :car-integration:test :app:assembleDebug
```
→ expect **963 tests, 0 failures**.
