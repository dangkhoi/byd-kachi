# Seal HUD + speed-sign reverse-engineering baseline — T0–T9

Scope is strictly off-car T0–T9 plus the T0 main-hook quarantine. No vehicle transport, installation, broadcast, discovery, or field execution was used. Paths below are corpus aliases; `<project-root>` and `<user-cache>` intentionally replace machine-specific paths.

## Starting baseline

- HEAD: `d85b9f2e13c3081287005bee0e90bb482bd6d272`
- Approved starting diff SHA-256: `174110d242ce46f8187eb0d612b7b5c9da73a9d16b3661d99a2d6da3ba4a0536`
- Approved spec starting SHA-256: `c93f502b96377752d725d0017e51ce4f0c6adf05d0f129874b1f3602f356f661`
- Four pre-existing dirty files were frozen before work. `FloatingBubbleService.kt` (`37fab7…6f65`) and `CastUILifecycleSafetyTest.kt` (`f1acda…f49e`) remain byte-for-byte unchanged. The manifest and `RebindReceiver.kt` changed only for the approved T0 quarantine.
- Invalid candidate: source ID `527589f2d16ac04400e811d89da31ae5b21f693058b5713cb8dd90eea365380c`; APK `<project-root>/apk/ClusterNav-1.04-v104-527589f2d16a-release.apk`, SHA-256 `b9a025…b598`. It predates the preserved first-launch fixes, crashes on clean first launch, and is **INVALID — DO NOT INSTALL OR PROMOTE**.

Exact starting/current hashes are in `corpus-completeness.json`.

## T0 quarantine

The main `RebindReceiver` is now `android:exported="false"`. Its manifest filter retains only package replacement, boot, locked boot, and the app watchdog. All main `TEST_*`, MASS, raw-ID, free-form feature-name, and HAL mutation handlers/actions were removed. No `vehicleTest` transport was created.

Focused contracts:

- `MainProbeSurfaceAbsenceTest`: receiver private; no probe tokens; system/watchdog actions and handling preserved.
- `FloatingBubbleFirstLaunchContractTest`: foreground-before-overlay in both lifecycle entries; one-shot overlay request; guarded teardown.
- The two pre-existing first-launch implementation/test files remain byte-identical to their starting hashes.

## T1 tool verdict

All large tools live under `<user-cache>/clusternav-re`; nothing was added to the tracked repository except `tools/re/manifest.json`.

| Tool | Result |
|---|---|
| JADX 1.5.6 | PASS. Resolved `/opt/homebrew/Cellar/jadx/1.5.6/bin/jadx`, launcher SHA-256 `64a6ee…8a7`, all-jar SHA-256 `966d31…0b65`. |
| Sibling JADX 1.5.0 | Explicitly REJECTED at `<project-root>/../tools/jadx`; jar SHA-256 `c12902…429f`. |
| Apktool 3.0.3 | PASS. Official asset/local SHA-256 `dbf930…9423`; decode-only smoke under JDK 17. |
| Ghidra 12.1.2 | PASS. Official archive/local SHA-256 `b62e81…f99d`; T1 headless usage smoke passed under Java 21 and the completed T3 deterministic analysis is recorded below. |
| Temurin Java 21.0.12+8 sidecar | PASS for Ghidra only. Archive SHA-256 `021d62…81c`; detached signature is available but its trust chain was not verified. |
| Project Gradle JDK 17.0.19 | Preserved at `/opt/homebrew/opt/openjdk@17`; project source/target/toolchain remain 17. |

GitHub provides release-asset SHA-256 digests but no separate release signature assets for JADX, Apktool, or Ghidra, so their manifest state is `SIGNATURE_UNAVAILABLE`. Official URLs, tags, upstream digests, local hashes, extracted executable hashes, and smoke results are recorded in `tools/re/manifest.json`.

## T2 corpus/decode verdict

**Available scope:** `COMPLETE_FOR_AVAILABLE_JAVA_CORPUS`  
**Overall corpus:** `NOT_EXHAUSTIVE`

Selected/hashes: AmapService + NaviInfo, CarSettings Java and four DEX files, old/new L3 Java/APKs, old/new services framework jars, old/new unstripped cluster libraries, and OpenBYD/DashCast/TMap/NavOpen references. `corpus-completeness.json` contains all 17 selected artifact/tree hashes.

Pinned re-decode completed into `<user-cache>/clusternav-re/decoded/<input-sha256>` for AmapService, four CarSettings DEX files, and old/new L3 APKs. JADX auto mode honestly records 8/11/0/2/0/83/83 errors respectively; every fallback pass exited 0. Apktool decode completed for the three valid APKs. The original `carsettings-apk/CarSetting.apk` is malformed (`zip END header not found`, unzip exit 9), so the four valid extracted DEX files were used instead; Apktool is explicitly `not-applicable-standalone-dex` for them.

