# Tin nhắn gửi anh em test Kachi 2.87

> **Trạng thái**: Session · **Cập nhật**: 2026-10-04 · **Chủ**: dangkhoi · **Mục đích**: tin nhắn ngắn gửi anh em sau khi 2.87 (188) lên kênh cập nhật tự động — kiêm nhật ký thay đổi (changelog) cho anh em. Owner 04/10: *"Xong hết ngon lành thì ota luôn nhé"* (không qua buổi xe, như 2.83–2.86). Nguồn nội dung: spec `docs/specs/kachi-i18n-zh-th-ms.html`, `docs/specs/kachi-287-look-and-keys.html`, `docs/specs/kachi-launcher-shortcuts-autostart.html` §4.Z. Chép nguyên phần dưới đường kẻ. Gửi khi commit đăng 2.87 đã lên `main`.

---

Chào anh em, Kachi **2.87** đã lên cập nhật tự động. Vào *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* để cài, rồi xem *Cài đặt › Giới thiệu* phải ra **Phiên bản 2.87 (mã 188)**.

**Sửa lỗi anh em báo trên 2.86**
- **Mở app khi nổ máy — chạy nền** và **tự mở nhạc khi lên xe bằng YouTube** trước đây im lặng không làm gì khi bố cục không có ô app nào. Giờ Kachi tự dựng chỗ chạy tạm, app chạy phía sau màn hình chính, không che màn.
  - YouTube: muốn tự phát thì dán **link** video/playlist vào ô *Phát gì*. Để trống thì Kachi chỉ mở YouTube, vì YouTube không có "phát tiếp" như YT Music.
  - Anh em đã chọn YouTube/YT Music thì Kachi mở luôn, kể cả khi xe đang tự phát lại nguồn nhạc cũ.
  - *Cài đặt › Hệ thống & quyền › Mở app khi nổ máy* giờ ghi **lý do từng app** của lần nổ máy gần nhất (vd "Waze: đã chạy nền", "YouTube: đã mở nhưng chưa phát — …"). Có lỗi thì chụp đúng chỗ này gửi về.
- **Widget không co giãn**: nút/số liệu/nhóm đặt vào khung giờ tự co giãn theo khung. Khung nhỏ thì thu lại, khung to thì giãn ra, các ô cùng cỡ và cách đều. Chữ không còn bị cắt nửa: chật quá thì xếp lại hàng/cột hoặc đặt icon cạnh chữ.
- **App trong ô bị tắt**: ô trả về trong suốt thấy hình nền, hết thẻ "App đã đóng — chạm để mở lại".
- **App đặt tạm vào ô widget** (vd mở app vào ô đang là widget lốp) rồi tắt thì ô **về lại widget lốp**, hết mảng đen.

**Mới**
- **Ngôn ngữ**: thêm **简体中文 · ไทย · Bahasa Melayu**, chọn ở *Cài đặt › Hiển thị & đơn vị › Ngôn ngữ*. Giọng nói vẫn **chỉ hiểu tiếng Việt**: danh sách câu lệnh luôn ghi câu tiếng Việt, ở mọi ngôn ngữ.
- **Độ trong suốt nền** (một mức chung cho thanh trên, thanh nút xe và widget): *Cài đặt › Hiển thị & đơn vị › Màu sắc › Độ trong suốt nền* — 0 % là như cũ, tối đa 60 %. Chữ và icon không mờ đi. Trên hình nền sáng, Kachi tự giữ nền đủ đục để chữ vẫn đọc được.
- **Nút ⇄ tự ẩn** (kể cả khung trống): chạm vào khung thì nút hiện, khoảng 3 giây sau ẩn. Chạm chỗ trống giữa các khung thì hiện nút của mọi khung. Muốn luôn hiện: tắt *Cài đặt › Màn hình chính › Tự ẩn nút ⇄*.
- **Nút ở đầu ô**: ô app có **[chạy nền] [⇄] [tắt]**, ô widget có **[⇄] [tắt]**.
  - *Chạy nền*: app ra sau màn hình chính và vẫn chạy (nhạc vẫn phát), ô trong suốt.
  - *Tắt*: **chạm 2 lần** (lần đầu nút hiện *"Chạm lần nữa để tắt"*, chạm lần nữa mới tắt) để khỏi bấm nhầm lúc đang lái.
  - Mọi thứ này chỉ là tạm. Lần nổ máy sau, Kachi vẫn mở theo bố cục của hồ sơ.
