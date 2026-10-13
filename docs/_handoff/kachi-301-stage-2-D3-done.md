# kachi-301 · Stage 2 · D3 — R11 hộp Địa chỉ + R12 hộp Lịch dẫn đường (bộ chọn giờ trong hộp) — DONE (off-car)

> Spec: `docs/specs/kachi-301-cast-steps-dialog-ia.html` R8/R9/R11/R12 · §4.3 · §4.4 (mẫu "Thêm địa chỉ", "Lịch dẫn đường").
> Dựng trên API của D1 (`docs/_handoff/kachi-301-stage-1-D1-done.md`). Không commit (cây chung).

## Files
| Tệp | Vai | Dòng |
|---|---|---|
| `core/src/main/kotlin/com/byd/clusternav/launcher/PlaceForm.kt` (MỚI) | Luật kiểm hộp địa chỉ, dựng từ đúng các mảnh của `SavedPlaces.of` (`sanitize`, `parseCoords`) + `presetIndex` (khớp chip theo `SavedPlaces.keyOf`) | 39 |
| `core/src/main/kotlin/com/byd/clusternav/launcher/automation/ScheduleFormMath.kt` (MỚI) | `TimeWheelMath` (giờ/phút quay vòng độc lập, phút bước 5 bắt lưới, `PRESETS` 06/07/08/17/18:00, trần suy từ `MAX_MIN`) + `enum DayPreset` (WEEKDAYS/WEEKEND/EVERYDAY, `matching` = tập ĐÚNG BẰNG) | 65 |
| `app/.../launcher/SettingsPlaceDialog.kt` (MỚI) | R11: chip Nhà·Công ty·Trường·Khác (Khác ⇒ ô tên tự do) · ô địa chỉ · phần gập "Nâng cao — toạ độ" · kiểm tại chỗ qua `PlaceForm.check` (ô toạ độ đỏ + câu lỗi, Nâng cao tự mở khi lỗi) · Xoá đỏ trái | 160 |
| `app/.../launcher/SettingsNavAutomationDialog.kt` | Giữ nguyên `SettingsNavAutomationFormat` (`hhmm`/`parseHhmm`/`days`/`dayLabel`/`navAppLabel`); hộp viết lại trên `KachiDialogs.form` — **0 `AlertDialog`, 0 `EditText`** | 317 |
| `app/.../launcher/TimeWheel.kt` (MỚI) | Hai ô giờ lớn Bắt đầu/Kết thúc (segmented) + cột giờ/phút ▲▼ (`Sp.TOUCH`², nhấn giữ qua `SettingsRepeatTouch.attach` — đọc, không sửa) + chip mốc nhanh | 170 |
| `app/.../launcher/KachiFormControls.kt` (MỚI — ngoài danh sách gốc, xem dưới) | chip · chip tròn · `flow` (ChipFlowLayout) · `heading` · thẻ chọn-một · hàng công tắc — cho thân hộp | 174 |
| `app/.../launcher/SettingsDialogs.kt` | `askPlace` = một dòng `SettingsPlaceDialog.ask(...)`; KDoc đoạn "hộp này không kiểm gì" thay bằng trỏ R11 | — |
| `app/src/main/res/values{,-en,-zh-rCN,-th,-ms}/strings_kachi_form301.xml` (MỚI) | 21 khoá `kform_*` × 5 | |
| `core/src/test/.../launcher/PlaceFormTest.kt` (MỚI) | 4 bài — gồm bài khớp chéo `check().ok ⇔ SavedPlaces.of() != null` trên lưới 7×5×5 đầu vào | |
| `core/src/test/.../launcher/automation/TimeWheelMathTest.kt` (MỚI) | 7 bài — quay vòng, bước 5, phút lẻ bắt lưới 1 nấc, vòng đủ về chỗ cũ, kẹp rác, mốc nhanh, chip thứ | |
| `app/src/test/.../launcher/KachiFormContractTest.kt` (MỚI) | 6 bài giao kèo nguồn (không AlertDialog / EditText / TimePicker* / NumberPicker trong hộp lịch; kiểm qua `ScheduledNavRules.of` & `PlaceForm.check`; ▲▼ nhấn giữ ≥48dp; mọi điều khiển có chỗ gọi; 0 luồng/nhịp/viền; 5 ngôn ngữ cùng khoá + cùng tham số + đều dùng) | |
| `app/src/test/.../launcher/KachiDialogContractTest.kt` (của D1) | bài `moi ham SettingsDialogs di qua khuon` — `askPlace` nay đòi `SettingsPlaceDialog.ask(` (uỷ quyền) thay vì `KachiDialogs.` trực tiếp | sửa 1 bài |

`SettingsSectionsPlaces.kt` / `SettingsSectionsAutomation.kt`: **không đổi** (chữ ký giữ nguyên ⇒ 0 chỗ gọi đổi; thông báo cũ — sổ đầy, thiếu trường, `kachi_nav_auto_bad` — giữ làm lưới cuối).

⚠ Lệch danh sách tệp: `KachiFormControls.kt` là tệp MỚI ngoài danh sách (tên duy nhất ⇒ 0 xung đột). Lý do: hai hộp
cùng cần chip/thẻ/công tắc trong thân hộp; nhét vào `TimeWheel.kt` sai vai, nhét vào hộp lịch đẩy tệp sát trần 500.

## Hành vi
- **R11**: mục MỚI ⇒ không chip nào chọn sẵn (chọn sẵn "Nhà" thì một cú Lưu vội sẽ ghi đè mục "Nhà" đang có — upsert
  theo tên). Sửa mục ⇒ chip khớp tên (không phân biệt hoa/thường, KHÔNG bỏ dấu) hoặc "Khác" + tên cũ. Lưu khoá khi
  thiếu tên/địa chỉ hoặc toạ độ hỏng; chỉ toạ độ hỏng mới hiện chữ đỏ (thiếu trường chỉ khoá nút — không đỏ ô trống
  ngay lúc mở). Khác `SavedPlaces.of` có chủ ý: `of` lặng lẽ bỏ toạ độ hỏng (đường giọng/nhập tệp), hộp thì nói ra.
