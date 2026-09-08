package com.byd.clusternav.navigation

import java.util.Locale

/**
 * Parse/format khoảng cách + ETA từ chuỗi notification. TÁCH khỏi ClusterBroadcaster để builder
 * (AmapFrameBuilder) và parser dùng chung 1 nguồn. Output PHẢI giữ y hệt bản cũ — đây là các giá trị
 * đi thẳng lên cụm (SEG_REMAIN_DIS, ROUTE_REMAIN_*, *_AUTO). Không đổi regex/format.
 */
object NavParse {

    // Regex COMPILE 1 LẦN (hot-path: dùng lại mỗi heartbeat 400ms + mỗi noti). Pattern GIỮ Y NGUYÊN.
    private val RE_METERS = Regex("""(\d+([.,]\d+)?)\s*(km|m)""", RegexOption.IGNORE_CASE)
    private val RE_ETA_KM = Regex("""(\d+([.,]\d+)?)\s*km""", RegexOption.IGNORE_CASE)
    private val RE_ETA_MIN = Regex("""(\d+)\s*(phút|min|分)""", RegexOption.IGNORE_CASE)
    private val RE_ETA_HR = Regex("""(\d+)\s*(giờ|h|hour|时)""", RegexOption.IGNORE_CASE)
    private val RE_CLOCK = Regex("""\b(\d{1,2}):(\d{2})\b""")

    /**
     * Đồng hồ CÓ hậu tố AM/PM: `"5:50 PM"`, `"5:50pm"`, `"12:05 a.m."`. Chỉ dùng bởi [extractArrivalClock24].
     * Không có `\b` ở đuôi vì hậu tố có thể kết bằng dấu chấm (`"p.m."`) — sau dấu chấm không có ranh giới từ.
     */
    private val RE_CLOCK_AMPM = Regex("""\b(\d{1,2}):(\d{2})\s*([ap])\.?\s*m\.?""", RegexOption.IGNORE_CASE)

    /** Làm TRÒN cự ly hiển thị theo bước theo độ xa (chống nhảy từng-mét NHƯNG đủ MỊN để đếm ngược mượt).
     *  Bước nhỏ lại (so bản cũ 50/100m) → số trượt đều thay vì "đứng im rồi nhảy cục". */
    fun quantizeDisplay(m: Int): Int = when {
        m < 0 -> m
        // J2 (1.16): LÀM TRÒN (round) thay vì floor. Đo trên xe (1.15, n=3239): floor khiến display thấp hơn
        // Google ~34m trung bình (cụm hiện ÍT hơn Maps; histogram dồn ở -10/-25/-100 = đúng các bậc floor).
        // Google làm tròn về bậc gần nhất → round khử bias-xuống của floor. (Bias nội suy proj−raw≈−16m còn
        // lại chờ ground-truth screenRead lần lái sau để chỉnh FACTOR.)
        m >= 1000 -> ((m + 50) / 100) * 100   // >1km: bậc 100m (0.1km)
        m >= 300 -> ((m + 25) / 50) * 50       // 300m-1km: bậc 50m (owner 2026-08-15: 25m→50m cho số tròn; các dải khác giữ)
        m >= 100 -> ((m + 5) / 10) * 10        // 100-300m: bậc 10m
        else -> ((m + 5) / 10) * 10            // <100m: bậc 10m
    }

    /** "250 m" / "1.2 km" / "1,2 km" -> mét (int). -1 nếu không đọc được. */
    fun parseMeters(s: String): Int {
        val m = RE_METERS.find(s) ?: return -1
        val v = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return -1
        return if (m.groupValues[3].equals("km", true)) (v * 1000).toInt() else v.toInt()
    }

    /** ETA "10:32 · 5.2 km · 8 phút" -> (mét còn lại, giây còn lại). -1 nếu thiếu. */
    fun parseEta(s: String): Pair<Int, Int> {
        val dis = RE_ETA_KM.find(s)
            ?.let { (it.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0) * 1000 }?.toInt() ?: -1
        val min = RE_ETA_MIN.find(s)
            ?.groupValues?.get(1)?.toIntOrNull()
        val hr = RE_ETA_HR.find(s)
            ?.groupValues?.get(1)?.toIntOrNull()
        val sec = when {
            min != null || hr != null -> ((hr ?: 0) * 3600) + ((min ?: 0) * 60)
            else -> -1
        }
        return dis to sec
    }

    // Khớp DashCast formatMeters/formatSeconds — giữ y output.
    fun formatMeters(m: Int): String =
        if (m >= 1000) String.format(Locale.US, "%.1f km", m / 1000.0f) else "$m m"

    fun formatSeconds(total: Int): String {
        val mins = total / 60; val h = mins / 60; val mm = mins % 60
        return if (h > 0) "${h}h ${mm}m" else "$mm min"
    }

    // ── Thời gian/ETA cho cụm: firmware AmapService.parseTime() CHỈ parse được TIẾNG TRUNG.
    //    Remaining-time: token 天(ngày)/时(giờ)/分(phút).  Arrival: "预计 ... HH:MM 到达" (今天=hôm nay).
    //    -> phải format kiểu này thì INSTRUMENT_NAVI_TRIP_INFO_HOUR/MINUTE mới được ghi.

