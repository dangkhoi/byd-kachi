# Voice 2.76 — làn ghi tuần tự (R5) · ghép vế sau khi vế trước đã đủ nghĩa (R6) · tiền tố ≥ 4 ký tự + mệnh đề ô (R7)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 · **Mục đích**: đóng ba vòng thiết kế voice còn mở sau buổi xe
> 27/09 (spec `docs/specs/kachi-276-closing.html` R5 · R6 · R7, làn L3). Mỗi mục: số đo gốc → cơ chế → quyết định
> → test → 🚗 một dòng kiểm trên xe. Buổi trước: `offcar-2026-09-26/voice-car-0927.md` (2.75).

---

## 0. Ba việc, một bảng

| # | Bệnh (nguồn) | Cơ chế 2.76 | Tệp | Mức bằng chứng |
|---|---|---|---|---|
| R5 | Câu ghép *"tăng gió rồi tắt điều hoà"*: vế 2 ghi vào **giữa nhịp 400 ms** của vế 1 (review Pass 1 kachi-274 [P2]) | `VoiceWriteLane` + `runFrom` nối vế bằng **lời gọi lại** | `core/…/voice/VoiceWriteLane.kt` · `app/…/VoiceDispatcher.kt` · `app/…/VoiceControlDispatch.kt` (tách) | [SUY] từ tiền đề [ĐO] của `DEFAULT_GAP_MS`; thứ tự sai **tái hiện được** bằng test trên 2.75 |
| R6 | *"mở vietmap"* ⟨ngừng⟩ *"vào ô số hai"*: vế sau **đã thu** rồi bị vứt vì vế trước đủ ([ĐO xe 10:42:46]) | `VoiceOpenTurn.attach/refine`; `VoiceOpenTurnArm.result` + `VoiceWavProbe` hỏi `tailRange` **trước** khi giữ/ghép | `core/…/voice/VoiceOpenTurn.kt` · `app/…/voice/VoiceOpenTurnArm.kt` · `app/…/voice/VoiceWavProbe.kt` | [ĐO xe] log + [ĐO máy ảo] phát lại bản thu thật có chèn quãng ngừng (§2.4) |
| R7 | Tên app rụng còn *viet* / *yout* ([ĐO xe 10:35:46 · 10:36:36]) — 2.75 cố ý giữ Unknown | `VoiceAppPrefix` (3 cổng), nhánh áp chót của `VoiceLastResort` | `core/…/voice/VoiceAppPrefix.kt` · `VoiceLastResort.kt` | [ĐO xe] chuỗi mô hình in ra; không có bản thu (lượt sai của 27/09 không được ghi — §3.3) |

Không đổi gì khác trong `voice/`. `VoiceDispatcher` 496 → 417 dòng; `VoiceControlDispatch` 155; mọi tệp ≤ 500.

---

## 1. R5 — VOICE-WRITE-LANE

### 1.1 Số đo / bối cảnh
- [ĐO] `ActionMacros.DEFAULT_GAP_MS = 400` tồn tại vì *"bắn hai lệnh HAL liên tiếp thì lệnh sau rơi"* (gói lệnh, 09-16).
- UX4 (2.74) đưa vế *rời AUTO* của giọng nói thành **2 lệnh + nhịp 400 ms trên luồng nền** (`VoiceClimateStep`), hàm trả về
  trước lệnh thứ hai. `VoiceDispatcher.runFrom` (tới 2.75) là vòng `while` trên luồng gọi ⇒ vế kế tiếp ghi ngay.
- Thứ tự thật trên 2.75 với *"tăng gió rồi tắt điều hoà"* (xe đang AUTO, gió 1): `[ac_auto OFF (vế 1), ac_auto OFF (vế 2),
  …400 ms…, fan=2]` — lệnh vế 2 rơi vào giữa nhịp. Bài `VoiceWriteLaneDispatchTest` ca 1 là bài **đỏ trên 2.75** (fired
  đã có lệnh vế 2 trước khi ai chạm làn nền) — đây là phép tái hiện, không phải suy đoán.
