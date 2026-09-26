# RE — Electro (`br.com.rory.electro` 1.13.0): camera 4-in-1, nắn fisheye bằng shader, đường vẽ không lag

> **Trạng thái**: Current · **Cập nhật**: 2026-09-26 · **Mục đích**: rút ra từ APK Electro những gì Kachi dùng được cho camera xi-nhan 2.74 — Electro mở AVMCamera thế nào, chia dải pano 4-in-1 ra sao, **toàn văn shader nắn fisheye** + toán đằng sau, cách nó vẽ không lag; rồi đối chiếu thẳng với `AvmCamera.kt`/`CameraOverlayView.kt`/`CamView` hiện tại và chốt 3 phương án. Liên quan: `camera-lag-analysis-2026-09-26.md` (nguồn giật), `camera-panorama-RE-2026-09-22.md` (Historical — hướng `BYDAutoPanoramaDevice`), spec `specs/camera-turn-signal-hal-socket.html`.

**Nguồn**: jadx `../jadx-electro/sources` (2412 tệp .java) · native `../jadx-electro/native/lib/arm64-v8a/{libelectropkg.so, libelectrolib.so, libnative-lib.so}` · APK `../apk-ref/Electro-Car-App/Electro-Car-App.apk` · SDK BYD thật `../firmware/fw-2602-diff/jadx-l3-new/sources/com/byd/dilink/hardware/camera/` · kinex `../jadx-kinex/sources` · carlog xe của chính Kachi `docs/diagnostics/carlog-kachi-20260914-2044/`.
**Quy ước dẫn nguồn**: đường dẫn kiểu `p055n/C0812h.java:31` là tương đối trong `../jadx-electro/sources/br/com/rory/electro/`, và các gói lồng viết tắt (`p055n/` = `p049e/p050k/p055n/`, `p056o/` = `p049e/p050k/p056o/`, `p054m/` = `p049e/p050k/p054m/`, `p051l/` = `p049e/p050k/p051l/`). `@0x…` là offset byte trong `libelectropkg.so` (`.rodata` có `Addr == Off` nên offset tệp = địa chỉ ảo); vài offset trỏ **vào giữa** một literal nhiều dòng — đó là cố ý, để chỉ đúng dòng.
**Đã kiểm lại bằng máy**: 161 offset trong tài liệu này đều phân giải đúng nội dung được dẫn; các khẳng định phủ định (`panorama`, `GLSurfaceView`, `TextureView`, `Choreographer`, `attachToGLContext`, `GL_MAX_TEXTURE_SIZE`, `glGetIntegerv`, `fisheye_`, `flip_`, `extra_cameras`, `splitNV21To4`) đều = **0 hit** trong `libelectropkg.so`, và `GLSurfaceView`/`TextureView`/`Choreographer`/`AVMCamera`/`selectedCameraId`/`splitNV21To4` = **0 hit** trong `classes.dex` (đối chứng dương `applyCLAHE`/`convertNV21ToI420`/`classesInit0` đều có).
**Không có xe, không có máy ảo trong phiên này** — mọi dòng 🚗 là việc phải đo, chưa đo.

---

## 0. Kết luận 5 dòng

1. **Hướng của Kachi đúng.** Electro *không* dùng `BYDAutoPanoramaDevice`, không LVDS, không service riêng: nó reflection vào `android.hardware.AVMCamera` y như `AvmCamera.kt`, đổ frame vào `SurfaceTexture` của chính nó → texture OES → GL [ĐO, §1]. Trong danh sách lớp `BYDAuto*` của nó **không có** `panorama` (`libelectropkg.so` chỉ có `bodywork` @0x4dec1, `gearbox` @0x4dfb1, `adas` @0x5eca1…).
2. **Pano = 4 dải DỌC bằng nhau, mỗi dải đúng 25% bề ngang**, và Electro chọn dải bằng **một uniform trong shader** (`vTexCoord.x * 0.25 + uStripOffset`, @0x493b5) — không cắt CPU, không chép [ĐO, §2].
3. **Electro là app duy nhất trong ba (Electro / kinex / Kachi) có nắn méo fisheye thật**: chiếu equidistant→rectilinear `r_src = K·atan(r_dst / F)`, 2 tham số + cường độ, trộn `mix()` — kinex chỉ crop + xoay bằng blit OES (`p005b1/S.java` chỉ có uniform `uTexMatrix`), Kachi không có GL nào [ĐO, §3, §5].
4. **id 1 = pano 4-in-1 là [ĐO], và KDoc của Kachi đang ghi ngược**: carlog xe của chính Kachi có `vehicle.config.cam_sort:rear:0;pano_h:1;` (`carlog-kachi-20260914-2044/10-logcat-baseline.txt:1776`) — xe này lộ **2 luồng AVM (id)** từ **4 camera vật lý** (trước · sau · 2 dưới gương — đúng cấu hình Seal Performance VN): `rear`→id 0 (luồng cam lùi riêng), `pano_h`→id 1 (ảnh ghép 4 mắt fisheye 5120×960); Electro cũng in `selectedCameraId=1 renders-full-frame` (@0x47d24). Đoạn KDoc `CameraSignalPolicy.kt:187` ghi "id 0 (fisheye 4-in-1)… id 1 (cam trước)" là **sai**, phải sửa (enum ở `:200-203` thì đúng).
5. **2.74 có ba đường**, xếp theo công: **A** nới/đổi crop chữ nhật (≈ nửa ngày, rủi ro thấp) · **B** shader OES nắn fisheye, giữ `TextureView` để không mất bo góc + R7 xoay, copy **toán** của Electro (≈ 2–3 ngày, rủi ro trung bình) · **C** dùng nguyên vòng — **C1** để HAL tự làm (`VIEW_CHANNEL_1..4`, `setPreviewSurface(Surface,int,int,int,int,int)` có thật trong SDK BYD: rẻ nhất nếu chạy, **phải đo trên xe trước**) hoặc **C2** bê nguyên kiến trúc hub+FBO của Electro (1–2 tuần, chỉ khi cần ≥ 2 đích) [§6].

---

## 0b. Đọc bằng chứng của tài liệu này thế nào (bắt buộc đọc trước §1)

Ba cái bẫy, cả ba đều đã làm sai một lần trong phiên RE này rồi phải gỡ:

- **Java của Electro bị đóng gói (VMP), nhưng chỉ package của nó.** 512 tệp dưới `br/com/rory/electro/` có **0 thân method** ngoài constructor; mỗi lớp mở đầu bằng `NativeLoader.classesInit0(<id>)` (1124 chỗ gọi, 1049 id khác nhau), thân nằm trong payload `libelectropkg.so` và được **thông dịch** bởi `vmInterpret` — hàm này do **`libelectrolib.so`** định nghĩa (@0x175cc, 33172 byte), `libelectropkg.so` chỉ export đúng `JNI_OnLoad` và import `vmInterpret`/`getCacheClass`/`cacheInitial`/`gVm` [ĐO]. Thư viện đi kèm thì đọc được bình thường (1884 tệp, 21338 thân method — cả 170 tệp `org/webrtc`), và **884 constructor còn nguyên** (551 có nội dung), trong đó có cả phần EGL và hình học quad. Vì vậy: **chữ ký, kiểu, quan hệ lớp = [ĐO]; luồng gọi và thứ tự = [CHƯA BIẾT]** trừ khi đọc được ở constructor hoặc ở thư viện không bị gói.
- **Bảng chuỗi trong `.rodata` đã được SẮP XẾP theo thứ tự chữ.** Đó là constant pool DEX (định dạng DEX *bắt buộc* `string_ids` sắp xếp + loại trùng) do packer mang nguyên vào, **không** phải do linker gộp chuỗi: 4662 literal nằm sau 0x68bf1 trong *cùng* section vẫn hoàn toàn không sắp xếp (2266 chỗ giảm dần) [ĐO]. Hệ quả cứng: **hai chuỗi cạnh nhau không có nghĩa là cùng một method**; một tên hàm có trong pool chỉ chứng minh app *tham chiếu* tới nó, **không** chứng minh lớp nào gọi. Đừng dựng luồng từ khoảng cách offset. Tổng pool = **12785 chuỗi** (base 0x472b8, bảng offset u32 @0x9b694 hết ở 0xA7E58; vùng 0xA7E58–0xA9B68 là **bảng khác**, 1860 mục kiểu string-index).
- **Nhiều export JNI trong `libnative-lib.so` là code chết.** `splitNV21To4`, `combineCamerasTo2x2GridNV21ToI420/…ToNV12`, `cropNV21To*`, `processAndCombineFrames`… có thân ARM64 đọc được, nhưng `common/YuvUtils.java:9-15` chỉ khai báo **4** native (`applyCLAHE`, `convertNV12ToI420`, `convertNV21ToI420`, `normalizeYChannel`), `classes.dex` và pool của `libelectropkg.so` **0 hit** cho các tên kia, `libnative-lib.so` không có `JNI_OnLoad`/`RegisterNatives`, không caller nội bộ, timestamp 1981 (lib prebuilt mượn lại) [ĐO]. ⇒ **Không được lấy hoán vị ô 2×2 trong đó làm sự thật về xe.**

---

## 1. Electro mở camera thế nào

### 1.1 Tầng reflection (giống Kachi, nhưng tổng quát hơn)

Hai lớp, cả hai bị VMP:

| Lớp | Vai trò | Bằng chứng |
|---|---|---|
| `p049e/p050k/C0759g.java` | *ReflectiveCameraManager* — cache 4 `Class<?>` + 8 `Method` tĩnh; `enum a { NORMAL, AVM }` (`:47-49`); `m2484a(String)` = **camera code → id** (`:66`); `m2486c(int)` = isValid (`:72`); `m2487d(int, a)` = mở theo (id, loại) (`:75`) | [ĐO] file + log tag `ReflectiveCameraManager.init` @0x5c465 |
| `p049e/p050k/C0758f.java` | *RefCameraDevice* — **25** `private Method` (`:13-94`) + 1 `Class<?>` (`f1511B`) + 1 `Object` là thiết bị thật (`f1512a`) + 1 cờ loại (`f1513b`); tất cả đi qua một invoker chung `m2475e(Method, Object, int... iArr)` (`:111`) | [ĐO] file + `RefCameraDevice.init` @0x5c450 + `Failed to find methods for camera device - ` @0x4c241 |

Nó **không** biên dịch stub SDK cho camera: `AVMCamera` xuất hiện **0 lần** trong cả 2412 tệp .java, chỉ có trong pool của `.so` (`android.hardware.AVMCamera` @0x5ea3d, `$IEventCallback` @0x5ea58, `$IPreviewCallback` @0x5ea82, `BmmCameraInfo` @0x5eace, `JNIBMMCamera` @0x5eb0f, `NormalCamera` @0x5eb48) [ĐO]. Không `dlopen`, không `.so` camera nào. (Ngược lại DEX *có* stub cho các `BYDAuto*Device` khác dưới `sources/android/hardware/` — tức "không stub" chỉ đúng cho họ camera.)

