# HANDOFF — Cluster Cast V2 `0.69`: cold bootstrap blocked before journal

> **Checkpoint:** 2026-07-25 after the consolidated Seal DL3 read-only harvest.  
> **Next phase:** off-car only. Do not reconnect to the vehicle, press Retry, or issue AutoContainer commands while fixing source.  
> **Owner feedback:** the live workflow became too slow and repetitive. Future work must use the consolidated evidence below, finish source/test/review off-car, and return to the vehicle only with a new exact build and a short predeclared action matrix.

---

## 0. TL;DR

- Exact ClusterNav `0.69 (69)` was built and installed successfully from source ID:
  `2d4ef414167acd5cbc1f4ad78fa2918d624df5a88c0fb3cf6d7cae4c5b44c1da`.
- Opening Cluster Cast passed: no crash, CarPlay survived, only logical Display `0`, Retry and Diagnostics were exposed.
- Pressing Retry **did not dispatch `30`, `16`, `35`, `18`, or `0`**. Bootstrap was blocked before transaction creation and before the five-row ledger.
- Diagnostics remained pristine:
  `schema=2`, `durableEpoch=0`, `effectiveUi=V2`, `transaction=null`, `stableSession=null`, `ledgerEntries=0`.
- Exact root causes are now proven from real dumps:
  1. Display preflight counts textual `Display ...` manager/power headings as logical displays instead of matching only numeric `Display N:` blocks.
  2. AM preflight expects `displayId` on every `taskId` line, but this ROM supplies it on the parent `Stack id=...` line; child tasks inherit the stack display.
  3. The UI's generic `500 ms` acknowledgement replaces the in-flight/result text while the valid raw preflight is still running, hiding the precise block reason.
- All remaining preflight facts are valid: exact vehicle tuple, WM display `0`, user `0`, animation values `0.5 / 0.5 / 1.0`, three AM stacks all on display `0`, and nonblank appops.
- A patch sub-agent was started but **cancelled before execution**. None of the three corrections above has been applied.
- Do not rebuild `0.69` or reuse its source ID. Patch, test, review, bump to `0.70 (70)`, create a new exact-source identity/security scan, then stop for separate build/install authorization.

---

## 1. Installed artifact and provenance

| Item | Value |
|---|---|
| Package | `com.byd.clusternav` |
| Installed version | `0.69 (69)` |
| Exact-source manifest | `docs/_handoff/two-track-cold-bootstrap-exact-source.json` |
| Source ID | `2d4ef414167acd5cbc1f4ad78fa2918d624df5a88c0fb3cf6d7cae4c5b44c1da` |
| Isolated APK | `.authorized-build/2d4ef414167a/app/outputs/apk/release/app-release.apk` |
| APK SHA-256 | `b1f1356251950601da5b7e87c5e0f09c3a94f1510766c841d17add2dfceb7c0c` |
| Certificate SHA-256 | `1d300db7d9190f72595ef7005f5f05157f009e4f7676c5a321cb69ce785ff85a` |
| Signature | APK Signature Scheme v2 verified |

Build/install results:

- Exact-source re-verification passed immediately before build.
- One isolated release APK was produced; historical APK hashes were unchanged.
- Installed `base.apk` was pulled back and matched the local APK byte-for-byte.
- MainActivity smoke passed; no crash/ANR.
- Evidence: `.authorized-build/2d4ef414167a/evidence/oncar-install-20260725T092104Z`.

The installed APK remains reproducibly tied to the source ID above. The working tree now also contains this new handoff and will later contain the fix, so **future builds require a new manifest/source ID**.

---

## 2. Last observed vehicle state

Last observed after Retry, Diagnostics, and the final read-only harvest:

```text
ClusterNav PID       10049
CarPlay PID          3116
Android Auto PID     2744
Container PID        1836
Logical displays     [0]
fission/xdja lines   0
ClusterNav crash/ANR 0
AutoContainer calls  0
```

Diagnostics screen:

```text
mode=READ_ONLY
observation=Unknown(reason=expected exactly one named cluster display)
schema=2
durableEpoch=0
effectiveUi=V2
transaction=null
stableSession=null
ledgerEntries=0
```

Interpretation:

