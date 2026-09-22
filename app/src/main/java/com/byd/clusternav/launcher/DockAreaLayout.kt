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
 * [KachiHomeActivity] (B5b). Byte-giữ so với `layoutMainArea()` cũ: cùng orientation, cùng LayoutParams, cùng gap.
 *
 * Gỡ [workspace]/[dock] khỏi cha cũ trước khi gắn lại (đổi viền ⇒ đổi thứ tự/chiều) nên gọi lại được nhiều lần.
 */
object DockAreaLayout {

    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    /** Áp bố cục: [mainArea] chứa [workspace] (giãn) + [dock] (cố định) theo [cfg]. [density] = displayMetrics.density. */
    fun apply(mainArea: LinearLayout, workspace: View, dock: View, cfg: DockConfig, density: Float) {
        fun dp(v: Int): Int = (v * density).toInt()
        (workspace.parent as? ViewGroup)?.removeView(workspace)
        (dock.parent as? ViewGroup)?.removeView(dock)
        mainArea.removeAllViews()
        // S1b — thanh ẩn: vùng ô lấp trọn màn, KHÔNG gắn dock (giữ nguyên viền/nút đã chọn trong cfg để hiện lại).
        if (!cfg.visible) {
            mainArea.orientation = LinearLayout.VERTICAL
            mainArea.addView(workspace, LinearLayout.LayoutParams(MATCH, MATCH))
            return
        }
        val vertical = !cfg.isVertical()
        mainArea.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        val wsLp = if (vertical) LinearLayout.LayoutParams(MATCH, 0, 1f) else LinearLayout.LayoutParams(0, MATCH, 1f)
        val dockLp = if (vertical) LinearLayout.LayoutParams(MATCH, dp(Bars.DOCK_THICK)) else LinearLayout.LayoutParams(dp(Bars.DOCK_WIDE), MATCH)
        // Nhiều nút hơn chiều dài/cao của dock ⇒ CUỘN, không cắt cụt. Bọc dock trong khung cuộn đúng trục:
        // viền TRÊN/DƯỚI (dock nằm ngang) → cuộn ngang; viền TRÁI/PHẢI (dock dọc) → cuộn dọc. `fillViewport` để
        // khi ít nút thì dock vẫn lấp trọn khung (căn như cũ), chỉ khi tràn mới cuộn. Thanh cuộn tắt (màn xe).
        val scroller = scrollWrap(dock, cfg.isVertical())
        val gap = dp(Sp.SLOT_GAP)
        when (cfg.edge) {
            DockEdge.BOTTOM -> { mainArea.addView(workspace, wsLp); dockLp.topMargin = gap; mainArea.addView(scroller, dockLp) }
            DockEdge.TOP -> { dockLp.bottomMargin = gap; mainArea.addView(scroller, dockLp); mainArea.addView(workspace, wsLp) }
            DockEdge.LEFT -> { dockLp.marginEnd = gap; mainArea.addView(scroller, dockLp); mainArea.addView(workspace, wsLp) }
            DockEdge.RIGHT -> { mainArea.addView(workspace, wsLp); dockLp.marginStart = gap; mainArea.addView(scroller, dockLp) }
        }
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
