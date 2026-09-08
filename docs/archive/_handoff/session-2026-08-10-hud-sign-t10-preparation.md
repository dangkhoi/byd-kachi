# HUD / navigation / speed-sign — T10 preparation handoff

**Snapshot date:** 2026-08-10
**Owner:** Đăng Khôi · `dangkhoi`
**Vehicle access:** **NO-GO**
**T10 implementation:** **NOT STARTED / NOT APPROVED**
**First Session N:** **NOT AUTHORIZED / NOT RUN**
**Mutation:** **FORBIDDEN — zero eligible rows**
**T11 production:** **FORBIDDEN / NOT STARTED**

> [!CAUTION]
> This repository is ready only for the next **off-car T10 boundary-reconciliation and implementation-preparation phase**, and only after explicit spec approval. Nothing in this handoff authorizes a connection to a vehicle, transport discovery, APK installation, Session N, a write, clear, restore, reboot, or any other vehicle-state operation.

## 1. TL;DR

- The parent T0–T9 off-car work and the candidate-expansion work exist. Candidate expansion is certified and passed its canonical verifier again on 2026-08-10.
- A fresh invocation of the full offline Gradle matrix succeeded. Gradle reused up-to-date assemble/analysis outputs where valid; parent/runtime unit tests were then forced separately, while planner tests were executed by the canonical expansion verifier. This proves local code/build health only; it is **not** vehicle authorization and does not create an authorized T10 APK.
- A fresh run of the parent verifier passed O1–O3, then correctly stopped at **O24** because the parent changed-path allowlist does not include the additive candidate-expansion/T10 planning tree. The parent boundary must be revised and resealed before T10 implementation. Do not delete or hide the certified expansion work to make O24 green.
- The current pack is intentionally inert: `INERT_IDENTITY_BLOCKED`, blocker `BLOCKER-MISSING-AUTHORIZED-T10-HANDOFF`, runtime exact identity `null`, 7 `READ_ONLY` rows, 4 `MILESTONE` rows, 0 `MUTATION` rows.
- There is no `READY_FOR_FIELD` candidate, no exact T10 handoff, no T10 vehicle-test candidate APK, no Session N authorization, and no D-H0/M1/M2/M3/M4 result.
- The exact old release APK `apk/ClusterNav-1.04-v104-527589f2d16a-release.apk` is stale and invalid. **Do not install it.** It predates the current first-launch fixes and candidate-expansion/T10 architecture. A historical debug APK may contain first-launch fixes, but it is also unauthorized and invalid for T10.
- The only approval phrase that can open T10 implementation is:

  `Approve spec seal-hud-sign-vehicle-test-t10`

  That phrase permits only T10 implementation and off-car verification. It does **not** authorize vehicle access.

## 2. Non-negotiable current decision: NO-GO

Do not perform any of the following from the current repository state:

- connect a transport to a vehicle or resolve a live target;
- install or launch any repository APK on a vehicle;
- treat a generic local `vehicleTest` build as a T10 candidate;
- execute Session N or any historical vehicle recipe;
- dispatch a mutation, clear, inverse, restore, raw selector, or free-form operation;
- use an old debug action, raw-ID recipe, MASS probe, or direct shell recipe;
- begin T11 production wiring;
- commit, push, release, reset, clean, or overwrite the existing dirty work.

The blockers are independent and cumulative. Passing an off-car test, obtaining the T10 implementation approval, or having a physically available vehicle closes none of the other gates automatically.

## 3. Repository identity and dirty-tree preservation

| Field | Snapshot |
|---|---|
| Branch | `main` |
| Upstream | `origin/main` |
| HEAD | `d85b9f2e13c3081287005bee0e90bb482bd6d272` |
| HEAD date | 2026-08-06 13:44:41 +0700 |
| HEAD subject | `feat(nav,ui): v1.04 — HUD turn-arrow fix...` |
| Tracked modifications | 20 files |
| Tracked diff | 589 insertions, 503 deletions |
| Staged files | 0 |
| Untracked files before this handoff | 93 |
| Commit/push after this work | None |

This is an intentionally dirty working tree containing first-launch fixes, parent off-car work, candidate expansion, specs, and generated artifacts. A future operator must:

1. inspect `git status` before editing;
2. preserve all current tracked and untracked work;
3. never use reset/clean/stash as a shortcut to satisfy a verifier;
4. never assume `HEAD` represents the current candidate-expansion or first-launch-fixed source;
5. stage only explicit files if a later, separately requested commit is prepared.

Creating this handoff adds one more untracked file; the expected post-write count is therefore 94 unless another process changes the tree.

## 4. Authoritative document hierarchy

Read these in order. A lower item cannot override a safety/authorization restriction in a higher active item.

