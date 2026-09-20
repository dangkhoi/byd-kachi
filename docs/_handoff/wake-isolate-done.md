# Handoff — "Hey Kachi" ra tiến trình `:wake` + đường tải OTA model KWS + nhãn thử nghiệm

> **Trạng thái**: DONE off-car · **Ngày**: 2026-09-19 · **Mục đích**: giao cho main-agent verify + host + ship.
> **CHƯA COMMIT** (theo yêu cầu). Cây làm việc có 10 tệp sửa + 3 tệp mới (danh sách ở §5).
> Spec đã ghi nhật ký: `docs/specs/kachi-wake-word.html` §9 (mục 2026-09-19).

---

## 1. TRẠNG THÁI — 5 dòng

| # | Việc | Trạng thái | Bằng chứng |
|---|---|---|---|
| A | `VoiceWakeService` → tiến trình `:wake` (crash KWS native không giết launcher) | **DONE** | manifest ĐÃ MERGE của bản debug mang `android:process=":wake"`; `VoiceWakeIsolationContractTest` 12/12 |
| A4 | Nhường mic cho phiên lệnh theo THỜI GIAN (`WAKE_HANDOFF_MS = 18_000`) | **DONE** | 3 bài canh thứ tự + hẹn giờ + huỷ ở `onDestroy` |
| B | Đường tải OTA model KWS (5 tệp ghim, 5,0 MB) | **DONE** | `WakeModelCatalogTest` 13/13; 2 bug thật đã vá (§3) |
| C | Nhãn thử nghiệm VI/EN | **DONE** | bài canh đọc thẳng `strings_kachi.xml` + `values-en` |
| — | Mặc định TẮT (opt-in) | **KHÔNG ĐỔI** | `Prefs.wakeEnabled` vẫn `getBoolean("voice_wake_enabled", false)` |

## 2. [ĐO] — lệnh tái lập được

```
JAVA_HOME=/opt/homebrew/opt/openjdk@17 GRADLE_OPTS=-Xmx3g ./gradlew test --rerun-tasks --continue
JAVA_HOME=/opt/homebrew/opt/openjdk@17 GRADLE_OPTS=-Xmx3g ./gradlew :app:assembleDebug
```

* **5 module: 4907 test / 0 đỏ / 0 lỗi** (`--rerun-tasks`, đếm từ JUnit XML).
  `core 2315` · `app debug 1199` · `app vehicleTest 1211` · `car-integration 61` · `offcar-planner 99` · `vehicle-contracts 22`.
  Mốc trước (1.80) là 4870 ⇒ **+37** = 13 (core) + 12×2 (hai biến thể app). Khớp chính xác.
* `:app:assembleDebug` **BUILD SUCCESSFUL**, 0 error.
* ⚠ **Lệch lệnh yêu cầu**: `:app:testReleaseUnitTest` **không tồn tại** trong dự án này
  (`Cannot locate tasks that match ':app:testReleaseUnitTest'`). Hai task thật là `:app:testDebugUnitTest`
  và `:app:testVehicleTestUnitTest` — `./gradlew test` chạy cả hai, nên phép đo ở trên phủ nhiều hơn yêu cầu.

**Thử phá 4 phép — đỏ đúng chỗ cả 4, restore byte-identical (`shasum -c` OK):**

| Phép phá | Bài đỏ |
|---|---|
| Bỏ `android:process=":wake"` khỏi manifest | `manifest khai VoiceWakeService o tien trinh wake` |
| `fireWake` nhả mic SAU `startActivity` | `fireWake nha mic truoc roi moi mo phien lenh` |
| `totalBytes` lệch 2 byte | `totalBytes ghim tuong minh khop tong cua bang` |
| Khôi phục công thức `staging` cũ | `thu muc dung do khong nam trong thu muc goi` — *expected: `<.staging>` but was: `<kws/.staging>`* |

## 3. CÂU HỎI CỦA OWNER: **A3 có vướng `AppContainer` không?** → **KHÔNG.**

[ĐO grep] cả ba tệp của bộ nghe — `VoiceWakeService.kt` · `VoiceWakeListener.kt` · `VoiceWakeKws.kt` — có
**0** lần xuất hiện `AppContainer`, `ShellTransport`, `WindowCommandDispatcher`, `NavRepository`,
`SimpleCastRuntime`. Import của chúng chỉ gồm `android.*`, `java.*`, `Prefs`, `R`, `EXTRA_START_VOICE`,
`KachiHomeActivity` (chỉ dùng làm **class literal** trong `Intent` — `companion object` của activity đó chỉ
giữ hai hằng `Int`, không có side-effect lúc nạp lớp) và các lớp voice thuần.

