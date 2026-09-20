# Handoff — SENIOR REVIEW 3 fix voice C · D · E

> **Trạng thái**: APPROVED sau 3 bản vá · **Ngày**: 2026-09-19 · **CHƯA commit / CHƯA push** (main-agent ship)
> **Phạm vi soát**: `docs/_handoff/cde-done.md` + `git diff` (11 tệp sửa · 3 tệp mới) trên HEAD `893483b`
> **Mục đích**: soát độc lập 3 fix từ phản hồi owner lượt lái thử 1.79 — C (cốp/ca-pô chỉ mở khi dừng) · D
> (*"mở kính"* → kính lái) · E (TOGGLE/COVER đọc-được thì đọc lại xác nhận).

## 0. Verdict + kết quả đo

| Phép đo | Kết quả |
|---|---|
| **Verdict** | **APPROVED** — sau khi tự vá 1×[P1] · 1×[P2] · 1×[P3] |
| `JAVA_HOME=…openjdk@17 GRADLE_OPTS=-Xmx3g ./gradlew test --rerun-tasks --continue` | **BUILD SUCCESSFUL in 1m 15s** · 73/73 task executed |
| **Đếm từ `build/test-results/**/TEST-*.xml`** (xoá sạch XML trước khi chạy) | **4870 test · 0 failures · 0 errors · 0 skipped** |
| `:app:compileReleaseKotlin` | BUILD SUCCESSFUL |
| Thử làm ĐỎ | **9 phép, đỏ đúng chỗ cả 9** — §5 |
| Trần 500 dòng (tệp sản phẩm) | mọi tệp OK — §4 |
| Commit/push | **KHÔNG** — HEAD vẫn `893483b`, cây làm việc còn nguyên thay đổi |

### Số test theo module (đếm bằng máy, không lấy từ log Gradle)

| module:task | tests | fail | err | skip |
|---|---:|---:|---:|---:|
| `app:testDebugUnitTest` | 1187 | 0 | 0 | 0 |
| `app:testVehicleTestUnitTest` | 1199 | 0 | 0 | 0 |
| `car-integration:test` | 61 | 0 | 0 | 0 |
| `core:test` | 2302 | 0 | 0 | 0 |
| `offcar-planner:test` | 99 | 0 | 0 | 0 |
| `vehicle-contracts:test` | 22 | 0 | 0 | 0 |
| **TỔNG** | **4870** | **0** | **0** | **0** |

Khớp số: mốc impl **4861** + 3 bài `:core` + 3 bài `:app` (×2 biến thể = 6) = **4870**. ⚠ `./gradlew test` chạy
`:app` ở **hai** biến thể (`debug` + `vehicleTest`) trên cùng tập lớp ⇒ 4870 là số **công bố**; số **phân biệt** ≈
3671. Ghi cả hai để đừng đọc 4870 thành "số ca khác nhau" (đúng luật `conversation-protocol.md` P1).

## 1. Findings — 3 vá, 4 báo-cáo-không-vá

| # | Sev | Chỗ | Tóm tắt | Xử lý |
|---|---|---|---|---|
| 1 | **[P1]** | `VoiceReadback.act` | Bộ phận chạy bằng mô-tơ bị báo **"✗ xe không nhận lệnh"** cho lệnh THÀNH CÔNG | **ĐÃ VÁ** |
| 2 | **[P2]** | `VoiceSynonyms` (D) | Cụm **số nhiều tường minh** (*"các cửa sổ"* · *"windows"*) bị dời sang MỘT cửa kính | **ĐÃ VÁ** |
| 3 | **[P3]** | gate C | Gói lệnh (`Macro`) **không đi qua** gate C ⇒ cửa sau mở im lặng ở lượt sau | **ĐÃ VÁ** (bất biến) |
| 4 | [P3] | `trunk` readKey | Đường đọc của cốp khả năng trả `null` trên xe ⇒ E không có tác dụng cho đúng nút owner quan tâm | báo, cần xe |
| 5 | [P3] | `readState` `> 0` | Ngữ nghĩa `>0` phụ thuộc enum thô của `sunroof_state`/`tailgate_status` | báo, cần xe |
| 6 | [P3] | `VoiceReadback.step` | STEP đọc-lại-khớp **vẫn còn** đuôi hedge (bất đối xứng với `doneConfirmed`) | báo, ngoài scope |
| 7 | [P3] | `VoiceReadback.act` | Lượt đọc ĐẦU chạy **trên luồng vẽ** | báo, đúng tiền lệ |

