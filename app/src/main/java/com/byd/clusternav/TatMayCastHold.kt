package com.byd.clusternav

import android.content.Context
import android.os.SystemClock
import com.byd.clusternav.modules.navaccess.TatMayCastHoldPlan
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ 2.96 R11 · TAT-MAY-CAST-HOLD — chỗ nối `:app` của [TatMayCastHoldPlan] (luật + lý do + số đo ở `:core`) ═══════════════
 *
 * Ba lời gọi, ba chỗ, không chỗ nào khác:
 *  • [arm] — `A11yLifecycleHeal.install` (luồng chính, `Application.onCreate`): chỉ ghi hai trường RAM, không I/O;
 *  • [release] — `finally` của lượt lớp 1 (`onProcessStart`) trên luồng nối tiếp của lượt chữa;
 *  • [holdMs] — `BubbleAutostart.open` trước `openProjection()` (luồng `Handler` chính): đọc RAM + MỘT `getLong` prefs (bộ nhớ).
 *
 * Phạm vi RAM đúng (CLAUDE.md §5): cờ chỉ HOÃN một việc của chính Kachi trong tiến trình này, không quyết đổi gì ngoài hệ
 * thống; phần "tiến trình sắp chết" đọc mốc BỀN `a11y_forcestop_elapsed` (ghi `commit()` trước lệnh force-stop).
 */
internal object TatMayCastHold {

    @Volatile private var armedAt = -1L
    private val pending = AtomicBoolean(false)

    /** Tiến trình bật lúc màn KHÔNG tương tác (`interactive != true`, cùng luật fail-closed của `nonInteractiveStartAt`). */
    fun arm(interactive: Boolean?, startedAt: Long) {
        if (interactive == true) return
        pending.set(true)
        armedAt = startedAt
    }

    /** Lượt lớp 1 đã kết luận (mọi nhánh — kể cả lỗi; nhánh bắn force-stop vẫn giữ nhờ mốc leo bền). */
    fun release() {
        pending.set(false)
    }

    fun verdict(app: Context): TatMayCastHoldPlan.Verdict =
        TatMayCastHoldPlan.verdict(armedAt, pending.get(), Prefs.a11yEscalatedAt(app), SystemClock.elapsedRealtime())

    /** `0` = mở chiếu ngay; `> 0` = hỏi lại sau chừng này ms. */
    fun holdMs(app: Context): Long =
        TatMayCastHoldPlan.holdMs(armedAt, pending.get(), Prefs.a11yEscalatedAt(app), SystemClock.elapsedRealtime())

    /** Ms đã giữ kể từ lúc tiến trình bật (chỉ để ghi nhật ký đo). */
    fun heldMs(): Long = armedAt.let { if (it < 0L) 0L else SystemClock.elapsedRealtime() - it }
}
