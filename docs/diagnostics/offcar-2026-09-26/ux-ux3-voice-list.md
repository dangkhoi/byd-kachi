# UX3 — Danh sách câu lệnh nói được (2.74 · R3)

Spec: `docs/specs/kachi-274-ux-voice-camera.html` §3 R3 · §4.2 · Ngày: 2026-09-26 (off-car) · Lane: UX3-VOICE-LIST

---

## 1. Root cause

**[ĐO]** Không có bất kỳ bề mặt NGƯỜI DÙNG nào liệt kê câu nói được, và cũng **không có nguồn sự thật** nào ở
`:core` **main** để dựng nó. Tri thức *"câu nào nói được"* tồn tại ở **5 bản sao**, không bản nào dùng được cho UI:

| # | Nơi | Vai thật | Vì sao không dùng được cho danh sách |
|---|---|---|---|
| 1 | `SherpaPhraseHotwords.phrases()` | sinh ~2000 cụm CÓ DẤU | để **bias bộ nghe**, không phải để đọc; [ĐO] 6,6 ms/lượt trên host ⇒ [SUY] ~130 ms trên đầu xe |
| 2 | `VoiceGrammarCoverageTest.sentenceFor` (VI) | bảng mẫu câu trong **test** | `:app` không gọi được |
| 3 | nhánh EN của cùng bài canh | bảng mẫu câu thứ hai | như trên |
| 4 | `FeatureCatalogDumpTest` — bộ sinh riêng + bảng `generic` **gõ tay** 16 dòng | dump JSON cho tài liệu | như trên; và **không** assert câu nào parse được |
| 5 | `docs/HUONG-DAN-KACHI.md` §6 — bảng 14 nhóm **chép tay** | tài liệu | thêm/bớt một nút registry không làm bảng đổi, **không gì đỏ** |

**[ĐO]** Không bản nào trong 5 bản có bài kiểm *"mọi câu ví dụ đều parse được"* ⇒ tài liệu (và một danh sách mới
nếu gõ tay) **có thể nói dối mà không gì đỏ**. Đây đúng họ lỗi `unitPrefs` ×4 / `customLayout` ×2 mà KDoc
`VoiceGrammar` lập ra để chặn.

**[ĐO]** Thêm hai chỗ đã rữa sẵn, cùng nguyên nhân: KDoc `VoiceGrammar` và `VoiceGrammarCoverageTest` còn ghi
*"65 nút + 123 datum + 4 gói"* trong khi số thật là **33 nút · 64 datum · 2 gói · 3 hành động launcher**.

⇒ Việc cần làm **không phải** *"gõ một danh sách vào Settings"* mà là **nâng bộ sinh câu mẫu từ test lên `:core`
main**, rồi cho UI + dump + bài canh độ phủ + tài liệu cùng đọc một nguồn.

---

## 2. Fix

### 2.1 `:core` — `VoiceCommandCatalog` (mới, thuần Kotlin, 422 dòng)

Sinh **nhóm** + **câu mẫu** + **cột "Kachi làm gì"** từ chính các bảng mà `VoiceIntentParser` dùng:

