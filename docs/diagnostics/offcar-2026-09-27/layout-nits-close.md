# L5 · Bố cục — đóng ba mục owner còn mở (thanh trên · khối lốp · ảnh xe user)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 · **Mục đích**: đóng R11 của spec `kachi-276-closing` — (1) bốn nút
> thanh trên cùng một hộp, (2) OQ7 khối 4 thẻ lốp tâm = tâm ảnh xe, (3) ảnh xe người dùng giải mã theo đường **fit**
> như ảnh mặc định. Số đo trước/sau trên máy ảo, test khoá ở `:core` + `:app`, một quyết định trái với con số trong
> spec (34 dp thay vì 40 dp) có bằng chứng + đường đổi một hằng nếu owner muốn ngược lại.

Làn L5 · bản **2.76 (177)** · nhánh `feat/voice-hotword-phrases` · máy ảo AVD `clusternav10` (`emulator-5554`,
1920×1080 @240 dpi = **1,5 px/dp**) · trước = bản 2.74 (175) đang cài trên máy ảo (hình học thanh trên/lốp **không đổi**
giữa 2.74 và 2.75 — `KachiTopStrip`/`TyreBoardView` không có commit nào ở 2.75), sau = APK `vehicleTest` dựng từ
worktree HEAD `90cfe11` + đúng các tệp của làn này.

---

## 1. Bối cảnh + số đo TRƯỚC

| Mục | Số đo TRƯỚC [ĐO máy ảo 13:04–13:13, `uiautomator dump` + PIL] | Nguồn lệch |
|---|---|---|
| (1) Thanh trên | Nói `[1605,30]–[1665,81]` **60×51** · Ứng dụng **60×51** · Cài đặt **60×51** · Hồ sơ `[1821,30]–[1872,81]` **51×51**; khe 12/12/12 px | pill là `WRAP_CONTENT` ⇒ bề ngang = hình gốc **24 dp** (`ic_grid.xml`/`ic_mic.xml` `android:width="24dp"`) + 2×`HEADER_BTN_PAD` (8) = **40 dp**; bề cao bị thanh (`HEADER_H` = 42, lề 4+4) kẹp về 34. Chip hồ sơ khai `minimumWidth/Height = 34` và nội dung 30 ⇒ 34 |
| (2) Khối lốp | ô lốp `[42,143]–[1878,553]`; thẻ TP `y 210–308`, SP `y 419–516` (cột x=1600) ⇒ tâm khối **363,0**; ảnh xe cột x=960 `y 173–530` ⇒ tâm **351,5** ⇒ lệch **+11,5 px** xuống dưới | neo bánh `CarLayout.wheel` y = 0,26/0,80 ⇒ tâm dải 0,53 ≠ 0,50; `TyreBoardView` đặt tâm thẻ = neo bánh ⇒ khối thẻ thấp hơn tâm ảnh `0,03 × Hc` (Hc = 385,4 ⇒ 11,6 px — khớp số đo) |
| (3) Ảnh xe user | `CarImageStore.loadFeathered` → `WallpaperStore.loadScaled` (**cover**, `scaledWidth` lấy `maxOf`) cho ảnh trong `Android/data/<gói>/files/car/`; ảnh MẶC ĐỊNH đã đi `decodePlan` (**fit**) từ closeout 25/09 | [ĐO số học] ảnh điện thoại 3000×4000 vào khung lốp 210×554: cover giải mã **415×553** (229 495 px) — fit chỉ cần **210×280** (58 800 px) ⇒ **3,9×** pixel không bao giờ được vẽ (letterbox `CarImageLayer.fitRect` lấy `minOf`); ảnh ngang 4000×3000 ⇒ **12,3×** |

Ảnh chụp (scratchpad phiên, không vào repo): `l5/before-home.png` · `l5/before-tyre.png` (có hộp thoại OTA *"Có bản
mới: v2.75"* đè giữa màn — cột đo x=300/1600/960 chọn ngoài vùng hộp thoại; cột x=960 của ảnh TRƯỚC bị hộp thoại che
từ y=420 nên tâm ảnh xe lấy từ ảnh SAU — cùng ô, cùng ảnh, `CarImageView` không đổi) · `l5/after-tyre.png` ·
`l5/after-home.png` + hai `uiautomator` dump tương ứng.

