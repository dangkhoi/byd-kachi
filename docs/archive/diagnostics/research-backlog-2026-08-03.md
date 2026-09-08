# Research Backlog — 2026-08-03

## 1. SL6 split cast: cả 2 app đều full, không chia đôi

### Triệu chứng
- Xe Sealion 6 (12.8 inch, màn to hơn Seal 10.8 inch)
- Cast trái, phải → cả 2 app đều lên FULL màn, đè lên nhau
- Trên Seal: split trái/phải work ngon

### Chẩn đoán (từ log V2 cũ)
```
mode=READ_ONLY
observation=Known(
  coarseState=ACTIVE_MULTI,
  displayIdentity=display-1,
  target=CastTarget(packageName=com.google.android.apps.maps, taskId=37, displayId=1),
  occupants=[com.google.android.apps.maps, vn.vietmap.live, com.byd.clusternav],
  taskBounds={
    37=CastRect(left=0, top=0, right=1920, bottom=800),
    39=CastRect(left=0, top=0, right=1920, bottom=800),
    36=CastRect(left=0, top=0, right=1920, bottom=800)
  }
)
```

### Phân tích
- Cả 3 task đều bounds `[0, 0, 1920, 800]` = FULL display → không split
- SL6 dùng CHUNG firmware với Seal (DiLink3, Android 10)
- Cast FULL work → display ID đúng, freeform sống
- Log có `mode=READ_ONLY`, `observation=Known(...)`, `store=EMPTY` → đây là **V2 code cũ** chạy trên SL6
- V2 code CÓ split logic KHÁC simplified code (dùng CastPlacementCommands FIT_CLUSTER_COMPOSITE)
- Khả năng cao: SL6 chưa cài bản simplified mới → split fail là bug của V2, không phải simplified
- Nếu đúng: cài bản simplified mới lên SL6 → test lại split → có thể work luôn

### Cần làm (không cần xe)
1. Xác nhận: SL6 đang chạy version nào? V2 hay simplified?
2. Nếu V2: cài bản simplified mới (đã proven split trên Seal) → test
3. Nếu simplified mà vẫn fail: thêm logging vào fitToCluster() → gửi APK, nhờ test, đọc log

### Lý thuyết
Log chẩn đoán từ V2 pipeline (có observation/store/mode). Simplified code mới đã bỏ hết V2, dùng trực tiếp `am task resize`. Trên Seal split work → cùng firmware SL6 cũng phải work nếu chạy cùng code.

---

## 2. Autostart split: chọn app trái + phải tự động

### Hiện trạng
- Đang có 1 spinner autostart cho FULL cluster (1 app)
- Muốn thêm: autostart trái + autostart phải (2 app cùng lúc)

### UX logic
- 3 dòng trong cài đặt:
  - Autostart Full: [dropdown app] — cast 1 app chiếm toàn cụm
  - Autostart Trái: [dropdown app] — cast vào slot trái
  - Autostart Phải: [dropdown app] — cast vào slot phải
- **Constraint:** Full vs Trái/Phải loại trừ nhau:
  - Chọn Full → disable Trái/Phải
  - Chọn Trái hoặc Phải → disable Full
  - Trái + Phải chọn cùng lúc OK (start 2 app split)
  - Cùng app cho Trái và Phải → cần validate? Hay cho phép (user tự chịu)?

### Implementation (SimpleCastPrefs)
```kotlin
// Thêm vào interface:
fun autoStartLeftPackage(): String?
fun setAutoStartLeftPackage(pkg: String?)
fun autoStartRightPackage(): String?
fun setAutoStartRightPackage(pkg: String?)
fun autoStartMode(): AutoStartMode  // FULL, SPLIT, OFF
fun setAutoStartMode(mode: AutoStartMode)

enum class AutoStartMode { OFF, FULL, SPLIT }
```

### Flow khi boot
```
if (autoStartMode == FULL) {
    dispatch(CastFull(autoStartPackage))
} else if (autoStartMode == SPLIT) {
    autoStartLeftPackage?.let { dispatch(CastSlot(it, LEFT)) }
    autoStartRightPackage?.let { dispatch(CastSlot(it, RIGHT)) }
}
```

### Effort: ~1h (prefs + UI + boot logic)

---

## 3. VietMap HUD Bluetooth: sniff speed limit / nav signal

### Bối cảnh
- VietMap bán cục HUD kết nối qua Bluetooth
- Pair vào app VietMap trên xe → hiển thị: dẫn đường, ETA, cảnh báo tốc độ (speed limit)
- Bên thứ 3 cũng bán mini HUD pair từ VietMap → lấy tín hiệu hiển thị
- Tức là protocol KHÔNG phải proprietary bí mật — có thể mò được

