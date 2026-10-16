package com.byd.clusternav.launcher

/**
 * ═══ 3.1.11 · DOCK-LABELS-310 (phần ảnh cũ) — nguồn trạng thái xe của MỘT bề mặt tự dựng lại (thanh nút) ═══════════════════════
 *
 * Luật 3.1.10 EVENT-PATH-SCOPED gộp HAI việc vào một cổng ở `KachiHomeRender`: *"có đổ lại ô không"* và *"thanh có nhận ảnh mới
 * không"*. Đổi không chạm tập thanh bày ⇒ thanh KHÔNG nhận ảnh ⇒ ảnh trong thanh đứng ở lượt chạm cuối. Thanh tự dựng lại ô từ ảnh
 * của chính nó (`ControlDockView.rebuild` khi đổi cấu hình · đổi hồ sơ · đổi chủ đề) ⇒ ô MỚI thêm vào thanh (datum chưa từng thuộc
 * tập) dựng từ số CŨ và giữ số cũ tới lần datum ấy tình cờ đổi lại [ĐO source, `docs/diagnostics/dock-labels-310-2026-10-16.md` §3].
 * `WorkspaceView` không có lỗ này: nó gán `carStatus = status` MỌI lượt rồi mới hỏi `delta.touches` — lớp này là đúng hình ấy.
 *
 * Luật (thuần, một chỗ): [offer] LUÔN giữ ảnh mới làm [latest] (một phép gán — 0 cấp phát, 0 vẽ), và chỉ trả `true` (đổ lại ô) khi
 * đổi chạm tập bề mặt bày ([CarDataDemand.interestOf], `null` = không biết ⇒ đổ). [scope] = bề mặt đổi nội dung ⇒ tập mới (tính MỘT
 * lần mỗi lần đổi cấu hình, không mỗi sự kiện như bản 3.1.10). Khoá: `SurfaceFeedTest`.
 *
 * Không khoá, không `@Volatile`: sống cùng view của bề mặt và chỉ được gọi trên luồng chính (`render` · `resyncTiles` · Cài đặt) —
 * như mọi trường của `View`. Gọi từ luồng khác là lỗi của chỗ gọi, không phải việc lớp này phải chịu chi phí đồng bộ mỗi sự kiện.
 */
class SurfaceFeed(ids: Collection<String>) {
    private var interest: CarDataDemand.Interest? = CarDataDemand.interestOf(ids)

    /** Ảnh trạng thái xe MỚI NHẤT mà bề mặt từng được đưa — mọi lần dựng ô đọc từ đây. */
    var latest: CarStatus = CarStatus()
        private set

    /** Bề mặt đổi nội dung (cấu hình thanh) ⇒ tập bày mới cho các lượt [offer] sau. */
    fun scope(ids: Collection<String>) { interest = CarDataDemand.interestOf(ids) }

    /** Giữ [next] làm [latest] (LUÔN); `true` ⇒ đổi chạm tập bề mặt bày ⇒ đổ lại ô. */
    fun offer(next: CarStatus): Boolean {
        val touched = CarStatusDelta.of(latest, next).touches(interest)
        latest = next
        return touched
    }
}
