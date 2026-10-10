# Seal 10/10 — R18 kéo Waze về ô · bơm biển dự báo 0x237 (biển XÁM) · nghe CAN 0 khung

> **Trạng thái**: Evidence · **Cập nhật**: 2026-10-10 · **Chủ**: dangkhoi · **Xe**: Seal, DiLink3, fw 2606, Kachi 2.98 (201) bản OTA
> **Liên quan**: backlog `SLOT-ESCAPE-VD-RETURN-ONCAR` · `ISA-NATIVE-SIGN` · `CAN-NAV-0X43F-LISTEN` · `HUD-ROADNAME-OEM-CADENCE` ·
> kế hoạch `docs/_handoff/oncar-plan-2.99.md` · chuẩn bị `docs/diagnostics/oncar-prep-2026-10-09-isa-can-odo.md` §B1/§B2 ·
> buổi trước `docs/diagnostics/oncar-seal-2026-10-09-hud-isa-power.md`. Script: `scripts/vehicle/isa-0x237-inject-2606.sh` ·
> `scripts/vehicle/can-0x43f-listen-2606.sh` (cả hai đã sửa sau buổi này — §5). Log xe (có dữ liệu xe) không lưu vào repo.

## 0. Tóm tắt

| Mục | Kết quả | Mức |
|---|---|---|
| R18 (kéo app thoát ô về lại màn ảo ô) | Waze thoát ô 2 lần ⇒ `esc moved` 2 lần, không NPE / `createTaskSnapshot`, không cầu chì; owner *"waze OK"* | [ĐO kachi-logs + owner] |
| Bơm `0x2370002E` = 11 qua TestDevice | Cụm hiện biển **XÁM "50"** đúng ô gốc; **HUD không hiện** | [ĐO owner nhìn] |
| Biển xám là gì | Ô "đèn dự báo giới hạn tốc độ" (限速预告指示灯), ảnh `speed_limit_forecast.png` xám — KHÁC widget biển viền đỏ TSR (khung 0x2B4) | [ĐO QML trong rcc fw 2606] |
| Nghe CAN `canmon` 0x235/0x237/0x43F | 0 khung (kể cả 0x237 đang online) ⇒ lỗi công cụ; bảng sai khuôn so với CanDataCollect | [ĐO xe] + [ĐO nguồn] |
| Lượt nghe 600 s | Bị watchdog navopen cắt ở ~120 s TRƯỚC khi xoá bảng ⇒ bảng còn trong MCU; đã dọn 18:12:41 | [ĐO xe] |

## 1. R18 — Waze thoát ô được kéo về ô

- Owner bật công tắc *Kéo app thoát ô về lại ô (thử nghiệm)* trên 2.98 OTA (*"có bật R18"*) [ĐO owner].
- `kachi-logs` [ĐO]:
  - `14:07:17 esc moved com.waze 126 15 5 967`
  - `14:07:49 esc moved com.waze 126 16 5 250`
  - Dòng `esc off` duy nhất là `esc off client-gone` — Kachi bị BYD giết lúc tắt máy (hành vi firmware đã biết, memory `kachi-car-facts-0929`), không phải R18 tự rút.
  - Không có `NullPointerException` · `createTaskSnapshot` · `kachi_escape_return_trip` (cầu chì) trong log.
- Owner: *"waze OK"* [ĐO owner].
- Display 0: `mStable=[0,84][1920,990]` [ĐO dumpsys] — tham khảo cho khung thanh trạng thái/thanh dưới BYD.
- Kết luận: tầng 1 (CLAUDE.md §14) của `SLOT-ESCAPE-VD-RETURN-ONCAR` xanh với Waze trên DiLink3 fw 2606 — `moveStackToDisplay` stack fullscreen → màn ảo ô không ném NPE như vụ CarPlay → cụm 08-01 [ĐO, 2 lượt, 1 app]. Chưa thử app khác (YouTube/VietMap/GMaps) với R18 bật [CHƯA BIẾT].

## 2. Bơm biển dự báo `0x2370002E` (TestDevice `0xAA00020F`)

Điều kiện: xe ĐỖ, owner duyệt tại chỗ. Lệnh thô: `navopen setbytes test AA00020F <hex>` (TEST_SIMULATE_DOWN).

