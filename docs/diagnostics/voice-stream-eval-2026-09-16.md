# [ĐO host 2026-09-16] STREAMING đa ngữ vs OFFLINE đang ship — và một phát hiện khác về ĐUÔI IM LẶNG

> Chủ đề owner đặt ra: *"Nếu có streaming thì quá ổn, nghe đến đâu xử đến đó."*
> Bối cảnh: [ĐO xe 2026-09-16] đường offline hiện tại chờ hết trần rồi mới giải mã ⇒ **5–6 s** trễ sau khi
> người nói xong. Tệp này đo xem bộ **streaming** có cắt được khoảng chờ đó mà không mất độ chính xác không.
>
> Toàn bộ số dưới đây sinh bằng máy: `scripts/voice/stream-matrix.py` (mới) + `scripts/voice/mishear-table.py`
> + `scripts/voice/hotword-matrix.py` (đã có). Chạy lại là ra y hệt.
> Owner: `dangkhoi`.

---

## 0. Mức bằng chứng — đọc trước khi trích số

| Nhãn | Nghĩa trong tệp này |
|---|---|
| **[ĐO host]** | Chạy thật trên MacBook (Apple Silicon, 8 lõi), sherpa-onnx **1.13.8** python, corpus 1 874 WAV TTS + 25 WAV `say -v Linh`. Đúng tham số `VoiceRecognizer.kt`: `modified_beam_search` · beam 4 · hotword score 3.0 · bpe. |
| **[ĐO xe]** | Trích từ log thật `car-0916b/voice-1.68-real.txt` (40 lượt nghe trên xe, 2 842 dòng). |
| **[SUY]** | Ngoại suy từ host sang Qualcomm TRINKET. **Không** phải phép đo trên xe. |
| **[CHƯA BIẾT]** | Chưa có dữ liệu. Nêu rõ cách chốt. |

⚠ Hai cảnh báo cứng, đúng CLAUDE.md §2:
1. **Đây là CPU host, KHÔNG phải xe.** RTF/RSS host chỉ dùng để **loại trừ** (host đã hỏng thì xe chắc chắn
   hỏng), không dùng để hứa hẹn.
2. **Giọng TTS ≠ giọng thật + mic 4 kênh + ồn đường.** Số nói về *mô hình*, không nói về *xe*.

---

## 1. Bốn ứng viên, và chúng khác nhau ở đâu

| Mã | Mô hình | Kiểu | Đường ngắt câu | Giấy phép |
|---|---|---|---|---|
| **A** | `sherpa-onnx-zipformer-vi-2025-04-20` (đang ship) | OFFLINE | endpointer RMS + **trần 8.4 s** | Apache-2.0 |
| **B** | `csukuangfj/sherpa-onnx-streaming-zipformer-ar_en_id_ja_ru_th_vi_zh-2025-02-10` (PengChengStarling) | **STREAMING** | endpoint **có sẵn** trong sherpa | ⚠ **không ghi** (xem §9) |
| **C** | A (hoặc D) + **Silero VAD** (`silero_vad.onnx`, 0.64 MB) | OFFLINE | VAD đóng đoạn ⇒ cắt cửa sổ, giải mã ngay | Apache-2.0 (VAD: MIT) |
| **D** | `csukuangfj2/sherpa-onnx-zipformer-vi-30M-int8-2026-02-09` | OFFLINE | như A | ⚠ **CC-BY-NC-ND-4.0** (xem §9) |

**Tệp đã tải và đã đối chiếu kích thước với HF** [ĐO host]:

| Gói | Tệp | Byte | Tổng (onnx + tokens) |
|---|---|---|---|
| A offline-vi | `encoder.onnx` 261 057 692 · `decoder.onnx` 5 165 084 · `joiner.onnx` 4 104 465 · `tokens.txt` 25 847 | | **270.4 MB** |
| B stream-multi | `encoder-…-chunk-16-left-128.int8.onnx` 296 583 597 · `decoder-….onnx` 33 837 085 · `joiner-….int8.onnx` 8 257 421 · `tokens.txt` 195 244 (+ `bpe.model` 476 049) | khớp HF từng byte | **338.9 MB** |
| D vi-30M-int8 | `encoder.int8.onnx` 27 699 063 · `decoder.onnx` 5 165 084 · `joiner.int8.onnx` 1 033 417 · `tokens.txt` 23 238 | khớp HF | **33.9 MB** |
| D′ vi-30M-fp32 | `encoder.onnx` 92 184 132 · `decoder.onnx` 5 165 084 · `joiner.onnx` 4 104 465 | khớp HF | **101.5 MB** |
| VAD | `silero_vad.onnx` 643 854 | | **0.64 MB** |

---

## 2. Độ chính xác — bảng chính

