# Bảng kiểm tra từng chức năng xe — Kachi

- **Ngày:** 2026-09-15 · **Chủ:** dangkhoi · **Nguồn:** sinh tự động từ `TelemetryRegistry` (123 thông tin) + `ControlRegistry` (64 hành động) + `CapabilityDescriptions` (diễn giải). KHÔNG chép tay — thêm nút mới thì tự có mặt ở đây (và trong công cụ trong app).
- **Công cụ trong app:** *Cài đặt › Hệ thống & quyền › Nâng cao › Kiểm tra từng nút xe*. Bấm **Bắt đầu / Tiếp tục** để đi tuần tự từng mục: mục **hành động** hiện nút **Chạy** rồi tự chấm **OK / Không OK**; mục **thông tin** hiện **giá trị đọc được**. Kết quả lưu trên xe, xuất lại bằng nút **Xuất báo cáo** (bản này luôn tươi theo lần soát mới nhất).
- **Log trên xe → THẺ NHỚ (1.58, owner 2026-09-15):** app ghi log ra `/sdcard/Android/data/com.byd.launcher/files/kachi-logs/` (external ⇒ nhẹ bộ nhớ trong đầu xe; `DiagStorageCap` tự dọn không cho phình). Ba tệp:
  - `captest-report.txt` — kết quả kiểm tra từng nút, **tự lưu mỗi lần chấm** OK/Không OK (và khi bấm *Xuất báo cáo*).
  - `usage-<ts>.log` — logcat của chính app, chạy nền suốt phiên (flush từng dòng ⇒ sống sót cả khi app chết).
  - `snapshot-<ts>.log` — chụp một phát toàn bộ logcat gần đây (gồm hệ thống) khi bấm *Chụp log ngay*.
- **Lấy log về (không cần root):** `adb pull /sdcard/Android/data/com.byd.launcher/files/kachi-logs/ ./kachi-logs/`
- **Ký hiệu:** `(tin)` = thông tin chỉ đọc · `(nút)` = hành động có thể chạy · `[ ]` chưa soát · `[OK]` đạt · `[X]` không đạt.
- **Trạng thái test:** tất cả đang **chưa soát** — cập nhật bằng công cụ trong app khi lên xe. Bảng dưới là ảnh chụp lúc chưa soát; nguồn sự thật là nhật ký trong app.

---

