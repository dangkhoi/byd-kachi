# Cluster/HUD injection — SESSION STATE (handoff for a fresh session)

> Read this first. It's the 2-minute "state of play". Deep detail + evidence: `hud-cluster-injection-findings-2026-08-10.md` (§1–25; §25 = 2026-08-11 off-car verdicts on the open questions).
> Owner: Đăng Khôi (dangkhoi). Car: BYD Seal DiLink3.0 (fw-2602), Android 10, dual-OS (`fission_single_os=0`). Test only when parked; power-cycle to clean.

## The two goals
1. **Nav → windshield HUD** (turn-by-turn: arrow + distance + road name; NOT a full map).
2. **Custom speed-limit number → cluster** (show an arbitrary value, e.g. 88, on the cluster speed-limit sign).

(Nav → cluster already works and is out of scope as a "problem".)

## TL;DR verdict (evidence-based, this session)
- **Nav → cluster: WORKS** — `am broadcast AUTONAVI_STANDARD_BROADCAST_SEND` (TYPE=1, IS_BYD_MAP=false, + the 4 `_AUTO` fields) → `amapservice` → cluster renders icon + road + distance. Live-confirmed.
- **Nav → HUD (#1): GATED (hardware).** This car's HUD hardware/scene is older and has no turn-by-turn nav widget. Friends' BYDs with newer HUD get nav on HUD automatically from the same cluster feed. Owner's plan: swap to newer HUD hardware. Not an ADB problem.
- **Custom speed-limit → cluster (#3): not reachable via any channel tried on-car so far (no root).** The value lives on a separate OS/domain, fed by CAN→provider→ZMQ; no Android-side channel tried yet exposes "set the sign = N". Root was considered and **declined (unsafe on a moving vehicle)**.
  - **Update 2026-08-11 (off-car RE, §25):** §24's "root is the ONLY unlock" is **too strong**. Two untried, on-car-testable, **non-root** doors now target this exact wall: **(A)** read the CAN/id config via the privileged Binder file-store `ICollect2FileStoreService.readTextFile("/collect2/byd_datasource_config.xml")` (resolved through DiCarServer's `BinderProvider`); **(B)** sniff the SLA CAN frame on-device via the `BYDAutoBigDataDevice` HAL monitor (`onWholeFrameDataChanged`), then inject via the TEST device `0xAA00020F`. Either yielding the SLA arbitration id + bits makes #3 achievable without root. Both may still hit a permission/uid gate — verify on-car.

## The mental model (most useful for fresh thinking)
```
CAN bus ──> data PROVIDER (separate OS/domain, on 192.168.195.x)
                 │ reads CAN signals (CanDataWrapper), maps to data-item id
                 ▼
            ZMQ PUB/SUB over TCP  192.168.195.2:8889 / 192.168.195.3:6666  (firmware-internal net)
                 │  msg = (businessId, dataId, payload)
                 ▼
            libBydDataSource (in the CLUSTER process, other domain)
                 │ SetDataItem_INT/STRING(id,val) ──JNI──> DataSourceManager::handleDataItemChanged(id,QVariant)
                 ▼
            Cluster Qt widget  ← this draws the speed-limit sign (data-item id 564)
```
Android (where adb + our app live) reaches the cluster ONLY through narrow bridges: **AutoContainer `sendInfo` / HAL `BYDAuto*Device` / TEST-device CAN SIMULATE**. None of these exposes "set sign value".

## Confirmed facts
- **Sign data-item id = 564 (0x234)** — `trafficSignValue` in the libBydDataSource registry (array base 0x21618, stride 24, index=id). Neighbors: 562 bsdLight, 563 bsdLightColor, 565 trafficSign, 566 adasTextTip, 567 adasWindow.
- **ZMQ endpoints** (firmware constants in libBydDataSource): `192.168.195.2:8889`, `192.168.195.3:6666`. **Not present on the Android interface** (only `lo` + `rmnet`/4G) → unreachable from adb.
- **No root**: uid=2000 shell; `su` absent; `/collect2/byd_datasource_config.xml` (the CAN-id ↔ data-item table) = Permission denied.
- **The "60"** seen 2026-07-29 = artifact of `ac 1000 2` (clusterDebug opcode 2 "all warning lamps") flooding FAKE CAN → sign decodes to fixed 60; opcode is value-less; `ac 1000 3` doesn't fully clean (power-cycle needed).
- Nav→HUD gate is coding/hardware: `INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG 0x38B00030` (MCU coding); guidance ids `INSTRUMENT_GUIDE_INFO_SIMPLE_SET 0x43F01010` exist but the HUD scene doesn't render nav on this trim (direct `sendSimpleGuidanceInfo` call rendered nothing).

## Channels analyzed for setting sign=N (all fail without root)
| Channel | Result |
|---|---|
| `sendInfo(1000,cmd)`→`clusterDebug(uint)` | opcode-only, no value; opcode 2 → fixed 60 |
| `sendInfo2(8,…)`→`handleIviRccReqMsg` | translate/online-resource + `sendPluginMsgString` only; no setDataItem |
| HAL `setraw statistic/setting/adas` | accepted (rc=0) but display ignores (reads the pipeline, not HAL) |
| ZMQ publish `(businessId,dataId,payload)` | bus on 192.168.195.x — unreachable domain, no root |
| TEST device CAN SIMULATE `0xAA00020F` | reaches MCU→provider→cluster, but needs the physical SLA CAN arbitration id + bits (root-locked config / MCU-side) |

**Why an app can't do it either:** an app is on the same Android side, same network (still can't reach 192.168.195.x — not a permission issue), fewer file perms (still can't read config), same/stricter bridges (AutoContainer whitelist = only `com.xdja.clusterdemo`). Only a **BYD-platform-signed system app** would help (we don't have the key). Supported channels (nav broadcast) DO work from an app.

