> **Trạng thái**: Current · **Cập nhật**: 2026-10-09 · **Chủ**: dangkhoi · **Mục đích**: Danh sách ảnh cho hướng dẫn có hình, bản **2.97**. Ảnh lưu ở `docs/guide/img/<tên tệp>`. Lượt 09/10 chụp lại toàn bộ trên máy ảo `kachi_play` và thêm các màn mới sau 2.79. Bản kê 2.77 cũ nằm trong lịch sử git của tệp này.

> ⛔ **ẢNH XE THẬT ĐÃ BỊ GỠ KHỎI REPO (27/09 tối)** — 7 ảnh chép vào `docs/guide/img/` chứa **biển số xe người khác**, bản đồ sống chỉ đúng vị trí + giờ, số nhà, và avatar tài khoản cá nhân; chúng đã lọt lên repo CÔNG KHAI một lần và được gỡ bằng ghi đè lịch sử. `.gitignore` nay chặn `car-*.png` · `cum-*.png` · `camera-frame-*.png` · `cluster-fb-*.png` **độc lập đường dẫn**. ⇒ Mọi ảnh 🚗 trong tệp này phải **chụp lại** với: app dẫn đường ĐÓNG, **không đăng nhập tài khoản nào**, và không có xe khác trong khung — hoặc che biển số/tên đường/avatar trước khi dùng. Ảnh cũ giữ ngoài repo, không bao giờ commit.


## Lượt chụp 2.97 — 09/10 (máy ảo `kachi_play`, `emulator-5556`, bản đăng `Kachi-2.97-release.apk`, mã 200)

**Cách đã dựng (để lượt sau làm lại được):**

1. KHÔNG đụng hồ sơ thật: tạo hồ sơ tạm (bản sao của «Mặc định»), chụp toàn bộ trên hồ sơ đó, cuối lượt về «Mặc định» rồi **xoá** hồ sơ tạm. Lý do: hồ sơ «Mặc định» trên máy ảo đang nhúng YouTube (có avatar + tên bài hát thật) ⇒ cấm chụp.
2. Ô 1 = app **Clock** (không có dữ liệu cá nhân). Ảnh nền = 5 ảnh vẽ sẵn (đồi + mặt trời, tự sinh). Ảnh widget Trình chiếu = 3 ảnh gradient tự sinh bằng PIL, chép vào `files/photos/` rồi **xoá** sau lượt. Phải tắt-bật lại "Dùng ảnh làm hình nền" thì widget mới quét lại thư mục ảnh.
3. Giao diện **Tối** cho hầu hết ảnh. Riêng 7 ảnh ngăn kéo đặt ô (`ngan-keo-*`, `nhom-kha-nang`, `the-dung-tay`, `o-tung-muc-rieng`) chụp lúc còn giao diện Sáng.
4. Ảnh đã nén bảng màu 256 (PNG, cạnh dài ≤ 1600 px). Cả thư mục ≈ 10 MB / 137 tệp.
5. Không bật Chế độ kiểm thử qua adb, không cần kênh shell. Không mở YouTube/Google/Maps/VietMap/Waze, không mở màn tài khoản, không kéo khay thông báo. Không bật micro (máy ảo thu micro thật của máy chủ).
6. **Bẫy công cụ**: cuộn trang Cài đặt bằng vuốt ở x=1800. Vuốt ở giữa trang (x≈1100) mà trúng khung xem trước biển báo/camera/bong bóng thì sẽ **kéo luôn vị trí** (lượt này lỡ kéo biển báo 1780→835 px, đã sửa; vì biển báo lưu theo hồ sơ nên xoá hồ sơ tạm là «Mặc định» còn nguyên 1780/80).

**Cột EN**: owner 09/10 chốt **không cần bộ ảnh tiếng Anh**. Bài viết song ngữ nhưng dùng chung ảnh giao diện tiếng Việt. Một phần ảnh `*-en.png` đã chụp trước khi có quyết định này, vẫn giữ trong thư mục (ghi ✅ ở cột EN), không chụp thêm.

**Ký hiệu**: ✅ đã chụp lượt này · ♻ giữ ảnh cũ (chưa chụp lại được) · 🚗 phải chụp trên xe · ❌ không làm được trên máy ảo · — không cần

