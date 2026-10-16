# Voice trên xe 16/10 (3.1.9): câu nhạc không động từ, hỏi lại gập tên bài, Hey Kachi nổ theo giọng dẫn đường

> **Trạng thái**: Current · **Ngày**: 2026-10-16 · **Bản**: 3.1.9 (214) trên xe → sửa cho 3.1.10 (chưa bump, chưa commit)
> **Nguồn**: nhật ký `KachiVoiceSession` / `KachiVoiceRec` / `KachiWakeAsr` của chuyến 16/10 (tiến trình `:wake`) · harness
> `scripts/voice/wake-harness` + `scripts/emulator/voice-e2e.sh` trên máy ảo `clusternav10` (API 29).
> **Mức bằng chứng**: [ĐO] = log xe / đo máy ảo / đọc mã · [SUY] = suy luận khớp hiện tượng · [ĐOÁN] = chưa kiểm.
> Chữ người nói chỉ giữ **hình dạng câu**; tên bài trong test là tổng hợp.

## 1. Câu nhạc không động từ ⇒ `NO_VERB` ⇒ hỏi lại *"… nào — Đèn đọc, Đèn pha, hay Đèn ban ngày?"*

**[ĐO log xe 10:00:58]** mô hình in câu dạng `nhạc <tên> trên youtube` (`<tên>` là MỘT chữ mang dấu, bỏ dấu thì trùng `den`).
`quyết định: … ⇒ không hiểu: NO_VERB` → `hỏi lại (lượt 1): "<Tên> nào — Đèn đọc, Đèn pha, hay Đèn ban ngày?"`. Người lái phải nói
lại cả câu, lần này có *"mở … của …"* ⇒ `Media(QUERY …)` 10:01:08 (đúng).

### 1a. Gốc ở ngữ pháp [ĐO mã]
`VoiceIntentParser.parseTokens`: không có động từ ở vị trí 0 ⇒ `headMatch` chỉ nhận MACRO/CONTROL/LAUNCHER, còn từ-nhạc là
`VoiceTermKind.MEDIA` ⇒ rơi tới nhánh `verbHit == null`, chỉ có `mediaSearch` (*"tìm + từ-nhạc"*) ⇒ `NO_VERB`. Tức một câu có ĐỦ
hai mảnh chỉ mang nghĩa phát (từ-nhạc + mệnh đề *"trên &lt;app nhạc&gt;"*) không có đường nào.

**Sửa (generic, cùng lẽ `VoiceSlotNoVerb`)**: `VoiceMediaNavParse.impliedPlay` — từ-nhạc ở đầu (`VoiceSynonyms.MEDIA_WORDS`) **và**
`VoiceTailClause.appAfterMarker(…, MUSIC)` khớp ⇒ đi đúng `media(PLAY, …)` của câu nói đủ (một đường). Thiếu mệnh đề app nhạc
(*"nhạc to quá"*, *"nhạc &lt;tên&gt;"*, *"… trên vietmap"*) ⇒ vẫn `NO_VERB`. `mediaSearch` dùng chung phép cắt từ-nhạc (DRY).

### 1b. Gốc ở câu hỏi lại [ĐO mã]
`VoiceClarify.ambiguity` → `familyIds(norm)` so trên bản **bỏ dấu**: chữ mang dấu khác thanh (`đen`) = `den` = đầu họ `đèn …` ⇒ hỏi họ
đèn; và nó quét **mọi** token, kể cả chữ của tên bài (từ vựng mở, sẽ giao nguyên văn cho `VoiceTailClause.queryText`).

**Sửa**, trong chính bộ hỏi lại, hai luật:
- `VoiceHomograph.leadsLabel` — token MANG DẤU chỉ là đầu họ của mã mà chữ đầu nhãn VI viết đúng chữ ấy (cùng luật `spelledOk`
  của từ nối / tên đã dạy). Token không dấu ⇒ như cũ (không có dữ liệu để phân biệt).
- `VoiceTailClause.openSpan` — token sau từ-nhạc, và token trước mệnh đề *"trên/bằng &lt;app&gt;"* chạm cuối câu, không bao giờ là
  đầu họ. Bộ hỏi lại không đổi chữ tìm (`queryText` không bị chạm).

Kết quả [ĐO máy ảo, cầu `say`, 3.1.10]: `nhạc Áo Đen trên youtube` ⇒ `Media · Tìm bài «Áo Đen» trên YouTube` (✓ đang phát);
`nhạc Áo Đen` / `đen` ⇒ `Unknown` *"Chưa rõ…"* (không còn *"Đèn nào"*). Test: `VoiceCarLog1016Test` (5 ca; đỏ khi gỡ `impliedPlay`,
khi gỡ `leadsLabel`, khi gỡ `openSpan` — đã chạy từng phép gỡ).

