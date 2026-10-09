# R7 — quét app thoát ô + VietMap mod nguội vào ô (máy ảo, 2026-10-09)

> **Trạng thái**: Evidence · **Cập nhật**: 2026-10-09 · **Chủ**: dangkhoi · **Liên quan**: spec `docs/specs/kachi-298-plan.html` R7 · §6 (e)(f) ·
> Reviewer Log Pass 7 · `docs/diagnostics/oncar-freeform-waze-2026-10-09.md` §3b · backlog `SLOT-ESCAPE-POLICY`.
> Máy ảo `emulator-5556` (AVD `kachi_play`, Android 10, 1920×1080 @240dpi), Kachi **vehicleTest 2.98 (201)** (bản cây làm việc, chưa commit).
> Không chạm xe. Không sửa mã. Ảnh chụp/log thô để ở scratchpad phiên (không đưa vào repo — có tên app/tài khoản máy ảo).

Owner 09/10 hỏi: *"R7 là cho mọi app không chịu ở trong ô, không riêng Waze? — tự test đi"* và *"test cả VietMap mod: lúc khởi động
nguội VietMap báo không đưa vào ô được, nhưng khởi động xong tới màn bản đồ thì lại vào ô được; không hiểu sao"*.

## 0. Cách đo

- Đặt app vào ô 1 (bố cục 1 ô, khung ô trên display 0 = `[19,89][1901,985]`) bằng cầu kiểm thử `am broadcast … --es cmd slot --ei n 1 --es pkg <gói>`
  (= `assignAppToSlot`, cùng đường lưu ô như người dùng). Trước mỗi app: `am force-stop <gói>` (khởi động nguội), xoá logcat.
- Chờ 35 s rồi đọc: `am stack list`, `logcat -b events` (`am_create_activity`/`am_focused_stack … launchToSide`/`am_crash`),
  `logcat` (`ActivityTaskManager START … from uid`, `Failed to put TaskRecord … on display N`, `KachiVd`, `KachiEscape`, `KachiSlotLife`),
  `dumpsys activity activities` (task: `intent=` gốc + số `Hist`), dấu `kachi_slot_escape` (`clusternav_state.xml`).
- Đưa-lại-lên (R7 thật): 3 × `input keyevent HOME`, mỗi lần chờ 4 s ⇒ Kachi `onResume` ⇒ `reconcile … refront` (lệnh sản phẩm
  `am start --display 0 -a MAIN -c LAUNCHER -n <root>`). Đếm `Hist` + pid trước/sau, đếm sự kiện của task trong `events`.
- So `am task focus <id>`: tạm `settings put global enable_freeform_support 0` (⇒ `SlotEscape.codes()==null`, R7 không đối chiếu; freeform
  vẫn hiệu lực vì cờ chỉ đọc lúc `systemReady`), 3 × (HOME → `am task focus <id>`), đọc stack đỉnh display 0 + khung + `Hist`; trả cờ = 1.

## 1. Bảng quét (Phần A) — 21 app, khởi động nguội

