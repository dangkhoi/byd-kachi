package com.byd.clusternav.system

/**
 * Ưu tiên của một [WindowMutation] trong hàng đợi cửa sổ HỢP NHẤT mà B2b sẽ dựng lên trên `ShellTransport`.
 *
 * [STOP] và [RESCUE] là ưu tiên CAO — phải được RÚT (drain) TRƯỚC mọi mutation [NORMAL], kể cả khi hàng đợi
 * đang có [NORMAL] xếp trước. Đây là để GIỮ đúng semantics an toàn của `BoundedCastExecutor` cũ
 * (`submitStop` cắt ngang active + purge pending): một hàng đợi FIFO thuần sẽ để một STOP kẹt sau một cast
 * đang treo → regress an toàn (xem handoff B1). B2b hạ về một priority-queue, KHÔNG hạ về FIFO.
 *
 * [drainRank] nhỏ hơn = rút sớm hơn. Sắp xếp TĂNG DẦN theo [drainRank] cho đúng thứ tự rút.
 *
 * Thuần JVM (:core) — không android.*, không dadb.
 */
enum class MutationPriority(val drainRank: Int) {
    /** Dừng khẩn: huỷ cast đang chạy / đóng stream in-flight. Rút ĐẦU TIÊN. */
    STOP(0),

    /** Cứu hộ: deep-rescue cụm (dọn cụm, force-stop bên tranh chấp). Rút SAU [STOP], TRƯỚC [NORMAL]. */
    RESCUE(1),

    /** Thao tác thường: mở app, resize ô, reflow workspace, cast thường. Rút CUỐI. */
    NORMAL(2),
    ;

    /** true nếu là ưu tiên cao ([STOP] hoặc [RESCUE]) — rút trước mọi [NORMAL]. */
    val isHighPriority: Boolean get() = this != NORMAL

    companion object {
        /**
         * So sánh theo THỨ TỰ RÚT (drain order): [drainRank] tăng dần ⇒ [STOP] < [RESCUE] < [NORMAL].
         * Dùng cho hàng đợi ưu tiên hợp nhất ở B2b (sort ổn định giữ nguyên thứ tự nạp trong cùng mức).
         */
        val DRAIN_ORDER: Comparator<MutationPriority> = compareBy { it.drainRank }
    }
}