| Thành phần của câu | Nguồn (không chép tay) |
|---|---|
| tiêu đề nhóm xe | `Domain.displayLabel` (8 miền, đã dịch, đã được `LangCoverageTest` đếm) |
| động từ VI | `SherpaPhraseHotwords.CONTROL_VERBS[kind]` × `SherpaSpokenWords.VERBS` — **đúng hai bảng** của parser |
| động từ EN | `VoiceCommandCatalog.EN_VERBS` (14 dòng, **canh hai chiều** với `VoiceGrammar.VERBS`) |
| danh từ | nhãn registry qua `SherpaHotwords.phrasesOf`, **có phép ĐO** (xem 2.2) |
| mức NỬA của kính/rèm | `args[2]` của chính dòng đó, chỉ bày khi `VoiceControlParse.mentionsHalf` nhận |
| bố cục | `VoiceLayouts.SPOKEN` **lọc qua `VoiceLayouts.match`** (cụm *"bố cục"* trần tự rụng) |
| mệnh đề ô | `VoiceSlotPhrases.SPOKEN` + số ô đọc bằng chính `VoiceTailClause.slotAt` |
| nhạc · dẫn đường · sổ địa chỉ · app · hồ sơ | `SherpaSpokenWords.VERBS` · `VoiceSynonyms.MEDIA_WORDS` · `VoicePlaces.PLACE_VERBS` · `VoiceProfileNames.MARKERS` |
| cột *"Kachi làm gì"* | `VoiceReply.preview(intent)` — **chuỗi thật** xe sẽ nói |
| cờ *"sẽ hỏi lại"* | `VoiceRiskTable.of(intent, confirmIds)` với `confirmIds` **truyền vào thật** |

Chuỗi khai tay còn lại, có lý do và bị máy canh: **4 tiêu đề** họ ngoài registry (Nhạc · Dẫn đường · Ứng dụng ·
Hồ sơ — không bộ đăng ký nào mang chúng; khai song ngữ bằng `Strings.t` đúng lệ `VoiceReply`), **14 dạng động từ
EN**, **2 cụm đánh dấu** (*"hồ sơ"* / *"profile"*, `"in slot 1"`). Cả ba nhóm đều có bài canh đối chiếu với bảng
thật của bộ phân tích ⇒ không thể lén thêm một câu lệnh vào đây.

### 2.2 Ba quyết định đáng ghi

1. **Danh từ chọn bằng phép ĐO, không bằng danh sách ngoại lệ.** Thử theo thứ tự: đoạn đầu đã dọn dấu câu
   (*"Pin (SOC)"* → *"pin"*) → mọi đoạn nối lại (*"Battery health (SOH)"* → *"battery health soh"*) → nhãn đầy đủ;
   nhận cụm đầu tiên mà `VoiceGrammar.matchAt` xác nhận trỏ về **đúng** dòng đó và không bị một dòng **cùng loại**
   khác giành. **[ĐO]** phép này tự giải hai ca thật: nhãn *"kính lái"* bị nút *"50% kính lái"* giành cụm ngắn ⇒
   nút 50% tự dùng nhãn đầy đủ; và bản EN không còn bày dấu ngoặc trong câu người ta phải ĐỌC.
2. **Không có tham số `lang`.** Cột *"Kachi làm gì"* là `VoiceReply.preview`, hàm đó đọc `Strings.current` và
   không có nạp chồng nhận `lang` (tệp 487/500 dòng). Nhận `lang` sẽ cho một hàng có câu theo `lang` mà phần giải
   thích theo `Strings.current` — hai thứ tiếng trên một dòng. Ngoại lệ duy nhất: `coverageSentence(def, lang)`
   (chỉ trả một CÂU) vì nó thay hai bảng của bài canh độ phủ, vốn dựng cả hai thứ tiếng trong một lượt chạy.
3. **Ngân sách thread giao diện.** **Không** gọi `SherpaPhraseHotwords.phrases()`. Mỗi nút/datum tốn một vòng ký
   tự + một `matchAt` trên từ vựng TĨNH đã `by lazy`; và tầng vẽ **chỉ dựng view của nhóm đang mở** (thân khối
   gập dựng ở lần mở đầu tiên). Bày phẳng ~150 dòng sẽ là ~300 `TextView` ngay lúc mở trang.

### 2.3 `:app` — hiện ở CUỐI nhóm *Giọng nói*

- `SettingsRowsDisclosure.kt` (**mới**, 115 dòng): `disclosureRow` (hàng tiêu đề bấm được ≥ `KachiSpace.TOUCH`,
  có số câu + dấu ▸/▾) + `disclosureLine` (câu đậm + việc nó làm, mờ). Tệp RIÊNG vì `SettingsRows.kt` đã
  **495/500 dòng** (CLAUDE.md §4.1), theo lối hàm mở rộng của `SettingsRowsColor.kt`.
