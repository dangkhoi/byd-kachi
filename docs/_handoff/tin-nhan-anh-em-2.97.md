# Tin nhắn gửi anh em test Kachi 2.97

> **Trạng thái**: Session · **Cập nhật**: 2026-10-08 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 2.97 (200) lên kênh cập nhật tự động — kiêm nhật ký thay đổi. Nguồn: spec `docs/specs/kachi-297-plan.html` (R1–R3, R5). Chép nguyên phần dưới đường kẻ. Gửi khi commit đăng 2.97 đã lên `main`.

---

Chào anh em, Kachi **2.97** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **Phiên bản 2.97 (mã 200)**.

**Sửa lỗi**
- **Đổi hồ sơ làm YouTube dừng hát / mở lại từ đầu**: Kachi không còn tắt app nào khi đổi hồ sơ. App mà hồ sơ mới vẫn có thì chạy tiếp ngay trong ô mới (nhạc không ngắt); app không có trong hồ sơ mới chỉ rời khỏi màn hình, nhạc nền / dẫn đường lên cụm–HUD của nó vẫn chạy.
- **YouTube phát tiếp sai bài / không phát gì sau khi nổ máy**:
  - Nay lưu đúng bài đang nghe ngay khi đổi bài (trước chỉ lưu mỗi phút, đổi bài rồi tắt máy liền là lưu bài cũ).
  - Lúc nổ máy mà mạng chưa có, Kachi thử tìm lại bài trong tối đa 1 phút thay vì bỏ cuộc. Trong lúc đó các app khác vẫn mở bình thường, không phải chờ.
  - Chọn đúng bài trong 5 kết quả đầu: cùng tên thì ưu tiên đúng kênh.
  - Nếu anh em đã tự bật nhạc khác thì Kachi không phát đè.
- **Đổi hồ sơ mà mất dẫn đường lên cụm/HUD không biết vì sao**: nếu hồ sơ mới đang tắt "Dẫn đường lên cụm đồng hồ", Kachi hiện một dòng nhắc kèm chỗ bật lại (*Cài đặt › Dẫn đường + HUD*).

**Nhẹ hơn**
- Đổi hồ sơ không còn phải tắt rồi mở lại app trong ô ⇒ nhanh hơn vài giây, đỡ tải cho đầu xe.

**Nhờ anh em thử và gửi về** 🚗
1. Đang nghe YouTube trong ô, đổi hồ sơ qua lại 3 lần (hai hồ sơ đều có YouTube) ⇒ nhạc có ngắt không?
2. Nghe YouTube, chuyển sang bài khác, tắt máy ngay, nổ lại ⇒ có phát tiếp đúng bài vừa chuyển không?
3. Đổi sang hồ sơ đang tắt dẫn đường lên cụm ⇒ có thấy dòng nhắc không?

Gặp lỗi gì gửi lại thư mục `kachi-logs` như lần trước. Cập nhật tự động không lùi bản được, lỗi sẽ gom sửa ở bản sau.
