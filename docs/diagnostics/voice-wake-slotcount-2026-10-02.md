# VOICE-WAKE-SLOTCOUNT — `:wake` quyết số ô bằng state giả

> **Trạng thái**: Current · **Cập nhật**: 2026-10-02 · **Loại**: Diagnostics · **Owner**: dangkhoi ·
> **Mục đích**: lỗi xe *"bố cục 6 ô mà giọng nói báo chỉ có 3 ô"*: gốc, bảng rà mọi chỗ dispatcher đọc `state()` trong `:wake`, bản vá và bằng chứng.
> **Spec**: `docs/specs/kachi-voice-rearchitecture-and-remaining.html` §8.2 (C) · **Backlog**: `VOICE-WAKE-SLOTCOUNT`

Mức bằng chứng theo CLAUDE.md §2: `[ĐO]` = output thật của lần chạy này · `[SUY]` = đọc nguồn, chưa chạy · `[CHƯA BIẾT]`.

## 1. Lỗi trên xe

[ĐO ảnh owner 02/10, bản 2.85] Bố cục tự vẽ 6 khung (bảng vẽ: *"6 khung · phủ kín màn"*), Cài đặt › Lối tắt hiện chip
Ô1…Ô6 (đúng). Câu *"mở YouTube vào ô 6"* trả **`✗ Mở ứng dụng YouTube vào ô 6 — bố cục hiện chỉ có 3 ô`**.

## 2. Gốc

[ĐO nguồn] Khi "Hey Kachi" bật, mọi lối vào giọng nói đi phiên trong tiến trình `:wake` (CLOSE-3).
`VoiceWakeSessionFactory.buildSession` dựng `VoiceDispatcher` với `state = { grammar().homeState() }`.
`VoiceGrammarSnapshot.homeState()` chỉ điền `activeProfile` · `profiles` · `savedPlaces`; mọi trường khác là mặc định
của `HomeUiState`: `workspace.preset = THREE`, `customLayout = null`, `carStatus = CarStatus()`.
`VoiceDispatcher.runOpenApp` (2.85, dòng 406–408) tính `EffectiveLayout.slotCount(preset, customLayout)` = **3** rồi trả
lời ngay, không gửi lệnh sang Activity. Đường in-process (wake TẮT) đọc `viewModel.uiState` thật nên không bị.

[ĐO JVM] `VoiceWakeSlotCountDispatchTest` › *"hinh dang 2-85"* dựng lại đúng hình dạng 2.85 (state ảnh chụp, không
`placeInSlot`) và nhận ra đúng chuỗi lỗi trong ảnh của owner.

## 3. Bảng rà — chuỗi dispatcher đọc `state()` trong `:wake`

Phạm vi: `VoiceDispatcher` + các lớp nó gọi (`VoiceTargetDispatch`, `VoiceControlDispatch`, `VoiceReadback`,
`VoiceClimateStep`). Hai lớp cuối không đọc `state()`. `VoiceSlotPhrases` không đọc state (hằng tĩnh), xem dòng cuối.

| Trường | Ai đọc (file:dòng 2.85) | Trong `:wake` | Xử lý ở bản này |
|---|---|---|---|
| `profiles` | `VoiceDispatcher.parse` :258 | THẬT (ảnh chụp, đọc lại mỗi lần) | giữ nguyên |
| `savedPlaces` | `VoiceDispatcher.parse` :263 · `VoiceTargetDispatch.runNavSaved` :129 | THẬT | giữ nguyên |
| `activeProfile` | không chỗ nào trong chuỗi dispatcher đọc | THẬT | — |
| `workspace.preset` + `customLayout` (số ô) | `VoiceDispatcher.runOpenApp` :406–408 | **GIẢ** (luôn 3 ô) | **vá**: giao nguyên lệnh cho Activity, Activity kiểm bằng state thật và trả số ô thật |
| nội dung ô (`workspace.slots`, `overlay`) | không chỗ nào trong chuỗi dispatcher đọc (chỉ `HomeViewModel.placeTemporary` ở Activity) | GIẢ nhưng không ai đọc | — |
| `carStatus` (câu hỏi số liệu) | `VoiceDispatcher.runRead` :365 — `freshCar(id) ?: state().carStatus` | **GIẢ** ([SUY]: nhu cầu màn của `:wake` là `null` ⇒ `AppContainer.refreshForRead` luôn trả `null` ⇒ rơi về `CarStatus()` rỗng ⇒ *"chưa có số"*) | **vá**: `screenless = true` ⇒ nhu cầu màn của `:wake` = rỗng ⇒ đọc TƯƠI đúng một datum |
| `carStatus.drivetrain.speedKmh` (cổng mở cốp/ca-pô khi xe chạy) | `VoiceControlDispatch.run` :95–96 | **GIẢ** ([SUY]: như trên ⇒ tốc độ `null` ⇒ cổng cho mở) | **vá**: cùng đường đọc tươi |
| `carStatus.controls[autoId]` (gió AUTO?) | `VoiceControlDispatch.run` :73 | GIẢ (`null` = "chưa biết") | để lại: `null` là trạng thái thiết kế cho phép, Activity cũng gặp khi ô điều hoà không trên màn. Backlog `VOICE-WAKE-AUTOON` |
| `VoiceSlotPhrases.MAX_SLOT` (hotword + đuôi rụng chữ "ô") | `SherpaPhraseHotwords` · `VoiceTailClause.bareSlot` | không đọc state; hằng = 4 (trần bố cục sẵn) trong khi bố cục tự vẽ tới `SLOT_CAP` = 6 | **vá**: `max(preset, SLOT_CAP)` = 6 |

