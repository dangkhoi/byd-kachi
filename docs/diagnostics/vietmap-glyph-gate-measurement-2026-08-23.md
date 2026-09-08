# Cổng dò glyph VietMap — đo lại toàn bộ bằng khung dựng từ asset APK

> **Trạng thái**: Current · **Cập nhật**: 2026-08-23 (vòng 3b) · **Mục đích**: Xác định ĐÚNG cổng nào của `NavGlyphLocator` đang chặn mũi tên VietMap, tìm tiêu chí thay thế bất biến tỉ lệ, và ĐO lại bằng chính locator Kotlin sau khi thay.
>
> **Đọc §7 + §7.7 trước** nếu chỉ cần kết quả cuối: §1–§5 là vòng đo bằng Python (thiếu `ringIsDark`) — đã bị thay thế; §7 là phép đo THẬT của vòng 3; **§7.7 là vòng 3b (hiện hành)** — nó ĐÍNH CHÍNH vài con số của §7.3/§7.4.

## 0. Vì sao có tài liệu này

Backlog B3.47 (viết sáng 08-23) quy thủ phạm cho `MAX_FILL` và ghi phủ sóng "17/34 template".
**Cả hai con số đều SAI** — chúng đo trên *proxy lưới 15×15* của chuỗi chữ ký, mà lưới đó đã chuẩn hoá
kích thước nên **không thể thấy cổng chiều cao**. Tài liệu này thay thế kết luận đó.

Owner chốt hướng (08-23): *"emulator end to end dẫn đường nhiều thật nhiều thì scan ra, chứ lên xe chạy
cũng khác gì? mà chạy trên đường cũng phải chạy cả năm mới đủ hết các case"* — đúng, và mạnh hơn thế:
không cần lái ở đâu cả, dựng khung thẳng từ asset là đủ.

## 1. Phương pháp — khung dựng từ chính asset VietMap

93 SVG `assets/flutter_assets/lib/assets/maps/directions_white/*.svg` (VietMap Live 3.3.4)
→ chèn `<rect fill="black">` → `qlmanage -t -s 288` → cắt viền trắng QuickLook → **thu canvas về đúng cỡ
trên màn (110 px)** bằng LANCZOS → chạy đúng thuật toán của `NavGlyphLocator` (flood-fill 4-liên-thông
luma > 200, `fill = n / (bbox area)`).

Cỡ canvas 110 px suy ra từ phép đo thật: `turn_right` ink = 95 px rộng, chiếm 0.862 bề rộng canvas.

## 2. Hiệu chuẩn — khung dựng có trung thực không? **CÓ** [ĐO]

| Phép đo | Maneuver | Ink THẬT trên màn | Khung dựng dự đoán | Lệch |
|---|---|---|---|---|
| Log v1.11 (map-DPI 0.5) | `turn_right` | 95×87, fill 0.282 | 95×88, fill 0.284 | 0.002 |
| Chụp 08-23 (map-DPI 1.0) | `turn_straight` | **51×96, fill 0.352** | **51×96, fill 0.352** | **0** |

⇒ Khung dựng KHÔNG phải mô phỏng gần đúng — nó dùng **chính file VietMap dùng để vẽ**.

**Hệ quả phụ, quan trọng**: hai phép đo trên ở **hai mức "DPI bản đồ" khác nhau (0.5 và 1.0)** đều khớp
**cùng một** cỡ canvas ⇒ [ĐO] **setting "DPI bản đồ" của VietMap scale phần BẢN ĐỒ, không scale banner**.
Banner là UI Flutter cỡ cố định. (Owner nêu lo ngại này 08-23 — đã kiểm, không ảnh hưởng; nhưng nguyên tắc
"không hardcode" vẫn giữ vì cast resize / `wm density` / chia đôi màn vẫn làm đổi cỡ.)

## 3. Kết quả — thủ phạm là `MAX_DP`, KHÔNG phải `MAX_FILL`

`MAX_DP = 58 dp` × scale 1.5 (dpi 240) = **87 px**.

- Glyph `turn_right` thật cao **đúng 87 px** ⇒ lọt qua với **biên = 0**. Lệch một pixel là mất.
- Họ `straight` / `slight` / `roundabout` của VietMap cao **95–96 px** ⇒ **rớt vì chiều cao**, chưa kịp xét fill.
- Cửa sổ `20–58 dp` được hiệu chuẩn trên **mũi tên Waze** (KDoc ghi: đo thật 33–42 dp). Mũi tên VietMap
  cao **64 dp** — nằm ngoài cửa sổ ngay từ thiết kế.

