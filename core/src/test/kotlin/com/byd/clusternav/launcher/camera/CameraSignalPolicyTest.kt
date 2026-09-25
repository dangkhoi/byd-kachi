package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Camera theo xi-nhan ([CameraSignalPolicy]) — luật thuần, off-car. */
class CameraSignalPolicyTest {

    @Test fun `xi nhan trai to camera trai + overlay ben trai`() {
        val t = CameraSignalPolicy.turnOf(left = true, right = false)
        assertEquals(CameraSignalPolicy.Turn.LEFT, t)
        assertEquals(CameraSignalPolicy.CamView.MIRROR_LEFT, CameraSignalPolicy.defaultView(t))
        assertEquals(CameraSignalPolicy.Side.LEFT, CameraSignalPolicy.defaultSide(t))
    }

    @Test fun `xi nhan phai to camera phai + overlay ben phai`() {
        val t = CameraSignalPolicy.turnOf(left = false, right = true)
        assertEquals(CameraSignalPolicy.Turn.RIGHT, t)
        assertEquals(CameraSignalPolicy.CamView.MIRROR_RIGHT, CameraSignalPolicy.defaultView(t))
        assertEquals(CameraSignalPolicy.Side.RIGHT, CameraSignalPolicy.defaultSide(t))
    }

    @Test fun `ca hai bat (den khan) khong mo camera`() {
        val t = CameraSignalPolicy.turnOf(left = true, right = true)
        assertEquals(CameraSignalPolicy.Turn.NONE, t)
        assertNull(CameraSignalPolicy.defaultView(t))
        assertNull(CameraSignalPolicy.defaultSide(t))
    }

    /** outputState là giá trị THẬT của HAL (RE) — ghim để không đổi bừa. */
    @Test fun `output state khop hang HAL`() {
        assertEquals(1, CameraSignalPolicy.CamView.FRONT_LEFT.outputState)
        assertEquals(2, CameraSignalPolicy.CamView.FRONT_RIGHT.outputState)
        assertEquals(13, CameraSignalPolicy.CamView.LEFT_FRONT.outputState)
        assertEquals(14, CameraSignalPolicy.CamView.RIGHT_FRONT.outputState)
    }
}
