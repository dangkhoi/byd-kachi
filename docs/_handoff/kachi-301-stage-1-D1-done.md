# kachi-301 · Stage 1 · D1 — `KachiDialog` + `SettingsDialogs` lên khung mới (R8, R9) — DONE (off-car)

> Spec: `docs/specs/kachi-301-cast-steps-dialog-ia.html` §3 R8/R9 · §4.3 · §4.4. Không commit (cây chung).

## Files
| Tệp | Vai | Dòng |
|---|---|---|
| `app/src/main/java/com/byd/clusternav/launcher/KachiDialog.kt` (MỚI) | Khung: `android.app.Dialog` cửa sổ trong suốt, thẻ `KachiTheme.surface(ctx, Sp.RADIUS_XXL)`, đầu/thân cuộn/chân cố định, an toàn show | 379 |
| `app/src/main/java/com/byd/clusternav/launcher/KachiDialogPatterns.kt` (MỚI) | `PickItem` + `object KachiDialogs`: 4 khuôn `pick` · `form` (+`field`/`Field`/`caption`) · `confirm` · `notice` | 284 |
| `app/src/main/java/com/byd/clusternav/launcher/SettingsDialogs.kt` | 7 hàm giữ NGUYÊN chữ ký, ruột chuyển sang `KachiDialogs` (0 `AlertDialog`) | 192 |
| `app/src/main/res/values{,-en,-zh-rCN,-th,-ms}/strings_kachi_dialog301.xml` (MỚI) | `kdlg_cancel` · `kdlg_close` · `kdlg_ok` · `kdlg_current` | 4 khoá × 5 |
| `app/src/test/java/com/byd/clusternav/launcher/KachiDialogContractTest.kt` (MỚI) | 7 bài canh | 101 |

⚠ Tiền tố khoá là `kdlg_`, KHÔNG phải `kachi_`: `LauncherI18nContractTest.khong co khoa tai nguyen mo coi` chỉ đọc
`values/strings_kachi.xml` ⇒ khoá `kachi_*` ở tệp khác sẽ đỏ "mã gọi khoá không có". Muốn gộp vào `strings_kachi.xml`
thì đổi tên khoá cùng lúc (owner của tệp đó).

## API (`internal`, package `com.byd.clusternav.launcher`)

### Khung — `KachiDialog(context)` (fluent, mọi hàm trả `this`)
```kotlin
typealias DialogAction = KachiDialog.Handle.() -> Unit
enum class Tone { ACCENT, DESTRUCTIVE }
fun title(text: CharSequence); fun subtitle(text: CharSequence?)
fun icon(res: Int)            // vector drawable, tô ON_ACCENT trên ô gradient (DESTRUCTIVE: RED trên RED_SOFT)
fun glyph(text: String?)      // ô icon là ký tự (chữ cái đầu, "!")
fun tone(t: Tone)             // DESTRUCTIVE ⇒ ô icon đỏ + nút chính kiểu nguy hiểm
fun body(view: View)          // thêm vào thân CUỘN (khe Sp.S); thân trần MAX_H_FRACTION = 0.86 màn
fun message(text: CharSequence)
fun primary(label, autoDismiss = true, run: DialogAction)   // phải cùng, gradient + ON_ACCENT
fun cancel(label: CharSequence? = null, run: DialogAction? = null) // mặc định CÓ, nhãn kdlg_cancel
fun noCancel()
fun danger(label, run: DialogAction)  // trái, RED_SOFT + RED
fun link(label, run: DialogAction)    // trái, chữ ACCENT_INK không nền ("Quản lý hồ sơ…")
fun cancelable(on: Boolean)
fun validate(check: () -> Boolean)    // chạy lúc mở + sau mỗi lần chữ đổi ở MỌI EditText trong thân ⇒ bật/khoá nút chính
fun focus(edit: EditText, showKeyboard = false)
fun onDismiss(run: () -> Unit)
fun show(): Handle?                   // null = không mở (Activity finishing/destroyed, BadTokenException)
inner class Handle { val isShowing; fun dismiss(); fun setPrimaryEnabled(on: Boolean); fun revalidate(): Boolean }
companion: activityOf(ctx), messageView(ctx, text), stackLp(ctx, top), MAX_H_FRACTION, DIM = 0.55f, DISABLED_ALPHA = 0.45f
```
Chân: `[danger][link] … [cancel][primary]`, mọi nút `minHeight = Sp.TOUCH`, `minWidth = Sp.CHIP_MIN_W`. Bề rộng
`min(Sp.NOTE_MAX_W=600dp, màn − 2·Sp.XXL)`. Theme cửa sổ `Theme_Material(_Light)_Dialog_NoActionBar` theo
`KachiTheme.night`; nền cửa sổ trong suốt; dim bằng `FLAG_DIM_BEHIND` (hệ thống vẽ). `SOFT_INPUT_ADJUST_RESIZE` +
`STATE_HIDDEN` (hoặc `STATE_VISIBLE` khi `focus(…, showKeyboard = true)`). 0 luồng/nhịp/listener nền.

