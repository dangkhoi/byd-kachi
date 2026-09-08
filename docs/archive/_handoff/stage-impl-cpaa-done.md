# Stage done — CP/AA-correct cluster switching (T7 + T9)

> Session: `impl_cpaa_correct` · 2026-07-24 · off-car (unit tests only) · per `docs/_handoff/review-projection-cpaa.md` §5.
> Scope: **T7** (swapOnVd fresh-launch/resume split + occlude-verify + returnAppToMain precondition) + **T9**
> (guardSinksOffVd move-stack…0 → returnAppToMain). NOT in scope: T8 (heal/retry), T10 (code-split), cmd16 (default OFF).

## Result
`export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ; ./gradlew --offline testDebugUnitTest` → **218/218 PASS** (0 fail/err/skip).
Baseline was 209; net +9 (CastSwapTest 12→15 rewritten, WmParseTest +6 for isVisibleOn). Runtime ~58s.

## Files changed (7)

### main (4)
| File | Change |
|---|---|
| `app/src/main/java/com/byd/clusternav/modules/clustercast/WmParse.kt` | + `isVisibleOn(dump, pkg, vd): Boolean` (occlude-verify helper). Safe-default = VISIBLE when token present but flag unparseable. |
| `.../CastShell.kt` | + `resolveComp(sh, pkg)` overload; rewrote `returnAppToMain` (sh-based, `oldOccluded` param); + `occludeVerify(...)`; rewrote `swapOnVd` (sh-based, fresh-launch/resume split, U3); `evictVd` (sh-based, per-stray occlusion). |
| `.../ClusterCast.kt` | `hotSwapOnVd`→`swapOnVd(sh,…)`; `placeAppOnVd`→`evictVd(sh,…)`; **T9** `guardSinksOffVd:707` move-stack…0 → `returnAppToMain`. |
| — | NOT touched: cold `castSeq`, `ClusterProfile.svcCall`, `StackParse`/`DisplayParse`, `applyBounds` tiers, `stop()`/`rollback()`/`placeLadder` move-stack, `hotSwapOnVd` structure (no cmd16). |

### test (3)
| File | Change |
|---|---|
| `.../FakeShell.kt` | Occlusion model: `FakeStack.front` (Z-order body field), `FakeDevice.isVisibleModel()`, render `isVisible=` on token line; `--activity-clear-task` fresh-reset vs resume; `resistReturnPkgs` field; front bump on `am start`/`move-stack`. |
| `.../CastSwapTest.kt` | Rewritten — 15 tests (see below). |
| `.../CastFlowTest.kt` | UC3 (5) updated to new guardSinksOffVd contract. |
| `.../CastStressTest.kt` | SL1/SL3/SL4/SL5 updated (occluded→gentle | resist→leave); `SL1_N=20` (gentle has real Thread.sleep). |

## New / changed signatures
```kotlin
// WmParse.kt (NEW)
fun isVisibleOn(dump: String, pkg: String, vd: Int): Boolean
//   true  = pkg has a visible token on vd (or present-but-flag-unreadable → safe default VISIBLE)
//   false = pkg absent from vd (occluded/gone) OR token isVisible=false; or vd<1

// CastShell.kt (CHANGED — dropped adb, sh-based)
fun returnAppToMain(sh: (String)->String, app: String, vd: Int, oldOccluded: Boolean, log: (String)->Unit): Boolean
internal fun occludeVerify(sh: (String)->String, oldApp: String, vd: Int, log: (String)->Unit,
                           timeoutMs: Long = 1200, stepMs: Long = 200): Boolean   // NEW
internal fun swapOnVd(sh: (String)->String, target: String, comp: String, oldApp: String, vd: Int, log): SwapResult
fun evictVd(sh: (String)->String, vd: Int, keepPkg: String, log: (String)->Unit)
fun resolveComp(sh: (String)->String, pkg: String): String?   // NEW overload (adb one delegates to it)
```

