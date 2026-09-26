# UX4 + UX5 · R4/R5 — gió gộp AUTO · datum ghế nói bằng hình và số

Spec: `docs/specs/kachi-274-ux-voice-camera.html` §3 R4 + R5 · §4.2 · lane UX4-UX5-CLIMATE-SEAT · off-car 2026-09-26 ·
bản gốc lỗi: 2.70/2.73 trên xe.

---

## 1. Nguyên nhân gốc

### UX4 — ô gió có một nấc **không tồn tại trên xe**

- **[ĐO xe 2026-09-20]** `docs/diagnostics/oncar-1.84-session-2026-09-20.md:41` ghi nguyên văn: *"⚠ KHÔNG phải
  `AC_WIND_MODE_SET` (=hướng gió) hay `AC_WIND_LEVEL_SET=0` (**bị bỏ qua**)"*. Nút `fan` khai `min = 0` nên người lái
  bấm `−` xuống tới 0 được, ô hiện chữ `"0"` — **trong khi quạt vẫn thổi**. Đây không phải một tính năng trang trí
  còn thiếu; đó là **một cái nút nói dối**, đúng họ lỗi mà `ControlVisuals` được dựng ra để dẹp.
- **[ĐO xe 2026-09-20]** thứ xe thật sự có ở đáy thang là **gió tự động**: `AC_CTRL_MODE_SET` (`=0` ⇒ AUTO · `=1` ⇒
  tay), rc = 0 hai chiều, owner đối chiếu trên màn AC gốc. Nút `ac_auto` đã ghi đúng đường này từ 1.85.
- **[ĐO mã]** `fan` (STEP) và `ac_auto` (TOGGLE) là **hai dòng registry rời nhau**; không một dòng mã nào nối chúng,
  nên tầng vẽ ô không biết `ac_auto` tồn tại. Datum cũng rời: `ac_wind` (mức) và `ac_wind_auto` (cờ auto) là hai mã,
  mà chip thanh trên dựng **một chip cho một mã** ⇒ về cấu tạo không thể hiện `"AUTO 1"`.
- **[ĐO xe 2026-09-16]** xe **vẫn báo mức gió khi đang AUTO**: `getAcControlMode() = 0` và `getAcWindLevel() = 1`
  trong **cùng một lượt đọc** (`docs/diagnostics/oncar-trace-2026-09-16b/hal-reads.txt:1,6`) ⇒ con số `n` trong
  `"AUTO n"` là số thật, không phải số bịa.
- **[ĐO mã]** cổng nhu cầu đọc (`CarDataDemand`) chỉ nạp đúng mã đang hiện và **không có khái niệm datum bạn đồng
  hành** ⇒ chip `ac_wind` một mình thì `ac_wind_auto` không bao giờ được đọc một lượt nào. Thiếu khâu này thì mọi thứ
  còn lại chỉ là chữ chết (CLAUDE.md §8: compile xanh ≠ chạy đúng).
- **[SUY]** *chữ AUTO hiện ra được* mới ở mức suy luận: datum `ac_wind_auto` = `getAcWindLevelManualSign` tới nay
  **chưa có một lượt `hal get` nào** trong `docs/diagnostics/` — nó chỉ xuất hiện như kế hoạch RE. Hai thứ **đã
  chứng minh** là (a) ghi mức 0 bị bỏ qua và (b) đường ghi auto chạy thật. Thiết kế vì thế giữ bất biến: **chưa đọc
  được ⇒ không bao giờ nói AUTO** (ca xấu nhất là *im lặng như hôm nay*, không phải *nói sai*).

### UX5 — chip ghế nói trạng thái bằng **chữ**, và sưởi ≡ mát

- **[ĐO mã]** chip thanh trên là **một glyph + một chuỗi**. Bốn nút ghế chia nhau đúng **hai** hình (`ic-seat-left`
  cho hai ô ghế lái, `ic-seat` cho hai ô ghế phụ) ⇒ **sưởi và mát trông y hệt nhau**. Hai datum mức lại mang hình
  thứ ba/thứ tư (ghế-nhìn-từ-trên và mặt trời) ⇒ cùng một khái niệm mà chip và nút vẽ hai thứ khác nhau.
- **[ĐO mã]** ghế **không** phải datum bật/tắt, nên nhánh *"trạng thái nói bằng icon, không bằng chữ"* (owner
  2026-09-21) không với tới nó ⇒ chip in đúng chữ `"Ghế sưởi · Tắt"` — lại là chữ trạng thái mà owner đã gạch bỏ.
- **[ĐO mã]** ô đọc lấy mức bằng cách **bóc chữ số đầu trong chuỗi đã dịch** (`"Mức 2"` → 2) — cùng cái bẫy so-chuỗi
  mà KDoc `TelemetryView.onOff` đã cấm: một bản dịch có số trong nhãn, hay một giá trị thập phân, là vẽ nhầm số vạch,
  im lặng.

---

