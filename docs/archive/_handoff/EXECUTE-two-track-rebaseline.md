# ClusterNav Two-Track Re-baseline — Execution Prompt

> Generated from: `docs/specs/clusternav-two-track-final-plan.html`
> Binding detail: `docs/specs/cluster-cast-rebaseline.html` + `docs/specs/clusternav-uxui-rebaseline.html`
> Dead Reckon dependency: `docs/specs/dead-reckon-revalidation.html`
> Working directory: `<workspace>/ClusterNav` (resolve to the current repository root before execution)
> Stages: 13 traceability waves in one autonomous off-car tranche | Runtime status: **IMPLEMENTATION AUTHORIZED / VEHICLE NOT STARTED**

## TASK
Implement exactly two independent product tracks after all required approvals: **A) Navigation + HUD** with one source/session and isolated lane/HUD outputs; **B) Cluster Cast** with durable V2 state, journal, execution, recovery and UI. Dead Reckon remains removed from target UX and is a separately approved release prerequisite, not a third track.

## OWNER-AMENDED AUTONOMOUS AUTHORIZATION MODEL

This section supersedes every contradictory per-wave implementation/build/car stop later in this document. Wave IDs remain traceability labels; they are not owner interaction checkpoints.

Owner decision recorded at `docs/_handoff/two-track-spec-approvals.md` on 2026-07-24:

1. The six existing Cast/UX section approvals remain canonical.
2. The approved Stage 1 baseline remains the provenance anchor; preserve the dirty tree and use the explicit union of Stage 2–10 allowlists. Any new path must be listed in the active progress handoff before mutation.
3. `AUTONOMOUS_OFFCAR_APPROVED stages=2-10` authorizes continuous code/UI/test/docs implementation across Waves 2–10 without intermediate build/install/car stops.
4. Required Dead Reckon retirement may proceed only after its deferred closure review is completed and an exact DR mutation list is recorded; this owner amendment authorizes that reviewed removal so the test candidate contains exactly two runtime tracks.
5. `AUTONOMOUS_TEST_BUILD_AUTH count=1 variant=release purpose=vehicle-test exactSourceId=generated-from-final-offcar-source output=collision-safe` authorizes exactly one final test build after full off-car tests, review, sensitive-data scan and exact-source closure. Use the collision-failing collector and record APK SHA-256, certificate, version, toolchain and flags.
6. The amendment does **not** authorize install, ADB/car mutation, commit, push, merge, public release, or physical deletion of rollback-readable legacy Cast code.
7. After building and generating vehicle scripts, stop at `WAITING_FOR_VEHICLE_TEST`. Stage 11 resumes when the owner has the car and supplies the exact APK SHA/vehicle context.
8. Stage 12 remains a separate future post-soak cleanup release.

No additional owner prompt is required between off-car waves. Stop only for a genuine security `[BLOCK]`, an unavoidable scope change outside the approved two-track product, or a destructive external action not authorized above.

## GLOBAL CONSTRAINTS

