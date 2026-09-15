# Playbook lượt test XE — verify remediation (Pha 1–4) trước khi push

- **Ngày:** 2026-09-15 · **Chủ:** dangkhoi · **Xe:** DiLink3 (DL3), Android 10. **Xe ĐỖ** (có bước cast dễ treo).
- **Mục đích:** đóng cổng "test xe trước push" cho spec `docs/specs/launcher-window-reconciler-rootcause.html`. 4 việc, mỗi việc có **bằng chứng bắt buộc**.
- **Kết nối:** cầu TCP loopback `127.0.0.1:5556 → <ip-xe>:5555` (adb trực tiếp `No route to host`), rồi `adb connect 127.0.0.1:5556`.
- **Bản để test:** build `vehicleTest` (debuggable, cùng khoá) hoặc release mới nhất; **CHƯA push** — cài tay qua `adb install -r`.

---

## 1) Bug "một app hai ô" — chốt vector + xác nhận HẾT (Pha 2, [CHƯA ĐO])

**Tái hiện + đo TRƯỚC/SAU** (Agent kiến trúc §2: cần biết display nào giữ task + ô nguồn đã trống chưa):

```
# a. Trạng thái sạch: gán GMaps vào ô 1 (picker). Chụp:
adb -s 127.0.0.1:5556 shell am stack list | grep -iE "displayId|maps|youtube"
# b. Gán GMaps vào ô 2 (picker) — CHÍNH thao tác bug. Ngay sau đó chụp lại:
adb -s 127.0.0.1:5556 shell am stack list | grep -iE "displayId|maps"
```

- **PASS**: sau (b), `am stack list` có **đúng 1 task** của GMaps, ở **đúng display của ô 2**; ô 1 không còn khung GMaps (không đóng băng). Registry (nếu đọc được qua T-BRIDGE `state`) chỉ 1 vị trí.
- **FAIL / cần đo thêm**: nếu vẫn 2 nơi → ghi lại **display id của cả hai** + component + `dumpsys display | grep -iE "kachi-slot|VirtualDisplay"` → xác định vector (VdAppHost đóng băng / openAppFullscreen orphan / 2 activity) rồi báo về. **Đừng đoán — dán nguyên `am stack list`.**

## 2) P0-1 `move-task` — cast app THƯỜNG lên cụm KHÔNG treo (xe ĐỖ)

Nguy cơ cũ: `am display move-stack` treo system_server 3/3. Bản mới dùng `am stack move-task`. **Xe phải ĐỖ** (nếu treo, rút cắm khởi động lại).

```
# Cast một app thường (vd VietMap) lên cụm qua bong bóng / lệnh cast. Ngay sau đó:
adb -s 127.0.0.1:5556 shell dumpsys activity | grep -iE "ANR|system_server died" ; echo "exit=$?"
adb -s 127.0.0.1:5556 shell am stack list | grep -iE "displayId|vietmap"
```

- **PASS**: app lên cụm (task ở display cụm), **không** ANR/crash system_server, CarPlay không rớt, màn chính không đơ. Kiểm log có `am stack move-task` (KHÔNG có `am display move-stack`).
- **FAIL**: nếu app không lên cụm (fit sai / freeform-bé) → ghi lại `am stack list` + `dumpsys display`; đây là phần fit [CHƯA ĐO] cần chỉnh.

## 3) Ba fix HAL (kính đóng / rèm 100% / đèn đọc) — qua CapTest

Mở **Cài đặt › Hệ thống › Nâng cao › Kiểm tra từng nút xe**, chạy từng mục:
- **Kính TT/TP/ST/SP + Tất cả kính**: bấm **Đóng** → kính phải ĐÓNG (trước đây gửi state 0 = câm; nay gửi 2). Bấm **Mở** vẫn mở.
- **Rèm che nắng**: bấm **Mở** → rèm mở **HẾT** (trước gửi 1 = "1%"; nay gửi 100%).
- **Đèn đọc**: bật/tắt tay → đèn phải theo (nay ON=2/OFF=1, trước 0/1 không trúng).
- Chấm OK/Không OK; báo cáo tự lưu ra thẻ.

## 4) Baseline HAL (Pha 4) — chụp `HalWriteProbe` cho control đã-verify

Chạy sweep HAL (T-BRIDGE `ctl` / `scripts/vehicle/kachi/71-hal-sweep.sh`) cho một tập control đã chạy thật (kính, ghế, gió, temp…). Lấy `hal-sweep.csv` (cột `id·route·device·hal_line`) về:

```
adb -s 127.0.0.1:5556 pull /sdcard/Android/data/com.byd.launcher/files/kachi-logs/ ./car-logs/
```

- Dùng `hal_line` (FQN/method/rc thật) làm **baseline** cho test `HalBaselineTest` (Pha 4): control nào lệch FQN/rc so với baseline → đỏ. Đây là thứ khoá "xanh mà vẫn hỏng" cho HAL. Gửi CSV về để chốt baseline.

---

## Sau khi 4 mục PASS
→ báo về; mình chạy **senior review (đã chạy off-car) + security scan** rồi **OTA** (bump version, đăng apk, push). Nếu mục nào FAIL, dán nguyên output tương ứng — mình sửa đúng chỗ, không đoán.
