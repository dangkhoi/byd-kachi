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

## Ghi công mô hình NGHE (nghĩa vụ giấy phép — CC BY-NC-ND 4.0)

Từ **1.69** mô hình nhận dạng mặc định cho máy cài mới là **`zipformer-vi-30M-int8-2026-02-09`**:

| | |
|---|---|
| Tác giả mô hình | **hynt** — `Zipformer-30M-RNNT-6000h` (~30 M tham số, RNNT, ~6 000 giờ tiếng Việt) |
| Gói sherpa-onnx | `csukuangfj2/sherpa-onnx-zipformer-vi-30M-int8-2026-02-09` |
| Giấy phép | **CC BY-NC-ND 4.0** — ghi công · phi thương mại · **cấm phái sinh** |
| Nguồn | `https://huggingface.co/hynt/Zipformer-30M-RNNT-6000h` |
| Cỡ | ~34 MB (4 tệp, ghim sha256 trong `SherpaModelCatalog`) |

**Vì sao nó ở đây dù spec `kachi-voice-engine-v2.html` từng loại nó (2026-09-14) vì đúng giấy phép này**: ngày
2026-09-16 **owner quyết định ngược lại** cho dự án của mình — *"phi lợi nhuận, vui vẻ với anh em nên cũng ko
quan trọng lắm về license đâu nhỉ"*. Đây là quyết định của **owner**, không phải của agent (ranh giới đã ghi ở
`docs/diagnostics/voice-stream-eval-2026-09-16.md` §9).

Ba nghĩa vụ và cách dự án giữ:

- **BY** — dòng ghi công hiện ở *Cài đặt › Giọng nói › Về mô hình nghe*, trong lời đáp `state.voice_model` của cầu kiểm thử, và ở đây. Cả ba
  đọc **cùng một** trường dữ liệu (`SherpaModelCatalog.attributions()`) nên không lệch nhau được.
- **NC** — điều kiện owner tự khẳng định cho dự án này.
- **ND** — **không fine-tune, không sửa** mô hình ở bất kỳ đâu trong kho mã; ship đúng bộ tệp int8 đã công bố.
  Tệp mô hình **không** đóng trong APK: xe tải thẳng từ HuggingFace khi người dùng bấm, nên APK không phát tán
  lại trọng số.

> ⚠ Bảng `assets/voice/zipformer-vi-30M-int8-2026-02-09.bpe_vocab.txt` được sinh **từ** `bpe.model` của gói (sherpa
> không nhận `bpe.model` nhị phân làm `bpeVocab`). Dự án hiểu đây là một phép **đổi định dạng cho runtime**, không
> đụng tới trọng số mô hình. Đó là **cách hiểu của dự án, không phải một kết luận pháp lý** — muốn chắc thì hỏi
> tác giả, và đó là việc của owner.
>
> ⚠ Bảng BPE này **KHÔNG dùng chung** được với gói `zipformer-vi-2025-04-20`: [ĐO 2026-09-16] cả hai đúng 2 000
> mảnh mà **1 997/2 000 dòng khác nhau**. Dùng nhầm bảng thì sherpa **lặng lẽ bỏ** mọi cụm hotword — biasing trông
> như đang bật mà không làm gì. Bài canh `SherpaModelCatalogTest` khoá đúng điều này.
