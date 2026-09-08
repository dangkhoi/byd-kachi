# VietMap "dark for many stretches" — chẩn gốc trên emulator + fix keep-alive a11y (F4b)

> **Trạng thái**: Current · **Ngày**: 2026-08-24 · **Mục đích**: Ghi bằng chứng gốc (đo trên emulator) của lỗi "VietMap tắt đen nhiều đoạn" và fix keep-alive a11y đã triển khai + test.

## Bằng chứng gốc [ĐO] (emulator-5554, Android 14, 1920×1080@240 = màn chính xe)

VietMap dẫn thật trên emulator (a11y content-desc: `10m Phố Huế`, `21:58 1d1h45p 1640km Cơ quan (COBI TOWER 1)`). Bắt log ClusterNav:

- **a11y VietMap phơi cự-ly + đường + ETA dạng CHỮ, KHÔNG có từ hướng rẽ** — hướng chỉ là ảnh mũi tên (glyph). Đã xác nhận bằng ảnh sạch: mũi tên rẽ trái ở (48,72)-(142,168), trắng-trên-tối, vành tối 4 phía — thoả mọi cổng `NavGlyphLocator`.
- **`NavOutputOwner.tick` chỉ đưa khung vào phễu khi `ScreenCaptureSignal.arrow` (glyph) còn tươi** (`plan.pushArrow`). Dữ liệu a11y (cự-ly/đường qua `NavViewIdSource`) CHỈ được đọc BÊN TRONG nhánh pushArrow.
- Khi glyph hết tươi (>`STALE_MS`=6s) → `plan.clear` → `issueClear` → `stopSession` → cụm tắt đen, **DÙ a11y cự-ly/đường vẫn tươi** (VietMap vẫn đang dẫn). Đây là gốc "dark for many stretches" + flicker: glyph VietMap phân loại được vài maneuver rồi gap >6s → xoá sạch.
- Ghi chú: [ĐO 2026-08-24, sửa nhận định trước] với **CHỈ VietMap foreground sạch** (force-stop GMaps/Waze), glyph VietMap **classify NGON trên emulator LIVE**: `arrow(glyph) name=maneuver_turn_normal_left/right amap=2/3` → `cluster-nav icon= seg=113→74→50 road='Hàm Long'/'Triệu Việt Vương'` (chỉ `InstrumentDevice null` = ghi HAL off-car). Lần trước glyph "không phân loại" là vì **GMaps/Waze chen màn** lúc capture, KHÔNG phải artifact captureFission. Locator dò được mũi tên ~nửa số nhịp (nhịp trượt rơi FIXED_CALIBRATED→im); freshness 6s bắc cầu các gap ngắn, keep-alive bắc cầu gap dài CÓ a11y cự-ly. Chốt bằng probe off-car trên ảnh VietMap thật: `NavGlyphLocator.locate=(41,85,125,172)` → `classifyWazeInk='maneuver_turn_normal_left' amap=2`.

## Fix — keep-alive a11y (không phụ thuộc glyph fragile)

Khi `plan.clear` định bật (mũi tên + làn + camera đều stale) NHƯNG `NavViewIdSource.freshReadingFor(activeFramePkg, now)` còn tươi và đã có HƯỚNG-LẦN-CUỐI cho gói đó → đưa vào phễu một khung keep-alive: hướng-lần-cuối + cự-ly/đường a11y tươi, thay vì nhả phiên (tắt đen).

**Cổng an toàn "im lặng > sai hướng"** (`NavOutputOwner.tryKeepAlive`):
1. Có `activeFramePkg` + `lastManeuver` (đã từng hiện khung có hướng cho gói này).
2. `freshReadingFor` != null (a11y còn đọc được — app đang dẫn).
3. **BASELINE + cự-ly-giảm-đơn-điệu**: `lastShownSeg >= 0` (đã từng HIỆN một cự-ly hợp lệ) VÀ `reading.turnMeters <= lastShownSeg` (đang tiến tới CÙNG khúc rẽ). Thiếu baseline hoặc cự-ly TĂNG (qua khúc rẽ → khúc MỚI, hướng chưa xác nhận) ⇒ im lặng (nhả phiên). ⇒ TUYỆT ĐỐI không hiện hướng cũ cho khúc rẽ mới.

