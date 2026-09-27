# L6 · Nợ mã — trần 500 dòng cho MỌI tệp · một cửa `SysProps` · danh mục chức năng dựng lại · full run 2.76

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 · **Mục đích**: đóng nợ off-car còn lại sau khi 5 làn L1–L5 xong (owner:
> *"làm hết tất cả off-car nợ… get things done, không kéo dài"*): (1) mọi tệp Kotlin/Python ≤ 500 dòng bằng **tách thuần**
> (không đổi hành vi); (2) gộp ba bản sao reflection `SystemProperties` về một cửa `SysProps` + bài canh; (3) dựng lại
> `docs/kachi-feature-catalog.html` để 10 mục 2.76 (camera IA/hồ sơ/lùi PANO/theo cụm · voice R5–R7 · chip R8/R9 · bố cục
> R11) có mặt kèm status thật; (4) chạy đủ 5 module + lint, đếm từ XML. Làn này **không** đụng `build.gradle.kts`, không
> `git commit`. Spec: `docs/specs/kachi-276-closing.html` (mục nợ mã).

Nhãn: **[ĐO]** đếm/đo trực tiếp (wc · XML · `--check`) · **[SUY]** suy từ mã · **[CHƯA BIẾT]**.

---

## 1. Bối cảnh · số đo TRƯỚC

| # | Sự thật | Mức | Nguồn |
|---|---|---|---|
| B1 | Sau L1–L5 còn **17 tệp > 500 dòng** (12 trong danh sách điều phối + 5 làn đẩy lên): 906 · 882 · 784 · 664 · 584 · 581 · 572 · 568 · 558 · 551 · 545 · 539 · 536 · 529 · 507 · 507 (×2) | [ĐO] `wc -l` toàn cây trước khi tách | §2.1 |
| B2 | Ba bản sao `private` của cùng phép reflection `Class.forName(…SystemProperties).getMethod("get")`: `AvmCamera.systemProp` · `SeatComfortApplier.systemProp` · `ClusterProfile.getProp` — KDoc `AvmCamera` 2.75 tự ghi nợ này vì hai tệp kia thuộc làn khác | [ĐO đọc mã] | CLAUDE.md §4.1 DRY |
| B3 | Baseline trước khi sửa: `:app` 1 490 bài / **1 đỏ** (`LauncherI18nContractTest.0 chuoi tieng Viet` — hai literal nhật ký `dải=` · `vùng=` trong `CameraOverlayView.kt` của L2, đúng như L1 báo chéo làn), `:core` 2 946/0, `:car-integration` 64/0, `:vehicle-contracts` 22/0, `:offcar-planner` 99/0 | [ĐO XML] | run baseline 13:5x |
| B4 | `gen-car.py --check` OK **41 tệp khớp byte** trước khi tách (mốc để chứng minh tách thuần) | [ĐO] | `python3 scripts/design/gen-car.py --check` |
| B5 | Danh mục `docs/kachi-feature-catalog.html` sinh 2026-09-26: 258 dòng (154 chức năng tay + registry 33 nút · 66 datum · 2 gói · 3 hành động); **0** dòng cho 10 việc của 2.76 | [ĐO] | `docs/catalog/features.json` 154 mục |

## 2. Cơ chế

### 2.1 Tách THUẦN — quy tắc chung
- **Mã sản phẩm**: thân hàm giữ nguyên byte; `private fun` trong lớp → **hàm mở rộng `internal`** cùng package trong tệp mới
  (khuôn có sẵn của repo: `ClusterNavBridgeCast.kt` · `KachiHomeWiring.kt` · `WorkspaceViewCards.kt` · `PrefsAutomation.kt`).
  Thành viên mà hàm chuyển đi chạm tới đổi `private` → `internal` (đúng một bước, không đổi kiểu/khởi tạo). Tên lớp,
  API công khai, chỗ gọi **không đổi**; chỉ 4 tệp gọi hàm `Prefs.badge*` ngoài package `com.byd.clusternav` thêm dòng
  `import` (hàm mở rộng cần import khi khác package).