---

## 2. Cơ chế + bản vá

### (1) Bốn nút thanh trên — `KachiTopStrip.pillLp` (`app/.../launcher/KachiTopStrip.kt`)

`LinearLayout.LayoutParams(WRAP, WRAP)` → `LayoutParams(dp(Bars.HEADER_BTN), dp(Bars.HEADER_BTN))`, vẫn
`marginStart = Sp.S`. Dùng chung cho cả bốn (`lpFor` nhánh `else`), không nhánh riêng cho PROFILE. Hộp hình của pill
nay đúng `34 − 2×8 = 18 dp` như KDoc `KachiBars.HEADER_BTN_PAD` hứa (hình vẽ vẫn 18 dp — trước cũng 18 vì `FIT_CENTER`
lấy chiều nhỏ; chỉ **nền pill** co 40→34). KDoc điểm 3 của `KachiBars.HEADER_BTN` (*"bề ngang thực tế lớn hơn 34"*)
cập nhật theo (tệp không thuộc làn nào — sửa 2 dòng KDoc, không đổi hằng).

### (2) Khối 4 thẻ lốp — `CarLayout.tyreCardShift` / `tyreCardCenterY` (`:core`) ← `TyreBoardView.onDraw`

`tyreCardShift = 0,5 − (y_trước + y_sau)/2 = −0,03` **suy từ neo**, không gõ số. Thẻ đặt tâm tại
`imageTop + (wheel.y + shift) × imageHeight`; **chấm cảnh báo vẫn ở neo bánh thật** (`wheelY`). Mép NGANG của thẻ không
đổi (`CellTextLayout.cardSpanX` kẹp theo khung ảnh — UX2 giữ nguyên). Không dịch neo (bảng cửa + xe mini dùng chung),
không dịch khung ảnh (`CAR_TOP/CAR_BOTTOM` chung với bảng cửa, `CompositeWidgetLayoutContractTest` canh).

### (3) Ảnh xe user — `CarImageStore.loadUserScaled` + `decodeOptions` (`app/.../launcher/CarImageStore.kt`)

`loadFeathered`: `imagePath(ctx)?.let { WallpaperStore.loadScaled(...) }` → `loadUserScaled(it, reqW, reqH)` — hai
lượt `BitmapFactory.decodeFile` (lượt đầu chỉ kích thước) rồi `decodeOptions(decodePlan(...))`. `decodeOptions(plan)`
là chỗ DUY NHẤT dịch `DecodePlan` → `inSampleSize`/`inScaled`/`inDensity`/`inTargetDensity`; `loadDefaultScaled` cũng
đi qua nó (trước có bản chép tại chỗ). Bậc 1 (`WallpaperStore.sampleSize`, luỹ thừa 2) vẫn dùng chung, không chép.
`WallpaperStore.loadScaled` **không đổi** (hình nền vẫn cover, đúng câu hỏi của nó).

---

## 3. Quyết định

| # | Quyết định | Mức | Bằng chứng / lý do |
|---|---|---|---|
| Q1 | **Bốn nút = 34 × 34 dp** (chip hồ sơ giữ nguyên, ba pill co về hộp), **KHÔNG** nâng cả bốn lên 40 dp như R11 ghi | [ĐO] + quyết định owner cũ | Tiền đề của R11 (*"ba pill 40 dp"*) đo ra **40 × 34** — 40 là bề NGANG do hình gốc 24 dp gây ra, không phải bề cao. Nâng cả bốn lên 40 cao = kéo `HEADER_H` 42 → 48 dp = **86 %** thay vì **75 %** owner chốt 2026-09-20 (*"header cao 75 % / nút 70 %"*, `BarOrderWiringContractTest.hai moc phan tram cua WP5 dung nhu owner chot` ghim 42/34/22). 34 × 34 thoả cả ba ý của R11 (đồng hộp · avatar tròn · nhịp 12 px) mà không đảo một số nào owner đã duyệt. Muốn 40: đổi đúng **một hằng** `KachiBars.HEADER_BTN = 40` (thanh tự lên 48, `HEADER_AVATAR` nên lên 26 để giữ tỉ lệ đĩa/nút 0,65) — chờ owner, không tự làm |
| Q2 | Dịch **thẻ**, không dịch neo / không dịch khung ảnh | [SUY] từ ràng buộc mã | Neo dùng chung 3 bảng; `CAR_BOTTOM` chung với bảng cửa (contract test); chấm cảnh báo nói *"bánh nào"* nên ở bánh thật; thẻ chỉ cần *nằm cạnh* (16 px ở ô thật ≪ cao thẻ 98 px) |
| Q3 | Fit cho ảnh user, cover giữ cho hình nền | [ĐO số học] | Hai bề mặt hỏi hai câu (letterbox vs phủ-kín-rồi-cắt); một `decodeOptions` cho cả hai nguồn ảnh xe để không lệch một cờ |
| Q4 | Không đổi `WallpaperStore` | — | Không cần: phần dùng chung (bậc 1) đã là hàm public |

