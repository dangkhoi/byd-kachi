# (N) ADAS-PURGE — gỡ sạch mọi chức năng an toàn/ADAS khỏi launcher

> **Trạng thái**: Current · **Cập nhật**: 2026-09-16 · **Mục đích**: ghi lại ĐÚNG những gì bị xoá trong lượt
> ADAS-PURGE, con số registry trước/sau, bằng chứng grep "0 dead code", và số bài test/E2E sau khi xoá.
> Bản build: **1.67 (68)** (không bump — đi cùng PERF + hotfix voice của cùng phiên).

## 0. Lệnh của owner (nguyên văn, 2026-09-16)

> *"clean toàn bộ các chức năng an toàn, ADAS trong launcher này, có rủi ro nếu chạy sai, ảnh hưởng an toàn lái
> xe, dẹp hết toàn bộ HAL, nút, chức năng, widget, whatever, coi như chưa bao giờ tồn tại, người lái xe muốn
> chỉnh gì mấy này thì vào setting thật của xe làm, mình không can thiệp vào cái này nhé, clean sạch sẽ nhé,
> không để lại deadcode gì luôn nhé"*

Đây là **quyết định của owner**, không phải một phép tối ưu: một lệnh sai bắn vào hệ an toàn chủ động là rủi ro
trên đường thật. Launcher **không** còn đọc, không còn ghi, không còn bày ra thứ gì thuộc ADAS/an toàn.

## 1. Đã xoá — danh sách đầy đủ

### 1.1 Nút (`ControlRegistry`) — 10

`adas_slw` (cảnh báo quá tốc) · `adas_esp` (ESP) · `adas_tsr` (nhận diện biển báo) · `adas_lane` (giữ làn) ·
`adas_fcw` (cảnh báo va chạm trước) · `adas_rcta` (cắt ngang sau) · `adas_dow` (cảnh báo mở cửa) ·
`adas_cpd` (phát hiện trẻ em) · `itac` (kiểm soát mô-men) · `avh` (giữ phanh tự động).

### 1.2 Thông tin đọc (`TelemetryRegistry`) — 17

`seatbelt_driver` · `seatbelt_passenger` · `oms_driver` · `oms_passenger` · `child_presence` ·
`speed_limit_warning` · `bsd_fl_alarm` · `bsd_fr_alarm` · `lca_left` · `lca_right` · `rcta_left` · `rcta_right` ·
`dow_left` · `dow_right` · `radar_zones` · `radar_volume` · `esp_state`.

### 1.3 Nhóm khả năng (`CapabilityGroups`) — 3

`g_adas` (*An toàn · ADAS*) · `g_occupants` (*Người ngồi*) · `g_parking` (*Cảm biến đỗ*). Cả ba chỉ gồm datum vừa
xoá ⇒ không còn gì để bày.

### 1.4 Giữ nhưng CHUYỂN NHÓM — 3

`mcu_status` · `volt_12v` · `volt_12v_level`: từ `Domain.SAFETY` → **`Domain.ENERGY`** (điện 12V và nguồn máy
không phải hệ an toàn lái). Ở `CarStatus` chúng rời cụm `Safety` sang cụm **`Energy`**.

Icon của ba mục cũng phải đổi: cả ba từng dùng `ic-bolt`, mà lĩnh vực Năng lượng đã có 3 mục khác dùng hình đó ⇒
`CapabilityIconsDiversityTest` báo `ic-bolt ×6` (trần 3). Nay: `volt_12v → ic-cell-volt` · `volt_12v_level →
ic-battery` · `mcu_status → ic-sensor`.

### 1.5 Kiểu/cơ chế bị gỡ hẳn ở `:core`

- `Domain.SAFETY` (enum) — `Domain.TELEMETRY` 8 → **7** domain.
- `CarStatus.Safety` (cả data class) — 3 field còn sống dời sang `CarStatus.Energy`.
- `GroupBoard`: `RADAR_ZONE_COUNT` · `RADAR_ALERT_LEVEL` · `radarLevels()` · `radarTone()` · `sidePlan()` ·
  `sideOf()`; mọi nhánh sắc thái ADAS trong `toneOf` (`alertIfNot` · `alarmLevel` cũng rụng theo).
