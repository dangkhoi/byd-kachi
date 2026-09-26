# `:wake` trả bộ nhớ native về hệ — JNI `mallopt(M_PURGE/M_DECAY_TIME)` + NDK/CMake vào build (CLOSE-4) — off-car 2026-09-26

> **Trạng thái**: Current · **Cập nhật**: 2026-09-26 (off-car, **chưa lên xe**) · **Mục đích**: ghi lại (a) vì sao
> phải thêm một thư viện native ~4 KB vào Kachi, kèm trích dẫn AOSP cho từng hằng số; (b) NDK/CMake nào được cài,
> cài bằng cách nào, để phiên sau dựng lại đúng bản đó; (c) bốn mốc pha gọi trim; (d) **phép đo trên xe** cần chạy
> để biết bản vá này thật sự ăn bao nhiêu MB.
> Liên quan: `diagnostics/ram-audit-2026-09-25.md` §4 (chẩn đoán gốc), `diagnostics/perf-oncar-2026-09-26/after-oncar-2.69.md`
> (số `:wake` trên xe), backlog **CLOSE-4 · WAKE-MALLOPT** (owner duyệt 2026-09-26, mục 7a *"7a OK"*).

---

## 0. Bốn dòng kết luận

1. `:wake` (tiến trình nghe câu gọi + nhận dạng) là tiến trình **im lặng nhất** của Kachi: nạp mô hình ASR int8
   74 MB rồi gần như không cấp phát gì nữa. Đúng vì thế nó là tiến trình **giữ rác nạp lâu nhất** — jemalloc của
   Android chỉ purge theo *tick sự kiện malloc*, không có luồng nền.
2. Không có API Java/Kotlin nào purge được malloc heap ⇒ phải có JNI. Thư viện mới `libkachimem.so` = **một tệp C
   64 dòng (≈20 dòng mã, còn lại là trích dẫn AOSP), 4 056 byte trong APK**, export đúng 2 hàm, `NEEDED` đúng `libc.so` (không kéo `libc++_shared.so`).
3. Thêm build native ⇒ cài **NDK r30 LTS (30.0.16248370)** + **CMake 3.31.6** của SDK (kèm ninja), ghim tường minh
   trong `app/build.gradle.kts`. APK **43 483 553 → 43 500 011 byte (+16 458 B, +0,038 %)** [ĐO, xem §5].
4. **Hiệu quả thật CHƯA ĐO** (🚗). Số 126 MB "đã free không trả hệ" là [ĐO **máy ảo**] 2026-09-25; cùng ngày trên
   **xe** thì `:wake` free chỉ **9,2 MB** trong khi vẫn 180 MB PSS ⇒ trên xe phần thắng đến từ đâu, và bao nhiêu,
   là câu hỏi mở (§6). Bản vá này an toàn khi nó không ăn (no-op), nhưng **đừng ghi vào backlog là "−100 MB"** tới
   khi có dòng log của xe.

---

## 1. Cơ chế — mọi hằng số đều trích từ source, không từ trí nhớ (CLAUDE.md §3)

Nguồn đã fetch trong phiên này: `platform/bionic` tag **android-10.0.0_r47** (DiLink 3/4 = Android 10) và
**android-12.0.0_r34** (DL5), `platform/frameworks/base` r47, `platform/external/jemalloc_new` r47, cộng header
của NDK r30 vừa cài.

| Hằng / hàm | Giá trị | Nguồn `file:line` | Từ API |
|---|---|---|---|
| `M_DECAY_TIME` | `-100` | `bionic/libc/include/malloc.h:164` (r47) · `:165` (A12) · NDK r30 `sysroot/usr/include/malloc.h:222` | 27 |
| `M_PURGE` | `-101` | `bionic/libc/include/malloc.h:171` (r47) · `:172` (A12) · NDK r30 `:229` | 28 |
| `M_PURGE_ALL` | `-104` — **KHÔNG dùng** | NDK r30 `malloc.h:229-241` (*"Available since API level 34"*) | 34 |
| `int mallopt(int, int)` | trả **1 = ok, 0 = lỗi** | `bionic/libc/include/malloc.h:174-181` | 26 |

