# Stage 2 (cast-freeform-resize-split) — Handoff

> Spec: `docs/specs/cast-freeform-resize-split.html` · Stage 2 = split-resize UI (T4) + autostart owner (T1).
> Each agent appends its own section. Do not overwrite another agent's section.

## Agent 2B — T4 split-resize UI (R5/R6)

**Status:** DONE (off-car). Both exit gates green; no new compile errors; no new deprecation warnings.

### Files changed (2, owned scope only)
- `app/src/main/java/com/byd/clusternav/modules/clustercast/CastResizeView.kt` (150 → **192** LOC)
  — added horizontal slot-band support (`setSlotBand`), band-aware clamping, and out-of-band shading.
- `app/src/main/java/com/byd/clusternav/modules/clustercast/CastGeometryEditor.kt` (232 → **265** LOC)
  — replaced the split-mode DPI-only setup with per-slot resize editors + a display-global DPI row.

Did NOT touch: `FloatingBubbleService`, `CastAutostart`, `MainActivityCastController` (Agent 2A),
`BubbleRenderer`, or any `:core` file. The coordinator API (`resizeActiveSlot`) was consumed, not modified.

### 1. `CastResizeView.setSlotBand(minX: Int, maxX: Int)` — new API (R5)
```kotlin
fun setSlotBand(minX: Int, maxX: Int)   // cluster coords; clamps rectLeft/rectRight into [minX, maxX]
```
- New private fields `bandMinX = 0`, `bandMaxX = clusterWidth` → **default = whole cluster**, so
  existing full-mode usage (`resizeActiveTarget` path in `setupResizeControls`) is unchanged.
- `toClusterX` now `coerceIn(bandMinX, bandMaxX)` (was `coerceIn(0, clusterWidth)`); the final X clamp
  in `applyDrag` and `setBounds` likewise clamp X into the band and Y into `[0, clusterHeight]`.
- `setSlotBand` clamps `minX/maxX` sanely (`maxX ≥ minX+1`), re-clamps the current rect into the band,
  and guards against a degenerate `rectRight ≤ rectLeft`.
- **CENTER (move) drag fixed for bands**: the move now coerces the box within `[toViewX(bandMinX),
  toViewX(bandMaxX) - w]` so it cannot collapse at a band edge. In full mode (`bandMinX=0`,
  `bandMaxX=clusterWidth`) this reduces to the original `[0, width - w]` → **no behavior change**.
- Edge/corner drags already enforce `minW=480`/`minH=180`; every band (min 30% of 1920 = 576 px) ≥ minW,
  so slots never collapse. Out-of-band area is shaded (`Color.argb(130,0,0,0)`) for a clear draggable half.

### 2. `CastGeometryEditor.updateVisibility(CastingSplit)` — per-slot editors (R5)
- `CastingSplit` branch now calls `setupSplitResizeControls(state)` (was `setupDisplayGlobalDpi()`,
  now removed — no dead code).
- For **each occupied slot** (`state.left` / `state.right`) it builds a `CastResizeView` via
  `buildSlotEditor(...)`:
  - `split = clusterWidth * leftPercent / 100` using `coordinator.prefs.splitRatioLeftPercent()`
    (coerced to `[1, clusterWidth-1]`).
  - **LEFT band = [0, split]**, **RIGHT band = [split, clusterWidth]** via `resizeView.setSlotBand(...)`.
  - Cluster dims read from a present slot's `displayConfig.wmSize` (fallback `1920×720`).
  - Drag-end → `coordinator.resizeActiveSlot(side, l, t, r, b)` for the correct side (the coordinator
    self-guards on state==CastingSplit + slot occupancy and persists to `CastProfile.of(side, ratio)`
    on shell success — Stage 1 contract).
- Editors are **stacked vertically** with labels `Trái` / `Phải` (allowed by "side by side or stacked").
- A single display-global **DPI control** is kept (`buildSplitDpiRow`) with label `(áp cho cả cụm)` /
  `(whole cluster)` + contentDescription noting it affects the whole cluster (Android-10 `wm density`
  is display-global — D4 OS limitation).
- Rebuild guard: container tag `split_resize_<leftPkg>_<rightPkg>_<leftPercent>` → no rebuild (and no
  loss of in-progress drag) while the split identity is unchanged; changes when slots/ratio change.

### 3. Restore behavior (R6)
`buildSlotEditor` initial bounds:
```kotlin
val saved = coordinator.prefs.displayConfigFor(slot.pkg, CastProfile.of(side, leftPercent))?.bounds
if (saved != null) resizeView.setBounds(saved.left, saved.top, saved.right, saved.bottom)
else               resizeView.setBounds(bandMinX, 0, bandMaxX, clusterHeight)   // ratio-default band rect
```
Saved profile bounds win; otherwise the slot fills its ratio-default half.