Required missing branches remain zero-hit/unavailable: vendor partition, `system_ext`, `odm`, `vendor_boot`, `libbydauto*`/`libbydautoservice*` providers, property registry/config database, provider APKs, service/hwservice context files, and cluster QML/RCC assets. Old/new native libraries are available but T3 native analysis is intentionally deferred. These gaps force `NOT_EXHAUSTIVE`.

## Concrete Java evidence

- **H1 map gate:** `carsettings/com/byd/ccs/impl/server/hud/Hud00600401300000.java:69` writes `INSTRUMENT_HUD_NAVIGATION_MAP_SET` with `2` for ON and `1` for OFF. Config/status keys are at `carsettings/com/byd/feature/instrument/Instrument.java:536–539`; status `2` is interpreted enabled at `Hud00600401300000.java:39–43,62–63`.
- **Amap canonical transport:** `amap/com/example/amapservice/AmapService.java:49` defines `AUTONAVI_STANDARD_BROADCAST_SEND`; the receiver branch starts at line 299 and parses navigation guidance separately.
- **NaviInfo shape:** `amap/byd/fbs/naviInfo/NaviInfo.java:246` calls `startObject(18)`. The indexed 18 fields include road, maneuver, distance, route and ETA fields; none contains speed/limit. Therefore speed-sign data is not hidden in NaviInfo.
- **Modern property interface:** `fw-new/com/byd/car/property/ICarPropertyManager.java:14,31` declares `getCarProperty`/`setCarProperty`; line 22 exposes configs. Concrete wrapper implementations are `fw-new/car/s2.java:104,225`. Deprecated singular methods remain explicitly marked fallback in the interface.
- **Statistics candidate:** `carsettings/com/byd/feature/statistics/Statistics.java:44–45` defines current-road speed-limit value/type SET keys; this is still `WRITE_INTENT_CONSTANT`, not write permission proof.
- **Instrument candidate:** `carsettings/com/byd/feature/instrument/Instrument.java:790–793` defines traffic-sign identify/value/color constants; it remains `STATUS_OR_OUTPUT_ONLY` until access and consumer mapping exist.
- **ADAS candidate:** `carsettings/com/byd/feature/adas/Adas.java:944` defines `ADAS_SLA_OUTPUT_SPEED_LIMIT`; output naming keeps it read/listener-only by default.
- **Provider candidates:** `carsettings/com/byd/dipilot/view/safetyassistance/old/trafficsign/TSRCellular.java:56,94` names Telenav and binds the trafficmonitor interface; `carsettings/com/byd/systemsettings/utils/VersionUtils.java:59` selects Neusoft or Telenav version providers. Package strings are evidence; provider APK presence is a zero hit.
- **S8 native consumer:** zero hit in the Java roots. The native binaries exist, but `trafficSignValue`, `trafficSignType`, `slaEquip`, and QML linkage remain a T3 task and are not promoted here.

`evidence-index.json` contains all H0–H7 and S0–S10 rows, classifications, hit counts, and producer/transport/consumer/surface edges. Public citations are metadata-only: artifact hash, alias/path, line, sanitized-line SHA-256, and explicit matched-token IDs; no decompiled source line or operational command body is retained. Every candidate is non-executable and off-car visual PASS is false.

## T3 native RE verdict

**Available native scope:** `COMPLETE_FOR_REQUESTED_NATIVE_SYMBOL_SCOPE`  
**Combined available scope:** `COMPLETE_FOR_AVAILABLE_JAVA_AND_REQUESTED_NATIVE_SYMBOL_SCOPE`  
**Overall corpus:** `NOT_EXHAUSTIVE`

The pinned Ghidra 12.1.2 archive was complete and hash-correct, but its initial macOS runtime path contained the token `Caches`; Ghidra's own class-search filter therefore ignored every module jar and failed with `Unable to locate extension points`. The runner exhausted a fresh-user-state attempt, identified that exact local cause, and performed a reversible offline re-extraction of the same archive (SHA-256 `b62e81…f99d`) to a non-cache runtime path. No download or source/binary upload occurred. The pinned `analyzeHeadless` (`302880…e30`) and Java 21 binary (`34b9c1…1a0`) hashes are checked before every run; projects are local, read-only and deleted after processing, with 1,200-second analysis and 1,500-second outer timeouts.

