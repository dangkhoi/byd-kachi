# Buổi xe 2026-09-29: gốc rễ phím vô-lăng chết khi tắt máy, nghiệm thu 2.82 cài tay, GMaps đổi đích

> **Trạng thái**: Current · **Cập nhật**: 2026-09-29 · **Chủ**: dangkhoi · **Mục đích**: ghi lại kết quả buổi xe 29/09 11:07–12:18: gốc rễ phím vô-lăng chết, kết quả nghiệm thu 2.82 cài tay, thử GMaps đổi đích, và quyết định của owner dẫn tới 2.83. Chỉ ghi phát hiện. Trong buổi không sửa mã.
>
> **Xe**: Seal DiLink 3, Android 10. adb qua cầu nc loopback (`<ip-xe>`, xem `docs/diagnostics/adb-car-tunnel-macos.md`). Lúc lên xe, bản đang chạy là **2.81 (182)** [ĐO `key5-state-113855.txt`]. Lúc 11:39:13 (giờ Mac) cài tay **2.82 (183)** bằng `adb install -r`, giữ dữ liệu, **không qua OTA** [ĐO `inst-state.txt`, transcript].
>
> **Bằng chứng**: nằm trong thư mục bằng chứng ngoài repo `oncar-0929/`. Thư mục này không commit. Ảnh, IP, đích dẫn đường và `getprop` đầy đủ chỉ có ở đó, tài liệu này không chép lại. Tài liệu chỉ ghi **tên tệp** (§9). Giờ ghi theo đồng hồ xe (logcat), trừ chỗ ghi "giờ Mac"; hai đồng hồ lệch nhau dưới 1 s. Lời owner lấy từ transcript của phiên, theo giờ VN.
>
> **Nhãn** (CLAUDE.md §2, `.kiro/steering/conversation-protocol.md`):
> - **[ĐO]**: dump hoặc log thật, đã đọc lại khi viết tài liệu này.
> - **[SUY]**: suy luận khớp dữ liệu nhưng chưa đo trực tiếp.
> - **[ĐOÁN]**: mới chỉ là giả thuyết.
> - **[CHƯA BIẾT]**: chưa có dữ liệu.
> - **[ĐO theo spec]**: trích AOSP mà `docs/specs/kachi-a11y-bind-stuck-autofix.html` đã đọc ở tag `android-10.0.0_r47`. Lúc viết buổi sáng, tài liệu này **không** đọc lại source AOSP. **Chiều 29/09 đã fetch lại** `android-10.0.0_r47` và `android-12.0.0_r34` (qua bản mirror, vì nguồn chính trả 503) theo CLAUDE.md §3 — kết quả ở §1.4 (khối *Fetch lại 29/09*) và spec 2.83 §4.9.
>
> **Việc tiếp theo**: 2.83, xem `docs/specs/kachi-283-key-heal-acc-off.html` (§7). **Trạng thái cuối 29/09**: **2.83 (184) đã lên kênh OTA (29/09)** — spec 2.83 đã duyệt; owner 14:05:56 cho **OTA thẳng cho anh em test** sau cổng off-car (§6); chi tiết bản đăng và kết quả cổng ở §7. Chờ phản hồi anh em.

---

## 0. Tóm tắt

1. **Gốc rễ, [ĐO] qua 3 lượt:** mỗi lần tắt máy xe, system_server giết hàng loạt gói với lý do `stop <gói>`. Kachi mất cả 2 đến 3 tiến trình. Vì Kachi là HOME nên hệ thống dựng lại nó sau **0,25–0,33 s**.
2. **Lượt giết này không gửi `PACKAGE_RESTARTED`** [ĐO ở lượt 2]. Vì vậy AOSP không chạy `onHandleForceStop`, và dịch vụ Hỗ trợ của Kachi nằm kẹt trong `Binding services`, không được bind lại. Mức bằng chứng tách ba tầng: cơ chế là [ĐO theo spec], việc áp cơ chế đó vào ca này là [SUY], còn trạng thái cuối là [ĐO].
3. Thủ phạm nhiều khả năng là `AccModeManagerService` của BYD [SUY]. Nội dung log của nó đã bị chatty cắt mất.
4. **Tái hiện từ trạng thái sạch** [ĐO]: lúc 11:33:22 dịch vụ còn Bound. Tắt rồi bật máy, đến 11:34:29 đã STUCK. Owner lúc 11:36:21: *"phím mất bind rồi nhé"*.
5. **Tự chữa của 2.81 có nhận ra kẹt nhưng không leo nấc** [ĐO log và mã]. Cổng "có app khách" chặn nó lại, vì ô đã mở YouTube xong **trước** khi kịp phân loại STUCK — [ĐO] ở ca sáng (`k1-stacks-110944.txt:9-11`), [SUY mạnh] ở ca 11:34 (không có dump stack lúc đó; chỉ có `am_create_activity` YouTube 11:34:20.849).
6. Có hai đường chữa đã đo được: `am force-stop` thật (nút *Sửa ngay*) và cài đè gói [ĐO]. *Sửa ngay* để lại hai hậu quả: launcher không tự lên, và một cửa sổ YouTube freeform nằm trên màn chính. Cửa sổ này do **chính Kachi mở** [ĐO `launchedFromPackage`]. Nhờ vậy cũng biết freeform chạy được trên xe.
7. Màn Chẩn đoán không mở được trên bản phát hành, nên nhật ký bền của dịch vụ Hỗ trợ không có đường nào để đọc [ĐO mã, ĐO lượt KEY-4].
8. Kết quả 2.82 cài tay: phím Bound lại, phím 328 ăn. SCHED-1, CAM-KEEP, CAM-B5, CAM-SEAL và GUIDE-1 đạt. Chế độ kiểm thử sống qua lần cài đè [ĐO].
9. GMaps: thêm cờ `CLEAR_TASK` (`-f 0x10008000`) thì mất hộp thoại "Thoát chế độ đi theo chỉ dẫn?" và GMaps dẫn thẳng tới đích mới [ĐO, owner xác nhận]. VietMap chưa đo. Hướng AVM 360 đóng hẳn. Trên Seal, khoá phân biệt đời xe là `persist.sys.car.type=138`.
10. **Owner quyết** dẹp vạch chuẩn [ĐO 12:02:39]. **Không đăng 2.82**: [SUY] phiên đề xuất lúc 12:01:24, owner không phản đối và duyệt kế hoạch 2.83 (12:34:54) — không có câu owner nói thẳng về OTA 2.82. Gom thành 2.83 gồm A (chữa kẹt phím khi tắt/bật máy), B (*Sửa ngay* phải về HOME và không để app mồ côi), C (nhật ký đọc được), D (gỡ vạch) và E (`CLEAR_TASK` cho lệnh dẫn đường).

---

## 1. Gốc rễ phím vô-lăng chết

Dịch vụ bị kẹt là `com.byd.launcher/com.byd.clusternav.modules.navaccess.NavAccessibilityService`, bộ nhận phím 328 và 304.

### 1.1 Chuỗi mắt xích

| # | Mắt xích | Mức | Nguồn |
|---|---|---|---|
| 1 | Tắt máy (`power_sleep_requested 1`) kéo theo một đợt `am_kill … stop <gói>`. Lượt 2 trúng 25 gói khác nhau, lượt 0 trúng 21 gói. Kachi mất `:wake`, `:tts` (nếu đang chạy) và tiến trình chính | [ĐO] 3 lượt: 18:25:41 (28/09), 11:20:50 và 11:33:25 | `k1-logcat-110944.txt:105,304-307` · `c1-logcat-112312.txt:3341,3590-3592` · `c2-logcat-113538.txt:3198,3414-3418` |
| 2 | Kachi được dựng lại vì là HOME (`am_proc_start … activity {…/KachiHome}`), sau 0,252 s, 0,314 s và 0,333 s | [ĐO] | `k1-logcat:339` · `c1-logcat:3650` · `c2-logcat:3479` |
| 3 | Thủ phạm là `AccModeManagerService` của BYD. Luồng `AccModeManagerS` của system_server ghi 41 dòng lúc 11:33:23.555, 12 ms trước `power_sleep_requested`, và ghi 51 dòng lúc 11:20:48.403, ngay trước lượt 1. Chatty đã cắt nội dung. Lớp `com.android.server.accmodemanager.AccModeManagerService` có trong `services_NEW.jar` của firmware (mới chạy `strings`) | Tên thủ phạm: [SUY]. Các dòng chatty: [ĐO] | `c2-logcat:533,3189` · `../firmware/fw-2602-diff/cmp/services_NEW.jar` |
| 4 | Lượt giết của BYD **không** gửi `PACKAGE_RESTARTED`: không cho Kachi, và không cho 24 gói khác cùng đợt | [ĐO] ở lượt 2. Lượt 0 và 1: [SUY], vì cửa sổ lịch sử broadcast không phủ tới (§1.3) | `c2-bcast-113538.txt` |
| 5 | Không có broadcast thì không có `onHandleForceStop`. Bằng chứng gián tiếp: ở cả 3 lần thức, Kachi đọc thấy mình **vẫn còn** trong `enabled_accessibility_services` (`đã có sẵn=true`), trong khi `onHandleForceStop` lẽ ra đã gỡ dòng này | Dòng log: [ĐO]. "Không chạy": [SUY chặt] | `usage-night.log:1540` · `usage-cycle1.log` · `usage-cycle2.log` · `app/src/main/java/com/byd/clusternav/NavConnect.kt:219,229` |
| 6 | Dịch vụ đang Bound thì chết theo tiến trình, bị đẩy ngược vào `mBindingServices`, và `updateServicesLocked` bỏ qua nó mãi mãi | Cơ chế: [ĐO theo spec]. Áp vào ca này: [SUY]. Trạng thái cuối: [ĐO] (Binding có Kachi, không có ServiceRecord) | `k1-acc-110944.txt:6` · `c2-acc-113538.txt` · `k1-svc-110944.txt` · `c2-svc-113538.txt` |
| 7 | Tự chữa 2.81 ra kết quả `kẹt=true, không app khách=false … → nấc NONE, KHÔNG leo`, vì ô đã có YouTube hiện | Dòng log + mã: [ĐO]. "YouTube hiện trong ô": [ĐO] ca sáng (`k1-stacks-110944.txt:9-11`), [SUY mạnh] ca 11:34 (xem §1.7) | §1.7 |
| 8 | Có hai đường chữa được: `am force-stop` thật (có `PACKAGE_RESTARTED`) và cài đè gói | [ĐO] | `fix-bcast-112813.txt:3141` · `fix2-acc-112916.txt` · `usage-282-120941.log:209` |