Ngoài `state()` (ghi lại, không vá — backlog `VOICE-WAKE-PREFS-STALE`): `confirmIds` · `navDefault` · `musicDefault`
đọc `SharedPreferences` của chính tiến trình `:wake` ⇒ [SUY] giữ bản nạp lúc `:wake` khởi động, đổi trong Cài đặt
không tới `:wake` cho tới khi tiến trình chết.

Cùng họ, thêm ở senior review lượt 1 (ghi lại, không vá — cùng mục backlog `VOICE-WAKE-PREFS-STALE`):
`ControlTileState.shared` là singleton THEO TIẾN TRÌNH ⇒ trong `:wake` [SUY nguồn] (a) cổng *"gói đang chạy"*
(`beginRun`) của `runMacro` không thấy gói đang chạy ở tiến trình chính (chạm ô gói + nói cùng gói ⇒ hai lượt song song);
(b) trạng thái lạc quan sau lệnh giọng nói (`setOn`/`setValue`) chỉ ghi vào bản của `:wake`, ô nút ở màn chính đợi nhịp
poll kế; (c) `VoiceControlDispatch` lùi về `st.value(def)` khi `readState` trả `null` — giá trị mặc định của `:wake`, không
phải giá trị ô đang hiện. Không cái nào là số ô, không cái nào trong bảng `state()` ở trên.

## 4. Phương án và lý do

Chọn **(a) relay**: `:wake` không kiểm dải ô; lệnh *"mở X vào ô N"* đi nguyên sang Activity qua relay có sẵn
(`VoiceWakeHomeRelay`), Activity kiểm bằng `viewModel.uiState` lúc thi hành và ack kèm số ô thật.

Không chọn (b) ghi bố cục vào ảnh chụp: số ô đổi qua ≥ 5 đường (chip bố cục · bảng vẽ · đổi hồ sơ · nhập tệp ·
migration). Sót một đường là `:wake` trả số ô cũ, tức lại quyết bằng một bản sao (CLAUDE.md §5).

Giá phải trả của (a): câu ngoài dải đi qua `:wake` cũng đưa Kachi lên trước khi trả lời. Câu trong dải đã làm vậy từ 2.69.

## 5. Tệp sửa

| Tầng | Tệp | Việc |
|---|---|---|
| `:core` | `voice/VoiceSlotPlace.kt` (mới) | `SlotPlaceOutcome` (Placed · Failed · OutOfRange(N)) + `VoiceSlotPlace.decide`: luật dải ô duy nhất, dùng chung cho in-process và Activity · `slotCountOf(HomeUiState)` |
| `:core` | `voice/VoiceEntryRoute.kt` | `VoiceHomeRelay.Ack(done, outOfRangeSlots)` · `ackOf` · `slotOutcome` (không có ack ⇒ Failed) |
| `:core` | `voice/VoiceSlotPhrases.kt` | `MAX_SLOT` 4 → 6 |
| `:core` | `voice/VoiceGrammarSnapshot.kt` | KDoc `homeState()`: nói rõ chỉ 3 trường là thật |
| `:app` | `VoiceDispatcher.kt` | tham số `placeInSlot` (mặc định `null` = kiểm bằng `state()`, giữ nguyên hành vi 2.85); `runOpenApp` không còn tự tính số ô |
| `:app` | `voice/VoiceWiring.kt` | chuyển `placeInSlot`; cờ `screenless` ⇒ `freshCar` đặt nhu cầu màn = rỗng, chỉ khi có lượt đọc |
| `:app` | `voice/VoiceWakeSessionFactory.kt` | `placeInSlot = relay.performSlot` (thay `assignAppToSlot`) · `screenless = true` |
| `:app` | `voice/VoiceWakeHomeRelay.kt` | `perform` và `performSlot` dùng chung `exchange` (một lượt hỏi–đáp có hạn); đọc `EXTRA_HOME_ACTION_SLOTS` |
| `:app` | `voice/VoiceEntry.kt` | `VoiceHomeActions(slotCount)`: ASSIGN → `decide(slot, slotCount())` → chính `slots.placeTemporary`; `ackHome(Ack)` kèm số ô; log `KachiVoiceEntry` khi ngoài dải |
| `:app` | `KachiHomeWiring.kt` | `slotCount = { VoiceSlotPlace.slotCountOf(state()) }` (state = `viewModel.uiState`) |