- Old ELF: SHA-256 `9f8a0b…7ca`, build ID `cd1479d41ea9a3894f8b33029c7cb091af499180`, 31,072,080 bytes.
- New ELF: SHA-256 `3197ab…ae8`, build ID `6b4c7dcbea6785270533c253c9d3e82eb193413e`, 31,077,752 bytes.
- Both are ELF64 little-endian AArch64 shared objects, dynamically linked, unstripped and carry debug info. Each nm index has 4,011 symbols and 27 relevant named symbols; full nm symbol-name sets are identical.
- Matching uses full demangled symbol plus implementation/thunk role, never an address. Ghidra exported 27 implementation pairs and 27 thunk pairs. Twenty-five implementations are T3-primary; two `trafficSignalStatus` implementations are explicitly `ADJACENT_OUT_OF_SCOPE`.
- Six implementation bodies are byte-identical. Twenty-one raw body hashes differ, but every one has equal address-normalized decompilation and is classified `LAYOUT_OR_RELOCATION_ONLY`; possible semantic changes: zero. The common implementation address shift is `+0x37c`. All 54 old/new rows decompiled successfully; unresolved functions: zero.
- Exact accessors/signals include `limitTrafficSignRecognition` (`0xd8ec4→0xd9240`), `slaEquip` (`0xd93ec→0xd9768`), `trafficSign` (`0xd9614→0xd9990`), `trafficSignValue` (`0xd95fc→0xd9978`) and `trafficSignType` (`0xd96ec→0xd9a68`), with callers and data references retained in the JSON.
- Broad Ghidra analysis emitted non-scoped GCC exception-disassembly and Varnode diagnostics (exact category counts and local sanitized log aliases are recorded), but every selected function exported/decompiled with zero unresolved exporter rows and neither run timed out. Full C text remains only in ignored local Ghidra cache output; the canonical report retains decompile status, character count, raw/address-normalized hashes and truncation state, never function bodies.
- Native strings contain 11 QML/RCC indicators per binary, including qrc/font and system RCC names, but no standalone asset exists in the available corpus and no direct DataSourceManager→QML binding is proven. QML/RCC therefore remains `UNAVAILABLE`; S1/S2/S3 transport→native-consumer linkage remains `UNPROVEN`.

The canonical report is `native/libbydcluster-diff.json`, SHA-256 `d2d7f6…e9464`. Raw C remains in ignored cache only; the tracked metadata-only report is 375,940 bytes and preserves all 108 decompile status/hash records.

## Determinism and privacy

Two independent metadata-only index runs were byte-identical (`2dcdc5…a9a6`), two pre-native graph builds were byte-identical (`23f704…736b`), and both zero-hit reports were byte-identical (`b8b1a0…b452`). The native-augmented evidence report is sealed as `ac3fd2…80b9`. `verify-reproducibility.py` rejects raw `snippet`/`decompile.c` fields and found no machine-home path or private-IP leak in tracked reports. No project, APK, firmware, or vehicle artifact was uploaded.

## T4–T9 contracts, runtime and consolidation verdict

**T4–T7:** the typed evidence graph, neutral `:vehicle-contracts`, transport-free `:offcar-planner`, deterministic inert M1–M4 packs, serialized Amap lane, UNKNOWN/no-write HUD controller and independent NOOP speed-sign ports are implemented and wired. All source candidates remain UNKNOWN or BLOCKED; no property, HAL, shell, ADB or visual execution path exists in the off-car planner.

**T8 verifier:** `scripts/verify-seal-hud-sign-offcar.sh` runs O1–O27 fail-closed and offline. It rehashes all 17 corpus inputs, verifies pinned tool bytes, performs an isolated seven-input decode repeat, checks all JSON/report schemas and bidirectional IDs, enforces exact CURRENT/FUTURE path sets and ≤500 LOC, scans main/release probe surfaces, validates dependency/transport fences, and runs the requested JVM/build/lint matrix. JADX auto mode has a stable file inventory and error count but may choose different equivalent try/catch reconstructions; O3 therefore requires exact auto inventory plus byte-identical fallback and Apktool trees. This normalization was established from the observed diffs and does not discard deterministic fallback evidence.

**T9 result:** 13 Python tests and 1,352 JVM executions passed with zero failures/errors/skips: contracts 3, planner 29, core 669, app debug 320, app vehicleTest 320 and car-integration 11. The debug, vehicleTest and release APK assemblies succeeded. All three lint variants report zero errors (147 pre-existing warnings each). The genuine AGP `testVehicleTestUnitTest` variant is enabled; it is not a debug-task alias. Changed source max is 498 LOC. The final sensitive-data scan covered 80 current source/report/spec/manifest files and returned `BLOCK=0 WARN=0`; canonical evidence contains zero raw Java snippets and zero Ghidra C bodies.

A local emulator binary and one configured AVD exist, but this session explicitly prohibited ADB and APK installation. `first-launch-emulator-result.json` therefore records `BLOCKED_BY_EXPLICIT_NO_ADB_INSTALL`, `executed=false` and four false observation claims; the three unit/static first-launch contracts pass. No visual PASS is inferred.

**Final review verdict:** `APPROVED` for all achievable T0–T9 off-car gates; `CORPUS_VERDICT=NOT_EXHAUSTIVE`; `FEATURE_DONE=false`. Missing vendor/provider/property/QML branches remain explicit and keep vehicle capability UNKNOWN. T10/T11, vehicle commands, install, commit and push were not performed.
