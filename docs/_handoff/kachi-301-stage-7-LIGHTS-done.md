# kachi-301 · stage 7 · LIGHTS → (1) cổng chạm MỞ cốp · (2) R24 Đèn trên hình xe — DONE off-car (máy ảo), chưa commit

Owner 13/10: (1) *"Chặn MỞ cốp khi xe chạy (Khuyên dùng)"* · (2) *"cái widget đèn cũng làm vậy luôn, có hình xe và chấm tròn hay
icon đèn xung quanh, đúng vị trí đèn, nhấn tắt, nhấn mở"*.

## 1. Cổng chạm cốp
- `:core` `CtlSafetyPolicy.blockedOnTap(id, arg, motion)` — CÙNG tập `REQUIRES_STATIONARY` (`trunk`) + cùng luật "chỉ chặn MỞ
  `arg > 0`" như `blockedAtSpeed` (giọng nói/phím). Khác duy nhất ca KHÔNG BIẾT: **fail-safe** — chỉ cho mở khi CHỨNG MINH xe đứng.

  | tốc độ | số | MỞ cốp |
  |---|---|---|
  | > 0 | bất kỳ | CHẶN |
  | = 0 | bất kỳ | cho |
  | không đọc được | P (`"P"` / mã thô HAL `3`) | cho |
  | không đọc được | R/N/D (`R N D 0 1 2`) | CHẶN |
  | không đọc được | không đọc / `255` | CHẶN |
  | — | — | ĐÓNG luôn cho, không tốn lượt đọc |

  Mã số: `BYDAutoGearboxDevice` `GEAR_P=3/R=1/N=0/D=2/INVALID=255` (trích sẵn ở `TelemetryRegistry` dòng `gear`). Giọng nói/phím giữ
  fail-open cũ (không thuộc quyết định này). ⚠ Hệ quả: máy ảo KHÔNG số giả ⇒ không tốc độ, không số ⇒ chạm MỞ cốp bị chặn.
- `:app` `ControlTileFactory.gated(def, arg, tile, undo, act)` bao lượt ghi của TOGGLE · COVER · SELECT (mọi đường chạm: ô thanh nút, ô
  nhóm Cửa & khoang, chấm cốp bảng Kính — chấm `performClick()` chính ô đó). Chặn ⇒ không gửi lệnh, hoàn hình lạc quan,
  `TileSafetyGate.refuse`: lắc ô (nếu hiện) + toast `kctl_stop_to_open_trunk` (5 ngôn ngữ) + `announceForAccessibility` + báo bảng
  Kính ⇒ chấm cốp LẮC 300 ms (`CarLevelsLayer.shake`). Chuyển động đọc TƯƠI trên làn ghi nền (`AppContainer.readFresh("speed")`,
  số chỉ khi không có tốc độ) + `CarStatusTap.apply` (số giả vehicleTest). Gói lệnh: không bao (bất biến cũ
  `khong goi lenh nao chua ma gate theo van toc` vẫn khoá). `DummyCarReceiver` thêm `--ei speed N --es gear X`.

## 2. R24 — Đèn trên hình xe
- `:core` `CarArt.spots` + `spot(iconKey)` (khoá vật = mã icon, px gốc, đủ hai bên) · `LightSpots.of(...)` gán đèn vào mốc đo ·
  `CarIconBoard` (`place` — ô có NÚT thiếu điểm ⇒ `null` ⇒ lưới; ô chỉ-xem thiếu điểm bị bỏ · `fits` — đĩa không chồng, khe ≥ XS ·
  `zones` — chia đệ quy ở khe rộng nhất, mỗi vùng ≥ 48 dp và chứa điểm · `kind` — số đọc / lệnh vừa gửi / chưa biết).
- Mốc đo [ĐO sub-agent trên ảnh gốc, bảng ở `docs/diagnostics/oem-car-art-2026-10-13.md §R24`]: Seal thấu kính thật ±4–8 px; ảnh
  mặc định đèn pha/hậu [SUY] ±15–25 px. Gán: cốt = đèn trước-trái · pha = trước-phải · sương mù trước = mũi · ban ngày = giữa ca-pô
  sau hai đèn · xi-nhan = gương · đèn hông = hậu trái · sương mù sau = đuôi giữa · đèn đọc = khe hai ghế trước. `headlight_feedback`
  (chế độ) không điểm.
