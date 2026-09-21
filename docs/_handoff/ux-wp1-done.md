# Kachi UX Overhaul — WP1 (nền) — DONE off-car, CHỜ OWNER DUYỆT LOOK

> Spec: `docs/specs/kachi-ux-overhaul.html` (WP1 · R1). Trạng thái: **code + test xong, CHƯA commit** (theo yêu cầu).
> Mục tiêu WP1 = **nền màu + glass mới** + **ẢNH emulator để owner duyệt LOOK trước** khi build WP2-9.
> Ngày 2026-09-20. Ảnh: `docs/diagnostics/ux-overhaul-2026-09-20/` (viewport 1280×720, css scale, < 2000px).

---

## 1. Đã làm gì (đúng WP1, KHÔNG đụng WP2-9)

1. **Hệ màu**: freshen **màu nhấn bảng tối** (xanh→tím tươi hơn) + thêm **2 vai màu MÉP KÍNH** (`glassSheen`/`glassShade`) cho cả hai bảng.
2. **`KachiTheme.surface()` → glass**: BỎ viền quanh thẻ nội dung (NEUTRAL) + thêm **mép kính bevel** (vệt sáng 1px đỉnh + vệt tối 1px đáy) trên nền gradient dọc, **0 blur runtime**.
3. **`card()` / `pill()`**: mặc định **KHÔNG viền** (opt-in `stroke` khi cần).
4. **Glass 2 chế độ**: công tắc `ui_glass_real` (Cài đặt › Hiển thị, mặc định TẮT) + `KachiGlassMode` (glass THẬT = `RenderEffect.createBlurEffect`, API ≥ 31; API thấp lùi về giả).

### ⚠ QUYẾT ĐỊNH QUAN TRỌNG — thang nền GIỮ NGUYÊN
Đã thử làm sâu/tươi cả **ramp nền** (base sâu hơn, step 0.05) nhưng **hoàn nguyên** vì:
- Nền tối sâu hơn (`#0f1626`) làm **lệch tương phản màu sơn xe** ⇒ `design/car/paint.json` phải sinh lại (việc của **WP3**);
- step 0.05 làm surface sáng hơn ⇒ **`vien ket cau` + `sac linh vuc` tụt sàn WCAG** (margins bảng gốc rất mong manh).

⇒ **Ramp nền bảng tối + bảng sáng = Y NGUYÊN**. Độ "hiện đại/glass" đến từ **BỎ VIỀN + MÉP KÍNH + GRADIENT sẵn có + ACCENT TƯƠI**, KHÔNG từ đổi độ sáng nền. Đây là lựa chọn có chủ đích để giữ WCAG (text ≥ 4.5:1) và không kéo theo WP3.

---

## 2. Hex bảng màu MỚI

### Bảng TỐI (`KachiPalette.DARK`)
| Vai | Cũ | MỚI | Ghi chú |
|---|---|---|---|
| `accent` | `#4c7dff` | **`#4d86ff`** | xanh azure tươi hơn |
| `accentInk` | `#7ba0ff` | **`#8ab2ff`** | sáng hơn → contrast tốt hơn trên nền tối |
| `accent2` | `#7b5cff` | **`#8a63ff`** | tím giàu hơn |
| `accentSoft` | `#264c7dff` | **`#264d86ff`** | theo accent mới |
| `accentLine` | `#8078a0ff` | **`#808ab2ff`** | theo accent mới |
| `accentWash` | `#2e4c7dff` | **`#2e4d86ff`** | theo accent mới |
| `tileOnFrom` | `#804c7dff` | **`#804d86ff`** | ô điều khiển BẬT |
| `tileOnTo` | `#667b5cff` | **`#668a63ff`** | ô điều khiển BẬT |
| `tileOnLine` | `#b34c7dff` | **`#b34d86ff`** | viền ô BẬT |
| `glassSheen` | — | **`#4dffffff`** (MỚI) | vệt sáng 1px đỉnh thẻ (30% trắng) |
| `glassShade` | — | **`#40000000`** (MỚI) | vệt tối 1px đáy thẻ (25% đen) |

