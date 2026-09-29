package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.os.Process
import android.os.SystemClock
import com.byd.clusternav.Prefs
import com.byd.clusternav.a11yMoXeAt
import com.byd.clusternav.a11yTatMayAt
import com.byd.clusternav.modules.navaccess.A11yBindJournal
import com.byd.clusternav.modules.navaccess.A11yBindJournalStore
import com.byd.clusternav.modules.navaccess.AccessibilityHealGates

/**
 * ═══ T-BRIDGE · `a11ylog` — ĐỌC NHẬT KÝ GẮN DỊCH VỤ HỖ TRỢ TRÊN BẢN PHÁT HÀNH (2.83) ═══════════════════════════
 *
 * `am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd a11ylog [--ei n 50]`
 *
 * Lý do tồn tại + ranh giới ở KDoc [TestBridgeCommands.A11YLOG]. Ở đây chỉ là phần chạm Android: đọc tệp qua ĐÚNG
 * [A11yBindJournalStore.read] (cùng khoá với hai luồng ghi — không mở tệp lần thứ hai bằng tay) và đọc hai mốc qua
 * ĐÚNG getter của [Prefs] mà thang chữa dùng (không chép tên khoá prefs sang đây).
 *
 * **CHỈ ĐỌC** — không một lời gọi ghi nào (`record`, `Prefs.set…`, `edit()`); bài canh
 * `A11yLogBridgeWiringContractTest` khoá điều đó. Đọc nhật ký mà làm đổi chính nhật ký/mốc là đo thứ mình vừa sửa.
 *
 * Kèm hai đồng hồ **lúc đọc**: `a11y_forcestop_elapsed` là một mốc `elapsedRealtime` và `a11y_deep_sleep_ms` là
 * lượng ngủ tích luỹ ở nhịp watchdog trước — thiếu "bây giờ" thì không ai biết hai số ấy cách hiện tại bao xa.
 * `escalated_this_boot` dựng bằng CHÍNH [AccessibilityHealGates.escalatedThisBoot] mà cổng chữa hỏi, không suy lại.
 */
internal object TestBridgeA11yLog {

    fun run(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        val all = A11yBindJournalStore.read(app)
        val tail = A11yBindJournal.trim(all, cmd.tail)
        val escalatedAt = Prefs.a11yEscalatedAt(app)
        val elapsedNow = SystemClock.elapsedRealtime()
        val uptimeNow = SystemClock.uptimeMillis()
        reply.ok(
            "n" to cmd.tail,
            "total" to all.size,
            "lines" to TestBridgeJson.Raw(TestBridgeJson.arr(tail)),
            "a11y_forcestop_elapsed" to escalatedAt,
            "a11y_deep_sleep_ms" to Prefs.lastDeepSleepMs(app),
            "escalated_this_boot" to AccessibilityHealGates.escalatedThisBoot(escalatedAt, elapsedNow),
            // 2.83 lớp 1/2 — hai claim "một lượt mỗi sự kiện" (`PrefsA11yLifecycle`): đọc được thì chốt được trên xe
            // lượt tắt-máy / mở-xe có chạy và có tự lặp không, không cần bản debug.
            "a11y_tat_may_elapsed" to Prefs.a11yTatMayAt(app),
            "a11y_mo_xe_elapsed" to Prefs.a11yMoXeAt(app),
            "elapsed_now_ms" to elapsedNow,
            "deep_sleep_now_ms" to A11yBindJournal.deepSleepMs(elapsedNow, uptimeNow),
            // R-C1 (spec 2.83) — tiến trình ĐANG TRẢ LỜI sinh lúc nào: so với `a11y_tat_may_elapsed` (claim ghi ở
            // `Application.onCreate`, vài trăm ms sau mốc này) là biết ngay tiến trình này có phải cái được HOME dựng
            // lại lúc tắt máy hay không, và `pid` khớp cột `pid=` của các dòng nhật ký. Chỉ đọc (API 24, minSdk 29;
            // [ĐO] `javap` android.jar compileSdk 37: có, không `@Deprecated`).
            "pid" to Process.myPid(),
            "proc_start_elapsed_ms" to Process.getStartElapsedRealtime(),
        )
    }
}
