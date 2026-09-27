# Buổi xe 27/09 — câu "mở &lt;app&gt; vào ô số N": 10/22 lượt hiểu sai, bốn họ, một cơ chế chung ở tầng NGHE

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 (2.75) · **Mục đích**: nhật ký buổi đo trên xe thật (Seal,
> 2.74 (175), 10:30–10:45) được **root-cause từng lượt**, vá, và đo lại off-car bằng chính bản thu của xe.
> Buổi trước: `voice-tail-fuzzy-phonetic.md` (26/09) · `voice-open-turn.md`. Backlog: `docs/PROJECT-BACKLOG.md`.

---

## 0. Cách đo — và **cái không đo được**, nói trước

Ba nguồn, ba mức bằng chứng khác nhau (CLAUDE.md §2):

| nguồn | dùng để | mức |
|---|---|---|
| `logcat` của xe (`KachiVoiceSession` · `KachiVoiceTiming` · `KachiVoiceRec`) | chuỗi mô hình in ra · ý định · mốc VAD từng lượt | **[ĐO xe]** |
| 30 bản thu WAV + JSON xe tự chụp (`VoiceUtteranceLog`, cục bộ, **không commit**) | phát lại off-car qua `cmd wav` trên máy ảo API 29, cùng mô hình `zipformer-vi-int8-2025-04-20` | **[ĐO máy ảo]** |
| bản thu **dựng thêm** (nối im lặng / cắt tại mốc) từ chính bản thu xe | quét tham số (pre-roll, cổng cắt đầu) | **[ĐO máy ảo, kích thích dựng]** |

### ⚠ 0.1 · 10 trong 10 lượt hiểu sai **KHÔNG có bản thu** — và đó là một lỗ chẩn đoán, không phải trùng hợp

Yêu cầu ban đầu của buổi off-car này là *"lấy WAV của các lượt hỏng, giải mã lại với pre-roll 0/150/250/350 ms"*.
Không làm được, vì **[ĐO]**:

- `VoiceUtteranceLog` chỉ ghi ở **lượt CHÍNH** (`VoiceSessionListen.runListen`, `keepPcm = true`); các lượt **NỐI**
  (`VoiceSessionTurns.listenOnce` — hỏi lại + hội thoại) chạy `keepPcm = false` và **không** gọi `logHeard`.
- R9 giữ micro 5 giây sau **mọi** câu trả lời ⇒ câu thứ hai trở đi của một phiên **luôn** là lượt nối.
- Kết quả: trong 22 lượt của buổi đo, **7 lượt** có bản thu (đều là lượt chính, và **7/7 đều hiểu ĐÚNG**), còn
  **10/10 lượt hiểu sai đều là lượt nối** ⇒ 0 bản thu. Nhận ra được điều này bằng cách đối chiếu: mọi lượt có dòng
  `lượt 1 (ngữ pháp) nghe được` trong log đều có JSON/WAV đi kèm, và không lượt nào khác có.
- Bộ zip cũng dừng ở **10:36:06** (nén lúc 10:39, vòng 30 mục) ⇒ các lượt 10:42–10:45 không có mặt.

**Vá ngay trong 2.75** (để buổi xe sau không lặp lại): `listenOnce` đổi sang `keepPcm = true` và gọi `logHeard`
**khi có chữ** (im lặng sau mỗi lệnh là THƯỜNG — ghi cả thì vòng 30 mục đầy toàn im lặng và đẩy đúng những lượt
đáng nghe lại ra ngoài). Bài canh `VoiceModelTuningWiringContractTest` khoá cả hai vế.

---

## 1. Từng lượt — 22 lượt 10:30–10:37 + 11 lượt 10:42–10:45

`ch` = lượt chính · `nối` = lượt nối (R9) · `t0` = `tieng_bat_dau` (im lặng dẫn đầu, ms).

