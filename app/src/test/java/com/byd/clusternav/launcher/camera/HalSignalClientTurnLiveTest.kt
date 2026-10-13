package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.LampLive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 3.01 TURN — [HalSignalClient] (client socket DUY NHẤT của xi-nhan thật) phát lại trạng thái vào [LampLive] cho bảng Đèn.
 * Socket THẬT: một [ServerSocket] đóng vai helper, gửi dòng theo nhịp test điều khiển.
 *
 * Khoá: chưa start ⇒ `null` (bảng lùi về getter) · nối được ⇒ `(false,false)` · ON/OFF ⇒ từng bước · helper đứt ⇒ `null` ·
 * `stop()` ⇒ `null`. Thử ĐỎ [ĐO 13/10]: bỏ `live.clear()` trong `finally` của `loop` ⇒ `helper dut` ĐỎ; bỏ `live.publish` sau
 * nối ⇒ `noi duoc` ĐỎ.
 */
class HalSignalClientTurnLiveTest {

    private fun line(topic: String, type: Int) = """{"topic":"$topic","type":$type}"""

    /** Chờ tới khi [live] mang [want] (hoặc hết 5 s). */
    private fun await(live: LampLive, want: LampLive.Lamps?): LampLive.Lamps? {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < end && live.state.value != want) Thread.sleep(5)
        return live.state.value
    }

    @Test
    fun `noi duoc - ON OFF - helper dut - stop`() {
        val server = ServerSocket(0)
        val send = java.util.concurrent.LinkedBlockingQueue<String>()
        val closed = CountDownLatch(1)
        Thread({
            runCatching {
                server.accept().use { s ->
                    val w = s.getOutputStream().bufferedWriter()
                    while (true) {
                        val l = send.poll(5, TimeUnit.SECONDS) ?: break
                        if (l == "CLOSE") break
                        w.write(l); w.write("\n"); w.flush()
                    }
                }
            }
            closed.countDown()
        }, "fake-kachi-hal-turn").apply { isDaemon = true; start() }

        val live = LampLive()
        // Ngủ dài giữa hai lần nối ⇒ sau khi helper đứt, client KHÔNG nối lại trong lúc test kiểm `null`.
        val client = HalSignalClient(port = server.localPort, live = live) { Thread.sleep(60_000) }
        assertNull(live.state.value, "chưa chạy ⇒ không biết")
        try {
            client.start { _, _ -> }
            assertEquals(LampLive.Lamps(false, false), await(live, LampLive.Lamps(false, false)), "nối được ⇒ biết: tắt")
            send.add(line(HalSignalClient.TOPIC_ON, HalSignalClient.TYPE_LEFT))
            assertEquals(LampLive.Lamps(true, false), await(live, LampLive.Lamps(true, false)))
            send.add(line(HalSignalClient.TOPIC_OFF, HalSignalClient.TYPE_LEFT))
            assertEquals(LampLive.Lamps(false, false), await(live, LampLive.Lamps(false, false)), "OFF ⇒ tắt ngay")
            send.add(line(HalSignalClient.TOPIC_ON, HalSignalClient.TYPE_RIGHT))
            assertEquals(LampLive.Lamps(false, true), await(live, LampLive.Lamps(false, true)))
            send.add("CLOSE")
            assertTrue(closed.await(5, TimeUnit.SECONDS))
            assertNull(await(live, null), "helper đứt ⇒ không biết ⇒ bảng lùi về số đọc")
        } finally {
            client.stop()
            runCatching { server.close() }
        }
        assertNull(live.state.value, "stop() ⇒ không biết")
    }

    @Test
    fun `stop khi dang noi - xoa ngay`() {
        val server = ServerSocket(0)
        val hold = CountDownLatch(1)
        Thread({
            runCatching {
                server.accept().use { s ->
                    val w = s.getOutputStream().bufferedWriter()
                    w.write(line(HalSignalClient.TOPIC_ON, HalSignalClient.TYPE_RIGHT)); w.write("\n"); w.flush()
                    hold.await(5, TimeUnit.SECONDS)
                }
            }
        }, "fake-kachi-hal-hold").apply { isDaemon = true; start() }
        val live = LampLive()
        val client = HalSignalClient(port = server.localPort, live = live) { Thread.sleep(60_000) }
        try {
            client.start { _, _ -> }
            assertEquals(LampLive.Lamps(false, true), await(live, LampLive.Lamps(false, true)))
            client.stop()
            assertNull(live.state.value, "tắt tính năng camera xi-nhan ⇒ bảng thôi tin socket")
        } finally {
            hold.countDown()
            client.stop()
            runCatching { server.close() }
        }
    }
}
