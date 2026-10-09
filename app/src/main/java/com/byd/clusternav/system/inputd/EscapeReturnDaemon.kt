package com.byd.clusternav.system.inputd

import android.os.Build
import android.os.SystemClock
import com.byd.clusternav.launcher.escape.EscapeReport
import com.byd.clusternav.launcher.escape.EscapeReturnApi
import com.byd.clusternav.launcher.escape.EscapeReturnBreaker
import com.byd.clusternav.launcher.escape.EscapeReturnConfig
import com.byd.clusternav.launcher.escape.EscapeReturnGuard
import com.byd.clusternav.launcher.escape.EscapeReturnGuard.Decision
import com.byd.clusternav.launcher.escape.EscapeReturnWire
import java.util.concurrent.Executors

/**
 * ═══ 2.98 · R18 (SLOT-ESCAPE-VD-RETURN) — keo trong DAEMON uid 2000: bảng Kachi gửi + sự kiện "thoát ô" ⇒ dời stack về màn ảo ô ══
 *
 * Sống trong tiến trình `InputDaemonMain` sẵn có (không tiến trình mới). Spec `docs/specs/kachi-298-plan.html` R18, evidence
 * `docs/diagnostics/emu-slot-escape-approaches-2026-10-09.md`.
 *
 * - Bảng ([onConfig]) đến từ Kachi qua khung điều khiển; bảng có api ⇒ đăng ký bộ nghe MỘT lần; bảng tắt / Kachi rớt ([detach]) ⇒
 *   xoá bảng + gỡ bộ nghe (CLAUDE.md §5: trạng thái sống theo chủ của nó — Kachi chết thì không còn ô nào để trả về).
 * - Sự kiện ([TaskStackBinder.Listener]) ⇒ [EscapeReturnGuard.precheck] (0 lời gọi) ⇒ đọc stack + chế độ màn đích (2 lời gọi) ⇒
 *   [EscapeReturnGuard.decide] ⇒ `moveStackToDisplay` ⇒ đọc lại ([EscapeReturnGuard.verify]) ⇒ [EscapeReturnBreaker].
 * - MỘT luồng cho mọi việc ở đây (bảng, sự kiện, cầu chì không cần khoá). Mọi lỗi bị bắt tại đây và đi vào cầu chì — daemon bơm
 *   chạm KHÔNG bao giờ chết vì phần này.
 * - Báo cáo một dòng/sự kiện về Kachi ([EscapeReturnWire.encodeReport]) + nhật ký daemon (`inputd-*.log`).
 */
