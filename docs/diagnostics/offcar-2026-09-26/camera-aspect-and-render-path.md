# Camera xi-nhan · khung ĐÚNG TỈ LỆ (CAM-ROT-2) + đường KẾT XUẤT chọn được (CLOSE-14) — off-car 2026-09-26

> **Trạng thái**: Current · **Cập nhật**: 2026-09-26 (off-car, chưa lên xe) · **Mục đích**: ghi lại hình học mới của
> cửa sổ overlay camera (đúng tỉ lệ vùng crop sau xoay, **không viền đen, không méo**) và lựa chọn đường kết xuất
> `TextureView`/`SurfaceView` để buổi xe tới **đo** nguồn giật (L2) mà không build lại.
> Liên quan: `docs/specs/camera-turn-signal-hal-socket.html` (R7 · R8), `diagnostics/camera-lag-analysis-2026-09-26.md`
> (L1–L4), `diagnostics/perf-oncar-2026-09-26/camera-fps-2.70.md` + `camera-after-2.70.md`, backlog **CAM-ROT-2** ·
> **CLOSE-14 · CAM-LAG**.

---

## 0. Hai việc, hai lý do

| Việc | Nguyên văn owner (2026-09-26) | Trạng thái |
|---|---|---|
| **A · Khung đúng tỉ lệ** | *"không muốn có viền đen, nên làm overlay cho nó đúng với tỷ lệ camera, không fix bừa"* (quyết định 7c) | ✅ off-car, 🚗 mắt owner + cỡ ảnh nguồn thật |
| **B · Đường kết xuất chọn được** | *"hiện tại nó hơi giật lag khi xe chạy"* (CLOSE-14) | ✅ off-car (cờ Cài đặt, **mặc định KHÔNG đổi**), 🚗 đo trên xe |

Cả hai đều **không** sửa một dòng nào của đường đã chạy hiện trường: ma trận crop+xoay (2.36 + R7) giữ nguyên, luật
giữ camera `CameraHold` (2.70) giữ nguyên, lưới an toàn `HalSignalClient` (2.72) giữ nguyên. Việc A chỉ **đổi cỡ cửa
sổ**; việc B là một nhánh **phụ** phải tự bật.

---

## 1. Việc A · Hình học: vì sao đổi CỠ CỬA SỔ chữa được cả viền đen lẫn méo

### 1.1 Trước bản này (2.35 → 2.72)

- Cửa sổ là **ô VUÔNG** cạnh `0,50 × chiều cao màn` (màn chính 1920×720 ⇒ **360×360 px**).
- Ảnh nguồn của cam gương là **dải DỌC**: crop `x[0,25..0,35] × y[0..1]` của ảnh 4-in-1 ⇒ **512×960 px**
  ([ĐO RE kinex `Y0/C0094o.java:318,342`]: pano `5120×960`; một cam đơn `1280×960`).
- Dải `512×960` bị **căng không đẳng hướng** cho lấp kín ô vuông ⇒ giãn ngang **1,875×**. Đó chính là thứ owner thấy:
  hoặc hình bị kéo, hoặc (nếu chống kéo) hai dải đen.

Kiểm được bằng số, off-car: bài đối chứng `khung vuong 2_72 thi meo` đo **tỉ số giãn x/y = 1,875** trên đúng ma trận
đang chạy.

### 1.2 Sau bản này

Ô vuông cũ **không mất** — nó trở thành **VÙNG CHO PHÉP** (trần). Cửa sổ thật = hình lớn nhất **đúng tỉ lệ vùng crop
sau xoay** nằm trong vùng đó, **căn giữa** vùng. Ba hệ quả:

1. **Không viền đen** — vì cửa sổ co lại bằng đúng ảnh, không phải ảnh co lại trong cửa sổ.
2. **Không méo** — bước bù tỉ lệ của ma trận (`scale(vw/vh, vh/vw)` khi xoay ±90) trở thành phép **đẳng hướng** ngay
   khi tỉ lệ khung = tỉ lệ ảnh sau xoay. Không phải công thức mới; là công thức cũ được cho một khung đúng.
