# ON-CAR INJECT SWEEP — icons · 4-mode (cửa MỚI) · speed-limit sign · 2026-08-15

> Xe: BYD Seal DiLink 3.0 · Android 10 · **PARKED-ONLY** (số P + phanh tay) · dọn = power-cycle nút nguồn.
> **HỎI LẠI IP:** `export VEH=<vehicle-ip>:5555`. ĐỪNG đoán.
> Nguồn: RE sweep đa tác nhân 2026-08-15 (inflate `cluster_theme*.rcc` QML + disassemble `libBydDataSource.so`
> + đối chiếu DiCarServer/AmapService/Adas). Mỗi feature-id/value có file:line; đã qua verifier đối kháng.
> **Mục tiêu owner:** bắn icon lên xem loại gì rồi map vào code — KHÔNG cần lái. Cộng: tìm cửa 4-mode mới,
> và speed-limit sign có đường ghi không.
> ⚠️ Phân biệt **CƠ CHẾ** (chứng minh từ source) vs **QUY KẾT** (chỉ ảnh trên xe mới chốt). Ghi rõ từng chỗ.

---

## 0. TL;DR — ba câu trả lời

| Câu | Trả lời | Rủi ro |
|---|---|---|
| **Icon: bắn lên xem được không?** | **ĐƯỢC, parked.** Nhưng mũi tên owner NHÌN THẤY đi qua **broadcast AMAP `NEW_ICON` 0..28**, KHÔNG phải HAL id. Map value→glyph đã **PROVEN** (inflate QML trong theme). Bắn `am broadcast` từng value, chụp, xong. | Thấp — RAM/overlay, power-cycle sạch |
| **4-mode: còn cửa mới không?** | **CÓ — cửa THẬT tìm ra:** `INSTRUMENT_NAVI_TYPE_SET = 0x4C10A018`. Đây là số hạng **quyết định** layout (`m_u8NaviTypeState`); `0x4C10E015` mà app đang ghi **chỉ là cổng phụ** → giải thích vì sao mọi lần thử cũ đều fail. Chưa từng thử on-car. | ⚠️ **CAO — PERSIST qua reboot** (config-53). Sweep phải KẾT bằng ghi lại 2=EASY |
| **Speed sign: có đường ghi?** | **KHÔNG (qua HAL).** Verdict = **needs-ADAS-bus.** Biển tốc độ là QML one-way bind data-item 564, nuôi bởi ZMQ/CAN trên **fission OS khác** (192.168.195.x, Android không với tới). Mọi HAL id speed-limit đã thử → rc=0 mà không đổi. Đường duy nhất còn: TEST device CAN-SIMULATE, cần frame id+bits trước. | ⚠️⚠️ **CAO NHẤT** — TEST device nguy hiểm, đụng safety feature sống |

---

## PRE — chung cho mọi sweep
```bash
export VEH=<vehicle-ip>:5555; ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" connect "$VEH"
# navopen: push tool 1 lần
"$ADB" -s "$VEH" push apks/navopen-v4.jar /data/local/tmp/navopen.jar
NAV(){ "$ADB" -s "$VEH" shell "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen $*"; }
SHOT(){ "$ADB" -s "$VEH" shell "fission_screencap -d ${2:-1} -p /data/local/tmp/p.png"; "$ADB" -s "$VEH" pull /data/local/tmp/p.png "./$1"; }
```
> ⚠️ **navopen device-routing bẫy** (NavOpen.java:348-356): tag bắt đầu bằng `s` → **SettingDevice** (ghi bền!),
> tag lạ → mặc định instrument. Gõ nhầm `setraw setting` thay vì `setraw instr` có thể ghi trúng feature bền
> (vd `SET_HUD_SWITCH_SET 0x4C10E023`) sống qua reboot. **Luôn `getraw` xác nhận id trước khi `setraw`.**

---

## S1 — ICON SWEEP (map value→glyph, không lái) ⭐

### Phát hiện gốc (PROVEN — đọc để hiểu vì sao có 2 kênh)
- Mũi tên **owner nhìn thấy trên cụm** = kênh **AMAP NEW_ICON 0..28**: `AUTONAVI_STANDARD_BROADCAST_SEND` →
  AmapService → flatbuffer `sendInfo2(4,…)` → QML `updateIcon()`. Map lấy từ inflate QML trong
  `cluster_theme1.rcc` (`case 16 → tunnel.png`, `case 20 → direct.png`, …). Đây là bảng `toAmapIcon()` của mình.
