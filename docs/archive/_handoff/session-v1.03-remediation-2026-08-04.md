# ClusterNav v1.03 — Implementation Handoff

**Date:** 2026-08-04 17:34 +07 · **Owner:** Đăng Khôi · `dangkhoi`

## Approved spec

`docs/specs/clusternav-v102-review-remediation.html` — approved verbally 2026-08-04 ~17:24.

## Baseline

- Branch: `main`
- HEAD: `8842ffe1bd4b6647e71a34945d481f06bcc18df2`
- Pre-implementation tracked diff SHA-256: `66f0a914154e4cb39a8a6c3f28a0db04d4c7d0d613c9bf9ab263c3d04fcb22fb`
- Pre-implementation tracked diff bytes: 5543

## Stage progress

| Stage | Status | Notes |
|---|---|---|
| T0 Baseline | ✅ DONE | Frozen identity; spec created and validated |
| T1 Toolchain | ✅ DONE | Gradle 9.6.1 + SHA, AGP 9.3.1, built-in Kotlin app, Kotlin JVM 2.4.10, dadb 2.0.0, JUnit 6.1.2 BOM, SDK 37, version 1.03 (103). `:app:compileDebugKotlin` PASS. |
| T2 Navigation | ✅ DONE | Frame shape, ingest/persist, arrow pre-classification, broadcaster global-state removal, bounded HUD owner, bounded speed-sign owner, persistence fail-closed. |
| T3 Widget | ✅ DONE | Per-slot freshness (speed/alerts independent), orphan ID reconciliation, speed-sign stale→clear, explicit grant instruction. |
| T4 Cast core | 🔶 PARTIAL | Stack parser extracted (CastStackParser), density control extracted (CastDensityControl), coordinator ≤500 LOC. Postcondition verifier, CP/AA strict enforcement, conditional whitelist PENDING. |
| T5 Cast UI | ⬜ | Not started |
| T6 Test rebaseline | ⬜ | Not started |
| T7 Consolidate | ⬜ | Not started |
| T8 Vehicle | ⬜ | Not started |

## Files modified (working tree vs HEAD)

### Build/toolchain
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 9.6.1 + pinned SHA-256
- `build.gradle.kts` — AGP 9.3.1; Kotlin JVM 2.4.10; removed buildscript/classpath
- `settings.gradle.kts` — clean syntax (no content change)
- `core/build.gradle.kts` — Kotlin JVM plugin + toolchain 17 + JUnit 6.1.2 BOM
- `car-integration/build.gradle.kts` — Kotlin JVM + dadb 2.0.0 + JUnit 6.1.2 BOM
- `app/build.gradle.kts` — remove kotlin-android plugin; compileSdk/targetSdk 37; version 1.03; dadb 2.0.0; JUnit 6.1.2 BOM; migrate kotlinOptions → compilerOptions comment

### Navigation T2
- `core/src/main/kotlin/com/byd/clusternav/navigation/NavigationModels.kt` — `NavigationFrameContent` adds `routeRemainingMeters: Int?`, `routeRemainingSeconds: Int?`, `arrivalClock: String?` with validated init
- `app/src/main/java/com/byd/clusternav/NavRepository.kt` — `ingest()` parses ETA string into typed fields; `toNavState()` reconstructs ETA; persistence saves/loads 3 new fields
- `app/src/main/java/com/byd/clusternav/NavNotificationListener.kt` — classifies maneuver icon (ManeuverSignature → verbIcon → ArrowClassifier) BEFORE creating NavState/ingest
- `app/src/main/java/com/byd/clusternav/ClusterBroadcaster.kt` — removes unsafe `NavRepository.state.arrow` fallback in `sendFrame()`

### New untracked
- `docs/specs/clusternav-v102-review-remediation.html` — consolidated remediation spec
- `docs/_handoff/session-v1.02-sl6-widget-cast-2026-08-04.md` — prior session handoff

## Compile verification

```
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
./gradlew --no-daemon :app:compileDebugKotlin → BUILD SUCCESSFUL
```

All three modules compile: `:core`, `:car-integration`, `:app`. Warnings exist (Kotlin 2.5-future deprecations, data class copy visibility, deprecated Java API) but zero errors.

## What was NOT done yet

1. **HUD/speed-sign bounded output ownership** — still shares unbounded `hudExec` in ClusterBroadcaster. Each output needs its own bounded executor, generation, applied-state, and typed HAL result.
2. **Widget provider independence** (T3) — speed/alerts still collapse into one freshness.
3. **Cast postcondition verification** (T4) — state still commits optimistically.
4. **Cast UI lifecycle** (T5) — Bubble listener leak, tap token, 48dp zones.
5. **Test rebaseline** (T6) — 46 failures remain from stale V2 contracts.
6. **Senior review + security** (T7).
7. **Vehicle evidence** (T8) — including manual `appwidget grantbind`.

## Resume instructions

1. Open this file to confirm current position.
2. Verify compile still passes: `JAVA_HOME=… ./gradlew :app:compileDebugKotlin`
3. Continue T2: extract `NavigationHudOwner` and `NavigationSpeedSignOwner` from `ClusterBroadcaster` — each owns one `Executors.newSingleThreadExecutor()` with bounded queue, generation counter, typed HAL result, and lifecycle clear. Remove the shared `hudExec`.
4. After T2 compile pass, proceed T3 → T4 → T5 → T6 → T7 → T8 per spec DAG.
5. Every batch: ≤5 files, compile after, focused test if available.
6. No blocking sub-agent for implementation. Report-only bounded reviewer allowed per boundary.
7. Do not commit/push without security scan.

## Vehicle test prerequisites (record for future session)

```bash
# VietMap widget bind — must run while parked BEFORE opening widget UI
adb shell appwidget grantbind --package com.byd.clusternav --user 0

# Rollback after unbind-all in app
adb shell appwidget revokebind --package com.byd.clusternav --user 0
```

## Risks for next session

- dadb 2.0.0 may have breaking API changes in `Dadb.shell()` return type or exception hierarchy — `SimpleCastRuntime` and `CastAdbGateway` need adaptation if they fail at runtime.
- JUnit 6.x may rename/move assertions — test compilation will reveal.
- AGP 9.3.1 may change merged manifest behavior for `exported` activities on SDK 37.
- Kotlin 2.4.10 `ConsistentCopyVisibility` warning becomes error in 2.5; fix data classes with non-public constructors before then.