- `:app` `CarIconBoardView` (206 dòng, `GroupBody`): `GroupBodies` đưa nhóm `GroupLayout.ICONS` vào đây; vẽ ảnh dựng sẵn
  (`CarImageLayer`, BYD nếu có) + điểm `StateDotPainter.drawIcon` (tắt = icon mờ trong vòng · bật = icon/vòng nhấn + quầng hai lớp ·
  chưa biết = vòng đứt nét); thử điểm 28 → 24 → 20 dp; không vừa ⇒ `IconBoardView` (lưới cũ) ⇒ khung ngoài lùi ô tóm tắt. Vùng chạm
  vô hình (không nền/sóng) → `kit.set(id, đảo trạng thái ĐANG THẤY)` (cùng `ActionTile.set` → `gated` → ghi HAL); ô không đặt tường
  minh ⇒ `performClick`. Phản hồi = điểm nảy 120 ms. Không chữ; `contentDescription` = `Đèn pha · chạm: bật` (`klight_tap_on/off`);
  lớp hình đọc tên + số các điểm chỉ-xem.
- Lượt máy ảo ĐẦU rơi về lưới (đo: điểm ban ngày ở mép ca-pô cách điểm sương mù mũi 30 px < 36 px; ảnh mặc định: đèn hông cách
  sương mù sau 34,8 px) ⇒ dời ban ngày ra sau (Seal x 160, mặc định y 250) + đèn hông mặc định (160, 1290) trong sai số; khoá bằng
  `CarIconBoardTest.hinh xe vua o Den that tren ca hai anh` (ô Đèn thật 267×329 px, điểm 20 dp).

## Bảng chạm [ĐO logcat `KachiCtl`, emulator-5556, số giả BẬT]
| Chạm | Trạng thái thấy | Lệnh |
|---|---|---|
| điểm Đèn ban ngày | bật (số giả `drl=true`) | `drl setDayTimeLightState [2]` (tắt) |
| điểm Đèn pha | tắt | `headl [1]` (m=none máy ảo) |
| điểm Đèn đọc | chưa biết | `readl feature:0x4f50003a [2]` (bật) → chạm lại `[1]` (tắt) |
| Đèn pha ×3 nhanh | — | `[1]` → `[0]` → `[1]` (đảo trong ân hạn 2,5 s) |
| chấm cốp, 112 km/h D | — | KHÔNG lệnh · `tap-gate motion speed=112 gear=D` · `trunk: MỞ bị chặn` · toast "Dừng xe để mở cốp" [ĐO ảnh] |
| ô cốp thanh nút, 112 km/h | — | KHÔNG lệnh · chặn (cùng log) |
| chấm cốp, 0 km/h | — | `trunk voiceCtlBackDoor [1]` (mở) |
| chấm cốp, 112 km/h, đang mở | — | `trunk voiceCtlBackDoor [3]` (ĐÓNG — luôn cho) |
| chấm cốp, 112 km/h, đã đóng | — | chặn lại |
Ghi chú: sau ân hạn, điểm theo SỐ ĐỌC — số giả ghim `drl=true`/`highBeam=false` nên chạm cách xa vẫn gửi cùng hướng (đúng: xe nói gì
hiện nấy).

## Test [ĐO XML, gradle-locked.sh]
- core tập trung (`*CtlSafety* *CtlTrunk* *CarIconBoard* *CarArt* *CarDot* *GroupActionMatrix* *SourcePathHygiene* *LangCoverage*
  *LayeringRules* *KeyCtl* *Light* *Trunk* *IconBoard* *Widget* *GroupTile*`): **202 / 0**.
- app tập trung (`*CtlSafety* *Trunk* *Light* *ActionMatrix* *GroupActionMatrix* *CarArt* *CarDot* *IconBoard* *GroupTile* *Widget*
  *ZeroBorder* *IconStyle* *LauncherI18n* *LangCoverage* *LayeringRules* *SourcePathHygiene* *KeyCtl* *ControlTile* *OemCarArt*
  *CarImage* *DummyCar*`): **276 / 0 / 1 bỏ qua** (IconStyle cần python ≥ 3.10 — như trước). Full suite CHƯA chạy.