- Nạp lớp: `addDexPath` @0x5e817 + các mảnh `/sy` @0x47302 + `stem/fr` @0x67696 + `am` @0x5e9c3 + `ework/bmmca` @0x61677 + `mera.ja` @0x648c2 ⇒ **[SUY]** `/system/framework/bmmcamera.jar`, đúng hằng `DEX` của Kachi (`AvmCamera.kt:133`). Thứ tự ghép là suy luận; APK **không** có `<uses-library>` nào [ĐO].
- Callback: pool có `java/lang/reflect/Proxy` @0x9208b, `newProxyInstance` @0x64bf5 và descriptor `(Ljava/lang/ClassLoader;[Ljava/lang/Class;Ljava/lang/reflect/InvocationHandler;)Ljava/lang/Object;` @0x80c4e ⇒ **[SUY]** `IPreviewCallback`/`IEventCallback` được hiện thực bằng `Proxy` (không thể implement lúc biên dịch vì interface chỉ có trong ROM). Chỗ gọi nằm trong bytecode VM ⇒ không đọc được.
- Miễn hidden-API: `exemptHiddenApis` @0x616b8, `getSetHiddenApiExemptionsMethodName` @0x62eba, `getVmRuntimeClassName` @0x63279 [ĐO] — nhưng nhóm chuỗi này thuộc **daemon chiếu cụm** (17 getter `getDaemon*Log`, `getSurfaceControlClassName` @0x6304d, `getSetDisplaySurfaceMethodName` @0x62e9b), **không phải camera** [ĐO]. Electro `targetSdk 25` nên hidden-API không bao giờ chặn nó; Kachi `targetSdk 37` (`app/build.gradle.kts:38,59,60`: compileSdk 37 / minSdk 29 / targetSdk 37) ⇒ khác biệt này **không** suy ra được từ Electro, xem §7.

### 1.2 ⚠ Chữ ký `addPreviewSurface` — chỗ dễ sai nhất, đã sửa

Chuỗi descriptor `(Landroid/view/Surface;[I)Z` @0x6ab4c **là wrapper của chính Electro**, không phải chữ ký BYD: nó là `C0758f.m2476a(Surface, int... iArr)` (`:114`) và `m2479g(Surface, int...)` (`:123`) — Java `int...` erase thành `[I`; cùng họ với invoker `(Ljava/lang/reflect/Method;Ljava/lang/Object;[I)Ljava/lang/Object;` @0x72bf2 [ĐO]. **`int[]` KHÔNG phải danh sách camera id, và một Surface KHÔNG phục vụ nhiều camera.**

Sự thật lấy từ SDK BYD trong firmware [ĐO] — `IDiLinkAVMCamera.java`:

| Hàm BYD | Dòng | Ghi chú cho Kachi |
|---|---|---|
| `boolean addPreviewSurface(Surface, int)` | `:12` | **1 int** = mode/kênh xem, KHÔNG phải id. `(Landroid/view/Surface;I)Z` **không tồn tại** trong `.so` của Electro [ĐO] |
| `boolean addTexture(SurfaceTexture, int)` | `:14` | 🎯 đưa thẳng `SurfaceTexture` — đúng thứ đường GL cần, Kachi chưa dùng |
| `boolean rmPreviewSurface(Surface, int)` / `rmTexture(SurfaceTexture,int)` | `:38` / `:40` | Kachi **không** gọi khi đóng (§5) |
| `boolean setPreviewSurface(Surface, int)` | `:62` | |
| `boolean setPreviewSurface(Surface, int,int,int,int,int)` | `:64` | 🎯 5 int — ứng viên **crop/đích ở tầng HAL**, xem §6-C |
| `boolean setTexture(SurfaceTexture,int)` / `(…,int,int,int,int,int)` | `:66` / `:68` | như trên, bản texture |
| `boolean setPreviewSize(int,int)` · `getPreviewWidth()` · `getPreviewHeight()` | `:60`,`:32`,`:30` | |
| `boolean setAlgMode(int)` | `:42` | [CHƯA BIẾT] nghĩa — ứng viên "chế độ thuật toán" của AVM |
| `boolean setCameraFps(int)` · `setDisplayOrientation(Surface,int)` | `:44`,`:46` | Kachi đã dùng cả hai |
| `enablePreviewCallback(int)` / `(int,int,int,int,int)` · `setPreviewCallback(IDiLinkAVMPreviewCallback)` | `:22`,`:24`,`:58` | đường NV21 CPU |
| `isOpen()` · `isPreview()` · `getCameraId()` · `startPreview()` · `stopPreview()` · `close()` | `:34`,`:36`,`:26`,`:70`,`:72`,`:16` | `getCameraId` chỉ có getter ⇒ **id gắn lúc mở**, không đổi được sau |
| `IDiLinkNormalCamera.addPreviewSurface(Surface)` | `:12` | camera thường: **0 int** |
| Manager: `open(int)`, `open(int,int)`, `open(int, IDiLinkWatermarkRender)`, `open(int, IDiLinkWatermarkRender, int)` | `IDiLinkAVMCameraManager.java:10-16` | có chỗ nhận watermark renderer |

⇒ Cái `int...` của Electro tồn tại để **một stub Java phủ mọi arity**: 0 int (Normal), 1 int (AVM add/rm/set), 5 int (AVM setPreviewSurface/setTexture) — tức một lớp đệm tương thích giữa các đời DiLink, **không** phải cơ chế nhiều camera [SUY]. Kachi hiện đã gọi đúng `addPreviewSurface(Surface, int mode)` và dò mode 0..3 như kinex (`AvmCamera.kt:60-70`) [ĐO] — **không được** port `(Surface, int[])`.

**`int` mode ấy là gì**: `DiLinkCameraConstants.java:47-58` [ĐO] có `VIEW_DEFAULT = 0`, `VIEW_CHANNEL_1..4 = 1..4`, `VIEW_TYPE_LINE = 0` / `VIEW_TYPE_DECUSSATION = 1`, `VIEW_DECUSSATION_3124 = 3124`, `VIEW_DECUSSATION_4123 = 4123`, `VIEW_DECUSSATION_HFLIP = 6`, `VIEW_DECUSSATION_VFLIP = 7`. **[SUY mạnh]** đây chính là miền giá trị của tham số `int` đó ⇒ `VIEW_CHANNEL_n` có thể yêu cầu HAL trả **một kênh camera** thay vì khung 4-in-1, và `VIEW_DECUSSATION_3124/4123` là hai thứ tự ô 2×2 sẵn có ở HAL. Chưa có chỗ gọi nào trong firmware đã giải nén (`grep VIEW_CHANNEL_1` chỉ ra chính tệp hằng) ⇒ **phải đo trên xe** (§6-C, §7).

### 1.3 Camera id + cỡ ảnh

- **Không hardcode id.** Địa chỉ hoá bằng **chuỗi "camera code"** rồi mới đổi sang int: `C0759g.m2484a(String)` (`:66`); bộ mô tả code `p055n/C0810f.java:35` `(String, boolean, C0759g.a, int, int, boolean, boolean)` + `m4638j(String)` phân tích chuỗi code (`:58`); hub khoá mọi thứ bằng chuỗi đó (`p055n/C0807c.java:56` `HashMap<String, C0812h>`) [ĐO]. Pool có `getCameraNumbers` @0x61fde, `getCameraId` @0x61fd2, `isValidCamera` @0x63f6f, `getValidCameraTag` @0x63248, `availableCameras` @0x5f5df ⇒ **liệt kê lúc chạy** [ĐO].
- **Chuỗi `pano_h` @0x65603 trong Electro không phải token vô danh** — nó là **tag camera của BYD**: `DiLinkCameraConstants.java:19` `CAMERA_CAR_PANO_H = "pano_h"` (cùng họ `rear`, `apa`, `pano_l`, `ims`, `dms`, `oms`, `rvs`, `nvs`, `uav`, `faceid`, `rf`) [ĐO]. Tra id bằng `IDiLinkBmmCameraInfo.getCameraId(String)` (`:14`), danh sách bằng `getAvailableCameraTypeList()` (`:10`).
- **Trên xe này, ai là id mấy — đã có [ĐO], từ carlog của chính Kachi**: `carlog-kachi-20260914-2044/10-logcat-baseline.txt:1776` → `vehicle.config.cam_sort:rear:0;pano_h:1;`. Dạng `<tag>:<id>;`. Tức HAL `AVMCamera` lộ **2 luồng (id)** — xe vẫn có **4 camera vật lý** (trước · sau · 2 dưới gương, cấu hình Seal Performance VN), 4 mắt ấy đã được ghép sẵn trong luồng `pano_h`: `rear` = **id 0**, `pano_h` (fisheye 4-in-1) = **id 1**. Khớp luôn với launcher gốc: `VehicleUtils.java:176` `hasAVMRecorder() = getAvailableCameraType().contains(CAMERA_CAR_PANO_H)`, và `getAvailableCameraType()` fallback về `SystemProperties.get("vehicle.config.cam_sort","")` (`:187-192`, `SystemProperties.get` ở `:192`) [ĐO]. ⇒ Kachi có sẵn một phép thử năng lực **rẻ hơn** `sys.byd.pano` (chuỗi @0x67865 của Electro, mức [ĐOÁN] vì chỗ gọi bị gói): `getprop vehicle.config.cam_sort` rồi tìm `pano_h`.
- Kéo theo: id 2/3/4/5 **không tồn tại** trên xe này, nên `CamView.REAR_LEFT(…, 2, …)`, `REAR_RIGHT(…, 3, …)` (`CameraSignalPolicy.kt:207-208`) chắc chắn không lên hình — không phải bug mode, mà là **không có camera** [SUY, dựa trên cam_sort].
- **Cỡ ảnh không bị nướng cứng ở đâu cả** [ĐO]: `strings` + immediate `.text` + hằng float + Java + resources đều **0 hit** cho 5120/1280/960/1920 trong `libelectropkg.so`. Nó hỏi/đọc lúc chạy: `setPreviewSize` @0x66c80, `getSupportedPreviewSizes` @0x63017, `getPreviewWidth`/`getPreviewHeight` @0x62c58/@0x62c47, và **lỗi không chết**: `Error setting preview size - ` @0x4c0ba [ĐO]. **Ngoại lệ đáng chú ý**: hình học 4 dải *thì* bị nướng cứng — số `0.25` nằm thẳng trong shader (@0x493b5), và `selectedCameraId=1` nướng trong một chuỗi log (@0x47d24).

### 1.4 Frame về bằng texture, không bằng byte[]

Hợp đồng frame của Electro **chỉ có texture** [ĐO]: `p055n/InterfaceC0813i.java:6-25` phơi đúng `getTextureId()`, `getWidth()`, `getHeight()`, `float[] mo4627b()` (ma trận 4×4) và long; bản hiện thực `p055n/C0808d.java:50` là ctor 11 tham số `(String, int, boolean, int, float[], int, int, boolean, boolean, long, long)`, mọi field `final` và chỉ gán ở đó. Trình vẽ `p056o/C0838m.java:459-465` nhận `Map<String, ? extends InterfaceC0813i>` — **không có `byte[]`/`ByteBuffer` nào trong cả package camera (`p055n`) và package EGL (`p054m`)** [ĐO].

Nguồn texture, đọc được vì constructor không bị gói [ĐO]: `p054m/C0802d.java:51-56` (rút gọn, bỏ 2 dòng gán field)
```java
int texId = C0801c.m4505a();                       // glGenTextures + GL_TEXTURE_EXTERNAL_OES
SurfaceTexture st = new SurfaceTexture(texId);
st.setOnFrameAvailableListener(this);
this.f2639e = new Surface(st);                     // <- Surface này đưa cho addPreviewSurface
```
và mỗi camera có một bọc riêng `p055n/C0812h.java`: `SurfaceTexture f2725h` (`:31`), `Surface f2726i` (`:34`), `float[16] f2721d` (`:43`), lớp lồng `implements SurfaceTexture.OnFrameAvailableListener` (`:64-74`), `C0758f f2722e` = cầu reflection (`:22`).