```
KIỂM TRA TỪNG NÚT — Kachi
OK 0 · Không OK 0 · Chưa soát 187 / 187

── Năng lượng & sạc ──
[ ]  (tin) Pin (SOC) — Phần trăm pin cao áp còn lại
[ ]  (tin) Tầm hoạt động EV — Quãng đường ước tính còn chạy bằng điện
[ ]  (tin) Tầm hoạt động xăng — Quãng đường ước tính còn chạy bằng xăng
[ ]  (tin) Mức xăng — Phần trăm nhiên liệu xăng còn lại
[ ]  (tin) Odo tổng — Tổng quãng đường xe đã đi
[ ]  (tin) Km chạy điện — Quãng đường đã chạy bằng điện
[ ]  (tin) Quãng đường chuyến — Quãng đường của chuyến đi hiện tại
[ ]  (tin) Thời gian chuyến — Thời gian đã đi của chuyến hiện tại
[ ]  (tin) Điện tiêu thụ chuyến — Điện năng đã tiêu thụ trong chuyến
[ ]  (tin) Tiêu thụ 50km — Mức tiêu thụ điện trung bình 50km gần nhất
[ ]  (tin) Công suất mô-tơ — Công suất mô-tơ điện đang phát ra
[ ]  (tin) Đang sạc — Xe có đang được sạc hay không
[ ]  (tin) Công suất sạc — Công suất sạc đang nhận vào pin
[ ]  (tin) Sạc % — Phần trăm pin đã sạc trong phiên sạc
[ ]  (tin) Còn (giờ) — Số giờ còn lại để sạc đầy
[ ]  (tin) Còn (phút) — Số phút còn lại để sạc đầy
[ ]  (tin) Đã sạc phiên — Điện năng đã nạp trong phiên sạc này
[ ]  (tin) Trạng thái sạc — Trạng thái hiện tại của quá trình sạc
[ ]  (tin) Trạng thái bộ sạc — Trạng thái hoạt động của bộ sạc trên xe
[ ]  (tin) Nhiệt độ pin — Nhiệt độ hiện tại của pin cao áp
[ ]  (tin) Nhiệt cell cao — Nhiệt độ cell pin cao nhất
[ ]  (tin) Nhiệt cell thấp — Nhiệt độ cell pin thấp nhất
[ ]  (tin) Nhiệt cell TB — Nhiệt độ trung bình các cell pin
[ ]  (tin) Áp cell cao — Điện áp cell pin cao nhất
[ ]  (tin) Áp cell thấp — Điện áp cell pin thấp nhất
[ ]  (tin) Sức khoẻ pin (SOH) — Tình trạng sức khoẻ pin so với lúc mới
[ ]  (tin) Mục tiêu sạc — Mức pin mục tiêu đã đặt cho sạc
[ ]  (tin) Tầm pin (thân xe) — Tầm hoạt động pin theo tính toán thân xe
[ ]  (nút) Mục tiêu sạc — Tăng/giảm mức pin mục tiêu cho sạc
[ ]  (nút) Giới hạn sạc — Bật/tắt giới hạn mức sạc tối đa
[ ]  (nút) Sạc không dây — Bật/tắt sạc không dây cho điện thoại
[ ]  (nút) Sạc ngay — Bắt đầu sạc xe ngay lập tức
── Động lực & tốc độ ──
[ ]  (tin) Tốc độ — Tốc độ di chuyển hiện tại của xe
[ ]  (tin) Chân ga — Độ nhấn bàn đạp ga hiện tại
[ ]  (tin) Chân phanh — Độ nhấn bàn đạp phanh hiện tại
[ ]  (tin) Vòng tua mô-tơ trước — Tốc độ quay mô-tơ điện trục trước
[ ]  (tin) Vòng tua mô-tơ sau — Tốc độ quay mô-tơ điện trục sau
[ ]  (tin) Mô-men mô-tơ trước — Mô-men xoắn mô-tơ điện trục trước
[ ]  (tin) Vòng tua máy xăng — Tốc độ quay động cơ xăng
[ ]  (tin) Góc vô-lăng — Góc xoay hiện tại của vô-lăng
[ ]  (tin) Tốc độ bánh — Tốc độ quay của bánh xe
[ ]  (tin) Độ dốc — Độ dốc mặt đường xe đang đi
[ ]  (tin) Số — Số hiện tại đang cài đặt (P/R/N/D)
[ ]  (tin) Chế độ lái — Chế độ lái đang được kích hoạt
[ ]  (tin) Chế độ năng lượng — Chế độ vận hành năng lượng đang dùng (EV/HEV)
[ ]  (tin) Chế độ drift — Chế độ drift có đang bật hay không
[ ]  (nút) Chế độ lái — Chọn chế độ lái (Eco/Normal/Sport…)
[ ]  (nút) EV / HEV — Chọn chế độ vận hành động cơ (EV/HEV)
[ ]  (nút) Mức tái tạo — Chọn mức thu hồi năng lượng phanh tái tạo
[ ]  (nút) iTAC (kiểm soát mô-men) — Bật/tắt hệ thống kiểm soát mô-men iTAC
[ ]  (nút) Giữ phanh tự động (AVH) — Bật/tắt chức năng giữ phanh tự động khi dừng
── Khí hậu & không khí ──
[ ]  (tin) Mức bụi mịn — Mức đánh giá chất lượng không khí trong xe
[ ]  (tin) Bụi mịn PM2.5 — Nồng độ bụi mịn PM2.5 trong cabin
[ ]  (tin) Cảm biến bụi mịn — Cảm biến bụi mịn có đang hoạt động không
[ ]  (tin) Nhiệt trong cabin — Nhiệt độ thực tế đo trong khoang cabin
[ ]  (tin) Nhiệt cài đặt — Nhiệt độ điều hoà đã cài đặt
[ ]  (tin) Nhiệt ngoài xe — Nhiệt độ không khí bên ngoài xe
[ ]  (tin) Nhiệt nước làm mát — Nhiệt độ nước làm mát động cơ
[ ]  (tin) Điều hoà — Điều hoà có đang bật hay không
[ ]  (tin) Mức quạt gió — Mức quạt gió điều hoà đang chạy
[ ]  (tin) Chế độ lấy gió — Chế độ lấy gió trong/ngoài đang dùng
[ ]  (tin) Đơn vị nhiệt — Đơn vị hiển thị nhiệt độ đang dùng (°C/°F)
[ ]  (tin) Ion âm — Chức năng ion âm có đang bật không
[ ]  (nút) Lọc bụi — Bật/tắt chế độ lọc bụi mịn tự động
[ ]  (nút) Ghế mát — Bật/tắt quạt làm mát ghế
[ ]  (nút) Nhiệt độ — Tăng/giảm nhiệt độ điều hoà
[ ]  (nút) Gió — Tăng/giảm mức quạt gió điều hoà
[ ]  (nút) Sấy kính — Bật/tắt sấy kính chắn gió trước
[ ]  (nút) Ghế sưởi — Bật/tắt sưởi ghế
[ ]  (nút) Lấy gió trong — Bật/tắt chế độ lấy gió trong xe
[ ]  (nút) Điều hoà AUTO — Bật/tắt chế độ điều hoà tự động (AUTO)
[ ]  (nút) Sấy kính sau — Bật/tắt sấy kính chắn gió sau
[ ]  (nút) Ion âm — Bật/tắt chức năng ion âm lọc không khí
[ ]  (nút) Sưởi vô-lăng — Bật/tắt sưởi vô-lăng
[ ]  (nút) Lọc ngay — Chạy lọc không khí một lần ngay
── Lốp ──
[ ]  (tin) Áp lốp trước-trái — Áp suất lốp trước bên trái
[ ]  (tin) Áp lốp trước-phải — Áp suất lốp trước bên phải
[ ]  (tin) Áp lốp sau-trái — Áp suất lốp sau bên trái
[ ]  (tin) Áp lốp sau-phải — Áp suất lốp sau bên phải
[ ]  (tin) Nhiệt lốp trước-trái — Nhiệt độ lốp trước bên trái
[ ]  (tin) Nhiệt lốp trước-phải — Nhiệt độ lốp trước bên phải
[ ]  (tin) Nhiệt lốp sau-trái — Nhiệt độ lốp sau bên trái
[ ]  (tin) Nhiệt lốp sau-phải — Nhiệt độ lốp sau bên phải
── Thân xe · cửa · kính ──
[ ]  (tin) Kính trước-trái — Độ mở kính cửa trước bên trái
[ ]  (tin) Kính trước-phải — Độ mở kính cửa trước bên phải
[ ]  (tin) Kính sau-trái — Độ mở kính cửa sau bên trái
[ ]  (tin) Kính sau-phải — Độ mở kính cửa sau bên phải
[ ]  (tin) Cửa trước-trái — Trạng thái đóng/mở cửa trước bên trái
[ ]  (tin) Cửa trước-phải — Trạng thái đóng/mở cửa trước bên phải
[ ]  (tin) Cửa sau-trái — Trạng thái đóng/mở cửa sau bên trái
[ ]  (tin) Cửa sau-phải — Trạng thái đóng/mở cửa sau bên phải
[ ]  (tin) Cốp sau — Trạng thái đóng/mở cốp sau
[ ]  (tin) Vị trí cốp — Vị trí mở hiện tại của cốp sau
[ ]  (tin) Cửa sổ trời — Trạng thái đóng/mở cửa sổ trời
[ ]  (tin) Vị trí cửa sổ trời — Vị trí mở hiện tại của cửa sổ trời
[ ]  (tin) Rèm che nắng — Vị trí mở hiện tại của rèm che nắng
[ ]  (tin) Gương chiếu hậu — Trạng thái gập/mở của gương chiếu hậu
[ ]  (tin) Gạt mưa — Trạng thái hoạt động của gạt mưa
[ ]  (tin) Nguồn xe — Cấp nguồn hiện tại của xe (tắt/ACC/bật máy)
[ ]  (tin) Mẫu xe — Mã model của xe
[ ]  (tin) Cảnh báo khẩn — Đèn cảnh báo khẩn cấp có đang bật không
[ ]  (nút) Khoá / mở khoá — Bật/tắt khoá cửa xe, di chuyển chốt khoá vật lý
[ ]  (nút) Kính cửa lái — Bật/tắt điều khiển kính cửa lái, dịch chuyển kính vật lý
[ ]  (nút) Cốp sau — Bật/tắt mở cốp sau, dịch chuyển cốp vật lý
[ ]  (nút) Mở khoá cửa — Mở khoá cửa xe ngay lập tức
[ ]  (nút) Ca-pô — Bật/tắt mở ca-pô, dịch chuyển nắp ca-pô vật lý
[ ]  (nút) Cửa sổ trời — Bật/tắt điều khiển cửa sổ trời, dịch chuyển tấm kính
[ ]  (nút) Gạt mưa — Bật/tắt gạt mưa kính chắn gió
[ ]  (nút) Kính trước-trái — Mở/đóng kính cửa trước bên trái
[ ]  (nút) Kính trước-phải — Mở/đóng kính cửa trước bên phải
[ ]  (nút) Kính sau-trái — Mở/đóng kính cửa sau bên trái
[ ]  (nút) Kính sau-phải — Mở/đóng kính cửa sau bên phải
[ ]  (nút) Tất cả kính — Mở/đóng đồng thời toàn bộ kính cửa xe
[ ]  (nút) Rèm che nắng — Mở/đóng rèm che nắng cửa sổ trời
[ ]  (nút) Khoá trẻ em — Bật/tắt khoá trẻ em cho cửa sau
[ ]  (nút) Tự đóng kính khi mưa — Bật/tắt tự động đóng kính khi trời mưa
[ ]  (nút) Gập gương khi khoá — Bật/tắt tự động gập gương khi khoá xe
[ ]  (nút) Gập gương — Gập hoặc mở gương chiếu hậu ngay
[ ]  (nút) Nhớ ghế lái — Gọi lại vị trí ghế lái đã lưu
── Đèn ──
[ ]  (tin) Đèn cốt — Đèn cốt có đang bật hay không
[ ]  (tin) Đèn pha — Đèn pha (chiếu xa) có đang bật hay không
[ ]  (tin) Đèn sương mù trước — Đèn sương mù trước có đang bật hay không
[ ]  (tin) Đèn sương mù sau — Đèn sương mù sau có đang bật hay không
[ ]  (tin) Xi-nhan trái — Xi-nhan trái có đang nháy hay không
[ ]  (tin) Xi-nhan phải — Xi-nhan phải có đang nháy hay không
[ ]  (tin) Đèn hông — Đèn hông (đèn định vị) có đang bật không
[ ]  (tin) Đèn ban ngày — Đèn chạy ban ngày (DRL) có đang bật không
[ ]  (tin) Chế độ đèn pha — Chế độ đèn pha hiện tại (auto/thủ công…)
[ ]  (tin) Đèn viền cabin — Đèn viền nội thất có đang bật không
[ ]  (tin) Màu viền trước — Màu đèn viền nội thất khu vực trước
[ ]  (tin) Màu viền sau — Màu đèn viền nội thất khu vực sau
[ ]  (tin) Độ sáng viền trước — Độ sáng đèn viền nội thất khu vực trước
[ ]  (tin) Độ sáng viền sau — Độ sáng đèn viền nội thất khu vực sau
[ ]  (nút) Đèn đọc — Bật/tắt đèn đọc sách trong cabin
[ ]  (nút) Đèn pha — Bật/tắt đèn pha
[ ]  (nút) Đèn ban ngày — Bật/tắt đèn chạy ban ngày
[ ]  (nút) Đèn viền cabin — Bật/tắt đèn viền nội thất cabin
[ ]  (nút) Màu đèn viền — Chọn màu đèn viền nội thất
[ ]  (nút) Độ sáng viền — Tăng/giảm độ sáng đèn viền nội thất
[ ]  (nút) Đèn viền theo nhạc — Bật/tắt đèn viền nhấp nháy theo nhạc
[ ]  (nút) Chế độ đèn pha — Chọn chế độ đèn pha (auto/cốt/pha…)
── An toàn · ADAS ──
[ ]  (tin) Dây an toàn lái — Dây an toàn ghế lái đã cài hay chưa
[ ]  (tin) Dây an toàn phụ — Dây an toàn ghế phụ đã cài hay chưa
[ ]  (tin) Nhận diện tài xế — Hệ thống có nhận diện tài xế trên ghế không
[ ]  (tin) Nhận diện ghế phụ — Hệ thống có nhận diện người ngồi ghế phụ không
[ ]  (tin) Phát hiện trẻ em — Hệ thống có phát hiện trẻ em trong xe không
[ ]  (tin) Cảnh báo quá tốc — Trạng thái cảnh báo vượt quá tốc độ giới hạn
[ ]  (tin) Điểm mù trước-trái — Cảnh báo điểm mù phía trước bên trái
[ ]  (tin) Điểm mù trước-phải — Cảnh báo điểm mù phía trước bên phải
[ ]  (tin) Chuyển làn trái — Cảnh báo hỗ trợ chuyển làn bên trái
[ ]  (tin) Chuyển làn phải — Cảnh báo hỗ trợ chuyển làn bên phải
[ ]  (tin) Cắt ngang sau trái — Cảnh báo phương tiện cắt ngang phía sau bên trái
[ ]  (tin) Cắt ngang sau phải — Cảnh báo phương tiện cắt ngang phía sau bên phải
[ ]  (tin) Mở cửa cảnh báo trái — Cảnh báo mở cửa an toàn bên trái
[ ]  (tin) Mở cửa cảnh báo phải — Cảnh báo mở cửa an toàn bên phải
[ ]  (tin) Cảm biến đỗ (8 vùng) — Trạng thái cả 8 vùng cảm biến đỗ xe
[ ]  (tin) Âm lượng cảm biến — Mức âm lượng cảnh báo cảm biến đỗ xe
[ ]  (tin) Cân bằng điện tử (ESP) — Trạng thái hoạt động của hệ thống cân bằng điện tử
[ ]  (tin) Trạng thái nguồn (MCU) — Trạng thái nguồn của bộ điều khiển trung tâm (MCU)
[ ]  (tin) Ắc-quy 12V — Điện áp ắc-quy 12V hiện tại
[ ]  (tin) Mức ắc-quy 12V — Mức đánh giá tình trạng ắc-quy 12V
[ ]  (nút) Cảnh báo quá tốc — Bật/tắt cảnh báo vượt quá tốc độ giới hạn
[ ]  (nút) Cân bằng điện tử (ESP) — Bật/tắt hệ thống cân bằng điện tử ESP
[ ]  (nút) Nhận diện biển báo — Bật/tắt nhận diện biển báo giao thông
[ ]  (nút) Hỗ trợ giữ làn — Chọn chế độ hỗ trợ giữ làn đường
[ ]  (nút) Cảnh báo va chạm trước — Tăng/giảm độ nhạy cảnh báo va chạm phía trước
[ ]  (nút) Cắt ngang phía sau — Bật/tắt cảnh báo phương tiện cắt ngang phía sau
[ ]  (nút) Cảnh báo mở cửa — Bật/tắt cảnh báo an toàn khi mở cửa
[ ]  (nút) Phát hiện trẻ em — Bật/tắt phát hiện trẻ em bỏ quên trong xe
── Danh tính · khoá ──
[ ]  (tin) Số VIN — Số khung nhận dạng xe (VIN)
[ ]  (tin) Chìa Bluetooth — Trạng thái kết nối chìa khoá Bluetooth
[ ]  (tin) Mã máy — Mã định danh động cơ xe
[ ]  (tin) Mức nước làm mát — Mức nước làm mát động cơ còn lại
[ ]  (tin) Mức dầu — Phần trăm dầu động cơ còn lại
[ ]  (tin) Vĩ độ — Vĩ độ GPS hiện tại của xe
[ ]  (tin) Kinh độ — Kinh độ GPS hiện tại của xe
[ ]  (tin) Cao độ — Độ cao GPS hiện tại của xe
[ ]  (tin) Hướng — Hướng di chuyển hiện tại theo la bàn
── Giải trí · cụm · HUD ──
[ ]  (nút) Camera 360 — Bật/tắt hiển thị camera 360 độ
[ ]  (nút) Âm lượng — Tăng/giảm âm lượng hệ thống giải trí
[ ]  (nút) Chiếu cụm — Bật/tắt chiếu màn hình lên cụm đồng hồ
[ ]  (nút) Xoay màn hình — Chọn hướng xoay màn hình trung tâm
[ ]  (nút) Góc camera — Chọn góc nhìn camera hỗ trợ đỗ xe
[ ]  (nút) Nhạc trên cụm — Bật/tắt hiển thị thông tin nhạc trên cụm đồng hồ
[ ]  (nút) Độ sáng màn — Tăng/giảm độ sáng màn hình trung tâm
[ ]  (nút) HUD kính lái — Bật/tắt hiển thị HUD trên kính lái
[ ]  (nút) Độ sáng HUD — Tăng/giảm độ sáng hiển thị HUD
```
