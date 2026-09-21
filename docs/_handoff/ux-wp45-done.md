# UX-OVERHAUL · WP4 + WP5 — vị trí item trong hai thanh + sizing (80/85 · 75/70)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-20 · **Mục đích**: Bàn giao lượt WP4 (cho người dùng chọn vị trí
> từng item BÊN TRONG thanh trên + thanh nút) và WP5 (hạ cỡ hai thanh theo mốc owner) — code + test xong off-car,
> ảnh máy ảo API 29 đã chụp. **CHƯA commit.**
> Spec: `docs/specs/kachi-ux-overhaul.html` §WP4 · §WP5 · §Reviewer Log.

---

## 1. Owner giao gì, làm được gì

| Mốc owner | Kết quả | Bằng chứng |
|---|---|---|
| **WP4 · R4.1** chọn vị trí từng item **bên trong** header + taskbar; KHÔNG đổi vị trí thanh; taskbar 4 cạnh giữ nguyên | ✅ hai danh sách sắp chỗ ở *Cài đặt › Thanh trạng thái & thanh nút*; thứ tự lưu **theo HỒ SƠ** | ảnh + cây trợ năng (§4) |
| **WP5 · R5.1** taskbar kích thước **80 %**, nội dung **85 %** | ✅ thanh 116→93dp (ngang) · 124→99dp (dọc); ô 84×86→71×73 · 100×70→**83**×60 | [ĐO] ảnh: 139px=92.7dp · 148px=98.7dp · ô dọc **60.0dp** |
| **WP5 · R5.2** header cao **75 %**, nội dung **70 %**, nút App+Voice+profile **70 %**, thông tin giữ nguyên | ✅ thanh 56→42dp · nút 48→34dp · đĩa hồ sơ 32→22dp; đồng hồ/ngày/chip **không đổi cỡ chữ** | [ĐO] ảnh: pill nhấn **34.0dp** cao · đĩa **22.0×22.0dp** · nội dung canh giữa y=55 (tâm của 24..87) |

---

## 2. WP4 — kiến trúc

**Một phép sắp lại cho CẢ HAI thanh.** Thanh trên sắp `HeaderItem`, thanh nút sắp mã khả năng (`String`) — hai loại
dữ liệu, một phép. `:core BarOrder.move(items, item, delta)` là chỗ duy nhất biết luật kẹp biên; `HeaderLayout.move`
và `DockConfig.moveEnabled` chỉ uỷ quyền. Viết hai bản là dựng sẵn bẫy hai-bản-sao mà dự án đã trả giá bốn lần
(`unitPrefs` 4 bản · `customLayout` 2 bản).

**Tính chất mà tầng vẽ dựa vào**: không dời được ⇒ trả về **CHÍNH** vật cũ (`assertSame`, không phải `assertEquals`).
Nhờ đó nút ◀/▶ ở hai đầu danh sách biết phải làm mờ mà không nhân đôi luật, và không có lượt ghi bền vô nghĩa.

### Thanh trên
- `:core HeaderItem` — **6 vật**: `CLOCK` (đồng hồ **+** ngày, một vật) · `CHIPS` (cả hàng chip, một vật) · `VOICE` ·
  `APPS` · `SETTINGS` · `PROFILE`. Mang `Localized` (nhãn VI+EN) + cờ `info` (thông tin vs nút — WP5 đọc nó).
- `:core HeaderLayout(order)` — **hoán vị ĐỦ**, `init { require }`. Vắng một vật thì vật đó *biến mất khỏi thanh mà
  không bề mặt nào nói ra*; ẩn/hiện là việc riêng và đã có chỗ riêng (chip: bộ chọn chip · nút nói: công tắc *Nút mic*).
- Khoá bền `header_order` **theo hồ sơ** (`ProfileScope.LAUNCHER_LAYOUT_SUFFIXES`), chuỗi **tên hằng** đọc được bằng mắt.
- `KachiTopStrip`: `build()` dựng view vào **bảng `items[HeaderItem]`**; `place(layout)` mới quyết chỗ. `setLayout()`
  chỉ **sắp lại** (`removeAllViews` + gắn lại) — KHÔNG dựng lại, nên chữ trên chip và tên hồ sơ không mất, và không
  tra/tint lại drawable mỗi cú bấm (việc mà bản vá [SOÁT P2-9] đã dọn khỏi đường nóng).
