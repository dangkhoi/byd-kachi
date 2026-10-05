package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ CLUSTER-THEME-SAFE B1a (2.89) — SỔ THEME BỀN: "lần ép theme cụm gần nhất" (phạm vi XE, không theo hồ sơ) ══════════════
 *
 * Dạng một dòng `op;pending|ok;elapsedRealtime;bootCount` (vd `30;ok;81234;57`). Ghi `pending` TRƯỚC khi gửi
 * ([ThemeGate.sending]), đổi thành `ok` khi shell nhận ([ThemeGate.sent]) — CLAUDE.md §5: state đổi ra ngoài (theme cụm) sống
 * lâu hơn tiến trình nên dấu phải ghi trước, đọc được sau khi tiến trình chết.
 *
 * ## Sổ chỉ được dùng để BỎ hoặc SUY, KHÔNG BAO GIỜ để CHO PHÉP gửi
 *  • BỎ: [remainingGapMs] (hai lần đổi theme cách nhau ≥ [MIN_GAP_MS] trong cùng một lần khởi động) và luật "lần ép gần
 *    nhất đã là opcode gốc" của [ClusterStylePlan.wanted].
 *  • SUY: [believed] — cụm đang ở kiểu nào khi cổng phải bỏ opcode.
 *
 * ## Vì sao "cùng tiến trình", không chỉ "cùng boot" ([sameProcess])
 * [ĐO 29/09] BYD giết Kachi MỖI lần tắt máy, Android dựng lại tiến trình ~0,3 s sau ⇒ một lần nổ máy mới luôn là một tiến
 * trình mới. [ĐO-gv 05/10] tắt/mở máy ⇒ cụm Seal về theme gốc. [CHƯA BIẾT] đầu máy có khởi động lại (BOOT_COUNT tăng) mỗi
 * lần nổ máy không — nếu không, "cùng boot" vẫn có thể là một lần nổ máy khác. Mục sổ do tiến trình TRƯỚC ghi ⇒ không suy
 * được kiểu (UNKNOWN). Tiến trình chết vì lý do khác giữa một lần nổ máy chỉ làm suy luận bảo thủ hơn (UNKNOWN), không sai.
 */
object ThemeLedger {

    /** Khoá prefs (`:app`, tệp `clustercast` — cùng chỗ `profileOverride`, phạm vi XE). */
    const val KEY: String = "cluster_theme_ledger"

    /**
     * Hai lần đổi theme phải cách nhau ≥ 15 s — [ĐO source] `cluster-rect-seal-2026-10-05.md` §4 ("khoá 15 s giữa hai lần
     * đổi theme"). KHÔNG ngủ chờ: executor có hạn cứng 15 s, ngắt giữa `sleep` là lỗi (`SimpleCastCoordinator.openProjection`).
     */
    const val MIN_GAP_MS: Long = 15_000L

    /** BOOT_COUNT không đọc được. */
    const val UNKNOWN_BOOT: Int = -1

    enum class State { PENDING, OK }

    data class Entry(val op: Int, val state: State, val atMs: Long, val boot: Int)

    /**
     * Đồng hồ của sổ: [elapsedMs] = elapsedRealtime (gồm ngủ sâu, về 0 khi khởi động lại), [boot] = BOOT_COUNT
     * ([UNKNOWN_BOOT] = không đọc được), [processStartMs] = elapsedRealtime lúc tiến trình này khởi động.
     */
    data class Now(val elapsedMs: Long, val boot: Int, val processStartMs: Long) {
        companion object {
            /** Không biết gì — mọi phép "cùng tiến trình" đều sai, "cùng boot" thì giả định CÓ (hướng an toàn của [MIN_GAP_MS]). */
            val UNKNOWN = Now(elapsedMs = 0L, boot = UNKNOWN_BOOT, processStartMs = Long.MAX_VALUE)
        }
    }

    /** Kho bền của sổ (`:app`: SharedPreferences `commit()`). [write] trả `false` khi không ghi được. */
    interface Store {
        fun read(): String?
        fun write(value: String): Boolean
    }

    /** Đồng hồ (`:app`: `SystemClock.elapsedRealtime` · `Settings.Global.BOOT_COUNT` · `Process.getStartElapsedRealtime`). */
    fun interface Clock {
        fun now(): Now
    }

