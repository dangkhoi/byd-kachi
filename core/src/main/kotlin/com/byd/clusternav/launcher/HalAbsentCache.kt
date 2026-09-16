package com.byd.clusternav.launcher

import java.util.concurrent.ConcurrentHashMap

/**
 * ═══ H1 · NGƯNG HỎI LẠI THỨ XE NÀY KHÔNG CÓ ══════════════════════════════════════════════════════════════════
 *
 * [ĐO xe 2026-09-16]: **10 804 dòng** `W/AbsBYDAutoDevice: You have no permission to use the feature…` trong 47
 * phút (≈229/phút) — cùng một nhúm feature-id bị framework từ chối, hỏi lại mỗi nhịp suốt chuyến đi. `BydHal` đã
 * có cache từ-chối cho đường **GHI** (`rejectedFeatures`, TASK 5 closeout 1.28) nhưng đường **ĐỌC** thì chưa có
 * gì: `callGetter`/`readFeature` trả `null` rồi thôi, không ai nhớ.
 *
 * ## Vì sao "thử lại có giãn cách" chứ không phải "cấm vĩnh viễn"
 * Một `null` KHÔNG chứng minh *"xe không có datum này"*. Nó cũng có thể là *"chưa có ngay lúc này"* — thời gian
 * sạc còn lại khi chưa cắm sạc, trạng thái ghế trước khi ECU tỉnh, HAL chưa lên lúc mới nổ máy. Cấm vĩnh viễn thì
 * đúng vào lúc người ta cắm sạc, ô ETA sẽ câm cho tới khi khởi động lại app — đổi một lỗi hiệu năng lấy một lỗi
 * chức năng, không phải một cuộc đổi chác tốt.
 *
 * Vì thế: [missesBeforeCold] lần `null` LIÊN TIẾP ⇒ nguội; nguội thì **vẫn thử lại**, giãn dần
 * [firstRetryMs] → ×2 → … → trần [maxRetryMs]. Đọc được một giá trị ⇒ quên sạch (đếm lại từ đầu). Một datum
 * thật sự vắng mặt rơi về ≈1 lượt/[maxRetryMs]; một datum chỉ vắng tạm thời quay lại trong vòng [firstRetryMs].
 *
 * THUẦN (nhận `nowMs` làm tham số) ⇒ test off-device bằng đồng hồ giả, không `Thread.sleep`.
 *
 * ⚠ Đặt ở tầng **poll** ([CarDataAdapter]), KHÔNG ở [HalBindingTable]: bảng ấy còn phục vụ các đường đọc TƯỜNG
 * MINH (cầu `sweep`/`read`, màn kiểm-tra-từng-nút, câu hỏi bằng giọng) — những đường mà người dùng vừa bấm và
 * đang chờ câu trả lời THẬT, không phải một câu trả lời từ bộ nhớ.
 */
class HalAbsentCache(
    private val missesBeforeCold: Int = DEFAULT_MISSES,
    private val firstRetryMs: Long = DEFAULT_FIRST_RETRY_MS,
    private val maxRetryMs: Long = DEFAULT_MAX_RETRY_MS,
) {

    /**
     * ⚠ [SOÁT P2-3 · 2026-09-16] `@Volatile` trên CẢ BA field, không chỉ khoá trong [record].
     *
     * Bản đầu ghi dưới `synchronized(e)` nhưng [shouldRead] đọc **ngoài** khoá, với field thường. Lúc viết, cả
     * hai chỉ chạy trên một luồng poll nên chưa lộ; từ bản vá P1-1 thì đường **câu hỏi bằng giọng** gọi
     * `refreshNow()` trên luồng của nó ⇒ có thật hai luồng. `nextTryAt` là `Long`: JMM cho phép đọc **rách** một
     * `long` không `volatile` (nửa trên của giá trị này, nửa dưới của giá trị kia) ⇒ một mốc giờ chưa bao giờ
     * tồn tại, và datum có thể nguội vĩnh viễn hoặc không bao giờ nguội. `@Volatile` xoá hẳn ca đó.
     */
    private class Entry {
        @Volatile var misses: Int = 0
        @Volatile var retryMs: Long = 0
        @Volatile var nextTryAt: Long = 0
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    /** Có nên đọc [id] ở nhịp này không. Datum chưa nguội ⇒ luôn `true`. */
    fun shouldRead(id: String, nowMs: Long): Boolean {
        val e = entries[id] ?: return true
        return e.nextTryAt == 0L || nowMs >= e.nextTryAt
    }

    /** Ghi nhận kết quả một lượt đọc [id]. [got] = có giá trị thật (không `null`). */
    fun record(id: String, got: Boolean, nowMs: Long) {
        if (got) { entries.remove(id); return }
        // `computeIfAbsent` chứ không `getOrPut`: bản Kotlin của `getOrPut` là **đọc rồi ghi**, hai luồng cùng
        // vào sẽ dựng hai [Entry] và một trong hai lượt đếm biến mất ⇒ datum nguội muộn hơn khai báo.
        val e = entries.computeIfAbsent(id) { Entry() }
        synchronized(e) {
            e.misses++
            if (e.misses < missesBeforeCold) return
            e.misses = 0
            e.retryMs = if (e.retryMs == 0L) firstRetryMs else (e.retryMs * 2).coerceAtMost(maxRetryMs)
            e.nextTryAt = nowMs + e.retryMs
        }
    }

    /** Số datum đang nguội — cho chẩn đoán/test, KHÔNG dùng để quyết định gì. */
    fun coldCount(nowMs: Long): Int = entries.count { (_, e) -> e.nextTryAt > nowMs }

    /** Quên sạch (đổi hồ sơ / người dùng bấm "đọc lại" / test). */
    fun clear() = entries.clear()

    companion object {
        /** Ba lần `null` liên tiếp — đủ để loại một lần shell/HAL chớp, chưa đủ để kết luận vội. */
        const val DEFAULT_MISSES = 3

        /** Lần nguội đầu chỉ 60 s: datum vắng TẠM THỜI (vừa cắm sạc) quay lại trong vòng một phút. */
        const val DEFAULT_FIRST_RETRY_MS = 60_000L

        /** Trần 10 phút: datum thật sự không có trên trim rơi về ≈6 lượt/giờ thay vì ≈360 lượt/giờ. */
        const val DEFAULT_MAX_RETRY_MS = 600_000L
    }
}
