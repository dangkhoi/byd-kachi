# Review — Robustness / Recovery + Code Health · ClusterCast

> Auditor: senior eng (off-car, no device) · 2026-07-24 · Scope: `modules/clustercast/`
> Complaint: a failed cast (Maps→CarPlay) makes the app "die" — every later cast is refused
> ("cần TẮT MÁY XE") until a hard reboot. No graceful exception handling, no retry, no self-heal.
> **Analysis only — no code edited.** Files: `ClusterCast.kt` (1284 LOC), `CastShell.kt` (407 LOC).

---

## PART A — ROBUSTNESS / RECOVERY

### A1. Failure-path trace of `cast()` (fun @ L363, body in `vdExec` L367, `finally{busy.set(false)}` L503)

| # | Failure | app process | busy lock | cluster/VD state | Can cast again? |
|---|---|---|---|---|---|
| 1 | blank/invalid target (L370-371) | untouched | released (503) | untouched | ✅ yes |
| 2 | app not installed (`isInstalled` L~382) | untouched | released | untouched | ✅ yes |
| 3 | `resolveComp==null` (L~393, no launcher) | untouched | released | untouched | ✅ yes (fatal for that app) |
| 4 | VD not detected, `vd<1` after 8s (L~484-490) | opened on d0 | released | anim=0 set → `rollback()` restores | ✅ yes |
| 5 | ladder all rungs fail (`placeAppOnVd`→null, L~498) | left on d0, `restoreFullscreenOnMain`+`rollback` (L~510-516) | released | restored to clock | ✅ yes |
| 6 | exception mid-cast (`catch(Throwable)` **L500**) | varies | released | `if(clusterMutated) rollback` | ✅ **iff** clusterMutated was true |
| 7 | exception in use{} **before** inner try (PIP/StackParse/`isWarm`) | varies | released | `.onFailure` L502 logs only — **PIP appop `ignore` NOT restored, anim not touched** | ⚠️ yes, but PIP leak (self-heals next cast via `restorePip` L~406) |
| 8 | **divergence orphan detected** (`divergenceOn` **L426**) | untouched | released | untouched (guard fires *before* mutation) | ❌ **NO — refused on every future cast** |
| 9 | CP/AA won't land (`isPhoneProjection`, R3 skipped, `placeLadder` L~790) | sink nudged back to d0 (best-effort) | released | `rollback` restores clock | ✅ yes (by policy — keep-session) |

**Key correction to the complaint's mental model:** the brick is **NOT** a stuck `busy` lock.
Every refuse path returns inside `use{}`/`runCatching{}` and reaches `finally{busy.set(false)}` (L503),
so `busy` is always released. The brick is **path #8**: an orphan persists on the VD and
`divergenceOn` re-detects it on *every* subsequent `cast()` (L426) / `hotSwapOnVd` (L605) /
`placeAppOnVd` (L722) / `applyScaleLive` (L986) → hard refuse with the "TẮT MÁY XE" string, forever.
There is **zero self-heal** anywhere (`grep force-stop|recover|heal` finds no orphan-clearing path).

**Secondary fragility (P2):** `rollback()` (L571) calls `guardSinksOffVd(...)` (L576) **outside** its
`runCatching` (L577). If adbd is hung and that shell call throws, `rollback` throws → the trailing
`setCasting(false)`/`lastDisplayId=-1` (L591, not in a `finally`) are skipped → `casting` stuck `true`
→ `reconcileOnStart` self-blocks (`if(casting) return`). Only the 60s watchdog recovers it.

### A2. THE BRICK — is the orphan actually unrecoverable by shell?

**Orphan** = a stack WindowManager sees on the VD (`mStackId`, `mDisplayContent=null`, comment L207)
that ActivityManager does **not** list (`WmParse.orphanStacksOn`, WmParse.kt L~140). `am`/`wm`
*stack* commands are keyed off AM bookkeeping → they genuinely cannot touch it. **But `am force-stop
<pkg>` is not a stack command — it kills by package/uid.** Process death fires the WMS binder
death-recipient, which removes *all* WindowStates owned by that process, including orphaned ones.

