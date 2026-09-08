# ClusterNav V2 — Vehicle Test Checklist

> **Trạng thái**: Current · **Cập nhật**: 2026-07-26 · **Mục đích**: Checklist thao tác thử trên xe + ma trận Stage 11 (execution NOT STARTED).

Owner: **Đăng Khôi · `dangkhoi`**  
Current state: **OFF-CAR 0.72 FIELD-EXECUTION CORRECTION CLOSED — WAITING FOR SEPARATE BUILD AUTHORIZATION**
Candidate source: `0.72 (72)`, exact-source ID `12b532429f9523f521145dc594d02c1793342d0f49a0c0ac6f1f5c0c98bb94e9`  
Vehicle execution state: **NOT STARTED — PROHIBITED FOR INVALIDATED SHA `1b9c016273296454c9fd0ac88bb51dd8c7447b8b7d60b113d689eb7eb9d6b184`**

## Hard boundaries

- Use only the exact APK path and SHA-256 recorded in `docs/_handoff/two-track-vehicle-ready.md`.
- Do not substitute, rename, rebuild, or use any historical APK under `apk/`.
- Vehicle/ROM/profile and test window must be recorded before install.
- `adb reboot` is not reboot evidence. Cases requiring reboot use the physical head-unit power button.
- Stop immediately on freeze, unsafe distraction, unexpected phone-session teardown, two protected residues, false idle, or hash mismatch.
- Vehicle evidence may contain identifiers, app inventory, network metadata, or location context. Scripts store it only under ignored `oncar-v2-*` directories; review/redact before sharing.

## Fixed connectivity setup

- **CarPlay / Android Auto phone connection: USB.**
- **Head-unit ADB connection: Wi-Fi (`adb connect <HEAD_UNIT_IP>:5555`).**
- Wi-Fi is reserved for ADB evidence/listening while CP/AA uses USB. Future test instructions should use this setup directly and not ask the owner to choose again.

## Environment

The candidate is not typed by hand. The authorized build writes
`docs/_handoff/vehicle-candidate.json` (apk path, SHA-256, `exactSourceId`, version), and every
script reads that file. Overriding `APK`/`EXPECTED_SHA256` is only accepted when the hash still
matches the recorded candidate, and all superseded candidates are blocklisted.

```bash
# Optional when more than one adb device is visible:
export ADB_SERIAL="<explicit serial>"
```

If `adb install -r` reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the unit holds a differently
signed build. The only remedy is `adb uninstall com.byd.clusternav`, which also clears Cast durable
state — after that, F1 and the durable-Adjustment rows must be recorded as fresh-install variants
and annotated in the sign-off table.

## Ordered execution

### 0. Off-car emulator pre-check (run before driving anywhere)

```bash
scripts/emulator/e2e-smoke.sh
```

Runs the same candidate resolution and install path the operator will use, on an Android 34
emulator with a simulated secondary display. It refuses to run against a non-emulator serial.
Expected result: `EMULATOR_E2E pass=18 fail=0 skip=0`, covering candidate resolution from the
build-written manifest, install of the exact hash, real resumption of Main and Cast screens,
secondary-display inventory, a truthful cast refusal with the F3 two-option prompt, fail-closed
Stop while no session is landed, an unchanged animation/PIP baseline, and no `FATAL EXCEPTION`.

This does not substitute for any Stage 11 row: the emulator has no BYD AutoContainer service, so
cluster creation itself is never exercised. Its purpose is to catch install, launch, navigation,
prompt-shape and baseline regressions before vehicle time is spent.

### 0b. Session plan — fastest order, and how to stop safely

25 interactive steps in total: 20 in the Cast matrix (C1–C10, F1–F9, C11) and 5 in the Navigation
matrix (N1–N5). Budget roughly 90–120 minutes including two physical reboots. Do the cheap checks
first so a broken candidate costs three minutes instead of an hour.

