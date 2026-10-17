# HUD qua receiver ClusterDebug — đường gửi khác HAL (chuẩn bị buổi xe, CHƯA chạy)

> **Trạng thái**: Evidence (RE + chuẩn bị) · **Cập nhật**: 2026-10-16 · **Chủ**: dangkhoi · **Xe**: Seal, DiLink3, fw 2606
> **Backlog**: `HUD-CLUSTERDEBUG-PATH` · **Script**: `scripts/oncar/hud-clusterdebug-nav.sh`
> **⛔ ĐÓNG 2026-10-17**: mã đường thứ hai (§9, 3.1.12) **removed 3.1.14, testers report no effect** — doc + script giữ làm lịch sử; tên đường về đúng đường 3.1.11 (HAL, chế độ A).
> **Liên quan**: ADR `docs/decisions/0002-hud-nav-coding-locked.md` · `docs/diagnostics/factory-hud-nav-RE-avenues-2026-08-19.md` §0.1 ·
> `docs/diagnostics/oncar-seal-2026-10-10-r18-sla-can.md` §2 (bơm TestDevice 0x237) · `docs/diagnostics/oncar-seal-2026-10-09-hud-isa-power.md` §2 ·
> `docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` §13.2/§25 Q5 · `docs/_handoff/re-showroom-ref.md` · backlog `HUD-ROADNAME-OEM-CADENCE`
>
> Owner 16/10: *"vì mình đi đường HAL nên bị chặn, người ta đi đường khác mà, cho nên cũng nên thử chứ?"* — buổi xe là "chỉ test",
> nên mọi thứ dưới đây phải sẵn, chạy một lượt là có kết luận.

## 0. Tóm tắt

| Câu hỏi | Trả lời | Mức |
|---|---|---|
| "Đường khác" của DiAuto là gì? | Broadcast tường minh tới `com.byd.clusterdebug/.BroadcastReceiverCAN`, extra `normal` = hex có dấu phẩy | [ĐO nguồn DiAuto] |
| Receiver làm gì với nó? | Tách theo `,` → `byte[]` → `BYDAutoTestDevice.set({0xAA00020F}, buffer)` = **TEST_SIMULATE_DOWN**. Không đọc action. `normal` và `wholeFrame` xử lý **y hệt** | [ĐO nguồn ClusterDebug 10601004 của fw 2606] |
| Có phải đường mới với mình? | **Không hẳn.** Cùng TestDevice `0xAA00020F` mà `navopen setbytes test` đã dùng ngày 10/10. Mới ở chỗ: (1) gói là bản ghi **dẫn đường** `0x43E/0x43F`, chưa từng bơm qua đường này; (2) app thường gọi được, không cần navopen | [ĐO nguồn] |
| Khác gì đường HAL của Kachi? | Kachi: `BYDAutoInstrumentDevice` → `setInt/setBuffer` từng feature (HAL kiểm quyền thiết bị 1007, mã hoá phía dưới). ClusterDebug: bơm thẳng bản ghi đã mã hoá vào TestDevice | [ĐO nguồn] |
| Kỳ vọng trên Seal | HUD: **nhiều khả năng KHÔNG** (10/10 đường này dựng biển 0x237 trên cụm, HUD không thấy; HUD Seal thiếu coding `40d`). Cụm: có thể hiện | [ĐOÁN] — chính là thứ H1 đo |
| Tên đường của Kachi có khác DiAuto? | Ở ranh giới Java→HAL **không khác OEM**: Kachi và SDK `sendNextPathName` gửi cùng UTF-16LE thô vào `0x43FA1008`. Khúc 7 byte + tổng kiểm của DiAuto là dạng **sau** bộ mã hoá HAL, cần vì TestDevice đi vòng qua HAL | [ĐO nguồn framework] + [SUY về bộ mã hoá] |

## 1. So sánh cơ chế