- Preserve the large pre-existing dirty tree. Never run reset, clean, checkout/switch, branch deletion, blanket stash, or blanket `git add .`.
- The prior one-build authorization is consumed by an invalidated candidate. Do not build a replacement without a new explicit authorization. Do not install, commit, push, merge, release, or mutate the car.
- Complete source, tests, review, security and provenance preparation first; then stop at `WAITING_FOR_REPLACEMENT_BUILD_AUTH`. Existing APKs remain prohibited.
- Physical power-button reboot is required for reboot evidence; `adb reboot` does not satisfy it.
- Public repository: run pattern-based + semantic sensitive-data scan before every separately authorized commit/push. `[BLOCK]` stops the workflow; `[WARN]` needs user confirmation.
- Exactly two runtime pipelines. No shared live ADB gateway/transport, mutable state, executor/thread pool, cache, lock, journal/store, epoch, cancellation, queue, watchdog, retry/recovery policy, lifecycle owner, or start/stop/reset/recovery call.
- Shared code is limited to pure encoding/formatting, immutable visual tokens/components and build provenance.
- Home renders two read-only summaries and dispatches to one selected owner; Home never orchestrates both.
- Dead Reckon stays absent from target UX. Before mutating DR/mock-provider paths, complete the deferred closure review and materialize the exact owner-approved removal list; then remove/quiesce the DR product runtime off-car and prove no third runtime track remains. No provider/car mutation or release claim is implied.
- `cluster-cast-rebaseline.html §D8` and `docs/specs/cast-ui-state-v2.schema.json` are the canonical owners of Cast schema, precedence, `StopDisposition`, all 20 recovery mappings, `UnavailableReason`, action translation/allowed actions and rollout/action ownership. The Stage 0 canonical SHA-256 fixture is `d2c143a5369487fce312bb0a506785a3396280b689ddfd7842557e1f6273ca7b`.
- Legacy active sessions default to `LEGACY_ACTIVE_READ_ONLY + UNAVAILABLE(LEGACY_SESSION_UNSAFE)`.
- One compensation attempt only. A full-operation retry is distinct and legal only after independently verified compensation plus fresh plan/new epoch.
- PARKED/MOVING/UNKNOWN is diagnostic-only and cannot gate features. Destructive sink/orphan recovery still requires typed owner/session-loss truth, two stable samples, explicit consequence confirmation, one durable attempt and verified postconditions.
- One hidden protected residue is allowed only as `ACTIVE_DEGRADED`; second residue is blocked; no display-global geometry while residue exists.
- No physical legacy Cast deletion during initial V2 release. Deletion belongs to a later separately approved cleanup release.
- Direct evidence remains `NOT STARTED` until produced and bound to provenance.
- Use one documentation owner per approved spec per stage. Implementers edit code/tests/handoffs only. Evidence ledgers are subordinate to approved specs and never contain approval state.

## REQUIRED CONTEXT7 RULE

Before changing or introducing any framework, dependency, Android API pattern, persistence mechanism, IPC, process model or test technology, each implementation agent must:

1. Run `resolve-library-id` then `query-docs` against official/current docs.
2. Verify current stable version, API status, deprecations and alternatives.
3. Prefer current non-deprecated APIs compatible with minSdk 29 / Android 10.
4. Record result in the owning evidence ledger and spec Design/References through the documentation owner.
5. If no technology changes, record `NO_TECH_CHANGE`; do not perform speculative migrations.

## DOCUMENT AND HANDOFF OWNERSHIP

- Cast spec owner: one agent only for `docs/specs/cluster-cast-rebaseline.html`.
- UX spec owner: one agent only for `docs/specs/clusternav-uxui-rebaseline.html`.
- Plan owner: one agent only for `docs/specs/clusternav-two-track-final-plan.html`.
- Track evidence ledgers:
  - `docs/design/navigation-hud-evidence.html`
  - `docs/design/cluster-cast-evidence.html`
- Stage handoffs: `docs/_handoff/two-track-stage-<N>-done.md`.
- Handoff fields: exact source ID, approved allowlist, files changed, contracts/shapes, tests and counts, Context7 results, evidence paths, unresolved findings, dirty-tree hash comparison, next authorization state.

## EXECUTION — 13 TRACEABILITY WAVES / ONE AUTONOMOUS OFF-CAR TRANCHE

> Orchestrator: execute directly in bounded 3–5-file batches under `.kiro/steering/execution-reliability.md`. Do not use a large blocking sub-agent DAG. Wave handoffs are concise progress/evidence checkpoints and do not pause execution. Run focused tests after each logical patch area, then one full off-car suite, direct senior boundary review, security scan, exact-source closure, one authorized test build and vehicle-script generation.

### Stage 0 — Document closure and approval checkpoint · no code

**Reads:** all four spec/plan documents and the independent senior synthesis.

**Sub-agents:** 2 reviewers in parallel, then 1 synthesis reviewer.
- Reviewer A — architecture/safety: canonical Cast shape, legacy Stop, compensation, PARKED, fault domains, Navigation producer, rollout/downgrade.
- Reviewer B — scope/execution: provenance, authorization, DR dependency, public docs, car waves, evidence honesty and deletion horizon.
- Synthesis reviewer — reconcile only independently supported findings; report `[P0]`–`[P3]`; documentation owner patches accepted items and reruns focused review.

**Limits:** report/documentation only; no runtime, build, install, git mutation or evidence claim.