**Tái hiện sạch và phạm vi của nó.** Trong buổi đo được 3 lượt giết:

| Lượt | Trạng thái trước lượt giết | Trạng thái sau |
|---|---|---|
| 0 | Sạch lúc 17:53 ngày 28/09 [ĐO 28/09, `docs/PROJECT-BACKLOG.md` mục *MỐC GỐC 2026-09-28 17:53*]. Từ 17:53 tới lúc giết 18:25:41 còn một khe **32 phút chưa đo** | Kẹt khi đo sáng hôm sau |
| 1 | **Đã kẹt sẵn** từ trước (`poll-cycle1.txt` 11:19:44–11:20:47: `binding=1 pid=682`) | Lượt này chỉ cho thấy lượt giết lặp lại, không chứng minh nhân quả |
| 2 | Sạch lúc 11:33:22, cách lượt giết chỉ **3 s** | Kẹt lúc 11:34:29. Đây là **phép thử nhân quả sạch nhất** |

### 1.2 Dòng thời gian

| Giờ | Sự kiện | Mức | Nguồn |
|---|---|---|---|
| 28/09 15:57:18 | `SYSTEM_BOOT`, tức đầu xe reboot. Không phải do tự chữa | [ĐO] | `k2-dropbox-111357.txt` · `k1-clock-110944.txt` |
| 28/09 15:57:24.612 | PID 3920 ghi `accessibility booster connected` | [ĐO] | `usage-before.log:190` |
| 28/09 17:53 | Mốc gốc: Binding rỗng, dịch vụ Bound | [ĐO 28/09] | `docs/PROJECT-BACKLOG.md` mục *MỐC GỐC 2026-09-28 17:53* |
| 28/09 18:25:39.429 | `power_sleep_requested 1` | [ĐO] | `k1-logcat:105` |
| 28/09 18:25:41.935–.941 | Giết `:wake` 4238, `:tts` 15430 và tiến trình chính 3920 | [ĐO] | `k1-logcat:304-307` |
| 28/09 18:25:42.193 | Dựng lại PID 682 (KachiHome). Suốt đời PID 682 không có dòng `booster connected` nào | [ĐO] | `k1-logcat:339` · `usage-night.log` |
| Đêm 28→29 | Không reboot. Xe tắt từ `power_sleep_requested` 18:25:39.429 tới màn bật 11:07:15.136 ≈ 16 g 41 ph. (Số "ngủ sâu 17 g 09 ph trên 19 g 13 ph" là **tổng từ lúc boot 15:57:18**, meminfo `Uptime 7442606`, `Realtime 69180764` — không phải riêng đêm nay.) PID 682 không ghi dòng a11y nào | [ĐO] | `k1-clock-110944.txt` · `k1-logcat:105,4265` · `usage-night.log` |
| 29/09 11:07:15.136 | Màn hình bật | [ĐO] | `k1-logcat` |
| 11:07:21.346 | Ô dựng màn ảo (display 3) | [ĐO] | `usage-night.log` |
| 11:07:21.622 | `grantAccessibility xong (đã có sẵn=true)` | [ĐO] | `usage-night.log:1540` |
| 11:07:23.217 | YouTube khởi động vào ô | [ĐO] | `k1-logcat` |
| 11:07:25.942 | `A11yJournal a11y BOUND → NOT_BOUND (wake)` | [ĐO] | `usage-night.log` |
| 11:07:31.581 → .747 | Toggle xong nhưng `bound=false`, chuyển sang `NOT_BOUND → STUCK` | [ĐO] | `usage-night.log` |
| 11:08:56 | Owner: *"đã lên xe, ko tìm thấy màn chẩn đoán, phím bind tạch rồi nhé"* | [ĐO] | transcript |
| 11:09:04.993 | Cổng: `… ô của mình=[3], tay=false) → nấc NONE, KHÔNG leo` | [ĐO] | `k1-logcat` |
| 11:09:44 | Binding có Kachi, Bound chỉ có StatusBar, không có ServiceRecord, YouTube `visible=true` trên display 3 | [ĐO] | `k1-acc-110944.txt:4-6` · `k1-svc-110944.txt` · `k1-stacks-110944.txt:9-11` |
| 11:20:48.433 → 11:20:50.165 | **Lượt 1** (lúc đã kẹt sẵn): giết `:wake` 1293 và tiến trình chính 682. Dựng lại PID 20517 lúc 11:20:50.479 | [ĐO] | `c1-logcat:3341,3590-3592,3650` |
| 11:22:15.490 | Cổng: `ô của mình=[4]` → NONE | [ĐO] | `usage-cycle1.log` |
| 11:25:54.222–.225 | **Sửa ngay**: giết `:wake` và tiến trình chính. `PACKAGE_RESTARTED` cho `com.byd.launcher` vào hàng lúc 11:25:54.368 | [ĐO] | `c2-logcat:2061-2064` · `fix-bcast-112813.txt:3141-3144` |
| 11:25:58.662 | PID 25367 `booster connected`. Phím 328 ăn lúc 11:27:34.309 và 11:30:41.658 | [ĐO] | `usage-afterfix.log:155,418,882` |
| 11:33:22 | Bộ ghi đúng: `bound=1 binding=0 svcrec=1 pid=25367` | [ĐO] | `poll-cycle2.txt` (dòng cuối) |
| 11:33:23.555 / .567 | `AccModeManagerS expire 41 lines`, rồi `power_sleep_requested 1` | [ĐO] | `c2-logcat:3189,3198` |
| 11:33:25.170–.182 | **Lượt 2**: giết `:wake` 25437, `:tts` 26503 và tiến trình chính 25367 | [ĐO] | `c2-logcat:3414-3418` |
| 11:33:25.515 | Dựng lại PID 29552 | [ĐO] | `c2-logcat:3479` |
| 11:34:19.114 → 11:34:29.704 | Ô dựng (display 6), toggle không ăn, STUCK lúc 11:34:29.238, cổng ra NONE | [ĐO] | `usage-cycle2.log` |
| 11:34:48 | `bound=0 binding=1 svcrec=0 pid=29552` | [ĐO] | `poll-cycle2-after.txt` (dòng đầu) |
| 11:35:38 | Dòng 4–7 của `c2-acc` trùng từng ký tự với `k1-acc`. `ConnectionRecord{1c1ce67…}`, cái đang sống lúc 11:30:16, nay nằm mồ côi và không có ServiceRecord | [ĐO] | `c2-acc-113538.txt` · `c2-svc-113538.txt:52-57` · `fix2-svc-113016.txt` |
| 11:36:21 | Owner: *"phím mất bind rồi nhé"* | [ĐO] | transcript |
| 11:39:13 (giờ Mac) | Cài đè 2.82 (183), kết quả `Success` | [ĐO] | transcript |
| 11:39:41.026 / 11:39:46.300 | PID 4628 `booster connected`, rồi `A11yJournal a11y STUCK → BOUND (sau-chua-ON)` | [ĐO] | `usage-282-120941.log:14,209` |
| 11:39:52 | `bound=1 binding=0 svcrec=1 pid=4628` | [ĐO] | `inst-poll.txt` |
| 11:47:29.656 | `onKeyEvent DOWN keycode=328` | [ĐO] | `usage-282-120941.log:1763` |

[CHƯA ĐO] trạng thái Binding trong khoảng 11:33:25–11:34:48, vì bộ ghi adb không chạy được lúc xe tắt máy.

### 1.3 Đối chứng `PACKAGE_RESTARTED`: lượt giết của BYD và *Sửa ngay*

`PACKAGE_RESTARTED` đi hàng đợi **nền**. Cả 4 bản ghi thấy được đều nằm trong khối `Historical broadcasts summary [background]` [ĐO]. Khối này giữ khoảng 300 mục, nên chỉ phủ được vài phút.

| Tệp | Cửa sổ khối nền (enq cũ nhất → mới nhất) | Lượt cần xét | Có phủ lượt đó? | `PACKAGE_RESTARTED` trong khối | Kết luận |
|---|---|---|---|---|---|
| `k2-bcast-111357.txt` | 11:09:38.811 → 11:13:58.421 | Lượt 0 (18:25:41) | Không | không có | [CHƯA ĐO] trực tiếp |
| `c1-bcast-112353.txt` | 11:21:19.605 → 11:23:49.940 | Lượt 1 (11:20:50) | Không | #195 cho YouTube, enq 11:22:05.516 | [CHƯA ĐO] trực tiếp |
| `fix-bcast-112813.txt` | 11:24:07.547 → 11:28:18.663 | Sửa ngay (11:25:54) | Có | **#179 cho `com.byd.launcher`, enq 11:25:54.368**, 143 ms sau `am_kill` | [ĐO] **có** |
| `c2-bcast-113538.txt` | 11:33:23.918 → 11:35:40.626 | Lượt 2 (11:33:25.18) | Có | chỉ có #161 cho YouTube, enq 11:34:19.651 | [ĐO] **không có cho Kachi** |

