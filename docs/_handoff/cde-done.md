# Handoff — 3 fix voice C · D · E (owner test xe 1.79)

> **Trạng thái**: DONE off-car · **Ngày**: 2026-09-19 · **CHƯA commit** (main-agent verify + ship)
> **Mục đích**: 3 fix từ phản hồi owner sau lượt lái thử 1.79 — C (cốp/ca-pô chỉ mở khi dừng) · D (*"mở kính"* mơ hồ → kính lái) · E (TOGGLE/COVER đọc-được thì đọc lại xác nhận).

## 0. Kết quả đo

| Phép đo | Kết quả |
|---|---|
| `./gradlew test --rerun-tasks --continue` (5 module) | **4861 test / 0 đỏ** (mốc 1.79 = 4829 ⇒ **+32**) |
| core · app · car-int · vehicle-contracts · offcar | 2299 · 2380 · 61 · 22 · 99 — tất cả 0 đỏ |
| `:app:compileReleaseKotlin` | BUILD SUCCESSFUL |
| Thử làm ĐỎ (4 phép) | đỏ **đúng chỗ** cả 4 — §5 |
| Trần 500 dòng | mọi tệp sản phẩm ≤ 500 — §4 |

⚠ **Lệnh verify trong đề bài không chạy được như ghi**: `:app:testReleaseUnitTest` **không tồn tại** — biến thể unit-test của `:app` là `debug` + `vehicleTest` (`./gradlew :app:tasks --all`). Đã dùng `:app:testDebugUnitTest`, và chốt bằng `./gradlew test` (đủ 5 module, cả 2 biến thể app).

⚠ **`--rerun-tasks` là bắt buộc**, không phải tuỳ chọn: lượt chạy đầu báo `BUILD SUCCESSFUL` với task cache trong khi các bài canh **quét mã nguồn lúc chạy** (`VoiceCommandWiringContractTest`, `LayeringRulesTest`) chưa hề chạy lại — đúng bẫy dấu-xanh-giả đã ghi ở `conversation-protocol.md` P5.

## 1. C — cốp/ca-pô chỉ mở khi xe DỪNG

- `CtlSafetyPolicy`: `REQUIRES_STATIONARY = setOf("trunk", "hood")` + `requiresStationary(id)`. Tiêu chí kết nạp ghi ở KDoc là **"mở ra thì bung khỏi bao xe / che tầm nhìn"**, *không* phải *"thuộc BODY"* — nên kính · cửa sổ trời · khoá cửa **KHÔNG** vào (mở kính lúc đang chạy là việc bình thường; gate rộng = lỗi, không phải an toàn).
- `VoiceDispatcher.runControl`: gate đặt **sau** khi tính `arg`, **trước** `actByKind`; chỉ chặn `arg > 0` (ĐÓNG cốp lúc đang chạy là đường **chữa**, không được chặn).
- Tốc độ đọc **TƯƠI** trước (`freshCar("speed")`) rồi mới lùi về ảnh chụp. Cần cả hai vì `AppContainer.refreshForRead` trả `null` khi datum **đang được poll** (đang hiện trên màn) — không có đường lùi thì gate mất số ở đúng ca tốc độ đang hiển thị.
- **`null` ⇒ CHO PHÉP (fail-open)**, có chủ ý: `speed` ở mức PROVEN nên trên xe thật gate có số; `null` gần như chỉ xảy ra off-car/máy ảo. Fail-closed thì mỗi lần đọc hụt trên xe **đỗ** thành một lời từ chối cho ca dùng thường nhất của nút.
- `VoiceReply.notWhileMoving` — nói ra **điều kiện mở được** (*"chỉ mở được khi xe đang dừng"*), không phải một lời từ chối trơn.

## 2. D — *"mở kính"* mơ hồ → kính LÁI, không phải cả 4

`VoiceSynonyms.CONTROL`: 4 cụm mơ hồ `kinh` · `cua so` · `cac cua so` · `windows` dời từ `windows_all` → `window`. `windows_all` giữ cụm TƯỜNG MINH-tất-cả (`het kinh` · `toan bo kinh` · `moi kinh` · `every window` · `het kieng` · `bon kinh`).