- `GroupBoardModel`: `GroupSide` (enum) · `GroupCell.side` · `leftCells`/`rightCells`/`centreCells` ·
  `SideBoardPlan` (data class).
- `CapabilityGroupModel`: bảng `EXPANDING` (chỉ có `radar_zones`) ⇒ `visibleReadCount` nay là `reads.size`.
- `CarDataAdapter.Gate.ints()` (chỉ `radar_zones` dùng).
- `HalBindingTable`: `readArg` bỏ `seatbelt_*`/`oms_passenger`; `featureDeviceFqn` bỏ nhánh `Domain.SAFETY →
  BYDAutoADASDevice`.
- `LauncherCatalog.iconFor`: bỏ nhánh `Domain.SAFETY → "ic-shield"`.

### 1.6 Tệp XOÁ hẳn

| Tệp | Vì sao |
|---|---|
| `app/src/main/java/com/byd/clusternav/launcher/RadarBoardView.kt` | ô vẽ 8 vùng cảm biến đỗ — nhóm `g_parking` không còn |
| `app/src/main/java/com/byd/clusternav/launcher/SideBoardView.kt` | ô vẽ sơ đồ hai bên xe — nhóm `g_adas` không còn |

### 1.7 Icon vector XOÁ — 24 tệp (`app/src/main/res/drawable/`)

Danh sách **sinh bằng máy** (quét mọi `ic_*.xml`, giữ lại tệp có ≥1 tham chiếu sống trong `core/` + `app/`):

`ic_car_top_bsd_l` · `ic_car_top_bsd_r` · `ic_car_top_lca_l` · `ic_car_top_lca_r` · `ic_car_top_rcta_l` ·
`ic_car_top_rcta_r` · `ic_car_top_rcta_all` · `ic_car_top_dow_l` · `ic_car_top_dow_r` · `ic_car_top_dow_all` ·
`ic_car_top_lane` · `ic_car_front_fcw` · `ic_car_top_park_all` · `ic_car_top_belt_fl` · `ic_car_top_belt_fr` ·
`ic_car_top_occupant_fl` · `ic_car_top_occupant_fr` · `ic_car_top_occupant_rear` · `ic_esp` · `ic_sign` ·
`ic_shield` · `ic_adas` · `ic_group_occupants` · `ic_group_parking`.

⚠ **KHÔNG xoá** `ic_torque` · `ic_brake` · `ic_speed` · `ic_volume` dù brief ban đầu liệt kê chúng: cả bốn còn
tham chiếu sống ngoài ADAS (`motor_front_torque → ic-torque`, `brake_pct → ic-brake`, `wheel_speed → ic-speed`,
nút `vol → ic-volume`). Xoá chúng là gãy build. Số tệp `ic_*.xml`: **168 → 144**.

`ic_*.xml` còn mồ côi **không** thuộc lượt này (đã mồ côi từ trước, ngoài phạm vi): `ic_check_selected` ·
`ic_corner_cut` · `segment_selected_bg` · `segment_selected_warm_bg` · `segment_track_bg` · `speed_dial_bg`.

### 1.8 Chuỗi `R.string` xoá

`kachi_speed_over` (*"Vượt tốc độ"* / *"Over the limit"*) và plural `kachi_board_hidden_n` — cả hai bản `values/`
+ `values-en/`. Widget *Tốc độ* (`WidgetViews.speed`) nay **chỉ** hiện tốc độ, không đổi màu/chữ theo
`speed_limit_warning`.

### 1.9 Voice

- `VoiceSynonyms.CONTROL`: bỏ `itac` (2 cụm) + `avh` (2 cụm).
- `SherpaSpokenWords.ACCENTED`: bỏ 4 mục có dấu tương ứng (*kiểm soát mô men · kiểm soát lực kéo · giữ phanh ·
  giữ phanh tự động*).
- Cụm ngữ pháp (`VoiceGrammarPhrasesTest`): giữ **342 → 305**, loại **274 → 236**, tổng mục **2 114 → 1 985**.
- Tệp hotword `core/build/hotwords/hotwords-phrases.txt`: **1 933 → 1 757 dòng** (sinh lại bằng
  `SherpaBiasingCoverageTest`).
