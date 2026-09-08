# AOSP 10 WindowManager — Safe shell-only relocation of a task off a Virtual Display

> Research target: BYD DiLink head unit, AOSP 10 / API 29, adb `uid=2000 (shell)`, **no root**.
> Goal: move/remove an app's task OFF the cluster virtual display (VD, displayId 1,
> owner `com.xdja.containerservice`, `FLAG_OWN_CONTENT_ONLY`) back to display 0 fullscreen,
> **without** triggering `createTaskSnapshot` (NPE B) or destroying the VD (NPE A).
> Source read: `aosp-mirror/platform_frameworks_base` tag **android-10.0.0_r47**
> (`services/core/java/com/android/server/wm/`).

## TL;DR — both NPEs share ONE root cause

Both crashes originate from **`AppWindowToken.initializeChangeTransition()`**:

- **NPE B** = the *immediate* crash: `initializeChangeTransition` calls
  `mWmService.mTaskSnapshotController.createTaskSnapshot(task, 1)` while the task is mid-reparent
  and its `DisplayContent` is transiently `null` → `getRotation()/getDisplayInfo()` NPE.
- **NPE A** = the *deferred* freeze: `initializeChangeTransition` did
  `mDisplayContent.mChangingApps.add(this)`. Later `AppTransitionController.handleChangingApps()`
  iterates `mChangingApps` → `wtoken.applyAnimationLocked(...)` → `AppWindowToken.loadAnimation()`
  which does `getTask().getDisplayContent().getDisplayInfo()`. If the VD was destroyed meanwhile,
  `getDisplayContent()` is `null` → NPE, and because it re-runs on every transition-ready pass it
  loops → hard freeze.

**Therefore: if you never let `initializeChangeTransition` run for the VD app, you avoid BOTH.**
It runs only via one gate — see Q1.

---

## Q1 — When does AOSP 10 call `initializeChangeTransition` → `createTaskSnapshot`?

**Answer: ONLY for a windowing-mode CHANGE (into/out of freeform) of an already-visible task.
NOT for task-to-front, NOT for new-task launches.**

Evidence — `AppWindowToken.onConfigurationChanged()` is the sole caller, gated by
`shouldStartChangeTransition()`:

```java
// AppWindowToken.onConfigurationChanged(...)
} else if (shouldStartChangeTransition(prevWinMode, winMode)) {
    initializeChangeTransition(mTmpPrevBounds);
}

private boolean shouldStartChangeTransition(int prevWinMode, int newWinMode) {
    if (mWmService.mDisableTransitionAnimation
            || !isVisible()                                        // (a)
            || getDisplayContent().mAppTransition.isTransitionSet() // (b)
            || getSurfaceControl() == null) {                       // (c)
        return false;
    }
    // Only do an animation into and out-of freeform mode for now.
    return (prevWinMode == WINDOWING_MODE_FREEFORM)
            != (newWinMode == WINDOWING_MODE_FREEFORM);             // (d)
}
```

So `createTaskSnapshot` fires **iff**: transition-anim not disabled at build time, app **visible**,
**no** app-transition already set on its (new) display, surface exists, **and** the windowing mode
is crossing the freeform boundary. New launches / task-to-front use the OPEN/TO_FRONT path
(`handleOpeningApps` → `loadAnimation`), which does **not** touch `initializeChangeTransition`.
Note `initializeChangeTransition` itself skips the snapshot **only** if a remote-animation adapter
is present and `!adapter.getChangeNeedsSnapshot()` — irrelevant to shell.

**Consequence for ClusterNav:** the VD app is cast in **freeform** (`WINDOWING_MODE_FREEFORM`).
Any in-place reparent to display-0 fullscreen crosses the freeform boundary (d), so the ONLY
levers to suppress the change transition from shell are: make the app **not visible** (a), or make
sure **a transition is already set** on the destination display (b) before the reparent's config
change fires.

---

## Q2 — Does `am start --display 0 --windowingMode 1 -n pkg/cls` avoid the snapshot path?

**Answer: It routes through a DIFFERENT (TASK_TO_FRONT / OPEN) transition and *prepares* an app
transition, which *can* trip guard (b) and skip the change path — but ordering is NOT guaranteed,
so treat it as RISKY, not proven-safe.**

Evidence — the launch/to-front flow prepares a transition on the display's `DisplayContent`:

```java
// ActivityStack.moveTaskToFrontLocked(...)
updateTransitLocked(TRANSIT_TASK_TO_FRONT, options);
// ActivityStack.updateTransitLocked(...)
getDisplay().mDisplayContent.prepareAppTransition(transit, false);
// ActivityStack.resumeTopActivity... -> new task:
dc.prepareAppTransition(TRANSIT_TASK_OPEN, false);
```

