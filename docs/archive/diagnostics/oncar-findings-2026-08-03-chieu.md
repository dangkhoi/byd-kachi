# On-car findings 2026-08-03 chiều — fix batch

## Proven sequence (đã work trên xe):
1. `wm size 1920x800 -d 1`
2. `wm overscan 0,0,0,0 -d 1`  
3. `wm density reset -d 1`
4. `service call AutoContainer 2 i32 1000 i32 30 s16 ""`
5. `service call AutoContainer 2 i32 1000 i32 16 s16 ""`
6. `service call AutoContainer 2 i32 1000 i32 35 s16 ""`
7. `am start --display 1 --windowingMode 5 -n ClusterBlackActivity` 
8. `am task resize <taskId> 0 0 1920 720` (full display)

## Fixes needed:

### Fix A: openProjection sequence order
- HIỆN: profile 30/16/35 → wm size (trong cast lần đầu)
- ĐÚNG: wm size 1920x800 → wm overscan → wm density reset → RỒII MỚI profile 30/16/35 → launch black → resize black full

### Fix B: Bubble tap slot trống trong split mode
- HIỆN: CastingSplit → LUÔN dispatch Stop(slot) → toast "Đang trả app về"
- ĐÚNG: CastingSplit → check slot occupied?
  - Occupied → Stop(slot)  
  - TRỐNG → detect foreground → CastSlot(pkg, slot)

### Fix C: Black activity resize full sau launch
- HIỆN: launch black activity → default freeform bounds (bé, 440px)
- ĐÚNG: launch → sleep 1s → `am task resize <taskId> 0 0 1920 720`

## Other observations:
- Cast VietMap 50% trái: OK, đẹp
- Cast GMaps 50% phải: OK, nhưng GMaps UI panels quá to (cosmetic, xử lý sau)
- Stop VietMap trái: OK, mảng đen còn nguyên
- Navigation broadcast vẫn chạy song song (two-pipeline OK)

## Session 2 findings (18:05-18:13)

### Working:
- Projection open (đen sẵn) OK khi timing 2s giữa profile commands ✅
- Cast GMaps trái/phải OK ✅
- DPI chỉnh OK ✅
- CarPlay cast lên OK ✅

### Issues:
- CP package = `com.byd.carplay.ui` (NOT `com.byd.autolink.carplay` as hardcoded)
- CP return fail → kẹt (findTaskId can't find wrong package name)
- Display 1 disappears after CP issue (all tasks moved to display 0)
- Resize panel disappears on re-cast (state race in refresh?)
- Bubble CastingSplit empty-slot foregroundPackage returns null (need debug)
- Cast trái khi phải đang cast → "Không xác định" (foreground detection issue)

### Fix needed:
- Update AppMover CARPLAY_PACKAGES to include `com.byd.carplay.ui`
- CP return: find task by actual package `com.byd.carplay.ui`
- Investigate resize panel disappearing
- Investigate foregroundPackage null in CastingSplit

## Session 3 findings (18:20-18:24) — GREAT PROGRESS

### Working perfectly:
- Cast qua về mượt ✅
- Tỷ lệ split chạy đúng ✅  
- DPI chỉnh cho 1 app khi full: OK, lưu + restore ✅
- ClusterBlack ready state ✅
- CP package fix ✅

### Issue: DPI in split mode
- 1 app full → DPI lưu + restore OK
- 2 app split → DPI KHÔNG restore per-app
- Root cause: `wm density` là DISPLAY-GLOBAL (giống wm size). Không set khác DPI cho 2 task.
- NHƯNG user CẦN chỉnh DPI cho từng app (app panel to quá ở DPI cao → không đọc được content)
- Thực tế: mỗi lần đổi app active (tap slot) → set DPI cho app đó → DPI cả display đổi → app kia cũng bị ảnh hưởng. Chấp nhận tradeoff: DPI theo "app đang focus" tại thời điểm chỉnh.

### Fix needed:
- Split mode: hiện control DPI cho từng slot (chỉnh slot nào → set DPI cho slot đó)
- Khi đang split: DPI apply cho TOÀN display (limitation Android) → cả 2 app đều bị ảnh hưởng
- Workaround: mỗi slot có nút DPI riêng, set DPI = DPI đã lưu cho app slot đó khi user tap
- Tradeoff rõ ràng: 2 app KHÔNG THỂ có DPI khác nhau cùng lúc (Android limitation)

### UX decision:
- Full mode: resize + DPI
- Split mode: DPI only (per-slot button, affects whole display)
- Split mode không có resize (bounds fixed theo ratio)

## Session 4: CP auto-cast issue (18:39)

### Observation:
- CP (`com.byd.carplay.ui`) tự nhảy lên display 1 khi iPhone kết nối (OEM behavior, ngoài ClusterNav control)
- ClusterNav coordinator state = Idle (không biết CP đang trên cụm)
- Bubble hiện trắng (Idle) → không có cách stop
- `am start --display 0` FAIL cho CP (activity not exported)
- `am stack move-task <taskId> <standard-stack-on-d0> true` WORK

### Fix needed:
1. `cleanDisplay1()`: dùng `am stack move-task` thay vì `am start` cho apps có activity not-exported
2. Bubble: khi state=Idle nhưng display 1 có app ngoại lai → detect + cho option return
3. Hoặc: `openProjection` cleanDisplay1 dùng move-task pattern (tìm non-home stack trên display 0 rồi move-task vào đó)

## Session 5: CP cast bé (18:46)

### Observation:
- Cast CP qua simplified code: CP lên display 1 nhưng BÉ ở giữa (freeform default bounds)
- Bấm bubble stop → CP trả về → cụm đen (black activity) → rồi CP crash
- OEM CarPlay khi TỰ lên thì full screen. Khi MÌNH move-task vào → inherit freeform bounds

### Root cause:
- `findOrCreateClusterStack` tạo freeform stack (Settings launch freeform)
- `am stack move-task` CP vào → CP gets freeform bounds (bé)
- Cần `am task resize` CP full display SAU khi move-task

### Fix needed:
- AppMover CP/AA path: sau `am stack move-task` thành công → `am task resize <taskId> 0 0 1920 720`
- Set wm size ĐÚNG cho CP trước: 1422×800 (spec) hoặc just full 1920×720?
- Hôm qua CP tự lên full = OEM set bounds, không phải freeform. Mình move-task = freeform.
- Có thể cần: launch CP lên display 1 bằng `am start --display 1` thay vì move-task? Nhưng CP not-exported...
- Alternative: tạo fullscreen stack trên display 1 (windowingMode 1) thay vì freeform (5)?