| | Kachi (hiện nay) | ClusterDebug (DiAuto) |
|---|---|---|
| Lớp gọi | `BYDAutoInstrumentDevice` qua bypass-context (`BydHal.writeNavFrame`, `BydHalContentPush.pushNavigation/pushPathName`) | `BroadcastReceiverCAN` trong tiến trình `com.byd.clusterdebug` (priv-app, có `BYDAUTO_TEST_SET`) |
| API framework | `set(dev, featureId, int)` / `set(dev, featureId, byte[])` → `BYDAutoDeviceManager.setInt/setBuffer` | `BYDAutoTestDevice.set(new int[]{0xAA00020F}, ev)` → `AbsBYDAutoDevice.set(int[],EventValue)` → `set(TEST, 0xAA00020F, bufferDataValue)` |
| Quyền | `INSTRUMENT_SET_PERM`; HAL từ chối `0x43F01030` và họ `0x1F7*` trên Seal (`no permission … device: 1007`) | `BYDAUTO_TEST_SET` (ClusterDebug giữ). Receiver `exported=true`, **không** permission ⇒ uid nào cũng gửi được |
| Dữ liệu | Giá trị từng feature; tên đường = UTF-16LE thô | Chuỗi bản ghi `[id BE 4][len 1][giá trị len]`, tên đường đã cắt khúc + tổng kiểm |
| Đi tới đâu | MCU → bus; cụm hiện mũi tên/cự ly (Seal); HUD zin SL6 hiện mũi tên + cự ly, không tên [ĐO owner/anh em] | 10/10 với `0x2370002E`: tới datasource cụm (biển xám), HUD không [ĐO owner]; ra bus hay không [CHƯA BIẾT — `canmon` 0 khung do lỗi công cụ] |
| Kiểm chứng của DiAuto | — | Chỉ bật khi `Build.FINGERPRINT` = một bản **Android 13** (`BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260722…`), ClusterDebug `longVersionCode 10601004`, cờ SYSTEM, receiver exported không permission, chữ ký SHA-256 `efe3ca8a…3efc` |

Hệ quả [SUY]: DiAuto đã thử thật trên một đời **DiLink Android 13**. Seal là **DiLink3.0 Android 10** (`ro.build.fingerprint
BYD-AUTO/DiLink3.0/DiLink3.0:10/QKQ1.210910.001/eng.build.20260610…`, `firmware/fw-2606/meta/getprop.txt`) nhưng có **đúng cùng**
ClusterDebug `versionCode:10601004` (`firmware/fw-2606/meta/packages.txt:24`). Receiver giống nhau; cái khác là phía nhận dữ liệu
TestDevice (MCU/cụm/HUD) — đó là [CHƯA BIẾT] mà buổi xe phải đo.

## 2. Bằng chứng nguồn (file:line)

Tái lập: `jadx -d <out> firmware/fw-2606/_system_priv-app/ClusterDebug/ClusterDebug.apk` và
`jadx --no-res -d <out> classes.dex` (rút từ `firmware/fw-2606/_system_framework/framework.jar`). Đường dẫn tính từ thư mục cha của repo.

### 2.1 ClusterDebug 1.6.1.4 (versionCode 10601004, compileSdk 33, minSdk 29)

- `AndroidManifest.xml:30-33` — `<receiver android:name="com.byd.clusterdebug.BroadcastReceiverCAN" android:enabled="true" android:exported="true"/>`:
  **không intent-filter, không permission** ⇒ chỉ nhận broadcast tường minh (`-n`), action tuỳ ý [ĐO].
- Manifest khai `BYDAUTO_TEST_SET`, `BYDAUTO_INSTRUMENT_COMMON` (`:12-20`) [ĐO]; được cấp hay chưa trên xe ⇒ H0 đọc `dumpsys package`.
- `BroadcastReceiverCAN.java:17-23` — `getStringExtra("normal")`, log `ClusterDebugService: receive normal: …`, `split(",")`.
- `BroadcastReceiverCAN.java:24-28` — `getStringExtra("wholeFrame")` **ghi đè** mảng của `normal` nếu có. Không có nhánh khác: hai extra
  là cùng một đường [ĐO]. (Script chỉ dùng `normal`, không bao giờ `wholeFrame`.)
- `BroadcastReceiverCAN.java:30-33` — `Integer.parseInt(tok, 16)` từng token ⇒ token sai = `NumberFormatException` **trong tiến trình
  hệ thống** ⇒ script kiểm khuôn trước khi gửi.
