package com.byd.clusternav

import com.byd.clusternav.launcher.CarControlPort
import com.byd.clusternav.launcher.CarDataAdapter
import com.byd.clusternav.launcher.ControlRegistry

/**
 * Bọc [CarControlPort]: SAU mỗi lệnh GHI (toggle/step/cover/select/press), ĐÁNH THỨC đường đọc của chính datum
 * điều khiển đó — quên trạng thái "nguội" trong [HalAbsentCache] để nhịp poll KẾ đọc lại NGAY.
 *
 * ## Vì sao cần (owner 2026-09-24 "khi có action phải chuyển NGAY")
 * Datum khí hậu (nhiệt/gió/gió-trong) có thật nhưng lúc boot AC HAL chưa sẵn ⇒ 3 lần đọc null liên tiếp ⇒
 * `HalAbsentCache` xử "nguội" ⇒ chỉ thử lại mỗi 60s→10min ⇒ ô/chip/widget giữ default (22 / gió 4) rất lâu dù
 * xe đã 26° / gió 1. Khi người dùng vừa tác động thì đó là dấu hiệu CHẮC CHẮN datum ấy đang sống ⇒ quên nguội,
 * poll kế đọc tươi. Grace-window của tile ([ControlTileState.touch]) chặn đọc-rách giữa lúc HAL chưa settle.
 *
 * Quên theo `readKey` (mã DATUM), không theo control id — vì [CarDataAdapter] đọc bằng datum id (readState của
 * control cũng resolve qua readKey). Control không có readKey ⇒ no-op.
 */
internal class WakeOnWriteControl(
    private val inner: CarControlPort,
    private val data: CarDataAdapter,
) : CarControlPort {

    private fun wake(id: String) {
        val key = ControlRegistry.byId(id)?.readKey ?: return
        if (key.isNotBlank()) data.forgetAbsent(key)
    }

    override fun toggle(id: String, on: Boolean): Boolean = inner.toggle(id, on).also { wake(id) }
    override fun step(id: String, value: Int): Boolean = inner.step(id, value).also { wake(id) }
    override fun cover(id: String, open: Boolean): Boolean = inner.cover(id, open).also { wake(id) }
    override fun coverLevel(id: String, level: Int): Boolean = inner.coverLevel(id, level).also { wake(id) }
    override fun select(id: String, index: Int): Boolean = inner.select(id, index).also { wake(id) }
    override fun press(id: String): Boolean = inner.press(id).also { wake(id) }
    override fun readState(id: String): Int? = inner.readState(id)
}
