# YouTube tự chuyển bài khi Kachi mở lại bài — đo máy ảo (2.99 R1)

> **Trạng thái**: Current · **Cập nhật**: 2026-10-10 · **Loại**: Diagnostics (đo máy ảo + gốc lỗi) ·
> **Mục đích**: chốt vì sao lượt "Tự mở nhạc khi lên xe → YouTube phát tiếp" chỉ phát MỘT bài rồi dừng (owner 10/10 trên 2.98),
> và cách mở để hết bài YouTube tự sang bài kế như khi mở app bình thường. Spec [`specs/kachi-299-plan.html`](../specs/kachi-299-plan.html) R1.
>
> Nhãn: **[ĐO]** đo máy ảo/mã · **[SUY]** suy luận · **[CHƯA BIẾT]**.

## 1. Kachi mở bài bằng đường nào [ĐO mã]

Kiểu *YouTube* + ô *Phát gì* trống ⇒ phát tiếp bài đã lưu (2.94 R3):

1. `TripMusicResume.prepare` (`app/…/trip/TripMusicResume.kt:361-371`) tìm `"<tiêu đề> <kênh>"` ⇒ `TripMusicPlan.watchUrl` ⇒
   `https://www.youtube.com/watch?v=<id>`.
2. `TripMusicRun.play` (`app/…/trip/TripMusicRun.kt:189-207`) quyết bằng `TripMusicPlan.play` (`core/…/TripMusicPlan.kt` `fun play`):
   phiên YouTube có SẴN và báo nhận URI ⇒ `MediaController.playFromUri`; không phiên (YouTube nguội trên trang chủ — đường
   thường gặp khi ô vừa mở lại app) ⇒ **K4-VIEW**: `am start --display <màn ảo ô> -a VIEW -d '<watch>' -p com.google.android.youtube
   --ez force_fullscreen true` (extra từ `VoiceAppTargets` YouTube `watchFullscreenExtra`, 2.96 R10, CHỈ đường phát tiếp —
   `TripMusicRun.viewWhenReady`).
3. `TripMusicResume.finish` chờ phiên PHÁT đúng tiêu đề ⇒ `seekTo(điểm đã lưu)` ngay.

Kachi KHÔNG `pause`/`stop` gì sau đó (grep `pause()` trong `app/src/main`: chỉ cầu nhạc + `SlotLiveProbe.pause` không liên quan);
tua muộn (`YoutubeLateSeek`) chỉ tua khi tiêu đề khớp bài đã lưu ⇒ không chạm bài kế.

## 2. Đo (máy ảo `kachi_play`, A10, YouTube 21.35.442 Premium đã đăng nhập)

Công cụ: app thăm dò nhỏ (ngoài repo) có quyền nghe thông báo ⇒ `MediaController` của YouTube: đọc trạng thái/metadata, `seekTo`,
`playFromUri`, `skipToNext`. Mỗi lượt: `am force-stop` YouTube → lệnh mở → chờ phát → tua tới `dur − 6…40 s` → đọc phiên mỗi 3 s
qua lúc hết bài. "Sang bài kế" = metadata đổi sang video khác và `state=3`. Dump nguyên văn: fixture
`core/src/test/resources/diagnostics/youtube-continue-2026-10-10.txt` (8 khối).

| Cách mở | Sang bài kế | Ghi chú |
|---|---|---|
| VIEW `watch?v=` + `--ez force_fullscreen true` (đường Kachi 2.96–2.98) | **0/9** (gồm 3 lượt thêm extra `autonav`/`autoplay`/`finish_on_ended=false`) | Toàn màn, hết bài nút ↻ + "More videos", `state=1` đứng yên. Chế độ một video BÁM task: `playFromUri` / VIEW thường đè lên sau đó vẫn dừng (1/1 mỗi cách) |
| VIEW `watch?v=` trơn (không extra) | **6/16** | Hàng "kế tiếp" (bit `ACTION_SKIP_TO_NEXT` 0x20 của phiên) chỉ có ở một phần lượt; không có ⇒ dừng sau bài. Mở YouTube trước rồi VIEW (giống ô mở app) 1/4 — không đỡ |
| VIEW `watch?v=<id>&list=RD<id>` (Mix YouTube tự dựng theo chính bài) | **7/7** (gồm 2 lượt tua NGAY khi phát như `finish`, 2 lượt kèm `force_fullscreen`) | Có 0x20 ngay khi phát; bài đầu = đúng bài; hết bài sang bài Mix kế. Kèm `&list=` thì YouTube BỎ QUA `force_fullscreen` (trang xem + bảng Mix) |
| `playFromUri(watch?v=…)` trên phiên thường | 1/1 | Phiên có sẵn — ít gặp trên xe |
| `playFromUri(watch?v=…&list=RD…)` | 0/1 | `playFromUri` BỎ hàng chờ ⇒ link có `&list=` phải đi K4-VIEW |
| Mở app, bấm bài trong kết quả tìm (người dùng thường) | 3/3 (+1 video ngắn) | Mốc so sánh: "như mở YouTube bình thường" |
| `skipToNext` khi đã dừng ở chế độ một video | 1/2 | Chỉ khi phiên có 0x20; không có ⇒ YouTube vào màn lỗi (`state=7`) — không dùng |

