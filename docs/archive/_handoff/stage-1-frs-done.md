# Stage 1 (cast-freeform-resize-split) — Handoff

> Spec: `docs/specs/cast-freeform-resize-split.html` · Stage 1 = Core foundation + Bubble UI.
> Each agent appends its own section. Do not overwrite another agent's section.

## Agent 1B — T2 Bubble redesign (R2)

**Status:** DONE (off-car). Bubble tests green, no new compile errors.

### Files changed (4, all within owned scope)
- `app/src/main/java/com/byd/clusternav/modules/clustercast/BubbleRenderer.kt`
  - `buildBubbleLayout` rewritten: was VERTICAL (FULL on top, LEFT+RIGHT row below) → now a **single HORIZONTAL** `LinearLayout` with **3 equal-size zones** in order **Trái · Phải · Full**. All three use the same size (`HALF_ZONE_WIDTH_DP` × `ZONE_MIN_DP`); first zone no left margin, next two separated by `ZONE_GAP_DP`. `createZoneView`, tap wiring (`setOnClickListener` + haptic), and the `zoneViews` map are unchanged — ordering in the row does not affect painting/state lookups.
  - `paintOccupied` / `paintEmpty` / `paintDisabled`: fill color changed from opaque `BRAND`/`BRAND_LIGHT` to translucent `FILL_OCCUPIED`/`FILL_EMPTY`/`FILL_DISABLED`. Opaque `BRAND` stroke retained on every zone for a crisp border; text colors unchanged (WHITE on occupied, `BRAND` blue on empty/disabled) to keep labels legible. `DISABLED_ZONE_ALPHA` (view-level) unchanged.
- `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/v2/CastBubbleProjection.kt`
  - `zoneShortLabel(BubbleZone.FULL)` changed `"Cả cụm"` → `"Full"` (ONLY this string). `zoneName(FULL)` kept as `"cả cụm"` (used in spoken/screen-reader sentences). LEFT `"Trái"` / RIGHT `"Phải"` unchanged. Pure Kotlin, `:core` stays Android-free.
- `app/src/test/java/com/byd/clusternav/modules/clustercast/BubbleAccessibilityTest.kt` (test) — label + constants + fills.
- `app/src/test/java/com/byd/clusternav/modules/clustercast/CastUILifecycleSafetyTest.kt` (test) — ZONE_MIN_DP.

### New / changed constants (BubbleRenderer companion)
- `ZONE_MIN_DP = 48` (was `40`). Smallest value that still honours the ≥48dp automotive touch-target guideline (owner wanted compact zones). Enforced via `minimumWidth`/`minimumHeight` in `createZoneView`, so the 35dp layout width still renders ≥48dp.
- Removed `BRAND_LIGHT` (`0xFFE6F1FB`) — became dead once fills went translucent (was only used by the empty/disabled fills + its own test).
- Kept `BRAND = 0xFF1565C0` (opaque) for zone strokes + empty/disabled text (legibility).
- Added translucent fills (same brand-blue RGB `0x1565C0`, alpha byte only differs):
  - `FILL_OCCUPIED = 0x991565C0` (alpha `0x99` ≈ 60%) — "casting" but see-through.
  - `FILL_EMPTY    = 0x331565C0` (alpha `0x33` ≈ 20%) — idle, content dominant.
  - `FILL_DISABLED = 0x1F1565C0` (alpha `0x1F` ≈ 12%) — faintest, paired with `DISABLED_ZONE_ALPHA=0.35f`.

### Label change + test files touched for the label
- Producer: `CastBubbleProjection.zoneShortLabel(FULL)` → `"Full"`.
- Only one test asserted the old value: `BubbleAccessibilityTest` (`zone short label for FULL` test → now expects `"Full"`; the empty content-description sample string also updated to `"Full · chạm để chiếu"`). Core `CastBubbleProjectionTest` never calls `zoneShortLabel` (its "cả cụm" occurrences are comments/messages only) → unaffected. No other test/source hard-codes the FULL short label.

### Tests updated
- `BubbleAccessibilityTest`: FULL label → `"Full"`; `ZONE_MIN_DP` assert `56`→`48` (+ `>=48`); "17% margin" test → "never below 48dp guideline" (margin `>= 0`); §8 BRAND/BRAND_LIGHT opaque tests replaced with translucency asserts (`FILL_OCCUPIED`/`FILL_EMPTY`/`FILL_DISABLED` alpha `< 0xFF`, empty lighter than occupied) + one assert that `BRAND` stays opaque for legibility.
- `CastUILifecycleSafetyTest`: `zone minimum dp is 56` → `is 48` (asserts `== 48` and `>= 48`). The existing `exceeds 48dp` test (`>= 48`) still passes.

### Not touched (other agents' scope)
`SimpleCastModels`, `SimpleCastCoordinator`, `SimpleCastRuntime`, `CastAutostart`, `FloatingBubbleService` (window-level `IDLE_ALPHA`/`ACTIVE_ALPHA` fade left intact).

