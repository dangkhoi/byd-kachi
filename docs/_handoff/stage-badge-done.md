# Stage: Speed-badge overlay LIFECYCLE fix + badge on/off toggle — DONE

> Branch `feat/speed-limit-badge-hal-hud`. Owner design: `_oncar-notes/2026-08-18-drive.md` §"HƯỚNG FIX".
> Bug (proven on-car, with log): the pipeline fired (`ClusterSpeedBadge: show 60 km/h`) but no overlay
> attached to display 1 — the old `SpeedBadgeOverlay` set `degraded=true` ONCE at init (app started before
> the cluster/cast display 1 existed) and never recovered. Root cause = TIMING, not data.

## Fix summary (event-driven, retry, teardown, no permanent degrade)
The overlay no longer degrades permanently. Init is idempotent + retried, a `DisplayManager.DisplayListener`
(re)attaches when display 1 appears and tears down when it disappears, and the badge is gated by a new
`Prefs.badgeEnabled` toggle (default ON). All WindowManager ops stay on the main handler and are degrade-safe;
off-car (no display 1) is a cheap no-op. Absolute-centre positioning (`BadgeLayout.clampCenter`) is unchanged.

## Files changed (6) + created (1)
- `app/src/main/java/com/byd/clusternav/speedbadge/SpeedBadgeOverlay.kt` (rewritten, 232 LOC) — lifecycle fix + gate.
- `app/src/main/java/com/byd/clusternav/Prefs.kt` (+`badgeEnabled`, default true).
- `app/src/main/java/com/byd/clusternav/NavigationSpeedSignOwner.kt` (+`onBadgeEnabledChanged()`).
- `app/src/main/java/com/byd/clusternav/modules/clustercast/BadgePlacementController.kt` (+Switch wiring).
- `app/src/main/res/layout/activity_main.xml` (narrow) — `switch_badge_enabled` added.
- `app/src/main/res/layout-w960dp/activity_main.xml` (wide) — `switch_badge_enabled` added.
- `offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionTransportFenceTest.kt` — **re-pinned** narrow-layout `T11_HASHES`.
- **NEW** `app/src/test/java/com/byd/clusternav/speedbadge/SpeedBadgeLifecycleContractTest.kt` (134 LOC, 10 tests).

## Retry / DisplayListener design (file:line — `SpeedBadgeOverlay.kt`)
- **Idempotent init** — `initOverlay()` @85 bails immediately if already initialized: `if (clusterWm != null) return` @86. If display 1 is absent it logs debug + `return` (stays UN-initialized, no permanent flag).
- **Retry on show** — `doShow()` @144 re-runs init when uninitialized: `if (clusterWm == null) initOverlay()` @153; `badgeView ?: return` keeps off-car a cheap no-op.
- **DisplayListener** — declared @52 (`DisplayManager.DisplayListener`), registered on the main handler @75 (`registerDisplayListener(displayListener, handler)`), unregistered on `close()` @138.
  - `onDisplayAdded` @53 (gated `id==1`) → `initOverlay()` + re-show pending: `lastSpeedKph?.let { doShow(it, lastSignType) }`.
  - `onDisplayRemoved` @62 (gated `id==1`) → `teardown()`.
- **Teardown** — `teardown()` @222 detaches (`removeView`), `attached=false`, and DROPS `clusterWm=null` + `badgeView=null` so the next `initOverlay()` rebuilds cleanly against the display that returns.
- **No permanent degrade** — the `degraded` field is gone entirely (a failed `addView` is caught and simply retried on the next show); verified by `SpeedBadgeLifecycleContractTest` (`assertFalse(overlay.contains("degraded"))`).
- **Main handler + degrade-safe** — single `Handler(Looper.getMainLooper())`; init body + register/unregister + add/remove/updateView all wrapped in `runCatching`.

