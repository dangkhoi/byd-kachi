# Hướng dẫn LẤY LOG bản DIAG (chẩn đoán VietMap/Waze) — cho người KHÔNG rành IT

> **Trạng thái**: Current · **Cập nhật**: 2026-08-26 · **Mục đích**: Anh em cài **bản DIAG v1.28**, lái thử VietMap/Waze, log **tự bật + tự ra thẻ nhớ**, rồi gửi cả thư mục về để phân tích (tìm thông tin còn thiếu để bắn hướng rẽ lên HUD).

> ⚠️ Bản **DIAG** này KHÁC bản thường: **log bật sẵn** (để anh em khỏi phải nhớ bật) và **tự copy log ra thẻ nhớ** cho dễ lấy. Chỉ dùng để thu thập dữ liệu, xong thì cài lại bản thường.

---

## Vì sao cần (nói ngắn)
VietMap/Waze khi chiếu lên cụm, app **chưa đủ thông tin để vẽ mũi tên rẽ lên HUD**. Bản DIAG ghi lại **mọi chữ + mọi ảnh** mà VietMap/Waze phát ra trong lúc dẫn đường, để bên mình xem **hướng rẽ nằm ở đâu** (chữ hay chỉ có trong hình) rồi bổ sung.

---

## BƯỚC 0 — Cài bản DIAG (1 lần)
1. Chép file **`ClusterNav2.0-v1.28-DIAG-logon-sdcard-*.apk`** vào xe (USB hoặc tải về).
2. Cài đè lên app cũ (cùng khoá ký nên cài đè được; nếu báo lỗi chữ ký thì gỡ app cũ rồi cài).
3. Mở app **Cluster Nav 2.0** → **bật công tắc Nav+HUD** (để app bắt đầu đọc dẫn đường).
4. **Không cần** bật gì thêm — bản DIAG **đã bật sẵn "Nhật ký chi tiết"** (công tắc trong app đang ở trạng thái BẬT).

---

## BƯỚC 1 — Lái thử (phần quan trọng nhất)

> ⚠️ **BẮT BUỘC trước khi lái:** phải **ĐĂNG NHẬP** VietMap và Waze, và app phải **ĐANG DẪN ĐƯỜNG THẬT** (đã chọn điểm đến, đang có mũi tên rẽ). Nếu app báo "Phiên hết hạn / đăng nhập lại" hoặc chỉ mở bản đồ mà KHÔNG dẫn → **log sẽ rỗng phần ngã rẽ = mất công**. Kiểm: màn hình có đang hiện mũi tên + "còn Xm rẽ ..." thì mới OK.

Lái bình thường, **đi qua NHIỀU ngã rẽ**. Cần thu đủ cả 2 app:

**A. VietMap** (~10–15 phút):
1. Mở **VietMap**, chọn 1 tuyến có nhiều ngã rẽ (trái, phải, đi thẳng, vòng xuyến).
2. Lái qua các ngã rẽ đó — **để VietMap hiện ở màn hình chính** một đoạn.
3. Rồi thử **chiếu VietMap lên cụm** (nút nổi) và lái tiếp vài ngã rẽ (đây đúng là tình huống đang lỗi — cần data).

**B. Waze** (~10 phút):
1. Mở **Waze**, chọn tuyến có nhiều ngã rẽ.
2. Lái qua trái / phải / quay đầu / vòng xuyến nếu có.

> Càng nhiều **kiểu rẽ khác nhau** càng tốt (trái, phải, gấp, chếch, quay đầu, vòng xuyến, tới đích).

---

## BƯỚC 2 — Xuất log ra thẻ nhớ (làm TRÊN XE, sau khi lái)
Log tự ghi trong lúc lái. Để đẩy ra chỗ dễ lấy:

**Cách dễ nhất:** trong app Cluster Nav 2.0, **GẠT công tắc "Nhật ký chi tiết" sang TẮT**.
→ App tự copy toàn bộ log ra thư mục **`Download/ClusterNavLog`** trên bộ nhớ máy (thư mục này **app Quản lý tệp nào cũng vào được**, không cần máy tính).

> (Kỹ thuật hơn, nếu quen adb: `adb shell am broadcast -a com.byd.clusternav.EXPORT_LOGS` cũng đẩy log ra đúng thư mục đó.)

---

## BƯỚC 3 — Lấy log mang về
1. Cắm **USB** vào xe.
2. Mở app **Quản lý tệp / File Manager** trên xe.
3. Vào thư mục **`Download/ClusterNavLog`** (bộ nhớ trong / Internal storage → Download → ClusterNavLog).
4. **Copy cả thư mục `ClusterNavLog`** đó ra USB.
5. Rút USB → gửi cả thư mục về cho bên mình (nén .zip là gọn nhất).

> Nếu không thấy thư mục `Download/ClusterNavLog`: kiểm lại đã **gạt công tắc Nhật ký sang TẮT** ở Bước 2 chưa (đó là lúc app copy ra). Log gốc luôn nằm ở `Android/data/com.byd.clusternav2/files/` — nhưng thư mục này nhiều file manager không vào được, nên mới cần bước xuất ra `Download`.

---

## Trong thư mục log có gì (để bên mình biết bạn thu đủ chưa)
- `nav_access_log_*.csv` — **CHỮ mà VietMap/Waze phát ra** (hướng/đường/cự ly/ETA…). **Đây là file chính** để tìm hướng rẽ.
- `nav_notif_*.csv`, `nav_log_*.csv` — notification + cự ly (Google Maps).
- `nav_arrow_pngs_*/`, `diag/seg-*.png` — **ẢNH mũi tên + ảnh chụp cụm/màn** mỗi ngã rẽ (để dựng mẫu mũi tên).
- `vietmap_*.csv` — tín hiệu widget VietMap (tốc độ/giới hạn).

---

## Xong rồi thì
- Cài lại **bản thường** (`ClusterNav2.0-v1.28-release-*.apk`) — bản thường **KHÔNG bật log**, không ghi gì, chạy nhẹ như cũ.
- Bản DIAG chỉ để thu data, đừng để chạy lâu dài (ghi log tốn dung lượng — có giới hạn ~150MB tự dọn, nhưng vẫn nên về bản thường).

---

## Tóm tắt 30 giây
1. Cài **APK DIAG** → bật Nav+HUD (log đã bật sẵn).
2. Lái VietMap (màn chính + chiếu cụm) rồi Waze, qua nhiều ngã rẽ.
3. **Gạt công tắc "Nhật ký chi tiết" sang TẮT** → log ra `Download/ClusterNavLog`.
4. Copy thư mục đó ra USB → gửi về.
5. Cài lại **APK bản thường**.
