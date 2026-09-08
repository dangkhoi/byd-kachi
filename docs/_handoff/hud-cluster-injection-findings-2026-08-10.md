# HUD / Cluster injection — on-car findings & handoff (2026-08-10)

> Vehicle: BYD DiLink 3.0 (DL3), adb over `<vehicle-ip>:5555`. Session was live on-car.
> Goal: put **custom navigation on the HUD (windshield)** and a **custom speed-limit number on the cluster** (and HUD).
> **Verdict: NOT achievable via any adb/app-reachable software path.** Proven exhaustively on-car. Remaining doors are **off-car only** (coding / native decompile / CAN) — see §6.

---

## 0. Straight answer: "is it fully out of options?"

- **adb / app / reflection layer: EXHAUSTED** (7 independent angles for the speed-limit number; HUD content confirmed coding-locked).
- **NOT absolutely closed.** Three off-car doors remain, untested here:
  1. **Variant/OBD coding** (most promising for HUD-nav; standard way BYD gates features per trim) — no coding tool available this session.
  2. **Decompile `DataSourceManager::receiveData2`** to confirm whether any byte/FlatBuffer channel feeds the traffic-sign (low probability; local Ghidra init is broken).
  3. **Direct CAN write** to the instrument MCU (deep, higher risk).

---

## 1. Architecture (verified this session)

Two independent subsystems drive the cluster:

- **Qt cluster compositor (Android side).** Renders the **nav overlay**, **warning lamps**, **projection surfaces**, and **traffic-sign ICONS**. Fed by external apps through the **AutoContainer bridge** → this is why nav injection works "over config".
- **MCU / ADAS (CAN side).** Computes the **speed-limit NUMBER** (ADAS SLA/SLR: camera + map fusion) and drives the **physical HUD** content. **No external push channel** — internal only.

`nav` is *designed* for external push (AmapService is an AutoContainer client). The **speed-limit number and HUD content are internal** → no reachable software channel.

### AutoContainer bridge (the key discovery)
- `getSystemService("AutoContainer")` → `android.os.AutoContainerManager` — **reachable** from a spoofed system context (navopen; `getPackageName=com.byd.dashcast`). Whitelist did **not** block us.
- Methods present on this ROM: `sendInfo(int id,int subtype,String)`, `sendInfo2(int channel, byte[])`. **No `sendJson`.**
- Client `sendInfo/sendInfo2` → cluster `DataSourceManager::receiveData(id,sub,str)` / `receiveData2(channel,bytes)`.
- Nav = `sendInfo2(4, NaviInfo FlatBuffer)`. AmapService also calls `sendInfo(5,0,"")`.
- `DataSourceManager` holds 578 `DATA_ITEM_ID_*` data-items incl. `DATA_ITEM_ID_TRAFFIC_SIGN_VALUE/_TYPE/_LIMIT_TRAFFIC_SIGN_RECOGNITION/_OVERSPEED_WARNING_LIGHT`.
- **`NaviInfo` FlatBuffer has NO speed-limit field** (verified from symbols + AmapService parser).

---

## 2. Feature-id map (HAL, verified on-car — rc=0 = write accepted)

| Feature | id (hex) | dev | R/W on this ROM | Effect |
|---|---|---|---|---|
| HUD config (0=none,1=W,2=AR) | `0x38B00015` | setting | read=1(W-mode); **write REJECTED** | — |
| HUD switch status / set | `0x38B0001C` / `0x4C10E023` | setting | r=1(on)/w ok | physical HUD on/off |
| HUD nav-content gate | set `0x4C10E03A`, status `0x38B00028` | setting | w rc=0 | **no visible HUD effect** (29/07 + reconfirmed) |
| HUD ADAS gate | set `0x4C10E030`, status `0x38B0001E` | setting | w rc=0 | **controls ADAS on HUD** (off→ADAS disappears) |
| Instrument HUD nav-map | set `0x32B1102E`, status `0x38B0002E`, config `0x38B00030` | instr | **write REJECTED; status/config=sentinel (not provisioned)** | the cluster→HUD mirror enable, **coding-locked** |
| ADAS SLA state | status `0x31600025`(=1 fusion), set `0x38500022` | adas | w 0/1 ok; **mode 3 REJECTED** | SLA off → speed sign becomes yellow-crossed "no limit" |
| ADAS SLA output speed-limit | `0x2D500020` | adas | **read-only (sentinel)** | the number the widget shows — not writable |
| ISA current-road speed-limit VALUE | `0x4B40001C` | statistic | **w rc=0 but display UNCHANGED** | SLA fusion ignores it (stays "20") |
| ISA traffic-sign TYPE | `0x4B400064` | statistic | **w rc=0 → RENDERS SIGN ICONS** | 1=roundabout, 2=crossroads, … (icons next to speed panel) |
| ISA type/unit/road-type | `0x4B400034/56/4B` | statistic | w rc=0 | no number effect |
| Setting SPEED_LIMIT_SET / change-switch | `0x4CA00040` / `0x4EF3603E` | setting | w rc=0 | no display effect |
| Setting ISA_MAP value/type/distance | `0x4B4000AA/BE/C1/CC/D7` | setting | **read-only (rejected)** | MCU-fed |
| Instrument TSR value / identify | `0x23A00010` / `0x2A60000E` | instr | **read-only (rejected)** | — |

**Speed-limit number source = ADAS SLA (`0x2D500020`, read-only) computed internally. Not settable by any writable id.**

---

## 3. Cluster nav render (WORKS — proven)

```
am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10001 --ei TYPE 1 \
  --ei EXTRA_STATE 1 --ei EXTRA_IS_FOREGROUND 0 --ez IS_BYD_MAP false --ez IS_BYD_BAIDU_MAP false \
  --ei NEW_ICON 3 --ei SEG_REMAIN_DIS 444 --es NEXT_ROAD_NAME 'Ba Test Le Loi' \
  --ei ROUTE_REMAIN_DIS 6000 --ei ROUTE_REMAIN_TIME 300 \
  --es SEG_REMAIN_DIS_AUTO '444 m' --es ROUTE_REMAIN_DIS_AUTO '6.0 km' \
  --es ROUTE_REMAIN_TIME_AUTO '5 min' --es ROUTE_REMAIN_TIME_STRING '5 min'
```
Stop: `KEY_TYPE 10019 EXTRA_STATE 9` (IS_BYD_MAP true then false). `IS_BYD_MAP=true`+TYPE=1 also renders on cluster; HUD unaffected. `TYPE=8` → nothing on this unit.

AmapService parser reads only: `IS_BYD_MAP, KEY_TYPE, TYPE, NEXT_SEG_REMAIN_DIS, SEG_REMAIN_DIS, NEXT_ROAD_NAME, NEXT_NEXT_ROAD_NAME, ROUTE_REMAIN_DIS, ROUTE_REMAIN_TIME, TRAFFIC_LIGHT_NUM, ROUNG_ABOUT_NUM, NEXT_ROUNG_ABOUT_NUM, NEW_ICON, NEXT_NEXT_TURN_ICON, ETA_TEXT, EXIT_NAME_INFO, EXIT_DIRECTION_INFO, ROUTE_REMAIN_*_AUTO, SEG_REMAIN_DIS_AUTO, EXTRA_STATE`. **No speed-limit key.**

---

## 4. What WORKS vs NOT (proven on-car)

| Target | Result |
|---|---|
| Nav (icon+road+distance) → cluster | ✅ works (broadcast) |
| Traffic-sign ICONS → cluster | ✅ works (`statistic 0x4B400064`) |
| Warning lamps / projection | ✅ works (AutoContainer `sendInfo(1000,op)`) |
| AutoContainer bridge callable | ✅ `sendInfo(5,0,"") -> 0` |
| **Custom speed-limit NUMBER → cluster** | ❌ internal ADAS SLA, not settable |
| **Nav / road / speed → physical HUD** | ❌ coding-locked (nav-map capability read-only) |

---

## 5. Approaches EXHAUSTED for the speed-limit number (all failed)

1. HAL value ids (statistic/adas/setting/instr) — read-only or ignored by SLA fusion.
2. Mass-write of all ~33 writable speed-limit ids = 66 → number unchanged.
3. AMap broadcast — no speed-limit field in parser/NaviInfo.
4. AutoContainer `sendInfo(id,0,"88")` swept **id 0..578** → zero visible effect (string channel is not the display path).
5. `sendJson` — does not exist on this ROM's AutoContainerManager.
6. ISA traffic-sign-TYPE sweep 0..20 with value=88 → drew sign ICONS but **never a settable number**; SLA "20" unchanged.
7. SLA mode change to nav-only (`0x38500022=3`) → REJECTED.

HUD: config `0x38B00015`→2 REJECTED; nav-map `0x32B1102E` + config `0x38B00030` REJECTED (not provisioned). No `hud`/`sla` system property (only `sys.init.navi_protect`, `sys.change_navi_auth`).

---

## 6. Remaining OFF-CAR doors