## 2. Hey Kachi nổ theo giọng DẪN ĐƯỜNG

**[ĐO log `:wake` xe]** cửa sổ ASR nền in các câu TTS của app bản đồ: `phía trước có camera` (09:56, 10:21) · `có camera theo dõi` ·
`có camera` · `có camera phản đối` — không nổ; **`chỉ có camera` 10:10:47 ⇒ `WAKE khớp` ⇒ mở phiên nghe** (lối vào `cau-goi`).

**Gốc [ĐO mã `WakeAsrMatcher`]**: `HEAD` chứa `co`/`cu`; luật (d) nhận *một head và một tail bất kỳ trong lõi ≤ 4 từ, không cần liền,
không cần thứ tự*: `chi`(tail) + `co`(head) ⇒ nổ. **[ĐO harness]** cùng gốc còn nổ `có chị gọi` (luật (c), `co`+`chi` liền) và cửa
sổ `chỉ có` của TTS. Đổi nguyên âm a→o/u không phải lỗi nghe của "ka": **0** câu gọi trong golden 23/09 (2 mẻ) và **0/57** tệp harness
ra `co`/`cu`.

**Sửa** (cùng bộ khớp, không lớp mới): (1) khoảng cách âm — bỏ `co`/`cu` khỏi `HEAD` (head giữ nguyên âm "a", chỉ trượt phụ âm đầu);
(2) ranh giới — luật (d) (head/tail **rời** nhau) đòi lõi toàn âm-wake như luật (e); liền nhau (luật (c)) như cũ. Ca giả định
`"co chi"` trong `WakeAsrMatcherTest` (chưa từng đo được) chuyển sang âm.

### Harness [ĐO máy ảo 16/10, TTS `Linh`, cabin yên, engine `asr` = bản ship]
Âm mới thêm vào `neg.txt`: 5 câu × 3 tốc độ (`phía trước có camera` · `chỉ có camera` · `có camera theo dõi` ·
`phía trước có camera giám sát tốc độ` · `có chị gọi`).

| Nhóm | n | 3.1.9 nổ | 3.1.10 nổ | lượt giải mã/tệp 3.1.9 → 3.1.10 |
|---|---|---|---|---|
| câu gọi | 57 | 49 (86,0 %) | **49 (86,0 %)** | 1,82 → 1,82 |
| câu thường cũ | 45 | 0 | 0 | 2,47 → 2,47 |
| câu thường mới (dẫn đường / *có chị*) | 15 | **6 (40 %)** | **0** | 3,20 → 4,00 ¹ |

¹ Không phải chi phí thêm: ở 3.1.9 lượt nổ nhầm cắt giải mã sớm (rồi mở cả một phiên lệnh). Chữ ASR từng cửa sổ của lượt chạy
3.1.9 lưu thành bài khoá `WakeAsrHarnessTextsTest` (`core/src/test/resources/voice/wake-harness-texts-2026-10-16.tsv`): câu gọi
không được tụt dưới 49/57, câu thường 0 nổ; đỏ khi trả `co` vào `HEAD`.

⚠ [SUY] TTS máy Mac ≠ giọng dẫn đường + loa + mic xe; nổ nhầm trên xe phải soi lại ở chuyến sau (`KachiWakeAsr: WAKE khớp`).

## 3. Ba dòng P3 của soát Pass 9

| ID | Gốc [ĐO mã] | Sửa | Bài đỏ khi gỡ |
|---|---|---|---|
| `INK-COALESCER-EMPTY-TURN` | `InkCoalescer.turn` gọi `arm()` cho từng mặt ⇒ mỗi lô thêm một lượt hẹn rỗng chỉ để `ran.clear()` | `turn` không tự hẹn; `request` xoá `ran` khi không hẹn nào chờ và không ở trong lượt hẹn; lời xin sinh ra TRONG lượt vẫn dồn sang lượt kế | `InkSolveGateTest` 5 · 5b |
| `WAKE-TAIL-STALE-FINISH` | `tailPending` sống qua YIELD/SUSPEND/HANDED ⇒ khung im đầu của lượt nghe mới phát `FINISH` trên cửa sổ cũ | `VoiceWakeController.onMicOpened()`; `VoiceWakeListener.inner` gọi một lần mỗi lần mở micro | `VoiceWakeControllerTest` *mo lai micro …* |
| `VOICE-CAPTURE-EP-OUTSIDE-TRY` | `VoiceTurnEndpoint.open` sau khi cầm micro, trước `try/finally` | `open` ném ⇒ `closeRecord` rồi ném tiếp | `VoiceLoopGuardWiringContractTest` *dung bo ngat cau nem …* |

