# Buổi xe 2026-09-26 — baseline 2.58 → 2.69 → hotfix 2.70 · kết quả C1–C14 + phát hiện mới

> **Trạng thái**: Current · **Cập nhật**: 2026-09-26 · **Mục đích**: số [ĐO] trên xe thật (Seal, DiLink 3.0, 8 lõi) cho vòng đóng dự án + 4 bug mới bắt được với gốc trong log. Dữ liệu thô: `perf-oncar-2026-09-26/` (snapshot json/md, logcat stream, camera log, window dump, kachi-logs 136 tệp, zip WAV lượt nói). Runbook đã dùng: `oncar-runbook-2.69.md`.

## 1. Bản đang chạy trên xe TRƯỚC buổi: **2.58 (159)** (không phải 2.65 như đoán — đọc `dumpsys package`). Cài 2.69 rồi hotfix 2.70 ngay trên xe.

## 2. Số đo (perf-snapshot 300 s, xe đậu nổ máy, màn chính, 8 lõi)

| Chỉ số | 2.58 | 2.69 (đo có lẫn D4 camera) | Ghi chú |
|---|---|---|---|
| CPU launcher (1 lõi) | 1,20 % | 5,68 % ⚠ | cửa sổ SAU có camera + Cài đặt của owner ⇒ không so được; cần đo lại yên |
| PSS launcher | 245,6 MB | **67,1 MB** | native 220 → **29 MB** (không còn bản ASR thứ hai) |
| Luồng launcher | 58 | 43 | |
| PSS `:wake` | 188,6 MB | 180,1 MB | native 166 → 157 MB; CPU 3,96 % → 3,09 % |
| `:tts` | 129,8 MB | (chết SIGSEGV, tự lên lại) | lỗi native Piper đã biết, cách ly |
| Khung vẽ/300 s | 2303 (56 % janky) | 510 (28,6 %) | launcher hết vẽ lại liên tục |
| HAL đọc/phút | 312 | 312 | dock có nút thật ⇒ 1 Hz theo owner #5 (K1b chỉ cắt nút off-car) |
| shell/phút | 19 | 19 | [CHƯA BIẾT] nguồn — không phải a11y (alarm no-op); tra trong `kachi-logs/usage-*.log` off-car |
| avc loadavg | 0 (wake bật? [CHƯA BIẾT]) | 0 | |
| log KB/phút | 0 | 41,2 | ghi log lượt nói/usage — kiểm off-car |

## 3. Tính năng — kết quả

| # | Việc | Kết quả |
|---|---|---|
| D4 camera xi-nhan | **2.69 tắt sau ~1 s** (BUG) ⇒ 2.70 giữ đúng tới khi đèn tắt (log: mở 57,18 s → đóng 62,41 s, đèn tắt 62,33 s) ✅; chiều xoay trái ↺/phải ↻ owner xác nhận **đúng** ✅ |
| D7 nút mic (Hey Kachi bật) | `:wake` ack 6 ms, sẵn sàng nghe 641 ms, mic 99 ms ✅; "bật gió tự động" ⇒ `Control(ac_auto=1)` ✅; native chính không tăng ✅ |
| D12 ô/bố cục từ `:wake` | qua **intent**: ✅ (VietMap vào ô 2, `VdAppHost slot=1`); qua **giọng**: ❌ ASR ra "mở vietmap một"/"mở vietmap" — vế "vào ô số" mất |
| D8 đổi hồ sơ bằng giọng | ❌ ASR: "chuyển sang hồ sơ" (mất "test"), "đổi sang hồ sơ định" (mất "mặc") ⇒ MISMATCH |
| Taskbar khi phản hồi | **[ĐO]** `WindowManager: Changing focus from KachiHome to Window{ed7e7f3 com.byd.launcher}` = `VoiceOverlay$build$5` 1920×1080 của `:wake` **nhận focus** lúc phản hồi, và `mCurrentFocus=null` ngay sau đó (`perf-oncar-2026-09-26/taskbar-window-dump.txt`). **[SUY]** ROM hiện taskbar vì cửa sổ tiền cảnh không còn là launcher-home, và không tự ẩn vì không có cửa sổ nào nhận focus lại — chưa đo trực tiếp luật ẩn/hiện của ROM. Chốt: đặt `FLAG_NOT_FOCUSABLE` cho overlay phản hồi rồi lặp lại đúng lượt nói (SYS-TASKBAR-VOICE-FOCUS) |
| D3 phím vô-lăng | dùng suốt buổi ✅ |
| D5 camera bật/tắt | `KachiHalSignal` 1 luồng; camera lên/đóng theo đèn ✅ |
| D1/D2 AC dock | owner chưa trả lời (bỏ qua vì hết giờ) |
| Crash | `:tts` SIGSEGV `KachiSpeak` (đã biết, cách ly); YouTube tự crash (không phải Kachi) |

