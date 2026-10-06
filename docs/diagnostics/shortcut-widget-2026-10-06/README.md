# 2.92 · SHORTCUT-WIDGET-292 — widget lối tắt: icon to, lề nhỏ, không trần 8 app · soát widget khác · đo máy ảo 2026-10-06

> **Trạng thái**: Current · **Cập nhật**: 2026-10-06 · **Spec**: [`specs/kachi-292-shortcut-widget.html`](../../specs/kachi-292-shortcut-widget.html) ·
> **Backlog**: `SHORTCUT-WIDGET-292` · **Máy**: `emulator-5554` (`clusternav10`, Android 10, 1920×1080, 240 dpi = màn xe [ĐO log xe 24/09 `wm size -d 0`]) · **Không đụng xe.**
> **Mục đích**: số đo trước/sau cho lời owner 06/10 (ảnh xe: ô dọc hẹp, widget *Lối tắt ứng dụng* một cột 8 icon tròn, lề hai bên trống lớn) — *"icon hơi bé so với thanh, margin 2 bên nhiều quá phí … check thêm các widget khác … không nên giới hạn 8 app"*.

## Cách đo

- **Bản TRƯỚC** = bản 2.89 release đang cài sẵn trên máy ảo (base.apk sha256 `7d9c6761…afd7` = sha bản đăng OTA 2.89 ở backlog
  `REL-2.89`, vc191). Cùng mã widget với
  HEAD 2.91: `git diff c558450..HEAD` rỗng ở mọi tệp widget/lưới (`Shortcut*`, `Widget*`, `Fit*`, `GridFit`, `FitRules`, `WorkspaceView*`,
  `AppWidgetSlotHost`, `MediaWidgetView`, `Group*`) [ĐO]. **Bản SAU** = `vehicleTest` dựng từ cây làm việc (vc193, chưa bump, không đăng):
  lượt đầu sha256 `cb2c98f8…` (chưa có lộ nửa icon khi cuộn), lượt cuối `f93b31fa…` (mọi số dưới đây).
- Bố cục dựng bằng prefs (không bấm UI): hồ sơ `Test` của máy ảo nhận `grid_layout` / `slot_n` / `app_shortcuts` / `dock_edge` /
  `dock_scale` / `theme_mode` / `wallpaper_prefs=false`; ghi `kachi_workspace.xml` giữa hai lượt `am force-stop com.byd.launcher` (adbd root),
  rồi HOME. Lệnh đổi trạng thái (CLAUDE.md §4): chỉ `am force-stop` gói Kachi · HOME · `input tap/swipe` · `am stack remove` đúng stack
  tác vụ Tệp do chính lượt chạm QA tạo. Không lệnh `wm`, không đụng display khác.
- Đo: khung + ô con mỗi icon từ `uiautomator dump`; icon + khe + trục cuộn từ dòng mới `adb logcat -s WidgetFit`
  (`shortcuts n=… frame=… -> c×r icon=… gap=… scroll=… content=…`, bản SAU); bản TRƯỚC tính icon/khe bằng phép R-SI1 cũ (vét cạn) rồi
  đối chiếu ô con trong dump (ô con = icon + 2 × ⌊khe/2⌋ — khớp mọi ca); widget khác: hộp mực (điểm khác nền > 28) trong từng khung trên ảnh chụp.
- Ứng dụng có màn khởi chạy trên máy ảo: **23** (22 trừ Kachi) [ĐO `cmd package query-activities`] ⇒ ca 30/40 app dùng thêm gói giả
  `vn.qa.fakeNN` (hiện icon app chung, mờ — đúng đường "app đã gỡ").
- Ảnh chụp (PNG) chỉ lưu cục bộ — `.gitignore` chặn `docs/diagnostics/**/*.png`; mô tả ảnh ở cuối tệp. Số đo đầy đủ ở các TSV cùng thư mục.

## 1. Ca ảnh owner (ô dọc hẹp)