minSdk của Kachi = **29** ⇒ `M_PURGE` (28) và `M_DECAY_TIME` (27) dùng được **không cần cổng phiên bản**;
`M_PURGE_ALL` (34) thì xe API 29/31-32 sẽ rơi vào `return 0` của `je_mallopt` (tuỳ chọn lạ) ⇒ không dùng.

### 1.1 `M_DECAY_TIME 0` — thi hành

`bionic/libc/bionic/jemalloc_wrapper.cpp:67-104`: `value ? 1000 : 0` ms, đặt `arenas.{dirty,muzzy}_decay_ms` (cho
arena **sẽ** sinh) rồi lặp `arena.<i>.{dirty,muzzy}_decay_ms` (cho arena **đã** có).

Vì sao cần: Zygote đặt `mallopt(M_DECAY_TIME, 1)` cho **mọi** app
(`frameworks/base/core/jni/com_android_internal_os_Zygote.cpp:523`) ⇒ 1000 ms. Với decay > 0 thì purge chỉ chạy khi
`arena_decay_ticks` tới hạn **1000 sự kiện malloc/free** trên luồng đó
(`external/jemalloc_new/include/jemalloc/internal/arena_types.h:17`, `arena_inlines_b.h:61-76`), và
`opt_background_thread` mặc định false ⇒ **không có ai purge hộ**. Một tiến trình im lặng sau khi nạp mô hình
không bao giờ tới tick.

### 1.2 `M_PURGE` — thi hành, và một hệ quả thiết kế phải biết

`jemalloc_wrapper.cpp:105-124`:

```c
} else if (param == M_PURGE) {
  // Only clear the current thread cache since there is no easy way to
  // clear the caches of other threads.
  je_mallctl("thread.tcache.flush", ...);
  ...
  snprintf(buffer, sizeof(buffer), "arena.%u.purge", narenas);   // narenas = "tất cả arena"
```

⇒ **tcache chỉ xả cho luồng ĐANG GỌI**. Phần arena (chỗ chứa gần hết 100+ MB) purge toàn cục, nên gọi từ luồng nào
cũng ăn phần lớn; nhưng để ăn nốt tcache thì **gọi trên đúng luồng đã cấp phát**. Cả 4 mốc pha ở §4 đều thoả:
trim chạy trên luồng vừa nạp mô hình / luồng `KachiSpeak` của `:tts` / luồng main của `:wake`.

### 1.3 Vì sao nền tảng không tự làm hộ

`frameworks/base/core/java/android/app/ActivityThread.java:6016-6019` gọi `mallopt(M_PURGE)` khi trim memory —
**nhưng** chỉ khi sysprop `debug.am.run_mallopt_trim_level` được đặt (mặc định `Integer.MAX_VALUE` ⇒ tắt); đường
thứ hai là idler của một **Activity** (`:2083,2091`) — mà `:wake` và `:tts` **không có Activity nào**. Đường
sysprop + `am send-trim-memory` chỉ dùng để **chốt chẩn đoán** trên máy ảo, không phải cách ship (đã ghi ở
`ram-audit-2026-09-25.md` §1.5).

---

## 2. NDK + CMake: bản nào, cài thế nào

Máy dev **không có** `cmdline-tools`/`sdkmanager` (và không có `ndk/`, `cmake/` trong SDK) ⇒ tải thẳng zip chính
thức từ `dl.google.com` rồi bung vào đúng layout mà AGP tìm. Cả hai đều **kiểm SHA-1** khớp
`repository2-3.xml` của Google.

| Gói | Bản | Đường dẫn sau khi cài | SHA-1 zip (đã khớp) | Cỡ bung |
|---|---|---|---|---|
| NDK | **r30 = 30.0.16248370** (LTS) | `~/Library/Android/sdk/ndk/30.0.16248370/` | `c060be96767eefbb8e0a27796d6f43115fc1a0c4` | 2,8 GB |
| CMake | **3.31.6** (kèm `bin/ninja` 1.12.1) | `~/Library/Android/sdk/cmake/3.31.6/` | `78c3bf819d7bf6144783aeef06c5f9cbd3f8f5a9` | ~200 MB |

