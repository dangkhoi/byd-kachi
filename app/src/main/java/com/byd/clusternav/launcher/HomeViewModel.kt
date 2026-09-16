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

    /**
     * T4 — đặt một widget Android của app khác vào ô. [content] đã **ràng buộc xong** (có id nền tảng cấp).
     *
     * Chỉ ghi state như mọi intent khác; việc **thu hồi id cũ** của ô đó do `AppWidgetSlotHost.reclaim` làm khi thấy
     * state đổi. Cố ý KHÔNG thu hồi ở đây: ViewModel không được giữ đối tượng Android, và trộn hai việc vào một chỗ
     * là cách chắc chắn để một trong hai bị quên khi ô đổi nội dung bằng đường khác (kéo-thả, đổi hồ sơ, xoá ô).
     */
    fun assignAppWidget(slot: Int, content: SlotContent.AppWidget) =
        mutate { it.copy(workspace = it.workspace.withSlot(slot, content)) }

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

    /**
     * Đặt **cả** cấu hình thanh nút một lượt (T6 · R-UI m).
     *
     * ⚠ Thay `toggleDock(id, on)` cũ, và đó là một quyết định chứ không phải đổi tên: bộ chọn nút
     * ([DrawerController.openDockPicker]) trả về một **TẬP** người dùng vừa chốt, nên phép đổi luôn có **cả hai
     * chiều** — mã bị bỏ tích phải rời thanh. Một cổng "bật/tắt từng mã" không diễn tả được chiều tắt hàng loạt
     * ⇒ chỗ gọi sẽ viết `selected.forEach { setEnabled(it, true) }` và cấu hình chỉ **lớn lên** (xem KDoc
     * [DockSelection] — đúng bẫy đó đã có thật ở bản nháp T6).
     *
     * Phép gấp TẬP → [DockConfig] nằm ở `:core` ([DockSelection.apply], có test); ViewModel chỉ nhận kết quả.
     */
    fun setDockConfig(config: DockConfig) = mutate { it.copy(dock = config) }

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

    /**
     * S4 · R8 — **thêm hồ sơ = BẢN SAO của hồ sơ đang dùng** (owner 2026-09-14: hồ sơ nay giữ *tất cả*, nên một hồ sơ
     * mới hoàn toàn trống là thứ không ai muốn dựng — họ muốn "giống cái đang dùng rồi sửa vài chỗ").
     *
     * Cùng khuôn [addProfile]: chỉ **uỷ quyền** rồi nạp lại toàn bộ. Phép chép (mọi hậu tố theo hồ sơ + ảnh chụp cấu
     * hình ClusterNav) nằm ở tầng lưu bền — để nó ở đây thì ViewModel phải biết tên từng khoá, đúng đường mà luật
     * *"tầng trên 0 lần chạm nơi lưu"* đã đóng.
     */
    fun duplicateProfile(name: String) = reload { repository.duplicateProfile(name) }

    fun deleteProfile(name: String) = reload { repository.deleteProfile(name) }

    /** V3 · R13 — đổi tên hồ sơ (owner E5). Cùng khuôn [addProfile]: uỷ quyền rồi nạp lại toàn bộ. */
    fun renameProfile(old: String, new: String) = reload { repository.renameProfile(old, new) }

    /**
     * Câu tóm tắt bố cục của MỘT hồ sơ (owner 2026-09-14) — **đọc**, không đổi state, không ghi bền.
     *
     * Ở đây chứ không ở tầng UI vì nó phải đọc **hồ sơ KHÔNG đang dùng**: [HomeUiState] chỉ mang hồ sơ đang dùng, nên
     * chỗ vẽ sẽ bị cám dỗ tự mở một `WorkspacePrefs` thứ hai — đúng đường đọc bền đi vòng mà [SOÁT P1-1] đã dọn. Câu
     * chữ do `:core` ([ProfileNames.summary]) gấp, nên nó kiểm được off-car ở cả hai thứ tiếng.
     */
    fun profileSummary(name: String): String {
        val (preset, filled) = repository.profileLayout(name)
        return ProfileNames.summary(preset, filled)
    }

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
        persistLayout(layout)
    }

    /**
     * ĐÚNG MỘT chỗ trong ViewModel nói với cổng lưu bố cục.
     *
     * Bố cục nằm ở **khoá riêng**, không đi qua `persist()`, nên nó là thứ dễ bị bỏ sót nhất khi thêm một đường ghi
     * mới: quên gọi thì màn hình đổi bố cục mà lần mở sau lại về bố cục cũ. Gom về một hàm để `GridSeamGuardTest`
     * (*"setGridLayout phải được gọi ở ĐÚNG MỘT chỗ"*) vẫn đúng **theo nghĩa nó muốn** khi có đường ghi thứ hai —
     * S4 · R1 đã gỡ đường thứ hai (`applyScene`), nhưng luật thì giữ.
     */
    private fun persistLayout(layout: GridLayout?) = repository.setGridLayout(layout)

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

    /**
     * **Sổ địa chỉ** của hồ sơ đang dùng — state + lưu bền trong MỘT lượt, cùng khuôn mẫu [setTopStrip].
     *
     * Không đi qua [mutate]/`persist` vì khoá này nằm ngoài bộ khoá mà `persist` ghi (đúng như đơn vị, hình nền,
     * chip thanh trên). Phép thêm/sửa/xoá là hàm thuần ở `:core` ([SavedPlaces]); ở đây chỉ nhận danh sách đã chốt.
     */
    fun setSavedPlaces(places: List<SavedPlace>) {
        _uiState.update { it.copy(savedPlaces = places) }
        repository.setSavedPlaces(places)
    }

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

    /**
     * S4 · R6 — **hồ sơ lúc nổ máy**; `null` = *"hồ sơ dùng gần nhất"*. State + lưu bền trong MỘT lượt, cùng khuôn
     * [setAutostart].
     *
     * ⚠ Khoá này theo **XE** chứ không theo hồ sơ (R4: nó CHỌN hồ sơ nên phải đọc được trước khi biết hồ sơ nào), nên
     * nó **không** đi qua [mutate]/`persist` — `persist` ghi vào khoá mang tiền tố hồ sơ đang dùng.
     *
     * Vẫn nằm trong [HomeUiState] vì màn Cài đặt **render** nó: để nó ngoài state thì chỗ vẽ phải tự đọc nơi lưu, tức
     * đường đọc bền thứ hai ở tầng UI ([SOÁT P1-1]).
     */
    fun setBootProfile(name: String?) {
        _uiState.update { it.copy(bootProfile = name) }
        repository.setBootProfile(name)
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
