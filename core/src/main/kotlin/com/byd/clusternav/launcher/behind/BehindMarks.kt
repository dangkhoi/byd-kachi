package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.ShellAppLauncher
import com.byd.clusternav.modules.clustercast.StackEntry

/**
 * ═══ BEHIND-HOME — DẤU BỀN "task này do Kachi đẩy ra sau màn nhà" (thuần, `:core`) ═════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` §9 (Nhật ký 02/10). App đẩy ra sau màn nhà là trạng thái
 * NGOÀI tiến trình — nó sống qua lần BYD giết Kachi. [ĐO máy ảo 02/10, `impl-probe/e6`] giết Kachi (`kill -9`) khi
 * VietMap đang nằm sau màn nhà ⇒ `am_finish_activity … KachiHome, proc died without state saved` ⇒ hệ RESUME VietMap
 * (`am_set_resumed_activity … resumeTopActivityInnerLocked`), stack home rỗng, 12 s sau VietMap vẫn che toàn màn — trái
 * owner *"không đè lên home"*. CLAUDE.md §5: đổi ra ngoài thì ghi dấu TRƯỚC khi đổi, dọn lúc khởi động.
 *
 * Dấu = `taskId:gói` (khoá `kachi_behind_marks`, tệp theo xe `clusternav_state`, ghi `commit()` TRƯỚC `move-task`).
 * Lúc thức (chuỗi SẴN), bản đọc thấy stack ĐỈNH display 0 đang hiện mà chỉ chứa task có dấu ([surfaced]) ⇒ đưa HOME lên
 * qua rào camera (K12). Dấu của task không còn trên display 0 bị tỉa ([prune]).
 */
object BehindMarks {

    /** Trần số dấu — mỗi lượt đặt tạm thêm tối đa một; quá trần ⇒ bỏ dấu cũ nhất (task cũ nhất ít khả năng còn sống). */
    const val MAX = 16

    fun encode(marks: Map<Int, String>): String =
        marks.entries.toList().takeLast(MAX).joinToString(",") { "${it.key}:${it.value}" }

    /** Chuỗi lạ ⇒ bỏ mục (không ném): đến từ đĩa. Thứ tự giữ nguyên (cũ → mới). */
    fun decode(raw: String?): Map<Int, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<Int, String>()
        for (item in raw.split(',')) {
            val cut = item.indexOf(':')
            if (cut <= 0) continue
            val id = item.substring(0, cut).trim().toIntOrNull() ?: continue
            val pkg = item.substring(cut + 1).trim()
            if (id > 0 && pkg.matches(ShellAppLauncher.PKG)) out[id] = pkg
        }
        return out
    }

    fun add(marks: Map<Int, String>, taskId: Int, pkg: String): Map<Int, String> =
        LinkedHashMap(marks).apply { remove(taskId); put(taskId, pkg) }.entries.toList().takeLast(MAX).associate { it.toPair() }

    /**
     * Một app Kachi đẩy ra sau màn nhà đã NỔI LÊN che màn nhà: stack ĐANG HIỆN trên cùng của display 0 có loại
     * `standard` đọc bằng chữ và MỌI task của nó có dấu (đúng id + đúng gói). Stack home đang hiện ở trên ⇒ `false`.
     * Bản đọc rỗng ⇒ `false` (không biết thì không đổi gì).
     *
     * "Đang hiện trên cùng", KHÔNG phải "đầu danh sách": [ĐO máy ảo 02/10, fixture `tm1-killed-surfaced`] sau khi Kachi
     * chết, hai stack rỗng (task `KachiHomeActivity` không còn activity, `visible=false`) nằm TRÊN stack của VietMap
     * trong thứ tự z và stack home rỗng tụt xuống đáy — lấy stack đầu danh sách là nhìn nhầm vào stack rỗng. Cửa sổ PIP
     * (luôn trên cùng) cũng không tính ([BehindHomePlan.topVisibleStackId]): app có dấu nổi lên ngay dưới PIP vẫn là che
     * màn nhà.
     */
    fun surfaced(entries: List<StackEntry>, marks: Map<Int, String>): Boolean {
        if (marks.isEmpty()) return false
        val top = BehindHomePlan.topVisibleStackId(entries, BehindHomePlan.MAIN_DISPLAY) ?: return false
        val tasks = entries.filter { it.stackId == top }
        return tasks.all { it.activityType == BehindHomePlan.STANDARD && marks[it.taskId] == it.pkg }
    }

    /** Giữ dấu của task còn trên display 0 (đúng gói) — phần còn lại đã chết / về ô / đổi chỗ. Bản đọc rỗng ⇒ giữ hết. */
    fun prune(entries: List<StackEntry>, marks: Map<Int, String>): Map<Int, String> {
        if (entries.isEmpty()) return marks
        val alive = entries.filter { it.displayId == BehindHomePlan.MAIN_DISPLAY }.associate { it.taskId to it.pkg }
        return marks.filter { (id, pkg) -> alive[id] == pkg }
    }
}
