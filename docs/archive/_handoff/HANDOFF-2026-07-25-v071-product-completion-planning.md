# HANDOFF — Cluster Cast `0.71` product completion: scope ready, approval pending

> **Checkpoint:** 2026-07-25 21:14 +07:00
> **Owner:** Đăng Khôi · `dangkhoi`
> **Status:** planning/review complete; implementation **NOT STARTED**; approval token **NOT RECEIVED**.
> **Resume rule:** do not modify runtime/tests until the user explicitly approves `docs/specs/cluster-cast-v071-product-completion.html`.

---

## 0. TL;DR

- Cluster Cast `0.70` is fully closed as frozen exact-source evidence and must remain immutable.
- Candidate `0.71` is planned to complete all five items deferred by 0.70:
  1. installed application icons;
  2. one durable default app;
  3. total typed protected-package policy;
  4. canonical V2 floating Bubble;
  5. guarded explicit-opt-in boot auto-cast.
- Consolidated spec:
  `docs/specs/cluster-cast-v071-product-completion.html`.
- Current spec evidence: 71,908 bytes, 268 physical lines, 22 requirements, 7 task phases, 3 inline SVG diagrams and 4 append-only reviewer passes.
- Final scope re-review returned:
  - `ZERO_ACTIONABLE_FINDINGS`
  - five-feature coverage `PASS`
  - Requirement → Design → Task → Verification `PASS`
  - `COMPLETE_SCOPE_APPROVED_FOR_USER_DECISION`
- This reviewer verdict means the document is ready for the owner's decision. It is **not** owner approval.
- No 0.71 runtime or test code has been modified. Project version remains `0.70 (70)`.
- No APK build, install, vehicle operation, commit, push, merge, reset or clean occurred during 0.71 planning.

---

## 1. Mandatory resume gate

The repository workflow requires one consolidated out-of-band approval before implementation.

Accepted approval text:

```text
Approve spec docs/specs/cluster-cast-v071-product-completion.html
```

Approval authorizes only:

1. canonical-document amendments required by T0;
2. source and test implementation of R1–R22/T1–T5;
3. focused and full off-car JVM validation;
4. bounded report-only senior reviews and remediation;
5. version bump to `0.71 (71)` only after clean implementation review;
6. a new exact-source identity and public-repository security scan;
7. stopping at an **OFF-CAR EXACT-SOURCE ONLY** candidate.

Approval does **not** authorize:

- `assemble*` or any APK build;
- APK install or device connection;
- ADB/DADB/vehicle commands;
- physical or synthetic vehicle testing;
- commit, push, merge, reset, clean or branch publication;
- support/release claims;
- reuse or relabelling of historical APKs/evidence.

If approval is absent, the only legal continuation is documentation review/clarification.

---

## 2. Frozen 0.70 evidence — preserve exactly

| Item | Frozen value |
|---|---|
| Version | `versionCode=70`, `versionName="0.70"` |
| Final exact-source manifest | `docs/_handoff/cluster-cast-v070-manual-cold-r2-exact-source.json` |
| Final source ID | `43efd3c96a43c2fda4f1c6c93b696cbe1fd07bc8241d3968dc113217216a8f64` |
| Prior/invalidated manifest | `docs/_handoff/cluster-cast-v070-manual-cold-exact-source.json` |
| Prior source ID prefix | `92e972b9…` |
| Final JVM result | 51 suites, 409 tests, 0 failures/errors/skips |
| Final bounded reviews | `ZERO_ACTIONABLE_FINDINGS`, `APPROVED` |
| Security inventory | 249 paths: 241 text, 8 binary; 0 blocked filename/secret/PII/private-infrastructure matches |
| Historical ignored APK | `app/build/outputs/apk/release/app-release.apk` |
| Historical APK SHA-256 | `8f5901c2c15cf513b5e64609258726ddaac11ab49a21ff66fc099d33e213f002` |
| Historical APK identity | old 0.67 artifact; predates 0.70 and must not be relabelled |
| Authorized 0.70 build path | `.authorized-build/43efd3c96a43` does not exist |

The first 0.70 manifest is retained as a hashed `PRIOR_OR_INVALIDATED_ATTESTATION` exclusion in the r2 identity. Never overwrite either manifest.

---

