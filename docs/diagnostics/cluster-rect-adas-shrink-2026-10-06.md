# CLUSTER-RECT-ADAS — thu nhỏ khung ADAS trắng bên phải ở cụm Chữ nhật (theme2 FULL): không có đòn bẩy phía Android

> **Trạng thái**: Current · **Cập nhật**: 2026-10-06 · **Loại**: Diagnostics (nghiên cứu, chỉ đọc, chưa chạm xe) · **Owner**: dangkhoi ·
> **Câu hỏi owner 06/10**: ở cụm Chữ nhật (theme2 FULL, opcode 31) khung ADAS trắng lớn bên phải luôn còn; muốn **THU NHỎ** (không
> phải né). Đo xe 06/10: phím menu vô-lăng chỉ thu hình xe/đường bên trong, nền trắng vẫn còn (`oncar-2026-10-06-cluster-rect.md` F6).
> Owner nhớ DashCast / OpenBYD từng thu được ở kiểu chữ nhật.
> **Liên quan**: `cluster-rect-seal-2026-10-05.md` · `oncar-2026-10-05-slot-cluster.md` §4 · `oncar-2026-10-06-cluster-rect.md` ·
> spec `../specs/kachi-290-cluster-rect-fix.html` · `ProjectionRecipe.FORBIDDEN_OPS`

Mức bằng chứng theo `CLAUDE.md` §2 / `conversation-protocol.md`: **[ĐO nguồn]** = đọc QML/disasm/decompile firmware thật, **[ĐO xe]** =
đo trên xe, **[SUY]** = suy luận khớp dữ liệu, **[ĐOÁN]**, **[CHƯA BIẾT]**.

## 0. Kết luận một dòng

**[ĐO nguồn, cả 3 firmware 2506030 · 2511080 · 2602030]** Ở theme2 FULL, nền trắng là ảnh `warningInfo/day_bg.png` 658×396 đặt
cố định ở (1256,225) và hiện khi `adasInterfaceDisplay !== 0`; còn `libBydDataSource.so` **chỉ sinh 0 khi xe KHÔNG ở số ON** — lúc xe
chạy giá trị luôn là 1 (trang ADAS lớn) hoặc 2 (cửa sổ ADAS nhỏ). ⇒ Ở Chữ nhật, khung trắng là **một phần cố định của bố cục OEM**,
không thu nhỏ được từ Android (không opcode, không HAL setter, không phím). Đòn bẩy "thu ADAS" duy nhất có thật là **theme1 (opcode
30 = kiểu Bo tròn hiện tại của Kachi)** — và đó **chính là thứ DashCast gọi là "ADAS Window Fix"**. OpenBYD không có mã ADAS nào.

## 1. Cách tái lập nguồn (để lần sau không mò)

Kho `<cache>/clusternav-re/sysimg` ghi trong `cluster-rect-seal-2026-10-05.md` **không còn** (chỉ còn Ghidra). Tái lập từ
`../firmware/fw-2602-diff/new_out/system.img` (2602030), `old_out/system.img` (2511080), và
`../firmware/BYDUpdatePackage/msm8953_64/UpdateFull.zip` (= **2506030**, `post-outswver=13.1.33.2506030.1`; dump bằng
`payload-dumper-go -p system`):

```
debugfs -R "dump /system/lib64/cluster_theme2.rcc <out>"   <system.img>   # cũng: cluster_theme1.rcc, libBydDataSource.so
debugfs -R "dump /system/framework/framework.jar <out>"    <system.img>
# .rcc = Qt "qres" v3, nén zlib: viết bộ giải 30 dòng (tree 22 byte/entry, cờ 1 = zlib + 4 byte cỡ) ⇒ qml10_25/cluster.qml
objdump -d --no-show-raw-insn libBydDataSource.so      # objdump của Xcode đọc được ELF aarch64
jadx --single-class android.hardware.bydauto.instrument.BYDAutoInstrumentDevice framework.jar
```

Địa chỉ dưới đây là bản **2602030** (xe đang chạy); 2506030 cùng logic, lệch địa chỉ (vd `updateAdasInterface` @0x12a1d4).

## 2. QML theme2 — điều kiện chính xác của nền trắng