1. [Parent T0–T9 off-car spec](../specs/seal-nav-hud-speed-sign-offcar.html) — sealed off-car architecture, evidence, current/future path fence, and T10/T11 exclusion.
2. [Candidate-expansion spec](../specs/seal-hud-sign-candidate-expansion.html) — certified additive registry/coverage/planning work.
3. [T10 vehicle-test spec](../specs/seal-hud-sign-vehicle-test-t10.html) — reviewed plan only; implementation approval is absent.
4. [Candidate registry](../diagnostics/hud-sign-re/expansion/candidate-registry.json), [corpus coverage](../diagnostics/hud-sign-re/expansion/corpus-coverage.json), [session plan](../diagnostics/hud-sign-re/expansion/vehicle-session-plan.json), and [pack manifest](../diagnostics/hud-sign-re/expansion/pack-manifest.json) — current machine-readable pack.
5. Historical file `docs/_handoff/session-2026-08-06-pm2-firstlaunch-fixes-and-hud-enable.md` — evidence only. It contains private target data and old operational recipes; intentionally not linked here and must not be used as a runbook.
6. [Older windshield-HUD spec](../specs/windshield-hud-enable.html) — context only.

The repository README and older files under `docs/review/`, `docs/diagnostics/`, and `docs/reference/` may describe other historical candidates or stages. They do not authorize this HUD/sign T10 track. In particular, the existing `vehicle-candidate.json` is not a T10 candidate.

## 5. What is complete off-car

### 5.1 Parent T0–T9 scope

The parent spec records the following certified state for the available corpus:

- achievable gates O1–O27 passed at certification time;
- `vehicle-contracts`: 3 tests;
- `core`: 669 tests;
- `app`: 320 tests;
- parent contract total: 992/992;
- debug and vehicle-test compilation passed;
- main/release had no active TEST/MASS probe surface;
- no T10/T11 runtime path was implemented;
- `FEATURE_DONE=false`;
- corpus completeness remained `NOT_EXHAUSTIVE`.

This historical 992/992 certification remains recorded in the spec, but the complete parent verifier is **not freshly green on 2026-08-10** because O24 now exposes a cross-spec path-fence mismatch; see §12.

### 5.2 Candidate expansion

The approved `seal-hud-sign-candidate-expansion` scope is implemented and certified. Senior review found and fixed scheduler/prune-closure, mutable-alias, optional-schema, and formatting defects. Final recorded evidence includes:

- `GATE-X-O1`–`O10`, `O12`: PASS;
- canonical O11: PASS;
- Python: 15/15;
- Gradle: 98/98 across 19 suites;
- 0 failures, errors, or skips;
- 12 generated outputs and 11 manifest entries;
- LSP diagnostics clean;
- reviewed Kotlin files at or below 500 LOC;
- independent post-patch reviewer: CLEAN, 0 P0–P3;
- REQ-X1…X18: 18/18;
- TASK-X0…X5: 6/6;
- security scan: 29 files, 840,743 bytes, 0 BLOCK, 0 WARN;
- reviewer `candidate_expansion_senior_audit_20260808_final`;
- projection `435147484b7d9b382f5b8cb63409ab67a636dbff1300342b327dd3b07aff99a9`.

The canonical candidate-expansion verifier was rerun on 2026-08-10 and passed again: 15 Python tests, three successful Gradle verification phases, 12 outputs, deterministic/offline checks, semantic privacy scan, and byte-diff checks all green.

### 5.3 Technical-health invocation on 2026-08-10

The exact offline Gradle task matrix embedded in the parent verifier was invoked separately after O24 stopped the full verifier. The invocation succeeded across 163 actionable tasks: 3 executed and 160 were validly reused as `UP-TO-DATE`. The task graph covered:

- `vehicle-contracts`, `offcar-planner`, `core`, both app unit-test variants, and `car-integration` tests;
- Debug, current generic vehicleTest, and Release assembly;
- Debug, current generic vehicleTest, and Release lint.

This means the matrix was freshly invoked and green; it does not claim every assemble/lint subtask was freshly executed. Parent/runtime unit-test tasks were subsequently forced with `--rerun-tasks` (64 tasks executed, PASS in 3m04s). Planner tests had already been executed repeatedly by the canonical candidate-expansion verifier.

The current XML total is 1,421 test executions, 0 failures, 0 errors, 0 skips:

| Scope | Suites | Tests |
|---|---:|---:|
| `vehicle-contracts` | 1 | 3 |
| `offcar-planner` | 19 | 98 |
| `core` | 80 | 669 |
| `app` Debug + current vehicleTest | 84 | 640 |
| `car-integration` | 2 | 11 |
| **Total** | **186** | **1,421** |

The app total counts the same 320-test contract in both Debug and vehicleTest variants. This is why it is larger than the historical 992 parent scope. Current XML after the forced test run records the counts above.

Non-fatal compiler warnings remain in pre-existing app/core/car-integration code, including deprecations and future Kotlin-language compatibility warnings. They did not fail the matrix and are not evidence of T10 implementation. Do not silently fold unrelated warning cleanup into the T10 fence.

### 5.4 Release/probe isolation checked

A focused scan of `app/src/main` found no current `TEST_`, MASS, raw-ID, HUD-sign vehicle-test, or equivalent probe marker in the main manifest/receiver surfaces checked. The first-launch guards remain in the dirty source (`startForegroundOnce`, one-shot overlay request, and initialized-component shutdown guards). These facts do not turn the dirty source into an exact vehicle candidate.