GIỮ NGUYÊN: `bg #141b30`, `gradFrom #3f6ae0`, `gradTo #6b4ce6`, `onAccent #ffffff`, `inkOnAccent #e7ecff`, mọi vai mực/nền/màu-nghĩa, toàn bộ ramp.

### Bảng SÁNG (`KachiPalette.LIGHT`)
| Vai | Cũ | MỚI | Ghi chú |
|---|---|---|---|
| `glassSheen` | — | **`#59ffffff`** (MỚI) | vệt sáng đỉnh (rất nhẹ trên thẻ trắng) |
| `glassShade` | — | **`#14000000`** (MỚI) | vệt tối đáy 8% (chiều sâu nhẹ) |

GIỮ NGUYÊN: toàn bộ accent (`#2f5ae0`/`#5b3ee0`…), mực, nền, ramp — bảng sáng modernize hoàn toàn bằng **bỏ viền + mép kính** (không đổi một mã nền nào).

---

## 3. Glass GIẢ vs THẬT

### GIẢ (mặc định — `ui_glass_real = false`, MỌI máy) — `KachiTheme.surface()`
`LayerDrawable`:
1. **nền**: `GradientDrawable(TOP_BOTTOM, [surfFrom, surfTo])` — chuyển sắc dọc (đỉnh sáng → đáy tối) = chiều sáng của tấm kính. **NEUTRAL không có viền quanh thẻ.**
2. (nếu có `domain`) một lớp sắc lĩnh vực rất nhạt.
3. **mép kính**: `setLayerGravity(TOP)+setLayerHeight(hair=1px)` màu `glassSheen` (vệt sáng đỉnh) + `Gravity.BOTTOM` 1px `glassShade` (vệt tối đáy) ⇒ **bevel** đọc ra là "tấm kính".
- **0 blur / 0 bóng đổ / 0 elevation** (GPU đầu máy TRINKET yếu — bài canh `SurfaceMaterialContractTest`).
- WELL (khay ô làm việc) giữ mép `LINE_STRONG`, ACTIVE (đang bật) giữ mép `ACCENT_LINE` — hai mép **chức năng**, không phải khung trang trí; và bảng tối cần mép khay để "ba bậc" đọc ra được.

### THẬT (tuỳ chọn — `ui_glass_real = true` **và** API ≥ 31) — `KachiGlassMode.applyBackdrop()`
- `RenderEffect.createBlurEffect(22dp, 22dp, CLAMP)` áp lên **`WallView`** (lớp nền) ⇒ làm mờ nền/hình-nền phía sau các thẻ trong mờ = kính thật.
- API < 31 ⇒ không làm gì (lùi về giả). Công tắc ở **Cài đặt › Hiển thị › "Kính thật (làm mờ nền)"** (khoá `ui_glass_real`, theo XE, `clusternav_prefs`, qua `ClusterNavBridge.setGlassReal`). Áp ở **lượt dựng màn kế tiếp**.
- `KachiGlassMode.kt` là tệp DUY NHẤT trong `launcher/` được phép `RenderEffect` (đã khai miễn trừ ở `SurfaceMaterialContractTest`).

### ⚠⚠ PHÁT HIỆN QUAN TRỌNG CHO OWNER — glass THẬT **VÔ NGHĨA trên xe**
`RenderEffect` cần **API ≥ 31 (Android 12)**. [ĐO] xe DiLink là **API 29 (Android 10)** ⇒ glass THẬT **luôn lùi về glass GIẢ trên xe**. Công tắc vẫn tồn tại đúng spec (owner muốn "đo CPU/fps/lag cả hai trên xe"), nhưng thực tế **không có gì để đo** vì nó không chạy được ở đó. Đề nghị owner cân nhắc: **glass GIẢ là con đường DUY NHẤT khả dụng trên phần cứng thật.**

