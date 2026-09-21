# UX-OVERHAUL WP1 · iteration — GỠ HẲN MÉP KÍNH + VIỀN khỏi `KachiTheme.surface()`

> **Trạng thái**: off-car DONE · **Ngày**: 2026-09-20 · **CHƯA commit** (chờ owner duyệt LOOK)
> **Nguồn yêu cầu**: owner xem ảnh WP1 bản sáng cùng ngày → *"better, bị bug gạch trên đầu mỗi khung, bỏ viền đi luôn"*
> Tiếp nối `docs/_handoff/ux-wp1-done.md` · Spec `docs/specs/kachi-ux-overhaul.html` §R1.1

---

## 1. Việc đã làm

`KachiTheme.surface()` nay **chỉ** còn: chuyển sắc DỌC (`Orientation.TOP_BOTTOM`) + (tuỳ chọn) một lớp sắc lĩnh vực
gom bằng `LayerDrawable`. **0 `setStroke` · 0 mép ghim cạnh · 0 blur/bóng/elevation.** Bốn `SurfaceTone` phân biệt
nhau **chỉ bằng MÀU FILL** qua `surfacePair(tone)`.

### 1.1 Vì sao lần này CẤM hình dạng, không khoá cường độ

Bản WP1 sáng 2026-09-20 đã thử đúng cách "khoá cường độ": mép được phép tồn tại nhưng phải 1px + bán trong suốt +
đi cặp sheen/shade (bevel). Nó **vẫn** bị owner gọi là *gạch*. Cộng `surfEdge`/`surfOnEdge` (Pass-4 → Pass-5 gỡ),
đây là **lần thứ BA** cùng một họ lỗi. Kết luận ghi thành bất biến trong KDoc `surface()`:

> Một hình chữ nhật ghim vào **cạnh** thẻ là một VẠCH ở **mọi** alpha và **mọi** độ dày. Cái sai là **HÌNH DẠNG**,
> không phải cường độ ⇒ chữa bằng BỎ, không bằng hạ alpha. Chiều nổi do chuyển sắc DỌC gánh một mình.

---

## 2. Test đã ĐỔI (2 bài, đảo chiều)

| Bài | Trước (WP1 bản đầu) | Sau (bản này) |
|---|---|---|
| `SurfaceContrastContractTest.mep kinh la 1px ban trong suot khong phai gach` → **đổi tên** `khong con mep hay vien tren be mat` | **ĐÒI** `GLASS_SHEEN` + `GLASS_SHADE` trong thân `surface()`; đòi `setLayerHeight(_, hair)`; đòi alpha < `ff` | **CẤM** 7 token: `GLASS_SHEEN` · `GLASS_SHADE` · `setStroke` · `Gravity.TOP` · `Gravity.BOTTOM` · `setLayerHeight` · `setLayerGravity`. Thêm: 4 vai màu mép (`surfEdge`/`surfOnEdge`/`glassSheen`/`glassShade`) không được mọc lại ở `KachiPalette` |
| `SurfaceMaterialContractTest.be mat loi co du lop va chuyen sac DOC` → **đổi tên** `be mat loi khong con vien hay mep` | **CHO PHÉP** mép 1px, chỉ chặn lớp dày (`hair * 2`+) | **CẤM** `setStroke` · `Gravity.TOP/BOTTOM` · `setLayerHeight` · `setLayerGravity` · `setLayerInset`. GIỮ: đúng 1 `TOP_BOTTOM`, 0 `TL_BR`, vẫn cho `LayerDrawable` (cho lớp tint) |

**Ràng buộc an toàn KHÔNG nới**: mọi phép WCAG giữ nguyên; luật `0 blur 0 shadow 0 elevation` giữ nguyên (ngoại lệ
`KachiGlassMode.kt` không đổi); `:core` vẫn thuần.

### 2.1 Bài thứ ba phải sửa — **bẫy cắt vùng**, không phải đổi luật

