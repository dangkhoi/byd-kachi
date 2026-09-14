# Nghiên cứu: STT tiếng Việt CHẠY TẠI MÁY cho head unit BYD DiLink3

> **Trạng thái**: Current · **Cập nhật**: 2026-09-14 · **Loại**: Diagnostics (research, có nguồn URL) ·
> **Owner**: dangkhoi · **Bối cảnh yêu cầu** (owner 2026-09-14): *"API key quá khó cho người dùng cuối, cần dễ
> dùng; đừng bỏ on-device sớm; research mini model nào tiếng Việt S2T ngon"*.
>
> **Mục đích**: chọn tầng NGHE (ASR/STT) tiếng Việt chạy **hoàn toàn tại máy**, KHÔNG cần user nhập API key,
> chạy được trên DiLink3, **thay tầng Vosk hiện tại** — giữ nguyên `VoiceIntentParser`/`VoiceDispatcher` (pha
> CHỮ + pha hiểu ý ở `:core` đã xong, xem backlog **V1**).

**Quy ước mức bằng chứng** (theo `.kiro/steering/conversation-protocol.md` + CLAUDE.md §2):
`[ĐO]` = đọc được trực tiếp từ nguồn (model card / doc / benchmark công bố) · `[SUY]` = suy luận khớp dữ
kiện, chưa đo trực tiếp trên phần cứng này · `[ĐOÁN]` = mới hợp lý · `[CHƯA BIẾT]` = phải đo trên xe mới biết.

> ⚠ **Lưu ý mức bằng chứng tối đa của tài liệu này**: mọi con số WER/RTF dưới đây là **[ĐO đọc được từ
> benchmark của tác giả]**, KHÔNG phải [ĐO trên DiLink3]. WER công bố đo trên tập test sạch (VLSP/VIVOS),
> **không** đo với mic mảng 4 kênh + ồn đường + giọng người dùng thật. RTF công bố đo trên CPU máy chủ/desktop,
> **không** phải 8 nhân của DiLink3. Nâng bất kỳ dòng nào lên "chắc chắn chạy tốt trên xe" mà chưa có phép đo
> mới = vi phạm CLAUDE.md §2/§14. Các mục cần đo liệt kê ở **§11**.

---

## 0. TL;DR — kết luận & đề xuất xếp hạng

**Bối cảnh phần cứng [ĐO từ yêu cầu]**: BYD DiLink3, Android 10 (API 29), arm64-v8a, 8 nhân, RAM ~7.8 GB,
KHÔNG root, KHÔNG platform-signed. Mic mảng **4 kênh 16 kHz**. Đã thử `vosk-model-small-vn-0.4`: **nói cả câu
chỉ ra 1 từ — KHÔNG đủ**. Kiki (Zalo) chạy tốt nhưng ASR ở **trên mây** ([ĐO] `kiki-car-RE-2026-09-14.md`).

**Ứng viên đứng đầu — sherpa-onnx + mô hình Zipformer tiếng Việt (transducer)**

- Đây là con đường **on-device thật, có sẵn Android AAR/JNI**, offline hoàn toàn, **không cần API key**, và
  **có mô hình tiếng Việt CHUYÊN DỤNG** (không phải model nhỏ đa ngữ như Vosk small).
- WER công bố **8–12% trên VLSP** — vượt xa Vosk small; RTF **~0.025 trên CPU** (12 s audio → 0.3 s) [ĐO model
  card] ⇒ thừa sức cho 8 nhân A10 nếu số này chỉ giảm vài lần trên mobile.
- **VẤN ĐỀ GIẤY PHÉP phải chốt trước** (xem §3.1 + §10): bản `hynt/Zipformer-30M` là **CC-BY-NC-ND-4.0**
  (phi thương mại + KHÔNG được sửa) — **không dùng cho sản phẩm phát hành** nếu chưa xin phép. Bản
  `sherpa-onnx-zipformer-vi-2025-04-20` (70k giờ) **[CHƯA BIẾT] giấy phép** — phải xác minh trước khi chọn.

**Dự phòng — whisper.cpp + PhoWhisper-base (ggml)**