## 4. Chi phí (owner: *"đừng vá trên vá làm chậm performance"*)
- `:wake` (đường nóng): **0 lượt giải mã thêm, 0 nhịp, 0 luồng** — bảng §2 (1,82 / 2,47 lượt/tệp không đổi). Bộ khớp thêm một phép
  `all` trên ≤ 4 từ chỉ khi đã có head+tail rời nhau; `onMicOpened` là một phép gán mỗi lần mở micro.
- Phiên lệnh: `impliedPlay` / `openSpan` / `leadsLabel` chỉ chạy trên câu đã rơi xuống nhánh không-động-từ hoặc câu hỏi lại.
- `InkCoalescer`: **bớt** một `Handler.post` mỗi lô.

## 5. Kiểm
- Gradle `:core:test :app:testDebugUnitTest :app:testVehicleTestUnitTest --continue` → `count-tests.py`: 10 720 test, 0 hỏng.
  (Lượt đầu có `BoundedCastExecutorBudgetTest` đỏ một lần dưới tải, xanh 2/2 khi chạy riêng — không thuộc tệp nào sửa ở đây; ghi sổ.)
- `voice-e2e.sh` máy ảo: T1 131/135 trước = 131/135 sau (4 ca hỏng cả hai bản: app đích chưa cài trên máy ảo); T2 26/34 = 26/34,
  ý định T2 trùng từng ca. Máy ảo `clusternav10` được đặt `persist.sys.locale=vi-VN` để harness chạy (harness đòi giọng tiếng Việt).
- Việc 🚗: soi `WAKE khớp` khi dẫn đường đọc cảnh báo camera; nói câu nhạc không động từ có *"trên youtube"*.

## 6. 3.1.10 bổ sung: *"video"* là từ chỉ loại · câu hỏi *"gì"* · bài test cast đỏ thất thường

### 6a. `VOICE-MEDIA-VIDEO-WORD` [ĐO mã + máy ảo]
`"video"` không có trong từ vựng nào ⇒ *"video &lt;tên&gt; trên youtube"* `NO_VERB`, *"tắt video"* `Không tìm thấy`, *"phát tiếp video"*
`Tìm bài «tiếp video»`. Sửa: `"video"` vào **cuối** `VoiceSynonyms.MEDIA_WORDS` (một bảng ⇒ `impliedPlay` · `mediaSearch` · động từ nhạc
cùng nhận, không đường thứ hai) + `SherpaSpokenWords.NO_VI_FORM` (chưa đo mô hình VN có in token `VIDEO` không ⇒ **không** bias, tệp
hotword không đổi ⇒ T2 không đổi). Ngữ pháp Vosk +2 mục (`video` · `vidéo`, `VoiceGrammarPhrasesTest` 2040 → 2042, đọc từ actual).
Không cướp: *"video to quá"* · *"video &lt;tên&gt;"* · *"… trên vietmap"* vẫn hỏi lại; *"mở youtube"* · *"tắt camera"* không đổi.

### 6b. `VOICE-QUESTION-TAIL-GI` [SUY từ mã → ĐO máy ảo]
Sửa ở **tầng câu hỏi** (không chạm `impliedPlay`): `VoiceQuestion.whatAt` nối sau `ASK_PHRASES` trong `askAt` ⇒ `isQuestion`,
`VoiceClarify`, `VoiceBareCover` cùng thấy. `gì` (`gi`) KHÔNG quét mọi vị trí (tên bài có *"gì"* rất thường); là câu hỏi khi **không có
động từ hành động ở đầu** (sau tiếng đệm) **và** `gi` là từ cuối HOẶC đứng ngay sau từ chỉ loại `MEDIA_WORDS` ở đầu câu
(*"nhạc gì …"* · *"bài hát gì"* · *"video gì"*). Tên bài có *"gì"* vẫn phát: có động từ (*"mở bài &lt;… Gì …&gt;"*), hoặc chữ của tên
chen giữa (*"nhạc &lt;… Gì …&gt; trên youtube"*). Ca mơ hồ thật (tên MỞ ĐẦU bằng *"Gì"*, không động từ) nghiêng về hỏi = không làm gì.
Giữ nguyên: câu có động từ *"mở nhạc gì …"* (cổng 1, như `isChoice`). Câu hỏi về bài đang phát ra `Unknown` (*"Không tìm thấy …"*) —
chưa có datum "đang phát" để đọc.

### 6c. Bằng chứng giọng nói [ĐO máy ảo `clusternav10` API 29, vi-VN, vehicleTest dựng từ cây làm việc]

