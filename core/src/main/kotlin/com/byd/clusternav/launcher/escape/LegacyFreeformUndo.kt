package com.byd.clusternav.launcher.escape

import com.byd.clusternav.launcher.ShellAppLauncher
import com.byd.clusternav.modules.clustercast.StackEntry

/**
 * ═══ 2.98 · R18 — ĐƯỜNG TRẢ cho máy đã chạy bản R7 (thuần, `:core`) ═════════════════════════════════════════════════════════
 *
 * R7 (SUPERSEDED) đổi task app thoát ô sang FREEFORM trên display 0 và ghi dấu bền `kachi_slot_escape` (`pkg|ô|task|l,t,r,b` nối
 * `;`, tệp theo xe `clusternav_state`) TRƯỚC lệnh — CLAUDE.md §5: state đổi ngoài hệ phải có đường trả chạy được cả khi tiến
 * trình đã chết. Bản R18 không còn quản freeform nữa ⇒ dấu nào còn thì trả task đó về TOÀN MÀN (mã 89 mode 1 toTop 0 —
 * [TaskBinderCodes]; [ĐO máy ảo 09/10] app nằm DƯỚI Kachi, cùng pid) rồi xoá dấu.
 *
 * Phạm vi lệnh (CLAUDE.md §4): chỉ task `standard` của ĐÚNG gói có dấu, ở display 0, đang `freeform` (task freeform ở màn cụm
 * là của đường chiếu cụm — không chạm). Không còn task freeform nào: gói đã gỡ ⇒ xoá dấu; còn cài ⇒ GIỮ dấu (task có thể quay
 * lại sau khởi động lại với chế độ Android nhớ theo app — lượt sau trả), 0 lệnh.
 */
object LegacyFreeformUndo {

    /** Khoá dấu R7 trên đĩa (đổi tên là mất dấu của máy đang chạy). */
    const val KEY = "kachi_slot_escape"

    const val MAIN_DISPLAY = 0

    data class Marker(val pkg: String, val raw: String)

    sealed interface Step {
        val marker: Marker

        /** Trả các task freeform [tasks] của gói về toàn màn, rồi xoá dấu nếu mọi lệnh OK. */
        data class Undo(override val marker: Marker, val tasks: List<Int>) : Step

        /** Gói đã gỡ ⇒ xoá dấu, 0 lệnh. */
        data class Forget(override val marker: Marker) : Step

        /** Chưa có task freeform nào để trả ⇒ giữ dấu, 0 lệnh. */
        data class Keep(override val marker: Marker) : Step
    }

    /** Dấu đọc từ đĩa — mục lạ bỏ qua, không ném; chỉ cần gói (task id đổi sau khởi động lại nên tìm theo gói). */
    fun decode(raw: String?): List<Marker> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { item ->
            val pkg = item.trim().substringBefore('|').trim()
            if (item.isBlank() || !pkg.matches(ShellAppLauncher.PKG)) null else Marker(pkg, item.trim())
        }.distinctBy { it.pkg }
    }

    fun encode(list: List<Marker>): String = list.joinToString(";") { it.raw }

    /** Đối chiếu dấu với bản đọc `am stack list` đã parse. Bản đọc rỗng (đọc hỏng) ⇒ không quyết gì (giữ hết). */
    fun plan(markers: List<Marker>, entries: List<StackEntry>, installed: (String) -> Boolean): List<Step> {
        if (entries.isEmpty()) return markers.map { Step.Keep(it) }
        return markers.map { m ->
            val freeform = entries.filter {
                it.displayId == MAIN_DISPLAY && it.pkg == m.pkg && it.isFreeform && it.isStandard && !it.isSystemStack
            }.map { it.taskId }.distinct()
            when {
                freeform.isNotEmpty() -> Step.Undo(m, freeform)
                !installed(m.pkg) -> Step.Forget(m)
                else -> Step.Keep(m)
            }
        }
    }
}
