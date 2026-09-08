# ClusterNav — Checklist test trên xe (đỗ P, có người phụ)

> [!CAUTION]
> **ARCHIVED HISTORICAL CHECKLIST — DO NOT EXECUTE.** This file predates the two-track re-baseline, advertises unsupported APK/install/mock-location behavior, and contains unrun Dead Reckon cases. It grants no build/install/car authorization. Dead Reckon remains REMOVE; C1–C4 are NOT STARTED. Any future vehicle run must name an exact APK SHA and approved case set; a real reboot uses the physical power button, not `adb reboot`.

> Cài 1 lệnh, cấp quyền 1 lần, rồi đi theo 3 khối A/B/C. Mỗi mục ghi rõ **PASS = gì**.
> Kết nối: `adb connect YOUR-CAR-IP:5555` (wifi) hoặc cáp USB.

---

## 0. CHUẨN BỊ (1 lần)

- [ ] Cài APK mới: `pwsh docs/diagnostics/autotest.ps1 -Wifi` (tự cài + chạy self-test mọi module + kéo report).
- [ ] **Notification Access**: mở app ClusterNav → bật "Notification Access" (cho đọc noti GMaps).
- [ ] **Accessibility**: Cài đặt > Hỗ trợ > ClusterNav → BẬT (booster đọc màn).
- [ ] **Quyền Location**: chạy `adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode mockprobe` → bấm Allow ở popup quyền (lần đầu), rồi chạy lại.
- [ ] **Mock location app**: Cài đặt > Tuỳ chọn nhà phát triển > "Chọn ứng dụng vị trí mô phỏng" = **ClusterNav**.
- [ ] Tự kiểm: mở app → bấm từng module → "Self-test ▶". Xanh = ok, đỏ = đọc chi tiết.

---

## ★ D. MAP LÊN CỤM (mới — quyết định CẢ vụ mini-map, chạy ĐẦU TIÊN)

**D1. Smoke-test: lấy được surface cụm không?**
```
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode clustersurface
```
- **PASS (✓✓):** log "LẤY ĐƯỢC" + surface valid → **mini-map khả thi!** → chạy D2.
- **FAIL:** `UnsatisfiedLinkError`(libgui) / SELinux / null → cần system_app → **đóng case map**, bỏ qua D2-D4.
- Ghi log: ____________

**D2. Pixel test (chỉ khi D1 PASS) — đẩy nội dung lên cụm**
```
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode clustermap --es how pixel
```
- [ ] Giữ 40s, **NHÌN CỤM**: có **nền xanh + chữ "CỤM MAP OK" + km/h** không?
- **PASS:** thấy → pipeline THÔNG, mình điều khiển được pixel cụm. **Win lớn.**

**D3. Gương màn chính → cụm** (đặt GMaps toàn màn ở màn chính)
```
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode clustermap --es how mirror
```
- [ ] **NHÌN CỤM:** có hiện y hệt màn chính (GMaps map) không?

**D4. GMaps thẳng lên cụm** (chạy nền được nếu thành)
```
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode clustermap --es how gmaps
```
- [ ] **NHÌN CỤM:** GMaps có chịu lên display ảo + resize không?

→ D2/D3/D4 cái nào hiện được → mình build module map cụm thật theo cách đó. (Hoặc mở module **"Map lên cụm (XDJA surface)"** trong app, bấm ①②③ tay.)

**D5. (phụ, EV thấp) đánh thức map ngủ đông:** `--es mode mapmode2` — sweep NAVI_TYPE/DYNAMIC_NAVI, nhìn cụm có đổi gì.

---

## A. MAPS REALTIME (core)

**A0. km hay mét** (sau khi mở GMaps dẫn đường, hoặc test riêng)
```
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode kmtest
```
- [ ] 4 cách V1-V4, **NHÌN CỤM** cái nào ra "km": V2/V4 ra km → cụm đọc AUTO (đã sửa, tự chạy). Cả 4 mét → cứng mét.

**A0b. Vòng xuyến — số nhánh ra**
```
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode roundabout --es exit 3
```
- [ ] **NHÌN CỤM:** glyph vòng xuyến + nhánh ra thứ 3? Thử exit 1/2/4. + Lái thật qua vòng xoay xem GMaps VN có phát "lối ra thứ N" (mình soi log).

**A1. Firmware có TỰ đếm cự ly không (quyết keep/kill nội suy) — LÁI XE**
```
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode holddist --es m 800 --es secs 90
```
- [ ] Giữ ga đều 90s, nhìn cụm.
- **PASS-A (giữ nội suy):** cụm ĐỨNG YÊN "800m" suốt → firmware không smooth.
- **PASS-B (bỏ nội suy):** cụm TỰ TỤT 800→790→... → firmware tự đếm, nội suy thừa.
- Ghi: ____________

