# Review — CP/AA projection freeze/orphan vs. DashCast recipe (v0.60→v0.67)

> Off-car review (xe tắt). Sources: `docs/reference/dashcast-projection-recipe.md` (SOURCE OF TRUTH),
> `docs/_handoff/research-aosp-wm.md`, `research-evidence-audit.md`, `oncar-v067-cp-fail-094907/castlog.txt`,
> and code `ClusterCast.kt` / `CastShell.kt` / `ClusterProfile.kt` / `StackParse.kt` / `WmParse.kt`.
> Opcode map (`ClusterProfile.svcCall` → `service call AutoContainer 2 i32 1000 i32 <n>`):
> **30**=curved/keep-km/h · **16**=CMD_PROJECT (cast + ADAS clear + may recreate VD) · **35**=DI40 ADAS-fix ·
> **18**=stop-cast · **0**=restore-native. castSeq=[30,16,35], teardownSeq=[18,0].

## 1. RECIPE-vs-CURRENT drift table

| Recipe rule | Where in code | Verdict |
|---|---|---|
| **Fresh-launch** all apps: `am force-stop` + `am start --display VD --windowingMode 5 --activity-clear-task` (reset = default; keep-state = opt-in) | Cold `placeLadder` R1 `ClusterCast.kt:766` uses `am start --display vd --windowingMode 5 … -n comp` **WITHOUT** `--activity-clear-task` (keep-state = default). Fresh-launch demoted to R3 `:827` (force-stop + clear-task), gated behind `keepSession`/`isPhoneProjection`. Warm `CastShell.swapOnVd` startCmd `CastShell.kt:356` = same **no clear-task, no force-stop**. | **DRIFTED** — default inverted (keep-state default vs recipe's fresh-launch default). Causes recipe root-diff #1 (white/GL) + #4 (stuck) risk. |
| **cmd16 on WARM** (re-issue CMD_PROJECT → clear ADAS + re-project, then place app on fresh VD) | Cold path issues 16 inside `castSeq` loop `ClusterCast.kt:464`. **Warm** `hotSwapOnVd:603` → `swapOnVd:355` issue **NO svcCall(16) anywhere**. Explicitly removed v0.61 (see const comment `ClusterCast.kt:66`: "cmd16 … đã bỏ ở v0.61: warm-switch KHÔNG tái tạo VD nữa"). | **DRIFTED (warm)** — this is recipe root-diff #3 (ADAS-đen) for every warm switch. |
| **density (scale) not overscan** | `applyBounds` `ClusterCast.kt:848` → `setDensityIfNeeded` (`wm density`) is tier-1 scale; `wm overscan` only cosmetic fallback; `AppScale.dpi` per-app. | **FOLLOWS**. |
| **Teardown order**: app off VD → reset density/overscan → 18 → 0; never reshape while task on VD | `stop()` `ClusterCast.kt:507`: evict off VD → `restoreFullscreenOnMain` → `resetDisplayAll` (density/overscan) → `teardownSeq[18,0]`. Order correct. | **FOLLOWS (order)** — but see #5 (uses move-stack to evict; non-fatal at teardown because VD destroyed after). |
| **No move-stack of a visible task off VD** (NPE B) | Switch path (`swapOnVd`, `returnAppToMain`, `evictVd`) move-stack **eliminated** (v0.66). **BUT residual `am display move-stack … 0`** survives in `guardSinksOffVd` `ClusterCast.kt:701` (fires on **CP/AA sinks** before VD teardown!), `restoreFullscreenOnMain` `CastShell.kt:147`, `stop()` `:534`, `rollback()`, `reconcileOnStart`. | **PARTIAL / DRIFTED** — switch is clean; sink-guard + teardown still move-stack. |

## 2. Why CP/AA switch broke in v0.67 (confirmed from code + on-car log)

Two compounding defects, both in the warm-switch path:

**(a) No cmd16 re-project + no fresh-launch for the new target.** `CastShell.swapOnVd:355-356`
places the incoming app with plain `am start --display vd --windowingMode 5` — **resume, not
fresh-launch** (no `--activity-clear-task`, no `am force-stop`) and **never issues cmd16**. Per recipe
root-diffs #1/#3: a resumed GL/sink task keeps its display-0 config → white; the Qt ADAS layer is
never cleared → black. First-cast of CP/AA renders fine *only because the cold `castSeq` loop
(`:464`) runs 16*; the warm switch drops it. → "CP/AA cast fine before" (cold) but wrong on switch.

**(b) The switch-AWAY gentle-move strands the sink → orphan → hard lock.** `swapOnVd` step ③ calls
`returnAppToMain(oldApp=CarPlay)` `CastShell.kt:298`. That function's **primary** path (`:301`,
executed *first for every app incl. sinks*) is gentle `am start --display 0 --windowingMode 1`.
CarPlay on the VD is **freeform** (placed via wm5) and non-resizeable → **size-compat**. Gentle-moving
a *visible* freeform task to display-0-fullscreen **crosses the freeform boundary** →
`shouldStartChangeTransition` → `initializeChangeTransition` (research-aosp-wm Q1). Research Q2 rates
this **RISKY (#3), not proven-safe** — and the on-car "4 switches NPE=0" validation only covered
**resizeable** Maps/Vietmap, never a size-compat sink. Result: the reparent half-completes, WM keeps
the old VD stack while AM drops it → **orphan**. `swapOnVd` returns old **immediately after
`landedOn(target)`** without ever verifying old is *occluded/invisible* first — so the move happens
while the sink is still visible = exactly the change-transition path.

On the next `cast()`, `divergenceOn` `ClusterCast.kt:647` correctly detects the orphan and blocks:
`oncar-v067-cp-fail-094907/castlog.txt` → `⛔ … cụm còn 1 cửa sổ mồ côi (com.byd.carplay.ui) … cần
TẮT MÁY XE`. The gate is **not** a false positive (a normally-cast CP/AA has its stack in `am stack
list` → not orphan); the orphan is real and was *created* by step (b). Fix must be upstream.

## 3. Unified correct switch — freeze-safe + renders all apps incl. CP/AA

Keep the v0.64-proven backbone (**VD stays alive; place-new-then-return-old**) but make it
occlude-correct and recipe-faithful. Single ordering for all apps:

```
U0. divergenceOn guard (keep). If already orphaned → block (needs reboot) — that's fine; we prevent creating it below.
U1. wm density = per-app dpi (scale fix, recipe #3).                              [safe]
U2. PLACE TARGET on the LIVE VD (VD never empty → not torn down; research: VD is OWN_CONTENT_ONLY):
      • normal app  → am force-stop <target>; am start --display VD --wm5 --activity-clear-task   (FRESH-LAUNCH, recipe #4)
      • keep-state / CP/AA sink → am start --display VD --wm5    (resume — NO force-stop: preserves phone-projection session)
      Gate landedOn(target,VD). Not landed → ABORT, keep old on VD (never empty → no freeze).      [TASK_OPEN transition = safe, research Q1]
U3. OCCLUDE-VERIFY: re-assert target on top + full-VD bounds; confirm OLD token isVisible()==false
      (dumpsys window). Only when old is invisible does moving it skip the change-transition (research #2, med-high).
U4. RETURN OLD off VD → display-0 fullscreen, old now invisible → NO change-transition:
      • am start --display 0 --windowingMode 1 (gentle, KEEPS state). Verify: off-VD ∧ d0 ∧ fullscreen.
      • normal old won't leave → am force-stop + relaunch d0 (state loss, safe).
      • SINK old won't leave → LEAVE it behind target (invisible), NEVER force-stop, accept 2-on-VD, no freeze.
U5. ADAS re-project (cmd16) — CONDITIONAL, see freeze analysis below.
U6. applyBounds (per-app) + commit lastCastApp/casting/lastDisplayId; post-op divergenceOn.
```

**Freeze-risk per step** (A = destroy-VD-mid-transition; B = move-stack visible task):

| Step | Risk | Mitigation |
|---|---|---|
| U2 place target | none | Fresh launch = `TRANSIT_TASK_OPEN` → never `initializeChangeTransition` (research Q1). VD keeps old → not empty. |
| U3 occlude-verify | none | Read-only. Turns U4 from RISKY→SAFE by driving old to `isVisible==false` first (research #2). |
| U4 return old | A/B avoided | Old is invisible → guard(a) suppresses change-transition → gentle move is snapshot-free. No move-stack (kills B). Sink that resists → left in place (never force-stopped, never moved-visible). |
| U5 cmd16 | **A (real)** | cmd16 **may recreate the VD** (recipe: "chờ VD mới rồi mới đặt app"; code lore `ClusterCast.kt:66`, `WmParse.clusterDisplayIds` KDoc). If it recreates while target's open-transition is live → strands target → NPE A. **See resolution.** |

**CRITICAL QUESTION resolved — is cmd16 on a LIVE VD safe from NPE A?**
Evidence says **cmd16 alone was never the proven trigger**: v0.60 froze with cmd16, but `OFFLINE-FIX-NOTES.md`
records **v0.61 removed cmd16 and STILL froze** → the real trigger was `move-stack` (NPE B) **+**
concurrent `restoreFullscreenOnMain` change-transition **+** VD mutation, all while a token was
mid-transition on the VD. The recipe explicitly calls warm cmd16 correct — **on a "VD tươi"** (a VD
with no app-token being torn). So: **cmd16 is NPE-A-safe iff no visible/transitioning app-token is on
the VD when it fires.** That is impossible in U5 (target is on the VD, mid open-transition).

→ **Resolution:** do **NOT** issue cmd16 in U5 by default. A **fresh-launched, full-VD target buffer**
composites over the Qt ADAS layer from the first frame — the recipe attributes ADAS-đen specifically
to *move-stack* (stale display-0 config), which U2 eliminates. So fresh-launch is expected to remove
ADAS-đen without cmd16. Add cmd16 back **only behind an on-car flag**, and if needed, using **Ordering
A** (below) — never with the target present.

**Ordering A (fallback, only if on-car still shows ADAS-đen after fresh-launch):**
`return old off VD FIRST → VD empty (safe, not torn down) → cmd16 (recreate OK, no token to strand) →
re-discover VD id → fresh-launch target on the new VD`. Safe under *both* cmd16 interpretations, but
sacrifices occlusion for the old-off step (fine for normal apps; for a SINK, old-off-first is the
risky step → keep the sink carve-out: don't switch-away a sink via cmd16, or force-stop the *already-
lost* orphan as recovery).

**MUST validate on-car:** (1) does warm cmd16 recreate the VD id or re-project in place? (2) does a
fresh-launched full-VD target eliminate ADAS-đen for CP/AA without cmd16? (3) is gentle
`am start --display 0` on an **occluded/invisible** CP/AA sink NPE=0 (the U3→U4 claim)?

## 4. Is `am force-stop` needed for a clean fresh-launch?

**Normal apps: YES (recipe #4).** Without force-stop, `am start --activity-clear-task` is short-circuited
by `willClearTask` when the task is still alive (`placeLadder` R3 KDoc `ClusterCast.kt:766`, clear-task
launch at `:827`; research
Q5) → the `--display` is ignored and the app resumes at display 0 (white/no-relocate). force-stop kills
the TaskRecord so the fresh `--display VD` launch is honored, giving a VD-sized buffer (fixes white).
State cost is minor for nav apps: the app relaunches but **resumes the active route** (recipe: "bấm Bắt
đầu dẫn rồi mới chiếu" keeps the route; only the pre-start preview is lost).

**CP/AA sinks: NO — force-stop is BANNED, and state-preservation is not even meaningful.** CP/AA are
projection **sinks**: the real state (nav/media) lives **on the phone** and is re-projected. Resetting
the sink activity is irrelevant; but force-stop drops the **projection session** → user must replug.
So for sinks: resume via `am start --display VD --wm5` (no force-stop, no clear-task) — the sink
re-attaches to the live phone session. Already enforced by `isPhoneProjection` exemptions in
`placeLadder` R3 (`:816`) and `returnAppToMain` (`CastShell.kt:326`); keep it.

## 5. Exact functions/steps to change

1. **`CastShell.swapOnVd` `CastShell.kt:355`** — the core rewrite.
   - `startCmd` `:356`: split into a **fresh-launch** for normal targets
     (`am force-stop <target>` + `am start --display vd --wm5 --activity-clear-task`) vs **resume**
     for `isPhoneProjection(target)||isKeepSession(target)` (current cmd, no clear-task).
   - After U2 `landedOn` gate, **add U3 occlude-verify**: re-assert target on top, apply full-VD bounds,
     and poll `dumpsys window` until `oldApp` token `isVisible==false` (add `WmParse` helper
     `isVisibleOn(dump, pkg, vd)`) **before** step ③. Only then call `returnAppToMain(oldApp)`.
   - Keep F1 (`oldApp!=target`), F4 (re-check target on VD before touching old).

2. **`CastShell.returnAppToMain` `CastShell.kt:298`** — precondition + safety.
   - Require caller to pass `oldOccluded: Boolean`; if `false`, **do not** run the gentle
     `am start --display 0` on a *visible* task (that is the v0.67 orphan trigger `:301`). For an
     occluded task the gentle path is safe; keep the sink carve-out (`:326`, no force-stop) and the
     normal-app force-stop fallback (`:331`).

3. **`ClusterCast.hotSwapOnVd` `ClusterCast.kt:603`** — orchestration.
   - Insert the optional **cmd16 ADAS re-project behind a flag**, using **Ordering A** only
     (return-old → VD-empty → `prof.svcCall(16)` → re-discover VD via existing 16-iter loop →
     fresh-launch target). Default flag OFF; wire to on-car finding (§3 MUST-validate #2).
   - Keep `divergenceOn` pre/post guards (`:604`, `:620`).

4. **`ClusterCast.guardSinksOffVd` `ClusterCast.kt:701`** — remove the last visible-task move-stack.
   - Replace `am display move-stack … 0` on a **live** sink (`:707`) with `returnAppToMain` (occlude-
     then-gentle, or leave-in-place). Only teardown paths where the VD is destroyed immediately after
     (`stop():534`, `rollback()`) may keep move-stack (VD gone → NPE B moot); leave those as-is.

5. **Recovery for an existing orphan (new, small):** when `divergenceOn` reports an orphaned
   **projection sink** specifically, offer `am force-stop <sink>` as recovery (the session is already
   lost, so force-stop is no longer destructive) to clear the WM token without a full car reboot —
   validate on-car whether this releases the orphan.

**Do NOT touch:** cold `castSeq` loop `:464` (proven), `ClusterProfile.svcCall`, parsers
(`StackParse`/`WmParse`/`DisplayParse`), `applyBounds` density/overscan tiers. Only the *warm switch*
mechanics (`swapOnVd`, `returnAppToMain`, `hotSwapOnVd`, `guardSinksOffVd`) change.
