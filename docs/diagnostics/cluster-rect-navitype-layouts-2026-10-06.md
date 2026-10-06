# CLUSTER-RECT-NAVITYPE — có bố cục cụm nào "thấy lớp chiếu mà không có khung ADAS to" không: KHÔNG (fw 2602030, DiLink 3.0)

> **Trạng thái**: Current · **Cập nhật**: 2026-10-06 · **Loại**: Diagnostics (nghiên cứu, chỉ đọc, chưa chạm xe) · **Owner**: dangkhoi ·
> **Câu hỏi**: owner đo trên xe 06/10: *"nếu mình không chiếu cụm, thì màn default của xe chữ nhật, nó đâu có miếng ADAS to vật đấy"*
> ⇒ khung ADAS to gắn với bố cục chiếu (naviType = 4), không gắn với theme chữ nhật. Vậy có bố cục nào Android chạm tới được
> (opcode AutoContainer KHÔNG ghi bền — không 17, không 41) mà **lớp chiếu (z −3) lộ ra** và **nền ADAS to không vẽ**?
> **Liên quan**: `cluster-rect-adas-shrink-2026-10-06.md` (tấm trắng; §1 cách tái lập) · `cluster-rect-seal-2026-10-05.md` ·
> `oncar-2026-10-05-slot-cluster.md` §4 · `oncar-2026-10-06-cluster-rect.md` · `ProjectionRecipe.FORBIDDEN_OPS`

Mức bằng chứng (CLAUDE.md §2 / `conversation-protocol.md`): **[ĐO nguồn …]** = đọc QML / PNG / disasm firmware thật, kèm `file:dòng`
hoặc địa chỉ · **[ĐO xe]** · **[ĐO hiện trường DashCast]** = log/README DashCast đo trên Seal EU · **[SUY]** · **[ĐOÁN]** · **[CHƯA BIẾT]**.

## 0. Kết luận

**KHÔNG có.** Trên firmware 2602030 (Seal, DiLink 3.0), không bố cục nào Android tạo được bằng opcode không ghi bền mà vừa thấy lớp
chiếu vừa không có nền ADAS to. Ba mắt xích, đều [ĐO nguồn]:

1. **Lớp chiếu chỉ lộ qua lỗ trong ảnh nền**, và chỉ hai ảnh nền có lỗ: `navi_full_bg` (naviType **4** FULL) và `navi_small_bg`
   (naviType **3** SMALL). Ở naviType 0/1/2 nền là `bg.png` hoặc ảnh địa hình, **đục 100 %**, nên lớp chiếu bị che kín (§2, §3).
2. **Từ Android chỉ tạo được naviType ∈ {0, 2, 4}**: opcode 16 → 4, 39 → 2, 18/42 → 0. Giá trị 3 chỉ do `updateNaviType()` sinh ra,
   mà hàm này **thoát ngay khi `g_productIndex == 1` (DiLink3.0)**. Không opcode nào ghi 3 (§4).
3. **Ở naviType 4, theme2 luôn vẽ tấm ADAS** khi xe ON. Lúc đó `adasInterfaceDisplay` luôn là 1 hoặc 2 (không bao giờ 0). Tấm đang
   hiện trên xe [ĐO xe 05–06/10], nên trên Seal hoặc `adasType == 0` (nhánh thường), hoặc nhánh 3R1V với `adasWindow ≠ 0`; nhánh nào
   cũng do CAN quyết. Mọi đầu vào của điều kiện vẽ tấm đều do CAN hoặc cấu hình xe ghi; các opcode chạm tới chúng chỉ ghi giá trị vô
   ích (41 ghi 466 = 2 kèm ghi bền; 53 và 41 ghi `adasType` = 0) (§5).

⇒ Quan sát của owner **đúng về cơ chế**: tấm ADAS thuộc bố cục FULL (naviType 4), không thuộc theme chữ nhật. Nhưng **lỗ chiếu chữ nhật
cũng chỉ có trong chính bố cục đó**. Hai thứ dính nhau trong cùng một trạng thái QML, nên Android không tách được. Màn "mặc định không
tấm" owner thấy là naviType 0: đó là giá trị lúc cụm khởi động, và cũng là thứ bước tắt chiếu của Kachi (`SEAL_TEARDOWN = [18, 0]`) trả
về. Ở trạng thái đó lớp chiếu có thể vẫn đang nạp (sau một lượt chiếu), nhưng bị nền đục che kín. Lối ra thật vẫn như `cluster-rect-adas-shrink` §6–7: **Bo tròn (theme1)** thì không có tấm nhưng màn cong; hoặc **vá rcc khi có
root**, hoặc chờ **firmware mới** đổi luật QML.

