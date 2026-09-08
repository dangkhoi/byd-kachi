# Two-Track Final Off-Car Closure Review

Owner: **Đăng Khôi · `dangkhoi`**  
Review date: **2026-07-25**  
Review mode: direct source/boundary review; blocking sub-agents remained disabled per owner workflow.  
Verdict: **OFF-CAR P1 APPROVED · WAITING FOR SEPARATE REPLACEMENT BUILD AUTHORIZATION**

> This verdict does **not** authorize an APK build, install, vehicle ADB/dadb, vehicle test, commit, push, merge or release. The prior one-build authorization was consumed. Exact source `d808db00313c9ca6ae5ddd88068859cbe8719af1d853d4886d861b971362093a` and APK SHA-256 `1b9c016273296454c9fd0ac88bb51dd8c7447b8b7d60b113d689eb7eb9d6b184` remain invalid and prohibited.

## Scope checklist

- ✅ Exactly two product/runtime tracks; Dead Reckon/mock-location product wiring remains quiesced.
- ✅ Navigation owns one source/session/frame/freshness coordinator; Cluster lane and HUD keep independent workers, health and output controls.
- ✅ Cast V2 owns envelope/store, epoch, journal, planner, bounded DADB gateway, executor lease, verification, recovery and adjustment workspace.
- ✅ PARKED/MOVING/UNKNOWN interaction context is diagnostic-only and cannot alter any feature or recovery action.
- ✅ Explicit phone-session markers—not package hints—prove connected/disconnected truth.
- ✅ Observation includes target visibility/residue, target-scoped PIP, animation values and typed active Android user profile; incomplete/ambiguous truth fails closed.
- ✅ Stable UI is rendered only when independent observation matches target/display/geometry/PIP/animation/profile field-by-field; Unknown cannot render false idle.
- ✅ Normal/CarPlay/Android Auto/warm-switch policy is typed; protected sessions are resume-only and no V2 `am display move-stack` exists.
- ✅ Disconnected-sink recovery requires exact owner, explicit disconnected phone marker, two stable samples, consequence confirmation and one durable attempt.
- ✅ Stop is preemptive, clears pending selection, returns the target, independently proves zero VD tasks, restores exact persisted display baseline, and requires two-sample convergence.
- ✅ Geometry Apply/Undo/Restore/Reset/Done is target/task/display/epoch bound; accepted geometry changes only on Done; compensation restores exact prior task/display geometry.
- ✅ Rapid in-flight app selections persist one latest durable target and drain only at a verified stable point; Stop supersedes the queue.
- ✅ App manager persists favorites and protected policy; adaptive base/w960dp/w1280dp resources restore selection/scroll/focus.
- ✅ Bubble persists/fences Stop before Activity launch and keeps Activity as sole planner/executor mutation dispatcher.
- ✅ Cast owns a separate same-boot lifecycle watchdog; Navigation watchdog is not shared. Revalidation observes only and never blindly replays mutation.
- ✅ Pending rollout/action ownership is durable and applies rollback only at idle/no-transaction boundaries.
- ✅ Diagnostics remains read-only/partial; legacy Cast remains rollback-readable and physical deletion remains deferred.
- ✅ Vehicle checklist/scripts cover the completed P1 matrix but reject every candidate before device selection until a replacement build is separately authorized.

## Senior review findings patched in this closure loop

- **[P0/P1] False stable rendering:** Unknown/divergent observation could render a persisted idle/active state. Added exact independent convergence checks and typed unsupported-profile routing.
- **[P1] Unbounded DADB workers:** cached thread pool could grow indefinitely after uninterruptible calls. Replaced it with two bounded workers, queue capacity four and fail-closed rejection.
- **[P1] Cross-call cancellation:** timeout of one DADB call closed every active Cast session. Timeout now owns/closes only its call session; global Stop fence still closes all.
- **[P1] Shared lifecycle watchdog:** Cast revalidation was attached to Navigation watchdog. Added non-exported Cast-owned receiver/alarm with one in-flight worker.
- **[P1] Lost rapid selection:** a second app choice during an operation was rejected instead of persisted. Added durable latest-target replacement, stable-boundary drain and Stop supersession.
- **[P1] Geometry compensation shape:** compensation omitted exact target and mapped display density to task bounds. It now preserves exact stable target, prior geometry and command kind.
- **[P1] Incomplete Stop restoration:** Stop verified baseline but did not plan restoration. It now requires persisted baseline geometry, proves zero tasks, then restores exact non-main display size/density before two-sample terminal verification.
- **[P1] Generic phone-session request:** targeted phone-session observation could enter the generic constructor. Both targeted read kinds now require validated package factories.
- **[P1] Documentation drift:** final plan/current guide/active matrix still contained superseded PARKED gates and stale build authorization wording. Current-facing artifacts now match the owner amendment.

