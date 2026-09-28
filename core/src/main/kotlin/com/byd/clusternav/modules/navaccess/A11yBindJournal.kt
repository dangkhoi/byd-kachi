package com.byd.clusternav.modules.navaccess

/**
 * NHẬT KÝ BỀN của trạng thái gắn dịch vụ Hỗ trợ (R7 · spec `kachi-a11y-bind-stuck-autofix.html`).
 *
 * VÌ SAO CẦN — [ĐO owner, lặp lại nhiều lần] phím chết sau khi **để xe qua đêm standby**, không phải sau khi
 * cài bản mới. Nhưng vòng đệm `logcat` trên xe chỉ giữ được vài chục phút ([ĐO 2026-09-28]: vòng đệm sự kiện
 * còn đúng 32 phút), nên đến sáng thì khoảnh khắc đứt đã trôi mất — không thể biết nó đứt lúc nào và đứt cùng
 * với cái gì. Ghi vào tệp của chính app thì sống qua cả standby lẫn khởi động lại, và người dùng **không phải
 * gõ adb** (CLAUDE.md §11: app tự chụp lấy dữ liệu của nó, anh em chỉ gửi ảnh màn hình).
 *
 * Thuần, không chạm hệ thống, không lấy giờ — mọi mốc do caller đưa vào để test off-device.
 *
 * RIÊNG TƯ: dòng ghi CHỈ có mốc giờ, hai đồng hồ, pid và trạng thái. KHÔNG tên app, KHÔNG vị trí, KHÔNG nội
 * dung màn hình — nhật ký này có thể bị gửi ra ngoài dưới dạng ảnh chụp nên phải vô hại theo thiết kế.
 */
object A11yBindJournal {

    /** Ba trạng thái phân biệt được, tương ứng ba nhánh chữa. */
    enum class State {
        /** Đã gắn thật (hỏi `AccessibilityManager`). */
        BOUND,

        /** Chưa gắn, và KHÔNG kẹt — ca thường, đường toggle chữa được. */
        NOT_BOUND,

        /** Chưa gắn, ĐANG kẹt trong `Binding services` — đường toggle vô hiệu, phải leo force-stop. */
        STUCK,
    }

    /** Giữ tệp bé: quá số này thì bỏ bớt dòng CŨ NHẤT. 200 dòng ≈ vài tháng vì chỉ ghi khi ĐỔI. */
    const val MAX_LINES = 200

    /**
     * Tổng thời gian máy đã NGỦ SÂU tính từ lúc bật máy, bằng hiệu hai đồng hồ của Android:
     * `elapsedRealtime()` đếm cả lúc ngủ, `uptimeMillis()` thì không.
     *
     * Đây là chìa khoá phân biệt **"mười tiếng đứng qua đêm"** với **"mười tiếng chạy đường dài"** — đúng ranh
     * giới owner vạch ra 2026-09-28 (*"nếu xe đang chạy OK, thì tôi lại không nghĩ nó gây hang như thế này"*).
     * Không cần shell, không cần đoán theo thời gian sống của tiến trình.
     *
     * [SUY] hành vi hai đồng hồ là chuẩn Android; CHƯA đo trên ROM DiLink — đo bằng chính nhật ký này.
     */
    fun deepSleepMs(elapsedMs: Long, uptimeMs: Long): Long = (elapsedMs - uptimeMs).coerceAtLeast(0L)

    /**
     * Xe vừa ra khỏi một đợt ngủ DÀI chưa: lượng ngủ tích luỹ tăng thêm ít nhất [thresholdMs] kể từ lần đo
     * trước. [prevDeepSleepMs] `< 0` = chưa có mốc trước ⇒ `false` (không kết luận ở lần đo đầu tiên).
     *
     * Dùng làm ngòi nổ cho lượt tự chữa: đó là lúc khởi động lại giao diện gần như miễn phí, vì trên màn chính
     * mới chỉ có nhà, chưa app nào trong ô, chưa nhạc.
     */
    fun wokeFromLongSleep(prevDeepSleepMs: Long, nowDeepSleepMs: Long, thresholdMs: Long): Boolean =
        prevDeepSleepMs >= 0L && nowDeepSleepMs - prevDeepSleepMs >= thresholdMs

    /**
     * Có ghi thêm dòng mới không. Ghi khi **ĐỔI trạng thái** (đó mới là thông tin), hoặc khi đã quá
     * [heartbeatMs] kể từ dòng trước (nhịp tim, để biết máy vẫn đang theo dõi chứ không phải nhật ký chết).
     * [prevState] `null` = tệp rỗng ⇒ luôn ghi dòng đầu.
     */
    fun shouldAppend(prevState: State?, now: State, sinceLastMs: Long, heartbeatMs: Long): Boolean =
        prevState == null || prevState != now || sinceLastMs >= heartbeatMs

    /**
     * Một dòng nhật ký, cố ý dễ grep và đọc được bằng mắt trên ảnh chụp màn hình.
     *
     * `2026-09-28T10:20:00 state=STUCK up=3600s sleep=32400s pid=12738 note=watchdog`
     *
     * [note] bị cắt còn tối đa 40 ký tự và lọc bỏ khoảng trắng lạ để một dòng luôn là một dòng.
     */
    fun line(wallIso: String, elapsedMs: Long, uptimeMs: Long, state: State, pid: Int, note: String): String {
        val safe = note.replace(Regex("[\\r\\n\\t ]+"), "-").take(40).ifBlank { "-" }
        return "$wallIso state=$state up=${uptimeMs / 1000}s sleep=${deepSleepMs(elapsedMs, uptimeMs) / 1000}s " +
            "pid=$pid note=$safe"
    }

    /** Đọc trạng thái từ một dòng đã ghi; `null` nếu dòng lạ (nhật ký cũ / tệp hỏng) — không làm vỡ luồng. */
    fun stateOf(line: String?): State? {
        val token = line?.substringAfter("state=", "")?.substringBefore(' ')?.trim().orEmpty()
        return State.entries.firstOrNull { it.name == token }
    }

    /** Giữ [max] dòng CUỐI. Nhật ký là để đọc đoạn gần đây; đoạn xa đã hết giá trị chẩn đoán. */
    fun trim(lines: List<String>, max: Int = MAX_LINES): List<String> =
        if (lines.size <= max) lines else lines.subList(lines.size - max, lines.size)
}
