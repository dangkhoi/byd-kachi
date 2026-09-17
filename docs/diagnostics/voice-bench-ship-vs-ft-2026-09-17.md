# Benchmark off-car: gói NGHE ship vs G fine-tune trên giọng THẬT (2026-09-17)

> **Trạng thái**: Current · **Mục đích**: chốt off-car câu hỏi *"gói G fine-tune có đáng ship kèm không"* bằng
> số đo trên **bộ giữ-riêng giọng thật** (không phải corpus TTS — bài học corpus đánh lừa 2 lần). Quyết MẶC ĐỊNH
> vẫn để owner trên xe (mic 4 kênh + ồn + RTF thật). Mọi số [ĐO] host, `/tmp/sherpa-venv` sherpa_onnx 1.13.8.

## 1. Bộ đo
- **270 câu giọng THẬT** (`scratchpad/ft/real-all/{cases.tsv, *.wav}`): owner + con gái + 3 giọng miền Nam
  (chậm/nhanh/có-nhạc), mỗi câu là một lệnh xe/nav/media thật. Đây là bộ **giữ riêng**, không nằm trong tập
  fine-tune.
- **Phép đo**: exact-match transcript sau chuẩn hoá (bỏ dấu + thường + bỏ ký tự lạ). Khắt khe hơn "đúng ý định"
  (parser :core tolerant), nên số tuyệt đối thấp hơn con số intent 74.8/84.8% của `voice-ft-2026-09-16.md`, **nhưng
  thứ hạng nhất quán** — đủ để so hai gói.
- Cấu hình giống xe: `modified_beam_search` · beam 4 · hotwords-phrases.txt (biasing) · score 3.0 · int8.

## 2. Kết quả [ĐO] 2026-09-17

| Gói | Khớp câu (exact-match) | RTF host | Cỡ int8 |
|---|---|---|---|
| SHIP `zipformer-vi-int8` | **167/270 = 61.9%** | 0.0127 | 77.1 MB |
| BASE `gipformer1.5-65M` | 168/270 = 62.2% | — | — |
| **G `gipformer-vi-ft-ep2`** | **177/270 = 65.6%** | **0.0130** | 78.3 MB |

- G hơn ship **+10 câu** (+3.7 điểm exact-match), hơn base +9 — fine-tune trên giọng thật lãi thật, đúng chỗ
  thủng (nói nhanh, giọng trẻ em, giọng miền Nam).
- RTF G ≈ ship (chênh 2%) trên host ⇒ nếu ship chạy được trên xe thì G cũng chạy được. RTF thật ARM = VFT-2 🚗.

## 3. Bằng chứng G tốt hơn — **54 câu G nghe ĐÚNG mà SHIP nghe SAI**

Trích (danh sách đầy đủ chạy lại bằng `scripts/voice/ft/bench-intent.py`):

| Câu (đúng) | SHIP nghe | G nghe |
|---|---|---|
| đóng hết kính | ĐÓNG MÁY TÍNH | ĐÓNG HẾT KÍNH ✓ |
| bật đèn đọc | BẠN ĐANG ĐỌC | BẬT ĐÈN ĐỌC ✓ |
| dừng nhạc | VƯỜN NHẠC / GIƯỜNG NHÀ | DỪNG NHẠC ✓ |
| bật ghế sưởi | BẰNG CÁI SƯỞI | BẬT GHẾ SƯỞI ✓ |
| mở cốp sau | MỞ GÓC SAU | MỞ CỐP SAU ✓ |
| xem áp suất lốp trước trái | SẢN XUẤT LỐP CHỮA CHÁY | XEM ÁP SUẤT LỐP TRƯỚC TRÁI ✓ |
| pin còn bao nhiêu | BIN CÒN ĐƯỢC BAO NHIÊU | PIN CÒN BAO NHIÊU ✓ |
| giảm âm lượng | MẮM LƯỢNG | GIẢM ÂM LƯỢNG ✓ |
| chế độ lái thể thao | CHẾ ĐỘ LÁI | CHẾ ĐỘ LÁI THỂ THAO ✓ |

(Có một chiều ngược lại nhỏ hơn — G sai vài câu ship đúng — nhưng ròng G +10.)

## 4. Kết luận + quyết định
- **G đáng ship kèm** (cờ `experimental` + nút chọn) — đã wire vào `SherpaModelCatalog.GIPFORMER_VI_FT`.
- **KHÔNG đổi mặc định** off-car (owner chốt 2026-09-17): mặc định `zipformer-vi-int8` giữ nguyên, owner nghe
  thật + so trên xe (mic 4 kênh + ồn) rồi mới gạt chọn. Bài học: host/corpus đã đánh lừa 2 lần.
- 🚗 **VFT-2**: đo RTF thật trên ARM (ước ~0.35, sát trần 0.30). 🚗 owner mirror gói G lên `byd-kachi` để tải OTA
  (tới lúc đó side-load USB chạy ngay).

## 5. Tái lập
```bash
# bộ bench (chép 1 lần): scratchpad/ft/real-all → /tmp/kachi-bench
/tmp/sherpa-venv/bin/python scripts/voice/ft/bench-intent.py <model_dir> <nhãn>
# RTF: scratchpad/ft/bench.py <model_dir> none   (đọc /tmp/kachi-voice-corpus/*.wav)
```
Model: SHIP = `scratchpad/model-int8-hf` · G = `~/.kachi/model-gip15-ep2` (sha256 ở `SherpaModelCatalog.GIPFORMER_VI_FT`).
