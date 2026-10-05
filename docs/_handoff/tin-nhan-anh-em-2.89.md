# Tin nhắn gửi anh em test Kachi 2.89

> **Trạng thái**: Session · **Cập nhật**: 2026-10-06 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 2.89 (191) lên kênh cập nhật tự động — kiêm nhật ký thay đổi (changelog). Owner 05/10: *"off-car rồi, làm hết đi rồi OTA test sau em ơi"* (không qua buổi xe, như 2.83–2.88). Nguồn nội dung: spec `docs/specs/kachi-289-field-fixes.html`, `docs/specs/kachi-287-look-and-keys.html` §4.6d, số đo xe `docs/diagnostics/oncar-2026-10-05-slot-cluster.md`. Chép nguyên phần dưới đường kẻ. Gửi khi commit đăng 2.89 đã lên `main`.

---

Chào anh em, Kachi **2.89** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **Phiên bản 2.89 (mã 191)**.

**Sửa lỗi anh em báo trên 2.88**
- **Nút chạy nền trên khung (khung đen rồi app hiện lại, mất nhạc)**: tìm ra gốc trên xe — hệ thống BYD từ chối cách cũ và mỗi lần đổi màn hình là app bị dựng lại. Giờ app chạy nền được cất vào một **"ô 7" ẩn**: app không bị dựng lại, nhạc YouTube vẫn phát. Mở lại app đó vào khung thì hiện ngay, giữ nguyên chỗ đang xem.
- **Đưa app khác vào khung / tắt app thì app cũ lòi lên**: hết. Tắt app ⇒ khung trong suốt.
- **Tắt app phải bấm ✕ hai lần, báo "chưa tắt được"**: xe tắt app chậm hơn Kachi tưởng. Giờ khung ẩn ngay khi bấm và Kachi chờ đủ lâu, không báo nhầm.
- **Mở app đang chạy nền vào khung thì app khởi động lại**: giờ giữ nguyên app, nhạc không bị ngắt.
- **Bo góc khung**: đủ cả 4 góc, không còn góc vuông hay chấm đen.
- **Nhạc khi lên xe không phát, màn đen khi YouTube đang nằm ở khung**: giờ nếu app nhạc nằm sẵn trong khung thì Kachi phát nhạc luôn trong khung đó. *Cài đặt › Hệ thống & quyền* ghi rõ lần nổ máy gần nhất đã chờ gì và phát ở đâu.
- **Khung số 1 đen sau khi khởi động nguội đầu xe** (VietMap bật toàn màn hình thay vì vào khung): đã sửa.
- **Màn chính có lúc tự khởi động lại khi bật chiếu cụm**: Kachi chỉ gửi lệnh đổi kiểu cụm khi cụm chưa chiếu gì.
- **VietMap hiện "Hệ thống IVI không hỗ trợ hoạt động này" mỗi lần mở** (hay gặp sau khi cài lại VietMap): đó là VietMap xin "bỏ tối ưu pin" mà màn đó bị BYD khoá. Giờ Kachi tự kiểm và thêm VietMap vào danh sách miễn tối ưu pin mỗi lần (cần đã cấp quyền gỡ lỗi cho Kachi).
- **Bóng VietMap không lên khi VietMap đứng lâu ở màn chờ** (mạng chậm): Kachi chờ VietMap vào bản đồ rồi mới thu xuống.

**Mới**
- **Cỡ thanh nút xe 50–150 %**: *Cài đặt › Hiển thị & đơn vị › Thanh nút xe › Cỡ*. Kéo để xem trước, thả tay mới áp. Khung, widget và icon tự co giãn theo. 100 % là cỡ cũ.
- **Kiểu chiếu cụm: Bo tròn / Chữ nhật** (xe Seal): *Cài đặt › Chiếu cụm › Kiểu chiếu cụm*. Chữ nhật cho bản đồ thẳng trọn ngang. Cụm mất số km/h gốc nên Kachi tự vẽ km/h ở góc dưới trái (không vẽ khi chiếu CarPlay/Android Auto). Khung ADAS trắng bên phải là của cụm, không bỏ được. Kiểu mới áp từ lần nổ máy sau. Mặc định vẫn là Bo tròn như cũ.
- **"Tự mở nhạc khi lên xe"** chuyển sang *Cài đặt › Hệ thống & quyền*, ngay dưới *Mở app khi nổ máy*.

**Nhờ anh em thử và chụp màn hình gửi về**
1. Đang phát YouTube trong khung → bấm *chạy nền* → nhạc còn không? Mở lại YouTube vào khung → có bị khởi động lại không?
2. Tắt app trên khung (✕ hai chạm) → khung trong suốt, không báo "chưa tắt được".
3. Khởi động lại đầu xe → khung số 1 có lên app không?
4. Bật / tắt chiếu cụm vài lần → màn chính có tự khởi động lại không?
5. Xe Seal: chọn *Chữ nhật*, tắt máy rồi nổ lại → chụp cụm (km/h có hiện ở góc dưới trái không).
6. VietMap: còn hiện "Hệ thống IVI không hỗ trợ…" không?
7. Kéo cỡ thanh nút xe về 50 % và 150 % → có gì bị cắt, chồng lên nhau không?

Bản này chưa chạy trên xe thật — gặp lỗi gì cứ chụp gửi về. Cập nhật tự động không lùi bản được, lỗi sẽ sửa ở 2.90.