- [SUY] lệnh mức gió *có thể* bị xe bỏ trong tình huống ấy — chưa có một lượt đo trên xe cho đúng câu ghép này (🚗 §1.5).

### 1.2 Cơ chế
- `VoiceWriteLane` (`:core`, thuần): `submit { done -> … }`; việc N+1 chỉ chạy khi việc N gọi `done()`. FIFO · `done` một
  lần · việc ném tự thả làn. Không luồng, không đồng hồ: chỗ chờ thật vẫn ở lambda nền của `VoiceClimateStep` (bài canh
  `nhip cho 400ms…` giữ nguyên).
- `VoiceDispatcher.execute` nộp **cả câu** vào làn; `runFrom(intents, from, labels, done)` đệ quy bằng `next`; vế CONFIRM hỏi
  rồi chạy tiếp từ callback (cổng an toàn OQ4 không đổi — `VoiceDispatcherSafetyTest` xanh nguyên).
- `run(intent, labels, next)`: chỉ **hai** nhánh ghi HAL có thể bất đồng bộ giữ `next` lại — nút xe (`VoiceControlDispatch.run`,
  `done` đứng cuối `finish`, sau nhịp 400 ms) và gói lệnh (`runMacro`, `onUi(next)` trong `finally`). Nhánh còn lại gọi `next`
  ngay ⇒ thứ tự/lời đáp 2.75 y nguyên. `VoiceReadback` (đọc lại 300 ms) **không** giữ `next`: nó chỉ đọc.
- Làn là của **riêng một `VoiceDispatcher`** (dựng lại mỗi lượt nói), không singleton: một `done` bị quên chỉ ghim phần còn
  lại của MỘT câu, không ghim mọi lệnh giọng nói tới khi khởi động lại đầu xe. Chồng lấn giữa hai lượt nói: **[SUY]** không
  xảy ra vì giải mã một lượt ≥ 1,3 s [ĐO xe] > nhịp 400 ms — **chưa đo trực tiếp**, và với vế *gói lệnh* (ngủ tới 1,2 s)
  khoảng cách ấy hẹp hơn hẳn. Từ bản vá soát 27/09, lượt nói **chờ làn cạn** mới chốt (`onSettled`) nên cửa sổ chồng lấn
  cũng hẹp lại theo — nhưng vẫn là [SUY], không phải [ĐO].

### 1.3 Quyết định
- **Không** đưa ba nhánh một lệnh xuống nền (đường đã chạy hiện trường, CLAUDE.md §6); bài `ba nhanh MOT lenh khong bi day
  xuong nen` giữ nguyên kỳ vọng.
- **Không** gộp với làn `tile-write` của ngón tay (`ControlTileWrite`/`MacroExec.submitSerial`): spec R5 nói *"mọi lệnh ghi từ
  giọng nói"*; gộp hai bề mặt là đổi đường của ngón tay ngoài phạm vi phép đo → nợ §5.
- Tách `runControl` **nguyên văn** sang `VoiceControlDispatch` (trần 500 dòng), chỉ thêm `done()` ở 5 lối ra.

### 1.4 Test
- `core` `VoiceWriteLaneTest` (5): FIFO theo callback · chạy ngay khi rỗi · `done` hai lần chỉ tính một · ném không ghim ·
  nộp từ trong `done`.
- `app` `VoiceWriteLaneDispatchTest` (5): AC của R5 tất định (giữ lambda nền: fired **rỗng** tới khi làn nền chạy ⇒
  `[toggle:ac_auto:false, step:fan:2, toggle:ac_auto:false]`) · mốc giờ luồng thật (nhịp ≥ 350 ms, lệnh vế 2 sau lệnh mức) ·
  5 lối ra sớm của nút vẫn báo xong · gói lệnh giữ vế sau · một vế đồng bộ ghi ngay.
