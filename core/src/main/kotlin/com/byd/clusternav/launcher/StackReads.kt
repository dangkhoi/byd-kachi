package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.StackEntry
import com.byd.clusternav.modules.clustercast.StackParse

/**
 * ═══ Đọc `am stack list` + ĐỌC LẠI sau lệnh gỡ — một bản cho mọi lượt gỡ stack (thuần, chặn) ═══════════════════════
 *
 * Tách từ `FloatingOrphanSweep` (PROFILE-SWITCH-SLOTS R-B3) khi L6 (*tắt* app trong ô, [SlotCloseRun]) cần đúng phép đó:
 * hai bản sao của một vòng đọc-lại có ngủ là hai chỗ để lệch (global §4.1 DRY). Hành vi giữ nguyên byte — chuỗi lệnh của
 * `FloatingOrphanSweepTest` khoá.
 *
 * ## Vì sao phải đọc lại vài lần — [ĐO máy ảo 2026-10-01 E2E ca 3b] + [ĐO AOSP r47]
 * Lệnh gỡ trả về TRƯỚC khi task rời stack: `removeTaskByIdLocked` → `TaskRecord.removeTaskActivitiesLocked`
 * (`ActivityStackSupervisor.java:1789-1800`) chỉ YÊU CẦU từng activity kết thúc; task chỉ rời stack khi activity cuối được
 * gỡ thật — sau lượt pause/destroy bất đồng bộ. Xem KDoc `FloatingOrphanSweep.settle` cho số đo.
 *
 * ⚠ CHẶN — chỉ gọi trên luồng nền.
 */
internal object StackReads {

    /** Một bản đọc: [entries] `null` = đọc hỏng (ném, hoặc parse rỗng — máy thật luôn có stack home), kèm [error]. */
    class Read(val entries: List<StackEntry>?, val error: String?)

    /** Bản đọc CUỐI của [settle] + số lần đọc. */
    class Settled(val read: Read, val reads: Int)

    /** Đọc + parse; ném lỗi hoặc parse rỗng ⇒ `entries = null` = đọc hỏng (không làm gì theo nó). */
    fun read(sh: (String) -> String): Read = try {
        val parsed = StackParse.parse(sh(FloatingOrphanPlan.LIST_CMD))
        if (parsed.isEmpty()) Read(null, "rỗng") else Read(parsed, null)
    } catch (e: Exception) {
        // Bắt rộng CÓ CHỦ Ý: kênh dadb có thể ném IOException/IllegalStateException/… (lambda Kotlin không khai ngoại
        // lệ). Chạy trên luồng nền của màn nhà: để lọt ra là sập HOME trên xe đang chạy. Đọc hỏng = không làm gì.
        Read(null, e.javaClass.simpleName)
    }

    /**
     * Đọc lại tới khi [done] (hoặc tới [maxReads] lần, cách nhau [stepMs]); trả bản đọc CUỐI + số lần đọc. Đọc hỏng ⇒ trả
     * ngay bản hỏng. Bị ngắt (màn huỷ ⇒ `shutdownNow`) ⇒ trả bản đọc gần nhất, giữ cờ ngắt, KHÔNG ném ra luồng nền.
     */
    fun settle(
        sh: (String) -> String,
        sleep: (Long) -> Unit,
        maxReads: Int,
        stepMs: Long,
        done: (List<StackEntry>) -> Boolean,
    ): Settled = settle(sh, sleep, LongArray(maxOf(0, maxReads - 1)) { stepMs }, done)

    /**
     * Cùng vòng đọc lại, nhịp nghỉ theo LỊCH [steps] (A3 · SLOT-CLOSE-SETTLE 2.89 — [SlotCloseSettle]): đọc 1 lần, rồi trước
     * lần đọc thứ `k + 2` nghỉ `steps[k]` ⇒ tối đa `1 + steps.size` lần đọc. Bản nhịp đều ở trên là ca đặc biệt (giữ byte
     * mọi chỗ gọi cũ — `FloatingOrphanSweepTest` khoá chuỗi lệnh + giấc ngủ). Luật đọc hỏng / bị ngắt y như trên.
     */
    fun settle(
        sh: (String) -> String,
        sleep: (Long) -> Unit,
        steps: LongArray,
        done: (List<StackEntry>) -> Boolean,
    ): Settled {
        var last = read(sh)
        var reads = 1
        while (true) {
            val e = last.entries ?: return Settled(last, reads)
            if (reads > steps.size || done(e)) return Settled(last, reads)
            try {
                sleep(steps[reads - 1])
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                return Settled(last, reads)
            }
            last = read(sh)
            reads++
        }
    }
}