### Bốn khuôn — `KachiDialogs`
```kotlin
data class PickItem(label, subtitle = null, current = false, icon = 0, drawable: Drawable? = null, initial: String? = null)

fun pick(context, title, items: List<PickItem>, subtitle = null, icon = 0, columns = 1 /*2 ⇒ lưới khi ≥ GRID_MIN_ITEMS=4*/,
         emptyText = null /*rỗng ⇒ notice*/, linkLabel = null, onLink = null, onPick: (Int) -> Unit): Handle?
fun form(context, title, body: List<View>, primaryLabel, subtitle = null, icon = 0, validate: (() -> Boolean)? = null,
         focus: EditText? = null, showKeyboard = false, dangerLabel = null, onDanger = null, onOk: () -> Unit): Handle?
fun confirm(context, title, message, confirmLabel, destructive = false, cancelLabel = null, icon = 0, onConfirm): Handle?
fun notice(context, title, message, icon = 0, actionLabel = null, onAction = null): Handle?
fun field(context, label: CharSequence?, value = "", hint = null, inputType = TYPE_CLASS_TEXT, selectAll = false): Field
class Field { val view; val edit: EditText; val text: String; var error: CharSequence? /* != null ⇒ ô RED_SOFT + câu lỗi đỏ dưới ô */ }
fun caption(context, text, bold = false): TextView
```

### Ví dụ
```kotlin
// Chọn (R10 hồ sơ)
KachiDialogs.pick(ctx, ctx.getString(R.string.kachi_profile_pick_title),
    profiles.map { PickItem(ProfileNames.display(it), current = it == active, initial = ProfileNames.display(it).take(1)) },
    columns = 2, linkLabel = ctx.getString(R.string.kachi_profile_manage), onLink = onManage) { i -> switch(profiles[i]) }

// Biểu mẫu có kiểm tại chỗ (R11)
val coords = KachiDialogs.field(ctx, ctx.getString(R.string.kachi_places_hint_coords))
KachiDialogs.form(ctx, title, listOf(name.view, coords.view), ctx.getString(R.string.kachi_save),
    validate = { val ok = coords.text.isBlank() || SavedPlaces.parseCoords(coords.text) != null
                 coords.error = if (ok) null else ctx.getString(R.string.<lỗi>); ok }) { save() }
// đổi trạng thái không qua ô chữ (chip) ⇒ giữ Handle trả về rồi handle.revalidate()

// Xác nhận phá huỷ
KachiDialogs.confirm(ctx, title, "Mất: bố cục, phím, khung cụm…", ctx.getString(R.string.kachi_delete), destructive = true) { delete() }

// Thông báo kèm hành động (#7)
KachiDialogs.notice(ctx, title, msg, actionLabel = useNow) { apply() }
```

## Quyết định tự chủ (ghi vào spec §4.5 khi gộp)
- Nút chính kiểu NGUY HIỂM (Tone.DESTRUCTIVE) = nền `RED_SOFT` + chữ `RED` (không tô `RED` đặc + chữ trắng như mock):
  bảng tối có `red = #ff8fa0` (hồng sáng) ⇒ chữ trắng trên đó không đạt 4.5:1; cặp RED/RED_SOFT đã đo 4.57 (KDoc palette).
