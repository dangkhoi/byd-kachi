# kachi-301 · stage 7 · TAPFIX → chạm trên hình xe thuộc ĐÚNG chấm (bảng Đèn + bảng Kính) — off-car, chưa commit

Owner 13/10 (máy ảo, 3.1 vehicleTest): (1) *"thử nhấn mấy cái nút trên emulator thấy nhảy loạn, nhấn nút này nó xanh nút
khác"* · (2) *"widget kính cũng bị vậy luôn đó, click lên xa trên top, tức là nó không đúng vùng, chứ ko chỉ là vùng chọn rộng
đâu"*.

## Gốc [ĐO]
- Bảng Đèn — `CarIconBoardView.kt` (R24) `plan()`/`onLayout()` gọi `CarIconBoard.zones(acts…)` CHỈ với 3 điểm có nút
  (`headl`·`drl`·`readl`), chia k-d nở tới mép khung. Chép thuật toán sang Python (công cụ ngoài repo) với hình học ô Đèn thật
  267×329 px, điểm 30 px: vùng `drl` = (0,0)–(153,103), `headl` = (153,0)–(267,103), `readl` = (0,103)–(267,329) trên cả Seal
  lẫn ảnh mặc định ⇒ tâm MỌI điểm chỉ-xem rơi vào vùng một đèn có nút:

  | điểm chỉ-xem | Seal ⇒ lệnh | mặc định ⇒ lệnh |
  |---|---|---|
  | đèn cốt (trước-trái) | `drl` | `drl` |
  | sương mù trước (mũi) | `drl` | `drl` |
  | xi-nhan trái / phải (gương) | `readl` / `readl` | `readl` / `readl` |
  | đèn hông (hậu trái) | `readl` | `readl` |
  | sương mù sau (đuôi) | `readl` | `readl` |

  Khớp [ĐO logcat owner 18:44]: ~20 chạm chỉ ra `drl`/`headl`/`readl`.
- Bảng Kính — `ActionMatrixCompact.onLayout` dùng `GroupActionMatrix.zones` (phần tư trái/phải × trước/sau + dải cốp đuôi) theo
  neo CHUNG `CarLayout.part`, mỗi vùng nở tới MÉP Ô ⇒ vùng kính trước bắt đầu từ y = 0 của ô: chạm sát mép trên (xa mọi chấm)
  vẫn mở kính trước. Không phải lệch hệ toạ độ (bảng + lớp vẽ cùng gốc `(0,0)`), mà vùng không gắn với chỗ chấm VẼ
  (`CarDots.center(p, art)` — neo đo theo ảnh, khác neo chung).
- Thứ tự/chỉ số (yêu cầu 3): bản cũ không trộn chỉ số (zip `acts` ↔ vùng đúng thứ tự; lạc quan theo mã nút `ControlTileState`).
  Bản mới bỏ hẳn chỉ số: nút trợ năng + nảy/lắc khoá theo CHÍNH ô (`IconTile`), cú chạm giữ đối tượng ô, không vị trí.
- Bảng Cửa 2D (`DoorBoardView`) — [ĐO đọc mã] chỉ vẽ, KHÔNG có vùng chạm nào ⇒ không thuộc họ lỗi này.

## Sửa (generic, hai bảng, mọi ảnh CarArt)
- `:core` mới `SpotTouch.kt`: `owner(points, x, y, reachPx)` = chấm có TÂM gần nhất nếu ≤ tầm, xa hơn ⇒ `null` (không ai) ·
  `reachPx(touch)` = 0,75 × 48 dp (bán kính 36 dp — đích rộng 72 dp; 60 dp phía trên chấm ⇒ không ai) · `ownSquare` = ô vuông
  không chồng (Chebyshev) cho nút trợ năng · `fitCenter` = MỘT phép đặt khung ảnh cho lớp vẽ + bài kiểm.
- `CarIconBoard.zones` GỠ; `GroupActionMatrix.zones`/`band` GỠ (không còn ai gọi — §8).
- `CarIconBoardView`: bảng tự nhận chạm (`onInterceptTouchEvent = onCar`), chủ bắt lúc NHẤN, bắn khi THẢ trên cùng chủ; tâm =
  `CarIconBoard.px` trên CÙNG `layer.frame` lớp vẽ dùng. Điểm chỉ-xem: KHÔNG lệnh, lắc 2 dp 240 ms + `announceForAccessibility`
  "<tên> · chỉ xem". Nút trợ năng cho MỌI ô (cả chỉ-xem), `contentDescription` "Sương mù trước · Tắt · chỉ xem" / "Đèn pha ·
  chạm: bật".