- "hai timestamp": **[SUY]** — [ĐO] chỉ là "hai `long`" (`C0808d.java:40,43`; interface chỉ phơi một).
- "không hề có byte[]": **[SUY] (mạnh)** — pool *có* `setPreviewCallback` @0x66c5a, `enablePreviewCallback` @0x613a1, `disablePreviewCallback` @0x60ee8, `$IPreviewCallback` @0x5ea82 và `convertNV21ToI420`, nhưng không gán được cho đường nào vì pool đã sắp xếp (§0b). Chắc chắn được: **trong bề mặt kiểu đọc được của đường camera/GL không tồn tại đường byte[]**, và cầu `C0758f` chỉ truyền được `Surface` + int, không truyền nổi một listener [ĐO].
- Đường CPU *có thật* nhưng ở nhánh **ghi/phân tích**: `p051l/C0773j.java:827` `native ByteBuffer m3756L(Map<String,? extends InterfaceC0813i>, int, int)` + `:879` + `glReadPixels` @0x63528 + 12 export `Java_..._MOG2Utils_*` (trừ nền, phát hiện chuyển động) [ĐO]. ⚠ Không phải "glReadPixels chỉ để xuất GIF" như suy đoán ban đầu.

### 1.5 Luồng và các bên tiêu thụ

- **Một hub dùng chung, chạy luồng riêng** [ĐO]: `p055n/C0807c.java` — `Thread f2677s` (`:29`), `HashMap<String,C0812h> f2666h` (`:56`, một bọc / một camera code), `HashMap<String,C0808d> f2667i` (`:59`, frame mới nhất / camera), hai `ArrayList<InterfaceC0811g>` (`:44`,`:47`), `HashMap<InterfaceC0811g, ArrayList<String>> f2665g` (`:53`, bên tiêu thụ khai nó cần camera nào), hàng việc + `CountDownLatch` (`:50`). Singleton `p055n/C0809e.java:14` (`private static C0807c f2710b`) + `:22` (`native C0807c m4633a(Context)`). Hợp đồng bên tiêu thụ: `InterfaceC0811g.java:13` `List<String> mo3167b()` + `:28` `mo3176g(C0807c, Map<String,C0808d>)`.
- **Đúng 4 bên tiêu thụ** (`implements InterfaceC0811g`) [ĐO]: `C0815k.java:17` (WebRTC/stream) · `C0820p.java:21` + `C0761i.java` (cửa sổ nổi `WindowManager`, `SurfaceView f1573e`, `m2567Y()` dựng view trong native) · `C0822r.java:20` (bố cục toàn màn trên một `SurfaceView` bất kỳ) · `p051l/AbstractC0765b.java:34` (`MediaCodec`/`AudioRecord`, bản ghi).
- Luồng đặt tên rõ: `FrameFeederThread` @0x4c3cb, `VideoProcessorThread` @0x5dbc0, `LiveCam-` @0x59ad8 + `-GPU` @0x4871d + `-Worker` @0x4872e, `runLoop(): hasPano=` @0x66168 [ĐO] ⇒ **không vẽ trên luồng UI**.
- **Không có Activity camera nào ở chế độ thường** [ĐO]: chỉ `activity_sentry_message.xml` có `SurfaceView android:id="@+id/camera_surface"` với `visibility="gone"` (`R.java:836`), thuộc `SentryMessageActivity` (`android:exported="true"`). Cấu hình camera **sửa từ xa qua JSON**, phiên do push `receiver/push/action/JoinSocketIO.java` mở [SUY].
- **Dỡ camera 3 bước, mỗi bước bắt lỗi riêng** [SUY]: hai tag lỗi *khác nhau* `livecam-graph-stop-remove-error: ` @0x644e7 và `livecam-graph-stop-release-error: ` @0x644c4 dưới `livecam-graph-stop: reason=` @0x64509 ⇒ `stopPreview` → `rmPreviewSurface` → `release/close`. Thứ tự là suy luận từ tên; **nhưng `rmPreviewSurface(Surface,int)` có thật trong SDK BYD** (`IDiLinkAVMCamera.java:38`) và Kachi **không gọi** (§5).
- **Đàm phán fps, có bỏ khung khi nghẽn** [ĐO, đủ nhiều khoá log để chắc]: `set-fps-source-` @0x6657e, `-baseFPS-` @0x48776, `-desiredFps-` @0x487ae, `-appliedFps-` @0x4875f, `-reqFps-` @0x48893, `cfg-fpsChangeThreshold-` @0x5fae4, `fps-change-from-` @0x6198e; nghẽn: `-busySkip-` @0x4878a, `-throttleSkip-` @0x488da, `-pending-` @0x4884e, ` dropped=` @0x47939, ` capacity=` @0x478e6. Chuẩn hoá mốc thời gian trước khi vào encoder: ` rawTimestampNs=` @0x47cb7, ` outputTimestampNs=` @0x47c15, `eglPresentationTimeANDROID` @0x611ce, `maybeLogAvDrift(): driftUs=` @0x647f2.

---

## 2. Bố cục pano 4-in-1 + map dải ↔ camera ↔ hướng

### 2.1 Hình học — [ĐO], nằm thẳng trong shader

Luật chọn dải đơn, literal @0x493b5 [ĐO]:
```glsl
samplePos = vec2((vTexCoord.x * 0.25) + uStripOffset, vTexCoord.y);
```
Số `0.25` là **hằng trong shader, không phải uniform** ⇒ Electro cũng coi pano là **4 dải dọc bằng nhau, mỗi dải 25% bề ngang, cao trọn khung** — khớp 100% `5120×960 = 4 × 1280×960`. Luật lưới 2×2, literal @0x4911f (xem trích đoạn §3.1): `localX = mod(vTexCoord.x, 0.5) * 0.5` (đúng bề rộng 1 dải) + `localY = mod(vTexCoord.y, 0.5) * 2.0` (kéo lại full chiều cao), offset mỗi góc lấy từ `uGridStripOffsets.xyzw` theo thứ tự **TL, TR, BL, BR** [ĐO].

| Dải | x chuẩn hoá | px (5120×960) | `uStripOffset` kỳ vọng | Tâm dải | Electro nói gì | kinex nói gì | Mức |
|---|---|---|---|---|---|---|---|
| 0 | `[0.00, 0.25)` | x 0–1279 | `0.00` | `0.125` | không gán nhãn | không dùng | hướng = **[CHƯA BIẾT]** |
| 1 | `[0.25, 0.50)` | x 1280–2559 | `0.25` | `0.375` | không gán nhãn | crop **TRÁI** `x[0.25,0.35]` + tâm fisheye `(0.375, 0.5)` = **đúng tâm dải 1** | **TRÁI [SUY]** |
| 2 | `[0.50, 0.75)` | x 2560–3839 | `0.50` | `0.625` | không gán nhãn | crop **PHẢI** `x[0.65,0.75]` + tâm fisheye `(0.625, 0.5)` = **đúng tâm dải 2** | **PHẢI [SUY]** |
| 3 | `[0.75, 1.00)` | x 3840–5119 | `0.75` | `0.875` | không gán nhãn | không dùng | hướng = **[CHƯA BIẾT]** |

Nguồn kinex: `../jadx-kinex/sources/Y0/C0094o.java:70` `f1783w = {0.25f,0.0f,0.35f,1.0f}` (trái) · `:73` `f1784x = {0.65f,0.0f,0.75f,1.0f}` (phải) · `:76` `f1785y = {0.375f,0.5f}` · `:79` `f1786z = {0.625f,0.5f}` · `:82` `f1771A = {0.25f,1.0f}` (cỡ một dải) [ĐO]. Hai crop **đối xứng gương quanh x = 0.5** (0.25↔0.75, 0.35↔0.65), đúng kiểu hai camera hông gắn đối xứng và lấy hai dải biên ngoài ⇒ đó là lý do đặt dải 1 = trái, dải 2 = phải ở mức [SUY]. Theo CLAUDE.md §14, hằng của app bên thứ ba **tối đa là "nghi là"** — cần một khung 5120×960 chụp từ xe mới lên [ĐO].

### 2.2 Ba manh mối về thứ tự dải, và cả ba đều chưa chốt được

| Manh mối | Nội dung | Mức | Vì sao chưa chốt |
|---|---|---|---|
| Shader Electro | 4 offset nằm trong `uniform vec4 uGridStripOffsets` (@0x68481), do code đã bị VMP đặt vào | [ĐO] là uniform · giá trị = **[CHƯA BIẾT]** | shader **không** ràng buộc offset phải là {0, .25, .5, .75}; chỗ gọi `glUniform4f` nằm trong bytecode VM |
| Hằng HAL BYD | `VIEW_DECUSSATION_3124 = 3124` và `VIEW_DECUSSATION_4123 = 4123` (`DiLinkCameraConstants.java:51-52`) | tồn tại = [ĐO] · nghĩa "thứ tự ô 3-1-2-4 / 4-1-2-3" = **[SUY]** | không có chỗ gọi nào trong firmware đã giải nén |
| 2 hàm CPU của Electro | `combineCamerasTo2x2GridNV21ToI420` cho TL←1, TR←0, BL←3, BR←2; bản `…ToNV12` lại cho TL←3, TR←0, BL←1, BR←2 | hoán vị = [ĐO] (giải mã `libnative-lib.so` @0x20208 / @0x1fe48) | **cả hai là code chết** (§0b) ⇒ **không dùng làm bằng chứng về xe**. Bất đồng ở đúng cặp TL/BL nên ít nhất một bản đã hỏng/cũ |

⚠ **Electro không gán nhãn ngữ nghĩa cho dải ở bất cứ đâu đọc được** [ĐO]: trong 12785 chuỗi không có token nào kiểu `frontCamera`/`leftCamera`/`stripLeft`/`cam0`; dải chỉ được địa chỉ hoá bằng số học. Ngữ nghĩa nằm ở **bảng SQLite do người dùng gán**: `CREATE TABLE camera_layout_slots (layout_id INTEGER NOT NULL, slot_id TEXT NOT NULL, camera_code TEXT NOT NULL, PRIMARY KEY(layout_id, slot_id))` @0x4a834, với `slot_id` ∈ {`top-left` , `top-right` @0x67b49, `bottom-left` @0x5f85d, `bottom-right` @0x5f869} [ĐO]. Kèm `camera_layouts (id, grid_id, sort_order, system_default, created_at, updated_at)` @0x4a8c5 và `camera_layout_feature_selection (feature TEXT PRIMARY KEY, layout_id INTEGER NOT NULL)` @0x4a7d0 [ĐO]. ⇒ **Triết lý Electro: không đoán hướng, cho người dùng gán rồi lưu.** Đúng tinh thần CLAUDE.md §7.