| Order | Command | Time | Stop if |
|---|---|---|---|
| 1 | `scripts/vehicle/preflight.sh` | ~2 min | model/ROM is not the approved profile, or the APK hash differs |
| 2 | `scripts/vehicle/auto-smoke-test.sh --serial <serial>` | ~3 min | install, launch, version or installed-APK SHA fails — do not continue |
| 3 | `scripts/vehicle/run-navigation-matrix.sh` | ~20 min | — |
| 4 | `scripts/vehicle/run-cast-matrix.sh` | ~60–90 min | — |
| 5 | `scripts/vehicle/capture-evidence.sh` | ~2 min | — |

Both matrices are resumable and step-addressable, so a session can be split across trips:

```bash
scripts/vehicle/run-cast-matrix.sh --list          # see the steps, no device needed
scripts/vehicle/run-cast-matrix.sh                 # start; type q at any prompt to stop
EVIDENCE_DIR=oncar-v2-<stamp> scripts/vehicle/run-cast-matrix.sh          # resume where you stopped
EVIDENCE_DIR=oncar-v2-<stamp> scripts/vehicle/run-cast-matrix.sh --redo --only F7
scripts/vehicle/run-cast-matrix.sh --from F1        # skip C1–C10, run the field cases only
```

At every step answer `p` (pass), `f` (fail), `s` (skip) or `q` (stop and keep progress); anything
typed after the letter is stored as the note, for example `f gauges did not return`. Verdicts land in
`<evidence>/results.tsv` and the script prints paste-ready rows for the sign-off table below, so
nothing has to be reconstructed from memory afterwards. Completed steps are skipped on resume unless
`--redo` is passed, and after a failed run the script prints the exact command to repeat only the
failures.

### 1. Read-only preflight

```bash
scripts/vehicle/preflight.sh
```
Record model, ROM fingerprint, Android SDK, display inventory, operator, date/time, and whether the approved profile matches. Do not install if the APK hash differs.

### 2. Exact-hash install

```bash
export CONFIRM_VEHICLE_INSTALL=YES
scripts/vehicle/install-test-apk.sh
```

Verify package `com.byd.clusternav`, versionCode/versionName, install timestamp and exact APK SHA.

### 3. Navigation + HUD matrix

```bash
scripts/vehicle/run-navigation-matrix.sh
```

Required observations:

- One authoritative source/session; road, maneuver and freshness agree.
- Cluster lane and HUD can each be disabled without stopping or corrupting the sibling.
- Whole-navigation Stop clears both outputs.
- No Dead Reckon card, service, mock-location instruction, or product control.
- Physical power-button reboot rehydrates without stale output.

### 3.1 On-car navigation source trace — 2026-07-25

Status: **SOURCE FEASIBILITY CAPTURED — OUTPUT/HUD INTEGRATION DEFERRED**

The exact candidate `0.67 (67)` with SHA-256 `afcd7a07651d5def520c62a4eb3016876d3b0610a438f156a0517564a4688005` was installed and passed the automated launch/health smoke (`10 PASS / 0 FAIL / 1 WARN`; the warning was unavailable UI hierarchy on this ROM). Raw evidence remains local and gitignored.

| Source scenario | Exposed signal | On-car conclusion |
|---|---|---|
| CarPlay + Google Maps | `byd.intent.action.NAVIGATION_STATE_CHANGED`, state `1/0` | Navigation lifecycle is detectable; maneuver, distance and road text are not exposed in clear Android-side channels. |
| CarPlay + Apple Maps | Same BYD navigation-state broadcast | Start is detectable even when the phone UI does not complete guidance; no maneuver payload observed. |
| Android Auto + Google Maps | Phone navigation-focus request plus BYD navigation-state `1/0` | Navigation lifecycle is detectable; maneuver payload is not exposed. |
| Android Auto + Waze | Navigation-focus request plus BYD navigation-state `1/0` | Start attempt is detectable, but the tested session had no usable GPS guidance. |
| Native VietMap | Accessibility/UI hierarchy `content-desc` | **Viable Navigation + HUD source candidate:** distance, next-road text, ETA, remaining time, route distance and destination were exposed. |
| Native Waze | Foreground app/notification only | No usable maneuver, distance or road payload in the tested no-GPS state. |