### 4. CP/AA path unchanged (R7)
`CastingFull && appType.isProtected` → `geometryContainer` stays `GONE` (untouched). Only NORMAL split
slots get editors; CP/AA never reach `CastingSplit` (`CastSlotValidator`).

### Layering (R8)
No new files added → `LayeringRulesTest.pureFilesStillInApp` stays **2**. Both edited files import
`android.*` and use `View`/`Activity`, so neither counts as a "pure" file. `LayeringRulesTest` green
(part of the passing `:app:testDebugUnitTest`).

### Context7 / tech-freshness
Android SDK framework only: `View.onDraw/onTouchEvent`, `Canvas.drawRect`, `MotionEvent.ACTION_*`,
`LinearLayout`/`FrameLayout`/`TextView`/`Button`, `Paint`, `parent.requestDisallowInterceptTouchEvent`.
Per Stage 1's finding, Context7 has no core Android-framework index (only Firebase Android SDK is
returned), so no doc pull was possible; all classes used are stable and **non-deprecated on API 34**.
Build shows **zero new** deprecation warnings from my two files (the only `FLAG_FULLSCREEN` deprecation
warnings are pre-existing in `ClusterBlackActivity`, not in scope).

### Exit-gate evidence
Commands (JAVA_HOME = Homebrew openjdk@17):

