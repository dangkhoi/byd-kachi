package com.byd.clusternav.launcher.behind

import com.byd.clusternav.modules.clustercast.StackEntry

/**
 * Kết quả chờ X lên đỉnh màn ảo dàn dựng ([StageWait.top]): [ms] đã chờ; [fell] = X tự lên display 0 TRƯỚC màn nhà trong lúc
 * chờ; [seen] = chính bản đọc ĐỌC ĐƯỢC đã thấy điều đó (2.93 · BEHIND-FELL-UNREAD-K12 — `BehindHomeSequence.afterStage` dùng nó
 * khi bản đọc lại kế tiếp hỏng); [timedOut] = hết trần mà chưa lên đỉnh. Tách khỏi `BehindHomeSequence.kt` theo trần 500 dòng
 * (CLAUDE.md §4.1) — phép chờ giữ nguyên byte, chỉ thêm [seen].
 */
internal data class StageWaited(val ms: Long, val fell: Boolean, val seen: List<StackEntry>? = null) {
    val timedOut: Boolean get() = !fell && ms >= BehindHomeSequence.X_TOP_WAIT_MS
}

internal object StageWait {

    /**
     * Chờ [pkg] lên đỉnh màn ảo [vd] tối đa [BehindHomeSequence.X_TOP_WAIT_MS] (mỗi lượt [read] — bản đọc hỏng là rỗng). L4: X TỰ
     * lên display 0 trước màn nhà trong lúc chờ ([BehindHomePlan.fellFront] — Waze `launchToSide` [ĐO `e2e-L4 · m1-stale-task-k4` (bằng chứng phiên, ngoài repo)],
     * trung chuyển VIEW [ĐO `e2e-L4 · m5a` (bằng chứng phiên, ngoài repo), T-M3]) ⇒ thôi chờ ngay ([StageWaited.fell]): chờ tiếp 4 s là 4 s màn nhà bị che.
     */
    fun top(read: () -> List<StackEntry>, sleep: (Long) -> Unit, vd: Int, pkg: String, homeWasTop: Boolean): StageWaited {
        var waited = 0L
        while (waited < BehindHomeSequence.X_TOP_WAIT_MS) {
            val r = read()
            if (BehindHomePlan.topIs(r, vd, pkg)) return StageWaited(waited, fell = false)
            if (homeWasTop && BehindHomePlan.fellFront(r, pkg)) return StageWaited(waited, fell = true, seen = r)
            sleep(BehindHomeSequence.X_TOP_STEP_MS); waited += BehindHomeSequence.X_TOP_STEP_MS
        }
        return StageWaited(waited, fell = false)
    }
}