Cùng corpus, cùng `vi_text.norm()` (NFC · số thành chữ · dấu thanh kiểu mới · bỏ dấu câu) với
`voice-mishear-2026-09-16.md`, nên số **ghép thẳng** được vào tài liệu đó.
Mọi cấu hình đều dùng tệp hotword THẬT của bản build (`core/build/hotwords/hotwords-phrases.txt`, **2 241 dòng**)
với score 3.0.

### 2.1 Tổng

| Cấu hình | corpus 1 874 WAV | 25 WAV cũ | Δ so với A |
|---|---|---|---|
| **A** offline-vi (đang ship) | **943 (50.3 %)** | **22/25** | — |
| **D** vi-30M-int8 | **977 (52.1 %)** | **22/25** | **+1.8 điểm** |
| **C** offline-vi + Silero VAD | 897 (47.9 %) | 21/25 | −2.4 điểm |
| **C′** vi-30M-int8 + Silero VAD | 911 (48.6 %) | 21/25 | −1.7 điểm |
| **B** stream-multi (hotword chữ thường) | **732 (39.1 %)** | **14/25** | **−11.2 điểm** |
| B stream-multi (không hotword) | 505 (26.9 %) | 13/25 | −23.4 điểm |

> Mốc đối chứng: `voice-mishear-2026-09-16.md` ghi 935/1 899 (49.2 %) với tệp hotword 1 757 dòng. Tệp hotword
> nay là 2 241 dòng ⇒ A lên 943/1 874 (50.3 %). Cùng một đường đo, chỉ khác tệp hotword.

**Kết luận §2.1**: B thấp hơn A **11.2 điểm** trên corpus và **8/25** câu trên bộ WAV cũ — vượt xa ngưỡng
10 điểm mà nhiệm vụ đặt ra để loại. D **cao hơn** A 1.8 điểm ở **1/8 kích thước**.

### 2.2 Theo LOẠI ý định (cột lề trái giống §3 của `voice-mishear`)

| intent_kind | A offline-vi | D vi-30M-int8 | C vi+VAD | C′ 30M+VAD | B stream-multi | số WAV |
|---|---|---|---|---|---|---|
| app | 16.6 % | 18.9 % | 15.8 % | 18.8 % | 17.3 % | 549 |
| control_button | 72.7 % | 75.0 % | 79.5 % | 72.7 % | **20.5 %** | 44 |
| control_cover | 60.6 % | **93.9 %** | 60.6 % | 87.9 % | 72.7 % | 33 |
| control_select | 71.4 % | 73.8 % | 69.0 % | 76.2 % | 52.4 % | 42 |
| control_step | 76.9 % | 79.5 % | 76.9 % | 76.9 % | **33.3 %** | 39 |
| control_toggle | 46.8 % | 43.6 % | 45.9 % | 37.6 % | 40.8 % | 218 |
| launcher | 100 % | 100 % | 86.7 % | 86.7 % | 66.7 % | 15 |
| layout | 50.0 % | 83.3 % | 50.0 % | 66.7 % | 66.7 % | 6 |
| macro | 81.0 % | 81.0 % | 61.9 % | 57.1 % | 52.4 % | 21 |
| media | 52.4 % | 52.4 % | 42.9 % | 47.6 % | **19.0 %** | 21 |
| nav | 55.6 % | 55.6 % | 55.6 % | 55.6 % | 22.2 % | 9 |
| old_case (câu tester) | 74.1 % | **78.2 %** | 65.5 % | 67.3 % | **50.9 %** | 220 |
| profile | 66.7 % | 66.7 % | 66.7 % | 66.7 % | 0 % | 3 |
| telemetry_read | 64.3 % | 64.9 % | 62.7 % | 62.7 % | 52.4 % | 633 |
| unknown | 71.4 % | 66.7 % | 47.6 % | 57.1 % | 23.8 % | 21 |

**Nhánh TÊN APP** (549 WAV, chỗ thủng lớn nhất theo `voice-mishear` §3): B **17.3 %** ≈ A **16.6 %** —
streaming *không* chữa được chỗ này. D nhỉnh hơn (18.9 %). Không ứng viên nào giải quyết được tên riêng
tiếng Anh; đó là việc của `VoiceSynonyms`, không phải của mô hình.

### 2.3 Theo giọng × tốc độ