- `SettingsVoiceSection.build` thêm khối cuối cùng, **sau** khối *Nhạc* (dòng `setMusicDefaultApp`). Ba nguồn động
  đọc **đúng ba nguồn** mà `VoiceDispatcher.parse` dùng (`state().profiles` · `VoiceWiring.appsByLabel` ·
  `VoicePlaces.labelsOf(state().savedPlaces)`) ⇒ danh sách không quảng cáo một tập app/địa chỉ khác tập mà lượt
  nói thật nhận. Không sinh khoá lưu bền mới (trạng thái gập không bền — nói rõ trong KDoc).
- 4 khoá tài nguyên mới (VI + EN), số đếm là `<plurals>` (bản một-chuỗi in *"1 phrases"* ở tiếng Anh).
- `SettingsCatalogEntries`: mục THÔNG TIN `voice_commands`, `prefKey = null`.

### 2.4 Gỡ bản sao (phần BẮT BUỘC, không phải dọn dẹp tuỳ chọn)

- `VoiceGrammarCoverageTest`: hai bảng VI/EN → `VoiceCommandCatalog.coverageSentence(…, lang)`; KDoc sửa số đã rữa.
- `FeatureCatalogDumpTest`: bộ sinh riêng → `samples(def)`/`samples(spec)`; 12 danh sách câu của bảng `generic` →
  `samplesFor(intent, …)`. **Lược đồ JSON không đổi** (`bindingKey` · `tier` · `min/max/step` · `reply*` …), chỉ
  trường `voice` đổi nguồn — `scripts/docs/feature-catalog.py` chạy nguyên.
- `docs/HUONG-DAN-KACHI.md` §6 (VI + EN): thêm khối nói rõ danh sách đầy đủ ở trong app và **sinh bằng máy**, bảng
  trong tài liệu là **bản tóm tắt gõ tay theo trục khác** (loại việc vs miền xe) và phải soát lại khi registry đổi,
  kèm hai lệnh sinh lại bản máy.

**Còn gõ tay có chủ ý** trong dump: 4 dòng `unknown_*` (ví dụ **KHÔNG hiểu được** — chúng minh hoạ lỗi, không phải
câu lệnh) và `media_query` (tra tên bài = **từ vựng MỞ**, Kachi cố ý không làm offline). Có bài canh ghim đúng hai
ngoại lệ ấy, nên dòng thứ ba mọc lên là đỏ.

---

## 3. Tests