- `lpFor(item)` phụ thuộc **LOẠI vật, không phụ thuộc chỗ**: hàng chip vẫn là phần co giãn duy nhất
  (`0dp + weight 1`), mọi vật khác `WRAP_CONTENT` ⇒ **không tổ hợp thứ tự nào làm thanh tràn**.
- `HeaderLayout.chipsAlignEnd` (`:core`): hàng chip là phần co giãn nên khoảng trống nằm *bên trong* nó. Căn END ⇒
  trống **trước** chip (mặc định, giữ y hình dạng 1.85); chip đứng **đầu** thanh ⇒ căn START, nếu không thanh mở đầu
  bằng một quãng trống.

### Thanh nút
Thứ tự đã là `DockConfig.enabled` từ RW0, nhưng tới 1.85 **không có bề mặt nào sắp lại được nó** (`DockSelection.apply`
cố ý nối mã mới vào cuối). WP4 vá đúng chỗ đó — **không** khoá lưu mới.

⚠⚠ `bars_dock_order` khai `prefKey = null` là **BẮT BUỘC**: `SettingsCatalog` ép bất biến *"một khoá chỉ thuộc ĐÚNG
một mục"*. [ĐO] khai `dock_enabled` lần thứ hai ⇒ `ExceptionInInitializerError` *"khoá hai chủ"*, **45 bài đỏ**. Đúng
tiền lệ `home_grid_editor`: một mục **sửa** giá trị mà mục khác **sở hữu**.

### ⚠ Sai lệch có chủ ý: nút ◀ ▶, KHÔNG kéo-thả
Lời giao ghi *"sắp/**kéo** thứ tự-vị trí"*. Chọn nút bấm, ba lý do:
1. **Kéo trong vùng cuộn là cử chỉ mơ hồ** — trang Cài đặt là `ScrollView` dài (R4: ≤ 2 màn cuộn), nên kéo dọc phải
   phân xử với cuộn trang; cách duy nhất phân xử được là giữ-rồi-kéo = một cử chỉ **không nhìn thấy được**, trong xe,
   cho một việc làm một lần rồi quên.
2. **Nút bấm ĐO được off-car** — kết quả một cú bấm là một `HeaderLayout`/`DockConfig` mới, kiểm bằng test thuần.
3. **Đích chạm** — hai nút 48dp rõ hơn một vùng kéo trên màn cảm ứng rung theo đường.

`BarOrder.move` nhận **delta bất kỳ** (không kẹp ±1) nên nối thêm kéo-thả về sau **không** phải viết lại luật — chỉ
thêm một chỗ gọi. **Xin owner xác nhận hướng này.**

---

## 3. WP5 — sizing

### Thang tách hai tệp
`KachiSpace.kt` đã **496 dòng** trước khi WP5 thêm một dòng ⇒ chạm trần 500 (CLAUDE.md §4.1). Cắt **theo VAI** (lệ
`ControlTileFactory` → `TileSize.kt` ở WP2): `KachiSpace` = thang dùng chung cho mọi bề mặt · **`KachiSpaceBars.kt`
(`object KachiBars`)** = cỡ của **hai khối bố cục** — và chúng **suy lẫn nhau** (bề dày thanh = ô + lề ô + lề thanh),
nên phải đứng cạnh nhau để không ai đổi một nửa của một phép trừ.

Hệ quả: hai bài canh phải **nới** — `SpacingScaleContractTest` nhận `Bars.`/`KachiBars.` là chỗ hợp lệ của hằng cỡ,
và phép kiểm lý-do-tại-chỗ quét **cả hai** tệp. Nới mà không ép lại điều kiện thì lần sau ai cũng có thể dựng
`KachiSpaceXxx.kt` rồi nhét số trần + mã vẽ vào ⇒ thêm bài **`KachiSpaceBars chi duoc khai hang`**: 0 lời gọi hàm
đổi dp · 0 import Android · 0 `fun` · ≥ 8 `const val`. Net: guard **mạnh hơn** trước.