| `MAX_DP` | = px @dpi240 | Maneuver qua cổng (fill giữ 0.35) |
|---:|---:|---:|
| **58 (hiện tại)** | 87 | **24 / 87** |
| 62 | 93 | 25 |
| 65 | 97 | 41 |
| 72 | 108 | 41 |
| 76 | 114 | 42 |

## 4. Tìm tiêu chí thay thế — BẤT BIẾN TỈ LỆ

Owner (08-23): *"vì nó có thể chỉnh DPI nên phải có giải pháp cho việc này, không hardcode được đâu"*.
Nới `MAX_DP` 58→65 chỉ là dời chỗ chết sang app thứ ba. Cần đại lượng **tỉ số**.

### 4.1 Ứng viên BỊ BÁC: tỉ lệ glyph / chiều cao banner

Đo trên 6 khung thật (VietMap d240 + Waze d160/d200/d240/d320 + Waze banner 2 dòng):
tỉ lệ chạy **0.16 → 1.03**. Không ổn định. **Loại.**

### 4.2 Ứng viên ĐƯỢC DỮ LIỆU ỦNG HỘ: tỉ lệ khung `w/h`

Đo **75 đảo rác** từ 3 fixture negative thật (`neg-lanestrip-d240`, `neg-statusbar-no-arrow-d240`,
`neg-vietmap-darkmap-idle-d240`) đối chiếu 87 maneuver:

| | mũi tên (phải GIỮ) | rác (phải LOẠI) |
|---|---|---|
| `fill` | 0.28 – **0.50** | 0.11 – 0.92 ⟵ **chồng nhau** |
| **`w/h`** | **0.53 – 1.13** | 0.19 – 7.69 |

`fill` KHÔNG tách được: dải làn có đảo `141×220 fill 0.168` nằm giữa vùng mũi tên; ngược lại mũi tên
VietMap béo tới 0.497. `w/h` tách sạch, và là **tỉ số** ⇒ bất biến với mọi thang tỉ lệ.

| Luật | Maneuver phủ được |
|---|---:|
| Hiện tại: `fill ≤ 0.35` · cao ≤ 87 px CỨNG | **24 / 87** |
| Nới `MAX_DP` 65 dp (vẫn hardcode) | 41 / 87 |
| **`fill ≤ 0.55` · `w/h ∈ [0.4, 1.5]` · cao ≤ ROI/2** | **83 / 87** |

Mở được cả **rẽ gấp · quay đầu · tới đích · vòng xuyến** — các họ B3.47 báo "mất trắng".

⚠ **SỐ 83/87 Ở BẢNG NÀY ĐÃ BỊ §7 THAY THẾ** — luật thật đem thi hành khác ba chỗ: `fill ≤ 0.60` (không phải
0.55), `w/h ∈ [0.35, 1.45]` (không phải [0.4, 1.5]), và **không có** vế "cao ≤ ROI/2" (bị bác, xem §7.2).
Con số đo bằng locator thật là **86/87 dò đúng · 0 đảo mồi**.

## 5. GIỚI HẠN CỦA CHÍNH PHÉP ĐO NÀY (đọc trước khi dùng số ở §4.2)

Bản dựng lại bằng Python **KHÔNG mô hình `ringIsDark`** (kiểm vành tối 4 phía) — mà KDoc `NavGlyphLocator`
ghi rõ đó là ràng buộc *"gánh phần lớn việc"* loại rác. Vì vậy:

- Cột "maneuver phủ được" ở §3 và §4.2: **[ĐO]** (chỉ phụ thuộc cổng kích thước/fill/aspect).
- Mọi kết luận về **an toàn** (rác có lọt không): **[SUY]**, KHÔNG được dùng để quyết.

⇒ Bước bắt buộc trước khi chạm `NavGlyphLocator`: biến 93 khung dựng + 75 đảo rác thật thành **fixture
test off-car**, chạy qua **`NavGlyphLocator` Kotlin thật**.

✅ **ĐÃ LÀM — xem §7.** Phép đo thật lật ngược đúng cái §5 cảnh báo: `ringIsDark` KHÔNG "gánh phần lớn việc"
như KDoc từng nói (nó để lọt một icon POI bản đồ ở 60/87 khung), và cổng thiếu không phải cổng lấp mà là
một cổng **vị trí** chưa từng tồn tại.

