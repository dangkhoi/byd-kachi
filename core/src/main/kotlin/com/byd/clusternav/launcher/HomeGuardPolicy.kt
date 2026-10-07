package com.byd.clusternav.launcher

/**
 * ═══ 2.96 · R8 — GIỮ Kachi làm màn hình chính khi người dùng ĐÃ chọn (luật thuần, test off-car) ═══════════════════
 *
 * Spec `docs/specs/kachi-296-plan.html` §3 R8.
 *
 * ## Vì sao cần [ĐO xe 07/10, firmware 2606, 3/3 lần nổ máy]
 * Một launcher khác giành vai HOME ~15–17 s sau khi Kachi lên (`MEDIA_MOUNTED` đánh thức nó, ~2 s sau
 * `commit_sys_config_file roles`, `dumpsys role` ⇒ HOME holder đổi). Lượt đặt lại của `KachiAutostart` chạy ~6 s ⇒ THUA.
 * [ĐO thử xe 21:09] đặt lại bằng `HomeActivityCmd.set` khi launcher kia đang sống ⇒ HOME giữ ≥ 60 s, không bị giành lại.
 *
 * ## Luật (generic — KHÔNG có tên gói nào, CLAUDE.md §7)
 * Đầu vào là SỰ THẬT đọc mỗi nhịp (HOME đang phân giải về gói nào — `PackageManager`, không shell) + lựa chọn bền của
 * người dùng. Bộ đếm/mốc trong RAM CHỈ dùng để giới hạn tần suất (chống vòng giằng co), không quyết định "HOME là ai"
 * (CLAUDE.md §5). Người dùng KHÔNG chọn Kachi ⇒ không bao giờ đụng (tôn trọng lựa chọn launcher khác).
 *
 * Nhịp: dày [FAST_TICK_MS] trong [FAST_WINDOW_MS] đầu mỗi chuyến (cửa sổ launcher kia hay giành), rồi thưa
 * [SLOW_TICK_MS] suốt chuyến (cắm USB giữa chuyến cũng đánh thức nó).
 */
object HomeGuardPolicy {

    /** Cửa sổ "đầu chuyến" — bao trọn ca giành HOME đã đo (~17 s) với dư lớn (USB/thẻ nhớ mount trễ). */
    const val FAST_WINDOW_MS = 5 * 60_000L

    /** Nhịp đầu chuyến: chậm nhất ~5 s sau khi bị giành là đã đặt lại. Một lượt đọc = một binder `resolveActivity`. */
    const val FAST_TICK_MS = 5_000L

    /** Nhịp suốt chuyến sau cửa sổ đầu. */
    const val SLOW_TICK_MS = 30_000L

    /** Hai lượt đặt lại cách nhau ít nhất chừng này — giằng co (nếu có) không thành vòng lặp dày. */
    const val MIN_GAP_MS = 20_000L

    /** Trần số lượt đặt lại mỗi chuyến. Hết trần ⇒ chỉ ghi nhật ký (có cái gì đó giành liên tục — cần người xem log). */
    const val MAX_PER_TRIP = 5

    /**
     * Người dùng có muốn Kachi làm HOME không — MỘT chỗ cho đường khởi động nguội (`KachiAutostart`) và [decide]:
     * đã bấm "Đặt làm màn hình chính" ([homeChosen]) hoặc bật "giữ khi nổ máy" ([keepHomeOnBoot]). "Bỏ chọn" xoá cả hai.
     */
    fun wantsKachiHome(homeChosen: Boolean, keepHomeOnBoot: Boolean): Boolean = homeChosen || keepHomeOnBoot

