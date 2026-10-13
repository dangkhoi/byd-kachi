package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.HalSignalDemand.Source
import com.byd.clusternav.launcher.LampLive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 3.01 LAMPLIVE — [HalSignalHub] là chủ DUY NHẤT của helper + client 19322. Socket THẬT: một [ServerSocket] đóng vai helper (đếm số
 * kết nối), `ensure` giả đếm lời gọi. Khoá: camera + bảng Đèn chồng nhau = MỘT `ensure` + MỘT kết nối · thôi một nguồn không nhả ·
 * hết nguồn ⇒ nhả sau ân hạn (hộp về `null`, kết nối đóng) · quay lại trong hạn ⇒ không nối lại · helper chết (link đứt) ⇒ `ensure`
 * lại (khởi lại helper) · tiến trình app mới (hub mới) với helper còn sống ⇒ nối lại, không cần gì thêm · camera gán người nghe lúc
 * xi-nhan đang bật ⇒ nhận NGAY.
 *
 * Thử ĐỎ [ĐO 13/10]: bỏ `exec.execute { heal() }` (móc `onLinkLost`) ⇒ `helper chet` ĐỎ; bỏ phát lại trong setter `onTurn` ⇒
 * `camera gan nguoi nghe` ĐỎ.
 */
class HalSignalHubTest {

    /** Helper giả: chấp nhận nhiều client, gửi ảnh chụp xi-nhan; đếm kết nối; [dropAll] mô phỏng helper chết. */
    private class FakeHelper {
        val server = ServerSocket(0)
        val accepted = AtomicInteger(0)
        val open = CopyOnWriteArrayList<Socket>()
        @Volatile var left = false

        init {
            Thread({
                while (!server.isClosed) {
                    val s = runCatching { server.accept() }.getOrNull() ?: break
                    accepted.incrementAndGet()
                    open += s
                    runCatching {
                        val w = s.getOutputStream()
                        w.write(line(if (left) HalSignalClient.TOPIC_ON else HalSignalClient.TOPIC_OFF, 4).toByteArray())
                        w.write(line(HalSignalClient.TOPIC_OFF, 5).toByteArray())
                        w.flush()
                    }
                }
            }, "fake-kachi-hal-hub").apply { isDaemon = true; start() }
        }

        fun send(l: String) = open.forEach { s -> runCatching { s.getOutputStream().apply { write("$l\n".toByteArray()); flush() } } }
        fun dropAll() { open.forEach { runCatching { it.close() } }; open.clear() }
        fun close() { dropAll(); runCatching { server.close() } }
        private fun line(t: String, type: Int) = """{"topic":"$t","type":$type}""" + "\n"
    }

