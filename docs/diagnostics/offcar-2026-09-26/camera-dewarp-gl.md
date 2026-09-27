# Nắn fisheye — ĐƯỜNG KẾT XUẤT `GL` (R8-B), kiểm xong trên máy ảo (off-car 2026-09-27)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 (off-car, **chưa lên xe**) · **Mục đích**: ghi lại đường vẽ mới
> `camera_render = GL` — kiến trúc, luồng nào làm gì, bảy pref owner chỉnh trên xe, thứ tự chỉnh, **kết quả đo trên
> máy ảo bằng số**, và kế hoạch dự phòng nếu GPU đầu xe không đủ trần texture.
>
> Liên quan: `camera-dewarp-math.md` (toán + hợp đồng uniform + bộ mặc định [ĐOÁN]) ·
> `camera-view-electro-a.md` (R8-A: dải/bề rộng/hình khung) · `camera-aspect-and-render-path.md` (khung đúng tỉ lệ,
> hai đường `TV`/`SV`) · `../electro-camera-RE-2026-09-26.md` (§3 shader · §4 EGL của Electro · §4.3 chống giật ·
> §5 K8/K9 · §7 Q13/Q17) · `../camera-lag-analysis-2026-09-26.md` · spec `../../specs/kachi-274-ux-voice-camera.html` R8-B.
>
> Mã: `core/.../camera/{CameraGlUniforms,CameraDewarpPrefs,CameraDewarp,CameraDewarpShader,CameraDewarpTestPattern}.kt` ·
> `app/.../camera/{CameraGlSurface,CameraGlRenderer,CameraGlProgram,CameraSynthFeeder,CameraVideoLayer}.kt` ·
> `app/PrefsCameraDewarp.kt` · harness `scripts/emulator/camera-dewarp-e2e.sh` + `camera_dewarp_check.py`.

---

## 0. Năm dòng kết luận

1. Đường `GL` **chạy thật trên GPU** — đã vẽ 38–40 khung mỗi lượt trên máy ảo, `busySkip = 0`, không crash lúc dỡ.
2. **Phép nắn làm đúng việc**: chân trời cong `48,62 px` trước khi nắn → **`3,34 px`** sau khi nắn (trung bình
   `18,39 → 0,74 px`). Đo bằng numpy trên ảnh PNG chụp từ chính đường vẽ ấy (§5).
3. `GL_MAX_TEXTURE_SIZE` của **máy ảo** = **8192** (≥ 5120 cần cho ảnh 4-in-1). **RE §7 Q13 vẫn mở cho XE** — con số
   của đầu xe chỉ đọc được trên xe, và nay nó tự in ra ở dòng `overlay show` + lời đáp `camera_frame` (§6).
4. **Mặc định KHÔNG đổi một ly**: `camera_render` vẫn là `TV`. Không chạm Cài đặt ⇒ không một ngữ cảnh EGL nào được
   dựng, không một khoá nắn nào được đọc.
5. Hình **TRÒN** của owner (R8-A) **ăn trên đường GL** — outline oval nằm ở view CHA nên nó cắt cả `TextureView` của
   đường GL y như của đường `TV`. Đã chụp ảnh xác nhận.

---

## 1. Kiến trúc — ba `SurfaceTexture`, hai luồng, một ngữ cảnh

Đường `TV` (2.73) có **một** `SurfaceTexture`: của `TextureView`, và HAL đổ thẳng vào đó. Đường `GL` có **hai**, và
lẫn chúng là cách chắc chắn nhất để không có khung nào hiện ra:

```
  AVMCamera (HAL)                                  ← producer
        │ đổ khung vào
        ▼
  Surface(inputSurfaceTexture)          ← "VÀO": do CameraGlRenderer tạo, gắn GL_TEXTURE_EXTERNAL_OES
        │ onFrameAvailable (Handler của luồng vẽ)
        ▼
  ┌─ luồng "kachi-camgl" ────────────────────────────────────────────────┐
  │  updateTexImage → getTransformMatrix → vẽ 1 quad với CameraDewarpShader │
  │  (10 uniform) → eglSwapBuffers                                        │
  └───────────────────────────────────────────────────────────────────────┘
        │ EGL window surface dựng TRÊN
        ▼
  SurfaceTexture của TextureView        ← "RA": cửa sổ overlay 2.73, không đổi một dòng nào
        │
        ▼
  cây view HWUI → clipToOutline (bo góc / OVAL) → WindowManager
```

| Luồng | Làm gì |
|---|---|
| **main** | `TextureView` callback; `CameraGlRenderer.start/stop` gọi từ đây và **chặn** chờ luồng vẽ; `AVMCamera.open` vẫn nằm trên main **y trình tự 2.73** |
| **`kachi-camgl`** | **mọi** lời gọi EGL/GL: dựng ngữ cảnh, biên dịch shader, `updateTexImage`, vẽ, `eglSwapBuffers`, FBO chụp thô, dỡ |
| **`kachi-camsynth`** | chỉ khi `camera_synth` bật: vẽ ảnh tổng hợp vào `Surface` VÀO qua `lockCanvas` (~5 fps) |

`EGLContext` là **current theo luồng**: một lời gọi GL từ luồng khác **không ném gì cả**, nó chỉ không làm gì —
khung đứng im với `logcat` sạch. Đó là lý do mọi thứ đi qua đúng một `Handler`.

### 1.1 Ba quyết định kiến trúc, và bằng chứng của chúng

| Quyết định | Vì sao | Bằng chứng |
|---|---|---|
| `EGL14` tự lái, **không** `GLSurfaceView` | cửa ra **phải** là `TextureView` (bo góc + hình TRÒN + `getBitmap` dựa vào việc lớp video nằm TRONG cây view); và nhịp vẽ do **khung** quyết, không do `Choreographer` | [ĐO] RE §4.1: Electro cũng vậy — `GLSurfaceView`/`Choreographer`/`setEGLContextClientVersion` = **0 hit** trong `classes.dex` và cả 4 `.so` |
| Listener khung mới đi qua **`Handler` của luồng vẽ** | bản một-tham-số gọi callback trên *"an arbitrary thread"*, và `updateTexImage` từ luồng không giữ ngữ cảnh là no-op im lặng | [ĐO] AOSP `android-10.0.0_r47` `graphics/java/android/graphics/SurfaceTexture.java:186-189` |
| `glUniformMatrix4fv(..., transpose = false, ...)` | ma trận của `SurfaceTexture` **đã là column-major** | [ĐO] cùng tệp `:308-309`: *"stored in column-major order so that it may be passed directly to OpenGL ES via … glUniformMatrix4fv"* |
| `eglCreateWindowSurface` nhận `Surface(outputSurfaceTexture)` | `Surface` **nhả được tường minh**; `SurfaceTexture` kia thuộc `TextureView` và nền tảng huỷ nó ở `onSurfaceTextureDestroyed` | [ĐO] AOSP `opengl/java/android/opengl/EGL14.java:246-266` (nhận cả `Surface` lẫn `SurfaceTexture`) |