- **Giấy phép sạch: BSD-3-Clause** ([ĐO] model card PhoWhisper-small) ⇒ thương mại OK, không vướng NC/ND.
- Độ chính xác tiếng Việt tốt ([ĐO] PhoWhisper-base VIVOS **8.46% WER**).
- **Nhược**: Whisper **KHÔNG streaming** (cửa 30 s), nặng CPU hơn transducer; phải **tự convert** base→ggml
  (medium đã có sẵn nhưng quá to). Với phiên **bấm-để-nói ≤ 9 s** hiện tại thì mô hình không-streaming vẫn
  dùng được — nhưng **RTF trên A10 là [CHƯA BIẾT]**, phải đo.

**TTS phản hồi giọng**: ưu tiên **sherpa-onnx VITS tiếng Việt** (tự chứa, không phụ thuộc Google/GMS); Google
TTS VN offline chỉ là dự phòng NẾU máy có sẵn engine Google TTS (head unit non-GMS **[CHƯA BIẾT]** có không).

**Loại sớm**: Android `SpeechRecognizer` on-device (**cần API 33**, DiLink3 chỉ API 29 — **bất khả thi**);
SenseVoice (**không có tiếng Việt**); Vosk (cả small lẫn big 78 M — vẫn quá nhỏ, small đã fail thực địa).

---

## 1. Bảng so sánh tổng hợp

Cột **WER-VN**: [ĐO đọc từ benchmark tác giả], tập test trong ngoặc. Cột **RTF@A10**: [CHƯA BIẾT] trên
DiLink3 — chỉ ghi ước lượng dựa trên số công bố + kiến trúc. **Cỡ** = cỡ mô hình tải về (không tính engine).

| Giải pháp | Model | WER-VN [ĐO công bố] | Cỡ | RTF @A10 | Offline? | Cần setup gì (end-user) | Giấy phép | Tích hợp Android | Streaming |
|---|---|---|---|---|---|---|---|---|---|
| **sherpa-onnx + Zipformer-VN** | `zipformer-vi-30M int8` (6000h) | VLSP2020 **12.3%**, VLSP2025 **~8%** | ~**40–80 MB** (int8, enc+dec+joiner) | [SUY] rất thấp (công bố RTF ~0.025 CPU server) | ✅ 100% | không gì (tải model 1 lần trong app) | ⚠ **CC-BY-NC-ND-4.0** | ✅ **AAR/JNI chính thức**, Kotlin | ✅ có |
| sherpa-onnx + Zipformer-VN (bản lớn) | `zipformer-vi-2025-04-20` (70k h) | [CHƯA BIẾT] công bố | [CHƯA BIẾT] (~vài chục MB int8) | [SUY] thấp | ✅ | không gì | ⚠ **[CHƯA BIẾT]** — phải xác minh | ✅ AAR/JNI | ✅ |
| **whisper.cpp + PhoWhisper-base** | ggml q5/q8 (tự convert) | VIVOS **8.46%**, CMV **16.2%** | ~**57 MB** (base Q5) | [CHƯA BIẾT] — Whisper nặng hơn | ✅ | không gì | ✅ **BSD-3-Clause** | ⚙ JNI (whisper.cpp) tự wrap | ❌ (cửa 30 s) |
| whisper.cpp + PhoWhisper-small | ggml (tự convert) | VIVOS **6.33%**, CMV **11.1%** | ~150–250 MB q5 | [SUY] chậm hơn base ~3× | ✅ | không gì | ✅ BSD-3-Clause | ⚙ JNI | ❌ |
| wav2vec2-VN-250h (ONNX) | CTC + 4-gram LM | VIVOS **6.15%** | ~360 MB fp32 (chưa quant) | [CHƯA BIẾT] | ✅ | không gì | ⚠ [CHƯA BIẾT] (thường CC-BY-NC) | ⚙ ONNX Runtime tự wrap, cần KenLM ngoài | ❌ |
| Vosk small VN | `vosk-model-small-vn-0.4` | — (đã fail thực địa) | **32 MB** | thấp | ✅ | không gì | ✅ Apache-2.0 | ✅ AAR (đang dùng) | ✅ |
| Vosk big VN | `vosk-model-vn-0.4` | — (server-grade nhưng vẫn nhỏ) | **78 MB** | thấp | ✅ | không gì | ✅ Apache-2.0 | ✅ AAR | ✅ |
| Android `SpeechRecognizer` on-device | Google | — | — | — | ⚠ chỉ ≥API 33 | Google app + GMS + tải gói giọng | (đóng) | ✅ nhưng **API 33** | — |
| Android `SpeechRecognizer` online | Google | tốt | — | — | ❌ cần mạng | GMS + Google app + mạng | (đóng) | ✅ nhưng non-GMS = 0 | — |
| SenseVoice (sherpa) | — | **KHÔNG có tiếng Việt** | ~230 MB | — | ✅ | — | (loại) | ✅ | ❌ |
| VieNeu | chủ yếu **TTS** (STT ít tài liệu) | [CHƯA BIẾT] | — | — | ✅ (sau tải) | tải model | **thương mại ~$5000/năm** | SDK | — |

