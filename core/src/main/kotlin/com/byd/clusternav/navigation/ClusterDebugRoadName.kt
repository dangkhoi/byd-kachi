package com.byd.clusternav.navigation

/**
 * ═══ 3.1.12 · HUD-ROADNAME-ALT-PATH — mã TÊN ĐƯỜNG theo khuôn khúc của receiver ClusterDebug (đường "B") ══════════════════
 *
 * Phần THUẦN (test off-device) của đường gửi tên đường thứ hai: broadcast tường minh tới
 * `com.byd.clusterdebug/.BroadcastReceiverCAN` (`--es normal <hex,hex,…>`) ⇒ receiver tách theo `,` → `byte[]` →
 * `BYDAutoTestDevice.set({0xAA00020F}, buffer)` [ĐO nguồn ClusterDebug 10601004 của fw 2606,
 * `BroadcastReceiverCAN.java:17-36` — doc `docs/diagnostics/hud-clusterdebug-path-2026-10-16.md` §2.1].
 *
 * Khuôn [ĐO DiAuto 0.3.11 đã decompile, lớp gói tên đường; khớp byte với gói H2a/H2b của
 * `scripts/oncar/hud-clusterdebug-nav.sh`]:
 *  • nội dung = `[số byte UTF-16LE][UTF-16LE…][tổng các byte & 0xFF]`, đệm `FF` tới bội 7 (`((n + 8) / 7) * 7` byte);
 *  • khúc thứ i (từ 0) = bản ghi `[id BE 4][len=08][số thứ tự = (id >> 12) & 0xFF][7 byte nội dung]`, id = `0x43FA1008 + (i << 12)`;
 *  • tối đa 48 đơn vị UTF-16 (96 byte ⇒ 98 byte nội dung ⇒ 14 khúc ⇒ id cuối `0x43FAE008`); cặp surrogate không bị tách;
 *  • ký tự điều khiển / khoảng trắng lạ ⇒ dấu cách; cắt xong thì `trim`; rỗng ⇒ một dấu cách (DiAuto không bao giờ gửi rỗng).
 *
 * ⚠ AN TOÀN (CLAUDE.md §4) — gói ra ngoài CHỈ được chứa bản ghi tên đường: [ALLOWED_IDS] (14 id liền nhau của họ `0x43FA?008`)
 * + đúng `len=8` + đúng số thứ tự. [allowed] là cổng cứng, nơi gửi ở `:app` gọi lại nó NGAY trước broadcast — gói sai ⇒ không
 * gửi. Không `wholeFrame`, không opcode cụm, không trạng thái dẫn `0x43E0003A`, không mũi tên/cự ly (chúng ở lại đường HAL).
 * ⚠ Trần id: kịch bản xe chỉ cần 4 khúc (hai chuỗi thử ≤ 13 đơn vị) nên allow-list của nó dừng ở `0x43FA4008`; trần 48 đơn vị
 * của DiAuto cần tới 14 khúc ⇒ ở đây là `0x43FA1008..0x43FAE008`. Khúc thứ 15 sẽ là `0x43FAF008`, khúc 16 tràn sang
 * `0x43FB0008` (một feature KHÁC) — [MAX_CHUNKS] chặn trước điều đó.
 */
object ClusterDebugRoadName {

    const val PACKAGE = "com.byd.clusterdebug"
    const val RECEIVER = "com.byd.clusterdebug.BroadcastReceiverCAN"

    /** Receiver KHÔNG đọc action (`BroadcastReceiverCAN.java`) — nhãn giữ như DiAuto để log hai bên đọc giống nhau. */
    const val ACTION = "byd.hud.NAVIGATION"

    /** Extra duy nhất được dùng. `wholeFrame` bị cấm (ghi đè mảng của `normal`, `BroadcastReceiverCAN.java:24-28`). */
    const val EXTRA = "normal"

    /** Bản ClusterDebug đã đọc source (fw 2606 `meta/packages.txt:24`). Bản khác ⇒ không gửi (chưa có source). */
    val KNOWN_VERSION_CODES: Set<Long> = setOf(10_601_004L)

    const val BASE_ID = 0x43FA1008
    const val MAX_UNITS = 48
    const val CHUNK_BYTES = 7
    const val MAX_CHUNKS = 14

    /** Tên dùng để XOÁ khi hết dẫn: đúng thứ bộ mã hoá DiAuto gửi cho tên rỗng (một dấu cách). */
    const val CLEAR_NAME = " "

    val ALLOWED_IDS: Set<Int> = (0 until MAX_CHUNKS).map { BASE_ID + (it shl 12) }.toSet()

