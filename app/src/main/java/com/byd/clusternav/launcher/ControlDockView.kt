package com.byd.clusternav.launcher

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiBars as Bars
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Thanh điều khiển — thẻ kính bo góc trên nền wall. Từ RW0 (spec `kachi-unified-capability-tile.html`) nó nhận **cả
 * hai loại khả năng** ([CapabilityKind]) chứ không chỉ nút:
 *  • **HÀNH ĐỘNG** → ô bấm được, render theo [ControlKind] (TOGGLE · STEP · COVER · SELECT · BUTTON).
 *  • **ĐỌC** → ô chỉ-xem (icon + nhãn + số + đơn vị), KHÔNG bấm được.
 *
 * Cách dựng ô nằm ở [ControlTileFactory] (dùng chung với ô giữa màn) — thanh nút chỉ quyết định **cỡ ô** và **thứ tự**.
 *
 * ## Cập nhật số mà KHÔNG dựng lại thanh (ràng buộc C5)
 * [setCarStatus] chỉ gọi [ReadTile.bind] trên các ô ĐỌC đã dựng — không `removeAllViews`, không đụng ô hành động.
 * Nếu dựng lại cả thanh mỗi nhịp trạng thái xe (1 nhịp/giây) thì: thanh nháy, và ô vừa bấm mất trạng thái sáng
 * (kể cả cú nháy 220ms của BUTTON) — đúng loại lỗi P-bug1 đã trả giá một lần.
 *
 * Hành động qua [CarControlPort] ([NoCar] off-car → no-op). Tier OVERDRIVE/DASHCAST → chấm amber "chưa kiểm".
 * **KHÔNG gate**: mọi ô bấm được bất kể tốc độ/số (owner bỏ gate 2026-09-10).
 *
 * ## 2.89 · B3 DOCK-SCALE — cỡ thanh theo % ([DockConfig.scalePct], 50–150, spec `kachi-289-field-fixes` §B3)
 * Mọi thứ của thanh dựng bằng [ui] = `DockScaleContext.wrap(context, %)` ⇒ ô, icon, chữ, lề, bo góc co/giãn đúng %. Ở 100 %
 * [ui] LÀ `context` và [place] đi [sized] như cũ ⇒ từng pixel + từng vùng chạm như 2.88. Khác 100 %: mỗi ô nằm trong một
 * [DockHitCell] (khung chạm ≥ 48 dp thật dọc trục thanh × trọn bề dày thanh), lề trong thanh chỉ còn ở hai đầu trục.
 * [tileState] riêng chỉ cho dải xem trước của Cài đặt (trơ — không đụng bảng trạng thái của thanh thật).
 */