```bash
# NDK (929 MB zip) — bung rồi ĐỔI TÊN thư mục theo Pkg.Revision, AGP tìm theo tên đó
curl -L -o /tmp/ndk.zip https://dl.google.com/android/repository/android-ndk-r30-darwin.zip
shasum -a 1 /tmp/ndk.zip          # phải = c060be96767eefbb8e0a27796d6f43115fc1a0c4
unzip -q /tmp/ndk.zip -d /tmp/ndkx && mkdir -p ~/Library/Android/sdk/ndk
mv /tmp/ndkx/android-ndk-r30 ~/Library/Android/sdk/ndk/30.0.16248370
cat ~/Library/Android/sdk/ndk/30.0.16248370/source.properties   # Pkg.Revision = 30.0.16248370

# CMake của SDK (40 MB zip) — zip PHẲNG (bin/, share/, source.properties) ⇒ bung THẲNG vào thư mục phiên bản
curl -L -o /tmp/cmake.zip https://dl.google.com/android/repository/cmake-3.31.6-darwin.zip
unzip -q /tmp/cmake.zip -d ~/Library/Android/sdk/cmake/3.31.6 && chmod +x ~/Library/Android/sdk/cmake/3.31.6/bin/*
```

Ba điểm dễ mất thời gian, ghi lại để phiên sau không mò:

* **Vì sao r30 chứ không r27**: [ĐO `developer.android.com/ndk/downloads` lấy 2026-09-26, trang *Last updated
  2026-09-08*] trang ghi thẳng *"Latest LTS Version (r30)"* + `ndkVersion "30.0.16248370"`. r27 là LTS **cũ**. Rule
  global §5.2 (dự án phải sống 5+ năm) ⇒ lấy LTS hiện hành. (Trang Mac chỉ mời tải `.dmg` 1,07 GB; **repo SDK vẫn
  có `android-ndk-r30-darwin.zip`** — dùng zip, không cần mount dmg.)
* **Không cần `cmake.dir` trong `local.properties`**: đặt CMake vào `$SDK/cmake/<ver>` là AGP tự tìm theo
  `version` khai trong `externalNativeBuild`. Dùng Homebrew cmake thì lại phải khai `cmake.dir` **và** tự lo
  `ninja` cạnh nó — zip của SDK đã chở ninja nên rẻ hơn. `local.properties` của máy này **không đổi một dòng**
  (vẫn `sdk.dir=~/Library/Android/sdk`; tệp này nằm trong `.gitignore` qua `local.properties*`, chưa từng tracked).
* **Toolchain là universal binary**: `clang`, `cmake`, `ninja` đều có nhánh `arm64` ⇒ chạy native trên Apple
  Silicon, dù thư mục NDK vẫn mang tên `prebuilt/darwin-x86_64` (tên lịch sử, đừng đọc là "chỉ x86").

`.gitignore` đã có sẵn `.cxx/` (thư mục trung gian của CMake) và `build/` ⇒ **không cần sửa `.gitignore`**.

---

## 3. Thư viện + mặt Kotlin

```
app/src/main/cpp/kachimem.c          64 dòng C (≈20 dòng mã), không STL, không dependency
app/src/main/cpp/CMakeLists.txt      project(kachimem LANGUAGES C) · -Os -fvisibility=hidden -Wall -Wextra -Werror
app/src/main/java/com/byd/clusternav/launcher/perf/KachiMem.kt   mặt an toàn (object, 3 hàm công khai)
```

`app/build.gradle.kts` thêm đúng ba thứ (KHÔNG bump `versionCode`/`versionName` — việc đó thuộc lượt ship):
`ndkVersion = "30.0.16248370"`, khối `externalNativeBuild { cmake { path; version = "3.31.6" } }`, và một
`inputs.dir("src/main/cpp")` cho task test (xem §5.2). `abiFilters` **giữ nguyên chỉ `arm64-v8a`**.

[ĐO] `.so` đã dựng (llvm-readelf/llvm-nm của NDK r30):

```
Type: DYN (Shared object file) · Machine: AArch64
NEEDED  libc.so                                    ← đúng một dependency, KHÔNG có libc++_shared.so
T Java_com_byd_clusternav_launcher_perf_KachiMem_nativePurge
T Java_com_byd_clusternav_launcher_perf_KachiMem_nativeDecayNow    ← đúng 2 ký hiệu export
4 056 byte trong APK (Stored, lib/arm64-v8a/libkachimem.so)
```

Ba luật của `KachiMem` (viết trong KDoc, khoá bằng test):

