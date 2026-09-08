# ON-CAR RUNBOOK — Track A: probe LAYOUT 4-mode cụm (OEM AMAP) + gap icon merge/tunnel/roundabout

> Phiên `prep_track_a` · 2026-08-14 · **ĐÃ CHẠY on-car 2026-08-16** — xem `## ✅ EXECUTED 2026-08-16` ngay dưới. (research/write-only, KHÔNG sửa code.)
> Xe: BYD Seal DiLink 3.0 · Android 10 (API 29) · **KHÔNG root** · dual-OS (`fission_single_os=0`).
> **Parked-only** (số P + phanh tay). Dọn = **power-cycle nút nguồn vật lý** (KHÔNG tính `adb reboot`).
> **HỎI LẠI IP mỗi phiên** (hotspot đổi) — đừng đoán.
> Phối hợp (oncar-workflow): máy verify được (rc, readback, version, logcat, file) → **agent tự chạy adb**;
> trực quan (cụm hiện Giữa+ETA/dải nhỏ/toàn/off? mũi tên gì? menu xám?) → **HỎI owner 1 câu ngắn** sau MỖI chùm.
> **KHÔNG assume** (no-assumptions.md): mọi kết luận value↔layout phải readback/quan sát THẬT trên xe.

## ✅ EXECUTED 2026-08-16 — KẾT QUẢ (xe owner · parked · session live · IP redact `<vehicle-ip>`)

> Chạy P0/P1/P2a/P2b/P3 trên xe. Mỗi claim trace: `[readback]` rc/getraw thật · `[owner]` quan sát trực quan.

**★ PHÁT HIỆN LỚN — naviState gate (giải thích regression "reboot mất centre"):**
Centre "Giữa+ETA" (HAL `43F01010`) **chỉ render khi có nav session sống (`naviState==1`)**. Raw inject (navopen `frame`, đủ icon+dist+road+eta, kể cả screen=1) trả **rc=0 nhưng KHÔNG hiện** khi thiếu session. Gửi **AMAP broadcast** (`AUTONAVI_STANDARD_BROADCAST_SEND … --ei EXTRA_STATE 1`) mở session → content hiện ngay. `[readback: logcat "AmapService: GuideInfo.naviState: 1"]`. ⇒ Boot/không-session = centre trống; đây là gốc "reboot mất centre".

**Content control = 100%:** đổi `NEW_ICON`/road/dist/ETA qua broadcast → cụm phản ánh đúng (verify bắn info khác nhau: rẽ-phải/Le Loi → U-turn/NGUYEN TRAI/555m). `[owner]`

**P0 (read-only):** `40C03032`=2 ở EASY. ⚠️ **`40C03032` KHÔNG mirror layout thị giác đáng tin** — nó nhảy 2→3 (P2a v=4) rồi →0 (P3 ac5) trong khi **hiển thị LUÔN EASY**. `4C10E015`=-10011 (write-only). `[readback+owner]`

**P2a — `4C10A018` (INSTRUMENT_NAVI_TYPE_SET):** write **ACCEPTED rc=0** (provisioned — KHÁC cửa chết) nhưng **layout thị giác KHÔNG đổi** (v=3+screen=1, v=4+screen=2 đều vẫn EASY). `[readback rc=0 + owner "vẫn Giữa+ETA"]`

**P1 — warm-restart trigger** (`015=v` + status `43E0003A` 4→2 + broadcast ch4): vẫn **EASY**, không land SMALL/FULL. `[owner]`

**P2b — `4C130041` (NAVIGATION_STYLE):** sweep 0..4 → **TẤT CẢ `rc=-2147482648` (REJECTED)** → **dead**, khớp RE (zero-ref trong `libBydDataSource.so`). `[readback]`

**P3 — AutoContainer `ac 5 0`:** `sendInfo(5,0,"")→0`; đẩy `40C03032` 3→0 nhưng **visual vẫn EASY**; Android write KHÔNG kéo state về được (chỉ power-cycle). `ac2 4 <flatbuffer-hex>` cần hex mà navopen **không dump được** → không test được. `[readback+owner]`

