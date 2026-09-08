package com.byd.clusternav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HudRoadCapabilityTest {
    @Test
    fun `Seal HUD and road remain UNKNOWN and requests are no-op`() {
        val controller = HudMirrorController { 123L }

        assertEquals(HudMirrorRequestResult.NO_OP_UNKNOWN, controller.requestEnabled(true))
        val snapshot = controller.snapshot()
        assertEquals(HudMirrorCapability.UNKNOWN, snapshot.capability)
        assertEquals(HudRoadCapability.UNKNOWN, snapshot.roadCapability)
        assertEquals(0, snapshot.physicalWriteCount)
        assertNull(snapshot.latestCanonicalFrame)
    }

    @Test
    fun `canonical Amap token is observed without becoming HUD display proof`() {
        val controller = HudMirrorController { 123L }
        val token = AmapFrameToken(2, 7, "gmaps", "session", 4)
        controller.onCanonicalFrame(token)

        val snapshot = controller.snapshot()
        assertEquals(token, snapshot.latestCanonicalFrame)
        assertEquals(HudMirrorCapability.UNKNOWN, snapshot.capability)
        assertEquals(HudRoadCapability.UNKNOWN, snapshot.roadCapability)
        assertEquals(0, snapshot.physicalWriteCount)
    }
}
