# DEEP RE — OEM AMAP cluster-nav LAYOUT mechanism (4-mode)

> Session `re_4mode_layout` · 2026-08-14 · off-car RE only (no code modified).
> Question: what decides the cluster nav LAYOUT — **Đơn giản/centre · Toàn/full · Nhỏ/small · OFF** — and can WE set it?
> Evidence tags: `[src:file:line]` (jadx/repo), `[asm:lib+vaddr]` (objdump aarch64), `[str:lib@off]` (rodata string), `[map:file:line]` (DiCarServer feature mapper).
> RE root: `~/Library/Caches/clusternav-re` (abbrev. `<re>`).

---

## TL;DR (the answer)

**The layout is decided CLUSTER-SIDE, not by anything AmapService varies.** It is computed inside the
cluster's own data-source process `libBydDataSource.so`, class `BusinessUi1`, in
`updateNaviType()` → `updateNaviDisplay()`. The layout is an enum written to a cluster data-item:

| Layout (UI name) | Value written by cluster | How | Evidence |
|---|---|---|---|
| **OFF** = `NAVI_TYPE_INVALID` | `SetDataItem_INT(0)` | data-item `[this+0x1e8]` = 0 | `[asm:libBydDataSource+0x129d74]`, `[str:...@0x368d5 "NAVI_TYPE_INVALID, not anim"]` |
| **Đơn giản / centre "Giữa+ETA"** = `NAVI_TYPE_EASY` | `SetDataItem_INT(2)` | data-item = 2 | `[asm:...+0x129d2c]`, `[str:...@0x31bea "NAVI_TYPE_EASY, not anim"]` |
| **Nhỏ / small strip** = `NAVI_TYPE_SMALL_SCREEN` | `SetDataItem_INT(3)` | data-item = 3 | `[asm:...+0x129ce0]`, `[str:...@0x364de "NAVI_TYPE_SMALL_SCREEN, not anim"]` |
| **Toàn màn hình / full** = `NAVI_TYPE_FULL_SCREEN` | `PluginMsgManager::SendIntMsg(15,1)` | QML plugin msg 15 (= `RECV_MSG_ID_NAVI_FULL_SWITCH_ANIM`) | `[asm:...+0x129b9c]`, `[str:...@0x31749 "NAVI_TYPE_FULL_SCREEN"]` |

`updateNaviType()` picks the enum from **cluster CAN/data-source signals** read via
`CanDataWrapper::GetData_UINT8(id)` — the *same* cross-domain pipeline that feeds the speed-limit sign:

1. `0x4C10E015` = **SET_NAVI_SCREEN_STATUS** (the "naviScreenStatuValue" in the debug log; the value our
   dropdown + AmapService write). `[asm:...+0x129918]`
2. `0x12D0002A` = **BODYWORK_POWER_LEVEL** — gate, must `== 3` (car powered/driving). `[asm:...+0x1299d4]` `[src:.../body/Body.java:1368]`
3. `0x26F00000` = **BODYWORK_ONLINE_HAS** — gate, checked `== 1`. `[asm:...+0x129a0c]` `[src:.../body/Body.java:941]`
4. internal power-on/animation state (`this+0x1130..0x1133`) + naviState (`this+0x1fc/0x204`) + DataItem 567 (adasTextTip). `[asm:...+0x12997c..0x129a60]`

Layout is **also persisted** as `m_u8NaviTypeStore` in the cluster config XML
`/collect2/byd_datasource_config.xml`, loaded at cluster boot via `getConfig`.
`[str:...@ "getConfig ... m_u8NaviTypeStore = %d"]`, `[str:...@ "/collect2/byd_datasource_config.xml"]`

**The `NaviInfo` flatbuffer we send has NO layout field** — all 18 fields are turn-by-turn data. So the
layout can never be set through the nav broadcast. `[src:<re>/diagnostic-amap/auto/sources/byd/fbs/naviInfo/NaviInfo.java]`

**Net:** the only Android-reachable input to the selector is `0x4C10E015`, and it only takes effect when
`updateNaviType()` is *triggered while the cluster is live* with the body-power gate satisfied — which is
exactly what AmapService's **service-restart** path does and its **cold-boot** path does not. Everything
else (the two gates, the persistent store) lives in the cluster domain (`cell2-rw` / `/collect2/...`) =
the same no-root wall as the speed-limit sign.

---

## 1. What decides the cluster nav LAYOUT?

