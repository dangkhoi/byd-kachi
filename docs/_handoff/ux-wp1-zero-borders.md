# UX-OVERHAUL WP1 · lượt 3 — **ZERO BORDER** toàn UI launcher + siết lề ngang còn 80 %

> **Trạng thái**: off-car DONE · **Ngày**: 2026-09-20 · **CHƯA commit** (theo lời giao)
> Nối tiếp `ux-wp1-done.md` → `ux-wp1-edges-removed.md`. Spec `docs/specs/kachi-ux-overhaul.html` §R1.1.
>
> **Nguồn yêu cầu** — owner sau khi xem ảnh lượt 2:
> *"1, gỡ hết, tất các button, widget gì gỡ hết, KHÔNG còn viền ở BẤT CỨ ĐÂU hết. 2, canh lại margin header,
> taskbar, trái phải, bé lại còn 80%, đang dư thừa khoảng trắng phí."* — bảng SÁNG owner đã chốt **GIỮ NGUYÊN**.

---

## 0. Kết quả một dòng

**[ĐO] 0 `setStroke` trong toàn tầng vẽ launcher · 0 vạch 1px trên cả ba ảnh (tối/sáng/Cài đặt) · lề ngang 24px → 19px (79.2 %) · 5 module 5085 test / 0 đỏ · 3 phép thử phá đỏ đúng chỗ + 1 phép chứng minh bộ dò ảnh không mù.**

---

## 1. ⚠⚠ Bài học lớn nhất của lượt này — **xoá THAM SỐ, không đổi MẶC ĐỊNH**

Lượt 2 (`ux-wp1-edges-removed.md`) đã đổi `card()`/`pill()` sang `stroke: String = CLEAR` — *"không viền theo mặc
định, viền là opt-in"*. Nghe là đủ. **Không đủ**: lượt này [ĐO] vẫn còn **5 chỗ gọi truyền viền vào**, và
grep chỉ tìm thấy **2** trong số đó.

Ba chỗ còn lại **chỉ lộ ra khi trình biên dịch từ chối**, sau khi tôi xoá hẳn tham số:

| chỗ gọi | grep thấy? | vì sao trượt |
|---|---|---|
| `KachiTopStrip:77` `card(…, BAR_TOP, LINE_STRONG)` | ✅ | khớp mẫu `LINE` |
| `VoiceOverlay:108` `card(…, BAR_TOP, LINE_STRONG)` | ✅ | khớp mẫu `LINE` |
| `GroupTileViews:464` `card(…, fillOf(t), strokeOf(t))` | ✅ | khớp `stroke` |
| **`AppDrawer:356`** `card(…, AMBER_SOFT, AMBER)` | ❌ | đối số viền là `AMBER` — **không** chứa chữ `stroke`/`LINE` |
| **`SettingsSectionsProfiles:165`** `card(…, CLEAR, RED)` | ❌ | đối số viền là `RED` — cùng lý do |

**Kết luận thành bất biến** (ghi vào KDoc `card()` + khoá bằng `ZeroBorderContractTest.card va pill khong con tham
so vien`): *một tham số opt-in chỉ là **lời nhắc**; **xoá tham số** là một **cổng biên dịch**.* Cùng lối mà P-bug2
đã đóng (`WorkspaceView.applyEmbedSeam` chuyển 4 field sang `private` ⇒ dựng lại lỗi cũ thì KHÔNG BIÊN DỊCH ĐƯỢC).

Hệ quả thứ hai: **grep theo tên vai màu là phép đếm không đáng tin** khi vai đó xuất hiện ở vị trí đối số tự do.
Con số đúng chỉ có được bằng cổng kiểu (compile) hoặc bằng bài canh quét cả tầng.

---

## 2. Danh sách ĐẦY ĐỦ chỗ đã gỡ viền (13 chỗ vẽ, 12 tệp)

