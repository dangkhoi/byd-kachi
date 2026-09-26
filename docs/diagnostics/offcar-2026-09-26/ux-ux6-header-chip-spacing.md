# UX6 — hai khe của chip thanh trên (icon↔chữ · chip↔chip)

**Ngày**: 2026-09-27 · **Bản đo**: Kachi **2.73 (174)** cài trên máy ảo `clusternav10` (1920×1080, density 240 ⇒
**1,5 px/dp**) · **Mã sửa**: nhánh `feat/voice-hotword-phrases`, 2.74 đang làm.

> Owner (2026-09-27, đang nhìn header trên máy ảo): *"canh lại cách hiển thị chip trên header luôn, khi có
> label, label nó sát icon quá, còn vị trí các icon với nhau có vẻ hơi rộng phải không?"*

**Kết luận ngắn**: owner đúng **cả hai vế**, và hai vế là **hai lỗi khác nhau**, không phải một.
Khe icon↔chữ = **0dp** (lỗi **thứ tự** trong mã, không phải lỗi chọn số); khe chip↔chip = **16dp** vì một
khoảng cách bị chia cho **ba hằng** (`marginStart` + hai lề trong) nên đọc mã ra 8dp mà màn hình hiện 16dp.

---

## 1. Đo thế nào (không đoán từ ảnh)

Theo `CLAUDE.md` §15: đọc **dữ liệu** trước, chỉ dùng ảnh để đối chiếu.

1. `adb -s emulator-5554 exec-out uiautomator dump /dev/tty` ⇒ hộp **px chính xác** của từng chip (chứ không
   phải mép mực trên ảnh — mép mực nhỏ hơn hộp icon vì glyph có lề trong của chính nó).
2. `adb -s emulator-5554 exec-out screencap -p` ⇒ đối chiếu bằng mắt + đo mép mực (phân tích cột).
3. `adb shell wm density` = **240** và `wm size` = **1920x1080** ⇒ **1,5 px/dp**. Chốt lại bằng phép cộng:
   chip hồ sơ đo được **51px** = `HEADER_BTN` 34dp × 1,5 ✓ ; ba pill đo được **60px** = `(18 hình + 2×8 lề) × 1,5` ✓.
4. `dumpsys package com.byd.launcher` ⇒ `versionName=2.73 versionCode=174` (§9: **không đoán** xe/máy ảo đang chạy bản nào).

Hộp chip đo được (14 chip đang bày, hàng chip `[259,1593]` = **1334px = 889dp**):

| chip | hộp (px) | rộng | ghi chú |
|---|---|---|---|
| Bụi mịn trong xe | `745…797` | **52** | icon + chữ `—` |
| Nhiệt độ ngoài | `809…857` | **48** | **không icon** (`TopStripChips`: `icon = null`) |
| Chế độ lấy gió | `869…921` | 52 | icon + chữ |
| … (9 chip nữa giống hệt) | … | 52 | |
| Trạng thái sấy trước | `997…1033` | **36** | `text=''` ⇒ **chỉ icon** |
| Điều hòa | `1429…1465` | 36 | chỉ icon |

Khe **giữa hai hộp** chip: `809−797 = 869−857 = … = ` **12px** ở cả 13 cặp ⇒ đúng `marginStart = dp(Sp.S)`.

---

## 2. [ĐO] Khe icon↔chữ đang là **0dp** — suy ra bằng phép trừ, không bằng mắt

`TextView` với compound drawable dẫn đầu: `rộng = lề trái + hộp icon + khe + chữ + lề phải`.
Ở 1,5 px/dp: lề `Sp.XS` = 6px, hộp icon `Sp.ICON_XS` = 24px.

| phép | số | ra |
|---|---|---|
| chip **chỉ icon** (`text=''`) | `6 + 24 + 0 + 0 + 6` | **36** ✓ khớp đo |
| chip **không icon** `"—°C"` | `6 + chữ + 6 = 48` | chữ `"—°C"` = **36px** |
| chip icon + chữ `"—"` | `6 + 24 + khe + chữ + 6 = 52` | `khe + chữ` = **16px** |
| chữ `"—"` (từ `"—°C"` = 36 − `"°C"` ≈ 20) | ≈ **16px** | ⇒ **khe = 0px = 0dp** |