### Bốn hằng phải đổi CÙNG LÚC, nếu không ô bị cắt IM LẶNG
| Hằng | Trước | Sau | Vì sao |
|---|---|---|---|
| `DOCK_THICK` / `DOCK_WIDE` | 116 / 124 | **93 / 99** | 80 % (mốc owner) |
| `DOCK_TILE_W` / `_H` | 84 / 86 | **71 / 73** | 85 % |
| `DOCK_TILE_H_VERTICAL` | 70 | **60** | 85 % |
| `DOCK_TILE_W_VERTICAL` | 100 | **83** (suy từ thanh) | xem ⚠ dưới |
| `DOCK_PAD` (lề trong thanh) | `Sp.S` = 8 | **`Sp.XS` = 4** | [ĐO] giữ 8 ⇒ ô ngang cần 97 > 93 và ô dọc cần 107 > 99 ⇒ **tràn** |
| `TOUCH_TIGHT` (bề cao VẼ nút −/+) | 32 | **27** | 85 %; [ĐO] giữ 32 ⇒ ô dọc cần 64 > 60 ⇒ tràn |

⚠ **`DOCK_TILE_W_VERTICAL` lệch mốc 2 điểm, có chủ ý**: bản trước WP5 khít **tuyệt đối** ở chiều này
(`100 + 8 + 16 = 124` = đúng `DOCK_WIDE` cũ), nên hai mốc của owner **không thể đúng cùng lúc** — 85 % của ô đòi
thanh ≥ 101dp (81.5 %, lệch mốc *thanh*), còn 80 % của thanh đòi ô ≤ 83dp (83 %, lệch mốc *ô*). Chọn giữ đúng mốc
**thanh** vì đó là thứ owner nhìn thấy và nói ra (*"taskbar kích thước 80 %"* = bề rộng cột chiếm chỗ trên màn),
còn 2 điểm bề rộng một ô thì không ai đọc ra bằng mắt. Viết dạng **phép trừ** nên ô không bao giờ tràn được.

⚠ `TOUCH_TIGHT` **không phải** một đích chạm nhỏ đi: vùng nhận chạm thật do `StepTouchTarget` cấp qua `TouchDelegate`
và nó lấy `Sp.TOUCH` (48dp), độc lập với bề cao vẽ. Chọn thu ở đây thay vì thu `ICON_S` — icon là manh mối nhận ra
nút, 5dp bề cao của một dấu `−` thì không mang thông tin nào.

### Thanh trên
`HEADER_BTN = 34` (70 % của 48) → `HEADER_H = HEADER_BTN + 2×XS = 42` = **đúng 75 %** của 56dp gốc (56 = 48 + 2×XS,
suy từ mã: bốn vật cao nhất đều khai `minimumHeight = TOUCH`). `HEADER_AVATAR = 22` (70 % của 32).
`HEADER_BTN_PAD = S` ⇒ hộp hình `34 − 16 = 18dp` với `FIT_CENTER` (hình **co theo nút**, không phải nút co theo hình).
Bề cao khai **tường minh** ở `KachiHomeActivity` thay vì `WRAP_CONTENT`: *"cao 75 %"* phải là tính chất **đo được
trên ảnh**, không phải hệ quả của việc vật nào tình cờ cao nhất.

---

## 4. [ĐO] — máy ảo `emulator-5554`, API **29** / 1920×1080 / density 240 (= cấu hình xe), chủ đề TỐI

Chủ đề kiểm bằng **độ chói trung bình cả màn = 34.8/255** (không tin nhãn tệp — bài học WP1: sao lưu
`*.xml.bak` là ô WAL của `SharedPreferencesImpl`, Android phục hồi nó đè tệp chính ⇒ hai ảnh "dark" đầu thực ra là SÁNG).

**Thanh trên (`wp45-home-dark.png`)**
- nội dung canh giữa **y = 44..66**, tâm 55 = đúng tâm của dải 24..87 (42dp) ⇒ `HEADER_H` có hiệu lực.
- mép trên vùng ô = **y = 100** = `87 + 13px` (khe `SLOT_GAP` 9dp). Trước WP5, WP1 đo đáy thanh ở **y = 107** ⇒ thanh
  **84px → 63px = 75.0 %**, và vùng ô được thêm **20px**.
