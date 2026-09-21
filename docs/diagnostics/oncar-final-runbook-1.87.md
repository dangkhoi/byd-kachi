# Runbook test-xe VÒNG CUỐI — Kachi 1.87 (88)

> **Trạng thái**: Current · **Ngày**: 2026-09-21 · **Mục đích**: đi HẾT một vòng trên xe, cái nào hỏng thì sửa
> luôn — vòng test cuối trước khi baseline đóng dự án. Bản 1.87 đã đăng OTA (main `f56c508`, `apk/Kachi-1.87-release.apk`).
>
> **Cách đọc**: mỗi bước ghi **LÀM GÌ** · **KẾT QUẢ MONG ĐỢI** · **AI** (EM = agent chuẩn bị/sửa off-car · OWNER =
> bấm/nhìn/nói trên xe). Cột **KẾT QUẢ THẬT / GHI CHÚ** để owner điền lúc test; hỏng thì chụp/nhớ để EM sửa.
>
> **Chuẩn bị**: xe đã bật máy, Kachi là màn hình chính. Nếu chưa 1.87: mở Kachi → nó tự dò OTA (Nav+HUD bật) hoặc
> `adb install -r apk/Kachi-1.87-release.apk`. Kiểm version ở Cài đặt › Giới thiệu.

---

## 0 · OTA + khởi động

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 0.1 | Nổ máy, chờ Kachi tự lên | Kachi là màn chính, hiện ngay (không kẹt splash) | OWNER | |
| 0.2 | Cài đặt › Giới thiệu → xem phiên bản | **1.87 (88)** | OWNER | |
| 0.3 | Nhìn tổng thể màn chính | KHÔNG còn **chấm tròn nhỏ** ở góc nút/ô/mục nào (đã bỏ hẳn) | OWNER | |

## 1 · Màn hình chính + ô/widget

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 1.1 | Mở picker (⇄ trên ô / ＋ ô trống) | Lưới nhóm + thẻ dựng tay + app; **KHÔNG có chấm amber** ở tile nào | OWNER | |
| 1.2 | Đặt widget **Lốp** (ô lớn) | Ô hiện **hình xe** (top-down, nền trong, đầu trên) + 4 bánh quanh xe | OWNER | |
| 1.3 | Đặt widget **Trạng thái xe** (ô lớn) | Hình xe mini hiện đúng hướng, không méo/lộn | OWNER | |
| 1.4 | Đổi bố cục (1 ô / 2 / 3 / 4) | App đang mở sắp lại đúng ô, không reset | OWNER | |
| 1.5 | Mở một app vào ô (từ Ứng dụng) | App vào ô, cuộn/chạm trong ô được (vuốt cuộn thật) | OWNER | |

## 2 · Ảnh xe / hình nền / trình chiếu (3 folder RIÊNG)

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 2.1 | Cài đặt › Hiển thị & đơn vị › **Hình xe** | Nói "Đang dùng hình xe mặc định" + 3 bước + nút **Sao chép đường dẫn** (`files/car/`) | OWNER | |
| 2.2 | Thay ảnh xe: chép ảnh top-down vào `files/car/` (USB/Files) | Mở lại màn chính → widget lốp/xe dùng ảnh MỚI | OWNER | |
| 2.3 | Cài đặt › Màn hình chính › **Hình nền** | Bước rõ + nút sao chép đường dẫn `files/wallpapers/` | OWNER | |
| 2.4 | Cài đặt › Màn hình chính › **Widget Trình chiếu ảnh** | Nói RÕ folder KHÁC (`files/photos/`) + nút sao chép | OWNER | |
| 2.5 | Bỏ ảnh vào `photos/` + đặt widget Trình chiếu ảnh | Ảnh trong `photos/` hiện + đổi vòng; KHÔNG lẫn với ảnh nền | OWNER | |

## 3 · Cài đặt › **Giọng nói** (menu MỚI, tách riêng)

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 3.1 | Mở rail Cài đặt | Có nhóm **"Giọng nói"** riêng (giữa *Tiện nghi xe* và *Hệ thống*) | OWNER | |
| 3.2 | Vào nhóm Giọng nói | Thấy: Hey Kachi · tải mô hình nghe · gói giọng đọc · giọng bé · đọc phản hồi · **App nhạc mặc định** · hỏi-xác-nhận · nguồn micro | OWNER | |
| 3.3 | Chọn **App nhạc mặc định** = YT Music (hoặc app đang cài) | Chip đổi + lưu | OWNER | |
| 3.4 | Nói "phát nhạc [tên bài]" (KHÔNG nêu app), không có nhạc đang phát | Mở đúng **app nhạc mặc định** vừa chọn | OWNER | |
| 3.5 | Đang phát nhạc app A → nói "phát bài X" (không nêu app) | Tiếp tục ở **app A** (không mở app default đè lên) | OWNER | |

