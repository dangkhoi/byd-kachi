# R7 hậu kỳ — R10 · R12–R17 trên máy ảo (2026-10-09)

> **Trạng thái**: Evidence · **Cập nhật**: 2026-10-09 · **Chủ**: dangkhoi · **Liên quan**: spec `docs/specs/kachi-298-plan.html` R10 · R12–R17 ·
> OQ4 · OQ5 · OQ6 · backlog `SLOT-APP-CRASH-WHITE` · `PARK7-FORCESTOP-CLEAR` · `SLOT-ADOPT-NOTIFY-OLD-HOST` · trước đó
> `docs/diagnostics/emu-slot-escape-app-sweep-2026-10-09.md`.
> Máy ảo `emulator-5556` (AVD `kachi_play`, Android 10, 1920×1080 @240dpi), Kachi **vehicleTest 2.98 (201)** cây làm việc (chưa commit).
> Không chạm xe. Ảnh chụp/log thô ở scratchpad phiên (không đưa vào repo — có tên/tài khoản máy ảo).

Owner 09/10: *"làm hết"*. Cách đo chung: đặt app vào ô 1 bằng cầu kiểm thử `slot` (cùng đường lưu ô như người dùng), đọc
`logcat` (`KachiVd`/`KachiEscape`/`KachiSlotLife`/`ActivityTaskManager START`), `logcat -b events`, `am stack list`,
`dumpsys activity activities` (đếm `Hist #` của task), `dumpsys activity recents` (intent gốc), `dumpsys window windows` (thứ tự cửa sổ).

## R10 — đưa lại lên bằng `am task focus` (thay `am start MAIN/LAUNCHER`)

- [ĐO] `am task focus 99999` (id không tồn tại) ⇒ in `Setting focus to task 99999`, exit 0, **không ném** (`runTaskFocus` →
  `setFocusedTask` trả im). `IllegalArgumentException` của bản quét trước chỉ là khi THIẾU đối số (`Argument expected after "focus"`,
  `ActivityManagerShellCommand.java:2794`). ⇒ không có ca "dấu cũ làm lệnh ném": id lấy từ CHÍNH bản đọc của lượt đối chiếu
  (`Step.Keep`), task mất giữa hai lệnh thì lượt kế thấy `Gone`.
- Mã mới: lần đầu mỗi tiến trình +1 `am stack list` xác nhận task lên ĐỈNH display 0 (log `refront focus (xác nhận đỉnh)`); hỏng ⇒ đường
  lùi `am start MAIN/LAUNCHER` CHỈ khi intent gốc (từ `dumpsys activity recents`) là MAIN/LAUNCHER.

| App (gói) | Intent gốc task | Hist trước → sau 3 × (Settings toàn màn → HOME) | Sự kiện của gói (`-b events`) | Cửa sổ sau |
|---|---|---|---|---|
| Waze | MAIN/LAUNCHER | 1 → 1, cùng pid | `am_resume_activity` ×3, **không** `am_create_activity`/`am_new_intent` | Waze #12 trên KachiHome #13, khung `[19,36][1901,985]` giữ |
| VietMap mod (singleTask, thiếu quyền vị trí ⇒ thoát ô, ca d) | MAIN/LAUNCHER | 2 → 2, cùng pid | không `am_new_intent` (bản `am start` cũ: ×3) | VietMap #13 trên KachiHome #14 |
| Messages — task do app/shell tự mở (`am start -n …ConversationListActivity`, intent `{flg=0x10000000 cmp=…}`), thoát ô bằng `am display move-stack` | **không** MAIN/LAUNCHER | 2 → 2 | không `am_create_activity` (bản `am start` cũ: Hist 2 → 8); `am_proc_died` ×2 = LMK máy ảo khi Settings ở trước (adj 700), activity cũ `am_restart_activity` trong CÙNG task | Messages #12 trên KachiHome #13 |

Log Kachi mỗi HOME: `[slots+resume] reconcile 1 dấu ⇒ <gói> refront focus · <gói> keep task N onTop=true`. 🚗 một lượt xe: `logcat -b events`
chỉ `am_resume_activity` khi HOME + `dumpsys window windows` app nằm trên `KachiHome` (DL3 fw 2606 có `am task focus` — chưa đo).

## R12 — app sập ngay khi vào ô ⇒ ô trắng/xám (`SLOT-APP-CRASH-WHITE`)

- [ĐO] Tái hiện: Maps vào ô 1 + `am crash` lặp (0,05–0,2 s) ⇒ `FATAL EXCEPTION` + `Force finishing activity` ở lượt mở và lượt thử lại
  2 s ⇒ ô xám (màn ảo trống) + hộp "Maps keeps stopping" của hệ. Bản cũ: kết luận sau **37 s** / **41 s** (hai lượt).