| giờ | loại | t0 | mô hình in ra | ý định 2.74 | họ | 2.75 |
|---|---|---|---|---|---|---|
| 10:30:14 | ch | 220 | mở vietmap vào ô số hai | `OpenApp(VietMap→ô 2)` | — | ✅ không đổi |
| 10:30:24 | nối | 220 | **áp** vào ô số một | `NO_VERB` | D | ❌ giữ `NO_VERB` (§4.4) |
| 10:30:52 | ch | 220 | mở vietmap vào ô số một | `OpenApp(VietMap→ô 1)` | — | ✅ |
| 10:31:05 | nối | 1020 | mở vietmap vào ô số hai | `OpenApp(VietMap→ô 2)` | — | ✅ |
| 10:31:17 | nối | 412 | mở vietmap | `OpenApp(VietMap)` — **mất ô** | C′ | ❌ (§4.4) |
| 10:31:30 | nối | 412 | **vietmap vào ô số một** | `NO_VERB` | A | **✅ `OpenApp(VietMap→ô 1)`** |
| 10:31:54 | ch | 636 | mở youtube vào ô số một | `OpenApp(YouTube→ô 1)` | — | ✅ |
| 10:32:10 | nối | — | mở vietmap | `OpenApp(VietMap)` — mất ô | C′ | ❌ |
| 10:32:33 | ch | 1020 | bật gió tự động ×3 | `Control(ac_auto=1)` | — | ✅ |
| 10:33:57 | ch | 220 | mở vietmap vào ô số một | `OpenApp(VietMap→ô 1)` | — | ✅ |
| 10:34:10 | nối | — | mở youtube vào ô số hai | `OpenApp(YouTube→ô 2)` | — | ✅ |
| 10:34:28 | nối | — | bật gió mức hai | `Control(fan=2)` | — | ✅ |
| 10:35:13 | ch | 636 | mở vietmap ô số hai | `OpenApp(VietMap→ô 2)` | — | ✅ |
| 10:35:23 | nối | 220 | **mát** vào ô số hai | `NO_VERB` | D | ❌ |
| 10:35:36 | nối | 412 | **viet** vào ô số hai | `NO_VERB` | D | ❌ |
| 10:35:47 | nối | 220 | **mát** vào ô số hai | `NO_VERB` | D | ❌ |
| 10:36:07 | ch | 636 | mở vietma vào ô số hai | `OpenApp(VietMap→ô 2)` | — | ✅ |
| 10:36:17 | nối | 1212 | mở vietmap vào ô số hai | `OpenApp(VietMap→ô 2)` | — | ✅ |
| 10:36:27 | nối | 412 | **bỏ** youtube vào ô số một | `NO_VERB` | B | **✅ `OpenApp(YouTube→ô 1)`** |
| 10:36:37 | nối | 220 | **có yout** vào ô số một | `NO_VERB` | D | ❌ (tên còn 4 ký tự — §4.4) |
| 10:36:52 | nối | **6012** (chạm trần 8 s) | mở vietmap **hai** | `OpenApp(VietMap)` — mất ô | C | **✅ `OpenApp(VietMap→ô 2)`** |
| 10:37:03 | nối | 1820 (chạm trần) | mở youtube vào ô số một | `OpenApp(YouTube→ô 1)` | — | ✅ |
| 10:42:46 | ch | 1436 | mở vietmap | `OpenApp(VietMap)` | E | ↗ (§2.4) |
| 10:42:55 | nối | 1436 | mở vietmap | `OpenApp(VietMap)` | E | ↗ |
| 10:43:03 | nối | 636 | mở vietmap | `OpenApp(VietMap)` | E | ↗ |
| 10:43:12 | nối | 636 | **mở vietmap vào ô số** | `OpenApp(VietMap)` — **không chờ vế sau** | E | **✅ chờ + ghép** |
| 10:44:02 | ch | 1020 | **chửi** ghế lái | `NO_VERB` | F | **✅ `Control(seath=1)`** |
| 10:44:11 | nối | 220 | **chửi** ghế lái | `NO_VERB` | F | **✅** |
| 10:44:21 | nối | 636 | sưởi ghế lái | `Control(seath=1)` | — | ✅ |
| 10:45:07 | ch | 412 | đặt **diêu tiếp** vào ô số một | `MISMATCH` | D | ❌ |
| 10:45:18 | nối | 412 | (đặt) mở **yout** vào ô số một | `MISMATCH` | D | ❌ |
| 10:45:26 | nối | 220 | (đặt) mở youtube vào ô số một | `OpenApp(YouTube→ô 1)` | — | ✅ |