## 2. Bản vá

### UX4 — nấc dưới cùng của thang tên là AUTO (dữ liệu + một luật thuần)

| Tầng | Thay đổi |
|---|---|
| dữ liệu | `ControlDef.autoId` (mặc định rỗng); `fan` khai `autoId = "ac_auto"`. Generic — nút thứ hai chỉ tốn một chữ, không dòng mã nào phải sửa (CLAUDE.md §7). |
| luật thuần | `ClimateAuto` (tệp mới, `:core`): `autoOnFromRaw` / `autoOnFromControl` / `fanText` / `StepIntent` + bảng quyết định / `StepPlan`. |
| đọc | `TelemetryReadout.format("ac_wind")` → `ClimateAuto.fanText` ⇒ chip header có `"AUTO 1"` **mà không phải sửa tầng vẽ**; `ac_wind` thêm nhãn ngắn `"Gió"/"Fan"`. |
| nhu cầu đọc | `CarDataDemand.COMPANION = { ac_wind → ac_wind_auto }`, áp lên **tập đã gom xong** (phủ cả đường ô nhóm); `controlsOf` nạp thêm `def.autoId`. |
| ô nút | `tileStep` giữ `auto: Boolean?` (null = chưa biết), hỏi `ClimateAuto.stepPlan`, thi hành: `toggle(autoId, …)` rồi (nếu có mức) chờ `ActionMacros.DEFAULT_GAP_MS` rồi `step(…)`. Đường đọc-lại lấy cả cờ auto và **không thoát sớm** khi mức đọc không ra. |

Bảng quyết định (`ClimateAuto.stepIntent`, mỗi ô có test):

| đang | bấm | ra |
|---|---|---|
| tay, đích > `min` | `−` / `+` | `SetLevel(đích)` — đường cũ, không đổi một dòng |
| tay, đích = `min` | `−` | **`EnableAuto`** — không bao giờ ghi mức `min` |
| AUTO | `+` | **`LeaveAuto(mức đang thổi + 1, kẹp ≤ max, không về min)`** |
| AUTO | `−` | **`NoOp`** — đã ở đáy; mã này không có nấc TẮT |
| chưa biết | `−` về `min` | `EnableAuto` — ghi `min` chắc chắn no-op, thử auto còn có cơ hội đúng |

**Hai cửa `autoOn*`, và vì sao không được gộp.** Đây là lỗi mà lượt soát đối kháng bắt được và nó sẽ làm ô nói
**ngược**: datum giữ **số thô** của khung (`AC_WINDLEVEL_MANUAL_SIGN_OFF = 0` ⇒ `0 = AUTO`), còn đường **nút** đã
đi qua `applyInverted` (vì `ac_auto` khai `readInverted`) nên đã quy về ước chung `1 = đang bật`. Đưa số của nút
vào cửa của datum là **đảo lần thứ hai** ⇒ ô hiện AUTO đúng lúc xe đang chỉnh tay, rc vẫn 0, **im lặng**. Hai hàm,
hai tên, và `ClimateAutoTest` khoá đúng sự khác nhau ấy (cùng con số `0` phải cho hai câu trả lời ngược nhau).

**Chữ trong ô là `"AUTO"`, con số sống trên chip.** [ĐO số học] ô giá trị của thanh nút dùng được ≈ 34dp, trừ đệm
hai bên còn ≈ 26dp; `"AUTO 1"` ở 15sp bold cần hơn gấp đôi ⇒ cắt cứng (`maxLines = 1`, không ellipsize).
**Sàn bề ngang CHUNG không được nới**: nâng `STEP_VALUE_CHARS` 3 → 4 là đặt sàn `"0000"` cho **mọi** ô stepper ⇒
`minWidth` thắng tỉ lệ ⇒ hai nút −/+ bị bóp và trôi ở **tất cả** các ô, tức lật đúng thứ R2.4 vừa chữa, cho một chữ
mà **một** nút cần. Cách chọn: giữ sàn chung, và ô nào có `stepValueChars(def) > STEP_VALUE_CHARS` thì **co chữ**
(`setAutoSizeTextTypeUniformWithConfiguration`, sàn 9sp). Nút không có AUTO **không đổi một pixel**.

### UX5 — mức là một con số, và bốn ô ghế là bốn hình

- **Lớp A (thuần).** `TelemetryView.level: Int?` — `0` = tắt · `1..n` = mức · `null` = chưa đọc / mã ngoài thang.
  Chip: mức 0 ⇒ chỉ nhãn + tông INACTIVE (bỏ chữ `"Tắt"`), mức ≥ 1 ⇒ ACTIVE + con số, `null` ⇒ `"—"` + NEUTRAL
  (*"không biết" ≠ "đang tắt"*). Điều kiện là **tính chất dữ liệu** (`CapabilityDots.maxLevel(id) >= 1`), không phải
  một nhánh theo mã ghế. Ô đọc dùng `v.level` thay cho regex, kẹp về số chấm vẽ được.
