# Camera xi-nhan "hơi giật lag khi xe chạy" — phân tích đường khung hình + playbook đo trên xe · 2026-09-26

> **Trạng thái**: Current · **Cập nhật**: 2026-09-26 · **Mục đích**: trả lời owner *"phần xử lý cam đó đã tối ưu chưa, vì hiện tại nó hơi giật lag khi xe chạy"* — tách rõ [ĐO]/[SUY]/[CHƯA BIẾT], nêu đúng phép đo cần làm trên xe trước khi đổi gì. Liên quan: spec `specs/camera-turn-signal-hal-socket.html`, CAM-ROT (xoay video), CLOSE-1.

## 1. Đường khung hình hiện tại [ĐO source]

`AVMCamera.open(camId)` → `setCameraFps(15)` (`AvmCamera.kt:58,89` — kinex cũng 15) → `addPreviewSurface(Surface(SurfaceTexture), mode)` → HAL đổ frame vào **`SurfaceTexture` của một `TextureView`** (`CameraOverlayView.kt:103`) → HWUI vẽ texture đó vào cây view (crop/xoay bằng `setTransform(Matrix)`, 9 số dựng ở `:core` `CameraOverlayTransform`) → cửa sổ `TYPE_APPLICATION_OVERLAY`, `PixelFormat.TRANSLUCENT` (`CameraOverlayView.kt:228`), nền bo góc + `clipToOutline` (`:119-124`, `:183-190`) → SurfaceFlinger ghép.

Kinex (RE `jadx-kinex/sources/p005b1/RunnableC0170d.java:120-129`): cùng `AVMCamera`, cùng fps 15, nhưng surface là `new Surface(SurfaceTexture)` của **pipeline GLES riêng** (`C0173g`/`RunnableC0171e` shader) — cũng GPU, không phải `SurfaceView` thẳng HWC. Ba chi tiết đáng lấy [ĐO RE]:
- Kinex tự **giới hạn nhịp vẽ**: `RunnableC0171e.java:104` bỏ khung nếu chưa đủ 16 ms kể từ khung trước (≈ 60 fps trần) — nó không vẽ mọi khung HAL đẩy tới.
- Xoay là **hai số độc lập theo bên**, từ intent `blind_spot_left_rotation_deg` / `blind_spot_right_rotation_deg`, **mặc định 0**, kèm hai cờ `*_flip_h` (`KinexBottomBarOverlayService.java:645-648`; dùng ở `Y0/C0094o.java:308-315`). Tức trong RE **không có** luật "trái ngược phải" — xem §Reviewer Log của spec R7.
- Khi xoay 90/270 kinex **thu hẹp dải y của crop** cho khớp tỉ lệ khung ra rồi mới xoay (`C0094o.java:330-345`: `min(cropW·inputW·4/3 ÷ 960, cropH)`, khung ra 640×480 thay vì 512×960) ⇒ xoay **không làm méo**. Kachi thay vào đó co giãn không đẳng hướng cho lấp khung vuông (giữ nguyên lượng méo đã có từ 2.36).

## 2. Nguồn giật khả dĩ — xếp theo mức bằng chứng

