# RE Kiki Car (Zalo AI) — học kiến trúc voice command cho Kachi · 2026-09-14

> **Trạng thái**: Current · **Mục đích**: RE app trợ lý giọng nói tiếng Việt **Kiki Car** để định hướng mục **V1**
> (điều khiển bằng giọng nói cho toàn bộ chức năng launcher). Owner 2026-09-14: *"Xem kiki đi, nó là voice command
> đấy, làm được một số việc, mình có thể học để làm voice command NLP cho launcher của mình"*.
>
> **Phạm vi**: nghiên cứu **kiến trúc + cơ chế tương tác**. **KHÔNG chép mã nguồn Kiki** vào repo — chỉ ghi tên lớp /
> chuỗi / tên intent / tên asset làm bằng chứng. Không ghi token/khoá/URL kèm tham số (xem §6.3).
>
> **Nguồn**: `../apks/kiki/` (`base.apk` 33 MB · `split_config.arm64_v8a.apk` 21 MB · split `en`/`hdpi`),
> package `ai.zalo.kiki.car` **versionName 26.08.01.01** (versionCode 26080101, build tag
> `Kiki-26.08.01.01_STOREProductionRelease`), decompile jadx `--deobf` tại `../jadx-kiki/`.
> Vì `--deobf` nên tên lớp bị đổi — mọi quy kết dưới đây bám vào **chuỗi ký tự / manifest / tên gói gốc còn sót**.
>
> **Mức bằng chứng** (CLAUDE.md §2): **[ĐO]** = thấy trực tiếp trong mã/manifest/asset · **[SUY]** = suy luận khớp
> hiện tượng · **[CHƯA BIẾT]** = chưa có dữ liệu.
>
> **Bối cảnh xe**: [ĐO on-car 2026-08-14] trên xe owner (Seal DiLink 3.0, Android 10, không root), giữ **nút mic
> vô-lăng (keycode 328)** → mở đúng Kiki, *"chạy ngon"* — xem `oncar-handoff-voicekey-2026-08-14.md`. Kachi đã có
> sẵn đường này: `Prefs.VK_TARGET_DEFAULT = "ai.zalo.kiki.car"`, `Prefs.VK_KEYCODE_DEFAULT = 328`.

---

## 0. TL;DR — 6 kết luận đổi hướng

1. **Kiki KHÔNG nhận dạng tại máy.** Tại máy chỉ có **wake word** + **VAD**. Toàn bộ ASR + NLU + TTS đều **trên
   mây**. Bằng chứng đóng đinh: `assets/tokens.txt` **dài đúng 19 byte**, nội dung là `<blk> 0 / i 1 / k 2 / o 3` —
   bộ ký tự 4 token, chỉ đủ đánh vần *"kiki oi"*. [ĐO]
2. **Kiki KHÔNG điều khiển xe BYD, và không có đường nào để làm được.** `grep -niw "byd|dilink"` trên toàn bộ
   `ai/zalo` = **0 hit**; không `android.car`, không `CarPropertyManager`. Điều hoà/kính/ghế của Kiki đi qua **AIDL
   của đầu máy Trung Quốc đời TS/Zestech** (`com.ts.tsspeechlib.car.TsCarService`) hoặc **khung CAN thô**
   (`ICarInfoService.write(byte[])`). Đầu BYD không có các service đó. [ĐO]
3. **Kachi KHÔNG dùng được Kiki làm front-end ASR/NLU.** Bề mặt public của Kiki **không có đường trả kết quả về**:
   không `RecognitionService`, không `VoiceInteractionService`, binder autowake chỉ nhận **tên gói** rồi mở UI. Gọi
   Kiki = "mở Kiki ra cho nó tự nghe tự làm", không phải "nhờ Kiki nghe hộ rồi đưa chữ cho mình". [ĐO]
4. **Nhưng có MỘT đường một chiều đáng giá**: Activity `CarMainActivity` **exported**, đọc extra **`text_command`**,
   và chuỗi đó chạy thẳng vào `startAssistantListen(micSource, ctx, logV2, **textCommand**, pluginController)` —
   tức **app ngoài đẩy được một câu lệnh CHỮ cho Kiki thi hành, bỏ qua ASR**. [ĐO chữ ký hàm] / hiệu lực thật
   **[SUY]**, chốt bằng đúng 1 lệnh trên xe (§8.2).
5. **Kiki là hàng BÁN, không phải hạ tầng miễn phí.** `assets/freemium_config.json`: `basic_trial` / `basic` /
   `premium`, mã kích hoạt 19 và 29 ký tự *"mua tại các đại lý màn hình"*, hết hạn thì câm. Cảnh báo giao thông +
   Điểm báo GenAI là tính năng **khoá sau Premium**. Ngoài ra cần **đăng nhập Zalo** (có chế độ khách hạn chế) và
   **bắt buộc có mạng**. [ĐO]
6. **⇒ Hướng V1 hiện tại (ASR tại máy + ngữ pháp sinh từ danh mục) là ĐÚNG — giữ nguyên trục chính**, nhưng RE này
   bổ sung ba điều chỉnh có bằng chứng: (a) **chia đôi việc** với Kiki thay vì cạnh tranh; (b) đổi hình dạng bộ nhận
   dạng từ "ASR đầy đủ" sang **"từ khoá/ngữ pháp đóng"** theo đúng thủ thuật Kiki dùng cho wake word; (c) lấy nguyên
   **bảng tham số ngắt câu** đã được Kiki tinh chỉnh cho môi trường xe. Chi tiết §8.

---

## 1. Manifest — quyền, thành phần exported, deep link

**Quyền đáng chú ý** [ĐO] (`../jadx-kiki/resources/AndroidManifest.xml`): `RECORD_AUDIO`, `FOREGROUND_SERVICE` +
`FOREGROUND_SERVICE_MICROPHONE` + `FOREGROUND_SERVICE_LOCATION`, `SYSTEM_ALERT_WINDOW`, `WAKE_LOCK`,
`CALL_PHONE`, `READ_CONTACTS`, `READ_PHONE_STATE`, `ACCESS_FINE/COARSE/BACKGROUND_LOCATION`,
`PACKAGE_USAGE_STATS`, `REORDER_TASKS`, `RECEIVE_BOOT_COMPLETED`, `INTERNET`.
`minSdk 21` · `targetSdk 36` · `usesCleartextTraffic="false"` · `largeHeap="true"`.

**Không khai báo**: `VoiceInteractionService`, `RecognitionService`, `AccessibilityService` — cả ba đều **vắng mặt**
[ĐO negative]. `android.speech.RecognitionService` chỉ xuất hiện **bên trong `<queries>`** (Kiki *đi hỏi* recognizer
khác), không phải thứ Kiki *cung cấp*. ⇒ Kiki **không đăng ký làm trợ lý hệ thống kiểu VIS**; nó ăn theo
`ACTION_ASSIST` ở tầng **Activity**.

### 1.1 Thành phần EXPORTED

