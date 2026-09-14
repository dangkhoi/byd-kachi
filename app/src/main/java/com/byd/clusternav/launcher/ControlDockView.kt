package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
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
 */
class ControlDockView(context: Context) : LinearLayout(context) {

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
    private val tiles = ControlTileFactory(context, control = { control }, size = TileSize.DOCK)

    init {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = dpi(context, Sp.RADIUS_XL).toFloat(); setColor(c(KachiTheme.BAR)); setStroke(dpi(context, Sp.HAIRLINE), c(KachiTheme.LINE_STRONG))
        }
        val p = dpi(context, Sp.S); setPadding(p, p, p, p)
        rebuild()
    }

    fun setConfig(cfg: DockConfig) { config = cfg; rebuild() }

    /**
     * Bơm trạng thái xe LIVE + lựa chọn đơn vị vào thanh (sự thật Đ4: trước RW0 thanh nút KHÔNG hề nhận trạng thái
     * xe, nên ô đọc không thể sống ở đây). Chỉ đổ lại **số của ô ĐỌC**; ô hành động không bị chạm tới.
     */
    fun setCarStatus(status: CarStatus, prefs: UnitPrefs = unitPrefs) {
        carStatus = status; unitPrefs = prefs
        readTiles.forEach { (id, tile) -> tile.bind(readout(id)) }
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

    private fun rebuild() {
        orientation = if (config.isVertical()) VERTICAL else HORIZONTAL
        removeAllViews(); readTiles.clear()
        config.enabled.forEach { id ->
            when (CapabilityCatalog.kindOf(id)) {
                CapabilityKind.WRITE -> {
                    // Mã HÀNH ĐỘNG có thể là NÚT ĐƠN hoặc GÓI LỆNH (W2). Thiếu nhánh gói lệnh thì ô sẽ không hiện
                    // gì cả mà cũng không báo lỗi — người dùng bật vào thanh rồi tưởng hỏng.
                    val def = ControlRegistry.byId(id)
                    if (def != null) addView(sized(tiles.actionTile(def)))
                    else ActionMacros.byId(id)?.let { addView(sized(tiles.macroTile(it))) }
                }
                CapabilityKind.READ -> CapabilityCatalog.pick(id)?.let { pick ->
                    val tile = tiles.readTile(pick)
                    tile.bind(readout(id))
                    readTiles[id] = tile
                    addView(sized(tile.view))
                }
                // S4 · R12: việc của CHÍNH launcher — ô vẽ như một cái nút, nhưng cú bấm đi ra ngoài qua
                // [onLauncherAction] chứ không xuống [control]. Nhánh riêng (không gộp vào WRITE) vì gộp thì
                // `ControlRegistry.byId` trả null, `ActionMacros.byId` cũng null ⇒ ô **không được thêm vào thanh**
                // mà cũng không báo gì — đúng lỗi "bật vào thanh rồi tưởng hỏng" đã phải vá cho gói lệnh ở W2.
                CapabilityKind.LAUNCHER -> CapabilityCatalog.pick(id)?.let { pick ->
                    addView(sized(tiles.launcherTile(pick) { onLauncherAction(id) }))
                }
                null -> Unit   // mã lạ (rác prefs / mã đã xoá) → bỏ qua, KHÔNG sập
            }
        }
    }

    /** Cỡ ô của thanh nút (100×70 khi dọc, 84×86 khi ngang, lề 4dp) — mọi số lấy từ [Sp]. */
    private fun sized(tile: View): View = tile.apply {
        layoutParams = LayoutParams(
            dpi(context, if (config.isVertical()) Sp.DOCK_TILE_W_VERTICAL else Sp.DOCK_TILE_W),
            dpi(context, if (config.isVertical()) Sp.DOCK_TILE_H_VERTICAL else Sp.DOCK_TILE_H),
        ).also { it.setMargins(dpi(context, Sp.XS), dpi(context, Sp.XS), dpi(context, Sp.XS), dpi(context, Sp.XS)) }
    }
}