**⇒ VERDICT (CHỐT):** **KHÔNG switch được 4-mode layout LIVE từ Android trên trim này nếu không root.** Mọi lever Android-reachable đều: reject (`4C130041`, `EASY_NAVI 4C10E040`, oversea `1F7xxxxx`) HOẶC accept-nhưng-no-visual-effect (`4C10A018`, `4C10E015`, `ac5`). Layout do **cluster-side (fission OS/QML)** quyết; điều khiển quan sát được chỉ là side-effect thô reboot(→small)/app-restart(→centre). **Content điều khiển 100%.** → Hướng thực tế: giữ "Giữa+ETA hiện ỔN ĐỊNH" (đảm bảo naviState sống lúc bật Nav+HUD/boot), **KHÔNG** đuổi theo switch FULL/SMALL live.

**Restore phiên:** `4C10A018=2` (config-53 → boot EASY). Android write không reset được `40C03032` → **power-cycle nút nguồn** cuối phiên cho sạch. Visual đã xác nhận vẫn EASY sau probe.

---

## Nguồn (đã RE off-car — đọc để hiểu WHY, không lặp lại ở đây)
- `docs/diagnostics/re-4mode-amap-layout-mechanism-2026-08-14.md` — §1.2 selector cụm, §2 bảng lever, §4 reboot↔restart, §5 probe P0–P4.
- `docs/diagnostics/re-maneuver-icon-tables-2026-08-14.md` — §2 gap CAN 4/6, §7.5 probe glyph, §9 tunnel/roundabout-exit.
- `docs/diagnostics/oncar-runbook-4mode-restore.md` — FINDINGS 2026-08-14 PM + đường ĐÃ THỬ-FAIL.
- `docs/_handoff/cluster-hud-injection-STATE.md` — verbs navopen, no-root walls.

---

## ⚠️ ĐỌC TRƯỚC — cái gì ĐÃ FAIL (đừng chạy lại như "fix")
Từ `oncar-runbook-4mode-restore.md` (on-car 2026-08-14 PM) — các đường sau **đã loại**, KHÔNG lặp:
- Ghi **chỉ** `0x4C10E015` (dropdown app ×4 · `setraw setting 4C10E015 3` · full-frame navopen) → cụm KHÔNG đổi.
- Combo `015=3 + 01D=1 + 03A=1` (navopen) → KHÔNG đổi.
- `EASY_NAVI 0x4C10E040` sweep 0..3 → **write REJECTED `rc=-2147482648`** (khác `-10011`); cụm KHÔNG đổi.
- `instr 0x1F701010 / 0x1F704010` → read `-2147482648` (not provisioned).
- **Menu OEM GỐC "Nav trên cụm" (không phải dropdown app), bấm cả 4 option → cụm ĐỨNG IM.**

→ Track A này thử những thứ **CHƯA test sạch**: (P0) readback ground-truth 4 state; (P1) **replay trigger warm-restart ĐẦY ĐỦ live** (015 + status re-assert + ch4 push cùng lúc, không bị amapservice kéo lại) — combo duy nhất chưa test tách bạch; (P2) **3 feature-id MỚI provisioned** chưa từng sweep; (P3) kênh AutoContainer; (P-ICON) gap glyph. Nếu P0–P3 vẫn fail → xác nhận verdict "không switch được live nếu không root".

### Giải mã rc navopen (dùng để interpret mọi write/read)
| rc trả về | Nghĩa | Hành động |
|---|---|---|
| `0` | Write ACCEPTED (HAL nhận) | Lever còn sống — xem cụm có đổi không |
| `-10011` | SET-only (write-only) khi `getraw` | Bình thường cho id nav; không đọc được → dựa screenshot/owner |
| `-2147482648` | REJECTED / not provisioned | Id CHẾT trên trim này — bỏ, đừng sweep tiếp |

---