## 6. What was historically observed on-car

The following observations come from the 2026-08-06 handoff and are retained only as evidence context:

- Cluster navigation through the canonical `AUTONAVI_STANDARD_BROADCAST_SEND` path worked.
- Direct HAL writes that returned `rc=0` did not prove visible cluster or HUD behavior.
- The old `writeSpeedLimit` path used an invalid or absent ADAS fallback.
- Speed-reminder setting writes were accepted, but acceptance did not prove speed-sign rendering.
- Windshield HUD maneuver/distance remained unverified.
- Cluster and windshield speed-limit signs remained unverified.
- First-launch fixes were exercised on-car in a debug build, but those fixes remain uncommitted in the current dirty tree.
- The historical vehicle state was restored as documented at the end of that session.

Do not upgrade any historical observation into a T10 PASS. In particular:

- cluster navigation is not proof of M1 HUD navigation;
- M1 is not proof of M2 HUD road name;
- a return code or accepted setting is not proof of M3 cluster sign;
- M3 cluster sign is not proof of M4 HUD sign.

## 7. Stale, invalid, and quarantined material

### 7.1 Exact old v1.04 release APK — invalid, do not install

Historical release artifact:

- path: `apk/ClusterNav-1.04-v104-527589f2d16a-release.apk`;
- APK SHA-256: `b9a0259e7174a694b7bd4fd8984199ac91091e5e52e8de9c73ae6c67542bb598`;
- source ID: `527589f2d16ac04400e811d89da31ae5b21f693058b5713cb8dd90eea365380c`;
- source manifest: [v1.04-exact-source.json](v1.04-exact-source.json).

This exact release artifact is invalid for all future vehicle work because:

1. it was produced from clean HEAD `d85b9f2` with an empty tracked diff;
2. it does not contain the current dirty-tree first-launch fixes;
3. it is known to crash on the historical clean-install first-launch overlay path;
4. it predates candidate expansion and the T10 closed-world architecture;
5. it has no T10 exact handoff, registry/pack binding, signer review, or Session N authorization.

The separate historical debug APK used to verify first-launch fixes may contain those fixes, but it is not an exact T10 artifact and has no current pack/handoff/session authorization. It must not be installed for T10 either.

### 7.2 Generic build outputs are not T10 artifacts

The fresh Gradle matrix can generate local Debug/current-vehicleTest/Release APK outputs. None is an authorized T10 vehicle-test candidate: the T10 receiver/activity, DADB transport, generated matrix runner, exact T10 handoff, and signer/identity binding are absent. Build success must never be interpreted as install permission.

### 7.3 Historical recipes are quarantined

The 2026-08-06 handoff and older on-car plans contain private target information, exported debug actions, raw IDs, MASS probing, and direct device-shell recipes. They are evidence of what happened, not executable next steps. Do not copy, adapt, parameterize, or replay them. T10 must use only fixed catalog operations generated from a reviewed frozen pack.

## 8. Current candidate registry and corpus truth

### 8.1 Registry states

Registry revision: **3**.

| Candidate revision | State | Consequence |
|---|---|---|
| `CAND-H-008-PROPERTY-CONFIG-METADATA@1` | `DISCOVERED` | Historical revision only |
| `CAND-H-008-PROPERTY-CONFIG-METADATA@2` | `SOURCE_BACKED` | Historical revision only |
| `CAND-H-008-PROPERTY-CONFIG-METADATA@3` | `READ_ONLY_READY` | Only candidate-bound read row allowed |
| `CAND-S-011-SOURCE-DOMAIN@1` | `MUTATION_REVIEW` | Not eligible to mutate |
| `CAND-S-012-REJECTED-SHAPE@1` | `REJECTED` | Permanently absent from execution rows |

There is no `READY_FOR_FIELD` candidate. `READ_ONLY_READY` and `MUTATION_REVIEW` are not substitutes for `READY_FOR_FIELD`.

### 8.2 Corpus coverage

- C01–C05: `AVAILABLE`;
- C06–C12: `UNAVAILABLE`;
- expansion verdict: `NOT_EXHAUSTIVE`.

`NOT_EXHAUSTIVE` is mandatory and must remain visible. It does not invalidate evidence derived from C01–C05, but it forbids claims that missing vendor HAL, partition, provider library, property registry, service/SELinux, ISA provider, or QML/RCC corpora have been exhausted.

## 9. Current machine-readable pack identity

