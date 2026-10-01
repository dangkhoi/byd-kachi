package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.StackEntry
import com.byd.clusternav.modules.clustercast.StackParse

/**
 * ═══ PROFILE-SWITCH-SLOTS · R-B3 — CHỌN cửa sổ nổi do Kachi mở cần ĐÓNG (thuần, không chạy lệnh) ═══════════════
 *
 * Đầu vào là SỰ THẬT của hệ (`am stack list` đã qua [StackParse.parse]) + dấu bền ([FloatingWindowLedger]) + các
 * gói state còn giữ trong ô. Đầu ra là danh sách id stack để `am stack remove <id>`. Không đọc cờ RAM nào (CLAUDE.md
 * §5). Thi hành ở [FloatingOrphanSweep]; spec `docs/specs/kachi-profile-switch-slots.html` §4.3.
 *
 * ## Bốn câu của CLAUDE.md §4 cho `am stack remove <id>`
 *  1. **Display nào** — chỉ stack có `displayId=0` trong bản đọc của CHÍNH lượt đó. Màn ảo của ô / cụm không bao giờ.
 *  2. **App nào** — allow-list: gói trong dấu − gói state còn giữ − chính Kachi − gói có task ở display khác 0. Một
 *     stack có BẤT KỲ task nào ngoài tập đó ⇒ bỏ cả stack.
 *  3. **Loại stack nào** — chỉ `mActivityType=standard` (CHỮ, không nhận chuỗi trống) + `freeform` + không pinned.
 *     ⚠ Chặt hơn [StackParse.floatingOnMain] (hàm đó coi loại trống là standard — chấp nhận được cho đường cast chỉ
 *     ĐỌC). Lý do bắt buộc [ĐO AOSP]: A10 r47 framework tự chặn non-standard (`ActivityTaskManagerService.java
 *     :3383-3386`, [ĐO máy ảo] `IllegalArgumentException … exit=255`), nhưng A12 r34 thì KHÔNG (`removeTask`
 *     `:1904-1926` không kiểm loại) ⇒ trên DL5 bộ lọc này là rào DUY NHẤT. Không sửa `floatingOnMain` (đường cast
 *     đang dùng — §6).
 *  4. **Hoàn tác** — không hoàn tác được (≈ vuốt khỏi Gần đây; [ĐO máy ảo] tiến trình app còn sống vì activity chưa
 *     dừng). Người dùng mở lại bằng chạm thẻ ô / ngăn kéo. Hỏng giữa chừng ⇒ dấu còn ⇒ lượt sau dọn tiếp.
 *
 * Chuỗi lệnh GIỐNG NHAU ở A10 và A12 ([ĐO AOSP] spec §2.6) ⇒ không có mục nào trong `ClusterProfile` (§7). ROM cắt
 * lệnh ⇒ đọc ra rỗng ⇒ [plan] rỗng ⇒ không làm gì.
 */
object FloatingOrphanPlan {

    /** Đọc sự thật — cùng chuỗi mọi chỗ khác của dự án dùng. */
    const val LIST_CMD = "am stack list"

    /** Màn chính — đích DUY NHẤT của lệnh gỡ. */
    const val MAIN_DISPLAY = 0

    /** Loại stack DUY NHẤT được gỡ — so CHỮ, chuỗi trống không tính (xem KDoc lớp, câu 3). */
    const val STANDARD = "standard"

    /**
     * Lệnh gỡ một stack. [ĐO máy ảo A10, uid 2000] `docs/diagnostics/profile-switch-slots-emulator-2026-10-01.md`.
     * A10: `ActivityManagerShellCommand.runStackRemove` (r47 `:2629-2634`); A12: `runRootTaskRemove` (r34 `:2716-2721`).
     */
    fun removeCmd(stackId: Int): String = "am stack remove $stackId"

    /** Cửa sổ nổi trên màn chính theo nghĩa CHẶT (loại `standard` đọc được bằng chữ). Một entry mỗi stack. */
    fun strictFloatingOnMain(entries: List<StackEntry>): List<StackEntry> =
        StackParse.floatingOnMain(entries).filter { it.activityType == STANDARD }

    /** Gói được phép đóng cửa sổ (câu 2). */
    fun targets(entries: List<StackEntry>, opened: Collection<String>, held: Collection<String>, selfPkg: String): Set<String> {
        val elsewhere = entries.filter { it.displayId != MAIN_DISPLAY }.map { it.pkg }.toSet()
        return opened.filterTo(mutableSetOf()) { it !in held && it != selfPkg && it !in elsewhere }
    }

    /**
     * Stack [stackId] có được phép gỡ không — xét MỌI task của nó trong [entries] (bản đọc của chính lượt đó).
     * Đây là hàm mà bộ thi hành gọi lại ngay trước lệnh gỡ (guard ở tầng thi hành, CLAUDE.md §5).
     * [selfPkg] trống / stack không có trong bản đọc ⇒ `false` (đọc hỏng là không làm gì).
     */
    fun admissible(
        stackId: Int,
        entries: List<StackEntry>,
        opened: Collection<String>,
        held: Collection<String>,
        selfPkg: String,
    ): Boolean {
        if (selfPkg.isBlank()) return false
        val tasks = entries.filter { it.stackId == stackId }
        if (tasks.isEmpty()) return false
        val allowed = targets(entries, opened, held, selfPkg)
        return tasks.all {
            it.displayId == MAIN_DISPLAY && it.activityType == STANDARD && it.isFreeform && !it.isPinned &&
                it.pkg in allowed
        }
    }

    /** Id stack cần gỡ, theo thứ tự xuất hiện trong bản đọc. Dấu rỗng ⇒ rỗng. */
    fun plan(entries: List<StackEntry>, opened: Collection<String>, held: Collection<String>, selfPkg: String): List<Int> {
        if (opened.isEmpty()) return emptyList()
        return strictFloatingOnMain(entries).map { it.stackId }.distinct()
            .filter { admissible(it, entries, opened, held, selfPkg) }
    }

    /**
     * Gói được xoá khỏi dấu sau lượt gỡ: không còn cửa sổ nổi trên màn chính (bản đọc LẦN HAI) **và** không còn ô nào
     * giữ. Vì sao trừ cả gói còn giữ (chặt hơn spec Pass 0): gói còn trong ô có thể vừa được chạm mở mà cửa sổ chưa kịp
     * hiện lúc đọc ⇒ xoá dấu lúc đó là mất dấu của đúng cửa sổ sắp hiện. Gói còn giữ ở lại trong dấu tới khi rời ô.
     */
    fun forgettable(after: List<StackEntry>, opened: Collection<String>, held: Collection<String>): Set<String> {
        val still = strictFloatingOnMain(after).map { it.pkg }.toSet()
        return opened.filterTo(mutableSetOf()) { it !in still && it !in held }
    }

    /** Gói trong dấu còn cửa sổ nổi trên màn chính — cho dòng log. */
    fun stillFloating(after: List<StackEntry>, opened: Collection<String>): Set<String> {
        val still = strictFloatingOnMain(after).map { it.pkg }.toSet()
        return opened.filterTo(sortedSetOf()) { it in still }
    }
}
