# UX-OVERHAUL · WP6 — Nút nổi chiếu cụm: toggle ẩn/hiện + icon Kachi

> **Trạng thái**: DONE off-car · **Ngày**: 2026-09-20 · **Spec**: `docs/specs/kachi-ux-overhaul.html` §WP6 (R6.1 · R6.2) + §Reviewer Log
> **Mục đích**: ghi lại thứ đã làm, số đo, và ba chỗ mà cách làm "hiển nhiên" là cách SAI.
> **CHƯA commit** (đúng lời giao). Ảnh bằng chứng **untracked**, không đưa vào repo public.

---

## 1. Owner yêu cầu gì · giao được gì

| # | Yêu cầu | Trạng thái | Bằng chứng |
|---|---|---|---|
| R6.1 | Toggle ẩn/hiện nút nổi cast; ẩn thì không dựng bubble **nhưng cast vẫn bật được qua cách khác** | DONE | [ĐO] cửa sổ overlay **1 → 0** trong ≤4 s, dịch vụ vẫn `isForeground=true foregroundId=1042` |
| R6.2 | Đổi icon nút nổi thành **icon app Kachi**, nhỏ gọn | DONE | [ĐO] hộp **72×72 px = 48.0dp** (từ 52dp) · **91.7 %** điểm mực nghiêng hồng ⇒ hoa anh đào, không phải mũi tên xanh |

Ngoài scope trực tiếp nhưng **bắt buộc** (trần 500 dòng, CLAUDE.md §4.1): tách 3 khối khỏi `FloatingBubbleService`
(537 → **397** dòng). Nợ này có **TRƯỚC** lượt WP6, cùng họ với `KachiHomeActivity` 541 mà WP4/5 đã ghi nhận.

---

## 2. Thiết kế — và ba chỗ cách "hiển nhiên" là cách SAI

### 2.1 ⚠⚠ Ẩn nút nổi ≠ dừng dịch vụ (lỗi này hỏng IM LẶNG)

Phản xạ đầu là `stopService(FloatingBubbleService)` — đúng như `CastBubbleControl.apply` (công tắc bong bóng đời cũ)
đang làm. Nhưng dịch vụ nổi **không chỉ là cái cửa sổ**, nó còn là:

1. driver **DUY NHẤT** của *tự chiếu khi nổ máy* (R1 — đường Activity đã thôi bắn để hết đua `SLOT_OCCUPIED`),
2. nhịp `repinEscapedCastApps` (kéo app bị giật khỏi cụm về lại),
3. nhịp `VmOverlayPosition.applyOnOpen` (áp lại vị trí bong bóng VietMap).

