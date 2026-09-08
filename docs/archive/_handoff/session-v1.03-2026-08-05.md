# ClusterNav v1.03 — Session Handoff 2026-08-05

**Date:** 2026-08-05 10:55 +07 · **Owner:** Đăng Khôi · `dangkhoi`

## Session summary

### Completed this session

| Stage | Status | Work done |
|-------|--------|-----------|
| T2 Navigation | ✅ DONE | Bounded HUD owner, bounded speed-sign owner, persistence fail-closed, SPEED_SIGN enum, exhaustive when |
| T3 Widget | ✅ DONE | Per-slot freshness, speed-sign stale→clear, grant instruction, orphan ID reconciliation (pre-existing) |

### On-car test (Seal, v1.02, 5 minutes)

- VietMap widget bind: ✅ PASS (host listening, data flowing, IDs active)
- `BydHal.writeSpeedLimit(50/30/120)` HAL write: ✅ rc=-2147482648 (0x80000008)
- **Speed limit NOT rendering on cluster display** — even after disabling camera speed recognition. The ADAS feature `ADAS_TRAFFIC_LIMIT_SPEED_STATUS_PROMPT` (0x4F40201D) sets without error but the cluster instrument ignores the value. Root cause unclear — may need different feature, or a status-enable sequence before value write. Deferred to T8.
- TestHalReceiver debug tool deployed and verified functional.

### Still remaining

| Stage | Status | Scope |
|-------|--------|-------|
| T4 Cast core | ⬜ | Extract transport/operation owner/stack parser/postcondition verifier from SimpleCastCoordinator (624 LOC). CP/AA enforcement. Bounded queue. |
| T5 Cast UI | ⬜ | Extract Bubble renderer/gesture. 48dp zones. Home autostart/geometry. Diagnostics replacement. |
| T6 Test rebaseline | ⬜ | Fix 46+ test failures (frame shape expansion + V2 contract removal). |
| T7 Consolidate | ⬜ | Senior review (per-boundary). Security scan. TestHalReceiver removal. |
| T8 Vehicle | ⬜ | On-car evidence. Speed-sign investigation. |

## Current working tree state (beyond HEAD)

### Build/toolchain (from prior session)
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 9.6.1 + pinned SHA-256
- `build.gradle.kts` — AGP 9.3.1; Kotlin JVM 2.4.10
- `settings.gradle.kts` — clean syntax
- `core/build.gradle.kts` — Kotlin JVM + toolchain 17 + JUnit 6.1.2 BOM
- `car-integration/build.gradle.kts` — Kotlin JVM + dadb 2.0.0 + JUnit 6.1.2 BOM
- `app/build.gradle.kts` — compileSdk/targetSdk 37; version 1.03; dadb 2.0.0; JUnit 6.1.2 BOM

### T2 Navigation
- `core/src/main/kotlin/.../navigation/NavigationModels.kt` — `SPEED_SIGN` in NavigationOutputTarget
- `core/src/main/kotlin/.../navigation/ClusterLaneAdapter.kt` — BoundedNavigationOutputWorker now public
- `core/src/main/kotlin/.../navigation/NavigationSessionCoordinator.kt` — exhaustive when for SPEED_SIGN
- `core/src/test/.../NavigationSessionCoordinatorTest.kt` — entries.size 2→3
- `app/src/main/java/.../NavigationHudOwner.kt` — NEW (114 LOC)
- `app/src/main/java/.../NavigationSpeedSignOwner.kt` — NEW (91 LOC)
- `app/src/main/java/.../ClusterBroadcaster.kt` — removed hudExec+dedup, delegates to owners (244 LOC)
- `app/src/main/java/.../NavRepository.kt` — persistence load wrapped in try/catch

### T3 Widget
- `core/src/main/kotlin/.../vietmapwidget/VietMapWidgetModels.kt` — per-slot freshness fields
- `app/src/main/java/.../vietmapwidget/VietMapWidgetBridge.kt` — independent freshness computation + grant instruction (500 LOC)
- `app/src/main/java/.../NavNotificationListener.kt` — uses `speedFreshness` per-slot

### Debug (remove before release)
- `app/src/main/java/.../TestHalReceiver.kt` — broadcast receiver for HAL testing
- `app/src/main/AndroidManifest.xml` — TestHalReceiver registration

## Compile verification

```
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
./gradlew --no-daemon :app:compileDebugKotlin → BUILD SUCCESSFUL
```

Core test compile has pre-existing failures from frame shape expansion (T6 scope).

## Resume instructions

1. Open this file to confirm current position.
2. Verify compile: `JAVA_HOME=… ./gradlew :app:compileDebugKotlin`
3. Read execution prompt: `docs/_handoff/v1.03-remaining-execution.md`
4. Continue from **Stage 3 (T4)**: read `SimpleCastCoordinator.kt` (624 LOC, needs splitting ≤500 LOC per file)
5. Key T4 extractions:
   - `CastTransport` — bounded shell execution, typed timeout/error
   - `CastOperationOwner` — one active + one queued, Stop never blocked
   - `CastStackParser` — parse `am stack list`, typed result
   - `CastPostconditionVerifier` — verify landing/resize/return
6. After T4: T5 (Bubble/Home) → T6 (test fix) → T7 (review+scan)
7. Every batch: ≤5 files, compile after, no blocking sub-agent.

## Risks

- `SimpleCastCoordinator` 624 LOC: must split but tight coupling between state machine steps
- Core test failures: 9+ files need `routeRemainingMeters`/`routeRemainingSeconds`/`arrivalClock` params added
- Speed-sign HAL: rc=0x80000008 does not display — may need entirely different approach at T8
- `VietMapWidgetBridge.kt` at exactly 500 LOC — any future addition requires extraction first
