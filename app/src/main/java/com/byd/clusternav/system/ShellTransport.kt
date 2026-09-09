package com.byd.clusternav.system

import android.content.Context
import com.byd.clusternav.AdbKeys
import dadb.Dadb
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

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
 * A single-thread [java.util.concurrent.ExecutorService] (`submit` + `Future.get`). kotlinx-coroutines is NOT a
 * dependency of `:app`, so `Dispatchers.IO.limitedParallelism(1)` is unavailable; the single-thread executor is
 * the dependency-free equivalent and gives the same strict one-at-a-time ordering.
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

    /** Single owner thread — serializes every command so two callers can't interleave one connection's streams. */
    private val owner = Executors.newSingleThreadExecutor { r ->
        Thread(r, "kachi-window-shell").apply { isDaemon = true }
    }

    /** Structured result mirroring the dadb `AdbShellResponse` fields the consumers read. */
    data class Response(val exitCode: Int, val stdout: String, val stderr: String, val allOutput: String)

    /** (Re)connect lazily and reuse the one connection. Runs ONLY on [owner]. */
    private fun conn(): Dadb = db ?: Dadb.create("localhost", 5555, AdbKeys.ensure(ctx)).also { db = it }

    private fun closeConn() { runCatching { db?.close() }; db = null }

    private fun attempt(cmd: String): Response {
        val r = conn().shell(cmd)
        return Response(r.exitCode, r.output, r.errorOutput, r.allOutput)
    }

    /**
     * Run [cmd] serialized on the single owner thread and return the structured [Response].
     * On failure: close + reconnect + retry ONCE (self-heals a stale connection, as fresh-conn-per-command did).
     * If the retry also fails, the (unwrapped) exception is thrown to the caller.
     */
    fun exec(cmd: String): Response = onOwner {
        runCatching { attempt(cmd) }.getOrElse {
            closeConn()
            attempt(cmd)
        }
    }

    /** `allOutput` of [cmd], or "" if BOTH attempts failed — byte-for-byte the legacy `DadbShell.run()` contract. */
    fun run(cmd: String): String = runCatching { exec(cmd).allOutput }.getOrDefault("")

    /** One-command seam for [com.byd.clusternav.launcher.ShellAppLauncher] / reflow / VdAppHost. */
    val seam: (String) -> String = { run(it) }

    /** true if the shell really runs (dadb connected + echoes back). */
    fun probe(): Boolean = runCatching { run("echo kachi_ok").contains("kachi_ok") }.getOrDefault(false)

    /**
     * LEGACY escape hatch for the (currently unreachable) [com.byd.clusternav.modules.clustercast.ClusterCast]
     * cast()/stop()/reconcile paths, whose helper functions take the raw [Dadb]. The [block] runs on the single
     * owner thread, so the shared connection is NEVER used concurrently — the same guarantee [exec] gives. New
     * code MUST use [run]/[exec]/[seam]; this exists only to fold ClusterCast's `Dadb.create` sites onto the one
     * owner WITHOUT a risky rewrite of proven-but-unreachable code, and is removed when ClusterCast is deleted.
     */
    fun <T> withConnection(block: (Dadb) -> T): T = onOwner { block(conn()) }

    /** Close the shared connection; the next command reconnects. */
    fun close() { onOwner { closeConn() } }

    /** Submit [body] to the single owner thread, block for its result, and unwrap execution exceptions. */
    private fun <T> onOwner(body: () -> T): T = try {
        owner.submit(Callable { body() }).get()
    } catch (e: ExecutionException) {
        throw (e.cause ?: e)
    }

    companion object {
        @Volatile private var instance: ShellTransport? = null

        /** Process-wide single owner. Thread-safe. (B5 will fold this into AppContainer.) */
        fun get(context: Context): ShellTransport =
            instance ?: synchronized(this) {
                instance ?: ShellTransport(context.applicationContext).also { instance = it }
            }
    }
}
