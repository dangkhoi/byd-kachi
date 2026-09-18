# BUG2 — Cài Kachi vào xe → ADB wireless chết (cả xe bạn). Trace.

> **Trạng thái**: Đang trace · **Ngày**: 2026-09-18 · **Mục đích**: Ghi kết quả trace off-car (đọc source) + phép thử QUYẾT ĐỊNH trên xe. Owner báo: cài Kachi → không `adb connect` wireless được nữa; không riêng xe owner, **xe bạn cũng y hệt**; *"không được quyền sửa các quyền này của xe"*.

## 1. Kết luận trace off-car (đọc source — [ĐO grep toàn repo])

**Kachi KHÔNG sửa quyền/thiết lập ADB một cách tường minh.** Đã grep toàn bộ mã: **0 chỗ** chạy `adb tcpip`, `setprop service.adb.*` / `persist.adb.*`, ghi `adb_keys`, `settings … adb_enabled/adb_wifi`, restart `adbd`. Đường cấp-quyền của Kachi chỉ chạm `cmd notification allow_listener` · `settings put secure enabled_accessibility_services` · `cmd package set-home-activity` · `pm grant` · `appops set` · `am`/`wm`/`input` — **không lệnh nào đụng adbd/adb**.

⇒ Kachi **không cố ý** đổi quyền ADB của xe. Nhưng nó **chiếm dụng adbd** như sau:

| Đường | Cơ chế | Vòng đời |
|---|---|---|
| `ShellTransport` | 1 kết nối `Dadb.create("localhost",5555,key)` — **cache `db`, GIỮ MỞ**, chỉ đóng khi lệnh lỗi | **thường trú** (mở ở lệnh cửa sổ/cast đầu tiên, không đóng) |
| `NavConnect` | dadb session cho `allow_listener` / rebind / grant a11y | mỗi lần cấp quyền |
| `UpdateChecker`/`LocalDeviceShell` | dadb `install -r` (OTA) | mỗi lần kiểm cập nhật |
| `AssistantLauncher` | dadb cho voice-key | mỗi lần bấm phím |
| `VietMapAutostart` | dadb whitelist float + pidof/monkey | mỗi lần mở |

Tất cả auth bằng **1 key Kachi tự sinh** (`AdbKeys.ensure` → `adb.key/adb.pub` ở filesDir). Lần đầu nối, adbd (xe có `ro.adb.secure=1` — vì thế mới hiện popup *"Allow USB debugging"*) thêm **public key của Kachi** vào `adb_keys` của xe khi owner bấm Allow.

## 2. Vì sao việc chiếm adbd có thể làm CHẾT adb wireless ngoài (giả thuyết xếp hạng)

Breakage là **tác dụng phụ của việc Kachi nối/giữ kết nối adbd**, không phải một lệnh sửa quyền. Xếp hạng theo khả năng:

- **H1 (mạnh nhất) — adbd giới hạn transport, Kachi giữ 1 kết nối thường trú.** `ShellTransport` giữ MỞ một transport loopback 24/7. adbd tuỳ biến trên IVI khoá có thể chỉ nhận **một** transport TCP tại một thời điểm (hoặc ít). Laptop `adb connect car:5555` không lấy được transport ⇒ "offline"/từ chối. **Khớp mọi bằng chứng**: cài Kachi (Kachi chạy → giữ kết nối) → wireless chết; xe bạn y hệt (hành vi của Kachi, không phải xe). Deterministic.
- **H2 — auth `ro.adb.secure=1` nghẽn.** adbd xử lý AUTH tuần tự; Kachi liên tục nối (nhiều đường ở §1) ⇒ AUTH của laptop bị kẹt sau AUTH pending của Kachi.
- **H3 — rò fd/transport ở adbd.** Nhiều đường Kachi nối rồi (nếu) không đóng sạch ⇒ adbd tích fd → hết slot → chối kết nối mới. (Ít khớp "deterministic + xe bạn y hệt" hơn H1.)
- **H4 (đã yếu đi) — đổi thiết lập bền.** BÁC một phần: grep cho thấy Kachi không ghi prop/adb_keys/settings adb. Chỉ còn khả năng adbd tự thêm key Kachi vào `adb_keys` (thêm, KHÔNG xoá key laptop) — riêng việc này không giải thích được laptop mất quyền.

