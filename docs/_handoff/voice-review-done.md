# SOÁT SENIOR — GÓI FIX VOICE (tts-isolate · iso · parser) — **APPROVED**

> **Ngày**: 2026-09-18 · **Vai**: senior architect · **Phạm vi**: toàn bộ cây chưa commit trên
> `feat/voice-hotword-phrases` (HEAD `23a6d2f`) — 3 handoff `docs/_handoff/{tts-isolate,iso,parser}-done.md` + `git diff`.
> **KHÔNG commit · KHÔNG push · KHÔNG bump version** (main-agent gom + ship).
> **[ĐO] FULL 5 module: 4829 test · 0 đỏ · 0 lỗi · 0 bỏ** (§4) · `:app:assembleRelease` + `assembleDebug` xanh.

---

## 0. Kết luận một câu

Ba stage làm đúng việc của chúng, nhưng **cổng D1 — thứ quan trọng nhất của cả gói — để hở đúng hình dạng câu hỏi
có/không phổ biến nhất của tiếng Việt**, và lỗ đó dẫn lại **chính tai nạn của nhật ký xe** (mở cửa sổ trời / mở cốp
trên xe đang chạy) chỉ qua một câu khác. Đã vá (4 bản vá), khoá bằng 4 bài canh **đã chứng minh đỏ được**, và thu hẹp
số ca tấn công **97 → 2 hình dạng còn lại** (cả hai đã ghim + đẩy lên owner).

---

## 1. Findings

| # | Sev | Việc | Trạng thái |
|---|---|---|---|
| F1 | **[P0]** | Cổng D1 không thấy dạng *"&lt;đối tượng&gt; đang &lt;trạng thái&gt; **không/chưa**"* ⇒ lượt trả lời ra **lệnh ghi** | ✅ VÁ |
| F2 | **[P0]** | `Ask(vague(), emptyList())` — carry **rỗng cứng** kể cả khi câu gốc ĐÃ nhận ra là câu hỏi | ✅ VÁ |
| F3 | **[P0]** | `looksAsked` (bản vá F1 của chính tôi) đo trên bản **đã lọc tiếng đệm** ⇒ dạng *"A **hay** B"* không bao giờ được đỡ | ✅ VÁ |
| F4 | **[P2]** | `RemotePiperSpeaker` bind hỏng ⇒ **rò `onDone`** của câu thứ hai (hai luồng gọi) ⇒ treo im lặng | ✅ VÁ |
| F5 | **[P2]** | Còn lại: *"mở cửa sổ trời **chưa**"* (động từ hành động ở vị trí 0) vẫn là **lệnh ghi** ở lượt 1 | ⚠ GHIM + owner |
| F6 | **[P3]** | Còn lại: câu ghép có dấu hiệu hỏi **ở giữa** (*"… mở không **và** kính thì sao"*) | ⚠ GHI, không vá |
| F7 | **[P3]** | `VoiceIntentParserTest.kt` = **501 dòng** (vượt trần 500, không guard nào thấy) | ✅ VÁ (499) |
| F8 | **[P3]** | 4 bài canh mới của chính lượt soát **MÙ** ở lượt thử phá đầu | ✅ VÁ (đổi sang `assertEquals`) |
| F9 | **[P3]** | Sai số kế toán trong handoff `parser` (số đếm hằng) | 📄 ghi lại |

### F1 · [P0] — **cổng D1 để hở, và lỗ đó dẫn lại đúng tai nạn của nhật ký**

Cổng D1 dựa vào `VoiceQuestion.isQuestion` = `askAt || isChoice || readsLead`. `isChoice` đòi **≥ 2** từ ngữ cảnh
(`đang`·`có`·`hay`·`chưa`) khi câu không có chữ `hay`. Ca của nhật ký (*"tất cả cửa đang khóa **hay** đang mở"*) có
`hay` + hai chữ `đang` ⇒ qua. Nhưng **dạng hỏi có/không phổ biến nhất chỉ có MỘT từ ngữ cảnh** ⇒ `context = 1 < 2` ⇒
không phải câu hỏi ⇒ `Unknown(NO_VERB)` ⇒ hỏi lại với **carry rỗng** ⇒ câu trả lời đứng một mình = một lệnh ghi hợp lệ.

**[ĐO] tôi dựng probe đi hết chuỗi thật `parse → VoiceClarify.ask → combine → parse`** (25 câu hỏi × 14 câu trả lời,
trong đó có đúng những cái tên mà bản 1.76 đã biến thành lệnh):

