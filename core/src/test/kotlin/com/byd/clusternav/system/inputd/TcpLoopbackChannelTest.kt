package com.byd.clusternav.system.inputd

import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [TcpLoopbackChannel] với một `ServerSocket` THẬT trên loopback (thuần JVM, không thiết bị): bắt tay token đi
 * trước, rồi khung chạm nguyên byte; không ai nghe ⇒ `false` + lý do nguyên văn của nền tảng.
 */
class TcpLoopbackChannelTest {

    @Test
    fun `bat tay token roi khung cham di nguyen byte`() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val got = ArrayList<ByteArray>()
            val done = CountDownLatch(1)
            Thread {
                server.accept().use { c ->
                    val input = DataInputStream(c.getInputStream())
                    val line = StringBuilder()
                    while (true) { val b = input.read(); if (b < 0 || b == '\n'.code) break; line.append(b.toChar()) }
                    got += line.toString().toByteArray()
                    val buf = ByteArray(InputWireProtocol.FRAME_BYTES)
                    input.readFully(buf)
                    got += buf
                }
                done.countDown()
            }.apply { isDaemon = true }.start()

            val ch = TcpLoopbackChannel(server.localPort, "abcdef0123456789")
            assertTrue(ch.connect(), "phải nối được tới server loopback")
            val frame = InputWireProtocol.encode(TouchFrame(2, 1, 640, 360))
            assertTrue(ch.write(frame))
            ch.close()

            assertTrue(done.await(3, TimeUnit.SECONDS), "server phải nhận đủ bắt tay + 1 khung")
            assertArrayEquals("abcdef0123456789".toByteArray(), got[0], "khung đầu = token")
            assertArrayEquals(frame, got[1], "khung chạm nguyên byte")
        }
    }

    @Test
    fun `khong ai nghe thi false va ly do nguyen van`() {
        val free = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        val ch = TcpLoopbackChannel(free, "abcdef0123456789")
        assertFalse(ch.connect())
        val err = ch.lastError()
        assertNotNull(err)
        assertTrue(err!!.contains("Exception"), "lý do phải là câu chữ của nền tảng, thấy: $err")
        assertFalse(ch.write(ByteArray(InputWireProtocol.FRAME_BYTES)), "chưa nối thì ghi phải false")
    }

    @Test
    fun `cong theo uid on dinh va trong dai 38000-38999`() {
        assertEquals(InputDaemonLaunch.portFor(10138), InputDaemonLaunch.portFor(10138))
        assertEquals(38_138, InputDaemonLaunch.portFor(10138))
        assertEquals(38_000, InputDaemonLaunch.portFor(20_000))
        assertTrue(InputDaemonLaunch.portFor(-7) in 38_000..38_999)
        assertTrue(InputDaemonLaunch.validToken("abcdef0123456789"))
        assertFalse(InputDaemonLaunch.validToken("short"))
        assertFalse(InputDaemonLaunch.validToken("abcdef0123456789 x"), "khoảng trắng phá dòng lệnh")
    }
}
