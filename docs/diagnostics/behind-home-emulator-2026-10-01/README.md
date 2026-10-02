# BEHIND-HOME — đưa app ra phía sau màn nhà mà không che màn nhà (máy ảo Android 10, 01–02/10)

> **Trạng thái**: Current (bằng chứng máy ảo — KHÔNG phải xe) · **Cập nhật**: 2026-10-02 (§Lượt 8 thêm `break-tests-review6.log` — senior review lượt 6; §Lượt 8 thêm `break-tests-review5.log` — senior review lượt 5; §Lượt 8 thêm `break-tests-review4.log` — senior review lượt 4; §Lượt 2 — mã thật nhóm A; §Lượt 3 — nhóm B; §Lượt 4 — nhóm C chuyến lên xe: T-M3 loại ý-định VIEW, phiên nhạc, E8/E9/E10/E12/E13/E15; §Lượt 5 — senior review lượt 1: hai lỗi E2E đã vá; §Lượt 6 — senior review lượt 2: nhạc lên xe khi tiến trình có mà không task; §Lượt 7 — senior review lượt 3: PIP đo thật; §Lượt 8 — đóng scope: T-M2 · T-M6 · E3b · E4 · E11 · E13 · E16 · camera đứng thay) · **Mục đích**: Bằng chứng §14 tầng 1 (shell thô) cho mảnh chung BEHIND-HOME của spec [`../../specs/kachi-launcher-shortcuts-autostart.html`](../../specs/kachi-launcher-shortcuts-autostart.html) (§2.3, R0). Owner 01/10: *"Các chức năng này chỉ là launcher, app android thuần, chả có gì phải chờ xe"* ⇒ đo trên máy ảo thay xe; phần chỉ ROM BYD trả lời ghi [CHƯA BIẾT] trong spec (§6.3 OC-1…OC-7).

## Môi trường

- Máy ảo `clusternav10` (Android 10, `emulator-5554`), Kachi 2.84 (185) là HOME, VietMap nằm trong ô (màn ảo `kachi-slot-*`, cờ `FLAG_OWN_CONTENT_ONLY`, 200 dpi) — `00-baseline.txt`.
- Lệnh gọi bằng uid 2000 (`su 2000 …`), đúng quyền của kênh shell Kachi (dadb).
- Anchor S trong các lượt đo là **Settings** khởi bằng `tools/LB.java` (chạy qua `app_process`, mã đo — **không** phải mã app). Anchor là activity của chính Kachi = phép đo **T-M1** của spec, CHƯA chạy.
- ⚠ Độ sạch: trong lúc đo có một luồng E2E khác tắt/bật màn và `force-stop com.byd.launcher` mỗi 25–30 s. Các tệp bị lẫn ghi INVALID bên dưới và **không** dùng làm căn cứ.

## Tệp và kết luận

| Tệp | Lượt | Kết luận | Dùng? |
|---|---|---|---|
| `00-baseline.txt` | Trạng thái gốc | `am stack list` **đầy đủ** (có dòng `configuration` với `mActivityType`/`mWindowingMode`) — mẫu định dạng A10 cho bộ lọc chặt | ✅ fixture định dạng |
| `01-p1-start-then-home.txt` | O5 nguội | Phần app nguội bị luồng E2E khác chen (giết Kachi 23:55:32) | ❌ INVALID (phần nguội) |
| `02-p2a-launch-behind.txt` | O4 `makeTaskLaunchBehind` | A10: stack mới lên **trên cùng**, app resumed, KachiHome stop | ✅ ⇒ loại O4 |
| `03-p2b-avoid-move-to-front.txt` | O3 khoá Bundle `android.activity.avoidMoveToFront` | Stack mới nằm **đáy** display 0, `visible=false`, **không** `am_proc_start` (activity INITIALIZING) | ✅ tạo S |
| `04-p3-bfirst-move-task.txt` | O2 lượt đầu | Màn tắt + Kachi chết lúc T0 (luồng khác) | ❌ INVALID |
| `05-p3-gated.txt` | O1 + chuẩn bị O2 (có cổng chờ màn thức + ô sống) | B mở vào **cùng** màn ảo: display 0 không có sự kiện nào; A dưới B, pid giữ | ✅ O1 |
| `06-p3-steps3-7.txt` | **Tệp chính** O1/O2/O2-sai/hoàn tác | bước 3: B lên đỉnh màn ảo (`bringingFoundTaskToFront` trên display 44) · bước 4: `am stack move-task <A> <S> false` ⇒ `wm_task_moved [2081,0,0]`, **0** `am_focused_stack` display 0 · bước 5: `am start --display <VD> -n A` đưa A về ô (`reparentToDisplay`, pid giữ) · bước 6 (**O2-sai**, A ở đỉnh màn ảo): `am_focused_stack [0,0,68,0,moveTaskToStack]`, KachiHome pause + stop = **che HOME** · bước 7: HOME lên lại | ✅ O1, O2, O2-sai, K8 |
| `07-p1-warm.txt` | O5 ấm (`am start` app rồi `am start` HOME liền) | HOME có lại sau ≈ 370 ms; app che HOME ≈ 0,35–0,4 s | ✅ ⇒ loại O5 |
| `08-cleanup-1.txt` | Dọn | YT Music có trên máy ảo | — |
| `09-p5-youtube-behind-home.txt` | YouTube phát rồi về HOME | YouTube kẹt màn bắt cập nhật | ❌ INVALID (không đo được tiếng) |
| `10-p4-placeholder-totop.txt` | **Chạy ngầm trọn chuỗi** (R0.3) | X (Đồng hồ, nguội) mở vào màn ảo ô (`am_proc_start` 00:06:50.723, `am_on_resume_called` 51.088) → B đưa lên lại (54.358, display 46) → `move-task <X> <S> true` ⇒ `wm_task_moved [2091,1,1]` (56.570); KachiHome resumed suốt, **0** `am_focused_stack` display 0 tới lúc người dùng tự mở lại X (58.713, `am_relaunch_resume_activity` — đổi cấu hình, tiến trình giữ) | ✅ O1+O2 = R0.3 |
| `99-final-state.txt` | Dọn cuối | Trạng thái về baseline | — |

## Nguồn AOSP giải thích (trích trong spec §2.3)

- Thứ tự "B trước, A sau" là điều kiện cứng: `TaskRecord.reparent` A10 r47 `:728-749` (`wasFront`/`wasFocused` tính trên display NGUỒN), A12 r34 `Task.java:1134-1146`.
- Khoá `avoidMoveToFront` ⇒ `mDoResume = false`: `ActivityStarter.java` A10 r47 `:1868-1870`; khoá đọc ở `ActivityOptions.java` A10 `:221/:954`, A12 `:265/:1148`; `setAvoidMoveToFront()` là `@hide` — chỉ đi được bằng chuỗi khoá trong Bundle (hợp đồng không công bố ⇒ spec R0.5 tự đo mỗi lần dùng).

