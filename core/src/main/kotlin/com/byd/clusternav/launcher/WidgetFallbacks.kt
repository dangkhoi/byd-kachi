package com.byd.clusternav.launcher

/**
 * ═══ PERF 3.1.5 — widget ĐANG BÀY GÌ lúc chạy: bản đầy đủ · bản lùi 2D · không hiện (backlog `CAR3D-FALLBACK-DEMAND`) ═════════
 *
 * [CarDataDemand.of] suy nhu cầu từ [HomeUiState] — mà state không biết một widget có lùi bản 2D lúc chạy (xe không mô hình, máy
 * RAM thấp, GLES < 3, widget 3D thứ hai thua chỗ Engine) hay đang bị KHUẤT dù vẫn gắn cửa sổ (ô khác bung R30, màn chính không ở
 * trước, màn tắt). [ĐO off-car `HalLoadBudgetTest`] xe 3D lùi vẫn đọc 4 nút ẩn ở nhịp NHANH (1 Hz) + lốp + cửa/đèn/ghế; khuất thì
 * không bày gì mà vẫn đọc đủ.
 *
 * Sổ này là nguồn DUY NHẤT của hai câu: *"mã widget nào CHỈ đang bày bản lùi"* ([degraded]) và *"mã nào không bản nào đang hiện"*
 * ([hidden]). Một mã vào [degraded] khi không bản nào HIỆN ở dạng đầy đủ mà có ≥ 1 bản HIỆN ở dạng lùi; vào [hidden] khi mọi bản
 * đang gắn đều khuất. Bản vừa gắn = hiện, đầy đủ (fail-open: đọc đủ tới khi chắc là lùi/khuất).
 *
 * Chỉ luồng chính gọi (vòng đời view); `@Synchronized` cho chắc khi test gọi từ luồng khác. [onChange] báo mỗi lần một tập đổi.
 */
class WidgetFallbacks {
    private class Entry(val id: String, var fallen: Boolean, var hidden: Boolean = false)
    private val entries = HashMap<Any, Entry>()
    @Volatile private var degradedNow: Set<String> = emptySet()
    @Volatile private var hiddenNow: Set<String> = emptySet()
    @Volatile var onChange: (degraded: Set<String>, hidden: Set<String>) -> Unit = { _, _ -> }

    /** Tập mã widget mà mọi bản ĐANG HIỆN đều bày bản lùi. */
    fun degraded(): Set<String> = degradedNow

    /** Tập mã widget mà mọi bản đang gắn đều KHUẤT — không bày gì ⇒ không đọc gì. */
    fun hidden(): Set<String> = hiddenNow

    /** Một bản [token] của widget [id] vừa gắn cửa sổ; [isFallen] = nó đã lùi từ trước (gắn lại). */
    @Synchronized fun attach(token: Any, id: String, isFallen: Boolean) { entries[token] = Entry(id, isFallen); recompute() }

    /** Bản [token] vừa lùi 2D. Bản CHƯA gắn thì bỏ qua — lần [attach] sau mang `isFallen` đúng (không để mục mồ côi). */
    @Synchronized fun fell(token: Any, id: String) { entries[token]?.takeIf { it.id == id }?.let { it.fallen = true; recompute() } }

    /** Bản [token] vừa khuất ([shown] `false`) / hiện lại. Bản chưa gắn ⇒ bỏ qua. */
    @Synchronized fun shown(token: Any, shown: Boolean) { entries[token]?.let { it.hidden = !shown; recompute() } }

    /** Bản [token] rời cửa sổ — không còn bày gì. */
    @Synchronized fun detach(token: Any) { if (entries.remove(token) != null) recompute() }

    private fun recompute() {
        val visible = entries.values.filter { !it.hidden }
        val full = visible.filter { !it.fallen }.mapTo(HashSet()) { it.id }
        val degraded = visible.filter { it.fallen }.mapTo(HashSet()) { it.id } - full
        val hidden = entries.values.mapTo(HashSet()) { it.id } - visible.mapTo(HashSet()) { it.id }
        if (degraded == degradedNow && hidden == hiddenNow) return
        degradedNow = degraded; hiddenNow = hidden
        onChange(degraded, hidden)
    }

    companion object {
        /** Sổ của tiến trình (màn chính). Test dựng bản riêng. */
        val shared = WidgetFallbacks()
    }
}
