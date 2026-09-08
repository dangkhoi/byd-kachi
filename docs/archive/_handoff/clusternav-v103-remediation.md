# ClusterNav v1.03 Remediation — Execution Prompt

> Auto-generated from plan: `docs/specs/clusternav-v103-remediation.html`
> Stages: 5 | Total deliverables: 21 requirements (R1–R21) across 9 task phases (T0–T8)

## TASK

Implement full remediation of ClusterNav v1.03 review findings (3 P0, 16 P1, 6 P2) plus letterbox fix (R21), following the approved spec. Deliver a clean, vehicle-ready source with 0 test failures, 0 review findings, and exact-source manifest.

## WORKING DIR

`<project-root>`

## CONTEXT (≤ 5 lines)

- Senior review 2026-08-05 found 14/20 requirements FAIL, 3 PARTIAL. Current 799 tests pass but 139 were deleted without replacement.
- Letterbox: `NORMAL_DEFAULT.wmSize = "1920x800"` causes 0.9 scale on 1920×720 display → 96px black bars. Fix: change to `"1920x720"`.
- Existing design: `docs/specs/clusternav-v102-review-remediation.html` §Design D1–D8.
- Two tracks: Navigation/HUD and simplified Cast. V2 runtime must be retired. Home is renderer only.
- Protected CP/AA must never enter freeform. Cast must commit only on verified postcondition.

## CONSTRAINTS

- Backward compat: CarPlay (1422×800) and Android Auto (1920×1080) configs unchanged.
- No `pm clear`, `adb reboot` as evidence, or global-setting mutation without explicit auth.
- Every modified file ≤500 LOC. UI components ≤400 LOC.
- No deprecated APIs. Use latest stable patterns (Kotlin 2.4.10, AGP 9.3.1, dadb 2.0.0).
- Security: no secrets/PII/private paths in committed code. Redact vehicle IPs.
- Vehicle tests (T8) require separate BUILD AUTH — do not assemble APK without explicit authorization.
- App never self-grants `appwidget grantbind`.

## EXECUTION — 5-STAGE CHAIN

> ⚠️ Đây là orchestrator prompt. Không implement trực tiếp.
> Chạy từng stage tuần tự. Mỗi stage = 1 sub-agent DAG blocking.

---

### Stage 1: Toolchain + Letterbox + Release Hardening (T0 + T1)

**Sub-agents**: 2 parallel

- Agent 1 (build-engineer): T0 baseline freeze + T1 toolchain migration
  - Record HEAD/diff/APK inventory
  - Migrate Gradle 9.6.1, AGP 9.3.1, Kotlin 2.4.10, JUnit 6.1.2, dadb 2.0.0, SDK 37
  - Add dependency verification metadata
  - Adapt `kotlinOptions` → `compilerOptions`; adapt dadb 2.0 API changes

- Agent 2 (release-engineer): T1 release hardening + R21 letterbox
  - Add `vehicleTest` variant (diagnostic, debuggable=true, release-key required)
  - Harden `release` (non-debuggable, no debug-sign fallback, lint-gating)
  - Fix collector to inspect APK contract (package/version/signer/debug)
  - **R21 letterbox fix:** change `NORMAL_DEFAULT.wmSize` from `"1920x800"` to `"1920x720"` in `SimpleCastModels.kt`
  - Update any tests referencing old `1920x800` value for NORMAL

**Context7 validation** (each agent runs before coding):
- `resolve-library-id` + `query-docs` for Gradle, AGP, Kotlin, dadb, JUnit
- Verify latest API patterns; no deprecated usage
- Record verified versions in SDD

**SDD**: Write to `docs/specs/clusternav-v103-remediation.html` §Reviewer Log (append design decisions)

**Key files to read** (agents read themselves):
- `app/build.gradle.kts`
- `core/build.gradle.kts`
- `car-integration/build.gradle.kts`
- `gradle/wrapper/gradle-wrapper.properties`
- `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/SimpleCastModels.kt`
- `settings.gradle.kts`