### Context7 / tech-freshness note
Only Android SDK framework classes used (`LinearLayout`, `GradientDrawable.setColor(Int)`, packed-ARGB int literals, `HapticFeedbackConstants.VIRTUAL_KEY`). Context7 has no core Android-framework index (only Firebase Android SDK returned). None of these APIs are deprecated on API 34; kept the existing packed-int convention (`0x..toInt()`) rather than `Color.argb(...)` for consistency with the pre-existing `BRAND` constant.

### Exit-gate evidence
Command: `./gradlew :core:test :app:testDebugUnitTest` (JAVA_HOME = Homebrew openjdk@17, 17.0.19)

```
> Task :core:compileKotlin
> Task :core:test
> Task :app:compileDebugKotlin
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 1m 46s
33 actionable tasks: 8 executed, 25 up-to-date
```

Per-class result XML (0 failures / 0 errors):
- `BubbleAccessibilityTest` — tests=27, failures=0, errors=0
- `CastUILifecycleSafetyTest` — tests=15, failures=0, errors=0
- `CastBubbleProjectionTest` (core, label-adjacent) — tests=28, failures=0, errors=0

---

## Agent 1A — T4-core + T3 (R3/R4/R5/R6/R7/R8)

**Status:** DONE (off-car). All new/changed tests green; no new compile failures introduced. `:core:test` = 650 tests / 0 fail; `:app:testDebugUnitTest` = 280 tests / 0 fail.