## Tái lập

Công cụ ở `tools/` (`a.sh` = adb tới `emulator-5554`, đường adb lấy từ biến `ADB`). Ví dụ lượt chính: đẩy `tools/p3b.sh` lên máy ảo rồi `sh p3b.sh` (cổng tự chờ màn thức + có ô sống). `LB.java` dựng thành `classes.dex`, đẩy tới `/data/local/tmp/kprobe_lb.dex`, gọi qua `app_process` (xem `tools/lb.sh`).

Định dạng các tệp 02–10 là `am stack list | grep -E 'Stack id|taskId'` (thiếu dòng `configuration`) ⇒ đủ cho phép thử "ai ở đỉnh màn ảo" và cờ `visible`, **không** đủ cho bộ lọc loại stack chặt. T-M1 của spec phải chụp `am stack list` nguyên bản ở mỗi bước làm fixture.


## Lượt 2 — mã THẬT của nhóm A (02/10 04:28–04:42) · thư mục `impl/`

Bản `vehicleTest` dựng từ cây làm việc (chưa commit), cài đè 2.84 (185) trên `clusternav10`; trước đo: sao lưu APK + `tar`
prefs; sau đo: cài lại APK gốc (sha256 `919c3aff…26f5`), `tar xf` đè tại chỗ + `chown`/`restorecon`, so sha256 cả 10 tệp
prefs = khớp (lượt trả thứ hai: 9/10 khớp, `clusternav_prefs.xml` chỉ khác `a11y_proc_start_elapsed` — mốc Kachi tự ghi mỗi lần tiến trình bật lại sau `force-stop`, không phải dấu của lượt đo). Luồng đo DUY NHẤT trên máy ảo (không có E2E khác chen). Lệnh mở ô đi đúng đường người dùng: giọng nói
*"mở đồng hồ vào ô 1"* qua cầu kiểm thử `say` (ô 1 = VietMap). Công cụ: `impl/tools/{e1,kill,orphan}.sh` (chạy trên máy).

| Thư mục | Ca | Kết quả | Mức |
|---|---|---|---|
| `impl/e1` | **T-M1 + E1** — đặt tạm Đồng hồ vào ô có VietMap | `KachiBehind … → MOVED task=2258 S=122 top0=0 kiểm=OK gỡ=1`. Display 0: **0** `am_focused_stack`, **0** `am_pause_activity` của KachiHome (duy nhất `am_focused_stack [0,83,…]` trên màn ảo 83). Giữ chỗ `BehindAnchorActivity` của chính Kachi: `am_create_activity` (stack 122 mới, ĐÁY display 0, `standard`/`fullscreen`, ẩn — `s12.txt`), **không** `am_on_create_called` (activity không chạy). `move-task` ⇒ `wm_task_moved [2258,1,1]`, `wm_stack_removed 120` (`s13.txt`). `finishAndRemoveTask` gỡ được giữ chỗ chưa từng `onCreate` (`am_finish_activity … finish-and-remove-task`, `s14.txt`: S chỉ còn VietMap). pid VietMap 5362 → 5362. `slot_0` trên đĩa không đổi (sha `kachi_workspace.xml` = trước đo) | [ĐO] |
| `impl/e1b` | E1 lặp lại sau khi thêm dấu bền | `MOVED task=2267 S=127`; `clusternav_state.xml` có `kachi_behind_marks = 2267:vn.vietmap.live` | [ĐO] |
| `impl/e6` | **E6** — giết Kachi `kill -9` (kiểu BYD) khi VietMap đang sau màn nhà, BẢN CHƯA CÓ đường trả lại | `am_finish_activity … KachiHome, proc died without state saved` → `am_set_resumed_activity [vn.vietmap.live …]`: **VietMap nổi lên che toàn màn**, stack home rỗng, 12 s sau vẫn vậy (Kachi chỉ dựng lại dịch vụ Hỗ trợ). Phát hiện mới ⇒ dấu bền + lượt trả lại (spec §9 Nhật ký 02/10) | [ĐO] |
| `impl/e6/e6b-…` | **E6b** — đường LƯU (cầu `slot`, cùng đường ngăn kéo) | `slot_0` ghi bền, `Force stopping vn.vietmap.live`, ô dựng lại màn ảo mới — y như hôm nay, không `KachiBehind` | [ĐO] |
| `impl/kill` | E6 với đường trả lại | Giết lúc ~04:39:58,9; VietMap ở đỉnh tới 04:40:02,35 (`timeline.txt`); `KachiBehind: recovery dấu=1 nổi-lên=true` 04:40:01,86 (chuỗi SẴN, trước `keys=`) ⇒ HOME lên lại; che ≈ **3,3 s** (màn đang bật) thay vì mãi mãi | [ĐO] |
| `impl/e1c` · `impl/kill2` | E1 + E6 lặp lại trên bản CUỐI (lượt trả lại chạy trên luồng `kachi-behind`, không chặn kiểm phím) | E1c: `MOVED task=2294 S=139`, **0** `am_focused_stack [0,0,…]`, **0** pause KachiHome. Kill2: VietMap ở đỉnh 04:50:40,57 → 04:50:43,92 (≈ **3,4 s**); `keys=BOUND` +32 ms sau `screen_on` (bản trước +671 ms vì lượt trả lại chạy trước kiểm phím trên cùng luồng); `recovery dấu=1 nổi-lên=true` 43,28 | [ĐO] |
| `impl/break-tests-mut.log` | Phá thử 6 mắt xích (bỏ cấm A ở đỉnh · dấu sau move-task · đặt tạm ghi bền · S loại rỗng · đọc stack đầu danh sách · đổi tại chỗ giết app cũ) | 6/6 làm bài canh/unit ĐỎ, khôi phục ⇒ xanh | [ĐO] |
| `impl/orphan` | Giết Kachi ĐÚNG lúc giữ chỗ đã dựng, chưa gỡ (stack 133 — `at-kill.txt`) | Hệ resume giữ chỗ mồ côi trong tiến trình mới ⇒ `onCreate` chạy, pid khác bên mở ⇒ `anchor mồ côi … tự gỡ, không tắt tính năng`; HOME resumed sau ≈ 0,8 s; lượt trả lại tỉa dấu cũ (`dấu=1 nổi-lên=false giữ=0`) | [ĐO] |

