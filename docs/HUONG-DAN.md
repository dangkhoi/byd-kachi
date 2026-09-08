# ClusterNav — Hướng dẫn sử dụng

> **Trạng thái**: Current · **Cập nhật**: 2026-08-16 · **Mục đích**: Hướng dẫn dùng ClusterNav 1.30 (bật Nav+HUD, cấp quyền, cluster display, voice-key). VI + EN.

> Phiên bản: **1.30** (versionCode 130). Dự án cá nhân thử nghiệm trên BYD DiLink 3.0 (Android 10). Không liên kết với BYD.

ClusterNav có **hai hệ thống độc lập** trên cùng một app:

1. **Navigation + HUD** — đưa chỉ dẫn Google Maps / Waze / VietMap lên **cụm đồng hồ** (làn nav + "Giữa + ETA").
2. **Cluster Cast** — chiếu app đang mở lên cụm đồng hồ.

Mặc định **cả hai đều TẮT** khi mở app lần đầu — mở app lên không tự đụng gì vào xe. Bật cái nào thì dùng cái đó.

---

## 1. Navigation + HUD

### Bật lần đầu
1. Mở ClusterNav → gạt công tắc **"Navigation + HUD"** sang BẬT.
2. Lần đầu app tự xin **quyền đọc thông báo** ngay trong app (không cần laptop/ADB): nó cấp quyền qua ADB nội bộ (loopback). Nếu xe hiện hộp thoại **"Allow USB debugging?"**, bấm **Allow** một lần (chỉ lần đầu).
3. Xong → app kết nối nguồn dẫn đường. Mở Google Maps / Waze / VietMap và dẫn đường như bình thường.

> Trước 1.13, bấm "Cấp quyền" hay hiện *"Hệ thống IVI không hỗ trợ hoạt động này"* vì head-unit không mở được màn Cài đặt "Truy cập thông báo". 1.13 bỏ hẳn đường đó: quyền notification là **quyền ADB**, nên app tự cấp qua ADB nội bộ. Màn Cài đặt chỉ còn là phương án dự phòng cuối.

### Chế độ hiển thị trên cụm
Chọn ở ô **"Chế độ hiển thị trên cụm"** — từ 1.30 chỉ còn **Bật / Tắt**:
- **Bật (Giữa + ETA)** — chỉ dẫn hiện ở **giữa cụm** kèm ETA.
- **Tắt** — không đẩy nav ra giữa cụm.

> Ba chế độ layout cũ (Toàn màn hình / Màn hình nhỏ / chỉ-OFF) đã bỏ vì không đổi được live từ Android nếu không root — nên bộ chọn rút gọn còn **Bật / Tắt**.

### Vòng xuyến
Khi đi qua **vòng xuyến**, cụm/HUD nay hiện **hướng ra** (trái / phải / thẳng / quay đầu) và **số lối ra** thay vì chỉ báo chung chung "vào vòng xuyến".

### Nút trên card
- **Nguồn dẫn đường** — Tự động / Google Maps / Waze.
- **Nguồn tốc độ + cảnh báo** — VietMap / Waze.
- **Cấp quyền / kết nối lại** — cấp quyền (nếu thiếu) hoặc kết nối lại nguồn dẫn đường.
- **Dừng toàn bộ** — dừng đẩy cụm.

Tắt công tắc **Navigation + HUD** → cụm về đồng hồ. Quyền đã cấp vẫn giữ qua khởi động lại, nên lần sau bật lại không phải cấp lại.

---

## 2. Cluster Cast

Gạt công tắc **"Cluster Cast"** sang BẬT (mặc định TẮT — khi tắt, cụm vẫn hiện dẫn đường + HUD như thường).

- **Chạm nút nổi** (bong bóng) → chiếu app đang mở lên cụm đồng hồ.
- **Chạm lại** → trả app về màn chính, cụm về chờ.
- **CarPlay / Android Auto** — luôn chiếu **toàn màn hình**, không resize.
- **App thường** — chiếu **toàn màn hình** hoặc **chia đôi** (trái/phải), chỉnh được kích thước.
- Cần quyền **vẽ overlay** một lần (để hiện nút nổi).

Tắt ClusterNav → cụm tự về đồng hồ mặc định.

---

## 3. Nút vật lý → Trợ lý giọng nói *(tuỳ chọn, mặc định TẮT)*

Gán một nút vật lý (vô-lăng / táp-lô) + cử chỉ để mở trợ lý giọng nói. **Không đổi chức năng gốc của nút** — app chỉ "bắt" đúng tổ hợp bạn cấu hình, các phím khác đi qua như thường.

1. Gạt **"Nút vật lý → Trợ lý giọng nói"** sang BẬT. App tự bật dịch vụ **Hỗ trợ (Accessibility)** qua ADB nội bộ (nếu chưa bật) — cũng có thể cần bấm **Allow USB debugging** lần đầu.
2. Chọn **Nút**: chọn từ danh sách ứng viên, hoặc **"Học phím…"** rồi bấm chính nút vật lý muốn dùng trên xe (app ghi lại keycode).
3. Chọn **Cử chỉ**: **Nhấn** hay **Nhấn giữ**.
4. Chọn **Mở trợ lý**: **Google / Gemini**, **BYD 小迪**, hoặc **Nhận dạng giọng nói**.

> Không biết chắc nút phát ra mã gì? Dùng **"Học phím…"** rồi bấm nút trên xe. Lên xe test nếu chưa ăn thì học lại / đổi cử chỉ.

> **Sau khi khởi động lại xe mà nút không còn tác dụng?** Gạt **"Nút vật lý → Trợ lý giọng nói"** **TẮT rồi BẬT lại** — thao tác này cấp lại quyền Hỗ trợ (Accessibility) và gắn lại dịch vụ (kèm timeout để không bị treo), **không cần mở lại app**.