- No transaction or journal was created.
- No forward or compensation command was issued.
- CarPlay/Android Auto/container processes stayed alive.
- No virtual cluster display exists yet.
- Installed `0.69` is safe in its current pristine state but its Retry path cannot reach opcode `30`.
- Do not press Retry again on `0.69`; it will only repeat the same pre-journal block.

---

## 3. Evidence map — read this instead of returning to the car

### Consolidated off-car harvest (primary source)

```text
.authorized-build/2d4ef414167a/evidence/offcar-harvest-20260725T093345Z/
  README.md
  HARVEST-SUMMARY.json
  SHA256SUMS
  meta/commands.tsv
  raw/*.txt
```

Harvest properties:

- 79 evidence files hash-inventoried.
- Read-only; no app action and no AutoContainer service call.
- Contains vehicle facts, AM/WM/display dumps, SurfaceFlinger, activity/process/service state, package/component resolution, user/animation/appops outputs, filtered logs, installed APK identity, and command timing.
- Deliberately excludes routes, destinations, Android ID, phone identifiers, contacts, notifications, Wi-Fi/IP configuration, and location dumps.

### Focused evidence

| Path | Purpose |
|---|---|
| `.authorized-build/2d4ef414167a/evidence/oncar-open-cast-20260725T092421Z` | Opening Cluster Cast; stable PIDs, Display `0`, no opcode/crash |
| `.authorized-build/2d4ef414167a/evidence/oncar-retry-blocked-20260725T092705Z` | Immediate state after blocked Retry; no mutation |
| `.authorized-build/2d4ef414167a/evidence/oncar-retry-diagnostics-20260725T092907Z` | Durable Diagnostics proof: epoch `0`, tx/stable null, ledger `0` |
| `.authorized-build/2d4ef414167a/evidence/oncar-preflight-raw-20260725T093031Z` | Exact six preflight shapes and timings |

`uiautomator dump` returned `null root` on this ROM during the Cluster Cast screen. This is an external hierarchy limitation, not an app crash. Screenshots plus Activity/Window dumps are retained; do not spend another vehicle session trying to make uiautomator work.

---

## 3A. Architecture correction — missing cluster display is a normal cold-cast state

This section supersedes any wording elsewhere in this handoff that treats an absent named cluster display as an inherently abnormal recovery condition or as a reason the user must perform a separate Retry before choosing/casting an app.

### Verified V1 behavior

Direct source inspection of `ClusterCast.kt` proves why V1 could cast when no cluster display existed:

- `setAutoCast()` persisted one default package in `autoCastPkg`.
- `autoCastOnBoot()` was called after `BOOT_COMPLETED`, waited `25_000 ms`, required an affirmative stationary-speed observation, and called the same `cast(..., allowDestructive=false)` path used for manual casting.
- `cast()` first resolved/opened the target app and inspected the current cluster display.
- If a live cluster display already contained an app, V1 used the warm/hot-swap path.
- If there was no usable cluster display, V1 treated that as the normal **first/cold cast** path:
  1. resolve the vehicle profile;
  2. run its cast sequence (Seal DL3: `30 -> 16 -> 35` with the established settles);
  3. poll until the newly created `fission/xdja` display appeared;
  4. place the selected/default app on that display;
  5. commit `lastCastApp/casting` only after the app landed.
- Therefore V1 did not require a pre-existing virtual display and did not expose “create display” as a separate user workflow.

### What the original freeze bug actually was

The historical freeze was not caused by creating the display for the first cast. The source comments and prior vehicle evidence identify the dangerous path as **switching apps while a live virtual display and app transition already existed**, especially when the implementation:

- destroyed/recreated the virtual display during switch; or
- moved a transitioning task away while AOSP 10 had an unstable `DisplayContent`/window transition.

The validated design lesson is:

- **Cold first cast:** creating a fresh cluster display, then placing the first app, is the expected path.
- **Warm app switch:** keep the existing display alive; place the new app and safely retire/return the old app without destroying/recreating the display.

### V2 regression

V2 inverted that dependency:

1. Normal CAST/SWITCH planning requires an already discovered named cluster display.
2. With only Display `0`, observation becomes `Unknown(expected exactly one named cluster display)`.
3. App selection/cast controls are disabled.
4. A separate Retry/bootstrap workflow is exposed before the user can execute the original high-level intent.

