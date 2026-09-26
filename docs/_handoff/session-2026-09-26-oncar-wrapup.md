# Wrap-up buổi xe 26/09 + kế hoạch off-car kế tiếp

> **Trạng thái**: Current (handoff, tạm) · **Cập nhật**: 2026-09-26 · **Mục đích**: một trang cho owner + anh em test: bản nào đang ở đâu, hôm nay đo được gì, còn gì làm ở nhà (làm SAU, theo thứ tự), còn gì cần xe. Chi tiết số: `docs/diagnostics/oncar-session-2026-09-26.md`; backlog: `docs/PROJECT-BACKLOG.md` (khối CLOSE-1 … ONCAR-2026-09-26).

## 1. Bản
| Ở đâu | Bản | Ghi chú |
|---|---|---|
| Xe owner (Seal) | **2.70 (171)** cài trực tiếp qua adb | = 2.69 + hotfix camera giữ tới khi đèn tắt |
| Kênh OTA `main` · `apk/Kachi-2.73-release.apk` | **2.73 (174)** | = 2.72 + 8 việc off-car R9 (xem §4); 2.72 = 2.70 + 2 dòng xoay video trái/phải + lưới an toàn helper HAL chết; **anh em cập nhật qua Cài đặt › Hệ thống › Kiểm tra cập nhật** |
| Nhánh `feat/voice-hotword-phrases` | = `main` | |

## 2. Hôm nay đo được gì (xe thật, 8 lõi) [ĐO]
- RAM launcher **246 → 67 MB** (native 220 → 29), luồng 58 → 43, vẽ lại khi đứng yên 2303 → 510 khung/5 phút. `:wake` 189 → 180 MB.
- Camera xi-nhan: chiều xoay đúng (trái ↺ / phải ↻); **bug 2.69 tắt sau 1 s đã vá** (helper HAL báo trạng thái đèn, không nháy).
- Nút mic khi Hey Kachi bật: nghe sau 641 ms (trước ~15 s lần đầu), phiên trong `:wake`, không nạp mô hình thứ hai.
- Đưa app vào ô bằng giọng: đường relay chạy (1 lượt "mở youtube vào ô số hai" ⇒ ô 2 ✓; intent ✓) nhưng **nhận dạng bỏ vế "vào ô"** ở ~9/10 lượt.

## 3. Anh em test 2.71 — nhìn gì, báo gì (không cần adb)
1. Xi-nhan trái/phải: camera **dọc**, đúng chiều, **giữ tới khi tắt đèn**; nếu ngược ⇒ Cài đặt › Tiện nghi xe › *Xi-nhan trái/phải: xoay video* chọn lại — báo chip đã chọn + đời xe.
2. Hey Kachi bật ⇒ bấm mic màn chính ⇒ có nghe ngay không (< 1 s).
3. Nói "bật gió tự động", "mở youtube vào ô số hai" (nói **liền một hơi**) — báo Kachi đọc lại gì.
4. Lúc Kachi đọc phản hồi, **taskbar hệ thống có trồi lên** không (lỗi đã biết, đang sửa).
5. Chụp Cài đặt › Hệ thống › Nâng cao › Chẩn đoán sau 10 phút lái.

## 4. Kế hoạch off-car — ✅ ĐÃ LÀM HẾT cùng ngày (2.73), chi tiết + số đo ở `docs/diagnostics/offcar-2026-09-26/*.md`, backlog từng mục ✅; bài tập xe ở `docs/diagnostics/oncar-runbook-2.73.md`

Kết quả một dòng mỗi việc [ĐO off-car]: (1) taskbar: gốc là đổi tiêu điểm cửa sổ, vá `FLAG_NOT_FOCUSABLE`, Back không còn huỷ · (2) "vào ô số": không phải VAD, không phải biasing — vế ô thiếu hotword; thêm "VÀO Ô SỐ N", 4/4 WAV bệnh khỏi; VAD mặc định giữ 600, trần 1200 · (3) tên app mờ: 2/3 chuỗi thật khỏi · (4) hồ sơ Test: 2/2 khỏi + hỏi lại khi thiếu tên · (5) shell 19 = 15 K7 + 4 probe, giữ; log 34 → ≈9 KB/phút · (6) chip Kết xuất TV/SV, mặc định không đổi · (7a) `libkachimem.so` mallopt, +16 KB APK · (7b) khung camera đúng tỉ lệ crop sau xoay (360×192 cho dải gương), 0 méo 0 đen. Ba việc chờ owner quyết: WATCHDOG-GATE-8S · CAM-ROT-3 · VOICE-OPEN-TURN (backlog).

### 4.0 Bảng kế hoạch gốc (giữ để đối chiếu)
| # | Việc | Bằng chứng/đầu vào | Kiểm off-car |
|---|---|---|---|
| 1 | **SYS-TASKBAR-VOICE-FOCUS** — overlay phản hồi `:wake` `FLAG_NOT_FOCUSABLE` + immersive, giữ chạm-để-huỷ | `perf-oncar-2026-09-26/taskbar-window-dump.txt` (focus đổi sang `VoiceOverlay$build$5`) | test canh cờ cửa sổ; 🚗 nhìn |
| 2 | **VOICE-SLOT-TAIL-CUT + VAD-SILENCE-CAP** — trần `voice_vad_min_silence_ms` 800 → 1200, mặc định câu ghép; kiểm biasing tên app không nuốt vế sau | 30 WAV thật trong `kachi-voice-20260926-185812-365.zip` (cục bộ, KHÔNG commit) + `scripts/voice/replay-car-log.py` | replay WAV ⇒ "mở vietmap vào ô số hai" phải ra `OpenApp(VietMap→ô 2)` |
| 3 | **VOICE-APP-NAME-FUZZY** — "youtubex/youtubec/youtub newt" ⇒ YouTube | cùng zip | replay 3 WAV |
| 4 | **VOICE-PROFILE-NAME-PHONETIC** — tên hồ sơ/địa chỉ tiếng Anh phiên âm vào hotword; "hồ sơ định" ⇒ "Mặc định" (khớp mờ) | cùng zip | replay |
| 5 | **SHELL-19 + LOG-41KB** — nguồn 19 lệnh shell/phút và 41 KB log/phút | `perf-oncar-2026-09-26/kachi-logs/usage-*.log` (cục bộ) | đếm theo tag |
| 6 | **CAM-LAG** — TextureView trên luồng chính (gfxinfo p99 400 ms khi camera hiện); `SurfaceFlinger --latency` không dùng được trên ROM ⇒ đo bằng gfxinfo | `camera-fps-2.70.md` | cờ Cài đặt SurfaceView/OPAQUE, không đổi mặc định |
| 7 | Owner đã quyết 26/09: **CLOSE-4 làm** (NDK `mallopt`) · **CAM-ROT-2 làm** (overlay đúng tỉ lệ camera, không viền đen, không lấp bừa) · **K7 = DONE-chấp nhận** (không tách cast) · **CLOSE-9 = A, chấp nhận** (chỉ hoàn nguyên ô khi chắc xe có nút) | — | test thuần + 🚗 |

## 5. Còn cần xe (một buổi ngắn, sau khi làm 1–4)
Đo CPU idle 2.7x **yên** 5 phút (SAU hôm nay bị lẫn camera) · D1/D2 AC dock · D6 `:wake` đứng xuống khi Hey Kachi tắt · D10 CarPlay/AA · D13 phím-thoại giữa lúc Kachi đọc · taskbar sau khi vá · câu ô/hồ sơ sau khi vá ASR.