Đọc thêm: dò chuỗi extra trong `classes.dex` của YouTube 21.35 (jadx) ⇒ bảng extra của VIEW chỉ có `finish_on_ended`,
`force_fullscreen`, `playlist_uri`, `query`, `REFERRER…` — không có cờ bật tự chuyển bài trong chế độ toàn màn.

## 3. Gốc lỗi

- **[ĐO máy ảo] cơ chế**: `force_fullscreen` = chế độ MỘT VIDEO của YouTube ⇒ hết bài dừng. Đường phát tiếp 2.96–2.98 luôn gửi
  extra này khi giao link bằng K4-VIEW.
- **[ĐO máy ảo] phụ**: kể cả bỏ extra, VIEW `watch?v=` trơn từ ngoài app chỉ đôi khi có hàng kế tiếp (6/16).
- **[SUY] quy kết cho xe owner**: triệu chứng *"hát đúng 1 bài xong nghỉ"* khớp đúng đường K4-VIEW + extra (YouTube nguội trong ô
  ⇒ không phiên ⇒ VIEW). Chốt: sổ chuyến (Cài đặt › nhật ký `KachiTrip`) lượt đó có `view(` (không phải `uri=`); bản 2.99 thêm
  dấu `cont:` khi link đã đổi sang khuôn liên tục.

## 4. Sửa (2.99 R1)

- `VoiceAppTargets` YouTube: `watchFullscreenExtra = null` (bỏ chế độ một video) + `watchContinue = watch?v={q}&list=RD{q}` (dữ liệu
  bảng, không nhánh tên gói).
- `TripMusicPlan.continuousUrl(target, url)`: mọi link YouTube của bước nhạc (phát tiếp + ô *Phát gì*) ⇒ khuôn liên tục, CÙNG id.
  `safeWatchUrl` nhận thêm đúng `&list=RD<cùng id>` (backreference). `TripMusicPlan.play`: link có hàng chờ ⇒ luôn K4-VIEW.
- YT Music không đổi (tự phát tiếp — `resumable`; chưa đo Mix). Giọng nói *"phát bài X"* không đổi (ngoài phạm vi R1).
- Đánh đổi: hết toàn màn của 2.96 R10 (YouTube bỏ qua extra khi có `&list=`). Chọn lại toàn màn = owner quyết (đổi lại MỘT dòng
  bảng, mất tự chuyển bài).

## 4b. "Có cả hai không?" — Mix + toàn màn tự động (đo lượt 2, 10/10 tối)

Owner hỏi *"ko có cả 2 à?"*; owner [ĐO owner]: mở kiểu Mix rồi **tự bấm** nút toàn màn ⇒ vẫn hát, vẫn qua bài. Câu hỏi: Kachi có
TỰ bấm được (không toạ độ, chung cho mọi xe) không. Công cụ: app thăm dò ngoài repo (NotificationListener + AccessibilityService
cùng cờ `flagRetrieveInteractiveWindows|flagReportViewIds` như `nav_accessibility_config.xml` + một `VirtualDisplay` cờ `8|256` cỡ ô
1104×790 như `VdAppHost`), đã gỡ khỏi máy ảo sau đo. "Màn ảo" dưới đây = màn ảo của app thăm dò (cầu kiểm thử Kachi đang tắt), cùng cờ.
Bố cục YouTube trong màn ảo đọc bằng `dumpsys activity top` (cây view: `watch_panel`/`watch_player` + khung).

