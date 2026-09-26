# `camera_frame` · chụp khung camera GỐC ra PNG (cầu kiểm thử) — off-car 2026-09-26

> **Trạng thái**: Current · **Cập nhật**: 2026-09-26 (off-car, chưa lên xe) · **Mục đích**: có một phép đo trả lời
> được *"vòng ảnh fisheye TRÒN (còn viền đen quanh) hay đã KÍN khung?"* bằng **một tệp PNG ở cỡ luồng gốc**, thay vì
> đoán từ ảnh chụp màn.
> Liên quan: `docs/specs/camera-turn-signal-hal-socket.html` (R7), `diagnostics/offcar-2026-09-26/camera-aspect-and-render-path.md`
> (CAM-ROT-2 · CLOSE-14), `diagnostics/oncar-runbook-2.74.md`, backlog **CAM-ROT-2**.

---

## 0. Vì sao ảnh chụp màn KHÔNG trả lời được câu hỏi này

Overlay camera là một cửa sổ nhỏ đã đi qua **ma trận crop + xoay** (`CameraOverlayTransform`) — tức đúng cái đang bị
nghi là sai. Một ảnh chụp màn chỉ cho thấy **kết quả sau** ma trận đó, nên mọi kết luận rút từ nó đều là **[SUY]**,
không phải **[ĐO]** (CLAUDE.md §2).

`camera_frame` đọc **layer** của `TextureView`, nơi ma trận ấy **chưa** được áp ⇒ tệp PNG ra là **khung nguồn**, so
được trực tiếp với giả thuyết *"ảnh 4-in-1 có vòng fisheye tròn"*.

---

## 1. Cơ chế — vì sao `getBitmap` KHÔNG mang theo crop + xoay

Đây là **[ĐO]** đọc source AOSP `android-10.0.0_r47`, không phải trí nhớ (CLAUDE.md §3). Trích dẫn nằm nguyên trong
KDoc `CameraOverlayView.captureFrame` (bài canh `KDoc captureFrame con giu trich dan AOSP…` giữ nó khỏi rữa):

| Bước | Tệp AOSP `android-10.0.0_r47` | Điều đọc được |
|---|---|---|
| 1 | `frameworks/base/core/java/android/view/TextureView.java:574-581` | `getBitmap(w,h)` dựng bitmap `ARGB_8888` đúng `w × h`; `w`/`h` ≤ 0 hoặc `!isAvailable()` ⇒ trả `null` |
| 2 | `TextureView.java:605-627` | `getBitmap(Bitmap)` gọi `mLayer.copyInto(bitmap)` |
| 3 | `frameworks/base/libs/hwui/Readback.cpp:86-105` | `dstRect` = **toàn bộ** bitmap (`:95`), `srcRect = nullptr` (`:99`) |
| 4 | `Readback.cpp:159` + `:184-186` | gọi `LayerDrawable::DrawLayer(…, srcRect, dstRect, false)` — **`useLayerTransform = false`** |
| 5 | `frameworks/base/libs/hwui/pipeline/skia/LayerDrawable.cpp:53-85` | `useLayerTransform` `false` ⇒ `matrix = textureMatrix` **thay vì** `Concat(layerTransform, textureMatrix)`; `layerTransform` chính là thứ `TextureView.setTransform` ghi vào layer (`TextureView.java:493` + `:522`) |
| 5b | `LayerDrawable.cpp:104-117` | `srcRect` rỗng ⇒ `MakeIWH(layerWidth, layerHeight)` rồi `matrixInv.mapRect` ⇒ vùng nguồn = **trọn ảnh gốc**, căng vào `dstRect` |

⇒ Xin đúng cỡ luồng thật thì ảnh ra là khung gốc 1:1; xin cỡ khác thì **vẫn là khung gốc**, chỉ bị co/giãn **đều**
(không bị cắt, không bị xoay).

**[SUY]** bước JNI `TextureLayer.copyInto` → `RenderProxy::copyLayerInto` chưa mở source ra đọc; chỉ có một chỗ nhận
`DeferredLayerUpdater*` trong `Readback` nên đường đi là duy nhất.

