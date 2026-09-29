# Handoff — 27/09/2026: chốt 2.79 (180), dự án đã nghiệm thu trên xe

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 (chiều) · **Mục đích**: mở file này ra là biết ngay **đang ở đâu · lát lên xe làm gì · tháng sau làm gì**. Thay `session-2026-09-27-275-after-car.md` (đã Superseded). Đánh giá đóng dự án: `docs/CLOSEOUT-2026-09-27.md`.

> ⚠ **ĐÃ QUA MỘT PHIÊN — đọc trước khi tin bảng dưới (2026-09-28).** Bảng §1 là trạng thái **chiều 27/09**. Nay: bản mới nhất là **2.81 (182)** (`apk/Kachi-2.81-release.apk`, **chưa đăng OTA** ⇒ kênh `main` và xe owner **vẫn 2.79 (180)**); **2.80 (181) đã rút và xoá** khỏi `apk/` vì lượt soát tìm ra 2 × [P0]. Dòng *"Không còn việc trên xe"* **hết đúng**: hai tính năng vào sau mốc nghiệm thu và **mỗi cái còn nợ một phép đo trên xe** — (1) tự chữa mối nối dịch vụ Hỗ trợ bị kẹt ⇒ để xe qua đêm rồi đọc nhật ký ở *Cài đặt › Chiếu cụm › Chẩn đoán* (`specs/kachi-a11y-bind-stuck-autofix.html` §6.2 V-oncar-1); (2) chọn nguồn camera hai bên ⇒ dò số camera nào lên hình trên Sealion 6 (`specs/kachi-camera-source-picker.html`). [ĐO 2026-09-28] `:app` 1 520 + `:core` 3 002 = **4 707 bài / 0 lỗi**, lint 0. Nhật ký từng bản: `apk/README.md`; task: `docs/PROJECT-BACKLOG.md`.

## 1. Đang ở đâu

| | |
|---|---|
| Kênh OTA `main` | **2.79 (180)** · `apk/Kachi-2.79-release.apk` · sha256 `8277ebef…0f460` · commit `0846ca5` |
| Xe owner (Seal) | **2.79 (180)** — cài và **nghiệm thu bằng mắt ngay trên xe** tối 27/09 |
| Off-car | **sạch** — test 4 646/0 · lint 0 · 0 tệp > 500 dòng · review Opus Pass 1 APPROVED (15 phát hiện, 14 vá) · quét bảo mật 0 BLOCK |
| Buổi xe closing | **ĐÃ CHẠY** — [P0] BufferQueue 0 dòng/917k · giật 11,15 → 0,81 % · PSS 75 → 57 MB · 64/66 datum đọc thật · 0 crash. Kết quả đầy đủ: backlog dòng `ONCAR-2026-09-27 CHIỀU` |
| Còn lại | **Không còn việc trên xe.** Off-car: hướng dẫn có hình (ảnh máy ảo). Tháng sau: phase 2 §2b |

## 2. Buổi xe tối 27/09 — ĐÃ XONG, không còn việc trên xe

Owner cài từng bản ngay tại chỗ và nghiệm thu bằng mắt: 2.77 → *"cắt rát quá"* · 2.78 → *"chỉ là 1 đường thẳng thôi mà, khác gì chữ nhật đâu"* · **2.79 → *"tôi thấy OK hết rồi, ko có gì phải nghĩ nữa"*** rồi *"ok hết rồi, ko còn cần làm gì"*.

Hồi quy tự động chạy lại trên chính 2.79 qua cầu adb: lỗi [P0] `BufferQueue abandoned` **0 dòng** sau 6 lượt đổi bên · **0 crash** · launcher **72 MB** · hình học khớp thiết kế (`cửa=641x428 tại=19,132 phóng=112% mất=11%`).

⚠ Nếu lần sau cần nối adb: IP của xe **đổi theo mạng**, hỏi owner rồi dựng cầu `nc` (xem memory `kachi-adb-car-tunnel`); đừng chép IP vào tệp theo dõi.

## 2b. Bốn việc phase 2 (KHÔNG cần làm lần này)
**Tự cập nhật không bao giờ tự chạy lại** — [ĐO 27/09 tối] cổng `AutoUpdateOnce.claim()` cho đúng MỘT lượt dò mỗi tiến trình, mà launcher sống hàng ngày ⇒ đẩy bản mới giữa chừng thì xe không tự thấy; đường tự động lại im lặng. **Cách lấy bản mới hôm nay: bấm tay** *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật*. Việc tháng sau: dò theo chu kỳ / mỗi lần nổ máy + một dấu hiệu nhìn thấy được.

Còn lại, nếu tiện thì liếc, không thì bỏ: Netflix mở từ launcher ZIN có tự thoát y hệt không (30 giây, chốt việc Kachi không dính) · tên app ngoại trong giọng nói (*"mở netflix"* ra *"nep leag"*) · app tự mở màn thứ hai không nằm được trong ô (đã chứng minh là cổng Android, không sửa được bằng app thường).

