# Cast + Nav UX v1.04 — Stage 5 (Senior Review) done

> Spec: `docs/specs/cast-nav-ux-release-v104.html` (R1–R9, D1–D6)
> Reviewer: senior architect (opus) · Date: 2026-08-11 · Off-car only · **No commit/push/vehicle-adb**
> Reads: `cnux-stage-1-done.md` … `cnux-stage-4-done.md` + every changed source/test file.

## VERDICT: ✅ APPROVED

0 P0–P1 findings · 100% scope (R1–R9 all ✅) · full JVM suite + `:app:assembleDebug` green.
Two accepted residual **[P3]** items, both already documented on-car checks in §Verification/§Open
Questions — neither is actionable off-car without re-introducing regression risk. **No code patch was
required**, so the review loop exits after one pass.

## Method

Read the spec (source of truth) + all four wave handoffs, then opened every changed file on BOTH
sides of each contract and traced one value end-to-end. Verified off-car by a fresh (`--rerun-tasks`)
run of the full suite and counted results from the JUnit XML reports (authoritative, not cached).

## Final test totals (fresh execution, counted from `build/test-results/**`)

| Module | Tests | Failures | Errors |
|--------|------:|---------:|-------:|
| `:core:test` | **723** | 0 | 0 |
| `:app:testDebugUnitTest` | **321** | 0 | 0 |
| `:car-integration:test` | **28** | 0 | 0 |
| **Total** | **1072** | **0** | **0** |

`:app:assembleDebug` → **BUILD SUCCESSFUL**. `LayeringRulesTest` green (`:core` stays Android-free;
`pureFilesStillInApp == 2` unchanged). All changed source files ≤500 LOC (largest:
`SimpleCastCoordinator` 498, `FloatingBubbleService` 491, `AppMover` 401).

## Scope completeness (R1–R9)

