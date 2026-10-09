package com.byd.clusternav.launcher

import android.graphics.drawable.GradientDrawable
import android.view.View
import com.byd.clusternav.R
import com.byd.clusternav.UpdateDotStore
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * 2.98 · OTA-UPDATE-DOT — chấm nhỏ góc trên-phải của một view (nút ⚙ thanh trên · hàng "Kiểm tra cập nhật") khi kênh có
 * bản mới ([UpdateDotStore.shows]). VẼ bằng `ViewOverlay` ⇒ không đổi bố cục/cỡ view, không thêm view con, không đụng
 * nền/màu chủ đề của view gốc (thanh trên giữ đúng hợp đồng `themed { }`).
 *
 * Làm mới: lúc view GẮN vào cửa sổ (mở màn chính / mở Cài đặt) + mỗi khi bản ghi đổi (lượt dò xong). Tháo khỏi cửa sổ ⇒
 * gỡ listener (không giữ view chết). Không vòng hỏi, không hẹn giờ — chi phí 0 khi không có lượt dò.
 */
object UpdateDotBadge {

    /** Gắn chấm vào [v]. Gọi MỘT lần lúc dựng view; idempotent theo tag. */
    fun bind(v: View) {
        if (v.getTag(R.id.kachi_update_dot) != null) return
        val dot = Dot()
        val refresh = { apply(v, dot) }
        v.setTag(R.id.kachi_update_dot, dot)
        v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { UpdateDotStore.listen(refresh); refresh() }
            override fun onViewDetachedFromWindow(view: View) { UpdateDotStore.unlisten(refresh) }
        })
        v.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) place(v, dot)
        }
        if (v.isAttachedToWindow) { UpdateDotStore.listen(refresh); refresh() }
    }

    /**
     * Senior review Pass 10 [P2] — tô lại chấm theo bảng màu HIỆN TẠI (gọi từ `themed { }` của thanh trên). Đổi chủ đề tại chỗ
     * (`configChanges=uiMode`, 2.96 — không dựng lại view) không qua [bind] ⇒ quầng [KachiTheme.BAR_TOP] chụp lúc dò giữ màu thanh
     * CŨ ⇒ một vòng sáng/tối lệch quanh chấm. Chưa [bind] ⇒ không làm gì.
     */
    fun refresh(v: View) {
        val dot = v.getTag(R.id.kachi_update_dot) as? Dot ?: return
        apply(v, dot)
    }

    /**
     * Hai đĩa FILL chồng nhau (luật không-viền `ZeroBorderContractTest`): quầng màu thanh [KachiTheme.BAR_TOP] rộng hơn
     * [Sp.HAIRLINE] mỗi phía ⇒ chấm đỏ tách khỏi nền gradient của nút ⚙ mà không kẻ viền.
     */
    private class Dot {
        val halo = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        val core = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    }

    private fun apply(v: View, dot: Dot) {
        v.overlay.remove(dot.halo); v.overlay.remove(dot.core)
        if (!UpdateDotStore.shows(v.context)) return
        dot.halo.setColor(c(KachiTheme.BAR_TOP))
        dot.core.setColor(c(KachiTheme.RED))
        place(v, dot)
        v.overlay.add(dot.halo); v.overlay.add(dot.core)
    }

    /** Góc trên-phải, lề trong [Sp.XS], đường kính [Sp.DOT] (+ quầng [Sp.HAIRLINE]). */
    private fun place(v: View, dot: Dot) {
        val d = Sp.dp(v.context, Sp.DOT)
        val inset = Sp.dp(v.context, Sp.XS)
        val ring = Sp.dp(v.context, Sp.HAIRLINE).coerceAtLeast(1)
        val right = v.width - inset
        dot.core.setBounds(right - d, inset, right, inset + d)
        dot.halo.setBounds(right - d - ring, inset - ring, right + ring, inset + d + ring)
    }
}
