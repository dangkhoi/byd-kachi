# Camera xi-nhan · VÙNG GƯƠNG nới được + HÌNH KHUNG tròn + móc ĐO HAL (R8-A) — off-car 2026-09-26

> **Trạng thái**: Current · **Cập nhật**: 2026-09-26 (off-car, **chưa lên xe**) · **Mục đích**: ghi lại phương án **A**
> của R8 (nới/đổi vùng cắt trong ảnh pano 4-in-1 + cửa sổ tròn tuỳ chọn) cùng **ba móc đo** cho buổi xe sáng 27/09, và
> nói rõ **vì sao A trước B** (shader nắn fisheye). Mọi thứ mới đứng sau chip, **mặc định = hành vi 2.73 từng pixel**.
> Liên quan: `diagnostics/electro-camera-RE-2026-09-26.md` (§2.1 · §5 K1/K2/K4/K6/K10 · §6 · §7), spec
> `specs/kachi-274-ux-voice-camera.html` R8, `diagnostics/offcar-2026-09-26/camera-aspect-and-render-path.md`
> (hình học cửa sổ 2.73), `diagnostics/offcar-2026-09-26/camera-frame-capture.md` (lệnh `camera_frame`).

---

## 0. Bốn việc trong bản này

| # | Việc | Vì sao | Trạng thái |
|---|---|---|---|
| 1 | **Sửa KDoc id camera** (`CameraSignalPolicy`) | RE §5 K1: doc ghi **ngược** id 0/1 trong khi code dùng đúng id 1 | ✅ off-car, doc-only |
| 2 | **Bề rộng + DẢI vùng gương** (`camera_span`, `camera_strip_left/right`) | RE §5 K10: 2.73 chỉ cắt `0,10` = **40 % một dải** ở rìa vòng fisheye ⇒ méo như ống; và **dải nào là hướng nào [CHƯA BIẾT]** (§7 Q1/Q2) | ✅ off-car, 🚗 mắt owner |
| 3 | **HÌNH KHUNG tròn** (`camera_shape`, `camera_circle_scale`) | owner 26/09: muốn thấy **trọn vòng ảnh** như app Electro vẽ (chưa nắn méo) | ✅ off-car, 🚗 mắt owner |
| 4 | **Ba móc ĐO HAL** (`camera_hal_mode`, `rmPreviewSurface`, log `cam_sort`) | RE §5 K2/K4/K6 · §6.3-C1: kênh `VIEW_CHANNEL_1..4` **chưa ai gọi thử**; Kachi chưa bao giờ dỡ surface | ✅ off-car, 🚗 **cả ba là phép đo** |

**Không sửa một dòng nào** của: ma trận crop+xoay (2.36 + R7), cỡ cửa sổ đúng tỉ lệ (2.73 · CAM-ROT-2), luật giữ camera
`CameraHold` (2.70), lưới an toàn `HalSignalClient` (2.72), hai bước dỡ camera `stopPreview → close`, vòng dò
`addPreviewSurface` `0..3`. Bằng chứng: xem §4.

---

## 1. Vì sao A trước B (RE §6.4)

RE xếp ba phương án: **A** = nới/đổi crop chữ nhật · **B** = shader OES nắn fisheye (copy *toán* của Electro) · **C** =
dùng nguyên vòng (C1 để HAL làm, C2 bê kiến trúc hub+FBO). Thứ tự khuyến nghị của RE là **C1 (đo) → A (ship được ngay)
→ B → C2**, và bản này làm đúng **A + móc đo của C1**, hoãn B, vì:

1. **A có thể làm B thành không cần thiết.** Vùng méo nặng nhất của fisheye là **biên**; 2.73 đang cắt đúng một vệt ở
   rìa vòng. Electro có sẵn một bố cục co source rect vào `0,03..0,97` **chỉ để giấu viền** (`p056o/C0851z.java:27`,
   [ĐO]) ⇒ "chọn đúng vùng trong vòng ảnh" là cách chống méo **không cần một dòng GL nào**. Chưa đo xem A đủ hay
   không mà đã leo lên B là vi phạm CLAUDE.md §6 (*tự đo xem đường cũ có thật hụt không rồi mới leo*).