## 6. Hiện vật tái lập

`<scratchpad>/vmfill/` — `measure.py` (bản dựng lại thuật toán locator) · `sweep.py` (quét 93 icon,
tham số = cỡ canvas) · `ratio.py` (thử ứng viên §4.1) · `sweep_110.json` (kết quả thô) ·
`now_dpi10.png` (ảnh chụp emulator 08-23, map-DPI 1.0).
Asset gốc: `<scratchpad>/vmapk/ext/assets/flutter_assets/lib/assets/maps/directions_white/`.
Công thức sinh template: `<scratchpad>/vm_recipe.txt`.


---

## 7. VÒNG 3 — đo bằng **chính `NavGlyphLocator` Kotlin**, và phát hiện đường dương-tính-giả

§5 nêu giới hạn của vòng Python: không mô hình `ringIsDark`. Vòng 3 dựng 87 khung 1920×1080 **trong test**
(dán glyph lên `_base-vietmap-1920x1080-d240.png` tại `origin=[33,69]`) rồi gọi thẳng
`NavGlyphLocator.locate(frame, CropRect(0,0,1920,1080), 240)`. Tái lập:

```
git checkout -- docs/diagnostics/hud-sign-re/
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :core:test --tests '*VietMapGlyphGateTest*'
git checkout -- docs/diagnostics/hud-sign-re/
```

### 7.1 [P1] Cổng cũ KHÔNG im lặng khi trượt — nó trả về một icon BẢN ĐỒ

Kết quả chạy trên 87 maneuver (bỏ 6 icon phi-chỉ-dẫn `close`·`flag`·`updown`·`road_icon_left/right`·`invalid`):

| Cổng CŨ (`MAX_DP` 58 dp · `MAX_FILL` 0.35) | số khung |
|---|---:|
| dò ĐÚNG mũi tên (x < 250) | **27** |
| trả **ĐẢO MỒI** ở rect (603,78)-(642,117) 39×39 | **60** |
| trả null | **0** |

Đảo mồi = **icon POI điểm dừng xe buýt trên BẢN ĐỒ** (chữ trắng trong ô bo góc, nền tối bao quanh). Nó qua
sạch cả bốn ràng buộc cũ: đảo sáng ✓ · vành tối 4 phía ✓ · 39×39 px ∈ [30, 87] ✓ · lấp 0.106 ∈ [0.10, 0.35] ✓.
Nằm cách mũi tên **560 px** về bên phải.

⇒ Giả định thiết kế *"không dò được ⇒ im lặng"* là **SAI**. Mũi tên thật bị loại thì luật "đảo TRÁI NHẤT
thắng" biến một POI bản đồ thành ứng viên, và crop SAI đó đi thẳng vào `classifyWazeInk`. Đây là đường
false-positive **có thật**, không phải giả thuyết — và nó nguy hiểm hơn hẳn việc thiếu phủ sóng.

### 7.2 Cổng mới — ba tiêu chí, cả ba là TỈ SỐ

| Cổng | Giá trị | Đơn vị | Thay cho |
|---|---|---|---|
| `MAX_FILL` | 0.35 → **0.60** | tỉ số (mực / diện tích bbox) | — |
| `MIN_ASPECT` / `MAX_ASPECT` | **0.35 / 1.45** | tỉ số (`gw/gh`) | `gw > MAX_DP*1.6` |
| `MAX_LEFT_ANCHOR` | **2.0** | tỉ số (`(x₀ − winLeft) / max(gw,gh)`) | — (cổng MỚI) |
| `MAX_DP` | **GỠ HẲN** | — | — |
| `MIN_DP` | 20 (giữ) | dp × dpi thật | — |

**Vì sao neo trái là tỉ số thật**: tử số (lề trái của banner) và mẫu số (cỡ icon) đều là đại lượng dp của
CÙNG một layout ⇒ tỉ số của chúng không đổi khi `wm density`, `wm size`, chia đôi màn hay app tự scale.
Không có px/dp tuyệt đối nào trong đó.

**Vì sao không cần trần kích thước nữa**: flood-fill vốn bị nhốt trong ROI, nên `gh ≤ roiH` là hiển nhiên;
một mảng sáng chiếm gần hết ROI thì rớt ở `MAX_ASPECT` hoặc `MAX_FILL`. Mọi ứng viên trần **tương đối theo
ROI** đều bị BÁC ở vòng này vì `roiH = min(0.45·winH, 220dp·scale)` phụ thuộc chiều cao cửa sổ (480/600/720/1080
và chia đôi) ⇒ `gh/roiH` của cùng một glyph chạy 0.16 → 0.49 giữa các fixture. Đó là cùng loại bất ổn với
"tỉ lệ glyph / chiều cao banner" đã bị bác ở §4.1.

