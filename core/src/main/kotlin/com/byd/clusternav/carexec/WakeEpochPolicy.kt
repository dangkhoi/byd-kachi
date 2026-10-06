package com.byd.clusternav.carexec

/**
 * ═══ 2.91 · F3 — "MỘT LẦN THỨC" là gì, và chuỗi SẴN chạy lượt của lần thức nào (spec `kachi-291-small-fixes.html` §4.3) ═══
 *
 * Bệnh [SUY từ nguồn 2.90, ghi chú QA 2.89 Q3, chưa bắt được trên xe]: ngay sau khởi động, màn tắt rồi bật lại trong ~10 s ⇒
 * lượt kiểm điều kiện nền của VietMap (`AppPrereqs.onReady`, trong chuỗi SẴN `EarlyShellChannel.readyChain`) bị BỎ ở lần thức
 * thật. Hai mắt xích, cả hai đọc ở `:app` [ĐO nguồn]:
 *  1. `KachiReadyLog.wake` gộp mọi tín hiệu "màn bật" cách mốc hiện tại < 10 s vào CÙNG một lần thức — kể cả khi giữa hai tín
 *     hiệu màn đã TẮT. Lần thức thật sau một lượt tắt-bật nhanh nhận lại mốc cũ.
 *  2. `readyChain` ghi "đã chạy cho mốc này" TRƯỚC khi kiểm cổng (kênh lên + màn tương tác). Lượt xếp hàng cho tín hiệu cũ chạy
 *     muộn trên luồng `kachi-ready` (sau lượt sớm dò kênh, có thể vài giây) lúc màn đã tắt ⇒ cổng hỏng, `return` — nhưng mốc đã bị
 *     TIÊU. Lần thức thật (mắt xích 1: cùng mốc) gặp "đã chạy" ⇒ bỏ.
 *
 * Bản vá (thuần, test off-device): [isNewWake] — một lần TẮT màn chen giữa ⇒ tín hiệu kế là lần thức MỚI (vẫn gộp các tín hiệu
 * của CÙNG lần thức: HOME hiện + broadcast + kênh lên cách nhau < [SAME_WAKE_MS] mà màn không tắt); [shouldRun] — chỉ TIÊU mốc
 * khi cổng ĐÃ QUA (lượt hỏng cổng không ăn mất lượt của lần thức đó; lượt kế cùng mốc — vd kênh lên — chạy được).
 */
object WakeEpochPolicy {

    /** Hai tín hiệu màn bật cách nhau dưới chừng này, KHÔNG có lần tắt màn chen giữa, là CÙNG một lần thức. */
    const val SAME_WAKE_MS = 10_000L

    /**
     * Tín hiệu màn bật lúc [at] (đồng hồ `elapsedRealtime`) có mở một lần thức MỚI không. [cur] = mốc lần thức hiện tại (`< 0` =
     * chưa có), [offAt] = lần màn TẮT gần nhất (`< 0` = chưa thấy). Màn tắt ở/sau mốc hiện tại và không muộn hơn [at] ⇒ mới.
     */
    fun isNewWake(cur: Long, offAt: Long, at: Long): Boolean = when {
        cur < 0 -> true
        at < cur -> true
        at - cur >= SAME_WAKE_MS -> true
        offAt in cur..at -> true
        else -> false
    }

    /**
     * Chuỗi SẴN có chạy (và TIÊU) mốc [epoch] không: chưa chạy cho mốc này ([lastRun] ≠ [epoch]), kênh shell lên, màn tương tác
     * (`null` = không hỏi được ⇒ không chạy, như cũ). `false` ⇒ KHÔNG ghi mốc — lượt sau của cùng lần thức còn quyền chạy.
     */
    fun shouldRun(lastRun: Long, epoch: Long, channelUp: Boolean, interactive: Boolean?): Boolean =
        lastRun != epoch && channelUp && interactive == true
}