Đo mép mực trên ảnh xác nhận: khoảng trắng icon↔chữ ở 11 chip là **3–5px** — toàn bộ là lề trong của glyph
(mực icon 17px nằm trong hộp 24px), **không có một pixel nào là khe**.

### Nguyên nhân: THỨ TỰ, không phải con số

Mã cũ viết đúng số (`dp(Sp.S)` = 8dp) nhưng hỏi sai chỗ:

- `KachiTopStrip.kt:274` (2.73) — `v.compoundDrawablePadding = if (v.text.isNullOrEmpty()) 0 else dp(Sp.S)`
- Hàng chip được **dựng với chữ rỗng** (`chip("", null, "")`), rồi `refreshChips` gọi `applyChipFace` **trước**
  khi đặt chữ (`v.text = c.text` nằm ở dòng SAU) ⇒ lượt đầu `v.text` còn `""` ⇒ **đệm 0**.
- Khoá `v.tag = c.icon + color` khiến `applyChipFace` **không chạy lại** khi chỉ có chữ đổi ⇒ số 0 đó **đóng
  băng suốt phiên**.

Đây đúng họ lỗi §8 của `CLAUDE.md` (*"compile xanh không có nghĩa là code chạy"*): cả ba dòng đều đúng khi đọc
riêng lẻ; chỉ thứ tự giữa chúng là sai. Vì vậy bản vá **truyền chữ vào** (`label: String`) thay vì đọc lại
`v.text` — bài canh khoá đúng tính chất đó, không khoá con số.

---

## 3. [ĐO] Khe chip↔chip đang là **16dp**, trong khi mã ghi 8dp

Chip dữ liệu **không có nền riêng** (không pill, không viền — quyết định cũ: *"chip prototype chỉ icon + chữ"*),
nên lề TRONG của chip **cộng thẳng** vào khe mắt người nhìn thấy:

```
khe VẼ = lề trong phải (XS 4dp) + lề ngoài (marginStart S 8dp) + lề trong trái (XS 4dp) = 16dp = 24px
```

Ảnh chụp xác nhận: khoảng trắng mực-đến-mực giữa hai chip đo được **26–32px** (24px + lề trong glyph).
Một khoảng cách, ba hằng ⇒ không ai đọc mã ra được con số thật. Đó là lý do owner thấy *"hơi rộng"* trong khi
mã trông như đang dùng bậc `S`.

---

## 4. Luật mới — một khoảng cách, một chủ

Hai hằng **mới, lấy từ chính thang** (không có số trần mới), khai ở `KachiSpaceBars.kt` (tệp thang, chỉ khai hằng):

| hằng | giá trị | vai |
|---|---|---|
| `KachiBars.CHIP_ICON_GAP` (`:113`) | `KachiSpace.S` = **8dp** | khe **trong** một chip: icon ↔ chữ |
| `KachiBars.CHIP_GAP` (`:130`) | `KachiSpace.M` = **12dp** | khe **giữa** hai chip — và là chủ **duy nhất** |

Thi hành ở `KachiTopStrip.kt`:

| chỗ | trước (2.73) | sau |
|---|---|---|
| `chipLp()` `:258` | `marginStart = dp(Sp.S)` | `marginStart = dp(Bars.CHIP_GAP)` |
| `chip()` `:264` | `setPadding(dp(Sp.XS), 0, dp(Sp.XS), 0)` | `setPadding(0, 0, 0, 0)` |
| `applyChipFace()` `:276/:284` | `(v, iconName, color)` · đọc `v.text` | `(v, iconName, color, label)` · `if (label.isEmpty()) 0 else dp(Bars.CHIP_ICON_GAP)` |
| `refreshChips()` `:335-336` | khoá `= icon + color` | khoá `= icon + color + c.text.isNotEmpty()` |