## 3. Final 0.71 product scope

### 3.1 Installed app presentation

- Load launcher label/icon off-main with framework `PackageManager` APIs.
- Use one bounded immutable icon/fallback model across:
  - Activity tiles and quick switch;
  - App Manager list/details/default selector;
  - Bubble default/favorite rows.
- Cover success, null, throw, fallback, recycling and recreation.
- Icon failure must not remove, reorder incorrectly or disable an eligible app.
- Search contract: blank query, case-insensitive label/package match, no-result state and recreation.

### 3.2 App Manager and durable default

- Row tap opens details only.
- Favorite Add/Remove, Cast, Set/Replace/Clear Default and protection actions are distinct labeled controls.
- At most one durable default lives in the existing AtomicFile Cast envelope.
- A stale/uninstalled/non-launchable default remains visibly unavailable until explicit Clear/Replace succeeds.
- Default preselection is local presentation only and emits zero Cast operations.
- Any eligible launchable policy-supported app may be default; favorites affect ordering/quick access only.
- This intentionally supersedes the older canonical “auto-cast radio across favorites” wording; T0 must amend the canonical UX document before runtime edits.

### 3.3 Total protected-package policy

All four classes are binding:

```text
NORMAL
SYSTEM_PROJECTION
USER_KEEP_SESSION
UNKNOWN_PROTECTED
```

- `SYSTEM_PROJECTION` and `UNKNOWN_PROTECTED` are visibly locked and non-overridable.
- `UNKNOWN_PROTECTED` exports no Cast/destructive action until fresh evidence resolves it.
- User KEEP_SESSION Add/Remove is allowed only for freshly proven NORMAL apps under Advanced with consequence text.
- Planner snapshot evidence remains final authority; preferences cannot downgrade system/unknown evidence.
- Legacy migration must never convert unknown/system truth into a user-removable normal override.

### 3.4 Canonical V2 Bubble

- Consumes the same canonical `CastRenderModel`/actions as Activity.
- No localized-title inference, legacy RAM policy or duplicate Activity Stop intent.
- One typed Stop request with immediate local acknowledgement and duplicate suppression.
- Shared icon+text default/favorite menu; disabled actions follow canonical export truth.
- Drag position is clamped/persisted; stale callbacks are lifecycle-generation fenced.
- Full accessibility contract:
  - Stop → apps → settings focus order;
  - state+target content description;
  - TalkBack node/action contract;
  - keyboard and rotary traversal;
  - Back/outside dismissal;
  - non-focusable overlay flag restoration;
  - focus behavior across recreation and empty/long menus.

Bubble desired-enabled lifecycle is independent from auto-cast:

- fresh install or absent legacy key → disabled;
- explicit legacy true/false migrates;
- Enable requires overlay permission or shows permission guidance;
- explicit Retry/Start after permission may start only the presentation service;
- Disable immediately removes overlay/notification and sends zero Cast Stop requests;
- post-unlock boot/package replacement may restore an opted-in Bubble as presentation only;
- Bubble on/off × auto-cast on/off combinations are all valid;
- overlay denial never changes automation eligibility.

### 3.5 Versioned consent and guarded boot auto-cast

Operational config remains in the Cast envelope:

```text
AutomationConfig(
  revision,
  defaultPackage,
  autoCastEnabled,
  consentVersion
)
```

- `CURRENT_AUTOMATION_CONSENT_VERSION = 1`.
- Enable requires the current consequence disclosure and explicit Accept.
- Cancel writes nothing.
- Revoke/Disable sets enabled=false and consent=null.
- A newer consent version renders `REVIEW_REQUIRED`; no arm/claim until re-accepted.
- Disable/Revoke/Clear/config change wins before first ISSUED effect.
- After any ISSUED row, the change persists immediately, fences not-yet-issued phases and uses existing Stop/recovery resolution; it never pretends an issued effect was undone.

Durable boot protocol:

