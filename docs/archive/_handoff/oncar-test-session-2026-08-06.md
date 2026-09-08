# On-car consolidated DEV test session — 2026-08-06

> Gom hết hạng mục pending từ phiên tối qua (`session-2026-08-06-handoff.md`,
> `waze-hlp-nav-speed-fix-2026-08-05.md`, `cast-freeform-resize-split-2026-08-05.md`,
> `speed-limit-sign-oncar-plan.md`) **+** fix mũi tên HUD hôm nay.
>
> **Đây là DEV smoke trên bản debug**, KHÔNG phải Stage 11 sign-off chính thức (Stage 11 cần commit →
> security scan → release candidate + `vehicle-candidate.json` + full matrix + owner sign-off). `adb reboot`
> bị chặn trên DiLink3 → reboot = tắt/mở chìa vật lý. IP redact `<vehicle-ip>` trước khi share.

## 🚩 RELEASE GATE phiên này (owner 2026-08-06)
**Chỉ 3 việc quyết định release** — phải PASS on-car mới release:
1. **Cast app bản đồ lên cụm** (Phase 6: Full → split → resize/profiles).
2. **Autostart** (Phase 3: boot tự cast; tắt thì giữ gauges).
3. **Mũi tên HUD đúng hướng** (Phase 5).

**Mò tiếp — KHÔNG chặn release** (take time): Speed-limit sign (Phase 9), Waze phần speed (Phase 8), Check-for-update (Phase 11), VietMap widget (Phase 10), glyph HUD phụ. Test nếu còn thời gian; fail mấy cái này KHÔNG giữ release lại.

## Artifact
- Cài bản **mới build hôm nay**: `app/build/outputs/apk/debug/app-debug.apk` (versionName **1.03**) = toàn bộ
  feature tối qua **+ fix mũi tên HUD hôm nay**. (Bản cũ `apk/ClusterNav-1.03-debug.apk` KHÔNG có fix HUD.)
- Cùng khóa debug với phiên trước → `install -r` giữ được Cast state (không cần uninstall trừ khi báo signature mismatch).

## Bảng mã icon (mấu chốt đọc HUD/log — trước fix cột HUD LUÔN = 11)
| Maneuver | HUD (`NavigationHudOwner`) | Cụm (`emit lane` NEW_ICON) |
|---|---|---|
| Rẽ trái | **1** | 2 |
| Rẽ phải | **2** | 3 |
| Chếch trái | 3 | 4 |
| Chếch phải | 5 | 5 |
| Ngoặt trái | 7 | 6 |
| Ngoặt phải | 8 | 7 |
| Quay đầu | 9 | 8 |
| Đi thẳng | 11 | 9 |
| Vòng xuyến | 15 | 11 |
| Điểm đến | 48 | 15 |

## Log tag để lấy bằng chứng
```bash
DEVICE=<vehicle-ip>:5555 ; ADB="adb -s $DEVICE"
$ADB logcat -c && $ADB logcat -s NavigationHudOwner:I ClusterBroadcaster:I NavigationSpeedSignOwner:I WazeHudSource:I
```
- HUD: `NavigationHudOwner` → `HUD icon=<can> seg=.. road='..' → rc`
- Cụm: `ClusterBroadcaster` → `emit lane icon=<amap> seg=.. raw='..' road='..' byd=..`
- Speed sign: `NavigationSpeedSignOwner` → `speed-sign limit=<kph> → rc`
- Waze source: `WazeHudSource` → `polling ... every 900ms`; producer tag `WazeHUD`

---

## PHASE 0 — Chuẩn bị (đỗ xe, phanh tay)
- [ ] 0.1 Điện thoại: Google Maps + (WazeMod nếu test Waze, `hud_link_log=true`). CP/AA nối **USB**.
- [ ] 0.2 (off-car, tùy chọn) `scripts/emulator/e2e-smoke.sh` bắt regression cài/khởi động trước.
- [ ] 0.3 Mọi thao tác bấm nút làm khi đỗ; khi chạy nhờ người ghế phụ quan sát HUD.

## PHASE 1 — Kết nối + cài + verify version
- [ ] 1.1 `adb connect <vehicle-ip>:5555` (ADB qua Wi-Fi).
- [ ] 1.2 `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- [ ] 1.3 Nếu `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: `adb uninstall com.byd.clusternav` rồi cài lại (⚠️ mất Cast durable state).
- [ ] 1.4 `adb shell dumpsys package com.byd.clusternav | grep versionName` → **1.03**.

