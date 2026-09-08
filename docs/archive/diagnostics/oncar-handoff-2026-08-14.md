# ON-CAR HANDOFF — Khôi phục cụm "Đơn giản (Giữa + ETA)" (regression sau reboot) · 2026-08-14

> Xe: BYD Seal DiLink 3.0, Android **10** (API 29), **KHÔNG root**. Chủ: Đăng Khôi (`dangkhoi`).
> **1 CHỖ DUY NHẤT** cho phiên tới. **Parked-only** (số P + phanh tay). Dọn = **power-cycle nút nguồn vật lý** (KHÔNG tính `adb reboot`).
> **HỎI LẠI IP** mỗi phiên (hotspot đổi): `export VEH=<vehicle-ip>:5555`. **ĐỪNG đoán IP.**
> **Nguyên tắc (bắt buộc):** KHÔNG assume — mọi map value↔menu phải **readback trên xe**; mỗi claim trace về nguồn. Xem `.kiro/steering/no-assumptions.md`.

---

## 0. TL;DR
- **Triệu chứng:** sau khi cài 1.17 + **reboot**, cụm **mất "Giữa + ETA"** (nav rớt về **dải nhỏ trên đỉnh**); **mất menu OEM "Nav trên cụm"**.
- **Đã chứng minh KHÔNG phải lỗi code version** (git diff 1.15→1.17: chỉ đổi làm-tròn-khoảng-cách + voice-key; **zero** thay đổi đường centre). Khác biệt = **trạng thái thiết bị do reboot** (menu OEM bị khoá/xám lại).
- **Mục tiêu phiên:** (1) tìm lại **chuỗi mở khoá menu** để cụm về Giữa+ETA; (2) **readback** map value↔menu cho **cả 4 mode** (Đơn giản/Nhỏ/Toàn/OFF) — hết đoán; (3) chốt để app tự khôi phục lúc boot.

---

## 1. BỐI CẢNH — chuyện gì đã xảy ra

Cơ chế "Giữa + ETA" trên cụm (RE + on-car xác nhận hôm 2026-08-13, xem `oncar-2026-08-13-amap-cluster-menu-and-op39-rootcause.md`):
- Nav-trên-cụm do **menu OEM "Nav trên cụm"** quyết định layout: **Đơn giản / Màn hình nhỏ / Toàn màn hình / OFF** (ghi chú OEM: "Chỉ hỗ trợ Bản đồ Amap tùy chỉnh để điều hướng").
- Lever mở/chọn menu = ghi HAL **`SET_NAVI_SCREEN_STATUS_SET (0x4C10E015)`** trên `BYDAutoSettingDevice`.
- **`com.example.amapservice`** (priv-app `/system/priv-app/AmapService`, uid system) là bên **vẽ** nav lên cụm khi nhận data (GMaps → ClusterNav → broadcast `AUTONAVI_STANDARD_BROADCAST_SEND` IS_BYD_MAP=false → `mIsGAODENaving=true`).
- **CHỐT quan trọng [RE:AmapService.java]:** AmapService **chỉ set/đọc `0x4C10E015` lúc init / kill-shutdown, KHÔNG lúc đang dẫn** → layout hiển thị phụ thuộc **giá trị menu đã persist**. Ghi `0x4C10E015` **lúc đang dẫn** thì nó **bơ**.
- **[doc]** "State reset (factory/OTA/wipe) làm menu **xám** lại." → **reboot = một state reset** → menu bị khoá.

Chuỗi sự kiện thực tế:
1. **~10:19 2026-08-13** [doc] mở khoá menu thủ công bằng navopen (ghi `0x4C10E015`). APK **1.15 build 10:25**.
2. Cả ngày **không reboot** → menu giữ mở khoá → **1.15/1.16/1.17 đều thấy Giữa+ETA** (owner: "1.15 cài lên là active ngay").
3. **Tối 2026-08-13 reboot** (để test Gemini) → menu **khoá/xám lại** → nav rớt về dải-top → regression.
4. Tối đó ghi lại `0x4C10E015` (app selector ×4, `navopen setraw`, `navopen full` =3, và combo `01D=1 + 03A=1 + 015=3`) — **KHÔNG re-unlock** được menu, centre **không lên**.

---

## 2. BẰNG CHỨNG (mỗi dòng có nguồn)