- **Bài canh đọc mã nguồn** (`SourceRoots.body(src, "private fun render(")` …): trỏ sang tệp mới + chữ ký mới
  (`fun KachiHomeActivity.render(`), **không** nới điều kiện. 21 bài đổi mốc; danh sách ở §4.
- **Tệp test**: tách theo CHỦ ĐỀ thành lớp thứ hai (khuôn `VoiceWindowScopeTest` · `TelemetryReadout276Test`) hoặc rút
  hằng/trợ giúp ra `internal object …Fixtures` rồi `import …Fixtures.<tên>` (Kotlin cho phép nhập thành viên `object`).
  Ba bài niêm phong `offcar-planner` (`ExpansionTransportFenceTest` · `LegacyBaselineIdentityTest` ·
  `ExpansionDeterminismTest`) **giữ nguyên toàn bộ `@Test` trong lớp cũ** — chỉ hằng/lớp trợ giúp rời đi — để bốn cổng
  `GATE-X-O1/O2/O9/O10` của `scripts/verify-hud-sign-candidate-expansion.sh` (chạy `--tests <đúng lớp>`) không mất bài.
- **Python**: `gen-car.py` thành gói `scripts/design/gencar/` (paths · pathdata · model · icons · faces · paint · cli); tệp
  `gen-car.py` còn 32 dòng = docstring + `sys.path` + `from gencar import *` + `main`, để `python3 scripts/design/gen-car.py
  [--check]` không đổi và `render-car.py` (nạp bằng importlib, dùng `gc.ICONS` · `gc.FACE_VD` · `gc.load_face` …) vẫn thấy
  đủ tên. `ROOT` trong `gencar/paths.py` lên thêm một bậc (`../../..`). `stream-matrix.py`: bốn hàm audio → `stream_audio.py`
  cùng thư mục (nạp qua `sys.path.insert(0, HERE)` như `vi_text`).

Bảng tệp (dòng TRƯỚC → SAU · tệp mới):

