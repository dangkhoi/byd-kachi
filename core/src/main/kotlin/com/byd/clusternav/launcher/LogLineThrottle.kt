package com.byd.clusternav.launcher

/**
 * ═══ TIẾT CHẾ DÒNG LOG **TRÙNG NỘI DUNG** TRƯỚC KHI GHI XUỐNG THẺ (LOG-41KB) ══════════════════════════════════
 *
 * ## Bệnh [ĐO buổi xe 2026-09-26]
 * Bộ đếm [KachiPerf] báo `log=41,2 KB/phút` lúc màn chính đứng yên (mục tiêu K5: **< 20 KB/phút**). Tra theo tag
 * trên chính tệp `kachi-logs/usage-1790420607259.log` (cửa sổ yên 18:11:30→18:16:30, 2 094 dòng / 34,3 KB/phút):
 *
 * | tag | byte/5 phút | dòng/phút | ai ghi |
 * |---|---|---|---|
 * | `BYDAutoAcDevice` · `BYDAutoSettingDevice` · `BYDAutoBodyworkDevice` · `…InstrumentDevice` · `…PM2p5Device` · `…StatisticDevice` | 158 874 | **378** | **thư viện HAL của BYD** trong tiến trình ta |
 * | `SimpleCast` | 8 850 | 30 | ta (`shell: am stack list` mỗi 4 s) |
 * | `ViewRootImpl` · `VmOverlayPos` · `NavRebind` · `KachiPerf` | 8 895 | 13 | framework + ta |
 *
 * ⇒ **≈ 90 % byte là `Log.d` của thư viện BYD**, mỗi lượt getter một dòng, do nhịp đọc ô điều khiển **1 Hz** mà
 * owner *yêu cầu* (2026-09-21 #5 *"không realtime"*, xem `CarDataAdapter.readFast`). Không được cắt nhịp đó, và
 * cũng không gọi được `Log` của thư viện khác để nó im.
 *
 * ## Vì sao tiết chế theo NỘI DUNG chứ không theo mức hay theo tag
 * Thứ có giá trị chẩn đoán trong những dòng ấy là **giá trị ĐỔI** (`getSeatVentilatingState … is 3` → `… is 1`
 * chính là cách suy ra thang OFF=1/1=2/2=3, buổi xe 17/09). Đọc 1 Hz thì cùng một câu chữ lặp lại y nguyên hàng
 * giờ, còn mỗi lần đổi giá trị là **một chuỗi khác** ⇒ lọc theo mức (`*:I`) thì mất sạch dữ liệu HAL, còn lọc
 * theo "đã thấy câu này trong [windowMs] chưa" thì:
 *  • mọi lần đổi giá trị đi qua **ngay** (chuỗi mới ⇒ chưa từng thấy),
 *  • mọi câu chữ khác nhau vẫn xuất hiện lại **mỗi [windowMs]** (còn mốc thời gian, còn biết nó vẫn đang chạy),
 *  • dòng lặp bị bỏ **không im lặng**: lần ghi kế mang hậu tố số lần đã bỏ (xem [suppressedBefore]).
 *
 * [ĐO mô phỏng trên chính tệp log thật, `windowMs = 10 s`, W/E/F không bao giờ bị bỏ]:
 * cửa sổ yên 34,3 → **8,6 KB/phút** (2 094 → 494 dòng); phút cao điểm (có camera) 127,6 → **48,1 KB/phút**.
 *
 * ## Vì sao KHÔNG dùng lại [ResendGate] / `ShellRunFailureLog`
 * `ResendGate` nhớ **một** payload cuối (đúng cho một thông điệp định kỳ, sai ở đây vì log xen kẽ hàng chục câu
 * khác nhau trong cùng một giây). `ShellRunFailureLog` đúng hình hơn nhưng khoá theo **lớp lỗi** — một tập hữu
 * hạn, nên nó dùng `HashMap` không chặn kích thước và không cần đếm số lần bỏ. Ở đây không gian khoá là **mọi
 * dòng log** (mỗi giá trị mới là một khoá mới) ⇒ phải có trần LRU ([maxKeys]) và phải có số đếm, không thì một
 * dòng bị bỏ là một dữ kiện mất không dấu vết.
 *
 * THUẦN (không Android, đồng hồ truyền vào) ⇒ `LogLineThrottleTest` khoá off-device. Chỗ gọi: `KachiLog.startCapture`.
 */