This is **already the codebase's own proven primitive**, just never wired to the orphan guard:
- `docs/_handoff/research-aosp-wm.md` Q3 (L101-124): *"Force-stop is a snapshot-free,
  change-transition-free window-removal path. SAFE from NPE A and NPE B, provided the VD is not
  destroyed (another app remaining guarantees that). The VD is owned by `com.xdja.containerservice`
  (`FLAG_OWN_CONTENT_ONLY`); force-stopping an app does not destroy it."*
- `CastShell.returnAppToMain` ③ (L~330) and `evictVd` (L~395) already use `am force-stop` as the
  safe way to remove an app from the VD without triggering the freeze NPE.

**Verdict: the "chỉ TẮT MÁY XE mới sạch" claim (L207, L663-665) was asserted, never tested.**
Force-stop of the orphan pkg is the well-grounded self-heal that was missing.

**Proposed `healDivergence(sh, vd, log): Boolean`** (new, in the divergence unit — see B5):
1. Collect orphan pkgs: `WmParse.pkgsOn(wm, vd)` for stack ids in `WmParse.orphanStacksOn(...)`.
2. For each (validate with `PKG_OK`): `sh("am force-stop $pkg")` → process death removes its windows.
3. `Thread.sleep(~600ms)` for the death-recipient to run.
4. Re-run the double-sample `divergenceOn(sh, vd)`.
5. Clear → `true` (proceed with cast). Still orphaned → `false` → **only now** show the reboot
   message, honestly reworded to *"đã thử tự phục hồi (force-stop cửa sổ mồ côi) nhưng chưa sạch —
   cần tắt máy xe"*, plus a user-facing **"🔧 Thử phục hồi"** button (wired to this same routine).

**Wire:** at the 4 guard sites (L426/605/722/986) replace `divergenceOn()?.let{refuse}` with
`diverged → if(healDivergence()) proceed else refuse`. Guard is preserved as **last resort**, not
first resort — never a silent brick.

**Honesty gate (repo culture "đo, đừng đoán"):** off-car we can only assert the command sequence +
that force-stop is the AOSP-correct window-removal path (Q3 = high confidence for a live-VD app; the
`mDisplayContent=null` orphan is a reasonable extension but **UNVERIFIED on-car**). Ship behind the
same on-car gate as v0.66. On-car check: create WM≠AM state → `am force-stop <orphan>` → confirm
`dumpsys window displays` loses the orphan stack + `am stack list`/`wm` regain control.

### A3. Graceful cast-failure handling (design)

