# Cast + Nav UX v1.04 — Stage 2 (Wave 2) done

> Spec: `docs/specs/cast-nav-ux-release-v104.html` (R3 #4 all-9 split ratios + per-ratio size, R4 #5 persist/re-apply DPI per ratio)
> Date: 2026-08-11 · Off-car only · **No commit/push** (per `docs/_handoff/AUTONOMOUS-RESUME.md`)
> Predecessor baseline: `docs/specs/cast-freeform-resize-split.html` ({FULL,L50,R50,L30,R30,L70,R70}, 7 profiles, bounds+density per profile)

## Scope status
- **R3 (#4 — full 9 split ratios + per-ratio size)** — ✅ code + test + wired
- **R4 (#5 — persist + re-apply DPI per ratio)** — ✅ code + test + wired
- No gaps in Wave 2 scope. R8 code-health honoured (files ≤500, `:core` Android-free, both prefs impls updated).

## Profile key format (R3)
`CastProfile` changed from a fixed 7-value **enum** to a data-driven **class** keyed by `(side, percent)`:
```kotlin
// core/…/simplified/SimpleCastModels.kt
class CastProfile private constructor(val side: ClusterSlotSide?, val percent: Int?) {
    val isFull: Boolean            // side==null || percent==null
    val key: String                // "FULL" | "L<pct>" | "R<pct>"  (round-trippable prefs token)
    companion object {
        val SPLIT_PERCENTS = (10..90 step 10).toList()   // [10,20,30,40,50,60,70,80,90] — 9 ratios
        const val DEFAULT_PERCENT = 50
        val FULL = CastProfile(null, null)
        fun of(side, leftPercent): CastProfile           // percent∉SPLIT_PERCENTS → 50
        fun normalizePercent(pct): Int                   // valid pct or 50
        fun fromKey(key): CastProfile?                   // "FULL"/"L30"/"R70" → profile; malformed → null
    }
}
```
- **19 profiles per app** = 9 (`L10…L90`) + 9 (`R10…R90`) + `FULL`.
- `key` tokens: `FULL`, `L10 L20 L30 L40 L50 L60 L70 L80 L90`, `R10 R20 R30 R40 R50 R60 R70 R80 R90`.
- Backward-compat: keys for the predecessor set `{50,30,70}` are byte-identical (`L30`,`R70`,…), so previously-saved configs still load. `fromKey` parses legacy tokens; a never-valid percent (e.g. `L55`) normalizes to `50`.
- `value` type: normal class w/ explicit `equals`/`hashCode`/`toString(=key)` (no data-class private-ctor `copy()` warning); construction only via `of()` / `FULL`.

## Exact prefs key strings (bounds + density — R3/R4)
`SharedPrefsSimpleCastPrefs` in `simple_cast_prefs`, and the mirrored `FakePrefs` test double, both compute:
```kotlin
profileKey(pkg, profile) = if (profile.isFull) pkg else "${pkg}__${profile.key}"
```
Per-field keys (identical shape for both bounds and density — SAME profile key):
```
config_size_<profileKey>        // e.g. config_size_com.foo.app         (FULL)
config_overscan_<profileKey>    //      config_overscan_com.foo.app__L30 (split)
config_density_<profileKey>     //      config_density_com.foo.app__R70  (split)   ← DPI per ratio
config_bounds_<profileKey>      //      config_bounds_com.foo.app__L20   (split)   ← size per ratio
```
- FULL → no suffix (`config_density_<pkg>`), split → `__L30` / `__R70` / … suffix.
- **Both bounds and density persist under the SAME `<profileKey>`** — that identity is the fix for #5.

## Spinner values (R3)
`CastAutostart.populateSplitRatioSpinner` (the wired simplified-path spinner, `R.id.spinner_split_ratio`) is now data-driven from `CastProfile.SPLIT_PERCENTS`:
- **9 options** (labels = `"left/right"`): `10/90, 20/80, 30/70, 40/60, 50/50, 60/40, 70/30, 80/20, 90/10`
- Stored value = `leftPercent` (10…90). One spinner covers both directions (leftPercent determines the whole split).
- Backfill on load: `CastProfile.normalizePercent(saved)` → any stored ratio outside `SPLIT_PERCENTS` falls back to `50` and is re-persisted.
- (The legacy `ClusterSplitRatio` / `CastFacade.splitRatioChoices` / `CastSplitRatioBinding` path is the unwired v2 pipeline — **out of scope**, untouched.)

## #5 root cause + fix (R4)
**Root cause (found):** the split-mode DPI button (`CastGeometryEditor.buildSplitDpiRow`) called `coordinator.setDensity(dpi)`. `setDensity` only persists via `(state as? SimpleCastState.CastingFull)?.targetPkg` — but in split the state is `CastingSplit`, so that resolves to **null** ⇒ `wm density` was applied to the display but **nothing was ever written to prefs**. Hence "DPI not saved after adjusting per ratio."
Secondary: the dead `setDensityForPkg`/`CastDensityControl.setForPkg` (no callers) would have saved under the **FULL** key (no-arg `displayConfigFor(pkg)`/`saveDisplayConfig(pkg,…)`), not the per-ratio key that restore (`applySavedProfile(pkg, CastProfile.of(side, leftPercent))`) reads — so even if wired it would never round-trip.

**Fix:**
- Added `SimpleCastCoordinator.setDensitySplit(dpi)` (replaces the dead `setDensityForPkg`; net-neutral LOC, coordinator stays 498).
- Added `CastDensityControl.setForSplit(shell, prefs, displayId, dpi, state)`: applies `wm density` once (display-global on Android 10 — D4), then on success persists the density under `CastProfile.of(side, leftPercent)` of **every occupied slot** — the SAME key bounds use. No-op unless `state is CastingSplit`.
- Removed dead `setForPkg`; `set(...)` (full-mode) unchanged (correctly persists to FULL profile).
- Wired `CastGeometryEditor` split DPI button → `setDensitySplit`. Full-mode DPI button still uses `setDensity` (FULL profile — correct).
- Restore path unchanged and now finds the value: `applySavedProfile` reads density from the per-ratio profile → emits `wm density <v> -d 1` on re-cast.

## Files changed
| File | Module | Change |
|------|--------|--------|
| `core/…/simplified/SimpleCastModels.kt` | :core | R3: `CastProfile` enum → class `(side,percent)`; `SPLIT_PERCENTS`, `DEFAULT_PERCENT`, `FULL`, `of`, `normalizePercent`, `fromKey`, `key`, `isFull`. |
| `core/…/simplified/CastDensityControl.kt` | :core | R4/#5: removed dead `setForPkg`; added `setForSplit` + `saveForProfile` (per-ratio-profile density persist). |
| `core/…/simplified/SimpleCastCoordinator.kt` | :core | R4/#5: `setDensityForPkg(dpi,pkg)` → `setDensitySplit(dpi)` → `CastDensityControl.setForSplit`. Net-neutral (498 LOC). |
| `app/…/simplified/SimpleCastRuntime.kt` | :app | R3/R8: `profileKey` uses `profile.isFull` + `profile.key` (keys unchanged for legacy set). |
| `app/…/clustercast/CastAutostart.kt` | :app | R3: split-ratio spinner now 9 options data-driven from `SPLIT_PERCENTS`; backfill via `normalizePercent`. Imported `CastProfile`. |
| `app/…/clustercast/CastGeometryEditor.kt` | :app | R4/#5: split DPI button → `coordinator.setDensitySplit(...)`. |
| `core/test/…/simplified/CastProfileDensityTest.kt` | :core test | **NEW** (8 tests) — see below. |
| `core/test/…/simplified/SimpleCastCoordinatorTest.kt` | :core test | Updated 4 existing CastProfile refs to the new `of()` API (named constants removed). |
| `core/test/…/simplified/CastCoordinatorTestFakes.kt` | :core test | R8: `FakePrefs.profileKey` mirrors real impl (`isFull`/`key`). |
| `docs/specs/cast-nav-ux-release-v104.html` | docs | §Design D3 implementation notes; §Tasks Wave 2 DONE; §Reviewer Log Pass 2 (append-only). |

## New tests — `CastProfileDensityTest` (:core, 8)
1. `SPLIT_PERCENTS is the full 9-ratio step-10 set` — `[10..90 step 10]`.
2. `9 percents map to 9 distinct profiles per side, 19 profiles total` — `L10…L90`, `R10…R90`, `FULL`; 19 distinct keys.
3. `profile key round-trips through fromKey, including predecessor keys` — `of(side,pct)==fromKey(key)`; `L30`/`R70` legacy; malformed → null.
4. `unknown or legacy percent falls back to default 50` — `normalizePercent(55/0/100/-1)==50`; `fromKey("L60")→60`, `fromKey("L55")→50`.
5. `prefs round-trips bounds AND density across distinct profiles` — L20 / R80 / FULL, no cross-contamination, no-arg==FULL.
6. `split DPI persists under the per-ratio profile of each occupied slot (#5)` — L+R@ratio20, `setDensitySplit(200)` → `__L20`/`__R20` density=200; FULL untouched.
7. `re-cast applies the DPI saved for that ratio profile on restore (#5)` — save 200@L20 → stop → re-cast L@20 → shell emits `wm density 200 -d 1`.
8. `setDensitySplit is a no-op outside split state` — in Idle, nothing persisted.

(Also confirmed still green: existing `SimpleCastCoordinatorTest` profile/resize tests, `LayeringRulesTest`, `:core` purity.)

## Verify (off-car)
Command:
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :core:test :app:testDebugUnitTest :car-integration:test :app:assembleDebug --console=plain
```
Result: **BUILD SUCCESSFUL in 1m 53s**

Test totals (0 failures, 0 errors):
- `:core:test` — **698** (was 690; +8 from `CastProfileDensityTest`)
- `:app:testDebugUnitTest` — **325**
- `:car-integration:test` — **28**
- Total **1051**, 0 failures.

## Constraints honored
- No new dependency (W1); SharedPreferences kept (no DataStore migration).
- `:core` stays Android-free (`CastProfile` pure class); `LayeringRulesTest` green; `pureFilesStillInApp==2` unchanged.
- Both prefs impls updated together (real `SharedPrefsSimpleCastPrefs` + `FakePrefs`).
- No file >500 LOC (coordinator 498, models 306, density 68, editor 267, autostart 151, new test 212).
- Did not touch speed-sign/HUD injection files. No commit/push/vehicle-adb.

## On-car (left for checklist — NOT verified here)
- Adjust size+DPI at several ratios (e.g. 20/80, 80/20) → exit/re-cast restores exact size + DPI per ratio.