Vì sao bản cũ sai: bản vá [SOÁT P2] gắn cụm mơ hồ vào nút GỘP với lập luận *"nó thuộc diện CONFIRM nên còn hộp hỏi lại"* — lập luận đó **sập** ở mặc định thật, vì `voice_confirm_ids` mặc định **RỖNG** (owner chốt *"không hỏi xác nhận gì cả"*) ⇒ không hộp nào hiện ⇒ câu mơ hồ nhất đi thẳng tới việc rộng nhất.

**[ĐO] số cụm ngữ pháp KHÔNG đổi** ⇒ `VoiceGrammarPhrasesTest` / `LangCoverage` / hotword **không phải cập nhật con số nào** (dời cụm giữa hai mã không đổi tổng).

### ⚠⚠ MỘT CÂU HỎI CHO OWNER — cụm mơ hồ **mất mức Nửa**

[ĐO off-car] *"mở một nửa kính"* / *"mở kính một nửa"* / *"mở kính 50%"*: trước `Control(windows_all, 2)` → nay **`Control(window, 1)`** = mở TRỌN kính lái.

Cơ chế: cờ `half` chỉ có tác dụng với `COVER` (`VoiceControlParse.control:33`), mà `window` khai là **TOGGLE**. Đó là một quyết định **có chủ ý đang bị khoá bằng test** — `ControlWriteArgsTest:39` liệt kê `{window, win_lf}` là *"cùng cửa kính lái, khác kiểu ô (TOGGLE vs COVER)"* ⇒ lượt này **không tự đổi registry**.

Giảm nhẹ đã có: (a) **không nói dối** — `VoiceReply.preview` cho TOGGLE đọc *"Mở Kính cửa lái"*, không nhắc *"Nửa"*, nên người lái nghe ra đúng việc đã xảy ra; (b) mức Nửa vẫn tới được bằng đường **tường minh** (*"mở một nửa toàn bộ kính"* · *"mở nửa kính trước trái"*) ⇒ tính năng 1.74 **không mất**.

**Hai đường chữa, owner chốt:**
1. **Trỏ cụm mơ hồ sang `win_lf`** thay vì `window` — cùng cửa kính lái đó (LHD: trước-trái = ghế lái), nhưng `win_lf` là `COVER` + có `args=[Đóng,Mở,Nửa]` + có `readKey="window_lf"` ⇒ được **cả** mức Nửa **lẫn** lượt đọc-lại của E. Sửa 1 dòng trong `VoiceSynonyms`. Giá: câu trả lời đọc *"Kính trước-trái"* thay vì *"Kính cửa lái"*.
2. **Cho `window` mức Nửa** (`COVER` + `args`/`argsEn`) — phải bỏ phân biệt TOGGLE/COVER đang khoá ở `ControlWriteArgsTest:39`, và sẽ đổi số nhãn EN ⇒ ripple sang `LangCoverage`/catalog.

Khuyến nghị: **(1)** — rẻ hơn, không đụng quyết định đã khoá, và thêm được E cho đúng câu về kính hay nói nhất.

## 3. E — TOGGLE/COVER đọc-được thì ĐỌC LẠI xác nhận

- Vai *"ghi xong thì đọc lại xe rồi mới nói"* tách thành **`VoiceReadback.kt`** (`:app`, 129 dòng) — `VoiceDispatcher` đứng ở 472 dòng nên thêm `sayActResult` sẽ vượt trần. Tách theo **VAI**: cả `step()` (R5, chuyển từ `sayStepResult` cũ) và `act()` (E mới) dùng **chung một khuôn ba nhánh**, để cạnh nhau là cách duy nhất khiến chúng không lệch nhau về sau.
- `runControl` kết bằng 3 lối: `STEP → readback.step` · `(TOGGLE|COVER) && readKey.isNotBlank() → readback.act` · còn lại → `done()` (**giữ** hedge *"chưa kiểm"* — ở đó thật sự không có gì để kiểm, hedge là **thành thật**).
- Ba nhánh của `act()`: `null` ⇒ `done()` (hedge) · khớp ⇒ **`doneConfirmed()`** (✓ trơn, **BỎ** hedge vì bằng chứng vừa được tạo trên chính chiếc xe này) · lệch ⇒ chờ `READBACK_SETTLE_MS` trên luồng NỀN, đọc lại **một** lần, rồi `doneConfirmed()` hoặc **`failed()`** (*"✗ xe không nhận lệnh"*).
- `VoiceReply.doneConfirmed` — khác `done()` ở đúng một chỗ: **không** gọi `unverified()`.