## 1. Tái lập (bổ sung §1 của `cluster-rect-adas-shrink-2026-10-06.md`)

Binaries là bản 2602030 dump từ `../firmware/fw-2602-diff/new_out/system.img`, đã đối chiếu sha256 với bản được phân tích:
`libBydDataSource.so` `1bda5155…`, `libBydCluster.so` (= `cmp/libBydCluster_NEW.so`) `3197abee…`, `cluster_theme2.rcc` `42940e7c…`,
`cluster_theme1.rcc` `1096a440…`. Đã đối chiếu thêm `libBydDataSource.so` 2511080 cho các mốc chính ở §4 (cổng `g_productIndex`, ba
opcode ghi naviType yêu cầu).

- **QML chính và hằng z không nằm trong rcc theme** mà nhúng sẵn trong `libBydCluster.so`. `qInitResources_common` @0x78840 khai
  cây 0xe0de8, tên 0xe213e, dữ liệu 0xe4628 (rcc v3, 22 byte/nút) ⇒ ra `/qml/main.qml` và `/common/singleton/CustomStyle.qml`.
- **Enum Qt (moc)**: tìm mảng `QByteArrayData` (24 byte/mục; mục 0 = `DataSourceManager` @0x11d82f8) → lấy chỉ số chuỗi → tìm các cặp
  (khoá, giá trị) trong `qt_meta_data`.
- **Bảng nhảy**: `DataSourceManager::handleDataItemChanged` @0x78adc (int32 tương đối @0x119a9b0, 0x242 mục, chỉ số = id data item) ·
  `BusinessUi1::clusterDebug` @0x14610c (uint16 @0x3875c, gốc 0x1461d8, 212 mục) · `BusinessUi1::msgDataUpdate` @0x14a190
  (uint16 @0x38d8a, gốc 0x14a1ec, msg 17..46).
- **Độ trong suốt PNG**: dùng PIL đếm điểm có alpha < 8, < 128 và < 250.
- Cụm chạy ở host: `/system/etc/init/fission_cluster.fission_host.rc` khai dịch vụ `byd_demo_dual` chạy trình
  `qtandroidnative` (cấu hình panel 5/15), trình này nạp `libBydCluster.so` [ĐO fw; diễn lại bằng lời, không chép nguyên văn].

Script giải mã để ở scratch của phiên, không đưa vào repo.

## 2. Lớp chiếu và nền: cái gì che cái gì (theme2 `qml10_25`)

| # | Sự thật | Mức |
|---|---|---|
| L1 | Thứ tự z: `bgZ = −2`, `presentationZ = −3`, `adasZ = 99`, `warningLightZ = 100` | [ĐO nguồn `common/singleton/CustomStyle.qml:8-12`, nhúng trong libBydCluster] |
| L2 | Lớp chiếu là `AndroidNativeWindowItem` có `disp_name "xdjaVirtualSurface"`, 1920×720, z −3, **luôn hiện (cố định)** (cổng theo naviType đã bị comment) | [ĐO nguồn `alwaysDisplay/Presentation.qml:6-19`] |
| L3 | Lớp chiếu chỉ được nạp khi dilinkName = 2; không có điều kiện nào khác | [ĐO nguồn `cluster.qml:2116-2121`] |
| L4 | `AndroidNativeWindowItem` là `QQuickPaintedItem` vẽ khung hình của VD vào cảnh Qt. Nó không gọi `DataSourceManager`, nên không tự ẩn/hiện theo naviType | [ĐO disasm libBydCluster 0x9fb40–0xa0db0] |
| L5 | Nền `bgImageContainerId` (z −2) phủ toàn màn. Cổng `presentationTestMode` của nó đã bị comment. Nền chỉ bị ẩn **tạm thời** trong hoạt cảnh đổi kiểu đồng hồ: `THEME_SWITCH_REQUEST_TO_PLAY_SWITCH_ANIM` (= 7) ẩn, `…TO_PLAY_DISP_ANIM` (= 10) hiện lại | [ĐO nguồn `cluster.qml:631-643`, `:324-341`; enum moc] |
| L6 | Ảnh nền do `updateBgImageSource()` chọn theo `powerGear` + `naviType` + địa hình | [ĐO nguồn `cluster.qml:133-192`] |