### 2.1 Viền `GradientDrawable.setStroke` — gỡ hẳn

| # | Tệp | Viền cũ | Sau khi gỡ, cái gì gánh |
|---|---|---|---|
| 1 | `KachiThemeDrawables.card()` | **tham số `stroke` XOÁ** | — (cổng biên dịch) |
| 2 | `KachiThemeDrawables.pill()` | **tham số `stroke` XOÁ** | — |
| 3 | `KachiThemeDrawables.gradientSoft()` | `TILE_ON_LINE` (ô thanh nút BẬT) | nền accent bán trong suốt: [ĐO] BẬT vs TẮT **1.98×** tối · **1.59×** sáng |
| 4 | `KachiTopStrip:77` | `LINE_STRONG` — **vạch y=107 owner chỉ đích danh** | nền `BAR_TOP` |
| 5 | `ControlDockView:53` | `LINE_STRONG` — **vạch y=882 owner chỉ đích danh** | nền `BAR` + khe `SLOT_GAP` |
| 6 | `VoiceOverlay:108` | `LINE_STRONG` | nền `BAR_TOP` |
| 7 | `SettingsPanel.paintRail` | `ACCENT` (nhóm đang chọn) | nền `ACCENT_SOFT` |
| 8 | `GroupTileViews.strokeOf` | **bảng tra viền XOÁ** (4 tone) | nền — xem §3.4 |
| 9 | `WorkspaceView` ô trống | gạch **ĐỨT** `EMPTY_LINE` | nền `emptyFill` đổi bậc — §3.1 |
| 10 | `SettingsRows.checkRow` | `MUT2` 2dp (ô tick CHƯA tích) | nền `FIELD_SUNKEN` — §3.2 |
| 11–13 | `SettingsRowsColor.swatchRow` | **ba** viền (vành `INK` ô đang chọn · gạch đứt ô "chưa có màu" · vành `LINE_STRONG` mọi ô) | §3.3 |
| 14 | `AppDrawer.notice` | `AMBER` | nền `AMBER_SOFT` ([ĐO] tách thẻ 1.56/1.36×, chữ 5.52/5.02) |
| 15 | `SettingsSectionsProfiles` nút Xoá | `RED` trên `CLEAR` = **nút viền rỗng** | vai MỚI `RED_SOFT` — §3.5 |

### 2.2 Viền vẽ bằng `Canvas` (KHÔNG phải `setStroke` — grep `setStroke` mù với chúng)

| # | Tệp | Viền cũ | Sau khi gỡ |
|---|---|---|---|
| 16 | `TyreBoardView.cellStroke` | `drawRoundRect(cell, …, nét)` mang **màu trạng thái** | nền ô pha 20 % màu trạng thái (`SEMANTIC_MIX`) — §3.4 |
| 17 | `GridEditorView.frameLine` | `drawRoundRect(box, …, nét)` 4f/2f quanh **từng khung** bố cục | vùng tô nâng `55→96` / `90→150`; "đang chọn" = tô đậm + **tay cầm** đổi cỡ (vốn chỉ hiện ở khung đang chọn) |
| 18 | `ClusterPreviewView` (qua chỗ gọi) | `drawRoundRect(rect, …, nét)` quanh mặt cụm | `line = CLEAR` + mặt đổi sang `FIELD_SUNKEN` — §3.6 |

### 2.3 Nét được GIỮ — **mực vẽ**, không phải khung (khai ở `ZeroBorderContractTest.INK_VIEWS`)

`RingView` · `Pm25GaugeView` (cung đồng hồ) · `CarArtPainter` (hình xe line-art) · `SeatDiagramView` (đường bao
thân xe + đệm/ốp/tựa ghế) · `DoorBoardView` (đường tách hai vùng tô chồng nhau) · `GridEditorView` (**lưới** ô —
thứ người dùng canh theo khi kéo) · `ClusterPreviewView` (vạch chia hai nửa cụm).