Chưa đo ở lượt này: giữ chỗ mồ côi KHÔNG phải stack resume kế tiếp (chỉ được `removeAll()` đầu lượt sau dọn — [SUY]
`getAppTasks` lọc theo uid, A10 `RecentTasks.java:809-828`); T-M2/T-M3/T-M5/T-M6 (ngoài phần nhóm A).

## Lượt 3 — nhóm B (Shortcuts UI), ĐƯỜNG CHẠM THẬT (02/10 05:30–05:50) · thư mục `ui/`

Bản `vehicleTest` dựng từ cây làm việc (chưa commit), cài đè 2.84 (185) trên `clusternav10`. Trước đo: `tar` prefs qua
`run-as` + sha1 10 tệp. Cấu hình (`dock_enabled` có `launcher_shortcuts`, `app_shortcuts`, `slot_2 = widget:w_apps`) ghi
bằng `run-as` giữa hai lượt `am force-stop` (HOME bị dừng thì hệ dựng lại ngay và đọc tệp cũ — lượt đầu của công cụ dính
đúng bẫy này). Chạm = `input tap` vào icon tìm bằng `uiautomator` (contentDescription = tên app); chụp `am stack list`
nguyên bản theo nhịp 0,5 s + `logcat -b events` + log `KachiShortcut`/`KachiBehind`. Công cụ: `tools/ui-groupB.py`,
`tools/silent_adbd.py` (adbd câm giả cho E5). Sau đo: `tar xf` prefs ⇒ **10/10 sha khớp**, cài lại APK 2.84 gốc,
VietMap về ô 1.

