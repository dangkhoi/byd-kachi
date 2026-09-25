package com.byd.clusternav.carexec

import dadb.AdbConnectException
import dadb.AdbKeyPair
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Hardening 2026-09-25 · audit F2 [P2] — `LocalDeviceShell.run/runAll` trước đây: `Dadb.create(host, port, keys)`
 * (socket timeout 0 ⇒ adbd câm là treo vĩnh viễn) + `runCatching{}.getOrNull()` (mọi lý do thành một chữ `null`).
 * Khoá: hạn đọc = [LocalShellRetry.BACKGROUND_READ_CAP.socketTimeoutMs] được truyền xuống connector; hỏng ⇒ `null`
 * + `onFailure` đúng lý do đã phân loại; thành công ⇒ stdout+stderr trim theo thứ tự lệnh, không thử lại.
 */
class LocalDeviceShellRunTest {

    private var cachedKeys: AdbKeyPair? = null
    private fun keys(dir: File): AdbKeyPair = cachedKeys ?: run {
        val priv = File(dir, "adb.key"); val pub = File(dir, "adb.pub")
        AdbKeyPair.generate(priv, pub)
        AdbKeyPair.read(priv, pub).also { cachedKeys = it }
    }

    private class FakeConnector(private val openFailure: Throwable? = null) : LocalShellConnector {
        val socketTimeouts = mutableListOf<Int>()
        val commands = mutableListOf<String>()
        var opens = 0
        override fun open(keys: AdbKeyPair, socketTimeoutMs: Int): LocalShellConnection {
            socketTimeouts += socketTimeoutMs
            opens++
            openFailure?.let { throw it }
            return object : LocalShellConnection {
                override fun handshake() {}
                override fun shell(command: String): LocalShellText {
                    commands += command
                    return LocalShellText(output = " out:$command \n", errorOutput = "err:$command\n", exitCode = 0)
                }
                override fun close() {}
            }
        }
    }

    @Test fun `co han doc 30 s va output = stdout+stderr trim theo thu tu`(@TempDir dir: File) {
        val c = FakeConnector()
        val failures = mutableListOf<LocalShellFailure>()
        val out = LocalDeviceShell.runWith(c, keys(dir), listOf("a", "b"), onFailure = { failures += it })
        assertEquals(listOf("out:a \nerr:a", "out:b \nerr:b"), out, "allOutput = output + errorOutput, trim (javap dadb 2.0.0)")
        assertEquals(listOf("a", "b"), c.commands, "một phiên, đúng thứ tự")
        assertEquals(listOf(LocalShellRetry.BACKGROUND_READ_CAP.socketTimeoutMs), c.socketTimeouts, "hạn đọc phải là 30 000, không còn 0 = treo vĩnh viễn")
        assertEquals(30_000, c.socketTimeouts.single())
        assertEquals(emptyList<LocalShellFailure>(), failures)
    }

    @Test fun `adbd cam cho bam Cho phep - null + AWAITING_APPROVAL, khong thu lai`(@TempDir dir: File) {
        val c = FakeConnector(AdbConnectException("Connection handshake failed", SocketTimeoutException("Read timed out")))
        val failures = mutableListOf<LocalShellFailure>()
        assertNull(LocalDeviceShell.runWith(c, keys(dir), listOf("echo x"), onFailure = { failures += it }))
        assertEquals(listOf(LocalShellFailure.AWAITING_APPROVAL), failures, "lý do phải được nói ra, không phải null câm")
        assertEquals(1, c.opens, "đường nền không tự phát lại (BACKGROUND_READ_CAP: attempts = 1)")
    }

    @Test fun `cong 5555 dong - PORT_CLOSED`(@TempDir dir: File) {
        val c = FakeConnector(AdbConnectException("Failed to connect to localhost:5555", ConnectException("Connection refused")))
        val failures = mutableListOf<LocalShellFailure>()
        assertNull(LocalDeviceShell.runWith(c, keys(dir), listOf("echo x"), onFailure = { failures += it }))
        assertEquals(listOf(LocalShellFailure.PORT_CLOSED), failures)
    }
}