| giọng · tốc độ | A | D | C | C′ | B | số WAV |
|---|---|---|---|---|---|---|
| linh 140 | 43.4 % | 48.2 % | 43.4 % | 45.8 % | 34.9 % | 83 |
| linh 180 | 64.3 % | 64.7 % | 63.8 % | 65.4 % | 54.8 % | 431 |
| linh 220 | 47.0 % | 47.0 % | 41.0 % | 43.4 % | 34.9 % | 83 |
| linh 260 | 64.7 % | 66.1 % | 62.9 % | 64.0 % | 52.7 % | 431 |
| linh 300 | 44.6 % | 43.4 % | 42.2 % | 39.8 % | 31.3 % | 83 |
| linh-ola 1.3 | 38.6 % | 44.6 % | 36.1 % | 39.8 % | 28.9 % | 83 |
| linh-ola 1.6 | 38.6 % | 42.2 % | 37.3 % | 41.0 % | 34.9 % | 83 |
| piper 0.9 | 25.3 % | 28.9 % | 20.5 % | 20.5 % | 15.7 % | 83 |
| piper 1.0 | 39.7 % | 42.0 % | 36.0 % | 34.6 % | 24.4 % | 431 |
| piper 1.3 | 22.9 % | 25.3 % | 15.7 % | 15.7 % | 16.9 % | 83 |

Hình dạng đường cong **giữ nguyên** ở cả 5 cấu hình (180/260 cao nhất, 140 và 300 thấp) ⇒ kết luận
*"phải nói chậm" bị bác* của `voice-mishear` **không** phụ thuộc vào mô hình.

### 2.4 Theo vùng miền và kiểu nói

| region | A | D | C | C′ | B | số WAV |
|---|---|---|---|---|---|---|
| bac | 46.4 % | 51.8 % | 42.9 % | 51.8 % | **10.7 %** | 56 |
| chung | 54.1 % | 55.0 % | 51.6 % | 51.4 % | 41.9 % | 1 575 |
| nam | 26.7 % | **33.7 %** | 25.1 % | 30.0 % | 27.2 % | 243 |

| style | A | D | C | C′ | B | số WAV |
|---|---|---|---|---|---|---|
| dai | 65.8 % | 65.3 % | 66.7 % | 61.4 % | 45.2 % | 363 |
| lich_su | 51.1 % | **82.2 %** | 46.7 % | 80.0 % | 20.0 % | 45 |
| ngan | 53.8 % | 55.1 % | 49.9 % | 51.0 % | 44.2 % | 1 197 |
| than_mat | 19.1 % | 22.8 % | 18.4 % | 22.1 % | 14.7 % | 136 |
| tieng_anh_viet | 8.3 % | 9.0 % | 9.0 % | 8.3 % | 7.5 % | 133 |

D vượt hẳn ở **câu lịch sự dài** (82.2 % vs 51.1 %) và **giọng Nam** (33.7 % vs 26.7 %) — hợp với việc nó
được huấn luyện trên 6 000 h dữ liệu Việt nhiều nguồn (§9).

---

## 3. Bộ streaming nghe NHẦM thành cái gì

20 cặp hay gặp nhất, cấu hình B + hotword chữ thường (chuỗi model **thật sự** in ra, không bịa cách viết):

| ref | hyp (B nghe ra) | lần |
|---|---|---|
| nhiệt độ hai mươi bốn độ | **nh** độ hai mươi bốn độ | 10 |
| mở nhạc du túp | mở nhạc youtube | 9 |
| mở việt máp | mở việt mát | 9 |
| mở quay | mở quai | 7 |
| mở ca plây | mở ca play | 6 |
| dừng nhạc | **r**ừng nhạc | 5 |
| lọc ngay | **来** | 5 |
| mở dút túp | mở | 5 |
| mở zalo | mở **ion** | 5 |
| mở gia lô / mở da lô | mở zalo | 5 + 5 |
| bật sưởi ghế | bật s**ủ**i ghế | 5 |
| dẫn đường đến bitexco | dẫn đường đến **i** | 4 |
| xem pin | xem **t**in | 4 |
| lọc bụi · lọc ngay đi | **来** | 4 + 4 |
| mở gu gồ máp | **ở** google map | 4 |
| đổi sang hồ sơ chính | **نổ** sang hồ sơ chính | 3 |

Hai họ lỗi **riêng của bộ đa ngữ**, không thấy ở mô hình chuyên Việt:

1. **Rụng phụ âm đầu** — *tắt* → *ắt*, *dừng* → *ừng*, *giảm* → *ảm*, *chế* → *ế*, *mở* → *ở*, *nhiệt* → *nh*.
   [ĐO] có ở **cả** cấu hình có và không hotword, cả khi đã đệm 300 ms im lặng đầu. Đây là chi phí khởi
   động của bộ mã hoá streaming (chunk-16/left-128) — nó chưa có đủ ngữ cảnh trái khi âm đầu tới.
2. **Nhảy sang chữ viết khác** — `来` · `那么` · `こん` · `نổ` · `但是`. Từ điển 16 016 token trộn 8 thứ tiếng,
   không có tín hiệu chọn ngôn ngữ nào được truyền vào từ phía sherpa (`OnlineRecognizer` 1.13.8 không có
   tham số ngôn ngữ; các token `<VI>` · `<ZH>` … có trong `tokens.txt` nhưng **không** được đặt).
   ⇒ với lệnh xe (câu ngắn, 2–4 từ) model không đủ ngữ cảnh để tự khoá vào tiếng Việt.

