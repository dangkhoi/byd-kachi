# ClusterNav v1.04 — Release-Readiness + Security-Hardening review

> Reviewer session `release-readiness-harden` · 2026-08-11 · off-car · **no commit/push, no install on vehicle.**
> Env: `JAVA_HOME=/opt/homebrew/opt/openjdk@17`, `ANDROID_HOME=~/Library/Android/sdk` (build-tools 34.0.0 + 36.0.0, aapt2 present).

## TL;DR verdict

| Surface | Verdict | Basis |
|---|---|---|
| **Debug build** | **READY** | `:app:assembleDebug` OK; debuggable retained; no location/test/mock surface; defined suite green. |
| **Release build (from current source)** | **READY** | `:app:assembleRelease` OK + signed; not debuggable; no TEST_ADAS/TEST_SPEED_LIMIT/location; `lintRelease` clean (abortOnError=true). |
| **Shipping to users (the published artifact)** | **READY-EXCEPT — rebuild the vehicle candidate + on-car Stage 11** | The archived `apk/…-v104-…-release.apk` referenced by `vehicle-candidate.json` **predates the WARN-1 hardening and still exports the ADAS/instrument-write test surface** ([P0] below). It must be rebuilt from the current hardened source via `collectAuthorizedApk`, then pass on-car Stage 11 + owner sign-off. |

**Net:** the *source tree* is release-ready and already hardened; the *previously published candidate binary* is not and must be superseded. No code hardening was required this session (the WARN-1 source hardening had already landed with the 7 waves / T10-preparation work); the one code/doc change made was README version+safety hygiene.

---

## 1. Build results

Both variants build. The anticipated keystore blocker did **not** materialize: `keystore.properties` (`storeFile=release.keystore`) resolves to `app/release.keystore` (2736 bytes, present, mode 0600, gitignored), so `assembleRelease` signs successfully in this environment.

### Debug — `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**
`aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk`:
```
package: name='com.byd.clusternav' versionCode='104' versionName='1.04' compileSdkVersion='37'
application-label:'ClusterNav'
application-debuggable                 <-- debug IS debuggable (correct)
uses-permission: FOREGROUND_SERVICE, POST_NOTIFICATIONS, READ_LOGS, RECEIVE_BOOT_COMPLETED,
                 INTERNET, SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE_SPECIAL_USE,
                 QUERY_ALL_PACKAGES, BIND_APPWIDGET
# NO ACCESS_FINE_LOCATION / ACCESS_MOCK_LOCATION
```

### Release — `./gradlew :app:assembleRelease` → **BUILD SUCCESSFUL** (signed)
`aapt2 dump badging app/build/outputs/apk/release/app-release.apk` (SHA-256 `954eccca…`):
```
package: name='com.byd.clusternav' versionCode='104' versionName='1.04' compileSdkVersion='37'
application-label:'ClusterNav'
# (no application-debuggable line)          <-- release NOT debuggable (correct)
uses-permission: FOREGROUND_SERVICE, POST_NOTIFICATIONS, READ_LOGS, RECEIVE_BOOT_COMPLETED,
                 INTERNET, SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE_SPECIAL_USE,
                 QUERY_ALL_PACKAGES, BIND_APPWIDGET
# NO ACCESS_FINE_LOCATION / ACCESS_MOCK_LOCATION
```

Both APKs: `package com.byd.clusternav`, `versionName 1.04`, `versionCode 104` ✔. `minSdk 29`, `targetSdk 37`, `compileSdk 37`. `allowBackup=false` (protects the on-device ADB private key). `extractNativeLibs=false`.

---

## 2. WARN-1 (attack surface) — status: **already hardened in source; [P0] on the stale artifact**

### Source is already hardened (no change needed this session)
The exported ADAS/instrument-write test surface described in the 2026-08-06 baseline has already been removed from `main` and confined to the `vehicleTest` build type (this landed with the T10-preparation work / 7 waves that are currently uncommitted in the working tree):