- pill *Cài đặt* (nền nhấn): **40.0 × 34.0dp** ⇒ bề cao = đúng `HEADER_BTN`.
- đĩa chữ-cái-đầu hồ sơ: **22.0 × 22.0dp** ⇒ đúng `HEADER_AVATAR`.

**WP4 sắp lại (`wp45-header-reordered-dark.png`)** — ghi `header_order = PROFILE,VOICE,CLOCK,CHIPS,APPS,SETTINGS`:
- đĩa 22×22dp dời **x 1749..1781 → x 62..94** (từ mép PHẢI sang mép TRÁI).
- pill *Cài đặt* dời **x 1670..1729 → x 1817..1876** (nó nay là vật cuối).
- mật độ cột sáng theo 1/6 màn: `[136,0,0,65,169,116]` → `[160,50,0,0,161,119]` ⇒ khoảng co giãn dời từ 1/6 thứ 2–3
  sang thứ 3–4, đúng chỗ hàng chip đã chuyển tới.

**Thanh nút NGANG (`wp45-home-dark.png`)**: thanh **y 917..1055 = 139px = 92.7dp** (khai 93).

**Thanh nút DỌC — ca CHẬT NHẤT (`wp45-dock-left-dark.png`)**: thanh **x 19..166 = 148px = 98.7dp** (khai 99); ô cao
**90px = 60.0dp** (đúng `DOCK_TILE_H_VERTICAL`). **Không ô nào bị cắt**: ô STEP `temp` mực y 798..858 trong ô 788..877;
ô STEP `fan` mực 902..960 trong ô 890..979 — cả hai có **đủ hai nhóm mực** (icon + hàng `[− giá trị +]`).

**WP4 bề mặt Cài đặt (`wp45-settings-bars-*.png`)** — đọc từ cây trợ năng (`uiautomator`), không suy từ ảnh:
- *"Status-bar item order"* + 6 hàng `1/6 … 6/6`, nhãn `Clock and date` · `Car status chips` · `Talk button` ·
  `Apps button` · `Settings button` (từ `HeaderItem.labelEn` ⇒ i18n chạy).
- *"Car-bar item order"* + 8 hàng `1/8 … 8/8`, nhãn `Temperature` · `Fan` (từ `CapabilityCatalog.pick`).
- mỗi hàng có `▲`/`▼` kèm mô tả `Move up one place` / `Move down one place` (TalkBack đọc được).

**Test**: `./gradlew test --rerun-tasks --continue` ⇒ **5 module · 5109 test / 0 đỏ / 0 lỗi / 0 bỏ qua** (đếm từ
JUnit XML). Lệnh bắt buộc của lượt này `:core:test :app:testDebugUnitTest --rerun-tasks` ⇒ **3684 / 0**.
`:core` **0** import `android.*`. **0** `setStroke`/blur/shadow/elevation trong hai tệp mới (giữ WP1).

Máy ảo đã **trả prefs gốc** (kiểm sau khi trả: không còn `header_order`, `dock_edge = BOTTOM`), tệp tạm ở
`/data/local/tmp` và `/sdcard/ui*.xml` đã xoá.

### 4b. Thử phá (3 phép, đỏ đúng chỗ cả 3)

| Phép phá | Kết quả | Hoàn nguyên |
|---|---|---|
| `BarOrder.move` trả **bản sao** thay vì chính danh sách ở biên | `BarLayoutTest` **2 đỏ** (đúng hai ca `assertSame`) | sửa tay + **sha256 khớp** |
| `DOCK_PAD` về `Sp.S` (giá trị trước WP5) | `BarOrderWiringContractTest` **3 đỏ** (ô-trong-thanh ×2 + ô-STEP-trong-ô-dọc) | sửa tay + **sha256 khớp** |
| bỏ dòng `if (prev?.header != state.header) topStrip.setLayout(...)` ở `render` | `BarOrderWiringContractTest` **1 đỏ** (chuỗi dây nối) | ⚠ xem dưới |