Remaining actionable findings: **0 P0 / 0 P1 / 0 P2 / 0 P3**.

## Boundary-shape verification

| Producer → consumer | Checked shape | Verdict |
|---|---|---|
| Shell request factory → DADB gateway | command kind, validated package, deadline, bounded worker/queue, per-call cancellation | PASS |
| AM/WM/display/profile/settings/app-ops → `ObservedState` | target/task/display, residue visibility, geometry, PIP block, animations, active profile nullability | PASS |
| Phone service dump → target classification/recovery | explicit marker nullable truth; package hint never proves connection | PASS |
| Planner → transaction → mutation request → Android command | target package/class, expected display/target, requested/prior geometry, epoch/deadline, command kind | PASS |
| Transaction/baseline → verification/stable session | exact Stop/Recover and geometry postconditions; two identical samples | PASS |
| Envelope + observation → projector → renderer → Activity/Bubble | stable convergence, every action enum, disabled reasons, acknowledgement | PASS |
| App selection → pending envelope → stable drain | one latest package, transaction/Stop guards, atomic clear | PASS |
| Boot dispatcher → Cast lifecycle receiver → lifecycle coordinator | separate watchdog ownership; read-only observation; no execute/requestStop | PASS |
| Rollout decision → durable envelope → Activity dispatch | sticky owner/effective UI/pending rollback | PASS |
| Adaptive XML IDs → Activity consumer | identical 16-ID shape across base/w960dp/w1280dp; selection/scroll/focus restoration | PASS |

## Validation evidence

- Fresh forced full JVM run: **45 XML reports, 327 tests, 0 failures, 0 errors, 0 skipped**; `BUILD SUCCESSFUL` in 1m07s.
- Canonical Cast UI schema SHA-256: `d013122ee274a4c1b2d0507ac0337fc172c8a77b8a7be415ee9095541312d892`.
- Source guardrails: reviewed current V2/Navigation/Android owner files are all ≤500 LOC; `ClusterCastActivity.kt` is 496 LOC.
- `git diff --check`: PASS. Adaptive XML parses; all six vehicle scripts pass `bash -n`.
- Static safety: V2 move-stack hits = 0; orchestrator = `PAUSED`; prohibited APK hash remains exactly `1b9c016273296454c9fd0ac88bb51dd8c7447b8b7d60b113d689eb7eb9d6b184`.
- Vehicle guard: every entry script calls unconditional candidate rejection before `select_device`; no vehicle script was executed.
- Local sensitive-data scan: 227 candidate text files, 0 BLOCK/WARN. Two INFO-only placeholders: the documented macOS `<user>` path and one braced build-evidence path. No automated gitleaks/trufflehog/detect-secrets binary is installed.
- No dependency or framework was added/changed in this closure. Existing JDK/Gradle/Kotlin warnings are pre-existing outside the P1 batch.

## Deferred evidence and authorization state

- Android lint remains an environment limitation because the offline cache lacks pinned `com.android.tools.lint:lint-gradle:31.5.2`; compile/resources/full JVM validation is green.
- Exact-build and on-car evidence are **NOT STARTED**. Active Android user identity versus OEM cluster-profile semantics remains a mandatory vehicle observation.
- The replacement exact-source review manifest is generated only after all closure artifacts are final. Its hash identifies source; it does not authorize build.
- Next allowed state: `WAITING_FOR_REPLACEMENT_BUILD_AUTH`.