Và một họ lỗi **của im lặng số**: [ĐO] đệm 1 000 ms im lặng tuyệt đối vào ĐẦU luồng kéo 25 WAV từ 13/25
xuống **1/25** — model đẻ token rác từ khoảng lặng. Trên xe mic chạy liên tục (im lặng thật = tiếng nền),
nhưng đây vẫn là dấu hiệu model này **không ổn định trên đoạn không phải tiếng nói**, đúng thứ nó sẽ gặp
nhiều nhất trong cabin.

---

## 4. Hotword có dùng lại được không — soát bộ mã BPE

Tệp hotword của dự án là **CHỮ HOA CÓ DẤU** (khớp `tokens.txt` của mô hình Việt). Mô hình đa ngữ có từ điển
trộn hoa/thường: mảnh tiếng Việt là **chữ thường** (`▁bật` · `▁mở` · `▁đọc`), mảnh chữ HOA gần như chỉ dành
cho tiếng Anh (`▁THE`). [ĐO host] soát 2 241 dòng hotword qua chính `bpe.model` của từng mô hình:

| Mô hình | cỡ từ điển | dòng mà **mọi từ** là 1 token | mảnh/dòng | dòng bị bẻ **vụn thành ký tự lẻ** |
|---|---|---|---|---|
| A offline-vi (HOA) | 2 000 | 1 149 / 2 241 | 4.92 | 5 |
| D vi-30M-int8 (HOA) | 2 000 | 891 / 2 241 | 5.15 | 7 |
| B stream-multi **HOA (nguyên trạng)** | 16 016 | **0 / 2 241** | **13.68** | **154** |
| B stream-multi **đã hạ chữ thường** | 16 016 | 486 / 2 241 | 6.14 | 8 |

⇒ **Bắt buộc hạ chữ thường** tệp hotword cho mô hình đa ngữ; để nguyên HOA thì 154 dòng thành ký tự lẻ và
mọi dòng bias sai đường token. Mọi số của B ở §2 đều đã dùng bản chữ thường (bản HOA còn tệ hơn).
Chữ viết **dấu thanh kiểu cũ**: 0/2 241 dòng — `SherpaPhraseHotwords` đã sinh đúng kiểu mới ở cả 3 mô hình,
không còn nợ như `voice-mishear` §8.

---

## 5. Ngắt câu — thứ mà cả cuộc thí nghiệm này nhắm vào

Đo: với mỗi WAV, ghi **thời điểm audio** endpoint nổ, trừ đi **điểm hết tiếng** đo bằng RMS (khung 20 ms,
ngưỡng đỉnh −25 dB, sàn −60 dBFS). Số âm ⇒ **cắt giữa câu** (hỏng). Nạp theo khối **320 ms**, đúng như
`VoiceCapture` đọc `AudioRecord`.

| khoảng (endpoint − hết tiếng) | **B** stream-multi | **C** offline-vi + Silero VAD |
|---|---|---|
| ÂM — **cắt giữa câu** | **35 (1.8 %)** | **0 (0 %)** |
| 0 – 400 ms | 33 (1.7 %) | 0 |
| 400 – 700 ms | 37 (2.0 %) | **1 116 (58.8 %)** |
| 700 ms – 1 s | 29 (1.5 %) | **783 (41.2 %)** |
| 1 – 1.5 s | 432 (22.8 %) | 0 |
| 1.5 – 2 s | **1 289 (68.1 %)** | 0 |
| > 2 s | 37 (2.0 %) | 0 |
| **không nổ lần nào** | 7 / 1 899 | 0 / 1 899 |
| **p50 · p90 · max** | **1 600 · 1 800 · 2 250 ms** | **660 · 780 · 920 ms** |

So với đường **đang chạy trên xe** [ĐO xe, `voice-1.68-real.txt`]: trần chốt hay gặp nhất là
`chot=4200ms` (165/299 lượt), có lượt `chot=8400ms`; cộng thời gian giải mã **1 371 – 2 407 ms** ở các lượt
lành mạnh ⇒ **≈ 3–6 s** kể từ lúc người nói xong.

| Đường | trễ sau khi nói xong (p50) |
|---|---|
| **đang ship** (trần + giải mã) [ĐO xe] | **≈ 3 000 – 6 000 ms** |
| **B** streaming [ĐO host] | 1 600 ms (chờ audio) + 20 ms (tính khối cuối) = **≈ 1 620 ms** |
| **C** offline + Silero VAD [ĐO host] | 660 ms (chờ audio) + ~20 ms + **giải mã** = **≈ 700 ms + giải mã** |