- Cửa sổ `c2` phủ được lượt 2 vì mục cũ nhất (enq 11:33:23.918) có trước lượt giết [ĐO]. Hàng nền lúc tắt máy bị dồn: các mục enq từ 11:33:23.9 tới 11:33:24.8 chỉ được dispatch từ 11:33:25.915 trở đi [ĐO]. Nếu có một broadcast phát lúc 11:33:25.18 thì nó phải còn trong vòng đệm [SUY, dựa trên thứ tự FIFO].
- Mục #161 (YouTube) ứng với **một lượt giết khác**, lúc 11:34:19.601 (`am_kill … youtube,700,stop …`), và đến sau lượt đó 50 ms. Điều này chứng tỏ khi force-stop đi đường bình thường thì broadcast có mặt và có được ghi lại [ĐO]. Thời điểm của lượt giết này trùng lúc Kachi dựng ô, nên [SUY] chính Kachi gọi nó.
- **Chỉ nhìn `am_kill` thì không phân biệt được** lượt giết của BYD với `am force-stop`, vì cả hai cùng in chuỗi lý do `stop com.byd.launcher` [ĐO]. Phân biệt được bằng `PACKAGE_RESTARTED` **và** bằng `am_schedule_service_restart`: *Sửa ngay* 11:25:54 có 4 dòng cho dịch vụ Kachi (VoiceWakeService, FloatingBubbleService, AutomationService, VoiceKeyKeepAliveService — `c2-logcat:2062-2069`), còn cả 3 lượt BYD không có dòng nào cho Kachi (lượt 11:33:25 chỉ có `com.byd.mediacenter`, `:3533`) [ĐO]. Mới một mẫu force-stop, nên dùng làm dấu phân biệt là [SUY].

### 1.4 Cơ chế AOSP ([ĐO theo spec] lúc viết; fetch lại chiều 29/09 — xem khối cuối mục)

| AOSP `file:line` (android-10.0.0_r47) | Nội dung | Dòng trong spec |
|---|---|---|
| `AccessibilityManagerService.java:4114-4117` | `serviceDisconnectedLocked()` gọi `removeServiceLocked(…)` rồi `mBindingServices.add(component)`. Tức là dịch vụ đang gắn mà bị đứt thì bị đẩy ngược vào Binding | `kachi-a11y-bind-stuck-autofix.html` §4.2 (bảng AOSP) + §4.3 |
| `AccessibilityServiceConnection.java:265` / `:209` | Tới đoạn trên qua `binderDied()`, hàm được gọi cả từ death-recipient lẫn `onServiceDisconnected()` | cùng bảng §4.2 |
| `AccessibilityManagerService.java:398`, `:410` | Việc park trong `binderDied()` là "during updating". Nơi dọn là `onPackageUpdateFinished` | cùng bảng §4.2 |
| `AccessibilityManagerService.java:1630-1631` | `updateServicesLocked`: `if (mBindingServices.contains(componentName)) continue;` đứng trên cả `bindLocked()` lẫn `unbindLocked()` | cùng bảng §4.2 + §4.3 |
| `AccessibilityManagerService.java:453-484` | `onHandleForceStop` gỡ dịch vụ khỏi enabled (`:473`) và khỏi binding (`:474`), rồi ghi bền (`:475-477`) | cùng bảng §4.2 |
| `PackageMonitor.java:412-416` · `ActivityManagerService.java:4515-4517` | `onHandleForceStop` được kích bởi `ACTION_PACKAGE_RESTARTED`, broadcast này do `finishForceStopPackageLocked` gửi. Các nơi gọi: `am force-stop` (`:4280`) và `pm clear` (`:4075`) | cùng bảng §4.2 |

Tách **cơ chế** khỏi **quy kết** theo CLAUDE.md §2:

| Mệnh đề | Mức |
|---|---|
| Mục nằm trong `mBindingServices` thì không được bind lại, chỉ `onHandleForceStop` hoặc `onPackageUpdateFinished` gỡ được nó | Cơ chế: [ĐO theo spec] |
| Ở ca này, dịch vụ đang Bound lúc tiến trình bị giết nên đi đường `binderDied` rồi bị park | [SUY]. Khớp thời điểm: còn Bound lúc 11:33:22, bị giết lúc 11:33:25.18, kẹt lúc 11:34:48 |
| Sau lượt giết của BYD không còn ServiceRecord (`k1-svc`, `c2-svc`), còn sau *Sửa ngay* thì có (`fix2-svc`). Tức service bị hạ kiểu force-stop, không phải crash rồi tự khởi động lại | Phần ServiceRecord: [ĐO]. Phần diễn giải: [SUY] |
| Tổ hợp tệ nhất: service bị hạ như force-stop, nhưng **không** có broadcast dọn như force-stop thật | [SUY] |

**Fetch lại 29/09 (chiều)** — [ĐO AOSP] tag `android-10.0.0_r47` qua bản mirror của `platform_frameworks_base` (nguồn chính `android.googlesource.com` trả 503 cả 7 lần). Chi tiết `file:line` ở spec 2.83 §4.9.

| Mệnh đề | Kết quả |
|---|---|
| Bảng trên (`:4114-4117`, `:1630-1631`, `:453-484`, `PackageMonitor.java:412-416`, `:4280`) | Đúng. Sửa nhỏ: `continue` ở `:1630-1632`; `onPackageUpdateFinished` là `:398-417`, chú thích *"during updating"* ở `:400-403` (không phải `:398`), `removeIf` ở `:410-412`; `finishForceStopPackageLocked` định nghĩa ở `:4516-4529` |
| **Điều kiện mới** | `onHandleForceStop` duyệt `mEnabledServices` (`:464-469`) ⇒ chỉ gỡ mục Binding khi component **đang có** trong danh sách enabled lúc broadcast tới |
| `killApplication` (`ActivityManagerService.java:4314-4339`, chỉ uid system) → `forceStopPackageLocked` (`:1732`) | Cùng tham số với `am force-stop` nhưng **không** gọi `finishForceStopPackageLocked` ⇒ không `PACKAGE_RESTARTED`. Đây là cách E2E máy ảo tái hiện đúng chữ ký lượt giết lúc tắt máy. BYD có gọi đúng hàm này không: [CHƯA BIẾT] (§1.8). Quan sát để chốt: dòng `Force stopping <gói> … : <lý do>` (AMS `:4642-4643`) lúc tắt máy — lý do `from pid N` là đường `forceStopPackage` |
| Android 12 (DL5, `android-12.0.0_r34`) | **Khác**: mối nối đứt đẩy vào `mCrashedServices` (`AccessibilityUserState.java:254-257`), không vào `mBindingServices`; gỡ component khỏi `enabled_accessibility_services` dọn mục crashed ⇒ toggle rẻ [SUY từ AOSP] chữa được ca đó. ROM DL5 có giữ AOSP gốc không: [CHƯA BIẾT] — chốt bằng một `dumpsys accessibility` trên DL5 sau một lần tắt máy |

### 1.5 Vì sao "cài mới thì OK"

- Cài đè gọi `onPackageUpdateFinished`, hàm này dọn mục kẹt trong `mBindingServices` [ĐO theo spec `:410`; việc áp vào ca này là SUY]. Kết quả đo được [ĐO]: lúc 11:35:10 còn `binding=1 svcrec=0`, sau khi cài thì có `STUCK → BOUND` lúc 11:39:46 và phím 328 ăn lúc 11:47:29.
- Hiệu lực của việc cài chỉ kéo dài **tới lần tắt máy kế tiếp**. Lượt giết của BYD không phụ thuộc bản cài, nên lần tắt máy sau sẽ kẹt lại [SUY, khớp mẫu "cài mới tốt, qua đêm kẹt"].
- Dòng nhật ký `STUCK → BOUND (sau-chua-ON)` lúc 11:39:46 là **ghi sai nhãn**: không có lượt chữa nào chạy, chính việc cài đè đã dọn [ĐO]. Runbook đã cảnh báo điều này (`docs/diagnostics/oncar-runbook-2.82.md` §4 INST, ghi chú nhãn `sau-chua-*`).

### 1.6 Vì sao thấy vào buổi sáng

- Thứ gây kẹt là **một lần tắt máy**, không phải "một đêm" hay "ngủ sâu". Ở lượt 2, xe chỉ tắt khoảng 50 s (tắt lúc 11:33:23.567, `SCREEN_ON` lúc 11:34:13.236) mà đã kẹt [ĐO].
- Người dùng thấy triệu chứng vào buổi sáng chỉ vì đó thường là lần đầu bấm phím sau một lần tắt máy [SUY]. Hai lượt 1 và 2 đều vào giữa trưa, sau những lần dừng xe ngắn, và cũng kẹt [ĐO].
- Con số "tiến trình sống liên tục 10 g 13 ph qua đêm ⇒ không bị giết" trong spec (`kachi-a11y-bind-stuck-autofix.html` §2.2) **không loại trừ** được lượt giết lúc tắt máy. Sáng 29/09, PID 682 cũng "sống liên tục qua đêm" (`ELAPSED 16:44:04`, `k1-ps-110944.txt`), nhưng chính nó sinh ra từ lượt giết 18:25:41. Số đo 29/09 là [ĐO]. Việc áp lại cho đêm 27→28 là [CHƯA BIẾT], vì log đêm đó không còn.

### 1.7 Vì sao tự chữa 2.81 không chạy

Thứ tự early-return trong `core/src/main/kotlin/com/byd/clusternav/modules/navaccess/AccessibilityHealGates.kt:100-106` [ĐO mã]:

| Dòng | Điều kiện | Kết quả | Giá trị trong ca này |
|---|---|---|---|
| `:100` | `bound` | NONE | `false` |
| `:101` | `!wanted && !userAsked` | NONE | Không dừng ở đây. [SUY] `wanted = true`, vì watchdog phím-thoại có chạy (`VoiceKeyKeepAlive … re-grant` lúc 11:07:25) |
| `:102` | `!stuckInBinding` | TOGGLE | `kẹt=true` [ĐO log] |
| `:103` | `userAsked` | FORCE_STOP | `tay=false` [ĐO log] |
| **`:104`** | **`guestAppVisible`** | **NONE** | Log in `không app khách=false`, tức `guestAppVisible = true`. **Dừng ở đây**. Lưu ý: `noGuestAppVisible` cũng trả `false` khi `am stack list` rỗng, tập VD `null` hoặc tên gói trống (`StackParse.kt:206` @`bfbb728`) ⇒ riêng dòng log không chứng minh có app hiện; ca sáng có `k1-stacks-110944.txt:9-11` (YouTube `visible=true` display 3) [ĐO], ca 11:34 không có dump stack ⇒ [SUY mạnh] |
| `:105` | `escalatedThisBoot(…)` | NONE | Không tới. [SUY] Sau *Sửa ngay* lúc 11:25:54 thì cổng này cũng sẽ chặn lượt 11:34, vì mốc leo chỉ được nhả khi xe ngủ sâu ≥ 2 giờ |

- Cổng được tính ở `app/src/main/java/com/byd/clusternav/NavConnect.kt:279,287`. Hàm dùng là `StackParse.noGuestAppVisible` (`core/src/main/kotlin/com/byd/clusternav/modules/clustercast/StackParse.kt:205-209`), trả `false` khi có một stack `visible` của gói khác nằm ở display 0 hoặc ở màn ảo của chính mình. Log ở `NavConnect.kt:292` được lặp lại khoảng 30 s một lần: 11:09:04, 11:09:34, 11:22:15, 11:34:29, 11:35:02, 11:35:32 [ĐO]. Nghĩa là cổng **đóng vĩnh viễn** chừng nào ô còn chứa app.
- **Tiền đề thiết kế bị số đo bác bỏ.** `AccessibilityHealGates.kt:84-85` giả định "lúc xe vừa thức thì ô còn rỗng". Thực tế đo được [ĐO]:

  | | Sáng 11:07 | Lượt 2 |
  |---|---|---|
  | Ô dựng lúc | 11:07:21.346 | 11:34:19.114 |
  | YouTube vào ô lúc | 11:07:23.217 | khoảng 11:34:20.8 |
  | Phân loại STUCK lúc | 11:07:31.747 | 11:34:29.238 |

  Trình tự này là **cấu trúc**: bước toggle mất khoảng 8 s mới biết không ăn, nên lần nào cổng cũng thua cuộc đua.

### 1.8 Còn [SUY] và [CHƯA BIẾT], kèm cách chốt

| Câu hỏi | Mức | Cách chốt |
|---|---|---|
| Thủ phạm có đúng là `AccModeManagerService` không | [SUY] | Dịch ngược `../firmware/fw-2602-diff/cmp/services_NEW.jar` bằng jadx, đọc `AccModeManagerService` / `AccModeManagerHandler` để tìm lời gọi xuống ActivityManagerService |
| BYD gọi hàm giết nào: `forceStopPackageLocked` nội bộ, hay biến thể `forceStopPackageLockedEx` / `killPackageProcessesLockedEx` (tên thấy qua `strings`), và biến thể đó có bỏ qua `finishForceStopPackageLocked` không | [CHƯA BIẾT] | Cùng lượt dịch ngược ở trên |
| BYD có danh sách miễn giết không. Firmware có `setPkg2AccWhiteList` / `rmPkg2AccWhiteList` (`../firmware/fw-2602-diff/jadx-l3-new/sources/com/byd/dilink50/os/DiLinkAccModeManager.java:61-118`) | [CHƯA BIẾT]: ý nghĩa, quyền cần có, ảnh hưởng tới ắc quy 12 V | Bước 1 chỉ đọc trên xe: `service list \| grep -i acc`, `dumpsys accmodemanager`. Nếu dùng thì đây là state sống lâu hơn tiến trình, nên phải có đường gỡ lại (CLAUDE.md §5) |
| Lượt 0 và lượt 1 có thiếu `PACKAGE_RESTARTED` không | [SUY chặt] | Chụp `dumpsys activity broadcasts` trong vòng khoảng 2 phút sau khi mở máy, đọc khối `[background]` |
| Nội dung 41 dòng `AccModeManagerS` đã bị chatty cắt | [CHƯA BIẾT] | [ĐOÁN] Đưa uid 1000 vào whitelist prune của logd (`logcat -p` để lưu danh sách cũ, `logcat -P` để đặt), rồi trả lại danh sách cũ. Lệnh này chưa kiểm trên ROM DiLink |
| Trạng thái Binding trong khoảng 11:33:25–11:34:48 | [CHƯA ĐO] | Dùng bộ ghi chạy **trên** xe (ClusterDiag, CLAUDE.md §11), vì adb qua WiFi rớt khi tắt máy |
| Nội bộ system_server (`binderDied` → park → `continue`) | [SUY] | Không có log nội bộ. Mọi dữ kiện bên ngoài đều khớp |

---

## 2. Các bẫy đo đã dính và cách đo đúng

### 2.1 Đếm "Bound" bằng grep một dòng: bẫy đã dính trong phiên

| Bộ ghi | Lệnh đếm `bound` | Đúng hay sai |
|---|---|---|
| `poll-cycle1.txt`, `fix-poll.txt` | `grep "Bound services" … \| grep -c byd.launcher` | **SAI** |
| `poll-cycle2.txt`, `poll-cycle2-after.txt`, `inst-poll.txt` | đọc trọn khối Bound…Enabled, cộng ServiceRecord, cộng khối Binding | Đúng |

Lệnh cũ sai ở hai chỗ [ĐO trên `fix2-acc-112916.txt:4-7`]:

1. AOSP in **mỗi dịch vụ Bound thêm ra một dòng riêng**. Dòng 4 là `Bound services:{Service[label=.custom.StatusBarAcces…`, còn Kachi nằm ở dòng 5, là dòng tiếp nối: `Service[label=ClusterNav — booster đọ…`.
2. ROM DiLink in khối Bound **chỉ bằng nhãn**, không in tên gói. Vì vậy `grep -c byd.launcher` ra 0 ngay cả khi đã gộp hai dòng.

Bẫy chỉ lộ khi dịch vụ đang khoẻ. Lúc kẹt, khối Bound chỉ có một dòng (StatusBar), nên lệnh sai tình cờ ra đúng số 0 [ĐO `k1-acc`].

Chứng minh bằng mâu thuẫn [ĐO]: `fix-poll.txt` ghi `bound=0` liên tục từ 11:26:22 tới 11:27:11. Trong khi đó PID 25367 đã `booster connected` từ 11:25:58.662 và ăn phím 328 lúc 11:27:34.309. Owner bắt lỗi lúc 11:28:49: *"phím thì đã bind OK, vừa nhấn ăn rồi, chỉ là giao diện loạn -> sau khi sửa thì phím ăn rồi nhé, không phải là không bind được, vậy đang check sai chỗ rồi"*.

Parser của Kachi không dính bẫy này. `AccessibilityRebind.isClusterNavBound` (`core/src/main/kotlin/com/byd/clusternav/modules/navaccess/AccessibilityRebind.kt:117`) quét cân bằng ngoặc `{…}` trên toàn khối, và có đường dự phòng theo nhãn cho ROM chỉ in nhãn (`:152,157`). Kết quả mô phỏng lại đúng logic này trên tệp thật: `fix2-acc` cho true, `k1-acc` cho false [ĐO mô phỏng]. **Lỗ hổng test**: fixture `dumpHealed` (`core/src/test/kotlin/com/byd/clusternav/modules/navaccess/AccessibilityBindingStuckTest.kt:41-49`) đặt hai dịch vụ trên **cùng một dòng** và dùng nhãn đầy đủ. Theo CLAUDE.md §10, nên thêm một fixture nguyên văn từ dòng 4–7 của `fix2-acc`.

**Cách đo đúng** (lệnh chỉ đọc, `<ip-xe>` qua cầu):

```sh
adb shell dumpsys accessibility > acc.txt
# Bound: đọc TRỌN khối, vì mỗi dịch vụ một dòng và ROM chỉ in nhãn
sed -n '/Bound services/,/Enabled services/p' acc.txt | grep -c "ClusterNav"
# Binding: khối này in đủ {gói/lớp}
grep "Binding services" acc.txt | grep -c "com.byd.launcher/"
# ServiceRecord thật của dịch vụ
adb shell dumpsys activity services com.byd.launcher | grep -c "ServiceRecord.*NavAccessibilityService"
```

Khoẻ nghĩa là Bound = 1, Binding = 0, ServiceRecord = 1. Kẹt nghĩa là Bound = 0, Binding = 1, ServiceRecord = 0. Hàm `a11y_read` của runbook grep nhãn trên cả tệp nên không dính bẫy (`docs/diagnostics/oncar-runbook-2.82.md` §2.3, hàm `a11y_read`), nhưng phiên đã không dùng hàm đó.

### 2.2 Các bẫy đọc số liệu khác (để lần sau không lặp lại)

