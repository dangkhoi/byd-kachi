package com.byd.clusternav.launcher.testbridge

import android.content.Context
import com.byd.clusternav.launcher.CtlJournalStore

/**
 * ═══ T-BRIDGE · `ctllog` — ĐỌC NHẬT KÝ MỖI LỆNH GHI XE (FIX286 · SR6) ═══════════════════════════════════════════
 *
 * `am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd ctllog [--ei n 50]`
 *
 * Ranh giới ở KDoc [TestBridgeCommands.CTLLOG]. Ở đây chỉ là phần chạm Android: đọc tệp qua ĐÚNG
 * [CtlJournalStore.read] (cùng hai lớp khoá với các lượt ghi — không mở tệp lần hai bằng tay).
 *
 * **CHỈ ĐỌC** — không một lời gọi ghi nào; đọc nhật ký mà làm đổi chính nhật ký là đo thứ mình vừa sửa.
 */
internal object TestBridgeCtlLog {

    fun run(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        val all = CtlJournalStore.read(app)
        val tail = if (all.size <= cmd.tail) all else all.subList(all.size - cmd.tail, all.size)
        reply.ok(
            "n" to cmd.tail,
            "total" to all.size,
            "lines" to TestBridgeJson.Raw(TestBridgeJson.arr(tail)),
        )
    }
}
