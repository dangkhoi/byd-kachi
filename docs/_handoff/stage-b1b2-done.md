# Stage B1+B2 — done (2026-08-19)

> Session `b1b2`. Branch `feat/speed-limit-badge-hal-hud`. Owner request 2026-08-19.
> Scope: B1 auto-start VietMap when the speed badge is enabled · B2 refine the "upcoming speed-limit" badge design.
> NOT committed/pushed (per task). `main`/keystore/`docs/README.md` untouched.

## Files changed (this task only)

| File | What |
|------|------|
| `app/src/main/java/com/byd/clusternav/MainActivity.kt` | **B1** — auto-start VietMap on open |
| `app/src/main/java/com/byd/clusternav/speedbadge/SpeedBadgeView.kt` | **B2** — `muted` (gray) style flag |
| `app/src/main/java/com/byd/clusternav/speedbadge/SpeedBadgeOverlay.kt` | **B2** — 80% size + muted + 45° lower-left placement |
| `docs/specs/upcoming-speed-limit-badge.html` | Design §4.2 updated + new §7 Nhật ký triển khai (deviation log, R2.6) |

> ⚠️ `git status` also shows other modified/new files (`ClusterBroadcaster.kt`, `TurnDistanceInterpolator*.kt`,
> `docs/diagnostics/distance-interpolation-validation-2026-08-18.md`, `docs/_handoff/stage-b3spec-done.md`,
> `stage-b4-done.md`, `docs/specs/waze-vietmap-screen-capture.html`). Those belong to **concurrent sibling
> sessions** sharing this worktree — NOT part of B1/B2. Do not attribute them here.

## B1 — auto-start VietMap (how it's gated)

`MainActivity.kt`:
- **Call**: line ~296 — `maybeAutoStartVietMap()` at the END of `onCreate` (after UI up + `maybeShowDisclaimer()`; fresh-creation only, not every resume).
- **Helper** `maybeAutoStartVietMap()` (line ~677):
  1. **Gate**: `if (!Prefs.badgeEnabled(this)) return` — the speed-badge DISPLAY toggle (default ON) is the trigger. Badge OFF → no auto-start.
  2. `packageManager.getLaunchIntentForPackage(VIETMAP_PACKAGE)` — null (not installed / not visible) → **no-op** (degrade-safe, wrapped in runCatching).
  3. `if (isAppForeground(VIETMAP_PACKAGE)) return` — best-effort skip if already foreground (don't yank it).
  4. `startActivity(launch.addFlags(FLAG_ACTIVITY_NEW_TASK))` inside `runCatching { }.onFailure { Log.w }` — any launch failure is a silent no-op, never crashes Home.
- **Foreground check** `isAppForeground(pkg)` (line ~691): `ActivityManager.runningAppProcesses` + `IMPORTANCE_FOREGROUND`, no special permission; degrades to `false` (→ launch) on Android 10+ where it returns only own processes. Fully `runCatching`.
- **Constant** `VIETMAP_PACKAGE = "vn.vietmap.live"` (private companion, line ~703). Manifest already declares `QUERY_ALL_PACKAGES`, so `getLaunchIntentForPackage` resolves VietMap on API 30+.
- Owner intent satisfied: **badge bật → VietMap tự chạy để widget có nguồn**, degrade-safe when VietMap absent.

## B2 — upcoming-badge refinement (style / size / position)

**Current-limit badge UNCHANGED** (`muted = false` → red ring + black number, same position/size).

`SpeedBadgeView.kt`:
- New `var muted: Boolean = false` (line 32). When true → **gray ring + gray number** (`MUTED_GRAY = 0xFF888888.toInt()`, line 73); ring/number color chosen per `onDraw` (lines 58, 63). Default false keeps the regulatory red/black.

`SpeedBadgeOverlay.kt`:
- **(a) Style (gray)**: upcoming `SpeedBadgeView` built with `.apply { muted = true }` (line 343) → gray ring + gray number.
- **(b) Size (80%)**: `UPCOMING_SCALE 0.7f → 0.8f` (line 48).
- **(c) Position (45° lower-left)**: `buildUpcomingLayoutParams` now offsets the upcoming badge CENTRE from the main badge's clamped centre equally left+down: `diag = UPCOMING_DIAG_FRAC(0.70f) × mainSizePx` (line 49/404); `upcomingCx = mcx − diag`, `upcomingCy = mcy + diag` (405–406); window `x = upcomingCx − containerW/2`, `y = upcomingCy − badgeSizePx/2` (420–421). Replaces the old "straight-below" (`y = top + mainSize + gap`). Removed now-unused `UPCOMING_GAP_FRAC`.
- Additive own window, degrade-safe (runCatching), never touches the current-limit badge window.

## Gate

`export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
`./gradlew :app:testDebugUnitTest --console=plain` → **BUILD SUCCESSFUL** — **396 tests, 0 failures, 0 errors, 0 skipped**.
- `SpeedBadgeLifecycleContractTest` (10 tests) PASS — source-reading contract; preserved every lifecycle substring it asserts (`if (clusterWm == null) initOverlay()`, `BadgeLayout.clampCenter(`, teardown, gate).
- `ClusterSpeedBadgePortTest` (4 tests) PASS.
- **No test asserted the old ~70%/straight-below size/placement** (`UpcomingBadgeDecisionTest` is pure show/hide decision logic), so no test needed updating.

### Environment note (important for whoever re-runs)
The gate initially failed with `java.io.EOFException` / `NoSuchFileException: .../binary/in-progress-results-generic.bin`. Root cause: **a concurrent sibling build was running `:app:testDebugUnitTest` in this SAME worktree**, colliding on `app/build/test-results/testDebugUnitTest/binary/`. My own test worker "finished executing tests" cleanly each time — it was only Gradle's shared results-serialization that collided. **Fix: run when the worktree is quiet** (no other `testDebugUnitTest`/`GradleWorkerMain` java procs) — then GREEN. Not a code issue.

## Not done / out of scope
- No commit/push (per task). No `docs/README.md` index edit (updated separately).
- On-car visual verification (gray/80%/45° actually rendered on cluster) is a V-visual step, not off-car.
