# Handoff sáng 2026-08-18 (chạy autonomous đêm 08-17)

## APK để lên xe
- **`~/Desktop/ClusterNav2.0-debug-len-xe-20260818.apk`** (bản debug, com.byd.clusternav2 vc2/1.1, đã verify chứa: badge UI, VietMap logging, Waze tag fix).
- Cài lên xe: gỡ bản cũ nếu khác chữ ký → `adb install -r` file trên. (debug tự bật verbose → tự ghi log.)

## Đã làm xong đêm nay (commit `d4dd6d5`, đã push feat)
1. **Badge UI kéo-thả**: hình chữ nhật tỉ lệ cụm + kéo cục badge + thanh size (WYSIWYG). Lưu toạ độ tuyệt đối. Đặt trong card Cluster Cast, trên "Khắc phục sự cố". (Bạn đã vọc trên emulator OK.)
2. **BUG-1**: gộp **1 overlay** duy nhất — hết cảnh 2 badge (60 thật + 50 debug).
3. **Dời mục VietMap** từ card Nav+HUD (trái) sang card Cluster Cast (phải) + dòng trạng thái bind.
4. **VietMap full logging** (verbose-gated, đã verify chạy trên emulator): `vietmap_signal_*.csv` (tốc độ + 2 alert) + `vietmap_views_*.csv` (dump mọi view để tìm field lạ) + `diag/vietmap-alert-<hash>.png` (icon cảnh báo/camera, 1 ảnh/hash).
5. **Waze — sửa tag** `WazeHUD` → `WazeHudLink`(+`-BLE`) theo doc WazeMod 5.20.90.901.
6. **Track B2 — ảnh cụm**: đổi `-gmaps`→`-main`; chụp cụm bằng **CẢ HAI** cách: `-cluster.png` (fission) + `-cluster-overlay.png` (screencap -d1, bắt được badge overlay).

Gates: 5 suite XANH · senior review APPROVED · security scan CLEAN (đã scrub IP xe khỏi doc public).

## Waze — kết luận trace-tận-cùng (KHÔNG bỏ, có bằng chứng)
- **Root cause 1 (đã sửa)**: app đọc sai tag logcat (`WazeHUD`) — WazeMod 5.20.90.901 log dưới `WazeHudLink`. Đã sửa.
- **Root cause 2 (chặn chính)**: WazeMod HudLink **chỉ phát HLP khi có thiết bị BT/BLE kết nối** (doc: *"log không tạo transport giả, không thể phát stream nếu chưa kết nối thiết bị"*). Emulator chạy WazeMod nhưng HUD Link kẹt "Starting", tag rỗng → xác nhận. Cùng-máy **không BT loopback được** → không tự cấp kết nối.
- **Vì sao "chạy được trên emulator" trước đây**: CHƯA rõ (mình không tự bật/đăng nhập WazeMod thay bạn được) — nhiều khả năng bản WazeMod cũ (tag `WazeHUD` + log độc lập) HOẶC từng có 1 peer BLE. **Cần bạn xác nhận**: hồi đó WazeMod version nào? có gắn ESP32/thiết bị BLE nào không?
- **Thí nghiệm quyết định (làm trên xe)**: bật WazeMod → HUD Link + `hud_link_log`, rồi thử **app đóng vai HUD device (BLE GATT server)** xem cùng-máy có loopback được không. Mình CHƯA build receiver vì loopback cùng-máy không verify được đêm nay (xe tắt; BT emulator là giả lập → không đáng tin) — build mù rồi ship lên xe là rủi ro. Doc đã có đủ UUID/handshake để build khi bạn quyết.
- **Fallback nếu loopback bất khả thi**: đọc **notification của Waze** (chỉ được nav/hướng rẽ, KHÔNG tốc độ/cảnh báo) — cần bắt format notification lúc lái. Hoặc dùng 1 thiết bị ngoài (ESP32/điện thoại 2) làm HUD peer.

## Chuyến mai nên test (thu data cho off-car)
1. **GMaps** dẫn đường → cụm Giữa+ETA + logs (đã chạy tốt hôm qua).
2. **VietMap** chạy song song, gặp **camera/cảnh báo** → check `vietmap_signal`/`vietmap_views`/`vietmap-alert-*.png` (đây là mục tiêu: xem VietMap bắn thêm gì để vẽ lên cụm).
3. **Badge**: bật cast → chỉnh vị trí/size bằng UI mới → xem có đúng chỗ trên cụm không.
4. **Ảnh cụm**: so `-cluster.png` (fission) vs `-cluster-overlay.png` (screencap) — cái nào phản ánh đúng cụm + badge.
5. **(Nếu muốn)** Waze: bật HUD Link + hud_link_log trên xe → xem tag `WazeHudLink` có ra HLP không (cần peer).

## Cần bạn quyết (open questions)
- Badge chỉ hiện khi cast BẬT — đúng ý chưa, hay cần hiện độc lập cast?
- Waze: WazeMod cũ version nào từng chạy? Có sẵn ESP32/thiết bị BLE để thử receiver không?

## Chưa làm (phụ thuộc)
- **Track L — phân tích log**: chờ data chuyến mai.
- Merge feat→main: chờ PASS trên xe.
