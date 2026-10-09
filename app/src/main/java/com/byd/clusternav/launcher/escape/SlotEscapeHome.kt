package com.byd.clusternav.launcher.escape

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.byd.clusternav.launcher.DefaultHome
import com.byd.clusternav.launcher.EffectiveLayout
import com.byd.clusternav.launcher.HomeViewModel
import com.byd.clusternav.launcher.SlotContent
import com.byd.clusternav.launcher.KachiSpace
import com.byd.clusternav.launcher.SlotLiveProbe
import com.byd.clusternav.launcher.SlotLiveness
import com.byd.clusternav.launcher.WorkspaceView
import com.byd.clusternav.modules.clustercast.ClusterProfile
import java.io.IOException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ═══ 2.98 · R7 — keo của MỘT màn nhà Kachi cho "app thoát ô ⇒ freeform đúng khung ô" ═══════════════════════════════════════
 *
 * Sống theo [activity] (dựng ở `KachiHomeSlotActions`). Không vòng hỏi mới nào (owner: hiệu năng là ưu tiên) — mọi lượt chạy
 * vào đúng các mốc sẵn có:
 *
 * | Mốc | Việc | Lệnh shell |
 * |---|---|---|
 * | nhịp đo ô (`SlotLiveProbe`) kết luận app RA KHỎI ô ([tryAdopt]) | nhận: freeform + đúng khung ô | 5 (hỏng: +1–2) |
 * | màn nhà hiện (onStart) / nội dung ô đổi (state) | đối chiếu dấu: giữ · trả (ô không còn app) · ô về luật hoàn ô (task mất) | 0 khi không dấu; có dấu 1 đọc (+1–2 mỗi lần trả) |
 * | màn nhà lên trước (onResume) / bảng Kachi đóng | như trên + đưa app đang quản lên trên màn nhà | có dấu: 1 đọc + 0–1 |
 * | bảng Kachi mở (ngăn kéo · Cài đặt · bố cục) · hộp thoại Kachi hiện (R16, [SlotEscape.shade]) khi app đang quản ở trên | gỡ lớp che + Home qua rào camera (app xuống dưới) | 1 |
 * | màn nhà khuất (onStop) | gỡ lớp che | 0 |
 *
 * Lớp che ([EscapeCoverOverlay]) chỉ hiện khi màn nhà đang hiện VÀ bản đọc vừa rồi thấy task đang quản ở trên màn nhà.
 */