3. **Không lấn chỗ** — cửa sổ không bao giờ to hơn vùng cho phép ⇒ lời hứa *"không đè thanh trên, không chiếm chỗ làm
   việc"* (spec R2) giữ nguyên.

### 1.3 Quy tắc, viết bằng lời

- Vùng crop tính ra **px nguồn**: `cw_px = (x1−x0) × streamW`, `ch_px = (y1−y0) × streamH`. Crop **áp trong toạ độ ảnh
  nguồn, TRƯỚC khi xoay** — đúng thứ tự ma trận đang làm.
- Góc là **bội LẺ của 90** (±90, 270…) ⇒ **đổi vai hai trục**: tỉ lệ khung = `ch_px : cw_px`. Góc 0/180 ⇒ `cw_px : ch_px`.
- Khung = hình lớn nhất của tỉ lệ đó **vừa khít** vùng `areaW × areaH` (so hai tỉ lệ bằng phép nhân chéo, không chia
  ⇒ không có ca chia-0). Cạnh bị kẹp trong `1..area` (cửa sổ 0 px = overlay vô hình mà không ai báo lỗi).
- Vị trí: `x = lề bên + (areaW − khungW)/2`, `y = lề trên + (areaH − khungH)/2`. `x`/`y` của `WindowManager` là độ lệch
  kể từ góc mà `gravity` chọn ⇒ một công thức dùng chung cho cả góc trên-trái và trên-phải.

### 1.4 Bảng số (tất cả đều là **bài test**, không phải ví dụ suông)

| Ảnh nguồn | Crop | Góc | Vùng | ⇒ Cửa sổ | Ghi chú |
|---|---|---|---|---|---|
| 1280×720 | toàn khung | 0 · 180 | 800×600 | **800×450** | 16:9 rộng hơn vùng ⇒ bề rộng quyết định |
| 1280×720 | toàn khung | 90 · 270 | 800×600 | **338×600** | `600 × 720/1280 = 337,5 → 338` |
| 5120×960 | gương trái `x[0,25..0,35]` | −90 (mặc định bên trái) | 360×360 | **360×192** | `360 × 512/960 = 192` |
| 5120×960 | gương trái | 0 | 360×360 | **192×360** | dải DỌC giữ nguyên chiều |
| 5120×960 | gương trái | 180 | 360×360 | **192×360** | 180° không đổi vai hai trục |
| 5120×960 | toàn khung | 0 | 360×360 | **360×68** | `360 × 960/5120 = 67,5 → 68` |
| *chưa biết* (0 hoặc âm) | bất kỳ | bất kỳ | 360×360 | **360×360** | **giữ đúng ô vuông 2.72** — không đoán tỉ lệ |

### 1.5 ⚠ Khung sau xoay ±90 của dải gương là khung **NGANG**, không phải khung dọc

Backlog CAM-ROT-2 viết *"khung dọc thay khung vuông"* — đó là **[SUY] của người ghi**. Hình học nói khác, và có hai
bằng chứng độc lập:

- **[ĐO tính toán]** dải gương là `512×960`; xoay ±90 đổi vai hai trục ⇒ ảnh hiện ra `960×512` = **1,875:1 NGANG**.
  Đúng chiều vật lý của một cam gương: trường nhìn **rộng**, thấp.
- **[ĐO RE kinex]** blind-spot của kinex khi xoay 90/270 xuất khung **640×480 NGANG** (`Y0/C0094o.java:343-344`).

Muốn khung **4:3** như kinex thì phải **thu dải y của crop** (`C0094o.java:330-336`:
`y_span = min(cw_norm × inputW × 4/3 ÷ 960, ch_norm)`) — tức **đổi crop**, tức đổi đường đã chạy hiện trường
(CLAUDE.md §6), và backlog ghi rõ *"Không tự làm"*. ⇒ **để owner quyết sau khi nhìn thấy khung 360×192 thật.**

---

## 2. Cỡ ảnh nguồn lấy ở đâu (KHÔNG hardcode)

Thứ tự, dừng ở nguồn đáng tin nhất có sẵn:

| # | Nguồn | Mức bằng chứng | Khi nào dùng |
|---|---|---|---|
| 1 | `AVMCamera.getPreviewWidth()` / `getPreviewHeight()` qua reflection, đọc **sau `startPreview`** | **[ĐO RE]** hai hàm CÓ THẬT trong lớp framework: firmware DiLink5.1 `com/byd/dilink51_main/hardware/camera/DiLinkAVMCamera.java` bọc thẳng chúng. **[CHƯA BIẾT]** trim DiLink3.0 trên xe owner có trả số đúng hay trả 0 ⇒ 🚗 | luôn ưu tiên; trả `≤ 0` hoặc ném ⇒ coi như **chưa biết** |
| 2 | **Gợi ý** theo view: `CamView.hintW/hintH` = `5120×960` cho hai view GƯƠNG | [ĐO RE kinex] + chính crop của hai view đó đã giả định ảnh 4-in-1 | lượt dựng cửa sổ ĐẦU TIÊN (trước khi camera mở xong) |
| 3 | Không gì cả (mọi view khác: `hintW = hintH = 0`) | — | ⇒ **giữ ô vuông 2.72**, không đoán tỉ lệ (CLAUDE.md §2) |

Khi (1) có số và số đó khác thứ đang dùng, cửa sổ **được dựng lại ngay** (`WindowManager.updateViewLayout`) và
`TextureView` tự nhận `onSurfaceTextureSizeChanged` ⇒ ma trận tính lại cho khung mới. Tức đường đi thật là:
*gợi ý → (≈ 0,4 s sau, khi camera lên) số đo thật → khung chuẩn*, và bước sau **không** phá bước trước.

`SurfaceTexture.setDefaultBufferSize` **không** được dùng để đoán cỡ: `TextureView` tự đặt buffer theo cỡ view, còn
HAL là **producer** nên chính nó quyết cỡ khung thật ⇒ đọc lại buffer size chỉ trả về cỡ view, không phải cỡ ảnh.

---

## 3. Việc B · Đường KẾT XUẤT chọn được (CLOSE-14 · L2)

### 3.1 Vì sao là một chip, không phải một lượt đổi mặc định

`camera-lag-analysis-2026-09-26.md` L2 để `TextureView` ở mức **[CHƯA BIẾT]**, và số liệu trên xe **mâu thuẫn**: hai
lượt `gfxinfo` **cùng bản 2.70** có camera hiện cho **26,9 %** (p99 400 ms) và **4,67 %** (p99 150 ms) khung giật. Đổi
mặc định để chữa một thứ chưa đo là đúng cái bệnh CLAUDE.md §6 cấm. Nên: **hai đường, mặc định = đường đang chạy.**

### 3.2 Hai đường, cái gì mất

| | `TV` — `TextureView` (**mặc định**) | `SV` — `SurfaceView` (phụ, để ĐO) |
|---|---|---|
| Vẽ ở đâu | TRONG cây view (HWUI) ⇒ +1 lượt GPU mỗi khung | **layer riêng**, SurfaceFlinger ghép |
| Crop vùng gương | ma trận `setTransform` | **cỡ + lề ÂM** của chính lớp video (phóng `1/cw` rồi kéo lệch) |
| Xoay (R7) | ma trận, chắc chắn | **chỉ còn** `AVMCamera.setDisplayOrientation(Surface, int)` — [ĐO RE] có hàm, **[CHƯA BIẾT]** ROM có thi hành |
| Bo góc lớp video | ăn thật (`clipToOutline` của view cha) | **[CHƯA BIẾT]** ROM có bo/cắt layer con theo biên cửa sổ hay không (đúng lý do 2.3x đổi sang `TextureView`) |
| Tỉ lệ cửa sổ | theo góc đã chọn | theo góc **chỉ khi** HAL nhận xoay; HAL từ chối ⇒ lấy tỉ lệ **chưa xoay** (không giả vờ đã xoay) |

Nhãn chip nói thẳng cái mất — *"SurfaceView (nhẹ hơn, xoay nhờ HAL — có thể không xoay)"* — thay vì im lặng bỏ góc
xoay owner đã chọn.

### 3.3 Mấy việc rẻ, KHÔNG đổi mặc định, đã soát trong bản này