- `scripts/emulator/voice-cases.tsv`: **0 ca** nhắm vào id đã xoá ⇒ **không bỏ ca nào**, tổng vẫn **70**.

### 1.10 Danh mục / catalog

- `docs/catalog/status-by-id.json`: **191 → 164** mục (xoá đúng 27 id đã gỡ).
- `docs/catalog/features.json`: **không có dòng nào** thuộc id đã xoá ⇒ giữ nguyên 133.
- `scripts/docs/feature-catalog.py`: bỏ `"SAFETY"` khỏi `DOMAIN_VI`; sinh lại `docs/kachi-feature-catalog.html`.

## 2. Số registry TRƯỚC → SAU

| Bộ đăng ký | Trước | Sau |
|---|---|---|
| Nút (`ControlRegistry`) | 64 | **54** |
| Thông tin (`TelemetryRegistry`) | 123 | **106** |
| Nhóm (`CapabilityGroups`) | 12 | **9** |
| Widget dựng tay (`WidgetRegistry`) | 9 | 9 (không đổi) |
| Gói lệnh (`ActionMacros`) | 4 | 4 (không đổi) |
| Hành động launcher (`LauncherActions`) | 3 | 3 (không đổi) |
| `Domain` (enum) | 9 | **8** · `Domain.TELEMETRY` 8 → **7** |
| Nhãn có bản EN (`LangCoverageTest`) | 319 | **288** |
| **Tổng "chức năng xe"** (nút + thông tin + gói + hành động) | **194** | **167** |

⇒ Con số **"187 chức năng"** dùng trong spec/backlog/công cụ CapTest **đã hết đúng**. Số mới cần ghi ở mọi chỗ:
**54 nút · 106 thông tin · 4 gói · 3 hành động = 167** *(2026-09-16 owner gỡ ADAS/an toàn — trước đó 187)*.

## 3. Bằng chứng "0 dead code" — grep sau khi xoá

Quét `core/ app/ car-integration/ scripts/ docs/catalog/ voice/` (bỏ `build/`, `.kotlin/`, `.gradle/`):

| Mẫu | Hit còn lại | Ở đâu |
|---|---|---|
| `adas_slw` `adas_esp` `adas_tsr` `adas_lane` `adas_fcw` `adas_rcta` `adas_dow` `adas_cpd` | **0** | — |
| `seatbelt_*` `oms_driver` `oms_passenger` `child_presence` `speed_limit_warning` | **0** mã sống | chỉ chú thích *"đã gỡ"* |
| `bsd_*` `lca_*` `rcta*` `dow_left` `dow_right` | **0** | — |
| `radar_zones` `radar_volume` `RadarZones` `esp_state` | **0** | — |
| `\bitac\b` `\bavh\b` | **0** mã sống | chỉ chú thích *"đã gỡ"* |
| `Domain.SAFETY` `\bSAFETY\b` | **0** mã sống | chỉ chú thích + 2 bài canh *"không được mọc lại"* |
| `\bADAS\b` | **0** ở tầng launcher | còn ở `carexec/*` (xem §4) + chú thích lịch sử |

Hai bài canh **chặn mọc lại** (không phải chú thích): `CapabilityModelTest` đòi `Domain.values()` không có phần tử
tên `SAFETY`; `CapabilityGroupsTest` đòi `CapabilityGroups.byId("g_adas"/"g_occupants"/"g_parking") == null`.

## 4. KHÔNG xoá — và lý do (có `file:line`)