---

### [P1] Bộ phận chạy bằng mô-tơ bị báo "✗ xe không nhận lệnh" cho lệnh THÀNH CÔNG

**Đây là finding nặng nhất của lượt soát, và nó là một hồi quy hành vi do chính fix E sinh ra.**

Handoff impl §3 có nhắc rủi ro này ở KDoc `READBACK_SETTLE_MS` nhưng **hạ cấp nó thành "cần đo trên xe"** và
**đếm hụt phạm vi**. Hai chỗ đếm hụt, đo được off-car:

1. **Không phải 7 nút vào đường E mà 14.** Bảng của impl bỏ sót `ac_auto` · `defrost_rear` · `anion` và — quan
   trọng nhất — **cả 4 kính riêng `win_lf`..`win_rr`**. (Lượt đếm đầu của tôi cũng ra 7 vì bộ quét cân ngoặc bị
   một dấu `(` **trong chú thích** ở `ControlRegistry.kt:171` làm lệch khối; đếm lại theo dòng mới ra 14. Ghi ra
   vì đó đúng họ lỗi *"phép đo sai trước khi code sai"* của repo.)
2. **Hướng ĐÓNG mới là hướng vỡ hệ thống**, không phải hướng mở mà KDoc nêu.

Cơ chế, [ĐO off-car từ registry]:

```
win_lf : ControlKind.COVER · args = [Đóng, Mở, Nửa] → arg 0/1/2 · tier = PROVEN
         readKey = "window_lf" → BYDAutoBodyworkDevice.getWindowOpenPercent → PHẦN TRĂM 0–100
```

*"đóng kính trước trái"* ⇒ `arg = 0` ⇒ `wantOn = false`. Cửa đang mở 100% ⇒ lượt đọc đầu **100** ⇒ lệch ⇒ chờ
`READBACK_SETTLE_MS = 300`ms ⇒ cửa kính mất **vài giây** nên vẫn còn ~90 ⇒ lệch ⇒ `VoiceReply.failed` =
**"✗ Đóng Kính trước-trái — xe không nhận lệnh"** cho một lệnh ở mức **PROVEN** (đã đo chạy thật trên xe owner).
Không phải thỉnh thoảng — **gần như luôn**, vì 300 ms < thời gian chạy của mọi cửa kính. Cùng ca cho `sunroof`
và `trunk` khi chúng đọc được.

Tức 1.79 nói ✓ quá lạc quan, còn bản E chưa vá nói **✗ sai hẳn** — và ✗ sai tệ hơn: nó là đúng thứ làm người lái
thôi tin cả tính năng, tức phá chính mục tiêu owner đặt ra cho E.

**Bản vá — lượt đọc lại là bằng chứng MỘT CHIỀU.** Khớp ⇒ chứng minh được THÀNH CÔNG. Lệch sau 300 ms ⇒ **không**
chứng minh được THẤT BẠI với bộ phận còn đang chạy dở. Nên:

- `:core CtlSafetyPolicy.MOVES_SLOWLY` — tập mã *"có mô-tơ kéo, mức đổi dần theo thời gian"*: `trunk` `hood`
  `sunroof` `sunshade` `window` `windows_all` `win_lf` `win_rf` `win_lr` `win_rr` (kê cả mã **chưa** có khoá đọc:
  tính chất là của **bộ phận**, không của việc hôm nay đọc được hay chưa);