## Tools & artifacts (ready)
- **navopen-v3** (`<byd>/apks/navopen-v3.jar`; source `<byd>/NavOpen/src/.../NavOpen.java`): reflection HAL/AutoContainer tool, permission-bypass, halt+9s-watchdog (no hangs). Commands: `getraw/setraw <dev> <hexid> [val]`, `getbytes/setbytes <dev> <hexid> [hex]`, `ac <id> <sub> <str>` (sendInfo), `ac2 <ch> <hex>` (sendInfo2), `callm <dev> <method> [int...]`, `multi/mget`, `acprobe`. Devices: instr/setting/adas/statistic/test/ota + FQN.
- **Scripts** in `scripts/vehicle/`: `hud3-recon.sh` (read-only recon), `hud3-speedlimit.sh`, `hud1-nav-hud.sh`.
- **Decompiled sources** in `<cache>/clusternav-re/sysimg/`: jadx of ClusterDebug, DiCarServer, BydDevelopmentTools, TMap (com.tmap.auto.byd = Korean SKT TMAP, not installed on this car), CanDataCollect; libs `libBydDataSource.so`, `libbydauto.so`, `libBydCluster_NEW.so`. Config JSON `diagnostic_config.json`.
- Extract method: `system.img` is ext4 → `brew install e2fsprogs` → `debugfs -R "rdump …" <img>`.
- Native fns (libBydCluster): `handleDataItemChanged@0x78adc`, `handleIviRccReqMsg@0x87198`, `clusterDebug`(libBydDataSource)`@0x14610c` (opcode-2@0x147148), `callbackDataItemUpdate{Int@0x7e154,String@0x7e2a0}`, `sendPluginMsgString@0x81074`.