`SurfaceMaterialContractTest.nhanh SUNKEN giu phang va thoat som` cắt vùng bằng `substringBefore("val active")`.
WP1 gỡ nhánh mép ⇒ biến `val active` **biến mất**, mà `substringBefore` **trả nguyên phần còn lại** khi không thấy
mốc ⇒ vùng quét trùm cả nhánh gradient bên dưới và bài **đỏ ở một dòng chẳng ai hiểu vì sao**. Đúng họ lỗi *"44 phép
cắt vùng"* đã ghi trong project-context. Đã sửa: mốc `SurfaceTone.SUNKEN) return` → `val base`, và **khẳng định cả
hai mốc tồn tại trước khi cắt** (mốc mất ⇒ NỔ ngay, không âm thầm nới vùng quét). Thêm một phép: ô lõm cũng không
có viền.

---

## 3. Dead code đã GỠ (grep xác nhận 0 chỗ dùng trước khi gỡ)

| Thứ | Ở đâu | Ghi chú |
|---|---|---|
| `KachiTheme.GLASS_SHEEN` / `GLASS_SHADE` getter | `KachiTheme.kt` | 2 getter |
| `KachiPalette.glassSheen` / `glassShade` | `KachiPalette.kt` — khai + 2 hạt giống (`DARK` `#4dffffff`/`#40000000`, `LIGHT` `#59ffffff`/`#14000000`) | **Phải gỡ cùng lúc với getter**: `ThemePaletteContractTest.moi vai mau tra duoc qua KachiTheme` đòi mọi vai `String` của `KachiPalette` có getter ⇒ giữ một bên là đỏ |
| `import android.view.Gravity` | `KachiTheme.kt` | dư sau khi gỡ mép — [ĐO] `grep Gravity KachiTheme.kt` = 0 dòng mã |

**Cố ý KHÔNG gỡ `surfLine`** (dù `surface()` không còn vẽ nó): nó vẫn là chỗ tra cho đường **opt-in**
`card(stroke = …)`/`pill(stroke = …)`, và bài canh còn đo nó để con số tách-thẻ-khỏi-nền hiện ra cho owner quyết
(xem §6). Gỡ nó sẽ kéo theo đổi thang chói bảng sáng = ngoài phạm vi lượt này.

---

## 4. Comment / KDoc đã cập nhật

| Tệp | Chỗ | Nội dung mới |
|---|---|---|
| `KachiTheme.kt` | `SurfaceTone.NEUTRAL` | "chỉ chuyển sắc DỌC, không viền, không mép" |
| `KachiTheme.kt` | `SurfaceTone.WELL` | ghi rõ **đã gỡ viền `LINE_STRONG`** mà tone này từng có; khay tách nền chỉ bằng bậc sáng |
| `KachiTheme.kt` | KDoc `surface()` | viết lại: bất biến "lần thứ BA", vì sao cấm hình dạng thay vì hạ alpha, trỏ tới 2 bài canh mới |
| `KachiTheme.kt` | chỗ gỡ 2 getter | để lại lý do tại chỗ (chống mọc lại dưới tên khác) |
| `KachiPalette.kt` | KDoc vai hình xe | gỡ 2 `@property` mép + ghi lý do |
| `KachiPalette.kt` | KDoc `surfLine` | ⚠ ghi rõ **không còn được `surface()` vẽ** + hệ quả đo được ở bảng SÁNG |
| `KachiPaletteSeeds.kt` | comment `DARK_RAMP` · `LIGHT_RAMP` | "glass" đến từ BỎ viền + BỎ mép + gradient + accent — không từ đổi thang nền; + cảnh báo 1.13× |
| `KachiGlassMode.kt` | KDoc glass GIẢ | "0 mép, 0 viền, 0 blur runtime" |
| `Prefs.kt` | comment `glassReal` | bỏ "mép kính" (giữ 1 dòng để không nới thêm tệp đã quá 500) |
| `SurfaceContrastContractTest.kt` | KDoc lớp + 3 bài + sinh bảng §6.4 | bỏ "mép sáng" khỏi mô tả chất liệu; bảng đo nay **nói rõ** chân viền không còn được vẽ |

