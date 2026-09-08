# Evidence Audit — Cluster App-Swap Freeze (v0.60→v0.65)

> Auditor: senior systems engineer. Scope: confirm/refute the primitive catalog, verdict the v0.65
> freeze trigger, and assess the proposed freeze-proof swap design. **No code was edited.**
> Sources: `docs/review/HANDOFF-2026-07-23-oncar-freeze.md`, 4 on-car evidence folders, and
> `ClusterCast.kt` / `CastShell.kt` / `StackParse.kt` / `DisplayParse.kt`.

---

## (a) Primitive Catalog — confirmed / refuted

### ✅ CONFIRMED SAFE — `am start --display 1 --windowingMode 5 -n comp` (place NEW app on LIVE VD)
Places a new app on the already-live VD without freeze (NPE=0).
- **Manual adb validation ~22:15** (HANDOFF §2 "VALIDATE hypothesis"): with Vietmap cold on VD, ran
  `am start --display 1 --windowingMode 5 -n <maps>` → "**NPE=0, KHÔNG treo**", 2 apps co-resident on VD.
- **v0.61 warm castlog** (`oncar-trace…192532/castlog/cast_cast_v0.61_1784809230068.txt`):
  `R1 am start --display 1 (giữ state)` → `✓ R1 bám VD: task 9/stack 9 @d1 freeform` → `✅ Đổi sang Maps`.
  R1 **succeeded**; the freeze occurred *after* this line, from the surrounding move-off/restore ops — not R1.
- **v0.64 on-car** (HANDOFF §2): `đổi Vietmap→Maps = NPE=0, KHÔNG TREO ✅✅✅`.
- HANDOFF §4 lists it as proven: "Đặt app MỚI lên VD ĐANG SỐNG … → KHÔNG treo (validated adb + v0.64 on-car)."
- `windowingMode 5` = FREEFORM; matches `placeLadder`/`hotSwapOnVd` R1 (`ClusterCast.kt:609,797`). **CONFIRMED.**

### ✅ CONFIRMED UNSAFE — destroy/recreate the VD during a switch → **NPE A** (getDisplayInfo loop → freeze)
- Trace (`oncar-coldswitch…220338/npe-trace.txt`, identical class in `…185754/wm-npe-trace.txt`):
  `NullPointerException … DisplayContent.getDisplayInfo() on a null object reference`
  `at AppWindowToken.loadAnimation(AppWindowToken.java:2744)` → `applyAnimationLocked:2695`
  → `AppTransitionController.handleChangingApps:415` → `handleAppTransitionReady:185`
  → `RootWindowContainer.performSurfacePlacement…` → `WindowSurfacePlacer.performSurfacePlacementLoop:159`
  = surface-placement **LOOP** (track1 trace = 96 blocks / 289 KB) → head-unit freeze.
- Triggers: warm `cmd16 CMD_PROJECT` re-project (v0.60) **and** cold `teardown[18,0]` VD destroy (v0.63) — both
  froze with the same getDisplayInfo NPE (HANDOFF §2 v0.60 & v0.63; §3 row A; coldswitch `castlog-coldswitch.txt`
  = clean→teardown→cold→bounce→NPE). **CONFIRMED.** Fixed by hot-swap (keep VD alive).

### ✅ CONFIRMED UNSAFE — `am display move-stack <old> 0` (move old app OFF the VD) → **NPE B** (createTaskSnapshot)
- HANDOFF §3 row B: `DisplayContent.getRotation()` null @ `TaskSnapshotController.createTaskSnapshot`
  → `initializeChangeTransition` → `onConfigurationChanged` (task changes display = config change). **One-shot, no loop.**
- **v0.65 castlog** (`oncar-v065-freeze-225937/castlog.txt`): `① bê task 6/stack 9 @d1 freeform → màn giữa`
  → `⚠ không bê được: Exception … java.lang.NullPointerException` — the move-off throwing NPE B, **caught** by
  `CastShell.moveRejected` (`CastShell.kt:32`). HANDOFF §4 lists it as the unresolved "nút thắt". **CONFIRMED.**
- Nuance (critical for verdict below): the throw is **caught and non-fatal** — see §(b).

---

## (b) Verdict — what actually froze v0.65

**NPE B did NOT directly freeze the head unit. The freeze came from a LATER transition (HOME press on
freeform windows stranded on display 0). This is the best-supported reading, but the later-trigger itself
was NOT captured — treat the mechanism as inferred, not proven.**

Evidence:
1. **`oncar-v065-freeze-225937/npe.txt` is EMPTY (0 bytes).** No WM NPE loop was captured at freeze time
   (folder stamp 22:59:37). Contrast the captured loops elsewhere: track1 = 289 KB, coldswitch = 3.4 KB.
   Absence ⇒ either no loop was running when sampled, or the unit was already dead / rebooted (logcat wiped).