### 1.2 Hai cái bẫy đã đóng lại bằng bài canh

**(a) `rotatesByMatrix` ≠ `usesTextureView`.** Tới 2.73 một hàm trả lời cả hai câu. Đường `GL` **là** `TextureView`
(nên bo góc, hình TRÒN, `getBitmap` vẫn ăn) nhưng **không** xoay bằng ma trận — shader xoay. Dùng lẫn hai phép hỏi
đẩy đường GL vào nhánh `SurfaceView` của tầng vẽ và **không một khung nào hiện ra** trong khi compile vẫn xanh
(CLAUDE.md §8). Ba hàm tách hẳn ở `CameraSignalPolicy`: `rotatesByMatrix` · `rotatesInShader` · `usesTextureView`.

**(b) Trục `y` của ẢNH đi XUỐNG, trục `t` của TEXTURE đi LÊN.** Mọi `crop` của dự án đo `y` từ trên xuống; đầu vào
của `uTexMatrix` thì là *"traditional 2D OpenGL ES texture coordinate"*, tức `t = 0` là **đáy ảnh** ([ĐO]
`SurfaceTexture.java:38-42`). Phép đổi là `t = 1 − y` (`CameraGlUniforms.textureT`) và nó **không** phải `flipV`:

```
  flipV     ⇒ (y, h) = (y1,   y0 − y1)      ← soi gương quanh tâm CROP
  textureT  ⇒ (y, h) = (1−y0, y0 − y1)      ← đổi trục, quanh tâm ẢNH
```

Hai công thức chỉ trùng nhau khi `y0 + y1 = 1`. **Mọi crop hôm nay đều đối xứng như vậy**, nên dùng `flipV` sẽ đúng
hôm nay và sai đúng vào lần đầu ai đó thu dải `y` (backlog CAM-ROT-2 đã ghi việc ấy sẽ tới, kiểu kinex
`Y0/C0094o.java:330-336`). Bài `doi truc t KHAC flipV, chi trung o crop doi xung` là thứ sẽ đỏ ở đúng lượt ấy.

Quy ước đi kèm: quad của luồng vẽ ghép `NDC y = +1` với `v = 0`, tức **`v = 0` là ĐỈNH cửa sổ** — đúng quy ước màn
hình mà `CameraOverlayTransform` và shader đang dùng (dương = cùng chiều kim đồng hồ). Lấy `v = 0` ở đáy (quad mẫu
của Grafika) thì cùng một `uRotation` sẽ xoay **ngược chiều** và khung còn bị lật dọc — không có lời báo lỗi nào.

### 1.3 Chống giật — hai trong ba đòn của Electro (RE §4.3)

1. **Vẽ chỉ khi có khung mới** — không `Choreographer`, không vòng lặp. Không xi-nhan ⇒ 0 % GPU.
2. **Bỏ khung khi lượt vẽ trước chưa xong** (`pending` + `busySkip`) — Electro gọi đúng tên ấy (@0x4878a); kinex bỏ
   khung nếu chưa đủ 16 ms. Kachi tới 2.73 **không có** cơ chế nào tương đương (RE §5 K8); đây là chỗ đầu tiên nó có.
   Số khung bỏ in ra `logcat` mỗi 100 khung ở mức **DEBUG** và có trong lời đáp `camera_frame` (`gl_stats`).
3. **Luồng vẽ riêng, có tên** (`kachi-camgl`) ⇒ không bao giờ tranh luồng UI của launcher.

Đường khung hình **không cấp phát một byte nào**: ma trận và quad là trường dựng sẵn, vị trí uniform lấy lúc liên
kết program, không `String.format`, không shell. Bài `duong khung hinh GL khong cap phat khong log khong shell`
canh đúng điều đó (đã **mutation-check**: chèn một `FloatArray(16)` vào vòng vẽ ⇒ bài đỏ).

---

## 2. Chín pref — owner chỉnh gì trên xe

Tất cả **theo XE** (`clusternav_prefs`, cùng họ `camera_render`/`camera_span`), tất cả nằm trong danh sách trắng
`prefs_set`, tất cả có hàng trong *Cài đặt › Tiện nghi xe › Nắn méo (khi chọn GL)*.

| Khoá | Miền | Mặc định | Ý nghĩa | Dấu hiệu đang sai |
|---|---|---|---|---|
| `camera_dewarp_cx` | `-300..300` % | `0` | **lệch** tâm quang theo x (0 = đúng tâm đã suy) | nắn **lệch**: một bên thẳng, bên kia còng |
| `camera_dewarp_cy` | `-300..300` % | `0` | lệch tâm theo y | như trên, theo trục dọc |
| `camera_dewarp_k` | `25..400` % | `100` | `K` — hệ số f-theta, **% của giá trị suy ra** | **đường thẳng vẫn cong** dù nắn hết tay |
| `camera_dewarp_focal` | `25..400` % | `100` | `F` — tiêu cự khung RA, % của giá trị suy ra. Lớn = **hẹp hơn**, phóng to | vật ở giữa to/nhỏ bất thường |
| `camera_dewarp_scale` | `25..400` % | `100` | `SCALE`. ⚠ Lớn = với **sâu hơn** vào fisheye ⇒ thấy **RỘNG hơn** | **viền đen ở góc** = đã với ra ngoài vòng ảnh ⇒ hạ xuống |
| `camera_dewarp_amount` | `0..100` % | `100` | cường độ **TRỘN**. `0` = y ảnh thô 2.73 | nắn quá tay (mép bị kéo dãn) ⇒ hạ về `60–80` |
| `camera_dewarp_pan_x` | `-50..50` % | `0` | **dịch CỬA SỔ** theo x của ô **chưa xoay** (2.75 — xem §"Xe 27/09" B) | khung lệch chỗ muốn nhìn ⇒ đây mới là núm, KHÔNG phải `cx` |
| `camera_dewarp_pan_y` | `-50..50` % | `0` | dịch cửa sổ theo y của ô chưa xoay | như trên, theo trục dọc |
| `camera_gl_texmatrix` | `on`/`off` | `on` | áp `getTransformMatrix` hay truyền ma trận đơn vị | ảnh **lật dọc** hoặc lệch ⇒ thử tắt; chính điều đó là câu trả lời cho RE §7 **Q17** |

### 2.1 Vì sao là PHẦN TRĂM của bộ suy ra, không phải trị tuyệt đối của `F`/`K`

`F` và `K` đo bằng **nửa bề ngang ô** ⇒ chúng **tỉ lệ nghịch với bề ngang ô**: vệt hẹp `512×960` cho `K = 1,13084`,
trọn dải `1280×960` cho `K = 0,452335` — tỉ số **đúng 2,5** (`camera-dewarp-math.md` §3.1, và bài
`bo suy ra khop tai lieu va ti le nghich voi be ngang o` ghim cả ba con số). Owner chạm chip *Vùng gương* hay chip
*Dải* là đổi bề ngang ô ⇒ **một trị tuyệt đối vừa chỉnh đúng sẽ lập tức sai**, và owner sẽ chỉnh lại từ đầu mà không
hiểu vì sao. Phần trăm của phép suy thì **sống qua** mọi lượt đổi crop.

