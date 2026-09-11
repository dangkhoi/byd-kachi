# Handoff — phiên 2026-09-11 (đêm): lượt soát độc lập đầu tiên

> **Trạng thái**: Session · **Ngày**: 2026-09-11 · **Mục đích**: giao lại kết quả lượt soát và các bản vá.

## Kết quả một dòng

Đã push (`9213a4d`). **2639 / 0 lỗi** (+11 test). Đóng món nợ tôi đã ghi sáu lần: bố cục động và hình nền **giờ đã có người thứ ba soi**.

## Vì sao tôi chọn việc này thay vì mở việc mới

Năm gói việc gần đây đều mang dòng "chưa có lượt soát độc lập". Tác nhân soát từng hỏng nhiều lần, nhưng mấy phiên nay nó chạy được (quét bảo mật và đọc ảnh đều thành công). Nên trước khi mở việc lớn tiếp theo, tôi đem 9 tệp / 1233 dòng code chưa ai soi ra soát.

Tôi yêu cầu soát viên **chỉ báo cáo, không sửa file** — để tôi tự vá và **tự kiểm từng cái**, thay vì nhận một mớ thay đổi mình không thấy.

Kết quả: **0 lỗi mức P0 · 4 P1 · 4 P2 · 4 P3.**

## ⚠ Phần quan trọng nhất: một tuyên bố của tôi bị bác

Phiên trước tôi viết trong tài liệu rằng giả thuyết "ô bị dựng lại liên tục" đã **bị số đo bác**. **Sai.**

Phép đo đó chạy **off-car**, nơi trạng thái xe luôn rỗng nên điều kiện "trạng thái đổi" **luôn là false** — tức ca đó **không thể xảy ra khi tôi đo**. Tôi chỉ chứng minh được *off-car không xảy ra*.

Và giả thuyết **đúng**: bảng tra khả năng trả "có nội dung đọc" cho **mọi** widget dựng tay, kể cả widget trình chiếu ảnh. Trên xe trạng thái đổi 1 nhịp/giây ⇒ ô bị dựng lại **mỗi giây** ⇒ trạng thái quay vòng bị đặt lại nên **ảnh đứng mãi ở một tấm**, cộng mỗi giây một lượt đọc tệp và giải mã ảnh trên thread chính.

**Bài học kép**: phần "nghi phép đo trước khi nghi code" là đúng, nhưng tôi rút **kết luận sai** từ đó. Đúng ra phải kết luận *"off-car không kiểm được ca này"* chứ không phải *"code không sao"*.

## Bốn lỗi P1 và cách tôi kiểm chứng từng cái

| Lỗi | Kiểm chứng |
|---|---|
| Widget trình chiếu bị dựng lại mỗi giây trên xe | Đọc mã bảng tra + test đúng ca trạng-thái-đổi (ca off-car không dựng được) |
| **Hình nền lần mở đầu giải mã ở cỡ 1×1** ⇒ vệt màu loang tới 30 phút | **Chứng minh hai chiều**: có vá ⇒ biên độ 116, 27 lần đổi sáng/tối; hoàn nguyên ⇒ biên độ **0**, ảnh biến mất |
| Bộ sắp cửa sổ app còn quét cứng 4 ô sau khi tôi nới trần lên 6 | Đọc mã: dòng trên dùng số ô thật, dòng dưới viết cứng `0..3` |
| Đổi hồ sơ tài xế không nạp lại bố cục ⇒ **ghi đè mất** bố cục hồ sơ khác | Đọc mã: chỉ có đúng một chỗ đọc, nằm trong lúc khởi động |

Lỗi thứ ba đáng nhắc riêng: **nó do chính bước 3 của tôi gây ra**, và **test canh tôi tự viết ở bước 2 không bắt được** vì nó chặn tên hàm mà không chặn số cứng. Nay chặn cả hai dạng.

Lỗi hình nền cũng đáng nhắc: phép đo cũ của tôi dùng **ảnh đơn sắc**, mà ảnh một điểm kéo to lên trông **y hệt** ảnh thật. Ảnh đơn sắc che được cả méo hình lẫn mất chi tiết — lần này tôi dùng ảnh lưới sọc.

## Còn chưa vá — ghi rõ, không che

- **Đọc và giải mã ảnh vẫn nằm trên thread chính**, ngay lúc về màn chính ⇒ có thể đứng hình. Cần đưa sang thread nền — việc riêng.
- **Cổng giảm cỡ yếu với ảnh tỉ lệ lệch**: ảnh 12000×9000 vẫn ra bitmap 27 MB.
- "Hiện ngay ảnh đầu tiên" thật ra hiện **ảnh thứ hai** (lệch chính tài liệu của tôi).
- Nút Back không đóng bảng vẽ · chưa xử nhiều ngón tay.

## Một phát hiện ngoài danh sách lỗi — cần anh quyết

**Ô trống che kín hình nền.** Đo được: thẻ giữa màn và ô trống cho **0%** nền lọt qua, nên giữa màn hình gần như **không thấy hình nền** — chỉ thấy ở lề và mép dưới (thanh trên 17%).

Nghĩa là nếu anh bật hình nền mà workspace đang kín ô, anh sẽ thấy rất ít. Ô trống có nên trong suốt để thấy nền không, đó là quyết định của anh nên tôi không tự đổi.

## Chờ owner (cộng dồn)

1. **Ô trống có nên để thấy hình nền không** (mới, ở trên).
2. Trần 6 khung có hợp không · ô lưới không vuông chấp nhận không · thứ tự khung có cần đổi được không.
3. Ngưỡng lốp · gói "Rời xe" khoá xe khi đóng kính hỏng · nhịp chờ 400ms · icon tới từng ô · vòng kiểm quyền có lặp không · thanh trên có cần tăng độ đục · widget và nền chung chu kỳ hay tách.

## Thứ tự burn còn lại

Ba việc chưa vá ở trên (nhỏ) → **S1** (dựng lại màn Cài đặt, anh đã duyệt 09-09) · W5 → U5 · T1 · P6.

## Tài liệu

`docs/specs/kachi-wallpaper.html` §7 (đính chính tuyên bố sai) + §10 Reviewer Log · `docs/specs/kachi-dynamic-grid.html` §Reviewer Log