Khung thật trên xe trong ảnh [CHƯA BIẾT] (ảnh không kèm số). Khung dọc hẹp nhất dựng được trên lưới 12×6 = 2 cột × 6 dòng, thanh nút TRÁI
150 % ⇒ **262×956 px** [ĐO]. Với phép 2.91, 8 app ra MỘT cột khi khung ≤ 258 px (vét cạn ở `ShortcutGridFitTest` · spec §6) — 262 px vừa
qua ngưỡng nên 7 app tái hiện đúng dáng ảnh.

| Ca | Khung | 2.91 (trước) | 2.92 (sau) | Mức |
|---|---|---|---|---|
| **Ca ảnh**, 8 app | 258×956 | 1 cột · icon **89 px** · lề hai bên **84,5 px** | 2 cột × 4 · icon **111 px** (+25 %) · lề **12 px** | [SUY phép thuần — test `ca anh - o doc hep 8 app…`] |
| Tái hiện máy ảo, 7 app | 262×956 | 1 cột · icon **101 px** · lề **80,5 px** (ô con 181×131) | 1 cột · icon **122 px** (+21 %) · lề **70 px** (ô con 192×134) | [ĐO] |
| Máy ảo, 8 app | 262×956 | 2×4 · icon 90 px · lề 27,3 px | 2×4 · icon **113 px** (+26 %) · lề 12 px | [ĐO] |

Ghi chú: 7 app vẫn ra một cột vì icon một cột (122 px) to hơn hai cột (113 px) — luật "icon to nhất" của R-SI1 (owner duyệt 03/10) giữ
nguyên; lề còn 70 px là chiều cao chặn (7 icon + khe 8 dp lấp đủ 956 px). Nếu owner muốn "lấp ngang trước" ⇒ OQ2 của spec.

## 2. Các khung khác + nhiều app — [`shortcut-grid.tsv`](shortcut-grid.tsv)

| Ca | Khung | 2.91 | 2.92 |
|---|---|---|---|
| ô hẹp, thanh nút DƯỚI, 8 app | 301×804 | 2×4 · 103 px | 2×4 · **132 px** |
| dải rộng thấp 12×1, 8 app | 1872×123 | 8×1 · 76 px · khe dọc 23,5 | 8×1 · **99 px** · khe dọc 12 |
| bố cục 4 ô, 8 app | 929×395 | 4×2 · 136 px | 4×2 · **179 px** |
| ô nén (ô 3 widget), 8 app | 277×252 | 3×3 · 60 px | 3×3 · **68 px** |
| ô 2×1, 8 app | 301×123 | 4×2 · **42 px**, ô con 68×54 px (36 dp < 48 dp) | **cuộn ngang** · 60 px · 3 icon trọn + nửa icon thứ 4 · ô con 82×90 |
| 12 app | 262×956 | chỉ hiện **8** (log `app_shortcuts: bỏ 4 mục quá trần 8`) | 2×6 · 113 px |
| 20 app | 262×956 | chỉ hiện **8** (`bỏ 12 mục`) | 2×10 · 82 px |
| 30 app | 262×956 | chỉ hiện **8** (`bỏ 22 mục`) | 3×10 · 71 px |
| 40 app | 262×956 | — | **cuộn dọc** · 3 cột · 60 px · nội dung 262×1078 |
| 20 app | 1872×123 | chỉ hiện 8 | 20×1 · 81 px |
| 30 app | 1872×123 | — | **cuộn ngang** · 60 px · 25 icon trọn + nửa · nội dung 2208×123 |

Mọi ô con ở chế độ khớp ≥ 72×72 px = 48 dp (icon ≥ 60 px + khe ≥ 12 px) [ĐO dump]. Không icon nào chồng/cắt (ngoài nửa icon lộ ở mép khi cuộn — chủ ý).

## 3. Cuộn + chọn > 8 app đầu-cuối — [`scroll-and-picker-e2e.tsv`](scroll-and-picker-e2e.tsv)

- [ĐO] Vuốt ngang ô 2×1 dời nội dung ~230 px; chạm icon vừa cuộn vào (Tệp) ⇒ mở ĐÚNG Tệp toàn màn; vuốt BẮT ĐẦU trên một icon chỉ cuộn,
  không mở app; chạm lề ô (ngoài icon) vẫn mở ngăn kéo gán ô như trước; vuốt nhanh dọc (40 app) trôi tới hàng cuối, hàng cuối 1 icon căn giữa.
