# Rule: Documentation & Backlog Discipline — ClusterNav 2.0

> **BẮT BUỘC 100% MỌI PHIÊN. KHÔNG drift, KHÔNG lệch, KHÔNG "để sau".**
> Repo = **nguồn sự thật DUY NHẤT**. Knowledge base + project-context = *derived* (sinh lại từ repo).
> Đây là kỷ luật nguồn-sự-thật của dự án — agent tự enforce trước/trong/sau mỗi phiên.

## R0. Source of truth
- `docs/README.md` = **INDEX canonical** (map MỌI doc hiện hành). **Không có trong index = archive/stale, KHÔNG authoritative.**
- `docs/PROJECT-BACKLOG.md` = **nguồn DUY NHẤT cho task** (KHÔNG dùng GitHub Issues song song).
- `.kiro/steering/` = luật bền + `project-context.md` (luôn-bật).

## R4. 9 loại document (taxonomy — doc để ĐÚNG chỗ)
**Luôn-current:** 1) Index `docs/README.md` · 2) Backlog `docs/PROJECT-BACKLOG.md` · 3) Rules/Steering `.kiro/steering/` (gồm project-context) · 4) Overview root `README.md` + `docs/CLOSEOUT-*`.
**Theo artifact:** 5) Spec `docs/specs/*.html` (duyệt TRƯỚC khi code) · 6) Diagnostics `docs/diagnostics/*.md` (finding có BẰNG CHỨNG + ngày) · 7) Guide `docs/HUONG-DAN-*` (user/anh em, song ngữ) · 8) ADR `docs/decisions/NNNN-*.md` (quyết định KIẾN TRÚC xuyên suốt).
**Tạm/lịch sử:** 9) Handoff `docs/_handoff/*.md` (plan/summary phiên, tạm). · `docs/archive/` = **TRẠNG THÁI** (doc bị thay thế; giữ history, KHÔNG authoritative).

Ranh giới: Spec=*sẽ làm gì* · Diagnostics=*đã tìm ra gì (bằng chứng)* · ADR=*quyết định kiến trúc + lý do* · Handoff=*tóm tắt phiên (tạm)* · Guide=*user dùng sao* · Overview=*dự án là gì* · Index=*map doc*.

## R1. Index
- Mỗi doc current liệt kê trong `docs/README.md`: đường dẫn · mục đích 1 dòng · trạng thái (Current/Archive) · ngày cập nhật.
- Tạo doc mới ⇒ **thêm entry vào index CÙNG commit**. Cấm doc mồ côi. Doc lỗi thời ⇒ chuyển `docs/archive/` + gỡ khỏi index.

## R2. Continuous documentation (KHÔNG out-of-date)
- **R2.1** Code + doc **ATOMIC**: đổi hành vi / có finding ⇒ update doc liên quan + index + backlog **TRONG CÙNG phiên/commit**.
- **R2.2** Mỗi doc có **header**: tiêu đề · trạng thái · ngày cập nhật · mục đích 1 dòng.
- **R2.3** **Document NGAY khi làm** — cấm "để sau".
- **R2.4** Cấm 2 doc "current" mâu thuẫn; nội dung bị thay thế ⇒ update hoặc archive.
- **R2.5** TRƯỚC khi làm: đọc index + backlog + doc liên quan. SAU khi làm: update chúng.
- **R2.6 — Nhật ký triển khai (đi CÙNG code):** khi code, mọi **quyết định · trade-off · sai lệch-spec · bug & cách xử · "spec không work → đổi X"** ghi NGAY vào **§ Nhật ký triển khai** của spec feature đó; nếu là **quyết định KIẾN TRÚC xuyên suốt** ⇒ mở **ADR** (`docs/decisions/`). **Spec phải phản ánh code THỰC TẾ, kể cả chỗ lệch** — không đứng yên ở "kế hoạch lý tưởng".

## R3. Backlog
- Mọi việc ở `PROJECT-BACKLOG.md`: ID · việc · trạng thái · bắt đầu · kết thúc · ghi chú.
- Status: `DONE · IN-PROGRESS · TODO · BLOCKED · REFINE · BACKLOG`.
- Chuyển trạng thái ĐÚNG LÚC (add trước khi làm → In-progress +ngày bắt đầu → Done +ngày kết thúc / Blocked). Việc/ý tưởng/finding-cần-làm mới ⇒ **add NGAY** (không dựa trí nhớ).

## R5. Cách đọc/ghi chuẩn
- **ĐỌC**: `docs/README.md` (index) → doc cụ thể; **knowledge-base search** tra nhanh (thay vì đọc lại từ đầu); `project-context.md` cho tóm tắt luôn-bật.
- **GHI**: doc mới → header + link vào index (+ entry backlog nếu là việc). Tên: kebab-case; doc theo ngày = `<topic>-YYYY-MM-DD.md`; ngày = `YYYY-MM-DD`. Diagram = **HTML visual** (không mermaid/ascii).

## R6. Self-learning / freshness
- `.kiro/steering/project-context.md` = tóm tắt luôn-bật (kiến trúc + bản đồ nguồn nav + HAL/CAN facts + map file + trạng thái). Update khi kiến trúc/trạng thái đổi.
- Knowledge base index `docs/` + source; **re-index sau khi đổi doc lớn** để search luôn tươi.

## ✅ SESSION CHECKLIST (bắt buộc mỗi phiên)
1. **ĐẦU phiên**: đọc `docs/README.md` + `PROJECT-BACKLOG.md` + spec/diagnostics liên quan việc đang làm. (Đừng đọc lại toàn code — dùng knowledge search.)
2. **TRONG phiên**: quyết định/trade-off/sai lệch/bug ⇒ ghi §Nhật ký triển khai spec / ADR **NGAY** (R2.6).
3. **CUỐI phiên** (khi có đổi trạng thái): update **INDEX + BACKLOG + project-context**; re-index knowledge base nếu đổi doc lớn.
4. Trước commit: doc + index + backlog đã khớp code chưa? Chưa ⇒ update rồi mới commit.
