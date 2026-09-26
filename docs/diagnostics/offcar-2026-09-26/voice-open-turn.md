# Off-car 2026-09-26 — VOICE-OPEN-TURN: câu còn dở thì chưa đóng lượt nghe

> **Trạng thái**: Current · **Cập nhật**: 2026-09-26 · **Mục đích**: thiết kế + số đo của OQ9 (owner: *"làm
> voice-open-turn cho chuẩn"*). Nền: `voice-tail-fuzzy-phonetic.md` (cùng thư mục) §1.4 · §5. Backlog:
> `PROJECT-BACKLOG.md` dòng **OQ9**.

**Một câu**: khi chuỗi vừa nghe **kết thúc bằng một vế dở** (*"mở vietmap vào ô"*, *"chuyển sang hồ sơ"*,
*"dẫn đường tới"*), lượt nghe **không đóng** — micro giữ mở thêm tối đa 1,2 s (trần cứng 1,5 s) để đón vế sau, rồi
hai vế được giải mã **riêng** và ghép ở tầng chữ trước khi phân tích. Câu đủ nghĩa đi đúng đường cũ, **0 ms** thêm.

---

## 0. Kết quả đo, gọn trước

| phép đo | kết quả | lệnh tái lập |
|---|---|---|
| test 5 module | **4 216 test · 0 fail** (+24 test mới) | `:app:testDebugUnitTest :core:test :car-integration:test :vehicle-contracts:test :offcar-planner:test --rerun-tasks --continue`, đếm từ `*/build/test-results/*/*.xml` |
| **thử làm ĐỎ** | **5/24 đỏ** đúng chỗ (4 bài `:core` + 1 bài dây nối) khi gỡ bảng vế dở và gỡ lời gọi `arm.arm(fed)` | xem §6 |
| lint release | **0 error** (513 warning = mốc cũ) | `:app:lintRelease` |
| replay 30 WAV THẬT của xe | **0/30** lượt bị tách vế ⇒ **0 ca đổi hành vi**; 6 ca lệch chuỗi so với log xe đúng bằng 6 ca mà bản 2.73 đã chữa | §4 |
| replay WAV **ghép tay** (quãng ngừng thật + im lặng chèn) | vế trước *"chuyển sang hồ sơ"* → giữ lượt → vế sau *"sau test"* → ghép ⇒ **`Profile(Test)`** | §4.2 |
| harness E2E chuẩn | **T1 105/106** (ca lệch duy nhất `t70` là nhiễu môi trường, chạy lại riêng ⇒ **PASS**) · **T2 23/27 nghe đúng nguyên văn** = **đúng mốc 2.73** | `scripts/emulator/voice-e2e.sh --serial emulator-5554` |

---

## 1. Vì sao cần, và phép đo **bác một nửa** giả thuyết cũ

### 1.1 Điều đã đo trước (nền)

`voice-tail-fuzzy-phonetic.md` §1.4 đo 30 bản thu thật và tách được **hai nhóm** quãng ngừng khác hẳn nhau:

| loại quãng ngừng | giá trị | số ca |
|---|---|---|
| trong cùng một vế (*"mở youtube ⟨…⟩ vào ô số hai"*) | 120 · 120 · 140 · 160 · 160 · 180 · 200 · 260 ms | 8 |
| **ngừng để NGHĨ giữa hai vế** | **720 · 820 · 820 · 1 060 ms** | 4 |

§5 của tài liệu đó ghi một **[ĐOÁN] chưa đo**: *"quãng ngừng-để-nghĩ > 800 ms vẫn CẮT câu ở mặc định 600 ms"*.

### 1.2 ⚠ Đính chính — [ĐO máy ảo 2026-09-26]: **900 ms KHÔNG cắt, 1 000 ms mới cắt**

Dựng WAV từ chính bản thu `…-184201`: `[0..1300] ms` + **G ms im lặng** + `[900..2600] ms`, đẩy qua đúng đường
`cmd wav → VoiceWavProbe → VoiceVad/sherpa` mà phiên mic đi, đọc `KachiVoiceWav`/`KachiVoiceRec`:

| G (im lặng chèn) | điểm ngắt giữa tệp? | bằng chứng (dòng log) |
|---|---|---|
| 700 ms | **không** | một lượt giải mã duy nhất, 58 880 mẫu (cả tệp) |
| 900 ms | **không** | một lượt giải mã duy nhất, 61 952 mẫu (cả tệp) · lặp 2 lượt ra **y hệt** ⇒ không phải nhiễu |
| 1 000 ms | **có** | hai lượt: 34 944 mẫu (vế trước) + 11 968 mẫu (vế sau) |
| 1 200 ms | **có** | hai lượt: 31 872 + 15 552 |
| 2 000 ms | **có** | hai lượt: 31 872 + 16 576 |

⇒ **Cần ≈ 1,0 s im lặng** để Silero chốt một đoạn giữa tệp, không phải 600 ms. Lý do [SUY, khớp số]: đuôi đoạn
mà sherpa trả về **nằm sâu trong khoảng lặng** — với `G = 1 200`, đoạn trước kết ở mẫu 31 872 = **1 992 ms** trong
khi tiếng thật hết ở 1 300 ms, tức +692 ms. Con số ấy khớp đúng bản thu gốc (tiếng hết ~2 040 ms, đuôi đoạn
2 600 ms, +560 ms). Nói cách khác KDoc `VoiceVadTrim.MIN_SILENCE_MS` — *"sherpa cắt đuôi hangover RA KHỎI đoạn"*
— **không đúng với AAR v1.13.8 đang ship**: đoạn giữ lại phần hangover, nên tổng im lặng cần ≈ hangover + ngưỡng
phát hiện ≈ 1,0 s. (Chi tiết này chỉ đổi **cách giải thích**, không đổi một dòng mã nào của bản vá.)

### 1.3 Điều đó đổi gì cho thiết kế — và vì sao vẫn phải làm

- Ba trong bốn quãng ngừng đo được (720 · 820 · 820 ms) **chưa bao giờ bị cắt** ⇒ hôm nay chúng vẫn ra một câu.
- Ca 1 060 ms nằm **đúng biên** ⇒ cắt hay không tuỳ giọng, tuỳ nền cabin. Đây là ca bập bênh, loại tệ nhất cho
  người lái: cùng một câu, hôm nay được hôm sau không.
- Và **tổng** quãng ngừng mà bản vá đỡ được = ngưỡng cắt (≈ 1,0–1,2 s) **+** cửa sổ ghép (1,2 s) ≈ **2,2–2,4 s**
  kể từ lúc hết tiếng vế trước. Tức nó phủ trọn nhóm đã đo **và** còn dư cho người ngừng lâu hơn — thứ mà nâng
  `MIN_SILENCE_MS` không làm được (nâng lên 1 100 là cộng 500 ms vào **mọi** lượt, kể cả câu một vế; và trần 1 200
  vẫn không tới 2,2 s).

---

## 2. Thiết kế

### 2.1 Bảng "vế dở" — **SINH** từ ngữ pháp, không khai tay (CLAUDE.md §7)

`core/.../voice/VoiceOpenTurn.kt` (233 dòng, thuần Kotlin). Năm họ, mỗi họ đọc lại **chính bảng** mà tầng nghe và
tầng chữ đang dùng, nên thêm một cách nói ở đó là tự có vế dở ở đây:

| họ | nguồn | vế dở sinh ra | vì sao nó là DỞ |
|---|---|---|---|
| **[S]** ô | `VoiceSlotPhrases.SPOKEN` **bỏ con số đuôi** · `VoiceLexicon.SLOT_HEADS` × `SLOT_ORDINALS` × giới từ | *vào ô* · *vào ô số* · *ô số* · *ở ô* · *sang ô* · *in slot* · *slot number*… | mệnh đề chỉ ô **bắt buộc** có một con số (`VoiceIntent.OpenApp.slot`); mất con số là mất đối số |
| **[P]** hồ sơ | `VoiceProfileNames.MARKERS` | *hồ sơ* · *profile* | [ĐO xe] **8/8** lượt *"chuyển sang hồ sơ"* rụng đúng cái tên ⇒ cụm đánh dấu đứng cuối = thiếu tên |
| **[N]** nav/nhạc | `VoiceOpenVocab.TRIGGERS`, **lọc bằng bộ phân tích thật** | *dẫn đường tới/đến* · *chỉ đường tới/đến* · *tìm đường tới/đến* · *đường tới/đến* · *đi tới/đến* · *navigate to* · *directions to* · *tìm bài* | cụm ấy chỉ có nghĩa khi *"còn ít nhất một từ đứng sau"* (`VoiceOpenVocab.triggerOf`); đứng cuối = mất cái tên |
| **[B]** *bằng &lt;app&gt;* | `VoiceLexicon.BY_APP_MARKERS` + **cổng tiền tố** | *bằng* · *trên* · *với* · *qua* · *dùng* · *with* · *on* · *using* | cụm đánh dấu chọn app mà không có app |
| **[V]** động từ trần | `VoiceGrammar.VERBS` + **cổng bộ phân tích** | *mở* · *bật* · *đóng* · *dẫn đường* … | động từ chưa có đối tượng |

**Hai cổng dùng bộ phân tích THẬT, không dùng trí nhớ** — vì lấy nguyên bảng thì hai họ tự bắn vào chân mình, và
cả hai ca đều là **chuỗi thật** trong `scripts/emulator/voice-cases.tsv`:

- **[N]**: *"phát nhạc"* · *"mở nhạc"* · *"nghe nhạc"* · *"phát bài"* · *"play"* nằm trong `TRIGGERS` nhưng **tự nó
  đã là một câu lệnh đủ** ⇒ chỉ giữ cụm mà `VoiceIntentParser.parseOne(cụm)` trả `Unknown`. [ĐO] bảng lọc thật:
  12 cụm nav + *tìm bài* được giữ; 7 cụm nhạc (`phát bài` · `phát nhạc` · `mở bài` · `mở nhạc` · `nghe bài` ·
  `nghe nhạc` · `bật bài` · `play`) bị loại vì parse ra `Media`.
- **[B]**: *"quá"* bỏ dấu ra `qua`, trùng một cụm đánh dấu chọn app ⇒ *"hôm nay trời đẹp quá"* sẽ bị coi là dở ⇒
  cổng: **phần câu trước cụm đánh dấu** phải parse ra `Media`/`Nav`/`NavigateSaved`/`OpenApp`.
- **[V]**: *"tạm dừng"* cũng là một cụm động từ trọn vẹn của `VoiceGrammar.VERBS` nhưng **là** lệnh PAUSE ⇒ cổng
  `parseOne is Unknown` phân biệt nó với *"mở"*.

**Lưới an toàn thay cho lời hứa**: `VoiceOpenTurnCasesTest` đọc thẳng `scripts/emulator/voice-cases.tsv` (106 câu
của harness E2E) và bắt buộc **0 câu** bị coi là dở, trừ danh sách ngoại-lệ-kèm-lý-do. [ĐO] đúng **1** ngoại lệ:
`t56 "dẫn đường"` — và chính bộ ca mong đợi `Unknown` cho câu đó, tức nó **đúng là** vế dở.

### 2.2 Cơ chế: giữ micro mở, giải mã vế trước **song song**

Ba cách đã cân; hai cách đầu sai **bằng số**, không bằng cảm giác (KDoc `VoiceOpenTurnArm`):

| cách | điều gì xảy ra |
|---|---|
| đóng mic → giải mã → thấy dở → **mở lại** mic | vế sau bắt đầu +120…460 ms sau điểm ngắt ([ĐO] §1.1) mà chữ chỉ có ở +1 300…2 000 ms ([ĐO xe] `decode_ms`) ⇒ micro đóng đúng lúc người ta đang nói ⇒ **không bao giờ** bắt được vế sau |
| giải mã **trên luồng đọc**, giữ mic mở | `AudioRecord` chỉ đệm `VoiceCaptureDevice.MIN_BUFFER_MS` = 1 s; không đọc 1,3–2 s ⇒ tràn đệm ⇒ mất đúng khúc tiếng của vế sau, **không lỗi nào báo** |
| ✅ **giữ vòng đọc chạy, giải mã ở luồng nền** | vòng đọc giữ nhịp 200 ms như cũ; phần chờ **trùng** với lượt giải mã mà hôm nay vẫn phải chạy ⇒ câu đủ nghĩa về đúng cùng thời điểm như trước bản này |

Sơ đồ một lượt CÓ GIỮ (mốc thời gian lấy từ [ĐO xe] `decode_ms` 779 ms và §1.2):

```
tiếng vế trước      im lặng          tiếng vế sau
├───────────────┤   ├────────────┤   ├──────────────┤
0            1300  ngắt câu ≈2000   2300         3800 ms
                    │
                    ├─ luồng nền: giải mã [0, trimA)  ──┐  (779 ms)
                    ├─ vòng đọc micro VẪN chạy         │
                    │                                   ▼
                    │                        chữ vế trước "…vào ô"
                    │                        dở? ── không ⇒ THOÁT NGAY (bằng đúng lúc bản cũ xong)
                    │                              có  ⇒ hiện chữ lên tấm chữ, chờ tối đa 1 200 ms
                    └─ vế sau chốt ⇒ giải mã RIÊNG [đầu đoạn, hết đoạn) ⇒ ghép ⇒ bộ phân tích
```

**Tranh chấp dữ liệu** — vì sao an toàn, không phải vì sao *chắc là* an toàn: luồng nền đọc `buffer[0, trimA)` của
`VoiceRecognizer`, vòng đọc tiếp tục `accept()` **ghi vào sau** chỉ số `filled ≥ trimA` ⇒ hai vùng không chồng
nhau; `Thread.start()` là mốc happens-before nên luồng nền thấy trọn phần đã gom tại lúc giữ; `minOf(filled,
trimA)` luôn ra `trimA` kể cả khi nó đọc một `filled` cũ. Không có hai lượt giải mã chạy song song (`result()`
`get()` xong vế trước rồi mới giải mã vế sau).

### 2.3 Hai con số, và vì sao là chúng

| hằng (`:core`) | giá trị | lý do |
|---|---|---|
| `VoiceOpenTurn.OPEN_JOIN_WINDOW_MS` | **1 200 ms** | cửa sổ cho **điểm BẮT ĐẦU** của vế sau, tính từ **điểm ngắt câu**. Bốn quãng ngừng đo được (720/820/820/1 060 ms) rơi vào **120–460 ms sau điểm ngắt** (điểm ngắt nổ sau ≈ 600–1 000 ms im lặng) ⇒ 1 200 ms là **2,6×** giá trị lớn nhất, còn dư cho một quãng ngừng chưa ai đo. Vế sau **đã bắt đầu** thì được nói hết bình thường (VAD chốt như mọi lượt, chỉ trần 8 s gác) — không ngắt lời ai bằng đồng hồ. |
| `VoiceOpenTurn.OPEN_MAX_EXTRA_MS` | **1 500 ms** | trần cứng phần chờ ⇒ câu **bị bỏ giữa** (*"mở vietmap vào ô"* rồi thôi) vẫn ra kết quả trong ≤ 1,5 s thêm, bằng đúng hành vi hôm nay (phân tích phần đã có ⇒ hỏi lại *"ô nào"*). Rộng hơn cửa sổ đúng 300 ms = 1,5 khối đọc (`CHUNK_SAMPLES` 200 ms) để cửa sổ 1 200 ms được xét **trọn**. |

**Không đổi**: `VoiceVadTrim.MIN_SILENCE_MS` = 600 (mặc định VAD) · `MIN_SPEECH_MS` · `THRESHOLD` · trần
`VoiceSession.MAX_LISTEN_MS` = 8 s · ba núm `prefs_set` và dải của chúng · không một câu đọc (TTS) nào thêm.

⚠ Trên xe, phần chờ ở ca dở **gần như không thêm giây nào**: nó trùng với lượt giải mã [ĐO xe] 1,3–2 s vốn đã
chạy. Trên máy ảo (giải mã ~50 ms) thì nó hiện ra đủ 1,2–1,5 s — đó là ca **chậm nhất** mà bản vá có thể gây ra.

### 2.4 Phép ghép, và vì sao phải gỡ phần trùng

[ĐO §1.2 của tài liệu nền] cùng bản thu `…-184201`: khúc `[900..2600] ms` ra *"áp **vào ô số một**"*, khúc
`[1400..3400] ms` ra *"**ô số một**"* — vế sau **lặp lại** cái đầu dở của vế trước. Nối thẳng ra *"mở vietmap vào
ô vào ô số một"*: bộ phân tích đọc mệnh đề ô **hai** lần. `VoiceOpenTurn.join` gỡ **dãy trùng dài nhất** ở biên
hai vế, nên cả hai cách nói tiếp cùng ra một câu:

| vế trước | vế sau | ghép | ý định (bộ phân tích THẬT) |
|---|---|---|---|
| mở vietmap vào ô | số hai | mở vietmap vào ô số hai | `OpenApp(VietMap → ô 2)` |
| mở vietmap vào ô | vào ô số một | mở vietmap vào ô số một | `OpenApp(VietMap → ô 1)` |
| chuyển sang hồ sơ | test | chuyển sang hồ sơ test | `Profile(Test)` |
| mở vietmap vào ô | *(rỗng / chỉ từ đệm)* | mở vietmap vào ô | như hôm nay ⇒ hỏi lại |

---

## 3. Cơ chế trong mã (file:line)

| vai | chỗ |
|---|---|
| quyết định *"còn dở không"* + phép ghép + hai con số | `core/src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceOpenTurn.kt:92` (`isOpen`) · `:117` (`join`) · `:74`/`:84` (hai hằng) |
| số học khúc mẫu của vế sau (thuần) | `core/.../voice/VoiceVadTrim.kt:190` (`tailRange`) |
| giữ lượt + luồng giải mã nền + 5 đường thoát | `app/src/main/java/com/byd/clusternav/launcher/voice/VoiceOpenTurnArm.kt:71` (`arm`) · `:89` (`stopReading`) · `:137` (`result`) |
| nối vào vòng đọc micro | `app/.../voice/VoiceCapture.kt:230` (dựng) · `:266` (pha chờ) · `:274` (giữ tại điểm ngắt) · `:283` (`flush` đoạn vế sau) · `:315` (lấy chữ đã ghép) |
| bật cho lượt CHÍNH (cả nút mic **và** `:wake`) | `app/.../voice/VoiceSessionListen.kt:48` (`openTurn = true`) |
| ba câu hỏi của bộ ngắt câu | `app/.../voice/VoiceTurnEndpoint.kt:93` (`openTurnReady`) · `:96` (`segmentCount`) · `:99` (`speaking`) · `:102` (`tailRange`) |
| `isSpeechDetected` của Silero | `app/.../voice/VoiceVad.kt:129` (`speaking`) — [ĐO javap AAR v1.13.8] `public final boolean isSpeechDetected()` |
| giải mã một **dải giữa** khúc đã gom | `app/.../voice/VoiceRecognizer.kt:126` (`rangeResult`) |
| đường đo WAV đi **cùng hai pha** | `app/.../voice/VoiceWavProbe.kt:88` (`onSplit`) · `:191` (`openTurn`) · cột `head`/`tail`/`open_head` ở `TestBridgeWav.kt:48` |

**Một chỗ bật là hai lối vào có** — không phải một câu trong tài liệu mà là **cấu trúc**: nút mic
(`KachiHomeWiring.kt:306`) và *"Hey Kachi"* (`VoiceWakeSessionFactory.kt:30`) đều dựng **cùng** `VoiceSession`, và
`VoiceSession.start` có **đúng một** chỗ khởi động lượt nghe chính (`VoiceSession.kt:191` → `runListen`).
`VoiceOpenTurnWiringContractTest` khoá cả ba vế đó.

**Đường LÙI (RMS) giữ nguyên hành vi cũ**: `openTurnReady()` trả `false` khi không dựng được Silero ⇒ không giữ
lượt. Cố ý: [ĐO xe] bộ RMS *"gần như không bao giờ nổ"* (`chot=4200ms` ở 165/299 lượt) nên ở đó không có điểm ngắt
nào để nối thêm, và mức năng lượng không phân biệt được giọng người với tiếng lốp.

### 3.1 Hai lượt tách tệp theo VAI (trần 500 dòng, CLAUDE.md §4.1)

| tệp mới | tách từ | nội dung | dòng |
|---|---|---|---|
| `VoiceCaptureDevice.kt` | `VoiceCapture.kt` (492/500) | vai *"dựng thiết bị micro"*: `open(ctx)` (AudioRecord + thứ tự nguồn) · `endpointer(ctx)` (bộ RMS theo prefs) · `MIN_BUFFER_MS` | 104 |
| `VoiceSessionListen.kt` | `VoiceSession.kt` (499/500) | vai *"một lượt nghe chính"*: `runListen` — thân hàm **nguyên văn**, chỉ đổi `private fun runSession` ⇒ hàm mở rộng, `TAG` ⇒ `VoiceSession.TAG`, và một tham số `openTurn = true` | 83 |

Cả hai là **pure move** (không đổi một dòng logic). Sáu bài canh đọc **văn bản nguồn** phải đổi theo cho đúng tệp
mới — mỗi bài giữ **nguyên câu hỏi cũ**, không nới assert nào:
`VoiceLoopGuardWiringContractTest` (`AudioRecord(` = 0 ở `VoiceCapture`, 1 ở `VoiceCaptureDevice`; chốt trước khi
mở thiết bị; bíp trước khi mở thiết bị) · `VoiceModelTuningWiringContractTest` (hàm dựng theo prefs · `logHeard`) ·
`VoiceFastNaturalWiringContractTest` (thứ tự nguồn từ `:core`; bảng nguồn viết cứng soi **cả hai** tệp) ·
`VoiceListenWiringContractTest` (chỉ hai tệp mở micro; trần 8 s được truyền vào vòng nghe) ·
`VoiceVadWiringContractTest` (đường đo WAV) · `VoiceWakeStandDownWiringContractTest` (chữ ký `decode`).

Dòng sau tách: `VoiceCapture.kt` **445** · `VoiceSession.kt` **445** · `VoiceSessionTurns.kt` 499 (không chạm) ·
mọi tệp mới ≤ 233.

---

## 4. Replay trên máy ảo — [ĐO 2026-09-26, emulator-5554 API 29]

Bản dựng: `:app:assembleVehicleTest` từ worktree này, `adb install -r`, mô hình **cùng** bản của xe
(`zipformer-vi-int8-2025-04-20`), hồ sơ máy ảo `["Mặc định", "Mặc định 2", "Test"]`. Đường đi:
`am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd wav --es path <wav>`.
WAV nguồn: `perf-oncar-2026-09-26/kachi-voice-20260926-185812-365.zip` — **cục bộ, gitignored, không commit**.

### 4.1 Toàn bộ 30 bản thu THẬT: **0 ca đổi hành vi**

| # | bản thu | log xe (2.70) | máy ảo, bản này | có vế sau? | ý định |
|---|---|---|---|---|---|
| 1 | …-104751 | a lô | a lô | không | Unknown |
| 2 | …-105126 | tắt đèn ban ngày | tắt đèn ban ngày | không | Control |
| 3 | …-142959 | thế nào đèn phạt hay đèn ban ngày em chào anh em | *(không đổi)* | không | Read |
| 4 | …-145510 | mở máy lạnh hai mươi bốn độ | *(không đổi)* | không | Control |
| 5 | …-154559 | mở cửa kính | *(không đổi)* | không | Control |
| 6 | …-154637 | đóng kính lái | *(không đổi)* | không | Control |
| 7 | …-155516 | chửi ghé lại | *(không đổi)* | không | Unknown |
| 8 | …-155803 | dẫn đường đến công ty một bằng… | *(không đổi)* | không | Nav |
| 9 | …-160307 | mát ghế lái mức hai | *(không đổi)* | không | Control |
| 10 | …-201352 | trens tan seat | *(không đổi)* | không | Unknown |
| 11 | …-175145 | đặt vietp vào ô số một | *(không đổi)* | không | OpenApp |
| 12 | …-175208 | đã mở ứng dụng mở | *(không đổi)* | không | Unknown |
| 13 | …-175353 | chỉnh gió mức một | *(không đổi)* | không | Control |
| 14 | …-182054 | mấy giờ rồi | *(không đổi)* | không | Unknown |
| 15 | …-182337 | bật gió tự động | *(không đổi)* | không | Control |
| 16 | …-182529 | đặt yout tiếp vào ô số hai | *(không đổi)* | không | Unknown |
| 17 | …-182614 | mở vietmap **một** | mở vietmap **vào ô số một** | không | OpenApp → ô 1 |
| 18 | …-182724 | mở youtubex vào ô hai | *(không đổi)* | không | OpenApp |
| 19 | …-182920 | mở vietmap | *(không đổi)* | không | OpenApp |
| 20 | …-183051 | mở youtube vào ô số một | *(không đổi)* | không | OpenApp |
| 21 | …-183215 | chuyển sang hồ sơ | chuyển sang hồ sơ **test** | không | Profile |
| 22 | …-183304 | đổi sang hồ sơ | đổi sang hồ sơ **test** | không | Profile |
| 23 | …-183912 | mở vietmap **một** | mở vietmap **vào ô số một** | không | OpenApp → ô 1 |
| 24 | …-184201 | mở vietmap **một** | mở vietmap **vào ô số một** | không | OpenApp → ô 1 |
| 25 | …-184341 | mở vietmap **một** | mở vietmap **vào ô số một** | không | OpenApp → ô 1 |
| 26 | …-184821 | mở youtube vào ô số hai | *(không đổi)* | không | OpenApp → ô 2 |
| 27 | …-185555 | bật gió mức bảy | *(không đổi)* | không | Control |
| 28 | …-185609 | bật gió mức hai | *(không đổi)* | không | Control |
| 29 | …-185656 | tắt đèn ban ngày | *(không đổi)* | không | Control |
| 30 | …-185732 | dẫn đường đến công ty một | dẫn đường đến công ty một về cho mẹ mang tiền về cho | không | Nav |

**Đọc bảng:**
- **0/30 có vế sau** ⇒ bản vá **không chạm** một lượt nào của bộ này: mọi bản thu là một vế liền, quãng ngừng
  trong-vế ≤ 260 ms, đuôi im lặng ≤ 808 ms — đều **dưới** ngưỡng cắt ≈ 1,0 s (§1.2).
- 6 ca lệch chuỗi so với log xe (17 · 21 · 22 · 23 · 24 · 25) là **đúng 6 ca mà bản 2.73 đã chữa**
  (`voice-tail-fuzzy-phonetic.md` §1.5 · §3.3), không phải hồi quy của bản này.
- Ca 30 lệch ở **đuôi tự do** (R16, `VoiceOpenVocab`): log cho thấy lượt ấy chạy **đúng một** độ dài giải mã
  (70 272 mẫu, dùng cho cả lượt ngữ pháp và lượt tự do) ⇒ **byte-identical** với đường cũ; khác biệt là giữa
  **xe 2.70** và **máy ảo** (cửa sổ mic khác, số cụm hotword khác), không phải giữa trước/sau bản vá.

### 4.2 WAV ghép tay — mô phỏng quãng ngừng-để-nghĩ, [ĐO] cả hai vế

Ghép từ **chính bản thu thật** (`python wave`): `[a..b] ms` + **im lặng G ms** + `[c..d] ms`.

| tệp ghép | vế trước (giải mã riêng) | dở? | vế sau (giải mã riêng) | câu ghép | ý định |
|---|---|---|---|---|---|
| `…-183215` `[0..1800]` + **2 000** + `[1500..3200]` | chuyển sang hồ sơ | **có** | sau test | **chuyển sang hồ sơ sau test** | **`Profile(Test)`** ✅ |
| `…-183215` `[0..1800]` + **1 300** + `[1500..3200]` | chuyển sang hồ sơ | **có** | tes | chuyển sang hồ sơ tes | Unknown (vế sau bị **cắt tay** giữa từ — hạn của WAV ghép, không phải của cơ chế) |
| `…-183215` `[0..1800]` + **1 000** + `[1500..3200]` | chuyển sang hồ sơ | **có** | ote | chuyển sang hồ sơ ote | Unknown (cùng lý do) |
| `…-184201` `[0..1300]` + **900** + `[900..2600]` | *(không tách — §1.2)* | — | — | mở vietmap một | OpenApp (đường cũ) |
| `…-184201` `[0..1300]` + **1 200** + `[900..2600]` | mở vietmap | **không** | *(bỏ)* | mở vietmap | OpenApp — **đúng**: vế trước đủ nghĩa ⇒ không chờ |

**Ba điều bảng này chứng minh** (dòng log `noi-tiep (WAV): "<vế trước>" + "<vế sau>" ⇒ "<ghép>"`):
1. cơ chế tách–giải-mã-riêng–ghép **chạy thật** trên audio thật, và đi tới **đúng ý định** (`Profile(Test)`);
2. vế trước **đủ nghĩa** thì vế sau bị bỏ, không có phép ghép nào — đúng yêu cầu số 1;
3. vế sau nghe được hay không là việc của **bộ giải mã**, không phải của cơ chế: ba tệp trên chỉ khác nhau ở chỗ
   cắt tay (`ote` → `tes` → `sau test`), cơ chế xử sự y nhau.

⚠ **Không dựng được** ca *"mở vietmap vào ô"* từ audio thật: [ĐO] mọi cách cắt bản thu `…-182614`/`…-184201` đều
cho ra **cả mệnh đề** *"vào ô số một"* ngay khi vượt mốc 1 900–2 100 ms (slice `[2000..4100]` đã ra đủ câu, slice
`[2000..3900]` chỉ ra *"mở vietmap"*) — chính **hotword mệnh đề ô của 2.73** kéo cái đuôi lên. Đó là một tin tốt
(ca *"vào ô"* trần hiếm hơn lo ngại) và là lý do ca ô được khoá ở **tầng chữ** bằng bộ phân tích thật
(`VoiceOpenTurnTest.ghep ve sau roi phan tich ra dung o`) thay vì bằng một WAV dựng tay.

### 4.3 Harness E2E chuẩn — giữ mốc

`scripts/emulator/voice-e2e.sh --serial emulator-5554`:

| tầng | kết quả | so với mốc 2.73 |
|---|---|---|
| T1 (chữ → ý định, 106 ca) | **105/106** | **bằng** — ca lệch duy nhất `t70 "mở gu gồ máp"`: ý định ĐÚNG (`OpenApp · Mở ứng dụng Google Maps`), chỉ phép kiểm *"app lên màn"* thất bại vì màn đang bị một app khác chiếm. Chạy lại riêng (`--cases` một dòng) ⇒ **1/1 PASS**. Đúng ca + đúng cách xác nhận mà `voice-tail-fuzzy-phonetic.md` §4 đã ghi. |
| T2 (tiếng → ý định, 27 WAV TTS) | **23/27 nghe đúng nguyên văn, 0 FAIL** | **bằng đúng** mốc §4 của tài liệu nền ⇒ bản vá không làm hụt bộ này |

---

## 5. Ranh giới — thứ bản vá này **KHÔNG** làm

1. **Câu đủ nghĩa rồi mới ngừng** (*"mở vietmap"* ⟨ngừng⟩ *"vào ô số hai"*) — **không** ghép, và đó là **chủ ý**:
   chờ ở đó là cộng độ trễ vào mọi lệnh *"mở &lt;app&gt;"*, đúng thứ yêu cầu số 1 cấm. Vế sau ở ca này rơi vào
   **cửa sổ hội thoại** đã có (R9, `voice_follow_up_ms` mặc định 5 000 ms) và được phân tích như một câu riêng.
   🚗 Owner muốn ca này ghép thì đó là một quyết định **khác** (nó đổi độ trễ của mọi câu) — ghi vào backlog, không
   tự làm.
2. **Ba lượt NỐI** (hội thoại · hỏi lại · xác nhận) không bật giữ-lượt: chúng đã có vòng hỏi-đáp riêng, không giữ
   PCM, và câu trả lời ở đó đã biết trước hình dạng.
3. **Đường LÙI RMS** không giữ lượt (§3).
4. **Không** đổi mặc định VAD, trần 8 s, dải ba núm; **không** thêm câu đọc nào; **không** thêm UI nào (chữ vế
   trước đi qua đúng đường `onPartial` mà tấm chữ vẫn dùng).

---

## 6. Chất lượng — số và cách tái lập

```bash
# 5 module (đếm từ XML, không tin dòng console)
GRADLE_PROJECT_DIR=<worktree> gradle-locked-wt.sh :app:testDebugUnitTest :core:test \
  :car-integration:test :vehicle-contracts:test :offcar-planner:test --rerun-tasks --continue
# lint release (0 error)
GRADLE_PROJECT_DIR=<worktree> gradle-locked-wt.sh :app:lintRelease
```

| hạng mục | số |
|---|---|
| test 5 module | **4 216** · 0 fail |
| test mới | **24** — `VoiceOpenTurnTest` 14 · `VoiceOpenTurnCasesTest` 3 · `VoiceOpenTurnWiringContractTest` 7 |
| lint release | 0 error · 513 warning (mốc cũ) |
| dòng tệp production | mọi tệp ≤ 500 (§3.1) |

**Thử làm ĐỎ** (test chưa từng đỏ là test mù — CLAUDE.md §10):

| gỡ gì | đỏ ở đâu |
|---|---|
| `if (HEADS.any { endsWith(words, it) }) return true` trong `isOpen` | `VoiceOpenTurnTest`: *mệnh đề ô mất con số* · *cụm đánh dấu hồ sơ* · *cụm dẫn đường* · `VoiceOpenTurnCasesTest`: *bốn chuỗi bệnh của xe 26/09* — **4 bài** |
| `if (arm != null && arm.arm(fed)) continue` trong `VoiceCapture.listenGranted` | `VoiceOpenTurnWiringContractTest`: *bộ giữ lượt được nối thật vào vòng đọc micro* — **1 bài** |

Khôi phục ⇒ xanh lại (đã chạy).

---

## 7. 🚗 Checklist trên xe

**Cách nói ĐÚNG để thử** (ngừng **sau vế dở**, không ngừng sau một câu đã đủ):

1. *"mở vietmap vào ô"* → **ngừng 1,5 giây** → *"số hai"* ⇒ mong đợi VietMap lên **ô 2**.
2. *"chuyển sang hồ sơ"* → **ngừng 1,5 giây** → *"Test"* ⇒ mong đợi đổi sang hồ sơ *Test*.
3. *"dẫn đường tới"* → **ngừng 1,5 giây** → *"chợ Bến Thành"* ⇒ mong đợi dẫn đường.
4. **Ca đối chứng (phải KHÔNG chậm hơn)**: *"bật gió tự động"* · *"mở vietmap vào ô số hai"* nói liền một hơi ⇒
   phản hồi ra **đúng nhanh như 2.73**, không có một nhịp chờ nào.
5. **Ca bỏ giữa**: *"mở vietmap vào ô"* rồi **im hẳn** ⇒ trong ≤ 1,5 s phải ra hành vi hôm nay (mở VietMap /
   hỏi lại *"ô nào"*), **không** treo tấm chữ.

**Log phải thấy** (`adb logcat -s KachiVoiceTiming`), nhãn ASCII `noi-tiep` grep được:

```
noi-tiep: giữ micro trong lúc giải mã vế trước (<N> mẫu)
noi-tiep: vế dở "mở vietmap vào ô" ⇒ chờ vế sau tối đa 1200 ms
noi-tiep: vế sau đã chốt sau <N> ms
noi-tiep: ghép "mở vietmap vào ô" + "số hai" ⇒ "mở vietmap vào ô số hai"
```

Ca đối chứng (câu đủ nghĩa) phải thấy đúng **một** dòng rồi thoát:

```
noi-tiep: vế trước đủ nghĩa ⇒ đóng lượt (chờ <N> ms)
```

và số `<N>` ấy phải **≤ `giải mã <M> ms`** của cùng lượt (dòng ngay sau) — đó là bằng chứng *"phần chờ trùng với
lượt giải mã"*, tức **0 ms** thêm cho người lái. Không đo được số này off-car (máy ảo giải mã ~50 ms nên phần chờ
lộ ra đủ 1,2 s); đây là **🚗 phép đo duy nhất còn thiếu** của bản vá.

