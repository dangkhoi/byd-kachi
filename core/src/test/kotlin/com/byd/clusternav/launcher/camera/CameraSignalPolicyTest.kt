package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    // ══ R7 · XOAY video (owner 2026-09-26: "bên trái rotation 90 xoay qua trái, bên phải xoay sang phải") ═══════

    /** Bảng đủ 5 chế độ × 2 bên. Âm = ↺ (qua trái), dương = ↻ (sang phải) — đúng chiều `Matrix.postRotate`. */
    @Test fun `bang xoay 5 che do x 2 ben`() {
        val P = CameraSignalPolicy
        val L = CameraSignalPolicy.Turn.LEFT
        val R = CameraSignalPolicy.Turn.RIGHT
        // Theo bên: trái −90 (↺), phải +90 (↻) — đúng nguyên văn owner.
        assertEquals(-90, P.rotationDegrees(P.ROTATE_BY_SIDE, L))
        assertEquals(90, P.rotationDegrees(P.ROTATE_BY_SIDE, R))
        assertEquals(0, P.rotationDegrees(P.ROTATE_NONE, L))
        assertEquals(0, P.rotationDegrees(P.ROTATE_NONE, R))
        assertEquals(-90, P.rotationDegrees(P.ROTATE_LEFT, L))
        assertEquals(-90, P.rotationDegrees(P.ROTATE_LEFT, R))
        assertEquals(90, P.rotationDegrees(P.ROTATE_RIGHT, L))
        assertEquals(90, P.rotationDegrees(P.ROTATE_RIGHT, R))
        assertEquals(180, P.rotationDegrees(P.ROTATE_180, L))
        assertEquals(180, P.rotationDegrees(P.ROTATE_180, R))
        // Theo bên NGƯỢC LẠI (review Pass 1): đường HOÀN TÁC mà §Verification của spec hứa — nếu cặp theo bên bị
        // ngược trên xe thì `↺90`/`↻90` (áp cả hai bên) KHÔNG diễn tả nổi, phải có cặp đảo.
        assertEquals(90, P.rotationDegrees(P.ROTATE_BY_SIDE_INV, L))
        assertEquals(-90, P.rotationDegrees(P.ROTATE_BY_SIDE_INV, R))
        // Và nó đúng là NGƯỢC của mặc định ở cả hai bên (không phải "khác một chút").
        listOf(L, R).forEach { t ->
            assertEquals(
                -P.rotationDegrees(P.ROTATE_BY_SIDE, t), P.rotationDegrees(P.ROTATE_BY_SIDE_INV, t),
                "SIDEINV phải là số đối của SIDE ở bên $t",
            )
        }
    }

    /** Mặc định = theo bên (owner 2026-09-26), và mode lạ trên đĩa rơi về mặc định — KHÔNG về 0 (0 là lựa chọn thật). */
    @Test fun `mac dinh theo ben, mode la roi ve mac dinh`() {
        val P = CameraSignalPolicy
        assertEquals(P.ROTATE_BY_SIDE, P.defaultRotation())
        assertEquals(-90, P.rotationDegrees("TOPLEFT", CameraSignalPolicy.Turn.LEFT))
        assertEquals(90, P.rotationDegrees("", CameraSignalPolicy.Turn.RIGHT))
        assertEquals(0, P.rotationDegrees(P.ROTATE_BY_SIDE, CameraSignalPolicy.Turn.NONE))
    }

    /** `isRotation` nhận đúng 6 mã, mã chip = giá trị lưu bền (không có bảng đổi thứ hai). */
    @Test fun `isRotation nhan dung 6 ma`() {
        val P = CameraSignalPolicy
        assertEquals(6, P.ROTATIONS.size)
        assertEquals(6, P.ROTATIONS.toSet().size)
        assertTrue(P.ROTATIONS.all(P::isRotation))
        assertFalse(P.isRotation("90"))
        assertFalse(P.isRotation("SIDE_INV"))   // mã thật là "SIDEINV" — prefs_set chỉ nhận đúng mã
        assertFalse(P.isRotation("side"))   // phân biệt hoa/thường: prefs_set chuẩn hoá trước khi ghi
        assertFalse(P.isRotation(""))
    }

    /** outputState là giá trị THẬT của HAL (RE) — ghim để không đổi bừa. */
    @Test fun `output state khop hang HAL`() {
        assertEquals(1, CameraSignalPolicy.CamView.FRONT_LEFT.outputState)
        assertEquals(2, CameraSignalPolicy.CamView.FRONT_RIGHT.outputState)
        assertEquals(13, CameraSignalPolicy.CamView.LEFT_FRONT.outputState)
        assertEquals(14, CameraSignalPolicy.CamView.RIGHT_FRONT.outputState)
    }
}
