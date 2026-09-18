# On-car 2026-09-18 tối: GỐC mất binding phím = PIPER CRASH + Hey Kachi chưa có trên xe

> **Trạng thái**: Trace xong on-car (<car-ip>), phím recover TẠM. **CHƯA fix code.** · Xe: Kachi **1.76 (vc77)** (CHƯA OTA 1.77/1.78). Client `/tmp/adb_raw.py`.

## GỐC XÁC ĐỊNH — mất binding phím = **Piper TTS crash native cả tiến trình launcher**
[ĐO tombstone `logcat -b crash`]:
```
20:33:20 F/libc: Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x7c2314bbebeb22 in tid 22211 (KachiSpeak), pid 23597 (com.byd.launcher)
  #21 com.k2fsa.sherpa.onnx.OfflineTts.generate      (native onnxruntime)
  #24 SherpaTtsSpeaker.synthesizeAndPlay+164
  #27 SherpaTtsSpeaker.speakInternal
  #37 ThreadPoolExecutor.runWorker   (thread "KachiSpeak")
```
**Chuỗi nhân quả**: owner nói voice → Kachi đọc reply bằng Piper (`SherpaTtsSpeaker`) → `OfflineTts.generate` **SIGSEGV** (SEGV_MAPERR — con trỏ hỏng, [SUY] memory corruption/OOM dưới áp lực RAM: xe đang chạy GMaps+VietMap+cdr, model Piper 61MB) → **cả tiến trình launcher CHẾT** (`WIN DEATH KachiHome`, `BYDAutoService binderDied`, `Scheduling restart of crashed service FloatingBubbleService`) → a11y service unbind → khi process restart, **rebind kẹt "Binding" không lên "Bound"** (không tự retry) → **phím chết**. Lặp → crash-loop (PID churn 31660→23597→22756→25754).

⚠ `runCatching { Throwable }` trong `SherpaTtsSpeaker` **KHÔNG bắt được** — SIGSEGV native abort tiến trình, không phải exception JVM.

### Đây là gốc HỢP NHẤT 2 lỗi:
1. **Mất binding phím voice** (crash → a11y unbind → rebind kẹt).
2. **"Piper đọc chưa hết câu đã đứng + overlay biến mất"** (finding B của log-case) — KHÔNG chỉ do `SPEAK_SAFETY_MS=10s`; gốc THẬT là **process chết giữa lúc synth**. → finding B reprioritize.

### Loại trừ (đo được):
- KHÔNG phải đói CPU lúc bind: main thread launcher = **S (ngủ)**, CPU 0%, nice -10, không bị LMK giết.
- KHÔNG phải bug bind thuần: bind kẹt vì **process vừa chết**, không phải starvation.

### Recover TẠM (đã làm): toggle a11y **KHÔNG** đủ khi process ở trạng thái post-crash xấu. Cách chạy: **force-stop launcher → tự lên HOME sạch → re-enable a11y** → NavAccessibilityService vào **Bound** → phím chạy. Nhưng **sẽ hỏng lại** lần Piper crash tiếp theo.

## Hey Kachi — CHƯA có trên xe
- Xe ở **1.76**; lớp Android wake (`VoiceWakeService`) ship ở **1.77**. [ĐO] `dumpsys package` không khai VoiceWakeService ⇒ **không có setting + không gọi lên được**. Cần **OTA 1.78**.
- ⚠ Kể cả sau OTA 1.78: công tắc "Hey Kachi" (Cài đặt › Hệ thống, mặc định TẮT) hiện ra, NHƯNG **model KWS chưa host** ⇒ degrade RMS-only ⇒ **KHÔNG thực sự nhận cụm "Hey Kachi"** (chỉ đo baseline CPU). Muốn gọi được cần owner host model KWS (`sherpa-onnx-kws-zipformer-gigaspeech`) lên OTA repo + tokenize. **Chưa dùng được như wake word.**

## FIX — REPRIORITIZE (gói voice-fix)
**#1 MỚI (chặn trên hết): Piper KHÔNG được đá chết launcher.** Hai đường:
- (a) Chạy Piper TTS **ở tiến trình RIÊNG** (`:tts` service) → crash native chỉ giết service TTS, không giết launcher (giữ được a11y binding + cast + voice). Bền nhất.
- (b) **Mặc định dùng System TTS (`AndroidTtsSpeaker`)**, Piper thành opt-in/TẮT — System TTS chạy ở tiến trình dịch vụ TTS của hệ, crash không giết launcher. Nhanh, ít rủi ro. `VoiceSpeakerSelector`/`ClipSpeaker` đã có sẵn đường.
→ Fix này **thay** phần lớn finding B (SPEAK_SAFETY) và **sửa gốc mất-binding-phím** cùng lúc.
Còn lại (thứ tự cũ): ① endpoint 150→600 · ③ overlay taskbar · ④ music UA · ⑤ vietmap parser · D1/D2/D3.
**+ watchdog binding**: khi phát hiện NavAccessibilityService enabled-mà-không-Bound quá N giây → **force-stop+re-enable** (đường đã proven recover), không chỉ grant.