| Thứ | Ở đâu | Vì sao giữ |
|---|---|---|
| `BYDAutoADASDevice.setAVMSwitchState` | `core/.../ControlRegistry.kt:215` (nút `cam`) | **tên lớp HAL thật của xe** cho **camera 360** — owner giữ camera 360. Không đổi được tên lớp của BYD. |
| `BYDAutoADASDevice` trong `HalBindingTable.deviceFqn` | `core/.../HalBindingTable.kt` | cùng lý do trên (route tên lớp), không còn nhánh `Domain.SAFETY` nào trỏ tới. |
| `CtlSafetyPolicy` + `CtlSafetyPolicyTest` | `core/.../CtlSafetyPolicy.kt:26,35` | **KHÔNG phải ADAS**: đây là cổng bắt `auto_confirm` cho nút **mở/khoá THÂN XE** (`lock` `door` `trunk` `hood` `sunroof` `sunshade` `window` `windows_all` `win_*`). Xoá nó làm launcher **kém an toàn hơn**, ngược ý owner. Ghi lại để owner chốt nếu muốn khác. |
| `carexec/*` (`CarExecHudCatalog` · `CarExecClusterDiagnosticsCatalog` · `CarExecScenarios` · `CarExecSpeedSignCatalog`) | `core/.../carexec/` | chữ "ADAS" ở đây là **lớp phủ ADAS của CHÍNH cụm đồng hồ xe** (cửa sổ Qt trên cluster) trong kịch bản RE/chẩn đoán — không phải chức năng launcher bày ra cho người lái. |
| `scripts/re/build-evidence-graph.py:34` (`adas_sla_output`) | `scripts/re/` | nút RE firmware (đồ thị bằng chứng), không phải launcher. |
| `docs/diagnostics/*` · `docs/specs/*` lịch sử | — | luật dự án: **không xoá lịch sử đo**, chỉ thêm ghi chú đầu mục. |

## 5. Test + E2E sau khi xoá (đếm từ JUnit XML)

| Bộ | Trước | Sau | Đỏ |
|---|---|---|---|
| `:core:test` | 2 006 | **1 994** | **0** |
| `:app:testDebugUnitTest` | 988 | **987** | **0** |
| E2E máy ảo `voice-e2e.sh --only say` (T1) | 69/70 | **69/70** | ca đỏ **duy nhất** vẫn là `t29` — lỗi của bộ ca, có từ 09-15, không liên quan lượt này |

Bài bị **gỡ** (không phải bị nới): 13 bài `:core` + 1 bài `:app`, mỗi chỗ để lại một dòng chú thích nói rõ bài đó
khoá cái gì và vì sao cái đó không còn. Bài bị **đổi số neo** (đếm lại từ registry, không nới luật): `LangCoverage`
(6 số) · `CapabilityGroups` (3 số) · `CapabilityPicker` (1) · `CapabilityIconPosition` (sàn 40 → 28) ·
`FeatureCatalogDump` (sàn 60 → 50) · `VoiceGrammarPhrases` (3) · `IconStyleContract` (12 → 9) ·
`IconGeometryContract` (sàn 55 → 42) · `GroupTileWiring` (bảng hình dạng 12 → 9 nhóm).

⚠ Một chỗ **nới có chủ ý, đã ghi lý do tại chỗ**: `GroupTileWiringContractTest` dò tệp thuộc tầng vẽ nhóm bằng
chuỗi `"GroupBoard."` → nay `"GroupBoard"`. Sau lượt xoá, `GroupBoardBinder.kt` không còn gọi `GroupBoard.<gì>`
nào nên nó rơi ra ngoài tầm mọi `assertFalse` — tức bài canh **tự yếu đi vì một lượt xoá ở chỗ khác**.

## 6. Còn nợ (🚗 / owner)

- **🚗 chưa đo trên xe**: bản 1.67 chưa chạy trên xe sau lượt xoá. Cấu hình ĐÃ LƯU của owner có thể còn trỏ tới id
  vừa xoá. ⚠ **Đã VÁ ở Pass 1 (xem §7 P0-1)**: ô mồ côi nay **rụng lúc nạp** (`WorkspaceState.sanitized`) kèm một
  dòng log, thay vì hiện `ADAS_FCW` + `"—"` mãi mãi. Vẫn cần một lượt mở app trên xe để nhìn màn thật.
- **owner chốt**: có giữ `CtlSafetyPolicy` không (xem §4) — mặc định của lượt này là **giữ**.

## 7. Pass 1 — 2026-09-16 (senior review)

Phạm vi soát: toàn bộ `git diff` chưa commit vs `703fb14` + tài liệu này. Version **giữ 1.67 (68)** (không bump,
không commit). Grep chạy lại **bằng máy** trên `core/ app/ car-integration/ scripts/ voice/ docs/catalog/`
(bỏ `build/`).

### 7.1 Phát hiện + bản vá

