# Cast + Nav UX (v1.04) — Execution Prompt

> Auto-generated from plan: `docs/specs/cast-nav-ux-release-v104.html`
> Stages: 5 (4 waves + senior review) | Total deliverables: 7 owner items → R1–R9
> Variant A (Kiro CLI, `subagent` tool). Owner authorized autonomous execution 2026-08-11.

## TASK
Ship 7 Cast/Nav/Bubble improvements+bugfixes for v1.04 (see spec): hide HUD option, exclude launchers from cast, full 9 split ratios + per-ratio size, per-ratio DPI persist, single-icon floating bubble, cast fullscreen-stuck fix, nav arrival clear.

## WORKING DIR
`<repo-root>`

## CONTEXT (≤5 dòng)
- Cast is modular (`FloatingBubbleService`, `BubbleActionDispatcher`, `BubbleRenderer`, `CastShell`, `CastFacade`, `AppMover`, `ClusterProfile`; coordinator `SimpleCastIntent`). Pure logic in `:core`, Android glue in `:app`.
- Predecessor `docs/specs/cast-freeform-resize-split.html` already built 3-zone bubble + {50,30,70} ratios + 7 per-app profiles (bounds+density). This batch is the delta.
- HUD toggle = `cb_hud`/`txt_hud_status` in `activity_main.xml` (+`layout-w960dp`), wired in `MainActivity`→`NavRepository.setOutputEnabled(HUD)`.

