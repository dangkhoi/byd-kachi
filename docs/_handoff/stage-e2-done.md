# Stage E2 — done: `.kiro/steering/project-context.md`

> **Trạng thái**: Session · **Cập nhật**: 2026-08-19 · **Mục đích**: Handoff phiên E2 — tạo steering project-context luôn-bật (R6.1).

## Đã tạo / sửa (DOCS/STEERING ONLY — no code/commit/push/main)
- **`.kiro/steering/project-context.md`** (NEW, 43 dòng / 6034 ký tự) — tóm tắt luôn-bật, 5 mục:
  1. Kiến trúc 2 nhánh (Nav+HUD: 1 nguồn→Maneuver trung lập→cluster-lane + centre "Giữa+ETA" + windshield HUD; Cluster Cast 4-state độc lập; Home=renderer/dispatcher).
  2. Bản đồ nguồn nav (GMaps notif-nền+arrow · VietMap widget-nền + a11y foreground-only + camera→B3 · Waze/WazeMod không-data-nền→screen-capture/HLP/ESP32).
  3. HAL/CAN facts (0x38B00030 NOT-provisioned=-2147482648 coding-locked · guide 0x43F01010/0x1F701010 · SET_NAVI_SCREEN 0x4C10E015 · toHudIcon roundabout 15/18/20/22 + 24+N, toAmapIcon=11 · cluster ghi rc=0 / HUD từ chối · vòng xuyến generic = OEM-render owner-variant).
  4. 10 map file chính (path + vai trò 1 dòng).
  5. Trạng thái (branch feat/speed-limit-badge-hal-hud @3745046 pushed; main f7843c0; validate 18/18 + nội suy; open C2/C3/B3).
- **`docs/README.md`** §3 Rules/Steering — flip row `project-context.md` `Pending/E2` → `Current` + link (R1: doc mới ⇒ vào index).

## Nguồn đã đọc để bảo đảm chính xác (GATE = khớp diagnostics + backlog)
- Rule `.kiro/steering/documentation-and-backlog.md` (R6.1) · `docs/PROJECT-BACKLOG.md`.
- `docs/diagnostics/`: arrow-validation-teammate, distance-interpolation-validation, app-code-updates-2026-08-16, oncar-session (grep 0x38B00030).
- Source skim: `Maneuver.kt` (toHudIcon/toAmapIcon roundabout table) · `modules/hal/BydHal.kt` (NOT_PROVISIONED_RC, guide/oversea/SET_NAVI_SCREEN consts, writeNavFrame) · `NavRepository.kt` · `NavNotificationListener.kt` (5 nav packages) · `VietMapWidgetModels.kt` (slots + upcoming) · `SpeedBadgeOverlay.kt`.

## Verify (tool output)
- `wc -l` = **43 dòng** (< 150 gate) ✅ ; ngắn gọn, mỗi mục vài dòng.
- Git (read-only): HEAD=`3745046`, upstream `origin/feat/speed-limit-badge-hal-hud` sync, `main`=`f7843c0` → §5 chính xác.
- 3 file cited (SimpleCastRuntime, WazeHudSource, NavigationHudOwner) + 7 file khác đều tồn tại đúng path (glob) → §4 chính xác.
- Số liệu HAL/CAN (0x38B00030=-2147482648, 0x43F01010/0x1F701010, 0x4C10E015, roundabout 15/18/20/22/24+N/11) khớp `BydHal.kt` + `Maneuver.kt` + diagnostics.

## Còn lại cho phiên sau
- **KHÔNG commit** trong phiên này (task DOCS/STEERING ONLY). Batch E1+E2 (+E4 nếu chạy) cần **security scan (W5)** trước khi owner commit.
- ⚠️ **`.kiro/` bị gitignore** (`.gitignore:76`) nhưng steering repo được **force-track** (`git ls-files` thấy `product-team-workflow.md`). File mới `project-context.md` tồn tại trên đĩa (6034 B, 43 dòng) nhưng **hiện chưa tracked** → khi commit owner phải `git add -f .kiro/steering/project-context.md` (như file steering cũ), nếu không sẽ bị bỏ sót.
- E4: tạo `docs/decisions/` (ADR) + flip index §8 Pending→Current.
- Khi kiến trúc/trạng thái đổi (vd on-car PASS, merge main, xong B3) → **update `project-context.md` §5 + §3** (R6.1).