---

## 5. [ĐO] Kết quả kiểm

### 5.1 Test
`JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test --rerun-tasks --continue`
⇒ **5 module · 73/73 task executed · 5073 test / 0 đỏ / 0 lỗi / 0 bỏ qua** (đếm từ JUnit XML).
Không đổi số so với mốc 1.85 (5073) — hai bài đảo chiều là thay 1-đổi-1, không thêm/bớt bài.

| module | tests |
|---|---|
| `app/testDebugUnitTest` | 1225 |
| `app/testVehicleTestUnitTest` | 1237 |
| `core/test` | 2429 |
| `car-integration/test` | 61 |
| `offcar-planner/test` | 99 |
| `vehicle-contracts/test` | 22 |

### 5.2 Thử phá (2 phép, **2/2 đỏ đúng chỗ**)

Sao lưu **theo tệp** (`/tmp/wp1-edges-backup/`), KHÔNG dùng `git checkout` — cây đang chưa commit.

| # | Phép phá | Kết quả |
|---|---|---|
| 1 | thêm lại `setStroke(dpi(ctx, HAIRLINE), c(SURF_LINE))` vào `base` | **cả 2 bài ĐỎ** · thông điệp nêu đúng `[setStroke]` |
| 2 | thêm lại lớp mép 1px ghim đỉnh (`setLayerGravity(1, Gravity.TOP)` + `setLayerHeight(1, hair)`) | **cả 2 bài ĐỎ** · nêu đủ `[Gravity.TOP, setLayerHeight, setLayerGravity]` |

Hoàn nguyên kiểm bằng **sha256**: `e0073d83080d7985620d895e1a97044460c94b7e01b388a457ecce027efa7f72` (khớp bản
trước khi phá), sau đó 2 bài XANH lại.

### 5.3 Trần dòng
`KachiTheme.kt` **499** · `KachiPalette.kt` **500** (≤ 500 ⇒ đạt; ⚠ sát trần, WP2 thêm vai nữa là phải tách)
· `Prefs.kt` 543 (**nợ có trước**, HEAD đã 536; lượt này thêm **0** dòng).

---

## 6. ⚠⚠ PHÁT HIỆN cần owner quyết

### 6.1 Bảng SÁNG: thẻ nay gần như hoà vào nền (1.13×)

[ĐO] thẻ trắng `#ffffff` trên nền `#eef1f8` chỉ tách **1.13×**. Trước WP1, việc tách do `surfLine` (viền thật
3.28:1) gánh — **nay không còn viền nào được vẽ**. Bài canh `the chat lieu tach duoc khoi nen o ca hai bang` VẪN
xanh, nhưng nó xanh **bằng một chân mà màn hình không còn dùng** — đã ghi thẳng vào KDoc của bài đó, không giấu.

Cố ý **không** tự sửa: hai cách chữa duy nhất là (a) hạ sàn 1.15 = nới luật an toàn (cấm), hoặc (b) đổi thang chói
bảng sáng = đúng thứ WP1 đã chốt GIỮ NGUYÊN (lớp che thẻ-kính-trên-ảnh ở tone COOL chỉ dư ~0.01 so sàn 4.5).
**Cần owner chốt**: giữ 1.13× (phẳng nhưng sạch viền), hay cho bảng sáng một ngoại lệ?

### 6.2 Còn ĐÚNG 2 đường kẻ trên màn chính — **KHÔNG phải từ `surface()`**

[ĐO ảnh `after-home-dark.png`] hai vạch 1px chạy gần hết chiều ngang:

| y | bề rộng | màu | nguồn |
|---|---|---|---|
| 107 | x 45..1874 (1830px) | `rgb(100,106,121)` trên nền 32 | **đáy thanh trên** — `KachiTopStrip.kt:77` `card(…, BAR_TOP, LINE_STRONG)` |
| 882 | x 50..1868 (1819px) | `rgb(99,103,117)` trên nền 31 | **đỉnh thanh nút** — `ControlDockView.kt:53` `setStroke(HAIRLINE, LINE_STRONG)` |

