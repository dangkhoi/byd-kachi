# Architecture Decision Records (ADR) — ClusterNav 2.0

> **Trạng thái**: Current · **Cập nhật**: 2026-08-19 · **Mục đích**: Index + hướng dẫn định dạng cho các quyết định KIẾN TRÚC xuyên suốt (`docs/decisions/NNNN-*.md`).

ADR ghi lại **quyết định kiến trúc + lý do** — loại doc #8 trong taxonomy 9-loại (`.kiro/steering/documentation-and-backlog.md` R4). Ranh giới với các loại khác:

- **Spec** (`docs/specs/*.html`) = *sẽ làm gì* (duyệt TRƯỚC khi code).
- **Diagnostics** (`docs/diagnostics/*.md`) = *đã tìm ra gì (bằng chứng + ngày)*.
- **ADR** (`docs/decisions/NNNN-*.md`) = *quyết định kiến trúc + lý do, xuyên suốt nhiều feature*.
- **Nhật ký triển khai** (§ trong mỗi spec) = quyết định/trade-off/bug **trong phạm vi 1 feature**. Khi một quyết định **vượt khỏi 1 feature** (ảnh hưởng kiến trúc chung) ⇒ nâng lên ADR (R2.6).

## Khi nào mở ADR

Mở ADR khi quyết định:

- Ảnh hưởng **nhiều feature / nhiều phiên** (không chỉ 1 spec).
- Chốt một **ranh giới kiến trúc** (nguồn dữ liệu, transport, tầng, vòng đời, khôi phục).
- Đóng/gác một hướng vì **bằng chứng** (kể cả "không thể" — kèm điều kiện mở khoá, theo `trace-den-tan-cung.md`).
- Đặt một **mặc định** có trade-off đáng ghi (an toàn / bộ nhớ / quyền riêng tư).

Quyết định chỉ trong 1 feature → ghi ở **§ Nhật ký triển khai** của spec đó, KHÔNG cần ADR.

## Định dạng (bắt buộc)

Mỗi ADR là 1 file Markdown, tên **`NNNN-<kebab-title>.md`** (số thứ tự 4 chữ số, tăng dần: `0001-`, `0002-`, …; tiêu đề kebab-case). Bắt đầu bằng **header 1 dòng** (R2.2) rồi đúng **5 mục**:

```markdown
# NNNN — <Tiêu đề>

> **Trạng thái**: Accepted · **Ngày**: YYYY-MM-DD · **Mục đích**: <1 dòng>.

## Context
Bối cảnh + vấn đề + bằng chứng (dẫn diagnostics/commit/file:line). Vì sao cần quyết định.

## Decision
Đã quyết định GÌ (rõ ràng, 1 câu chốt + chi tiết). Nêu cả phương án bị loại nếu đáng.

## Consequences
Hệ quả: được gì, mất gì, trade-off, việc phát sinh (link backlog ID nếu có).

## Status
Proposed · Accepted · Superseded (bởi ADR-NNNN) · Deprecated. + điều kiện đổi trạng thái.

## Date
YYYY-MM-DD (ngày chốt; cập nhật khi đổi Status).
```

Quy ước:

- **Status** hợp lệ: `Proposed` · `Accepted` · `Superseded` · `Deprecated`. Không xoá ADR cũ — đánh `Superseded`/`Deprecated` + trỏ ADR thay thế (giữ lịch sử quyết định).
- **Đánh số** không tái sử dụng; ADR mới luôn lấy số kế tiếp.
- Tạo ADR mới ⇒ **thêm 1 dòng vào bảng Index dưới đây CÙNG commit** (R1 — cấm doc mồ côi) và thêm vào `docs/README.md`.

## Index

| ADR | Tiêu đề | Status | Ngày |
|-----|---------|--------|------|
| [0001](0001-nav-source-strategy.md) | Chiến lược nguồn dẫn đường per-app (GMaps notif · VietMap widget+a11y · Waze screen-capture) | Accepted | 2026-08-19 |
| [0002](0002-hud-nav-coding-locked.md) | HUD kính ZIN nav = gate firmware (nghi phạm `0x38B00030`, CHƯA bác), không phải app · xe anh em là HUD **Taobao** (đường độc lập, không bác được cờ zin) · sửa 2 lần 2026-08-19 | Accepted (sửa ×2) | 2026-08-19 |
| [0003](0003-datacollection-logging-default-off.md) | Thu thập dữ liệu (log + ảnh) mặc định OFF + storage cap ~150 MB | Accepted | 2026-08-19 |
