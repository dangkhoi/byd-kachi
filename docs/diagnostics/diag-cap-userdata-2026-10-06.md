# 2.92 · DIAG-CAP-USERDATA — bộ dọn ~150 MB xoá dữ liệu người dùng · đo máy ảo 2026-10-06

> **Trạng thái**: Current · **Cập nhật**: 2026-10-06 · **Spec**: [`specs/kachi-292-diag-cap.html`](../specs/kachi-292-diag-cap.html) ·
> **ADR**: [`decisions/0004-storage-cap-allowlist.md`](../decisions/0004-storage-cap-allowlist.md) · **Backlog**: `DIAG-CAP-USERDATA` (P1) ·
> **Máy**: `emulator-5554` (`clusternav10`, Android 10) · **Không đụng xe.**
> **Mục đích**: bằng chứng trước/sau cho lỗi mất dữ liệu: `DiagStorageCap` (cap ~150 MB) dọn CẢ thư mục ngoài của app, nơi
> cũng chứa ảnh trình chiếu, hình nền, ảnh xe, hồ sơ xuất và gói giọng side-load.

## 1. Mã (2.91) [ĐO]

- `app/…/DiagStorageCap.kt` (2.91) `enforceLocked`: `base = getExternalFilesDir(null)` → `collectFiles(base)` (MỌI tệp, mọi độ
  sâu) → `StorageCapPlanner.selectForDeletion` (cũ nhất trước tới khi tổng ≤ 150 MB) → xoá → `pruneEmptyDirs(base)` (mọi thư
  mục rỗng, kể cả `photos/` `wallpapers/` `car/` mà app tạo sẵn để người dùng thấy chỗ bỏ ảnh).
- Chỗ gọi: `KachiHomeActivity` (mỗi lần dựng HOME, `force = true`) · `NavNotificationListener` (`force = true`) ·
  `ClusterBroadcaster` (khi `NavLog.verbose`, chặn nhịp 60 s).

## 2. Bộ ghi vào thư mục ngoài — xếp loại (đọc từng bộ ghi)

| Đường (tương đối gốc) | Bộ ghi | Loại |
|---|---|---|
| `kachi-logs/` (`usage-*.log`, `snapshot-*.log`, `crash-*.log`, `captest-report.txt`, tệp đo cầu kiểm thử camera/featmap/sweep, log inputd) | `KachiLog` (+ `TestBridgeCameraFrame`/`FeatMap`/`Sweep`, `AppContainer.logDir`) | chẩn đoán |
| `diag/diag-<mốc>.txt` | `ClusterDiag` (tự giữ 20) | chẩn đoán |
| `test/<mốc>-<lệnh>.json` | `TestBridgeReply` (`getExternalFilesDir("test")`, tự giữ một số tệp) | chẩn đoán |
| `castlog/cast_*.txt` | `ClusterCast` TEE — đã gỡ ở 1.63, chỉ còn tàn dư trên máy cài từ ≤ 1.62 | chẩn đoán (cũ) |
| `nav_notif_log_<ms>.csv` · `nav_notif_raw_<ms>.csv` (gốc) | `NavNotifLog` · `NavNotifRawLog` (chỉ khi verbose) | chẩn đoán |
| `kachi-voice-<yyyyMMdd-HHmmss-SSS>.zip` (gốc) | `VoiceUtteranceLog.zipToAppDir` — đường lùi khi ROM không cho ghi vào Download (nhật ký lượt nói nằm ở `filesDir/voice-log/`, bộ nhớ trong) | chẩn đoán |
| `kachi-voice-test.wav` (gốc) | `TestBridgeWav.stage` · `TestBridgeKws.stage` (chép tệp tiếng thử) | chẩn đoán |
| `photos/` | `PhotoStore` | NGƯỜI DÙNG |
| `wallpapers/` (+ `.kachi-art/` bộ đệm ảnh mờ) | `WallpaperStore` · `WallArt` | NGƯỜI DÙNG |
| `car/` | `CarImageStore` | NGƯỜI DÙNG |
| `profiles/` | `ProfileIoStore` | NGƯỜI DÙNG |
| `sherpa/import/<gói>/…` | `VoiceModelStore` (side-load gói giọng, xe không mạng) | NGƯỜI DÙNG |
| tệp tên do lệnh thử chỉ định (gốc) | `CameraSignalController.setSynth` — chỉ ĐỌC (`adb push` từ ngoài) | chỉ đọc |
| `kachi-voice-test.wav` | `VoiceWavProbe` — chỉ ĐỌC | chỉ đọc |

Không bộ ghi nào ở `:core`/`:car-integration` gọi `getExternalFilesDir` [ĐO grep]. Bảng này là bảng của bài
`DiagStorageCapWiringContractTest` — thêm lời gọi mới mà không xếp loại ⇒ đỏ.

## 3. Đo máy ảo — trước (2.89 = cùng mã cap với 2.91) / sau (vehicleTest cây hiện tại)

Cùng kịch bản (`dcap.sh`, ngoài repo), dựng trên thư mục ngoài thật của máy ảo (≈ 58 MB sẵn có: `kachi-logs/` 47 MB, ảnh thử ở
gốc 9 MB, …):

| Tệp dựng | Cỡ | mtime |
|---|---|---|
| `photos/qa-oldest-photo.png` (bản sao ảnh trình chiếu có sẵn) | 21 KB | 2001-01-01 |
| `sherpa/import/qa-pack/qa-model.bin` | 2 MB | 2001-01-02 |
| `kachi-logs/qa-diag-big-old.bin` | 110 MB | 2002-01-01 |

rồi `am force-stop` + mở HOME (`DiagStorageCap.enforce(force = true)`), đợi 8 s, đọc logcat + `ls`.

| Bản | Logcat | Ảnh người dùng | Tệp gói giọng | Log 110 MB | Thư mục rỗng `car/` (người dùng) | Thư mục rỗng `kachi-logs/qa-empty-sub/` |
|---|---|---|---|---|---|---|
| 2.89 | `DiagStorageCap: pruned 3 file(s) ~112 MB → cap 150 MB` | **XOÁ** | **XOÁ** (cả thư mục `qa-pack/`) | xoá | **XOÁ** | — |
| 2.92 (lượt 1) | `DiagStorageCap: pruned 1 file(s) ~110 MB → cap 150 MB` | giữ | giữ | xoá | (đã mất ở lượt 2.89) | — |
| 2.92 (lượt 2, dựng lại `car/` rỗng + `kachi-logs/qa-empty-sub/` rỗng) | `pruned 1 file(s) ~110 MB` | giữ | giữ | xoá | **giữ** | xoá (trong thư mục cho phép) |

Sau buổi đo: thư mục ngoài trả từ bản sao lưu đầu phiên — 1 527/1 527 tệp trùng sha256, 0 thừa
(`shortcut-widget-2026-10-06/emulator-restore.tsv`, lượt 3).

## 4. Chưa đo / giới hạn

- Trên xe: dung lượng thật của thư mục ngoài + có gói giọng side-load hay chưa [CHƯA BIẾT] ⇒ xe nào từng side-load gói > ~100 MB
  rồi mở HOME nhiều lần trên bản ≤ 2.91 có thể đã mất tệp gói (triệu chứng: cài gói báo thiếu tệp) — [SUY từ mã, chưa có báo cáo].
- Tệp không trong danh sách (bộ đệm `wallpapers/.kachi-art/`, ảnh thử `e2e-*.png` ở gốc) nay không còn được cap bao — chấp nhận
  (ADR-0004 · Consequences).