**Exit gate:**
- [ ] 0 unresolved accepted P0/P1 in both main specs and consolidated plan.
- [ ] Cast D8 and UX consumer mirror match field/nullability/enum/precedence semantics.
- [ ] All direct V2/UX/car evidence remains `NOT STARTED`.
- [ ] User separately records all six `SPEC_APPROVAL` tokens.

**Stop state:** `WAITING_FOR_SPEC_APPROVAL` if any token is absent.

**Handoff:** `docs/_handoff/two-track-stage-0-done.md`.

---

### Stage 1 — Baseline manifest and public-doc inventory · no runtime code

**Reads:** Stage 0 handoff; all three detailed specs; consolidated plan; current git status/diff and public docs.

**Sub-agents:** 2 bounded agents, then 1 senior reviewer.
- Agent 1 — baseline/provenance, max 5 deliverables:
  1. Branch/HEAD and full tracked-diff hash.
  2. Intended untracked path list and per-file hashes.
  3. Path classification: `BASELINE_INPUT`, `PLAN_DOC`, `GENERATED_ARTIFACT`, `UNRELATED_PRESERVE`, `FUTURE_STAGE_OUTPUT`.
  4. Existing APK hash/version/signature inventory as evidence-only.
  5. Per-stage path allowlists and `EXACT_SOURCE` schema.
- Agent 2 — docs/evidence inventory, max 5 deliverables:
  1. README and `docs/HUONG-DAN.md` classification.
  2. Diagnostics/reference/review-page classification.
  3. Screenshot and APK/version-link classification.
  4. Gesture/state/flag/rollback claim inventory.
  5. Historical-artifact labeling rules.
- Senior reviewer — verify completeness, hashes, unrelated preservation and absence of hidden runtime change; patch manifest/docs only.

**Exit gate:**
- [ ] Owner approves manifest and allowlists as `BASELINE_APPROVAL`.
- [ ] Existing APKs cannot be overwritten/relabelled.
- [ ] Before any new build/install authorization, existing APKs/guides/screenshots are quarantined as historical/unsupported with immutable SHA/version/known flags; current-facing T1/T3/Dead-Reckon and safety-success wording is removed.
- [ ] Runtime/source paths remain byte-for-byte unchanged from stage entry.
- [ ] `IMPLEMENTATION_AUTH` is explicit before Stage 2.

**Stop state:** `WAITING_FOR_BASELINE_APPROVAL` or `WAITING_FOR_IMPLEMENTATION_AUTH`.

**Handoff:** `docs/_handoff/two-track-stage-1-done.md`.

---

### Stage 2 — Independent contract foundations

**Reads:** Stage 1 handoff; approved allowlists; both main specs, especially Cast D8 and UX D3/D4/D7.

**Sub-agents:** 2 implementation agents in parallel, then 1 senior reviewer/fixer.
- Agent A — Navigation contracts, max 5 deliverables:
  1. `NavigationSessionCoordinator` source/session/freshness contract.
  2. Immutable frame/store and process-rehydration contract.
  3. Independent `ClusterLaneAdapter` queue/executor/deadline/health contract.
  4. Independent `HudAdapter` queue/executor/deadline/health contract.
  5. Field/nullability/enum and static no-Cast-edge tests.
- Agent B — Cast foundations, max 5 deliverables:
  1. Versioned envelope/stable session/transaction models and AtomicFile store.
  2. Strict typed observation/gateway boundary and command allowlist.
  3. Pure planner and canonical 32-case manifest.
  4. Canonical `CastUiState` schema/hash/precedence projection.
  5. Rollout/action-owner registry, downgrade/pending-rollback models, and collision-failing APK collection guard in <code>app/build.gradle.kts</code>: require slice + exactSourceId, copy only the newly built requested variant to a previously non-existing unique destination, and fail if it exists. No `BUILD_AUTH_*` may be issued until static/configuration verification passes.
- Senior reviewer/fixer — read both producer and consumer ends; patch mismatches; run focused tests; repeat review until no actionable findings.

**Exit gate:**
- [ ] No shared live gateway/executor/cache/lifecycle/state primitive across tracks.
- [ ] Canonical schemas and exhaustive tests pass.
- [ ] AtomicFile corruption/process-kill/epoch tests pass.
- [ ] All changed source files ≤500 LOC; UI/controller target ≤300 LOC.
- [ ] APK collection no longer finalizes every assemble into a version-only path: it requires slice + exactSourceId, selects only the requested freshly built variant, writes a unique destination, and fails on collision. Existing APK bytes remain unchanged; all `BUILD_AUTH_*` stay blocked until this gate passes.
- [ ] Stage scope checklist is 100% complete.

