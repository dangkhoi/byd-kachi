package com.byd.clusternav.launcher

/**
 * Cổng ra XE (Port/Adapter). Mọi thứ chạm xe/freeform nằm sau các interface này → lõi launcher thuần Android,
 * test được trên emulator + JVM. Adapter thật (freeform am/dadb + BydHal) ở :app; off-car/emulator dùng [NoCar].
 */

/** Mở/di/đóng một app THẬT trong một ô workspace. Adapter xe reuse cast: freeform (`am --windowingMode 5`) + `am task resize` theo [SlotRect]. */
interface AppLauncher {
    /** Phóng [pkg] vào khung [slot] trên display chính. Trả true nếu đã phát lệnh đặt bound thành công. */
    fun openInSlot(pkg: String, slot: SlotRect): Boolean
    /** Đặt LẠI khung cho [pkg] ĐANG chạy (chỉ resize, KHÔNG relaunch → không cướp focus/flash). Fallback về [openInSlot] nếu chưa chạy/đang fullscreen. */
    fun moveToSlot(pkg: String, slot: SlotRect): Boolean
    /** Đóng/đưa [pkg] ra khỏi ô (trả fullscreen / dừng). */
    fun closeSlot(pkg: String)
    /** Thiết bị có hỗ trợ freeform (đặt nhiều cửa sổ theo bound) không. Emulator thường false → UI fallback/placeholder. */
    fun isFreeformAvailable(): Boolean
}

/** Đọc dữ liệu xe LIVE cho widget/thanh trạng thái. Off-car trả null → UI hiện "—". */
interface CarDataPort {
    fun batteryPercent(): Int?
    fun rangeKm(): Int?
    /** Áp suất 4 lốp (bar): [trước-trái, trước-phải, sau-trái, sau-phải]. */
    fun tirePressuresBar(): List<Double>?
    /** Mức PM2.5 (1..6) hoặc null nếu chưa đọc được. */
    fun pm25Level(): Int?
    fun speedKmh(): Int?
    fun outsideTempC(): Int?
}

/** Bơm một hành động điều khiển tới xe (BydHal). Off-car = no-op (trả false). KHÔNG gate an toàn (owner bỏ 2026-09-10). */
interface CarControlPort {
    /** TOGGLE bật/tắt. */
    fun toggle(id: String, on: Boolean): Boolean
    /** STEP đặt giá trị (nhiệt/quạt/độ sáng…). */
    fun step(id: String, value: Int): Boolean
    /** COVER mở (true) / đóng (false) kính·nóc·rèm·cốp. */
    fun cover(id: String, open: Boolean): Boolean
    /**
     * COVER theo MỨC (T7, owner 2026-09-15 "nút mở cửa 50%"): [level] = chỉ số trong `ControlDef.args`
     * (0=Đóng · 1=Mở · 2=Nửa…). Mặc định gập về [cover] (0→đóng, ≥1→mở) nên MỌI impl cũ giữ nguyên hành vi;
     * impl thật ([CarControlAdapter]) override để đưa thẳng mức xuống `HalBindingTable.writeArgs`
     * (kính: 2→`WINDOW_OPEN_HALF`=4). Không đổi chữ ký [cover] — voice/dock cũ không phải sửa.
     */
    fun coverLevel(id: String, level: Int): Boolean = cover(id, level > 0)
    /** SELECT chọn lựa chọn thứ [index] (0-based, khớp `ControlDef.args`). */
    fun select(id: String, index: Int): Boolean
    /** BUTTON bấm-1-phát (lọc-ngay·nhớ-ghế·gập-gương·sạc-ngay). */
    fun press(id: String): Boolean
}

/**
 * ĐỊNH TUYẾN MỘT HÀNH ĐỘNG tới **đúng cửa** của [CarControlPort] theo [ControlDef.kind] — MỘT bảng định tuyến duy
 * nhất cho cả dự án ([CarControlAdapter.act] uỷ quyền về đây).
 *
 * [arg] theo kind: TOGGLE 1/0 · STEP giá trị · COVER 1(mở)/0(đóng) · SELECT chỉ số · BUTTON bỏ qua (luôn bấm).
 * Mã lạ ⇒ `false` (không sập).
 *
 * ⚠ Vì sao phải có: chỗ gọi chỉ giữ **interface** (vd ô gói lệnh ở `:app` nhận `() -> CarControlPort`) nên không tới
 * được [CarControlAdapter.act]. Thiếu hàm này thì chỗ gọi sẽ tự chọn một cửa — [ĐO] 2026-09-11: ô gói lệnh từng bắn
 * MỌI bước qua [CarControlPort.toggle], tức bước trỏ nút STEP (nhiệt/quạt) bị nén thành 1/0, bước SELECT mất chỉ số,
 * bước BUTTON có `arg=0` thì **không bấm gì cả**. Bốn gói đang khai chỉ dùng TOGGLE/COVER với 1/0 nên chưa lộ ra
 * (`toggle` và `cover` ghi cùng một giá trị) — đúng loại lỗi ngủ đông tới gói lệnh thứ năm.
 */
fun CarControlPort.actByKind(id: String, arg: Int): Boolean = when (ControlRegistry.byId(id)?.kind) {
    ControlKind.TOGGLE -> toggle(id, arg > 0)
    ControlKind.STEP -> step(id, arg)
    // T7: đưa MỨC thô xuống (0/1 y như trước; 2=Nửa mới tới được writeArgs). Impl mặc định gập về cover(bool).
    ControlKind.COVER -> coverLevel(id, arg)
    ControlKind.SELECT -> select(id, arg)
    ControlKind.BUTTON -> press(id)
    null -> false
}

/**
 * Mặc định OFF-CAR / EMULATOR: không freeform, dữ liệu null ("—"), điều khiển no-op.
 * Nhờ [NoCar], toàn bộ vỏ launcher chạy + test được mà không cần xe.
 */
object NoCar : AppLauncher, CarDataPort, CarControlPort {
    override fun openInSlot(pkg: String, slot: SlotRect): Boolean = false
    override fun moveToSlot(pkg: String, slot: SlotRect): Boolean = false
    override fun closeSlot(pkg: String) {}
    override fun isFreeformAvailable(): Boolean = false
    override fun batteryPercent(): Int? = null
    override fun rangeKm(): Int? = null
    override fun tirePressuresBar(): List<Double>? = null
    override fun pm25Level(): Int? = null
    override fun speedKmh(): Int? = null
    override fun outsideTempC(): Int? = null
    override fun toggle(id: String, on: Boolean): Boolean = false
    override fun step(id: String, value: Int): Boolean = false
    override fun cover(id: String, open: Boolean): Boolean = false
    override fun select(id: String, index: Int): Boolean = false
    override fun press(id: String): Boolean = false
}