Đường Activity (mic khi wake TẮT · ô *Gõ lệnh chữ* · cầu kiểm thử) không truyền `placeInSlot`/`screenless`. Chúng
đi mặc định, cùng luật và cùng câu trả lời như 2.85. Bài *"in-process - cung ba ca"* khoá điều này.

## 6. Test · phá thử · kết quả

- `:core` `VoiceWakeSlotCountTest` (7): tiền đề `homeState()` = 3 ô · `slotCountOf` · `decide` (ngoài dải không chạm
  bên thi hành) · khứ hồi `Ack` mang số ô · không ack = Failed · hotword *"VÀO Ô SỐ SÁU"* · đuôi *"mở youtube sáu"* ⇒ ô 6.
- `:app` `VoiceWakeSlotCountDispatchTest` (5), hình dạng `:wake` thật (state ảnh chụp + giao thức relay thuần → `VoiceHomeActions` với state thật):
  6 khung + ô 6 ⇒ đặt vào ô (0-based 5), lời đáp ✓ · 6 khung + ô 7 ⇒ *"chỉ có 6 ô"* · bố cục sẵn 3 ô + ô 4 ⇒ *"chỉ có 3 ô"* ·
  in-process cùng ba ca cho cùng lời đáp, cùng ô · tái hiện hình dạng 2.85 ra đúng câu lỗi.
- `:app` `VoiceWakeFakeStateContractTest` (5, quét nguồn bằng `SourceRoots.body`): bảng đọc `state()` của ba tệp
  dispatcher là đóng (thêm chỗ đọc ⇒ đỏ) · `:wake` truyền `placeInSlot`, không truyền `assignAppToSlot` · relay mang số ô
  hai chiều · `screenless` đặt nhu cầu rỗng trước lượt đọc · trần 500 dòng.
- Ba bài canh cũ đổi mốc theo hình dạng mới, tính chất giữ đủ (ghi chú tại chỗ): `VoiceWakeHomeRelayWiringContractTest`
  (thân chờ-ack dời từ `perform` sang `exchange`) · `VoiceEntryRouteWiringContractTest` (thêm `slotCount`) ·
  `VoiceCommandWiringContractTest` (số ô + phép đổi 1→0-based ở `place`). `VoiceWakeIsolationContractTest` giữ nguyên.
  Bản đầu đặt nhu cầu màn trong tệp wake và bị bài này chặn, nên chuyển vào `VoiceWiring` (lười, trong `freshCar`).

**Phá thử** [ĐO, narrow tests, mỗi lần khôi phục bằng bản sao + `cmp`]:

| # | Gỡ gì | Bài đỏ (thông điệp) |
|---|---|---|
| A | `:wake` quay lại `assignAppToSlot = relay.perform(...)` | `VoiceWakeFakeStateContractTest`: *"`:wake` phải giao NGUYÊN lệnh gắn ô…"* |
| B | `place` bỏ qua `placeInSlot` | `VoiceWakeSlotCountDispatchTest` ×3: `expected <✓ … ô 6> but was <✗ … chỉ có 3 ô>` (đúng câu lỗi xe) |
| C | `ackOf` bỏ số ô | `:core` khứ hồi + `:app` *"vào ô 7"*: `expected <… chỉ có 6 ô> but was <… không mở được>` |
| D | bỏ `screenless = true` | `VoiceWakeFakeStateContractTest` (4) |
| E | `MAX_SLOT` về 4 | `:core` `expected <6> but was <4>` · `mở youtube sáu` ⇒ `null` |

