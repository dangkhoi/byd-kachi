# Stage E1 — Docs taxonomy refactor + canonical INDEX — DONE

> **Trạng thái**: Current · **Cập nhật**: 2026-08-19 · **Mục đích**: Handoff E1 — kết quả refactor `docs/` theo 9-loại + tạo `docs/README.md` INDEX (rule `documentation-and-backlog.md`).

Branch: `feat/speed-limit-badge-hal-hud` · DOCS ONLY · KHÔNG commit/push · KHÔNG đụng code/keystore/steering/main.

## Kết quả GATE

- ✅ `docs/README.md` (INDEX canonical) **đã tạo** — nhóm theo đúng 9-loại taxonomy + Design/evidence + Archive pointer.
- ✅ **Link check: 89/89 link phân giải OK, 0 broken** (mọi link trỏ file tồn tại, resolve tương đối từ `docs/`).
- ✅ **Coverage: 0 Current doc bị sót.** Mọi `.md/.html` top-level current đều có entry. Các file "unlinked" duy nhất là thành viên nội bộ của 2 workspace (`diagnostics/hud-sign-re/`, `refactor-car-execution/`) + `diagnostics/artifacts/` — đã được đại diện bằng entry-doc/folder-pointer (chủ đích, không enumerate từng file).
- ✅ Header 4-dòng đã thêm cho **11 doc Current dạng .md** thiếu header.

## Số liệu

- Doc `.md/.html` trong `docs/` (trừ `archive/`): **96** file.
- File trong `docs/archive/`: **159** (1 pointer duy nhất trong index, không enumerate).
- Link trong index kiểm tra: **89**, broken: **0**.
- **File archived lần này (git mv): 0** — xem lý do bên dưới.

## Bảng phân loại (file → loại → trạng thái)

**Tóm tắt theo loại** (chi tiết Current đầy đủ nằm trong `docs/README.md`):

| Loại | Current (authoritative) | Historical/Session (giữ tại chỗ, context) |
|------|------------------------|-------------------------------------------|
| 1 Index | `docs/README.md` | — |
| 2 Backlog | `PROJECT-BACKLOG.md` | — |
| 3 Rules/Steering | `../.kiro/steering/documentation-and-backlog.md`, `product-team-workflow.md` | (project-context.md = Pending E2) |
| 4 Overview | `../README.md`, `CLOSEOUT-2026-08-16.md`, `HISTORICAL-ARTIFACTS.md` | — |
| 5 Spec (`specs/`) | 11 spec (upcoming-speed-limit-badge, speed-limit-cluster-hud-oncar-ready, speed-badge-placement-vietmap-logging, v2-accessibility-navsource-handoff, waze-vietmap-signal-revival, clusternav-two-track-final-plan, cluster-cast-rebaseline, clusternav-uxui-rebaseline, dead-reckon-revalidation, notif-grant-docs-voicekey-1.13, clusternav-closeout-1.28) | 28 spec lineage + `cast-ui-state-v2.schema.json` (Historical, giữ tại chỗ) |
| 6 Diagnostics (`diagnostics/`) | 10 (VEHICLE-TEST-V2, arrow-validation-2026-08-18, distance-interpolation-2026-08-18, oncar-session-2026-08-16, app-code-updates-2026-08-16, nav-icon-mapping-2026-08-16, nav-output-architecture-2026-08-16, re-maneuver-icon-tables-2026-08-14, gemini-assistant-voicekey-2026-08-13, hud-sign-re/ workspace) | oncar-handoff-voicekey-2026-08-14, `artifacts/`, 4 `.ps1` scripts (tooling) |
| 7 Guide (`HUONG-DAN-*`) | HUONG-DAN.md, HUONG-DAN-LAY-LOG.md, HUONG-DAN-LAY-LOG-WINDOWS.html, HUONG-DAN-THU-DATA-HUD.html | — |
| 8 ADR (`decisions/`) | — | Pending E4 (thư mục chưa tạo — đúng phân công) |
| 9 Handoff (`_handoff/`) | — | 20 handoff phiên (Session) + file này |
| (ngoài 9-loại) | design/evidence ×2 (context) | research/ (gps-dead-reckon-tunnel), reference/ (dashcast-recipe), refactor-car-execution/ workspace, images/ (assets) |
| Archive | — | `archive/` (diagnostics/review/_handoff) — 1 pointer |