| Thành phần | Loại | Exported | permission | Action / dữ liệu |
|---|---|---|---|---|
| `ai.zalo.kiki.auto.ui.CarMainActivity` | activity | **true** | **không có** | `MAIN/LAUNCHER` · `android.intent.action.ASSIST` · `VOICE_ASSIST` · `android.speech.action.WEB_SEARCH` · deep link `kikiassistant://` (host `main`, `settings` + path `/request_help`,`/purchase_product`, `guideline`, `gotech`) |
| `ai.zalo.kiki.auto.service.autowake.KikiAutoWakeService` | service | **true** | **không có** | action **`ai.zalo.kiki.car.autowake`** · AIDL `ai.zalo.kiki.auto.IKikiAutoAutoWakeRemoteService` |
| `…app_handle.kws.KWSTriggerActivity` | activity | **true** | không có | không intent-filter; trampoline trong suốt, `finish()` ngay ở `onStart/onResume` |
| `ai.zalo.kiki.auto.service.KikiBootService` | service | true | `BIND_NOTIFICATION_LISTENER_SERVICE` | `NotificationListenerService` |
| `ai.zalo.kiki.auto.service.KikiBootReceiver` | receiver | true | `RECEIVE_BOOT_COMPLETED` | `BOOT_COMPLETED` · `QUICKBOOT_POWERON` · `LOCKED_BOOT_COMPLETED` · **`auto.dvd.system.dvdautosystemhelper.BOOT_DISPATCH`** (broadcast boot của đầu máy TQ) |
| `…app_update.DownloadService` | service | true | không có | — |
| activity-alias `com.syu.radio.Launch` · `com.syu.app.AppRadio` | alias | true | không có | action `com.syu.radio`, scheme `radio://` — **giả dạng app radio của nền SYU** để nút "Radio" phần cứng mở Kiki |

**Content provider**: chỉ `androidx.core.content.FileProvider` (authority `ai.zalo.kiki.car`, **exported=false**) và
`androidx.startup.InitializationProvider`. **Không có provider công khai nào để đọc/ghi dữ liệu Kiki** [ĐO].

**`<queries>`** liệt kê ~80 gói [ĐO] — chân dung thị trường của Kiki: đầu máy hậu mãi VN (Zestech, Gotech, Kovar,
Winca, Bravigo, AICity, Eonon, Teyes), nav (`com.google.android.apps.maps`, `com.vietmap.s1OBU`/`S2OBU`,
`vn.vietmap.*`, `com.navitel`), nhạc/video (`com.zing.mp3`, YouTube + ~10 bản vá), TV (VTVgo, FPT Play, MyTV),
camera 360 (`cn.cardoor.zt360`, `com.tanzhang.dct360`), và `android.car.server`, `com.spd.txzAdapter`, `com.ts.MainUI`.
**Không có gói `com.byd.*` nào** [ĐO].

### 1.2 `KikiAutoWakeService` — đọc kỹ, vì đây là "API công khai" duy nhất

`onBind` trả về một Binder thủ công (không phải AIDL sinh tự động). `onTransact` chỉ xử lý **transaction code 1**:
đọc `readString()` (tên gói của bên gọi) + 1 `readString()` bỏ đi + 1 `readInt()` bỏ đi, rồi **mở
`CarMainActivity`** với `activate_source` = `"zing_mp3"` (nếu bên gọi là `com.zing.mp3`) hoặc
`"third_party_<pkg>"`. Nếu API > 28 mà chưa có `canDrawOverlays` thì xếp hàng chờ. [ĐO]

> **Kết luận cứng**: binder này **không nhận câu lệnh, không nhận chữ để đọc, không trả gì về**. Năng lực duy nhất =
> *"mở Kiki lên và bắt đầu nghe"*. Nó **không** biến Kiki thành dịch vụ ASR cho app khác. [ĐO]

---

## 2. ASR — nhận dạng ở đâu, bằng gì

### 2.1 Tại máy: CHỈ wake word + VAD

Thư viện native nằm ở **`split_config.arm64_v8a.apk`** (không phải `base.apk` — đây là app split, nếu chỉ mở
`base.apk` sẽ kết luận nhầm là "không có native lib") [ĐO]:

| `.so` | Kích thước | Vai trò |
|---|---|---|
| `libsherpa-onnx-jni.so` | 3.37 MB | sherpa-onnx (bản build đầy đủ) |
| `libonnxruntime.so` | 15.99 MB | ONNX Runtime `VERS_1.17.1` |
| `libvad.so` | 60 KB | VAD riêng của Zalo, namespace `zlb_speech` |
| `libdecoder.so` | 1.02 MB | decoder |
| `libcrashlytics*.so` | — | Firebase |

Asset mô hình trong `base.apk` [ĐO]:

| Asset | Kích thước | Vai trò |
|---|---|---|
| `assets/ww_20260526.onnx` | **11,886,796 B** | mô hình wake word (CTC) |
| `assets/tokens.txt` | **19 B** | bảng token |
| `assets/vad.ort` | 1,956,984 B | mô hình VAD (định dạng ORT) |
| `assets/vad_config.json` | 621 B | luật ngắt câu |
| `assets/vad_version.txt` | 14 B | `20230804000010` |

**`tokens.txt` đầy đủ, nguyên văn** [ĐO] — đây là bằng chứng quyết định:

```
<blk> 0
i 1
k 2
o 3
```

Bốn token. Không có `keywords.txt`, không có encoder/decoder/joiner, không có từ điển tiếng Việt.
⇒ Mô hình 11.9 MB này **chỉ phát ra được ba chữ cái `k`, `i`, `o`** — vừa đủ đánh vần `k i k i o i` = **"Kiki ơi"**.
Không thể là ASR mở. Xác nhận chéo trong `res/values/strings.xml`: `str_setting_wakeword_trigger` = **`Kiki ơi`**,
`str_setting_wakeword_trigger_section` = `Kích hoạt Kiki bằng "Kiki ơi"`. **Không có "Hey Kiki"** [ĐO].

Cấu hình nhận dạng [ĐO]: `OnlineRecognizerConfig` + **`OnlineWenetCtcModelConfig`** (WeNet-CTC streaming), fbank
80 chiều, **16 kHz**, chunk 16 / left-chunk 4, **numThreads = 1**, device `"cpu"`, `enableEndpoint=true`,
`decodingMethod="greedy_search"`. Tìm từ khoá: `beamSize=10`, `topK=5`, `scoreThreshold=-7.0`; log của app ghi
*"KikiKWS initialized with KeywordBeamSearch"*. Vòng giải mã cửa sổ **640 ms**, chạy trên executor một luồng tên
`KikiKWS-Thread`.

> **Thủ thuật đáng học nhất của cả app**: thay vì dùng kiến trúc KWS chuyên dụng, Kiki **huấn luyện một mô hình ASR
> streaming trên bộ chữ cái tí hon (3 ký tự)** rồi tự chạy beam-search từ khoá lên trên. Đổi câu đánh thức = đổi
> file `ww_<ngày>.onnx` + `tokens.txt`, **không đụng runtime**; và model **cập nhật OTA được** (tên file có ngày,
> có đường tải mô hình từ remote config). [ĐO]

**Dịch vụ**: `ai.zalo.kiki.auto.service.KWSService` (foreground, `foregroundServiceType="microphone"`), action nội bộ
`…KWS_START` / `…KWS_STOP`, giữ `PowerManager.WakeLock` tên `"KWS::WakeLock"`. Khi bắt được từ khoá → mở
`CarMainActivity` với `activate_source="wake_word"`; nếu `startActivity` ném lỗi thì thử lại qua `PendingIntent.send()`.
Có `WakeWordRestartWorker` (WorkManager) canh sống lại. [ĐO]

### 2.2 Trên mây: ASR streaming qua WebSocket

[ĐO] `assets/koin.properties` — **file cấu hình lộ toàn bộ topology** (⚠ file này cũng chứa **khoá riêng dạng
plaintext**; **cố ý không chép vào repo**, xem §6):