**Full** [ĐO 02/10]: `./gradlew testDebugUnitTest test lint assembleRelease assembleVehicleTest --continue --rerun-tasks`
⇒ BUILD SUCCESSFUL, 152/152 task chạy lại. `python3 scripts/count-tests.py`: 806 XML · **7 125 test · 0 fail · 0 error**
(2.85 = 7 098 / 801 XML; +27 = 7 core + 10 app × 2 biến thể debug/vehicleTest). Lint debug + release: 0 Error · 0 Fatal.
APK `app/build/outputs/apk/vehicleTest/app-vehicleTest.apk` 48 082 887 B, sha256 `f030053e…cfe003`.

**Máy ảo** [ĐO 02/10, `emulator-5554` API 29, vehicleTest bản này cài `install -r` lên 2.85; prefs khôi phục nguyên trạng
sau khi đo, kiểm bằng `cmp`]. Gửi đúng intent mà `VoiceWakeHomeRelay.launch` gửi (`voice_home_action` · `voice_home_arg` ·
`voice_home_nonce` · `voice_home_deadline`) tới `KachiHomeActivity`, đọc `logcat -s KachiVoiceEntry` + cầu kiểm thử `state`:

| Ca | Bản | Bố cục (cầu `state`) | Gửi | Kết quả |
|---|---|---|---|---|
| mốc | 2.85 đang cài | `THREE` | `3:com.google.android.youtube` (ô 4) | chỉ `không thi hành được` — ack không có số ô |
| 1 | bản này | `THREE` | ô 4 | `ô ngoài dải — bố cục đang hiệu lực có 3 ô` |
| 2 | bản này | `preset THREE · custom true · slot_count 6` (đúng hình dạng xe owner) | `5:…` (ô 6) | ô 6 = `app:com.google.android.youtube`, không log lỗi |
| 3 | bản này | như ca 2 | `6:…` (ô 7) | `ô ngoài dải — bố cục đang hiệu lực có 6 ô` |

**Nửa `:wake`** [ĐO 02/10, `emulator-5554`, cùng APK vehicleTest `f030053e…`]. Cầu kiểm thử `say`/`wav` chạy trong tiến
trình chính và máy ảo chạy `-no-audio`, nên chữ được gán thẳng vào phiên `:wake`: mở một phiên nghe thật theo đường nút
mic khi wake BẬT, gắn `jdb` vào tiến trình `:wake`, gán câu cần thử vào đúng chỗ bộ nhận dạng trả chữ ra
(`VoiceFreeTail.kt:38`). Từ đó trở đi mọi bước chạy thật: hiểu câu → `performSlot` → intent sang Activity → kiểm số ô →
ack → nói.

| Ca | Log (`KachiVoiceSession` · `KachiVoiceEntry` · `KachiHomeRelay`) | Lời đáp (nhật ký lượt nói) |
|---|---|---|
| wake BẬT · tự vẽ 6 khung · *"mở youtube vào ô 6"* | `OpenApp(YouTube→ô 6)` · `Activity ack sau 222 ms (hạn 1500 ms…) ⇒ true · ngoài dải 0`; ô 6 có YouTube | `✓ Mở ứng dụng YouTube vào ô 6` |
| như trên · *"… ô 7"* | `ô ngoài dải — bố cục đang hiệu lực có 6 ô` · `ack sau 65 ms ⇒ false · ngoài dải 6`; các ô không đổi | `✗ Mở ứng dụng YouTube vào ô 7 — bố cục hiện chỉ có 6 ô` |
| wake BẬT · bố cục sẵn 3 ô · *"… ô 4"* | `bố cục đang hiệu lực có 3 ô` · `ack sau 52 ms ⇒ false · ngoài dải 3` | `✗ Mở ứng dụng YouTube vào ô 4 — bố cục hiện chỉ có 3 ô` |
| wake TẮT · 6 khung · ô *Gõ lệnh chữ* và phiên mic in-process (cùng cách gán chữ) | không có dòng chuyển sang `:wake` | ô 6 ✓ · ô 7 *"chỉ có 6 ô"* (giống `:wake`) |

Giới hạn: bước NHẬN DẠNG tiếng trong `:wake` không chạy (gán chữ thay tiếng) — [SUY] cùng bộ nhận dạng mà T2 đã đo ở
tiến trình chính. [CHƯA ĐO] lỗi gốc 2.85 trong `:wake` trên máy ảo: bản release không cho gắn `jdb`; lỗi gốc chỉ tái hiện
bằng test JVM (*"hinh dang 2-85"*).