**Handoff:** `docs/_handoff/two-track-stage-2-done.md`.

---

### Stage 3 — Navigation runtime + Navigation UX; Cast dry planner

**Reads:** Stage 2 handoff. Do not reimplement contracts.

**Sub-agents:** 2 implementation agents in parallel, then 1 senior reviewer/fixer.
- Agent A — Track A runtime/UI, max 5 deliverables:
  1. Wire authoritative source/session producer.
  2. Wire isolated lane and HUD adapters.
  3. Two-card Home and Navigation detail renderer.
  4. Whole-navigation Stop vs per-output toggle semantics.
  5. Recreation/restart/bidirectional block-throw-saturation tests.
- Agent B — Track B dry mode, max 5 deliverables:
  1. Typed AM/WM/display observation.
  2. Pure planner for all 32 cases.
  3. Dry command transcript generation and allowlist validation.
  4. Fault fixtures and requirement-to-case traceability.
  5. Verify all Cast mutation flags remain OFF.
- Senior reviewer/fixer — verify UI wiring, truthful `EMITTING` vs observed status, no cross-edge and direct user-facing flow; patch/retest loop.

**Exit gate:**
- [ ] Lane failure cannot stop/backpressure HUD; HUD failure cannot stop/backpressure lane.
- [ ] Either Track A output fault leaves Cast state/journal hash unchanged.
- [ ] Activity/View recreation dispatch count = 0.
- [ ] Dead Reckon card/setup/state/action remains absent; no DR runtime path changed.
- [ ] Cast planner executes no mutation.

**Continuous off-car transition:** write the concise Stage 3 evidence checkpoint, run focused tests, and continue directly to Stage 4. Do not build or wait for vehicle evidence here; Navigation vehicle cases are included in the final candidate script.

**Handoff:** `docs/_handoff/two-track-stage-3-done.md`.

---

### Stage 4 — Cast Stop/recovery dark mode

**Reads:** Stage 3 off-car handoff; Cast D3–D8; UX D4 consumer contract.

**Sub-agents:** 1 implementation agent, then 1 senior reviewer/fixer.
- Implementation agent, max 5 deliverables:
  1. Durable epoch, mutation lease, deadlines and unknown-effect fencing.
  2. Exactly-one compensation path and distinct full-operation retry.
  3. Legacy read-only projection with `LEGACY_SESSION_UNSAFE`.
  4. Context-independent disconnected-sink/orphan recovery with typed owner/session/two-sample/confirmation/one-attempt guards.
  5. Exhaustive canonical Cast state → UI/Bubble/action tests.
- Senior reviewer/fixer — inspect both state producer and every consumer; patch contract mismatches; rerun blocked-I/O, restart and exhaustive tests until clean.

**Exit gate:**
- [ ] No V2 `am display move-stack` in any direction.
- [ ] Legacy restart never synthesizes V2 baseline/compensation/action owner/interactive Stop.
- [ ] No `COMPENSATION_RETRY_ELIGIBLE` or second compensation mutation.
- [ ] PARKED/MOVING/UNKNOWN/stale context produces the same recovery action; owner/session/two-sample/confirmation/one-attempt predicates still fail closed.
- [ ] V2 runtime mutation flags remain OFF.

**Handoff:** `docs/_handoff/two-track-stage-4-done.md`.

---

### Stage 5 — Normal cold vertical slice + Cast renderer

**Reads:** Stage 4 handoff. Normal slice only.

**Sub-agents:** 2 implementation agents, then 1 senior reviewer/fixer.
- Agent A — normal cast engine slice, max 5 deliverables: fresh launch; target verification; Stop; rollback; process restart.
- Agent B — Cast control/Bubble slice, max 5 deliverables: canonical render; direct Stop iff AVAILABLE; 500ms acknowledgement; recreation zero mutation; accessibility/focus.
- Senior reviewer/fixer — exact boundary review and focused regression patch/retest loop.

**Off-car exit gate:** all normal-slice exact-source tests pass and evidence bundle is bound to current manifest.