**A2. Notification SỐNG khi YouTube che (giả thuyết then chốt)**
- [ ] Mở GMaps dẫn đường → mở YouTube TOÀN MÀN ~40s → đỗ P → mở module **"Soi nhịp noti"**.
- **PASS:** các dòng vẫn có mốc thời gian chạy đều TRONG lúc che (Δ vài giây) → noti sống khi nền.
- Ghi nhịp TB: ______ ms/lần.

**A3. Cụm bám thực tế khi che — LÁI XE**
- [ ] Lái có dẫn đường, mở YouTube che GMaps ~15s ngay TRƯỚC 1 ngã rẽ.
- **PASS:** cụm vẫn đếm ngược mượt + báo rẽ ĐÚNG lúc (không rẽ hụt).

**A4. (bonus) Vắt RemoteViews GMaps** — mở GMaps dẫn đường → module **"Vắt RemoteViews noti GMaps"** → bấm Quét.
- [ ] Xem có field cự-ly-mịn / làn / "then…" ngoài title/text không. Ghi: ____________

**A5. (bonus) Xung audio** — module **"Xung audio dẫn đường"**, lái nghe giọng dẫn.
- [ ] **PASS:** "nav cue" tăng mỗi câu + "usage=12 lộ: CÓ". Nếu UNKNOWN → ROM ẩn, bỏ hướng audio.

---

## B. TIẾNG PÔ (12 preset synth)

```
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode sound --es preset v8 --es vol 2.7
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode soundstop
```
Hoặc trong app: module **"Tiếng pô"** → chọn preset → BẬT → đạp ga.

- [ ] Nghe lần lượt, đạp ga lên tua, NHẢ ga nghe crackle. Chấm điểm preset nào "ngọt":
  - v8 (cross-plane bụp bụp): ___ · v8flat (Ferrari): ___ · v10: ___ · v12: ___ · turbo: ___
  - i6 (BMW): ___ · boxer (911): ___ · harley (potato): ___ · ducati: ___ · rotary: ___ · ev: ___
- [ ] Volume: 2.0× vừa? 2.7× to? (nút Volume hoặc `--es vol`).
- [ ] Còn "giả"/buzz/rè không, preset nào? Ghi để tune `combG`/`fres`/lowpass: ____________
- **PASS:** ít nhất vài preset nghe "như máy thật", không chipmunk, bám ga ~realtime, không rè/ngắt.

---

## C. GPS HẦM — dead-reckoning (theo THỨ TỰ; C1 là go/no-go)

**C1. MOCK có ĐÈ được GMaps không (GO/NO-GO CẢ HƯỚNG)**
```
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode mockprobe
```
- [ ] Bơm 60s → MỞ Google Maps trên head unit.
- **PASS (ĐI):** chấm xe NHẢY về Hồ Gươm Hà Nội → mock đè được → tiếp C2-C4.
- **FAIL:** chấm đứng nguyên → head unit dùng GPS OEM riêng → DỪNG hướng dead-reckoning, báo lại để tính cách khác.
- Ghi: ____________

**C2. Nguồn HƯỚNG**
```
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode sensorscan   # rẽ trái/phải
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode steerscan    # quay vô-lăng
```
- [ ] sensorscan: có TYPE_GYROSCOPE không? Trục nào đổi khi rẽ (= yaw)? Ghi: gyro=___ trục=___
- [ ] steerscan: Bodywork/Special getInstance OK? getter nào đổi theo vô-lăng? Ghi: ____________

**C3. GNSS mất-fix trong hầm — LÁI XE**
```
adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode gnsslog --es secs 120
```
- [ ] Lái qua hầm/gầm cầu. **PASS:** `usedInFix` tụt ~0 trong hầm + hồi khi ra (trigger tin cậy).

**C4. Dead-reckoning thật — LÁI XE (chỉ khi C1 PASS)**
- [ ] Mở module **"Dead-reckoning GPS hầm"** → BẬT service. Mở GMaps dẫn đường. Lái qua hầm.
- [ ] Soi readout: state phải REAL→DEAD_RECKON khi mất sat, "đang BƠM mock", ra hầm về REAL ("vào DR +1").
- **PASS:** trong hầm chấm GMaps ĐI TIẾP theo xe (không đứng/nhảy), ra hầm snap về GPS thật mượt (không giật/nhân đôi).
- **Gyro mặc định TẮT** (an toàn = DR đi thẳng theo hướng vào hầm — đúng cho đa số hầm). CHỈ bật "Gyro heading: BẬT" SAU khi C2 xác minh đúng trục yaw + dấu; bật rồi mà chấm đi lệch (rẽ sai chiều) → trục sai → TẮT lại.
- An toàn: service tự gỡ mock khi ra hầm; có failsafe 5 phút (không bao giờ kẹt mock đè GPS thật). Tắt service = gỡ sạch.
- Ghi độ lệch cuối hầm: ______ m.

---

## Sau test — báo lại để quyết
- A1: firmware tự đếm? (giữ/bỏ nội suy)
- A5: usage=12 lộ?
- B: preset nào ngọt + chỗ cần tune
- C1: mock đè GMaps? (go/no-go) · C2: nguồn heading · C4: DR chạy được không + độ lệch
