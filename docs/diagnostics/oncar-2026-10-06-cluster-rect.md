# Buổi xe 2026-10-06 — cụm Chữ nhật / theme trên Seal (Kachi 2.89 (191))

> **Trạng thái**: Current · **Ngày đo**: 2026-10-06 · **Xe**: Seal, DiLink 3.0, fw 2602030 · **Bản Kachi trên xe**: 2.89 (191)
> **Spec xử lý**: [`../specs/kachi-290-cluster-rect-fix.html`](../specs/kachi-290-cluster-rect-fix.html) (2.90 Phần 1)
> Ghi lại NGUYÊN VĂN sự thật owner đo trên xe (không địa chỉ đích, không IP). Mức bằng chứng theo `CLAUDE.md` §2.

## Sự thật đo được

| # | Sự thật | Mức |
|---|---|---|
| F1 | Màn ảo chiếu cụm `fission_bg_xdjaVirtualSurface` (owner `com.xdja.containerservice`, `FLAG_PRESENTATION`) **CÓ TỪ LÚC ĐẦU MÁY KHỞI ĐỘNG** (display 4, uptime 15 h). Cổng 2.89 (`themeOnVacantVd=false`) log ở MỌI lượt mở: `[SimpleCast] theme 31 cụm=[4] → Skip(reason=VD_PRESENT, …) ⇒ SKIP_KNOWN` ⇒ theme không bao giờ được gửi. | [ĐO] |
| F2 | Theme cụm **giữ qua các lần tắt/nổ máy** (cong từ hôm trước, đầu máy không khởi động lại). Giả định "nổ máy trả theme gốc" (F3 của spec 2.89) **SAI** trên xe này; suy kiểu hiện tại từ sổ/boot không tin được. | [ĐO] |
| F3 | 2.89 tin RECT, ghim Maps vào `FULL__RECT 50,128,1285,555` trên cụm đang CONG ⇒ `ClusterBlackActivity` đen lộ quanh Maps (owner: "bị overlay đen"). | [ĐO] |
| F4 | Thử tay: màn ảo 4 có **0 task + 0 cửa sổ** (`am stack list \| grep -c displayId=4` = 0, `dumpsys window windows \| grep -c mDisplayId=4` = 0, sau khi force-stop VietMap) ⇒ `service call AutoContainer 2 i32 1000 i32 31 s16 ""` ⇒ **KHÔNG sập** (pid `system_server`/`surfaceflinger` không đổi), màn ảo dựng lại với **id MỚI (4 → 9)**, rồi `16 → 35` OK, cụm thành chữ nhật ("m/h" lạc góc trên trái, panel ADAS lớn bên phải). Hai lần sập ngày 05/10 đều khi CÓ lớp (Maps) trên màn ảo. | [ĐO] |
| F5 | Bóng nổi của mod VietMap giữ **3 cửa sổ `TYPE_APPLICATION_OVERLAY` của `vn.vietmap.live`** trên màn ảo cụm, thường trực (kể cả khi tắt chiếu) ⇒ màn ảo "không trống" mỗi khi VietMap chạy. | [ĐO] |
| F6 | Ở RECT, phím menu vô-lăng chỉ thu nhỏ hình xe/đường; nền ADAS trắng vẫn còn. | [ĐO] |
| F7 | Kachi mở ở RECT: sau khi theme đổi, bước đọc lại khung vẫn hỏi display CŨ: `shell: wm size -d 4` → `Physical size: 0x0` ⇒ `khung Chữ nhật … lệch: muốn 50,128,1285,555, đọc được ? ⇒ resize lại MỘT lần` ⇒ `VẪN lệch`. | [ĐO] |
| F8 | Cài đặt › Chiếu cụm đứng "Đang mở cụm…" sau khi mở xong: `SettingsSectionsCast.refreshStatus()` chỉ chạy sau thao tác người dùng, không theo trạng thái coordinator ⇒ nút kích thước/vị trí khoá tới khi mở lại trang. | [ĐO] triệu chứng · [SUY đọc mã] nguyên nhân |
| F9 | Ở RECT bộ chỉnh khung kẹp vào `ClusterRectLayout.FREE_AREA` (50,128,1285,555) mọi hướng. Owner: *"bị giới hạn cả cao thấp trái phải, kỳ lắm, mở bung ra cho người ta tự set size"*; rồi *"cứ để full resolution nhé, nó over thì user họ tự chỉnh được vị trí, kích thước, DPI mà, nên mình không cần tính gì, chừa gì đâu"*. | [ĐO] |
| F10 | Owner về km/h: *"ko cần vẽ kmh … HUD, bản đồ, bóng VietMap đều có"*. | quyết định owner |

## Root-cause F7 (off-car, sau buổi xe)