- `0x43F01010` (INSTRUMENT_GUIDE_INFO_SIMPLE_SET, CAN 1..49) = **sink RIÊNG** (HUD/CAN), glyph ROM **KHÔNG**
  nằm trong theme cụm. Đây là bảng `toHudIcon()`. **Câu mở lớn: sink này có vẽ lên CỤM hay chỉ HUD kính lái
  (có thể xe Seal này không có)?** Chỉ ảnh CH-A vs CH-B mới chốt — và nó quyết `toHudIcon()` có nhìn thấy được
  trên trim này không.
- Theme **CÓ** `tunnel.png`(16) `service_area`(13) `toll_station`(14) `approach_point`(10) `destination`(15)
  u-turn, 12 biến thể vòng xuyến → bắn được. Theme **KHÔNG** có glyph merge/ramp/fork/keep/slight →
  các maneuver đó **buộc fallback** (merge→straight 9, ramp→slight 4/5). **Đây là giới hạn firmware, KHÔNG phải
  bug của mình** — khớp encoder trong code.
- Glyph vòng-xuyến-có-số `round_right_N.png` **LIKELY KHÔNG có** trong theme (QML dựng động, không thấy asset).

### ⭐ HAI BỘ ICON KHÁC NHAU — phải map CẢ HAI (owner nhấn mạnh)
Cụm và HUD **đánh số khác hệ**, code đã có cả hai bảng (`Maneuver.kt`):
- **Bộ CỤM** = `toAmapIcon()` → AMAP `NEW_ICON` **0..28** (AmapService tự remap sang CAN qua `TurnIdMapToCAN`).
- **Bộ HUD** = `toHudIcon()` → CAN `0x43F01010` **1..49** (ghi thẳng).
- Liên hệ: bất biến code `toHudIcon(m) == TurnIdMapToCAN[toAmapIcon(m)]` (Maneuver.kt:23).

**Kiểm bằng máy (deterministic, `TurnIdMapToCAN` RE:AmapService.java:67): khớp 22/24 ô. Chỉ 2 ô LỆCH — đúng
2 chỗ code đã gắn TODO chờ đo:**

| Maneuver | CỤM NEW_ICON (glyph) | HUD code gửi | HUD theo bảng RE | Ghi chú |
|---|---|---|---|---|
| **ROUNDABOUT** | 11 (enter_roundabout) | **15** (vòng xuyến chung) | **13** (enter-RA CCW) | Maneuver.kt:92-94 TODO — chụp CH-A 13 vs 15 |
| **CONTINUE** | 20 (continue/顺行) | **11** (=straight) | **12** (顺行) | Maneuver.kt:95-96 TODO — chụp CH-A 11 vs 12 |

22 Maneuver còn lại: hai bảng **nhất quán** (vd hầm cụm=16 ↔ HUD=49; U-turn cụm=8 ↔ HUD=9; waypoint 10↔45).
⚠️ Chú ý số **khác nhau giữa hai bộ cho CÙNG một hướng** — đây chính là bẫy đảo trái↔phải của bug 1.14.

**Hai sweep dưới map ĐỘC LẬP hai bộ:** CH-B map bộ CỤM (NEW_ICON 0..28) · CH-A map bộ HUD (CAN 0..49).
**So CH-A vs CH-B trên cùng một hướng** trả lời câu lớn nhất: `0x43F01010` (bộ HUD) có vẽ lên **CỤM** không,
hay chỉ HUD kính lái (Seal này có thể không có)? Nếu CH-A không vẽ gì lên cụm → `toHudIcon()` vô hình trên
trim này, chỉ bộ CỤM có ý nghĩa thị giác; nếu CH-A CÓ vẽ → phải map đúng cả hai. **Ưu tiên chụp 2 ô lệch trên
ở CH-A.**

### CH-B — kênh CỤM THẬT: sweep NEW_ICON 0..28 (map đã PROVEN, cần ảnh xác nhận)
> Cảnh: **Cast OFF, ClusterNav Nav+HUD OFF, không có route GMaps sống** (để broadcast của mình là nguồn duy nhất).
> AmapService phải đang chạy.
```bash
for v in $(seq 0 28); do
  "$ADB" -s "$VEH" shell "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10001 --ei TYPE 1 --ez IS_BYD_MAP false --ei NEW_ICON $v --ei SEG_REMAIN_DIS 300 --es NEXT_ROAD_NAME 'IconTest $v' --ei ROUTE_REMAIN_DIS 6000 --ei ROUTE_REMAIN_TIME 300"
  sleep 2; SHOT chB-newicon-$v.png 1
done
```
Ưu tiên nhìn: **16 (hầm — câu chính owner)**, 10 (waypoint), 13 (service), 14 (toll), 8/19 (U-turn L/R),
20 (continue), 4 vs 6 (slight vs sharp trái).
**Restore:** `am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10019 --ei EXTRA_STATE 9` (stop), hoặc power-cycle.

