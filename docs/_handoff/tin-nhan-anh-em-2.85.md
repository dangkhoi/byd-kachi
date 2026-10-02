# Tin nhắn gửi anh em test Kachi 2.85

> **Trạng thái**: Session · **Cập nhật**: 2026-10-02 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 2.85 (186) lên kênh cập nhật tự động. Owner 02/10: *"Xong thì OTA luôn"* · *"Bản đủ"* (không qua buổi xe, như 2.83/2.84). Chép nguyên phần dưới đường kẻ. Nguồn nội dung: spec `docs/specs/kachi-launcher-shortcuts-autostart.html` (lối tắt · mở app khi nổ máy · tự mở nhạc), `docs/specs/kachi-ready-at-home.html`, `docs/specs/kachi-profile-switch-slots.html`, `docs/specs/kachi-profiles-are-everything.html` §12. Gửi khi commit đăng 2.85 đã lên `main`.

---

Chào anh em, Kachi **2.85** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **2.85 (mã 186)**.

**Bản này có gì**
- **Mở xe là màn chính dùng được ngay.** Xe đã từng cho phép Kachi thì Kachi tự nối từ lúc khởi động, phím vô-lăng được kiểm và sửa trước khi ô app mở. Xe chưa cho phép thì màn chính hiện thẻ *"Kachi cần quyền điều khiển cửa sổ"*: chạm *Hỏi lại*, khi hộp *"Cho phép gỡ lỗi USB?"* hiện thì tích *"Luôn cho phép từ máy tính này"* rồi bấm OK.
- **Lối tắt app trên thanh nút.** *Cài đặt › Thanh trạng thái & thanh nút › Lối tắt ứng dụng*: chọn tối đa 8 app, mỗi app một kiểu mở:
  - *Ô 1, Ô 2…*: đặt tạm app vào ô đó. App đang ở ô lùi ra sau màn chính, không bị tắt. Tắt/mở xe hoặc đổi hồ sơ là ô về bố cục đã lưu.
  - *Toàn màn*: mở cả màn hình. Nếu app đang nằm trong một ô thì bấm Home là nó về lại ô.
  - *Chạy ngầm*: mở app phía sau màn chính, không che gì (cần ít nhất một ô app đang mở).
  - Muốn thấy icon trên thanh nút thì bật khối *Lối tắt ứng dụng* ở mục *Chọn nút trên thanh…* ngay phía trên. Hoặc đặt widget *Lưới lối tắt app* vào một ô.
- **Mở app khi nổ máy.** *Cài đặt › Hệ thống & quyền › Mở app khi nổ máy*: chọn tối đa 6 app, mỗi app *Chạy nền* hoặc *Mở bình thường* (chỉ 1 app). Chạy một lần mỗi lần nổ máy, khoảng 15–45 giây sau khi màn chính sẵn sàng. Ngay dưới có dòng *Lần nổ máy gần nhất* cho biết đã mở gì.
- **Tự mở nhạc khi lên xe.** *Cài đặt › Giọng nói › Tự mở nhạc khi lên xe*: Tắt · Theo player của xe · YouTube Music · YouTube. Chọn YouTube / YouTube Music thì có thêm ô *Phát gì* (từ khoá hoặc link YouTube; để trống là phát tiếp bài cũ, nhưng tắt máy xong app hay bị đóng nên đặt từ khoá cho chắc). App nhạc đang nằm trong một ô thì phát ở ô đó, không thì phát phía sau màn chính. Đang có nguồn khác phát thì Kachi không đè.
- **Xuất hồ sơ để chia sẻ.** Có hai kiểu xuất: *đầy đủ (sao lưu)* và *để chia sẻ*. Bản chia sẻ không kèm sổ địa chỉ và lịch dẫn đường. Mỗi lần xuất ra một tệp mới, không ghi đè tệp cũ. Nhập thì chọn đúng một tệp trong danh sách.
- **Đổi hồ sơ không còn ô đen.** Hai hồ sơ đặt thanh nút ở hai cạnh khác nhau (hoặc một bên ẩn thanh nút) đổi qua lại thì ô app vẫn hiện. App của hồ sơ cũ không còn sót lại thành cửa sổ nổi.

**Nhờ anh em thử trên xe (không cần máy tính)**
1. Tắt xe, chờ vài phút rồi mở lại: lên màn chính là ô app hiện, bấm phím vô-lăng ăn ngay. Thấy thẻ xin quyền thì làm như trên rồi chụp lại.
2. Lối tắt: chọn 3 app, mỗi app một kiểu (Ô 1 · Toàn màn · Chạy ngầm), chạm từng cái. Với Ô 1: mở lại app cũ của ô, xem nó còn nguyên chỗ đang dùng hay phải tải lại từ đầu. Với Toàn màn: thử thêm một app đang nằm trong ô, chạm cho nó ra toàn màn rồi bấm Home, app có về lại ô mà không tải lại không.
3. Mở app khi nổ máy: chọn 1–2 app *Chạy nền*, tắt rồi mở xe. Màn chính không bị che, mở app đó lên thấy đã chạy sẵn. Chụp dòng *Lần nổ máy gần nhất*. Nếu được, thử vào số lùi ngay sau khi mở xe: màn camera không bị app nào che.
4. Tự mở nhạc: chọn YouTube hoặc YouTube Music, đặt một từ khoá ở ô *Phát gì*, tắt rồi mở xe. Có tiếng mà màn chính không bị che. Không có tiếng thì chụp dòng *Lần nổ máy gần nhất* (ở mục *Mở app khi nổ máy*).
5. Xuất hồ sơ *để chia sẻ*, gửi cho người khác nhập thử.
6. Hai hồ sơ, thanh nút ở hai cạnh khác nhau: đổi qua lại vài lần, ô app không đen, không có cửa sổ nổi sót lại.

**Báo lại**
- Chụp màn hình đúng lúc + **giờ** + việc vừa làm, ví dụ: "07:45, vừa mở xe, chọn YouTube Music, không có tiếng".
- Che biển số, tên đường, số nhà trong ảnh trước khi gửi.
- Không cần gõ lệnh gì.

Bản này mới thử kỹ trên máy ảo, **chưa chạy trên xe thật**. Cập nhật tự động không quay về bản cũ được; có lỗi thì mình ra bản 2.86. Cảm ơn anh em!