- **[ĐO test]** `SimpleCastCoordinator` dựng `CastGeometryController(shell, prefs, { displayId })` trong khối khởi tạo — ở đó
  `displayId` là **tham số dựng (SEED)**, che thuộc tính `displayId` ⇒ bộ đọc kích / đọc-lại khung chụp seed mãi mãi. Seed =
  `prefs.lastDisplayId()` lúc tiến trình dựng (4 — màn ảo trước khi đổi theme). Test `ClusterRectStaleDisplayTest` dựng lại đúng
  `wm size -d 4` sau khi màn ảo dời 4 → 9 (đỏ trên mã cũ, xanh sau vá `{ this.displayId }`).
- **[ĐO nguồn A10 r47]** `wm size -d N` cho display không còn tồn tại in `Physical size: 0x0`:
  `WindowManagerShellCommand.java:111-113` ← `WindowManagerService.getInitialDisplaySize` (`WindowManagerService.java:4996-5003`)
  chỉ điền kích khi `getDisplayContent(N) != null`.
- **[ĐO nguồn A10 r47]** id logical display cấp tăng dần, không tái dùng: `DisplayManagerService.java:1033-1034`
  (`mNextNonDefaultDisplayId++`) ⇒ khi hai màn ảo cụm cùng tên cùng hiện, id lớn hơn là màn mới.

## Mở / chưa biết

- [CHƯA BIẾT] Gửi `30` khi cụm ĐÃ cong (cùng kiểu) — màn ảo có dựng lại không (OQ1 spec 290). Mã chịu được cả hai.
- [CHƯA BIẾT] App khác ngoài VietMap có giữ cửa sổ phủ trên màn ảo cụm không (OQ2) — bảng `ClusterBubbleApps` chỉ có VietMap.
- 🚗 Buổi xe kế: kịch bản V-oncar (a)–(d) ở §6 spec 290.

## Badge giới hạn tốc độ không hiện (sáng 06/10) — phân tích off-car sau buổi xe

Nguồn: log Kachi tự chụp `usage-all.log` (07:05–07:52), `live-kachi.txt` (07:39–08:15), `display.txt` / `windows.txt` / `stacks.txt` (07:55). Spec 290 §4.5.

| # | Sự thật | Mức |
|---|---|---|
| B1 | `SpeedBadgeOverlay: overlay initialized for display 4 (1920x720)` lúc 07:05:19 và 07:12:01 — display 4 = `fission_bg_xdjaVirtualSurface`; display 8 = `kachi-slot-0-…` (`FLAG_PRIVATE`) | [ĐO] |
| B2 | 07:05–07:52 đang dẫn đường (`NavListener nav dist=…` liên tục) mà **0 dòng `ClusterSpeedBadge show`**; chỉ `hide gen=1 reason=PROCESS_RESTARTED` ⇒ badge chưa bao giờ có giá trị để vẽ | [ĐO] |
| B3 | Cả ba widget VietMap `getAppWidgetInfo(38\|39\|40) returned null for SPEED_LIMIT\|ALERTS\|ALERT_FULL — keeping saved ID` ở cả hai lần khởi động tiến trình | [ĐO] |
| B4 | `getAppWidgetInfo` null = widget không còn hoặc provider null/zombie; danh sách cài loại zombie ⇒ provider có mà id null ⇒ widget đã xoá (gỡ hẳn gói xoá mọi widget của provider) — r47 `AppWidgetServiceImpl.java:1410-1417`, `:1725`, `:452-462` → `:3445` → `:3430-3442` → `:2312-2317` → `:2293-2310` | [ĐO nguồn] |
| B5 | Kachi 2.89 giữ id chết mãi + `autoBindMissing` bỏ qua ô đã có id ⇒ không RemoteViews ⇒ không giới hạn tốc độ (tự khoá) | [ĐO mã] |
| B6 | Widget mất vì bản mod VietMap được cài lại kiểu gỡ-rồi-cài (`PACKAGE_ADDED vn.vietmap.live` 07:54:56) | [ĐOÁN] |
| B7 | Id display KHÔNG phải gốc sáng nay; nhưng mã cũ thử `getDisplay(1)` trước (sau khởi động nguội display 1 = ô `kachi-slot-0` [ĐO 15/09]) và bỏ lượt thêm màn ảo dựng lại khi màn mới đến trước lúc màn cũ bị gỡ | [ĐO mã] · thứ tự thêm/gỡ [CHƯA BIẾT] |

Chốt 🚗 buổi kế: `dumpsys appwidget | grep -A3 com.byd.launcher` (id hiện có của host Kachi) + log `auto-bound SPEED_LIMIT` / `widget đã mất … bind lại` rồi `ClusterSpeedBadge show`.
