package com.byd.clusternav.launcher.behind

import java.util.concurrent.CopyOnWriteArraySet

/**
 * ═══ Công tắc TẮT MỘT CHIỀU của cả tiến trình + báo người nghe (thuần, `:core`) ═════════════════════════════════════════
 *
 * Dùng cho BEHIND-HOME (`BehindHomeRunner.disabledReason`): một PHÉP ĐO (`ANCHOR_IN_FRONT` — giữ chỗ bị ROM đưa lên trước
 * màn nhà; giữ chỗ bị chạy thật) tắt tính năng tới khi tiến trình chết. Cờ RAM này chỉ làm Kachi BỚT việc (CLAUDE.md §5).
 *
 * Soát vòng 2 [P3]: trước đây chỉ là một `@Volatile var` — chuỗi của chuyến lên xe / lối tắt *Chạy ngầm* / đặt tạm tắt nó
 * thì không ai báo cho đầu ô ⇒ ở chế độ luôn hiện (công tắc tắt · TalkBack) nút *chạy nền* cũ còn đó, chạm vào im lặng không
 * làm gì. Nay mọi lối tắt đi qua [off] và người nghe ([listen]) được báo ĐÚNG MỘT lần, ở luồng của bên tắt (người nghe tự
 * chuyển về luồng của mình). Đã tắt ⇒ giữ lý do ĐẦU, không báo lại.
 */
class ProcessOffSwitch {

    /** Lý do tắt (`null` = còn bật). Chỉ [off] ghi. */
    @Volatile var reason: String? = null
        private set

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun listen(l: () -> Unit) { listeners.add(l) }

    fun unlisten(l: () -> Unit) { listeners.remove(l) }

    /** Tắt với [why]. `true` = lượt NÀY vừa tắt (người nghe đã được báo); đã tắt từ trước ⇒ `false`, không báo lại. */
    fun off(why: String): Boolean {
        synchronized(this) {
            if (reason != null) return false
            reason = why
        }
        listeners.forEach { it() }   // ngoài khoá: người nghe chỉ `post`, không bao giờ chờ bên tắt
        return true
    }
}