## Quyết định tự chủ (autonomous decisions)

1. **Archived = 0 (bảo thủ, có chủ đích).** Rule step 4 chỉ git mv file "rõ ràng bị thay thế" và nhấn mạnh "nghi ngờ thì GIỮ Current". README hiện hành (1.30) **cố ý giữ** spec cũ / `reference/` / `research/` trong `docs/` làm "context only". Di chuyển hàng loạt (nhất là 28 spec + workspace `refactor-car-execution/` 40+ file fixture/evidence) sẽ (a) phá cross-link lineage, (b) rủi ro cao trên feature branch, (c) trái chỉ thị bảo thủ. Thay vào đó INDEX **tách rõ Current-authoritative vs Historical/lineage** — đạt đúng mục tiêu step 4 (doc chết không giả làm current) qua R0 ("không trong Current-index = không authoritative") mà không đụng vị trí file → giữ nguyên link-validity gate.
2. **"Current" cho spec = đúng bộ canonical README liệt kê + spec mới trên branch này.** README (Overview sống ở 1.30) là nguồn phán quyết của owner về "cái gì hiện hành"; mọi spec khác README gọi là "context only" → xếp Historical/lineage (vẫn liệt kê để index đầy đủ, không mồ côi).
3. **Handoff = "Session" (tạm), không ép header 4-dòng.** R4 xếp handoff là "tạm/lịch sử"; mỗi file đã có H1 mô tả. Liệt kê trong index nhưng không thêm header để giảm churn trên doc tạm.
4. **hud-sign-re/ + refactor-car-execution/ = đại diện bằng entry-doc/folder-pointer**, không enumerate từng JSON/fixture (chúng là data/artifact của workspace, không phải doc độc lập).
5. **HISTORICAL-ARTIFACTS.md** giữ nguyên (đã có header trạng thái/ngày riêng) → không thêm header trùng; xếp Overview (Current — hồ sơ duy trì).

## File đã đổi (KHÔNG commit)

- **Tạo:** `docs/README.md` (INDEX), `docs/_handoff/stage-e1-done.md` (file này).
- **Thêm header 4-dòng (11):** `CLOSEOUT-2026-08-16.md`, `PROJECT-BACKLOG.md`*, `HUONG-DAN.md`, `HUONG-DAN-LAY-LOG.md`, `diagnostics/VEHICLE-TEST-V2.md`, `diagnostics/gemini-assistant-voicekey-oncar-2026-08-13.md`, `diagnostics/arrow-validation-teammate-2026-08-18.md`, `diagnostics/distance-interpolation-validation-2026-08-18.md`, `diagnostics/oncar-session-2026-08-16.md`, `diagnostics/app-code-updates-2026-08-16.md`, `diagnostics/re-maneuver-icon-tables-2026-08-14.md`.
  - *`PROJECT-BACKLOG.md` vẫn untracked (`??`) — header nằm trong file mới, chưa commit.

## Bàn giao cho stage sau

- **E4 (ADR):** tạo `docs/decisions/` + ADR đầu tiên → cập nhật mục §8 trong `docs/README.md` (đổi Pending → Current, thêm entry ADR + link).
- **E2 (project-context):** tạo `../.kiro/steering/project-context.md` → mục §3 index đã có dòng Pending, đổi sang Current khi xong.
- **Rule R1:** doc mới bất kỳ ⇒ thêm entry vào `docs/README.md` cùng commit (tránh mồ côi).
- **Pre-commit:** khi owner commit đợt này, chạy security scan bắt buộc (W5) — chưa chạy vì E1 không commit.