### CH-B exit-number — vòng xuyến có SỐ lối ra (LIKELY blank = giới hạn firmware)
```bash
for ic in 11 17; do for n in 1 2 3; do
  "$ADB" -s "$VEH" shell "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10001 --ei TYPE 1 --ez IS_BYD_MAP false --ei NEW_ICON $ic --ei ROUNG_ABOUT_NUM $n --ei SEG_REMAIN_DIS 300 --es NEXT_ROAD_NAME 'RA ex$n' --ei ROUTE_REMAIN_DIS 6000 --ei ROUTE_REMAIN_TIME 300"
  sleep 2; SHOT chB-round-ic${ic}-n${n}.png 1
done; done
```
**Đọc kết quả:** nếu vẽ vòng xuyến + số → glyph có thật (tính năng dùng được). Nếu **blank** trong khi cùng
NEW_ICON *không* kèm ROUNG_ABOUT_NUM (ở CH-B primary) vẫn vẽ vòng xuyến → asset số **thiếu** = giới hạn firmware.

### CH-A — sink HUD/CAN 0x43F01010 sweep 0..49 (+ chốt gap 4/6)
> Đây là bảng `toHudIcon()`. Diagnostic chính: **CH-A có đổi mũi tên CỤM không?** Nếu KHÔNG (mà CH-B có) →
> `0x43F01010` chỉ nuôi HUD riêng, `toHudIcon()` vô hình trên Seal này.
```bash
NAV open                       # status=2 + screen=3, bật surface nav
for v in $(seq 0 49); do
  for i in $(seq 1 10); do NAV setraw instr 43F01010 $v >/dev/null; sleep 0.4; done   # giữ ~4s chống đè
  SHOT chA-can-$v.png 1
done
NAV close                      # restore (status=4 + clear); rồi power-cycle cho chắc
```
**Gap 4 & 6** (2 id duy nhất trong 1..49 không được NEW_ICON nào map — **P-ICON-A trong runbook, CHUẨN BỊ mà
chưa chạy**): nếu 4 hoặc 6 vẽ **glyph merge/nhập-làn** → là target riêng, cập nhật `Maneuver.toHudIcon`; nếu
trùng 3/5/7/8 hoặc rác → giữ fallback.

---

## S2 — 4-MODE: CỬA MỚI (đột phá RE) ⭐⭐

### Vì sao mọi lần cũ fail — và cửa thật ở đâu (PROVEN, disasm libBydDataSource.so)
Cụm subscribe `INSTRUMENT_NAVI_TYPE_SET (0x4C10A018)` trong `BusinessUi1::registerCanData`, dispatch ở
`onlineCanDataUpdate` → `updateNaviTypeVoice` (đọc value 1..4, lưu **RAW vào `m_u8NaviTypeState` this+0x1da**,
persist **config-53**, republish `0x40C03032`, gọi `updateNaviType`). `updateNaviType` tính layout **từ
`m_u8NaviTypeState` là số hạng CHÍNH**, `0x4C10E015` chỉ là cổng phụ:
- **EASY** ⟺ state==2 (KHÔNG cần 0x4C10E015)
- **SMALL** ⟺ state==3 **AND** 0x4C10E015==1
- **FULL** ⟺ state==4 **AND** 0x4C10E015==2
- else **OFF**
(dưới các cổng power 0x12D0002A==3, online 0x26F00000==1, naviState ok).

⇒ Ghi `0x4C10E015` một mình **không bao giờ** đổi layout (nó chỉ là cổng, không set state) — đúng cái đã fail.
Và "restart app → về centre" chỉ là re-trigger `updateNaviType` trên state RAM cũ. **Cửa thật = ghi thẳng
`0x4C10A018`**, chưa từng thử (runbook P2 unrun).