- **Lớp B (hình).** Bốn glyph GHÉP mới — `seat_{heat,vent}_{left,right}` — thân ghế chép từ `seat.svg` (thu 0,72,
  dịch xuống-trái) + **một** dấu phương thức ở góc trống: **ba làn nhiệt** (mô-típ của `defrost.svg`) cho sưởi,
  **bông tuyết** (mô-típ của `ac.svg`) cho mát. Hai mô-típ ấy đã là "chữ" mà launcher dùng cho nóng/lạnh nên người
  dùng không phải học hình mới. Bản `-left` (ghế LÁI) là **bản gương tính bằng máy** của bản `-right`
  (`x → 24 − x`, lật cờ sweep của cung) — quy ước cạnh giữ đúng như `ic-seat-left` đã dạy.
  - **Quyết định thiết kế bắt buộc nói ra — *"ba ngọn lửa"* ở 16dp.** (a) Ba làn nhiệt là **MỘT `<path>` ba
    subpath**, không phải ba path: chúng là **một** mô-típ (*"đang sưởi"*), không phải ba vật, và đường ống icon đếm
    path theo `<path>` nên cách này cũng giữ glyph trong trần ≤ 6 (mỗi glyph: lưng ghế + đệm + chân + dấu = **4
    path**, 2 lớp). (b) Quan trọng hơn cái đếm path là **bước giữa hai làn**: bản vẽ đầu đặt ba làn cách nhau 2,6
    đơn vị với biên độ ±1,0 ⇒ [ĐO số học] phần được tô của mỗi làn ≈ 2,9 đơn vị (0,8 thân + 1,8 nét) ⇒ khe còn
    **0,1 đơn vị**, ở 16dp là **dưới một pixel** — đúng nghĩa một vệt nhoè. Bản chốt nới bước lên **3,4** và hạ
    biên độ xuống ±0,9 ⇒ khe **0,8 đơn vị** (≈ 0,8px @16dp, 1.5×), cùng bậc với `ic_defrost` đang chạy hiện trường
    (bước 4,8 · khe 1,3). Vẫn là bề mặt **duy nhất** mà phép đo hình học không thay được mắt ⇒ xem §5 mục 4.
  - Sinh bằng `python3 scripts/design/gen-icons.py`; `--check` so byte **114/114 khớp**. Không vá tay drawable.
- **Lớp C (gộp).** Chip dựng sẵn `chip_seat`: sưởi > 0 ⇒ `[ghế+sưởi] n`; ngược lại mát > 0 ⇒ `[ghế+mát] n`; cả hai
  tắt ⇒ ghế trống, mờ, không số; cả hai chưa đọc ⇒ `"—"` + NEUTRAL. Cả hai cùng chạy **không nên xảy ra** ([SUY] RE
  `seat-comfort-auto.html` §2 nói hai chế độ loại trừ nhau, nhưng đường HAL của launcher chưa đo ca ấy) ⇒ quy tắc cố
  định, có test: **ưu tiên sưởi**, và cố ý **không** `require`/`error` (một cái chip không được làm sập màn chính vì
  xe trả một cặp số lạ).
  - ⚠ `DEFAULT_IDS` được **tách khỏi** `BUILT_IN` trong cùng lượt: trước đó `DEFAULT_IDS = BUILT_IN.toList()`, nên
    thêm một chip dựng sẵn là **lặng lẽ** mọc thêm chip thứ tư trên thanh trên của mọi người đang dùng máy — một
    thay đổi mặc định mà không ai xin. Hai danh sách trả lời hai câu khác nhau.
  - Hai datum lẻ **ở lại nguyên** (câu hỏi bằng giọng đọc chúng, và ai muốn hai chip vẫn đặt được).

---

## 3. Tệp đã tách (CLAUDE.md §4.1)

`ControlTileFactory.kt` đã **497/500 dòng** trước khi UX4 thêm một dòng nào (có bài canh
`ControlStateUxContractTest.moi tep cua luot WP2 duoi tran 500 dong`). Vai *"ô hành động của **chính launcher**"*
(`launcherTile`) tách sang `LauncherTile.kt` — đường cắt theo **vai**: đó là vai duy nhất trong tệp ấy **không nói
về cái xe** (không có dòng registry, không qua `CarControlPort`, không mang dấu *"chưa kiểm trên xe"*). Cùng lệ đã
dùng khi `readTileOf` rời sang `ReadTile.kt` ở WP2. Cửa vào `ControlTileFactory.launcherTile` giữ nguyên nên ba
tính chất mà bài canh của nó đo vẫn đo được ở cả hai đầu; sau lượt tách tệp còn **499** dòng.

---

## 4. Test khoá lại bài học

