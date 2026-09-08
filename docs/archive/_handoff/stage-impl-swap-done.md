# Stage: impl freeze-proof cluster app-switch (T1/T2/T3/T5) — DONE

> Session: `impl_freeze_swap` · 2026-07-24 · off-car (no device/adb) · verify = unit tests (FakeShell/FakeDadb).
> Plan: `docs/specs/freeze-proof-cluster-switch.html` (Design D1–D3, Tasks T1/T2/T3/T5, Reviewer Log Pass 1 F1–F10).

## Files changed

| File | Change |
|------|--------|
| `app/src/main/java/com/byd/clusternav/modules/clustercast/CastShell.kt` | **+ `returnAppToMain`** (T1), **+ `SwapResult` + `swapOnVd`** (T2 shell core), **rewrite `evictVd`** (T3) |
| `app/src/main/java/com/byd/clusternav/modules/clustercast/ClusterCast.kt` | **rewrite `hotSwapOnVd`** (T2) — delegate ②③④⑤ to `CastShell.swapOnVd`, keep ① density + ⑥ applyBounds + commit + `divergenceOn` guards |
| `app/src/test/java/com/byd/clusternav/modules/clustercast/FakeShell.kt` | **extend** (F9): `am force-stop`, `am start --display`, `cmd package resolve-activity`; `commands`/`forceStoppedPkgs`/`afterStackList`/`stuckFreeformPkgs`; `FakeStack.mode` → var; **+ `FakeDadb`** |
| `app/src/test/java/com/byd/clusternav/modules/clustercast/CastSwapTest.kt` | **NEW** — 10 tests |
| `docs/specs/freeze-proof-cluster-switch.html` | §Design D2/D3 impl note, §Tasks impl status, Reviewer Log **Pass 2** |

## New helper signatures (additive — no existing public signature changed)

```kotlin
// CastShell.kt
fun returnAppToMain(adb: dadb.Dadb, sh: (String) -> String, app: String, vd: Int, log: (String) -> Unit): Boolean
internal data class SwapResult(val target: StackEntry?, val note: String)
internal fun swapOnVd(adb: dadb.Dadb, sh: (String) -> String, target: String, comp: String, oldApp: String, vd: Int, log: (String) -> Unit): SwapResult
// evictVd(adb, sh, vd, keepPkg, log) — signature unchanged, body rewritten to route via returnAppToMain
```

`returnAppToMain` returns `true` **only** when the app (a) left the VD **and** (b) `displayId==0` **and**
(c) `mode=="fullscreen"` (R3/H4). `keepSession = ClusterCast.isKeepSession(app) || ClusterCast.isPhoneProjection(comp, app)`
is computed **internally** (F2) — caller cannot wire it wrong. Never emits `am display move-stack …0`.

## Tests added (`CastSwapTest`, 10)

1. `swap thuong - dat Maps len VD, force-stop Vietmap, Vietmap ve d0 fullscreen, VD chi con Maps`
2. `re-cast cung app - KHONG force-stop target, VD khong rong (F1)`
3. `B bounce sau landed - HUY swap, giu old, KHONG force-stop (F4)`
4. `old la CarPlay - KHONG force-stop (isPhoneProjection che du keepSessionApps rong) (F2 F5)`
5. `switch path tuyet doi khong co move-stack ra display 0 (evict cung dung force-stop)`
6. `returnAppToMain app thuong - force-stop roi ve d0 fullscreen tra true (F3)`
7. `returnAppToMain app ket freeform-be tren d0 - ep lai roi van tra false (F3)`
8. `returnAppToMain sink giu phien - KHONG force-stop, chi am start nhe (F2)`
9. `evictVd force-stop app la thuong nhung mien tru sink (F6)`
10. `evictVd vd khong hop le thi no-op`

## Test count

`export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ; ./gradlew --offline testDebugUnitTest`
→ **194 / 194 PASS**, 0 failures, 0 errors (baseline 184 + 10 new; CastSwapTest=10, CastFlowTest=15,
CastStressTest=5, SinkGuardTest=5, StackParseTest=20 — all pass).

## How each binding finding is satisfied

- **F1 (P0)** — `swapOnVd` step ③ guarded by `if (oldApp.isNotBlank() && oldApp != target)`; re-cast test asserts no force-stop of target, VD not empty.
- **F2** — `returnAppToMain` computes `keepSession` internally with `isPhoneProjection`; CarPlay test proves it is not force-stopped even though `keepSessionApps` is empty.
- **F3** — `returnAppToMain` verifies `off-VD ∧ d0 ∧ fullscreen`, re-forces `am start --windowingMode 1` if freeform, returns false if still not fullscreen (test 7).
- **F4** — `swapOnVd` re-checks `B on VD` via fresh `am stack list` immediately before returning old; bounce → abort keeping old (test 3, driven by `afterStackList` hook).
- **F5** — R6/abort branch only logs + returns; `restoreFullscreenOnMain` is NOT called on the switch path (grep-verified).
- **F6** — `evictVd` routes every stray via `returnAppToMain` (also used by cold `placeAppOnVd`); sink stray exempted (test 9).
- **F10** — `swapOnVd` re-picks B via `landedOn` after re-assert; `hotSwapOnVd` feeds that re-picked entry to `applyBounds`.

## Deviation from plan

- **Extracted the D2 shell orchestration into `CastShell.swapOnVd` (Context-free)** instead of keeping it
  inline in `ClusterCast.hotSwapOnVd`. Reason: `hotSwapOnVd` needs `android.content.Context`
  (`labelOf`/`setLastCastApp`→`SharedPreferences`) → not unit-testable in plain-JVM; project has no
  Robolectric. Extracting the shell layer makes F9's swap-sequence tests possible and matches Code-Health
  ("reusable shell logic in CastShell.kt"). Behavior unchanged — only code location. `hotSwapOnVd` keeps
  the Context-bound bits (density, applyBounds, commit flags, divergence guards).
- Added `FakeDadb` + `stuckFreeformPkgs`/`afterStackList` to the test harness (F9 was explicit about
  extending FakeShell; `FakeDadb` needed because `resolveComp` takes `adb` — `Dadb` is a Kotlin interface
  with default impls, only `open`/`supportsFeature`/`shell`/`close` need overriding).

## NOT done (out of this session's scope)

- **T4** — HUD road-name (NavFormat/ClusterBroadcaster).
- **T6** — version bump 0.66, APK build, on-car validation, commit/security-scan/merge.
- **On-car validation** remains a MANDATORY gate before trusting "no freeze"/merge (see spec §Verification on-car).
  Off-car tests only lock the command sequence + invariants; FakeShell cannot reproduce the WM NPE.