### A · Màn hình chính

| # | Tệp | Cần thấy gì | VI | EN (không cần — owner 09/10) | Mục bài |
|---|---|---|---|---|---|
| 01 | `man-hinh-chinh-tong-quan.png` | Bố cục 3 ô: app Clock · thẻ Đồng hồ + thời tiết · ô trống (chỉ còn nút ⇄); thanh trên; thanh nút xe (Cốp sau · Đèn đọc · Ứng dụng · Nói với xe · Camera sau) | ✅ | ✅ | §1, §2 |
| 02 | `thanh-trang-thai-tren.png` | Cắt từ #01: giờ, ngày, 5 chip "—", nút Ứng dụng/Cài đặt, chip hồ sơ | ✅ (chip "—", số thật cần 🚗) | ✅ | §2 |
| 03 | `vung-o-lam-viec.png` | Cắt từ #01: các ô, nút ⇄ / ✕ / "chạy nền" ở mép trên ô | ✅ | ✅ | §2 |
| 04 | `thanh-nut-xe.png` | Cắt từ #01: thanh nút xe cỡ 110 % | ✅ | ✅ | §2 |
| 04a | `bo-cuc-1-o.png` · `bo-cuc-2-cot.png` · `bo-cuc-2-hang.png` · `bo-cuc-4-o.png` | 4 bố cục sẵn (mới) | ✅ | ✅ (chỉ `bo-cuc-4-o-en`) | §3 |
| 04b | `o-nut-tat-xac-nhan.png` | Nút ✕ chuyển ĐỎ chờ chạm lần hai mới tắt ô (mới) | ✅ | — | §3 |

### B · Ngăn kéo đặt ô

| # | Tệp | Cần thấy gì | VI | EN | Mục bài |
|---|---|---|---|---|---|
| 05 | `ngan-keo-dat-vao-o.png` | Tiêu đề + lưới "Ứng dụng" | ✅ (Sáng) | ✅ | §3 |
| 05a | `ngan-keo-widget-app-khac.png` | Khối "Widget của app khác" (widget Android) (mới) | ✅ | ✅ | §3 |
| 06 | `nhom-kha-nang.png` | 8 ô nhóm | ✅ | ✅ | §3, §4 |
| 06a | `ngan-keo-camera-theo-yeu-cau.png` | Khối "Camera theo yêu cầu": Sau · Trái · Phải · Trước · Tắt camera (mới, 2.93) | ✅ | ✅ | §5 |
| 07 | `the-dung-tay.png` | 10 thẻ dựng tay, gồm "Lưới lối tắt app" (mới 2.85) | ✅ | ✅ | §3, §4 |
| 08 | `o-tung-muc-rieng.png` | Khối "Từng mục riêng" › Năng lượng & sạc, huy hiệu con mắt/bàn tay, dòng "Đã có trong nhóm" | ✅ | ✅ | §3, §4 |
| 09 | `ngan-keo-tran-8-muc.png` | 8 mục đã chọn, nút "Đặt 8 widget", dòng "Ô chứa tối đa 8 mục" | ✅ | ✅ | §4 |

### C · Từng ô (cắt quanh ô)