| Giờ | Khung gửi | Kết quả | Mức |
|---|---|---|---|
| ~17:5x | `23,70,00,2E,01,0B` (có dấu phẩy, bản script cũ) | `NumberFormatException` — **không ghi gì** (lỗi script) | [ĐO] |
| 17:54 | `2370002E010B` ×5, rc=0 | Cụm hiện **biển XÁM "50"** ở ô gốc của cụm; **HUD không hiện gì** | [ĐO owner] |
| 17:55:37 | `2370002E0100` | Trả (mã 0 = ẩn) | [ĐO] |
| 18:04 | `2370000001` (5 byte, thiếu byte dữ liệu — gõ nhầm của điều phối) | Biển **biến mất** (khung hỏng làm online 0x23700000 ≠ 1 [SUY]) | [ĐO owner] |
| 18:0x | `237000000101` (online = 1) | Biển lên lại — owner *"lên lại rồi, xám"*. Giá trị gốc đọc trước đó của `0x23700000` = 1 | [ĐO owner] |
| 18:0x | `2370002A0101` | Cụm hiện thông báo *"theo sau 1 phương tiện ở làn kế bên"* — tín hiệu ADAS khác, KHÔNG phải màu biển | [ĐO owner] |
| 18:06:26 | `2370002A0100` + `2370002E0100` | Trả cả hai; dừng thử 0x2370002A/2D | [ĐO] |

Quan sát owner khi lái bình thường: biển thật hiện **viền đỏ** trên cả cụm và HUD [ĐO owner]. Owner **chưa bao giờ** thấy xe tự hiện biển xám [ĐO owner 10/10].

### 2.1 Cơ chế (RE fw 2606, chỉ đọc)

- `BusinessUi1::updateSpeedLimitValue` @0x13254c (`lib64_libBydDataSource.so`) chỉ đọc `0x2370002E` + `0x23700000`; ghi data-item 587 (status: 1 = hiện, 2 = ẩn) và 588 (giá trị) [ĐO disasm]. Bảng mã giữ như prep §B1 (11 ⇒ 50 km/h).
- QML cụm (Qt rcc dạng nguồn, không biên dịch): `firmware/fw-2606/_libs/lib64_cluster_theme1.rcc` → `qml/WarinLightLayout.qml:1006-1030` (theme2: `qml10_25/WarinLightLayout.qml:953-977`) [ĐO]:
  ```
  //交通标志识别 限速预告指示灯
  WarningLight { id: speedLimitWarningLight; displayState: DataSource.speedLimitStatus
    Image { visible: DataSource.speedLimitStatus === 0x1; source: "qrc:/images/adas2d/speed_limit_forecast.png" }
    Text  { visible: DataSource.speedLimitStatus === 0x1; text: DataSource.speedLimitValue; color: "#000000" } }
  ```
  `speed_limit_forecast.png` = vòng + nền xám (đã mở ảnh) [ĐO]. ⇒ biển xám 17:54 đúng là ô này, vẽ đúng thiết kế.
- Biển **viền đỏ** là widget KHÁC — TSR (交通标志识别), `WarinLightLayout.qml:937-1003`: `displayState: DataSource.limitTrafficSignRecognition`, số `limitTrafficSymbolValue`, ảnh theo `trafficSignType` (1,5 ⇒ `trafic_border.png` viền đỏ; 2 `tsr_fault`; 3 `tsr_off`; 4 `tsr_noSpeed`; 6,7 `tsr_border_yellow`; 8 `release_speed_limit`; 5/6 thêm "?") [ĐO QML]. Ghi bởi `BusinessUi1::updateTrafficSymbol` @0x131f40 từ khung **0x2B4** (`0x2B40000D/20/16/10/00/3D`) + `0x12D0002A` + `0x1ED00010` [ĐO disasm]. 0x2B4 là khung camera/ADAS [SUY — tên thuộc tính + cùng hàm gọi `AdasWarningInfo`]. Ghép item 495/497/583 ↔ thuộc tính QML từng cái [SUY].
- `WarningLight::onDisplayStateChanged` @0xb6cd0 (`lib64_libBydCluster.so`, bảng nhảy 0x11a093c): 0 ẩn · 1 hiện tĩnh · 2 ẩn · 3–8 hiện nhấp nháy · >8 không đổi [ĐO disasm]. Datasource chỉ ghi 1/2 ⇒ biển xám chỉ có hiện/ẩn.
- TEST_SIMULATE_DOWN chỉ nuôi datasource cục bộ của cụm, không ra bus CAN ⇒ HUD (ECU riêng trên CAN) không thấy [SUY mạnh: khớp hiện tượng HUD trống + đường TestDevice không qua MCU→CAN trong nguồn đã đọc; chưa nghe được bus để chốt vì §3].

### 2.2 Ý nghĩa xám/đỏ

