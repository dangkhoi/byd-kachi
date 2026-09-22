package com.byd.clusternav.launcher

/**
 * ═══ MỨC (chấm dưới icon) + ICON MỘT-NGUỒN cho một khả năng ══════════════════════════════════════════════════
 *
 * Owner 2026-09-22: *"cái nào có mức mà chỉ 1-2 thì làm chấm dưới icon (icon bé lại đẩy lên chừa chỗ); ghế mát/
 * sưởi off/mức1/mức2, kính mở 50%=1 mức, mở all=2 mức. Đẹp hơn số."* + *"icon header và widget phải giống nhau"*.
 *
 * Thuần (`:core`, cấm `android.*`) ⇒ test off-car. Tầng vẽ (`DatumIconView`) đọc [maxLevel] để vẽ hàng chấm.
 */
object CapabilityDots {

    /**
     * Số MỨC tối đa (số chấm) của một khả năng — 0 nghĩa là "không có mức, không vẽ chấm".
     *
     *  • SELECT (ghế mát/sưởi: `args`=[Tắt,Mức 1,Mức 2]) ⇒ `args.size - 1` (bỏ mức "Tắt") = 2.
     *  • COVER có nửa (kính `windows_all` / `win_half_*` / rèm) ⇒ 2 (đóng/nửa/mở) hoặc 1 (nút 50% chỉ đóng↔nửa).
     *  • còn lại ⇒ 0 (bật/tắt hoặc số — không dùng chấm).
     */
    fun maxLevel(id: String): Int {
        ControlRegistry.byId(id)?.let { def ->
            return when (def.kind) {
                ControlKind.SELECT -> (def.args.size - 1).coerceAtLeast(0)
                // Nút 50% (win_half_*) = đóng↔nửa ⇒ 1 mức. Nút "mở tất cả kính"/rèm/nóc = 2 mức (đóng/nửa/mở).
                ControlKind.COVER -> if (id.startsWith("win_half")) 1 else 2
                else -> 0
            }
        }
        // Datum TRẠNG THÁI của một control (vd `seat_vent_state`) mang mức của chính control đó.
        controlOfState(id)?.let { return maxLevel(it) }
        return 0
    }

    /**
     * MỘT NGUỒN icon cho một khả năng (R1) — tên `ic-*`, dùng CHUNG cả header/widget/picker.
     *
     * Datum TRẠNG THÁI (`seat_vent_state`) trỏ về icon của CONTROL tương ứng (`seatc` → `ic-seat-left`) để chip
     * và ô không còn hai hình khác nhau cho cùng khái niệm. Còn lại trả `null` ⇒ chỗ gọi dùng icon sẵn của pick.
     */
    fun iconOverride(id: String): String? =
        controlOfState(id)?.let { ControlRegistry.byId(it)?.icon }

    /**
     * Control nào có `readKey` = [stateId] (đảo bảng `ControlDef.readKey`). Cache một lần.
     *
     * Vd `seatc.readKey = "seat_vent_state"` ⇒ `controlOfState("seat_vent_state") = "seatc"`.
     */
    private val stateToControl: Map<String, String> by lazy {
        ControlRegistry.ALL.mapNotNull { def -> def.readKey.takeIf { it.isNotEmpty() }?.let { it to def.id } }.toMap()
    }

    private fun controlOfState(stateId: String): String? = stateToControl[stateId]
}
