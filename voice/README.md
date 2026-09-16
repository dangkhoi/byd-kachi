# voice/ — gói giọng nói tải trong app (kênh OTA cùng repo)

Owner 2026-09-16: gói giọng phải **tải được trong app**, không USB. GitHub không có `gh`/release ở máy soạn thảo,
nên gói được đăng **ngay trong repo** và app tải từng tệp qua `raw.githubusercontent.com/dangkhoi/byd-kachi/main/voice/...`
(cùng cách `apk/` cho OTA), kiểm **sha256 + kích thước** từng tệp (`SherpaTtsCatalog`, `VoiceModelStore`).

| Gói | Nội dung | Cỡ | Giấy phép |
|---|---|---|---|
| `tts/piper-vi_VN-vais1000-medium/` | Piper VITS tiếng Việt VAIS-1000 (medium) + `espeak-ng-data` **đã tỉa còn phần tiếng Việt** (11 tệp, 728 KB thay 18 MB) — [ĐO host 2026-09-16] audio giống hệt gói đầy đủ | 61 MB | CC-BY-4.0 (VAIS-1000) · Piper · sherpa-onnx |

Bảng ghim: `tts/piper-vi_VN-vais1000-medium.sha256.tsv`. Gói gốc: `github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models`
(`vits-piper-vi_VN-vais1000-medium.tar.bz2`). Đổi tệp ⇒ phải ghim lại sha trong `SherpaTtsCatalog` (test khoá).
Side-load USB vẫn chạy: `<thẻ>/Android/data/com.byd.launcher/files/sherpa/import/piper-vi_VN-vais1000-medium/<đường dẫn tương đối>`.
