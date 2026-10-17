# Tin nhắn gửi anh em test Kachi 3.1.14

> **Trạng thái**: Session · **Cập nhật**: 2026-10-18 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn sau khi 3.1.14 (219) lên kênh cập nhật tự động — bản sửa lag (anh em SL6 báo 3.1.13 đơ khi nổ máy và cả sau đó). Nguồn: `docs/diagnostics/perf-sl6-3113-2026-10-17.md` (R1–R5, WAKE-WEAK-UNIT) + gỡ phần tên đường HUD (`HUD-ROADNAME-ALT-PATH`). Owner 17/10: *"ota luôn em"*. Chép nguyên phần dưới đường kẻ.

---

Chào anh em, Kachi **3.1.14** đã lên cập nhật tự động — bản này **sửa lag/đơ** anh em báo ở 3.1.13. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **Phiên bản 3.1.14 (mã 219)**.

**Đã sửa**
- **Widget xe 3D** không còn làm đơ màn hình: việc nạp xe 3D chạy ở luồng riêng; quay lại màn chính trong vòng 30 giây không phải nạp lại; lần nạp đầu đợi máy khởi động xong; nếu xe 3D không dựng được thì Kachi thôi thử (không còn thử lại mỗi lần nổ máy).
- **Hey Kachi** nhẹ hơn nhiều lúc nổ máy: chờ máy rảnh mới nạp giọng nói, chạy ở mức ưu tiên thấp nhất, và tự giảm tải khi đầu xe đang bận. Bấm nút mic thì vẫn nghe ngay.
- Bỏ phần thử **"Đường gửi tên đường lên HUD"** (A/A48/B/C) của bản trước — anh em SL6 thử không có tác dụng. Tên đường về như cũ; anh em bỏ qua mục SL6 trong tin nhắn 3.1.13.

**Nếu vẫn còn lag**
Mở *Kachi › Cài đặt › Hệ thống & quyền* → màn **Chẩn đoán**, để ở màn chính ít nhất 2 phút rồi **chụp màn Chẩn đoán gửi về** (có khối hiệu năng: đời xe, số nhân, RAM, dòng `wake:` và `KachiPerf … 3D-dựng`). Gửi kèm 2–3 tệp mới nhất trong `Android/data/com.byd.launcher/files/kachi-logs/` nếu được. Tạm thời có thể tắt Hey Kachi hoặc bỏ ô xe 3D khỏi màn chính.