| Bẫy | Cách đọc đúng | Mức |
|---|---|---|
| Tìm `PACKAGE_RESTARTED` trong khối **foreground** của lịch sử broadcast. Trong phiên từng nói "`c1-bcast` phủ từ 11:07:36, bao trọn lượt 11:20:50". Câu này **sai**: mốc 11:07:36 thuộc khối foreground, còn khối nền của `c1` chỉ bắt đầu từ 11:21:19.605 | Đọc khối `Historical broadcasts summary [background]` và ghi lại cửa sổ enq cũ nhất → mới nhất trước khi kết luận "không có" | [ĐO] |
| Dùng `am_kill … stop <gói>` để phân biệt force-stop với lượt giết của BYD | Không phân biệt được, vì hai loại in cùng một chuỗi. Phải xem có `PACKAGE_RESTARTED` hay không, và có `am_schedule_service_restart` cho dịch vụ Kachi hay không (§1.3) | [ĐO] |
| Lấy cột 2 của `/proc/uptime` làm "thời gian thức" | Cột 2 là tổng thời gian idle của CPU. Muốn biết thời gian ngủ sâu thì lấy `Realtime − Uptime` trong header `dumpsys meminfo`. Ở đây: 69 180 764 − 7 442 606 ≈ 61,74 triệu ms, khoảng 17 g 09 ph, khớp `a11y_deep_sleep_ms` trong prefs | [ĐO] số. Nghĩa cột 2 theo proc(5) |
| `logcat -c` giữa buổi | Không xoá log trong cả buổi. Mở một tệp logcat chạy liên tục (KEY-1 của runbook) | quy trình |

---

## 3. Các kết quả khác

| ID | Kết quả | Mức | Nguồn |
|---|---|---|---|
| KEY-0 | Phím chết khi lên xe. Owner không tìm thấy màn Chẩn đoán (nút đã gỡ từ 21/09, `SettingsSectionsCast.kt:442`) | [ĐO] | transcript 11:08:56 |
| KEY-4 | `am start -n …/DiagActivity` bị AMS từ chối: output còn 3 dòng stack cuối của `ActivityManagerService.onTransact`, activity không mở. Dòng thông báo lỗi đã bị `\| tail -3` cắt mất. [SUY mạnh] nội dung lỗi là `Permission Denial … not exported`, vì `AndroidManifest.xml:91-92` khai `exported="false"` | [ĐO] bị từ chối · [SUY] nội dung | transcript 11:12:28 |
| JOURNAL | Bản phát hành không có đường đọc `filesDir/diag/a11y-bind.log` (`A11yBindJournalStore.kt:16,26`). Nơi đọc duy nhất là `DiagActivity.kt:204`. Lệnh `diag` của cầu kiểm thử không chép tệp này. Bản phát hành không cho `run-as`. Đường thay thế duy nhất hiện có là các dòng tag `A11yJournal` trong `kachi-logs/usage-*.log` | [ĐO mã] + [ĐO KEY-4] | `k2-extfiles-111357.txt` |
| Chú thích sai trong mã | "adb vẫn `am start` được activity non-exported" (`AndroidManifest.xml:89`, `SettingsSectionsCast.kt:444-445`). Câu này **sai** trên ROM user của Seal | [ĐO KEY-4] | — |
| INST | 2.82 (183) cài đè, `Success`. Tới lúc Bound mất khoảng 28 s tính từ lúc bắt đầu cài (theo log app). Con số "40 s" chỉ là cận trên từ bộ ghi | [ĐO] | `inst-state.txt` · `inst-poll.txt` · `usage-282-120941.log` |
| POST-INST | Lúc 11:40:08: `shell_usable=false`, `slot_displays=[]`. Ô chỉ dựng lúc 11:40:55.320 (display 7). Đến 11:46:28 thì đủ quyền. Vì sao 70 s đầu ô không tự dựng: [CHƯA BIẾT] | [ĐO] | `inst-state.txt` · `usage-282-120941.log:541` |
| Không crash sau cài | 0 dòng `FATAL EXCEPTION`/`ANR`/`KachiCrash` trong log 11:39:40–12:11. Tệp `crash-*.log` sau lúc cài: [CHƯA ĐO] | [ĐO] | `usage-282-120941.log` · `final-logcat-120941.txt` · `camb5-logcat.txt` |
| SCHED-0 (2.81) | Lịch sống có sha1 `13eeda82691d`, dài 138 ký tự, 4 luật. Chưa có mốc `migrated_nav_schedule_v1`. Cả hai hồ sơ `Mặc định` và `Test` đều chưa có lịch | [ĐO] | `sched0-cn.txt` · `sched0-ws.txt` · `sched0-profiles.txt` |
| SCHED-1 (2.82) | `migrated_nav_schedule_v1 = true`. Cả 'Mặc định' lẫn 'Test' có `nav_automation_rules` sha1 `13eeda82691d`, trùng lịch sống. Tiêu chí (4) (ảnh màn Lịch từng hồ sơ): [CHƯA ĐO] | [ĐO] | `sched1-cn.txt` · `sched1-ws.txt` |
| CAM-KEEP | `diff` 26 khoá `camera_*` trước và sau cài ra rỗng | [ĐO] | `sched0-cn.txt` so với `sched1-cn.txt` |
| TEST-MODE | ⚠ **Bảo mật.** Sống qua cài đè **và qua 2 lần tắt/bật máy**: `test_mode_minutes_left` là 30 lúc 11:38:55, 29 lúc 11:40:08, 22 lúc 11:46:28 ⇒ công tắc bật khoảng 11:08:55–11:09:55, **không** phải lúc owner báo (11:37:39); sau đó có hai lượt `am_kill` tắt máy 11:20:50.162 và 11:33:25.170 mà cửa sổ vẫn còn. Cơ chế [ĐO mã]: cửa sổ khoá theo `boot_id` (`TestBridgeStore.kt:53-56`) + 60 phút `elapsedRealtime` (`TestBridgeWindow.kt:29,41`), mà tắt máy BYD không reboot. Trái chữ UI "Tự tắt sau 60 phút và sau khi tắt máy" (`app/src/main/res/values/strings_kachi.xml:527`). Đây là công tắc duy nhất chắn receiver exported `KachiTestBridge` ⇒ backlog `TEST-MODE-ACC-OFF` | [ĐO] (ba số đọc + hai mốc `am_kill`) | `key5-state-113855.txt` · `inst-state.txt` · `usage-282-120941.log:1741` · `c1-logcat-112312.txt:3590` · `c2-logcat-113538.txt:3414` |
| KEY-328 trên 2.82 | Lúc 11:47:29.656 `onKeyEvent DOWN keycode=328`, rồi `voice-key fire`. Cũng ăn phím 304 lúc 11:47:30 và 294 lúc 11:49:01 | [ĐO] | `usage-282-120941.log:1763` |
| GUIDE-1 | Hai bên đều đạt. Vạch nấc n nằm ở y = 137,5 + 49,5·n trên khung 371×495, tức y/H = n/10, lệch ≤ 1 px, nấc 1–9 (`CameraGuide.kt:66`) | [ĐO ảnh, đo bằng máy] | `guide1-logcat.txt` + ảnh (ngoài repo) |
| GUIDE-AXIS | Hình xoay 90° (mặc định Seal): khoảng cách tới xe bên cạnh chạy **ngang** khung, trong khi vạch lại ngang ⇒ **sai trục**. Hình không xoay (`camera_rot_*="0"`): **đúng trục** | Ảnh: [ĐO]. Hình học: [SUY] | `norot-prefs.txt` · transcript 11:51, 11:58 |
| GUIDE-2 (không xoay) | Bên phải: nấc 7 = mép trong vạch vàng dưới đất, cách bánh khoảng 20 cm. Bên trái: giữa nấc 7 và 8, vạch mờ cách khoảng 5 cm | [ĐO owner] | transcript 11:58:43, 12:00:47 |
| CAM-B5 | Trái ⇄ phải liên tiếp 3 lượt rồi `none`: 0 dòng `BufferQueue has been abandoned`, 0 crash | [ĐO] | `camb5-logcat.txt` |
| CAM-SEAL | Xi-nhan thật trên cụm (`hình=CLUSTER`). Trái lúc 12:09:01.855 crop 0,25–0,5. Phải lúc 12:09:08.306 crop 0,5–0,75. 0 abandoned. **Lưu ý:** xe owner đặt `camera_view_right=MIRROR_LEFT`, không phải mặc định, và hai bên đều `camId=1`. Nên kết luận đúng là "đạt với cấu hình của owner" | [ĐO] | `final-logcat-120941.txt` · `sched0-cn.txt` |
| FIX-UI | Sau *Sửa ngay*: launcher không tự lên, và YouTube nằm ở stack 32, `mWindowingMode=freeform`, display 0, `bounds=[37,136][1883,1043]`, đè lên màn nhà. Ô không dựng lại (không có dòng `tạo màn ảo` trong `usage-afterfix.log`). Bấm HOME một lần thì Kachi force-stop YouTube (11:32:49.721) rồi mở lại vào ô (11:32:50.834). Owner: *"bấm HOME về OK ngay, bấm 1 lần là xong, đẹp"* | [ĐO] | `fix-stacks-112813.txt:1-3` · `c2-logcat:3035-3108` |
| FIX-UI, cơ chế | Cửa sổ freeform này **do Kachi mở**, không phải app trong ô bị rơi xuống. Task #38 có `flg=0x30000000 cat=LAUNCHER`, `mCallingPackage=com.byd.launcher`, `launchedFromPackage=com.byd.launcher`, và là task YouTube duy nhất trong dump. Sự kiện tạo là `am_create_activity … 805306368` lúc 11:25:57.261. Dấu vết giống hệt có ở lượt 0 (18:25:43.688) và lượt 2 (11:33:26.526). Đường trong mã: `IntentAppLauncher.openInSlot`, gọi từ `LauncherWindows` khi chưa có kênh shell | Dấu vết: [ĐO]. Đường mã: [SUY mạnh] | `fix-activities-112813.txt:134-149` · `c2-logcat:2192,3612` · `k1-logcat:439` |
| FREEFORM | Chế độ freeform chạy được trên xe: stack 32 trên display 0, và cả stack của cụm trên display 1 | [ĐO] | `fix-stacks-112813.txt` · owner 11:27:27 |
| QUICKBOOT | Mỗi lần mở máy có `BOOT_COMPLETED` với `from_quickboot=true`, nhưng nó tới **sau** lúc ô đã dựng app. `SCREEN_OFF` cũng được dispatch **sau** lượt giết (lượt 2: vào hàng lúc 11:33:24.521, dispatch lúc 11:33:25.384; lượt 1: dispatch lúc 11:20:50.357). ⇒ Không thể chữa sẵn bằng hai broadcast này | [ĐO] | `c1-bcast-112353.txt:3041` · `c2-bcast-113538.txt:3039` |
| KEY-8 | Vụ tiến trình khởi động lại khoảng 15:57 hôm 28/09 là **reboot đầu xe** (`SYSTEM_BOOT` lúc 15:57:18), không phải do tự chữa | [ĐO] | `k2-dropbox-111357.txt` · `k1-clock-110944.txt` |
| PERF | PSS tiến trình chính 56 817 kB, so với mốc 48 514 kB là +17,1 %. `:wake` 137 341 kB; mốc 28/09 176 286 kB là `:wake` + `:tts` **gộp** (backlog mục *MỐC GỐC 2026-09-28 17:53*) và sáng 29/09 không có `:tts` ⇒ **không so được**. Tiền đề "launcher sống mãi" của PERF-STANDBY-FRESH **sai** trên Seal, vì mỗi lần tắt máy đều bị giết | [ĐO] | `k1-meminfo-all-110944.txt` · `k1-ps-110944.txt` |
| SLEEP | Thời gian ngủ sâu Kachi tự ghi (`a11y_deep_sleep_ms` 61 738 158) khớp đồng hồ hệ thống. Ngủ sâu có thật, nhưng **không phải** ngòi nổ | [ĐO] | `k1-clock-110944.txt` · `sched0-cn.txt` |
| SL6-BASE | Trên Seal: `ro.product.model=BYD AUTO`, `ro.product.name=DiLink3.0`, `ro.product.brand=BYD-AUTO`, `ro.product.manufacturer=BYD AUTO`, `ro.product.device=DiLink3.0`, `ro.build.product=DiLink3.0`, `ro.board.platform=trinket`, `ro.hardware=qcom`. Các khoá "tên máy" này không phân biệt được đời xe. `persist.sys.car.type=138`, trùng `persist.sys.vehicle_40d_code=138`, [SUY] là khoá phân biệt. Để chốt trên SL6 chỉ cần `getprop persist.sys.car.type`. Owner đã bỏ hướng này (§6) | [ĐO Seal] | `sl6base-seal-getprop.txt` (chỉ trích các khoá được phép) |