| Vai trò | Domain (chỉ tên miền) |
|---|---|
| ASR streaming | `gotech-socket.asr.zalo.ai` (đường `/client/ws/speech`), biến thể `v6-gotech-socket.asr.zalo.ai` |
| ASR dự phòng không-streaming | `kiki-wss-failover-http.kiki.zalo.ai` (`api/v1/non-streaming/decode`) |
| TTS | `partner-speech.lab.zalo.ai` (`tts/stream`) |
| NLU / hội thoại / backend app | `api.kiki.zalo.ai` |

**Định dạng âm thanh gửi lên** [ĐO] — chuỗi query dựng trong client:
`content-type=audio/x-raw,layout=interleaved,rate=16000,format=S16LE,channels=1`
⇒ **PCM thô 16 kHz, mono, signed 16-bit LE. Không nén, không Opus** (grep `opus` trên `ai/zalo` = 0 hit).
Dạng giao thức này là kiểu **Kaldi-GStreamer `client/ws/speech`** cổ điển [SUY].

Kèm mỗi phiên ASR có **`lat`/`long` GPS** [ĐO] — [SUY] để server thiên vị tên địa danh khi nhận dạng.

**`android.speech.SpeechRecognizer` có được dùng không?** — **Có, làm engine phụ** [ĐO]: một lớp
`implements ASREngine, RecognitionListener` gọi `SpeechRecognizer.createSpeechRecognizer` +
`isRecognitionAvailable()`, extras `LANGUAGE_MODEL="free_form"`, `LANGUAGE="vi-VN"`. [SUY] đây là nhánh dự phòng
Google, ứng với chuỗi `error_google_installed` = *"Vui lòng bật nhập liệu bằng giọng nói và thử lại"*.

**Phạm vi sherpa-onnx**: `.so` là bản build đầy đủ (export cả `OfflineRecognizer`, `KeywordSpotter`, `Vad`,
`SpeakerEmbedding…`), **nhưng phía Kotlin chỉ nối `OnlineRecognizer`/`OnlineStream`/`OnlineWenetCtcModelConfig`**, và
call site duy nhất là lớp KWS. Bảng mô hình mẫu của sherpa (zipformer/paraformer/nemo…) còn sót trong mã là **mã
chết** — các mô hình đó không có trong APK. [ĐO]

### 2.3 VAD & ngắt câu — bảng tham số nên lấy nguyên

`libvad.so` **không phải** WebRTC, **không phải** Silero dựng sẵn: là thư viện riêng của Zalo, namespace
`zlb_speech`, dựng trên ONNX Runtime 1.17.1 + frontend fbank của **WeNet**. Symbol export: `zlb_speech::Init /
Reset / Decode`, `OnnxVadModel`, `VadPipeline`, `EndpointDetection{ReadEndpointConfig, EndpointDetected,
CheckEndWord, CheckWaitWord}`, `Trie/TrieNode`. [ĐO] · `vad.ort` có phải gốc Silero không → **[CHƯA BIẾT]** (phải
mở đồ thị ORT mới biết).

Khung xử lý: **640 byte = 320 mẫu = 20 ms** @16 kHz mono S16, cộng bộ đệm nhìn lui **7040 byte ≈ 220 ms** [ĐO].

**`assets/vad_config.json` — ba luật ngắt câu** [ĐO]:

| Luật | `must_contain_non_silence` | `min_trailing_silence` | `min_utterance_length` | Nghĩa |
|---|---|---|---|---|
| rule1 | false | **6.0 s** | 0.0 | im 6 s mà chưa nói gì → bỏ phiên |
| rule2 | true | **1.5 s** | 0.0 | đã nói, im 1.5 s → chốt câu |
| rule3 | false | 0.0 | **15.0 s** | trần cứng 15 s một câu |

`end_word` / `wait_word` = **rỗng** — bộ máy Trie/`CheckEndWord` có sẵn nhưng chưa bật; [SUY] là chỗ chừa để bật
ngắt câu theo từ khoá qua remote config. Cả `vad.ort` + `vad_config.json` đều được **chép ra `filesDir` và khoá
phiên bản bằng `vad_version.txt`** ⇒ server đẩy VAD mới **không cần cập nhật APK** [ĐO].

> Ba con số **6.0 / 1.5 / 15.0** là tham số đã được tinh chỉnh cho **cabin xe đang chạy**. Đây là thứ rẻ nhất và
> chắc nhất để mượn — nó là **dữ liệu, không phải mã**.

### 2.4 Thu âm & xử lý ồn

- **`audioSource = 1` = `MIC`** [ĐO] (không phải `VOICE_RECOGNITION`, không phải `VOICE_COMMUNICATION`), 16 kHz,
  `CHANNEL_IN_MONO`, `ENCODING_PCM_16BIT`. [SUY] cố ý: `VOICE_COMMUNICATION` trên đầu máy TQ hay kèm AGC/NS mạnh
  tay làm hỏng mô hình wake word CTC.
- **`echoCancellation = false` ở bản production** [ĐO] — cờ có nối dây nhưng mọi chỗ đăng ký DI đều truyền `false`.
  `AcousticEchoCanceler` chỉ xuất hiện ở **màn chẩn đoán**, và còn **vứt luôn giá trị trả về** (không hề
  `setEnabled(true)`) ⇒ thực chất vô hiệu.
- **Không có `NoiseSuppressor`, không có `AutomaticGainControl`** ở bất kỳ đâu [ĐO negative]. Xử lý ồn **hoàn toàn
  bằng phần mềm + UX**: một `CarVolumeMonitor` tính dBFS trượt (`20*log10(rms)`, cửa sổ 8000 mẫu, EMA α=0.2) và bật
  cờ `isNoisy` khi nhạc lớn hơn giọng tham chiếu > 3 dB → hiện chuỗi *"Nhạc nền quá lớn có thể khiến Kiki nhận diện
  kém hơn"*. [ĐO]
- **MỘT recorder dùng chung** cho cả KWS và ASR (`AudioRecorderService.newSession()/endSession()`), không mở
  `AudioRecord` thứ hai [ĐO]. Có `AndroidAudioFocusHandler` riêng cho audio focus.

---

## 3. NLU — làm ở đâu, có những ý định gì, thi hành ra sao

### 3.1 NLU **hoàn toàn ở server** [ĐO]

Client gửi âm thanh (hoặc chữ), server trả về một phong bì JSON chứa **danh sách "directive" đã sẵn sàng thi hành**.
Không có bộ so khớp ý định cục bộ nào cho đường online.

**Hình dạng phong bì** [ĐO] (tên trường nguyên văn, lấy từ `…/core/app/dao/NLPIntentDAOKt.java`):

```
status · asr · log_request_id · session_key
directives[] : { action_id · action_code · required[] · <payload riêng từng loại> }
```

Khoá payload hay gặp: `query`, `text`, `utter`, `utter_text`, `render`, `link`, `package_name`, `packages`,
`app_type`, `ui_promt`, `ui_suggestions`. **Không có** trường tên `intent` / `slot` / `entity` / `domain` / `nlu` —
loại hành động nằm trong chính payload, do `DirectiveClassifier.classify(JSONObject)` phân loại. [ĐO]

