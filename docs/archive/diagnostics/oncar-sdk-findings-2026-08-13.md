# On-car note — 2026-08-13 (SDK/DashCast findings + this session's fixes)

> Prep for tonight's casual on-car session. Two parts:
> - **Part A** needs the NEW ClusterNav build installed (J1/J2 from today).
> - **Part B** is pure `navopen` poking — **no app build needed**, safe to "chơi thêm".
>
> ⚠️ Safety: do writes **parked**, engine on. Always `getraw` (read) **before** any `setraw` (write).
> IPs redacted → set `VEH=<vehicle-ip>`. navopen jar assumed at `/data/local/tmp/navopen.jar`
> (push `apks/navopen-v4.jar` if missing).

```bash
VEH=<vehicle-ip>            # e.g. adb over tcpip
NAV() { adb -s "$VEH" shell "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen $*"; }
# navopen cmds:  getraw <instr|setting|adas> <hexid>   |   setraw <instr|setting|adas> <hexid> <val>
```

---

## Part A — verify today's off-car code (needs new build)

Shipped as **1.15 (versionCode 115)** — pushed to `main` as `apk/ClusterNav-1.15-release.apk`. The car self-updates
**OTA** (polls `apk/` on `main`, installs via dadb `-r`, same key). Just wait for the update (or trigger it),
then confirm the app shows **1.15** before testing below.

- [ ] **J1 — HUD keep-alive**: drive a **long straight with no turn** for >2–3 s. HUD/centre "Giữa+ETA" should **stay visible** (no ~1 s blank/flicker). If it still blanks → the keep-alive interval (currently **400 ms** in `HudKeepAlivePolicy`) needs lowering; note how long the blank is.
- [ ] **J2 — comparison log**: after a short drive with a turn or two:
  ```bash
  adb -s $VEH pull /sdcard/Android/data/com.byd.clusternav/files/   # grab nav_log_*.csv
  python3 scripts/analyze-nav-distance-log.py nav_log_*.csv
  ```
  Confirm the **`screenRead_m`** column is now populated (GMaps on-screen distance) and check the "projected − screen" bias → that's the data to tune the interpolator next cycle.

---

## Part B — SDK/DashCast feature-id experiments (navopen only, no build)

> These come from the BYD SDK scan + DashCast source (`dashcast-src`, "OpenBYD 2.x RE").
> ⚠️ **DashCast's inline hex comments are WRONG** — I recomputed the real hex from its authoritative
> decimal literals (only `SETTING_NAVI_SCREEN` matched its comment). Use the hex below.

### B1 — cross-check ids (READ-ONLY, do this first)
Confirm which ids ClusterNav/DashCast use actually resolve on this ROM, and their live values while GMaps nav is running:
```bash
NAV getraw setting 4C10E015   # nav-screen status (expect 3 when nav shown on cluster)
NAV getraw instr   43E0003A   # SEND_NAVI_STATUS (expect 2 while navigating, 4 stopped)
NAV getraw instr   43F01010   # GUIDE_SIMPLE (turn icon + dist-to-turn)
NAV getraw instr   43F01018   # FRONT_CROSSING_DIST (m to next crossing)
NAV getraw instr   43FA1008   # NEXT_PATHNAME (next road, bytes)
```
→ Note which return sane values. Compare against what ClusterNav's `featureId(name)` resolves (logcat `NavigationHudOwner` / self-test). This tells us whether ClusterNav's name-based ids == these RE ids.

### B2 — NEW: ETA / mileage on the cluster centre (the interesting bit)
DashCast has ids for remaining distance + ETA that ClusterNav does **not** use yet (it hacks a Chinese time-string). With GMaps nav active, try writing and watch the cluster centre:
```bash
NAV getraw instr 43F02028      # NAVI_MILEAGE (remaining route dist) — read first
NAV setraw instr 43F02028 5000 #   → does "5.0 km remaining" show on cluster?
NAV setraw instr 43F02010 0    # NAVI_HOUR   (ETA hour 0-23)
NAV setraw instr 43F02018 25   # NAVI_MINUTE (ETA min 0-59)
NAV setraw instr 43F0201E 480  # NAVI_REMAINING_SEC (remaining time, s)
NAV setraw instr 43F08010 1    # NAVI_LEAD_MSG (advanced lead icon)
NAV setraw instr 43F08018 300  # DISTANCE_TARGET_AHEAD (advanced)
```
→ Note which ones actually render on the cluster. If ETA/mileage work → ClusterNav can show them natively (drop the CN-string hack). If nothing renders → they're not wired on this trim; keep current path.