---

## 4. GMaps đổi đích và `CLEAR_TASK`

**Tình huống** [ĐO `gmaps-popup-logcat-121156.txt`, `gmaps-popup-windows-121156.txt`, `gmaps-popup-stacks-121156.txt`]:

- Đang dẫn đường, Kachi (uid 10138) bắn `START u0 {act=android.intent.action.VIEW dat=google.navigation:q=<đích> flg=0x10000000 pkg=com.google.android.apps.maps cmp=…/com.google.android.maps.MapsActivity}`. Có hai lần, lúc 12:11:30.559 và 12:11:49.882.
  - URI được dựng ở `core/src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceAppTargets.kt:301`.
  - Cờ `NEW_TASK` được thêm ở `app/src/main/java/com/byd/clusternav/launcher/voice/VoiceAppIntents.kt:70`.
- GMaps hiện hộp thoại "Thoát chế độ đi theo chỉ dẫn?". Đây là một cửa sổ Dialog riêng (`ty=APPLICATION fl=DIM_BEHIND`, khung 1056×260) trên display 0, cùng stack với Maps.
  - Owner lúc 12:12:36: *"Thoát chế độ đi theo chỉ dẫn? --> Không / Có --> chỉ nhấn được Không, (Có bị disable) --> không chuyển hướng được"*.
  - Vì sao nút "Có" bị khoá: [CHƯA BIẾT], đó là mã của Google. [ĐOÁN] do chưa có định vị: log 12:11:41.816 ghi `KachiAutoGps: chưa có fix nào`.
- Lúc đó cụm (display 1) chỉ có ClusterBlack. Ca "Maps trên cụm" của 28/09 **không** tái hiện [ĐO].

**Thử `CLEAR_TASK`** lúc 12:16:54 (giờ Mac), chạy từ uid shell, cùng đích:

```sh
am start -W -a android.intent.action.VIEW -d '<đích>' -f 0x10008000 \
  -n com.google.android.apps.maps/com.google.android.maps.MapsActivity
```

- [ĐO] Kết quả `Status: ok · LaunchState: WARM · TotalTime: 1152`.
- [ĐO] Sau đó Maps chỉ còn một cửa sổ `ty=BASE_APPLICATION` trên display 0, **0** dòng `DIM_BEHIND` (`gmaps-exp-windows.txt`).
- [ĐO owner] Lúc 12:17:25: *"ok rồi đó em, ko có gps nên nó đứng, nhưng nó đã dẫn về <đích> rồi, clear task cũ ok đó em"*.

**CLAUDE.md §14: các tầng bằng chứng cho `CLEAR_TASK`**

| Tầng | GMaps | VietMap |
|---|---|---|
| 1. Shell thô trên xe thật | **Xanh**, nhưng có giới hạn: mới thử một lần; gọi từ uid 2000, không phải uid Kachi ([SUY] cờ không phụ thuộc uid gọi); không có GPS fix; Maps ở display 0, không chiếu lên cụm | **[CHƯA ĐO]** |
| 2. `car-integration` / 3. `core` / 4. `app` | Việc E của 2.83: lúc viết báo cáo chưa có mã; sau đó mã 2.83 bật cờ cho GMaps (đã đăng OTA trong 2.83, 29/09 — spec 2.83 §9 D-6) | Giữ `NEW_TASK` tới khi đo |

Cần đo tiếp trước khi áp cho mọi ca (CLAUDE.md §6):
- **GMaps đang chiếu lên cụm (display 1)** rồi nhận `CLEAR_TASK`: [CHƯA BIẾT]. [ĐOÁN] Task bị dựng lại có thể về display 0 và làm cụm mất bản đồ.
- **VietMap**, app `singleTask`: khi chưa dẫn và khi đang dẫn, so `-f 0x10000000` với `-f 0x10008000`. Cần xem có khởi động lạnh không và luồng làn đường có còn lên cụm không.
- Lặp lại ca GMaps khi đã có GPS fix và gọi từ uid Kachi, trên bản 2.83.

---

## 5. AVM 360: đóng hẳn

| Đường | Lệnh (chỉ đọc, uid shell) | Kết quả | Nguồn |
|---|---|---|---|
| `pano_sdk.txt` | `ls /collect2/autovideo/ /collect2/autovideo/sdk/`, `cat …/sdk/pano_sdk.txt` | `Permission denied` cả ba lệnh | [ĐO] `avm-ls-120315.txt` · `avm-pano_sdk-120315.txt` |
| `panorama_online` trong settings chuẩn | `settings get {system,global,secure} panorama_online` | `null` cả ba | [ĐO] transcript |
| Provider `carsettings` của BYD | `content query --uri content://carsettings/…` | `system`, `secure`, `carsettings` đều trả `Bad root path`. `global` có 43 hàng, không khoá nào chứa pano, avm hay 360. Lọc theo khoá `panorama_online` trả `No result found.` | [ĐO] `avm-panorama-120534.txt` · `avm-carsettings-global-120603.txt` · transcript |

- ⇒ Câu hỏi [CHƯA BIẾT] cuối cùng của AVM-360 (`docs/PROJECT-BACKLOG.md` mục *AVM-360*: xe là PI hay APA) không có đường nào đọc được từ uid shell. [SUY] app thường càng không đọc được.
- Hướng 360 **đóng hẳn**. Hướng thay thế trước đây là vạch chuẩn, và owner cũng đã dẹp (§6).

---

## 6. Quyết định của owner (nguyên văn, giờ VN)

| Giờ | Nguyên văn | Nghĩa |
|---|---|---|
| 11:12:30 | "tôi không cần sửa, tôi cần tìm rootcause và xử lý về sau" | Buổi này chỉ đo gốc rễ, không sửa tại chỗ |
| 12:00:47 | "đúng, bên trái thì giữa 7-8, giờ làm gì với mớ này, mớ vạch vẽ ra cho vui vậy chứ có ý nghĩa gì với đời đâu hả?" | — |
| 12:02:39 | "ò, dẹp vạch đi, còn 1 đường đi mò lấy cam 360 của xe thì làm chưa, check lại on-car còn những gì?" | **Gỡ vạch chuẩn** (D) |
| 12:01:24 (phiên) → 12:02:39 | Phiên đề xuất "**không đăng 2.82**: nó mang vạch chuẩn, còn thứ anh em thật sự cần là bản sửa phím". Owner không phản đối | Không đăng 2.82, gom vào 2.83. Không có câu nào của owner nói thẳng về OTA 2.82 [ĐO transcript] |
| 12:17:25 | "… clear task cũ ok đó em" | Dùng `CLEAR_TASK` cho lệnh dẫn đường (E). Mới đo với GMaps |
| 12:27:07 | "là lức tắt máy thì chạy, ko đc thì lúc mở xe,kiểm tra ngay, nếu fail thì restart lại hả?" | Thiết kế chữa theo lớp: lúc tắt máy, rồi lúc mở xe |
| 12:29:08 | "nghe có lý đó, lúc đang chạy mà lỗi thì user tự chửa ok, ko cần mình tự chửa làm gì, nút tự chữa đó phải trả về home, ko để app mồ côi là OK" | Đang chạy thì **không** tự chữa. Nút sửa phải về HOME và không để app mồ côi (A, B) |
| 12:31:55 | "cái hồ sơ đó chắc OK thôi, ko quan trọng gì đâu, SL6 hay gì người ta tự tìm cam đúng là được, còn các xe HAN, Dolphin đồ nữa mà, mò chi cho mệt đầu, chốt hết chưa, autonomus cho đến khi ra apk đi nhé, cần hỏi gì nữa ko" | Bỏ mặc định camera theo đời xe (CAM-SL6-PROFILE). Không đo SCHED-2/3 |
| 12:34:54 | "ok, làm đi, làm đi" | Trả lời kế hoạch 2.83 của phiên (12:33:13: chữa phím lớp 1 + 2, nút sửa về màn nhà, nhật ký đọc được, gỡ vạch, `CLEAR_TASK` chỉ cho Google Maps) |
| 12:37:33 | "chia nhau làm theo workflow, như gmaps là việc riêng kia, nhiều việc song song được mà đúng không? dựng lại home sau restart không để app mồ côi cũng riêng kìa ??" | Chia việc làm song song; nêu đích danh E (GMaps) và B (dựng lại home, không để app mồ côi) |
| 14:05:56 | "xong chắc kiểm tra kỷ rồi OTA luôn cho anh em test nhỉ, mình ko có thời gian on-car test" | **Cổng OTA mới cho 2.83**: đăng OTA sau kiểm off-car (test + lint + E2E máy ảo + senior review sạch + đọc AOSP theo CLAUDE.md §3 + quét bảo mật), **không** qua buổi xe của owner; anh em test trên xe của họ. Phiên đáp 14:06:16 nêu hai rủi ro: lớp 1/2 chưa chạy trên xe thật; OTA không lùi bản (`UpdateChecker.kt:87` chỉ nhận bản cao hơn ⇒ lỗi phải ra 2.84). Owner không đáp thêm [ĐO transcript] |

