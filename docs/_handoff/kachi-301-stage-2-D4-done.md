# kachi-301 · Stage 2 · D4 — R13: các hộp còn lại lên `KachiDialog` — DONE (off-car, không commit)

> Spec: `docs/specs/kachi-301-cast-steps-dialog-ia.html` R8 · R9 · R13 · §4.3. Đọc trước: `kachi-301-stage-1-D1-done.md` (API).

## Files
| Tệp | Hộp (§4.3) | Đổi |
|---|---|---|
| `app/src/main/java/com/byd/clusternav/UpdateFlow.kt` | #21 Bản mới → **Xác nhận** | `KachiDialogs.confirm(…, cancelLabel = "Để sau")`; chữ vẫn qua `Lang.f/t` (không R.string) |
| `launcher/KachiHomeDisclaimer.kt` | #20 Miễn trừ → **Thông báo** | `KachiDialog` trực tiếp (giữ nhãn `kachi_disclaimer_ok`, icon `ic_alert`); **cancelable như cũ** (AlertDialog mặc định đóng được); cờ vẫn đặt TRƯỚC khi hiện |
| `launcher/SettingsVoiceNamesPage.kt` | #14 trang Dạy tên → khung + đầu hộp Kachi | `KachiDialog` + `fullWidth(root)` (nới cửa sổ MATCH_PARENT ngang qua `WindowManager.updateViewLayout` decor — cùng đường `Window.setLayout`); #19 xoá tên → `KachiDialogs.confirm(destructive = true)` |
| `launcher/SettingsVoiceNamesDialog.kt` | #15/#17 Dạy tên → **Biểu mẫu**; #16 Thử → **Thông báo** | Lưu = `primary(autoDismiss = false) { save() }`; `getButton(BUTTON_POSITIVE).isEnabled` → `Handle.setPrimaryEnabled`; `onDismiss { relay.cancel(); onClosed(changed) }` (cùng đường đóng cũ); hộp thử: `onDismiss { relay.cancel() }`, không mở được ⇒ không bắt đầu nghe. #18 Lưu dù cảnh báo: đã là `SettingsDialogs.confirm` (khuôn Xác nhận) — giữ |
| `launcher/SettingsSectionsKeys.kt` | #8 chuỗi gán phím → 5 hộp **Chọn** | một chỗ dựng `keyPick(...)` (dòng phụ "Bước n/3"); bước 1: dòng phụ = đích ĐANG gán cho đúng (mã, nguồn); bước 2: icon (`ic_grid`, `ic_cam`, icon nhóm từ `CapabilityGroups.icon` → `KachiTheme.iconRes`) + dòng phụ 2 việc đầu của nhóm; camera: icon vị trí từ `LauncherActions` (cùng nút thanh xe); app: chỉ trợ lý có `ic_mic` (KHÔNG bung icon mọi app trên luồng vẽ). Logic chọn/ghi + chữ ký 4 hàm `pick*` giữ nguyên (bài KeyCtl ghim) |
| `res/values{,-en,-zh-rCN,-th,-ms}/strings_kachi_misc301.xml` (MỚI) | — | `kmisc_keys_step` · `kmisc_keys_bound` |
| `app/src/test/java/com/byd/clusternav/launcher/KachiDialogMigrationContractTest.kt` (MỚI) | — | 6 bài (dưới) |

`launcher/AppDrawerApps.kt` (#22 menu nhấn giữ) — **KHÔNG đổi, giữ `PopupMenu`**. [ĐO mã] `DrawerController.kt:149–156`: ngăn kéo
mở bằng `windowManager.addView(..., TYPE_APPLICATION_OVERLAY)` khi có quyền vẽ-đè. `KachiDialog` là cửa sổ `TYPE_APPLICATION`
⇒ nằm DƯỚI lớp overlay ⇒ hộp Chọn bị ngăn kéo che (đúng bệnh "trang mở nằm dưới ngăn kéo" đã đo 06/10). `PopupMenu` neo vào ô
nên là cửa sổ con của overlay ⇒ nổi đúng. Muốn đổi thì `KachiDialog` cần chế độ cửa sổ con theo token của anchor (việc của chủ
`KachiDialog.kt`) — ghi Open Question.

## Bài canh mới — `KachiDialogMigrationContractTest` (6/6 xanh)
1. `AlertDialog` cấm trong MÃ (bóc chú thích) toàn `app/src/main/java`, ngoại lệ TƯỜNG MINH kèm lý do:
   `CapTestConsole.kt`, `VoiceTextConsole.kt` (hộp chết, OQ3) · `SettingsNavAutomationDialog.kt` (D3 đang thay). **Điều phối
   siết**: khi D3 xong, xoá dòng `SettingsNavAutomationDialog.kt` khỏi `allowed`.
2. Hộp dạy: Lưu không tự đóng · `setPrimaryEnabled` ở `repaint`/`commit` · đóng ⇒ `relay.cancel()`; hộp thử: show trước listen.
3. Trang: `KachiDialog` + `fullWidth` · `onDismiss` refresh · không `ScrollView` · xoá = phá huỷ.
4. UpdateFlow/miễn trừ giữ hành vi (nhãn "Để sau", `doUpdate`, cờ trước hiện, nhãn nút riêng).
5. Chuỗi gán phím: không còn `SettingsDialogs.pick(`, đúng 5 lượt `keyPick(`, có `kmisc_keys_step`.
6. `kmisc_` đủ 5 thư mục, cùng khoá, không dấu Việt ngoài `values/`, đều được dùng.

## Đổi hành vi có chủ đích (ghi vào spec §4.5)
- #14 trang Dạy tên: thân `KachiDialog` tự cuộn ⇒ ô tìm + chip lọc CUỘN cùng danh sách (trước: cố định trên, danh sách cuộn
  riêng), chiều cao theo nội dung tới trần 0,86 màn (trước: MATCH_PARENT cao). Muốn giữ y cũ cần API khung "thân không cuộn +
  toàn màn" ở `KachiDialog.kt` (không thuộc tệp của tôi) — Open Question cho chủ khung.
