# B3 — Chuỗi "đọc cụm → đẩy HUD" chạy thông off-car (cụm 1920×720 + màn chính 1920×1080)

> **Trạng thái**: Current · **Ngày**: 2026-08-21, bổ sung 2026-08-22 · **Loại**: diagnostics (đo thật)
> **Bối cảnh**: emulator `clusternav` (API 34) dựng 2 display; WazeMod dẫn thật (Hà Nội, GPS giả).
> **Backlog**: B3.23 (calib), B3.6 (template), B3.15/B3.18 (owner→HAL).

## 0. Kết luận một dòng

Chuỗi **screencap → crop → ManeuverSignature → ScreenCaptureSignal → NavOutputOwner → BydHal.pushNavigation**
đã chạy **thông từ đầu đến cuối** ở đúng kích thước cụm 1920×720, ra `amap=3` (rẽ phải) đúng với màn hình.
Trên đường đi lộ ra **hai lỗi CÓ THẬT** đủ để làm kênh mũi tên **câm hoàn toàn trên xe**, cả hai đã vá.

## 1. Rig emulator

`emulator -avd clusternav -multidisplay 1,1920,720,240,0` (⚠ cú pháp **dấu phẩy**; dạng cách-trắng
`-multidisplay 1 1920 720 240 0` bị parser build 36.6.11 từ chối: `invalid command-line parameter: 1920`).

Kết quả: display **0** = 1920×1080 (khớp màn chính xe) · display **2** = 1920×720 (khớp cụm xe).

> ⚠ **Emulator KHÔNG chụp được display phụ.** `screencap -d` nhận **physical display id**
> (`dumpsys SurfaceFlinger --display-id` chỉ liệt kê display 0 = `4619827259835644672`); display phụ của
> emulator là **VIRTUAL** nên `screencap -d 2` trả file **0 byte**. `screenrecord --display-id` cùng giới hạn.
> ⇒ Muốn chạy pixel-pipeline ở kích thước cụm off-car thì phải đặt **display 0 = 1920×720** (`wm size`),
> KHÔNG chụp được display 2. Đã `wm size reset -d 0` sau khi đo (state này PERSIST — CLAUDE.md §5).

## 2. Lỗi 1 — rect crop cụm chưa có ⇒ mũi tên CÂM trên xe [B3.23]

`CaptureCalibration.TABLE` chỉ có entry `(ARROW, 960, 720)` (đo trên emulator 960×720 hôm 08-20). Cụm THẬT
là **1920×720** ⇒ không khớp entry nào ⇒ rơi về seed OpenBYD `(26,218,208,298)` 182×80.

Đo trên khung Waze dẫn thật @1920×720:

| rect | số bit chữ ký | khớp gần nhất | kết quả |
|---|---|---|---|
| seed OpenBYD `(26,218,208,298)` | **4** | — (< `MIN_SIG_BITS`=10) | **null** |
| B3.9 `(38,38,120,110)` (960×720) | 12 | Hamming **33** > 18 | **null** |
| **`(78,50,158,163)` đo thật @1920×720** | 24 | Hamming **15–18** ≤ 18 | ✅ `maneuver_turn_normal_right` |

⇒ Cài bản trước lên xe thì kênh mũi tên **im lặng mà không báo lỗi gì**. Đã thêm entry
`Key(ARROW, 1920, 720) → WAZE_ARROW_1920x720`.

## 3. Lỗi 2 — file tạm ghi vào cacheDir riêng của app ⇒ shell không ghi được

Lệnh chụp chạy qua dadb nên tiến trình là **shell (uid 2000)**, không phải app:

```
screencap -p /data/user/0/com.byd.clusternav2/cache/screencap/cap-....png
  → Error opening file: Permission denied ; exit=1
```

Trên xe adbd chạy **root** nên đường này proven (08-17); trên mọi adbd **không root** (emulator + head-unit
user-build) thì **mọi khung bị bỏ, im lặng**. Vá theo §6 (đường cũ giữ nguyên, đường mới xuống cuối):
`ScreenCaptureTransport` thử `cacheDir` trước, thất bại **một lần** thì chuyển hẳn sang
`getExternalFilesDir(null)/screencap-tmp` và nhớ lựa chọn.

Chọn externalFilesDir chứ **không** `/data/local/tmp`: `drwxrws--- ext_data_rw` (app khác không đọc trộm ảnh
màn hình được), trong khi `/data/local/tmp` world-executable ⇒ ảnh nằm đó bị lộ.

## 4. ĐÍNH CHÍNH B3.6 — registry KHÔNG thiếu glyph Waze