Tâm đi theo cùng lẽ nhưng là **độ LỆCH** (`0` = đúng tâm đã suy): tâm suy ra của crop gương nằm **ngoài** ô
(`centerX = 1,25` cho dải 1), nên một *"phần trăm của ô"* tuyệt đối sẽ có mặc định khác nhau cho từng dải — không có
con số nào viết được vào Cài đặt mà đúng cho cả bốn dải.

### 2.2 Bước nhảy của hàng −/+

`±5 %` cho bốn núm tỉ lệ (dưới mức đó một cú chạm không thấy gì ⇒ owner bấm mười lần rồi kết luận núm chết),
`±1 %` cho tâm (tâm lệch 5 % là **quá** một bước: nắn lệch tâm thì một bên thẳng bên kia còng, phải rà từng chút).

---

## 3. Thứ tự chỉnh trên xe — **tâm → K → tiêu cự → phóng → độ nắn**

Đây cũng đúng thứ tự các hàng trên màn (không phải thứ tự chữ cái), và bài canh ghim thứ tự ấy.

1. **Tâm** — chỉnh tới khi hai bên khung cong **giống nhau**. Tâm sai thì mọi bước sau vô nghĩa.
2. **`K`** — chỉnh tới khi cột đèn / vạch kẻ đường **THẲNG**. Đây là núm *"đúng/sai"*; ba núm sau là *"thẩm mỹ"*.
3. **Tiêu cự** — chọn độ rộng khung ra. Nhãn nói `lớn hơn = hẹp hơn, phóng to`.
4. **Phóng** — nếu thấy viền đen ở góc thì **hạ** (đã với ra ngoài vòng ảnh).
5. **Độ nắn** — hạ xuống nếu nắn quá tay. Chỉ `100 %` mới thật sự là mô hình equidistant; ở giữa là một phép TRỘN
   không thuộc mô hình quang học có tên nào (`camera-dewarp-math.md` §2).

⚠ Mọi pref chỉ ăn ở **lượt xi-nhan SAU** (bộ uniform bất biến trong một lượt overlay — đúng khuôn mọi pref camera).
Trên xe: bật/tắt xi-nhan một nhịp, hoặc `camera --es name none` rồi `camera --es name left` qua cầu kiểm thử.

---

## 4. `camera_frame` — và vì sao đường GL BẮT BUỘC phải có `--es name raw`

| Đường | `getBitmap` trả về | `content` trong lời đáp |
|---|---|---|
| `TV` | khung **GỐC** (ma trận `setTransform` không đi vào ảnh — bằng chứng AOSP ở KDoc `CameraOverlayView.captureFrame`) | `raw` |
| `SV` | **không có** `getBitmap` nào | — (`capture_failed`, kèm cách đổi về đường chụp được) |
| `GL` | khung **ĐÃ NẮN** (shader ghi thẳng vào cửa ra) | `dewarped` |
| `GL` + `--es name raw` | khung **THÔ** qua một lượt vẽ thứ hai vào FBO (`amount = 0`, nguyên khung, không xoay) rồi `glReadPixels` | `raw_fbo` |

Không có `content` thì một PNG đã nắn trông **y hệt** một PNG thô, và mọi phép đo bán kính/tâm vòng ảnh sẽ chạy trên
ảnh đã bị biến đổi bởi chính bộ tham số đang muốn chốt — một vòng tự xác nhận (CLAUDE.md §2). Vì vậy trên xe:
**đo tham số bằng `--es name raw`**, xem kết quả bằng lệnh thường.

`glReadPixels` đọc theo hệ toạ độ GL (gốc **góc dưới-trái**) còn `Bitmap` đánh hàng từ trên xuống ⇒ có một bước lật
hàng tường minh (`CameraGlProgram.argbFlipped`). Bỏ bước ấy thì PNG ngược mà **mắt không nhận ra** trên một ảnh
fisheye đối xứng — nó chỉ làm mọi phép đo sai dấu trục `y`.

Lời đáp còn mang `gl` (`GL_MAX_TEXTURE_SIZE` + `GL_RENDERER`), `gl_stats` (`frames=… busySkip=…`) và `synth`.

---

## 5. KẾT QUẢ ĐO TRÊN MÁY ẢO — 2026-09-27

Chạy `scripts/emulator/camera-dewarp-e2e.sh` trên `emulator-5554` (Android SDK arm64, ANGLE/SwiftShader), bản
`vehicleTest` của cây 2.74 đang làm.

### 5.1 GPU

```
GL_MAX_TEXTURE_SIZE   = 8192          ← ≥ 5120 cần cho ảnh 4-in-1 ✔
GL_MAX_VIEWPORT_DIMS  = 8192 x 8192
GL_RENDERER           = Google (Google Inc. (Google)) / Android Emulator OpenGL ES Translator
                        (ANGLE (Google, Vulkan 1.3.0 (SwiftShader Device (LLVM 10.0.0)), SwiftShader driver-5.0.0))
```

⚠ Đây là con số của **máy ảo**. RE §7 **Q13 vẫn mở cho XE** — không được đọc bảng này thành *"đầu xe cũng 8192"*.

### 5.2 Đường vẽ

| | |
|---|---|
| Khung đã vẽ mỗi lượt overlay | **38–40** (ảnh tổng hợp bơm ~5 fps trong ~8 s) |
| `busySkip` | **0** (GPU kịp mọi khung ở nhịp này) |
| `camera_frame` | `content=dewarped render=GL` — chụp được, 5120×960 |
| `camera_frame --es name raw` | `content=raw_fbo` — FBO + `glReadPixels` chạy |
| Hình **TRÒN** trên đường GL | **ăn** — chụp lại được, crop vuông quanh tâm dải |
| Dỡ overlay 4 lượt liên tiếp | không crash, không `SIGSEGV` |

### 5.3 Phép nắn có làm đúng việc không — **đo chân trời**

Ảnh thử vẽ một **chân trời lệch tâm quang** (`horizonY = 0.35`): một đường thẳng của thế giới **không** đi qua trục
quang, nên fisheye bẻ cong nó (đường **qua** tâm thì vẫn thẳng vì nó là một bán kính — nên nó không phải phép thử).
Tách pixel theo màu riêng của chân trời, lấy **tâm cột**, khớp `y = ax + b` bằng bình phương tối thiểu, đo độ lệch:

| | pixel đo được | lệch **max** | lệch **trung bình** |
|---|---|---|---|
| trước (`camera_dewarp_amount = 0`) | 15 806 | **48,62 px** | 18,39 px |
| sau (`camera_dewarp_amount = 100`) | 35 567 | **3,34 px** | **0,74 px** |

**Hai ngưỡng, cả hai ĐẠT**:
- **tỉ số**: `sau × 3 ≤ trước` ⇒ `10,02 ≤ 48,62` ✔
- **tuyệt đối**: `sau ≤ 2 % bề cao khung` ⇒ `3,34 ≤ 19,20` ✔