⇒ Cổng `if (isBackgroundVoiceProcess()) return` là **đúng về ngữ nghĩa**, không chỉ là tiết kiệm RAM: nếu một
tệp wake có chạm `AppContainer`, tiến trình `:wake` sẽ dựng **đồ thị DI thứ hai** (một `ShellTransport` thứ hai
mở thêm một kết nối dadb `localhost:5555` — đúng nghi phạm của BUG2 *"cài Kachi thì adb wireless chết"*), hoặc
ngã vì container chưa khởi tạo trong tiến trình đó. Điều này nay **khoá bằng bài canh**
(`ba tep wake khong cham do thi DI cua launcher`), nên nó không thể trôi.

## 4. HAI BUG THẬT phát hiện khi nối (không phải khi đọc spec) — đã vá

1. **Gói KWS sẽ KHÔNG BAO GIỜ cài được** nếu chỉ làm đúng chữ của spec.
   `VoiceModelStore.staging` dùng `dir.substringBeforeLast('/')`. Kotlin trả **chính chuỗi đó** khi không có dấu
   ngăn ⇒ với `dir = "kws"` (phẳng, vì `VoiceWakeKws` đọc tệp phẳng) thư mục dựng dở thành `kws/.staging`, tức
   **nằm TRONG đích**. Bước cuối của `install` làm đúng thứ nó phải làm — `dest.deleteRecursively()` để đổi tên
   vào chỗ sạch — và **tự xoá luôn thư mục vừa tải xong**; `renameTo` thất bại, người dùng nhận câu *"không
   chuyển được thư mục gói"* (trỏ sai chỗ hoàn toàn).
   **Vá**: luật đường dẫn tách sang `:core VoicePackPaths.stagingDir` (thuần ⇒ kiểm off-car). Hai gói cũ
   (`sherpa/<id>`, `sherpa-tts/<id>`) đi nhánh `else` ⇒ **không đổi một byte**, có bài canh khoá cả hai chiều.

2. **`sync()` một mình KHÔNG nạp được model vừa tải.**
   `ensureListener()` cố ý không dựng lại khi luồng còn chạy, và luồng chỉ thử nạp model **một lần** cho cả vòng
   đời (`kwsTried` — thiếu model là trạng thái bền). Nên gói tải xong chỉ có tác dụng **sau lần nổ máy sau**.
   **Vá**: `sync(ctx, reloadModel = false)` + `EXTRA_RELOAD`; `onStartCommand` thấy cờ thì `stopListening()`
   trước `ensureListener()`. Tham số có giá trị mặc định ⇒ hai chỗ gọi cũ (`SettingsSections` qua cầu,
   `KachiAutostart`) không phải sửa.

Ngoài ra `VoiceWakeListener.defaultKws` nay lấy **thư mục + 5 tên tệp từ chính `WakeModelCatalog`** thay vì chép
chuỗi lần thứ hai — chép là mở đúng cái khe im lặng: gói về đủ 5 tệp, engine soi một tên khác, `build` trả
`null`, "Hey Kachi" chạy mà **không bao giờ nhận**, không log nào nói vì sao.

## 5. TỆP ĐÃ ĐỤNG (13)

**Sửa (10)**
```
app/src/main/AndroidManifest.xml                                    A1  +android:process=":wake"
app/src/main/java/com/byd/clusternav/KachiApplication.kt            A3  isTtsProcess → isBackgroundVoiceProcess
app/src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeService.kt   A2+A4  PROCESS_SUFFIX · WAKE_HANDOFF_MS · resumeTask · EXTRA_RELOAD · sync(reloadModel)
app/src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeListener.kt  B   defaultKws đọc từ WakeModelCatalog
app/src/main/java/com/byd/clusternav/launcher/voice/VoiceModelStore.kt   B   staging → VoicePackPaths (vá bug §4.1)
app/src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeWake.kt    B2  tải model trên luồng nền + reload
core/src/main/kotlin/com/byd/clusternav/launcher/voice/VoicePack.kt      B   +object VoicePackPaths (luật thuần)
app/src/main/res/values/strings_kachi.xml                           C   nhãn + phụ đề "thử nghiệm"
app/src/main/res/values-en/strings_kachi.xml                        C   "(experimental)" + "report problems"
app/src/test/.../voice/VoiceTtsIsolationContractTest.kt             theo tên cổng mới
```
**Mới (3)**
```
core/src/main/kotlin/com/byd/clusternav/launcher/voice/WakeModelCatalog.kt      104 dòng
core/src/test/kotlin/com/byd/clusternav/launcher/voice/WakeModelCatalogTest.kt   13 ca
app/src/test/java/com/byd/clusternav/launcher/voice/VoiceWakeIsolationContractTest.kt  12 ca
```
Trần 500 dòng: mọi tệp đụng tới đều dưới trần (có bài canh). `strings.xml` **niêm phong T11 không bị đụng**
(có bài canh chặn). `docs/specs/kachi-wake-word.html` §9 thêm một mục nhật ký.

## 6. VIỆC CỦA MAIN-AGENT