| Việc | Trạng thái |
|---|---|
| `isOpaque = true` cho `TextureView` | **đã có từ trước**, giữ. Nền bo góc nằm ở view **CHA** (`GradientDrawable` + `clipToOutline`) nên `TextureView` không cần alpha của riêng nó ⇒ không phải đánh đổi gì |
| Không cấp phát `Matrix` mỗi khung | **[ĐO code]** `Matrix` chỉ dựng trong `applyTransform`, gọi ở hai callback **đổi cỡ** (available / size-changed). Bài test đếm đúng **1** chỗ `Matrix()` trong tệp |
| Không log / không việc gì mỗi khung | `onSurfaceTextureUpdated {}` và `surfaceChanged {}` **trống** — có bài test regex canh chúng trống |
| Không shell / `dumpsys` trong đường vẽ | bài test cấm `Runtime.getRuntime` · `dumpsys` · `ProcessBuilder` · `KachiShell` xuất hiện trong tầng vẽ overlay |
| Cửa sổ `PixelFormat.TRANSLUCENT` | **giữ** (đường đã chạy). `OPAQUE` là một đòn bẩy 🚗 khác: nó bỏ blend nhưng bốn góc bo sẽ thành đen — chỉ đổi sau khi đo |
| `setCameraFps(15)` (L3) | **không chạm** — đổi fps là đổi tải HAL, phải đo trước (playbook L3 của doc phân tích) |

---

## 4. Mã đã đổi

| Tầng | Tệp | Việc |
|---|---|---|
| `:core` | `launcher/camera/CameraOverlayFrame.kt` **(mới)** | `fit(streamW, streamH, crop, rotationDeg, areaW, areaH) → Frame(w, h, streamKnown)` · `stretch(frameW, frameH, crop) → (w, h, x, y)` cho đường `SurfaceView` · `quarterTurn(deg)`. Thuần, không `android.*` |
| `:core` | `launcher/camera/CameraSignalPolicy.kt` | `RENDER_TEXTURE`/`RENDER_SURFACE` · `RENDERS` · `defaultRender()` · `isRender()` · `rotatesByMatrix()`; `CamView` thêm `hintW`/`hintH` (chỉ hai view GƯƠNG có số) |
| `:core` | `launcher/testbridge/TestBridgeCommand.kt` | danh sách trắng `prefs_set` **23 → 24**: `+camera_render` |
| `:app` | `launcher/camera/CameraOverlayView.kt` | vùng cho phép vs cửa sổ; cửa sổ lấy cỡ từ `:core`, căn giữa vùng; `onStreamMeasured(streamW, streamH, rotationEffective)` dựng lại cửa sổ; hai nhánh kết xuất; bỏ accessor `surfaceView()` không có chỗ gọi nào |
| `:app` | `launcher/camera/AvmCamera.kt` | `previewSize()` (`getPreviewWidth/Height`, `≤ 0` ⇒ `null`) · `setDisplayOrientation(surface, deg)` (best-effort, trả "có nhận") |
| `:app` | `launcher/camera/CameraSignalController.kt` | đọc `camera_render`; truyền `render` + gợi ý cỡ; sau khi mở camera: thử xoay qua HAL (chỉ khi `SurfaceView`), đo cỡ, báo `onStreamMeasured`; log thêm `kết xuất=` |
| `:app` | `PrefsAutomation.kt` | khoá `camera_render` (device-scope) + đọc-lạ-rơi-về-mặc-định |
| `:app` | `launcher/ClusterNavBridgeAutomation.kt` | `cameraRender()` / `setCameraRender(v)` |
| `:app` | `launcher/SettingsSectionsCar.kt` | một hàng chip *Kết xuất camera* (2 chip, mã từ hằng `:core`) |
| `:app` | `launcher/testbridge/TestBridgePrefsSet.kt` | nhánh ghi + `read_back` cho `camera_render` |
| `:app` | `res/values{,-en}/strings_kachi.xml` | 4 chuỗi mới × 2 ngôn ngữ |

### Test thêm