| R | Item | Code + wired (evidence) | Test | ✅/❌ |
|---|------|-------------------------|------|:---:|
| R1 | #6 hide HUD | Both `activity_main.xml` (portrait + `layout-w960dp`): `cb_hud`/`txt_hud_status` `visibility="gone"`, ids kept, `cb_lane` stays visible. `MainActivity` force-disables ×3 (`Prefs.setHud(this,false)` → `NavRepository.setOutputEnabled(HUD,false)` → `speedSign.onOutputEnabled(HUD,false)`), no `cb_hud` listener/field, `NavigationOutputTarget.HUD` kept. | `HudOutputHiddenContractTest` 4 · `NavigationOutputIsolationTest` 4 | ✅ |
| R2 | #3 exclude launchers | `AppMover.isLauncher` (pure `:core`) + `BubbleActionDispatcher.detectForeground` unions `homePackages()` (all CATEGORY_HOME) ∪ self ∪ `isLauncher`, posts guard toast on hit; `CastAutostart` filters `isLauncher`. | `AppMoverLauncherExclusionTest` 4 · `BubbleGestureContractTest` (guard) | ✅ |
| R3 | #4 nine ratios | `CastProfile(side,percent)` + `SPLIT_PERCENTS={10..90 step 10}` (19 profiles/app); spinner data-driven 9 options `10/90…90/10`; `normalizePercent` backfill. | `CastProfileDensityTest` 8 | ✅ |
| R4 | #5 DPI per ratio | `setDensitySplit → CastDensityControl.setForSplit` persists density under `CastProfile.of(side,leftPercent)` of **each occupied slot** (same key bounds use); restore via `applySavedProfile`; wired to `CastGeometryEditor` split DPI button. | `CastProfileDensityTest` (persist-per-slot + re-apply-on-recast + no-op-outside-split) | ✅ |
| R5 | #7 single-icon bubble | `BubbleGesturePlanner` (pure); `BubbleRenderer` one `ImageView` (`ic_bubble_nav`, `background=null`, 52dp ≥ 48dp); `BubbleGestureHandler` `GestureDetector` tap/long-press + drag threshold; `BubbleSubmenuOverlay` (3 rows ≥48dp, dismiss on choice/outside); `FloatingBubbleService` wires onTap/onLongPress. | `BubbleGesturePlannerTest` 9 · `BubbleAccessibilityTest` 26 · `CastUILifecycleSafetyTest` 17 · `BubbleGestureContractTest` 12 | ✅ |
| R6 | #1 cast return fullscreen | `AppMover.fullscreenReturnCommand` (windowingMode 1 + SINGLE_TOP `0x20000000` + LAUNCHER) + `returnToMain` NORMAL rewrite + self-heal re-issue via `isWindowedOnMain`; CP/AA `move-task` unchanged; `handleStop` always → `Idle`. | `AppMoverReturnFullscreenTest` 5 · `CastReturnFullscreenTest` 2 | ✅¹ |
| R7 | #2 nav arrival clear | `NavArrivalGuard` (pure): `isArrivalText` (EN+VI), `arrivedByRouteRemaining`, `acceptDistance` (regression + hysteresis). `NavNotificationListener`: arrival→`stop`, route-remain~0→`stop`, distance-guard **before** ingest, reset on stop/arrival/removal. Neutral `Maneuver`/`AmapFrameBuilder` untouched. | `NavArrivalGuardTest` 9 · `NavArrivalClearContractTest` 5 | ✅¹ |
| R8 | code health + layering | All files ≤500 LOC; `:core` Android-free (`NavArrivalGuard`, `BubbleGesturePlanner`, `CastProfile` pure); both prefs impls mirrored (`SharedPrefsSimpleCastPrefs.profileKey` ≡ `FakePrefs.profileKey`). | `LayeringRulesTest` 9 (green) | ✅ |
| R9 | no regress CP/AA + speed-sign | CP/AA full-only (`CastSlotValidator` intact, CP return = `move-task`, `AppMoverReturnFullscreenTest` asserts CP never uses windowingMode-1 recipe); speed-sign/HUD **injection** pipeline (`hud3-*`, `AmapFrameBuilder`, `HudMirrorController`) untouched by the waves; #6 only hid the UI toggle. | covered across suite | ✅ |

¹ App-side logic + tests complete; the **visual/windowing-mode confirmation is on-car** (R6 `dumpsys`
freeform check; R7 GMaps-arrival-clears-cluster) and is correctly left in §Verification — not claimed
PASS here.

## Boundary-shape (both sides read, 1 value traced E2E)

| # | Contract (producer → consumer) | Trace | Result |
|---|--------------------------------|-------|--------|
| #6 | nav→cluster output ↔ HUD output | `CLUSTER_LANE` stays user-driven & emitting; `HUD` forced `enabled=false`, no UI re-enable path; `NavigationOutputTarget.HUD` retained (isolation test iterates `.entries`). | **OK** |
| #3 | `isLauncher`+CATEGORY_HOME ↔ `detectForeground`/`CastAutostart` | `com.byd.dudu*`/`*launcher*` → excluded; VietMap/GMaps/CarPlay → castable. | **OK** |
| #4/#5 | prefs SAVE key ↔ RESTORE key (real + Fake) | `profileKey` byte-identical in both impls; save `setForSplit(CastProfile.of(LEFT,20))` → key `__L20` density=200 → stop → re-cast L@20 → `applySavedProfile` emits **`wm density 200 -d 1`** (test-proven). Legacy `{50,30,70}`→`L50/R50/L30/R30/L70/R70` still load. | **OK** |
| #7 | planner ↔ dispatcher ↔ coordinator | tap Idle→`CAST_FULL`→`CastFull`; casting→`RETURN`→`Stop()`; submenu `slotFor(CAST_LEFT)=LEFT`→`onCastSlot(LEFT)`→`CastSlot(fg,LEFT)`; `OPEN_CONFIG`→`slotFor=null`→`openConfig`→`Intent(MainActivity)+NEW_TASK`. Same `BubbleMenuAction`/`ClusterSlotSide` enums (`:core`). Icon `background=null`, ≥48dp. | **OK** |
| #1 | cast freeform (mode 5) ↔ return fullscreen (mode 1) | `castToCluster` NORMAL `--windowingMode 5`; `returnToMain` NORMAL → `fullscreenReturnCommand` (`--windowingMode 1`) for the SAME component; self-heal re-issue once; CP/AA path emits `move-task`, never mode-1 (test-asserted). | **OK** |
| #2 | listener ↔ `NavArrivalGuard` ↔ NavRepository | `parseEta(state.eta).first` = **route-remaining meters** (`RE_ETA_KM`×1000) → `arrivedByRouteRemaining`; `parseMeters(state.distance)` + `cleanRoadName(road)+"|"+maneuverText` → `acceptDistance`; guard sits **before** `NavRepository.ingest` (contract-test ordered); arrival/route-end → `NavRepository.stop`. Types match field-by-field. | **OK** |