Gỡ những nét này không phải "bỏ viền" mà là **xoá mất cái hình**. Mỗi tệp khai kèm lý do; tệp mới dùng nét mà chưa
khai ⇒ bài canh ĐỎ, buộc người sửa nói ra mình đang vẽ hình hay đang vẽ khung.

**Cố ý NGOÀI phạm vi**: `speedbadge/` · `modules/clustercast/` · `VmBubblePlacementView` — ba bề mặt vẽ **trên
cụm** / trên pixel của app khác, đã chạy ổn trên xe, CLAUDE.md §6 cấm đảo thứ tự đường đã chạy tốt ngoài hiện
trường.

---

## 3. ⚠ SÁU chỗ phải xử ĐẶC BIỆT — gỡ viền là **tàng hình**

Owner dặn: *"Nếu một bề mặt trước CHỈ nhìn thấy nhờ viền → thay bằng FILL đủ tương phản (KHÔNG để tàng hình)"*.
[ĐO] quét tương phản từng bề mặt trên nền của chính nó tìm ra **sáu** ca như thế. Mọi con số dưới đây là tỉ số
tương phản WCAG tính bằng máy; ngưỡng *nhìn-ra-được của một mảng lớn* mà dự án đã chấp nhận là **~1.15×** (thẻ bảng
sáng chỉ 1.13× và owner đã chốt GIỮ).

### 3.1 Ô TRỐNG ở màn chính — **1.012×** (tàng hình hoàn toàn, bảng tối)
Nền cũ `emptyFill = DARK_RAMP.at(-1)` = `#131a2e` trên nền màn `#141b30` ⇒ **1.012×**. Cái duy nhất làm nó thấy
được là gạch đứt.
**Xử**: đổi **bậc** của vai (không đổi recipe thang): tối `at(-1)` → **`at(3)`** = **1.326×** · sáng `at(-1)` →
**`at(-2)`** = **1.184×**.
⚠ **Hai bảng chọn bậc NGƯỢC chiều nhau, có chủ đích**: thang tối bước `0.04` nên hạ xuống gần như không tách được
(`at(-2)` chỉ 1.023×); thang sáng bước `0.5` nên nâng lên là **chạm trần trắng** (`at(2..5)` đều `#ffffff`).
Vai `emptyLine` **XOÁ** khỏi bảng màu (nó LÀ cái gạch, không có vai trò thứ hai nào).

### 3.2 Ô TICK chưa tích — trong suốt hoàn toàn
Cũ: `setColor(CLEAR)` + `setStroke(2dp, MUT2)` ⇒ gỡ viền là một ô **trong suốt, không chữ, không nền**.
**Xử**: nền `FIELD_SUNKEN` = **1.22×** tối · **1.34×** sáng. Chọn đúng vai `fieldSunken` chứ không mã mới: một ô
tick trống LÀ một ô lõm chờ được tô, cùng ẩn dụ `SurfaceTone.SUNKEN`.

### 3.3 Ô MÀU (`swatchRow`) — **hai** ca trong một hàng
[ĐO] quét cả 10 ô (5 sơn × 3 màu nhấn, hai bảng) trên nền hàng: **chỉ 2 ô** tàng hình, 8 ô còn lại ≥ 1.66×.

| ca | trước | sau | cách xử |
|---|---|---|---|
| sơn **BLACK** (bảng TỐI) | điểm giữa `#2e343e` trên `#222941` = **1.15×** | **1.85×** | ô sơn nay tô **CHUYỂN SẮC hai đầu THẬT** (`#4a5361`→`#11151c`) thay một điểm giữa. `KachiCarPaint.swatch()` → **`ends()`**; `Swatch` thêm `color2`. **Đúng hơn về mặt dữ liệu**: thân xe vốn LÀ chuyển sắc đó |
| ô *theo ảnh nền* **chưa có ảnh** | `CHIP_OFF` = `#222941`, **trùng đúng byte** với nền hàng = **1.00×** | **1.22×** / **1.34×** | nền đổi sang `FIELD_SUNKEN`, giữ dấu `?` |