Backlog ghi *"`ManeuverRegistry` toàn icon AMAP/Mapbox → mũi tên Waze luôn null"*. **Sai.**
So khớp máy: `ManeuverRegistry.RAW` **trùng 38/38 cả tên lẫn bitstring** với bảng
`jadx-openbyd24/sources/defpackage/z40.java:39` — mà đó chính là bảng OpenBYD dùng để classify **mũi tên Waze**
(`WazeArrowCaptureService` → `WazeManager.processArrowPixels`).

⇒ Vấn đề không phải "thiếu template" mà là **quy ước crop**:

- Đo cả 38 template: **`row1 = 13` cho 38/38** (mực glyph luôn kết thúc ở hàng 13/15) ⇒ template là **khung
  vẽ có lề**, KHÔNG phải bbox mực tight.
- B3.9 kết luận "crop sát glyph" ⇒ glyph lấp đầy 15×15 ⇒ lệch template ⇒ Hamming 44 ⇒ null.
- Nguồn của khung vẽ ở OpenBYD: `BydAccessibilityService.java:257` đọc **`<wazePkg>:id/navBarDirection`** qua
  a11y rồi `updateArrowBounds(rect)` — tức khung = **bounds của chính ImageView mũi tên** của Waze.
  (Cùng chỗ đó: `navBarDistance`, `navBarStreetLine`, `lblArrivalTime`, `lblTimeToDestination`,
  `lblDistanceToDestination`, `laneGuidanceView`.)

> ⚠ **WazeMod bản trên emulator dựng nav-bar bằng Compose** (`com.waze:id/mainContentCompose`), a11y
> **KHÔNG** phơi `navBarDirection` — banner chỉ là node không id, bounds `[45,36][960,186]`. Nên đường
> view-id của OpenBYD **không dùng lại nguyên si được** cho bản Waze này; hiện phải đi bằng rect cố định.

## 5. Bằng chứng chuỗi end-to-end (logcat thật)

```
I/ScreenCapTransport: fission-d1: cacheDir bị từ chối → chuyển sang externalFilesDir cho các nhịp sau
I/ScreenCapNav : arrow-sig pkg=com.chisadin.wazemod 80x113 sig=000...
I/ScreenCapNav : arrow(image) pkg=com.chisadin.wazemod maneuver=null amap=3
I/NavOutputOwner: nav output ACTIVE decided(arrow=true lane=false camera=false) sink=true
```

Tra ngược chữ ký live → `maneuver_turn_normal_right` (Hamming **18**) → `nameToAmap` → **amap=3 (rẽ phải)**,
khớp đúng glyph đang hiển thị. `sink=true` = owner đã quyết định bắn và gọi `HalSink`; **push HAL thật vẫn
là on-car** (off-car `BydHal.device` null).

## 6. Mức bằng chứng (§2) — đọc kỹ trước khi tin

| Mệnh đề | Mức |
|---|---|
| `screencap -d` không chụp được display phụ của emulator | **đã chứng minh** (file 0 byte + SF chỉ liệt kê display 0) |
| rect 960×720 và seed OpenBYD đều CÂM ở 1920×720 | **đã chứng minh** (test hồi quy trên ảnh thật) |
| shell không ghi được cacheDir app ⇒ bỏ khung | **đã chứng minh** (exit=1 Permission denied) |
| `RAW` == bảng Waze của OpenBYD | **đã chứng minh** (so khớp 38/38) |
| rect `(78,50,158,163)` là rect ĐÚNG cho cụm | **nhiều khả năng** — mới đúng trên **một** maneuver (rẽ phải), Hamming 18 = **sát trần**. Cần ≥1 maneuver khác (trái/thẳng/quay-đầu) để nâng lên "đã chứng minh" |
| rect này áp được cho cụm THẬT trên xe | **chưa biết** — cụm xe do fission dựng, chrome/letterbox của app cast có thể lệch offset |

## 7. Việc còn lại

1. **Thu thêm maneuver** (trái/thẳng/quay-đầu/vòng-xuyến) ở 1920×720 → xác nhận hoặc chỉnh rect; hiện
   margin chỉ 0 bit so với trần Hamming 18.
2. **Rect cho màn chính 1920×1080** — chưa đo, vẫn rơi về seed OpenBYD ⇒ CASE 1/2 còn câm.
3. **On-car**: chạy lại chuỗi này trên cụm thật (fission `-d 0`), đối chiếu offset.
4. Waze bản Compose: cân nhắc lấy khung từ **banner node a11y** (`[45,36][960,186]`) theo tỉ lệ thay vì rect
   tuyệt đối — generic hơn theo §7, nhưng cần nhiều mẫu mới fit được tử tế.