Deferred VietMap implementation notes:

- Treat native VietMap as the strongest source candidate for ClusterNav lane and HUD text/numeric fields.
- The maneuver graphic was an `ImageView` without semantic `content-desc`; left/right/straight decoding remains an explicit gap.
- No BYD lifecycle broadcast was observed for VietMap, so source activation/freshness must be derived from robust UI/accessibility state with relaunch tolerance.
- Do not embed personal route or destination samples in tracked documentation. Local evidence directories are `oncar-signals-carplay-gmaps-start-*`, `oncar-signals-carplay-applemaps-start-*`, `oncar-signals-aa-gmaps-start-*`, `oncar-signals-aa-waze-start-*`, `oncar-signals-vietmap-start-*`, and `oncar-signals-waze-start-*`.

### 4. Cluster Cast V2 matrix

```bash
scripts/vehicle/run-cast-matrix.sh
```

Required observations:

- Normal cold Cast, same-target recast and normal→normal switch converge with one visible target.
- CarPlay and Android Auto use resume-only behavior and retain the phone session.
- Protected pairwise switches create at most one hidden protected residue and report `ACTIVE_DEGRADED` truthfully.
- Stop restores gauges and reports idle only after observation.
- Interrupted transport never blindly replays; compensation is used at most once.
- Adjustment Apply/Undo/Restore/Reset/Done is target-bound; accepted geometry changes only on Done and Stop restores the exact pre-cast baseline after zero-task proof.
- Favorites/protected policy survives recreation; base/w960dp/w1280dp layouts retain usable focus/scroll state.
- Bubble direct Stop durably acknowledges within 500ms before opening Activity.
- Connected projection asks for phone disconnect; disconnected recovery requires explicit owner/session/two-sample confirmation and runs at most once.
- Sleep/wake uses the Cast-owned lifecycle observer and physical power-button reboot rehydrates the durable journal without auto-cast.
- Placement follows the D10 ladder and each rung is journaled: `force_resizable_activities 0` → animation quiesce → PIP block → pre-open on display 0 (only when the target has no task) → `am start … --display <vd> --windowingMode 5` without `--activity-clear-task` → `am display move-stack <stack> <vd>` → reassert → `[wm density;] am task resize`.
- `am display move-stack` appears only toward the cluster display. Nothing moves a stack to display 0; a protected sink is returned with `am start --display 0`. `--activity-clear-task` appears only on the destructive rung.
- The destructive rung (`am force-stop` + clear-task relaunch) runs only after the on-screen consequence prompt is accepted, and never for CarPlay/Android Auto or a keep-session app.
- Bootstrap adopts an existing unoccupied cluster display with zero `service call AutoContainer` commands; an occupied cluster must be cleared with Dừng first.
- Verification uses the measured cluster size/density, not the constants 1920×720/180.
- Stop restores the journaled PIP app-op and animation scales before the display reset.
- Blocked DADB work has bounded workers and queue.

### 4b. 0.72 field-execution cases (record PASS/FAIL plus the copied operation log for each)

`scripts/vehicle/run-cast-matrix.sh` prompts F1–F9 after C1–C11 and writes one capture per case
(`cast-f1-…` … `cast-f9-…`) into the evidence directory.