| Identity | Value |
|---|---|
| Registry revision | `3` |
| Pack SHA-256 | `9b5fd9975555daadf4e90ec9e2ace23d7358cfaf6e91321a394e0b13e68e85e7` |
| Full `vehicle-session-plan.json` SHA-256 | `d21e368b10d127255d3e6285330fc1dd16f1562aa3d60d25ac4f1dd7cf8ee357` |
| Manifest `selfSha256` | `4322150d5db29b488616e9eedbe675319b2edf259b55a132395c435ca90f976f` |
| Full registry SHA-256 | `211aaff556d7d1ed2fcaca32f6955f9f45d18f3dc0fe4482ce79bb93a3a9e0b7` |
| Full coverage SHA-256 | `d7b99563ee4710c121620904301d50a1f5a0cd479683c6c453715617bde9def4` |
| Identity state | `INERT_IDENTITY_BLOCKED` |
| Identity blocker | `BLOCKER-MISSING-AUTHORIZED-T10-HANDOFF` |
| Runtime exact identity | `null` |

Allowed candidate revision:

- `CAND-H-008-PROPERTY-CONFIG-METADATA@3`

Allowed probe IDs:

- `PROBE-LIST-PACKAGE-METADATA`
- `PROBE-LIST-PROPERTY-CONFIGS`
- `PROBE-LIST-SERVICE-METADATA`
- `PROBE-READ-PACKAGE-METADATA`
- `PROBE-READ-PROPERTY-CONFIG`
- `PROBE-READ-SERVICE-METADATA`

Allowed mutation candidate revisions: `[]`.

## 10. Frozen first-session rows

The reviewed plan has exactly 11 rows: 7 `READ_ONLY`, 4 `MILESTONE`, 0 `MUTATION`. These rows describe the future first authorized T10 Session N; they are not executable now.

| Order | Row ID | Kind | Purpose |
|---:|---|---|---|
| 1 | `ROW-0001-DISCOVERY-LIST-PACKAGE-METADATA` | `READ_ONLY` | List package metadata |
| 2 | `ROW-0002-DISCOVERY-LIST-PROPERTY-CONFIGS` | `READ_ONLY` | List property configurations |
| 3 | `ROW-0003-DISCOVERY-LIST-SERVICE-METADATA` | `READ_ONLY` | List service metadata |
| 4 | `ROW-0004-DISCOVERY-READ-PACKAGE-METADATA` | `READ_ONLY` | Read package metadata |
| 5 | `ROW-0005-DISCOVERY-READ-PROPERTY-CONFIG` | `READ_ONLY` | Read one frozen property configuration |
| 6 | `ROW-0006-DISCOVERY-READ-SERVICE-METADATA` | `READ_ONLY` | Read service metadata |
| 7 | `ROW-0007-READ-H-008-PROPERTY-CONFIG-METADATA` | `READ_ONLY` | H8 candidate-bound metadata read |
| 8 | `ROW-0008-D-M1-SURFACE` | `MILESTONE` | Observe HUD navigation independently |
| 9 | `ROW-0009-D-M2-SURFACE` | `MILESTONE` | Observe HUD road-name surface independently |
| 10 | `ROW-0010-D-M3-SURFACE` | `MILESTONE` | Observe cluster speed-sign surface independently |
| 11 | `ROW-0011-D-M4-SURFACE` | `MILESTONE` | Observe HUD speed-sign surface independently |

Session N invariants:

- zero mutation dispatch;
- zero clear dispatch;
- zero restore dispatch;
- frozen row order and bytes;
- every new fact is `DISCOVERY_ONLY`;
- no same-session candidate promotion;
- milestone observations are independent and cannot be inferred from sibling surfaces.

## 11. Readiness matrix

| Layer | Current state | Vehicle consequence |
|---|---|---|
| T0–T9 parent off-car | Implemented/certified for available corpus | Does not authorize vehicle work |
| Candidate expansion | Implemented/certified; fresh verifier PASS | Pack preparation only |
| Corpus completeness | `NOT_EXHAUSTIVE` | No exhaustive capability claim |
| Parent fresh verifier | **FAIL at O24 after O1–O3 PASS** | Boundary must be revised/resealed |
| T10 plan | Reviewed plan only | No implementation permission |
| T10 implementation | Not started | No runnable T10 surface |
| T10-specific vehicleTest APK | Absent | Nothing may be installed |
| Exact T10 handoff | Absent | Runtime identity remains inert |
| Runtime exact identity | Unresolved / `null` | No transport event |
| Session N authorization | Absent | No connection/session |
| Session N | `NOT_RUN` | No field evidence |
| Mutation candidates | 0 | All writes forbidden |
| D-H0 | `NOT_RUN`; default no consent | Physical diagnostic omitted |
| D-M1 | `NOT_RUN` | HUD navigation unproven |
| D-M2 | `NOT_RUN` | HUD road unproven |
| D-M3 | `NOT_RUN` | Cluster speed sign unproven |
| D-M4 | `NOT_RUN` | HUD speed sign unproven |
| T11 production | Forbidden/not started | No release wiring |
| **Vehicle readiness** | **NO-GO** | **Do not connect/install/run** |

## 12. Immediate blocker: parent O24 path-fence mismatch

The parent verifier is strictly off-car and contains no vehicle connection or installation step. On 2026-08-10 it produced:

- `PASS O1 repo/corpus manifest`;
- `PASS O2 tool manifest; TOOL_VERDICT=PASS_PINNED`;
- `PASS O3 decode determinism`;
- then `NEEDS_CHANGES` at O24.

