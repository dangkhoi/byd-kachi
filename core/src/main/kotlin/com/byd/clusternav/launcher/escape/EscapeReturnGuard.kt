package com.byd.clusternav.launcher.escape

/**
 * ═══ 2.98 · R18 (SLOT-ESCAPE-VD-RETURN) — QUYẾT ĐỊNH có dời stack app vừa thoát ô về lại màn ảo ô không (thuần, `:core`) ═══
 *
 * Gốc rễ [ĐO nguồn `android-10.0.0_r47`]: app tự mở activity thứ hai vào CÙNG task ⇒ cổng `canBeLaunchedOnDisplay` xét uid của
 * APP (`ActivityRecord.java:1422-1425` → `ActivityStackSupervisor.java:1067,1098,1102`) ⇒ bị từ chối trên màn ảo của Kachi ⇒
 * `ActivityStarter.setTaskFromSourceRecord` (`:2352-2397`) dời CẢ task sang display 0, rồi `handleNonResizableTaskIfNeeded`
 * báo `onActivityLaunchOnSecondaryDisplayFailed(task, màn yêu cầu)` (`ActivityStackSupervisor.java:2436-2439`) cho mọi
 * `ITaskStackListener`. Daemon nghe đúng tín hiệu đó và dời stack về bằng `moveStackToDisplay` (`RootActivityContainer.java:937`,
 * không qua cổng nhúng). Lớp này chỉ QUYẾT; cơ chế ở `EscapeReturnDaemon` (`:app`, uid 2000).
 *
 * ## Bốn câu CLAUDE.md §4
 *  1. **Display nào** — đúng [Escape.requestedDisplay] của sự kiện VÀ là một màn ảo ô trong bảng Kachi gửi; không quét gì.
 *  2. **App nào** — gói gốc của task = gói ô đó đang hiện (allow-list). Task gói khác (Gmail soạn thư `NEW_TASK` từ Waze, hộp
 *     miễn pin của Settings) không chạm.
 *  3. **Loại stack nào** — stack chứa task đó: display 0, `fullscreen`, `standard`, đúng MỘT task (chính task đó). Màn đích phải
 *     `fullscreen`: `AppWindowToken.shouldStartChangeTransition` (`:1711-1721`) chỉ dựng chuyển tiếp + `createTaskSnapshot` (đường
 *     NPE 08-01, `docs/archive/diagnostics/carplay-move-stack-npe-crash-2026-08-01.md`) khi đổi VÀO/RA freeform ⇒ fullscreen →
 *     fullscreen không đi đường đó [SUY nguồn; 🚗 phải đo]. home/recents/pinned/freeform/split không bao giờ.
 *  4. **Hoàn tác** — lệnh không ghi gì bền; hỏng ⇒ task ở lại display 0 như 2.93; cầu chì [EscapeReturnBreaker] ngắt.
 */
object EscapeReturnGuard {

    const val MAIN_DISPLAY = 0

    /** `WindowConfiguration.WINDOWING_MODE_FULLSCREEN` (`WindowConfiguration.java:85`). */
    const val WINDOWING_MODE_FULLSCREEN = 1

    /** `WindowConfiguration.ACTIVITY_TYPE_STANDARD` (`WindowConfiguration.java:129`). */
    const val ACTIVITY_TYPE_STANDARD = 1

    /** Một sự kiện "task [taskId] (gói gốc [pkg]) muốn lên màn [requestedDisplay] mà không được". */
    data class Escape(val taskId: Int, val pkg: String?, val requestedDisplay: Int)

    /** Sự thật về một stack lúc đọc (`ActivityManager.StackInfo`, đọc trong daemon). */
    data class StackFact(val stackId: Int, val displayId: Int, val windowingMode: Int, val activityType: Int, val taskIds: List<Int>)

    enum class Why {
        DISABLED, NOT_SLOT_VD, NOT_SLOT_PKG, TRIPPED_ALL, TRIPPED_PKG, RATE,
        NO_STACK, NOT_MAIN_DISPLAY, NOT_FULLSCREEN, NOT_STANDARD, MULTI_TASK, TARGET_NOT_FULLSCREEN,
    }

    sealed interface Decision {
        data class Move(val stackId: Int, val vd: Int) : Decision
        data class Skip(val why: Why) : Decision
    }

    /**
     * Phần KHÔNG cần đọc hệ (rẻ, chạy trước mọi lời gọi binder): bảng bật · màn là màn ảo ô · gói khớp · cầu chì. `null` = qua.
     * [breaker] được hỏi (không ghi) — ghi lượt dời chỉ khi đã dời ([EscapeReturnBreaker.onMoved]).
     */
    fun precheck(e: Escape, config: EscapeReturnConfig, breaker: EscapeReturnBreaker, nowMs: Long): Why? {
        if (!config.enabled) return Why.DISABLED
        val want = config.slots[e.requestedDisplay] ?: return Why.NOT_SLOT_VD
        if (e.pkg == null || e.pkg != want) return Why.NOT_SLOT_PKG
        return breaker.blocks(e.pkg, nowMs)
    }

    /** Phần đọc hệ: [stacks] = mọi stack lúc này · [targetMode] = chế độ cửa sổ của màn đích (`IWindowManager.getWindowingMode`). */
    fun decide(e: Escape, stacks: List<StackFact>, targetMode: Int): Decision {
        val s = stacks.firstOrNull { e.taskId in it.taskIds } ?: return Decision.Skip(Why.NO_STACK)
        return when {
            s.displayId != MAIN_DISPLAY -> Decision.Skip(Why.NOT_MAIN_DISPLAY)
            s.windowingMode != WINDOWING_MODE_FULLSCREEN -> Decision.Skip(Why.NOT_FULLSCREEN)
            s.activityType != ACTIVITY_TYPE_STANDARD -> Decision.Skip(Why.NOT_STANDARD)
            s.taskIds.size != 1 -> Decision.Skip(Why.MULTI_TASK)
            targetMode != WINDOWING_MODE_FULLSCREEN -> Decision.Skip(Why.TARGET_NOT_FULLSCREEN)
            else -> Decision.Move(s.stackId, e.requestedDisplay)
        }
    }

    /** Kết quả đọc lại sau lệnh dời. */
    enum class After { IN_SLOT, ELSEWHERE, LOST }

    /**
     * Đọc lại [stacks] sau lệnh: task ở [vd] ⇒ xong; còn ở display khác ⇒ lệnh không ăn; KHÔNG còn ở đâu ⇒ đúng hậu quả vụ NPE
     * 08-01 ("task biến mất khỏi `am stack list` hoàn toàn") ⇒ cầu chì bền. Bản đọc rỗng (đọc hỏng) ⇒ [ELSEWHERE] (không kết luận mất).
     */
    fun verify(taskId: Int, vd: Int, stacks: List<StackFact>): After {
        if (stacks.isEmpty()) return After.ELSEWHERE
        val s = stacks.firstOrNull { taskId in it.taskIds } ?: return After.LOST
        return if (s.displayId == vd) After.IN_SLOT else After.ELSEWHERE
    }
}
