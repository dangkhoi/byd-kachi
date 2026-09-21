package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.util.Log
import com.byd.clusternav.launcher.CapTestStore
import com.byd.clusternav.launcher.CapTestSummary
import com.byd.clusternav.launcher.CapTestVerdict
import com.byd.clusternav.launcher.CapabilityTestPlan
import com.byd.clusternav.launcher.KachiLog
import com.byd.clusternav.launcher.testbridge.TestBridgeCommands.CapTestOps

/**
 * ═══ T-BRIDGE · LỆNH `captest` — CÔNG CỤ SOÁT TỪNG NÚT XE, ĐƯỜNG CỦA MÁY ═════════════════════════════════════
 *
 * UX-OVERHAUL · WP7. Bảng bấm tay (`CapTestConsole`) từ nay đứng sau cổng `DevMode`; lệnh này là đường **script**
 * đi hết 25 mục RE (`docs/diagnostics/oncar-re-tasks-can-2026-09-20.md`) mà không cần ai chạm màn:
 *
 * ```
 * captest --es op list                                  # mã + loại + đã-map-chưa (script biết phải sweep gì)
 * hal/ctl …                                             # chạy thật (đường CŨ, không nhân bản ở đây)
 * captest --es op ok     --es id defrost                # đóng dấu sau khi NHÌN xe
 * captest --es op notok  --es id lock --es text "cua khong keu"
 * captest --es op report                                # xuất báo cáo (cùng CapTestReport với nút Xuất)
 * captest --es op clear                                 # bắt đầu lượt soát mới
 * ```
 *
 * ## Vì sao KHÔNG chạy trên luồng nền như `sweep`
 * Cả sáu op chỉ chạm **prefs của chính Kachi** (`kachi_captest`) + dựng chuỗi ở `:core` — không một lời gọi HAL
 * nào (đó là việc của `hal`/`ctl`/`sweep`). `CapTestStore.record` đã tự đẩy lượt ghi báo cáo ra thẻ sang luồng
 * nền của nó. Dựng thêm một `Thread` ở đây chỉ làm lời đáp về sau khi lượt chạy đã kết thúc.
 *
 * ## `list` trả gì, và vì sao đúng những trường đó
 * `id` (script dùng làm khoá) · `kind` INFO/ACTION (biết nên `sweep` hay `ctl`) · `routed` (đã map HAL chưa —
 * chưa map thì `ctl` sẽ câm, và đó là một *kết quả*, không phải một lỗi) · `arg` (mặt "làm việc" của nút, đúng số
 * mà bảng bấm tay gửi) · `verdict` (đã chấm gì). **Không** trả nhãn/diễn giải: chúng song ngữ theo ngôn ngữ đang
 * chọn nên không phải khoá ổn định cho script — ai cần chữ thì đọc `op report`.
 */
internal object TestBridgeCapTest {

    fun run(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        val store = CapTestStore(app)
        when (cmd.op) {
            CapTestOps.LIST -> list(store, reply)
            CapTestOps.OK -> mark(store, cmd, CapTestVerdict.OK, reply)
            CapTestOps.NOT_OK -> mark(store, cmd, CapTestVerdict.NOT_OK, reply)
            CapTestOps.SKIP -> mark(store, cmd, CapTestVerdict.UNTESTED, reply)
            CapTestOps.REPORT -> report(app, store, reply)
            CapTestOps.CLEAR -> {
                store.clear()
                reply.ok("op" to CapTestOps.CLEAR, "total" to CapabilityTestPlan.total())
            }
            // Không có nhánh `else` thật sự đạt tới được: [TestBridgeCommands.parse] đã chặn op lạ bằng
            // `ERR_BAD_OP`. Vẫn trả lời thay vì `error(...)`: một op mới thêm ở `:core` mà quên nối ở đây thì
            // script phải đọc được lý do, không phải thấy tiến trình launcher chết.
            else -> reply.fail(TestBridgeCommands.ERR_BAD_OP + cmd.op)
        }
    }

    private fun list(store: CapTestStore, reply: TestBridgeReply) {
        val results = store.load()
        val items = CapabilityTestPlan.items()
        val rows = items.map { item ->
            TestBridgeJson.Raw(
                TestBridgeJson.obj(
                    "id" to item.id,
                    "kind" to item.kind.name,
                    "domain" to item.domain.name,
                    "routed" to item.isRouted,
                    "arg" to item.runArg,
                    "confirm" to item.needsConfirm,
                    "verdict" to (results[item.id]?.verdict?.name ?: CapTestVerdict.UNTESTED.name),
                ),
            )
        }
        reply.ok(summary(items, results) + listOf("items" to TestBridgeJson.Raw(TestBridgeJson.arr(rows))))
    }

    private fun mark(
        store: CapTestStore,
        cmd: TestBridgeCommand,
        verdict: CapTestVerdict,
        reply: TestBridgeReply,
    ) {
        // Mã phải CÓ THẬT trong bảng soát: chấm một mã gõ sai sẽ nằm im trong nhật ký và lượt `report` sau đó đếm
        // thiếu một mục mà không ai biết vì sao. Danh sách mã hợp lệ đi kèm câu lỗi (cùng luật `runCtl`).
        val items = CapabilityTestPlan.items()
        if (items.none { it.id == cmd.id }) {
            reply.fail(ERR_UNKNOWN_ID, "id" to cmd.id, "hint" to "captest --es op list")
            return
        }
        store.record(cmd.id, verdict, cmd.text.trim())
        val results = store.load()
        reply.ok(summary(items, results) + listOf("id" to cmd.id, "verdict" to verdict.name))
    }

    private fun report(app: Context, store: CapTestStore, reply: TestBridgeReply) {
        val text = store.exportReport()
        // `saveCaptestReport` đã tự nuốt lỗi và trả `null` khi vắng thẻ — bọc thêm `runCatching` ở đây là bắt
        // một ngoại lệ không bao giờ tới, còn `null` thì vẫn phải xử lý. Nói thẳng chỗ ghi hỏng vào nhật ký.
        val file = KachiLog.saveCaptestReport(app, text)
        if (file == null) Log.w(TestBridgeReply.TAG, "captest: khong ghi duoc bao cao ra the")
        val items = CapabilityTestPlan.items()
        reply.ok(
            summary(items, store.load()) + listOf(
                "file" to (file?.absolutePath ?: ""),
                "pull" to KachiLog.pullCommand(app),
                // Chữ báo cáo ĐI KÈM lời đáp: `TestBridgeReply` tự cắt bản logcat nhưng vẫn ghi đủ vào tệp JSON,
                // nên một buổi RE không có thẻ nhớ vẫn lấy được báo cáo qua `am broadcast`.
                "report" to text,
            ),
        )
    }

    private fun summary(
        items: List<com.byd.clusternav.launcher.CapTestItem>,
        results: Map<String, com.byd.clusternav.launcher.CapTestResult>,
    ): List<Pair<String, Any?>> {
        val s = CapTestSummary.of(items, results)
        return listOf("total" to s.total, "ok" to s.ok, "not_ok" to s.notOk, "untested" to s.untested)
    }

    /** Mã ASCII: `--es id` không có trong bảng soát. */
    const val ERR_UNKNOWN_ID = "unknown_captest_id"
}