---

## 2. PhoWhisper (VinAI) — Whisper fine-tune tiếng Việt

**[ĐO] Nguồn**: bài ICLR 2024 Tiny Paper + repo VinAI + model card HuggingFace.

- **Kiến trúc**: fine-tune Whisper đa ngữ trên **844 giờ** tiếng Việt (CMV-Vi = phần VN của Common Voice,
  VIVOS, VLSP 2020 ASR, + tập private lớn), 5 cỡ. [ĐO README]
- **Bảng WER (%) — [ĐO] từ README VinAI**:

  | Model | Params | CMV-Vi | VIVOS | VLSP2020 T1 | VLSP2020 T2 |
  |---|---|---|---|---|---|
  | tiny | 39M | 19.05 | 10.41 | 20.74 | 49.85 |
  | base | 74M | 16.19 | 8.46 | 19.70 | 43.01 |
  | small | 244M | 11.08 | 6.33 | 15.93 | 32.96 |
  | medium | 769M | 8.27 | 4.97 | 14.12 | 26.85 |
  | large | 1.55B | 8.14 | 4.67 | 13.75 | 26.68 |

- **Giấy phép**: **BSD-3-Clause** [ĐO model card `vinai/PhoWhisper-small`] ⇒ **dùng thương mại được**, đây là
  điểm mạnh lớn so với các model NC.
- **ggml / whisper.cpp**: `dongxiat/ggml-PhoWhisper-medium` **đã có sẵn** [ĐO search] — nhưng **medium = 769M
  quá nặng** cho A10. **base/small chưa thấy bản ggml sẵn** ⇒ phải **tự convert** bằng script
  `whisper.cpp/models/convert-*` rồi quantize q5/q8. [SUY] khả thi, whisper.cpp có convert pipeline chuẩn.
- **ONNX**: `huuquyet/PhoWhisper-*` có bản ONNX (Transformers.js) cho tiny→large [ĐO search] — nhưng để chạy
  sherpa-onnx cần format encoder/decoder riêng của sherpa, **không phải drop-in** [SUY].
- **RTF trên A10 [CHƯA BIẾT]**: Whisper base ~142 MiB ggml, ~388 MB RAM runtime [ĐO whisper.cpp docs cho base
  fp16]; NEON bật mặc định cho arm64. Nhưng Whisper xử theo **cửa 30 s không streaming** ⇒ độ trễ phụ thuộc độ
  dài phát âm, [SUY] base có thể đạt RTF < 1 trên 8 nhân cho câu ngắn — **phải đo T-STT trên xe**.

**Chốt**: PhoWhisper-**base** (74M, VIVOS 8.46%, BSD-3) là **ứng viên dự phòng mạnh** — giấy phép sạch, độ
chính xác đủ. Nhược: không streaming + tự convert ggml + RTF chưa đo.

---

## 3. sherpa-onnx (k2-fsa) — engine on-device có Android AAR

**[ĐO] Nguồn**: repo `k2-fsa/sherpa-onnx` + doc sherpa 1.3 + model card HF.

- **Bản chất**: STT/TTS/VAD/diarization bằng next-gen Kaldi + onnxruntime **không cần Internet**; hỗ trợ
  **Android, iOS, embedded, RISC-V**… có sẵn **AAR + JNI**, bind **12 ngôn ngữ lập trình** (có Kotlin/Java).
  [ĐO README] ⇒ đây là engine **hợp nhất** cả ASR lẫn TTS, giảm số phụ thuộc.