`./gradlew :core:test :app:testDebugUnitTest`
```
> Task :app:compileDebugKotlin
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 1m 1s
```
Aggregate JUnit XML totals (core + app): **tests=930, failures=0, errors=0** (= Stage 1's 650 core + 280 app; UI-only change added no tests).

`./gradlew :app:assembleDebug`
```
> Task :app:packageDebug
> Task :app:assembleDebug
BUILD SUCCESSFUL in 1s
```
Note: a first `assembleDebug` invocation hit a spurious Kotlin Build-Tools-API incremental-compilation
error; `:app:compileDebugKotlin --rerun-tasks` recompiled with **only pre-existing warnings (no `e:`
errors)** and the clean re-run built successfully.

### File sizes (≤500 LOC guardrail)
`CastGeometryEditor.kt` = 265 · `CastResizeView.kt` = 192.

---

## Agent 2A — T1 autostart fix (R1)

**Status:** DONE (off-car). Exit gate green; no new compile errors; no new warnings.

### Root cause (confirmed) & fix
TWO autostart drivers both listened for `Idle` and both dispatched split → the 2nd `CastSlot`
hit `SLOT_OCCUPIED` → `setError` → auto-recover to `Idle`, **wiping `CastingSplit`** → bubble
painted empty. The two drivers were `FloatingBubbleService.dispatchBootAutoStart()` **and**
`CastAutostart.dispatchAutoStartIfEnabled()` (Activity). Fix = make the **service the sole driver**
and remove the Activity dispatch; sequence RIGHT off the coordinator state instead of a blind delay.

### Files changed (3 edited, 1 new — see decision note)
- `app/.../clustercast/FloatingBubbleService.kt` (330 → **409** LOC)
  — `dispatchBootAutoStart` rewritten as the SOLE driver; added `dispatchAutoFull` + `dispatchAutoSplit`
    helpers; added re-entry guard + two tracked listener refs; `onDestroy` now detaches them.
- `app/.../clustercast/CastAutostart.kt` (198 → **143** LOC)
  — deleted `dispatchAutoStartIfEnabled()` (body + the call in `setup()`) and its now-dead
    `scheduler`/`fullAutoListener`/`splitAutoListener` fields + 7 unused imports. **Kept** all setup UI:
    `setup()`, `populateAutoStartSpinner`, `populateSplitRatioSpinner` (R3 backfill untouched), `destroy()`.
- `core/.../simplified/SimpleCastCoordinatorTest.kt` (492 → **439** LOC)
  — appended 2 new `@Test` methods (did NOT modify Agent 1A's tests); removed the now-unused
    `CountDownLatch`/`TimeUnit` imports.
- `core/.../simplified/CastCoordinatorTestFakes.kt` (**NEW**, 131 LOC) — see decision note below.

**Did NOT touch:** `MainActivityCastController.kt` (its `setup()`/`destroy()` calls to `CastAutostart`
still valid — signatures unchanged, so no call had to be dropped), `CastSlotValidator` (CP/AA
protection intact — R7/§3), `CastGeometryEditor`/`CastResizeView` (Agent 2B), `BubbleRenderer`, and
all `:core` **main** source (coordinator behavior unchanged — sequencing lives in the service).

### New sequencing logic (R1) — state-driven, not timed
`dispatchBootAutoStart(coordinator)`:
1. `if (autoStartDispatched) return` — re-entry guard.
2. Read `autoStartEnabled()` / `autoStartSplitEnabled()` via `coordinator.prefs` (single key source —
   replaces the old raw `getSharedPreferences("simple_cast_prefs")` string keys; verified same file +
   keys as `SharedPrefsSimpleCastPrefs`).
3. Register ONE `idleListener`; on the first `Idle` it detaches itself, sets `autoStartDispatched = true`,
   then routes to full or split.
- **Full**: `dispatch(CastFull(pkg, classifyApp(pkg)))`.
- **Split**: `dispatch(CastSlot(LEFT))`, then register a `rightListener` that dispatches
  `CastSlot(RIGHT)` **only** once the coordinator reports `CastingSplit` with `left != null` (verified
  landing) — replacing the old `handler.postDelayed(2000)`. If LEFT fails (transient `Error → Idle`),
  it never reaches `CastingSplit`, so RIGHT is never dispatched → **no collision, no wipe**. Left-only
  / right-only splits cast just that side.

### Guard + lifecycle
- `@Volatile private var autoStartDispatched = false` — a re-entrant `onStartCommand`/`onCreate` never
  double-dispatches within one service instance.
- `@Volatile autoStartIdleListener` / `autoStartSplitRightListener` are held and detached in
  `onDestroy` (fixes a latent listener leak when projection never reached `Idle`).

### Tests added (both in `SimpleCastCoordinatorTest.kt`, green)
1. `autostart split sequences LEFT then RIGHT into CastingSplit with no Error` — dispatch LEFT, await
   `CastingSplit(left)`, dispatch RIGHT, await `CastingSplit(left,right)`; asserts final state has both
   slots **and no `Error` state was recorded** in between (R1 positive proof of the service sequencing).
2. `dispatching the same slot twice is rejected SLOT_OCCUPIED without returning the other slot` —
   establish `CastingSplit(left,right)`, re-dispatch the (occupied) LEFT slot → asserts transient
   `Error` whose message contains `SLOT_OCCUPIED`, **and** that no `--display 0` (return-to-main)
   command was issued for the other slot (the reject path touches no shell → the other app is not
   torn down). Uses existing `FakeShell`/`awaitState`/`awaitTrue` patterns.

### Autonomous decision — fakes extraction (documented per constraint conflict)
Appending 2 tests pushed `SimpleCastCoordinatorTest.kt` to 564 LOC, over the ≤500 guardrail that
Agent 1A deliberately stayed under (492). Since the task mandates the tests live in *that* file, I
extracted the shared `FakeShell`/`FakePrefs` (already used by `CastCoordinatorPolicyEnforcementTest`
and `CastSafetyTest` too) **verbatim, no logic change** into a sibling `CastCoordinatorTestFakes.kt`
(same package → no consumer edits needed). Result: test file back to **439 LOC**, fakes file 131 LOC,
and shared infra now lives in one place. Consumer suites re-verified green
(`CastCoordinatorPolicyEnforcementTest`=14/0, `CastSafetyTest`=19/0).

### Context7 / tech-freshness
New code uses only Android SDK: `Service` lifecycle, `android.os.Handler(Looper.getMainLooper())`
(non-deprecated constructor, already in-file), and prefs via the `SimpleCastPrefs` interface (no raw
`SharedPreferences` in the new code). Per Stage 1's finding, Context7 has no core Android-framework
index (only Firebase Android SDK is returned); all APIs used are **stable, non-deprecated on API 34**.
Build shows zero new deprecation warnings from the changed files.

### Exit-gate evidence
Command (JAVA_HOME = Homebrew openjdk@17): `./gradlew :core:test :app:testDebugUnitTest`
```
> Task :core:compileTestKotlin
> Task :core:compileTestJava NO-SOURCE
> Task :core:testClasses UP-TO-DATE
> Task :core:test

BUILD SUCCESSFUL in 48s
33 actionable tasks: 2 executed, 31 up-to-date
```
Aggregate JUnit XML totals: **`:core:test` tests=652 failures=0 errors=0** (Stage 1's 650 + my 2),
**`:app:testDebugUnitTest` tests=280 failures=0 errors=0**. `SimpleCastCoordinatorTest`=27/0/0.

### File sizes (≤500 LOC guardrail)
`FloatingBubbleService.kt` = 409 · `CastAutostart.kt` = 143 · `SimpleCastCoordinatorTest.kt` = 439 ·
`CastCoordinatorTestFakes.kt` = 131. All ≤ 500.