### Baseline (T0) recorded — pre-change red tests (NOT mine to fix)
`./gradlew :core:test :app:testDebugUnitTest` before any edit → **278 completed, 4 failed**, all `ZONE_MIN_DP` (Agent 1B's T2 scope):
- `BubbleAccessibilityTest > ZONE_MIN_DP is 56 exceeding 48dp automotive guideline`
- `BubbleAccessibilityTest > ZONE_MIN_DP provides 17 percent margin over guideline`
- `CastUILifecycleSafetyTest > zone minimum dp is 56`
- `CastUILifecycleSafetyTest > zone minimum dp exceeds 48dp automotive guideline`

`:core:test` was already green at baseline. (These 4 are now green because Agent 1B set `ZONE_MIN_DP=48` in parallel — not my change.)

### Files changed (owned scope only)
- **`core/.../simplified/SimpleCastModels.kt`** (222→253 LOC) — added `CastProfile` enum + extended `SimpleCastPrefs` interface with two profile-scoped methods.
- **`core/.../simplified/SimpleCastCoordinator.kt`** (597→**492** LOC) — added `resizeActiveSlot`; profile-aware `handleCastSlot`/`handleCastFull`; delegated freeform-geometry + cluster-display stack queries to the new helper (see split note).
- **`core/.../simplified/CastGeometryController.kt`** (**NEW**, 158 LOC, pure JVM) — split out of the coordinator to satisfy ≤500 LOC and centralize the "persist geometry ONLY on shell success" rule.
- **`app/.../simplified/SimpleCastRuntime.kt`** (236→251 LOC) — `SharedPrefsSimpleCastPrefs` profile impl + key scheme.
- **`app/.../clustercast/CastAutostart.kt`** (195→198 LOC) — `populateSplitRatioSpinner` reduced to 3 ratios + backfill.
- **`core/.../simplified/SimpleCastCoordinatorTest.kt`** (402→492 LOC) — `FakePrefs` profile impl + 4 new tests + `awaitTrue` helper.

### Coordinator split (why a new :core file)
`SimpleCastCoordinator` was already **597 LOC** (pre-existing debt) before my additions. Extracted the freeform-geometry cluster into `CastGeometryController` (pure JVM): `findTaskIdForPkg`, `resizeFull` (was `resizeActiveTarget` body incl. `wm size` fallback), `resizeSlot` (new), `applySavedProfile` (was `applySavedPreferences`, now profile-aware), `ensureFreeformFlags`, `isFreeformAlive`, `isAppOnDisplay`, `verifyFullscreenStackAvailable`, `queryDisplayPhysicalSize`. Coordinator keeps thin public wrappers (`resizeActiveTarget`, `resizeActiveSlot`, `isFreeformAlive`) so external callers (`CastGeometryEditor`, etc.) are unaffected. Removed dead `companion.parseForeground` (no callers). **Behavior preserved** — same shell command strings/order. `foregroundPackage`/`dismissPipOnDisplay` left in the coordinator (external callers, separate concern).

### New prefs key scheme (R4/R8, backward-compatible)
`SimpleCastPrefs` gained profile overloads; the no-arg ones now delegate to `CastProfile.FULL`.
- **FULL** → legacy no-suffix keys: `config_size_<pkg>`, `config_overscan_<pkg>`, `config_density_<pkg>`, `config_bounds_<pkg>` (existing saved full configs survive untouched).
- **Other profiles** → same keys with `__<profile.name>` appended: e.g. `config_bounds_<pkg>__L30`, `config_size_<pkg>__R70`.
- Helper `profileKey(pkg, profile) = if (FULL) pkg else "${pkg}__${profile.name}"`, implemented identically in **both** `SharedPrefsSimpleCastPrefs` and `FakePrefs` so the coordinator tests exercise the real non-collision semantics.

### CastProfile API (R4) — pure Kotlin, no Android import
```kotlin
enum class CastProfile { FULL, L50, R50, L30, R30, L70, R70;
  companion object {
    fun of(side: ClusterSlotSide, leftPercent: Int): CastProfile = when (side) {
      ClusterSlotSide.LEFT  -> when (leftPercent) { 30 -> L30; 70 -> L70; else -> L50 }
      ClusterSlotSide.RIGHT -> when (leftPercent) { 30 -> R30; 70 -> R70; else -> R50 }
    }
  }
}
// SimpleCastPrefs additions:
fun displayConfigFor(pkg: String, profile: CastProfile): DisplayConfig?
fun saveDisplayConfig(pkg: String, profile: CastProfile, config: DisplayConfig)
```

### resizeActiveSlot signature + behavior (R5/R6)
```kotlin
fun resizeActiveSlot(side: ClusterSlotSide, left: Int, top: Int, right: Int, bottom: Int)
```
- Runs on the serial executor; **no-op unless state is `CastingSplit`** and the targeted `side` currently holds an app.
- Resolves that slot's pkg → `geometry.findTaskIdForPkg` on the cluster display → `am task resize <task> l t r b`.
- Persists `CastBounds` to profile `CastProfile.of(side, prefs.splitRatioLeftPercent())` **ONLY on shell success**. No `wm size` fallback for slots (split needs per-task bounds; `wm size` is display-global). CP/AA untouched (`CastSlotValidator` unchanged).

### handleCastSlot / handleCastFull profile behavior (R6/R7)
- `handleCastSlot`: after a **verified** landing it calls `geometry.applySavedProfile(pkg, CastProfile.of(side, leftPercent))` — restores saved bounds (per-task `am task resize`) **and** DPI. If no profile saved → the ratio-default bounds from `AppMover.fitToCluster` stand.
- `handleCastFull`: after a verified landing (NORMAL only) calls `geometry.applySavedProfile(pkg, CastProfile.FULL)`.
- CP/AA path unchanged: still full-only, `verifyFullscreenStackAvailable` still gates protected casts (relocated verbatim into the helper, still invoked from `handleCastFull`).

### T3 spinner (R3)
`populateSplitRatioSpinner`: `options=["50/50","30/70","70/30"]`, `percentValues=[50,30,70]`. Backfill: if stored `splitRatioLeftPercent()` ∉ {50,30,70} → coerce to 50, **re-persist**, and select index 0 (so coordinator reads a valid ratio).

### Test additions (all pass, 0 failures in `SimpleCastCoordinatorTest.xml`)
1. `CastProfile of maps side and leftPercent` — all 6 mappings + out-of-set → 50-variant.
2. `prefs round-trip per profile uses distinct non-colliding keys` — FULL/L30/R70 saved distinctly, no-arg reads FULL, untouched profiles null.
3. `resizeActiveSlot persists to the matching profile on shell success` — LEFT×ratio30 → L30 bounds persisted; FULL stays null.
4. `resizeActiveSlot does not persist when am task resize fails` — RIGHT×ratio70; `failCommands += "am task resize"` → R70 stays null.

### Context7 / tech-freshness
`SharedPreferences.edit()`/`apply()` — Context7 (`/websites/developer_android`) returned no deprecation record; these remain current stable Android APIs (androidx-core adds the `edit {}` KTX sugar but the base API is not deprecated). Kept the existing `sp.edit()...apply()` pattern for consistency; no androidx dependency introduced into `:core` (stays pure JVM).

### Exit-gate evidence
Command: `./gradlew :core:test :app:testDebugUnitTest` (JAVA_HOME = Homebrew openjdk@17, 17.0.19)
```
> Task :core:compileKotlin
> Task :core:test
> Task :app:compileDebugKotlin
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 1m 48s
33 actionable tasks: 11 executed, 22 up-to-date
```
Totals: `:core:test` tests=650 failures=0 errors=0 · `:app:testDebugUnitTest` tests=280 failures=0 errors=0. `LayeringRulesTest` green (`:core` pure incl. new `CastGeometryController`; `pureFilesStillInApp==2` held). All touched files ≤500 LOC (coordinator 492).

### Contract notes for Stage 2 (Agent 2B — split resize UI)
- Call `coordinator.resizeActiveSlot(side, l, t, r, b)` from the per-slot drag editor; it self-guards on state/side/bounds and persists on success.
- `resizeActiveTarget(l,t,r,b)` (full) is unchanged (public wrapper, delegates to `geometry.resizeFull`).
- Persistence is profile-scoped; the profile for a slot is derived from the **current** `splitRatioLeftPercent()`, so read/set the ratio before resizing if the UI changes it.