- **Mô hình tiếng Việt CÓ SẴN** [ĐO]:
  - **`sherpa-onnx-zipformer-vi-30M-int8-2026-02-09`** — nguồn `hynt/Zipformer-30M-RNNT-6000h`, **30M params**,
    huấn luyện **~6000 giờ** tiếng Việt chất lượng cao. WER [ĐO model card]: **VLSP2020-T1 12.29%**,
    VLSP2023-Public 10.40%, VLSP2023-Private 11.10%, **VLSP2025-Public 7.97%**, VLSP2025-Private 8.10%.
    RTF [ĐO card]: CPU **12 s audio → 0.3 s** (~0.025), RTX3090 < 0.1 s. Kiến trúc **RNN-Transducer ⇒ hỗ trợ
    streaming**. **Giấy phép: CC-BY-NC-ND-4.0** ⚠ (xem §3.1).
  - **`sherpa-onnx-zipformer-vi-2025-04-20`** — nguồn `zzasdf/viet_iter3_pseudo_label`, huấn luyện **~70k giờ**
    (pseudo-label). Tải: `github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-zipformer-vi-2025-04-20.tar.bz2`.
    Có bản **int8**. WER công bố + **giấy phép: [CHƯA BIẾT]** — model card sparse ra rỗng, phải mở
    trực tiếp `csukuangfj/sherpa-onnx-zipformer-vi-2025-04-20` để đọc.
- **SenseVoice có tiếng Việt không?** **KHÔNG** [ĐO doc sherpa]: SenseVoice chỉ zh/yue/en/ja/ko ⇒ **loại**.
- **Độ khó tích hợp Kotlin**: **THẤP** [SUY] — có AAR chính thức + ví dụ Android real-time ASR trong repo; API
  `OfflineRecognizer`/`OnlineRecognizer` nhận PCM 16k, trả text. So với Vosk (đang dùng) là **đổi engine cùng
  tầng** — cùng mô hình "nạp model → feed PCM → nhận chữ".

### 3.1 ⚠ Cảnh báo giấy phép (chốt TRƯỚC khi code — CLAUDE.md §1.1)

- `hynt/Zipformer-30M`: **CC-BY-NC-ND-4.0** = **Phi thương mại + KHÔNG được tạo bản phái sinh**. Với một app
  phát hành (kể cả free cho người dùng, nếu có yếu tố thương mại/OTA/đại lý) → **rủi ro pháp lý**. "NoDerivs"
  còn cấm cả việc fine-tune/chỉnh model. ⇒ **KHÔNG ship khi chưa có thoả thuận** với tác giả.
- `zipformer-vi-2025-04-20` (70k h): **[CHƯA BIẾT] giấy phép** — nếu Apache/MIT thì đây là **lựa chọn tốt hơn**
  cả về dữ liệu (70k > 6000 h) lẫn pháp lý. **Việc cần làm**: đọc model card + LICENSE của
  `csukuangfj/sherpa-onnx-zipformer-vi-2025-04-20` và `zzasdf/viet_iter3_pseudo_label`.
- Sherpa-onnx **engine** là Apache-2.0 (an toàn); vướng mắc chỉ ở **weights của model VN**.
- Nếu cả hai model VN đều vướng NC/ND → **rơi về PhoWhisper-base (BSD-3) qua whisper.cpp** hoặc train/xin bản
  weights có giấy phép mở.

---

## 4. whisper.cpp — ggml Whisper trên Android arm64

**[ĐO] Nguồn**: repo `ggml-org/whisper.cpp` + README + models/README.

- **Android arm64**: **NEON SIMD bật mặc định**; chạy được trên Android/iOS/Pi/WASM. [ĐO README]
- **Cỡ quantized** [ĐO models README]: tiny Q5_1 **31 MB**, base Q5_1 **57 MB** (base fp16 ~142 MiB ggml,
  ~388 MB RAM runtime).
- **Tốc độ**: tiny/base "chạy hữu ích trên Pi 4/Pi 5, small thì phải kiên nhẫn" [ĐO blog]. ⇒ A10 8 nhân [SUY]
  mạnh hơn Pi 4 ⇒ base khả thi cho câu ngắn; small biên giới.
- **Bản VN fine-tune ggml**: **medium đã có** (`dongxiat/ggml-PhoWhisper-medium`) [ĐO]. **tiny/base/small chưa
  có sẵn** ⇒ tự convert từ `vinai/PhoWhisper-base` (`convert-h5-to-ggml.py` + `quantize`) [SUY khả thi].
