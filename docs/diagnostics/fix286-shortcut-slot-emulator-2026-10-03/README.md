# FIX286 · R-SC — lối tắt *Ô n* đè widget + mở lại app đã đóng trong ô · đo máy ảo 2026-10-03

> **Trạng thái**: Current · **Spec**: [`specs/kachi-286-field-fixes.html`](../../specs/kachi-286-field-fixes.html) §3.10 R-SC ·
> **Backlog**: `FIX286-SHORTCUT-SLOT` · **Máy**: `emulator-5554` (`clusternav10`, A10, API 29) · **Không đụng xe.**

Lỗi xe owner 03/10 (spec lối tắt [`kachi-launcher-shortcuts-autostart.html`](../../specs/kachi-launcher-shortcuts-autostart.html), `ShortcutPlan.kt`):
(A) ô 1 là widget trình chiếu ảnh, lối tắt Google Maps kiểu *Ô 1* ⇒ Maps mở **toàn màn** thay vì vào ô 1;
(B) Maps đang ở ô 1 bị tắt ⇒ chạm lại lối tắt chỉ nháy ô, Maps **không mở lại**.

## Cách đo

- Bản đỏ: `apk/Kachi-2.85-release.apk` (sha256 `c3a7b12f…`) đang cài sẵn. Bản xanh: `vehicleTest` dựng từ cây làm việc
  (chưa commit, không bump — `versionCode 186`): lượt 1 sha256 `09868c04…` (ca a2/a3/b2/b3/d1/p1), lượt cuối `ce790c07…`
  (ca c0/c1/e1/e2 — khác lượt 1 đúng MỘT dòng log ở nhánh `Reopen` của `KachiHomeShortcuts.act`).
- Prefs sao lưu TRƯỚC (`tar` 9 tệp + sha1); ghi prefs giữa hai lượt `am force-stop` (root cho bản release, `run-as` cho
  `vehicleTest`). Cấu hình đo: bố cục `THREE`, ô 1 `widget:w_photos`, ô 2 widget bên thứ ba YT Music `aw:714`, ô 3
  `widget:w_clock`; thanh nút có `launcher_shortcuts`; lối tắt `Maps|S1, Đồng hồ|S2` (ca c/e: `Maps|B, Đồng hồ|F`).
- Chạm = `input tap` vào icon tìm bằng `uiautomator` (contentDescription = tên app). Mỗi ca: `am stack list` trước/sau,
  `logcat -b events` (`am_`/`wm_`), log `KachiShortcut`/`KachiVd`/`VdAppHost`/`KachiAppWidget`, sha prefs trước/sau.
  Công cụ: `tools/drv.py` (driver), `tools/mutate_sc.py` (phá thử).
- Lệnh đổi trạng thái (CLAUDE.md §4): `am force-stop` đúng MỘT gói khách (Maps / Đồng hồ) hoặc Kachi; `input tap`;
  `adb root/unroot` + `adb reverse tcp:5555 tcp:5555` (kênh dadb loopback); không lệnh `wm`, không display 1.
- Sau đo: cài lại `apk/Kachi-2.85-release.apk`; `tar xf` đè tại chỗ + `chown 10163:10163` + `chmod 660` + `restorecon`
  ⇒ 8/9 tệp sha khớp; `clusternav_prefs.xml` lệch đúng một khoá `a11y_proc_start_elapsed` (mốc thời gian mỗi lần tiến
  trình bật, Kachi là HOME nên tự bật lại ngay sau `force-stop`); xoá `kachi_test_bridge.xml` do lượt này tạo; HOME một lần
  ⇒ VietMap về ô 1 như trước đo.

## Kết quả