**Quy kết bằng đo, không suy luận**: quét cột sáng 1px theo từng dải —
`TOP-STRIP (y 30..104)` = **15** cột (viền thanh + viền các pill) · `WORKSPACE (y 112..878)` = **0** cột ·
`DOCK (y 886..1040)` = **12** cột. Cả hai dải có viền tại `x=24` (mép trái của chính thanh đó). Vùng
`surface()` vẽ (workspace) có **0 pixel viền cả ngang lẫn dọc** ⇒ *"gạch trên đầu mỗi khung"* đã hết thật.

**KHÔNG tự gỡ 2 vạch này**: lời giao lượt này scope ở *"khỏi `KachiTheme.surface()`"*. Nhưng chúng thuộc **cùng
R1.1** (*"Bỏ HẾT border stroke (thẻ/khối/nút/widget)"*) và là hai đường kẻ dễ thấy nhất còn lại. **Xin owner chốt**:
gỡ luôn (2 dòng, 2 tệp) hay giữ để thanh trên/thanh nút không trôi vào hình nền?

---

## 7. Ảnh — ĐÃ CHỤP LẠI (ghi đè), chủ đề kiểm bằng độ chói

`docs/diagnostics/ux-overhaul-2026-09-20/` · `emulator-5554` **API 29** (khớp xe DiLink) · 1920×1080 · bản debug
vừa build.

| tệp | độ chói TB | xác nhận |
|---|---|---|
| `after-home-dark.png` | **32.1** | TỐI thật |
| `after-settings-dark.png` | **35.4** | TỐI thật |
| `after-home-light.png` | **240.6** | SÁNG thật |

Ba tệp cũ `after-settings-display-*.png` / `before-home.png` **giữ nguyên** (1280×720, lượt trước) để so tham chiếu.

**Ảnh khớp đúng bản mã cuối** — [ĐO] chụp lại sau lượt sửa chú thích cuối: khác biệt pixel duy nhất là hộp
`(84,57)-(96,76)` = **đồng hồ** trên thanh trên; mọi pixel khác **giống hệt**.

### 7.1 ⚠ Sai sót phép đo của tôi (đã phát hiện và sửa trong phiên)

Lượt chụp đầu, tôi sao lưu prefs **trên máy** thành `kachi_workspace.xml.bak`. Đó đúng là **ô WAL của chính
`SharedPreferencesImpl`**: Android thấy `<tên>.xml.bak` thì **phục hồi nó lên đè** tệp chính ở lượt nạp sau ⇒ bản ghi
`NIGHT` của tôi bị ghi đè bằng `DAY`, và hai ảnh *"dark"* đầu thực chất là **SÁNG** (độ chói 240). Bắt được vì đo độ
chói chứ không tin nhãn tệp. Sửa: xoá `.bak` trên máy, sao lưu **phía host**, và đặt **CẢ HAI** store —
`kachi_workspace/<hồ sơ>__theme_mode` **và** `clusternav_theme/theme_choice` (cái thứ hai là cái `attachBaseContext`
thật sự đọc). Máy ảo đã **trả về trạng thái gốc** (`DAY`/`light`, 0 tệp `.bak` sót, đã dọn `/data/local/tmp`).

---

## 8. Chưa làm / còn lại

- **CHƯA commit** (theo lời giao). Cây làm việc mang cả WP1 + lượt này.
- **CHƯA soát độc lập**: phiên này không có công cụ sinh sub-agent ⇒ cũng không đọc được ảnh bằng mắt máy
  (`image-reading-subagent.md`). Bằng chứng hình ảnh vì thế là **phép đo pixel** (§6.2, §7), không phải mô tả thị
  giác. Owner nhìn ảnh là bước xác nhận cuối.
- **KHÔNG đụng** WP2–WP9.
- 🚗 chưa đo trên xe.