- `BroadcastReceiverCAN.java:34-36` — `BYDAutoTestDevice.getInstance(ctx).set(new int[]{-1442840049}, ev)`; `-1442840049` = `0xAA00020F`.
- Không đọc `intent.getAction()` ở đâu cả ⇒ `byd.hud.NAVIGATION` chỉ là nhãn [ĐO].
- `ClusterDebugService.java:23-27` — đăng ký thêm cùng receiver động với action `com.byd.cluster.spi` (đường "community" cũ) [ĐO].

### 2.2 framework.jar (fw 2606)

- `BYDAutoFeatureIds.java:4211` — `TEST_SIMULATE_DOWN_SET = (!isCanFD && isToyota) ? 62923 : -1442840049` [ĐO].
- `AbsBYDAutoDevice.java:259-289` — `set(int[], EventValue)`: kiểm `checkDeviceFeatures`, `enforceCallingOrSelfPermission(getSetPermission())`,
  một id + `bufferDataValue` ⇒ log `AbsBYDAutoDevice: set featureID is aa00020f bufferDataValue is […]` rồi `setBuffer` [ĐO].
  (`BYDAutoEventValue` khởi tạo `intValue = INVAILD_INT`, `BYDAutoEventValue.java:16` ⇒ không lạc sang nhánh INT.)
- `BYDAutoTestDevice.java:36` — `TEST_SET_PERM = android.permission.BYDAUTO_TEST_SET` [ĐO].
- `BYDAutoInstrumentDevice.java:1689-1705` — `sendNextPathName(name)`: kiểm quyền, `getBytes("UnicodeLittleUnmarked")`, nhận 0..255 byte,
  rồi `super.set(dev, INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET, unicodes)` — **không** cắt khúc, **không** tổng kiểm ở Java [ĐO].

### 2.3 DiAuto (AGPL-3.0, chỉ lấy dữ kiện giao thức, không chép mã)

`github.com/shihabal3amri/DiAuto`, `app/src/main/java/com/andrerinas/openheadunit/hud/`:
`BydStandaloneHudOutput.kt` (đích, extra `normal`, `FLAG_RECEIVER_FOREGROUND`, cổng fingerprint/version/chữ ký) ·
`BydStandalonePackets.kt` (khuôn bản ghi, start/clear, tên đường) · `BydStandaloneSession.kt` (thứ tự + nhịp) ·
`BydFactoryTurnCode.kt` (bảng NEW_ICON → mã cụm). Dữ kiện [ĐO nguồn DiAuto]:

- start = `43,E0,00,3A,01,02`; clear = `43,E0,00,3A,01,01` (id `0x43E0003A` = `INSTRUMENT_SEND_NAVI_STATUS_SET`, DiCarServer `Instrument.java:762`).
- guidance = bản ghi `0x43F01018` (cự ly, 4 byte BE, 0..16 777 214) + `0x43F01010` + `0x43F01030` (cùng mã mũi tên), nối liền trong **một** chuỗi;
  tên đường nếu có nối tiếp sau, cùng broadcast.
- Hex in HOA, `%02X`, nối bằng `,`.
- Phiên: lần đầu gửi start (ghi cờ `pending_clear` vào prefs **trước**), rồi guidance; gửi lại khi gói đổi **hoặc** ≥ 1 s; clear khi hết dẫn,
  và clear lúc khởi động nếu cờ còn (khôi phục sau crash) — đúng tinh thần CLAUDE.md §5.
- Mã mũi tên: `BydFactoryTurnCode` NEW_ICON 3 (rẽ phải) → 2 — **khớp** `Maneuver.TURN_RIGHT.toHudIcon() = 2` của Kachi
  (`core/src/main/kotlin/com/byd/clusternav/navigation/Maneuver.kt:114`) [ĐO cả hai nguồn].
- Đối chiếu của mình: khuôn bản ghi `[id4][len][val]` đã chạy qua đúng TestDevice này trên Seal 10/10 (`2370002E010B` ⇒ biển xám) [ĐO xe];
  khác duy nhất: navopen nhận hex liền, ClusterDebug cần dấu phẩy (lần gửi có phẩy qua navopen ra `NumberFormatException`).

