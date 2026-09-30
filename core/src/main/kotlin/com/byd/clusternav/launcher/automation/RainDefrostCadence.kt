package com.byd.clusternav.launcher.automation

/**
 * ═══ kachi-automation V8.1 · NHỊP của automation mưa → sấy (R-V8.8 · OQ-V8.6) ═══════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` §V8.1. Thuần: đồng hồ (`elapsedRealtime` ở `:app`) bơm từ ngoài ⇒ test
 * chạy THẬT mọi mốc thời gian off-car.
 *
 * ## Vì sao có lớp này — [ĐO git] `79be642` (2.29, 25/09)
 * Commit đó đổi nhịp mưa từ đếm nhịp sang **thời gian trôi** (đúng: lúc ấy vòng ngủ 250 ms khi bật camera nên đếm
 * nhịp không còn ra 5′) nhưng so với `lastRainMs = 0L` ⇒ điều kiện `nowMs - 0 >= 5′` chỉ đúng khi đầu xe đã chạy
 * **≥ 5 phút**. Ai tạt nước thử cảm biến ngay sau khi nổ máy sẽ chờ ~5′ và kết luận "không work". Chú thích
 * *"Nhịp ĐẦU chạy ngay"* trên vòng vẫn đứng nguyên — độ trễ là phụ phẩm, không phải quyết định.
 *
 * ## Bốn luật, mỗi luật một lý do
 *  1. **Vòng mới ⇒ nhịp kế là NGAY** ([loopStarted]) — không phụ thuộc uptime.
 *  2. **Sàn [retryMs] giữa hai nhịp** — kể cả khi có yêu cầu đổi lựa chọn, kể cả khi vòng hỏi dày (nhịp 250 ms của
 *     camera từng có, và một lượt thêm lại nó không được kéo nhịp mưa dày theo — đúng thứ `79be642` cần giữ). Yêu cầu
 *     chưa qua sàn thì được **GIỮ**, không mất.
 *  3. **Đọc lỗi ⇒ thử lại sau [retryMs], tối đa [maxRetries] lần liên tiếp** ([ĐO nguồn] `WakeOnWriteControl.kt:11`:
 *     lúc boot AC HAL chưa sẵn, đọc `null`). Có TRẦN vì trim không có cảm biến đọc `null` mãi — thử lại vô hạn là ×5
 *     lượt HAL suốt chuyến. Một nhịp đọc được đủ ⇒ trần về 0.
 *  4. **Ổn ⇒ nhịp kế sau [periodMs]** theo thời gian trôi (R1.2, không đổi).
 *
 * Chỉ ĐỌC lỗi mới thử lại (spec V8.1 · D13): ghi hỏng đã nhả chủ quyền (D5) nên nhịp sau tự bật lại; tính cả ghi hỏng
 * là dội lệnh vào một xe đang từ chối mỗi 60 s.
 *
 * Một bản cho cả tiến trình (ở `RainDefrostApplier`, D14): dòng tình trạng cần giờ nhịp kế, và sàn phải giữ cả khi
 * một vòng mới dựng lại ngay sau vòng cũ. Luồng nền gọi [shouldRun]/[ran], luồng vẽ gọi [nextDueMs] ⇒ khoá ngắn.
 */