```
mốc GIAO CỦA 3 STAGE  ⇒ 97 ca ghi vào xe
«cửa sổ trời đang mở không»  + «cửa sổ trời» ⇒ Control(sunroof,1)   ← mở cửa sổ trời, xe đang chạy
«cốp đã mở chưa»             + «mở cốp»      ⇒ Control(trunk,1)
«xe đã khóa cửa chưa»        + «rời xe»      ⇒ Macro(mac_leave)      ← đóng kính + khoá xe
«điều hòa đang bật không»    + «khóa xe»     ⇒ Control(lock,1)
«kính hạ hết chưa» · «hạ hết kính chưa»      ⇒ nt (12 lệnh ghi mỗi câu)
```

Tức bản giao **chưa đóng** được lỗi mà nó sinh ra để đóng — chỉ dịch nó sang một hình dạng câu khác.

**Vá** (`VoiceQuestion.isChoice` cổng (4)): đuôi `không`/`chưa` + **≥ 1** từ ngữ cảnh + **không có động từ HÀNH ĐỘNG
ở vị trí 0**. Điều kiện cuối giữ nguyên đúng ca mà cổng (3) sinh ra để bảo vệ (*"bật đèn đọc không"* = một lệnh đang
chạy đúng). Tách `actionAtHead` dùng chung với `bareAskBody` (bản cũ chép logic ấy tại chỗ).

**Lợi thêm, [ĐO]**: lượt 1 nay **trả lời** thay vì nói *"không hiểu"* —
`«cửa sổ trời đang mở không» ⇒ Read(sunroof_state)` · `«điều hòa đang bật không» ⇒ Read(ac_on)` ·
`«đã mở cửa sổ trời chưa» ⇒ Read(sunroof_state)`.

### F2 · [P0] — carry rỗng CỨNG ở đường *"Chưa rõ — nói lại giúp"*

`VoiceClarify.ask` kết bằng `return Ask(vague(), emptyList())`. [ĐO] *"cốp đã mở chưa"* là câu hỏi **đã nhận ra được**
(sau F1) nhưng họ *"Cốp …"* không đủ hai nhãn để hỏi lại ⇒ nó rơi xuống đúng dòng ấy ⇒ carry rỗng ⇒ trả lời
*"cửa sổ trời"* ra `Control(sunroof,1)`. Vá: dòng đó nay cũng đi qua `carryFor(...)`.

Đánh đổi đã cân và ghi tại chỗ: sau câu *"nói lại giúp"* người lái thường **hỏi lại**; nếu họ ra lệnh thật thì lượt
ấy thành một câu ĐỌC (mất một lượt nói) — rẻ hơn hẳn một lệnh thân xe không ai xin.

### F3 · [P0] — **bản vá đầu của CHÍNH TÔI hỏng, probe bắt được**

Tôi thêm `VoiceQuestion.looksAsked` (dấu hiệu hỏi yếu: đuôi `không`/`chưa`, hoặc `hay` không ở vị trí 0) làm đường
lùi cho `carryFor`. [ĐO] nó **không bao giờ khớp** dạng *"A hay B"*: `ask()` đo trên bản **đã lọc `FILLERS`**, mà
`hay` nằm trong `FILLERS` (vai *"hãy"*) ⇒ dấu hiệu bị cắt trước khi nhìn tới.

```
trước:  «kính hay cửa» ⇒ ask="Kính nào — …?" carry=[]   + «cửa sổ trời» ⇒ Control(sunroof,1)
sau:    «kính hay cửa» ⇒ ask="Kính nào — …?" carry=[xem] + «cửa sổ trời» ⇒ Read(sunroof_state)
```

Vá: `ask()` tokenize **một lần** (bản cũ tokenize hai lần), đo `isQuestion` **và** `looksAsked` trên bản **CHƯA lọc**,
rồi truyền `asked` xuống `ambiguity`/`carryFor` như một cờ riêng. **Không** gộp vào `asking` — `asking` còn điều khiển
`readsFirst`, gộp là đổi thêm hành vi không ai xin.

### F4 · [P2] — `RemotePiperSpeaker` bind hỏng ⇒ rò `onDone`