2. **B cần ba phép đo mà off-car không có được**: một khung PNG `5120×960` thật để chỉnh `F`/`K`/tâm bằng mắt (RE §7
   Q6/Q2), `glGetIntegerv(GL_MAX_TEXTURE_SIZE)` cho texture rộng 5120 (Q13 — Electro **không hề kiểm**, nên không có
   bằng chứng nào), và ma trận `SurfaceTexture` thật của id 1 (Q17). Viết shader trước khi có ba số đó là dựng code
   cho một khả năng chưa chứng minh — đúng thứ CLAUDE.md §14 cấm.
3. **B đụng đúng vùng đang điều tra GIẬT.** `camera-lag-analysis-2026-09-26.md` L2 vẫn ở mức [CHƯA BIẾT] (hai lượt
   `gfxinfo` **cùng** bản 2.70 cho 26,9 % và 4,67 % khung giật). Thêm một lượt GPU mỗi khung có thể chữa méo mà làm
   giật thêm ⇒ B phải có số framestats trước/sau, tức phải chờ buổi xe.
4. **Không được dùng số của Electro.** Bốn tham số `F`/`K`/`SCALE`/`AMOUNT` nằm trong bytecode VM ⇒ [CHƯA BIẾT] (RE
   §0b · §7 Q6). B sẽ phải tự chỉnh bằng mắt trên xe dù có làm hôm nay hay mai.

Còn **C1 rẻ nhất** và có thể xoá cả A lẫn B (nếu HAL trả một kênh camera thì không cần crop, không cần chia dải) ⇒ đưa
vào bản này dưới dạng **một pref đo**, không phải một mặc định (§3.4).

---

## 2. Hình học: ảnh pano = 4 dải, và 2.73 đang ở đâu trong đó

[ĐO RE §2.1] Khung của `AVMCamera` id 1 là `5120×960` = **4 dải DỌC bằng nhau**, mỗi dải `0,25` bề ngang, cao trọn
khung (`4 × 1280×960`) — hằng `0,25` nằm thẳng trong shader của Electro. [ĐO kinex `Y0/C0094o.java:70,73`] hai crop mà
2.73 đang dùng là `x[0,25..0,35]` và `x[0,65..0,75]`, tức **nằm trong** dải 1 (`[0,25 … 0,50)`) và dải 2
(`[0,50 … 0,75)`), mỗi cái rộng **40 %** của dải mình.

| Dải | x chuẩn hoá | px | 2.73 dùng gì | Hướng |
|---|---|---|---|---|
| 0 | `0,00 … 0,25` | 0–1279 | — | **[CHƯA BIẾT]** |
| 1 | `0,25 … 0,50` | 1280–2559 | vệt TRÁI `0,25 … 0,35` (đầu dải) | **[SUY] trái** (kinex đặt tâm fisheye `0,375` = đúng tâm dải 1) |
| 2 | `0,50 … 0,75` | 2560–3839 | vệt PHẢI `0,65 … 0,75` (cuối dải) | **[SUY] phải** (tâm `0,625` = tâm dải 2) |
| 3 | `0,75 … 1,00` | 3840–5119 | — | **[CHƯA BIẾT]** |

⚠ Theo CLAUDE.md §14, hằng của app bên thứ ba **tối đa** là "nghi là" ⇒ cột *Hướng* chỉ chốt được bằng **một khung PNG
thật** (§5 bước 0).

### 2.1 Ba bề rộng mà bản này cho chọn (cùng một công thức, không hằng rời rạc)

