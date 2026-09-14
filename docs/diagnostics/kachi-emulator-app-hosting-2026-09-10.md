# Kachi trên emulator — Play Store, dadb loopback & host app vào ô (findings)

> **Trạng thái**: Current · **Ngày**: 2026-09-10 · **Mục đích**: Ghi lại bằng chứng phiên test Kachi trên emulator (cài app qua Play Store + thử mở-app-vào-ô "như trên xe"). Gồm: gốc rễ popup adb lặp, cấu hình loopback đúng, **1 bug thật của launcher (host app lúc mở)** kèm diff sửa, và **giới hạn cứng display phụ**. Nguồn-sự-thật là THIẾT BỊ (lệnh adb/dumpsys) — mọi mục [ĐO] có lệnh kèm.

## Bối cảnh
Owner cài mớ app qua **Play Store** trên emulator rồi thử chạy Kachi làm launcher, mở app vào ô (freeform/VirtualDisplay) "theo cách dùng trên xe". Gặp: popup "Allow USB debugging" lặp mãi · app hiện dạng THẺ không render · đơ khi mở/chiếu · không mở được fullscreen bình thường · layout revert + nút ✕ lạ.

## Finding 1 — [ĐO] Popup adb lặp = image Play Store khoá cứng (artifact emulator, KHÔNG phải xe)
Log adbd (emulator Play): `alwaysAllow=false` + `/data/misc/adb/adb_keys` **rỗng** + `adb client authorized` rồi `read failed: I/O error` ngay sau đó. Kiểm quyết định:
```
adb root                → "adbd cannot run as root in production builds"
adb shell 'su 0 id'     → su: inaccessible or not found
adb shell 'touch /data/misc/adb/_t' → Permission denied (shell uid 2000)
getprop ro.adb.secure=1  ro.debuggable=0  ro.build.type=user
```
⇒ Image có Play Store là **production build khoá cứng**: không root, shell không ghi được `adb_keys` ⇒ **không thể pre-authorize khoá dadb** ⇒ mỗi lần dadb nối lại (probe launcher) đều hỏi lại, và always-allow không lưu được (transport rớt trước khi adbd kịp ghi khoá). **Đây là artifact của Play image**, KHÔNG phải cách xe hành xử (xe có adbd mạng thật trên tcp:5555, lưu always-allow đàng hoàng, không có vòng tự-nối).

## Finding 2 — [ĐO] Loopback đúng cần image `google_apis` (ro.adb.secure=0) + cổng chuẩn
- Image **`google_apis`** có `ro.adb.secure=0` ⇒ dadb nối localhost:5555 **không cần auth, KHÔNG popup**. Xác nhận: mở Kachi → `UsbDebugging windows = 0`, focus = KachiHomeActivity.
- **Bẫy 2-emulator**: emulator thứ 2 lấy cổng console 5556 → **adb ở host:5557** (không phải 5555). `adb -s emulator-5556 reverse tcp:5555 tcp:5555` trỏ nhầm về host:5555 = emulator Play (secure=1) ⇒ probe dadb của launcher trên 5556 **auth thất bại** ⇒ `shell=null` ⇒ chỉ hiện thẻ + đơ. Sửa: hoặc `reverse tcp:5555 tcp:5557` (trỏ đúng adbd của chính nó), hoặc **chạy DUY NHẤT 1 emulator** để nó lấy cổng 5554/5555 chuẩn.
- **2 emulator chạy cùng lúc** cũng gây lag/đơ (tranh GPU/CPU).
- Xác nhận probe THÀNH CÔNG trên google_apis đơn: `adb shell appops get com.byd.launcher SYSTEM_ALERT_WINDOW` → **allow** (launcher chỉ set appops này qua seam dadb KHI probe OK) ⇒ loopback chạy thật, `embedding=true`.

**Recipe emulator test app-vào-ô (1 emulator, sạch):**
```
# boot DUY NHẤT google_apis (clusternav10) → nó lấy 5554/5555
emulator @clusternav10 -no-snapshot -netdelay none -netspeed full &
adb reverse tcp:5555 tcp:5555
adb shell settings put global enable_freeform_support 1
adb shell settings put global force_resizable_activities 1
adb shell cmd package set-home-activity com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity
adb shell am start -n com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity
```
Cài app từ Play emulator sang: `adb -s <play> shell pm path <pkg>` → `pull` (gồm split) → `adb -s <googleapis> install-multiple -r -g <*.apk>`.

## Finding 3 — [ĐO][BUG launcher] App trong ô KHÔNG host lúc mở, phải đổi preset mới host
**Triệu chứng**: mở Kachi (đã có app lưu trong ô) → chỉ hiện **thẻ** (icon+tên), dumpsys display chỉ Display 0, **0 VirtualDisplay**. Đổi preset (chạm chip layout) → **VirtualDisplay `kachi-slot-*` tạo ngay + app launch** (xác nhận `dumpsys display` có 3 VD 1132×768 + 2×731; `dumpsys activity` có task app trên stack VD).

**Gốc rễ [ĐO code]**: `WorkspaceView.render()` cập nhật TĂNG DẦN, chỉ dựng lại ô khi **nội dung** đổi (`sameContent`). Luồng:
1. `onCreate` render lần đầu lúc `shell=null` (probe dadb chạy NỀN chưa xong) → ô App dựng **thẻ** (makeSlot nhánh `shell==null`, không gắn `VdAppHost`).
2. Probe dadb xong (LUÔN sau render đầu) → set `workspace.shell = seam` → gọi `workspace.render(state cũ)`.
3. Nội dung ô KHÔNG đổi (cùng pkg) ⇒ `sameContent==true` ⇒ **không dựng lại ô** ⇒ `VdAppHost` **không bao giờ được gắn** ⇒ app không host.

