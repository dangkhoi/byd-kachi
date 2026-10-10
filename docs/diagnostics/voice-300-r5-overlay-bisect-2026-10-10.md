# 3.0 R5 VOICE-OVERLAY-FLICKER-SLOW — bisect phiên bản trên máy ảo + gốc CPU của tấm chữ

> **Trạng thái**: Current · **Ngày**: 2026-10-10 · Spec `docs/specs/kachi-300-plan.html` R5.
> Owner (xe, 2.99): *"cái voice lúc hiện lên nó hơi chập chờn, lúc hiện lên tý tắt, rồi đang nghe, xử lý cũng có vẻ chậm hơn trước"*;
> sau đó: *"không phải từ 2.98 đâu, nên so nhiều version trước nữa"* ⇒ bisect 2.85 → 2.99.
> Nhãn: **[ĐO]** máy ảo lượt này · **[SUY]** đọc source · **[ĐOÁN]** · **[CHƯA BIẾT]**.

## 0. Cách đo (tái lập được)

| Thành phần | Chi tiết |
|---|---|
| Máy ảo | `emulator-5554` AVD `clusternav10` (API 29, x86_64, GPU mặc định khi `-no-window`), bật âm thanh + `-grpc 8554` |
| Bản | APK release đã đăng lấy từ git (`git show <commit>:apk/Kachi-<ver>-release.apk`, kiểm `aapt2 badging`): 2.85 `b50db44` · 2.88 `8880d76` · 2.92 `8fbdf96` · 2.95 `edc7ecd` · 2.96 `559f247` · 2.98 `420f16b`; 2.99 = `assembleRelease` của `81c8357` trong worktree riêng (cùng khoá ⇒ `install -r -d` giữ dữ liệu). Mô hình `zipformer-vi-int8-2025-04-20` chép sẵn vào `files/sherpa/`. |
| Cấu hình | giống nhau mọi bản: `voice_wake_enabled=true` + phím 231 → `__KACHI_VOICE__` (mô hình nằm ở `:wake`, đúng chế độ xe 10/10 — phiên chạy ở pid `:wake`) |
| Tiếng | gRPC `EmulatorController.injectAudio` (16 kHz mono, thời gian thực) bơm vào micro ảo: 0,8 s lặng + `w02.wav` (*"bật đèn đọc"*, `say -v Linh`) + 4 s lặng. Cùng tệp, cùng đường mỗi lượt. |
| Lối vào | **phím**: `cmd activity start-foreground-service … -a com.byd.launcher.LISTEN_NOW --es entry phim` (đúng đường `AssistantLauncher.launchKachiVoice`) · **nút mic**: `input tap` vào nút *Talk to car* (đường `VoiceEntry.tryWake` → ack) · **từ đánh thức** (thử thêm, xem §3) |
| Tấm chữ | vòng đọc `dumpsys SurfaceFlinger --list` mỗi ~10 ms trên máy (2 ms/lượt): lớp `<id> com.byd.launcher#0` = cửa sổ `TYPE_APPLICATION_OVERLAY` của `VoiceOverlay` ⇒ đếm số cửa sổ khác nhau + số lần chuyển có↔không mỗi phiên |
| Độ trễ | `KachiVoiceTiming: ngắt câu` → `KachiVoiceSession: quyết định` (logcat `-v monotonic`); mốc bấm = `/proc/uptime` ngay trước lệnh |
| CPU | `/proc/<pid :wake>/stat` utime+stime trước/sau cửa sổ ~11 s; theo luồng: `/proc/<pid>/task/*/stat` |

## 1. Bảng bisect (trung vị 3 lượt/bản, sau 1 lượt khởi động bỏ đi) — [ĐO]