**Trạng thái duyệt spec 2.83**: **đã duyệt** (Requirements · Design · Tasks) — ba câu 12:31:55 ("autonomus cho đến khi ra apk"), 12:34:54 ("ok, làm đi, làm đi") và 12:37:33 (chia việc song song) [ĐO transcript], khớp quy ước đã lưu của owner *"làm đi" = đã duyệt* (memory 2026-09-15). Câu "spec 2.83 chờ owner duyệt trước khi code" trong khung công việc là do script tính ra, không phải lời owner.

**Tóm tắt các quyết định dùng cho 2.83** (đối chiếu cuối 29/09):

| Chủ đề | Quyết định | Nguồn |
|---|---|---|
| Lớp 1 (tắt máy) + lớp 2 (mở xe) | Tự chữa | owner 12:27:07 nêu luồng, 12:29:08 *"nghe có lý đó"* |
| Lớp 3 (đang chạy) | **Không** tự chữa; người dùng tự bấm *Kiểm tra / Sửa ngay* | owner 12:29:08 |
| Nút sửa | **Luôn** về màn nhà, không để app mồ côi | owner 12:29:08 (+ 11:27:27, 11:33:11) |
| `CLEAR_TASK` | Chỉ Google Maps (đã đo); VietMap giữ nguyên | kế hoạch 12:33:13, owner 12:34:54 |
| Mặc định camera theo đời xe (CAM-SL6-PROFILE) | **Không làm — đóng** | owner 12:31:55 |
| SCHED-2/3 | **Không đo thêm** | owner 12:31:55 |
| Phát hành 2.83 | OTA thẳng cho anh em test sau kiểm off-car; không buổi xe của owner — **đã lên kênh 29/09** (§7) | owner 14:05:56 |
| D-10 (lớp 1 chỉ dựa `isInteractive`) · `TEST-MODE-ACC-OFF` | **Chưa có câu trả lời riêng** [CHƯA BIẾT — chưa ghi nhận tới lúc đăng 2.83] — spec 2.83 OQ11 | — |


---

## 7. Việc cho 2.83

Spec: `docs/specs/kachi-283-key-heal-acc-off.html`. Bảng dưới chỉ trỏ phát hiện nào dẫn tới việc nào. Thiết kế chi tiết nằm trong spec (§4, viết lại theo mã ở Pass 3).

> **Trạng thái cuối 29/09 — 2.83 (184) ĐÃ ĐĂNG OTA**: cả 5 việc triển khai, `apk/Kachi-2.83-release.apk` (43 907 845 B, sha256 `b44691e65c53916e079dcece2ed1a455211a539581f50d95162ae984d82f2213`) **đã lên kênh (29/09)**, thay 2.81 trong cùng commit. Cổng off-car trên mã cuối [ĐO 29/09]: test **6 411 / 0** (735 XML) · lint 0 lỗi · E2E máy ảo PASS (lượt giết mô phỏng đúng chữ ký BYD bằng `killApplication` uid system) · senior review A–E lượt cuối CLEAN + rào camera 3 lượt, lượt 3 CLEAN · APK cert `92:57:…:99:17`, vc184, không debuggable, 0 `TEST_` · AOSP đối chiếu qua mirror tag `android-10.0.0_r47` + `android-12.0.0_r34`. Trên xe thật: chưa chạy ⇒ **chờ phản hồi anh em** (spec 2.83 §6.2). Cột *Điều phải đo trước* dưới đây là kế hoạch lúc 12:30: sau quyết định 14:05:56 (§6), các phép đo trên xe **không** làm trước OTA mà thành nghiệm thu hiện trường do anh em chạy sau OTA (spec 2.83 §6.2). Cột *Kết quả (mã 2.83)* ghi cái mã thật làm.

| Việc | Phát hiện dẫn tới | Điều phải đo hoặc đọc trước (CLAUDE.md §3, §14) | Kết quả (mã 2.83 bản đăng) |
|---|---|---|---|
| **A**. Chữa kẹt phím sau lượt giết lúc tắt máy, theo lớp: lúc tắt máy, rồi lúc mở xe; đang chạy thì không tự chữa | §1.1–§1.7. Cổng app khách thua cuộc đua mỗi lần. Hạn mức "một lần mỗi đợt thức" chặn nhầm sau *Sửa ngay*. Lúc tiến trình được dựng lại khi màn tắt, không có mã nào kiểm dịch vụ Hỗ trợ [SUY từ mã, xem spec] | Đo lúc màn tắt: `am stack list` (nghĩa của `visible=`), khối Bound và Binding, FGS. Chạy tay lệnh force-stop khi đang kẹt lúc màn tắt. Fetch AOSP cho `StackInfo.visible`. Dịch ngược `AccModeManagerService` (§1.8) | Xong off-car: lớp 1 lúc tiến trình khởi động khi màn tắt, lớp 2 trong 10 s sau `SCREEN_ON` (chỉ sau một lần tắt máy), kẹt phải thấy hai lần cách 5 s; lớp 3 không tự giết và đã biết kẹt thì không tắt/bật cài đặt. AOSP đã fetch lại (§1.4). Trên xe: chưa chạy ⇒ anh em test sau OTA |
| **B**. *Sửa ngay* đưa launcher lên và không để app mồ côi | §3 FIX-UI: cửa sổ freeform do `IntentAppLauncher` của Kachi mở khi chưa có kênh shell. Cửa sổ đó giữ tiêu điểm, nên kênh shell không lên | Sau khi dựng bản: bấm *Sửa ngay* khi ô đang chạy app, rồi kiểm không có `am_create_activity … 805306368` từ Kachi và `mCurrentFocus` về KachiHome | Xong off-car theo cách khác: nút bấm **luôn** về màn nhà (Home, 3 s sau đo lại cửa sổ mồ côi); lượt tự động chỉ về nhà khi đỉnh display 0 là cửa sổ mồ côi. **Rào camera bản cuối** (spec D-15): mọi lần Home của cả hai đuôi qua một cửa `GO_HOME_UNLESS_CAMERA` — bỏ Home nếu **bất kỳ** stack đang hiện nào của display 0 là camera BYD `com.byd.avc/` (kể cả khi PIP / cửa sổ mồ côi nằm trên camera), đọc hỏng ⇒ không Home [ĐO máy ảo, Settings đóng vai camera; trên xe SUY]. Gốc (đường dự phòng `IntentAppLauncher`) chưa sửa |
| **C**. Nhật ký bền đọc được trên bản phát hành | §3 KEY-4, JOURNAL. Ba chú thích mã sai về `am start`. Bài canh `A11yBindStuckWiringContractTest` đang khoá một đường không tới được (CLAUDE.md §8) | Không cần đo trước. Nên có **trước** buổi đo A | Xong: lệnh cầu kiểm thử `a11ylog` (chỉ đọc) + mọi dòng nhật ký ra logcat tag `A11yJournal` (nằm trong `usage-*.log`). Ba chú thích mã sai còn lại chờ sửa |
| **D**. Gỡ vạch chuẩn | §3 GUIDE-1, GUIDE-2, GUIDE-AXIS. Owner dẹp | Không cần đo. Dữ liệu `camera_guide_*` còn sót trên xe owner | Xong: màn Camera 14 hàng, grep mã chạy = 0 |
| **E**. Thêm `FLAG_ACTIVITY_CLEAR_TASK` cho lệnh giao điểm đến, theo **vai trò** dẫn đường, không theo tên gói (CLAUDE.md §7) | §4. Tầng 1 đã xanh với GMaps | VietMap (§4) — chặn việc bật cho VietMap. GMaps đang chiếu lên cụm: rủi ro nghiệm thu V-E4, không chặn GMaps (kế hoạch owner duyệt 12:33:13/12:34:54). Fetch `ActivityStarter.java` | Xong: chỉ Google Maps mang `CLEAR_TASK` (dữ liệu từng đích, một cửa cho giọng nói + lịch); ý-định dự phòng không mang cờ; VietMap giữ `NEW_TASK`. AOSP: giữ cùng task, không đổi display ⇒ ca cụm [SUY] ổn, chưa đo |
| Đính chính tài liệu | §1.6 (con số "10 g 13 ph"), §1.7 (giả định "ô rỗng lúc thức"), §3 FIX-UI (quy kết "app trong ô rơi xuống"), §2.1 (fixture nhiều dòng) | — | Fixture khối Bound nhiều dòng có trong test (`a11y-0929/`); các đính chính còn lại nằm trong spec 2.83 |
| *(không làm)* CAM-SL6-PROFILE, SCHED-2/3 | owner 12:31:55 (§6) | — | Đóng — không làm |