| Bài | Khoá cái gì |
|---|---|
| `VoiceCommandCatalogTest.moi cau vi du deu parse ra dung y dinh da hua — VI va EN` | **bài canh không tồn tại ở cả 5 bản sao**: mọi câu bày ra đi qua `VoiceIntentParser.parseOne` và phải ra đúng ý định mà cột *"Kachi làm gì"* đang hứa, ở **cả hai** thứ tiếng |
| `…cot Kachi lam gi la cau that cua y dinh phan tich ra` | `does` == `VoiceReply.preview(câu đã parse)` — không phải một câu mô tả gần đúng |
| `…cau cua nhom mien xe deu thuoc dung mien do` | mở nhóm *Lốp* không thấy câu về đèn |
| `…moi nut, datum, goi lenh va hanh dong launcher deu co it nhat mot cau` | đếm lại từ 4 bộ đăng ký ⇒ thêm một dòng registry là **tự có câu**, xoá là tự mất |
| `…cau vi du khong rong, khong trung trong mot nhom, va nhom rong thi khong hien` | cả hai thứ tiếng; không dấu ngoặc của nhãn trong câu phải đọc; nhóm rỗng không hiện |
| `…danh sach dong dung du lieu THAT cua may nay` | đổi hồ sơ/app/sổ ⇒ danh sách đổi theo, không giữ bản chụp cũ |
| `…bang dong tu tieng Anh khop VoiceGrammar VERBS hai chieu` | mỗi dạng EN phải CÓ THẬT trong ngữ pháp và map về đúng động từ; mỗi `VoiceVerb` phải có một dạng EN |
| `…cum danh dau ho so va menh de chi o deu la cum bo phan tich THAT SU doc duoc` | hai cụm khai tay bị đối chiếu với `VoiceProfileNames.MARKERS` / `VoiceTailClause.slotAt` |
| `…co se-hoi-lai theo dung tap ma nguoi dung da tich` | tập rỗng ⇒ **không** câu nào hỏi lại (mặc định owner 2026-09-16); tích một mã ⇒ **đúng** mã đó |
| `FeatureCatalogDumpTest.moi ho cau lenh trong generic deu lay cau tu danh muc` | chống **bản sao thứ năm mọc lại** |
| `SettingsStackMarginContractTest` (siết) | Chiều 1 nay quét **cả tệp mở rộng** (`internal fun SettingsRows.…` khai TOP-LEVEL): [ĐO] `SettingsRowsColor.kt` chưa từng bị quét từ ngày ra đời ⇒ luật lề stack đang đúng nhờ **may mắn** |
| `SettingsCatalogControlContractTest` | mục danh mục `voice_commands` ⇔ control thật (`VoiceCommandCatalog.groups(`) |
| `LangCoverageTest` | +1 mục Cài đặt có nhãn ở cả hai thứ tiếng (75 → 76 · 238 → 239) |

**Kết quả** (đếm từ `build/test-results` XML): `:core` **123/123 xanh** trên 7 lớp chạy tập trung
(`VoiceCommandCatalogTest` 9 · `VoiceGrammarCoverageTest` 18 · `FeatureCatalogDumpTest` 2 · `LangCoverageTest` 21 ·
`SherpaBiasingCoverageTest` 18 · `VoiceIntentParserTest` 43 · `SherpaHotwordsTest` 12).

---

## 4. Việc lượt kiểm HÌNH (máy ảo / xe) phải soi

Off-car **không** chứng minh được phần vẽ. Bốn ảnh cần chụp ở 1920×720 / API 29 (lượt này **không** dùng máy ảo —
một agent khác đang chạy harness voice trên đó):

1. *Cài đặt › Giọng nói* cuộn tới **đáy**: tiêu đề *"Câu lệnh nói được"* phải nằm **SAU** khối *Nhạc*; mọi nhóm
   đang **GẬP**; mỗi tiêu đề có số câu (*"45 câu"*…); không tràn ngang ở bề rộng 1920.
2. Chạm một nhóm dài (*Khí hậu & không khí* — 45 câu): thân mở ra, mỗi dòng là **câu (đậm)** trên **việc nó làm
   (mờ)**; trang vẫn cuộn hết, không cắt chữ, không dính hàng (lề stack).
3. Chạm lại: **đúng** nhóm đó gập, nhóm khác không đổi. Rồi mở 2–3 nhóm cùng lúc — kiểm không có nhóm nào tự mở.
4. Đổi ngôn ngữ sang **English**, mở lại: tiêu đề nhóm và cột giải thích là tiếng Anh; câu ví dụ của nhóm xe là
   tiếng Anh (*"turn on reading light"*); **cố ý** còn tiếng Việt ở nhóm *Apps · slots · layout* (câu bố cục) và
   *Navigation* (sổ địa chỉ) — `VoiceLayouts.SPOKEN` và `VoicePlaces.PLACE_VERBS` **không có dạng EN** trong ngữ
   pháp, và dòng ghi chú dưới tiêu đề đã nói ra điều đó. Không phải lỗi.

