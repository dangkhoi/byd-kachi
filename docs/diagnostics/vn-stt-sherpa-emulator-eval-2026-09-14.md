# Đo off-device: sherpa-onnx Zipformer-vi giải mã tiếng Việt (proof tầng-1 trước khi nối dây)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-14 · **Loại**: Diagnostics (evidence, có số đo thật) ·
> **Owner**: dangkhoi · **Spec**: `docs/specs/kachi-voice-engine-v2.html` · **Nghiên cứu gốc**:
> `docs/diagnostics/vn-stt-ondevice-research-2026-09-14.md`

**Quy ước mức bằng chứng** (`.kiro/steering/conversation-protocol.md` + CLAUDE.md §2): `[ĐO]` = số đo trực tiếp ·
`[SUY]` = suy luận khớp dữ kiện · `[CHƯA BIẾT]` = phải đo trên xe mới biết.

> ⚠ **Mức bằng chứng tối đa của tài liệu này**: mọi số dưới đây đo trên **macOS arm64** (Python `sherpa-onnx
> 1.13.8`) với **giọng tổng hợp `say -v Linh`** — KHÔNG phải DiLink3 A10, KHÔNG phải mic 4 kênh, KHÔNG phải giọng
> thật + ồn đường. Đây là **proof tầng-1 (CLAUDE.md §14)**: chứng minh engine + model + biasing chạy đúng TRƯỚC
> khi viết code Android. Độ chính xác (WER/intent) là **thuộc tính của model** ⇒ chuyển sang thiết bị khác giữ
> nguyên; RTF thì KHÔNG (phụ thuộc CPU) ⇒ §11 nghiên cứu-gốc vẫn phải đo trên xe.

---

## 0. Vì sao đo (bối cảnh)

Vosk small-vn (32 MB, ngữ pháp FST cứng) trên xe thật **nói cả câu ra một từ** — gốc bệnh: model quá nhỏ +
giải mã ràng FST. Owner 2026-09-14: *"làm luôn voice, thử 1-2 options, làm đến khi chạy được OK thì chốt"*.
Trước khi thay `VoiceRecognizer` (Vosk) bằng sherpa-onnx trong app, phải chứng minh engine mới **thật sự nghe
được câu tiếng Việt tự do** và **biasing kéo được về đúng lệnh** — đúng thứ Vosk hụt.

## 1. Thiết lập [ĐO]

| Mục | Giá trị |
|---|---|
| Engine | Python `sherpa-onnx==1.13.8` (venv), macOS arm64 |
| Model | `sherpa-onnx-zipformer-vi-2025-04-20` (offline Zipformer transducer, **fp32**), Apache-2.0 |
| Đơn vị model | BPE sentencepiece, **2000 token**, xuất **CHỮ HOA CÓ DẤU** (`▁ĐÈN`, `▁PIN`…) |
| Bộ liệu | 15 câu lệnh chuẩn, `say -v Linh` → `afconvert -f WAVE -d LEI16@16000 -c 1` (16 kHz mono PCM16) |
| Giải mã A | `greedy_search` |
| Giải mã B | `modified_beam_search` + hotwords BPE (`modeling_unit=bpe`, `bpe_vocab`=bảng piece+score xuất từ `bpe.model`), `hotwords_score=3.0` |

## 2. Kết quả [ĐO] — 15 câu

