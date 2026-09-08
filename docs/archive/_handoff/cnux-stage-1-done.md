# Cast + Nav UX v1.04 — Stage 1 (Wave 1) done

> Spec: `docs/specs/cast-nav-ux-release-v104.html` (R1 #6 hide HUD, R2 #3 exclude launchers)
> Date: 2026-08-11 · Off-car only · **No commit/push** (per `docs/_handoff/AUTONOMOUS-RESUME.md`)

## Scope status
- **R1 (#6 hide HUD option)** — ✅ code + test + wired
- **R2 (#3 exclude launchers from cast)** — ✅ code + test + wired
- No gaps in Wave 1 scope.

## Files changed
| File | Change |
|------|--------|
| `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/AppMover.kt` | R2: added pure `isLauncher(pkg)` to companion. |
| `app/src/main/java/com/byd/clusternav/modules/clustercast/BubbleActionDispatcher.kt` | R2: `detectForeground()` now unions all CATEGORY_HOME packages ∪ blocklist ∪ `isLauncher`; posts guard toast + no dispatch on null/launcher; `handleIdleTap`/`handleSplitTap` bail via `?: return` (single toast site). Replaced default-only `homePackage()` with `homePackages()` (query-all). |
| `app/src/main/java/com/byd/clusternav/modules/clustercast/CastAutostart.kt` | R2: spinner population filter also drops `AppMover.isLauncher(pkg)`; imported `AppMover`. |
| `app/src/main/java/com/byd/clusternav/MainActivity.kt` | R1: removed `hudEnabled` field + `findViewById(R.id.cb_hud)` + checkbox listener; force-disable HUD once after lane wiring (`Prefs.setHud(false)` → `NavRepository.setOutputEnabled(HUD,false)` → `speedSign.onOutputEnabled(SpeedSignOutput.HUD,false)`). Kept `NavigationOutputTarget.HUD` enum + harmless `txt_hud_status` refresh. |
| `app/src/main/res/layout/activity_main.xml` | R1: `cb_hud` + `txt_hud_status` → `android:visibility="gone"` (ids kept). |
| `app/src/main/res/layout-w960dp/activity_main.xml` | R1: `cb_hud` + `txt_hud_status` → `android:visibility="gone"` (ids kept). |
| `app/src/test/java/com/byd/clusternav/SpeedSignSourceLifecycleTest.kt` | R1: assertion updated — MainActivity now feeds `speedSign.onOutputEnabled(SpeedSignOutput.HUD, false)` (force-off) instead of the removed `...HUD, enabled)` toggle. |
| `docs/specs/cast-nav-ux-release-v104.html` | §Design D1/D2 implementation notes appended; §Tasks Wave 1 marked done; §Reviewer Log Pass 1 appended (no sections overwritten). |

## New tests
| Test | Module | Cases |
|------|--------|-------|
| `AppMoverLauncherExclusionTest` | `:core` (`…/simplified/`) | 4 — launcher pkgs true; dudu pkgs true; case-insensitive; real targets (VietMap/GMaps/CarPlay) false. |
| `HudOutputHiddenContractTest` | `:app` | 4 — MainActivity no cb_hud wiring; force-disable HUD + enum kept; cluster-lane path untouched; both layouts hide HUD block (ids kept), cb_lane stays visible. |

Existing `NavigationOutputIsolationTest` stayed green (cluster nav output path unchanged).

## isLauncher signature + location
```kotlin
// core/src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/AppMover.kt
// (inside `companion object`)
fun isLauncher(pkg: String): Boolean {
    val p = pkg.lowercase()
    return p.contains("launcher") || p.startsWith("com.byd.dudu") || p.contains("dudu")
}
```
Pure (no Android import) → `:core`, `LayeringRulesTest` green.

## Exact query used for home packages
`BubbleActionDispatcher.homePackages()`:
```kotlin
context.packageManager.queryIntentActivities(
    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
).mapNotNull { it.activityInfo?.packageName }.toSet()
```
`detectForeground()` excluded set = `homePackages() + setOf(context.packageName, "com.byd.clusternav")`; guard = `foreground == null || foreground in excluded || AppMover.isLauncher(foreground)` → toast `Lang.t("Không cast màn hình chính","Won't cast the launcher/home screen")`.

## Verify (off-car)
Command:
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :core:test :app:testDebugUnitTest :car-integration:test :app:assembleDebug --console=plain
```
Result: **BUILD SUCCESSFUL in 1m 1s**

Test totals (0 failures, 0 errors):
- `:core:test` — **690**
- `:app:testDebugUnitTest` — **325**
- `:car-integration:test` — **28**
- Total **1043**, 0 failures.

Exit-gate grep (`MainActivity.kt` for `cb_hud|hudEnabled`): only 1 match — a comment; **0 listeners/wiring**.

Note: pre-existing warning `AppMover.kt:51 'when' is exhaustive so 'else' is redundant` is in the untouched `castToCluster` CP/AA branch — not introduced by Wave 1, not a build error.

## Constraints honored
- No new dependency (W1); SharedPreferences kept.
- Did not touch speed-sign/HUD **injection** investigation (navopen, `hud-cluster-injection-*`, `scripts/vehicle/hud3-*`). #6 only hid the UI toggle; nav→cluster pipeline intact.
- No file >500 LOC introduced; `:core` stays Android-free.
- No commit/push.