### Mục tiêu
ClusterNav giả lập pair/handshake với VietMap để lấy:
- **Speed limit** hiện tại (50/60/80/120 km/h)
- **Hướng dẫn dẫn đường** (rẽ trái 200m, vòng xuyến...)
- **ETA** (còn bao lâu tới đích)

→ Hiển thị lên cluster overlay (SYSTEM_ALERT_WINDOW trên display 1) hoặc HUD

### Hướng nghiên cứu

#### A. Sniff Bluetooth protocol
1. Pair cục HUD VietMap với xe → bật Bluetooth HCI snoop log
2. `adb pull /data/misc/bluetooth/logs/btsnoop_hci.log`
3. Mở bằng Wireshark → filter SPP/RFCOMM/BLE GATT
4. Quan sát: data format (JSON? binary? protobuf?)
5. Identify: service UUID, characteristic UUID (nếu BLE), RFCOMM channel (nếu classic)

#### B. Decompile VietMap APK
1. `jadx vietmap.apk` → tìm Bluetooth service class
2. Grep: `BluetoothGatt`, `BluetoothSocket`, `UUID`, `RFCOMM`
3. Tìm data format gửi ra HUD: serialization, field names
4. Identify: handshake sequence, auth (nếu có)

#### C. Reverse từ thiết bị bên thứ 3
- Tìm mua/mượn mini HUD bên thứ 3 kết nối VietMap
- Pair → sniff → compare với cục chính hãng
- Nếu giống = protocol chuẩn, dễ replicate

### Khả thi?
- **Cao**: bên thứ 3 đã làm được → protocol không quá bí mật
- **Rủi ro**: VietMap update app → đổi protocol → break
- **Effort**: 2-4h sniff + reverse, 2-4h implement pair/read

### Deliverable
- `core/.../hud/VietMapBtBridge.kt` — pair + read speed limit + nav
- Hiển thị lên cluster overlay hoặc expose qua broadcast cho HUD riêng

---

## 4. Display cự ly: bỏ bậc 25 m ở dải 300 m–1 km → số tròn như GMaps

> Added 2026-08-14 PM (owner note, phiên phân tích chuyến lái về). **Làm sau, không gấp.**

### Triệu chứng
- Cụm thỉnh thoảng hiện số lẻ kiểu **725 m** → nhìn kỳ. GMaps toàn số **tròn** (10/20/50 m hoặc "0,7 km").

### Nguồn (đã neo code)
- `core/src/main/kotlin/com/byd/clusternav/navigation/NavParse.kt` → `quantizeDisplay()`:
  ```kotlin
  m >= 1000 -> ((m + 50) / 100) * 100   // >1km: bậc 100m  (OK, tròn)
  m >= 300  -> ((m + 12) / 25)  * 25     // 300m–1km: bậc 25m  ← THỦ PHẠM (725 = 29×25)
  m >= 100  -> ((m + 5)  / 10)  * 10     // 100–300m: bậc 10m  (OK)
  else      -> ((m + 5)  / 10)  * 10     // <100m: bậc 10m     (OK)
  ```
- Chỉ dải **300 m–1 km (bậc 25 m)** tạo số lẻ (725/775…). Các dải khác đã tròn.

### Lưu ý (đừng nhầm với report)
- **KHÔNG phải lỗi độ chính xác.** Report `docs/diagnostics/interp-factor-drive-analysis-2026-08-14-pm.md` cho `display − screen` median **0** — tức số ĐÚNG ~giá trị. Đây thuần **thẩm mỹ/banding**: bậc 25 m sinh số nhìn lẻ so với banding của GMaps. (Sai số ±25 ở dải này là thiểu số, đối xứng → không dời median, nhưng mắt vẫn thấy 725 kỳ.)

### Cần làm (off-car, không cần xe)
1. Xác định GMaps phát bậc nào ở 300 m–1 km bằng **cột `screenRead_m` của log đã có** (`docs/diagnostics/nav-logs/commute-2026-08-14-pm.csv`) — 50 m? 100 m? hay đổi sang "0,x km"?
2. Đổi nhánh `m >= 300` cho khớp: ứng viên (a) bậc **50 m** (700/750/800); (b) chuyển **km 1 chữ số thập phân** ("0,7 km") ở ≥ ngưỡng nào đó; (c) khác — theo dữ liệu bước 1.
3. Chỉ sửa nhánh 25 m; **giữ nguyên** các dải đã validate. Update `NavParseTest.kt`.

---