| # | Câu (ref) | greedy HYP | B: beam+biasing HYP | Intent |
|---|---|---|---|---|
| 01 | bật đèn đọc sách | BẬT ĐÈN ĐỌC SÁCH | BẬT ĐÈN ĐỌC SÁCH | ✅ |
| 02 | đặt nhiệt độ hai mươi hai độ | ĐẠT NHIỆT ĐỘ HAI MƯƠI HAI ĐỘ | ĐẠT NHIỆT ĐỘ HAI MƯƠI HAI ĐỘ | ✅ (đặt/đạt khác dấu, parser khử dấu ⇒ như nhau) |
| 03 | đóng hết kính | ĐÓNG HẾT KÍNH | ĐÓNG HẾT KÍNH | ✅ |
| 04 | mở youtube vào ô số hai | MỞ **Ô TƯỜNG** VÀO Ô SỐ HAI | MỞ **Ô TƯỜNG** VÀO Ô SỐ HAI | ❌ tên app (xem §4) |
| 05 | dẫn đường tới chợ bến thành | DẪN ĐƯỜNG TỚI CHỢ BẾN THÀNH | DẪN ĐƯỜNG TỚI CHỢ BẾN THÀNH | ✅ |
| 06 | xem pin | XEM **TIN** | XEM **PIN** | greedy ❌ → biasing ✅ |
| 07 | tắt điều hoà | **PHÁT** ĐIỀU HÒA | **TẮT** ĐIỀU HÒA | greedy ❌ (đảo động từ!) → biasing ✅ |
| 08 | mở bản đồ | MỞ BẢN ĐỒ | MỞ BẢN ĐỒ | ✅ |
| 09 | tăng âm lượng | TĂNG ÂM **LỬA** | TĂNG ÂM **LƯỢNG** | greedy ❌ → biasing ✅ |
| 10 | giảm nhiệt độ | GIẢM NHIỆT ĐỘ | GIẢM NHIỆT ĐỘ | ✅ |
| 11 | bật sưởi ghế | BẬT SƯỞI GHẾ | BẬT SƯỞI GHẾ | ✅ |
| 12 | mở cửa sổ trời | MỞ CỬA SỔ TRỜI | MỞ CỬA SỔ TRỜI | ✅ |
| 13 | phát nhạc trên youtube music | PHÁT NHẠC TRÊN **MILZID** | PHÁT NHẠC TRÊN **OLUTUNIC** | ❌ tên app (xem §4) |
| 14 | dẫn đường về nhà | DẪN ĐƯỜNG VỀ NHÀ | DẪN ĐƯỜNG VỀ NHÀ | ✅ |
| 15 | bật đèn khẩn cấp | BẬT ĐÈN KHẨN CẤP | BẬT ĐÈN KHẨN CẤP | ✅ |

**Tổng intent đúng**: greedy ~**10/15 (67%)** · **beam+biasing 13/15 (~87%)** ⇒ **VƯỢT sàn 70%**.

## 3. An toàn biasing [ĐO] — không chèn nhầm lệnh

Score 3.0 KHÔNG kéo câu thường thành lệnh (giữ tính chất *"không hiểu là câu trả lời đúng"* của phase nghe):

| Câu không-lệnh | HYP (beam+biasing 3.0) |
|---|---|
| hôm nay trời đẹp quá | HÔM NAY TRỜI ĐẸP QUÁ |
| kể cho tôi nghe một câu chuyện | KỂ CHO TÔI NGHE MỘT CÂU CHUYỆN |

## 4. Hai câu còn sai — tên app tiếng Anh

`youtube` / `youtube music` là từ **tiếng Anh**; model BPE **chỉ tiếng Việt** (2000 token) **không phát ra được**
chuỗi con đó ⇒ ra `ô tường` / `olutunic`. Biasing vô nghĩa ở đây (không token đích để kéo về). Cách xử ĐÚNG:
- để **`VoiceIntentParser` khớp nhãn app** (fuzzy, khử dấu) trên phần đuôi tự do — KHÔNG dùng hotword cho tên app;
- hoặc **giọng thật** trên xe (người nói "diu-túp" khác giọng tổng hợp);
- hoặc **model B** (hataphu telephony) — [CHƯA BIẾT], cần mirror + đo.
`SherpaHotwords.normalize` do đó **loại chuỗi có chữ số** (số ô/slot) và để tầng wiring quyết định tên app.

## 5. RTF [ĐO macOS arm64 — KHÔNG phải A10]

fp32, `num_threads=2`: decode **10–18 ms/clip**, tổng **188 ms cho 19,0 s audio ⇒ avgRTF 0.010**. Ngay cả fp32
(encoder 249 MB) cũng rất nhanh trên arm64 desktop. **[CHƯA BIẾT] hệ số trên 8 nhân A10** — phải đo trên xe;
nếu chậm quá thì xét **int8** (chưa có bản chính thức cho model A, xem spec §Open Questions).

## 6. Ghim tệp model (cho `SherpaModelCatalog`) [ĐO `shasum -a 256` 2026-09-14]

