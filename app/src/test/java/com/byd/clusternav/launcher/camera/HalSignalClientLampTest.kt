package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.LampLive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 3.01 LAMPLIVE — client hiểu thêm dòng đèn KHÁC xi-nhan (cùng hợp đồng dây, `type` 1/2/3/6/7 + `light.dayRunning`), phát vào
 * [LampLive], và KHÔNG đánh thức camera ([HalSignalClient.start] `onTurn`) vì đèn cốt/pha. Dòng lạ bỏ, không đoán.
 *
 * Thử ĐỎ [ĐO 13/10]: đổi `if (changed)` thành luôn gọi `onTurn` ⇒ `socket - den cot vao hop, onTurn chi khi xi-nhan doi` ĐỎ.
 */
class HalSignalClientLampTest {

    private val z = LampLive.Lamps(left = false, right = false)

    @Test
    fun `applyLine - thang type va DRL`() {
        assertEquals(true, HalSignalClient.applyLine(z, HalSignalClient.TOPIC_ON, 2)?.lowBeam)
        assertEquals(false, HalSignalClient.applyLine(z, HalSignalClient.TOPIC_OFF, 3)?.highBeam)
        assertEquals(true, HalSignalClient.applyLine(z, HalSignalClient.TOPIC_ON, 7)?.rearFog)
        assertEquals(true, HalSignalClient.applyLine(z, HalSignalClient.TOPIC_ON, 4)?.left)
        assertEquals(true, HalSignalClient.applyLine(z, HalSignalClient.TOPIC_DRL, 1)?.drl)
        assertEquals(false, HalSignalClient.applyLine(z, HalSignalClient.TOPIC_DRL, 2)?.drl)
        assertNull(HalSignalClient.applyLine(z, HalSignalClient.TOPIC_DRL, 0), "INVALID ⇒ bỏ")
        assertNull(HalSignalClient.applyLine(z, HalSignalClient.TOPIC_ON, 8), "LIGHT_FOOT/ngoài thang ⇒ bỏ")
        assertNull(HalSignalClient.applyLine(z, "light.other", 2))
    }

    @Test
    fun `socket - den cot vao hop, onTurn chi khi xi-nhan doi`() {
        val server = ServerSocket(0)
        val go = CountDownLatch(1)
        Thread({
            runCatching {
                server.accept().use { s ->
                    val w = s.getOutputStream().bufferedWriter()
                    for (l in listOf(
                        """{"topic":"light.onLightOn","type":2}""",
                        """{"topic":"light.dayRunning","type":1}""",
                        """{"topic":"light.onLightOn","type":5}""",
                    )) { w.write(l); w.write("\n") }
                    w.flush()
                    go.await(5, TimeUnit.SECONDS)
                }
            }
        }, "fake-kachi-hal-lamp").apply { isDaemon = true; start() }
        val live = LampLive()
        val turns = CopyOnWriteArrayList<Pair<Boolean, Boolean>>()
        val client = HalSignalClient(port = server.localPort, live = live) { Thread.sleep(60_000) }
        try {
            client.start { l, r -> turns += l to r }
            val want = LampLive.Lamps(left = false, right = true, lowBeam = true, drl = true)
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < end && live.state.value != want) Thread.sleep(5)
            assertEquals(want, live.state.value)
            assertEquals(listOf(false to true), turns, "camera chỉ nghe xi-nhan — đèn cốt/DRL không gọi onTurn")
        } finally {
            go.countDown()
            client.stop()
            runCatching { server.close() }
        }
        assertNull(live.state.value)
        assertTrue(turns.size == 1)
    }
}