Tám bố cục dựng sẵn (tất cả `implements InterfaceC0832g`, rect đích chuẩn hoá + góc xoay từng ô) [ĐO] — hai cái là hình 360 kinh điển:
- `p056o/C0844s.java:22-25` — 2×2 phẳng: bốn ô `0.5 × 0.5` tại (0,0)/(0.5,0)/(0,0.5)/(0.5,0.5).
- `p056o/C0825a0.java:22-25` — hình **chữ thập**: trên `(0, 0, 1.0, 0.27128863)` xoay `0` · trái `(0, 0.27128863, 0.5, 0.45742276)` xoay **270** · phải `(0.5, 0.27128863, 0.5, 0.45742276)` xoay **90** · dưới `(0, 0.72871137, 1.0, 0.27128863)` xoay **180**.
- `p056o/C0849x.java:24-25` — **2 ô gương** cạnh nhau, xoay 90 và 270, source rect truyền vào ctor (`:21`) ⇒ **đây đúng ca dùng của Kachi**.
- `p056o/C0851z.java:27` — một ô toàn màn, source rect co vào `0.03..0.97` để **giấu viền méo của fisheye**.
- Còn lại: `C0827b0.java:22-26` (1 lớn trên + 4 nhỏ), `C0842q.java:22-26` (1 lớn trái 8/14 + 4 nhỏ phải 3/14), `C0845t.java:22-25`, `C0850y.java:23`. Đăng ký ở `p056o/C0833h.java:11-21`.
- ⚠ Lưới 2×2 **không giữ tỉ lệ khung** [ĐO]: mỗi góc nhận trọn một dải 1280×960 (4:3) nhồi vào 1/4 khung ra, shader không có bước letterbox nào ⇒ méo nếu mỗi ô không phải 4:3.

### 2.3 Trạng thái xuất của AVM (`BYDAutoPanoramaDevice`) — Electro **không** dùng

Trong `libelectropkg.so` **không có** chuỗi `panorama`/`BYDAutoPanoramaDevice` nào (`grep -i panorama` → 0; chỉ có `uHasPano`, `sys.byd.pano`, `pano_h`) [ĐO], dù nó vẫn nạp hơn chục lớp `BYDAuto*Device` khác. Nhưng Electro *có* biết phải nhường khi hệ thống camera của xe đang chạy: `res/values/strings.xml:74` `cluster_projection_pause_message` = "App paused\nwhile the camera\nsystem is active.", cùng `ClusterProjectionPauseActivity` trong manifest [ĐO] — **cơ chế kích hoạt thì [CHƯA BIẾT]** (tín hiệu số, `$IEventCallback`, hay khác).
⇒ **[SUY] đáng để Kachi kiểm**: nếu `AVMCamera` id 1 (= `pano_h`) *luôn* trả khung 4-in-1 thô bất kể trạng thái xuất, thì việc Kachi đặt `outputState` (1 = LEFT, 2 = RIGHT… `CamView`) có thể **không cần** cho đường pano — chính chuỗi `selectedCameraId=1 renders-full-frame` @0x47d24 nói camera đó "vẽ trọn khung". Phép đo ở §7.

---

## 3. Shader nắn fisheye — trích đoạn (toàn văn giữ cục bộ) + toán + uniform

### 3.0 Cách các shader này được lấy ra (đừng lặp lại lỗi của chúng tôi)

Toàn bộ GLSL của **đường camera** nằm trong pool chuỗi của `libelectropkg.so`, **không** trong dex (2412 tệp: `uHasPano`/`uCameraTex`/`uStripOffset`/`uSourceRect`/`uGridMode`/`uGridStripOffsets` = **0 hit**; các lớp `AVMCamera`/`JNIBMMCamera`/`BmmCameraInfo`/`NormalCamera`/`$IPreviewCallback` cũng 0 hit) [ĐO]. Dex vẫn có GLSL **khác**, không liên quan pano: `p049e/p050k/p054m/C0803e.java:28` (blit OES của encoder — và đây là **shader duy nhất của app còn nguyên dạng Java**) + `org/webrtc/GlGenericDrawer.java:10,57-72`, `GlRectDrawer.java:5`, `YuvConverter.java:11` [ĐO].

Đó là **6 văn bản shader** + 1 bộ dựng vertex tham số hoá, ghép từ **34 mảnh** (27 mảnh nguồn + 7 chuỗi tên uniform tra cứu) [ĐO]. Vỡ mảnh vì **nội suy chuỗi lúc chạy**, và điều này chứng minh được mà **không** cần phép thử vòng tròn nào: các mảnh đầu kết thúc **giữa câu khai báo** — 0x47e10 dừng ở `uniform samplerExternalOES `, 0x47eba và 0x48004 dừng ở `uniform float ` — còn các mảnh thân (0x49099, 0x490e2, 0x4911f 792 B, 0x483cd, 0x4850a, 0x48fbc, 0x48fd8, 0x48aad, 0x48aed, 0x48b0f) mỗi mảnh xuất hiện **đúng 1 lần** và **không** nằm trong bất kỳ literal hoàn chỉnh nào [ĐO]. Một thân shader chỉ tồn tại dưới dạng mảnh mở `;\n` và đóng bằng `(` hoặc `= ` thì **chỉ dùng được bằng nối chuỗi**. Chỉ 3 chương trình là literal tự đủ: 0x48113 (361 B), 0x5f481 (144 B), 0x6586f (134 B).
⚠ **Thứ tự offset trong `.rodata` KHÔNG phải thứ tự nguồn** (§0b) — thứ tự ghép suy ra từ tính hợp lệ của GLSL, không phải từ vị trí byte. Mọi chỗ `<...>` dưới đây là **văn bản chèn lúc chạy, [CHƯA BIẾT]**: tên uniform hoặc số nướng cứng.

### 3.1 Shader D — chọn dải pano / lưới 2×2 (trích đoạn; ghép đủ cục bộ)

Header @0x47eba (329 B) + thân @0x4911f (792 B) + @0x473e7 (61 B) + @0x47425 (170 B) [ĐO]:

```glsl
// TRÍCH ĐOẠN (14 dòng) — toàn văn ghép từ 34 mảnh .rodata được lưu CỤC BỘ, không đăng lên repo public
// vì đây là mã của app trả phí bên thứ ba (rủi ro bản quyền, quét bảo mật 2026-09-27). Ý nghĩa toán học ở §3.3.
uniform samplerExternalOES uCameraTex;
uniform mat4  uTexMatrix;
uniform float uHasPano;
uniform float uGridMode;
uniform float uStripOffset;
uniform vec4  uGridStripOffsets;
uniform float <FLIP_H>;          // tên: [CHƯA BIẾT]
uniform float <FLIP_V>;          // tên: [CHƯA BIẾT]
  vec2 samplePos;
      float localX = mod(vTexCoord.x, 0.5) * 0.5;   // -> dải rộng 0.25
      samplePos = vec2(localX + stripOffsetX, localY);
      samplePos = vec2((vTexCoord.x * 0.25) + uStripOffset, vTexCoord.y);
    samplePos = (uTexMatrix * vec4(texCoord, 0.0, 1.0)).xy;
  gl_FragColor = texture2D(uCameraTex, samplePos);
```

Ba điều đọc ra được, cả ba là [ĐO]:
- **Nhánh pano KHÔNG áp `uTexMatrix`** — phép nhân ma trận chỉ nằm trong `else`. Nhánh không-pano áp ma trận, nhánh pano lấy toạ độ texture **thô**.
- **Hai cờ lật chỉ có tác dụng ở nhánh KHÔNG-pano.** Ở chế độ pano (dải đơn hoặc lưới) hai cờ bị bỏ qua hoàn toàn — nếu Kachi port shader này thì **phải bổ sung lật trong nhánh pano**, vì Kachi cần soi gương.
- **Không có kiểm biên, không viền đen** trong shader D (dựa vào clamp của sampler).
- ⚠ "Cả 4 camera trong **một** lệnh vẽ": **[SUY]**, không phải [ĐO] — số lệnh vẽ không nằm trong artifact (`libelectropkg.so` không import EGL/GLES, gọi GL qua JNI theo tên: `android/opengl/GLES20` @0x4e5b6, `glShaderSource` @0x63535). Cùng chương trình này *cũng* phục vụ được cách vẽ 4 quad với `uGridMode <= 0.5`. Chốt bằng GL trace / hook `glDrawArrays` 🚗.

### 3.2 Shader E — source rect + xoay + **nắn fisheye** (trích đoạn; ghép đủ cục bộ)

Header @0x48004 (270 B) + @0x490e2 + @0x48a95 + @0x48aad + @0x48b0f + @0x483cd (303 B) + @0x47333 (179 B) + @0x483b3 (25 B) + @0x48fd8 (67 B) + @0x48fbc (27 B) + @0x4850a (267 B) + @0x474d0 (67 B) + @0x47514 (464 B) [ĐO từng mảnh; thứ tự ghép [SUY] theo tính hợp lệ GLSL]:

```glsl
// TRÍCH ĐOẠN (14 dòng) — toàn văn ghép từ 34 mảnh .rodata được lưu CỤC BỘ, không đăng lên repo public
// vì đây là mã của app trả phí bên thứ ba (rủi ro bản quyền, quét bảo mật 2026-09-27). Ý nghĩa toán học ở §3.3.
uniform samplerExternalOES uCameraTex;
uniform mat4  uTexMatrix;
uniform vec4  uSourceRect;
uniform float uHasPano;
uniform float <FLIP_H>;
uniform float <FLIP_V>;
    float theta     = atan(pLen, <PARAM_F>);                 // atan 2 đối số = atan2
    float r         = theta * <PARAM_K>;
    correctedLocal  = mix(local, projectedLocal, amount);
      gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0); return;       // viền đen #1
    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0); return;         // viền đen #2
  vec2 raw       = uSourceRect.xy + (correctedLocal * uSourceRect.zw);
  vec2 samplePos = raw;
  if (uHasPano <= 0.5) { samplePos = (uTexMatrix * vec4(raw, 0.0, 1.0)).xy; }
```

Hai shader còn lại, ngắn, đều là literal nguyên vẹn [ĐO]: **F** (@0x47e10, rect + lật, **có** viền đen, không pano/fisheye) · **C** (@0x48113, blit OES thuần *luôn* áp `uTexMatrix`) · **A** vertex (@0x5f481) · **B** (@0x6586f, blit `sampler2D uTex` — dùng cho bước FBO→màn).
⚠ Vì shader C (@0x48113) **không hề có** `uHasPano` và áp `uTexMatrix` vô điều kiện, câu "Electro **không bao giờ** áp `uTexMatrix` cho pano" là **sai theo nghĩa chữ**; đúng là: "trong hai shader *có biết* pano thì phép nhân bị chặn vào nhánh không-pano". Camera id 1 có khi nào đi qua shader C hay không: **[CHƯA BIẾT]**.

### 3.3 Toán của phép nắn — [ĐO], và nó KHÔNG phải mô hình quen tay

Chiều ánh xạ: `local` ← từ `vTexCoord` (**đích**); `correctedLocal` → `uSourceRect` → texture (**nguồn**). Tức **ngược: đích → nguồn**, đúng kiểu lấy mẫu.