Thứ duy nhất chạy cục bộ là **bộ so khớp địa danh** (không phải ngữ pháp lệnh) [ĐO]:
`res/raw/parser_key_word.json` — `level_1` = 63 tỉnh/thành; `type` = `thành phố, tỉnh, quận, huyện, thị xã, thị trấn`;
`place` = `sân bay, bến xe, bệnh viện, cầu`. Trong mã còn có `hẻm, ngách, ngõ, chợ, sân bay`, số đọc chữ
(`một … mười`), và từ xưng hô (`chị`, `em gái`) để chuẩn hoá tên danh bạ.

### 3.2 Danh mục directive — "từ vựng ý định" thật của Kiki

[ĐO] 42 hằng số, nguyên văn từ `NLPIntentDAOKt.java`:

| Nhóm | Directive |
|---|---|
| Nói / hiển thị | `SpeechSynthesizer` · `SpeechSynthesizerOffline` · `DisplayCard` · `ShowUiGuideline` |
| Nhạc / media | `PlayerMP3` · `PlayerPodcast` · `PlayerVolume` · `PlayMusicApp` · `PlayRadioApp` · `PlayTVApp` · `PlayVideoApp` |
| Dẫn đường | `MapDirection` · `MapDirectionOpenApp` · `SearchMap` |
| Ứng dụng | `OpenApp` · `AppAction` · `InstallApp` · `CheckAppInstalled` |
| Liên lạc | `MessageInput` · `CallSkill` · `CallGotech` *(deprecated)* |
| Khác | `Alerts` (báo thức/nhắc) · `Audio` (dịch) · `CallApi` · `Delay` |
| **Điều khiển xe** | **`ActionACTemp`** · **`ActionACWind`** · **`ActionACDry`** · `ZestechCamera` · `GotechCamera` · `Camera360` · `RequestCarModelCode` |
| **Intent tổng quát** | **`ExecuteIntent`** · **`ActivityIntent`** · **`ServiceIntent`** · **`LaunchIntent`** · **`LocalBroadcast`** · **`GlobalBroadcast`** |
| Cổng điều kiện | `CheckPermission` · `CheckVisibility` · `LocationPermissionCheck` · `LocationPermissionRequest` |

Enum phụ `Directive$ExternalActionType` [ĐO]: `MAP_DIRECTION, OPEN_APP, PLAY_VIDEO, PLAY_MUSIC, CALL_SKILL,
PLAY_RADIO_APP, PLAY_TV_APP, APP_ACTION, INSTALL_APP, UNKNOWN`.

Hợp đồng thi hành `…/directive_handler/contract/execute_services/` [ĐO]: `Mp3Service`, `PlayerService`,
`VolumeService`, `AlertService`, `OpenAppService`, `ApiCallService`, `ShowUiService`, `CheckPermissionService`,
`CheckVisibilityService`, `AssistantDisplayService`, `UIService`, `CallExecutor`.

> **Điểm kiến trúc quan trọng nhất của §3**: nhóm `ExecuteIntent / ActivityIntent / ServiceIntent / LaunchIntent /
> LocalBroadcast / GlobalBroadcast` nghĩa là **server đẩy được một Intent Android tuỳ ý** (component + action +
> extras) xuống máy để bắn. Đây chính là **cơ chế mở rộng theo hãng đầu máy** của Kiki: thêm một dòng xe/đầu máy
> mới **không cần sửa app**, chỉ cần cấu hình server. `RequestCarModelCode` là bước bắt tay để server biết đang
> chạy trên đầu nào. [ĐO cơ chế] / [SUY] về ý đồ.
>
> Hệ quả cho Kachi: **về lý thuyết** Zalo có thể cấu hình server để Kiki bắn broadcast sang Kachi. **Nhưng** đó là
> quyết định nằm ở phía Zalo, không phải thứ Kachi tự mở được → **không phải đường đi cho V1**. [SUY]

### 3.3 Thi hành — Kiki chạm vào xe bằng gì

**Không có gì của BYD.** `grep -niw "byd|dilink"` trên `ai/zalo` = **0 hit**; không `android.car`, không
`CarPropertyManager`, không `Car.createCar` [ĐO negative]. Enum nền tảng đầu máy:
`…/kiki_car_native_lib/internal/device/DeviceType.java` = **`SYU`, `TS`, `TW`** [ĐO]. `koin.properties` còn dòng bị
comment `kiki_app_device_type=ANDROID_ZESTECH` ⇒ **bản APK này là bản dựng cho đầu máy Gotech/Zestech**, không phải
cho xe hãng [ĐO].

Hai cơ chế chạm phần cứng, **cả hai đều là AIDL bind tới service của đầu máy**:

1. **SDK "TS"** — bó AIDL `com/ts/tsspeechlib/*.aidl` kèm trong APK, bind theo action ẩn:
   - `com.ts.tsspeechlib.car.TsCarService` → `ITsSpeechCar`: điều hoà (`onTurnOnAir`, `onSetAir(temp)`,
     `onUpWindSpeed/onDownWindSpeed`, `onWindMode`, `onSetLoop`), **kính/nóc** (`onOpenSkylight/onCloseSkylight`,
     `onOpenAllCarwindow/onCloseAllCarwindow`, `onOpenCarwindow/onCloseCarwindow`), sấy kính (`onHeatRearwindow`).
   - `com.ts.tsspeechlib.function.TsFunctionService` → `ITsSpeechFunction`: màn hình
     (`onOpenScreen/onCloseScreen/setScreenBrightness/onBrightenScreen/onDimmingScreen`), âm lượng
     (`getCurrentVolume/setVolume/onVolumeUp/Down/Max/Min/Mute/Unmute`), `showLauncher/openSetting/openCarInfo`.
   - `ITsSpeechMusic` (`onMusicPlay/Pause/Prev/Next`), `ITsSpeechRadio` (`openRadio/onRadioFM/onRadioAM/onNextFreq`),
     `ITsSpeechBt` (danh bạ + gọi qua Bluetooth xe).
   - Gốc `ITsSpeech` nhận binder **do đầu máy trao xuống** (`setCar/setFunction/setRadio/setMusic/setBtPhone`).
2. **Service CAN của Zestech** — bind action `com.ts.can.carinfo.CarInfoService`, descriptor
   `com.ts.can.carinfo.ICarInfoService`, rồi **ghi khung CAN thô** bằng `write(byte[], len)`. Các khung liệt kê
   nguyên văn trong mã, ví dụ bật A/C `5AA5023D020141`, tăng nhiệt `5AA5023D0D014C`, sấy kính trước
   `5AA5023D2C016B`, sấy vô-lăng `5AA5023D2D016C`, sưởi ghế `5AA5023D110150`. Cờ đọc về: `fgAC`, `fgAutoAC`,
   `nLeftTemp`, `nWindValue`, `fgInnerLoop`, `nLtChairHot`, `fgWheelHot`, `bForeWindHotFlg`. [ĐO]

Các đường thi hành khác [ĐO]:
- **Gọi điện**: `Intent("android.intent.action.CALL")` + `Uri "tel:<số>"` (quyền `CALL_PHONE`); trên đầu TS thì đi
  `ITsSpeechBt` qua Bluetooth xe.
- **Mở app / nav / video**: dựng Intent + `setPackage(...)`, có nhánh riêng cho `com.google.android.apps.maps`.
- **Âm lượng**: đi **SDK đầu máy**, **không** `AudioManager.setStreamVolume` (grep = 0 hit) [ĐO negative].
- **Media**: có `NowPlayingBackingService` (foreground `mediaPlayback`) + trình phát YouTube trong app; **không**
  bơm `KEYCODE_MEDIA_*`, **không** dùng `MediaController` transport [ĐO negative].
