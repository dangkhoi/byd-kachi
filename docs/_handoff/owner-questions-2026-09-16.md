# Câu hỏi cần owner chốt — 2026-09-16 · **ĐÃ TRẢ LỜI 09-16 (ghi lại nguyên văn bên dưới)**

> **A**: vòng nhanh không phân biệt được; cửa/lọc gió có phản ứng, không chắc từng nút ⇒ giữ 🚗, lần sau bắn TỪNG nút có người nhìn.
> **B**: B1–B12 **chạy luôn** (không hỏi); B6 xe không có nắp ca-pô; **B13 có** — mục Cài đặt tự chọn nút nào hỏi.
> **C1** nhận mọi từ đồng nghĩa YES (ừ/vâng/có/được/ok/đúng/làm đi…). **C2 không** đọc câu hỏi. **C3 có** đọc phản hồi ("đã bật đèn đọc, đã chỉnh gió mức 3, nhiệt độ 24 độ").
> **D1** giữ mic 5 s. **D2** hỏi lại cho tới khi hiểu rồi làm. **D3** phải tải được trong app (OTA), không USB. **D4** thử int8 và mọi cách tới khi ngon. **D5** đo lúc lái + phải nhận được khi đang mở nhạc (Kiki/Gemini lọc tốt).
> **E1** OK · **E2** hỏi rủi ro · **E3** nav lên cụm OK (🟢) · **E4** để optional · **E5** nên cho đổi tên hồ sơ · **E6** xe CÓ cả 4 tính năng ⇒ cần RE feature-id · **E7** không ép tiền tố, phải hiểu tự nhiên (đã giải thích: luật tiền tố chỉ là nội bộ biasing, không ràng buộc người nói).
> **F1** OK.

Trả lời ngắn ngay dưới mỗi mục (✅/❌, số, hoặc một dòng). Chưa chắc thì ghi "chưa biết" — tôi sẽ không suy diễn. Mục nào không trả lời = giữ mặc định đang ghi.

## A. Vòng action trên xe 09:21–09:24 (HAL "nhận" 34 lệnh — xe có THẬT SỰ phản ứng không?)
Đánh ✅ (thấy xe làm) · ❌ (không thấy gì) · ⚠ (một phần / lạ) · ? (không để ý)

| # | Nút | Lệnh đã bắn | Xe phản ứng? | Ghi chú |
|---|---|---|---|---|
| A1 | Đèn đọc `readl` | bật → tắt | | |
| A2 | Sấy kính trước `defrost` | bật → tắt | | |
| A3 | Sấy kính sau `defrost_rear` | bật → tắt | | |
| A4 | Ghế mát `seatc` | bật → tắt | | |
| A5 | Ghế ấm `seath` | bật → tắt | | |
| A6 | Lọc bụi `pm25` | bật → tắt | | |
| A7 | Ion âm `anion` | bật → tắt | | |
| A8 | Gió trong `recirc` | bật → tắt | | |
| A9 | Sưởi vô-lăng `steer_heat` | bật → tắt | | |
| A10 | Quạt `fan` | 5 → 4 | | |
| A11 | Nhiệt độ `temp` | 24 → 22 | | |
| A12 | Âm lượng `vol` | 14 → 12 | | |
| A13 | Kính trước-trái `win_lf` | mở → **50%** → đóng | | 50% có dừng giữa không? |
| A14 | Rèm che nắng `sunshade` | mở → đóng | | |
| A15 | DRL `drl` | bật → tắt | | |
| A16 | Camera 360 `cam` | bật → tắt | | |
| A17 | Cửa kính lái (toggle) `window` | mở → đóng | | |
| A18 | Cốp `trunk` | mở → đóng | (framework báo không có method — xe có động gì không?) | |

## B. Lệnh giọng nào cần HỎI XÁC NHẬN trước khi chạy
Hiện tại hỏi: **mở khoá cửa** · **khoá (khi MỞ khoá)** · **tất cả kính (khi MỞ)** · **tắt chiếu cụm** · gói **"Mở hết kính"** · **đổi hồ sơ** · **dẫn đường bằng app ngoài** (xác nhận điểm đến tra được).
Anh nói: *"cái nào nguy hiểm lái xe mới hỏi, chứ mở cửa hỏi làm gì"*.