- [ĐO] Lưới cuộn khai trợ năng `HorizontalScrollView`/`ScrollView`, `scrollable=true` (dump).
- [ĐO] Từ ô "Chưa có lối tắt" ⇒ Cài đặt *"Chọn ứng dụng… (0)"* (hết "/8") ⇒ ngăn kéo chọn 12 app ⇒ *"Áp dụng (12)"*, không câu nhắc
  trần ⇒ Cài đặt *"Lối tắt ứng dụng (12)"* ⇒ widget 12 icon; prefs giữ 12 mục.
- [ĐO] Khối lối tắt trên thanh nút với 20 app nằm trong khung cuộn sẵn có của thanh (HorizontalScrollView, scrollable).

## 3b. Lượt 2 — sau senior review (3 vá tầng vẽ) — [`scroll-and-picker-e2e.tsv`](scroll-and-picker-e2e.tsv) dòng R1–R5

Bản `vehicleTest` sha256 `20fc2948…` (vá review F1 `setWillNotDraw(false)` · F2 dồn phần lẻ khi kéo · F3 trợ năng YES khi cuộn + thao tác cuộn theo hướng).
- [ĐO] Mép mờ "còn nữa" nay VẼ THẬT: ô 2×1 tối, nửa icon lộ ở mép phải sáng 108 → 33 (nền 30) trong 18 px; sáng: 108 → 235; 40 app: hàng dưới mờ dần.
  Bản trước review phẳng 108 → 107 — tức mép mờ của lượt 1 chưa bao giờ hiện (ảnh lượt 1 chỉ có nửa icon lộ, không mờ).
- [ĐO] Kéo chậm 100 px trong 4 s (≈ 0,4 px/khung): nội dung dời 87 px = 100 − ngưỡng chạm, không mất phần lẻ; không mở app; chạm icon sau kéo mở đúng app.

## 4. Soát widget khác — [`widget-ink.tsv`](widget-ink.tsv)

Mỗi widget ở 3 khung: hẹp cao **301×804** · rộng thấp **1558×123** · to **1558×668** (lưới `0,0,2,6;2,0,10,1;2,1,10,5`, thanh nút DƯỚI, tối).
"Mực" = hộp điểm khác nền. Vá 2.92: khối dọc chung `WidgetViews.col` lề 16 dp → 8 dp.

| Widget | Dựng bằng | Trước → sau (mực) | Kết luận |
|---|---|---|---|
| Năng lượng (vòng) | `col` + `RingView` | hẹp 209 → **229** px · rộng thấp 63 → **81** px cao · to 510 → 530 | vá (lề 16 → 8 dp) |
| PM2.5 (vòng) | `col` + `RingView` | hẹp 209 → 229 · rộng thấp 66 → 88 cao | vá |
| Trạng thái xe | `col` + `CarMiniView` | hẹp 213×619 → 233×652 · rộng thấp: hình xe 31 → **52** px cao | vá; ở dải rộng thấp chú thích vẫn xếp DƯỚI hình ⇒ hình còn nhỏ — backlog `WIDGET-CAR-STRIP-LAYOUT` |
| Nhạc | `col` | hẹp 235×283 → 257×309 · rộng thấp 75 → 99 cao · to: không đổi (trần cỡ 1,5) | vá |
| Đồng hồ | `col` (co giãn) | hẹp 234×172 → 260×193 · rộng thấp chữ 44 → 52 cao · to: không đổi (trần) | vá; ☀ "ngoài xe" nằm tách xa chữ (drawable đầu dòng của TextView bề ngang MATCH) — có từ trước, backlog `CLOCK-SUN-DETACHED` |
| Tốc độ | `col` (co giãn) | hẹp 208 → 218 rộng · to: không đổi (trần cỡ 1,5) | vá; khung to chữ nhỏ do trần `MAX_SCALE_SINGLE` [ĐỀ XUẤT chờ owner `FIT-OWNER-0410`] — không đổi |
| Thẻ đọc chung (vòng/số/huy hiệu/dải) · thẻ chữ suy giảm | `col` | cùng đường vá | vá (không chụp riêng — cùng hàm) |
| Lốp (bảng 4 bánh) | `TyreBoardView` (tự vẽ) | 285×258 / 1496×106 / 1496×583 — lề 8/31 px | giữ |
| Bảng tổng hợp | `board` (lề 8 + 4 dp) | 247×502 · 1526×87 · 1504×534 | giữ |
| Ảnh | `PhotoWidgetView` (lấp khung) | lề 8 px | giữ |
| Nhóm khí hậu / lốp / kính | `GroupTileView` | không đổi; khung một hàng chỉ còn tiêu đề (đã có backlog `GROUPBOARD-1ROW`); khí hậu ở khung hẹp 3 cột nhãn bị cắt ("Tr on…") | giữ — ghi chú |
| Nút (gió) · ô đọc (PM2.5 datum) | `ControlTileFactory` + lề 12 dp | thẻ cách mép 27 px (hẹp/to), 16 px (rộng thấp) | giữ (thẻ trong ô, lề 12 dp có lý do riêng ở `actionTile`) |
| Widget app khác (VietMap · YT Music…) | `AppWidgetHostView` | lề 18 / 6 / 18 / 30 px (trái/trên/phải/dưới) | giữ — lề MẶC ĐỊNH nền tảng (dưới) |

