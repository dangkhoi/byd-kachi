package com.byd.clusternav.system.inputd

import java.util.concurrent.Executor
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [InputDaemonClient] — the :app lifecycle/routing brain. Verifies (with a FAKE [DaemonChannel] + fake shell +
 * synchronous executors, no device):
 *  • daemon DOWN → [InputDaemonClient.sendTouch] returns false (caller falls back to `input -d`) AND the ONLY
 *    thing sent through the command queue is the exact daemon-launch command (never a touch),
 *  • daemon UP → sendTouch returns true and the compact frame is written over the SOCKET (data path ≠ queue),
 *  • an already-running daemon is reused with no relaunch,
 *  • a write failure marks the daemon down so the next touch falls back,
 *  • start attempts are throttled within the cooldown.
 */
class InputDaemonClientTest {

    /** Runs the submitted work inline so the async lifecycle/sender paths are deterministic in the test. */
    private val direct = Executor { it.run() }

    /** Fake data-channel: connect() replies from a script; write() records bytes and reports [writeResult]. */
    private class FakeChannel(
        private val connectScript: MutableList<Boolean>,
        var writeResult: Boolean = true,
        private val error: String? = "ConnectException: Connection refused",
    ) : DaemonChannel {
        val writes = mutableListOf<ByteArray>()
        var connectCalls = 0
        var closes = 0
        override fun connect(): Boolean {
            connectCalls++
            return if (connectScript.isNotEmpty()) connectScript.removeAt(0) else false
        }
        override fun write(frame: ByteArray): Boolean { writes += frame; return writeResult }
        override fun close() { closes++ }
        override fun lastError(): String? = error
    }

    /**
     * Lệnh khởi động MONG ĐỢI của một lượt test: máy giả trả `""` cho lượt dò `nohup` ⇒ `hasNohup` = false ⇒
     * biến thể `… &` (xem KDoc [InputDaemonLaunch.hasNohup] — "không rõ" thì KHÔNG dùng nohup).
     */
    private fun expectedLaunch(logPath: String? = null) =
        InputDaemonLaunch.launchCmd("/x/base.apk", "kachi_input", logPath, useNohup = false)

    /** Chỉ những lệnh KHỞI ĐỘNG (bỏ lượt dò `nohup`) — cái mà các bài dưới đây thật sự nói về. */
    private fun launchesOnly(all: List<String>) = all.filter { it.startsWith("CLASSPATH=") }

    private fun client(
        fake: FakeChannel,
        launches: MutableList<String>,
        connectTries: Int = 2,
        logDir: String? = null,
        disabled: () -> Boolean = { false },
    ) = InputDaemonClient(
        apkPath = "/x/base.apk",
        launchShell = { launches += it; "" },
        socketName = "kachi_input",
        channelFactory = { fake },
        lifecycleExecutor = direct,
        senderExecutor = direct,
        sleep = {},
        now = { 1_000L },          // constant clock → cooldown throttle is deterministic
        connectTries = connectTries,
        connectStepMs = 0L,
        retryCooldownMs = 3_000L,
        logDir = { logDir },
        disabled = disabled,
        log = {},
    )

    @Test
    fun `daemon down - sendTouch falls back and issues ONLY the exact launch command on the queue`() {
        val fake = FakeChannel(connectScript = mutableListOf())   // connect always false
        val launches = mutableListOf<String>()
        val c = client(fake, launches)

        val routed = c.sendTouch(displayId = 7, action = 0, x = 640, y = 360)

        assertFalse(routed, "daemon unavailable → sendTouch must return false so caller runs input -d")
        assertFalse(c.isHealthy())
        assertEquals(
            listOf(expectedLaunch()),
            launchesOnly(launches),
            "the ONLY command on the queue is the daemon lifecycle launch (exact string)",
        )
        assertEquals(
            listOf(InputDaemonLaunch.WHICH_NOHUP),
            launches.filterNot { it.startsWith("CLASSPATH=") },
            "lệnh phụ DUY NHẤT được phép là lượt dò `nohup` (1.69) — không có gì khác lọt vào hàng đợi",
        )
        assertTrue(fake.writes.isEmpty(), "no touch bytes should have been written when down")
    }

    @Test
    fun `daemon up (cold start) - first touch falls back, then touch is routed over the socket not the queue`() {
        val fake = FakeChannel(connectScript = mutableListOf(false, true))  // fail once, then connect after launch
        val launches = mutableListOf<String>()
        val c = client(fake, launches)

        val first = c.sendTouch(displayId = 1, action = 0, x = 10, y = 20)   // triggers cold start (connects)
        val second = c.sendTouch(displayId = 1, action = 1, x = 30, y = 40)  // now healthy → socket

        assertFalse(first, "first touch during cold start falls back")
        assertTrue(second, "once connected, touch is taken by the daemon")
        assertTrue(c.isHealthy())
        // Lifecycle launch went through the queue exactly once; NO touch command ever hit the queue.
        assertEquals(listOf(expectedLaunch()), launchesOnly(launches))
        assertTrue(launches.none { it.startsWith("input ") }, "touch must never ride the command queue")
        // The touch frame was delivered over the SOCKET (its own data path).
        assertEquals(1, fake.writes.size)
        assertArrayEquals(InputWireProtocol.encode(TouchFrame(1, 1, 30, 40)), fake.writes[0])
    }