Ngưỡng đặt theo **tỉ số** chứ không theo một con số px tuyệt đối vì cửa sổ overlay trên máy ảo và trên xe khác cỡ —
một ngưỡng px cứng sẽ sai ở một trong hai. `3,34 px` còn lại là sai số lấy mẫu của sampler + độ dày nét 3 px của
chính ảnh thử, không phải sai số mô hình (mô hình chụp và mô hình nắn dùng **cùng** `K`, nên về lý thuyết là nghịch
đảo chính xác — xem bài `:core` `khu hoi dich to nguon roi ve dich`).

### 5.4 ⚠ Vòng này KHÔNG chứng minh cái gì

Ảnh vào là ảnh **tổng hợp**, sinh bằng **chính mô hình đang kiểm** (`CameraDewarpTestPattern` dùng
`CameraDewarp.idealEquidistantSource`). Nó chứng minh **cài đặt** đúng — GLSL khớp Kotlin, mười uniform vào đúng
chỗ, quy ước trục/xoay khớp đường đang chạy — và **không nói một chữ nào** về ống kính thật của BYD (equidistant hay
equisolid? bán kính vòng ảnh bao nhiêu? tâm ở đâu?). Tham số chốt bằng một khung `5120×960` chụp từ xe
(`camera-dewarp-math.md` §3.2 D1/D2). Xem **CAM-B1..B4** ở §7.

Lưu ý đọc ảnh: `camera_frame` mặc định chụp ở `5120×960` trong khi cửa sổ overlay thật chỉ rộng vài trăm px ⇒ ảnh bị
phóng ~26× và **răng cưa** là của lượt phóng ấy, không phải của lượt vẽ. Muốn soi nét thì xin đúng cỡ cửa sổ
(`camera_frame --es name 384x192`).

### 5.5 Một lỗ đã vá nhờ lượt chạy này

`prefs_set camera_signal_enabled 1` trả `read_back` **rỗng** — sáu khoá camera (`camera_signal_enabled`,
`camera_on_cluster`, `camera_cam_left/right`, `camera_pos_left/right`) ghi được nhưng không có nhánh đọc lại, tức
lời đáp nói *"đã ghi"* mà không nói ghi được gì. Vá trong cùng lượt.

---

## 6. `GL_MAX_TEXTURE_SIZE` — kế hoạch nếu đầu xe không đủ 5120 (RE §7 Q13)

**Đã làm** (có trong bản này):
- Đo **một lần** lúc dựng ngữ cảnh, in ra `logcat` (`GL ngữ cảnh: GL_MAX_TEXTURE_SIZE=…`), vào dòng `overlay show`
  và vào lời đáp `camera_frame` ⇒ buổi xe chỉ cần **một ảnh chụp màn**, không phải một lệnh riêng (CLAUDE.md §11).
- `setDefaultBufferSize` **kẹp theo trần đã đo** trước khi xin, kèm một dòng cảnh báo khi phải hạ. Không xin 5120 rồi
  chờ một khung đen mà [ĐO] AOSP nói rõ là *"might not be reported until updateTexImage()"*
  (`SurfaceTexture.java:242-245`).
- Lượt chụp thô cũng kẹp theo cùng con số.

**Chưa làm** (chỉ ghi kế hoạch, đúng luật *"chưa đo thì chưa được làm cổng"*):

| Nếu trần là | Hệ quả | Đường đi |
|---|---|---|
| `≥ 5120` | không có gì phải làm | — |
| `4096` | HAL vẫn đổ `5120×960`, nhưng buffer xin bị hạ ⇒ ảnh **co ngang** trước khi vào texture | phép nắn vẫn **đúng** (mọi toạ độ đều chuẩn hoá, co đều giao hoán với chúng) — chỉ mất độ nét. Xác nhận bằng `camera_frame --es name raw` rồi so bán kính vòng ảnh |
| `2048` | co mạnh, nét vạch kẻ đường có thể mất | thử `AVMCamera.setPreviewSize` (RE §5 **K5**: Electro có gọi, Kachi **chưa**) để xin HAL một cỡ nhỏ hơn ngay từ nguồn — rẻ hơn co sau |
| trần cho một dải nhưng không cho cả khung | — | thử `addPreviewSurface(Surface, VIEW_CHANNEL_n)` (`camera_hal_mode`, RE §6.3-C1): nếu HAL trả **một kênh** thay vì khung ghép thì cả tầng crop thành không cần |

Ba đường trên **không** được hiện thực trước khi có con số của xe: mỗi đường là một giả định về thứ HAL làm, và
CLAUDE.md §3 cấm ship một phép vá cho một bệnh chưa đo.

---

## 7. Runbook buổi xe — CAM-B1..B4

Chạy **sau** phần R8-A (dò dải/bề rộng). Cần: chế độ kiểm thử đang mở, `camera_signal_enabled = 1`.

### CAM-B1 · Đọc hai con số của GPU (RE §7 Q13 + Q17) — **làm TRƯỚC mọi thứ khác**

```
adb shell am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd prefs_set --es key camera_render --es text GL
# bật xi-nhan trái một nhịp (hoặc: --es cmd camera --es name left), rồi:
adb logcat -d -s KachiCamera | grep -E "GL ngữ cảnh|GL uTexMatrix|overlay show"
```
**Ghi lại**: `GL_MAX_TEXTURE_SIZE`, `GL_RENDERER`, và **16 số** của `uTexMatrix` khung đầu.
→ `< 5120` ⇒ đọc §6 trước khi đi tiếp. `uTexMatrix` khác ma trận đơn vị ⇒ Q17 **đã trả lời**, ghi vào RE.

### CAM-B2 · Chụp một khung THÔ để chốt vòng ảnh (D1/D2 của `camera-dewarp-math.md`)

```
… --es cmd camera_frame --es name raw
# lời đáp mang `path` → adb pull về máy
```
**Đo trên PNG**: bán kính vòng ảnh (viền tối bắt đầu ở đâu) · tâm vòng ảnh của **từng dải** · dải nào là gương
trái/phải (Q1/Q2) · biên sau khi nắn còn cong không (equidistant hay **equisolid**, Q/D2).
→ Đây là lượt đo **quan trọng nhất** của cả tính năng. Nếu chỉ làm được một việc trên xe thì làm việc này.

### CAM-B3 · Chỉnh bằng mắt, đúng thứ tự §3

*Cài đặt › Tiện nghi xe › Nắn méo (khi chọn GL)* — **tâm → K → tiêu cự → phóng → độ nắn**. Sau mỗi lần chỉnh: tắt/bật
xi-nhan một nhịp rồi nhìn. Hoặc bằng máy:
```
… --es cmd prefs_set --es key camera_dewarp_k --es text 120
… --es cmd camera --es name none ; … --es cmd camera --es name left
… --es cmd camera_frame --es name 384x192
```
**Ghi lại** sáu con số cuối cùng (lời đáp `prefs_set` có `read_back`).

