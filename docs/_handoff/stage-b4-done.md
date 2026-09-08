# Stage B4 — done

> screenRead INVALID khi stale / no-road (diagnostics hygiene) · branch `feat/speed-limit-badge-hal-hud` · 2026-08-19

## TASK
Mark the `nav_log` screen-read ground-truth INVALID when it is stale or has no road, and guard
`TurnDistanceInterpolator.refine` against snapping onto a stale/garbage anchor. Root cause (from
`docs/diagnostics/distance-interpolation-validation-2026-08-18.md`): when GMaps runs in the background the
a11y scan can't read it → `screenRead` froze the last value (`screenRead_age_ms` up to **224049ms**, road
empty) → misleading log + risk of refining off a garbage anchor. Interpolation itself is correct (tracks the
notification); only the screen-read ground-truth is junk when stale.

## Threshold
`SCREEN_READ_STALE_MS = 2500L` (ms) — a screen-read older than this is STALE (matches the doc's "~2-3s").
`INVALID = -1` — sentinel logged for a stale / no-road read (same -1 the log already means by "not read").

## Guard logic
- **Log classifier (pure, `:core`)** — `TurnDistanceInterpolator.freshScreenRead(meters, ageMs, road)`:
  returns `meters` **only** when `meters >= 0 && ageMs in 0..2500 && road.isNotBlank()`, else `INVALID (-1)`.
  Fresh+valid → returns the real value unchanged (log behaviour identical when fresh).
- **refine guard (`:core`)** — `refine(seg, nowMs, readAgeMs = 0L)`: rejects hard (`return` — no ground-truth
  write, no baseline snap) when `seg < 0` **or** `readAgeMs > 2500`. Default `readAgeMs = 0` (a just-scanned
  read is fresh) → the existing sole caller keeps IDENTICAL behaviour. Still `@Synchronized`, still pure/off
  Android; degrade-safe.
- **Wiring (`:app`, `ClusterBroadcaster.sendFrame`)** — computes the raw screen-read + age from
  `lastRefined()/lastRefinedAt()`, then logs `srM = freshScreenRead(srRaw, srAgeRaw, NavAccessibilitySource.road)`
  and `srAge = if (srM >= 0) srAgeRaw else -1L`. Runs inside the existing `runCatching` off the nav-emit hot
  path exactly as before; CSV header unchanged (`screenRead_m,screenRead_age_ms` kept).

## Files + lines
- `core/src/main/kotlin/com/byd/clusternav/navigation/TurnDistanceInterpolator.kt`
  - L41–42: `const val SCREEN_READ_STALE_MS = 2500L`, `const val INVALID = -1`
  - L59–60: pure `fun freshScreenRead(meters, ageMs, road): Int`
  - L119–124: `fun refine(seg, nowMs, readAgeMs = 0L)` — stale/invalid reject at L120
- `app/src/main/java/com/byd/clusternav/ClusterBroadcaster.kt`
  - L7: `import ...modules.navaccess.NavAccessibilitySource`
  - L157–165: nav_log record now uses `freshScreenRead(...)` → logs `-1` when stale/no-road
- `core/src/test/kotlin/com/byd/clusternav/navigation/TurnDistanceInterpolatorTest.kt`
  - +8 B4 unit tests (freshScreenRead fresh/stale/no-road/negative/boundary; refine fresh-snap,
    stale-reject, boundary) — file now 136 LOC, 17 tests total
- `docs/diagnostics/distance-interpolation-validation-2026-08-18.md` — added a "Fix applied (2026-08-19, B4)" line

## GATE — GREEN
`export JAVA_HOME=/opt/homebrew/opt/openjdk@17/... && ./gradlew :core:test :app:testDebugUnitTest --console=plain`
→ **BUILD SUCCESSFUL**.
- `:core:test` — **560 tests, 0 failures, 0 errors, 0 skipped** (incl. `TurnDistanceInterpolatorTest` = 17, all pass)
- `:app:testDebugUnitTest` — **396 tests, 0 failures, 0 errors, 0 skipped**

Note: the shared Gradle daemon was repeatedly killed by a parallel worktree's `--stop`, which truncated
`.gradle/9.6.1/executionHistory/executionHistory.bin` (EOFException in `getPreviousFailedTestClasses`). Fixed
by deleting that regenerable cache and running `--no-daemon`; no code/test relation.

## Scope check
- [x] Staleness threshold defined (2500ms) — `SCREEN_READ_STALE_MS`
- [x] Log marks screenRead `-1 (INVALID)` when age > threshold OR scanned road empty
- [x] `refine` rejects stale/invalid anchor (age > threshold or seg<0); no ground-truth write, no snap
- [x] Fresh+valid behaviour identical; degrade-safe, off nav-thread as before
- [x] Unit test added for the pure stale-guard (freshScreenRead + refine)
- [x] Diagnostics doc updated with "Fix applied" line
- [x] Gate green; no commit/push (per constraints)
