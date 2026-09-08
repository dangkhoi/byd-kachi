package com.byd.clusternav.carexec

import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File

/**
 * Shell thô cho việc **đánh giá** capability trên xe.
 *
 * Tách hẳn khỏi gateway của app, và đó là chủ ý. Gateway của app bị bọc bởi cả một chính sách — chỉ nhận
 * `CommandKind` đã khai báo, có fence theo epoch, có deadline — vì trong app mọi lệnh phải truy được về
 * một giao dịch. Ở đây thì ngược lại: ta đang đi tìm xem lệnh nào mới đúng, nên phải chạy được lệnh thô
 * theo catalog. Hai mục đích khác nhau thì không nên dùng chung một cửa.
 *
 * Chỉ dùng từ runner. Có test chặn `:app` tham chiếu tới lớp này.
 */
class CarExecShell(
    private val host: String,
    private val port: Int,
    private val keys: AdbKeyPair,
    private val connectTimeoutMs: Int = 3_000,
    private val socketTimeoutMs: Int = 10_000,
) : AutoCloseable {

    private var session: Dadb? = null

    private fun connection(): Dadb = session ?: Dadb.create(host, port, keys, connectTimeoutMs, socketTimeoutMs)
        .also { session = it }

    fun run(command: String): ShellOutcome {
        val started = System.currentTimeMillis()
        return runCatching {
            val response = connection().shell(command)
            ShellOutcome(command, response.exitCode, (response.allOutput ?: "").trim(), System.currentTimeMillis() - started)
        }.getOrElse { failure ->
            ShellOutcome(command, -1, "${failure.javaClass.simpleName}: ${failure.message}", System.currentTimeMillis() - started)
        }
    }

    override fun close() {
        runCatching { session?.close() }
        session = null
    }

    companion object {
        fun keysFrom(directory: String): AdbKeyPair? {
            val priv = File(directory, "adbkey")
            val pub = File(directory, "adbkey.pub")
            if (!priv.exists() || !pub.exists()) return null
            return runCatching { AdbKeyPair.read(priv, pub) }.getOrNull()
        }
    }
}