---

## 4. Số đo SAU [ĐO máy ảo 13:15, bản 2.76 (177) `versionCode=177` đọc từ `dumpsys package`]

| Mục | SAU | Mong đợi | Verdict |
|---|---|---|---|
| Nói · Ứng dụng · Cài đặt · Hồ sơ | `[1632,30]–[1683,81]` · `[1695,30]–[1746,81]` · `[1758,30]–[1809,81]` · `[1821,30]–[1872,81]` — **cả bốn 51 × 51 px** | 51 × 51 (34 dp) | ✅ |
| Khe giữa bốn nút | 1683→1695 = **12** · 1746→1758 = **12** · 1809→1821 = **12** px | 12 px = `Sp.S` | ✅ nhịp |
| UX1 đĩa hồ sơ | đĩa `[1830,39]–[1863,72]` 33×33, tâm (1846,5 · 55,5) = tâm nút (1846,5 · 55,5) ⇒ lệch **0,0 / 0,0** | ≤ 1 px | ✅ không hồi quy |
| Khối 4 thẻ lốp | TP `y 199–296` · SP `y 407–504` ⇒ tâm khối **351,5**; ảnh xe x=960 `y 173–530` ⇒ tâm **351,5** ⇒ lệch **0,0 px** (trước +11,5) | ± 1 px | ✅ |
| Thẻ không đè xe | thẻ trái `x 79–853`, thẻ phải `x 1066–1840`; thân xe tại hàng giữa `x 855–1031` ⇒ khe 2 px / 35 px | không giao | ✅ UX2 giữ |
| Ảnh xe user | không có ảnh user trên máy ảo (thư mục `car/` trống) ⇒ đường mới **chưa chạy thật** trên máy ảo; kích bitmap khoá bằng test số học (§5) | — | [CHƯA BIẾT] trên thiết bị — xem 🚗 |

---

## 5. Test (đếm từ XML `build/test-results`, worktree cách ly vì `:core` cây chính đang đỏ do tệp làn L3 `VoiceAppPrefix.kt`)

Lệnh: `gradle-locked-wt.sh :core:test --tests '*CarLayout*' --tests '*TyreCard*' --tests '*CellTextLayout*' --tests
'*TopStrip*' --tests '*TyreBoard*' :app:testDebugUnitTest --tests '*TopStrip*' --tests '*Tyre*' --tests '*CarImage*'
--tests '*BarOrder*' --tests '*CompositeWidget*' --tests '*Goi2*' --tests '*Wallpaper*'`

| Module | Bài | Kết quả |
|---|---|---|
| `:core` | `TyreCardBlockTest` (**mới**, 4 ca) · `CarLayoutTest` (+1 = 5) · `CellTextLayoutTest` 20 · `TopStripTest` 35 · `TopStripStateIconTest` 10 · `TopStripMigrationTest` 10 · `TyreBoardTest` 22 · `GroupBoardTest` 1 | **107 / 0 fail / 0 error** |
| `:app` | `TopStripSurfaceContractTest` (+1 = 10) · `CarImageStoreTest` (+1 = 15) · `CarImageLayerContractTest` (sửa 1 = 12) · `BarOrderWiringContractTest` 10 · `CompositeWidgetLayoutContractTest` 8 · `Goi2FeatureWiringContractTest` 17 · `TopStripWiringContractTest` 16 · `WallpaperWiringContractTest` 16 · `WallpaperScaleTest` 4 · `CarImageLoadRetryTest` 4 | **112 / 0 / 0** |