Ô **đang chọn** nhận diện bằng **dấu ✓** (mực đã chọn theo tương phản đo được) + tên ở caption — hai dấu hiệu, đủ
không cần vành thứ ba.

### 3.4 Màu NGỮ NGHĨA (WARN/ALERT) — **chuyển sang nền, và phải ĐẬM hơn cũ**
Đây là ca owner nêu riêng: *"màu ngữ nghĩa nếu đang là viền màu → chuyển sang nền/chữ màu (giữ nghĩa, bỏ viền)"*.

- **Ô con nhóm** (`GroupTileViews.fillOf`): trước nói ra bằng **hai** thứ — nền 15 % (`26`) + viền cùng màu 55 %
  (`8C`). Giữ 15 % mà bỏ viền thì tín hiệu tụt còn [ĐO] **1.38×** (hổ phách tối) / **1.25×** (sáng) = **một cảnh
  báo trên màn hình lái xe mờ đi vì một lượt dọn thẩm mỹ**. Nền nay **35 %** (`SEMANTIC_ALPHA = "59"`): [ĐO]
  **2.25×** hổ phách / **1.99×** đỏ (tối) · **1.74×** / **1.83×** (sáng) — **đậm hơn cả cặp nền+viền cũ**, 0 đường kẻ.
- **Ô giá trị bảng lốp** (`TyreBoardView`): viền cũ mang màu trạng thái để *"bánh sai nhìn ra được cả khi chưa đọc
  con số"*. Nay nền ô **pha `SEMANTIC_MIX = 0.20`** màu trạng thái vào `CARD2` — cùng công thức `0x33` của
  `amberSoft`/`redSoft` để ba nền-cảnh-báo không lệch nhau.

### 3.5 Nút "Xoá hồ sơ" — **nút viền rỗng**, gỡ viền là mất luôn cái nút
Cũ: `fill = CLEAR` + viền `RED` + chữ đỏ ⇒ gỡ viền còn **một dòng chữ đỏ trơn**, mất hẳn dấu hiệu "bấm được"
trên một hành động **không hoàn lại được**.
**Xử**: vai MỚI **`redSoft`** (`0x33` + red, cùng công thức `amberSoft`) ⇒ [ĐO] nền tách thẻ **1.45×** tối ·
**1.40×** sáng, chữ `red` trên chính nó **4.57:1** / **4.65:1** (≥ 4.5). ⚠ `0x40` làm chữ tụt còn **4.09/4.25** —
ghi vào KDoc để không ai nâng alpha cho "rõ hơn".

### 3.6 Ô xem-trước cụm — **1.10×**, và là view DÙNG CHUNG
`ClusterPreviewView` lấy mặt `FIELD` ⇒ [ĐO] chỉ **1.10×** trên nền thẻ; gỡ viền là ô biến mất.
**Xử**: mặt → `FIELD_SUNKEN` (**1.22×** / **1.34×**), viền tắt bằng **`line = CLEAR` ở CHỖ GỌI**, không sửa
`onDraw`. Lý do: view này **dùng chung** với màn ClusterNav cũ (`activity_main.xml` → `hero_cast_preview`), mà màn
đó KHÔNG thuộc phạm vi WP này. Sửa trong view = đổi một bề mặt ngoài phạm vi.

---

## 4. VIỆC 2 — lề ngang header + taskbar còn 80 %

| | cũ | mới |
|---|---|---|
| hằng | `Sp.L` = 16dp | **`Sp.EDGE_H` = 13dp** (80 % của 16 = 12.8, làm tròn **lên** 13) |
| pixel @ density 1.5 | 24px | **19px** |
| tỉ lệ thật | — | **19 / 24 = 79.2 %** |

