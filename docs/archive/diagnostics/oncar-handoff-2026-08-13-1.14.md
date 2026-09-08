# ON-CAR HANDOFF — verify 1.14 (HUD arrow · marquee · 10m · cluster-mode · autostart) · 2026-08-13 (pm2)

> **Supersedes** `oncar-handoff-2026-08-13-1.13.md`. Bản 1.14 **gộp cả** thay đổi 1.13 (chưa test on-car) + 5 fix mới từ lần chạy 1.12 → verify chung trên MỘT build.
> Xe: BYD Seal DiLink 3.0, Android 10 (API 29), **không root**. Chủ: Đăng Khôi (dangkhoi).
> **1 CHỖ DUY NHẤT** cho lần lên xe tới. Tất cả **parked-only** (số P, phanh tay). Dọn = **power-cycle nút nguồn vật lý** (không tính `adb reboot`).
> **1.14 đã build off-car XANH nhưng CHƯA lên `main`/OTA** — trên `main` vẫn 1.12. Test 1.14 phải **cài tay APK** (§1). Chưa uỷ quyền merge/push.

---

## 0. TRẠNG THÁI (off-car 2026-08-13 pm2)

| Build | 1.14 (versionCode 114) — off-car XANH: `:core:test`+`:app:testDebugUnitTest`+`assembleRelease` SUCCESSFUL (1494 cases), lint pass, aapt2 vc114 no test surface, senior review APPROVED (1×P3 đã vá), security scan CLEAN. |
|---|---|

**Gồm (chưa cái nào verify on-car):**
- **1.14** — I1 mũi tên HUD (CAN toHudIcon) · I2 marquee mượt (time-based) · I3 nội suy bước 10m · I4 chế độ cụm (OFF→clear + áp-ngay re-assert) · I5 tự mở app khi boot.
- **1.13** (mang theo) — tự cấp quyền notification qua dadb (bỏ màn Settings chết) · Nav+HUD mặc định TẮT · nút vật lý → trợ lý giọng nói.

---

## 1. CHUẨN BỊ (nối máy + cài 1.14)
```bash
export VEH=<vehicle-ip>:5555        # HỎI lại IP (hotspot đổi), ĐỪNG đoán
adb connect $VEH && adb devices
adb -s $VEH install -r app/build/outputs/apk/release/app-release.apk
adb -s $VEH shell dumpsys package com.byd.clusternav | grep versionName   # kỳ vọng 1.14
```
- Cụm = `fission_screencap -d 0` (thấy đồng hồ). Log: `-s NavigationHudOwner NavConnect NavAccess VoiceKeyLauncher ClusterBroadcaster NavRebind`.
- Test default-OFF/boot sạch: cân nhắc `pm clear com.byd.clusternav` (mất pref) rồi cài lại.
- navopen ở `/data/local/tmp/navopen.jar` (uid shell) cho §2B.

---

## 2. VIỆC PHẢI LÀM ON-CAR

### 2A — I1: Mũi tên HUD (chính) + regression cụm
1. **Cast = TẮT**, mở GMaps dẫn có route thật.
2. Đi/đỗ qua vài lệnh **rẽ trái / rẽ phải / vòng xuyến**. Kỳ vọng:
   - **HUD kính lái**: mũi tên nay ĐÚNG hướng (hết mirror trái↔phải).
   - **Cụm** (nhỏ + Giữa+ETA): **VẪN ĐÚNG** (regression — quan trọng). 1.14 đổi mã ghi INSTRUMENT_GUIDE_INFO_SIMPLE_SET từ AMAP→CAN (`toHudIcon`).
3. **Nếu cụm giữa bị lật** (tức cụm giữa cũng đọc feature này, không phải broadcast) ⇒ báo lại: revert 1 dòng trong `NavRepository` (đổi `maneuver?.toHudIcon()` về `maneuverCode`) và HUD sẽ cần đường feature riêng — mò tiếp on-car.
   ```bash
   adb -s $VEH shell "logcat -d -s NavigationHudOwner" | tail   # icon=<CAN 1=trái/2=phải> để đối chiếu
   ```

