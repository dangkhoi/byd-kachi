# Credits — Kachi (fork của ClusterNav)

## dadb — embedded ADB client
[`dev.mobile:dadb`](https://github.com/mobile-dev-inc/dadb) — ADB client thuần JVM, nhúng trong app để tự nối
`localhost:5555` → chạy lệnh đặc quyền dưới uid shell (no-root). **License: Apache-2.0.**

## navopen.jar
`app/src/main/assets/navopen.jar` — code của chính dự án (HAL writer ghi frame nav xuống cụm qua reflection),
chạy bằng `app_process` phía uid shell. Không phải thư viện bên thứ ba.

## Bundled / transitive dependencies · Phụ thuộc đóng gói / gián tiếp

Các thư viện dưới đây được **đóng gói vào APK gián tiếp qua `dadb`** (và Kotlin runtime). Ghi công đầy đủ để tuân thủ giấy phép.
The libraries below are **bundled into the APK transitively via `dadb`** (and the Kotlin runtime). Attributed here for license compliance.

### okio
[`com.squareup.okio:okio`](https://github.com/square/okio) — thư viện I/O (buffer/streams) mà `dadb` dùng cho giao tiếp ADB.
I/O library (buffers/streams) used by `dadb` for ADB communication. **License: Apache-2.0.**

### Kotlin standard library
[`org.jetbrains.kotlin:kotlin-stdlib`](https://github.com/JetBrains/kotlin) — runtime chuẩn của Kotlin, đi kèm mọi module Kotlin trong app.
Kotlin's standard runtime, bundled with every Kotlin module in the app. **License: Apache-2.0.**

### Bouncy Castle
[Bouncy Castle](https://www.bouncycastle.org/) — thư viện mật mã mà `dadb` dùng để tạo/ký khoá RSA cho xác thực ADB (ADB key crypto).
Cryptography library used by `dadb` to generate/sign the RSA key for ADB authentication. **License: Bouncy Castle License (MIT-style / adaptation of the MIT license).**

## Giọng nói tại máy · On-device voice (V2, cập nhật 2026-10-09)

Âm thanh KHÔNG rời khỏi đầu xe. Audio never leaves the head unit.

### sherpa-onnx (Android AAR)
[`k2-fsa/sherpa-onnx`](https://github.com/k2-fsa/sherpa-onnx) — engine nhận dạng tiếng nói, phát hiện từ khoá "Hey Kachi" và đọc
phản hồi (TTS) tại máy; đóng gói `libsherpa-onnx-jni.so`. Speech recognition, keyword spotting and TTS engine. **License: Apache-2.0.**

### ONNX Runtime
[`microsoft/onnxruntime`](https://github.com/microsoft/onnxruntime) — đóng gói `libonnxruntime.so` (đi kèm AAR sherpa-onnx).
Bundled with the sherpa-onnx AAR. **License: MIT © Microsoft Corporation.**

### Mô hình nghe · Recognition model `sherpa-onnx-zipformer-vi-int8-2025-04-20`
Trọng số `zzasdf/viet_iter3_pseudo_label`, gói sherpa `csukuangfj/sherpa-onnx-zipformer-vi-int8-2025-04-20`. KHÔNG đóng trong APK: tải
trong app (*Cài đặt › Giọng nói*), kiểm sha256. Not bundled; downloaded in-app, sha256-verified. **License: Apache-2.0.**

### Mô hình từ khoá · Keyword model `sherpa-onnx-kws-zipformer-gigaspeech-3.3M` (int8)
Phát hiện "Hey Kachi"; tệp nằm ở `voice/kws/` của repo này, tải trong app. Wake-word spotter, hosted in `voice/kws/`. **License: Apache-2.0 (k2-fsa).**

### Giọng đọc · TTS voice Piper `vi_VN-vais1000-medium`
Gói `vits-piper-vi_VN-vais1000-medium` (sherpa-onnx `tts-models`), host tại `voice/tts/piper-vi_VN-vais1000-medium/`, tải trong app.
Dữ liệu huấn luyện VAIS-1000 Vietnamese Speech Synthesis Corpus (IEEE DataPort) — **License: CC-BY-4.0** (ghi công bắt buộc · attribution
required). Mô hình Piper ([`rhasspy/piper`](https://github.com/rhasspy/piper)) — MIT.

### espeak-ng data (đã tỉa phần tiếng Việt · Vietnamese subset)
`voice/tts/piper-vi_VN-vais1000-medium/espeak-ng-data/` (11 tệp: `phondata`, `phonindex`, `phontab`, `vi_dict`, `lang/aav/vi*`…) — Piper
dùng espeak-ng để chuyển chữ thành âm vị. Phonemizer data required by Piper.
[`espeak-ng/espeak-ng`](https://github.com/espeak-ng/espeak-ng) — **License: GPL-3.0-or-later.** Bản sao nguyên trạng, không sửa nội dung;
mã nguồn đầy đủ tại repo gốc. Redistributed unmodified; full source at the upstream repository.

### Đã gỡ · Removed
Vosk (`vosk-android`), mô hình `vosk-model-small-vn-0.4` và JNA — không còn đóng gói từ V2 (sherpa-onnx thay thế). No longer bundled since V2.

## Research references — clean-room (MIT) · Tham chiếu nghiên cứu — clean-room (MIT)

Kachi launcher's car telemetry/control layer (feature **W1**) reimplements — **clean-room** — the BYD DiLink HAL API
surface documented by the two MIT-licensed projects below. We describe the API + numeric feature-ids + CAN opcodes
(facts about the BYD HAL, not copyrightable expression) and re-author the code on ClusterNav's own `BydHal`
reflection infrastructure; **no source is copied verbatim**.

Lớp dữ liệu/điều khiển xe của Kachi (**W1**) **viết lại clean-room** từ bề mặt API HAL BYD DiLink mà 2 dự án MIT dưới
đây đã tài liệu hoá. Chỉ dùng lại FACTS (feature-id số, CAN opcode, tên method HAL), **KHÔNG copy mã nguồn**.

### Overdrive-release
[`yash-srivastava/Overdrive-release`](https://github.com/yash-srivastava/Overdrive-release) — cầu BYD ↔ Home-Assistant;
nguồn của bảng feature-id số + catalog telemetry/control (harvest vào `docs/diagnostics/kachi-capability-catalog-2026-09-10.md`).
**License: MIT © 2026 Yash Srivastava.**

### byd-dashcast
[`Kiroha/byd-dashcast`](https://github.com/Kiroha/byd-dashcast) — kỹ thuật cluster-cast + CAN HUD/nav (AutoContainer opcodes, NaviInfo).
**License: MIT © 2026 Cedric Carre.**