⇒ Người lái chỉ thấy lớp chiếu ở những chỗ ảnh nền trong suốt và không có phần tử z ≥ 0 nào đè lên.

## 3. Bảng naviType × bố cục (theme2)

Enum `NaviType` [ĐO moc libBydCluster, cặp khoá/giá trị ở offset tệp 0x11a8790]: `INVALID 0 · OFF 1 · EASY 2 · SMALL_SCREEN 3 ·
FULL_SCREEN 4`. Data item **67** = `naviType` (`m_naviType` @+0x164); item 68 = `naviTypeStore` [ĐO disasm bảng nhảy 0x119a9b0:
67 → 0x79650, 68 → 0x79674].

| naviType | Android tạo được? | Ảnh nền (xe ON) | Thấy lớp chiếu? | Nền ADAS to (658×396 @1256,225) | Ô ADAS nhỏ (305×260 @1266.5,392) | Tốc độ |
|---|---|---|---|---|---|---|
| 0 INVALID | Có: opcode 18, 42, hoặc khi QML nạp lại | `bg.png` hoặc ảnh địa hình — **đục 100 %** | **Không** | Không (cần naviType 4) | Có khi `adasInterfaceDisplay == 2` | Đồng hồ gốc (`updateDashBoardUi`) |
| 1 OFF | **Không**: không nơi nào ghi giá trị 1 | như 0 | Không | Không | như 0 | như 0 |
| 2 EASY | Có: opcode **39** | như 0 — đục | **Không** | Không | như 0 | Đồng hồ gốc; cụm tự vẽ dẫn đường giản lược nếu IVI gửi dữ liệu (`Center.qml:208,335`) |
| 3 SMALL | **Không trên DiLink3.0** (§4) | `navi_small_bg.png`: lỗ giữa α<8 (666,188)-(1261,519), α<128 (619,130)-(1308,563) | Có (ô giữa ~600×330) | **Không** | Có khi = 2 | Đồng hồ gốc **vẫn còn** |
| 4 FULL | Có: opcode **16** (kèm bắt tay hoạt cảnh) | `navi_full_bg.png`: α<8 (185,139)-(1730,548), α<128 (0,115)-(1920,572) | **Có** (chữ nhật trọn ngang) | **Có** khi ON, `adasInterfaceDisplay ≠ 0` và `adasType == 0` (nhánh không phải 3R1V) | Có khi = 2 — nằm **trong** tấm | Đồng hồ gốc bị huỷ; chỉ còn `naviPowerId` (kW) và `naviSpeedId` không toạ độ (chữ "km/h" lạc góc) |

Nguồn từng ô [ĐO nguồn `cluster.qml`]: ảnh nền `:135-191`; đồng hồ `:106-132` (FULL `return` sớm ở `:116-118`); tấm ADAS `:47-48`
(đè lên khai báo ở `:2026`); ô nhỏ `:45-46`, `:2013-2021`, nội dung `Adas.qml` ở `:2029-2062`; trang ADAS lớn dời +585 vào trong tấm
khi FULL ở `Center.qml:226-235,275-276`; `naviPowerId`/`naviSpeedId` ở `:1938-2010`. Độ trong suốt [ĐO PNG]: `display_always/{day,night}/bg.png`
và mọi `terrainMode/{day,night}/*_mode.png` đều không có điểm nào alpha < 250.

## 4. Ai ghi naviType — toàn bộ chuỗi

### 4.1 Android chỉ chạm được `clusterDebug(op)`
- [ĐO disasm libBydCluster `DataSourceManager::receiveData` @0xda6f8] Kiểu 1000 → `sendPluginMsgInt(40 = RECV_MSG_ID_CLUSTER_DEBUG,
  code)` (0xda810–0xda828); kiểu 5 → 43 `IVI_BOOT_COMPLETED`; kiểu khác bị bỏ qua. Bên `libBydDataSource`, `msgDataUpdate` msg 40 gọi
  `clusterDebug(code)` (0x14a3a4). ⇒ Toàn bộ mặt điều khiển của Android là opcode 0..211.