- Bài canh cập nhật: `VoiceCommandWiringContractTest` (nguồn gộp thêm `VoiceControlDispatch.kt`; nhánh `runControl(intent,
  next)`/`runMacro(intent, next)`) · `LayeringRulesTest` (lý do tệp thuần ở `:app`).

### 1.5 🚗
- Xe đang AUTO: nói *"tăng gió rồi tắt điều hoà"* ⇒ màn AC gốc phải thấy **mức gió +1 rồi AC tắt** (không phải AC tắt mà mức
  cũ); log `KachiVoice` hai câu trả lời đúng thứ tự.

---

## 2. R6 — Ghép vế sau khi vế trước ĐÃ đủ nghĩa

### 2.1 Số đo
- [ĐO xe 2026-09-27 10:42:46, `KachiVoiceTiming`] *"mở vietmap"* ⟨ngừng⟩ *"vào ô số hai"*: `noi-tiep: vế trước đủ nghĩa ⇒
  đóng lượt`, nhưng bộ ngắt câu đã thu vế sau (`tieng_dut` 2 400 → 4 384 ms sau `flush`) — vì vế sau bắt đầu trong lúc lượt
  giải mã vế trước (1,3–2 s) còn chạy. `result()` của 2.75 `return` **trước** khi hỏi `tailRange` ⇒ khúc tiếng bị vứt.