| Tệp | Bài | Cái nó khoá |
|---|---|---|
| `:core CameraOverlayFrameTest` **(mới)** | 10 | bảng số 1280×720 × 4 góc · ca thật 512×960 trong vùng 360 · luôn nằm trong vùng · chưa-biết-cỡ ⇒ ô vuông 2.72 · crop suy biến · **không méo** (đo tỉ số giãn x/y qua `mapSource`, ≈ 1) · **bài đối chứng** khung vuông ⇒ 1,875 · `stretch` cắt đúng dải bằng lề âm · `quarterTurn` với góc âm/>360 |
| `:core CameraSignalPolicyTest` | +3 (8 → 11) | mặc định kết xuất = `TextureView` · `isRender` loại mã lạ · `rotatesByMatrix` theo đường · gợi ý cỡ **chỉ** có ở hai view gương (các view khác phải để 0) |
| `:app CameraFrameAndRenderWiringContractTest` **(mới)** | 9 | cửa sổ lấy cỡ từ `:core` + căn giữa · `onStreamMeasured` gọi `updateViewLayout` + HAL trả 0 thì giữ gợi ý · **cỡ ảnh không hardcode trong `:app`** · hai nhánh kết xuất + `setZOrderMediaOverlay` (không `setZOrderOnTop`) · controller truyền `render` + báo `rotationEffective` thật · `AvmCamera` gọi đúng tên hàm framework · chip Cài đặt lấy mã từ `:core` + nhãn nói rõ giới hạn HAL · pref/whitelist/`prefs_set`/`read_back` · **đường khung hình trống** (không log, 1 chỗ `Matrix()`, không shell) |
| `:core TestBridgeCommandTest` | sửa | số khoá 23 → 24 |

**Thử-ĐỎ [ĐO 2026-09-26 off-car]** — hai mutation, mỗi bài đỏ đúng chỗ, rồi khôi phục:

| # | Sửa gì | Kết quả |
|---|---|---|
| M1 | bỏ phép **đổi vai hai trục** khi xoay ±90 trong `CameraOverlayFrame.fit` | `:core` **21 tests / 4 failed** — `bang 1280x720…`, `guong trai 512x960…`, `khong meo khi khung dung ti le`, `goi y co anh nguon…` |
| M2 | cửa sổ lấy **cỡ VÙNG** thay vì cỡ khung đã tính (`layoutParams`) | `:app` **9 tests / 1 failed** — `cua so lay co tu core va can giua vung` |


---

## 5. 🚗 Việc trên xe (checklist chính xác)

> Cần: xi-nhan bật ≥ 20 s mỗi lượt; công tắc *Camera theo xi-nhan* đang BẬT. `<pkg>` = gói launcher.

### D-A · Khung đúng tỉ lệ (việc A)

1. **Xem cỡ khung mà app tự tính** (không cần chụp màn):
   ```
   adb logcat -c ; adb logcat -s KachiCamera
   ```
   Bật xi-nhan trái. Phải thấy **hai** dòng:
   - `overlay show corner=TL side=LEFT cluster=false rot=-90 kết xuất=TV khung=360x192 vùng=360x360 nguồn-biết=true`
   - `previewSize HAL = <W>x<H>` **hoặc** `previewSize HAL trả 0x0 ⇒ coi như chưa biết`
   - nếu cỡ thật khác gợi ý: `overlay cỡ nguồn <W>x<H> xoay-thật=true ⇒ khung <w>x<h>`
2. **Chốt hai câu chưa biết**:
   - `previewSize HAL = 5120x960` ⇒ gợi ý ĐÚNG, cột 1 của §2 dùng được trên ROM này ⇒ nâng dòng đó từ [CHƯA BIẾT] lên [ĐO].
   - `previewSize HAL trả 0x0` ⇒ ROM không trả cỡ; khung vẫn đúng nhờ gợi ý, nhưng ghi lại để không hứa quá.
3. **Mắt owner** — với `khung=360x192` (NGANG): còn thấy viền đen không? hình còn bị kéo không? Nếu owner muốn khung
   **4:3 kiểu kinex** (thu dải y của crop) thì đó là quyết định owner, ghi vào backlog CAM-ROT-2, **không tự làm**.
