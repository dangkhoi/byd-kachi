# Kim Launcher — bản mô tả phong cách để làm đẹp widget Kachi (3.1 · R22)

> Owner 13/10: *"nhìn người ta làm launcher này em, đẹp, elegant quá trời, mình làm nó quê mùa lắm"* · *"có apk của nó nè, xem học theo cũng OK"*.
> Chỉ học PHONG CÁCH; không chép ảnh, logo, mô hình 3D hay nhận diện của Kim. Nhãn: [ĐO] = đo pixel/đọc tệp · [SUY] = ước lượng từ ảnh.
> Nguồn: ảnh trang kimlauncher.pro (banner 1600×900), APK `apk-ref/kim-launcher-1.0.2717.apk` + `1.0.203` (Flutter).

## 1. Ngôn ngữ hình ảnh của Kim
- **Nền**: ảnh chụp tràn màn (showroom/đường, bản ngày/đêm) + xe ở giữa [ĐO]. Thẻ dồn MỘT cột trái ~324 dp (~25 % màn); phần còn lại là "khoảng thở"; một thanh dock dạng viên thuốc ở giữa dưới.
- **Thẻ**: kính mờ (blur thật — `BackdropFilter`/`_GlassCard` trong chuỗi `libapp.so` [ĐO]). Nền tối #122130–#19222D, sáng #DDE9F5–#D9E3EB [ĐO]; bo ≈18 dp; viền tóc ~1 px trắng 10–15 %; không đổ bóng [SUY]. Lề 16 dp, khe giữa thẻ ≈10 dp, đệm trong 16 dp [SUY].
- **Đầu thẻ**: icon trong ô vuông bo 28 dp tô màu chính ~15 % + tiêu đề ≈14 sp medium màu mực đầy; phải là chip nhỏ dạng viên ("☀ 30°C") [SUY].
- **Chữ**: Roboto hệ thống (APK chỉ có font icon [ĐO]). Số lớn ≈50 sp nét LIGHT; đơn vị nhỏ bên dưới. Nhãn ≈13 sp regular màu lùi #9EAEBD; giá trị ≈13–14 sp medium, canh phải.
- **Màu**: Material 3 sinh từ MỘT màu gốc xanh (`ColorScheme.fromSeed` [ĐO]). Tối: primary #A2C9FE, surface #111622, surface-variant #19202C, mực lùi #9EAEBD. Sáng: primary #147ACE. Dữ liệu: thanh pin #6BAAF6, nhiên liệu #1DC75C, rãnh #293745.
- **Điểm nhấn** chỉ ở dữ liệu và lựa chọn (chấm số đang vào, nút phát, tab dock đang chọn, thanh tiến độ). KHÔNG có nền thẻ màu.
- **Icon**: nét mảnh (Material outlined), 16–18 dp, đơn sắc mực.
- **Dữ liệu xe**: vòng tốc độ ≈136 dp nét 2 dp + viên P R N D; hai thanh mảnh 3 dp bo tròn cho pin/nhiên liệu ("nhãn trái · số phải"); lốp = danh sách chữ 2×2. Giá trị chỉ-đọc KHÔNG có ô chứa riêng.
- **Mật độ thấp**, lưới hai cột nhãn/giá trị, nhịp hàng ≈8 dp. Trống dữ liệu ⇒ một chỗ giữ chỗ mờ ("--:--"), không lưới gạch.