**[ĐO 2026-09-27]** Số câu chốt lại: *Khí hậu & không khí* = **45**, tổng VI = **156** (đếm **bằng máy** trên máy ảo,
`visual-pass-2026-09-27.md` §6); *Thân xe* **không đổi** = **47**. Bản đầu của tài liệu này ghi *43 / 153* và đã sai
ngay khi viết xong: hai làn **UX4** và **UX5b** thêm dòng **registry** SAU đó — **+2 datum ghế phụ** (vào đúng nhóm
*Khí hậu*, nên nhóm ấy 43 → 45) và **ô Gió gộp** (+1, nên tổng 153 → 156). Danh sách câu được **SINH** từ chính
registry (`VoiceCommandCatalog`), nên mọi dòng registry thêm vào sẽ tự đẩy số lên — con số gõ tay trong tài liệu là
thứ **luôn** phải đếm lại bằng máy, không phải thứ chép lại.

**Trên xe (🚗, chưa đo):** thời gian mở trang *Giọng nói*. Hai khoản, đo riêng vì đường sửa khác nhau:

- **Dựng danh sách** (`VoiceCommandCatalog.groups`): **[ĐO host 2026-09-26, lượt kiểm đối kháng]** *1,0 ms* lượt
  đầu · *0,9 ms* lượt sau (JVM host, 12 nhóm · 156 câu VI / 152 EN; đo bằng một bài tạm trong
  `VoiceCommandCatalogTest` rồi gỡ). ⇒ [SUY] ~20 ms trên đầu xe. Không phải khoản đáng lo.
- **Một lượt dò `PackageManager`** (`VoiceWiring.appsByLabel`, `by lazy`): KDoc của chính hàm đó ghi *"lượt hỏi ấy
  tốn ~100 ms trên đầu xe"*, và phần đắt là `loadLabel` cho **từng** activity launcher. ⇒ đây là khoản chi phối,
  không phải bộ sinh câu. (Bản đầu của tài liệu này ghi *"[SUY] vài chục ms"* — nói nhẹ hơn số của chính dự án.)

Nếu owner thấy trang mở chậm rõ rệt: đường sửa là dời phép dò vào **lần mở nhóm *Apps*** (thân khối gập đã dựng
trễ sẵn, chỉ cần chuyển nguồn `apps` thành lambda) — **nhưng** lúc đó tiêu đề nhóm *Apps* không còn nói được **số
câu** trước khi mở, tức đánh đổi đúng cái tính chất *"biết mình sắp mở cái gì"* mà khối gập lập ra. Chọn cái nào
là quyết định của owner sau khi có số đo thật trên xe.

**Cũng nên soi bằng máy (off-car, không cần máy ảo):** sinh lại `docs/kachi-feature-catalog.html` rồi so cột
*"voice"* với danh sách trong app — cùng một nguồn nên phải khớp từng câu.

---

## 4b. Lượt kiểm ĐỐI KHÁNG (2026-09-26) — 3 bản vá đã áp, tất cả trong phạm vi lane

| # | Mức | Chỗ | Trước → Sau |
|---|---|---|---|
| 1 | P2 | `VoiceCommandCatalog.mediaNoun` | *"phát bài hát"* / *"dừng bài hát"* → **"phát nhạc"** / **"dừng nhạc"**. Bản đầu lấy **phần tử đầu** của `VoiceSynonyms.MEDIA_WORDS`, mà thứ tự bảng ấy là thứ tự **so khớp** của parser (dài trước ngắn), không phải thứ tự tự nhiên khi nói ⇒ danh sách bày ra câu lạ trong khi bỏ mất đúng câu `HUONG-DAN` §6 dạy từ V1. Nay khoá `MEDIA_NOUN_VI` khai tường minh + **bị bài canh ép** phải có thật trong bảng và có dạng có dấu (cùng lối `profileMarker`/`slotTail`). Bảng `MEDIA_WORDS` **không** bị đụng (CLAUDE.md §6). |
| 2 | P3 | `VoiceCommandCatalog.longVerb` | *"quay lại bài"* → **"bài trước"**. Luật *"nhiều khoảng trắng nhất"* chọn một cụm **bỏ lửng vế đối tượng**: parser nhận, nhưng người đọc nó lên sẽ tự nối thành *"quay lại bài hát trước"* — câu nối thêm **không** parse được ⇒ danh sách dạy sai đúng chỗ nó ra đời để dạy đúng. Nay bỏ dạng có **từ cuối là một mục của `MEDIA_WORDS`** (phép đo trên bảng thật, không danh sách ngoại lệ). NEXT / NAV / SWITCH không đổi. |
| 3 | P3 | `VoiceCommandCatalogTest.same()` | Bài mang tên *"parse ra đúng ý định đã hứa"* chỉ so **mã**, nên **lật cực** một nút (*"tắt X"* gửi 1) vẫn xanh ở đó — chỉ bài `cot Kachi lam gi…` bắt được. Nay so cả `value` + `relative`. **[ĐO]** lật `val on` trong `controlPairs`: trước bản vá **1** bài đỏ, sau bản vá **2** bài đỏ. |

