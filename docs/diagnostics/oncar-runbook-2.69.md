# Runbook buổi xe 2.69 — theo PHÚT, mỗi bước có lệnh · kết quả mong đợi · quyết định · 2026-09-26

> **Trạng thái**: Current · **Cập nhật**: 2026-09-26 · **Mục đích**: một buổi xe **~55 phút** đo được HẾT nợ off-car của CLOSE-1..3 + CAM-ROT + CAM-LAG, không mò. Thay `oncar-runbook-2.67.md`. Kết quả điền vào: `perf-closeout-2026-09-25.md` §4 (C1–C10), `camera-lag-analysis-2026-09-26.md` §3, backlog CLOSE-1 → DONE-oncar.

## 0. Ở nhà, TRƯỚC khi ra xe (10 phút — làm hết, ra xe chỉ chạy)

```bash
cd ~/Documents/workspaces/experiments/byd/byd-launcher
A=~/Library/Android/sdk/platform-tools/adb
# APK mới nhất trên nhánh (2.68 hoặc 2.69 nếu đã có) + đường lùi
ls -la apk/Kachi-*-release.apk
git show 93dc1b4:apk/Kachi-2.65-release.apk > /tmp/Kachi-2.65-release.apk        # bản trước vòng đóng dự án
# bảng chép số (in ra giấy hoặc mở trên điện thoại): docs/diagnostics/perf-closeout-2026-09-25.md §1 (số TRƯỚC máy ảo) để đối chiếu
```
Chuẩn bị máy: bật Local Network cho Terminal/Claude (memory `kachi-adb-car-tunnel`), hoặc dùng cầu `nc` bên dưới. WiFi laptop = hotspot xe.

## 1. Nối adb (3 phút) — hai đường

```bash
# Đường 1 (nếu Local Network đã cấp): 
$A connect <ip-xe>:5555 && S=<ip-xe>:5555
# Đường 2 (cầu nc, khi adb báo "No route to host" mà nc thông):
mkfifo /tmp/adbfifo; (nc -l 127.0.0.1 15555 < /tmp/adbfifo | nc <ip-xe> 5555 > /tmp/adbfifo) & 
$A connect 127.0.0.1:15555 && S=127.0.0.1:15555
# KHÔNG ĐOÁN bản đang chạy (CLAUDE.md §9):
$A -s $S shell dumpsys package com.byd.launcher | grep -E "versionName|versionCode|lastUpdateTime"
```
Ghi: bản đang chạy = ______ (kỳ vọng 2.65/2.6x). Nếu adb không nối được ⇒ nhảy sang **§8 đường không-adb**.

## 2. BASELINE bản cũ (10 phút) — xe đậu, nổ máy, màn chính Kachi, không chạm

```bash
OUT_DIR=docs/diagnostics/perf-oncar-2026-09-26 scripts/emulator/perf-snapshot.sh before-oncar 300 $S   # 5 phút, chạy nền được
# trong lúc chờ 5 phút — KHÔNG chạm màn; sau khi xong:
$A -s $S logcat -d -s KachiPerf | tail -3            # chép: HAL đọc/phút · bỏ-xe-không-có · shell/phút · log KB/phút
$A -s $S shell dumpsys meminfo com.byd.launcher | grep -E "TOTAL PSS|Native Heap" | head -2
$A -s $S shell dumpsys meminfo com.byd.launcher:wake | grep -E "TOTAL PSS|Native Heap" | head -2
$A -s $S logcat -d | grep -c "avc: denied.*loadavg"   # kỳ vọng > 0 ở bản cũ nếu Hey Kachi bật
$A -s $S shell "top -H -n 2 -d 10 -p \$(pidof com.byd.launcher)" | awk 'NR>1' | sort -k9 -rn | head -6   # luồng ăn CPU
```
| Chép | TRƯỚC (xe) | máy ảo TRƯỚC (tham chiếu) |
|---|---|---|
| CPU launcher % (perf-snapshot) | | 1,99 |
| PSS launcher / native | | 178 MB / 146 MB |
| PSS `:wake` | | 266 MB |
| HAL đọc/phút · shell/phút | | 420 · 4 |
| avc loadavg | | 58/phút |