---

## 8. Lỗi công cụ và quy trình của chính phiên

1. **Đếm Bound bằng grep một dòng** (§2.1). Cột `bound` trong `poll-cycle1.txt` và `fix-poll.txt` sai. Owner bắt lỗi lúc 11:28:49 (*"đang check sai chỗ rồi"*). Từ 11:29:16 mới đo đúng bằng cách đọc trọn khối Bound, cộng ServiceRecord và Binding.
2. **Chạy `logcat -c` trước CAM-B5** (khoảng 11:44:46, giờ Mac). Việc này trái luật "không `logcat -c` cả buổi" (`docs/diagnostics/oncar-runbook-2.82.md` §0.2 mục 4). Phiên cũng không mở tệp logcat chạy liên tục. Hậu quả: mất khoảng 1 ph 45 s (11:42:58–11:44:43) log của các tiến trình ngoài Kachi. Log buổi sáng (`k1`, `k2`, `c1`, `c2`, `fix`) và `guide1-logcat.txt` đã được lưu trước, nên hồ sơ gốc rễ không mất gì.
3. **Chỉ sai đường tới màn Chẩn đoán.** Lúc 11:01:28, phiên bảo owner mở *Cài đặt › Chiếu cụm › Chẩn đoán*, trong khi nút này đã gỡ từ 21/09 (`oncar-runbook-2.82.md` phụ lục §1.3 *Đính chính*). Sau đó phiên lại tin chú thích mã "adb vẫn am start được", và chú thích đó sai.
4. **KEY-4 bị cắt bằng `| tail -3`**, không `tee` ra tệp như runbook yêu cầu (`oncar-runbook-2.82.md` mục KEY-4), nên mất nguyên văn thông báo lỗi.
5. **Không tạo `inst-time.txt`.** Giờ cài lấy từ `date` trên Mac (11:39:13).
6. **SL6-BASE chụp trọn `getprop`** (766 dòng) thay vì ba lệnh của runbook, nên thư mục bằng chứng có thêm dữ liệu riêng tư. Tài liệu này chỉ trích các khoá được phép.
7. **Nói "cửa sổ broadcast `c1` bao trọn lượt 11:20:50"**. Câu này sai, xem §2.2.
8. **Nói sai về chế độ kiểm thử**: "tự tắt … khi tắt máy" và "bật khoảng 11:36". Cả hai không khớp ba số đọc (§3 TEST-MODE).
9. **Tuyên bố "2.82 đủ điều kiện đăng OTA"** lúc 11:46:45, khi chưa kiểm tệp `crash-*` (runbook §10.1 mục 3b). Không có hậu quả vì không đăng.
10. **Bỏ ảnh màn Lịch** ở SCHED-0 và ở tiêu chí (4) của SCHED-1.
11. **Bộ dò vạch GUIDE-1 lần đầu bắt nhầm** nền trắng của YouTube. Phiên tự phát hiện trước khi báo, nên không có kết quả sai nào được báo ra.

**Chưa đo trong buổi**, ghi "chưa đo", không suy ra kết quả:
- SCHED-1 tiêu chí (4), SCHED-2/3 (owner bỏ), SCHED-4.
- GUIDE-3..5.
- CAM-F16, CAM-PREVIEW-HOLD, TRIP-HAL, PKGS.
- Tệp `crash-*` sau lúc cài.
- GMaps trên cụm, GMaps có GPS fix, GMaps gọi từ uid Kachi, và VietMap.
- (Chế độ kiểm thử sống qua tắt/bật máy: nay đã chốt [ĐO] từ ba số đọc + hai mốc `am_kill`, §3 TEST-MODE.)

---

## 9. Nguồn

**Thư mục bằng chứng ngoài repo `oncar-0929/`**. Chỉ ghi tên tệp. Ảnh `.png`, tệp đích dẫn đường và tệp môi trường không liệt kê và không chép vào repo.

| Nhóm | Tệp |
|---|---|
| Logcat | `k1-logcat-110944.txt`, `c1-logcat-112312.txt`, `fix-logcat-112731.txt`, `c2-logcat-113538.txt`, `guide1-logcat.txt`, `camb5-logcat.txt`, `final-logcat-120941.txt`, `gmaps-popup-logcat-121156.txt` |
| `dumpsys accessibility` | `k1-acc-110944.txt`, `fix-acc-112731.txt`, `fix2-acc-112916.txt`, `c2-acc-113538.txt` |
| `dumpsys activity services` | `k1-svc-110944.txt`, `fix2-svc-113016.txt`, `c2-svc-113538.txt`, `k2-svc-all-111357.txt` |
| Lịch sử broadcast | `k2-bcast-111357.txt`, `c1-bcast-112353.txt`, `fix-bcast-112813.txt`, `c2-bcast-113538.txt` |
| Stack và activity | `k1-stacks-110944.txt`, `fix-stacks-112813.txt`, `fix-activities-112813.txt`, `gmaps-popup-stacks-121156.txt`, `gmaps-popup-activities-121156.txt`, `gmaps-popup-displays-121156.txt`, `gmaps-popup-windows-121156.txt`, `gmaps-exp-windows.txt` |
| Bộ ghi | `poll-cycle1.txt` và `fix-poll.txt` (cột `bound` SAI), `poll-cycle2.txt`, `poll-cycle2-after.txt`, `inst-poll.txt` |
| Log Kachi tự ghi | `usage-before.log`, `usage-night.log`, `usage-cycle1.log`, `usage-afterfix.log`, `usage-cycle2.log`, `usage-282-120941.log` |
| Trạng thái và prefs | `key5-state-113855.txt`, `inst-state.txt`, `c1-state-112244.txt`, `sched0-cn.txt`, `sched0-ws.txt`, `sched0-profiles.txt`, `sched1-cn.txt`, `sched1-ws.txt`, `norot-prefs.txt`, `guide-reset.txt`, `k1-settings-110944.txt`, `c1-settings-112312.txt` |
| Hệ thống | `k1-clock-110944.txt`, `k1-ps-110944.txt`, `k1-meminfo-all-110944.txt`, `k2-dropbox-111357.txt`, `k2-extfiles-111357.txt`, `k2-alarm-111357.txt`, `k2-power-111357.txt`, `k2-idle-111357.txt`, `k2-proc-111357.txt` |
| AVM, SL6 | `avm-ls-120315.txt`, `avm-pano_sdk-120315.txt`, `avm-panorama-120534.txt`, `avm-providers-120426.txt`, `avm-providers-full.txt`, `avm-carsettings-global-120603.txt`, `sl6base-seal-getprop.txt` (chỉ trích 10 khoá được phép) |
| Transcript phiên | Chỉ trích lời owner (IP đã che, đích dẫn đường viết `<đích>`), cùng mốc cài 11:39:13 và các lệnh AVM |

**Repo** (mã trích ở gốc `bfbb728` = 2.82; mã 2.83 (đăng 29/09) đổi sau đó nên số dòng có thể lệch; tài liệu trích theo mục khi có thể):

| Tệp | Dòng |
|---|---|
| `core/src/main/kotlin/com/byd/clusternav/modules/navaccess/AccessibilityHealGates.kt` | `:84-85`, `:100-106` |
| `core/src/main/kotlin/com/byd/clusternav/modules/navaccess/AccessibilityRebind.kt` | `:117`, `:152`, `:157` |
| `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/StackParse.kt` | `:205-209` |
| `core/src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceAppTargets.kt` | `:301` |
| `core/src/test/kotlin/com/byd/clusternav/modules/navaccess/AccessibilityBindingStuckTest.kt` | `:41-49` |
| `app/src/main/java/com/byd/clusternav/NavConnect.kt` | `:179`, `:219`, `:229`, `:279`, `:287`, `:292` |
| `app/src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeKeys.kt` | `:80-81` (*Sửa ngay* → `grantAccessibilityDetailed(reset = true)`) |
| `app/src/main/java/com/byd/clusternav/launcher/voice/VoiceAppIntents.kt` | `:70` |
| `app/src/main/java/com/byd/clusternav/launcher/SettingsSectionsCast.kt` | `:442-445` |
| `app/src/main/java/com/byd/clusternav/modules/navaccess/A11yBindJournalStore.kt` | `:16`, `:26` |
| `app/src/main/java/com/byd/clusternav/modules/clustercast/DiagActivity.kt` | `:204` |
| `app/src/main/AndroidManifest.xml` | `:89`, `:91-92` |
| `app/src/main/res/values/strings_kachi.xml` | `:527` |
| `docs/specs/kachi-a11y-bind-stuck-autofix.html` | §2.2, §4.2 bảng AOSP, §4.3 (trích AOSP) |
| `docs/diagnostics/oncar-runbook-2.82.md` | §0.2 mục 4 · §2.3 `a11y_read` · KEY-4 · §4 INST · phụ lục §1.3 |
| `docs/PROJECT-BACKLOG.md` | mục *MỐC GỐC 2026-09-28 17:53*, mục *AVM-360* |

**Workspace RE**: `../firmware/fw-2602-diff/cmp/services_NEW.jar` (mới chạy `strings`, chưa dịch ngược), và `../firmware/fw-2602-diff/jadx-l3-new/sources/com/byd/dilink50/os/DiLinkAccModeManager.java`.
