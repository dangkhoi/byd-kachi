# stage3-bugs — DONE (2026-09-06)

ClusterNav bug-fix stage. Three on-car bugs diagnosed 2026-09-06
(`docs/diagnostics/seat-vietmaploop-oncar-2026-09-06.md`). All off-car; NO git, NO version change.
Reused the stage1 helper `BydHal.callNamedInt(dev, method, vararg args)` for the seat write path.

## Verification (this run) — [ĐO]

- Build: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug -q` → **exit 0**.
- Full suite: `./gradlew test --rerun-tasks --continue -Dorg.gradle.parallel=false` → **BUILD SUCCESSFUL** (3m09s).
  Per-module (JUnit XML aggregate): **core 808 · app 956 · car-integration 44 · offcar-planner 99 — 0 failures, 0 errors** (all 5 module test tasks; app count = debug + vehicleTest unit tests).
- Changed/relevant test classes (each 0 fail): `comfort.SeatComfortTest` **6** · `VietMapAutostartGateTest` **5** (new) · `comfort.Pm25FilterTest` **5** · `L2CockpitUiWiringContractTest` **10** · `VoiceKeyAdbApprovalWiringTest` **6** · `LayeringRulesTest` **9**.
- grep gates:
  - Seat path uses `setSeatVentilatingState` / `setSeatHeatingState` ✓ (constants in `SeatComfort.kt`, applier KDoc + `methodFor`).
  - **No `0x43101010` anywhere in the seat path** ✓ (applier/model/diagram); applier has **no** `BydHal.AC` / `BydHal.setInt` / `.featureId(` / `HAL_VALUE` code ref (only KDoc noting removal).
  - `VietMapAutostart` has `COOLDOWN_MS` + `inFlight` (AtomicBoolean) + `tryBeginRun`/`finishRun` + `outsideCooldown` ✓; `MainActivity` gates `if (savedInstanceState == null) maybeAutoStartVietMap()` ✓.

---

## B1 — SEAT COOL/HEAT: wrong HAL path → NOT_PROVISIONED

Root cause (on-car [ĐO]): applier wrote raw feature-id `0x43101010` via `BYDAutoAcDevice.set(int[], ev)` →
HAL returned `NOT_PROVISIONED` (rc=-2147482648) even for the front (RE-proven) seat. The AC-device + raw-id
assumption was wrong on this trim.

What changed:
- **`core/.../comfort/SeatComfort.kt` (rewrite):** dropped `coolFeatureId`/`heatFeatureId`, `featureId(...)`,
  `halValue(...)`, `HAL_VALUE`. Added the HAL contract confirmed from the OEM decompile
  (`/tmp/byd-ac`, module `airseating`, cross-checked on-car):
  - `METHOD_VENTILATING = "setSeatVentilatingState"`, `METHOD_HEATING = "setSeatHeatingState"` + `methodFor(mode)`.
  - `seatId(index) = index + 1` (0-based UI → 1-based HAL: 1=driver, 2=passenger, 3=rear-left, 4=rear-right).
  - `stateForLevel(level)` = `[1,2,3]` clamp (STATE_OFF=1 / STATE_L1=2 / STATE_L2=3 — RE
    `AirSeatingVentilateAndHeatModel`: OFF/LOW/HIGH). Kept `SeatMode`, `LEVEL_OFF`, `SEATS` (index+labelKey),
    `seatsForModel`/`seatCountForModel`/`isHanModel`. Han rear seats (3,4) supported.
- **`app/.../comfort/SeatComfortApplier.kt` (rewrite of `apply()` + KDoc):** now resolves the SETTING device
  (`BydHal.device(BydHal.SETTING, systemBypassContext(), bypass(app))`) and writes each active seat via
  `BydHal.callNamedInt(dev, SeatComfort.methodFor(mode), seatId, state)`. Only the active mode's method is
  called per seat (cool/heat mutually exclusive at the MCU); apply-on-start still skips OFF levels. Log tag
  "SeatComfort" now logs `method(seatId, state) [seatIndex mode] rc`. UI/Prefs keys unchanged.
- **`core/.../comfort/SeatComfortTest.kt` (rewrite):** locks `stateForLevel` (0→1/1→2/2→3 + clamp),
  `seatId` (+1), `methodFor` (the two named methods), the 4-seat table, and `seatsForModel`/`isHanModel`.

Test evidence: `SeatComfortTest` 6/0; `L2CockpitUiWiringContractTest` 10/0 (UI wiring — `applyNow`/`detectSeatCount`
API kept intact); full core+app green.
On-car re-verify pending: `setSeatVentilatingState/HeatingState` rc on the real SETTING device (Seal front; Han rear 3/4).

## B2 — VietMap autostart loop (flash)

Root cause (on-car [ĐO] + code): `ensureRunning`→`runNow` had NO dedup/cooldown; with the bubble ON it always
did `launch VietMap → sleep 1500 → relaunch ClusterNav`, and it fired on every `onCreate` — including
theme/language `recreate()` and boot auto-open → the foreground flash loop.

What changed (three layers):
- **(a) cooldown + in-flight gate** in `VietMapAutostart.kt`: `COOLDOWN_MS = 30_000`, an `AtomicBoolean inFlight`
  + `@Volatile lastRunAtMs`; pure `outsideCooldown(now, last, cd)`; `tryBeginRun()`/`finishRun()`
  (CAS-guarded, stamps time). `runNow` now claims a slot after the no-reason/not-installed early returns and
  releases in a `finally` — a burst of onCreate/recreate/boot triggers launches at most once per 30s.
- **(b) skip when already foreground**: inside the dadb session, after `pidof`, it reads the resumed/focused
  activity (`dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|ResumedActivity'`);
  if VietMap is already foreground it logs + `return@sessionResult` (no launch → no flash). Degrade-safe
  (read/grep failure ⇒ treated as not-foreground ⇒ prior behavior). Existing `pidof` dedup preserved.
- **(c) recreate gate**: `MainActivity.onCreate` now calls `maybeAutoStartVietMap()` only when
  `savedInstanceState == null` — `recreate()` (theme/language) always passes a non-null saved state, so it no
  longer re-triggers autostart. Real first-open / boot behavior kept.
- **`VietMapAutostartGateTest.kt` (new, pure, in `:app`):** 5 tests locking first-run, cooldown boundary
  (`>=`), in-flight refusal, within-cooldown-after-finish refusal, and release. Placed in `:app` (references
  the app-only `VietMapAutostart`) so `LayeringRulesTest` does not flag it; no new pure `:app` main file added.

Test evidence: `VietMapAutostartGateTest` 5/0; `VoiceKeyAdbApprovalWiringTest` 6/0 (still asserts
`BACKGROUND_READ_CAP`, no `AWAIT_ADB_APPROVAL`); `LayeringRulesTest` 9/0.
On-car re-verify pending: no more flash on open / language+theme change / boot with the bubble ON.

## B3 — PM2.5 popup-suppress rc error (filtering already works)

Investigation: `enablePurificationFunctionPrompt` does **not** exist anywhere in the OEM AC app decompile
(`/tmp/byd-ac/src/sources`, `com.byd.airconditioning`) — that app only calls `setQuickCleanAirState(z?1:2)` +
`set(...)` + getters, so there is **no OEM evidence of a correct arg**. On-car [ĐO]:
`enablePurificationFunctionPrompt(0) rc=-2147482645` (`Int.MIN_VALUE + 1003`, a rejection sentinel distinct
from `NOT_PROVISIONED`) while `setAutoCleanAirState(1) rc=0` works. Conclusion: genuinely unsupported on this
trim → make it best-effort (per task option 2).

What changed in `app/.../comfort/Pm25FilterApplier.kt`:
- **Reordered** `enableNow`/`disableNow` so the essential, proven `setAutoCleanAirState(1|0)` runs **first**,
  then the popup call — the working filter path can never be gated behind the unsupported call.
- Added a `bestEffort: Boolean` param to `acInt`: best-effort calls log the rc at **DEBUG** (with a
  "trim không hỗ trợ ⇒ bỏ qua" note) instead of INFO, so a rejection sentinel is no longer surfaced as an
  error. Still never throws (per-call `runCatching`). Kept the method (not removed) so trims that DO support
  it still get popup suppression. Tag "Pm25Filter" kept.

Test evidence: `Pm25FilterTest` 5/0 (core model unchanged); full app green. `setAutoCleanAirState` path
untouched (still the working call). On-car re-verify pending: filtering still enables (rc=0) and the popup
rc no longer clutters INFO logcat.

---

## Files

Modified: `core/.../comfort/SeatComfort.kt` · `app/.../comfort/SeatComfortApplier.kt` ·
`app/.../comfort/Pm25FilterApplier.kt` · `app/.../VietMapAutostart.kt` · `app/.../MainActivity.kt` ·
`core/src/test/.../comfort/SeatComfortTest.kt` (rewrite).
Added: `app/src/test/.../VietMapAutostartGateTest.kt`.

## Notes / follow-ups (not in this stage's scope)

- **Spec drift (doc, R2.1):** `docs/specs/seat-comfort-auto.html` and `docs/diagnostics/byd-pm25-airclean-RE-2026-09-04.md`
  still describe the OLD seat model (`HAL_VALUE = intArrayOf(0,2,3)`, raw feature-ids) and phrase the PM2.5
  popup call as non-best-effort. Code + tests are now the source of truth; these HTML/MD docs should be
  reconciled by the doc-consolidation pass (left untouched here to avoid conflict with concurrent work).
- All three are HAL/behavior fixes → **must be re-verified on the car** (over ADB wifi or the next OTA build).
- NO git, NO version change (per task).