4. Thử cả hai bên và cả `rot=0` (chip *Không xoay*) để thấy khung đổi chiều `192x360` ⇔ `360x192`:
   ```
   adb shell am broadcast -a com.byd.launcher.TEST -p <pkg> --es cmd prefs_set --es key camera_rot_left --es text 0
   ```
   (trả lời có `read_back` = giá trị thật sau lượt ghi.)

### D-B · Đo giật của hai đường kết xuất (việc B · CLOSE-14 L2)

1. **Baseline (`TextureView`, mặc định)** — xi-nhan bật, camera hiện ≥ 20 s:
   ```
   adb shell dumpsys gfxinfo <pkg> reset
   ... bật xi-nhan, giữ 20 s ...
   adb shell dumpsys gfxinfo <pkg> | grep -E "Total frames|Janky|percentile"
   ```
2. **Đổi sang `SurfaceView` mà KHÔNG build lại** (Cài đặt › Tiện nghi xe › *Kết xuất camera* → chip **SurfaceView**,
   hoặc bằng máy):
   ```
   adb shell am broadcast -a com.byd.launcher.TEST -p <pkg> --es cmd prefs_set --es key camera_render --es text SV
   ```
   Lượt xi-nhan **sau** đã theo (controller đọc pref mỗi lần dựng overlay) — không cần khởi động lại gì.
3. Lặp bước 1. So `Janky %` + `99th percentile` giữa hai đường, **cùng đoạn đường, cùng tốc độ**.
4. Cùng lúc đó ghi lại ba quan sát mắt cho đường `SV`:
   - video có **xoay** không? (`logcat` có dòng `setDisplayOrientation(<deg>) nhận=true/false` — *nhận* ≠ *có tác dụng*,
     mắt mới chốt được);
   - bốn góc còn **bo** không, hay thành vuông?
   - có thấy **đúng dải gương** không, hay thấy cả ảnh 4-in-1? (câu này chốt giả thuyết "ROM cắt layer con theo biên
     cửa sổ" ở §3.2).
5. Trả về mặc định sau khi đo:
   ```
   adb shell am broadcast -a com.byd.launcher.TEST -p <pkg> --es cmd prefs_set --es key camera_render --es text TV
   ```

### Tiêu chí kết luận (viết trước khi đo, để không đọc số theo ý mình)

- `SV` giảm `Janky %` **≥ 1/3** và không mất crop/xoay/bo góc ⇒ đáng bàn chuyện đổi mặc định (vẫn là **quyết định
  owner**, vì nó đổi đường đang chạy hiện trường).
- `SV` giảm không đáng kể **hoặc** mất crop/xoay ⇒ L2 **không** phải nguồn giật chính ⇒ sang **L3** (fps 15) theo
  playbook `camera-lag-analysis-2026-09-26.md §3`, đừng đổi gì thêm ở tầng vẽ.
- Hai lượt `gfxinfo` cùng cấu hình lệch nhau > 2× (như đã xảy ra ở 2.70) ⇒ phép đo chưa đủ tin: lặp 3 lượt mỗi đường,
  lấy trung vị, và ghi rõ đoạn đường/thời điểm.

---

## 6. Còn nợ (không tự quyết)

- **CAM-ROT-2 phần cuối**: khung NGANG `360x192` là *đúng tỉ lệ*; nếu owner thích khung 4:3 như kinex thì phải **thu
  dải y của crop** — đổi crop = đổi đường đã chạy ⇒ owner quyết.
- **Cỡ ảnh nguồn thật** của ROM DiLink3.0: [CHƯA BIẾT] tới khi có dòng `previewSize HAL = …` (D-A bước 1).
- **`setDisplayOrientation` có thi hành không**: [CHƯA BIẾT] (D-B bước 4).
- **ROM có cắt/bo layer `SurfaceView`** theo cửa sổ cha không: [ĐOÁN] (D-B bước 4).
- **Vùng cho phép** vẫn là ô vuông 50 % chiều cao. Khung ngang nay chỉ dùng 53 % bề cao vùng đó; nếu owner muốn
  overlay **to hơn**, chỗ đúng để đổi là trần vùng (một hằng), không phải tỉ lệ.