### CAM-B4 · Đo giật, hai đường cạnh nhau (CLOSE-14 · L2 · RE §5 K8)

```
adb shell dumpsys gfxinfo com.byd.launcher reset
# chạy 2 phút có xi-nhan, đường TV
adb shell dumpsys gfxinfo com.byd.launcher framestats > tv.txt
… --es cmd prefs_set --es key camera_render --es text GL
# lặp lại 2 phút, đường GL
adb shell dumpsys gfxinfo com.byd.launcher framestats > gl.txt
adb logcat -d -s KachiCamera | grep "GL frames="
```
**So**: % khung giật + p99, và `busySkip` của đường GL. `busySkip` lớn ⇒ GPU đầu xe không kịp ở 15 fps ⇒ hạ tải
(`setPreviewSize`, §6) trước khi bàn tới việc bật mặc định.

> **Không** bật `camera_render = GL` làm mặc định cho tới khi CAM-B1 xanh, CAM-B2 có số, và CAM-B4 không tệ hơn `TV`.

---

## 8. Còn chưa biết (không được phát biểu như đã biết)

| # | Chưa biết | Chốt bằng |
|---|---|---|
| G1 | `GL_MAX_TEXTURE_SIZE` của GPU đầu xe | 🚗 CAM-B1 (máy ảo = 8192, **không suy ra được** cho xe) |
| G2 | `uTexMatrix` thật của camera id 1 (RE §7 Q17) | 🚗 CAM-B1 — dòng `GL uTexMatrix khung đầu` |
| G3 | Thêm một lượt GPU có làm giật hơn không | 🚗 CAM-B4 `framestats` trước/sau + `busySkip` |
| G4 | Bán kính + tâm vòng ảnh thật, ống kính equidistant hay equisolid | 🚗 CAM-B2 (kế thừa D1/D2 của `camera-dewarp-math.md`) |
| G5 | `lockCanvas` trên `Surface` của `SurfaceTexture` có chạy trên **ROM đầu xe** không | 🚗 chỉ ảnh hưởng `camera_synth` (lệnh đo), không ảnh hưởng đường camera thật |
| G6 | Sáu tham số nắn đúng cho xe này | 🚗 CAM-B3 — mắt owner; không có đường off-car nào |

---

## Xe 27/09 — lỗi xoay + pan

Buổi xe sáng 27/09 (Seal, Adreno 610, bản 2.74 (175), gương trái, `camera_render = GL`, `camera_span = STRIP`
dải 1). Mục này ghi **hai** kết quả: một nghi vấn bị **bác bằng số**, và một núm mới sinh ra từ yêu cầu của owner.

### A. *"Khung ↺90 cong hơn khung rot 0"* — **KHÔNG phải lỗi xoay** [ĐO]

Hiện tượng báo về: cùng bộ tham số (`F` 65 % ⇒ `0,2940` · `K` 100 % ⇒ `0,4523` · `S` 134 % · độ nắn 100 % · tâm
`(0,5, 0,5)`), khung ở `camera_rot_left = 0` trông thẳng, khung ở `L90` trông **cong** ở đoạn giữa. Giả thuyết ban
đầu: nhánh xoay hợp tỉ lệ khung/góc xoay **sai thứ tự**, hoặc lấy tỉ lệ **cửa sổ** thay vì tỉ lệ ô **nguồn**.

Phép đo chốt lại giả thuyết ấy — hai ảnh do chính `camera_frame` chụp trên xe, cách nhau ~1 phút, xe đứng yên
trong hầm:

| Ảnh | Cỡ | Phép so |
|---|---|---|
| `gl-left-rot0-f65.png` | `1280×960` | xoay **CCW 90°** rồi trừ ảnh kia |
| `gl-left-f65.png` (`L90`) | `960×1280` | — |

⇒ lệch **trung bình 3,11/255**, **p95 = 9/255** (nhiễu cảm biến + nén của hai lượt chụp khác nhau). So chiều
**CW** cho `37,9` ⇒ phép so không phải trùng khớp ngẫu nhiên.

**Kết luận [ĐO]: `L90` đã đúng bằng ảnh `rot 0` xoay lại.** Không có lỗi thứ tự nào để sửa. Cái cong owner thấy có
ở **cả hai** góc — nó thuộc về bộ tham số ống kính (G4: equidistant hay equisolid vẫn **[CHƯA BIẾT]**), không thuộc
về góc xoay. Ở `rot 0` mắt bám cây cột (đường **qua** trục quang ⇒ luôn thẳng dù méo), ở `L90` mắt bám vạch kẻ
đường (đường **lệch** trục quang ⇒ chỗ méo lộ ra) — cùng một ảnh, hai thứ được nhìn.

**Lý do cơ chế** (`CameraDewarp.kt:453-466` — `sample`, và `CameraDewarpShader.kt:101-113`): `rotateDstToLocal`
là một phép **affine** trong toạ độ ô đã chuẩn hoá (`90° ⇒ (a,b) = (v, 1−u)`), và nó chạy **trước** phép nắn, còn
`uAspect` là tỉ lệ ô **NGUỒN** nên **không** đổi theo góc xoay (`CameraGlUniforms.kt:23-25` bẫy 2). Hợp một phép
affine với một phép xuyên tâm thì **tính thẳng được bảo toàn**: một góc ±90 chỉ có thể cho ra đúng ảnh `rot 0` đã
xoay, không thể thêm một chút cong nào.

Khoá lại bằng hai bài `:core` (**đã mutation-check**: đảo thành *nắn rồi mới xoay* ⇒ **cả hai đỏ**):
`CameraDewarpRotatePanTest.xoay 90 chi la anh rot 0 da xoay` (2 ô không vuông × 3 góc × 81 điểm) và
`CameraGlUniformsTest.do nan 100 thi xoay chi la anh rot 0 da xoay, ca 16 to hop`.

### A2. *"Hình TRÒN ở rot 0 bị xoay"* (11:12) — **cũng KHÔNG phải lỗi hình TRÒN** [ĐO]

Báo về: `camera_shape = ROUND` + `GL` + `STRIP` + `F55/K100/S130`, cửa sổ tròn `495×495` hiện cây cột E4 **nằm
ngang**, trong khi cửa sổ chữ nhật cùng bộ số ở `rot 0` thì đứng.

Đo lại off-car trên **chính khung xe chụp sáng nay** (`camera_synth --es name file:…`, máy ảo, cùng `F/K/S`):

| Ca | Kết quả |
|---|---|
| `ROUND` + `rot 0` | **ĐỨNG** — cột E4 thẳng đứng, chữ đọc được (`round/v-ROUND-0.png`) |
| `ROUND` + `L90` | cột nằm ngang, chữ xoay — **giống hệt ảnh owner gửi** (`round/v-ROUND-L90.png`) |
| `ROUND L90` so với `ROUND rot 0` xoay CCW | lệch **trung bình 0,83/255** (chiều CW: 38,95) |