| # | Tệp | Cần thấy gì | VI | EN | Ghi chú |
|---|---|---|---|---|---|
| 10 | `o-nang-luong.png` | Vòng pin "— pin" | ✅ "—" | ✅ | số thật 🚗 |
| 11 | `o-khong-khi.png` | Vòng PM2.5 "— µg/m³" | ✅ "—" | — | số thật 🚗 |
| 12 | `o-toc-do.png` | "— km/h · Tốc độ hiện tại" | ✅ "—" | — | |
| 13 | `o-trang-thai-xe.png` | Hình xe + "Trạng thái cửa —" | ✅ "—" | — | |
| 14 | `o-bang-tong-hop.png` | 4 ô con pin · PM2.5 · lốp · nhạc | ✅ "—" | — | số thật 🚗 |
| 15 | `o-ap-suat-lop.png` | Hình xe + 4 thẻ TT/TP/ST/SP | ✅ "—" | — | số thật 🚗 |
| 16 | `o-dang-phat.png` | Ảnh bìa mặc định, 3 nút; KHÔNG có tên bài (cố ý, tránh tên bài thật) | ✅ (trạng thái chờ) | — | |
| 17 | `o-dong-ho-thoi-tiet.png` | Giờ lớn, thứ + ngày, "— · ngoài xe" | ✅ | ✅ | |
| 18 | `o-trinh-chieu-anh.png` | Ảnh gradient tự sinh phủ ô | ✅ | — | |
| 18a | `o-luoi-loi-tat-app.png` | Widget "Lưới lối tắt app" (mới 2.92) | ✅ | — | |
| 18b | `o-camera-theo-yeu-cau.png` | Ô "Camera sau" (chạm để bật/tắt camera) (mới 2.93) | ✅ | ✅ | hình camera thật 🚗 |
| 19 | `ben-trong-o-nhom.png` | Nhóm "Kính": đầu ô · 4 ô con · hàng nút đáy | ✅ "—" | — | |
| 20 | `o-cua-va-khoang.png` | Nhóm "Cửa & khoang": hình xe + Cốp sau / Cửa sổ trời / Rèm che nắng | ✅ "—" | — | |
| 21 | `o-nhieu-widget.png` | 6 mục lẻ trong một ô (lưới 3+3) | ✅ "—" | — | |

### D · Ngăn kéo mở app · hồ sơ · giọng nói

| # | Tệp | Cần thấy gì | VI | EN | Mục bài |
|---|---|---|---|---|---|
| 22 | `ngan-keo-mo-ung-dung.png` | "Mở ứng dụng", hàng Gần đây, lưới Tất cả ứng dụng | ✅ | ✅ | §3 |
| 45 | `hop-doi-ho-so.png` | "Đổi hồ sơ tài xế": Mặc định · hồ sơ thứ hai · Quản lý hồ sơ… | ✅ | ✅ | §7 |
| 46 | `noi-voi-xe-dang-nghe.png` | Tấm chữ đang nghe | ♻ ảnh 2.79 (máy ảo chưa có mô hình nghe; không bật micro vì máy ảo thu micro thật của máy chủ) | — | §6 |
| 46a | `noi-voi-xe-chua-co-mo-hinh.png` | Bấm "Nói với xe" khi chưa tải mô hình: "Chưa tải mô hình nhận dạng" + "Mở Cài đặt" (mới) | ✅ | — | §6, §9 |
| 47 | `noi-voi-xe-xac-nhan.png` | Tấm chữ trạng thái Xác nhận | ❌ cần mô hình nghe + micro, chưa chụp | — | §6 |

### E · Cài đặt (theo cột trái)

