package com.byd.clusternav.launcher

import android.os.SystemClock
import android.util.Log
import android.view.View
import com.byd.clusternav.launcher.SlotExpand.Change

/**
 * ═══ R30 — BUNG Ô: phần VIEW của [SlotExpand] cho MỘT cây [WorkspaceView] ═══════════════════════════════════════════
 *
 * Owner 14/10: *"chỉ bung khung đó ra full screen của launcher thôi, ẩn các khung khác đi, khi nhấn lại thì scale về lại như
 * cũ"*. Spec `kachi-301-cast-steps-dialog-ia.html` R30 / §4.8. Luật (ô nào, khung nào, một ô một lúc) ở `:core` [SlotExpand];
 * ở đây chỉ THI HÀNH lên view:
 *  - **Bung** — khung khác `INVISIBLE` (không đo lại, không chạm, không vào cây trợ năng) + [VdAppHost.veil] cho mặt vẽ màn ảo
 *    của chúng (SurfaceView không theo `visibility` của cha — [ĐO nguồn] KDoc [VdAppHost.veil]). App trong đó chạy tiếp: không
 *    nhả màn ảo, không lệnh nào. Khung ô bung đổi ở `WorkspaceView.effectiveRects` ⇒ lượt đo/bố cục sẵn có tự đo lại widget
 *    và gọi `VdAppHost.resize` (đường DUY NHẤT, luật mật độ như hôm nay) — đúng MỘT lượt đổi cỡ.
 *  - **Thu** — gỡ che ĐÚNG những host chính lớp này đã che, hiện lại mọi khung, cùng lượt đo/bố cục đó ⇒ một lượt đổi cỡ ngược.
 *
 * TẠM (R30.3): trạng thái nằm trong RAM của cây view này — không prefs, không ViewModel, không marker; tiến trình / màn chính
 * dựng lại ⇒ bố cục thường. 0 lệnh shell; không `Handler`, không hẹn giờ: thời gian đo từ lúc đổi tới cuối `onLayout` kế tiếp
 * ([laidOut]) và ghi MỘT dòng `KachiWorkspace`. Luồng chính.
 */
internal class WorkspaceExpand(private val ws: WorkspaceView) {

    private val state = SlotExpand()

    /** Host mà lượt bung đã che mặt vẽ — lượt thu gỡ ĐÚNG những host này (host đang *tắt* / đã chết không bị hiện lại). */
    private val veiled = ArrayList<VdAppHost>()

    /** Dòng log chờ `onLayout` kế: động từ, ô, lý do thu (`null` = bung), mốc `uptimeMillis`. */
    private class Pending(val verb: String, val index: Int, val why: SlotExpand.Why?, val t0: Long)

    private var pending: Pending? = null

    /** Ô đang bung (0-based); `null` = bố cục thường. */
    val expanded: Int? get() = state.expanded

    /** Khung đang hiệu lực từ bố cục [base] trong vùng [w]×[h] — `WorkspaceView.effectiveRects` gọi. */
    fun rects(base: List<SlotRect>, w: Int, h: Int): List<SlotRect> = state.rects(base, w, h)

    /** Nút *Bung to* / *Thu về* của ô [index]. `false` = không đổi gì (bố cục 1 ô / ô khác đang bung). */
    fun toggle(index: Int): Boolean = apply(state.toggle(index, ws.slotViews.size))

    /** Thu (Back · HOME · đổi hồ sơ · đổi bố cục · dựng lại khung). Không có ô bung ⇒ không làm gì. Gọi TRƯỚC khi tháo view. */
    fun drop(why: SlotExpand.Why): Boolean = apply(state.clear(why))

    private fun apply(c: Change): Boolean {
        when (c) {
            Change.None -> return false
            is Change.Expand -> ws.slotViews.forEachIndexed { i, v ->
                if (i == c.index) return@forEachIndexed
                v.visibility = View.INVISIBLE
                ws.hostAt(i)?.let { h -> if (h.veil(true)) veiled += h }
            }
            is Change.Collapse -> {
                veiled.forEach { it.veil(false) }
                veiled.clear()
                ws.slotViews.forEach { it.visibility = View.VISIBLE }
            }
        }
        val t0 = SystemClock.uptimeMillis()
        pending = if (c is Change.Expand) Pending("bung", c.index, null, t0) else (c as Change.Collapse).let { Pending("thu", it.index, it.why, t0) }
        ws.requestLayout(); ws.invalidate()
        ws.heads.refreshAll()   // glyph ⤢ ↔ ⤡ đổi ngay, không đợi lần chạm sau
        return true
    }

    /** Cuối `WorkspaceView.onLayout` (sau `pushSlotSize`): ghi dòng chờ một lần — đo + bố cục + `VirtualDisplay.resize`. */
    fun laidOut() {
        val p = pending ?: return
        pending = null
        Log.i(TAG, "${p.verb} ô ${p.index} · ${SystemClock.uptimeMillis() - p.t0} ms" + (p.why?.let { " ($it)" } ?: ""))
    }

    private companion object { const val TAG = "KachiWorkspace" }
}
