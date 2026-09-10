package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi

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
 * Nếu dựng lại cả thanh mỗi nhịp trạng thái xe (2 nhịp/giây) thì: thanh nháy, và ô vừa bấm mất trạng thái sáng
 * (kể cả cú nháy 220ms của BUTTON) — đúng loại lỗi P-bug1 đã trả giá một lần.
 *
 * Hành động qua [CarControlPort] ([NoCar] off-car → no-op). Tier OVERDRIVE/DASHCAST → chấm amber "chưa kiểm".
 * **KHÔNG gate**: mọi ô bấm được bất kể tốc độ/số (owner bỏ gate 2026-09-10).
 */
class ControlDockView(context: Context) : LinearLayout(context) {

    var control: CarControlPort = NoCar
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
            cornerRadius = dpi(context, 22).toFloat(); setColor(c("#d915191f")); setStroke(dpi(context, 1), c("#33ffffff"))
        }
        val p = dpi(context, 8); setPadding(p, p, p, p)
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
     * ([UnitFormat.apply] — R11/R12). Mã không phải telemetry (8 widget dựng tay) ⇒ `null` ⇒ ô hiện "—" + mờ, vì
     * chúng có bố cục riêng ở ô giữa màn chứ không có dạng một-số-một-đơn-vị để nhét vào thanh.
     */
    private fun readout(id: String): TelemetryView? =
        TelemetryReadout.of(id, carStatus)?.let { UnitFormat.apply(it, unitPrefs) }

    private fun rebuild() {
        orientation = if (config.isVertical()) VERTICAL else HORIZONTAL
        removeAllViews(); readTiles.clear()
        config.enabled.forEach { id ->
            when (CapabilityCatalog.kindOf(id)) {
                CapabilityKind.WRITE -> ControlRegistry.byId(id)?.let { addView(sized(tiles.actionTile(it))) }
                CapabilityKind.READ -> CapabilityCatalog.pick(id)?.let { pick ->
                    val tile = tiles.readTile(pick)
                    tile.bind(readout(id))
                    readTiles[id] = tile
                    addView(sized(tile.view))
                }
                null -> Unit   // mã lạ (rác prefs / mã đã xoá) → bỏ qua, KHÔNG sập
            }
        }
    }

    /** Cỡ ô của thanh nút — y hệt bản trước RW0 (100×70 khi dọc, 84×86 khi ngang, lề 4dp). */
    private fun sized(tile: View): View = tile.apply {
        layoutParams = LayoutParams(
            dpi(context, if (config.isVertical()) 100 else 84),
            dpi(context, if (config.isVertical()) 70 else 86),
        ).also { it.setMargins(dpi(context, 4), dpi(context, 4), dpi(context, 4), dpi(context, 4)) }
    }
}
