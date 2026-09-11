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
    override fun cover(id: String, open: Boolean): Boolean = ok(table.write(id, if (open) 1 else 0))

    /** SELECT: chọn lựa chọn thứ [index] (0-based, khớp [ControlDef.args]). */
    override fun select(id: String, index: Int): Boolean = ok(table.write(id, index))

    /** BUTTON: bấm-1-phát (lọc-ngay / nhớ-ghế / gập-gương / sạc-ngay). */
    override fun press(id: String): Boolean = ok(table.write(id, 1))

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