Đổi preset ép `rebuild()` (preset đổi) ⇒ makeSlot chạy lại lúc `shell!=null` ⇒ gắn VdAppHost. **Bug này ảnh hưởng CẢ XE** (probe dadb cũng async sau render đầu).

**Fix đã kiểm (2 dòng) — [ĐO] VD tạo ngay khi mở sau fix — nhưng REVERT để làm đàng hoàng (spec/test/senior review), chưa commit:**
```diff
# WorkspaceView.kt — thêm hàm công khai ép rebuild
+    /** Ép dựng lại TOÀN BỘ ô khi shell dadb sẵn sàng SAU render đầu (shell=null chỉ dựng thẻ) → gắn VdAppHost. */
+    fun rebuildSlots() = rebuild()
+
     private fun rebuild() { ... }

# KachiHomeActivity.kt — trong block probe-success (runOnUiThread), sau khi set shell/inputClient:
-                    workspace.render(viewModel.uiState.value.workspace, viewModel.uiState.value.carStatus)
+                    workspace.rebuildSlots()   // ô App đã có sẵn (render đầu shell=null) nay được gắn VdAppHost
```
Làm lại đúng quy trình: viết test (WorkspaceView không gắn host khi shell=null → gắn sau rebuildSlots) + senior review + verify on-car. **KHÔNG commit fix ad-hoc này.**

## Finding 4 — [ĐO][GIỚI HẠN] App thật từ chối display phụ → nhảy fullscreen (emulator không test được app-vào-ô cho nhóm này)

> **⚠ SỬA SAI 2026-09-14 — phần "Nguyên nhân" của Finding 4 ĐÃ BỊ BÁC BỎ.** Triệu chứng (Waze nhảy fullscreen) là thật, nhưng quy kết *"app không khai hỗ trợ đa-màn + màn ảo PRIVATE"* **sai cả hai vế**: Waze khai `resizeableActivity=true`; cờ riêng-tư không nằm trên đường quyết định. Gate thật = `ActivityStackSupervisor.isCallerAllowedToLaunchOnDisplay` (`android-10.0.0_r47:1096-1106`) — **`FLAG_ALLOW_EMBEDDED` của activity ĐÍCH khi lời gọi đến từ uid CỦA APP**. Google Maps **ở lại màn ảo bình thường**. Xem `waze-into-slot-research-2026-09-14.md`.

Sau khi VdAppHost gắn + launch, **Waze render THẬT nhưng bị đẩy FULLSCREEN trên display chính**, kèm toast **"App does not support launch on secondary displays"** (sub-agent đọc screenshot xác nhận). `force_resizable_activities=1` KHÔNG ép được trên emulator sideload.
- **Nguyên nhân**: app không khai báo hỗ trợ đa-màn + VirtualDisplay của VdAppHost là **PRIVATE** (`FLAG_PRIVATE|FLAG_OWN_CONTENT_ONLY`), launcher sideload **không platform-signed** ⇒ launch app bất kỳ lên display phụ bị từ chối.
- **Trên XE**: Kachi chạy như **launcher hệ thống platform-signed** (như Dudu) + display tin cậy ⇒ app host vào ô được. Đây là lý do P3.1 ghi "verify trên xe".
- **Nhóm hoạt động trên emulator**: app hỗ trợ đa-màn (Clock, và gmaps/VietMap theo [ĐO] P3.1 cũ). **Nhóm từ chối**: Waze, Google Maps (app nav nặng) → nhảy fullscreen.
- ⇒ **Emulator tốt để test UI/widget/dữ liệu-xe-W1/nút-điều-khiển/Tuỳ-biến; app-vào-ô cho app từ chối display phụ = verify TRÊN XE.**

## Finding 5 — [ĐO] GMaps/YouTube trên google_apis là bản 2019 (GMS cũ)
So version: GMaps 5556=**10.16.7** (2019 system) vs Play=26.37; YouTube 5556=**15.18** vs Play=21.35; YT Music + Waze đã là bản mới (cài từ Play emulator). Copy bản mới từ Play emulator được, nhưng **GMS trên google_apis là 2019** ⇒ GMaps/YouTube đời mới dễ nhắc cập nhật/lỗi; và GMaps/Waze **từ chối ô** (Finding 4) nên cập nhật cũng không đưa vào ô được.

## Finding 6 — [ĐO] Icon: 20 nút gốc CÓ icon riêng; ~45 nút W1 mới xài fallback
Sub-agent đọc dock: 8 nút mặc định (khoá/kính/cốp/đèn/lá/ghế/nhiệt/quạt) có icon line-art riêng, KHÔNG generic. Thiếu icon là ~45 nút MỚI (nóc/rèm/gương/đèn pha/ADAS/sạc...) trong picker Tuỳ biến → backlog **U1**.

## Finding 7 — [ĐO] Chấm cam = badge "chưa kiểm trên xe" (đúng thiết kế R3)
Chấm cam trên Khoá xe/Đèn đọc/nhiệt/quạt = evidence-tier badge (`EvidenceTier.needsBadge = OVERDRIVE||DASHCAST`): tính năng nối từ nguồn Overdrive tham chiếu, **chưa verify trên trim xe**. Cái proven-on-car (ghế/PM2.5) không có chấm.

## Kết luận
- Popup + đơ + revert + ✕ = **artifact cấu hình emulator** (Play image khoá cứng / loopback trỏ nhầm cổng / 2 emulator lag / probe-async không gắn host), KHÔNG phải bug logic W1.
- **1 bug thật** (Finding 3, ảnh hưởng cả xe) — đã có diff, làm lại đúng quy trình.
- **1 giới hạn cứng** (Finding 4) — app-vào-ô cho app từ chối display phụ chỉ verify được trên xe.