## 3. Gói từng bước (hex chính xác, đã kiểm lại bằng máy)

| Bước | Gói gửi (`--es normal …`) | Nghĩa |
|---|---|---|
| H1 start (1 lần) | `43,E0,00,3A,01,02` | trạng thái dẫn = 2 |
| H1 guidance (×10, 1 s) | `43,F0,10,18,04,00,00,01,F4,43,F0,10,10,04,00,00,00,02,43,F0,10,30,04,00,00,00,02` | 500 m · rẽ phải (2) · rẽ phải (2) |
| H1b (chỉ khi H1 không hiện gì ở cả hai) | start, rồi mỗi giây 3 broadcast: `43,F0,10,18,04,00,00,01,F4` · `43,F0,10,10,04,00,00,00,02` · `43,F0,10,30,04,00,00,00,02` | cùng giá trị, **một bản ghi/broadcast** (khuôn đã chạy 10/10) — tách lỗi "không ghép nhiều bản ghi" khỏi "không tới HUD/cụm" |
| H2a (×10, 1 s) | guidance + `43,FA,10,08,08,A1,14,4B,00,61,00,63,00,43,FA,20,08,08,A2,68,00,69,00,20,00,74,43,FA,30,08,08,A3,00,65,00,73,00,74,00,43,FA,40,08,08,A4,C0,FF,FF,FF,FF,FF,FF` | "Kachi test" |
| H2b (×10, 1 s) | guidance + `43,FA,10,08,08,A1,18,10,01,B0,01,DD,1E,43,FA,20,08,08,A2,6E,00,67,00,20,00,4C,43,FA,30,08,08,A3,00,EA,00,20,00,4C,00,43,FA,40,08,08,A4,E3,1E,69,00,BE,FF,FF` | "Đường Lê Lợi" (NFC) |
| H3 + trap EXIT | `43,E0,00,3A,01,01` rồi 1 s sau `43,E0,00,3A,01,04` | clear kiểu DiAuto (1) rồi kết thúc kiểu AmapService OEM (4, `AmapService.java:190`) |

Dẫn xuất tên đường (theo dữ kiện DiAuto): nội dung = `[số byte UTF-16LE][UTF-16LE…][tổng các byte & 0xFF]`, đệm `FF` tới bội 7;
khúc thứ i (từ 0) = id `0x43FA1008 + (i << 12)` · len `08` · số thứ tự `(id >> 12) & 0xFF` (`A1`, `A2`…) · 7 byte nội dung. Tối đa 48 đơn vị
UTF-16 (96 byte). "Kachi test" = 20 byte, tổng `0x3C0` ⇒ `C0`; "Đường Lê Lợi" = 24 byte, tổng ⇒ `BE`. Script nhúng gói tính sẵn và tự
kiểm từng gói qua allow-list trước khi gửi (`check_packet`: chỉ `0x43E0003A`∈{1,2,4}, `0x43F01018/10/30` 4 byte, `0x43FA1008..0x43FA4008`
đúng số thứ tự; còn lại ⇒ từ chối, không gửi).

## 4. Tên đường — Kachi vs DiAuto

| | Kachi | OEM AmapService / SDK | DiAuto qua ClusterDebug |
|---|---|---|---|
| Id | `0x43FA1008` (+ oversea `0x1F7A1008`) | `0x43FA1008` | `0x43FA1008`, `0x43FA2008`, … (một id mỗi khúc) |
| Dữ liệu | UTF-16LE thô, không BOM (`HudPathName.encode`, `core/.../navigation/HudPathName.kt:37`) + gọi thêm SDK `sendNextPathName` | `getBytes("UnicodeLittleUnmarked")` thô (`BYDAutoInstrumentDevice.java:1696-1700`) | `[len][UTF-16LE][tổng kiểm]`, cắt 7 byte, đệm `FF` |
| Trần | 255 byte (127 đơn vị) | 255 byte (Java) | 96 byte (48 đơn vị) — DiAuto ghi "HAL caps this feature at 96 bytes" |
| Ai cắt khúc | bên dưới Java (`setBuffer` → dịch vụ native) | như Kachi | chính app, vì TestDevice đi vòng qua bộ mã hoá |
| Thứ tự | R9 (2.98): status=2 → tên → mũi tên/cự ly, chỉ khi tên đổi | tên chỉ khi đổi | guidance trước, tên nối sau, cùng gói, gửi lại ≥ 1 s |