`prepareAppTransition(...)` sets `mAppTransition.isTransitionSet()` = true. If that happens on the
**destination** display *before* the reparent's `onConfigurationChanged` runs, guard (b) short-
circuits `shouldStartChangeTransition` → **no `initializeChangeTransition`** → no snapshot, no
`mChangingApps` entry. The risk: for an *existing* singleTask/singleTop task, `ActivityStarter`
reparents the task to the target display (via `TaskRecord.reparent` → `onParentChanged` →
`onConfigurationChanged`, the freeform→fullscreen change) and the exact interleave of that reparent
vs. `prepareAppTransition` is path-dependent. If the config change lands while the task is still
visible and the destination transition is not yet set, it hits the **same** change path as
move-stack. **Verdict: safer than `move-stack` (it at least sets a transition), but not
guaranteed.** Confidence: **medium**.

---

## Q3 — Does `am force-stop <pkg>` trigger a snapshot / change transition?

**Answer: NO. Force-stop is a snapshot-free, change-transition-free window-removal path. It is SAFE
from NPE A and NPE B, provided the VD itself is not destroyed (another app remaining on it
guarantees that).**

Evidence:
- Force-stop → process death → `ActivityStack.handleAppDiedLocked` / `finishCurrentActivityLocked`
  which prepares **close** transitions only: `TRANSIT_CRASHING_ACTIVITY_CLOSE` / `TRANSIT_TASK_CLOSE`
  / `TRANSIT_ACTIVITY_CLOSE` — the `handleClosingApps` → `loadAnimation` path, **never**
  `initializeChangeTransition`. No windowing-mode change occurs (the task is destroyed, not
  reparented), so guard (d) is never evaluated. → **No NPE B.**
- The only snapshot on close is `TaskSnapshotController.notifyAppVisibilityChanged(false)` →
  `handleClosingApps` → `snapshotTask` → `createTaskSnapshot`. But (1) after process death
  `findAppTokenForSnapshot` requires `isSurfaceShowing()` + a visible child → typically returns
  `null` → snapshot skipped; and (2) even if taken, the task is still parented to the **live** VD,
  so `DisplayContent` is non-null → **no `getRotation`/`getDisplayInfo` NPE.**
- The app never enters `mChangingApps` (that only happens via `initializeChangeTransition`) → the
  `handleChangingApps` NPE A loop cannot fire for it.
- The VD is owned by `com.xdja.containerservice` (`FLAG_OWN_CONTENT_ONLY`); its lifecycle is not
  tied to whether apps are on it. Force-stopping an app does **not** destroy the VD. With another
  app remaining, NPE A (display-destroyed-with-pending-transition) is structurally impossible here.
- `am force-stop` is available to `uid=shell` (standard adb capability).

**Caveat (product, not WM):** force-stop kills the process → **loses in-app state (active nav
route).** Safe for WM, destructive for a "keep the route" flow. Confidence: **high** (WM safety).

---

## Q4 — Can `settings put global …_animation_scale 0` or `wm` make the change transition skip the snapshot?

**Answer: NO. Animation scales do NOT gate `shouldStartChangeTransition`, and the only flag that
does (`mDisableTransitionAnimation`) is a build-time resource, not shell-settable.**

Evidence:
```java
// WindowManagerService ctor:
mDisableTransitionAnimation = context.getResources().getBoolean(
        com.android.internal.R.bool.config_disableTransitionAnimation);
```
- `mDisableTransitionAnimation` comes from a **resource overlay** (`config_disableTransitionAnimation`),
  set once at construction. There is no `settings`/`wm`/`cmd` verb to change it at runtime.
- `window_animation_scale` / `transition_animation_scale` / `animator_duration_scale` feed
  `mWindowAnimationScaleSetting` etc. via `SettingsObserver`; they are consumed by
  `getTransitionAnimationScaleLocked()` (scales the animation **duration**) — they are **not**
  referenced by `shouldStartChangeTransition`.
- `initializeChangeTransition` calls `createTaskSnapshot` regardless of animation scale (it skips
  only for a remote animator declaring `!getChangeNeedsSnapshot()`).

So `settings put global transition_animation_scale 0` will **not** prevent NPE B. Confidence:
**high**. (It's still worth setting scales to 0 as defense-in-depth for other animation paths, but
it does not fix this bug.)

---

## Q5 — Any other snapshot-free primitive to relocate a task between displays as shell?