⚠⚠ **TAI NẠN LẶP LẠI ĐÚNG CÁI ĐÃ GHI TRONG `project-context.md`.** Phép phá thứ ba hoàn nguyên bằng
`git checkout -- KachiHomeActivity.kt` trên **cây chưa commit** ⇒ lệnh trả về **HEAD**, không trả về bản trước khi
phá ⇒ **xoá sạch 3 sửa đổi WP4/WP5 của lượt này** trong tệp đó. Phát hiện ngay vì sha256 báo *did NOT match*; đã
**áp lại tay** cả ba mốc (`header = {…}` · `dp(KachiBars.HEADER_H)` · dòng `setLayout`) và chạy lại **5109 / 0**,
rồi **build + cài + chụp lại** `wp45-home-dark.png` để ảnh khớp mã cuối
([ĐO] lại: nội dung y 44..66 tâm **55** · mép vùng ô **y = 101** · pill **40.0 × 34.0dp** · đĩa **22.0 × 22.0dp** ·
chói **38.3/255**).

Phép phá **thứ nhất** cũng đụng đúng bẫy này theo hướng khác: `BarLayout.kt` là tệp **MỚI (untracked)** nên
`git checkout` **thất bại** và để lại phép phá trong mã sản phẩm — nếu không kiểm sha256 thì một dòng
`// MUTATION` đã đi vào bản giao. **LUẬT (nhắc lại, đã trả giá lần hai): cây chưa commit thì hoàn nguyên bằng cách
ĐẢO CHÍNH PHÉP SỬA + kiểm sha256, tuyệt đối không dùng `git`.**

---

## 5. Bài canh mới / đổi

| Bài | Việc |
|---|---|
| `BarLayoutTest` (`:core`, **13 ca**) | luật dời (biên · mã lạ · nhiều bậc) · bất biến hoán vị đủ · `decode` chữa dữ liệu **THIẾU** · `chipsAlignEnd` · dock dời chỗ không đổi viền/cờ hiện · **sắp chỗ KHÔNG thêm được mã mới vào thanh** |
| `BarOrderWiringContractTest` (`:app`, **8 ca**) | chuỗi dây nối **Cài đặt → intent → VM → prefs → state → tầng vẽ** · thanh nút KHÔNG có khoá lưu thứ hai · danh sách đọc lại state (không ảnh chụp) · nút hết dời được thì mờ + bỏ nhận chạm · cỡ lấy từ thang · **ô luôn nằm trong thanh** (số học) · **nội dung ô STEP nằm trong ô dọc** · hai mốc % đúng như owner chốt |
| `SpacingScaleContractTest` | +`KachiSpaceBars chi duoc khai hang`; `SCALE_FILES`/`SCALE_PREFIXES` khai một chỗ cho hai vai |
| `StepTouchTargetContractTest` | bài *"thanh nút dọc đủ 48 cả hai chiều"* đổi tên + đổi nội dung — xem ⚠ §6 |
| `TopStripSurfaceContractTest` | đích chạm pill: `Sp.TOUCH` → `Bars.HEADER_BTN`, **thêm** khẳng định `FIT_CENTER` + lề trong |
| `TopStripWiringContractTest` | hàng chip co giãn nay đọc ở `lpFor` (+ chốt **đúng MỘT** vật co giãn) |
| `LangCoverageTest` | `ENTRIES` 76 → **78**; tổng nhãn 287 → **295** (+2 mục, **+6 `HeaderItem`** — bộ đăng ký mới đưa vào tầm quét ngay lượt này) |
| `SettingsCatalogTest` · `SettingsCatalogControlContractTest` | `bars_dock_order` vào danh sách `prefKey = null` kèm lý do; hai mục mới có control tương ứng |
| `LayeringRulesTest` | `KachiSpaceBars.kt` vào `pureButMustStayInApp` — **`:core` bị CẤM giữ số dp**, nên nó không thể chuyển |

---

## 6. ⚠ Owner cần biết — hai đánh đổi AN TOÀN do mốc % gây ra