1. **Không bao giờ giết bản release** — `System.loadLibrary` bọc `try/catch (UnsatisfiedLinkError|SecurityException)`
   **một lần** lúc khởi tạo `object`; thiếu `.so` ⇒ `available() == false` và mọi hàm là no-op trả `false`, **không
   chạm `Debug`/`Log`** (cổng `if (!loaded) return false` là dòng đầu tiên).
2. **Không bao giờ nằm trên đường nóng** — chỉ gọi ở mốc pha (§4).
3. **Chỉ đo, không quyết định** — không đọc pref, không hẹn giờ; nhờ vậy test được off-device.

---

## 4. Bốn mốc pha gọi trim (+ một lần đặt decay)

| # | Nơi gọi | Mốc | Vì sao đúng ở đây |
|---|---|---|---|
| — | `VoiceWakeService.onCreate` | `decayNow()` = `mallopt(M_DECAY_TIME,0)` | Một lần / đời tiến trình `:wake`. Từ đó **mọi** đường free tự madvise, kể cả đường không ai nhớ gọi trim. Kết quả in vào `logcat -s KachiWake`. |
| 1 | `VoiceRecognizer.kt` · `VoiceEngine.build(...)` nhánh `onSuccess` | nạp xong mô hình ASR | onnxruntime vừa nhả `ModelProto` (~71 MB encoder int8) sau khi dựng phiên; và đây là **đúng luồng** đã cấp phát. Phủ cả tiến trình chính (preload) lẫn `:wake`. |
| 2 | `VoiceRecognizer.kt` · `VoiceEngine.release()` (sau khi nhả khoá ghi) | nhả xong mô hình | `OfflineRecognizer.release()` free 85-110 MB mà jemalloc giữ lại ⇒ không có dòng này thì BG-20 (đứng xuống `:wake` khi wake TẮT) chỉ giảm số trong `mallinfo`, PSS đứng nguyên. |
| 3 | `VoiceWakeService.resumeTask` | phiên lệnh xong (hết lượt nhường micro, 18 s sau khi wake nổ) | Mốc thưa nhất mà vẫn phủ **mỗi** lượt wake, và nằm NGOÀI đường audio — không khung nào bị `madvise` chen vào. |
| 4 | `PiperTtsService.sendDone` (sau `to.send`) | `:tts` đọc xong một câu | Một câu = một lượt `generate` cấp phát rồi nhả PCM + buffer ORT. Đặt **sau** lời báo xong để không cộng vài ms vào độ trễ phía launcher; chạy trên luồng `KachiSpeak` = đúng luồng đã cấp phát. |

Không gọi ở `VoiceWakeAsr` / `VoiceCapture` / `VoiceWakeListener` — ba tệp trên đường audio; `KachiMemTest` khoá
điều đó bằng một bài riêng (một `trim` mỗi khúc 100 ms là đổi 100 MB RAM lấy một đường trễ mới).

### 4.1 Dòng log để chứng minh trên xe

```
logcat -s KachiMem:D
D KachiMem: purge(sau nạp mô hình zipformer-vi-int8-…) ok=true 12ms · RSS 268.4→171.9 MB · alloc 164.6→164.6 MB · size 173.8→173.8 MB
```

⚠ **`alloc`/`size` gần như KHÔNG đổi, và đó là ĐÚNG** — đừng đọc nó là "vá không ăn":
`Debug.getNativeHeapSize()` = `mallinfo().usmblks`, `getNativeHeapFreeSize()` = `fordblks`
(`frameworks/base/core/jni/android_os_Debug.cpp:157-174`), mà Android tính `usmblks = hblkhd` và
`fordblks = hblkhd - uordblks` với `hblkhd` = tổng `arena->stats.mapped`
(`external/jemalloc_new/src/android_je_mallinfo.c:59,66,67`). `M_PURGE` **madvise page**, không unmap extent ⇒
`mapped` không đổi. Thứ rơi là **RSS/PSS**. Vì vậy dòng log in cả **RSS** (`/proc/self/statm` cột 2 × `sysconf(_SC_PAGESIZE)`)
và số để kết luận là RSS.

