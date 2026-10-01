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
) {
    private val overlayHeads by lazy { OverlayHeads(activity) }
    private val density = activity.resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * density).toInt()

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

    /**
     * RECONCILE registry vị trí app THEO STATE — gọi từ collector `render(state)` mỗi nhịp (quality-review
     * 2026-09-15, R1/R2). Đây là bước biến registry thành **PROJECTION của state**, thay cho các lệnh
     * `d.place`/`d.remove` sửa TAY rải rác ở handler (`KachiHomeSlots.assignApp/clearSlot/swap`) — nguồn drift
     * của "3 nguồn sự-thật vị-trí-app".
     *
     *  • **mount** (từ [LauncherBootPlan.reconcile]) → `d.place(pkg,0,slot)`: registry khớp đúng ô của state.
     *  • **evict** = app đang ở màn launcher (display 0) mà state KHÔNG còn ô nào giữ → `d.remove` + `closeApp` +
     *    [sweepFloating]. App cụm đang giữ (`!isCastable`) bị loại khỏi mount ⇒ launcher không giành với cụm; nó ở
     *    display cụm nên KHÔNG lọt vào tập display-0 ⇒ KHÔNG bị evict.
     *
     * ⚠ ĐÍNH CHÍNH (PROFILE-SWITCH-SLOTS R-B5, [ĐO mã + máy ảo 2026-10-01]): bản trước ghi `closeApp` "đóng cửa sổ
     *   freeform off-car" — SAI. `closeApp` là no-op ở MỌI đường: chưa có kênh thì bộ mở là `IntentAppLauncher` mà
     *   `closeSlot` của nó rỗng (không có API công khai đóng cửa sổ app khác); có kênh thì `closeApp` thoát sớm vì
     *   `embedding = true`. Hệ quả đo được: đổi hồ sơ A→B→A lúc chưa có kênh thì app ô của B nổi lại trên nhà (fixture
     *   `am-stack-list-emulator-2026-10-01-noshell-A-back.txt`). Đóng thật là việc của [sweepFloating] — chỉ khi có
     *   kênh, chỉ cửa sổ nổi do CHÍNH Kachi mở (dấu bền), quyết bằng `am stack list` (không bằng sổ RAM này: sổ RAM
     *   chỉ quyết KHI NÀO nhìn, không quyết đóng gì). Ô nhúng on-car do `WorkspaceView.releaseSlotHost` nhả theo diff.
     */
    fun reconcileLocations(slots: List<SlotContent>) {
        val d = dispatcher() ?: return
        val placedOnLauncher = d.locations.onDisplay(0).map { it.pkg }.toSet()
        val r = LauncherBootPlan.reconcile(slots, placedOnLauncher) { pkg -> !d.locations.isCastable(pkg) }
        r.mount.forEach { d.place(it.pkg, 0, it.slot) }
        r.evict.forEach { pkg -> d.remove(pkg); closeApp(pkg) }
        if (r.evict.isNotEmpty()) sweepFloating("evict")
    }

    /** PROFILE-SWITCH-SLOTS R-B2/R-B4 — dấu bền theo xe; lười để dựng màn nhà không đụng đĩa trên luồng chính. */
    private val floatingLedger by lazy { FloatingWindowLedger(FloatingLedgerStore(activity)) }
    private val floatingSweep by lazy { FloatingOrphanSweep(floatingLedger, activity.packageName) }

    /**
     * PROFILE-SWITCH-SLOTS R-B3 — đóng cửa sổ NỔI trên màn chính mà CHÍNH Kachi đã mở lúc chưa có kênh, nay không ô nào
     * giữ nữa. Thân ở [FloatingOrphanSweep] (`:core`, test bằng shell ghi âm trên dump thật): `am stack list` →
     * [FloatingOrphanPlan] → `am stack remove <id>` → đọc lại → xoá dấu. Không `am force-stop`, không `fullscreenCmd`.
     *
     * Hai mốc gọi: [reconcileLocations] có gỡ app (`"evict"`) và kênh shell vừa lên (`"shell-up"`, `KachiHomeActivity`
     * lambda `onSeam`). Cố ý KHÔNG xét [embedding] (khác [closeApp]): có kênh chính là lúc dọn được. Không có kênh ⇒
     * không làm gì (dấu còn, mốc "shell-up" sẽ dọn). [held] đọc LÚC CHẠY từ state — mọi ô App, kể cả ô tràn.
     */
    fun sweepFloating(reason: String) {
        val s = shell() ?: return
        submit {
            val held = state().slots.filterIsInstance<SlotContent.App>().mapTo(HashSet()) { it.pkg }
            Log.i(FLOAT_TAG, floatingSweep.run(s, held, reason).line())
        }
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

    /** Dựng lại nút ⇄ NỔI cho mỗi ô app đang hiện (đường freeform). Nhúng → không cần ([WorkspaceView.slotHead] lo). */
    private val overlayUpdate = Runnable {
        if (embedding() || drawerOpen()) { overlayHeads.clear(); return@Runnable }
        val st = state(); val n = EffectiveLayout.slotCount(st.preset, custom())
        val heads = ArrayList<OverlayHeads.Head>()
        for (i in 0 until n) {
            (st.slots.getOrNull(i) as? SlotContent.App)?.let { app ->
                absoluteSlotRect(i)?.let { r ->
                    val a = appRect(r)
                    heads.add(OverlayHeads.Head(a.left, r.top + dp(Sp.XS), a.width, a.height, appTop = a.top, onSwap = { onSlotSwap(i) }))
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
     * Sau khi đổi bố cục/viền/ẩn-hiện thanh nút/hồ sơ (đường KHÔNG nhúng): CHỈ dựng lại nút ⇄ nổi theo khung ô mới.
     *
     * ⚠ PROFILE-SWITCH-SLOTS R-B1 (owner 2026-10-01 "2 ok sửa"): hàm này KHÔNG còn tự mở/đóng app nào. Bản cũ mở lại
     * MỌI app ô thành cửa sổ nổi mỗi lần đổi bố cục/hồ sơ và ở lượt vẽ đầu — đúng hai nguồn app mồ côi đã đo: [ĐO máy
     * ảo 01/10] đổi hồ sơ lúc chưa có kênh ⇒ app ô của hồ sơ cũ nổi lại trên nhà (không ai đóng được, `closeApp` rỗng);
     * [SUY dữ liệu xe 29/09] tiến trình mới sinh mở YouTube nổi, cướp tiêu điểm nên kênh shell không lên. Đường này chỉ
     * chạy khi CHƯA có kênh và ROM không cho ActivityView — khi đó ô hiện thẻ "Chạm để mở" (`WorkspaceViewCards.appCard`)
     * và chạm thẻ đi [placeApp] (ghi dấu bền trước khi mở ⇒ kênh lên thì [sweepFloating] dọn được).
     * Giới hạn đã ghi ở spec §4.4: L1 cửa sổ đã chạm mở còn nổi tới khi kênh lên; L2 không tự dời theo bố cục mới
     * (chạm lại thẻ ô ⇒ [placeApp] đặt lại khung).
     */
    fun reflow() {
        if (embedding()) return   // nhúng: ô đổi kích thước theo layout view → app tự reflow, không cần am task resize
        updateOverlayHeads()
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
            // PROFILE-SWITCH-SLOTS R-B2: dấu bền TRƯỚC lệnh mở (CLAUDE.md §5) — chết ngay sau lệnh mở thì dấu vẫn còn,
            // kênh lên là [sweepFloating] biết cửa sổ nổi này do Kachi mở. Ghi hỏng vẫn mở: người dùng vừa yêu cầu.
            if (!floatingLedger.markOpened(pkg)) Log.w(FLOAT_TAG, "không ghi được dấu cửa sổ nổi cho $pkg — vẫn mở")
            if (fresh) {
                if (s != null) runCatching { s("am force-stop $pkg") }
                launcher.openInSlot(pkg, appRect(rect))
            } else {
                launcher.moveToSlot(pkg, appRect(rect))
            }
            activity.runOnUiThread { updateOverlayHeads() }
        }
    }

    /**
     * Đưa [pkg] ra khỏi ô trên thread nền. Nhúng → no-op (ActivityView/màn ảo tự lo).
     * ⚠ Không nhúng thì bộ mở là `IntentAppLauncher` có `closeSlot` RỖNG ⇒ hàm này thực tế không đóng gì ở đường nào
     * (PROFILE-SWITCH-SLOTS E10/R-B5). Đóng cửa sổ nổi thật: [sweepFloating]. Giữ hàm (OQ-6, backlog).
     */
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

    private companion object {
        /** Thẻ log của lượt dọn cửa sổ nổi (R-B6) — vào `usage-*.log` qua `KachiLog`. */
        const val FLOAT_TAG = "KachiFloat"
    }
}
