package com.byd.clusternav.launcher

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Nút ⇄ NỔI (overlay `TYPE_APPLICATION_OVERLAY`) đặt lên TRÊN cửa sổ freeform của app trong ô.
 *
 * Vì sao cần: view của launcher nằm DƯỚI cửa sổ app, nên nút ⇄ vẽ trong [WorkspaceView.slotHead] bị app che khi app
 * chạy cửa sổ freeform (đường **sideload**: freeform + overlay; khi Kachi build vào ROM xe thì nhúng sạch qua
 * [SlotAppHost] và không cần lớp này). Overlay nằm TRÊN mọi thứ ⇒ nút luôn bấm được.
 *
 * ⚠ 2026-09-13 — owner báo "lúc 1 icon lúc 2 icon" trên máy ảo: lớp này từng vẽ THANH cũ (nền đục che caption
 * freeform + chấm + tên + ⇄ + ✕) trong khi đường nhúng đã là 1 nút ⇄ giữa, không nền. Nay cả hai đường dùng chung
 * [SlotSwapButton]: một dải màu KHUNG Ô trải hết bề rộng ô (che caption ▭ ✕ mà hệ vẽ cho cửa sổ freeform — [ĐO] máy
 * ảo 2026-09-13 caption lộ ra khi bỏ nền), chỉ một nút ⇄ ở giữa. Không còn ✕/tên/chấm: đổi/gỡ đều đi qua ngăn kéo mở
 * bằng ⇄ (cùng lối với ô nhúng). Lớp này bị ẩn khi ngăn kéo / bảng Cài đặt / bảng vẽ đang mở (LauncherWindows).
 *
 * Cần quyền vẽ overlay (SYSTEM_ALERT_WINDOW) — Kachi tự cấp qua dadb (appops) như ClusterNav cấp cho bong bóng.
 */
class OverlayHeads(private val activity: Activity) {

    /**
     * 1 ô app đang hiện: khung ô (toạ độ MÀN HÌNH) + [appTop] = mép trên CỬA SỔ APP (đã thụt `CAPTION_INSET` so với
     * [top]) + callback ⇄. Cần [appTop] vì caption của hệ bắt đầu từ mép trên cửa sổ app, không phải mép trên ô.
     */
    data class Head(val left: Int, val top: Int, val width: Int, val height: Int, val appTop: Int, val onSwap: () -> Unit)

    private val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun clear() {
        ACTIVE.forEach { (v, w) -> runCatching { w.removeViewImmediate(v) } }
        ACTIVE.clear()
    }

    /** Vẽ lại toàn bộ nút theo [heads]. Bỏ qua nếu chưa có quyền vẽ overlay. */
    fun show(heads: List<Head>) {
        clear()
        if (!android.provider.Settings.canDrawOverlays(activity)) return
        val minH = SlotSwapButton.overlayHeightPx(activity)
        val caption = KachiTheme.dpi(activity, Sp.CAPTION_COVER)   // phủ hết caption freeform của hệ (KDoc hằng)
        heads.forEach { hd ->
            // Dải màu khung ô trải hết bề rộng ô, cao ít nhất bằng khung nút và PHỦ HẾT caption (từ mép trên cửa sổ app
            // xuống đủ một caption) — [ĐO 2026-09-13] dải cao đúng SLOT_HEAD_CLEAR để hở caption phía dưới. Nút ⇄
            // vẫn ở giữa mép trên; vì sao có nền: xem KDoc [SlotSwapButton.strip].
            val h = maxOf(minH, (hd.appTop - hd.top) + caption)
            addOverlay(SlotSwapButton.strip(activity, hd.onSwap), hd.width, h, hd.left, hd.top)
        }
    }

    private fun addOverlay(v: View, w: Int, h: Int, x: Int, y: Int) {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val lp = WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }
        runCatching { wm.addView(v, lp); ACTIVE.add(v to wm) }.onFailure { android.util.Log.i("KACHI", "overlay add fail: $it") }
    }

    private companion object {
        /** Tĩnh: (view, WM đã add nó) — clear() gỡ bằng đúng WM, bền cả khi Activity bị tạo lại (tránh orphan/leak). */
        private val ACTIVE = ArrayList<Pair<View, WindowManager>>()
    }
}