1. only post-unlock `BOOT_COMPLETED` is the initial automation trigger;
2. receiver uses `Settings.Global.BOOT_COUNT` and one store-locked `initializeForBoot + recordOrGet`;
3. PENDING request is committed first;
4. only after commit may receiver enqueue non-exported `CastAutomationService` with exact `requestId`;
5. enqueue throw terminalizes that same request as `FOREGROUND_START_DENIED`;
6. service validates requestId and calls `startForeground()` before worker work;
7. duplicate hosts coalesce at process runtime/claim CAS;
8. death after record but before enqueue leaves a durable zero-effect PENDING request; only duplicate same-boot `BOOT_COMPLETED` may enqueue the same ID, otherwise next-boot rollover archives it;
9. only one request-bound `DEFERRED(PRIOR_JOURNAL)` revalidation alarm is allowed;
10. generic startup, Activity/Bubble creation, package replacement, refresh and unrelated alarms are not automation triggers.

Automation always uses the process-singleton Cast runtime and existing manual-intent path. No receiver/view/legacy code may emit raw mutation.

---

## 4. Exact schema targets

Current frozen baseline:

```text
Cast envelope schema = 2
Canonical UI schema = 4
Canonical UI hash = c7d0a589043f838280d2c28b05928fb64da9a50557718e9ea61ca3e7b8a24f09
```

0.71 target:

- envelope schema 3;
- canonical UI schema 5 with a newly generated hash;
- origin-tagged `PendingCastIntent(USER | BOOT_AUTO, automationRequestId?)`;
- v1/v2 pending strings decode as USER;
- exact `AutomationConfig`, `BootAutomationRequest` and `AutomationOutcome` shapes from D4;
- successful COMPLETED request/outcome has null reason;
- BLOCKED/SUPERSEDED has a compatible non-null reason;
- invalid state/attempt/timestamp/reason/boot/config combinations decode fail-closed as corrupt/unsupported;
- UI projection precedence:
  1. durable Stop;
  2. recovery/manual;
  3. active transaction;
  4. automation request/outcome;
  5. steady stable/cold state.

---

## 5. Required implementation order after approval

Execute direct 3–5-file batches; do not launch a broad implementation DAG.

1. **T0 — Canonical docs first**
   - Amend narrow 0.70 R11 exception for origin-tagged BOOT_AUTO.
   - Correct stale canonical UI schema prose to v4/current hash.
   - Promote v3/v5 targets, four-class protection, App Manager/default/consent semantics, durable-before-host protocol and Bubble lifecycle.
   - Carry downstream exact-build/on-car/release gates forward.

2. **T1 — Catalog/App Manager/config/protection**
   - Extract immutable presentation and icon loading.
   - Add search/details/separate actions.
   - Add envelope-owned default/config API and migration.
   - Add total four-class policy and tests.

3. **T2 — Activity and Bubble surfaces**
   - Extract presentation before touching Activity; it is already 493 physical lines and must remain `<495`.
   - Add local default preselection and shared icons.
   - Replace Bubble heuristics/duplicate Stop.
   - Add independent desired-enabled lifecycle and accessibility contracts.

4. **T3 — Envelope/automation/platform host**
   - Implement schema-3 codec/backward decode and UI-v5 projection.
   - Implement request/claim/supersession/rollover state machine.
   - Add durable-before-enqueue receiver protocol and bounded foreground service.
   - Route only through process-singleton runtime/manual-intent owner.

5. **T4 — Prove off-car**
   - Run focused suites after each batch.
   - Run complete forced `:app:testDebugUnitTest` only after convergence.
   - Run schema/hash/static graph/LOC/diff checks.
   - Audit R1–R22 one by one for code + test + user-facing wiring.

6. **T5 — Bounded review loop**
   - Separate catalog/config, Bubble/actions and automation/store/orchestration boundaries.
   - Each reviewer is report-only, ≤5 files, one contract boundary and no Gradle.
   - Primary agent patches, runs focused tests and re-reviews until zero P0–P3.

7. **T6 — Close source and stop**
   - Bump to `0.71 (71)` only after clean implementation reviews.
   - Force full JVM rerun.
   - Generate a new collision-safe 0.71 exact-source manifest/ID.
   - Run pattern + semantic public-repository security scan.
   - Label candidate `OFF-CAR EXACT-SOURCE ONLY`.
   - Stop before every APK/build/install/vehicle/publication action.

---

## 6. Active task state