**Answer: No shell verb reparents a *visible freeform* task to fullscreen while suppressing the
change transition. The only reliably snapshot-free relocation is DESTROY + RELAUNCH
(`force-stop` then `am start --display 0`), because a fresh launch uses `TRANSIT_TASK_OPEN`
(`loadAnimation`), never `initializeChangeTransition`.**

- `am display move-stack <stackId> 0` → `RootActivityContainer.moveStackToDisplay` →
  `stack.reparent(display, onTop, false)`. `ActivityStack.reparent` does
  `removeFromDisplay()` → `mTaskStack.reparent(...)` → `addChild(...)` → `postReparent()` with
  **no `prepareAppTransition`** anywhere → guard (b) is false → change transition fires on the
  freeform→fullscreen config change → **NPE B** (this is exactly the current failing path).
- `am stack move-task` / windowing-mode changes → same reparent/change semantics → same risk.
- `monkey -p pkg --display 0` / `cmd activity start-activity --display 0` → equivalent to
  `am start` (Q2): may reuse+reparent an existing task → same change-transition risk.
- Making the app **invisible first** (occlude it on the VD with the incoming app / another
  activity) flips guard (a) to false → `shouldStartChangeTransition` returns false → a subsequent
  `move-stack`/`am start` reparents **without** a change transition. This is the only way to move
  an existing task and **preserve its process/state** safely. Confidence: **medium-high**
  (depends on reliably driving the token to `isVisible()==false` before the move).

---

## RANKED shell recommendations (return old VD app → display-0 fullscreen)

| # | Mechanism | NPE A | NPE B | State | Confidence |
|---|-----------|-------|-------|-------|-----------|
| 1 | **`am force-stop <pkg>` → then `am start -n pkg/cls --display 0 --windowingMode 1`** (destroy + relaunch fresh). No reparent, no windowing-mode change, fresh task uses `TRANSIT_TASK_OPEN`. | **SAFE** | **SAFE** | **lost** (kills process/route) | high |
| 2 | **Occlude first, then move.** Put the incoming app on the VD (or bring any other activity to front on displayId 1) so the target token becomes `isVisible()==false`, THEN `am display move-stack <stackId> 0` (or `am start --display 0`). Guard (a) suppresses the change transition. | **SAFE** | **SAFE** | **kept** | med-high |
| 3 | **`am start -n pkg/cls --display 0 --windowingMode 1`** on a *visible* task. Prepares `TRANSIT_TASK_TO_FRONT`; may trip guard (b) before the reparent config change — but ordering not guaranteed. | RISKY | RISKY | kept | medium |
| 4 | **`am display move-stack <stackId> 0`** on a *visible* freeform task (current approach). No transition prepared before reparent → change transition + snapshot fire. | **UNSAFE** | **UNSAFE** | — | high |
| 5 | **`settings put global transition_animation_scale 0`** (alone). Does not gate the change transition; snapshot still taken. Ineffective for this bug. | no effect | **UNSAFE** | — | high |

### Operational guidance
- **Best "must keep route" path:** #2 — sequence so the *new* app is placed on the VD (occluding
  and hiding the old token) **before** relocating the old task; then move the now-invisible old
  task. Verify invisibility (e.g. `dumpsys window` shows the AppWindowToken hidden / not visible)
  before issuing the move.
- **Best "state loss acceptable" path:** #1 — deterministic and simplest; safe by construction.
- **NPE A hard rule:** never let `com.xdja.containerservice` tear down / recreate the VD while any
  app on it has a pending transition. Because `mChangingApps` is populated **only** by
  `initializeChangeTransition`, avoiding change transitions (paths #1/#2) also structurally
  prevents the `handleChangingApps` freeze.
- Combine #1/#2 with `transition_animation_scale 0` as harmless defense-in-depth, but do not rely
  on it (Q4).

## Caveats / confidence notes
- Exact line "~298 getRotation" is a sub-revision detail (in r47 `createTaskSnapshot(Task,float)`
  reads `task.getSurfaceControl()`/bounds; other 10.x QPR revisions read display rotation for the
  `TaskSnapshot`). The **call path** `onConfigurationChanged → shouldStartChangeTransition →
  initializeChangeTransition → createTaskSnapshot` is confirmed and revision-stable — that is what
  the mitigation targets.
- Q2 ordering (reparent vs `prepareAppTransition`) was not traced to a single deterministic
  sequence in `ActivityStarter`; hence #3 is RISKY, not SAFE. If you must use #3, first drive the
  old app invisible (collapses to #2).
