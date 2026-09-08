# Stage: Badge foundation + BUG-1 unify — DONE

> Branch `feat/speed-limit-badge-hal-hud`. Spec: `docs/specs/speed-badge-placement-vietmap-logging.html` §4.3 (Part B).
> This stage replaces the 4-corner badge model with an **absolute cluster-pixel centre**, and unifies the two
> speed-badge overlays into ONE window (BUG-1). It is the foundation for the UI stage (BadgePlacementView).

## Files changed
- `core/src/main/kotlin/com/byd/clusternav/speedbadge/BadgeLayout.kt` — dropped the corner/gravity infra;
  added pure absolute-centre math (`clampCenter`, `topLeftFromCenter`); kept `clampSizeDp` + size constants.
- `core/src/test/kotlin/com/byd/clusternav/speedbadge/BadgeLayoutTest.kt` — rewritten for the new math
  (size clamp, clamp at all 4 edges, centre-within, oversize-badge, top-left conversion, E2E round-trip). 9 tests.
- `app/src/main/java/com/byd/clusternav/Prefs.kt` — replaced `badgeCorner/badgeDx/badgeDy` with
  `badgeCenterX/Y`; kept `badgeSizeDp`; added a one-time legacy migration.
- `app/src/main/java/com/byd/clusternav/speedbadge/SpeedBadgeOverlay.kt` — `buildLayoutParams()` now uses
  `gravity=TOP|LEFT` + absolute centre; captures display-1 real size (fallback 1920×720). `refreshLayout()` unchanged.
- `app/src/main/java/com/byd/clusternav/speedbadge/ClusterSpeedBadgePort.kt` — ctor now takes the shared
  `SpeedBadgeOverlay` (no longer creates its own).
- `app/src/main/java/com/byd/clusternav/NavigationSpeedSignOwner.kt` — creates the ONE shared overlay and
  injects it; debug force/hide/refresh operate on that same instance (BUG-1). `debugBadgeOverlay` removed.