## 0. CHUẨN BỊ (Prep — ~30s, agent tự chạy)
```bash
# HỎI owner IP trước:  "IP hotspot xe hôm nay?"  → điền vào VEH
export VEH=<vehicle-ip>:5555
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" connect "$VEH"

# version đang chạy (xác nhận build)
"$ADB" -s "$VEH" shell dumpsys package com.byd.clusternav | grep -E 'versionName|versionCode'

# navopen helper (jar đã sẵn ở /data/local/tmp/navopen.jar; nếu thiếu, push apks/navopen-v4.jar lên trước)
NAV(){ "$ADB" -s "$VEH" shell "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen $*"; }

# đọc int từ output getraw
RD(){ NAV getraw "$1" "$2" 2>/dev/null | sed -n 's/.*= *\(-\{0,1\}[0-9][0-9]*\).*/\1/p' | tail -1; }

# chụp CỤM (đồng hồ) = -d 0 ; IVI = -d 1
SHOT(){ "$ADB" -s "$VEH" shell "fission_screencap -d 0 -p /data/local/tmp/p.png"; "$ADB" -s "$VEH" pull /data/local/tmp/p.png "./$1"; }

# (tùy chọn) xác nhận package OEM bridge tên gì trên xe (RE thấy com.example.amapservice)
"$ADB" -s "$VEH" shell pm list packages | grep -iE 'amap'
```
**Setup cảnh (owner + agent):** power-cycle nút nguồn → boot sạch → **Cast OFF** → mở **GMaps** dẫn 1 tuyến thật (đứng yên OK) → bật **Nav+HUD** trong ClusterNav (đảm bảo cụm đang render nav).

> Feature-id dùng trong runbook (đã RE):
> `setting 4C10E015`=SET_NAVI_SCREEN_STATUS · `instr 40C03032`=INSTRUMENT_NAVI_TYPE (read) ·
> `instr 43E0003A`=INSTRUMENT_SEND_NAVI_STATUS_SET · `instr 4C10A018`=INSTRUMENT_NAVI_TYPE_SET ·
> `instr 4C130041`=INSTRUMENT_NAVIGATION_STYLE_SET · `setting 4C10E020`=SET_METER_DEPTH_MODE_SET ·
> `instr 43F01010`=INSTRUMENT_GUIDE_INFO_SIMPLE_SET (CAN turn-id).

---

## P0 — READ-ONLY: pin value→layout ở CẢ 4 state (zero-risk)
**Mục tiêu:** giải §4 soft spot — `0x4C10E015` đọc ra **2 hay 3** ở state "Đơn giản"? và `INSTRUMENT_NAVI_TYPE (0x40C03032)` có mirror `m_u8NaviType` (2=EASY/3=SMALL/4=FULL/0=OFF) không?

Owner đưa cụm về TỪNG state (qua dropdown app / menu OEM / restart / power-cycle). Ở MỖI state, agent chạy:
```bash
# đọc ground-truth 2 id chính (+ 1 companion tùy chọn)
NAV getraw setting 4C10E015          # SET_NAVI_SCREEN_STATUS  (có thể ra -10011 = SET-only)
NAV getraw instr   40C03032          # INSTRUMENT_NAVI_TYPE    (kỳ vọng ĐỌC được enum layout)
NAV getraw setting 99000349          # (tùy chọn) METER_DEPTH_MODE status
SHOT p0-<state>.png                  # <state> = easy | small | full | off
```
Chạy đủ 4 lần, mỗi lần đúng 1 state theo owner: **easy (Giữa+ETA)** → **small (dải nhỏ đỉnh)** → **full (toàn màn hình)** → **off**.

**❓ Hỏi owner (1 câu/lần):** "Cụm ĐANG hiện chính xác cái nào: Giữa+ETA / dải nhỏ / toàn màn hình / tắt?" (agent ghép với readback).

**Cách interpret:**
- Nếu `40C03032` đổi số theo state (vd 2/3/4/0) → **đây là gương của layout enum**, và ta có bảng value→layout THẬT → dùng cho P1/P2.
- Nếu `4C10E015` đọc ra số (không phải `-10011`) → ghi lại số cho từng state ⇒ trả lời "015 = 2 hay 3 ở EASY". Nếu ra `-10011` → 015 là SET-only, bỏ qua, dựa vào `40C03032` + sweep P1.
- Nếu cả 2 id bất biến qua 4 state → layout KHÔNG phản chiếu ra id Android đọc được → củng cố "cluster-side, no-root".

**Safety/restore:** thuần đọc, không cần restore.

---

