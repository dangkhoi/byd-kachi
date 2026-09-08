# Hướng dẫn LẤY LOG lái thử — ClusterNav 2.0 (cho người KHÔNG rành IT)

> **Trạng thái**: Current · **Cập nhật**: 2026-08-17 · **Mục đích**: Cách lấy log + ảnh sau khi lái thử (Cách A không cần máy tính / Cách B dùng máy tính).

> Mục đích: sau khi lái thử, lấy toàn bộ **log + ảnh chụp** từ xe về máy tính để gửi lại phân tích.
> Làm theo đúng thứ tự. Mỗi bước chỉ vài phút. Có 2 cách — **Cách A (dễ nhất, không cần máy tính)** và **Cách B (dùng máy tính)**.

---

## BƯỚC 0 — Bật ghi log (làm TRÊN XE, trước khi lái) ⚠️ bắt buộc
Nếu không bật, xe sẽ **không ghi log**.
1. Mở app **Cluster Nav 2.0** trên xe.
2. Ở màn hình chính, **nhấn-GIỮ dòng chữ phiên bản** (“ClusterNav · v1.1…”) ~2 giây.
3. Thấy dòng báo **“Nhật ký chi tiết: BẬT”** là được. (Nhấn-giữ lần nữa để TẮT.)
4. Lái xe bình thường, đi qua nhiều ngã rẽ. App tự ghi log + tự chụp hình mỗi lần đổi hướng.

---

## CÁCH A — Chép qua USB, KHÔNG cần máy tính (dễ nhất)
1. Cắm một **USB** vào xe.
2. Trên xe mở app **Quản lý tệp / File Manager**.
3. Vào đúng thư mục: `Android/data/com.byd.clusternav2/files/`
4. **Copy cả thư mục `files`** đó ra USB.
5. Rút USB, cắm vào máy tính, gửi cả thư mục đó về cho mình.

> Nếu file manager trên xe không vào được `Android/data`, dùng **Cách B**.

---

## CÁCH B — Dùng máy tính (macOS hoặc Windows)

### BƯỚC 1 — Cài “adb” (chỉ làm 1 LẦN)

**Trên macOS:**
- Cách nhanh (nếu có Homebrew): mở **Terminal**, gõ:
  ```
  brew install android-platform-tools
  ```
- Không có Homebrew: tải https://developer.android.com/tools/releases/platform-tools → giải nén → nhớ thư mục `platform-tools`.

**Trên Windows:**
- Tải https://developer.android.com/tools/releases/platform-tools → giải nén (ví dụ ra `C:\platform-tools`).
- **Chép file `pull-drive-logs.bat`** (trong `scripts/vehicle/`) vào **cùng thư mục** `platform-tools` đó.

### BƯỚC 2 — Nối xe với máy tính
- **Cách cáp USB:** cắm cáp từ máy tính vào cổng USB của xe. Nếu màn xe hỏi **“Allow USB debugging?”** → bấm **Allow / Cho phép**.
- **Cách WiFi:** máy tính và xe **cùng một WiFi**; hỏi mình **địa chỉ IP của xe** (dạng `192.168.x.x`).

### BƯỚC 3 — Lấy log

**macOS (Terminal):**
```
# nối bằng cáp USB:
bash pull-drive-logs.sh
# hoặc nối qua WiFi (thay IP của xe):
bash pull-drive-logs.sh 192.168.1.50
```
*(nếu Terminal báo “command not found: adb”, gõ trước:* `export PATH="$HOME/Downloads/platform-tools:$PATH"` *rồi chạy lại — sửa đường dẫn cho đúng chỗ bạn giải nén.)*

**Windows:**
- Nối cáp USB → **double-click `pull-drive-logs.bat`**.
- Hoặc nối WiFi → mở **Command Prompt** trong thư mục `platform-tools`, gõ: `pull-drive-logs.bat 192.168.1.50`

---

## Log nằm ở đâu?
Sau khi chạy xong, một thư mục **`clusternav-logs-<ngày-giờ>`** hiện ra trên **Desktop** (và tự mở). Bên trong gồm:
- `nav_notif_*.csv` — notification Google Maps xe nhận được (hướng rẽ, cự ly, tên đường).
- `nav_log_*.csv` — khoảng cách (GMaps thô · nội suy · hiển thị · đọc-màn accessibility · tốc độ xe).
- `nav_arrow_log_*.csv` + `nav_arrow_pngs_*/` — phân loại mũi tên + **ảnh mũi tên**.
- `diag/seg-*-main.png`, `diag/seg-*-cluster.png` và `diag/seg-*-cluster-overlay.png` — **ảnh chụp màn chính + cụm (OEM composite) + lớp overlay cụm (có badge)** mỗi ngã rẽ.

**Gửi nguyên cả thư mục đó về cho mình** (nén .zip rồi gửi là gọn nhất).

---

## Không lấy được? Kiểm tra nhanh
- Chưa thấy log / thư mục trống → **quên BƯỚC 0** (chưa bật “Nhật ký chi tiết”), hoặc chưa lái đủ để có ngã rẽ.
- “command not found: adb” / “adb is not recognized” → **BƯỚC 1** chưa xong.
- “Không thấy xe” → cáp chưa cắm chặt / chưa bấm **Allow** trên màn xe / nhập sai IP WiFi.