**Widget app khác** [ĐO decompile `framework.jar` máy ảo A10]: `AppWidgetHostView.setAppWidget` luôn đặt lề `default_app_widget_padding_*`;
[ĐO `aapt2 dump resources framework-res.apk`] giá trị `sw720dp` (màn 1280×720 dp thuộc nhóm này) = 12 / 4 / 12 / 20 dp = đúng 18 / 6 / 18 / 30 px đo
ở widget VietMap trong ô 730×395 của hồ sơ gốc. `updateAppWidgetSize(Bundle, …)` 5 tham số trừ CHÍNH lề mặc định khỏi cỡ báo cho nhà cung cấp
⇒ đổi lề phải bù cỡ báo; lợi ≤ 12 px mỗi trục ⇒ chưa đổi, ghi backlog `APPWIDGET-PADDING-UNIFORM` [P3] cho owner chọn.

## 5. Trả máy ảo — [`emulator-restore.tsv`](emulator-restore.tsv)

Làm HAI lượt (lượt 2 sau senior review), mỗi lượt cùng một bản sao lưu đầu phiên. APK 2.89 (191) gốc cài lại (sha trùng); thư mục dữ liệu tráo nguyên từ bản sao lưu (103/108 tệp trùng sha — 5 tệp lệch là dấu chạy do tiến
trình Kachi tự bật lại ngay khi dịch vụ Hỗ trợ được gắn lại: `a11y_proc_start_elapsed`, sổ chuyến, `a11y-bind.log`, ProfileInstaller);
thư mục ngoài 1 527/1 527 tệp trùng; AppWidget giữ nguyên id; màn trước = Google Maps như lúc nhận (VietMap do Kachi tự mở sau khi bật lại ⇒ force-stop, lúc nhận
nó không chạy); còn một tác vụ HOME của Kachi trong home stack (vùng cấm gỡ, mất sau khởi động lại); adbd về không root,
`adb reverse tcp:5555` đặt lại; máy ảo tạm dừng lại.

## 5b. Lượt 3 — quyết định điều phối OQ1–OQ3 (`oq-pass3.tsv`)

Bản `vehicleTest` của cây hiện tại; "trước" = 2.89 cài sẵn (cùng mã widget cũ ở hai chỗ đổi).

- **OQ2 (lấp bề ngang)** [ĐO]: ô 262×956 (thanh nút trái 150 %) + 7 app ⇒ `WidgetFit … 262x956 -> 2x4 icon=113
  gap=12.0x100.8` — trước OQ2 là 1×7 icon 122 lề 70. Mực hai bên 12/11 px; hàng cuối một icon, tâm x 390,5 = tâm khung.