## 2. 8 lý do Kachi trông "quê" + cách sửa bằng token (ưu tiên theo tác động)
1. **Không có không khí**: nền xanh đậm phẳng #131A2D ⇒ như biểu mẫu. Sửa: ảnh/nền trừu tượng mặc định do mình sở hữu + lớp tối dần, thẻ trong ≈0,62.
2. **Hộp trong hộp**: mỗi giá trị chỉ-đọc nằm trong ô tô nền riêng (Trip 3 ô con, Khí hậu 12 ô, Kính). Sửa: giá trị chỉ-đọc là HÀNG trên thẻ (nhãn đầu · số cuối, khe 8 dp); ô chứa chỉ cho thứ bấm được.
3. **Thẻ tô màu theo miền** (Khí hậu xanh két, Đèn nâu, Trip xanh két) ⇒ đục, lệch tông. Sửa: một mặt thẻ trung tính; màu miền dồn vào ô icon đầu thẻ.
4. **Đậm khắp nơi** (`DEFAULT_BOLD` ở `ReadTile.kt:123`, `WidgetViews.kt:324`, `GroupTileViews.kt:495`). Sửa: ba độ đậm — số lớn light 44–52 sp + `tnum`; giá trị medium 14–16 sp; nhãn regular 13 sp lùi; đơn vị 0,55×.
5. **Đầu thẻ yếu** (tiêu đề 11 sp xám). Sửa: ô icon 28 dp bo 8 + tiêu đề 14–15 sp medium + chip trạng thái tuỳ chọn.
6. **Gạch giữ chỗ** lặp 12 lần ⇒ trông hỏng. Sửa: ẩn hàng không có dữ liệu; cả thẻ trống ⇒ một dòng lùi.
7. **Điểm nhấn chói** (ô bật chàm bão hoà, gradient tím/hồng/hổ phách `tile_*`). Sửa: một dải tông M3 từ màu thương hiệu; màu thứ hai chỉ từ ngữ nghĩa dữ liệu (xanh/hổ phách/đỏ).
8. **Nhịp và trực quan hoá**: thanh trên nhồi chữ (PM2.5, nhiệt ngoài, ghế). Sửa: token 16/10/16/8; thanh trên chỉ giờ + icon; % (pin, kính) thành thanh 3 dp trên rãnh.

## 3. Ràng buộc chi phí (đầu xe API 29)
- Blur sống (`BackdropFilter`) = thêm một lượt vẽ mỗi khung; DiLink API 29 không có, `KachiGlassMode` đã chặn < 31. Tương đương rẻ: blur ảnh nền MỘT lần khi đặt (thu ×8 + stack blur, cache), thẻ vẽ ảnh đã blur qua `BitmapShader` theo toạ độ màn, cắt theo khung bo, phủ màu ≈55 %. 0 chi phí mỗi khung.
- Xe 3D (WebView + `.glb`) và shader đường động: đắt ⇒ chỉ ảnh tĩnh của mình, không chuyển động.
- Viền tóc 1 px rẻ nhưng owner từng bỏ viền (20/09) ⇒ không dùng, hoặc ≤ 8 % nếu owner đồng ý. Không đổ bóng.
- ⚠ Mục 8 "neo nội dung lên trên" của bản gốc MÂU THUẪN yêu cầu owner 13/10 "nằm chơi vơi" (đã canh giữa dọc ở W2) ⇒ giữ canh giữa, chống khoảng trống bằng cỡ thẻ/mật độ chứ không đẩy nội dung lên.

## 4. Bổ sung sau khi đọc tài nguyên APK (13/10)
- [SUY] ~60–70 % cảm giác "elegant" đến từ lớp NỀN: phòng trưng bày tối giản (đá, đèn hắt, bệ tròn) hoặc đường lái, bản ngày/đêm, xe đặt lên trên (mô hình 3D `.glb` hoặc ảnh xe theo đời — ảnh quảng cáo BYD, có bản quyền ⇒ không dùng). Widget chỉ chiếm cột trái ~25 % + dock.
- [ĐO mã Kachi] Kachi ĐÃ có bản rẻ của kính Kim: `WallWindowDrawable` (`WallGlass.kt`) vẽ vùng ảnh nền đã blur sẵn dưới mỗi thẻ qua `BitmapShader`, lớp che theo độ chói đo được (`GlassVeil`), blur tính MỘT lần + cache (`WallArtBuilder`, `WallpaperController.kt:143`).
- **Lỗ hổng chính**: ảnh nền chỉ lấy từ thư mục người dùng tự chép (`WallpaperStore.FOLDER`) — KHÔNG có nền mặc định ⇒ đa số thấy nền xanh phẳng #131A2D. Hướng: nền mặc định của chính mình (dựng bằng mã hoặc ảnh có giấy phép sạch), bản tối + sáng, giữa/phải yên, dưới tối dần; độ trong thẻ trên nền ≈0,6–0,65 (hiện 0,8).
