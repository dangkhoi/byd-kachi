# Distance/interpolation validation — owner afternoon drive (2026-08-18)

> **Trạng thái**: Current · **Cập nhật**: 2026-08-18 · **Mục đích**: Kiểm cự ly 2 bên + nội suy km→turn từ chuyến chiều owner (có a11y screenRead).

Dataset: `~/Desktop/clusternav-drive-pm-20260818/files` (chuyến chiều owner, có a11y screenRead).
Mục tiêu (b): kiểm cự ly 2 bên đồng nhất, tìm sai lệch để patch nội suy.

## Kết luận: NỘI SUY ĐÚNG — KHÔNG cần patch
- `display_m` vs `rawGmaps_m` (notif GMaps, ground-truth tin cậy): **n=7512, exact<1m=70%, ≤25m=81%, median=0, mean=−13** → nội suy bám sát cự ly GMaps.
- Chuyến sáng (GMaps foreground, screenRead hợp lệ): **95% khớp <1m** → nội suy đã validate chính xác.

## "Lệch 440m vs screenRead" = ground-truth HỎNG (không phải nội suy)
- `display−screenRead` median=440 NHƯNG `display−rawGmaps` median=0 → screenRead là số lệch, không phải display.
- Bằng chứng: `screenRead_road=""` toàn bộ 7069 dòng; `screenRead_age_ms` tới **224049ms (224s stale)**; dòng ví dụ `display=4500=rawGmaps` nhưng `screenRead=10` (đóng băng).
- Nguyên nhân: chuyến chiều **GMaps chạy nền / không ở màn nav-card** (main hiện home/settings/AVM) → a11y scan không đọc được GMaps → `screenRead` đóng băng giá trị cũ. scan/refine KHÔNG chạy khi GMaps nền → nội suy không bị đầu độc, vẫn bám notif.

## Việc nên fix (diagnostics hygiene — KHÔNG phải nội suy)
- `screenRead` ground-truth nên **đánh dấu INVALID khi stale (age > ~2-3s) hoặc không có road** (GMaps nền) → tránh nhiễu phân tích + tránh refine bằng anchor rác.
- Để validate nội suy vs màn-thật lần sau: đảm bảo **GMaps foreground + đang dẫn** khi thu (như chuyến sáng).

**Fix applied (2026-08-19, B4):** đã implement — `TurnDistanceInterpolator.freshScreenRead(meters, ageMs, road)` (thuần) khiến `nav_log` ghi `screenRead_m = -1 (INVALID)` + `screenRead_age_ms = -1` khi **age > `SCREEN_READ_STALE_MS` (2500ms) HOẶC đường-đọc-màn rỗng** (wired ở `ClusterBroadcaster` qua `NavAccessibilitySource.road`); `refine(seg, nowMs, readAgeMs = 0L)` giờ **từ chối** mẫu stale (`readAgeMs > 2500ms`) hoặc invalid (`seg < 0`) — không ghi ground-truth, không snap baseline → nội suy không bị refine bằng anchor rác. Fresh+valid → hành vi GIỮ NGUYÊN. Unit test: `core/.../TurnDistanceInterpolatorTest` (+8 case, tổng 17, xanh).

## Tổng 2 goal (2026-08-18)
- Goal 1 (mũi tên 1-1): PASS — app bắn đúng 18/18. Vòng xuyến generic của owner = OEM-render (CAN 18), không phải app. Xem `arrow-validation-teammate-2026-08-18.md`.
- Goal 2 (nội suy): PASS — nội suy đúng (bám notif median 0; sáng 95% vs màn). "Lệch" chiều nay = screenRead stale do GMaps nền. Không patch nội suy.