Bài khoá gì:
- `TyreCardBlockTest` — trên lưới ô THẬT (mọi preset `WorkspaceLayout` × 3 chiều cao workspace + ô lốp máy ảo 1836×410 + 4 tỉ lệ khác) × 3 tỉ lệ ảnh: tâm khối = tâm ảnh ± 1 px · thẻ trọn trong ô · thẻ không giao khung ảnh, mép ngang không đổi · **cổng phủ định** công thức cũ lệch đúng `0,03·Hc` (11,6 ± 0,6 px ở ô máy ảo — khớp 11,5 đo).
- `CarLayoutTest.khoi the lop dich…` — ghim `tyreCardShift = −0,03` và neo bánh KHÔNG đổi (0,26/0,80).
- `TopStripSurfaceContractTest.bon nut thanh tren cung mot hop…` — `pillLp` khai `LayoutParams(dp(HEADER_BTN), dp(HEADER_BTN))`, không `WRAP`; cổng phủ định `24 + 2×8 = 40 > 34` (= 60 px đã đo); UX1 `HEADER_AVATAR + 2×XS ≤ HEADER_BTN`.
- `CarImageStoreTest.anh NGUOI DUNG - cover vs fit…` — 3000×4000 → cover 415×553 / fit 210×280 (3,9×); 1080×1920 → 311×553 / 210×373; 4000×3000 → 738×554 / 210×158.
- `CarImageLayerContractTest.anh mac dinh VA anh nguoi dung deu giai ma theo decodePlan…` — cả hai loader qua `decodeOptions(decodePlan(`, kho không còn `WallpaperStore.loadScaled`, vẫn dùng chung `sampleSize`.

`wc -l`: `KachiTopStrip.kt` 500 · `CarImageStore.kt` 448 · `TyreBoardView.kt` 287 · `CarLayout.kt` 98 — đều ≤ 500. Call site (§8): `tyreCardCenterY` ← `TyreBoardView.kt:166`; `loadUserScaled` ← `CarImageStore.kt:289`; `decodeOptions` ← 2 loader.

---

## 6. 🚗 Kiểm trên xe (mỗi mục một dòng, runbook 2.76)

- 🚗 Thanh trên: bốn nút phải (mic · ứng dụng · cài đặt · hồ sơ) là **bốn hình tròn cùng cỡ**, chữ cái hồ sơ đúng giữa — PASS nếu nhìn không thấy nút nào rộng hơn nút nào.
- 🚗 Bảng lốp (bố cục 2 hàng như owner): khối 4 thẻ **cân giữa** thân xe (xe không "nhô lên"); thẻ không đè gương; chấm đỏ (nếu có bánh non) vẫn nằm trên bánh.
- 🚗 Ảnh xe user: thả một ảnh chụp điện thoại (≥ 3000 px) vào `Android/data/com.byd.launcher/files/car/` ⇒ bảng lốp/cửa/xe mini hiện ảnh đó, không tràn, không mờ; `dumpsys meminfo com.byd.launcher` phần Graphics/Bitmap không tăng theo cỡ ảnh gốc.

---

## 7. Nợ còn lại

- Q1: nếu owner muốn nút to hơn (40 dp) ⇒ đổi `KachiBars.HEADER_BTN` (+ `HEADER_AVATAR` 26) và chụp lại; test `hai moc phan tram…` sẽ đỏ đúng chỗ và nói ra bộ số mới.
- Máy ảo đang cài **2.76 (177) dựng từ worktree HEAD + làn L5** (không chứa thay đổi dở của các làn khác); prefs `kachi_workspace.xml` đã trả nguyên văn (md5 `bb5a473d…`, `active_profile = Mặc định 2`); hộp thoại OTA *"v2.75"* của bản 2.74 không còn hiện.
- Ảnh máy ảo không có dữ liệu lốp thật (`—`), tâm khối đo theo mép THẺ, không theo chữ.
