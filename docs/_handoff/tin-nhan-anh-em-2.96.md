# Tin nhắn gửi anh em test Kachi 2.96

> **Trạng thái**: Session · **Cập nhật**: 2026-10-08 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 2.96 (199) lên kênh cập nhật tự động — kiêm nhật ký thay đổi. Nguồn: spec `docs/specs/kachi-296-plan.html` (R1–R18). Chép nguyên phần dưới đường kẻ. Gửi khi commit đăng 2.96 đã lên `main`.

---

Chào anh em, Kachi **2.96** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **Phiên bản 2.96 (mã 199)**.

**Sửa lỗi**
- **Bấm HOME về launcher BYD** (firmware mới 2606): launcher BYD tự giành quyền HOME sau mỗi lần nổ máy. Nay Kachi tự lấy lại nếu anh em đã chọn Kachi làm màn chính (muốn dùng launcher khác thì bấm *Bỏ chọn* trong Kachi trước).
- **Chiếu cụm chia đôi lúc lên lúc không** (nửa phải VietMap không lên), **hai lớp bản đồ chồng nhau trên cụm**, lượt chiếu lúc nổ máy chậm/hết hạn — đã sửa gốc.
- **YouTube phát tiếp**: không còn "phải chạm mới hát" hay phát bài khác; mở lại đúng bài, đúng giây, **toàn màn hình**.
- **Nền quanh bản đồ trên cụm** đổi ngay khi xe chuyển Sáng/Tối (trước đây kẹt màu ngày tới lần chiếu sau).
- **Ảnh nền**: chép ảnh vào thư mục là tự lên, không cần ra/vào màn chính.

**Mượt hơn**
- Khởi động sau nổ máy nhanh hơn: bỏ chiếu cụm hai lần, bỏ chép lại model giọng nói mỗi lần, bớt lệnh thừa.
- Lúc tắt màn/standby Kachi gần như nghỉ hẳn (bớt đánh thức máy, bớt lệnh nền).

**Giọng nói**
- "Hey Kachi" **dễ gọi hơn** (bản nhạy đã chọn từ trước giờ mới thật sự lên xe).
- Kết thúc bằng: *bai bai, gút bai, tạm biệt, cảm ơn (Kachi), thôi được rồi, không cần, kết thúc, thoát…*
- Câu trả lời tự nhiên hơn: *"Đã đóng kính lái"*, *"Đã tắt sưởi ghế phụ"*, *"Đã đặt gió mức 3"*…

**Giao diện**
- Lề trên/dưới màn chính đều 13 px; icon thanh nút to hơn, khoảng cách đều.

**Nhờ anh em thử và gửi về** 🚗
1. Tắt máy, nổ lại vài lần: bấm HOME có về Kachi không? Cụm chia đôi có lên đủ hai nửa, không chồng lớp?
2. Xem YouTube vài phút, tắt máy, nổ lại ⇒ có tự phát tiếp đúng bài, toàn màn không?
3. Đang chiếu cụm, đổi Sáng/Tối trong Cài đặt xe ⇒ nền quanh bản đồ có đổi theo ngay không?
4. Nói "Hey Kachi" vài lần, xong việc nói "tạm biệt"/"cảm ơn" ⇒ có nghe và kết thúc không?

Gặp lỗi gì gửi lại thư mục `kachi-logs` như lần trước. Cập nhật tự động không lùi bản được, lỗi sẽ gom sửa ở 2.97.
