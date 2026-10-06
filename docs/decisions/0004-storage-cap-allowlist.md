# 0004 — Storage cap chỉ chạm DANH SÁCH tệp chẩn đoán, không bao giờ chạm dữ liệu người dùng

> **Trạng thái**: Accepted · **Ngày**: 2026-10-06 · **Mục đích**: Sửa phạm vi của cap ~150 MB (ADR-0003 mục 2): từ "cả thư mục ngoài của app" thành "đúng danh sách đường chẩn đoán", vì cùng thư mục ấy chứa dữ liệu người dùng.

## Context

ADR-0003 (2026-08-19) đặt cap ~150 MB luôn-bật trên **toàn bộ** `getExternalFilesDir(null)` — lúc ấy thư mục này chỉ có log chẩn đoán. Từ đó tới 2.91, cùng thư mục được dùng cho **dữ liệu người dùng**:

- `photos/` (ảnh trình chiếu — `PhotoStore`), `wallpapers/` (hình nền + bộ đệm `.kachi-art/` — `WallpaperStore`/`WallArt`), `car/` (ảnh xe — `CarImageStore`), `profiles/` (hồ sơ xuất — `ProfileIoStore`), `sherpa/import/<gói>/` (gói giọng side-load khi xe không có mạng — `VoiceModelStore`).

`DiagStorageCap.enforceLocked` (2.91) liệt kê **mọi** tệp trong cây rồi xoá cũ-nhất-trước tới khi tổng ≤ 150 MB, sau đó xoá mọi thư mục rỗng [ĐO mã]. Dữ liệu người dùng thường là thứ CŨ NHẤT ⇒ bị xoá TRƯỚC log. `enforce(force = true)` chạy ở mỗi lần mở HOME (`KachiHomeActivity`).

Bằng chứng [ĐO máy ảo 2.89, cùng mã cap với 2.91] (`docs/diagnostics/diag-cap-userdata-2026-10-06.md`): đặt một ảnh trong `photos/`, một tệp 2 MB trong `sherpa/import/`, một log 110 MB trong `kachi-logs/` ⇒ mở HOME ⇒ `DiagStorageCap: pruned 3 file(s) ~112 MB` — mất cả ảnh lẫn tệp gói giọng, và thư mục `car/` rỗng (app tạo sẵn để người dùng thấy chỗ bỏ ảnh) cũng bị xoá. Một gói giọng side-load có thể tự nó vượt 150 MB ⇒ cap xoá dần chính gói đó.

## Decision

Cap chỉ được **liệt kê và xoá** những đường trong danh sách cho phép thuần `:core` `DiagFiles`:

- thư mục (mọi tệp, mọi độ sâu): `kachi-logs/` · `diag/` · `test/` · `castlog/` (bộ ghi đã gỡ ở 1.63 — dọn tàn dư);
- tệp ở gốc: `nav_notif_log_<mốc>.csv` · `nav_notif_raw_<mốc>.csv` · `kachi-voice-<mốc>.zip` (mốc chỉ gồm chữ số và `-`, có ít nhất một chữ số — senior review 2.92) · `kachi-voice-test.wav`.

Mọi đường khác — kể cả thư mục mới thêm sau này — **không bao giờ** bị chạm (mặc định từ chối). Ngữ nghĩa cap giữ nguyên trên tập cho phép: tổng tập ấy ≤ 150 MB, cũ nhất đi trước, ít lần xoá nhất. Dọn thư mục rỗng chỉ bên trong các thư mục cho phép. Không đi theo liên kết tượng trưng.

Ràng buộc kèm theo: mọi lời gọi `getExternalFilesDir` trong mã (kể cả nguồn riêng của biến thể `vehicleTest`; đếm theo LỜI GỌI, không chỉ theo tệp) phải được xếp loại (chẩn đoán / người dùng / chỉ đọc) trong bài canh `DiagStorageCapWiringContractTest` ⇒ bộ ghi mới không thể lặng lẽ thành "dọn được" hay "không ai dọn".

Phương án bị loại: (a) danh sách CẤM thư mục người dùng — thư mục người dùng mới sẽ lại bị xoá mặc định; (b) chuyển dữ liệu người dùng ra chỗ khác — đổi đường dẫn mà người dùng đã biết (chép ảnh/gói giọng qua USB), ngoài phạm vi sửa lỗi.

## Consequences

- **Được:** ảnh, hình nền, ảnh xe, hồ sơ xuất, gói giọng side-load không bao giờ bị cap xoá; log vẫn bị giới hạn 150 MB như ADR-0003.
- **Mất / trade-off:** tệp KHÔNG trong danh sách không còn được cap bao — gồm bộ đệm `wallpapers/.kachi-art/` (vài chục KB một ảnh, chưa có luật dọn riêng) và tệp thử do công cụ đẩy vào gốc (`e2e-*.png`, `wavin/`). Thêm bộ ghi chẩn đoán mới ⇒ phải thêm đường vào `DiagFiles` (bài canh nhắc).
- Việc phát sinh: backlog `DIAG-CAP-USERDATA` (P1, đóng bởi bản vá này) · spec `docs/specs/kachi-292-diag-cap.html`.

## Status

Accepted — đã implement (2.92, chưa đăng OTA). Sửa phạm vi mục 2 của ADR-0003 (phần còn lại của ADR-0003 giữ nguyên).

## Date

2026-10-06