Kết luận:
- [ĐO nguồn] Ở ranh giới Java, Kachi gửi **byte-y-hệt** OEM; ghi raw và gọi SDK là cùng một lệnh `set(0x43FA1008, utf16le)`.
- [SUY] Dạng khúc + tổng kiểm của DiAuto là dạng dây mà bộ mã hoá HAL (`encodeReqSplitString`, theo chú thích DiAuto) tạo ra từ chuỗi thô. Mình
  **chưa** thấy bộ mã hoá đó trong fw 2606: `grep encodeReqSplitString|SplitString` trên `firmware/fw-2606/_libs/*` và `_system_framework/*`
  = 0 kết quả (HAL vendor chưa trích) ⇒ [CHƯA BIẾT] trong ảnh của mình.
- [SUY] Vì vậy khác biệt định dạng **không giải thích** việc HUD SL6 hiện mũi tên mà không bao giờ hiện tên: Kachi đi đúng đường OEM, rc=0.
  Nghi phạm còn lại vẫn là cổng tên đường phía cụm/HUD (`0x420A1010` check-state = 0 trên cả hai xe, `factory-hud-nav-RE-avenues` §0.1).
- [ĐOÁN] Ngoại lệ có thể: tên > 48 đơn vị (96 byte) — Kachi cho tới 127; nếu HAL thật trần 96 thì tên dài bị bỏ. Tên đường VN thường < 30
  đơn vị nên khó là nguyên nhân chính. Cách chốt: đọc HAL vendor, hoặc H2 với tên dài (không làm trong buổi này).
- Nếu H2 cho HUD hiện tên qua ClusterDebug trong khi HAL không bao giờ hiện ⇒ cổng nằm ở phía HAL/IVI, không phải định dạng.

## 5. Rủi ro và đường lùi

| Rủi ro | Mức | Chặn bằng |
|---|---|---|
| Bơm nhầm id khác (opcode cụm cấm 1/2/18/41/91/92, theme 30, `wholeFrame`) | — | Không dùng `sendInfo`/opcode; chỉ `normal`; allow-list `check_packet` theo id + độ dài + giá trị |
| Token hex sai ⇒ `NumberFormatException` trong ClusterDebug (priv-app) | [ĐO nguồn `:32`] | Kiểm khuôn `^[0-9A-F]{2}(,[0-9A-F]{2})*$` trước khi gửi |
| Mũi tên/tên kẹt trên cụm/HUD sau buổi thử | [ĐOÁN] | trap EXIT luôn gửi status=1 rồi 4; script in lệnh gửi tay; cuối cùng tắt/nổ máy. 10/10 các giá trị 0x237 trả về được bằng cách bơm lại giá trị gốc [ĐO]. TestDevice không ghi tệp [SUY — không thấy đường ghi bền trong nguồn đã đọc; chưa chứng minh] |
| Kachi và bơm tay "đánh nhau" trên `0x43E0003A`/`0x43F*` | [SUY] | Owner TẮT `Dẫn đường lên cụm đồng hồ` trước H1; không app dẫn đường nào chạy; script chụp `dumpsys activity services` trước/sau |
| Đổi trạng thái cụm làm SurfaceFlinger sập (05/10) | [ĐO lịch sử, khác loại lệnh] | Không đụng theme/display; kiểm pid SurfaceFlinger/system_server/Kachi **mỗi nhịp 1 s**, `logcat -b crash` sau mỗi bước; bất thường đầu tiên ⇒ xoá + thoát (đã thử bằng adb giả: pid đổi ⇒ dừng ở H1, gửi xoá) |
| Xe chạy | — | Cổng owner xác nhận đỗ số P + gõ `GUI` |
| `status=1` (DiAuto) nghĩa khác trên DL3 | [CHƯA BIẾT] | Gửi thêm `4` (giá trị OEM DL3 dùng để kết thúc) |

## 6. Bảng quyết định (kết quả → Kachi làm gì)