| # | Tệp | Cần thấy gì | VI | EN | Mục bài |
|---|---|---|---|---|---|
| 23 | `cai-dat-man-hinh-chinh.png` | Câu nhắc hồ sơ, 6 chip bố cục, "Vẽ bố cục riêng…", "Tự ẩn nút ⇄" (mới 2.88), Hình nền | ✅ | ✅ | §2 |
| 23a | `cai-dat-hinh-nen-trinh-chieu.png` | Chu kỳ / Cách phủ / Làm tối + "Widget Trình chiếu ảnh" | ✅ | ✅ | §2 |
| 24 | `ve-bo-cuc.png` | Lưới, 4 khung, nút Thêm/Xoá/Về bố cục sẵn/Lưu | ✅ | — | §3 |
| 24a | `ve-bo-cuc-bao-chong-lan.png` | Dòng đỏ "Chưa lưu được: khung 1 và khung 3 đè lên nhau", nút Lưu mờ (mới) | ✅ | — | §3 |
| 25 | `cai-dat-thanh-tren-va-thanh-nut.png` | "Chip trên thanh trạng thái · ĐANG BẬT 5/16" với ◀ ▶ | ✅ | ✅ | §2 |
| 25a | `cai-dat-vi-tri-thanh-tren.png` | "Hiện nhãn trên thanh trên" + "Vị trí trên thanh trên" 6 hàng | ✅ | ✅ | §2 |
| 26 | `cai-dat-nut-mic-thanh-tren.png` | Cắt: hàng "Nút mic trên thanh trạng thái" | ✅ | — | §2 |
| 26a | `cai-dat-nut-mic-va-thanh-nut-xe.png` | Nút mic + khối "Thanh nút xe" (Hiện thanh, Viền đặt thanh, Chọn nút…) | ✅ | ✅ | §2 |
| 26b | `cai-dat-loi-tat-ung-dung.png` | "Lối tắt ứng dụng (17)": mỗi app Ô 1/2/3 · Toàn màn · Chạy ngầm (mới 2.85) | ✅ | ✅ | §2 |
| 27 | `chon-nut-thanh-nut-xe.png` | Khối Launcher (Ứng dụng · Cài đặt · Nói với xe · Lối tắt ứng dụng) + Camera theo yêu cầu | ✅ | ✅ | §2 |
| 28 | `cai-dat-hien-thi-don-vi.png` | Đơn vị · Sáng/Tối/Tự động · Kính thật · Màu nhấn | ✅ | ✅ | §2 |
| 28a | `cai-dat-do-trong-suot-nen.png` | Màu nhấn · Tông thể · **Độ trong suốt nền** 0–100 % (mới 2.88) · Hình xe | ✅ | ✅ | §2 |
| 28b | `cai-dat-co-thanh-nut-ngon-ngu.png` | Cỡ thanh nút xe 50–150 % (mới 2.89) + **Ngôn ngữ**: Theo xe · Tiếng Việt · English · 简体中文 · ไทย · Bahasa Melayu (mới 2.87) | ✅ | ✅ | §2, §8 |
| 29 | `cai-dat-ho-so-tai-xe.png` | 2 thẻ hồ sơ, Đổi tên/Xoá, HỒ SƠ LÚC NỔ MÁY, Thêm/Xuất đầy đủ/Xuất để chia sẻ/Nhập | ✅ | ✅ | §7 |
| 30 | `hop-hoi-ten-ho-so.png` | Hộp "Hồ sơ mới" điền sẵn "Bản sao của Mặc định" | ✅ | — | §7 |
| 30a | `ho-so-xuat-chia-se.png` | Thông báo "Đã xuất: …/files/profiles/…-share.kachi" (mới) | ✅ | — | §7 |
| 30b | `hop-nhap-ho-so.png` | Hộp "Chọn tệp hồ sơ để nhập" (mới) | ✅ | — | §7 |
| 31 | `cai-dat-dan-duong-cum.png` | Sổ địa chỉ (trống) · Tự dẫn đường theo lịch (trống) · Dẫn đường + HUD với các dòng trạng thái | ✅ | ✅ | §5 |
| 31a | `cai-dat-dan-duong-hud.png` | Chế độ hiện trên cụm · Chạy chữ tên đường dài · App dẫn đường mặc định · Kết nối lại | ✅ | ✅ | §5 |
| 31b | `cai-dat-bien-bao-toc-do.png` | Biển báo tốc độ: 3 công tắc, cỡ, vị trí ngang/dọc, khung kéo | ✅ | ✅ | §5 |
| 32 | `cai-dat-so-dia-chi.png` | Cắt: "Sổ địa chỉ · Chưa lưu địa chỉ nào · Thêm địa chỉ…" (cố ý để trống, không có địa chỉ) | ✅ | — | §5 |
| 32a | `hop-them-dia-chi.png` | Hộp "Thêm địa chỉ…" trống (Tên gọi · Địa chỉ · Toạ độ) | ✅ | ✅ | §5 |
| 33 | `cai-dat-tu-dan-duong-theo-lich.png` | Cắt: "Tự dẫn đường theo lịch · Chưa có lịch nào · Thêm lịch…" | ✅ (trạng thái trống) | — | §5 |
| 34 | `hop-them-lich-dan-duong.png` | Hộp Thêm lịch có giờ/thứ | ♻ ảnh 2.79 (cần có sẵn 1 địa chỉ; `adb input text` không gõ được dấu tiếng Việt) | — | §5 |
| 34a | `hop-them-lich-chua-co-dia-chi.png` | Hộp "Thêm lịch…" khi sổ trống: "Chưa có địa chỉ nào để dẫn tới…" (mới) | ✅ | ✅ | §5 |
| 35 | `cai-dat-chieu-len-cum.png` | Bật Cluster Cast · Nút nổi chiếu cụm · **Tỉ lệ chia đôi 1:9…9:1** (mới 2.96) · Tự chiếu khi nổ máy | ✅ (bật tạm để chụp, đã tắt lại) | ✅ (đang tắt) | §5 |
| 35a | `cai-dat-chieu-ngay-cuu-ho.png` | Chiếu ngay (toàn cụm / nửa trái / nửa phải / dừng) · Cứu hộ (Trả cụm về đồng hồ · Dọn sạch cụm) | ✅ | ✅ | §5, §9 |
| 35b | Kiểu chiếu cụm, khung **Bo tròn / Chữ nhật**, DPI + khung từng app | Chỉ hiện khi có cụm thật | 🚗 | — | §5 |
| 36 | `cai-dat-phim-vo-lang.png` | Nhận nút vật lý · Kiểm tra / Sửa ngay · Gán phím · Học phím mới | ✅ | ✅ | §6 |
| 36a | `gan-phim-buoc-1.png` · `gan-phim-buoc-2.png` | Bước 1 chọn nút · Bước 2 nút này làm gì (Mở ứng dụng · trợ lý / Camera theo yêu cầu / các nhóm xe) (mới) | ✅ | ✅ | §6 |
| 37 | `cai-dat-phim-vo-lang-kachi-nghe.png` | Bước 3: "Kachi nghe (tại máy)" cùng 3 đích trợ lý + danh sách app | ✅ | ✅ | §6 |
| 37a | Gán riêng **núm yên ngựa / vô-lăng** (dòng "nguồn: …" dưới nút vừa học) | Chỉ hiện sau khi học phím trên xe | 🚗 | — | §6 |
| 38 | `cai-dat-tien-nghi-xe.png` | Lấy gió trong · Tự sấy kính khi mưa (2 ô độc lập, 2.84) · Camera theo xi-nhan | ✅ | ✅ | §5 |
| 39 | `cai-dat-camera-xi-nhan.png` | Hình khung camera · **Kiểu hình** Nắn thẳng / Thẳng rộng / Gương cầu · Thu phóng · Từng camera | ✅ | ✅ | §5 |
| 39a | `cai-dat-camera-tung-camera.png` | Chọn camera Sau/Trái/Phải/Trước · Xem thử · Góc mặc định · khung kéo đặt chỗ · Cỡ (mới 2.93) | ✅ | ✅ | §5 |
| 39b | `cai-dat-camera-tung-camera-2.png` | Hình khung / Kiểu hình / Xoay / Lật gương từng camera · thử số camera (mới) | ✅ | ✅ | §5 |
| 39c | `cai-dat-ghe-va-loc-bui.png` | Ghế mát/sưởi + sơ đồ ghế · Lọc bụi mịn ("chưa đọc được") | ✅ "—" | ✅ | §5 |
| 39d | Hình camera thật trong ô / trên cụm | — | 🚗 | — | §5 |
| 40 | `cai-dat-giong-noi.png` | Hey Kachi · Nhận dạng giọng nói (tại máy) **chưa cài** · Giọng đọc offline chưa cài | ✅ (máy ảo chưa tải mô hình) | ✅ | §6 |
| 40a | `cai-dat-giong-noi-xac-nhan.png` | Đọc phản hồi bằng giọng · "Hỏi xác nhận trước khi chạy" | ✅ | ✅ | §6 |
| 40b | `cai-dat-day-ten-app-va-nhac.png` | Nguồn micro · **Dạy Kachi tên app** (2.91) · App nhạc mặc định · đầu Câu lệnh nói được | ✅ | ✅ | §6 |
| 40c | `day-ten-app.png` | Hộp "Dạy tên app": Tất cả 22 / Đã dạy 0 / Nên dạy 13, nút ▶ Thử (mới) | ✅ | ✅ | §6 |
| 41 | `cau-lenh-noi-duoc.png` | Nhóm "Đèn" mở: câu nói + việc sẽ làm | ✅ | ✅ | §6 |
| 42 | `cai-dat-he-thong-va-quyen.png` | Quyền (đủ) · Màn hình chính · Khởi động · đầu "Mở app khi nổ máy" | ✅ | ✅ | §8 |
| 42a | `cai-dat-mo-app-khi-no-may.png` | Mở app khi nổ máy · **Tự mở nhạc khi lên xe** (Tắt / Theo player của xe / YouTube Music / YouTube) · Bảo trì (mới 2.85+) | ✅ | ✅ | §8 |
| 42b | `chon-app-khi-no-may.png` | Hộp "Chọn app mở khi nổ máy" (mới) | ✅ | ✅ | §8 |
| 42c | `cai-dat-bao-tri-nang-cao.png` | Bảo trì (Tự động cập nhật · Kiểm tra cập nhật · Dừng toàn bộ dẫn đường · Khởi động lại launcher) · Nâng cao (Chế độ kiểm thử qua adb — TẮT) | ✅ | ✅ | §8, §9 |
| 43 | `hop-co-ban-moi.png` | Hộp "Có bản mới" | ♻ ảnh cũ (máy ảo đang ở bản mới nhất 2.97) | — | §8 |
| 43a | `kiem-tra-cap-nhat-moi-nhat.png` | Nút đổi thành "đang ở bản mới nhất (v2.97)" (mới) | ✅ | ✅ | §8 |
| 44 | `cai-dat-gioi-thieu.png` | "Phiên bản 2.97 (mã 200)", tên gói, giấy phép MIT, miễn trừ | ✅ | ✅ | §1, §8 |

