# voice/ — gói giọng nói tải trong app (kênh OTA cùng repo)

Owner 2026-09-16: gói giọng phải **tải được trong app**, không USB. GitHub không có `gh`/release ở máy soạn thảo,
nên gói được đăng **ngay trong repo** và app tải từng tệp qua `raw.githubusercontent.com/dangkhoi/byd-kachi/main/voice/...`
(cùng cách `apk/` cho OTA), kiểm **sha256 + kích thước** từng tệp (`SherpaTtsCatalog`, `VoiceModelStore`).

| Gói | Nội dung | Cỡ | Giấy phép |
|---|---|---|---|
| `tts/piper-vi_VN-vais1000-medium/` | Piper VITS tiếng Việt VAIS-1000 (medium) + `espeak-ng-data` **đã tỉa còn phần tiếng Việt** (11 tệp, 728 KB thay 18 MB) — [ĐO host 2026-09-16] audio giống hệt gói đầy đủ | 61 MB | CC-BY-4.0 (VAIS-1000) · Piper · sherpa-onnx |
| `tts/kachi-giong-be-v1/` | **Giọng Kachi bé** — gói **phát âm sẵn**, không phải mô hình: 1 607 clip ADTS AAC-LC 32 kbps / 24 kHz mono (425 câu trọn · 158 đầu câu · 24 đuôi/đơn vị · 1 000 số 0–999) + `index.tsv`/`num.tsv` để tra theo **chuỗi chính xác**. Sinh **một lần trên máy soạn thảo** bằng F5-TTS nhân bản từ một bản thu có đồng thuận; xe chỉ tra bảng và phát. Số đo chính xác (byte · thời lượng · tỉ lệ ASR đọc lại đúng từng clip) nằm trong `manifest.json` của chính gói — **một chỗ duy nhất**, đừng chép sang đây. | xem `manifest.json` | audio tự sinh · mô hình sinh ra nó: `toandev/F5-TTS-Vietnamese` **CC-BY-NC-4.0** (ghi công + đồng thuận ở `tts/kachi-giong-be-v1/LICENSE-NOTES.md`) |

Bảng ghim: `tts/piper-vi_VN-vais1000-medium.sha256.tsv` · `tts/kachi-giong-be-v1.sha256.tsv`. Gói gốc: `github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models`
(`vits-piper-vi_VN-vais1000-medium.tar.bz2`). Đổi tệp ⇒ phải ghim lại sha trong `SherpaTtsCatalog` (test khoá).
Side-load USB vẫn chạy: `<thẻ>/Android/data/com.byd.launcher/files/sherpa/import/piper-vi_VN-vais1000-medium/<đường dẫn tương đối>`.

---

## Mô hình NGHE — **đúng một gói**, Apache-2.0

Bản release production (owner 2026-09-21) giữ **đúng một** mô hình nhận dạng: gói đang chạy tốt trên xe.

| | |
|---|---|
| Gói | `zipformer-vi-int8-2025-04-20` (`SherpaModelCatalog.ZIPFORMER_VI_INT8`, cũng là `DEFAULT_ID`) |
| Nguồn trọng số | `zzasdf/viet_iter3_pseudo_label` (~70 000 giờ pseudo-label) |
| Gói sherpa-onnx | `csukuangfj/sherpa-onnx-zipformer-vi-int8-2025-04-20` — tải trực tiếp, không cổng |
| Giấy phép | **Apache-2.0** ⇒ **không có nghĩa vụ ghi công** nào phải hiện trên xe |
| Cỡ | 74 MB (4 tệp, ghim sha256 + kích thước trong `SherpaModelCatalog`) |
| Bảng BPE | `assets/voice/zipformer-vi-2025-04-20.bpe_vocab.txt` (đóng theo APK, không tải) |

Tệp mô hình **không** nằm trong APK: xe tải thẳng từ HuggingFace khi người dùng bấm *Tải mô hình* trong
*Cài đặt › Giọng nói*, kiểm sha256 + cỡ từng tệp.

> ⚠ Tên asset BPE mang id của bản **fp32** (`zipformer-vi-2025-04-20`) là **cố ý**: hai bản là một bản huấn luyện,
> `tokens.txt` giống nhau tới từng byte, nên chúng dùng chung bảng BPE — `VoiceEngine` tra asset theo
> `SherpaModel.bpeVocab`, không theo `id`. Bài canh `SherpaModelCatalogTest` ghim cả điều này lẫn điều ngược lại
> (hai bản huấn luyện KHÁC nhau thì **không** được dùng chung bảng: [ĐO 2026-09-16] hai bảng cùng 2 000 mảnh mà
> **1 997/2 000 dòng khác nhau**, và dùng nhầm thì sherpa **lặng lẽ bỏ** mọi cụm hotword — biasing trông như đang
> bật mà không làm gì).

### Bốn gói đã BỎ (2026-09-21) — và vì sao ghi lại ở đây

Tới 1.87 danh mục giữ năm gói để A/B trên xe, kèm hai nút *"chuyển sang mô hình nhẹ"* / *"gỡ bản nặng"* trong Cài
đặt. Owner chốt dừng thử nghiệm mô hình nghe, nên cả bốn gói **và** bề mặt cho-chọn-mô-hình đã gỡ khỏi mã (sha256
+ URL của chúng còn trong git history):

| Gói | Giấy phép | Vì sao bỏ |
|---|---|---|
| `zipformer-vi-2025-04-20` (fp32, 266 MB) | Apache-2.0 | [ĐO xe] nặng gấp 3,6× mà cùng kết quả giải mã với bản int8 — int8 đã thay nó từ 1.66 |
| `zipformer-hataphu-vi` | MIT | repo HF **có cổng** (401) ⇒ chưa bao giờ ghim sha256 ⇒ chưa bao giờ tải được |
| `zipformer-vi-30M-int8-2026-02-09` | CC BY-NC-ND 4.0 | [ĐO giọng THẬT owner 2026-09-16] sai **~7/30** câu so với ≈2/30 của gói đang ship (corpus TTS từng chấm nó cao hơn +1,8 điểm — nó **đã đánh lừa**) |
| `gipformer-vi-ft-ep2` (fine-tune) | MIT | +10 điểm trên **host** với giọng thu sẵn, chưa bao giờ đo trên mic cabin |

⇒ Từ lượt này bản phát hành **không ship gói nào mang giấy phép họ BY**, nên hàng *"Về mô hình nghe"* trong Cài
đặt tự ẩn. Cơ chế ghi công (`SherpaModelCatalog.attributions()` → Cài đặt · `state.voice_model` · README) vẫn còn
nguyên và vẫn đọc **cùng một** trường dữ liệu: thêm lại một gói CC BY là ba bề mặt tự hiện lời ghi công.
