# kachi-301 · Stage 2 · D2 — R10 Bộ chọn hồ sơ dạng thẻ + Xoá hồ sơ qua hộp Xác nhận — DONE (off-car)

> Spec: `docs/specs/kachi-301-cast-steps-dialog-ia.html` §3 R10 · §4.3 · §4.4 (mẫu "Đổi hồ sơ tài xế" + "Xoá hồ sơ?").
> Dựng trên API của D1 (`docs/_handoff/kachi-301-stage-1-D1-done.md`). Không commit (cây chung). Không đụng
> `KachiDialog*.kt` / `SettingsDialogs.kt`.

## Files
| Tệp | Đổi | Dòng |
|---|---|---|
| `app/src/main/java/com/byd/clusternav/launcher/ProfileChip.kt` | `picker` → `KachiDialogs.pick` thẻ: `initial` (chữ cái đầu, 1 điểm mã, `uppercase()`), `current`, dòng phụ = `viewModel.profileSummary(name)`, `columns = 2`, `linkLabel`/`onLink = { onManage() }`, subtitle hộp | 112 |
| `app/src/main/java/com/byd/clusternav/launcher/SettingsSectionsProfiles.kt` | nút Xoá → `confirmDelete(name)` (MỚI) = `KachiDialogs.confirm(…, destructive = true) { deps.onDeleteProfile(name) }` | 344 |
| `app/src/main/res/values{,-en,-zh-rCN,-th,-ms}/strings_kachi_prof301.xml` (MỚI) | `kprof_pick_subtitle` · `kprof_delete_title` · `kprof_delete_message` · `kprof_delete_confirm` | 4 khoá × 5 |
| `app/src/test/java/com/byd/clusternav/launcher/ProfileDialog301ContractTest.kt` (MỚI) | 3 bài canh | 65 |
| `app/src/test/java/com/byd/clusternav/launcher/TopStripSurfaceContractTest.kt` | `SettingsDialogs.pick(` → `KachiDialogs.pick(` + cấm `AlertDialog` | ±3 |
| `app/src/test/java/com/byd/clusternav/launcher/SettingsScreenWiringContractTest.kt` | `nut xoa ho so…`: `deps.onDeleteProfile(` trong `profileRow` → `confirmDelete(name)` | ±3 |

## Quyết định
- **Gọi thẳng `KachiDialogs.pick`, không qua `SettingsDialogs.pick`**: chữ ký kia chỉ nhận `List<String>` ⇒ không chở
  được `initial`/`current`/`subtitle`/`columns`/`linkLabel`. Không được sửa `SettingsDialogs.kt`, và một bản quá tải ở
  đó cũng chỉ chuyển tiếp nguyên văn. ⇒ sửa `TopStripSurfaceContractTest:169` tối thiểu (lời hứa giữ nguyên: dùng LẠI
  khuôn chung, không AlertDialog). KDoc lớp `ProfileChip` ghi lý do. `SettingsScreenWiringContractTest:365` (cấm
  `AlertDialog`/`askName` trong `picker`, đòi `onManage()`) vẫn xanh không cần sửa — vì thế `onLink = { onManage() }`.
- **Dòng phụ = dữ liệu thật**: `HomeViewModel.profileSummary(name)` (tóm tắt bố cục; cùng câu thẻ ở Cài đặt). [ĐO]
  `WorkspacePrefs` không có mốc "sửa lần cuối" theo hồ sơ ⇒ không hiện ngày như mẫu §4.4.
- Hồ sơ đang dùng: dòng phụ dùng khoá có sẵn `kachi_profile_pick_active` ("<tóm tắt> · đang dùng") + khuôn tự thêm
  "✓ Đang dùng" — đúng mẫu (dòng phụ "Đang dùng" + ✓). Giữ khoá này có người dùng ⇒ không thành khoá mồ côi trong
  `strings_kachi.xml` (tệp không thuộc D2).
- "Quản lý hồ sơ…" thành nút chân trái (link) thay mục cuối danh sách ⇒ bỏ phép so `which == list.size`; chọn chỉ số
  ngoài danh sách ⇒ `getOrNull` bỏ qua.
- Không có drawable hồ sơ/người trong `res/drawable` ⇒ hộp chọn không có icon đầu (icon = 0); khuôn `pick` không nhận
  `glyph`. Nếu muốn ô 👤 như mẫu: thêm `glyph` vào `KachiDialogs.pick` (tệp D1).
- **Câu "mất gì"** kê theo [ĐO source] thứ `WorkspacePrefs.deleteProfile` dọn: `profileKeys(name)` =
  `ProfileScope.LAUNCHER_SUFFIXES` = bố cục/ô/thanh (LAYOUT) + giao diện/hình nền/đơn vị/ngôn ngữ/sổ địa chỉ/tên app đã
  dạy (PERSONAL) + ảnh chụp ClusterNav (`SNAPSHOT_SUFFIXES`: chiếu cụm + `cast_geometry` khung/DPI, camera,
  `voicekey_bindings` phím vô-lăng, `nav_automation_rules` lịch dẫn đường). Gợi ý "Xuất hồ sơ trước" (không có hoàn tác).
- Nút Xoá vẫn chỉ hiện khi `!active && total > 1` (không đổi); Huỷ/Back/chạm ngoài ⇒ không làm gì.
- Đổi tên / Thêm (bản sao) vẫn qua `SettingsDialogs.askName` (đã trên KachiDialog từ D1) — không đổi.
- 0 luồng/nhịp/listener nền mới; `profileSummary` đọc prefs đồng bộ mỗi hồ sơ lúc mở hộp (cùng chi phí thẻ ở Cài đặt).

## Tests (đếm từ XML)
Cây chung KHÔNG dùng để build (việc dở của agent khác). Chạy trong worktree tách `<scratchpad>/wt-d2` (HEAD + tệp
stage-1 D1 + tệp D2, xác nhận `cmp` trước/sau), gradle qua cùng khoá `kachi-gradle.lock.d`; worktree đã gỡ.
- `:app:compileDebugKotlin` — XANH.
- `:app:testDebugUnitTest --tests *Profile* *TopStrip* *SettingsScreenWiring* *KachiDialog* *LauncherI18n* *Spacing*
  *TypeScale* *VoiceFastNatural* *SettingsCatalogControl* *ZeroBorder*`: **25 lớp · 244 tests · 0 fail · 0 error ·
  0 skip** (ProfileDialog301ContractTest 3/3, SettingsScreenWiringContractTest 21/21).
- Chứng minh đỏ: tạm đổi `setOnClickListener { confirmDelete(name) }` → `{ deps.onDeleteProfile(name) }` ⇒
  `ProfileDialog301ContractTest` 1 fail (*"intent xoá phải có ĐÚNG MỘT chỗ gọi … expected <1> but was <2>"*); khôi phục
  (cmp) ⇒ xanh lại.
- Chưa đo: hình thật trên máy ảo (stage 3) — lưới 2 cột với số hồ sơ lẻ (hàng cuối 1 thẻ chiếm cả hàng, do
  `gridRow` của D1 dùng weight), độ dài câu "mất gì" ở th/ms trong bề rộng 600dp.
