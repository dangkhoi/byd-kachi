# Stage E4 — Implementation-Log section + ADR type — DONE

> **Trạng thái**: Current · **Cập nhật**: 2026-08-19 · **Mục đích**: Handoff phiên E4 (template §Nhật ký triển khai + `docs/decisions/` ADR). Đọc bởi reviewer/E1.
> Working dir: `byd-cluster-2-wt-speed-limit-badge-hal-hud` · branch `feat/speed-limit-badge-hal-hud`
> Scope guard: **DOCS ONLY** — không code/commit/push; `main` không đụng; `docs/README.md` KHÔNG sửa (E1 sở hữu).

## Rule áp dụng
`.kiro/steering/documentation-and-backlog.md` **R2.6** (Nhật ký triển khai đi cùng code; quyết định KIẾN TRÚC xuyên suốt ⇒ ADR `docs/decisions/`) + **R4** loại #8 (ADR) + **R2.2** (header mỗi doc).

## Files created (4 mới)

| File | Nội dung |
|---|---|
| `docs/specs/_template.html` | **MỚI HOÀN TOÀN** — template không tồn tại trong worktree này (xem "Sai lệch" bên dưới). Dựng đủ 10 section canonical + section mới **§9 Nhật ký triển khai (Implementation Log)** đặt ngay TRƯỚC §10 Reviewer Log. Style Apple-2026 (CSS var light/dark, inline) mô phỏng spec mới nhất `upcoming-speed-limit-badge.html`; kèm helper diagram `.diagram/.layers/.flow/.legend` mà `workflow.md §3.1` tham chiếu là "dựng sẵn trong template". |
| `docs/decisions/README.md` | Index ADR + hướng dẫn định dạng (5 mục: Context · Decision · Consequences · Status · Date; tên `NNNN-<kebab-title>.md`; Status hợp lệ; khi nào mở ADR vs §Nhật ký triển khai). Bảng Index có 3 dòng. |
| `docs/decisions/0001-nav-source-strategy.md` | ADR: nguồn nav per-app — GMaps notif (nền, chính) · VietMap widget(nền)+a11y(foreground) · Waze screen-capture (B3) — + adapter trung lập `NavReading`. |
| `docs/decisions/0002-hud-nav-coding-locked.md` | ADR: `0x38B00030` NOT provisioned + write bị reject (control: `43F01010/018` ghi rc=0) → HUD kính là **coding BYD**, không phải app; mở khoá qua OBD/UDS. |
| `docs/decisions/0003-datacollection-logging-default-off.md` | ADR: log/ảnh opt-in mặc định OFF (`Prefs.navVerboseLog=false`, bỏ `\|\| BuildConfig.DEBUG`) + `StorageCapPlanner` cap ~150 MB xoá-cũ-nhất luôn-bật. Trade-off: mất auto-collect + trim >150 MB trước khi pull. |

## GATE — verified bằng tool output
- ✅ Template có section mới: `grep` thấy `<h2>9. Nhật ký triển khai (Implementation Log)</h2>` + đủ 5 sub-bullet guidance (Quyết định + lý do · Trade-off · Sai lệch spec · Bug & cách xử · "Spec không work → đổi X"). Đặt đúng **trước Reviewer Log** (verify: `index('Nhật ký triển khai') < index('Reviewer Log')` = True).
- ✅ Template HTML sound: 10 `<h2>` section; tag `html/head/body/style` cân bằng (mỗi cái open=close).
- ✅ `docs/decisions/` có README + 3 ADR; mỗi ADR có đúng `## Context / ## Decision / ## Consequences / ## Status / ## Date` (grep xác nhận cả 3 file).
- ✅ `docs/README.md` KHÔNG bị sửa bởi E4 (`git status` = `?? docs/README.md`, untracked của E1 — không có ` M`). Thay đổi của E4 thuần cộng thêm: `docs/decisions/` + `docs/specs/_template.html`.

## Sai lệch so với task (ghi rõ — R2.6)
- **Task nói "add a new section" vào `docs/specs/_template.html`, nhưng file KHÔNG tồn tại** trong worktree này (`glob **/_template.html` = 0; workflow.md có tham chiếu nó như starting point → đây là gap). ⇒ E4 **tạo mới** template với TRỌN BỘ section canonical (theo `workflow.md`: Changelog · Context · Requirements · Design · Tasks · Verification · Open Questions · References · **Implementation Log** · Reviewer Log) + section mới. Đây là cách trung thành nhất với ý định task (template có section Implementation Log + khớp style specs hiện có).
- **Backlog E4 rộng hơn task E4 này**: backlog ghi "Thêm §Nhật ký triển khai vào spec template **+ specs hiện có**". Task orchestrated CHỈ yêu cầu template + `docs/decisions/` + 3 ADR — E4 **không** động vào 38 specs hiện có (ngoài scope task; stay-on-track W6). Nếu owner muốn back-fill section vào specs hiện có → task riêng.

## Nợ cho reviewer / E1 (R1 — cấm doc mồ côi)
Task cấm E4 sửa `docs/README.md` ("you'll be added by the reviewer"). **CẦN thêm vào index `docs/README.md`** (cùng commit batch) 4 doc mới:
- `docs/specs/_template.html` — Template spec (Spec type) — Current.
- `docs/decisions/README.md` — ADR index + format guide (ADR type) — Current.
- `docs/decisions/0001-nav-source-strategy.md` · `0002-hud-nav-coding-locked.md` · `0003-datacollection-logging-default-off.md` — Current.
- Flip index §8 (ADR) từ "Pending" → "Current" (E1 handoff đã ghi việc này).
- Backlog `PROJECT-BACKLOG.md` **E4**: có thể chuyển 🔲→✅ (phần template + decisions/ đã xong; phần "specs hiện có" nếu owner muốn thì tách task).

## Còn lại trước khi owner commit
- Pre-commit security scan (W5 / `pre-commit-security.md`) trên toàn batch docs — **bắt buộc**, chưa chạy.
- ADR/template không chứa secret/PII/IP: readback HUD ở off-repo `~/Desktop/hud-xe-minh.txt` (chỉ nêu tên file, không nội dung); giá trị `0x38B00030=-2147482648` là mã kỹ thuật, không nhạy cảm; không có tên đường/VIN/IP thật.