**Exit gate** (verify ALL before Stage 2):
- [ ] `./gradlew --rerun-tasks :app:processDebugResources :app:compileDebugKotlin` SUCCESS
- [ ] `./gradlew --rerun-tasks :core:test :app:testDebugUnitTest :car-integration:test` — 0 failures
- [ ] `grep "1920x800" core/src/main/kotlin/` returns 0 matches in NORMAL_DEFAULT context
- [ ] `vehicleTest` and `release` variants exist in build config
- [ ] Release variant has `isDebuggable = false` and no debug-sign fallback
- [ ] All modified files ≤500 LOC
- [ ] Scope completeness: T0+T1 deliverables all ✅

**Handoff**: Write `docs/_handoff/stage-1-done.md`:
- Files created/modified (list paths)
- Gradle/Kotlin/AGP/dadb versions confirmed
- Test count after stage
- Letterbox fix confirmed (wmSize value)
- Release variant config summary

---

### Stage 2: Navigation + Widget (T2 + T3)

**Reads**: `docs/_handoff/stage-1-done.md`
**Assumes done** (do not re-implement):
- Toolchain migration complete and compiling
- Letterbox fix applied
- Release variants configured

**Sub-agents**: 2 parallel

- Agent 1 (nav-engineer): T2 Navigation outputs
  - Keep original session/source/sequence through to physical owners (no NavState conversion)
  - Each output (Lane, HUD, Speed-sign) owns one bounded queue/executor/cache
  - Physical writer returns typed `OutputDeliveryResult` (success/failure)
  - Generation-fence: clear invalidates queued positive writes
  - Fix stale FutureTask retention and timeout reason overwrite
  - Persistence load corruption fails closed without crashing

- Agent 2 (widget-engineer): T3 VietMap widget
  - Split speed/alerts into independent `VietMapProviderSnapshot<T>` with per-slot freshness/reason/generation
  - Remove combined parser gate that couples providers
  - Check all preference `commit()` results; reconcile orphan widget IDs
  - Generation-bind HostView/listener callbacks
  - Move drawable hashing off main thread
  - Speed-sign clear: retryable state machine for OFF/stale/disconnect/destroy/bootstrap

**Context7 validation**:
- Kotlin coroutines / executor patterns for bounded queues
- Android AppWidgetHost lifecycle best practices

**Key files to read**:
- `core/src/main/kotlin/.../navigation/` — all files
- `app/src/main/java/.../NavNotificationListener.kt`
- `app/src/main/java/.../NavRepository.kt`
- `app/src/main/java/.../NavigationHudOwner.kt`
- `app/src/main/java/.../NavigationSpeedSignOwner.kt`
- `app/src/main/java/.../vietmapwidget/VietMapWidgetBridge.kt`
- `app/src/main/java/.../vietmapwidget/VietMapWidgetPrefs.kt`
- `core/src/main/kotlin/.../NavigationModels.kt`

**Exit gate**:
- [ ] Producer→frame→lane/HUD/speed-sign tests pass (typed results, generation fencing)
- [ ] Clear/saturation/wedge/stale tests pass for each output independently
- [ ] Widget lifecycle tests: bind/unbind/restart/stale/commit-failure pass
- [ ] Per-slot freshness: bad Alerts cannot invalidate fresh speed (test proves)
- [ ] `./gradlew --rerun-tasks :core:test :app:testDebugUnitTest` — 0 failures
- [ ] All modified files ≤500 LOC
- [ ] Scope completeness: T2+T3 deliverables all ✅

**Handoff**: Write `docs/_handoff/stage-2-done.md`:
- Files created/modified
- Navigation boundary contracts (frame fields → output typed results)
- Widget boundary contracts (provider snapshot shape)
- New test count

---

### Stage 3: Cast Core + Cast UI (T4 + T5)