| Bước | Công thức | Nghĩa |
|---|---|---|
| bán kính đích | `pLen = length((local − 0.5) · 2)` | 0 ở tâm rect, 1 ở biên |
| góc tia | `theta = atan(pLen, F)` = `atan2(r_dst, F)` | ⇒ `r_dst = F·tan θ` ⇒ **đích là phối cảnh thẳng (rectilinear/gnomonic)**; `F` = tiêu cự chuẩn hoá của khung ra; nửa-FOV tại bán kính 1 là `θmax = atan(1/F)`, tức `F = 1/tan(θmax)` [SUY cho phần diễn giải vật lý] |
| bán kính nguồn | `r = theta · K` | `r_src ∝ θ` ⇒ **nguồn là fisheye ĐẲNG KHOẢNG (equidistant, r = f·θ)**; fisheye 180° phủ hết bán kính 1 ⇒ `K ≈ 1/(π/2) ≈ 0.6366` [SUY] |
| tổng hợp | **`r_src = (0.5·K·SCALE) · atan(r_dst / F)`** | một tham số tỉ lệ duy nhất nhân vào |
| cường độ | `amount = clamp(AMOUNT,0,1)` rồi `mix(local, projectedLocal, amount)` | **là một phép TRỘN**, không phải mô hình thuần: chỉ khi `amount == 1` nguồn mới thật sự equidistant. Ở giữa, `r_eff = (1−a)·r_dst + a·K·atan(r_dst/F)` — không thuộc mô hình có tên nào |

**Loại trừ được, và loại trừ vét cạn** [ĐO]: trong toàn `.rodata` có **đúng một** `atan(` (@0x473c9), **đúng hai** chuỗi chứa `theta` (@0x473c9, @0x483b6), **không** `pow(`/`asin(`/`sqrt(`/`tan(` trong GLSL, và `sin(`/`cos(` duy nhất là `cos(rotationRad)` @0x483d0 / `sin(rotationRad)` @0x483f8 (xoay rect, không phải θ). ⇒ **không** equisolid (`2f·sin(θ/2)`), **không** stereographic (`2f·tan(θ/2)`), **không** đa thức.

Bốn ràng buộc phải nói kèm, nếu không sẽ dựng sai:
1. **Chỉ chạy cho nguồn pano**: cổng là `uHasPano > 0.5 && <FISH_ON> > 0.0001` ⇒ Electro không đụng camera thường (đã được HAL nắn sẵn). Shader lưới 2×2 (@0x4911f) **không có một dòng fisheye nào** — chế độ lưới hiển thị dải fisheye **thô**.
2. **Tâm quang bị ghim ở `(0.5, 0.5)` của rect** — hai hằng `0.5` nằm nguyên trong literal (@0x4735b, @0x48ff9), **không** có uniform/hằng nào cho lệch tâm, cũng không có hằng bán kính vòng ảnh [ĐO]. Nói cho đúng: mọi camera dùng chung **GIẢ ĐỊNH** "tâm quang = tâm rect", **không** phải "dùng chung một tâm" — vì rect khác nhau thì tâm theo pixel cũng khác nhau. (Không có hằng bán kính vòng ảnh **không** có nghĩa là thiếu: `K · SCALE` chính là hệ số f-theta, chỉ là đã tham số hoá.)
3. **Không có hiệu chỉnh tỉ lệ khung**: `length(p)` đo trong không gian rect chuẩn hoá và chỉ có một hệ số vô hướng ⇒ với rect không vuông theo pixel (dải Seal là 1280×960 = 4:3) các đường đồng-θ là **ellipse** theo pixel, không phải tròn [SUY]. `<SCALE>` là float hay vec2: **[CHƯA BIẾT]**.
4. `theta = atan(pLen, F)` chỉ bằng `atan(pLen/F)` khi **F > 0** (atan2 với đối số thứ hai âm trả góc trong `(π/2, π]`).

⚠ **Electro GIẢ ĐỊNH ống kính equidistant.** Nếu fisheye AVM của BYD là equisolid (khá thường gặp với AVM ~190°), thì **chính phép nắn của Electro đã hơi sai ở vùng biên** ⇒ **không được copy hằng số của nó như sự thật về xe**; copy **cấu trúc**, còn số thì tự chỉnh bằng mắt (§6-B).

### 3.4 Bảng uniform

| Uniform | Kiểu | Có chuỗi tra cứu riêng? | Có ở shader | Vai trò | Mức |
|---|---|---|---|---|---|
| `uCameraTex` | `samplerExternalOES` | ✅ @0x68466 | C, D, E, F | texture OES của `SurfaceTexture` | [ĐO] |
| `uTexMatrix` | `mat4` | ✅ @0x684ba | C, D, E | ma trận biến đổi; **[SUY]** là `SurfaceTexture.getTransformMatrix` (`getTransformMatrix` @0x631e3 + `glUniformMatrix4fv` @0x63585 chỉ tồn tại ở dạng chuỗi — chỗ gán bị VMP) | [ĐO] tồn tại · [SUY] nội dung |
| `uHasPano` | `float` (cờ) | ✅ @0x68496 | D, E | `>0.5` ⇒ nguồn là khung 4-in-1 | [ĐO] |
| `uGridMode` | `float` (cờ) | ✅ @0x68477 | D | `>0.5` ⇒ lưới 2×2 thay vì 1 dải | [ĐO] |
| `uStripOffset` | `float` | ✅ @0x684a5 | D | offset x của dải đang chọn (kỳ vọng `i·0.25`) | [ĐO] tồn tại · giá trị [CHƯA BIẾT] |
| `uGridStripOffsets` | `vec4` | ✅ @0x68481 | D | 4 offset dải cho **TL, TR, BL, BR** theo `.xyzw` | [ĐO] thứ tự thành phần · giá trị [CHƯA BIẾT] |
| `uSourceRect` | `vec4` (x,y,w,h) | ❌ **chỉ nằm trong text shader** (@0x480d2, @0x4760f, @0x47632) | E | vùng con của texture nguồn, áp ở **bước cuối** | [ĐO] tồn tại trong nguồn · ⚠ xem ghi chú |
| `uTex` | `sampler2D` | ✅ @0x684b5 | B | blit FBO → màn | [ĐO] |
| `<FLIP_H>` / `<FLIP_V>` | `float` (cờ) | ❌ | D, E, F | lật ngang/dọc, so `> 0.5` | tên = **[CHƯA BIẾT]** |
| `<RECT>`, `<ROT_DEG>`, `<FISH_ON>`, `<PARAM_F>`, `<PARAM_K>`, `<SCALE>`, `<AMOUNT>` | — | ❌ | E | 7 chỗ chèn: rect nội bộ, góc xoay, bật fisheye, 2 tham số, tỉ lệ, cường độ | **[CHƯA BIẾT]** — số nướng cứng hay tên uniform |
| attribute `aPosition`, `aTexCoord` | `vec4`, `vec2` | ✅ @0x5e5b2, @0x5e5bf | A | quad toàn màn | [ĐO] |

⚠ Hai ghi chú quan trọng về bảng này:
- **`uSourceRect` không có chuỗi đứng riêng** ⇒ code đã bị gói **không bao giờ gọi `glGetUniformLocation("uSourceRect")`** ⇒ **[SUY]** đường source-rect (shader E) có thể **chưa được nối dây / ít dùng** ở 1.13.0, hoặc vị trí uniform được lấy bằng cách khác. Đây là rủi ro phải biết trước khi coi shader E là "đường đang chạy của Electro".
- **`<RECT>` trong thân E có thể KHÔNG phải `uSourceRect`** mà là một `vec4` crop nội bộ nướng cứng: nếu `<RECT> == uSourceRect` thì bước cuối lại nhân rect **lần thứ hai**, và tâm fisheye `(0.5,0.5)` sẽ rơi vào **ranh giới dải 2/3** của ảnh 4-in-1 — vô nghĩa về quang học. Với `<RECT>` là rect nội bộ trong `[0,1]²` thì mọi thứ khớp: tâm fisheye = tâm dải, kiểm biên `[0,1]` có nghĩa. **[SUY]**, chốt bằng dump `glGetShaderSource` 🚗.

### 3.5 Tham số fisheye lưu ở đâu

Migration thêm cột, **nhưng chỉ tên hàm là [ĐO], tên cột là [SUY]**: `database/migrations/Version18.java:13,15,17,19,21,23` (`getAddExtraCamerasFisheyeEnabledSql` / `…FisheyeParameterASql` / `…FisheyeParameterBSql` / `getAddSlotsFisheyeEnabledSql` / `…ParameterASql` / `…ParameterBSql`) + `Version20.java:13,15,17,19` (4 hàm `…FlipHorizontal/FlipVerticalSql`) + `Version16.java:13` `getCreateExtraCamerasTableSql`; DB version **25** (`p065h/C0899a.java:20` `super(context, m6940a(), null, 25)`) [ĐO]. Cả hai tệp **không chứa một câu SQL nào** — thân là `native`, và grep byte trên **cả 6 `.so`**: `fisheye_` = **0 hit**, `flip_` = **0 hit**, `extra_cameras` = **0 hit** [ĐO]. Suy tên cột từ camelCase là **không an toàn**, có phản ví dụ trong chính binary này: `getAddTotalFuelConSql` → **2** cột (`total_fuel_con_start/_end` @0x49d77/@0x49d34), `getAddMileageEvSql`+`…HevSql` → **4** cột, `getAddTotalFuelConLastRawSql` → **không** chuỗi nào chứa `last_raw`.

POJO tương ứng thì đọc được [ĐO]: `p056o/C0828c.java:23` `C0828c(boolean, int, int)` = {bật, a, b} + `m4924r(int)` kiểm miền + JSON hoá (`:41`, `:65-71`) · slot `p056o/C0839n.java:36` `(String slotId, String cameraCode, C0828c fisheye, boolean flipH, boolean flipV)` · camera phụ `p056o/C0836k.java:38` `(String, String, C0824a size, RectF dest, int rot, C0828c, boolean, boolean)` · bố cục `p056o/C0835j.java:43` · DAO `p065h/p066d/C0903b.java:65` `m7005R(ContentValues, C0828c)` (ghi 3 cột fisheye), `:68` `m7006S(ContentValues, boolean, boolean)` (ghi 2 cột lật), `:74` `m7008U(Cursor)` (đọc lại).
Phát biểu **dùng được** (bỏ cái vế "hoặc uniform đổi" vì nó làm câu không thể sai): **Electro chèn literal số vào nguồn GLSL ⇒ đổi tham số fisheye là phải biên dịch lại program.** Bằng chứng: các mảnh treo lơ lửng `float theta = atan(pLen, ` @0x473c9, `float r = theta * ` @0x483b6, `float amount = clamp(` @0x48fbe, `float rotationRad = radians(` @0x48b14, và hai tag lỗi **riêng cho bố cục**: `Could not compile camera layout shader: ` @0x4ba07 / `Could not link camera layout program: ` @0x4ba4b, khác với bản chung @0x4ba30/@0x4ba72 [ĐO].

---

## 4. Cách Electro vẽ mà không lag

### 4.1 Không `GLSurfaceView`, không `TextureView` — EGL14 tự lái

[ĐO], và mạnh hơn mức chuỗi: `GLSurfaceView`, `TextureView`, `Choreographer`, `requestRender`, `RENDERMODE`, `queueEvent`, `onSurfaceCreated`, `onDrawFrame`, `setEGLContextClientVersion` = **0 hit** trong `classes.dex` **và** trong cả 4 `.so`. Layout chỉ có `SurfaceView` trần (`res/layout/activity_sentry_message.xml:7-12`, `activity_cluster_mirror.xml:6-9`). `org/webrtc/SurfaceViewRenderer.java:12` *có* extends `SurfaceView implements SurfaceHolder.Callback` nhưng **không lớp nào của app tham chiếu** ⇒ lib đi kèm không dùng.
⚠ Cảnh báo bẫy `grep`: `grep -i "GLSurface"` cho 10 hit trong `libelectropkg.so` — **tất cả là `EGLSurface`**. Phải so khớp đúng chữ.

