package com.byd.clusternav.launcher

/** Vị trí thanh điều khiển trên viền màn (owner: đặt được 4 viền). */
enum class DockEdge { BOTTOM, LEFT, RIGHT, TOP }

/** Kiểu tile: bật/tắt hoặc −/+. */
enum class ControlKind { TOGGLE, STEP }

/**
 * Catalog 1 nút điều khiển. Hành động THẬT bơm qua [CarControlPort] (BydHal on-car; [NoCar] off-car no-op).
 * Nhãn + ngữ nghĩa map theo RE doc `docs/diagnostics/launcher-hal-re-overdrive-2026-09-08.md`.
 */
data class ControlDef(
    val id: String,
    val label: String,
    val icon: String,
    val kind: ControlKind,
    val enabledByDefault: Boolean = false,
    val onByDefault: Boolean = false,   // cho TOGGLE
    val value: Int = 0,                 // mặc định cho STEP
    val min: Int = 0,
    val max: Int = 0,
    val step: Int = 1,
) {
    fun clamp(v: Int): Int = if (kind == ControlKind.STEP) v.coerceIn(min, max) else v
}

/** Cấu hình thanh (bền qua prefs): viền + danh sách id đang hiện (thứ tự = thứ tự hiển thị). */
data class DockConfig(
    val edge: DockEdge = DockEdge.BOTTOM,
    val enabled: List<String> = ControlRegistry.defaultEnabledIds(),
) {
    fun withEdge(e: DockEdge): DockConfig = copy(edge = e)
    fun setEnabled(id: String, on: Boolean): DockConfig {
        if (ControlRegistry.byId(id) == null) return this
        val cur = enabled.toMutableList()
        if (on) { if (id !in cur) cur.add(id) } else cur.remove(id)
        return copy(enabled = cur)
    }
    fun isVertical(): Boolean = edge == DockEdge.LEFT || edge == DockEdge.RIGHT
}

object ControlRegistry {
    val ALL: List<ControlDef> = listOf(
        ControlDef("lock",    "Khoá xe",       "ic-lock",      ControlKind.TOGGLE, enabledByDefault = true, onByDefault = true),
        ControlDef("window",  "Kính 50%",      "ic-window",    ControlKind.TOGGLE, enabledByDefault = true),
        ControlDef("trunk",   "Cốp sau",       "ic-trunk",     ControlKind.TOGGLE, enabledByDefault = true),
        ControlDef("readl",   "Đèn đọc",       "ic-readlight", ControlKind.TOGGLE, enabledByDefault = true),
        ControlDef("pm25",    "Lọc bụi",       "ic-leaf",      ControlKind.TOGGLE, enabledByDefault = true, onByDefault = true),
        ControlDef("seatc",   "Ghế mát",       "ic-seat",      ControlKind.TOGGLE, enabledByDefault = true, onByDefault = true),
        ControlDef("temp",    "Nhiệt độ",      "ic-temp",      ControlKind.STEP,   enabledByDefault = true, value = 22, min = 17, max = 33, step = 1),
        ControlDef("fan",     "Gió",           "ic-fan",       ControlKind.STEP,   enabledByDefault = true, value = 4,  min = 0,  max = 7,  step = 1),
        // Có sẵn trong kho, mặc định TẮT (bật qua Tuỳ biến):
        ControlDef("defrost", "Sấy kính",      "ic-defrost",   ControlKind.TOGGLE),
        ControlDef("cam",     "Camera 360",    "ic-cam",       ControlKind.TOGGLE),
        ControlDef("door",    "Mở cửa",        "ic-door",      ControlKind.TOGGLE),
        ControlDef("hood",    "Ca-pô",         "ic-hood",      ControlKind.TOGGLE),
        ControlDef("sunroof", "Cửa sổ trời",   "ic-sunroof",   ControlKind.TOGGLE),
        ControlDef("headl",   "Đèn pha",       "ic-light",     ControlKind.TOGGLE),
        ControlDef("seath",   "Ghế sưởi",      "ic-seat",      ControlKind.TOGGLE),
        ControlDef("recirc",  "Lấy gió trong", "ic-recirc",    ControlKind.TOGGLE),
        ControlDef("drl",     "Đèn ban ngày",  "ic-light",     ControlKind.TOGGLE),
        ControlDef("vol",     "Âm lượng",      "ic-volume",    ControlKind.STEP,   value = 12, min = 0, max = 30, step = 1),
        ControlDef("wiper",   "Gạt mưa",       "ic-wiper",     ControlKind.TOGGLE),
        ControlDef("cast",    "Chiếu cụm",     "ic-cast",      ControlKind.TOGGLE),
    )

    fun byId(id: String): ControlDef? = ALL.firstOrNull { it.id == id }
    fun defaultEnabledIds(): List<String> = ALL.filter { it.enabledByDefault }.map { it.id }
    fun defaultDock(): DockConfig = DockConfig()
}
