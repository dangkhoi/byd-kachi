package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.util.Log
import com.byd.clusternav.launcher.NowPlayingControl

/**
 * ═══ 3.1.11 · CLUSTER-NOW-PLAYING — lệnh `np` (cú pháp ở `:core` [TestBridgeNowPlayingCommands]) ═══════════════════════════
 *
 * Đi ĐÚNG đường của công tắc Cài đặt ([NowPlayingControl] → `ClusterNowPlaying.setEnabled`) — không đường thứ hai. `on` dẫn tới
 * lượt GHI HAL cụm ⇒ từ chối nếu thiếu `--ez auto_confirm true`, và để dấu `AUTO-CONFIRM` (cùng cổng `hal set`). `status` chỉ đọc.
 * Lời đáp: `enabled` · `kind` · `detail` (rc, không tên bài) · `at_ms`. Dòng `np …` của từng lượt ghi đọc bằng lệnh `ctllog`.
 */
internal object TestBridgeNowPlaying {

    fun run(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        when (TestBridgeNowPlayingCommands.opOf(cmd.op)) {
            TestBridgeNowPlayingCommands.OP_ON -> {
                if (!cmd.autoConfirm) {
                    reply.fail(TestBridgeHal.ERR_NEEDS_CONFIRM, "note" to "them --ez auto_confirm true de bat ghi ten bai len cum")
                    return
                }
                Log.i(TestBridgeReply.TAG, "AUTO-CONFIRM: np on (ghi ten bai len cum)")
                NowPlayingControl.setEnabled(app, true)
            }
            TestBridgeNowPlayingCommands.OP_OFF -> NowPlayingControl.setEnabled(app, false)
            else -> Unit
        }
        reply.ok(listOf("enabled" to NowPlayingControl.enabled(app)) + NowPlayingControl.statusFields(app))
    }
}