- [ĐO] Nguyên nhân thời gian: (a) trên A10 task của activity đã `force-finish` CÒN trong `am stack list` ~20 s (31,5 → 51,5 s; trong khi hộp
  "keeps stopping" hiện) ⇒ bộ đo thấy "sống"; (b) 6 mốc burst đứng yên đẩy `unchangedSweeps` lên ⇒ nhịp thường lùi 15 s ngay sau lượt mở;
  (c) nhịp kế được hẹn theo bức tranh CŨ ⇒ lần vắng đầu tới, lần thứ hai phải chờ hết 15 s.
- [SUY từ mã, khoá bằng test] Ca app sập trước MỌI nhịp đo (không bản đọc nào thấy task): luật 1 "chưa thấy sống thì không kết luận"
  giữ ô mãi (đúng triệu chứng xe 13:02 YouTube mod). Trên máy ảo không dựng được ca này vì (a).
- Sửa: `SlotLiveness` thêm bằng chứng DƯƠNG `GONE` (bản đọc được, gói không còn task ở display nào) × `GONE_SWEEPS`=2 ⇒ kết luận "đã
  đóng" kể cả khi chưa thấy sống; `SlotLiveProbe`: mốc burst không tính lùi nhịp; bức tranh đổi ⇒ nhịp kế về 5 s ngay (`rearm`).
- [ĐO sau sửa] Maps sập trong ô: lần đầu vắng 16:12:24,100 → kết luận 16:12:29,117 (**5,0 s**, trước 15 s); lượt khác 16:13:51,565 →
  16:13:56,621 ⇒ `APP_DIED -> Clear` (luật hoàn ô: trong suốt / widget LƯU). Tổng từ lúc sập ≈ 20 s hệ giữ task + 5 s.

## R13 — app đỗ ô 7 bị force-stop rồi đặt lại vào ô ⇒ ô bị xoá (`PARK7-FORCESTOP-CLEAR`)

- [ĐO trước sửa, 2 lần] VietMap: ô 1 VietMap → YouTube (VietMap đỗ) → `am force-stop vn.vietmap.live` (task mất ngay — `am stack list`
  rỗng) → ô 1 VietMap: `nhận lại … display=13` → **10 s** sau `app đã đóng` → `APP_DIED vn.vietmap.live -> Clear`. YouTube cùng kịch bản
  (không có nhịp burst ngay trước) ⇒ kết luận ngay 24 ms, mở lại đúng PARK-2b.
- Gốc [ĐO log + đọc mã]: `SlotLiveProbe` GHI bản đọc của chính nó vào `StackListSnapshot` với mốc LÚC XONG ⇒ nhịp kế (mốc `since` = lúc
  BẮT ĐẦU nhịp trước) coi bản đó là "mới" ⇒ nhịp `kick` của màn ảo nhận lại dùng lại bản đọc burst 9 s của ô cũ, chụp khi VietMap CÒN
  trên màn ảo đỗ ⇒ `seenAlive` ⇒ luật chết thường (2 nhịp) ⇒ `onDead` thay vì `onMissing` (mở lại). Lệch PARK-2b do bản đọc cũ, không do luật.
- Sửa: bản đọc ghi mốc LÚC BẮT ĐẦU đọc (bộ đo + lượt dò repin); `kick` đặt `freshAfter` ⇒ chỉ dùng bản chụp SAU lượt nhận lại.
- [ĐO sau sửa] cùng kịch bản: `nhận lại vn.vietmap.live display=27` 16:14:45,575 → bản đọc mới `GONE` 45,596 → `START … vn.vietmap.live`
  46,975 (mở lại vào CHÍNH màn ảo) → VietMap sống trong ô (`alive=true` các nhịp sau), không `APP_DIED`.

## R14 — OQ4 màn chính cũ vẫn dò ô sau `slot-taken` (`SLOT-ADOPT-NOTIFY-OLD-HOST`)

- [ĐO trước sửa, 15:48:57] HOME dựng màn chính MỚI (`KachiHome` alias) khi task `KachiHomeActivity` cũ còn ⇒ `giải phóng màn ảo kachi-slot-0
  · slot-taken` ⇒ **1,6 s** sau bộ đo màn cũ `ô ws@34366045#0: … app đã đóng` ⇒ `KachiSlotLife: APP_DIED … -> Clear` trên màn đang ẩn.
- Sửa: `SlotVdOwner.adopt/move` gỡ bộ đo của host cũ theo khoá `SlotVdLedger.keyOf(owner, slot)` TRƯỚC khi nhả; `VdAppHost.probeKey` dùng
  chung hàm khoá.