## Remaining options (honest)
- **#1 HUD nav:** newer HUD hardware (owner's plan) or dealer UDS coding. Not ADB.
- **#3 custom speed-limit:**
  - **Root** = the theoretical unlock (read config → CAN id → TEST-device inject; or reach ZMQ). **Declined — unsafe on a driving car** (brick risk). Bootloader likely locked; community never had root (navopen = workaround).
  - **Hardware CAN sniffer** on the bus (no root, doesn't touch head unit): capture the real SLA speed-limit frame → learn arbitration id + encoding → craft `setbytes test AA00020F <frame-with-88>`. Uncertain the ADAS/SLA signal is exposed; a separate side-project.
  - Otherwise: accept the ceiling (only the fixed-60 artifact via `ac 1000 2`).

## Fresh-eyes VERDICTS (2026-08-11, off-car RE — details in findings §25)
The 5 open questions are now answered off-car. Reproducible RCC extractor: `scripts/re/rcc_extract.py`.
1. **Non-root file-read of the config? → YES, untried door found.** DiCarServer exposes Binder svc `com.byd.car.collect2.ICollect2FileStoreService` (`readTextFile`/`readFile`), run privileged inside DiCarServer, resolvable via `BinderProvider` (`query()`→`Spi.getService(ctx, Class.forName(arg))`). Could read `/collect2/byd_datasource_config.xml` + `/collect2/dataCollect/datacollectioncfg` that the uid=2000 shell can't. May be permission/uid-gated — **test on-car**.
2. **HAL CAN read/monitor? → YES.** `BYDAutoBigDataDevice.registerListener(l,{-1728053216})`→`onWholeFrameDataChanged(byte[])` = raw whole CAN frames; ids programmed via `BYDAutoVehicleDataDevice.sendRegisterTable(3, table)` (entry 8B `[canid BE:4][subid:1][chan:1][mode:1][flag:1]`; recv `[canid BE:4][subid:1][chan:1][data]`). The `TEST_CANIN_*` getbytes were the wrong API. On-device sniff, no hardware — **test on-car** (permission risk).
3. **Community DBC for the SLA id? → NO.** wheregoes/byd-dolphin-hacking (Dolphin=DiLink50/13.1.32) confirms the inject path + UDS nets (ADAS网; 0x720/728, 0x747/74F, 0x782/78A) but publishes **no** SLA/speed-limit arbitration id; Seal is a different branch. Derive the id on-car via Q2 sniff. (Aside: that car's bootloader is UNLOCKED+Magisk-viable — contradicts "bootloader likely locked", but different trim; root still owner-declined.)
4. **msg-45 → QML sign text? → NO (closed, proven).** The `.rcc` QML is fully extractable (Qt v3, 819/1056 blobs plain zlib). Sign = one-way binding `text: DataSource.trafficSignValue`. The 7 `onPluginMsgReceived(id,value)` handlers switch only on theme-switch/self-check/physical-key ids — none touches the sign. `sendPluginMsgString(45,…)` has no QML text sink.
5. **SIMULATE frame reaches decode? → mechanism confirmed.** `wholeFrame`/`normal` comma-hex → `byte[]` → `BYDAutoTestDevice.set({0xAA00020F}, ev)` (= `navopen setbytes test AA00020F <hex>`). Internal downlink layout not fully pinned (community `wholeFrame '28,C0,00,…'` 9B vs uplink 4B-BE id) → capture a real frame (Q2) and echo it.

**Ranked next on-car (parked; power-cycle ready; `fission_screencap -d 1` self-verify):**
1. **Door A** — `dumpsys package providers | grep -iE 'byd|spi|collect2|binder'`; via navopen resolve `ICollect2FileStoreService` + `readTextFile` the two `/collect2` configs. If text returns → SLA canid+bits in hand.
2. **Door B** — via navopen register `BYDAutoBigDataDevice` listener + `sendRegisterTable`; diff `onWholeFrameDataChanged` while the real limit changes to isolate the SLA frame.
3. **Inject 88** — `navopen setbytes test AA00020F <bytes>` (+`AA000210`); hold/repeat; screencap.
4. If both gated → ADB surface truly exhausted (§24 stands); remaining = root (declined) or hardware CAN.
**TOOLING READY (built + verified off-car 2026-08-11, §26):** `apks/navopen-v4.jar` has the `readcfg` (Door A), `canmon`/`canreg` (Door B) verbs + the existing `setbytes test AA00020F` inject. Turnkey runner: `scripts/vehicle/hud3-speedlimit-v4.sh` — `VEH=<ip:port> ./hud3-speedlimit-v4.sh` (recon: reads both /collect2 configs + optional sniff) → build FRAME from the dumped config → `VEH=<ip:port> FRAME=<id..,val,..> ./hud3-speedlimit-v4.sh` (inject 88 + screencap). Parked; power-cycle cleans.

## Safety / cleanup / git
- Power-cycle the head unit next time on-car to clear this session's toggles (HUD color/mode, opcode 2).
- Working tree is **dirty, nothing committed**. Redaction rule for any commit: machine paths → relative, owner adb IPs → `<vehicle-ip>` (192.168.195.x is a firmware constant, non-owner).