- `ActionMatrixCompact`: cùng luật trên tâm `CarLevelsLayer.centerOf(p)` (hàm lớp vẽ dùng để vẽ chấm); chạm rơi vào nút chân
  ⇒ để nút thật xử lý.
- `StateDotPainter.drawIcon(…, tappable)`: chỉ-xem = đĩa 72 %, KHÔNG vòng, icon mờ 60 % (bật vẫn icon nhấn + 1 lớp quầng);
  có nút = vòng + icon như cũ.
- Chuỗi `klight_view_only` ×5 (vi "chỉ xem" · en "view only" · zh "仅查看" · th "ดูอย่างเดียว" · ms "lihat sahaja").
- Nhật ký `KachiCtl`: `tap-board centres …` (tâm vẽ theo toạ độ màn, mỗi lần đặt lại khung) · `tap-board lights|windows (x,y) → <id | chỉ xem … | không ai>`.

## Test [ĐO XML, gradle-locked.sh]
- `:core` tập trung (`*CarIconBoard* *CarDot* *GroupActionMatrix* *CarArt* *Trunk* *SourcePathHygiene* *LangCoverage*
  *LayeringRules* *IconBoard* *Light* *Widget* *GroupTile*`): **166 / 0**.
- `:app` tập trung (`*CarIconBoard* *GroupActionMatrix* *ActionMatrix* *LightCarBoard* *CarDot* *CarArt* *TrunkTap* *GroupTile*
  *ZeroBorder* *LauncherI18n* *LayeringRules* *SourcePathHygiene* *LangCoverage* *IconBoard* *Widget* *StateDot* *IconStyle*
  *ControlTile*`): **234 / 7 đỏ / 1 bỏ qua**. CẢ 7 đỏ thuộc việc 3D đang làm của agent khác (`Car3dWidgetView.kt` chuỗi viết cứng,
  `Car3dTaps.kt` nhãn gốc, khoá `kachi_car3d_title`, `w_car3d` không đổ tại chỗ, `GroupTileWiringContractTest` tileFiles,
  `OemCarArtSourceSetContractTest` ảnh `oem-3d/…/car_control.png`) — không tệp nào của lượt này. Bài của lượt này: LightCarBoard
  4/0 · ActionMatrixWiring 9/0 · TrunkTapGate 4/0 · CarDotWiring 4/0 · ZeroBorder 5/0 · LayeringRules 9/0.
- Mới: `CarIconBoardTouchTest` (7 — hình học ô Đèn thật 267×329 + 267×312, cả Seal + mặc định: tâm mỗi điểm về chính nó; tâm
  ±6 px của MỌI điểm chỉ-xem không bao giờ ra lệnh; điểm 40 %/60 % giữa sương mù ↔ ban ngày về điểm gần hơn; quét lưới 3 px =
  đúng Voronoi, ngoài tầm ⇒ `null`; 60 dp trên điểm cao nhất ⇒ `null`; ô trợ năng không chồng) · `CarDotTouchTest` (bảng Kính:
  2 ảnh × 4 cỡ ô — 2×3 owner, cao, vuông, 6×2 cốp xuống chân — tâm chấm về chính nó, 60 dp trên chấm cao nhất ⇒ không ai, mép
  trên ô ⇒ không ai). Sửa có trích owner: `CarIconBoardTest` (bỏ bài `zones`), `GroupActionMatrixCompactTest` (bỏ 3 bài khoá vùng
  phần tư/dải đã gỡ), `LightCarBoardContractTest` (+1 bài), `ActionMatrixWiringContractTest` (khoá `SpotTouch.owner` +
  `levels.centerOf` thay `GroupActionMatrix.zones`).
- Thử ĐỎ: (a) bỏ tầm (`bestD <= reachPx`) ⇒ 4/8 đỏ; (b) "điểm đầu tiên trong tầm" thay "gần nhất" ⇒ 4/8 đỏ; khôi phục ⇒ xanh.
- Call site §8: `SpotTouch.owner(` 2 bảng · `ownSquare(` 2 · `reachPx(` 2 · `fitCenter(` 1 (`CarLevelsLayer.fit`) ·
  `centerOf(` vẽ + chạm + trợ năng · `logCentres(` 2 · `R.string.klight_view_only` 2 · `drawIcon(… tappable` 1 (+ `Car3dDotsLayer`
  của agent 3D đã gọi đúng chữ ký `scale, tappable`).

