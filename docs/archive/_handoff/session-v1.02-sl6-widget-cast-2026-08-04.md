# ClusterNav v1.02 — SL6 widget/cast handoff

**Date:** 2026-08-04 · **Owner:** Đăng Khôi · `dangkhoi`

## Status at handoff

| Area | Verified result | Status |
|---|---|---|
| VietMap widget acquisition — emulator | Two providers bind; VietMap background updates speed/limit/alerts; stale at 5 s; unavailable at 30 s; process restart restores IDs | PASS |
| VietMap widget acquisition — SL6 | User photo shows IDs `19/20`, `FRESH`, age `183 ms`, speed `0 km/h`, limit `50 km/h`, VietMap `3.3.4` | PASS |
| SL6 widget bind UX | Earlier attempt displayed `No widget bind UI and shell grantbind failed`, although the final runtime state is bound and fresh | UX/RACE UNRESOLVED |
| SL6 Cast full | App reaches the physical cluster | PASS |
| SL6 Cast split | Left/right request still leaves each app at full bounds, so the apps cover one another | FAIL — needs measured resize evidence |
| Seal Cast | Full/split previously proven on Seal; do not change the known-good path speculatively | PROTECT |
| Cluster road name | User reports cluster name rendering works | PASS |
| HUD road name | Arrow/distance work, road name absent; committed v1.00 raised HUD budget from 7 to 20 UTF-16 units | NEEDS SEAL RETEST |
| VietMap limit → speed-sign output | Acquisition is proven; HAL output is wired using `ADAS_TRAFFIC_LIMIT_SPEED_STATUS_PROMPT`, based on the owner’s prior successful shell injection of `60` while the real sign was `20` | NEEDS SEAL VISUAL RETEST |

## Source, commits, and artifacts

- Branch: `main`
- Last pushed commit: `8842ffe` — removes dead same-device BLE HUD runtime/permissions.
- Main feature commit: `20eb6da` — v1.00 widget bridge, speed-limit output wiring, display-aware task lookup/dynamic resize, HUD road budget.
- **No v1.02 source commit exists yet.**
- Current Gradle version: `versionCode=102`, `versionName=1.02`.
- Candidate APK (ignored artifact): `apk/ClusterNav-1.02-release.apk`
  - Package metadata verified: version code `102`, version name `1.02`, min SDK `29`.
  - SHA-256: `69f21744eaf87ab2e230329a24de95f5b6edb9d212457122d1f10dc8fe70508d`
- Last build: `./gradlew :app:assembleRelease` — PASS.

### Important working-tree warning

Four tracked files are currently modified:

1. `app/build.gradle.kts` — version `1.00 → 1.02`.
2. `app/src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt` — experimental display autodetection.
3. `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetBridge.kt` — shell-grant and post-bind rechecks.
4. `apk/ClusterNav-1.00-release.apk` — **accidentally overwritten by a later build**. It no longer represents the committed v1.00 bytes. Restore it from `HEAD` before any future commit:

```bash
git restore apk/ClusterNav-1.00-release.apk
```

The v1.02 APK is ignored by `.gitignore`; rebuilding it will change its hash.

## What is committed already

### Widget bridge

- Internal, non-visible `AppWidgetHostView` instances for:
  - `VMOnlySpeedLimitWidgetProvider`
  - `VMAlertWidgetProvider`
- Dynamic VietMap resource lookup by name; no decompiled integer IDs.
- Typed snapshot: current speed, speed limit, up to two alerts, freshness and reason.
- Navigation-owned lifecycle; Cast remains independent.
- Diagnostics renders parsed fields only, never the widget UI.

### Speed-limit output

`VietMapWidgetSnapshot.speedLimitKph` flows through:

```text
VietMap RemoteViews → VietMapWidgetBridge → NavNotificationListener listener
→ ClusterBroadcaster.pushSpeedLimit() → BydHal.writeSpeedLimit()
```

Stale/unavailable snapshots call clear (`0`). This output has not yet been visually revalidated on Seal.

### Cast split changes in v1.00

- `AppMover.fitToCluster()` now looks up the task specifically on the target display.
- Bounds come from `wm size -d <displayId>` rather than fixed Seal dimensions.
- Despite this, the tested SL6 still rendered requested split apps full/full. Do not infer another fix without command output.

## Uncommitted v1.02 experiments

### 1. Cluster-display autodetection

`SimpleCastRuntime.kt` parses `dumpsys display` and guesses a non-zero 1920×700–900 display, with a non-1080p fallback.

**Caution:** SL6 full cast already reaches the correct physical cluster. Therefore wrong display identity is not established as the split root cause. This change may be unrelated and could regress Seal or a head unit with DuDu/CarLink virtual displays. Prefer reverting it unless measured logs prove the current target display is wrong.

### 2. Widget bind fallback

`VietMapWidgetBridge.kt` currently:

1. Rechecks `getAppWidgetInfo()` if `bindAppWidgetIdIfAllowed()` returns false.
2. Tries `appwidget grantbind` and `cmd appwidget grantbind` through dadb shell.
3. Rechecks whether firmware bound the ID anyway before showing failure.

This candidate is not on-car verified. The SL6’s final bound IDs `19/20` prove acquisition works, but do not establish which binding path succeeded or why an error was shown first. Do not unbind the working SL6 widgets merely to reproduce the message.

## Next SL6 session — measure before patching

Run while parked. Keep the known-good full-cast path intact.

### A. Baseline before installing another candidate

```bash
adb shell settings get global force_resizable_activities
adb shell dumpsys display > sl6-display-before.txt
adb shell am stack list > sl6-stacks-before.txt
```

