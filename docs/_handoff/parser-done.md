# Handoff — VOICE PARSER (⑤ VietMap · D1 an toàn · D2/D3) — DONE off-car

> **Trạng thái**: ✅ off-car **4825 test / 0 đỏ**, **CHƯA commit / CHƯA push** (main-agent gom + ship) · **Ngày**: 2026-09-18
> **Phạm vi**: parser core — `VoiceIntentParser` · `VoiceQuestion` · `VoiceClarify` · `VoiceLexicon` · `VoiceSynonyms` · `SherpaSpokenWords` + bài canh.
> **KHÔNG đụng** `VoiceVadTrim` / `VoiceOverlay` / `VoiceYoutubeResolver` / `VoiceSession*` (stage `iso`) · `SherpaTtsSpeaker` / `PiperTtsService` (stage `tts-isolate`).
> Nguồn: `oncar-voice-cases-findings-2026-09-18.md` (§D1 §D2 §D3) · `oncar-voice-music-vietmap-2026-09-18.md` (§BUG A) ·
> `oncar-piper-crash-binding-2026-09-18.md` (#0) · **và log thô `logs/20260918` (53 phiên `VoiceWavProbe`, tôi tự bung + đọc)**.

---

## 0. ⚠⚠ PHÁT HIỆN QUYẾT ĐỊNH CỦA LƯỢT NÀY — lỗi D1 **không nằm ở lượt phân tích đầu**

Cây làm việc đã có sẵn một lượt chạy trước của stage này (`VoiceQuestion.kt` · `VoiceFeatureGone.kt` ·
`VoiceLogCases0918Test.kt` + sửa 8 tệp). Tôi **không tin** bản đó mà đo lại từng ca, và tìm ra một lỗ mà mọi bài
canh của nó đều không thấy.

**Tôi bung log thô thay vì đọc bảng tóm tắt trong doc.** Tệp của ca nặng nhất
(`/tmp/kachi-logs/20260917-200206-017.json`) ghi nguyên văn:

```json
{ "heard": "tất cả cửa đang khóa hay đang mở",
  "decision": "Control(sunroof=1)", "replies": ["✗ Bật Cửa sổ trời — xe không nhận lệnh"],
  "clarify": true, "follow_up": true }
```

Hai cờ cuối đổi hẳn chẩn đoán: **lượt phân tích ĐẦU không bắn lệnh nào.** Nó ra `NO_OBJECT`, Kachi **hỏi lại**
*"Cửa nào …?"*, người lái đọc một cái tên, và **chính lượt trả lời ấy** mới thành lệnh mở cửa sổ trời. Tức:

* mọi cổng đặt ở lượt đầu — kể cả `VoiceQuestion.isChoice` mà lượt trước thêm — **không đóng được** đường này;
* câu trả lời *"cửa sổ trời"* đứng một mình là một câu ra lệnh **hoàn toàn hợp lệ**, và nó **không còn mang dấu
  hiệu nào** của câu hỏi ban đầu;
* [ĐO] tôi dựng lại chuỗi `parse → ask → combine → parse` trên cây trước khi vá: trả lời *"cửa sổ trời"* ra
  **`Control(sunroof, 1)`** — **đúng y nhật ký trên xe**. Bug vẫn còn sống nguyên.

Ba tệp nhật ký khác cũng phải đọc lại theo cách này:

| `heard` | `decision` trên xe | `clarify` | Nghĩa thật |
|---|---|---|---|
| `kính lái đang mở bao nhiêu` | `Read(window_lf)` *"Kính trước-trái: 0 %"* | **true** | đi qua hỏi lại và **đi đúng** — phải giữ, không được "sửa" |
| `tất cả cửa đang khóa hay đang mở` | `Control(sunroof=1)` | **true** | lệnh ghi sinh ra ở **lượt hai** |
| `áp suất lốp bên trái là bao nhiêu` | `Read(soc)` | false | lỗi ở lượt ĐẦU (tầng chữa chính tả) |

⇒ **Doc `oncar-voice-cases-findings` §D1 mô tả chưa đủ**: nó gán cả ba ca cho lượt phân tích. Nên sửa doc (§8).

---

## 1. ✅ Trạng thái từng việc — [ĐO] trên cây CUỐI

Đo bằng chuỗi THẬT (`parse` → `VoiceClarify.ask` → `VoiceClarify.combine` → `parse`), đúng đường `:app` chạy
(`VoiceSessionTurns.kt:188`). Cột *mốc HEAD* = tôi chạy cùng phép đo trong một **worktree tách rời tại HEAD**
(`git worktree add --detach`, KHÔNG `git checkout` trên cây chưa commit).

| Câu (chuỗi `heard` THẬT) | mốc HEAD | SAU lượt này | ghi vào xe? |
|---|---|---|---|
| `tất cả cửa đang khóa hay đang mở` | lượt2 → **`Control(sunroof,1)`** | hỏi *"Cửa nào — Cửa trước-trái, …"* → **`Read(door_lf)`** | **không** |
| `bật đèn khẩn cấp` | **`Control(trunk,1)`** = mở cốp | `Unknown(FEATURE_GONE)` + *"chưa điều khiển được đèn khẩn cấp"* | không |
| `áp suất lốp bên trái là bao nhiêu` | **`Read(soc)`** = % pin | hỏi *"Áp nào — Áp lốp **trước-trái, sau-trái**, …"* → `Read(tyre_p_fl)` | không |
| `kiểm tra áp suất` | `Unknown` + hỏi *"Áp **cell cao, cell thấp**…"* | hỏi *"Áp nào — Áp lốp…"* → `Read(tyre_p_fl)` | không |
| `chỉ số áp suất lớp` (ASR: *lớp*) | `Unknown` + hỏi *"**Số** nào — Odo tổng hay Số VIN?"* | hỏi *"Áp nào — Áp lốp…"* → `Read(tyre_p_fl)` | không |
| `ghế mất mấy` (ASR: *mất*) | `Unknown(NO_VERB)` | **`Read(seat_vent_state)`** | không |
| `quạt gió đang mất máy` ×2 | `Unknown(MISMATCH)` | **`Read(ac_wind)`** | không |
| `kính lái đang mở bao nhiêu` | hỏi *"Kính nào…"* → (lượt2 chưa nối) | hỏi *"Kính nào…"* → **`Read(window_lf)`** = đúng kết quả trên xe | không |
| `hev đi được bao nhiêu` | `Unknown` + hỏi *"**Hev** gì?"* | `Unknown` + *"Chưa rõ — nói lại giúp"* | không |
| `chỉ số xăng` · `xăng còn bao nhiêu` | `Unknown(NO_OBJECT)` | `Read(fuel_pct)` *(lượt trước đã làm)* | không |
| `nhiệt độ đang bao nhiêu` · `máy lạnh máy lạnh đang bao nhiêu độ` | `Read(inside_temp)` | không đổi | không |
| `kiểm tra dây an toàn` · `gập gương chiếu hậu` · `xe đang sạc pin hay không sạc` · `mở xi nhan phải` | `Unknown`/`MISMATCH` | `FEATURE_GONE` + *"đã bỏ"* / *"chưa điều khiển được"* *(lượt trước)* | không |
| `dẫn đường … bằng/qua/**dùng** vietmap\|vietma\|vietmáp` | *"bằng vietma"* → **GMaps + địa chỉ rác** | `Nav("chợ bến thành", vietmap)` cả 5 cách nói | — |

**[ĐO] 20/20 câu log: không câu nào ghi vào xe.**

---

## 2. Việc MỚI của lượt này (ngoài phần lượt trước đã làm)

### ⑤ — cụm đánh dấu thứ ba: *"dùng &lt;app&gt;"*
`VoiceLexicon.BY_APP_MARKERS` += `"dung"`. `bang`/`qua` đã có; `dùng` là nợ backlog (*app-hint "dùng google map"
chưa bắt*). Bỏ dấu thì `dung` trùng **ba** từ: *"dừng"* (động từ PAUSE), *"đừng"*, *"đúng"* — nó vào được **chỉ
vì** ba cổng của `VoiceTailClause.appAfterMarker` đứng chắn (phải chạm cuối câu · phải khớp tên app đã biết · trần
`LONGEST_SPOKEN`). [ĐO] `dừng nhạc` · `tạm dừng` · `đừng mở youtube` · `phát nhạc trên youtube` **không đổi một ý
định nào**. `vietmáp` là ca khớp **CHÍNH XÁC** (bỏ dấu ra đúng `vietmap`) — không cần phép khớp mờ, ghi rõ trong
bài canh để lần sau khỏi thêm một bảng lẫn âm vô ích.

### D1 — cổng cho **lượt trả lời** (việc quan trọng nhất)
`VoiceClarify.Ask.carry` nay mang một **động từ ĐỌC** khi câu gốc là câu hỏi ⇒ `combine` dán nó vào trước câu trả
lời (*"cửa sổ trời"* → *"xem cửa sổ trời"*) ⇒ lượt hai đi **đường ĐỌC**. Hệ quả [ĐO]:

* trả lời một thứ đọc được ⇒ `Read(door_lf)` — đúng câu hỏi;
* trả lời một thứ chỉ có NÚT (*"cửa sổ trời"*) ⇒ `Read(sunroof_state)` (datum có thật) — **không** lệnh ghi;
* người lái tự thêm động từ hành động (*"mở cửa sổ trời"*, *"mở hết kính"*) ⇒ vẫn nằm trong đường đọc.

Chọn cách này vì nó **dùng lại đường `carry`/`combine` đã có**, nên không thêm một mảnh trạng thái nào vào `:app`
— nơi `VoiceSession.kt` = 498/500 và `VoiceSessionTurns.kt` = 491/500 dòng (cảnh báo của stage `iso`).
Kèm: câu hỏi thì danh sách lựa chọn **ưu tiên mục ĐỌC** (`readsFirst`) — hỏi *"cửa đang khóa hay mở"* mà đưa ra
nút *"Cửa sổ trời"* là mời đúng cái lệnh ghi vừa chặn.

### D1 — hỏi lại **đúng họ**: xếp theo *mức ủng hộ của cả câu*
`VoiceClarify.support(...)` = số từ CÒN LẠI của câu xuất hiện trong cách nói của một mã; đầu họ **và** thành viên
đều xếp theo nó (hoà thì giữ thứ tự câu — `sortedByDescending` ổn định ⇒ mọi câu hỏi lại đang đúng không đổi một
chữ). Chữa 3 ca đo được: *"suất"*+*"lốp"* nâng họ lốp lên trên họ **điện áp cell pin**; *"số"* của cụm dẫn *"chỉ
số"* tự rơi xuống vì không từ nào đỡ.
Kèm `VoiceQuestion.FRAME_WORDS` — **trừ bộ khung câu hỏi ra trước khi đo**. [ĐO] thiếu phép trừ thì *"kính lái
đang mở **bao nhiêu**"* hỏi lại thành *"Đang nào — Tốc độ hay Đèn đọc?"*: hai chữ *"bao nhiêu"* trùng cách nói
*"đang chạy bao nhiêu"* của `speed` nên chúng nâng một họ **không liên quan** lên đầu.

### D1 — không đổi vai danh từ thành động từ
*"&lt;x&gt; gì?"* chỉ dựng khi vị trí 0 **thật là** một cụm động từ, và lấy **cả cụm** (*"kiểm tra"*, không phải
*"kiểm"*). Chữa *"hev đi được bao nhiêu"* → *"**Hev** gì?"*. Câu ra lệnh nay mang chính động từ của nó sang lượt
hai ⇒ *"kiểm tra áp suất"* → trả lời *"áp lốp trước trái"* → `Read(tyre_p_fl)`; trước đây carry rỗng nên câu trả
lời của chính máy rơi vào `NO_VERB` (một tên datum đứng trần không phải một lệnh — cổng `[SOÁT 1.69 · P1]`).

### D2 — chữ hỏi rụng còn MỘT tiếng (`VoiceQuestion.bareAskBody`)
*"ghế mát mức mấy"* → ASR **`ghế mất mấy`** (rụng hẳn chữ *"mức"*). Bỏ dấu thì *mát* = *mất* nên đối tượng vẫn
đúng; thiếu duy nhất **chữ hỏi**. Chữ `may` đứng trần là dấu hiệu KDoc lớp vốn **cấm** (trùng *"máy"*), nên nó chỉ
được nhận trong hình dạng rất hẹp — 4 cổng: từ CUỐI câu · còn ≥ 2 từ phía trước · không động từ hành động ở vị trí
0 · **phần thân phải ra một datum THẬT** (cổng cuối ở chỗ gọi, và là cổng mạnh nhất: hình dạng này chỉ biến câu
thành lệnh **ĐỌC**, không bao giờ thành lệnh ghi).

### D2 — `ac_wind` nhận cách gọi ĐỌC
*"quạt gió đang mất máy"* + *"quạt điều hòa đang mất máy"* (2 lượt THẬT) = *"quạt gió đang **mức mấy**"*. Datum
`ac_wind` mang nhãn *"Mức quạt gió"* nên nó cần chữ *"mức"* mới khớp — mà không ai nói đủ chữ ấy. Cụm trùng nút
`fan` là **hợp lệ** và là cơ chế đã có (`choose` lấy datum cho động từ ĐỌC, nút cho động từ hành động — cùng khuôn
`inside_temp` ↔ `temp`); [ĐO] *"tăng quạt gió"* vẫn là `Control(fan, relative)`.

### Dọn: `READ_LEADS` về **một** chỗ
Cụm dẫn *"chỉ số X"* dời từ `private val` của `VoiceIntentParser` sang `VoiceQuestion.READ_LEADS`, và
`isQuestion` nay tính cả nó. Hai chỗ cần **cùng** câu trả lời (bộ phân tích để đi đường đọc; `VoiceClarify` để
biết lượt trả lời cũng phải đi đường đọc) — hai bản sao của một bảng hai từ là hai bản sẽ lệch, và lệch ở đây
nghĩa là một câu hỏi có thể thành một lệnh ghi. `VoiceIntentParser` **479 → 478 dòng**.

---

## 3. [ĐO] Kiểm — tự chạy lại trên nguồn CUỐI

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 GRADLE_OPTS=-Xmx3g \
  ./gradlew test :app:assembleRelease :app:assembleDebug --rerun-tasks --continue
⇒ BUILD SUCCESSFUL
```

| module | test | đỏ |
|---|---|---|
| `core:test` | 2291 | 0 |
| `app:testDebugUnitTest` | 1170 | 0 |
| `app:testVehicleTestUnitTest` | 1182 | 0 |
| `car-integration` · `offcar-planner` · `vehicle-contracts` | 61 · 99 · 22 | 0 |
| **TỔNG** | **4825** | **0** |

Mốc trước lượt này 4813 (gồm **2** bài probe tạm). Nay **−3 probe + 14 bài mới = 4825**, khớp đúng.
`assembleRelease` xanh ⇒ lint `abortOnError` qua. Đã đọc `*/build/test-results/**/*.xml` để xác nhận hai lớp guard
mới **thật sự có mặt**, không chỉ tin `BUILD SUCCESSFUL`.

⚠ **Bẫy lệnh (brief ghi sai một task, lặp lại từ handoff `iso`)**: `:app:testReleaseUnitTest` **không tồn tại** —
chỉ có `testDebugUnitTest` + `testVehicleTestUnitTest`. Tôi chạy hai task thật (phủ rộng hơn).
⚠ `--tests` không lọc `:core:test` trong cấu hình này và task báo `UP-TO-DATE` khi chỉ đổi filter ⇒ luôn `--rerun`.

### Thử phá — **9 phép, đỏ ĐÚNG CHỖ cả 9** (và một bài canh MÙ đã bị bắt)
Sao lưu **theo tệp** (`cp` → `/tmp/kachi-parser-bak`, KHÔNG `git checkout`), mutate → chạy → phục hồi →
`cmp` **khớp từng byte cả 5 tệp**.

| # | Phép phá | Bài đỏ |
|---|---|---|
| 1 | bỏ `carry = READ_VERB` cho câu HỎI (cổng D1 chính) | `cau hoi lua chon di qua luot hoi lai VAN khong ra lenh ghi` |
| 2 | bỏ phép trừ `FRAME_WORDS` | `cum hoi khong duoc keo cau sang mot ho khac` |
| 3 | quay lại chọn đầu họ theo từ ĐẦU TIÊN | `cau hoi ve lop thi hoi lai ve lop, khong ve cell pin` |
| 4 | bỏ ưu tiên mục ĐỌC cho câu hỏi | `cau hoi thi chi dua ra thu doc duoc` |
| 5 | bỏ `dung` khỏi `BY_APP_MARKERS` | `chon app dan duong nhan ca ba cum danh dau` |
| 6 | bỏ nhánh (a‴) `bareAskBody` | `chu hoi rung con MOT tieng o cuoi cau van doc duoc` |
| 7 | nới cổng (2) của `bareAskBody` xuống 2 từ | `chu hoi mot tieng KHONG duoc cuop cau lenh nao` |
| 8 | bỏ carry động từ của câu ra lệnh | `tra loi cau hoi ve lop thi doc dung datum lop` |
| 9 | quay lại lấy từ đầu câu làm động từ vô điều kiện | `danh tu dau cau khong bi doi vai thanh dong tu` |

⚠⚠ **Phép 7 lượt đầu VẪN XANH** — bài canh của tôi cho cổng (2) là **trang trí**: ba câu *"tắt máy"* / *"mở máy"*
/ *"nổ máy"* đã bị cổng (3)/(4) chặn trước, nên hạ trần xuống 2 mà không ai đỏ. Ca mà **chỉ** cổng (2) đứng chắn
là danh ngữ tiếng Việt kết bằng *"máy"* mà từ trước lại là một datum: **`số máy`** (số điện thoại → `gear`,
nhãn *"Số"*) và **`pin máy`** (→ `soc`). Đã đổi bài canh sang hai câu ấy ⇒ phép 7 đỏ đúng chỗ. *(Luật của dự án
lại đúng một lần nữa: thử phá phải kiểm cả BÀI CANH, không chỉ mã sản phẩm.)*

### GUARD KHÔNG BỊ NỚI
`VoiceCommandWiringContractTest` · `SherpaBiasingCoverageTest` · `VoiceGrammarCoverageTest` ·
`VoiceCommandWiringContractTest.pha NGHE khong day tep nao qua tran 500 dong` — **không sửa một dòng nào theo
hướng yếu đi**. Hai bài đếm đổi số **kèm lý do [ĐO] tại chỗ** (§4). Mọi tệp ≤ 500 dòng
(`VoiceIntentParser` 478 · `VoiceClarify` 354 · `VoiceQuestion` 180 · `VoiceLogCases0918Test` 302).
⚠ Bẫy đã cắn một lần: **chú thích khối Kotlin LỒNG NHAU** — `logs/20260918/` + `*.zip` trong KDoc mở một comment
con và ăn cả phần còn lại tệp (*"Unclosed comment"*). Đừng viết `/` liền `*` trong KDoc.

---

## 4. Số đếm đã đổi (tường minh, kèm [ĐO])

| Hằng | Trước | Sau | Lý do |
|---|---|---|---|
| `VoiceGrammarPhrasesTest.EXPECTED_PHRASES_KEPT` | 361 | **362** | +1 = đúng một cách nói mới *"quạt điều hòa"*; ba cụm kia của `ac_wind` đã có ở nút `fan` và bị `distinct()` gộp |
| `VoiceGrammarPhrasesTest.EXPECTED_ENTRIES` | 2109 | **2110** | +1 = đúng mục cụm ấy; **0** từ đơn mới (`quạt`·`điều`·`hòa` đã có). Cụm `dung` cộng **0** — chữ ấy đã có ở bảng động từ (*"dừng"*) và `CONFIRM_YES` |
| `SherpaSpokenWords.ACCENTED` | — | +`"quat dieu hoa" → "quạt điều hòa"` | `SherpaBiasingCoverageTest` đòi mọi cụm mới có dạng CÓ DẤU (nó bắt đúng chỗ, tôi không thêm mục cho phép) |

`EXPECTED_PHRASES_DROPPED` (217) **không đổi**. **Không** đổi mặc định/khoá prefs nào.

---

## 5. Tệp đã đổi

| Tệp | Đổi gì | Dòng |
|---|---|---|
| `core/…/voice/VoiceClarify.kt` | carry theo câu-hỏi/động-từ · `support` · `readsFirst` · `leadVerb` · `READ_VERB` | 237 → **354** |
| `core/…/voice/VoiceQuestion.kt` | `bareAskBody` · `BARE_ASK` · `FRAME_WORDS` · `READ_LEADS` + `readsLead` | 130 → **180** |
| `core/…/voice/VoiceIntentParser.kt` | nhánh (a‴) · dùng `VoiceQuestion.READ_LEADS` (gỡ bản sao) | 479 → **478** |
| `core/…/voice/VoiceLexicon.kt` | `BY_APP_MARKERS` += `dung` + KDoc ba cổng | 356 → **363** |
| `core/…/voice/VoiceSynonyms.kt` | `ac_wind` ← 4 cách gọi ĐỌC | 270 → **276** |
| `core/…/voice/SherpaSpokenWords.kt` | +1 dạng có dấu | +2 |
| `core/src/test/…/voice/VoiceClarifyQuestionTest.kt` | **MỚI** — 9 bài, chuỗi hỏi-lại END-TO-END | **179** |
| `core/src/test/…/voice/VoiceLogCases0918Test.kt` | +5 bài từ chuỗi `heard` THẬT | 200 → **302** |
| `core/src/test/…/voice/VoiceGrammarPhrasesTest.kt` | 2 hằng đếm + KDoc danh sách cụm đánh dấu | — |

🧹 **Đã xoá 3 tệp probe tạm** (`ZzScratchProbeTest` · `ZzScratchProbe2Test` — hai tệp stage `iso` + `tts-isolate`
đã flag, đúng là của stage này; và `ZzScratchProbe3Test` của tôi). Cây nay **0** tệp probe.

---

## 6. 🚗 CHƯA đo trên xe / rủi ro còn lại

1. **Cả gói parser chưa chạy trên xe.** Mọi kết luận trên là [ĐO] off-car trên **chuỗi `heard` thật**, tức đúng
   chữ mà mô hình in ra — nhưng chưa phải một lượt nói thật.
2. **Cổng D1 dựa vào `Ask.carry`**, tức dựa vào việc `:app` còn gọi `VoiceClarify.combine(ask.carry, heard)`
   (`VoiceSessionTurns.kt:188`). Ai bỏ qua `carry` ở lượt sau là **mở lại** đường mở-cửa-sổ-trời, và bài canh
   `:core` **không thấy**. → nên thêm một bài canh quét nguồn ở `:app` (tôi **không** làm: `VoiceSessionTurns.kt`
   = 491/500 dòng và là tệp của stage `iso`; ghi vào nợ).
3. **Không có lưới an toàn cuối ở tầng thi hành.** Cách bền nhất là `VoiceDispatcher` từ chối `Control`/`Macro`
   khi lượt đang là *trả lời một câu hỏi* — nhưng thông tin ấy hiện chỉ có trong `VoiceSession`. Cần owner chốt
   vì nó chạm đúng hai tệp đang sát trần.
4. **`hev đi được bao nhiêu` vẫn là Unknown** (lịch sự). Đúng brief: [ĐO] bộ đăng ký **không có** datum tầm hoạt
   động chung cho xe hybrid — chỉ `ev_range_km` và `fuel_range_km` riêng. Không map bừa. Nếu owner muốn, đường rẻ
   nhất là cho `"di duoc"` trỏ cả hai rồi hỏi lại *"Tầm nào — EV hay xăng?"*.
5. **`đang chạy bao nhiêu kilomet`** (log, 1 lượt) vẫn Unknown. Gốc: cách nói `speed ← "dang chay bao nhieu"` là
   **dữ liệu CHẾT** — cụm hỏi *"bao nhiêu"* bị `askAt` cắt **trước** khi so khớp nên không cách nói nào chứa
   *"bao nhiêu"* khớp được. Cùng bệnh với `ev_range_km ← "con di duoc bao nhieu"`. Ngoài phạm vi brief → §8.
6. **`gặp gu`** / `lấy do` / `bật đèn hat` / `tắc cơm sao` vẫn Unknown — đó là câu **bị cắt giữa** (finding A,
   `silence_ms` 150). Sửa bằng fix ① của stage `iso`, không phải bằng parser.
7. **Chữ `dung` làm cụm đánh dấu** — an toàn nhờ ba cổng, nhưng nếu trên xe xuất hiện một điểm đến/tên bài kết
   bằng đúng một tên app sau chữ *"dùng"* thì nó sẽ bị cắt. [ĐO] off-car không dựng được ca nào như vậy.

---

## 7. Sai lệch có chủ ý so với brief (4 điểm)

1. **Sửa `VoiceClarify` — brief không nêu tên tệp này.** Nhưng đó là nơi lỗi D1 THẬT sống (§0); vá đúng chữ của
   brief (chỉ lượt đầu) là ship một bản vá đã đo được là **không đóng được bug**.
2. **`áp suất lốp bên trái` ra CLARIFY, không ra `Read(tyre_p_*)`** như bảng brief gợi ý. *"bên trái"* ứng với
   **hai** lốp (trước-trái + sau-trái) ⇒ chọn một là đoán. Theo đúng escape của brief (*"không chắc datum →
   Unknown/clarify"*), và câu hỏi lại nay nêu **đúng hai lốp trái trước**.
3. **Thêm `ac_wind` + cụm đánh dấu `dung`** — ngoài danh sách brief, nhưng cùng ba câu log và cùng họ lỗi.
4. **D3 làm đủ, không hoãn.** Brief cho phép ghi D3 vào handoff nếu phức tạp; lượt trước đã dựng
   `VoiceFeatureGone` nên tôi chỉ xác nhận + thêm chuỗi `heard` thật (`xe đang sạc pin hay không sạc` ·
   `mở xi nhan phải`).

---

## 8. Nợ cho main-agent

* 📄 **Sửa doc `oncar-voice-cases-findings-2026-09-18.md` §D1**: nó gán cả ba ca cho *lượt phân tích*, trong khi
  nhật ký ghi `clarify:true/follow_up:true` cho hai ca ⇒ mô tả gốc chưa đủ (§0). Thêm: *"`kính lái đang mở bao
  nhiêu` đi qua một lượt hỏi lại và ĐI ĐÚNG"* — §D4 hiện ghi nó như một ca một-lượt.
* 📄 `oncar-voice-music-vietmap-2026-09-18.md` §BUG A: hướng fix nay **đã làm** (tách app-selector + khớp mờ);
  ghi rõ *"vietmáp"* khớp CHÍNH XÁC, không cần khớp mờ.
* 📄 `PROJECT-BACKLOG.md` + `project-context.md` chưa có mục cho lượt này. Đóng được nợ cũ:
  *app-hint "dùng google map" chưa bắt* (phần cụm đánh dấu đuôi).
* 🐛 **Dữ liệu chết cần dọn** (ngoài phạm vi, nhưng nên có bài canh): mọi cách nói chứa *"bao nhiêu"* trong
  `VoiceSynonyms` **không bao giờ khớp** được (bị `askAt` cắt trước) — hiện có 2 dòng (`speed`, `ev_range_km`).
  Một bài canh quét bằng máy sẽ chặn dòng thứ ba.
* ⚠ **`VoiceSession.kt` 498/500 · `VoiceSessionTurns.kt` 491/500** — muốn thêm lưới an toàn ở tầng phiên (§6.2/6.3)
  thì **phải tách tệp trước**.
* **KHÔNG commit · KHÔNG push · KHÔNG bump version.**