Vì sao B chậm gấp đôi C dù đặt `rule2_min_trailing_silence = 0.8 s`: sherpa đếm im lặng đuôi bằng **khung
đã giải mã**, mà bộ mã hoá streaming chunk-16 còn một khoảng trễ nhìn-trước của chính nó; cộng lượng tử hoá
320 ms/khối ⇒ p50 rơi vào 1.6 s. Hạ `rule2` xuống 0.5 s thì **cắt giữa câu** nhảy từ 0 lên 3–4/25 ngay ở bộ
WAV cũ ([ĐO host], lưới `lead × rule2`) — tức không có chỗ ngồi thoải mái cho B.

---

## 6. ⚠ PHÁT HIỆN LỚN NHẤT CỦA PHIÊN — ĐUÔI IM LẶNG GIẾT ĐỘ CHÍNH XÁC

Trong lúc dò tham số, một phép đo phụ hoá ra quan trọng hơn cả câu hỏi ban đầu.
**Mô hình A đang ship**, tệp hotword thật, 25 WAV cũ, chỉ đổi **độ dài đuôi im lặng nối thêm**:

| đuôi im lặng nối thêm | đúng nguyên văn |
|---|---|
| 0.00 s | **22/25** |
| 0.40 s | 18/25 |
| 0.75 s | 15/25 |
| 1.50 s | 11/25 |
| 4.00 s | **6/25** |

[ĐO host] 22/25 → 6/25 chỉ vì nối thêm im lặng. Không đổi mô hình, không đổi hotword, không đổi câu.

**Vì sao chuyện này quan trọng**: đường đang chạy trên xe nạp **nguyên cửa sổ mic tới trần** vào bộ giải mã.
[ĐO xe] `chot=4200ms` với `tieng_dut` thường ở 1–2 s ⇒ **2–3 s đuôi** không phải tiếng nói được đưa thẳng vào
mô hình, lượt nào cũng vậy. Đó chính là vùng mà bảng trên nói là 11–15/25.
Và đúng là log xe có những chuỗi kiểu *"**ừ** bật đèn đọc"* · *"**đang đọc sách**"* · *"mở cửa sổ **bật**"* —
dạng token mọc thêm ở đầu/cuối, hệt như khi nạp đuôi rỗng.

⇒ **Cắt cửa sổ ở điểm ngắt câu không chỉ để NHANH — nó còn để ĐÚNG.** Đây là lý do mạnh nhất để làm C,
mạnh hơn cả lý do độ trễ.

⚠ Mức bằng chứng: phép đo trên dùng **im lặng số tuyệt đối** (mẫu = 0), thứ không tồn tại trên mic thật.
Trên xe đuôi là **tiếng nền cabin**. Nên quan hệ *"đuôi dài ⇒ sai nhiều"* là **[ĐO host]**, còn việc nó giải
thích đúng các chuỗi lạ trong log xe là **[SUY]**. Cách chốt: một lượt trên xe, cùng một câu, chụp WAV thô
qua `ClusterDiag` rồi giải mã hai lần — nguyên cửa sổ vs cắt tại `tieng_dut` — và so chuỗi.

---

## 7. Cỡ · RAM · tốc độ (host, sạch, đo từng tiến trình một)

`/usr/bin/time -l` · 100 lượt / 140.2 s audio · `num_threads=2` · hotword bật:

| Ứng viên | tệp mô hình | **RSS đỉnh** | nạp mô hình | **RTF host** |
|---|---|---|---|---|
| **A** offline-vi (đang ship) | 270.4 MB | 607 MB | 301 ms | **0.0120** |
| **D** vi-30M-int8 | **33.9 MB** | **219 MB** | 236 ms | **0.0091** |
| D′ vi-30M-fp32 | 101.5 MB | 348 MB | 207 ms | 0.0097 |
| **B** stream-multi | 338.9 MB | **827 MB** | 1 391 ms | **0.0382** |
| Silero VAD (thêm vào C) | 0.64 MB | không đo riêng (≪ 1 %) | ~10 ms | ~0.001 |

### [SUY] Ngoại suy sang TRINKET — **không phải phép đo trên xe**

Hệ số quy đổi lấy từ log xe: A giải mã **1 371 ms** cho **3 400 ms** audio (`54 400 mẫu`) với 4 luồng ⇒
**RTF xe ≈ 0.40**; cùng việc đó trên host 2 luồng là 0.0120 ⇒ **xe chậm hơn host ≈ 30×** (đã tính cả chênh
số luồng ⇒ con số này là *bảo thủ*, tức có lợi cho B).

| Ứng viên | RTF xe [SUY] | Nghĩa |
|---|---|---|
| A offline-vi | ≈ 0.40 | khớp [ĐO xe] 1.4–2.4 s cho 4–8 s audio ✔ |
| D vi-30M-int8 | **≈ 0.30** | giải mã ≈ **3/4** thời gian của A |
| B stream-multi | **≈ 1.15** | **> 1 ⇒ KHÔNG theo kịp mic** ở 2 luồng; 4 luồng may ra ≈ 0.6 nhưng khi đó nó ăn hết CPU suốt cả phiên nghe, trong khi A chỉ ăn CPU **một nhịp** ở cuối |