1. **Host 5 tệp `voice/kws/`** lên `main` của `dangkhoi/byd-kachi` đúng đường dẫn `voice/kws/<tên phẳng>`.
   URL mà app sẽ gọi: `https://raw.githubusercontent.com/dangkhoi/byd-kachi/main/voice/kws/<tên>`.
   Số ghim phải khớp **chính xác** (nếu re-encode/LFS đổi byte là app từ chối tải, đúng thiết kế):

   | tệp | bytes | sha256 |
   |---|---|---|
   | `encoder.int8.onnx` | 4 807 159 | `1e721676515bcd42a186979733981213c66c80db680e1cc582dfedf3be76e678` |
   | `decoder.int8.onnx` | 277 985 | `e40ff43297abe815e8898494c17e71bba2152d9d40fa3eb803f75d0f7533329a` |
   | `joiner.int8.onnx` | 163 380 | `eae9da0c7e1e6c6a3f4cc42d167899c388f6c6701b94cb96320e4f55df79624c` |
   | `tokens.txt` | 5 006 | `fd2ded4050a55d2b1578870ba8697d02371980217806b7558bd0a5cc60f3ba53` |
   | `keywords.txt` | 252 | `992decc8a440bcf56dd8bbac2b6ea738f3751cdab82daacd765f88afdaae7850` |

   Tổng **5 253 782 B**. ⚠ `voice/kws/` hiện là **untracked** trong cây (`git status` = `?? voice/kws/`).
2. **Bump `versionCode`/`versionName`** trước khi giao APK cho anh em (CLAUDE.md §9) — lượt này **chưa bump**.
3. **Security scan trước commit** (rule global §6) — chưa chạy, và tôi không commit.
4. Cập nhật `project-context.md` + `PROJECT-BACKLOG.md` khi chốt số hiệu bản.

## 7. 🚗 CHƯA ĐO TRÊN XE — ghi ra, không giấu

1. **Đường nóng của cả lượt này chưa từng chạy**: `kill <pid :wake>` giữa lúc đang nghe ⇒ launcher + phím gán +
   màn hình phải sống nguyên (đúng phép đo đã dùng cho `:tts` ở 1.79).
2. **`WAKE_HANDOFF_MS = 18 s`** là con số **[SUY]** (trần nghe 8 s + một lượt hỏi-lại + câu đọc + lề). Chốt bằng
   một lượt nói hai câu liên tiếp trên xe.
3. **RỦI RO ĐÁNG CHÚ Ý NHẤT — nút mic bấm tay trong lúc `:wake` đang giữ mic.** `VoiceMicPreempt` (bắt tay
   *yield* thêm ở 1.77) là **in-process**, nên nó **không còn bắc qua** ranh giới `:wake`. `fireWake` đã xử ca
   wake-nổ (nhả mic trước), nhưng ca **người lái tự bấm nút mic** thì `:wake` vẫn đang giữ `AudioRecord`.
   **[CHƯA BIẾT]** ROM xử hai tiến trình **CÙNG UID** cùng thu ra sao (từ chối `AudioRecord`, hay trả khung im
   lặng). Nếu on-car thấy "bấm mic không nghe được gì khi Hey Kachi đang bật": đường vá nhỏ nhất là để
   `VoiceCapture` bắn một lệnh "tạm nghỉ" sang `:wake` qua **chính `sync()` đã có** (thêm một extra, không thêm
   IPC mới). Cố ý **không** làm trong lượt này — spec chốt "decoupled, không IPC", và mở IPC là đổi kiến trúc.
4. **Cầu chì false-accept ghi pref trong `:wake`** ⇒ bộ nhớ đệm `SharedPreferences` của tiến trình launcher
   không thấy ⇒ công tắc ở Cài đặt có thể còn hiện **BẬT** sau khi cầu chì đã tắt wake. Tự đúng lại ở lần nổ máy
   sau; gạt tắt–bật là khôi phục. Đây là hệ quả **mới** của việc tách tiến trình (trước 1.80 cùng tiến trình nên
   ghi là thấy) — không vá trong lượt này vì mọi cách vá đều cần một kênh IPC thứ hai.
5. FGS type `microphone` trên packageinstaller DL3 (API 29) vẫn là **[SUY]** như 1.77 — nay có thêm
   `android:process` trong cùng khối `<service>`. Khối vẫn **không có `<property>`/`<intent-filter>`** (bẫy
   "Fail in installation" [ĐO xe 2026-09-15]), có bài canh giữ.
6. Model KWS là gói **tiếng Anh** (gigaspeech) ⇒ tỉ lệ nhận với giọng Việt nói "Hey Kachi" là **[CHƯA BIẾT]**.
   Đây đúng là thứ lượt crowd-test này đi đo, và cũng là lý do nhãn nay tự nói "có thể chưa nhận tốt — báo lại".