    @Test
    fun `an already-running daemon is reused with no relaunch`() {
        val fake = FakeChannel(connectScript = mutableListOf(true))   // connects on the first probe
        val launches = mutableListOf<String>()
        val c = client(fake, launches)

        c.sendTouch(1, 0, 5, 5)                       // connects to the resident daemon
        val routed = c.sendTouch(1, 1, 5, 5)

        assertTrue(routed)
        assertTrue(launches.isEmpty(), "resident daemon already up → must NOT relaunch it (kể cả lượt dò `nohup`)")
        assertEquals(1, fake.writes.size)
    }

    @Test
    fun `a write failure marks the daemon down so the next touch falls back`() {
        val fake = FakeChannel(connectScript = mutableListOf(true), writeResult = false)
        val launches = mutableListOf<String>()
        val c = client(fake, launches)

        c.sendTouch(1, 0, 5, 5)                       // connect
        val second = c.sendTouch(1, 1, 5, 5)          // enqueues a write that fails → marks down
        val third = c.sendTouch(1, 1, 6, 6)

        assertTrue(second, "the event was accepted for delivery")
        assertFalse(c.isHealthy(), "a failed socket write marks the daemon unhealthy")
        assertFalse(third, "the next touch must fall back after the daemon dropped")
    }

    @Test
    fun `start attempts are throttled within the cooldown`() {
        val fake = FakeChannel(connectScript = mutableListOf())   // never connects
        val launches = mutableListOf<String>()
        val c = client(fake, launches)

        c.sendTouch(1, 0, 5, 5)
        c.sendTouch(1, 0, 6, 6)
        c.sendTouch(1, 0, 7, 7)

        assertEquals(1, launchesOnly(launches).size, "within the cooldown the daemon launch must be issued only once")
    }

    // ═══ 1.69 — ĐẦU DÒ "vì sao daemon không lên" ═════════════════════════════════════════════════════════════

    /**
     * KHOÁ: stdout+stderr của daemon đi vào tệp THẬT khi có thư mục log.
     *
     * Bản trước ném cả hai vào `/dev/null`, và đó đúng là lý do sau 4 lượt xe vẫn [CHƯA BIẾT] vì sao daemon
     * không lên ([ĐO xe 2026-09-16] §9.1).
     */
    @Test
    fun `nhat ky daemon di vao tep that khi co thu muc log`() {
        val fake = FakeChannel(connectScript = mutableListOf())
        val launches = mutableListOf<String>()
        val dir = "/sdcard/Android/data/com.byd.launcher/files/kachi-logs"
        client(fake, launches, logDir = dir).sendTouch(1, 0, 5, 5)

        val cmd = launchesOnly(launches).single()
        assertTrue(
            cmd.contains(">$dir/${InputDaemonLaunch.logFileName(1_000L)} 2>&1"),
            "lệnh khởi động phải đổ stdout+stderr vào kachi-logs/inputd-<stamp>.log — thấy: $cmd",
        )
        assertFalse(cmd.contains(">/dev/null 2>&1"), "không được còn đường nào ném bằng chứng vào /dev/null")
    }

    /** KHOÁ: không có thẻ (logDir = null) ⇒ quay về đúng hành vi cũ, KHÔNG ném lỗi, chạm vẫn có đường lùi. */
    @Test
    fun `khong co the nho thi ghi ra dev null nhu cu`() {
        val fake = FakeChannel(connectScript = mutableListOf())
        val launches = mutableListOf<String>()
        client(fake, launches, logDir = null).sendTouch(1, 0, 5, 5)
        assertTrue(launchesOnly(launches).single().endsWith(">/dev/null 2>&1 &"))
    }

    /** KHOÁ: lượt dò `nohup` chạy ĐÚNG MỘT lần mỗi tiến trình (không phải mỗi lượt khởi động). */
    @Test
    fun `luot do nohup chi chay mot lan`() {
        val fake = FakeChannel(connectScript = mutableListOf())
        val launches = mutableListOf<String>()
        val c = InputDaemonClient(
            apkPath = "/x/base.apk",
            launchShell = { launches += it; "" },
            channelFactory = { fake },
            lifecycleExecutor = direct,
            senderExecutor = direct,
            sleep = {},
            now = { launches.size * 10_000L },   // đồng hồ chạy ⇒ cooldown KHÔNG chặn lượt khởi động thứ hai
            connectTries = 1,
            connectStepMs = 0L,
            log = {},
        )
        c.sendTouch(1, 0, 5, 5)
        c.sendTouch(1, 0, 6, 6)
        assertEquals(2, launchesOnly(launches).size, "hai lượt khởi động (đồng hồ đã qua cooldown)")
        assertEquals(1, launches.count { it == InputDaemonLaunch.WHICH_NOHUP }, "nhưng chỉ MỘT lượt dò `nohup`")
    }