**Camera lag baseline (3 phút, xe đậu)**: bật xi-nhan trái, giữ 20 s:
```bash
$A -s $S shell dumpsys SurfaceFlinger --list | grep -i byd.launcher     # copy tên layer overlay camera (dòng có Camera/Overlay)
$A -s $S shell dumpsys SurfaceFlinger --latency "<layer>" | awk 'NR>1 && $2>0 {print $2}' | awk 'NR>1{d=($1-p)/1e6; if(d>0) printf "%.1f\n", d} {p=$1}' | sort -n | awk '{a[NR]=$1} END{print "khung="NR, "median_ms="a[int(NR/2)], "p90_ms="a[int(NR*0.9)]}'
$A -s $S logcat -d -s KachiCamera | grep -i "fps\|addPreviewSurface" | tail -3
```
Chép: khung, median ms (15 fps = 66,7 ms), p90 ms. **p90 > 130 ms hoặc khung < 100 trong 20 s ⇒ rớt khung thật (L2)**; median ≈ 67 và p90 < 90 ⇒ giật là do 15 fps (L3).

## 3. CÀI bản mới (2 phút)

```bash
$A -s $S install -r apk/Kachi-2.72-release.apk && $A -s $S shell dumpsys package com.byd.launcher | grep versionName
$A -s $S shell am force-stop com.byd.launcher; $A -s $S shell am start -n com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity
```
Giữ dữ liệu (cùng khoá ký). **Lùi**: `$A -s $S install -r -d /tmp/Kachi-2.65-release.apk`. Nếu launcher không lên trong 30 s ⇒ lùi ngay, chụp `logcat -d -b crash`, dừng buổi đo phần mới.

## 4. ĐO LẠI y hệt §2 (8 phút) — mở màn chính, chờ 2 phút rồi chạy `perf-snapshot.sh after-oncar 300 $S` + 5 lệnh còn lại. Điền cột SAU cạnh cột TRƯỚC.

Đạt (từ `perf-closeout-2026-09-25.md`): HAL < 150/phút · shell ≈ 0 · avc = 0 · PSS chính giảm ≥ 80 MB nếu Hey Kachi bật · CPU giảm. **Không đạt mục nào ⇒ ghi số, KHÔNG suy diễn, mang về.**

## 5. TÍNH NĂNG (18 phút) — mỗi dòng: làm · đạt khi · lệnh chốt · nếu sai

