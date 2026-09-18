# Voice — findings từng case + 3 lỗi cảm nhận (log thật 2026-09-18) → resolution

> **Trạng thái**: Trace xong off-car từ log THẬT, **CHƯA implement** (owner: tìm findings + đưa vào giải quyết). · **Ngày**: 2026-09-18 · **Nguồn**: `logs/20260918/*.zip` — **53 phiên voice unique** (owner + anh em), mỗi phiên có `.wav` + `.json` (`VoiceWavProbe`): heard/decision/intents/replies + timing endpoint/speech/silence. App trên xe: 1.76.

## A. NGẮT CÂU QUÁ GẤP (owner: "ngắt hơi nhanh, chưa nói hết đã ngắt") — **XÁC NHẬN, gốc rõ**
[ĐO 90/90 case] `silence_ms` = **150–174ms** ở MỌI case. Gốc: **`VoiceVadTrim.MIN_SILENCE_MS = 150`** — Silero VAD chốt câu sau **150ms im**. Khoảng ngừng lấy hơi/nghĩ giữa câu (200–500ms) > 150ms ⇒ **bị cắt**.
Bằng chứng cắt giữa câu: `gặp gu` (sp=814ms — "gập gương chiếu hậu" cụt còn 2 tiếng) · `chỉ số bụi mịn` (cụt trước "là bao nhiêu" — bản đủ thì hiểu) · `lấy do`.
⚠ Vì sao off-car không thấy: eval `VoiceVadTrim §8` dùng câu thu SẴN (không ngừng giữa chừng) ⇒ 150ms "0/1899 cắt giữa câu". Giọng lái xe THẬT có ngừng ⇒ cắt.
**Fix**: nâng `MIN_SILENCE_MS` mặc định **150 → ~600ms** (trần hiện `MAX_MIN_SILENCE_MS=800`; núm `voice_vad_min_silence_ms` chỉnh được trên xe — owner test nhanh được ngay). Head-trim (margin 0) vẫn cắt đuôi im ⇒ **không hại độ chính xác decode** (cắt ở hết đoạn tiếng, không nạp thêm im). Kiểm lại tương tác trim khi nâng.

## B. PIPER TẮT GIỮA CÂU + OVERLAY BIẾN MẤT SỚM (owner: "feedback chưa hết câu đã mất overlay, đứng nửa chừng") — gốc rõ, nối với CPU
Flow: `scheduleClose(SPEAK_SAFETY_MS=10s)` → `speakLines` (Piper) → `onReplyDone` (nán `LINGER_MS=2.5s` sau khi đọc xong). `SPEAK_SAFETY` là lưới an toàn PHÒNG khi `onDone` không về.
**Gốc**: dưới tải CPU xe (**load 14** — mục C dưới), Piper (`SherpaTtsSpeaker`, ONNX single-thread) synth+phát một câu dài (vd reply APP_CLOSE, "✓ Mở Tất cả kính — chưa kiểm…") **vượt 10s** ⇒ `SPEAK_SAFETY` đóng overlay **giữa lúc đang đọc**; và AudioTrack **underrun** khi CPU đói ⇒ tiếng **đứng nửa chừng**. Off-car (CPU rảnh) synth nhanh <10s nên không thấy.
**Fix**: (1) `SPEAK_SAFETY_MS` theo **độ dài câu** (ms/ký tự) thay vì 10s cứng — hoặc chỉ đóng theo `onReplyDone`, coi safety là backstop rất dài; (2) giảm tải Piper (câu ngắn hơn / TTS nhẹ hơn / hạ chất lượng khi load cao); (3) **nối mục C** — giảm GMaps-in-slot + VietMap để CPU thở.

## C. OVERLAY KÉO TASKBAR HỆ THỐNG LÊN (owner: "không muốn cái này") — **XÁC NHẬN, gốc rõ**
[ĐO code `VoiceOverlay.kt`] Cửa sổ overlay `TYPE_APPLICATION_OVERLAY` + `isFocusableInTouchMode=true` (cố ý, để bắt phím Back) nhưng **KHÔNG set cờ ẩn system bars** (immersive). Khi overlay lấy focus, Android **hiện lại status/nav/taskbar** vì cửa sổ này thiếu cờ immersive mà launcher đang có.
**Fix**: set immersive trên view/cửa sổ overlay — `SYSTEM_UI_FLAG_HIDE_NAVIGATION | FULLSCREEN | IMMERSIVE_STICKY` (hoặc `WindowInsetsController.hide(systemBars())`) + `FLAG_LAYOUT_IN_SCREEN`. Giữ nguyên đường bắt phím Back.

