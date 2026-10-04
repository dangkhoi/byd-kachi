# Tin nhắn gửi anh em test Kachi 2.88

> **Trạng thái**: Session · **Cập nhật**: 2026-10-04 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 2.88 (189) lên kênh cập nhật tự động — kiêm nhật ký thay đổi (changelog). Owner 04/10: *"Ok, làm xong OTA rồi báo"* (không qua buổi xe, như 2.83–2.87). Nguồn nội dung: spec `docs/specs/kachi-288-key-source-split.html`, `docs/specs/kachi-288-tyre-car-state.html`, `docs/specs/kachi-287-look-and-keys.html` §4.1.2 · §4.6c. Chép nguyên phần dưới đường kẻ. Gửi khi commit đăng 2.88 đã lên `main`.

---

Chào anh em, Kachi **2.88** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **Phiên bản 2.88 (mã 189)**.

**Sửa lỗi anh em báo trên 2.87**
- **Tắt app trong khung mà app khác bị kéo vào** (vd đang chạy ChatGPT nền, tắt YouTube thì ChatGPT nhảy vào khung): giờ tắt app, cho app chạy nền hay app tự tắt thì khung **luôn trong suốt**. Chỉ khi khung đó vốn để **widget** thì widget hiện lại (như vụ widget lốp).
- **Độ trong suốt nền chỉ ăn thanh trên và thanh nút**: giờ nền khung chứa widget và nền các widget cũng trong theo.
- **Nút chạy nền trên khung (đen khung rồi app hiện lại)**: chưa tìm ra gốc trên xe. Từ bản này, nếu chạy nền không được, Kachi hiện câu *"… chưa chạy ngầm được (…)"* kèm lý do trong ngoặc. Gặp thì **chụp ngay câu đó** gửi về.

**Mới**
- **Độ trong suốt nền: thanh kéo 0–100 %** (thay 5 nấc cũ). *Cài đặt › Hiển thị & đơn vị › Màu sắc › Độ trong suốt nền* — kéo rồi thả tay là áp. 0 % là như cũ, 100 % là nền thanh trên, thanh nút xe và widget trong hoàn toàn. Chữ và icon giữ nguyên. Hình nền sáng mà kéo cao thì chữ có thể khó đọc — tuỳ anh em chọn mức vừa mắt.
- **Gán riêng núm âm lượng bệ giữa và nút âm lượng vô-lăng.** *Cài đặt › Phím vô-lăng › Học phím mới* → xoay núm (hoặc bấm nút vô-lăng) → hộp ghi *nguồn: núm yên ngựa* / *vô-lăng* → lưu. Nút lưu ra có đuôi nguồn, vd *"Núm lên (mã 291 · núm yên ngựa)"*. Gán nút đó cho việc khác (vd *Gió +1*) thì xoay núm đổi quạt, còn nút vô-lăng vẫn chỉnh âm lượng. Xe không đọc được nguồn thì phím chạy như cũ (âm lượng).
- **Chip áp suất lốp trên thanh trên**: icon bánh xe + 4 số theo thứ tự **trước-trái · trước-phải · sau-trái · sau-phải**, đơn vị theo cài đặt. Bật ở *Cài đặt › Thanh trạng thái & thanh nút › Chip trên thanh trạng thái › Áp suất lốp*.
  - Màu từng số **theo chính xe báo** (không theo ngưỡng của Kachi): **xanh** bình thường · **vàng** chú ý · **đỏ** cảnh báo · **xám** chưa đọc được.
  - Widget lốp và nhóm lốp cũng đổi sang màu theo xe báo. Lần đầu xe chưa báo kịp thì số hiện xám vài giây.

**Nhờ anh em thử trên xe (không cần máy tính)**
1. **Núm bệ giữa**: học *núm xoay lên* và *núm xoay xuống* (hộp phải ghi *núm yên ngựa*), lưu, gán vào *Gió +1* / *Gió −1*. Xoay núm: quạt phải đổi, **âm lượng không đổi**. Bấm âm lượng vô-lăng: **âm lượng đổi**, quạt không đổi. Rồi xoay núm một nấc và bấm vô-lăng ngay sau đó (dưới 1 giây), lặp 5 lần. Thử thêm xoay núm thật nhanh nhiều nấc.
2. **Chip lốp**: bật chip, so 4 số với màn áp suất lốp của xe (đúng bánh nào là bánh nấy không). Lốp bình thường phải ra **xanh**. Chụp lại cả ngày lẫn đêm. Nếu cả 4 số cứ **xám** mãi thì chụp gửi.
3. **Chạy nền trên khung YouTube**: nếu lại đen khung rồi YouTube hiện lại, chụp câu báo *"… chưa chạy ngầm được (…)"*. Không có câu báo mà app vẫn quay lại khung thì báo thêm giờ xảy ra.
4. **Khung lưu một app (vd ChatGPT), mở tạm app khác vào khung đó** (lối tắt hoặc giọng nói) rồi bấm *tắt*: khung phải trong suốt, ChatGPT không được nhảy vào.
5. **Thanh kéo độ trong suốt**: kéo 0 % / 50 % / 100 %, chụp màn chính mỗi mức.

**Báo lại**
- Chụp màn hình đúng lúc + **giờ** + việc vừa làm.
- Che biển số, tên đường, số nhà trong ảnh trước khi gửi.
- Không cần gõ lệnh gì.

Bản này đã thử kỹ trên máy ảo, **chưa chạy trên xe thật** (nhất là núm bệ giữa và màu chip lốp). Cập nhật tự động không quay về bản cũ được; có lỗi thì mình ra bản 2.89. Cảm ơn anh em!
