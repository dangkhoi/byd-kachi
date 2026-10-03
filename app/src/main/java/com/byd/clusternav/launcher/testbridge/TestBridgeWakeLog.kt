package com.byd.clusternav.launcher.testbridge

import android.content.Context
import com.byd.clusternav.launcher.voice.WakeSessionLog

/**
 * ═══ T-BRIDGE · `wakelog` — ĐỌC NHẬT KÝ PHIÊN NGHE CỦA `:wake` (FIX286 · VK6) ═══════════════════════════════════
 *
 * `am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd wakelog [--ei n 50]`
 *
 * Ranh giới ở KDoc [TestBridgeCommands.WAKELOG]. Ở đây chỉ là phần chạm Android: đọc tệp qua ĐÚNG
 * [WakeSessionLog.read] (cùng hai lớp khoá với các lượt ghi từ `:wake` — không mở tệp lần hai bằng tay).
 *
 * **CHỈ ĐỌC** — không một lời gọi ghi nào (cùng luật `ctllog`).
 */
internal object TestBridgeWakeLog {

    fun run(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        val all = WakeSessionLog.read(app)
        val tail = if (all.size <= cmd.tail) all else all.subList(all.size - cmd.tail, all.size)
        reply.ok(
            "n" to cmd.tail,
            "total" to all.size,
            "lines" to TestBridgeJson.Raw(TestBridgeJson.arr(tail)),
        )
    }
}