The pre-cast state must be an **invariant to restore to**: gauges (or the previously-cast app) on the
cluster, target app fullscreen on d0, PIP/anim/density/overscan reset. A failure must land in exactly
one of two states — **(a) committed** (app on VD, `casting=true`) or **(b) fully pre-cast** — never a
half-broken orphan. Concretely:
1. **Widen the rollback envelope.** `clusterMutated` (L~460) arms rollback only *after* `castSeq`.
   But `blockPip` (L~419) mutates appops *before* that. Introduce a single `mutated` set the moment
   *any* system state changes (PIP, anim, castSeq) and have one `finally`-guaranteed
   `restoreConsistentState()` cover PIP + anim + VD + clock together (path #7 today leaks PIP).
2. **One handler, specific first.** Replace the lone `catch(Throwable)` (L500) with
   `catch(IOException)` (adbd/dadb transport — retryable, see A4) vs `catch(Throwable)` (logic — log
   full stack, rollback). Currently only `e.message` is logged (L500) → no stack for field triage.
3. **Fix `rollback()` atomicity:** move `guardSinksOffVd` (L~575) *inside* the `runCatching`, and put
   `setCasting(false)/lastDisplayId=-1` in a `finally` so state always resets even if teardown throws.
4. **Surface a next action, don't die.** On land-fail (#5) / heal-fail (#8) / CP-AA (#9), the log/UI
   must offer **retry**, **try another app**, **toggle keep-session (◈)**, or (last resort)
   **recover/reboot** — instead of a dead end.

### A4. Retry policy

| Failure | Class | Action |
|---|---|---|
| dadb connect / `IOException` mid-cast (transport) | transient | bounded retry **2×**, backoff 500ms→1s+jitter (currently `.onFailure` L502 gives up immediately) |
| VD not detected `vd<1` (#4) | transient | retry `castSeq` **once** before rollback (busy head-unit races VD creation) |
| `landedOn` timeout R1/R2 (`CastShell.landedOn` L~20) | transient | already laddered R1→R2→R3; add **1** whole-ladder retry for the R1 `am start` race only |
| divergence orphan (#8) | recoverable | `healDivergence` (A2) **once**, then refuse |
| app not installed / `resolveComp==null` (#2,#3) | **fatal** | no retry — surface "install app" |
| CP/AA won't land w/o R3 (#9) | **fatal-by-policy** | no retry — surface "toggle ◈" |
| divergence persists after heal | **fatal** | no retry — recover/reboot action |

Guard rails: cap total attempts (≤2), never retry fatal, never retry a mutation that wasn't rolled
back first (avoid stacking `am start` — the exact "đẻ mồ côi" pattern from the 22/07 incident).

---

## PART B — CODE HEALTH

### B5. `ClusterCast.kt` is 1284 LOC → must split (<500). Behavior-preserving boundaries:

| New file | Moves | ~LOC |
|---|---|---|
| `CastState.kt` (state/prefs) | all `@Volatile var` prefs + `save`/`loadPrefs` + `applyDefaultModes` + `migrateDriftedRects` + setters (`setScale`/`scaleOf`/`setT3`/`setKeepSession`/…) + `activeProfile`/`setActiveProfile` | ~260 |
| `CastPlacement.kt` | `placeAppOnVd`, `placeLadder`, `applyBounds`, `escalateFreeform` | ~230 |
| `CastDivergence.kt` | `divergenceOn`, `sampleDivergence`, `guardSinksOffVd`, `phoneProjectionSinksOn`, `isPhoneProjection` + **new `healDivergence`** | ~130 |
| `CastTeardown.kt` | `stop`, `rollback`, `reconcileOnStart`, `unseedFreeform`, `repairLegacyAnim` | ~300 |
| `CastLifecycle.kt` | `watchdogTick`, `autoCastOnBoot`, `autoDiag`, `measureClusterInProcess`, `castLogger` | ~150 |
| **`ClusterCast.kt`** (orchestrator, kept) | `cast`, `hotSwapOnVd`, `takeBusy`/`vdExec`, `blockPip`/`restorePip`, anim helpers | ~300 |

Note: extracting state out of a Kotlin `object` needs care — either make `CastState` its own `object`
referenced by the orchestrator, or pass state explicitly to the now-pure placement/divergence fns
(they already take `sh` — same pattern as `CastShell`/`StackParse`). No behavior change.

### B6. Dead / accumulated code (v0.36→v0.67 churn)

- **`escalateFreeform` + `t3Apps`/`setT3`/`isT3` + `T3Daemon.java` (whole file)** — L~890-935 + L108-113.
  Its own KDoc (L108-113, L~885) says the daemon "KHÔNG thể đặt app ở chỗ R2 đã hụt" and only acts
  when freeform is already alive. Near-dead opt-in dead-weight → **[P2] remove path + file**.
- **`PROFILE_CURVED=30` / `PROFILE_RECT=31`** consts (L64-65) — **confirmed dead** (`grep` finds zero
  references in main or test); superseded by `ClusterProfile.styleOps` (`styleCmdFor` L~300). **Delete [P3].**
- **One-time migrations** now long-past: `applyDefaultModes`/`DEFAULTS_KEY` (L~270), `migrateDriftedRects`/
  `RECT_FIX_KEY` (L~290), `repairLegacyAnim`/`LEGACY_ANIM_KEY`/`animRepair36` (L~1020). Still wired but
  crufty — **[P3]** gate for removal after a version horizon.
- **Contradictory / stale comments:** L78 references cmd16 "đã bỏ ở v0.61" while v0.64/66 hot-swap
  narrative supersedes it; L207 + L663-665 assert orphan "chỉ TẮT MÁY XE mới sạch" — the exact claim
  A2 refutes. Version-era comment layers (v0.36/42/50/58/60/63/64/66) coexist → **[P3]** prune.

### B7. Bare catch / swallowed errors · scattered state · duplication

- **[P1] `catch(Throwable)` L500** logs only `e.message`, no stack; too broad (see A3.2). Also
  `.onFailure` L502 does not roll back PIP/anim for pre-inner-try throws (path #7).
- **[P2] `rollback` non-atomic** — `guardSinksOffVd` (L576) outside `runCatching` (L577), state reset
  at L591 not in `finally` (A1 secondary).
- **[P3] silent swallows:** `.onFailure {}` empty at L1039 (`repairLegacyAnim`) and L1144
  (`reconcileOnStart`) — intent OK ("retry next open") but *no* log line at all.
- **[P2] duplication — `fun sh(c: String)`** closure re-declared ~7× (`cast` L~375, `stop` L~518,
  `applyScaleLive` L~965, `reconcileOnStart` L~1155, `watchdogTick` L~1215, `unseedFreeform` L~1065,
  `rollback` inline lambdas). Variants differ only in stderr logging → extract one `shellOn(adb, log)`
  wrapper. The `errorOutput`-into-string + `moveRejected`/`resizeRejected` string-sniffing (CastShell
  L~40, L~160) is the intentional "measure via truth" contract — keep, but centralize.
- **[P3] teardown loop** `teardown.forEachIndexed{ if(i>0) sleep(800); sh(svcCall) }` duplicated in
  `stop` (L~547), `rollback` (L~583), `reconcileOnStart` (L~1135) → extract `runTeardown(seq)`.
- **[P2] scattered "am I casting?" truth:** `casting` (RAM) vs `am stack list` (truth) vs
  `lastDisplayId` + persistent state split across 2 prefs files (`clustercast`, `clusternav_state`).
  Repeatedly reconciled ad hoc. Consolidating into `CastState` (B5) reduces the drift surface.

---

## Prioritized cleanup

- **[P0] Self-heal the brick (A2):** add `healDivergence` (force-stop orphan → re-check), wire the 4
  guard sites to heal-then-refuse, add "🔧 Thử phục hồi" UI action. Behind on-car gate. *This is the
  fix for the maintainer's complaint.*
- **[P0/P1] Consistent-state guarantee (A3):** widen `mutated` to cover PIP, single
  `restoreConsistentState()` in `finally`; fix `rollback` atomicity (guardSinks inside runCatching +
  `finally` state reset).
- **[P1] Exception specificity (A3.2):** split `IOException` (retry, A4) vs logic; log full stack.
- **[P1] Bounded retry (A4):** transport + VD-detect + single ladder retry with backoff; never retry
  fatal/un-rolled-back.
- **[P2] Split `ClusterCast.kt` <500 LOC (B5).** Extract `sh` wrapper + `runTeardown` (B7).
  Remove T3/`escalateFreeform` dead-weight (B6).
- **[P3] Prune stale migrations + contradictory comments + unused consts (B6); log silent swallows (B7).**

**Verification note:** all of A1–A4 shell contracts are unit-testable off-car via the existing
`FakeShell`/`FakeDadb` (see `CastSwapTest`, `SinkGuardTest`); the *effect* of force-stop on a real
orphan (A2) and the freeze-safety are **on-car only** — gate exactly like v0.66 (§3 of
`HANDOFF-2026-07-24-v066-freezeproof.md`).