**`MIN_DP` giữ lại** — và giữ có lý do đo được, không phải vì ngại đụng: nó là **sàn**, không phải trần, nên
không thể loại một glyph vì glyph đó TO (đúng cơ chế đã giết họ `straight`/`slight`/`roundabout`). Gỡ nó ra
thì 3 rect icon status bar (9×15 px @dpi240 · lấp 0.36 · `w/h` 0.60 · neo trái 0.87–2.07) qua sạch mọi cổng
tỉ số và **thắng vì nằm bên trái mũi tên** — [ĐO] trên `fork_straight` và `w1920h720-d240-2line`.

### 7.3 Biên hai đầu của từng ngưỡng (94 glyph được chọn: 86 VietMap + 8 Waze)

| Cổng | glyph ĐƯỢC CHỌN | rác gần nhất bị cổng này chặn | ngưỡng | dư địa |
|---|---|---|---|---|
| lấp | 0.2065 – 0.4968 | (không đảo nào bị chặn *chỉ* bởi cổng này) | ≤ 0.60 | 21 % |
| `w/h` | 0.4167 – 1.1707 | (như trên) | [0.35, 1.45] | 16 % / 19 % |
| **neo trái** | 0.417 – **1.358** (ca căng nhất là **Waze**, không phải VietMap) | chữ cự ly **2.75** · 3.56 · dải LÀN 3.16 · 8.42 · 10.59 · 12.00 · POI xe buýt **15.46** | ≤ 2.0 | 47 % / 38 % |

Ngưỡng neo trái 2.0 ≈ trung bình nhân của 1.36 và 2.75 — chọn giữa khe, không sát mép (đúng bài học `MAX_DP`
lọt với biên 0 px).

> ⚠ **ĐÍNH CHÍNH (vòng 3b)** — bảng trên trình bày ba cổng như thể độc lập. Chúng KHÔNG độc lập: rác thật sự
> gần cổng neo trái nhất không phải chữ cự ly 2.75 mà là **icon status bar ở neo 0.93 và đúng 2.00** — lọt với
> biên **0** (cổng là `>` nghiêm ngặt), và thứ chặn nó là **SÀN**, không phải neo trái. Tức con số "38 % dư
> địa" chỉ đúng khi đã giả định sàn làm việc. Đó là lý do vòng 3b thêm vế sàn thứ hai (§7.7).

### 7.4 Kết quả SAU

| | trước | sau |
|---|---:|---:|
| dò ĐÚNG mũi tên | 27 / 87 | **86 / 87** |
| trả **ĐẢO MỒI** | 60 / 87 | **0 / 87** |
| trả null | 0 | 1 (`fork_straight`) |
| đi trọn đường tới mã AMAP | 15 / 87 | **39 / 87** |
| bbox 8 khung Waze | — | **không đổi một pixel** |

`fork_straight` là ca null DUY NHẤT. Câu trả lời "null" là AN TOÀN (im lặng) và trước bản vá chính nó trả về
đảo mồi — nhưng **không phải hành vi thiết kế**: [ĐO] vòng 3b, `fork_straight` và `continue_straight` có bbox
**giống nhau từng pixel** (63,76)-(114,172) 51×96 và lấp lệch **0.002** (0.3523 vs 0.3503), một cái rớt
`ringIsDark` một cái qua. Đây là biên của `DARK`/`RING_DP`, không phải một quyết định.

Phần hụt còn lại (86 dò được nhưng chỉ 39 ra mã AMAP) **không còn do cổng locator** mà do
`WazeArrowRegistry.VIETMAP_INK` chỉ có 34 template cho ~16 lớp quyết định, cộng giới hạn "ngưỡng mực" đã ghi
trong KDoc `VIETMAP_INK` (nhiều tên asset khác nhau vẽ cùng một đường mực trắng).

### 7.5 Phép thử LÀM ĐỎ (mutation) — test không mù

Khôi phục tạm cổng cũ (`MAX_DP` 58 + `MAX_FILL` 0.35, bỏ aspect + neo trái) ⇒ **5/6 test
`VietMapGlyphGateTest` ĐỎ**, đúng chỗ:

| Test | Thông điệp khi đỏ |
|---|---|
| `khung nen KHONG co mui ten thi im lang` | `expected: <null> but was: <CropRect(603,78,642,117)>` |
| `khong maneuver nao tra ve icon POI tren ban do` | 60 tên maneuver |
| `phu song that — 86 do dung, 0 dao moi, 1 null` | danh sách 60 rect ngoài vùng mũi tên |
| `moi glyph chon duoc deu cach mep cong it nhat 10 phan tram` | `arrive: neo trái 15.46 sát trần 2.0` |
| `phu song toi matcher` | 15 thay vì 39 |

`NavGlyphLocatorTest` (12 test, gồm khoá bbox Waze từng pixel) **xanh ở CẢ HAI phía** của mutation ⇒ bằng
chứng độc lập rằng Waze không hồi quy.

### 7.6 Test khoá (thay cho phép đo tạm)

- `core/src/test/kotlin/.../screencapture/VietMapGlyphGateTest.kt` — 6 test: bảng phủ sóng · đảo mồi = 0 ·
  khung nền không mũi tên ⇒ null · **bất biến tỉ lệ** (phóng khung ×2 + dpi ×2 ⇒ bbox ×2, đúng từng pixel) ·
  biên ≥ 10 % mọi cổng · phủ sóng end-to-end tới mã AMAP + canary sai-hướng.
- `core/src/test/kotlin/.../screencapture/NavGlyphLocatorTest.kt` — thêm
  `bbox Waze khong doi mot pixel nao sau khi thay cong (B3_47)` (8 rect chốt cứng).
- **ĐÃ GỠ** `WazeArrowRegistryTest.phu song that qua cong NavGlyphLocator` — nó đo trên proxy lưới 15×15 đã
  chuẩn hoá kích thước nên **không thể** thấy cổng chiều cao; chính nó đẻ ra con số sai "17/34 · `MAX_FILL`".

---

## 7.7 VÒNG 3b (08-23, sau ba lượt phản biện độc lập) — ba đường false-positive nữa

Vòng 3 đóng đường "mũi tên rớt ⇒ vồ icon POI". Ba lượt phản biện chỉ ra rằng **cổng thì đúng, nhưng ĐẦU VÀO
của cổng có thể sai** — và đã đo được ba đường vào.

### 7.7.1 [P1] `windowRect == null` ⇒ publish mũi tên của APP KHÁC

Dựng khung 1920×1080 chia đôi từ hai fixture THẬT (nửa trái = Waze `w1920h720-d240`, rẽ **TRÁI**; nửa phải =
khung VietMap `turn_right`, rẽ **PHẢI**), rồi hỏi locator về VietMap:

| ô cửa sổ truyền vào | rect trả về | chấm ra |
|---|---|---|
| nửa phải (960,0,1920,1080) | (1000,85)-(1095,172) | `maneuver_turn_normal_right` amap **3** ✓ |
| `null` (nghĩa cũ = cả khung) | (72,56)-(118,109) | `maneuver_turn_normal_left` amap **2** ✗ mũi tên của Waze |

Hamming **0** ⇒ khớp chắc chắn ⇒ `publishArrow(pkg=VietMap, amap=2)`. Đường vào có thật:
`CaptureLocationResolver` trả `windowRect = null` mỗi khi không parse ra task, và KDoc của chính nó ghi ca đó
**đã xảy ra thật** trên API 34 (regex chỉ-`Stack` không khớp `RootTask id=`), im lặng.

**Vá**: `NavGlyphLocator.locate(window: CropRect)` — bỏ hẳn kiểu nullable (trình biên dịch chặn);
`ScreenCaptureNavSource.handleArrowByGlyph` `return false` khi `loc.windowRect == null`. Cùng hạng với §R-BI
đã áp cho bounds tier-1 và làn, chỉ khác là đường glyph phải tự chốt vì nó tự đi tìm rect.

### 7.7.2 [P1] `dpi` KHAI SAI làm sàn sụp ⇒ icon status bar thắng ở 87/87

`MIN_DP` quy ra px bằng `dpi/160`, mà `dpi` là số parse từ `am stack list`. [ĐO] trên khung vẽ ở dpi 240:

| dpi khai | rect trả về |
|---|---|
| 0 / 60 / 80 / 100 / 120 / 124 | **(14,11)-(23,26) 9×15 = icon status bar**, cho **cả 87/87** maneuver |
| 128 … 480 | mũi tên |