- [ĐO disasm] `m_naviType` chỉ được ghi ở `handleDataItemChanged` (0x79664) và trong hàm dựng (0x7ec3c); `DataSourceManager` không có
  setter cho nó.

### 4.2 Máy trạng thái naviType trong `libBydDataSource.so`
- `+0x1dc` = naviType **được yêu cầu**; item 67 = naviType **đang hiện**, được ghi qua wrapper cache ở `+0x1e8` (`BusinessUi1::Init`
  0xff678–0xff680).
- **Người ghi `+0x1dc`** — danh sách đủ, đã quét mọi `strb` và mọi lệnh lưu rộng có thể chồng lên offset 0x1dc:

| Ghi | Giá trị | Nguồn |
|---|---|---|
| opcode **16** @0x146844 | 4 | [ĐO disasm] |
| opcode **39** @0x146450 → 0x146848 | 2 | [ĐO disasm] |
| opcode **18** @0x1462c0 | 0 | [ĐO disasm] |
| opcode **42**, đoạn cuối @0x146f20 | 0 | [ĐO disasm] |
| `updateNaviType()` @0x1299f8 / 0x129a74 | 0/2/3/4, tính từ CAN | [ĐO disasm] — **thoát ngay nếu `g_productIndex == 1`** (0x1298a8) |

  Cả bốn opcode đều gọi `updateNaviDisplay()` (0x146850, 0x146f24). Không opcode nào ghi 3.
- **Người ghi item 67** (đều qua `+0x1e8`):
  - `updateNaviDisplay()` @0x129b2c ghi thẳng 3 (0x129ce0), 2 (0x129d2c), 0 (0x129d74).
  - **Riêng FULL (4) không ghi thẳng**: hàm gửi `PluginMsgManager::SendIntMsg(15 = SEND_MSG_ID_NAVI_FULL_SWITCH_REQUEST, 1)`
    (0x129b84–0x129b9c). Rời FULL cũng đi đường đó với `SendIntMsg(15, 0)`.
  - `msgDataUpdate` msg 17 `QML_LOAD_COMPLETE` → 0 (0x14a24c, khi cờ `+0x169` bật).
  - `msgDataUpdate` msg 41 `NAVI_FULL_SWITCH_ANIM`: giá trị 1 → **item 67 = `+0x1dc`** (0x14a65c–0x14a668); giá trị 2 → kết thúc hoạt
    cảnh, gọi `updateThemeIndex(1)` rồi `updateNaviType()` (0x14b1e8–0x14b20c).
- **Bắt tay với QML** [ĐO nguồn `main.qml:256-265,279-286,369-381`, nhúng trong libBydCluster]:
  1. QML nhận msg 15 → `rootItemId.grabToImage`, ẩn cây theme và hiện ảnh vừa chụp.
  2. QML gửi msg 41 với `START` → naviType đổi.
  3. Cây theme hiện lại, mờ dần vào trong 500 ms; xong thì gửi msg 41 với `COMPLETE`.

  Mã message theo enum moc: SEND 15 `NAVI_FULL_SWITCH_REQUEST`; RECV 17 `QML_LOAD_COMPLETE`, 39 `NAVI_TYPE`, 40 `CLUSTER_DEBUG`,
  41 `NAVI_FULL_SWITCH_ANIM`, 42 `MENU_NAVI_TYPE`.
- **Vì sao không tới được 3**: chỉ `updateNaviType()` sinh ra 3 (0x129ab0–0x129ac4), và chỉ khi `+0x1da == 3` **và** CAN
  `0x4C10E015 == 1`. Trên DiLink3.0, hàm này và `updateNaviTypeVoice()` (0x1295f8) đều thoát ngay. Menu cụm (msg 39) chỉ ghi `+0x1da`,
  `SetConfig(53)` và CAN rồi gọi `updateNaviType()`, nên cũng vô tác dụng [ĐO disasm 0x14ac8c–0x14acb8, 0x14af48]. Mục chọn dẫn đường
  trong menu chỉ hiện khi dilinkName = 2 [ĐO nguồn `menu/FirstMenu.qml:41,215`].