| Bài | Khoá cái gì |
|---|---|
| `ClimateAutoTest` (12) | hai cửa `autoOn*` **ngược nhau ở cùng con số 0**; chưa đọc ⇒ không bao giờ nói AUTO; toàn bộ bảng quyết định; nút không khai `autoId` đi đúng đường cũ; `autoId` phải trỏ vào một nút TOGGLE **có đường đọc**. |
| `ControlVisualsTest` (+3) | ô AUTO hiện `"AUTO"` và vẫn sáng màu nhấn; `null`/`false` ⇒ y hệt trước UX4; **sàn chung vẫn là 3** còn `stepValueChars(fan) = 4`. |
| `TelemetryReadoutTest` (+4) | chip gió ra `"AUTO 1"`/`"AUTO"`/`"3"`/`null` đúng bốn ca; **chữ mức và số mức không bao giờ lệch** (hai chỗ trong `TelemetryReadout` là cố ý — dạng `"id" -> s.<cụm>.<field>` là hợp đồng đọc-ngược bằng máy); thang ghế theo đúng số đo trên xe; lệch *thang ↔ số lựa chọn* phải là một lệch **đã biết** (hôm nay đúng `seath`/`seath_r`, [SUY] chờ đo). |
| `TopStripTest` (+6) | chip ghế hết chữ `"Tắt"`; sáng/mờ/trung tính theo mức; chip mang đúng hình của nút; chip gộp đổi hình theo chế độ; cả hai cùng chạy ⇒ ưu tiên sưởi; **chip dựng sẵn mới không vào mặc định**. |
| `CarDataDemandTest` (+3) | chip gió kéo theo chỉ báo AUTO **qua cả đường ô nhóm**; bảng bạn-đồng-hành chỉ chứa datum có thật; ô có mặt tự động kéo theo nút phụ. |
| `CapabilityIconsDiversityTest` (+2) | ô ĐỌC của một nút mang **đúng hình của nút ấy** (miễn trừ duy nhất có lý do: `ac_wind_auto`); **bốn ô ghế bốn hình**, quy ước `-left` = ghế lái. |
| `ControlStateUxContractTest` (+1, sửa 1) | nấc AUTO do `:core` quyết; đường đọc-lại dùng **cửa của NÚT** và cấm dùng cửa của datum; sàn chung không nới + có co chữ; `LauncherTile.kt` vào danh sách trần 500 dòng. |
| `ControlTileOffMainWiringContractTest` (sửa 1) | `control().toggle(` nay **đúng hai** chỗ (ô bật/tắt + nấc AUTO), chỗ thứ hai phải ghi qua cổng nút phụ và nằm trong làn nền. |
| `VoiceCommandWiringContractTest` (sửa 1) | phép kẹp dời vào `:core` ⇒ bài ghim **chỗ gọi** + kiểm `def.clamp` vẫn ở `ClimateAuto`. |
| `IconStyleContractTest` (+2 dòng miễn trừ) | hai tên icon mất chỗ dùng phải khai kèm lý do, **không** xoá tệp sinh (xoá làm `--check` lệch byte). |
| `CarDataDemandRendererContractTest` (sửa 1) | bài đọc-ngược nay hiểu **cách thứ hai** một chip đọc datum (qua `TelemetryReadout`), không chỉ chạm thẳng field. |

Kết quả: `:core` **2737 bài, 0 đỏ thuộc làn này** (2 đỏ còn lại là của làn voice/settings đang chạy song song:
`SettingsCatalogTest` mục `voice_commands`, `VoiceGrammarPhrasesTest` 190 → 191 cụm). `:app` bộ tập trung
**189 bài, 0 đỏ** (13 + 9 lớp bài canh liên quan).

---

## 5. Lượt kiểm bằng MẮT phải làm (máy ảo, rồi xe)

Lượt này **không** dùng máy ảo (một agent khác đang chiếm), nên mọi khẳng định về hình ảnh ở trên là [ĐO mã] hoặc
số học. Phải chốt bằng ảnh:

1. **Ô gió trên thanh nút** — chữ `AUTO` có **nằm trọn** trong ô giá trị không (đây là chỗ phép co chữ phải chứng
   minh nó làm việc), và hai nút `−`/`+` có còn **đúng vị trí cũ** ở cả ô gió lẫn ô nhiệt độ không (so ảnh
   trước/sau: nếu nhiệt độ nhích một pixel thì sàn chung đã bị đụng).
2. **Ô gió ở vùng BIG** (26sp) — `AUTO` ở cỡ chữ lớn hơn, vẫn phải vừa.
3. **Chip gió trên thanh trên** — `"Gió · AUTO 1"` có bị cắt đuôi không ở cấu hình 8 chip.
4. **Bốn ô ghế cạnh nhau** (bật cả bốn qua Tuỳ biến) — bốn hình phải **phân biệt được ở 16dp**, và ba làn nhiệt
   không được nhoè thành một vệt. Đây là điều duy nhất phép đo hình học **không** kiểm được.
