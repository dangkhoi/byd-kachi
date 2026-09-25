package com.byd.clusternav.system

import android.content.Context
import com.byd.clusternav.AdbKeys
import dadb.Dadb

/**
 * SINGLE OWNER of the localhost:5555 window/display-command dadb connection (uid-2000 shell) that Kachi uses to
 * run `am` / `wm` / `cmd` / `input` / `settings` for the launcher (freeform slots) AND for cluster-cast.
 *
 * ── WHY (Stage B1 — transport consolidation) ────────────────────────────────────────────────────────────────
 * Before B1 there were THREE independent transports, each opening its OWN `Dadb.create("localhost", 5555, …)`:
 *   • [com.byd.clusternav.launcher.DadbShell] — launcher reflow + VdAppHost launch/touch,
 *   • the cast runtime's `DadbSimpleCastShell` — a FRESH connection per command,
 *   • the legacy `ClusterCast` object — its own fresh connection per cast()/stop().
 * `DadbShell.run()` was NOT synchronized, and VdAppHost forwards launch/touch on its OWN worker threads. Two
 * threads calling `shell(cmd)` on one connection interleave that connection's stdin/stdout framing → corrupted
 * command/response streams. This class removes that hazard: ONE connection, and EVERY command runs on ONE
 * single-thread executor, so concurrent callers can never interleave the streams.
 *
 * ── SERIALIZATION PRIMITIVE ──────────────────────────────────────────────────────────────────────────────────
 * A single-worker [com.byd.clusternav.system.PrioritySerialExecutor] (submit + block for the result). kotlinx-
 * coroutines is NOT a dependency of `:app`, so `Dispatchers.IO.limitedParallelism(1)` is unavailable; the
 * single-worker executor is the dependency-free equivalent and gives the same strict one-at-a-time ordering.
 *
 * ── PRIORITY (Stage B2b) ─────────────────────────────────────────────────────────────────────────────────────
 * B1 merged the launcher + cast window commands onto THIS one connection/owner. A plain FIFO owner would let a
 * cast STOP get stuck behind a queued launcher command; instead the owner is a PRIORITY queue that still runs
 * one-at-a-time but drains [MutationPriority.STOP]/[MutationPriority.RESCUE] ahead of [MutationPriority.NORMAL].
 * [exec]/[run] take an optional `priority` that DEFAULTS to NORMAL, so every pre-B2b caller is byte/behaviour-
 * identical; only callers that opt into STOP/RESCUE preempt the queue.
 *
 * ── RECONNECT / RESILIENCE ───────────────────────────────────────────────────────────────────────────────────
 * [exec] retries ONCE on failure (close + reconnect + retry). This REPLICATES the resilience the cast path used
 * to get from opening a fresh connection per command: a stale shared connection self-heals instead of failing
 * the command. Command STRINGS are unchanged — only the connection lifecycle is consolidated.
 *
 * ⚠ BLOCKING I/O — never call on the main thread. Process singleton via [get]; B5 folds it into AppContainer.
 */
class ShellTransport private constructor(context: Context) {

    private val ctx = context.applicationContext

    /** The one shared connection. Touched ONLY on [owner] (see [onOwner]) so no external lock is needed. */
    private var db: Dadb? = null

    /**
     * Single owner — serializes every command (two callers can't interleave one connection's streams) AND drains
     * STOP/RESCUE-priority commands ahead of NORMAL on the shared window/cast queue. Still strictly one-at-a-time.
     */
    private val owner = PrioritySerialExecutor("kachi-window-shell")

    /** Structured result mirroring the dadb `AdbShellResponse` fields the consumers read. */
    data class Response(val exitCode: Int, val stdout: String, val stderr: String, val allOutput: String)

    /**
     * (Re)connect lazily and reuse the one connection. Runs ONLY on [owner].
     *
     * ⚠ [P0-2 · quality-review 2026-09-15] TIMEOUT BẮT BUỘC. Overload 3-arg `Dadb.create(host,port,keys)` đặt
     * connect+socket timeout = **0 = VÔ HẠN** ([ĐO] decompile dadb-2.0.0). Vì MỌI lệnh cửa sổ + cast dồn vào một
     * worker nối tiếp chặn ở `Future.get()`, một `adbd` xe wedge giữa chừng (xóc/chớp nguồn/TCP nửa-mở) sẽ treo
     * worker VĨNH VIỄN ⇒ đơ toàn bộ đặt-cửa-sổ + cast (kể cả STOP), im lặng, rò thread → OOM. Truyền timeout tường
     * minh (đúng cách shell của runner đánh giá trong `car-integration` đã làm) ⇒ lệnh treo NHẢ sau ≤ socket-timeout,
     * `exec` retry một lần rồi ném cho caller; worker được giải phóng thay vì chặn mãi.
     */
    private fun conn(): Dadb =
        db ?: Dadb.create("localhost", 5555, AdbKeys.ensure(ctx), CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS).also { db = it }