`13` chứ không `12`: 12 trùng đúng bậc `Sp.M` và bậc đó mang nghĩa *lề-trong-thẻ* — đọc code sẽ hiểu sai vai.

### ⚠ Vì sao chỉ có MỘT con số cho cả ba thứ (sai lệch lời giao, có chủ đích)
Lời giao nói *"margin header, taskbar"*. Nhưng thanh trên, **vùng ô** và thanh nút đều là con của **cùng một**
`LinearLayout` gốc ở `KachiHomeActivity`; lề ngang của chúng **LÀ** `paddingLeft/Right` của khung đó. Cho riêng hai
thanh một lề nhỏ hơn thì phải dùng **lề âm**, và hai thanh sẽ **lệch cột** với các ô ở giữa — một mép lệch 3dp đọc
ra như lỗi vẽ, không như thiết kế. Nên hạ đúng lề ngang dùng chung; vùng ô đi theo (nó cũng đang thừa khoảng trắng
ở đúng hai mép ấy).

**Chỉ NGANG** — lề DỌC giữ `Sp.L`. Hệ quả: khung nội dung từ nay **không còn cách đều 4 cạnh** như S1b
(2026-09-14) đã chốt. Đây là sai lệch tường minh với S1b, ghi ở KDoc `Sp.EDGE_H`.

**An toàn cho cửa sổ app on-car**: `LauncherWindows.absoluteSlotRect` lấy vị trí bằng `workspace.getLocationOnScreen`
(không phải một hằng lề) ⇒ cửa sổ freeform tự theo lề mới, không có bẫy P-bug2.

---

## 5. Test — [ĐO]

### 5.1 Bộ test
`JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test --rerun-tasks --continue`
⇒ **5 module · 73/73 task executed · BUILD SUCCESSFUL · 5085 test / 0 đỏ / 0 lỗi / 0 bỏ qua** (đếm từ JUnit XML).

| module | tests |
|---|---|
| `app/testDebugUnitTest` | 1231 |
| `app/testVehicleTestUnitTest` | 1243 |
| `core/test` | 2429 |
| `car-integration/test` | 61 |
| `offcar-planner/test` | 99 |
| `vehicle-contracts/test` | 22 |

Mốc trước (1.85 / WP1 lượt 2) = **5073** ⇒ **+12** = 6 bài mới × 2 biến thể `:app` (5 bài
`ZeroBorderContractTest` + 1 bài `ThemePaletteContractTest` tách ra).

### 5.2 Bài canh MỚI — `ZeroBorderContractTest` (5 bài)

Vì sao cần bài riêng dù `SurfaceMaterialContractTest` đã cấm `setStroke` trong `surface()`: **bài kia chỉ soi thân
MỘT hàm**. [ĐO] lượt 2: `surface()` sạch viền, bài đó XANH, mà màn chính **vẫn còn 27 cột/hàng sáng 1px** ở dải
thanh trên + thanh nút — viền nằm ở *chỗ khác*. Tức **phạm vi bài canh nhỏ hơn phạm vi lời hứa**, đúng họ lỗi
*"bài canh bỏ sót đúng thứ nó sinh ra để bắt"*.

1. `khong con setStroke nao trong tang ve launcher` — **0 lần**, **không có danh sách miễn trừ** (một `setStroke`
   trên `GradientDrawable` chỉ làm được đúng một việc: kẻ đường bao quanh mép).
2. `net ve chi ton tai o cac view line art da khai` — mọi `Paint.Style.STROKE` phải có tên trong `INK_VIEWS` **kèm
   lý do**. Đây là nửa còn lại: `canvas.drawRoundRect(rect, r, r, paintNét)` cho đúng cùng một cái khung.
3. `danh sach INK_VIEWS khong co muc chet` — chống mục rữa (lệ `SettingsCatalog.NOT_SETTINGS`).
4. `thanh tren va thanh nut khong con vien` — ghim **hai chỗ gọi** owner chỉ đích danh, để thông điệp nói ngay
   *thanh trên* / *thanh nút*.