Bẫy công cụ [ĐO] khi gán chữ bằng `jdb` (để lượt sau khỏi dò lại): chuỗi có dấu gửi qua `jdb` thành `�` — dựng chuỗi trong
máy bằng `android.net.Uri.decode`; gán kết quả một lời gọi hàm vào biến cục bộ làm `jdb` báo *"Thread has been resumed"*
— đi vòng qua một trường chỉ-hiển-thị rồi trả giá trị cũ; biến cục bộ trong lambda inline của Kotlin tên dạng
`sentence\1`, `jdb` không gọi tên được.

**E2E giọng nói** [ĐO 02/10, `scripts/emulator/voice-e2e.sh --only all`, chạy lại vì trần hotword đổi]: T1 **106/106 PASS**
(mốc 2.68: 106/106) · T2 **23/27** nghe đúng nguyên văn (mốc: 23/27). Báo cáo ở scratchpad phiên, không commit.

## 7. Cần xe / để lại

- 🚗 Wake BẬT, bố cục tự vẽ 6 khung: *"mở YouTube vào ô 6"* ⇒ YouTube ở ô 6 + ✓; *"vào ô 7"* ⇒ *"chỉ có 6 ô"*;
  `logcat -s KachiHomeRelay KachiVoiceEntry` thấy `ngoài dải 6` / `bố cục đang hiệu lực có 6 ô`.
- 🚗 Wake BẬT: *"tốc độ bao nhiêu"* / *"pin còn bao nhiêu"* ra số thật thay vì *"chưa có số"*. [CHƯA BIẾT] HAL đọc từ
  `:wake` trên xe, dù lệnh ghi từ `:wake` đã chạy từ 2.68.
- ~~Câu *"vào ô số không"* qua `:wake` ⇒ *"không mở được"*, in-process *"chỉ có N ô"* [P3]~~ — **đính chính ở senior
  review lượt 1**: ca này không tồn tại. [ĐO JVM `VoiceWakeSlotCountTest` › *"o so khong…"*] bộ phân tích không bao giờ
  ra ô ≤ 0 (`VoiceTailClause.slotAt` bỏ `n.value <= 0`, mọi nguồn `OpenApp.slot` qua hàm ấy): *"mở youtube vào ô số
  không"* ra `OpenApp(YouTube)` KHÔNG ô ở cả hai đường. Cổng `slot < 0` của `performSlot` chỉ là phòng thủ.
- Ngoài phạm vi, đã vào backlog: `VOICE-WAKE-AUTOON` · `VOICE-WAKE-PREFS-STALE` · `VOICE-READ-STALE-BG`
  (tiến trình chính, màn khuất, wake TẮT: vòng poll dừng mà `refreshForRead` vẫn coi `null` = "poll đọc hết").

## 8. Senior review lượt 1 (02/10, chưa commit)

Đã soát toàn bộ diff + tệp mới. Không có lỗi ở phần sửa chính: mọi quyết định của `:wake` cần sự thật màn chính (số ô ·
số liệu xe khi hỏi · tốc độ cho cổng cốp/ca-pô) không còn dựa ảnh chụp; relay chỉ nói ✓ khi Activity ack `done = true`
trong hạn, hết hạn ⇒ *"không mở được"* (không im, không ✓ sai); đường in-process đi mặc định, cùng luật `decide`.
`screenless` chỉ đặt nhu cầu màn của tiến trình `:wake` (không vòng poll nào ở đó đọc nó — [ĐO nguồn] chỉ
`VoiceWakeService` chạy ở `:wake`, `KachiApplication` bỏ qua `AppContainer`/poll ở tiến trình nền).

Đã vá ở lượt này:

| Mức | Chỗ | Việc |
|---|---|---|
| [P3] | tài liệu §6 + spec §8.2 (C) + backlog | *"nửa `:wake` chưa chạy được trên máy ảo"* đã lỗi thời — thay bằng số đo `jdb` ở §6 |
| [P3] | tài liệu §7 | ca lệch *"vào ô số không"* không tồn tại — đính chính + test tiền đề `VoiceWakeSlotCountTest` (8 test) |
| [P3] | `VoiceSessionLog.logAsked` · `VoiceSession.execute` · `VoiceSessionTurns.clarifyGaveUp` | E2E báo: lượt HỎI LẠI / BỎ CUỘC có nhật ký `decision`/`replies` rỗng ([ĐO] mục `20261002-172417-715`, `…-172621-101`). Gốc: hai lối thoát ấy không qua `settle()` — chỗ duy nhất gọi `logDone`. Nay ghi câu Kachi nói, `clarify = true`, TRƯỚC khi mở lượt nghe nối. Bài canh mới `VoiceClarifyLogContractTest` (3); phá thử: dời ghi ra sau `askAgain` + gỡ ghi ở nhánh bỏ cuộc ⇒ 2/2 đỏ |
| [P3] | `scripts/emulator/voice-e2e.sh` | E2E báo: harness để máy ở hồ sơ khác sau ca t58. Nay ghi hồ sơ lúc bắt đầu, `cleanup` trả về bằng lệnh `profile` của cầu (CHÍNH `viewModel.switchProfile`), không trả được thì nói. Kèm sửa `«$VAR»` ⇒ `«${VAR}»`: với `set -u`, ký tự `»` dính vào tên biến ⇒ *"unbound variable"* làm `cleanup` chết giữa chừng (để ngỏ cầu kiểm thử) — [ĐO] lượt chạy thử đầu tiên gặp đúng lỗi này; dòng `«$SERIAL»` của cổng `require_emulator` có cùng lỗi tiềm ẩn |

[ĐO máy ảo 02/10] harness `--only say` với bộ ca chỉ có t58: *"hồ sơ lúc bắt đầu: Mặc định"* → t58 PASS (đổi sang
"Mặc định 2") → *"hồ sơ đã về «Mặc định»"* → *"đã tắt chế độ kiểm thử"*. Ghi chú: trả hồ sơ KHÔNG làm prefs về đúng byte —
lượt rời hồ sơ ghi lại ảnh hồ sơ ấy từ cấu hình đang sống (thiết kế S4); prefs máy ảo đã khôi phục từ bản sao tar, khớp sha
10/10 (sau khi đưa HOME lên chỉ lệch `a11y_proc_start_elapsed`, như lượt E2E).

**Kiểm sau vá** [ĐO 02/10]: hẹp — `VoiceWakeSlotCountTest` 8/8 · `VoiceClarifyLogContractTest` 3/3 · `VoiceModelTuningWiringContractTest`
16 · `VoiceFastNaturalWiringContractTest` 15 · `VoiceListenWiringContractTest` 17 (trần 500 dòng) · `VoiceWakeFakeStateContractTest` 5 ·
`VoiceWakeSlotCountDispatchTest` 5, 0 fail. Full `testDebugUnitTest test lint assembleRelease assembleVehicleTest --continue` ⇒
BUILD SUCCESSFUL; `scripts/count-tests.py` 808 XML · **7 132 test · 0 fail · 0 error** (+7 = 1 `:core` + 3 `:app` × 2 biến thể);
lint debug 0 Error · 0 Fatal, `lintVitalAnalyzeRelease` qua. APK vehicleTest mới sha256 `f7ce9746…a5a0` (chưa cài lên máy ảo;
số đo máy ảo ở §6 là của APK `f030053e…`, phần mã khác nhau chỉ ở nhật ký lượt hỏi lại/bỏ cuộc).

## 9. Senior review lượt 2 (02/10, chưa commit)

Soát lại toàn bộ diff + tệp mới, kể cả 4 bản vá của lượt 1. Phần sửa chính và bản vá lượt 1: không có lỗi.

- [ĐO nguồn] Ba quyết định của `:wake` cần sự thật (số ô · câu hỏi số liệu · tốc độ cho cổng cốp/ca-pô) không còn dựa
  vào `homeState()`. Activity kiểm bằng `viewModel.uiState`; `HomeViewModel` dựng `_uiState` từ
  `repository.load()` đồng bộ, và `load()` có `customLayout`, nên Activity mở lạnh cũng không có ca 3 ô giả.
- [ĐO nguồn] `screenless` không có tác dụng phụ: `refreshNow` chỉ `publish` một lượt, không gọi `start()`. Chỉ
  `KachiHomeWiring.collectHome` khởi động poll, và nó chạy ở tiến trình chính. `controlDemand` của `:wake` rỗng nên
  không đọc nút nào. Datum được ghim mà đọc ra `null` thì trả `null` (`Gate.fresh`), không giữ số cũ.
- Relay: `✓` chỉ khi có ack `done = true` trong hạn. Không ack / không đăng ký được receiver ⇒ `null` ⇒ `Failed` ⇒
  *"không mở được"*. Activity quá hạn thì không làm (`expired`). Đường in-process giữ đúng phép so `slot !in 1..count`
  của 2.85 (`decide(slot - 1, count)` tương đương).