| Thư mục | Ca | Kết quả | Mức |
|---|---|---|---|
| `a1-red-285-widget-slot1` | **Mốc đỏ (A)** 2.85: chạm *Maps · Ô 1*, ô 1 = trình chiếu ảnh | `tap … S1 usable=true stages=0 -> OpenFull(reason=SLOT_WIDGET)`; Maps `mResumedActivity` toàn màn display 0 | [ĐO] |
| `b1-red-285-dead-in-slot` (+`-tap`) | **Mốc đỏ (B)** 2.85: Maps ở ô 1 (VD 231) ⇒ `am force-stop` ⇒ chạm *Maps · Ô 1* | 1,5 s sau: stack của VD 231 biến mất, 0 dòng Maps (`stack-after-force-stop.txt`); +2 s ô còn khung cuối; nhịp đo kết luận chết ⇒ thẻ *"App đã đóng — chạm để mở lại"* (≤ 28 s). Chạm lối tắt ⇒ `Noop(highlight=0, reason=null)`, Maps không pid, không task | [ĐO] |
| `a2-green-widget-slot1` | **(A) xanh**: cùng ca a1 | `presence=UNKNOWN -> PlaceTemp(slot=0, evict=null)` ⇒ tạo VD 232, Maps task 3088 trên VD 232, KachiHome resumed display 0; sha `kachi_workspace.xml` trước = sau, `slot_0` vẫn `widget:w_photos` | [ĐO] |
| `a3-green-appwidget-slot2` | Widget bên thứ ba: *Đồng hồ · Ô 2*, ô 2 = YT Music `aw:714` | `PlaceTemp(slot=1, evict=null)` ⇒ Đồng hồ trên VD 233; `dumpsys appwidget` id 714 còn ở `hostId:19265` trước và sau; `slot_1` không đổi; Maps ô 1 giữ pid | [ĐO] |
| `b2-green-dead-card-then-tap` | **(B) xanh**: Maps (đặt tạm ô 1) `force-stop` ⇒ chờ thẻ ⇒ chạm *Maps · Ô 1* | `đo ô 0: … vd=232 ⇒ GONE` ⇒ `Reopen(slot=0)` ⇒ Maps task mới 3090 trên CÙNG VD 232, pid mới; thẻ mất; Đồng hồ ô 2 giữ pid | [ĐO] |
| `b3-green-kill-then-double-tap-fast` | `force-stop` Maps ⇒ 2 s ⇒ chạm HAI lần cách 0,3 s (trước khi nhịp đo kết luận) | hai lượt `GONE -> Reopen(slot=0)`; **đúng một** `am_create_activity` + một `am_proc_start` của Maps (lượt 2 bị rào "đang mở dở") | [ĐO] |
| `d1-green-restart-widget-back` | `am force-stop com.byd.launcher` ⇒ HOME | ô 1 trình chiếu ảnh + ô 2 YT Music (id 714, `hostId:19265`) về; sha prefs không đổi; task Maps/Đồng hồ mất cùng VD | [ĐO] |
| `p1-green-profile-switch-widget-back` | Maps tạm đè ô 1 ⇒ cầu `profile 'Mặc định 2'` ⇒ `profile 'Mặc định'` | `state.slots[1] = widget:w_photos`; id 714 còn; Maps task mất (host nhả). Hồ sơ 2 có YouTube ở ô ⇒ YouTube (bản cũ trên máy ảo) tự nhảy toàn màn che HOME — hành vi có sẵn, không thuộc R-SC; HOME ⇒ VD ô của hồ sơ 2 được nhả (`giải phóng … còn lại 0`) | [ĐO] |
| `c0-bg-alive-in-slot-noop` | *Maps · Chạy ngầm*, Maps sống ở ô 1 (lưu) | `⇒ IN_SLOT -> Noop(highlight=0, reason=RUNNING)`, 0 sự kiện `am_` của Maps | [ĐO] |
| `c1-bg-dead-in-slot-reopen` | dòng 12: `force-stop` Maps ⇒ 2 s ⇒ chạm *Chạy ngầm* | `⇒ GONE -> Reopen(slot=0)` + `mở lại … vào ô 0: đã ra lệnh` ⇒ Maps task 3099 trên VD 236 | [ĐO] |
| `e1-full-dead-in-slot-openfull` | dòng 9′: `force-stop` Đồng hồ (ô 3) ⇒ chạm *Đồng hồ · Toàn màn* | `⇒ GONE -> OpenFull(reason=null)` ⇒ Đồng hồ resumed toàn màn display 0 (task 3100) | [ĐO] |
| `e2-full-alive-in-slot-detach` | Hồi quy dòng 9: thẻ "đã đóng" ô 3 ⇒ chạm thẻ (mở lại) ⇒ chạm *Toàn màn* | `⇒ IN_SLOT -> DetachToFull(slot=2, byIntent=false)` ⇒ toàn màn (task 3101 giữ) ⇒ HOME ⇒ K8 về VD 237 (task 3101) — y như 2.85 | [ĐO] |
| `tools/mutations.out` | Phá thử 11 mắt xích (`tools/mutate_sc.py`) | 11/11 ĐỎ, khôi phục ⇒ xanh | [ĐO] |

Fixture vào `core/src/test/resources/diagnostics/`: `am-stack-list-emulator-2026-10-03-sc-maps-in-slot.txt` (=
`a0-red-285/stack-maps-in-slot.txt`) · `…-sc-maps-force-stopped.txt` (= `b1-…/stack-after-force-stop.txt`).

## Chưa đo / giới hạn

- ROM BYD (DiLink 3 A10 của owner): đường đo `am stack list` cùng định dạng `Stack id=` với máy ảo [SUY]; xe chưa chạy bản
  này [CHƯA BIẾT] — OC-SC1/2 (spec §6.2).
- Owner tả ô 1 **đen** sau khi tắt Maps; máy ảo cho khung cuối đóng băng rồi thẻ "đã đóng" sau ≤ 28 s. Owner tắt Maps
  bằng cách nào (vuốt khỏi gần đây / quản lý ứng dụng BYD / nút trong Maps) [CHƯA BIẾT] — R-SC2 quyết theo task, không
  theo cách tắt.
- A12/DL5: bộ đọc của phép đo lúc chạm (`StackParse`) nhận `RootTask id=` (bài `SlotPresenceTest`), nhưng nhịp đo dựng
  thẻ "đã đóng" (`SlotLiveProbe` → `FreeformLaunch.parseTaskIdOnDisplay`) chỉ nhận `Stack id=` ⇒ trên A12 thẻ có thể không
  bao giờ hiện [SUY đọc mã] — backlog `SLOTPROBE-A12-FORMAT`, ngoài phạm vi R-SC.