| # | Cách | Lượt | Sang bài kế | Toàn màn | Ghi chú |
|---|---|---|---|---|---|
| 1a | A11y tìm `com.google.android.youtube:id/fullscreen_button` khi đang phát (display 0) | 25 lần dò / 10 s | — | — | **Không có trong cây** khi thanh điều khiển ẩn (chỉ có lúc vừa chạm hoặc đang dừng). Mô tả nút = *"Enter fullscreen"* |
| 1b | Hiện thanh điều khiển bằng a11y: `ACTION_CLICK` lên `player_overlays` / `ACTION_ACCESSIBILITY_FOCUS` / `ACTION_FOCUS` | 1+2+1 | — | ✗ | `player_overlays` không có action CLICK (chỉ 1,4,8,64,…); FOCUS/A11Y_FOCUS trả `false` |
| 1c | Khi thanh điều khiển ĐANG hiện (đang dừng): `ACTION_CLICK` lên `fullscreen_button` | 3 (+1 với YouTube trong `packageNames`) | — | ✗ 0/4 | `performAction` trả **false**; nút Play/Pause cũng false; nút *Repeat* ở bảng Mix thì **true** ⇒ YouTube cố ý không nhận cú bấm a11y trên lớp điều khiển player |
| 1d | A11y nhìn YouTube trong màn ảo (A10) | 7 lượt | — | — | `getWindowsOnAllDisplays` = `NoSuchMethodException` (API 30); `getWindows()` chỉ thấy display 0 (2 cửa sổ: systemui + …) ⇒ YouTube trong ô **vô hình** với a11y trên A10 |
| 2 | Mix trước, chờ phát, rồi VIEW `watch?v=<id>` + `force_fullscreen` vào CÙNG task (display 0) | 8 | **7/8** | ✓ 8/8 | Bài kế KHÔNG phải bài 2 của Mix (Mix luôn ra *SKY DECADE*; ở đây mỗi lượt một bài khác — Hà Anh Tuấn, Mỹ Tâm…) ⇒ hàng Mix bị THAY bằng tự-phát-đề-xuất; 1/8 dừng (bit 0x20 không có); VIEW lần 2 phát lại từ 0 |
| 2v | Như 2, trong màn ảo | 4 | **0/4** | ✓ (player 1104×790) | Hết bài là dừng (`state=1`) |
| 3a | `input keyevent KEYCODE_F` (display 0) | 1 | — | ✗ | Không đổi bố cục |
| 3b | VIEW `watch?v=` + `force_fullscreen` + `--es playlist_uri` (`…/playlist?list=RD<id>` hoặc `RD<id>`) | 2 | **0/2** | ✓ | `playlist_uri` có trong bảng extra (`iyk.java`) nhưng không tạo hàng chờ |
| 3c | API trình phát rời (`StandalonePlayerActivity` / `START_PLAYLIST`) | `dumpsys package` | — | — | Không còn trong YouTube 21.35 |
| ref | Mix trơn (đường 2.99) trong màn ảo | 3 | **3/3** (đúng bài 2 của Mix) | ✗ (trang xem + bảng Mix) | Chốt dòng [SUY] cũ ở §5: Mix trong ô vẫn sang bài kế |

Không đo: chạm giả (double-tap / `input tap` / `dispatchGesture`) — là toạ độ; trên A10 `dispatchGesture` chỉ display 0
(`GestureDescription.setDisplayId` từ API 30 [SUY theo mức API]); tầng chạm ô là việc của agent khác (không đụng).

**Kết luận [ĐO máy ảo]**: KHÔNG có cách tự động đáng tin để có cả hai. A11y bị chặn ở 3 tầng (nút không có trong cây khi đang phát ·
YouTube từ chối `ACTION_CLICK` trên điều khiển player · a11y A10 không thấy màn ảo ô); VIEW lần hai giữ toàn màn nhưng bỏ hàng Mix và
trong ô thì 0/4. ⇒ **Giữ 2.99 R1 như đã làm (Mix, không toàn màn)**; người lái muốn toàn màn thì tự chạm nút toàn màn — owner đã
đo: hàng Mix còn nguyên. Không đổi `nav_accessibility_config.xml` (không thêm `packageNames`, không thêm cờ).

## 5. Chưa biết / cần xe

- ~~[SUY] K4-VIEW Mix vào màn ảo ô ở lại ô~~ → [ĐO máy ảo §4b] Mix trong màn ảo cờ `8|256` sang bài kế 3/3 (bài 2 của Mix).
- [CHƯA BIẾT] YouTube trên xe (bản/tài khoản owner) cư xử như máy ảo. 🚗: lên xe, để bài cũ chạy hết ⇒ sang bài kế; sổ có `cont:`.