| Thư mục | Ca | Kết quả | Mức |
|---|---|---|---|
| `ui/m1-bottom4` · `ui/m7-*` | **E7** khối thanh nút | 1/4/8 app ⇒ 90/324/636 px theo trục (dưới · trái · phải) = n × 52 dp + 2 × 4 dp @1,5×; bề dày 109 px (ngang) / 124 px (dọc); 8 app ở viền trái ⇒ thanh cuộn | [ĐO] |
| `ui/m13-edge-switch` | Đổi viền DƯỚI→TRÁI ở Cài đặt khi đang chạy | task + màn ảo y nguyên (chỉ khác bounds, `[slot-resize]`), không `tạo màn ảo`; khối thành dọc 246 px (3 app) | [ĐO] |
| `ui/m2-slot-tap` | **E1 qua lối tắt** (Đồng hồ = *Ô 1*, ô 1 có VietMap) | `PlaceTemp(slot=0, evict=vn.vietmap.live)` → `MOVED task=2320 S=147`; 0 `am_focused_stack [0,0,…]`; KachiHome không `am_pause_activity`; pid VietMap giữ; sha `kachi_workspace.xml` + `slot_0` không đổi | [ĐO] |
| `ui/m5-noop` | Chạm lại Đồng hồ (đã ở ô 1) | `Noop(highlight=0)`, 0 lệnh | [ĐO] |
| `ui/m3a-bg-running` | *Chạy ngầm* YT Music khi đang chạy | `StartBehind` → chuỗi: `đang chạy (pidof), 0 lệnh đổi cửa sổ` | [ĐO] |
| `ui/m3b-bg-cold` | **E3** *Chạy ngầm* YT Music nguội | dàn qua ô 1 (Đồng hồ) → `MOVED task=2323 S=149`; `am_proc_start` + `am_on_resume_called` YT Music; 0 `am_focused_stack [0,0,…]`; Đồng hồ về đỉnh ô. `onResume` của app nguội tới SAU `move-task` (≈ 1,7 s, `chờ=0ms`) | [ĐO] |
| `ui/m4-full` · `ui/m8c-widget-tap` | *Toàn màn* (app không ở ô), từ khối và từ widget | `OpenFull` → Waze resumed toàn màn display 0; HOME về Kachi | [ĐO] |
| `ui/m6-restart` · `ui/m6b-…` | **E6** khởi động lại Kachi | ô 1 = VietMap (bố cục lưu), Đồng hồ không còn ở ô. `force-stop` HOME ⇒ hệ resume app trên cùng còn lại (Waze người dùng tự mở) tới khi bấm HOME; `BehindHomeRecovery` không đụng (không mang dấu) | [ĐO] |
| `ui/m8b-tap-gone` · `ui/m8d-generic-icon` | App đã gỡ trong danh sách | chạm ⇒ `Refuse(NOT_INSTALLED)` + toast "… chưa cài"; bản đầu để ô TRỐNG (không có gì để mờ) ⇒ sửa: hình app chung mờ | [ĐO] |
| `ui/m11-empty` | Danh sách rỗng | khối + widget hiện một ô "Chưa có lối tắt — chạm để chọn ứng dụng"; chạm ⇒ Cài đặt mở nhóm Thanh (ở đầu trang) | [ĐO] |
| `ui/m10-picker` | Cài đặt + ngăn kéo `PICK_SHORTCUTS` | chip Waze → *Chạy ngầm* ⇒ `com.waze|B`; ▼ Đồng hồ ⇒ đổi thứ tự; ngăn kéo tô sẵn 4 app, chọn Chrome, **Áp dụng (5)** ⇒ `…,com.android.chrome|F`; tiêu đề mục "(5)" | [ĐO] |
| `ui/m12-no-channel` | **E5** chưa có kênh (adbd câm: `adb reverse tcp:5555 tcp:5556`) | `needs lost` + thẻ MẤT DUYỆT; icon *Ô n*/*Chạy ngầm* mờ, *Toàn màn* không; chạm *Ô 1* ⇒ `Prompt` + `card show`, task y nguyên; chạm *Toàn màn* ⇒ Waze mở; trả kênh + *Thử lại* ⇒ `up src=f4`, icon sáng lại (thời gian sáng lại chưa đo riêng) | [ĐO] |
| `ui/break-tests-groupB.log` | Phá thử 9 mắt xích nhóm B | 9/9 ĐỎ, khôi phục ⇒ xanh | [ĐO] |

Chưa đo ở lượt này: E3b (bố cục không có ô app sống ⇒ `NO_STAGE`) — chỉ có test; E4 (*Toàn màn* khi app đang ở ô — chờ
T-M2); hai cú chạm dồn trên cùng một ô; ROM BYD (OC-1/OC-2) [CHƯA BIẾT].

## Lượt 4 — nhóm C (chuyến lên xe F2/F3) (02/10 06:13–07:22) · thư mục `trip/`

### 4a · T-M3 bằng shell thô (uid 2000) — ý-định VIEW vào màn ảo ô **che màn nhà** ⇒ loại K9

| Tệp | Ca | Kết quả | Mức |
|---|---|---|---|
| `trip/tm3-ytmusic.txt` | K9 `am start --display <VD> -a VIEW -d '<watch>' -p <YT Music>`, app **nguội** | activity trung chuyển `MusicServiceDeepLinkActivity` dựng trên màn ảo, rồi CHÍNH app mở `MusicActivity` bằng NEW_TASK lên **display 0**: `am_focused_stack [0,0,164,0,reuseOrNewTask]`, KachiHome `am_pause_activity` + stop ⇒ **che màn nhà**. Lượt ấm (app đã ở display 0) nhận `am_new_intent` ở display 0. | [ĐO] |
| `trip/tm3v-ytmusic.txt` | K4 (MAIN/LAUNCHER) dàn app vào màn ảo TRƯỚC, rồi VIEW vào CÙNG màn ảo | task đang ở màn ảo bị **kéo sang display 0**: `am_focused_stack [0,0,194,0,reparentToDisplay]` ⇒ vẫn che màn nhà | [ĐO] |
| `trip/tm3s-ytmusic.txt` | K4 nguội vào màn ảo + `MediaController` (`tools/MS.java`, `app_process`) | app ở lại màn ảo, 0 sự kiện display 0; phiên hiện ra ở trạng thái 2 (tạm dừng, *Despacito* — hàng chờ cũ); `play()` ⇒ 3; `playFromSearch` không đổi bài trong 15 s; app bị app ô che vẫn phát | [ĐO] |
| `trip/tm3u-ytmusic.txt` | `playFromUri(watch?v=9bZkp7q19f0)` vào phiên đang phát | đổi đúng bài (*Despacito* → *Gangnam Style*) trong ≤ 15 s, **0** sự kiện `am_`/`wm_`; link danh sách phát (`playlist?list=`) không đổi bài | [ĐO] |

⇒ Chuyến giao "phát gì" qua **phiên nhạc** (`MediaBridge.playFromUri`/`playPackage`), không bao giờ qua ý-định VIEW.
Nguồn gốc vì sao activity trung chuyển rơi về display 0 (chính sách `LaunchParams` của task mới từ app khác uid): [SUY],
chưa đọc AOSP — không cần cho quyết định (mã không dùng đường đó).

### 4b · Mã THẬT bản `vehicleTest` (chưa commit), đường thật: giết kiểu BYD → bật màn → chuỗi SẴN → chuyến

Driver `trip/tools/trip_e2e.py` (`ignite` = màn tắt · `kill -9` MỌI tiến trình Kachi · chờ 10 s · màn bật; ghi đỉnh display 0
mỗi giây + `KachiTrip`/`KachiBehind`/`KachiReady`/`A11yLifecycle` + `logcat -b events`). Cấu hình ghi bằng `run-as` giữa hai
lượt `am force-stop`. Bằng chứng `trip/e2e/<ca>/` (đã lọc: chỉ khoá `kachi_trip_*` của `clusternav_state`, không chép prefs).

| Ca | Làm | Kết quả | Mức |
|---|---|---|---|
| `e8-1` | Đồng hồ = *Chạy nền* | tiến trình dựng lại lúc màn TẮT (`mWakefulness=Asleep`) ⇒ claim tắt-máy; màn bật ⇒ `run trip=n39.t177132007 … deskclock:skip-SYSTEM_APP` — Đồng hồ là app HỆ THỐNG trên máy ảo (R0.6) | [ĐO] |
| `e8-2` | Waze = *Chạy nền* (nguội) | chờ BOOT 20 s (BOOT_COMPLETED của lần khởi động này chưa tới Kachi bản mới) ⇒ dàn qua ô 0 (VietMap) ⇒ `MOVED task=2373 S=174`; đỉnh display 0 = KachiHome **69/69** mẫu; **0** `am_focused_stack [0,0,…]`; `am_proc_start` + `am_on_resume_called` Waze | [ĐO] |
| `e8-3` | lần nổ máy thứ ba, Waze còn sống sau nhà | lần giết làm Waze nổi lên 1 mẫu ⇒ `recovery … nổi-lên=true` (nhóm A) ⇒ HOME; chuyến: `com.waze:bg-ALREADY_RUNNING`, 0 lệnh đổi cửa sổ | [ĐO] |
| `e8-4-screen` | tắt/bật màn KHÔNG giết | `skip trip=… why=ALREADY_FIRED`, 0 lệnh | [ĐO] |
| `e8-5-upgrade` | `install -r` giữa chuyến (màn sáng) | tiến trình mới `interactive=true` ⇒ cùng id ⇒ `ALREADY_FIRED` | [ĐO] |
| `e9-killmid` | giết Kachi lần nữa ở +6 s sau màn bật (sổ đã CLAIMED, đang chờ BOOT) | tiến trình mới: `run … tries=2 -> RAN … com.waze:bg-MOVED`; sổ `FIRED tries=2`; HOME 69/69 mẫu | [ĐO] |
| `e10a-normal-generic` | Waze = *Mở bình thường*, hồ sơ cụm máy ảo (chưa biết màn camera) | `com.waze:skip-CAMERA_UNKNOWN`, 0 lệnh | [ĐO] |
| `e10b-normal-dl3` | như trên + override hồ sơ cụm `seal_dl3` (dấu hiệu `com.byd.avc/`, máy ảo không có màn đó) | sau khi HOME yên: K10 chạy qua `launcherSeam` trên mksh ⇒ `normal-OPENED`, Waze lên trước display 0 (`am_focused_stack [0,0,204,0,reuseOrNewTask]`) — đúng nghĩa "mở bình thường", CUỐI chuỗi. Nhánh camera của K10: chỉ test (`TripPlanTest` chạy `/bin/sh` + fixture xe) + 2.83 [ĐO 29/09] | [ĐO] |
| `e12-music-link` | nhạc = YT Music, *Phát gì* = `https://youtu.be/9bZkp7q19f0?si=abc` | YT Music dàn sau nhà (`MOVED S=185`) ⇒ phiên ⇒ `playFromUri` ⇒ `uri=true playing`; `MS list` cuối: `state=3 title=Gangnam Style`; HOME 75/75; 0 `am_focused_stack [0,0,…]` | [ĐO] |
| `e12b-music-keyword` | *Phát gì* = từ khoá, YT Music hết hàng chờ (lượt trước đã dừng hẳn) | `MOVED` nhưng app nguội KHÔNG dựng phiên trong 15 s ⇒ `open-only (no session)` — không có đường giao bài mà không che màn nhà (4a). Giới hạn ghi ở spec R3.4 | [ĐO] |
| `e13-no-override` · `e13-playing` | lần nổ máy kế, YT Music đang phát trước đó | lần giết Kachi làm YT Music nổi lên rồi dừng hẳn (`state=1`, phiên không còn hoạt động); `media dispatch play` không phát lại được ⇒ chuyến: `ALREADY_RUNNING:open-only`. Vì sao YT Music dừng (bản miễn phí không phát nền khi tắt màn? hay do nổi lên/relaunch?) [CHƯA BIẾT]; nhánh "đang phát ⇒ không đè" chỉ có test (`TripMusicPlanTest`) | [ĐO] / [CHƯA BIẾT] |
| `e15-no-channel` | adbd câm (`adb reverse tcp:5555 tcp:5556`) rồi nổ máy; trả kênh ở ≈ +71 s | lúc chưa có kênh: thẻ `card show variant=LOST`, KHÔNG có dòng `KachiTrip` (chuỗi SẴN không chạy); trả kênh ⇒ `up src=f4` ⇒ `screen_on src=up` ⇒ chuyến chờ `HOME_SCREEN` → `SLOTS` ⇒ `run … ready after 89251ms … com.waze:bg-MOVED` | [ĐO] |
| `e15b-expired` | như trên, trả kênh ở ≈ +3,5 phút | `close trip=… code=EXPIRED`, 0 lệnh (Waze không chạy) | [ĐO] |
| `e2e/u6-settings/*.png` | Cài đặt bằng chạm thật | Hệ thống › Khởi động › *Mở app khi nổ máy*: chip Waze → *Chạy nền* ⇒ `ignition_apps = com.waze|B`; *+ Thêm app (1/6)* ⇒ ngăn kéo `PICK_TRIP` ("Chọn app mở khi nổ máy", ẩn Kachi, tô sẵn Waze) ⇒ chọn YT Music ⇒ *Áp dụng (2)* ⇒ `com.waze|B,com.google.android.apps.youtube.music|B`; dòng "Lần nổ máy gần nhất: đã chạy lúc 07:16". Giọng nói › *Tự mở nhạc khi lên xe*: chip YouTube Music ⇒ `ignition_music = ytmusic`; ô *Phát gì* (hộp nhập) ⇒ `ytmusic|https%3A%2F%2Fyoutu.be%2F9bZkp7q19f0`, nút đổi chữ thành "Phát gì: …" | [ĐO] |

Ghi nhận ngoài phạm vi: trên máy ảo có sẵn luật dẫn-theo-lịch `r1` (`KachiAutoNav`); khôi phục prefs (dấu `fired` cũ) làm
nó mở Google Maps lần nữa trong ngày — hành vi của tính năng lịch, không do chuyến. Sau lượt `install -r` (`e8-5`) dòng
`summary screen_on->tile=8906` (các lượt khác ≈ 330 ms) — chưa điều tra [CHƯA BIẾT].

Dọn: `restore` (`tar xf` prefs + cài lại 2.84 gốc): **9/10 sha khớp**; `clusternav_state.xml` lệch — [SUY] khoá
`kachi_trip_*` do tiến trình bản `vehicleTest` ghi trong khoảng giữa `tar xf` và lượt cài 2.84 (bản 2.84 bỏ qua khoá lạ);
không đọc lại được vì 2.84 không debuggable. Waze/YT Music `force-stop` (trước lượt đo YT Music đang là tiến trình nền
không task, Waze không chạy).

## Lượt 5 — senior review lượt 1: hai lỗi E2E đã vá (02/10) · thư mục `e2e/`

Bằng chứng chép từ lượt E2E đường chạm thật (bản release `a7476da0…`, 02/10 07:54–08:15), chỉ tệp chữ; giá trị `fp=` của
`kachi_shell_approval` trong `state-after.txt` đã thay bằng `<redacted>`. Mỗi thư mục: `kachi.txt` (log Kachi), `events.txt`
(`logcat -b events`), `stack-after.txt` (`am stack list` nguyên văn), `timeline.txt` (đỉnh display 0 mỗi giây),
`state-after.txt` (`clusternav_state.xml`).

| Ca | Thấy gì [ĐO] | Vá (spec §10 Pass 1) |
|---|---|---|
| `c5a-trip-generic` | Sau lượt `install -r` (`KachiAutostart` → `am start -n …KachiHomeActivity`), màn nhà đang hiện là task `…KachiHomeActivity` trong stack `standard` id=214, stack `home` id=0 RỖNG. Chuyến: `wait HOME_STEADY (streak=0 home=…KachiHome)` suốt 180 s ⇒ `EXPIRED`, không mở app nào. | `DefaultHome.shownComponents` (alias + activity thật) cho `TripPlan.homeTopVisible` · K10 (`CameraGuard.unlessCameraOnHome`, nhánh `*"a "*\|*"b "*`) · `BehindHomePlan.homeOnTop`. Fixture nguyên văn `core/src/test/resources/diagnostics/am-stack-list-emulator-2026-10-02-e2e-standard-home.txt` (= `stack-after.txt`). |
| `c6b-music-slot` | YT Music nằm trong ô 3: `am_kill … youtube.music … stop` 08:14:00.849 (lượt dựng ô) → `am_create_activity … MusicActivity` 08:14:02 → chuyến `music:…:resume-existing=true playing` — phiên do chính Kachi vừa tạo bị coi là "có trước", ô "Phát gì" bị bỏ. | `TripMusicPlan.preexisting(pkg, before, inSlot)`: app trong ô không bao giờ là phiên có trước. |

Phá thử 6/6 đỏ, khôi phục thì xanh (đổi `shownComponents` về chỉ alias · `homeTopVisible` chỉ dạng đầu · `preexisting` bỏ
`inSlot` · lượt trả lại chạy mỗi lần thức · K10 chỉ HOME đầu · `homeOnTop` bỏ `homeComps`).

## Lượt 6 — senior review lượt 2 (02/10 09:12–09:35) · thư mục `e2e/r2-*`

Bản release dựng từ cây làm việc (chưa commit, 2.84/185 — không bump), `install -r` lên máy ảo; cấu hình chuyến ghi thẳng
vào `kachi_workspace.xml` bằng root khi Kachi đã dừng (`ignition_apps=com.waze|B`, `ignition_music=ytmusic`), xong thì trả
nguyên `shared_prefs` cũ (tar trước/sau). Đường chạy: `tools/k.py ignite` (màn tắt → `kill -9` mọi tiến trình Kachi → màn
bật). Ô 1 = VietMap (app), ô 2 = widget YT Music. Chỉ tệp chữ; `fp=` đã che.

| Ca | Thấy gì [ĐO] | Kết luận |
|---|---|---|
| `r2-alias-trip` (bản có vá PIP/đỉnh-đang-hiện, CHƯA vá dưới đây) | `am_proc_start … youtube.music,broadcast,{…MusicWidgetProvider}` 09:16:34.666 — widget ở ô 2 bật TIẾN TRÌNH YT Music, không task. Bước nhạc 09:16:38: `→ đang chạy (pidof), 0 lệnh đổi cửa sổ` ⇒ `music:…:ALREADY_RUNNING:open-only (no session)`. Waze `bg-MOVED`; đỉnh display 0 = KachiHome 69/69 mẫu, 0 `am_focused_stack [0,0,…]`. | **Lỗi [P2]**: "đang chạy" đo bằng `pidof` ⇒ có widget YT Music trên màn nhà là nhạc lên xe không bao giờ phát. |
| `r2-widget-proc` (bản vá: "đang chạy" = có TASK trong `am stack list`) | `tools/r2-widget-proc.py` bắn broadcast cập nhật widget sau khi màn bật ⇒ `pid=17509 tasks=[]` lúc 09:23:46 (đúng trạng thái trên). Bước nhạc: `behind X=…youtube.music … → MOVED task=2552 S=271` ⇒ `music:…:MOVED:resume=true playing`; `dumpsys media_session` `state=3`. Waze `bg-MOVED`; KachiHome đỉnh display 0 73/73 mẫu, 0 `am_focused_stack [0,0,…]`. | Vá xanh trên máy ảo. Test khoá: `BehindHomeSequenceTest.chay ngam - tien trinh co ma KHONG co task (widget) van dan nhu app nguoi`. |
| `r2-music-widget` (bản vá, YT Music đã force-stop) | Lần này widget KHÔNG bật tiến trình trước bước nhạc (`am_proc_start … activity` lúc dàn) ⇒ ca "có tiến trình không task" không xảy ra; chuyến `com.waze:bg-MOVED | music:…:MOVED:resume=true playing`, KachiHome đỉnh 73/73. | Lý do có `tools/r2-widget-proc.py`: ép đúng trạng thái bằng broadcast. |
| `r2-latch-trip` → `r2-latch` (vá P2 lượt 1: lượt trả lại một lần mỗi TIẾN TRÌNH) | Chuyến đẩy Waze ra sau màn nhà, dấu `2568:com.waze`. `tools/r2-latch.py`: mở Waze toàn màn bằng Intent MAIN/LAUNCHER (như ngăn kéo — `bringingFoundTaskToFront`), tắt màn 4 s, bật màn. Cùng tiến trình Kachi (pid 21415 trước/sau). Chuỗi SẴN có chạy (`KachiReady: screen_on` 09:34:17.192 → `KachiTrip: skip … ALREADY_FIRED` 09:34:17.224) nhưng 0 dòng `KachiBehind: recovery`; đỉnh display 0 = Waze 15/15 mẫu sau khi bật màn. | Vá P2 lượt 1 xanh — chuyển từ [CHƯA BIẾT] sang [ĐO máy ảo]. |

Không tái lập được trạng thái "màn nhà = `…KachiHomeActivity` trong stack `standard`" của `c5a` bằng `install -r` (màn
sáng ×2, màn tắt ×1) hay `am force-stop` + `am start -n …KachiHomeActivity` (hệ đều đưa alias vào stack `home`; `am start`
trả `intent has been delivered to currently running top-most instance`) ⇒ vá P1 của lượt 5 vẫn chỉ được khoá bằng fixture
nguyên văn `e2e-standard-home` + bài canh nối dây, CHƯA chạy lại E2E ở đúng trạng thái đó. Chuỗi nào sinh ra trạng thái đó
ở `c5a`: [CHƯA BIẾT] — cách chốt: khi gặp lại (`am stack list` thấy stack `home` rỗng + `…KachiHomeActivity visible=true`),
chạy `k.py ignite` và chờ dòng `KachiTrip run … RAN`.

## Lượt 7 — senior review lượt 3 (02/10 09:54–10:05) · thư mục `e2e/r3-*`

Bản release dựng từ cây làm việc (`1f8e7518…7daf`, gồm vá R0.3 "đọc lại cả sau `MOVED`" của lượt 3), `install -r`; trước đo
sao lưu APK đang cài (`155e688f…59d7`) + `tar` `shared_prefs`; cấu hình chuyến ghi bằng root khi Kachi dừng
(`Mặc định__ignition_apps=com.waze|B`); xong cài lại APK cũ + trả nguyên prefs (9/10 sha khớp, `clusternav_prefs.xml` chỉ
khác `a11y_proc_start_elapsed`). Công cụ `tools/r3-pip.py` (PIP bằng `am stack move-top-activity-to-pinned-stack <stack> l t
r b` — A10 nhận BỐN số rời). Mẫu đỉnh mỗi giây ghi cả stack ĐẦU display 0 (`first0`, thường là PIP) lẫn stack ĐANG HIỆN đầu
tiên không ghim (`visible0`). Chỉ tệp chữ; `fp=` trong `state-after.txt` đã che.

| Ca | Thấy gì [ĐO] | Kết luận |
|---|---|---|
| `r3-pip-wait` (PIP dựng SAU khi màn bật, lúc chuyến đang chờ) | `pip-after-wake.txt`: stack `pinned` 294 (GMaps, `visible=true`) đứng TRƯỚC stack home. `first0` = GMaps `[pip]` **55/55** mẫu, `visible0` = KachiHome **55/55**. `KachiTrip: run trip=n39.t188635263 … -> RAN … ready after 20808ms \| com.waze:bg-MOVED`; `KachiBehind … MOVED task=2595 S=296 top0=0 kiểm=OK`. `am_focused_stack` sau PIP chỉ ở màn ảo 170. | Vá PIP của lượt 2 (`topStackId`/`topVisibleStackId` bỏ `pinned`) xanh trên đường chờ chuyến + chuỗi chạy ngầm — [SUY] → [ĐO máy ảo]. |
| `r3-pip-trip` (PIP dựng TRƯỚC khi tắt máy kiểu BYD) | `pip-before.txt`: `pinned` 286 đứng đầu display 0. 09:57:42.31 `am_remove_task [2586,286]` + `wm_stack_removed 286`: GMaps về stack toàn màn 288 ở đáy (≈ 2,4 s sau khi giết Kachi, màn tắt). Chuyến `RAN … com.waze:bg-MOVED`, `first0`=`visible0`=KachiHome 69/69. | PIP KHÔNG sống qua tắt máy kiểu BYD trên máy ảo; vì sao [CHƯA BIẾT]; ROM BYD [CHƯA BIẾT]. |
| Lẫn đo ở `r3-pip-trip` (ghi để không đọc nhầm `events.txt`) | Lượt đầu hỏng (lệnh PIP sai cú pháp `l,t,r,b`) ⇒ GMaps toàn màn, chuyến của tiến trình 26898 chờ `HOME_SCREEN`. Lượt hai dựng PIP 09:57:35 ⇒ chuyến CŨ chạy: K4 Waze vào màn ảo 168 09:57:39.48 + K3 39.72 — **sau** `screen_toggled 0` (37.915) — rồi Kachi bị giết 39.925 giữa chuỗi. | Không giữ chỗ nào dựng; task Waze trên màn ảo bị gỡ cùng màn ảo (`wm_stack_removed 287` 09:57:51.24); sau khi bật màn KachiHome ở đỉnh 69/69. Chuỗi bị cắt giữa chừng vô hại [ĐO]. |

## Lượt 8 — đóng scope (02/10 10:18–11:10) · thư mục `finish/`

Máy ảo `clusternav10` (Android 10, `emulator-5554`), luồng đo DUY NHẤT. Trước đo: sao lưu APK đang cài (2.84/185, sha256
`155e688f…59d7`) + `tar` `shared_prefs` (10 tệp, sha1 ghi lại). Đo T-M2/T-M6 bằng shell thô (`su 2000`) trên bản `vehicleTest`
cũ (`cc6049d1…9b39`, = cây làm việc trước Pass 8; cầu kiểm thử `open` = `KachiHomeSlots.openAppFullscreen` = `AppOpener.openByIntent`);
E3b · E10c · E11 · E13 trên cùng bản (mã các nhánh đó không đổi ở Pass 8); E4 · E4b · E16 · `esc-after-chain` trên bản `vehicleTest`
có mã Pass 8 (`3cacbc57…dbf5`). Công cụ `finish/tools/` (`m.py` chung, `tm2.py`, `tm6.py`, `esc.sh` chạy trên máy, `avc/AndroidManifest.xml`
của APK camera đứng thay — dựng bằng `aapt2 link` + khoá ký tạm, KHÔNG có mã, chỉ cài trên máy ảo rồi gỡ). Chỉ tệp chữ + vài ảnh;
`fp=` trong `state-after.txt` đã che.

| Thư mục | Ca | Kết quả | Mức |
|---|---|---|---|
| `tm2/a1…a4-*-intent` | **T-M2 (a)** cầu `open` (Intent từ HOME) với VietMap · YT Music · Đồng hồ · Kiki đang ở màn ảo ô | `am_new_intent` TẠI CHỖ (task 2613/2614/2618/2619), 0 `wm_task_moved`, task ở lại màn ảo, KachiHome đỉnh suốt ⇒ Intent KHÔNG tách | [ĐO] |
| `tm2/b1…b4-*-k7` | **T-M2 (b)** K7 `am start --display 0 --windowingMode 1 -f 0x20000000 -a MAIN -c LAUNCHER -n <comp>` (uid 2000) rồi HOME rồi K8 | 4/4: `wm_task_moved [task,1,0]` + `am_focused_stack [0,0,…,reparentToDisplay]`, 1920×1080, pid giữ; HOME ⇒ app ẩn trong stack riêng dưới màn nhà; K8 ⇒ `wm_task_removed [task,reParentTask]` + về màn ảo, pid giữ, 0 `am_focused_stack [0,0,…]` (Đồng hồ/Kiki `am_relaunch_resume_activity` — đổi cấu hình, tiến trình giữ) | [ĐO] |
| `tm6/m1…m4` | **T-M6** K8 từ stack display 0 ẨN về màn ảo: VietMap + YT Music (S do Kachi tạo, mang dấu — dàn bằng giọng nói "mở … vào ô 1"), Waze (`standard`, sau khi tự thoát ô), Kiki (`singleInstance`, mở toàn màn rồi HOME) | 4/4 (+ Đồng hồ ở `tm2/b3`): pid giữ, `am_focused_stack` chỉ trên màn ảo 173, 0/11–16 mẫu task `visible=true` trên display 0 | [ĐO] |
| `e3b-no-stage` | **E3b** bố cục `w_apps` · widget YT Music · đồng hồ (0 ô app), chạm Waze *Chạy ngầm* | `Refuse(reason=NO_STAGE)` + toast "Chạy ngầm cần ít nhất một ô app đang mở trên màn nhà"; 0 sự kiện, `am stack list` trước = sau | [ĐO] |
| `e11-music-in-slot` | **E11** YT Music ở ô 2, *Phát gì* = "Gangnam Style", nổ máy kiểu BYD | `music:…youtube.music:in-slot:uri=true playing`, `state=3 description=Gangnam Style, PSY`, KachiHome đỉnh 66/66 | [ĐO] |
| `e13-other-playing` | **E13 không đè** YT Music đang phát (không task), nhạc lên xe = YouTube | `music:OTHER_PLAYING`, YouTube không mở, YT Music vẫn `state=3` | [ĐO] |
| `e13b-media-unreadable` | **E13 không đọc được** thu quyền nghe thông báo của Kachi ở +4 s sau bật màn (`cmd notification disallow_listener`), trả lại sau | `music:UNKNOWN_MEDIA`, YT Music không mở; tập listener trước = sau (`notes.txt`) | [ĐO] |
| `e10c-camera-during-trip` | **E10 nhánh camera** override hồ sơ cụm `seal_dl3` (`clustercast.xml` — tệp CHƯA có trước đo, xoá sau đo) + `ignition_apps = YT Music|B, Waze|N`; camera đứng thay bật khi YT Music vào ô dàn dựng | chạy nền lùi O1 (`KEPT_UNDER … NOT_MOVED` — hệ gỡ stack giữ chỗ khi camera lên); đỉnh display 0 = camera 14/14 mẫu; K12/K10 không đè camera; *Mở bình thường* Waze chỉ mở 10:42:39, 2 s sau khi camera tắt | [ĐO] (camera đứng thay) |
| `e4-full-from-slot` · `e4b-closed-while-full` | **E4** lối tắt VietMap *Toàn màn* (đang ở ô 1) — bản Pass 8 | `DetachToFull(slot=0)` ⇒ `FULL task=2667` 0,7 s; thẻ "Đang mở toàn màn — chạm để đưa về ô" (`2-home-immediately.png`); HOME ⇒ `K8 … IN_SLOT` 0,6 s; pid 20147 giữ. App bị đóng khi toàn màn ⇒ `app đã đóng` ⇒ thẻ "App đã đóng — chạm để mở lại", 0 lệnh | [ĐO] |
| `e16-r18-shortcut` | **E16 R1.8** lối tắt *Ô 1* = YT Music (VietMap ra sau), rồi *Ô 1* = VietMap | `r1.8 vd=187 … K8 … IN_SLOT`; pid VietMap 23956 giữ; 0 `Force stopping vn.vietmap.live`; 0 `am_focused_stack [0,0,…]`; 0/45 mẫu VietMap hiện trên display 0; dấu VietMap gỡ, dấu YT Music thêm | [ĐO] |
| `esc-race` (bản trước Pass 8) | giọng nói "mở waze vào ô 1" (ô có VietMap) | Waze `launchToSide` 02.334 GIỮA đọc lại và `move-task` 02.781 ⇒ S lên trước (`[0,0,339,340,moveTaskToStack]`) ⇒ `FRONT_CHANGED` ⇒ K12; màn nhà bị che ≈ 1,9 s; Waze sau màn nhà KHÔNG dấu ⇒ fixture `esc-b-on-top` (`s036.txt`) + `esc-after-move` (`s999-after.txt`) | [ĐO] |
| `esc-after-chain` (bản Pass 8) | cùng lệnh | `MOVED kiểm=OK` 31.848, Waze `launchToSide` 32.895 SAU chuỗi ⇒ Waze toàn màn che màn nhà tới khi bấm HOME; ô trống ⇒ BACKLOG `SHORTCUTS-B-ESCAPE` | [ĐO] |
| `e4c-double-tap-final` | E4 trên bản `vehicleTest` CUỐI (`bfb69539…5e15`: + chạm đúp, + nhả ô khi toàn màn, + hâm nóng daemon ở R1.8), hai cú chạm cách 150 ms | chỉ MỘT dòng `KachiShortcut` (cú thứ hai không tới tầng chạm) ⇒ `FULL task=2701`; 9 s toàn màn, không thẻ "đã đóng"; HOME ⇒ `IN_SLOT`; pid 28730 giữ | [ĐO] (nhánh chạm đúp thật: [SUY], chỉ bài canh) |
| `break-tests-finish.log` | Phá thử (công cụ `tools/mut_finish.py`, `REPO` qua biến môi trường): lượt 1 — 9 mắt xích; lượt 2 — 11 (thêm chạm đúp, nhả ô khi toàn màn) | lượt 1 9/9 ĐỎ; lượt 2 M7 LỌT (bài thứ tự không đếm số `am force-stop`) ⇒ siết `BehindHomeWiringContractTest` ⇒ M7 ĐỎ ⇒ 11/11; khôi phục ⇒ xanh | [ĐO] |
| `break-tests-review4.log` | Phá thử của senior review lượt 4 (công cụ `tools/mut_review4.py`, `REPO` qua biến môi trường; spec §10 Pass 5): K7 xong mà app ẩn ⇒ K8 về ô ngay · `GONE` khi đọc được mà task mất · không `GONE` khi đọc hỏng · host xử lý `out.back` · `swapApp` gọi `full.reset()` | 5/5 ĐỎ; khôi phục ⇒ xanh. Mã chạy chỉ ở test off-device — chưa dựng lại trên máy ảo (cách chốt ở spec §10 Pass 5) | [ĐO] test · [SUY] máy ảo |
| `break-tests-review5.log` | Phá thử của senior review lượt 5 (công cụ `tools/mut_review5.py`, `REPO` qua biến môi trường; spec §10 Pass 6): bỏ cổng màn-nhà-ở-đỉnh (K8 dưới camera — fixture DẪN XUẤT từ `cam-standin-top`) · app rời ô sang display 0 mà không ở trước bị đo lại ô (ô đen) · coi như toàn màn cả khi app sang display khác 0 | 3/3 ĐỎ; khôi phục ⇒ xanh. Chỉ test off-device — chưa dựng trên máy ảo (cần camera đứng thay lên trong ≈ 2 s sau K7) | [ĐO] test · [SUY] máy ảo |
| `break-tests-review6.log` | Phá thử của senior review lượt 6 (công cụ `tools/mut_review6.py`, `REPO` qua biến môi trường; spec §10 Pass 7): `bringBack` bỏ cổng màn-nhà-ở-đỉnh (K8 dưới camera khi về ô từ màn nhà / chạm thẻ — fixture DẪN XUẤT từ `cam-standin-top`) · R1.8 bỏ cổng (fixture NGUYÊN VĂN `cam-standin-top`, task 2676 mang dấu) · bên thi hành trao danh sách màn nhà rỗng (bài canh `BehindHomeWiringContractTest`) | 3/3 ĐỎ; khôi phục ⇒ xanh. Chỉ test off-device — chưa dựng trên máy ảo (cần camera đứng thay lên trong ≈ 0,3 s sau HOME) | [ĐO] test · [SUY] máy ảo |

Còn [CHƯA BIẾT] — chỉ xe trả lời: K7/K8 trên ROM BYD (OC-9), camera DL5 (rào K7 chỉ còn cổng "màn nhà đang hiện"),
app nào thoát ô trên xe (`SHORTCUTS-B-ESCAPE`), cùng OC-1…OC-8.

**Dọn** [ĐO]: `am force-stop` Waze · Kiki · Đồng hồ · YT Music; gỡ APK camera đứng thay (`pm list packages` không còn `com.byd.avc`); `clustercast.xml` (override `seal_dl3`) đã xoá — tệp không có trước đo; quyền nghe thông báo trả lại (tập `enabled_notification_listeners` trước = sau); cài lại APK đang cài trước đo (`155e688f…59d7`, 2.84/185); `am force-stop` → `tar xf` đè tại chỗ → `chown 10163:10163` → `chmod 660` → `restorecon` → `am force-stop` ⇒ **10/10 sha1 khớp**; bấm HOME ⇒ KachiHome ở đỉnh display 0, VietMap trong ô 1; sau khi Kachi bật lại 9/10 khớp, `clusternav_prefs.xml` chỉ khác `a11y_proc_start_elapsed` (mốc Kachi tự ghi mỗi lần tiến trình bật — như các lượt trước). Tệp tạm trên máy (`/data/local/tmp/kprobe*`, `kprefs-finish.tar`, `/sdcard/kui.xml`) đã xoá. Sau lượt `e4c` (bản cuối) trả lại lần hai cùng quy trình: APK `155e688f…59d7` (không debuggable), **10/10 sha1 khớp**, HOME ⇒ KachiHome đỉnh display 0, VietMap ô 1. Hàng chờ/bài của YT Music (app bên thứ ba) đổi theo các ca nhạc — không trả được.