| # | Nguồn | Mức | Vì sao | Cách chốt trên xe |
|---|---|---|---|---|
| L1 | **Tải CPU chung khi lái** (HAL poll 7 lượt/s ≈ 16 % một lõi, watchdog shell, 2 luồng socket camera, vòng 250 ms) — **đã cắt ở 2.66** (CLOSE-1) | [ĐO máy ảo] CPU launcher 1,99 → 0,82 %; xe [CHƯA ĐO] | Overlay vẽ trên luồng chính của launcher; luồng chính bận ⇒ khung rớt | So cảm nhận 2.65 vs 2.66 cùng đoạn đường; `adb shell dumpsys gfxinfo com.byd.launcher framestats` lúc camera đang hiện |
| L2 | **`TextureView`** ⇒ mỗi khung: HAL → GPU texture → **HWUI vẽ lại cây view** → SurfaceFlinger ghép lớp cửa sổ | [ĐO source] `TextureView` vẽ TRONG cây view (khác `SurfaceView` = layer riêng) ⇒ một lượt GPU thêm mỗi khung; **[CHƯA BIẾT]** lớp cửa sổ có được HWC ghép hay bị đẩy sang GPU composition — HWC đời này có blend lớp trong suốt hay không thì chỉ `dumpsys SurfaceFlinger` trên xe trả lời (đừng khẳng định như bản trước) | Hai lượt GPU/khung. `SurfaceView` rẻ hơn nhưng: bo góc lớp video là ⚠ [CHƯA BIẾT] đã ghi ở KDoc `CameraOverlayView` (chính là lý do đổi sang `TextureView` ở 2.3x), **và** từ R7 nó còn phải xoay được — `setTransform` chỉ có ở `TextureView` ⇒ đổi lại là mất luôn R7 | `adb shell dumpsys SurfaceFlinger --latency` cho layer overlay (jank khung) + `dumpsys SurfaceFlinger` xem dòng composition type của layer; chỉ sau đó mới bàn `PixelFormat.OPAQUE`/`SurfaceView` **sau một cờ Cài đặt** |
| L3 | **fps 15** cố định (`setCameraFps(15)`) | [ĐO code]; HAL nhận 30 không [CHƯA BIẾT] | 15 fps tự nó trông "giật" khi cảnh chuyển động nhanh (xe chạy) dù không rớt khung | `logcat -s KachiCamera` xem `setCameraFps` có bị từ chối; thử 30 qua reflection và đo `SurfaceFlinger --latency` |
| L4 | Kích thước texture nguồn 5120×960 fisheye trong khi chỉ hiện 10 % bề ngang (crop) | [ĐO] `CamView.MIRROR_*` crop x 0,10 | HWUI vẫn sample cả texture lớn mỗi khung (rẻ trên GPU nhưng băng thông bộ nhớ đầu xe hạn) | Không đổi được ở app (HAL quyết cỡ); chỉ đo |
| L5 | Xoay 90° mới (CAM-ROT) thêm một phép nhân ma trận — **không** thêm chép | [ĐO code] ma trận dựng MỘT lần mỗi lượt xi-nhan (`CameraOverlayTransform.matrix` gọi từ 2 callback SurfaceTexture, không phải mỗi khung); `setTransform` chỉ đổi vertex của lớp texture | Không đáng kể | — |

**Kết luận thẳng**: phần xử lý camera **chưa được tối ưu riêng** (chưa từng đo khung/giây trên xe); CLOSE-1 đã bỏ phần tải CPU nền quanh nó (L1) — đó là thứ dễ gây giật nhất lúc xe chạy vì overlay vẽ trên luồng chính. L2/L3 là hai đòn bẩy còn lại, **phải đo trước khi đổi** (đổi `SurfaceView` là mất bo góc **và mất R7 xoay**; đổi fps là thay đổi tải HAL). ⚠ Không có dòng nào ở đây được nâng lên mức "đã chứng minh" cho tới khi có output của playbook §3 — CLAUDE.md §2/§14.

## 3. Playbook một buổi xe (🚗, làm sau C1–C10)

```bash
# 1) đang xi-nhan, camera hiện ≥ 20 s
adb shell dumpsys SurfaceFlinger --list | grep -i "byd.launcher"          # tên layer overlay camera
adb shell dumpsys SurfaceFlinger --latency "<layer>" | head -130           # 128 khung: khoảng cách giữa các timestamp ⇒ fps thật + khung rớt
adb shell dumpsys gfxinfo com.byd.launcher framestats | head -60           # jank của cây view (TextureView ở luồng chính)
adb logcat -d -s KachiCamera | grep -i fps                                 # setCameraFps có bị từ chối?
adb shell top -H -n 3 -d 5 -p $(pidof com.byd.launcher) | head -20         # luồng nào ăn CPU lúc camera hiện
```
Tiêu chí: fps thật ≥ 14 và ≤ 2 khung rớt/10 s ⇒ giật là do 15 fps (L3) ⇒ thử 30. fps thật < 10 hoặc rớt nhiều ⇒ L2 ⇒ cân nhắc cửa sổ OPAQUE/SurfaceView **có cờ Cài đặt** (mất bo góc/xoay), không đổi mặc định.
