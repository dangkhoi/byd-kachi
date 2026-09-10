# Credits — ClusterNav

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