```text
[done]    #1  Finalize deferred-feature list and boot/autostart safety boundary
[pending] #2  Consolidated spec reviewed clean; waiting for explicit owner approval
[pending] #8  Implement icons/default/protected-package policy
[pending] #9  Implement canonical V2 Bubble
[pending] #10 Implement guarded boot auto-cast
[pending] #11 Run off-car validation and bounded senior reviews
[pending] #12 Close 0.71 exact-source candidate
```

Do not mark task #2 complete until the owner supplies the approval token.

---

## 7. Current implementation facts and hard constraints

- Toolchain remains fixed:
  - AGP 8.5.2;
  - Kotlin 1.9.24;
  - JDK 17;
  - compileSdk/targetSdk 34;
  - minSdk 29;
  - dadb 1.2.10;
  - JUnit Jupiter 5.10.2;
  - jqwik 1.8.4.
- No dependency or toolchain upgrade is in scope.
- `CastAndroidRuntime.create()` is process-singleton; Activity, Bubble and automation service must share it.
- Runtime mutation must continue through one Cast coordinator/store/executor lease/gateway/journal.
- No legacy `ClusterCast` mutation import/delegation.
- No second operational preference store or journal.
- Existing catalog preference file remains `cast-v2-app-catalog` for visual/catalog state only.
- Operational default/consent/auto config belongs in the existing AtomicFile envelope.
- Manifest already declares foreground service, notification, boot, overlay and special-use permissions; the new automation service still needs its own non-exported special-use declaration/subtype property.
- `ClusterCastActivity.kt` is 493 physical lines; keep it `<495` by extracting presentation first.
- Every modified/new source and test file must remain `≤500` LOC.
- Preserve extensive pre-existing working-tree changes. Never reset, clean, checkout over or restart implementation from scratch.

---

## 8. Review history and latest evidence

Planning review progression:

1. Initial architecture review: 0 P0, 6 P1, 1 P2 → remediated.
2. Terminal-schema re-review: 2 P1 → exact state/reason matrix and representable supersession added.
3. Projection re-review: 1 P1 → R19/D4 aligned to active-transaction-before-automation.
4. Fresh product-scope audit: 6 P1, 2 P2 → all promoted into binding requirements/design/tasks/tests.
5. Final exact-four-file scope re-review:
   - `ZERO_ACTIONABLE_FINDINGS`;
   - all five deferred increments PASS;
   - traceability PASS;
   - prerequisite/gate verdict PASS for user decision;
   - `COMPLETE_SCOPE_APPROVED_FOR_USER_DECISION`.

Final structural validation after reviewer-log append:

```text
bytes=71908
lines=268
requirements=22
execution tasks=7
reviewer passes=4
inline SVG diagrams=3
git diff --check=clean
versionCode=70
versionName=0.70
```

No Gradle, APK, install, device, vehicle or Git publication operation was used to close 0.71 planning.

---

## 9. Files to read first on resume

Read in this order:

1. `docs/_handoff/HANDOFF-2026-07-25-v071-product-completion-planning.md`
2. `docs/specs/cluster-cast-v071-product-completion.html`
3. `docs/specs/cluster-cast-v070-manual-cold-intent.html`
4. `docs/specs/cluster-cast-rebaseline.html`
5. `docs/specs/clusternav-uxui-rebaseline.html`

After approval and before each implementation batch, read only the 3–5 relevant source/test files for that boundary. Do not load or mutate the entire repository in one opaque stage.

---

## 10. Downstream gates intentionally NOT STARTED

Even after source implementation succeeds, the following remain outside this authorization:

- canonical Navigation/Dead-Reckon sequencing evidence still applicable to release;
- exact behavior manifest/screenshots;
- exact APK/source/signature/version/flag provenance binding;
- APK build and install;
- case-30 boot auto-cast on-car evidence;
- App Manager/Bubble/accessibility/migration exact-build evidence;
- physical vehicle tests and owner sign-off;
- support, merge or release claim.

A later authorization must name a unique exact-source ID and isolated output path before any APK task. This handoff grants no such authorization.

---

## 11. Resume prompt

If the owner has not approved:

```text
Spec 0.71 đã review và scope-audit sạch. Chờ owner xác nhận:
Approve spec docs/specs/cluster-cast-v071-product-completion.html
```

If the owner supplies that exact approval, continue autonomously from T0 through T6 under the constraints above, then stop at the off-car exact-source boundary without asking feature-by-feature questions.