## Máy ảo emulator-5556 [ĐO logcat `KachiCtl`]
`:app:assembleVehicleTest` 3.1 (205) — CHƯA bump (orchestrator), APK 83 MB (đã gồm tài nguyên 3D của agent kia). Bố cục QA2:
ô 0 lớn = Đèn, ô 1 = Chuyến đi, ô 2 = Kính (2×3). Tâm vẽ (toạ độ màn) từ dòng `tap-board centres`:

| Chạm (màn) | Điểm | Kết quả |
|---|---|---|
| (709,225) | đèn cốt — chỉ xem | `→ chỉ xem ic-car-front-lowbeam`, KHÔNG lệnh |
| (897,228) | đèn pha | `headl [1]` |
| (802,182) | sương mù trước — chỉ xem | chỉ xem, KHÔNG lệnh |
| (802,865) | sương mù sau — chỉ xem | chỉ xem, KHÔNG lệnh |
| (660,422) / (944,422) | xi-nhan T / P — chỉ xem | chỉ xem, KHÔNG lệnh |
| (679,810) | đèn hông — chỉ xem | chỉ xem, KHÔNG lệnh |
| (802,283) | đèn ban ngày | `drl setDayTimeLightState [1]` |
| (802,475) | đèn đọc | `readl feature:0x4f50003a [2]` |
| (802,152) | mép trên ô Đèn (30 px trên sương mù) | chỉ xem sương mù, KHÔNG lệnh |
| (620,600) | khoảng trống thân xe | `→ không ai`, KHÔNG lệnh |
| (1712,673) | kính trước-trái | `win_half_lf [1,4]` |
| (1786,673) | kính trước-phải | `win_half_rf [2,4]` |
| (1712,722) / (1786,722) | kính sau trái / phải | `win_half_lr [3,4]` / `win_half_rr [4,4]` |
| (1749,798) | cốp | cổng chạm: `speed=null gear=null` ⇒ `MỞ bị chặn` (đúng luật fail-safe khi số giả không chạy) |
| (1712,583) | 60 dp trên kính trước-trái | `→ không ai`, KHÔNG lệnh |
| (1650,575) | sát mép trên ô Kính | `→ không ai`, KHÔNG lệnh |

⚠ Sự cố trong lượt (đã trả): điểm thử (1749,560) rơi ra NGOÀI bảng, trúng nút đầu ô "Đổi ứng dụng ô 3" ⇒ mở hộp chọn; hai cú
chạm thử lại kế đó chọn trong hộp ⇒ ô 0 trống + ô 2 = Maps [ĐO logcat `KachiWorkspace [slot-empty]` 19:09:11]. ĐÃ TRẢ lúc app
dừng: `QA2__slot_0` → `widget:g_lights`, `QA2__slot_2` → `widget:g_windows` (đúng trạng thái đo 19:08:15, `tap-board centres`
lại y hệt); bản trước sửa giữ ở thư mục nháp. Bài học: điểm "phía trên" phải tính trong khung bảng, không lấy toạ độ ngoài ô.
⚠ `/data` máy ảo: APK lớn hơn 10 MB ⇒ cài phải hạ ngưỡng tạm 20 MB (đã `delete`, `get` = null). Mô hình sherpa NGOÀI
(`/sdcard/…/files/sherpa`) đã trả, md5 khớp; bản TRONG (`files/sherpa`, 68 MB) **CHƯA trả — /data chỉ còn 64 MB**. Sao lưu
nguyên ở `sherpa-bak/inner.tar` (thư mục nháp). Cần: khởi động lại máy ảo / tăng phân vùng rồi
`cat inner.tar | adb exec-in run-as com.byd.launcher tar xf -`. Giọng nói trên máy ảo có thể không chạy tới khi trả.

[ĐO sub-agent đọc ảnh sau sửa] ô Đèn: 3 điểm có nút (pha, ban ngày, đèn đọc) = to hơn, có vòng (đứt nét = chưa đọc trên máy ảo),
icon sáng hơn; 6 điểm chỉ-xem = đĩa nhỏ, không vòng, icon mờ — "phân biệt rõ"; không chồng, không cắt mép. Ô Kính: chấm kính
trước-trái nửa đầy màu nhấn (đích lạc quan 50 % vừa gửi — đúng), còn lại vòng đứt nét chưa đọc. Ảnh chỉ ở thư mục nháp.

## Còn mở
- 🚗 [CHƯA ĐO] trên xe: tầm 36 dp có vừa tay khi xe chạy không (owner nhìn) · BYD Seal thật.
- Ô Đèn/Kính cỡ khác (3D, 6×2) chưa thử trên máy ảo — chỉ `:core`.
- Spec `kachi-301` §Tasks/§Design + backlog chưa ghi TAPFIX (một chủ spec — orchestrator).
