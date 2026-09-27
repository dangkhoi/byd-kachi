> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 · **Mục đích**: Danh sách ảnh chụp cho hướng dẫn có hình (bản 2.77). Xếp để chụp HẾT trong MỘT lượt đi qua app trên máy ảo, không phải quay lại màn cũ. Ảnh lưu vào `docs/guide/img/<tên tệp>`.

> ⛔ **ẢNH XE THẬT ĐÃ BỊ GỠ KHỎI REPO (27/09 tối)** — 7 ảnh chép vào `docs/guide/img/` chứa **biển số xe người khác**, bản đồ sống chỉ đúng vị trí + giờ, số nhà, và avatar tài khoản cá nhân; chúng đã lọt lên repo CÔNG KHAI một lần và được gỡ bằng ghi đè lịch sử. `.gitignore` nay chặn `car-*.png` · `cum-*.png` · `camera-frame-*.png` · `cluster-fb-*.png` **độc lập đường dẫn**. ⇒ Mọi ảnh 🚗 trong tệp này phải **chụp lại** với: app dẫn đường ĐÓNG, **không đăng nhập tài khoản nào**, và không có xe khác trong khung — hoặc che biển số/tên đường/avatar trước khi dùng. Ảnh cũ giữ ngoài repo, không bao giờ commit.


## P0 · Chuẩn bị trước khi chụp (làm hết, theo thứ tự)

1. Máy ảo `clusternav10`, cài bản **2.77** `vehicleTest` (cùng chữ ký, `adb install -r`).
2. Cài đặt › Hệ thống & quyền → bật **Chế độ kiểm thử qua adb** (dùng `am broadcast … --es name …` để dựng nhanh trạng thái ô; xem memory `kachi-274-morning-session`). Chụp xong nhớ tắt.
3. Chép **3 ảnh** vào thư mục ảnh nền và **3 ảnh khác** vào thư mục widget Trình chiếu ảnh (hai thư mục KHÁC nhau — lấy đường dẫn bằng nút "Sao chép đường dẫn thư mục" ở Cài đặt › Màn hình chính).
4. Mở một app nhạc và phát một bài rồi bấm HOME (để ô "Đang phát" và ô "Bảng tổng hợp" có tên bài, không phải "—").
5. Cài đặt › Giọng nói → tải **mô hình nghe** và **gói giọng đọc** (mất nhiều phút — làm trước, không làm giữa lượt chụp).
6. Đặt hồ sơ về **Mặc định**, bố cục **3 ô**, giao diện tối (đồng bộ với ảnh trên xe).
7. Trạng thái ô ban đầu: ô-trái = một app đã nhúng · ô-phải-trên = TRỐNG · ô-phải-dưới = thẻ "Đồng hồ + thời tiết".
8. Chụp bằng `adb exec-out screencap -p`; ảnh cắt (crop) ghi rõ ở cột "Cần thấy gì".

**Ký hiệu**: “—” = ảnh sẽ có dấu gạch vì máy ảo không có dữ liệu xe · 🚗 = phải dùng ảnh chụp trên xe thật thay thế/kèm theo.

## A · Màn hình chính (không rời màn)

| # | Tệp | Đường đi | Cần thấy gì | “—” ? |
|---|---|---|---|---|
| 01 | `man-hinh-chinh-tong-quan.png` | Bấm HOME | Cả màn: thanh trên, 3 ô, thanh nút xe ở viền Dưới, hình nền | Chip trên "—" — **chấp nhận** (ảnh này để chỉ bố cục), đặt kèm 🚗 `car-man-hinh-that.png` |
| 02 | `thanh-trang-thai-tren.png` | cắt từ #01 | Giờ + ngày, 5 chip, nút mic/Ứng dụng/Cài đặt, chip hồ sơ | **KHÔNG chấp nhận** "—" → dùng 🚗 `car-thanh-tren-chip-that.png` làm ảnh chính, ảnh máy ảo chỉ để chú thích tên từng vật |
| 03 | `vung-o-lam-viec.png` | cắt từ #01 | Ô trống có dấu ＋ và chữ "Mở ứng dụng" · ô có app · ô có thẻ · nút ⇄ ở mép trên hai ô có nội dung | Không |
| 04 | `thanh-nut-xe.png` | cắt từ #01 | Dải nút xe: Cốp sau, Đèn đọc, Lọc bụi, Mát ghế lái, Nhiệt độ −/+, Gió −/+, Kính lái | Chấp nhận (hình nút là thứ cần thấy); kèm 🚗 cắt từ `car-man-hinh-that.png` |

## B · Ngăn kéo gán ô (mở một lần, cuộn dần — không đóng giữa chừng)