class ControlDockView(
    context: Context,
    private val tileState: ControlTileState = ControlTileState.shared,
) : LinearLayout(context) {

    var control: CarControlPort = NoCar

    /**
     * S4 · R12 — cú bấm một ô [CapabilityKind.LAUNCHER] (`launcher_apps` / `launcher_settings`).
     *
     * Mặc định **no-op** để mọi chỗ dựng cũ (kể cả test) không phải sửa; chỗ nối thật là
     * [Activity.controlDock] ở `KachiHomeWiring`, và nó gọi ĐÚNG hai đường mà thanh trên đang dùng
     * (`drawerController.openAppList()` · `panels.openSettings()`) — không mở đường thứ hai.
     */
    var onLauncherAction: (String) -> Unit = {}
    private var config = ControlRegistry.defaultDock()
    // Trạng thái xe + lựa chọn đơn vị: CHỈ dùng cho ô ĐỌC. Bơm từ Activity (một chiều, từ HomeUiState.carStatus).
    private var carStatus: CarStatus = CarStatus()
    private var unitPrefs: UnitPrefs = UnitPrefs.DEFAULT
    // Ô ĐỌC đang hiện, theo mã. Giữ tham chiếu để cập nhật TẠI CHỖ (xem setCarStatus) thay vì dựng lại.
    private val readTiles = LinkedHashMap<String, ReadTile>()
    // Ô HÀNH ĐỘNG có đường đọc: giữ hàm refresh để đổ giá trị THẬT của xe (2026-09-17) mà KHÔNG dựng lại ô.
    private val actionRefreshers = LinkedHashMap<String, (CarStatus) -> Unit>()

    /**
     * B3 — `Context` dựng mọi thứ của thanh ở cỡ [uiPct]. 100 % ⇒ CHÍNH `context` (đường cũ). Giữ trong TRƯỜNG của view
     * (sống/chết cùng Activity) — không bộ đệm toàn cục (K1, KDoc `DockScaleContext`).
     */
    private var ui: Context = context
    private var uiPct = BarScale.DEFAULT
    private var tiles = factory()

    private fun factory() = ControlTileFactory(ui, control = { control }, size = TileSize.DOCK, state = tileState)

    init {
        gravity = Gravity.CENTER
        // WP1 · R1.1 — thanh nút **KHÔNG viền**. [ĐO ảnh `after-home-dark.png`] viền cũ là vạch 1px
        // `rgb(99,103,117)` từ x=50 tới x=1868 ở y=882 = đường kẻ dễ thấy thứ hai của màn chính. Thanh tách khỏi
        // vùng ô bằng nền `BAR` + khe [Sp.SLOT_GAP] mà `DockAreaLayout` đặt.
        // R-OP — nền theo độ đục nền chung ([KachiChrome.fade]; 0 % trong suốt ⇒ y như cũ, 100 % ⇒ không tô gì).
        background = KachiChrome.fade(GradientDrawable().apply {
            cornerRadius = dpi(ui, Sp.RADIUS_XL).toFloat(); setColor(c(KachiTheme.BAR))
        })
        rebuild()
    }

    /** Cỡ đổi ⇒ dựng lại [ui] + bộ dựng ô + nền (bo góc theo cỡ) rồi ô; không đổi ⇒ chỉ dựng lại ô như trước B3. */
    fun setConfig(cfg: DockConfig) {
        config = cfg
        if (BarScale.snap(cfg.scalePct) != uiPct) rescale() else rebuild()
    }

    /**
     * B3 — bề dày thanh (px) ở cỡ hiện tại: CÙNG số với `DockAreaLayout` (`BarScale.scaledDensity` = phép `ResourcesImpl`
     * dựng mật độ của [ui] — `BarScaleTest.mat do thanh trung bit…`). Dải xem trước của Cài đặt dùng để đặt khung.
     */
    fun thicknessPx(): Int = dpi(ui, if (config.isVertical()) Bars.DOCK_WIDE else Bars.DOCK_THICK)

    private fun rescale() {
        uiPct = BarScale.snap(config.scalePct)
        ui = DockScaleContext.wrap(context, uiPct)
        tiles = factory()
        Log.i(TAG, "[bar-scale] pct=$uiPct dpi=${ui.resources.displayMetrics.densityDpi} base=${DockScaleContext.baseDpiOf(ui)}")
        restyle()
    }

    /**
     * Mật độ hệ thống đổi lúc chạy (`configChanges=density` ⇒ Activity không dựng lại): [ui] giữ `densityDpi` ghi đè TUYỆT
     * ĐỐI tính từ mật độ cũ ⇒ dựng lại theo mật độ mới. 100 % không có lớp ghi đè nên không cần gì (như trước B3).
     */
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        if (!BarScale.isIdentity(uiPct) && DockScaleContext.baseDpiOf(ui) != context.resources.configuration.densityDpi) rescale()
    }

    /**
     * Bơm trạng thái xe LIVE + lựa chọn đơn vị vào thanh (sự thật Đ4: trước RW0 thanh nút KHÔNG hề nhận trạng thái
     * xe, nên ô đọc không thể sống ở đây). Chỉ đổ lại **số của ô ĐỌC**; ô hành động không bị chạm tới.
     */
    fun setCarStatus(status: CarStatus, prefs: UnitPrefs = unitPrefs) {
        carStatus = status; unitPrefs = prefs
        readTiles.forEach { (id, tile) -> tile.bind(readout(id)) }
        // 2026-09-17 — ô HÀNH ĐỘNG cũng đọc giá trị THẬT của xe (nhiệt/gió/gió-trong/cốp…), cập nhật tại chỗ.
        actionRefreshers.values.forEach { it(status) }
    }

    /**
     * Giá trị hiển thị của một mã ĐỌC: đọc thô theo registry rồi **bắt buộc** đi qua lựa chọn đơn vị của người dùng
     * ([UnitFormat.apply] — R11/R12). Mã không phải telemetry (9 widget dựng tay) ⇒ `null` ⇒ ô hiện "—" + mờ, vì
     * chúng có bố cục riêng ở ô giữa màn chứ không có dạng một-số-một-đơn-vị để nhét vào thanh.
     *
     * **NHÓM (G1) xét TRƯỚC** và ra một dòng TÓM TẮT ("2 cảnh báo") — [GroupBoard.summaryView]. Thiếu nhánh này thì
     * nhóm rơi xuống `TelemetryReadout.of` (không có mã `g_*`) ⇒ ô hiện "—" **mãi mãi**, tức màn Cài đặt bày ra một
     * lựa chọn chết. Đơn vị đã áp bên trong `summaryView` nên không đi qua [UnitFormat] lần thứ hai.
     */
    private fun readout(id: String): TelemetryView? =
        GroupBoard.summaryView(id, carStatus, unitPrefs)
            ?: TelemetryReadout.of(id, carStatus)?.let { UnitFormat.apply(it, unitPrefs) }

    /** #10 (2026-09-23) — đổi màu theme MỚI: nền khay (BAR) + dựng lại nút (dock không giữ ô app nên an toàn). */
    fun restyle() {
        background = KachiChrome.fade(GradientDrawable().apply {
            cornerRadius = dpi(ui, Sp.RADIUS_XL).toFloat(); setColor(c(KachiTheme.BAR))
        })
        rebuild()
    }

    /**
     * WP5 · R5.1 — lề trong **[Bars.DOCK_PAD]** (trước WP5 là [Sp.S]): thanh mỏng lại 80 % mà ô chỉ nhỏ 85 % nên phần khung
     * phải nhường chỗ trước, nếu không ô 73/83dp không còn nằm trong thanh 93/99dp (số học ở KDoc [Bars.DOCK_PAD]).
     * B3 — khác 100 %: lề chỉ ở HAI ĐẦU trục; ngang trục là 0 để khung chạm ([DockHitCell]) lấp trọn bề dày thanh (hình ô
     * vẫn ở giữa ⇒ cùng chỗ như có lề).
     */
    private fun applyPad() {
        val p = dpi(ui, Bars.DOCK_PAD)
        when {
            BarScale.isIdentity(uiPct) -> setPadding(p, p, p, p)
            config.isVertical() -> setPadding(0, p, 0, p)
            else -> setPadding(p, 0, p, 0)
        }
    }

    private fun rebuild() {
        orientation = if (config.isVertical()) VERTICAL else HORIZONTAL
        applyPad()
        removeAllViews(); readTiles.clear(); actionRefreshers.clear()
        config.enabled.forEach { id ->
            when (CapabilityCatalog.kindOf(id)) {
                CapabilityKind.WRITE -> {
                    // Mã HÀNH ĐỘNG có thể là NÚT ĐƠN hoặc GÓI LỆNH (W2). Thiếu nhánh gói lệnh thì ô sẽ không hiện
                    // gì cả mà cũng không báo lỗi — người dùng bật vào thanh rồi tưởng hỏng.
                    val def = ControlRegistry.byId(id)
                    if (def != null) {
                        val at = tiles.actionTile(def)
                        actionRefreshers[id] = at.refresh; at.refresh(carStatus)   // đổ giá trị hiện có ngay khi dựng
                        // B3 — ô STEP ở thanh NGANG có HAI đích (− +) xếp dọc trục ⇒ sàn chạm tính cho hai.
                        addView(place(at.view, if (def.kind == ControlKind.STEP && !config.isVertical()) 2 else 1))
                    } else ActionMacros.byId(id)?.let { addView(place(tiles.macroTile(it))) }
                }
                CapabilityKind.READ -> CapabilityCatalog.pick(id)?.let { pick ->
                    val tile = tiles.readTile(pick)
                    tile.bind(readout(id))
                    readTiles[id] = tile
                    addView(place(tile.view))
                }
                // S4 · R12: việc của CHÍNH launcher — ô vẽ như một cái nút, nhưng cú bấm đi ra ngoài qua
                // [onLauncherAction] chứ không xuống [control]. Nhánh riêng (không gộp vào WRITE) vì gộp thì
                // `ControlRegistry.byId` trả null, `ActionMacros.byId` cũng null ⇒ ô **không được thêm vào thanh**
                // mà cũng không báo gì — đúng lỗi "bật vào thanh rồi tưởng hỏng" đã phải vá cho gói lệnh ở W2.
                // F1 (spec shortcuts-autostart R1.2): khối LỐI TẮT không phải một nút mà một hàng icon dài theo số app
                // ⇒ cỡ do chính khối đặt theo [shortcutStripLength] (không qua [sized]); bề dày = bề dày một ô.
                CapabilityKind.LAUNCHER -> if (id == LauncherActions.SHORTCUTS) addView(shortcutStrip())
                else CapabilityCatalog.pick(id)?.let { pick ->
                    addView(place(tiles.launcherTile(pick) { onLauncherAction(id) }))
                }
                null -> Unit   // mã lạ (rác prefs / mã đã xoá) → bỏ qua, KHÔNG sập
            }
        }
    }

    /**
     * Khối lối tắt (F1 · U3): bề DÀY = bề dày một ô của thanh (cùng số [Bars] với [sized]); bề DÀI do khối tự đặt theo
     * số app ([shortcutStripLength]) mỗi lần danh sách đổi — thanh dọc thì khối cao ra, ngang thì rộng ra (R1.2).
     * B3 — dựng bằng [ui] (icon + khe co/giãn); khác 100 %: khối lấp TRỌN bề dày thanh ([ShortcutIconsView.fillAcross]) và
     * mỗi khe ≥ 48 dp thật dọc trục ([shortcutSlotPx]) ⇒ mỗi icon là một đích chạm đủ cỡ dù hình nhỏ.
     */
    private fun shortcutStrip(): View = ShortcutIconsView(ui, grid = false).apply {
        val v = config.isVertical()
        val len = shortcutStripLength(ui, ShortcutHub.items().size)
        val fill = !BarScale.isIdentity(uiPct)
        val m = dpi(ui, Sp.XS)
        fillAcross = fill
        layoutParams = LayoutParams(
            if (v) (if (fill) MATCH else dpi(ui, Bars.DOCK_TILE_W_VERTICAL)) else len,
            if (v) len else (if (fill) MATCH else dpi(ui, Bars.DOCK_TILE_H)),
        ).also { if (!fill) it.setMargins(m, m, m, m) else if (v) it.setMargins(0, m, 0, m) else it.setMargins(m, 0, m, 0) }
        vertical = v
    }

    /** B3 — 100 % ⇒ [sized] y như trước; khác ⇒ ô nằm giữa một [DockHitCell] ([targets] = số đích dọc trục trong ô). */
    private fun place(tile: View, targets: Int = 1): View =
        if (BarScale.isIdentity(uiPct)) sized(tile) else hitCell(tile, targets)

    /** Cỡ ô của thanh nút (WP5: 83×60 khi dọc, 71×73 khi ngang, lề 4dp) — mọi số lấy từ [Bars]. */
    private fun sized(tile: View): View = tile.apply {
        layoutParams = LayoutParams(
            dpi(ui, if (config.isVertical()) Bars.DOCK_TILE_W_VERTICAL else Bars.DOCK_TILE_W),
            dpi(ui, if (config.isVertical()) Bars.DOCK_TILE_H_VERTICAL else Bars.DOCK_TILE_H),
        ).also { it.setMargins(dpi(ui, Sp.XS), dpi(ui, Sp.XS), dpi(ui, Sp.XS), dpi(ui, Sp.XS)) }
    }

    /**
     * B3 — khung chạm của một ô ở cỡ ≠ 100 %: hình = cùng số [Bars] với [sized] (trên [ui]); dọc trục =
     * [BarScale.cellAlongPx] (≥ 48 dp thật mỗi đích, trần = hình ở 100 % đo trên `context` GỐC); ngang trục = trọn bề dày.
     */
    private fun hitCell(tile: View, targets: Int): View {
        val v = config.isVertical()
        val w = dpi(ui, if (v) Bars.DOCK_TILE_W_VERTICAL else Bars.DOCK_TILE_W)
        val h = dpi(ui, if (v) Bars.DOCK_TILE_H_VERTICAL else Bars.DOCK_TILE_H)
        val len100 = dpi(context, if (v) Bars.DOCK_TILE_H_VERTICAL else Bars.DOCK_TILE_W)
        val along = BarScale.cellAlongPx(if (v) h else w, dpi(ui, Sp.XS), DockScaleContext.touchFloorPx(ui), targets, len100)
        return DockHitCell(ui, tile, w, h).apply {
            layoutParams = if (v) LayoutParams(MATCH, along) else LayoutParams(along, MATCH)
        }
    }

    private companion object {
        const val TAG = "KachiBar"
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
