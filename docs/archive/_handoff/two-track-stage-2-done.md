# Two-Track Rebaseline — Stage 2 Done

Owner: Đăng Khôi · `dangkhoi`  
Date: 2026-07-24  
Authorization: `IMPLEMENTATION_AUTH stage=2 allowlist=docs/_handoff/two-track-stage-1-done.md#stage-2` plus `AUTONOMOUS_OFFCAR_APPROVED stages=2-10`  
Approved Stage 1 baseline: `b26006ecb689974d616deb5222778639e22f283f20664abe2e80023a7f2c068e`

## Exit verdict

**STAGE 2 SOURCE/JVM VERDICT: PASS.** Senior-review patch loop ended with **0 actionable P0–P3 findings**. Navigation deliverables 5/5 and Cast/collector deliverables 5/5 are implemented inside the exact Stage 2 allowlist. Stage 2 remains deliberately dark: no runtime integration path imports the V2 package and no existing APK or vehicle state was touched.

This is not release, APK, install, on-car, physical-reboot, compatibility, or safety evidence.

## Senior-review findings patched

- **[P1] Navigation fan-out:** contained both `submit` and `recordFault` failures so one adapter cannot suppress its peer.
- **[P1] Navigation persistence ordering:** durable clear now succeeds before coordinator RAM/output state is cleared.
- **[P2] Navigation startup:** first session no longer issues unnecessary output stops.
- **[P2] JVM compatibility:** removed three `Files.readString` calls from Navigation static tests.
- **[P1] Cast rollout:** added independent `navUiV2`, durable `effectiveUiVersion` and `pendingUiRollback`, sticky active ownership, and safe idle/no-transaction rollback application.
- **[P1] Cast STOP shape:** a transaction/recovery export containing `STOP` is accepted only with `StopDisposition.AVAILABLE`; malformed producer shapes fail closed.
- **[P1] Cast epoch:** `bumpEpoch` now enforces exactly `old + 1` after transformation.
- **[P1] Cast 32-case manifest:** replaced abbreviated rows with all six authoritative fields from approved rows 1–32; exact normalized fingerprint is `e469bd761d724d09c337818a798123a907d810daee7fd19eb261672a7ca94411`.
- **[P1] Observation boundary:** package resolution requires a validated package target; profile provenance no longer aliases package-list output.
- **[P1] Collector publication:** copy goes to a unique same-directory temporary file and publishes by no-replace atomic move (safe fallback), with cleanup on every failure.
- **[P2] Cast immutable state:** defensive unmodifiable snapshots cover baseline occupants/animation, transaction ledger, observed occupants, plan steps/actions, planner/UI actions/reasons, and rollout slices.
- **[P1] Canonical projector boundary:** aligned transaction operation IDs and deadlines with the approved `UUID`/`Instant` schema and made fresh-PARKED comparisons use `Instant` directly.
- **[P1] Durable codec boundary:** operation IDs now serialize as canonical UUID text and decode fail-closed through `UUID.fromString`; round-trip tests use deterministic UUID fixtures.

## Boundary-shape checklist

- ✅ Navigation source → frame store → coordinator: source/session/sequence/freshness and nullable frame fields match both producer and consumer.
- ✅ Coordinator → lane/HUD ports: independent call, queue, worker, deadline, cache, health, generation and fault containment; double-failure regression passes.
- ✅ Cast envelope model ↔ codec/store: schema/checksum/epoch/boot/stop/pending intent/effective UI/pending rollback/stable/transaction/ledger round-trip; corrupt and unsupported input fail closed.
- ✅ Observation request ↔ gateway/parser: closed read-only request type and command partition; target parameters validated; Unknown/Unsupported propagate without planning.
- ✅ Planner ↔ manifest: pure typed plan; authoritative rows 1–32 match exact six-field fingerprint.
- ✅ Planner/recovery ↔ UI projector: canonical enum/nullability/action/reason contracts, STOP invariant, 20 mappings and nine-row precedence pass.
- ✅ Durable envelope ↔ rollout: nav-only flag changes do not alter Cast decision; V2 owner remains sticky while active; rollback applies only at `IDLE_VERIFIED` with no transaction.
- ✅ Gradle properties/input APK ↔ destination artifact: requested release only, slice and 64-hex source validation, one fresh release APK, unique name, no overwrite/partial final artifact.
- ✅ Exactly two: no Navigation↔Cast import/control/live resource, no legacy V2 wiring, no shared executor/queue/lock/journal/epoch/watchdog/recovery policy.

## Verification evidence

Environment: Homebrew OpenJDK `17.0.19` at `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`.

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.byd.clusternav.navigation.*' \
  --tests 'com.byd.clusternav.modules.clustercast.v2.*' \
  --tests 'com.byd.clusternav.BuildArtifactNamingTest'

