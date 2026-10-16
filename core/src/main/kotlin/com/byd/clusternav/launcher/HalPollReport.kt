package com.byd.clusternav.launcher

/**
 * ═══ 3.1.9 · HAL-POLL-COVERED — một dòng *"còn poll gì, vì sao"* (thuần) ═══════════════════════════════════════════════════════
 *
 * [CarDataAdapter] gọi mỗi nhịp CHẬM; dòng chỉ được log khi nó ĐỔI (đổi chế độ sự kiện, device vừa chứng minh, màn đổi nhu cầu) — không
 * theo hạn đối soát, vì [HalEventCache.trust] không xét hạn. Để chuyến xe sau đọc thẳng log thay vì suy từ đếm getter (§B' doc
 * `oncar-perf-2026-10-15-evening.md`).
 *
 * Nhóm lý do (datum của màn ∪ datum mà ô điều khiển đang hiện mượn):
 *  • `nhanh` — [CarDataDemand.FAST_IDS]: cố ý giữ poll (tín hiệu ghép / cổng an toàn đọc tươi — [HalEventMap.POLL_ONLY]);
 *  • `không-sự-kiện` — getter không có trong [HalEventMap.GETTERS] / tên hằng không có trên xe ⇒ không vào kế hoạch;
 *  • `chưa-chứng-minh` — đã đăng ký nhưng device chưa đẩy sự kiện nào tới helper (F6-TRUST);
 *  • `helper-vắng` — chế độ POLL (helper chưa chào / đứt dây).
 * Datum `PROVEN` chỉ được đếm (`sự-kiện=N`): chúng đi poll đúng một lượt mỗi hạn đối soát.
 */
object HalPollReport {

    fun line(demand: Set<String>?, controls: Set<String>, trust: (String) -> HalEventCache.Trust): String {
        if (demand == null) return "poll: đọc hết (màn chưa công bố nhu cầu)"
        val ids = sortedSetOf<String>().apply { addAll(demand); controls.forEach { c -> readPathOf(c)?.id?.let(::add) } }
        val groups = linkedMapOf("nhanh" to mutableListOf<String>(), "không-sự-kiện" to mutableListOf(),
            "chưa-chứng-minh" to mutableListOf(), "helper-vắng" to mutableListOf())
        var proven = 0
        for (id in ids) {
            val g = if (id in CarDataDemand.FAST_IDS) "nhanh" else when (trust(id)) {
                HalEventCache.Trust.PROVEN -> { proven++; null }
                HalEventCache.Trust.UNPROVEN -> "chưa-chứng-minh"
                HalEventCache.Trust.NONE -> "không-sự-kiện"
                HalEventCache.Trust.OFF -> "helper-vắng"
            }
            g?.let { groups.getValue(it) += id }
        }
        val parts = groups.filterValues { it.isNotEmpty() }.map { (k, v) -> "$k=${v.joinToString(",")}" }
        return "poll còn lại: ${parts.joinToString(" · ").ifEmpty { "—" }} · sự-kiện=$proven"
    }
}