No shape mismatch found on any contract.

## Findings

No **[P0]/[P1]/[P2]** findings.

- **[P3] (accepted — on-car item, not patched).** `NavArrivalGuard.ARRIVAL` uses the `arriv` prefix,
  which also matches Google Maps' *"Arriving at …"* shown during the final approach (still moving),
  so the cluster may clear slightly early. Rationale for accepting as-is: (a) the regex semantics are
  unchanged from the pre-existing listener (moved to `:core`, not broadened); (b) it is only checked
  on `title`/`text`/`bigText` (not ETA/subtext), so it fires only on genuine end-of-route strings;
  (c) an early clear is strictly less harmful than the stuck-stale-frame bug being fixed; (d) it is an
  explicit §Verification on-car check. Patching without on-car telemetry risks re-introducing the
  stuck-frame class. **No change.**
- **[P3] (accepted — documented app-side limit).** `AppMover.isWindowedOnMain` parses
  `Stack id=N bounds=[..][..] displayId=N` from `am stack list`; the real A10/DiLink3 format may
  differ. It is **fail-open** (unparseable → returns false → no retry, never loops/relaunches), and
  the deep windowing-mode truth (`dumpsys window displays`) is the documented §Open-Questions on-car
  follow-up (port legacy `CastShell.restoreFullscreenOnMain` if stuck cases persist). **No change.**

## Tech-freshness

- **No new dependency introduced by the waves.** Every reviewed wave source imports only Kotlin
  stdlib + existing Android APIs (+ `dadb` in the already-existing runtime layer). The
  `settings.gradle.kts`/`core`/`car-integration` gradle edits and `hud3-*`/`vehicleprobe/`/
  `AmapFrameBuilder*` files in the working tree are **pre-existing uncommitted WIP** (HUD-injection
  investigation, per spec §Context "Baseline: main + uncommitted WIP") — not wave changes.
- **SharedPreferences retained intentionally** (spec §Context/§References: DataStore is newer but
  SharedPreferences is valid for small synchronous KV; migration deferred, no dep added).
- **No deprecated Android API introduced** by wave code: `GestureDetector`, `TYPE_APPLICATION_OVERLAY`
  (guarded, `TYPE_PHONE` fallback < 26), `ImageView`, `WindowManager` overlays are all current.
  Pre-existing deprecation warnings (`ClusterBlackActivity` FLAG_FULLSCREEN, `NavAccessibilityService`
  `recycle()`, `UpdateChecker !!`) are in untouched/baseline files. Context7 not required — no new
  library/API surface was added.
- **Verdict: FRESH.**

## Constraints honored

Off-car only · no commit/push/vehicle-adb · no new dependency · `:core` Android-free · all files
≤500 LOC · scope stayed within the 7 owner items / R1–R9 · speed-sign/HUD-injection files untouched.
