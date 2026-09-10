# Handoff — Phiên 2026-09-10 (chiều): test emulator + host app vào ô

> **Loại**: Handoff (tạm) · **Ngày**: 2026-09-10 · **Trạng thái cây git**: **SẠCH** (`main`, ahead 1 = `b2c85f7` W1, chưa push; 0 file lệch). Chi tiết bằng chứng: `docs/diagnostics/kachi-emulator-app-hosting-2026-09-10.md`.

## Phiên này làm gì
Tiếp nối W1 (đã commit `b2c85f7`). Owner cài mớ app qua **Play Store** trên emulator + thử chạy Kachi làm launcher, mở app vào ô "như trên xe". Gặp nhiều trục trặc → phiên đi sâu vào gỡ rối emulator (drift khỏi kế hoạch). Owner chốt: dừng, dọn sạch, viết handoff.

## Kết quả (đã verify, không đoán)
1. **Popup adb lặp** = image Play Store **production khoá cứng** (`adb root` bị chặn, không ghi được `adb_keys`) → không pre-authorize dadb được → artifact emulator, KHÔNG phải xe.
2. **dadb loopback CHẠY trên `google_apis`** (`ro.adb.secure=0`, appops=allow) — bẫy đã sửa: 2-emulator làm reverse trỏ nhầm cổng (5556→adb ở host:5557, không phải 5555). Recipe 1-emulator sạch ở diagnostics.
3. **[BUG launcher, ảnh hưởng cả xe]** App trong ô không host lúc mở, phải đổi preset mới host (probe dadb async xong SAU render đầu, `render()` diff theo nội dung nên không dựng lại ô). Có **diff sửa 2 dòng đã kiểm** ([ĐO] VD tạo ngay khi mở sau fix) — **đã REVERT**, lưu diff trong diagnostics để làm lại đúng quy trình.
4. **[GIỚI HẠN cứng]** Waze/Maps **từ chối display phụ** → nhảy fullscreen ("App does not support launch on secondary displays"). Kachi sideload không ép được; trên **xe** (platform-signed + display tin cậy) mới host được. ⇒ app-vào-ô cho nhóm app này = verify TRÊN XE.
5. GMaps/YouTube trên google_apis là bản 2019 (GMS cũ); ~45 nút W1 mới thiếu icon (U1); chấm cam = badge tier đúng thiết kế.

## Quyết định đã làm
- **Revert 2 file sửa ad-hoc** (`KachiHomeActivity.kt`, `WorkspaceView.kt`) → cây về `b2c85f7` sạch. Fix launcher-hosting KHÔNG commit vội (chưa qua spec/test/senior review, chưa verify xe).
- **KHÔNG push** (public repo — cần security scan trước; W1 vẫn local ahead-1).

## Trạng thái emulator hiện tại
1 emulator `google_apis` (clusternav10, cổng 5554/5555), Kachi 1.38 = home, loopback + freeform bật, cài Waze/YT Music/Kiki (+ Kachi). Workspace đã `pm clear` về sạch (không app tự hijack). Play emulator đã tắt (app còn trong AVD, boot lại khi cần Play Store).

## Việc tiếp (owner chốt hướng)
| # | Việc | Loại | Ghi chú |
|---|------|------|---------|
| A | **Nút "mở toàn màn" + đường mở app kiểu thường** | off-car | Owner vướng nhất "mở 1 app bình thường không được". Launcher hiện slot-centric, không có mở-fullscreen hạng nhất. → backlog **U3** |
| B | **Fix launcher-hosting lúc mở** (diff sẵn) đúng quy trình | off-car code + 🚗 verify | spec/test/senior review; render thật = xe. → backlog **P-bug2** |
| C | **~45 icon SVG** cho nút W1 mới | off-car | → backlog **U1** |
| D | App-vào-ô cho app từ chối display phụ | 🚗 | chỉ verify được trên xe (platform-signed) |
| E | Copy GMaps/YouTube mới sang emulator | off-car | kèm caveat GMS 2019 + app từ chối ô |

## Cạm bẫy cho phiên sau
- **Không dùng image Play Store để test app-vào-ô** (khoá cứng, dadb loopback lặp popup). Play Store chỉ để cài app; test slot dùng `google_apis` + copy APK sang.
- **Chỉ chạy 1 emulator** (tránh lag + bẫy cổng reverse).
- **Không kết luận "app-vào-ô hỏng"** từ emulator: Waze/Maps từ chối display phụ là giới hạn sideload, xe khác.
- Push cần security scan trước (public `byd-kachi`).