**(a) Nút thanh trên xuống 34dp, dưới mức tối thiểu 48dp** mà chính dự án dùng làm lý do cấm chip thanh trên bắn
lệnh xe. Giảm nhẹ, cả ba đều đo được: không nút nào ở đây **bắn lệnh xe** (ba pill mở một bề mặt, chip hồ sơ mở bộ
chọn) ⇒ bấm nhầm = mở sai bảng rồi Back, hoàn lại được ngay; 34dp vẫn lớn hơn hộp hình 18dp; bề ngang thực tế lớn
hơn 34 (pill `WRAP_CONTENT` + lề trong). **Trả về 48dp = đổi đúng một hằng** `HEADER_BTN` (thanh cao lại 56dp).

**(b) Vùng chạm nút −/+ của thanh nút DỌC: 50×48 → 41×48dp.** Trước WP5 đây là hướng **duy nhất** đủ 48×48, và
`StepTouchTargetContractTest` có một bài tồn tại để nói *"giới hạn 42dp của thanh ngang không phải do phép tính"*.
Ô dọc hẹp lại 100→83dp ⇒ mỗi làn 41dp. Bài canh đổi từ *"đủ 48"* sang **đo được đúng bao nhiêu** (một con số thật
thì phiên sau còn đối chiếu; một lời hứa đã sai thì chỉ làm người đọc tin nhầm). Diện tích vẫn 1968dp² = **3.07×**
mốc 640dp² trước khi có `TouchDelegate`. Muốn trả lại 48dp thì phải nới `DOCK_WIDE` ≥ 112dp, tức **bỏ mốc 80 % ở
thanh dọc**.

---

## 7. Còn tồn / cần owner chốt

1. **Kéo-thả** — hiện là ◀ ▶ (lý do ở §2). Muốn kéo thật thì `BarOrder.move` đã nhận delta bất kỳ, chỉ thêm chỗ gọi.
2. **`KachiHomeActivity.kt` 542 dòng > trần 500** — **có TRƯỚC lượt này** (536 ở `HEAD`, do WP1–WP3b); WP4/WP5 thêm
   6 dòng nối dây. Cắt nó phải chạm đúng khối mà `TopStripSurfaceContractTest` canh (3 khẳng định về `.picker` /
   `SettingsGroup.PROFILES` / `topStrip.setProfile`) ⇒ để **một lượt riêng**, không gộp vào WP5.
3. **`HeaderItem` không có đường ẨN từng vật** — cố ý (§2). Nếu owner muốn *"bỏ đồng hồ khỏi thanh"* thì đó là một
   yêu cầu mới, và nó phải là công tắc riêng chứ không phải "xoá khỏi thứ tự".
4. 🚗 **Cần xe**: cảm nhận nút 34dp khi lái · thanh nút dọc 83dp trên cụm thật · bốn viền × hai chủ đề (ảnh lượt này
   chỉ có TỐI, viền BOTTOM + LEFT).
5. **Chưa soát độc lập**: phiên này **không có công cụ sinh sub-agent** ⇒ không có reviewer thứ hai, và ảnh không đọc
   được bằng mắt máy (luật `image-reading-subagent.md`) ⇒ mọi bằng chứng hình ảnh là **phép đo điểm ảnh**. Owner nhìn
   5 ảnh ở `docs/diagnostics/ux-overhaul-2026-09-20/wp45-*.png` là bước xác nhận cuối.

---

## 8. Tệp đụng tới

**`:core`** (mới) `BarLayout.kt` · (sửa) `ControlRegistry.kt` (`DockConfig.moveEnabled`/`canMove`) ·
`HomeUiState.kt` (`header`) · `WorkspaceRepository.kt` · `ProfileScope.kt` (`header_order`) · `SettingsCatalogEntries.kt`.

**`:app`** (mới) `KachiSpaceBars.kt` · `SettingsBarOrderRows.kt` · (sửa) `KachiSpace.kt` (dời cỡ thanh + `TOUCH_TIGHT`) ·
`KachiTopStrip.kt` · `ControlDockView.kt` · `DockAreaLayout.kt` · `TileSize.kt` · `StepTouchTarget.kt` (KDoc) ·
`SettingsSectionsBars.kt` · `SettingsPanel.kt` · `HomePanels.kt` · `KachiHomeWiring.kt` · `KachiHomeActivity.kt` ·
`WorkspacePrefs.kt` · `PrefsWorkspaceRepository.kt` · `HomeViewModel.kt` · `res/values{,-en}/strings_kachi.xml` (+8 chuỗi).
