package com.byd.clusternav.navigation

/**
 * ═══ R9 (2.98) — tên đường lên HUD/cụm theo ĐÚNG nhịp của AmapService OEM ════════════════════════════════════════
 *
 * [ĐO nguồn fw 2606, `AmapService.java:120-145`, cùng sha1 với 2602] OEM ghi `SEND_NAVI_STATUS=2` → tên đường kế
 * (`INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET` 0x43FA1008, UTF-16LE không BOM, ≤255 byte, MỘT khung) **chỉ khi tên
 * ĐỔI** so với lần trước, giữ NGUYÊN độ dài → `FRONT_CROSSING_DISTANCE` → icon `GUIDE_INFO_SIMPLE`.
 * `BYDAutoInstrumentDevice.sendNextPathName` (:1689-1705) chỉ kiểm quyền + độ dài.
 *
 * Kachi trước R9 ghi tên đường MỌI khung, kể cả keep-alive (~4/s), SAU icon+cự ly. Trên SL6 HUD thật hiện
 * mũi tên + cự ly nhưng KHÔNG bao giờ hiện tên — [ĐOÁN] firmware coi ghi lặp liên tục là "đang đổi" và không
 * kịp/không chịu vẽ. Chưa đo được trên SL6 (owner: "cứ bắn thôi, không lên cũng không sao").
 *
 * Phần THUẦN ở đây (test off-device): cắt theo ngân sách byte + quyết định gửi/không gửi theo từng kênh.
 */
object HudPathName {
    /** Trần byte OEM cho tên đường (UTF-16LE, một khung). */
    const val MAX_BYTES = 255

    /** UTF-16LE = 2 byte/code-unit ⇒ 255 byte chứa tối đa 127 code-unit (byte lẻ cuối không dùng được). */
    const val MAX_UNITS = MAX_BYTES / 2

    /**
     * Cắt [road] cho vừa [maxBytes] byte UTF-16LE, KHÔNG tách đôi surrogate (emoji/ký tự ngoài BMP). Không rút gọn,
     * không bỏ dấu, không chuẩn hoá — OEM gửi nguyên văn, ta cũng vậy; chỉ chặn trần để HAL không từ chối.
     */
    fun fit(road: String, maxBytes: Int = MAX_BYTES): String {
        val maxUnits = maxBytes / 2
        if (road.length <= maxUnits) return road
        var cut = maxUnits
        if (cut > 0 && Character.isHighSurrogate(road[cut - 1])) cut--   // nửa đầu cặp surrogate ⇒ bỏ cả cặp
        return road.substring(0, cut)
    }

    /** Payload gửi HAL: [fit] rồi mã UTF-16LE (không BOM — `Charsets.UTF_16LE` không thêm BOM). */
    fun encode(road: String): ByteArray = fit(road).toByteArray(Charsets.UTF_16LE)

    /** rc HAL/SDK coi là "đã nhận": `0` [ĐO log xe 09-20 `PATHNAME=0 … sdk.road=0`]. "skip"/lỗi/sentinel ⇒ chưa. */
    fun accepted(rc: String?): Boolean = rc?.trim()?.toIntOrNull() == 0
}

/**
 * Nhớ tên đường đã gửi THÀNH CÔNG theo từng kênh (domestic raw · oversea raw · SDK) — mỗi kênh là một ô riêng
 * trên firmware nên dedupe riêng (kênh oversea bị từ chối không được chặn kênh domestic).
 *
 * Luật (khớp OEM): keep-alive ⇒ KHÔNG ghi; real push ⇒ chỉ ghi khi tên khác tên đã nhận lần trước; lần đầu với tên
 * rỗng ⇒ không ghi (không có gì để xoá). [reset] khi hết dẫn đường (status=4) ⇒ lần dẫn sau cùng tên vẫn gửi lại.
 */
class HudPathNameGate {
    enum class Channel { DOMESTIC, OVERSEA, SDK }

    private val lastSent = HashMap<Channel, String>()

    @Synchronized
    fun shouldSend(channel: Channel, road: String, keepAlive: Boolean): Boolean {
        if (keepAlive) return false
        return HudPathName.fit(road) != (lastSent[channel] ?: "")
    }

    /** Ghi nhận kết quả một lần gửi; chỉ nhớ khi [rc] là "đã nhận" ([HudPathName.accepted]). */
    @Synchronized
    fun onResult(channel: Channel, road: String, rc: String?) {
        if (HudPathName.accepted(rc)) lastSent[channel] = HudPathName.fit(road)
    }

    @Synchronized
    fun reset() = lastSent.clear()
}