---

## Tóm tắt

| Thao tác | Kết quả |
|----------|---------|
| Mở ClusterNav | Không đụng xe (mọi thứ mặc định TẮT) |
| Bật Navigation + HUD | Tự cấp quyền notification (qua ADB) + kết nối; chỉ dẫn lên cụm |
| Chọn "Chế độ hiển thị trên cụm" | Bật/Tắt nav ở giữa cụm (Giữa + ETA) |
| Bật Cluster Cast + chạm nút nổi | Chiếu app đang mở lên cụm; chạm lại để về |
| Bật "Nút vật lý → Trợ lý" | Bấm nút đã gán → mở trợ lý giọng nói |
| Tắt ClusterNav | Cụm về đồng hồ |

## Lưu ý

> ⚠️ Đây là dự án cá nhân thử nghiệm. Không đảm bảo an toàn lái xe, tương thích phần cứng, khả năng hoàn tác, hay sẵn sàng sản xuất. Cài đặt và sử dụng hoàn toàn tự chịu rủi ro. Không liên kết với BYD.

---

# ClusterNav — User Guide (English)

> Version: **1.30** (versionCode 130). Personal hobby experiment on BYD DiLink 3.0 (Android 10). Not affiliated with BYD.

ClusterNav has **two independent systems** in one app:

1. **Navigation + HUD** — puts Google Maps / Waze / VietMap guidance on the **instrument cluster** (lane + "Giữa + ETA" centre).
2. **Cluster Cast** — casts the foreground app onto the cluster.

Both are **OFF by default** on first launch — opening the app touches nothing on the car. Turn on what you need.

---

## 1. Navigation + HUD

### First-time enable
1. Open ClusterNav → flip the **"Navigation + HUD"** switch ON.
2. The first time, the app grants **notification access** in-app (no laptop/ADB): it grants the permission over local ADB (loopback). If the car shows an **"Allow USB debugging?"** dialog, tap **Allow** once (first time only).
3. Done → the app connects to the navigation source. Open Google Maps / Waze / VietMap and navigate as usual.

> Before 1.13, tapping "Grant" often showed *"Hệ thống IVI không hỗ trợ hoạt động này"* because the head unit can't open the Android "Notification access" settings screen. 1.13 drops that path: the notification permission is an **ADB permission**, so the app self-grants it over local ADB. The settings screen is now only a last-resort fallback.

### Cluster display mode
Pick in **"Cluster display mode"** — since 1.30 it's just **On / Off**:
- **Bật (Giữa + ETA)** — guidance shown in the **cluster centre** with ETA.
- **Tắt** — don't push nav to the centre.

> The three old layout modes (Full screen / Small / OFF-only) were removed because they can't switch live from Android without root — so the selector is reduced to **On / Off**.

### Roundabouts
Going through a **roundabout**, the cluster/HUD now shows the **exit direction** (left / right / straight / u-turn) and the **exit number** instead of a generic "enter roundabout".

### Card buttons
- **Navigation source** — Auto / Google Maps / Waze.
- **Speed + alert source** — VietMap / Waze.
- **Grant / reconnect** — grants the permission (if missing) or reconnects the source.
- **Stop all** — stops pushing to the cluster.

Turn the **Navigation + HUD** switch OFF → the cluster returns to the clock. The granted permission persists across reboots, so you won't need to re-grant next time.

---

## 2. Cluster Cast

Flip the **"Cluster Cast"** switch ON (OFF by default — while off, the cluster still shows navigation + HUD normally).

- **Tap the floating button** → cast the foreground app to the cluster.
- **Tap again** → return the app to the main screen; cluster goes to standby.
- **CarPlay / Android Auto** — always cast **full-screen**, no resize.
- **Regular apps** — cast **full-screen** or **split** (left/right), resizable.
- Requires the **draw-overlay** permission once (for the floating button).

Close ClusterNav → the cluster returns to the default clock.

---

## 3. Physical button → Voice assistant *(optional, OFF by default)*

Map a physical button (steering wheel / dashboard) + gesture to launch a voice assistant. It **does not change the button's native function** — the app only intercepts the exact combo you configure; every other key passes through.

1. Flip **"Physical button → Voice assistant"** ON. The app enables the **Accessibility** service over local ADB (if not already on) — may also need **Allow USB debugging** the first time.
2. Pick **Button**: choose a candidate from the list, or **"Learn key…"** then press the actual physical button on the car (the app records its keycode).
3. Pick **Gesture**: **Press** or **Hold**.
4. Pick **Open assistant**: **Google / Gemini**, **BYD 小迪**, or **Speech recognizer**.

> Not sure which code your button emits? Use **"Learn key…"** and press it on the car. If it doesn't trigger on-car, re-learn or change the gesture.

> **Button stopped working after a reboot?** Flip **"Physical button → Voice assistant"** **OFF then ON** — this re-grants the Accessibility permission and re-binds the service (with a grant timeout so it can't hang), **no app restart needed**.

---

## Summary

| Action | Result |
|--------|--------|
| Open ClusterNav | Touches nothing (everything OFF by default) |
| Enable Navigation + HUD | Self-grants notification access (over ADB) + connects; guidance on cluster |
| Pick "Cluster display mode" | Turn centre nav on/off (centre + ETA) |
| Enable Cluster Cast + tap floating button | Cast the foreground app to the cluster; tap again to return |
| Enable "Physical button → Voice assistant" | Press the mapped button → open the voice assistant |
| Close ClusterNav | Cluster returns to clock |

## Disclaimer

> ⚠️ This is a personal hobby experiment. No driving-safety, compatibility, reversibility, or production-readiness claim. Install and use at your own risk. Not affiliated with BYD.