---

## 4. Ảnh emulator (docs/diagnostics/ux-overhaul-2026-09-20/)

| Ảnh | Mô tả LOOK |
|---|---|
| `before-home.png` | **TRƯỚC (cũ "nhà quê")** — bảng SÁNG: mọi thẻ + ô + tile có **viền xám 1px** (kiểu form ô vuông), nền xám bệch, phẳng, không chiều sâu. Đúng cái owner chê "30 năm trước". |
| `after-home-dark.png` | **SAU · bảng TỐI** — thẻ **KHÔNG viền**, nổi lên bằng gradient kính trên nền indigo sâu; ô điều khiển BẬT (Lock/unlock, Air purifier) mang gradient **xanh-tím tươi**; sạch, "floating glass". Hết cảm giác form ô vuông. |
| `after-settings-dark.png` | **SAU · Cài đặt TỐI (nhóm Màn hình chính)** — chip pill bo tròn KHÔNG viền, chip chọn = accent tươi; rail nhóm chọn có mép kính accent nhẹ; panel sạch. |
| `after-settings-display-dark.png` | **SAU · Cài đặt › Hiển thị TỐI** — chip đơn vị + chip **Sáng/Tối/Tự động** + công tắc **"Real glass (blur the backdrop)"** MỚI hiện đủ (bằng chứng toggle wired). |
| `after-home-light.png` | **SAU · bảng SÁNG** — thẻ trắng kính **KHÔNG viền**, tách nền bằng sắc độ + mép nhẹ; ô BẬT mang sắc **oải hương** nhạt; airy, sạch, giống iOS. **Cải thiện rõ rệt** so với `before-home`. |
| `after-settings-display-light.png` | **SAU · Cài đặt › Hiển thị SÁNG** — chip xanh tươi, chip "Light" chọn, công tắc **Real glass** trong bảng sáng. |

**Đánh giá "còn nhà quê không": KHÔNG.** Cái làm nó "nhà quê" là **viền bao mọi thẻ + nền phẳng** — cả hai đã hết. Cả hai bảng nay đọc ra là "panel kính nổi", accent tươi.

**Real glass (API 34):** đã dựng + chạy KHÔNG crash với `ui_glass_real=true`, nhưng **KHÔNG chụp được hình mờ**: hiệu ứng chỉ thấy được khi có **hình nền** (làm mờ nền trơn/glow là vô hình), mà emulator API-34 quét/vẽ hình-nền chập chờn (scoped-storage + launch focus nhảy về Pixel launcher). Vì glass-thật **moot trên xe API 29**, không đầu tư thêm; toggle đã chứng minh functional qua ảnh Cài đặt + test.

---

## 5. Test — [ĐO]

- **`./gradlew :core:test :app:testDebugUnitTest --rerun-tasks`**: 4 bài contrast/surface (`ThemePaletteContractTest`, `SurfaceContrastContractTest`, `SurfaceMaterialContractTest`, `ColorChoiceContractTest`) + toàn bộ core+app **XANH**.
- **`./gradlew test --rerun-tasks --continue`**: **5 module, 73 task, BUILD SUCCESSFUL, 0 đỏ.**
- WCAG giữ nguyên: text ≥ 4.5:1, viền kết cấu ≥ 3:1 (bảng đo sinh máy `docs/diagnostics/visual-refresh-2026-09-16/contrast-table.md` chạy lại vẫn PASS).
- Bài canh đã **cập nhật theo hợp đồng mới** (giữ WCAG): `SurfaceMaterialContractTest` + `SurfaceContrastContractTest` nay **cho phép** mép kính 1px (đảo Pass-5) nhưng vẫn CẤM lớp dày (`hair * 2`+); `LangCoverageTest` 75→76 entry + 286→287 nhãn EN; +`display_glass_real` vào catalog/control/bridge-wiring/ProfileScope.

---

