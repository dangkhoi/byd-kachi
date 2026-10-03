package com.byd.clusternav.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.byd.clusternav.AppContainer
import com.byd.clusternav.R
import com.byd.clusternav.launcher.behind.BehindHomePlan
import com.byd.clusternav.launcher.behind.BehindHomeRunner
import com.byd.clusternav.launcher.behind.BehindHomeSequence
import com.byd.clusternav.modules.clustercast.ClusterProfile

/**
 * ═══ GLUE INTENT THEO-Ô của màn chính — TÁCH KHỎI [KachiHomeActivity] (trần 500 dòng) ═══════════════════════
 *
 * Sáu việc mà một cú chạm vào **một ô** sinh ra: gắn app · gắn widget · mở lại · mở toàn màn · xoá ô · đổi chỗ
 * hai ô. Chúng đi cùng nhau vì cùng một hình dạng: **intent cho ViewModel** (state + lưu bền, một chiều) rồi
 * **side-effect cửa sổ** (sổ vị trí của `windowDispatcher` + [LauncherWindows]). Giữ cả sáu ở một chỗ là để
 * không ai thêm việc thứ bảy mà quên một trong hai nửa — đã có một lỗi thật đúng kiểu đó (ô thay app khác mà
 * app cũ còn nguyên trong sổ vị trí).
 *
 * ## Vì sao là một LỚP nhận lambda, không phải hàm mở rộng của `Activity`
 * Sáu hàm dùng chung đúng bảy phụ thuộc. Viết thành `fun Activity.assignApp(viewModel, container, windows, …)`
 * thì mỗi chỗ gọi phải chép lại bảy đối số — tức bảy cơ hội truyền nhầm. `windows`/`drawerController` là
 * `lateinit` ở màn chính nên nhận qua lambda (`() -> …`), đọc đúng lúc dùng chứ không chụp lúc dựng.
 *
 * ⚠ Lớp này **không** giữ tham chiếu tới Activity: mọi thứ nó cần đi qua lambda của chỗ dựng. Nhờ vậy nó không
 * phải biết gì về vòng đời màn chính (CLAUDE.md §5 — không mở thêm đường sống lâu hơn thứ nó phục vụ).
 */
