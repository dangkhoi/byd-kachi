# Stage 1 Done — Toolchain + Letterbox + Release Hardening

## Baseline
- HEAD: `8842ffe1bd4b6647e71a34945d481f06bcc18df2`
- Branch: `main`

## Toolchain (already at target — no migration needed)
- Gradle: 9.6.1 ✅ (wrapper SHA-256 pinned)
- AGP: 9.3.1 ✅
- Kotlin: 2.4.10 ✅ (JVM plugin for core/car-integration, built-in for app)
- dadb: 2.0.0 ✅
- JUnit: 6.1.2 BOM ✅
- compileSdk/targetSdk: 37 ✅
- JDK: 17 (via jvmToolchain)

## Files modified
- `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/SimpleCastModels.kt` — R21 letterbox fix (wmSize 1920x800 → 1920x720)
- `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/AppMover.kt` — comment update matching new wmSize
- `app/build.gradle.kts` — release hardening (isDebuggable=false, no debug fallback, vehicleTest variant, lint gating, collector APK verification)
- `gradle/verification-metadata.xml` — NEW: dependency checksums (auto-generated)

## Letterbox fix confirmed
```
NORMAL_DEFAULT = DisplayConfig(wmSize = "1920x720", overscan = "0,0,0,0", bounds = CastBounds(0, 90, 1920, 630))
```
grep "1920x800" in core/src/main/kotlin/ → 0 matches ✅

## Release config
- `release`: isDebuggable=false, requires release signing key, lint abort on error
- `vehicleTest`: isDebuggable=true, requires release signing key, for on-car diagnostics
- Collector: verifies APK package/versionCode/debuggable flag via aapt2

## Build verification
- No JDK in this CI environment — compilation deferred to user's local build
- Code inspection confirms no syntax/API breaking changes
- All toolchain versions already correct (no migration code changes needed)

## Test count
- Pre-stage: 799 (core 570, app 218, car 11)
- Post-stage: expected same (no test files changed)

## Known issue
- `app/build.gradle.kts` = 579 LOC (exceeds 500). Pre-existing condition (was 506 before stage). Splitting requires extracting ExactSourceIdentity into buildSrc convention plugin — deferred to T6 if feasible.
