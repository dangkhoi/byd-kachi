package com.byd.clusternav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AmapEmissionArbiterTest {
    @Test
    fun `fresh frame resets session then emits and heartbeats at exactly 400ms`() {
        val harness = Harness()
        val token = harness.source("gmaps", "s1", 1, "left")!!

        assertEquals(listOf(AmapEmissionKind.SESSION_RESET, AmapEmissionKind.SOURCE_FRAME), harness.kinds())
        harness.scheduler.advanceBy(399)
        assertEquals(2, harness.events.size)
        harness.scheduler.advanceBy(1)
        assertEquals(AmapEmissionKind.HEARTBEAT, harness.events.last().kind)
        assertEquals(token.generation, harness.events.last().token.generation)
        assertEquals("left", harness.events.last().payload)
    }

    @Test
    fun `gate ready adds one replay and coalesces the pending heartbeat`() {
        val harness = Harness()
        val token = harness.source("gmaps", "s1", 1, "right")!!
        harness.scheduler.advanceBy(100)

        assertTrue(harness.arbiter.forceReplay(7, token.sessionId, token.sourceSequence))
        assertFalse(harness.arbiter.forceReplay(7, token.sessionId, token.sourceSequence))
        assertEquals(1, harness.events.count { it.kind == AmapEmissionKind.FORCED_REPLAY })

        harness.scheduler.advanceBy(300)
        assertEquals(0, harness.events.count { it.kind == AmapEmissionKind.HEARTBEAT })
        harness.scheduler.advanceBy(100)
        assertEquals(1, harness.events.count { it.kind == AmapEmissionKind.HEARTBEAT })
    }

    @Test
    fun `source or session change resets before new frame and rejects foreign replay`() {
        val harness = Harness()
        val old = harness.source("gmaps", "s1", 1, "old")!!
        val next = harness.source("waze", "s2", 1, "new")!!

        assertEquals(
            listOf(
                AmapEmissionKind.SESSION_RESET,
                AmapEmissionKind.SOURCE_FRAME,
                AmapEmissionKind.SESSION_RESET,
                AmapEmissionKind.SOURCE_FRAME,
            ),
            harness.kinds(),
        )
        assertTrue(next.generation > old.generation)
        assertFalse(harness.arbiter.forceReplay(1, old.sessionId, old.sourceSequence))
        assertNull(harness.source("waze", "s2", 1, "duplicate"))
    }

    @Test
    fun `announced source change immediately cancels old heartbeat and foreign queued frame`() {
        val harness = Harness()
        val old = harness.source("gmaps", "s1", 1, "old")!!
        assertTrue(harness.arbiter.beginSession("waze", "s2"))
        harness.scheduler.advanceBy(400)
        assertEquals(0, harness.events.count { it.kind == AmapEmissionKind.HEARTBEAT })
        assertFalse(harness.arbiter.forceReplay(1, old.sessionId, old.sourceSequence))
        assertNull(harness.source("gmaps", "s1", 2, "late-old"))

        harness.source("waze", "s2", 1, "new")
        assertEquals(AmapEmissionKind.SESSION_RESET, harness.events[harness.events.lastIndex - 1].kind)
        assertEquals(AmapEmissionKind.SOURCE_FRAME, harness.events.last().kind)
        assertEquals("new", harness.events.last().payload)
    }

    @Test
    fun `stop invalidates pending heartbeat and tokens stay globally monotonic`() {
        val harness = Harness()
        harness.source("vietmap", "s3", 1, "frame")
        val stop = harness.arbiter.stop("vietmap", "s3", "stop")
        harness.scheduler.advanceBy(2_000)

        assertEquals(AmapEmissionKind.STOP, harness.events.last().kind)
        assertEquals(stop, harness.events.last().token)
        assertEquals(0, harness.events.count { it.kind == AmapEmissionKind.HEARTBEAT })
        val tokens = harness.events.map { it.token.emissionToken }
        assertEquals(tokens.sorted(), tokens)
        assertEquals(tokens.distinct().size, tokens.size)
        assertNull(harness.arbiter.latestToken())
    }

    @Test
    fun `stale frame self heals with one stop and cannot heartbeat afterward`() {
        val harness = Harness(freshForMs = 800)
        harness.source("gmaps", "s1", 1, "frame")
        harness.scheduler.advanceBy(800)
        harness.scheduler.advanceBy(800)

        assertEquals(1, harness.events.count { it.kind == AmapEmissionKind.HEARTBEAT })
        assertEquals(1, harness.events.count { it.kind == AmapEmissionKind.STOP })
        assertEquals(AmapEmissionKind.STOP, harness.events.last().kind)
    }

    private class Harness(private val freshForMs: Long = 10_000L) {
        val scheduler = ManualScheduler()
        val events = mutableListOf<AmapEmission<String>>()
        val arbiter = AmapEmissionArbiter(
            scheduler = scheduler,
            sink = events::add,
            monotonicNowMs = scheduler::now,
        )

        fun source(source: String, session: String, sequence: Long, payload: String) =
            arbiter.sourceFrame(source, session, sequence, scheduler.now(), freshForMs, payload)

        fun kinds() = events.map { it.kind }
    }

    private class ManualScheduler : AmapEmissionScheduler {
        private data class Task(val dueAt: Long, val action: () -> Unit, var cancelled: Boolean = false)
        private val tasks = mutableListOf<Task>()
        private var clock = 0L

        fun now(): Long = clock

        override fun schedule(delayMs: Long, action: () -> Unit): AmapScheduledTask {
            val task = Task(clock + delayMs, action)
            tasks += task
            return AmapScheduledTask { task.cancelled = true }
        }

        fun advanceBy(deltaMs: Long) {
            val target = clock + deltaMs
            while (true) {
                val next = tasks.filter { !it.cancelled && it.dueAt <= target }.minByOrNull { it.dueAt }
                    ?: break
                tasks.remove(next)
                clock = next.dueAt
                next.action()
            }
            clock = target
        }
    }
}
