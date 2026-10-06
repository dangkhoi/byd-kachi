# Đối chiếu ghi chú Overdrive "cổng khởi động BYD chặn bind dịch vụ Hỗ trợ" với thang chữa phím của Kachi

> **Trạng thái**: Current · **Cập nhật**: 2026-10-06 · **Loại**: Diagnostics (đối chiếu tài liệu + mã, KHÔNG đo xe) ·
> **Mục đích**: trả lời backlog `A11Y-BIND-GATE-OD` — ghi chú của Overdrive (OD) về cổng `isTargetAppEnabledStartedBy3rd` có
> khớp lỗi *"kẹt Binding"* Kachi ghi 29/09 không, và thang chữa hiện tại của Kachi có thiếu bước nào so với trình tự OD đề
> xuất không. Kết luận: **không đổi mã ở 2.93** (CLAUDE.md §6/§14); hai lỗ còn mở thành việc đo trên xe 🚗.
>
> Nhãn: **[ĐO]** = lệnh/đọc nguồn của phiên này · **[SUY]** = suy từ tài liệu/mã · **[CHƯA BIẾT]** = chưa có dữ liệu.
> Ghi chú OD là dữ liệu của NGƯỜI KHÁC trên xe KHÁC ⇒ với Kachi tối đa **[SUY]** (CLAUDE.md §2, §14).

## 1. Nguồn

| Nguồn | Nội dung | Mức |
|---|---|---|
| OD `BYD_ACCESSIBILITY_BIND_GATE_DISCOVERY.md` — repo Overdrive-release (MIT, `github.com/yash-srivastava/Overdrive-release`), commit `ea11ef6e17a8` (2026-09-21) | 84 dòng: vị từ cổng trong `ActivityManagerService` của BYD, một dòng log `ssc_skip … want to bind … ignored`, trình tự phục hồi 3 bước | [ĐO] đã tải và đọc 06/10 · nội dung = [SUY] với xe Kachi |
| AOSP `android-10.0.0_r47` — `AccessibilityManagerService.java`, `ActiveServices.java` | đường gắn/gỡ dịch vụ Hỗ trợ, `onHandleForceStop` | [ĐO] tải từ googlesource 06/10 |
| Mã Kachi tại 2.92 (`8fbdf96`) | `AccessibilityRebind.forceStopRebindCommand` · `NavConnect.escalateIfStuck` · `A11yLifecycleHeal` | [ĐO] đọc mã |
| Buổi xe 29/09 · spec `specs/kachi-283-key-heal-acc-off.html` §2 | BYD giết Kachi mỗi lần tắt máy, không `PACKAGE_RESTARTED` ⇒ kẹt `Binding` | [ĐO xe 29/09] |
| Spec `specs/kachi-launcher-shortcuts-autostart.html` (Gốc 5) · `diagnostics/oncar-2026-10-05-slot-cluster.md` | cổng `relatestart` của firmware chặn service/broadcast/provider vào app KHÔNG chạy (activity đi qua) · log `ssc_skip startServiceLocked` trên xe owner 05/10 | [ĐO firmware/xe] |

## 2. OD nói gì (tóm tắt, nguyên ý)

1. Trên DiLink 3.0 / Android 10, cổng khởi động của BYD trong AMS **từ chối im lặng** lượt hệ thống bind một
   `AccessibilityService` bên thứ ba khi tiến trình app đó **chưa chạy**: `isTargetAppEnabledStartedBy3rd` cho qua khi uid
   không bị đánh dấu chặn HOẶC app đang chạy. Dấu chặn (`mapAppOpsData[uid] = 1`) đặt lại ở MỖI `PACKAGE_ADDED`/`REPLACED`.
2. Cổng được hỏi từ `ActiveServices.bindServiceLocked` ⇒ lượt bind do `AccessibilityManagerService` gửi cũng chịu cổng;
   AMS giữ một lượt bind treo không bao giờ xong ⇒ dịch vụ nằm mãi trong `Binding services`.
3. Ghi chỉnh setting (toggle) **không** gỡ được — toggle không tạo ra tiến trình.
4. Trình tự phục hồi, thứ tự bắt buộc: **(1)** `am force-stop <gói>` (dọn lượt bind treo; AOSP gỡ component khỏi danh
   sách enabled) → **(2)** khởi động TIẾN TRÌNH (một `am start` bất kỳ — miễn trừ của cổng) → **(3)** ghi lại component
   vào `enabled_accessibility_services`. Đảo (3) trước (2), hoặc (2) mà thiếu (1) ⇒ vẫn kẹt.

## 3. Đối chiếu với Kachi