| # | Sự thật | Mức |
|---|---|---|
| Q1 | `qml10_25/cluster.qml:47-48` (trong `Component.onCompleted`, **đè** binding khai báo ở `:2026`) — diễn đạt lại, không chép nguyên văn: nền ADAS lớn (`adas2dNaviWindowBg`) hiện khi đang ở trang dẫn đường (`naviType` = 4) và máy ON (`powerGear` = 3), cộng với: nền tảng ADAS loại 1 ⇒ xét `adasWindow` khác 0; nền tảng còn lại ⇒ xét `adasInterfaceDisplay` **khác 0** và `adasType` = 0 | [ĐO nguồn 2602030; giống hệt 2511080 và 2506030] |
| Q2 | `adas2dNaviWindowBg` là một ảnh đặt tại x 1256, y 225, nguồn là ảnh nền ban ngày `warningInfo/day_bg.png` hoặc ban đêm `warningInfo/night_bg.png` (`cluster.qml:2022-2028`); ảnh PNG **658×396**, tấm bo góc xám-trắng ⇒ phủ (1256..1914, 225..621) = "khung trắng 1/3 bên phải" | [ĐO nguồn + PNG] |
| Q3 | Cửa sổ ADAS nhỏ là ảnh khác: `adasWindowBackground` `adas2d/window_light_new.png` 305×260 ở (1266.5,392), hiện khi `adasInterfaceDisplay === 2` (`:45-46`, `:2012-2021`); hình xe/đường nhỏ = `Adas.qml` trong khung 189×120 (`:2029-2061`). Nằm **bên trong** tấm trắng ⇒ phím menu (1→2) chỉ đổi nội dung bên trong, tấm trắng vẫn đứng — khớp đúng F6 | [ĐO nguồn] · [ĐO xe 06/10 F6] |
| Q4 | Trang ADAS lớn (`adasInterfaceDisplay === 1`) do `Center.qml:230-235,275-276` vẽ, dời x+585 vào trong tấm trắng khi `naviType === 4` | [ĐO nguồn] |
| Q5 | Theme1 (`qml/cluster.qml:47-48`, 12.3"): cùng tấm nhưng điều kiện `adasInterfaceDisplay === 1` ⇒ về 2 là tấm biến mất. Đây là lý do "Bo tròn thu được, Chữ nhật không" | [ĐO nguồn] |
| Q6 | Mọi chỗ còn lại đọc `adasShowMode/adasTestMode/adasDebugMode/dashbordTestMode` (đích của opcode 12/13, 32/33, 47/48) **không** chạm tấm trắng (chỉ ADAS 3D / màn debug) | [ĐO nguồn grep toàn theme2] |
| Q7 | `naviType === 4` = FULL: tấm hiện ở FULL (đo xe) và opcode 17/41 đặt 3 = SMALL | [SUY mạnh] |

⇒ Muốn tấm trắng tắt khi xe chạy (`powerGear === 3`) cần MỘT trong: `adasInterfaceDisplay = 0`, `adasType ≠ 0`, `naviType ≠ 4`,
hoặc `adasPlatform = 1` kèm `adasWindow = 0`.

## 3. Ai ghi `adasInterfaceDisplay` (data item 466) — `libBydDataSource.so`

- [ĐO disasm `libBydCluster.so` `DataSourceManager::handleDataItemChanged` @0x78adc, bảng nhảy 0x119a9b0] id **466** → @0x7d12c →
  `adasInterfaceDisplayChanged`; 467 → `adasTypeChanged`; 552 → `adasPlatformChanged`; 47 → `adasTestMode`; 533 → `adasShowMode`;
  549 → `adasDebugMode`. Phía cụm chỉ có getter, không có setter cho QML/Android.
- Người ghi item 466 (quét toàn bộ `#0x1d2` trong `libBydDataSource.so`), **đủ cả 3, không còn ai khác**:

| Ghi | Giá trị | Nguồn |
|---|---|---|
| `updateAdasInterfaceDisplayDataItem` @0x12c974 → `SetDataItem(466, this+0x230)` (trễ 1 s khi vừa ON) | = trường `+0x230` | [ĐO disasm] |
| opcode **41** (`clusterDebug` khối 0x146db8 → @0x146ea8) | **2** cứng | [ĐO disasm] |
| opcode **42** (@0x146bbc → @0x146efc) | trả về `+0x230` thật | [ĐO disasm] |

- Người ghi trường `+0x230` (quét `strb … #0x230]`), đủ cả 4:

| Ghi | Logic | Nguồn |
|---|---|---|
| hàm dựng `BusinessUi1` @0xfdb40 | 0 | [ĐO disasm] |
| `updateAdasInterface` @0x12c2a0 | nếu CAN `0x12D0002A` (số/power gear) ≠ 3 ⇒ **0**; ngược lại CAN `0x26F00028` == 6 ⇒ **1**, khác ⇒ **2**; rồi `SetConfig_UINT8(50, v)` (**ghi bền** trang ADAS cuối) | [ĐO disasm] |
| `adasSelfStudy` @0x12c74c | `0x26F00028` == 6 ⇒ 1, khác ⇒ 2 (==1 ⇒ còn ghi CAN `0x40C03035=1`) | [ĐO disasm] |

  ⇒ **Khi xe ở ON, `adasInterfaceDisplay ∈ {1,2}` — không đường nào ra 0.** [ĐO disasm; 2506030 @0x12a1d4 cùng nhánh]
- `0x26F00028` là **đầu vào CAN** (handler `canDataUpdate` @0x149858 lưu vào `+0x1fc`), đúng bằng
  `BYDAutoFeatureIds.INSTRUMET_VIEW_INTERFACE` nhánh CanFD (653262888) trong `framework.jar` 2602030, và
  `BYDAutoInstrumentDevice.INSTRUMET_ADAS_VIEW = 6` ⇒ đây là **"trang hiện tại" của cụm** (1 lái xe, 2 menu, 3 lỗi, 4 sạc, 5 xả, 6 ADAS,
  7 hành trình, 8 tăng tốc). Phím menu vô-lăng đổi trang **phía ECU** rồi gửi về qua CAN [ĐO disasm: lib không có handler phím; SUY:
  phím đi thẳng CAN — Android chỉ thấy keycode 309, xem `oncar-2026-10-05-slot-cluster.md` §4].
- Cụm tự **xin** trang qua CAN `0x40C03035` (1 = lớn, 2 = nhỏ): `requestToShowAdasWindow(n)` @0x12c360 (lớn rồi hẹn `n` giây gọi
  `requestToShowAdasCtrl` @0x12c564 = xin nhỏ), `adasInterfaceSet` @0x12c1b4, khôi phục `config 50` khi lên ON (@0x149cb8).
  Không có giá trị "0/ẩn" trong giao thức này. [ĐO disasm]
- `adasType` (467) = CAN `0x43200021` qua `adasSelfStudy` (`+0x204`) = cấu hình ECU ADAS (QML `AdasDebug.qml:15`: 0 "单车道" /
  khác "三车道"). `adasPlatform` (552) = `switchAdasPlatform` @0x13a7c4 từ mã đời xe `+0x103a`. Cả hai là **cấu hình xe**, không
  phải trạng thái hiển thị. [ĐO disasm] · nguồn ghi `+0x103a` [CHƯA BIẾT]

## 4. Bảng opcode AutoContainer (`clusterDebug(op)` = handler `sendInfo(1000, op)`, bảng nhảy 0..211 @0x3875c)

| Opcode | Đích (2602030) | Tác dụng lên tấm trắng | Mức |
|---|---|---|---|
| 12 / 13 | item 47 `adasTestMode` = 1 / 0 | Không | [ĐO disasm + QML] · DashCast 0.1.30: *"cmd 12/13 confirmed without effect on 2D cluster Seal EU"* [ĐO hiện trường DashCast] · Kachi 05/10 [ĐO xe] |
| 32 / 33 | item 533 `adasShowMode` = 1 / 0 | Không (chỉ ADAS 3D) | [ĐO disasm + QML] |
| 47 / 48 | item 549 `adasDebugMode` = 1 / 0 | Không (màn debug ADAS) | [ĐO disasm + QML]; DashCast 0.3.2 "secret ADAS debugging mode" |
| 14 / 15 | item 49 = 1 / 0 | Không | [ĐO disasm] |
| **41** | đèn báo test (item 0=3, đèn lưng, item 46=2, mọi đèn `debugSetDisplayState(7)`), rồi `SetConfig(49,2)` + `updateThemeIndex(1)` + `SetConfig(53,3)` + CAN `0x40C03032=3` + item 467 + **item 466 = 2** + 533=1, 537=1 | **Không** — đặt 2, mà theme2 hiện tấm khi ≠ 0 | [ĐO disasm] |
| 42 | thoát test: 533=0, **466 = `+0x230` thật**, đèn ADAS, `updateNaviDisplay`, 537=0 | Không | [ĐO disasm] — **KHÔNG** trả lại `config 49` / `config 53` |
| 30 / 31 / 29 | đổi theme 12.3" / 10.25" / 8.8" | 30 ⇒ theme1 ⇒ tấm theo luật `=== 1` (thu được) | [ĐO xe 05/10] + Q5 |

**Opcode 41 có ghi bền thật không?** [ĐO disasm] Có: `ConfigureManager::SetConfig_UINT8(49, 2)` @0x146de0 và `(53, 3)` @0x146e20 —
cùng lớp lưu với `getConfig … m_u8ThemeIndexStore … m_u8NaviTypeStore` (chuỗi @0x301c4) [SUY: 49 = kho theme, 53 = kho kiểu dẫn đường]
— và CAN `0x40C03032=3` @0x146e40. Đường trả: opcode 42 **không** khôi phục hai config đó; muốn trả phải biết giá trị cũ (không đọc
được từ Android) và tìm opcode ghi lại đúng khoá [CHƯA BIẾT]. ⇒ 41 vừa **vô dụng cho mục tiêu này** vừa để lại trạng thái bền mồ
côi: giữ nguyên trong `FORBIDDEN_OPS` (bổ sung lý do mới: nó đặt 466 = 2 chứ không phải 0).

## 5. DashCast / OpenBYD đã làm gì với ADAS

- **DashCast** [ĐO `../dashcast-github` git + CHANGELOG]: chỉ có 4 thứ, không có gì khác (`git log -S adasInterfaceDisplay / navi_type /
  theme_index` = rỗng):
  - opcode 12/13 nút ADAS (0.1.28) → gỡ ở 0.1.30 vì *không tác dụng trên Seal EU 2D*; tab ADAS chẩn đoán gỡ hẳn ở `ce39a79f`.
  - opcode 47/48 "ADAS debug mode" (0.3.2).
  - **"ADAS Window Fix" = gửi opcode 30 trước 16** (v1.46 *"cmd30 before cmd16 sequence — fixes ADAS stretching"*; `7c90dc14`
    tách thành công tắc mặc định TẮT; CHANGELOG 1.8.27: *"the ADAS fix still forces 12.3" there"*, kèm cảnh báo bản 10.25" bị đọc nhầm
    thành 12.3" vì đã bị ép).
  ⇒ Cái DashCast "thu được ADAS" **là theme1 (12.3")** — đúng bằng kiểu **Bo tròn** Kachi đang gửi. Khi tắt công tắc đó DashCast ở
  theme gốc của Seal = theme2 = có tấm trắng y như Kachi. [ĐO nguồn DashCast] · việc owner nhớ là "chữ nhật" [SUY: khung app DashCast
  là chữ nhật 1920×720 nhưng nhìn qua thấu kính theme1]
- **OpenBYD** (`../jadx-openbyd`, `../jadx-openbyd24`) [ĐO grep]: không gọi AutoContainer / `sendInfo` / `setViewSwitch`; chỉ dùng
  `BYDAutoInstrumentDevice` để `get(...)` và `sendAddressInfo` (HUD). Chiếu cụm bằng VirtualDisplay riêng + lớp phủ gradient
  (`ClusterOverlayManager.addGradientOverlays`, jadx không dịch được thân hàm). Không có đường nào chạm trang ADAS.
- **HAL setter duy nhất liên quan**: `BYDAutoInstrumentDevice.setViewSwitch(0..2)` → `INSTRUMET_2IN1_VIEW_SWITCH_SET` (CanFD
  `0x4C108012`; 1 `WIN_CLOSE`, 2 `WIN_OPEN`), trạng thái đọc `getViewStatus` = `0x26F0001E` (1 `HUGE_WIN`, 2 `BAR`) [ĐO decompile
  framework 2602030]. `libBydDataSource.so` **không đọc** cả `0x4C108012` lẫn `0x26F0001E` [ĐO quét hằng] ⇒ nếu ECU phản ứng thì cũng
  chỉ đổi `0x26F00028` ⇒ vẫn 1/2 ⇒ tấm vẫn còn [ĐO logic §3]. Đây là ghi CAN, hiệu ứng thật [CHƯA BIẾT] ⇒ không đề xuất.

## 6. Đòn bẩy, xếp theo an toàn

| # | Đòn bẩy | Thu được tấm trắng ở Chữ nhật? | An toàn | Kết luận |
|---|---|---|---|---|
| L1 | **Bo tròn** (theme1, opcode 30 — mặc định Kachi) + phím menu / tự nhỏ | Không áp dụng cho Chữ nhật; ở Bo tròn **thu được** [ĐO xe 05/10] | Đang chạy | Đây là câu trả lời thật cho "thu ADAS" |
| L2 | Đọc trạng thái (chỉ đọc) để chốt cơ chế trên xe | — | An toàn (đọc) | Nên làm 1 lần để chuyển §3 từ [ĐO nguồn] sang [ĐO xe] |
| L3 | Chữ nhật + đặt app tránh tấm (vùng (50,128)-(1285,555)) | Né, không thu | An toàn | Owner đã bỏ (F9: "để full") — giữ làm gợi ý |
| L4 | `setViewSwitch` (HAL, ghi CAN `0x4C108012`) | Không (đầu ra vẫn 1/2) | Rủi ro ghi CAN, hiệu ứng chưa biết | **Không làm** |
| L5 | Opcode 12/13, 32/33, 47/48, 14/15 | Không | Thấp nhưng vô ích | **Không làm** |
| L6 | Opcode 41 | Không (đặt 2) | **Nguy hiểm**: ghi bền config 49/53 + CAN, 42 không trả | **Cấm** (giữ `FORBIDDEN_OPS`) |
| L7 | Bơm phím 309 / phím khác | Không (lib không nhận phím; 309 = cúp cuộc gọi) | Có hại | **Cấm** |
| L8 | Vá `cluster_theme2.rcc` (`!== 0` → `=== 1`) | Có | Cần root + P0 cụm trắng nếu QML lỗi | Chỉ khi có root (giống `CLUSTER-RECT-SEAL` mở khoá 2) |
| L9 | BYD đổi QML trong firmware mới | Có | — | Kiểm mỗi OTA: dòng 47-48 theme2 |

### Đo trên xe (dừng ngay ở lần sập đầu tiên — memory `kachi-feedback-oncar-stop-at-first-crash`)

**L2 — chỉ đọc, không đổi trạng thái** (cụm đang Chữ nhật, Maps đang chiếu, xe ON). `<A>` = lệnh adb qua cầu nc
(`adb-car-tunnel-macos.md`); cầu kiểm thử phải đang bật (công tắc 60 phút, `TestBridgeStore`), `hal getid` là đường chỉ-đọc:

1. Trước khi bấm phím: `<A> shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd hal --es op getid --es dev BYDAutoInstrumentDevice --es m 653262888"`
   (= `getInstrumentView` / `0x26F00028`) và lặp với `--es m 653262878` (`0x26F0001E`). Kỳ vọng: 6 khi tấm đang hiện trang ADAS lớn.
2. Bấm phím menu vô-lăng một lần, chạy lại bước 1. Kỳ vọng [ĐO nguồn]: `0x26F00028` ≠ 6, tấm trắng **vẫn** còn (chụp
   `fission_screencap -d 0`). Nếu `getid` báo `no permission`/sai id ⇒ thử tên hằng `INSTRUMET_VIEW_INTERFACE` (máy có thể không phải
   nhánh CanFD — khi đó id là 460853).
3. (tuỳ chọn) `<A> shell "logcat -d | grep -E 'updateAdasInterfaceDisplayDataItem|adasInterface'"` — lib có in log nhưng có cổng mức log;
   tiến trình nạp lib phía Android có ghi vào logcat hay không [CHƯA BIẾT].

Nếu bước 2 ra giá trị khác 1..8 hoặc tấm biến mất ⇒ kết luận §3 sai trên xe ⇒ dừng, ghi lại, mở lại nghiên cứu.

**L1 — không cần đo thêm** (đã [ĐO xe 05/10]). Nếu muốn chốt "tự nhỏ sau 5 s" (`updateWindowGetSignal` → `requestToShowAdasWindow(5)`):
ở Bo tròn, để trang ADAS lớn, chờ 10 s không bấm, chụp `fission_screencap -d 0`.

**L4–L7 — không đo**: đã chết bằng nguồn (§3–§5); đo chỉ thêm rủi ro.

## 7. Nếu làm gì thì code gì

- **Không có tính năng "thu ADAS ở Chữ nhật" để code** trên firmware 2506030–2602030 [ĐO nguồn].
- Việc nhỏ đáng làm (cần owner duyệt, ghi vào backlog `CLUSTER-RECT-OPTION`):
  1. Câu gợi ý ở Cài đặt › Chiếu cụm khi chọn Chữ nhật: *"Khung ADAS bên phải là cố định của firmware ở kiểu Chữ nhật — muốn thu nhỏ
     ADAS bằng phím menu, chọn Bo tròn."* (chuỗi qua i18n đủ vi/en/zh/th/ms).
  2. Ghi lý do mới vào KDoc `ProjectionRecipe.FORBIDDEN_OPS` cho 41: *đặt 466 = 2, không phải 0; 42 không trả config 49/53*.
  3. (Nếu L2 xanh) thêm vào `ClusterDiag` một dòng đọc `0x26F00028` để ảnh chụp màn chẩn đoán tự có trang cụm hiện tại.
- Mở khoá thật (trace-den-tan-cung): **(a)** firmware mới đổi `cluster.qml:47-48` theme2 sang `=== 1` — kiểm mỗi OTA bằng §1; hoặc
  **(b)** có root để vá rcc. Không điều kiện nào đang đúng ⇒ "thu ADAS ở Chữ nhật" = **BLOCKED**, bỏ hay giữ là quyết định owner.
