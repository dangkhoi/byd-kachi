# Off-car 2026-09-26 — vế "vào ô", tên app bóp méo, tên hồ sơ rụng (VOICE-SLOT-TAIL-CUT · VOICE-APP-NAME-FUZZY · VOICE-PROFILE-NAME-PHONETIC)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-26 · **Mục đích**: ba bệnh voice bắt trên xe 26/09 được **đo lại
> off-car bằng chính bản thu của xe**, tìm ra nguyên nhân, vá, và đo lại. Backlog: `PROJECT-BACKLOG.md` ba dòng
> VOICE-SLOT-TAIL-CUT · VOICE-APP-NAME-FUZZY · VOICE-PROFILE-NAME-PHONETIC. Buổi xe: `perf-oncar-2026-09-26/`.

## 0. Cách đo — vì sao các số dưới là [ĐO], không phải [SUY]

30 bản thu thật của buổi xe (`perf-oncar-2026-09-26/kachi-voice-20260926-185812-365.zip` — **cục bộ, gitignored,
không commit**) được phát lại qua cầu kiểm thử trên máy ảo `emulator-5554` (API 29):

```
am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd wav --es path <wav>
```

Đường này đi **đúng** `VoiceWavProbe → VoiceVad (cắt đuôi) → VoiceRecognizer/sherpa` mà phiên mic thật đi, với
**cùng** mô hình `zipformer-vi-int8-2025-04-20` của xe.

**[ĐO] 13/13 bản thu liên quan cho ra ĐÚNG chuỗi `heard` mà xe đã ghi trong JSON đi kèm** ⇒ off-car tái lập được
hiện tượng của xe theo từng ký tự. Đó là điều kiện để mọi kết luận dưới đây không phải là suy đoán.

---

## 1. VOICE-SLOT-TAIL-CUT — **không phải VAD cắt, mà bộ giải mã bỏ chữ**

### 1.1 Cắt đuôi VAD chỉ ăn vào im lặng [ĐO]

`KachiVoiceWav` in ra đúng lượng cắt mỗi lượt; đường bao năng lượng (khung 20 ms, ngưỡng `max(2 % đỉnh, 200 RMS)`)
cho điểm hết tiếng:

| bản thu | cửa sổ mic | điểm cắt (`endpoint`) | tiếng nói hết ở | phần bị cắt |
|---|---|---|---|---|
| …-184201 | 3 400 ms | 2 600 ms | ~2 040 ms | 800 ms **im lặng** |
| …-184341 | 3 400 ms | 2 600 ms | ~2 040 ms | 800 ms im lặng |
| …-183912 | 4 600 ms | 3 976 ms | ~3 620 ms | 624 ms im lặng |
| …-182614 | 5 200 ms | 4 584 ms | ~4 180 ms | 616 ms im lặng |

**13/13** bản thu câu-có-ô đều có `điểm cắt ≥ điểm hết tiếng` ⇒ cắt đuôi **chưa lấy đi một chữ nào**.

### 1.2 Tiếng nói CÓ mang cái đuôi — chứng minh bằng cách cắt tay rồi giải mã từng khúc [ĐO]

| bản thu | khúc | mô hình in ra |
|---|---|---|
| …-184201 | `[0 .. 1 300] ms` | *"mở vietmap"* |
| …-184201 | `[900 .. 2 600] ms` | *"áp vào ô số một"* |
| …-184201 | `[1 400 .. 3 400] ms` | *"ô số một"* |
| …-184201 | **nguyên cửa sổ** | *"mở vietmap **một**"* ← mất *"vào ô số"* |
| …-182614 | `[2 000 .. 3 200] ms` | *"mở vietmap"* |
| …-182614 | `[3 000 .. 5 200] ms` | *"vào ô số một"* |
| …-184821 (ca ĐÚNG) | `[0 .. 1 500] ms` / `[1 300 .. 3 800] ms` | *"mở youtube"* / *"vào ô số hai"* |

⇒ Cái đuôi nằm trong tiếng. Giải mã **cả câu** mới làm nó biến thành một từ. Không phải mic, không phải VAD.

### 1.3 Không có một dòng hotword nào đỡ cái đuôi [ĐO]

- Soát tệp thật (`core/build/hotwords/hotwords-phrases.txt`, **2 223 dòng**): **0 dòng** chứa *"VÀO Ô"*.
- Cụm *"MỞ VIỆT MÁP"* cũng **không có** trong tệp — nó bị `SherpaHotwords.dropPrefixes` bỏ vì là tiền tố theo từ
  của `MỞ VIỆT MÁP LIVE` / `MỞ VIỆT MÁP LAY`.