**Generic, một luật cho MỌI chip** (§7): bản vá nằm ở `chip()`/`applyChipFace()`/`chipLp()` — ba chỗ mà **mọi**
chip đều đi qua, kể cả ba chip dựng sẵn (`PM25` · `TEMP` · `ENERGY`), chip gộp `SEAT`, và mọi chip datum.
Không có nhánh theo mã chip nào.

**Ràng buộc gần-xa** (thứ làm chip đọc ra là *một vật*): `CHIP_GAP ≥ 1,5 × CHIP_ICON_GAP` ⇒ `12 ≥ 12` ✓.
Hạ `CHIP_GAP` xuống `S` hoặc nâng `CHIP_ICON_GAP` lên `M` đều phá luật này — có bài canh số học.

**Đích chạm**: không đổi — chip datum **không bấm được** (không `OnClickListener`); bốn nút thật của thanh
vẫn `Bars.HEADER_BTN` = 34dp.

---

## 5. Trước / sau — bảng số học

Ở 1,5 px/dp. `chữ` = bề rộng chữ do font quyết (không đổi).

| đại lượng | TRƯỚC (px / dp) | SAU (px / dp) | đổi |
|---|---|---|---|
| **khe icon↔chữ** (chip có chữ) | `0` / **0dp** | `12` / **8dp** | **+8dp** ✅ owner vế 1 |
| **khe chip↔chip (VẼ)** | `6+12+6 = 24` / **16dp** | `0+18+0 = 18` / **12dp** | **−4dp** ✅ owner vế 2 |
| tỉ lệ khe ngoài / khe trong | ∞ (0 ở tử số) | **1,5×** | đọc ra nhóm |
| rộng chip **icon + chữ** | `6+24+0+chữ+6 = 36+chữ` | `24+12+chữ = 36+chữ` | **0** |
| rộng chip **chỉ icon** | `6+24+6 = 36` | `24` | **−12px** |
| rộng chip **không icon** (`—°C`) | `6+36+6 = 48` | `36` | **−12px** |
| hàng chip (14 chip đang bày) | **860px** (đo được: `1593−733`) | **890px** (dựng lại) | +30px |
| chỗ còn của hàng chip | 1334px | 1334px | — |

Bề rộng chip *có chữ* **không đổi** vì `2 × XS` (8dp) đúng bằng `CHIP_ICON_GAP` (8dp) — khoảng cách chỉ **chuyển
chỗ**: từ hai bên ngoài vào giữa icon và chữ, đúng chỗ nó có nghĩa.

### Có tràn không?

- Đang bày 14 chip: **890 / 1334px**, dư **444px**. Không chip nào chạm trần `fitChips` (`cap = room/2` = 667px)
  ⇒ **không chip nào đổi giữa "chữ đủ" và `…`**.
- Đầy trần `TopStripConfig.CAP` = **16** chip cỡ rộng nhất đang thấy (35dp): `16 × (35 + 12) = 752dp ≤ 889dp` ✓
  (có bài canh số học, xem §6).
- Bộ **mặc định** (`PM25 · TEMP · ENERGY`) + **hai chip ghế gộp** mà nhánh khác đang thêm = 5 chip ⇒ ≈ `5 × 47 = 235dp`.
  Thừa chỗ.

---

## 6. Bài canh (test)

`app/src/test/java/com/byd/clusternav/launcher/TopStripWiringContractTest.kt` — **9 → 13 bài**:

| bài | khoá cái gì |
|---|---|
| `khe icon-chu lay tu thang va khong doc lai v_text` | đệm = `dp(Bars.CHIP_ICON_GAP)` **và** thân hàm **không** chứa `v.text` (khoá đúng NGUYÊN NHÂN, không khoá con số) |
| `khe giua hai chip di qua mot hang duy nhat` | `chipLp` dùng `Bars.CHIP_GAP`; `chip()` có `setPadding(0, 0, 0, 0)` và **không** `setPadding(dp(` |
| `khe ngoai chip phai rong hon khe icon-chu` | số học: `CHIP_ICON_GAP ≥ S` · `2·CHIP_GAP ≥ 3·CHIP_ICON_GAP` · `CHIP_GAP < S + 2·XS` (phải hẹp hơn khe cũ) |
| `hang chip van vua cho khi thanh day` | `CAP × (chip rộng nhất + CHIP_GAP) ≤ chỗ còn` |

