# VietMap bong bóng → cụm qua SurfaceControl daemon (no-mod) — 2026-08-27

> **Trạng thái**: Diagnostics (bằng chứng off-car) · **Ngày**: 2026-08-27 · **Mục đích**: ghi lại cơ chế + bằng
> chứng đo được cho hướng "chiếu ĐÚNG bong bóng pixel VietMap lên cụm mà KHÔNG mod VietMap", và ẩn số còn lại (xe).

## Bối cảnh
Owner muốn hiện **đúng giao diện bong bóng VietMap** (mũi tên/cự ly/tốc độ thật) trên cụm, không phải dựng lại
bằng HAL. Hai đường đã loại: (a) **mod VietMap** dời overlay — fail trên xe (cụm siết); (b) **addView** của app
thường lên cụm — bị từ chối (`permission denied for window type 2038` / cụm OEM). Đường còn lại: **daemon uid
shell present qua SurfaceControl** (như navopen/kinex có quyền mà app thường không có).

## Cơ chế daemon (`tools/vietmap-cluster-mirror/`)
1. **Dò cụm**: `DisplayManagerGlobal.getDisplayIds()` + cờ `FLAG_PRESENTATION` (1<<6) → display id != 0. Không
   hardcode (nghi phạm khiến bản mod hụt cụm).
2. **Chụp**: `screencap -d 0 -p` (màn chính) → `BitmapFactory` → crop vùng bong bóng bằng `getPixels`/`setPixels`
   (KHÔNG Canvas — app_process trần chưa init Typeface, `lockCanvas`/`new Paint()` assert `gDefaultTypeface==nullptr`).
3. **Present**: `ImageWriter` → buffer vào **SurfaceControl layer** (buffer-backed) → `Transaction.setLayerStack`
   (layerStack của cụm) + `setLayer/setPosition/setVisibility/apply`.
4. **Bám vị trí động**: mỗi ~4 khung parse `dumpsys window` cửa sổ overlay VietMap → cập nhật crop; đổi size ⇒ dựng lại layer.

## Bằng chứng [ĐO] — emulator Android 10 (API 29, KHỚP xe), uid shell (2000)
| Việc | Kết quả đo | Cách đo |
|---|---|---|
| Quyền daemon | `SurfaceControl.createDisplay` OK ở **uid 2000** (app thường bị SecurityException) | probe `Main`, adb unroot |
| Present pixel-thật | display phụ vs vùng nguồn màn chính: **mean abs diff 0.0, max 0** (112 điểm) | screencap + PIL |
| Bong bóng VietMap thật | bong bóng [1030,127][1401,285] → mirror display 1: diff **0.0** (104 điểm) | screencap + PIL |
| Bám vị trí | kéo bong bóng → daemon `re-track pos→[460,393]` (khớp dumpsys) → diff **0.0** | input swipe + so pixel |
| Dò cụm | `auto cụm = display 1 (FLAG_PRESENTATION)` | log daemon |

## Phát hiện then chốt: BLAST vs BufferQueue
- **API 34 (emulator mới)**: layer buffer-backed = **BLAST** → đẩy buffer qua Surface/ImageWriter KHÔNG gắn
  (`buffer=0x0`); phải `Transaction.setBuffer(HardwareBuffer)` (API 31+).
- **API 29 (Android 10 = xe)**: layer dùng **BufferQueue cũ** → ImageWriter gắn buffer OK (`BufferLayer`,
  activeBuffer 700x320). ⇒ đường ImageWriter **đúng cho xe**; **phải test đúng API 29**, không phải API 34.
- Display phụ: API 29 = **display 1** (khớp cụm xe); API 34 = display 2.

## ẨN SỐ — chỉ xe chốt
Emulator display phụ = **virtual display AOSP dễ tính**; addView cũng chạy được ở đó (sau khi cấp
`SYSTEM_ALERT_WINDOW`). **Cụm xe = surface OEM `xdja` bị siết** — CHƯA biết có nhận layer SurfaceControl từ uid
shell không. Đây là điều **DUY NHẤT** phải kiểm trên xe: chạy `tools/vietmap-cluster-mirror/run-on-car.sh`, xem
`mir.log` + `dumpsys SurfaceFlinger | grep clusternav-mirror` + `dumpsys display` phần cụm.

## Giới hạn
Là **mirror pixel** ⇒ hiện đúng bong bóng trên cụm NHƯNG bong bóng **vẫn còn trên màn chính** (cả 2). Muốn
chỉ-cụm cần cách khác (mod dời cửa sổ / render VietMap trên virtual display) — ngoài phạm vi bản này.

## Liên quan
- Code + jar + script: `tools/vietmap-cluster-mirror/` (README có hướng dẫn chạy).
- ClusterNav prototype addView-mirror (chỉ chạy display dễ tính, off-car): `app/.../mirror/VietMapBubbleMirror.kt`
  + broadcast `MIRROR_START/STOP` trong `NavAccessibilityService.kt`.