- `0x4C10E015` là `BYDAutoFeatureIds.SET_NAVI_SCREEN_STATUS_SET` (nhánh CanFD); `updateNaviType` log nó dưới tên
  `"Navi naviScreenStatuValue=%d…"` (chuỗi 0x321f4) [ĐO framework.jar + chuỗi]. Đây là đường của DiLink4/5: IVI báo cụm "đang chiếu
  nhỏ/lớn" qua CAN. Thiết bị HAL nào phơi setter của nó thì [CHƯA BIẾT]; `BYDAutoInstrumentDevice` không tham chiếu tới [ĐO decompile].
- `+0x1da` (cấu hình bền 53, [SUY] khoá `navi_type`) được ghi bởi: opcode 17 (= 3, kèm CAN `0x40C03032`), opcode 41, menu cụm
  (msg 39), `updateNaviTypeVoice` (DiLink4/5), và lúc nạp cấu hình. Mọi đường đều ghi bền.

### 4.3 `g_productIndex` phía host — tiền đề duy nhất còn ở mức [SUY]
- [ĐO disasm `BydDataSourceModule::Init` 0xf73d8–0xf74e4] Hàm đọc `ro.product.name`: `DiLink3.0` → 1, `DiLink4.0` → 2,
  `DiLink5.0` → 3. **Không khớp thì giữ mặc định 2** (`.data` 0x182008 = 2). `BusinessUi1::Init` đặt item 65 `dilinkName` =
  `g_productIndex` (0xff598–0xff5b0).
- [ĐO xe] Phía IVI: `ro.product.name=DiLink3.0`. [ĐO fw] Cụm ở host chạy từ cùng `/system` (`ro.product.system.name=DiLink3.0`).
- [ĐO hiện trường DashCast, README mục "VirtualDisplay cluster creation — CONFIRMED (03/05/2026)", Seal EU DL3.0] VD cụm **không có
  lúc khởi động**; nó chỉ được tạo ~280 ms **sau `sendInfo(35)`**. Suy ra lúc khởi động `dilinkName ≠ 2`, tức host khớp `DiLink3.0` và
  `g_productIndex == 1` [SUY mạnh].
- F1 của `oncar-2026-10-06` ("VD có từ lúc khởi động", uptime 15 h) không mâu thuẫn với điều trên. Chiếu cụm của Kachi là `16 → 35`,
  tắt chiếu là `SEAL_TEARDOWN = [18, 0]`, không bao giờ gửi 34 ⇒ `dilinkName` giữ 2 và VD sống tiếp sau lượt chiếu đầu tiên trong
  phiên host [SUY mạnh từ `ProjectionRecipe.kt:120-121` + disasm].
- **Kết luận không phụ thuộc tiền đề này.** Kể cả host không phải DiLink3.0, muốn ra 3 vẫn cần đồng thời `config 53 = 3` (ghi bền —
  chỉ có opcode 17/41 hoặc menu cụm) **và** CAN `0x4C10E015 = 1` (ghi CAN qua HAL). Cả hai đều nằm ngoài tập đòn bẩy được phép.

### 4.4 Bảng opcode 2602030 (đủ 212 ô, gom nhóm) — tác động lên bố cục

