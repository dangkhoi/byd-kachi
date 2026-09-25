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

    /**
     * ĐỌC LẠI giá trị THẬT của một nút [ControlKind.STEP] (nhiệt độ · gió · âm lượng · độ sáng).
     * `null` = **không đọc được** (off-car, chưa map đường đọc, feature không provision trên trim này).
     *
     * ## Vì sao cổng GHI lại mọc thêm một đường ĐỌC (spec `kachi-voice-feedback.html` R5)
     * Tới 1.65 câu trả lời của giọng nói dựng từ **con số vừa gửi đi**: nói *"đặt nhiệt độ 24"* thì Kachi đáp
     * *"✓ Đặt Nhiệt độ = 24"* ngay cả khi xe kẹp nó về 17 hay bỏ qua hẳn. Đó là một câu **lạc quan**, và trên một
     * cái xe thì lạc quan nghĩa là nói sai: người lái nghe "xong" rồi thôi không nhìn lại nữa.
     *
     * Mặc định `null` (không override) có chủ ý: mọi bản cài đặt cũ ([NoCar], bản giả trong test, máy ảo) giữ
     * nguyên hành vi *"nói con số đã gửi"* mà không phải sửa một dòng nào — và đó cũng là hành vi ĐÚNG cho
     * chúng, vì ở đó **không có** giá trị thật nào để đọc. Chỉ [CarControlAdapter] (có `HalBindingTable`) mới
     * trả được số thật.
     *
     * ⚠ **Đọc ngay sau khi ghi có thể trả giá trị CŨ** — xe chưa kịp áp và bắn lại lên bus. Đây là tính chất của
     * phần cứng, không phải của hàm này; tầng gọi (`VoiceDispatcher.runControl`) là chỗ xử lý, xem KDoc ở đó.
     *
     * H1 · T5: nay uỷ quyền về [readState] (đường đọc đi qua `ControlDef.readKey` — một **mã datum** — chứ không qua
     * `bindingKey` là khoá GHI). Giữ tên cũ để mọi chỗ gọi 1.66 và mọi bản giả trong bài kiểm không phải sửa.
     */
    fun readStep(id: String): Int? = null

    /**
     * Nút này có đường điều khiển **trên CHIẾC XE NÀY** không. `true` = có, hoặc **không biết** (mặc định).
     *
     * ## Vì sao mặc định là `true`, không phải `false`
     * Mặc định phải là *"cứ thử đi"*: [NoCar], mọi bản giả trong bài kiểm và máy ảo đều không có bảng feature-id
     * của xe, nên một mặc định `false` sẽ khiến Kachi đọc to *"chưa điều khiển được trên xe này"* cho **mọi**
     * nút ở khắp mọi nơi trừ đúng một chiếc xe — một câu sai, và sai theo kiểu làm người ta tin là xe hỏng.
     *
     * Chỉ [CarControlAdapter] trả `false`, và chỉ khi bảng thật **có mặt** mà id **vắng** trong đó (xem
     * `featureAbsentOnCar`). Chỗ gọi dùng nó để đổi một câu thất bại chung chung thành một câu **có ích**:
     * *"chờ cũng vô ích"* khác hẳn *"thử lại xem"*.
     */
    fun wiredOnThisCar(id: String): Boolean = true

    /**
     * Một cú GHI vừa trả `false` cho nút [id] — đó là **xe thật từ chối**, hay chỉ là *"máy này không có xe"*?
     *
     * ## Vì sao KHÔNG dùng [wiredOnThisCar] cho câu hỏi này (review Pass 2 · 2026-09-26)
     * [wiredOnThisCar] trả `true` cho CẢ HAI nghĩa *"xe có nút"* và *"không biết"* (xem KDoc của nó) — đúng cho việc
     * nó sinh ra (chọn CÂU NÓI), nhưng sai làm cổng hoàn nguyên: off-car / máy ảo cũng ra `true`, mà ở đó **mọi** cú
     * ghi đều trả `false` ⇒ mọi ô bấm xong đều nảy về, đúng cái spec `kachi-closeout-hardening` OQ3 muốn tránh.
     *
     * `true` ⇔ **hai** vế cùng đúng: (a) máy này có bảng HAL THẬT của xe ([HalGateway.featureMapAvailable] — off-car /
     * máy ảo / mọi bản giả đều `false`), **và** (b) nút không bị bảng ấy khai là *vắng* (`featureAbsentOnCar`). Mặc
     * định `false` = *"không biết ⇒ đừng kết luận gì"*: [NoCar], bản giả trong bài kiểm, máy ảo giữ hành vi lạc quan.
     *
     * Chỗ gọi: [ControlTileWrite.submit] (`failureIsReal`). Ai muốn "fail loud" cả off-car thì bỏ cổng ở chỗ gọi, ĐỪNG
     * đổi mặc định ở đây (mặc định là lời khai "không biết", không phải một lựa chọn hành vi).
     */
    fun writeFailureIsReal(id: String): Boolean = false

    /**
     * ═══ H1 · T5 — GIÁ TRỊ THẬT của MỘT NÚT BẤT KỲ (không riêng [ControlKind.STEP]) ══════════════════════
     *
     * `null` = **chưa đọc được**: off-car · máy ảo · trim không provision · nút chưa có đường đọc. Chỗ gọi phải giữ
     * nguyên hành vi 1.68 khi gặp `null` (lùi về mức đang nhớ trong RAM) — **không bịa số**.
     *
     * ## [ĐO] bệnh nó chữa — tester 1.66 *"điều hoà chỉnh lung tung, quất một phát như lò heo quay"*
     * Lệnh **tương đối** (*"tăng gió"*) phải cộng vào **mức thật của xe**. Tới 1.68 nó cộng vào mặc định trong RAM
     * (gió 4 · nhiệt 22), nên [ĐO xe 2026-09-16] xe đang **gió 1** mà nói *"tăng gió"* thì lệnh bắn đi là **5** — đúng
     * cái *"quất một phát"* mà người dùng thấy. Đây là cổng để tầng trên hỏi xe trước khi tính.
     *
     * ## Ngân sách (⚠ [ĐO xe 1.68]: 33 lượt đọc HAL/phút · 27 shell/phút)
     * Mỗi lượt gọi là **một** lượt đọc HAL, **theo yêu cầu**: một câu lệnh giọng nói, hoặc một cú chạm − / +. KHÔNG
     * được gọi trong vòng vẽ, vòng poll, hay lúc dựng ô — làm thế là biến một phép đo thành một vòng lặp.
     *
     * Mặc định `null` (không override) có chủ ý, cùng lẽ với [readStep]: [NoCar], bản giả trong bài kiểm và máy ảo
     * giữ NGUYÊN hành vi cũ mà không phải sửa một dòng nào — ở đó **không có** giá trị thật nào để đọc.
     */
    fun readState(id: String): Int? = null
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