## 6. Files đã sửa/thêm

**Sửa:** `app/.../KachiPalette.kt` (accent + glass roles) · `KachiPaletteSeeds.kt` (comment, ramp giữ nguyên) · `KachiTheme.kt` (surface glass + getters) · `KachiThemeDrawables.kt` (card/pill bỏ viền) · `WallView.kt` (applyBackdrop) · `Prefs.kt` · `ClusterNavBridge.kt` · `SettingsSections.kt` · `res/values{,-en}/strings_kachi.xml` · `core/.../SettingsCatalogEntries.kt` · `core/.../ProfileScope.kt` · 4 test (`SurfaceMaterialContractTest`, `SurfaceContrastContractTest`, `ClusterNavBridgeWiringContractTest`, `SettingsCatalogControlContractTest`, `LangCoverageTest`).
**Thêm:** `app/.../KachiGlassMode.kt`.

Ràng buộc giữ: `:core` thuần 0 android.*; trần 500 dòng; tên vai màu giữ nguyên (21 tệp không đổi call-site); KHÔNG đụng WP2-9.

---

## 7. Còn tồn / cần owner quyết

1. **Duyệt LOOK** 6 ảnh trên (đây là mục tiêu WP1). Nếu OK → build WP2-9.
2. **Mép kính 1px**: đưa lại vệt sáng đỉnh mà **Pass-5 từng gỡ** vì owner chê "gạch trên top". Nay là **1px + bán trong suốt + đi cặp sheen/shade trên nền gradient** (khác "gạch đặc trên nền phẳng"). Nếu owner nhìn ảnh vẫn thấy "kỳ" → gỡ ở pha sau (rẻ).
3. **Palette nền giữ nguyên độ sáng** (chỉ đổi accent + mép kính): nếu owner muốn nền tối/sáng KHÁC HẲN (mood mới) → cần **sinh lại `design/car/paint.json`** (gen-car.py) + tinh chỉnh lại margins WCAG = một pha riêng (đụng WP3 hình xe).
4. **Glass THẬT moot trên xe API 29** (xem §3) — owner quyết có giữ công tắc hay bỏ.
5. **WP3 (board widget)**: các ô lốp/cửa vẫn tự vẽ nét Canvas riêng — chưa đụng ở WP1.
6. Emulator: `clusternav10` (API29) + `clusternav` (API34) đang cài **bản DEBUG** của Kachi (đã gỡ bản release cũ trên API29 để cài debug); là emulator dev, không ảnh hưởng xe.

## 8. Sai lệch spec CÓ CHỦ ĐÍCH (ghi rõ để owner biết)

- **"shadow tính-sẵn rẻ" (spec R1.3) → làm bằng BEVEL, không phải drop-shadow.** Dự án có bài canh CỨNG
  `SurfaceMaterialContractTest` **cấm** `setShadowLayer`/`elevation`/`RenderEffect` trong tầng vẽ (GPU đầu máy
  TRINKET yếu — mỗi cái bắt vẽ thêm một lượt off-screen/khung). Nên chiều sâu "kính" làm bằng **mép sáng đỉnh +
  mép tối đáy** (bevel, 0 chi phí GPU) thay cho bóng đổ. Đây là cách duy nhất giữ đúng ràng buộc 0-blur/0-shadow
  của dự án; nếu owner muốn bóng đổ THẬT thì phải nới bài canh đó (và đo lại fps trên xe) — một pha riêng.
- **"trong mờ" (translucent):** thẻ mặc định (KHÔNG hình nền) là **gradient ĐỤC** (đúng kiến trúc sẵn có); độ
  trong mờ chỉ kích hoạt khi có **hình nền** (đường `surf*OverArt` + `KachiGlass` sẵn có, 80% alpha). Trên nền
  trơn, gradient đục nhìn y hệt trong-mờ; nên không đổi (đổi sẽ phá phép đo tương phản `surfFrom` trên `bg`).