⇒ hình TRÒN ở `rot 0` **đúng**, và ở `±90` nó cũng chỉ là ảnh `rot 0` đã xoay — y như hình chữ nhật.
[SUY, độ tin cao] khung tròn trên xe lúc 11:12 đang chạy `camera_rot_left = L90` chứ không phải `0`. Chốt bằng một
dòng: `adb logcat -d -s KachiCamera | grep "hình=ROUND"` và đọc trường `rot=` của **chính** dòng ấy (nó có sẵn
trong `overlay show …`), hoặc `prefs_set --es key camera_rot_left` không kèm `--es text` để đọc lại.

Hình TRÒN **đã** nằm trong ma trận kiểm từ 2.74: bài `duong GL va duong TextureView cung mot hinh hoc khi khong nan`
đi hết `SPANS × SHAPES × 4 góc × 5 điểm` = 16 tổ hợp, và 2.75 thêm bài cùng ma trận ở **độ nắn 100 %**
(`do nan 100 thi xoay chi la anh rot 0 da xoay, ca 16 to hop`). Ô vuông của hình TRÒN có `aspect = 1` nên nó là ca
**dễ nhất**, không phải ca đặc biệt.

### B. Núm mới `camera_dewarp_pan_x` / `_pan_y` — **dịch cửa sổ**, không dời tâm quang

Cùng buổi, owner **duyệt** bộ `F 55 % (0,2488) · K 100 % (0,4523) · S 130 % · độ nắn 100 %` ở `rot 0`
(*"thẳng và tự nhiên"*) rồi xin đúng một việc: *"chỉ cần dịch 1 tý ra sau nữa thôi"*. Hai đường có sẵn đều **bị
bác tại chỗ**:

| Đường thử | Kết quả | Vì sao |
|---|---|---|
| `scale` 140–145 % | **bác** — *"nặng"* | phóng ra là đổi FOV, không phải dời khung; vật nhỏ đi, mép kéo dãn |
| `cx −10 %` | **bác** — hết thẳng | dời tâm quang = đổi **trục** của phép nắn ⇒ đồng-θ lệch khỏi vòng ảnh ⇒ một bên thẳng, bên kia còng |
| **dịch cửa sổ** (mới) | **nhận** | tâm quang đứng yên; `dst → p` chỉ thêm một **số hạng hằng** ⇒ vẫn affine ⇒ đường thẳng **vẫn thẳng** |

Cơ chế: `local = rot(dst) + pan` — một phép tịnh tiến đặt **giữa** xoay và nắn
(`CameraDewarp.kt:281-291` `panLocal`, GLSL `CameraDewarpShader.kt:113-116` `local = local + uPan;`), tức trong ô
**CHƯA XOAY**. Nhờ vậy `pan_x` mang **cùng một nghĩa vật lý ở mọi góc xoay**: gương trái dải 1 có đuôi xe ở mép
**trái** ô ⇒ `pan_x` **âm** là *"ra sau"*, dù đang ở `rot 0` (lùi sang trái khung) hay `L90` (lùi xuống đáy khung).
Owner không phải đổi núm khi đổi chip *Xoay*.

| Khoá | Miền | Mặc định | Bước | Ý nghĩa |
|---|---|---|---|---|
| `camera_dewarp_pan_x` | `-50..50` % | `0` | `5` | dịch cửa sổ theo **x của ô chưa xoay**, % bề ngang ô. Âm = về phía đuôi xe (gương trái dải 1) |
| `camera_dewarp_pan_y` | `-50..50` % | `0` | `5` | dịch theo **y của ô chưa xoay**, % bề cao ô |

Bước `5 %` (không phải `1 %` của tâm): cửa sổ gương trên xe [ĐO] `371×495` px ⇒ `1 %` ≈ 4 px, dưới ngưỡng phân
biệt khi ngồi trên xe; `5 %` ≈ 19–25 px. Trần `±50 %`: quá nửa ô thì tâm quang rơi hẳn ra ngoài cửa sổ.

Hai hàng −/+ nằm **cuối** mục *Nắn méo* trong Cài đặt (CLAUDE.md §6 — đường mới xuống cuối): chúng **trượt** khung
chứ không chữa bệnh cong, đặt lên trên sẽ dẫn owner kéo nhầm núm.

### C. Số của vòng máy ảo (`scripts/emulator/camera-dewarp-e2e.sh`, 2.75)

Vòng 2.74 chỉ đo ở `rot 0` — tức **không** đo ca mặc định của cả hai bên gương (trái ↺ −90 / phải ↻ +90). 2.75 đo
cả ba góc; `camera_dewarp_check.py --axis auto` tự chọn trục khớp (chân trời nằm ngang ở `rot 0`, gần **dọc** sau
khi xoay ±90) và lấy ngưỡng tuyệt đối theo bề **vuông góc** với nét, không luôn lấy bề cao.

| Ca | trục | lệch tối đa trước (px) | sau | trung bình sau | Kết luận |
|---|---|---|---|---|---|
| `rot 0` | x | 48,62 | **3,34** | 0,74 | ĐẠT |
| `L90` | y | 261,83 | **14,93** | 4,32 | ĐẠT |
| `R90` | y | 261,76 | **14,93** | 4,32 | ĐẠT |
| `L90` + `pan_x −20 %` | y | 249,78 | **14,89** | 4,37 | ĐẠT — **dịch không làm cong** |

(`L90`/`R90` có số tuyệt đối lớn hơn `rot 0` vì khung chụp ra FBO cỡ luồng `5120×960`: sau khi xoay, trục đo là
trục **ngang** dài 5120 px, nên cùng một độ cong tương đối cho nhiều px hơn. Ngưỡng là **tỉ số** nên phép so vẫn
đúng; cột "trung bình sau" mới là con số so được giữa các hàng.)

### D. Khung THẬT từ xe chạy qua đúng đường GL trên máy ảo

`camera_synth --es name file:<tên>` (2.75) bơm một PNG trong `getExternalFilesDir(null)` thay ảnh sinh
(`CameraSynthFeeder.fromFile`; tên tệp lọc bằng `TestBridgeSynth.safeName` — bỏ mọi thành phần thư mục, từ chối
`..`/tên rỗng/tên bắt đầu bằng dấu chấm). Nhờ đó **khung `5120×960` chụp từ xe sáng nay** chạy được qua đúng chuỗi
`Surface → SurfaceTexture(OES) → shader → TextureView` trên máy ảo, với đúng bộ số owner duyệt.

Đo trên ba khung ấy:
* `L90` so với `rot 0` xoay lại: bằng nhau (mục A).
* `L90` so với `L90 + pan_x −20 %`: tương quan chéo cực đại **0,99999994** tại đúng **192 px = 20,0 %** bề cao
  khung, không lệch một pixel — tức phép dịch là một **tịnh tiến thuần**, không kéo dãn, không cong. Đây là bằng
  chứng bằng số cho điều owner cần: dịch được khung mà **không** đánh đổi độ thẳng.

### E. Còn chưa biết sau buổi này

