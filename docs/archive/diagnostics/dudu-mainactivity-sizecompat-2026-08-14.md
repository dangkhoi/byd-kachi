# ClusterNav MainActivity dưới dudu: dư viền đen + scale (size-compat) · 2026-08-14

> Xe **anh em** (không phải xe owner) cài **dudu** (launcher tuỳ biến IVI). Mở ClusterNav trên màn chính →
> app **không full khung dudu, dư đen, bị scale**. Owner gửi ảnh. Chưa repro được off-car.

## Triệu chứng
- ClusterNav `MainActivity` mở trên **màn chính** dưới **dudu** → letterbox (viền đen) + bị scale, không lấp đầy khung.

## Điều tra code (off-car, 2026-08-14)
- `activity_main.xml`: root `ScrollView match_parent×match_parent` + `fillViewport`, dùng dp/sp → **layout co giãn tốt, KHÔNG phải nguyên nhân**.
- `themes.xml` `AppTheme` = `@android:style/Theme.Material.Light` (không dialog, không cứng size) → không phải theme.
- **Manifest gap (nghi phạm chính):** `MainActivity` THIẾU `resizeableActivity` + `configChanges` + `screenOrientation`, trong khi sibling `ClusterNavActivity` (chiếu cụm) CÓ đủ cả 3.
- Dự án tự ghi nhận cơ chế size-compat: `app/.../modules/clustercast/ClusterDiag.kt:30` ("app có rơi vào size-compat không → framework ĐÓNG BĂNG densityDpi"), `ClusterCast.kt:997`. **Size-compat = letterbox + scale** → khớp triệu chứng.
- Giả thuyết: dudu (launcher tuỳ biến, thường đổi DPI / chạy app trong khung riêng) đẩy `MainActivity` vào **size-compat mode** → dư đen + scale.

## Fix đã áp (A — 2026-08-14, off-car, low-risk, mirror ClusterNavActivity)
`app/src/main/AndroidManifest.xml` — `MainActivity` thêm:
```xml
android:screenOrientation="landscape"
android:resizeableActivity="true"
android:configChanges="orientation|screenSize|screenLayout|density|smallestScreenSize|keyboardHidden"
```
Chưa commit. Build JVM verify sau khi sửa (xem báo cáo phiên).

## CẦN VERIFY on-car (B — chờ trả lời từ xe anh em, dự kiến hôm sau)
1. Mở **ClusterNav → Chẩn đoán**: còn báo **size-compat = yes** không? **density (DPI)** = bao nhiêu?
2. Mở MainActivity từ **launcher gốc BYD** (không qua dudu): **full khung** không? (cô lập dudu-specific vs mọi launcher.)
3. Bạn ấy có **đổi DPI/độ phân giải** trong dudu? Xe **model gì** (khác Seal → khác resolution)?

### Rủi ro cần theo dõi
- `screenOrientation="landscape"`: nếu dudu chạy app trong khung **KHÔNG landscape** → có thể letterbox theo chiều khác. Nếu B cho thấy vậy → **bỏ screenOrientation**, chỉ giữ `resizeableActivity` + `configChanges`.

## So sánh v1.04 (git, 2026-08-14)
- **Manifest:** `MainActivity` ở v1.04 (`d85b9f2`) **y hệt hiện tại** — không có `resizeableActivity`/`configChanges`/`screenOrientation`. Lịch sử `resizeableActivity` chỉ ở initial-release + 1 refactor, **chưa từng trên MainActivity**. ⇒ **KHÔNG phải regression** — size-compat dưới dudu là lỗi tiềm ẩn lâu nay; fix A là **hardening mới**. (Nên KHÔNG thể dùng "v1.04 chạy full dưới dudu" làm bằng chứng — vẫn cần B xác nhận fix ăn.)
- **Icons (liên quan Track B):** v1.04 = commit giới thiệu neutral Maneuver → narrow làn cụm còn 11 mã (mất tunnel 16 / merge / service…). Pre-v1.04 làn cụm truyền raw AMAP đầy đủ (theo comment Maneuver.kt) ⇒ tunnel bị bỏ **tại v1.04**; Track B khôi phục đúng cái đó.