    private fun closeConn() { runCatching { db?.close() }; db = null }

    private fun attempt(cmd: String): Response {
        // PERF 2026-09-16: đếm Ở ĐÂY (chỗ lệnh thật sự rời tiến trình), không ở `exec` — một `exec` hỏng rồi thử
        // lại là HAI lượt chặn trên hàng đợi dùng chung, và đó đúng là cái giá mà bộ đếm phải nói ra.
        com.byd.clusternav.launcher.KachiPerf.add(com.byd.clusternav.launcher.KachiPerf.Counter.SHELL_CMD)
        val r = conn().shell(cmd)
        return Response(r.exitCode, r.output, r.errorOutput, r.allOutput)
    }

    /**
     * Run [cmd] serialized on the single owner at [priority] and return the structured [Response].
     * On failure: close + reconnect + retry ONCE (self-heals a stale connection, as fresh-conn-per-command did).
     * If the retry also fails, the (unwrapped) exception is thrown to the caller.
     * [priority] defaults to [MutationPriority.NORMAL] — pre-B2b callers stay byte/behaviour-identical; a cast
     * STOP/RESCUE path may pass a higher priority to preempt queued NORMAL launcher commands.
     */
    fun exec(cmd: String, priority: MutationPriority = MutationPriority.NORMAL): Response = onOwner(priority) {
        runCatching { attempt(cmd) }.getOrElse {
            closeConn()
            // ═══ 1.70 · [ĐO xe 2026-09-17] lệnh `input …` KHÔNG được gửi lại ═══════════════════════════
            // Lượt thử lại là nguồn của cú chạm ĐÔI (*"play → pause → play"*): dưới tải xe một `input tap`
            // có thể quá `SOCKET_TIMEOUT_MS` mà đã bơm xong. Luật nằm ở `:core` ([ShellIdempotency]); kết nối
            // vẫn được đóng để lệnh SAU tự chữa như B1 — chỉ lệnh này không đi lần hai.
            if (!ShellIdempotency.retryable(cmd)) throw it
            attempt(cmd)
        }
    }

    /**
     * `allOutput` of [cmd], or "" if BOTH attempts failed — byte-for-byte the legacy `DadbShell.run()` contract.
     *
     * Hardening 2026-09-25 (audit F1): hỏng thì vẫn trả `""` (consumer không đổi), nhưng **ghi một dòng W** có lệnh
     * rút gọn + lớp lỗi, tiết chế theo lớp lỗi ([ShellRunFailureLog]) để dadb chết không thành bão log.
     */
    fun run(cmd: String, priority: MutationPriority = MutationPriority.NORMAL): String =
        runCatching { exec(cmd, priority).allOutput }.getOrElse { t ->
            ShellRunFailureLog.line(cmd, t, android.os.SystemClock.elapsedRealtime())?.let { android.util.Log.w(TAG, it) }
            ""
        }

    /** One-command seam for [com.byd.clusternav.launcher.ShellAppLauncher] / reflow / VdAppHost. */
    val seam: (String) -> String = { run(it) }

    /** true if the shell really runs (dadb connected + echoes back). */
    fun probe(): Boolean = runCatching { run("echo kachi_ok").contains("kachi_ok") }.getOrDefault(false)

    /** Close the shared connection; the next command reconnects. */
    fun close() { onOwner(MutationPriority.NORMAL) { closeConn() } }

    /** Submit [body] to the single owner at [priority], block for its result (unwrap happens in the executor). */
    private fun <T> onOwner(priority: MutationPriority, body: () -> T): T = owner.submit(priority, body)

    companion object {
        private const val TAG = "ShellTransport"

        /** Timeout kết nối/đọc cho dadb (P0-2). Cùng giá trị proven của shell runner đánh giá trong `car-integration`. */
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val SOCKET_TIMEOUT_MS = 10_000

        /**
         * Dựng một [ShellTransport] mới cho [com.byd.clusternav.AppContainer] (chủ đồ thị DI). AppContainer giữ DUY
         * NHẤT một instance (lazy) → factory này chỉ được gọi một lần cho cả tiến trình.
         */
        internal fun createOwned(context: Context): ShellTransport = ShellTransport(context.applicationContext)

        /**
         * Process-wide single owner — NAY UỶ QUYỀN về [com.byd.clusternav.AppContainer] (đồ thị DI, B5). Mọi caller
         * cũ (cast, [com.byd.clusternav.launcher.DadbShell], FreeformSeed…) chạy y nguyên; instance đến TỪ container
         * thay cho @Volatile riêng ở đây. Thread-safe (AppContainer.get + field `by lazy` đều đồng bộ hoá).
         */
        fun get(context: Context): ShellTransport = com.byd.clusternav.AppContainer.get(context).shellTransport
    }
}