## Behaviour (per §3/§5)
- **swapOnVd ② place target — SPLIT (R9):**
  - NORMAL new target (`!isSink && oldApp != target`) → FRESH-LAUNCH: `am force-stop <target>` + `am start --display vd --wm5 --activity-clear-task -n comp` (recipe #4 → composite full-VD, no white/ADAS-black).
  - SINK (`isPhoneProjection(comp,target) || isKeepSession(target)`) OR re-cast same app → RESUME: `am start --display vd --wm5 -n comp` (NO force-stop, NO clear-task → keeps phone-projection session; and re-cast-same avoids force-stopping the only app on VD = F1 anti-empty-VD).
  - Keep landedOn gate (R6) + R2 move-stack→VD fallback + F1 (`oldApp != target`) + F4 (re-check target on VD).
- **swapOnVd ③ U3 occlude-verify (R11):** re-assert target on top (resumeCmd) → `occludeVerify` polls `isVisibleOn(oldApp)` until invisible → pass `oldOccluded` to `returnAppToMain`.
- **returnAppToMain (R11):** occluded → gentle `am start --display 0 --wm1` (keeps state) + fullscreen re-force (H4) + force-stop fallback for normal; NOT-occluded → normal force-stop (safe), SINK **leave-in-place** (never force-stop, never move-stack, accept 2-on-VD).
- **guardSinksOffVd (T9):** per-sink occlusion via `isVisibleOn` → `returnAppToMain` (occlude→gentle keeps session | visible→leave). Returns false if a sink remains (relies on VD-destroy removeMode 0 relocation in teardown). `stop()`/`rollback()` move-stack unchanged.
- **Invariants kept:** VD never emptied (F1/F4 → NPE A), no `move-stack …0` on switch path (NPE B), VD never destroyed on switch.

## Tests (CastSwapTest = 15)
1 normal→fresh-launch(force-stop+clear-task) · 2 sink→resume(no clear-task/force-stop) · 3 occlude-verify-before-gentle (order) · 4 sink-resist→LEFT(no force-stop/move-stack) · 5 re-cast-same F1(no force-stop) · 6 B-bounce F4(abort,keep old) · 7 R2 move-stack→VD · 8 returnAppToMain occluded-normal gentle · 9 not-occluded-normal force-stop · 10 occluded gentle-hut→force-stop · 11 stuck-freeform→false · 12 occluded-sink gentle · 13 not-occluded-sink LEAVE · 14 evictVd(occluded stray gentle, sink not force-stopped) · 15 evictVd vd<1 no-op.
WmParseTest +6 (isVisibleOn: true/false/absent/**safe-default**/vd<1/occlusion). CastFlowTest UC3 +CastStressTest SL* updated.

## MUST-VALIDATE-ON-CAR (off-car cannot prove — gate before merge/trust)
1. **Fresh-launch renders CP/AA correctly** — full-VD freeform buffer composites over ADAS without cmd16 (no white, no ADAS-black). Recipe attributes ADAS-black to move-stack (eliminated); verify holds for CP/AA.
2. **`dumpsys window displays` visibility field name** — off-car FakeShell emits `isVisible=`. Confirm real DiLink3 dump uses the same (or adjust `WmParse.RE_TOKEN_VIS`). If field differs, `isVisibleOn` falls to safe-default VISIBLE → normal old gets **force-stopped (state loss)** each switch, sinks **left** (2-on-VD) — safe, no orphan, but suboptimal until parser tuned.
3. **Gentle `am start --display 0` on an OCCLUDED CP/AA sink = NPE 0** (the U3→U4 claim). Also confirm a resisting/size-compat sink truly stays visible so occlude-verify → leave (no orphan).
4. **Switch matrix** Maps↔CarPlay↔AA↔Vietmap many rounds: each NPE=0, no orphan, cluster shows correct app; switching AWAY from CP/AA keeps the phone session (no replug).

## Deferred (not this session)
- **T8** — healDivergence / restoreConsistentState / bounded retry / "🔧 Thử phục hồi" UI (R10).
- **T10** — split ClusterCast.kt (>500 LOC) into 6 files + dead-code removal.
- **cmd16 (Ordering A, flag-gated default OFF)** — only if on-car still shows ADAS-black after fresh-launch (§3).
- Version bump / APK build / commit / on-car run.