O24 reported 30 additive paths not present in the parent `current` allowlist: 12 expansion outputs, 2 active specs, 4 planner source files, 1 expansion schema, 8 regression-test files, and 3 expansion scripts/verifiers. This is a policy/contract mismatch, not evidence that the canonical expansion verifier failed.

Required resolution after, and only after, T10 implementation approval:

1. treat T10-Q4 as open and enumerate every required test/verifier path;
2. revise and review the parent current/future boundary explicitly;
3. preserve the certified expansion artifacts and history;
4. update the verifier to the reviewed boundary rather than bypassing it;
5. rerun both the candidate-expansion verifier and the complete parent verifier;
6. do not start T10 implementation until the resealed boundary is green.

Forbidden “fixes” include deleting expansion files, resetting the tree, moving files outside the scan, weakening O24, or silently adding paths without a reviewed parent-boundary revision.

## 13. What is still missing for T10

Of the 15 parent-enumerated T10 future paths, only the pre-existing `CarExecCatalog.kt` currently exists. Its existence is not T10 approval. The other T10-specific implementation and output artifacts are absent, including:

- vehicleTest manifest and fixed receiver/activity;
- DADB vehicle transport adapter;
- generated/fixed matrix runner;
- navigation, HUD, and speed-sign catalog splits;
- exact `hud-sign-vehicle-test-candidate.json`;
- all five D-result metadata files.

Additional gaps:

- exact mandatory T10 test/verifier paths are unresolved (T10-Q4);
- no exact T10 source/diff identity;
- no exact APK hash or reviewed signer identity;
- no proven sender/permission mode on the target profile;
- no exact target/profile/transport tuple;
- no physical-session window, operator, observer, battery plan, evidence-retention decision, or approved recovery procedure;
- no Session N ledger or results;
- no N→N+1 review;
- no `READY_FOR_FIELD` mutation row.

## 14. Authorization ladder — never collapse these gates

### Layer 1 — T10 spec approval

Exact phrase:

`Approve spec seal-hud-sign-vehicle-test-t10`

Current state: **not received**. The user previously approved only `seal-hud-sign-candidate-expansion`.

Effect if received: permits T10 implementation, parent-boundary reconciliation, and off-car verification only. It does not authorize a live target, installation, Session N, or mutation.

### Layer 2 — exact implementation/artifact review

Must review and bind all of the following:

- T10 code and fixed operation catalogs;
- focused tests, full affected tests, lint, release isolation, security, and privacy;
- exact APK bytes and signer;
- exact source, dirty diff, registry, pack, candidate set, and profile assumptions;
- new exact `hud-sign-vehicle-test-candidate.json`;
- independent senior-review result with no actionable findings.

Passing this layer still does not authorize a vehicle session.

### Layer 3 — explicit Session N READ_ONLY authorization

A separate authorization must name the exact handoff, pack SHA, APK SHA, signer, registry revision, target profile, transport, vehicle window, operator, observer, and recovery procedure. It may permit only the frozen 7 `READ_ONLY` + 4 `MILESTONE` rows. Mutation/clear/restore dispatch must remain exactly zero.

### Layer 4 — offline N→N+1 review

Close and leave the vehicle session first. Every discovery remains `DISCOVERY_ONLY`. Sanitize and review evidence offline, disposition each discovery exactly once, regenerate N+1, and independently review all registry/pack/row changes. No same-session promotion is allowed.

### Layer 5 — separate mutation authorization

Possible only if the latest reviewed N+1 pack contains exact `READY_FOR_FIELD` rows. Authorization must freeze candidate revisions and row order. Each row requires one mutation dimension/live rollback frame, typed prior, armed inverse, immediate read-back, approved clear, restore, and terminal evidence. The current pack has zero eligible rows, so this layer is presently impossible.

### Layer 6 — T11 production

Requires a separate future spec and explicit authorization after field proof. A T10 D-result, including PASS, never authorizes T11 by itself.

## 15. Sealed T10 path boundary

The parent currently enumerates these 15 T10 future paths. This list is a fence, not permission to edit before approval:

1. `app/src/vehicleTest/AndroidManifest.xml`
2. `app/src/vehicleTest/java/com/byd/clusternav/HudSignProbeReceiver.kt`
3. `app/src/vehicleTest/java/com/byd/clusternav/HudSignProbeActivity.kt`
4. `car-integration/src/main/kotlin/com/byd/clusternav/vehicleprobe/DadbVehicleTransport.kt`
5. `scripts/vehicle/run-seal-hud-sign-matrix.sh`
6. `core/src/main/kotlin/com/byd/clusternav/carexec/CarExecCatalog.kt`
7. `core/src/main/kotlin/com/byd/clusternav/carexec/CarExecNavigationCatalog.kt`
8. `core/src/main/kotlin/com/byd/clusternav/carexec/CarExecHudCatalog.kt`
9. `core/src/main/kotlin/com/byd/clusternav/carexec/CarExecSpeedSignCatalog.kt`
10. `docs/_handoff/hud-sign-vehicle-test-candidate.json`
11. `docs/diagnostics/hud-sign-re/vehicle/d-h0-hud-physical-temp-result.json`
12. `docs/diagnostics/hud-sign-re/vehicle/d-m1-nav-hud-result.json`
13. `docs/diagnostics/hud-sign-re/vehicle/d-m2-hud-road-result.json`
14. `docs/diagnostics/hud-sign-re/vehicle/d-m3-cluster-sign-result.json`
15. `docs/diagnostics/hud-sign-re/vehicle/d-m4-hud-sign-result.json`

