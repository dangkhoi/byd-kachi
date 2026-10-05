package com.byd.clusternav.launcher

/**
 * ═══ Áp state lên VIEW + chọn bố cục — nửa "vẽ" của [KachiHomeActivity] ═══
 *
 * Tách THUẦN khỏi `KachiHomeActivity.kt` (572 dòng → trần 500, L6-debt 2026-09-27): thân hàm giữ nguyên byte, chỉ đổi
 * `private fun` thành hàm mở rộng `internal` cùng package (cùng khuôn `KachiHomeWiring.kt` · `KachiHomeSlots`). Các field
 * chúng đọc (`workspace` · `dock` · `topStrip` · `windows` · `panels` · `appWidgets` · `cameraSignal` · `shownState` ·
 * `mainArea` · `rootFrame` · `wall` · `viewModel`) là `internal` của Activity — không có luật mới, không có đường vẽ thứ hai.
 */

/**
 * Áp [state] lên VIEW (duy nhất một chỗ, do collector gọi) — chỉ đọc-vẽ, KHÔNG đổi state. Diff so với [shownState]
 * để chỉ làm việc khi phần liên quan đổi. Side-effect cửa sổ theo-ô ở handler; ở đây chỉ reflow khi preset/viền đổi.
 */
/**
 * #10 (owner 2026-09-23) — ĐỔI MÀU theme mà GIỮ STATE, KHÔNG recreate Activity: re-áp bảng màu mới lên các
 * view chrome (nền · thanh trên · thanh nút · chrome ô), GIỮ ô App đang chiếu (VdAppHost sống ⇒ app KHÔNG
 * restart). Nguyên tắc launcher: dù đổi gì màn cũng chạy tiếp.
 */
internal fun KachiHomeActivity.applyThemeInPlace() {
    runCatching { KachiGlass.refresh(rootFrame) }; wall.restyle()   // nền kính + màu nền tổng theo palette (#4)
    topStrip.restyle(); dock.restyle(); workspace.restyle()            // chrome đổi màu; ô App giữ nguyên (app chạy tiếp)
}

/**
 * Soát vòng 2 [P3] — bảng lệnh cuối vừa đổi bởi `:wake` (cầu `ControlSentRelay` → `ControlLastSent.absorb`) ⇒ ô nút vẽ lại
 * NGAY với trạng thái xe ĐANG CÓ: thanh nút ([ControlDockView.setCarStatus] — đổ lại ô đọc + ô hành động, không dựng lại)
 * và ô hành động giữa màn ([WidgetRefreshers.resyncActions]). Hàm đổ của ô so hình với bảng (`TileResync.stale`) nên ô đã
 * khớp không vẽ lại. Gọi từ [collectHome] (luồng chính).
 */
internal fun KachiHomeActivity.resyncTiles() {
    val car = viewModel.uiState.value.carStatus
    dock.setCarStatus(car)
    WidgetRefreshers.resyncActions(workspace, car)
}

