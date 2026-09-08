# DashCast cluster-projection — cách cast ANY app (YouTube/GMaps) lên cụm

> [!CAUTION]
> **ARCHIVED RESEARCH RECIPE — NOT A CURRENT IMPLEMENTATION OR SAFETY CONTRACT.** Commands and conclusions below are historical inputs only. Do not execute them on a vehicle or treat them as V2 evidence. The canonical current design is `docs/specs/cluster-cast-rebaseline.html`; build/install/car mutation remains separately authorized and exact-build gated.

> RE từ source DashCast (`jadx-dashcast` + `dashcast-src`) 2026-07-14. Trả lời: vì sao DashCast cast YouTube/Doraemon
> full mượt lên cluster mà ClusterNav lại **trắng / nhỏ / ADAS đen / kẹt khi tắt**.

## Điểm mấu chốt (1 câu)
DashCast **KHÔNG move-stack**. Nó **launch app MỚI vào VD ở FREEFORM (windowingMode 5)** + set **`wm density`** (không phải overscan). ClusterNav dùng `am display move-stack` + `wm overscan` → sai cả render lẫn scale.

## 4 khác biệt gốc
| Triệu chứng | ClusterNav (SAI) | DashCast (ĐÚNG) |
|---|---|---|
| **Trắng** (GMaps/Waze/video) | `am display move-stack` bê task màn-0 sang VD → lớp GL/video giữ config màn-0, **không composite** vào VD cụm → vùng map/video trắng | **Launch mới** vào VD: `am start --display N --windowingMode 5 --activity-clear-task` (hoặc in-proc `ActivityOptions.setLaunchDisplayId + setLaunchWindowingMode(5) + setLaunchBounds`). Activity mới cấp buffer theo config cụm từ đầu → mọi lớp composite đúng |
| **Nhỏ** (YouTube) | chỉ `wm overscan` (cắt pixel, **không đổi scale**); task giữ density màn-0 (240) → trên 1920×720 llayout tí hon | `wm density <dpi> -d <vd>` (mặc định **160**, tune 96–480) TRƯỚC khi launch, settle 150ms. **Density mới là fix scale**; overscan chỉ là khung |
| **ADAS đen** | warm-switch chỉ move-stack, **không re-issue cmd 16** trên VD → lớp ADAS của Qt còn composite → mảng đen | re-issue **cmd 16** trên VD tươi để xoá vùng ADAS (chờ VD mới rồi mới đặt app) |
| **Kẹt khi tắt** | reshape geometry (profile 31) **trong lúc** kéo task khỏi VD sắp biến mất, **không reset** density/overscan → WM/Qt lệch → kẹt → reboot | teardown có thứ tự: app off VD → reset density/overscan → **18** (stop) → **0** (restore native). KHÔNG reshape khi task còn trên VD |

## Recipe đầy đủ (DL3/Seal — path native-fission, KHÔNG tự createVirtualDisplay)
1. **Activate** VD OEM: `service call AutoContainer 2 i32 1000 i32 30` → sleep 3s → `i32 16` → 3s → `i32 35` (ADAS-fix). Warm (VD còn sống): chỉ `i32 16`.
2. **Discover** VD id (KHÔNG tạo): `dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja'` → id != 0 tên chứa fission/xdja. Đổi profile ⇒ VD **id mới** → luôn dò lại.
3. **Density**: `wm density <dpi> -d <CID>` (160 khởi điểm), settle 150ms. **Không bao giờ -d 0.**
4. **Launch freeform**: `am force-stop <pkg>; am start --display <CID> --windowingMode 5 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n <pkg/cls> --activity-clear-task`. Cờ then chốt chỉ là `--display` + `--windowingMode 5`.
5. **Overscan** (tuỳ, DL3): `wm overscan 0,90,0,90 -d <CID>` — khung mỹ thuật.
6. **Teardown**: app về display 0 → `wm density reset -d CID; wm overscan reset -d CID` → `i32 18` → sleep → `i32 0`.