| App (gói) | Kết quả lần đầu | Activity gây thoát (do CHÍNH app mở) | R7 nhận? | Ghi chú |
|---|---|---|---|---|
| Waze `com.waze` | **THOÁT** | `MainActivity` (Waze tự mở, `has extras`) | ✅ `adopt … ⇒ OK`, cùng pid | như §3b |
| Chrome `com.android.chrome` | **THOÁT + CRASH** | `FirstRunActivity` | ❌ không kịp (sập 0,8 s sau) | `am_crash` "64-bit native library" ×2 — lỗi gói Chrome trên máy ảo [ĐO], không do Kachi |
| Gmail `com.google.android.gm` | **THOÁT** | `welcome.WelcomeTourActivity` (+ `OsVersionNudgeActivity`) | ✅ OK, cùng pid | |
| Messages `com.google.android.apps.messaging` | **THOÁT** | `BugleExpressSignInActivity` (`act=VIEW`) | ✅ OK | |
| Photos `com.google.android.apps.photos` | **THOÁT** (chỉ lần đầu) | `permissioncontroller/.GrantPermissionsActivity` — hộp thoại quyền của HỆ, vào CHUNG task Photos | ✅ OK | lần 2 (đã trả lời quyền): **Ở LẠI** ô |
| Drive `com.google.android.apps.docs` | **THOÁT** | `NavigationActivity` (trampoline `NewMainProxyActivity` → app tự mở) | ✅ OK | |
| Meet `com.google.android.apps.tachyon` | **THOÁT** | `MeetOnboardingActivity` → `ExpressSignInActivity` | ✅ OK | |
| Camera `com.android.camera2` | **CRASH ×2 rồi THOÁT** | `PermissionsActivity` | ✅ OK, rồi app sập lại khi HOME | NPE `CameraActivity` — máy ảo [ĐO]; dấu ⇒ `dormant`, sau dọn bằng `release` |
| YouTube, Maps, Play Store, Settings, Files, Calendar, Clock, Contacts, YT Music, Google (search), Play Music, Wallpapers, Kiki (Zalo) | **Ở LẠI** ô (task trên màn ảo, 1 Hist) | — | không cần | không app nào đen/FLAG_SECURE ([ĐO] `dumpsys window windows` không cửa sổ nào cờ SECURE) |
| VietMap mod 3.4.3 (Phần B) | xem §3 | | | |

Bỏ qua: Dialer, Kachi. Không có app nào trong máy ảo khai `allowEmbedded`.

**Cơ chế chung** [ĐO log hệ, 8/8 app thoát]: app được Kachi mở vào màn ảo bằng uid 2000 (shell — được phép), rồi **chính app**
`startActivity` một activity thứ hai (của nó hoặc của gói khác, KHÔNG `NEW_TASK` ⇒ vào cùng task) ⇒ hệ ghi
`W ActivityTaskManager: Failed to put TaskRecord{#N …} on display <vd>` ⇒ `am_focused_stack … launchToSide` ⇒ **cả task** về display 0
toàn màn (khớp AOSP Q `ActivityStackSupervisor` từ chối activity không `allowEmbedded` lên màn ảo chủ ≠ SYSTEM — evidence 14/09).
Phần lớn là màn **chào/đăng nhập/xin quyền lần đầu** ⇒ [SUY] cùng app ấy, khi đã qua các màn này, mở vào ô sẽ ở lại (Photos đã đo ✅).

⇒ **Trả lời owner**: R7 là cho **mọi** app thoát ô, không riêng Waze [ĐO]: 7/7 app thoát còn sống đều được nhận vào đúng khung ô
`[19,89][1901,985]`, cùng pid, lớp che cùng 6 vùng (`CAPTION[19,89][1901,154] · STATUS[0,0][1920,36] · 4×EDGE`), không rẽ theo tên gói.
Thời gian nhận: 10–14 s sau khi mở (2 nhịp đo `ELSEWHERE_SWEEPS` × 5 s + lệnh) [ĐO log].

## 2. Đưa lại lên: `am start MAIN/LAUNCHER` (sản phẩm) vs `am task focus` (ứng viên)