- **Streaming**: Whisper **không native streaming**; whisper.cpp có ví dụ "stream" nhưng bằng cách chạy lại
  cửa sổ ⇒ tốn CPU. Với **bấm-để-nói ≤ 9 s** hiện tại thì **one-shot decode** là đủ, không cần streaming.

**Chốt**: whisper.cpp + PhoWhisper-base = **đường dự phòng có giấy phép sạch nhất**. Chi phí: viết JNI wrapper
(hoặc dùng binding cộng đồng) + tự convert ggml + đo RTF.

---

## 5. Vosk model VN lớn hơn?

**[ĐO] Nguồn**: `alphacephei.com/vosk/models` + vosk-space/models.md.

- Có **hai** model VN 0.4: `vosk-model-small-vn-0.4` (**32 MB**, lightweight — bản đang dùng, đã fail thực địa)
  và `vosk-model-vn-0.4` (**78 MB**, "bigger Vietnamese model for server"). **Không có bản lớn hơn 78 MB**.
- [SUY] 78 MB vẫn thuộc lớp "small/medium Kaldi", cải thiện có nhưng khó bằng Zipformer 30M chuyên dụng
  (6000 h) hay PhoWhisper. Triệu chứng "cả câu ra 1 từ" của small là dấu hiệu **model quá yếu cho câu tự do**,
  nâng lên 78 MB [ĐOÁN] chỉ giảm nhẹ, không giải quyết gốc.
- **Kết luận**: **loại** khỏi lựa chọn chính. Chỉ giữ như "thử nhanh" nếu muốn xác nhận trần của họ Vosk trước
  khi bỏ hẳn (rẻ vì AAR đã tích hợp) — nhưng **không đầu tư**.

---

## 6. Android SpeechRecognizer / RecognizerIntent (Google) — "dễ nhất"?

**[ĐO] Nguồn**: Picovoice "Android Speech Recognition 2026" + docs Android.

- **On-device `createOnDeviceSpeechRecognizer()`**: **chỉ có từ API 33 (Android 13)**. DiLink3 = **API 29
  (Android 10)** ⇒ **KHÔNG khả dụng**. [ĐO] Đây là điểm chặn cứng.
- **`SpeechRecognizer` online**: cần **mạng + GMS + app Google (RecognitionService)**. Head unit BYD **[CHƯA
  BIẾT] có GMS không** nhưng thường ROM Trung Quốc **non-GMS**; và cắm CarPlay/AA thì đầu xe **tắt WiFi**
  (CLAUDE.md §11) ⇒ đúng lúc cần thì không có mạng.
- **Kết luận**: ứng viên "dễ dùng nhất về mặt code" nhưng **bất khả thi trên phần cứng/OS này**. Loại. (Giữ
  làm ghi chú: nếu tương lai có đời xe API ≥ 33 + GMS thì mở lại được đúng mục tiêu "không key, không setup".)

---

## 7. vieneu.io

**[ĐO] Nguồn**: `github.com/tanbt/VieNeu-TTS`, `docs.vieneu.io`, `pnnbao-ump/VieNeu-TTS` (HF), PyPI `vieneu`.

- **Bản chất**: chủ yếu **TTS tiếng Việt + voice cloning** (on-device, real-time CPU, 24 kHz). Có nhắc **STT**
  nhưng **tài liệu STT rất mỏng** — không tìm thấy model STT tải-về/benchmark rõ ràng.
- **SDK**: chạy **hoàn toàn trên máy sau khi tải model, không cần Internet** [ĐO] — đúng tinh thần on-device.
- **Giá / giấy phép**: `VieNeu-TTS-0.3B` **miễn phí cho sinh viên/nghiên cứu/phi lợi nhuận**; **thương mại phải
  liên hệ tác giả (~$5000/năm, thương lượng)**. Bản v4 **proprietary, chỉ qua API**. [ĐO]
- **Kết luận cho STT**: **không phải lựa chọn STT** (thiếu model/benchmark on-device rõ). **Nhưng đáng cân
  nhắc cho TTS** nếu muốn giọng đẹp — vướng **phí thương mại**, nên xếp sau sherpa-onnx VITS (miễn phí).

---

## 8. Đường "mượn app trợ lý" + LLM-audio-in