RAM: B là **827/607 = 1.36×** A. [ĐO xe] A nạp mất **16 861 ms** lần đầu trên xe; B có tệp lớn hơn 25 % và
RSS lớn hơn 36 % ⇒ [SUY] nạp lâu hơn nữa. D có tệp **nhỏ hơn 8×** và RSS **nhỏ hơn 2.8×** ⇒ [SUY] đây là
thứ duy nhất trong bảng có khả năng **rút ngắn** 16.8 s đó một cách đáng kể.

---

## 8. Phương án C — offline + Silero VAD: chi tiết cách cắt

VAD chỉ làm **cái đồng hồ**; thứ quyết định độ chính xác là **cắt ở đâu**. Ba cách đã đo trên 25 WAV
(mô hình A, hotword thật):

| cách nạp | 25 WAV cũ | ghi chú |
|---|---|---|
| `segment` — chỉ đoạn VAD giữ lại | **8/25** | VAD mở đoạn **muộn** ⇒ nuốt mất từ đầu câu (*"bật đèn đọc"* → *"ĐÈN ĐỌC SÁCH"*) |
| `window` — từ đầu cửa sổ tới lúc VAD **chốt** | 18/25 | dính thêm cả `min_silence` ⇒ rơi vào đúng cái bẫy §6 |
| **`head` — từ đầu cửa sổ tới HẾT đoạn VAD, margin 0** | **21/25** | giữ trọn đầu câu, cắt sát đuôi |

Tham số đã chốt bằng lưới: `threshold 0.5` · `min_speech 0.10 s` · **`min_silence 0.15 s`** · `margin 0`.
Với bộ này: endpoint p50 **660 ms**, p90 780 ms, **0/1 899 cắt giữa câu**, **0/1 899 không nổ**.

Trên corpus, C mất **2.4 điểm** so với A (897 vs 943). Nhưng đó là vì **corpus vốn đã cắt sát tiếng** —
mốc A ở đây là một cửa sổ *lý tưởng* mà đường trên xe **không bao giờ** có. So với cái xe **thật sự** đang
nạp vào mô hình (cửa sổ 4.2 s, đuôi 2–3 s không tiếng), bảng §6 nói mốc thật thấp hơn nhiều.
⇒ [SUY] trên xe, C **thắng** A, không thua. Chốt bằng đúng phép đo nêu ở cuối §6.

---

## 9. Nguồn gốc + GIẤY PHÉP — chỗ chặn cứng

**B — PengChengStarling** (`https://github.com/PCL-Voice/PengChengStarling`, GitHub chuyển hướng từ
`yangb05/PengChengStarling`):

- README nói: mô hình streaming đa ngữ **8 thứ tiếng** (Trung · Anh · Nga · Việt · Nhật · Thái · Indonesia ·
  Ả Rập), **mỗi thứ tiếng ~2 000 giờ**; kiến trúc **Transducer + Zipformer encoder**; *"7x speed improvement
  in inference compared to Whisper-Large v3"*, *"only 20 % of its size"*.
- **WER tiếng Việt: 7.09** trên `gigaspeech2-vi test` (Whisper-Large-v3: 17.94). Bảng đầy đủ: ja 13.34 ·
  th 17.39 · zh 22.67 · ar 24.37 · id 20.54.
- **Giấy phép: KHÔNG CÓ.** [ĐO] `GET /repositories/912682822` trả `"license": null`; liệt kê thư mục gốc
  (`README.md · config_* · eval.sh · export*.sh · local · shared · train.sh · zipformer`) **không có tệp
  LICENSE**. Repo HF `csukuangfj/…-2025-02-10` cũng **không có** metadata giấy phép (`cardData: None`),
  README của nó chỉ ghi *"Model files are from PengChengStarling"*.
  ⇒ Mặc định là **all-rights-reserved**. **Không được ship** khi chưa hỏi được tác giả.

**D — vi-30M** (`csukuangfj2/sherpa-onnx-zipformer-vi-30M-{int8-,}2026-02-09`):

- README của cả hai repo HF chỉ có một dòng: *"Model files are from
  https://huggingface.co/hynt/Zipformer-30M-RNNT-6000h"*.
- Model card gốc `hynt/Zipformer-30M-RNNT-6000h`: **ZipFormer ~30 M tham số**, RNNT loss, **~6 000 giờ**
  tiếng Việt (VLSP2020/2021/2023, FPT, VIET_BUD500, VietSpeech, FLEURS, VietMed, Sub-GigaSpeech2-Vi,
  ViVoice, Sub-PhoAudioBook). WER: VLSP2025-PublicTest **7.97** · VLSP2023-PublicTest **10.40** ·
  GigaSpeech2-Test **7.56**. Tác giả nói đã **nhất VLSP 2025**.