## 4 · Voice — dẫn đường + sổ địa chỉ

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 4.1 | Cài đặt › Dẫn đường: thêm địa chỉ **Nhà** (dán lat,lng nếu có) | Lưu vào sổ (theo hồ sơ) | OWNER | |
| 4.2 | Nói "dẫn đường về nhà" | Mở app dẫn đường (mặc định đã chọn) tới đúng địa chỉ Nhà | OWNER | |
| 4.3 | Nói "dẫn đường tới [nơi] bằng vietmap" | Mở đúng VietMap (app được nêu) | OWNER | |
| 4.4 | Nói câu thường có chữ "nhà" (vd "nhà hàng gần đây") | KHÔNG bị hiểu nhầm thành dẫn-về-Nhà | OWNER | |

## 5 · Voice — điều khiển xe + câu trả lời

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 5.1 | Nói vài lệnh: "bật đèn đọc", "mở kính lái", "tăng nhiệt độ 24 độ" | Chạy đúng; câu trả lời KHÔNG còn đuôi "chưa kiểm trên xe" | OWNER | |
| 5.2 | Nói câu HỎI: "nhiệt độ đang bao nhiêu" | ĐỌC số, không bắn lệnh ghi | OWNER | |
| 5.3 | Bật "Hey Kachi" (nhóm Giọng nói) rồi gọi | (thử nghiệm) mở phiên nghe — ghi lại nhận hay không | OWNER | |

## 6 · Chiếu cụm + **bóng VietMap (bug vừa sửa)**

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 6.1 | Bật công tắc bóng VietMap (Cài đặt › Chiếu màn lên cụm hoặc Dẫn đường) | Autostart VietMap | OWNER | |
| 6.2 | **QUAN TRỌNG** — để autostart tự chạy (mạng chậm càng tốt để tái hiện bug cũ) | Kachi **chờ VietMap vào MAP thật** (không hạ khi còn ở flash) → **bóng LÊN cụm** mà KHÔNG phải bấm tay | OWNER | |
| 6.3 | Nếu bóng vẫn không lên | Ghi lại: VietMap dừng ở màn nào bao lâu → EM chỉnh SETTLE/timeout | OWNER→EM | |
| 6.4 | Chỉnh vị trí bóng (kéo-thả) | Bóng dời đúng chỗ | OWNER | |
| 6.5 | Chiếu một app lên cụm (nút nổi) rồi trả về | Lên/về cụm mượt | OWNER | |

## 7 · Tiện nghi + các nút xe (đi 1 lượt)

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 7.1 | Bấm lần lượt nút thanh nút xe + ô control (khoá/kính/cốp/đèn/ghế/điều hoà/lọc bụi/lấy gió…) | Nút nào chạy / không chạy — **ghi lại cái hỏng** | OWNER→EM | |
| 7.2 | Ghế mát/sưởi tự động, lọc bụi PM2.5, lấy gió khi nổ máy | Chạy như mong đợi | OWNER | |
| 7.3 | Mở "Chế độ kiểm thử qua adb" (Cài đặt › Hệ thống › Nâng cao) | Sau khi bật, các công cụ dev/chẩn đoán MỚI hiện; tắt thì ẩn | OWNER | |

## 8 · Giọng bé OTA (WP9)

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 8.1 | Cài đặt › Giọng nói → "Giọng Kachi bé" → **Tải** | Tải 1 file .zip (~10 MB) + tự giải nén trên xe | OWNER | |
| 8.2 | Chọn "Giọng phản hồi = giọng bé" + nói một lệnh | Câu trả lời đọc bằng giọng bé (câu lạ lùi về Piper) | OWNER | |

---

## Xử lý khi có mục HỎNG
- Owner ghi lại (chụp màn / nhớ hiện tượng) → báo EM.
- EM sửa **off-car** ngay trong buổi nếu tái hiện được (đa số UI/logic); cái cần đo HAL/xe thật thì EM chuẩn bị bản
  `vehicleTest` + đầu dò để owner chạy tại chỗ.
- Sửa xong → build bản mới → OTA → owner test lại đúng mục đó.

## Cột mốc kết thúc
Đi hết 8 mục, mọi mục PASS hoặc đã có cách xử → **baseline đóng dự án** (cập nhật PROJECT-BACKLOG + project-context).