Not the `NaviInfo` flatbuffer, not `naviState`, not the AutoContainer channel by itself. The decision is a
cluster-side state machine.

### 1.1 The flatbuffer has no layout field (ruled out)
`byd.fbs.naviInfo.NaviInfo.createNaviInfo(...)` takes 18 args, all turn-by-turn:
`naviState, nextRouteName, curToSegmentDist, forwardState, nextTurnIcon, routeRemainTime, routeRemainDist,
stringEtaArrivalTime, exitNameInfo, exitDirectionInfo, routrRemainDisAuto, routrRemainTimeAuto,
SegRemainDisAuto, nextNextTurnIcon, nextToSegmentDist, nextNextRouteName, roungAboutNum, nextRoungAboutNum`.
No display-type / screen / layout / mapType field exists.
`[src:<re>/diagnostic-amap/auto/sources/byd/fbs/naviInfo/NaviInfo.java:263]`

### 1.2 The cluster-side selector (definitive)
`libBydDataSource.so` symbols (the cluster's data-source process, other OS domain):
`BusinessUi1::updateNaviType()` `[asm:libBydDataSource@0x12988c]`,
`BusinessUi1::updateNaviDisplay()` `[asm:@0x129b2c]`,
`BusinessUi1::sendNaviMessage()/sendNaviMessage2()` `[asm:@…]`,
and the enum print
`"Navi naviScreenStatuValue=%d, m_u8PreNaviTypeState=%d, m_u8NaviType=%d"` `[str:libBydDataSource]`.

**`updateNaviType()` reads three data-items** (all via `CanDataWrapper::GetData_UINT8`):
```
w21 = GetData_UINT8(0x12D0002A)   ; BODYWORK_POWER_LEVEL     [asm:+0x129904..+0x129910]
w20 = GetData_UINT8(0x4C10E015)   ; SET_NAVI_SCREEN_STATUS   [asm:+0x129918..+0x129930]  ← "naviScreenStatuValue"
w22 = GetData_UINT8(0x26F00000)   ; BODYWORK_ONLINE_HAS      [asm:+0x129934..+0x129948]
```
then gates on power-on/anim state (`this+0x1130..0x1133`, e.g. `(1<<state)&0x3001 → state∈{0,12,13}`)
`[asm:+0x12997c..+0x1299cc]`, requires `BODYWORK_POWER_LEVEL==3` `[asm:+0x1299d4]`, branches on
`naviScreenStatuValue==2` `[asm:+0x1299e4]` and `BODYWORK_ONLINE_HAS==1` `[asm:+0x129a0c]`, computes the
candidate `m_u8NaviType` (`this+0x1dc`), and calls `updateNaviDisplay()` when it changed `[asm:+0x129a88]`.

**`updateNaviDisplay()` maps the candidate to the final layout** and writes it to the output data-item
`[this+0x1e8]`:
- candidate `2` → `SetDataItem_INT(2)` → **EASY / centre** `[asm:+0x129d2c]`
- candidate `3` → `SetDataItem_INT(3)` → **SMALL / strip** `[asm:+0x129ce0]`
- candidate `4` → `SendIntMsg(15,1)` → **FULL_SCREEN** (plugin animation) `[asm:+0x129b9c]`
- else → `SetDataItem_INT(0)` → **INVALID / OFF** `[asm:+0x129d74]`

So the layout selector = **`m_u8NaviType` in the cluster's `BusinessUi1`**, driven by a body-power gate,
an online gate, the OEM `SET_NAVI_SCREEN_STATUS` value, and the persisted `m_u8NaviTypeStore`.

---

## 2. Is there a field/property/feature-id WE can set to change it?

**Short answer: one — `0x4C10E015` (SET_NAVI_SCREEN_STATUS_SET) — and only as part of a live re-assert
sequence, not by itself. The persistent store and the two gate signals are cluster-domain / read-only CAN
= not reachable without root.**

### Candidate-lever table

| Candidate lever | Reachable from Android? | Effect on layout | Verdict | Evidence |
|---|---|---|---|---|
| **`0x4C10E015` SET_NAVI_SCREEN_STATUS** via `BYDAutoSettingDevice.set` / `navopen setraw setting 4C10E015 <v>` | **Yes** (provisioned setting, HAL write rc=0) | Read by `updateNaviType` as `naviScreenStatuValue`; but recompute only fires when `updateNaviType()` is *triggered* AND `BODYWORK_POWER_LEVEL==3` + anim gate | **CANDIDATE** — must be delivered inside a trigger sequence (see §5) | `[map:.../setting/SettingMapper.java:374]`, `[asm:libBydDataSource+0x129918]` |
| **AutoContainer ch4 flatbuffer** `navopen ac2 4 <NaviInfo hex>` (mimics `sendInfo2(4,…)`) | **Yes** | Feeds nav DATA; may be the event that *triggers* `updateNaviType`/`updateNaviDisplay` on a live cluster | **CANDIDATE (trigger)** — pair with the `0x4C10E015` write | `[src:AmapService.java:740]` |
| **AutoContainer ch5** `navopen ac 5 0 ""` (mimics cold-boot `sendInfo(5,0,"")`) | **Yes** | Cold-boot path; may reset cluster nav to boot/stored (small) | **CANDIDATE (reset)** | `[src:AmapService.java:199]` |
| **FULL_SCREEN via PluginMsg 15** (`RECV_MSG_ID_NAVI_FULL_SWITCH_ANIM`) | **Unlikely** — plugin-msg bus is cluster-internal; `onPluginMsgReceived` handlers cover theme/self-check/physical-key only (findings 2026-08-11 §25 Q4), none nav | **BLOCKED** (no Android sink found) | `[asm:libBydDataSource+0x129b9c]`, hud-cluster-injection-findings §25 |
| **`m_u8NaviTypeStore`** in `/collect2/byd_datasource_config.xml` (`BydConfigInfo::SetConfig_UINT8`/`saveConfigXML`) | **No** — file in cluster domain; uid=2000 shell = Permission denied; Door A (`ICollect2FileStoreService.readTextFile`) is **read-only** | Would set boot-default layout persistently | **BLOCKED without root** (same wall as speed-limit) | `[str:libBydDataSource@"/collect2/byd_datasource_config.xml"]`, hud-cluster-injection-findings §Confirmed |
| **`0x12D0002A` BODYWORK_POWER_LEVEL** (gate ==3) | **No** — read-only body CAN status, not a `*_SET` feature | It's a precondition (car powered), not a selector | **RULE OUT** | `[src:.../body/Body.java:1368]` |
| **`0x26F00000` BODYWORK_ONLINE_HAS** (gate ==1) | **No** — read-only body CAN status | Precondition (online), not a selector | **RULE OUT** | `[src:.../body/Body.java:941]` |
| `0x4C10E01D` SET_MAP_SENDING_STATUS | Yes (mapped) but on-car write = `-10011` | Not read by `updateNaviType` | RULE OUT (already dead) | `[map:SettingMapper.java:375]` |
| `0x4C10E03A` SET_DYNAMIC_NAVI_FUNCTION_STATUS | Yes (mapped) but on-car write = `-10011` | Not read by `updateNaviType` | RULE OUT (already dead) | `[map:SettingMapper.java:304]` |
| `0x4C10E040` EASY_NAVI_SIGNAL_MAP_TYPE | Yes (mapped) but on-car dead | Not read by `updateNaviType` | RULE OUT (already dead) | `[map:SettingMapper.java:2941]` |
| `0x1F701010 / 0x1F704010` EASY_NAVI guide | Mapped but write REJECTED `-2147482648` (not provisioned) | — | RULE OUT (already dead) | `[map:InstrumentMapper.java:678-679]` |
| **NEW (untested) `0x4C10E020` SET_METER_DEPTH_MODE** | Yes — **provisioned** | Unknown (meter "depth" mode; not seen in `updateNaviType`) | **LOW-PRI PROBE** | `[map:SettingMapper.java:295,664]` |
| **NEW (untested) `0x4C10A018` INSTRUMENT_NAVI_TYPE_SET** (status `0x40C03032`) | Yes — **provisioned** | Name matches "NaviType"; not seen read by `updateNaviType`, may drive `m_u8NaviTypeState` | **PROBE** | `[map:InstrumentMapper.java:466,472]` |
| **NEW (untested) `0x4C130041` INSTRUMENT_NAVIGATION_STYLE_SET** | Yes — **provisioned** | Name matches "navigation style" | **PROBE** | `[map:InstrumentMapper.java:813]` |
| `0x40C0C0xx` projection family (CENTER/LEFT/RIGHT_PROJECTION) | **No** — absent from InstrumentMapper on this build | — | RULE OUT (not provisioned) | grep InstrumentMapper (empty) |

**System property for `mClusterType`:** it is `ro.build.system.fission_single_os` `[src:AmapService.java:138]`
`[src:.../clusterdebug/MainActivity.java:36]`. But it is **not** the centre/small selector — it only gates
*whether the AutoContainer 1for2 flatbuffer path runs at all* (`==1` → CAN-only; `≠1` → CAN + AutoContainer)
`[src:AmapService.java:194,440]`. On this car it is `0`, constant across reboot vs restart, so it cannot
explain the layout difference. It is a `ro.` prop (boot-only, root to change) anyway.

---

## 3. What is `mClusterType`, and what sets it? (full trace)

- **AmapService side:** `this.mClusterType = SystemProperties.get("ro.build.system.fission_single_os")`
  in `onCreate()` `[src:AmapService.java:138]`. Compared only with `.equals("1")`:
  - `onStartCommand`: `if (!mClusterType.equals("1")) { … sendInfo(5,0,"") }` `[src:AmapService.java:194-199]`
  - `sendNaviToCluster`: `if (!mClusterType.equals("1")) { sendNaviInfoTo1for2Clster(); sendNavigateInfoToCAN(); } else { sendNavigateInfoToCAN(); }` `[src:AmapService.java:440-447]`
  - i.e. `mClusterType` selects **transport**, not layout: `≠"1"` (dual-OS, this car = `0`) → push the
    NaviInfo flatbuffer over AutoContainer `sendInfo2(4,…)` **and** write the independent-CAN instrument
    fields; `="1"` (single-OS) → CAN instrument fields only.
- **Cluster side (`m_u8NaviType`, the real layout var) is unrelated to `mClusterType`.** It is the enum in
  `libBydDataSource` §1.2, sourced from `BODYWORK_POWER_LEVEL` + `SET_NAVI_SCREEN_STATUS` +
  `BODYWORK_ONLINE_HAS` + persisted `m_u8NaviTypeStore`. Do not confuse the two "ClusterType" names.
- Same prop is read identically by ClusterDebug `[src:.../clusterdebug/MainActivity.java:36-37]` and by
  DiPilotUtil/VersionUtils (`getInt(...,1)==1`) — confirming semantics: `1` = single-fission-OS, `0` =
  dual-OS. `[grep: fission_single_os across <re>]`

---

## 4. Best evidence-based explanation: reboot → small vs app-restart → centre

The trigger is AmapService's **`isServiceRestart()`** branch, combined with the cluster loading its stored
type at cold boot.

**Full power-cycle reboot (→ SMALL strip):**
1. Cluster process cold-starts → power-on animation → `getConfig` loads `m_u8NaviTypeStore` from
   `/collect2/byd_datasource_config.xml` and applies it. Observed boot value = SMALL_SCREEN (=3).
   `[str:libBydDataSource@"getConfig … m_u8NaviTypeStore = %d"]`
2. AmapService also cold-starts → `isServiceRestart(...)` returns **false**
   `[src:AmapService.java:185-186]` → it **skips** `setNaviScreenStatus(0x4C10E015,3)` and only does
   `sendInfo(5,0,"")` on AutoContainer channel 5 `[src:AmapService.java:194-199]`.
3. Nothing writes `0x4C10E015` at startup, and the normal guidance path never writes it either
   (only restart/kill/shutdown do). So the cluster keeps its stored SMALL layout.

**App/service restart, e.g. OTA reinstall (→ centre "Giữa+ETA" = EASY):**
1. The cluster process keeps running (a system process; not restarted by an app OTA). Its `m_u8NaviType`
   state machine is live.
2. Only AmapService restarts → `isServiceRestart(...)` returns **true**
   `[src:AmapService.java:188]` → it immediately does
   `setNaviScreenStatus(0x4C10E015, 3)` + `setNaviStatus(0x43E0003A, 4)` +
   `GuideInfo.naviState = 9` + `sendNaviInfoTo1for2Clster()` (flatbuffer on channel 4)
   `[src:AmapService.java:189-192, 710-718, 740]`.
3. That live re-assert of `SET_NAVI_SCREEN_STATUS` + nav-status + a fresh NaviInfo push, arriving at an
   already-running cluster with `BODYWORK_POWER_LEVEL==3`, triggers `updateNaviType()`→`updateNaviDisplay()`,
   which recomputes to EASY (centre, value 2). `[asm:libBydDataSource updateNaviType/updateNaviDisplay]`

So the difference is **the startup trigger, not a persisted layout switch**: cold boot never writes
`0x4C10E015` (stays stored=small); warm restart writes it + re-asserts nav into a live cluster (→ centre).

> Soft spot (needs on-car readback): AmapService writes `0x4C10E015 = 3`, but the disassembled EASY branch
> tests `naviScreenStatuValue == 2`. Either (a) `3` = "nav active" and the actual type comes from
> `m_u8NaviTypeState` (`this+0x1da`, set by the store / a switch message), or (b) the value→type map differs
> from the naïve reading. **Confirm by `getraw setting 4C10E015` in each of the four on-screen states.**

---

## 5. Ranked NEW on-car probes (non-destructive first)

Tooling: navopen-v3/v4 verbs `getraw/setraw <dev> <hexid> [val]`, `ac <id> <sub> <str>` (=`sendInfo`),
`ac2 <ch> <hex>` (=`sendInfo2`), devices `instr/setting/adas/statistic/test/ota`. Parked; power-cycle to
clean. Self-verify with `fission_screencap -d 1`, or just ask the owner "what does the cluster show?".

**P0 — Read ground-truth values in each layout state (pure read, zero risk).**
Put the cluster into each of the 4 states (via the app dropdown / an OTA restart / a reboot), and for each
read the selector inputs:
```
navopen getraw setting 4C10E015     # SET_NAVI_SCREEN_STATUS  (expect distinct per layout? or const 3)
navopen getraw instr   40C03032     # INSTRUMENT_NAVI_TYPE    (does it track EASY/SMALL/FULL/OFF?)
navopen getraw setting 99000349     # SET_METER_DEPTH_MODE status
```
Goal: pin the `0x4C10E015` value→layout map and see whether `INSTRUMENT_NAVI_TYPE (0x40C03032)` mirrors
`m_u8NaviType`. This resolves the §4 soft spot and tells us which value to write in P1.

**P1 — Replay the proven warm-restart trigger LIVE with a value sweep (the main test).**
The lever isn't "write `0x4C10E015`" alone; it's "write it *and* re-assert nav so `updateNaviType` fires".
For `v` in `{2,3,4,0,1}` (2=EASY, 3=SMALL, 4=FULL, 0=OFF per §1.2), while the car is powered (so
`BODYWORK_POWER_LEVEL==3`) and a nav session is active:
```
# 1) set the OEM nav-screen status to the target value
navopen setraw setting 4C10E015 <v>
# 2) re-assert nav status like AmapService restart (INSTRUMENT_SEND_NAVI_STATUS_SET 0x43E0003A)
navopen setraw instr 43E0003A 2            # 2 = navigating (4=exit, then 2 to force a transition)
# 3) trigger the cluster to recompute by pushing a NaviInfo on AutoContainer ch4 (mimic sendInfo2(4,…))
navopen ac2 4 <NaviInfo-flatbuffer-hex>    # reuse a captured live frame, or a naviState=9 frame
```
Ask owner after each `v`: centre / small / full / off? First `v` that flips layout = the live lever.
(To avoid AmapService clobbering `0x4C10E015` back to 3, run with Nav+HUD **off** in our app, or stop
`com.example.amapservice` for the duration, then drive the sequence manually.)

**P2 — Sweep the two NEW provisioned nav feature-ids (untested doors).**
```
navopen setraw instr 4C10A018 <0..4>       # INSTRUMENT_NAVI_TYPE_SET  (name = "NaviType"; strongest new candidate)
navopen setraw instr 4C130041 <0..4>       # INSTRUMENT_NAVIGATION_STYLE_SET
navopen setraw setting 4C10E020 <0..3>     # SET_METER_DEPTH_MODE_SET
```
Ask owner if any value changes centre/small/full. These are provisioned `*_SET` ids not exercised before
and semantically match nav layout.

**P3 — Cold-boot-path reset probe (channel 5 vs channel 4).**
```
navopen ac 5 0 ""     # mimic AmapService cold-boot sendInfo(5,0,"") — does it force SMALL live?
navopen ac2 4 <hex>   # then push flatbuffer — does it force CENTRE live?
```
Confirms whether the AutoContainer channel (5 vs 4) is itself the latch, independent of `0x4C10E015`.

**P4 — Persistent store (read-only door; likely blocked, do last).**
Via Door A (`ICollect2FileStoreService.readTextFile` through DiCarServer `BinderProvider`, findings §25),
read `/collect2/byd_datasource_config.xml` and locate the `m_u8NaviTypeStore` node to learn the config-id
and the stored value. **Writing it needs `SetConfig_UINT8`+`saveConfigXML` inside the cluster domain =
not reachable without root** — this probe is for confirmation only, not a fix path.

### If P0–P3 all fail
Then the layout is **controlled by `m_u8NaviType` in the cluster's `libBydDataSource`, gated by
`BODYWORK_POWER_LEVEL==3` + the power-on/anim state, and only re-evaluated on cluster-internal events
(`RECV_MSG_ID_NAVI_FULL_SWITCH_ANIM` / boot / theme-switch)** — none of which Android can raise directly,
and whose persistent default lives in `/collect2/byd_datasource_config.xml` in the cluster OS domain.
That is the same cross-domain / no-root wall documented for the speed-limit sign. In that case the honest
verdict is: **live 4-way layout switching is not reachable from Android on this trim without root; the only
observed control is the coarse reboot(small)/app-restart(centre) side effect of AmapService's
`isServiceRestart` path.**

---

## Appendix — key evidence index

- `AmapService.java`: `mClusterType`=`fission_single_os` L138; `isServiceRestart` branch L185-192;
  cold-boot `sendInfo(5,0,"")` L194-199; kill/shutdown re-pin L274/288-289; `setNaviScreenStatus` L710-716;
  `setNaviStatus` L717-721; `sendNaviInfoTo1for2Clster` → `sendInfo2(4,…)` L740; `isServiceRestart` impl L825.
  (`<re>/diagnostic-amap/auto/sources/com/example/amapservice/AmapService.java`)
- `NaviInfo.java`: `createNaviInfo(...)` 18 turn-by-turn args, no layout field, L263.
- `libBydDataSource.so` (aarch64): `BusinessUi1::updateNaviType` @0x12988c; `updateNaviDisplay` @0x129b2c;
  reads `GetData_UINT8(0x12D0002A/0x4C10E015/0x26F00000)` @0x129904-0x129948; `SetDataItem_INT(2|3|0)`
  @0x129d2c/0x129ce0/0x129d74; `SendIntMsg(15,1)` FULL @0x129b9c.
  Strings: `NAVI_TYPE_EASY` @0x31bea/0x33dbd, `NAVI_TYPE_SMALL_SCREEN` @0x364de/0x30240,
  `NAVI_TYPE_FULL_SCREEN` @0x31749, `NAVI_TYPE_INVALID` @0x368d5/0x3223b;
  `"Navi naviScreenStatuValue=%d, m_u8PreNaviTypeState=%d, m_u8NaviType=%d"`;
  `"getConfig … m_u8NaviTypeStore = %d"`; `/collect2/byd_datasource_config.xml`.
- DiCarServer feature ids: `SET_NAVI_SCREEN_STATUS_SET 0x4C10E015` (SettingMapper:374);
  `SET_METER_DEPTH_MODE_SET 0x4C10E020` (295/664); `SET_MAP_SENDING_STATUS_SET 0x4C10E01D` (375);
  `SET_DYNAMIC_NAVI_FUNCTION_STATUS_SET 0x4C10E03A` (304); `EASY_NAVI_SIGNAL_MAP_TYPE 0x4C10E040` (2941);
  `INSTRUMENT_NAVI_TYPE_SET 0x4C10A018` / `INSTRUMENT_NAVI_TYPE 0x40C03032` (InstrumentMapper:466/472);
  `INSTRUMENT_SEND_NAVI_STATUS_SET 0x43E0003A` (586); `INSTRUMENT_NAVIGATION_STYLE_SET 0x4C130041` (813);
  `BODYWORK_POWER_LEVEL 0x12D0002A` (body/Body.java:1368); `BODYWORK_ONLINE_HAS 0x26F00000` (body/Body.java:941).
- Repo: `AmapFrameBuilder.kt` (broadcast extras we send); `BydHal.kt` `writeNavFrame` writes
  `INSTRUMENT_SEND_NAVI_STATUS_SET=2`, `SET_NAVI_SCREEN_STATUS_SET=screenMode`, guide fields;
  `NavigationHudOwner.kt` (OFF path = clear/status=4; `reapply()` re-asserts 4→2).
- Prior wall (same domain): `docs/_handoff/cluster-hud-injection-STATE.md`,
  `docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` §24-26 (Door A/B, `/collect2` config, no root).