- **Accessibility**: **không dùng** [ĐO negative]. **Shell/root**: có hàm `Runtime.exec` nhưng call site duy nhất là
  traceroute mạng — **không** dùng để điều khiển xe [ĐO].

---

## 4. TTS

**Trên mây, không có engine tại máy** [ĐO]. **Không dùng `android.speech.tts.TextToSpeech` ở bất kỳ đâu**
[ĐO negative].

- Host `partner-speech.lab.zalo.ai`, `POST tts/stream`; trả về **link HLS** để trình phát kéo về (không phải blob).
- **Hai giọng Việt** [ĐO]: `speakerId = 11` = **Miền Bắc**, `speakerId = 5` = **Miền Nam**
  (`setting_tts_voice_title` = *"Giọng đọc Trợ lý Kiki"*, `setting_tts_voice_north` / `_south`). `encodeType = "2"`.
- **Có cache TTS trên đĩa**, đầy đủ Room + nhật ký + LRU evictor (`voice_tts/cache/{Cache, journal/JournalDatabase,
  evictor/CacheEvictor}`) — [SUY] để các câu hay lặp (xác nhận, báo lỗi) vẫn nói được khi mạng chập chờn.
- **~137 câu thu sẵn** dạng `res/raw/northern_*.aac` + `southern_*.aac` (mỗi câu hai giọng), phát qua directive
  `SpeechSynthesizerOffline` + `OfflineSpeechMapper`. Khoá gồm `INTERNET_ISSUE`, `GENERAL_ERROR`, `ASR_NOT_CLEAR`,
  `ASR_REASK`, `SEARCH_REASK`, `CONFIRM_YES/NO`, `OPEN_APP_*`, `APP_NOT_INSTALLED_*`, `NAVIGATE_TO_LOCATION`,
  `PLAY_RADIO`, `KIKI_READY`… [ĐO]

---

## 5. UI / UX — nghe, xác nhận, báo lỗi, hết giờ

- **Bong bóng nổi**: `…ui.custom.chathead.ChatHeadService` (foreground `location|mediaPlayback|dataSync`) — cần
  `SYSTEM_ALERT_WINDOW`. Chuỗi mời cấp quyền: *"Tiện ích nổi cho phép một số tiện ích của Kiki luôn hiển thị trên
  màn hình… Vui lòng cấp quyền hiển thị cho Kiki"* (`assets/version_info.json`). [ĐO]
- **`CarMainActivity` là Activity trong suốt** (`AppTheme.ActiTheme.Transparent.NoAnim`), `singleInstance` +
  `clearTaskOnLaunch` ⇒ trợ lý hiện đè lên app đang chạy chứ không chiếm hẳn màn. [ĐO]
- **Trạng thái nghe**: `micro_listening` = *"Đang lắng nghe…"*, `searching` = *"Đang tìm kiếm…"*,
  `gotech_idle_status_text` = *"Bấm micro để nói"*. [ĐO]
- **Nghe không rõ / hỏi lại**: `error_asr_reask` = *"Mình chưa nghe rõ, hãy thử nói lại"*;
  *"Không nhận diện được giọng nói"*; khoá offline `ASR_NOT_CLEAR`, `ASR_REASK`, `SEARCH_REASK`,
  `MP3_NOT_FOUND_REASK`, `GUIDE_SPECIFY_SONG`, `LOCATION_NOT_FOUND`, `CANNOT_LOCATE_DESTINATION`,
  `INTENT_NOT_SUPPORTED`, `FUNCTIONALITY_NOT_SUPPORTED`. Trượt wake word: *"Kiki không khoá được, thử nói khá hơn!"*. [ĐO]
- **Gợi ý sửa sai**: server trả về gợi ý dạng `Thử nói "…"`; client **cắt tiền tố + bỏ ngoặc kép** rồi render thành
  chip bấm được. [ĐO]
- **Xác nhận trước khi thi hành** — chỉ ở **vài đường rủi ro**, không phải mọi lệnh [ĐO]:
  `call_name` = *"Bạn muốn gọi cho %1$s?"* · `call_number` = *"Bạn muốn gọi đến số %1$s?"* ·
  `call_confirm` = *"Hãy bấm nút trên màn hình để thực hiện cuộc gọi cho %1$s"* ·
  `call_title` = *"Tìm thấy %1$d liên hệ có tên %2$s"* · khoá offline `CONFIRM_YES` / `CONFIRM_NO`.
  Cảnh báo an toàn khi lái: `gotech_video_warning` = *"Không nên xem video trong khi lái xe…"*,
  *"Xem video trên ứng dụng khác?"*.
- **Hết giờ nghe**: do **VAD quyết định**, không phải timer UI — 1.5 s im sau khi đã nói / 6.0 s im nếu chưa nói /
  trần 15 s (§2.3). [ĐO]
- **Lệnh ghép**: `compound_command_warning_description` nêu ví dụ *"Chỉ đường đến Bitexco và mở nhạc trẻ"*, kèm
  `compound_command_split_screen_title` = *"Tự động chia đôi màn hình"* và một
  `CompoundCommandSplashActivity` ⇒ **hai ý định trong một câu nối bằng "và", thi hành song song + tự chia đôi màn**. [ĐO]
- **Phát hiện người dùng vật lộn** (`res/raw/default_settings.json`): sau N lần hỏi hỏng liên tiếp thì hiện banner
  *"Thiết bị đang có lỗi ghi âm"* / *"Gửi báo lỗi đến Kiki để nhận hỗ trợ qua Zalo"*. [ĐO] — mẫu UX đáng học.

---

## 6. Mạng & riêng tư

**Domain gọi ra** (⚠ **chỉ ghi tên miền**; token/khoá/tham số cố ý bỏ) [ĐO]:

| Domain | Vai trò |
|---|---|
| `gotech-socket.asr.zalo.ai` · `v6-gotech-socket.asr.zalo.ai` | ASR streaming (WebSocket) |
| `kiki-wss-failover-http.kiki.zalo.ai` | ASR dự phòng không-streaming + dự phòng wake word |
| `partner-speech.lab.zalo.ai` | TTS |
| `api.kiki.zalo.ai` · `kiki.zalo.ai` | NLU/hội thoại, xác thực, kích hoạt, cấu hình, guideline, trang điều khoản |
| `ipv4.kiki.zalo.ai` · `ipv6.kiki.zalo.ai` | tự chẩn đoán kết nối |
| `oauth.zaloapp.com` · `graph.zaloapp.com` · `id.zalo.me` · `zalo.me` | đăng nhập Zalo (OAuth + QR) |
| `ztevents.zaloapp.com` | analytics sự kiện |
| `srv.mp3.zing.vn` · `news.zing.vn` · `stc-ki-ki.zdn.vn` | nội dung nhạc / tin / CDN |
| `kiki-200de.firebaseio.com` · `kiki-200de.appspot.com` | Firebase (Crashlytics + GA) |
| `kiki-auto-cs.online` | tra cứu APK gỡ lỗi ("hỗ trợ kỹ thuật viên") |
| `focusit209.pythonanywhere.com` | **upload log/telemetry** — host PaaS bên thứ ba, bất thường với một sản phẩm production [ĐO] |
| `dns.google` · `www.google.com` · `api.ipify.org` · `checkip.amazonaws.com` | dò mạng / captive portal / IP công cộng |