| Mã | Vùng cắt (dải `s`, bên trái) | px | Cửa sổ sau xoay ±90 (vùng 360×360) |
|---|---|---|---|
| `NARROW` **(mặc định = 2.73)** | `s·0,25 … s·0,25 + 0,10` | 512×960 | **360×192** (ngang) |
| `STRIP` | `s·0,25 … (s+1)·0,25` | 1280×960 | **270×360** (dọc) |
| `ROUND` (hình tròn, bỏ qua bề rộng) | ô VUÔNG giữa dải, cạnh = chiều cao ảnh | 960×960 | **360×360** (vuông ⇒ vòng bo là hình TRÒN) |

Bên PHẢI thì `NARROW` neo vào **cuối** dải (`(s+1)·0,25 − 0,10`) — đúng thế đối xứng gương quanh `x = 0,5` mà hai crop
của 2.73 đang có. Cả bảng này là **phép tính**, không phải sáu hằng: đổi chỉ số dải là đổi một con số trong pref, không
phải một lượt build. Cửa sổ thì **tự** đúng tỉ lệ nhờ `CameraOverlayFrame` của 2.73 — bản này không sửa nó một dòng.

### 2.2 Hình TRÒN: giả định và cái núm để chữa giả định

Cửa sổ tròn = (a) vùng cắt là **ô vuông** giữa dải (cạnh = chiều cao ảnh = 960 px ⇒ `0,1875` bề ngang) nên ảnh không bị
bóp theo một trục, + (b) `Outline.setOval` trên **đúng** `ViewOutlineProvider` mà 2.73 đang dùng để bo góc (không thêm
cơ chế cắt thứ hai — quan trọng vì CLOSE-14 đang điều tra giật).

⚠ **[ĐOÁN]**: *"đường kính vòng ảnh fisheye ≈ chiều cao dải, và nằm giữa dải"*. Chưa có khung PNG nào để kiểm. Vì thế
có `camera_circle_scale` (%, mặc định 100, dải nhận **50…100**) để co ô vuông ngay trên xe nếu vòng ảnh nhỏ hơn. Trần
là 100 vì ở 100 cạnh đã bằng **trọn** chiều cao ảnh — không còn pixel nào để giãn; nếu vòng ảnh **rộng hơn cao** (bị
cắt trên/dưới) thì đó là một **phát hiện phải báo**, không phải một núm (xem §5 điểm quyết định D3).

⚠ Đường `SurfaceView` (`camera_render = SV`): vòng bo [ĐOÁN] **không ăn** (layer riêng do SurfaceFlinger ghép, cùng chỗ
chưa biết đã ghi ở KDoc bo góc của 2.73) ⇒ chip "Tròn" nên thử ở đường `TextureView` (mặc định).

---

## 3. Ba móc ĐO cho HAL (không cái nào là mặc định)

### 3.1 KDoc id camera — sửa cái sai, không đổi hành vi

[ĐO carlog xe của chính Kachi, `carlog-kachi-20260914-2044/10-logcat-baseline.txt:1776`]
`vehicle.config.cam_sort:rear:0;pano_h:1;`. Đọc đúng là: **AVMCamera phơi ra 2 LUỒNG (id)** —
`rear` = id 0 (luồng camera lùi, đứng riêng) và `pano_h` = id 1 (**4 camera fisheye đã được HAL ghép sẵn thành MỘT
khung** `5120×960`, 4 dải 25 %). Xe Seal Performance VN có **4 camera vật lý** (trước · sau · hai dưới gương — owner
xác nhận 26/09); chúng đi chung luồng id 1. Tag là hằng BYD (`DiLinkCameraConstants.java:19,21`). Khớp Electro:
`selectedCameraId=1 renders-full-frame`. KDoc tới 2.73 ghi ngược ⇒ sửa doc, **enum vẫn `cameraId = 1`**, không đổi một
hằng nào.

### 3.2 `cam_sort` — chỉ GHI NHẬT KÝ, không gate

[ĐO firmware] launcher gốc dò camera bằng đúng khoá này (`VehicleUtils.java:176,187-192` →
`SystemProperties.get("vehicle.config.cam_sort","")` rồi `hasAVMRecorder() = contains("pano_h")`) — rẻ hơn mở camera để
xem có ra hình. Bản này ghi **một dòng** lúc bật tính năng (thread nền) và **không gate gì**: chưa ai đo trên một đời xe
thứ hai, nên biến nó thành cổng mở camera là tự tắt tính năng trên mọi trim mà ROM viết khác dấu phân cách (CLAUDE.md
§3 · §7).