- Đổi núm `voice_hotword_score` **2,0 → 3,0 → 4,0** cho ra **y hệt một chuỗi** trên cả 6 bản thu app ⇒ đúng điều
  phải xảy ra khi đường đúng **không có** hotword nào để được cộng điểm. (Nhận xét: đây **không** bác giả thuyết
  biasing, nó chỉ ra chỗ trống — không có gì để mà co giãn.)

### 1.4 Quãng ngừng đo được, và vì sao **mặc định VAD không đổi**

| loại quãng ngừng (30 bản thu) | giá trị | số ca |
|---|---|---|
| trong cùng một vế (*"mở youtube ⟨…⟩ vào ô số hai"*) | 120 · 120 · 140 · 160 · 160 · 180 · 200 · 260 ms | 8 |
| ngừng để **nghĩ** giữa hai vế | 720 · 820 · 820 · 1 060 ms | 4 |

- Mặc định `MIN_SILENCE_MS` = **600 ms** đã ≥ **2,3×** quãng ngừng trong-vế lớn nhất, và §1.1 cho thấy nó chưa cắt
  cụt câu ghép nào ⇒ **không nâng mặc định**. Nâng lên ≥ 800 sẽ (a) cộng ≥ 200 ms vào **mọi** lượt nói và (b) phá
  bất biến *"VAD chốt sớm hơn bộ RMS"* (`VoiceEndpointer.HANGOVER_MS` = 800 ms, `VoiceVadTrimTest` canh).
- **Trần** thì sai thật: owner thử `prefs_set voice_vad_min_silence_ms 1100` **trên xe** và trần cũ 800 ⇒ giá trị bị
  từ chối, tức núm chỉnh-trên-xe không tới được vùng ngừng-để-nghĩ 720–1 060 ms. ⇒ **trần 800 → 1 200 ms**.

### 1.5 Bản vá + kết quả

Thêm mệnh đề ô vào nguồn hotword (`VoiceSlotPhrases` → `SherpaPhraseHotwords.phrases`): `VÀO Ô SỐ <N>` ·
`VÀO Ô <N>` · `Ô SỐ <N>`, `N = 1..LayoutPreset` (=4). Tệp **2 223 → 2 235 dòng**, **không bỏ dòng nào**.

| bản thu | ASR TRƯỚC | ý định TRƯỚC | ASR SAU | ý định SAU |
|---|---|---|---|---|
| …-182614 | mở vietmap một | `OpenApp(VietMap)` | **mở vietmap vào ô số một** | `OpenApp(VietMap → ô 1)` ✅ |
| …-183912 | mở vietmap một | `OpenApp(VietMap)` | **mở vietmap vào ô số một** | `OpenApp(VietMap → ô 1)` ✅ |
| …-184201 | mở vietmap một | `OpenApp(VietMap)` | **mở vietmap vào ô số một** | `OpenApp(VietMap → ô 1)` ✅ |
| …-184341 | mở vietmap một | `OpenApp(VietMap)` | **mở vietmap vào ô số một** | `OpenApp(VietMap → ô 1)` ✅ |
| …-182920 | mở vietmap | `OpenApp(VietMap)` | mở vietmap | `OpenApp(VietMap)` (câu không nêu ô — đúng) |
| …-183051 | mở youtube vào ô số một | `OpenApp(YouTube → ô 1)` | không đổi | không đổi |
| …-184821 | mở youtube vào ô số hai | `OpenApp(YouTube → ô 2)` | không đổi | không đổi |

**4/4 ca bệnh khỏi, 0 ca đang chạy bị đổi.** Tầng CHỮ vốn đã đúng: `parseOne("mở vietmap vào ô số hai")` ra
`OpenApp(VietMap → ô 2)` từ trước bản này (bài `cau day du van ra dung o` khoá lại điều đó).

---

## 2. VOICE-APP-NAME-FUZZY — khớp mờ tên app, **ở nhánh cuối**

Ba chuỗi thật của xe; hai chuỗi đầu trước bản này không hiểu được:

| ASR | TRƯỚC | SAU |
|---|---|---|
| mở **youtubex** vào ô hai | `NO_OBJECT` — *"không tìm thấy thứ đó"* | `OpenApp(YouTube → ô 2)` ✅ |
| đặt **vietp** vào ô số một | `MISMATCH` (nhãn datum *"Số"* khớp giữa câu) | `OpenApp(VietMap → ô 1)` ✅ |
| đặt **yout tiếp** vào ô số hai | `MISMATCH` | `MISMATCH` ❌ (xem §5) |

**Cách làm** — `VoiceLastResort.appFuzzy`, chỉ chạy khi **không cách hiểu nào CÓ NGHĨA** (sau mọi phép khớp chính
xác), với bốn cổng:

1. chỉ **vị trí 0** (ngay sau động từ), chỉ động từ hành động và không phải động từ đóng;
2. dải từ đem so **dừng trước mệnh đề ô** (`VoiceLexicon.SLOT_WORDS`) ⇒ mệnh đề ô không bị nuốt vào tên app;
3. ứng viên khớp phải **DUY NHẤT** theo **nhãn** (nhãn app đã cài + mọi cách gọi của bảng đích; hai dòng cùng
   nhãn = một app). Hai app khác nhãn cùng khớp ⇒ **không chọn** ⇒ câu giữ nguyên *"không hiểu"* ⇒ hỏi lại;
4. phép so dùng chung `VoiceNameFuzzy`: neo **4 ký tự đầu** · lệch ≤ **2** · cả hai chuỗi ≥ **5** ký tự — đúng ba
   con số mà `VoiceAppTargets.bySpokenFuzzy` đã đo từ 2026-09-20, nay ở **một** chỗ (DRY).

Ca âm đã khoá bằng test: *"mở netfliy"* với hai app *Netflix*/*Netflax* ⇒ **không** mở app nào; *"mở wazi"* (4 ký
tự) ⇒ không thành *Waze*; *"mở cửa sổ trời"* · *"mở kính lái"* · *"bật đèn đọc"* vẫn là lệnh xe; điểm đến của câu
dẫn đường vẫn nguyên văn.

---

## 3. VOICE-PROFILE-NAME-PHONETIC — tên hồ sơ

### 3.1 Ba chỗ hụt (mỗi chỗ một bằng chứng)

| hiện tượng | bằng chứng | chỗ hụt |
|---|---|---|
| 8/8 lượt nghe *"chuyển sang hồ sơ"* — **rụng cái tên** | bản thu …-183215 · …-183304 phát lại ra đúng hai chuỗi ấy | tên hồ sơ **chưa bao giờ** vào tệp hotword |
| *"chuyển sang hồ sơ định"* (rụng chữ *"Mặc"*) ⇒ `MISMATCH` | cùng bộ chuỗi | parser không khớp mờ tên hồ sơ |
| câu mất tên ⇒ *"việc đó không đi với thứ đó — thử nêu mức…"* | preview của cầu kiểm thử | không có đường **hỏi lại** tên hồ sơ |

### 3.2 Bản vá

1. **Hotword**: `VoiceProfileNames.phrases` sinh `HỒ SƠ <tên>` + `<động từ đổi> HỒ SƠ <tên>`, cho **cả** tên viết
   thường gốc **và** dạng đọc tiếng Việt qua `VoiceAppPhonetics.spokenForms` (thêm hai âm `test` → *"tét/thét"*,
   `mom` → *"mom/mâm"* vào bảng âm — cùng bảng mà tên app dùng, không bảng thứ hai). `SherpaBiasing.hotwordsFile`
   nhận thêm `profiles`, `VoiceRecognizer.open` truyền vào (tham số `profiles` trước đây bị `@Suppress(UNUSED)`).
2. **Khớp mờ** (`VoiceProfileNames.pick`, ở nhánh cuối): chỉ chạy khi câu có **cụm đánh dấu** *"hồ sơ"*/*"profile"*,
   chỉ xét phần **sau** cụm ấy, và nhập nhằng ⇒ không chọn. Hai phép so: lệch ký tự (`nearly`) và **rụng hẳn một
   từ ở biên** (`wordEdge` — *"định"* ⇒ *"Mặc định"*, cần ≥ 4 ký tự và tên không dài hơn quá 2 từ).
3. **Hỏi lại** (`VoiceClarify` + `VoiceProfileNames.missingName`): câu nêu *"hồ sơ"* mà không có tên nào ⇒
   *"Hồ sơ nào — A hay B?"*, mang theo nguyên vế đã hiểu để câu trả lời ghép lại thành một câu phân tích được.
   Máy chỉ có một hồ sơ ⇒ không hỏi (không có gì để chọn).
   ⚠ Chỗ gọi `VoiceClarify.ask` ở `:app` trước đây **không truyền `terms`** ⇒ mặc định chỉ có tập TĨNH (không có
   tên hồ sơ) ⇒ đường hỏi lại sẽ **compile xanh mà không bao giờ nổ** (đúng bẫy CLAUDE.md §8). Nay cả hai chỗ gọi
   dùng `sessionTerms()` (hồ sơ + app của máy), và bài canh wiring khoá đúng chuỗi ấy.

### 3.3 Kết quả — đo lại bằng chính hai bản thu [ĐO]

| tình huống | ASR | ý định |
|---|---|---|
| bản cũ, máy **chưa có** hồ sơ *"Test"* | chuyển sang hồ sơ | `Unknown` (MISMATCH) |
| bản mới, máy **chưa có** hồ sơ *"Test"* | chuyển sang hồ sơ | `Unknown` ⇒ nay **hỏi lại** *"Hồ sơ nào — …?"* |
| bản mới, máy **có** hồ sơ *"Test"* | **chuyển sang hồ sơ test** | `Profile(Test)` ✅ |
| bản mới, máy **có** hồ sơ *"Test"* (bản thu 2) | **đổi sang hồ sơ test** | `Profile(Test)` ✅ |

