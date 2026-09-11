package com.byd.clusternav.launcher

import android.app.Activity
import android.util.Log
import com.byd.clusternav.system.WindowCommandDispatcher
import java.util.concurrent.ExecutorService
import com.byd.clusternav.launcher.KachiSpace as Sp

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
    /**
     * Bố cục tự vẽ đang hiệu lực. Là HÀM để bộ sắp cửa sổ luôn đọc giá trị **mới nhất** — nếu nhận giá trị chụp sẵn
     * thì đổi bố cục xong app sẽ bị đặt theo bố cục CŨ, tức app nằm lệch khỏi ô.
     */
    private val custom: () -> GridLayout? = { null },
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

    /**
     * B2b: ghi vị trí ban đầu của các ô App vào registry → bất biến MỘT-VỊ-TRÍ có mặt ngay khi mở app.
     * B6 (cast coordination): dùng quyết định THUẦN [LauncherBootPlan] + [AppLocationRegistry.isCastable] để
     * BỎ QUA app mà cluster-cast đang sở hữu trên cụm (display 1) — KHÔNG ghi đè vị trí cast (không "giành" app
     * khỏi cụm). App chưa ở cụm ⇒ launcher sở hữu ô như cũ. Registry rỗng lúc boot ⇒ mọi app castable ⇒ y hệt cũ.
     */
    fun seedLocations() {
        val d = dispatcher() ?: return
        LauncherBootPlan.plan(state().slots) { pkg -> !d.locations.isCastable(pkg) }
            .mount.forEach { d.place(it.pkg, 0, it.slot) }
    }

    fun clearOverlays() = overlayHeads.clear()

    /**
     * Huỷ MỌI lượt đã hẹn của bộ này + khoá không nhận việc mới. Gọi từ `onDestroy` TRƯỚC khi tắt thread nền.
     *
     * ## [SOÁT P2-4] Vì sao cần
     * Bộ này hẹn hai loại việc: `overlayUpdate` (350 ms) và thân `reflow` (`workspace.post`). Cả hai chạy SAU khi
     * `onDestroy` đã gọi `winExec.shutdownNow()` là (a) dựng cửa sổ overlay bằng WindowManager của activity đã chết
     * ⇒ giữ view, giữ activity; (b) `winExec.execute` trên executor đã tắt ⇒ `RejectedExecutionException` **không
     * ai bắt** ⇒ sập. Không dựa vào giả định "view đã tháo thì lượt post không chạy" — tài liệu Android không nói
     * rõ, và ở chỗ khác dự án đang dựa vào giả định NGƯỢC LẠI. Chặn tường minh thì đúng với cả hai khả năng.
     */
    fun cancelPending() {
        stopped = true
        workspace.removeCallbacks(overlayUpdate)
        overlayHeads.clear()
    }

    /** Đã huỷ màn ⇒ không hẹn thêm, không nộp thêm việc nền. */
    @Volatile private var stopped = false

    /** Nộp việc nền an toàn: bỏ qua nếu đã huỷ màn, và không để executor-đã-tắt làm sập tiến trình. */
    private fun submit(block: () -> Unit) {
        if (stopped) return
        runCatching { winExec.execute { if (!stopped) block() } }
            .onFailure { Log.w("LauncherWindows", "bỏ việc cửa sổ vì thread nền đã tắt: ${it.javaClass.simpleName}") }
    }

    /** Dựng lại dải header NỔI che caption freeform + ⇄/✕ cho mỗi ô app đang hiện. Nhúng → không cần. */
    private val overlayUpdate = Runnable {
        if (embedding() || drawerOpen()) { overlayHeads.clear(); return@Runnable }
        val st = state(); val n = EffectiveLayout.slotCount(st.preset, custom())
        val heads = ArrayList<OverlayHeads.Head>()
        for (i in 0 until n) {
            (st.slots.getOrNull(i) as? SlotContent.App)?.let { app ->
                absoluteSlotRect(i)?.let { r ->
                    val a = appRect(r)
                    heads.add(OverlayHeads.Head(a.left, r.top + dp(Sp.XS), a.width, a.height, appLabel(app.pkg), "#4c7dff",
                        onSwap = { onSlotSwap(i) }, onClose = { onSlotClose(i) }))
                }
            }
        }
        overlayHeads.show(heads)
    }

    fun updateOverlayHeads() {
        workspace.removeCallbacks(overlayUpdate)                       // debounce: gọi dồn → chỉ chạy 1 lần
        if (embedding() || drawerOpen()) { overlayHeads.clear(); return }
        if (!stopped) workspace.postDelayed(overlayUpdate, 350)
    }

    /**
     * Sau khi đổi bố cục/viền: sắp lại cửa sổ app ĐANG mở theo THỨ TỰ ô (KHÔNG reset, app vẫn chạy).
     * App ô hiện → freeform đúng khung; app tràn → fullscreen chạy nền, ẩn sau launcher.
     * Z-order: overflow→fullscreen trước, kéo launcher lên (che overflow), rồi mở lại app hiện (nổi trên launcher).
     */
    fun reflow() {
        if (embedding()) return   // nhúng: ô đổi kích thước theo layout view → app tự reflow, không cần am task resize
        if (stopped) return
        workspace.post {
            if (stopped) return@post
            val st = state(); val n = EffectiveLayout.slotCount(st.preset, custom())
            val visible = ArrayList<Pair<String, SlotRect>>()
            val overflow = ArrayList<String>()
            // [SOÁT P1-3] Trước đây viết cứng 0..3. Sau khi nới trần ô lên 6, app ở khung 5/6 KHÔNG được đặt lại
            // khung khi đổi bố cục ⇒ nằm lệch khỏi ô, hoặc đang toàn màn thì cứ toàn màn che launcher — đúng hình
            // dạng P-bug2. Quét theo trần ô thật.
            for (i in 0 until WorkspaceState.SLOT_CAP) {
                val c = st.slots.getOrNull(i)
                if (c is SlotContent.App) {
                    if (i < n) absoluteSlotRect(i)?.let { visible.add(c.pkg to appRect(it)) } else overflow.add(c.pkg)
                }
            }
            if (visible.isEmpty() && overflow.isEmpty()) return@post
            val s = shell(); val launcher = appLauncher()
            submit {
                overflow.forEach { launcher.closeSlot(it) }                                     // tràn → fullscreen chạy nền
                if (overflow.isNotEmpty() && s != null) { s(HOME_FRONT); Thread.sleep(250) }      // kéo launcher lên che overflow
                visible.forEach { (pkg, rect) -> launcher.openInSlot(pkg, rect) }                // ô hiện → freeform, đưa LÊN TRƯỚC launcher
                activity.runOnUiThread { updateOverlayHeads() }
            }
        }
    }

    /**
     * Mở/đặt cửa sổ app THẬT vào ô [index] (freeform + resize) trên thread nền (dadb blocking).
     *
     * [fresh] = true (đặt app MỚI vào ô): dừng hẳn app trước để nó mở TƯƠI dạng freeform, KHÔNG tái dùng task
     * fullscreen cũ (gốc lỗi đè full).
     *
     * [fresh] = false (đưa app ĐANG chạy về ô — vd chạm ô): **chỉ đặt lại khung**, KHÔNG mở lại app ⇒ hết nháy /
     * hết cướp focus (U2). `moveToSlot` tự lùi về `openInSlot` nếu app chưa có task hoặc đang toàn màn (bị từ chối
     * resize) ⇒ suy giảm an toàn, không mất chức năng.
     */
    fun placeApp(pkg: String, index: Int, fresh: Boolean = false) {
        if (embedding()) return   // WorkspaceView nhúng app bằng ActivityView → không cần freeform
        val rect = absoluteSlotRect(index) ?: return
        val s = shell(); val launcher = appLauncher()
        submit {
            if (fresh) {
                if (s != null) runCatching { s("am force-stop $pkg") }
                launcher.openInSlot(pkg, appRect(rect))
            } else {
                launcher.moveToSlot(pkg, appRect(rect))
            }
            activity.runOnUiThread { updateOverlayHeads() }
        }
    }

    /** Đưa [pkg] ra khỏi ô (trả fullscreen/dừng) trên thread nền. Nhúng → no-op (ActivityView tự lo). */
    fun closeApp(pkg: String) {
        if (embedding()) return
        val launcher = appLauncher()
        submit { launcher.closeSlot(pkg) }
    }

    /** Khung ô ở toạ độ MÀN HÌNH (cho freeform on-car): offset vị trí workspace + Rect ô. */
    private fun absoluteSlotRect(index: Int): SlotRect? {
        if (workspace.width <= 0 || workspace.height <= 0) return null
        val rects = EffectiveLayout.rects(state().preset, custom(), workspace.width, workspace.height, dp(Sp.SLOT_GAP))
        val r = rects.getOrNull(index) ?: return null
        val loc = IntArray(2); workspace.getLocationOnScreen(loc)
        return SlotRect(index, loc[0] + r.left, loc[1] + r.top, loc[0] + r.right, loc[1] + r.bottom)
    }

    /** Khung CỬA SỔ app = LẤP ĐẦY ô; bo góc lo bằng dải header đục (che caption) + 2 mặt nạ góc dưới. */
    private fun appRect(s: SlotRect): SlotRect {
        val m = dp(Sp.SLOT_APP_INSET)        // margin trái/phải/dưới
        val topCap = dp(Sp.CAPTION_INSET)   // thụt TRÊN cho caption freeform (~36px) lọt trong ô → hết "lòi đầu"
        return SlotRect(s.index, s.left + m, s.top + topCap, s.right - m, s.bottom - m)
    }

    companion object {
        const val HOME_FRONT = "am start -n com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity"
    }
}
