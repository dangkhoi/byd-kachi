# Runbook buổi xe 2.67 — baseline bản cũ → cài mới → so cùng lệnh · 2026-09-26

> **Trạng thái**: Current · **Cập nhật**: 2026-09-26 · **Mục đích**: một buổi xe ~40 phút, thứ tự cố định, mỗi bước có lệnh + kết quả cần chép lại. Kết quả điền vào `perf-closeout-2026-09-25.md` §4 (C1–C10) và `camera-lag-analysis-2026-09-26.md` §3. Cần adb vào xe (cầu `nc` — memory `kachi-adb-car-tunnel`) HOẶC chỉ chụp màn `DiagActivity`.

## 0. Chuẩn bị (ở nhà)
```bash
A=~/Library/Android/sdk/platform-tools/adb; S=<serial xe>            # ví dụ 127.0.0.1:15555 qua cầu nc
$A -s $S shell dumpsys package com.byd.launcher | grep -E "versionName|versionCode"   # KHÔNG đoán bản đang chạy (CLAUDE.md §9)
git show 93dc1b4:apk/Kachi-2.65-release.apk > /tmp/Kachi-2.65-release.apk              # đường lùi (cùng khoá ký)
```

## A. BASELINE bản đang chạy (xe đậu, nổ máy, màn chính Kachi, đứng yên 5 phút) — ~8 phút
```bash
OUT_DIR=docs/diagnostics/perf-oncar-2026-09-26 scripts/emulator/perf-snapshot.sh before-oncar 300 $S
$A -s $S logcat -d -s KachiPerf | tail -5                       # HAL đọc/phút · shell/phút · log KB/phút
$A -s $S shell top -H -n 2 -d 10 -p $(pidof com.byd.launcher)   # luồng nào ăn CPU (chờ 2 mẫu)
$A -s $S shell dumpsys meminfo com.byd.launcher | grep -E "TOTAL PSS|Native Heap"
$A -s $S shell dumpsys meminfo com.byd.launcher:wake | grep -E "TOTAL PSS|Native Heap"
$A -s $S logcat -d | grep -c "avc: denied.*loadavg"             # kỳ vọng > 0 ở bản cũ
```
Camera lag baseline (xi-nhan trái giữ 20 s khi xe đứng, rồi một đoạn xe chạy chậm nếu an toàn):
```bash
$A -s $S shell dumpsys SurfaceFlinger --list | grep -i byd.launcher      # tên layer overlay camera
$A -s $S shell dumpsys SurfaceFlinger --latency "<layer>" | head -130    # 128 khung → fps thật + khung rớt
$A -s $S logcat -d -s KachiCamera | grep -i fps
```
Chép: CPU %, PSS 2 tiến trình, HAL/phút, shell/phút, số dòng avc, fps camera thật.

## B. CÀI 2.67 (giữ dữ liệu, cùng khoá ký) — 2 phút
```bash
$A -s $S install -r apk/Kachi-2.67-release.apk && $A -s $S shell dumpsys package com.byd.launcher | grep versionName
$A -s $S shell am force-stop com.byd.launcher; $A -s $S shell am start -n com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity
```
Lùi nếu hỏng: `$A -s $S install -r -d /tmp/Kachi-2.65-release.apk` (`-d` vì versionCode thấp hơn).

## C. ĐO LẠI y hệt A (sau khi mở màn chính 2 phút) — ~8 phút
Cùng 6 lệnh mục A, label `after-oncar`. Tiêu chí đạt (từ `perf-closeout-2026-09-25.md`): HAL < 150/phút (kỳ vọng ≈ 0 khi dock toàn nút xe không có; xe Seal có nút thật thì bằng số nút × 60) · shell ≈ 0 · avc loadavg = 0 · PSS chính giảm ≥ 80 MB khi "Hey Kachi" bật · CPU idle giảm.

## D. TÍNH NĂNG (C1–C10 rút gọn + camera) — ~15 phút
| # | Làm | Đạt khi | Ghi |
|---|---|---|---|
| D1 | Chỉnh gió/nhiệt trên màn AC gốc | ô dock Kachi đổi ≤ 1 s (K1b không làm chậm) | |
| D2 | Bấm nút gió +/− 5 lần nhanh; bấm 1 nút xe không có | không giật; nút xe không có: nảy về + `logcat -s ControlWrite` có W | |
| D3 | Phím vô-lăng gọi Kachi ở 3 mốc (0/15/30 phút) | nghe cả 3 lần; `logcat -s NavConnect` có "đã BOUND (AccessibilityManager) → bỏ dadb" | |
| D4 | **Xi-nhan trái rồi phải** | video **dọc**, đúng chiều; sai một bên ⇒ Cài đặt › Xoay video › *Theo bên, ngược lại*; cả hai lộn ⇒ *180°* → báo lại để đổi mặc định | |
| D5 | Tắt/bật công tắc camera 3 lần, xi-nhan nháy → tắt hẳn | camera lên khi nháy, đóng sau ~1,2 s; `ps -T -p $(pidof com.byd.launcher) \| grep -c KachiHalSignal` ≤ 1 | |
| D6 | "Hey Kachi" TẮT, gọi bằng phím ngoài Kachi, nói xong chờ 10 s | `dumpsys activity services \| grep VoiceWakeService` rỗng | |
| D7 | Nhìn widget lốp 1 phút | hình xe không nháy | |
| D8 | Cắm CarPlay/AA 1 lần như thường | cast lên như 2.65 (không đổi đường) | |

## E. Về nhà
`adb pull /sdcard/Android/data/com.byd.launcher/files/kachi-logs/` (có `crash-*.log` nếu có sự cố) → điền §4 C1–C10 + §3 lag doc → backlog CLOSE-1 → DONE-oncar; nếu D4 sai chiều ⇒ đổi `CameraSignalPolicy.defaultRotation` + bump.