Thứ duy nhất đổi giữa hai hàng cuối và hàng trên là **danh sách hồ sơ**, và tác động duy nhất của nó lên tầng NGHE
là cụm hotword mới ⇒ quy kết sạch.

⚠ **Phát hiện đi kèm, bác một nửa giả định cũ**: KDoc `SherpaBiasing` từng nói *"tên hồ sơ/app là danh từ riêng /
tiếng Anh nên mô hình VN không phát ra được token đó ⇒ biasing vô nghĩa"*. [ĐO] mô hình **in ra được** chữ
`test`. [CHƯA BIẾT] dòng nào kéo được nó — dạng gốc `HỒ SƠ TEST` hay dạng đọc `HỒ SƠ TÉT` (cả hai đều trong tệp);
chốt bằng một lượt dựng tệp chỉ có một trong hai. Dạng đọc tiếng Việt cho *"test"*/*"mom"* vẫn ở mức **[ĐOÁN]** về
mặt phát âm, chưa có bản thu nào của người nói *"Mom"* để kiểm.

---

## 4. Chất lượng — số và cách tái lập

| phép đo | kết quả | lệnh |
|---|---|---|
| test 5 module | **5 639 test · 0 fail** | `./gradlew test --rerun-tasks --continue`, đếm từ `*/build/test-results/*/*.xml` |
| test mới thêm | **14** (`VoiceCarWav0926Test` 8 · `VoiceSlotProfileHotwordTest` 6) | |
| **thử làm ĐỎ** (gỡ bản vá ⇒ test phải đỏ) | **5/14 đỏ** đúng chỗ: 2 bài parser (app mờ · hồ sơ mờ) + 3 bài hotword (mệnh đề ô · luật lọc · tên hồ sơ) | gỡ `VoiceLastResort.pick` + hai dòng `out.addAll(...)` rồi chạy lại |
| E2E T1 (chữ → ý định, 106 ca) | **105/106**; ca lệch duy nhất `t70 mở gu gồ máp` là **nhiễu môi trường** (hộp thoại *NewVersionAvailable* của YouTube đang chiếm màn) — chạy lại riêng ⇒ **PASS**, ý định + câu đọc không đổi | `scripts/emulator/voice-e2e.sh --only say` |
| E2E T2 (tiếng → ý định, 27 WAV TTS) | **23/27 nghe đúng nguyên văn, 0 FAIL** = **đúng bằng** mốc đã ghi ở `perf-closeout-2026-09-25.md` §T2 ⇒ thêm hotword **không** làm hụt bộ này | `scripts/emulator/voice-e2e.sh --only wav` |
| tệp hotword | 2 223 → **2 235** dòng (+12 mệnh đề ô; cụm hồ sơ chỉ sinh khi có hồ sơ truyền vào) | `./gradlew :core:test --tests '*SherpaBiasingCoverageTest*'` ⇒ `core/build/hotwords/` |

---

## 5. Còn lại

- ❌ **`đặt yout tiếp vào ô số hai`** — *"yout tiếp"* lệch **3** ký tự so với *"youtube"* sau khi ghép liền, ngoài
  tầm luật ≤ 2. Nới lên 3 là mở cửa cho khớp bừa giữa các app; ca này để **hỏi lại** lo (trên xe nó đã đi đường
  hỏi lại: JSON có `clarify:true`). Không nới.
- 🚗 **Đo lại trên xe**: (a) nói *"mở vietmap vào ô số 2"* 10 lượt — mong đợi vế ô không rụng; (b) *"chuyển sang hồ
  sơ Test"* sau khi hồ sơ tên tiếng Anh đã có trên xe; (c) `prefs_set voice_vad_min_silence_ms 1100` nay **ăn** —
  đo độ trễ chốt câu (`KachiVoiceTiming`: `nghe … ms`) để biết giá của vùng ngừng-để-nghĩ 720–1 060 ms.
- 🚗 **Chưa đo**: quãng ngừng-để-nghĩ > 800 ms vẫn **cắt** câu ở mặc định 600 ms. Hướng rẻ hơn nâng mặc định:
  **không đóng lượt khi chuỗi đang dở** (kết thúc bằng *"vào ô"*, *"vào ô số"*, hay một động từ chưa có đối tượng)
  — chưa làm, cần một vòng thiết kế riêng (ghi vào backlog).
- [CHƯA BIẾT] dạng hotword nào kéo được chữ `test` (§3.3), và dạng đọc Việt của *"mom"* chưa có bản thu để kiểm.