- **Giấy phép: `cc-by-nc-nd-4.0`** [ĐO] (`cardData.license` của repo gốc) — **phi thương mại + cấm phái sinh**.
- ⚠ **Đây đúng là mô hình mà `specs/kachi-voice-engine-v2.html` đã loại từ 2026-09-14 vì lý do giấy phép**
  (*"Loại `hynt/Zipformer-30M` (CC-BY-NC-ND)"*). Phép đo hôm nay **xác nhận nó nghe tốt hơn thật** — nhưng
  không làm giấy phép đổi. Quyết định dùng hay không là **của owner**, không phải của agent.

---

## 10. KHUYẾN NGHỊ

### B streaming đa ngữ — **NO-GO**

Bốn lý do độc lập, mỗi lý do đủ để loại:

1. **Độ chính xác −11.2 điểm** trên corpus (39.1 % vs 50.3 %) và **14/25 vs 22/25** trên bộ WAV cũ.
   Lỗi đặc trưng là **rụng phụ âm đầu** — mà tiếng Việt phân biệt lệnh bằng chính phụ âm đầu
   (*tắt* / *bật*, *mở* / *đóng*), nên loại lỗi này **đắt hơn** con số thô.
2. **Ngắt câu chậm hơn C 2.4×** (p50 1 600 ms vs 660 ms) và có **1.8 % cắt giữa câu**; hạ ngưỡng để nhanh
   hơn thì cắt giữa câu tăng ngay.
3. **Nặng hơn**: RSS 827 MB (1.36× A), tệp 338.9 MB (1.25× A), [SUY] RTF xe ≈ **1.15 ở 2 luồng** — không
   theo kịp mic; và nó ăn CPU **liên tục suốt phiên nghe** thay vì một nhịp cuối như A.
4. **Không có giấy phép.**

Nói thẳng với owner: ý *"nghe đến đâu xử đến đó"* là **đúng hướng** — nhưng thứ tạo ra cái lợi ấy là
**ngắt câu tự động**, không phải *streaming*. Và ngắt câu thì lấy được **rẻ hơn, nhanh hơn, chính xác hơn**
bằng một tệp VAD 0.64 MB.

### C offline + Silero VAD — **GO** (ưu tiên số 1)

- **Trễ**: p50 **660 ms** chờ audio thay cho trần 4.2–8.4 s ⇒ [SUY] cắt **≈ 2.5–5 s** mỗi lượt.
- **Chất lượng ngắt**: **0/1 899** cắt giữa câu, **0/1 899** trượt. p90 780 ms, max 920 ms.
- **Độ chính xác**: −2.4 điểm so với cửa sổ *lý tưởng* của corpus; [SUY] **+ nhiều điểm** so với cửa sổ
  *thật* trên xe (§6: đuôi 1.5 s đã kéo 22/25 → 11/25).
- **Chi phí**: **0.64 MB** thêm vào gói, ~0.001 RTF, không đổi mô hình, không đổi hotword, không đổi
  `VoiceIntentParser`. Rủi ro hồi quy thấp nhất trong cả bảng.
- Tham số đã chốt off-car: `min_silence 0.15 s` · `threshold 0.5` · `min_speech 0.10 s` · nạp kiểu **`head`,
  margin 0** (§8). Vẫn **giữ trần 8.4 s** làm lưới an toàn cho cabin ồn liên tục.

### D vi-30M-int8 — **GO về kỹ thuật, CHẶN vì giấy phép** ⇒ cần owner quyết

Nếu chỉ nhìn số thì đây là kẻ thắng rõ ràng:

| | A đang ship | D vi-30M-int8 |
|---|---|---|
| corpus | 50.3 % | **52.1 % (+1.8)** |
| 25 WAV cũ | 22/25 | **22/25** (hoà) |
| giọng Nam | 26.7 % | **33.7 % (+7.0)** |
| câu lịch sự dài | 51.1 % | **82.2 % (+31.1)** |
| tệp | 270.4 MB | **33.9 MB (1/8)** |
| RSS đỉnh | 607 MB | **219 MB (1/2.8)** |
| RTF host | 0.0120 | **0.0091 (−24 %)** |
| [ĐO xe] nạp lần đầu | 16.9 s | [SUY] ngắn hơn nhiều |
| giấy phép | Apache-2.0 ✔ | **CC-BY-NC-ND-4.0 ✘** |