5. **Chip ghế**: tắt ⇒ chỉ hình mờ, không chữ; mức 2 ⇒ sáng + số `2`; chip gộp đổi hình khi chuyển sưởi ↔ mát.
6. **Ô đọc ghế** — hàng chấm phải khớp con số (đừng để 3 chấm sáng trên một ô 2 chấm).

Trên xe (🚗, buổi sáng 27/09):

- `−` ở gió mức 1 ⇒ màn AC gốc của xe phải nhảy sang **AUTO** (đây là phép đo đóng khe [SUY] duy nhất còn lại).
- `+` khi đang AUTO ⇒ rời auto và mức nhảy đúng **mức đang thổi + 1**, không nhảy về mức mặc định (nếu nhảy về
  mặc định thì khoảng chờ giữa hai lệnh chưa đủ).
- Đọc `getAcWindLevelManualSign` một lượt và lưu vào `docs/diagnostics/` — tới nay **chưa có** lượt đo nào, và
  chừng nào chưa có thì chữ AUTO chỉ ở mức [SUY].
- Đọc `getSeatHeatingState(1)` ở **hai** mức khác nhau để đóng khe [SUY] thang ghế sưởi (`ControlLevels` đang khai
  4 mã khung mà nút chỉ bày 3 lựa chọn — bài canh mới giữ lệch này **nhìn thấy được**).

---

## 6. Bảng rà *"còn nên gộp gì nữa"* (R4 đòi kèm) — **chỉ hai cặp đầu được làm ở lượt này**

| Cặp | Khuyến nghị | Vì sao |
|---|---|---|
| `fan` + `ac_auto` (ô bấm) | **ĐÃ GỘP** | Owner xin, và nó vá một nút hỏng: [ĐO] ghi mức 0 bị xe bỏ qua. |
| `ac_wind` + `ac_wind_auto` (chip) | **ĐÃ GỘP** | Owner xin `"icon gió AUTO 1"`; [ĐO] hai getter độc lập, số có thật. |
| `seat_heat_state` + `seat_vent_state` (chip) | **ĐÃ GỘP** (`chip_seat`, không vào mặc định) | Owner xin; hai chip cùng hình ghế đứng cạnh nhau trên bề mặt hẹp nhất. |
| `seath` + `seatc` (ô bấm, ghế lái) | **NÊN GỘP — chưa làm** | Một ô *"Ghế lái"* thang hai cực (mát 2 · mát 1 · Tắt · sưởi 1 · sưởi 2). Cùng device, cùng `seatID`, không cần đường HAL mới. ⚠ **Chặn:** `seath` còn [SUY] 4 mức chưa đo ⇒ vẽ vạch hai cực bây giờ là hứa một mức chưa ai thấy. Đo trước, gộp sau. |
| `seath_r` + `seatc_r` (ô bấm, ghế phụ) | **NÊN GỘP — cùng lượt với ghế lái** | Cùng setter, chỉ khác `seatID`. Gộp một bên mà bỏ bên kia là để lại một bất đối xứng khó hiểu. |
| `defrost_front_state` + `defrost_rear_state` (chip) | **NÊN GỘP chip** | Người lái hỏi một câu (*"có đang sấy không"*) ⇒ một chip với hai dấu trước/sau sáng-mờ. |
| `defrost` + `defrost_rear` (ô bấm) | **KHÔNG GỘP** | Mỗi nút một việc, chạm một phát — owner đã chốt ở 1.94; gộp thành cycle là đảo một quyết định vừa duyệt (CLAUDE.md §6). |
| `ac_mode_auto` + `ac_wind_auto` (chip) | **DEDUPE — cần owner chốt** | Với người dùng là hai chip cùng đọc ra chữ `AUTO`. [ĐO 09-20] xe **không có nhiệt-auto** ⇒ `ac_mode_auto` gần như không còn trả lời câu hỏi nào. Giữ một, mã kia lùi về vai chẩn đoán (ẩn khỏi bộ chọn như `hood`/`cast`). |
| `temp_unit` (chip riêng) | **NÊN GỘP vào chính con số nhiệt** | Đơn vị là thuộc tính *trình bày* của một con số, không phải một trạng thái của xe; một chip chỉ để nói `"°C"` chiếm đúng chỗ mà con số nên chiếm. |
| `pm25_value` + `pm25_level` + `pm25_outside` + `pm25_online` (chip) | **NÊN GỘP thành một chip *"Không khí"*** | Bốn chip cho một câu hỏi là bốn lần chiếm chỗ. `pm25_online` nói về **thiết bị**, nên nó là *trạng thái của chip kia* (cảm biến chết ⇒ chip mờ + `"—"`), không phải một chip. |
| `window_lf/rf/lr/rr` (chip %) | **NÊN GỘP chip, GIỮ NGUYÊN ô bấm** | Bốn chip % gần như luôn cùng là 0. Phía hành động owner đã chốt ở 1.94 (*"mỗi nút một việc"*) ⇒ không đụng. |
| `ac_on` + `temp`/`inside_temp` | **GỘP MỘT NỬA** (chip nhiệt mờ khi AC tắt) | *"Đặt 22°"* khi máy lạnh đang tắt là một con số vô nghĩa. Phía hành động **không có gì để gộp**: [ĐO] registry không có nút ghi `ac_on` (chỉ có datum) — bịa ra một nút là bắn lệnh theo phỏng đoán. |
| `pm25` + `pm25_clean_now` | **KHÔNG GỘP** | Gộp buộc phải dùng **nhấn-giữ** — một cử chỉ mới, trên xe đang chạy, cho một việc không khẩn cấp; và *"bật lọc tự động"* ≠ *"lọc ngay một phát"*. |
| `child_lock` + `child_lock_r` | **GÓI LỆNH, không phải gộp ô — chờ owner duyệt** | Một `ControlDef` = một feature-id (registry đã ghi sẵn lập luận). Nhãn phải tiếp tục nói rõ bên nào: hứa *"cả xe"* mà khoá nửa xe là chỗ tệ nhất để hứa quá. |
| `recirc` + `ac_cycle` | **KHÔNG LÀM GÌ — đã đúng** | Đây là **hình mẫu** nối nút ↔ datum của dự án (`recirc.readKey = "ac_cycle"`), là thứ cặp gió/auto vừa noi theo. |