| Bản | Lối | bấm→tấm chữ hiện (ms) | cửa sổ/phiên | chuyển có↔không | ngắt câu→quyết định (ms) | CPU `:wake` / phiên (ms) |
|---|---|---|---|---|---|---|
| 2.85 | phím | 10 | 1 | 2 (hiện 1 lần, tắt 1 lần) | 236 | 4 050 |
| 2.85 | nút mic | 120 | 1 | 2 | 235 | 4 190 |
| 2.88 | phím | 20 | 1 | 2 | 234 | 4 190 |
| 2.88 | nút mic | 100 | 1 | 2 | 230 | 4 250 |
| 2.92 | phím | 20 | 1 | 2 | 236 | 4 260 |
| 2.92 | nút mic | 110 | 1 | 2 | 465 ¹ | 5 360 |
| 2.95 | phím | 20 | 1 | 2 | 236 | 4 030 |
| 2.95 | nút mic | 90 | 1 | 2 | 232 | 4 010 |
| 2.96 | phím | 10 | 1 | 2 | 235 | 3 990 |
| 2.96 | nút mic | 80 | 1 | 2 | 246 | 4 030 |
| 2.98 | phím | 20 | 1 | 2 | 233 | 5 140 |
| 2.98 | nút mic | 80 | 1 | 2 | 239 | 4 650 |
| 2.99 | phím | 10 | 1 | 2 | 238 | 4 080 |
| 2.99 | nút mic | 110 | 1 | 2 | 231 | 5 180 |

¹ một lượt `giải mã` 200 ms do tranh CPU nhất thời (lượt khác của cùng bản 230–250 ms) — nhiễu, không lặp lại.

**Kết luận bisect [ĐO]:** trên máy ảo **không bản nào hồi quy** so với 2.85 ở cả bốn chỉ số; tấm chữ luôn **một cửa sổ, hiện một lần, tắt một lần** (không có khe tắt-giữa-chừng ở độ phân giải 10 ms). Điểm cuối 2.85 ≈ 2.99 ⇒ bisect dừng: không có "bản đầu tiên hồi quy" để chỉ ra trên máy ảo. Máy ảo chạy trên CPU Mac nhanh hơn nhiều đầu xe ⇒ không tái hiện được độ chậm tuyệt đối của xe; chỉ số tương đối giữa các bản là phẳng.

## 2. Phát hiện chính — gốc CPU có từ 2.16, không theo bản — [ĐO] + [SUY source]

CPU `:wake` ~4–5 s cho MỘT phiên ~11 s (lúc rảnh 10 s: ~10 ms) là bất thường cho một lượt giải mã ~100 ms. Chia theo luồng (2.99, phím, 3 lượt):

| Luồng | CPU / phiên |
|---|---|
| `RenderThread` | **4 030 · 4 340 · 4 590 ms** |
| mọi luồng khác cộng lại (VAD, sherpa, main…) | 330 · 280 · 390 ms |

Gốc [SUY source, `app/…/voice/VoiceWaveView.kt:60` bản 2.99]: `onDraw` kết thúc bằng `if (listening || level > 0.01f) postInvalidateOnAnimation()` — **đang nghe thì hẹn khung kế vô điều kiện** ⇒ vòng sóng vẽ lại 60 khung/giây suốt phiên kể cả khi mức âm đứng yên. View này nằm trong `VoiceOverlay` — cửa sổ **phủ toàn màn, `TRANSLUCENT`** ⇒ mỗi khung là một lượt dựng + ghép toàn màn. Có từ commit `40ff32fd` (2.16, *"waveform vòng tròn"*) ⇒ khớp bảng §1: mọi bản 2.85–2.99 đều tốn như nhau.

Nối với triệu chứng xe [ĐOÁN — chưa đo trên xe]: SoC đầu xe yếu, buổi 10/10 có video YouTube chạy bên dưới tấm chữ ⇒ 60 khung/giây ghép toàn màn tranh CPU/GPU với VAD + giải mã sherpa (cùng `:wake`) ⇒ khung hình giật (*"chập chờn"*) và xử lý chậm. Owner thấy rõ hơn *"trước"* có thể vì nay thường dùng giọng nói khi video/nhạc đang chạy (xem §3), không vì một bản cụ thể.

## 3. Ca có nhạc nền — đúng điều kiện buổi xe 10/10 — [ĐO]

Bơm *"bật đèn đọc"* chồng lên một giọng hát liên tục (TTS, −12 dB, dài 9 s):

| Bản | bấm → quyết định | đường ngắt | chữ nghe được |
|---|---|---|---|
| 2.85 | 9,4 · 10,1 · 9,9 s | `hết trần` 8 s (VAD không bao giờ thấy im) | *"bật đèn đọc về những người yêu thích và chờ đợi em ơi …"* |
| 2.99 | 9,7 · 8,4 · 8,4 s | như trên | như trên |
| (không nhạc, mọi bản) | 2,8 s | `ngắt câu` | *"bật đèn đọc"* |