Tắt một cái NÚT mà tắt luôn ba thứ đó ⇒ owner thấy *"tự chiếu tự nhiên không chạy nữa"*: không lỗi, không log,
không liên hệ nhân-quả nào nhìn ra được. Đây chính là lằn ranh owner đặt ra (*"ẩn thì không dựng bubble, nhưng cast
vẫn bật được qua cách khác"*), nên cờ gác **đúng một thứ**: cửa sổ.

### 2.2 Ba nhánh ở `:core`, không phải một `Boolean`

`BubblePresence.decide(visible, overlayGranted)` → `SHOW` · `HIDDEN` · `NEEDS_OVERLAY_PERMISSION`. Mỗi nhánh sai là
một lỗi thật:

- đã-tắt mà đi xin quyền ⇒ bung màn hệ thống *"cho phép hiển thị trên ứng dụng khác"* cho thứ vừa bị tắt;
- đã-tắt mà `stopSelf` ⇒ mất ba tính năng ở §2.1;
- muốn-hiện-mà-thiếu-quyền mà im ⇒ nút nổi **không bao giờ hiện** và không gì nói vì sao.

Thứ tự xét là **ý muốn trước, quyền sau**. Đảo lại là rơi vào ca thứ nhất.

Công tắc CHÍNH `cast_enabled` **cố ý không** là tham số của `decide`: câu trả lời của nó là *"dịch vụ có chạy
không"* (`stopSelf` ở cả hai lối vào vòng đời), không phải *"cửa sổ nào"*.

### 2.3 ⚠⚠ Áp bằng NHỊP 2 giây, KHÔNG stop-rồi-start dịch vụ

`onCreate` là nơi chạy `dispatchBootAutoStart`. Dựng lại dịch vụ ⇒ `autoStartDispatched` về false ⇒ **tự chiếu nổ
lại giữa chuyến**, tức gạt một công tắc *trình bày* lại đẩy một app lên cụm trước mặt người lái. Nhịp `refresh`
(2 s) đã có sẵn nên cái giá bằng 0 và không có cửa đó. Cầu chỉ **ghi cờ**; dịch vụ tự đọc lại.

### 2.4 Fail-safe NGƯỢC nhau, có lý do

| Hàm | Đọc hỏng ⇒ | Vì sao |
|---|---|---|
| `castEnabledNow()` | coi như **TẮT** | đừng tự ý đi giành mặt cụm trước mặt người lái |
| `bubblePresence()` | coi như **HIỆN** | đừng xoá lối vào chính của việc chiếu trên chiếc xe owner vừa bật Cast |

Hai hàm đứng cạnh nhau với hai mặc định trái dấu là thứ người sau dễ "sửa cho nhất quán" ⇒ lý do viết ngay tại chỗ.

### 2.5 Icon: dùng CHÍNH `launcher_fg`, không chép sang một `ic_*` thứ hai

- `ic_launcher.xml` (24dp) là bản **MỘT TÔNG** cho smallIcon thông báo (hệ thống tô trắng theo hợp đồng) ⇒ dùng nó
  ở đây buộc phải tự tô màu trong Kotlin = mã màu viết cứng, đúng thứ `ThemePaletteContractTest` chặn.
- Nút nổi nằm trên **pixel của app đang chiếu** (bản đồ sáng, video tối). Glyph **nhiều tông** (hồng chuyển sắc +
  nhị vàng) còn hình để nhận ra ở cả hai; mũi tên một tông `#1565C0` tan vào mọi nền xanh đậm.
- `ic_bubble_nav.xml` **xoá** + rời `IconStyleContractTest.legacy` (bài `danh sach legacy tu rua hai chieu` đòi tệp
  khai ở đó phải còn tồn tại).

### 2.6 ⚠⚠ Bản vá đầu làm nút nổi TO GẤP ĐÔI — chỉ SỐ ĐO bắt được

`launcher_fg` khai cỡ riêng **108dp** (nó là lớp trước của adaptive icon), còn `minimumWidth/Height` chỉ đặt **SÀN**
⇒ cỡ nội tại thắng. [ĐO máy ảo API 29, density 240] bản đầu ra cửa sổ **162×162 px = 108dp** — ngược hẳn yêu cầu
*"nhỏ gọn"*. Nhìn ảnh vẫn ra "một bông hoa", **không ai thấy sai**.

Vá: `adjustViewBounds = true` + `maxWidth`/`maxHeight` (`ImageView.resolveAdjustedSize` lấy
`min(nội tại, max, spec)`) ⇒ **72×72 px = 48.0dp**. Bài canh ghim đúng ba thuộc tính đó.

> **Bài học**: với overlay, `dumpsys window` là bước **bắt buộc**, không phải bước xác nhận cho vui.

Cỡ 52 → **48dp = đúng SÀN chạm**, không xuống thêm (nút này bắn một lệnh chiếu THẬT lên cụm). Ghim bằng **quan hệ**
`ICON_SIZE_DP == TOUCH_MIN_DP`, không ghim hai số rời — ghim rời thì hạ sàn về 40 mà bài vẫn xanh.

---

## 3. Tệp đổi

**MỚI**
- `core/.../modules/clustercast/simplified/BubblePresence.kt` (53) — ba nhánh thuần.
- `app/.../modules/clustercast/BubbleAutostart.kt` (154) — bộ tự-chiếu-khi-nổ-máy, **tách nguyên, không sửa bước nào**.
- `app/.../modules/clustercast/BubblePipGuard.kt` (82) — chặn/trả PiP GMaps-YouTube, tách nguyên.
- `app/.../modules/clustercast/BubbleForegroundNotice.kt` (55) — thông báo FGS, tách nguyên.
- `core/src/test/.../BubblePresenceTest.kt` (4 ca) · `app/src/test/.../BubbleVisibilityWiringContractTest.kt` (8 ca).

**SỬA**
- `SimpleCastModels.kt` (`:core`) — `SimpleCastPrefs.bubbleVisible/setBubbleVisible`.
- `SimpleCastRuntime.kt` — đọc/ghi `cast_bubble_visible` (mặc định **true**).
- `FloatingBubbleService.kt` — **537 → 397** dòng: cổng ba nhánh ở cả hai lối vào vòng đời · `syncBubbleWindow()` ·
  `hideBubble()` · gỡ ba khối đã tách.
- `BubbleRenderer.kt` — glyph `launcher_fg`, hộp 48dp, padding 0, cap `max*`+`adjustViewBounds`.
- `ClusterNavBridgeCast.kt` — `castBubbleVisible()` / `setCastBubbleVisible()`.
- `SettingsSectionsCast.kt` — hàng công tắc dưới công tắc chính.
- `SettingsCatalogEntries.kt` (mục `cast_bubble`) · `SettingsCatalogClusterNav.kt` (khoá → `simple_cast_prefs`) ·
  `ProfileScope.kt` (DEVICE + lý do) · `strings_kachi.xml` VI/EN.
- Test cập nhật: `IconStyleContractTest` (gỡ legacy) · `BubbleGestureContractTest` (glyph + cỡ) ·
  `CastUILifecycleSafetyTest` (52→48) · `OcrReviewContractTest` (PiP theo chỗ ở mới) · `LangCoverageTest` (78→79 mục,
  295→296 nhãn) · `SettingsCatalogControlContractTest` (bảng mục→control).

**XOÁ**: `app/src/main/res/drawable/ic_bubble_nav.xml`.

---

## 4. [ĐO] Bằng chứng

### Test
- `./gradlew test --rerun-tasks --continue` = **BUILD SUCCESSFUL**, đếm từ **567** tệp JUnit XML:
  **tests=5131 · failures=0 · errors=0 · skipped=0** (stage trước: 5109 ⇒ **+22**).
- **Thử phá 3/3 đỏ đúng chỗ**, hoàn nguyên kiểm `shasum -a 256` (3/3 OK):
  1. đảo thứ tự xét trong `decide` (quyền trước) ⇒ `BubblePresenceTest > an nut noi KHONG duoc di xin quyen…` ĐỎ;
  2. nhánh ẩn cũng `stopSelf` ⇒ `BubbleVisibilityWiringContractTest > nhanh an KHONG dung dich vu xuong…` ĐỎ;
  3. mặc định cờ về `false` ⇒ `… > hang Cai dat di qua cau, cau ghi dung khoa ben` ĐỎ.

### Ràng buộc steering
- `:core` **0** import `android.*`.
- **0** `setStroke` / `RenderEffect` / `BlurMask` / `elevation` / `setShadowLayer` trong mọi tệp WP6 chạm tới
  (giữ WP1). ⚠ Còn 1 hit `elevation` ở `BubbleSubmenuOverlay.kt` — tệp **không** thuộc WP6, không đụng.
- Trần 500: `FloatingBubbleService` **397** · `BubbleAutostart` 154 · `BubblePipGuard` 82 ·
  `BubbleForegroundNotice` 55 · `BubbleRenderer` 113 · `SettingsSectionsCast` 452 · `ClusterNavBridgeCast` 275.
  Có bài canh (`moi tep cua luot WP6 duoi tran 500 dong`).

### Máy ảo API 29 (`emulator-5554`, `ro.build.version.sdk=29`, 1920×1080 @ density 240)
- Hộp nút nổi: `Requested w=72 h=72` · `mFrame=[1806,504][1878,576]` ⇒ **48.0 × 48.0 dp**.
- Mực hoa thấy được: **36×34 px = 24.0 × 22.7 dp**; **91.7 %** điểm mực có `r>g` và `r>b` ⇒ hồng, tức hoa anh đào
  (mũi tên cũ là xanh, `b>r`). Mực chiếm 15.6 % hộp ở alpha nghỉ 0.35.
- Hàng Cài đặt: `✓` + `'Show the floating cast button'` + dòng phụ, nằm giữa `'Opens the cluster…'` và
  `'Split ratio'`. Nhãn ra tiếng Anh ⇒ i18n chạy.
- **Tắt công tắc** ⇒ `cast_bubble_visible=false` trên đĩa · overlay có appop `SYSTEM_ALERT_WINDOW`: **1 → 0** ·
  `ServiceRecord{…FloatingBubbleService}` vẫn `isForeground=true foregroundId=1042`.
- **Bật lại** ⇒ overlay 0 → 1 · **pid 20267 → 20267** (không dựng lại tiến trình/dịch vụ).
- Ảnh (untracked, **KHÔNG commit**): `docs/diagnostics/ux-overhaul-2026-09-20/wp6-bubble-home.png` ·
  `wp6-bubble-zoom.png` (6×) · `wp6-settings-row.png` · `wp6-bubble-hidden.png`.

---

## 5. Còn tồn

- 🚗 **cần xe**: nhìn nút hoa trên nền GMaps/VietMap thật (mực 24dp ở alpha nghỉ 0.35 có đủ thấy khi lái không) ·
  **tắt nút nổi rồi nổ máy lại ⇒ tự-chiếu vẫn phải chạy** (tính chất đắt nhất của WP6; off-car chỉ kiểm được bằng
  đọc mã + đo dịch vụ còn sống, vì mở chiếu cần cầu adb của xe) · gạt công tắc trong lúc đang chiếu chia đôi.
- ⚠ **Chưa soát độc lập** (phiên không có công cụ sinh sub-agent).
- `CastBubbleControl` nay chỉ còn dùng cho `requestOverlay` + `bind/rebind` **không có chỗ gọi** (di sản của công
  tắc bong bóng đời cũ). Cố ý KHÔNG dọn trong WP6: nó là mã ngoài phạm vi, và `optedIn`/`bubbleEnabled` của
  `CastAppCatalog` cũng cùng họ ⇒ nên dọn một lượt riêng khi rà mã chết (ứng viên cho WP7/WP8).
- Ảnh trong `docs/diagnostics/ux-overhaul-2026-09-20/` **chưa được `.gitignore`** che. Lượt commit S7 phải stage
  **tường minh theo tệp**, tuyệt đối không `git add .` / không `git add docs/diagnostics/…`.

## 6. Kế tiếp

WP7 (ẩn dev-UI giữ-qua-adb) · WP8 (purge 35 mục + ẩn cast-tile) · WP9 (giọng bé OTA .zip) → S7 finalize + bump
1.86, commit + push nhánh feat, **KHÔNG** FF main / KHÔNG OTA.