| Opcode | Đích (2602030) | Ghi bền? | Bố cục |
|---|---|---|---|
| 0 / 1 | item 9 `sleepMode` (1: đặt cờ rồi = 1; 0: trả lại giá trị đã lưu) | Không | Không |
| 2–5 | trạng thái đèn báo (debug) | Không | Không |
| 6 / 7 | `SetConfig(48, 1/2)` + item 4 `dayNightMode` | **Có** | Ảnh ngày/đêm |
| 8 / 9 | `SetConfig(49, 1/2)` + `updateThemeIndex` (kiểu đồng hồ) | **Có** | Kiểu đồng hồ (FULL bỏ qua) |
| 10, 11, 24–28 | item 46 `dashbordTestMode` | Không | Không (theme2 QML không đọc item này) |
| 12/13 · 32/33 · 45/46 · 47/48 | item 47 · 533 · 538 · 549 (ADAS test / 3D / self-study / debug) | Không | Không chạm tấm (§5) |
| 14 / 15 | item 49 `fpsTestMode` = 1 / 0 | Không | Chữ FPS (`main.qml:344-353`) |
| **16** | naviType yêu cầu = 4 | Không | **FULL** (có lỗ chiếu + tấm ADAS) |
| 17 | `+0x1da` = 3, `SetConfig(53, 3)`, CAN `0x40C03032` = 3 | **Có** | Không gì trên DiLink3.0 — **cấm** |
| **18** | naviType yêu cầu = 0 | Không | Thường (nền đục) |
| 19 / 20 | item 50 `osdFrameTestMode` = 1 / 2 | Không | Không |
| 21 / 22 / 23 | item 8 `vehicleType` = 1 / 2 / 3, rồi làm mới các mục năng lượng | Không | Đổi widget năng lượng — không dùng |
| 29 / 30 / 31 | `g_ro_clu_size` = 1 / 3 / 2 → item 63 → `updateThemeRcc()` (nạp lại QML); không có `SetConfig` | Không (RAM host) | Đổi theme ⇒ **VD dựng lại** ⇒ nguy cơ sập nếu còn lớp |
| **34** | item 65 `dilinkName` = 1 | Không | **Gỡ lớp chiếu** (Loader rỗng ⇒ huỷ VD) — chưa ai từng dùng |
| **35** | item 65 `dilinkName` = 2 | Không | **Nạp lớp chiếu** (tạo VD) |
| 36 | `SendMsg(13)` = chụp QML thành tệp `/data/logs` phía host | Ghi tệp ở host | Không |
| 37 / 38 | mức log 0 / 1 | Không | Không |
| **39** | naviType yêu cầu = 2 (EASY) | Không | Thường (nền đục) + dẫn đường giản lược |
| 40, 43, 44, 49–52 | chẩn đoán (dumpinfo, thông tin CPU, làm mới cảnh báo) | Không | Không |
| 41 | chế độ thử đèn (xem §4 tài liệu trước) | **Có** | **Cấm** |
| 42 | thoát chế độ thử: 46 = 0, 533 = 0, 466 = giá trị thật, naviType yêu cầu = 0, 537 = 0, rồi **49 `fpsTestMode` = 1** | Không | Rời FULL **và bật chữ FPS** (phát hiện mới) |
| 53 | `adasType` = 0 | Không | Không giúp |
| 88 / 89 | item 1 `language` = 10 / 18 | Không | Đổi ngôn ngữ |
| **211** | chạy một lệnh shell nền xoá tệp `/collect2/byd_datasource_config.xml` (diễn lại bằng lời) | **Xoá kho cấu hình bền** | **Cấm** |
| 54–87, 90–210 | không làm gì (nhảy thẳng về cuối hàm) | — | — |

[ĐO disasm `clusterDebug` 0x14610c–0x147180, bảng nhảy 0x3875c; tên item lấy theo bảng nhảy của `handleDataItemChanged`.]

## 5. Tấm ADAS: đầu vào và người ghi

Điều kiện [ĐO nguồn `cluster.qml:47-48`]:
(diễn lại bằng lời, không chép nguyên văn) nếu adasPlatform = 1 thì tấm hiện khi naviType = 4, powerGear = 3 và adasWindow ≠ 0;
ngược lại thì khi naviType = 4, powerGear = 3, adasInterfaceDisplay ≠ 0 và adasType = 0.
Không chỗ nào khác chạm tới `adas2dNaviWindowBg` [ĐO grep toàn bộ theme2].

| Đầu vào | Item | Người ghi (đủ) | Android ghi được mà không bền? |
|---|---|---|---|
| naviType | 67 | §4 | Phải là 4 thì mới có lỗ chiếu |
| powerGear | 0 | CAN | Không; nếu ≠ 3 thì nền thành `IMG_000.ktx` (đục) |
| adasInterfaceDisplay | 466 | `updateAdasInterfaceDisplayDataItem` (CAN; luôn 1/2 khi ON); op 41 (= 2); op 42 (= giá trị thật) | Không tạo ra 0 |
| adasType | 467 | `adasSelfStudy` = CAN `0x43200021` khi CAN `0x43200000 == 1` (0x12c884–0x12c8d0); op 53 (= 0, 0x1464f4); op 41 (= `g_productIndex ≠ 1`, tức 0 trên DL3.0, 0x146e94–0x146ea0) | Không tạo ra ≠ 0 |
| adasPlatform | 552 | `switchAdasPlatform`, tính từ mã xe `+0x103a` = CAN `0x3D90103A` (ghi trong `canDataUpdate` 0x1489cc; Seal = 0x8a = 138) | Không |
| adasWindow (3R1V) | 567 | `updateAdasWindow3R1V`, tính từ CAN (0x12D0002A, 0x26F00028, 0x23500008, 0x26F00000, 0x23500000) | Không |