---

## 2. Chuỗi lệnh adb — nguyên văn

⚠ Cờ của **cả hai** lệnh camera là `--es name`, **KHÔNG** phải `--es arg` (`TestBridgeCommands.EXTRA_ARG == "name"`).
Gõ `--es arg left` thì `camera` nhận chuỗi rỗng ⇒ tắt overlay, rồi `camera_frame` trả `overlay_not_showing` — một
câu trả lời sai trông y như *"tính năng chưa chạy"*.

```bash
PKG=com.byd.launcher
COMP=$PKG/com.byd.clusternav.launcher.testbridge.KachiTestBridge
ACT=com.byd.launcher.TEST

# 0. Bật công tắc "Chế độ kiểm thử qua adb" trong Cài đặt (cầu KHÔNG có lệnh tự bật — §6 rule global).
#    Kiểm cầu sống: data="{...}" phải có trong kết quả.
adb shell am broadcast -n $COMP -a $ACT --es cmd state

# 1. Bật tính năng camera-theo-xi-nhan (mặc định TẮT) + chọn đường kết xuất CHỤP ĐƯỢC (TextureView = "TV").
adb shell am broadcast -n $COMP -a $ACT --es cmd prefs_set --es key camera_signal_enabled --es text true
adb shell am broadcast -n $COMP -a $ACT --es cmd prefs_set --es key camera_render       --es text TV

# 2. Ép MỘT nhịp xi-nhan giả ⇒ overlay dựng lên (left | right | none).
adb shell am broadcast -n $COMP -a $ACT --es cmd camera --es name left

# 3. Chụp. Vắng `--es name` ⇒ cỡ MẶC ĐỊNH (cỡ ảnh fisheye 4-in-1).
adb shell am broadcast -n $COMP -a $ACT --es cmd camera_frame
#    hoặc xin cỡ khác:
adb shell am broadcast -n $COMP -a $ACT --es cmd camera_frame --es name 4096x768

# 4. Kéo về. Ảnh nằm trong kachi-logs/ (không cần run-as, không cần quyền bộ nhớ).
adb shell ls -t /sdcard/Android/data/$PKG/files/kachi-logs/ | grep camera-frame- | head -1
adb pull /sdcard/Android/data/$PKG/files/kachi-logs/ ./kachi-logs/
#    JSON lời đáp (đầy đủ hơn resultData) nằm ở files/test/:
adb shell ls -t /sdcard/Android/data/$PKG/files/test/ | head -1
```

Trong bộ script trên xe thì gọn hơn — `k_test` (`scripts/vehicle/kachi/_common.sh`) đã lo `-n`, `-a` và kéo JSON:

```bash
k_test camera      --es name left
k_test camera_frame
k_test camera_frame --es name 2560x480
```

---

## 3. Lời đáp đọc gì

| Trường | Nghĩa |
|---|---|
| `path` · `bytes` | tệp PNG trong `kachi-logs/`, tên `camera-frame-<yyyyMMdd-HHmmss>.png` |
| `width` × `height` | cỡ **thật** của bitmap đã ghi (`asked` = cỡ đã xin, khác nhau khi bị kẹp trần) |
| `render` | đường kết xuất **đang treo** (`TV`/`SV`) — ĐO từ tầng vẽ, không tra pref |
| `available` | `TextureView.isAvailable` (đã có `SurfaceTexture` chưa) |
| `capturable` | lớp video có phải `TextureView` không (xem §5) |
| `view` · `cam_id` · `crop` · `rotation_deg` | ngữ cảnh của **đúng** khung vừa chụp (ghi lúc dựng overlay, không tra lại prefs) |

Ba mã lỗi:

- `overlay_not_showing` — chưa có overlay nào treo (chưa bật pref, hoặc chưa ép nhịp xi-nhan ở bước 2).
- `capture_failed` — có overlay nhưng không ra ảnh; `reason` nói rõ **hai lý do khác nhau** (§4 và §5).
- `write_failed` — nén/ghi tệp hỏng (đĩa đầy, thẻ chưa gắn).

