# Cast Freeform Resize / Split / Profiles + Bubble Redesign — Execution Prompt

> Auto-generated from plan: `docs/specs/cast-freeform-resize-split.html` (approved 2026-08-05)
> Stages: 3 | Total deliverables: T1–T4 (+ T6 gates) | Variant A (Kiro CLI, sub-agent DAG)
> Scope locked: T5 (CP/AA editor) DEFERRED. No APK build unless owner asks.

## TASK
Off-car ClusterNav Cast work: (T1) fix autostart-split double-dispatch so `CastingSplit` commits and bubble paints blue; (T2) redesign floating bubble to 3 equal horizontal semi-transparent buttons Trái·Phải·Full; (T3) reduce split ratios to 50/50, 30/70, 70/30; (T4) 7 per-app geometry profiles (full + split L/R×{50,30,70}) storing bounds+DPI, per-slot resize UI, restore on re-cast.

## WORKING DIR
`<project-root>/ClusterNav` (repo root of this checkout)

## CONTEXT (≤5 lines)
- Simplified Cast (4-state) is the ONLY active control plane. Core (`:core`) is pure JVM (no Android/dadb) — enforced by `LayeringRulesTest`.
- Freeform (`am task resize`) works for `AppType.NORMAL` after power-cycle; CP/AA MUST stay full-only (freeform → surfaceflinger crash). Keep `CastSlotValidator` / `verifyFullscreenStackAvailable`.
- Persistence = `SimpleCastPrefs` (SharedPrefs impl in `SimpleCastRuntime.kt`, fake in `SimpleCastCoordinatorTest.kt`).
- Working tree is WIP; baseline suite is RED (2 tests assert `ZONE_MIN_DP==56`, code has `40`) — record at T0, fix in T2.

## CONSTRAINTS
- Read `docs/specs/cast-freeform-resize-split.html` §Design + §Requirements BEFORE coding. Trace every change to a requirement (R1–R8).
- `:core` stays pure JVM. New pure logic (enum, prefs API) goes in `:core`, not `:app` (`pureFilesStillInApp==2` must hold).
- Any `SimpleCastPrefs` change → update BOTH `SharedPrefsSimpleCastPrefs` and `FakePrefs` in the same stage.
- Each modified file ≤ 500 LOC. Persist geometry ONLY after a verified successful shell apply (R6).
- Keep prefs backward-compat: old keys `config_*_<pkg>` (no suffix) = profile FULL.
- Verify each stage with `./gradlew :core:test :app:testDebugUnitTest` (+ `:app:assembleDebug` at final). Fix failures before handoff.

## EXECUTION — 3-STAGE CHAIN

> ⚠️ Orchestrator prompt. Run each stage as a blocking sub-agent wave. Verify exit gate before proceeding.

---

### Stage 1: Core foundation + Bubble UI (2 agents parallel)
**Agent 1A (core/model owner):** T4-core + T3
- Add `CastProfile` enum (`FULL,L50,R50,L30,R30,L70,R70` + `of(side,leftPercent)`) to `:core` `SimpleCastModels.kt`.
- Extend `SimpleCastPrefs`: `displayConfigFor(pkg, profile)` / `saveDisplayConfig(pkg, profile, config)` (keep old no-arg = FULL). Update `SharedPrefsSimpleCastPrefs` (suffix `__<profile>`, FULL = legacy key) + `FakePrefs`.
- `SimpleCastCoordinator`: add `resizeActiveSlot(side,l,t,r,b)` (CastingSplit only, per-slot task resize, persist to `(pkg,ratio,side)` profile on success); make `handleCastSlot`/`handleCastFull` read saved profile bounds/DPI before ratio/default.
- T3: `CastAutostart.populateSplitRatioSpinner` → options `[50/50,30/70,70/30]`, values `[50,30,70]`; backfill out-of-set leftPercent → 50.
- Owns files: `core/…/simplified/{SimpleCastModels,SimpleCastCoordinator}.kt`, `app/…/simplified/SimpleCastRuntime.kt`, `app/…/clustercast/CastAutostart.kt`, `core/…/simplified/SimpleCastCoordinatorTest.kt`.

**Agent 1B (bubble UI owner):** T2
- `BubbleRenderer.buildBubbleLayout` → single HORIZONTAL row, 3 equal-size zones, order Trái·Phải·Full.
- `zoneShortLabel(FULL)` → "Full". Fills semi-transparent (idle ARGB alpha ≈0x33, occupied ≈0x99) so content shows through; keep window fade alphas.
- Update `BubbleAccessibilityTest` + `CastUILifecycleSafetyTest` for new label/size/alpha; keep ≥48dp automotive.
- Owns files: `app/…/clustercast/BubbleRenderer.kt`, `app/…/clustercast/BubbleAccessibilityTest.kt`, `app/…/clustercast/CastUILifecycleSafetyTest.kt`.

**Context7 validation:** all Android SDK (Kotlin `View`/`LinearLayout`/`SharedPreferences`), no new libs — confirm no deprecated API for `GradientDrawable`/`WindowManager.LayoutParams`; note in spec §Reviewer Log.