| # | Làm | Đạt khi | Lệnh chốt | Nếu sai |
|---|---|---|---|---|
| D1 (K1b) | Chỉnh gió/nhiệt trên màn AC gốc 3 lần | ô dock Kachi đổi ≤ 1 s | — (mắt) | chép độ trễ; `logcat -s KachiPerf` xem HAL/phút |
| D2 (P1-main) | Bấm nút gió +/− 5 lần nhanh; bấm 1 nút xe không có (vd ghế sau nếu không có) | không giật; nút xe không có: nảy về + W | `$A -s $S logcat -d -s ControlWrite` | nếu nút xe CÓ mà nảy về ⇒ chép rc: `logcat -s BydHalGateway` |
| D3 (B1) | Phím vô-lăng gọi Kachi ở phút 0 / 15 / 30 | 3/3 nghe | `logcat -d -s NavConnect` có "đã BOUND (AccessibilityManager) → bỏ dadb"; `dumpsys accessibility \| grep -A3 "Bound services"` có Kachi | nếu không nghe ⇒ chạy `dumpsys accessibility` + `logcat -s NavRebind NavConnect` ngay, chép |
| D4 (CAM-ROT) | Xi-nhan trái 10 s, phải 10 s | video **dọc**, đúng chiều cả hai bên | — (mắt, chụp ảnh 2 bên) | một bên lộn ⇒ Cài đặt › Tiện nghi xe › Xoay video › *Theo bên, ngược lại*; cả hai lộn ⇒ *180°*; ghi chip đã chọn |
| D5 (B2) | Tắt/bật công tắc camera 3 lần; xi-nhan nháy rồi tắt hẳn | camera lên khi nháy, đóng sau ~1,2 s | `$A -s $S shell "ps -T -p \$(pidof com.byd.launcher)" \| grep -c KachiHalSignal` ≤ 1 (0 khi tắt) | >1 ⇒ chép `ps -T`; camera không đóng ⇒ `logcat -s KachiCamera` |
| D6 (BG-20) | Hey Kachi TẮT → phím ngoài Kachi → nói → chờ 10 s | `:wake` đứng xuống | `dumpsys activity services \| grep VoiceWakeService` rỗng; `dumpsys meminfo com.byd.launcher:wake` không có/ < 30 MB | còn sống ⇒ `logcat -s WakeSvc` chép |
| D7 (CLOSE-3) | Hey Kachi BẬT → **nút mic màn chính** → nói "mấy giờ rồi" | nghe NGAY (không chờ ~15 s lần đầu) | `logcat -d -s KachiVoiceEntry` có "đã ack (hạn 1500 ms)"; `dumpsys meminfo com.byd.launcher \| grep Native` KHÔNG tăng ~74 MB | có "lùi in-process" ⇒ chép hạn ack + `logcat -s WakeSvc` |
| D8 (CLOSE-3b) | Cài đặt › Hồ sơ: thêm hồ sơ "Test"; nút mic: "đổi sang hồ sơ Test"; thêm sổ địa chỉ; nút mic: "về nhà" | Kachi đổi hồ sơ; nav mở địa chỉ MỚI | `$A -s $S shell run-as com.byd.launcher cat files/voice/grammar-snapshot.tsv` có `profile\tTest` | tệp không đổi ⇒ chép; câu không hiểu ⇒ `logcat -s KachiVoice` |
| D9 (car-image) | Nhìn widget lốp 1 phút | hình xe không nháy | — | nháy ⇒ quay video 10 s |
| D10 (cast) | Cắm CarPlay/AA một lần như thường | lên như 2.65 | — | không lên ⇒ `am stack list` + `logcat -s SimpleCast` |
| D12 (2.69 relay) | Hey Kachi BẬT → nút mic: "mở YouTube vào ô hai"; rồi "đổi bố cục hai cột" | YouTube vào ô 2; bố cục đổi; Kachi đọc "Đã…" (không "không làm được") | `logcat -d -s KachiHomeRelay KachiVoiceEntry` không có "không ack"/"tới sau hạn" | có "tới sau hạn" ⇒ chép thời gian, nới `VoiceHomeRelay.ACK_MS` ở nhà |
| D13 (2.69 owner) | Bảo Kachi đọc dài ("xe còn bao nhiêu pin, lốp thế nào") rồi bấm phím-thoại NGAY | tiếng cũ tắt, một tấm chữ mới | `logcat -d -s WakeSessions` có "cắt phiên cũ" | hai tiếng chồng ⇒ chép |
| D14 (2.69 ack) | Ba ca: Kachi đang hiện · app khác toàn màn · vừa nổ máy — mỗi ca nói "mở YouTube vào ô hai" qua Hey Kachi | cả 3 ca ✓ | `logcat -d -s KachiHomeRelay` — dòng đo thời gian ack THẬT (ms) từng ca; `logcat -s KachiVoiceEntry` không "tới sau hạn" | ack > 1,5 s (ấm) hoặc > 4 s (lạnh) ⇒ chép số, nới `VoiceHomeRelay` ở nhà |
| D11 (crash) | — | | `$A -s $S shell ls /sdcard/Android/data/com.byd.launcher/files/kachi-logs/ \| grep crash` | có tệp ⇒ pull |

## 6. Camera lag SAU (3 phút) — lặp đúng 3 lệnh §2-camera với bản mới; so median/p90/khung. Nếu vẫn giật mà số bằng TRƯỚC ⇒ L2/L3 (đọc `camera-lag-analysis-2026-09-26.md` §2), quyết định đổi cờ ở nhà, không sửa trên xe.

## 7. Mang về (2 phút)
```bash
$A -s $S pull /sdcard/Android/data/com.byd.launcher/files/kachi-logs/ docs/diagnostics/perf-oncar-2026-09-26/kachi-logs/
$A -s $S logcat -d > docs/diagnostics/perf-oncar-2026-09-26/logcat-after.txt
```
Về nhà: điền C1–C10 §4 `perf-closeout-2026-09-25.md` · §3 `camera-lag-analysis` · CLOSE-1 → DONE-oncar · nếu D4 sai chiều ⇒ đổi `CameraSignalPolicy.defaultRotation` + bump.

## 8. Đường KHÔNG-adb (khi không nối được) — 15 phút, chỉ chụp màn
1. Cài APK từ USB/OTA như thường (Cài đặt › Hệ thống › Kiểm tra cập nhật khi `main` đã đẩy).
2. Cài đặt › Hệ thống › Nâng cao › **Chẩn đoán** (DiagActivity) ⇒ chụp màn (có version, KachiPerf, dịch vụ).
3. D1, D2, D4, D5, D7, D8, D9, D10 làm bằng mắt như bảng §5, chụp ảnh từng bước; D4 chụp cả hai bên.
4. Về nhà pull `kachi-logs/` bằng cáp.

## Thứ tự nếu chỉ có 20 phút
§1 nối → §3 cài → **D4 D7 D8 D3 D1** → `perf-snapshot.sh after-oncar 120` → §7. Baseline bản cũ bỏ (đã có số máy ảo + số xe 09-16 ở `perf-profile-2026-09-16.md` §0).