2. **The v0.65 `castlog.txt` is a STOP log** (`### cast-log stop · v0.65 · 22:57:17`). The move-off threw NPE B
   (`⚠ không bê được: Exception … NullPointerException`) **yet stop ran to completion**: PIP restored →
   `② trả đồng hồ [18,0]` → `✅ Đã trả đồng hồ gốc. App về màn giữa.` ⇒ NPE B is genuinely **one-shot, caught,
   and does not, by itself, freeze** — proven by the operation finishing normally right after it.
3. HANDOFF §2 v0.65 narrative: "Maps lên OK, Vietmap vẫn không về, 2 app scale bé ở màn chính, **user nhấn
   home → màn chính đơ**." The freeze is tied to the HOME press, i.e. a transition *after* the swap, animating
   the freeform-bounded windows left on display 0.

**Conclusion:** NPE B's real damage is *indirect* — the failed `move-stack` reparent leaves the old app as a
freeform window on display 0 (never forced fullscreen), and the *next* app-transition (HOME/recents animating
that stranded freeform token) drives the freeze, most plausibly an NPE-A-class `loadAnimation→getDisplayInfo`
during that animation. That last step is **not backed by a captured trace** (npe.txt empty); it rests on the
proven one-shot/caught nature of NPE B + the narrative. This directly validates the design's insistence that
**A must return to display-0 FULLSCREEN** — that requirement is *safety-critical*, not cosmetic.

---

## (c) Design assessment — "place B, return A (start→fullscreen, fallback force-stop), never move-stack, never destroy VD"

**Verdict: directionally SOUND — it bans both proven freeze triggers (NPE A: never destroy VD; NPE B: never
move-stack) and, unlike the pure "bring-to-front-only" option, it honors the hard single-app invariant by
actually removing A. But it has real holes that must be closed or it silently degrades to "force-stop A on
every switch" and can still leave freeze-prone freeform residue.**

Holes / races / ordering (highest → lowest):