`bound = true` được đặt **trong khoá, TRƯỚC** `bindRemote()` (cố ý — để hai luồng không cùng bind). Câu thứ hai tới
giữa hai bước ấy thấy `bound == true` ⇒ chỉ xếp vào `queued`/`pending` rồi trả `true`. Đường bind-hỏng cũ làm
`queued = null` + `settle(my)` ⇒ **`pending` của câu thứ hai không bao giờ được gọi** = đúng cái treo im lặng cả lớp
sinh ra để chặn. Hai luồng gọi là chuyện thật (luồng vẽ của `VoiceSession` + luồng `KachiClip` của `ClipSpeaker` —
chính KDoc `lock` nói thế). Vá: đường hỏng đi qua `onRemoteGone()` (mở **mọi** sổ + unbind + `bound = false`).
Bài canh `bind hong thi dong so ngay va tra false` đổi từ `settle(my)` sang `onRemoteGone()` — **chặt hơn**.

### F5 · [P2] — CÒN LẠI, đã ghim, **cần owner chốt**

```
«mở cửa sổ trời chưa» ⇒ Control(sunroof,1)      (isQuestion = false)
```

Câu mở đầu bằng **động từ hành động** + đuôi `chưa`. Tiếng Việt thì nó nghiêng về câu **hỏi**; nhưng [ĐO log xe] mô
hình có **thêm/rụng từ ở đuôi câu**, nên coi nó là câu hỏi sẽ làm **câm một lệnh nói đúng**. Tôi **không tự đổi**
(brief: *"KHÔNG đổi mặc định ngầm"*) và **không chứng nhận nó an toàn**. Đã ghim bằng
`assertEquals(Control("sunroof",1), one("mở cửa sổ trời chưa"))` kèm chú thích *"đổi chiều là quyết định của owner"*,
để nó không tự lật lặng lẽ. → **owner chốt**.

### F6 · [P3] — CÒN LẠI, không vá có chủ ý

`«cửa sổ trời đang mở không và kính thì sao»` — dấu hiệu hỏi nằm **giữa** câu (câu ghép), `looksAsked` chỉ soi đuôi
câu + `hay`. Nới sang *"dấu hiệu ở bất kỳ đâu"* thì `không` trong *"lọc **không** khí"* / *"điều hòa **không** khí"*
bắt đầu ăn vào các câu đang chạy đúng. Ca này do **tôi bịa ra**, không có trong log ⇒ đổi một rủi ro thật lấy một
lợi ích giả. Ghi lại kèm chuỗi tái lập chính xác.

### F7–F9 · [P3]

* **F7** `VoiceIntentParserTest.kt` 501 dòng (stage parser cộng +3 làm nó vượt trần; `pha NGHE khong day tep nao qua
  tran 500 dong` chỉ quét mã sản phẩm nên không thấy). Gom 4 dòng KDoc còn 2 ⇒ **499**.
* **F8** 4 bài canh mới của tôi **MÙ** ở lượt thử phá đầu: chúng chỉ đòi *"không phải lệnh ghi"*, mà gỡ cổng (4) thì
  câu rơi về `Unknown` — cũng không ghi gì ⇒ vẫn xanh. Đã thêm hai bài **`assertEquals`** (`Read(sunroof_state)` ·
  `Read(ac_on)`) + một bài riêng cho dạng *"A hay B"*. *(Luật của dự án lại đúng: thử phá phải kiểm cả BÀI CANH.)*
* **F9** Handoff `parser` §4 ghi `EXPECTED_PHRASES_KEPT` 361→362 và `EXPECTED_ENTRIES` 2109→2110; so với **HEAD** thì
  diff thật là **359→362** và **2099→2110** (con số của handoff là delta trên một lượt chạy trước đã có trong cây).
  Lý do [ĐO] **có** viết tại chỗ trong tệp test cho cả hai. Không phải lỗi mã — sửa khi gom doc.

---

## 2. Bảy mục brief yêu cầu — trạng thái