**Tổng 2.75 (chỉ tính 22 lượt 10:30–10:37):** hiểu đúng **12 → 15** ; trong 10 lượt sai, **3 lượt khỏi**
(A · B · C), **7 lượt còn** (đều là họ D/C′ — tên app không còn trong chuỗi, xem §4.4). Cộng đợt 10:42–10:45:
thêm **3 lượt khỏi** (E một lượt · F hai lượt).

---

## 2. Bốn họ + hai họ của đợt 10:42–10:45 — nguyên nhân, `file:line`, bản vá

### 2.1 Họ A · rụng ĐỘNG TỪ đầu câu — *"vietmap vào ô số một"*

`VoiceIntentParser.kt:282` trả `Unknown(NO_VERB)` cho **mọi** câu không có động từ ở vị trí 0. Nhưng mệnh đề
*"vào ô số N"* **không trung tính**: nó chỉ có nghĩa với một loại việc duy nhất (`VoiceIntent.OpenApp`) — không lệnh
xe nào, không datum nào, không câu nhạc/dẫn đường nào nhận nó. ⇒ Câu có **cả** một tên app **và** một mệnh đề ô
hoàn chỉnh thì động từ là thứ duy nhất thiếu, và nó suy ra được.

**Vá**: `VoiceSlotNoVerb.kt` (mới, `:core`) + **đúng một dòng** đổi tại `VoiceIntentParser.kt:282` (tệp đang
**500/500 dòng** ⇒ sửa tại chỗ, không thêm dòng). Ba cổng:

1. phải có mệnh đề ô **có số** (`VoiceTailClause.slotAt`) — *"vietmap"* trần ⇒ không mở gì;
2. phần đầu phải giải ra một app qua **đúng** `VoiceLastResort.pick(OPEN, …)` mà nhánh cuối của bộ phân tích dùng
   (không dựng phép so thứ hai — DRY, và thừa hưởng cả bốn cổng của nó);
3. mệnh đề ô trần (*"vào ô số một"*, không tên) ⇒ `null`: đó là **câu trả lời của vòng hỏi lại**, `VoiceClarify` đã
   mang vế trước theo; nhận ở đây là cướp đường vòng hỏi-đáp.

### 2.2 Họ B · động từ nghe NHẦM — *"**bỏ** youtube vào ô số một"*

Owner nói *"đặt"*, mô hình in *"bỏ"*. Tiếng Việt còn nói *"**cho** X vào Y"* — cùng nghĩa GẮN.

**⚠ Không** thêm `bo` vào `VoiceGrammar.VERBS` (dòng ~113): *"bố cục"* bỏ dấu ra `bo cuc`, nên một động từ `bo` ở
vị trí 0 làm **mọi** câu bố cục không bao giờ tới `VoiceLayouts.match` (`VoiceIntentParser.kt:256`). Thay vào đó
`VoiceSlotNoVerb.pick` thử **bỏ qua tối đa MỘT từ đầu** rồi lặp lại cả ba cổng. Hai từ lạ ⇒ không nhận. Bài canh
`chu BO van la BO CUC` khoá đúng cái bẫy ấy.

### 2.3 Họ C · rụng chữ *"vào ô"*, còn CON SỐ — *"mở vietmap **hai**"*

`VoiceTailClause.slotAt` đòi một `SLOT_HEADS` (`o`/`slot`). Con số còn sót là **thông tin thật người lái đã nói**;
bỏ nó là mở app vào ô sai một cách im lặng.

**Vá**: `VoiceTailClause.bareSlot` — đường lùi **sau** khi `slotAt` trượt, kẹp chặt hơn hẳn:
phần đuôi (sau khi bỏ tiếng đệm) phải **chỉ** còn `<số>` hoặc `<số/thứ> <số>`, và con số phải trong
`1..VoiceSlotPhrases.MAX_SLOT` (khác `slotAt`, cố ý **không** kẹp — con số không có chữ *"ô"* là chứng cứ yếu).
*"mở youtube tập hai"* · *"mở youtube hai mươi"* ⇒ không khớp. Chỉ các nhánh `OpenApp` gọi tới nên lệnh xe /
datum / nhạc không với tới được.

### 2.4 Họ E · vế DỞ ở lượt NỐI — *"mở vietmap vào ô số"* mà **không ai chờ**