- `VoiceReadback.act` nhánh sau settle: khớp ⇒ `doneConfirmed` · **chạy chậm** ⇒ `done()` (✓ + hedge, đúng câu
  1.79) · còn lại ⇒ `failed()` — **nửa owner yêu cầu ở E còn nguyên** cho nhóm điện/khí (`recirc` `drl` `anion`
  `defrost` `ac_auto` + ghế), nơi lệch dai dẳng ĐÚNG là *"lệnh không ăn"*;
- `st.setOn` **không ghi đè** mức lạc quan khi bộ phận đang chạy dở — ghi đè là để ô thanh nút hiện *"đang mở"*
  cho một cửa kính vừa nhận lệnh ĐÓNG và đang đóng thật.

**Cố ý KHÔNG nới `READBACK_SETTLE_MS`**: chờ đủ cho một cửa kính (vài giây) là bắt **mọi** câu trả lời chậm thêm
vài giây. Khoảng chờ theo từng nút vẫn là việc của lượt có số ĐO trên xe — bản vá này chỉ bỏ **lời khẳng định
sai**, không giả vờ đã biết thời gian chạy của từng bộ phận.

### [P2] Cụm SỐ NHIỀU tường minh bị dời sang một cửa kính

Lượt D tự phát biểu nguyên tắc: *"cụm **MƠ HỒ** ⇒ phạm vi HẸP NHẤT; muốn cả bốn thì phải nói TƯỜNG MINH"* — rồi
dời **cả bốn** cụm sang `window`, trong đó `cac cua so` và `windows` **không mơ hồ**: `các` là dấu hiệu số nhiều
của tiếng Việt, `windows` là số nhiều tiếng Anh. Cả hai đã tường minh-nhiều-cửa y như *"bốn kính"*.

[ĐO off-car] *"mở các cửa sổ"* ⇒ `Control(window, 1)` = hạ **một** cửa. Không phải cái giá rẻ hơn — chỉ là cái sai
đổi chiều, và nó **không** nằm trong phản hồi của owner (owner báo đúng *"mở kính"*).

**Vá**: `cac cua so` · `windows` về `windows_all`; `kinh` · `cua so` ở lại `window`. Luật dãy-dài-nhất giữ hai bên
không cướp nhau — *"mở các cửa sổ"* khớp `cac cua so` (3 từ) ở vị trí 1, thắng `cua so` (2 từ) ở vị trí 2; đã khoá
bằng bài canh hai chiều. Số cụm ngữ pháp **không đổi** (dời giữa hai mã) ⇒ `VoiceGrammarPhrasesTest`/`LangCoverage`
không phải sửa con số nào — đã chạy lại để xác nhận.

### [P3] Gói lệnh không đi qua gate C

Gate C sống ở `VoiceDispatcher.runControl`, còn `VoiceIntent.Macro` đi `runMacro` → `MacroRunner.run` →
`CarControlPort.actByKind`, **không** qua `runControl`. [ĐO] hôm nay **chưa có lỗ** — ba gói hiện tại chỉ chạm 4
kính · `readl` · `door` · `lock`. Nhưng thêm `trunk` vào một gói *"rời xe"* ở lượt sau là mở lại đúng ca owner vừa
báo, và mở **im lặng** vì mã gate vẫn còn nguyên ở chỗ cũ.

**Vá bằng bất biến, không bằng cách bơm tốc độ vào `MacroRunner`**: một gói lệnh là thứ người lái tự dựng từ ý định
rõ ràng, và một gói nửa chạy nửa bị chặn còn khó hiểu hơn một gói không bao giờ chứa mã ấy. Bài canh
`khong goi lenh nao chua ma gate theo van toc` đỏ ngay tại chỗ khai nếu ai thêm.

## 2. Bốn điểm BÁO-CÁO-KHÔNG-VÁ (nêu rõ vì sao không tự sửa)