**[ĐO] từ RE nội bộ** (`kiki-car-RE-2026-09-14.md`, backlog V1):

- **Đọc text mà Kiki/Google nhận được**: Kiki **không** phơi `RecognitionService`/`VoiceInteractionService`;
  binder autowake **chỉ MỞ + nghe, KHÔNG trả kết quả về** [ĐO] ⇒ **không lấy được transcript của Kiki**.
- **AccessibilityService đọc màn hình trợ lý**: [ĐOÁN] về lý thuyết có thể đọc text hiển thị trên UI trợ lý,
  nhưng **cực kỳ giòn** (phụ thuộc layout app khác, đổi bản là gãy — đúng bài học §7/§15 "mò UI"), và **không
  có transcript ổn định** để nuôi parser. **Không nên làm trục chính.**
- **LLM audio-in (Gemini/GPT nghe thẳng) làm STT+NLU**: **cần API key** ⇒ **trái yêu cầu owner** ("API key quá
  khó cho user"). Chỉ khả thi nếu có **proxy do mình vận hành** (user không nhập key) — nhưng khi đó **cần
  mạng + chi phí server**, không còn là on-device. Xếp là **hướng cloud tuỳ chọn tương lai**, không phải bây giờ.

**Kết luận**: giữ **phương án C** đã chốt ở V1 — Kachi tự làm tập đóng offline; các đường "mượn app" chỉ ở mức
"mở app", không dùng làm front-end ASR.

---

## 9. TTS tiếng Việt on-device (phản hồi giọng)

- **Google TTS VN offline** [ĐO]: engine Google TTS hỗ trợ **gói giọng tiếng Việt tải về**, sau khi cài thì
  **tổng hợp on-device**. Nhược: **phụ thuộc engine Google TTS có mặt trên máy** — head unit non-GMS **[CHƯA
  BIẾT]** có sẵn không; nếu không có thì user phải tự cài (trái "dễ dùng").
- **sherpa-onnx VITS tiếng Việt** [SUY, cùng engine ASR]: sherpa-onnx có TTS VITS, cộng đồng có model VN ⇒
  **tự chứa, không phụ thuộc Google/GMS**, dùng **cùng một engine/AAR** với ASR ⇒ **ưu tiên** cho non-GMS.
- **VieNeu-TTS** [ĐO §7]: giọng đẹp, on-device CPU real-time, nhưng **phí thương mại** ⇒ dự phòng.
- **Ghi chú V1 hiện tại**: pha nghe **không TTS**, chỉ âm báo + tấm chữ. TTS là hạng mục mở (backlog V1 "TTS
  🔲 chưa biết"). Nếu thêm, **sherpa-onnx VITS** hợp nhất tốt nhất với lựa chọn ASR đề xuất.

---

## 10. Đề xuất XẾP HẠNG cho ca DiLink3

Ưu tiên theo yêu cầu owner: **chính xác tiếng Việt thật + KHÔNG cần user nhập key + chạy được trên DiLink3**.

1. **LỰA CHỌN CHÍNH — sherpa-onnx + Zipformer-VN (transducer)**
   - Vì: on-device thật, Android AAR sẵn, **WER 8–12% VLSP** (vượt Vosk small nhiều), RTF công bố rất thấp,
     streaming, **không key, không setup cho user** (app tự tải model 1 lần). Đây là bản nâng cấp **trực tiếp
     cùng tầng** với Vosk đang dùng.
   - **Điều kiện chặn (CLAUDE.md §1.1 — chốt TRƯỚC khi code)**: **xác minh giấy phép**. Ưu tiên bản
     `zipformer-vi-2025-04-20` (70k h) NẾU giấy phép mở (Apache/MIT); nếu bản đó cũng NC/ND như bản 30M →
     **không ship**, chuyển sang lựa chọn dự phòng. **KHÔNG dùng `hynt/Zipformer-30M` (CC-BY-NC-ND) cho bản
     phát hành** khi chưa có thoả thuận.

2. **DỰ PHÒNG — whisper.cpp + PhoWhisper-base (ggml q5/q8)**
   - Vì: **BSD-3-Clause (thương mại OK)**, VIVOS **8.46% WER**, cỡ ~57 MB, arm64 NEON. Không vướng pháp lý.
   - Chi phí: viết/wrap JNI whisper.cpp + **tự convert base→ggml** + **đo RTF** (không streaming, nhưng phiên
     bấm-để-nói ≤ 9 s chấp nhận one-shot). Nếu base chậm → thử tiny (VIVOS 10.41%) hoặc small (6.33%, nặng hơn).

3. Xa hơn: wav2vec2-VN-250h (VIVOS 6.15% — WER thấp nhất nhóm) NẾU chấp nhận tự wrap ONNX Runtime + KenLM
   ngoài + xác minh giấy phép; Vosk big 78 MB chỉ để "thử nhanh"; các đường cloud/LLM/assistant = loại cho
   yêu cầu end-user không-key.

**TTS**: sherpa-onnx VITS VN (chính) · Google TTS VN offline (dự phòng nếu có engine) · VieNeu (nếu chịu phí).

---

## 11. [CHƯA BIẾT] — phải đo trên xe (không được nâng cấp mức bằng chứng nếu chưa đo)

Theo CLAUDE.md §2/§14/§15 — **shell thô trên xe TRƯỚC, nối dây theo tầng SAU**:

1. **RTF thật trên DiLink3**: nạp model đã chọn, decode WAV 5–9 s, đo ms/câu + %CPU + RAM. Số công bố (RTF
   0.025) đo trên CPU server — **[CHƯA BIẾT] hệ số trên 8 nhân A10**.
2. **Mic mảng 4 kênh**: **lấy kênh nào?** (kênh 0? beamformed? mix?). Vosk hiện mở nguồn `VOICE_RECOGNITION`→
   `MIC` — đo xem sherpa/whisper feed cùng nguồn có tốt không, hay cần chọn 1 trong 4 kênh / cần AEC.
3. **Độ chính xác GIỌNG THẬT + ồn đường** (không phải WER tập sạch): 25 câu ở 3 mức ồn (đứng yên / 40 / 80
   km/h) — dùng playbook §2.16 T18/T25.
4. **Giấy phép** `zipformer-vi-2025-04-20` + `zzasdf/viet_iter3_pseudo_label` — đọc LICENSE thật (blocker
   pháp lý, làm ngay, **không cần xe**).
5. **Engine Google TTS** có sẵn trên ROM BYD không (`TextToSpeech` enumerate engines) — quyết định TTS.
6. **Cỡ APK sau khi nhúng**: model ASR + (nếu có) TTS → tổng cỡ tải; hiện Vosk đẩy APK 9→26.8 MB, model mới
   có thể lớn hơn ⇒ xét đường tải-về-sau như Vosk (`VoiceModelStore` + HTTPS).

---

## 12. Kế hoạch tích hợp (khi giấy phép đã chốt)

**Nguyên tắc**: **thay tầng NGHE, giữ nguyên pha hiểu ý.** `VoiceIntentParser`/`VoiceDispatcher`/`VoiceGrammar`
ở `:core` **không đổi** — chúng nhận **chuỗi chữ** đầu vào. Chỉ đổi thứ **sinh ra chuỗi chữ đó**.

- **Tầng 1 (shell trên xe — CLAUDE.md §14)**: đẩy binary sherpa-onnx (hoặc whisper.cpp) + model lên xe qua
  adb loopback, decode WAV thật, **lưu output làm evidence** trong `docs/diagnostics/`. Chưa xanh bước này thì
  chưa viết code `car-integration`/`core`.
- **Tầng 2 (`car-integration`)**: thay `VoskListener` bằng adapter engine mới; giữ hợp đồng "PCM 16k mono →
  chuỗi chữ (+ optional partial)". Test khoá chuỗi lệnh/parse thật (pattern E2E command-log).
- **Tầng 3 (`core`)**: **không sửa** parser. Điều chỉnh duy nhất có thể cần: engine mới cho **transcript tự do**
  (không còn ràng buộc FST của Vosk) ⇒ **biasing/hotword** thay cho "ngữ pháp đóng" — sherpa-onnx hỗ trợ
  **contextual biasing** để tăng trúng tập lệnh đóng (65 nút + 123 datum) trong khi vẫn nghe được câu tự do.
  Đây thực ra **giải đúng triệu chứng "cả câu ra 1 từ"** của Vosk (model quá nhỏ + ngữ pháp cứng).
- **Tầng 4 (`app`/UI)**: giữ nguyên 3 lối vào (ô *Nói với xe* · nút mic thanh trên · phím `__KACHI_VOICE__`),
  phiên bấm-để-nói, tấm chữ, cổng CONFIRM mặc-định-KHÔNG. Đổi model store (cỡ/sha/HTTPS) theo engine mới.
- **Bump version** khi đã báo APK (CLAUDE.md §9). **Senior review (Opus) + security scan trước commit** (§5/§6).

**Lưu ý DRY/an toàn**: model tải-về đi qua `VoiceModelStore` sẵn có (sha256 + chống zip-slip + đường gỡ);
KHÔNG thêm `os.getenv`/config rải rác; giữ 1 owner cho spec.

---

## 13. Nguồn (URL)

**PhoWhisper**
- https://github.com/VinAIResearch/PhoWhisper/blob/main/README.md (bảng WER, dữ liệu 844 h)
- https://huggingface.co/vinai/PhoWhisper-small (giấy phép **BSD-3-Clause**)
- https://huggingface.co/vinai/PhoWhisper-base , https://huggingface.co/vinai/PhoWhisper-medium , https://huggingface.co/vinai/PhoWhisper-large
- https://openreview.net/pdf?id=x3c3MkJfpG (ICLR 2024 Tiny Paper) · https://arxiv.org/pdf/2406.02555
- https://huggingface.co/dongxiat/ggml-PhoWhisper-medium (ggml sẵn — medium) · https://huggingface.co/huuquyet/PhoWhisper-large (ONNX)

**sherpa-onnx + Zipformer VN**
- https://github.com/k2-fsa/sherpa-onnx (engine, Android AAR, offline)
- https://huggingface.co/hynt/Zipformer-30M-RNNT-6000h (WER VLSP, RTF, **CC-BY-NC-ND-4.0**)
- https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-vi-2025-04-20 (bản 70k h — **giấy phép cần đọc**)
- https://huggingface.co/zzasdf/viet_iter3_pseudo_label (nguồn 70k h) · https://k2-fsa.github.io/sherpa/onnx/
- https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html (SenseVoice — **không có VN**)

**wav2vec2 VN**
- https://huggingface.co/nguyenvulebinh/wav2vec2-base-vietnamese-250h (VIVOS **6.15%**, CTC + 4-gram)
- https://github.com/nguyenvulebinh/vietnamese-wav2vec2 · https://github.com/vietai/ASR

**whisper.cpp**
- https://github.com/ggml-org/whisper.cpp/blob/master/README.md · .../models/README.md (cỡ q5: tiny 31 MB, base 57 MB; NEON arm64)

**Vosk**
- https://alphacephei.com/vosk/models (small-vn-0.4 32 MB · **vn-0.4 78 MB**) · https://github.com/alphacep/vosk-api

**Android SpeechRecognizer**
- https://picovoice.ai/blog/android-speech-recognition/ (on-device chỉ **API 33+**; online cần GMS + mạng)

**vieneu**
- https://www.vieneu.io/ · https://docs.vieneu.io/ · https://github.com/tanbt/VieNeu-TTS (on-device TTS; thương mại ~$5000/năm)

**TTS**
- https://micmonster.com/does-google-tts-work-offline/ · https://saomaicenter.org/en/node/64 (gói giọng VN offline Google TTS)

**Nội bộ**
- `docs/diagnostics/kiki-car-RE-2026-09-14.md` (Kiki ASR ở mây; không phơi RecognitionService)
- `docs/diagnostics/oncar-playbook-kachi-1.53.md` §2.16 (T18/T25 đo giọng thật) · backlog **V1**

---

## §Nhật ký

- **2026-09-14** — Tạo tài liệu. Nghiên cứu 9 hướng theo yêu cầu owner. Kết luận: **sherpa-onnx + Zipformer-VN**
  (chính, chốt giấy phép trước) / **whisper.cpp + PhoWhisper-base BSD-3** (dự phòng). Loại Android
  SpeechRecognizer (API 33 vs A10=API29), SenseVoice (không VN), Vosk (quá nhỏ). Mọi WER/RTF là [ĐO công bố],
  **chưa đo trên DiLink3** — §11 liệt kê việc phải đo trên xe. Cập nhật `docs/README.md` §6 + backlog dòng V-STT.