Owner cố ý nói *"mở vietmap vào ô"* ⟨ngừng 1,5 s⟩ *"số hai"*, **3 lần**. Cả 3 lần bộ giải mã in ra *"mở vietmap"*
(vế *"vào ô"* trần rụng hẳn) ⇒ `VoiceOpenTurn.isOpen` đúng khi trả `false` — nó không thấy vế dở nào.

Lần thứ **4** (10:43:12) mô hình in ra *"mở vietmap vào ô số"* — một vế dở mà `VoiceOpenTurn.isOpen` **nhận ra từ
2.73** (`VoiceOpenTurn.kt` họ [S] sinh `["vao","o","so"]` từ `VoiceSlotPhrases.SPOKEN`; bài canh
`VoiceCarWav0927Test.ve do cua buoi xe 2709 duoc nhan ra o tang chu` khoá điều đó) — mà lượt ấy vẫn `OpenApp(VietMap)`
**ngay**. Vì sao: **[ĐO]** lượt đó không có một dòng `noi-tiep:` nào trong log, và cũng không có dòng
`sẵn sàng nghe sau … kể từ lúc bấm` ⇒ nó là lượt **NỐI**, và `openTurn = true` chỉ được truyền ở
`VoiceSessionListen.kt:57` (lượt chính). `listenOnce` **chưa bao giờ** giữ lượt.

**Vá**: `VoiceSessionTurns.listenOnce` truyền `openTurn = true`. Bật cho **cả hai** đường nối (hỏi lại + hội thoại):
câu trả lời *"hai"* / *"đồng ý"* không phải vế dở ⇒ đóng lượt ngay như hôm nay; cổng XÁC NHẬN
(`listenForConfirm`) **không** bật — ở đó một vế dở vô nghĩa. Bài canh
`VoiceOpenTurnWiringContractTest.chi cac luot nhan MOT CAU cua nguoi lai bat open-turn` đổi kỳ vọng kèm phép đo bác
bỏ kỳ vọng cũ.

⚠ Ba lượt đầu (*"mở vietmap"*, vế *"vào ô"* rụng ở tầng NGHE) thì bản vá này **không** đỡ: không có vế dở trong
chuỗi thì không có gì để chờ. Xem §5 về đề nghị *"hotword cho cụm đầu trần"* và vì sao nó **bị từ chối**.

### 2.5 Họ F · lẫn âm `sưởi` → `chửi`

Hai lượt liền ra *"chửi ghế lái"* ⇒ `NO_VERB`; lượt thứ ba mô hình in đúng *"sưởi ghế lái"* và câu chạy
(`Control(seath=1)`) ⇒ sai ở tầng **NGHE**, không ở câu nói. Ba nhóm quy luật của `VoicePhoneticConfusions` không
phủ được cặp này: bỏ dấu ra `suoi`/`chui` — `s`↔`ch` **không** nằm trong `INITIALS` (ở đó chỉ có `s`↔`x` và
`tr`↔`ch`), và `uoi`↔`ui` không phải một cặp vần đã khai.

**Vá**: một dòng `OBSERVED` (cơ chế có sẵn, không nhánh `if` nào mới) + một mức bằng chứng mới `Seen.CAR` —
*"nhật ký của xe đang lăn bánh"*, mức duy nhất có thêm vế *"người lái nói lại cùng câu và lần sau chạy đúng"*.

---

## 3. Họ D — **im lặng dẫn đầu phá bộ giải mã**, và đó là một bản vá ở tầng NGHE

### 3.1 Cơ chế, đo bằng bản thu THẬT + kích thích dựng [ĐO máy ảo]

Lấy bản thu **đã hiểu đúng** của chính buổi xe (`…-103014`, *"mở vietmap vào ô số hai"*, mốc bắt đầu tiếng 220 ms),
nối thêm **6 000 ms tiếng nền thật của chính bản thu ấy** vào đầu rồi giải mã **cả cửa sổ** (= hành vi 2.74):

| cửa sổ đưa vào bộ giải mã | mô hình in ra | ý định |
|---|---|---|
| bản gốc (im lặng dẫn đầu 220 ms) | mở vietmap vào ô số hai | `OpenApp(VietMap→ô 2)` |
| + 1 500 ms nền dẫn đầu | **bỏ bỏ việc map** vào ô số hai | `Unknown` |
| + 3 000 ms | **bỏ bỏ bỏ việt nam** vào ô số hai | `Unknown` |
| + 6 000 ms | **bỏ bỏ việt nam** vào ô số hai | `Unknown` |

