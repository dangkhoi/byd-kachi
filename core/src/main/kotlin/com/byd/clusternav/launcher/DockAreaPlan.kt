package com.byd.clusternav.launcher

/**
 * ═══ PROFILE-SWITCH-SLOTS · R-A1 — sắp vùng ô + thanh nút mà KHÔNG BAO GIỜ tháo vùng ô ════════════════════════════
 *
 * ## Bệnh nó chữa — [ĐO máy ảo + mã 2026-10-01] spec `docs/specs/kachi-profile-switch-slots.html` §2.3
 * Đổi giữa hai hồ sơ khác cạnh (hoặc khác ẩn/hiện) thanh nút ⇒ ô app **đen mãi**. Bản cũ của `DockAreaLayout.apply`
 * dựng lại `mainArea` bằng `removeView(workspace)` + `removeAllViews()`. Tháo một view khỏi cha đang gắn cửa sổ luôn
 * đẩy `dispatchDetachedFromWindow()` xuống CẢ cây con [ĐO AOSP: A10 `ViewGroup.java:5430-5431, 3806`; A12
 * `:5571-5572, 3948`] ⇒ `WorkspaceView.onDetachedFromWindow` → `VdAppHost.release()` (`released = true` + `am
 * force-stop`) ⇒ gắn lại thì `surfaceChanged` gặp `if (released) return` ⇒ màn ảo không bao giờ được tạo.
 *
 * ## Luật
 * Chỉ **thanh nút** (cùng khung cuộn bọc nó) được tháo/gắn lại. Vùng ô ở yên trong `mainArea`; đổi viền chỉ đổi
 * chiều xếp + thứ tự + tham số bố trí — cả ba làm được mà không tháo nó [ĐO AOSP: `addView(child, index, …)` chỉ gắn
 * đúng con mới A10 `ViewGroup.java:5145-5149` / A12 `:5286-5290`; `View.setLayoutParams` và
 * `LinearLayout.setOrientation` chỉ `requestLayout()` — A10 `View.java:17268-17278` / A12 `:18445-18455`,
 * `LinearLayout.java:1839-1844` cả hai đời].
 *
 * Bất biến (khoá ở `DockAreaPlanTest`, 64 cặp chuyển): **không bước nào tháo [DockAreaChild.WORKSPACE]**; thứ tự con
 * sau khi chạy đủ các bước = [DockAreaTarget.order]. Thuần Kotlin — `:app` chỉ ánh xạ từng bước sang một lời gọi
 * `ViewGroup` (xem `DockAreaLayout.apply`).
 */
enum class DockAreaChild { WORKSPACE, DOCK }

/**
 * Bố cục đích của `mainArea`.
 *
 * @property vertical `mainArea` xếp DỌC (thanh ở TRÊN/DƯỚI, hoặc thanh ẩn) — `false` = xếp NGANG (thanh TRÁI/PHẢI).
 * @property order thứ tự con từ đầu tới cuối. Luôn có đúng một [DockAreaChild.WORKSPACE].
 */
data class DockAreaTarget(val vertical: Boolean, val order: List<DockAreaChild>) {
    /** Thanh nút có hiện không (ẩn ⇒ vùng ô lấp trọn `mainArea`). */
    val dockShown: Boolean get() = DockAreaChild.DOCK in order
}

/** Một bước sửa con của `mainArea`. Chỉ số trong [Remove] là chỉ số **tại lúc thi hành** bước đó. */
sealed interface DockAreaStep {
    /** Tháo con ở [index]. Bất biến: không bao giờ trỏ vào vùng ô. */
    data class Remove(val index: Int) : DockAreaStep

    /** Gắn vùng ô vào CUỐI — chỉ khi `mainArea` chưa có nó (lần dựng đầu ở `onCreate`). */
    data object AddWorkspace : DockAreaStep

    /** Gắn thanh nút (đã bọc khung cuộn) vào ĐẦU ([atStart]) hoặc CUỐI. */
    data class AddDock(val atStart: Boolean) : DockAreaStep
}

object DockAreaPlan {

    /**
     * Đích theo [cfg] — đúng thứ tự và chiều của bản cũ (`DockAreaLayout.kt` 2.84 dòng 28-47): ẩn ⇒ `[W]` dọc ·
     * DƯỚI ⇒ `[W, D]` dọc · TRÊN ⇒ `[D, W]` dọc · TRÁI ⇒ `[D, W]` ngang · PHẢI ⇒ `[W, D]` ngang.
     */
    fun target(cfg: DockConfig): DockAreaTarget {
        val w = DockAreaChild.WORKSPACE
        val d = DockAreaChild.DOCK
        if (!cfg.visible) return DockAreaTarget(vertical = true, order = listOf(w))
        return when (cfg.edge) {
            DockEdge.BOTTOM -> DockAreaTarget(vertical = true, order = listOf(w, d))
            DockEdge.TOP -> DockAreaTarget(vertical = true, order = listOf(d, w))
            DockEdge.LEFT -> DockAreaTarget(vertical = false, order = listOf(d, w))
            DockEdge.RIGHT -> DockAreaTarget(vertical = false, order = listOf(w, d))
        }
    }

    /**
     * Các bước đưa con hiện có [current] của `mainArea` về [target].
     *
     * 1. Tháo mọi con **không phải** vùng ô (khung cuộn cũ của thanh nút), duyệt từ cuối lên để chỉ số các bước sau
     *    vẫn đúng.
     * 2. Vùng ô chưa có (lần dựng đầu) ⇒ gắn nó. Đã có ⇒ **không đụng** — sau bước 1 nó là con duy nhất.
     * 3. Thanh hiện ⇒ gắn thanh ở đầu hoặc cuối theo [DockAreaTarget.order].
     */
    fun steps(current: List<DockAreaChild>, target: DockAreaTarget): List<DockAreaStep> {
        val out = ArrayList<DockAreaStep>()
        for (i in current.indices.reversed()) {
            if (current[i] != DockAreaChild.WORKSPACE) out.add(DockAreaStep.Remove(i))
        }
        if (DockAreaChild.WORKSPACE !in current) out.add(DockAreaStep.AddWorkspace)
        if (target.dockShown) out.add(DockAreaStep.AddDock(atStart = target.order.first() == DockAreaChild.DOCK))
        return out
    }
}