**Reads**: `docs/_handoff/stage-2-done.md`
**Assumes done**:
- Navigation outputs are independently owned with typed results
- Widget providers are independent with per-slot generation
- Toolchain and letterbox are stable

**Sub-agents**: 2 parallel

- Agent 1 (cast-core-engineer): T4 Cast core safety
  - CP/AA classification: closed type, rechecked by placement; full-only + exact fullscreen standard stack
  - Bounded operation owner: connect/read/op deadlines, close-on-timeout
  - Typed `DeviceMutationOutcome` (Verified/Rejected/Unknown/TimedOut)
  - Commit state ONLY on `Verified`; preserve prior on rejection/unknown
  - Parser: typed stack/task/windowing/bounds model; ambiguity → reject (no first-match)
  - Stop is priority/preemptive: not queued behind pending I/O
  - Block occupied-slot replacement; persist only applied geometry/density
  - Extract transport/operation-owner/parser/verifier into separate files ≤500 LOC each

- Agent 2 (cast-ui-engineer): T5 Cast UI lifecycle
  - Bubble: extract renderer/gesture/dispatcher; ≥48dp zones; one tap token; remove listeners on destroy; non-destructive disabled zones; accurate content descriptions
  - Home: extract autostart/geometry/status; display-aware editor (no 1920×720 hardcode); one shared DPI for split; cancel delayed work on lifecycle change
  - Diagnostics: reads SimpleCastRuntime state only; no V2 CastAndroidRuntime/CastFacade creation
  - All extracted files ≤500 LOC (target ≤400 for UI)

**Context7 validation**:
- dadb 2.0.0 API for transport deadlines and typed exceptions
- Android accessibility best practices for touch targets and content descriptions

**Key files to read**:
- `core/src/main/kotlin/.../simplified/SimpleCastCoordinator.kt`
- `core/src/main/kotlin/.../simplified/SimpleCastModels.kt`
- `core/src/main/kotlin/.../simplified/AppMover.kt`
- `core/src/main/kotlin/.../simplified/CastStackParser.kt`
- `app/src/main/java/.../FloatingBubbleService.kt`
- `app/src/main/java/.../MainActivityCastController.kt`
- `app/src/main/java/.../BubbleActionDispatcher.kt`
- `app/src/main/java/.../DiagActivity.kt`
- `app/src/main/java/.../simplified/SimpleCastRuntime.kt`

**Exit gate**:
- [ ] CP/AA cannot enter freeform: test proves reject on non-fullscreen stack
- [ ] Cast failure matrix: timeout/reject/partial never commits success
- [ ] Real-dump parser tests with ambiguity rejection
- [ ] Stop preempts pending operation (test proves)
- [ ] Bubble ≥48dp zones (test or resource verification)
- [ ] Rapid tap coalescing (test proves no duplicate commands)
- [ ] No V2 runtime in DiagActivity (grep proves)
- [ ] `./gradlew --rerun-tasks :core:test :app:testDebugUnitTest` — 0 failures
- [ ] All files ≤500 LOC (FloatingBubbleService, MainActivityCastController split)
- [ ] Scope completeness: T4+T5 deliverables all ✅

**Handoff**: Write `docs/_handoff/stage-3-done.md`:
- Files created/modified
- Cast operation contract shape (DeviceMutationOutcome variants)
- CP/AA policy enforcement point
- UI extraction map (old file → new files)
- Test count

---

### Stage 4: Test Rebaseline + V2 Cleanup (T6)

**Reads**: `docs/_handoff/stage-3-done.md`
**Assumes done**:
- Cast core with typed postconditions and bounded transport
- Cast UI split into lifecycle-safe components ≤500 LOC
- Navigation + widget independently owned

**Sub-agents**: 2 parallel

- Agent 1 (cleanup-engineer): V2 retirement
  - Semantic reference scan: grep/find all callers of V2 production files
  - Verify each V2 file has ZERO active runtime callers (only test/docs refs)
  - Delete retired V2 production source (`core/.../clustercast/v2/` runtime files)
  - Delete stale V2 test files that test retired architecture
  - Update DiagActivity, Bubble, notification references to use simplified types only
  - Remove orphan XML resources, stale comments referencing V2