**Continuous off-car transition:** close normal-slice tests and continue to Stage 6. Do not build an intermediate APK; append normal vehicle cases to the final script.

**Handoff:** `docs/_handoff/two-track-stage-5-done.md`.

---

### Stage 6 — CarPlay vertical slice

**Reads:** Stage 5 off-car handoff. CarPlay only.

**Sub-agents:** 1 implementation agent, then 1 senior reviewer/fixer.
- Implementation agent, max 5 deliverables: resume-only activation; connected-session protection; Stop/occlusion; rollback; restart continuity.
- Senior reviewer/fixer — verify no force-stop/clear-task/move-stack while protected; patch/retest loop.

**Continuous off-car transition:** verify protected-session invariants with synthetic/fault tests, append CarPlay vehicle cases, and continue to Stage 7 without a build.

**Handoff:** `docs/_handoff/two-track-stage-6-done.md`.

---

### Stage 7 — Android Auto vertical slice

**Reads:** Stage 6 off-car handoff. Android Auto only.

**Sub-agents:** 1 implementation agent, then 1 senior reviewer/fixer.
- Implementation agent, max 5 deliverables: behavior/component classification; resume-only activation; redirect fail-closed; Stop/rollback; restart continuity.
- Senior reviewer/fixer — verify no AA placement exception or hidden unsafe primitive; patch/retest loop.

**Continuous off-car transition:** verify Android Auto fail-closed contracts, append AA vehicle cases, and continue to Stage 8 without a build.

**Handoff:** `docs/_handoff/two-track-stage-7-done.md`.

---

### Stage 8 — Warm matrix + app manager + deterministic Bubble

**Reads:** Stage 7 off-car handoff.

**Sub-agents:** 2 implementation agents, then 1 senior reviewer/fixer.
- Agent A — warm engine matrix, max 5 deliverables: same target; normal↔normal; normal↔protected; protected↔protected; one-residue preflight/degraded rules.
- Agent B — app/Bubble UX, max 5 deliverables: favorites/details; protected labels; deterministic menu; canonical Stop status; TalkBack/focus/Back/outside behavior.
- Senior reviewer/fixer — pairwise engine↔UI shape review; patch/retest loop.

**Continuous off-car transition:** pass the warm/pairwise/residue off-car matrix, append vehicle cases, and continue to Stage 9.

**Handoff:** `docs/_handoff/two-track-stage-8-done.md`.

---

### Stage 9 — Geometry, lifecycle, adjustment and fault isolation

**Reads:** Stage 8 off-car handoff.

**Sub-agents:** 2 implementation agents, then 1 senior reviewer/fixer.
- Agent A — Cast geometry/lifecycle, max 5 deliverables: target-bound geometry; no global geometry with residue; process death; sleep/wake; physical-reboot rehydration.
- Agent B — adjustment/isolation, max 5 deliverables: draft vs accepted; stale target/epoch rejection; failure restore/recovery; D8 fault matrix; partial Diagnostics.
- Senior reviewer/fixer — verify producer↔consumer boundaries and bidirectional fault guarantees; patch/retest loop.

**Binding interaction-context rule:** no PARKED provider is required and PARKED/MOVING/UNKNOWN cannot enable, disable or alter setup, adjustment, repair or recovery controls; their ordinary target/session/transaction/confirmation predicates remain binding.

**Continuous off-car transition:** complete geometry/lifecycle/fault tests and append the physical power-button reboot procedure to the final vehicle script; continue to Stage 10 without a build.

**Handoff:** `docs/_handoff/two-track-stage-9-done.md`.

---

### Stage 10 — Support, migration, accessibility, docs + DR prerequisite

**Reads:** Stage 9 off-car handoff; Stage-1 docs inventory; DR revalidation spec.

**Sub-agents:** 2 implementation agents maximum, then 1 senior reviewer/fixer.
- Agent A — support/migration, max 5 deliverables: bounded partial Diagnostics; pipeline-scoped repair boundary; effective UI/action-owner rollback; N/N−1 or no-downgrade fence; preserve legacy rollback path.
- Agent B — accessibility/docs, max 5 deliverables: adaptive resources; accessibility/focus; exact-build behavior manifest; README/guide/screenshots; historical labeling/APK links.
- Senior reviewer/fixer — verify docs match exact enabled flags/build and legacy code is not physically deleted; patch/retest loop.