---

## 7. Việc còn lại / cần người khác quyết

1. **`forgetAbsentControl` chưa với tới bạn đồng hành** (`CarDataAdapter`, ngoài phạm vi làn này). Sau cú bấm `−`
   vào AUTO, lượt "đánh thức" chỉ quên nguội cho `ac_auto` + `ac_wind_auto`, **không** quên `fan`/`ac_wind` ⇒ mức
   gió có thể đứng chữ cũ tới nhịp tái thử sau. Đề nghị: quên thêm `def.autoId` (+ `readKey` của nó) và
   `CarDataDemand.COMPANION[readKey]`.
2. **Giọng nói và ngón tay đang làm hai đằng** (`VoiceDispatcher.runControl`, ngoài phạm vi làn này). *"Giảm gió"* ở
   mức 1 ra `clamp(0) = 0` ⇒ ghi mức 0 ⇒ **xe bỏ qua**, trong khi bấm `−` thì bật AUTO. Đề nghị: cho `runControl`
   đi qua đúng `ClimateAuto.stepPlan` như ô nút.
3. **`ac_auto` có nên ẩn khỏi bộ chọn không** (quyết định của owner). Sau UX4, ai đã đặt **cả** `fan` lẫn `ac_auto`
   lên thanh nút thì hai ô nói cùng một việc. **Không** tự gỡ khỏi prefs của xe đang chạy — đó là bố cục người ta
   đã đặt; cách đúng là hỏi owner có ẩn nó như `hood`/`cast` không.
4. **Một lượt đọc cho một cú chạm** vẫn giữ nguyên: cú bấm đọc mức thật tại chỗ, còn cờ auto lấy từ **ảnh chụp của
   vòng poll** (tối đa cũ 10 s). Đọc lần hai là phá ngân sách K1 đã ghi. Hệ quả xấu nhất: người lái vừa đổi chế độ
   trên màn BYD gốc rồi bấm ngay ⇒ một cú bấm đi nhầm nhánh, tự đúng lại ở nhịp poll sau.

---

## 8. UX5b (owner 2026-09-27) — chip ghế vào MẶC ĐỊNH, và **tách ghế lái / ghế phụ**

> Owner, ngồi trước máy ảo lúc 00:xx: *"sao còn ghế mát và ghế sưởi riêng, với ghế sao không có ghế lái hay ghế phụ?
> 2 ghế nó khác nhau mà"*.
>
> Đọc ra hai việc: (a) chip GỘP đã có từ UX5 nhưng **không ai tự đổi** cho người đang dùng máy — §2 lượt trước cố ý
> *"không vào mặc định"*, và hệ quả đúng như owner thấy: hai chip ghế lẻ vẫn nằm cạnh nhau; (b) chip ghế chỉ có **một
> bên** — ghế phụ không có datum nào để đọc.

### 8.1 Đã đổi gì

