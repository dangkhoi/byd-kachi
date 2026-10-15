package com.byd.clusternav.launcher

/**
 * ═══ PERF 3.1.5 — widget có ĐANG BÀY nội dung không, lúc chạy (backlog `CAR3D-FALLBACK-DEMAND` → `CAR3D-FALLBACK-STICKY`) ═══════
 *
 * [CarDataDemand.of] suy nhu cầu từ [HomeUiState] — mà state không biết một widget đang KHUẤT dù vẫn gắn cửa sổ (ô khác bung R30,
 * màn chính không ở trước, màn tắt) hay đang KHÔNG VẼ (xe 3D chờ chỗ Engine / ô nhỏ / lỗi chờ thử lại / không khả thi trên xe này).
 * Cả hai: không bày số nào ⇒ không đọc gì ([ĐO off-car `HalLoadBudgetTest`]).
 *
 * 3.1.8 · `CAR3D-FALLBACK-STICKY` (owner 15/10 *"không bao giờ lùi về 2D"*): bỏ hẳn trạng thái "bày bản lùi 2D" (`degraded`) —
 * widget xe 3D không còn bản lùi. Sổ còn MỘT câu: *"mã nào không bản nào đang bày"* ([hidden]) — một mã vào [hidden] khi mọi bản
 * đang gắn đều khuất HOẶC không vẽ. Bản vừa gắn = hiện, đang vẽ (fail-open: đọc đủ tới khi chắc là khuất/không vẽ).
 *
 * Chỉ luồng chính gọi (vòng đời view); `@Synchronized` cho chắc khi test gọi từ luồng khác. [onChange] báo mỗi lần tập đổi.
 */
class WidgetFallbacks {
    private class Entry(val id: String, var hidden: Boolean = false, var idle: Boolean = false)
    private val entries = HashMap<Any, Entry>()
    @Volatile private var hiddenNow: Set<String> = emptySet()
    @Volatile var onChange: (hidden: Set<String>) -> Unit = { }

    /** Tập mã widget mà mọi bản đang gắn đều KHUẤT hoặc KHÔNG VẼ — không bày gì ⇒ không đọc gì. */
    fun hidden(): Set<String> = hiddenNow

    /** Một bản [token] của widget [id] vừa gắn cửa sổ; [rendering] = nó có đang vẽ không (gắn lại một bản đang chờ ⇒ `false`). */
    @Synchronized fun attach(token: Any, id: String, rendering: Boolean = true) { entries[token] = Entry(id, idle = !rendering); recompute() }

    /** Bản [token] vừa thôi vẽ / vẽ lại (xe 3D: chờ chỗ · ô nhỏ · lỗi · không khả thi). Bản chưa gắn ⇒ bỏ qua (lần [attach] sau mang đúng). */
    @Synchronized fun rendering(token: Any, on: Boolean) { entries[token]?.let { it.idle = !on; recompute() } }

    /** Bản [token] vừa khuất ([shown] `false`) / hiện lại. Bản chưa gắn ⇒ bỏ qua. */
    @Synchronized fun shown(token: Any, shown: Boolean) { entries[token]?.let { it.hidden = !shown; recompute() } }

    /** Bản [token] rời cửa sổ — không còn bày gì. */
    @Synchronized fun detach(token: Any) { if (entries.remove(token) != null) recompute() }

    private fun recompute() {
        val showing = entries.values.filter { !it.hidden && !it.idle }.mapTo(HashSet()) { it.id }
        val hidden = entries.values.mapTo(HashSet()) { it.id } - showing
        if (hidden == hiddenNow) return
        hiddenNow = hidden
        onChange(hidden)
    }

    companion object {
        /** Sổ của tiến trình (màn chính). Test dựng bản riêng. */
        val shared = WidgetFallbacks()
    }
}
