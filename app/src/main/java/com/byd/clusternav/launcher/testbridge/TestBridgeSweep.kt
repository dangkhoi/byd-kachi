package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.util.Log
import com.byd.clusternav.launcher.BindingRoute
import com.byd.clusternav.launcher.BydHalGateway
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.HalBindingTable
import com.byd.clusternav.launcher.KachiLog
import com.byd.clusternav.launcher.TelemetryRegistry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ═══ T-BRIDGE · LỆNH `sweep` — QUÉT RAW TOÀN BỘ TELEMETRY + ROUTE TOÀN BỘ CONTROL, MỘT LƯỢT ═══════════════════
 *
 * Spec `docs/specs/kachi-hal187-cast-remediation.html` R4 / §4.3. Owner 2026-09-15: *"mất quá nhiều thời gian
 * trên xe vô nghĩa"* — bấm tay 187 mục trong "Kiểm tra từng nút xe" tốn cả buổi mà ra "số vô nghĩa" không có
 * route/raw để phân loại. Lệnh này thay việc đó bằng **một broadcast**: đọc `readRaw(id)` cho MỌI telemetry (đúng
 * đường `HalBindingTable` mà UI dùng) + `describeWrite` cho MỌI control (route+device, **KHÔNG bắn**), ghi JSON
 * ra thẻ để phân loại off-car: chưa-map / route-sai / scale-sai / bị-chặn.
 *
 * ## Chỉ ĐỌC — an toàn, không cần confirm
 * Không gọi `set*`/feature-set nào; control chỉ được *mô tả* route. Vì thế không qua cổng CONFIRM, không đổi
 * state xe. Vẫn gated bởi chế độ kiểm thử như mọi lệnh của cầu.
 *
 * ## Luồng nền
 * `readRaw` = binder reflection CHẶN (xem `AppContainer.carScope` chạy trên IO) — 100+ mục không được đọc trên
 * luồng broadcast. Chạy trên `Thread`, trả lời khi xong (như `runWav`/`runDiag`). Không cần màn chính (như `hal`).
 */
internal object TestBridgeSweep {

    /** `--es op`: `info` (chỉ telemetry) · `ctl` (chỉ control) · rỗng/`all` (cả hai). */
    const val OP_INFO = "info"
    const val OP_CTL = "ctl"

    /** Không ghi được tệp ra thẻ (vắng external) — vẫn trả tóm tắt trong lời đáp. */
    const val NOTE_NO_FILE = "khong ghi duoc tep (vang the) — chi co tom tat"

    fun run(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        Thread({
            runCatching { sweep(app, cmd.op, reply) }
                .onFailure { t ->
                    Log.w(TestBridgeReply.TAG, "sweep nem: ${t.javaClass.simpleName}", t)
                    reply.fail(KachiTestBridge.ERR_THREW, "exception" to t.javaClass.simpleName)
                }
        }, "KachiTestSweep").start()
    }

    private fun sweep(app: Context, op: String, reply: TestBridgeReply) {
        val table = HalBindingTable(BydHalGateway(app))
        val doInfo = op.isBlank() || op == "all" || op == OP_INFO
        val doCtl = op.isBlank() || op == "all" || op == OP_CTL

        var infoRead = 0
        val info = if (!doInfo) emptyList() else TelemetryRegistry.ALL.map { t ->
            val value = runCatching { table.readRaw(t.id) }.getOrNull()
            if (value != null) infoRead++
            TestBridgeJson.Raw(
                TestBridgeJson.obj(
                    "id" to t.id,
                    "tier" to t.tier.name,
                    "key" to t.bindingKey,
                    "route" to routeName(t.bindingKey),
                    "value" to (value ?: ""),
                ),
            )
        }

        var ctlRouted = 0
        val ctl = if (!doCtl) emptyList() else ControlRegistry.ALL.map { c ->
            val (route, device) = HalBindingTable.describeWrite(c)
            val routed = HalBindingTable.routeOf(c.bindingKey) !is BindingRoute.None
            if (routed) ctlRouted++
            TestBridgeJson.Raw(
                TestBridgeJson.obj(
                    "id" to c.id,
                    "kind" to c.kind.name,
                    "tier" to c.tier.name,
                    "key" to c.bindingKey,
                    "route" to route,
                    "device" to device,
                    "routed" to routed,
                ),
            )
        }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val json = TestBridgeJson.obj(
            "stamp" to stamp,
            "info" to TestBridgeJson.Raw(TestBridgeJson.arr(info)),
            "ctl" to TestBridgeJson.Raw(TestBridgeJson.arr(ctl)),
        )
        val file = writeToCard(app, "sweep-$stamp.json", json)

        reply.ok(
            listOf(
                "file" to (file?.absolutePath ?: ""),
                "note" to (if (file == null) NOTE_NO_FILE else ""),
                "info_total" to info.size,
                "info_read" to infoRead,
                "ctl_total" to ctl.size,
                "ctl_routed" to ctlRouted,
                "pull" to KachiLog.pullCommand(app),
            ),
        )
    }

    /** Tên route ổn định để script so (`NamedMethod`/`Feature`/`Setting`/`Local`/`None`). */
    private fun routeName(bindingKey: String): String =
        HalBindingTable.routeOf(bindingKey)::class.simpleName ?: "None"

    /** Ghi ra thư mục log trên thẻ (cùng chỗ `captest-report.txt`) — `null` khi vắng thẻ/ghi hỏng. */
    private fun writeToCard(app: Context, name: String, text: String): File? = runCatching {
        val f = File(KachiLog.dir(app) ?: return null, name)
        f.writeText(text)
        f
    }.onFailure { Log.w(TestBridgeReply.TAG, "sweep ghi tep hong: ${it.message}") }.getOrNull()
}