## Files
- `core/.../navigation/NavContentBuilder.kt` — thêm `fromKeepAlive` (thuần).
- `app/.../NavOutputOwner.kt` — state `lastManeuver`/`lastShownSeg`; lưu ở nhánh pushArrow; `tryKeepAlive`; nối vào tick; reset ở `issueClear`.
- Test: `NavContentBuilderTest` (4 ca giá trị), `NavOutputOwnerTest` (4 ca gồm mutation-proof cự-ly-tăng + baseline).

## Verify [ĐO]
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test --rerun-tasks --continue` ⇒ **2154 pass / 0 fail / 0 error** (5 module, đếm từ `build/test-results/**/*.xml`).
- Mutation-check: gỡ chặn cự-ly-giảm → test `cu-ly TANG` ĐỎ; khôi phục → xanh.
- Senior review độc lập: APPROVED, vá 1 [P1] (lỗ baseline `lastShownSeg=-1`), [P2]/[P3] ghi nhận không chặn.
- APK: `ClusterNav2.0-v1.17-vietmap-keepalive-a11y-20260824.apk` (versionCode 18, sha256 `cd18568255d61bf3a2cc1c9e4995930cf17017d116aa0ae2056d1d5dd13a20d1`).

## CHƯA làm / hướng tiếp
- **Waze detection [ĐO 2026-08-24]: CHẠY E2E TRÊN EMULATOR** (owner tap Go để vào nav). Log ClusterNav khi Waze dẫn: `arrow(glyph) pkg=com.waze name=maneuver_turn_slight_left amap=4 → turn_normal_left amap=2` (glyph classify OK) · `viewid-nav pkg=com.waze dist=410→330m road='Lê Thái Tổ'/'Hàng Tre' eta='22:28'/'22:24' left=26000m/2520s` (a11y đọc đủ) · `cluster-nav icon=3/1 seg=360→330 road='Hàng Tre'` (ra khung cụm). Chỉ `InstrumentDevice null` (ghi HAL off-car). Trước đó cũng đã probe off-car 3 fixture Waze thật → classify `turn_right/left amap 3/2`. ⇒ code nhận diện Waze **KHÔNG hỏng**; a11y Waze còn GIÀU hơn VietMap (có ETA + quãng/thời-gian còn lại) nên keep-alive ăn tốt. "Waze never appears" trên xe chuyến trước = **đặc thù phiên** (bị chen/layout/cast-state), không phải bug code.
- **Nút thắt tự-động-hoá (không phải bug)**: không tự lái Waze vào nav trên emulator bằng ADB được (render GL, a11y thưa/đổi, nút Go không tap tự động, deep-link chỉ mở start-state). Cần 1 tap "Go" thủ công trên emulator để vào nav — sau đó tự động hết.
- [P2] keep-alive so cự-ly THÔ (không dung sai) — cân nhắc thêm dung sai tăng nhỏ khi có log xe.

## F4c — LIGHT/DARK MODE (owner chỉ 2026-08-24, "cả light mode dark mode cả 2 app")

**Gốc [ĐO] — nghi phạm mạnh nhất cho "Waze never appears / VietMap dark ban ngày":** `NavGlyphLocator` CHỈ dò **mực SÁNG trên nền TỐI** (dark/night theme). Xác nhận off-car: đảo màu ảnh VietMap dark thật (đang classify được `turn_left amap=2`) = mô phỏng light/day theme → `NavGlyphLocator.locate` trả **null**. App dẫn ở day theme (mũi tên tối/nền sáng) ⇒ không classify ⇒ HUD không lên. KDoc locator cũng tự thú ("App theme SÁNG không được phủ"). Cả phiên test dark mode nên không thấy — owner chỉ 1 câu là ra.

**Fix — dò CẢ HAI CỰC (F4c):**
- `PixelFrameOps.invert` (đảo màu chuẩn hoá light↔dark).
- `NavGlyphLocator.locateAny` — dò cực sáng-trên-tối (dark, `locate` thẳng) TRƯỚC; null thì **nếu nền ROI thực sự sáng** (`isLightBanner`, mean luma > `LIGHT_BG_MEAN`=128) mới đảo màu locate lại → `Located(rect, inverted=true)`. Caller (`ScreenCaptureNavSource.handleArrowByGlyph`) đảo màu CROP trước khi `classifyWazeInk` khi `inverted`.
- **Guard chống dương-tính-giả (an toàn "im lặng > sai hướng"):** `isLightBanner` chặn việc đảo màu ở ca "dark mode giữa hai khúc, không mũi tên" (đảo khung tối có thể vồ đảo-sáng-giả → sai hướng). 3 lớp phòng thủ: guard mean>128 (cần >43.5% ROI sáng) · cổng hình học locator · Hamming ≤18 template.

**Verify:** dark mode `locate` KHÔNG đổi 1 dòng ⇒ không hồi quy (2158 test/0). Test `NavGlyphLocatorLightModeTest`: dark dò thẳng inverted=false; light (đảo ảnh thật) 1-cực null=bug, `locateAny` ra inverted=true CÙNG maneuver; dark-không-mũi-tên (kể cả có status bar sáng) → null (guard chặn). Senior review APPROVED (vá [P2] perf 3→1 getPixels, [P3] thêm test biên).

**⚠ CHƯA verify LIVE banner sáng thật:** [ĐO] VietMap banner **vẫn TỐI dù đồng hồ 12:00 trưa** (VietMap không đổi day theme theo đồng hồ hệ thống — banner charcoal gần đen, mũi tên trắng) ⇒ chưa ép được banner sáng trên emulator để test live. Fix mới proven OFF-CAR (đảo ảnh thật). **[CHƯA BIẾT]** VietMap/Waze trên xe ban ngày có thật sự vẽ banner SÁNG (mũi tên tối) không, hay banner luôn tối như VietMap ở đây. Nếu banner luôn tối ⇒ fix này là phòng thủ (không phải bug xe); nếu có banner sáng ⇒ đây LÀ bug "never appears ban ngày". Cần owner xác nhận màn xe ban ngày, hoặc ép được app sang day theme trên emulator.

**APK: `ClusterNav2.0-v1.18-lightdark-glyph-keepalive-20260825.apk`** (versionCode 19, sha256 `9b5a66d9e6804bde9937a47a3357404f452b6f48d8c53898d596d411eb867d26`) — gồm keep-alive (F4b) + dual-polarity (F4c).

## F4b-fix — SỐ CỰ-LY NHẢY on-car (owner báo 2026-08-25: "100-50-80-100")

**On-car [ĐO owner v1.1x]:** VietMap lên HUD OK (đường thông), nhưng số cự-ly nhảy 100→50→80→100 (tăng lại = sai).

**Truy [ĐO code]:** hai đường distance của nguồn ẢNH KHÔNG nhất quán —
- Đường thường (glyph tươi): `plausibleSegOrUnknown` → qua guard `TurnDistancePlausibility` (chặn nhảy-tăng, đóng-băng, warmup).
- Keep-alive (glyph stale, F4b bản đầu): dùng `reading.turnMeters` **THÔ, BỎ QUA guard**.
Glyph VietMap chập chờn (~nửa nhịp) ⇒ cự-ly xen kẽ giữa hai đường ⇒ nhảy. Tệ hơn: guard từ chối tăng → `issueClear` → reset `lastShownSeg=-1` → keep-alive nhịp sau nhận lại số bất kỳ. `VietMapDescParser` KHÔNG phải thủ phạm (neo `^`, lấy đúng dòng cự-ly tức thời, bỏ "Sau đó (Xm)"). F4c (light/dark) KHÔNG đụng cự-ly.

**Fix (v1.19):** keep-alive đi qua **CÙNG guard** với đường thường (`plausibleSegOrUnknown`) — cự-ly luôn được làm mượt + state guard (anchor/streak) KHÔNG lệch trong lúc keep-alive; cả hai nhánh track `lastShownSeg` = cự-ly THÔ (danh tính khúc rẽ). Guard từ chối/warmup ⇒ blank ô cự-ly (giữ mũi tên) — im lặng > số nhảy. Test 2158/0.

**⚠ CHƯA chốt 100%:** không reproduce được số nhảy trên emulator (VietMap mất route + đứt kết nối sau đổi giờ test light-mode). Fix nhắm đúng nghi phạm (keep-alive-thô) NHƯNG root có thể còn ở: (a) cự-ly a11y VietMap tự nhảy (banner "Sau đó" đổi khúc / GPS), (b) guard nhận tăng khi tên đường chớp đổi. Cần **log xe** (`seg guard raw=X out=Y` + `cluster-nav seg=Z`) để thấy cự-ly THÔ có nhảy không + version đã test (v1.16 chưa-keepalive hay v1.17 có-keepalive).

**APK: `ClusterNav2.0-v1.19-distguard-fix-20260825.apk`** (versionCode 20, sha256 `806303c8c36b671f4408941c93b940af0ba9129aa193d48266173f1273926d0a`).