- Agent 2 (test-engineer): Replacement test coverage
  - Add tests for NavigationHudOwner / NavigationSpeedSignOwner (clear ordering, HAL fail, saturation)
  - Add tests for VietMapWidgetBridge independent freshness + lifecycle + commit failure
  - Add tests for Cast postcondition (real-dump ambiguity, CP/AA stack proof, split/return/resize failure)
  - Add tests for Bubble rapid tap/recreate/48dp accessibility
  - Update HUD budget test to approved value
  - Verify replacement count ≥ deleted count (no net coverage loss)

**Key files to read**:
- `core/src/main/kotlin/.../clustercast/v2/` — list all, check callers
- `core/src/test/kotlin/.../clustercast/v2/` — identify active vs stale
- All new files from Stage 2 and 3 (need test coverage)

**Exit gate**:
- [ ] `grep -r "CastAndroidRuntime\|CastFacade\|CastCoordinator\b" --include="*.kt" app/ core/` — 0 matches in non-test production code (excluding docs/comments about removal)
- [ ] `./gradlew --rerun-tasks :core:test :app:testDebugUnitTest :car-integration:test` — 0 failures
- [ ] New test count ≥ prior test count (no net loss from V2 deletion)
- [ ] Direct test exists for each: HudOwner, SpeedSignOwner, WidgetBridge per-slot, CastPostcondition, CP/AA policy
- [ ] No file in active source exceeds 500 LOC
- [ ] Scope completeness: T6 deliverables all ✅

**Handoff**: Write `docs/_handoff/stage-4-done.md`:
- V2 files deleted (list)
- Replacement tests added (list with coverage area)
- Final test count (core/app/car)
- Any V2 file kept with justification

---

### Stage 5: Consolidate + Senior Review + Security Scan (T7)

**Reads**: `docs/_handoff/stage-4-done.md`
**Assumes done**:
- All implementation (T0–T6) complete
- V2 retired, replacement tests in place
- Full suite passing

**Sub-agents**: 2 parallel + 1 senior reviewer (sequential after)

- Agent 1 (scope-auditor): Scope completeness check
  - Read spec `docs/specs/clusternav-v103-remediation.html` — list every R1–R21
  - For each requirement: verify code exists + is wired + has test + user can use it
  - Mark ✅ / ❌ per requirement
  - If any ❌: implement missing piece immediately
  - Verify no hardcoded 1920×800, no V2 runtime in active code, no file >500 LOC

- Agent 2 (security-scanner): Security + redaction scan
  - Scan all changed files for secrets/PII/credentials (pattern + semantic)
  - Verify no private IPs (`<user-home>`, `<vehicle-ip>`, RFC1918 in non-artifact docs)
  - Verify `.gitignore` coverage adequate
  - Report: CLEAN / WARN / BLOCK

**Then sequentially — Senior Review sub-agent:**

```
TASK: Senior review + patch + scope completeness + technology freshness + boundary shape
ROLE: Senior architect / code reviewer
FILES TO REVIEW: [all files changed across ALL stages — read from stage-1/2/3/4-done.md]
SPEC DOCUMENT: docs/specs/clusternav-v103-remediation.html
DESIGN REFERENCE: docs/specs/clusternav-v102-review-remediation.html (D1-D8)
WORKING DIR: <project-root>
REQUIREMENTS:
1. Read SPEC DOCUMENT — list every R1–R21 deliverable/exit criteria
2. Read all changed files across all stages
3. For each deliverable: verify code exists, is wired, has test, user can use it
4. Identify bugs, edge cases, cross-platform issues, missing error handling
5. Fix each issue directly. Tag severity [P0]–[P3]
6. If scope gaps: implement missing pieces or report SCOPE GAP clearly
7. Context7: verify each major dependency (latest version? deprecated API?)
8. Boundary shape check: for every contract (Nav frame→outputs, widget→speed-sign,
   Cast intent→operation→postcondition, UI→coordinator, build→collector→candidate),
   read BOTH sides and compare shape field-by-field
9. Run tests after fixes. If code changed → rerun review (loop until 0 P0–P1)
10. Report: scope checklist, tech freshness table, boundary shape table, issues+severity, fixes, test results
DO NOT: Add features beyond plan scope, refactor beyond scope
```