### F · Không chụp được trên máy ảo (lượt này)

- **HOME guard / hộp chọn launcher, YouTube phát tiếp, nhắc khi đổi hồ sơ tắt dẫn đường lên cụm**: cần app dẫn đường/YouTube đang chạy ⇒ phải mở app có tài khoản ⇒ **cấm** theo luật riêng tư. ❌
- **Cụm đồng hồ, HUD, bong bóng VietMap, biển báo trên cụm, khung Bo tròn/Chữ nhật, ô 7 đỗ ẩn**: máy ảo không có cụm. 🚗
- **Camera (hình thật), số liệu xe (pin, lốp, PM2.5, tốc độ, cửa)**: máy ảo không có HAL xe. 🚗
- **Nguồn phím núm/vô-lăng (2.88)**: cần học phím trên xe. 🚗
- **Gửi log / màn Chẩn đoán**: không có lối vào trong Cài đặt của 2.97 ở các trang đã đi qua; chưa chụp.


## G · Ảnh trên xe — không chụp ở máy ảo, chỉ chép vào `docs/guide/img/`

| Tệp đích | Nguồn | Thay cho ảnh nào |
|---|---|---|
| `car-man-hinh-that.png` | `car-0927/car-home-274.png` | kèm #01 |
| `car-thanh-tren-chip-that.png` | cắt từ ảnh trên | **thay** #02 |
| `car-o-lop-that.png` | cắt từ ảnh trên | **thay** #15 |
| `car-camera-tron-xi-nhan.png` | `car-0927/car-round.png` | kèm #39 |
| `car-khung-anh-camera-tho.png` | `car-0927/camera-frame-20260927-095818.png` | kèm #39 (ảnh thô 4 vòng mắt cá) |
| `car-cum-chieu-1.png` / `-2` / `-3` | `cluster-276/zoom-c0.png` / `zoom-c1.png` / `zoom-c2.png` | kèm #35 |
| `car-cum-framebuffer.png` | `car-0927/cluster-fb-d1.png` | kèm #35 |

⚠ Cả 9 ảnh này chụp ở bản **2.74–2.76**. Trước khi dán vào bài phải đối chiếu với 2.77 (ít nhất: thanh trên, thanh nút, khung camera) — nếu lệch thì chụp lại ở lượt lên xe kế tiếp.

---

**Tổng lượt 09/10: 79 ảnh VI + 55 ảnh EN (giữ lại, không bổ sung) mới hoặc chụp lại; 3 ảnh cũ giữ nguyên (♻); ≈ 10 MB.**