internal class SlotEscapeHome(
    private val activity: Activity,
    private val viewModel: HomeViewModel,
    private val workspace: () -> WorkspaceView,
    private val shell: () -> ((String) -> String)?,
    /** Task của app đang quản đã mất mà ô còn hiện app ⇒ ô đi luật hoàn ô như app vừa đóng (luồng chính). */
    private val onTaskGone: (Int, String) -> Unit,
) : DefaultLifecycleObserver {

    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var resumed = false
    /** 2.98 · R16 — bảng + hộp thoại Kachi đang mở (`EscapeShade`, luồng chính); trước đây chỉ có cờ bảng một nguồn. */
    private val shade = EscapeShade()
    private var pendingRefront = false
    private var pendingWhy = ""
    private val reconcileRun = Runnable { reconcileNow() }

    init {
        // Nhịp đo ô tiếp thêm vài nhịp khi màn nhà vừa bị app thoát ô che (Waze `launchToSide` toàn màn) — chỉ khi R7 bật.
        SlotLiveProbe.pausedGraceSweeps = if (SlotEscape.codes(activity) != null) SlotEscape.PAUSED_GRACE_SWEEPS else 0
        // Owner 09/10 #1 — app thoát ô được thấy sau ~2–3,5 s thay vì 10–14 s: vài mốc đo có trần ngay sau lượt mở app vào ô.
        SlotLiveProbe.launchBurstMs = if (SlotEscape.codes(activity) != null) SlotLiveness.LAUNCH_BURST_MS else emptyList()
        (activity as? LifecycleOwner)?.let { owner ->
            owner.lifecycle.addObserver(this)
            // Nội dung ô đổi (đổi app · đổi hồ sơ · đổi bố cục · luật hoàn ô) ⇒ đối chiếu; lượt phát đầu mỗi lần STARTED = mốc onStart.
            owner.lifecycleScope.launch {
                owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.uiState.map { it.effectiveWorkspace.slots }.distinctUntilChanged().collect { requestReconcile(false, "slots") }
                }
            }
        }
    }

    fun owns(a: Activity): Boolean = a === activity

    /** Ngữ cảnh [c] (hộp thoại dựng bằng màn này — có thể bọc `ContextThemeWrapper`) thuộc màn này không. */
    fun ownsContext(c: android.content.Context?): Boolean {
        var x = c
        while (x != null) {
            if (x === activity) return true
            x = (x as? android.content.ContextWrapper)?.baseContext?.takeIf { it !== x }
        }
        return false
    }

    override fun onStart(owner: LifecycleOwner) { started = true; SlotEscape.current = this }
    override fun onResume(owner: LifecycleOwner) { resumed = true; requestReconcile(true, "resume") }
    override fun onPause(owner: LifecycleOwner) { resumed = false }

    override fun onStop(owner: LifecycleOwner) {
        started = false
        main.removeCallbacks(reconcileRun)
        // Chỉ gỡ lớp che CỦA MÀN NÀY: hai màn nhà cùng sống (R3 — [ĐO xe 09/10] hai task KachiHome) thì onResume của màn MỚI
        // (đã che) chạy TRƯỚC onStop của màn CŨ — gỡ mù ở đây là xoá lớp che màn mới vừa dựng (soát Pass 7).
        EscapeCoverOverlay.hideIf(activity, "home-stop")
        if (SlotEscape.current === this) SlotEscape.current = null
    }

    override fun onDestroy(owner: LifecycleOwner) { owner.lifecycle.removeObserver(this) }

    /**
     * App [pkg] của ô [index] vừa RA KHỎI ô mà task còn ở display khác (nhịp đo — `APP_ELSEWHERE`). R7 tắt / chưa có kênh / ô chưa
     * đo được khung ⇒ [fallback] ngay (đúng đường 2.93). Không thì thử nhận trên luồng R7; không nhận được ⇒ [fallback].
     */
    fun tryAdopt(index: Int, pkg: String, fallback: () -> Unit) {
        val codes = SlotEscape.codes(activity)
        val sh = shell()
        val rect = workspace().hostAt(index)?.let(::rectOf)
        if (codes == null || sh == null || rect == null) {
            Log.i(SlotEscape.TAG, "ô $index: $pkg thoát ô — R7 ${if (codes == null) "tắt (đời ROM/freeform)" else if (sh == null) "chưa có kênh" else "chưa đo được khung ô"} ⇒ luật hoàn ô")
            return fallback()
        }
        val shownPkg = shownAt(index)
        val dpi = activity.resources.displayMetrics.densityDpi
        val lift = SlotEscape.liftPx(dpi)
        SlotEscape.submit("adopt") {
            val r = try {
                SlotEscape.run(activity, sh, codes, lift).adopt(index, pkg, shownPkg, rect, dpi)
            } catch (e: IOException) {
                SlotEscapeRun.Adopted(false, null, "adopt $pkg slot $index -> channel ${e.javaClass.simpleName}")
            } catch (e: RuntimeException) {
                SlotEscapeRun.Adopted(false, null, "adopt $pkg slot $index -> ${e.javaClass.simpleName}")
            }
            Log.i(SlotEscape.TAG, r.line)
            val bars = if (r.ok && r.onTop) barsFor(sh) else null
            main.post {
                val t = r.task
                if (!r.ok || t == null) return@post fallback()
                // Soát Pass 7: chỉ che khi lệnh nhận đã đưa task lên ĐỈNH (toTop) — một app khác đang ở đỉnh thì task nằm dưới nó,
                // che lúc đó là đặt gương Kachi lên thanh của app kia; lượt đối chiếu kế (onResume/state) sẽ che đúng lúc.
                if (started && !shade.open && r.onTop) showCovers(listOf(EscapeMarker(pkg, index, t, r.rect ?: rect)), bars) { rect }
            }
        }
    }

    /**
     * Gộp các mốc gần nhau (onStart + onResume + state) thành MỘT lượt đối chiếu. Gọi được từ mọi luồng (`SlotEscape.claimsLive`
     * chạy trên luồng nền của host ô; dịch vụ trợ năng nối lại) — ngoài luồng chính thì nhảy về luồng chính trước, vì
     * [pendingRefront]/[pendingWhy] chỉ được đọc/ghi ở đó (soát Pass 7: trước đây ghi thẳng từ luồng nền ⇒ đua, mất mốc).
     */
    fun requestReconcile(refront: Boolean, why: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { requestReconcile(refront, why) }; return }
        pendingRefront = pendingRefront || refront
        pendingWhy = if (pendingWhy.isEmpty()) why else "$pendingWhy+$why"
        main.removeCallbacks(reconcileRun)
        main.postDelayed(reconcileRun, COALESCE_MS)
    }

    /** Bảng Kachi mở/đóng (`LauncherWindows`). Mở khi có app đang quản ở trên ⇒ gỡ che + app xuống dưới màn nhà; đóng ⇒ đưa lại lên. */
    fun onPanels(open: Boolean) = onShade(shade.panels(open), "panel")

    /** 2.98 · R16 — hộp thoại Kachi của màn này hiện/đóng ([SlotEscape.shade]): cùng đường với bảng, chỉ ở CẠNH ([EscapeShade]). */
    fun onDialog(shown: Boolean) = onShade(shade.dialog(shown), "dialog")

    private fun onShade(edge: EscapeShade.Edge?, why: String) {
        if (edge == null) return
        if (edge == EscapeShade.Edge.CLOSED) return requestReconcile(true, "$why-closed")
        if (!EscapeCoverOverlay.isShown) return
        EscapeCoverOverlay.hide(why)
        val sh = shell() ?: return
        if (!SlotEscape.homeAllowed(activity)) { Log.i(SlotEscape.TAG, "bảng mở: Kachi không là home mặc định ⇒ không Home (app freeform ở trên bảng)"); return }
        // Đẩy app freeform xuống dưới màn nhà (Home qua rào camera, 1 lệnh) để bảng không bị cửa sổ app đè — KDoc `SlotEscapePlan.homeCmd`.
        val cmd = SlotEscapePlan.homeCmd(ClusterProfile.resolveCached(activity).cameraSignature, DefaultHome.shownComponents(activity)) ?: return
        SlotEscape.submit("$why-home") { sh(cmd); Log.i(SlotEscape.TAG, "$why mở ⇒ app freeform xuống dưới màn nhà") }
    }

    private fun reconcileNow() {
        val refront = pendingRefront && resumed && !shade.open
        val why = pendingWhy
        pendingRefront = false; pendingWhy = ""
        if (!started) return
        val codes = SlotEscape.codes(activity) ?: return
        val sh = shell() ?: return
        val st = viewModel.uiState.value
        val slots = st.effectiveWorkspace.slots
        // Ô "đang hiện" theo STATE (lớp HIỆN + số ô của bố cục), KHÔNG theo view: [ĐO máy ảo 09/10] lúc kênh vừa lên host ô chưa
        // dựng xong ⇒ hỏi view thì thấy "ô không có app" ⇒ trả nhầm app đang quản. View chỉ dùng để đo khung (thiếu ⇒ không kéo lại).
        val count = EffectiveLayout.slotCount(st.workspace.preset, st.customLayout)
        val shown = slots.indices.map { i -> if (i < count) (slots[i] as? SlotContent.App)?.pkg else null }
        val rects = slots.indices.map { i -> workspace().hostAt(i)?.let(::rectOf) }
        val lift = SlotEscape.liftPx(activity.resources.displayMetrics.densityDpi)
        SlotEscape.submit("reconcile") {
            val r = SlotEscape.run(activity, sh, codes, lift)
                .reconcile({ shown.getOrNull(it) }, { SlotEscape.installed(activity, it) }, { rects.getOrNull(it) }, refront)
            if (r !== SlotEscapeRun.Reconciled.NOTHING) Log.i(SlotEscape.TAG, "[$why] ${r.line}")
            val bars = if (r.onTop.isNotEmpty()) barsFor(sh) else null
            main.post {
                r.gone.forEach { m -> onTaskGone(m.slot, m.pkg) }
                if (started && !shade.open && r.onTop.isNotEmpty()) showCovers(r.onTop, bars) { rects.getOrNull(it) }
                else if (r.line != NO_READ) EscapeCoverOverlay.hide("reconcile")
            }
        }
    }

    /**
     * Lớp che cho các task đang quản ở trên: [EscapeMarker.rect] = khung TASK đã đặt (có thể cao hơn ô — `EscapeFit`), [slotRect] =
     * khung ô hiện tại (`null` ⇒ coi như khung task). Bán kính góc = bán kính khung ô (`WorkspaceView.makeSlot` → `SlotFrameClip`).
     * Nút ⇄ của ô ([slotHeads]) được vẽ lại trên vùng [EscapeCoverPlan.Kind.GAP].
     */
    private fun showCovers(onTop: List<EscapeMarker>, bars: EscapeCoverPlan.Bars?, slotRect: (Int) -> PxRect?) {
        val dpi = activity.resources.displayMetrics.densityDpi
        val dm = activity.resources.displayMetrics
        val decor = activity.window?.decorView
        val screen = PxRect(0, 0, maxOf(dm.widthPixels, decor?.width ?: 0), maxOf(dm.heightPixels, decor?.height ?: 0))
        val radius = KachiSpace.dpf(activity, KachiSpace.RADIUS_L).toInt()
        val covers = onTop.flatMapIndexed { i, m ->
            val b = if (i == 0) bars ?: EscapeCoverPlan.Bars.NONE else EscapeCoverPlan.Bars.NONE
            EscapeCoverPlan.covers(m.rect, b, dpi, screen, slotRect(m.slot) ?: m.rect, m.slot, radius)
        }
        EscapeCoverOverlay.show(activity, covers, ::slotHeads)
    }

    /** Con của khung ô [index] TRỪ bộ chiếu (= nút ⇄ nổi ở đầu ô) — vẽ lại trên vùng GAP. Ô chưa dựng ⇒ rỗng. */
    private fun slotHeads(index: Int): List<View> {
        val host = workspace().hostAt(index) ?: return emptyList()
        val frame = host.parent as? android.view.ViewGroup ?: return emptyList()
        return (0 until frame.childCount).map { frame.getChildAt(it) }.filter { it !== host && it.visibility == View.VISIBLE }
    }

    private fun shownAt(index: Int): String? = (viewModel.uiState.value.effectiveWorkspace.slots.getOrNull(index) as? SlotContent.App)?.pkg

    private companion object {
        const val COALESCE_MS = 150L
        const val NO_READ = "reconcile ⇒ NO_READ"

        /** Khung ô trên màn (display 0) = khung của host ô (lấp đầy khung ô). Chưa đo xong bố cục ⇒ `null`. */
        fun rectOf(v: View): PxRect? {
            if (v.width <= 0 || v.height <= 0 || !v.isAttachedToWindow) return null
            val loc = IntArray(2); v.getLocationOnScreen(loc)
            return PxRect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
        }

        /**
         * Khung hai thanh hệ thống — đọc MỘT lần mỗi tiến trình (thanh không dời chỗ), chỉ khi dịch vụ trợ năng đã nối (không có nó
         * thì không che được, khỏi đọc). Luồng R7.
         */
        @Volatile var cachedBars: EscapeCoverPlan.Bars? = null

        fun barsFor(sh: (String) -> String): EscapeCoverPlan.Bars? {
            if (com.byd.clusternav.modules.navaccess.A11yOverlayPort.service == null) return null
            cachedBars?.let { return it }
            val out = try { sh(EscapeCoverPlan.WINDOWS_CMD) } catch (e: IOException) { return null } catch (e: RuntimeException) { return null }
            return EscapeCoverPlan.parseBars(out).also {
                // Soát Pass 7: chỉ ghim khi đọc được ít nhất một thanh — bản đọc rỗng (định dạng lạ / khung chưa đặt) mà ghim là cả tiến
                // trình không bao giờ che thanh nữa; đọc lại lần sau tốn đúng một `dumpsys window windows` (chỉ-đọc).
                if (it.status != null || it.nav != null) cachedBars = it
                Log.i(SlotEscape.TAG, "thanh hệ thống: trạng thái ${it.status} · điều hướng ${it.nav}")
            }
        }
    }
}
