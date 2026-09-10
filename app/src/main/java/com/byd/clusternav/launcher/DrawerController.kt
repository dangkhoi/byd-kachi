package com.byd.clusternav.launcher

import android.app.Activity
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Điều khiển App Drawer (lớp phủ chọn app/widget cho một ô) — tách khỏi [KachiHomeActivity] (B5b).
 *
 * Mở dạng overlay (`TYPE_APPLICATION_OVERLAY`, nổi TRÊN cửa sổ app freeform) nếu có quyền vẽ-đè; nếu không → gắn vào
 * [rootFrame]. Byte-giữ so với `openDrawer()`/`closeDrawer()` cũ (kể cả xử lý phím BACK khi ở overlay).
 */
class DrawerController(
    private val activity: Activity,
    private val rootFrame: FrameLayout,
    private val currentWidgets: (Int) -> List<String>,
    private val onClearOverlays: () -> Unit,
    private val onOverlayHeads: () -> Unit,
    private val onPickApp: (Int, String) -> Unit,
    private val onPickWidgets: (Int, List<String>) -> Unit,
    private val onOpenApp: (String) -> Unit = {},        // U3: chạm app ở chế độ mở-thường → mở TOÀN MÀN
    private val recentApps: () -> List<String> = { emptyList() },
) {
    private var drawer: AppDrawer? = null
    private var asOverlay = false

    fun isOpen(): Boolean = drawer != null

    /** Ngăn kéo GÁN VÀO Ô [index] (hành vi cũ). */
    fun open(index: Int) {
        if (drawer != null) return
        val current = currentWidgets(index)
        show(
            AppDrawer(
                activity, WidgetRegistry.ALL, current,
                onPickApp = { pkg -> onPickApp(index, pkg) },
                onPickWidgets = { ids -> onPickWidgets(index, ids) },
                onClose = { close() },
            ),
        )
    }

    /**
     * Ngăn kéo **MỞ ỨNG DỤNG** (U3) — không gắn ô nào: chạm app là mở toàn màn. Có hàng "Gần đây".
     * Không đụng bố cục/gán ô đã lưu.
     */
    fun openAppList() {
        if (drawer != null) return
        show(
            AppDrawer(
                activity, WidgetRegistry.ALL, emptyList(),
                onPickApp = { pkg -> onOpenApp(pkg) },
                onPickWidgets = {},
                onClose = { close() },
                mode = AppDrawer.Mode.OPEN_APP,
                recentApps = recentApps(),
            ),
        )
    }

    /** Gắn ngăn kéo lên màn — dùng CHUNG cho cả 2 chế độ (byte-giữ so với nhánh overlay cũ). */
    private fun show(d: AppDrawer) {
        drawer = d
        onClearOverlays()
        // Drawer NỔI như overlay → trên cả cửa sổ app freeform (tránh app đè popup). Chưa có quyền overlay → fallback
        // vào cửa sổ launcher.
        asOverlay = android.provider.Settings.canDrawOverlays(activity) && runCatching {
            d.isFocusableInTouchMode = true
            d.setOnKeyListener { _, code, ev ->
                if (code == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) { close(); true } else false
            }
            activity.windowManager.addView(
                d,
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ),
            )
            d.requestFocus()
            true
        }.getOrDefault(false)
        if (!asOverlay) rootFrame.addView(d, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    fun close() {
        drawer?.let { if (asOverlay) runCatching { activity.windowManager.removeViewImmediate(it) } else rootFrame.removeView(it) }
        drawer = null; asOverlay = false; onOverlayHeads()
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
