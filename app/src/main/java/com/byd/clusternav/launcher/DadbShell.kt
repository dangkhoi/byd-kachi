package com.byd.clusternav.launcher

import android.content.Context
import com.byd.clusternav.system.ShellTransport

/**
 * Launcher shell over the **dadb loopback** (localhost:5555, uid-shell) — the SAME channel ClusterNav cast uses
 * on the car to run `am`.
 *
 * - **On the car**: adbd listens on tcp 5555 (owner enabled it) → the app connects directly.
 * - **On the emulator**: adbd only speaks over qemu-pipe, so run `adb reverse tcp:5555 tcp:5555` once per boot
 *   (`ro.adb.secure=0`, no auth). That "port config" mirrors the car, after which the launcher runs
 *   `am ... --windowingMode 5` + `am task resize` exactly as on the car.
 *
 * ── Stage B1 ──────────────────────────────────────────────────────────────────────────────────────────────
 * This is now a THIN façade over [ShellTransport], the single owner of the one window/display-command
 * connection. Previously this class held its OWN `Dadb` with a NON-synchronized `run()` that could interleave
 * streams with VdAppHost's worker threads. The public API (`run`/`probe`/`close`/`seam`) is unchanged so
 * `ShellAppLauncher` / reflow / VdAppHost callers do not churn — only the connection is now shared + serialized.
 *
 * ⚠ BLOCKING I/O — do NOT call on the main thread.
 */
class DadbShell(ctx: Context) {

    private val transport = ShellTransport.get(ctx)

    fun run(cmd: String): String = transport.run(cmd)

    /** true if the shell really runs (dadb connects + returns output). */
    fun probe(): Boolean = transport.probe()

    fun close() = transport.close()

    /** Seam 1-command cho [ShellAppLauncher] / reflow. */
    val seam: (String) -> String = { run(it) }
}