| # | Mục | Kết quả |
|---|---|---|
| 1 | **#0 `onDone` LUÔN fire, kể cả khi `:tts` chết** | ✅ sau F4. Rà **mọi** chỗ đụng `pending`: thêm (`speakInternal`) · `superseded` → `fire` · `onRemoteGone` → `toList()+clear()+fire` từng cái · `settle` → `fire` · `stop` → fire hết · `shutdown` → `stop()` trước. `onServiceDisconnected`/`onBindingDied`/`onNullBinding` **cả ba** vào `onRemoteGone`. `sendSpeak` ném ⇒ `onRemoteGone` ngay (không chờ callback trễ). Ca duy nhất không phủ: tin `MSG_SPEAK` **thiếu id** ⇒ `:tts` không báo được cho ai — chỉ xảy ra nếu tin dựng sai ở đầu kia, đã log + ghi KDoc. |
| 1b | `PiperTtsService` `process=":tts"` trong manifest | ✅ có, `exported="false"`, không `<intent-filter>`/`<property>`. **[ĐO thử phá]** gỡ `android:process` ⇒ đỏ đúng bài. |
| 1c | `SherpaTtsSpeaker` chỉ chạy trong `:tts` | ✅ Router cầm `RemotePiperSpeaker`; **[ĐO thử phá]** trả Router về `SherpaTtsSpeaker(ctx)` ⇒ **2 bài đỏ**. `KachiApplication` chặn `:tts` nạp mô hình NGHE (74 MB) + VAD — cổng đứng **trước** `AppContainer.get`. |
| 1d | Không chặn luồng gọi | ✅ `Messenger` một chiều, `bindService` bất đồng bộ, `onDone` về luồng chính qua `post`. Còn lại: `available()` stat 3 tệp trên luồng gọi mỗi câu — **y như bản 1.78** (`filesPresent`), không phải hồi quy. |
| 1e | `dead = true` sau `shutdown` có giết Piper vĩnh viễn? | ✅ không: `VoiceSession` (và máy đọc) dựng **theo Activity** (`KachiHomeWiring.voiceSession` + `voiceLazy`), và `SherpaTtsSpeaker.shutdown()` bản 1.78 **cũng** `dead.set(true)` ⇒ ngữ nghĩa không đổi. |
| 2 | **D1 AN TOÀN — grep + thử phá** | ✅ sau F1–F3. `writesToCar` phủ đúng hai loại ghi thân xe (`Control` · `Macro`) trong 11 nhánh `VoiceIntent`. Đường câu ghép: `parseTokens` chạy **từng vế** nên bốn nhánh hỏi (a)/(a″)/(a‴)/(a′) vẫn gác từng vế; vế nào Unknown ⇒ cả câu đi qua `fuzzy` với **token đầy đủ** ⇒ cổng câu-hỏi có hiệu lực. **[ĐO] 97 → 2 hình dạng còn lại** (F5, F6). |
| 3 | **endpoint 600 không phá test** | ✅ `MIN_SILENCE_MS` 150→600; dải `MIN/MAX_MIN_SILENCE_MS` **80–800 không đụng**; `VoiceVadTrimTest` ghim 600 + sàn ≥ 500. **[ĐO thử phá]** hạ về 150 ⇒ **2 bài đỏ**. Lý do *"head-trim không dính núm"* có [ĐO] từ nguồn sherpa v1.13.8 (`voice-activity-detector.cc`), đã tự đối chiếu. |
| 4 | **overlay immersive không mất Back** | ✅ `FLAG_NOT_FOCUSABLE` **không** có (bài canh `assertFalse`), `isFocusableInTouchMode = true`, `dispatchKeyEvent` KEYCODE_BACK → `onCancel()`, `dimAmount = 0f`, không `FLAG_DIM_BEHIND`; cổng chặn vòng lặp `vis and FULLSCREEN == 0` có. **[ĐO thử phá]** gỡ `IMMERSIVE_STICKY` ⇒ đỏ đúng bài. |
| 5 | **file ≤ 500 dòng** | ✅ sau F7. Cao nhất: `VoiceSession` **498** · `VoiceSessionTurns` **491** · `VoiceIntentParser` **478** · `VoiceIntentParserTest` **499** · `VoiceClarify` **377** · `VoiceQuestion` **230** · `RemotePiperSpeaker` **296**. |
| 6 | **test có thật đỏ được** | ✅ **9/9 phép thử phá đỏ ĐÚNG CHỖ** (§3), phục hồi **khớp từng byte** (`cmp`). Trong đó 4 phép là trên bản vá của chính tôi, và 2 phép đầu **lột ra bài canh mù** (F8). |
| 7 | **không đổi mặc định ngầm / không nới guard** | ✅ `Prefs.kt` **không bị đụng**; `build.gradle.kts` không đụng (version do main-agent bump). Ba hằng đổi đều **tường minh + có lý do tại chỗ**: `MIN_SILENCE_MS` 150→600 · `SPEAK_SAFETY_MS` 10 s cứng → **15 s SÀN** + cộng theo độ dài câu · `MAX_CHARS` 600 K→1,8 M. Hai hằng đếm test đổi kèm [ĐO]. **0 danh sách `allowed` nào được nới** — cả ba stage đều chọn *đổi mã* thay vì *nới bài canh* khi `LauncherI18nContractTest` / `VoiceCommandWiringContractTest` bắt, và tôi đã xác nhận lại bằng diff. `:core` vẫn **0** import `android.*`. |