internal fun KachiHomeActivity.render(state: HomeUiState) {
    val prev = shownState
    // #10 (2026-09-23) GIỮ STATE: theme đổi ⇒ restyle tại chỗ (không recreate=không giết ô app); chỉ LANG mới recreate.
    val themeChanged = ThemeHost.sync(state)
    if (LangHost.changed(prev, state) && prev != null) { recreate(); return }
    if (themeChanged && prev != null) applyThemeInPlace()
    // T4: thu hồi id ở ĐÚNG chỗ diff này ⇒ mọi đường đổi đều qua đây. CẢ state, vì "còn dùng" tính cả sổ cảnh.
    prev?.let { appWidgets.reclaim(it, state) }
    // 2.87 · R-AH3 — công tắc "Tự ẩn nút ⇄" đổi (hoặc lượt đầu / đổi hồ sơ) ⇒ áp lại ⇄ TẠI CHỖ, không dựng lại ô. Đặt TRƯỚC
    // `workspace.render` để ô dựng trong lượt này đăng ký với đúng cờ của hồ sơ.
    if (prev?.slotHeadAutoHide != state.slotHeadAutoHide) workspace.setSlotHeadAutoHide(state.slotHeadAutoHide)
    // Màn vẽ bố cục ĐANG HIỆN (lớp lưu + lớp tạm — đính chính owner 01/10); ô có mốc đặt-tạm mới ⇒ đổi app tại chỗ.
    // Pass 3 · whole-r2-2: lượt dựng lại do ĐỔI HỒ SƠ nhả app rời ô như 2.88 (không đỗ ô 7) — `SlotParkPlan.leave`.
    workspace.render(state.effectiveWorkspace, state.carStatus, WorkspaceRenderPlanner.swapCandidates(prev?.swapNonce, state.swapNonce),
        profileSwitch = prev != null && prev.activeProfile != state.activeProfile)
    // R1/R2 (quality-review 2026-09-15): registry vị-trí-app là PROJECTION của state — reconcile MỖI render ở
    // ĐÚNG MỘT chỗ, thay các lệnh d.place/d.remove sửa tay ở handler (nguồn drift "3 nguồn sự-thật"). Đọc-vẽ,
    // không đổi state. `WorkspaceView.render` phía trên đã lo VdAppHost theo-ô; đây lo registry + evict app rời ô.
    windows.reconcileLocations(state.effectiveWorkspace.slots)
    // F1 — danh sách lối tắt (theo hồ sơ) tới khối thanh nút + widget `w_apps`; giống hệt bản đang có ⇒ không làm gì.
    shortcuts.publish(state)
    // WP4 — thứ tự vật trên thanh trên đổi ⇒ ĐẶT LẠI CHỖ (không dựng lại view — `KachiTopStrip.setLayout`).
    if (prev?.header != state.header) topStrip.setLayout(state.header)
    // ⚠ xét CẢ `topStrip`: thiếu nó thì đổi danh sách chip mà màn hình không đổi gì (off-car trạng thái xe gần như không đổi).
    if (prev == null || prev.carStatus != state.carStatus || prev.topStrip != state.topStrip) {
        topStrip.refreshChips(state.carStatus, unitPrefs, state.topStrip)
        // RW0/Đ4: thanh nút cũng cần trạng thái xe để ô ĐỌC sống được ở đó. CHỈ đổ lại số của ô đọc — KHÔNG
        // dựng lại thanh (C5: dựng lại mỗi nhịp 1/giây sẽ nháy + mất trạng thái ô vừa bấm).
        dock.setCarStatus(state.carStatus, unitPrefs)
        workspace.setUnitPrefs(unitPrefs)   // R11: ô giữa màn cũng theo lựa chọn đơn vị (tự bỏ qua nếu không đổi)
        cameraSignal.tick()   // camera theo xi-nhan (tự đọc xi-nhan qua HAL — findings 2026-09-23; pref mặc định TẮT)
    }
    // ⚠ S4 · R7 — KHÔNG còn dải nút bố cục trên thanh trên nên ở đây không còn gì để tô sáng. Ô đang sáng của
    // bố cục sẵn nay chỉ nằm trong Cài đặt › Màn hình chính, và trang đó tự dựng lại khi state đổi.
    if (prev?.dock != state.dock) {
        // Đặt lại chỗ thanh nút khi ĐỔI VIỀN hoặc ĐỔI cờ ẩn/hiện (S1b): cả hai đều đổi vị trí/việc gắn của
        // thanh nút trong `mainArea`, mà `dock.setConfig` chỉ đổi nút BÊN TRONG thanh, không gắn/tháo thanh.
        // Thiếu nhánh `visible` thì bật/tắt "Hiện thanh nút" không có tác dụng tới khi đổi viền/dựng lại màn.
        // ⚠ R-A1 (PROFILE-SWITCH-SLOTS): chạy SAU `workspace.render` ở trên ⇒ vùng ô phải ở yên trong cây view, nếu
        // không ô app vừa dựng bị nhả ngay (đen mãi). `DockAreaLayout.apply` chỉ tháo/gắn thanh nút — [DockAreaPlan].
        // 2.89 · B3 — đổi CỠ thanh (thả tay ở Cài đặt / đổi hồ sơ) đổi bề dày ⇒ cùng đường đặt lại như đổi viền.
        val layoutChanged = prev != null &&
            (prev.dock.edge != state.dock.edge || prev.dock.visible != state.dock.visible || prev.dock.scalePct != state.dock.scalePct)
        dock.setConfig(state.dock)
        if (layoutChanged) DockAreaLayout.apply(mainArea, workspace, dock, state.dock, resources.displayMetrics.density)
    }
    // Đổi/thêm/xoá hồ sơ nạp lại TOÀN BỘ state ⇒ trang đã dựng của màn Cài đặt (nếu đang mở) trở nên cũ. Đi theo
    // đường một chiều: state đổi → render → bảng dựng lại. ⚠ phải xét CẢ `profiles`: xoá một hồ sơ KHÔNG phải hồ
    // sơ đang dùng thì `activeProfile` không đổi, và nếu chỉ xét nó thì danh sách trên màn vẫn còn hồ sơ vừa xoá.
    if (prev == null || prev.activeProfile != state.activeProfile || prev.profiles != state.profiles) {
        topStrip.setProfile(state.activeProfile)
        panels.invalidateSettings()
    }
    // [SOÁT P1-1 kiến trúc] Bố cục tự vẽ đẩy xuống view ở ĐÚNG MỘT CHỖ: theo state, khi state đổi. Trước đây chỗ
    // này tự đọc lại repository khi đổi hồ sơ (đường đọc bền nằm trong tầng UI) còn việc đẩy xuống view thì ở hàm
    // khác ⇒ hai đường song song. Nay `load()`/`switchProfile()` đã nạp bố cục vào state nên ca đó tự đúng.
    // S4 · R6: hồ sơ lúc nổ máy đổi ⇒ chip "Gần nhất/<tên>" phải vẽ lại (trang Cài đặt được nhớ nên không tự
    // dựng lại; thiếu dòng này thì chọn hồ sơ nổ máy là "màn hình không đổi gì" — họ lỗi nút bố cục sẵn ở P9).
    if (prev != null && prev.bootProfile != state.bootProfile) panels.invalidateSettings()
    if (prev?.customLayout != state.customLayout) {
        workspace.setCustomLayout(state.customLayout)
        windows.reflow()
    }
    // Ẩn/hiện thanh (S1b) cũng đổi KÍCH THƯỚC vùng ô (ẩn ⇒ ô lấp trọn màn), nên cửa sổ app đặt trong ô phải đặt
    // lại theo khung mới — cùng lý do đổi viền/bố cục.
    if (prev != null &&
        (prev.preset != state.preset || prev.dock.edge != state.dock.edge || prev.dock.visible != state.dock.visible ||
            prev.dock.scalePct != state.dock.scalePct)
    ) {
        windows.reflow()
    }
    shownState = state
    windows.updateOverlayHeads()
}