⇒ Có nhạc là phiên luôn chạm trần 8 s + giải mã nguyên cửa sổ 8 s (máy ảo 0,2–1,6 s) + nghe lẫn lời hát — **như nhau mọi bản** [ĐO]. Đây là phần *"xử lý chậm"* lớn nhất khi có nhạc; chữa ở R4 `VOICE-DUCK-MUSIC` (giảm/dừng nhạc khi nghe), không ở R5.

Thử thêm (không đổi kết luận): từ đánh thức *"Hây ca chi"* bơm bằng gRPC ⇒ 3/4 lượt nổ, tấm chữ hiện 23–47 ms sau `WAKE khớp`, 1 cửa sổ/phiên. Khởi động nguội (bấm ngay khi vừa mở HOME): lượt đầu sau cài cho 3,9–6,0 s nạp mô hình ở 2.99 nhưng chạy lại cho 1,4 s — bằng 2.85/2.98 (1,4–1,6 s) ⇒ nhiễu dexopt sau cài, **không** phải hồi quy.

## 4. Sửa — gốc, không thêm tầng

- `core/…/voice/VoiceWaveMotion.kt` (mới, thuần): `step(level, goal)` (nội suy 0,25 như cũ, vào trong `EPS = 0,01` thì chốt đúng đích) + `moving(level, goal)`.
- `app/…/voice/VoiceWaveView.kt`: `onDraw` chỉ hẹn khung kế khi `moving(level, goal)`; `setLevel` chỉ hẹn khung khi mức mới lệch thấy được (mức âm tới 5 lần/giây — khúc 200 ms). Hình vẽ, hệ số mượt, pha nghe/mờ dần giữ nguyên.
- Bài khoá: `core/src/test/…/voice/VoiceWaveMotionTest.kt` (4 ca: im lặng đều ⇒ ≤ 12 khung thay vì 480; đang nói vẫn nhích; dừng khi tới đích; mờ dần kết thúc).

Trước/sau (2.99 vs 2.99+bản vá, cùng máy ảo, 3 lượt) — [ĐO]:

| Chỉ số | 2.99 | 2.99 + vá |
|---|---|---|
| `RenderThread` / phiên lặng | 4 030–4 590 ms | **440–610 ms** |
| CPU `:wake` / phiên (phím) | 4 080 ms | **760 ms** (−81 %) |
| CPU `:wake` / phiên (nút mic) | 5 180 ms | **850 ms** |
| CPU `:wake` / phiên có nhạc | 6 110–7 830 ms | **2 370–2 420 ms** |
| bấm→tấm chữ hiện | 10 ms | 20 ms (trong độ phân giải đo) |
| ngắt câu→quyết định | 238 ms | 235 ms |
| cửa sổ/phiên · chuyển | 1 · 2 | 1 · 2 |
| Ảnh tấm chữ lúc nghe | vòng + 2 vòng mờ + *"Listening…"* | như cũ (ảnh `fix_speech.png` trong scratchpad phiên) |

Chi phí bản vá: 0 vòng lặp mới, 0 luồng, 0 listener; bớt ~85 % khung vẽ khi lặng.

## 5. Còn lại cho xe 🚗 (một lượt, không cần gõ lệnh)

1. Cài bản có vá, nói 3 câu khi **có video/nhạc chạy** và 3 câu khi lặng; chụp màn *Chẩn đoán*.
2. Đối chiếu trong `usage-wake-*.log` (KachiLog 2.99 R7 đã ghi `:wake`): `KachiVoiceTiming: sẵn sàng nghe sau … ms` · `ngắt câu`/`hết trần` · `giải mã … ms` · `KachiVoiceRec: createStream(hotwords) … ms` · `KachiVoiceSession: quyết định`. Chỉ số cần: bấm→sẵn sàng < 300 ms, `giải mã` khi lặng, tỉ lệ `hết trần` khi có nhạc.
3. Hiện tượng *"hiện lên tý tắt rồi đang nghe"* **[CHƯA BIẾT]** gốc trên xe: máy ảo không tái hiện ở mức cửa sổ (1 cửa sổ/phiên ở mọi bản, mọi lối). Hai giả thuyết chốt bằng log trên: (a) khung giật do CPU (§2) — hết sau bản vá thì đúng; (b) hai phiên nối nhau (dòng `WakeSessions: LISTEN_NOW giữa lúc phiên cũ đang nói/nán — cắt phiên cũ` hoặc `KachiVoiceEntry: … không ack … lùi về phiên in-process`) — có dòng đó thì là ca khác, mở việc riêng.