Tầng EGL của nó là **Grafika**, và phần này packer **không** gói nên đọc được nguyên văn [ĐO]:
- `p054m/C0799a.java:41-59` (= *EglCore*): `EGL14.eglGetDisplay(0)` (`:41`), `eglChooseConfig` **ba lần** (`:53` và `:54` kèm `EglBase.EGL_RECORDABLE_ANDROID`, `:55` bỏ nó làm chốt cuối), `eglCreateContext(..., {12440, 2, 12344})` = `EGL_CONTEXT_CLIENT_VERSION 2` (`:59`). Ctor `C0799a(EGLContext shared, int)` (`:33`) **nhận context chia sẻ**; ctor không tham số (`:29`) truyền `null` ⇒ `EGL_NO_CONTEXT`, **không** chia sẻ.
- `p054m/C0804f.java` (= *WindowSurface/EglSurfaceBase*): đúng hai ctor — `(C0799a, int, int)` offscreen pbuffer và `(C0799a, Surface)` window (`throw new RuntimeException("Failed to create window EGLSurface")`).
- `p054m/C0800b.java:12-38` (= *FullFrameRect*): ma trận đơn vị 4×4 + quad `−1..1` + texcoord vào `FloatBuffer` direct native-order.
- `p054m/C0803e.java:27-35` (= *Texture2dProgram*): cặp GLSL nguyên văn + 5 lần `glGetAttribLocation`/`glGetUniformLocation` theo thứ tự.

### 4.2 Đường khung: OES → FBO → N bên tiêu thụ

| Chặng | Bằng chứng | Có chép CPU? |
|---|---|---|
| HAL → `SurfaceTexture` → texture **`GL_TEXTURE_EXTERNAL_OES`** | 4/6 shader khai `samplerExternalOES` + `#extension GL_OES_EGL_image_external : require` (@0x47e10, @0x47eba, @0x48004, @0x48113); `updateTexImage` @0x6861c, `getTransformMatrix` @0x631e3, `setDefaultBufferSize` @0x66808, `glGenTextures` @0x6349f [ĐO] | **Không** |
| Trộn vào **FBO** | `glGenFramebuffers` @0x6348d, `glFramebufferTexture2D` @0x63476, `glCheckFramebufferStatus` @0x63377, lỗi `LiveCam FBO incompleto: 0x` @0x59a65 [ĐO] | Không, nhưng **có một lượt chép trên GPU** |
| FBO → màn / encoder / WebRTC / bộ phát hiện chuyển động | `prepareGraphSources(): sources=` @0x6590e, ` sinks=` @0x47d5e, `-sinkCount-` @0x488a9; encoder: `createInputSurface` @0x60a31, `signalEndOfInputStream` @0x67325; overlay watermark pass riêng bằng `uniform sampler2D uTex;` @0x658a0 + `glBlendFunc` @0x6336b [ĐO] | Không (trừ nhánh ghi/MOG2, §1.4) |

⚠ **Đừng gọi đây là "zero-copy"** — nó render vào FBO rồi lấy mẫu lại (shader B). Câu đúng: **không có chép CPU trên đường hiển thị**, và trên đường hiển thị texture chưa bao giờ rời GPU.
- `SurfaceTexture` **dựng trên luồng GL và không di trú**: `attachToGLContext`/`detachFromGLContext` = **0 hit** trong toàn bộ pool, trong khi mọi method `SurfaceTexture` khác nó dùng đều có [SUY nhưng chắc].
- **Không truy vấn giới hạn GL**: `glGetIntegerv`, `glGetString`, `GL_MAX_TEXTURE_SIZE`, `glGetError` = 0 hit, dù 30+ tên `GLES20` khác có mặt [ĐO] ⇒ Electro **không** phòng trường hợp texture rộng 5120 vượt `GL_MAX_TEXTURE_SIZE`; nó "cứ chạy" trên phần cứng này [SUY].
- Chân WebRTC (xem xa từ điện thoại): **[SUY]** khung FBO được bọc thành `org/webrtc/TextureBufferImpl` @0x91a5b (ctor `(IILorg/webrtc/VideoFrame$TextureBuffer$Type;ILandroid/graphics/Matrix;Landroid/os/Handler;Lorg/webrtc/YuvConverter;Ljava/lang/Runnable;)V` @0x7ceda) → `VideoFrame` (@0x7368a) → `getCapturerObserver().onFrameCaptured` (@0x62011, @0x650b9). Chỉ [SUY] vì cùng pool cũng có `JavaI420Buffer.wrap` (@0x75867) — không phân biệt được chân nào. Context EGL chia sẻ với WebRTC thì **có thông điệp lỗi riêng** [ĐO]: `LiveCam GPU requer contexto EGL14 compartilhado com WebRTC` @0x59a80 + `LiveCam context indisponivel` @0x59abb; nhưng **"chết cứng" là [ĐOÁN]** — bằng chứng nghiêng về **bắt-rồi-báo** (`Erro ao iniciar LiveCam GPU: ` @0x4bf77, `livecam-gpu-texture-frame-error: ` @0x64469, `livecam-gpu-oem-hub-fallback: no-oem-camera frontCameraId=` @0x643e8). Chiều nhân quả cũng nhẹ hơn mô tả ban đầu: app **LẤY** context của WebRTC (`p078n/C0958m.java:12` `private static EglBase f4009a;` + `:19`; factory `p082q/C0981e.java:28`), không phải ngược lại.

### 4.3 Ba đòn chống giật **thực sự** dùng được cho Kachi

1. **Tự giới hạn nhịp, tự bỏ khung** — đàm phán fps với HAL, đo fps vào, bỏ khung khi lượt vẽ trước chưa xong (`-busySkip-` @0x4878a, `-throttleSkip-` @0x488da, ` dropped=` @0x47939) [ĐO]. kinex cũng làm: bỏ khung nếu chưa đủ 16 ms (`../jadx-kinex/sources/p005b1/RunnableC0171e.java:104`) [ĐO, dẫn lại từ `camera-lag-analysis-2026-09-26.md`]. **Kachi hiện không có cơ chế nào tương đương** — nó vẽ mọi khung HAL đẩy tới, trên **luồng chính của launcher**.
2. **Chuẩn hoá mốc thời gian** trước encoder (`rawTimestampNs` → `outputTimestampNs`, `eglPresentationTimeANDROID`) [ĐO] — chỉ quan trọng khi Kachi đi ghi/stream, chưa cần cho 2.74.
3. **Luồng vẽ riêng, có tên** (`FrameFeederThread`, `LiveCam-…-GPU`) [ĐO] ⇒ không bao giờ tranh luồng UI.
Cộng thêm [SUY]: khung ra được hạ cấp theo **bậc phân giải** (`cfg-videoRes-` @0x5fb8d, nhãn `HD` @0x4c530 / `Full HD` @0x4c41a, `scaleResolutionDownBy` @0x6635c, watermark ship 3 cỡ 48/28/18 px) — **con số pixel thật thì [CHƯA BIẾT]**, không literal nào sống sót.

---

## 5. Đối chiếu với Kachi hiện tại — Kachi đang làm khác/sai chỗ nào

| # | Hạng mục | Electro (và SDK BYD) | Kachi hôm nay | Đánh giá |
|---|---|---|---|---|
| K1 | **KDoc id camera** | `selectedCameraId=1 renders-full-frame` @0x47d24 + carlog `pano_h:1` | `CameraSignalPolicy.kt:187` viết "id 0 (fisheye 4-in-1, 5120×960) và id 1 (cam trước)" — **ngược**; `:200-201` lại viết đúng ("id 1 = fisheye… id 0 crop ra sai") | 🔴 **SAI, phải sửa doc/KDoc ngay** (không đổi code: enum `:202-203` đã dùng `cameraId = 1`) [ĐO] |
| K2 | **Phép thử năng lực pano** | `getAvailableCameraType()` → `getprop vehicle.config.cam_sort` chứa `pano_h` (`VehicleUtils.java:176,187-192`) | không có; suy từ việc mở camera có ra hình | 🟡 thêm được 1 dòng, **không cần xe để viết**, chỉ cần xe để nghiệm [ĐO cơ chế] |
| K3 | **`addPreviewSurface` arity** | BYD: `(Surface, int)` AVM · `(Surface)` Normal | `AvmCamera.kt:60-70` gọi đúng `(Surface, int)` + dò mode 0..3, fallback `(Surface)` | 🟢 **đúng rồi** — và **không được** đổi sang `(Surface, int[])` như descriptor của Electro gợi ý (đó là wrapper của Electro) [ĐO] |
| K4 | **Ý nghĩa `mode`** | `VIEW_DEFAULT=0`, `VIEW_CHANNEL_1..4=1..4` (`DiLinkCameraConstants.java:47-55`) | dò 0..3 rồi **lấy cái đầu tiên nhận** ⇒ gần như luôn trúng `0 = VIEW_DEFAULT` = khung 4-in-1; **chưa bao giờ thử `4`** | 🟡 có thể đang bỏ qua đường HAL rẻ nhất (§6-C) [SUY] |
| K5 | **`setPreviewSize`** | gọi thật, lỗi thì chỉ log (`Error setting preview size - ` @0x4c0ba) | **không gọi** (`AvmCamera.kt` chỉ đọc `getPreviewWidth/Height` ở `:94-95`) | 🟡 thiếu một đòn hạ tải: xin HAL cỡ nhỏ hơn 5120×960 |
| K6 | **Dỡ camera** | 3 bước, có `rmPreviewSurface(Surface,int)` (`IDiLinkAVMCamera.java:38`) | `AvmCamera.kt:122-128` chỉ `stopPreview` + `close`, **không** `rmPreviewSurface` | 🟡 ứng viên rò rỉ khi bật/tắt overlay nhiều lần [SUY] |
| K7 | **Đường vẽ** | EGL14 tự lái + `SurfaceView` + FBO; **không** `TextureView` | `TextureView` + `setTransform(Matrix)` (`CameraOverlayView.kt:46,338-354`), đường phụ là `SurfaceView` không `setTransform` (`:296-311`) | 🟡 khác về bản chất; **đừng đổi mù** — xem `camera-lag-analysis-2026-09-26.md` L2: đổi sang `SurfaceView` là **mất bo góc và mất R7 xoay** |
| K8 | **Giới hạn nhịp vẽ** | đo fps + `busySkip`/`throttleSkip` | không có; `onSurfaceTextureUpdated` để trống (`CameraOverlayView.kt:293`) nên **không** có chỗ bỏ khung | 🟡 đòn rẻ nhất chưa dùng (nhưng phải đo trước, L3) |
| K9 | **Nắn méo fisheye** | có, shader equidistant→rectilinear 2 tham số + cường độ | **không có** — Kachi không có một dòng GL nào (`grep GLES20/EGL14/glCreateShader` trên `app/src core/src car-integration/src` = **0 tệp**) | 🔴 **đây chính là khoảng trống owner đang thấy**: ảnh gương bị cong |
| K10 | **Vùng crop gương** | dải đầy `0.25`, hoặc rect tuỳ ý + xoay ở shader E | `x[0.25..0.35]`, `x[0.65..0.75]` = **0.10 bề ngang** = chỉ **40 %** của một dải (`CameraSignalPolicy.kt:202-203`) | 🟡 **không** "giống Electro" như tưởng; Kachi hẹp hơn 2,5 lần. Đối xứng gương quanh 0.5 thì đúng |
| K11 | **Xoay** | thuộc tính từng ô, người dùng đặt, `radians(<ROT_DEG>)` trong shader; icon `ic_side_camera_rotate_left/right` (`public.xml:752-757`) | `rotationDegrees(Prefs.cameraRotation(...))` (`CameraSignalController.kt:157`) → ma trận `setTransform` | 🟢 cùng triết lý (người dùng đặt, không hardcode) |
| K12 | **Méo khi xoay 90/270** | shader xoay quanh tâm rect, không co giãn | Kachi co giãn **không đẳng hướng** để lấp khung vuông; kinex thì **thu hẹp dải y** cho khớp tỉ lệ rồi mới xoay (`Y0/C0094o.java:333-338`, khung ra 640×480) | 🟡 kinex làm đúng hơn cả hai — đã ghi ở `camera-lag-analysis-2026-09-26.md` |
| K13 | **`uTexMatrix` cho pano** | hai shader biết-pano chặn phép nhân vào nhánh không-pano | Kachi **không thể** "copy" điều này: không có GL, và ma trận `SurfaceTexture` được ghép **bên dưới** `Matrix` của nó trong framework (KDoc `CameraOverlayView.kt:222-223` dẫn `TextureView.java:493`, `:522`) | ⚪ **không áp dụng được** cho đường hiện tại; chỉ thành vấn đề nếu chọn §6-B |
| K14 | **Cấu hình theo camera** | SQLite + JSON, sửa được từ xa, mỗi ô có {bật fisheye, a, b, lật H, lật V, xoay} | hằng trong `enum CamView` + vài `Prefs` | 🟡 nếu làm §6-B thì nên theo Electro: **đưa tham số ra Cài đặt**, đừng nướng vào code |
| K15 | **Tải nền** | luồng riêng, không HAL poll | đã cắt ở 2.66 (CLOSE-1): CPU launcher 1,99 → 0,82 % trên máy ảo | 🟢 đã xử lý phần dễ gây giật nhất khi xe chạy |
| K16 | **`targetSdk`** | 25 (manifest: `versionCode 190`, `versionName 1.13.0`, min=target=25) | 37 (`app/build.gradle.kts:38,59,60`) | 🟡 Electro **không** chứng minh được gì cho Kachi về hidden-API — xem §7 |

