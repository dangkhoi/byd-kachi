# CarPlay lên cụm: `am stack move-task` THÀNH CÔNG — đo trên xe 2026-08-01

## TL;DR

**`am stack move-task <taskId> <stackId> true` đặt được CarPlay lên cụm mà KHÔNG crash system_server.**
Đường vòng qua NPE `am display move-stack` (crash 3/3 đêm 31/7) đã được chứng minh trên xe thật.
DashCast chưa bao giờ giải được bài này.

---

## Điều kiện đo

- Xe: BYD Atto 3, DiLink3, Android 10 (SDK 29)
- CarPlay đang cắm dây sống, `com.byd.carplay.ui/.VideoActivity` visible trên display 0
- Display 1 (cụm) đã có stack sẵn (VietMap đang chiếu, stackId=6)
- Xe đỗ, đang sạc

## Kết quả từng bước

| Step | Lệnh | Kết quả | Ghi chú |
|------|-------|---------|---------|
| T0 | read-only probe | ✅ | CarPlay task=15, stackId=14, display 0. `RESIZE_MODE_UNRESIZEABLE` nhưng `isResizeable=true` |
| T1 | `am stack move-task 15 6 true` | ✅ **THÀNH CÔNG** | CarPlay lên display 1, bounds=[0,0][1920,720], visible=true. 0 crash. Stack 14 tự dọn (rỗng) |
| T2 | `am task resize 15 960 0 1920 720` | ❌ | `IllegalArgumentException: resizeTask not allowed` — UNRESIZEABLE |
| T2b | `settings put global force_resizable_activities 1` + retry | ❌ | Flag chỉ ảnh hưởng lúc launch, không giúp resize runtime |
| T2c | `wm density 160 -d 1` | ✅ **THÀNH CÔNG** | CarPlay tự scale vào 1920×720, bounds khớp cụm |
| T3 | `am stack move-task 15 12 true` | ✅ | CarPlay về display 0 an toàn |

## Bài học quan trọng

### 1. `move-task` ≠ `move-stack` — khác hoàn toàn ở tầng framework

- `am display move-stack` → `DisplayContent.moveStackToDisplay` → gỡ stack khỏi display cũ → NPE trong cửa sổ `mDisplayContent = null` → **CRASH 100%** khi vượt ranh giới freeform
- `am stack move-task` → `TaskStack.positionChildAt` → gán `task.mStack` TRƯỚC `addChild` → **không rơi vào cửa sổ null**

### 2. CarPlay KHÔNG resize được — nhưng density override GIẢI QUYẾT

- `RESIZE_MODE_UNRESIZEABLE` từ chối mọi `am task resize`
- Nhưng `wm density <dpi> -d 1` thay đổi density của display → app tự layout lại để vừa khung
- Display 0: 1920×1080, density 240 → Display 1: 1920×720, density 320
- Nếu giữ nguyên density 320: CarPlay render 1080px cao nhưng cụm chỉ hiện 720px → cắt mất 360px dưới
- Đặt density thấp hơn (160) → CarPlay scale xuống vừa 720px

### 3. Cần stack có sẵn trên display đích

`move-task` di chuyển task VÀO một stack — stack phải tồn tại. Nếu cụm rỗng (không có stack nào) thì phải cast một app bình thường trước để tạo stack, rồi mới move CarPlay vào.

### 4. `am start --display 0` không dùng được với CarPlay

`not exported from uid 1000` — phải dùng `move-task` cả hai chiều (đặt lên + trả về).

## Đường sản phẩm rõ ràng

1. Cast một app thường lên cụm (tạo stack)
2. `am stack move-task <carplay-task> <cluster-stack> true` (CarPlay lên cụm)
3. `wm density <calculated-dpi> -d 1` (scale cho vừa)
4. Khi trả về: `am stack move-task <carplay-task> <display0-stack> true`
5. `wm density reset -d 1` (trả density về mặc định)

## Vấn đề mở

- **Density nào đúng?** 160 là con số thử, chưa tính chính xác. Cần: `original_density × (cluster_height / source_height)` = 320 × (720/1080) ≈ 213. Hoặc đơn giản hơn: density sao cho nội dung CarPlay vừa đủ hiện hết.
- **Chưa nhìn thấy cụm** (đang sạc, màn sạc đè). Cần verify bằng mắt khi sạc xong.
- **VietMap bị đè** khi CarPlay fullscreen cùng display — cần trả VietMap về display 0 trước, hoặc chấp nhận chỉ CarPlay trên cụm.
- **Density thay đổi ảnh hưởng app khác** trên cùng display (VietMap) — nếu hai app cùng trên display 1 thì cả hai nhận cùng density mới.

## Tệp liên quan

- `docs/diagnostics/carplay-aa-cluster-placement-research-2026-08-01.md` — phân tích NPE, chứng minh AOSP
- `docs/diagnostics/carplay-move-stack-npe-crash-2026-08-01.md` — crash trace 3/3 lần