⇒ Cùng tiếng nói, chỉ khác phần dẫn đầu, và mô hình sinh ra **đúng bộ từ mà xe đã in ra**: *"bỏ"* (lượt 10:36:27),
*"có"* (10:36:37), *"việt/viet"* (10:35:36), *"áp"* (10:30:24). Đây là quan hệ **[ĐO]**, cùng họ với bảng §6 của
`voice-stream-eval-2026-09-16.md` (đuôi im lặng 0 s ⇒ 22/25 · 4 s ⇒ 6/25) — chỉ là im lặng ở **đầu**.

### 3.2 Quét PRE-ROLL — con số 300 ms là **đo được**, không phải biên cho chắc

Cắt chính tệp *"+6 000 ms"* tại `mốc bắt đầu tiếng − pre-roll` rồi giải mã:

| pre-roll | …-103014 (*"mở vietmap vào ô số hai"*) | …-103153 (*"mở youtube vào ô số một"*) |
|---|---|---|
| 0 ms (cắt **đúng tại** mốc) | **việt áp** vào ô số hai ❌ | **có** youtube vào ô số một ❌ |
| 150 ms | **việt nam** vào ô số hai ❌ | mở youtube vào ô số một ✅ |
| **300 ms** | **mở vietmap vào ô số hai** ✅ | **mở youtube vào ô số một** ✅ |
| 500 ms | **bỏ** việt nam vào ô số hai ❌ | mở youtube vào ô số một ✅ |
| không cắt (2.74) | bỏ bỏ việt nam vào ô số hai ❌ | mở youtube vào ô số một ✅ |

Hai đầu đều xấu, và **cả hai đầu đều đã có lời giải thích từ trước**:
- pre-roll **0** = đúng chế độ `segment` mà §8 đo được **8/25** (*"VAD mở đoạn muộn ⇒ nuốt mất từ đầu câu"*) —
  ở đây nó nuốt đúng chữ *"mở"* và biến *"vietmap"* thành *"việt áp"*;
- pre-roll **quá dài** = nạp lại im lặng vào mô hình, tức bảng §6.

`PRE_ROLL_MS = 300` = **3 × `MIN_SPEECH_MS`** (100 ms — lượng tiếng tối thiểu để Silero **mở** một đoạn, tức đúng
độ trễ mà luật mở đoạn tự gây ra). Bài canh `VoiceVadTrimTest.preroll phu duoc do tre cua luat mo doan` khoá quan hệ
này bằng một bất biến, không bằng một con số chép tay.

### 3.3 Cổng cắt đầu 1 200 ms — chọn để **không ca đang chạy nào đổi**

Mốc bắt đầu tiếng của **cả 30 bản thu 26–27/09**: 220 · 412 · 636 · 1 020 ms. Mốc của các lượt **hỏng** trên xe:
1 212 · 1 436 · 1 820 · 6 012 ms. Cổng đặt ở **1 200 ms** — trên mọi giá trị đã nghe đúng, dưới mọi giá trị đã nghe
sai. Bài canh `im lang dan dau NGAN thi khong cat gi` + `preroll phu duoc…` (`in 1_021..1_211`) khoá cả hai biên.

### 3.4 Đo lại A/B trên máy ảo — **cùng một bản build, chỉ đổi hai hằng số**

`PRE_ROLL_MS = 0` + `HEAD_SILENCE_CUT_MS = 99_000` cho ra **đúng** hành vi 2.74 (không cắt đầu bao giờ) ⇒ A/B sạch.

| phép đo | kết quả |
|---|---|
| **30 bản thu THẬT**, 2.74 vs 2.75, từng ký tự | **29/30 y hệt**; 1 đổi: `…-175208` *"đã mở ứng dụng mở"* → *"mở"* (cả hai `Unknown`, không đổi ý định — bớt một ảo giác) |
| bắc cầu quanh cổng, **cùng kích thích** (nối im lặng để mốc = 1 100 / 1 300 / 2 000 / 4 000 ms) | 1 100 (cổng TẮT) ✅ · 1 300 · 2 000 · 4 000 (cổng BẬT, cắt 912/1 712/3 536 ms) ✅ — cả 8 tệp (2 câu × 4 mốc) ra **đúng nguyên văn** |
| nối im lặng 1 500 / 3 000 / 6 000 ms, bản 2.75 | **6/6 đúng nguyên văn** (cắt 1 328 · 2 736 · 5 936 · 1 712 · 3 312 · 6 320 ms) |