The durable journal and verified-state architecture are still valuable, but making a pre-existing cluster display a prerequisite for the user's CAST intent is an architectural regression from the working V1 cold path. The two real-dump parser bugs (§4–§5) prevent the added bootstrap from running, but fixing those parsers alone would still leave the flow unnecessarily split and feature-incomplete.

### Correct target architecture

Model two legitimate starting states instead of treating one as generic Unknown:

1. **COLD_PRISTINE / no cluster display**
   - Exact supported vehicle profile, pristine durable state, main-only topology, and no unresolved journal.
   - This state is actionable for a user CAST intent or the approved default-app autostart intent.
   - The high-level flow journals and runs cold bootstrap, reaches durable `IDLE_VERIFIED`, then plans/executes placement of the selected/default app without requiring a second user click.
2. **WARM_VERIFIED / one cluster display**
   - Normal cast/switch uses the existing verified display.
   - Switching apps must not emit the cold create sequence or destroy/recreate the display.

The implementation may use two durable transactions (BOOTSTRAP followed by CAST) with an explicit stable boundary, but they must be orchestrated as one user-visible high-level intent. Safety requirements remain:

- five bootstrap rows persisted before opcode `30`;
- no app placement before durable `IDLE_VERIFIED`;
- unknown effect stops the chain;
- no blind retry;
- warm switch never recreates the display;
- Stop/compensation remains fenced and journaled;
- process restart never replays an issued opcode.

### UI and autostart implications

- “Retry connection” must not be the normal first-cast entry point. Reserve Retry for genuine transport failure/recovery after a failed intent.
- The user must be able to select an app while the system is cold/pristine.
- Pressing Cast with no display should run the safe cold-create-then-place flow automatically.
- Owner-confirmed V1 parity requires the persisted default app to run through the same high-level flow exactly once after boot when the approved safety gates pass.
- The phased/manual-only policy in §7A may be used as a temporary diagnostic rollout, but it is not the final feature-parity acceptance target unless the owner explicitly changes the requirement.

### Consequence for the next off-car plan

Do not treat `0.70` as merely a two-regex/parser patch and then declare Cluster Cast complete. The next spec/design review must cover:

- cold-pristine as an explicit actionable state rather than generic Unknown;
- composition of BOOTSTRAP -> stable `IDLE_VERIFIED` -> CAST for one selected/default-app intent;
- app selection availability before the display exists;
- exactly-once boot intent for the default app;
- warm-switch preservation of the existing display;
- parser corrections from the real dumps;
- removal/rewording of the misleading normal-path Retry/500 ms UX.

## 4. Proven root cause 1 — display heading boundary

Current code in `CastColdBootstrapPreflight.inspect()` first collects every trimmed line beginning with `Display `, excluding only `Display Devices:`. The real `dumpsys display` includes manager and power headings such as:

```text
DISPLAY MANAGER (dumpsys display)
Display Adapters: size=4
Display Devices: size=1
Display 0:
Display Power Controller Locked State:
Display Power Controller Configuration:
Display Power Controller Thread State:
Display Power State:
```

The failing capture produced seven candidates after excluding `Display Devices:` but only one actual numeric logical header (`Display 0:`). Current result:

```text
Blocked("display topology must contain exactly logical Display 0")
```

Required correction:

- Define candidates only by a full match of the existing numeric logical-header grammar: `Display N:`.
- Count the numeric candidates before integer conversion so an overflowing numeric ID still fails closed.
- Require exactly one candidate and parsed IDs exactly `[0]`.
- Ignore textual manager/adapter/power headings.
- Preserve all existing `mDisplayId` ambiguity checks.
- Never derive a logical ID from `mPhysicalDisplayId`, `uniqueId`, dimensions, order, or a textual heading.

---

## 5. Proven root cause 2 — AM stack/task parent shape

Real `am stack list` is block-structured:

```text
Stack id=27 ... displayId=0 userId=0
  taskId=31: com.byd.clusternav/... bounds=... userId=0 ...

Stack id=0 ... displayId=0 userId=0
  taskId=12: com.android.launcher3/... bounds=... userId=0 ...

Stack id=19 ... displayId=0 userId=0
  taskId=23: com.android.systemui/... bounds=... userId=0 ...
```

Current code scans each `taskId` line and requires exactly one `displayId` on that same line. All three real task lines have none, so this would become the next blocker after fixing display headings.