## 8. Ghi chú lặt vặt

- Waze chỉ hiện glyph maneuver khi GPS **nằm trên** tuyến; lệch tuyến thì banner là
  *"Proceed to highlighted route"* (mũi tên nhỏ khác hẳn) → không dùng khung đó để hiệu chỉnh.
- Bản WazeMod này có **lane strip** (4 mũi tên làn) ở đỉnh màn khi vào đoạn có làn — liên quan B3.14.

---

# Bổ sung 2026-08-22 — màn chính 1920×1080 + giới hạn banner 1 dòng

## 9. Màn phụ giả lập cụm: KHÔNG chụp được, kể cả hotplug

Thử `-hotplug-multi-display` (cờ hứa dùng "HAL hotplug display" thay virtual):

- display 2 vẫn là `mAdapter=VirtualDisplayAdapter`, `type VIRTUAL`;
- `dumpsys SurfaceFlinger --display-id` vẫn CHỈ liệt kê display 0;
- `screencap -d 2` → exit=1, file 0 byte; thử luôn virtual-id của SF (`11529215046327404770`) →
  `screencap` exit=1, `screenrecord --display-id` trả thẳng **"Invalid physical display ID"**.

⇒ **Đã chứng minh**: trên emulator không có cách nào chụp display phụ bằng công cụ shell. Cách tương đương
hợp lệ là đặt **display 0** đúng kích thước cần đo — bảng rect khoá theo **geometry của ẢNH**, không theo
display id, nên pipeline chạy y hệt.

## 10. Rect màn chính 1920×1080 = **CÙNG** rect với cụm

Đo thật (WazeMod dẫn Cửa Nam, density 240):

| | banner (hộp đen) | ink glyph | rect dùng | kết quả |
|---|---|---|---|---|
| cụm 1920×720, banner 2 dòng | — | (83,73)-(147,148) | `(78,50,158,163)` | ✅ `turn_normal_right` H=15–18 |
| chính 1920×1080, banner 2 dòng | (45,36)-(960,186) 915×150 | (83,73)-(147,139) | `(78,50,158,163)` | ✅ `turn_normal_right` H=18 |

Ink **x trùng khít** (83..147) ở cả hai ⇒ banner Waze neo theo **dp/density**, KHÔNG theo chiều cao màn.
Hằng số đổi tên `WAZE_ARROW_1920x720` → **`WAZE_ARROW_BANNER_D240`** và phục vụ cả hai entry
`(ARROW,1920,720)` + `(ARROW,1920,1080)`. ⇒ **CASE 1/2 hết câm.**

## 11. GIỚI HẠN MỚI PHÁT HIỆN — banner **1 dòng** chưa phủ

Khi tên đường ngắn, Waze rút banner còn MỘT dòng (đo: *"140 m Quang Trung"*, rẽ TRÁI):

| | hộp banner | ink glyph |
|---|---|---|
| 2 dòng | 915×**150** | 64×66 tại (83,73) |
| **1 dòng** | ×**93** | **46×53 tại (72,56)** |

Rect cố định không phủ ca này ⇒ chữ ký 14 bit, Hamming tốt nhất **27 > 18**.

**Nhưng suy giảm ĐÚNG cách: trả `null` (im lặng), KHÔNG ra hướng SAI** — nhờ `MIN_SIG_BITS=10` +
`MAX_HAMMING=18`. Đã thêm test khoá đúng tính chất này (nếu ai nới hai ngưỡng, test gãy TRƯỚC khi lên xe),
vì một mũi tên SAI hướng trên cụm/HUD nguy hiểm hơn hẳn không có mũi tên nào.

⇒ Muốn phủ nốt thì phải bỏ rect cố định, chuyển sang **dò hộp banner động** (hộp đen ở góc trên-trái) rồi
lấy khung mũi tên theo TỈ LỆ của hộp — hoặc tier-1 a11y bounds. Đây là thay đổi thiết kế, để pass riêng.
(Thử nhanh một detector "quét pixel tối" đã thấy fragile: nền bản đồ tối ở cụm làm nó nuốt cả màn.)

## 12. Trạng thái test

`assembleRelease` xanh · **core 680** (8 test `ClusterArrowCalibrationTest` trên 3 ảnh chụp THẬT) +
**app 807**, 0 fail.

## 13. Còn lại (cập nhật)