## PHASE 2 — Quyền + freeform flags + autostart + mở app 1 lần
- [ ] 2.1 Notification access + Overlay (canDrawOverlays) đã cấp.
- [ ] 2.2 Freeform flags (app tự set khi mở projection; hoặc thủ công):
  ```bash
  $ADB shell settings put global enable_freeform_support 1
  $ADB shell settings put global force_resizable_activities 1
  ```
- [ ] 2.3 Bật **autostart** (để test boot) + bật toggle **HUD** + toggle **Cluster-lane** + công tắc tổng nav.
- [ ] 2.4 **Mở ClusterNav 1 lần** (để nhận BOOT_COMPLETED + set flags).

## PHASE 3 — POWER-CYCLE VẬT LÝ (tắt/mở chìa) — kích hoạt freeform + test boot autostart
- [ ] 3.1 Tắt chìa → mở lại (KHÔNG `adb reboot`). `adb connect` lại.
- [ ] 3.2 **Boot autostart (autostart ON)**: không mở app → cụm tự cast (split nếu freeform đã sống). Gauges nhường chỗ cast.
- [ ] 3.3 (tùy chọn, power-cycle #2) **Autostart OFF** → boot giữ nguyên đồng hồ gauges (không cast).

## PHASE 4 — Verify freeform alive (điều kiện cho split/resize)
- [ ] 4.1 `am start --display 1 --windowingMode 5 -n '<pkg>/<activity>'` rồi `am task resize <task> 0 0 960 720`
      → **không exception** = freeform sống. (Nếu vẫn exception → cần power-cycle thêm lần nữa.)

## PHASE 5 — Mũi tên HUD (FIX HÔM NAY) + parity cụm
- [ ] 5.1 GMaps đặt route mà khúc đầu **rẽ trái** (đỗ vẫn thấy maneuver kế) → HUD hiện **mũi tên TRÁI**.
- [ ] 5.2 Log: `HUD icon=1` (KHÔNG phải 11) + `emit lane icon=2`. PASS = khớp bảng + không kẹt đi-thẳng.
- [ ] 5.3 Lặp: rẽ phải (HUD 2/cụm 3), chếch (3/4 | 5/5), vòng xuyến (15/11), điểm đến (48/15).
- [ ] 5.4 Đoạn thẳng thật → HUD 11 (đi thẳng) đúng, không bịa cua.

## PHASE 6 — Cast: Full → Split L/R → Resize per-slot → Profiles persist
- [ ] 6.1 **Cast Full** 1 app → chiếm trọn cụm.
- [ ] 6.2 **Split**: cast app vào slot **Trái**, rồi app khác vào **Phải** (sequential, right chỉ sau khi CastingSplit(left)) → mỗi nửa đúng bên (không full-screen). Tỉ lệ 50/50, 30/70, 70/30.
- [ ] 6.3 **Resize per-slot** trong split qua UI → bounds đổi đúng rect (freeform).
- [ ] 6.4 **7 profiles**: chỉnh geometry app A (bounds+DPI) → Stop → **re-cast app A** → khôi phục đúng geometry đã lưu. DPI trong split là display-global ("last edit wins"); bounds per-task.
- [ ] 6.5 KHÔNG còn override rác `934×240`/density 160 (bug prefs cũ đã fix).

## PHASE 7 — Bubble 3 zone
- [ ] 7.1 Bubble = 3 vùng ngang mờ **Trái · Phải · Full** (label FULL="Full", zone-min 48dp).
- [ ] 7.2 Autostart split → 2 nửa cast, bubble hiển thị đúng trạng thái (2 zone active).

## PHASE 8 — Waze HLP nav + speed (source phải là Waze)
> Config: Speed source = **Waze**; Nav source = **PREFER_WAZE**; master ON; WazeMod đang chạy.
- [ ] 8.1 `$ADB logcat -d -s WazeHUD:V -t 5` → thấy `{"v":1,"t":"s",...}` (producer sống).
- [ ] 8.2 `WazeHudSource:I` → `polling ... every 900ms`, không lặp `poll failed`.
- [ ] 8.3 Lái không route → **biển giới hạn tốc độ** theo `lim` (speed KHÔNG bị gate bởi navigating).
- [ ] 8.4 Có route → cụm lane arrow/dist/road theo `trn/dst/st2`; ETA theo `eta/rmin/rkm`.
- [ ] 8.5 **Vòng xuyến Waze** → HUD **15** (vòng xuyến), KHÔNG phải 48 (điểm đến) — kiểm chứng bug magic-int đã diệt.

## PHASE 9 — Speed-limit sign → cụm (ĐIỀU TRA, chưa xong)
> `writeSpeedLimit` hiện dùng ADAS feature SAI (rc lỗi). Mục tiêu: dò feature INSTRUMENT render biển "60".
> Harness trong debug APK (RebindReceiver, exported), mọi broadcast cần `-f 0x01000000`.
- [ ] 9.1 PROBE: `$ADB shell am broadcast -a com.byd.clusternav.TEST_ADAS_PROBE --es dev instrument -f 0x01000000` → `logcat -d | grep "PROBE\["`.
- [ ] 9.2 MASS val=60: `$ADB shell am broadcast -a com.byd.clusternav.TEST_ADAS_MASS --es dev instrument --ei val 60 -f 0x01000000` → nhìn cụm có "60"? warning icon nào sáng? `grep "MASS\["` ghi feature rc=0.
- [ ] 9.3 Narrow: mỗi feature rc=0 → `TEST_ADAS_WRITE --ei id <ID> --ei val 60` rồi val=80 → tìm ĐÚNG feature đổi số biển đỏ.
- [ ] 9.4 Reset: MASS `--ei val 0` (tắt warning icons).
- [ ] 9.5 Ghi lại feature ID + device tìm được → phiên sau bake vào `BydHal.writeSpeedLimit`, rồi `TEST_SPEED_LIMIT --ei limit 60` phải render.

## PHASE 10 — VietMap widget self-grant bind
> Cần loopback `localhost:5555` (uid 2000) sống.
- [ ] 10.1 "Bind hai nguồn VietMap" → khi `BIND_UI_UNAVAILABLE`, app tự chạy `appwidget grantbind --package com.byd.clusternav --user 0` (dadb, off-main) rồi retry bind 1 lần.
- [ ] 10.2 Trạng thái `NOT_BOUND` → `Fresh` (không phải chỉ hiện message thủ công).

## PHASE 11 — Check-for-update (một phần)
> Full install cần đẩy 1 release version CAO HƠN lên GitHub (chưa có) → hôm nay chỉ test tới bước detect/list.
- [ ] 11.1 Cast panel → **Chẩn đoán** → **⬇ Kiểm tra cập nhật** → gọi GitHub Contents API `apk/`, liệt kê được (cần repo public + loopback).
- [ ] 11.2 (Blocked) download → `pm install -r` → restart: chỉ chạy full khi có release version > bản đang cài, cùng khóa ký.

## PHASE 12 — Isolation / Regression
- [ ] 12.1 Tắt HUD → cụm vẫn chạy; bật lại HUD → HUD chạy lại (không cái nào kéo sập cái kia).
- [ ] 12.2 Cụm arrow vẫn khớp maneuver (parity cột phải bảng) — như trước fix (byte-identical).
- [ ] 12.3 **Stop nav** → cả HUD + cụm cùng clear.
- [ ] 12.4 Không có card/dịch vụ Dead Reckon / mock-location.

## PHASE 13 — Evidence + dọn dẹp
- [ ] 13.1 Lưu logcat + ảnh HUD/cụm từng maneuver + kết quả PROBE/MASS speed-limit. Redact IP/PII.
- [ ] 13.2 Trước khi rời: reset override hiển thị nếu có lỡ set:
  ```bash
  $ADB shell "wm size reset -d 1; wm density reset -d 1; wm overscan reset -d 1"
  ```

---

## Bảng ký kết nhanh (điền PASS/FAIL + đường dẫn evidence)
| # | Hạng mục | Loại | Kết quả |
|---|---|---|---|
| 5 | Mũi tên HUD đúng hướng (fix hôm nay) | verify | |
| 6 | Cast full/split/resize/profiles persist | verify (cần freeform) | |
| 7 | Bubble 3 zone + autostart split | verify | |
| 3 | Boot autostart (ON) / gauges giữ (OFF) | verify | |
| 8 | Waze nav + speed (source=Waze) | verify | |
| 9 | Speed-limit sign feature ID | **điều tra** | |
| 10 | VietMap widget self-grant bind | verify | |
| 11 | Check-for-update detect | một phần | |
| 12 | Isolation/toggle/Stop/parity | verify | |

## Ghi chú trung thực
- Speed-limit (Phase 9) là **đi tìm feature**, không phải verify — kết quả mong đợi là "biết được feature ID", không phải "biển hiện chắc chắn".
- Update-check (Phase 11) chỉ test tới detect; full install cần release đẩy lên GitHub.
- Split/resize (Phase 6) & boot-autostart-split (Phase 3) **phụ thuộc freeform alive** → bắt buộc power-cycle sau khi set flags (Phase 2→3). Trước power-cycle chỉ Cast Full + DPI chạy.
- Đây là dev smoke trên debug; muốn Stage 11 chính thức phải qua Track B (commit + candidate + matrix + sign-off).