| # | Mức | Chỗ (`file:line`) | Phát hiện | Đã làm |
|---|---|---|---|---|
| 1 | **[P0]** | `app/.../WidgetViews.kt:85,238` · `core/.../LauncherCatalog.kt:244-270` | **Ô đã lưu của người dùng thành ô hỏng vĩnh viễn.** `CapabilityCatalog.HIDDEN_FROM_PICKER` chốt luật *"ẩn khỏi bộ chọn ≠ XOÁ MÃ"* đúng vì *"mã là KHOÁ LƯU BỀN"* — lượt này xoá **27 mã + 3 mã nhóm** khỏi registry mà **không có đường chuyển đổi**. Thanh nút (`ControlDockView.rebuild` nhánh `null -> Unit`) và chip thanh trên (`TopStripChips.render` `mapNotNull`) đã bỏ mã lạ từ trước; **ô giữa màn thì không** — nó rơi xuống `telemetry(...)` → `TelemetryReadout.of` trả `null` → vẽ `label(ctx, id.uppercase(), "—", "")` ⇒ người lái thấy một ô ghi thẳng **`ADAS_FCW`** trên màn chính, đúng thứ owner bảo *"coi như chưa bao giờ tồn tại"*. Không sập, nhưng không tự khỏi và không có cách nào biết vì sao. | `WorkspaceState.sanitized()` nay **bỏ mã widget không còn trong bộ đăng ký** (ô chỉ còn mã chết ⇒ về TRỐNG); thêm `WorkspaceState.unknownWidgetIds()` để `PrefsWorkspaceRepository.load()` ghi **một dòng log** (`KachiWorkspace [dọn ô] …`) — mất một thứ đã lưu mà im lặng là kênh im lặng. Đặt ở tầng MÔ HÌNH chứ không ở `SlotCodec` (dạng chuỗi trên đĩa là hợp đồng lưu bền; *"mã này còn không"* là câu hỏi về registry). Mã chỉ **bị ẩn** (`HIDDEN_FROM_PICKER`) KHÔNG bị đụng. 2 bài hồi quy `:core` nạp đúng `adas_fcw` · `radar_zones` · `g_adas` + một bài khoá luật mã-ẩn-vẫn-sống. |
| 2 | **[P2]** | `app/.../KachiSpace.kt:228-238` | `Sp.BOARD_ROW_MIN` — hằng của **riêng** `SideBoardView`, sau lượt xoá **0 chỗ gọi**. Dead code. | Xoá hằng, để lại 3 dòng nói rõ nó từng là gì và suy lại từ đâu nếu cần. |
| 3 | **[P2]** | `core/.../GroupBoardModel.kt:41` | `GroupTone.isLoud` — chỗ gọi DUY NHẤT là `GroupBoard.sidePlan` (đã xoá) ⇒ **0 chỗ gọi**. Đúng loại *"nút chết"* dự án cấm (CLAUDE.md §8). | Xoá thuộc tính, để lại chú thích. |
| 4 | **[P2]** | `docs/catalog/features.json` (mục 110 · 112 · 114) | Danh mục **tay-khảo-sát** vẫn ghi *"CapTest — 123 thông tin + 64 hành động"*, *"187 diễn giải"*, *"quét CẠN 187 mục"*. Đó là **mô tả hiện trạng**, không phải phép đo lịch sử ⇒ nói sai. Grep id thì sạch, nhưng grep **con số** thì không. | Sửa 5 chỗ sang 167 / 106 / 54 kèm ghi chú *"trước đó 187"*; chạy lại `scripts/docs/feature-catalog.py`. |
| 5 | **[P3]** | `core/.../CapabilityModel.kt:55` · `app/.../GroupTiles.kt:32` · `GroupTileViews.kt:131,204` · `KachiPalette.kt:48` · `KachiSpace.kt:208-211` · `TyreBoardView.kt:31,77` · `KachiType.kt:34` · `DoorBoardView.kt:106,170` · `TypeScaleContractTest.kt:26,36` · `SpacingScaleContractTest.kt:26` · `CapabilityModelTest.kt:50` | **KDoc treo + KDoc nói sai thì hiện tại**: 8 liên kết `[RadarBoardView]` / `[SideBoardView]` / `[SideBoardPlan]` trỏ vào lớp đã xoá; `WidgetShape.BOARD` còn ghi *"8 zone radar"*; `GroupTiles` còn cấm mã `radar_*`; `GroupTileViews` còn lấy *ADAS 10 mục · Người ngồi 5 mục* làm ví dụ SỐNG cho luật giữ icon; `CapabilityModelTest` còn kể `adas_esp` là một trong *"4 nút yếu nhất bộ"*. | Sửa hết 13 chỗ. Phép **đo** (`[ĐO]` 2026-09-12) giữ nguyên làm bằng chứng, chỉ thêm một dòng nói bảng đó đã xoá — luật dự án §12: không xoá lịch sử đo. |
| 6 | **[P3]** | `app/.../GroupTileWiringContractTest.kt:272` | Bảng cấm *"tầng vẽ chép tay mã thành viên"* **rụng mất** `"radar_` và `"seatbelt_` khi bỏ hai ô vẽ — mất một cổng chống-mọc-lại rẻ tiền. | Thêm lại `"radar_` `"seatbelt_` và thêm `"bsd_` `"adas_`, kèm lý do: nay chúng là **cổng chống mọc lại**, không còn là cổng chống bản-sao-thứ-hai. |
| 7 | **[P3]** | `docs/specs/kachi-settings-screen.html` · `kachi-unified-capability-tile.html` · `kachi-profiles-are-everything.html` · `kachi-captest-v2.html` | Bốn spec còn nói *"187 ô"* / *"123 + 64"* / *"191 mục"* mà **không có** ghi chú lịch sử (ba spec khác đã có). `kachi-captest-v2.html` là spec **Draft sắp làm** ⇒ số sai ở đó sẽ chảy thẳng vào lượt sau. | Thêm một dòng Changelog *"con số … từ đây là LỊCH SỬ · hôm nay 167"* vào cả bốn, đúng khuôn ba spec kia. |