### 2.1 KHÔNG phải regression code [git]
`git diff a126d72(1.15) → 3cdac14(1.17)` — file code đổi:
- **1.16** `f9c3b81`: **chỉ** `core/.../navigation/NavParse.kt` (floor→round khoảng cách) + test.
- **1.17** `3cdac14`: **chỉ** `app/.../modules/voicekey/AssistantLauncher.kt` (Gemini launch).
- **Zero** thay đổi ở: `NavigationHudOwner.kt`, `modules/hal/BydHal.kt`, `NavRepository.kt`, `ClusterBroadcaster.kt`, `AmapFrameBuilder.kt`, `Prefs` nav-screen, startup. ⇒ **code 1.17 kích hoạt centre y hệt 1.15.**

### 2.2 On-car tối 2026-08-13 [on-car readback]
- Version xe = `versionCode=117 versionName=1.17` (`dumpsys package`).
- ClusterNav đang chạy, ghi cụm mỗi ~800ms, **rc=0 hết** [logcat NavigationHudOwner]: `cluster-nav icon=.. seg=.. mode=1 → SEND_NAVI_STATUS=0 NAVI_SCREEN=0 GUIDE=0 …` (=0 là **rc thành công**, KHÔNG phải value).
- **Selector 4 mode (app) = NO-OP:** owner đổi OFF/Đơn giản/Nhỏ/Toàn — cụm **không đổi**.
- **Dải-top = làn broadcast của ClusterNav** (owner tắt công tắc Nav+HUD → dải mất; bật → hiện lại). Data GMaps vẫn sang OK (mũi tên + tên đường).
- **Centre (HAL) chết cho MỌI writer:** với ClusterNav bị force-stop, `navopen full 'Test'` ghi `status=2 + screen=3 + GUIDE/CROSSING/ETA/mileage` **rc=0 hết** nhưng cụm **không lên centre** (owner: mũi tên thẳng "Trần Trọng Kim" của GMaps vẫn hiện, KHÔNG phải data test).
- Combo `01D(map-sending)=1 + 03A(dynamic-navi)=1 + 015=3` (navopen, rc=0) → **vẫn không lên centre**.
- `getraw setting 4C10E015 / 4C10E01D / 4C10E03A` đều trả **`-10011`** = SET-only (không đọc được value).
- AmapService **đang chạy** (`ps` → `com.example.amapservice` uid system; ServiceRecord `.AmapService` bound). → không phải process chết.

### 2.3 Value↔menu CHƯA verify [doc + source]
- **Chỉ `3` = "Toàn màn hình"** là navopen-proven (rc=0). **"Đơn giản" = 1 là GUESS** [source:Prefs.kt "Đơn giản đoán=1"].
- Layout có thể là **TỔ HỢP 3 setting** [source:scripts/vehicle/nav-screen-mode-probe.sh]: `0x4C10E015` navi-screen · `0x4C10E01D` **map-sending** · `0x4C10E03A` **dynamic-navi**. ClusterNav hiện **chỉ ghi `0x4C10E015`**.

---

## 3. ĐÃ LOẠI TRỪ (đừng tốn thời gian lại)
- ❌ **Code 1.16/1.17** — git diff chứng minh không đụng centre.
- ❌ **`navi_protect` setprop** — `sys.init.navi_protect=1` là **baseline bình thường**; [doc:verdicts.tsv 2026-07-29] cụm render với navi_protect=1 không đổi.
- ❌ **Context ClusterNav (bypass) hỏng** — navopen (systemMain, device thật) cũng không render → không phải riêng app.
- ❌ **AmapService chết** — nó đang chạy.
- ❌ **Gemini / voice-key** — đã abandon + gỡ UI (mảng khác, không liên quan centre).

---

## 4. GIẢ THUYẾT CÒN LẠI (thứ tự test)
1. **App tự mở khoá khi cài mới (pref=default 3)** — cần §A0 phân định.
2. **Menu cần chuỗi mở khoá đặc biệt** (broadcast-đang-dẫn TRƯỚC + `0x4C10E015`, hoặc value ≠ 3, hoặc tổ hợp 015+01D+03A) — §A1–A3.
3. **Reboot đưa menu vào trạng thái cần re-select thủ công trong Cài đặt** (không phải chỉ ghi HAL) — §B readback sẽ lộ.

---