### B. Reproduce one left-slot cast with logging

```bash
adb logcat -c
# Tap Cast Left once in ClusterNav, choose VietMap, wait until it appears full.
adb logcat -d -v time -s SimpleCast > sl6-simplecast-left.log
adb shell am stack list > sl6-stacks-after-left.txt
```

Required evidence from `sl6-simplecast-left.log`:

- selected cluster display ID;
- exact `am start ... --display ... --windowingMode 5` command;
- exact `am task resize <taskId> ...` command;
- command exit code and any stderr;
- `fitToCluster` target task ID and requested bounds.

Then compare the same task in `sl6-stacks-after-left.txt`: requested half-bounds versus observed full-bounds.

### C. Only if the resize command targeted the correct cluster task but stayed full

First save the original global setting. Do not toggle it blindly before baseline.

```bash
adb shell settings get global force_resizable_activities
adb shell am task resize <cluster-task-id> 0 0 960 <cluster-height>
adb shell am stack list
```

Interpretation:

- Explicit rejection/error → inspect resizeability/windowing mode; then consider a parked, reversible `force_resizable_activities=1` test.
- Exit 0 but observed bounds remain full → firmware/task ignores resize; investigate windowing mode or stack placement rather than adding more bounds calculations.
- Observed half-bounds but screen stays full → renderer/surface behavior differs from task bounds.

Restore any changed global setting to its captured value after the experiment.

## Next Seal session

1. Keep cluster marquee behavior unchanged; cluster road text is already good.
2. Test HUD road text with the committed 20-unit budget.
3. Confirm widget bridge binds and remains fresh with VietMap backgrounded.
4. Visually verify VietMap limit output on the cluster speed sign using a segment with a known limit.
5. Verify stale data clears the sign rather than leaving an old limit displayed.

## Validation notes

- Focused `VietMapWidgetTextParserTest` passes.
- Release assembly passes for the current v1.02 tree.
- Emulator proved binding, background updates, value changes, stale/unavailable clearing, and process restart.
- The full core suite has five known pre-existing failures in Cast tests/static ratchets; they were not introduced by the widget parser and remain separate debt.
- v1.02 has not been installed/tested on SL6 or Seal at this handoff.

## Explicit stop condition

Do not continue guessing at SL6 split behavior off-car. Capture the exact resize command, task/display identity, command result, and post-command task bounds first. Preserve the known-good Seal path until that evidence identifies the failing boundary.

## Resume update — 2026-08-04 15:21 +07

This update supersedes the earlier working-tree warning for subsequent sessions.

### Completed off-car cleanup and instrumentation

- Restored `apk/ClusterNav-1.00-release.apk` exactly from `HEAD`; its tracked blob now matches the committed v1.00 artifact.
- Reverted the unproven display-dimension autodetection. Runtime selection remains the measured Seal default display `1`, overridden only by an existing saved display ID.
- Reverted the unapproved `appwidget grantbind` / `cmd appwidget grantbind` fallback. Binding again follows the approved official consent/fail-fast contract in `docs/specs/vietmap-widget-bridge.html`.
- Added measurement-only `SimpleCast` logging for:
  - selected display ID and whether it came from saved state or the measured default;
  - existing `AppMover` task/bounds messages under the `SimpleCast` log tag;
  - exact `am start`, `am task resize`, and `wm size -d` command results, including exit code and escaped stdout/stderr.
- No Cast command, placement decision, resize bounds, widget binding behavior, or control flow was changed.

### Validation completed

- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:compileDebugKotlin` — **PASS**.
- `VietMapWidgetTextParserTest` — **PASS**.
- `SimpleCastCoordinatorTest.cast to left slot creates CastingSplit` — **PASS**.
- Full focused `SimpleCastCoordinatorTest` run still has the pre-existing stale assertion `cast to slot applies display config before move`: `openProjection()` has already applied `NORMAL_DEFAULT`, so `DisplayConfigurator` correctly skips the redundant second `wm size`, while the test expects one after clearing command history. No `core` source or test file changed in this resume batch.
- `git diff --check` — **PASS**; changed source is 193 LOC; reverted experiment identifier scan is clean.

### Artifact and vehicle gate

The existing ignored `apk/ClusterNav-1.02-release.apk` still has SHA-256 `69f21744eaf87ab2e230329a24de95f5b6edb9d212457122d1f10dc8fe70508d`, but it was built before the cleanup/instrumentation above. It is therefore **not exact-source for the current tree and must not be treated as the next test candidate**. No new release APK was assembled because this session did not have exact build authorization.

The configured SDK sees only `emulator-5554`; no SL6 is connected. The next allowed action remains: authorize/build an exact-source candidate, connect the parked SL6, then run sections A–C above and collect the four required evidence files. Do not patch split placement until that evidence exists.

### Senior review update — 2026-08-04

The bounded senior review found and resolved three measurement-logging reliability issues, all without changing Cast behavior:

1. Transport exceptions now emit the same structured `command` / `exit=-1` / `stdout` / `stderr` evidence shape as normal shell returns.
2. Evidence payloads are escaped to reconstructable ASCII and split into indexed 768-character chunks with encoded character/UTF-8 lengths, avoiding silent per-entry truncation.
3. Every header and chunk carries a process-local atomic evidence ID, so concurrent shell records can be separated after logcat collection.

After each patch, `:app:compileDebugKotlin` and the focused widget-parser/split-state tests passed. Final senior micro-review verdict: **APPROVED — zero actionable P0–P3 findings for the changed batch**. Overall vehicle feature status remains **INCOMPLETE** for the artifact, SL6 evidence, and Seal visual gates listed above.