The T10 spec explicitly notes that mandatory test/verifier files may fall outside this list. Before any such edit, revise and reseal the parent boundary. Do not silently add a test path, implementation path, generated output, or helper.

## 16. Minimum safe route to the first vehicle connection

No step may be skipped or merged with a later authorization layer.

1. **Keep vehicle access closed.** Review this handoff and the three active specs.
2. **Obtain exact T10 implementation approval.** Without the exact phrase in §14, make no T10 code or boundary change.
3. **Execute T10-P0 off-car only.** Resolve Q4; revise/reseal the parent boundary; rerun parent and expansion verifiers; then implement fixed catalogs, vehicleTest isolation, strict adapter/transport, generated runner, and tests within the reviewed fence.
4. **Complete off-car safety review.** Verify no main/release probe path, no raw/free-form runtime input, no arbitrary IDs/values, deterministic plan/ledger behavior, release isolation, privacy, and security.
5. **Produce exact artifact identity.** Generate the T10-specific APK and exact handoff; bind source/diff/APK/signer/registry/pack/candidates/profile assumptions.
6. **Resolve physical and operational questions.** Q1–Q7 at minimum must have explicit answers for Session N; no guessed battery threshold or target tuple.
7. **Independent review.** Review both ends of every identity, operation, ledger, and result boundary; retain zero actionable P0–P3 findings.
8. **Obtain separate Session N READ_ONLY authorization.** It must match the exact bytes and physical window.
9. **Only then permit the first connection.** Any identity drift or prerequisite failure returns immediately to NO-GO.

The first connection is not part of implementation approval and must not be bundled into a “quick smoke test.”

## 17. Session N policy-level procedure

This section intentionally provides no device command or runner invocation.

### 17.1 Entry

- Compare live target/profile/transport and exact handoff fields with the approved identity; require deep equality.
- Confirm the physical checklist in §18 with both operator and observer.
- Confirm local ignored evidence storage and the approved retention plan.
- Instantiate the freeze only after every precondition passes.
- Confirm the loaded pack still has 11 rows, mutation allowlist `[]`, and no mutation/clear/restore operation IDs.

### 17.2 Execution

- Follow rows 1–11 in frozen order.
- Record a precondition and exactly one terminal outcome for every row.
- Run only the fixed read probe attached to rows 1–7.
- Treat every newly observed package, service, property, type, permission, enum, or candidate clue as `DISCOVERY_ONLY`.
- Record M1, M2, M3, and M4 independently. Do not infer one display surface from another.
- Do not alter pack bytes, registry state, candidate state, eligibility, or order while on site.

### 17.3 Closeout

- Validate the metadata ledger, event order, terminal outcomes, and local evidence hashes.
- Preserve raw evidence only in the approved local ignored store; do not add it to tracked outputs.
- Close the session and disconnect before any discovery review or promotion.
- Perform N→N+1 work offline in a separate review phase.

## 18. Physical setup checklist before any future connection

All items are mandatory and must be recorded without exposing private values in tracked files:

- private, level, ventilated location;
- vehicle fully stationary;
- selector in P;
- parking brake engaged;
- approved compatible battery support;
- owner-approved battery threshold and stop procedure;
- operator and independent observer both present;
- exact approved target/profile/transport selected;
- local ignored, preferably encrypted, raw-evidence location prepared;
- approved recovery procedure available;
- observer has unconditional STOP authority;
- no D-H0 physical HUD toggle unless separate explicit consent exists.

Movement, selector drift, parking-brake change, identity drift, unexpected warning, battery issue, transport ambiguity, operator uncertainty, observer STOP, or deadline expiry means: start no new row and close safely.

## 19. Stop and recovery rules

For the read-only first session:

- no mutation frame should exist;
- no clear or restore operation should be dispatched;
- on any anomaly, stop new rows, preserve metadata/evidence, close the current row as `BLOCKED` or `INCONCLUSIVE`, and end the session;
- if the system appears to require a write, clear, restore, reboot, permission weakening, or alternate transport to continue, the scope is invalid: stop rather than improvise.

For a possible later mutation session, only after N+1 and separate authorization:

- capture typed prior state before a write;
- arm and push the immutable inverse frame before mutation;
- change exactly one mutation dimension/live frame;
- perform immediate typed read-back;
- execute only the approved clear/sentinel;
- restore and verify before popping the frame;
- on restore failure, retain the frame/evidence, execute only the separately approved recovery procedure, and end the mutation window;
- never auto-reboot, retry blindly, reorder rows, or add a newly discovered candidate.