## 5. CHUẨN BỊ
```bash
export VEH=<vehicle-ip>:5555
adb connect "$VEH"; adb -s "$VEH" shell dumpsys package com.byd.clusternav | grep -E 'versionName|versionCode'
# helper
NAV(){ adb -s "$VEH" shell "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen $*"; }
SHOT(){ adb -s "$VEH" shell "fission_screencap -d 0 -p /data/local/tmp/p.png"; adb -s "$VEH" pull /data/local/tmp/p.png "./$1"; }
```
- Cụm = `fission_screencap -d 0` (thấy đồng hồ = đúng). IVI = `-d 1`.
- navopen sẵn ở `/data/local/tmp/navopen.jar` (uid shell). getraw các id nav → `-10011` (SET-only) là bình thường.
- **Power-cycle nút nguồn** trước khi bắt đầu → boot sạch. Cast **OFF**. Mở **GMaps** dẫn đường thật (đứng yên OK).
- **NGAY sau boot, TRƯỚC khi ghi gì:** mở **Cài đặt xe → "Nav trên cụm"** → menu **có sẵn (chọn được) hay xám/mất?** Chụp. (Ghi trạng thái mặc định boot sạch — dữ liệu quan trọng.)

---

## 6. KỊCH BẢN TEST

### §A0 — Phân định "app tự mở khoá" vs "menu mở sẵn" (LÀM TRƯỚC — trả lời trực tiếp câu 1.15)
```bash
adb -s "$VEH" shell pm clear com.byd.clusternav            # pref về DEFAULT (nav-screen = FULL = 3)
adb -s "$VEH" install -r apk/ClusterNav-1.15-release.apk   # cài đúng bản 1.15 owner nhớ "active ngay"
# → mở app, bật công tắc Nav+HUD, Cast OFF, GMaps đang dẫn, chờ ~10s, NHÌN cụm
adb -s "$VEH" shell "logcat -d -s NavigationHudOwner" | tail
```
- **Cụm LÊN Giữa+ETA ngay** ⇒ **APP TỰ mở khoá** (ghi `SET_NAVI_SCREEN=3` lúc pref=default). ⇒ regression = **pref bị đổi sang value không mở khoá** (vd "Đơn giản"=1). **Fix (§C-1):** app luôn re-assert value đã-proven lúc boot/bật Nav+HUD. Sau đó cài **1.17 SẠCH** (`pm clear` + `-r apk/ClusterNav-1.17-release.apk`) → phải lên y hệt (git đã chứng minh code như nhau) — test để chắc.
- **Cụm VẪN xám/dải-top** ⇒ app KHÔNG tự mở khoá → sang §A1.

### §A1–§A3 — Tìm chuỗi mở khoá (chỉ khi §A0 fail). Sau MỖI bước, **mở Cài đặt xem menu hết xám chưa** (không chỉ nhìn cụm)
```bash
# A1: demo OPEN chuẩn của navopen (status=2 + screen=3 + 1 frame)
NAV
# A2: bơm broadcast "đang dẫn" TRƯỚC, rồi mới OPEN
adb -s "$VEH" shell "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10001 --ei TYPE 1 --ei EXTRA_STATE 1 --ei EXTRA_IS_FOREGROUND 0 --ez IS_BYD_MAP false --ez IS_BYD_BAIDU_MAP false --ei NEW_ICON 3 --ei SEG_REMAIN_DIS 444 --es NEXT_ROAD_NAME 'Le Loi' --ei ROUTE_REMAIN_DIS 6000 --ei ROUTE_REMAIN_TIME 300 --es SEG_REMAIN_DIS_AUTO '444 m' --es ROUTE_REMAIN_DIS_AUTO '6.0 km' --es ROUTE_REMAIN_TIME_AUTO '5 min' --es ROUTE_REMAIN_TIME_STRING '5 min'"
NAV setraw setting 4C10E015 3
# A3: sweep value + tổ hợp, chụp + mở Cài đặt sau mỗi lần
for v in 0 1 2 3 4 5; do NAV setraw setting 4C10E015 $v; sleep 2; SHOT unlock-015-$v.png; done
for c in 0 1; do NAV setraw setting 4C10E01D $c; NAV setraw setting 4C10E03A $c; NAV setraw setting 4C10E015 3; sleep 2; SHOT unlock-combo-$c.png; done
```
- **BÁO LẠI §A:** chuỗi CHÍNH XÁC nào làm **menu hết xám** (đây là fix gốc regression).

