package com.byd.clusternav.launcher.testbridge

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import com.byd.clusternav.modules.clustercast.DiagActivity
import com.byd.clusternav.vietmapwidget.VietMapWidgetDiagActivity

/**
 * ═══ 2.93 wave 2B · DIAG-SCREENS-UNREACHABLE — lệnh `diag_screen`: lối DUY NHẤT tới hai màn chẩn đoán ═══════════════════
 *
 * Bảng lệnh + danh sách trắng tên màn ở `:core` ([TestBridgeScreenCommands], chặn tên lạ ở tầng phân tích); tệp này chỉ
 * ánh xạ tên → lớp Activity rồi mở bằng chính `Context` của app (cùng tiến trình ⇒ `exported=false` không cản — khác
 * `am start` từ uid shell bị từ chối [ĐO xe 29/09]). Đứng SAU cổng *Chế độ kiểm thử* như mọi lệnh: [KachiTestBridge] hỏi
 * `TestBridgeStore.isOn` TRƯỚC khi phân tích ⇒ công tắc TẮT thì lệnh này không bao giờ tới đây.
 *
 * Không cần màn chính (đi [TestBridgeNoHome]): hai màn tự đứng được, và lúc cần chẩn đoán *"launcher không lên"* thì màn
 * chính có thể chưa có. Lời đáp nói `requested`, KHÔNG nói "đã mở": hệ thống có thể chặn mở Activity từ nền (luật BAL của
 * Android 10+) mà `startActivity` không ném — "đã lên màn chưa" là câu hỏi của `dumpsys activity top`, không của cầu này
 * (cùng lẽ lệnh `open`).
 */
internal object TestBridgeScreens {

    private const val ERR_START = "screen_start_failed"

    /** Tên màn ([TestBridgeScreenCommands.TARGETS]) → lớp Activity. Không có trong bảng ⇒ không mở gì. */
    private fun classOf(target: String): Class<*>? = when (target) {
        TestBridgeScreenCommands.DIAG -> DiagActivity::class.java
        TestBridgeScreenCommands.VIETMAP -> VietMapWidgetDiagActivity::class.java
        else -> null
    }

    fun run(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        val target = TestBridgeScreenCommands.targetOf(cmd.arg)
        val cls = target?.let(::classOf)
        if (cls == null) {
            // Không tới được: tầng phân tích đã chặn tên lạ. Giữ nhánh để lượt thêm tên mà quên nối lớp trả lỗi, không im lặng.
            reply.fail(TestBridgeScreenCommands.ERR_BAD_SCREEN + cmd.arg, "all" to TestBridgeJson.Raw(TestBridgeJson.arr(TestBridgeScreenCommands.TARGETS)))
            return
        }
        try {
            app.startActivity(Intent(app, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Log.i(KachiTestBridge.TAG, "diag_screen $target → ${cls.simpleName}")
            reply.ok("screen" to target, "activity" to cls.simpleName, "requested" to true)
        } catch (e: ActivityNotFoundException) {
            Log.w(KachiTestBridge.TAG, "diag_screen $target: không mở được ${cls.simpleName}", e)
            reply.fail(ERR_START, "screen" to target, "exception" to e.javaClass.simpleName)
        } catch (e: SecurityException) {
            Log.w(KachiTestBridge.TAG, "diag_screen $target: bị từ chối", e)
            reply.fail(ERR_START, "screen" to target, "exception" to e.javaClass.simpleName)
        }
    }
}