| Bộ | trước | sau |
|---|---|---|
| `voice-e2e.sh` T1 (135 ca) | 131/135 | **131/135** — ý định + chuỗi xem trước trùng TỪNG ca |
| `voice-e2e.sh` T2 (34 WAV) | 26/34 | **26/34** — `heard` + ý định trùng từng ca |
| 7 câu mới qua `--cases` (cầu `say`) | 3/7 | **7/7** |

7 câu mới, sau: *"video Mưa Phố Cũ trên youtube"* ⇒ `Tìm bài «Mưa Phố Cũ» trên YouTube` · *"tắt video"* ⇒ `Dừng nhạc` · *"phát tiếp
video"* ⇒ `Phát nhạc` (đã mở YouTube) · *"video to quá"* ⇒ hỏi lại · *"nhạc gì đang phát trên youtube"* (trước: `Tìm bài «gì đang
phát»`) và *"bài gì đây"* ⇒ `Unknown` · *"mở bài Em Làm Gì Đó trên youtube"* ⇒ `Tìm bài «Em Làm Gì Đó»` (cả hai bản). Tên bài tổng hợp.
Test: `VoiceVideoWhatQuestionTest` (5 ca; đỏ khi gỡ `"video"` — 3 ca, khi gỡ `?: whatAt(t)` — 1 ca; đã chạy từng phép gỡ).

### 6d. `TEST-FLAKY-CAST-BUDGET` [ĐO, tái hiện]
Thân bài cũ (giờ thật) lặp 300 lần dưới 40 tiến trình `yes`: 0 đỏ. Thêm tải TRONG JVM (48 luồng quay + rác liên tục, mô phỏng ba bộ test
song song + GC): **3/200 đỏ**, cả ba ở `assertTrue("cast" in timeouts)` khi `shortRun` đã là *"bị ngắt"*. Gốc [ĐO mã]: luồng hẹn giờ làm
hai bước `future.cancel(true)` **rồi** `onTimeout` — bước 1 ngắt thân, thân đếm latch, luồng test đọc `timeouts` trước khi luồng hẹn giờ
(bị tải đẩy ra) tới bước 2. Đó là đua của bài test, không phải lỗi mã chạy (dòng `TIMEOUT:` có thể đứng sau dòng log của thân bị ngắt —
chỉ là thứ tự log).

Sửa bài (không nới khẳng định, không ngủ/thử lại): `BoundedCastExecutor` nhận `timer: CastTimer` (mặc định = scheduler chung, bản phát
hành không đổi); bài dùng bộ hẹn giờ tay, hạn bắn **đồng bộ** trên luồng test sau khi thân đã chạy (latch), thân chờ latch thay vì ngủ.
Bài *"quá hạn mà thân còn chạy ⇒ BẬN"* cũng chuyển sang bộ hẹn giờ tay (cùng họ: `Thread.sleep(300)` chờ hạn 100 ms).
**Bằng chứng**: 20 lượt Gradle liên tiếp (`--rerun`) dưới 30 tiến trình `yes` + cùng tải trong JVM; mỗi lượt = cả lớp (6) + 300 lần lặp ba
bài mới ⇒ **6 120 lần chạy, 0 đỏ** (bản cũ: 3/200 dưới cùng tải trong JVM). Bài lặp tạm đã xoá khỏi cây.

Lộ ra khi soi [ĐO mã — lỗi thật, có từ trước, không phải gốc của lần đỏ]: hạn tính từ lúc GỬI, nên thao tác chờ sau một thao tác dài
(lượt mở 25 s) có thể **hết hạn khi chưa chạy** ⇒ bị huỷ **im lặng** (không `TIMEOUT` — đúng F4 — nhưng cũng không `DISCARDED`), trái
hợp đồng R5 *"mỗi lần bỏ đều báo"*; hàng gộp −/+ chỉ biết nhờ đoán `staleMs` ở lần bấm sau (ô `busy` kẹt tới lúc đó). Sửa: nhánh
`started == false` của bộ hẹn giờ gọi `reportDiscard(…, "hết hạn … khi còn chờ trong hàng (chưa từng chạy)")` ⇒ log `DISCARDED:` + hàng
gộp nhận `DROPPED` ngay (người nghe vốn tự `post` về luồng chính). `shutdown()` huỷ các thao tác còn chờ để bộ hẹn giờ không báo sau
khi đã tắt. Bài `thao tac het han khi con cho thi bao BO, khong TIMEOUT gia, khong chay` đỏ khi gỡ nhánh ấy. Chi phí: 0 luồng, 0 nhịp.