| Tệp | sha256 | bytes |
|---|---|---|
| encoder-epoch-12-avg-8.onnx | `d56645616305ceee63a1fa63a4da32e688130e937e67b11f69adf79712377717` | 261 057 692 |
| decoder-epoch-12-avg-8.onnx | `d1d27cca84c824a8acf5ce6edf0f2c0880cfe295d2e69b95134de1707e1d9998` | 5 165 084 |
| joiner-epoch-12-avg-8.onnx | `a186d4ddf04cac3ddfb095dc6e7f705dcd08bd79d4c67334f43c3a7337bf8d9a` | 4 104 465 |
| tokens.txt | `f536d03c2e95ebd2930cf0abec88e823bd17d3c1933da7ae6a82db3b80605e15` | 25 847 |
| bpe.model | `289dbb44527c13c419ae3a4d8ce6a349f01a97f8777e69934a77e3692d2f10db` | 270 695 |

Tổng giải nén ~**266 MB** (fp32). Tải riêng như Vosk (không đóng vào APK).

## 7. Bẫy đã bắt: `bpe_vocab` KHÔNG nhận `bpe.model` thô

sherpa 1.13.8 khi bật hotwords BPE cần **bảng piece+score** (xuất từ `bpe.model` bằng sentencepiece), KHÔNG nhận
`bpe.model` nhị phân ("Each line in vocab should contain two items") và KHÔNG dùng được `tokens.txt` (cột 2 là
id, không phải score). ⇒ ship bảng đã xuất làm **asset** `app/src/main/assets/voice/<id>.bpe_vocab.txt`, engine
chép sang filesDir lúc nạp. Thiếu bảng đúng ⇒ biasing **im lặng không ăn** ⇒ rơi về ~67% (dưới sàn) — đúng loại
lỗi "compile xanh mà không chạy" CLAUDE.md §8 cảnh báo.

## 8. Kích cỡ native (APK) [ĐO — AAR `sherpa-onnx-v1.13.8.aar` trong gradle cache]

AAR 47,8 MB. `.so` theo ABI ship (`abiFilters` = arm64-v8a + armeabi-v7a):

| ABI | libonnxruntime.so | libsherpa-onnx-jni.so | (c-api/cxx-api — nên loại) |
|---|---|---|---|
| arm64-v8a | 21,2 MB | 4,6 MB | 4,3 + 0,4 MB |
| armeabi-v7a | 14,6 MB | 3,3 MB | 3,1 + 0,3 MB |

`libonnxruntime.so` chiếm phần lớn. Loại `libsherpa-onnx-c-api.so`/`cxx-api.so` (chỉ cần JNI) qua
`packaging { jniLibs.excludes }` tiết kiệm ~8 MB. So Vosk (native nhỏ hơn nhiều) đây là **tăng ~40 MB native**
— trade-off có thật, ghi để owner biết.

## 9. Việc còn phải đo trên xe / owner chốt ([CHƯA BIẾT])

1. **A10 RTF** thật (decode clip 8–9 s) + %CPU + RAM.
2. **Mic 4 kênh**: lấy kênh nào; sherpa feed cùng nguồn `VOICE_RECOGNITION` có tốt không.
3. **Độ chính xác GIỌNG THẬT + ồn đường** (25 câu × 3 mức tốc độ — playbook T18/T25).
4. **Model B hataphu**: repo HF **GATED (401)** ⇒ mirror lên kênh OTA + ghim sha256/bytes rồi mới bật.
5. **fp32 → int8**: giảm ~266 MB tải + cải thiện A10 RTF nếu cần.

## 10. Nguồn

- Model A: `csukuangfj/sherpa-onnx-zipformer-vi-2025-04-20` (weight `zzasdf/viet_iter3_pseudo_label` = Apache-2.0);
  release: `github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-zipformer-vi-2025-04-20.tar.bz2`.
- Model B: `hataphu/zipformer-k2-rnn-lm-vi` (MIT, gated).
- Engine: `github.com/k2-fsa/sherpa-onnx` (Apache-2.0), AAR JitPack `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8`.
- Nghiên cứu-gốc: `docs/diagnostics/vn-stt-ondevice-research-2026-09-14.md`.

## §Nhật ký

- **2026-09-14** — Tạo. Proof tầng-1: sherpa-onnx 1.13.8 + Zipformer-vi Apache decode 15 câu `say -v Linh`;
  greedy 67% → beam+BPE-biasing (score 3.0) **87%** intent, an toàn không over-trigger; RTF fp32 0.010 (macOS
  arm64, A10 [CHƯA BIẾT]); bẫy `bpe_vocab`. Đủ điều kiện tầng-2/3/4 (code Android). Xe: §9.