## ⚠ Ẩn số phải TEST trên xe (gate quyết định)
Header ClusterCast cũ ghi `am start --display <phụ>` bị **SecurityException từ uid-2000 (dadb)** — nên mới xài move-stack. Nhưng DashCast (DL5 path) dùng `am start --display N --windowingMode 5` từ shell. **`--windowingMode 5` có thể là mảnh thiếu.** Test lệnh #4 từ dadb uid-2000:
- **Qua** → shell freeform launch là fix đúng (đã code vào v0.22 `placeAppOnVd`).
- **Vẫn SecurityException** → cần path in-process `ActivityOptions.setLaunchDisplayId` — NHƯNG DashCast chạy được in-proc vì **ký platform.keystore + INTERNAL_SYSTEM_WINDOW**; ClusterNav KHÔNG ký platform → in-proc sẽ cũng bị chặn. Khi đó phải: (a) xin ký platform, hoặc (b) đường daemon app_process uid-2000 (DashCast Path B: tự tạo VirtualDisplay backed by SurfaceView overlay — nặng). Ghi lại kết quả để chọn.

## v0.23 — fresh-launch MẶC ĐỊNH (KIỂM CHỨNG TRÊN XE 2026-07-15)
**GATE #1 QUA:** `am start --display N --windowingMode 5 --activity-clear-task` từ uid-2000 chạy được (KHÔNG SecurityException — `--windowingMode 5` là mảnh thiếu). GMaps **render FULL trên cụm** (screencap VD xác nhận: bản đồ đủ đường/nhãn/chip, KHÔNG trắng). ⇒ **fresh freeform launch giờ là MẶC ĐỊNH cho MỌI app**; auto-probe screencap của v0.22 (`probeBlank`/`WHITE_PNG_MAX`) **ĐÃ XOÁ** (không tin cậy).

`ClusterCast.placeAppOnVd()`: `wm density $clusterDpi -d VD` (fix scale, mặc định **200**) → `freshLaunch` (`am start --display VD --windowingMode 5 --activity-clear-task`) → fallback move-stack nếu không bám → `wm overscan` (khung). Warm re-issue 16 (ADAS). Teardown reset density/overscan → 18 → 0.

**move-stack = OPT-IN "giữ state" per-app** (`keepStateApps`, mặc định RỖNG = mọi app fresh): **giữ nhấn** 1 app trong Cài đặt chiếu để bật (marker ◈) — cho app cần giữ nguyên màn (vd VietMap). **State tradeoff:** launch mới reset activity, NHƯNG app dẫn đường tự resume nav đang chạy → "bấm Bắt đầu dẫn rồi mới chiếu" giữ tuyến; chỉ mất màn preview trước khi bấm Bắt đầu.

**DPI** chỉnh live trong Cài đặt chiếu (nút **Chữ nhỏ/to**, clamp 120–320): **DPI CAO = chữ/UI TO hơn** (px = dp × density/160). Mặc định 200 trên cụm gốc 320dpi → nội dung ~62.5% cỡ gốc.

Còn treo (cần xe — task #17/#18): verify fresh-launch render full + DPI-live + keep-state toggle trên cụm. Test: `docs/diagnostics/cluster-cast-test.ps1 -Wifi -Pkg anddea.youtube`.

## Path B (dự phòng — nếu OEM-VD vẫn trắng GL của GMaps)
DashCast daemon tự tạo VD của nó: SurfaceView overlay type=2006 trên cụm → `createVirtualDisplay(name,w,h,160,surface,flags)`, flags=`PRESENTATION|OWN_CONTENT_ONLY`(=10) hoặc +TRUSTED(1024)=1346. SurfaceFlinger GPU-composite MỌI lớp vào 1 Surface → phủ cả ADAS. Cần app_process daemon uid-2000. Chỉ dùng nếu Path A còn trắng GMaps.