## P1 — MAIN: replay trigger warm-restart LIVE, sweep v ∈ {2, 3, 4, 0}
**Giả thuyết (RE §4):** layout đổi khi `updateNaviType()` được **trigger lúc cụm đang sống** với gate `BODYWORK_POWER_LEVEL==3` thỏa (xe đang bật). Lever = **015 = v** + **re-assert nav-status** + **1 lần push NaviInfo ch4** — đúng chuỗi mà AmapService restart phát. Chưa test tách bạch bao giờ.
> `v`: 2=EASY/centre · 3=SMALL · 4=FULL · 0=OFF (theo `re-4mode` §1.2 `updateNaviDisplay`).
> ⚠️ RE §4: đường guidance STEADY của AmapService **không** ghi lại `015` (chỉ restart/kill/shutdown mới ghi `015=3`). Nên trong lúc dẫn ổn định, giá trị `015` mình set NÊN giữ. Nếu on-car thấy `015` bị kéo về 3 → chuyển sang **P1-B** (tắt nguồn ghi).

### P1-A (KHUYẾN NGHỊ — dùng broadcast AUTONAVI làm trigger, KHÔNG cần hex thô)
Đây là đường push ch4 **proven** (chính app mình đang dùng): `am broadcast AUTONAVI_STANDARD_BROADCAST_SEND` → OEM bridge tự dựng + đẩy NaviInfo ch4 = đúng "trigger". Giữ amapservice sống.
```bash
for v in 2 3 4 0; do
  echo "=== v=$v ==="
  # 1) set OEM nav-screen status
  NAV setraw setting 4C10E015 $v
  # 2) re-assert nav-status như AmapService restart (2=navigating; mẹo ép transition: 4 rồi 2)
  NAV setraw instr 43E0003A 4; sleep 1; NAV setraw instr 43E0003A 2
  # 3) trigger cụm recompute bằng 1 frame guidance THẬT (bridge tự phát ch4 flatbuffer)
  "$ADB" -s "$VEH" shell "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND \
    --ei KEY_TYPE 10001 --ei TYPE 1 --ei EXTRA_STATE 1 --ez IS_BYD_MAP false \
    --ei NEW_ICON 3 --ei SEG_REMAIN_DIS 444 --es SEG_REMAIN_DIS_AUTO '444 m' \
    --es NEXT_ROAD_NAME 'Le Loi' --ei ROUTE_REMAIN_DIS 6000 --ei ROUTE_REMAIN_TIME 300"
  sleep 2; RDv=$(RD setting 4C10E015); echo "   readback 015=$RDv"; SHOT p1a-v$v.png
done
```
**❓ Hỏi owner (1 câu/v):** "Với lần này cụm ra: Giữa+ETA / dải nhỏ / toàn màn hình / tắt / không đổi?"

### P1-B (ĐÚNG SPEC §5 — chỉ khi P1-A thấy 015 bị kéo về 3): tắt nguồn ghi + ac2 hex
Tắt đường ghi 015 của mình rồi bơm thẳng ch4 bằng hex đã bắt:
```bash
# tắt Nav+HUD trong app (owner gạt master switch OFF) HOẶC dừng OEM bridge:
"$ADB" -s "$VEH" shell am force-stop com.example.amapservice   # (đổi tên nếu pm list ở Prep cho tên khác)
for v in 2 3 4 0; do
  NAV setraw setting 4C10E015 $v
  NAV setraw instr 43E0003A 2
  NAV ac2 4 <NaviInfo-hex>        # <NaviInfo-hex> = frame bắt được (xem "Lấy NaviInfo hex" bên dưới)
  sleep 2; SHOT p1b-v$v.png
done
```

**Lấy NaviInfo hex (làm TRƯỚC P1-B, khi bridge còn sống + đang dẫn thật):**
> ⚠️ **CHƯA có verb navopen nào CHẮC CHẮN dump payload ch4** mà bridge phát (chưa verify — no-assumptions).
> - Thử `NAV acprobe` để xem nó có in payload AutoContainer đang chạy không → nếu có, copy chuỗi hex ch4.
> - Nếu `acprobe` không dump payload → **KHÔNG tự bịa flatbuffer** (18 field, dễ sai). Dùng thẳng **P1-A** (broadcast tự sinh ch4) — cùng trigger, khỏi cần hex thô. Ghi vào BÁO LẠI là "capture hex = prep gap".

**Cách interpret P1:** `v` ĐẦU TIÊN làm cụm **đổi layout** = lever sống → ghi `(v → layout)` cho cả 4. Nếu KHÔNG `v` nào đổi (kể cả 015 readback đúng) → 015 xác nhận **no-op** cho switch live ngay cả khi kèm trigger đầy đủ → sang P2.