| # | Việc | Hỏi? (✅ hỏi / ❌ chạy luôn) |
|---|---|---|
| B1 | Mở khoá cửa | |
| B2 | Khoá cửa | |
| B3 | Mở TẤT CẢ 4 kính | |
| B4 | Mở 1 kính / 50% | |
| B5 | Mở cốp | |
| B6 | Mở nắp ca-pô | |
| B7 | Cửa sổ trời | |
| B8 | Tắt chiếu cụm (đang chạy nav trên cụm) | |
| B9 | Đổi hồ sơ (thay toàn bộ bố cục) | |
| B10 | Dẫn đường tới điểm tra được (xác nhận đúng chỗ) | |
| B11 | Đổi chế độ lái / regen / AVH / ADAS | |
| B12 | Sạc ngay / mục tiêu sạc | |
| B13 | Muốn có mục trong Cài đặt để tự bật/tắt hỏi cho từng nút? | ✅/❌ |

## C. Trả lời "đồng ý" bằng gì
| # | | |
|---|---|---|
| C1 | Nhận thêm **"ừ" / "vâng" / "có" / "được"** làm đồng ý (chỉ khi cả câu đúng một từ)? | |
| C2 | Muốn Kachi **đọc câu hỏi thành tiếng** rồi mới mở mic (thêm ~1,5 s)? — chỉ có khi lắp gói giọng | |
| C3 | Câu trả lời sau lệnh: ngắn kiểu *"Đã bật đèn đọc"* hay im lặng chỉ hiện chữ? | |

## D. Voice — hướng làm "nói qua lại tự nhiên" (xem mục 2 tin nhắn)
| # | | |
|---|---|---|
| D1 | Sau khi chạy xong lệnh, **giữ mic mở ~3 s** để nói lệnh tiếp mà không bấm phím lại? | |
| D2 | Khi không hiểu, Kachi **hỏi lại một câu ngắn** ("Kính nào — lái hay tất cả?") và nghe tiếp? | |
| D3 | Gói giọng Việt offline **61 MB**: anh upload lên GitHub Release `dangkhoi/byd-kachi` (tôi đưa 13 tệp + sha), hay chép **USB** (`<thẻ>/Android/data/com.byd.launcher/files/sherpa/import/piper-vi_VN-vais1000-medium/`)? | |
| D4 | Thử model **int8** (RAM 266 → ~70 MB, host không giảm độ chính xác, tốc độ trên xe chưa biết) qua USB lần sau? | |
| D5 | Nói khi đang lái: ồn đường ở 60–80 km/h — lần xe sau dành 5 phút thu 10 câu lúc chạy để đo? | |

## E. Kỹ thuật / quản trị
| # | | |
|---|---|---|
| E1 | Ngân sách dựng hotword mỗi phiên trên xe: tôi đặt tạm **≤150 ms** (host 6,6 ms). OK? | |
| E2 | Lịch sử public repo còn handle cũ ở **33 commit cũ** (metadata doc). Nhánh đã purge sẵn `purge/main-rewritten` (local). Anh muốn force-push xoá không? (lệnh: `git push --force-with-lease origin purge/main-rewritten:main`) — cert APK `O=…` vẫn cố định | |
| E3 | Catalog: **dẫn đường lên cụm / op-39** tôi hạ 🟢 → 🚗 vì chưa đo lại trên Kachi 1.6x. Anh đã thấy nav lên cụm chạy với Kachi 1.6x chưa? | |
| E4 | Xe khác đời (DL5) có cần hỗ trợ không? (`ClusterProfile` hiện không được gọi — nếu chỉ DL3 thì xoá cho gọn) | |
| E5 | Cần **đổi tên hồ sơ** không (hiện chỉ tạo bản sao/xoá)? | |
| E6 | 4 nút HAL báo "absent on trim" (điều hoà auto, đèn viền, gập gương, khoá trẻ em): xe anh **có** các tính năng này trên màn BYD không? (có ⇒ sai feature-id, không ⇒ ẩn nút) | |
| E7 | Cụm hotword ngắn bị luật tiền tố bỏ (`MỞ KHOÁ`, `MỞ CỬA SỔ`, `BẬT ĐIỀU HOÀ` khi nói một mình): chấp nhận, hay muốn tôi đo thêm để giữ? | |

## F. Ưu tiên
| # | | |
|---|---|---|
| F1 | Thứ tự tôi đề xuất: voice nhanh → xác nhận/Cài đặt → 12 datum sai device → pha 2 TTS → bug ô/inset → OTA 1.65. Đổi gì không? | |