## 6. So với theme1 (Bo tròn, thư mục `qml/`)
- Điều kiện vẽ tấm (diễn lại bằng lời): naviType = 4, adasInterfaceDisplay = 1, powerGear = 3 và adasType = 0 [ĐO nguồn
  `qml/cluster.qml:47-48`, khai báo ở `:1917-1924`]. Tức chỉ khi đang **trang ADAS lớn**; theme2 thì dùng «≠ 0», nên trang nhỏ cũng vẽ.
- Khi naviType yêu cầu ∈ {3, 4}, `requestToShowAdasWindow(n)` xin trang lớn rồi hẹn `n` giây sau xin trang nhỏ (0x12c458–0x12c4cc).
  Ở theme1 tấm tự biến mất; ở theme2 tấm đứng yên, chỉ nội dung bên trong đổi (khớp F6 của `oncar-2026-10-06`).
- Hình lỗ: theme1 là thấu kính, α<8 (178,195)-(1763,488), α<128 (77,158)-(1849,533); tấm 563×353 @ (1256,225). Theme2 là chữ nhật
  trọn ngang, tấm 658×396 [ĐO PNG]. `bg.png` của theme1 cũng đục.
- Lớp chiếu, giá trị z và cổng dilinkName = 2 giống hệt theme2 (`qml/cluster.qml:2014-2016`, `qml/alwaysDisplay/Presentation.qml`).
  `naviSpeedId` của theme1 có toạ độ (x 1501), nên km/h nằm ngay dưới thấu kính.

⇒ Bo tròn "không có tấm" là nhờ **luật QML khác** («= 1») cộng với việc cụm tự xin trang nhỏ, chứ không phải vì dùng giá trị naviType
khác.

## 7. Ứng viên — không cái nào đạt; xếp theo độ an toàn

| # | Bố cục | Chuỗi opcode | Thấy chiếu | Tấm ADAS | Ghi bền | VD dựng lại | Rủi ro sập | Kết luận |
|---|---|---|---|---|---|---|---|---|
| A | theme2 thường / EASY | 18 hoặc 39 | **Không** (nền đục) | Không | Không | Không | Thấp | Chính là màn owner thấy — nhưng không có chiếu |
| B | theme1 FULL (Bo tròn — mặc định của Kachi) | 30 → 16 → 35 | Có (thấu kính) | Chỉ khi trang lớn; tự thu nhỏ | Không | Có, khi đổi theme | Chỉ đổi theme khi VD trống (`CLUSTER-THEME-SAFE`) | Đạt "không tấm", **không** đạt "chữ nhật" — [ĐO xe 05/10] |
| C | theme2 FULL + đặt app trong (50,128)-(1285,555) | 31 → 16 → 35 | Có, chữ nhật | **Có** (chỉ né, không bỏ được) | Không | Có, khi đổi theme | như B | Owner đã bỏ (F9) |
| D | theme2 SMALL | không có đường | Có (ô giữa ~600×330, còn đồng hồ gốc) | Không | — | — | — | **Không tới được** trên DiLink3.0; host loại khác thì cần 17 (cấm) + ghi CAN |
| E | 8.8" (29) | 29 → 16 → 35 | Thu nhỏ về góc trái | Có (thu nhỏ theo) | Không | Có | như B | Vô ích |
| F | Vá `cluster_theme2.rcc` («≠ 0» → «= 1») | cần root | Có | Không | Có (tệp hệ thống) | — | P0: cụm trắng nếu QML lỗi | Lối thật duy nhất (L8 ở tài liệu trước) |

## 8. Đo trên xe (tuỳ chọn, ≤ 3 bước) — đưa ô naviType 2 lên [ĐO xe]