| # | Chưa biết | Chốt bằng |
|---|---|---|
| G4 (nhắc lại) | ống kính AVM là equidistant hay equisolid — phần cong **còn lại** ở cả hai góc xoay thuộc về đây, không thuộc về phép xoay | 🚗 CAM-B2; hoặc dò `K`/`F` bằng mắt tới khi vạch kẻ thẳng ở **cả** vùng biên |
| G7 | Trị `pan` owner thật sự muốn | 🚗 một buổi: `prefs_set camera_dewarp_pan_x` từng bước `−5` tới khi vừa mắt, rồi ghi `read_back` |

---

## Xe 27/09 — [P0] `BufferQueue has been abandoned` chạy mãi sau lượt đổi bên

Bản 2.74 (175). Triệu chứng owner đo được: launcher ăn **3,5 % CPU lúc rảnh**, nhật ký phình **130 KB/phút** (trần
2.73 là 20), `logcat` đầy một dòng lặp ở ~**16 dòng/giây** và **không bao giờ dứt** — **55 004 dòng** từ 09:58 tới
10:58, dù trên màn **không có** overlay camera nào:

```
E/BufferQueueProducer( 4893): [SurfaceTexture-0-4893-0] dequeueBuffer: BufferQueue has been abandoned
```

### Nguyên nhân gốc [ĐO] — đổi BÊN không dỡ phiên cũ

Mốc thời gian trong `kachi-logs/usage-1790477853304.log`:

| Giờ | Dòng | Việc |
|---|---|---|
| 09:58:15,418 | `xi-nhan LEFT → … kết xuất=TV` | mở phiên LEFT |
| 09:58:15,819 | `addPreviewSurface ok cameraId=1 mode=0` | HAL **nhận** `Surface` của `SurfaceTexture-0-4893-0` |
| 09:58:26,017 | `xi-nhan RIGHT → …` | rẽ sang phải — **không** một lời `stopPreview`/`close` nào cho LEFT |
| 09:58:26,**104** | dòng `abandoned` **đầu tiên** | 87 ms sau; từ đây lặp mãi |
| 09:58:36,549 | `rmPreviewSurface(mode=0) rc=false` · `close op=…` | lượt dỡ này thuộc phiên **RIGHT**, không cứu được LEFT |

Cả bản log chỉ có **một** hàng đệm kêu (`…-4893-0`, 55 004/55 004 dòng) — đúng hàng đệm của phiên duy nhất **không**
đi qua đường dỡ. Bốn phiên có dỡ (09:58:36 · 09:59:25 · 10:02:21 · 10:04:28) đều im.

Cơ chế: `CameraSignalController.tickMain` chỉ gọi `stop()` ở nhánh `Turn.NONE`. Khi `turn` đổi **từ một bên sang
bên kia**, nó đi thẳng xuống `overlay.show(...)` — mà `CameraOverlayView.show()` mở đầu bằng `hide()` ⇒ lớp video cũ
bị `release()` và `TextureView` bị gỡ ⇒ nền tảng **huỷ `SurfaceTexture`** (hàng đệm bị bỏ). Nhưng `avm.open(...)`
thì **ghi đè** tham chiếu `AVMCamera` cũ mà không đóng nó, nên HAL vẫn giữ đúng `Surface` ấy và tiếp tục
`dequeueBuffer` — vào một hàng đệm không còn ai tiêu thụ. Không có gì dừng nó cho tới khi tiến trình chết.

### Bản vá 2.75

1. `tickMain`: đổi bên ⇒ `closeSession(keepPano = true)` **trước** khi dựng phiên mới.
   * **Không** dùng `stop()`: nó `hold.reset()` + huỷ hẹn giờ, mà `turn` vừa tính ra **từ** `hold` ⇒ xoá nền HOLD
     ngay sau đó sẽ làm lượt sau đọc pha TẮT của đèn nháy thành NONE ⇒ overlay chớp giữa chuyến.
   * `keepPano = true` ⇒ **không** tắt thiết bị panorama giữa hai lượt rẽ: một vòng `WORK_OFF → WORK_ON` là hành vi
     chưa ai đo trên xe (CLAUDE.md §6), và con bọ nằm ở `AVMCamera` chứ không ở thiết bị panorama.
2. Chuỗi dỡ tách thành `closeSession()` — **một** bản duy nhất, dùng chung cho `stop()` và lượt đổi bên. Thứ tự
   **HAL trước, cửa sổ sau** (`avm.close()` → `hal.close()` → `overlay.hide()`): đảo lại là dựng lại đúng con bọ.
3. `AvmCamera.close()`: `rmPreviewSurface` chuyển lên **giữa** `stopPreview` và `close`. [ĐO] cả bốn lượt trên xe
   ghi `rc=false` vì nó đang bị gọi **sau** `close()` — HAL từ chối, tức móc đo ấy không đo được gì. Vị trí mới cũng
   là thứ tự Electro dùng (`stop → rm → release`, RE §3.1). Cổng `rmOnClose` giữ nguyên ⇒ xe lúc chạy bình thường
   vẫn đúng hai lời gọi của 2.73.
4. `PanoramaHal.close()` ghi mã đã **giải**: `close op=-2147482645 (0x800003EB err-bit31 code=1003)`. So với
   `open` trả `op=0` ⇒ [SUY] lượt tắt bị HAL báo lỗi; **[CHƯA BIẾT]** bảng mã `1003` (**G8**).

Khoá bằng `CameraGlWiringContractTest.do phien cu TRUOC khi mo phien moi` (ghim cả bốn: có gọi `closeSession` khi
đổi bên · `keepPano` · thứ tự `avm → hal → overlay.hide` · `rmPreviewSurface` nằm trước `close`).

### ⚠ Máy ảo KHÔNG tái lập được con bọ này — cần một lượt kiểm trên XE

[ĐO] chạy LEFT → RIGHT → LEFT → NONE trên máy ảo, cả `TV` lẫn `GL`, **trước và sau** bản vá: **0 dòng** `abandoned`
ở cả hai. Lý do: máy ảo không có `android.hardware.AVMCamera`; producer là `CameraSynthFeeder` **trong cùng tiến
trình**, và nó bị `CameraVideoLayer.release()` dừng ngay ở lượt `hide()`. Thứ rò trên xe là producer **native của
HAL**, không có bản thế nào trên máy ảo. Vì vậy bản vá được chứng minh bằng (a) mốc thời gian ở trên, (b) bài canh
thứ tự, và (c) **phải** có một lượt kiểm trên xe:

### CAM-B5 · Đổi bên không để lại hàng đệm mồ côi (🚗 bắt buộc cho 2.75)

```
adb logcat -c
# bật xi-nhan TRÁI ~5 s → chuyển thẳng sang xi-nhan PHẢI ~5 s → tắt hẳn, chờ 30 s
adb logcat -d -s BufferQueueProducer:E | grep -c abandoned     # PHẢI = 0
adb logcat -d -s KachiCamera | grep -E "xi-nhan|close op|rmPreviewSurface"
```
**Đạt** khi: `0` dòng `abandoned` sau 30 s, và giữa hai lượt `xi-nhan` có một dòng `rmPreviewSurface(mode=…)` với
`rc=true` (chế độ kiểm thử mở) hoặc ít nhất một lượt dỡ `AVMCamera`. **Không đạt** ⇒ chưa được coi 2.75 là xong.