    /** Cắt [road] về ≤ [MAX_UNITS] đơn vị UTF-16 tại ranh giới code point, bỏ surrogate mồ côi, ký tự điều khiển ⇒ cách. */
    fun fit(road: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < road.length && sb.length < MAX_UNITS) {
            val ch = road[i]
            when {
                Character.isHighSurrogate(ch) -> {
                    if (i + 1 < road.length && Character.isLowSurrogate(road[i + 1])) {
                        if (sb.length + 2 > MAX_UNITS) break
                        sb.append(ch).append(road[i + 1])
                        i++
                    }
                }
                Character.isLowSurrogate(ch) -> Unit
                Character.isISOControl(ch) || (Character.isWhitespace(ch) && ch != ' ') -> sb.append(' ')
                else -> sb.append(ch)
            }
            i++
        }
        return sb.toString().trim().ifEmpty { CLEAR_NAME }
    }

    /** Số đơn vị UTF-16 thật sự đi ra (để log độ dài — KHÔNG log chữ). */
    fun units(road: String): Int = fit(road).length

    /** Chuỗi bản ghi khúc cho [road] (đã [fit]). Luôn qua được [allowed]. */
    fun encode(road: String): ByteArray {
        val text = fit(road).toByteArray(Charsets.UTF_16LE)
        val size = ((text.size + 8) / CHUNK_BYTES) * CHUNK_BYTES
        val body = ByteArray(size) { 0xFF.toByte() }
        body[0] = text.size.toByte()
        text.copyInto(body, 1)
        body[text.size + 1] = text.sumOf { it.toInt() and 0xFF }.toByte()
        val chunks = size / CHUNK_BYTES
        val out = ByteArray(chunks * RECORD_BYTES)
        for (c in 0 until chunks) {
            val id = BASE_ID + (c shl 12)
            val at = c * RECORD_BYTES
            out[at] = (id ushr 24).toByte()
            out[at + 1] = (id ushr 16).toByte()
            out[at + 2] = (id ushr 8).toByte()
            out[at + 3] = id.toByte()
            out[at + 4] = (CHUNK_BYTES + 1).toByte()
            out[at + 5] = (id ushr 12).toByte()
            body.copyInto(out, at + 6, c * CHUNK_BYTES, (c + 1) * CHUNK_BYTES)
        }
        return out
    }

    /** Dạng extra `normal`: `%02X` nối bằng `,` (receiver `Integer.parseInt(tok, 16)` từng token). */
    fun hex(bytes: ByteArray): String = bytes.joinToString(",") { "%02X".format(it.toInt() and 0xFF) }

    /**
     * Cổng CỨNG: mọi bản ghi trong [bytes] phải là một khúc tên đường hợp lệ — id ∈ [ALLOWED_IDS], `len = 8`, byte đầu giá
     * trị = số thứ tự của id, không thừa/thiếu byte, ≥ 1 bản ghi, ≤ [MAX_CHUNKS] bản ghi, không id lặp. Sai một chỗ ⇒ `false`.
     */
    fun allowed(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.size % RECORD_BYTES != 0) return false
        val n = bytes.size / RECORD_BYTES
        if (n > MAX_CHUNKS) return false
        val seen = HashSet<Int>()
        for (r in 0 until n) {
            val at = r * RECORD_BYTES
            val id = ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or
                ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)
            if (id !in ALLOWED_IDS || !seen.add(id)) return false
            if ((bytes[at + 4].toInt() and 0xFF) != CHUNK_BYTES + 1) return false
            if ((bytes[at + 5].toInt() and 0xFF) != ((id ushr 12) and 0xFF)) return false
        }
        return true
    }

    /** id 4 + len 1 + số thứ tự 1 + 7 byte nội dung. */
    private const val RECORD_BYTES = 4 + 1 + 1 + CHUNK_BYTES

    // ── khả dụng (quyết định THUẦN; `:app` đọc PackageManager rồi đưa sự thật vào đây) ───────────────────────────────

    enum class Availability { OK, NOT_INSTALLED, NOT_SYSTEM, DISABLED, NO_RECEIVER, PROTECTED, UNKNOWN_VERSION }

    /**
     * Đường B chỉ mở khi đúng các sự thật đã đọc source: gói có, là app HỆ THỐNG, đang bật, receiver có + exported + bật +
     * KHÔNG permission, và versionCode là bản đã RE. Thứ tự kiểm = thứ tự lý do hiện cho người dùng.
     */
    fun availability(
        installed: Boolean,
        system: Boolean,
        appEnabled: Boolean,
        versionCode: Long,
        receiverFound: Boolean,
        receiverExported: Boolean,
        receiverEnabled: Boolean,
        receiverPermission: String?,
    ): Availability = when {
        !installed -> Availability.NOT_INSTALLED
        !system -> Availability.NOT_SYSTEM
        !appEnabled -> Availability.DISABLED
        !receiverFound -> Availability.NO_RECEIVER
        !receiverEnabled -> Availability.DISABLED
        !receiverExported || !receiverPermission.isNullOrEmpty() -> Availability.PROTECTED
        versionCode !in KNOWN_VERSION_CODES -> Availability.UNKNOWN_VERSION
        else -> Availability.OK
    }
}