- **H1 — Step 2 (`am start --display 0 --windowingMode 1 -n Acomp`) will usually NOT relocate A; it no-ops to
  "brought to front".** ActivityStarter finds A's existing task on VD1 and re-fronts it *there*
  (`placeLadder` R1 KDoc `ClusterCast.kt:777-780`; live proof "Warning: Activity not started, its current task
  has been brought to the front" in the v0.61 castlog). Net effect: A stays on VD → you fall to Step 3
  (force-stop) **almost every switch**, so the "keep A's state" path rarely fires. The invariant is still met
  (fullscreen A on display 0), but with **state loss on every switch** (bad for a live nav app). Decide if
  acceptable; if not, this design cannot preserve A's session and the tradeoff must be stated to the user.

- **H2 — Even when Step 2 *does* relocate A, it re-enters the NPE-B path.** Moving A from VD1-freeform to
  display-0-fullscreen is *both* a display change *and* a windowing-mode change = a `initializeChangeTransition`
  → `createTaskSnapshot`, the exact NPE B site. So Step 2 "hoping to avoid the change-transition snapshot" is
  optimistic. **However this is not fatal** (NPE B is caught/one-shot per §b) — A simply stays stuck → Step 3.
  So the design stays *safe*, just not *clean*. Do not claim Step 2 avoids NPE B; treat it as best-effort.

- **H3 — Missing "verify B LANDED before removing A" gate (ordering bug).** Steps are place-B then remove-A,
  but the design only says "verify A left the VD", not "verify B is on the VD first". If B is Google Maps and it
  **bounces off the VD** (HANDOFF §4: "App GL … hay bounce khỏi VD"), and A is then force-stopped anyway, the VD
  is left **empty** → container may tear it down → back to NPE-A territory / blank cluster. Must abort the swap
  and **keep A** when B fails to land (current `hotSwapOnVd` already does this, `ClusterCast.kt:619-624`).

- **H4 — "A left the VD" is an insufficient success check; must also verify A is FULLSCREEN on display 0.**
  This is the v0.65 lesson (§b). If A ends up on display 0 still *freeform* (bounds leftover — HANDOFF §4:
  "lưu freeform bounds → mở lại … scale bé"), the later HOME-press freeze is back. Verify **displayId==0 AND
  mode==fullscreen AND bounds reset**, else escalate to Step 3. `DisplayParse`/`StackParse` already expose
  `isFreeform`/`displayId`; `CastShell.restoreFullscreenOnMain` already force-fullscreens + `resetDisplayAll`.

- **H5 — force-stop A must EXEMPT phone-projection sinks and keep-session apps.** `placeLadder` R3 deliberately
  refuses to force-stop CarPlay/Android Auto (`isPhoneProjection`, `ClusterCast.kt:845-853`) and `keepSession`
  apps (`:835-838`) because force-stop drops the phone-projection session (user must replug). Step 3 as written
  would regress this. For those apps, fall back to bring-to-front-only (leave A hidden behind B on the VD),
  accepting a temporary 2-on-VD state, rather than force-stopping.

- **H6 — GL/SurfaceView B (Google Maps): bounce + white/stale surface even when it lands.** `placeLadder` R2
  KDoc (`ClusterCast.kt:815-820`): a running GL/video task moved onto the VD keeps display-0 config → cluster
  shows **white** until re-composited by `am task resize`, which needs freeform *alive*. So B=Maps landing is
  flaky and may need a composite kick (Step 6 bounds/DPI helps, but resize is rejected if freeform not seeded).
  Conversely, when **A** is a GL app, force-stop A actually *helps* (HANDOFF §4: "force-stop Maps mới sạch"
  clears the stuck GL surface) — an argument to prefer force-stop specifically for GL old-apps.

- **H7 — Z-order/focus race between "place B" and "remove A".** While both are on the VD, a Step-2 `am start`
  aimed at A can re-front A on VD1 (H1), flickering A back onto the cluster before Step 3 kills it. Mitigate
  with a final **re-assert B on top** (`am start --display 1 --windowingMode 5 -n Bcomp`) after A is gone —
  `hotSwapOnVd` already does this in its stuck-old-app branch (`ClusterCast.kt:643-644`).

- **H8 — Step 5 "remove any stray non-B app" must reuse `evictableOnVd` filtering, not "everything ≠ B".**
  Touching home/recents/pinned stacks is the documented launcher-freeze (`StackParse.evictableOnVd` KDoc,
  `StackParse.kt`). Restrict victims to `standard`, non-pinned, on the exact VD; and remove them via the same
  start→fullscreen / force-stop route (never move-stack).

- **H9 — Guard rails to keep:** short-circuit when `B == lastCastApp` (`oldApp != target`, `:634`); run the
  `divergenceOn` WM↔AM orphan gate first (`hotSwapOnVd` already does, `:603`); read `curVd` fresh.

**Refinements:** (1) Make the success predicate for A = "off VD **and** fullscreen on 0 **and** bounds reset".
(2) Gate B-landed before any A removal. (3) Exempt sinks/keep-session from force-stop. (4) Re-assert B on top
as the final step. (5) For GL apps prefer force-stop-A. (6) Accept + document the "state loss on switch"
tradeoff (H1/H2) — with these primitives, deterministic safety and A-session-preservation are largely
mutually exclusive.

---

## (d) Exact functions / steps to change (replace-with)

1. **`ClusterCast.hotSwapOnVd` step ③ — the move-off block** (`ClusterCast.kt:626-645`, the
   `for (attempt in 1..4) { … am display move-stack $it 0 … }` retry loop).
   **Replace with:** `am start --display 0 --windowingMode 1 -f 0x10000000 -n <Acomp>` (activity-launch to
   display-0 fullscreen; `0x10000000`=NEW_TASK). Then verify A is **off VD1 AND fullscreen on display 0 AND
   bounds reset**. If not, and A ∉ (`isPhoneProjection` ∪ `keepSession`), `am force-stop <A>` then relaunch A
   fullscreen on display 0 (windowingMode 1 + reset bounds). For sink/keep-session A: skip force-stop, leave A
   behind B. **Delete every `am display move-stack … 0`.** Keep the 4×-retry idea only around the *start→verify*
   pair, not around move-stack.

2. **`ClusterCast.hotSwapOnVd` step ④ — `CastShell.evictVd(...)`** (`ClusterCast.kt:647`). This calls the
   move-stack-based evict. **Replace** with a start→fullscreen / force-stop eviction that reuses
   `StackParse.evictableOnVd` for victim selection (standard, non-pinned, on VD) — same safe method as step ③.

3. **`CastShell.evictVd`** (`CastShell.kt`). Its core is `am display move-stack ${v.stackId} 0` (NPE-B path).
   **Replace** the relocation mechanism with `am start --display 0 --windowingMode 1 -n <victimComp>` +
   force-stop fallback (exempting sinks/keep-session). Keep victim filtering via `evictableOnVd`.

4. **`CastShell.restoreFullscreenOnMain`** (`CastShell.kt`). Its first loop
   (`am display move-stack $it 0` over `clusterIds`) is *also* the NPE-B path when a stack is still on the VD.
   **Add/route a start→fullscreen relocation** for on-VD stacks instead of move-stack. ⚠ Blast radius: this
   function is shared by `stop()` (`:544`) and `rollback()`; prefer a new `am start`-based helper used by the
   swap path over rewriting the shared function in place, or gate the move-stack loop behind a "teardown-only"
   flag (in `stop()` the VD is destroyed right after with `removeMode=MOVE_CONTENT_TO_PRIMARY`, which auto-
   migrates leftovers, so `stop()`'s move-off failing is non-fatal — do not "fix" that path aggressively).

5. **`ClusterCast.stop` move-off** (`ClusterCast.kt:534`, `am display move-stack ${e.stackId} 0`). **Leave as-is
   but note:** the v0.65 STOP castlog proves this throws NPE B too, yet stop completes because the subsequent
   VD teardown auto-migrates content. No change required for safety; optional consistency cleanup only.

**Do NOT touch:** `placeLadder` R1/R2/R3 (first-cast COLD path — proven), `divergenceOn`/`guardSinksOffVd`
(guards), `StackParse.*`/`DisplayParse.*` (pure parsers). The COLD first-cast path stays; only the *switch*
(hot-swap) eviction mechanics change.