**Safety/restore:** cuối P1 → owner bật lại Nav+HUD (P1-B), hoặc power-cycle. Đặt lại cụm về Đơn giản mong muốn.

---

## P2 — 3 feature-id MỚI provisioned (chưa từng sweep — ứng viên mạnh)
Từ `re-4mode` bảng lever: 3 id sau **provisioned** (khác EASY_NAVI đã REJECTED) và tên khớp "nav type/style/depth". Sweep từng cái, hỏi owner sau MỖI id.
```bash
# P2a — INSTRUMENT_NAVI_TYPE_SET (tên = "NaviType" — ứng viên MẠNH NHẤT)
for v in 0 1 2 3 4; do NAV setraw instr 4C10A018 $v; sleep 2; echo "4C10A018=$v"; SHOT p2a-$v.png; done
# P2b — INSTRUMENT_NAVIGATION_STYLE_SET
for v in 0 1 2 3 4; do NAV setraw instr 4C130041 $v; sleep 2; echo "4C130041=$v"; SHOT p2b-$v.png; done
# P2c — SET_METER_DEPTH_MODE_SET
for v in 0 1 2 3;   do NAV setraw setting 4C10E020 $v; sleep 2; echo "4C10E020=$v"; SHOT p2c-$v.png; done
```
**❓ Hỏi owner (1 câu/id, sau khi sweep xong id đó):** "Trong lúc mình quét `<tên id>`, cụm CÓ đổi layout ở giá trị nào không? Nếu có, đổi ở lần thứ mấy?"

**Cách interpret:**
- `rc=0` + cụm đổi ở giá trị `v` → **tìm ra lever mới** → ghi `(id, v → layout)`, đây là đường code fix.
- `rc=0` nhưng cụm bất động → provisioned nhưng không phải bộ chọn layout (như 015).
- `rc=-2147482648` → id chết → bỏ, note lại.

**Safety/restore:** nếu sweep để lại giá trị lạ → set lại giá trị proven từ P0/P1, hoặc power-cycle cuối phiên.

---

## P3 — Test KÊNH AutoContainer (ch5 cold-boot vs ch4 flatbuffer)
**Mục tiêu:** kênh (5 vs 4) tự nó có phải "latch" layout không, độc lập với 015?
```bash
# ch5 = mimic AmapService cold-boot sendInfo(5,0,"") — có ép SMALL live không?
NAV ac 5 0 ""
sleep 2; SHOT p3-ch5.png
# ch4 = push flatbuffer — có ép CENTRE live không? (cần <NaviInfo-hex> như P1-B; nếu không có, thay bằng
#        broadcast AUTONAVI ở P1-A rồi chụp)
NAV ac2 4 <NaviInfo-hex>
sleep 2; SHOT p3-ch4.png
```
**❓ Hỏi owner (1 câu):** "Sau lệnh thứ nhất cụm thành gì, sau lệnh thứ hai cụm thành gì (nhỏ/centre/không đổi)?"

**Cách interpret:** nếu `ac 5 0 ""` kéo về SMALL và `ac2 4` kéo về CENTRE → **kênh chính là latch** (giải thích reboot→small vs restart→centre). Nếu cả hai bất động → kênh không phải lever; layout do state cụm quyết.

**Safety/restore:** power-cycle nếu cụm kẹt; bật lại Nav+HUD.

---

## P-ICON-A — Dò gap CAN turn-id 4 & 6 (glyph merge/keep bí ẩn)
**Bối cảnh (icon §2):** trong bảng CAN 1..49, **chỉ 4 và 6 là chưa map** (mọi id khác đã pin qua `TurnIdMapToCAN`). GUESS: có thể là biến thể trái/phải, hoặc **merge/keep**. Đo THẬT, đừng đoán.
> **Cần cụm nav đang active** (đang render mũi tên). Vì mỗi frame guidance ghi đè `43F01010`, ghi 1 phát dễ bị frame kế xóa → ghi **lặp nhanh** để giữ glyph đủ lâu owner nhìn.
```bash
# CAN turn-id = 4  (giữ ~4s bằng lặp)
for i in $(seq 1 10); do NAV setraw instr 43F01010 4; sleep 0.4; done
SHOT picon-a-can4.png
# CAN turn-id = 6
for i in $(seq 1 10); do NAV setraw instr 43F01010 6; sleep 0.4; done
SHOT picon-a-can6.png
```
**❓ Hỏi owner (1 câu):** "Với id=4 mũi tên/biểu tượng cụm vẽ HÌNH GÌ? Với id=6 hình gì? (mô tả: rẽ trái/phải, nhập làn/merge, giữ làn, mũi tên chẻ, hay trống/rác)"