- Mới: `CtlTrunkTapSafetyTest` (4) · `CarIconBoardTest` (5) · `TrunkTapGateContractTest` (4) · `LightCarBoardContractTest` (3).
  Sửa có trích owner: `ActionMatrixWiringContractTest` (GroupBodies → `CarIconBoardView`, lưới là dạng lùi) ·
  `GroupTileWiringContractTest` (khai `CarIconBoardView.kt` vào notBuilders).
- Thử ĐỎ: `provenStationary` fail-open ⇒ `CtlTrunkTapSafetyTest` đỏ · `place` bỏ kiểm nút ⇒ `CarIconBoardTest` đỏ · bỏ `gated(` ở
  cover ⇒ `TrunkTapGateContractTest` đỏ · `GroupBodies` về `IconBoardView` ⇒ `LightCarBoardContractTest` đỏ. Bài vừa-ô-thật đỏ THẬT ở
  lượt đầu (số đo trên). Đều khôi phục ⇒ xanh.
- Call site §8: `blockedOnTap(` 1 · `gated(def` 3 · `TileSafetyGate.refuse(` 1 · `.motion(` 1 · `.listen(` 1 · `levels.shake(` 1 ·
  `CarIconBoardView(` 1 · `CarIconBoard.place/zones/fits/kind` ≥ 1 · `drawIcon(` 1 · `.spot(` 1.

## Máy ảo emulator-5556 [ĐO]
`:app:assembleVehicleTest` 3.1 (205) — CHƯA bump (orchestrator). `/data` đầy: mỗi lần cài = rút sherpa (trong `files/sherpa` +
ngoài `/sdcard/Android/data/…/files/sherpa`, sao lưu scratchpad `sherpa-bak/`) + **tạm** `settings put global
sys_storage_threshold_max_bytes 100MB` rồi `delete` (đã trả, `get` = null) → cài → trả mô hình, md5 khớp cả 95 + 6 tệp (script
scratchpad `emu-install.sh`). ⚠ `/data` còn ~74 MB sau 3 lần cài (giảm dần — [CHƯA BIẾT] nguồn) ⇒ lượt sau nên khởi động lại máy ảo.
⚠ Sự cố trong lượt: một cú `input tap` mở hộp "Đặt widget hoặc mở app vào ô này" (nghi bấm-giữ do máy ảo ì — [CHƯA BIẾT]), cú chạm
kế chọn nhầm app *Styles & wallpapers* vào ô 0 hồ sơ QA2. ĐÃ TRẢ: `QA2__slot_0` → `widget:g_windows` (sửa prefs lúc app dừng, bản
trước sửa ở scratchpad `ws-before-fix.xml`); kiểm lại uiautomator: bảng Kính + Đèn như cũ.
Màn để lại: HOME, bố cục **Kính lớn · Chuyến đi · Đèn**, Tiếng Việt, Tối, số giả BẬT (speed 112, D).
Ảnh (có ảnh BYD) CHỈ ở scratchpad `shots/`: `r24-home-final.png`, `r24-lights-crop3x.png`, `r24-before-tap.png`, `r24-after-tap1.png`,
`trunk-blocked-toast.png`, `trunk-toast-bottom.png`, `trunk-open-at-0.png`.
[ĐO sub-agent đọc ảnh] ô Đèn: Seal đầu lên, 9 điểm, không chồng/không cắt mép; bật = vòng/icon xanh + quầng; đèn đọc = vòng đứt nét;
không chữ ngoài tiêu đề; không mảng nhấn. Nhận xét: icon trong điểm 20 dp nhỏ, khó phân biệt; vòng TẮT trắng hơi đậm; điểm mũi/đèn
trước nằm sát/ngoài mép thân — owner cần nhìn.

## Còn mở
- 🚗 [CHƯA ĐO] trên xe: `readFresh("speed")` + số trên đường chạm (tốc độ PROVEN nên ca "không biết" hiếm); firmware có tự từ chối
  `voiceCtlBackDoor(1)` khi chạy không — vẫn [CHƯA BIẾT].
- Gán một điểm cho đèn có hai bên (cốt trái/pha phải…) là quy ước, không phải vị trí duy nhất — owner duyệt nhìn.
- Đèn ở ảnh mặc định là [SUY] (không thấy thấu kính). Ô Đèn 2×3/khác cỡ chưa đo trên máy ảo (chỉ test `:core` ô 267×329).
- Spec `kachi-301` §Tasks/§Design (R24 + cổng chạm) + backlog chưa ghi (một chủ spec — orchestrator).