## 4. Sự thật xe mới — **nhãn mức bằng chứng theo TỪNG dòng** (không phải cả mục đều [ĐO])
- **[ĐO]** HAL helper báo xi-nhan theo **TRẠNG THÁI**: một `trái=true` lúc bật, một `trái=false` lúc tắt (4,2 s sau) — `perf-oncar-2026-09-26/camera-after-2.70.md` dòng `18:18:57.177` → `18:19:02.325`; KHÔNG nháy theo bóng (không có cặp ON/OFF nào cách nhau ~340 ms trong log). **[SUY]** ⇒ luật "giữ 1,2 s từ ON cuối" sai trên xe này (2.69); giữ tới OFF là đúng (2.70, `CameraHold`) — **[ĐO]** 2.70 mở 57,18 s → đóng 62,41 s (đèn tắt 62,33 s). ⚠ Nguồn báo trạng thái ⇒ phải có đường tắt khi nguồn chết giữa lúc ON: `HalSignalClient.announceOffIfDropped` (soát Pass 2, spec R8).
- Camera fps: **[ĐO]** `dumpsys SurfaceFlinger --latency` cho layer cửa sổ Kachi **không trả frame** trên ROM này (`perf-oncar-2026-09-26/camera-fps-2.70.md:2`) ⇒ đo fps lớp camera bằng cách đó không được. **[ĐO]** gfxinfo launcher lúc camera hiện, 2.70: hai lượt cho hai kết quả KHÁC NHAU — lượt 18:52 (giữ xi-nhan ~20 s) p90 **44 ms** / p99 400 ms / janky **26,9 %**; lượt 18:18 (`camera-after-2.70.md`, cũng có camera hiện) p90 **11 ms** / p99 150 ms / janky **4,67 %**. **[ĐOÁN]** "TextureView vẽ trên luồng chính launcher là nguồn giật" (L2) — hai số trên **không** ủng hộ kết luận đó: nếu chỉ do camera thì hai lượt phải giống nhau. Chốt bằng: `gfxinfo reset` → giữ xi-nhan 20 s, KHÔNG chạm màn, KHÔNG mở Cài đặt → đọc lại; so với cùng 20 s không xi-nhan.
- **[SUY] (đọc source, chưa có WAV chứng minh)** VAD `voice_vad_min_silence_ms` mặc định 600, trần cầu 800 ⇒ **[ĐOÁN]** câu ghép có nghỉ > 0,6–0,8 s bị cắt; **[SUY]** hai cài đặt `voice_endpoint_*` không tác dụng khi `duong=vad`. Chốt: WAV "mở vietmap … vào ô số hai" có khoảng nghỉ 700 ms qua `scripts/emulator/voice-e2e.sh` với hai giá trị `min_silence` (600 vs 1200).
- **[ĐO]** bộ nhận dạng zipformer-vi ra "mở vietmap một" / "chuyển sang hồ sơ" / "đổi sang hồ sơ định" cho ba câu đã nói (log `KachiVoiceSession`) ⇒ vế sau tên app bị bỏ/méo, "test" và "mặc" mất. **[SUY] (1–2 mẫu mỗi loại, chưa phải luật)** nguyên nhân là mô hình không phát được từ tiếng Anh và rớt âm không dấu trọng âm thấp — cần bộ WAV ≥ 10 lượt mỗi câu để lên [ĐO].
- **[ĐO]** logcat xe tràn bộ đệm trong ~30 s (4 700 dòng/5 phút, phần lớn AudioSystem/APM của ROM) ⇒ phải stream `logcat -v time > file`, không dùng `-d`.
- **[ĐO]** `run-as` không dùng được trên bản release ⇒ đọc prefs/tệp riêng chỉ qua cầu kiểm thử (`prefs_set` cần `--es text`, `voice_dump` xuất zip ra `kachi-logs/`).

## 5. Việc off-car sinh ra từ buổi này (ghi backlog, LÀM SAU — owner: "note lại làm sau")
VOICE-SLOT-TAIL-CUT · VOICE-APP-NAME-FUZZY · VOICE-PROFILE-NAME-PHONETIC · SYS-TASKBAR-VOICE-FOCUS (overlay phản hồi `FLAG_NOT_FOCUSABLE` + immersive, giữ đường thoát bằng chạm) · VAD-SILENCE-CAP (trần 800 → 1200, mặc định câu ghép) · CAM-ROT 2.71 (2 dòng trái/phải — đã code, chưa gộp) · SHELL-19 (tra nguồn 19 lệnh/phút từ usage log) · LOG-41KB (vì sao log 41 KB/phút sau 2.69). Dữ liệu ASR: zip WAV `kachi-voice-*.zip` (nếu xuất được) ⇒ `scripts/voice/replay-car-log.py`.