Không có ứng viên đạt yêu cầu, nên không có bài thử kiểu "làm cho chạy". Bài dưới chỉ đóng ô cuối cùng tới được mà chưa ai nhìn trên
xe (EASY), để xác nhận "naviType ≠ 4 ⇒ không thấy lớp chiếu".
- An toàn: không đổi theme, không đổi `dilinkName` ⇒ không dựng lại VD [ĐO nguồn L3]; không ghi bền [ĐO disasm op 39].
- Điều kiện: xe đỗ, số P. Dừng ngay ở bất thường đầu tiên (memory `kachi-feedback-oncar-stop-at-first-crash`). `<A>` = adb qua cầu nc.
- Trạng thái đầu: cụm đang Chữ nhật FULL, Kachi đang chiếu (tấm ADAS đang hiện).

1. Ghi mốc: `<A> shell "pidof system_server surfaceflinger; dumpsys display | grep -c fission_bg_xdjaVirtualSurface"`; chụp cụm bằng
   `<A> shell "fission_screencap -d 0 -p /data/local/tmp/nt0.png"` rồi `pull`.
2. `<A> shell 'service call AutoContainer 2 i32 1000 i32 39 s16 ""'`, chờ 2 s, chụp `nt39.png`. Kỳ vọng [ĐO nguồn]: cụm mờ chuyển
   (~0,5 s) về đồng hồ chữ nhật thường, **không có tấm**, **không thấy app chiếu** (app vẫn chạy trên VD).
3. `<A> shell 'service call AutoContainer 2 i32 1000 i32 16 s16 ""'`, chờ 2 s, chụp `nt16.png`, chạy lại lệnh mốc của bước 1.
   Kỳ vọng: về lại FULL (lỗ chữ nhật + tấm); pid và số VD **không đổi**.

Dừng ngay nếu: pid đổi, service `AutoContainer` biến mất, hoặc cụm đen quá 5 s. Cũng dừng nếu ở bước 2 **thấy app chiếu mà không có
tấm**: khi đó §2–§3 sai trên xe — ghi lại và mở lại nghiên cứu (và đó cũng chính là điều owner muốn). Nếu bước 3 không về FULL: gửi lại
16 một lần; vẫn không được thì tắt chiếu Kachi theo cách bình thường.

## 9. Hệ quả cho mã (đề xuất — chưa làm, cần owner duyệt)
- `ProjectionRecipe.FORBIDDEN_OPS` hiện là `{17, 41}` (`ProjectionRecipe.kt:104`). Chuỗi opcode trong hồ sơ là dữ liệu chia sẻ không tin
  cậy (`:37`), nên nên chặn thêm:
  - **211**: xoá kho cấu hình bền `/collect2/byd_datasource_config.xml`.
  - **6/7/8/9**: ghi bền cấu hình 48/49.
  - **34**: gỡ lớp chiếu ⇒ huỷ VD — cùng loại rủi ro với đổi theme khi còn lớp.
  - **42**: bật `fpsTestMode`.
  - **211, 6–9** chắc chắn nên cấm; **34, 42** thì hoặc cấm, hoặc cho qua cổng giống `ThemeGate`.
- Trên fw 2602030 không có tính năng "chữ nhật không tấm ADAS" nào để viết mã.
- Mỗi lần có OTA, kiểm lại: `qml10_25/cluster.qml:47-48` (luật vẽ tấm) và `updateNaviType` (cổng `g_productIndex == 1`).

## 10. Còn mở / chưa biết
- [CHƯA BIẾT] Giá trị `g_productIndex` thật ở host. Hiện ở mức [SUY mạnh] = 1 (§4.3); kết luận không phụ thuộc vào nó.
- [CHƯA BIẾT] EASY trên Seal hiện gì ngoài đồng hồ; điều đó phụ thuộc dữ liệu dẫn đường mà IVI gửi (`mapSendStatus`, `naviState`).
- [CHƯA BIẾT] HAL nào phơi `SET_NAVI_SCREEN_STATUS_SET`; chỉ có ý nghĩa nếu host không phải DiLink3.0.
- [CHƯA BIẾT] `ResetDataItems()` (msg 17) có trả `dilinkName` về giá trị cũ không. F4 ngày 06/10 (VD dựng lại sau opcode 31) gợi ý là
  vẫn giữ 2 [SUY].