## 20. Evidence and privacy handling

Tracked T10 metadata must not contain:

- private target addresses, VINs, serials, personal identifiers, GPS coordinates, routes, credentials, tokens, or private absolute paths;
- raw payload dumps, screenshots with identifying data, or unredacted transcripts;
- arbitrary live selector/ID/value inputs.

Use placeholders in documentation. Keep raw evidence local, ignored, access-limited, and retained only for the approved duration. Publish only sanitized metadata, hashes, aliases, terminal outcomes, and evidence references. Run both pattern-based and semantic privacy/security review before any future commit or push.

The five D-result files are metadata-only outcomes; they must not embed raw evidence. Ledger events must be gapless, immutable, identity-consistent, and have one terminal result per row.

## 21. Independent result matrix

All current states are `NOT_RUN`.

| Result | Required direct evidence | Invalid inference | Current state |
|---|---|---|---|
| D-H0 | Separately consented temporary physical-HUD diagnostic, including prior and restore | Setting acceptance or another HUD surface | `NOT_RUN`; consent defaults to no |
| D-M1 | Direct observation of HUD navigation maneuver/distance surface | Working cluster navigation | `NOT_RUN` |
| D-M2 | Direct observation of HUD road-name surface | M1 maneuver/distance | `NOT_RUN` |
| D-M3 | Direct observation of cluster speed-sign surface | `rc=0`, setting acceptance, or HAL read-back alone | `NOT_RUN` |
| D-M4 | Direct observation of windshield HUD speed-sign surface | D-M3 cluster sign | `NOT_RUN` |

Allowed terminal semantic outcomes are PASS, FAIL, INCONCLUSIVE, or BLOCKED as defined by the T10 contract. No result may be manufactured from an off-car test or rewritten after vehicle evidence is captured.

## 22. Open questions — unresolved unless explicitly answered

| ID | Question | Current default/consequence |
|---|---|---|
| T10-Q1 | Exact approved vehicle model/ROM/profile tuple? | Unresolved ⇒ no Session N |
| T10-Q2 | Approved battery-support procedure and stop threshold? | No numeric guess; unresolved ⇒ NO-GO |
| T10-Q3 | Which sender/permission mode is proven on the target? | Keep non-exported/in-app boundary; never weaken permission |
| T10-Q4 | Which exact tests/verifiers are required beyond the parent 15-path T10 list? | Revise/reseal parent boundary before edits |
| T10-Q5 | When/where is Session N, and who are operator/observer? | No vehicle window scheduled |
| T10-Q6 | Raw-evidence retention duration and encrypted local location? | Local + ignored + least retention; no upload |
| T10-Q7 | Is D-H0 physical OFF→ON→OFF diagnostic consented? | No; omit D-H0 mutation |
| T10-Q8 | If N+1 has multiple READY rows, what exact pack window is authorized? | Frozen order; no ad-hoc subset/reordering |
| T10-Q9 | What recovery is approved for restore failure on the exact profile? | No automatic reboot/retry; stop and preserve frame evidence |
| T10-Q10 | When may T11 production wiring begin? | Never from T10 alone; separate approved spec |

## 23. T10 task sequence

The reviewed spec defines this order:

- **T10-P0 — Approval & build:** approval, parent-boundary reconciliation, fixed off-car implementation.
- **T10-P1 — Safety & identity:** exact handoff, exact artifact/profile identity, physical preflight, Session N authorization.
- **T10-P2 — Session N:** exactly seven read-only rows and four independent milestones.
- **T10-P3 — Offline N+1:** sanitize/review/disposition discoveries, regenerate and independently review.
- **T10-P4 — Conditional M1:** only exact READY navigation-HUD rows under separate mutation authorization.
- **T10-P5 — Conditional M2:** only exact READY HUD-road rows after field-proven M1 prerequisites.
- **T10-P6 — Conditional M3:** only exact READY cluster-sign rows; S11 cannot mutate while in `MUTATION_REVIEW`.
- **T10-P7 — Conditional M4:** only exact READY HUD-sign rows after independent M3 content-plane proof; S12 remains rejected.
- **T10-P8 — Recovery & sealing:** clear/restore completion, metadata-only result sealing, privacy/integrity checks.
- **T10-P9 — Independent review:** requirements/gates/boundary review, off-car patches/retests, no rewriting vehicle evidence.

Only P0 is a possible next implementation phase, and even P0 requires the exact approval phrase. P1 does not authorize a session until its separate operational authorization is complete. P4–P7 are unreachable with the current registry.

## 24. Verification record and safe off-car commands

The following existing local/off-car commands were invoked for this 2026-08-10 preparation. None contains a vehicle connection or installation step.

Canonical verifier invocations:

```bash
scripts/verify-hud-sign-candidate-expansion.sh
scripts/verify-seal-hud-sign-offcar.sh
```

