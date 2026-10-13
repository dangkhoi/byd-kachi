package com.byd.clusternav.launcher

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ═══ 3.01 R20 — CHỖ CẮM số liệu GIẢ cho máy ảo (CHỈ bản `vehicleTest` cắm được) ═══════════════════════════════════════
 *
 * Off-car mọi ô hiện `"—"` (R3/OQ1: KHÔNG demo), nên lỗi canh lề/co giãn chỉ lộ ra khi có SỐ THẬT dài (`123.4 km`,
 * `45678 km`) — owner 13/10: *"thử dummy số lên đi để fix"*. Lớp này là chỗ cắm DUY NHẤT: [override] mặc định `null` ⇒
 * [apply] trả nguyên trạng thái xe đọc được. Chỉ mã trong `app/src/vehicleTest/` (receiver `DummyCarReceiver`) được gán
 * nó — `DummyCarTapContractTest` khoá điều đó; bản release/debug không có mã nào gán ⇒ không thể hiện số giả.
 */
object CarStatusTap {

    /** Hàm biến đổi trạng thái xe; `null` = không cắm (mọi bản phát hành). */
    @Volatile
    var override: ((CarStatus) -> CarStatus)? = null
        private set

    private val bumps = MutableStateFlow(0)

    /** Đổi mỗi lần cắm/gỡ ⇒ chỗ thu trạng thái xe đẩy lại một lượt (trạng thái thật không đổi thì `StateFlow` không phát). */
    val bump: StateFlow<Int> get() = bumps

    fun apply(s: CarStatus): CarStatus = override?.invoke(s) ?: s

    fun set(f: ((CarStatus) -> CarStatus)?) {
        override = f
        bumps.value = bumps.value + 1
    }
}