- **Lưới lối tắt app** trong widget tự co giãn: ít app thì icon to, nhiều app thì nhỏ lại, cùng cỡ, cách đều.
- **Gán phím — "Đảo" cho mọi nút**: nút bật/tắt và mở/đóng (kể cả **cốp**) có việc *Đảo*; nút nhiều mức có *Kế tiếp*. Một phím là đủ.
  - Nút Kachi không đọc được trạng thái (như cốp) thì đảo theo **lệnh cuối Kachi đã gửi**. Nếu cốp được mở/đóng bằng cách khác (chìa, nút ở cốp) thì lần bấm đầu có thể không ăn, bấm lại là được.
  - Đang chạy mà phím Đảo cốp ra hướng "mở" thì Kachi **đóng** — mở cốp vẫn chỉ chạy khi xe đứng yên.
- **Phân biệt núm âm lượng bệ giữa với nút âm lượng vô-lăng — bước đo**: màn *Học phím mới* hiện thêm một dòng chi tiết, có mục *nguồn*. Chưa gán riêng được ở bản này; cần ảnh của anh em trước (xem mục 4 bên dưới).

**Nhờ anh em thử trên xe (không cần máy tính)**
1. *Mở app khi nổ máy*: chọn một app (vd Waze) để **Chạy nền**, *Tự mở nhạc khi lên xe* = YouTube **có link** (ô *Phát gì*). Dùng bố cục **không có ô app** (toàn widget). Tắt xe, mở lại, chờ 1 phút. Xem app có chạy phía sau không, nhạc có phát không. Chụp *Cài đặt › Hệ thống & quyền › Mở app khi nổ máy* (dòng lần nổ máy gần nhất).
2. Ô đang chạy app (vd Google Maps): chạm vào ô → bấm **chạy nền** → app phải ra sau, màn hình chính không bị che. Mở lại bằng lối tắt hoặc ⇄. Thử thêm **tắt** (chạm 2 lần).
3. Đặt widget vài nút điều khiển vào một khung **thấp và rộng** (kiểu khung trong ảnh báo lỗi): chữ phải đọc được, không bị cắt nửa. Chụp lại.
4. **Núm âm lượng bệ giữa**: *Cài đặt › Phím vô-lăng › Học phím mới* → **vặn núm một nấc** → chụp dòng chi tiết dưới ô tên. Rồi *Học phím mới* lại → bấm **nút âm lượng trên vô-lăng** → chụp. Dòng *nguồn* ra "núm yên ngựa" / "vô-lăng" là bản sau gán riêng được; ra "không đọc được (…)" thì cũng chụp gửi.
5. Gán một phím cho **Đảo cốp** (xe đứng yên): bấm mở, bấm lại đóng.
6. Ai đọc được tiếng Trung, Thái hoặc Mã Lai: đổi ngôn ngữ, lướt *Cài đặt*, chỗ nào dịch kỳ thì chụp gửi.

**Báo lại**
- Chụp màn hình đúng lúc + **giờ** + việc vừa làm, ví dụ: "07:45, vừa mở xe, Waze không chạy nền, ảnh dòng Mở app khi nổ máy".
- Che biển số, tên đường, số nhà trong ảnh trước khi gửi.
- Không cần gõ lệnh gì.

Bản này đã thử kỹ trên máy ảo, **chưa chạy trên xe thật**. Cập nhật tự động không quay về bản cũ được; có lỗi thì mình ra bản 2.88. Cảm ơn anh em!