Ghi chú đính chính cho `ram-audit-2026-09-25.md` §1.5: dòng *"free phải rơi về ~0"* sau `am send-trim-memory` là
[ĐOÁN] chưa kiểm — theo `android_je_mallinfo.c:66` thì `free` (= `mapped − allocated`) **không** rơi vì purge;
phép chốt đúng là so **PSS/RSS** trước/sau.

---

## 5. Kiểm off-car — số thật

Mọi số dưới đây đo trong một **worktree sạch tại HEAD `e4a85be` + đúng bản vá này** (cây làm việc chính đang có
việc dở của các agent khác ⇒ không dùng để đo cỡ APK).

### 5.1 APK

| | byte | ghi chú |
|---|---|---|
| Trước (HEAD `e4a85be`, 2.72/173) | **43 483 553** | `sha256 3762a2b7…`, trùng `apk/Kachi-2.72-release.apk` |
| Sau (cùng HEAD + CLOSE-4) | **43 500 011** | +**16 458 B** = +16,1 KiB = **+0,038 %** |

Trong đó `lib/arm64-v8a/libkachimem.so` = 4 056 B (Stored); phần còn lại ~12,4 KB là dex của `KachiMem` +
padding căn trang của zip. Bảng `lib/` sau khi vá — **chỉ arm64-v8a, không có `libc++_shared.so`**:

```
     4056  lib/arm64-v8a/libkachimem.so
 22249552  lib/arm64-v8a/libonnxruntime.so
  4771760  lib/arm64-v8a/libsherpa-onnx-jni.so
```

### 5.2 Test + lint

* `:app:testDebugUnitTest` — **1370 → 1377 (+7), 0 fail / 0 error**, kể cả lượt `--rerun-tasks`.
* `:app:lintRelease` — **BUILD SUCCESSFUL** (`abortOnError = true`, `checkReleaseBuilds = true` ⇒ 0 error). Không
  có một cảnh báo nào trỏ vào tệp mới (0 lần xuất hiện chữ `KachiMem`/`kachimem` trong báo cáo lint).
* `:app:assembleRelease` — BUILD SUCCESSFUL, task `configureCMakeRelWithDebInfo[arm64-v8a]` +
  `buildCMakeRelWithDebInfo[arm64-v8a]` chạy thật.

**Thử phá (mutation) — 2/2 ĐỎ đúng chỗ, không cần `--rerun-tasks`** (tức `inputs.dir("src/main/cpp")` có tác dụng
thật; nếu thiếu khai thì Gradle báo UP-TO-DATE và cho dấu xanh GIẢ — đúng bệnh mà KDoc khối `tasks.withType<Test>`
trong `app/build.gradle.kts` mô tả):

| Đổi | Bài đỏ |
|---|---|
| `mallopt(M_PURGE, 0)` → `mallopt(M_DECAY_TIME, 0)` trong `kachimem.c` | `KachiMemTest > cmake la C thuan khong STL` |
| Chuyển `Debug.getNativeHeapAllocatedSize()` lên TRƯỚC cổng `if (!loaded)` | `KachiMemTest > khong co lib thi trim la no-op tra false` |

Hai bài canh cũ phải sửa theo (cả hai là **mốc**, không phải hành vi):

* `VoiceWakeStandDownWiringContractTest.kt:115` — `SourceRoots.body(engine, "fun release() = synchronized(this) {")`
  ⇒ `"fun release(): Unit = synchronized(this) {"`. Lý do: `release()` thêm một dòng cuối trả `Boolean`, mà
  thân-biểu-thức Kotlin lấy giá trị câu lệnh CUỐI ⇒ chữ ký công khai sẽ **âm thầm** thành `Boolean`; khai `: Unit`
  chặn điều đó. `SourceRoots.body` NỔ khi mốc biến mất — đúng thiết kế, nên mốc phải sửa theo.
* `LauncherI18nContractTest.kt` — thêm `\bKachiMem\.trim\(` vào `DIAGNOSTIC_CALL`. Tham số duy nhất của `trim` là
  **nhãn mốc pha đi thẳng vào `Log.d`**, nên nó thuộc nhóm "chuỗi chẩn đoán nhận ra bằng CẤU TRÚC" y như `Log.*`,
  không phải 4 mục `allowed` cùng một lý do (doctrine đã ghi trong KDoc của chính tệp đó).

### 5.3 Không kiểm được off-car

