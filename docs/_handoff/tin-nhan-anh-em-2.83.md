# Tin nhắn gửi anh em test Kachi 2.83

> **Trạng thái**: Session · **Cập nhật**: 2026-09-29 tối (soát lại theo **bản đăng cuối** — `apk/Kachi-2.83-release.apk`, sha256 `b44691e6…2213`, dex chứa rào camera bản cuối [ĐO]: hai câu về camera khớp mã — nút sửa không tự về màn nhà khi màn camera của xe đang hiện, phím vẫn được sửa) · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 2.83 (184) **đã lên kênh cập nhật tự động (29/09)** (owner 14:05:56: *"xong chắc kiểm tra kỷ rồi OTA luôn cho anh em test nhỉ, mình ko có thời gian on-car test"*). Chép nguyên phần dưới đường kẻ. Nguồn nội dung: spec `docs/specs/kachi-283-key-heal-acc-off.html` §6.2. Gửi khi commit đăng 2.83 đã lên `main` (kênh OTA đọc `main`).

---

Chào anh em, Kachi **2.83** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **2.83 (mã 184)**.

**Bản này có gì**
- Phím vô-lăng **tự hồi phục sau khi tắt máy rồi mở lại** (trước đây hay "chết" sau một lần tắt máy).
- Nút *Kiểm tra / Sửa ngay* sửa xong **tự về màn hình chính**, không còn app nổi thành cửa sổ lẻ (trừ lúc đang hiện camera của xe, ví dụ đang lùi — khi đó Kachi không che camera, phím vẫn được sửa).
- Google Maps đang dẫn mà đổi đích (bằng giọng nói hoặc lịch) thì **dẫn thẳng đích mới**, hết hộp "Thoát chế độ đi theo chỉ dẫn?".
- Bỏ vạch chuẩn khoảng cách trên hình camera.

**Nhờ anh em thử trên xe (không cần máy tính)**
1. Tắt máy → chờ khoảng 1 phút → mở lại → trong 30 giây đầu bấm phím vô-lăng đã gán. Lặp vài lần trong ngày, nhất là buổi sáng. Lúc mở xe màn hình chính có thể tải lại một nhịp — bình thường.
2. Đang chạy mà phím chết: *Cài đặt › Phím vô-lăng › Kiểm tra / Sửa ngay*. Giao diện khởi động lại một nhịp rồi phải **tự về màn hình chính** (nếu lúc đó camera của xe đang hiện thì không tự về — đúng thiết kế).
3. Google Maps đang dẫn → nói một đích mới → xem có dẫn thẳng không.

**Báo lại**
- Chụp màn hình đúng lúc hỏng + **giờ** + việc vừa làm, ví dụ: "07:42, vừa mở xe, bấm phím không ăn".
- Che biển số, tên đường, số nhà trong ảnh trước khi gửi.
- Không cần gõ lệnh gì. Ai quen adb muốn gửi thêm log thì nhắn, mình gửi hướng dẫn riêng.

Bản này mới thử kỹ trên máy ảo, **chưa chạy trên xe thật** — nên mới nhờ anh em. Cập nhật tự động không quay về bản cũ được; có lỗi thì mình ra bản 2.84. Cảm ơn anh em!