> ⚠ **Ghi nhận vệ sinh bảo mật (của Kiki, không phải của ta)**: `assets/koin.properties` trong APK phát hành chứa
> **khoá riêng API dạng plaintext** và token đối tác ASR. **Không chép các giá trị đó vào repo này** — đã cố ý loại
> bỏ khỏi tài liệu. Nêu ra vì nó là bài học ngược: đừng bao giờ để khoá trong `assets/` (CLAUDE.md §6).

**Hoạt động offline?** — **Không hiểu được gì khi mất mạng** [ĐO]. Chạy cục bộ chỉ có: wake word, VAD, và ~137 câu
TTS thu sẵn. **Không có bộ phân loại ý định cục bộ nào.** Chuỗi báo lỗi xác nhận điều này:
`error_internet_issue` = *"Vui lòng kết nối internet và thử lại"* · `check_network` ·
`error_server_issue` = *"Kết nối không ổn định, vui lòng thử lại sau"* ·
`error_reject_asr_when_slow_network_checking` = *"Kết nối hiện tại không ổn định, vui lòng thử lại trong giây lát"*.

Đáng chú ý: có hẳn **cổng kiểm tra sức khoẻ mạng TRƯỚC khi mở mic** (`NetworkHealthService.checkNetworkHealth(minHealth)`)
— khi đoán đường truyền quá yếu thì **từ chối mở mic** thay vì để người dùng nói xong rồi mới báo hỏng. [ĐO]
**Mẫu UX rất đáng học.**