---

## 3. Thử phá — 9 phép, đỏ đúng chỗ cả 9

Sao lưu **theo tệp** (`cp` → `/tmp/kachi-review-bak*`; **không** `git checkout` trên cây chưa commit — luật dự án),
mutate → chạy → phục hồi → `cmp` khớp từng byte.

| # | Phép phá | Bài đỏ |
|---|---|---|
| 1 | gỡ cổng (4) của `isChoice` | `cong thu tu tra loi ngay o luot mot` |
| 2 | `carryFor` bỏ nhánh `asked` | `dang A hay B mang dong tu DOC sang luot tra loi` |
| 3 | `Ask(vague(), emptyList())` trở lại | `cau hoi co-khong mot dau hieu…` + `danh tu dau cau khong bi doi vai…` |
| 4 | bỏ `!actionAtHead` khỏi cổng (4) | `cong thu tu khong duoc lam cam cau ra lenh` |
| 5 | đo `looksAsked` trên bản **đã lọc** tiếng đệm | `dang A hay B mang dong tu DOC sang luot tra loi` |
| 6 | gỡ `android:process=":tts"` (stage `tts-isolate`) | `manifest khai PiperTtsService o tien trinh tts…` |
| 7 | Router về `SherpaTtsSpeaker(ctx)` (stage `tts-isolate`) | `chi PiperTtsService duoc dung SherpaTtsSpeaker` + `duong Piper cua Router…` |
| 8 | overlay bỏ `IMMERSIVE_STICKY` (stage `iso`) | `tam chu dung DUNG bo co an thanh he thong cua man chinh` |
| 9 | `MIN_SILENCE_MS` về 150 (stage `iso`) | `nguong im chiu duoc quang ngung…` + `tham so mac dinh bang dung bo dang chay` |

⚠ **Phép 1 và 2 lượt đầu VẪN XANH** ⇒ bài canh của chính tôi là trang trí (F8). Đã đổi sang `assertEquals` trên kết
quả **nhìn thấy được** rồi mới đỏ.

---

## 4. [ĐO] FULL 5 MODULE — số đếm từ `build/test-results/**/*.xml`

```
JAVA_HOME=/opt/homebrew/opt/openjdk@17 GRADLE_OPTS=-Xmx3g ./gradlew test --rerun-tasks --continue
⇒ BUILD SUCCESSFUL in 1m 16s · 73 actionable tasks: 73 executed
```

| task | lớp | test | bỏ | **đỏ** | lỗi |
|---|---|---|---|---|---|
| `core:test` | 232 | 2295 | 0 | **0** | 0 |
| `app:testDebugUnitTest` | 142 | 1170 | 0 | **0** | 0 |
| `app:testVehicleTestUnitTest` | 143 | 1182 | 0 | **0** | 0 |
| `car-integration:test` | 7 | 61 | 0 | **0** | 0 |
| `offcar-planner:test` | 19 | 99 | 0 | **0** | 0 |
| `vehicle-contracts:test` | 2 | 22 | 0 | **0** | 0 |
| **TỔNG** | | **4829** | **0** | **0** | **0** |

Khớp số học: mốc stage `parser` **4825** + **4 bài mới của lượt soát** = **4829** (core 2291 → 2295).
⚠ `:app:testReleaseUnitTest` **không tồn tại** trong cấu hình này — chỉ có `testDebugUnitTest` +
`testVehicleTestUnitTest` (cả hai đã chạy; `vehicleTest` = release + debuggable, phủ rộng hơn).

Kèm: `:app:assembleRelease` + `:app:assembleDebug` **xanh** ⇒ lint `abortOnError = true` cũng qua.

---

## 5. Tệp lượt soát đã đổi