Máy ảo `clusternav10` là **x86_64** ⇒ `libkachimem.so` arm64 **không nạp được ở đó** kể cả khi cài; và phiên này
không được cài gì lên máy ảo (agent khác đang sở hữu). Nghĩa là **đường `loaded == true` chưa từng chạy ở đâu** —
off-car chỉ chứng minh được đường `loaded == false` (no-op) + đúng ký hiệu trong `.so`. Việc `mallopt` trả `1`
và RSS rơi bao nhiêu là **[CHƯA BIẾT], phải đo trên xe**.

---

## 6. 🚗 Phép đo trên xe (làm đúng thứ tự này)

Xe chạy bản 2.72+ có CLOSE-4. Cài xong **đừng** kết luận gì trước khi có ba số dưới.

```bash
# 0) Bản nào đang chạy — không đoán (CLAUDE.md §9)
adb shell dumpsys package com.byd.launcher | grep versionName

# 1) lib có nạp được không (đường loaded == true đã chạy chưa)
adb logcat -d -s KachiWake | grep "M_DECAY_TIME"      # mong: "mallopt(M_DECAY_TIME,0) = true · lib=true"

# 2) Trước: PSS/native của :wake khi mô hình ĐÃ nạp mà chưa có phiên nào
adb shell dumpsys meminfo com.byd.launcher:wake | grep -E "Native Heap|TOTAL"

# 3) Một lượt thoại thật ("Hey Kachi" → một câu lệnh → nghe Kachi trả lời), đợi > 18 s (WAKE_HANDOFF_MS)
adb logcat -d -s KachiMem:D                            # bốn mốc pha, đọc cột RSS trước→sau

# 4) Sau: cùng lệnh như (2), so PSS. Chốt bằng scripts/emulator/perf-snapshot.sh nếu muốn cả CPU/luồng
adb shell dumpsys meminfo com.byd.launcher:wake | grep -E "Native Heap|TOTAL"
```

Tiêu chí đọc kết quả:

* **Ăn** = RSS trong dòng `purge(...)` rơi ≥ 20 MB ở ít nhất một mốc, **và** `Native Heap Pss` của `:wake` ở bước
  (4) thấp hơn bước (2). Mốc kỳ vọng theo audit: −100…−126 MB (máy ảo). Trên xe [ĐOÁN] thấp hơn, vì
  `after-oncar-2.69.md` cho thấy xe chỉ có 9,2 MB "free chưa trả" tại thời điểm chụp.
* **Không ăn mà vẫn `ok=true`** = phần lớn 164 MB `alloc` là bộ nhớ **đang dùng thật** (mô hình còn trong RAM) ⇒
  bài toán chuyển sang đề xuất (A)/(C) của audit (một mô hình cho cả máy · tự nhả sau phiên), không phải purge.
  Ghi số vào backlog rồi **dừng**, đừng thêm mốc trim nữa — thêm mốc chỉ thêm `madvise`.
* **`lib=false`** = APK không có `.so` (build sai) hoặc ROM chặn `dlopen`: đọc
  `adb shell run-as com.byd.launcher ls lib/` trước khi đoán bất cứ điều gì.

---

## 7. Việc còn để lại

* **[P3] `:tts` chưa đặt `M_DECAY_TIME 0`** — chỉ có trim ở mốc "đọc xong câu". Cố ý: `:tts` sinh/chết theo bind
  nên rác của nó tự về khi tiến trình tắt; đặt decay 0 ở đó là thêm `madvise` cho một tiến trình ngắn hạn. Xét lại
  **nếu** §6 cho thấy `:tts` giữ RAM qua nhiều câu.
* **Tiến trình chính** hưởng mốc 1 + 2 (cùng `VoiceRecognizer`) nhưng **không** đặt decay 0: nó có UI churn nên
  vẫn tới tick (audit [ĐO]: free chỉ 10,5 MB). Không đổi gì ở đó.
* **INDEX + BACKLOG + `project-context.md`** (CLAUDE.md §16 · R2.1) chưa cập nhật trong lượt này — hai tệp đó nằm
  ngoài phạm vi tệp được giao cho lượt việc này. Doc mồ côi = không tồn tại ⇒ **phải thêm dòng cho tệp này vào
  `docs/README.md` và đánh dấu CLOSE-4 trong `docs/PROJECT-BACKLOG.md` cùng phiên**.