    /** Thời gian CÒN LẠI -> "1时20分" / "8分" / "2天3时5分" (parseTime đọc 天/时/分). "-1" nếu thiếu. */
    fun formatRemainTimeCn(total: Int): String {
        if (total < 0) return "-1"
        val mins = total / 60
        val d = mins / (60 * 24)
        val h = (mins / 60) % 24
        val m = mins % 60
        val sb = StringBuilder()
        if (d > 0) sb.append("${d}天")
        if (h > 0) sb.append("${h}时")
        sb.append("${m}分")          // luôn có 分 để parseTime có token hợp lệ
        return sb.toString()
    }

    /** Lấy giờ tới "HH:MM" từ chuỗi ETA notification ("10:32 · 5.2 km · 8 phút"). null nếu không có. */
    fun extractArrivalClock(s: String): String? =
        RE_CLOCK.find(s)?.let {
            val h = it.groupValues[1].toInt(); val m = it.groupValues[2].toInt()
            if (h in 0..23 && m in 0..59) String.format(Locale.US, "%d:%02d", h, m) else null
        }

    /**
     * Giờ tới nơi → `"H:MM"` **24 GIỜ**, hiểu cả hậu tố AM/PM. `null` = không đọc được (KHÔNG đoán).
     *
     * ── VÌ SAO PHẢI CÓ HÀM RIÊNG, KHÔNG SỬA [extractArrivalClock] (CLAUDE.md §6) ──────────────────────────
     * [extractArrivalClock] đang nuôi đường notification **GMaps** đang chạy ngoài hiện trường
     * (`NavRepository.kt` §buildContent, `AmapFrameBuilder.kt` §ETA_TEXT). Sửa nó là lặng lẽ đổi hành vi một
     * đường đã proven — đúng thứ §6 cấm. Đường mới xuống cuối: hàm này chỉ được gọi từ producer MỚI
     * (`NavAccessibilityService.probeNavByViewId`, đường view-id của Waze).
     *
     * ── LỖI NÓ ĐÓNG (ĐÃ CHỨNG MINH bằng probe 08-23, không suy luận) ──────────────────────────────────────
     * Waze phơi `lblArrivalTime='5:50 PM'` — chuỗi THẬT đã đo (xem KDoc [NavViewIdSource]). Trước sửa, producer
     * ghi **thô** chuỗi đó vào `NavViewIdSource.Reading.arrivalClock`, trong khi producer VietMap
     * ([VietMapDescParser.parseEta]) ghi bản đã chuẩn hoá 24 h ⇒ **một ô, hai miền**. Đo hạ nguồn:
     * ```
     * extractArrivalClock("5:50 PM")            = "5:50"            ← MẤT PM ⇒ sai 12 TIẾNG
     * "5:50 PM".matches("""\d{1,2}:\d{2}""")    = false             ← NavigationFrame.init `require` NÉM
     * "5:50 PM".split(":")                      = [5, null]         ← BydHal §ETA_H/ETA_M: giờ 5 (đúng 17),
     *                                                                  phút RỤNG IM LẶNG
     * ```
     * Hôm nay `Reading.arrivalClock` chưa có consumer nên chưa hỏng — nhưng consumer ĐẦU TIÊN nhận một trong
     * ba kết cục trên, và cả ba đều im lặng hoặc nổ trên xe đang chạy. Chuẩn hoá thuộc về **nơi sản xuất**,
     * không phải chờ consumer tự đoán miền (CLAUDE.md §13).
     *
     * ── LUẬT ──────────────────────────────────────────────────────────────────────────────────────────────
     *  • Có hậu tố ⇒ giờ PHẢI ∈ 1..12 (`"18:21 PM"` là vô nghĩa ⇒ null, không đoán). `12 AM`→0, `12 PM`→12.
     *  • Không hậu tố ⇒ đã là 24 h, kiểm miền như [extractArrivalClock] (h 0..23, m 0..59).
     *  • VietMap đi lối "không hậu tố": node (c) của nó khớp `^\d{1,2}:\d{2}$` nên hậu tố không bao giờ tới
     *    được đây — hàm này là SIÊU TẬP của hành vi cũ, không đổi kết quả đã đo của VietMap.
     */
    fun extractArrivalClock24(s: String): String? {
        RE_CLOCK_AMPM.find(s)?.let {
            val h12 = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            if (h12 !in 1..12 || m !in 0..59) return null
            val pm = it.groupValues[3].equals("p", ignoreCase = true)
            val h = when {
                pm && h12 < 12 -> h12 + 12   // 5 PM → 17
                !pm && h12 == 12 -> 0        // 12 AM → 0
                else -> h12                  // 12 PM → 12; 1..11 AM giữ nguyên
            }
            return String.format(Locale.US, "%d:%02d", h, m)
        }
        return extractArrivalClock(s)
    }

    /** "10:32" -> "预计今天10:32到达" (parseTime cần 预计 + 到达 + ":"). */
    fun formatEtaCn(clock: String): String = "预计今天${clock}到达"
}