| Tệp | Đổi gì | Dòng |
|---|---|---|
| `core/…/voice/VoiceQuestion.kt` | cổng (4) của `isChoice` · `actionAtHead` (tách dùng chung) · `looksAsked` | 180 → **230** |
| `core/…/voice/VoiceClarify.kt` | `ask` tokenize một lần + cờ `asked` · `carryFor(…, asked)` · `vague` mang ngữ cảnh | 354 → **377** |
| `app/…/voice/RemotePiperSpeaker.kt` | bind hỏng ⇒ `onRemoteGone()` (mở MỌI sổ) | 296 → **296** |
| `core/src/test/…/voice/VoiceClarifyQuestionTest.kt` | **+4 bài** · 1 khẳng định siết chặt kèm [ĐO] | 179 → **232** |
| `app/src/test/…/voice/VoiceTtsIsolationContractTest.kt` | bài bind-hỏng đòi `onRemoteGone` thay `settle(my)` | — |
| `core/src/test/…/voice/VoiceIntentParserTest.kt` | gom KDoc về dưới trần 500 | 501 → **499** |

🧹 Tệp probe tạm của lượt soát (`ZzReviewD1ProbeTest.kt`) **đã xoá**; `git status` còn **0** tệp probe/scratch.

---

## 6. Nợ / cần owner

1. **[F5, owner chốt]** *"mở cửa sổ trời **chưa**"* vẫn mở cửa sổ trời. Hai đường: (a) giữ như nay (một lệnh nói
   thiếu vẫn chạy) — đang ghim; (b) coi đuôi `chưa` là hỏi **vô điều kiện** ⇒ gỡ `!actionAtHead` khỏi cổng (4), giá
   là mọi câu *"&lt;động từ&gt; … chưa"* thành câu ĐỌC. Đổi thì sửa đúng một dòng + một khẳng định đã ghim.
2. **[F6]** câu ghép có dấu hiệu hỏi ở giữa — chuỗi tái lập ghi ở §1.
3. **🚗 Cả gói chưa chạy trên xe.** Ba phép đo quyết định, theo thứ tự rủi ro:
   (a) `kill <pid com.byd.launcher:tts>` **giữa lúc đang đọc** ⇒ launcher còn sống · overlay **tắt** (`onDone` đã về)
   · câu sau vẫn đọc được · `dumpsys accessibility` còn **Bound** (mục đích cuối của #0);
   (b) nói *"cửa sổ trời đang mở không"* ⇒ phải **đọc trạng thái**, không được mở gì;
   (c) overlay có còn kéo taskbar BYD lên không (máy ảo không có taskbar đó).
4. **📄 Doc chưa gom** (chủ ý, main-agent): 3 doc diagnostics 2026-09-18 còn ghi A/B/C/§D1/§BUG A-B ở trạng thái
   *"CHƯA implement"*; `kachi-voice-rearchitecture-and-remaining.html` còn gọi `SPEAK_SAFETY_MS` là *"lưới cố định"*;
   `PROJECT-BACKLOG.md` + `project-context.md` chưa có mục cho lượt này. Thêm: **§D1 của
   `oncar-voice-cases-findings-2026-09-18.md` mô tả chưa đủ** — nhật ký ghi `clarify:true/follow_up:true`, tức lệnh
   ghi sinh ra ở **lượt hai** (stage `parser` đã nêu; lượt soát này xác nhận và mở rộng: nó còn sinh ra ở những hình
   dạng câu hỏi mà `isQuestion` chưa nhận).
5. **🧹 Hai handoff cũ trùng vai còn trong cây**: `docs/_handoff/voice-batch-iso-done.md` (bản báo cáo lượt trước của
   stage `iso`, đã bị `iso-done.md` thay) + `voice-fix-RESUME-2026-09-18.md`. Kiểm/gỡ trước khi commit.
6. **Chưa bump** `versionCode`/`versionName` (1.79 / vc80 khi ship).

---

## 7. VERDICT

**APPROVED.**

Ba tiêu chí đóng: (a) mọi finding **[P0]/[P2]** đã vá và mỗi bản vá có **một phép thử phá đỏ đúng chỗ**; (b) full 5
module **4829 test / 0 đỏ** đếm từ XML, `assembleRelease` xanh; (c) **0** mặc định bị đổi ngầm, **0** guard bị nới,
**0** tệp vượt trần 500, `:core` vẫn thuần.

Hai mục còn lại (**F5** · **F6**) **không** phải lỗi bỏ ngỏ im lặng: cả hai đã ghim bằng bài canh, ghi chuỗi tái lập,
và F5 đã nêu rõ là **quyết định của owner** chứ không phải điều tôi chứng nhận an toàn.

⚠ Điều kiện đi kèm: **gói này chưa chạy trên xe một lần nào**. Mọi số trên là [ĐO] off-car (trên chuỗi `heard` THẬT
của 53 phiên log). Ba phép đo ở §6.3 nên chạy **ngay lượt lên xe đầu tiên**.
