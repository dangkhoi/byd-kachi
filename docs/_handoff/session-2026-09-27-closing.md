# Handoff — 27/09/2026: chốt 2.77 (178) sau buổi xe closing

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 (chiều) · **Mục đích**: mở file này ra là biết ngay **đang ở đâu · lát lên xe làm gì · tháng sau làm gì**. Thay `session-2026-09-27-275-after-car.md` (đã Superseded). Đánh giá đóng dự án: `docs/CLOSEOUT-2026-09-27.md`.

## 1. Đang ở đâu

| | |
|---|---|
| Kênh OTA `main` | **2.77 (178)** · `apk/Kachi-2.77-release.apk` · sha256 `2f41abeb…b699f` · commit `e720009` |
| Xe owner (Seal) | **2.76 (177)** — buổi xe closing chiều 27/09 chạy trên bản này; 🚗 **chưa cài 2.77** |
| Off-car | **sạch** — test 4 646/0 · lint 0 · 0 tệp > 500 dòng · review Opus Pass 1 APPROVED (15 phát hiện, 14 vá) · quét bảo mật 0 BLOCK |
| Buổi xe closing | **ĐÃ CHẠY** — [P0] BufferQueue 0 dòng/917k · giật 11,15 → 0,81 % · PSS 75 → 57 MB · 64/66 datum đọc thật · 0 crash. Kết quả đầy đủ: backlog dòng `ONCAR-2026-09-27 CHIỀU` |
| Còn lại | **5 phút trên xe cho 2.77** (xem §2) + phase 2 tháng sau + nợ không ai hứa (CLOSEOUT §4) |

## 2. LẦN LÊN XE CUỐI — 5 phút, chỉ để nghiệm thu 2.77

Buổi closing đã xong trên 2.76. Bản 2.77 chỉ đổi **hai thứ nhìn bằng mắt**, chưa ai thấy trên xe:

1. Cài đặt → nhận **2.77**, chốt `versionCode` 178.
2. *Cài đặt › Tiện nghi xe › Camera* — đếm đúng **10 hàng**, **không còn** khối *Nâng cao (kỹ thuật)* và **không còn** hàng *Nguồn*. (15 núm kỹ thuật vẫn đọc/ghi được qua cầu kiểm thử: `prefs --es file clusternav_prefs` để đọc, `prefs_set` để ghi — **không có `prefs_get`**.)
3. Bật *Hiện lên cụm* + hình *Theo cụm* → xi-nhan trái ⇒ **mép trái ảnh bám đường cong kính cụm**, không còn cạnh thẳng đứng. Hỏng ⇒ chụp màn + đọc `dải=` trong `logcat -s KachiCamera`.

⚠ **Nếu chụp ảnh cho hướng dẫn**: tắt app dẫn đường, **không đăng nhập tài khoản nào**, không để xe khác trong khung. Lý do ở §7.

## 2b. Ba câu hỏi phase 2 (KHÔNG cần làm lần này)
Nếu tiện thì liếc, không thì bỏ: Netflix mở từ launcher ZIN có tự thoát y hệt không (30 giây, chốt việc Kachi không dính) · tên app ngoại trong giọng nói (*"mở netflix"* ra *"nep leag"*) · app tự mở màn thứ hai không nằm được trong ô (đã chứng minh là cổng Android, không sửa được bằng app thường).

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

Tôi chép ảnh xe vào `docs/guide/img/` cho hướng dẫn; `git check-ignore` báo *"không bị chặn"* và tôi coi đó là **giấy phép** thay vì **cờ đỏ**. Lọt lên `main` công khai trong commit `491e169`: **biển số một xe của người khác**, 3 ảnh cụm hiện **bản đồ sống chỉ đúng vị trí + giờ**, một ảnh đọc được **số nhà** + **avatar tài khoản Google**, 2 ảnh màn hình có **YouTube đang đăng nhập** + telemetry xe.

Đã xử lý: gỡ → `--amend` → `push --force-with-lease` cả hai nhánh → xác minh remote sạch (3 ref đều `4297555`, rồi `e720009`) → `.gitignore` chặn **theo mẫu tên tệp, độc lập đường dẫn** (`car-*.png` · `cum-*.png` · `camera-frame-*.png` · `cluster-fb-*.png` · `**/img/car-*.png`).

**Việc của owner nếu muốn chắc chắn**: commit `491e169` có thể vẫn truy cập theo SHA tới khi GitHub dọn rác ⇒ mở ticket GitHub Support xin purge. Phần đáng gỡ triệt để nhất là **biển số của người thứ ba**.

**Ba luật rút ra, đã ghi backlog + memory**: (a) `check-ignore` trả *"không chặn"* cho một tệp dữ liệu xe là **cờ đỏ**; (b) lưới chặn ghim theo thư mục không bảo vệ được thư mục **chưa tồn tại**; (c) 6 scanner song song bỏ sót vì không ai được giao đọc **thư mục mới** — chỉ vòng **critic** bắt được, nên vòng ấy là bắt buộc.
