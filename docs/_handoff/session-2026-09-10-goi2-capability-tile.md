# Handoff — phiên 2026-09-10 (đêm): gói 2 launcher Kachi

> **Trạng thái**: Session · **Ngày**: 2026-09-10 · **Mục đích**: giao lại điểm dừng của gói 2 (ô khả năng hợp nhất · bảng lốp · tự lấy gió · chọn đơn vị) để phiên sau không phải đọc lại từ đầu.

## Kết quả một dòng

Gói 2 **DONE off-car, senior APPROVED, đã push** (`6a18447`). Full 5 module **2434 / 0 lỗi** (mốc trước gói 2 = 2343 ⇒ **+91**). Cây git sạch, local = remote.

## Owner đã chốt gì trong phiên

| Việc | Quyết định |
|---|---|
| Gói 2 | Duyệt làm: ô khả năng hợp nhất → bảng lốp → tự lấy gió |
| Đơn vị | **Cho người dùng chọn**, đừng ép ("mấy cái có đơn vị thì cho user chọn luôn") → làm TRONG gói 2 |
| Đa ngôn ngữ | **Tách thành việc riêng** (backlog U5), không nhập vào gói 2 |
| Cách chạy | Tự chủ, owner đi ngủ |

## Bốn sự thật nền đã ĐO (đừng suy luận lại)

1. **Ba** bộ đăng ký, không phải hai. **195 mã giao nhau RỖNG** cả 3 cặp ⇒ dùng một không gian mã phẳng, **khỏi chuyển đổi cấu hình người dùng đã lưu**. Đã có test khoá: thêm mã trùng là đỏ.
2. Ô giữa màn **đã** nhận được thông tin đọc từ trước; mã hành động vào ô thì ra ô vô dụng nhưng **không sập**.
3. **Cổng chặn thật chỉ là MỘT dòng** trong phần bật/tắt mục của thanh nút — nó bỏ qua **im lặng** mọi mã không phải nút. Đã nới, và **giờ có test canh**.
4. Thanh nút **không** nhận trạng thái xe (thanh trên thì có) ⇒ đã bơm vào.

## Ba lỗi mức cao mà senior review tìm ra (tôi làm sai)

| Lỗi | Vì sao nguy | Đã sửa |
|---|---|---|
| **Cổng chặn không có test nào canh** — hoàn nguyên đúng một dòng mà 2431 test vẫn xanh | Tài liệu tôi khai "mutation đỏ" là **SAI**. Thành quả chính của RW0 không được bảo vệ | Thêm test khoá; đã đính chính tài liệu |
| **Đổi đơn vị dựng lại TOÀN BỘ ô** | Nhả bộ chiếu ⇒ app đang chạy trong ô phải mở lại, chỉ vì đổi chữ "bar" thành "psi" | Chỉ dựng lại ô widget |
| **Ô widget chứa nút bị dựng lại theo nhịp trạng thái xe** | Trên xe 2 nhịp/giây ⇒ view bị tháo/gắn giữa cú chạm ⇒ **mất cú bấm**. Off-car không lộ vì mọi field null | Ô chỉ-chứa-nút không dựng lại theo nhịp |

## Bốn lỗi chỉ lộ khi ĐỌC ẢNH máy ảo

2434 test xanh **không** bắt được cái nào trong bốn cái này — bằng chứng test không thay được việc mở app ra xem:

1. **Nhiệt độ bảng lốp vẫn ghi °C** dù người dùng chọn °F (ô vẽ tự ghép ký hiệu cứng) — đúng loại lỗi gói này đi dọn.
2. **Hai nhãn ô đọc bị cắt thành y hệt nhau**: "Áp lốp trước-trái" và "…phải" đều thành "Áp lốp trước-t…".
3. **Một bánh hai màu cảnh báo** (số đỏ + dòng phụ hổ phách) ⇒ không rõ báo cái gì.
4. **Hình xe không ra hình xe**: khối gần vuông (0.85) + viền chênh nền 21/255 nên tan vào nền.

Chip trên thanh trên cũng từng bỏ qua lựa chọn đơn vị. Tất cả đã vá.

## Bất biến phải giữ (đừng phá ở phiên sau)

- **Lớp đơn vị là bước TRÌNH BÀY riêng**, không nhét vào đường đọc dữ liệu. Đường đọc đang bị test khoá ở đơn vị gốc của xe.
- **Đơn vị đích trùng đơn vị gốc ⇒ trả về view NGUYÊN VẸN**, không định dạng lại. Định dạng lại làm "28.5" thành "29" âm thầm.
- **Ngưỡng lốp nằm đúng MỘT chỗ** ở tầng lõi. Trước gói 2 dự án có **ba** ngưỡng lệch nhau; đừng tạo cái thứ tư.
- **Ô tick lấy gió nằm ở bề mặt dựng bằng code**. Đừng chuyển sang màn Cài đặt cũ — tệp layout đó bị niêm phong.
- **Không hứa lệnh lấy gió chạy được**: mã đó ở mức "đọc từ mã nguồn khác, chưa kiểm trên xe owner".

## Điểm dừng — làm gì tiếp

**Câu hỏi mở duy nhất cần owner**: ngưỡng cảnh báo lốp. Hiện đặt non &lt; 2.0 bar · căng &gt; 3.2 bar · lệch ≥ 0.3 bar — **số agent tự chọn**, không phải chuẩn nhà sản xuất. Đặt một chỗ, đổi một dòng.

**Nợ trên xe (nút thắt lớn nhất, một buổi test mở khoá cả loạt)**:
- Số áp suất + nhiệt độ **thật** từng bánh → chốt lại ngưỡng.
- Lệnh **lấy gió trong** có thật sự ăn không.
- Ô đọc trong thanh nút và ô hành động trong ô giữa màn khi có kênh điều khiển thật.
- **Khoảng đứng bảng Tuỳ biến**: nay dựng **187 ô** đồng bộ trên thread chính (trước gói 2 là 64) ≈ 600+ view không tái dùng. Vá đúng là việc của P9.

**Còn tồn (đã đo, không che)**:
- Nút đổi-app ở đầu ô **đè lên đỉnh hình xe** trong bảng lốp — chrome chung của mọi ô, sửa khi làm P9.
- Bảng lốp cho biết **bánh nào** có vấn đề nhưng chưa nói **sai cái gì** (không mũi tên, không chữ "thấp").
- Nhóm khả năng trong bảng Tuỳ biến dùng **một icon cho cả nhóm** ⇒ 10 ô cùng icon. Nợ U1 (~45 icon).

**Thứ tự burn đề xuất**: gói 3 = W2 (đủ hoá thanh action + **lớp gộp lệnh**, hiện chưa có) + W5 (camera 360 + tự gán phím vô-lăng) → gói 4 = P8 (vòng kiểm quyền) · S1 (dựng lại màn Cài đặt) → gói 5 = P9 (bố cục động 12×6) → lấp chỗ trống U1 · U4 · U5 · T1 · P6.

## Tài liệu liên quan

- Spec gói 2: `docs/specs/kachi-unified-capability-tile.html` (§6 số đo thật · §9 nhật ký + còn tồn · §10 reviewer log)
- Ảnh bằng chứng: `docs/prototypes/kachi-tyre-board-emulator.png` · `kachi-units-recirc-panel-emulator.png` · `kachi-rw0-capability-tiles-emulator.png`
- Backlog: `docs/PROJECT-BACKLOG.md` nhóm 3 (RW0 · W4 · W3 · W-unit đã ✅) + nhóm 4 (U5 mới)
