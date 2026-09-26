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

## 2. Bảy pref — owner chỉnh gì trên xe

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