| # | Case | Expected |
|---|---|---|
| F1 | Cold start with the cluster VD already present | bootstrap adopts it, zero seal commands in the log, cast reaches ACTIVE_VERIFIED |
| F2 | Cast a navigation app with a route already set | route survives (no force-stop in the log), cluster composites without a white frame |
| F3 | App whose task refuses to reparent | prompt offers "Tắt app và chiếu lại"; declining leaves the previous state intact |
| F4 | CarPlay / Android Auto | resume-only, no force-stop offered, phone session intact |
| F5 | Kiểu cụm thẳng (31) for one app, cong (30) for another | each app brings its own cluster shape; km/h gauge returns with the curved app |
| F6 | Cỡ chữ cụm (DPI) per app | `wm density` appears once for that app in the log and the rendered size changes |
| F7 | Force-stop the cast app from outside | watchdog runs exactly one canonical Stop within ~2 minutes and the gauges return |
| F8 | Stop after any cast | PIP app-op and animation scales read back to their pre-cast values |
| F9 | Firmware dump wording changed / parse miss | the log shows the DisplayManager fallback identity instead of a dead read-only state |

### 5. Final evidence snapshot

```bash
scripts/vehicle/capture-evidence.sh
```

Review `SHA256SUMS.txt`, screenshots, logs and dumps. Do not commit raw vehicle evidence.

Also copy the in-app operation log for every case: **Chiếu cụm → Chẩn đoán → sao chép**. It contains each journaled step, the exact dispatched shell command and every refusal reason, which is the fastest way to classify an on-car failure. Note that the log lists installed package names, so treat a pasted dump as device-local evidence.

## Sign-off table

| Area | Result | Evidence path | Notes |
|---|---|---|---|
| Exact APK/hash/install | NOT STARTED | — | — |
| Navigation both outputs | NOT STARTED | — | — |
| Independent output toggles | NOT STARTED | — | — |
| Navigation Stop | NOT STARTED | — | — |
| Normal Cast cold/warm | NOT STARTED | — | — |
| CarPlay continuity | NOT STARTED | — | — |
| Android Auto continuity | NOT STARTED | — | — |
| Protected pairwise/residue | NOT STARTED | — | — |
| Durable Adjustment + exact Stop baseline | NOT STARTED | — | — |
| App manager + adaptive focus/scroll | NOT STARTED | — | — |
| Bubble direct Stop ≤500ms | NOT STARTED | — | — |
| Phone disconnect + one-shot recovery | NOT STARTED | — | — |
| Unknown-effect recovery + bounded workers | NOT STARTED | — | — |
| Sleep/wake Cast-owned revalidation | NOT STARTED | — | — |
| F1 Adopt existing cluster VD (no seal) | NOT STARTED | — | — |
| F2 Route survives cast | NOT STARTED | — | — |
| F3 Reparent refusal → prompt | NOT STARTED | — | — |
| F4 CarPlay/AA resume-only | NOT STARTED | — | — |
| F5 Cluster kind 30/31 per app | NOT STARTED | — | — |
| F6 Cluster DPI per app | NOT STARTED | — | — |
| F7 Watchdog single canonical Stop | NOT STARTED | — | — |
| F8 Stop restores PIP + animation baseline | NOT STARTED | — | — |
| F9 Parse-miss DisplayManager fallback | NOT STARTED | — | — |
| Physical power-button reboot | NOT STARTED | — | `adb reboot` invalid |
| Final owner sign-off | NOT STARTED | — | — |

## Exit rule

Stage 11 passes only when every required row is PASS against the exact candidate SHA and the owner explicitly signs off. Any code change requires a new exact-source identity, a separately authorized replacement build, and repetition of affected evidence. Stage 12 legacy Cast source deletion remains deferred until the separately approved post-soak cleanup release.

## Appendix — optional, not part of Stage 11

If the trip passes through a tunnel, `scripts/vehicle/probe-gps-tunnel.sh` collects the M1–M4
measurements for [`docs/research/gps-dead-reckon-tunnel.html`](../research/gps-dead-reckon-tunnel.html).
It is read-only (no install, no activity start, no setting write, no location mutation), it does not
gate any sign-off row, and it can be skipped freely. Its output tells us whether the head unit has an
IMU at all and whether a tunnel countdown is even possible.
