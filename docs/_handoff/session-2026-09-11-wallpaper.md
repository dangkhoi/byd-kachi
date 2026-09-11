# Handoff — phiên 2026-09-11 (trưa-2): U4 hình nền từ ảnh + trình chiếu

> **Trạng thái**: Session · **Ngày**: 2026-09-11 · **Mục đích**: giao lại điểm dừng của U4.

## Kết quả một dòng

Đã push (`874a820`). Full 5 module **2580 / 0 lỗi** (đầu phiên 2563 ⇒ **+17**). Cây git sạch.

## Vì sao chọn U4

Sau bốn phiên làm các mảng nền, lần này tôi chọn một việc **hoàn chỉnh trong một phiên** thay vì nửa việc lớn. U4 nhỏ, thấy được ngay, không đụng nền rủi ro (tầng chiếu app vào ô).

## Câu khó nhất không phải vẽ ảnh, mà là lấy ảnh từ đâu

Hai đường thông thường đều **vướng ràng buộc của xe**:

| Đường | Vướng gì |
|---|---|
| Màn chọn tệp của hệ thống | Màn hệ thống trên xe **bị khoá** — nút "chọn ảnh" kiểu thường sẽ dẫn anh vào chỗ không làm được gì |
| Quét bộ nhớ chung | Cần **quyền đọc bộ nhớ**, phải cấp lúc chạy |

Đường đã chọn: **thư mục riêng của app ở bộ nhớ ngoài** — không cần quyền nào, không cần màn hệ thống nào, và app **đã dùng** chính lối này để xuất log.

**Cách dùng**: bỏ ảnh vào `Android/data/com.byd.launcher/files/wallpapers`, rồi bật trong bảng Tuỳ biến. Bảng đó **nói rõ đường dẫn** cho anh.

## Bất biến phải giữ

- **Mặc định TẮT.** Ai không dùng không thấy gì khác; nền vẽ sẵn vẫn là mặc định **và** là đường lùi (chưa bật / không ảnh / ảnh hỏng).
- **Làm tối ảnh là cần thiết, không phải trang trí** — chữ và ô của launcher màu sáng; ảnh sáng làm chữ không đọc được. Mặc định 45%.
- **Chưa tới hạn thì giữ nguyên cả chỉ số và mốc thời gian.** Cập nhật mốc mà không đổi ảnh sẽ đẩy hạn lùi mãi ⇒ ảnh không bao giờ đổi, và lỗi đó **im lặng**. Có test khoá.
- **Nạp ảnh giảm cỡ**, không bao giờ nạp nguyên bản (ảnh 12MP ≈ 48 MB).
- **Nhả ảnh cũ SAU** khi đưa ảnh mới vào View, không trước.

## Đo hai lượt, và lượt hai là nhờ tác nhân tự nêu giới hạn

**Lượt 1** — ba ảnh đơn sắc: nền đổi **navy → lục** sau 22 giây, chữ vẫn đọc được.

Tác nhân đọc ảnh **tự nói ra** rằng ảnh đơn sắc thì *không thể* kết luận về kéo méo hay lặp gạch. Đúng, nên có lượt 2.

**Lượt 2** — ảnh **vuông 600×600** có lưới đều + vòng tròn, phủ lên khung 16:9:

| Phép đo | Kết quả |
|---|---|
| Bước lưới dọc vs ngang | **192.00** vs **192.27** px ⇒ ô còn **vuông** |
| Vòng tròn | bán kính 800.3 px, lệch chuẩn **0.53** px ⇒ còn **tròn**, không thành elip |
| Cắt trên / dưới | **258 / 264** px ⇒ **cân** |
| Viền đen ở mép | **0 pixel** |
| Lặp gạch | không — 217 vệt vòng tròn đều nằm trên **một** vòng |

⇒ Chế độ phủ kín **cắt** đúng chứ không kéo méo.

## Lỗi dùng-được tìm ra khi ĐO, test không bắt

Thư mục ảnh **chỉ được tạo khi đã bật** tính năng. Nhưng muốn thấy ảnh phải bật, mà bật có nghĩa **phải bỏ ảnh vào trước** — và thư mục lại chưa tồn tại để mà bỏ vào. **Vòng lặp chết ngay lần dùng đầu.**

Sửa: luôn tạo thư mục lúc mở launcher, kể cả khi tính năng đang tắt.

## Còn tồn

- **Widget trình chiếu riêng đặt vào ô chưa làm** — anh nêu cả hai (hình nền + widget). Nền là phần dùng nhiều hơn nên làm trước; widget nay **rẻ** vì logic và kho ảnh đã có sẵn.
- **Thanh trên là chỗ yếu nhất**: đo được nền lọt qua **18%** ở đó (thẻ workspace 0%). Với ảnh nhiều hoa văn thì chữ ở thanh trên sẽ rối nhất. Cách chữa: tăng độ đục riêng thanh trên — nhưng cần anh xem với **ảnh thật của anh** mới biết có đáng đổi không.
- Sai số nhịp tối đa 10 giây (do dùng lại nhịp có sẵn).
- **Chưa có lượt soát độc lập phần code** (tác nhân soát hỏng nhiều phiên gần đây). Phần xác minh **bằng mắt** thì có tác nhân độc lập làm 2 lượt.

## Chờ owner (tích lại)

1. **Ngưỡng lốp** — số agent tự chọn.
2. **Gói "Rời xe"** có nên khoá xe khi đóng kính hỏng không.
3. **Nhịp chờ giữa các bước** gói lệnh: đang 400 ms.
4. **Icon** có cần phân biệt tới từng ô không.
5. **Vòng kiểm quyền** có nên lặp tới khi đủ không.
6. **Bố cục "3 ô"** không lên lưới được — có muốn thêm bố cục mới gần giống mà đúng lưới không.
7. **Mới**: thanh trên có cần tăng độ đục khi bật hình nền không · có muốn **widget trình chiếu riêng** không.

## Thứ tự burn còn lại

P9 bước 2 (trình vẽ khung) → P9 bước 3 (nối tầng vẽ — rủi ro cao nhất còn lại) → U4 phần widget trình chiếu (rẻ) · S1 (dựng lại màn Cài đặt, owner đã duyệt) · W5 (camera 360) → U5 (đa ngôn ngữ) · T1 · P6.

## Tài liệu

`docs/specs/kachi-wallpaper.html` — §2 ràng buộc xe · §4.3 bẫy kinh điển · §5 số đo hai lượt · §6 còn tồn · §7 nhật ký
