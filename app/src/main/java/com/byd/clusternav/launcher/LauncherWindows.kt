package com.byd.clusternav.launcher

import android.app.Activity
import com.byd.clusternav.system.WindowCommandDispatcher
import java.util.concurrent.ExecutorService

/**
 * Điều phối CỬA SỔ freeform + dải header NỔI (caption overlay) cho HOME — tách khỏi [KachiHomeActivity] (B5a) để
 * activity mỏng lại. KHÔNG phải "view-component" (TopStrip/Dock/Drawer là việc của B5b) mà là orchestration cửa sổ.
 *
 * KHÔNG giữ state launcher: đọc qua provider [state] (= `HomeViewModel.uiState.value`). Đọc runtime dễ đổi
 * (shell/appLauncher/embedding/drawer) qua provider vì chúng thay đổi khi dadb nối / drawer mở.
 */
class LauncherWindows(
    private val activity: Activity,
    private val workspace: WorkspaceView,
    private val winExec: ExecutorService,
    private val state: () -> HomeUiState,
    private val embedding: () -> Boolean,
    private val drawerOpen: () -> Boolean,
    private val shell: () -> ((String) -> String)?,
    private val appLauncher: () -> AppLauncher,
    private val dispatcher: () -> WindowCommandDispatcher?,
    private val onSlotSwap: (Int) -> Unit,
    private val onSlotClose: (Int) -> Unit,
) {
    private val overlayHeads by lazy { OverlayHeads(activity) }
    private val density = activity.resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * density).toInt()

    private fun appLabel(pkg: String): String = runCatching {
        activity.packageManager.getApplicationLabel(activity.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** B2b: ghi vị trí ban đầu của các ô App vào registry → bất biến MỘT-VỊ-TRÍ có mặt ngay khi mở app. */
    fun seedLocations() {
        state().slots.forEachIndexed { i, c -> if (c is SlotContent.App) dispatcher()?.place(c.pkg, 0, i) }
    }

    fun clearOverlays() = overlayHeads.clear()

    /** Dựng lại dải header NỔI che caption freeform + ⇄/✕ cho mỗi ô app đang hiện. Nhúng → không cần. */
    private val overlayUpdate = Runnable {
        if (embedding() || drawerOpen()) { overlayHeads.clear(); return@Runnable }
        val st = state(); val n = st.preset.slotCount
        val heads = ArrayList<OverlayHeads.Head>()
        for (i in 0 until n) {
            (st.slots.getOrNull(i) as? SlotContent.App)?.let { app ->
                absoluteSlotRect(i)?.let { r ->
                    val a = appRect(r)
                    heads.add(OverlayHeads.Head(a.left, r.top + dp(3), a.width, a.height, appLabel(app.pkg), "#4c7dff",
                        onSwap = { onSlotSwap(i) }, onClose = { onSlotClose(i) }))
                }
            }
        }
        overlayHeads.show(heads)
    }

    fun updateOverlayHeads() {
        workspace.removeCallbacks(overlayUpdate)                       // debounce: gọi dồn → chỉ chạy 1 lần
        if (embedding() || drawerOpen()) { overlayHeads.clear(); return }
        workspace.postDelayed(overlayUpdate, 350)
    }

    /**
     * Sau khi đổi bố cục/viền: sắp lại cửa sổ app ĐANG mở theo THỨ TỰ ô (KHÔNG reset, app vẫn chạy).
     * App ô hiện → freeform đúng khung; app tràn → fullscreen chạy nền, ẩn sau launcher.
     * Z-order: overflow→fullscreen trước, kéo launcher lên (che overflow), rồi mở lại app hiện (nổi trên launcher).
     */
    fun reflow() {
        if (embedding()) return   // nhúng: ô đổi kích thước theo layout view → app tự reflow, không cần am task resize
        workspace.post {
            val st = state(); val n = st.preset.slotCount
            val visible = ArrayList<Pair<String, SlotRect>>()
            val overflow = ArrayList<String>()
            for (i in 0..3) {
                val c = st.slots.getOrNull(i)
                if (c is SlotContent.App) {
                    if (i < n) absoluteSlotRect(i)?.let { visible.add(c.pkg to appRect(it)) } else overflow.add(c.pkg)
                }
            }
            if (visible.isEmpty() && overflow.isEmpty()) return@post
            val s = shell(); val launcher = appLauncher()
            winExec.execute {
                overflow.forEach { launcher.closeSlot(it) }                                     // tràn → fullscreen chạy nền
                if (overflow.isNotEmpty() && s != null) { s(HOME_FRONT); Thread.sleep(250) }      // kéo launcher lên che overflow
                visible.forEach { (pkg, rect) -> launcher.openInSlot(pkg, rect) }                // ô hiện → freeform, đưa LÊN TRƯỚC launcher
                activity.runOnUiThread { updateOverlayHeads() }
            }
        }
    }

    /** Mở/đặt cửa sổ app THẬT vào ô [index] (freeform + resize) trên thread nền (dadb blocking). */
    fun placeApp(pkg: String, index: Int, fresh: Boolean = false) {
        if (embedding()) return   // WorkspaceView nhúng app bằng ActivityView → không cần freeform
        val rect = absoluteSlotRect(index) ?: return
        val s = shell(); val launcher = appLauncher()
        winExec.execute {
            // Đặt MỚI: force-stop trước để app mở TƯƠI dạng freeform, KHÔNG tái dùng task fullscreen cũ (gốc lỗi đè full).
            if (fresh && s != null) runCatching { s("am force-stop $pkg") }
            launcher.openInSlot(pkg, appRect(rect))
            activity.runOnUiThread { updateOverlayHeads() }
        }
    }

    /** Đưa [pkg] ra khỏi ô (trả fullscreen/dừng) trên thread nền. Nhúng → no-op (ActivityView tự lo). */
    fun closeApp(pkg: String) {
        if (embedding()) return
        val launcher = appLauncher()
        winExec.execute { launcher.closeSlot(pkg) }
    }

    /** Khung ô ở toạ độ MÀN HÌNH (cho freeform on-car): offset vị trí workspace + Rect ô. */
    private fun absoluteSlotRect(index: Int): SlotRect? {
        if (workspace.width <= 0 || workspace.height <= 0) return null
        val rects = WorkspaceLayout.slots(state().preset, workspace.width, workspace.height, dp(10))
        val r = rects.getOrNull(index) ?: return null
        val loc = IntArray(2); workspace.getLocationOnScreen(loc)
        return SlotRect(index, loc[0] + r.left, loc[1] + r.top, loc[0] + r.right, loc[1] + r.bottom)
    }

    /** Khung CỬA SỔ app = LẤP ĐẦY ô; bo góc lo bằng dải header đục (che caption) + 2 mặt nạ góc dưới. */
    private fun appRect(s: SlotRect): SlotRect {
        val m = dp(10)        // margin trái/phải/dưới
        val topCap = dp(24)   // thụt TRÊN cho caption freeform (~36px) lọt trong ô → hết "lòi đầu"
        return SlotRect(s.index, s.left + m, s.top + topCap, s.right - m, s.bottom - m)
    }

    companion object {
        const val HOME_FRONT = "am start -n com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity"
    }
}