| Tệp gốc | Dòng | Tệp mới (dòng) |
|---|---|---|
| `SimpleCastCoordinator.kt` | 784 → **448** | `SimpleCastCoordinatorIntents.kt` 222 (`handleCastFull` · `handleCastSlot` · `handleStop`) · `SimpleCastCoordinatorOps.kt` 138 (`openProjectionBody` · `doRepinEscapedCastApps`) |
| `KachiHomeActivity.kt` | 572 → **475** | `KachiHomeRender.kt` 111 (`applyThemeInPlace` · `render` · `selectPreset` · `applyCustomLayout`) |
| `Prefs.kt` | 558 → **457** | `PrefsBadge.kt` 117 (badge · upcoming · alert chip · vm bubble · float whitelist · migrate; 4 hằng `BADGE_*` ở lại) |
| `AppDrawer.kt` | 545 → **448** | `AppDrawerTiles.kt` 122 (`addWidgetGrid` · `widgetTile` · `addPickGrid` · `kindPill` · `pickTile`) |
| `ClusterNavBridge.kt` | 536 → **485** | `ClusterNavBridgeSystem.kt` 81 (quyền hệ thống ×3 + `hasSecureComponent` · `checkUpdate` · `openVietMapData` · `openDiagnostics` · `launch` · `applyRecircNow`) |
| `WorkspaceView.kt` | 507 → **494** | `WorkspaceViewSlotDomain.kt` 25 (`slotDomain` · `startSlotDrag`) |
| `ExpansionTransportFenceTest.kt` | 906 → **447** | `ExpansionTransportFenceFixtures.kt` 487 (`companion object` → `internal object`, kể cả nhật ký 27 lần cập nhật `T11_HASHES`) |
| `LegacyBaselineIdentityTest.kt` | 584 → **431** | `LegacyBaselineJsonSupport.kt` 161 (`SchemaSubset` · `MiniJsonReader`, `private class` → `internal class`) |
| `ExpansionDeterminismTest.kt` | 581 → **483** | `ExpansionDeterminismSupport.kt` 112 (15 trợ giúp thành hàm mở rộng của lớp test; `root` → `internal`) |
| `TopStripTest.kt` | 664 → **445** | `TopStripSeatChipTest.kt` 231 (UX5/UX5b chip ghế + `seatStatus`/`one`) |
| `VoiceIntentParserTest.kt` | 568 → **342** | `VoiceIntentParserHarness.kt` 24 (mồi + `one`/`all`/`expect`/`unknown`, dùng CHUNG) · `VoiceIntentParserEverydayTest.kt` 244 (M · L7 · cụm hỏi · số rút gọn · danh từ đầu câu · LOG XE) |
| `LangCoverageTest.kt` | 507 → **455** | `LangCoverageFixtures.kt` 65 (`companion object` → `internal object`) |
| `SimpleCastCoordinatorTest.kt` | 539 → **270** | `SimpleCastCoordinatorHarness.kt` 46 (lớp cha: fake + `@BeforeEach` + `awaitState`/`awaitTrue`) · `SimpleCastCoordinatorProfileTest.kt` 245 (R4/R5/R6 · tỉ lệ sống · autostart · lỗi tự nhả) |
| `LauncherI18nContractTest.kt` | 551 → **429** | `LauncherI18nAllowlists.kt` 141 (`allowed` · `rawLabelAllowed` · `uiLiteralAllowed`; **+2** mục `dải=` · `vùng=` — nhật ký `overlay show` của `CameraOverlayView`, đóng đỏ B3) |
| `gen-car.py` | 882 → **32** | `gencar/__init__.py` 28 · `paths.py` 24 · `pathdata.py` 246 · `model.py` 175 · `icons.py` 170 · `faces.py` 150 · `paint.py` 43 · `cli.py` 106 |
| `stream-matrix.py` | 529 → **476** | `stream_audio.py` 68 |

[ĐO lại sau soát Opus 27/09] `find … -name '*.kt' -o -name '*.py' | xargs wc -l | awk '$1 > 500'` ⇒ **rỗng**. **TÁM** tệp
đứng đúng 500: `KachiTopStrip` · `KachiPalette` · `GroupTileViews` · `ExpansionPackRenderer` · `T10SessionSafetyTest` ·
`gen-icons.py` · **`CameraOverlayView`** · **`TestBridgeCommandTest`** — ≤ 500, không đụng.

⚠ Bản đầu của dòng này (viết lúc L6 đo) nói *bảy* tệp và xếp `CameraOverlayView` ở **499**: hai làn khác cùng phiên đã
đẩy `CameraOverlayView` (L2) và `TestBridgeCommandTest` lên đúng trần SAU lượt đo ấy ⇒ một [ĐO] hết đúng ngay trong
cùng bản (CLAUDE.md §2). Số ở đây là lượt đo lại trên cây làm việc 2.76 (177).

### 2.2 `SysProps` — một cửa reflection
`app/src/main/java/com/byd/clusternav/SysProps.kt`: `object SysProps { fun get(key): String }` = đúng thân của ba bản sao
(`Class.forName` + `getMethod("get")` · lỗi/off-car ⇒ `""`). Ba nơi cũ giữ TÊN hàm cho chỗ gọi, thân uỷ quyền:
`AvmCamera.systemProp(key) = SysProps.get(key)` · `ClusterProfile.getProp(key) = SysProps.get(key)` ·
`SeatComfortApplier.systemProp(key) = SysProps.get(key).takeIf { it.isNotBlank() }` (hợp đồng `null` = không có manh mối, y cũ).
Ở `:app` vì `:core` bị cấm nhắc chữ `android` kể cả trong chuỗi (`LayeringRulesTest.core khong duoc biet Android`);
`LayeringRulesTest.pureButMustStayInApp` +1 mục `SysProps.kt` kèm lý do. Bài canh mới `SysPropsContractTest` (2 bài):
đúng **một** chuỗi tên lớp đầy đủ trong `app/src/main + core/src/main` và nó ở `SysProps.kt`; ba nơi cũ **gọi**
`SysProps.get(` và không còn `Class.forName("…SystemProperties")` riêng.