| Tầng | Thay đổi |
|---|---|
| datum | **+2**: `seat_vent_state_r` · `seat_heat_state_r` (*"Mức ghế mát/sưởi phụ"*, `Pass. vent`/`Pass. heat`). Cùng getter `BYDAutoSettingDevice.getSeat{Ventilating,Heating}State`, **chỉ khác `seatID` = 2** — khai ở `HalReadTables.readArg`, đúng cơ chế cặp sấy kính trước/sau (`area` 1/2) đã chạy từ T2. **KHÔNG** thêm trường `readArg` vào `TelemetrySpec`: đó sẽ là đường thứ hai cho cùng một con số. Tổng datum 64 → **66**. |
| trạng thái xe | `CarStatus.Climate.seatVentRRaw` / `seatHeatRRaw` (giữ **mã thô**, phép đổi mức vẫn ở `ControlLevels`). `CarDataAdapter` nối hai dòng `g.int(...)` — thiếu chúng thì datum compile xanh mà vĩnh viễn `"—"` (CLAUDE.md §8). |
| nút | `seatc_r`/`seath_r` **nay có `readKey`** (trước là write-only). Không getter nào bị đoán: cùng getter đã ĐO, khác `seatID`. Hệ quả kèm theo: ô ghế phụ có **mức thật**, và `CapabilityDots.maxLevel`/`iconOverride` nhìn thấy hai datum mới (bảng tra đảo của nó chính là `readKey`) ⇒ chip ghế phụ có hàng chấm + đúng hình của nút. Độ phủ đường đọc 13 → **15/33**. |
| icon | `seat_*_state_r` → `ic-seat-vent-right` / `ic-seat-heat-right` (bản `-right` của cặp glyph ghép UX5, đã có sẵn). Chip lúc **cả hai tắt/chưa biết** dùng ghế TRƠN: `ic-seat-left` (lái) · `ic-seat` (phụ) — `ic-seat` vì thế **ra khỏi** danh sách `unusedMapping` của `IconStyleContractTest`. |
| chip | `chip_seat` = **Ghế lái** · `chip_seat_r` = **Ghế phụ**, **một bộ dựng** (`TopStripChips.seatChip`) tham số hoá bằng (cặp mã · nhãn · hình ghế trống). Cặp mã khai **một chỗ**: `TopStripConfig.SEAT_PAIRS`. |
| nhãn chip | Chữ nay nói **GHẾ NÀO** (`"Ghế lái · 2"`), không nói chế độ (`"Ghế sưởi · 2"` như UX5): chế độ đã nằm trong glyph, còn thứ không hình nào nói được là bên nào. `ChipView.desc` vẫn đọc đủ **cả hai** chế độ nên trình đọc màn hình không mất gì. |
| mặc định | `DEFAULT_IDS` = `PM25 · TEMP · ENERGY · SEAT · SEAT_R` (3 → **5**). Ba chip cũ giữ nguyên thứ tự, chip mới **nối vào cuối** (CLAUDE.md §6). Cơ chế *"hai danh sách rời"* của UX5 **còn nguyên** — chỉ nội dung một danh sách đổi, một lần, do owner xin. |
| nhu cầu đọc | `CarDataDemand.CHIPS` +1 dòng cho `chip_seat_r`. Mặc định nay kéo 3 + 4 = **7** datum mỗi nhịp chậm (không có datum nhịp nhanh nào ⇒ vòng 1 Hz vẫn ngủ). |
| tách tệp | `TopStrip.kt` sẽ vượt trần 500 dòng ⇒ tách theo **VAI**: `TopStrip.kt` (cấu hình bền: danh sách · trần · mặc định · mã hoá · **di trú**) **370 dòng** · `TopStripChips.kt` (dựng chữ + hình) **203 dòng**. Không đổi một dòng hành vi (cùng package, cùng tên, cùng chữ ký). |

### 8.2 Luật DI TRÚ (`TopStripConfig.migrate`, gọi từ `decode` ⇒ áp cho **mọi hồ sơ** lúc nạp)

1. Danh sách có chip ghế **LẺ** (`seat_heat_state` / `seat_vent_state`) ⇒ thay bằng **MỘT** `chip_seat` tại **vị trí
   của cái đầu tiên**; thứ tự các chip khác không đổi; đã có `chip_seat` sẵn thì **không nhân đôi**.
2. **Không** có mã ghế nào **và** danh sách **bằng đúng mặc định CŨ** (`PM25,TEMP,ENERGY`) ⇒ nâng lên mặc định MỚI.
3. Còn lại ⇒ **KHÔNG ĐỤNG** (danh sách chip là thứ người ta tự đặt).

Tính chất có bài kiểm: **luỹ đẳng** · **giữ thứ tự** · **không trùng lặp** · không vượt trần.

**Đánh đổi nói thẳng** (luật 2): trên đĩa, *"chưa từng sửa gì"* và *"đã sửa và đang muốn đúng ba chip này"* là **cùng
một chuỗi** ⇒ ai cố ý giữ ba chip cũ sẽ thấy hai chip ghế mọc thêm **một lần**. Đường ra có và có test: gỡ chúng đi
thì lần nạp sau **không** mọc lại (danh sách lúc đó đã khác mặc định cũ ⇒ rơi vào luật 3).

### 8.3 Bề ngang thanh trên — [ĐO số học], chưa [ĐO] ảnh