- Xám = **dự báo** giới hạn tốc độ (nguồn 0x237); đỏ = biển đang hiệu lực do camera nhận dạng (nguồn 0x2B4) [SUY mạnh — tên tài nguyên + chú thích OEM].
- Ghi chú cũ "xám = SLA theo bản đồ" chỉ là [SUY]; nguồn dữ liệu của 0x237 (bản đồ hay không) [CHƯA BIẾT] vì chưa biết ECU phát 0x237.
- Owner chưa từng thấy biển xám ⇒ nghi xe không bao giờ phát 2..30 vào `0x2370002E` (tính năng dự báo không được nuôi) [ĐOÁN]. Cách chốt: đọc định kỳ `0x2370002E` (cầu kiểm thử `hal getid`, chỉ đọc) khi chạy qua biển thật; luôn 0/1 ⇒ xác nhận.
- Hệ quả cho `ISA-NATIVE-SIGN`: bơm `0x2370002E` chỉ dựng được **biển xám dự báo, chỉ trên cụm** — không ra biển đỏ, không ra HUD [SUY từ [ĐO] trên]. Biển đỏ phải qua các mục TSR của `updateTrafficSymbol` (0x2B4); cổng/mã giá trị của hàm đó chưa phân tích [CHƯA BIẾT]. Đường bơm vẫn là TestDevice (công cụ test, không phải API sản phẩm) ⇒ không đưa vào app.

## 3. Nghe CAN (`canmon`) — 0 khung

| Giờ | Lượt | Kết quả | Mức |
|---|---|---|---|
| 18:08 | ids `0x235,0x237,0x43F`, 120 s | `frames=0` — kể cả 0x237 đang online ⇒ lỗi công cụ, không phải bus im; `register table cleared` OK | [ĐO] |
| 18:10 | `SECS=600` | navopen **tự giết ở ~120 s** (`watchdogMsFor` trần 120 s + 10 s, `NavOpen.java:63-69`) TRƯỚC dòng xoá bảng ⇒ bảng còn trong MCU | [ĐO] |
| 18:12:41 | canmon 5 s | `register table cleared` ⇒ đã dọn | [ĐO] |

Trong lúc nghe owner chạy qua biển 20 km/h thật; `logcat` 18:06–18:15 không có dòng biển tốc độ nào [ĐO].

Bài học: script cho phép 600 s trong khi công cụ cắt ở 120 s — script nay trần 110 s/lượt và chạy nhiều lượt, mỗi lượt kiểm `register table cleared` (§5).

### 3.1 Vì sao 0 khung — so với CanDataCollect.apk fw 2606 (chỉ đọc)

Khuôn một dòng bảng mà CanDataCollect gửi [ĐO `entity/CanDataHandle.java:224-260` (`collect_id_conf`), `:264-301` (`trigger_id_conf`)]:

```
[canid BE 4 byte][subid (0 nếu 0xFF)][canChanel kênh MCU][mode][op]
```

- `canChanel` = `confCanChanelToMcuCollectChanel(netWorkIndex)` — cấu hình 1→4, 2→5, 3→2, 5→1, khác→0 [ĐO `JsonDataHandel.java:42-53`]. `netWorkIndex` của từng id lấy từ `/collect2/dataCollect/datacollectioncfg` / `canConfigureJson`.
- `mode` = **2** cho id thu chu kỳ/lỗi (`JsonDataHandel.java:133,263`), **1** cho id kích sự kiện (`:179`). CanDataCollect **không bao giờ** gửi mode 0.
- `op` (byte cuối) = tham số `i` của `notify_hal_collect_id_conf` = **1** (`CanDataCollectService.java:287,1186,1294`); trường `flag` của `CanConfigInfo` không được dùng. Bảng xoá `{00 00 03 D5 00 03 00 03}` = op **3** (khi op 3 chỉ gửi dòng đầu — `CanDataHandle.java:253`).
- CanDataCollect gửi bảng thu và bảng kích bằng **hai** lần `sendRegisterTable` liên tiếp ⇒ MCU **cộng dồn** chứ không thay bảng [SUY].
- Khung nhận: `[canid BE4][subid][canChanel][data 0..64]` (`CanDataCollectService.java:1894-1913`) — khớp `NavCanListener`.

navopen `buildRegTable` gửi `[canid BE4][00][00][00][01]` (`NavOpen.java:635-648`) ⇒ **mode = 0** (giá trị CanDataCollect không dùng) và **kênh 0** cố định [ĐO]. Đây là nghi phạm số 1 [SUY mạnh].