- `logAsked` ghi trước `askAgain`, và lối bỏ cuộc ghi đúng câu bỏ cuộc. `restore_profile` chạy được khi script chết
  giữa chừng (`|| true` trong `cleanup`).

Đã vá ở lượt này:

| Mức | Chỗ | Việc |
|---|---|---|
| [P3] | `VoiceWakeFakeStateContractTest` (1) | Bảng đọc `state()` chỉ quét LỜI GỌI `state()`. Chuyển nguyên lambda sang một lớp mới (`Foo(state = state)`, `val s = state`) lọt qua, trong khi bài tự nhận là "đóng". Thêm `handOffs`: dispatcher chỉ được chuyển `state` cho `VoiceTargetDispatch` và `VoiceControlDispatch`, hai lớp ấy không chuyển tiếp. Phá thử: thêm `private val probeHandOff: () -> HomeUiState = state` vào `VoiceDispatcher` ⇒ đỏ ở đúng assert mới; khôi phục bằng bản sao + `cmp` |
| [P3] | chú thích bài canh trên · KDoc `VoiceGrammarSnapshot.homeState()` · `project-context.md` | Ba chỗ ghi "số liệu xe ⇒ đọc tươi" mà không nêu ngoại lệ `carStatus.controls[autoId]`. Ở `:wake` trường này luôn rỗng, nên `autoOn` = `null` ("chưa biết"), không đọc tươi. Bảng §3 và backlog `VOICE-WAKE-AUTOON` đã ghi đúng; nay ba chỗ kia cũng ghi. Chỉ sửa chú thích, mã chạy không đổi |
| [P3] | `scripts/emulator/camera-cluster-e2e.sh:64` · `camera-dewarp-e2e.sh:74,269` · `scripts/vehicle/kachi/30-profiles.sh:58,60` · `70-voice.sh:116,173,188` | Cùng lỗi `«$VAR»` + `set -u` mà lượt 1 sửa trong `voice-e2e.sh`, tìm bằng cách quét mọi `scripts/**/*.sh`. [ĐO host] `/usr/bin/env bash` = 3.2.57: `set -u; X=1; echo "«$X»"` ⇒ `X�: unbound variable`, rc 127. Trên xe, `70-voice.sh` chết ngay lượt nói đầu (`k_shot … «$say_it»`); `30-profiles.sh` chết khi gõ tên hồ sơ. Đã sửa thành `«${VAR}»`, `bash -n` qua; bài `CameraClusterBandWiringContractTest` (đọc `camera-cluster-e2e.sh`) vẫn xanh |
| [P3] | backlog `VOICE-WAKE-SLOTCOUNT` | Số test `VoiceWakeSlotCountTest` 7 ⇒ 8 (bài tiền đề của lượt 1) |

Không vá, đã có trong backlog: `VOICE-WAKE-AUTOON` · `VOICE-WAKE-PREFS-STALE` · `VOICE-READ-STALE-BG`.

**Kiểm sau vá** [ĐO 02/10, 18:14]
- Chạy hẹp: `VoiceWakeFakeStateContractTest` 5 · `VoiceWakeSlotCountDispatchTest` 5 · `CameraClusterBandWiringContractTest` 10 · `:core` `VoiceWakeSlotCountTest` 8, 0 fail.
- Full `testDebugUnitTest test lint assembleRelease assembleVehicleTest --continue` ⇒ BUILD SUCCESSFUL. `count-tests.py`: 808 XML · **7 132 test · 0 fail · 0 error**. Số test không đổi vì lượt này chỉ thêm assert vào bài sẵn có. Lint debug 0 Error · 0 Fatal, `lintVitalAnalyzeRelease` qua.
- APK vehicleTest sha256 `941b6c7c…7fbd`. Khác `f7ce9746…` vì KDoc `homeState()` dài thêm ⇒ số dòng trong bytecode đổi; mã chạy không đổi. Chưa cài lên máy ảo.

## 10. Senior review lượt 3 (02/10, chưa commit)

Soát lại toàn bộ diff + tệp mới, kể cả 3 bản vá của lượt 2. Mã chạy: không có lỗi.

- [ĐO nguồn] `VoiceHomeRelay.decodeSlot` chỉ chặn ô âm, không có trần trên ⇒ *"vào ô số hai mươi"* qua `:wake` tới được
  Activity và ra *"chỉ có N ô"*, giống in-process. `EffectiveLayout.slotCount` ≥ 1 (`usable` đòi khung không rỗng,
  preset nhỏ nhất `ONE`) ⇒ ack ngoài dải luôn mang số > 0, `slotOutcome` không rơi nhầm về `Failed`.