## D. PARSE — 31/90 KHÔNG HIỂU + nhiều MAP SAI (nguy hiểm)
### D1. MAP SAI — câu HỎI (đọc) → lại bắn LỆNH GHI / sai datum (ưu tiên cao, an toàn)
| heard | ra | đúng phải là |
|---|---|---|
| `tất cả cửa đang khóa hay đang mở` | **Control(sunroof=1)** — MỞ cửa sổ trời! | READ trạng thái khóa cửa (thiếu datum) |
| `máy lạnh máy lạnh đang bao nhiêu độ` | Read(**media_vol**) | Read nhiệt độ AC |
| `chỉ số xăng` | Read(**speed**) | Read nhiên liệu |
| `áp suất lốp bên trái là bao nhiêu` | Read(**soc**) | Read(tyre_p_*) |
| `bật đèn khẩn cấp` | Control(**trunk**=1) — mở cốp! | đèn hazard (chưa map) |
| `mở xi nhan trái` | Control(**drl**=1) | xi nhan (chưa map) |

### D2. KHÔNG HIỂU — câu hợp lệ nhưng parser/synonym thiếu
- `nhiệt độ đang bao nhiêu` (×2) — 1.73 sửa "bao nhiêu độ", chưa bắt **"đang bao nhiêu"**.
- `xăng còn bao nhiêu`, `hev đi được bao nhiêu` — quãng đường/nhiên liệu.
- `mở xi nhan phải` (×2) — xi nhan chưa có control.
- `chỉ số bụi mịn` (thiếu "là bao nhiêu") — bản đủ thì hiểu ⇒ cần bắt cụm ngắn.
- `ghế mát mức mấy` → ASR "ghế **mất** mấy" — cần khớp-mờ mát/mất + đọc mức ghế.
- `kiểm tra áp suất`, `chỉ số áp suất lốp` — đọc áp suất lốp thiếu cách nói.
### D3. KHÔNG HIỂU đúng nhưng trả lời cụt — feature đã gỡ (ADAS-PURGE/FEATURE-FILTER)
`kiểm tra dây an toàn` (dây an toàn — gỡ) · `gập gương chiếu hậu` (gương gập — gỡ) · `xe đang sạc pin hay không` (sạc — gỡ, nay ra SOC). ⇒ nên trả lời "tính năng này đã bỏ" thay vì KHÔNG HIỂU chung.
### D4. Đúng (đối chứng) — parser CHẠY tốt nhiều câu
`kính lái đang mở bao nhiêu → Read(window_lf) 0%` · `bin đang bao nhiêu → SOC` · `giảm nhiệt độ xuống ba độ → temp -3` · `mở kính lái phía trước bên trái → window=1` · `xe đang ở chế độ lái nào → op_mode` · `dẫn đường đến 1294 võ văn kiệt → Nav` (GMaps, chạy) · `mở youtube/google map → OpenApp`.

## E. MUSIC + VIETMAP (đã trace on-car — doc `oncar-voice-music-vietmap-2026-09-18.md`)
- Music: `VoiceYoutubeResolver` UA **di động** → YouTube 0 videoId → lùi search. Fix: **desktop UA** ([ĐO] →225 videoId).
- VietMap: "bằng vietmap" không tách → GMaps + địa chỉ rác. Fix: parser tách "bằng &lt;app&gt;" cho nav + khớp-mờ tên app.

## RESOLUTION — thứ tự đề xuất (chờ owner cho làm)
1. **A** — `MIN_SILENCE_MS` 150→~600 (đau nhất, 1 dòng default, có núm test nhanh trên xe).
2. **C** — overlay set immersive (ẩn taskbar), nhỏ gọn.
3. **E** — resolver desktop UA (music) + parser app-selector nav (vietmap).
4. **B** — `SPEAK_SAFETY_MS` theo độ dài câu + giảm tải Piper (nối CPU).
5. **D1** — chặn câu HỎI bắn LỆNH GHI (an toàn) + sửa map sai datum.
6. **D2/D3** — synonym "đang bao nhiêu"/áp suất/ghế mát + reply "đã bỏ" cho D3.

## Núm chỉnh trên xe (owner test ngay, không cần build)
`voice_vad_min_silence_ms` (80–800, default 150 → thử **600**) · `voice_endpoint_silence_ms` · `voice_endpoint_min_speech_ms` · `voice_endpoint_floor_cap`.
