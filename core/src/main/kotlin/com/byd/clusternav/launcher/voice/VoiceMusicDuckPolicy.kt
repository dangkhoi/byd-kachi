package com.byd.clusternav.launcher.voice

/**
 * ═══ 3.0 R4 `VOICE-DUCK-MUSIC` — HẠ âm lượng nhạc xuống 20 % trong lúc Kachi nghe, trả về mức cũ khi thôi nghe ═════════
 *
 * 3.1 (owner 13/10, spec `kachi-301-cast-steps-dialog-ia.html` §2.4 V1–V4 · R14–R17): mức hạ theo dB ([duckTarget]),
 * quyết định trả có dung sai dốc ([decide]).
 * 3.1.1 (đo xe 13/10): mức hạ có SÀN [KEEP_FLOOR_PERCENT] % chỉ số HIỆN TẠI ([duckTarget]) — trên loa BYD "20 %" thực tế là
 * "không dưới một nửa nấc đang nghe"; [FACTOR_PERCENT]/[DUCK_DB] chỉ còn quyết khi đích của chúng cao hơn sàn.
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
     * Mức nhạc trong lúc nghe theo CHỈ SỐ — luật 3.0, nay chỉ còn là **đường lùi** của [duckTarget] khi máy không cho
     * đường cong dB. Đang 0 (tắt tiếng) ⇒ giữ 0. Còn lại = làm tròn `current × 20 %`, nhưng **không bao giờ về 0** (owner:
     * hạ, không tắt — người lái vẫn phải biết nhạc còn đó) và không dưới mức sàn của luồng.
     */
    fun target(current: Int, min: Int = 0): Int {
        if (current <= 0) return current.coerceAtLeast(0)
        val scaled = (current * FACTOR_PERCENT + 50) / 100   // làm tròn nửa-lên, số nguyên (không Float)
        return maxOf(scaled, 1, min).coerceAtMost(current)
    }

    /**
     * 3.1 R17 — "20 %" là 20 % ĐỘ TO (biên độ), không phải 20 % chỉ số: `20·log10(0,2)` ≈ −13,98 dB. Đường cong âm lượng
     * của Android đi theo dB ⇒ 20 % chỉ số (10 → 2) rơi xuống gần câm — đúng triệu chứng owner 13/10 *"như dừng nhạc"*.
     */
    const val DUCK_DB = -13.98f

    /** Đường cong phẳng hơn ngưỡng này (dB) ⇒ máy không có đường cong thật (âm lượng cố định/bus) ⇒ dùng luật chỉ số. */
    private const val FLAT_CURVE_DB = 0.01f

    /**
     * Mức nhạc trong lúc nghe theo ĐƯỜNG CONG THẬT của máy. [dbByIndex]`[i]` = `AudioManager.getStreamVolumeDb(STREAM_MUSIC,
     * i, thiết bị ra)` (bên app đọc; lỗi/không hỏi được ⇒ `NaN`). Chọn chỉ số trong `[max(min,1), current)` có dB gần nhất
     * với `dB(current) + DUCK_DB`; hoà ⇒ chỉ số thấp hơn (nhỏ hơn thì micro sạch hơn). Không có đường cong dùng được (null ·
     * thiếu chỉ số · dB hiện tại không hữu hạn · không ứng viên hữu hạn · đường cong phẳng) ⇒ lùi về [target] (luật chỉ số).
     * Không bao giờ về 0 khi đang có tiếng: ứng viên bắt đầu từ 1, chỉ số `-inf` (dưới `VOLUME_MIN_DB`) bị bỏ.
     */
    fun duckTarget(current: Int, min: Int, dbByIndex: FloatArray?): Int {
        val picked = pickByDb(current, min, dbByIndex) ?: target(current, min)
        // 3.1.1 · SÀN GIỮ NHẠC — [ĐO xe 13/10, `docs/diagnostics/oncar-2026-10-13/11-voice-30-duck.txt` + `12-volume-curves.txt`]
        // thang nhạc BYD là 0..39 và đường cong loa dốc (1 % → −58 dB, 20 % → −40 dB): cả luật chỉ số (10 → 2) lẫn luật −14 dB
        // (10 → 3) đều ra gần như im ⇒ owner *"lúc như dừng nhạc"*. Không hạ dưới [KEEP_FLOOR_PERCENT] chỉ số hiện tại.
        val floor = (current * KEEP_FLOOR_PERCENT + 99) / 100
        return if (picked >= current) picked else maxOf(picked, minOf(floor, current - 1))
    }

    /** Sàn: mức khi nghe không dưới ngần này phần trăm CHỈ SỐ hiện tại (xem [duckTarget]). */
    const val KEEP_FLOOR_PERCENT = 50

    /** Phần chọn theo dB của [duckTarget]; `null` ⇒ không đủ dữ liệu, bên gọi lùi về [target]. */
    private fun pickByDb(current: Int, min: Int, dbByIndex: FloatArray?): Int? {
        val lo = maxOf(min, 1)
        if (dbByIndex == null || current <= lo || current >= dbByIndex.size) return null
        val ref = dbByIndex[current]
        if (!ref.isFinite()) return null
        val goal = ref + DUCK_DB
        var best = -1
        var bestDist = Float.MAX_VALUE
        var lowDb = ref
        var highDb = ref
        for (i in lo until current) {
            val d = dbByIndex[i]
            if (!d.isFinite()) continue
            lowDb = minOf(lowDb, d); highDb = maxOf(highDb, d)
            val dist = kotlin.math.abs(d - goal)
            if (dist < bestDist) { best = i; bestDist = dist }
        }
        if (best < 0 || highDb - lowDb < FLAT_CURVE_DB) return null
        return best
    }

    /**
     * Dấu bền: mức GỐC, mức Kachi ĐÃ ĐẶT, mốc ghi (ms đồng hồ tường) và pid của tiến trình đã hạ (main hoặc `:wake` — hai
     * tiến trình cùng có thể chạy phiên nghe; pid để tiến trình kia biết dấu còn chủ sống hay không).
     */
    data class Marker(val original: Int, val set: Int, val atMs: Long, val pid: Int)

    /** Quyết định cho một dấu khi lượt nghe xong (hoặc lúc khởi động gặp dấu của tiến trình đã chết). */
    enum class Decision {
        /** Nhạc vẫn đang nhỏ do Kachi (mức hiện tại ≤ mức đã đặt), hoặc đang đúng mức gốc ⇒ đặt mức gốc, xoá dấu. */
        RESTORE,

        /** Người dùng đã vặn TO lên / tắt tiếng, hoặc không đọc được mức ⇒ không đụng, chỉ xoá dấu. */
        KEEP_USER,

        /** Dấu quá tuổi (hoặc hỏng) ⇒ chỉ xoá. */
        STALE,
    }

    /**
     * Trả lại hay không. 3.1 R16 — **dung sai dốc**: âm lượng có thể áp TRỄ hoặc đi theo DỐC (ROM/HAL), nên mức hiện tại
     * không nhất thiết đúng bằng `set` lúc trả. Luật 3.0 so khớp tuyệt đối (`current == set`) ⇒ đọc trúng một mức giữa dốc
     * là KEEP_USER ⇒ nhạc kẹt nhỏ (owner 13/10: *"lúc như dừng nhạc luôn"*). Nay:
     *  - `current` < 0 (không đọc được) hoặc == 0 (Kachi không bao giờ đặt 0 ⇒ người dùng tự tắt tiếng) ⇒ KEEP_USER.
     *  - `current > set` ⇒ người dùng vặn TO lên (dốc của Kachi chỉ đi XUỐNG tới `set`) ⇒ KEEP_USER.
     *  - `current == original` ⇒ chưa thấy hạ (áp TRỄ còn treo) hoặc người dùng tự vặn về gốc ⇒ RESTORE: đặt lại mức gốc
     *    là vô hại khi đã đúng mức, và một lệnh hạ còn treo (ROM xử lý lệnh theo thứ tự) bị lệnh này đè ⇒ không kẹt nhỏ.
     *  - `current ≤ set` ⇒ vẫn đang nhỏ do Kachi ⇒ RESTORE (trừ dấu quá tuổi/vô lý ⇒ STALE).
     * Rủi ro chấp nhận (spec R16): người dùng vặn NHỎ HƠN nữa trong ≤ 8 s nghe ⇒ bị trả về mức gốc.
     * Đồng hồ lùi (RTC xe chỉnh lại sau mất điện ⇒ tuổi âm) ⇒ coi là tươi: dấu chỉ có thể do chính Kachi ghi.
     */
    fun decide(marker: Marker, current: Int, nowMs: Long): Decision {
        if (marker.original < 0 || marker.original <= marker.set) return Decision.STALE
        if (current <= 0 || current > marker.original) return Decision.KEEP_USER
        if (current > marker.set && current != marker.original) return Decision.KEEP_USER
        if (nowMs - marker.atMs > MAX_MARKER_AGE_MS) return Decision.STALE
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
