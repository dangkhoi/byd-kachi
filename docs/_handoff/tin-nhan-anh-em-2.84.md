# Tin nhắn gửi anh em test Kachi 2.84

> **Trạng thái**: Session · **Cập nhật**: 2026-09-30 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 2.84 (185) lên kênh cập nhật tự động. Owner 30/09: *"Ota luôn"* (không qua buổi xe, như 2.83). Chép nguyên phần dưới đường kẻ. Nguồn nội dung: spec `docs/specs/kachi-automation.html` §V8/§V8.1 và `docs/specs/kachi-profiles-are-everything.html` §11. Gửi khi commit đăng 2.84 đã lên `main`.

---

Chào anh em, Kachi **2.84** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **2.84 (mã 185)**.

**Bản này có gì**
- **Tự sấy kính khi mưa: hai lựa chọn độc lập.** *Cài đặt › Tiện nghi xe* giờ chỉ còn hai ô: *"Mưa thì tự bật sấy kính trước"* và *"… sau + gương"*, chọn cái nào chạy cái đó, không ràng nhau. Bỏ công tắc chung.
  - Xe tự tắt sấy sau giữa lúc mưa thì Kachi bật lại. Hết mưa Kachi chỉ tắt cái do Kachi bật.
  - Ngay dưới hai ô có **dòng tình trạng** cho từng kính: giờ kiểm gần nhất, có mưa không, xe báo sấy đang bật/tắt, lệnh bật có tới xe không.
- **Cấu hình cụm theo hồ sơ.** Bật chiếu màn, nút nổi, DPI + khung từng app khi chiếu, camera lên cụm và góc hiện nay đi theo từng hồ sơ tài xế. Cấu hình đang dùng đã được chép sẵn vào mọi hồ sơ, không ai mất gì.
  - Đổi hồ sơ lúc đang chiếu thì cụm **không đổi ngay**. DPI/khung của hồ sơ mới áp từ **lần chiếu sau**; bật/tắt chiếu áp từ **lần nổ máy sau** (hoặc bấm *Áp ngay* trong màn Chiếu màn lên cụm).
  - Cam nào là cam trái/phải và phần cắt dải vẫn theo xe.

**Nhờ anh em thử trên xe (không cần máy tính)**
1. Hôm nào mưa: chỉ tích *"sau + gương"* (bỏ tích kính trước), chạy vài phút rồi **chụp màn Tiện nghi xe** chỗ dòng tình trạng. Chấm xanh là chạy đúng (vd *"Kachi bật: tới xe ✓"* hoặc *"đang sấy, để nguyên"*); chấm vàng/đỏ thì chụp gửi luôn, dòng đó nói hỏng ở khâu nào.
2. Tạo hai hồ sơ, mỗi hồ sơ chỉnh DPI khác nhau cho cùng một app chiếu lên cụm. Đổi hồ sơ, chiếu lại app đó, xem DPI có đúng hồ sơ không.
3. Đang chiếu mà đổi hồ sơ: cụm phải đứng yên, không nháy.

**Báo lại**
- Chụp màn hình đúng lúc + **giờ** + việc vừa làm, ví dụ: "15:20, trời mưa, chỉ chọn sấy sau, dòng tình trạng chấm đỏ".
- Che biển số, tên đường, số nhà trong ảnh trước khi gửi.
- Không cần gõ lệnh gì.

Bản này mới thử kỹ trên máy ảo, **chưa chạy trên xe thật**. Cập nhật tự động không quay về bản cũ được; có lỗi thì mình ra bản 2.85. Cảm ơn anh em!