| App | `intent=` gốc của task | HOME ×3 → R7 `am start` (sản phẩm) | `am task focus` ×3 (R7 tắt) |
|---|---|---|---|
| Waze | MAIN/LAUNCHER (Kachi mở) | ✅ đúng khung, cùng pid, Hist 1→1; events: chỉ `am_resume_activity` ×3 | ✅ Waze lên trên KachiHome (cửa sổ #9 trên #10), khung giữ, chỉ `am_resume_activity` |
| Gmail | MAIN/LAUNCHER | ✅ Hist 1→1, cùng pid, chỉ resume | ✅ lên đỉnh, `[19,89][1901,985]`, chỉ resume |
| Messages | MAIN/LAUNCHER | ✅ Hist 4→4, chỉ resume | ✅ lên đỉnh, Hist 4→4 |
| Drive | MAIN/LAUNCHER | ✅ Hist 2→2, chỉ resume | ✅ lên đỉnh |
| Meet | MAIN/LAUNCHER | ✅ Hist 4→4, chỉ resume | ✅ lên đỉnh |
| VietMap (singleTask) | MAIN/LAUNCHER | ✅ Hist 1→1, nhưng **`am_new_intent` ×3** (app nhận `onNewIntent` mỗi HOME) | ✅ lên đỉnh, chỉ resume, không `new_intent` |
| Photos | — | không áp (lần 2 ở lại ô) | — |
| Camera | — | app sập (máy ảo) ⇒ không đo được | — |
| **Messages — task do app tự mở** (dựng tay: `am start -n …ConversationListActivity`, base intent `{flg=0x10000000 cmp=…}` KHÔNG MAIN/LAUNCHER, đặt freeform đúng khung) | không MAIN/LAUNCHER | ❌ **mỗi lần `am_create_activity` ConversationListActivity MỚI** (+ app tự mở thêm `BugleExpressSignInActivity`) ⇒ **Hist 2 → 8** sau 3 lần, cùng pid | ✅ Hist 8 → 8, chỉ `am_resume_activity` ×3, lên đỉnh, khung `[19,89][1901,985]` giữ |

- [ĐO] Mối lo Pass 7 [P2] **là thật**, không còn [SUY]: task có base intent ≠ MAIN/LAUNCHER + root không tự dọn ⇒ `am start MAIN/LAUNCHER`
  chồng instance mới mỗi HOME (AOSP Q `ActivityStarter.setTaskFromIntentActivity`, `!isSameIntentFilter`). Với task do **Kachi** mở nguội
  (`VdAppHost`: `am force-stop` + `am start --display <vd> -a MAIN -c LAUNCHER`) base intent là MAIN/LAUNCHER ⇒ chỉ resume (đo 6/6 app).
- [ĐO] Kachi có đường mở **không** MAIN/LAUNCHER: lần đặt lại VietMap đang sống vào ô, log hệ `START u0 {flg=0x10000000 cmp=vn.vietmap.live/.MainActivity} from uid 2000`
  (đường K8/`openLive`). [SUY] nếu đường này dựng task MỚI (tiến trình sống, không task) hoặc app được mở bằng VIEW (dẫn đường giọng nói
  → Waze/Maps), task đó thoát ô sẽ có base intent ≠ MAIN/LAUNCHER ⇒ rơi đúng ca chồng instance. Chưa dựng được ca này qua Kachi trên máy ảo.
- [ĐO] `am task focus` trên A10 (5/5 app thật + ca dựng tay): đưa đúng task freeform lên trên màn nhà, giữ khung, không create/new_intent;
  với VietMap còn tránh được `onNewIntent` thừa. Không ném lỗi trên task thường; task không tồn tại ⇒ `IllegalArgumentException` (đo khi
  id rỗng) ⇒ cần bắt lỗi như mã 89.
- Khuyến nghị (không đổi mã — owner quyết, CLAUDE.md §14): đổi lệnh đưa-lại-lên sang `am task focus <taskId>` **sau một lượt xe** đọc
  `logcat -b events` xác nhận chỉ `am_resume_activity` + cửa sổ app nằm trên `KachiHome` (`dumpsys window windows`). Có taskId sẵn trong
  dấu (`EscapeMarker.task`, cập nhật ở `Keep`) ⇒ không thêm lệnh đọc. Nếu xe không cho/khác kết quả: giữ `am start` nhưng chỉ khi
  `intent=` gốc của task là MAIN/LAUNCHER (cần 1 lần `dumpsys activity activities` — đắt hơn).

## 3. VietMap mod nguội vào ô (Phần B)

APK: `VietMap-3.4.3-mod-cluster-v2.apk` (sha256 `bddc0be7…`, cài được, chạy được trên máy ảo arm64). `aapt dump xmltree`:
`MainActivity` (Flutter, **launchMode=singleTask**, không `allowEmbedded`, không `taskAffinity` riêng, không `documentLaunchMode`);
activity khác (`MimiAssistantActivity` affinity `.LauncherTask` singleTask, Facebook/Google sign-in, uCrop…) không chạy lúc khởi động.
Kachi tự miễn pin khi cài (`AppPrereqs: package-added: vn.vietmap.live … ghi=DOZE_EXEMPT … → ĐỦ`) [ĐO].

| Ca | Trình tự đo (`logcat` + `am stack list` mỗi 3–15 s, 75 s) | VietMap ở đâu | Kachi |
|---|---|---|---|
| a. đủ quyền vị trí, ĐÃ miễn pin | `START MAIN/LAUNCHER from uid 2000` → không activity nào khác | **Ở LẠI** ô suốt 75 s (bản đồ, chưa đăng nhập) | không log thoát |
| c. đủ quyền, **CHƯA** miễn pin (`deviceidle whitelist -`) | +1,5 s VietMap `START act=android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS dat=package:vn.vietmap.live flg=0x10000000 cmp=com.android.settings/.fuelgauge.RequestIgnoreBatteryOptimizations` → `Failed to put TaskRecord{#355 A=com.android.settings …} on display 33` → task **riêng** của Settings, toàn màn display 0, hộp "Let app always run in background?" đè lên Kachi | VietMap **vẫn ở trong ô** (task 354 trên màn ảo) suốt | không thoát, không nhận (đúng); Deny ⇒ hộp đóng, VietMap vẫn trong ô |
| d/e. ĐÃ miễn pin, **CHƯA** cấp vị trí (`pm revoke`) | +3,7 s VietMap mở `permissioncontroller/.GrantPermissionsActivity` (KHÔNG NEW_TASK ⇒ vào task VietMap) → `Failed to put TaskRecord{#356 A=vn.vietmap.live …} on display 33` → **cả task VietMap** về display 0 toàn màn | **THOÁT** ô | +10 s `KachiVd … app RA KHỎI ô` → `adopt vn.vietmap.live … ⇒ OK` + lớp che; hộp quyền hiện trong khung ô; Allow ⇒ màn "Sử dụng vị trí / Bật Vị trí" của VietMap, vẫn freeform đúng khung; HOME ×3 ✅ |
| f. mở lại VietMap đang sống (đã qua quyền) vào ô | `START {flg=0x10000000 cmp=…MainActivity} from uid 2000` → task 357 dời sang màn ảo `[0,0][1882,896]` | **Ở LẠI** ô | — |

**Vì sao "nguội thì không vào, xong rồi thì vào"** — tách mức:
- [ĐO máy ảo] Một app chỉ bị đá khỏi ô khi **chính nó** mở activity thứ hai vào CÙNG task (ca d: hộp quyền của hệ). Khi màn đó không còn
  được mở (đã cấp quyền / đã qua chào) thì mở vào ô lần sau ở lại (ca f, Photos). Đây là lý do "khởi động xong tới màn bản đồ thì vào được".
- [ĐO máy ảo] Hộp xin miễn pin VietMap gọi với `NEW_TASK` ⇒ nằm ở task riêng của gói hệ trên display 0; VietMap **không** rời ô —
  nhưng người lái thấy một hộp thoại toàn màn đè Kachi ngay lúc khởi động.
- [SUY, khớp memory 05/10] Trên xe ROM không có `com.android.settings`; `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` rơi vào CarSetting
  `UnsupportActivity` với chữ **"Hệ thống IVI không hỗ trợ hoạt động này"** — owner rất có thể đọc câu đó là "không đưa vào ô được",
  trong khi VietMap vẫn ở trong ô phía sau hộp. Bấm OK ⇒ thấy VietMap trong ô.
- [SUY, thay thế] Nếu trên xe VietMap thiếu một quyền runtime (vị trí, mic…) thì đó là ca d: VietMap thật sự thoát ô; trên 2.97 (chưa R7)
  Kachi hoàn ô + câu "đã rời ô, vẫn mở ngoài ô"; lần mở sau (đã cấp) vào được.
- [CHƯA BIẾT] ca nào xảy ra trên xe. Chốt bằng MỘT lượt trên xe, khởi động nguội VietMap vào ô rồi đọc:
  `logcat -d | grep -E "START u0|Failed to put TaskRecord"` (thấy `cmp=com.byd.carsettings/…UnsupportActivity` ⇒ ca miễn pin; thấy
  `GrantPermissionsActivity` ⇒ ca quyền) + `dumpsys deviceidle whitelist | grep vietmap`.

**R7 xử lý ca nguội** [ĐO máy ảo]:
- Hộp thoại task riêng (ca c — gói hệ): R7 **không** nhận — `checkAdopt` đòi gói task = gói ô đang hiện; task Settings root
  `com.android.settings` ≠ `vn.vietmap.live`, và task VietMap vẫn ở màn ảo nên nhịp đo thấy app sống ⇒ không có `APP_ELSEWHERE`.
  Rủi ro nhận nhầm task hệ chỉ còn khi ô đang hiện CHÍNH gói hệ đó (vd đặt Settings vào ô) — khi đó nhận là đúng ý người dùng.
- Hộp thoại trong task app (ca d): R7 nhận **task của app** (root `vn.vietmap.live`) kèm hộp ở đỉnh — đúng; xong hộp, app ở lại freeform
  đúng khung (không tự quay về màn ảo, giữ dấu tới khi ô đổi).
- Khác xe: máy ảo có `com.android.settings` + `permissioncontroller` Google; xe có CarSetting `UnsupportActivity` (hộp có nút OK) ⇒ chữ và
  gói khác, cơ chế đặt task giống nếu VietMap vẫn gọi với `NEW_TASK` (đã thấy cờ `flg=0x10000000` trong START của chính VietMap) [SUY].
  Xe có `StatusBar`/`NavigationBar0` khác (§2 mục 8 evidence 09/10) ⇒ lớp che 🚗 như spec.

## 4. Phát hiện phụ (không thuộc R7, ghi để soát)

- [ĐO] Ô 7 (đỗ ẩn): VietMap đang **đỗ** trên màn ảo cũ bị `am force-stop` (do script đo) ⇒ khi đặt lại vào ô, Kachi "nhận lại" màn ảo đỗ,
  thấy không có task ⇒ `KachiVd … app đã đóng` → `KachiSlotLife: APP_DIED … -> Clear` (ô bị xoá, không mở lại app). [CHƯA BIẾT] có phải
  lệch với PARK-2b ("màn ảo nhận lại không có app ⇒ mở như đường thường") không — một lần tái hiện; ngoài phạm vi R7.
- [ĐO] Đặt lại vào ô một app ĐÃ thoát (task cũ còn) khiến app tự chạy lại màn chào: Messages Hist 2→4, Meet 2→4, Drive 1→2 — do app,
  không do lệnh đưa-lại-lên.
- [ĐO một lần, không tái hiện] Lượt d: sau Allow + HOME, task VietMap mất (`reconcile … vn.vietmap.live gone` → `APP_DIED -> Clear`);
  log hệ lúc đó đã bị xoá nên [CHƯA BIẾT] vì sao. Lượt e (lặp lại y hệt) VietMap sống bình thường.
- [ĐO] Gmail freeform: nội dung app hụt ~10 px mỗi bên trong khung ô (ảnh chụp) — viền/bóng freeform; không ảnh hưởng chạm.

## 5. Trạng thái máy ảo để lại

Kachi HOME đỉnh display 0; ô 1 = YouTube (như lúc đầu); dấu `kachi_slot_escape` **không còn**; mọi task display 0 `fullscreen`
(Waze/Gmail/Messages/Drive/Meet/Photos/VietMap/Camera đã được trả mode 1 bởi R7 `release` hoặc tay — Camera); `enable_freeform_support=1`;
VietMap 3.4.3 mod v2 **còn cài**, đã cấp vị trí (fg), đã miễn pin; `svc power stayon false`. Chế độ kiểm thử (cầu) tự hết hạn ~14:42.
