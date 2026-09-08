# Two-Track Autonomous Off-Car Progress

Owner: Đăng Khôi · `dangkhoi`  
Authorization: `AUTONOMOUS_OFFCAR_APPROVED stages=2-10`

## Completed

- Stage 2 PASS; exactSourceId `4a1441a6bdc8887a0efb2521d792fed051beb1aa241739e5a2fc17e2598389d6`.
- Wave 3 Navigation runtime: authoritative listener → coordinator ingest, durable SharedPreferences frame store, independent lane/HUD adapters and physical entry points.
- Home reduced to exactly two read-only/dispatcher cards; no direct Cast mutation and no DR surface.
- Cast dry transcript is pure/typed and has no gateway execution.
- Wave 4 dark core: persist-before-call journal, sole mutation lease, deadline/unknown-effect fencing, one compensation fence across restart, typed context-independent destructive recovery gate, observation-only stable convergence.
- Dead Reckon/mock-location product runtime quiesced without deleting rollback-readable source: removed manifest permissions/service, module registrations, Rebind auto-start/cleanup, preference and Home control edge.

## Focused validation so far

- Navigation + Home contract tests: PASS.
- Cast dry planner/manifest tests: PASS.
- Cast blocked-I/O/restart/recovery/normal convergence tests: PASS.
- Exact-two-runtime DR retirement tests: PASS.

## Additional exact paths declared before remaining implementation

- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastAndroidRuntime.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastUiRenderer.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastGeometry.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastRendererContractTest.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastWarmMatrixTest.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastCarPlaySliceTest.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastAndroidAutoSliceTest.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastGeometryTest.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastLifecycleTest.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastDiagnosticsContractTest.kt`

These paths are within the owner-approved product scope and are added to the autonomous union allowlist by this progress record. No APK has been built and no install/car command has run.

## Waves 5–10 off-car closure progress

- Normal Cast activation/verification/Stop, throw fencing, restart no-replay and transaction-scoped compensation are implemented.
- Warm switch now targets incoming placement and durable outgoing return separately; unqualified `ACTIVE_MULTI` cannot become clean verified state.
- CarPlay/Android Auto target classes use resume-only protected plans; V2 contains no `am display move-stack`.
- Android runtime owns one ephemeral DADB session per call and closes it deterministically; package/component/display inputs fail closed.
- Activity is the sole V2 mutation dispatcher. Bubble and diagnostics project canonical state; Bubble emits an explicit Activity Stop intent with separate 64dp Stop/Menu controls.
- Session-sticky rollout/action ownership gates all Android mutation. Pristine observed-clean state migrates once to V2; active/unknown legacy state remains read-only.
- V2 app catalog migrates favorites/protected policy one-way without deleting rollback-readable legacy preferences.
- Target-bound durable geometry adjustment is implemented. PARKED/MOVING/UNKNOWN is diagnostic-only and cannot alter `ADJUST`, setup, profile or recovery actions.
- Adaptive base/w960dp/w1280dp app-manager resources and labeled 56dp/64dp controls are present.
- Boot/update lifecycle rehydrates V2 only; legacy Cast watchdog/reconcile/auto-cast paths are disconnected.
- Read-only diagnostics exports observation and durable journal without repair/reset mutation.
- Vehicle scripts and checklist are prepared under `scripts/vehicle/` and `docs/diagnostics/VEHICLE-TEST-V2.md`; none has been executed.

## Latest validation

- Complete `:app:testDebugUnitTest`: PASS (`BUILD SUCCESSFUL in 58s`).
- Cast V2 focused suite after rollout/catalog/accessibility changes: PASS.
- Static boundary audit: PASS for ≤500 LOC target files, no cross-track imports, no V2 move-stack, no active legacy mutation routes, no `catch(Throwable)`, and immutable historical APK hashes.
- Android lint was attempted offline but could not resolve uncached pinned artifact `com.android.tools.lint:lint-gradle:31.5.2`; compile/resources/JVM validation remains green. No network dependency download was authorized solely to fill this optional cache gap.
- No APK, install, vehicle ADB/dadb, commit, push or merge operation has run.
