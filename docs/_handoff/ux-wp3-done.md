# WP3 — Board widget chuẩn hoá + hình xe chuyên nghiệp nhiều model · XONG off-car, CHƯA commit

> **Trạng thái**: code + test xong · 5 module **5131 test / 0 đỏ** · đã chụp máy ảo API 29 · **Cập nhật**: 2026-09-20
> **Spec**: `docs/specs/kachi-ux-overhaul.html` §WP3 (R3.1 board · R3.2 hình xe) · **Nền**: WP1 (glass+0 viền) + WP2 (control-state) đã duyệt
> **Ảnh cho owner duyệt**: `docs/diagnostics/ux-overhaul-2026-09-20/wp3-*.png`
> **KHÔNG commit** (cây làm việc còn giữ cả WP1/WP2 chưa commit — theo lời giao). CHỈ WP3, KHÔNG đụng WP4–9.

---

## 1. Owner giao gì → làm gì

| # | Owner | Đã làm |
|---|---|---|
| R3.1 | Chuẩn hoá MỌI board widget theo MỘT chuẩn; sửa lệch *"Cửa&Khoang có khóa/cốp/cửa-sổ-trời = 1 nút mà rèm 3 option → lệch cả widget"* | Trong hàng nút của ô nhóm/board, **COVER (cốp/rèm) nay là MỘT tile cycle** (như TOGGLE/SELECT): hiện trạng thái + bấm cycle Đóng→Mở→(Nửa)→Đóng, **KHÔNG còn xếp 2–3 nút phụ** ⇒ 5 ô của *Cửa & khoang* (Khoá·Mở khoá·Cốp·Cửa sổ trời·Rèm) đồng nhất cỡ/dáng. Nút mở-50% giữ nguyên ở **ô rộng** (thanh nút · ô giữa màn — còn chỗ). |
| R3.2 | Vẽ LẠI hình xe chuyên nghiệp (bỏ kiểu "trẻ con"); model Seal·Han·Sealion 6·Sealion 8 + generic, cho user chọn | **(a)** Vẽ lại hình xe: bánh **thu vào vè** (bỏ dáng "que/sơ đồ"), cửa **đóng liền thân** (chỉ MỞ mới swing vạt), gương nêm nhỏ ở chân kính. **(b)** Bộ chọn KIỂU XE ở Cài đặt › Màu sắc: 5 model, mỗi model một tỉ lệ dáng riêng (sedan thuôn ↔ SUV vuông). |

---

## 2. R3.1 — Board chuẩn hoá: vì sao "lệch" và fix

[ĐO đọc mã] Trong nhóm *Cửa & khoang* (`g_doors`), hàng nút gồm: `lock` (TOGGLE) · `door` (BUTTON) · `trunk` (COVER) · `sunroof` (TOGGLE) · `sunshade` (COVER). Bộ dựng cũ vẽ COVER ở ô hẹp thành **nhãn + 2–3 nút phụ xếp DỌC** (Đóng/Mở/Nửa), nên hai ô `trunk`/`sunshade` **cao hơn & khác hẳn** ba ô một-tile cạnh nó — đúng "lệch cả widget" owner báo.

**Fix** (`ControlTileFactory.tileCover` tách theo VÙNG):
- **narrow** (hàng nút board — `TileSize.GROUP`) → `coverCycle`: một tile, nhãn + chữ mức hiện tại, bấm `nextSelectIndex` → `coverLevel(id, mức)`. Giữ đủ Đóng/Mở/Nửa (cycle qua), **0 nút phụ**.
- **rộng** (dock · ô giữa màn — `DOCK`/`BIG`) → `coverButtons`: nhiều nút có nhãn như cũ (giữ đường "nút mở 50%" T7, ở đó còn chỗ).
- Đường **đọc-lại xe** (WP2 · R2.3, cốp mở → màu nhấn) giữ nguyên trong `tileCover`.

Test: `ControlStateUxContractTest.WP3 COVER trong board la MOT tile cycle...` (mới) + `GroupTileTightSpaceContractTest.hang nut cua nhom dung co o HEP` (đảo assertion cũ `size.narrow) LinearLayout.VERTICAL` → `if (size.narrow) coverCycle(`).

⚠ **Đánh đổi để owner biết**: cycle-tile gọn & đồng nhất, nhưng bấm nhiều lần mới tới "Nửa" (thay vì thấy 3 nút cùng lúc). Ô RỘNG vẫn bày 3 nút, nên khi cần chính xác thì đặt cốp/rèm ra thanh nút. Nếu owner muốn board cũng bày đủ nút → nói, em đổi hướng.

---

## 3. R3.2a — Hình xe chuyên nghiệp hơn (bỏ "trẻ con")

