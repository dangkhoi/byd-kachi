package com.byd.clusternav.launcher.testbridge

import android.content.Context

/**
 * ═══ T-BRIDGE · NHỮNG LỆNH **KHÔNG CẦN MÀN CHÍNH** ══════════════════════════════════════════════════════════
 *
 * Bảy lệnh chỉ chạm HAL / prefs / thẻ nhớ, không chạm view nào — nên chúng phải chạy được cả khi `KachiHomeActivity`
 * chưa lên. Đây là cùng một tính chất, và trước WP7 nó nằm rải trong `when` đầu của [KachiTestBridge.dispatch];
 * gom lại một chỗ vì đó **là** một khái niệm, không phải một tối ưu:
 *
 *  • một buổi RE trên xe hay bắt đầu bằng `force-stop` rồi đo ngay, và bắt lệnh chờ launcher lên mới trả lời là
 *    mất đúng những mục đo ngay sau khi khởi động lại;
 *  • `prefs` là ca cố ý đầu tiên của luật này (spec `kachi-test-bridge.html`: chạy được để chẩn đoán đúng ca
 *    *"launcher không lên"*), rồi `hal`/`sweep`/`featmap`/`voice_dump`/`prefs_set` đi theo, và WP7 thêm `captest`.
 *
 * ⚠ `prefs_set` nhận móc **nullable** (một khoá của nó phải đi qua màn chính — xem KDoc [TestBridgePrefsSet]), nên
 * nó vẫn thuộc đây: nó tự quyết định, không cần người gọi chặn trước.
 *
 * @return `true` nếu lệnh đã được xử lý xong (người gọi phải dừng); `false` = lệnh cần màn chính.
 */
internal object TestBridgeNoHome {

    fun handle(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply): Boolean {
        when (cmd.name) {
            TestBridgeCommands.PREFS ->
                reply.ok("file" to cmd.file, "values" to TestBridgeState.prefsSnapshot(app, cmd.file))
            TestBridgeCommands.HAL -> TestBridgeHal.run(app, cmd, reply)
            TestBridgeCommands.SWEEP -> TestBridgeSweep.run(app, cmd, reply)
            TestBridgeCommands.FEATMAP -> TestBridgeFeatMap.run(app, reply)
            TestBridgeCommands.VOICE_DUMP -> TestBridgeVoiceDump.run(app, cmd, reply)
            TestBridgeCommands.PREFS_SET -> TestBridgePrefsSet.run(app, cmd, KachiTestHooks.get(), reply)
            TestBridgeCommands.CAPTEST -> TestBridgeCapTest.run(app, cmd, reply)
            else -> return false
        }
        return true
    }
}
