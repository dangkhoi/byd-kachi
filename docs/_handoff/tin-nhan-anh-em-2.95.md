# Tin nhắn gửi anh em test Kachi 2.95

> **Trạng thái**: Session · **Cập nhật**: 2026-10-07 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 2.95 (198) lên kênh cập nhật tự động — kiêm nhật ký thay đổi. Nguồn: spec `docs/specs/kachi-295-sl6-cluster-theme.html` (log SL6 07/10). Chép nguyên phần dưới đường kẻ. Gửi khi commit đăng 2.95 đã lên `main`.

---

Chào anh em, Kachi **2.95** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **Phiên bản 2.95 (mã 198)**.

**Sửa lỗi: chiếu bản đồ không lên cụm (SL6, cụm cong)**
- Trên SL6, bấm chiếu thì cụm vẫn hiện đồng hồ, bản đồ "biến mất". Nguyên nhân đã tìm ra từ log anh em gửi: từ 2.89 Kachi bỏ mất một lệnh bật chế độ chiếu của cụm khi cụm đã sẵn sàng từ lúc nổ máy. 2.95 gửi lại lệnh đó như thời ClusterNav (vẫn giữ các bước an toàn: chỉ gửi khi trên cụm không có app nào khác).
- Seal (cụm phẳng) không đổi gì.

**Nhờ anh em SL6 thử và gửi về** 🚗
1. Tắt máy, nổ lại, chiếu Google Maps / VietMap lên cụm ⇒ cụm có hiện bản đồ không? Thử cả Bo tròn và Chữ nhật.
2. Nếu vẫn không lên: gửi lại thư mục log `kachi-logs` như lần trước.

Mọi thứ của 2.94 (camera Thẳng rộng, nền cụm theo sáng/tối, YouTube phát tiếp) giữ nguyên.