**Tóm một câu**: Kachi đang đúng ở tầng mở camera (K3, K11, K15), lệch ở tài liệu (K1), và **thiếu hẳn tầng xử lý ảnh** (K9, K10) — đó là lý do ảnh gương vừa cong vừa hẹp.

---

## 6. Ba phương án cho Kachi 2.74

Cả ba **không loại trừ nhau**: A là mạng an toàn, C là đường rẻ nhất *nếu* HAL cho, B là đường chắc chắn làm được nhưng tốn công nhất. Theo CLAUDE.md §6: **đường mới xuống cuối, và phải tự đo xem đường cũ có thật hụt không rồi mới leo.**

### 6.1 Phương án A — nới/đổi crop chữ nhật cho đúng vùng gương

**Làm gì**: giữ nguyên toàn bộ đường vẽ hiện tại (`TextureView` + `setTransform`), chỉ đổi **số crop** trong `CamView` và (nếu cần) đưa nó ra Cài đặt như Electro làm với slot.

**Căn cứ**: Kachi đang lấy `0.10` bề ngang, **40 %** của một dải; một dải đầy là `0.25` (`CameraSignalPolicy.kt:202-203` vs shader @0x493b5) [ĐO]. Vùng méo nặng nhất của fisheye là **biên**, nên Electro có sẵn một bố cục co source rect vào `0.03..0.97` chỉ để **giấu viền** (`p056o/C0851z.java:27`) [ĐO] — tức "crop hẹp vào giữa dải" là cách chống méo **không cần shader**, và Kachi đã đi đúng hướng đó, chỉ là chưa ai tinh chỉnh bằng mắt.

**Công**: ≈ **nửa ngày** off-car (đổi hằng + test `CameraOverlayTransform` đã có sẵn + thêm 2 `Prefs` nếu muốn tinh chỉnh trên xe) — không đụng kiến trúc, không đụng `AvmCamera.kt`.
**Rủi ro**: **thấp**. Không có đường phục hồi nào phải viết; sai thì đổi số lại. Rủi ro duy nhất: crop rộng hơn ⇒ thấy nhiều méo hơn; crop hẹp hơn ⇒ mất tầm nhìn.
**🚗 Cần đo**: một khung 5120×960 chụp từ xe (chụp qua `ClusterDiag` theo §11, đừng bắt owner gõ adb) rồi **xem mắt** để chọn dải và biên; kèm cảm nhận trên đoạn đường thật.

### 6.2 Phương án B — nắn fisheye bằng shader, copy **toán** của Electro

**Làm gì**: chèn một lượt GL giữa HAL và view:
```
AVMCamera id 1 ──► SurfaceTexture #1 (OES, của Kachi) ──► shader (crop dải + xoay + nắn + lật)
                                                            └─► EGLWindowSurface trên Surface(SurfaceTexture #2 của TextureView) ──► HWUI (bo góc vẫn ăn)
```
**Vì sao vẫn giữ `TextureView`** (chứ không `GLSurfaceView`): `TextureView` chỉ cho **một** producer — AOSP `TextureView.java:100` "only one producer can use the TextureView", `:669` "already connected to an image producer (for instance: the camera…)", `getSurfaceTexture()` `:726` [ĐO]. Hôm nay producer là HAL camera; đổi producer thành luồng GL của Kachi là **thay một producer bằng producer khác**, nên **giữ được `clipToOutline`/bo góc và giữ được R7 xoay** — đúng hai thứ mà `camera-lag-analysis-2026-09-26.md` L2 cảnh báo sẽ mất nếu chuyển sang `SurfaceView`. **[SUY]** — cơ chế đọc từ doc-comment AOSP, **chưa** dựng thử trong Kachi; phải làm một bản nhỏ trên máy ảo trước.

**Copy gì từ Electro**: **cấu trúc, không copy số.**
- Toán: `theta = atan(pLen, F); r = theta * K; projected = 0.5 + radialDir*r*0.5*SCALE; out = mix(local, projected, amount)` (§3.2, §3.3) [ĐO].
- Đưa ra UI đúng bộ tham số của Electro: `{bật fisheye, F, K, SCALE, cường độ, lật H, lật V, xoay}` — trong đó chip **FOV** tương ứng `F = 1/tan(θmax)` và chip **tâm** thì Kachi nên làm **tốt hơn** Electro: Electro ghim tâm ở `(0.5,0.5)` của rect (@0x4735b, @0x48ff9) [ĐO], còn kinex tham số hoá tâm theo từng dải (`(0.375,0.5)` / `(0.625,0.5)`, `Y0/C0094o.java:76,79`) [ĐO] — **lấy kiểu kinex**, vì crop của Kachi là 0.10 nên tâm dải KHÔNG nằm giữa crop.
- Ba chi tiết phải sửa khi port: (1) **bổ sung lật trong nhánh pano** — Electro bỏ qua lật ở nhánh này (§3.1) mà Kachi cần soi gương; (2) **hiệu chỉnh tỉ lệ khung** — Electro không có, dải 4:3 sẽ cho đường đồng-θ hình ellipse (§3.3); (3) **giới hạn nhịp vẽ** kiểu `busySkip` (§4.3), vì có shader rồi thì bỏ khung là gần như miễn phí.
- **Đừng** giả định ống kính là equidistant như Electro — để `F`/`K` chỉnh được trên xe rồi chốt bằng mắt (§3.3).

**Công**: ≈ **2–3 ngày**: ~1 ngày EGL thread + program + test off-device cho phần thuần (đặt toán ở `:core` cạnh `CameraOverlayTransform`, có test như luật §10), ~0,5 ngày UI chip tham số, ~0,5 ngày ghép vào `CameraOverlayView` sau một **cờ Cài đặt** (mặc định TẮT, đường cũ nguyên vẹn — CLAUDE.md §6), ~0,5 ngày đệm.
**Rủi ro**: **trung bình**. (a) Thêm một lượt GPU mỗi khung, mà `camera-lag-analysis-2026-09-26.md` đang điều tra **đúng chuyện giật** — có thể chữa méo mà làm giật thêm; (b) `GL_MAX_TEXTURE_SIZE` cho texture rộng 5120: Electro **không hề kiểm** (§4.2) nên không có bằng chứng nào, Kachi phải tự log; (c) EGL + vòng đời overlay `TYPE_APPLICATION_OVERLAY` là chỗ dễ rò context.
**🚗 Cần đo**: `glGetIntegerv(GL_MAX_TEXTURE_SIZE)` một dòng log · `dumpsys gfxinfo com.byd.launcher framestats` và `dumpsys SurfaceFlinger --latency` **trước/sau** khi bật cờ · một khung 5120×960 để chỉnh `F`/`K`/tâm bằng mắt.

### 6.3 Phương án C — **dùng nguyên vòng** (không tự dựng từng mảnh)

Hai cách đọc chữ "nguyên vòng", và **cả hai đều đáng cân**: **C1** = dùng nguyên vòng xử lý **của HAL BYD** (đừng tự cắt/tự nắn — bảo HAL trả sẵn thứ mình cần); **C2** = bê nguyên vòng **của Electro** (hub → FBO → nhiều bên tiêu thụ, bố cục nằm trong DB). C1 rẻ nhất trong cả ba phương án, C2 đắt nhất.

#### C1 — để HAL làm nguyên vòng

**Làm gì**: trước khi viết bất kỳ shader nào, **thử ba thứ đã có sẵn trong SDK BYD**:
1. `addPreviewSurface(surface, VIEW_CHANNEL_n)` với `n = 1..4` (`IDiLinkAVMCamera.java:12` + `DiLinkCameraConstants.java:47-50`) — nếu HAL trả **một kênh camera** thay vì khung 4-in-1 thì **không cần crop, không cần chia dải**, và ảnh có thể đã được HAL nắn sẵn. Kachi hiện chỉ dò `0..3` và dừng ở cái đầu nhận được (`AvmCamera.kt:63-70`) ⇒ **gần như chắc đang dùng `VIEW_DEFAULT = 0`** [SUY].
2. `setPreviewSurface(surface, int, int, int, int, int)` (`:64`) — 5 int, ứng viên **rect đích/crop ở tầng HAL**. Nếu đúng thì crop là **miễn phí** (không lượt GPU nào của Kachi).
3. `setAlgMode(int)` (`:42`) — [CHƯA BIẾT] nghĩa; đáng thử vì tên gợi "chế độ thuật toán" của AVM (có thể chính là bật/tắt nắn méo trong HAL).
Cộng thêm `setPreviewSize(w,h)` (`:60`) để hạ tải (K5), và `rmPreviewSurface` (`:38`) để dỡ cho sạch (K6).