**Exit gate (verify ALL):**
- [ ] `./gradlew :core:test :app:testDebugUnitTest` — 0 failures
- [ ] `LayeringRulesTest` green (`:core` pure, `pureFilesStillInApp==2`)
- [ ] Round-trip test: save profile `(pkg,L30)` bounds+DPI → read back exact; distinct profiles don't collide
- [ ] Bubble: 3 zones, FULL label "Full", alphas semi-transparent, ≥48dp

**Handoff → `docs/_handoff/stage-1-frs-done.md`:** files changed, new prefs key scheme, `CastProfile` API, `resizeActiveSlot` signature, test count.

---

### Stage 2: Autostart fix + Split resize UI (2 agents parallel)
**Reads:** `docs/_handoff/stage-1-frs-done.md`. Assumes CastProfile/prefs/resizeActiveSlot exist.

**Agent 2A (autostart owner):** T1
- Make `FloatingBubbleService.dispatchBootAutoStart` the SOLE autostart driver: cast RIGHT only after state becomes `CastingSplit` with `left` (state-listener gated), not blind `postDelayed`. Add `@Volatile autoStartDispatched` re-entrancy guard.
- Remove `CastAutostart.dispatchAutoStartIfEnabled()` dispatch (keep checkbox/spinner setup); Activity no longer auto-dispatches.
- Add/adjust a JVM-testable sequencing check (e.g. coordinator 2-slot sequential → `CastingSplit(left,right)`, no `Error` wipe).
- Owns: `app/…/clustercast/FloatingBubbleService.kt`, `app/…/clustercast/CastAutostart.kt`, `app/…/clustercast/MainActivityCastController.kt` (if needed).

**Agent 2B (split resize UI owner):** T4-UI
- `CastGeometryEditor.updateVisibility(CastingSplit)` → build a resize view per occupied slot (drag constrained to that slot's horizontal band) + DPI control, wired to `resizeActiveSlot(side,…)`. Full mode uses profile FULL.
- `CastResizeView`: support a slot band (min/max x) so left/right editors don't overlap.
- Owns: `app/…/clustercast/CastGeometryEditor.kt`, `app/…/clustercast/CastResizeView.kt`.

**Exit gate:**
- [ ] `./gradlew :core:test :app:testDebugUnitTest` — 0 failures
- [ ] `:app:assembleDebug` builds
- [ ] Coordinator test proves sequential 2-slot → `CastingSplit(left,right)` (no double-dispatch Error)
- [ ] No file > 500 LOC

**Handoff → `docs/_handoff/stage-2-frs-done.md`.**

---

### Stage 3: Gates — senior review + security scan
**Reads:** both handoff files. Orchestrator first re-runs full suite + `assembleDebug`.

**Senior review sub-agent** (model `claude-opus-4.8`, role senior architect): use §5 template — read spec, verify each R1–R8 deliverable (code + test + wired), boundary-shape check (SimpleCastPrefs producer↔consumer, coordinator↔UI, CastProfile↔prefs keys), Context7 freshness, patch findings [P0]–[P3], loop until 0 actionable. Verdict APPROVED only at 100% scope.

**Security scan sub-agent** (model `claude-opus-4.8`, role security reviewer): §6 template on the full diff (secrets/PII/private-keys/internal-infra). Report-only; BLOCK on real secret. Redact `/Users/<user>/` and vehicle IPs per `.kiro/steering/security-overrides.md`.

**Exit gate:**
- [ ] Full JVM suite + `assembleDebug` green
- [ ] Senior review: 0 P0–P1, scope 100%
- [ ] Security scan: CLEAN / NEEDS_CONFIRMATION (no BLOCK)
- [ ] Spec §Reviewer Log updated (Pass N)

---

## ORCHESTRATOR INSTRUCTIONS (Variant A)
1. Re-read spec `docs/specs/cast-freeform-resize-split.html` (W6).
2. T0 baseline: `./gradlew :core:test :app:testDebugUnitTest`; record pre-existing failures.
3. Run Stage 1 via `subagent` (blocking, 2 stages no depends_on). Sub-agent prompts include "use Context7 to verify APIs before coding."
4. Verify Stage 1 exit gate (run tests/grep) + scope check (W3). PASS → write handoff → Stage 2. FAIL → fix in-context, re-verify, no skip.
5. Run Stage 2 similarly.
6. Stage 3: senior review (W4) + security scan (W5) before any commit.
7. Report final: scope checklist, test result, review verdict, scan verdict.

## FINAL EXIT CRITERIA (source of truth = spec §Verification off-car list)
- [ ] `:core:test` + `:app:testDebugUnitTest` 0 failures; `:app:assembleDebug` OK
- [ ] R1 autostart split commits `CastingSplit` (test) · R2 bubble 3-horizontal+transparent (test) · R3 ratios {50,30,70} · R4/R5/R6 7 profiles + per-slot resize + restore (tests)
- [ ] R7 CP/AA still full-only · R8 layering + ≤500 LOC + FakePrefs synced
- [ ] Senior review APPROVED (0 P0–P1) · Security scan no BLOCK · spec Reviewer Log updated
- [ ] On-car visual checks left as checklist (NOT self-certified)