### 3.3 `rmPreviewSurface` khi dỡ camera (RE §5 K6)

[ĐO firmware] hàm có thật trên **chính lớp framework**: `com/byd/dilink51_main/hardware/camera/DiLinkAVMCamera.java:143-144`
gọi `f321DDC.rmPreviewSurface(surface, i)` với `f321DDC` khai `android.hardware.AVMCamera` (`:22`, import `:4` — dòng `:3` là `android.graphics.SurfaceTexture`); chữ ký
trong SDK là `IDiLinkAVMCamera.java:38` `boolean rmPreviewSurface(Surface, int)`. Tên `removePreviewSurface`
**không tồn tại** (0 hit trên firmware + jadx kinex/electro/openbyd) ⇒ chỉ thử một tên.

⚠ Đặt **SAU** `stopPreview` + `close` dù Electro làm `stop → rm → release`: đường hai bước của Kachi **đang chạy tốt
ngoài hiện trường**, và CLAUDE.md §6 cấm đảo thứ tự một đường như thế để chữa cho một thứ chưa đo. Log mức DEBUG, vì
camera đã đóng thì HAL có quyền từ chối — đó là ca **bình thường**, không phải lỗi.

⚠ **CẬP NHẬT sau soát Opus 2026-09-27 — bước này CÓ CỔNG, không chạy mọi lượt dỡ.** `rmPreviewSurface` chỉ là một
**móc ĐO** (để buổi xe đọc được dòng `rmPreviewSurface(mode=…) rc=…` mà runbook CAM-A5 hứa), nên nó được gác sau
`AvmCamera.rmOnClose`, gán từ `TestBridgeStore.isOn` trong `CameraSignalController.stop()`: **chỉ chạy khi Chế độ
kiểm thử đang BẬT**. Lý do: lớp `android.hardware.AVMCamera` **không có trong workspace** ⇒ hành vi của nó sau
`close()` là **[CHƯA BIẾT]**, và một lỗi native ở đó là thứ **không bắt được** bằng `runCatching` ⇒ sẽ hạ launcher
trên xe **đang chạy**. Vì vậy chuỗi dỡ **mặc định** (ngoài chế độ kiểm thử) là `stopPreview` + `close`, **y từng byte
như 2.73**.

### 3.4 `camera_hal_mode` — kênh xem (RE §5 K4 · §6.3-C1)

[ĐO firmware] `addPreviewSurface(Surface, int)` (`IDiLinkAVMCamera.java:12`); miền của `int` là
`DiLinkCameraConstants.java:47-55`: `VIEW_DEFAULT = 0`, `VIEW_CHANNEL_1..4 = 1..4`. 2.73 dò `0..3` **và bỏ qua giá trị
trả về** ⇒ gần như luôn dừng ở `0` (khung ghép), và **chưa bao giờ thử 4**. [SUY mạnh] `VIEW_CHANNEL_n` có thể bắt HAL
trả **một kênh camera** thay vì khung ghép — nếu đúng thì **cả tầng crop thành không cần**, và ảnh có thể đã được HAL
nắn sẵn.

- Pref vắng (mặc định) = **tự dò**, vòng `0..3` giữ **nguyên từng byte**.
- Pref `0..4` ⇒ gọi **đúng một lần** với kênh đó **và đọc `rc`** — đó mới là một phép đo. HAL từ chối ⇒ rơi về đường
  `addPreviewSurface(Surface)` một tham số như 2.73, và **dòng log in `rc`** để lượt đo không bị đọc thành *"kênh n
  chạy"* khi ảnh thật ra tới từ đường dự phòng.

---

## 4. Bằng chứng "mặc định không đổi một pixel nào"

