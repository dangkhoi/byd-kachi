# Lượt xe tới — cài thử 2.99 + đo biển tốc độ / HUD / R18

> **Trạng thái**: Session (một phần đã chạy 10/10) · **Cập nhật**: 2026-10-10 · **Chủ**: dangkhoi · **Nguồn**: spec `docs/specs/kachi-298-plan.html` (R18, R9, R-PREP) +
> `docs/specs/kachi-299-plan.html` + chuẩn bị `docs/diagnostics/oncar-prep-2026-10-09-isa-can-odo.md`.
> Owner 10/10: *"note đó lên xe test nhé"* · 2.99 **không OTA** — cài tay lên xe owner qua adb. Xe: `<vehicle-ip>` (không ghi IP vào repo).
> **Kết quả buổi 10/10 (Seal, 2.98 OTA — 2.99 CHƯA cài)**: [`diagnostics/oncar-seal-2026-10-10-r18-sla-can.md`](../diagnostics/oncar-seal-2026-10-10-r18-sla-can.md).
> §1 ✅ (R18 Waze: `esc moved` ×2, không NPE/cầu chì; `mStable=[0,84][1920,990]`) · §2 ⏳ chưa cài 2.99 · §3 ❌ `canmon` 0 khung
> (bảng navopen sai khuôn — sửa navopen trước) · §4 ✅ đã bơm: biển XÁM "50" trên cụm, HUD không, đã trả · §5 ✅ ghi doc/backlog/INDEX.
> Script đã sửa sau buổi: hex liền không dấu phẩy, kiểm độ dài khung, `ONLINE_FIX=1`, cổng số null ⇒ `PARKED-NO-GEAR`; nghe CAN ≤110 s/lượt.
> Bài học: grep logcat theo tag hẹp (một grep rộng đã in một phần token OAuth của GMS ra phiên — không ghi tệp nào).
> Luật: thấy sập lần đầu là dừng (không thử lại thao tác đổi trạng thái khi chưa hiểu gốc); mọi lệnh GHI cần owner duyệt tại chỗ.

## 0. Trước khi đi
- Dựng 2.99 release (cùng chữ ký), ghi sha256. Cầu nc → `127.0.0.1:15555` (memory `kachi-adb-car-tunnel`). Bật chế độ kiểm thử khi màn sáng.

## 1. Lúc đỗ — đọc trước, không đổi gì
1. `dumpsys package com.byd.launcher | grep versionName` (đang 2.98), kéo **toàn bộ `kachi-logs`** của 2.98 về máy (không commit).
2. Tìm trong log 2.98: `esc moved` · `esc off` · `NullPointerException` · `createTaskSnapshot` · `kachi_escape_return_trip` ⇒ kết luận R18 trên xe [ĐO log].
3. `dumpsys window displays | grep mStable` (đỉnh ổn định — tham khảo).

## 2. Cài 2.99 (adb `install -r`, giữ dữ liệu) rồi kiểm
- **R18 bật sẵn**: Waze vào ô → mở địa điểm cho thoát → về ô? `logcat -b events` chỉ `am_resume_activity`/không crash; toast "does not support launch on secondary displays" + thanh BYD có lóe không.
- **Regression ô**: YouTube · VietMap · GMaps trong ô như cũ; đổi hồ sơ; HOME; lùi xe (camera).
- **YouTube tự chuyển bài**: để hết một bài khi Kachi mở lại bài cũ ⇒ có sang bài kế không.
- **Ngày tháng thanh trên**: đọc rõ ở sáng/tối, vài mức trong suốt.
- **VietMap mở nguội**: hộp "IVI không hỗ trợ" che lên, VietMap vẫn trong ô? (`logcat | grep -E "START u0|Failed to put TaskRecord"`).
- Giọng nói: "tắt sưởi ghế phụ tắt gió tự động", "sưởi ghế phụ bật gió tự động", "đóng kính lái xong bật đèn đọc", "hả cốp", "mở <app> vào ô số 2" (mục Odo/VIN — §B3 tài liệu chuẩn bị).
- R6: cổng log khi đang chiếu cụm (R6-D) · hai màn chính (R3) · `dumpsys alarm` sau một đêm (R6-B′).

## 3. Lúc chạy xe — chỉ đọc (script đã soát an toàn)
- `VEH=<vehicle-ip>:5555 scripts/vehicle/can-0x43f-listen-2606.sh` — nghe khung **biển tốc độ `0x237`** khi qua vài biển thật + khung **dẫn đường `0x43F`** khi GMaps đang dẫn (HUD Seal có ra bus không). Không cắt ngang (script tự xoá bảng khi hết giờ, kiểm `register table cleared`).
  - **10/10**: 0 khung — KHÔNG chạy lại tới khi navopen sửa `buildRegTable` (kênh MCU + mode 2 + op 1) và in mã trả; trước đó lấy bảng thật: `logcat -d -s "[DataCollect]CanDataHandle" | grep "sendRegisterTable config:"` (chỉ đọc).

## 4. Lúc đỗ — GHI, chỉ khi owner duyệt tại chỗ
- `scripts/vehicle/isa-0x237-inject-2606.sh` (cổng số P + 0 km/h + gõ `ISA237`): bơm mã 11 = 50 km/h vào `0x2370002E`, nhìn cụm/HUD; trả mã 0. Khuôn `2370002E01<v>` hex liền đã [ĐO 10/10] ⇒ biển XÁM "50" trên cụm, HUD không. Không cần lặp; còn lại chỉ đọc `0x2370002E` khi chạy qua biển thật (xe có tự nuôi ô dự báo không).

## 5. Sau buổi
- Ghi kết quả vào `docs/diagnostics/oncar-…-<ngày>.md` + backlog (`SLOT-ESCAPE-VD-RETURN-ONCAR`, `ISA-NATIVE-SIGN`, `CAN-NAV-0X43F-LISTEN`, `HUD-ROADNAME-OEM-CADENCE`) + INDEX; R18 sạch ⇒ đề xuất OTA 2.99.
- Riêng SL6 (anh em): tên đường lên HUD (R9, 2.98 đã OTA).