**DR retirement gate:** complete the deferred independent DR closure review first, materialize exact Requirements/Design/Tasks decisions and a path-level DR removal allowlist, then remove/quiesce the DR and mock-provider product runtime off-car. Run manifest/provider/preference/static-boundary regression tests and bind results to the final exact source. Do not claim vehicle success until Stage 11, and do not physically delete rollback-readable legacy Cast code.

**Handoff:** `docs/_handoff/two-track-stage-10-done.md`. It must materialize a UTF-8-byte-sorted exact Stage 11 mutation-path list equal to `(owner-approved autonomous allowlist ∩ paths actually changed)`, record the canonical newline-delimited list SHA-256, and bind Stage 11 patches to that list. The owner amendment preauthorizes this generated exact list; narrative union wording is not an allowlist.

---

### Stage 11 — Final off-car closure, one test build, vehicle validation and release decision

**Reads:** every stage handoff, both main specs, final plan, evidence ledgers, complete changed-file list, and the Stage 10 materialized Stage 11 path list/hash. The owner amendment preauthorizes patches only within that generated exact list.

**Execution:** direct primary-agent review in bounded boundary batches under the anti-stall rule; no broad blocking sub-agent DAG. A final independent report-only check may be used only if it satisfies the bounded-call limits.

**Senior reviewer/fixer requirements:**
1. List every TP1–TP18 requirement, 32 Cast case, UX1–UX15 and U1–U24 deliverable.
2. Verify code exists, is wired, has meaningful tests and user-facing flow; no stubs/placeholders.
3. Read both ends of every boundary field-by-field: names, types, nullable, enums and wrappers; trace at least one real value end-to-end.
4. Run Context7 freshness check for every major changed dependency/API; patch deprecated usage within authorization.
5. Review correctness, deadlines, cancellation, recovery, fault domains, accessibility, rollback and dirty-tree scope.
6. Patch findings `[P0]`–`[P3]`, rerun focused/full tests, rerun review until no accepted actionable findings.
7. Produce scope, tech freshness, boundary shape, tests and evidence reports.

**Off-car closure and build boundary:**
1. Run focused and full JVM/static/resource validation after the final patch.
2. Verify scope completeness, exact-two-runtime boundary, DR retirement, sensitive-data scan, and exact-source provenance.
3. Invoke exactly one authorized release-variant build through `collectAuthorizedApk` with the generated final exactSourceId and a stable `vehicle-test` slice.
4. Record APK path, SHA-256, signing certificate, version, toolchain and effective flags.
5. Generate non-destructive preflight, install, Navigation, normal Cast, CarPlay, Android Auto, warm matrix, geometry, lifecycle, failure/recovery and physical-reboot scripts/checklists bound to that exact APK SHA.
6. Stop at `WAITING_FOR_VEHICLE_TEST`; do not install or call ADB/dadb.

**Final exit gate after vehicle execution:**
- [ ] 0 unresolved accepted P0/P1 and 100% approved initial-release scope.
- [ ] All off-car tests pass after final patches.
- [ ] Exact-source provenance matches every changed path.
- [ ] Dead Reckon retirement is complete and exactly two runtime tracks remain.
- [ ] Exactly one provenance-bound test APK and complete vehicle scripts exist.
- [ ] Required exact-build on-car matrix PASS with owner sign-off.
- [ ] No physical legacy Cast deletion.

**Commit/push boundary:**
- Without `COMMIT_AUTH`, stop at `WAITING_FOR_COMMIT_AUTH` and leave changes uncommitted.
- With `COMMIT_AUTH`, stage only explicit approved files, run mandatory full sensitive-data scan, and stop on `[BLOCK]`; ask user on `[WARN]`.
- Push requires separate `PUSH_AUTH` plus push-history security scan.
- Merge requires separate `MERGE_AUTH` and final car PASS. Never push directly to main/master unless explicitly ordered.

**Handoff:** `docs/_handoff/two-track-stage-11-done.md`.

---

### Stage 12 — Deferred legacy deletion release · do not execute here

