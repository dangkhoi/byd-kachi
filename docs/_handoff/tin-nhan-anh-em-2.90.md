# Tin nhắn gửi anh em test Kachi 2.90

> **Trạng thái**: Session · **Cập nhật**: 2026-10-06 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 2.90 (192) lên kênh cập nhật tự động — kiêm nhật ký thay đổi (changelog). Nguồn nội dung: spec `docs/specs/kachi-290-cluster-rect-fix.html` (R1–R10, Pass 4), chẩn đoán buổi xe `docs/diagnostics/oncar-2026-10-06-cluster-rect.md`. Chép nguyên phần dưới đường kẻ. Gửi khi commit đăng 2.90 đã lên `main` (trạng thái `REL-2.90` chốt sau khi đăng).

---

Chào anh em, Kachi **2.90** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **Phiên bản 2.90 (mã 192)**.

**Sửa lỗi**
- **Chọn *Chữ nhật* mà cụm vẫn cong** (xe Seal): Kachi chỉ đổi kiểu cụm khi cụm trống. Giờ trước khi đổi, Kachi tự gỡ các lớp của chính nó trên cụm (biển tốc độ…) và nhờ bản VietMap sửa đổi tạm ẩn bóng, rồi mới đổi. Đổi xong thì tự hiện lại.
- **Khung chiếu Chữ nhật**: dùng trọn 1920×720, Kachi không tự chừa chỗ nữa. Anh em tự chỉnh vị trí, kích thước, DPI theo ý.
- **Bỏ số km/h Kachi tự vẽ** ở kiểu Chữ nhật.
- ***Cài đặt › Chiếu cụm* đứng ở "Đang mở cụm…"**: giờ trạng thái tự cập nhật ngay trên trang.
- **Biển giới hạn tốc độ không hiện** (sáng 06/10): gốc là widget VietMap mất liên kết. Giờ Kachi tự liên kết lại widget và tự tìm đúng màn hình cụm.
- **Công tắc *Hiện bong bóng VietMap trên cụm*** (*Cài đặt › Dẫn đường & cụm đồng hồ*): TẮT giờ ẩn thật bóng trên cụm, BẬT thì hiện lại. **Cần bản VietMap sửa đổi v2** — bản cũ không làm theo lệnh ẩn. Ai chưa từng chạm công tắc thì bóng vẫn hiện như trước. Muốn ẩn mà công tắc đang tắt sẵn: bật lên rồi tắt lại.

**Nhờ anh em thử và chụp màn hình gửi về** 🚗
1. Xe Seal, đang có bóng VietMap trên cụm: chọn *Chữ nhật* → *Áp ngay* (hoặc tắt máy rồi nổ lại) → cụm có chuyển sang chữ nhật không? Bóng và biển tốc độ có hiện lại sau đó không?
2. Chữ nhật: chụp cụm — khung có trọn ngang không, chỉnh vị trí/kích thước/DPI có ăn không?
3. *Cài đặt › Chiếu cụm* khi vừa bật chiếu: chữ có tự đổi từ "Đang mở cụm…" sang trạng thái đang chiếu không?
4. Dẫn đường trên VietMap: biển giới hạn tốc độ có hiện trên cụm không (kể cả sau khi khởi động lại đầu xe)?
5. Có bản VietMap v2: tắt / bật công tắc bóng → bóng trên cụm có ẩn / hiện theo không? Đổi hồ sơ có giữ đúng lựa chọn không?
6. Nếu *Cài đặt › Chiếu cụm* báo "bản mod VietMap cũ chưa hỗ trợ ẩn bóng" → chụp lại gửi về.

Bản này chưa chạy trên xe thật — gặp lỗi gì cứ chụp gửi về. Cập nhật tự động không lùi bản được, lỗi sẽ sửa ở bản sau.