5. `card va pill khong con tham so vien` — khoá cổng biên dịch ở §1.

**Phạm vi quét** = gói `launcher/` **cộng** 3 view tự vẽ được nhúng vào Cài đặt (`comfort/SeatDiagramView` ·
`comfort/Pm25GaugeView` · `ui/ClusterPreviewView`). Ba tệp đó nằm ngoài `launcher/` nên phép quét theo thư mục của
`SurfaceMaterialContractTest` **không chạm tới** — nhưng người dùng nhìn thấy chúng **bên trong** màn Cài đặt của
Kachi, nên với owner chúng LÀ giao diện launcher.

### 5.3 Bài canh ĐẢO CHIỀU (4 bài) — *"viết lại theo ý định bỏ hẳn"*

| bài | trước | sau |
|---|---|---|
| `ThemePaletteContractTest.vien ket cau dat 3 to 1` | **ĐÒI** `emptyLine trên emptyFill ≥ 3:1` (tức đòi **CÓ** một cái gạch) | vế `emptyLine` **bỏ**; `lineStrong` giữ nguyên |
| `ThemePaletteContractTest.o trong tach duoc khoi nen bang mau` (**MỚI**) | — | đòi `emptyFill ÷ bg ≥ 1.15×`. **Bỏ bài cũ mà không viết bài này là đánh mất một bất biến** — tính chất *"người dùng thấy được chỗ đặt app"* vẫn phải có, chỉ **đổi chân** từ viền sang bước sáng |
| `SurfaceContrastContractTest` bảng sinh | hàng `emptyLine trên emptyFill` ngưỡng 3.0 | hàng **`emptyFill ÷ bg`** ngưỡng 1.15 — [ĐO] bảng sinh lại: tối **1.33** ✅ · sáng **1.18** ✅ |
| `GroupTileWiringContractTest.mau nen o con…` | **ĐÒI** `fun strokeOf(` tồn tại | **CẤM** `strokeOf` mọc lại **+** đòi `SEMANTIC_ALPHA` tồn tại **+** đòi nền cảnh báo **đậm hơn** nền "đang bật" (đọc cả hai alpha từ nguồn rồi so) |

### 5.4 ⚠ Bẫy cắt vùng — bắt được **2 lần**, cả 2 lần NỔ đúng chỗ
`SourceRoots.body` ném khi mốc không tồn tại, nên khi tôi xoá `strokeOf` và dời `setGlassReal`, hai bài
(`GroupTileWiringContractTest` · `ClusterNavBridgeWiringContractTest`) **đỏ với thông điệp rõ ràng**
*"không tìm thấy 'fun strokeOf(' — test đang quét vùng KHÔNG tồn tại (quét tràn = test giả)"* — thay vì âm thầm
quét tràn rồi xanh. Đây là bản vá của lượt 2 đang làm đúng việc; ghi lại vì nó là bằng chứng helper đó đáng giá.

### 5.5 Thử phá — **3/3 đỏ đúng chỗ** (hoàn nguyên kiểm bằng sha256)
Sao lưu **theo tệp** (`/tmp/wp1-zeroborder-backup/`), KHÔNG `git checkout` (cây chưa commit).

| # | phép phá | kết quả |
|---|---|---|
| 1 | thêm lại `setStroke(HAIRLINE, LINE_STRONG)` vào `ControlDockView` | **2 bài đỏ** |
| 2 | thêm lại tham số `stroke` + `setStroke` cho `card()` | **2 bài đỏ** |
| 3 | hoàn nguyên `emptyFill` về `at(-1)` (bậc tàng hình) | **1 bài đỏ** |

Hoàn nguyên khớp sha256 cả 3 tệp ⇒ tests XANH lại.

---

