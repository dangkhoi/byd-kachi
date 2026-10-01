package com.byd.clusternav.launcher

import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byd.clusternav.launcher.KachiBars as Bars
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Sắp [workspace] + [dock] trong vùng chính theo viền [DockConfig.edge] (BOTTOM/TOP/LEFT/RIGHT) — tách khỏi
 * [KachiHomeActivity] (B5b). Cùng orientation, cùng LayoutParams, cùng gap như `layoutMainArea()` cũ.
 *
 * ⚠⚠ PROFILE-SWITCH-SLOTS · R-A1 (2026-10-01): **KHÔNG BAO GIỜ tháo [workspace] khỏi `mainArea`.** Bản 1.55–2.84 gỡ
 * vùng ô rồi gắn lại mỗi lần đổi viền/ẩn-hiện ⇒ cả cây ô nhận `onDetachedFromWindow` ⇒ `VdAppHost.release()` ⇒ ô app
 * **đen mãi** khi đổi giữa hai hồ sơ khác cạnh thanh nút [ĐO máy ảo]. Nay thứ tự các bước do [DockAreaPlan] (thuần,
 * `:core`) quyết: chỉ khung cuộn của thanh nút bị tháo/gắn; vùng ô chỉ đổi chiều xếp + tham số bố trí. Gọi lại được
 * nhiều lần (lần dựng đầu ở `onCreate`: `mainArea` rỗng ⇒ gắn cả hai).
 */
object DockAreaLayout {

    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    /** Áp bố cục: [mainArea] chứa [workspace] (giãn) + [dock] (cố định) theo [cfg]. [density] = displayMetrics.density. */
    fun apply(mainArea: LinearLayout, workspace: View, dock: View, cfg: DockConfig, density: Float) {
        fun dp(v: Int): Int = (v * density).toInt()
        val target = DockAreaPlan.target(cfg)
        // Thanh nút rời khung cuộn cũ (khung cũ cuộn theo trục cũ; viền mới có thể khác trục) — như bản trước.
        (dock.parent as? ViewGroup)?.removeView(dock)
        val current = (0 until mainArea.childCount).map {
            if (mainArea.getChildAt(it) === workspace) DockAreaChild.WORKSPACE else DockAreaChild.DOCK
        }
        mainArea.orientation = if (target.vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        // S1b — thanh ẩn: vùng ô lấp trọn màn, KHÔNG gắn dock (giữ nguyên viền/nút đã chọn trong cfg để hiện lại).
        val wsLp = when {
            !target.dockShown -> LinearLayout.LayoutParams(MATCH, MATCH)
            target.vertical -> LinearLayout.LayoutParams(MATCH, 0, 1f)
            else -> LinearLayout.LayoutParams(0, MATCH, 1f)
        }
        val dockLp = if (target.vertical) LinearLayout.LayoutParams(MATCH, dp(Bars.DOCK_THICK)) else LinearLayout.LayoutParams(dp(Bars.DOCK_WIDE), MATCH)
        val gap = dp(Sp.SLOT_GAP)
        when (cfg.edge) {
            DockEdge.BOTTOM -> dockLp.topMargin = gap
            DockEdge.TOP -> dockLp.bottomMargin = gap
            DockEdge.LEFT -> dockLp.marginEnd = gap
            DockEdge.RIGHT -> dockLp.marginStart = gap
        }
        for (step in DockAreaPlan.steps(current, target)) {
            when (step) {
                is DockAreaStep.Remove -> mainArea.removeViewAt(step.index)
                DockAreaStep.AddWorkspace -> mainArea.addView(workspace, wsLp)
                // Nhiều nút hơn chiều dài/cao của dock ⇒ CUỘN, không cắt cụt — xem [scrollWrap].
                is DockAreaStep.AddDock ->
                    mainArea.addView(scrollWrap(dock, cfg.isVertical()), if (step.atStart) 0 else mainArea.childCount, dockLp)
            }
        }
        // Vùng ô đã ở sẵn ⇒ CHỈ đổi tham số bố trí (setLayoutParams chỉ `requestLayout()`, không tháo — AOSP ở KDoc
        // [DockAreaPlan]). Ô đổi cỡ đi đường `VdAppHost.resize` như mọi lượt bố trí lại.
        workspace.layoutParams = wsLp
    }

    /**
     * Bọc [dock] trong khung cuộn để nút tràn quá chiều dock thì **cuộn được**, không bị cắt.
     *
     * - [dockVertical] = true (viền TRÁI/PHẢI, dock xếp DỌC) ⇒ [ScrollView], con WRAP cao / MATCH rộng.
     * - false (viền TRÊN/DƯỚI, dock xếp NGANG) ⇒ [HorizontalScrollView], con WRAP rộng / MATCH cao.
     *
     * `isFillViewport = true`: ít nút thì con giãn lấp trọn khung (giữ căn như bản không-cuộn); chỉ khi tổng
     * cỡ con vượt khung mới sinh cuộn. Tắt thanh cuộn (màn xe — thanh cuộn nhấp nháy nhìn rối).
     */
    private fun scrollWrap(dock: View, dockVertical: Boolean): View {
        val childLp = if (dockVertical) {
            LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT)
        } else {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, MATCH)
        }
        val ctx = dock.context
        return if (dockVertical) {
            ScrollView(ctx).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(dock, childLp)
            }
        } else {
            HorizontalScrollView(ctx).apply {
                isFillViewport = true
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(dock, childLp)
            }
        }
    }
}