Kèm theo, đo lại hai con số mà con bọ này làm hỏng: `top -n 1 | grep launcher` lúc rảnh (**< 1 %**, 2.74 đo 3,5 %)
và tốc độ phình nhật ký KachiPerf (**< 20 KB/phút**, 2.74 đo 130).

### G8 (mới) — còn chưa biết

| # | Chưa biết | Chốt bằng |
|---|---|---|
| G8 | `setPanoOperation(WORK_OFF)` trả `0x800003EB` (mã 1003) nghĩa gì; lượt tắt có thật sự thất bại không | 🚗 so mã của `open`/`close` nhiều lượt + `getPanoWorkState` ngay sau `close` |

---

## Xe 27/09 — nguồn **MỘT KÊNH** camera (R9) + trần nhịp vẽ

### F. `addPreviewSurface(surface, 1..4)` cho TRỌN khung một camera — RE §7 **Q3/D7 đã trả lời** [ĐO 11:16]

Kênh HAL `1..4` trả **`rc = true`**, và buffer (vẫn `5120×960`) chứa **trọn khung fisheye của MỘT camera**, bị
**kéo ngang** cho đầy — tức *anamorphic* đúng `STRIPS = 4` lần (ảnh thật `1280×960`). Bản đồ kênh trên Seal này:

| Kênh | Hướng | Mức |
|---|---|---|
| `1` | trước | [SUY] (xe đỗ đầu-đuôi phía trước) |
| `2` | **gương TRÁI** (phía cột E4) | [ĐO] owner xác nhận |
| `3` | **gương PHẢI** (phía E3) | [ĐO] owner xác nhận |
| `4` | sau | [SUY] (đầu làn trống) |

Vì sao đây là nguồn **tốt hơn** cho gương: không phải cắt dải, được **trọn vòng ảnh** của ống kính, và ô vẫn là
`1280×960` ⇒ `K`/`F` suy ra **trùng** ca `SPAN = STRIP`, tức bộ `F 55 % · K 100 % · S 130 %` owner đã duyệt **không
phải chỉnh lại** khi đổi chip.

**Pref mới `camera_source`** = `PANO` (mặc định, đường 2.36…2.74) | `CHANNEL`. Kênh dùng cho từng bên lấy từ
`CamView.channel` (per-side theo hồ sơ xe — Seal trái `2`, phải `3`), owner đè được bằng `camera_hal_mode`
(`CameraSignalPolicy.channelFor`). Chip *Nguồn* đứng **trước** *Vùng gương*/*Dải* trong Cài đặt (chọn một kênh thì
hai hàng kia hết nghĩa).

**Cái bẫy duy nhất, và là toàn bộ nội dung của bản vá**: mọi tầng hình học phải đo trên bề ngang **NỘI DUNG**
(`CameraPanoCrop.contentWidth` = `streamW / STRIPS`), không phải bề ngang buffer. Lấy nhầm `5120` ⇒ `aspect = 5,33`
(đồng-θ thành ellipse dẹt), `K` suy ra nhỏ đi **đúng 4 lần**, cửa sổ sai tỉ lệ — **ba** thứ sai cùng lúc mà ảnh vẫn
"ra hình". Bài `lay nham be ngang buffer thi aspect va K deu sai` ghim đúng con số ấy.

Không cần **một dòng GLSL nào**: shader làm việc trong toạ độ ô **chuẩn hoá**, nên phép kéo ngang tan hết vào
`uSrcRect` (trọn buffer) + `uAspect` (tỉ lệ nội dung). Đường `TextureView` cũng tự đúng: cửa sổ lấy tỉ lệ nội dung
`4:3` ⇒ `TextureView` căng buffer `5120×960` vào khung `4:3` chính là phép **nén ngang ×4** cần có.

[ĐO máy ảo, khung THẬT từ xe kéo ngang ×4, `F55/K100/S130`, `camera_source = CHANNEL`]:
`crop=-` (trọn buffer) · `halMode=2` (kênh của view) · `nguồn=CHANNEL`; ảnh `rot 0` **đứng, tỉ lệ 4:3**, ảnh `L90`
lệch **0,88/255** so với ảnh `rot 0` xoay CCW ⇒ hình học đúng ở cả hai góc. PNG: `chan/v-chan-0.png`,
`chan/v-chan-L90.png`.

### G. Trần nhịp vẽ của đường GL — CAM-B4 [ĐO 11:25]

Hai phút xi-nhan **thật**, cửa sổ TRÒN `495×495`:

| Đường | Khung | fps | Giật | p50/p90/p95/p99 (ms) | CPU |
|---|---|---|---|---|---|
| `GL` (2.74) | 4052 | ≈34 | **11,15 %** | 5 / 18 / 31 / 61 | **10,7 %** |
| `TV` | 982 | ≈8 | 1,0 % | 5 / 8 / 11 / 16 | ≈0 % |

Nguyên nhân: đường GL vẽ **mỗi khi có khung** (`onFrameAvailable`), mà HAL đẩy ~34 fps **dù** Kachi xin
`setCameraFps(15)`. 34 fps là công vô ích trả bằng đúng thứ owner cảm thấy.

Vá: trần nhịp **ở tầng vẽ** (`CameraSignalPolicy.RENDER_FPS_CAP = 15`, `renderMinGapMs()` = **50 ms**).
`updateTexImage()` **vẫn luôn chạy** ở lượt bị bỏ — không nhận khung thì `BufferQueue` đầy và producer của HAL có
thể nghẽn, tức đổi một vấn đề giật lấy một vấn đề đứng hình. Chỉ bỏ phần **đắt**: `paint` (fragment shader có
`atan` từng pixel) + `eglSwapBuffers` (chờ vsync).

Vì sao **3/4** chu kỳ chứ không trọn: khung HAL tới mỗi ~29,4 ms; ngưỡng 66 ms rơi **giữa** hai khung ⇒ lượt vẽ
trượt sang khung thứ ba ⇒ chỉ còn ~11 fps. Ngưỡng 50 ms rơi trước khung thứ hai ⇒ **nhịp dự kiến ≈ 17 fps**
(vẽ một khung, bỏ một khung), tức **giảm ~½ tải GPU** so với 34 fps. `stats()` nay in `fpsSkip=` và `fpsCap=` để
chốt nhịp thật trên xe.

> 🚗 **CAM-B6**: chạy lại CAM-B4 sau bản vá và so bốn con số. Kỳ vọng: `frames` ≈ nửa, `janky` xuống rõ,
> `fpsSkip` ≈ `frames`. Nếu `janky` **không** giảm thì thủ phạm không phải nhịp vẽ mà là cỡ vẽ ⇒ bước tiếp là
> `setPreviewSize` (RE §5 K5), không phải hạ trần thêm.