## Badge toggle wiring (default ON)
- **Prefs** — `Prefs.kt`: `K_BADGE_ENABLED` @188; `badgeEnabled()` default `true` @194; `setBadgeEnabled()` @195.
- **Gate (covers BOTH real pipeline and debug force-show)** — `SpeedBadgeOverlay.doShow()` @148 early-returns via `teardown()` when `!Prefs.badgeEnabled(appContext)`, so the cluster badge does NOT show and the overlay never attaches. Because `debugForceBadge` also calls `overlay.show()`, the preview button respects the toggle too. `applyEnabled()` @124 re-evaluates on toggle: detach when off, re-show `lastSpeedKph` when on.
- **Owner** — `NavigationSpeedSignOwner.onBadgeEnabledChanged()` @104 → `badgeOverlay.applyEnabled()` (the ONE shared overlay — BUG-1 unify preserved; still exactly one `SpeedBadgeOverlay(` construction).
- **Controller** — `BadgePlacementController.bind()` @44: binds `R.id.switch_badge_enabled`; detaches listener @45 before restoring `isChecked = Prefs.badgeEnabled(activity)` (no spurious toggle on open); user change persists `Prefs.setBadgeEnabled(activity, checked)` @48 + calls `onBadgeEnabledChanged()` @49. Null-tolerant (a layout variant missing the id can't crash Home).
- **UI** — `<Switch android:id="@+id/switch_badge_enabled" android:checked="true" …>` in BOTH layouts: narrow @184, wide @278, placed right under the "Biển báo tốc độ trên cụm" header in the Cluster Cast card. Default checked = ON.

## Fence-hash re-pin — YES (required by the intentional narrow-layout change)
Adding the Switch changed the SHA-256 of `app/src/main/res/layout/activity_main.xml`, which
`ExpansionTransportFenceTest` pins in `T11_HASHES`. Re-pinned per the documented procedure (precedent:
`stage-ui-done.md`; 1.28 TASK4; the `strings.xml 7e5113f7→4b068200` re-pin):
- `app/src/main/res/layout/activity_main.xml`: `96f4092d…39fa` → **`4738ceb6ffaa87b24f0058a7e99c9d9e2a187d8913a59e3b50effbc34d4946ec`**
- `layout-w960dp/activity_main.xml` is NOT pinned (no change needed); `strings.xml` pin untouched.
- Note: the fence lives in `:offcar-planner`, which is OUTSIDE the task gate (`:core:test :app:testDebugUnitTest`); re-pinned anyway to keep the repo green.

## GATE result
- `./gradlew :core:test :app:testDebugUnitTest --console=plain` → **BUILD SUCCESSFUL** (2m5s).
  - `:app:testDebugUnitTest` → **tests=387 failures=0 errors=0** (377 baseline + 10 new).
  - `:core:test` → **tests=515 failures=0 errors=0**.
  - New `SpeedBadgeLifecycleContractTest` → **tests=10 failures=0 errors=0**.
  - Layout-reading contract tests still green (`NavCastUiWiringContractTest` incl. the `<501` renderer-LOC guard, `CastEnableToggleContractTest`, `SpeedSignSourceLifecycleTest` incl. the BUG-1 single-overlay assertion).
- `./gradlew :offcar-planner:test --tests …ExpansionTransportFenceTest` → **tests=10 failures=0 errors=0** after re-pin.

## Tests added
- `SpeedBadgeLifecycleContractTest` (source-reading, since the overlay needs Android and there is no Robolectric):
  no permanent degrade; idempotent + retryable init; DisplayListener add/re-show + remove/teardown; teardown
  drops WM+view; main-handler + degrade-safe; `doShow` gates on `Prefs.badgeEnabled`; Prefs default ON; owner
  `onBadgeEnabledChanged`→`applyEnabled`; controller switch wiring (listener-detach-before-restore ordering);
  both layouts carry `switch_badge_enabled` defaulting `checked="true"`.

## Constraints honored
- Did NOT touch `keystore.properties` / `release.keystore` / `local.properties` / `main`. No git commit/push, no APK build.
- Files ≤ 500 LOC: SpeedBadgeOverlay 232, Prefs 263, NavigationSpeedSignOwner 125, BadgePlacementController 144, test 134.
- Main handler + degrade-safe throughout; off-car (no display 1) stays a cheap no-op.
- Absolute-centre positioning (`BadgeLayout.clampCenter`) intact; BUG-1 single-shared-overlay preserved.

## Off-car verification note (lifecycle, per owner)
Emulator has no display 1, so the overlay stays a no-op there — the add→remove→re-add cycle can only be
observed on the car (parked): open app with Cast off → badge absent; enable Cast (display 1 appears) → badge
attaches on the next VietMap emission; disable Cast → badge detaches; re-enable → re-attaches. Toggle OFF →
badge detaches immediately; toggle ON → re-shows the last value. `screencap -d1` confirms attach on display 1.