| # | Tệp | Đường đi | Cần thấy gì | “—” ? |
|---|---|---|---|---|
| 05 | `ngan-keo-dat-vao-o.png` | Chạm ô-phải-trên (ô trống) | Tiêu đề "Đặt widget hoặc mở app vào ô này" + câu hướng dẫn + lưới khối "Ứng dụng" | Không |
| 06 | `nhom-kha-nang.png` | cuộn xuống khối "Nhóm — xem cả cụm cùng lúc" | 8 ô nhóm + câu phụ "Một ô cho cả bộ…" | Không |
| 07 | `the-dung-tay.png` | cuộn tiếp tới khối "Thẻ dựng tay" | 9 thẻ, thấy rõ huy hiệu loại ở góc ô | Không |
| 08 | `o-tung-muc-rieng.png` | cuộn tiếp tới khối "Từng mục riêng", mở một lĩnh vực | Huy hiệu con mắt (xanh) và bàn tay (cam), viên "chưa kiểm trên xe", dòng "Đã có trong nhóm: …" | Không |
| 09 | `ngan-keo-tran-8-muc.png` *(mới, phụ cho §4)* | tích đủ 8 mục trong cùng ngăn kéo | Nút đáy "Đặt 8 mục" + dòng hổ phách "Ô chứa tối đa 8 mục — bỏ một mục để thêm" + các ô bị làm mờ | Không |

## C · Vòng chụp từng ô (lặp: nút ⇄ của ô-phải-trên → chọn → Đặt → chụp)

> Mỗi ảnh chỉ cần thấy ĐÚNG ô đó (cắt quanh ô). Dùng test-bridge để đặt nội dung ô cho nhanh nếu có.

| # | Tệp | Đặt vào ô | Cần thấy gì | “—” ? |
|---|---|---|---|---|
| 10 | `o-nang-luong.png` | thẻ "Năng lượng" | Vòng pin + dòng "≈ N km" | "—" → **cần** 🚗 (chưa có ảnh riêng; cắt tạm từ `car-man-hinh-that.png` nếu có ô pin, nếu không thì để lượt lên xe sau) |
| 11 | `o-khong-khi.png` | thẻ "Không khí" | Vòng bụi mịn + "PM2.5 · Tốt/TB/Kém" | "—" → cần 🚗 (chưa có ảnh) |
| 12 | `o-toc-do.png` | thẻ "Tốc độ" | Số to + "km/h" + "Tốc độ hiện tại" | "—" → chấp nhận (dáng số là thứ cần thấy), ghi chú rõ trong bài |
| 13 | `o-trang-thai-xe.png` | thẻ "Trạng thái xe" | Hình xe nhỏ + dòng kết luận | "—" (“Trạng thái cửa —”) → chấp nhận, kèm chú thích |
| 14 | `o-bang-tong-hop.png` | thẻ "Bảng tổng hợp" | Bốn ô con: pin · không khí · lốp · nhạc (ô nhạc CÓ dữ liệu nhờ P0.4) | 3/4 ô "—" → cần 🚗 |
| 15 | `o-ap-suat-lop.png` | thẻ "Áp suất lốp" | Hình xe + 4 thẻ số | **KHÔNG chấp nhận** → dùng 🚗 `car-o-lop-that.png` |
| 16 | `o-dang-phat.png` | thẻ "Đang phát" | Ảnh bìa, tên bài, thanh tiến trình, 3 nút | Không (không cần xe) |
| 17 | `o-dong-ho-thoi-tiet.png` | thẻ "Đồng hồ + thời tiết" | Giờ lớn, thứ+ngày, dòng "— · ngoài xe" | Chỉ dòng nhiệt độ "—" → chấp nhận |
| 18 | `o-trinh-chieu-anh.png` | thẻ "Trình chiếu ảnh" | Một ảnh phủ kín ô (nhờ P0.3) | Không |
| 19 | `ben-trong-o-nhom.png` | nhóm "Kính" | Ba tầng: đầu ô (hình + tên) · ô con chỉ xem · **hàng nút ở đáy** | "—" ở ô con → chấp nhận (mục đích là chỉ ba tầng) |
| 20 | `o-cua-va-khoang.png` | nhóm "Cửa & khoang" | Hình xe + hàng nút Cốp / Nóc / Rèm | "—" → chấp nhận |
| 21 | `o-nhieu-widget.png` | 6 mục lẻ trong một ô | Lưới 3+3, mỗi mục bản nén (hình + số + dòng phụ) | "—" → chấp nhận |

## D · Ngăn kéo mở app

| # | Tệp | Đường đi | Cần thấy gì | “—” ? |
|---|---|---|---|---|
| 22 | `ngan-keo-mo-ung-dung.png` | HOME → nút **Ứng dụng** trên thanh trên | Câu "Chạm một app để mở TOÀN MÀN…", hàng "Gần đây", lưới "Tất cả ứng dụng" | Không |