1. **Coding (variant/OBD)** — enable "HUD content = navigation" variant so the MCU mirrors cluster nav to the physical HUD (this car's menu only offers "ADAS safety" content → nav-HUD is coded OFF). Needs a BYD coding/diagnostic tool. **Most promising; untested.**
2. **Native decompile** of `DataSourceManager::receiveData2` + `initDataSource` (binaries at `firmware/fw-2602-diff/cmp/libBydCluster_{OLD,NEW}.so`) to confirm whether any `sendInfo2` byte-channel feeds traffic-sign. Local Ghidra 12.1.2 (`<cache>/clusternav-re/tools/`) fails init with **"Unable to locate extension points!"** despite complete install (tried: removing `timeout` (absent on macOS), clearing `~/Library/ghidra/...`, `xattr -cr`). Use a working Ghidra/IDA on another machine. Low probability (evidence says traffic-sign is internal ADAS).
3. **Direct CAN write** to the instrument MCU for the SLA/HUD signals — deep RE + risk on the instrument bus.

---

## 7. Tools built this session (ready for reuse)

- **`apks/navopen-v3.jar`** — extended navopen (source `<byd>/NavOpen/src/com/byd/navopen/NavOpen.java`, outside repo):
  - Added `statistic` device + generic FQN device resolver.
  - `Runtime.halt(0)` at end so the process **exits ~1s** (fixed the earlier "hang": main Looper kept the JVM alive).
  - Commands: `getraw/setraw <instr|setting|adas|statistic> <hexid> [val]`, `multi`/`mget` (batch in one JVM), `acprobe`, `ac <id> <sub> <str>` (sendInfo), `ac2 <ch> <hex>` (sendInfo2), `acrange <s> <e> <sub> <str>`.
  - Run: `adb -s <vehicle-ip>:5555 shell CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen <cmd>`
- **`scripts/vehicle/hud-cluster-probe.sh`** — safe prior→write→observe→restore field runner (4 groups).
- **`docs/diagnostics/hud-sign-re/hud-cluster-field-checklist.html`** — fillable field checklist.
- Build navopen: `JAVA_HOME=<jdk17> javac -classpath <sdk>/platforms/android-34/android.jar -source 11 -target 11 NavOpen.java` → `d8 --min-api 26 --lib android.jar --output navopen-v3.jar *.class`.

---

## 8. Cleanup / vehicle state

- All HAL writes restored best-effort; SLA cycled. **Reboot the head unit once to guarantee a 100% clean cluster** (removes any lingering injected sign; SLA re-reads the real limit).
- `navopen.jar` left at `/data/local/tmp/` (harmless).
- No repo commit made.

---

## 9. Recommended next step

Pursue **coding** (door #1) off-car: identify the BYD coding channel/variant for "HUD navigation content", flip it, then re-test — if it opens, the existing broadcast nav render should mirror to the HUD with no further code. If coding is unavailable, this line is effectively closed for a hobby setup.

---

## 10. UPDATE — deeper native RE (the enable flag found)

**#1 (nav→HUD) enable flag identified — the "kẹt 1 cờ" the owner suspected:**
- **`INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG = 0x38B00030`** — the provisioning/capability flag.
  - Consumer `Hud00600401300000.readSelfLearnState()`: HUD nav-map is enabled when this **config == 1**.
  - This car reads it = **sentinel (not provisioned)**. On BYDs where "cluster nav auto-mirrors to HUD", it = **1**.
  - Related ids: `..._STATUS 0x38B0002E` (read), `..._SET 0x32B1102E` (write **REJECTED** because config not provisioned).
- **Not writable from the head unit:** HAL `set` rejected; the "self-learn" mechanism (`VisibleFromSelfLearnUtil`) only **mirrors HAL/MCU state into a local `CarSettingsDb`** (not a writable provisioning store); whole-firmware search found **no factory/diagnostic app that writes MCU coding**.
- ⇒ **To enable: set `0x38B00030 = 1` via an external diagnostic/coding tool (OBD → instrument ECU variant coding, UDS `WriteDataByIdentifier`). Not adb.** Then the existing broadcast/HAL nav render should mirror to the HUD automatically.
- Also: this ROM's settings APK renders **only the ADAS button** in "Optional display content" (`HudOptionDisplay`); the nav update method is a logging no-op → consistent with nav-HUD being un-provisioned.

**#3 (speed-limit number) — binary-confirmed internal:**
- `DataSourceManager::receiveData2(channel, bytes)` handles exactly **channel 4 = NaviInfo (nav)** and **channel 8 = PadToClusterReq → handleIviRccReqMsg (generic RCC RPC: cmdId/subId/intParam1/strParam1)**. **No traffic-sign channel.**
- `receiveData(id, sub, string)` swept **id 0..578 → zero visible effect** (control path, not display).
- All data-items (incl. `DATA_ITEM_ID_TRAFFIC_SIGN_VALUE`) are set by the internal central dispatcher **`DataSourceManager::handleDataItemChanged(int dataItemId, QVariant)`** (~22 KB switch), fed by an **internal subscriber**; libBydCluster has **no CAN-reading code** → the CAN signal id lives in a **separate vehicle-data service** (not this binary).
- ⇒ speed-limit number reachable **only via CAN** (`#3`): sniff to find the SLA frame, then inject (parked). CAN id must come from a CAN dump or RE of the vehicle-data service (not libBydCluster).

**AutoContainer bridge (confirmed reachable, for reference):**
- `getSystemService("AutoContainer")` → `AutoContainerManager`; `sendInfo(int,int,String)` returns 0 (callable); `sendInfo2(int,byte[])` present; **no `sendJson`**.
- Carries nav (ch4) + RCC (ch8) + warning-lamps/projection (id 1000 opcodes). **Not** traffic-sign / HUD.

**Net:** both goals now require **hardware/tools, not adb** — #1: BYD coding tool (set `0x38B00030=1`); #3: CAN interface (sniff+inject). Give the coder the exact string: *"provision instrument-ECU HUD navigation map config `0x38B00030` = 1"*.

---

## 11. NEW candidates for next on-car session (cross-ref community RE: github wheregoes/byd-dolphin-hacking, same DiLink 3)

**Key advantage:** community car = `fission_single_os=1` (single-OS) → AutoContainer **blocked** ("no AutoContainerNative"), all cluster-debug commands fail. **Our car = `fission_single_os=0` (dual-OS)** and AutoContainer is **live** (proven: `sendInfo(5,0,"")→0`). ⇒ the cluster-debug commands they couldn't run, **ours likely can**.

**Candidate A — ClusterDebug commands via `sendInfo(1000, <cmd>, "")`** (= `navopen ac 1000 <cmd> ""`). I earlier brute-forced the wrong axis (swept id 0..578 with sub=0); **id=1000 + cmd was never tried.** Command table (from ClusterDebug.apk):
- **39 = Simple navigation** ← primary target for nav display / possible HUD mirror
- 12/13 = show/hide ADAS · 6/7 = day/night · 8/9 = classic/tech dashboard · 34/35 = Di3.0/Di4.0 mode · 16/17/18 = cast on/off · 88/89 = car-body image
- **AVOID: 1 (disconnect cluster video), 41 (stress test — DO NOT USE), 18 (cast off).**
- Order on car: `ac 1000 7 ""` then `ac 1000 6 ""` (night/day — sanity that ch1000 works), then `ac 1000 39 ""` (nav), `ac 1000 12 ""` (ADAS).

**Self-verify without eyes:** `fission_screencap -d 1 -p /data/local/tmp/c.png` + `adb pull` → capture the cluster (works on DiLink 3). (Windshield HUD is a separate display, not a fission screencap target.)

**Candidate B — CAN injection (VCDS-style, no root/OBD) for #3 speed-limit:**
- `am start -n com.byd.clusterdebug/.MainActivity` → `am startservice -n com.byd.clusterdebug/.ClusterDebugService`
- `am broadcast -a com.byd.cluster.spi --es wholeFrame '<CANID,bytes...>'` (or `--es normal '<bytes>'`) → ClusterDebug injects via `BYDAutoTestDevice.set(TEST_SIMULATE_DOWN_SET 0xAA00020F, bytes)` → MCU/CAN.
- Still need the **SLA speed-limit CAN frame** (arbitration id + payload). Get by CAN sniff (log while limit changes) or RE the vehicle-data service.

**Candidate C — UDS coding for #1 provisioning (`0x38B00030=1`):**
- `BYDAutoOtaDevice.set({0xAA000140}, udsFrameBytes)` sends UDS; `registerListener(…, {0x99000140})` for responses (from `BydDevelopmentTools.apk`).
- UDS: `10 05` (extended session), `3E 80` (tester present), `2E`/`22` (write/read DID). CAN domains incl. **ADAS net**; sample ids: left 0x720/0x728, right 0x747/0x74F, IPB 0x782/0x78A.
- **Missing:** the exact DID that codes HUD-nav. Needs sniff with official BYD tool or more RE of `BydHealthDiagnostic.apk`.

**Prereqs to verify on our car:** `com.byd.clusterdebug` present (priv-app), `BydDevelopmentTools.apk` present, `com.byd.cluster.spi` receiver exists. Master sideload password (DiLink 3): `BYD6125F`.

### 11.1 Caveat (honest) — DiLink-3.0 command subset

The `sendInfo(1000,cmd)` command table differs by DiLink generation. Per the community RE, **DiLink-3.0 MainActivity exposes only a SUBSET**: `0,1,14,15,19,20,36,200,201,212,218,219,257-268,278,279` (FPS, OSD frames, screenshot, screen-record, light/ADAS indicators, racing modes, resume/disconnect video). **`39` (Simple navigation), `6/7` (day/night), `8/9` (classic/tech) are in the fuller newer-DiLink list and are NOT confirmed on DiLink-3.0.** So `ac 1000 39 ""` is a **probe**, not a guaranteed nav-HUD win. Command set may also vary by our specific model (fw-2602) vs the Dolphin.

Reality check for the trip: the cluster-debug 1000-channel is a **new axis worth probing** (our dual-OS car can run what their single-OS car couldn't), and `fission_screencap -d 1` is worth confirming (self-verify future tests). But **no confirmed nav→HUD command** exists on the DiLink-3.0 list. The most grounded nav→HUD path remains provisioning `0x38B00030=1` via UDS (`BYDAutoOtaDevice 0xAA000140`), which needs the exact coding DID (not yet known — sniff with official tool or deeper RE). Speed-limit→cluster remains CAN-inject via `BYDAutoTestDevice 0xAA00020F` (needs the SLA CAN frame).

### 11.2 CAN primitives on our firmware (Test.java) + navopen-direct approach

- **`TEST_SIMULATE_DOWN_SET = 0xAA00020F`** — inject CAN frame (host→MCU direction; what ClusterDebug uses).
- **`TEST_SIMULATE_UP_SET = 0xAA000210`** — inject in the "UP" direction (likely MCU→cluster/IVI); **new candidate** — injecting the SLA frame here may make the cluster render it as if from the real ADAS. Try for #3.
- **`TEST_CANIN_TEST_*`** — specific CAN-in test ids: `0x6B001040` (6B0), `0x6B401040` (6B4), `0x6DC00044/0444/0844` (6DC), `0x6F100044/0444/0844` (6F1), `0x6F400044/0444/0844` (6F4). Try **getraw** on these — may reflect live incoming CAN (partial sniff of those arbitration ids).
- **`TEST_NOTIFY_MCU_WAKEUP_CAN_NETWORK_MODULE_SET = 0xAA000330`**, `TEST_SHUTDOWN_FROM_CAN = 0x6E94D010`.
- **navopen can drive the TEST device directly** (permission-bypass context) — no need for `com.byd.clusterdebug`:
  - device FQN: `android.hardware.bydauto.test.BYDAutoTestDevice` (use `getraw/setraw <fqn> <id>` or extend navopen shortname `test`).
  - CAN inject = `setBytes(TEST, 0xAA00020F, frameBytes)` / `0xAA000210`. Frame format (from community): `[featureId_BE:4][dataLen:1][data...]`; `wholeFrame` includes the CAN id.
- **Still missing for #3:** the SLA speed-limit CAN arbitration id + payload. Get by: reading `TEST_CANIN_TEST_*`, a CAN sniff (hardware), or RE the vehicle-data service / `DiCarServer` (SLA→CAN mapping) — not in libBydCluster.

### 11.3 Prioritized plan for the next 15-min on-car session (honest confidence)
1. **Probe cluster 1000-channel** (new axis; dual-OS advantage): `navopen ac 1000 <cmd> ""` for DiLink-3.0 cmds (e.g. 259/260 ADAS indicators, 257/258 lights, 218 record) + try 39/6/7/8/9 anyway (may not exist on DL3). Verify with `fission_screencap -d 1`. *Exploratory, not a sure nav-HUD win.*
2. **Read `TEST_CANIN_TEST_*`** (getraw) — see if any reflects live CAN (would enable capturing the SLA frame).
3. **Confirm helper apps** on car: `pm list packages | grep -i 'clusterdebug\|development'`; check `getSystemService("AutoContainer")` still live.
Deeper (need missing piece): #1 = UDS provision `0x38B00030=1` (need coding DID); #3 = inject SLA frame via `0xAA000210/0xAA00020F` (need the frame).

---

## 12. ONE-TRIP CANDIDATE CHECKLIST (navopen-v3 turnkey; run parked)

navopen-v3 now supports: `getraw/setraw` (int), **`setbytes/getbytes`** (byte payloads — CAN/UDS), `ac/ac2/acrange/acprobe` (AutoContainer), device shortnames incl. **`test`** (`BYDAutoTestDevice`) and **`ota`** (`BYDAutoOtaDevice`). Prefix each: `CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen <cmd>`.

### Phase 1 — READ/PROBE (safe, gather intel first)
1. Env: `pm list packages | grep -iE 'clusterdebug|develop'` ; `navopen acprobe` (AutoContainer live?).
2. **Cluster 1000-channel probe** (dual-OS advantage): `ac 1000 259 ""` /`260`/`257`/`258`/`218`, then probe `39`,`6`,`7`,`8`,`9`,`12` (may not exist on DL3). `fission_screencap -d 1 -p /data/local/tmp/c.png` + pull after each notable one. Watch cluster + HUD.
3. **CAN/diag reads (getbytes)** — try to sniff live frames:
   - `getbytes test 6B001040` · `6B401040` · `6DC00044` · `6F100044` · `6F400044` (CANIN — do they reflect live CAN?)
   - `getbytes ota 99000140` (UDS candata) · `getraw test 99000057` (MCU state)
   - **`getbytes ota 99000053`** (ECU softcode/coding read — reveals coding structure for #1).
4. Re-read the flag: `getraw instr 38B00030` (HUD nav-map config; target=1).

### Phase 2 — UDS PROBE (for #1 coding DID; read-only)
5. Extended diagnostic session to instrument ECU via OTA multi-frame, then read DIDs:
   - `setbytes ota AA000140 <udsFrame: session 10 03>` → `getbytes ota 99000140` (response)
   - Frame envelope (community): `chkHi,chkLo, totalPkts, pktNum, dataLen, 00,03,E8, 00,01, reqIdHi,reqIdLo, 01, recvIdHi,recvIdLo, <UDS payload>`. Instrument-ECU diag CAN id: **unknown — discover here** (try known domains; ADAS/instrument).
   - Sweep `22 <DID>` (ReadDataByIdentifier) to find the HUD-nav coding DID.

### Phase 3 — INJECT (WRITE — higher risk; only after Phase 1–2 intel, parked)
6. **#3 speed-limit via CAN**: `setbytes test AA000210 <SLA CAN wholeFrame>` (SIMULATE_UP → cluster) and/or `AA00020F` (DOWN). Needs the SLA frame (from Phase-1 CANIN capture or a hardware sniff).
7. **#1 provision via UDS coding**: `setbytes ota AA000140 <2E DID 01>` (WriteDataByIdentifier). ⚠ Likely needs **security access** (27 seed/key) first — a real gate; and writing ECU coding can set DTCs/misconfig. Do last, accept risk, have `pm`/reboot recovery.

### Honest confidence
- Phase 1–2 = **safe intel-gathering**, high value (finds the SLA frame + coding DID/softcode that unblock #3/#1). Worth the trip.
- Phase 3 writes = the actual wins, but gated by missing frame/DID + likely UDS security access. Not guaranteed same trip.
- The 1000-channel probe (step 2) is the one that *might* surprise with a nav→HUD effect on our dual-OS car.

---

## 13. DECISIVE off-car intel from system.img (extracted 2026-08-10)

Extraction (reproducible): `system.img` is ext4; macOS has no 7z/fuse but **`brew install e2fsprogs`** gives `debugfs`. Dump files with `debugfs -R "rdump /system/priv-app/<App> <out>" <img>` / `dump /system/lib64/<lib> <out>`. Extracted to `<cache>/clusternav-re/sysimg/`: **ClusterDebug, DiCarServer, BydDevelopmentTools, BydHealthDiagnostic, CanDataCollect** APKs + `libbydauto.so`, `libBydDataSource.so`, `libbyddiagnosticservice.so` + `/system/etc/diagnostic_config.json`. Decompiled with `jadx -d <out> --no-res <apk>`.

### 13.1 ClusterDebug command channel (CONFIRMS navopen path)
`MainActivity`/`SecondActivity`: `mAutoContainerManager.sendInfo(1000, <cmd>, "")` — **identical to `navopen ac 1000 <cmd> ""`**. Device resolve for Di3.0: `getSystemService("AutoContainer")` (matches our car).
- **Di3.0 MainActivity subset** (top menu): `0,1,14,15,19,20,36,200,201,212,218,219,257,258,259,260,261,262,263,264,265,266,267,268,278,279`.
- **SecondActivity full list `0–107`** (reached via "高级测试" button; base list NOT version-gated) includes: **`39:简易导航`(Simple Navigation)**, `6/7`(day/night), `8/9`(classic/tech), `12/13`(show/hide ADAS), `16/17/18`(cast full/half/off), **`45`(show ADAS self-learning result — includes speed-limit)**, **`47/48`(ADAS debug mode on/off)**, `53`(2D ADAS). `86/87`(HUD level-1/2 menu) are **R4/Di6-gated → likely absent on Di3.0**. AVOID `41`(stress test), `91/92`(SIGABRT/SIGSEGV), `1/18`.
- **Correction to §11.1:** cmd 39 IS a valid base command (not version-gated) → `ac 1000 39 ""` is a legitimate try on our car, not a long shot.

### 13.2 CAN inject mechanism (CONFIRMS `setbytes`)
`BroadcastReceiverCAN`: extras `wholeFrame` or `normal` = comma-separated hex bytes → `byte[]` → `BYDAutoEventValue.bufferDataValue` → `BYDAutoTestDevice.getInstance().set(new int[]{-1442840049}, ev)`. **`-1442840049` == `0xAA00020F` == TEST_SIMULATE_DOWN_SET.** So the community `am broadcast com.byd.cluster.spi --es wholeFrame` == **`navopen setbytes test AA00020F <hex,hex,..>`** (our new command). Both need the actual SLA CAN frame bytes (still the missing piece for #3).

### 13.3 Speed-limit feature-ids (from firmware Adas.java)
- `ADAS_SLA_OUTPUT_SPEED_LIMIT 0x2D500020` — SLA output, **read-only** (confirmed inert to writes).
- **`ADAS_SPEED_LIMIT_ASSIST_OFFSET_KPH_SET 0x43F03028`** — settable offset; try `setraw adas 43F03028 <n>` (may shift displayed/assist limit). MPH variant `0x43F0302C`.
- `ADAS_TSR_SPEED_LIMIT_MAP_CONFIG 0x4320002A`, `ADAS_SMART_SPEED_LIMIT_CONTROL_SET 0x32B0E018` — config candidates.
- `libBydDataSource.so` field names feeding the cluster: `trafficSignValue`, `trafficSignType`, `trafficSignalStatus`, `overspeedWarningLight` (via `BydPropertyContext`) — source of the cluster number; maps to the read-only SLA id, so injection must come via CAN (13.2) or a DiCar property write.

### 13.4 Still-missing (next digs, off-car)
- **SLA CAN frame** (arbitration id + payload) for #3 → dig `CanDataCollect` + `DiCarServer` (property↔CAN map) + `libbydauto.so`.
- **UDS coding DID** for #1 (`0x38B00030=1`) + security-access → dig `BydDevelopmentTools` + `libbyddiagnosticservice.so` + `BydHealthDiagnostic`.

---

## 14. VERDICT after full APK dig (honest ceiling + refined candidates)

The head-unit software abstracts everything to **feature-ids**; the raw **CAN arbitration ids + UDS DIDs + coding + seed/key live in the MCU/ECU** (not in system.img). Confirmed by: DiCarServer only name-maps feature-ids (no CAN frame table); BydDevelopmentTools has no arbitrary-DID coding UI (RepairMode/RollbenchMode/Obd use an obfuscated diag helper `a.a.a.l0.b` for narrow functions + password verify, not WriteDataByIdentifier); no CAN matrix in any APK asset.

### Honest ceiling
- **#1 nav→HUD (`0x38B00030=1`):** the flag is MCU **softcode** set by **factory/dealer UDS coding** (proprietary DID + security-access seed/key). No head-unit app performs it. **Not reachable from ADB alone.** The HUD nav-map render is gated on this flag → windshield-HUD navigation is effectively **dealer-tool-only** (or not doable). `ac 1000 39` "简易导航" is a **cluster** feature, not the windshield HUD.
- **#3 speed-limit number on cluster:** inject **mechanism is ready** (`setbytes test AA00020F <frame>` == ClusterDebug), but the **SLA CAN frame (id+payload) is MCU-side** — not in the head-unit image. HAL id `0x2D500020` is read-only. Needs an on-car CAN capture or MCU RE.

### What the dig unlocked (real, new, trip-worthy)
1. **New cluster-command probes** (all base, valid on Di3.0 via `ac 1000 <cmd> ""`): **39** (simple nav → cluster), **45** (show ADAS self-learning result — includes speed-limit), **47/48** (ADAS debug mode on/off), **12/13** (show/hide ADAS), **6/7** (day/night), **8/9** (classic/tech), **16/17** (cast full/half). Self-verify each with `fission_screencap -d 1`.
2. **Settable speed-limit offset**: `getraw adas 43F03028` then `setraw adas 43F03028 <n>` (`ADAS_SPEED_LIMIT_ASSIST_OFFSET_KPH_SET`) — may shift the displayed/assist limit (partial #3).
3. **CAN-frame capture attempt**: `getbytes test 6B001040/6B401040/6DC00044/6F100044/6F400044` — if any reflects live CAN, capture the SLA frame → then `setbytes test AA00020F <captured, edited>` to inject #3.
4. Full local reference copy of ClusterDebug/DiCarServer/DevTools/HealthDiagnostic/CanDataCollect + HAL libs in `<cache>/clusternav-re/sysimg/` for any future lookup.

### Remaining off-car option (if pushing #3 further)
RE `libbydauto.so` (135KB, native) for the feature-id→SPI packet framing of `0xAA00020F`, and objdump `libBydDataSource.so` for how `trafficSignValue` is parsed from the incoming frame — could reveal the byte layout to craft an SLA frame without a live capture. Uncertain payoff (arbitration id still MCU-side).

---

## 15. CORRECTION to §14 — #3 has a concrete door (statistic ISA setters)

§14 was too pessimistic on #3. `Statistics.java` (our firmware) defines a full family of **settable** ISA speed-limit ids in the SAME device as the traffic-sign icon that already rendered on the cluster (`STATISTICS_ISA_TRAFFIC_SIGN_TYPE_SET 0x4B400064`, proven):

- **`STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET = 0x4B40001C`** ← the speed-limit **VALUE** (top #3 candidate).
- `STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_TYPE_SET 0x4B400034`, `..._TIME_CONDITIONAL 0x4B400021` / `0x4B400023`, `..._UNIT 0x4B400056`, rain `0x4B400041`, snow `0x4B400046`.
- Also: `SETTING_SPEED_LIMIT_SET 0x4CA00040` (setting device), `INSTRUMENT_ELECTRONIC_EYE_OVERSPEED_WARNING_SET 0x4C108044` (instrument device).

Why earlier mass-write was inert: wrong device / SLA fusion overwrote it each cycle. **Refined on-car method for #3:**
1. `setraw statistic 0x4B40001C <kph>` via the SAME statistic device the icon injection used (navopen `setraw statistic 4B40001C 60`).
2. **Control the overwriter**: try with `ADAS_SLA_STATE_SET 0x38500022 = 0` (SLA off) and `ADAS_ISLA_SWITCH_SET 0x38500044` toggled, so SLA fusion doesn't recompute over our value.
3. **Hold it**: repeat-write in a tight loop (navopen `multi` / a shell loop) to beat any refresh.
4. Also feed the sign type `0x4B400064` (icon) alongside so the sign frame renders with our number.
5. If value is a MAP-limit INPUT to fusion, it may render only when the map source is the active SLA source — test with camera source suppressed.

**Net:** #3 is NOT blocked — it has a concrete, proven-device injection id (`0x4B40001C`) + a refined method. Real chance (not certain; SLA-fusion overwrite is the risk). Only **#1 windshield-HUD nav-map** remains the hard dealer-coding wall (`0x38B00030`).

---

## 16. CORRECTION to §14/§1 — #1 has an untried writable HUD control surface

§14's "HUD closed" was over-stated: it's true only for the **MAP widget** (`INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG 0x38B00030`, MCU-coding, `0x38B0` family). But there's a whole **writable HUD/nav control surface** in the `0x4C10E0xx` Setting family (menu-settable, NOT MCU-coded) + settable guidance feeds — **never tried together**:

**HUD/nav control (Setting, `0x4C10E0xx` — likely writable via `setraw setting`):**
- `SET_HUD_SWITCH_SET 0x4C10E023` (HUD on/off), `SET_HUD_MODE_SET 0x4C10E025`, `SET_HUD_MODE_CHOICE_SET 0x4C10E03C`
- `SET_NAVI_SCREEN_STATUS_SET 0x4C10E015`, `SET_MAP_SENDING_STATUS_SET 0x4C10E01D`, `SET_DYNAMIC_NAVI_FUNCTION_STATUS_SET 0x4C10E03A`
- `SETTING_HUD_IMAGE_TEXT_INFO_FUSION_SWITCH_SET 0x4C10E044`, `SET_MULTIMEDIA_INFO_FUSION_SWITCH_SET 0x4C10E03E`, `SETTING_EASY_NAVI_SIGNAL_MAP_TYPE 0x4C10E040`

**Fusion switches (obfuscated ids):**
- `SET_NAVIGATION_FUSION_SWITCH_SET = 0x8e2fcdbf` — enable nav fusion onto HUD/cluster (**#1**).
- `SET_SAFETY_DRIVING_AID_FUSION_SWITCH_SET = 0xd61b6746` — disable to stop SLA overwriting injected speed-limit (**#3**). FSE variants `0x4C500012`/`0x4C500010`.

**Guidance feeds (Instrument, settable turn-by-turn — NOT the coding-gated map):**
- `INSTRUMENT_GUIDE_INFO_SIMPLE_SET 0x43F01010`, `..._AND_ROAD_AHEAD_DISTANCE_SET 0x43F01030`, `..._SAFETY_SET 0x43F04010`, `..._CAMERA_SET 0x43F03010`, `..._ADVANCED_ACTION_SET 0x43F08030`, `INSTRUMENT_FRONT_CROSSING_DISTANCE_SET 0x43F01018`
- `INSTRUMENT_EASY_NAVI_GUIDE_INFOR_SET 0x1F701010`, `INSTRUMENT_HUD_NAVIGATION_MAP_SET 0x32B1102E`, `INSTRUMENT_DYNAMIC_NAVI_FUNCTION 0x38B0002A`.

### Revised #1 stance
The full **map** on HUD is coding-gated (0x38B00030). But **turn-by-turn guidance on HUD** is untried: `setraw setting 0x4C10E023=1` (HUD on) + `0x4C10E03A=1` (dynamic navi) + `0x4C10E015`/`0x4C10E01D` (navi screen/map sending) + `SET_NAVIGATION_FUSION_SWITCH 0x8e2fcdbf=1`, then feed `INSTRUMENT_GUIDE_INFO_SIMPLE_SET 0x43F01010` (or `setbytes`) and watch the HUD. Also try `INSTRUMENT_HUD_NAVIGATION_MAP_SET 0x32B1102E` (a SET, distinct from the coding CONFIG) — may push HUD nav content without the gate. **Real chance for turn-by-turn on HUD; the full moving-map likely still needs coding.**

### Revised #3 method (add to §15)
Before/around injecting `statistic 0x4B40001C`, disable the overwriter: `setraw setting 0xd61b6746 0` (safety-aid fusion off) — cleaner than only SLA-state off. Then inject + hold.

### Honest calibration
These are settable feature-ids (writable by convention, `0x4C10E0` = menu settings not MCU-coded) → navopen can drive them. Whether the HUD physically renders depends on W-mode + gate scope — **untried, worth a real on-car test**. Not a guarantee, but #1 is NOT the flat wall §14 implied.

---

## 17. Turnkey field scripts (ready, QA'd off-car)

Two self-contained scripts in `scripts/vehicle/` (auto-detect device + navopen jar; every adb call hard-timeout-guarded; save→act→verify→restore; reboot fully cleans). Run parked.

- **`hud3-speedlimit.sh [SPEED_KPH] [SIGN_TYPE]`** — #3. Injects `statistic 0x4B40001C = SPEED` and tries 3 combos: (C) as-is, (A) safety-aid **fusion off `0xd61b6746=0`** [the bet], (B) + ISLA off. Screencaps cluster after each (`./hud3_*.png`). Restores fusion/SLA/ISLA. Run: `VEH=<ip:port> NAVOPEN_JAR=<path> ./hud3-speedlimit.sh 60`.
- **`hud1-nav-hud.sh`** — #1. Enables the writable HUD/nav surface (`setting 0x4C10E023/03A/015/01D` + nav-fusion `0x8e2fcdbf`, HUD mode `0x4C10E025`), triggers nav (`ac 1000 39` + `instr 0x32B1102E`/`0x43F01010`), cycles HUD modes. **HUD is NOT screencap-able → operator must eyeball the windshield.** Restores the Setting surface.

QA verified off-car: `bash -n` clean; `argi=Integer.parseInt` (decimal); `ac 1000 39`→`sendInfo(1000,39,"")`; `getFeatureIntRaw` logs `get <tag> (0x..) = <v>` (parsed by `read_val`); relative jar path `../../../apks/navopen-v3.jar` resolves. Not run on-car yet.

---

## 18. #1 turn-by-turn on HUD — likely enabler found (NO coding needed)

The owner's real #1 goal is **turn-by-turn (arrow + distance + road name)**, NOT the moving map. That target does **not** hit the coding wall (`0x38B00030` gates only the MAP).

Dug the built-in nav app `BydAutoTMap` (`/system/app`, extracted): its dex references **`sendSimpleGuidanceInfo` / `sendSafeGuidanceInfo` / `sendCameraGuidanceInfo`** on `BYDAutoInstrumentDevice` (→ `INSTRUMENT_GUIDE_INFO_SIMPLE_SET 0x43F01010`) AND a preference **`PREFKEY_TMAP_SETTING_G_USE_HUD_VIEW`** plus `SET_HUD_SWITCH_SET` / `SET_HUD_SWITCH_STATUS_FEEDBACK` / `hudMode` / `SET_HUD_CONFIG`.

**Implication:** the turn-by-turn→HUD plumbing already exists in software. TBT on HUD is plausibly enabled by:
1. **HUD switch on**: `setraw setting 0x4C10E023 = 1` (+ `SET_HUD_MODE_SET 0x4C10E025`). This makes the nav app's `SET_HUD_SWITCH_STATUS_FEEDBACK` report HUD present.
2. **Nav app "use HUD view" ON**: enable the HUD toggle in the BYD map app settings (pref `PREFKEY_TMAP_SETTING_G_USE_HUD_VIEW`) — the toggle likely appears only once (1) reports HUD present.
3. Run navigation → nav app calls `sendSimpleGuidanceInfo` → guidance renders on HUD (if the cluster HUD scene has the TBT widget — the one remaining car-only unknown; the compressed `cluster_theme*.rcc` couldn't be inspected off-car).

This is far more hopeful than §14 implied: **no dealer coding for turn-by-turn** — just the HUD switch + the nav app's own HUD-view setting. Only open question = does this trim's HUD scene render the TBT widget (answer on-car). `hud1-nav-hud.sh` sets the HUD switch/mode; operator then enables "HUD" in the map app and drives.

---

## 19. FINAL VERDICT — on-car live run + TMAP RE (2026-08-10 night)

### On-car live results (car at vehicle-ip, navopen halt+watchdog, no hangs)
- **navopen HAL writes reach the HUD for SETTINGS but NOT for content.** Proven: sweeping `SET_HUD_MODE 0x4C10E025`/`0x4C10E03C` **changed the HUD color (blue↔white) on command** (owner confirmed). But `SET_HUD_SWITCH 0x4C10E023=0` did NOT turn the HUD off; height/brightness (`0x40C07010/018`) rejected.
- **#3 speed-limit: blocked.** `setraw statistic 0x4B40001C 88` → **rc=0 but cluster still shows "20"** (owner confirmed). `adas 0x2D500020` (SLA output, the actual display source) → **rc=-2147482648 (read-only)**. `setting 0x4CA00040` accepted (rc=0) but no display change. `0x4C108044`/`0x43F03028` rejected. Cluster renders the read-only SLA fusion output; our writes are ignored.
- **#1 nav→HUD: blocked.** Called the exact API `BYDAutoInstrumentDevice.sendSimpleGuidanceInfo(icon,dist)` (added navopen `callm`) sweeping icons + `sendSafeGuidanceInfo` + `navistate 1` + enabling `0x4C10E036/03A/015/01D` → **rc=0 everywhere, HUD showed nothing** (owner confirmed HUD unchanged: still km/h + speed-limit + ADAS, no nav arrow).
- HUD hardware **exists and works** (shows km/h, speed-limit, ADAS) — owner confirmed. It just has no turn-by-turn nav element.

### TMAP RE (installed `com.tmap.auto.byd` = SKT TMAP Korea; then decompiled)
- Installed via `pm install /system/app/BydAutoTMap/BydAutoTMap.apk` (Success) — but it's the **Korean** SKT TMAP (no VN maps).
- **TMAP does NOT push nav to the BYD HUD:** no caller of `sendSimpleGuidanceInfo` anywhere; it only **READS** telemetry (`BYDAutoSpeedDevice.getCurrentSpeed`, `BYDAutoStatisticDevice.getElecPercentageValue/getElecDrivingRangeValue`). It renders nav + speed-limit (`nSdiSpeedLimit`→`SdiRenderer`) in its **own app UI**. Its "HUD view" pref (`PREFKEY_TMAP_SETTING_G_USE_HUD_VIEW`) is TMAP's own screen-mirror HUD, not the BYD hardware HUD.
- Gmaps/VietMap are user-installed and **do not call any BYD guidance API** (VietMap dex grep = empty).

### Root cause (why both fail, evidence-based)
The cluster/HUD **content widgets render only their designated real sources** (SLA output ECU for speed-limit; an MCU/coded nav widget for nav). navopen can drive HUD **settings** (color/mode) but cannot inject **content**. No installed nav app pushes BYD HUD nav; the HUD nav widget is coded off/absent in this VN trim. Speed-limit source (`0x2D500020`) is read-only.

### What would be required (not achievable via ADB/app on this trim)
- **#1 turn-by-turn on HUD:** dealer UDS coding to enable the HUD nav widget + a nav app that calls `sendSimpleGuidanceInfo` (none usable in VN). 
- **#3 custom speed-limit:** inject the ADAS SLA CAN frame at the MCU (`TEST_SIMULATE_DOWN/UP` needs the arbitration id+payload, MCU-side, not in head-unit image).

Both are MCU/coding-layer, outside what navopen HAL writes or app emulation can reach. Investigation closed with evidence.

---

## 20. The 2026-07-29 cluster "60" — identified (owner photo)

Owner's 2026-07-29 photo shows the **cluster speed-limit sign = 60 while the HUD stayed correct (30)**. Traced to the recorded test ledger — it was NOT a dedicated speed-limit candidate; it was an **artifact of `overlay.warning-lamps-on` (opcode 2)**.

**Evidence — `docs/refactor-car-execution/verdicts.tsv`, line 2026-07-29T10:47:11.890 (`overlay.warning-lamps-off` FAIL):** opcode 2 (`service call <AutoContainer> 2 i32 1000 i32 2 s16 ""`, = navopen `ac 1000 2 ""`) lit ALL warning lamps incl. the speed-limit sign at test value **60**; opcode 3 (off) sent 2× + opcode 0 (video refresh) did NOT clean up; the **60 stuck** (HUD kept the real 30) until a **real power-cycle**.

**Meaning:**
- The "60" is the diagnostic **test-pattern's fixed value** from the all-warning-lamps test — not a number we chose, lights everything, and needs a reboot to clear. It IS reproducible (`ac 1000 2`).
- **BUT it proves the cluster speed-limit widget can render a value INDEPENDENT of the read-only SLA output** (60 shown vs real 30). So a custom number is likely reachable by finding which data-item opcode 2 writes for the sign (native `DataSourceManager::trafficSignValue`, evidence-index S8, addr 0xd9978 new / 0xd95fc old) and driving it directly.
- The dedicated candidates in `CarExecSpeedSignCatalog.kt` (`sign-inject.*` via statistic `0x4B40001C` / setting ISA-map) remain NOT field-proven for a custom number (today's live run: statistic write rc=0 but display ignored).

**2026-07-29 "catalog exec" test files:**
- Runner: `scripts/vehicle/carexec.sh`; CLI `car-integration/.../carexec/{CarExecCli,CarExecShell,LocalDeviceShell}.kt`
- Candidate catalogs: `core/.../carexec/CarExecCatalog.kt` + `CarExecSpeedSignCatalog.kt` (#3), `CarExecClusterDiagnosticsCatalog.kt` (opcode 2/3 warning-lamps, 12/13 ADAS), `CarExecHudCatalog.kt`, `CarExecNavigationCatalog.kt`, `CarExecClusterProjectionCatalog.kt`, `CarExecClusterLifecycleCatalog.kt`, `CarExecScenarios.kt`, `CarExecModels.kt`, `VerdictLedger.kt`
- Results: `docs/refactor-car-execution/verdicts.tsv` (the 60 incident = last line)
- Plan/checklist: `docs/diagnostics/next-car-session-plan-2026-07-29.md` (§8 on-car results), `docs/diagnostics/artifacts/carexec-checklist.html`
- Earlier hud-sign RE (39 files): `docs/diagnostics/hud-sign-re/**` (evidence-index.json, m3-cluster-sign-plan.json, candidate-report.html, expansion/*)

**Next lead for a CUSTOM number:** RE `libBydCluster` `DataSourceManager::trafficSignValueChanged` (0xd45d8 new) to find its input data-item/subscriber, and what opcode 2 writes into it — that path renders on the cluster (proven), unlike the statistic/setting HAL writes.

---

## 21. #3 custom speed-limit — native RE + ranked on-car test cases (for tomorrow)

### Native RE of libBydCluster (objdump)
- Cluster speed-limit number = data-item **`DATA_ITEM_ID_TRAFFIC_SIGN_VALUE`** (siblings: `_TYPE`, `DATA_ITEM_ID_LIMIT_TRAFFIC_SYMBOL_VALUE`, `DATA_ITEM_ID_SPEED_WARNING_VALUE`, `DATA_ITEM_ID_OVERSPEED_WARNING_LIGHT(_COLOR)`, `DATA_ITEM_ID_SLA_EQUIP`).
- All data-items are set by `DataSourceManager::handleDataItemChanged(int id, QVariant)` @0x78adc. Its external entry points are `callbackDataItemUpdateString(int id, const char*)` @0x7e2a0 and int/real siblings (0x7e1a8/0x7e24c/0x7e3c0 call handleDataItemChanged) — i.e. the data SOURCE pushes (id,value) in. On our car the source for the sign is the **read-only SLA output** (0x2D500020) → why plain HAL writes are ignored.
- **Test-mode bypass:** cluster has `dashbordTestMode`/`presentationTestMode`/`adasTestMode` + `testInt`/`testReal` data-items (`testIntChanged`/`testRealChanged`). `overlay.warning-lamps-on` (opcode 2) uses a test path that FORCES data-items to test values (the "60" seen 2026-07-29) — bypassing the read-only source. This is the proof-of-render + the most promising injection surface.
- RCC channel `handleIviRccReqMsg` @0x87198 handles only a few cmdIds (query/translate + `sendPluginMsgString(45, strParam1)`); no generic "set data-item" there. BUT `sendInfo(1000,cmd,STRING)` carries a String param we have **never** used (ClusterDebug always sent `""`).

### Ranked on-car test cases (parked; reboot cleans; screencap self-verifies)
Prefix `NAV="CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"`.

1. **Reproduce + bound the artifact** — `ac 1000 2 ""` → confirm sign=60 (+ all warning lamps); screencap. Then `ac 1000 3 ""`; keep power-cycle ready (opcode 3 did NOT fully clean on 2026-07-29). Establishes the render path + that a non-SLA value shows.
2. **1000-channel WITH a value string (new axis)** — sweep `ac 1000 <cmd> "88"` (and `"60"`) for cmds, watching the sign for 88. Priority cmds: test/warning/ADAS family and neighbors of 2 — e.g. 2,4,5,10,11,12,45,47 + a scan 0..107. If any renders 88 → **custom number achieved**.
3. **Test-mode value** — look for a cmd that enters dashboard/adas test mode then sets `testInt`/`testReal`; try `ac 1000 <testcmd> "88"`. The 60 came from a test path, so a parameterized test value is the likely lever.
4. **Statistic/HAL family retry (held)** — `setraw statistic 4B40001C 88` (failed once) + siblings `4B400034`(type), `4B400021/23`(time-cond), `LIMIT_TRAFFIC_SYMBOL`, held in a tight loop, and with `ADAS_SLA_STATE` combos. Also `DATA_ITEM_ID_SPEED_WARNING_VALUE` via any settable id. Lower odds (source path is read-only) but cheap.
5. **RCC plugin-string** (advanced) — craft `sendInfo2`/PadToClusterReq to hit `sendPluginMsgString(45, "...")` / `callbackDataItemUpdateString(TRAFFIC_SIGN_VALUE_id, "88")`; needs the numeric data-item id (TBD) + the plugin msg format.

### Cleanup / discipline
Parked only; screencap after each; opcode-3/reboot ready; restore any switch toggled. The winning case is whichever renders **88** (our chosen number) on the cluster sign — that promotes #3 from "artifact-only (fixed 60)" to "custom number".

---

## 22. THE real mechanism — cluster data comes over a ZMQ bus (answers "how" for a custom value)

RE of `libBydDataSource.so` (the lib that feeds the cluster) reveals the actual data path:

`vehicle-data provider` → **ZMQ (CZMQ `zmsg_*`)** → `ZmqRecvThread::ThreadFunction` → `ZmqDataWrapper::DispatchZmqMsgs` → `ProcessPayloadData(topic, subtopic, payload, len)` → business handler → **`DataItemWrapper::SetDataItem_INT(id, value)`** (also `_STRING/_REAL/_BOOL`) → (JNI) `DataSourceManager::callbackDataItemUpdateInt(id, value)` → `handleDataItemChanged(id, QVariant)` → **cluster widget (the sign)**.

- Publisher fn: `BydZmqPublisher::BydZmqPublisherSend(char* topic, void* data, int len)`; update fn `BusinessBase::zmqDataUpdate(uint businessId, uint dataId, void* data, const char*)`.
- Item table + ids: `g_ConfigDataArray` / `g_ConfigDataArraySize` in libBydDataSource, + runtime config **`/collect2/byd_datasource_config.xml`** (on device).
- CAN decode/encode: `CanDataWrapper::{Get,Set}Data_INT8/16/32/FLOAT/8BYTES` — the provider maps CAN→data-item.

### Why every earlier attempt behaved as it did
- Plain HAL writes (statistic/setting) → ignored by the display because the sign widget only consumes what arrives on this **ZMQ bus** (sourced from the read-only SLA CAN signal).
- opcode 2 (warning test) → cluster-internal test path forces values (the fixed 60), bypassing the bus.
- **A ZMQ publish onto this bus = set ANY data-item to ANY value = master key** (custom speed-limit, and more).

### Case 3 "how" (the real method) — ZMQ injection. On-car recon for tomorrow:
1. Find the endpoint: `cat /collect2/byd_datasource_config.xml` ; `ps -A | grep -i cluster` → pid ; `ls -l /proc/<pid>/fd | grep -i socket` ; `netstat -anp 2>/dev/null | grep <pid>` / `ss -xp` — locate the ZMQ SUB endpoint (tcp port or ipc/inproc path).
2. Get the sign's data-item id + businessId from `g_ConfigDataArray` (off-car: objdump the .data table in libBydDataSource) or the config XML.
3. Publish a crafted message (topic/businessId + payload encoding id+value) to that endpoint → sign shows our number. Needs a small ZMQ publisher (CZMQ) or replicating the wire frames.
   - **Off-car TODO to make this turnkey:** RE `ProcessPayloadData` + `zmqDataUpdate` to get the exact payload byte layout; RE `g_ConfigDataArray` for the TRAFFIC_SIGN_VALUE numeric id + businessId.

### Case 5 "how" (plugin-string) — concrete but QML-dependent:
`PluginMsgManager::RecvStringMsg(uint id, const char*)` receives plugin strings; reachable from the RCC path (`handleIviRccReqMsg` → `sendPluginMsgString(45, strParam1)`). Send by crafting a `PadToClusterReq` FlatBuffer (vtable: cmdId@4, subId@6, intParam1@8, strParam1@18) and `navopen ac2 8 <flatbuffer-hex>` (sendInfo2 channel 8). Whether the QML plugin renders msg-45 as the sign is unknown (QML in compressed `cluster_theme*.rcc`). Lower confidence than the ZMQ bus.

### Bottom line
The clean, general "how" = **the ZMQ data bus** (owning it sets any cluster value, incl. a custom speed-limit). Next off-car step to make it turnkey: RE the ZMQ payload format + `g_ConfigDataArray` id table. Cheapest first on-car try remains the `ac 1000 <cmd> "88"` sweep (§21 case 2).

---

## 23. AUTONOMOUS DEEP-RE RESULT (2026-08-10 night) — full pipeline + turnkey plan for #3

### The complete data pipeline (traced end-to-end in libBydDataSource + libBydCluster)
`vehicle CAN bus` → **data provider** reads signals `CanDataWrapper::GetData_UINT8/16/32(key)` (keys like `0x26F00000`, `0x26100000`, `0x25D00000`) → maps to **data-item id** (small int, e.g. 397/398/542/543) via `DataItemContainer::GetDataItemWrapper(id)` → `DataItemWrapper::SetDataItem_INT/STRING(value)` → (JNI) `DataSourceManager::callbackDataItemUpdateInt/String(id,value)` → `handleDataItemChanged(id,QVariant)` → **cluster Qt widget (the sign)**. Provider↔cluster transport = **ZMQ PUB/SUB over TCP `192.168.195.2:8889` + `192.168.195.3:6666`** (dual-OS internal net); msg = `(businessId, dataId, payload)` via `ZmqDataWrapper::ProcessPayloadData`.

### Why every HAL write failed, and what the "60" was
- The sign reads ONLY what arrives on this pipeline (real CAN → provider → ZMQ). Our `statistic/setting/adas` HAL writes never enter it → ignored. (SLA output `0x2D500020` is the read-only source.)
- `BusinessUi::clusterDebug(uint cmd)` opcode **2** = floods FAKE CAN frames through `warningLightCanDataUpdate/canDataUpdate(canId,data)` → CanDataWrapper decodes → all warnings + sign show test pattern = **60**. Opcode-only (no value arg) ⇒ **the `ac 1000 <cmd> "88"` string is NOT a value channel (case 2 is dead for a custom number).**

### THE injection = a CAN frame (canId + bytes) for the speed-limit signal
Deliver the SLA speed-limit CAN frame with our value, two ways:
- **(A) TEST device SIMULATE (have the primitive):** `navopen setbytes test AA00020F <frame>` (down) / `AA000210` (up) — injects a CAN frame the provider decodes exactly like opcode-2's fake frames, but with OUR bytes → sign renders our number. **Most viable.**
- **(B) ZMQ publish** `(businessId, dataId, payload)` to the cluster SUB — topology-dependent (SUB connects to a fixed PUB; needs us to be/replace the PUB or reach the bus). Backup.

### The only missing values (both on the car, safe to READ)
`/collect2/byd_datasource_config.xml` (parsed by `BydConfigInfo::DecodeNodeToDataItem`) holds the **CAN-id + bit layout** for the traffic-sign/speed-limit signal AND the **data-item id**. One `cat` hands us the exact frame to inject.

### TURNKEY on-car plan for tomorrow (parked; reboot/​power-cycle ready)
Push tool first: `adb -s <veh> push apks/navopen-v3.jar /data/local/tmp/navopen.jar`.
1. **Recon (safe reads):**
   - `cat /collect2/byd_datasource_config.xml` → find the speed-limit/traffic-sign entry → its **CAN id + start-bit/len + factor** and **data-item id**. (If /collect2 is empty, `find / -name 'byd_datasource_config.xml' 2>/dev/null`.)
   - `ip addr` ; `netstat -anp 2>/dev/null | grep -E '8889|6666|192.168.195'` — is the Android side on the ZMQ net / can we reach it (path B feasibility).
   - Reproduce: `ac 1000 2 ""` → confirm sign=60; then `ac 1000 3 ""` (+power-cycle ready).
2. **Inject (path A):** from the config, build the CAN frame encoding **88** at the right bits → `navopen setbytes test AA00020F <hh,hh,...>` (try `AA000210` too). Watch cluster sign for **88**. Hold/repeat if the real signal overwrites.
3. If A renders 88 → **#3 solved (custom number).** If not, and ZMQ reachable → build a tiny ZMQ PUB to send `(businessId,dataId,payload)`.

### Honest odds
Mechanism now fully understood — the earlier "blocked" was **wrong channel** (HAL) not impossible. #3 via **CAN-frame inject (path A)** has a **real, good chance**: it's exactly what opcode-2 does (proven to move the sign), just with our bytes + correct id. Dependencies: the config XML gives the CAN id+bits (very likely present on /collect2), and the SIMULATE frame reaches the provider's decode (opcode-2 proves that decode path renders). Remaining risk: the real CAN signal refreshing over ours (mitigate: hold/repeat, or mute the real source). This is the strongest, best-grounded path found in the whole investigation.

### RE artifacts (for reference)
Decompiled: `<cache>/clusternav-re/sysimg/{jadx-ClusterDebug,jadx-DiCarServer,jadx-BydDevelopmentTools,tmap_c1,tmap_c2}`; libs `libBydDataSource.so`, `libbydauto.so`. Native fns: `clusterDebug@0x14610c`, opcode-2@0x147148, `ProcessPayloadData@0x1723f8`, `ZmqRecvThread::Init@0x173ea8`, `handleIviRccReqMsg@0x87198`, `handleDataItemChanged@0x78adc`, `callbackDataItemUpdate{Int@0x7e154,String@0x7e2a0}`.

---

## 24. On-car 2026-08-11 + off-car RE verdict — the hard wall (and the one unlock)

### Confirmed on-car (<vehicle-ip>)
- **No root** (`su` absent, uid=2000 shell). `/collect2/byd_datasource_config.xml` = **Permission denied** (can't read the CAN/id table).
- **ZMQ data bus is on a DIFFERENT domain**: Android has only `lo` + `rmnet` (4G); **no `192.168.195.x` interface**, ping .2/.3 = 100% loss. The cluster + libBydDataSource + ZMQ run on the other fission OS (fission_single_os=0), NOT reachable from adb.
- Live sniff dead: `getbytes` on TEST CANIN ids = null; SLA out `0x2D500020` read = sentinel; cluster data-items not in main logcat.

### Confirmed off-car (libBydDataSource registry, extracted by content)
- Data-item registry = array of `{tag=0x403, char* name, wrapper*}` @ base 0x21618, stride 24; **index = data-item id**.
- **`trafficSignValue` = id 564 (0x234)** (neighbors: 562 bsdLight, 563 bsdLightColor, 565 trafficSign, 566 adasTextTip, 567 adasWindow).

### Channel analysis — NO ADB-reachable way to set data-item 564 to a custom value
| Channel (Android→cluster) | Verdict |
|---|---|
| `sendInfo(1000,cmd)` → `clusterDebug(uint)` | opcode-only, hardcoded; opcode 2 = fake-CAN flood → fixed **60** (not parameterizable) |
| `sendInfo2(8,…)` → `handleIviRccReqMsg` | only translate/online-resource + `sendPluginMsgString`; **no setDataItem/handleDataItemChanged** |
| HAL writes (statistic/setting/adas) | accepted (rc=0) but display ignores (reads the pipeline, not HAL) |
| ZMQ publish `(businessId,dataId,payload)` | bus on unreachable 192.168.195.x domain; **no route, no root** |
| TEST device CAN SIMULATE `0xAA00020F` | reaches MCU→provider→cluster, but needs the **physical SLA CAN arbitration id + bits** (provider/MCU-side; only in the root-locked config, NOT in the cluster .so) |

### Verdict
**A custom speed-limit number on the cluster is NOT reachable from ADB on this unit.** The value flows only through CAN → data-provider (separate domain) → ZMQ → cluster; every Android-side channel is either value-less (clusterDebug), display-ignored (HAL), unreachable (ZMQ domain), or needs the physical CAN id that lives behind root/MCU. The "60" was a reproducible fake-CAN test artifact, not a parameterizable value. This is a genuine exhaustion of the ADB surface.

### The ONE unlock = ROOT
With root we could: read `/collect2/byd_datasource_config.xml` → the SLA CAN id+bits → craft the TEST-device SIMULATE frame with our value; OR run a process on the correct domain / MITM the ZMQ bus; OR write the data-item pipeline directly. Root is the master key for #3 (and #1 coding). Realistic root research for DiLink3 (Android 10 / Qualcomm QKQ1.210910, boot image inside `new_update.zip:payload.bin`): bootloader-unlock + Magisk-patched boot, or a local-privilege-escalation, or a factory/engineering mode (BydDevelopmentTools RepairMode/Verification, master password `BYD6125F`). All out of the current adb-shell scope until root is obtained.

### What's confirmed reproducible without root (partial #3)
`ac 1000 2` → sign shows **60** (+ all warning lamps), clears with `ac 1000 3` (+ power-cycle if it sticks). Fixed value only.


---

## 25. OFF-CAR RE (2026-08-11) — fresh-eyes verdicts on the 5 open questions + two untried NON-ROOT doors

Session was **off-car** (no adb). Worked the STATE file's 5 open questions against the extracted `system.img` artifacts in `<cache>/clusternav-re/sysimg/` (jadx of DiCarServer / ClusterDebug / CanDataCollect; `libbydauto.so`, `libBydDataSource.so`; both `cluster_theme*.rcc`). Reproducible RCC extractor added at `scripts/re/rcc_extract.py`.

### Q4 (msg-45 / QML sign text) — CLOSED, negative (source-proven)
- The `.rcc` is **not** a real blocker. Qt RCC **v3**; theme1 = 819 zlib + 2037 raw blobs, theme2 = 1056 zlib + 2436 raw, **0 zstd**. `rcc_extract.py` yields 100/105 text files (~1.7 MB QML/JS corpus each). Prior sessions' "needs an rcc decompressor to check" is now resolved.
- The cluster speed-limit sign renders as a **one-way binding**: `Text { text: DataSource.trafficSignValue }` (theme1 corpus L43645) plus `text: DataSource.limitTrafficSymbolValue` and `switch(DataSource.trafficSignType)` selecting `tsr_fault/off/noSpeed/border_yellow` + `release_speed_limit.png`. `DataSource.*` is a C++ context property; QML **never assigns** it.
- QML **does** receive inbound messages: `Connections { target: DataSource; function onPluginMsgReceived(id,value){…} }` — 7 handlers. But every handler switches only on `SEND_MSG_ID_THEME_SWITCH_REQUEST`, `SEND_MSG_ID_DASHBOARD_SELFCHECK`, and `SEND_MSG_ID_KEY_UP..KEY_LONG_PRESS`/`KEY_DRIVEINFO_SHORT` (theme + self-check animations, physical-key routing). **None** references trafficSign/limit/SLA/speed. Outbound ids are `RECV_MSG_ID_*` (drive-info, menu display/page, AC volume/temp, nav-type, theme page, speed-reminder, poweron-anim) — control only.
- ⇒ `sendPluginMsgString(45,…)` has **no QML text sink** for the sign. The msg-45 hypothesis is **FALSE**. The only lever on the number is the `DataSource.trafficSignValue` property, driven by the native CAN→provider→ZMQ→`handleDataItemChanged(564)` pipeline → i.e., back to the CAN route (Q2/Q5).

### Q1 (non-root read of the CAN/id config) — NEW untried door (promising)
- DiCarServer registers a DiStore/SPI Binder service **`com.byd.car.collect2.ICollect2FileStoreService`** (impl `com.byd.car.store.Collect2FileStoreServiceImpl`, `@ServiceImpl(service=ICollect2FileStoreService.class, singleton=true)`) exposing `readTextFile(String)→String`, `readFile(String)→byte[]`, `isFileExist`, `createFile`, `appendFile`, … all delegating to DiStore `IFileStoreService`. The read executes **inside DiCarServer (privileged uid, per community: UID 1000)** → it can read `/collect2/*` that the uid=2000 shell cannot (the exact §24 Permission-denied wall).
- Resolve the binder via DiCarServer's **`com.byd.spi.ipc.provider.BinderProvider`** (a ContentProvider): its `query()` does `Spi.getService(getContext(), Class.forName(selectionArgs[0]))` and returns the `IBinder` wrapped in a `BinderCursor`. So a client resolves the service **by class name** → `ICollect2FileStoreService.Stub.asInterface(binder).readTextFile(path)`.
- Read targets: **`/collect2/byd_datasource_config.xml`** (CAN-id ↔ data-item + bit layout, incl. `trafficSignValue`=id 564) AND **`/collect2/dataCollect/datacollectioncfg`** (the JSON of CAN ids CanDataCollect streams — may directly name the SLA/traffic-sign id; path from `JsonUrlUtil.filePath`).
- Unknowns (on-car only): the provider **authority** (`dumpsys package providers | grep -iE 'byd|spi|collect2|binder'`); whether the SPI service/provider enforces a caller **permission/uid** (community found the *DiCar* ContentProvider blocks uid 2000 by a package check — so this MAY be gated; the SPI BinderProvider is a **distinct** surface worth trying); whether `IFileStoreService` scopes paths to a sandbox (the Collect2 impl passes `path` straight through). navopen's `BydPermissionContext`/spoofed-system context may satisfy it.

### Q2 (HAL CAN read/monitor) — YES, a non-root path exists
- `com.byd.CanDataCollect.service.CanDataCollectService`: `BYDAutoBigDataDevice.getInstance(ctx).registerListener(listener, new int[]{-1728053216})` → `onWholeFrameDataChanged(byte[])` delivers **raw whole CAN frames**. The streamed arbitration ids are programmed into the MCU via `BYDAutoVehicleDataDevice.sendRegisterTable(3, table)`.
- Decoded formats:
  - **register-table entry** (`collect_id_conf`, 8 bytes): `[canid BE:4][subid:1][canChanel:1][mode:1][flag:1]`.
  - **received frame** (`recv_can`): `[canid BE:4][subid:1][canChanel:1][data:0..64]`.
- Source of the canids = `/collect2/dataCollect/datacollectioncfg` → `JsonDataHandel` builds `CanConfigInfo{canid,…}` → `CanDataHandle.pushCanidConfigure`. So Q1 (read that file) and Q2 (register + listen) reinforce each other.
- ⇒ An **on-device CAN monitor without hardware or root**, IF navopen's bypass context can hold the BigData/VehicleData permission (risk: server-side enforcement, like Panorama). The `TEST_CANIN_*` `getbytes` (null in §24) were the **wrong API**; this BigData listener is the right one.

### Q3 (community DBC for the SLA id) — no shortcut (evidence-based)
- `github.com/wheregoes/byd-dolphin-hacking` (the **Dolphin** = DiLink **50**, branch **13.1.32**, `fission_single_os=1`) is the authoritative DiLink-3 RE. It confirms the identical inject path (`com.byd.cluster.spi` → `BYDAutoTestDevice.TEST_SIMULATE_DOWN_SET 0xAA00020F`, `wholeFrame` includes the id) and the full UDS stack, but publishes **no** speed-limit/traffic-sign/SLA **arbitration id** — only UDS **diagnostic** req/resp ids per network: 6 CAN nets incl. **ADAS网**; left `0x720/0x728`, right `0x747/0x74F`, IPB `0x782/0x78A`; and it explicitly states the per-feature coding DIDs are "NOT YET KNOWN." Different branch than the owner's **Seal** (Seal = DiLink **50P**/13.1.33 or **DiLink 100**/Android 12).
- ⇒ Derive the SLA arbitration id **on-car via the Q2 sniff** (wiggle method: change the real road's limit, diff frames), not from a public DBC.
- Correction to a STATE assumption: the community reports the Dolphin **bootloader UNLOCKED** (`ro.boot.flash.locked=0`) with **Magisk viable** (KernelSU **not** — kernel 4.14.117 is non-GKI). This contradicts STATE's "bootloader likely locked; community never had root," but it's a **different trim**, and root remains **owner-declined**; treat as unverified for the Seal.

### Q5 (SIMULATE frame format) — mechanism confirmed; internal layout partly open
- `com.byd.clusterdebug.BroadcastReceiverCAN`: extras `wholeFrame` **or** `normal` = comma-separated hex → `byte[]` → `BYDAutoEventValue.bufferDataValue` → `BYDAutoTestDevice.getInstance(ctx).set(new int[]{-1442840049}, ev)`; `-1442840049 == 0xAA00020F`. navopen equivalent = `setbytes test AA00020F <hh,hh,…>`.
- Community-confirmed working inject: `--es wholeFrame '28,C0,00,00,00,00,00,00,00'` (9 bytes; the id is encoded **inside** the bytes). The **downlink/SIMULATE** internal layout is **not 100% pinned** (the **uplink** `recv_can` uses a 4-byte BE canid + subid + chan; the 9-byte downlink example may use a 2-byte id). Resolve by capturing a real frame (Q2) and echoing its exact bytes back through SIMULATE with the value edited.

### Net reframing (honest)
§24's "custom speed-limit NOT reachable from ADB; **ROOT is the only unlock**" is **too strong**. Two untried, on-car-testable, **non-root** doors target the exact wall:
- **Door A (read the config):** `ICollect2FileStoreService.readTextFile("/collect2/byd_datasource_config.xml")` (and `…/dataCollect/datacollectioncfg`) via `BinderProvider` — get the SLA canid + bit layout directly.
- **Door B (sniff → inject):** `BYDAutoBigDataDevice` monitor (Q2) to learn the SLA canid+bits, then `BYDAutoTestDevice 0xAA00020F` inject (Q5).
Either one yielding the SLA arbitration id + bit layout makes **#3** achievable **without root**. Both may still hit a permission/uid gate (unverified on-car). **#1** (nav→HUD) is unchanged: hardware/coding-gated on this trim (Q4 doesn't affect it).

### Ranked next on-car (parked; power-cycle ready; `fission_screencap -d 1` self-verify)
1. **Door A** — `dumpsys package providers | grep -iE 'byd|spi|collect2|binder'` to get the authority; then via navopen resolve `ICollect2FileStoreService` (BinderProvider query by class name) and `readTextFile` both `/collect2/byd_datasource_config.xml` and `/collect2/dataCollect/datacollectioncfg`. If either returns text → SLA canid+bits in hand (best; no CAN guessing).
2. **Door B** — via navopen: `BYDAutoBigDataDevice.registerListener(l,{-1728053216})` + `BYDAutoVehicleDataDevice.sendRegisterTable(3,<table for candidate ADAS/SLA ids or a broad sweep>)`; log `onWholeFrameDataChanged`; change the real road's speed limit and diff frames to isolate the SLA id + bits.
3. **Inject** — craft the frame encoding **88** → `navopen setbytes test AA00020F <bytes>` (and `AA000210` up); hold/repeat vs refresh; screencap.
4. If **both** gated → the ADB surface is genuinely exhausted (§24 stands); remaining = root (owner-declined) or a hardware CAN interface.

**navopen extension needed for next trip:** add a `readcfg` verb (BinderProvider query → `ICollect2FileStoreService.readTextFile`) and `canmon`/`canreg` verbs (`BYDAutoBigDataDevice` listener + `BYDAutoVehicleDataDevice.sendRegisterTable`). Build per §7.


---

## 26. TURNKEY TOOLING BUILT (2026-08-11 off-car) — navopen-v4 + hud3-speedlimit-v4.sh for #3

Built + verified off-car so the next on-car #3 run is deterministic (final visual proof stays on-car).

### `apks/navopen-v4.jar` (v3 kept)
New verbs — sources: `NavOpen/src/com/byd/navopen/{NavOpen,NavCanListener}.java`, `NavOpen/src/com/byd/spi/ipc/cursor/BinderCursor.java`; compile-only stub `NavOpen/stubs/android/hardware/bydauto/bigdata/AbsBYDAutoBigDataListener.java`.
- **`readcfg [path] [authority]` — Door A.** Resolves `com.byd.car.collect2.ICollect2FileStoreService` through the exported `CarServiceProvider` (extends `BinderProvider`) at `content://com.byd.car.server.provider.CarServiceProvider`, then raw `Binder.transact` isFileExist(6)/readFile(7)/readTextFile(8), descriptor `com.byd.car.collect2.ICollect2FileStoreService`. The read executes inside privileged DiCarServer → bypasses the uid=2000 Permission-denied on `/collect2`. Self-contained (only `android.os.*` + a matching `BinderCursor$BinderParcelable` so the cursor's strong binder unmarshals via CREATOR/readStrongBinder). Default path `/collect2/byd_datasource_config.xml`.
- **`canmon [seconds] [canidHexCsv] [filterHex]` — Door B.** Registers a real `AbsBYDAutoBigDataListener` (`NavCanListener`) on `BYDAutoBigDataDevice(-1728053216)`, programs ids via `BYDAutoVehicleDataDevice.sendRegisterTable(3, table)` (entry `[canid BE4][sub][chan][mode][flag=1]`), logs each whole frame `[canid BE4][sub][chan][data]` + unique ids. Watchdog auto-extends to `secs+10`.
- **`canreg <canidHexCsv>`** — program the MCU register table only.
- **inject** (unchanged): `setbytes test AA00020F <hh,hh,..>` (wholeFrame; CAN id encoded in the bytes) — confirmed identical to ClusterDebug's `com.byd.cluster.spi` path.

Build (needs `JAVA_HOME=/opt/homebrew/opt/openjdk@17` so the `d8` wrapper finds java): `javac` stubs→S; `javac` src (cp `android.jar:S`)→B; `jar` B→classes.jar; `d8 --min-api 26 --lib android.jar --lib stubs.jar --output navopen-v4.jar classes.jar`. `dexdump` confirms defined classes = NavOpen/NavCanListener/BinderCursor(+BinderParcelable); `AbsBYDAutoBigDataListener` is **reference-only** (stub excluded from the dex; the device's real class is used at runtime).

### `scripts/vehicle/hud3-speedlimit-v4.sh` (phased, timeout-guarded, screencap self-verify)
- **Phase 1 (recon, safe reads):** push jar; `dumpsys package providers` (authority fallback); `readcfg` both `/collect2/byd_datasource_config.xml` + `/collect2/dataCollect/datacollectioncfg` → `./doorA_*.txt`; optional `canmon` sniff (set `CANIDS`) → `./doorB_canmon.txt`.
- **Phase 2 (inject, set `FRAME`):** `setbytes test AA00020F <FRAME>` in a hold loop (+`AA000210`), screencap cluster before/after.
- Run: `VEH=<ip:port> ./hud3-speedlimit-v4.sh` (recon) → inspect `./doorA_datasource_config.txt` → build FRAME → `VEH=<ip:port> FRAME=<id..,val,..> ./hud3-speedlimit-v4.sh` (inject). Parked; power-cycle cleans.

### The only unknowns left (all on-car)
1. Is `CarServiceProvider.query` reachable and does it return the Collect2 binder for our uid, or is there a permission/uid gate? If `query -> null`/`no binder`, override `AUTH` from the providers dump.
2. Does `readcfg` return the config text → **SLA canid + bit layout directly** (best outcome)?
3. If Door A is gated: does `canmon` register + stream (is BigData server-enforced like Panorama)? → sniff the SLA frame live (wiggle the real limit).
4. Encode the number into the frame → inject → does the cluster sign render it? = the #3 win.
