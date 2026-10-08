package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ 2.98 · R6-D — lệnh shell CHỈ-ĐỌC dò định kỳ: log khi KẾT QUẢ ĐỔI hoặc khi HỎNG, không log mỗi lượt ═════════
 *
 * ## Bệnh [ĐO log xe]
 * `DadbSimpleCastShell.execute` (`:app` `SimpleCastRuntime.kt`) ghi **hai** dòng cho MỌI lệnh: `shell: <lệnh>` +
 * `shell OK: exit=0`. Tệp `usage-1790434182772.log` (xe, 26/09): **8 719 / 8 746** dòng `shell:` là `am stack list`
 * (lượt dò của `AppMover`); log SL6 08/10: ~533 dòng / 37 phút dù `LogLineThrottle` đã chặn còn 1 dòng/10 s mỗi câu.
 * Hai dòng ấy không mang thông tin gì: không có kết quả, và lượt dò thì đã biết là chạy mỗi vài giây.
 *
 * ## Luật (owner 2026-10-08 *"làm đi"*: không thêm chi phí chạy, không thứ gì phình vô hạn)
 *  • **Lệnh ĐỔI trạng thái** (`am start`, `am force-stop`, `wm …`, `settings put`, `service call`, …) — KHÔNG đi qua cổng
 *    này, log đầy đủ như cũ: chúng là bằng chứng pháp y (CLAUDE.md §5/§6).
 *  • **Lệnh chỉ-đọc** — chỉ những lệnh khớp [READ_ONLY_PREFIXES] (danh sách CHO PHÉP, không phải "mọi thứ trừ…",
 *    CLAUDE.md §4; `dumpsys` siết thêm theo dịch vụ — [DUMPSYS_SERVICES]) và KHÔNG mang ký tự nối/chuyển hướng shell
 *    ([SHELL_META]) — log khi:
 *      1. lần đầu thấy lệnh đó trong tiến trình;
 *      2. kết quả (mã thoát + dấu vân tay stdout/stderr) KHÁC lần trước;
 *      3. **hỏng** (mã thoát ≠ 0) — không bao giờ nuốt một dòng lỗi;
 *      4. đã im quá [HEARTBEAT_MS] (còn mốc thời gian để biết lượt dò vẫn sống).
 *    Dòng được ghi mang số lượt giống hệt đã lược ([Decision.suppressed]) ⇒ không có lượt nào mất không dấu vết.
 *
 * Bộ nhớ có biên: LRU [MAX_KEYS] lệnh, mỗi mục chỉ giữ dấu vân tay (không giữ chuỗi kết quả). Khoá bị đẩy ra ⇒ lần
 * sau coi là "chưa thấy" ⇒ GHI (biên bộ nhớ không bao giờ biến thành một dòng bị mất — cùng luật [com.byd.clusternav.launcher.LogLineThrottle]).
 *
 * THUẦN (đồng hồ truyền vào) ⇒ `ShellLogGateTest` khoá off-device. Chỗ gọi: `DadbSimpleCastShell.execute`.
 */