### 2B — I4: Chế độ hiển thị cụm (value map + áp-ngay + OFF)
1. Trong app, spinner **"Chế độ hiển thị trên cụm"** đổi lần lượt **Đơn giản → Toàn màn hình → Màn hình nhỏ → OFF** khi ĐANG dẫn. Với mỗi lựa chọn: xem có **áp NGAY không cần reboot** không (1.14 ép re-assert status 4→2 qua `owner.reapply`). Chụp cụm mỗi mode.
2. **OFF**: kỳ vọng nav giữa cụm **tắt** (status=4/clear), làn strip vẫn còn.
3. **Chốt value↔menu** — dùng script đã chuẩn bị off-car: `scripts/vehicle/nav-screen-mode-probe.sh`.
   > **RE 2026-08-13:** value→layout do **firmware CỤM (MCU/Qt)** quyết định, KHÔNG có trong Java. `AmapService.setNaviScreenStatus` chỉ là passthrough và AMAP **luôn ghi value 3** (mặc định/reset) → vì thế 1.12 chọn "Toàn=3" thấy **không đổi** (trùng baseline AMAP), còn "Đơn giản=1" thì đổi được. navopen có `getraw` nên **đọc ngược được** giá trị OEM ghi.
   - **Cách CHUẨN (readback)** — bạn tự vào menu OEM chọn từng option, script đọc lại số OEM ghi (ground truth):
     ```bash
     VEH=$VEH MODE=readback bash scripts/vehicle/nav-screen-mode-probe.sh
     ```
     → ra `nav-mode-probe/value-map.txt` (mỗi label → 0x4C10E015 + 0x4C10E01D map-sending + 0x4C10E03A dyn-navi). Gửi file này lại để set đúng `Prefs.NAV_SCREEN_*` + nhãn selector.
   - **Sweep (đối chiếu)** — ghi 0x4C10E015 = 0..7 + combo với map-sending, chụp cụm mỗi bước, tự restore:
     ```bash
     VEH=$VEH MODE=sweep bash scripts/vehicle/nav-screen-mode-probe.sh
     ```
   - Nghi vấn cần trả lời: "mode" là **1 value đơn** hay **tổ hợp** nav-screen(0x4C10E015) + map-sending(0x4C10E01D) (full-map vs simple)? Combo pass sẽ cho biết.
4. Nếu re-assert 4→2 **vẫn phải reboot mới đổi** ⇒ ghi lại: OEM chỉ áp lúc mở phiên → cần cơ chế khác (mò off-car sau).

### 2C — I2 marquee + I3 10m
- Tên đường DÀI (>~8 ký tự): kỳ vọng **chạy phải→trái ĐỀU, chậm, mượt** (không dựt). Nếu nhanh/chậm quá → chỉnh `ClusterBroadcaster.MARQUEE_STEP_MS` (đang 700ms). Toggle "Chạy chữ tên đường dài (marquee)" bật/tắt được.
- Cự ly <100m: bước nhảy nay **10m** (khớp Google) — chỉ quan sát, không phải làm gì.

### 2D — I5: Tự mở app khi boot
1. **Power-cycle nút nguồn** (boot thật). Kỳ vọng: **app ClusterNav tự mở** (Home) sau khi máy lên.
2. **Cast TẮT** (mặc định): boot → mở app, **KHÔNG** có nút nổi. **Cast BẬT**: boot → mở app **+** nút nổi.
   ```bash
   adb -s $VEH shell "logcat -d -s NavRebind" | tail   # "launch Home requested"; bubble chỉ khi castEnabled
   ```
3. Sau khi OTA/cài đè (MY_PACKAGE_REPLACED): app vẫn tự mở lại (đã có sẵn).

### 2E — (mang từ 1.13) Quyền notification + nút vật lý → trợ lý
- **Notification**: app mở ra công tắc Nav+HUD **TẮT** (default). Gạt BẬT → tự cấp quyền qua dadb (lần đầu bấm **Allow USB debugging** trên xe) → hết toast "IVI không hỗ trợ", nav lên. Reboot → quyền còn, bật lại không cần cấp.
- **Nút vật lý → trợ lý**: bật công tắc "Nút vật lý → Trợ lý giọng nói" (tự bật Accessibility qua dadb) → **"Học phím…"** bấm nút vô-lăng muốn dùng → chọn cử chỉ + trợ lý → bấm nút thử. Xác nhận chức năng gốc của nút KHÔNG mất (với cử chỉ Nhấn-giữ, nhấn ngắn vẫn chạy gốc).

---

## 3. DỌN DẸP (bắt buộc)
- **Power-cycle nút nguồn vật lý**.
- Nếu đổi chế độ cụm lạ / bật voice-key thử → chỉnh lại theo ý sau power-cycle.

---

## 4. THAM CHIẾU
- **Spec 1.14:** `docs/specs/nav-oncar-fixes-1.14.html` (I1-I5, §7 on-car, Reviewer Log Pass 1).
- **Spec 1.13:** `docs/specs/notif-grant-docs-voicekey-1.13.html`.
- **Code I1:** `core/.../navigation/Maneuver.kt` (toHudIcon) · `NavRepository.kt`. **I2:** `ClusterBroadcaster.kt` (MARQUEE_STEP_MS) · `NavFormat.roadWindow`. **I3:** `NavParse.quantizeDisplay`. **I4:** `NavigationHudOwner.kt` (reapply/OFF) · `Prefs.NAV_SCREEN_*`. **I5:** `RebindReceiver.kt` (launchHome).
- **APK off-car:** `app/build/outputs/apk/release/app-release.apk` (vc114). Chưa copy vào `apk/`, chưa lên main.
- **Nợ trước:** `oncar-handoff-2026-08-13-1.13.md`, `oncar-handoff-2026-08-13.md` (1.12).