- `app/src/main/.../RebindReceiver.kt` + `app/src/main/AndroidManifest.xml`: `RebindReceiver` is **`android:exported="false"`** and handles **only** production self-heal actions (`MY_PACKAGE_REPLACED`, `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `com.byd.clusternav.REBIND_WATCHDOG`). No `TEST_*` actions. The real rebind/boot/watchdog behavior is intact.
- The on-car diagnostic probe (`HudSignProbeReceiver` exported=false + inert, `HudSignProbeActivity` exported=true launcher-only) lives **only** in `app/src/vehicleTest/` — absent from `main`/`debug`/`release`.
- There is **no** `app/src/debug/` or `app/src/release/` manifest, so debug and release merge the same `main` manifest (differ only by `debuggable`).
- Guard tests enforce this permanently: `app/src/test/.../MainProbeSurfaceAbsenceTest.kt` (runs for every variant incl. release) and `app/src/testVehicleTest/.../VehicleTestSurfaceContractTest.kt`.

**aapt2 verification of the freshly-built RELEASE (`app-release.apk`, SHA `954eccca…`):**
```
TEST_ADAS occurrences   : 0
TEST_SPEED_LIMIT        : 0
debuggable attr         : 0
exported=true components: com.byd.clusternav.MainActivity                          (LAUNCHER)
                          com.byd.clusternav.modules.clustercast.ClusterBlackActivity (projection surface)
                          com.byd.clusternav.ClusterNavActivity                     (projection surface)
```
The `vehicleTest` merged manifest, by contrast, *does* carry `android:debuggable="true"` + `HudSignProbeReceiver`/`HudSignProbeActivity` — confirming the isolation works: the probe surface exists only in vehicleTest.

### BEFORE / AFTER (exported test surface in the release manifest)

| | Exported test surface in **release** manifest |
|---|---|
| **BEFORE** (2026-08-06 baseline; still present in the archived candidate `b9a0259e`) | `RebindReceiver` `exported=true` with intent-filter actions `TEST_SPEED_LIMIT`, `TEST_ADAS_PROBE`, `TEST_ADAS_WRITE`, `TEST_ADAS_READ`, `TEST_ADAS_MASS` |
| **AFTER** (current source → `app-release.apk` `954eccca`) | none — `RebindReceiver` `exported=false`, zero `TEST_*` actions |

### [P0] The published/archived 1.04 candidate is UNSAFE and must be rebuilt
`docs/_handoff/vehicle-candidate.json` points to `apk/ClusterNav-1.04-v104-527589f2d16a-release.apk` (SHA-256 `b9a0259e7174a694b7bd4fd8984199ac91091e5e52e8de9c73ae6c67542bb598`, built 2026-08-06T06:46:54Z). `aapt2 dump xmltree` on that APK:
```
E: receiver (line=122)
   android:name = "com.byd.clusternav.RebindReceiver"
   android:exported = true                          <-- exported!
   intent-filter actions:
     com.byd.clusternav.TEST_SPEED_LIMIT
     com.byd.clusternav.TEST_ADAS_PROBE
     com.byd.clusternav.TEST_ADAS_WRITE            <-- instrument WRITE, callable by any app
     com.byd.clusternav.TEST_ADAS_READ
     com.byd.clusternav.TEST_ADAS_MASS
# application-debuggable: absent  → looks like a legit release, which makes it MORE dangerous
```
Any co-installed app could broadcast `TEST_ADAS_WRITE`/`TEST_ADAS_MASS` to this receiver and drive the ADAS/instrument HAL. This binary predates the hardening (the baseline lists WARN-1 as *pending* on 2026-08-06). It must **not** be handed to users.

**Remediation (owner action — release-governance gated):**
1. Rebuild the vehicle candidate from the current hardened source via the exact-source pipeline (`scripts/evidence/gen-exact-source.py` → `./gradlew collectAuthorizedApk -PclusterNavVariant=release -PclusterNavSlice=v104 -PexactSourceId=<64hex> -PexactSourceManifest=docs/_handoff/<manifest>.json`). This regenerates `docs/_handoff/vehicle-candidate.json` with the new (clean) identity.
2. Quarantine/remove the stale `apk/ClusterNav-1.04-v104-527589f2d16a-release.apk` so it cannot be installed.
3. This session did **not** delete the stale APK or mint a new authorized candidate — both are owner/release-governance actions, not a reviewer's. It is flagged here for the owner.

README was corrected this session so it no longer offers the stale binary as the download (see §6).

---

## 3. Release-manifest / release-source leakage scan (checklist 3b)

Scanned the packaged release manifest (`app/build/intermediates/packaged_manifests/release/…/AndroidManifest.xml`) and the shipped sources (`app/src/main`; there is no `app/src/release`).

| Token | Release manifest | Debug manifest | `app/src/main` sources |
|---|---|---|---|
| `MockGps` | absent | absent | 0 files |
| `TEST_WIDGET_DUMP` | absent | — | 0 files |
| `debugDump` | absent | — | 0 files |
| `ACCESS_FINE_LOCATION` | absent | absent | 0 files |
| `ACCESS_MOCK_LOCATION` / `ACCESS_COARSE_LOCATION` | absent | — | — |
| `addTestProvider` / `setTestProviderLocation` | — | — | 0 files |
| `TEST_ADAS` / `TEST_SPEED_LIMIT` / `T10_` | absent | absent | 0 files |
| `HudSignProbe` | absent | absent | (vehicleTest only) |
| `android:debuggable` | **absent** (correct) | **present** (correct) | — |
| `usesCleartextTraffic` / `cleartextTraffic` | absent | — | — |

Exported components in the release manifest = **3**, all production, none a test surface and none handling sensitive intent data:
- `MainActivity` — LAUNCHER (exported-without-permission is normal for a launcher).
- `ClusterBlackActivity`, `ClusterNavActivity` — cluster projection surfaces, no intent-filter, launched by explicit component via `am start` from shell-uid; documented as **required** `exported=true` because DiLink3 enforces an export-check for shell-uid (verified on-car 2026-07-20). See [P3] below.

`:core` stays Android-free: 0 files under `core/src/main` contain `import android.` ✔.

---

## 4. Lint

`./gradlew :app:lintRelease` → **BUILD SUCCESSFUL** with `abortOnError=true` + `checkReleaseBuilds=true` (from `app/build.gradle.kts`). `lintVitalRelease` also passes as part of `assembleRelease`. No lint errors; no suppressions/baseline were added this session. Reports: `app/build/reports/lint-results-release.html` / `.sarif`.

---

## 5. Tests

`./gradlew :core:test :app:testDebugUnitTest :car-integration:test` — **BUILD SUCCESSFUL** (up-to-date against the current tree; results dated 2026-08-11 14:04–14:06, no source newer). Counts from the JUnit result XML:

| Module | tests | failures | errors | skipped |
|---|---|---|---|---|
| `:core:test` | 723 | 0 | 0 | 0 |
| `:app:testDebugUnitTest` | 321 | 0 | 0 | 0 |
| `:car-integration:test` | 28 | 0 | 0 | 0 |
| **Defined suite total** | **1072** | **0** | 0 | 0 |

Extra modules in `settings.gradle.kts` (not part of the task's defined suite): `:vehicle-contracts:test` passes; `:offcar-planner:test` has **1 failure** — see [P3] below.

---

## Findings

- **[P0] Published vehicle candidate ships the WARN-1 ADAS/instrument-write surface.** `apk/ClusterNav-1.04-v104-527589f2d16a-release.apk` (SHA `b9a0259e…`, referenced by `vehicle-candidate.json`) exports `RebindReceiver` with `TEST_ADAS_*`/`TEST_SPEED_LIMIT` and is not debuggable. Do not distribute. Rebuild from current hardened source via `collectAuthorizedApk`, regenerate `vehicle-candidate.json`, quarantine the stale APK. (Source is already hardened; this is a stale-artifact problem.) README fixed this session to stop offering it.
- **[P3] (informational, accepted) Two projection activities are `exported=true` without a permission.** `ClusterBlackActivity` + `ClusterNavActivity` have no intent-filter and process no intent data; they only present a projection surface. `exported=true` is documented as required for DiLink3 shell-uid `am start`. Threat is limited to a co-installed app popping a projection UI (nuisance), not instrument-write or data exfiltration; acceptable for a personal sideloaded hobby app. No action.
- **[P3] (informational, out-of-scope) `:offcar-planner:test` — 1 failing test** `ExpansionTransportFenceTest > "parent baseline and T11 retain exact hashes while authorized T10 may be absent"` (`ExpansionTransportFenceTest.kt:134`), a SHA-256 hash-fence attestation in the T10/T11 HUD-sign expansion tooling. `:offcar-planner` is an **untracked** module in the do-not-touch speed-sign/HUD investigation domain, is **not** in the task's defined suite, and is **not** a dependency of `:app` (app → only `:core` + `:car-integration`), so it **cannot** affect the shipped APK. Not touched per the constraint; flagged for the investigation owner (a tracked file it fingerprints has drifted from its recorded hash).
- **[P3] (fixed) README version/status drift.** Was `Current version: 0.99` / status `0.72`; updated to `1.04 (versionCode 104)` and the status line rewritten to the accurate current state, plus a safety note replacing the stale-candidate download link (see §6).

## 6. Doc hygiene (README) — applied

`README.md` (the only tracked file modified this session — `git status` shows `M README.md` and nothing else):
- Status line: `OFF-CAR CANDIDATE 0.72 — FIELD-EXECUTION CORRECTION` → `OFF-CAR CANDIDATE 1.04 (versionCode 104)` with an accurate description (JVM suite green off-car, debug+release build clean, test/instrument surfaces confined to `vehicleTest`, on-car Stage 11 NOT started, no supported public release until on-car pass + owner sign-off).
- Download line: `Current version: 0.99` + link to `ClusterNav-0.99-release.apk` → `Current version: 1.04` with build-from-source guidance **and an explicit ⚠️ warning not to install the archived `…-v104-…-release.apk`** because it predates the WARN-1 hardening. (The download link was intentionally *not* pointed at the stale unsafe candidate.)

---

## To actually ship to users (note)

1. **Rebuild the vehicle candidate from the current hardened source** through the authorized exact-source pipeline (per the 2026-08-06 baseline): `gen-exact-source.py` → `collectAuthorizedApk -PclusterNavVariant=release -PclusterNavSlice=v104 -PexactSourceId=<64hex> -PexactSourceManifest=…`. This produces `apk/ClusterNav-1.04-v104-<newid>-release.apk`, refreshes `docs/_handoff/vehicle-candidate.json`, and verifies pkg/versionCode + not-debuggable via aapt2 during collection. Retire the old `527589f2…` candidate.
2. **Confirm the new candidate is clean** (`aapt2 dump xmltree` → no `TEST_ADAS_*`/`TEST_SPEED_LIMIT`, `RebindReceiver` exported=false, not debuggable, no location perms).
3. **On-car Stage 11 has not started** — the release gate (Cast full/split/resize/profiles + autostart + HUD arrows) plus owner sign-off is still required before any APK is a supported public release. A physical ignition off/on is required where a reboot is called for (`adb reboot` is not accepted on DiLink3).
4. Do not merge to `main`, and do not commit/push, without the mandatory public-repo sensitive-data scan and explicit authorization.

## Evidence index
- Fresh debug APK: `app/build/outputs/apk/debug/app-debug.apk` (debuggable, clean).
- Fresh release APK: `app/build/outputs/apk/release/app-release.apk` (SHA `954eccca362949c622f699df8bd0daf2b709044c6871b26d918c7109282a1b2f`, hardened, signed).
- Stale unsafe candidate: `apk/ClusterNav-1.04-v104-527589f2d16a-release.apk` (SHA `b9a0259e7174a694b7bd4fd8984199ac91091e5e52e8de9c73ae6c67542bb598`, has `TEST_ADAS_*`).
- Packaged manifests: `app/build/intermediates/packaged_manifests/{release,debug,vehicleTest}/…/AndroidManifest.xml`.
- Guard tests: `MainProbeSurfaceAbsenceTest`, `VehicleTestSurfaceContractTest`, `DeadReckonRetirementTest`.