- Hộp dạy/thử không mở được (Activity đã đóng) ⇒ không điền sẵn/không bắt đầu nghe (trước: lượt nghe chạy mà không ai huỷ).

## Việc cần ở `SettingsSectionsCast.kt` (#12 Cứu sâu — tệp của agent khác)
`SettingsSectionsCast.kt:452` (trong `rescue`, `onConfirm = { proceed -> … }`): đổi
```kotlin
SettingsDialogs.confirm(
    context,
    context.getString(R.string.kachi_cast_deep),
    context.getString(R.string.kachi_cast_deep_confirm),
    context.getString(R.string.kachi_cast_deep_ok),
    proceed,
)
```
thành
```kotlin
KachiDialogs.confirm(
    context,
    context.getString(R.string.kachi_cast_deep),
    context.getString(R.string.kachi_cast_deep_confirm),   // đã nói rõ mất gì + "Không hoàn tác được"
    context.getString(R.string.kachi_cast_deep_ok),
    destructive = true,
    onConfirm = proceed,
)
```

## Exit gate (đếm từ XML)
Cây chung KHÔNG biên dịch lúc chạy: `SettingsPlaceDialog.kt`/`TimeWheel.kt` thiếu khoá `kform_*` (D3 đang làm). Chạy trong git
worktree tách `<scratchpad>/wt-d4` (HEAD + rsync toàn bộ cây làm việc + MỘT tệp chuỗi giả `zz_stub_kform.xml` CHỈ trong worktree
cho 14 khoá `kform_*` của D3), qua cùng khoá gradle; `cmp` xác nhận 12 tệp của tôi trùng byte với cây chung; worktree đã gỡ.
- `:app:compileDebugKotlin` — XANH.
- `:app:testDebugUnitTest` focused (`*VoiceNames*` `*Teach*` `*Keys*` `*KeySource*` `*Update*` `*Disclaimer*` `*KachiDialog*`
  `*LauncherI18n*` `*Spacing*` `*TypeScale*` + `*KeyCtl*` `*VoiceKeyBinding*` `*ButtonOptionLabel*` `*VoiceAppNames*`
  `*SettingsScreenWiring*` `*SettingsCatalogControl*` `*ClusterNavSettingsWiring*` `*SettingsStackMargin*`):
  **241 tests · 4 fail · 0 skip** — cả 4 thuộc việc dở của agent KHÁC, không tệp nào của D4:
  - `KachiDialogContractTest.moi ham SettingsDialogs di qua khuon` — `askPlace` không còn gọi `KachiDialogs.` (D3 chuyển sang `SettingsPlaceDialog`; D3/điều phối cập nhật bài).
  - `LauncherI18nContractTest` ×3: chữ Việt cứng ở `voice/VoiceMusicDuck.kt` (V) · `.label(` thô ở `SettingsPlaceDialog.kt` (D3) · khoá `kachi_cast_frame_step`/`kachi_cast_geom_*` chỉ ở `strings_kachi_geom301.xml` mà bài chỉ đọc `strings_kachi.xml` (G — đổi tiền tố như D1 đã cảnh báo).
- `KachiDialogMigrationContractTest` 6/6 · `VoiceAppNamesWiring` 11/11 · `VoiceTeachHint` 3/3 · `KeyCtlWiring` 7/7 · `KeySourceL7` 7/7 · `KeySourceSplit` 5/5 · `Update*` 28/28 · `Spacing` 10/10 · `TypeScale` 3/3.
- Call site (CLAUDE.md §8): `keyPick(` 5 · `fullWidth(` 1 · `camOpIcon(` 1 · `previewOf(` 1. `wc -l` mọi tệp ≤ 440.
- Chưa đo: hình thật Sáng/Tối trên máy ảo (stage 3), cụ thể trang Dạy tên toàn ngang + hộp dạy giữ nghe khi mở hộp gõ tên chồng lên.

## Open Questions (cho chủ `KachiDialog.kt` / owner)
1. #22: thêm chế độ cửa sổ con (token anchor) cho `KachiDialog` để menu nhấn giữ trên ngăn kéo overlay đi được khuôn Chọn?
2. #14: thêm API "toàn màn + thân không cuộn" để ô tìm cố định như cũ (và thay `fullWidth` của trang)?