Bài cũ `mau chip to ca icon chu khong chi to chu` được nới theo khoá `tag` mới (vẫn đòi **màu** nằm trong khoá).

**[ĐO mutation]** — ba bản lùi, ba bài đỏ đúng chỗ:

| lùi về | bài đỏ |
|---|---|
| `compoundDrawablePadding = if (v.text.isNullOrEmpty()) 0 else dp(Sp.S)` | `khe icon-chu lay tu thang va khong doc lai v_text` |
| `CHIP_GAP = KachiSpace.S` | `khe ngoai chip phai rong hon khe icon-chu` |
| `setPadding(dp(Sp.XS), 0, dp(Sp.XS), 0)` | `khe giua hai chip di qua mot hang duy nhat` |

**Kết quả chạy** (đếm từ XML `app/build/test-results/testDebugUnitTest/`):
`--tests '*TopStrip*' '*Spacing*' '*BarOrder*' '*I18n*'` ⇒ **53 bài, 0 đỏ**
(`TopStripWiring 13 · TopStripSurface 9 · SpacingScale 10 · BarOrderWiring 10 · LauncherI18n 11`).
Chạy rộng `*Contract*` (830 bài) còn **3 đỏ**, cả ba thuộc nhánh khác đang làm dở, **không** chạm tệp của UX6:
`CarDataDemandRendererContractTest` (thiếu nhánh `PM25` — `core/…/TopStripChips.kt` đang ở trạng thái untracked),
`IconStyleContractTest` (`ic-seat`), `CameraFrameAndRenderWiringContractTest` (chip kết xuất).

---

## 7. Lượt soát bằng MẮT phải kiểm gì (sau khi cài lại)

Ảnh dựng sẵn trong scratchpad của phiên: `ux6-before.png` · `ux6-after.png` · `ux6-before-after.png` ·
`ux6-row-before-after.png` (mock-up dựng **bằng số học** từ chính ảnh 2.73 — không phải ảnh của bản đã cài).

1. **Chip có nhãn thật** (không phải `—`): `PM2.5 · Tốt`, `24°C ngoài`, `82% · 418 km` — chữ phải **tách rõ**
   khỏi icon, và hai chip cạnh nhau vẫn đọc ra **hai nhóm**. Đây là điểm duy nhất còn rủi ro: ở máy ảo mọi chip
   đang hiện `—` nên hàng chip trông như một dãy gạch đều nhau; **phải xem lại với dữ liệu thật**.
2. **Chip chỉ-icon** (sấy trước · điều hòa) phải **không** thừa lề phải (đệm = 0 khi chữ rỗng — luật B6 giữ nguyên).
3. **Chip gộp ghế** (nhánh khác đang thêm vào mặc định) — kiểm cùng lượt.
4. Mép phải hàng chip vẫn **cách nút mic** đúng một `CHIP_GAP`, không dính.

**Nếu owner vẫn thấy hai chip đọc thành một** khi có dữ liệu thật: hai khe nay **độc lập** — nâng riêng
`CHIP_GAP` lên `KachiSpace.L` (16dp) là quay về khe cũ mà **vẫn giữ** 8dp icon↔chữ. Không phải sửa chỗ nào khác.

---

## 8. Chưa biết / còn lại

- **[CHƯA BIẾT]** Trên **xe thật** (1920×720, density khác) con số px sẽ khác; luật là dp nên không đổi, nhưng
  số chip vừa chỗ thì ít hơn. Chưa đo — bài `hang chip van vua cho khi thanh day` dùng bề rộng **máy ảo**.
- **[CHƯA LÀM]** Không cài, không chạm máy ảo (owner đang nhìn màn). Ảnh "sau" là **mock-up số học**;
  ảnh thật do bên điều phối chụp sau khi cài.
- **[SUY]** `2 × Sp.XS == Sp.S` là trùng hợp của thang khiến chip *có chữ* không đổi bề rộng. Nếu thang đổi
  bậc thì bề rộng chip sẽ đổi — bảng §5 phải tính lại, không phải chép lại.