**Riêng tư**: gửi GPS `lat`/`long` kèm **mỗi phiên ASR**; có màn "Mục đích sử dụng dữ liệu" (*"Cung cấp, vận hành và
phát triển tính năng"*, *"Cá nhân hoá quảng cáo và giới thiệu tính năng"*), có công tắc lưu lịch sử, và tuỳ chọn
**chia sẻ đoạn âm thanh wake word** (lưu `filesDir/wake_word_files`, tự dọn khi > 10 MB). [ĐO]

---

## 7. Bài học cho Kachi

### 7 (a) Cái gì Kachi gọi thẳng Kiki được

| Khả năng | Được không | Bằng chứng |
|---|---|---|
| Mở Kiki + bắt đầu nghe (qua nút mic 328) | ✅ **đang chạy thật** | [ĐO on-car 08-14] |
| Mở Kiki + bắt đầu nghe (qua bind `ai.zalo.kiki.car.autowake`) | ✅ | service exported, **không permission**, transaction 1 [ĐO] |
| Mở Kiki (qua `ACTION_ASSIST` / `VOICE_ASSIST` / deep link `kikiassistant://`) | ✅ | intent-filter trên activity exported [ĐO] |
| **Đẩy một câu lệnh CHỮ cho Kiki thi hành (bỏ qua ASR)** | ⚠ **rất có thể** | extra `text_command` trên activity **exported**, chạy vào `startAssistantListen(…, textCommand, …)` [ĐO chữ ký] · hiệu lực **[SUY]**, chốt bằng §8.2 |
| **Lấy lại chữ đã nhận dạng** | ❌ **KHÔNG** | không `RecognitionService`, không `VoiceInteractionService`, binder không trả gì [ĐO] |
| **Lấy lại ý định/slot đã phân tích** | ❌ **KHÔNG** | directive đi thẳng từ server vào bộ thi hành nội bộ [ĐO] |
| **Nhờ Kiki đọc một câu chữ mình đưa** | ❌ **KHÔNG** | chữ cho TTS chỉ đến từ directive `SpeechSynthesizer` của server [ĐO] |
| **Nhờ Kiki điều khiển xe BYD** | ❌ **KHÔNG** | không có mã BYD nào; chỉ AIDL đầu máy TS/Zestech [ĐO] |

> **Kết luận 7(a)**: **Kiki KHÔNG dùng được làm front-end ASR/NLU cho Kachi.** Không có đường trả kết quả về —
> đây là rào chắn kiến trúc, không phải thiếu tài liệu. Quan hệ khả dĩ duy nhất là **một chiều, mức "mở app"**:
> Kachi nhường nút cho Kiki ở các miền Kiki giỏi. Nếu §8.2 xác nhận `text_command` chạy thật thì thêm được một
> đường một chiều nữa — nhưng vẫn là *"Kachi bảo Kiki làm"*, không bao giờ là *"Kiki bảo Kachi làm"*.

### 7 (b) Cái gì Kachi BẮT BUỘC phải tự làm

1. **Toàn bộ điều khiển xe.** 65 nút `ControlRegistry` + 123 datum `TelemetryRegistry` của Kachi đi qua **BYD HAL**;
   Kiki không có một dòng nào chạm tới đó và không có cơ chế nào để Kachi cắm vào. [ĐO]
2. **Toàn bộ hành động riêng của launcher**: đổi hồ sơ tài xế, bố cục lưới, gán ô, chiếu cụm, thanh trên, 5 gói lệnh
   `ActionMacros`, 3 `LauncherActions`. Kiki không biết những khái niệm này tồn tại.
3. **Ngữ pháp lệnh**: Kiki **không có ngữ pháp cục bộ để mượn** — nó là bộ phân loại trên server. Kachi phải tự
   dựng. (Cái mượn được là **danh sách câu mẫu** ở 7(c) và **hình dạng động từ × đối tượng**.)
4. **Chạy khi mất mạng.** Xe vào hầm / hết 4G / SIM hết dung lượng thì Kiki **câm hoàn toàn**. Nếu Kachi muốn
   *"bật điều hoà"* luôn chạy được, nó **phải** nhận dạng tại máy.

### 7 (c) Mẫu câu tiếng Việt — hạt giống ngữ pháp cho `:core`

> **Lưu ý mức bằng chứng**: đây **toàn bộ là chuỗi GỢI Ý hiển thị cho người dùng** (carousel guideline + màn "Kiki
> làm được gì"), **không phải mẫu khớp trong mã** — vì Kiki không có bộ khớp cục bộ (§3.1). Giá trị của chúng là
> **cho biết người Việt thật sự nói thế nào với trợ lý trên xe**, đã qua kiểm chứng thị trường. [ĐO nguyên văn]

**Dẫn đường** — `res/values/strings.xml` + danh sách dự phòng trong mã
1. Dẫn đường đến chợ Bến Thành
2. Chỉ đường đến số 72 Nguyễn Cơ Thạch
3. Tìm trạm xăng gần đây
4. Chỉ đường đến trạm xăng gần nhất
5. Chỉ đường đến chợ Bến Thành
6. Chỉ đường đến Hà Nội

**Nhạc**
7. Mở bài Nồng nàn Hà Nội
8. Mở nhạc Trữ tình
9. Mở nhạc của Mỹ Tâm
10. Mở bài Cỏ dại và hoa dành dành
11. Mở nhạc của Lệ Quyên
12. Mở nhạc Bolero
13. Mở nhạc trẻ remix

**Điều khiển phát / âm lượng**
14. Tăng âm lượng tối đa
15. Giảm âm lượng
16. Chuyển bài tiếp theo
17. Tắt nhạc

**Radio**
18. Mở radio
19. Mở kênh VOV Giao thông
20. Mở kênh FM 99.9 MHz

**Truyền hình**
21. Mở ứng dụng VTV Go
22. Mở kênh VTV1
23. Mở kênh HTV7 trên MyTV

**Video**
24. Mở video nhạc trẻ
25. Mở video bóng đá trên Youtube
26. Mở phim hoạt hình trên Youtube
27. Mở video phim hoạt hình
28. Mở video nhạc thiếu nhi

**Tin tức**
29. Tin thời sự hôm nay
30. Nghe tin tức bóng đá
31. Giá xăng hôm nay bao nhiêu?
32. Nghe điểm tin hôm nay
33. Nghe tin tức mới nhất
34. Mở điểm báo hôm nay

**Hỏi đáp**
35. Quốc ca được sáng tác năm nào?
36. Hành tinh nào lớn nhất trong Hệ Mặt Trời?

**Tiện ích / xe / thiết bị**
37. Kiểm tra áp suất lốp
38. Thời tiết hôm nay thế nào?
39. Hôm nay ngày mấy âm lịch?
40. 12 x 15 bằng bao nhiêu?
41. Hôm nay trời có mưa không?

**Lệnh ghép / khác**
42. Chỉ đường đến Bitexco và mở nhạc trẻ  *(hai ý định nối bằng "và" → chia đôi màn)*
43. Tốc độ giới hạn ở đây là 50km/h  *(câu phản hồi sửa dữ liệu)*
44. Kiki ơi  *(câu đánh thức — matcher duy nhất chạy tại máy)*

**Nhãn miền do Kiki đặt** [ĐO]: `Tìm đường` · `Nghe nhạc` · `Điều khiển nhạc` · `Nghe radio` · `Xem video` ·
`Xem truyền hình` · `Tin tức` · `Hỏi đáp` · `Tiện ích` · `Cảnh báo giao thông` · `Điểm báo` · `Giải trí` · `Dẫn đường`.
ID nhóm trong carousel: navigate=0, music=1, video=2, tv=3, utilities=6, search=7, news=8, radio=10 (4/5/9 bỏ trống —
[SUY] là gọi điện / nhắn tin / điều khiển xe ở các bản OEM khác).

**Hình dạng ngữ pháp rút ra** [SUY] — đây mới là thứ đem vào `:core`:

- Bộ động từ **nhỏ và rất đều**: `Mở` · `Phát` · `Nghe` · `Bật` · `Tắt` · `Tăng` · `Giảm` · `Chuyển` · `Tìm` ·
  `Chỉ đường` · `Dẫn đường` · `Kiểm tra`.
- **`Mở` bị quá tải nặng** (bài hát, ca sĩ, thể loại, kênh radio, kênh TV, video, app, điểm báo) ⇒ **phân biệt bằng
  KIỂU ĐỐI TƯỢNG, không bằng động từ**. Đây là ràng buộc thiết kế trực tiếp cho bộ phân tích của Kachi.
- Khuôn slot đáng bê nguyên: `Mở bài <bài hát>` · `Mở nhạc <thể loại>` · `Mở nhạc của <ca sĩ>` ·
  `Mở kênh <kênh>` · `Mở video <chủ đề> trên <app>` · `Chỉ đường đến <địa điểm>` · `Tìm <POI> gần đây|gần nhất` ·
  `<X> hôm nay thế nào?`
- **Nối "và"** cho câu ghép là tính năng hạng nhất, không phải trường hợp biên.
- Bộ từ điển địa chỉ của họ (63 tỉnh/thành + `thành phố|tỉnh|quận|huyện|thị xã|thị trấn` +
  `ngõ|ngách|hẻm|chợ|sân bay|bến xe|bệnh viện|cầu`) là **mẫu** cho bộ điền slot địa điểm cục bộ — ta tự dựng danh
  sách, không chép file của họ.

### 7 (d) Rủi ro nếu dựa vào Kiki

| Rủi ro | Mức | Chi tiết |
|---|---|---|
| **Không có API công khai** | 🔴 cao | `text_command` là extra **nội bộ không tài liệu** (bên dùng thật là bong bóng chip gợi ý của chính Kiki). Zalo đổi tên extra ở bản sau → đường của ta chết **im lặng**, không báo lỗi. |
| **Phải trả tiền / hết hạn** | 🔴 cao | `basic_trial` / `basic` / `premium`; mã kích hoạt mua ở đại lý; hết hạn là câm. Kachi không kiểm soát được vòng đời giấy phép của người dùng. |
| **Bắt buộc có mạng** | 🔴 cao | Mất mạng = không hiểu được gì (§6). Điều khiển xe mà phụ thuộc 4G là sai về bản chất an toàn. |
| **Bắt buộc đăng nhập Zalo** | 🟠 vừa | Có chế độ khách nhưng hạn chế; còn giới hạn số thiết bị đăng nhập. |
| **ToS / giấy phép** | 🟠 vừa | Có `Điều khoản sử dụng` bắt buộc đồng ý (`kiki.zalo.ai/terms-of-use`). **Mở app Kiki bằng Intent công khai là cách dùng Android bình thường** — không đụng gì. Nhưng **nhúng, bắt chước, hay tự động hoá Kiki như một dịch vụ backend** thì cần rà lại ToS. **Tuyệt đối không** chép mã/model/asset của Kiki vào Kachi. |
| **Riêng tư** | 🟠 vừa | Kiki gửi GPS kèm mỗi phiên ASR + telemetry qua host PaaS bên thứ ba. Nếu Kachi chủ động gọi Kiki thì đang thay mặt người dùng phát sinh việc đó → phải nói rõ trong UI. |
| **Không có BYD** | 🔴 cao | Kiki sẽ **không bao giờ** điều khiển được xe owner. Mọi năng lực V1 quan trọng nhất đều nằm ngoài tầm Kiki. |

---

## 8. Đề xuất cập nhật hướng V1

### 8.1 So với đề xuất hiện tại

Đề xuất đang ghi ở backlog V1: *"ASR tại máy Vosk + mô hình VI nhỏ + ngữ pháp sinh từ danh mục; hiểu ý = bộ phân
tích tất định ở `:core`"*.

**Phán quyết: GIỮ trục chính — RE này củng cố chứ không bác.** Kiki chứng minh bằng phản chứng: chính đội làm trợ lý
tiếng Việt mạnh nhất thị trường **cũng không** chạy nổi ASR mở tại máy trên phần cứng đầu xe — họ đẩy hết lên mây.
Ta **không** có hạ tầng mây, và điều khiển xe **không được** phụ thuộc mạng ⇒ đường duy nhất còn lại là **thu hẹp
bài toán**, đúng như đề xuất đang nói.

Ba điều chỉnh có bằng chứng:

- **(i) Đổi hình dạng bộ nhận dạng.** Đừng nhắm "ASR tiếng Việt đầy đủ rồi khớp ngữ pháp sau". Làm đúng thủ thuật
  Kiki dùng cho wake word: **giải mã trên một ngữ pháp/tập từ khoá ĐÓNG**. Kiki chạy mô hình 11.9 MB, **1 luồng
  CPU**, cửa sổ 640 ms, mượt trên chính lớp phần cứng này [ĐO] — đó là ngân sách tính toán đã được chứng minh, và
  nó chỉ đủ cho **tập đóng**, không đủ cho từ vựng mở.
- **(ii) Chia đôi việc với Kiki thay vì cạnh tranh.** Kiki đã **chạy tốt trên xe owner** cho nhạc / dẫn đường / hỏi
  đáp — các miền **từ vựng mở** mà ta không thể làm offline. Kachi **không nên** đua ở đó. Kachi giữ đúng phần
  không ai làm thay được: **điều khiển xe + hành động launcher**, tập đóng, chạy offline.
- **(iii) Lấy nguyên bảng tham số ngắt câu** 6.0 / 1.5 / 15.0 s (§2.3) + mẫu UX "từ chối mở mic khi biết sẽ hỏng"
  (§6) + mẫu "phát hiện người dùng vật lộn" (§5). Đây là **dữ liệu và mẫu thiết kế**, không phải mã.

### 8.2 Ba phương án

| | **A · Toàn bộ tại máy, tập đóng** | **B · Lai: tại máy + mây** | **C · Chia đôi việc với Kiki** ⭐ |
|---|---|---|---|
| ASR | Vosk/sherpa-onnx, **ngữ pháp đóng** sinh từ 6 bộ đăng ký | như A + ASR mây cho từ vựng mở | **như A** cho xe+launcher; **Kiki lo** nhạc/nav/hỏi đáp |
| Kích hoạt | nút Kachi riêng / câu đánh thức | như A | **nút mic 328 → Kiki (giữ nguyên, đang chạy)** + **một nút khác → Kachi** |
| Offline | ✅ toàn phần | ⚠ một phần | ✅ phần điều khiển xe chạy offline toàn phần |
| Điểm đến / tên bài hát | ❌ không làm được | ✅ | ✅ (Kiki lo) |
| Chi phí xây | vừa | **cao** (phải dựng + nuôi hạ tầng mây) | **vừa** (= A, cộng phần định tuyến nút) |
| Chi phí chạy | 0 | 🔴 định kỳ, theo phút nói | 0 (Kiki do người dùng tự mua) |
| Rủi ro phụ thuộc | 0 | phụ thuộc mây của ta | phụ thuộc Kiki **chỉ ở miền không quan trọng**; xe vẫn chạy khi Kiki hết hạn |
| Độ tin cậy | cao trong tập đóng | mạng quyết định | cao ở phần quan trọng |
| ToS | sạch | sạch | sạch (chỉ mở app bằng Intent công khai) |

**→ CHỌN phương án C.** Lý do: nó cho **năng lực đầy đủ nhất với rủi ro thấp nhất**, và nó **tôn trọng thực tế đã
đo trên xe** — Kiki đang chạy ngon cho nhạc/nav, không có lý do gì phá thứ đang hoạt động (CLAUDE.md §6: *"không
đảo thứ tự đường đã chạy tốt ngoài hiện trường"*). Phần Kachi tự làm **giống hệt phương án A** ⇒ nếu sau này muốn
bỏ Kiki, không mất gì; C là A **cộng thêm** một quyết định định tuyến ở tầng nút bấm.

**Ranh giới rạch ròi giữa hai bên**:

| Miền | Ai lo | Vì sao |
|---|---|---|
| Điều hoà, kính, ghế, sạc, đèn, chế độ lái… (65 nút) | **Kachi** | Kiki không chạm được vào BYD |
| Đọc thông số xe (123 datum) | **Kachi** | như trên |
| Hồ sơ tài xế, bố cục, ô, chiếu cụm, gói lệnh | **Kachi** | khái niệm riêng của launcher |
| Mở một app đã biết tên | **Kachi** | tập đóng, có sẵn danh sách |
| Bài hát / ca sĩ / thể loại | **Kiki** | từ vựng mở |
| Điểm đến dẫn đường | **Kiki** | từ vựng mở |
| Hỏi đáp, thời tiết, tin tức | **Kiki** | cần mây |

### 8.3 Tầng 1 bắt buộc — đo trên xe TRƯỚC khi viết dòng `:core` nào (CLAUDE.md §14)

Playbook §2.14 đã có 4 phép đo (mic cho app thường · RecognitionService hệ thống · TTS tiếng Việt · CPU/RAM).
RE này **thêm 3 phép đo Kiki-specific**, đều **chỉ đọc / hoàn tác được**:

```bash
# K1 — bản Kiki + trạng thái giấy phép trên xe owner (đừng đoán, CLAUDE.md §9)
adb -s "$VEH" shell dumpsys package ai.zalo.kiki.car | grep -E 'versionName|versionCode'

# K2 — CHỐT dấu hỏi lớn nhất của RE này: text_command có thi hành thật không?
#      Kỳ vọng nếu ĐÚNG: Kiki mở lên và tự làm luôn, KHÔNG mở mic nghe.
adb -s "$VEH" shell am start -n ai.zalo.kiki.car/ai.zalo.kiki.auto.ui.CarMainActivity \
    --es text_command "chỉ đường đến chợ Bến Thành"
adb -s "$VEH" shell logcat -d -s TextCommand   # phải thấy dòng "handeIntent: [...]"

# K3 — service autowake có bind được từ app thường không (chỉ liệt kê, không gọi)
adb -s "$VEH" shell dumpsys package ai.zalo.kiki.car | grep -A3 -i autowake
```

**Cây quyết định sau K2**:
- K2 **chạy** → mở thêm một lựa chọn UX: Kachi bắt được câu nhưng thấy nó thuộc miền của Kiki (nhạc/nav) thì
  **chuyển tiếp nguyên văn** cho Kiki thay vì báo *"không hiểu"*. Vẫn phải coi là **đường không có bảo hành** (7d)
  ⇒ bọc sau một cổng dò, hỏng thì lùi êm về "mở Kiki cho người dùng tự nói".
- K2 **không chạy** → giữ đúng phương án C bản gốc: hai nút, hai trợ lý, không nối chữ giữa hai bên. **Không mất
  gì** — đây vẫn là phương án đã chọn.

### 8.4 Việc kế tiếp (chưa làm — cần owner duyệt)

1. **Spec** `docs/specs/<slug>.html` cho V1 theo phương án C (CLAUDE.md §1 — chưa có spec thì chưa code).
2. Tầng 1: chạy §2.14 + K1–K3 ở phiên lên xe kế tiếp, lưu vào `docs/diagnostics/carlog-kachi-*/`.
3. Chỉ sau khi tầng 1 xanh mới sang `car-integration` → `core` → `app` (CLAUDE.md §14).

---

## 9. Tham chiếu

- Quyết định nút mic + trạng thái đang chạy trên xe: `docs/diagnostics/oncar-handoff-voicekey-2026-08-14.md`
- Phép đo năng lực giọng nói của đầu xe: `docs/diagnostics/oncar-playbook-kachi-1.47.md` §2.14
- Danh mục năng lực (nguồn sinh ngữ pháp): `docs/diagnostics/kachi-capability-catalog-2026-09-10.md`
- Bộ đăng ký `:core` làm hạt giống ngữ pháp: `core/src/main/kotlin/com/byd/clusternav/launcher/`
  (`ControlRegistry.kt` **65 nút** · `TelemetryRegistry.kt` **123 datum** · `ActionMacros.kt` **5 gói lệnh** ·
  `LauncherActions.kt` **3 hành động**)
- Đường nút vật lý hiện có: `core/src/main/kotlin/com/byd/clusternav/voicekey/` ·
  `app/src/main/java/com/byd/clusternav/modules/voicekey/AssistantLauncher.kt` ·
  `app/src/main/java/com/byd/clusternav/Prefs.kt` (`VK_KEYCODE_DEFAULT=328`, `VK_TARGET_DEFAULT="ai.zalo.kiki.car"`)
- Mục backlog: `docs/PROJECT-BACKLOG.md` dòng **V1**