class ShellLogGate(
    private val heartbeatMs: Long = HEARTBEAT_MS,
    private val maxKeys: Int = MAX_KEYS,
) {

    /** Quyết định cho một lượt chạy lệnh chỉ-đọc. */
    data class Decision(val log: Boolean, val suppressed: Int, val changed: Boolean)

    private class Slot(var fingerprint: Long, var loggedAtMs: Long, var suppressed: Int)

    private val seen = object : LinkedHashMap<String, Slot>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Slot>?): Boolean = size > maxKeys
    }

    /**
     * Một lượt [command] (đã biết là chỉ-đọc — [isReadOnly]) vừa chạy xong với [exitCode] / [stdout] / [stderr].
     * Trả có ghi hay không, và số lượt giống hệt đã lược kể từ dòng ghi trước.
     *
     * Đồng hồ lùi (`nowMs < loggedAtMs`) ⇒ coi như đã quá nhịp ⇒ GHI (không để một cú nhảy giờ khoá log vĩnh viễn).
     */
    @Synchronized
    fun decide(command: String, exitCode: Int, stdout: String, stderr: String, nowMs: Long): Decision {
        val fp = fingerprint(exitCode, stdout, stderr)
        val slot = seen[command]
        if (slot == null) {
            seen[command] = Slot(fp, nowMs, 0)
            return Decision(log = true, suppressed = 0, changed = true)
        }
        val changed = slot.fingerprint != fp
        val elapsed = nowMs - slot.loggedAtMs
        val due = elapsed !in 0 until heartbeatMs
        if (exitCode == 0 && !changed && !due) {
            slot.suppressed++
            return Decision(log = false, suppressed = 0, changed = false)
        }
        val skipped = slot.suppressed
        slot.fingerprint = fp
        slot.loggedAtMs = nowMs
        slot.suppressed = 0
        return Decision(log = true, suppressed = skipped, changed = changed)
    }

    companion object {
        /**
         * Lệnh chỉ-đọc được phép đi qua cổng (so ĐẦU chuỗi). Mỗi mục là một lệnh KHÔNG đổi trạng thái hệ thống:
         *  • `am stack list` — liệt kê stack (AppMover dò mỗi vài giây — 99,7 % dòng `shell:` của log xe 26/09);
         *  • `dumpsys ` — chỉ với dịch vụ trong [DUMPSYS_SERVICES] (xem [dumpsysReadOnly]);
         *  • `appops get ` — đọc app-op (không phải `appops set`);
         *  • `settings get ` — đọc (không phải `settings put/delete`);
         *  • `getprop ` — đọc thuộc tính (không phải `setprop`).
         * Thêm lệnh mới vào đây = khẳng định nó chỉ-đọc; `ShellLogGateTest` khoá danh sách.
         */
        val READ_ONLY_PREFIXES: List<String> = listOf("am stack list", "dumpsys ", "appops get ", "settings get ", "getprop ")

        /**
         * Soát Fable 2.98 Pass 1 [P2] — `dumpsys` KHÔNG phải một họ chỉ-đọc: nhiều dịch vụ nhận lệnh con GHI qua chính `dump()`
         * (`dumpsys window tracing start` · `dumpsys deviceidle whitelist +gói` · `dumpsys battery unplug` · `dumpsys gfxinfo <gói>
         * reset` · `dumpsys batterystats --reset` · `dumpsys car_service …` của Automotive). Tiền tố `dumpsys ` một mình là "mọi thứ
         * trừ…" (CLAUDE.md §4) ⇒ chỉ dịch vụ trong danh sách này — dump THUẦN, không có lệnh con ghi trên A10/A12 — mới được
         * tiết chế; dịch vụ khác (kể cả Kachi chưa gọi bao giờ) ⇒ log đầy đủ như lệnh đổi trạng thái. Token ghi [DUMPSYS_WRITE_TOKENS]
         * bị chặn ở mọi dịch vụ; `activity` chỉ với trang đọc trong [DUMPSYS_ACTIVITY_PAGES] (dump `service <tên>` chuyển tham số cho
         * dịch vụ bên thứ ba — không đoán nó làm gì).
         */
        val DUMPSYS_SERVICES: Set<String> = setOf(
            "display", "window", "activity", "accessibility", "package", "notification", "appwidget", "meminfo", "dropbox",
            "input", "power", "alarm", "role",
        )

        /** Trang của `dumpsys activity` là dump thuần (tham số đầu). Không tham số ⇒ dump tổng — cũng chỉ-đọc. */
        val DUMPSYS_ACTIVITY_PAGES: Set<String> = setOf(
            "activities", "a", "services", "s", "recents", "r", "top", "broadcasts", "b", "processes", "p", "intents", "i",
            "starter", "lastanr", "permissions", "providers", "prov", "settings", "containers", "all",
        )

        /**
         * Soát Pass 2 — lệnh con GHI của `dumpsys activity` (AMS `doDump`: `write` ép ghi recents ra đĩa · `track-associations` /
         * `untrack-associations` bật-tắt theo dõi) và `dumpsys package` (PMS `dump`: `write` ⇒ `mSettings.writeLPr()` ghi
         * `packages.xml`) — [SUY] theo nguồn AOSP A10/A12 từ trí nhớ, Context7 không nối được; danh sách CHO PHÉP trang đọc ở trên là
         * hàng rào chính, danh sách này chỉ là hàng rào thứ hai (và là chỗ gọi tên bệnh cho người đọc sau).
         */
        val DUMPSYS_WRITE_TOKENS: Set<String> = setOf("write", "track-associations", "untrack-associations", "tracing")

        /**
         * [args] = phần sau `dumpsys ` (đã trim). Dịch vụ phải thuộc [DUMPSYS_SERVICES]; không mang token [DUMPSYS_WRITE_TOKENS] ở
         * bất kỳ vị trí nào; `activity` thì token KHÔNG-tuỳ-chọn ĐẦU TIÊN (bỏ qua mọi `-x`/`--xx` đứng trước — soát Pass 2:
         * `dumpsys activity -a write` từng lọt vì chỉ nhìn token thứ hai) phải là trang đọc trong [DUMPSYS_ACTIVITY_PAGES]. Mọi dấu
         * `-` tuỳ chọn (`-d`, `--proto`, `--noredact`) tự nó không đổi trạng thái. Dấu nháy/escape/glob đã bị [SHELL_META] chặn
         * trước đó ⇒ so token nguyên văn là đủ (`'write'` không lách được).
         */
        fun dumpsysReadOnly(args: String): Boolean {
            val toks = args.split(' ', '\t').filter { it.isNotEmpty() }
            val service = toks.firstOrNull() ?: return false
            if (service !in DUMPSYS_SERVICES) return false
            if (toks.any { it in DUMPSYS_WRITE_TOKENS }) return false
            return when (service) {
                "activity" -> toks.drop(1).firstOrNull { !it.startsWith("-") }?.let { it in DUMPSYS_ACTIVITY_PAGES } ?: true
                else -> true
            }
        }

        /**
         * Ký tự nối / chuyển hướng / thay thế lệnh / nháy / escape / glob: có mặt ⇒ không còn là MỘT lệnh chỉ-đọc so được theo token
         * ⇒ log đầy đủ. Nháy–escape–glob (`'` `"` `\` `*` `?` `[` `{`) vào đây từ soát Pass 2: shell bóc nháy TRƯỚC khi dịch vụ
         * nhận tham số, nên `dumpsys package 'write'` mà so token nguyên văn sẽ lọt. Mọi lệnh chỉ-đọc Kachi đang gọi không mang
         * ký tự nào trong số này (các lệnh có nháy đều đi kèm `| grep` ⇒ đã log đầy đủ từ trước) — [ĐO grep 2026-10-08].
         */
        private val SHELL_META = charArrayOf(';', '&', '|', '>', '<', '`', '$', '\n', '\r', '\'', '"', '\\', '*', '?', '[', '{')

        /** Nhịp sống: một lệnh chỉ-đọc không đổi kết quả vẫn được ghi ít nhất mỗi 10 phút. */
        const val HEARTBEAT_MS = 10L * 60_000L

        /** Trần số lệnh khác nhau được nhớ — lệnh dò là một tập nhỏ hữu hạn (vài chục), 64 đủ rộng. */
        const val MAX_KEYS = 64

        /** [command] có thuộc tập chỉ-đọc được tiết chế không. */
        fun isReadOnly(command: String): Boolean {
            val c = command.trim()
            if (c.isEmpty() || c.indexOfAny(SHELL_META) >= 0) return false
            val hit = READ_ONLY_PREFIXES.firstOrNull { p -> val head = p.trimEnd(); c == head || c.startsWith("$head ") } ?: return false
            return if (hit.trimEnd() == "dumpsys") dumpsysReadOnly(c.removePrefix("dumpsys").trim()) else true
        }

        /** Hậu tố cho dòng ghi: kết quả đổi hay chỉ là nhịp sống, và số lượt giống hệt đã lược. */
        fun suffix(d: Decision): String =
            (if (d.changed) "" else " [không đổi]") + (if (d.suppressed <= 0) "" else " [+${d.suppressed} lượt giống hệt đã lược]")

        /** Dấu vân tay 64-bit của kết quả (FNV-1a) — không giữ chuỗi kết quả trong bộ nhớ. */
        internal fun fingerprint(exitCode: Int, stdout: String, stderr: String): Long {
            var h = -0x340d631b7bdddcdbL   // FNV offset basis 0xcbf29ce484222325
            fun mix(s: String) {
                for (ch in s) { h = (h xor ch.code.toLong()) * 0x100000001b3L }
                h = (h xor 0x1fL) * 0x100000001b3L   // dấu phân cách giữa các trường
            }
            mix(exitCode.toString()); mix(stdout); mix(stderr)
            return h
        }
    }
}
