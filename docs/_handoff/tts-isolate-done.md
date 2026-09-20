# Stage #0 — CÔ LẬP PIPER RA TIẾN TRÌNH RIÊNG `:tts` — DONE off-car

> **Trạng thái**: ✅ off-car xanh, **CHƯA commit / CHƯA push** (main-agent gom + ship). · **Ngày**: 2026-09-18
> **Gốc**: `docs/diagnostics/oncar-piper-crash-binding-2026-09-18.md` (#0, chặn trên hết)
> **Đường (a)** của doc — Piper chạy tiến trình riêng — đã làm; đường (b) (đổi mặc định sang System TTS) **không** làm,
> mặc định giọng phản hồi **KHÔNG đổi** (vẫn Piper, `VoiceSpeakerSelector.FEEDBACK_PIPER`).

---

## 1. Vấn đề, nói bằng một câu

`OfflineTts.generate` **SIGSEGV** (SEGV_MAPERR) trên luồng `KachiSpeak` ⇒ **cả tiến trình launcher chết** ⇒
`NavAccessibilityService` unbind ⇒ rebind kẹt *"Binding"* ⇒ **phím gán chết** (owner phải force-stop + bật lại trợ
năng bằng tay). `runCatching { Throwable }` **không bắt được** — abort ở tầng native, không phải exception JVM ⇒
trong cùng tiến trình **không có bản vá nào khả thi**. Đường duy nhất còn lại là một **ranh giới tiến trình**.

Nay Piper nổ ⇒ chỉ tiến trình `:tts` chết; launcher thấy `onServiceDisconnected`, mở mọi chỗ đang chờ, và câu sau
tự nối lại. Binding phím · cast · a11y · nav **không bị đụng**.

---

## 2. Tệp tạo / đổi

### Tạo mới (3)
| Tệp | Vai | Dòng |
|---|---|---|
| `app/…/launcher/voice/PiperTtsService.kt` | Bound `Service` ở `:tts`; giữ **một** `SherpaTtsSpeaker(this)`; hộp thư `Messenger` trên luồng chính của `:tts` | 157 |
| `app/…/launcher/voice/RemotePiperSpeaker.kt` | `VoiceSpeaker` **ở launcher** — bind lười, gửi câu, mang `onDone` qua ranh giới tiến trình | 296 |
| `app/src/test/…/launcher/voice/VoiceTtsIsolationContractTest.kt` | 16 bài canh (xem §5) | 315 |

### Sửa (4)
| Tệp | Đổi gì |
|---|---|
| `app/…/launcher/voice/VoiceSpeakerRouter.kt` | `private val sherpa = SherpaTtsSpeaker(ctx)` → **`RemotePiperSpeaker(ctx)`**. `ClipSpeaker(ctx, fallback = sherpa)` · `android` · `clip` **không đổi** |
| `app/…/launcher/voice/SherpaTtsSpeaker.kt` | `private companion` → `companion` + **`voiceFilesPresent(ctx, voice = PIPER_VI_VAIS1000)`** và vế thuần `voiceFilesPresent(root: File, voice)`; `filesPresent()` nay uỷ quyền về đó (logic **y nguyên**: `model.isFile && tokens.isFile && dataDir.isDirectory`). Hằng cũ giữ nguyên giá trị, chuyển thành `private const`. + KDoc: lớp này nay chạy ở `:tts`; note SharedPreferences cross-process ở chỗ đọc `voice_tts_speed` |
| `app/src/main/AndroidManifest.xml` | `<service android:name=".launcher.voice.PiperTtsService" android:process=":tts" android:exported="false">` — đặt sau `VoiceWakeService`. KHÔNG `<property>`, KHÔNG `<intent-filter>` (bẫy packageinstaller Android 10) |
| `app/…/KachiApplication.kt` | **Cổng tiến trình** (xem §4 — sai lệch có chủ ý) |

**Không đụng**: `AndroidTtsSpeaker` · `ClipSpeaker` · `VoiceSpeakerSelector` · `VoiceSession` · `VoiceSpeaker`
(`:core`) · mặc định giọng phản hồi · mọi tệp của các stage khác.

---

## 3. IPC — hợp đồng đầy đủ

```
LAUNCHER (com.byd.launcher)                        :tts  (com.byd.launcher:tts)
─────────────────────────────                      ───────────────────────────────
VoiceSpeakerRouter
  └─ RemotePiperSpeaker : VoiceSpeaker
       available()  = SherpaTtsSpeaker.voiceFilesPresent(app) && !dead   ← ĐỌC ĐĨA, không IPC
       speak(text, onDone)
         ├─ bound?  ── MSG_SPEAK {id, text}, replyTo=inbox ──►  PiperTtsService.onSpeak
         │                                                        └─ sherpa.speak(text) { sendDone(reply, id) }
         │            ◄──────────── MSG_DONE {id} ─────────────────────┘
         └─ chưa bound? lưu `queued` + bindService(BIND_AUTO_CREATE)
                        onServiceConnected ⇒ gửi `queued` ngay
       stop()       ── MSG_STOP ──────────────────────────────►  sherpa.stop()
       shutdown()   ── unbindService ────────────────────────►  onDestroy ⇒ sherpa.shutdown()
```

* **Messenger, không AIDL** — cả hai chiều là *"một tin, không chờ tại chỗ"*; Messenger xếp hàng một luồng ⇒ không
  lời gọi nào chặn luồng gọi của launcher (ràng buộc số 1 của `VoiceSpeaker`).
* **Mã tin + khoá Bundle khai MỘT chỗ** ở `PiperTtsService.companion` (`MSG_SPEAK/MSG_STOP/MSG_DONE`, `KEY_ID`,
  `KEY_TEXT`, `PROCESS_SUFFIX`); `RemotePiperSpeaker` **tham chiếu**, không chép giá trị (bài canh khoá).
* **`id` = số thế hệ câu** (`generation`, đếm từ 1) — đi sang rồi quay về nguyên vẹn; `0` là *"không có id"*.
* **Chuỗi gửi đi đã qua `TtsPronunciation.normalise`** ở launcher (Router làm như trước) — `:tts` chỉ đọc.
* **`onDone` chạy trên luồng CHÍNH của launcher** (hộp thư đặt trên main looper) — hợp đồng cho phép luồng bất kỳ,
  nhưng về đúng luồng vẽ là bớt một lượt `post` có thể quên.

### ⚠ Hợp đồng chịu lực: `onDone` **đúng một lần**, mọi đường

| Đường | Ai đóng sổ |
|---|---|
| đọc hết · bị cắt ở `:tts` · tổng hợp ném · câu lỗi thời | `MSG_DONE` về ⇒ `settle(id)` |
| `text` rỗng / gói chưa lắp / đã `shutdown` | `speakInternal` gọi ngay, trả `false` |
| `bindService` trả `false` / ném | `speakInternal` ⇒ `settle(my)` + **`unbindService`** (xem §6), trả `false` |
| `Messenger.send` ném (`RemoteException`) | `onRemoteGone()` ⇒ đóng **hết** |
| câu đang chờ nối bị **đè** bởi câu mới | `superseded` ⇒ `fire` (ngoài khoá) |
| **`:tts` SIGSEGV** / LMK dọn / `onBindingDied` / `onNullBinding` | `onRemoteGone()` ⇒ đóng **hết** + unbind ⇒ câu sau bind lại |
| launcher `stop()` (Router gọi trước MỖI câu) | đóng hết — câu bị cắt = coi như đọc xong |
| `shutdown()` | `stop()` rồi unbind |

Nguyên tắc: **phân vân thì gọi `onDone`**. Gọi thừa vô hại (`pending.remove` một lần); gọi thiếu = `VoiceSession`
treo im lặng (overlay không tắt, cổng xác nhận không mở micro).

**Khoá**: một `lock` cho 4 mảnh (`remote` · `bound` · `queued` · `pending`) vì chúng đổi cùng nhau (`speak` tới từ
luồng vẽ **hoặc** luồng `KachiClip` của `ClipSpeaker`; `ServiceConnection` ở luồng chính). Trong khoá **chỉ** gán +
`HashMap` — không `bindService`, không `send`, không chạy `onDone` (đường tới deadlock).

---

## 4. Sai lệch có chủ ý so với spec đặt hàng (1 mục, cần main-agent biết)

**Thêm cổng tiến trình vào `KachiApplication.onCreate`** — spec liệt kê 5 hạng mục, đây là hạng mục thứ 6.

Lý do: `Application.onCreate` chạy ở **mọi** tiến trình. Không có cổng này thì `:tts` cũng gọi
`VoiceEngine.preload` (mô hình NGHE **74 MB** int8) + `VoiceVad.preload` — vừa vô ích (nó chỉ ĐỌC, không nghe) vừa
**đúng thứ gây ra lỗi đang vá**: SIGSEGV là `SEGV_MAPERR` dưới áp lực RAM ([ĐO] xe chạy GMaps 61 % + VietMap +
cdr). Cô lập tiến trình mà nhân đôi RAM là cô lập hỏng.

```kotlin
if (isTtsProcess()) return          // TRƯỚC AppContainer.get + 2 lượt preload
…
private fun isTtsProcess(): Boolean =
    runCatching { Application.getProcessName() }.getOrNull()?.endsWith(PiperTtsService.PROCESS_SUFFIX) == true
```

⚠ **[ĐO]** `android.jar` của compileSdk 37 khai `Application.getProcessName()` là **static** (API 28) và **không**
phơi `Context.getProcessName()` ⇒ `this.processName` **không biên dịch** (*"Unresolved reference 'processName'"* —
lượt đầu đỏ đúng thế). Phải gọi qua tên lớp. minSdk 29 ⇒ luôn có.

---

## 5. Test

**Mới**: `VoiceTtsIsolationContractTest` (`:app`) — **16 ca**, [ĐO] 16/16 xanh.

*Quét cấu hình + dây nối (không chạy được trong JVM: `android:process` do nền tảng thi hành, `bindService`/`Messenger`
cần Android thật, dự án không có Robolectric)*
1. `duong Piper cua Router di qua tien trinh rieng` — Router cầm `RemotePiperSpeaker`, **không** `SherpaTtsSpeaker`; `ClipSpeaker` vẫn lùi về chính đường đó.
2. `chi PiperTtsService duoc dung SherpaTtsSpeaker` — quét **cả 3 module**, chỉ đúng một tệp được dựng máy đọc.
3. `manifest khai PiperTtsService o tien trinh tts va khong exported` — cắt đúng khối `<service>` của nó; đòi `android:process=":tts"` + `exported="false"`; **cấm** `<property>` / `<intent-filter>`.
4. `hau to tien trinh khai mot cho va duoc dung that` — `PROCESS_SUFFIX` khai một chỗ, `KachiApplication` dùng chính nó.
5. `tien trinh tts khong nap mo hinh nghe` — cổng `isTtsProcess()` phải nằm **TRƯỚC** `AppContainer.get` + 2 lượt `preload` (so vị trí, không chỉ so tồn tại).
6–10. Hợp đồng `onDone`: 3 callback mất-kết-nối ⇒ `onRemoteGone`; `onRemoteGone` dọn sổ **và gọi thật** từng việc chờ + unbind + `bound = false`; bind hỏng ⇒ `settle` + `false`; câu bị đè ⇒ `fire`; `stop` ⇒ `MSG_STOP` + mở sổ.
11–12. Phía `:tts`: `sherpa.speak(text) { sendDone(reply, id) }` + **đúng 2** chỗ `sendDone`; `onDestroy` ⇒ `shutdown`; hai đầu dùng cùng bộ mã tin.
13. Trần 500 dòng cho 2 tệp mới.

*Chạy hành vi THẬT off-car (thư mục tạm) — `voiceFilesPresent`*
14. đủ model + tokens + **thư mục** `espeak-ng-data` ⇒ `true`.
15. thiếu bất kỳ thứ nào (3 ca) ⇒ `false`.
16. thư mục rỗng / chưa tồn tại ⇒ `false`, **không ném**; `dataDir` là **tệp** thay vì thư mục ⇒ `false`.

**Thử phá (chứng minh bài canh không mù)** — 3 phép, đỏ đúng chỗ cả 3, rồi hoàn nguyên **bằng bản sao tệp**
(không `git checkout` — luật của dự án với cây chưa commit) và **so sha256 khớp từng byte**:

| Mutation | Kết quả [ĐO] |
|---|---|
| gỡ `android:process=":tts"` khỏi manifest | `manifest khai PiperTtsService…` FAILED (1/16) |
| Router về `SherpaTtsSpeaker(ctx)` | `duong Piper…` + `chi PiperTtsService…` FAILED (2/16) |
| gỡ `if (isTtsProcess()) return` | `tien trinh tts khong nap mo hinh nghe` FAILED (1/16) |

**Guard KHÔNG bị nới**: `LauncherI18nContractTest` đỏ ở lượt đầu với **5 chuỗi** (`"binder rỗng"`, `"tiến trình :tts
chết"`, …) mà tôi truyền làm **tham số** `onRemoteGone(why)` — bài đó nhận chuỗi chẩn đoán bằng **cấu trúc**
(`Log.*` · `throw` · `require` · `Lang.t`), nên tham số hàm không phân biệt được với chữ trên màn. **Không** thêm
mục vào `allowed`; thay vào đó dời câu nhật ký **vào chính lời gọi `Log.w`** ở từng chỗ và bỏ tham số `why`. Kết
quả: 0 dòng test bị sửa, và mỗi chỗ còn nói được nguyên nhân riêng ngay khi xảy ra.

### [ĐO] Verify cuối

```
JAVA_HOME=/opt/homebrew/opt/openjdk@17 GRADLE_OPTS=-Xmx3g \
  ./gradlew :app:assembleDebug :app:assembleRelease test --rerun-tasks --continue
⇒ BUILD SUCCESSFUL in 1m 17s
```

| module | tests | fail |
|---|---|---|
| `app:testDebugUnitTest` | 1164 | 0 |
| `app:testVehicleTestUnitTest` | 1176 | 0 |
| `core:test` | 2274 | 0 |
| `car-integration:test` | 61 | 0 |
| `offcar-planner:test` | 99 | 0 |
| `vehicle-contracts:test` | 22 | 0 |
| **TỔNG** | **4796** | **0** |

⚠ Số trên **gồm cả** phần việc của các stage khác đã có sẵn trong cây (`VoiceVadTrim` · `VoiceOverlay` ·
`VoiceYoutubeResolver` · `VoiceSession` · parser…) — tôi không đụng tệp nào của họ.

`:app:assembleRelease` xanh ⇒ **lint `abortOnError = true` cũng qua** (không có `NewApi` nào từ
`onBindingDied`/`onNullBinding`/`getProcessName` — cả ba ≤ API 28, minSdk 29).

**[ĐO] manifest ĐÃ TRỘN** (bằng chứng cô lập tồn tại ở tầng APK, không chỉ trong nguồn) —
`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`:

```xml
<service android:name="com.byd.clusternav.launcher.voice.PiperTtsService"
         android:exported="false" android:process=":tts" />
```
…và đó là **`android:process` DUY NHẤT** trong cả manifest đã trộn (grep: 1 kết quả).

---

## 6. Rủi ro còn lại / chưa đo được

**🚗 Cần xe (không kiểm được off-car)**
1. **Đường nóng của cả bản vá chưa từng chạy**: `:tts` crash → `onServiceDisconnected` → mở sổ chờ → câu sau bind
   lại. Off-car không có cách nào bắn SIGSEGV vào một tiến trình Android thật. **Phép thử trên xe**: `adb shell am
   force-stop` không dùng được (giết cả app) ⇒ dùng `adb shell kill <pid của com.byd.launcher:tts>` **giữa lúc
   Kachi đang đọc một câu** rồi kiểm 3 điều: (a) launcher **còn sống** (`pidof com.byd.launcher` không đổi);
   (b) overlay **tắt** (không treo) ⇒ `onDone` đã về; (c) nói lệnh tiếp theo ⇒ **vẫn đọc được** (đã bind lại).
2. **Phím gán sống sót qua một lần Piper nổ** — mục đích cuối cùng của #0. Kiểm `dumpsys accessibility` →
   `NavAccessibilityService` vẫn **Bound** sau khi `:tts` chết.
3. **RAM/LMK**: `:tts` là tiến trình của một service **đang bind** nên thừa hưởng độ quan trọng của launcher ⇒
   [SUY] không bị dọn giữa câu. Chưa đo dưới load 14 thật. Nếu nó **bị** dọn giữa câu thì biểu hiện là câu cụt +
   một dòng `tiến trình :tts chết` trong logcat — **không** phải treo (sổ chờ vẫn được mở).
4. **Độ trễ câu ĐẦU** tăng thêm một lượt fork tiến trình (engine 61 MB vốn đã nạp lười ở câu đầu từ trước, nên
   phần thêm chỉ là fork + bind). [CHƯA BIẾT] bao nhiêu ms trên TRINKET dưới load 14.
5. **Tổng RAM app tăng** (2 tiến trình, mỗi tiến trình một bộ Zygote + lớp Kotlin). Cổng `isTtsProcess()` đã cắt
   phần đắt nhất (74 MB mô hình NGHE + VAD) nhưng phần khung vẫn là chi phí mới.

**Đã biết, chấp nhận có chủ ý**
6. **`voice_tts_speed` trễ một vòng đời `:tts`**: `SharedPreferences` là bộ đệm **riêng của mỗi tiến trình**, nên
   núm chỉnh ở launcher chỉ ăn sau khi `:tts` dựng lại (unbind → bind, hoặc mở lại app). **KHÔNG** thêm
   `MODE_MULTI_PROCESS` (deprecated + không tin được). Đã ghi note tại chỗ đọc trong `SherpaTtsSpeaker`.
   Nếu owner thấy khó chịu: đường sạch là **gửi speed trong `MSG_SPEAK`** (một trường Bundle nữa) — chưa làm vì
   spec chốt *"`SherpaTtsSpeaker` tự đọc Prefs trong `:tts`"*.
7. **`bindService` trả `false` vẫn phải `unbindService`** (tài liệu Android) — đã xử lý; bỏ bước đó là rò một
   `ServiceConnection` mỗi lần bind hỏng, và lượt bind sau nổ `IllegalArgumentException: Service not registered` ở
   một chỗ hoàn toàn khác. Tìm ra khi tự soát lại, **sau** khi bộ test đã xanh ⇒ không bài canh nào bắt được ca này
   off-car.
8. **Không dựa vào lượt tự-nối-lại của `BIND_AUTO_CREATE`** (nền tảng có thể tự gọi lại `onServiceConnected` khi
   service khởi động lại): chủ ý unbind rồi để câu sau bind lại — một đường duy nhất, đo được. `onBindingDied` thì
   bắt buộc phải unbind mới bind lại được.

**Ngoài phạm vi stage này (doc #0 có nêu, chưa làm)**
9. **Watchdog binding**: `NavAccessibilityService` enabled-mà-không-Bound quá N giây ⇒ force-stop + re-enable
   (đường đã proven recover on-car). #0 chặn **nguyên nhân** phổ biến nhất; watchdog là lưới an toàn cho mọi
   nguyên nhân khác (LMK, crash của thành phần khác). Vẫn nên làm.
10. **Finding B** (`SPEAK_SAFETY_MS` theo độ dài câu) do stage batch-iso xử; #0 xoá phần *"process chết giữa synth"*
    của nó nhưng **không** xoá phần *"synth chậm > 10 s dưới load 14"*.

**Dọn dẹp cho main-agent (không phải của tôi)**
11. Cây có 2 tệp test tạm của worker khác: `core/src/test/…/voice/ZzScratchProbeTest.kt` +
    `ZzScratchProbe2Test.kt` — **không nên ship**, kiểm/gỡ trước khi commit.

---

## 7. Không làm

* **KHÔNG commit, KHÔNG push** (main-agent gom + ship).
* **KHÔNG** đổi mặc định nào: giọng phản hồi vẫn Piper, `voice_speak_replies` · `voice_ask_aloud` ·
  `voice_tts_speed` · `voice_feedback_voice` y nguyên.
* **KHÔNG** nới/gỡ một guard nào (xem §5 — `LauncherI18nContractTest` `allowed` **không** thêm mục).
* **KHÔNG** bump `versionCode`/`versionName` (main-agent làm khi ship 1.79/vc80).