### B3 — confirm I4 nav-screen value map (ties to your `nav-screen-mode-probe.sh`)
```bash
for v in 0 1 2 3 4 5; do NAV getraw setting 4C10E015; NAV setraw setting 4C10E015 $v; sleep 2; done
# screenshot each; map value → Đơn giản / Màn hình nhỏ / Toàn màn hình / OFF
```
DashCast uses **3 = activate** on nav start (not cleared on stop). Confirm which value = "Đơn giản (Giữa+ETA)" to set ClusterNav's default.

---

## Part C — reference (no action; context)

**Verified nav feature-ids (decimal authoritative → recomputed hex):**

| Name (DashCast/OpenBYD) | dec | hex | device |
|---|---|---|---|
| SEND_NAVI_STATUS (2=on,4=off) | 1138753594 | 0x43E0003A | instr |
| GUIDE_SIMPLE (icon+dist) | 1139806224 | 0x43F01010 | instr |
| GUIDE_ROAD_DISTANCE | 1139806256 | 0x43F01030 | instr |
| FRONT_CROSSING_DIST | 1139806232 | 0x43F01018 | instr |
| NEXT_PATHNAME (bytes) | 1140461576 | 0x43FA1008 | instr |
| NAVI_MILEAGE (rem. dist) | 1139810344 | 0x43F02028 | instr |
| NAVI_HOUR | 1139810320 | 0x43F02010 | instr |
| NAVI_MINUTE | 1139810328 | 0x43F02018 | instr |
| NAVI_REMAINING_SEC | 1139810334 | 0x43F0201E | instr |
| NAVI_LEAD_MSG | 1139834896 | 0x43F08010 | instr |
| DISTANCE_TARGET_AHEAD | 1139834904 | 0x43F08018 | instr |
| SETTING_NAVI_SCREEN (=3 on) | 1276174357 | 0x4C10E015 | setting |

**Permissions (from SDK Javadoc):** scheme `android.permission.BYDAUTO_<DEVICE>_<GET|SET|COMMON>`.
Instrument has only `..._GET` / `..._COMMON` (no grantable `..._SET`) → why cluster writes need a
privileged path. DashCast = **proxy daemon**; ClusterNav = **BydPermissionBypassContext** spoof. No BYD
platform private key exists in any artifact (only the public `auto_api@byd.com` cert on OEM apps + the
public Android debug key in the SDK), so "sign to get the perm" is not possible — current approach stays.

**Docs:** SDK scan report → `docs/specs/hud-keepalive-interp-log-1.15.html` (J1/J2) · `docs/diagnostics/byd-sdk-v1.0.5-scan.html` (SDK).

---

## Session results — 2026-08-13 evening (ran on 1.15, shipped 1.16)

Live over adb (`<vehicle-ip>`), car parked at home.

1. **OTA OK** — ClusterNav auto-updated to **1.15 (versionCode 115)**; drive-home log `nav_log_1786617743666.csv` is 1.15 format (8058 rows).
2. **`screenRead_m` was empty (0/8058)** — root cause: ClusterNav's `NavAccessibilityService` was **declared but not enabled** (only BYD vrassistant + systemui were in `enabled_accessibility_services`). **Fixed live**: appended the service + set `accessibility_enabled=1`; `dumpsys accessibility` now shows `ClusterNav — booster` bound → the next GMaps drive will populate the ground-truth column. *(Revert: remove that service from the secure setting. Better: self-grant it in-app over dadb, like the 1.13 notification-listener grant.)*
3. **Interp bias measured** (n=3239 moving, turn-approach <2000 m): `mean(display − GMaps-notification) = −34.5 m`, `mean(projected − GMaps) = −16 m`; histogram of `display − GMaps` piles at **−10 / −25 / −100** = exactly the `quantizeDisplay` **floor** buckets → the cluster reads *less* than Google.
4. **Shipped 1.16**: `quantizeDisplay` floor → **round** (nearest bucket) — removes the floor half of the bias. FACTOR (the −16 m interpolation part) left for next drive once the screen-read ground-truth is in.
5. **navopen getraw cross-check**: the nav guidance registers (`43E0003A`, `43F01010`/`43F01018`, `43FA1008`, `43F020xx`, `setting 4C10E015`) all return **−10011** = write-only / not gettable (nav also idle while parked). Can't read-validate — must write and look at the cluster.

### Queued for the next drive
- **Validate round + tune FACTOR**: drive with GMaps nav (accessibility now on) → `adb pull … nav_log_*.csv` → `python3 scripts/analyze-nav-distance-log.py …`. Expect `screenRead_m` populated now; confirm `display − screen` bias shrank, and read `projected − screen` to set FACTOR (raise toward 1.0 if projected still reads low).
- **ETA/mileage id render test** (needs eyes on the cluster while navigating): with GMaps nav active, `setraw instr 43F02028 <m>` (mileage), `43F02010`/`43F02018` (ETA h/m); watch the centre. getraw won't help (write-only); no restore needed (ClusterNav overwrites the next frame).