- `app/src/main/java/com/byd/clusternav/modules/clustercast/DiagActivity.kt` — its badge tuner ported to the
  centre model (corner buttons → centre presets, X/Y nudge → centre px nudge). *(Consequential compile fix; the
  full drag-to-place UI is the UI stage's BadgePlacementView.)*
- `app/src/test/java/com/byd/clusternav/SpeedSignSourceLifecycleTest.kt` — updated source-text assertion for
  the new port ctor; added a BUG-1 guard (exactly one `SpeedBadgeOverlay(`, no `debugBadgeOverlay`).

## Prefs API (for the UI stage)
```kotlin
// Absolute badge CENTRE in cluster pixels (display 1). Read triggers a one-time legacy migration.
Prefs.badgeCenterX(ctx: Context): Int          // default 1780
Prefs.setBadgeCenterX(ctx: Context, v: Int)    // no clamp on write — clamp with BadgeLayout.clampCenter before saving
Prefs.badgeCenterY(ctx: Context): Int          // default 80
Prefs.setBadgeCenterY(ctx: Context, v: Int)
Prefs.badgeSizeDp(ctx: Context): Int           // clamped 60..240 on read AND write
Prefs.setBadgeSizeDp(ctx: Context, v: Int)

// Public constants:
Prefs.BADGE_DEFAULT_CENTER_X = 1780            // (1920-140) top-right-ish on the default 1920×720 cluster
Prefs.BADGE_DEFAULT_CENTER_Y = 80
Prefs.BADGE_MIGRATE_CLUSTER_W = 1920           // used only by the one-time migration
Prefs.BADGE_MIGRATE_CLUSTER_H = 720
```
- **New keys:** `badge_center_x`, `badge_center_y` (kept `badge_size_dp`).
- **Default centre:** `(1780, 80)` — CENTRE of the badge, top-right-ish, visible on a 1920×720 cluster. The
  overlay always re-clamps against the real display size + density, so the default is on-screen at any density.
- **Migration (one-time):** if the legacy `badge_corner` key is present AND `badge_center_x/y` are absent, the
  centre is computed from old `corner + dx/dy + size` on the 1920×720 cluster (legacy ids 0=TL,1=TR,2=BL,3=BR),
  clamped on-screen, and written once. Approximate (treats stored dp≈px) — keeps existing users near their old
  corner. Never clobbers a user-set absolute position; fresh installs just use the defaults.

## Pure core API — `BadgeLayout` (`:core`, no Android)
```kotlin
fun clampSizeDp(sizeDp: Int): Int                                             // coerceIn(60, 240)
fun clampCenter(cx: Int, cy: Int, sizePx: Int, clusterW: Int, clusterH: Int): Pair<Int, Int>
//   keeps the sizePx×sizePx square (centred at cx,cy) fully inside [0,W]×[0,H], each axis independent;
//   if the badge is larger than the cluster on an axis, it centres on that axis (defensive, no throw).
fun topLeftFromCenter(cx: Int, cy: Int, sizePx: Int): Pair<Int, Int>          // (cx-sizePx/2, cy-sizePx/2)
```
Overlay math is `topLeftFromCenter(clampCenter(centreX, centreY, sizePx, clusterW, clusterH), sizePx)`.
`SIZE_MIN_DP=60`, `SIZE_MAX_DP=240`, `SIZE_DEFAULT_DP=120`. (Corner/gravity helpers were removed.)

## Overlay API (`SpeedBadgeOverlay`)
- `buildLayoutParams()` → `gravity=TOP|LEFT`, `width=height=badgeSizeDp*density`, `x,y = top-left of the
  clamped centre`. Cluster W/H come from display 1 `getRealSize` (fallback 1920×720).
- `show(speedKph, signType?)`, `hide()`, `refreshLayout()`, `close()` — all post to the **main handler** and are
  **degrade-safe** (no-op off-car when display 1 is absent; `runCatching` around WindowManager calls).
- `refreshLayout()` applies the current Prefs LIVE via `updateViewLayout` when attached (unchanged).

## BUG-1 — ONE overlay (unified)
- `NavigationSpeedSignOwner` owns a single `private val badgeOverlay = SpeedBadgeOverlay(appContext)` (declared
  before `coordinator` for init order) and passes it into `ClusterSpeedBadgePort(badgeOverlay)`.
- `debugForceBadge / debugHideBadge / debugRefreshBadgeLayout` all operate on that SAME `badgeOverlay`, so the
  real speed-sign pipeline and the debug force-show are literally one badge window.
- **Grep proof — exactly one construction:**
  `app/src/main/java/com/byd/clusternav/NavigationSpeedSignOwner.kt:29: private val badgeOverlay = SpeedBadgeOverlay(appContext)`
  (the only other `SpeedBadgeOverlay(` hit is the class declaration at `SpeedBadgeOverlay.kt:18`).

### For the UI stage (BadgePlacementView)
- Persist drags via `Prefs.setBadgeCenterX/Y` (clamp with `BadgeLayout.clampCenter` first) and size via
  `Prefs.setBadgeSizeDp`; then call `NavigationSpeedSignOwner.get(ctx).debugRefreshBadgeLayout()` to apply LIVE
  and/or `debugForceBadge(50)` to preview on the cluster.
- Get cluster W/H for the placement rect from display 1 (`DisplayParse.realSize(..., fallback 1920×720)`), and
  render the WYSIWYG marker with `SpeedBadgeView` scaled to the rect (spec §4.3).

## GATE result — `:core:test` + `:app:testDebugUnitTest`
- `:core:test` → **result=SUCCESS, total=512, failed=0**
- `:app:testDebugUnitTest` → **result=SUCCESS, total=377, failed=0**
- Targeted: `*BadgeLayoutTest` 9/9 pass; `*SpeedSignSourceLifecycleTest` 7/7 pass (incl. the new BUG-1 guard).

> ⚠️ **Environment note (Gradle 9.6.1 infra bug — NOT a test failure):** on this machine the `:core:test` /
> `:app:testDebugUnitTest` tasks intermittently exit non-zero at *report finalization* with
> `java.io.EOFException` / `NoSuchFileException: .../binary/in-progress-results-generic.bin` inside
> `SerializableTestResultStore$Writer.close`. **Proven environmental:** a pristine baseline (all changes
> stashed, clean tree) fails `:core:test` with the identical error, and no test XML ever has `failures>0`.
> To read the true pass/fail counts, run with an `afterSuite` reporter, e.g.:
> ```
> ./gradlew :core:test :app:testDebugUnitTest --console=plain --init-script /tmp/testsummary.gradle --rerun-tasks --continue
> ```
> where the init script prints `TESTSUMMARY task=... result=... total=... failed=...` from each root suite
> (fires before the buggy store close). Both suites report `result=SUCCESS failed=0`.

## Constraints honored
- Overlay ops on main handler + degrade-safe (unchanged). Did NOT touch
  `keystore.properties` / `app/release.keystore` / `local.properties`. No git commit/push, no APK build, no
  `main` branch changes. All edited files remain < 500 LOC.
