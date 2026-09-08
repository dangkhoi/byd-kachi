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