## 6. Ảnh + số đo — `docs/diagnostics/ux-overhaul-2026-09-20/`

`emulator-5554` **API 29** (khớp xe DiLink) · **1920×1080 · density 240** · bản debug build từ mã cuối. Ba tệp
**ghi đè**: `after-home-dark.png` · `after-home-light.png` · `after-settings-dark.png`.

| ảnh | độ chói TB | chủ đề | vạch 1px: THANH TRÊN / VÙNG Ô / THANH NÚT |
|---|---|---|---|
| `after-home-dark.png` | **38.5** | TỐI (< 60) ✅ | **0 / 0 / 0** (ngang) · **0 / 0 / 0** (dọc) |
| `after-home-light.png` | **240.8** | SÁNG (> 200) ✅ | **0 / 0 / 0** · **0 / 0 / 0** |
| `after-settings-dark.png` | **42.7** | TỐI ✅ | **0 / 0 / 0** · **0 / 0 / 0** |

Bộ đo: `scripts/design/measure-shot.py` (mới). Tiêu chí *vạch 1px* giống lượt 2: một hàng/cột sáng hơn **cả hai**
hàng/cột kề ≥ 12/255 trên một đoạn liên tục ≥ 200px (để không đếm chữ/icon).

### 6.1 ⚠⚠ CHỨNG MINH BỘ DÒ **KHÔNG MÙ** — phép đo thứ tư
*"0 vạch"* là vô nghĩa nếu bộ dò luôn trả 0. Nên tôi **thêm lại đúng một viền** (`ControlDockView`), **build lại,
cài lại, chụp lại**, và đo:

```
[DOCK y 880..1060]  vạch NGANG 1px = 2 [(882, 1830), (1055, 1830)]   vạch DỌC 1px = 2 [19, 1900]
```

Nó tìm ra **đúng** vạch `y=882` dài `1830px` mà lượt 2 đã [ĐO] độc lập (`y=882`, `x 50..1868`, 1819px). Hai dải kia
vẫn 0. ⇒ bộ dò hoạt động, và *"0 vạch"* ở §6 là số thật. Sau đó hoàn nguyên (sha256 khớp) + build/cài/chụp lại.

### 6.2 Lề ngang — đo bằng pixel, hai đường độc lập
- **Trực tiếp**: mép trái mảng thanh nút ở `y=1000` = **x=19** ở **cả hai** bảng (`19.5px` = 13dp × 1.5, cắt xuống 19).
- **Qua phép phá §6.1**: viền thanh nút hiện ở **x=19 và x=1900** ⇒ lề trái 19, lề phải `1919 − 1900 = 19` — **cân
  đối**, và khớp con số trực tiếp.
- Lượt 2 [ĐO] viền cũ ở **x=24**. ⇒ **24 → 19 = 79.2 %** ≈ 80 % owner yêu cầu.

### 6.3 ⚠ Ba ảnh ở §6 KHÔNG chạm tới ca "ô trống" — đã đo riêng
[ĐO] quét màu vùng ô của `after-home-dark.png`: chỉ **54** pixel gần `emptyFill` mới ⇒ **màn chính hiện không có ô
trống nào** (bố cục `THREE`, cả 3 ô đều có widget). Tức ba ảnh kia **không chứng minh** được §3.1 — ghi ra thay vì
để con số 1.33× chỉ là phép tính.

Nên đo riêng: tạm để trống `slot_2` (sao lưu prefs **phía host** trước, theo bài học `.bak` của lượt 2), chụp lại
bảng TỐI:

| phép đo | kết quả |
|---|---|
| pixel mang `emptyFill` mới `#293149` | **29 397** (so với 54 khi không có ô trống) ⇒ ô trống render đúng vai mới |
| nền màn đo tại chỗ (`x=5, y=500`) | `#131c31` |
| **tỉ số ô trống ÷ nền màn, ĐO TRÊN ẢNH** | **1.316×** (phép tính từ bảng màu cho 1.326× — khớp) |
| vạch 1px ở cả ba dải | **0 / 0 / 0** ⇒ gạch đứt đã hết thật |