**Exit gate**:
- [ ] R1–R21 scope checklist: all ✅
- [ ] `./gradlew --rerun-tasks :core:test :app:testDebugUnitTest :car-integration:test` — 0 failures
- [ ] Senior review: 0 P0–P1 findings remaining
- [ ] Tech freshness: no deprecated APIs in new code
- [ ] Boundary shape: all contracts verified field-by-field
- [ ] Security scan: CLEAN (no BLOCK findings)
- [ ] All modified files ≤500 LOC
- [ ] Exact-source manifest generated for v1.03
- [ ] `docs/specs/clusternav-v103-remediation.html` §Reviewer Log updated

---

## ORCHESTRATOR INSTRUCTIONS

1. **Đọc lại spec document** (`docs/specs/clusternav-v103-remediation.html`) trước khi bắt đầu (W6 — stay on track)
2. Chạy Stage 1 bằng `subagent` tool (blocking mode, role: kiro_default)
   - Sub-agent prompt PHẢI include: "Dùng Context7 (resolve-library-id + query-docs) verify dependencies trước khi code"
3. Verify exit gate (chạy test/grep/build) + scope completeness check (W3)
4. PASS → ghi handoff file → proceed Stage 2
5. FAIL → fix trong context, re-verify, KHÔNG skip
6. Lặp cho đến hết stages
7. Stage 5: senior review sub-agent (W4) + security scan (W5) trước commit
8. **Autonomous mode**: sau khi user approve, chạy liên tục đến xong. Không hỏi giữa chừng.
   - Chỉ BLOCK khi security scan phát hiện [BLOCK] secrets
   - Mọi ambiguity nhỏ → chọn safe option, ghi vào spec §Reviewer Log

## ERROR RECOVERY

- Sub-agent fail / partial output → đọc output, identify missing items, re-run stage với scope thu hẹp
- Context approaching limit → ghi progress vào handoff file, báo user resume point
- Test fail sau stage → fix trong stage đó trước khi proceed
- Context7 unavailable → proceed with best-known version, flag in spec as "unverified"
- Senior review finds P0/P1 → fix immediately, rerun tests, rerun review (loop)

## FINAL EXIT CRITERIA

- [ ] R1–R21 each has code + test + wiring evidence
- [ ] Fresh full test suite: 0 failures
- [ ] Senior review loop exits with 0 P0–P3 findings
- [ ] Security scan CLEAN; no private IP/path in tracked docs
- [ ] No `1920x800` in NORMAL_DEFAULT (R21 letterbox)
- [ ] No V2 runtime in active production code (R2)
- [ ] CP/AA cannot enter freeform (R4 — test proves)
- [ ] Cast commits only on verified postcondition (R3 — test proves)
- [ ] All modified files ≤500 LOC (R18)
- [ ] Exact-source manifest generated for v1.03 (R19)
- [ ] SDD complete at `docs/specs/clusternav-v103-remediation.html`
- [ ] Context7 tech freshness verified — no deprecated APIs
- [ ] T8 (vehicle) NOT executed — requires separate BUILD AUTH from owner

**Workflow gates (bắt buộc):**
- [ ] Context7 tech freshness verified for: Gradle, AGP, Kotlin, dadb, JUnit
- [ ] Senior review verdict: APPROVED (0 P0–P1, scope 100%)
- [ ] Security scan: CLEAN (no [BLOCK] findings in final diff)
- [ ] All tests pass after senior review patches