- [ĐO sau sửa, 16:27:58] tái hiện hai màn (force-stop Kachi → mở `KachiHomeActivity` → Settings → HOME): `slot-taken` 58,412, 40 s sau
  không có dòng `app đã đóng`/`APP_DIED` nào của chủ cũ.

## R15 — OQ5 câu "rời ô" ~20 s sau HOME

- Gốc [ĐO mã]: `SlotLiveProbe.resume()` nối tiếp nhịp đã lùi (15 s) + `start()` hẹn 5 s ⇒ ~20 s (số QA 2.98).
- Sửa: màn chính hiện lại ⇒ `unchangedSweeps = 0`, thay nhịp đang hẹn bằng một nhịp 5 s.
- [ĐO sau sửa, 16:21] Calendar trong ô 75 s (nhịp đã lùi) → Settings toàn màn (Kachi `onStop`) → `am display move-stack` Calendar về
  display 0 → 12 s → HOME 16:21:18,090 ⇒ `RA KHỎI ô` 16:21:23,137 (**5,0 s**) ⇒ R7 nhận freeform (R7 tắt thì là câu "rời ô").

## R16 — hộp thoại Kachi nằm DƯỚI app freeform (OQ6 [P3])

- [ĐO trước sửa, 15:54] Waze đang được quản, chạm chip hồ sơ QUA lớp che ⇒ bộ chọn hồ sơ (`AlertDialog`, `ty=APPLICATION`, wrap, CENTER)
  là Window #13 của task KachiHome, **dưới** Waze Window #12 ⇒ người lái không thấy, BACK rơi vào Waze.
- Sửa: một móc `SlotEscape.shade(dialog)` ở các cửa dựng hộp thoại dùng chung (`SettingsDialogs` ×6 · `UpdateFlow` · `KachiHomeDisclaimer`);
  `EscapeShade` (core) gộp bảng + hộp thoại, chỉ hành động ở CẠNH: mở ⇒ gỡ che + Home qua rào camera (app xuống dưới); đóng ⇒ đối chiếu +
  đưa lên lại. Theo dõi bằng `OnAttachStateChangeListener` của decor (không đè listener của bên gọi).
- Không dùng bọc `WindowManager` của Activity: [ĐO nguồn AOSP Q] `Window.setWindowManager` ép kiểu `(WindowManagerImpl) wm` ⇒ bọc là
  `ClassCastException` ở mọi `Dialog`.
- [ĐO sau sửa, 16:03:45] chạm chip ⇒ `lớp che gỡ (dialog)` → `dialog mở ⇒ app freeform xuống dưới màn nhà` ⇒ hộp thoại Window #3 trên
  Waze #5, hiện đủ (ảnh); "Hủy" 16:03:56,812 ⇒ `[dialog-closed] … refront focus … onTop=true` + che lại, Waze #12 trên KachiHome #13.

## R17 — đầu ô trên xe (đỉnh ổn định 84)

- [ĐO máy ảo] `cmd overlay enable …cutout.emulation.tall` chỉ cho `mStable=[0,42]` (không giả được 84) ⇒ đã TẮT lại (đọc lại
  `mStable=[0,36][1920,1080]`); hình học khoá bằng test với số xe (`EscapeFitTest` R17): task `[19,84][1901,985]`, CAPTION 5 px, GAP
  `[19,89][1901,149]` (nền sau ô + ⇄ — ⇄ cao 60 px nằm trọn), khung bo bắt đầu ở 149 = đỉnh nội dung app; ô có đỉnh ≥ đỉnh ổn định + thanh
  tiêu đề ⇒ không GAP. Không đổi mã: thiết kế Pass 8 (GAP vẽ nền + CORNER ở đỉnh nội dung) đã là "dời đỉnh nhìn thấy của CHÍNH ô đó xuống
  đỉnh nội dung"; hai lối khác bị loại — tô khay ô = "thanh xám" owner đã bỏ; dời khung ô trong bố cục Kachi ⇒ khung task đổi theo ⇒ vòng
  kéo khung. 🚗 `dumpsys window displays | grep mStable` + nhìn đầu ô.

## Trạng thái máy ảo để lại

Kachi HOME đỉnh display 0 (một task màn nhà), ô 1 = YouTube trên màn ảo, dấu `kachi_slot_escape` **không còn**, mọi task display 0
`fullscreen`, `enable_freeform_support=1`, overlay cutout giả lập **tắt** (`mStable=[0,36]`), VietMap đã cấp lại vị trí; chế độ kiểm thử adb
đang bật (tự tắt sau 60 phút). Bản cài: vehicleTest 2.98 (201) cây làm việc (gồm R10 · R12–R17, không còn log gỡ lỗi).
