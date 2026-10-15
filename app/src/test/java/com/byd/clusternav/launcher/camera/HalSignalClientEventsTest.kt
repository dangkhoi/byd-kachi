package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.HalEventWire
import com.byd.clusternav.launcher.LampLive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 3.1.6 · HAL-EVENTS — client trên socket THẬT với một helper giả: gửi dòng `sub` hiện hành ngay khi nối, chuyển dòng `hal.*` cho
 * sink (không vào bảng đèn), dòng đèn vẫn vào [LampLive], đứt dây ⇒ `onHalDown`.
 */
class HalSignalClientEventsTest {

    @Test
    fun `gui sub khi noi, chuyen dong hal cho sink, dut day bao down`() {
        val server = ServerSocket(0)
        val gotSub = CopyOnWriteArrayList<String>()
        val serverDone = CountDownLatch(1)
        Thread({
            runCatching {
                server.accept().use { s ->
                    val r = s.getInputStream().bufferedReader()
                    gotSub += r.readLine()
                    val w = s.getOutputStream().bufferedWriter()
                    listOf(
                        """{"topic":"hal.hello","type":2}""",
                        """{"topic":"light.onLightOn","type":2}""",
                        """{"topic":"hal.ev","type":692060186,"dev":"d","v":1,"d":1.0}""",
                    ).forEach { w.write(it); w.write("\n") }
                    w.flush()
                    Thread.sleep(300)
                }
            }
            serverDone.countDown()
        }, "fake-helper").apply { isDaemon = true }.start()

        val live = LampLive()
        val msgs = CopyOnWriteArrayList<HalEventWire.Msg>()
        val downs = AtomicInteger()
        val gotEv = CountDownLatch(1)
        val client = HalSignalClient(port = server.localPort, live = live) { Thread.sleep(60_000) }
        client.onHal = { m -> msgs += m; if (m is HalEventWire.Msg.Event) gotEv.countDown() }
        client.onHalDown = { downs.incrementAndGet() }
        client.subscribe("sub android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice=692060186")   // trước khi nối: giữ lại
        client.start { _, _ -> }
        assertTrue(gotEv.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("sub android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice=692060186"), gotSub)
        assertEquals(listOf(HalEventWire.Msg.Hello(2), HalEventWire.Msg.Event(692060186, "d", 1, 1.0)), msgs.toList())
        assertEquals(true, live.state.value?.lowBeam, "dòng đèn vẫn đi đường cũ")
        assertTrue(serverDone.await(5, TimeUnit.SECONDS))
        val t0 = System.currentTimeMillis()
        while (downs.get() == 0 && System.currentTimeMillis() - t0 < 3000) Thread.sleep(20)
        assertTrue(downs.get() >= 1, "đứt dây ⇒ onHalDown (bộ nhớ sự kiện xoá, poll lại)")
        client.stop()
        server.close()
    }
}