1. ~~rect màn chính 1920×1080~~ ✅ xong (dùng chung rect với cụm).
2. **Banner 1 dòng** — chưa phủ (im lặng). Cần dò hộp banner động / a11y bounds.
3. **Margin mỏng**: cả hai geometry đều Hamming **18 = sát trần**. Mới có 1 maneuver khớp (rẽ phải);
   maneuver rẽ TRÁI bắt được hôm 08-22 lại rơi đúng ca banner 1 dòng nên chưa dùng để nới margin được.
4. **Density khác 240** chưa đo.
5. **On-car**: cụm thật do fission dựng, offset chrome/letterbox app cast có thể lệch.

---

# Bổ sung 2026-08-22 (lần 2) — rect cố định bị BÁC, thay bằng dò glyph động

Owner chỉ ra: trên cụm người dùng chỉnh được **dpi / kích thước / vị trí** cửa sổ cast và cast **1 hoặc 2**
app ⇒ mọi rect cố định (kể cả rect vừa hiệu chỉnh ở §10) chỉ đúng ở một cấu hình. Xem
`docs/specs/b3-glyph-locator-cast-invariant.html`.

## 14. Ma trận đo (6 cấu hình, cùng MỘT maneuver để so được)

| màn | dpi | bbox mực glyph | cỡ |
|---|---|---|---|
| 1280×480 | 160 | (18,37)-(48,73) | 30×36 |
| 1600×600 | 200 | (23,47)-(61,91) | 38×44 |
| 1920×720 | 160 | (48,37)-(78,73) | 30×36 |
| 1920×720 | 240 | (72,56)-(118,109) | 46×53 |
| 1920×720 | 320 | (36,75)-(98,139) | 62×64 |
| 1920×1080 | 240 | (72,56)-(118,109) | 46×53 |

Chiều cao glyph / dpi ≈ **0.20–0.28** ⇒ ~33–42 dp. Đây là ràng buộc làm locator bất biến theo mật độ.

## 15. Hai hướng đã thử và BÁC (đừng làm lại)

1. **Fit một tỉ lệ nới chung** bbox-mực → khung-vẽ của OpenBYD: không tồn tại. Ở dpi thấp (160/200)
   **không padding nào** cho ra đúng template bằng Hamming; chỉ NCC mới với được, và ở padding KHÁC hẳn
   padding của dpi cao.
2. **Quét nhiều canvas ứng viên, lấy khớp tốt nhất**: **tệ hơn** — 5/9 đúng nhưng **4/9 ra SAI hướng**
   (`off_ramp_normal_left` → AMAP 4 *chếch trái* thay vì 2 *rẽ trái*). Thêm lượt thử = thêm cơ hội cho
   template sai thắng điểm. Đây là lý do không dùng "thử nhiều rồi chọn".

Nguyên nhân gốc: 38 template OpenBYD ở quy ước **khung vẽ có lề** (sinh từ bounds view a11y
`navBarDirection`), và **không suy ngược được** khung đó từ bbox mực — mực luôn kết thúc ở hàng 13/15 nhưng
hàng bắt đầu biến thiên 1..7 tuỳ maneuver.

## 16. Quy ước mới: BBOX MỰC — có bằng chứng bất biến

| phép đo | Hamming |
|---|---|
| nội-lớp (cùng maneuver; dpi 160/200/240/320 × 4 kích thước) | **0 – 17** (cùng dpi = 0) |
| liên-lớp (rẽ trái vs rẽ phải) | **62 – 72** |

Ngưỡng khớp 18 nằm gọn giữa hai biên. ⇒ chỉ cần vài template/maneuver là phủ mọi cấu hình cast.

## 17. Lỗi im lặng bắt được nhân tiện

`am stack list` trên **Android 12+/API 34** in `RootTask id=`, trong khi regex chỉ nhận `Stack id=`
(Android 10 trên xe). Không khớp dòng nào ⇒ resolver rơi hết về nhánh "không thấy task" ⇒ `windowRect` luôn
null và dpi luôn 0. Pipeline vẫn chạy bằng nhánh dự phòng nên **không lộ ra lỗi gì**. Đã nhận cả hai dạng.

## 18. Còn lại

1. **OQ1 — bản đồ chế độ ĐÊM**: ràng buộc "vành tối" yếu đi khi nền bản đồ cũng tối. Mới có 1 mẫu đêm.
2. **OQ2 — mới 2 maneuver** (trái/phải) trong registry quy ước mới.
3. **OQ3 — VietMap** chưa đo.
4. **On-car**: chưa verify trên cụm fission thật, chưa verify ca cast 2 app thật.

