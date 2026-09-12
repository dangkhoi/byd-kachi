package com.byd.clusternav.launcher

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/**
 * NGUỒN SỰ THẬT DUY NHẤT cho HOME (Kachi): giữ [HomeUiState] trong một [StateFlow] read-only.
 * Trạng thái launcher KHÔNG còn nằm trong [WorkspaceView] nữa — view chỉ `render(state)` + phát event lên.
 *
 * Luồng MỘT CHIỀU: view/user event → intent (hàm dưới) → cập nhật `_uiState` (immutable copy) + ghi bền qua
 * [WorkspaceRepository] → `uiState` phát → Activity thu (`repeatOnLifecycle`) → render.
 *
 * Kế thừa [androidx.lifecycle.ViewModel] (chuẩn hiện hành: có sẵn `viewModelScope`, sống qua config-change).
 * Các intent ở đây cập nhật state ĐỒNG BỘ + ghi bền ĐỒNG BỘ (SharedPreferences `apply()` vốn ghi nền) — giữ đúng
 * hành vi cũ (Activity trước cũng `prefs.save(...)` ngay trên main). Không dùng coroutine cho ghi để test tất định.
 */
class HomeViewModel(
    private val repository: WorkspaceRepository,
    initialEmbedded: Boolean = false,
) : ViewModel() {

    private val _uiState = MutableStateFlow(repository.load().copy(embedded = initialEmbedded))

    /** Trạng thái HOME hiện tại — nguồn sự thật duy nhất cho toàn bộ view của launcher. */
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // ── Intent: workspace (ô + bố cục) ───────────────────────────────────────────
    fun setPreset(preset: LayoutPreset) = mutate { it.copy(workspace = it.workspace.withPreset(preset)) }

    fun assignApp(slot: Int, pkg: String) =
        mutate { it.copy(workspace = it.workspace.withSlot(slot, SlotContent.App(pkg))) }

    fun assignWidgets(slot: Int, ids: List<String>) = mutate {
        val content = if (ids.isEmpty()) SlotContent.Empty else SlotContent.Widget(ids)
        it.copy(workspace = it.workspace.withSlot(slot, content))
    }

    fun clearSlot(slot: Int) = mutate { it.copy(workspace = it.workspace.clearSlot(slot)) }

    fun swapSlots(a: Int, b: Int) = mutate { it.copy(workspace = it.workspace.swap(a, b)) }

    // ── Intent: dock (thanh điều khiển) ──────────────────────────────────────────
    /**
     * Đặt THẲNG một viền — đường DUY NHẤT. Màn Cài đặt → Màn hình chính → "Viền đặt thanh" bày cả 4 viền.
     *
     * ⚠ `cycleDockEdge()` (xoay vòng BOTTOM → LEFT → RIGHT → TOP) đã **XOÁ** cùng lúc với pill "Thanh" ở thanh trên:
     * sau khi gỡ pill nó không còn chỗ gọi nào ⇒ mã chết. Xoay vòng cũng là hình dạng SAI cho bề mặt hiện tại — khi
     * cả 4 viền đang hiện ra thì bấm "Phải" phải ra "Phải", chứ không phải viền kế tiếp.
     */
    fun setDockEdge(edge: DockEdge) = mutate { it.copy(dock = it.dock.withEdge(edge)) }

    /** Bật/tắt một control trong thanh (danh sách control hiện) — [DockConfig.setEnabled]. */
    fun toggleDock(id: String, on: Boolean) = mutate { it.copy(dock = it.dock.setEnabled(id, on)) }

    // ── Intent: theme ────────────────────────────────────────────────────────────
    fun setThemeMode(mode: ThemeMode) = mutate { it.copy(themeMode = mode) }

    /**
     * U5·T3 — NGÔN NGỮ. State + lưu bền trong MỘT lượt, cùng khuôn [setAutostart]/[setTopStrip]: khoá này nằm ngoài
     * bộ khoá theo hồ sơ (chung cả máy) nên không đi qua [mutate]/`persist`.
     *
     * Chỉ ghi *lựa chọn*. Việc giải nghĩa ra ngôn ngữ thật (`Strings.current` + locale của `Context`) là của `LangHost`
     * ở `:app`, gọi từ `attachBaseContext` — tức nó chạy lại **mỗi lần Activity được dựng**, kể cả lượt `recreate()`
     * sau khi đổi ngôn ngữ. Đặt việc đó ở đây thì `ViewModel` phải biết Android và ngôn ngữ sẽ chỉ đúng ở lượt đổi,
     * không đúng ở lượt mở app kế tiếp.
     */
    fun setLangMode(mode: LangMode) {
        _uiState.update { it.copy(langMode = mode) }
        repository.setLangMode(mode)
    }

    // ── Intent: hồ sơ tài xế (uỷ quyền repository re-scope prefs + nạp lại hồ sơ đó) ──
    fun switchProfile(name: String) = reload { repository.switchProfile(name) }

    fun addProfile(name: String) = reload { repository.addProfile(name) }

    fun deleteProfile(name: String) = reload { repository.deleteProfile(name) }

    // ── Runtime host capability (không bền) ─────────────────────────────────────
    /** Cập nhật cờ nhúng (dadb loopback nối được / ROM platform-signed). Chỉ runtime, KHÔNG ghi bền. */
    fun setEmbedded(embedded: Boolean) = _uiState.update { it.copy(embedded = embedded) }

    /**
     * Cập nhật trạng thái xe LIVE (do Activity thu từ `CarStatusRepository.status` qua `repeatOnLifecycle` rồi bơm
     * vào — MỘT CHIỀU). Chỉ runtime, KHÔNG ghi bền (off-car mọi field null ⇒ widget "—").
     */
    fun setCarStatus(status: CarStatus) = _uiState.update { it.copy(carStatus = status) }

    // ── Bền, nhưng lưu ở KHOÁ RIÊNG (không nằm trong `persist`) ─────────────────
    // Ba intent dưới đây tồn tại để tầng UI KHÔNG tự gọi repository: trước đây màn chính ghi thẳng
    // `workspaceRepository.setGridLayout/setUnitPrefs/setWallpaperPrefs`, tức có đường ghi bền đi VÒNG qua
    // ViewModel ⇒ state trên màn và state đã lưu có thể lệch nhau mà không ai phát hiện.

    /** Bố cục tự vẽ (P9). `null` = quay về bố cục sẵn. Cập nhật state + lưu bền trong MỘT lượt. */
    fun setCustomLayout(layout: GridLayout?) {
        _uiState.update { it.copy(customLayout = layout) }
        repository.setGridLayout(layout)
    }

    /** Lựa chọn đơn vị (R11). */
    fun setUnitPrefs(prefs: UnitPrefs) {
        _uiState.update { it.copy(unitPrefs = prefs) }
        repository.setUnitPrefs(prefs)
    }

    /** Chip thanh trên (RW0 vùng thứ ba) — state + lưu bền trong MỘT lượt. */
    fun setTopStrip(config: TopStripConfig) {
        _uiState.update { it.copy(topStrip = config) }
        repository.setTopStrip(config)
    }

    /** Bật/tắt một chip. Luật (trần 4 · chỉ nhận mục ĐỌC) nằm ở `:core`, đây chỉ chuyển tiếp. */
    fun toggleTopStrip(id: String, on: Boolean) = setTopStrip(_uiState.value.topStrip.setEnabled(id, on))

    /** Lựa chọn hình nền (U4). */
    fun setWallpaperPrefs(prefs: WallpaperPrefs) {
        _uiState.update { it.copy(wallpaper = prefs) }
        repository.setWallpaperPrefs(prefs)
    }

    /**
     * S1·T4 — **tự mở khi nổ máy**. State + lưu bền trong MỘT lượt, cùng khuôn mẫu [setTopStrip].
     *
     * Không đi qua [mutate]/`persist` vì khoá này nằm ngoài bộ khoá theo hồ sơ (chung cả máy), đúng như đơn vị và
     * hình nền.
     */
    fun setAutostart(on: Boolean) {
        _uiState.update { it.copy(autostart = on) }
        repository.setAutostart(on)
    }

    /** Cập nhật state (atomic) rồi ghi bền phần lưu-được. */
    private fun mutate(block: (HomeUiState) -> HomeUiState) {
        val next = _uiState.updateAndGet(block)
        repository.persist(next)
    }

    /** Nạp lại state từ repository (đổi/thêm/xoá hồ sơ) — giữ nguyên cờ [HomeUiState.embedded] runtime. */
    private fun reload(loader: () -> HomeUiState) {
        val embedded = _uiState.value.embedded
        _uiState.value = loader().copy(embedded = embedded)
    }
}