[ĐO ảnh máy ảo] Dáng cũ "trẻ con" = **bánh thò xa ra ngoài trên "que"** — thực ra "que" là **vạt cửa đóng** vẽ thành đường viền nối thân ra tận bánh (`door_lf` chạy tới x3.9), + bánh ở x3.1/18.3 (rất xa thân 7.5–16.5). Cả cụm đọc thành "sơ đồ nổ" chứ không phải xe.

**Ba thay đổi** (2 file, đều off-car, `gen-car --check` OK 49 tệp byte-match):
1. `design/car/top.svg` — bánh **5.6–7.8 / 16.2–18.4** (peek ở vè, không thò xa), hẹp hơn (2.2 thay 2.6, giống lốp nhìn từ trên); gương nêm nhỏ peek ~1.4 (trước thò tận 4.7); vạt cửa ngắn lại (mép ngoài 3.9→5.6).
2. `CarPartStyle` (`:core`) — **FLAP đóng/chưa-đọc = HIDDEN** (cửa liền thân, chỉ MỞ mới swing vạt), tách khỏi PANEL (ca-pô/cốp giữ nét gờ mờ TRÊN thân). Test `CarPartStyleTest.cua dong an, panel giu net mo`.
3. gen-car sinh lại 42 icon + `car_face_*` + `CarFramesGenerated.kt` + manifest (byte-check gate). Icon `ic_car_*` chỉ đổi vị trí bánh/gương/vạt — vẫn hợp lệ, `IconStyleContractTest`/`CarFramesSourceContractTest` xanh.

Kết quả (ảnh): xe nghỉ = thân + kính + 4 bánh ở góc + gương nhỏ = **một chiếc xe nhìn từ trên**, hết dáng nổ. Bảng lốp cũng sạch hơn.

---

## 4. R3.2b — Bộ chọn KIỂU XE (5 model)

**Kiến trúc** (dùng lại trọn bộ dây `ColorChoice`→`applyTheme`→selector của màu sơn, ít ripple):
- `CarModel` (`:core`, mới) — 5 model, mỗi cái cặp `stretchX/stretchY` (kéo dãn quanh tâm 24×24). `title()` song ngữ.
- `ColorChoice.model` (`:core`) — trường thứ 4, encode nối sau (`ACCENT;TONE;paint;model`), cấu hình cũ thiếu ⇒ generic.
- `KachiTheme.carModel` (`:app`) — set trong `applyTheme` + vào change-detection (đổi model ⇒ dựng lại như đổi sơn).
- `CarArtPainter.layout` — kéo dãn theo model quanh tâm rồi mới phóng vào ô, **ma trận GỘP** (`fit ∘ model`) ⇒ đường vẽ + hộp bao (ô giá trị bảng lốp) đi qua cùng phép biến hình, không lệch. GENERIC = (1,1) ⇒ không đổi.
- Cài đặt › Màu sắc: hàng chip "Kiểu xe" (`kachi_row_car_model`, VI/EN).

**[ĐO ảnh]** 5 model khác nhau rõ theo tỉ lệ:
`Seal` sedan thuôn/hẹp nhất · `Han` sedan dài, đầy hơn · `Chung` trung tính · `Sealion 8` SUV rộng+dài · `Sealion 6` SUV vuông/ngắn nhất. Đổi model ở prefs `color_choice` → xe đổi dáng đúng như thiết kế; ô giá trị bảng lốp bám đúng bánh mới.

Test: `CarModelTest` (5 model · mã vòng tròn · nhãn khác nhau · **5 tỉ lệ dài:rộng phân biệt** · Seal thuôn hơn generic · Sealion 6 vuông hơn) + `ColorChoiceTest` (roundtrip có model · thiếu trường → generic).

### ⚠⚠ Giới hạn ĐÃ BIẾT (bản đầu — cần owner duyệt hướng)
- **Kéo dãn tỉ lệ KHÔNG cho mỗi model một dáng RIÊNG THẬT.** Hai xe cùng lớp (Seal↔Han, Sealion 6↔8) chỉ khác **nhẹ** ở tỉ lệ — vì phóng-giữ-tỉ-lệ nên chỉ tỉ lệ dài:rộng nhìn thấy được. Muốn Seal có mũi dài/đuôi fastback riêng, Han có khoang kính trang trọng riêng, Sealion có dáng SUV thật → phải **vẽ hình học riêng từng model** (per-model geometry, vd `CarModelsGenerated` + `ModelCarArt` hoặc bộ SVG per-model). Đó là **bước lặp sau khi owner chốt hướng** — hệ số hiện tại là chỗ owner tinh chỉnh bằng mắt trước.
- **Scope pref**: owner spec ghi *"pref theo XE"* (DEVICE); bản đầu này để **theo HỒ SƠ** (trong `ColorChoice`, cạnh màu sơn) để dùng lại trọn dây, ít ripple. Đổi sang DEVICE là việc nhỏ — **xin owner chốt**.