class LogLineThrottle(
    private val windowMs: Long = WINDOW_MS,
    private val maxKeys: Int = MAX_KEYS,
) {

    private class Slot(var writtenAtMs: Long, var suppressed: Int)

    /**
     * Khoá → lần ghi gần nhất. `LinkedHashMap` **accessOrder = true** ⇒ chính nó là LRU: [maxKeys] dòng khác nhau
     * gần đây nhất được nhớ, dòng cũ nhất bị đẩy ra (mất số đếm của nó, KHÔNG bao giờ mất một dòng cần ghi — khoá
     * vắng nghĩa là "chưa từng thấy" = ghi thẳng).
     */
    private val seen = object : LinkedHashMap<String, Slot>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Slot>?): Boolean = size > maxKeys
    }

    /**
     * `null` = **bỏ** dòng này (đã ghi một dòng y hệt trong [windowMs]). Số ≥ 0 = **ghi**, và đó là số dòng y hệt
     * đã bị bỏ kể từ lần ghi trước (0 ⇒ ghi thẳng, không cần hậu tố).
     *
     * [key] là phần dòng log **sau dấu thời gian** (chỗ gọi cắt) — cùng một câu ở hai giây khác nhau phải ra cùng
     * một khoá, nếu không thì không có gì trùng cả.
     *
     * ⚠ Đồng hồ **lùi** (đầu xe chỉnh giờ giữa phiên — đã từng làm hỏng một cửa sổ đo, xem [KachiPerf.dueLine]):
     * `nowMs - writtenAtMs < 0` ⇒ coi như đã quá cửa sổ ⇒ **ghi** và đặt lại mốc. Không bao giờ để một cú nhảy giờ
     * khoá một dòng log vĩnh viễn.
     */
    @Synchronized
    fun suppressedBefore(key: String, nowMs: Long): Int? {
        val slot = seen[key]
        if (slot == null) {
            seen[key] = Slot(nowMs, 0)
            return 0
        }
        val elapsed = nowMs - slot.writtenAtMs
        if (elapsed in 0 until windowMs) {
            slot.suppressed++
            return null
        }
        val skipped = slot.suppressed
        slot.writtenAtMs = nowMs
        slot.suppressed = 0
        return skipped
    }

    companion object {
        /**
         * Cửa sổ trùng nội dung. 10 s: nhịp ô điều khiển là 1 Hz và nhịp chậm là 10 s, nên mọi câu chữ phân biệt
         * được vẫn hiện lại **ít nhất mỗi 10 s** (còn mốc thời gian để đọc log), mà 9/10 dòng lặp thì biến mất.
         * `0` (hoặc số âm) = tắt hẳn tiết chế — mọi dòng đi qua.
         */
        const val WINDOW_MS = 10_000L

        /**
         * Trần số dòng-khác-nhau được nhớ. 512: [ĐO] cửa sổ yên 5 phút chỉ có ~40 câu chữ phân biệt, phút cao
         * điểm (camera, mỗi dòng mang số khung) lên vài trăm — trần này đủ rộng để không đẩy mất khoá đang dùng,
         * mà vẫn là một bộ nhớ có biên (mỗi slot ≈ một chuỗi dòng log + 16 byte).
         */
        const val MAX_KEYS = 512

        /** Hậu tố cho dòng được ghi lại sau khi đã bỏ [n] dòng y hệt — để không có dữ kiện nào mất không dấu vết. */
        fun repeatSuffix(n: Int): String = if (n <= 0) "" else "   [+$n lặp]"
    }
}