- [ĐO nguồn] `VoiceWakeService` khai `android:process=":wake"` và là nơi duy nhất gọi `buildSession` ⇒ `screenless`
  không bao giờ chạy ở tiến trình chính. Ba bề mặt chung tiến trình với màn (`KachiHomeWiring.voiceSession` ·
  `VoiceTextConsole` · `TestBridgeHooks`) không truyền `placeInSlot`/`screenless`.
- Script: không còn `$VAR` trần nào đứng ngay trước byte ≥ 0x80 trong mọi script shell của repo (quét bằng `perl`,
  chỉ còn một dòng chú thích trong `gradlew`). `bash -n` qua cả 5 script đã sửa.

Đã vá ở lượt này (chỉ bài canh, mã chạy không đổi):

| Mức | Chỗ | Việc |
|---|---|---|
| [P3] | `VoiceWakeHomeRelayWiringContractTest` › *"perform cho ack…"* | `assertFalse(fn.contains("return true"))` là canh của bản `Boolean`; từ khi thân chờ-ack thành `exchange(): Ack?` chuỗi ấy không thể xuất hiện ⇒ canh rỗng (trái luật *không nới bài canh cũ*). Thêm: thân `exchange` dựng `Ack(` ở ĐÚNG một chỗ — chỗ ack đã tới |
| [P3] | `VoiceWakeFakeStateContractTest` (3) | `performSlot` không được tự trả `Placed`/`OutOfRange` — ✓ và số ô chỉ đến từ ack của Activity |
| [P3] | `VoiceWakeFakeStateContractTest` (bài mới) | *"Đường Activity không đổi"* chỉ được khoá ở mặc định; nay quét mọi tệp mã chính: chỉ `VoiceWakeSessionFactory.kt` rời mặc định `placeInSlot`/`screenless` (`screenless = true` ở tiến trình chính sẽ đè `null` = lượt poll đầu đọc đủ khi màn quay lại) |
| [P3] | spec §8.2 (C) dòng *Test* | `VoiceWakeSlotCountTest` 7 ⇒ 8 (backlog đã sửa ở lượt 2, spec sót) |

**Phá thử** [ĐO 02/10, chạy hẹp] ba đột biến cùng lúc: `exchange` trả `VoiceHomeRelay.Ack(true)` cho `SET_LAYOUT` trước
khi gửi · `performSlot` trả `SlotPlaceOutcome.Placed` khi `slot > 1000` · `screenless = true` trong
`KachiHomeWiring.voiceSession`. Bài canh trước bản vá: 3 lớp xanh hết (`BUILD SUCCESSFUL`). Sau bản vá: 3 đỏ đúng assert
mới (`expected <1> but was <2>` · `expected <false> but was <true>` · `expected <[VoiceWakeSessionFactory.kt]> but was
<[KachiHomeWiring.kt, VoiceWakeSessionFactory.kt]>`). Nguồn khôi phục từ bản sao, kiểm bằng `cmp`.

Bẫy công cụ [ĐO]: sửa tài liệu trong lúc full build đang chạy làm đỏ `:offcar-planner` `ExpansionTransportFenceTest`
(bài này chụp hash cả cây repo trước/sau một lượt sinh) — không phải lỗi mã; chạy lại khi không ai ghi tệp.

**Kiểm sau vá** [ĐO 02/10, 18:25]
- Chạy hẹp: `VoiceWakeHomeRelayWiringContractTest` 6 · `VoiceWakeFakeStateContractTest` 6 · `VoiceWakeSlotCountDispatchTest` 5 · `VoiceEntryRouteWiringContractTest` 9 · `VoiceCommandWiringContractTest` 12 · `VoiceWakeIsolationContractTest`, 0 fail.
- Full `testDebugUnitTest test lint assembleRelease assembleVehicleTest --continue` ⇒ BUILD SUCCESSFUL (lượt chạy lại, không ai ghi tệp). `count-tests.py`: 808 XML · **7 134 test · 0 fail · 0 error** (+2 = bài mới × 2 biến thể debug/vehicleTest). Lint debug 0 Error · 0 Fatal.
- APK vehicleTest sha256 `941b6c7c…7fbd` — trùng lượt 2: bản vá chỉ ở `src/test`, mã chạy không đổi một byte. Không cài lên máy ảo (không có gì mới để đo trên máy).