## ⚠️ CẬP NHẬT (owner: anh em nói v1.04 KHÔNG bị, bản hiện tại BỊ → là REGRESSION thật)

Tức fix manifest ở trên có thể chỉ là **mitigation**, chưa chắc gốc. So sánh v1.04 (`d85b9f2`) → HEAD để tìm cái gây:
- **Declarative windowing: GIỐNG HỆT** — compileSdk/targetSdk 37, minSdk 29 (y nguyên); `<application>` tag y nguyên; `AppTheme` (Material.Light) y nguyên; không có aspect/supports-screens/compatible-screens. ⇒ **regression KHÔNG ở manifest/gradle/theme** → là RUNTIME.
- **MainActivity manifest: y hệt** v1.04 (cả 2 thiếu resizeable/configChanges) → fix của mình KHÔNG phải "khôi phục v1.04".
- **`wm density/size` (cast)**: scope `-d $vd` (display cụm), **đã có từ v1.04**; không phải mới.
- **Cast-projection-on-open: BÁC** — v1.04 `MainActivityCastController.onCreate` gọi `openProjection()` **VÔ ĐIỀU KIỆN** (d85b9f2:56); HEAD **gate** `if (castEnabled) openProjection()` (mặc định OFF). ⇒ HEAD chạy cluster-setup lúc mở **ÍT hơn** v1.04 → không thể là thủ phạm (v1.04 làm nhiều hơn mà vẫn full).

### ⇒ Chưa pin được root từ so sánh tĩnh (15 version runtime changes). ĐỔI CÁCH:
Bug chỉ hiện dưới **dudu** → phải lấy ground-truth trên xe đó (không đoán tiếp từ static diff):
1. **A/B APK trên chính xe dudu đó:** cài `apk/ClusterNav-1.04-*.apk` (đã archive) vs bản hiện tại, mở MainActivity, so trực tiếp — xác nhận regression sạch + loại yếu tố cấu hình xe.
2. **Bisect:** nếu có APK trung gian (1.06/1.11/1.14/1.16) → cài lần lượt, tìm version ĐẦU TIÊN bị → chốt commit gây.
3. **Diag screen (B):** trên xe dudu, ClusterNav → Chẩn đoán: **density màn chính (d0)** dudu đặt bao nhiêu? MainActivity có **size-compat = yes**? bounds? Cast đang ON hay OFF? (nếu Cast ON → mổ tiếp path openProjection/seal-config v1.04→now; nếu OFF → root nằm chỗ khác).
4. Câu hỏi chốt cho bạn ấy: **Cluster Cast đang bật hay tắt?** + black-margin hiện **ngay khi mở app** hay **sau khi cast**?

### Fix manifest (đã áp) = giữ làm mitigation
`resizeableActivity=true` + `configChanges(density|screenSize|...)` khiến MainActivity **hấp thụ** đổi density/size tại chỗ thay vì size-compat — có thể chữa triệu chứng dù root là runtime trigger nào. Nhưng CHƯA xác nhận trúng gốc (v1.04 thiếu attrs này mà vẫn ổn → root là 1 trigger runtime chỉ bản mới chạm).

---

## Trạng thái
- Fix A applied off-car (manifest). Verify build. **Cần B để xác nhận đúng size-compat + fix có ăn không** (build + OTA lên xe anh em test, hoặc bạn ấy mở Chẩn đoán trước/sau khi cài bản mới).

---

## ✅ RESOLVED 2026-08-15 (owner confirm)
Owner: **"dudu đã OK, make done."** Bản **1.20** (MainActivity `resizeableActivity=true` + `configChanges` + `screenOrientation=landscape`) **fix được** trên xe anh em cài dudu — hết dư đen/scale, app full khung. ⇒ đây là **fix thật**, không chỉ mitigation; và đúng là **regression runtime** mà fix manifest xử được (dù root runtime chính xác không cần đào thêm vì triệu chứng đã hết). Đóng vụ dudu. B/bisect không cần nữa.
