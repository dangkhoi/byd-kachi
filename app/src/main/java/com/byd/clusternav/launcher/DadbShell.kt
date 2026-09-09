package com.byd.clusternav.launcher

import android.content.Context
import com.byd.clusternav.AdbKeys
import dadb.Dadb

/**
 * Shell qua **dadb loopback** (localhost:5555, uid-shell) — CÙNG đường ClusterNav cast dùng trên xe để chạy `am`.
 *
 * - **Trên xe**: adbd nghe sẵn tcp 5555 (owner đã bật) → app connect thẳng.
 * - **Trên emulator**: adbd chỉ nói qua qemu-pipe, KHÔNG mở tcp trong guest → cần **`adb reverse tcp:5555 tcp:5555`**
 *   một lần mỗi lần boot (mirror cổng; `ro.adb.secure=0` nên không cần auth). Đây là cách "config cổng" để giả lập
 *   giống hệt xe — sau đó launcher chạy `am ... --windowingMode 5` + `am task resize` y như trên xe.
 *
 * ⚠ BLOCKING I/O — KHÔNG gọi trên main thread. Giữ 1 kết nối, tự nối lại nếu đứt.
 */
class DadbShell(private val ctx: Context) {
    @Volatile private var db: Dadb? = null

    @Synchronized
    private fun conn(): Dadb = db ?: Dadb.create("localhost", 5555, AdbKeys.ensure(ctx)).also { db = it }

    fun run(cmd: String): String =
        runCatching { conn().shell(cmd).allOutput ?: "" }.getOrElse {
            close()
            runCatching { conn().shell(cmd).allOutput ?: "" }.getOrDefault("")
        }

    /** true nếu shell thật sự chạy được (dadb nối được + trả output). */
    fun probe(): Boolean = runCatching { run("echo kachi_ok").contains("kachi_ok") }.getOrDefault(false)

    @Synchronized
    fun close() { runCatching { db?.close() }; db = null }

    /** Seam 1-lệnh cho [ShellAppLauncher] / reflow. */
    val seam: (String) -> String = { run(it) }
}
