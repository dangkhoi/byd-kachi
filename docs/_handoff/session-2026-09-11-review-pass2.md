# Handoff — phiên 2026-09-11 (đêm-2): vá tiếp phần còn tồn của lượt soát

> **Trạng thái**: Session · **Ngày**: 2026-09-11 · **Mục đích**: giao lại ba bản vá tiếp và một hồi quy tôi tự gây.

## Kết quả một dòng

Đã push (`3143e82`). **2654 / 0 lỗi** (+15). Ba mục tôi để lại ở lượt soát đã vá xong.

## Ba mục đã vá

**1. Đọc và giải mã ảnh nền sang thread nền.** Trước đây tạo thư mục, quét thư mục và hai lượt giải mã đều chạy trên thread chính **ngay trong lúc về màn chính** ⇒ đứng hình mỗi lần về HOME. Nay chạy ở thread nền có sẵn, chỉ đưa ảnh vào màn hình trên thread chính.

**2. Cổng giảm cỡ ảnh.** Phép giảm cũ dừng khi *một* chiều sắp nhỏ hơn khung, nên ảnh tỉ lệ lệch vẫn ra bitmap to: ảnh 12000×9000 cho khung 1920×1080 ra **27 MB**, mà lúc đổi ảnh có **hai** ảnh cùng sống. Nay hạ đúng khung **ngay trong lúc giải mã** (không tạo ảnh trung gian, vì làm thế lại cần hai ảnh cùng sống). Còn **~11 MB**, và vẫn trùm đủ khung.

**3. "Hiện ngay ảnh đầu tiên" thật ra hiện ảnh thứ hai.** Mỗi lần về màn chính nó lại bắt đầu từ ảnh #2, nên anh **không bao giờ** thấy ảnh đầu ở đầu vòng. Hai test cũ đang khoá đúng hành vi sai đó — đã sửa cho khớp ý định.

## ⚠ Bản vá đầu của tôi tự gây một hồi quy

Quét bảo mật tìm ra: tôi dùng **một** thẻ đánh dấu chung cho **hai** việc chạy nền (quét thư mục và giải mã ảnh). Nhịp trình chiếu tăng thẻ trước lúc lượt quét về ⇒ **lượt quét bị bỏ oan** ⇒ ảnh anh mới thêm, ảnh vừa xoá, chu kỳ vừa đổi đều **không được nhận**. Và lỗi đó **im lặng** — chỉ thấy "sao thêm ảnh mà không hiện".

Đã tách thành hai thẻ riêng, kèm test khoá. Cũng dọn một trường đã thành **mã chết** sau khi vá.

Đây là lần thứ hai trong ngày một bản vá của tôi sinh ra lỗi mới, và cả hai lần **đều do người/công cụ khác tìm ra chứ không phải tôi**. Ghi lại để phiên sau biết là bản vá cũng cần soát, không chỉ code mới.

## Hai test cũ vỡ vì lý do đáng nhớ

Chúng kiểm **vị trí dòng** làm đại diện cho luật ("nạp nguồn ảnh trước cổng bật/tắt"). Tôi đổi cấu trúc sang thread nền thì vị trí đổi, **luật vẫn đúng** nhưng test vỡ. Đã viết lại cho kiểm **luật**: nhánh "nền đang tắt" không được thoát hàm, và lệnh tạo thư mục phải chạy vô điều kiện.

## Đo được

Sau khi chuyển thread: nền vẫn **nét** (biên độ 120, 24 vạch lưới) và vẫn **đổi ảnh** (ảnh xám lệch đỏ-xanh 0 → ảnh cam +47), không ngoại lệ nào.

## Còn chưa vá

- **Widget trình chiếu vẫn giải mã ảnh trên thread chính.** Lượt soát chỉ nói hình nền, nhưng nói cho đủ thì widget cũng nên chuyển.
- Nút Back không đóng bảng vẽ bố cục · chưa xử nhiều ngón tay · vài điểm vụn.

## Chờ owner

Vẫn nguyên danh sách của phiên trước, trong đó cái mới nhất và dễ thấy nhất: **ô trống có nên để thấy hình nền không** — hiện ô trống đục hoàn toàn nên giữa màn gần như không thấy nền.

## Thứ tự burn còn lại

Ba mục P3 nhỏ ở trên → **S1** (dựng lại màn Cài đặt, anh đã duyệt 09-09) · W5 → U5 · T1 · P6.

## Tài liệu

`docs/specs/kachi-wallpaper.html` §10 Reviewer Log Pass 2