### 2.3 Danh mục chức năng
`docs/catalog/features.json` **+10 mục** (154 → 164, chỉ thêm — diff 107 dòng cộng, 0 dòng trừ): 4 Camera xi-nhan (R1 · R2 ·
R3 · R4) · 3 Voice (R5 · R6 · R7) · 2 Thanh trên (R8/R9 · R11-1) · 1 Widget Kachi (R11-2/3). Mỗi mục: `desc` = cơ chế thật,
`status` = phần đã đo off-car **và** phần 🚗 chờ xe theo đúng dòng kiểm của lane doc, `evidence` = doc + tệp. Registry
(`core/build/catalog/registry.json`, dump lại bởi `FeatureCatalogDumpTest` trong lượt `:core:test`) **không đổi** số:
33 nút · 66 datum · 2 gói · 3 hành động. Sinh: `python3 scripts/docs/feature-catalog.py --date 2026-09-27`.

## 3. Quyết định (kèm mức bằng chứng)

| # | Quyết định | Vì sao | Mức |
|---|---|---|---|
| Q1 | Tách bằng **hàm mở rộng `internal`**, không phải lớp mới / partial | Kotlin không có partial class; hàm mở rộng cùng package là khuôn repo đã dùng 6 lần; chỗ gọi không đổi | [ĐO khuôn có sẵn] |
| Q2 | `private → internal` cho thành viên bị chạm là **đổi tầm nhìn trong module**, không phải đổi hành vi | không đổi kiểu, khởi tạo, thứ tự; `internal` không lộ ra ngoài module | [SUY] |
| Q3 | Ba bài niêm phong `offcar-planner`: chỉ rút hằng/trợ giúp, **không** dời `@Test` | verifier chạy `--tests <lớp>` theo cổng; dời bài là cổng mất bài mà không ai đỏ | [ĐO `verify-hud-sign-candidate-expansion.sh` GATE-X-O1/O2/O9/O10] |
| Q4 | Ba tệp mới của `offcar-planner` **không** tự đưa vào `SOURCE_SEAL_INPUT` (155 đường dẫn) | authority niêm phong theo revision hash-chained ⇒ revision 3 là việc owner/coordinator, không phải làn nợ mã | [ĐO `offcar-boundary-revisions.json`] |
| Q5 | **Tám** tệp đúng 500 dòng **để nguyên** | luật là "≤ 500"; cắt thêm là tách vì tách | [ĐO wc, đo lại 27/09 sau soát] |
| Q6 | Hai literal `dải=` · `vùng=` của L2 vào `allowed` với lý do "nhật ký `overlay show`" thay vì sửa tệp L2 | tệp L2 thuộc làn khác; hai chuỗi là đúng loại đã có 13 mục "nhật ký" trong bảng | [ĐO thông điệp đỏ B3] |
| Q7 | Không thêm dòng danh mục cho chính L6 | danh mục là chức năng người dùng + status xe; nợ mã không phải chức năng | [SUY] |

## 4. Test

- **Tách thuần được chứng minh bằng**: (a) `gen-car.py --check` **OK 41/41 byte** sau tách [ĐO]; (b) số bài test của
  `:core` **2 946 → 2 946**, `:offcar-planner` **99 → 99** trước/sau tách (không mất, không nhân bài) [ĐO XML]; (c) `:app`
  1 490 → 1 492 (+2 bài `SysPropsContractTest`), đỏ 1 → **0**.