- **[P3] `trunk` khả năng đọc `null` trên xe ⇒ E không giúp gì cho đúng nút owner quan tâm.** `ControlDef("trunk")`
  ghi chú *"Đọc trạng thái từ `getBackDoorOpenedHeight` cũng ở Setting device (**Bodywork trả rỗng**)"*, nhưng
  `readKey = "tailgate_status"` lại trỏ tới `BYDAutoBodyworkDevice.getHatchDoorStatus` — **chính cái device mà chú
  thích nói là trả rỗng**. Nếu đúng ⇒ `readState` = `null` ⇒ nhánh 1 ⇒ câu 1.79 + hedge (an toàn, nhưng vô ích).
  Đây là chỗ lệch **có trước** lượt C/D/E; sửa nó = đổi khoá đọc của một datum theo [ĐOÁN], phải có số đo trên xe.
- **[P3] Ngữ nghĩa `> 0` phụ thuộc enum thô.** `sunroof_state`/`tailgate_status` đọc qua getter enum; nếu enum là
  `1 = mở / 2 = đóng` (đúng họ enum kính mà `OpenBYD` dùng cho đường GHI) thì `first > 0` **luôn** đúng. `>0` là
  quy ước **đã có sẵn và đang chạy** toàn repo (`CarStatus.controls` KDoc · `ControlTileFactory:130` ·
  `CarDataAdapter` đọc 5 datum này bằng `g.bool`) nên đây **không** phải lỗi do E sinh ra — nhưng E biến một chỗ
  lệch *trang trí* (ô sáng sai) thành một câu **nói ra miệng**. Bản vá [P1] đã bịt hậu quả nặng nhất (không còn ✗
  oan cho bộ phận chạy chậm); phần còn lại là **việc đo số 1 trên xe**.
- **[P3] STEP đọc-lại-khớp vẫn còn đuôi hedge.** `doneActual` ở nhánh khớp trả *đúng câu cũ* = `done()` ⇒ còn
  *"chưa kiểm trên xe"*, trong khi lý lẽ của `doneConfirmed` (*"bằng chứng vừa được tạo trên chính chiếc xe này"*)
  áp y hệt cho STEP. **Không vá**: owner đặt E cho TOGGLE/COVER, và đổi `doneActual` là chạm hợp đồng R5 + bộ bài
  canh của nó ngay trước một lượt ship. Là việc một dòng cho lượt sau.
- **[P3] Lượt đọc ĐẦU chạy trên luồng vẽ.** Lượt chờ 300 ms thì đã ở `background` và câu nói/`ControlTileState`
  quay về `onUi` — **đúng** yêu cầu *"settle nền không chặn luồng vẽ"*. Nhưng `readState` đầu tiên là một lượt HAL
  đồng bộ trên luồng chính. Đúng tiền lệ đã có (`runControl` nhánh tương đối · `ControlTileFactory.nudge` ·
  `VoiceReadback.step` của R5) và vẫn là **một** lượt đọc cho một câu lệnh (ngân sách [ĐO xe 1.68] 33 lượt/phút) ⇒
  không đổi mô hình luồng trong lượt này.

## 3. Bốn hạng mục đề bài — kết quả kiểm

**(1) C — gate cốp/ca-pô.** ✓ Đọc đúng datum: `freshCar("speed")` → `"speed"` có thật trong `TelemetryRegistry`
(`BYDAutoSpeedDevice.getCurrentSpeed`, tier **PROVEN**), lùi về ảnh chụp `state().carStatus.drivetrain.speedKmh`
khi đọc tươi hụt — cần cả hai vì `refreshForRead` trả `null` cho datum đang được poll. ✓ `kmh == null` ⇒ fail-open,
**có** khối chú thích nêu lý do (`speed` PROVEN ⇒ trên xe có số; fail-closed biến ca *"mở cốp lúc đỗ"* — ca dùng
thường nhất — thành lời từ chối). ✓ Chỉ chặn `arg > 0` nên **ĐÓNG** cốp lúc đang chạy không bị chặn (đường chữa).
✓ **Không** gate nhầm: bài canh liệt kê `window` `windows_all` `win_*` `sunroof` `sunshade` `readl` `lock` `door`
`fan` `temp` phải `assertFalse`. ✓ Đặt đúng chỗ (`runControl`, trước `actByKind`, sau khi tính `arg`); đường
`Macro` vá ở [P3].

