# ON-CAR TỐI NAY — 2026-08-13 (bản 1.14) · doc DUY NHẤT, gọn

> Xe: BYD Seal DiLink 3.0, Android 10, không root. Parked-only (số P + phanh tay). Dọn = **power-cycle nút nguồn vật lý**.
> Chi tiết đầy đủ: `oncar-handoff-2026-08-13-1.14.md`. Doc này chỉ để tối nay test chơi: **2 món cần MÒ (C, E)** + vài check nhanh + món mở khác.

## 0. Nạp bản 1.14
- Mở app / bấm **"Kiểm tra cập nhật"** → xe (1.12) OTA lên **1.14**. (`dumpsys package com.byd.clusternav | grep versionName` = 1.14.)
- Nối ADB: `export VEH=<vehicle-ip>:5555 && adb connect $VEH`. navopen sẵn ở `/data/local/tmp/navopen.jar` (các lần trước đã đẩy).

---

## 🔍 MÒ #1 — C: Chốt chế độ hiển thị cụm (Nhỏ / Toàn / value↔menu)
**Vì sao chưa xong:** value→layout do **firmware CỤM (MCU/Qt)** quyết định, không có trong Java; AMAP luôn ghi **value 3** (mặc định) → 1.12 chọn "Toàn=3" thấy không đổi, "Đơn giản=1" đổi được. navopen có `getraw` nên **đọc ngược** được số OEM ghi.

**Cách CHUẨN (readback) — để OEM tự khai số:**
```bash
VEH=$VEH MODE=readback bash scripts/vehicle/nav-screen-mode-probe.sh
```
→ script nhắc bạn **tự vào menu OEM chọn từng option** (Đơn giản/Nhỏ/Toàn/OFF); mỗi lần nó đọc `0x4C10E015` + `0x4C10E01D` (map-sending) + `0x4C10E03A` → ra `nav-mode-probe/value-map.txt`. **Gửi file đó về** là chốt được `Prefs.NAV_SCREEN_*` + nhãn selector.

**Đối chiếu (sweep):** `VEH=$VEH MODE=sweep bash scripts/vehicle/nav-screen-mode-probe.sh` — ghi 0x4C10E015 = 0..7 + combo với map-sending, chụp cụm mỗi bước, tự restore.

**Câu hỏi cần trả lời:** (a) số nào = Nhỏ/Toàn? (b) "mode" là 1 value hay **tổ hợp** nav-screen + map-sending? (c) đổi trong app 1.14 có **áp ngay không cần reboot** không (re-assert 4→2)? (d) **OFF có tắt** nav giữa không?

---

## 🔍 MÒ #2 — E: Gắn nút vật lý mở trợ lý (Gemini / 小迪)
1. App → bật **"Nút vật lý → Trợ lý giọng nói"** → app tự bật Accessibility qua dadb (lần đầu có thể bấm **Allow USB debugging**).
2. Ô **Nút** → chọn **"Học phím…"** → **bấm nút vô-lăng** muốn dùng → toast "Đã gán nút: … (keycode)".
   ```bash
   adb -s $VEH shell "logcat -d -s NavAccess" | tail   # "learned voice keycode=<n>"
   ```
3. Chọn **Cử chỉ** (Nhấn / Nhấn-giữ) + **Mở trợ lý** (Gemini / BYD 小迪 / recognizer) → **bấm nút** → trợ lý mở.
   ```bash
   adb -s $VEH shell "logcat -d -s NavAccess VoiceKeyLauncher" | tail   # "voice-key fire → …" + "launched assistant …"
   ```
4. **Cần mò nếu chưa ăn:** (a) nút có phát keycode tới accessibility không (nếu "Học phím" không bắt được → hệ thống nuốt phím trước → ghi lại, tính đường khác); (b) trợ lý nào **thực sự mở được** trên IVI này (thử lần lượt Gemini/小迪/recognizer). Với cử chỉ **Nhấn-giữ**, nhấn ngắn phải vẫn chạy chức năng gốc của nút.

---

## ✅ Check nhanh (mấy cái này coi như pass — chỉ liếc, lệch thì báo)
- **A · Quyền:** mở app Nav+HUD **mặc định TẮT**; gạt ON → tự cấp quyền (Allow USB debugging lần đầu) → nav lên, hết toast "IVI không hỗ trợ". Reboot → khỏi cấp lại.
- **B · Nav/HUD:** **I1** mũi tên **HUD đúng hướng** (hết ngược) **+ cụm giữa vẫn đúng** (nếu cụm giữa lệch → báo, revert 1 dòng); **I2** tên đường dài chạy marquee mượt; **I3** cự ly bước 10m.
- **D · Boot:** tắt/mở máy → **app tự mở**; Cast tắt = không bubble, Cast bật = app + bubble.

---

## 🧪 Nếu còn hứng (nặng đô hơn, có tool sẵn)
**#3 — Ép biển báo tốc độ = 88 trên cụm** (custom speed-limit). Off-car RE 2026-08-11 đã tìm 2 cửa non-root + dựng tool; Door A/B đã recon (có `scripts/vehicle/doorA_*.txt`, `doorB_canmon.txt`) nhưng **chưa xác nhận inject 88 lên được sign**.
```bash
VEH=$VEH bash scripts/vehicle/hud3-speedlimit-v4.sh          # recon: đọc /collect2 config + sniff CAN
VEH=$VEH FRAME=<id..,88,..> bash scripts/vehicle/hud3-speedlimit-v4.sh   # inject 88 + screencap -d1
```
> Cần `navopen-v4` (readcfg/canmon verbs) trên máy — không có trong `apks/`, nếu thiếu thì bỏ qua. Đây là side-project CAN, không phải món casual.

## 🚫 Biết trước là bí — đừng tốn công mò bằng ADB
**#1 — Bản đồ FULL trên kính HUD:** gated phần cứng/coding (`INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG 0x38B00030`, dealer UDS). HUD trim này không có widget nav đầy đủ. Kế hoạch của owner = **đổi HUD hardware**, không phải chuyện ADB. (Mũi tên TBT thì vẫn lên — chính là I1.)

---

## Dọn trước khi rời
- **Power-cycle nút nguồn vật lý** (dọn toggle HUD/mode/opcode). Chọn lại chế độ cụm mong muốn nếu đã sweep.