Và bộ test vòng 3 **không bắt được** vì tiêu chí "dò đúng" của nó là `rect.left < 250` — icon ở `left=14` cũng
thoả ⇒ báo "hit 87/87" trong khi 100 % là crop sai.

**Vá hai phần**:
1. Sàn = `max(MIN_DP × dpi/160 , win.height × MIN_H_WIN_FRAC)` với `MIN_H_WIN_FRAC = 0.025`. Vế hai không đi
   qua `dpi`. Chọn 0.025 từ số đo: mũi tên thật / chiều cao cửa sổ thật nhỏ nhất = **0.049** (Waze
   `w1920h1080-d240`: 53 px trong cửa sổ 1080) ⇒ dư địa **1.96×**; icon status bar 15/1080 = **0.0139** ⇒ dư
   địa **1.80×**. Là tỉ số hai đại lượng px cùng khung ⇒ vẫn bất biến tỉ lệ (test ×2 vẫn xanh).
2. Tiêu chí "dò đúng" của test đổi thành **rect nằm trọn trong ô canvas glyph đã dán** (`origin` + `canvas`
   trong `index.json`), không còn là một ngưỡng toạ độ.

### 7.7.3 [P1] Rơi về rect cố định sau khi ĐÃ đo được mũi tên ⇒ 2 ca SAI HƯỚNG

Rect ARROW cố định `CaptureRouter.WAZE_ARROW_BANNER_D240 = (78,50,158,163)` tra theo `(target, W, H)` chứ
không theo package, nên nó cũng bị áp lên khung VietMap. [ĐO] trong 48 khung mà đường glyph không giải được,
rect cố định ra mã cho **3**, trong đó **2 SAI HƯỚNG**:

| maneuver | rect cố định ra | amap | đúng phải là |
|---|---|---:|---:|
| `depart_right` | `maneuver_roundabout_enter_and_exit_cw_normal_left` | 11 | 3 |
| `fork_slight_right` | `maneuver_turn_normal_right` | 3 | 5 |
| `fork` | `maneuver_turn_normal_right` | 3 | — |

Cả 3 đi qua nhánh **NCC mềm** (`NCC_MIN` 0.45; Hamming tới template gần nhất 27/37/38 ≫ ngưỡng 18).

**Vá**: dò được bbox ⇒ **tầng glyph SỞ HỮU kênh ARROW nhịp đó** — `handleArrowByGlyph` trả `true` ở mọi lối
thoát sau khi `locate` thành công, kể cả khi registry chưa có template. Đây là **đảo ngược** quyết định
Pass-1 của spec B3.27 (*"chỉ trả true khi đã publish, rơi tiếp là an toàn"*): câu đó đúng về *quy ước khớp*
nhưng bỏ sót rằng *rect* cũng là một giả định. Đánh đổi có chủ ý: mất phủ sóng, được im lặng.
Nhánh NCC mềm của chính đường rect cố định vẫn còn (nó phục vụ Waze/GMaps đã proven ngoài hiện trường) ⇒
tách sang **B3.53**.

> **CẬP NHẬT 2026-08-23 — B3.53 đã đóng.** Đo lại đúng ba ca trên cho thấy cả ba đều là khung locator **dò
> được** (`locate=OK`), tức ở luồng thường chúng đã bị chính bản vá §7.7.3 chặn; đường sống thật sự còn lại
> là ca **`windowRect == null`** (bỏ qua cả tầng glyph). Bản vá B3.53: tier `BoundsSource.FIXED_CALIBRATED`
> chỉ chấp nhận khớp **CỨNG** (`ManeuverSignature.classifyStrict`, chỉ Hamming), tier `A11Y_DYNAMIC` giữ
> nguyên. Kết quả [ĐO]: 87 khung VietMap ra mã **3 → 0** (sai họ **2 → 0**); 418 khung GMaps đường
> notification **0/418** đổi; 3 khung Waze thật **0/3** đổi. Bảng số của hai ứng viên bị bác nằm ở
> `docs/specs/b3-53-fixed-rect-ncc.html` §4.2.
>
> **ĐÍNH CHÍNH 2026-08-23 (B3.53 Pass 2 — review) — câu "tier `A11Y_DYNAMIC` giữ nguyên" ở trên KHÔNG phải
> là một kết luận an toàn, nó là một lỗ hổng chưa đo.** [ĐO] `CaptureBounds` khi đó không mang MỤC TIÊU, còn
> `CaptureRouter.computeBounds` áp snapshot tier-1 cho **mọi** target. Với VietMap, producer a11y chọn node
> bằng `CaptureTarget.forPackage(pkg)` = **CAMERA**, nên plan **ARROW** nhận y hệt rect icon camera kèm nhãn
> `A11Y_DYNAMIC` rồi đi vào nhánh **khớp MỀM** — đúng lớp lỗi mà bản vá B3.53 chặn ở tier rect-cố-định:
> ```
> PLAN target=ARROW  bounds=CropRect(1500,300,1620,420) src=A11Y_DYNAMIC   ← rect của node CAMERA
> PLAN target=CAMERA bounds=CropRect(1500,300,1620,420) src=A11Y_DYNAMIC
> ```
> Crop 87 khung VietMap qua `CaptureCalibration.VIETMAP_CAMERA_SEED`: khớp mềm ra mã **1** khung
> (`arrive_straight` → amap 12, đúng phải 9), khớp cứng ra **0**. Đã vá bằng `CaptureBounds.target` + gate
> `a11y.target == target` (khoá bằng `CaptureRouterTest`, `ScreenCaptureHoldersTest`); Waze/GMaps **0 đổi**.
> Chi tiết + bảng giá phủ sóng: `docs/specs/b3-53-fixed-rect-ncc.html` §6.1 và §10 Reviewer Log.