**Cách interpret:**
- Nếu 4 hoặc 6 hiện **glyph merge/nhập-làn hoặc keep-làn** → đó là **target chuyên dụng** cho merge/keep → thay vì fallback STRAIGHT/slight ở `icon §7.1/§7.2`, encode thẳng vào id đó (ghi lại chính xác 4 hay 6).
- Nếu trống/trùng/rác → không có glyph riêng → fallback STRAIGHT (icon §7.2) giữ nguyên là đúng.

**Safety/restore:** một frame guidance THẬT kế tiếp sẽ tự ghi đè `43F01010` về đúng hướng đang đi; để chắc, sau khi chụp xong đẩy 1 broadcast AUTONAVI (như P1-A) hoặc power-cycle. **Chỉ parked** — không làm khi đang lái.

---

## P-ICON-B — GMaps có EXPOSE token hầm + lối-ra vòng xuyến không? (source-limit vs our-drop)
**Bối cảnh (icon §9):** glyph HẦM (NEW_ICON 16→CAN 49) và vòng-xuyến-lối-ra-N (CAN 25..44) **có sẵn trên cụm** nhưng enum mình hiện không phát. Câu hỏi mấu chốt: **GMaps có đưa token đó cho mình không?** Nếu có → mình drop (thêm lại được); nếu không → giới hạn nguồn (không bịa được).
> ⚠️ **Đây là probe DUY NHẤT cần LÁI THẬT.** An toàn: **owner lái bình thường** qua 1 đoạn có **hầm** + 1 **vòng xuyến có đánh số lối ra**; **agent KHÔNG thao tác gì lúc xe chạy**. Đảm bảo **Nav+HUD ON** để pipeline ghi CSV. Pull log **khi đã đỗ lại**.

Khi đã đỗ, agent chạy:
```bash
"$ADB" -s "$VEH" pull /sdcard/Android/data/com.byd.clusternav/files/ ./navarrowlog-<date>/
# xem cột: small_amap, sig_name, verb_amap, final_icon (header đầy đủ:
#  t_ms,maneuver,display_road,raw_road,distance,small_amap,sig_name,sig_amap,verb_amap,heuristic_amap,final_icon,arrow_src,bitmap_hash)
grep -iE 'tunnel|hầm|ham|roundabout|vòng|vong|exit|lối ra|loi ra' ./navarrowlog-<date>/nav_arrow_log_*.csv
# hoặc mở cả file soi các dòng quanh lúc qua hầm / vòng xuyến
```
**❓ Hỏi owner (1 câu):** "Đoạn nào là hầm, đoạn nào là vòng xuyến (áng chừng phút thứ mấy / tên đường)?" — để khớp với timestamp `t_ms` trong CSV.

**Cách interpret:**
- Nếu tại lúc qua hầm, **`small_amap` / `sig_name` / `verb_amap`** có token hầm (icon 16 / chữ "tunnel"/"hầm") → **GMaps EXPOSE** → mình đã drop → thêm lại `Maneuver.TUNNEL` (icon §9) khả thi.
- Nếu tại vòng xuyến, có `NEW_ICON 11/12` kèm số lối-ra (hoặc `sig_name`/`verb_amap` chứa "lối ra N/exit N") → **GMaps EXPOSE** số nhánh → wire `ROUNG_ABOUT_NUM` (icon §7.4/§9) khả thi.
- Nếu **KHÔNG cột nào** có token trong suốt đoạn hầm/vòng xuyến (chỉ straight/slight) → **GIỚI HẠN NGUỒN** (GMaps không cho) → không vẽ được, không phải lỗi mình.
- `final_icon` = cái mình THẬT SỰ vẽ → so với token nguồn để biết mất ở tầng nào.

**Safety/restore:** thuần đọc log; không ghi gì lên xe.