/**
 * Chọn một bố cục sẵn — **đường DUY NHẤT**, dùng cho cả 5 nút ở thanh trên lẫn dãy chip trong màn Cài đặt (§4.5).
 *
 * [ĐO] P9: bấm bố cục sẵn trong khi đang dùng bố cục tự vẽ thì trước đây **màn hình không đổi gì** (bố cục tự vẽ
 * vẫn thắng) nhưng vẫn **dựng lại TOÀN BỘ ô** — người dùng tưởng nút hỏng, còn app trong ô thì bị nhả/gắn vô ích.
 * Hành động tường minh của người dùng phải có tác dụng ⇒ chọn bố cục sẵn = BỎ bố cục tự vẽ. Đây cũng là đường quay
 * về bố cục sẵn mà không phải mở bảng vẽ.
 *
 * Phải đi qua [applyCustomLayout]: [ĐO] xoá riêng biến ở đây thì khung vẽ vẫn giữ BẢN SAO của nó ⇒ cấu hình đã xoá
 * mà màn hình vẫn hiện bố cục tự vẽ. Một đường duy nhất, có test canh.
 */
internal fun KachiHomeActivity.selectPreset(preset: LayoutPreset) {
    if (customLayout != null) applyCustomLayout(null)
    viewModel.setPreset(preset)
}

/**
 * Áp bố cục tự vẽ = **ghi vào nguồn sự thật, hết**. Việc đẩy xuống màn hình + sắp lại cửa sổ app do `render()`
 * làm khi state đổi (một chiều). Trước đây hàm này tự gán field riêng + tự ghi bền + tự đẩy xuống view, tức
 * ba việc ở một chỗ và không ai bảo đảm ba việc đó thấy cùng một giá trị.
 */
internal fun KachiHomeActivity.applyCustomLayout(layout: GridLayout?) = viewModel.setCustomLayout(layout)