**Ca bỏ giữa** phải thấy một trong hai:

```
noi-tiep: hết cửa sổ 1200 ms, không ai nói tiếp
noi-tiep: trần 1500 ms — thôi chờ
```

---

## 8. Còn lại

- 🚗 **Chưa đo trên xe**: độ trễ thật của ca *"vế trước đủ nghĩa"* (§7) — cần một lượt nói để chốt *"0 ms thêm"*
  bằng số, hôm nay nó là **[SUY]** từ cơ chế (luồng nền) + **[ĐO]** rằng đường cũ vẫn là đúng một lượt giải mã.
- 🚗 **Chưa đo trên xe**: ngưỡng cắt ≈ 1,0 s (§1.2) đo trên **máy ảo**; trên cabin thật (nền ồn) Silero có thể
  chốt sớm/muộn hơn. Đọc `noi-tiep: giữ micro…` xuất hiện sau bao nhiêu ms kể từ `tieng_dut=` để chốt.
- **[CHƯA BIẾT]** vế sau dài hơn phần còn lại của trần 8 s thì bị cắt ở đâu — đường `flush` đã lo (đoạn đang mở
  vẫn vào hàng đợi), nhưng chưa có bản thu nào dài tới mức ấy để kiểm.
- **Đính chính cần mang sang KDoc/tài liệu khác** (§1.2): câu *"sherpa cắt đuôi hangover RA KHỎI đoạn"* ở KDoc
  `VoiceVadTrim.MIN_SILENCE_MS` **không đúng** với AAR v1.13.8 — đoạn giữ lại ~600–700 ms hangover. Việc này
  không đổi một dòng mã nào (phép cắt `head` vẫn cắt ở đuôi đoạn), nhưng nó đổi **con số kỳ vọng** của bất kỳ
  phép đo nào về *"ngừng bao lâu thì bị cắt câu"*.
- **Ngoài scope, đã ghi**: ghép khi vế trước **đủ nghĩa** (§5.1) — cần owner quyết vì nó đổi độ trễ mọi câu.