## 3. PHÉP THỬ QUYẾT ĐỊNH trên xe (chạy khi xe online)

**Test A — force-stop (phân biệt "kết nối sống" vs "thiết lập bền"):**
```
# xe đang bị: laptop KHÔNG adb connect wireless được
adb ... shell am force-stop com.byd.launcher      # (qua kênh còn dùng được, hoặc trên màn xe)
# thử lại adb connect <car-ip>:5555
```
- Wireless **TRỞ LẠI** sau force-stop ⇒ **H1/H2/H3 đúng** (Kachi ĐANG chiếm adbd). Gốc = Kachi giữ/hammer kết nối, KHÔNG phải đổi thiết lập bền. → sửa bằng giảm footprint (đóng kết nối khi rảnh / gộp về 1 đường / công tắc tắt kênh).
- Wireless **VẪN chết** sau force-stop ⇒ Kachi đã đổi thứ gì đó **bền**. Reboot (nút nguồn) rồi thử lại: vẫn chết ⇒ H4 (đổi prop/keys) — kiểm `getprop | grep adb`, `cat $ADB_KEYS`, so trước/sau cài Kachi.

**Test B — số liệu adbd lúc bị:**
```
getprop | grep -iE 'adb|debug'                     # service.adb.tcp.port? persist?
cat /proc/net/tcp | grep 15B3                      # 0x15B3=5555: bao nhiêu kết nối, trạng thái
ls /proc/$(pidof adbd)/fd | wc -l                  # đếm fd adbd (rò?)
dumpsys activity services | grep -i adb
```

**Test C — isolation (chắc chắn nhất):** build Kachi có **công tắc tắt hẳn kênh dadb** (0 kết nối loopback) → bật tắt → reboot → thử adb wireless. Wireless sống khi tắt kênh ⇒ xác nhận 100% + cho owner đường dùng ngay. (Xem §4.)

## 4. Hướng sửa (theo kết quả test, KHÔNG tự đoán)

- **Nếu H1/H2/H3 (force-stop chữa)**: (a) `ShellTransport` **đóng kết nối khi rảnh** thay vì giữ 24/7 (nhưng cân nhắc: nối lại nhiều lần có thể làm H2 tệ hơn — chọn theo Test A/B); (b) **gộp mọi đường dadb về một `ShellTransport`** (hiện NavConnect/OTA/voicekey/VietMap mở riêng); (c) **công tắc "Kênh ADB nội bộ"** (mặc định giữ hành vi hiện tại, tắt = 0 dadb, mọi tính năng cần shell degrade) — vừa là isolation test vừa là lối thoát cho owner.
- **Nếu H4 (bền)**: xác định prop/key bị đổi, khôi phục, và **ngừng** làm điều đó. Owner: *"không được quyền sửa các quyền này của xe"* — nếu Kachi đang đổi gì bền thì phải bỏ.

## 5. Liên hệ với "xe cứ offline" (chặn mọi đo on-car)

[ĐO doc CPU 18-09] xe rớt mạng 100% packet loss + adb daemon Mac bị macOS chặn LAN (phải dùng client python thô). Bug2 (adb wireless chết) **rất có thể là cùng gốc hoặc cộng hưởng** — nếu Kachi chiếm adbd thì mọi lượt đo on-car (CPU per-thread, wake-word) đều bị chặn. ⇒ **Giải bug2 là điều kiện tiên quyết** để đo được CPU (bug phím-thoại) và wake-word trên xe.

## 6. [CHƯA BIẾT] — cần xe
- adbd của ROM DiLink3.0 có giới hạn transport không (H1).
- force-stop Kachi có chữa được không (Test A) — phép thử QUYẾT ĐỊNH.
- Kachi có đổi prop/adb_keys bền không (Test B/C).