## 5. Mũi tên rẽ: GMaps đa dạng hơn cụm — "mình lấy thiếu" hay "HAL chỉ nhận số ít"?

> Added 2026-08-14 PM (owner note). **Làm sau.** Cần RE + 1 lần đo trên xe.

### Triệu chứng
- GMaps có **nhiều** loại mũi tên (fork, roundabout nhiều nhánh, slight/sharp, merge, ramp, keep left/right…). Cụm mình hiển thị **ít** loại hơn. Nghi 1 trong 2: **(a)** ta map thiếu / collapse nhiều loại về 1 icon; **(b)** HAL/AMAP của xe chỉ render được số ít loại.

### Kiến trúc liên quan (đã neo code)
- `core/.../navigation/Maneuver.kt` — enum **Maneuver trung lập**; 2 encoder: `toAmapIcon()` (làn cụm, AMAP NEW_ICON 0..28) + `toHudIcon()` (HUD, mã CAN).
- `core/.../navigation/ManeuverRegistry.kt` — **38 chữ ký maneuver GMaps** (tự sinh từ Open BYD 2.3 `w40.java`).
- `core/.../navigation/ManeuverSignature.kt` — classify frame (Hamming ≤18 bit) → tên → AMAP.
- `app/.../IconResource.kt` (`NAME_TO_AMAP`) + `core/.../navigation/NavFormat.kt` (`maneuverVerbIcon`, map từ chữ).
- `app/.../AmapFrameBuilder.kt` — NEW_ICON index **0..28**; HAL tự remap CAN qua **`TurnIdMapToCAN`**.

### Cần làm
1. **Off-car — đếm diversity mỗi tầng:** distinct outputs của `Maneuver` enum & `toAmapIcon()`; số icon phân biệt trong 38 signatures (`ManeuverRegistry`); tập AMAP NEW_ICON 0..28 thực sự dùng. Tìm chỗ **mất diversity** (enum thiếu? mapping collapse nhiều→1? hay trần 0..28?).
2. **RE (`~/Library/Caches/clusternav-re/`)**: đọc `AmapService` + `TurnIdMapToCAN` xem CAN table cụm render **được** bao nhiêu icon, loại nào bị gộp/rớt.
3. **On-car (sau, 1 lần):** cho GMaps phát loại mũi tên "lạ" (fork/roundabout-N/merge) → `getraw` xem cụm nhận mã nào + có render không.
4. **Kết luận & hành động:** nếu (a) ta thiếu/collapse mà HAL có → **enrich mapping** (thêm Maneuver + toAmapIcon/toHudIcon, giữ hợp đồng "một quyết định, hai đầu ra" trong `ManeuverTest`). Nếu (b) trần HAL → **document giới hạn thiết bị** (không sửa được), liệt kê loại nào cụm không có.

### Ví dụ thực tế (2026-08-14, owner gửi ảnh on-car)
- **GMaps**: icon **merge / nhập làn** (mũi tên thẳng + 1 nhánh nhập từ dưới-trái) → "Tôn Đức Thắng", 1,1 km.
- **Cụm**: hiện **mũi tên rẽ/chếch PHẢI** — không phải merge → cảm nhận SAI hướng.
- **Root (đã trace code):** enum `Maneuver` KHÔNG có MERGE/FORK/RAMP — **cố ý gộp merge/fork → SLIGHT** (comment `Maneuver.kt`). Cụ thể `ManeuverSignature.kt:230` `name.contains("merge") -> 5` (=SLIGHT_RIGHT, AMAP icon 5); `IconResource.kt` `merge_left→4, merge_right→5`. ⇒ cụm vẽ icon 5 = mũi tên phải.
- **Nhận xét:** merge ≈ "đi thẳng khi có đường nhập" → map sang SLIGHT_RIGHT (rẽ phải) là lựa chọn **tệ**; nếu chưa có glyph merge riêng, map `merge → STRAIGHT(9)/CONTINUE(20)` đã trông đúng hơn hẳn.
- **Fix 2 tầng:**
  1. **(nhanh, off-car)** đổi rule `merge → STRAIGHT/CONTINUE` thay vì slight-right ở `ManeuverSignature` + `IconResource` (+ test). Ít nhất hết cảnh "merge hiện thành rẽ phải".
  2. **(đúng)** RE bảng **AMAP NEW_ICON 0..28** (`AmapService`/`TurnIdMapToCAN`) tìm glyph **merge/fork/ramp** riêng → thêm `Maneuver.MERGE` (+ `toAmapIcon`/`toHudIcon`) map thẳng; nếu 0..28 KHÔNG có glyph merge → giữ STRAIGHT (xác nhận HAL trần cho merge).