### ⚠️⚠️ CẢNH BÁO PERSIST (CLAUDE.md §5)
`0x4C10A018` ghi qua `SetConfig_UINT8(53)`, và boot `BusinessUi1::Init` nạp `GetConfig_UINT8(53)` vào state.
**Power-cycle KHÔNG khôi phục layout cũ — value ghi cuối trở thành mặc định boot.** Sweep primary kết ở v=1 (OFF)
mà dừng ở đó → cụm boot với nav OFF. **BẮT BUỘC kết sweep bằng ghi lại `4C10A018 2` (EASY) + verify
`getraw instr 40C03032`==2.** Nếu HAL write không được cấp cho uid mình (rc=-2147482648) → cửa bị chặn NHƯNG
cũng không có regression (đối xứng, an toàn).

### Recipe (PARKED · Nav+HUD ON · GMaps đang dẫn để naviState hợp lệ)
```bash
# PRIMARY — bản lề. EASY (v=2) test trước vì không cần cổng phụ:
for v in 2 3 4 1; do
  echo "=== 0x4C10A018=$v ==="; NAV setraw instr 4C10A018 $v; sleep 2
  echo -n 'readback 40C03032='; NAV getraw instr 40C03032; SHOT p-navtype-$v.png 1
done
NAV setraw instr 4C10A018 2   # ⚠️ BẮT BUỘC: kết ở EASY để boot không lệch
NAV getraw instr 40C03032     # phải = 2
```
- **v=2 → Đơn giản/centre 'Giữa+ETA' vô điều kiện.** Nếu `40C03032` đổi theo → write tới cụm OK. Nếu KHÔNG đổi →
  HAL write chưa được cấp → cửa chặn (verdict thật).
- **SMALL/FULL** cần cổng phụ + **dừng app khỏi ghi đè** `0x4C10E015` (app ghi mỗi frame): tắt Nav+HUD master
  hoặc `am force-stop com.example.amapservice`, RỒI:
```bash
# SMALL: gate=1 trước, rồi trigger state=3
NAV setraw setting 4C10E015 1; sleep 1; NAV setraw instr 4C10A018 3; sleep 2; NAV getraw instr 40C03032; SHOT p-small.png 1
# FULL: gate=2 trước, rồi trigger state=4
NAV setraw setting 4C10E015 2; sleep 1; NAV setraw instr 4C10A018 4; sleep 2; NAV getraw instr 40C03032; SHOT p-full.png 1
NAV setraw instr 4C10A018 2   # ⚠️ kết ở EASY
```

### Cửa đã CHẾT — đừng thử lại (đã fail on-car / zero-ref trong cụm)
- `0x4C10E015` một mình · combo `015+01D+03A` · `EASY_NAVI 0x4C10E040` (rc=-2147482648) · `0x1F701010/0x1F704010`
  (not provisioned) — tất cả trong runbook.
- `INSTRUMENT_NAVIGATION_STYLE_SET 0x4C130041` — **DOWNGRADE**: zero reference trong `libBydDataSource.so`,
  không drive layout. (Doc cũ ghi "PROBE" — bỏ.)

### Low-pri (tuỳ chọn, tách biệt — KHÔNG phải 4-mode)
Theme/depth `0x4C10E020 / 0x4E400028 / 0x40C0B02C` = đổi skin/màu cụm, **range/persist UNKNOWN**, `0x40C0B02C`
sink chưa xác minh. Chỉ chạy nếu muốn đổi skin, chấp nhận có thể persist.

---

## S3 — SPEED-LIMIT SIGN: verdict = needs-ADAS-bus

### Xác nhận gap (đọc trước)
- `NavigationSpeedSignOwner.kt:21-22` — cả 2 port là `NoopSpeedSignPort`; `SpeedSignPorts.kt:57-79` — "performs
  no I/O". **App chưa ghi biển tốc độ ra đâu cả.**
- Biển trên cụm = QML one-way `Text{ text: DataSource.trafficSignValue }` = data-item **564**, nuôi bởi
  CAN → data-provider (**fission OS riêng 192.168.195.x**) → ZMQ → `handleDataItemChanged(564)`. Số gốc =
  `ADAS_SLA_OUTPUT_SPEED_LIMIT 0x2D500020` (**READ-ONLY**).