Required correction:

- Parse `am stack list` as stack blocks.
- Every line beginning with `Stack ` is a stack-header candidate and must contain exactly one parseable `displayId`.
- Require every stack display ID to be `0` during pristine cold-bootstrap preflight.
- Each `taskId` line must have a valid current parent stack and inherits that parent's display ID.
- Block malformed stack headers, missing task parents, non-main stack IDs, multiple display IDs, overflow, or ambiguous structure.
- Do not infer display ownership from task ID, package, bounds, visibility, ordering, or a global default.

The harvest proves all three current stack headers are valid and all parent display IDs are `0`.

---

## 6. Proven issue 3 — false `500 ms` status

Current UI path:

1. `runOperation()` sets `operationRequestedAt` and initial text.
2. It schedules `refresh()` after 450 ms.
3. Bootstrap raw preflight runs before a durable transaction/`operationPhase` exists.
4. After 500 ms, `CastUiRenderer` emits `Chưa nhận xác nhận trong 500 ms`.
5. The worker later returns the exact blocked reason via a Toast/status, but immediate `refresh()` overwrites it with the generic acknowledgement message.

This is not the preflight rejection itself. It is a presentation defect that hid the useful reason.

Required correction, without weakening Stop acknowledgement:

- Track a local bounded in-flight/result status for `runOperation()`.
- While the worker runs, render the local initial status instead of the generic 500 ms text.
- Completion is an acknowledgement: clear `operationRequestedAt`, preserve the exact result visibly for a short bounded interval, refresh controls immediately, then return to projected status.
- Keep the existing request-Stop 500 ms engine acknowledgement behavior unchanged.
- `ClusterCastActivity.kt` is currently 499 LOC; it must remain `<=500`. Use compact replacement or extract a helper rather than allowing file growth.

---

## 7. Remaining preflight fields already proven valid

Do not return to the vehicle to rediscover these:

```text
SDK                       29
manufacturer              BYD AUTO
brand                     BYD-AUTO
product/device/buildProduct DiLink3.0
logical display headers   Display 0 only (numeric grammar)
dumpsys display mDisplayId [0]
WM mDisplayId             [0]
AM stack parent IDs       [0, 0, 0]
active Android user       0
animation values          [0.5, 0.5, 1.0]
appops                     nonblank, ~484 ms in focused capture
```

Focused raw timings were all well below the per-call 15 s ceiling (AM ~135 ms, WM ~237 ms, display ~219 ms, profile ~140 ms, appops ~484 ms). The absolute deadline design remains necessary, but the observed blocker was parser shape, not command latency.

---


## 7A. Additional feature gaps reported on `0.69`

These are product-completeness gaps, not explanations for the pre-journal bootstrap block. They are now part of the handoff and must not be silently treated as implemented merely because the parser fix passes.

### 7A.1 App selection has labels but no application icons

Observed/current behavior:

- The Cluster Cast application grid renders text-only buttons such as app labels.
- The owner reports that recognizable application icons are missing when choosing a target.
- Existing screenshots support the text-only presentation; no icon acceptance test has been closed.

Required UX/design work:

- Render each app's icon together with its label in the app chooser and app-management surfaces.
- Use `PackageManager`/catalog evidence through one shared icon-loading path; do not duplicate package lookup logic in individual buttons/dialogs.
- Provide a deterministic fallback icon when a package has no loadable icon.
- Keep icon decoding/scaling off the UI-critical path and bound cache size; do not retain unbounded `Drawable`/`Bitmap` objects.
- Preserve enabled/disabled policy from the V2 projector. An icon must never make a blocked app actionable.
- Preserve readable labels, content descriptions, focus order, large touch targets, and the 960/1280 layouts.
- Add tests for icon present, missing-icon fallback, package removal/race, app-list refresh, accessibility label, and unchanged action gating.

Likely boundaries to inspect before planning:

```text
CastAppCatalog.kt / catalog model
ClusterCastActivity.kt app grid
CastAppManagerDialog.kt
activity_cluster_cast.xml variants or extracted app-tile view
```

Do not add this to the already constrained 499-LOC Activity without extracting an app-tile component/helper.

### 7A.2 No clear user-facing place to enable/show the floating control button

Observed/current behavior:

- `FloatingBubbleService.kt` exists in source, but the owner cannot find a clear UI location that enables, shows, hides, or explains the floating control.
- The Home and Cluster Cast screens captured on `0.69` do not provide an obvious bubble-control entry point or visible bubble state.
- Therefore the floating control is not a complete usable feature, regardless of service code existing.

Required UX/design work:

- Add one explicit, discoverable control surface (Home or Cluster Cast settings) for:
  - overlay permission status and deep link to the system permission screen;
  - enable/show floating button;
  - hide/disable floating button;
  - current state (`disabled`, `permission required`, `shown`, `service unavailable`);
  - optional position reset if the bubble is off-screen.
- Define and document which actions the bubble exposes. At minimum, each action must route through the same V2 coordinator/policy/store as the full screen; no direct shell, legacy Cast call, raw opcode, or parallel journal is allowed.
- The bubble must not bootstrap, cast, switch, recover, or Stop automatically merely because its service starts.
- Stop from the bubble must retain the existing bounded acknowledgement/fence semantics.
- Persist the user's bubble preference and last safe position atomically; clamp restored position to the current display bounds.
- Handle overlay-permission revocation, service/process restart, orientation/size changes, duplicate start requests, and app upgrade without spawning duplicate bubbles.
- Add UI/service/lifecycle tests and one later on-car check that the bubble does not obscure critical vehicle controls.

Open product decisions that require plan approval:

- Exact screen/location of the bubble toggle.
- Default off versus default on (safe recommendation: default off until explicitly enabled).
- Allowed bubble actions while the durable state is pristine, active, recovering, or manual-required.
- Whether the bubble should appear when ClusterNav is foreground, background, or only during an active verified cast.

### 7A.3 Missing autostart and V1 default-app projection parity

Owner-confirmed historical behavior:

- V1 allowed the owner to choose and persist a **default application**.
- After the head unit/app startup path ran, V1 automatically projected that selected default application to Cluster Cast.
- `0.69` does not expose or complete this feature parity: there is no clear default-app selection/status in the current V2 UI and no verified startup path that places the chosen app on the cluster.
- Treat this as an owner-confirmed product requirement. Verification of the historical V1 implementation details in source is still required before reusing any idea from it.

Required user-facing behavior:

1. **Default-app selection.** The app chooser/app manager must allow setting one eligible package as the default, changing it, and clearing it. Show icon + label and visibly mark the current default.
2. **Independent auto-project preference.** Provide an explicit `Tự chiếu app mặc định khi khởi động` enable/disable control. Choosing a default app must not silently enable autostart, and disabling autostart must not erase the selected default.
3. **Durable preference.** Persist the package name, enablement flag, and relevant policy version atomically. If the package is removed, disabled, no longer launchable, or no longer policy-eligible, fail closed and show a recoverable reason instead of selecting another app silently.
4. **Startup orchestration.** After a real head-unit boot/package replacement/user-available event, initialize the read-only control plane, Navigation/HUD preferences, and optional bubble idempotently; then evaluate the default-app intent exactly once for that boot generation.
5. **Auto-project execution.** App placement must still use the V2 planner, executor, durable journal, policy, observation, and verification path. No legacy Cast call, direct shell shortcut, raw opcode, or second executor is allowed.
6. **No blind loop.** A blocked, unknown-effect, recovery-required, or verification-failed startup attempt must remain recorded and must not retry on Activity resume, repeated broadcasts, watchdog ticks, or process recreation. The user gets a clear status/Diagnostics action.
7. **Protected-session policy.** Explicitly decide whether CarPlay/Android Auto may be selected as the default. Until approved and tested, protected projection packages must remain fail-closed rather than being treated like normal apps.

Critical V2 safety decision — automatic virtual-display bootstrap:

- The current reviewed V2 contract deliberately permits cold bootstrap only after explicit **Retry**. The requested V1 parity may require automatically creating the missing cluster display (`30 -> 16 -> 35`) before projecting the default app.
- Do not silently remove the Retry-only boundary or copy the legacy V1 startup implementation. The canonical spec must explicitly approve one of these policies:
  - **Phased/safe policy:** startup auto-projects the default app only when a durable `IDLE_VERIFIED` cluster display already exists. If the display is missing, startup exposes the Cluster Cast/bubble status and requires one explicit Retry.
  - **Full V1-parity policy:** one opt-in, exactly-once-per-boot cold bootstrap may run automatically on the exact Seal DL3 profile, then project the default app only after durable `IDLE_VERIFIED`. This requires a dedicated safety review and on-car matrix for journal-before-opcode, deadline, Stop fencing, compensation, process death, protected sessions, and no retry loops.