| Ghim ở đâu | Ghim cái gì |
|---|---|
| `CameraPanoCropTest.mac dinh trung 2 rect cua 2 73` | `(dải 1, NARROW, trái)` ⇒ **`0.25, 0, 0.35, 1`** và `(dải 2, NARROW, phải)` ⇒ **`0.65, 0, 0.75, 1`**, sai số `0f`, và **bằng đúng** hằng `CamView.crop` mà 2.73 truyền vào overlay |
| `CameraPanoCropTest.pref rac hoac vang thi ve dung rect cu` | bốn pref **vắng hoặc rác** (xe nâng cấp từ 2.73) ⇒ vẫn đúng hai rect trên |
| `CameraPanoCropTest.bon goc crop phu kin cua so …` | 3 bề rộng × 4 góc xoay: bốn góc crop đi đúng bốn góc cửa sổ ⇒ **không một pixel đen nào**, với ma trận 2.73 **không sửa** |
| `CameraSignalPolicyTest.mac dinh be rong, hinh khung, dai, kenh HAL …` | `NARROW` · `RECT` · dải 1/2 · kênh `AUTO(−1)` · `circle 100` |
| `CameraSpanShapeWiringContractTest.vong do 0 3 …` | hai dòng của vòng dò `addPreviewSurface` giữ **nguyên văn**, và chỉ chạy khi pref = AUTO |
| `CameraSpanShapeWiringContractTest.close giu thu tu 2 73 …` | thứ tự theo **vị trí** trong thân hàm: `stopPreview` < `close` < `rmPreviewSurface` |
| `CameraSpanShapeWiringContractTest.hinh tron dung dung ViewOutlineProvider …` | **một** `ViewOutlineProvider`, hai nhánh; không `clipPath`/`BitmapShader` |

Phép thử đột biến đã chạy: đổi `defaultSpan()` sang `STRIP` ⇒ **5 bài đỏ** (4 ở `CameraPanoCropTest`, 1 ở
`CameraSignalPolicyTest`), rồi khôi phục ⇒ xanh lại. Tức lớp canh **thật sự** bắt được lượt đảo mặc định.

---

## 5. 🚗 Buổi xe sáng 27/09 — làm theo đúng thứ tự này

Mọi lệnh đi qua cầu kiểm thử (**Chế độ kiểm thử phải đang BẬT** trong Cài đặt). Dùng đúng biến `BR` của runbook
(`am broadcast -n <pkg>/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a <pkg>.TEST`):

```
BR --es cmd prefs_set --es key <khoá> --es text <giá trị>
```

Lời đáp mang `read_back` = **giá trị thật sau lượt ghi** ⇒ nếu `read_back` khác cái vừa gõ thì dừng lại, đừng đo tiếp.

### Bước 0 — lấy MỘT khung thô (việc quan trọng nhất của cả buổi)

```
BR --es cmd camera --es name left      # ép overlay bên trái lên  (cờ là --es name, KHÔNG phải --es arg)
BR --es cmd camera_frame               # PNG cỡ nguồn 5120×960 vào files/kachi-logs/
```

PNG **đen** ⇒ hạ cỡ: `--es name 4096x768`, rồi `2560x480` (trần texture GPU). Có PNG rồi thì cắt 4 dải và **xem mắt** —
một lần này chốt luôn RE §7 **Q1 + Q2** (dải nào là hướng nào, dải 0/3 là gì) và trả lời cả §2.2 (vòng ảnh to bằng bao
nhiêu, có nằm giữa dải không). **Nếu chỉ làm được một việc trong cả buổi thì làm việc này.**

### Bước 1 — nới bề rộng, bên TRÁI trước

```
BR --es cmd prefs_set --es key camera_span --es text STRIP
```
Bật xi-nhan trái, **xem**. Ba khả năng:
- **thấy trọn hình camera gương, méo ít hơn** ⇒ A đủ. Giữ `STRIP`, sang bước 3.
- **thấy đúng hướng nhưng vẫn méo như ống** ⇒ A chưa đủ ⇒ ghi lại, B (shader nắn) là việc kế (spec riêng).
- **thấy sai hướng** (không phải gương trái) ⇒ sang bước 2.

