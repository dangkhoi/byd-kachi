# Handoff — phiên 2026-09-11 (chiều): P9 bước nền (model bố cục động 12×6)

> **Trạng thái**: Session · **Ngày**: 2026-09-11 · **Mục đích**: giao lại điểm dừng của bước nền P9 và lý do chia 3 bước.

## Kết quả một dòng

Đã push (`5ee6654`). Full 5 module **2563 / 0 lỗi** (đầu phiên 2547 ⇒ **+16**). Chỉ **2 file mới** ở tầng lõi, **0 file cũ bị sửa**. Cây git sạch.

## Vì sao chia P9 thành 3 bước

P9 là việc lớn nhất còn lại ("mỗi người một launcher"), nhưng nó đụng **tầng vẽ** — chỗ phần *chiếu app vào ô* đang sống, và chỗ vừa được sửa hai lỗi khó (cập nhật tăng dần, app-không-hiện-lúc-mở). Làm cả P9 một lần trong lúc owner ngủ là rủi ro không cần thiết.

| Bước | Nội dung | Trạng thái |
|---|---|---|
| 1 | Model thuần: khung theo ô · kiểm tra · đổi sang pixel · lưu bền · **phép chứng minh tương đương** | ✅ xong |
| 2 | **Trình vẽ khung** cho người dùng (kéo tạo/đổi cỡ, hiện ô trống, tô đỏ chỗ đè) | chưa |
| 3 | Nối tầng vẽ + hồ sơ + nới trần số ô | chưa — **rủi ro cao**, đã giảm nhờ bước 1 |

Nguyên tắc bước 1: **cộng thêm, không sửa gì cũ**. Đo được: 2 file mới, 0 file cũ bị sửa.

## Sự thật làm đổi phạm vi

Câu hỏi phải trả lời trước khi viết code: **lưới thay thế hay cộng thêm** vào 5 bố cục sẵn có?

| Bố cục | Trên lưới 12×6 | Kết quả |
|---|---|---|
| 1 ô · 2 cột · 2 hàng · 4 ô | (0,0,12,6) · 6+6 · 3+3 · 2×2 | **đúng**, mỗi cái phủ 72/72 ô |
| **3 ô** | cột trái = 1.55/2.55 = 0.6078 bề ngang = **7.294 cột** | **không** — không phải số nguyên |

Ép về 7 cột lệch **2.45%** bề ngang, ép 8 cột lệch **5.88%**.

⇒ **Lưới là nguồn cộng thêm, không thay thế.** Năm bố cục sẵn có đã khớp pixel với prototype owner duyệt và đã proven trên máy ảo — viết lại chúng bằng lưới là làm xấu đi đúng cái đang đúng. Hàm tra trả **rỗng** cho "3 ô" thay vì làm tròn: làm tròn âm thầm là đổi bố cục owner đã chốt mà không ai biết.

## Phép chứng minh chịu lực — thứ đáng giá nhất của bước này

Test quét **4 bố cục × 5 cỡ màn × 5 khe hở = 100 tổ hợp**, đòi khung pixel ra **đúng từng pixel** như cách tính hiện tại. Có cả cỡ lẻ (1921×1081, 777×333) và khe lẻ (1, 7, 23) để bắt lỗi làm tròn.

Không có phép chứng minh này thì đổi tầng vẽ ở bước 3 là **đánh cược** — bố cục người dùng có thể xê dịch mà không ai phát hiện.

**Nó bắt lỗi ngay lần chạy đầu**: tôi dùng **làm tròn**, code cũ dùng **chia số nguyên (cắt)** ⇒ lệch **1 pixel** ở bố cục 2 cột, màn 1920 khe 1 (959 so với 960). Hình học giống nhau, chỉ quy tắc làm tròn khác — và 1 pixel là đủ để bố cục xê dịch.

## Luật kiểm tra — chỗ tôi lệch với backlog và vì sao

| Tình huống | Xử lý |
|---|---|
| Hai khung **đè nhau** | LỖI — và nói **đích danh cặp nào**, để trình vẽ tô đỏ được |
| Khung **ra ngoài lưới** | LỖI |
| Khung **nhỏ hơn 2×1 ô** | LỖI — 1 cột ≈ 160px trên màn 1920, không hiện nổi gì có nghĩa |
| **Còn ô trống** | **KHÔNG phải lỗi** |

Backlog liệt kê "không hở" vào mục kiểm tra, nhưng tôi cố ý **không** coi đó là lỗi: để chỗ trống là quyền của người dùng (nền vẫn hiện ra ở đó), chặn vì lý do đó là ép họ phủ kín màn. Vẫn **báo được** số ô trống cho trình vẽ hiển thị.

## Thử phá tìm ra code chết — lần thứ hai trong hai phiên

5 phép, 4 đỏ đúng chỗ. Phép thứ 5 — bỏ nhánh *"khung sát mép thì lấy trọn bề rộng"* — **không làm đỏ test nào**. Soi ra công thức **đã tự** cho ra đúng mép, và chứng minh được: `edge(count) − gap = total`, đúng cả với chia số nguyên.

Đã **bỏ nhánh** và thay bằng test khoá **chính tính chất** đó. Chứng minh tính chất tốt hơn viết một ca đặc biệt để né nó — và nó giải thích cho người đọc sau *vì sao* không cần ca đặc biệt.

## Còn tồn

- **Chưa có lượt soát độc lập** (tác nhân soát hỏng nhiều phiên gần đây). Bù lại: phép chứng minh 100 tổ hợp + 5 phép thử phá.
- Bước 2 và 3 chưa làm; bước 3 là chỗ rủi ro cao nhất còn lại của toàn dự án launcher.

## Chờ owner (tích lại từ nhiều phiên)

1. **Ngưỡng lốp** — số agent tự chọn.
2. **Gói "Rời xe"** có nên khoá xe khi đóng kính hỏng không (có đường thứ ba: đọc lại % độ mở để tự kiểm chứng).
3. **Nhịp chờ giữa các bước** gói lệnh: đang 400 ms.
4. **Icon** có cần phân biệt tới từng ô không.
5. **Vòng kiểm quyền**: backlog viết "lặp tới khi đủ" — tôi cố ý không lặp vô hạn.
6. **Mới**: bố cục "3 ô" không lên lưới được — có muốn thêm một bố cục sẵn **mới** gần giống mà đúng lưới (7+5 cột) không? Tôi **không tự thêm** vì nó khác thứ owner đã duyệt 2.45%.

## Việc trên xe

Quyền có mất sau khởi động lại máy không · lệnh dừng kính giữa hành trình · mã bảo trì gạt mưa · đèn đọc bật một hay tất cả · số áp suất/nhiệt lốp thật · lệnh lấy gió có ăn không · khoảng đứng bảng Tuỳ biến 187 ô.

## Thứ tự burn còn lại

P9 bước 2 (trình vẽ khung) → P9 bước 3 (nối tầng vẽ — rủi ro cao) → S1 (dựng lại màn Cài đặt, owner đã duyệt) · W5 (camera 360) → U4 · U5 (đa ngôn ngữ) · T1 · P6.

## Tài liệu

`docs/specs/kachi-dynamic-grid.html` — §2 sự thật đổi phạm vi · §4.3 phép chứng minh · §5 số đo · §6 nhật ký (nhánh dư) · §7 hai bước còn lại