---

## 4. ⚠ Caveat: PNG ĐEN hoặc `capture_failed` vì trần texture của GPU

Cỡ mặc định là cỡ ảnh fisheye 4-in-1, và **bề rộng của nó vượt `GL_MAX_TEXTURE_SIZE` của không ít GPU đầu máy**
(4096 là mốc phổ biến). Khi đó `Readback` không đọc ra được gì mà cũng **không ném**: kết quả là một tệp PNG **đen
hoàn toàn**, hoặc `capture_failed`. Đen ≠ "camera không có hình" — đừng kết luận sai bệnh.

**Cách chốt:** xin cỡ nhỏ hơn, **giữ nguyên tỉ lệ** (ảnh ra vẫn là khung gốc, chỉ co đều — §1):

```bash
k_test camera_frame --es name 4096x768    # nửa cỡ theo cả hai cạnh, dưới mốc 4096
k_test camera_frame --es name 2560x480    # nửa nữa — nếu 4096 vẫn đen
```

Ảnh có hình ở `2560x480` mà đen ở cỡ mặc định ⇒ **[ĐO]** trần texture, không phải lỗi camera. Ghi mốc đo được vào
`docs/diagnostics/` để lần sau khỏi đo lại. Cỡ nào cũng trả lời được câu hỏi ban đầu (vòng fisheye tròn hay kín
khung) vì phép co là **đẳng hướng**.

---

## 5. Đường kết xuất `SurfaceView` **không bao giờ** chụp được

Chip *Kết xuất* trong Cài đặt có hai đường (CLOSE-14): `TextureView` (`TV`, mặc định) và `SurfaceView` (`SV`).
`SurfaceView` ghép layer **ngoài** cây view, do SurfaceFlinger — `TextureView.getBitmap` **không tồn tại** ở đó và
không có hàm nào trong tiến trình app thay thế.

⇒ Lệnh trả `capture_failed` với `capturable=false` và `reason` nói thẳng cách thoát (đổi `camera_render` về `TV` rồi
ép lại nhịp xi-nhan). Chờ thêm / bật lại xi-nhan / xin cỡ khác đều **vô ích** ở đường này — nói ra là để không ai
phải mò (CLAUDE.md §15).

`capturable` được **ĐO** (`video is TextureView`) chứ không tra pref: pref nói *sẽ* dựng đường nào, còn thứ đang treo
là cái đã dựng ở lượt xi-nhan trước — hai thứ khác nhau đúng trong khoảng owner vừa đổi chip, và đúng khoảng đó là
lúc lệnh chẩn đoán bị gọi.

---

## 6. Cỡ ảnh: ba luật, phân tích ở `:core` (thuần, có bài canh)

`TestBridgeFrameSize.parse` — off-device test được, vì đây là chỗ **sai được mà không ai thấy**:

1. **Không đọc được ⇒ MẶC ĐỊNH.** `""`, `"abc"`, `"1280"`, `"x720"`, `"0x0"`, `"-1x-1"`, `"1280x720x3"` đều cho cỡ
   mặc định. KHÔNG ném, KHÔNG trả `0×0` — người gõ sai vẫn cần thấy cái khung.
2. **Trần MỖI CẠNH 8192 px** — xin hơn thì `Bitmap.createBitmap` ném `OutOfMemoryError` mà `Readback` có thể đã kịp
   giữ một GPU surface.
3. **Trần DIỆN TÍCH 20 M px** (≈ 80 MB ở `ARGB_8888`) — co **đều** hai cạnh theo `sqrt` để **giữ tỉ lệ** ảnh. Cắt một
   cạnh sẽ làm méo đúng thứ lệnh này sinh ra để đo.

Trần đặt ở tầng **phân tích**, không ở tầng thi hành: một cỡ không dùng được không bao giờ được dựng thành một lệnh
"chạy được" rồi mới hỏng ở giữa. Chữ `X` HOA và khoảng trắng thừa đều nhận (`" 1280X720 "`).