Về mặc định bất cứ lúc nào: `… --es key camera_span --es text NARROW` (hoặc chạm chip *Vệt hếp* trong Cài đặt).

### Bước 2 — dò DẢI (chỉ khi bước 1 cho sai hướng)

```
BR --es cmd prefs_set --es key camera_strip_left  --es text 0    # xem, rồi 3, rồi 2 — dừng ở dải ra ĐÚNG gương trái
BR --es cmd prefs_set --es key camera_strip_right --es text 3    # bên phải: thử 3, rồi 0, rồi 1
```
Ghi lại cặp (dải → hướng) cho **cả bốn** dải nếu xem đủ: đó là câu trả lời [ĐO] cho Q1/Q2, và nó vào
`ClusterProfile`/spec chứ không rải trong code.

### Bước 3 — thử khung TRÒN

```
BR --es cmd prefs_set --es key camera_shape --es text ROUND
```
Bật xi-nhan, **xem**. Nếu vòng ảnh **nhỏ hơn** khung tròn (thấy viền đen quanh vòng):
```
BR --es cmd prefs_set --es key camera_circle_scale --es text 90    # rồi 80, 70… tới khi vòng ảnh vừa khít
```
Về chữ nhật: `… --es key camera_shape --es text RECT` (một cú chạm chip *Chữ nhật* cũng được).

### Bước 4 — dò KÊNH HAL (móc đo C1, làm sau cùng vì có thể mất hình)

Với **mỗi** `n` trong `1, 2, 3, 4`:
```
BR --es cmd prefs_set --es key camera_hal_mode --es text <n>
# bật xi-nhan, xem; rồi chụp khung:
BR --es cmd camera_frame
```
Đọc `logcat -s KachiCamera` tìm dòng `addPreviewSurface … halMode=<n> rc=…`:
- `rc=false`/`rc=null` ⇒ ROM **không nhận** kênh đó (kết luận sạch, ghi lại rồi thử `n` kế).
- `rc=true` **và** khung PNG khác hẳn (một camera, không phải 4 ô ghép) ⇒ **phát hiện lớn**: cả tầng crop thành không
  cần ⇒ dừng buổi đo ở đây và báo, đừng chỉnh tiếp bề rộng/dải.
- `rc=true` nhưng khung vẫn là ảnh ghép ⇒ kênh không có tác dụng trên trim này.

Về mặc định: `… --es key camera_hal_mode --es text -1` (hoặc chip *Tự dò*).

Ở cùng lượt, `logcat` cũng có sẵn (không cần gõ thêm gì): `vehicle.config.cam_sort='…' ids={…} panoId=…` (§3.2) và
`rmPreviewSurface(mode=…) rc=…` mỗi lần overlay đóng **khi Chế độ kiểm thử đang BẬT** (có cổng — §3.3).

### Điểm quyết định cho owner

| Mã | Câu hỏi | Nếu ĐÚNG | Nếu SAI |
|---|---|---|---|
| **D1** | `STRIP` có làm ảnh gương dùng được không? | chốt `STRIP` làm mặc định ở bản sau (đổi `defaultSpan`) | giữ `NARROW`, mở spec **B** (shader nắn) với ba phép đo của §1 điểm 2 |
| **D2** | Dải nào là hướng nào? | ghi [ĐO] vào spec + `ClusterProfile`; đổi mặc định `defaultStrip` nếu khác 1/2 | đo lại ở bước 0 bằng PNG, đừng đoán từ màn hình nhỏ |
| **D3** | Vòng ảnh có nằm giữa dải và cao bằng dải không? | `camera_circle_scale` giữ 100, chốt `ROUND` làm một lựa chọn thật | ghi **số đo thật từ PNG** (đường kính + tâm) → cần đổi công thức ô vuông, **không** phải nới trần núm |
| **D4** | `VIEW_CHANNEL_n` có trả một kênh camera? | **bỏ A và B**, đi hướng C1 (HAL làm hết) — cần spec riêng | ghi [ĐO] "kênh không có tác dụng trên trim này", đóng RE §7 Q3 |
| **D5** | Tròn có đẹp/hữu dụng hơn chữ nhật khi lái? | giữ chip, xét đổi mặc định | giữ mặc định chữ nhật |

