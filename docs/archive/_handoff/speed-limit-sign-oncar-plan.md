# Speed Limit Sign — On-Car Test Plan (resume next session)

> Status 2026-08-05: OFF-CAR. Debug harness built into ClusterNav debug APK.
> Goal: find the exact BYD HAL feature that renders the cluster speed-limit sign (red circle "60").

## What we confirmed on-car (Seal <vehicle-ip>, DiLink3.0)

| Finding | Evidence |
|---------|----------|
| ADAS `setSLAState(1)` works | rc=**0** (success) — turns Speed Limit Assist ON |
| ADAS `setSLAState(60)` fails | rc=-2147482645 — method only accepts 0/1 (on/off), not km/h value |
| Generic `set(int[],EventValue)` to ADAS_SLA_STATE_SET (944767010) | rc=-2147482648 (error sentinel) |
| Generic `get(int[],Class)` to all SLA/ISLA features | returns -2147482648 (error sentinel = not readable this way) |
| AUTONAVI broadcast (nav lane) | ✅ renders icon+distance+road on cluster; does NOT carry speed limit (AmapService ignores LIMITED_SPEED/CAMERA_SPEED) |
| `rc=0` = success; `rc=-2147482648`/`-2147482645` = HAL error | consistent across all writes |

**Owner evidence:** previously fired a MASS of commands → cluster lit up many warning icons AND set speed limit to 60. This points to **BYDAutoInstrumentDevice** (drives cluster warning icons), not ADAS. The "60" was one INSTRUMENT feature among a mass-write.

## Debug harness (in current debug APK, RebindReceiver, exported for adb)

All broadcasts need flag `-f 0x01000000` (FLAG_RECEIVER_INCLUDE_BACKGROUND) to reach the background receiver.

```bash
DEVICE=<vehicle-ip>:5555
ADB="adb -s $DEVICE"

# 1. PROBE a device — list speed/limit/sign methods + feature IDs
$ADB shell am broadcast -a com.byd.clusternav.TEST_ADAS_PROBE --es dev instrument -f 0x01000000
$ADB shell am broadcast -a com.byd.clusternav.TEST_ADAS_PROBE --es dev adas      -f 0x01000000
# read: adb -s $DEVICE shell logcat -d | grep "PROBE\["

# 2. MASS-WRITE value 60 to every speed/limit/sign _SET feature on a device.
#    Logs each feature + rc. rc=0 = accepted. Then LOOK at cluster for "60".
$ADB shell am broadcast -a com.byd.clusternav.TEST_ADAS_MASS --es dev instrument --ei val 60 -f 0x01000000
$ADB shell am broadcast -a com.byd.clusternav.TEST_ADAS_MASS --es dev adas       --ei val 60 -f 0x01000000
# reset: --ei val 0
# read:  adb -s $DEVICE shell logcat -d | grep "MASS\["

# 3. WRITE a single feature id (once identified) — narrow down
$ADB shell am broadcast -a com.byd.clusternav.TEST_ADAS_WRITE --ei id <FEATURE_ID> --ei val 60 -f 0x01000000

# 4. READ back candidate feature values
$ADB shell am broadcast -a com.byd.clusternav.TEST_ADAS_READ -f 0x01000000

# 5. Speed-sign via the production path (VietMap/Waze → NavigationSpeedSignOwner → BydHal.writeSpeedLimit)
$ADB shell am broadcast -a com.byd.clusternav.TEST_SPEED_LIMIT --ei limit 60 -f 0x01000000
```

## On-car test sequence (next session)

1. **Reconnect + verify freeform** (for cast) — separate from speed sign.
2. **PROBE instrument**: run TEST_ADAS_PROBE dev=instrument → collect all SPEED/LIMIT/SIGN feature IDs + writable methods. Save logcat.
3. **MASS-write instrument val=60**: run TEST_ADAS_MASS dev=instrument → note every feature with rc=0. LOOK at cluster: does "60" appear? Which warning icons light up (matches owner's memory)?
4. **Narrow down**: for each rc=0 feature, TEST_ADAS_WRITE individually with val=60 then val=80 → find the ONE that changes the red-circle number. That is the speed-limit feature ID.
5. **Reset**: TEST_ADAS_MASS val=0 to clear warning icons.
6. **Bake in**: replace `BydHal.writeSpeedLimit` feature (currently the invalid ADAS_TRAFFIC_LIMIT_SPEED_STATUS_PROMPT fallback 1329602589) with the confirmed feature ID + device. Then TEST_SPEED_LIMIT should render.

## Code changes needed once feature ID confirmed

- `app/.../modules/hal/BydHal.kt` `writeSpeedLimit`: use confirmed device (likely INSTRUMENT) + confirmed feature ID + correct set method (dedicated setter if rc=0, else generic setInt).
- If it needs `setSLAState(1)` first (enable assist) then a value feature, chain both.
- Remove debug harness actions (TEST_ADAS_*) + `exported=true` on RebindReceiver before release build.

## Cleanup reminder (before release)
- RebindReceiver: remove TEST_ADAS_PROBE/WRITE/READ/MASS/SPEED_LIMIT handlers, set `exported="false"`.
- Manifest: remove the 5 TEST_* actions.

## Today's wins (2026-08-05)
- Letterbox fix (wmSize 1920×720), bounds full top=0, bubble 70%.
- PiP block (appops deny GMaps/YouTube on start), restore on stop.
- WazeMod HLP/1 logcat source + 2-spinner source selection (nav vs speed/alert).
- VietMap widget correct provider class + auto-bind + persist.
- Freeform enable (settings + power-cycle) → cast resize/split work on SL6 after restart.
- Distance-assist removed (firmware jitter).
- Installed + permissioned on SL6 (<vehicle-ip>) and Seal (<vehicle-ip>).
- APK: apk/ClusterNav-1.03-debug.apk