This is a planning marker only. Initial V2 never authorizes physical deletion. A future cleanup spec must first define and approve measurable soak duration, minimum successful-session coverage, supported vehicle/ROM/profile scope, zero unresolved P0/P1, rollback-readable proof, exact evidence-bundle identity and owner sign-off; only after every threshold passes may it start fresh approvals, baseline, implementation authorization, senior review, security scan, unique build and car validation. Do not delete legacy Cast paths in Stages 0–11.

## ORCHESTRATOR INSTRUCTIONS — KIRO CLI

1. Read only the current wave's relevant spec sections and previous concise checkpoint; do not repeatedly reload every document.
2. Execute directly in visible 3–5-file batches. Follow `.kiro/steering/execution-reliability.md`; no large blocking `subagent` DAG.
3. The owner amendment authorizes continuous Waves 2–10. Do not pause for intermediate implementation/build/install/car tokens.
4. Stay inside the explicit union allowlist. Before any DR/mock-provider mutation, finish the DR closure review and record an exact path list.
5. Reuse recorded Context7 results when technology/API choices are unchanged. Query official docs only for a new or changed technology decision.
6. Run focused tests after each logical patch area; fix failures immediately. Run one full off-car suite after all implementation waves.
7. Maintain one concise autonomous progress handoff with changed paths, contracts, tests, findings and remaining scope; wave-specific handoffs may be generated from it without pausing.
8. Perform direct senior boundary/scope review in small batches, patch and retest until no actionable P0–P3 remains.
9. Run the mandatory sensitive-data scan before producing the candidate/public artifact and before any future commit/push.
10. Compute the final exact-source manifest, then invoke exactly one collision-safe release-variant test build. Never build intermediate APKs.
11. Generate scripts/checklists bound to the exact APK SHA and stop at `WAITING_FOR_VEHICLE_TEST`. Do not install or run ADB/dadb while the car is unavailable.
12. Never auto-commit, push or merge. Those remain separately authorized actions after vehicle evidence.

## ERROR RECOVERY

- Cancellation/partial output: inspect completed files/tests, preserve valid side effects, and continue directly with a smaller batch; never repeat the same blocking call.
- Test failure: fix within the active union allowlist and rerun the focused area before continuing.
- Context7 unavailable: do not introduce/change the affected technology. Record `TECH_VALIDATION_BLOCKED` and stop the stage.
- Dirty-tree mismatch outside allowlist: stop at `BASELINE_DIVERGED`; preserve all files and ask the owner to re-approve the manifest.
- Car unavailable: continue all authorized off-car implementation, review, validation, exact-source closure, the one test build and script generation; stop only at `WAITING_FOR_VEHICLE_TEST`. Never substitute historical APK/car evidence.
- Security `[BLOCK]`: do not commit/push; report path/line/category/remediation. `[WARN]` requires user decision.
- Required design change outside approved specs: stop at `SCOPE_CHANGE_REQUIRES_REAPPROVAL`; patch documents first, then obtain affected approvals again.

## FINAL EXIT CRITERIA

- [ ] Exactly two independent runtime tracks; no cross-control/live-resource edge.
- [ ] Navigation authoritative producer and isolated lane/HUD outputs are fully wired and tested.
- [ ] Cast durable V2 schema/store/planner/executor/recovery/UI contracts are complete.
- [ ] Legacy active Stop is fail-closed; one compensation attempt only.
- [ ] Interaction context cannot alter actions; destructive recovery fails closed on owner/session/two-sample/confirmation/one-attempt evidence.
- [ ] Canonical rollout registry, action ownership, pending rollback and downgrade behavior pass.
- [ ] Diagnostics is read-only, bounded and partial-capable.
- [ ] Dead Reckon target UX remains absent; separate retirement prerequisite and deferred review are complete before release claims.
- [ ] Initial release preserves rollback-readable legacy Cast code.
- [ ] All TP1–TP18, Cast 32 cases, UX1–UX15 and U1–U24 have honest traceability and required evidence.
- [ ] Exact-source, exact-build off-car and exact-build on-car provenance are distinct and complete.
- [ ] Senior review loop exits with 0 accepted actionable findings and all tests passing.
- [ ] Public docs/screenshots/APK links match the exact enabled build and flags.
- [ ] Security scan is clean before any authorized commit/push.
- [ ] No main merge before final exact-build on-car PASS and explicit `MERGE_AUTH`.