- Under either policy, **no app placement may occur before durable `IDLE_VERIFIED`**, and a startup failure must never trigger a second automatic attempt in the same boot generation.

Autostart responsibilities that remain independently testable:

1. **Read-only control-plane initialization.** Initialize store/catalog/status without mutation.
2. **Navigation + HUD restoration.** Restore only persisted enablement choices; do not invent an active navigation session.
3. **Floating-button restoration.** Restore only when explicitly enabled and overlay permission remains granted.
4. **Default-app intent.** Evaluate the persisted package and auto-project preference once, then follow the approved phased or full-parity policy.

Required design/verification:

- Specify exact Android triggers (`BOOT_COMPLETED`, package replacement, and user-unlocked availability where applicable) and confirm what this Android 10 ROM delivers after a physical power cycle.
- Use one idempotent startup coordinator; duplicate broadcasts/process recreation must not duplicate services, bubbles, workers, journals, listeners, bootstrap transactions, or CAST intents.
- Never block a broadcast receiver thread with DADB, package scans, sleeps, or dumpsys calls; hand off bounded work to the approved control plane.
- Preserve CarPlay/Android Auto continuity and never force-stop/move/resize a protected projection task merely because startup ran.
- Test default selection/change/clear, package removal, invalid launcher, autostart disabled, duplicate triggers, process death, package replacement, permission revocation, existing `IDLE_VERIFIED`, missing display, unresolved journal, unknown effect, and exactly-once-per-boot behavior.
- Physical-power-cycle on-car verification is mandatory; `adb reboot` is not equivalent evidence.

### 7A.4 Scope decision before implementation

The immediate four-file `0.70` parser/status correction is small and backed by complete vehicle evidence. Icons, floating control, and V1-parity default-app autostart are a broader product increment touching UI, service lifecycle, permissions, persistence, boot behavior, and potentially the explicit-Retry safety contract.

Before coding, explicitly choose and document one of these plans:

- **Recommended split:** `0.70` fixes bootstrap preflight + status only. First prove manual cold bootstrap on-car. A separately approved candidate then implements icons, floating control, default-app selection, and the chosen phased/full-parity autostart policy.
- **Combined candidate:** update the canonical spec Requirements/Design/Tasks first, explicitly approve whether automatic cold bootstrap is allowed, then implement all gaps with separate acceptance tests. Do not append them opportunistically to the four-file parser patch.

Until that decision is approved, all three items in §7A remain **MISSING / NOT VALIDATED**.

## 8. Current source status

Current source still declares:

```text
versionCode = 69
versionName = "0.69"
```

Current cold-bootstrap implementation before this newly discovered fix already includes:

- Exact Seal DL3 vehicle-facts gate.
- Explicit Retry-only bootstrap entry.
- Five fixed typed commands (`30/16/35`, compensation `18/0`).
- Full five-row ledger before opcode `30`.
- ISSUED-before-dispatch and OBSERVED-after-clean-return.
- Single executor lease/journal/gateway.
- Aggregate absolute deadline across all observation commands.
- Per-call/mutation-only DADB cancellation and immediate pre-shell checks.
- Stop fence between compensation `18` and `0`.
- Two equal Known `IDLE_CLEAN` samples before `IDLE_VERIFIED`.
- Authoritative logical cluster block and same-block `1920x720`, density `180` verification.
- Atomic final fold and restart/no-replay rules.

Last clean off-car baseline before installation:

```text
46 JVM suites
356 tests
0 failures
0 errors
0 skipped
senior review: APPROVED, 0 actionable P0-P3
security scan: CLEAN
```

The attempted post-vehicle patch sub-agent was cancelled. Verify the four target files before editing, but assume **no correction was applied**:

```text
app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastColdBootstrap.kt
app/src/main/java/com/byd/clusternav/modules/clustercast/ClusterCastActivity.kt
app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastColdBootstrapTest.kt
app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastRendererContractTest.kt
```

No commit, staging, push, or merge was performed.

