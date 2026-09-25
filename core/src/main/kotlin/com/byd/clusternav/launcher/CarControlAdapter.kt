package com.byd.clusternav.launcher

/**
 * ADAPTER ĐIỀU KHIỂN XE (W1b) — nối [CarControlPort] (toggle/step cũ) + route theo [ControlKind] qua
 * [HalBindingTable.write]. **KHÔNG gate an toàn** (owner bỏ 2026-09-10): ghi THẲNG, KHÔNG đọc tốc độ/số/permit
 * để chặn. Off-car → [HalBindingTable.write] trả null ⇒ [ok] = false (no-op).
 *
 * Tham số chính (primary) theo kind: TOGGLE 1/0 · STEP giá trị · COVER 1(mở)/0(đóng) · SELECT index · BUTTON 1.
 * [HalBindingTable] tự đổi thành args cuối cho named-method nhiều-arg (ghế/kính…).
 */
class CarControlAdapter(private val table: HalBindingTable) : CarControlPort {

    // ── Port cũ (ControlDockView đang dùng) ────────────────────────────────────────────────────────────
    override fun toggle(id: String, on: Boolean): Boolean = ok(table.write(id, if (on) 1 else 0))
    override fun step(id: String, value: Int): Boolean = ok(table.write(id, value))

    // ── Kind mới (Stage 3 renderer dùng) ────────────────────────────────────────────────────────────────
    /** COVER: mở/đóng (kính/nóc/rèm/cốp). */
    override fun cover(id: String, open: Boolean): Boolean = coverLevel(id, if (open) 1 else 0)

    /** COVER theo mức (T7): mức đi THẲNG xuống `writeArgs` (kính 2→OPEN_HALF=4, rèm 2→50%). 0/1 = y như [cover]. */
    override fun coverLevel(id: String, level: Int): Boolean = ok(table.write(id, level))

    /** SELECT: chọn lựa chọn thứ [index] (0-based, khớp [ControlDef.args]). */
    override fun select(id: String, index: Int): Boolean = ok(table.write(id, index))

    /** BUTTON: bấm-1-phát (lọc-ngay / nhớ-ghế / gập-gương / sạc-ngay). */
    override fun press(id: String): Boolean = ok(table.write(id, 1))

    /**
     * R5 — đọc lại giá trị THẬT của một nút STEP. Nay chỉ là [readState] **có thêm cổng kiểu**: giữ đúng lời hứa cũ
     * *"R5 chỉ áp cho STEP"* mà `VoiceStepReadbackTest.nut khong phai STEP thi khong doc lai gi` đang khoá — một lượt
     * HAL thừa mỗi lần bấm một nút bật/tắt là thứ ngân sách 33 lượt đọc/phút không gánh được.
     */
    override fun readStep(id: String): Int? =
        if (ControlRegistry.byId(id)?.kind != ControlKind.STEP) null else readState(id)

    /**
     * H1 · T5 — giá trị THẬT của một nút, qua **đúng đường đọc** mà ô thông tin đang dùng
     * ([HalBindingTable.readState] → `ControlDef.readKey` → datum → `readInt`).
     *
     * Không có đường đọc thứ hai: `readInt` đã lọc sentinel (`readRaw` trả `null` khi `rawIsSentinel`) và đã biết các
     * ca đặc thù (`float=` của `BYDAutoEventValue`, `INVALID_VALUES`). Tự gọi `gateway.getter` ở đây là
     * dựng bản sao thứ hai của những luật ấy — và bản sao sẽ đọc ra `-999999999` rồi Kachi đọc to con số đó cho người
     * lái nghe.
     *
     * `runCatching`: đường HAL đi qua reflection, một trim thiếu lớp là `ClassNotFoundException` — mà đây chỉ là một
     * câu trả lời đẹp hơn, không đáng làm hỏng cả lệnh vừa gửi thành công.
     */
    override fun readState(id: String): Int? = runCatching { table.readState(id) }.getOrNull()

    /**
     * Xem KDoc [CarControlPort.wiredOnThisCar]. `runCatching`: đường HAL đi qua reflection, và đây chỉ là một
     * câu trả lời đẹp hơn — không đáng làm hỏng một lệnh vừa gửi.
     */
    override fun wiredOnThisCar(id: String): Boolean =
        runCatching { !table.featureAbsentOnCar(id) }.getOrDefault(true)

    /**
     * Xem KDoc [CarControlPort.writeFailureIsReal]: `true` chỉ khi bảng HAL thật CÓ MẶT **và** nút không bị khai vắng.
     * Không có bảng (off-car · máy ảo · trim không provision) ⇒ `false` = *"không biết"*. `runCatching` cùng lẽ với
     * [wiredOnThisCar] (reflection), nhưng rơi về `false`: một ngoại lệ ở đây cũng là *"không biết"*.
     */
    override fun writeFailureIsReal(id: String): Boolean =
        runCatching { table.featureMapPresent() && !table.featureAbsentOnCar(id) }.getOrDefault(false)

    /**
     * Định tuyến chung theo [ControlDef.kind] — cho UI gọi 1 điểm. [arg]: TOGGLE 1/0, STEP giá trị, COVER 1/0,
     * SELECT index, BUTTON bỏ qua. Id lạ → false.
     *
     * Uỷ quyền về [actByKind] để bảng định tuyến chỉ tồn tại **một chỗ**: trước đây bảng này viết ở đây, nhưng chỗ
     * gọi nào chỉ giữ [CarControlPort] thì không tới được ⇒ nó tự chọn cửa và đi sai (xem KDoc của [actByKind]).
     */
    fun act(id: String, arg: Int): Boolean = actByKind(id, arg)

    /** rc hợp lệ (khác null + khác sentinel không-provisioned/không-hợp-lệ). Off-car null → false. */
    private fun ok(rc: Long?): Boolean = rc != null && !HalBindingTable.isSentinelRc(rc)
}