class RainDefrostCadence(
    val periodMs: Long = PERIOD_MS,
    val retryMs: Long = RETRY_MS,
    val maxRetries: Int = MAX_RETRIES,
) {
    init {
        require(retryMs > 0 && periodMs >= retryMs && maxRetries >= 0) { "nhịp sai: $periodMs/$retryMs/$maxRetries" }
    }

    private val lock = Any()
    private var started = false
    private var lastRunMs: Long? = null
    private var nextAtMs: Long? = null
    private var retriesUsed = 0
    private var requested = false

    /** Vòng mới bắt đầu: nhịp kế = NGAY (luật 1), trần thử lại về 0. Mốc nhịp trước GIỮ để sàn 60 s còn hiệu lực. */
    fun loopStarted() {
        synchronized(lock) {
            started = true
            nextAtMs = null
            retriesUsed = 0
        }
    }

    /**
     * Lượt thức này có chạy nhịp mưa không.
     *
     * @param request yêu cầu nhịp sớm (R-V8.5 — người dùng vừa đổi lựa chọn). Được GIỮ tới lượt chạy kế, kể cả khi
     *   lượt này bị sàn chặn ⇒ chỗ gọi đọc-và-xoá cờ của mình mỗi lượt mà không làm mất yêu cầu.
     */
    fun shouldRun(nowMs: Long, request: Boolean): Boolean = synchronized(lock) {
        if (request) requested = true
        val last = lastRunMs
        if (last != null && nowMs - last < retryMs) return@synchronized false
        val next = nextAtMs
        requested || next == null || nowMs >= next
    }

    /** Nhịp vừa chạy lúc [nowMs]; [halReady] = mọi lượt ĐỌC của nhịp ra giá trị ([halReady] của companion). */
    fun ran(nowMs: Long, halReady: Boolean) {
        synchronized(lock) {
            lastRunMs = nowMs
            requested = false
            nextAtMs = if (!halReady && retriesUsed < maxRetries) {
                retriesUsed++
                nowMs + retryMs
            } else {
                if (halReady) retriesUsed = 0
                nowMs + periodMs
            }
        }
    }

    /**
     * Mốc (cùng đồng hồ với [shouldRun]) của nhịp mưa kế — cho dòng tình trạng. Vòng thật chạy ở lượt thức đầu tiên
     * từ mốc này (≤ một lượt thức sau), nên màn hình ghi *"~"*.
     *
     * @param request có yêu cầu nhịp sớm mà vòng CHƯA đọc tới (cờ `due` của chỗ gọi) — ngay sau một cú chạm Cài đặt,
     *   cờ còn nằm ở applier tới lượt thức kế; bỏ qua nó là hứa "+5′" cho một nhịp sẽ chạy trong ≤ 60 s.
     * @return `null` khi không biết: vòng chưa từng bắt đầu trong tiến trình này, hoặc mốc đã quá hạn hơn một
     *   [retryMs] (vòng không chạy — đừng hứa một giờ đã trôi qua).
     */
    fun nextDueMs(nowMs: Long, request: Boolean = false): Long? = synchronized(lock) {
        if (!started) return@synchronized null
        val planned = if (requested || request) nowMs else (nextAtMs ?: nowMs)
        val due = maxOf(planned, lastRunMs?.plus(retryMs) ?: planned)
        if (nowMs - due > retryMs) null else maxOf(due, nowMs)
    }

    companion object {
        /** Sàn giữa hai nhịp + khoảng thử lại — bằng một lượt thức của `AutomationService` (60 s). */
        const val RETRY_MS = 60_000L

        /** Nhịp thường ≈ 5′ (R1.2). */
        const val PERIOD_MS = 5 * 60_000L

        /** Số lần thử lại liên tiếp khi đọc lỗi (R-V8.8). */
        const val MAX_RETRIES = 5

        /**
         * Nhịp có đọc được đủ không: mưa đọc được VÀ mọi kính đã chọn đọc được. Không chọn kính nào (rỗng) = không có
         * gì để thử lại ⇒ `true`.
         */
        fun halReady(outcomes: List<RainGlassOutcome>): Boolean =
            outcomes.none { it.plan.rainSpeed == null || it.plan.read == null }

        /** Đổi một mốc `elapsedRealtime` sang đồng hồ tường (chỉ để HIỆN giờ; nhịp vẫn đi theo đồng hồ đơn điệu). */
        fun toWallMs(elapsedAt: Long, nowElapsed: Long, nowWall: Long): Long = nowWall + (elapsedAt - nowElapsed)
    }
}
