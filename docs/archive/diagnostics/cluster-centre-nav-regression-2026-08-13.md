# Regression — cluster centre "Giữa + ETA" gone after 1.17 install + reboot (2026-08-13 evening)

> On-car, live over adb (IP redacted → `<vehicle-ip>`), parked, GMaps navigating. Owner report:
> after installing **1.17** and **rebooting**, (1) lost the OEM "menu chỉnh AMAP nav trên cluster",
> (2) ClusterNav no longer shows the cluster‑centre **"Giữa + ETA"** — only a small nav strip at the top.

## What was verified (evidence)

1. **NOT a 1.17 code regression.** `git log` shows the cluster‑nav code (`Prefs` nav‑screen, `BydHal.writeNavFrame`, `NavigationHudOwner`) is **unchanged since 1.12** (commit `3884d55`). 1.16 = interp rounding only; 1.17 = voice‑key only. No commit touched the nav path after 1.15.

2. **ClusterNav is running and writing the cluster every ~800 ms**, all HAL writes return **rc=0** (accepted):
   ```
   AbsBYDAutoDevice: set featureID is 4c10e015 intValue is 1
   NavigationHudOwner: cluster-nav icon=11 seg=0 road='…' mode=1 → SEND_NAVI_STATUS=0 NAVI_SCREEN=0 GUIDE=0 CROSSING=0 PATHNAME=0   (=0 are rc, success)
   ```
   The `mode` = `Prefs.navClusterScreenMode` = **1** (SIMPLE). Note the app's value↔label map is a **GUESS** in source ("Đơn giản=1" unverified; only **3** documented as the proven centre value).

3. **The in-app cluster display-mode selector is a NO-OP now.** Owner cycled all 4 options (OFF/Đơn giản/Màn hình nhỏ/Toàn màn hình) → **no change** on the cluster. So the `SET_NAVI_SCREEN_STATUS (0x4C10E015)` write is not affecting the display.

4. **The top strip is ClusterNav's LANE (broadcast), and it works.** Owner toggled Nav+HUD off → strip disappeared; on → reappeared. `ClusterBroadcaster: emit lane … byd=false` fires every ~400 ms. But it carries **`seg=-1` (no distance)**, only road name → incomplete GMaps data.

5. **The centre HAL path is dead for EVERY writer — including navopen.** With ClusterNav force‑stopped, `navopen` wrote a clean centre frame and got **real devices** + rc=0:
   ```
   InstrumentDevice = android.hardware.bydauto.instrument.BYDAutoInstrumentDevice
   SettingDevice    = android.hardware.bydauto.setting.BYDAutoSettingDevice
   set instr (0x43e0003a)=2 rc=0   # SEND_NAVI_STATUS on
   set setting (0x4c10e015)=3 rc=0 # NAVI_SCREEN=3 (proven centre)
   set instr (0x43f01010)=9 rc=0   # GUIDE icon
   set instr (0x43f01018)=500 rc=0 # FRONT_CROSSING 500m
   ```
   → **centre did NOT render.** Since even the raw RE tool (navopen, `systemMain` context) can't paint the centre, this is **NOT a ClusterNav bypass‑context bug** — it is a **system/OEM‑level state** that stopped rendering HAL centre‑nav after the reboot.

6. **`navi_protect` is NOT the cause.** `sys.init.navi_protect=1`, `sys.change_navi_auth=1` are the **normal baseline** — `docs/refactor-car-execution/verdicts.tsv` (2026‑07‑29) proved the cluster renders with `navi_protect=1` unchanged. Ruled out.

7. **The OEM renderer `com.example.amapservice` IS running** (priv‑app `/system/priv-app/AmapService`, uid=system, `.AmapService` bound, has `BootCompleteReceiver`). So it's not a dead‑process problem — it's running but not painting the HAL centre frames.

## Current best theory
The reboot left the OEM cluster‑centre nav renderer (AmapService / instrument nav) in a state where it **accepts HAL nav writes (rc=0) but does not paint the centre**, and the associated OEM "AMAP nav on cluster" menu is gone. Trigger not yet pinned. The lane/broadcast channel is unaffected. Recurring theme with tonight's other finding (assistant wiped on reboot): **reboot resets OEM state that isn't re‑applied.**

## Open questions for owner / next steps
- Before the reboot, was "Giữa+ETA" showing **GMaps** nav or the **OEM 高德/AMAP** nav? Where did the "AMAP nav on cluster" menu live (car Settings? which app)?
- Try a **clean power‑button reboot** — does the centre + menu come back? (State broke at a reboot; a fresh boot may re‑init the renderer.)
- If reboot doesn't restore: focused RE needed — diff AmapService state / find the enable flag or trigger that re‑inits the OEM centre renderer; consider whether ClusterNav must re‑assert it on boot.

## Research pass 2 — what today's noon/1.12 docs actually say (no assumptions)
Read `oncar-handoff-2026-08-13.md` (11:24, 1.12), `-1.13.md` (13:04), `oncar-2026-08-13-amap-cluster-menu-and-op39-rootcause.md` (10:19), `scripts/vehicle/nav-screen-mode-probe.sh` (08:41).

**Owner-CONFIRMED on-car (proven):**
- Writing HAL `SET_NAVI_SCREEN_STATUS_SET` (`0x4C10E015`, BYDAutoSettingDevice) **unlocked the OEM "Nav trên cụm" menu** (Đơn giản / Màn hình nhỏ / Toàn màn hình / OFF).
- `navopen` (uid **shell**) demo `status=2 + screen=3 + frame` → rc=0 (proven write path).

**NEVER verified on-car (explicit debt, carried 1.12 → 1.13 → still open):**
- Whether the **app** (app-uid via BydPermissionBypassContext) rendering Giữa+ETA works — always marked "CHƯA verify".
- The **value↔menu map**: which `0x4C10E015` value = "Đơn giản (Giữa+ETA)". Only **3 = "Toàn màn hình"** is navopen-proven; **"Đơn giản" = 1 is a GUESS** (nav-screen-mode-probe.sh header: value→layout is decided by the cluster MCU/Qt firmware, "learn it on-car").
- A "mode" may be a **combination** of `0x4C10E015` navi-screen + `0x4C10E01D` map-sending + `0x4C10E03A` dynamic-navi (per the probe script).

**Tonight's contradiction to resolve:** the doc claims writing `0x4C10E015` re-unlocks the greyed menu, but tonight writing it (app selector ×4, navopen setraw, navopen full, and 01D=1+03A=1+015=3) did **not** re-unlock the menu nor move nav to centre. So the exact unlock that worked earlier is not reproduced by a plain `0x4C10E015` write post-reboot. This is the crux for tomorrow.

**Honest status:** a code-only "fix tonight" would be an assumption. The definitive restore requires the on-car **readback probe** (owner hand-picks each OEM menu option → script reads back the real `0x4C10E015`/`01D`/`03A` values) to learn the true value↔menu map + the real unlock sequence. Prepared as tomorrow's plan.

## Not the cause (ruled out)
1.17 code · navi_protect setprop · ClusterNav bypass‑context · AmapService not running.