**Đã thử phá để chứng minh bài canh có răng [ĐO]:** lật cực `val on` ⇒ `cot Kachi lam gi la cau that cua y dinh
phan tich ra` + `moi cau vi du deu parse ra dung y dinh da hua` đỏ; trả lại ⇒ xanh.

**Soi bằng máy, không đụng gì:** `core/build/catalog/registry.json` sau lượt vá — `media_play: ["phát nhạc"]` ·
`media_pause: ["dừng nhạc"]` · `media_next: ["bài tiếp theo"]` · `media_prev: ["bài trước"]`; lược đồ JSON (5 mảng,
đủ `bindingKey`/`halDevice`/`tier`/`min`/`max`/`reply*`/`confirmQuestion`) **không đổi** ⇒
`scripts/docs/feature-catalog.py` chạy nguyên.

**Còn lại KHÔNG vá, cố ý (dữ liệu của bộ đăng ký, không phải lỗi của danh sách):** nhóm *Thân xe* bày cả cặp
*"bật/tắt 50% kính lái"* vì `win_half_*` khai `ControlKind.TOGGLE` (1 = mở 50%, 0 = đóng — `ControlRegistry.kt:295`),
và cặp *"bật/tắt kính lái"* thay vì *"mở/đóng kính lái"* vì `CONTROL_VERBS[TOGGLE]` xếp `ON`/`OFF` trước
`OPEN`/`CLOSE` (parser nhận **cả bốn**, danh sách chỉ bày hai cái đầu). Sửa cho tự nhiên thì phải khai *động từ ưu
tiên* ở `ControlDef` — một trường mới trong bộ đăng ký, ngoài phạm vi R3; để owner chốt.

---

## 5. Còn nợ / cần người khác chạm

- **1 dòng ngoài quyền lane**: `SettingsCatalogTest.muc khong luu ben thi khai prefKey null` đang ĐỎ vì mục mới
  `voice_commands` (`prefKey = null`). Sửa: chèn `"voice_commands",` **ngay sau** `"voice_tts_pack",` trong danh
  sách chờ, và đổi câu *"mười bảy mục"* → *"mười tám mục"*. Tệp đó không thuộc lane này.
- **KDoc đã rữa ở tệp không thuộc lane**: `VoiceGrammar` (đầu tệp) còn ghi *"65 nút + 123 datum + 4 gói lệnh +
  2 hành động launcher"*; số thật là *33 · 64 · 2 · 3*.
- **Chưa làm (cố ý, ngoài phạm vi R3)**: nhóm cho **câu ghép** (*"… và …"*) và **câu kết thúc phiên** (*"tạm
  biệt"*). Cả hai chỉ hiện ý định qua `VoiceIntentParser.parse` (không phải `parseOne`) nên bài canh *"mọi câu
  parse ra đúng ý định"* phải mở thêm một đường; để owner chốt có cần bày ra không.
- **Chưa làm (cố ý)**: chạm một dòng để **chạy thử** câu lệnh ngay từ Cài đặt — đó là mở đường GHI xe từ màn Cài
  đặt, phải đi qua spec riêng.