- **OQ3 (widget app khác lề đều 8 dp)** [ĐO]: ràng buộc hai widget thật (đồng hồ số, lịch biểu) qua bảng chọn của chính
  Kachi (gói đã có bind-grant từ trước — không cấp quyền mới) trên 2.89, đo, rồi cài bản mới ĐÈ (giữ dữ liệu, cùng id
  widget) và đo lại: lề con gốc 18/6/18/30 → 12/12/12/12 px; cỡ báo nhà cung cấp (đọc `appwidgets.xml`, số hệ 16)
  595×512 → 603×520 dp = đúng vùng nội dung ÷ 1,5. Thêm hai dáng khung (dải 1872×123, ô hẹp 301×804): không chữ nào ra
  ngoài khung, đồng hồ số tự co theo bề rộng.
- **Dọn**: thu hồi hai id widget bằng đường của chính app (`AppWidgetSlotHost.sweep` lúc nạp, với một ô trỏ id giả không
  thuộc host) ⇒ `dumpsys appwidget` (host · grant · danh sách widget) trùng bản đầu phiên. Một cú chạm lỡ (đầu ô ⇄ đã tự
  ẩn) mở Lịch Google ⇒ gỡ đúng stack `standard` #52 do QA tạo + `force-stop` Lịch (không bấm tiếp màn chào của Lịch).
- Nội dung sự kiện trong widget lịch (dữ liệu mẫu của máy ảo) cố ý KHÔNG chép vào đây.

## 6. Mô tả ảnh (lưu cục bộ)

- `b-photo7.png` (2.89) — ô 262×956 một cột 7 icon tròn ~101 px, dải trống hai bên rõ (≈ 80 px mỗi bên) — dáng ảnh owner.
- `a-photo7.png` (2.92) — cùng ô, icon ~122 px, khe dọc ~13 px, lề hai bên ~70 px.
- `a-photo8.png` (2.92) — 8 app: hai cột icon 113 px kín bề ngang (lề 12 px), khe dọc ~100 px. Bản sáng `a-photo8-day.png` cùng hình học.
- `a-photo20.png` / `a-photo30.png` — 20 app 2×10 icon 82 px · 30 app 3×10 icon 71 px, không cắt.
- `a-photo40.png` — 3 cột icon 60 px, hàng 13 lộ nửa ở mép dưới (dấu cuộn), icon app giả hiện hình app chung mờ.
- `a-small8.png` / `a-small8-day.png` — ô 2×1: 3 icon trọn + nửa icon Danh bạ ở mép phải.
- `a-wide8.png` — dải 12×1: 8 icon 99 px cách đều, khe dọc 12 px.
- `aw-w_energy.png` / `aw-w_car.png` / `aw-w_media.png` (+ `awd-*` bản sáng) — vòng/hình xe/ảnh bìa to hơn bản `bw-*` ở cả ba khung.
- `a-dock20.png` — thanh nút dưới: 20 icon lối tắt, ô nút xe đẩy sang phải (cuộn được).
- Lượt 3: `oq2/narrow7.png` — hai cột icon 113 px kín bề ngang, hàng cuối một icon giữa · `oq3/before-2widgets.png`
  (2.89: thẻ lịch cách đỉnh 6 px, cách đáy 30 px) / `oq3/after-2widgets.png` (12 px đều bốn phía) · `oq3/after-strip-crop.png`
  / `oq3/after-narrow-crop.png` — đồng hồ số trọn trong dải thấp và ô hẹp.

## Chưa đo / giới hạn

- Khung thật của ô trong ảnh owner [CHƯA BIẾT] ⇒ 🚗 OC-292-1: chụp màn + `adb logcat -s WidgetFit` (dòng `shortcuts … frame=W×H`) trên xe.
- Cảm nhận khe 8 dp / icon 40 dp sàn trên màn xe thật [CHƯA BIẾT]; mật độ xe 240 dpi [SUY từ log xe `density=1.5`].
- Một ảnh sáng lượt đầu (`a-photo8-day.png` của bản `cb2c98f8…`) dính tấm "Đang nghe…" của giọng nói (tự bật, không do lượt đo) — đã chụp lại sạch.