## E · Cài đặt — đi thẳng từ trên xuống theo rail, không quay lại

| # | Tệp | Đường đi (từ màn trước) | Cần thấy gì | “—” ? |
|---|---|---|---|---|
| 23 | `cai-dat-man-hinh-chinh.png` | Nút **Cài đặt** → nhóm "Màn hình chính" | Câu nhắc hồ sơ, hàng chip 6 bố cục (chip "3 ô" đang sáng), khối Hình nền đang BẬT (đủ 3 hàng chu kỳ/phủ/làm tối), mục Widget Trình chiếu ảnh | Không |
| 24 | `ve-bo-cuc.png` | cùng trang → "Vẽ bố cục riêng…" | Lưới 12×6, 3–4 khung, dòng trạng thái "còn N ô trống", nút Thêm/Xoá/Về bố cục sẵn/Lưu/Đóng | Không — đóng bằng **Đóng** (đừng Lưu, kẻo hỏng bố cục 3 ô cho các ảnh sau) |
| 25 | `cai-dat-thanh-tren-va-thanh-nut.png` | rail → "Thanh trạng thái & thanh nút" | Khối "Đang bật · 5/16" có nút ◀ ▶ trên ô, các khối lĩnh vực đang gập, mục "Vị trí trên thanh trên" 6 hàng | Không |
| 26 | `cai-dat-nut-mic-thanh-tren.png` | cắt từ #25 | Hàng "Nút mic trên thanh trạng thái" + câu phụ | Không |
| 27 | `chon-nut-thanh-nut-xe.png` | cùng trang → "Chọn nút trên thanh… (N đang bật)" | Khối **Launcher** đứng đầu, khối Nhóm, khối Từng mục riêng, nút đáy "Áp dụng (N)" | Không — thoát bằng Áp dụng với đúng tập nút cũ |
| 28 | `cai-dat-hien-thi-don-vi.png` ⚠ | rail → "Hiển thị & đơn vị" | Cả trang (chưa có bản kê — chụp trước, kê sau) | Không |
| 29 | `cai-dat-ho-so-tai-xe.png` | rail → "Hồ sơ tài xế" | Câu mở đầu, ≥2 thẻ hồ sơ (một thẻ ghi "Đang dùng"), nút Đổi tên/Xoá, mục HỒ SƠ LÚC NỔ MÁY, 3 nút Thêm/Xuất/Nhập | Không — **tạo hồ sơ thứ hai ngay ở bước này** (cần cho #30 và #47) |
| 30 | `hop-hoi-ten-ho-so.png` | cùng trang → "Thêm hồ sơ (bản sao của …)" | Tiêu đề "Hồ sơ mới", ô nhập điền sẵn "Bản sao của «Mặc định»", nút Lưu | Không |
| 31 | `cai-dat-dan-duong-cum.png` ⚠ | rail → "Dẫn đường & cụm đồng hồ" | Đầu trang (chưa có bản kê) | Không |
| 32 | `cai-dat-so-dia-chi.png` ⚠ | cùng trang → mục "Sổ địa chỉ" | Danh sách địa chỉ — **thêm 1 địa chỉ ở đây**, bắt buộc cho #33/#34 | Không |
| 33 | `cai-dat-tu-dan-duong-theo-lich.png` | cuộn tiếp tới "Tự dẫn đường theo lịch" | ≥1 hàng lịch "07:30–09:00 → «tên»", dòng phụ ghi thứ + app + "chờ có GPS", công tắc hàng, nút Sửa, nút "Thêm lịch…" | Không |
| 34 | `hop-them-lich-dan-duong.png` | cùng trang → "Thêm lịch…" | Bật lịch này · hai ô giờ · 7 ô thứ (T2–T6 đang tick) · "Chỉ dẫn khi đã có GPS" · Dẫn tới · Bằng app · Lưu | Không |
| 35 | `cai-dat-chieu-len-cum.png` ⚠ | rail → "Chiếu màn lên cụm" | Cả trang (chưa có bản kê) | Có thể có hàng cần cụm thật → kèm 🚗 `car-cum-chieu-1..3.png`, `car-cum-framebuffer.png` |
| 36 | `cai-dat-phim-vo-lang.png` ⚠ | rail → "Phím vô-lăng" | Danh sách gán (chưa có bản kê) | Không |
| 37 | `cai-dat-phim-vo-lang-kachi-nghe.png` | cùng trang → thêm/sửa một gán → danh sách đích | Đích **"Kachi nghe (tại máy)"** đang được chọn, thấy cả 3 đích còn lại | Không |
| 38 | `cai-dat-tien-nghi-xe.png` | rail → "Tiện nghi xe" | Ảnh dài (ghép 2–3 lần cuộn): Lấy gió trong · Tự sấy kính khi mưa (2 ô con **đang mờ** vì công tắc chính tắt) · Ghế mát/sưởi + sơ đồ ghế · Lọc bụi mịn + đồng hồ + nút "Lọc ngay một lượt" | "Bụi mịn hiện tại: chưa đọc được" → chấp nhận, bài phải chú thích |
| 39 | `cai-dat-camera-xi-nhan.png` | cùng trang, cuộn tới "Camera theo xi-nhan" | **Đúng 10 hàng của 2.77**, KHÔNG có "Nâng cao (kỹ thuật)", KHÔNG có hàng "Nguồn" | Không — nhưng ô camera thật thì cần 🚗 `car-camera-tron-xi-nhan.png` |
| 40 | `cai-dat-giong-noi.png` | rail → "Giọng nói" | Ảnh dài: Hey Kachi + dòng trạng thái model câu gọi (xanh) · mô hình nghe **đã cài** (nút "Gỡ mô hình") · gói giọng đọc đã cài · 2 công tắc đọc · khối "Hỏi xác nhận" (tích sẵn 2–3 dòng để thấy lý do) · Nguồn micro · App nhạc mặc định | Không |
| 41 | `cau-lenh-noi-duoc.png` | cuối trang → mở khối "Câu lệnh nói được" | 2–3 nhóm đang mở, mỗi dòng: câu nói + việc sẽ làm; thấy đuôi "· sẽ hỏi lại trước khi chạy" ở dòng đã tích ở #40 | Không |
| 42 | `cai-dat-he-thong-va-quyen.png` | rail → "Hệ thống & quyền" | Ảnh dài: phần Quyền (máy ảo THIẾU kênh shell nên **sẽ thấy các hàng quyền** — đúng thứ bài cần) · Màn hình chính · Khởi động 2 công tắc · Bảo trì 4 hàng · Nâng cao đúng 1 hàng | Không |
| 43 | `hop-co-ban-moi.png` | cùng trang → "Kiểm tra cập nhật" | Hộp "Có bản mới: v…", nút "Tải & cài" / "Để sau" | **Khó dựng**: chỉ hiện khi kênh phát hành có bản cao hơn bản đang cài. Cách dựng: cài 2.76 rồi để kênh ở 2.77. Nếu không dựng được → bỏ ảnh, mô tả bằng chữ |
| 44 | `cai-dat-gioi-thieu.png` | rail → "Giới thiệu" | Dòng phiên bản (phải đọc ra "2.77"), tên gói, giấy phép, miễn trừ | Không |

## F · Quay về màn chính — hồ sơ và giọng nói (cuối lượt)

| # | Tệp | Đường đi | Cần thấy gì | “—” ? |
|---|---|---|---|---|
| 45 | `hop-doi-ho-so.png` | Nút **Xong** → chạm chip hồ sơ trên thanh trên | Tiêu đề "Đổi hồ sơ tài xế", hàng "«tên» · đang dùng", hàng hồ sơ thứ hai, "Quản lý hồ sơ…" | Không |
| 46 | `noi-voi-xe-dang-nghe.png` | Chạm nút mic trên thanh trên, nói "bật đèn đọc", chụp lúc đang hiện chữ nghe được | Tấm chữ giữa-dưới: vòng sóng âm + một dòng chữ; chụp thêm một kiểu ở trạng thái "Đã nghe" + câu trả lời | Không (nhận dạng chạy tại máy, không cần xe) |
| 47 | `noi-voi-xe-xac-nhan.png` | Cài đặt › Giọng nói đã tích "Cốp sau" ở #40 → nói "mở cốp sau" | Tấm chữ ở trạng thái **Xác nhận**: câu hỏi kèm lý do + nút "Đồng ý" | Không — nhưng sau khi bấm Đồng ý thì lệnh không có tác dụng thật (không có xe) |

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

## H · Còn thiếu sau lượt này (phải lên xe mới có)

- Ô "Năng lượng", "Không khí", "Bảng tổng hợp", "Tốc độ", "Trạng thái xe" với **số thật** (#10, #11, #12, #13, #14).
- Ô camera đặt trên **màn cụm** (chip "Hiện camera lên màn cụm" + "Theo cụm").
- Sơ đồ ghế với **số ghế thật** của xe (máy ảo luôn vẽ 2 ghế).

---

**Tổng: 47 ảnh chụp máy ảo (gồm 1 ảnh mới `ngan-keo-tran-8-muc.png` ngoài bản kê) + 9 ảnh trên xe.**