- **R12**: Lưu khoá khi `ScheduledNavRules.of(CHECK_ID, …bản nháp…) == null` — đúng hàm `SettingsNavAutomationSection.edit`
  gọi lúc lưu (không chép luật); câu lỗi `kform_sched_invalid` hiện dưới bộ giờ khi khoá. Dòng phụ đầu hộp = tóm tắt
  sống (`kform_sched_summary`, đang tắt ⇒ bọc `kachi_nav_auto_row_off`), cập nhật sau mọi chạm. App chip có icon app khi
  máy cài gói (3 lần `PackageManager.getApplicationIcon` lúc mở; không cài ⇒ chỉ chữ). Thứ tự app lưu theo bảng `:core`.
- Hiệu năng: 0 luồng/nhịp/listener nền; `postDelayed` chỉ trong `SettingsRepeatTouch` lúc ngón tay đang giữ ▲▼.

## Quyết định tự chủ (gộp vào spec §4.5)
1. **Dòng phụ sống**: `KachiDialog.Handle` không có `setSubtitle` và `KachiDialog.kt` không thuộc D3 ⇒ tìm TextView dòng
   phụ theo NỘI DUNG trong cây cửa sổ hộp (`SettingsNavAutomationDialog.findText`). Không tìm thấy ⇒ dòng phụ đứng yên
   (xuống cấp thấy được, không hỏng). **Việc tiếp (chủ KachiDialog)**: thêm `Handle.subtitle(text)` rồi bỏ `findText`.
2. Chip trong hộp cao `Sp.TOUCH` (48) — khác chip trang Cài đặt (`ICON_XL` 44, owner chốt cho trang): R8 "mọi đích chạm
   ≥ 48 dp" áp cho hộp; `ControlHeightContractTest` chỉ canh 5 tệp trang, không vướng.
3. Nhãn chip địa chỉ lưu theo NGÔN NGỮ ĐANG HIỆN ("Home" khi UI tiếng Anh). [SUY] voice tiếng Việt "về nhà" sẽ không
   khớp mục "Home" — cùng hành vi cũ khi người dùng tự gõ tên; ghi Open Question cho owner nếu muốn khoá tên gốc.
4. Không emoji trên chip (mock có 🏠🏢) — giữ chữ thuần; icon đầu hộp dùng `ic_nav_g` / `ic_clock_g` có sẵn.

## Việc để lại cho chủ tệp khác
- `strings_kachi.xml`: `kachi_nav_auto_window` = "Khung giờ (HH:mm)" vẫn dùng làm nhãn mục giờ — nên bỏ "(HH:mm)"
  (không còn ô gõ). `kachi_nav_auto_bad` nhắc "đúng dạng HH:mm" — nay gần như không tới được (nút Lưu khoá trước).
- `KachiDialog.kt`: `Handle.subtitle()` (xem QĐ 1).

## Tests (đếm từ XML, cây CHUNG qua `<scratchpad>/gradle-locked.sh` — cây chung biên dịch được nên không cần worktree)
- `:app:compileDebugKotlin` — XANH.
- `:core:test` (`*PlaceForm*` `*TimeWheelMath*` `*SavedPlaces*` `*ScheduledNav*` `*NavAutomation*`): **85 tests · 0 fail · 0 skip**
  (PlaceFormTest 4/4, TimeWheelMathTest 7/7).
- `:app:testDebugUnitTest` (`*Automation*` `*Place*` `*NavAutomation*` `*KachiDialog*` `*KachiForm*` `*LauncherI18n*` `*Spacing*`
  `*TypeScale*` `*Trip*` `*GridSeam*` `*ZeroBorder*` `*SurfaceMaterial*` `*ControlHeight*` `*SettingsRepeatTouch*`):
  **158 tests · 2 fail · 0 skip** (KachiFormContractTest 6/6, KachiDialogContractTest 7/7, Automation185WiringTest 16/16).
  Hai bài đỏ KHÔNG do D3 — gốc nằm ở tệp WIP của agent khác:
  - `LauncherI18nContractTest.0 chuoi tieng Viet viet cung…` ← `voice/VoiceMusicDuck.kt:120,144` (`enqueue("hạ")`, `"trả"`, `"khôi phục"`).
  - `LauncherI18nContractTest.khong co khoa tai nguyen mo coi` ← `SettingsSectionsCastGeometry.kt` gọi `kachi_cast_frame_step`,
    `kachi_cast_geom_overflow_note`, `kachi_cast_geom_resize_failed` (khai ở `strings_kachi_geom301.xml`, không phải `strings_kachi.xml`).
  - Bài đỏ thứ ba của vòng 1 là của D3 và đã sửa đúng gốc: `tang ve khong doc nhan GOC cua core` bắt `KachiFormControls.label(` ⇒ đổi tên `heading(`.
- `grep` chỗ gọi (CLAUDE.md §8): `SettingsPlaceDialog.ask(` ← `SettingsDialogs.kt`; `TimeWheel(` ← `SettingsNavAutomationDialog.kt`;
  `PlaceForm.check(` ← `SettingsPlaceDialog.kt`; `TimeWheelMath.*`/`DayPreset` ← `TimeWheel.kt`/`SettingsNavAutomationDialog.kt`.
- Chưa đo: hình thật trên máy ảo (stage 3) — bố cục, dòng phụ sống tìm được TextView thật, nhấn giữ ▲▼, icon app.