**(2) D — *"mở kính"*.** ✓ grep + bài canh: `kinh`/`cua so` → `window`; `het kinh`/`toan bo kinh`/`moi kinh`/
`bon kinh`/`het kieng`/`every window` → nút gộp. ✓ *"mở hết kính"* → `Macro("mac_win_open_all")`, *"mở toàn bộ
kính"* → `Control(windows_all, 1)`, *"mở bốn kính"* → `Control(windows_all, 1)` (chống hồi quy NGƯỢC). ✓ Thử ĐỎ:
trả `kinh` về `windows_all` ⇒ 2 bài đỏ. ✓ *"mở cửa sổ trời"* vẫn về `sunroof` (nhãn *"Cửa sổ trời"* = 3 từ thắng
`cua so` = 2 từ). Sửa [P2] ở trên.

**(3) E — đọc lại xác nhận.** ✓ `doneConfirmed` chỉ khác `done` ở chỗ **không** gọi `unverified()`, và chỉ có **2
chỗ gọi**, cả hai ở nhánh đã khớp. ✓ Nút không `readKey` ⇒ `else -> done()` (còn hedge) **và** không tiêu một lượt
HAL (bài canh `readCount == 0`). ✓ `readState` trả `null` ⇒ `done()` (hedge), **không** báo *"không nhận lệnh"* oan.
✓ Settle nền không chặn luồng vẽ (`background { sleep }` → `onUi { … }`). ✓ Cổng kind hẹp hơn spec
(`TOGGLE||COVER`) là **đúng**, không phải thiếu: `BUTTON` so mức sau khi bấm sẽ báo ✗ cho cú bấm bình thường,
`SELECT` thì `>0` không mang nghĩa *bật* — cả hai có bài canh. ✓ `VoiceReply.failed` đọc *"✗ … — xe không nhận
lệnh"*, đúng câu owner yêu cầu. Sửa [P1] ở trên.

**(4) Trần 500 dòng.** ✓ `VoiceDispatcher` 468 · `VoiceReadback` **151** (sau vá) · `CtlSafetyPolicy` **95** ·
`VoiceReply` 472 · `VoiceSynonyms` **298** · `VoiceClarify` 401. Tệp bài canh: `VoiceActGateReadbackTest` 296 ·
`CtlSafetyPolicyTest` 129 · `VoiceWindowScopeTest` 101 · `VoiceIntentParserTest` 484.
✓ Hai tệp hạ tầng bài canh đã cập nhật đúng (thiếu thì **phạm vi kiểm mất mà test vẫn xanh**):
`VoiceCommandWiringContractTest.dispatcher` += `VoiceReadback.kt` · `LayeringRulesTest.pureButMustStayInApp` +=
`VoiceReadback.kt` kèm lý do.

## 4. Tệp tôi đổi trong lượt soát

| Tệp | Dòng | Việc |
|---|---|---|
| `core/…/CtlSafetyPolicy.kt` | 62 → **95** | **[P1]** `MOVES_SLOWLY` + `movesSlowly()` |
| `core/…/voice/VoiceSynonyms.kt` | 288 → **298** | **[P2]** `cac cua so`/`windows` về `windows_all` |
| `app/…/VoiceReadback.kt` | 129 → **151** | **[P1]** lệch dai dẳng + chạy chậm ⇒ `done()`; không ghi đè ô; sửa 2 KDoc đã nói sai |
| `core/test/…/CtlSafetyPolicyTest.kt` | 101 → **129** | +2 bài: gói lệnh không chứa mã gate · vệ sinh `MOVES_SLOWLY` |
| `core/test/…/voice/VoiceWindowScopeTest.kt` | 82 → **101** | +1 bài: số nhiều tường minh vẫn là cả bốn |
| `app/test/…/VoiceActGateReadbackTest.kt` | 252 → **296** | +3 bài: chạy chậm không báo ✗ · chạy chậm tới đích vẫn xác nhận · nhóm điện-khí không được miễn ✗ |