### §B — Menu đã chọn được → READBACK map value↔menu (định danh THẬT, hết đoán)
```bash
VEH=$VEH MODE=readback bash scripts/vehicle/nav-screen-mode-probe.sh
```
→ tự tay chọn **Đơn giản → Màn hình nhỏ → Toàn màn hình → OFF**; script readback `0x4C10E015` + `0x4C10E01D` + `0x4C10E03A` sau mỗi lần.
- **BÁO LẠI §B:** file `nav-mode-probe/value-map.txt` — con số cho **cả 4 mode** (giải quyết luôn "3 mode chưa set được": Nhỏ/Toàn/OFF).

### §C — Chốt code (off-car sau khi có §A/§B)
- **§C-1** (nếu §A0 = app tự mở khoá): đảm bảo `NavigationHudOwner`/`NavRepository` re-assert `SET_NAVI_SCREEN` = value **"Đơn giản"** (từ §B) mỗi lần **boot / bật Nav+HUD**, không phụ thuộc pref cũ. Set `Prefs.NAV_SCREEN_SIMPLE` = value THẬT.
- **§C-2** (nếu mode là tổ hợp): `BydHal.writeNavFrame` ghi **cả** `0x4C10E015` + `0x4C10E01D` + `0x4C10E03A` theo bảng §B (hiện chỉ ghi 015).
- **§C-3** (nếu chỉ uid shell mở khoá được, app-uid không): cho app chạy `navopen`/chuỗi mở khoá qua **dadb loopback** (`SimpleCastRuntime.coordinator(ctx).executeShell(...)`, uid shell — hạ tầng đã có) lúc bật Nav+HUD / boot.
- Set toàn bộ `Prefs.NAV_SCREEN_{SIMPLE,SMALL,FULL,OFF}` + `BydHal.NAV_SCREEN_MODE_ON` theo bảng readback §B.

### §D — Verify app tự lên Giữa+ETA
- Set value "Đơn giản" trong app → cụm về **GIỮA + ETA** (không phải dải top).
- Nếu app-uid ghi không render mà navopen (uid shell) render → xác nhận cần đường **dadb loopback** (§C-3).
- Mũi tên rẽ đúng hướng? (`INSTRUMENT_GUIDE_INFO_SIMPLE_SET` nhận mã **AMAP** hay **CAN** — nếu sai map `TurnIdMapToCAN`).

---

## 7. BÁO LẠI (checklist gửi về để chốt code)
- [ ] §0: menu OEM sau **boot sạch** = xám/mất hay có sẵn?
- [ ] §A0: cài **1.15 sạch** → centre tự lên **CÓ/KHÔNG**? (+ log NavigationHudOwner)
- [ ] §A: chuỗi nào **mở khoá** menu (nếu §A0 fail)?
- [ ] §B: bảng `value-map.txt` — value cho **4 mode** (015 [+01D +03A nếu combo]).
- [ ] §D: app set "Đơn giản" → centre lên chưa? mũi tên đúng?

---

## 8. DỌN DẸP (trước khi rời/lái)
- **Power-cycle nút nguồn vật lý** — dọn HAL/opcode + compositor.
- Nếu đã đổi mode lạ / OFF → chọn lại chế độ mong muốn (menu OEM hoặc selector app).

---

## 9. THAM CHIẾU
- Điều tra regression đầy đủ (tối 2026-08-13): `docs/diagnostics/cluster-centre-nav-regression-2026-08-13.md`
- Root-cause menu + cơ chế AmapService: `docs/diagnostics/oncar-2026-08-13-amap-cluster-menu-and-op39-rootcause.md`
- Handoff 1.12 (lever HAL + selector): `docs/diagnostics/oncar-handoff-2026-08-13.md`
- Script probe (readback|sweep): `scripts/vehicle/nav-screen-mode-probe.sh`
- Feature-ids [RE]: `~/Library/Caches/clusternav-re/diagnostic-amap/.../AmapService.java`, `BYDAutoFeatureIds`
- Code: `NavRepository.kt` (wire owner + navOnlyMode gate) · `NavigationHudOwner.kt` · `modules/hal/BydHal.kt` (`writeNavFrame(screenMode)`, `NAV_SCREEN_MODE_ON=3`) · `Prefs.kt` (`navClusterScreenMode`, default FULL=3)
- Commits: `a126d72`(1.15) · `f9c3b81`(1.16) · `3cdac14`(1.17)