### Vì sao KHÔNG có đường ghi HAL (đã thử hết, đều inert)
Mọi HAL id speed-limit ghi được đều **rc=0 mà cụm không đổi** — widget chỉ đọc pipeline ZMQ/DataItem, bơ HAL:
`STATISTICS_ISA...4B40001C`, `SETTING_SPEED_LIMIT 0x4CA00040`, `INSTRUMENT_...OVERSPEED 0x4C108044`,
`ADAS...OFFSET 0x43F03028`, mass-write 33 id — tất cả §2/§5/§19 handoff 2026-08-10/11.
`ADAS_SLA_STATE 0x31600025` = enum MODE 5 giá trị (off/fusion/vision/…), **KHÔNG phải km/h**.

### Đường DUY NHẤT còn (shell-reachable) — và vì sao nguy hiểm
TEST device **CAN-SIMULATE**: `setbytes test AA00020F <frame>` — cần **frame id + bit layout của SLA** trước,
lấy qua 2 cửa CHƯA THỬ:
- **Cửa A** (an toàn nhất, READ-ONLY): `NAV readcfg /collect2/byd_datasource_config.xml` qua binder đặc quyền
  Collect2FileStore. Nếu trả XML → có ngay CAN arb-id + bit + factor.
- **Cửa B**: `NAV canmon 30` sniff frame SLA sống trong lúc owner đi qua 2 biển tốc độ khác nhau, diff ra frame
  có byte = km/h.

> ⚠️⚠️ **AN TOÀN — đọc kỹ:**
> - TEST device (`0xAA0002xx`) là factory/EOL, hàng xóm id-space nguy hiểm: `TEST_SHUTDOWN_FROM_CAN`,
>   `TEST_START_RUNIN` (burn-in), EEPROM writes. **CHỈ inject đúng id lấy từ Cửa A/B — TUYỆT ĐỐI KHÔNG sweep id.**
> - `ADAS_SLA_STATE=0` **tắt tính năng an toàn sống** (SLA/TSR) → biển vàng gạch chéo. Đọc value gốc bằng `getraw`
>   trước, khôi phục sau. Process chết trước khi restore → SLA off qua reboot.
> - Biển kẹt không tự sạch (`ac 1000 3` không clear) → power-cycle.

**Kết luận wiring:** **CHƯA** wire `NoopSpeedSignPort` sang HAL id nào — sẽ là dead write. Chỉ wire khi
CAN-SIMULATE frame được xác nhận. Nếu cả 2 cửa bị gate on-car → **Android/adb đã cạn** cho việc vẽ số tuỳ ý;
còn lại chỉ root (owner đã loại) hoặc phần cứng CAN. HUD kính lái cùng nguồn read-only, đã hiện đúng biển thật.

---

## S4 — CODE-MAPPING follow-up (đóng vòng sau khi có ảnh)
- **Icon:** ảnh CH-B 0..28 → xác nhận/sửa bảng `core/.../navigation/Maneuver.kt` `toAmapIcon()`. Ảnh CH-A gap 4/6
  → nếu là merge glyph, sửa `toHudIcon()`. **Nếu CH-A không vẽ lên cụm** → `toHudIcon()` vô hình trên trim này,
  ghi nhận (không cần tối ưu HUD sink).
- **4-mode:** nếu `4C10A018=2` đổi được layout live → thêm lever thật vào `Prefs` + `BydHal` (ghi `0x4C10A018`
  theo mode chọn, thay vì chỉ `0x4C10E015`), **kèm marker persist §5** để khôi phục lúc boot. Đây có thể là fix
  gốc cho regression "reboot mất centre".
- **Speed sign:** chỉ khi Cửa A/B ra frame → viết `HalSpeedSignPort` (CAN-SIMULATE) thay `NoopSpeedSignPort`.

## S5 — Còn UNKNOWN cho tới khi có ảnh
- `0x43F01010` (CAN 1..49) vẽ lên CỤM hay chỉ HUD riêng? → CH-A vs CH-B.
- Gap CAN 4/6 là glyph gì.
- Glyph vòng-xuyến-có-số có tồn tại trong theme không.
- HAL write `0x4C10A018` có được cấp cho uid mình không (make-or-break 4-mode).
- Cửa A/B speed-sign có bị gate uid/server không.

## Tham chiếu
- Runbook 4-mode cũ (P0–P3, P-ICON): `docs/diagnostics/oncar-runbook-4mode-track-a-probes-2026-08-14.md`
- Bảng icon: `docs/diagnostics/re-maneuver-icon-tables-2026-08-14.md`
- Speed-sign injection RE: `docs/_handoff/hud-cluster-injection-findings-2026-08-10.md`, `cluster-hud-injection-STATE.md`