Một chip = đệm `XS`×2 + icon `ICON_XS` + khe `S` + chữ, cộng `marginStart = S` ⇒ **32dp + chữ + 8dp**; chữ 13.5sp
(`KachiType.BODY`) ≈ 7dp/ký tự. Năm chip mặc định ở ca xấu nhất (mọi chip có giá trị): `"PM2.5 · Tốt"` ≈ 117 ·
`"24 °C ngoài"` ≈ 93 (không icon) · `"82 % · 418 km"` ≈ 131 · `"Ghế lái · 2"` ≈ 117 · `"Ghế phụ · 2"` ≈ 117 ⇒ tổng
≈ **575dp** trên ≈ **1240dp** của hàng chip ở 1920×720 ⇒ thừa **hơn hai lần**, không cần dựa vào phép cắt `…` của
`fitChips`. Vẫn phải chốt bằng MẮT (mục 8.5).

### 8.4 Test

| Bài | Khoá cái gì |
|---|---|
| `TopStripMigrationTest` (9, tệp mới) | cả ba luật di trú; luỹ đẳng; không nhân đôi; **gỡ rồi không mọc lại**; `decode` thật sự gọi `migrate`. |
| `TopStripTest` (+2, sửa 5) | nhãn chip nay là *ghế nào*; chip ghế PHỤ đọc **đúng cặp mã của nó** (hai ca CHÉO: mồi bên này, hỏi bên kia ⇒ *"chưa biết"*); ưu tiên SƯỞI đúng ở **cả hai** ghế; bài *"chip dựng sẵn mới KHÔNG vào mặc định"* **viết lại theo quyết định của owner** (không xoá — nó vẫn ghim thứ tự + việc hai danh sách là hai vật). |
| `TelemetryReadoutTest` (+1, sửa 3) | thang mức của hai datum `_r` (OFF=1→0 · 2→1 · 3→2); ca CHÉO field; bốn datum thang mức (không còn hai). |
| `ControlReadKeyTest` (+1, sửa 1) | bốn ô ghế: **cùng getter, KHÁC `seatID`** (gateway giả trả hai số theo arg ⇒ lẫn arg là đỏ ngay); độ phủ 15/33. |
| `ControlLevelsTest` (sửa 1) | bảng thang mức nằm trên đường đọc thật của **cả bốn** nút ghế. |
| `CarDataDemandTest` (+1) | `CHIPS` khớp `SEAT_PAIRS` (bản chép tay được **đo**, không được tin); 5 chip tổng hợp. |
| `CarDataDemandRendererContractTest` (sửa) | đọc nguồn ở `TopStripChips.kt`; hiểu **cách thứ ba** một chip đọc datum (gọi bộ dựng chung + tra `SEAT_PAIRS`). |
| `TopStripWiringContractTest` (+2) | di trú nằm trên đường NẠP thật; `DEFAULT_IDS` là khai báo **rời**, không suy từ `BUILT_IN`. |
| `IconStyleContractTest` (sửa) | `ic-seat` có chỗ dùng lại ⇒ phải RA khỏi `unusedMapping` (bài canh hai chiều). |
| đếm lại | `CapabilityGroupsTest` · `CapabilityPickerTest` · `LangCoverageTest` (64 → 66 datum, 239 → 241 nhãn). |

**Lượt kiểm đột biến** (3 phép, mỗi phép ít nhất một bài đỏ): bỏ chốt chống-trùng ⇒ 4 đỏ · nới phép so mặc định cũ
thành so TẬP ⇒ 1 đỏ (ca *"đã sắp lại thứ tự"*) · gỡ `migrate` khỏi `decode` ⇒ 3 đỏ (kể cả bài canh ở `:app`).

### 8.5 Còn phải làm

- 🚗 **Đọc `getSeatHeatingState(2)` / `getSeatVentilatingState(2)` ở hai mức khác nhau trên xe.** Hôm nay đường đọc là
  [ĐO] (lượt 2026-09-16 gọi cả seatID 1 và 2, cả hai trả giá trị hợp lệ — bằng nhau vì hai ghế đang tắt), nhưng
  *"mức nào ứng với mã nào ở ghế phụ"* vẫn [SUY]: nó **mượn** thang ghế lái ở `ControlLevels` (`seatc_r`/`seath_r`,
  owner B10 2026-09-22). Mã ngoài thang ⇒ `"—"`, không làm tròn, nên ca xấu nhất là *im lặng*, không phải *nói sai*.
  Cùng lượt xe ấy đo nốt `seath` (4 mã khung vs 3 lựa chọn — khe [SUY] đã ghi ở §5).
- 👁 **Máy ảo**: năm chip mặc định trên thanh 1920×720 — hai chip ghế có bị cắt `…` không (số học nói *không*);
  hai hình ghế trơn (`ic-seat-left` vs `ic-seat`) có **phân biệt được** ở 16dp không; bật sưởi ghế lái + mát ghế phụ
  cùng lúc ⇒ hai chip phải hiện **hai hình khác nhau** và số của đúng ghế mình.
- ↩ **Di trú chỉ chạy được một lần cho mỗi hồ sơ** và không có nhật ký. Nếu owner báo *"tự nhiên mọc thêm chip"* thì
  đó là luật 2 đang làm đúng việc của nó (mục 8.2) — không phải một lỗi.
