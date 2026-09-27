# Handoff — 27/09/2026: chốt 2.76 (177), owner đi công tác một tháng

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 (chiều) · **Mục đích**: mở file này ra là biết ngay **đang ở đâu · lát lên xe làm gì · tháng sau làm gì**. Thay `session-2026-09-27-275-after-car.md` (đã Superseded). Đánh giá đóng dự án: `docs/CLOSEOUT-2026-09-27.md`.

## 1. Đang ở đâu

| | |
|---|---|
| Kênh OTA `main` | **2.76 (177)** · `apk/Kachi-2.76-release.apk` · sha256 `7a4b182b…9794` · commit `1823c67` |
| Xe owner (Seal) | **2.74 (175)** — 🚗 **chưa cài 2.76** |
| Off-car | **sạch** — test 4 646/0 · lint 0 · 0 tệp > 500 dòng · review Opus Pass 1 APPROVED (15 phát hiện, 14 vá) · quét bảo mật 0 BLOCK |
| Còn lại | 22 phép đo trên xe + 13 câu owner chốt + nợ không ai hứa (CLOSEOUT §4) |

## 2. LÁT LÊN XE — làm đúng ba bước

**Bước 1 — cài.** Trên xe: *Cài đặt › Hệ thống › Kiểm tra cập nhật* ⇒ nhận 2.76. Không có mạng thì `adb -s <serial> install -r Kachi-2.76-release.apk`. Chốt: `dumpsys package com.byd.launcher | grep versionCode` ⇒ **177**.

**Bước 2 — bật lại *Chế độ kiểm thử qua adb***: *Cài đặt › Hệ thống & quyền › Quyền*. Nó **tắt mỗi lần launcher khởi động lại**, và nó là cửa duy nhất mở cầu lệnh + khối *Nâng cao (kỹ thuật)* của màn Camera.

**Bước 3 — chạy `docs/diagnostics/oncar-runbook-2.76.md`.** 18 mục · **54 phút** · xếp theo giá trị trên mỗi phút, hết giờ thì dừng giữa chừng cũng được. 9 mục có dấu 👁 làm được **không cần adb** (chỉ mắt + màn Cài đặt).

**Mục quan trọng nhất là CAM-B5** (mục thứ hai): xi-nhan trái → phải **trực tiếp** ba lần rồi tắt, sau 30 giây log phải **im**. Đây là bằng chứng **duy nhất** cho bản vá [P0]; máy ảo không có phần cứng camera nên không thể thay thế. Bỏ mục này = bản vá đó chưa ai xác nhận.

**Mang về**: chép vào `docs/diagnostics/oncar-<ngày>/` — **đúng thư mục ngày**, vì `.gitignore` chặn theo mẫu `oncar-*/`. Commit **chỉ** bản .md tóm tắt + một dòng `ONCAR-<ngày>` trong backlog.

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
