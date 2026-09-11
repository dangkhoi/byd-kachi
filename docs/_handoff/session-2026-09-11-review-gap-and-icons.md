# Handoff — phiên 2026-09-11 (sáng): đóng lỗ hổng soát xét gói 3 + U1 icon

> **Trạng thái**: Session · **Ngày**: 2026-09-11 · **Mục đích**: giao lại điểm dừng. Phiên này **không làm việc mới lớn** — ưu tiên đóng nợ soát xét trước, rồi chữa một lỗi dùng-được đã đo.

## Kết quả một dòng

Đã push (`4eec440`). Full 5 module **2503 / 0 lỗi** (đầu phiên 2482 ⇒ **+21**). Cây git sạch, local = remote.

## Vì sao chọn hai việc này

Gói 3 ship ra với **lỗ hổng soát xét ghi rõ trong tài liệu** — 3 điểm chưa ai duyệt. Làm việc mới đè lên nền chưa soát thì tệ hơn là đóng nó. Sau đó làm U1 vì đó là lỗi **đã đo được** (bảng chọn có hàng 5 ô trông y hệt nhau), không phải phỏng đoán.

## Phần 1 — ba điểm gói 3, giờ đã có kết luận

⚠ **Tác nhân soát hỏng 2 lần** trong phiên này (lỗi kết nối, rồi hệ thống quá tải). Tôi tự soi, nhưng **bằng thực nghiệm và thử phá**, không bằng suy luận. Ghi rõ để không ai tưởng đây là lượt soát của người thứ ba.

| Điểm | Kết luận | Xử lý |
|---|---|---|
| Gọi lại giao diện sau khi ô đã bị tháo | **LỖI THẬT** (mức cao) — và kèm một lỗi **thứ hai chưa ai thấy**: bảng trạng thái dùng `HashMap` thường mà gói lệnh ghi từ luồng nền ⇒ tranh chấp dữ liệu | Đưa việc ghi trạng thái **ra khỏi** chỗ phụ thuộc view + đổi sang map đồng thời. **Thiết kế nay không cần biết** ô đã tháo thì lời gọi kia có chạy hay không — thứ mà tài liệu Android không nói rõ |
| Phụ thuộc thứ tự khai enum | **Không phải lỗi** — test có khoá thật (thử phá: đảo hai giá trị là đỏ). Chỉ thiếu ghi giao kèo tại chỗ khai | Ghi giao kèo vào ngay đầu enum. Không đổi code |
| Gói "Rời xe" khoá xe dù đóng kính có thể hỏng | **Quyết định của owner**, không phải lỗi kỹ thuật — hai luật của chính spec xung đột đúng ở ca này | **Không tự đổi.** Ghi thành **OQ5 có ưu tiên**, kèm đường thứ ba tốt hơn cả hai |

**Nhưng tìm ra một lỗi thật đi kèm điểm 3**: kết quả gói chỉ vào **nhật ký**, mà người lái không bao giờ đọc nhật ký. Bấm "Rời xe" xong xe khoá mà kính chưa đóng thì **không có gì nói cho người ta biết**. Đã thêm thông báo ngắn gọi bước hỏng theo **nhãn**; thành công thì **im lặng**.

**Đường thứ ba cho OQ5** (đáng cân nhắc): xe cho **đọc** độ mở từng kính theo phần trăm, ở mức **đã chạy trên xe**. Nên gói có thể **tự kiểm chứng** (đóng kính → đọc lại % → chỉ khoá khi thật sự đã đóng) thay vì tin vào kết quả ghi. Cần thêm khái niệm "bước có điều kiện" vào lớp gộp lệnh ⇒ ngoài phạm vi gói 3.

## Phần 2 — U1 icon

| Đo được | Trước | Sau |
|---|---|---|
| Icon phân biệt trên 123 mục đọc | **7** | **29** (gấp 4,1 lần) |
| Nhóm năng lượng (28 mục) | 1 icon | 6 icon |
| Nhóm an toàn (20 mục) | 1 icon | 7 icon |
| Ô trống icon / icon lỗi | — | **0 / 0** (đo trên ảnh máy ảo, 30 ô) |

