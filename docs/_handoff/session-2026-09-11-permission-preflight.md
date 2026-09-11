# Handoff — phiên 2026-09-11 (trưa): P8 vòng kiểm quyền

> **Trạng thái**: Session · **Ngày**: 2026-09-11 · **Mục đích**: giao lại điểm dừng của P8, và ghi lại hai bài học đáng nhớ nhất của phiên.

## Kết quả một dòng

Đã push (`8ec61c6`). Full 5 module **2547 / 0 lỗi** (đầu phiên 2503 ⇒ **+44**). Cây git sạch, local = remote.

## Vì sao chọn P8

Chính ghi chú trong backlog: *"làm sớm còn giúp giảm nhiễu khi test trên xe"*. Nợ-trên-xe là **nút thắt lớn nhất** của dự án — một buổi test mở khoá cả chục mục. Thêm nữa P8 chữa một lỗi **lặp lại nhiều phiên**: quyền hay mất sau khi khởi động lại, và mỗi lần lại chẩn đoán từ đầu.

## Làm được gì

Gom **6 điều kiện** launcher phụ thuộc về một chỗ: đọc thông báo · trợ năng · vẽ trên màn khác · cờ cửa sổ tự do · kênh điều khiển cửa sổ · là màn hình chính. Trước đó kiểm **rải rác ở 5 chỗ** và không nơi nào biết bức tranh toàn cảnh.

Bốn luật: **đủ thì im lặng** · **tự xin lại** cái tự xin được (không hỏi người dùng) · **không chặn launcher** (đây là màn hình chính của xe) · **không chỉ tới màn cài đặt hệ thống** vì màn đó bị khoá trên xe.

## Phát hiện đáng giá nhất — lời giải cho lỗi lặp lại

Backlog ghi *"gán phím vô-lăng hay mất quyền sau khi khởi động lại"*. Đo được:

| Bước | Danh sách trợ năng | Cờ |
|---|---|---|
| Sau khi cấp | có Kachi | 1 |
| **Tắt hẳn app**, không mở lại | **null** | **0** |

⇒ **Hệ thống tự thu hồi** khi tiến trình chết. Không phải app tự đánh mất. Vòng kiểm này **tự lành** mỗi lần mở launcher.

⚠ **Giới hạn của số đo**: tôi đo bằng **tắt hẳn app**, chưa đo bằng **khởi động lại máy**. Cơ chế khớp với hiện tượng owner báo, nhưng muốn chắc phải đo trên xe.

## Bài học 1 — hai guard kiến trúc bắt tôi làm sai

Guard từ phiên kiến trúc B0–B6 vừa **trả lãi**:

1. Tôi cho vòng kiểm ra **lệnh thô** bật cờ cửa sổ tự do → guard trạng-thái-bền đỏ ngay: dự án có luật **một nơi ghi duy nhất**, phía launcher **cấm ghi trực tiếp**.
2. Tôi sửa bằng cách cho **màn chính** gọi đường gieo → guard màn-chính-giữ-mỏng đỏ ngay: *"HOME must not seed freeform"*.

**Kết luận đúng**: vòng kiểm chỉ **báo**, đường khởi động mới **sửa**. Tôi thêm một loại phân loại mới — *"app tự lo, nhưng ở đường khởi động"* — và nó làm mô hình **đúng hơn** chứ không phải để lách guard: tới màn chính mà cờ vẫn thiếu nghĩa là việc gieo lúc khởi động **đã không ăn**, và đó là tin đáng nói.

## Bài học 2 — quét bảo mật tìm ra lỗi đúng-đắn, không chỉ rò rỉ

Lượt quét vốn đi tìm rò rỉ dữ liệu, nhưng nó đọc kỹ code cấp quyền và tìm ra **ba lỗi**, cả ba phá đúng cái bảo đảm tôi vừa tuyên bố *"không ghi đè trợ năng của app khác"*:

| Lỗi | Hậu quả thật |
|---|---|
| Nội suy giá trị vào lệnh shell **không bọc nháy** | Token có khoảng trắng ⇒ lệnh chỉ nhận phần đầu ⇒ **mất phần còn lại của danh sách dùng chung** |
| Đọc-sửa-ghi chỉ lọc đúng chữ `"null"` | Lệnh đọc trả về **câu lỗi** ⇒ câu lỗi bị ghép vào danh sách rồi ghi đè cấu hình hệ thống |
| Phép kiểm "trợ năng đủ" **bỏ qua cờ** | Ca "có trong danh sách nhưng cờ = 0" báo **đủ** trong khi trợ năng đang **tắt** — và đo được chính cờ đó bị về 0 khi tiến trình chết ⇒ **ca thật** |

Đã sửa cả ba và thêm **một lớp test riêng** cho chỗ này, vì làm sai là xoá trợ năng của app khác — kể cả của người khuyết tật đang dùng.

Sau đó **chứng minh bằng thực nghiệm**: đặt sẵn một service trợ năng của app khác + tắt cờ → mở launcher → service của app khác **được giữ nguyên**, Kachi nối thêm vào sau, cờ được bật. Việc này đóng luôn giới hạn *"chỉ khoá bằng test logic"* mà tôi đã tự ghi ra.

## Bài học 3 — thử phá còn tìm được code chết

Phép thử "đủ mà vẫn hiện thông báo" **không làm đỏ test nào**. Soi ra: dòng kiểm *đủ-thì-trả-rỗng* là **dòng chết** — vì khi đủ hết thì hai danh sách phía sau vốn đã rỗng. Đã bỏ. Trước giờ tôi dùng thử phá để tìm test mù; lần này nó tìm ra code không ai chạy tới.

Và: **một test của tôi báo nhầm chính code đúng** (so chuỗi literal trong khi lệnh dùng hằng số nội suy). Đã sửa cho nó khoá **ý định** thay vì cách viết.

## Còn tồn

- Vòng kiểm chỉ chạy **lúc mở launcher**. Quyền mất trong lúc đang chạy thì phải mở lại mới tự lành.
- Số đo thu hồi quyền làm bằng **tắt hẳn app**, chưa phải **khởi động lại máy**.
- **Chưa có lượt soát độc lập** — tác nhân soát hỏng nhiều lần trong hai phiên gần đây (lỗi kết nối, hệ thống quá tải). Phần quét bảo mật thì có tác nhân độc lập làm, và chính nó tìm ra ba lỗi ở trên.

## Chờ owner (tích lại)

1. **Ngưỡng lốp** (gói 2) — số agent tự chọn.
2. **Gói "Rời xe"** có nên khoá xe khi đóng kính hỏng không (OQ5, có đường thứ ba: tự kiểm chứng bằng cách đọc lại % độ mở).
3. **Nhịp chờ giữa các bước** gói lệnh: đang 400 ms.
4. **Icon**: có cần phân biệt tới từng ô không.
5. **P8/OQ3**: backlog viết *"lặp tới khi đủ"* — tôi cố ý **không lặp vô hạn**; nếu owner muốn lặp thì cần trần số lần + khoảng nghỉ.

## Việc trên xe

Quyền có mất sau **khởi động lại máy** không và vòng kiểm có tự lấy lại được · lệnh dừng kính giữa hành trình · mã bảo trì gạt mưa · đèn đọc bật một hay tất cả · số áp suất/nhiệt lốp thật · lệnh lấy gió có ăn không · khoảng đứng bảng Tuỳ biến 187 ô.

## Thứ tự burn còn lại

S1 (dựng lại màn Cài đặt) · W5 (camera 360 + tự gán phím) → P9 (bố cục động 12×6, việc lớn nhất còn lại) → U4 · U5 (đa ngôn ngữ) · T1 · P6.

## Tài liệu

`docs/specs/kachi-permission-preflight.html` — §2.2 sáu điều kiện · §6 số đo (gồm hai phép đo trên máy ảo) · §9 nhật ký (hai guard + ba lỗi quét bảo mật tìm ra)