    /** Sự thật một nhịp. Mốc là `elapsedRealtime` (giờ tường đầu xe nhảy — [ĐO] 14/09). */
    data class Facts(
        /** [wantsKachiHome]. */
        val chosen: Boolean,
        /** Gói Kachi. */
        val ownPackage: String,
        /** Gói HOME đang phân giải; `null` = KHÔNG đọc được (không kết luận là bị giành). */
        val currentHomePackage: String?,
        /** Màn có đang tương tác; `null` = không đọc được ⇒ coi như bật. */
        val interactive: Boolean?,
        /** Kênh shell đã lên (đặt HOME cần uid shell). */
        val channelUp: Boolean,
        val nowMs: Long,
        /** Mốc lượt đặt lại gần nhất trong chuyến này; `null` = chưa có. */
        val lastReassertMs: Long?,
        val reassertsThisTrip: Int,
    )

    enum class Reason { NOT_CHOSEN, UNREADABLE, ALREADY_HOME, SCREEN_OFF, CAP_REACHED, RATE_LIMITED, CHANNEL_DOWN, TAKEN }

    /** [reassert] chỉ `true` với [Reason.TAKEN]. [taker] = gói đang giữ HOME khi không phải Kachi (chỉ để ghi log). */
    data class Decision(val reassert: Boolean, val reason: Reason, val taker: String?)

    fun decide(f: Facts): Decision {
        if (!f.chosen) return Decision(false, Reason.NOT_CHOSEN, null)
        val cur = f.currentHomePackage ?: return Decision(false, Reason.UNREADABLE, null)
        if (cur == f.ownPackage) return Decision(false, Reason.ALREADY_HOME, null)
        val skip = when {
            f.interactive == false -> Reason.SCREEN_OFF
            f.reassertsThisTrip >= MAX_PER_TRIP -> Reason.CAP_REACHED
            f.lastReassertMs != null && f.nowMs - f.lastReassertMs < MIN_GAP_MS -> Reason.RATE_LIMITED
            !f.channelUp -> Reason.CHANNEL_DOWN
            else -> null
        }
        return if (skip != null) Decision(false, skip, cur) else Decision(true, Reason.TAKEN, cur)
    }

    /**
     * 2.96 · R18 — nhịp khi màn đọc được là TẮT: không đặt lại được (SCREEN_OFF) nên không đọc HOME; chỉ cần thấy màn BẬT
     * lại kịp để mở chuyến mới ([startsNewTrip]) ⇒ trễ tối đa chừng này so với nhịp dày 5 s. [ĐO máy ảo 07/10] trước:
     * nhịp 5 s chạy cả khi màn tắt (0,6 lần thức/giây của luồng `kachi-home-guard`).
     */
    const val SCREEN_OFF_TICK_MS = 10_000L

    /** Khoảng tới nhịp sau, theo thời gian kể từ đầu chuyến; màn đọc được là tắt ⇒ [SCREEN_OFF_TICK_MS]. */
    fun nextDelayMs(sinceTripStartMs: Long, interactive: Boolean? = null): Long = when {
        interactive == false -> SCREEN_OFF_TICK_MS
        sinceTripStartMs < FAST_WINDOW_MS -> FAST_TICK_MS
        else -> SLOW_TICK_MS
    }

    /**
     * Chuyến MỚI bắt đầu khi màn đi từ TẮT (đã đọc được là tắt) sang BẬT — tiến trình sống qua tắt/mở xe thì bộ đếm và
     * cửa sổ nhịp dày phải về đầu. `null` (không đọc được) không bao giờ mở chuyến mới.
     */
    fun startsNewTrip(prevInteractive: Boolean?, nowInteractive: Boolean?): Boolean =
        prevInteractive == false && nowInteractive == true

    /**
     * Có ghi nhật ký nhịp này không: mọi lượt đặt lại; còn lại chỉ khi lý do/gói giữ HOME ĐỔI so với nhịp trước (không
     * ghi một dòng mỗi 5 s khi mọi thứ vẫn thế).
     */
    fun shouldLog(prev: Decision?, now: Decision): Boolean =
        now.reassert || prev == null || prev.reason != now.reason || prev.taker != now.taker
}