### Sai lệch spec có chủ ý (1 chỗ, hẹp hơn spec)

Spec ghi điều kiện là `def.readKey.isNotBlank()`; đã **giao** thêm cổng kind `(TOGGLE || COVER)` — đúng như tiêu đề spec (*"TOGGLE/COVER đọc-được"*). Lý do: `BUTTON` là nút bấm-một-phát (`pm25_clean_now`), mức **sau** khi bấm không nói gì về việc cú bấm có tới hay không ⇒ so mức ở đó sẽ báo *"✗ xe không nhận lệnh"* cho một cú bấm hoàn toàn bình thường. `SELECT` cũng vậy: mức của nó là **chỉ số lựa chọn**, `> 0` không mang nghĩa *bật* (chỉ số 0 là lựa chọn hợp lệ, không phải "tắt"). Có bài canh cho cả hai.

### 🚗 CẦN ĐO TRÊN XE (đường nóng của E chưa từng chạy thật)

`READBACK_SETTLE_MS = 300` là **giả định**, không phải phép đo (kế thừa từ R5). Với bộ phận **chạy vật lý vài giây** (kính · cốp · cửa sổ trời) 300 ms gần như chắc chắn **chưa đủ** ⇒ câu trả lời cho *"mở cốp"* có thể là *"✗ xe không nhận lệnh"* trong khi cốp **đang mở dở**. Cách chữa khi đo xong là khoảng chờ **theo từng nút**, không phải nới một hằng chung (nới chung = mọi câu trả lời chậm thêm vài giây). Ghi ở KDoc `VoiceReadback.READBACK_SETTLE_MS`.

## 4. Tệp đổi

| Tệp | Dòng | Việc |
|---|---|---|
| `core/…/CtlSafetyPolicy.kt` | 62 | **C** — `REQUIRES_STATIONARY` + `requiresStationary` |
| `core/…/voice/VoiceReply.kt` | 472 | **C** `notWhileMoving` · **E** `doneConfirmed` |
| `core/…/voice/VoiceSynonyms.kt` | 288 | **D** — dời 4 cụm mơ hồ |
| `core/…/voice/VoiceClarify.kt` | 401 | vá phụ (§6) |
| `app/…/VoiceDispatcher.kt` | **468** (từ 472) | **C** gate · **E** 3 lối · uỷ quyền readback |
| `app/…/VoiceReadback.kt` 🆕 | 129 | **E** — vai đọc-lại (`step` + `act`) |
| `core/test/…/CtlSafetyPolicyTest.kt` | 101 | +4 bài **C** (gồm cả chiều *"không gate quá tay"*) |
| `app/test/…/VoiceActGateReadbackTest.kt` 🆕 | 252 | 14 bài hành vi **C + E** (dựng `VoiceDispatcher` THẬT) |
| `core/test/…/voice/VoiceWindowScopeTest.kt` 🆕 | 82 | 2 bài **D** (tách chủ đề, §7) |
| `core/test/…/voice/VoiceIntentParserTest.kt` | **484** (từ 499) | dời 2 bài kính ra tệp trên |
| `core/test/…/voice/{VoiceClarifyQuestionTest,VoiceLogCases0918Test}.kt` | 270 · 302 | đổi kỳ vọng **D** |
| `app/test/…/VoiceCommandWiringContractTest.kt` | 740 (đã 738 từ trước) | +`VoiceReadback.kt` vào vùng quét |
| `app/test/…/LayeringRulesTest.kt` | 290 | +`VoiceReadback.kt` vào allowlist |

**Trần 500**: mọi tệp **sản phẩm** ≤ 500. `VoiceCommandWiringContractTest.kt` 740 là **có trước** (738 ở HEAD, tôi thêm 3 dòng vào vùng quét).

## 5. Thử làm ĐỎ — 4 phép, đỏ đúng chỗ cả 4