---

## 9. Off-car execution plan

### Step 1 — patch only the proven boundary defects

1. Read `docs/specs/cluster-cast-rebaseline.html` and this handoff.
2. Patch numeric logical display-header selection.
3. Patch AM stack-block/task-parent validation.
4. Patch bounded local operation-status overlay while preserving Stop acknowledgement.
5. Add sanitized real-shape regressions; do not import evidence paths or private endpoint data into source fixtures.

### Step 2 — focused and full validation

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew --offline :app:testDebugUnitTest \
  --tests 'com.byd.clusternav.modules.clustercast.v2.CastColdBootstrapTest' \
  --tests 'com.byd.clusternav.modules.clustercast.v2.CastRendererContractTest'

./gradlew --offline :app:testDebugUnitTest
```

Required regressions:

- Real display manager/power headings are ignored.
- Exactly one numeric `Display 0:` passes.
- Multiple numeric, nonzero, overflow, and malformed numeric logical headers block.
- Real Stack-parent/task-child shape passes.
- Non-main stack, malformed/multiple display IDs, task without parent, and overflow block.
- Long bootstrap preflight keeps exact local in-flight/result status.
- Completion clears `operationRequestedAt` and refreshes controls.
- Request Stop retains its existing 500 ms acknowledgement behavior.
- V2/legacy static isolation remains green.
- Every modified source/test file remains `<=500` LOC.

### Step 3 — mandatory independent senior review

Review both ends of these boundaries together:

- `dumpsys display` producer shape → cold preflight parser.
- `am stack list` stack/task shape → main-only authorization.
- `runOperation` local state → renderer acknowledgement → final status.
- Raw preflight → locked transaction creation → five-row ledger → opcode `30`.

Reviewer patches findings, reruns focused/full tests, and loops until zero actionable P0-P3 findings.

### Step 4 — new candidate identity

Only after review is clean:

1. Bump to `0.70 (70)`.
2. Rerun full JVM suite.
3. Create a new non-overwriting exact-source manifest; include/inventory this handoff.
4. Run full exact-source security scan.
5. Stop and request separate authorization for one isolated build/install.

Never reuse source ID `2d4ef414...c1da`, APK hash `b1f13562...7c0c`, or the `0.69` isolated output path for the corrected source.

---

## 10. Next on-car session — keep it short

Do not schedule another vehicle session until the reviewed `0.70` exact source and isolated APK are ready.

Proposed minimal matrix:

1. Install + Home smoke; verify exact installed hash/version/certificate.
2. Open Cluster Cast once; confirm Retry + Diagnostics.
3. Start one listener.
4. Press Retry once; wait for completion.
5. Stop listener and analyze all evidence in one batch:
   - five ledger rows persisted before opcode `30`;
   - `30 -> 16 -> 35` order and settles;
   - exactly one logical `fission/xdja` display;
   - same-block `1920x720`, density `180`;
   - two equal Known `IDLE_CLEAN` samples;
   - durable `IDLE_VERIFIED`;
   - no crash/ANR/orphan task/window;
   - CarPlay PID/session continuity.
6. Only after bootstrap PASS, run separate listener/action pairs for selecting CarPlay, Cast, switch, and Stop.

No blind retries. If Retry fails, do not press it again; collect Diagnostics plus one consolidated harvest and leave the vehicle.

---

## 11. Hard safety boundaries

- No auto-bootstrap from Activity lifecycle, Bubble, Diagnostics, boot, watchdog, or receiver.
- No raw/user-supplied opcode path.
- No legacy Cast runtime import or parallel journal/executor.
- No app placement before durable `IDLE_VERIFIED`.
- Unknown effect means no next forward opcode and no compensation.
- Compensation is one attempt only; failed/unknown `18` means no `0`.
- No `adb reboot` as physical-reboot evidence.
- Do not overwrite historical APKs or prior exact-source manifests.
- Every code change requires a new exact-source identity and separately authorized replacement build/install.

---

## 12. Resume instruction

Start the next session with:

```text
Read docs/_handoff/HANDOFF-2026-07-25-v069-bootstrap-blocked.md.
Work off-car only. Patch the two proven preflight boundary shapes and the false 500 ms status, run focused/full tests and senior review, prepare reviewed 0.70 exact source, then stop before build/install authorization.
```