## CONSTRAINTS
- Off-car only. Verify: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew :core:test :app:testDebugUnitTest :car-integration:test :app:assembleDebug --console=plain` → BUILD SUCCESSFUL, 0 failures.
- **No commit/push/merge/vehicle-adb** (`docs/_handoff/AUTONOMOUS-RESUME.md`).
- Do NOT touch speed-sign/HUD **injection investigation** (navopen, `docs/_handoff/hud-cluster-injection-*`, `scripts/vehicle/hud3-*`). #6 only hides the UI toggle.
- W1: no new dependency; keep SharedPreferences (`simple_cast_prefs`).
- Code health: file ≤500 LOC (split if over); `:core` no Android import; keep `LayeringRulesTest` green; when extending a prefs interface, update BOTH `SharedPrefs*` and `Fake*`.

## EXECUTION — 5-STAGE CHAIN
> Orchestrator runs each stage as a blocking `subagent` (role: kiro_default). Each sub-agent prompt MUST include: "Read the spec `docs/specs/cast-nav-ux-release-v104.html` first; read the real code before editing (W6); update spec §Design/§Tasks in parallel (W2); run the verify command; do a scope check of your wave's R-items (W3). No new deps (W1). No commit."

### Stage 1 — Wave 1 (hide HUD #6 + launcher exclusion #3)
**Sub-agent (1):** R1 + R2.
- R1: `visibility=gone` the HUD block in `layout/activity_main.xml` + `layout-w960dp/activity_main.xml`; drop `cb_hud` listener in `MainActivity`; force `NavRepository.setOutputEnabled(HUD,false)` once; keep enum value. Keep `NavigationOutputIsolationTest` green (cluster nav unchanged).
- R2: add pure `isLauncher(pkg)` (`:core` `AppMover`/classifier: matches `*launcher*`, `dudu`); in `BubbleActionDispatcher.detectForeground()` expand excluded = `queryIntentActivities(CATEGORY_HOME)` ∪ blocklist; foreground∈set or null → toast "không cast màn hình chính", no dispatch; same in `CastAutostart`.
**Key files:** `app/.../MainActivity.kt`, `app/src/main/res/layout*/activity_main.xml`, `app/.../NavRepository.kt`, `core/.../AppMover*` (or classifier), `app/.../modules/clustercast/BubbleActionDispatcher.kt`, `CastAutostart.kt`.
**Exit gate:** grep shows no `cb_hud` listener; new tests (launcher-exclusion true for home+dudu; dispatcher no-op on launcher; HUD output disabled) green; full suite + assembleDebug green.
**Handoff:** `docs/_handoff/cnux-stage-1-done.md` (files changed, new test names, isLauncher signature/location).

### Stage 2 — Wave 2 (9 ratios #4 + per-ratio DPI #5)
**Reads:** `cnux-stage-1-done.md`. **Assumes done:** HUD hidden, launcher exclusion.
**Sub-agent (1):** R3 + R4.
- R3: `CastProfile` → side×{10..90 step 10} + FULL (19 profiles); spinner 9 options/side; bounds persisted per (pkg,side,percent); unknown percent → default.
- R4: density persisted+restored under the SAME profile key; find+fix the gap where DPI "wasn't saved"; re-cast applies saved density (fallback default).
**Key files:** `core/.../simplified/SimpleCastModels.kt` (or `CastProfile`), `app/.../modules/clustercast/ClusterProfile.kt`, `Prefs.kt`/`SimpleCastPrefs`+`Fake*`, resize/DPI-apply path, ratio spinner UI + strings.
**Exit gate:** prefs round-trip test (bounds+density) for ≥3 distinct profiles; percent-coverage test; suite + build green.
**Handoff:** `docs/_handoff/cnux-stage-2-done.md` (profile key format, prefs keys, spinner values).

### Stage 3 — Wave 3 (single-icon bubble #7)
**Reads:** `cnux-stage-1-done.md` + `cnux-stage-2-done.md` (avoid dispatcher/profile write-conflict).
**Sub-agent (1):** R5.
- `BubbleRenderer` → one arrow icon, no frame/border/fill (icon + alpha only). `BubbleActionDispatcher`/`FloatingBubbleService`: tap → toggle (Idle→`CastFull(foreground)`; casting→return/stop); long-press (GestureDetector) → submenu overlay Trái(`CastSlot LEFT`)/Phải(`CastSlot RIGHT`)/Cấu hình(`Intent MainActivity` NEW_TASK). Keep drag in all states (tap/drag/long-press disambiguation by move-threshold + long-press timeout).
**Key files:** `app/.../modules/clustercast/BubbleRenderer.kt`, `FloatingBubbleService.kt`, `BubbleActionDispatcher.kt`; bubble tests.
**Exit gate:** renderer test (single zone, no border); dispatcher tests (tap toggle; longpress opens submenu; L/R slot; config intent); suite + build green.
**Handoff:** `docs/_handoff/cnux-stage-3-done.md`.

### Stage 4 — Wave 4 (cast fullscreen-stuck #1 + nav arrival #2) — investigate-first
**Reads:** prior handoffs.
**Sub-agent(s) (1–2):** R6 + R7 (may split into 2 parallel: cast vs nav, independent code areas).
- R6: read `CastShell`/`CastFacade`/`AppMover` + coordinator return/stop; root-cause windowing-mode not reset to fullscreen; fix return/stop to set `windowing-mode fullscreen` + full bounds; state never stuck freeform. Test state/logic.
- R7: read `NavNotificationListener`/`NavRepository`/`NavState`/`ClusterBroadcaster`/`Maneuver`; root-cause arrival not clearing + distance jump; emit clear/stop on arrival; guard distance regression; maneuver not stuck straight after stop. Test.
**Exit gate:** cast return=fullscreen test; nav arrival→clear + distance-regression-guard test; suite + build green; on-car checklist appended to spec §Verification (NOT ticked).
**Handoff:** `docs/_handoff/cnux-stage-4-done.md` (root causes found, on-car items).

### Stage 5 — Senior review (opus) + scope + boundary + tech-freshness
**Spawn 1 reviewer sub-agent (model claude-opus-4.8 or highest available):**
```
TASK: Senior review + patch + scope completeness + boundary shape + tech freshness
ROLE: Senior architect / code reviewer
SPEC: docs/specs/cast-nav-ux-release-v104.html
FILES: all changed across Waves 1–4 (from cnux-stage-*-done.md)
WORKING DIR: <repo-root>
REQUIREMENTS:
1. Read spec → list R1–R9; verify each has code + test + wired + user-usable.
2. Boundary shape: nav→cluster output (unchanged), prefs save↔restore (bounds+density per profile), bubble→dispatcher→coordinator intents, launcher-exclusion producer↔consumer. Trace 1 value E2E each.
3. Fix issues directly, tag [P0]–[P3]; review loop (patch→rerun tests→rerun review) until 0 P0–P1.
4. Context7 tech-freshness: confirm no deprecated Android API introduced; no new dep.
5. Report: scope checklist ✅/❌, boundary table, findings+severity, fixes, test results.
DO NOT: add features beyond spec; change public API beyond scope; commit/push.
```
**Exit gate:** 0 P0–P1; 100% scope; full suite + assembleDebug green; spec §Reviewer Log Pass N appended. **No commit** (owner decides commit + release build separately).

## ORCHESTRATOR INSTRUCTIONS
1. Re-read the spec (W6) before each stage.
2. Run stage via `subagent` (blocking, kiro_default); sub-agent prompt includes the W1/W2/W3/W6 line above.
3. Verify exit gate yourself (run the gradle verify + grep). Scope check the wave's R-items (W3).
4. PASS → write `cnux-stage-N-done.md` → next stage. FAIL → fix in-context, re-verify, do NOT skip.
5. Stage 5: senior review sub-agent (W4). W5 security scan is N/A (not committing); if owner later asks to commit, run the mandatory pre-commit scan first.
6. Final: re-verify all §Verification off-car gates; report; STOP (leave commit + release-candidate build to owner).

## ERROR RECOVERY
- Sub-agent partial output → read output, identify missing R-items, re-run stage with narrowed scope.
- Test fail after a stage → fix before proceeding.
- #1/#2 root cause turns out MCU/windowing-outside-app → document the limit in spec §Open Questions; ship the app-side fix that is possible; flag on-car dependency.
- Context approaching limit → write progress to the stage handoff, report resume point.

## FINAL EXIT CRITERIA
- [ ] R1–R9 each: code + test + wired (scope 100%).
- [ ] Full JVM suite + `:app:assembleDebug` green.
- [ ] Senior review APPROVED (0 P0–P1).
- [ ] Spec §Design/§Tasks/§Reviewer Log complete.
- [ ] On-car checklist recorded (items #1,#2,#3,#4,#5,#7) — not ticked (off-car).
- [ ] No commit/push (owner-gated).
