package com.byd.clusternav.launcher

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
        val dockLp = if (vertical) LinearLayout.LayoutParams(MATCH, dp(Sp.DOCK_THICK)) else LinearLayout.LayoutParams(dp(Sp.DOCK_WIDE), MATCH)
        val gap = dp(Sp.SLOT_GAP)
        when (cfg.edge) {
            DockEdge.BOTTOM -> { mainArea.addView(workspace, wsLp); dockLp.topMargin = gap; mainArea.addView(dock, dockLp) }
            DockEdge.TOP -> { dockLp.bottomMargin = gap; mainArea.addView(dock, dockLp); mainArea.addView(workspace, wsLp) }
            DockEdge.LEFT -> { dockLp.marginEnd = gap; mainArea.addView(dock, dockLp); mainArea.addView(workspace, wsLp) }
            DockEdge.RIGHT -> { mainArea.addView(workspace, wsLp); dockLp.marginStart = gap; mainArea.addView(dock, dockLp) }
        }
    }
}
