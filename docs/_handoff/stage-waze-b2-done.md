# Stage handoff — Waze logcat tag fix + Track B2 cluster screenshot

> Branch: `feat/speed-limit-badge-hal-hud` · Date: 2026-08-17 · applicationId `com.byd.clusternav2`, namespace `com.byd.clusternav`
> Scope: two off-car source fixes. No commit/push, no APK build, no keystore/local.properties/main touched.

## FIX 1 — Waze logcat TAG regression (evidence-based)

**Root cause:** `WazeHudSource` filtered logcat on the stale tag `WazeHUD`. WazeMod 5.20.90.901 (owner's car + emulator) emits the HLP payload under `WazeHudLink` (and `WazeHudLink-BLE` for BLE) per the official doc `wazemod.chisadin.id.vn/tai-lieu/esp32` (`adb logcat -s WazeHudLink WazeHudLink-BLE`). The old `-s WazeHUD:V` filter never matched → Waze dead on car.

**Change:** tag constants + `DUMP_CMD` now read BOTH tags. `parseHlp` / HLP/1 parsing left UNCHANGED (fields identical).

- `app/src/main/java/com/byd/clusternav/modules/wazehud/WazeHudSource.kt`
  - L54: `private const val LOGCAT_TAG = "WazeHudLink"` (was `"WazeHUD"`)
  - L55: `private const val LOGCAT_TAG_BLE = "WazeHudLink-BLE"` (new)
  - L58 — **exact new DUMP_CMD** (template): `"logcat -d -v raw -s $LOGCAT_TAG:V $LOGCAT_TAG_BLE:V -t $TAIL_LINES"`
  - **Resolved (TAIL_LINES=60):** `logcat -d -v raw -s WazeHudLink:V WazeHudLink-BLE:V -t 60`
  - KDoc updated: `LOGCAT TAG (corrected 2026-08-17 for WazeMod 5.20.90.901)` block + `NOT SUFFICIENT ALONE` honest note.

**Honest note (in KDoc):** the tag fix is necessary but NOT sufficient alone. WazeMod HudLink only emits HLP while a BT/BLE HUD peer is connected (doc: "log không tạo transport giả, không thể phát stream nếu chưa kết nối thiết bị"). A single device has no BT self-loopback, so a real peer (ESP32 / 2nd device) or an on-car BLE-loopback test is still required. No BLE receiver was built (out of scope).

## FIX 2 (Track B2) — reliable cluster screenshot + rename

**On-car finding (2026-08-17):** `fission_screencap -d 1` is UNRELIABLE (sometimes returns the main screen; never captures the `TYPE_APPLICATION_OVERLAY` badge). Plain `screencap -d 1` DOES capture the Android overlay layer (our badge). Display 0 shows whatever nav app is foreground, not necessarily GMaps.

**Change:** `app/src/main/java/com/byd/clusternav/SegmentShotCapturer.kt`

Seg filename scheme after change (file:line):
- L67: `captureFission(0, File(diag, "seg-$n-$ts-main.png")…)` — display 0 (main head-unit), fission composite. **Renamed** `-gmaps` → `-main`.
- L72: `captureFission(1, File(diag, "seg-$n-$ts-cluster.png")…)` — display 1, fission OEM composite (kept).
- L73: `captureAndroid(1, File(diag, "seg-$n-$ts-cluster-overlay.png")…)` — display 1, platform `screencap` overlay layer (**new**, incl. our badge).
- L74: log line `seg-$n-$ts-*.png` (unchanged).

Commands (per-turn capture):
- Fission (unchanged form): `fission_screencap -d <display> -p <outPath>`  — fission's `-p` TAKES the path.
- Platform (new): `screencap -d 1 <outPath>` — path is POSITIONAL; `.png` extension forces PNG; no `-p` flag (platform `-p` is a boolean; omitting avoids any fork consuming the path as an arg).

`capture()` split into `captureFission()` + `captureAndroid()`, each its own `runCatching` so one failure never blocks the others. Behavior preserved: verbose-gated (`NavLog.verbose`, default OFF), off-thread (single-thread daemon executor), ~3 s debounce (`SegmentShotDecision.shouldFire`), degrade-safe. Class KDoc + inline path comments updated (`{main,cluster,cluster-overlay}.png`, per-file "WHAT EACH FILE IS" table).

## Tests updated
**None required.** Case-sensitive scan confirmed the old tag literal `"WazeHUD"`, `LOGCAT_TAG`, and `DUMP_CMD` existed ONLY in `WazeHudSource.kt` — no unit test asserted on the tag or the command string. No test referenced `SegmentShotCapturer`, `fission_screencap`, `screencap`, or the `seg-*` PNG filenames. `WazeHudSourceTest` (14 @Test) exercises only `parseHlp`/`pollOnce`/`processDump`/`toNavState`/`hlpTurnToManeuver`, which are unchanged, and all still pass.

## Files changed (this stage only)
- `app/src/main/java/com/byd/clusternav/modules/wazehud/WazeHudSource.kt` (281 LOC ≤ 500)
- `app/src/main/java/com/byd/clusternav/SegmentShotCapturer.kt` (107 LOC ≤ 500)

(Other modified/untracked files in the working tree are pre-existing branch work — not touched by this stage.)

## GATE — final counts
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:test :app:testDebugUnitTest --console=plain   # BUILD SUCCESSFUL in 1m 1s
```
- `:core:test`            → **512 tests, 0 failures, 0 errors, 0 skipped**
- `:app:testDebugUnitTest`→ **377 tests, 0 failures, 0 errors, 0 skipped**
- **Total: 889 tests, 0 failures → GREEN**

## Constraints honored
- No touch to keystore.properties / app/release.keystore / local.properties.
- No git commit/push; no APK build; main branch untouched (`git log -1` unchanged: `1c4485c`).
- Both files ≤ 500 LOC; degrade-safe + verbose-gated behavior preserved.
