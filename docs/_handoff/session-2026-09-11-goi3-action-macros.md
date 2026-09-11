# Handoff — phiên 2026-09-11 (đêm): gói 3 launcher Kachi (lớp gộp lệnh)

> **Trạng thái**: Session · **Ngày**: 2026-09-11 · **Mục đích**: giao lại điểm dừng của gói 3 (W2 — lớp gộp lệnh) + nợ đã đóng + việc chờ trên xe.

## Kết quả một dòng

Lớp gộp lệnh **DONE off-car, đã push** (`afa109f`). Full 5 module **2482 / 0 lỗi** (mốc trước gói 3 = 2434 ⇒ **+48**). Cây git sạch, local = remote.

## Owner nói gì

"Làm tiếp được gì thì tự làm, tôi đi ngủ, autonomous." Tôi chọn W2 vì đó là thứ **duy nhất trong nhóm 3 còn thiếu hẳn trong code**, và là thứ kế tiếp trong thứ tự burn đã đề xuất.

## Ba sự thật đã ĐO (đừng suy luận lại)

1. **Không có khái niệm gộp lệnh nào trong code.** Trong 64 nút, mọi nút là một lệnh. Nên yêu cầu *"mở cửa + tắt/mở đèn"* của owner trước đây **không thể có** — không phải chưa làm, mà là không có chỗ để làm.
2. ⚠ **Nút mang nhãn "Kính 50%" không làm 50%.** Nó ghi **đúng cùng một lệnh** với nút kính cửa lái (chỉ đóng/mở). Nhãn hứa thứ xe không làm. Đã đổi thành **"Kính cửa lái"** và khoá **cả họ**: bất kỳ nhãn nút nào chứa dấu phần trăm là test đỏ.
3. **Đọc được** độ mở kính theo phần trăm (đã chạy trên xe) nhưng **không có lệnh ghi** phần trăm ⇒ *"kính ½ tất cả"* **không làm được off-car**.

## Bảy hành động owner nêu — tình trạng thật

| Owner muốn | Tình trạng |
|---|---|
| Cốp · đèn ban ngày | **Khỏi làm** — đã có nút sẵn |
| **Kính mở hết** | ✅ Xong (gói lệnh gộp 4 nút kính **đã chạy trên xe**, thay vì nút gộp chưa kiểm) |
| **"Mở cửa + tắt/mở đèn"** | ✅ Xong — đúng ca dùng chính của lớp gộp lệnh |
| Kính ½ tất cả | ⚠ **Chưa làm được** — thiếu lệnh *dừng giữa hành trình*. Đường đi đề xuất ở spec §4.5 |
| Bảo trì gạt mưa | 🚗 **Chưa có mã** — không bịa |
| Đèn đọc "tất cả" | 🚗 Chưa rõ nút hiện bật một đèn hay tất cả |

Nói trước để owner **không chờ đủ 7**.

## Bất biến phải giữ

- **Lớp gộp lệnh không có luồng.** Chờ và bơm lệnh nằm ở tầng app. Nhờ vậy test không phải chờ thời gian thật.
- **Mức bằng chứng của gói = thấp nhất trong các bước.** Lấy cao nhất là hứa quá. Có test khoá; đổi là đỏ.
- **Một bước hỏng không dừng gói.** Bỏ dở giữa gói còn tệ hơn (đóng 2 kính rồi dừng).
- **Mọi bước đi qua đúng cửa theo kiểu nút.** Đừng bắn tất cả qua một cửa — xem mục dưới.
- **Không gộp việc không đảo lại được.** Owner đã bỏ hết cổng an toàn, nên chỗ định nghĩa gói là nơi duy nhất còn tiết chế được.

## Lượt soát độc lập chết giữa đường — nhưng kịp vá 3 lỗi

Lượt soát **hỏng vì lỗi kết nối** trước khi viết báo cáo. Nó kịp áp 3 bản vá; tôi **tự kiểm chứng từng cái** rồi mới nhận:

| Mức | Lỗi | Vì sao nguy |
|---|---|---|
| Cao | Ô gói lệnh bắn **mọi bước qua một cửa** | Bước trỏ nút bước-nhảy bị nén thành bật/tắt; nút chọn mất chỉ số; nút bấm-một-phát với tham số 0 **không bấm gì**. Bốn gói hiện tại chưa lộ ⇒ **lỗi ngủ đông tới gói thứ năm** |
| Vừa | Cờ chống-bấm-kép **kẹt vĩnh viễn** nếu luồng chết trước lúc hạ cờ | Ô chết hẳn, bấm mãi không chạy |
| Vừa | **Một test của tôi là trang trí** — cắt vùng quét bằng mốc chú thích mà bộ quét đã bỏ hết chú thích | Quét sai vùng nhưng vẫn xanh |

Bản vá còn sửa một lỗi tôi **chưa nghĩ tới**: sau khi gói ghi thật, phải cập nhật lại bảng trạng thái dùng chung — không thì ô "Đèn đọc" vẫn sáng sau khi gói "Rời xe" đã tắt đèn.

## ⚠ Ba điểm CHƯA có ai soi độc lập

Vì lượt soát chết giữa đường. **Không tự tuyên bố đã duyệt** — phiên sau soi giúp:

1. Gọi lại giao diện sau khi ô đã bị tháo (đổi bố cục giữa lúc gói đang chạy).
2. Cách xếp mức bằng chứng dựa vào **thứ tự khai của enum** — thêm mức mới về sau có thể tính sai. (Đã có test khoá giả định đó, nhưng chưa ai soi ngoài tôi.)
3. Gói "rời xe" **khoá xe ở bước cuối dù bước đóng kính có thể đã hỏng**. Đúng theo luật "một bước hỏng không dừng gói", nhưng hệ quả thực tế là *khoá xe khi kính còn mở* — cần owner quyết.

## Nợ gói 2 đã đóng

Bảng lốp nay nói **sai cái gì** ("non" / "căng" / "lệch"), chữ đặt ở tầng lõi cùng chỗ với ngưỡng. Đo được: `"TT · căng · 82°F"` và `"SP · non · 93°F"`.

## Chờ owner

- **Ngưỡng lốp** (từ gói 2, chưa trả lời): non dưới 2.0 bar · căng trên 3.2 · lệch từ 0.3 — số agent tự chọn.
- **Gói "rời xe"** có nên khoá xe khi bước đóng kính hỏng không.
- **Nhịp chờ giữa các bước**: đang 400 ms, đặt một chỗ.

## Việc trên xe (mở khoá phần còn lại của W2)

1. **Kính có lệnh dừng giữa hành trình không** — mở khoá "kính ½ tất cả".
2. **Mã bảo trì gạt mưa** (nâng cần).
3. **Đèn đọc** bật một hay tất cả.
4. Các gói lệnh chạy đủ bước trên xe không, nhịp chờ bao nhiêu là đủ.

## Thứ tự burn còn lại

W5 (camera 360 + tự gán phím vô-lăng) → P8 (vòng kiểm quyền) · S1 (dựng lại màn Cài đặt) → P9 (bố cục động 12×6) → lấp chỗ trống U1 (~45 icon) · U4 · U5 (đa ngôn ngữ) · T1 · P6.

## Tài liệu

- Spec: `docs/specs/kachi-action-macros.html` (§2.2 ba sự thật · §4.5 đường đi cho kính ½ · §6 số đo · §9 nhật ký · §10 nhật ký soát)
- Ảnh: `docs/prototypes/kachi-tyre-reason-emulator.png`
- Backlog: `docs/PROJECT-BACKLOG.md` nhóm 3 (W2 nay ghi rõ 7 mục ở tình trạng nào)