### 7.7.4 Sửa nhỏ + đính chính số đo

| Chỗ | Ghi sai | Đo lại (vòng 3b) |
|---|---|---|
| `NavGlyphLocatorTest` KDoc fixture bản-đồ-tối | "12 đảo · neo trái loại 7 (2.07…93.0) · sàn loại 3 · tỉ lệ khung loại 2" | **11** đảo qua vành-tối, **cả 11 rớt ở SÀN** (cao nhất 26 px < sàn 30), neo lớn nhất **53.67** |
| `NavGlyphLocator` KDoc | "94 glyph (87 VietMap + 8 Waze)" · "98 khung THẬT" | 86 VietMap + 8 Waze; 87 khung là **ghép**, chỉ 8+3 là ảnh chụp nguyên văn |
| `WazeArrowRegistry` KDoc | "4 mục nhiều thành phần" | **10** tên (thêm cả họ `depart` và `arrive_straight`) |
| ROI KDoc | "chặn theo dp kéo xuống còn ~1/3" của 1.9 MB | quên mảng `stack`: thực tế **1.86 MB → 1.27 MB** (−32 %), 5 B/px chứ không phải 1 B/px |
| `MAX_BLOB_PX` | chạm trần ⇒ bbox dở dang vẫn đi qua các cổng | chạm trần ⇒ **bỏ đảo** |
| fixture Waze | 8 bbox coi như "bbox on-car" | fixture là **dải cao 240–320** ⇒ `roiH` chỉ ~44 % thực tế; đắp lên nền bản đồ THẬT cho đủ chiều cao màn thì 7/8 giống hệt, `w1920h720-d240-2line` ra (83,73,147,**148**) — tức **144 là artefact của fixture**. Hướng chấm KHÔNG đổi. |

### 7.7.5 Đã CÂN NHẮC rồi BÁC (kèm số đo)

- **Nâng `MIN_FILL` 0.10 → ~0.17** để tự loại họ POI thay vì nhờ neo trái. Đo đủ họ POI trong khung nền:
  lấp chạy tới **0.1676** (ô 42×26), còn glyph thật thấp nhất **0.2065** ⇒ chỉ cách **1.23×**, mỗi mép do
  1–2 mẫu định ra. Siết ở đó là lặp lại đúng cái sai của `MAX_DP` (hiệu chuẩn trên vài mẫu, chết ở app thứ
  ba). Họ POI đã có dư địa **7.7×** ở neo trái khi ô cửa sổ đúng — mà ô cửa sổ nay là bắt buộc (§7.7.1).
- **Cổng "chiều cao tương đối so với đảo cao nhất trong ROI"**: đóng được ca §7.7.2 khi CÓ mũi tên nhưng
  không đóng khi KHÔNG có (chỉ còn icon thì icon vẫn là cao nhất). Sàn theo cửa sổ đóng được cả hai ⇒ chọn
  cái đơn giản hơn.

### 7.7.6 Test khoá thêm ở vòng 3b