| H1 HUD | H1/H1b cụm | Log `receive normal` / `set featureID is aa00020f` | Kết luận | Kachi làm |
|---|---|---|---|---|
| có | bất kỳ | có | HUD Seal nhận nav qua TestDevice ⇒ cổng HUD nằm ở đường HAL/IVI, không ở coding HUD (bác một phần ADR 0002) | Viết spec: transport `ClusterDebugNav` trong `car-integration` (broadcast tường minh, allow-list bản ghi, nhịp 1 s, cờ `pending_clear` vào prefs trước start — §5), **chọn bằng đo**: một phép thử khả năng (receiver tồn tại + version + owner/HUD xác nhận) ghi vào `ClusterProfile` theo đời xe; xếp **cuối** sau HAL (§6). TestDevice là công cụ test ⇒ cần owner duyệt đưa vào sản phẩm |
| không | có | có | Đường này chỉ nuôi datasource cụm (giống 0x237 ngày 10/10) | Không làm gì: cụm đã chạy bằng HAL. Đóng `HUD-CLUSTERDEBUG-PATH` "đo: không mở HUD"; ADR 0002 giữ |
| không | chỉ H1b có | có | DL3 không ghép nhiều bản ghi trong một buffer (khác DL5 của DiAuto) | Như dòng trên; ghi dữ kiện khuôn |
| không | không | có | TestDevice nhận nhưng `0x43E/0x43F` không có người tiêu thụ trên đường mô phỏng | Đóng; ADR 0002 giữ |
| không | không | **không có** | Broadcast không tới receiver (uid shell, tiến trình stopped, hay bị chặn) | Không kết luận được về HUD; [CHƯA BIẾT] — chỉ thử lại từ uid app (cầu kiểm thử Kachi) nếu owner muốn |
| — | — | có `AndroidRuntime`/`NumberFormat`/`denied` | Lỗi phía ClusterDebug/quyền | Dừng, ghi lỗi, không thử lại trong buổi |

Thêm cho H2 (chỉ có nghĩa khi H1 có hiện ở đâu đó):
- H2a có, H2b có đúng dấu ⇒ khuôn khúc dùng được; nếu đi tiếp transport mới thì mã tên đường theo khuôn khúc (chỉ cho transport đó; đường HAL giữ chuỗi thô).
- H2a có, H2b sai/mất dấu ⇒ giới hạn phông/bảng chữ của bộ vẽ, không phải đường gửi.
- H2 HUD có tên mà đường HAL chưa bao giờ có ⇒ cổng tên đường nằm phía HAL/IVI (đóng nghi án định dạng ở §4).
- H3 không sạch ⇒ ghi giá trị status nào xoá được; cập nhật `clear` của transport (nếu có) theo đúng giá trị đó.

## 7. Chạy

```
scripts/oncar/hud-clusterdebug-nav.sh [127.0.0.1:15555] [docs/diagnostics/oncar-hud-cd-<ts>/]
```

- Cầu adb: `docs/diagnostics/adb-car-tunnel-macos.md`. Thư mục ra khớp `oncar-*/` trong `.gitignore` (log xe không vào repo).
- H0 chỉ đọc: `getprop` build, `dumpsys package com.byd.clusterdebug` (versionCode, codePath, cờ SYSTEM, `BYDAUTO_TEST_SET`, stopped, receiver),
  `cmd package query-receivers`, phiên bản + dịch vụ Kachi, `dumpsys notification` (bộ nghe), logcat mốc, pid mốc. ClusterDebug khác 10601004 /
  không SYSTEM / không có receiver ⇒ dừng, không gửi.
- Mỗi bước: một dòng việc cho owner, chờ Enter, gửi, lọc logcat (`ClusterDebug|TestDevice|AbsBYDAutoDevice|CLUSTER_LOG|AndroidRuntime`), đếm
  `receive normal:` và `set featureID is aa00020f`, hỏi c/k HUD rồi cụm, hỏi bất thường. Kết quả gộp ở `summary.tsv`; mọi broadcast ở `broadcast.log`.
- Đã kiểm off-car: `bash -n` + `shellcheck` sạch; chạy hết luồng bằng adb giả (16 broadcast đúng thứ tự, xoá 1 lần); luồng bất thường (pid
  SurfaceFlinger đổi sau start) ⇒ dừng ở H1, gửi `…01,01` + `…01,04`, thoát mã 3. Chưa chạy trên xe.