internal class KachiHomeSlots(
    private val viewModel: HomeViewModel,
    private val container: AppContainer,
    private val windows: () -> LauncherWindows,
    private val drawer: () -> DrawerController,
    private val appOpener: AppOpener,
    /** Kênh shell (dadb) — `null` khi chưa dò ra; đọc MỖI LẦN vì nó được gán ở luồng nền sau khi màn đã mở. */
    private val shell: () -> ((String) -> String)?,
    /** Cửa duy nhất đẩy việc xuống thread nền của màn chính (đã huỷ ⇒ tự bỏ) — xem `KachiHomeActivity.submitBg`. */
    private val submitBg: (() -> Unit) -> Boolean,
    /** Khung ô (lateinit ở màn chính) — chỉ để trả host về app đang hiện khi lượt đặt tạm không thành. */
    private val workspace: () -> WorkspaceView,
    /** Context ỨNG DỤNG (không phải Activity — lớp này không giữ màn chính): runner BEHIND-HOME + chuỗi lý do. */
    private val app: Context,
) {

    /** BEHIND-HOME (spec shortcuts-autostart R0) — bên thi hành dùng chung; mutex là luồng `kachi-behind` của tiến trình. */
    private val behind by lazy { BehindHomeRunner(app, shell) }

    /**
     * ĐẶT TẠM [pkg] vào ô [index] (0-based) — giọng nói *"mở X vào ô n"* (và lối tắt kiểu *Ô n*, nhóm B). Đính chính
     * owner 01/10: KHÔNG ghi `slot_n`. Cùng hai nửa với [assignApp] (state qua ViewModel + side-effect cửa sổ), khác
     * đúng một chỗ: lớp tạm thay cho lớp lưu, và ô đang có app khác thì app cũ ra sau màn nhà ([evictBehind]) thay vì
     * bị force-stop. `false` = ô ngoài bố cục đang hiện.
     */
    fun placeTemporary(index: Int, pkg: String): Boolean {
        drawer().close()
        if (!viewModel.placeTemporary(index, pkg)) return false
        windows().placeApp(pkg, index, fresh = true)   // chưa có bộ chiếu ⇒ lời nhắc kênh (READY-AT-HOME); có ⇒ no-op
        return true
    }

    /**
     * Host của ô [index] vừa mở [b] vào màn ảo [vd] TRÊN [a] (đặt tạm) ⇒ đẩy [a] ra sau màn nhà (R0.1). B không vào
     * được ô mà A còn ở đỉnh ⇒ host nhận lại A + lớp tạm trả ô về A + một dòng lý do (§4.4.5).
     */
    fun evictBehind(index: Int, vd: Int, a: String, b: String) {
        behind.evict(vd, a, b) { out ->
            if (out.result != BehindHomeSequence.Result.B_NOT_IN_SLOT) return@evict
            Log.i(BehindHomeRunner.TAG, "ô $index: $b không vào được ô — trả ô về $a")
            runCatching { workspace().hostAt(index)?.adoptShown(a) }
            viewModel.revertTemporary(index, a)
            Toast.makeText(app, app.getString(R.string.kachi_sc_place_failed, appLabel(b)), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * R0.3 — chạy [pkg] PHÍA SAU màn nhà qua một ô đang sống trong [stages] (lối tắt kiểu *Chạy ngầm*, nhóm B; chuyến lên
     * xe, nhóm C). `null` = không có ô sống nào ⇒ 0 lệnh; bên gọi lùi về màn ảo ẩn ([startBehindHidden], L8). [done] chạy trên
     * luồng chính với kết quả của chuỗi (đã có dòng log `KachiBehind`). Cùng runner (mutex `kachi-behind`) với [evictBehind].
     */
    fun startBehind(pkg: String, stages: List<BehindHomePlan.Stage>, done: (BehindHomeSequence.Outcome) -> Unit): BehindHomePlan.Stage? =
        behind.startBehind(pkg, stages, done)

    /** L4 — chuỗi tuỳ ý của chuyến lên xe (màn ảo ẩn D2(a), K4-VIEW D3(ii)) trên CÙNG runner/mutex `kachi-behind`. */
    fun behindChain(what: String, body: (BehindHomeRunner.Kit) -> BehindHomeSequence.Outcome, done: (BehindHomeSequence.Outcome) -> Unit) =
        behind.chain(what, done, body)

    /**
     * L8 — lối tắt *Chạy ngầm* khi KHÔNG có ô app sống (`ShortcutAction.StartBehindHidden`): màn ảo ẨN của Kachi, CÙNG chuỗi
     * với chuyến lên xe (L4 · D2(a)) — đường mới đứng sau đường ô sống ([startBehind]). [done] chạy trên luồng chính.
     */
    fun startBehindHidden(pkg: String, done: (BehindHomeSequence.Outcome) -> Unit) =
        behind.chain("behind-hidden X=$pkg", done) { kit -> kit.seq.startBehindHidden(pkg, kit.hidden) }

    /**
     * L8 — nút *chạy nền* của ô [index]: lớp che của Kachi lên đỉnh màn ảo [vd] CỦA Ô → move-task app [pkg] ra sau màn nhà
     * → gỡ che ([BehindHomeSequence.evictCovered] — bốn câu CLAUDE.md §4 ở KDoc đó). [done] (luồng chính): `true` = bản đọc
     * cuối thấy app đã RỜI màn ảo ô và sống trên display 0 ⇒ bên gọi áp luật hoàn ô; `false` = ô giữ app (dòng `KachiBehind`).
     * Lớp che dùng bộ phận Android của màn ảo ẩn (`StagingDisplay.cover/uncover` — không tạo / nhả màn ảo nào ở đây).
     */
    fun toBack(index: Int, vd: Int, pkg: String, done: (Boolean) -> Unit) =
        behind.chain("slot-back slot=$index X=$pkg", { out -> done(out.outOfStage) }) { kit -> kit.seq.evictCovered(vd, pkg, kit.hidden) }

    /**
     * F1 · R1.5 dòng 9 — lối tắt *Toàn màn* cho app ĐANG ở ô [index]: K7 qua rào (màn nhà Kachi đang hiện; dấu hiệu camera
     * của đời xe nếu đã biết — `ClusterProfile.cameraSignature`). Về lại ô khi màn nhà hiện lại ([WorkspaceView.returnDetached]).
     * `false` = ô chưa sẵn sàng, 0 lệnh. [done] (luồng chính): đã ra toàn màn chưa.
     */
    fun detachToFull(index: Int, done: (Boolean) -> Unit): Boolean = workspace().detachToFull(
        index, ClusterProfile.resolveCached(app).cameraSignature, DefaultHome.shownComponents(app), done,
    )

    /**
     * FIX286 · R-SC2 — [pkg] được xếp ở ô [index]: còn task trên màn ảo của ô không, đo bằng SỰ THẬT lúc chạm (MỘT
     * `am stack list` trên luồng nền của màn) — không bằng bố cục, không bằng cờ RAM của host (CLAUDE.md §5). [done] chạy
     * trên luồng chính. Ô không có màn ảo đang giữ [pkg] (chưa mở xong / đã nhả) · chưa có kênh · việc nền bị từ chối ⇒
     * [SlotPresence.UNKNOWN] ⇒ bảng giữ hành vi trước FIX286 (không mở lại = không `force-stop`).
     */
    fun presence(index: Int, pkg: String, done: (SlotPresence) -> Unit) {
        val stage = workspace().hostAt(index)?.stage()
        val sh = shell()
        if (stage == null || stage.pkg != pkg || sh == null) { done(SlotPresence.UNKNOWN); return }
        val accepted = submitBg {
            val out = runCatching { sh(BehindHomePlan.LIST_CMD) }.getOrDefault("")
            val p = SlotPresence.of(out, pkg, stage.vd)
            Log.i(KachiHomeShortcuts.TAG, "đo ô $index: $pkg vd=${stage.vd} ⇒ $p")
            mainHandler.post { done(p) }
        }
        if (!accepted) done(SlotPresence.UNKNOWN)
    }

    /**
     * FIX286 · R-SC2 — [pkg] được xếp ở ô [index] mà [presence] đo thấy KHÔNG còn task ⇒ mở lại vào đúng ô qua
     * `VdAppHost.reopen` (mở ô golden: `am force-stop` + `am start --display` màn ảo của ô; app Kachi đẩy ra sau màn nhà ⇒
     * K8, không giết). Nhịp đo cũ thôi TRƯỚC (không báo "app rời ô" giữa lượt mở lại — L6: thẻ "đã đóng" cũ đã gỡ, ô đi
     * luật hoàn ô), trạng thái "đang toàn màn" bỏ. `false` = 0 lệnh: ô không có host giữ [pkg] / đã nhả / lượt mở đang
     * chạy dở (chưa vào nhịp đo, chưa báo chết) ⇒ bên gọi chỉ nháy ô.
     */
    fun reviveInSlot(index: Int, pkg: String): Boolean = workspace().hostAt(index)?.reviveInSlot(pkg) ?: false

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private fun appLabel(pkg: String): String = InstalledApps.labelOf(app, pkg) ?: pkg

    fun assignApp(index: Int, pkg: String) {
        drawer().close()
        // MỘT-APP-MỘT-Ô + đúng vị trí đều do STATE lo (quality-review 2026-09-15, R1/R2): `viewModel.assignApp`
        // → `WorkspaceState.withSlot` tự dedup (pkg chỉ còn ở [index], ô cũ → Empty). Collector `render(state)`:
        //   • `workspace.render` dựng lại ô đổi nội-dung ⇒ `releaseSlotHost` nhả VdAppHost của ô cũ (hết khung-đóng-băng);
        //   • `windows.reconcileLocations` cập nhật registry + evict app rời ô.
        // Handler KHÔNG còn sửa tay registry (d.place/d.remove) — đó là nguồn drift "3 nguồn sự-thật" nay đã gỡ.
        viewModel.assignApp(index, pkg)
        windows().placeApp(pkg, index, fresh = true)   // đặt/di chuyển cửa sổ freeform (off-car); on-car VdAppHost do WorkspaceView
    }

    fun assignWidgets(index: Int, ids: List<String>) {
        drawer().close()
        viewModel.assignWidgets(index, ids)   // state+persist → collector: workspace.render
    }

    fun reopenApp(index: Int) {
        (viewModel.uiState.value.effectiveWorkspace.slots.getOrNull(index) as? SlotContent.App)?.let { windows().placeApp(it.pkg, index) }
    }

    /**
     * U3 — mở [pkg] **toàn màn** (đường "mở app kiểu thường"): KHÔNG ghi vào ô, KHÔNG đổi bố cục đã lưu, KHÔNG ghi
     * sổ vị trí ô. Bấm HOME là về Kachi (Kachi là HOME).
     *
     * Thứ tự do SỐ ĐO quyết định (xem bảng ở [AppOpener]): thử **đường API** trên thread chính trước (đo được là
     * tốt bằng-hoặc-hơn); chỉ khi nó thất bại mới dùng **đường shell** trên thread nền (dadb chặn).
     * Ghi nhận "gần đây" trước để lần mở ngăn kéo sau đã thấy.
     */
    fun openAppFullscreen(pkg: String) {
        drawer().close()
        runCatching { container.workspaceRepository.touchRecentApp(pkg) }
        if (appOpener.openByIntent(pkg)) return
        val sh = shell() ?: return
        submitBg { appOpener.openByShell(pkg, sh) }
    }

    fun clearSlot(index: Int) {
        // Đóng cửa sổ NGAY cho phản hồi tức thì; registry do `reconcileLocations` (render) gỡ theo state (evict).
        (viewModel.uiState.value.effectiveWorkspace.slots.getOrNull(index) as? SlotContent.App)?.let { windows().closeApp(it.pkg) }
        viewModel.clearSlot(index)   // state+persist → collector: workspace.render + reconcileLocations
    }

    /** Kéo-thả đổi chỗ 2 ô (widget/app). Registry do `reconcileLocations` (render) cập nhật theo state mới. */
    fun swapSlots(a: Int, b: Int) {
        val cur = viewModel.uiState.value
        if (a !in cur.slots.indices || b !in cur.slots.indices) return
        viewModel.swapSlots(a, b)   // state+persist → collector: workspace.render + reconcileLocations
    }
}