## 5. Thử làm ĐỎ — 9 phép, đỏ đúng chỗ cả 9

Sao lưu **theo tệp** (`/tmp/cde-review-bak`), **không** `git checkout` (luật: cây chưa commit thì tuyệt đối không
hoàn nguyên bằng git). `cmp -s` xác nhận **mọi** tệp phục hồi **byte-identical**.

| # | Phép phá | Đỏ ở |
|---|---|---|
| M1 | `REQUIRES_STATIONARY → emptySet()` | `CtlSafetyPolicyTest` 1 · `VoiceActGateReadbackTest` 3 (**chứng minh dây nối đọc chính bảng policy**) |
| M2 | vô hiệu gate C trong `runControl` | `VoiceActGateReadbackTest` 3 |
| M3 | `kinh` về `windows_all` | `VoiceWindowScopeTest` 2 |
| M4 | `cac cua so` sang `window` (= đúng bản vá D đầu) | `VoiceWindowScopeTest` 1 — **chứng minh [P2] là lỗi thật, nay đã khoá** |
| M5 | `act()`: `doneConfirmed` → `done` | `VoiceActGateReadbackTest` 2 (`recirc` tier OVERDRIVE ⇒ hai câu **khác** chuỗi, không phải bài trang trí) |
| M6 | bỏ lượt đọc lại sau settle | `VoiceActGateReadbackTest` 2 |
| M7 | nhét `trunk` vào gói *"rời xe"* | `CtlSafetyPolicyTest` 1 — **chứng minh [P3] cửa sau** |
| M8 | bỏ cổng `slow` (luôn `failed()`) | `VoiceActGateReadbackTest` 1 — chiều *"không báo ✗ oan"* |
| M9 | nhét `recirc` vào `MOVES_SLOWLY` | `VoiceActGateReadbackTest` 2 — chiều *"không nới quá tay"* |

## 6. 🚗 CẦN ĐO TRÊN XE (xếp theo thứ tự quan trọng)

1. **Enum thô của `tailgate_status` / `sunroof_state` / `window_*`** — nếu `closed ≠ 0` thì quy ước `>0` sai ở
   **nhiều bề mặt** (ô nút · `GroupBoard` · `TelemetryReadout` · nay cả câu nói của E), không riêng lượt này.
2. **`trunk` có đọc ra số không** (finding [P3] §2) — nếu `null` thì E vô tác dụng cho đúng nút owner quan tâm, và
   cách chữa là đổi khoá đọc sang đường `BYDAutoSettingDevice`, cần số đo.
3. **C**: nói *"mở cốp"* lúc đang chạy phải bị từ chối kèm câu nói điều kiện; lúc đỗ phải mở được.
4. **D**: *"mở kính"* chỉ hạ **một** cửa · *"mở các cửa sổ"* hạ cả bốn.
5. **E**: thời gian chạy thật của kính/cốp/nóc ⇒ chốt khoảng chờ **theo từng nút** (thay cho hằng chung 300 ms).

## 7. Còn lại cho main-agent

- **CHƯA commit / CHƯA push** — HEAD vẫn `893483b`; chưa bump `versionCode`/`versionName`; chưa quét bảo mật
  trước commit (rule global §6 — việc của lượt ship).
- **Câu hỏi mở cho owner vẫn nguyên**: mức **Nửa** của cụm mơ hồ (`cde-done.md` §2). Lượt soát **không** tự quyết.
  ⚠ Một ghi chú làm rõ lựa chọn: đường (1) của impl — trỏ cụm mơ hồ sang `win_lf` — cũng kéo cụm mơ hồ vào
  **đường E của một bộ phận chạy chậm**; sau bản vá [P1] thì ca ấy chỉ còn ✓ + hedge (không ✗ oan), nên đường (1)
  nay **an toàn hơn trước khi vá**, nhưng nó đổi câu trả lời thành *"Kính trước-trái"*.
- Ngoài phạm vi, không đụng: `voice/tts/kachi-giong-be-v1/` + 6 handoff cũ đã untracked từ các phiên trước.