### 3.5 Bản vá

| tệp | việc |
|---|---|
| `VoiceVadTrim.headStartSamples` (`:core`, thuần) | `mốc bắt đầu tiếng − PRE_ROLL_MS`, **chỉ khi** im lặng dẫn đầu > `HEAD_SILENCE_CUT_MS`; không có đoạn nào ⇒ `0` |
| `VoiceVadTrim.tailRange` | thêm pre-roll cho **vế SAU** của VOICE-OPEN-TURN (trước đây cắt **đúng tại** mốc = chế độ 8/25), chặn dưới ở **điểm hết tiếng của vế trước** |
| `VoiceVad.headStartSamples` · `VoiceTurnEndpoint.headStart` | bề mặt; đường LÙI (RMS) trả **0** — nó không biết tiếng bắt đầu ở đâu (`tieng_bat_dau=-1` ở 189/300 lượt) nên không được cắt đầu |
| `VoiceCapture` (2 chỗ) · `VoiceOpenTurnArm.arm` | `rec.finalResult(trim)` → `rec.rangeResult(ep.headStart(fed), trim)` |
| `VoiceWavProbe` | đường đo đi qua **cùng** phép cắt (R14) — nếu không, off-car thôi nói về phiên thật ở đúng lượt bệnh |
| nhật ký | `bo_dau=<ms>` thêm vào dòng `cắt:` và `ngắt câu:` ⇒ buổi xe sau đọc được một dòng là biết cổng có nổ không |

**Không** thêm núm prefs: hai hằng số đã chốt bằng phép đo ở §3.2/§3.3, và mọi tệp test-bridge đang do một phiên
khác sửa (tránh xung đột ghi).

---

## 4. Vá xong còn gì — và vì sao **cố ý** không chữa

### 4.1 `VÀO Ô` / `VÀO Ô SỐ` làm hotword — **BỊ TỪ CHỐI, có bằng chứng**

Đề nghị: cho cụm đầu trần (*"vào ô"*, *"vào ô số"*) vào tệp hotword để bộ giải mã giữ chúng (họ E, §2.4).
**Không làm**, vì `SherpaHotwords.dropPrefixes` sẽ bỏ đúng những dòng ấy (chúng là **tiền tố theo từ** của
*"VÀO Ô SỐ MỘT"*), và bộ lọc đó **sinh ra từ một phép đo**: [ĐO 2026-09-16 host, 25 WAV] bỏ 155 dòng tiền tố ⇒
20/25 → 21/25, vì *"khớp trọn cụm ngắn là đồ thị về gốc, phần đuôi không còn đường cộng điểm"*. Thêm `VÀO Ô SỐ` vào
tệp là **lấy bias khỏi** `VÀO Ô SỐ MỘT` — tức làm hỏng đúng cái vế ta đang chữa. Cùng lẽ đã ghi ở
`VoiceSlotPhrases` KDoc (*"vì sao KHÔNG có dòng «ô» / «vào ô» trần"*).

Đường đi đúng cho họ E, **chưa đo, cần một vòng thiết kế**: khi lượt đã giữ và **có** một đoạn tiếng mới sau điểm
ngắt (`ep.tailRange` ≠ `null`), giải mã + ghép vế sau **dù vế trước trông đủ nghĩa**, rồi chỉ nhận câu ghép nếu nó
phân tích ra một ý định đầy đủ hơn. [ĐO xe 10:42:46] lượt ấy **đã** thu được vế sau (`tieng_dut` 2 400 → 4 384 ms
sau `flush`) rồi **bỏ đi**. Ghi vào backlog, không làm trong 2.75 (đổi đường đã chạy tốt ⇒ cần phép đo riêng).

### 4.2 Tên app rụng còn ≤ 4 ký tự — *"viet"* · *"yout"* · *"mát"* · *"áp"*