### 7.2 Đã KIỂM và **không** phải sửa (ghi ra để lượt sau không kiểm lại)

- **Grep sạch**: 0 hit mã sống cho mọi id/hằng đã gỡ (kể cả `GroupSide` · `SideBoardPlan` · `RADAR_*` · `sidePlan`
  · `sideOf` · `alertIfNot` · `alarmLevel`). Hit còn lại **chỉ** là chú thích *"đã gỡ"* + hai bài canh chống mọc lại
  + bốn chuỗi trong bảng cấm ở mục 6 trên.
- **`BYDAutoADASDevice`** còn đúng **4** chỗ, tất cả hợp lệ: `ControlRegistry.kt:213,216` (`bindingKey` camera 360
  `setAVMSwitchState`), `HalBindingTable.kt:302` (chú thích enum AVM), `BydFeatureIds.kt:180` (bảng tra `1038 → tên
  lớp`). `deviceFqn` là công thức chung nên route camera vẫn dựng ra
  `android.hardware.bydauto.adas.BYDAutoADASDevice`; **không** còn nhánh `Domain.SAFETY` nào.
- **`R.string` mồ côi = 0** (quét bằng máy: 463 khai báo `string`/`plurals`/`string-array` trong `values*/`, mỗi
  cái đều có ít nhất một tham chiếu `R.string.*`/`@string/*`). `kachi_speed_over` · `kachi_board_hidden_n` đã sạch
  cả hai ngôn ngữ.
- **Drawable mồ côi**: quét bằng máy toàn bộ 177 tệp `res/drawable/*.xml` (144 tệp `ic_*` — khớp §1.7) qua
  `app/src` + `core/src`. Mồ côi thật: **2**, và **cả hai có TỪ TRƯỚC**, không thuộc lượt này ⇒ **không xoá**:
  `segment_selected_bg` · `segment_selected_warm_bg`. (`ic_check_selected` · `ic_corner_cut` **không** mồ côi — có
  dòng trong `IconStyleContractTest.orphanPending`; `segment_track_bg` · `speed_dial_bg` được `layer-list` khác
  tham chiếu. Doc §1.7 liệt kê 6 tệp là **quá tay** — chỉ 2 tệp đúng.)
