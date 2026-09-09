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
    }

    private fun client(
        fake: FakeChannel,
        launches: MutableList<String>,
        connectTries: Int = 2,
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
            listOf(InputDaemonLaunch.launchCmd("/x/base.apk", "kachi_input")),
            launches,
            "the ONLY command on the queue is the daemon lifecycle launch (exact string)",
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
        assertEquals(listOf(InputDaemonLaunch.launchCmd("/x/base.apk", "kachi_input")), launches)
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
        assertTrue(launches.isEmpty(), "resident daemon already up → must NOT relaunch it")
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

        assertEquals(1, launches.size, "within the cooldown the daemon launch must be issued only once")
    }
}
