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

    // ══ R7 · XOAY video — 2.71 GÓC THEO BÊN (owner trên xe 2026-09-26: "2 line setting độc lập cho camera trái và
    // phải, có thể 2 camera cần xoay khác nhau") ═══════════════════════════════════════════════════════════════

    private val P = CameraSignalPolicy

    /** Bảng đủ 4 mã × 2 bên: mã là góc TUYỆT ĐỐI, không còn phụ thuộc bên. Âm = ↺, dương = ↻ (chiều `Matrix.postRotate`). */
    @Test fun `bang xoay 4 ma x 2 ben`() {
        listOf(true, false).forEach { left ->
            assertEquals(0, P.rotationDegrees(P.ROTATE_NONE, left), "0 · left=$left")
            assertEquals(-90, P.rotationDegrees(P.ROTATE_LEFT, left), "L90 · left=$left")
            assertEquals(90, P.rotationDegrees(P.ROTATE_RIGHT, left), "R90 · left=$left")
            assertEquals(180, P.rotationDegrees(P.ROTATE_180, left), "180 · left=$left")
        }
    }

    /**
     * Mặc định theo bên = nguyên văn owner 2.67 (trái ↺ −90, phải ↻ +90). Mã lạ — kể cả mã CŨ `SIDE`/`SIDEINV` chưa
     * qua migrate — rơi về mặc định của BÊN, KHÔNG về 0 (0 là lựa chọn thật).
     */
    @Test fun `mac dinh theo ben, ma la roi ve mac dinh cua ben`() {
        assertEquals(P.ROTATE_LEFT, P.defaultRotation(left = true))
        assertEquals(P.ROTATE_RIGHT, P.defaultRotation(left = false))
        assertEquals(-90, P.rotationDegrees("TOPLEFT", left = true))
        assertEquals(90, P.rotationDegrees("", left = false))
        assertEquals(-90, P.rotationDegrees(P.ROTATE_BY_SIDE, left = true))
        assertEquals(90, P.rotationDegrees(P.ROTATE_BY_SIDE_INV, left = false))
    }

    /** `isRotation` nhận đúng 4 mã; hai mã cũ `SIDE`/`SIDEINV` KHÔNG còn là lựa chọn (chỉ migrate đọc). */
    @Test fun `isRotation nhan dung 4 ma, ma cu bi loai`() {
        assertEquals(listOf("0", "L90", "R90", "180"), P.ROTATIONS)
        assertTrue(P.ROTATIONS.all(P::isRotation))
        assertFalse(P.isRotation(P.ROTATE_BY_SIDE))
        assertFalse(P.isRotation(P.ROTATE_BY_SIDE_INV))
        assertFalse(P.isRotation("90"))
        assertFalse(P.isRotation("l90"))   // phân biệt hoa/thường: prefs_set chuẩn hoá trước khi ghi
        assertFalse(P.isRotation(""))
    }

    /**
     * Migrate khoá đơn cũ `camera_rotation` → góc từng bên, đủ 6 mã cũ × 2 bên. Bất biến: **số độ trước và sau
     * nâng cấp bằng nhau** cho mọi mã cũ hợp lệ — xe đang nhìn thấy gì thì sau khi lên 2.71 vẫn thấy đúng thế.
     */
    @Test fun `migrate 6 ma cu x 2 ben giu nguyen so do`() {
        // Bảng độ của mô hình CŨ (2.67–2.70), chép nguyên từ `rotationDegrees(mode, turn)` trước khi gỡ.
        val oldDeg: Map<String, Pair<Int, Int>> = mapOf(   // mã cũ → (trái, phải)
            "SIDE" to (-90 to 90), "SIDEINV" to (90 to -90), "0" to (0 to 0),
            "L90" to (-90 to -90), "R90" to (90 to 90), "180" to (180 to 180),
        )
        oldDeg.forEach { (old, lr) ->
            val l = P.migrateRotation(old, left = true)
            val r = P.migrateRotation(old, left = false)
            assertTrue(P.isRotation(l) && P.isRotation(r), "$old phải migrate ra mã hợp lệ ($l/$r)")
            assertEquals(lr.first, P.rotationDegrees(l, left = true), "$old · trái")
            assertEquals(lr.second, P.rotationDegrees(r, left = false), "$old · phải")
        }
        // Đúng mã, không chỉ đúng độ: SIDE → L90/R90 ; SIDEINV → R90/L90 (đây là lý do 2.69 trên xe ra rot=90 cả
        // hai bên nếu pref cũ là R90 — migrate giữ nguyên, không "sửa hộ").
        assertEquals(P.ROTATE_LEFT, P.migrateRotation(P.ROTATE_BY_SIDE, left = true))
        assertEquals(P.ROTATE_RIGHT, P.migrateRotation(P.ROTATE_BY_SIDE, left = false))
        assertEquals(P.ROTATE_RIGHT, P.migrateRotation(P.ROTATE_BY_SIDE_INV, left = true))
        assertEquals(P.ROTATE_LEFT, P.migrateRotation(P.ROTATE_BY_SIDE_INV, left = false))
        // Mã lạ trên đĩa (prefs sửa tay) → mặc định của bên, không ném.
        assertEquals(P.ROTATE_LEFT, P.migrateRotation("BOGUS", left = true))
        assertEquals(P.ROTATE_RIGHT, P.migrateRotation("", left = false))
    }

    /** outputState là giá trị THẬT của HAL (RE) — ghim để không đổi bừa. */
    @Test fun `output state khop hang HAL`() {
        assertEquals(1, CameraSignalPolicy.CamView.FRONT_LEFT.outputState)
        assertEquals(2, CameraSignalPolicy.CamView.FRONT_RIGHT.outputState)
        assertEquals(13, CameraSignalPolicy.CamView.LEFT_FRONT.outputState)
        assertEquals(14, CameraSignalPolicy.CamView.RIGHT_FRONT.outputState)
    }
}
