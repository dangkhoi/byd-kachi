# HANDOFF — Cluster Cast `0.72` field-execution correction (OFF-CAR EXACT-SOURCE ONLY)

> **Checkpoint:** 2026-07-26 · Owner: Đăng Khôi · `dangkhoi`
> **Why:** the 2026-07-25 vehicle run of the 0.70/0.71 source failed on every case. The architecture was sound; the executed recipe was not. `0.72` keeps the V2 architecture and restores what V1 proved on the car.
> **Hard stop:** no APK build, install, ADB/DADB, vehicle command, commit, push or merge was performed.

---

## 1. Root causes found by the V1 ↔ V2 comparison

| # | Defect in 0.70/0.71 | On-car symptom |
|---|---|---|
| P0-1 | Bootstrap preflight demanded a topology of exactly `Display 0`; real vehicles already own the cluster VD and also expose `Display 1` launcher-split | every cast blocked: no `IDLE_VERIFIED`, so `CAST` was refused forever |
| P0-2 | Verification hardcoded `bounds == 0,0,1920,720` and `densityDpi == 180` | any other measured cluster never verified |
| P0-3 | Placement used `am start --display X --windowingMode 1 --activity-clear-task`, no `force_resizable_activities`, no reassert, no `am task resize` | white/blank cluster, lost session |
| P0-4 | `am display move-stack` was banned in every direction | `ActivityStarter` silently redirected the launch back to display 0 → app stayed on the centre screen |
| P0-5 | `FORCE_STOP_NORMAL` was the first placement step | navigation session destroyed on every cast |

Also missing versus V1: PIP block, animation quiesce, per-app cluster style and density, in-process measurement fallback, watchdog restore, visible step log.

## 2. What 0.72 executes now

```
SET_FORCE_RESIZABLE           settings put global force_resizable_activities 0
DISABLE_TRANSITION_ANIMATION  window/transition animation scale = 0   (restored on Stop)
BLOCK_PIP                     appops set <pkg> PICTURE_IN_PICTURE ignore  (restored on Stop)
PRE_OPEN_ON_MAIN              am start -a MAIN -c LAUNCHER -n <comp>      (no-op if a task exists)
PLACE_KEEP_SESSION   R1       am start … --display <vd> --windowingMode 5  (NO clear-task)
MOVE_STACK_TO_CLUSTER R2      am display move-stack <stack> <vd>          (no-op if already landed)
REASSERT_ON_CLUSTER           repeat R1 to force composite
FIT_CLUSTER_COMPOSITE         [wm density <dpi> -d <vd>;] am task resize <task> 0 0 <W> <H>
R3 (opt-in only)              force-stop + clear-task relaunch + fit
```

- R3 requires an explicit consequence prompt (`CastRetryPrompt`) and is impossible for CarPlay/Android Auto or keep-session apps.
- Bootstrap **adopts** an existing unoccupied cluster display with zero seal commands; an occupied cluster is refused with an actionable reason.
- Verification is **measured** (origin 0,0, positive size/density within bounds, `fission…xdja` name).
- `DisplayManager` provides an in-process fallback display identity when the dump wording changes.
- Cluster **style per app** (opcode 30 curved keeps km/h · 31 rect full pane) and **density per app**.
- Watchdog is restorative only: two identical observations proving the target vanished trigger the canonical Stop so gauges return — never a re-cast, never force-stop.
- `CastOperationLog` records every journaled step, the exact dispatched shell string and each refusal reason; Diagnostics renders it read-only and it can be copied on the vehicle.

## 3. Off-car evidence

| Item | Value |
|---|---|
| Version | `versionCode = 72`, `versionName = "0.72"` |
| Forced full JVM | `--rerun-tasks :app:testDebugUnitTest` → 62 suites, **554 tests, 0 failures / 0 errors / 0 skips** |
| Bounded review | 5 report-only passes on placement/bootstrap/watchdog; final `ZERO_ACTIONABLE_FINDINGS / APPROVED` |
| New/updated suites | `CastFieldParityTest`, `CastOperationLogTest`, `CastColdBootstrapPreflightTest`, `ExpectedLadder` (ladder order lock), plus updated planner/manual-intent/runtime contracts |
| Exact source | the highest-revision `docs/_handoff/cluster-cast-v072-field-execution-r*-exact-source.json`, which records its own collision-safe source ID (self-excluded to prevent recursion) over the hashed source/test/spec inputs and the tracked-diff binding. Earlier 0.72 revisions are retained as hash-inventoried superseded attestations. |
| Predecessors intact | `92e972b9…`, `43efd3c96a43…` (0.70) and `1bd4aa6835c2…` (0.71) still recompute unchanged |
| Security | pattern scan 274 paths (266 text / 8 binary) → zero matches; semantic scan of the 0.72 files → `CLEAN`, 0 BLOCK, 0 WARN |
| LOC | every changed source/test ≤500; Activity 493 (<495); only pre-existing legacy `ClusterCast.kt` and `DeadReckonService.kt` exceed and were untouched |
| Canonical | binding **D10** added to `docs/specs/cluster-cast-rebaseline.html` (Pass 13); 0.71 spec Pass 7 records the correction |

## 3b. Defects the review loop closed after the first 0.72 draft

- Tolerant rungs (`already landed`, `nothing to reparent`, `size unreadable`, `display re-occupied`) returned null, which the gateway turned into a refused mutation and a false recovery — tearing down a cast that had actually landed.
- The in-process measurement could rescue a cancelled or fenced dispatch.
- The watchdog's restorative Stop planned a return step that would have relaunched the vanished app on the centre screen.
- Stop's own restores (animation scales, PIP app-op) were gated behind cluster-display discovery, so a disappeared cluster left animation at 0 and PIP suppressed on the head unit.
- An unquoted component broke launcher activities containing `$` (nested classes).
- A normal app returning to the centre screen stayed a floating window; it is now relaunched with `--windowingMode 1` while a protected sink is still returned gently.

## 4. What to do on the next vehicle trip

1. Build and install must be authorized separately, naming the source ID recorded in the final 0.72 attestation.
2. First screen check: open Cast → if it says the cluster already holds an app, press Dừng once, then cast.
3. If an app does not land: the prompt offers "Tắt app và chiếu lại" (destructive). CarPlay/AA will refuse by design.
4. If anything looks wrong, open Chẩn đoán and copy the log — it now contains every command and refusal reason, in order.
5. Per-app tuning lives in Quản lý ứng dụng → app details: Kiểu cụm (cong/thẳng) and Cỡ chữ trên cụm (DPI).

## 5. Still NOT STARTED

exact APK/source/signature binding · behavior manifest and screenshots · APK build and install · on-car evidence for the restored ladder, App Manager, Bubble, accessibility and migration · physical vehicle tests and sign-off · commit, push, merge, release or support claim.