    private fun until(what: String, ms: Long = 5_000, ok: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms)
        while (System.nanoTime() < end) { if (ok()) return; Thread.sleep(5) }
        assertTrue(ok(), what)
    }

    private fun hub(h: FakeHelper, live: LampLive, ensures: AtomicInteger, grace: Long, healMs: Long = 0) = HalSignalHub(
        ensure = { ensures.incrementAndGet(); true },
        graceMs = grace,
        healMinIntervalMs = healMs,
        live = live,
        clientFactory = { lost -> HalSignalClient(port = h.server.localPort, live = live, sleeper = { Thread.sleep(50) }, onLinkLost = lost) },
    )

    @Test
    fun `camera va bang Den chong nhau - mot ensure mot ket noi, nha sau an han`() {
        val h = FakeHelper(); val live = LampLive(); val ensures = AtomicInteger()
        val hub = hub(h, live, ensures, grace = 300)
        try {
            hub.want(Source.CAMERA, true)
            hub.want(Source.LAMP_WIDGET, true)
            until("nối được") { live.state.value != null }
            Thread.sleep(150)
            assertEquals(1, ensures.get(), "MỘT ensure cho hai nguồn")
            assertEquals(1, h.accepted.get(), "MỘT kết nối")
            hub.want(Source.CAMERA, false)
            Thread.sleep(500)
            assertTrue(hub.isActive, "còn bảng Đèn ⇒ giữ")
            assertTrue(live.state.value != null)
            hub.want(Source.LAMP_WIDGET, false)
            assertTrue(hub.isActive, "ngay sau khi thôi: còn trong ân hạn")
            until("nhả sau ân hạn") { live.state.value == null && !hub.isActive }
            assertEquals(1, h.accepted.get())
        } finally { hub.want(Source.CAMERA, false); hub.want(Source.LAMP_WIDGET, false); h.close() }
    }

    @Test
    fun `quay lai trong an han - khong noi lai`() {
        val h = FakeHelper(); val live = LampLive(); val ensures = AtomicInteger()
        val hub = hub(h, live, ensures, grace = 2_000)
        try {
            hub.want(Source.LAMP_WIDGET, true)
            until("nối được") { live.state.value != null }
            hub.want(Source.LAMP_WIDGET, false)
            Thread.sleep(200)
            hub.want(Source.LAMP_WIDGET, true)
            Thread.sleep(2_300)
            assertEquals(1, ensures.get())
            assertEquals(1, h.accepted.get())
            assertTrue(live.state.value != null, "vẫn nối")
        } finally { hub.want(Source.LAMP_WIDGET, false); h.close() }
    }

    @Test
    fun `helper chet - link dut thi khoi lai helper`() {
        val h = FakeHelper(); val live = LampLive(); val ensures = AtomicInteger()
        val hub = hub(h, live, ensures, grace = 60_000)
        try {
            hub.want(Source.LAMP_WIDGET, true)
            until("nối được") { h.accepted.get() == 1 && live.state.value != null }
            h.dropAll()   // ROM giết app_process
            until("ensure lại sau khi link đứt") { ensures.get() >= 2 }
            until("nối lại") { h.accepted.get() >= 2 && live.state.value != null }
        } finally { hub.want(Source.LAMP_WIDGET, false); h.close() }
    }

    /**
     * [SOÁT Pass 2 · 13/10] Helper chết TRONG trần 30 s (ở đây trần 800 ms) sau lần `ensure` trước ⇒ khởi lại được HOÃN tới hết trần,
     * không bị vứt; nhiều lần đứt trong trần gộp thành MỘT `ensure`. Thử ĐỎ [ĐO 13/10]: trả `heal()` về bản `if (… < healMinIntervalMs)
     * return` ⇒ `ensures` kẹt ở 1 ⇒ ĐỎ.
     */
    @Test
    fun `helper chet trong tran - hoan chu khong bo, van mot lan moi tran`() {
        val h = FakeHelper(); val live = LampLive(); val ensures = AtomicInteger()
        val hub = hub(h, live, ensures, grace = 60_000, healMs = 800)
        try {
            hub.want(Source.LAMP_WIDGET, true)
            until("nối được") { h.accepted.get() == 1 && live.state.value != null }
            h.dropAll()                                        // chết ngay sau khi lên (< trần)
            until("nối lại lần 2") { h.accepted.get() >= 2 && h.open.isNotEmpty() }
            h.dropAll()                                        // chết lần nữa, vẫn trong trần
            Thread.sleep(150)
            assertEquals(1, ensures.get(), "trong trần: chưa ensure lại")
            until("hết trần ⇒ ensure lại (hoãn, không bỏ)") { ensures.get() == 2 }
            Thread.sleep(400)
            assertEquals(2, ensures.get(), "hai lần đứt trong trần ⇒ MỘT ensure")
        } finally { hub.want(Source.LAMP_WIDGET, false); h.close() }
    }

    @Test
    fun `tien trinh app moi - helper con song thi noi lai`() {
        val h = FakeHelper(); val ensures = AtomicInteger()
        val live1 = LampLive()
        val first = hub(h, live1, ensures, grace = 0)
        first.want(Source.CAMERA, true)
        until("hub 1 nối") { live1.state.value != null }
        first.want(Source.CAMERA, false)                 // tiến trình cũ chết ⇒ nhu cầu RAM mất
        until("hub 1 nhả") { live1.state.value == null }
        val live2 = LampLive()
        val second = hub(h, live2, ensures, grace = 0)   // tiến trình mới: hub mới, helper (server) vẫn sống
        try {
            second.want(Source.LAMP_WIDGET, true)
            until("hub 2 nối") { live2.state.value != null }
            assertEquals(2, h.accepted.get())
        } finally { second.want(Source.LAMP_WIDGET, false); h.close() }
    }

    @Test
    fun `camera gan nguoi nghe luc xi-nhan dang bat - nhan ngay`() {
        val h = FakeHelper().apply { left = true }; val live = LampLive(); val ensures = AtomicInteger()
        val hub = hub(h, live, ensures, grace = 0)
        try {
            hub.want(Source.LAMP_WIDGET, true)
            until("bảng thấy trái BẬT") { live.state.value?.left == true }
            val got = CopyOnWriteArrayList<Pair<Boolean, Boolean>>()
            hub.onTurn = { l, r -> got += l to r }
            assertEquals(listOf(true to false), got, "phát lại trạng thái đang biết")
            h.send("""{"topic":"light.onLightOff","type":4}""")
            until("OFF tới camera") { got.size == 2 }
            assertEquals(false to false, got[1])
        } finally { hub.want(Source.LAMP_WIDGET, false); h.close() }
    }

    @Test
    fun `khong ai can - khong ensure, khong ket noi`() {
        val h = FakeHelper(); val live = LampLive(); val ensures = AtomicInteger()
        val hub = hub(h, live, ensures, grace = 0)
        try {
            hub.want(Source.CAMERA, false)
            hub.want(Source.LAMP_WIDGET, false)
            Thread.sleep(200)
            assertEquals(0, ensures.get())
            assertEquals(0, h.accepted.get())
            assertNull(live.state.value)
            assertFalse(hub.isActive)
        } finally { h.close() }
    }
}