---

## 6. Còn CHƯA BIẾT sau bản này

| # | Câu | Mức | Chốt bằng |
|---|---|---|---|
| U1 | Dải 0 và 3 là hướng nào | [CHƯA BIẾT] | 🚗 bước 0 + bước 2 |
| U2 | Dải 1 = trái, dải 2 = phải | [SUY] (hằng kinex) | 🚗 bước 0 |
| U3 | Đường kính + tâm vòng ảnh fisheye trong một dải | [ĐOÁN] (= chiều cao dải, giữa dải) | 🚗 bước 0 (đo trên PNG) |
| U4 | `VIEW_CHANNEL_1..4` trả gì | [SUY] từ hằng SDK | 🚗 bước 4 |
| U5 | Kachi có rò rỉ surface khi bật/tắt overlay nhiều lần | [SUY] (RE K6) | 🚗 `dumpsys SurfaceFlinger` trước/sau 20 lượt xi-nhan |
| U6 | Vòng bo có ăn trên đường `SurfaceView` | [ĐOÁN] không | 🚗 bước 3 với `camera_render SV` |
| U7 | `A` có đủ hay phải leo lên `B` | [CHƯA BIẾT] | 🚗 D1 (mắt owner trên đường thật) |

---

## 7. Dòng cần thêm vào runbook (cho điều phối)

Chèn vào `diagnostics/oncar-runbook-2.74.md`, khối camera, **sau** các mục must-have 2.73 còn lại:

1. `CAM-A0` (3 phút, **ưu tiên cao nhất của khối camera**) — `camera` + `camera_frame` lấy PNG `5120×960`; đen ⇒ hạ
   `4096x768` → `2560x480`. Kết quả chốt RE Q1/Q2 + §2.2. *Không làm được bước này thì bốn bước sau chỉ là xem mắt.*
2. `CAM-A1` (2 phút) — `BR --es cmd prefs_set --es key camera_span --es text STRIP`, xi-nhan trái, xem → **D1**. Về `NARROW` nếu xấu hơn.
3. `CAM-A2` (4 phút, chỉ khi A1 sai hướng) — `BR … --es key camera_strip_left --es text 0|3|2` và `camera_strip_right 3|0|1`, ghi cặp
   (dải → hướng) → **D2**.
4. `CAM-A3` (3 phút) — `BR … --es key camera_shape --es text ROUND`; viền đen quanh vòng ⇒ `camera_circle_scale 90|80|70` → **D3**.
   Thử lại một lượt với `camera_render SV` để trả lời U6.
5. `CAM-A4` (6 phút, **làm cuối khối** vì có thể mất hình) — `BR … --es key camera_hal_mode --es text 1..4`, mỗi giá trị kèm
   `camera_frame` + đọc `logcat -s KachiCamera` dòng `halMode=… rc=…` → **D4**. Kết thúc: `camera_hal_mode -1`.
6. `CAM-A5` (0 phút, đọc kèm) — trong `logcat -s KachiCamera` lấy sẵn: `vehicle.config.cam_sort=…` (§3.2) và
   `rmPreviewSurface(mode=…) rc=…` (§3.3 — **chỉ khi Chế độ kiểm thử BẬT**; các bước CAM-A đều qua `BR` nên nó
   luôn bật). Chụp màn/lưu log, không cần gõ lệnh riêng.

Dọn sau buổi đo (một dòng mỗi khoá): `camera_span NARROW` · `camera_shape RECT` · `camera_circle_scale 100` ·
`camera_hal_mode -1` · `camera_strip_left 1` · `camera_strip_right 2`.
