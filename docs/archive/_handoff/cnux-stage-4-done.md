# Cast + Nav UX v1.04 — Stage 4 (Wave 4) done

> Spec: `docs/specs/cast-nav-ux-release-v104.html` (R6 #1 cast stuck-as-window, R7 #2 nav arrival not clearing)
> Date: 2026-08-11 · Off-car only · **No commit/push/vehicle-adb** (per `docs/_handoff/AUTONOMOUS-RESUME.md`)
> Reads: `cnux-stage-1-done.md` (launcher guard), `cnux-stage-2-done.md` (profiles/DPI), `cnux-stage-3-done.md` (single-icon bubble)
> Method: investigate-first. On-car-observed bugs → fixed off-car by **root cause + logic tests**; on-car confirmation recorded (NOT claimed PASS).

## Scope status
- **R6 (#1 — cast VietMap stuck as a WINDOW, never returns to fullscreen)** — ✅ code + test + wired (on-car freeform-mode confirmation left to §Verification)
- **R7 (#2 — nav "arrival" does not clear on the cluster)** — ✅ code + test + wired (on-car GMaps-arrival confirmation left to §Verification)
- No gaps in Wave 4 scope. Waves 1–3 changes untouched; CP/AA full-only untouched; speed-sign/HUD injection untouched; `:core` stays Android-free; no new dependency.

---

## BUG #1 (R6) — cast stuck as a window

### Root cause (found by reading the wired path)
The wired cast path is **`SimpleCastCoordinator` → `AppMover`** (NOT `CastShell`/`CastFacade` — that is the unwired v2 pipeline). A NORMAL app is cast to the cluster with `am start --display 1 --windowingMode 5` (**WINDOWING_MODE_FREEFORM**) followed by `am task resize`, so its task is left in **freeform** mode with cluster bounds.

`AppMover.returnToMain` (NORMAL branch) issued only:
```
am start --display 0 --windowingMode 1 -n '<component>'
```
On Android 10 (BYD DiLink3) this bare command does **not** reliably clear freeform on an **already-running** task: the app returns to display 0 but keeps freeform + its `[0,0,W,H]` cluster rect, rendering as a **skewed floating window**. Every cast/return cycle leaves it stuck (owner: "cast VietMap back and forth → window, even 'return to main screen' stays windowed; permanently stuck windowed").

The reliable recipe already existed in the legacy `CastShell.restoreFullscreenOnMain` (field-validated after the 2026-07-22 failure "VietMap scaled on the main screen, still scaled after restart") but was **never ported** to the simplified `AppMover`.

### Exact fix
`core/…/simplified/AppMover.kt`:
- New pure companion builder `fullscreenReturnCommand(component)`:
  ```
  am start --display 0 --windowingMode 1 -f 0x20000000 \
     -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n '<component>'
  ```
  `--windowingMode 1` is the only shell verb that changes a running task's windowing-mode on A10; `-f 0x20000000` (FLAG_ACTIVITY_SINGLE_TOP) + LAUNCHER reparent the **existing** task into a fullscreen stack instead of spawning a duplicate activity.
- `returnToMain` NORMAL now issues that recipe, then **self-heals**: if `isWindowedOnMain(pkg)` (best-effort read of `am stack list` + `wm size` → a stack on display 0 whose bounds are smaller than the full display) still shows a freeform window, it re-issues once (bounded, fail-open).
- **CP/AA return path unchanged** (`am stack move-task`).
- State machine: `SimpleCastCoordinator.handleStop` already transitions to `Idle` on full/all-stop (never lingers `CastingSplit`/freeform); Wave-4 tests lock that.

### Files changed (#1)
| File | Change |
|------|--------|
| `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/AppMover.kt` | `fullscreenReturnCommand` (companion), `returnToMain` NORMAL rewrite (proven recipe + verify/re-issue), `isWindowedOnMain` freeform probe, KDoc. |

---

## BUG #2 (R7) — nav arrival does not clear

### Root cause (found by reading the notification → cluster path)
`NavNotificationListener.handle`:
- **A (stuck frame):** on arrival keyword it *ingested* one destination frame (`maneuverIcon = 15`) and kept it until the notification was removed. But `ClusterBroadcaster` **heart-beats** the last frame every 400 ms for `STALE_MS = 180_000` (3 min). If the notification is not removed (or a later frame overwrites it), the cluster stays stuck for up to 3 minutes — exactly "Google Maps already announced arrival, but ClusterNav stayed stuck showing '3.5 km go straight'".
- **B (distance regression):** there was **no guard** — `handle` ingested whatever distance the parser produced, so a spurious `500 m → 3.5 km` jump (same maneuver, no reroute) became the heart-beaten value.

### Exact fix
New pure `:core` `navigation/NavArrivalGuard.kt` (Android-free, unit-tested off-car):
- `isArrivalText(vararg fields)` — EN+VI arrival regex (moved out of the listener → one source of truth for both call-sites).
- `arrivedByRouteRemaining(m)` — route-remaining ≤ 30 m ⇒ arrived.
- `acceptDistance(meters, maneuverKey)` — while approaching (last-good ≤ 800 m) a jump of > +1500 m on the **same** maneuver is dropped; a new maneuver/reroute (key change) is always accepted; a persistent large value is released after 2 rejects (real reroute recovers).

`app/…/NavNotificationListener.kt` wiring:
- Arrival keyword → `arrivalGuard.reset()` + `TurnDistanceInterpolator.reset()` + **`NavRepository.stop(applicationContext)`** (CLEAR → cluster returns to gauges; no lingering heart-beat frame). The old icon-15 planting is removed.
- Route-remaining ~0 (from parsed ETA) → same STOP/clear.
- Distance-regression guard runs **before** `NavRepository.ingest`; a rejected frame is dropped so it never reaches the cluster (last-good stays shown).
- `onNotificationRemoved` uses the shared `isArrivalText` and `arrivalGuard.reset()`.
- Neutral pipeline (`Maneuver` / `AmapFrameBuilder`, cluster byte-identical encoder) untouched — the guard only decides ingest-vs-stop.

### Files changed (#2)
| File | Change |
|------|--------|
| `core/src/main/kotlin/com/byd/clusternav/navigation/NavArrivalGuard.kt` | **NEW** — pure arrival detection + distance-regression guard (91 LOC). |
| `app/src/main/java/com/byd/clusternav/NavNotificationListener.kt` | Arrival→STOP, route-remaining→STOP, distance guard before ingest, shared `isArrivalText`, `arrivalGuard` field + resets; removed the private ARRIVAL regex + icon-15 planting. |

---

## New tests
| Test | Module | Cases | Asserts |
|------|--------|-------|---------|
| `AppMoverReturnFullscreenTest` | `:core` | 5 | `fullscreenReturnCommand` shape (windowingMode 1 + SINGLE_TOP + LAUNCHER + --display 0); NORMAL return issues it once when nothing stuck; re-issues once when a freeform window lingers on display 0; no re-issue when already fullscreen; CP/AA still uses `move-task` (no fullscreen recipe). |
| `CastReturnFullscreenTest` | `:core` | 2 | Stop from full → state `Idle` (not freeform/split) + display-0 fullscreen reset via proven recipe; Stop-all from split → `Idle` + both apps returned fullscreen (no lingering split/freeform). |
| `NavArrivalGuardTest` | `:core` | 9 | arrival text EN/VI across fields + negatives; route-remaining threshold; drop spurious jump-up (same maneuver); release persistent value after hysteresis; new maneuver jump allowed; no guard past approach window; negative distance always accepted; reset. |
| `NavArrivalClearContractTest` | `:app` | 5 | listener wires `NavArrivalGuard.isArrivalText`; arrival emits `NavRepository.stop` (and NO `maneuverIcon = 15`); route-remaining clear; distance guard precedes ingest; guard reset on removal. |

`:app` uses no Robolectric (the listener is an Android `NotificationListenerService`), so its wiring is locked by the source-contract test — same pattern as the repo's `NavNotificationListenerTest` / `BubbleGestureContractTest`. All decision logic is unit-tested in `:core`.

---

## Verify (off-car)
Command:
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :core:test :app:testDebugUnitTest :car-integration:test :app:assembleDebug --console=plain
```
Result: **BUILD SUCCESSFUL in 1m 56s**

Test totals (0 failures, 0 errors):
- `:core:test` — **723** (was 707; +16 = AppMoverReturnFullscreen 5 + CastReturnFullscreen 2 + NavArrivalGuard 9)
- `:app:testDebugUnitTest` — **321** (was 316; +5 = NavArrivalClearContract)
- `:car-integration:test` — **28**
- Total **1072**, 0 failures/0 errors.

`LayeringRulesTest` green (`NavArrivalGuard` is pure `:core`, no Android import). Pre-existing warning `AppMover.kt:51 'when' is exhaustive` is in the untouched `castToCluster` CP/AA branch (noted in Stage 1 handoff) — not introduced here, not an error.

## Constraints honored
- No new dependency. `:core` stays Android-free. Files ≤500 LOC (AppMover ~360, NavArrivalGuard 91, listener ~330).
- Waves 1–3 changes intact; CP/AA full-only untouched; speed-sign/HUD injection files untouched; neutral Maneuver/encoder pipeline unchanged.
- No commit/push/vehicle-adb.

## On-car verification items (recorded — NOT claimed PASS)
### #1 / R6
- Cast VietMap full → "return to main" → VietMap fullscreen on the main screen, no window/skew. Repeat cast↔return ≥5× → never stuck windowed. `dumpsys window displays`: VietMap task on display 0 has windowing-mode **fullscreen** (mode 1), not freeform.
- Split → return-all → both apps fullscreen, no floating window left.
- Self-heal: if the first return still shows a window → log `[AppMover] returnToMain: … re-issuing fullscreen reset` and the app becomes fullscreen after re-issue.
- **App-side limit:** deep windowing-mode verification / forced change-mode at the framework/MCU layer (via `dumpsys window displays`, as legacy `CastShell.restoreFullscreenOnMain` does) is outside the simplified `AppMover`'s reach; `isWindowedOnMain` is a best-effort `am stack list` heuristic (fail-open). If on-car still shows stuck cases after re-issue, port the `dumpsys`-verify path from the (unwired) v2 `CastShell`.

### #2 / R7
- Run one GMaps route to the destination → on "arrived"/notification removal → cluster CLEARS to gauges (no stuck icon+distance). Same for Waze / VietMap.
- At ~500 m to destination, a spurious 3.5 km notification (same road/maneuver) → cluster HOLDS ~500 m, does not jump to 3.5 km (log `bỏ frame cự ly nhảy vô lý`). A real reroute (road change) → cluster updates to the new distance normally.
- When route-remaining shows ~0 → cluster clears even without an explicit "arrived" text.

## Next
Stage 5 — senior review (opus): scope + boundary shape + tech-freshness across all four waves; no commit until owner authorizes.