---

## 5. Tệp đổi

**Mới**: `core/.../CarModel.kt` (56) · `core/src/test/.../CarModelTest.kt` (52)
**Sửa `:core`**: `CarPartStyle.kt` (FLAP↔PANEL tách, cửa đóng ẩn) · `ColorChoice.kt` (+`model`) · test `CarPartStyleTest`·`ColorChoiceTest`
**Sửa `:app`**: `ControlTileFactory.kt` (498, `tileCover`→`coverCycle`/`coverButtons`) · `CarArtPainter.kt` (model matrix) · `KachiTheme.kt` (`carModel`) · `SettingsSections.kt` (hàng model) · test `ControlStateUxContractTest`·`GroupTileTightSpaceContractTest`
**Res `:app`**: `strings_kachi.xml` VI+EN (+`kachi_row_car_model`) · **SINH LẠI** 42 `ic_car_*.xml` + 4 `car_face_*.xml` + `CarFramesGenerated.kt`
**Design**: `design/car/top.svg` (bánh/gương/vạt) · `design/car/manifest.json` · `design/car/paint.json` (sinh lại)

---

## 6. [ĐO] Bằng chứng

| Phép đo | Kết quả |
|---|---|
| `./gradlew test --rerun-tasks --continue` | **5 module · 5131 test · 0 đỏ · 0 lỗi · 0 bỏ qua** (đếm từ JUnit XML) |
| `python3 scripts/design/gen-car.py --check` | **OK — 49 tệp khớp byte** (SVG ↔ icon ↔ CarFramesGenerated đồng bộ) |
| `:core` thuần | `CarModel`/`ColorChoice`/`CarPartStyle`: **0** import `android.*` |
| Trần 500 dòng | `ControlTileFactory` 498 · `CarArtPainter` 223 · `SettingsSections` 450 · `CarModel` 56 · `CarPartStyle` 222 — đạt |

**Ảnh** (`docs/diagnostics/ux-overhaul-2026-09-20/`, máy ảo `emulator-5554` API **29**, 1920×1080 = cấu hình xe, dark):
- `wp3-part1-boards-dark.png` — Cửa & khoang + Lốp; hàng nút *đồng nhất* (Cốp·Rèm là tile cycle "Close", không còn nhiều nút). ⚠ ảnh này chụp SAU Part 1 nhưng TRƯỚC 2a nên xe còn dáng cũ ⇒ là mốc *"before car / after board"*.
- `wp3-car-2a-dark.png` — xe generic sau 2a (bánh thu vào vè, cửa liền thân).
- `wp3-model-{generic,seal,han,sealion6,sealion8}.png` — 5 model.

⚠ Phiên KHÔNG có công cụ sinh sub-agent ⇒ chưa soát độc lập; và luật `image-reading-subagent.md` cấm đọc ảnh lớn trong phiên chính ⇒ em đọc ảnh **đã thu nhỏ ≤1100px** (an toàn) để tự kiểm look. **Owner nhìn ảnh gốc là bước xác nhận cuối.** Máy ảo đã trả về cấu hình gốc + `adb unroot`.

---

## 7. ⚠ Owner quyết — câu hỏi

1. **Board COVER cycle-tile** (R3.1): đồng nhất nhưng "Nửa" phải bấm cycle mới tới. OK, hay muốn board bày đủ nút (chấp nhận lệch cỡ)?
2. **Hình xe per-model** (R3.2b): bản đầu phân biệt bằng TỈ LỆ (Seal↔Han, SL6↔8 khác nhẹ). Chốt hướng: (a) đủ cho giờ, tinh chỉnh hệ số; hay (b) đầu tư vẽ **hình học riêng từng model** (mũi/đuôi/khoang kính khác nhau)?
3. **Scope pref model**: theo HỒ SƠ (hiện tại) hay đổi sang theo XE (DEVICE) như spec?
4. Hệ số kéo dãn từng model (trong `CarModel.kt`) — owner ngắm ảnh rồi bảo "thuôn hơn/vuông hơn", em chỉnh số.

---

## 8. Chưa làm / ngoài phạm vi
- WP4–WP9 chưa đụng.
- Chưa vẽ hình học riêng per-model (xem §4 giới hạn).
- Chưa commit / chưa bump version / chưa OTA (theo lời giao).
- Chưa đo trên xe thật (màu/tỉ lệ trên cụm; cửa mở swing vạt khi có dữ liệu thật).