    /** Kho trong bộ nhớ — JVM/test (không bền: tiến trình chết là mất, đúng là "sổ trống"). */
    class InMemory(@Volatile private var value: String? = null) : Store {
        override fun read(): String? = value
        override fun write(value: String): Boolean { this.value = value; return true }
    }

    /** Đồng hồ JVM cho test: một "boot" cố định, tiến trình bắt đầu lúc nạp lớp. */
    val JVM_CLOCK: Clock = object : Clock {
        private val start = System.nanoTime() / 1_000_000L
        override fun now(): Now = Now(System.nanoTime() / 1_000_000L, boot = 0, processStartMs = start)
    }

    fun encode(e: Entry): String = "${e.op};${e.state.name.lowercase()};${e.atMs};${e.boot}"

    private val RE = Regex("^(\\d{1,3});(pending|ok);(\\d{1,19});(-?\\d{1,10})$")

    /** Đọc chặt: sai dạng ⇒ `null` (= sổ trống — hướng an toàn: không bỏ gì, không suy gì). */
    fun decode(raw: String?): Entry? {
        val m = RE.matchEntire(raw?.trim() ?: return null) ?: return null
        val op = m.groupValues[1].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        val at = m.groupValues[3].toLongOrNull() ?: return null
        val boot = m.groupValues[4].toIntOrNull() ?: return null
        return Entry(op, if (m.groupValues[2] == "ok") State.OK else State.PENDING, at, boot)
    }

    /**
     * Cùng một lần khởi động? Hai BOOT_COUNT đều biết ⇒ so bằng. Không biết ⇒ `elapsedRealtime` lùi là chắc chắn KHÁC boot
     * (đồng hồ về 0 khi khởi động lại); còn lại coi là CÙNG (hướng an toàn cho [remainingGapMs]: thà bỏ thêm một lượt).
     */
    fun sameBoot(e: Entry, now: Now): Boolean = when {
        e.boot != UNKNOWN_BOOT && now.boot != UNKNOWN_BOOT -> e.boot == now.boot
        else -> now.elapsedMs >= e.atMs
    }

    /** Do CHÍNH tiến trình này ghi (cùng boot ĐÃ BIẾT + mốc nằm trong đời tiến trình) — xem KDoc lớp. */
    fun sameProcess(e: Entry, now: Now): Boolean =
        e.boot != UNKNOWN_BOOT && e.boot == now.boot && e.atMs >= now.processStartMs && e.atMs <= now.elapsedMs

    /** Còn bao lâu mới được đổi theme lần nữa; `null` = được. Mục `pending` cũng tính (có thể đã tới cụm). */
    fun remainingGapMs(e: Entry?, now: Now): Long? {
        if (e == null || !sameBoot(e, now)) return null
        val age = now.elapsedMs - e.atMs
        return if (age in 0 until MIN_GAP_MS) MIN_GAP_MS - age else null
    }

    /**
     * Kiểu cụm SUY từ sổ khi cổng phải bỏ opcode: `ok` do chính tiến trình này ghi ⇒ kiểu mà opcode đó ép
     * ([ĐO 05/10] tắt chiếu KHÔNG trả theme; chỉ Kachi gửi opcode theme); còn lại ⇒ [BelievedStyle.UNKNOWN].
     *
     * Review 2.89 Pass 3 · cluster-r2-3 — NGOẠI LỆ duy nhất: `<opcode GỐC>;ok` (vd `31;ok` trên Seal 10.25") chứng minh kiểu GỐC bất
     * kể tiến trình / lần khởi động — cả hai lịch sử khả dĩ cùng một kết cục: opcode gốc còn hiệu lực trong lần nổ máy này, HOẶC
     * lần nổ máy đã trả cụm về kiểu gốc [ĐO-gv F3]. Đây đúng là giả định mà luật 3 của [ClusterStylePlan.wanted] đã dùng để BỎ gửi
     * opcode gốc — hai chỗ dùng sổ nay nói cùng một điều. `30;ok` của tiến trình trước thì thật sự mơ hồ (B1b-OQ6) ⇒ vẫn UNKNOWN.
     */
    fun believed(e: Entry?, recipe: ProjectionRecipe, now: Now): BelievedStyle {
        if (e == null || e.state != State.OK) return BelievedStyle.UNKNOWN
        val native = recipe.nativeStyle
        if (native != null && recipe.styleOps[native] == e.op) return BelievedStyle.of(native)
        if (!sameProcess(e, now)) return BelievedStyle.UNKNOWN
        return BelievedStyle.of(recipe.styleOf(e.op))
    }
}
