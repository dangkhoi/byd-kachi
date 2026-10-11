package com.byd.clusternav.launcher.voice

/**
 * ═══ 3.0 R4 `VOICE-DUCK-MUSIC` — HẠ âm lượng nhạc xuống 20 % trong lúc Kachi nghe, trả về mức cũ khi thôi nghe ═════════
 *
 * Owner 11/10 (chốt, một hành vi, không tuỳ chọn): *"làm hạ nhạc là được… những thằng khác đều làm thế, chốt mức 20% là
 * ok, xong việc thì trả về mức cũ, 1 option thôi"*. Phần THUẦN (kiểm off-car) của cơ chế; phần chạm `AudioManager` + tệp
 * dấu ở `app/…/voice/VoiceMusicDuck.kt`.
 *
 * Âm lượng `STREAM_MUSIC` là **trạng thái hệ thống sống lâu hơn tiến trình** (CLAUDE.md §5): BYD giết Kachi mỗi lần tắt
 * máy, có thể đúng giữa lượt nghe. Nên mọi lần hạ đều đi kèm một DẤU bền ([Marker]) ghi TRƯỚC khi hạ, và quyết định trả
 * lại ([decide]) chỉ dựa vào dấu + mức đọc thật lúc ấy — không cờ RAM.
 */
object VoiceMusicDuckPolicy {

    /** Mức nhạc khi nghe = 20 % mức HIỆN TẠI (owner 11/10). */
    const val FACTOR_PERCENT = 20

    /**
     * Dấu quá tuổi này ⇒ chỉ xoá, KHÔNG trả lại dù mức chưa đổi. 24 h phủ được ca thật duy nhất có dấu sót: BYD giết Kachi
     * lúc tắt máy giữa lượt nghe, xe đỗ qua đêm (≈ 8–16 h), sáng nổ máy ⇒ trả lại. Quá một ngày thì "mức vẫn bằng mức đã
     * hạ" không còn là bằng chứng đủ rằng đó là mức Kachi để lại (người dùng có thể đã tự chọn đúng mức ấy), và một cú bật
     * to bất ngờ tệ hơn một lần nhạc nhỏ mà người dùng tự vặn lại.
     */
    const val MAX_MARKER_AGE_MS = 24L * 60 * 60 * 1000

    /**
     * Mức nhạc trong lúc nghe. Đang 0 (tắt tiếng) ⇒ giữ 0. Còn lại = làm tròn `current × 20 %`, nhưng **không bao giờ về
     * 0** (owner: hạ, không tắt — người lái vẫn phải biết nhạc còn đó) và không dưới mức sàn của luồng.
     */
    fun target(current: Int, min: Int = 0): Int {
        if (current <= 0) return current.coerceAtLeast(0)
        val scaled = (current * FACTOR_PERCENT + 50) / 100   // làm tròn nửa-lên, số nguyên (không Float)
        return maxOf(scaled, 1, min).coerceAtMost(current)
    }

    /**
     * Dấu bền: mức GỐC, mức Kachi ĐÃ ĐẶT, mốc ghi (ms đồng hồ tường) và pid của tiến trình đã hạ (main hoặc `:wake` — hai
     * tiến trình cùng có thể chạy phiên nghe; pid để tiến trình kia biết dấu còn chủ sống hay không).
     */
    data class Marker(val original: Int, val set: Int, val atMs: Long, val pid: Int)

    /** Quyết định cho một dấu khi lượt nghe xong (hoặc lúc khởi động gặp dấu của tiến trình đã chết). */
    enum class Decision {
        /** Mức hiện tại vẫn đúng mức Kachi đặt ⇒ trả về mức gốc, xoá dấu. */
        RESTORE,

        /** Người dùng đã vặn núm trong lúc nghe ⇒ giữ mức của người dùng, chỉ xoá dấu. */
        KEEP_USER,

        /** Dấu quá tuổi (hoặc hỏng) ⇒ chỉ xoá. */
        STALE,
    }

    /**
     * Trả lại hay không. Đồng hồ lùi (RTC xe chỉnh lại sau mất điện ⇒ tuổi âm) ⇒ coi là tươi: dấu chỉ có thể do chính
     * Kachi ghi, và mức chưa đổi là bằng chứng đủ mạnh trong cửa sổ đó.
     */
    fun decide(marker: Marker, current: Int, nowMs: Long): Decision {
        if (current != marker.set) return Decision.KEEP_USER
        val age = nowMs - marker.atMs
        if (age > MAX_MARKER_AGE_MS) return Decision.STALE
        if (marker.original < 0 || marker.original <= marker.set) return Decision.STALE
        return Decision.RESTORE
    }

    /** Lúc khởi động/lúc hạ gặp dấu có sẵn: dấu của tiến trình KHÁC còn sống ⇒ để yên (nó đang nghe và sẽ tự trả). */
    fun ownedElsewhere(marker: Marker, myPid: Int, ownerAlive: Boolean): Boolean = marker.pid != myPid && ownerAlive

    private const val VERSION = "v1"

    fun encode(m: Marker): String = "$VERSION ${m.original} ${m.set} ${m.atMs} ${m.pid}"

    /** Đọc dấu; sai khuôn ⇒ `null` (bên gọi coi như dấu hỏng: xoá, không trả lại gì). */
    fun decode(raw: String?): Marker? {
        val p = raw?.trim()?.split(' ') ?: return null
        if (p.size != 5 || p[0] != VERSION) return null
        val original = p[1].toIntOrNull() ?: return null
        val set = p[2].toIntOrNull() ?: return null
        val at = p[3].toLongOrNull() ?: return null
        val pid = p[4].toIntOrNull() ?: return null
        if (original < 0 || set < 0) return null
        return Marker(original, set, at, pid)
    }
}
