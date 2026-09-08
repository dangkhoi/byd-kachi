# 0003 — Thu thập dữ liệu (log + ảnh) mặc định OFF + storage cap ~150 MB

> **Trạng thái**: Accepted · **Ngày**: 2026-08-19 · **Mục đích**: Chốt data-collection là opt-in (mặc định TẮT) + cap bộ nhớ ~150 MB luôn-bật, để app không bao giờ làm tràn bộ nhớ xe.

## Context

Bản data-collection trước ép bật verbose logging + diagnostic screenshots bằng `|| BuildConfig.DEBUG`, khiến app **ghi liên tục** và **làm đầy bộ nhớ xe 7 GB+** (`nav_arrow_pngs` + `diag` screenshots + nhiều CSV). Với người dùng bình thường, đây là hành vi sai: máy chỉ chạy nav mà vẫn tích dữ liệu chẩn đoán vô giới hạn.

Bằng chứng (`docs/_handoff/stage-logging-off-done.md`; backlog **A8**, commit `11751ba`):

- `NavLog.verbose` từng = `Prefs.navVerboseLog(ctx) || BuildConfig.DEBUG` → auto-on ở build DEBUG.
- Có ~9 writer chẩn đoán ghi đĩa: `NavNotifLog` · `NavNotifRawLog` · `NavAccessLog` · `VietMapSignalLog` · `NavDistanceLog` · `NavArrowLog` (+ dump PNG mũi tên) · `SegmentShotCapturer` (screenshot cụm/màn) · `VietMapWidgetVerboseLog` (PNG alert).
- Cụm/xe không có cơ chế nào chặn app điền đầy bộ nhớ; owner phải tự dọn.

## Decision

1. **Data-collection = opt-in, mặc định TẮT.** Bỏ `|| BuildConfig.DEBUG`; `NavLog.verbose` **chỉ** do pref bền `Prefs.navVerboseLog` điều khiển (default **false**). Cài mới/bình thường → verbose=false → **mọi writer chẩn đoán early-return** → không log, không PNG, không screenshot. Có **công tắc hiện rõ** "Thu thập dữ liệu chẩn đoán (log + ảnh)" (mặc định OFF) + long-press version label route qua đúng công tắc đó (một nguồn sự thật).
2. **Storage cap ~150 MB, luôn-bật kể cả khi verbose OFF.** `StorageCapPlanner` (`:core`, thuần, unit-tested) với `DEFAULT_CAP_BYTES = 150 MB`, `selectForDeletion` xoá **cũ nhất trước** tới khi phần còn lại ≤ cap. `DiagStorageCap.enforce` chạy off-thread, throttle 60 s, bọc `runCatching` (không bao giờ ném vào nav); gọi ở session start (`force=true`, kể cả verbose OFF — dọn rác phiên verbose trước để lại), định kỳ khi đang log, và khi bật công tắc.

## Consequences

- **Được:** dùng bình thường **KHÔNG thu thập gì**; hết cảnh đầy bộ nhớ 7 GB+; privacy mạnh hơn (không có dữ liệu trừ khi owner chủ động bật). Cap là **backstop phòng thủ** — app không bao giờ điền đầy bộ nhớ xe.
- **Mất / trade-off (ghi rõ):** mất **auto-collect** — owner phải **bật công tắc trước** mỗi chuyến thu dữ liệu; và một chuyến sinh **> 150 MB** sẽ bị xoá file cũ nhất **ngay cả trước khi owner kịp pull** (hành vi "không bao giờ tràn" cố ý → pull sớm). Dưới 150 MB giữ nguyên.
- **Chất lượng:** cap thuần + unit-test (`StorageCapPlannerTest`, 10 case: under/at/over cap, oldest-first, fewest-deletions, tie-break id, floor size âm, input rỗng, cap âm, default 150 MB). Writer ngoài phạm vi (cast `castlog/` TEE tự giới hạn 5 file; `ClusterDiag` "Chẩn đoán" one-shot) vẫn bị cap bao. OTA APK ở `filesDir/update` (internal) — không phải ứng viên cap.
- Gate xanh: `:core` 543 test · `:app` 392 test, 0 fail.

## Status

Accepted — đã implement (commit `11751ba`, A8, gate GREEN). Đổi cap/hành vi phải qua ADR mới (hoặc §Nhật ký triển khai của spec nếu chỉ tinh chỉnh nội bộ 1 feature).

## Date

2026-08-19

---

**Tham chiếu:** `docs/_handoff/stage-logging-off-done.md` · `docs/PROJECT-BACKLOG.md` (A8) · `core/.../StorageCapPlanner.kt`(+Test) · `app/.../DiagStorageCap.kt` · `app/.../NavLog.kt` · `app/.../MainActivity.kt` (`setDiagLogging`).