Hai điều đáng nhớ:
- **59 tệp icon đã tồn tại mà chỉ 29 được nối** — phần lớn việc là **nối lại**, chỉ 6 khái niệm thật sự phải vẽ mới.
- **Bẫy**: tiền tố **dài phải xét trước** tiền tố ngắn. Nhiệt lốp và áp suất lốp cùng bắt đầu bằng "lốp"; xếp sai thì nhiệt lốp ra icon lốp. Có test khoá — và đây cũng là ví dụ rõ nhất của việc icon mang thông tin.

## Phần 3 — lỗi phát sinh từ gói 2, sửa luôn

Gói 2 gộp thông tin đọc và nút bấm vào **một lưới**, làm lộ **18 nhãn trùng** (hai ô đều ghi "Kính trước-trái": một để **xem** độ mở %, một để **bấm** đóng/mở). Trước gói 2 hai loại ở hai màn khác nhau nên trùng không sao.

Sửa: thêm gợi ý loại (**· xem** / **· bấm** / **· thẻ**) **chỉ ở chỗ bị trùng**; nhãn **gốc** không đổi.

⚠ **Bản sửa đầu của tôi chưa đủ**: nó chỉ xét trùng *giữa hai loại*. Một **test toàn cục** ("sau khi phân biệt thì 0 nhãn trùng") bắt ra ca trùng **cùng loại** mà tôi không nghĩ tới — widget "Tốc độ" và mục đọc "Tốc độ", cả hai đều là đọc. Đây là **lần thứ hai** trong dự án một phép kiểm toàn cục bắt được thứ mà phép kiểm theo từng ca bỏ sót. Đáng nhớ khi viết test.

## Còn tồn (đã đo, không che)

- **Icon phân biệt theo HỌ, chưa theo từng ô**: tia sét vẫn dùng cho **11 ô** trong nhóm năng lượng. Đi từ "vô dụng" lên "hữu dụng ở mức phân loại họ".
- **Cặp đối lập chưa phân biệt**: "Nhiệt cell cao" và "Nhiệt cell thấp" cùng một nhiệt kế; "Còn (giờ)" và "Còn (phút)" cùng một đồng hồ. Đây đúng là chỗ icon đáng lẽ giúp nhất → OQ1.
- **Nhãn hàng cuối bảng Tuỳ biến bị viewport cắt ~4px** (mất dấu thanh dưới). Lỗi bố cục bảng.
- **U1 chưa có lượt soát độc lập** (tác nhân hỏng 2 lần). Phần xác minh **bằng mắt** thì đã có tác nhân độc lập làm — và chính nó tìm ra hai lỗi ngoài icon ở trên.

## Chờ owner (tích lại từ các phiên trước)

1. **Ngưỡng lốp** (gói 2): non dưới 2.0 bar · căng trên 3.2 · lệch từ 0.3 — số agent tự chọn.
2. **OQ5 — gói "Rời xe"**: có nên khoá xe khi bước đóng kính thất bại không? (đường thứ ba: tự kiểm chứng bằng cách đọc lại %)
3. **Nhịp chờ giữa các bước** trong gói lệnh: đang 400 ms.
4. **OQ1 — icon**: có cần phân biệt tới từng ô không (thêm ~10 biến thể)?

## Việc trên xe

Lệnh dừng kính giữa hành trình (mở khoá "kính ½ tất cả") · mã bảo trì gạt mưa · đèn đọc bật một hay tất cả · số áp suất/nhiệt lốp thật · lệnh lấy gió có ăn không · khoảng đứng bảng Tuỳ biến 187 ô.

## Thứ tự burn còn lại

W5 (camera 360 + tự gán phím vô-lăng) → P8 (vòng kiểm quyền) · S1 (dựng lại màn Cài đặt) → P9 (bố cục động 12×6) → U4 · U5 (đa ngôn ngữ) · T1 · P6.

## Tài liệu

- `docs/specs/kachi-capability-icons.html` — U1 (§6 số đo · §9 nhật ký)
- `docs/specs/kachi-action-macros.html` §10 **Pass 2** — kết luận 3 điểm + §7 OQ5