---

## 7. Ba luồng (một lượt chụp KHÔNG được giật hình xe đang chạy)

| Luồng | Việc | Vì sao phải ở đó |
|---|---|---|
| **main** | `TextureView.getBitmap` | op cây view + đồng bộ với render thread (KDoc AOSP) |
| **nền** (`kachi-camframe`, daemon) | nén PNG + ghi tệp | một khung cỡ luồng gốc nén mất hàng trăm ms ⇒ trên main là giật hình, và `ANR` nếu đĩa chậm |
| **nền** | chốt `reply` | `path`/`bytes` chỉ có thật sau khi ghi; `TestBridgeReply` tự chống chốt-hai-lần nên đường hết-giờ (`CAP_MS` = 20 s) chạy song song vẫn an toàn |

`bitmap.recycle()` trong `finally` — kể cả nhánh hỏng. Một khung cỡ luồng gốc là hàng chục MB; bỏ sót đúng ở nhánh
lỗi là cách một lệnh **chẩn đoán** làm hết bộ nhớ của launcher sau vài lượt gọi.

Lệnh **chỉ ĐỌC**: không mở/đóng camera, không dựng lại overlay, không chạm prefs, không đổi một dòng nào của đường
khung hình. Gọi được giữa lúc đang lái mà không đổi hành vi.

---

## 8. Bài canh (off-car, đã xanh)

| Bài | Ở đâu | Khoá điều gì |
|---|---|---|
| `TestBridgeFrameSizeTest` (9 ca) | `:core` | lệnh có thật trong bảng · `name` tuỳ chọn · mặc định khi chuỗi lạ · hai trần · giữ tỉ lệ khi co · mọi kết quả đều hợp lệ |
| `camera_frame noi du bon mat day tu bang lenh xuong controller` | `:app` | **bốn** mắt dây: bảng lệnh `:core` → nhánh điều phối → móc `hooks.cameraFrame` → `AppContainer.cameraSignal.grabFrame` (CLAUDE.md §8 — `CastShell.evictVd` compile xanh mà chưa từng chạy) |
| `camera_frame chup tren main, ghi PNG vao kachi-logs, va nha bitmap` | `:app` | chụp trên main · nén **PNG** (JPEG thêm nhiễu vào đúng phép đo) · ghi vào `kachi-logs/` · `recycle` |
| `camera_frame noi ro khi duong ket xuat SurfaceView khong chup duoc` | `:app` | `reason` có mặt trong lời đáp · rẽ theo phép ĐO (`capturable`) không theo pref · mã kết xuất lấy từ hằng `:core` |
| `KDoc captureFrame con giu trich dan AOSP cho getBitmap khong mang transform` | `:app` | bốn trích dẫn AOSP (`TextureView.java` · `Readback.cpp` · `LayerDrawable.cpp` · `useLayerTransform`) còn nằm trong mã |

Tổng lượt chạy off-car: `:core` 110 ca / 0 lỗi (lọc `*TestBridge*` + `*Camera*`), `:app` 40 ca / 0 lỗi (cùng bộ lọc).

---

## 9. Còn cần XE (🚗)

- 🚗 **Trần texture thật của GPU đầu máy** — cỡ nào còn ra hình, cỡ nào ra PNG đen (§4). Off-car không đo được:
  emulator/máy ảo có trần khác.
- 🚗 **Vòng fisheye tròn hay kín khung** — chính câu hỏi lệnh này sinh ra để trả lời; cần video thật từ HAL panorama
  (off-car `PanoramaHal` no-op ⇒ `TextureView` đen, chụp ra khung đen hợp lệ).
- 🚗 **Cỡ luồng thật** — `AVMCamera.getPreviewWidth/Height`; ROM này **[CHƯA BIẾT]** có trả số hay trả 0.
- 🚗 **Đường `SurfaceView` có bo góc không** — không liên quan chụp khung, nhưng cùng buổi đo thì ghi luôn.
