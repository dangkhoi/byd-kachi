package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Khoá lưới an toàn "KHÔNG treo overlay" của [HalSignalClient] (soát Pass 2 · 2026-09-26).
 *
 * Bối cảnh THẬT: từ 2.70 `CameraHold` giữ camera theo **trạng thái** tới khi thấy một sự kiện OFF, vì
 * [ĐO xe 2026-09-26] helper HAL báo một `trái=true` lúc bật và một `trái=false` lúc tắt (4,2 s sau), không nháy
 * theo bóng đèn. Hệ quả: nếu helper **chết giữa lúc ON** thì sự kiện OFF không bao giờ tới, `CameraHold` không hẹn
 * hết hạn cho bên đang ON ⇒ overlay camera nằm trên màn tới khi tắt máy. Bài này dùng socket THẬT (một
 * [ServerSocket] đóng vai helper rồi tự ngắt) để chứng minh client tự báo `(false,false)` khi đứt dây.
 *
 * Thử ĐỎ [ĐO 2026-09-26]: bỏ lời gọi `announceOffIfDropped` trong `finally` của `loop` ⇒ bài 1 ĐỎ
 * (`AssertionFailedError: đứt dây giữa lúc ON phải báo TẮT cả hai bên`, 2 tests 1 fail). Bài 2 xanh ở cả hai bên
 * của mutation — đó là vai của nó: chặn hướng vá NGƯỢC (báo `(false,false)` mỗi lần đứt dây kể cả khi đã OFF).
 */
class HalSignalClientDropTest {

    private fun line(topic: String, type: Int) = """{"topic":"$topic","type":$type}"""

    /** Helper giả: gửi [lines] rồi **đóng** kết nối (mô phỏng bị ROM giết). Trả cổng đang lắng nghe. */
    private fun fakeHelper(vararg lines: String, accepts: Int = 1): Pair<ServerSocket, Thread> {
        val server = ServerSocket(0)
        val t = Thread({
            repeat(accepts) {
                runCatching {
                    server.accept().use { s ->
                        val w = s.getOutputStream().bufferedWriter()
                        lines.forEach { w.write(it); w.write("\n") }
                        w.flush()
                    }   // use{} đóng socket ⇒ client thấy readLine() = null
                }
            }
        }, "fake-kachi-hal").apply { isDaemon = true; start() }
        return server to t
    }

    @Test
    fun `helper chet giua luc ON - client tu bao TAT ca hai ben`() {
        val (server, _) = fakeHelper(line(HalSignalClient.TOPIC_ON, HalSignalClient.TYPE_LEFT))
        val seen = LinkedBlockingQueue<Pair<Boolean, Boolean>>()
        val client = HalSignalClient(port = server.localPort) { /* không ngủ thật giữa hai lần nối */ }
        try {
            client.start { l, r -> seen.add(l to r) }
            assertEquals(true to false, seen.poll(5, TimeUnit.SECONDS), "ảnh chụp ON của bên trái phải tới")
            assertEquals(
                false to false, seen.poll(5, TimeUnit.SECONDS),
                "đứt dây giữa lúc ON phải báo TẮT cả hai bên — không có nó thì CameraHold (2.70) giữ camera mãi",
            )
        } finally {
            client.stop()
            runCatching { server.close() }
        }
    }

    @Test
    fun `nguon bao ON roi OFF dung luat - dut day sau OFF khong sinh them nhip`() {
        // Helper sống đủ lâu để gửi cả ON và OFF; đứt dây sau OFF ⇒ hai cờ đã false ⇒ KHÔNG báo lại (không nhịp rác).
        val (server, _) = fakeHelper(
            line(HalSignalClient.TOPIC_ON, HalSignalClient.TYPE_RIGHT),
            line(HalSignalClient.TOPIC_OFF, HalSignalClient.TYPE_RIGHT),
            accepts = 2,
        )
        val seen = LinkedBlockingQueue<Pair<Boolean, Boolean>>()
        val second = CountDownLatch(2)
        val client = HalSignalClient(port = server.localPort) { }
        try {
            client.start { l, r -> seen.add(l to r); second.countDown() }
            assertTrue(second.await(5, TimeUnit.SECONDS), "phải nhận đủ ON rồi OFF")
            assertEquals(false to true, seen.poll())
            assertEquals(false to false, seen.poll())
            // Lần nối thứ hai (helper lên lại) gửi đúng hai dòng ấy ⇒ nhịp tiếp theo phải là ON lại, KHÔNG phải
            // một `(false,false)` thừa sinh ra từ lần đứt dây trước.
            assertEquals(false to true, seen.poll(5, TimeUnit.SECONDS), "nối lại ⇒ ảnh chụp ON, không có nhịp rác")
        } finally {
            client.stop()
            runCatching { server.close() }
        }
    }
}