    /** KHOÁ: ảnh chụp cho cầu kiểm thử mang đủ ba thứ owner cần đọc từ xe: khoẻ · lý do · số lượt. */
    @Test
    fun `anh chup mang healthy lastError va so luot`() {
        val fake = FakeChannel(connectScript = mutableListOf())
        val launches = mutableListOf<String>()
        client(fake, launches, connectTries = 3).sendTouch(1, 0, 5, 5)

        val snap = InputDaemonClient.lastSnapshot()
        assertFalse(snap.healthy)
        assertEquals("ConnectException: Connection refused", snap.lastError, "lý do nguyên văn của nền tảng")
        assertEquals(3, snap.attempts, "đếm đủ số lượt đã thử")
    }

    /**
     * KHOÁ: công tắc ẩn `inputd_disabled` ⇒ **không** khởi daemon, **không** nối, chạm đi thẳng đường lùi.
     *
     * Đây là thứ duy nhất cho phép máy ảo (nơi daemon lên bình thường) diễn đúng nhánh mà xe đang mắc kẹt.
     */
    @Test
    fun `inputd_disabled ep duong lui va khong dung toi shell`() {
        val fake = FakeChannel(connectScript = mutableListOf(true))   // kênh SẼ nối được nếu ai đó thử
        val launches = mutableListOf<String>()
        val c = client(fake, launches, disabled = { true })

        assertFalse(c.sendTouch(1, 0, 5, 5), "phải trả false ⇒ caller chạy đường lùi theo cử chỉ")
        assertFalse(c.isHealthy())
        assertTrue(launches.isEmpty(), "không một lệnh shell nào — kể cả lượt dò `nohup`")
        assertEquals(0, fake.connectCalls, "không được thử nối socket")
        assertEquals("disabled_by_pref", InputDaemonClient.lastSnapshot().lastError)
    }

    /**
     * KHOÁ [SOÁT 1.69 · P3]: `close()` KHÔNG được dán nhãn *"socket write failed"* lên [InputDaemonClient.Health.lastError].
     *
     * Ô ấy là **cả mục đích** của lượt 1.69 (bốn lượt xe trôi qua vì không ai biết daemon hỏng ở đâu — xem
     * `docs/diagnostics/inputd-selinux-and-gesture-fallback-2026-09-17.md` §1). `close()` đi chung đường
     * `markDown` với lượt ghi socket hỏng, nên bản trước ghi đè đúng câu chữ nguyên văn vừa đo được
     * (`"IOException: Permission denied"` = sepolicy) bằng một lý do SAI, ngay trước lúc owner đọc nó.
     *
     * Gỡ tham số `reason` của `markDown` ra (quay về hằng cũ) thì ca này ĐỎ.
     */
    @Test
    fun `close khong bia ra ly do ghi socket hong`() {
        val fake = FakeChannel(connectScript = mutableListOf(), error = "IOException: Permission denied")
        val c = client(fake, mutableListOf(), connectTries = 1)
        c.sendTouch(1, 0, 5, 5)                                   // thử nối, hỏng vì sepolicy
        assertEquals("IOException: Permission denied", InputDaemonClient.lastSnapshot().lastError)

        c.close()
        assertFalse(
            InputDaemonClient.lastSnapshot().lastError == "socket write failed",
            "close() không ghi socket lần nào — dán nhãn ghi-hỏng lên là bịa ra một chẩn đoán",
        )
    }

    /** KHOÁ (soát OCR #70): `close()` dừng cả hai executor của chính nó, không chỉ đóng socket. */
    @Test
    fun `close dung hai executor cua chinh no`() {
        val life = java.util.concurrent.Executors.newSingleThreadExecutor()
        val send = java.util.concurrent.Executors.newSingleThreadExecutor()
        val c = InputDaemonClient(
            apkPath = "/x/base.apk",
            launchShell = { "" },
            channelFactory = { FakeChannel(mutableListOf(true)) },
            lifecycleExecutor = life,
            senderExecutor = send,
            sleep = {},
            log = {},
        )
        c.close()
        assertTrue(life.isShutdown, "lifecycleExecutor phải được shutdown")
        assertTrue(send.isShutdown, "senderExecutor phải được shutdown")
    }
}
