package com.byd.clusternav.launcher

import com.byd.clusternav.AppContainer

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
) {

    fun assignApp(index: Int, pkg: String) {
        drawer().close()
        val prev = viewModel.uiState.value.slots.getOrNull(index) as? SlotContent.App
        viewModel.assignApp(index, pkg)                                       // state+persist → collector: workspace.render
        val d = container.windowDispatcher
        if (prev != null && prev.pkg != pkg) d.remove(prev.pkg)               // ô thay app khác → gỡ app cũ khỏi registry
        d.place(pkg, 0, index)                                                // app mới chiếm ô index trên display 0
        windows().placeApp(pkg, index, fresh = true)
    }

    fun assignWidgets(index: Int, ids: List<String>) {
        drawer().close()
        viewModel.assignWidgets(index, ids)   // state+persist → collector: workspace.render
    }

    fun reopenApp(index: Int) {
        (viewModel.uiState.value.slots.getOrNull(index) as? SlotContent.App)?.let { windows().placeApp(it.pkg, index) }
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
        val cur = viewModel.uiState.value
        (cur.slots.getOrNull(index) as? SlotContent.App)?.let { app ->
            windows().closeApp(app.pkg)
            container.windowDispatcher.remove(app.pkg)   // ô đóng → gỡ vị trí (bất biến MỘT-VỊ-TRÍ)
        }
        viewModel.clearSlot(index)   // state+persist → collector: workspace.render + updateOverlayHeads
    }

    /** Kéo-thả đổi chỗ 2 ô (widget/app). */
    fun swapSlots(a: Int, b: Int) {
        val cur = viewModel.uiState.value
        if (a !in cur.slots.indices || b !in cur.slots.indices) return
        viewModel.swapSlots(a, b)   // state+persist → collector: workspace.render
        val ns = viewModel.uiState.value
        val d = container.windowDispatcher   // 2 ô đổi chỗ → cập nhật lại index vị trí của app (nếu có) ở mỗi ô
        (ns.slots.getOrNull(a) as? SlotContent.App)?.let { d.place(it.pkg, 0, a) }
        (ns.slots.getOrNull(b) as? SlotContent.App)?.let { d.place(it.pkg, 0, b) }
    }
}