Direct offline Gradle matrix after the parent verifier stopped at O24:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew --offline --console=plain \
  :vehicle-contracts:test \
  :offcar-planner:test \
  :core:test \
  :app:testDebugUnitTest \
  :app:testVehicleTestUnitTest \
  :car-integration:test \
  :app:assembleDebug \
  :app:assembleVehicleTest \
  :app:assembleRelease \
  :app:lintDebug \
  :app:lintVehicleTest \
  :app:lintRelease
```

Forced parent/runtime unit-test rerun; planner was already executed by the expansion verifier:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew --offline --console=plain --rerun-tasks \
  :vehicle-contracts:test \
  :core:test \
  :app:testDebugUnitTest \
  :app:testVehicleTestUnitTest \
  :car-integration:test
```

Final working-tree whitespace check:

```bash
git diff --check
```

Current outcomes:

- candidate-expansion verifier: **PASS**;
- parent verifier: **FAIL/NEEDS_CHANGES at O24**, after O1–O3 PASS;
- direct offline Gradle matrix: **BUILD SUCCESSFUL**, 163 actionable tasks (3 executed, 160 up-to-date);
- forced parent/runtime unit rerun: **BUILD SUCCESSFUL**, 64/64 tasks executed in 3m04s;
- final `git diff --check`: **PASS**.

Do not invent or invoke the future vehicle runner. `scripts/vehicle/run-seal-hud-sign-matrix.sh` does not exist yet.

## 25. Resume checklist for the next agent/operator

### Before approval

- [ ] Read this handoff and all three active specs.
- [ ] Confirm Git HEAD, dirty counts, and zero staged files without modifying the tree.
- [ ] Reconfirm pack/plan/registry/coverage hashes from §9.
- [ ] Confirm exact T10 implementation approval is still absent.
- [ ] Keep vehicle access closed.
- [ ] Do not implement, install, connect, commit, or push.

### After exact T10 implementation approval

- [ ] Record that approval covers off-car implementation only.
- [ ] Resolve T10-Q4 and enumerate every required test/verifier/output path.
- [ ] Revise/reseal parent current/future boundaries; do not weaken O24.
- [ ] Rerun expansion and parent verifiers; require full green before continuing.
- [ ] Implement only the reviewed fixed-catalog/vehicleTest/transport/runner scope.
- [ ] Add focused tests for both sides of every operation, identity, ledger, and result boundary.
- [ ] Verify main/release have zero probe, raw, free-form, or arbitrary-ID surface.
- [ ] Run complete affected tests/build/lint, privacy/security scan, and independent senior review.
- [ ] Generate exact T10 APK, signer record, source/diff identity, and `hud-sign-vehicle-test-candidate.json`.
- [ ] Keep vehicle access closed after implementation.

### Before any future vehicle connection

- [ ] All T10 code/artifact reviews green, including parent O24.
- [ ] Exact handoff, APK, signer, source/diff, registry, pack, candidate set, profile, and transport deep-equal approved values.
- [ ] Q1–Q7 explicitly resolved for Session N.
- [ ] Physical checklist and two-person roles complete.
- [ ] Separate exact Session N READ_ONLY authorization received.
- [ ] Pack still has 7 READ_ONLY + 4 MILESTONE + 0 MUTATION rows.
- [ ] Mutation/clear/restore dispatch remains exactly zero.
- [ ] Any drift returns status to NO-GO.

## 26. Explicit do-not-do list

- Do not install `apk/ClusterNav-1.04-v104-527589f2d16a-release.apk`, any historical debug APK, or any generic build output.
- Do not reuse old exported debug actions, raw IDs, MASS probes, or direct shell recipes.
- Do not treat `rc=0`, accepted settings, or read-back alone as display proof.
- Do not infer M1 from cluster navigation, M2 from M1, or M4 from M3.
- Do not turn `READ_ONLY_READY` or `MUTATION_REVIEW` into `READY_FOR_FIELD` manually.
- Do not promote a discovery during the session that found it.
- Do not add runtime-provided IDs, values, selectors, commands, or free-form shell execution.
- Do not expose probe hooks in main/release.
- Do not modify T11 production files or create P-result artifacts.
- Do not bypass/reduce the parent path fence or delete certified work to satisfy it.
- Do not connect merely to discover the target profile or sender mode; those require an authorized identity preflight.
- Do not reset, clean, stage unrelated files, commit, push, or release without a separate request and mandatory scan.

## 27. Final status and next allowed action

**Final vehicle decision: NO-GO.**

The next possible action is a human review of this handoff and the T10 spec. If the owner chooses to authorize implementation, the required phrase is:

`Approve spec seal-hud-sign-vehicle-test-t10`

After that phrase, the next action is still **off-car only**: T10-P0 must first reconcile/reseal the parent path boundary exposed by O24, rerun the complete off-car gates, and only then implement the reviewed T10 surface. Vehicle connection, APK installation, Session N, mutation, and T11 each remain behind later independent authorization layers.

No vehicle command was run while preparing this handoff. No APK was installed. No vehicle state was changed. No file was staged, committed, or pushed.
