package com.byd.clusternav.navigation

/**
 * ═══ FIX286 R-HUD — SỰ THẬT "nguồn thông báo đã GẮN chưa" đọc từ `system_server` (thuần, `:core`) ══════════════════
 *
 * HUD kính lái chỉ có MỘT nguồn: `NavNotificationListener` (thông báo Google Maps — [NavApps.NOTIFICATION]). Dịch vụ đó
 * được CẤP (có trong danh sách duyệt) mà KHÔNG được `NotificationManagerService` GẮN là ca "HUD lúc có lúc không"
 * (spec `kachi-286-field-fixes.html` §3.9). Cờ `NavNotificationListener.connected` trong RAM chỉ nói phía TA đã nhận
 * `onListenerConnected` chưa — quyết định ĐỔI trạng thái hệ thống (disallow → allow) phải dựa vào sự thật của chính
 * NMS (CLAUDE.md §5), tức khối `Live notification listeners` trong `dumpsys notification`.
 *
 * ## Định dạng [ĐO nguồn]
 * AOSP `android-10.0.0_r47` `ManagedServices.java:216-234` và `android-12.0.0_r34` `ManagedServices.java:370-392` in
 * cùng một khuôn (caption của bộ nghe = `"notification listener"`, NMS r47 `:7826` · r34 `:10088`):
 * ```
 *     All notification listeners (3) enabled for current profiles:
 *       ComponentInfo{com.byd.launcher/com.byd.clusternav.NavNotificationListener}
 *     Live notification listeners (4):
 *       ComponentInfo{com.byd.launcher/com.byd.clusternav.NavNotificationListener} (user 0): android.service…Proxy@22d7cc3
 *     Snoozed notification listeners (0):
 * ```
 * Dòng Live in `info.component` = `ComponentName.toString()` = `ComponentInfo{gói/LỚP-ĐẦY-ĐỦ}`; dòng Snoozed in
 * `flattenToShortString()` (`gói/.Lớp`). Khối trợ lý (`Live notification assistants (1):`, caption
 * `"notification assistant"`) đứng ngay sau và có tiền tố KHÁC — không bao giờ lẫn vào đây.
 * Bản dump nguyên văn từ máy ảo A10 (`emulator-5554`, 03/10) nằm ở `core/src/test/resources/diagnostics/
 * dumpsys-notification-emulator-2026-10-03-*.txt` và khoá ở `NlsLiveDumpTest`.
 *
 * Bộ lọc gói `dumpsys notification p <gói>` (NMS r47 `DumpFilter.parseFromArguments` `:8403-8411`, r34 `:10944`) thu
 * khối Live về đúng các component của gói đó — đầu ra ~27 KB thay vì ~300 KB [ĐO máy ảo 03/10].
 */
object NlsLiveDump {

    /** Kết luận cho MỘT component. [UNREADABLE] = không thấy khối Live ⇒ KHÔNG được hành động (không đoán). */
    enum class Verdict { LIVE, NOT_LIVE, UNREADABLE }

    /** Ba danh sách của khối bộ nghe — dạng phẳng `gói/lớp-đầy-đủ`. */
    data class ListenerBlock(val enabled: Set<String>, val live: Set<String>, val snoozed: Set<String>)

    /** Lệnh đọc — CHỈ ĐỌC, lọc theo gói của chính mình (CLAUDE.md §4: không quét mù). */
    fun command(ownPackage: String): String {
        require(PACKAGE.matches(ownPackage)) { "gói không hợp lệ: '$ownPackage'" }
        return "dumpsys notification p $ownPackage"
    }

    /** `null` ⇔ không thấy dòng `Live notification listeners (` (lệnh hỏng / định dạng lạ). */
    fun parse(dump: String?): ListenerBlock? {
        if (dump.isNullOrBlank()) return null
        val enabled = LinkedHashSet<String>()
        val live = LinkedHashSet<String>()
        val snoozed = LinkedHashSet<String>()
        var sawLive = false
        var mode: MutableSet<String>? = null
        var headerIndent = 0
        for (raw in dump.lineSequence()) {
            val line = raw.trimEnd('\r')
            val t = line.trimStart()
            val indent = line.length - t.length
            val into = mode
            when {
                t.startsWith(HDR_ENABLED) -> { mode = enabled; headerIndent = indent }
                t.startsWith(HDR_LIVE) -> { mode = live; headerIndent = indent; sawLive = true }
                t.startsWith(HDR_SNOOZED) -> { mode = snoozed; headerIndent = indent }
                // Dòng trống / dòng ngang hàng tiêu đề (`mListenerHints: 0`, `Allowed …:`) ⇒ hết danh sách.
                t.isEmpty() || indent <= headerIndent -> mode = null
                into == null -> Unit
                // Snoozed in `flattenToShortString()` trần; Enabled/Live in `ComponentInfo{…}`.
                into === snoozed -> normalize(t.substringBefore(' '))?.let { into.add(it) }
                else -> COMPONENT_INFO.find(t)?.groupValues?.get(1)?.let(::normalize)?.let { into.add(it) }
            }
        }
        return if (sawLive) ListenerBlock(enabled, live, snoozed) else null
    }

    fun verdict(dump: String?, component: String): Verdict {
        val block = parse(dump) ?: return Verdict.UNREADABLE
        val want = normalize(component) ?: return Verdict.UNREADABLE
        return if (want in block.live) Verdict.LIVE else Verdict.NOT_LIVE
    }

    /** `gói/.Lớp` → `gói/gói.Lớp`; dạng khác `gói/lớp` ⇒ `null`. */
    fun normalize(flat: String): String? {
        val slash = flat.indexOf('/')
        if (slash <= 0 || slash == flat.length - 1) return null
        val pkg = flat.substring(0, slash)
        val cls = flat.substring(slash + 1)
        return if (cls.startsWith(".")) "$pkg/$pkg$cls" else "$pkg/$cls"
    }

    private const val HDR_ENABLED = "All notification listeners ("
    private const val HDR_LIVE = "Live notification listeners ("
    private const val HDR_SNOOZED = "Snoozed notification listeners ("
    private val COMPONENT_INFO = Regex("""ComponentInfo\{([^}\s]+)\}""")
    private val PACKAGE = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+""")
}
