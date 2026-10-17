package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.util.Log
import com.byd.clusternav.launcher.HudRoadNameControl

/**
 * ═══ 3.1.12 · HUD-ROADNAME-ALT-PATH — lệnh `hudroad` (cú pháp ở `:core` [TestBridgeHudRoadCommands]) ══════════════════════
 *
 * Đi ĐÚNG đường của hàng Cài đặt ([HudRoadNameControl] → `HudRoadNameTransport.setMode`) — không đường thứ hai. `b`/`c` dẫn
 * tới lượt GỬI bản ghi vào TestDevice qua ClusterDebug ⇒ từ chối nếu thiếu `--ez auto_confirm true`, và để dấu `AUTO-CONFIRM`
 * (cùng cổng `hal set`). Xe không có ClusterDebug dùng được ⇒ `unavailable` + lý do, không lưu. `status` chỉ đọc.
 * Lời đáp: `mode` · `availability` · kế hoạch · lần gửi cuối (độ dài + rc, KHÔNG tên đường). Dòng `hudroad …` đọc bằng `ctllog`.
 */
internal object TestBridgeHudRoad {

    const val ERR_UNAVAILABLE = "unavailable"

    fun run(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        val mode = TestBridgeHudRoadCommands.modeOf(cmd.op)
        if (mode != null) {
            if (TestBridgeHudRoadCommands.needsConfirm(cmd.op) && !cmd.autoConfirm) {
                reply.fail(TestBridgeHal.ERR_NEEDS_CONFIRM, "note" to "them --ez auto_confirm true de gui ten duong qua ClusterDebug")
                return
            }
            if (mode.clusterDebug) Log.i(TestBridgeReply.TAG, "AUTO-CONFIRM: hudroad ${mode.code} (ten duong qua ClusterDebug)")
            val refused = HudRoadNameControl.setMode(app, mode)
            if (refused != null) {
                reply.fail(ERR_UNAVAILABLE, "availability" to refused.name)
                return
            }
        }
        reply.ok(HudRoadNameControl.statusFields(app))
    }
}