**Công**: ≈ **2–3 giờ** viết một **màn dò trong `ClusterDiag`** (theo §11: app tự chụp, không bắt owner gõ adb) thử lần lượt các tổ hợp trên và ghi kết quả ra tệp; + ≈ nửa ngày nối dây nếu có cái nào chạy.
**Rủi ro**: **thấp về code, cao về kết quả** — hoàn toàn có thể **cả ba đều không có tác dụng** (tiền lệ: CHANGELOG của DashCast ghi ROM DL5 cắt bỏ `cmd activity set-task-windowing-mode`, và `cmd activity task resize` trả exit 0 mà **không có tác dụng gì** — CLAUDE.md §12). Rủi ro thật cần canh: `setPreviewSurface`/`setAlgMode` là **lệnh đổi state của hệ thống camera xe** ⇒ bắt buộc trả lời 4 câu của CLAUDE.md §4 (đúng camera nào? đúng surface nào? hoàn tác kiểu gì? có chắc không đụng AVM khi xe vào R?) và phải có đường trả lại; thêm nữa Electro *có* cơ chế nhường khi hệ thống camera xe đang chạy (§2.3) mà ta chưa biết trigger.
**🚗 Cần đo**: toàn bộ phương án này **là** một phép đo. Thứ tự đúng theo CLAUDE.md §14: tầng 1 = shell/`ClusterDiag` thô trên xe thật → tầng 2 `car-integration` → tầng 3 `core` → tầng 4 UI.

#### C2 — bê nguyên vòng của Electro

**Làm gì**: dựng lại đúng kiến trúc §1.5 + §4.2 trong Kachi: **một hub** giữ khung mới nhất theo từng camera trên luồng riêng (`C0807c`), bên tiêu thụ khai báo cần camera nào (`InterfaceC0811g.java:13`), trộn vào **FBO** rồi phát ra N đích (overlay, cụm, ghi), **bố cục nằm trong dữ liệu** chứ không trong code — Electro để `camera_layouts` / `camera_layout_slots` / `camera_layout_feature_selection` trong SQLite (@0x4a8c5, @0x4a834, @0x4a7d0) với 8 bố cục dựng sẵn và mỗi ô có `{bật fisheye, a, b, lật H, lật V, xoay}` [ĐO].

**Khi nào đáng làm**: chỉ khi Kachi thật sự cần **≥ 2 đích cùng lúc** (ví dụ overlay trên màn **và** chiếu lên cụm, hoặc thêm ghi/chụp), vì đó mới là thứ hub + FBO giải quyết mà §6-B không giải quyết. Cho **một** khung gương thì đây là bắn đại bác vào ruồi.

**Công**: ≈ **1–2 tuần**, và bắt buộc phải có spec riêng theo CLAUDE.md §1 (đây là thay đổi kiến trúc, không phải vá).
**Rủi ro**: **cao** — nó gộp toàn bộ rủi ro của B (EGL, thêm lượt GPU, `GL_MAX_TEXTURE_SIZE`) cộng thêm vòng đời hub dùng chung, cộng một tầng dữ liệu mới, cộng nguy cơ trôi khỏi scope đã thống nhất (CLAUDE.md §4). Và phần lớn cái vòng đó Electro dựng để phục vụ **stream WebRTC ra điện thoại** — nhu cầu Kachi **chưa** có.
**🚗 Cần đo**: đúng những gì §6-B cần, cộng số framestats cho **hai** đích cùng bật.

⚠ Một điều **không** được bê theo: Electro chèn literal số vào nguồn GLSL nên **đổi tham số là biên dịch lại program** (§3.5). Kachi nên dùng **uniform** cho các số đó ngay từ đầu — vẫn giữ được chip tinh chỉnh trên xe mà không phải recompile mỗi lần kéo thanh trượt.

### 6.4 Khuyến nghị thứ tự

**C1 (đo, 2–3 giờ) → A (nửa ngày, ship được ngay) → B (2–3 ngày, sau một cờ Cài đặt) → C2 chỉ khi có nhu cầu nhiều đích**, vì: C1 có thể làm A và B thành không cần thiết và nó rẻ nhất; A cho owner thứ dùng được trong 2.74 kể cả khi C1 trượt; B chỉ nên leo khi đã **tự đo** rằng A thật sự hụt (§6 luật "tự đo rồi mới leo"), và vì nó đụng đúng vùng đang điều tra giật thì phải có số framestats trước/sau; C2 là quyết định kiến trúc của owner, cần spec riêng — **không** tự leo vào. **Việc bắt buộc, không phụ thuộc phương án nào và làm được ngay off-car**: sửa KDoc K1 + thêm phép thử `vehicle.config.cam_sort` (K2) + gọi `rmPreviewSurface` khi đóng (K6).

---

## 7. Câu hỏi mở

Mỗi câu kèm **đúng một** phép đo để chốt. 🚗 = cần xe.

| # | Câu hỏi | Mức hiện tại | Chốt bằng |
|---|---|---|---|
| Q1 | Dải 0 và dải 3 là **trước** hay **sau** (cái nào là cái nào)? | [CHƯA BIẾT] — Electro không gán nhãn ở đâu cả; kinex chỉ dùng dải 1 & 2 | 🚗 chụp **một** khung 5120×960 của id 1 rồi cắt 4 dải, **xem mắt**. Đây cũng chốt luôn Q2 |
| Q2 | "Dải 1 = trái, dải 2 = phải" — đúng không? | [SUY] từ hằng kinex đối xứng gương (`Y0/C0094o.java:70,73`); theo §14 dữ liệu app khác **tối đa** là "nghi là" | 🚗 cùng phép đo Q1 |
| Q3 | `VIEW_CHANNEL_1..4` có thật sự cho **một kênh** camera? | [SUY] từ hằng SDK (`DiLinkCameraConstants.java:47-50`); không có chỗ gọi nào trong firmware đã giải nén | 🚗 §6-C bước 1: dò `addPreviewSurface(surface, 1..4)` trong `ClusterDiag`, xem có ra hình khác không |
| Q4 | 5 int của `setPreviewSurface(Surface,int,int,int,int,int)` là gì? | [CHƯA BIẾT] | 🚗 §6-C bước 2 |
| Q5 | `setAlgMode(int)` làm gì? | [CHƯA BIẾT] | 🚗 §6-C bước 3, quét vài giá trị nhỏ và ghi lại ảnh |
| Q6 | `F`, `K`, `SCALE`, `AMOUNT` của Electro bằng bao nhiêu? | [CHƯA BIẾT] — 7 chỗ chèn nằm trong bytecode VM, không đọc tĩnh được | dump nguyên văn shader: hook `glShaderSource` (Frida) hoặc `glGetShaderSource` khi Electro đang chạy 🚗. **Hoặc bỏ qua**: Kachi tự chỉnh bằng mắt thì rẻ hơn (§6-B) |
| Q7 | `<RECT>` trong thân shader E là `uSourceRect` hay vec4 nướng cứng? | [SUY] là rect nội bộ (nếu không thì tâm fisheye rơi vào ranh giới dải 2/3 — vô nghĩa) | cùng dump ở Q6 |
| Q8 | Đường source-rect (shader E) có thật đang chạy ở 1.13.0? | [SUY] **có thể không** — `uSourceRect` không có chuỗi tra cứu đứng riêng (§3.4) | cùng dump ở Q6, hoặc cài Electro rồi đọc SQLite `camera_layout_slots` (`run-as br.com.rory.electro`) |
| Q9 | Tên cột fisheye/flip thật trong DB của Electro? | [SUY] từ camelCase; grep byte trên 6 `.so` cho **0 hit** `fisheye_`/`flip_`/`extra_cameras` | cài APK vào máy ảo, chạy tới migration v20 rồi `sqlite3 <db> ".schema"` — **không cần xe** |
| Q10 | 4 khoá `feature` trong `p056o/C0837l.java:112,118,124,130` (`m5001O/m5003P/m5005Q/m5007R`) ứng với bề mặt nào (stream / ghi / overlay / sentry)? | [CHƯA BIẾT] — getter bị gói | cùng phép đọc DB ở Q9 (`camera_layout_feature_selection.feature`) |
| Q11 | `p049e/p050k/p051l/C0769f.java:51` `private volatile int f1963e = 420` là đơn vị gì (kbps? chiều cao? fps quantum?) | [CHƯA BIẾT] | **đừng copy số này vào Kachi** dù bất cứ lý do gì |
| Q12 | Lưới 2×2 vẽ trong **một** lệnh vẽ hay bốn? | [SUY] một (trọng số theo góc phần tư vô nghĩa nếu bốn) | 🚗 GL trace / hook `glDrawArrays` khi Electro đang chiếu |
| Q13 | Texture rộng 5120 có vượt `GL_MAX_TEXTURE_SIZE` của GPU đầu xe? | [CHƯA BIẾT] — Electro không kiểm bao giờ (§4.2) | 🚗 một dòng `glGetIntegerv(GL_MAX_TEXTURE_SIZE)` từ Kachi. **Bắt buộc trước §6-B** |
| Q14 | Kachi đặt `outputState` (`BYDAutoPanoramaDevice`) có **cần** cho đường pano không? | [SUY] có thể **không** — Electro không dùng HAL panorama, và chuỗi `selectedCameraId=1 renders-full-frame` @0x47d24 nói camera đó vẽ trọn khung | 🚗 mở id 1 **không** đặt `outputState`, xem có ra khung 4-in-1 không |
| Q15 | Cơ chế nào làm Electro **nhường** khi hệ thống camera xe bật (vào R chẳng hạn)? | [CHƯA BIẾT] — chỉ có chuỗi UI `strings.xml:74` + `ClusterProjectionPauseActivity` | 🚗 `logcat` khi vào R lúc Electro đang chiếu. Đúng loại regression CLAUDE.md §4 cảnh báo ⇒ phải biết trước khi Kachi mở AVM lâu |
| Q16 | `targetSdk 37` của Kachi có bị hidden-API chặn khi `Class.forName("android.hardware.AVMCamera")`? | [CHƯA BIẾT] — Electro ở `targetSdk 25` nên **không chứng minh được gì**; phụ thuộc `bmmcamera.jar` có trong `BOOTCLASSPATH` hay không | 🚗 `adb shell echo $BOOTCLASSPATH` — một lệnh, không cần sửa app |
| Q17 | Ma trận biến đổi thật của `SurfaceTexture` với id 1 trên xe này là gì? | [CHƯA BIẾT] | 🚗 log `getTransformMatrix` từ Kachi. Cần trước khi §6-B quyết áp hay bỏ ma trận |
| Q18 | Trên xe có id nào khác `0`/`1` không (xe khác đời, `cam_sort` khác)? | [ĐO] cho xe này (`rear:0;pano_h:1`) · [CHƯA BIẾT] cho đời khác | 🚗 `getprop vehicle.config.cam_sort` trên từng xe; nếu khác đời thì khác biệt phải vào `ClusterProfile`, **không** rải trong code (CLAUDE.md §7) |

---

### Việc phải làm ngay trong cùng phiên (CLAUDE.md §16 / R2.1)

- [ ] Thêm dòng cho tài liệu này vào `docs/README.md` (INDEX canonical) — doc mồ côi = **không tồn tại**.
- [ ] Thêm hạng mục vào `docs/PROJECT-BACKLOG.md`: sửa KDoc K1 · phép thử `cam_sort` K2 · `rmPreviewSurface` K6 · màn dò §6-C trong `ClusterDiag` · 3 phương án 2.74.
- [ ] Cập nhật `.kiro/steering/project-context.md` (bản đồ nguồn camera: `pano_h` = id 1, `rear` = id 0 trên xe hiện tại).
- [ ] Sửa KDoc `core/.../camera/CameraSignalPolicy.kt:187` (đang ghi ngược id 0/id 1) — đây là sửa **doc trong code**, không đổi hành vi.