## 9. Kết quả xe 17/10 (Seal) và đường gửi tên đường thứ hai cho SL6 (3.1.12)

> **Cập nhật**: 2026-10-17 · **Backlog**: `HUD-CLUSTERDEBUG-PATH` (Seal: đóng nhánh HUD) · `HUD-ROADNAME-ALT-PATH` (mới, 🚗 SL6)

### 9.1 Seal, fw 2606 — kết quả

| Đo | Kết quả | Mức |
|---|---|---|
| Gói qua `com.byd.clusterdebug/.BroadcastReceiverCAN --es normal <hex>` có tới TestDevice không | **Có** — `BYDAutoTestDevice.set(0xAA00020F)` nhận, cả gói gộp lẫn từng bản ghi, liên tục > 1 phút | [ĐO xe] |
| HUD Seal hiện gì mới | **Không** | [ĐO owner] |
| Đường HAL của Kachi trên HUD Seal | Không hiện gì (như trước) | [ĐO] |
| Kết luận | HUD Seal khoá bởi coding (ADR 0002 giữ); đường TestDevice không mở được HUD Seal. Theo bảng §6: dòng "HUD không" ⇒ nhánh HUD của Seal đóng | [ĐO] cho Seal · **chưa biết** cho SL6 |

SL6 cùng firmware thì khác: HUD zin SL6 **có** hiện mũi tên + cự ly từ đường HAL của Kachi nhưng **không bao giờ** hiện tên đường [ĐO anh em
SL6]. Tức HUD SL6 không bị khoá coding như Seal, chỉ ô tên đường là không lên. Đó là chỗ đường thứ hai có thể giúp — và chỉ xe SL6 thật mới
trả lời được [CHƯA BIẾT].

### 9.2 Kachi 3.1.12 — bốn chế độ "Đường gửi tên đường lên HUD"

*Cài đặt › Dẫn đường + HUD › Đường gửi tên đường lên HUD*, lưu **theo xe** (`hud_road_name_mode`, `clusternav_prefs`). Mũi tên, cự ly và
trạng thái dẫn **luôn** đi đường HAL như cũ, ở mọi chế độ; chỉ tên đường đổi đường.

| Chế độ | Tên đường đi đâu | Trần | Ghi chú |
|---|---|---|---|
| **A** (mặc định) | HAL `0x43FA1008` + oversea `0x1F7A1008` + SDK `sendNextPathName` | 127 đơn vị UTF-16 | y hệt 3.1.11 |
| **A48** | như A | 48 đơn vị, cắt ở ranh giới code point | trần thử |
| **B** | **chỉ** ClusterDebug: khúc 7 byte + tổng kiểm (khuôn §3, `0x43FA1008 + (i<<12)`) | 48 đơn vị (≤ 14 khúc) | cách DiAuto |
| **C** | A + B | A: 127 · B: 48 | |

Vì sao trần 48 là chế độ riêng (A48), không phải mặc định mới: [ĐO nguồn] OEM AmapService gửi tên **nguyên văn** (`getCodeUnicode` =
`getBytes("UnicodeLittleUnmarked")`, không cắt; `jadx-amap2 … AmapService.java:459-470, 589-598`) và SDK `sendNextPathName` nhận tới 255 byte
(§2.2). Trần 96 byte chỉ có trong chú thích DiAuto [ĐOÁN về HAL]. Đổi mặc định khi chưa có bằng chứng là đổi một đường đang chạy (CLAUDE.md §6).

Khuôn khúc của B đối chiếu thêm với DiAuto 0.3.11 đã decompile (lớp gói tên đường): làm sạch (ký tự điều khiển / khoảng trắng lạ ⇒ cách,
surrogate mồ côi bỏ, cặp surrogate chỉ vào khi còn đủ 2 chỗ), cắt ≤ 48, `trim`, rỗng ⇒ một dấu cách; nội dung `((n + 8) / 7) * 7` byte; số thứ
tự = `(id >>> 12)` [ĐO bytecode]. Bộ mã của Kachi ra **đúng từng byte** hai gói H2a/H2b của script (test `ClusterDebugRoadNameTest`).