internal object EscapeReturnDaemon {

    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "kachi-esc").apply { isDaemon = true } }

    // Mọi trường dưới chỉ chạm trên [exec].
    private var config = EscapeReturnConfig.OFF
    private var sink: ((String) -> Unit)? = null
    private var log: (String) -> Unit = {}
    private val breaker = EscapeReturnBreaker()
    private var system: TaskStackBinder.System? = null
    private var listener: TaskStackBinder.Listener? = null
    private var registered: Any? = null

    /** Một client vừa qua bắt tay — [send] ghi một dòng về nó (ném ⇒ bỏ, client đã rớt). */
    fun attach(send: (String) -> Unit, logger: (String) -> Unit) = exec.execute {
        sink = send
        log = logger
    }

    /** Client rớt ⇒ quên bảng, gỡ bộ nghe. */
    fun detach() = exec.execute {
        sink = null
        config = EscapeReturnConfig.OFF
        unregister("client-gone")
    }

    /** Thân khung điều khiển (UTF-8). Không phải bảng R18 ⇒ bỏ qua, giữ bảng cũ. */
    fun onConfig(text: String) = guarded("config") {
        val c = EscapeReturnWire.decodeConfig(text) ?: return@guarded log("esc: khung điều khiển lạ — bỏ qua")
        config = c
        log("esc: bảng api=${c.api?.id ?: "tắt"} ô=${c.slots}")
        val api = c.api
        when {
            api == null -> unregister("config-off")
            breaker.allTripped != null -> {
                // Soát Pass 12 [P2]: client này có thể là Kachi MỚI (bị giết đúng lúc ngắt bền / socket ghi hỏng lúc đó) chưa có dấu
                // bền ⇒ phát lại đúng báo cáo bền lần đầu để nó ghi (idempotent phía Kachi), rồi mới báo tắt.
                breaker.persisted?.let(::report)
                report(EscapeReport.Off("tripped:${breaker.allTripped}"))
            }
            !api.usableOn(Build.VERSION.SDK_INT) -> report(EscapeReport.Off("sdk${Build.VERSION.SDK_INT}!=${api.sdk}"))
            registered == null -> register(api)
        }
    }

    /**
     * Soát Pass 12 [P3]: mọi việc trên luồng `kachi-esc` đi qua đây — một ngoại lệ lọt khỏi [block] trên luồng của `Executor.execute`
     * là uncaught ⇒ Android giết CẢ tiến trình daemon (bơm chạm chết theo). Bắt `Exception` (không bắt `Error`) ⇒ cầu chì tắt tất cả.
     */
    private fun guarded(what: String, block: () -> Unit) = exec.execute {
        try {
            block()
        } catch (e: Exception) {
            mechanism("uncaught:$what:${e.javaClass.simpleName}:${e.message?.take(80)}")
        }
    }

    private fun register(api: EscapeReturnApi) {
        try {
            val sys = system ?: TaskStackBinder.System(api).also { system = it }
            val l = listener ?: TaskStackBinder.Listener(api) { f -> guarded("event") { onFailed(f) } }.also { listener = it }
            registered = sys.register(l)
            report(EscapeReport.Ready(api.id))
        } catch (e: ReflectiveOperationException) {
            mechanism("register:${e.javaClass.simpleName}:${e.message}")
        } catch (e: RuntimeException) {
            mechanism("register:${e.javaClass.simpleName}:${e.message}")
        }
    }

    private fun unregister(why: String) {
        val r = registered ?: return
        registered = null
        try {
            system?.unregister(r)
        } catch (e: ReflectiveOperationException) {
            log("esc: gỡ bộ nghe hỏng: ${e.javaClass.simpleName}")
        } catch (e: RuntimeException) {
            log("esc: gỡ bộ nghe hỏng: ${e.javaClass.simpleName}")
        }
        report(EscapeReport.Off(why))
    }

    private fun onFailed(f: TaskStackBinder.Failed) {
        val e = f.escape
        val now = SystemClock.uptimeMillis()
        EscapeReturnGuard.precheck(e, config, breaker, now)?.let { why ->
            if (why != EscapeReturnGuard.Why.DISABLED) report(EscapeReport.Skipped(e.pkg, e.taskId, e.requestedDisplay, why.name))
            return
        }
        val pkg = e.pkg ?: return
        val sys = system ?: return
        val decision = try {
            EscapeReturnGuard.decide(e, sys.stacks(), sys.windowingMode(e.requestedDisplay))
        } catch (t: ReflectiveOperationException) {
            return mechanism("read:${t.javaClass.simpleName}:${t.message}")
        } catch (t: RuntimeException) {
            return mechanism("read:${t.javaClass.simpleName}:${t.message}")
        }
        val move = decision as? Decision.Move ?: run {
            report(EscapeReport.Skipped(pkg, e.taskId, e.requestedDisplay, (decision as Decision.Skip).why.name))
            return
        }
        try {
            sys.move(move.stackId, move.vd)
        } catch (t: Exception) {
            // Lỗi phía system_server truyền qua binder giữ tên lớp (NPE 08-01) — phân loại ở `EscapeReturnBreaker.classify`.
            return tripped(breaker.onMoveThrew(pkg, t.javaClass.name, "${t.message} ${t.stackTraceToString().take(STACK_CHARS)}"))
        }
        val after = try {
            EscapeReturnGuard.verify(e.taskId, move.vd, sys.stacks())
        } catch (t: Exception) {
            EscapeReturnGuard.After.ELSEWHERE.also { log("esc: đọc lại hỏng: ${t.javaClass.simpleName}") }
        }
        breaker.onAfter(pkg, after)?.let { return tripped(it) }
        report(EscapeReport.Moved(pkg, e.taskId, move.stackId, move.vd, SystemClock.uptimeMillis() - f.atMs))
        breaker.onMoved(pkg, SystemClock.uptimeMillis())?.let(::tripped)
    }

    private fun mechanism(why: String) = tripped(breaker.onMechanismError(why))

    /** Báo cầu chì; ngắt TẤT CẢ (kể cả bền) ⇒ gỡ luôn bộ nghe — không còn lý do nhận sự kiện tới hết đời daemon. */
    private fun tripped(r: EscapeReport.Tripped) {
        report(r)
        if (r.scope != EscapeReport.Scope.PKG) unregister("tripped")
    }

    private fun report(r: EscapeReport) {
        val line = EscapeReturnWire.encodeReport(r)
        log(line)
        val s = sink ?: return
        try {
            s(line)
        } catch (e: java.io.IOException) {
            sink = null
        }
    }

    /** Trần chữ của vết lỗi đưa vào bộ phân loại (đủ để thấy `createTaskSnapshot` nếu có). */
    private const val STACK_CHARS = 2_000
}