- Không có WAV của lượt này (vòng zip dừng 10:36:06). Phép đo off-car dùng bản thu thật 26/09 `…-184201` (*"mở vietmap ⟨…⟩
  vào ô số một"*, xe nghe gộp thành *"mở vietmap một"*), cắt làm hai khúc + chèn im lặng (§2.4).

### 2.2 Cơ chế
- `VoiceOpenTurn.attach(head, tail)`: vế dở ⇒ `join` như 2.74 (không đổi); vế đủ ⇒ `refine`: chỉ nhận câu ghép khi
  `parseOne(joined)` là **cùng ý định, đầy đủ hơn** `parseOne(head)` — hôm nay: `OpenApp` có thêm `slot` (`before.copy(slot =
  after.slot) == after`). Lệnh khác · tiếng ồn · mệnh đề ô cụt · đã có ô ⇒ `null` ⇒ giữ vế trước.
- `VoiceOpenTurnArm.result`: hỏi `ep.tailRange` **trước**; có vế sau ⇒ giải mã + `attach`; `Outcome.tail` mang chữ đã bỏ để
  log/cầu `wav` còn thấy. `stopReading` **không đổi**: vế đủ vẫn thoát ngay ⇒ **không thêm một mili-giây NGHE** (F2b *"nhanh
  đúng như 2.73"* giữ). `VoiceWavProbe.openTurn` đi cùng luật.
- ⚠ **[P2 · soát Opus 27/09]** câu trên chỉ đúng cho thời gian **NGHE**. Thời gian **GIẢI MÃ** thì CÓ thêm: một lượt
  `rangeResult` cho vế sau, **[CHƯA BIẾT]** bao nhiêu ms trên xe (🚗 đọc dòng `giải mã … ms` của `VoiceCapture` buổi tới) và
  nó nằm trên đường tới hành động. Bản vá gác bằng `VoiceOpenTurn.mayAttach(head)` (:core): chỉ giải mã khi `attach` còn có
  đường nhận vế trước (vế dở, hoặc *mở app chưa có ô*) ⇒ *"bật đèn đọc"* + một tiếng trong cabin không còn phải trả giá một
  lượt giải mã. KHÔNG gác theo cửa sổ `OPEN_JOIN_WINDOW_MS` hay độ dài đoạn: ca [ĐO] 10:42:46 chính là một vế sau bắt đầu
  TRONG lúc lượt giải mã vế trước còn chạy, hai cổng ấy sẽ bỏ đúng nó.

### 2.3 Quyết định
- Không nới `refine` sang lệnh xe/nhạc/dẫn đường: [CHƯA BIẾT] chưa có lượt đo nào cho chúng; luật viết theo hình dạng dữ
  liệu để thêm một nhánh `when` khi có số.
- `refine` dùng từ vựng **tĩnh** (không nhãn app đã cài — tầng nghe không cầm danh sách app): app chỉ-có-trên-máy ngoài bảng
  đích ⇒ hai vế cùng Unknown ⇒ giữ vế trước (đúng 2.75). Nợ §5.
- `VoiceOpenTurnCasesTest` (2.74) **không đổi** kỳ vọng (không có phép đo mới nào bác nó).

### 2.4 Test + phép đo máy ảo
- `core` `VoiceOpenTurnTest` (+3): ghép ca 10:42:46 · 6 ca giữ vế trước · vế dở y `join`. `VoiceCarWav0927Test` (+1) khoá ca
  10:42:46. `app` `VoiceOpenTurnWiringContractTest`: `attach` ở cả arm + probe; `tailRange` đứng trước `attach`; cấm dòng
  `if (!isOpen(head)) return` quay lại.
- [ĐO máy ảo API 29, cầu `wav`, bản 2.76 (177) vehicleTest] phát lại `…-184201` cắt `[0..1300]` + im lặng + `[1300..3400]`:
  
  | tệp | im lặng chèn | `head` | `tail` | `heard` (đã ghép) | ý định |
  |---|---|---|---|---|---|
  | `r6-h1300-g700` | 700 ms | *mở vietmap* | (không có đoạn) | *mở vietmap* | OpenApp(VietMap) — y 2.75 |
  | `join-1300-900-3400` (26/09) | 900 ms | *mở vietmap* | (không có đoạn) | *mở vietmap* | y 2.75 |
  | `r6-h1300-g1000` | 1 000 ms | *mở vietmap* | (không có đoạn) | *mở vietmap* | y 2.75 |
  | **`r6-h1300-g1500`** | **1 500 ms** | *mở vietmap* | ***ô số một*** | ***mở vietmap ô số một*** | **OpenApp(VietMap → ô 1)** |

  Đọc: (a) khi bộ ngắt câu của đường đo **có** đoạn sau (`tailRange ≠ null`), vế đủ nghĩa *"mở vietmap"* nay **ghép** được vế
  sau đã thu — đúng cơ chế R6 chạy trên bộ nhận dạng thật; 2.75 với cùng tệp trả `tail=""` (bảng `replay-join.tsv` 26/09).
  (b) ba tệp quãng ngừng ≤ 1 000 ms đường đo **không tách** thành đoạn thứ hai ⇒ không có vế sau ⇒ **y hệt 2.75**, tức R6 không đổi
  một câu nào không có vế sau. [CHƯA BIẾT] ngưỡng tách đoạn của đường đo WAV so với vòng đọc micro thật (200 ms/khối) — trên xe
  10:42:46 quãng ngừng đã cho hai đoạn; đó là 🚗 §2.5.

### 2.5 🚗
- Nói *"mở vietmap"*, ngừng ~1 s, nói *"vào ô số hai"* ⇒ VietMap vào **ô 2**; log `noi-tiep: ghép "mở vietmap" + "vào ô số
  hai"`; và *"mở vietmap"* rồi im ⇒ mở ngay như 2.75 (không chờ thêm).

---

## 3. R7 — Tiền tố ≥ 4 ký tự + mệnh đề ô có số

### 3.1 Số đo
- [ĐO xe 27/09] họ D: *"áp vào ô số một"* (10:30:24) · *"mát vào ô số hai"* ×2 (10:35:23 · 10:35:35) · *"viet vào ô số hai"*
  (10:35:46) · *"có yout vào ô số một"* (10:36:36). 2.75 giữ Unknown cả năm (`VoiceNameFuzzy` đòi ≥ 5 ký tự), hỏi lại.
- [ĐO máy ảo 2.75] cơ chế sinh *bỏ/có/việt/áp* là im lặng dẫn đầu ≥ 1,5 s; pre-roll 300 ms đã vá phần lớn, nhưng khi vẫn
  xảy ra thì hai lượt *viet*/*yout* có đủ 4 ký tự để giải — hai lượt *mát*/*áp* thì không.

### 3.2 Cơ chế — ba cổng (`VoiceAppPrefix.pick`)
1. Phải có **mệnh đề ô CÓ SỐ** ngay sau phần tên (`slotAt` trên đuôi bắt đầu bằng `SLOT_WORDS`; con số trần *"mở yout hai"*
   **không** đủ).
2. Tiền tố (ghép liền, bỏ dấu) **≥ 4 ký tự** — *mát*(3) · *áp*(2) ⇒ Unknown như cũ.
3. **Duy nhất theo nhãn** trên cùng danh sách ứng viên của `VoiceLastResort.candidates` (nhãn đã cài + bảng đích); ngoại lệ có
   lý: các nhãn khớp **lồng nhau theo từ** (*YouTube* ⊂ *YouTube Music*) ⇒ nhãn ngắn nhất thắng (người nói rụng đuôi của
   chính từ *youtube*; nếu định nói *YouTube Music* thì còn cả từ *music* phải rụng). Hai nhãn không lồng (*Netflix*/*Netflax*)
   ⇒ Unknown ⇒ hỏi lại.
- Vị trí: `VoiceLastResort.pick` = spokenApp → appFuzzy → **VoiceAppPrefix** → VoiceProfileNames. `VoiceSlotNoVerb` (câu không
  động từ) tới được cùng đường ⇒ *"có yout vào ô số một"* ra `OpenApp(YouTube → 1)`.

### 3.3 Quyết định
- Kỳ vọng bài `VoiceCarWav0927Test.ten app rung thi KHONG duoc doan` **đổi** cho *viet*/*yout* (giữ cho *mát*/*áp*), lý do:
  owner duyệt R7 + hai lượt xe đo được nằm đúng khe ba cổng. Bài mới `ten app rung con tien to 4 ky tu…`.
- Không có WAV để phát lại (lượt nối sai của 27/09 không được ghi — 2.75 mới bật `keepPcm` cho lượt nối) ⇒ R7 chỉ có bằng chứng
  tầng chữ (chuỗi mô hình in ra) + test; ASR có in ra đúng *viet*/*yout* trên xe lần sau không là 🚗.

### 3.4 Test
- `core` `VoiceAppPrefixTest` (7): 2 ca dương của spec · cổng 2 (*mát/áp*) · cổng 1 (tiền tố trần, ô thiếu số, số trần) · cổng 3
  (Netflix/Netflax ⇒ Unknown, một nhãn ⇒ mở) · động từ đóng · đường cũ không đổi (đủ tên, khớp mờ *vietma*).

### 3.5 🚗
- Nói nhanh *"mở vietmap vào ô số hai"* / *"mở youtube vào ô số một"* ×5; lượt nào log in *"viet …"*/*"yout …"* phải ra đúng app
  đúng ô (không hỏi lại); lượt in *"mát/áp"* vẫn hỏi lại.

---

## 4. Test tổng + harness

- `:core:test --tests '*Voice*'` và `:app:testDebugUnitTest --tests '*Voice*' '*LayeringRulesTest*'` (đếm từ XML):
  `core` 60 lớp `*Voice*` · **598 test · 0 fail** (mới: `VoiceWriteLaneTest` 5 · `VoiceAppPrefixTest` 7; `VoiceOpenTurnTest`
  17 (+3) · `VoiceCarWav0927Test` 15 (+2)) · `app` 37 lớp · **330 test · 1 fail** — fail duy nhất là
  `LayeringRulesTest.so file thuan…` do tệp `ClusterNavBridgeCamera.kt` của làn L1 (không thuộc L3; mọi lớp `*Voice*` của app xanh,
  `VoiceWriteLaneDispatchTest` 5/5 · `VoiceRelativeStepTest` 17/17 · `VoiceDispatcherSafetyTest` 8/8 · `VoiceCommandWiringContractTest`
  12/12 · `VoiceOpenTurnWiringContractTest` 7/7 · `VoiceOpenTurnCasesTest` 3/3).
- `scripts/emulator/voice-e2e.sh` (bản 2.76 (177) vehicleTest, `install -r`): **T1 106/106 PASS** · **T2 23/27** nghe đúng nguyên văn — **y hệt** ba lượt trước (26/09
  00:20 · 20:41 · 22:23): 4 ca lệch cũ là tên riêng TTS (*Bitexco→bico*, *Waze→loa e*), *w13 âm lượng→âm lửa* (nút `vol` đã bỏ
  1.90, ý định Unknown như trước) và lỗ *w22* đã biết. Không ca nào đổi.

---

## 5. Nợ còn lại

- **Làn ngón tay + giọng nói chưa là một**: `ControlTileWrite` (làn `tile-write`) và `VoiceWriteLane` là hai làn; một cú chạm ô
  trong nhịp 400 ms của một câu nói vẫn có thể chen. Chưa có phép đo ca này; gộp là đổi đường ngón tay (CLAUDE.md §6) ⇒ chờ
  số đo.
- `VoiceOpenTurn.refine` dùng từ vựng tĩnh: app chỉ-có-trên-máy (ngoài bảng đích) không được ghép ô ở vế sau — cần đưa danh
  sách app xuống `VoiceOpenTurnArm` (qua `VoiceCapture`) nếu có ca đo.
- `refine` chỉ biết `OpenApp` + ô. Lệnh xe kiểu *"tăng gió"* ⟨ngừng⟩ *"hai mức"*: [CHƯA BIẾT], chưa đo.
- R7 chưa có bản thu để phát lại; khi có (2.76 lượt nối `keepPcm=true`), thêm vào `VoiceCarWav0927Test` dạng WAV replay.

---

## 6. Sau soát Opus 2026-09-27 (Pass 1) — hai bản vá của làn này
- **[P1] Lượt nói chốt SAU khi làn ghi cạn.** `VoiceSession.execute` gom mảng lời đáp ngay sau `d.execute`, mà từ R5 hàm ấy
  trả về **trước** khi vế bất đồng bộ ghi xong ⇒ với *"tăng gió rồi tắt điều hoà"* mảng RỖNG ⇒ `speakLines` thoát ngay ⇒
  `onReplyDone` mở micro nối trong vài ms ⇒ hai câu trả lời thật (≈ 400 ms sau) bị cổng `micOpen` bỏ **im**, kể cả
  `VoiceReply.notOnThisCar/failed`. Người lái nghe đúng một tiếng chuông cho hai thay đổi trên xe; 2.75 còn đọc được vế 2.
  Vá: `VoiceDispatcher.execute(intents, onSettled)` gọi lại **đúng một lần** khi `runFrom` đi hết câu HOẶC dừng ở hộp hỏi lại
  (phần còn lại chờ NGƯỜI LÁI — giữ y 2.75, không treo tấm chữ suốt lượt hỏi/đáp), cộng lưới an toàn
  `VoiceSession.TURN_SETTLE_MS` = 4 s (gói dài nhất đang khai ngủ 3 × 400 ms; đường tra mạng KHÔNG giữ làn). Khoá:
  `VoiceWriteLaneDispatchTest` ca 6 + ba assert mới trong `VoiceFastNaturalWiringContractTest`.
- **[P2] Cổng rẻ trước lượt giải mã vế sau** — xem ⚠ ở §2.2. Khoá: `VoiceOpenTurnTest.mayAttach…` + hai assert thứ tự trong
  `VoiceOpenTurnWiringContractTest`.