Các nghi phạm khác (đều không lộ trong log hiện tại):
- `NavOpen.canmon` **bỏ qua mã trả** của `sendRegisterTable` (int) và in "sent"/"cleared" chỉ vì không ném lỗi (`NavOpen.java:606,616`) ⇒ "cleared" chỉ chứng minh không có exception, KHÔNG chứng minh MCU nhận [ĐO nguồn].
- `AbsBYDAutoDevice.registerListener(l, ids)` **lặng lẽ không làm gì** nếu `checkDeviceFeatures` sai hoặc `mDeviceManager` null (`AbsBYDAutoDevice.java:130-145`); navopen in "listener registered" vô điều kiện [ĐO nguồn]. `0x99000020` CÓ trong bản đồ tính năng của device 1061 (`BYDAutoDeviceFeaturesMap.java:30`) ⇒ nhánh `checkDeviceFeatures` qua được [ĐO nguồn].
- Quyền: `BYDAUTO_BIGDATA_GET` + `BYDAUTO_VEHICLE_DATA_SET` khai ở `AutoPermission.apk` với protectionLevel `signature` (0x2) [ĐO manifest]; `Shell.apk` không xin quyền BYDAUTO nào [ĐO manifest]. Nhưng lượt 18:08 không ném `SecurityException` (đi tới "cleared") ⇒ kiểm tra phía Java qua được trong ngữ cảnh `systemContext()` của navopen [ĐO xe]; cơ chế vì sao qua [CHƯA BIẾT]. Đường sự kiện về tiến trình là native (`BYDAutoManager.nativeEnableDevice/nativeEnableObserver`) — native có lọc uid shell hay không [CHƯA BIẾT]. `mForbidCall` chỉ chặn theo tên gói trong danh sách đen chiến lược (`BYDAutoManager.java:113-128`).

### 3.2 Bảng sửa cho 0x235 / 0x237 / 0x43F

Mỗi dòng: `[00 00 0X XX][00][CH][02][01]` — subid 0, mode 2, op 1. Kênh `CH` của ba id trên Seal [CHƯA BIẾT] ⇒ hoặc đọc đúng từ máy (khuyến nghị), hoặc gửi đủ 5 kênh MCU hợp lệ {0,1,2,4,5}:

```
00 00 02 35 00 CH 02 01
00 00 02 37 00 CH 02 01
00 00 04 3F 00 CH 02 01
```

Một lệnh chỉ đọc chốt kênh + khuôn thật trên xe (CanDataCollect tự log bảng nó gửi):

```
adb -s <vehicle-ip>:5555 logcat -d -s "[DataCollect]CanDataHandle" | grep "sendRegisterTable config:"
```

(Bắt được khi MCU thức / `onNeedRendRegisterTable`; nếu log đã trôi thì chờ lần nổ máy kế.) Có hex đó ⇒ biết `CH`/`mode` THẬT của các id CanDataCollect đang thu, và có thể chọn một id CanDataCollect **đã** thu làm đối chứng: nếu canmon (bảng sửa) vẫn 0 khung với id đã có người thu ⇒ lỗi ở đường sự kiện native tới uid shell, không phải ở bảng.

Khả thi với uid shell: **chưa biết** [CHƯA BIẾT]. Phía Java đã qua [ĐO]; phía native giao sự kiện cho tiến trình shell chưa chứng minh. Cần sửa navopen trước khi đo lại: (1) `buildRegTable` nhận `CH`/`mode` (mặc định mode 2, op 1); (2) in mã trả của `sendRegisterTable` và của `enableDevice`; (3) trần thời gian là tham số script (≤110 s). Việc sửa navopen nằm ngoài phạm vi phiên này (repo `../NavOpen`).

## 4. Sự cố quy trình

- Một lệnh `logcat | grep` quá rộng đã in một phần token OAuth của Google từ log GMS ra màn phiên làm việc (không ghi vào tệp/repo nào) [ĐO]. Bài học: grep log theo **tag/chuỗi hẹp** (`-s <tag>`, grep từ khoá cụ thể), không grep rộng kiểu `token|auth|key` trên toàn logcat.
- Khung gõ tay thiếu byte (`2370000001`) đã làm biển tắt — lỗi người, không phải lỗi script; script nay kiểm độ dài khung trước khi gửi (§5).

## 5. Sửa script (sau buổi)

- `scripts/vehicle/isa-0x237-inject-2606.sh`: khung hex **không dấu phẩy** (`2370002E010B`); kiểm khung đúng 12 ký tự hex trước khi gửi; thêm `ONLINE_FIX=1` gửi `237000000101` (khôi phục online = 1 như đọc gốc); cổng đỗ: `getCurrentGear` trả null trên xe 10/10 ⇒ vẫn **đóng an toàn**, nhưng có đường thay thế tường minh: tốc độ đọc được = 0 **và** owner gõ `PARKED-NO-GEAR` xác nhận đang P (ghi vào log).
- `scripts/vehicle/can-0x43f-listen-2606.sh`: trần 110 s/lượt (dưới watchdog 120 s của navopen); `SECS` > 110 ⇒ chia nhiều lượt ≤110 s, mỗi lượt kiểm `register table cleared`, lượt nào thiếu ⇒ dừng vòng + chạy canmon 5 s để dọn; ghi rõ bảng navopen-v4 sai khuôn (§3.1) nên 0 khung là kết quả dự kiến tới khi navopen được sửa.