Máy ảo đã **trả về trạng thái gốc**: `slot_2 = widget:w_pm25`, `theme_choice = light`, `theme_mode = DAY`;
`kachi_workspace.xml` **giống hệt** bản sao lưu đầu phiên (diff rỗng), **0** tệp `.bak`.

---

## 7. Vai màu — thêm 1, xoá 1

| vai | việc | lý do |
|---|---|---|
| **`redSoft`** (MỚI, cả 2 bảng) | `#33ff8fa0` tối · `#33b32439` sáng | nền nút nguy hiểm — §3.5. Cùng alpha `0x33` với `amberSoft` để ba nền-cảnh-báo không lệch |
| **`emptyLine`** (XOÁ) | gỡ khai + 2 hạt giống + getter `KachiTheme.EMPTY_LINE` + 2 phép kiểm + 1 hàng bảng sinh | nó **LÀ** một cái viền, 0 chỗ vẽ, và **không có vai trò thứ hai nào** |

**GIỮ (không xoá) dù 0 chỗ vẽ**, mỗi cái một lý do khác `emptyLine`:
`tileOnLine` (còn đi qua `KachiPaletteDerive.rc` khi người dùng đổi màu nhấn) · `surfLine` (còn được bài canh ĐO để
con số tách-thẻ-khỏi-nền hiện ra cho owner) · `line` (mã màu chung) · `lineStrong` (**vẫn được vẽ** — nhưng là
**mực nét** của `SeatDiagramView`, không phải khung của thẻ).

---

## 8. Nợ / sai lệch ghi rõ

1. **CHƯA commit** (theo lời giao). Cây làm việc mang WP1 lượt 1 + 2 + 3.
2. **Trần 500 dòng**: đã kéo **4 tệp** vừa vượt trần về lại ≤ 500 bằng cách **tỉa KDoc của chính mình**
   (`KachiPalette` 516→**500** · `GroupTileViews` **500** · `KachiTheme` **500**) và **dời** `glassReal`/
   `setGlassReal` sang `ClusterNavBridgeHome.kt` (cầu chính `ClusterNavBridge` đã **499** dòng trước WP1 ⇒ 0 chỗ;
   nay **499**). Còn **3 tệp vượt trần từ TRƯỚC HEAD**, delta của lượt này gần 0:
   `Prefs.kt` 536→543 (+7, là công tắc glass của WP1 lượt 1, không phải việc border) · `KachiHomeActivity` 536→537
   (+1) · `AppDrawer` 509→510 (+1). **Nợ có trước, không phải nợ mới.**
3. **Bảng SÁNG vẫn tách thẻ chỉ 1.13×** — owner đã chốt GIỮ (mục 6.1 của lượt 2). Không đụng thang chói bảng sáng;
   duy nhất `emptyFill` đổi **bậc** (bắt buộc, §3.1).
4. **KHÔNG soát độc lập**: phiên này không có công cụ sinh sub-agent ⇒ cũng không đọc được ảnh bằng mắt máy
   (`image-reading-subagent.md`). Bằng chứng hình ảnh là **phép đo pixel**, không phải mô tả thị giác. **Owner nhìn
   ảnh là bước xác nhận cuối.**
5. **KHÔNG đụng WP2–WP9.**
6. 🚗 **chưa đo trên xe.** Cần owner nhìn trên cụm: (a) ô trống bảng tối ở bậc `at(3)` có sáng quá so với khay ô đã
   có nội dung không; (b) nền cảnh báo 35 % có gắt không dưới nắng; (c) lề 19px có còn dư/đã sát quá.
7. Quét bảo mật diff: **CLEAN** (0 secret · 0 IP nội bộ · 0 đường dẫn `/Users/<tên>`).