Tức: **nhanh hơn, nhẹ hơn 8 lần, nghe đúng hơn**, và nó **không thua một ô nào** trong bảng §2.2 ngoài
`control_toggle` (−3.2) và `unknown` (−4.7). Nếu giấy phép thông, đây là thứ đáng đổi ngay.
Nhưng giấy phép **CC-BY-NC-ND** cấm dùng thương mại **và** cấm phái sinh, và spec V2 đã loại nó một lần rồi.
⇒ **Không tự ý đổi.** Việc cần làm: hỏi tác giả `hynt` xin ngoại lệ, hoặc tìm mô hình Việt nhỏ tương đương
có giấy phép mở. Ghi vào backlog, owner chốt.

### Thứ tự đề nghị

1. **Làm C ngay** (Silero VAD thay endpointer RMS) — lợi cả tốc độ lẫn độ chính xác, giấy phép sạch, rủi ro
   thấp nhất. Đi đúng 4 tầng CLAUDE.md §14: chứng minh bằng WAV thô trên xe trước.
2. **Đo lại §6 trên xe thật** — một lượt, cùng câu, giải mã 2 lần (nguyên cửa sổ vs cắt tại `tieng_dut`).
   Đây là phép đo rẻ nhất trả lời được câu đắt nhất.
3. **Hỏi owner về giấy phép D.** Không đụng vào cho tới khi có câu trả lời.
4. **Bỏ B.** Nếu sau này vẫn muốn streaming, điều kiện mở khoá là: có mô hình streaming **chuyên tiếng Việt**
   (không phải đa ngữ) + giấy phép rõ + RTF host < 0.02.

---

## 11. Những thứ tệp này KHÔNG trả lời được

| Câu hỏi | Vì sao chưa trả lời được | Cách chốt |
|---|---|---|
| Trên xe C nhanh/đúng hơn A bao nhiêu? | host ≠ TRINKET; đuôi ở đây là im lặng số, trên xe là tiếng cabin | chụp WAV thô qua `ClusterDiag`, giải mã 2 lần (§6) |
| Giọng người thật + mic 4 kênh + ồn đường? | corpus toàn TTS | đợt thu giọng thật — `voice-recording-campaign-2026-09-16.md` |
| B có khá hơn nếu ép token ngôn ngữ `<VI>`? | `OnlineRecognizer` 1.13.8 **không có** tham số ngôn ngữ | cần vá sherpa hoặc xuất lại ONNX — ngoài phạm vi |
| RTF thật của B trên ARM Android? | chỉ đo trên Apple Silicon | nếu ai đó muốn theo đuổi: build AAR, đo bằng `KachiVoiceTiming` |
| D nạp trên xe mất bao lâu? | chưa chạy trên xe (và đang bị chặn giấy phép) | chỉ đo sau khi owner gỡ chặn |
| RSS host có phản ánh RSS Android không? | ORT cấp phát khác nhau giữa 2 nền | đo bằng `dumpsys meminfo` trên xe |

---

## 12. Tái lập

```bash
# 1. Bộ streaming đa ngữ
python3 -m venv /tmp/sherpa-venv && /tmp/sherpa-venv/bin/pip install sherpa-onnx==1.13.8 numpy sentencepiece
# tải 5 tệp từ https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-ar_en_id_ja_ru_th_vi_zh-2025-02-10
/tmp/sherpa-venv/bin/python scripts/voice/stream-matrix.py --model <DIR> --make-bpe-vocab
/tmp/sherpa-venv/bin/python scripts/voice/stream-matrix.py --model <DIR> \
    --corpus /tmp/kachi-voice-corpus --wav /tmp/kachi-voice-wav \
    --hotwords none core/build/hotwords/hotwords-phrases.txt --lower-hotwords \
    --pad zero --lead-ms 300 --rule2 0.8 --dump /tmp/stream_full.tsv

# 2. Phương án C (offline + Silero VAD) — tham số đã chốt
curl -L -o /tmp/silero_vad.onnx \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
/tmp/sherpa-venv/bin/python scripts/voice/stream-matrix.py --mode offline-vad \
    --model <DIR_OFFLINE> --vad-model /tmp/silero_vad.onnx \
    --vad-feed head --vad-margin 0.0 --vad-min-silence 0.15 \
    --corpus /tmp/kachi-voice-corpus --wav /tmp/kachi-voice-wav \
    --hotwords core/build/hotwords/hotwords-phrases.txt --pad zero --lead-ms 0

# 3. Mốc offline (công cụ cũ, không đổi)
/tmp/sherpa-venv/bin/python scripts/voice/mishear-table.py --model <DIR_OFFLINE> \
    --hotwords none core/build/hotwords/hotwords-phrases.txt --summary-only
```

Thời gian chạy [ĐO host]: corpus 1 899 WAV — offline **38 s/cấu hình**, offline+VAD **~90 s/cấu hình**,
streaming **~320 s/cấu hình**. Toàn bộ phiên (tải mô hình + 9 lượt corpus + lưới tham số) ≈ 45 phút.