---

## 🚧 NO-ROOT WALLS — biết trước để KHỎI mất công (từ re-4mode §2/§4 + STATE)
- **`m_u8NaviTypeStore` trong `/collect2/byd_datasource_config.xml`** = default layout persist, nằm ở **domain cụm**. uid=2000 shell = **Permission denied**. Ghi cần `SetConfig_UINT8`+`saveConfigXML` bên trong cụm = **cần root**. Door A (`ICollect2FileStoreService.readTextFile`) chỉ **ĐỌC**. → KHÔNG đặt default layout persist được không root.
- **Gate `BODYWORK_POWER_LEVEL==3` (`0x12D0002A`)** + **`BODYWORK_ONLINE_HAS==1` (`0x26F00000`)** = body-CAN read-only status, **không phải `*_SET`**. Chỉ là điều kiện (xe bật/online), KHÔNG phải bộ chọn — thỏa sẵn khi xe đang bật, đừng cố "ghi" chúng.
- **FULL_SCREEN qua PluginMsg 15** (`RECV_MSG_ID_NAVI_FULL_SWITCH_ANIM`) = bus **nội bộ cụm**, không có sink Android → không raise được từ adb.
- **ZMQ `192.168.195.x`** (bus provider→cụm) **không thấy trên interface Android** → không tới được.
- `0x4C10E01D`, `0x4C10E03A`, `0x4C10E040`, `0x1F70xxxx` = **đã chết** (write-only vô hiệu / REJECTED) — đừng probe lại.

→ Nếu **P0–P3 đều fail và P2 không có id nào ăn**: kết luận trung thực = **switch 4-mode live KHÔNG khả thi từ Android trên trim này nếu không root**; điều khiển duy nhất quan sát được là side-effect thô reboot(small)/app-restart(centre). Chuyển mục tiêu sang "Giữa+ETA hiện ỔN ĐỊNH" (re-assert lúc bật Nav+HUD/boot) như `oncar-runbook-4mode-restore.md` §HƯỚNG THỰC TẾ.

---

## ✅ BÁO LẠI (checklist gửi về để chốt hướng code)
- [ ] **Prep:** IP? version cụm? tên package OEM bridge (`pm list | grep amap`) = `com.example.amapservice` hay khác?
- [ ] **P0:** với 4 state — `getraw 40C03032` ra số nào mỗi state? `getraw 4C10E015` ra số hay `-10011`? ⇒ 015 ở EASY = **2 hay 3**? `40C03032` có mirror layout không?
- [ ] **P1:** `v` nào (2/3/4/0) làm cụm ĐỔI layout? readback 015 có giữ giá trị không (hay bị kéo về 3)? Dùng P1-A hay phải P1-B? (nếu P1-B: bắt được NaviInfo hex bằng cách nào — `acprobe` có dump không?)
- [ ] **P2:** `4C10A018` / `4C130041` / `4C10E020` — id nào `rc=0`? id nào đổi layout ở giá trị nào? id nào `-2147482648` (chết)?
- [ ] **P3:** `ac 5 0 ""` → cụm thành? `ac2 4 <hex>` → cụm thành? Kênh có phải latch không?
- [ ] **P-ICON-A:** CAN turn-id **4** vẽ glyph gì? **6** vẽ glyph gì? Có phải merge/keep không?
- [ ] **P-ICON-B:** trong CSV lúc qua hầm/vòng xuyến — `small_amap`/`sig_name`/`verb_amap` có token hầm/lối-ra không? `final_icon` vẽ gì? ⇒ source-limit hay our-drop?

## 9. DỌN DẸP (trước khi rời/lái)
- **Power-cycle nút nguồn vật lý** — dọn mọi HAL write/opcode + trạng thái compositor.
- Nếu P1/P2/P3 để lại giá trị lạ (015, 43E0003A, 4C10A018, …) hoặc OFF → sau power-cycle chọn lại chế độ mong muốn (menu OEM hoặc bật lại Nav+HUD).
- Nếu đã `force-stop com.example.amapservice` (P1-B) → power-cycle để nó chạy lại sạch.
- **KHÔNG commit/push** artifact (ảnh cụm, CSV, log) trước khi qua scan sensitive-data; redact IP xe → `<vehicle-ip>` theo `security-overrides.md`.