`VoiceNameFuzzy` đòi **cả hai** chuỗi ≥ 5 ký tự và lệch ≤ 2. *"viet"* (4) và *"yout"* (4) trượt cổng độ dài;
*"mát"*/*"áp"* thì không còn liên quan gì tới một tên app. Nới cổng là mời mọi câu lạ mở app (ca âm đã khoá:
*"mở netfliy"* với hai app *Netflix*/*Netflax* ⇒ **không** mở gì). Đường đúng: **hỏi lại** — 2.74 đã có
(`VoiceClarify`), và [ĐO xe 26/09] *"đặt yout tiếp…"* đi đúng đường đó (`clarify:true` trong JSON).
Một hướng hẹp **chưa làm**: *"chuỗi ≥ 4 ký tự là **tiền tố** của đúng MỘT nhãn app, và câu có mệnh đề ô"* ⇒ backlog.

### 4.3 *"mở vietmap"* mất trắng vế ô (C′, 2 lượt)

Chuỗi không còn một mẩu thông tin nào về ô ⇒ tầng chữ không có gì để cứu. Chờ họ E (§4.1) và họ D (§3).

### 4.4 Tổng kết mức bằng chứng

| khẳng định | mức |
|---|---|
| 10/10 lượt sai là lượt nối; lượt nối không giữ tiếng | **[ĐO xe + đọc mã]** |
| im lặng/nền dẫn đầu ≥ 1,5 s làm mô hình sinh *"bỏ/có/việt/áp"* | **[ĐO máy ảo]** trên bản thu thật + kích thích dựng |
| chính cơ chế ấy giải thích 5 lượt họ D của xe | **[SUY]** — bộ từ khớp hệt, nhưng 5 lượt ấy **không có bản thu** để chốt |
| pre-roll 300 ms là điểm tối ưu | **[ĐO máy ảo]**, 2 câu × 5 mức |
| cổng 1 200 ms không đổi ca nào đang chạy | **[ĐO]** 29/30 bản thu y hệt từng ký tự |
| họ D còn lại sẽ khỏi khi có bản thu | **[CHƯA BIẾT]** |

---

## 5. Chất lượng

| phép đo | kết quả |
|---|---|
| `:core` + `:app` unit test | **4 317 test · 0 fail** (đếm từ `*/build/test-results/*/*.xml`) |
| test mới | `VoiceCarWav0927Test` **13** · `VoiceVadTrimTest` +**7** (10 → 17) |
| bài canh dây nối phải đổi kỳ vọng (kèm phép đo bác bỏ kỳ vọng cũ) | 4: `VoiceOpenTurnWiringContractTest` · `VoiceVadWiringContractTest` · `VoiceLoopGuardWiringContractTest` · `VoiceModelTuningWiringContractTest` |
| E2E `scripts/emulator/voice-e2e.sh` | xem §6 |
| trần 500 dòng | `VoiceSessionTurns.kt` 500 → **458** (tách `VoiceSessionLog.kt`, **pure move** hai nửa nhật ký); `VoiceIntentParser.kt` giữ **500** (sửa tại chỗ, 0 dòng thêm) |

---

## 6. 🚗 Buổi xe sau — đo gì, đọc dòng nào

1. **Họ A/B/C khỏi chưa**: nói *"vietmap vào ô số hai"* · *"bỏ youtube vào ô số một"* · *"mở vietmap hai"* — mong đợi
   `OpenApp(… → ô N)` ở cả ba.
2. **Cổng cắt đầu có nổ không**: bấm mic rồi **chờ 2 giây** mới nói. Đọc `KachiVoiceTiming`:
   `cắt: … bo_dau=<ms>` phải > 0 và ≈ `tieng_bat_dau − 300`.
3. **Lượt NỐI đã chờ vế dở chưa**: nói *"mở vietmap vào ô"* ⟨ngừng 1,5 s⟩ *"số hai"* **ở lượt thứ hai** của một
   phiên. Mong đợi một dòng `noi-tiep:` (lượt nối trước đây **không có** dòng nào).
4. **Bản thu của lượt nối**: sau buổi đo, xuất zip và kiểm có WAV cho các lượt nối — đây là điều kiện để chẩn đoán
   họ D ở buổi off-car sau.
5. **Họ F**: nói *"sưởi ghế lái"* vài lượt; nếu lại ra *"chửi"* thì câu vẫn phải chạy.
6. Chưa đo: `voice_vad_min_silence_ms 1100` (trần đã nới từ 2.73) — độ trễ chốt câu của vùng ngừng-để-nghĩ
   720–1 060 ms.
