# ClusterNav Two-Track Senior Review Verdict

Owner: **Đăng Khôi · `dangkhoi`**  
Verdict: **BLOCKED / INCOMPLETE — DO NOT INSTALL OR VEHICLE-TEST**  
Orchestrator: **PAUSED**

## Invalidated candidate

The first source patch invalidated both artifacts below. Their bytes remain immutable, but they are prohibited for vehicle use:

- Exact source: `d808db00313c9ca6ae5ddd88068859cbe8719af1d853d4886d861b971362093a`
- APK SHA-256: `1b9c016273296454c9fd0ac88bb51dd8c7447b8b7d60b113d689eb7eb9d6b184`
- Build authorization: **1/1 consumed**; no rebuild was run.

## Patched blockers

- Navigation disconnect now immediately publishes `SOURCE_DISCONNECTED` and independently marks lane/HUD stale without clearing the durable session.
- Lane inactive/stale self-heal no longer clears HUD; a visible, persistent lane-only toggle now supports the N3 matrix.
- Cast runtime is process-singleton; Activity is `singleTask`; UI/Bubble/diagnostics/lifecycle no longer create competing mutation leases or close a shared gateway.
- Stop synchronously persists `stopRequested`, bumps the durable epoch, fences in-flight transport without waiting for the mutation lease, blocks new non-Stop intents, and clears the flag only after verified restoration.
- DADB create/shell/resolve/display operations now use enforced future deadlines; timeout/fence cancels tasks and closes active sessions.
- Plans bind exact display identity; parser extracts task ID and available geometry; verification requires two identical samples and rejects missing identity/geometry. Stop checks captured baseline geometry.
- Same-target normal recast no longer force-stops/restarts. Launcher preflight uses PackageManager before journal creation. Fresh normal launch declares display, fullscreen window mode and clear-task semantics.
- Projection-session `null` is fail-closed instead of being treated as connected.
- Existing envelopes are boot-ID fenced: matching stable truth requires two equal samples; transactions move to recovery without replay.
- UNKNOWN/MOVING context no longer exports idle Cast/app-management/deep-setup actions; disabled reasons are typed.
- Collector now binds `exactSourceId` to the exact-source manifest byte hash.
- Vehicle scripts are intentionally fail-closed until a replacement candidate is separately authorized.

## Remaining P1 scope blockers

1. **Geometry / UX8 / R4:** Android gateway still has no implemented apply/reset command contract carrying target-bound geometry, and there is no durable AdjustmentDraft workspace with Apply, Undo-last, Restore-entry/Cancel, Reset-default, Done/Back and process-death semantics.
2. **Trusted PARKED truth / UX6:** no independently observed vehicle PARKED provider exists. The patch safely disables affected actions; therefore first Cast/deep setup cannot be approved for vehicle use until the provider/profile contract is designed and proven.
3. **Protected session evidence / R3:** CarPlay/Android Auto connection truth is not independently observed. Package-name hints now fail closed, leaving protected slices unavailable rather than unsafe.
4. **Observation completeness / R7:** protected residue, PIP and animation/profile restoration are not fully parsed/read back. `ACTIVE_DEGRADED` and complete Stop baseline proof are therefore not vehicle-ready.
5. **Action boundary / UX5–UX10:** projector/renderer export the closed action set, but Activity still lacks usable Adjustment, eligible recovery, profile setup, phone-disconnect guidance and full favorites/policy management flows.
6. **Adaptive UI / UX12:** required base stacked, `layout-w960dp` and `layout-w1280dp` resources with scroll/focus restoration are absent; integer-only column qualifiers do not satisfy the accepted requirement.
7. **Lifecycle/rollout:** same-boot sleep/wake re-observation and durable pending-rollout persistence remain incomplete.
8. **Bubble:** Stop delegates to Activity; durable acknowledgment is synchronous once Activity handles it, but the Bubble-to-Activity hop itself has no proven ≤500ms bound.
9. **Vehicle kit:** scripts are correctly blocked now. A replacement kit must bind installed package/version/certificate and add geometry, PARKED/deep-setup and repaired protected-session cases.

## Verification

- Full JVM: **43 reports, 307 tests, 0 failures, 0 errors, 0 skipped**.
- Focused Navigation, Cast V2, Stop-fence, lifecycle, UI and collector suites: PASS.
- Vehicle scripts: `bash -n` PASS; invalidated-candidate gate rejects before device selection.
- Changed source files over 500 LOC: 0.
- `git diff --check`: PASS.
- Sensitive-data scan across senior-review changes: 0 findings.
- Candidate APK remains 3,095,497 bytes with SHA-256 `1b9c…b184`; historical APKs remain SHA-256 `8f5901…002`.
- No APK build, install, vehicle ADB/dadb execution, commit, push, merge, reset, clean or branch switch occurred.

## Next authorization boundary

Do not authorize a replacement build yet. First close every remaining P1 above, rerun full review and tests, generate a new exact-source manifest, and re-review the vehicle kit. Only then may the owner issue a separate explicit one-build authorization.