- Thẻ "đang dùng" = `surface(..., SurfaceTone.ACTIVE)` + chữ "✓ Đang dùng" (mock dùng outline — cấm viền WP1 · R1.1).
- Chân hộp tách thân bằng fill `CARD_FILL` (mock có border-top — cấm viền).
- `SettingsDialogs.confirm` giữ kiểu thường (không phá huỷ) vì chữ ký không mang thông tin đó; chỗ gọi phá huỷ
  (dọn sạch cụm `SettingsSectionsCast.kt:446`, xoá tên đã dạy `SettingsVoiceNamesPage.kt:216`) nên chuyển sang
  `KachiDialogs.confirm(destructive = true)` ở giai đoạn 2.
- `askPlace` giữ hành vi (3 ô, không kiểm); chỉ đổi khung + nhãn TRÊN ô (dùng chính chuỗi hint cũ). R11 ở giai đoạn 2.
- `askName`/`askText` focus ô + bôi chọn hết, KHÔNG tự bật bàn phím (giữ hành vi cũ của AlertDialog).

## Tests (đếm từ XML)
Chạy trong git worktree tách (`<scratchpad>/wt-d1`, HEAD + đúng các tệp trên) qua cùng khoá gradle — vì cây chung
**không biên dịch được** lúc chạy: `core/.../SimpleCastCoordinator.kt:76` (`Unresolved reference 'submit'`, việc dở của
agent R1–R4, không phải tệp của tôi).
- `:app:compileDebugKotlin` — XANH.
- `:app:testDebugUnitTest` (`*Dialog*` `*TopStripSurfaceContract*` `*SettingsScreenWiringContract*` `*ThemePalette*`
  + `*KeySourceL7Wiring*` `*SpacingScale*` `*TypeScale*` `*ZeroBorder*` `*SurfaceMaterial*` `*LauncherI18n*`
  `*TopStripWiring*` `*ProfileIoWiring*` `*ProfileImportFix286*` `*TripWiring*` `*CastDeepRescue*` `*ScaleConstantsUsed*`
  `*SlotEscapeReturn*` `*LauncherLocale*`): **189 tests · 0 fail · 0 skip** (KachiDialogContractTest 7/7).
- `*LayeringRules*` `*IconStyle*` `*KachiChrome*`: XANH.
- Ba bài đỏ ở vòng 1 và đã sửa đúng gốc: `KeySourceL7` (giữ dạng `if (detail == null) input else`), `LauncherI18n`
  (`Action.label` → `text`, bài cấm đọc `.label` thô), `SpacingScale` (bỏ hằng dp riêng, dùng `Sp.NOTE_MAX_W`).
- Chưa đo: hình thật trên máy ảo (stage 3), thời gian mở hộp trước/sau (+20 % — NFR spec).

## Còn `AlertDialog` (giai đoạn 2 — R13; bài canh hiện CHỈ phủ SettingsDialogs/KachiDialog*)
| Tệp:dòng | Hộp |
|---|---|
| `app/src/main/java/com/byd/clusternav/UpdateFlow.kt:61` | #21 Bản mới |
| `app/src/main/java/com/byd/clusternav/launcher/SettingsNavAutomationDialog.kt:195` | #3 Lịch dẫn đường |
| `app/src/main/java/com/byd/clusternav/launcher/KachiHomeDisclaimer.kt:28` | #20 Miễn trừ |
| `app/src/main/java/com/byd/clusternav/launcher/SettingsVoiceNamesPage.kt:90` | #14 trang Dạy tên |
| `app/src/main/java/com/byd/clusternav/launcher/SettingsVoiceNamesDialog.kt:68,100,108,124,229,267` | #15/#16/#17 (khoá nút chính qua `getButton(BUTTON_POSITIVE)` ⇒ `Handle.setPrimaryEnabled`) |
| `app/src/main/java/com/byd/clusternav/launcher/CapTestConsole.kt:165,227,242,265` | hộp chết (OQ3) |
| `app/src/main/java/com/byd/clusternav/launcher/VoiceTextConsole.kt:207` | hộp chết (OQ3) |
Khi hết: mở rộng `KachiDialogContractTest.khung, khuon va SettingsDialogs khong con AlertDialog` ra toàn `app/src/main`.