| Bước / khẳng định OD | Kachi hôm nay | Khớp? |
|---|---|---|
| Triệu chứng: kẹt trong `Binding services`, toggle không gỡ | [ĐO xe 28/09] gỡ mình khỏi enabled mà mục `Binding` vẫn còn — đúng kết luận này (KDoc `AccessibilityRebind.forceStopRebindCommand`) | ✅ |
| Cơ chế vào trạng thái kẹt | [ĐO AOSP r47] `serviceDisconnectedLocked` đẩy component vào `mBindingServices` (`AccessibilityManagerService.java:4114-4117`) và `updateServicesLocked` bỏ qua mọi component có trong đó (`:1630-1632`). OD thêm một đường KHÁC tới cùng triệu chứng: lượt bind bị cổng BYD từ chối khi tiến trình chết | [SUY] hai cơ chế, một triệu chứng — không loại trừ nhau |
| (1) `am force-stop` | ✅ bước đầu của lệnh tách rời (`AccessibilityRebind.kt` `forceStopRebindCommand`); [ĐO AOSP] `onHandleForceStop` (`:453-484`) gỡ component khỏi `mEnabledServices` LẪN `mBindingServices` — **chỉ với component đang có trong enabled** (`:464-474`) | ✅ (xem lỗ L2) |
| (2) có tiến trình trước khi ghi lại | **Không có lệnh tường minh.** Kachi là HOME ⇒ Android dựng lại tiến trình ngay sau `am force-stop` ([ĐO c2-logcat 29/09] `am_proc_start` KachiHome 0,33 s sau `am_kill`); lệnh chờ `sleep 4` rồi mới ghi lại ⇒ tiến trình đã có — thoả miễn trừ của cổng | ✅ khi Kachi là HOME · ⚠ lỗ L1 khi không |
| (3) ghi lại component | ✅ `settings put secure enabled_accessibility_services "<danh sách + mình>"` rồi `accessibility_enabled 1` | ✅ |
| Dấu chặn đặt lại ở mỗi lần cài/cập nhật | Kachi cập nhật qua OTA (cài đè) ⇒ [SUY] sau mỗi OTA Kachi bị đánh dấu chặn tới khi tiến trình chạy; đường HOME dựng lại tiến trình ngay nên chưa thành bệnh quan sát được | [CHƯA BIẾT] trên xe owner |
| Vị từ `isTargetAppEnabledStartedBy3rd` | KHÔNG có trong AOSP r47 ([ĐO] `ActiveServices.java` tải 06/10: 0 lần xuất hiện) ⇒ là sửa của BYD. Firmware của xe Kachi chưa dịch ngược `services.jar` ([ĐO grep] 0 kết quả trong các cây RE cục bộ) | [CHƯA BIẾT] cho firmware xe owner |
| Họ hàng đã đo trên xe Kachi | cổng `relatestart` chặn service/broadcast/provider vào app không chạy, activity đi qua ([ĐO firmware], spec shortcuts-autostart Gốc 5) · `ssc_skip startServiceLocked` trên xe 05/10 · chú thích `NavConnect` đã nghi "ROM thả bind (ssc_skip)" | [SUY] cùng một cổng — OD bổ sung đúng mảnh `bindServiceLocked` |

## 4. Hai lỗ còn mở (chưa đổi mã — cần đo trước, CLAUDE.md §14)

- **L1 — Kachi KHÔNG phải HOME** (cài như app thường, chưa bấm *Đặt làm màn hình chính*): `am force-stop` không kéo theo
  lượt dựng lại tiến trình ⇒ thiếu bước (2) của OD ⇒ [SUY theo OD] lượt ghi lại ở bước (3) gặp cổng ⇒ vẫn kẹt. Hướng (chờ
  đo): chèn một lượt khởi động tiến trình tường minh giữa `sleep` và `settings put` (vd `am start -n <gói>/<activity
  không giao diện>`), CHỈ sau khi đo được ca này trên xe — lệnh tách rời là đường đã chạy ngoài hiện trường (§6).
  Đề xuất backlog: `A11Y-REBIND-EXPLICIT-START` 🚗.
- **L2 — lượt ghi thứ hai của toggle hỏng** (CODE-FIX-AFTER-283 mục 5): nếu component đã VẮNG khỏi enabled lúc
  `am force-stop`, [ĐO AOSP] `onHandleForceStop` chỉ duyệt `mEnabledServices` (`:464-474`) ⇒ mục `Binding` của mình KHÔNG bị
  gỡ ⇒ lượt ghi lại sau đó vẫn gặp `mBindingServices.contains → continue` (`:1630-1632`) ⇒ kẹt tới lượt leo KẾ (lúc đó
  component đã có trong enabled nên force-stop gỡ được). Hướng: ghi lại danh sách CÓ mình TRƯỚC `am force-stop` trong lệnh
  tách rời (ghi khi đang kẹt là no-op theo `:1630-1632`). Cùng lẽ L1: không đổi lệnh tách rời khi chưa đo. Đề xuất backlog:
  `A11Y-PREWRITE-FORCESTOP` 🚗.

## 5. Cách chốt trên xe (một buổi, chỉ đọc + một lượt "Sửa ngay")

1. `logcat -b all -s ssc_skip` trong lúc tắt máy → mở xe: có dòng `bindServiceLocked 1000 want to bind <uid Kachi> … ignored`
   không (khớp OD) — lấy qua `ClusterDiag`/nhật ký phiên, không bắt người lái gõ lệnh (CLAUDE.md §11).
2. Ca L1 (cần một xe/hồ sơ Kachi KHÔNG làm HOME): bấm *Kiểm tra / Sửa ngay* khi đang kẹt, đọc `dumpsys accessibility`
   (`Binding services`) sau ~8 s.
3. Ca L2: không tái hiện tự nhiên được — chỉ đo nếu thấy một lượt toggle hỏng trong nhật ký `A11yJournal`.

## 6. Kết luận

[SUY] Ghi chú OD **khớp** và **bổ sung** cho hiểu biết của Kachi: lệnh tách rời 2.83 đã đi đúng trình tự 1 → 2 → 3 của OD
nhờ Kachi là HOME (bước 2 ngầm). Không có bằng chứng cần đổi watchdog/thang chữa ở 2.93; hai lỗ L1/L2 có hướng sửa rõ,
chờ một phép đo trên xe trước khi chạm lệnh đang chạy tốt.