| Test | Khoá |
|---|---|
| `VietMapGlyphGateTest.dpi khai SAI chi duoc lam mat mui ten…` | 6 maneuver × 14 mức dpi khai ⇒ chỉ được là mũi tên hoặc null |
| `VietMapGlyphGateTest.chia doi man — moi nua tra dung mui ten cua app trong nua do` | nửa trái ⇒ amap 2 · nửa phải ⇒ amap 3 · ô cửa sổ sai ⇒ trả mũi tên app kia (khoá **cơ chế** lỗi) |
| `VietMapGlyphGateTest.glyph nhieu thanh phan chua duoc phep ra ma AMAP` | canary cho B3.52 — 10 tên mảnh không được khớp template |
| `NavGlyphLocatorTest.ROI day du — dap dai Waze len ban do THAT` | ROI đầy đủ (không phải 44 %) không đổi bbox lẫn hướng |
| `NavGlyphLocatorTest.so do mui ten Waze that cach mep MOI cong…` | biên ≥10 % cho lấp · tỉ lệ khung · **neo trái** · cả hai vế sàn (thay test cũ chỉ kiểm lấp) |
| `ScreenCaptureNavSourceContractTest` +2 | `windowRect == null` ⇒ bỏ nhịp · sau `locate` non-null không còn `return false` nào |

**MUTATION-CHECK [ĐO]**: gỡ vế `MIN_H_WIN_FRAC` khỏi sàn ⇒ `dpi khai SAI…` ĐỎ (`turn_right@dpi60=CropRect(14,11,23,26)`).
Trả `handleArrowByGlyph` về hành vi cũ (null-window = cả khung + rơi xuống rect cố định) ⇒ **2 test contract
`:app` ĐỎ** (`expected: <1> but was: <4>` cho số `return false` sau `locate`). Khôi phục ⇒ xanh lại.

**Test 5 module — CHẠY TÁCH, vì `./gradlew test` gộp cho số ẢO** [ĐO] 08-23 vòng 3b:
`git checkout -- docs/diagnostics/hud-sign-re/` → `:app :core :vehicle-contracts :car-integration` (`--rerun-tasks --continue`)
= **1802 test / 0 fail** (`:app` 450+461 · `:core` **841** · `:vehicle-contracts` 22 · `:car-integration` 28);
restore lại → `:offcar-planner` = **99 / 5 fail**. Tổng **1901 test / 5 fail**, **0 fail mới**.

> ⚠ **ĐÍNH CHÍNH BASELINE (vòng 3b)** — con số "baseline = 10 fail" vẫn còn artefact. `:offcar-planner:test`
> ghi đè 9 file niêm phong **NGAY TRONG lượt chạy**, nên trong một lượt `./gradlew test` gộp, 5 test
> `:core T10SessionSafetyTest` fail **chỉ vì `:offcar-planner` tình cờ chạy trước**. [ĐO] chạy `:core` với
> seal SẠCH ⇒ 5 test đó **XANH**; chạy `:core :vehicle-contracts :car-integration` với seal sạch ⇒ **841/22/28
> đều 0 fail**. Trong CÙNG phiên này, hai lượt `./gradlew test --rerun-tasks --continue` giống hệt nhau cho
> **10** rồi **25** fail — khác nhau chỉ bởi thứ tự task. **Fail có sẵn THẬT = 5** (`:offcar-planner`, so
> output sinh mới với pack đã check-in). Mọi con số 10 / 25 / 37 đều là hàm của thứ tự task.

**Trạng thái**: off-car xanh; **CHƯA on-car** (`CLAUDE.md §14` tầng 1 chưa xanh cho vòng 3 lẫn 3b).

---

## 8. Liên quan

- Backlog **B3.47** (vòng 3 = §7, vòng 3b = §7.7), **B3.52** (thiếu template + glyph nhiều mảnh),
  **B3.53** (nhánh NCC mềm của đường rect cố định), **B3.43** (bộ 34 template),
  **B3.27** (thiết kế locator bất biến)
- Spec `docs/specs/b3-glyph-locator-cast-invariant.html` · `docs/specs/b3-full-nav-capture.html` (§Nhật ký triển khai)
- `core/.../screencapture/NavGlyphLocator.kt` · `core/.../WazeArrowRegistry.kt`
- Test khoá: `core/src/test/kotlin/.../screencapture/VietMapGlyphGateTest.kt` · `NavGlyphLocatorTest.kt`
- Fixture: `core/src/test/resources/diagnostics/vietmap-glyph/` (93 glyph + base + `index.json`) ·
  `core/src/test/resources/diagnostics/glyph/` (8 khung Waze dương + 3 khung âm)