## 3. Sau buổi xe — 13 câu chỉ owner trả lời được
Ghi thẳng vào backlog, đừng để trong đầu: 1) hướng ảnh camera gương: đứng hay xoay, mỗi bên · 2) lật gương bật hay tắt mặc định · 3) giá trị *Dịch khung* trái/phải · 4) có đổi mặc định sang *Một camera* không · 5) hai getter AUTO có lệch nhau không (chốt gộp chip) · 6) khối thẻ lốp nhìn đã cân chưa · 7) `camera_hal_mode` 6 (lật ngang toàn hệ) · 8) `setPreviewSize` · 9) fixture `offcar-planner` có nhận revision 3 không · 10) 27 datum chưa có đường đọc: làm hay bỏ · 11) chính sách hoàn nguyên nút khi ghi hỏng · 12) ba màn dev đã chết (`CapTestConsole`…): xoá hay nối lại sau cổng kiểm thử · 13) **luật §11**: bản ship không còn bề mặt chẩn đoán không-adb — chấp nhận hay nối lại.

## 4. Tháng sau — thứ tự đề xuất

1. **Nếu buổi xe có mục đỏ** → sửa trước, bump 2.77, OTA. Runbook ghi sẵn "nếu sai chép gì" cho từng mục.
2. **Nếu xanh hết** → cập nhật mặc định `ClusterProfile` theo câu 1–3 (hướng ảnh · lật gương · dịch khung) rồi bump 2.77. Đây là việc off-car cuối cùng của giai đoạn này.
3. **Vòng thiết kế đáng làm nhất**: câu ghép giọng nói rụng vế — [ĐO] 1 342/3 000 ca thiếu vế mà không báo. Không phải lỗi an toàn, nhưng là giới hạn lớn nhất còn lại của phần giọng nói.
4. Còn lại trong backlog, không ai hứa, an toàn bỏ hẳn: CLOSE-1 C1–C10 · CapTest v2 · 16 chip teo chữ · helper HAL không tự thoát · dọn mã chết Vosk · di trú mô hình fp32 → int8.

## 5. Đọc gì trước khi chạm code (thứ tự)
`.kiro/steering/project-context.md` → `docs/README.md` (INDEX) → `docs/PROJECT-BACKLOG.md` (nguồn task duy nhất) → spec liên quan trong `docs/specs/` → file này. Luật viết code và luật phiên làm việc ở `CLAUDE.md` + 5 file `.kiro/steering/`.

## 6. Bẫy công cụ đã dính, đừng dính lại
- Cầu kiểm thử: lệnh **đọc** pref là `prefs --es file clusternav_prefs`; **không có** `prefs_get`.
- `hal get` cho getter **không tham số** thì **bỏ** `--es args` (truyền `args 0` ⇒ tìm chữ ký `(int)` ⇒ `unavailable`).
- Đổi pref camera chỉ áp **sau** `camera none` → `camera left` (dựng lại overlay).
- Chế độ kiểm thử **tắt** sau mỗi lần launcher khởi động lại.
- Log: dòng `quyết định … không hiểu: MISMATCH` in **trước** nhánh hỏi lại — không có nghĩa Kachi bỏ cuộc (2.75 thêm dòng `hỏi lại:` cho rõ).
- Nhiều agent chạy gradle song song ⇒ luôn qua `scratchpad/gradle-locked.sh`, và **đếm test từ XML**, đừng tin grep console.

## 7. Sự cố 27/09 tối — 7 ảnh xe lọt repo public ~10 phút (ĐÃ GỠ)

Tôi chép ảnh xe vào `docs/guide/img/` cho hướng dẫn; `git check-ignore` báo *"không bị chặn"* và tôi coi đó là **giấy phép** thay vì **cờ đỏ**. Lọt lên `main` công khai trong một commit (đã ghi đè; SHA gửi riêng owner, không ghi ở tệp public): **biển số một xe của người khác**, 3 ảnh cụm hiện **bản đồ sống chỉ đúng vị trí + giờ**, một ảnh đọc được **số nhà** + **avatar tài khoản Google**, 2 ảnh màn hình có **YouTube đang đăng nhập** + telemetry xe.

Đã xử lý: gỡ → `--amend` → `push --force-with-lease` cả hai nhánh → xác minh remote sạch (3 ref đều `4297555`, rồi `e720009`) → `.gitignore` chặn **theo mẫu tên tệp, độc lập đường dẫn** (`car-*.png` · `cum-*.png` · `camera-frame-*.png` · `cluster-fb-*.png` · `**/img/car-*.png`).

**Việc của owner nếu muốn chắc chắn**: commit bị ghi đè (SHA gửi riêng owner) có thể vẫn truy cập theo SHA tới khi GitHub dọn rác — còn truy cập được không: [CHƯA BIẾT] ⇒ mở ticket GitHub Support xin purge (backlog `BACKLOG-LOC-SCRUB`). Phần đáng gỡ triệt để nhất là **biển số của người thứ ba**.

**Ba luật rút ra, đã ghi backlog + memory**: (a) `check-ignore` trả *"không chặn"* cho một tệp dữ liệu xe là **cờ đỏ**; (b) lưới chặn ghim theo thư mục không bảo vệ được thư mục **chưa tồn tại**; (c) 6 scanner song song bỏ sót vì không ai được giao đọc **thư mục mới** — chỉ vòng **critic** bắt được, nên vòng ấy là bắt buộc.