- **Đường suy giảm còn lại đều an toàn**: `ControlDockView.rebuild` bỏ mã lạ (`null -> Unit`) · `TopStripChips.render`
  `mapNotNull` · `DockConfig.setEnabled` từ chối lúc GHI · `TopStripConfig`/`CapabilityCatalog.pick` trả `null` chứ
  không ném · `voice_confirm_ids`: màn chọn dựng từ `VoiceRisk.askableIds()` (bảng riêng, không đụng registry) nên
  một mã chết còn trong prefs chỉ đơn giản không bao giờ khớp. **Không** có `Domain.valueOf` / `first {}` / `!!`
  nào chạy trên dữ liệu đã lưu.
- **Bài canh bị nới**: doc §5 nói `GroupTileWiringContractTest` *"tự yếu đi"* khi đổi `"GroupBoard."` → `"GroupBoard"`.
  Kiểm lại: đổi đó **NỚI CHIỀU DÒ** (bắt được nhiều tệp hơn), và vì `builders` được so bằng `assertEquals` với danh
  sách khai nên nó **chặt hơn**, không yếu hơn — `GroupBoardBinder.kt` vẫn nằm trong `tiles` và vẫn chịu mọi
  `assertFalse`. Bảo chứng duy nhất thật sự mất là bốn chuỗi ở mục 6 §7.1, đã trả lại.
- **Các số neo hạ xuống đều suy từ registry, không phải "hạ cho xanh"**: `covered.size >= 65` (phủ thật **71**,
  dư 6 — bản cũ 80 với 88 phủ, dư 8) · `cars.size >= 42` (thật **44**) · `IconStyleContract` `assertEquals(9,…)` là
  neo CHÍNH XÁC chứ không phải sàn · `CapabilityGroupsTest` bỏ sàn `>= 10` của R1 nhưng ngay trên nó là
  `assertEquals(9, …)` — chặt hơn một cái sàn, **và** thêm ba `assertNull` chống mọc lại.
- **Voice**: 0 mục `SherpaSpokenWords.ACCENTED` / `VoiceSynonyms` / `VoiceLexicon` trỏ tới id đã xoá;
  `SherpaBiasingCoverageTest` (15 bài, có *"khong co muc chet"*) **xanh**; `hotwords-phrases.txt` sinh lại
  **1 757** dòng (không đổi sau các bản vá của Pass 1 — Pass 1 **không** đụng logic voice/registry).
- **Danh tính**: `docs/kachi-feature-catalog.html` ghi *"Chủ: dangkhoi"*; 0 hit email / IP / VIN trong catalog +
  `docs/catalog/*.json` + tài liệu này.

### 7.3 Số cuối (đếm từ JUnit XML)

| Bộ | Trước Pass 1 | Sau Pass 1 | Đỏ |
|---|---|---|---|
| `:core:test` | 1 994 | **1 996** (+2 bài hồi quy ô mồ côi) | **0** |
| `:app:testDebugUnitTest` | 987 | **987** | **0** |

E2E máy ảo **không chạy lại**: Pass 1 không đụng một dòng logic voice/registry nào (tệp hotword byte-giống,
số registry không đổi), và bản vá P0-1 chỉ tác động khi cấu hình đã lưu chứa mã chết — máy ảo không có mã nào như
vậy nên `sanitized()` là no-op ở đó. Mốc T1 **69/70** của §5 vẫn là số đang có hiệu lực (`t29` đỏ sẵn từ 09-15).

### 7.4 Kết luận

**APPROVED.** Lượt xoá làm đúng và làm sạch; một lỗi **[P0]** thật (ô đã lưu thành ô hỏng vĩnh viễn) đã vá kèm
bài hồi quy, ba mẩu dead code còn sót đã dọn, tài liệu/số đếm đã khớp. Còn **đúng hai** việc ngoài tầm off-car:

- **🚗 cần XE** — mở app sau khi nâng cấp từ một bản có ô ADAS, xác nhận log `KachiWorkspace [dọn ô] …` xuất hiện
  đúng một lần và không ô nào mồ côi trên màn.
- **👤 cần OWNER** — chốt `CtlSafetyPolicy` (§4): lượt này **giữ**, vì nó là cổng xác nhận cho nút **thân xe**
  (khoá/cửa/cốp/kính), không phải ADAS; xoá nó làm launcher **kém an toàn hơn**.
