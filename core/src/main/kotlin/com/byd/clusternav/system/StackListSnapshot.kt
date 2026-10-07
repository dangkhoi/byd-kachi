package com.byd.clusternav.system

/**
 * ═══ 2.96 · R18 — BẢN ĐỌC `am stack list` DÙNG CHUNG trong tiến trình launcher ═══════════════════════════════════════
 *
 * [ĐO log xe 07/10, Seal 17:20–18:40 + SL6 10:45–12:20] chạy thường lúc đang chiếu: `KachiPerf shell=19/phút`, trong đó
 * 15/phút là `am stack list` của lượt dò repin (`SimpleCastCoordinator`, 4 s) và ~4/phút là `am stack list` của nhịp đo ô
 * (`SlotLiveProbe`, lùi tới 15 s). HAI bên đọc CÙNG một lệnh chỉ-đọc, xếp hàng trên CÙNG một kênh dadb nối tiếp.
 *
 * Bên ghi (lượt dò repin) [record] bản đọc; bên đọc thưa hơn (đo ô) hỏi [fresh] trước — có bản ĐỦ MỚI ⇒ dùng lại, 0 lệnh.
 * "Đủ mới" = trẻ hơn [REUSE_MAX_AGE_MS] VÀ chụp SAU lượt đo trước của chính bên đọc ([fresh] `notBeforeMs`) ⇒ mỗi nhịp
 * đo ô vẫn nhìn một bản đọc MỚI so với nhịp trước (không bao giờ đếm hai lần cùng một bản), chỉ là không tự chạy lệnh.
 *
 * Chỉ ghi bản đọc ĐỌC ĐƯỢC (có tiêu đề `Stack id=` — luôn có stack home); rỗng/lỗi ⇒ không ghi ⇒ bên đọc tự chạy lệnh
 * như cũ. Không dùng cho lượt GHI/ĐẶT nào (repin vẫn dò tươi trước khi đặt — bất biến R1 của nó không đổi).
 * Đồng hồ: [System.nanoTime] (đơn điệu, cùng một nguồn cho cả hai bên; giờ tường đầu xe nhảy — [ĐO] 14/09).
 */
object StackListSnapshot {

    /** Tuổi tối đa để dùng lại — bằng nhịp dò repin khi màn bật ([StandbyCadence.REPIN_ON_MS]). */
    const val REUSE_MAX_AGE_MS = StandbyCadence.REPIN_ON_MS

    private class Entry(val out: String, val atMs: Long)

    @Volatile private var last: Entry? = null

    fun nowMs(): Long = System.nanoTime() / 1_000_000L

    /** Ghi một bản đọc vừa chạy xong. Bản không đọc được (không có `Stack id=`) bị bỏ. */
    fun record(out: String?, atMs: Long = nowMs()) {
        if (out == null || !readable(out)) return
        last = Entry(out, atMs)
    }

    /**
     * Bản đọc dùng lại được, hoặc `null` ⇒ tự chạy lệnh. [notBeforeMs] = mốc lượt đo trước của bên hỏi (bản phải chụp
     * SAU mốc đó). Đồng hồ lùi (tuổi âm) ⇒ không dùng.
     */
    fun fresh(notBeforeMs: Long, nowMs: Long = nowMs(), maxAgeMs: Long = REUSE_MAX_AGE_MS): String? {
        val e = last ?: return null
        if (e.atMs <= notBeforeMs) return null
        val age = nowMs - e.atMs
        return if (age in 0..maxAgeMs) e.out else null
    }

    fun readable(out: String): Boolean = "Stack id=" in out

    /** Chỉ cho test. */
    fun clear() { last = null }
}
