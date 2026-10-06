# Tin nhắn gửi anh em test Kachi 2.91

> **Trạng thái**: Session · **Cập nhật**: 2026-10-06 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 2.91 (193) lên kênh cập nhật tự động — kiêm nhật ký thay đổi (changelog). Owner 06/10: *"làm tiếp dạy tên app bằng giọng đi"* · *"xử hết luôn đi nhé"* · *"ok xong thì OTA luôn nhé"*. Nguồn nội dung: spec `docs/specs/kachi-290-voice-app-names.html` (VOICE-APP-NAMES, đích 2.91), `docs/specs/kachi-291-small-fixes.html` (FIX-291-SMALL), nghiên cứu `docs/diagnostics/cluster-rect-adas-shrink-2026-10-06.md`. Chép nguyên phần dưới đường kẻ. Gửi khi commit đăng 2.91 đã lên `main`.

---

Chào anh em, Kachi **2.91** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **Phiên bản 2.91 (mã 193)**.

**Mới: dạy Kachi tên app bằng giọng của mình**
- App tên tiếng Anh hay khó đọc (Netflix, Spotify, quản lý tệp…) gọi bằng giọng hay bị không hiểu, vì Kachi chỉ nghe tiếng Việt. Giờ anh em tự dạy: *Cài đặt › Giọng nói › Dạy Kachi tên app*.
- Chọn app → bấm 🎤 → nói "mở <tên app>" như lúc lái, **2–3 lần**. Kachi lưu đúng chữ nó nghe được từ giọng mình (không lưu tiếng). Cũng có thể gõ tên gọi riêng, ví dụ "phim" cho Netflix.
- Lối tắt: nhấn giữ app trong ngăn app; hoặc khi nói "mở …" mà Kachi không hiểu, có nút "Dạy tên «…»".
- Kachi chặn tên trùng lệnh xe hay trùng app khác để khỏi mở nhầm. Tên ngắn 3 chữ cái (vd "Chrome" nghe ra «cơm») phải nói 2 lần ra giống nhau mới lưu. Tên dạy lưu theo hồ sơ.

**Sửa lỗi**
- **Công tắc bóng VietMap nói thật**: *Cài đặt › Dẫn đường & cụm đồng hồ › Hiện bong bóng VietMap trên cụm* giờ đúng với bóng đang hiện hay không (2.90 có lúc hiện "tắt" trong khi bóng vẫn hiện). Việc tự mở VietMap nền tách ra công tắc riêng *Tự mở VietMap cho bong bóng*. Không ai bị đổi gì sau khi cập nhật.
- **Mở lại app đang chạy nền (ô 7) vào khung khác cỡ**: hết viền đen; app giữ độ nét cũ để không bị dựng lại, nhạc chạy tiếp. Lần mở app mới sau đó mới về độ nét chuẩn của khung.
- **Bật/tắt màn hình nhanh ngay sau khởi động** không còn bỏ lỡ lượt Kachi kiểm VietMap (miễn tối ưu pin, quyền bóng).
- **Kiểu chiếu cụm Chữ nhật**: Cài đặt ghi rõ khung ADAS trắng bên phải do firmware xe vẽ cố định, không thu nhỏ được; muốn ADAS nhỏ thì chọn *Bo tròn* (phím menu vô-lăng thu ADAS ở kiểu đó).

**Nhờ anh em thử và chụp màn hình gửi về** 🚗
1. Dạy 2–3 app tên tiếng Anh bằng giọng mình, rồi nói "mở <tên>" khi đang ở màn nhà → có mở đúng app không? Chụp màn *Dạy tên app* kèm chữ Kachi nghe được.
2. Dạy tên qua phím voice trên vô-lăng ("Hey Kachi"/giữ phím) có chạy không?
3. Nói "mở …" một app chưa dạy → có hiện nút "Dạy tên «…»" không?
4. Công tắc *Hiện bong bóng VietMap trên cụm*: tắt / bật → bóng trên cụm ẩn / hiện theo không (cần bản VietMap sửa đổi v2)?
5. Đang phát nhạc trong khung → bấm *chạy nền* → mở lại app đó vào **khung khác cỡ** → nhạc còn chạy không, có viền đen không?

Bản này chưa chạy trên xe thật — gặp lỗi gì cứ chụp gửi về. Cập nhật tự động không lùi bản được, lỗi sẽ sửa ở bản sau.