Sao lưu **theo tệp** (`/tmp/cde-bak`), **không** dùng `git checkout` (luật: cây chưa commit thì tuyệt đối không hoàn nguyên bằng git); đã `diff -q` xác nhận cả 4 tệp phục hồi **byte-identical**.

| Phép phá | Đỏ ở |
|---|---|
| `REQUIRES_STATIONARY → emptySet()` | `CtlSafetyPolicyTest.cop va ca-po…` + 3 bài `VoiceActGateReadbackTest` (**chứng minh dây nối đọc chính bảng policy**) |
| `doneConfirmed → done` trong `act()` | `doc lai khop thi bo duoi chua-kiem` · `lenh tat doc lai khop…` |
| trả `kinh` về `windows_all` | 2 bài `VoiceWindowScopeTest` (+3 bài khác ở lượt trước khi tách) |
| `readsFirst` lùi về `ids` khi reads<2 | `cau hoi thi chi dua ra thu doc duoc` |

Đếm `@Test` sau lượt tách tệp: `VoiceIntentParserTest` 41 → 39, `VoiceWindowScopeTest` 2 ⇒ **41 = 39 + 2**, không mất bài nào (luật đếm `@Test` sau mọi lượt sửa hàng loạt).

## 6. Vá phụ phát sinh — `VoiceClarify.readsFirst` (một lỗi NGỦ ĐÔNG mà lượt D làm lộ)

Lượt D làm đỏ `VoiceClarifyQuestionTest.cau hoi thi chi dua ra thu doc duoc`: câu *"tất cả cửa đang khóa hay đang mở"* hỏi lại thành **_"Khóa nào — Khóa / mở khóa hay Khóa trẻ em?"_** — mời người lái đọc tên hai cái **nút** để trả lời một câu hỏi về **trạng thái**.

Gốc **không phải** lượt D: `readsFirst` có một đường LÙI (`if (reads.size >= 2) reads else ids`) nói **ngược** lại chính `ambiguity` — hàm đó đã có sẵn cơ chế *"thử tiếp đầu họ sau"* khi một họ không đủ hai nhãn, và đường lùi kia **chặn** đúng vòng thử tiếp bằng cách trả về đủ 2 nhãn của **loại SAI**. Lượt D chỉ đổi thứ tự ủng hộ giữa hai đầu họ `cua`/`khoa` nên nó **làm lộ** chỗ này.

Đã bỏ đường lùi ⇒ họ không có mã đọc được thì trả rỗng ⇒ `ambiguity` thử họ tiếp (`cua` có 4 datum cửa) ⇒ câu hỏi đúng loại. Không họ nào đủ thì rơi về `vague()` — **vẫn mang `READ_VERB`** nên cổng D1 (câu HỎI không bao giờ thành lệnh GHI) còn nguyên.

## 7. Ghi chú kiến trúc cho lượt sau

- `VoiceIntentParserTest` đứng sẵn ở **499** dòng (sát trần 500) — mọi lượt thêm ca vào đó từ nay phải **tách theo chủ đề** như `VoiceWindowScopeTest`, đừng thêm thẳng.
- `VoiceReadback.kt` "thuần" theo phép đo của `LayeringRulesTest` nhưng **đọc/sửa `ControlTileState`** (state tầng vẽ `:app`) ⇒ đã vào allowlist `pureButMustStayInApp` kèm lý do, cùng lẽ `VoiceTargetDispatch.kt`.
- Vùng quét của `VoiceCommandWiringContractTest.dispatcher` đi theo **VAI** (nay 3 tệp) — tách tệp lần sau phải thêm vào đó, nếu không phạm vi kiểm mất mà bài vẫn xanh.

## 8. Còn lại

- 🚗 **Chưa đo gì trên xe.** Ba việc cần đo: (a) C — nói *"mở cốp"* lúc xe chạy phải bị từ chối, lúc đỗ phải mở; (b) E — `READBACK_SETTLE_MS` 300 ms với kính/cốp (§3); (c) D — *"mở kính"* chỉ hạ **một** cửa.
- Owner chốt câu hỏi mức Nửa ở §2.
- **CHƯA commit** theo yêu cầu; chưa bump `versionCode`/`versionName` (việc của lượt ship).
