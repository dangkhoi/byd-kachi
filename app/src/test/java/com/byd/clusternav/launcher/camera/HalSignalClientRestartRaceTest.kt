package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.LampLive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * [SOÁT Pass 2 · 13/10] `stop()` rồi `start()` nhanh (hub nhả rồi xin lại) KHÔNG được để luồng đọc CŨ sống tiếp song song luồng mới.
 *
 * Dựng điểm đua một cách TẤT ĐỊNH: `onTurn` của lượt chạy cũ kẹt (và nuốt cờ ngắt — bên nhận ngoài tầm kiểm soát của client) trong
 * lúc test `stop()` + `start()`; thả ra rồi đo. Luồng cũ phải thoát ngay: không `onLinkLost` giả, không nối thêm, không phát vào
 * [LampLive] đè luồng mới; `stop()` lần hai dừng được mọi luồng `KachiHalSignal`.
 *
 * Thử ĐỎ [ĐO 13/10]: trả `current()` về `running` (vòng đọc chỉ hỏi cờ chung) ⇒ `onLinkLost` = 1 và số kết nối > 2 ⇒ ĐỎ.
 */
class HalSignalClientRestartRaceTest {

    private fun line(type: Int) = """{"topic":"${HalSignalClient.TOPIC_ON}","type":$type}""" + "\n"

    private fun readers(): Set<Thread> = Thread.getAllStackTraces().keys.filter { it.name == "KachiHalSignal" && it.isAlive }.toSet()

    /** Luồng đọc còn sống do CHÍNH bài này dựng (trừ luồng sót của bài khác chạy cùng JVM). */
    private fun mine(baseline: Set<Thread>) = (readers() - baseline).size

    private fun until(what: String, ms: Long = 5_000, ok: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms)
        while (System.nanoTime() < end) { if (ok()) return; Thread.sleep(5) }
        assertTrue(ok(), what)
    }

    @Test
    fun `stop roi start nhanh - luong cu thoat, khong phat song song`() {
        val server = ServerSocket(0)
        val accepted = AtomicInteger(0)
        val open = CopyOnWriteArrayList<Socket>()
        Thread({
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                val n = accepted.incrementAndGet()
                open += s
                // Kết nối 1: xi-nhan TRÁI bật (kích onTurn cũ) · kết nối sau: xi-nhan PHẢI bật.
                runCatching { s.getOutputStream().apply { write(line(if (n == 1) 4 else 5).toByteArray()); flush() } }
            }
        }, "fake-kachi-hal-race").apply { isDaemon = true; start() }

        val baseline = readers()
        val live = LampLive()
        val lost = AtomicInteger(0)
        val client = HalSignalClient(port = server.localPort, live = live, onLinkLost = { lost.incrementAndGet() }) { Thread.sleep(30) }
        val inTurn = CountDownLatch(1)
        val gate = CountDownLatch(1)
        try {
            client.start { _, _ ->
                inTurn.countDown()
                // Kẹt và NUỐT cờ ngắt (bên nhận thật có thể làm vậy) — đúng điểm đua.
                while (gate.count > 0) runCatching { gate.await(5, TimeUnit.SECONDS) }
            }
            assertTrue(inTurn.await(5, TimeUnit.SECONDS), "onTurn cũ đã chạy")
            client.stop()
            client.start { _, _ -> }
            val newest = LampLive.Lamps(left = false, right = true)
            until("luồng mới nối + phát") { accepted.get() == 2 && live.state.value == newest }

            gate.countDown()                                   // thả luồng cũ
            Thread.sleep(400)                                  // > 10 nhịp ngủ 30 ms: luồng cũ chạy tiếp thì đã nối lại
            assertEquals(0, lost.get(), "luồng cũ không được báo onLinkLost giả (hub sẽ khởi lại helper vô cớ)")
            assertEquals(2, accepted.get(), "luồng cũ không được nối thêm")
            assertEquals(newest, live.state.value, "luồng cũ không được phát đè luồng mới")
            assertEquals(1, mine(baseline), "chỉ còn MỘT luồng đọc")

            client.stop()
            until("stop() dừng mọi luồng đọc") { mine(baseline) == 0 }
        } finally {
            gate.countDown()
            client.stop()
            open.forEach { runCatching { it.close() } }
            runCatching { server.close() }
        }
    }
}
