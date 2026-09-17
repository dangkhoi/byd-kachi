# Voice + chạm trên xe 2026-09-17 — số đo lượt xe + thay đổi 1.70 cần verify

> **Trạng thái**: Current · **Mục đích**: gom số đo lượt xe owner 08:35–09:10 (bản 1.69) + liệt kê thay đổi
> 1.70 cần xác nhận trên xe (mỗi mục kèm cách đo). Nguồn số: handoff `session-2026-09-17-visual-voice-touch.md`
> §1.2 (scratchpad `car-0917/`, đã chép ra `~/.kachi/car-0917/`). Mọi số [ĐO] trên xe owner DL3 `eng.build.20260204`,
> `getenforce`=Enforcing, load ~18.

## 1. Bệnh [ĐO] trên xe 1.69 (đã vá ở 1.70 — chờ xe xác nhận)

| Bệnh | Số đo 1.69 | Vá 1.70 | Cách verify trên xe |
|---|---|---|---|
| Bấm → mic mở chậm | **1,5 s** lượt đầu (dựng overlay + nạp VAD ONNX) | VAD preload singleton (nạp 1 lần, reset mỗi lượt) | `logcat -s KachiVoiceTiming`: dòng "sẵn sàng nghe sau … ms" < 300ms lượt sau |
| Chime chặn luồng nghe | `ToneGenerator status -110` chặn **3,0 s** | VoiceChime PCM async + cầu chì | `logcat`: không còn dòng ToneGenerator; "mic mở sau … ms" nhỏ |
| Tiếng đầu mất | đệm `AudioRecord` chỉ 80ms | đệm ≥ 1s (`MIN_BUFFER_MS`) | nói ngay sau bấm → nhận đúng (không mất đầu câu) |
| Giải mã im lặng → bịa | 7/10 lượt chính giải mã 8,2s im lặng ra "ừm/vâng giấc mơ" | VAD silence gate (route=vad + !sawSpeech) | `logcat`: "bỏ giải mã: lượt này không có tiếng" ở lượt im |
| Overlay tắt giữa câu feedback | LINGER 2,5s < câu 3-4s | overlay sống theo onDone TTS + SPEAK_SAFETY | nói lệnh → nghe/nhìn TRỌN câu feedback rồi tấm chữ mới biến |
| Model fp32 vẫn nạp dù chọn int8 | `state.voice_model.loaded_id` fp32 | VoiceModelStore ưu tiên int8 + engine reload | bridge `state` → `voice_model.loaded_id` = int8 id |
| Vòng lặp hội thoại hoang | **309** mic/7 phiên, "ừ"×102 | single-flight + cầu chì 12/phút + máy trạng thái (canOpenMic chỉ LISTENING) | 12 phút lái: `KachiPerf` mic ≤ 12/phút |

## 2. Chạm trong ô — sepolicy đã chốt [ĐO]

- Daemon socket abstract bị sepolicy **Enforcing** chặn ở lượt NỐI: `IOException: Permission denied` 25/25 lượt
  (daemon thường trú vẫn sống: `bind failed: Address already in use`). Client khởi động lại daemon ~5,5 s/lần
  → 45 tệp `inputd-*.log` trong vài phút, `KachiPerf shell=36/phút`.
- **Vá 1.70**: (a) cầu chì daemon 1 chu kỳ sepolicy ⇒ thôi hẳn tới lần mở app sau; (b) `input` no-retry (bỏ tap
  đôi); (c) kênh TCP loopback `127.0.0.1:<port>+token` thay socket abstract (cả hai miền là net_domain, không
  qua luật connectto).
- **Verify xe**: bridge `state` → `inputd.fused`/`inputd.port`; `logcat` "CẦU CHÌ: không khởi động lại nữa";
  YouTube trong ô: vuốt cuộn được + tap ĐƠN (không đôi). Nếu TCP cũng bị chặn ⇒ ghi `avc:`, cử chỉ `input` là
  đường chính (đã có `GestureFallback`).

## 3. Dữ liệu xe đã đo → đã code (KHÔNG cần lên xe lại, chỉ verify)

| Mục | Số đo | Verify xe |
|---|---|---|
| Cốp | `voiceCtlBackDoor(1)`=mở · `(3)`=đóng | nói "mở cốp"/"đóng cốp" → cốp chạy |
| Ghế mát | OFF=1 · mức1=2 · mức2=3 (không mức 3) | chạm ghế mát 3 nấc khớp màn BYD |
| AC AUTO | `getAcControlMode` 0=auto/1=tay (đọc) | ô "Điều hòa AUTO" hiện đúng trạng thái |

🚗 **Còn phải đo**: `seath` (ghế sưởi) thang mức · `voiceCtlBackDoor(2)` lúc cốp đang chạy · listener Wiper
(RainPolicy) · RTF model G trên ARM (VFT-2) · bấm-mic-nói-liền có nhận không.

## 4. Harness voice-e2e (off-car)

`scripts/emulator/voice-e2e.sh --only all` — [ĐO] `bash -n` sạch, `voice_e2e_json.py` `py_compile` sạch.
Chạy E2E thật cần emulator (`adb reverse tcp:5555 tcp:5555` + cài APK). Phiên này KHÔNG có emulator ⇒ chỉ kiểm
tĩnh; E2E chạy ở lượt có máy ảo (mốc trước: T1 108/109, đỏ duy nhất t29 lỗi bộ ca 09-15).
