# kachi-301 · Stage 2 · I — `KachiDialog`: dòng phụ sống + chế độ trang (toàn màn + vùng ghim) — DONE (off-car, không commit)

> Spec `docs/specs/kachi-301-cast-steps-dialog-ia.html` R8. Đọc trước: D1 (API khung), D3 (QĐ 1 — `findText`), D4 (hồi quy #14).

## Files
| Tệp | Đổi | Dòng |
|---|---|---|
| `app/src/main/java/com/byd/clusternav/launcher/KachiDialog.kt` | + `Handle.setSubtitle(CharSequence?)` · + `pinned(view)` · + `fullScreen()`; dòng phụ LUÔN có trong cây (GONE khi trống); `frame` tách `column(...)` dùng chung cho vùng ghim + thân; `page(...)` = lớp nền trang | 379 → 433 |
| `launcher/SettingsNavAutomationDialog.kt` | tóm tắt sống = `handle?.setSubtitle(summary())`; xoá `findText`, biến `subtitleView`, `firstSummary`, 2 import thừa | 317 → 296 |
| `launcher/SettingsVoiceNamesPage.kt` | `.fullScreen().pinned(root).body(list)`; xoá `fullWidth()` + import `WindowManager` | 250 → 239 |
| `app/src/test/.../launcher/KachiDialogContractTest.kt` | + 1 bài `dong phu song va trang toan man co cho goi that` | 7 → 8 bài |

## API mới (thêm vào bảng của D1)
```kotlin
fun pinned(view: View)    // vùng KHÔNG cuộn giữa đầu hộp và thân cuộn; lề ngang Sp.XL, khe Sp.S (giống thân)
fun fullScreen()          // cửa sổ setLayout(MATCH_PARENT, MATCH_PARENT); thẻ chừa lề Sp.L (FrameLayout trong suốt);
                          // thân cao 0 + trọng số 1 = lấp phần còn lại; bỏ trần MAX_H_FRACTION và NOTE_MAX_W
Handle.setSubtitle(text: CharSequence?)  // null/trống ⇒ ẩn dòng phụ
```
- Trang: chạm vào lề (ngoài thẻ, trong cửa sổ) ⇒ `dialog.cancel()` khi `cancelable` — giữ ngữ nghĩa `setCanceledOnTouchOutside`; thẻ `isClickable` để chạm trong thẻ không lọt xuống nền.
- Bàn phím: vẫn `SOFT_INPUT_ADJUST_RESIZE` ⇒ trang co, thân cuộn co, vùng ghim + chân đứng yên.
- `setTitle` KHÔNG thêm: không có chỗ gọi (CLAUDE.md §8 — không viết hàm chết).
- Hiệu năng: 0 luồng/nhịp/listener mới (bài `banned` của KachiDialogContractTest vẫn xanh).

## #14 sau sửa (khôi phục hành vi trước R13, trên khung Kachi)
Ghi chú · banner · ô tìm · chip lọc = **ghim** (đứng yên); **chỉ danh sách cuộn**; trang cao/rộng hết màn trừ lề Sp.L; đầu hộp Kachi (icon mic + tiêu đề) + chân "OK".

## ⚠ Việc của điều phối (tệp không thuộc tôi)
`KachiDialogMigrationContractTest.kt:66` còn đòi `"fullWidth(root)" in open` ⇒ **ĐỎ 1 bài** sau đổi này. Đổi thành:
```kotlin
assertTrue("KachiDialog(context)" in open && ".fullScreen()" in open && ".pinned(root)" in open,
    "trang toàn màn trên khung Kachi, ô tìm + chip ghim")
```
(Các dòng 67–69 giữ nguyên — trang vẫn không có `ScrollView`, vẫn `onDismiss` refresh, xoá vẫn phá huỷ.)

## Exit gate (cây CHUNG, `gradle-locked.sh`, đếm từ XML)
- `:app:compileDebugKotlin` — XANH.
- `:app:testDebugUnitTest` `*KachiDialog*` `*KachiForm*` `*VoiceNames*` `*Teach*` `*Automation*` `*Spacing*` `*TypeScale*` `*ZeroBorder*`:
  **57 tests · 1 fail · 0 skip** — fail duy nhất = `KachiDialogMigrationContractTest.trang day ten giu khung moi…` (dòng 66, xem trên).
  KachiDialogContractTest 8/8, KachiFormContractTest 6/6, Automation185WiringTest 16/16, SpacingScale 10/10, TypeScale 3/3,
  ZeroBorder 5/5, VoiceTeachHint 3/3. (`*VoiceNames*` không khớp lớp test nào.)
- Grep chỗ gọi: `setSubtitle(` ← `SettingsNavAutomationDialog.kt:233`; `.fullScreen()`/`.pinned(` ← `SettingsVoiceNamesPage.kt:93-94`;
  `findText`/`fullWidth` = 0 trong `app/src/main/java`.
- Chưa đo: hình thật trên máy ảo (stage 3) — lề trang, vùng ghim đứng yên khi cuộn, dòng phụ đổi sau mỗi chạm, bàn phím mở trên trang.