BUILD SUCCESSFUL
7 test classes · 44 tests · 0 failures · 0 errors · 0 skipped
```

```text
./gradlew help --task collectAuthorizedApk
BUILD SUCCESSFUL
```

The help command configured the task only; it did not execute the collector or `assembleRelease`.

Additional checks:

- Canonical JSON: version 2, 18 fields, 10 next-safe-action translations, 20 recovery mappings, nine precedence rows.
- Canonical SHA-256: `d2c143a5369487fce312bb0a506785a3396280b689ddfd7842557e1f6273ca7b` — PASS.
- Static boundary scan: five Navigation source files, eight Cast source files, zero runtime V2 edges, zero `finalizedBy`, zero production/test `Files.readString` in Stage 2 — PASS.
- Size guardrail: 21 reviewed Kotlin/build files; maximum 326 LOC; all ≤500 LOC — PASS.
- Kotlin/semantic diagnostics on patched core files: no diagnostics.

## Technology freshness

- Gradle project baseline: wrapper 8.7, AGP 8.5.2. Context7 `/websites/gradle_9_4_1` confirms `tasks.register`, managed `Property`/`DirectoryProperty`, and task input annotations remain current; removed Gradle 9 task lookup pattern is not used.
- Kotlin project baseline: 1.9.24. Context7 `/jetbrains/kotlin-web-site` confirms `Enum.entries` is current and read-only collections are not immutable snapshots; collection boundaries were hardened accordingly.
- Android `AtomicFile`: official API reference confirms API 17, current/non-deprecated `startWrite`/`finishWrite`/`failWrite`/`readFully`, and no locking semantics. Stage 2 therefore requires one coordinator/store writer; approved future process separation must preserve a single writer through typed IPC.
- No dependency or toolchain version was added or changed.

## Explicitly not run / not claimed

- APK assemble or `collectAuthorizedApk` execution — **NOT RUN**.
- APK install, relabel, byte mutation, signature/hash publication — **NOT RUN**.
- Runtime/legacy integration, flags ON, Activity/Home wiring — **NOT RUN** by design.
- Android process-kill, physical power-button reboot, sleep/wake, on-car cases, exact-build V2-CAR matrix — **NOT STARTED**.
- Vehicle-profile support/parking provider proof — **NOT STARTED**; deep/destructive runtime behavior remains disabled.
- Dead Reckon retirement, legacy Cast deletion, commit, branch, push, merge, security scan and vehicle action — **NOT RUN IN STAGE 2**. The later owner amendment authorizes reviewed off-car DR retirement and one final test build, but still does not authorize install/car mutation, commit, push, merge or physical legacy Cast deletion.

## Evidence ledgers

- `docs/design/navigation-hud-evidence.html`
- `docs/design/cluster-cast-evidence.html`

## Stage 2 exact-source identity

- Manifest: `docs/_handoff/two-track-stage-2-exact-source.json`
- Canonical identity algorithm: SHA-256 of parsed manifest serialized with recursively sorted object keys, preserved array order, compact separators, UTF-8, and no trailing LF.
- `exactSourceId`: `4a1441a6bdc8887a0efb2521d792fed051beb1aa241739e5a2fc17e2598389d6`
- Branch / HEAD: `release/v0.60-cast-hardening` / `fd4890c1ffabf4b8cb37f5ccbd5cdb93f0343ae6`
- Tracked binary diff: 112,299 bytes; SHA-256 `91e68b53a8bd4994f271ac1f7365351570eeb218e9f173683e6eb09701d692d9`.
- Intended untracked inputs: 45.
- Hash-inventoried exclusions: 11 — four local `.kiro` tooling files, five historical APKs, and two self-referential Stage 2 attestations.

The tracked-diff change from the approved Stage 1 baseline (105,464 bytes / `1969fa39…9831a`) is explained by the authorized Stage 2 `app/build.gradle.kts` collector change plus the owner-approved plan/public-document edits. Stage 2 source/test/evidence files are represented as intended untracked inputs. Existing APK bytes remain unchanged.

## Exact Stage 2 path closure

- 23 authorized Stage 2 paths exist: 13 production source files, seven test files, one Gradle build file and two evidence ledgers.
- Every Kotlin/Kotlin-DSL file is ≤500 LOC; maximum observed production file size remains below 300 LOC.
- Production Navigation has zero Cast imports; production Cast V2 has zero Navigation imports and no `am display move-stack` primitive.
- No existing runtime path imports the V2 package; runtime flags remain OFF.
- Canonical schema fixture remains `d2c143a5369487fce312bb0a506785a3396280b689ddfd7842557e1f6273ca7b`.
- All eight quarantined APK paths still match their recorded size/SHA-256 values.
- Personal project identity scan: no company handle or email in Stage 2 artifacts.

## Final direct verification — autonomous amendment

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
./gradlew --offline :app:testDebugUnitTest \
  --tests 'com.byd.clusternav.navigation.*' \
  --tests 'com.byd.clusternav.modules.clustercast.v2.*' \
  --tests 'com.byd.clusternav.BuildArtifactNamingTest'

BUILD SUCCESSFUL in 649ms
44 tests represented by 7 authorized test classes
```

Deterministic scope/boundary/schema/collector/APK audit: **PASS**. The first audit attempt incorrectly scanned test fixture strings as production imports; the corrected production-only audit passed. No product patch resulted from that audit-script correction.

## Scope-completeness verdict

- ✅ Navigation closed models/store/coordinator and independent output contracts.
- ✅ Bidirectional lane/HUD block, throw, saturation, stale and verification isolation.
- ✅ Cast durable model/store/checksum/schema/epoch contracts.
- ✅ Typed read-only observation boundary, pure planner and exact 32-case manifest.
- ✅ Canonical 18-field UI state, 20 recovery rows, action translation and Stop precedence.
- ✅ Session-sticky rollout/action ownership with flags default OFF.
- ✅ Explicit collision-failing release collector; no automatic finalizer and no historical overwrite.
- ✅ No cross-track mutable/live-resource/control edge and no runtime wiring in Stage 2.
- ✅ Context7/official API results and extraction limitations recorded in the evidence ledgers.

**Final Stage 2 verdict: PASS — 0 actionable P0–P3 remaining in the direct closure review.**

## Next state

The owner amendment removes the old `WAITING_FOR_IMPLEMENTATION_AUTH_STAGE3` stop. Continue directly with Stage 3 inside the autonomous Stage 2–10 union allowlist:

`AUTONOMOUS_OFFCAR_STAGE3_IN_PROGRESS`

No APK was built during Stage 2. The single authorized test APK is reserved for the final off-car exact-source boundary after Waves 3–10 and final review.