- 21 bài canh đổi mốc sang tệp mới (không nới điều kiện): `AppWidgetWiring` · `CapabilityTileWiring` · `GridSeamGuard` ·
  `KeepStateTheme` · `SettingsScreenWiring` · `TopStripWiring` · `BarOrderWiring` · `ControlHeight` (+bề mặt `AppDrawerTiles`)
  · `TypeScale` (+bề mặt) · `GroupPickerWiring` · `DrawerGridSeam` (+`gridBuilders`) · `PickerCapNotice` ·
  `ClusterNavBridgeCastKeysWiring` · `Goi2FeatureWiring` · `SpeedBadgeLifecycle` · `AlertChipWiring` · `ClusterNavKeys`
  (+`PrefsBadge.kt` vào danh sách tệp `clusternav_prefs`) · `LegacyScreenAbsence` (`T11_HASHES` ở Fixtures) · `IconStyle`
  (bảng `ICONS` ở `gencar/icons.py`) · `LayeringRules` (+`SysProps.kt`) · `LauncherI18n` (+2 mục).
- **Full run** `bash …/gradle-locked.sh :app:testDebugUnitTest :core:test :car-integration:test :vehicle-contracts:test
  :offcar-planner:test :app:lintRelease --rerun-tasks --continue` — đếm từ `*/build/test-results/*/TEST-*.xml` [ĐO]:
  `:app` **1 492 / 0 đỏ** · `:core` **2 946 / 0** · `:car-integration` **64 / 0** · `:vehicle-contracts` **22 / 0** · `:offcar-planner`
  **99 / 0** ⇒ tổng **4 623 bài · 0 fail · 0 error**; `lintRelease`: **0 lỗi (522 cảnh báo có sẵn, không mục nào ở tệp của làn này); XML mới 14:14–14:15, `BUILD SUCCESSFUL`**.

## 5. 🚗 Kiểm trên xe
- Không có phép đo xe nào riêng của L6 (tách thuần + DRY reflection). `SysProps.get` trên xe phải trả đúng như 2.75:
  🚗 `logcat -s KachiCamera` lúc bật camera vẫn in dòng `cam_sort=…` (đường `AvmCamera.systemProp` qua `SysProps`), và
  *Cài đặt › Tiện nghi xe* vẫn hiện **2 ghế** trên Seal (`SeatComfortApplier.isHanModel` đọc `ro.product.*` qua `SysProps`).
- 🚗 của 10 dòng danh mục mới = đúng dòng 🚗 của lane doc L1–L5 (đã chép vào cột `status`).

## 6. Nợ còn lại
- Ba tệp mới của `offcar-planner/src/test` (`ExpansionTransportFenceFixtures` · `LegacyBaselineJsonSupport` ·
  `ExpansionDeterminismSupport`) **chưa** nằm trong `SOURCE_SEAL_INPUT` của `docs/diagnostics/hud-sign-re/offcar-boundary-revisions.json`
  (155 đường dẫn, niêm phong hash-chained). Quyết định của owner: thêm revision 3 hay chấp nhận fixtures ngoài seal.
- **Tám** tệp đứng đúng 500 dòng (danh sách đúng ở §2.1 — gồm `CameraOverlayView.kt` và `core/src/test/.../testbridge/TestBridgeCommandTest.kt`): lần sửa kế tiếp ở bất kỳ tệp nào trong số đó phải **tách trước khi thêm dòng**. Không có bài test nào canh trần cho hai tệp mới vào danh sách (`TestBridgeSafetyContractTest` chỉ quét thư mục testbridge của `:app`) ⇒ danh sách này là hàng rào duy nhất, phải đo lại mỗi phiên.
- `ExpansionTransportFenceFixtures.kt` 487 dòng, trong đó ~400 dòng là nhật ký cập nhật hằng `T11_HASHES` — nếu còn cập nhật
  seal T11 thì nên chuyển nhật ký sang một `.md` cạnh backlog trước khi tệp chạm trần.
