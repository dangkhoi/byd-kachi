# ═══ libkachimem.so — thư viện native DUY NHẤT của Kachi (CLOSE-4 · WAKE-MALLOPT) ════════════════════════
#
# Doc: `docs/diagnostics/offcar-2026-09-26/wake-mallopt-ndk.md`.
#
# Cố ý nhỏ nhất có thể: MỘT tệp C, KHÔNG C++ ⇒ không link STL ⇒ APK không phải chở `libc++_shared.so`.
# Không `find_package`, không dependency, không `target_link_libraries` (chỉ cần `libc` — NDK link mặc định).
# `abiFilters` ở `app/build.gradle.kts` giữ arm64-v8a ⇒ CMake chỉ chạy cho một ABI.
cmake_minimum_required(VERSION 3.22)

project(kachimem LANGUAGES C)

add_library(kachimem SHARED kachimem.c)

# -Os: tệp này không nằm trên đường nóng nào (chỉ chạy ở mốc pha), nên ưu tiên KÍCH CỠ.
# -Wall -Wextra -Werror: vài chục dòng C thì không có lý do gì để chấp nhận một cảnh báo nào.
# -fvisibility=hidden an toàn vì JNIEXPORT của NDK là `__attribute__((visibility("default")))`
# [ĐO NDK r30 `sysroot/usr/include/jni.h:1105`] ⇒ đúng hai hàm JNI còn export, phần còn lại ẩn.
target_compile_options(kachimem PRIVATE -Os -fvisibility=hidden -Wall -Wextra -Werror)

# Bỏ section chết + không giữ bảng ký hiệu gỡ lỗi trong .so đã ship.
target_link_options(kachimem PRIVATE "-Wl,--gc-sections" "-Wl,--as-needed")
