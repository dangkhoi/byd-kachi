# Tin nhắn gửi anh em test Kachi 2.86

> **Trạng thái**: Session · **Cập nhật**: 2026-10-03 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 2.86 (187) lên kênh cập nhật tự động — kiêm nhật ký thay đổi (changelog) cho anh em. Owner 03/10: *"Ok, OTA và viết change log cho anh em"* (không qua buổi xe, như 2.83/2.84/2.85). Chép nguyên phần dưới đường kẻ. Nguồn nội dung: spec `docs/specs/kachi-286-field-fixes.html` (R-SR cửa sổ trời · R-VK phím vô-lăng · R-PI nhập hồ sơ · R-ES khung trống · R-HUD · R-SC lối tắt · R-KC gán phím mọi nút) + commit `c45d326` (giọng nói khi bật Hey Kachi). Tên nhóm/nút/màn trong tin đã đối chiếu `app/src/main/res/values/strings_kachi.xml` + `core/.../SettingsCatalogGroups.kt` và đọc lại trên màn Cài đặt của chính bản đăng [ĐO máy ảo 03/10]. Gửi khi commit đăng 2.86 đã lên `main`.

---

Chào anh em, Kachi **2.86** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **Phiên bản 2.86 (mã 187)**.

**Sửa lỗi anh em báo**
- **Cửa sổ trời**: nút mở/đóng giờ gửi đúng lệnh giống app Cài đặt của xe.
- **Phím vô-lăng gọi Kachi nghe** không còn kẹt ở *"Đang chuẩn bị…"*. Lần bấm đầu ngay sau khi mở xe có thể phải chờ một chút (màn hiện *"Đang nạp giọng nói…"*), các lần sau gần như ngay.
- **HUD dẫn đường (kính lái)**: nếu đang bật *Dẫn đường lên cụm đồng hồ* (*Cài đặt › Dẫn đường & cụm đồng hồ*), sau mỗi lần tắt/mở xe Kachi tự kiểm và nối lại nguồn dẫn đường cho HUD, không phải tắt/bật lại bằng tay nữa. Lưu ý: HUD chỉ nhận chỉ dẫn từ **Google Maps** — VietMap và Waze không lên HUD.
- **Nhập hồ sơ** từ xe khác: app nào trong tệp không có khung chiếu cụm thì giữ khung đang có trên xe mình, không phải bật lại và chỉnh tay. Hộp thoại nhập cho biết tệp có gì, và có nút *Dùng hồ sơ này ngay*.
- **Nói "mở … vào ô 6"** với bố cục tự vẽ nhiều ô (khi bật Hey Kachi hoặc gọi bằng phím vô-lăng) không còn bị trả lời "chỉ có 3 ô".

**Mới**
- **Gán phím cho mọi nút điều khiển của Kachi**: *Cài đặt › Phím vô-lăng › Thêm gán…* → chọn phím → chọn nhóm → chọn việc. Ví dụ một phím → *Khí hậu & không khí › Gió +1*.
  - Núm vặn ở bệ giữa: bấm *Học phím mới…* rồi vặn núm, xem Kachi có bắt được không. Được thì gán *Gió +1* / *Gió −1* cho hai chiều vặn.
  - Phím đã gán sẽ **mất chức năng cũ** (ví dụ núm âm lượng gán sang gió thì không chỉnh âm lượng nữa).
  - Cốp hiện là hai việc riêng *Mở Cốp sau* và *Đóng Cốp sau* — Kachi chưa đọc được cốp đang mở hay đóng nên chưa làm được một nút bấm-mở-bấm-đóng. Mở cốp bằng phím chỉ chạy khi xe đứng yên.
- **Khung trống trong suốt**: khung đã vẽ mà chưa có app giờ thấy hình nền phía sau; chạm nút ⇄ trên khung để chọn app. Bố cục để trống toàn bộ thì giữ trống, không tự điền lại.
- **Lối tắt *Ô 1, Ô 2…***: ô đang là widget thì app vẫn được đặt tạm vào ô đó (widget về lại khi tắt/mở xe hoặc đổi hồ sơ). App trong ô bị tắt thì chạm lại lối tắt là app mở lại vào ô.

**An toàn**
- Mở cửa sổ trời bằng giọng nói sẽ hỏi lại trước khi mở (đóng thì không hỏi). Muốn đổi: *Cài đặt › Giọng nói › Hỏi xác nhận trước khi chạy*.
- Khi bật Hey Kachi, câu "mở cốp" lúc xe đang chạy giờ bị chặn đúng — Kachi đọc tốc độ thật của xe ngay lúc đó.

**Nhờ anh em thử trên xe (không cần máy tính)**
1. Xe có cửa sổ trời mở được, đỗ xe, có người nhìn nóc: bấm ô *Cửa sổ trời* để mở → chờ chạy hết → bấm đóng. Rồi nói "mở cửa sổ trời" (phải hỏi lại) và "đóng cửa sổ trời". Chụp ô sau mỗi lần bấm.
2. Phím vô-lăng gán *Kachi nghe*: mở xe, chờ khoảng 1 phút, bấm phím nói một lệnh; bấm lại thêm vài lần. Ghi lại khoảng bao lâu thì Kachi bắt đầu nghe.
3. HUD: đang bật *Dẫn đường lên cụm đồng hồ*, tắt xe rồi mở lại, dẫn đường bằng Google Maps, không chạm gì trong 1 phút. HUD không lên thì vào *Cài đặt › Dẫn đường & cụm đồng hồ*, chụp mấy dòng bắt đầu bằng *"HUD —"* **trước** khi bấm gì, rồi bấm *Kết nối lại nguồn dẫn đường* và chụp tiếp.
4. Núm vặn ở bệ giữa: *Cài đặt › Phím vô-lăng › Học phím mới…* rồi vặn mỗi chiều. Chụp danh sách nút đã học; học được thì gán *Gió +1* / *Gió −1*, vặn thử xem gió có đổi đúng từng nấc không.
5. Nhập một hồ sơ *để chia sẻ* từ xe người khác: chụp hộp thoại nhập, bấm *Dùng hồ sơ này ngay*, rồi chiếu một app lên cụm xem khung có giữ đúng không.

**Báo lại**
- Chụp màn hình đúng lúc + **giờ** + việc vừa làm, ví dụ: "08:10, vừa mở xe, bấm phím vô-lăng, khoảng 5 giây mới nghe".
- Che biển số, tên đường, số nhà trong ảnh trước khi gửi.
- Không cần gõ lệnh gì.

Bản này mới thử kỹ trên máy ảo, **chưa chạy trên xe thật**. Cập nhật tự động không quay về bản cũ được; có lỗi thì mình ra bản 2.87. Cảm ơn anh em!