### 9.3 An toàn và hiệu năng

- **Phạm vi lệnh (CLAUDE.md §4)**: display — không chạm; app — chỉ `com.byd.clusterdebug/.BroadcastReceiverCAN`, broadcast tường minh;
  loại lệnh — chỉ 14 id khúc tên đường `0x43FA1008..0x43FAE008` (allow-list ở `:core`, kiểm lại **ngay trước** `sendBroadcast`; khúc 15
  `0x43FAF008` và họ kề `0x43FB0008` bị chặn), chỉ extra `normal`, không `wholeFrame`, không trạng thái dẫn, không mũi tên/cự ly qua đường
  này; hoàn tác — hết dẫn (`clearNavFrame`) mà kênh B đã có tên ⇒ gửi tên xoá (một dấu cách, đúng thứ DiAuto gửi cho tên rỗng). Script xe
  dừng ở `0x43FA4008` vì hai chuỗi thử chỉ cần 4 khúc; trần 48 đơn vị cần 14.
- **Khả dụng**: B/C chỉ mở khi PackageManager báo: có gói, app **hệ thống**, đang bật, receiver có + exported + bật + **không** permission, và
  `versionCode` = 10601004 (bản đã đọc source). Đọc mới mỗi lần trang Cài đặt hiện và mỗi lần gửi; kế hoạch của một khung dùng bản ≤ 60 s.
  Thiếu ⇒ chip B/C mờ + dòng lý do; nếu đã chọn B/C rồi mà mất khả dụng ⇒ tự lùi về A, dòng tình trạng màu hổ phách nói rõ.
- **Nhịp**: cùng cổng R9 (2.98) của tên đường: chỉ real push, chỉ khi tên **đổi** (kênh `CLUSTER_DEBUG` riêng trong `HudPathNameGate`);
  keep-alive không đọc prefs, không gửi gì. Không luồng, không nhịp mới. Chế độ A/A48: thêm đúng một lượt đọc prefs mỗi real push.
- **Nhật ký**: một dòng `hudroad sent mode=<m> units=<n> rc=<…>` mỗi lần tên thật sự được gửi (nhật ký lệnh ghi xe, đọc bằng cầu `ctllog`),
  `hudroad mode a->b src=…` khi đổi chế độ. Không bao giờ ghi chữ của tên đường.
- **Chưa biết**: broadcast từ uid của Kachi (không phải uid shell như buổi 17/10) có tới receiver không — [SUY] có (exported, không permission,
  §2.1), chốt bằng dòng `receive normal:` trong logcat trên xe đầu tiên chạy B. Tiến trình Kachi chết giữa chuyến ⇒ tên B có thể còn tới lượt
  dẫn sau / tắt máy — cùng mức với tên của đường HAL.

### 9.4 Đo trên SL6 (người thử)

Hướng dẫn gửi anh em: `docs/_handoff/sl6-hud-roadname-tester-2026-10-17.md`. Cầu kiểm thử (khi có adb):
`am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd hudroad --es op b --ez auto_confirm true`
(`op` = `status|a|a48|b|c`; `b`/`c` cần `auto_confirm`; xe không có ClusterDebug ⇒ `unavailable` + lý do).

| SL6 | Kết luận | Kachi làm |
|---|---|---|
| B hiện tên, A không | Ô tên của HUD SL6 chỉ ăn đường TestDevice; HAL tên đường bị chặn phía IVI/HAL | Đề xuất owner: mặc định B cho xe có ClusterDebug **và** đã đo HUD hiện tên — chọn bằng đo, không theo tên đời xe |
| A48 hiện, A không | Trần thật 48 đơn vị ⇒ tên dài bị bỏ | Đổi trần A về 48 (một hằng) |
| Không chế độ nào hiện | Cổng nằm ở HUD SL6 (`0x420A1010` check-state = 0, `factory-hud-nav-RE-avenues` §0.1) | Giữ A, đóng `HUD-ROADNAME-ALT-PATH` |
| B làm mất mũi tên / HUD lạ | Ghi lỗi | Quay A ngay; ghi bằng chứng |
