# Cast + Nav UX v1.04 — Stage 3 (Wave 3) done

> Spec: `docs/specs/cast-nav-ux-release-v104.html` (R5 / #7 — single-icon floating bubble)
> Date: 2026-08-11 · Off-car only · **No commit/push/vehicle-adb** (per `docs/_handoff/AUTONOMOUS-RESUME.md`)
> Reads: `cnux-stage-1-done.md` (Wave-1 launcher guard) + `cnux-stage-2-done.md` (profiles/DPI)

## Scope status
- **R5 (#7 — 1 nav-arrow icon; tap = toggle full; long-press = Trái/Phải/Cấu hình submenu; drag in all states)** — ✅ code + test + wired
- No gaps in Wave 3 scope. R8/R9 honoured (files ≤500; `:core` Android-free; CP/AA full-only untouched; speed-sign/HUD injection untouched; Wave-1 launcher guard preserved verbatim).

## New bubble view structure
- The bubble is now **ONE view**: an `ImageView` showing only `R.drawable.ic_bubble_nav` (a Material navigation arrow vector, brand-blue `#1565C0`, **no background/border/fill plate**).
- Sizing: `background = null`; `minimumWidth/Height = ICON_SIZE_DP (52dp)`, which is ≥ `TOUCH_MIN_DP (48dp)` automotive floor; `ICON_PADDING_DP (6dp)` inset so the arrow reads as an app-icon glyph. Window is WRAP_CONTENT, so the icon min drives the bubble box.
- Idle/active dimming is the **window alpha** (`IDLE_ALPHA 0.35` / `ACTIVE_ALPHA 1.0`) owned by `FloatingBubbleService` — unchanged; the icon itself is never repainted with a background.
- `refreshFromState(state)` only swaps the **content description** (accessibility), via the pure `BubbleRenderer.contentDescriptionFor(state)`:
  - Idle → `"ClusterNav cast"`
  - CastingFull / CastingSplit → `"ClusterNav cast · chạm để trả về"`
  - transient (Off/Opening/Stopping/Closing/Error) → `"ClusterNav cast · đang xử lý"`
- The old 3-zone layout (Trái/Phải/Full `TextView`s), its painting (`paintOccupied/paintEmpty/paintDisabled`), its zone-hit-test (`isZoneDisabled`) and its `zoneViews` map are **removed**.

## Gesture mapping (single icon)
| Gesture | Detected by | Action |
|---------|-------------|--------|
| **TAP** (down/up within touch slop) | `GestureDetector.onSingleTapUp` → `FloatingBubbleService.onBubbleTap()` → `submitTapAction("bubble-tap")` → `BubbleActionDispatcher.onTap()` | **Toggle full.** `BubbleGesturePlanner.tapOutcome(state)`: Idle → `detectForeground` (Wave-1 guard) → `SimpleCastIntent.CastFull`; CastingFull/CastingSplit → `SimpleCastIntent.Stop()` (slot-less → returns everything to gauges); transient → "Đang chuẩn bị cụm…" toast. |
| **LONG-PRESS** (held past long-press timeout, no move) | `GestureDetector.onLongPress` → `FloatingBubbleService.onBubbleLongPress()` | Shows `BubbleSubmenuOverlay`. Second long-press toggles it closed. |
| **DRAG** (moved past `scaledTouchSlop`) | manual `ACTION_MOVE` in `BubbleGestureHandler.attachDrag` | Moves the overlay window (works in **all** states). Sets `dragging=true` which suppresses tap AND long-press (detector cancels the pending long-press on scroll; `!dragging` guards close the threshold race). |

Tap/long-press disambiguation is done by a `GestureDetector.SimpleOnGestureListener`; the drag stays manual so the window follows the finger. Long-press can **never** fire mid-drag.

## Submenu implementation
- `BubbleSubmenuOverlay` (new, Android): a **full-screen transparent scrim** window (`FLAG_NOT_FOCUSABLE`, `TYPE_APPLICATION_OVERLAY`) with a centered brand-blue card.
  - Rows come from `BubbleGesturePlanner.submenuItems()` → **Trái / Phải / Cấu hình** (in that order = TalkBack order).
  - Each row is a `TextView` with `minimumHeight = BubbleRenderer.TOUCH_MIN_DP (≥48dp)` + horizontal padding + haptic on tap.
  - **Dismiss on outside tap:** the scrim `setOnClickListener { dismiss() }`; the card is `isClickable` so its taps don't fall through.
  - **Dismiss on choice:** row tap → `dismiss()` then `onAction(item.action)`.
- Routing (`FloatingBubbleService.onBubbleLongPress`): for each chosen `BubbleMenuAction`,
  - `slotFor(action) != null` (Trái/Phải) → `submitTapAction("submenu-…")` (token-gated, off main thread because `detectForeground` does shell I/O) → `BubbleActionDispatcher.onSubmenuAction` → `onCastSlot(side)` → `SimpleCastIntent.CastSlot(fg, side)` (same Wave-1 launcher guard).
  - `slotFor(action) == null` (Cấu hình) → `onSubmenuAction(OPEN_CONFIG)` → `openConfig()` = `startActivity(Intent(ctx, MainActivity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK))` on the main thread (not gated by the cast token).

## Decision logic (pure, `:core`) vs Android glue
`BubbleGesturePlanner` (new, Android-free, `:core`) holds every branch that doesn't need Android, so it is unit-tested off-car:
```kotlin
enum class BubbleTapOutcome { CAST_FULL, RETURN, PREPARING }
fun tapOutcome(state): BubbleTapOutcome           // Idle→CAST_FULL; CastingFull/Split→RETURN; else PREPARING
enum class BubbleMenuAction { CAST_LEFT, CAST_RIGHT, OPEN_CONFIG }
data class BubbleSubmenuItem(val label, val action)
fun submenuItems(): List<BubbleSubmenuItem>       // [Trái/CAST_LEFT, Phải/CAST_RIGHT, Cấu hình/OPEN_CONFIG]
fun slotFor(action): ClusterSlotSide?             // LEFT / RIGHT / null(OPEN_CONFIG)
```
The Android UI (drag mechanics, gesture detection, overlay windows, `startActivity`) is not unit-testable without Robolectric (the app test source has none — `LayeringRulesTest` even treats `Robolectric` as an Android signal), so its wiring is locked by a **source-contract test** (`BubbleGestureContractTest`), the same pattern the repo already uses in `FloatingBubbleFirstLaunchContractTest`.

## Files changed
| File | Module | Change |
|------|--------|--------|
| `core/…/simplified/BubbleGesturePlanner.kt` | :core | **NEW** — pure tap/submenu/slot decision logic (78 LOC). |
| `app/…/clustercast/BubbleRenderer.kt` | :app | **Rewrite** — single transparent nav-arrow `ImageView`; `contentDescriptionFor`; `ICON_SIZE_DP`/`TOUCH_MIN_DP`. Removed 3-zone painting + `zoneViews` + `isZoneDisabled` (103 LOC). |
| `app/…/clustercast/BubbleGestureHandler.kt` | :app | GestureDetector tap+long-press; kept manual drag threshold; `onTap`/`onLongPress` ctor params (174 LOC). |
| `app/…/clustercast/BubbleActionDispatcher.kt` | :app | **Rewrite** — `onTap` (toggle), `onCastSlot`, `onSubmenuAction`, `openConfig`; `detectForeground`/`homePackages` **unchanged** (Wave-1 guard) (130 LOC). |
| `app/…/clustercast/BubbleSubmenuOverlay.kt` | :app | **NEW** — long-press submenu overlay (scrim + 3 ≥48dp rows, dismiss on choice/outside) (129 LOC). |
| `app/…/clustercast/FloatingBubbleService.kt` | :app | Wire onTap/onLongPress; `buildBubble()`; submenu show/dismiss; `bubble: View`; dismiss submenu in `onDestroy`; size fallback → `ICON_SIZE_DP`. onCreate/onStartCommand/onDestroy contract strings intact (~430 LOC). |
| `app/src/main/res/drawable/ic_bubble_nav.xml` | :app | **NEW** — Material navigation-arrow vector, brand-blue, no plate. |
| `core/test/…/simplified/BubbleGesturePlannerTest.kt` | :core test | **NEW** (9). |
| `app/test/…/clustercast/BubbleAccessibilityTest.kt` | :app test | **Rewrite** — single-icon content-desc + ≥48dp; dropped 3-zone labels/fills. |
| `app/test/…/clustercast/CastUILifecycleSafetyTest.kt` | :app test | Updated — 48dp icon target; removed disabled-zone no-op tests (concept gone). |
| `app/test/…/clustercast/BubbleGestureContractTest.kt` | :app test | **NEW** (12) — Android wiring source-contract. |
| `docs/specs/cast-nav-ux-release-v104.html` | docs | §Design D4 impl notes; §Tasks Wave 3 DONE; §Reviewer Log Pass 3 (append-only). |

## Tests (what each new test asserts)
- **`BubbleGesturePlannerTest`** (:core, 9): tap Idle→CAST_FULL / casting→RETURN (full + split) / transient→PREPARING; every state maps to one outcome (icon never a dead no-op); submenu = exactly Trái/Phải/Cấu hình in order; `slotFor` CAST_LEFT→LEFT, CAST_RIGHT→RIGHT, OPEN_CONFIG→null.
- **`BubbleAccessibilityTest`** (:app, rewritten): idle desc == `"ClusterNav cast"`; no description carries a zone word (Trái/Phải/Full/…); casting desc hints "trả về"; split reads as casting; transient reads busy; icon size in 48–56 and ≥ `TOUCH_MIN_DP`.
- **`CastUILifecycleSafetyTest`** (:app, updated): single icon app-icon-sized & ≥48dp (replaces 38dp zone + disabled-zone no-op); tap-token / listener-cleanup / geometry-parse / teardown-guard tests kept.
- **`BubbleGestureContractTest`** (:app, 12): tap uses planner + `Stop()` (RETURN) + `detectForeground ?: return` + `CastFull` (CAST_FULL); Wave-1 guard preserved (`homePackages` ∪ `isLauncher` + toast, CATEGORY_HOME); submenu routes via `slotFor`; `onCastSlot` dispatches `CastSlot(foreground, side)` with guard; `openConfig` builds `Intent(MainActivity)+FLAG_ACTIVITY_NEW_TASK`+`startActivity`; service wires onTap/onLongPress, shows submenu, token-gates cast choices, dismisses submenu on destroy, builds `buildBubble()` (no `buildBubbleLayout`/`onZoneTap`); overlay rows from planner at ≥48dp, dismiss on choice/outside; renderer builds one `ImageView` with `background = null` + `ic_bubble_nav` + long-clickable.

## Verify (off-car)
Command:
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :core:test :app:testDebugUnitTest :car-integration:test :app:assembleDebug --console=plain
```
Result: **BUILD SUCCESSFUL in 1m 55s**

Test totals (0 failures, 0 errors):
- `:core:test` — **707** (was 698; +9 from `BubbleGesturePlannerTest`)
- `:app:testDebugUnitTest` — **316** (was 325; +12 contract, rewritten accessibility/lifecycle nets −21 old 3-zone/fill/disabled-zone cases)
- `:car-integration:test` — **28**
- Total **1051**, 0 failures.

`LayeringRulesTest` green: `BubbleGesturePlanner` is pure `:core` (no Android import); `pureFilesStillInApp == 2` unchanged (all new app files touch Android).

## Constraints honored
- No new dependency. `:core` stays Android-free. No file >500 LOC (service ~430, gesture 174, dispatcher 130, overlay 129, renderer 103, planner 78).
- Wave-1 `detectForeground` launcher-exclusion guard preserved verbatim.
- CP/AA full-only unchanged (`CastSlot` still validated by `CastSlotValidator`); speed-sign/HUD injection files untouched; `CastFacade`/`BubbleZone` (unwired v2) not touched — service confirmed to not reference `CastFacade`; core `CastBubbleProjectionTest` stays green.
- No commit/push/vehicle-adb.

## On-car (left for §Verification — NOT verified here)
- The bubble shows as **one nav-arrow icon with no frame**; tap casts full / returns; long-press opens the Trái/Phải/Cấu hình menu (Cấu hình opens the app).
- Drag feel and the tap-vs-long-press-vs-drag disambiguation timing on the real head unit.
- Arrow **colour/contrast/visibility** over the live cluster (brand-blue may want tuning) and the submenu **card position** (currently screen-centred).
