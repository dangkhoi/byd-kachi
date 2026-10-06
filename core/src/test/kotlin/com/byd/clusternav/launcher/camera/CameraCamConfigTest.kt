package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · CAMERA-PER-CAM-CONFIG — tên khoá · miền · phép "theo chung" (spec `docs/specs/kachi-293-cam.html` R2) ═════
 *
 * Bài khoá điều quan trọng nhất của bộ chỉnh: **mặc định = 2.92 từng pixel** (khoá vắng ⇒ đọc đúng khoá cũ / pref chung),
 * và hai camera gương dùng LẠI sáu khoá 2.35/2.71/2.76 thay vì đẻ khoá thứ hai cho cùng một sự thật.
 */
class CameraCamConfigTest {

    @Test fun `hai camera guong dung lai dung sau khoa cu`() {
        assertEquals("camera_pos_left", CameraCamConfig.cornerKey(CameraWhich.LEFT))
        assertEquals("camera_pos_right", CameraCamConfig.cornerKey(CameraWhich.RIGHT))
        assertEquals("camera_rot_left", CameraCamConfig.rotationKey(CameraWhich.LEFT))
        assertEquals("camera_rot_right", CameraCamConfig.rotationKey(CameraWhich.RIGHT))
        assertEquals("camera_mirror_left", CameraCamConfig.mirrorKey(CameraWhich.LEFT))
        assertEquals("camera_mirror_right", CameraCamConfig.mirrorKey(CameraWhich.RIGHT))
        assertEquals(28, CameraCamConfig.ALL_KEYS.size)
        assertEquals(CameraCamConfig.ALL_KEYS.size, CameraCamConfig.ALL_KEYS.toSet().size, "không trùng")
        assertEquals(22, CameraCamConfig.NEW_KEYS.size)
        assertEquals(20, CameraCamConfig.PROFILE_KEYS.size)
        assertEquals(8, CameraCamConfig.DEVICE_KEYS.size)
        assertTrue((CameraCamConfig.PROFILE_KEYS intersect CameraCamConfig.DEVICE_KEYS.toSet()).isEmpty())
        CameraCamConfig.ALL_KEYS.forEach { assertTrue(it.startsWith("camera_") && it == it.lowercase(), it) }
        // Không khoá nào mang chữ `place` (dáng dữ liệu ĐỊA ĐIỂM — ProfileSharePolicyTest soi): vị trí là toạ độ MÀN.
        CameraCamConfig.ALL_KEYS.forEach { assertFalse("place" in it, it) }
    }

    @Test fun `goc mac dinh - guong cung ben, camera giua tren phai`() {
        assertEquals(CameraSignalPolicy.CORNER_TOP_LEFT, CameraCamConfig.defaultCorner(CameraWhich.LEFT))
        assertEquals(CameraSignalPolicy.CORNER_TOP_RIGHT, CameraCamConfig.defaultCorner(CameraWhich.RIGHT))
        assertEquals(CameraSignalPolicy.CORNER_TOP_RIGHT, CameraCamConfig.defaultCorner(CameraWhich.REAR))
        assertEquals(CameraSignalPolicy.CORNER_TOP_RIGHT, CameraCamConfig.defaultCorner(CameraWhich.FRONT))
        CameraWhich.ALL.filter { it.side }.forEach {
            assertEquals(CameraSignalPolicy.defaultCorner(it == CameraWhich.LEFT), CameraCamConfig.defaultCorner(it), "= 2.35 R4")
        }
        assertEquals(CameraSignalPolicy.ROTATE_NONE, CameraCamConfig.CENTRE_ROTATION_DEFAULT, "[CHƯA BIẾT] ⇒ không xoay")
    }

    @Test fun `co phan tram - 100 la hom nay, thanh keo 20 nac`() {
        assertEquals(100, CameraCamConfig.SIZE_DEFAULT)
        assertTrue(CameraCamConfig.isSizePct(50) && CameraCamConfig.isSizePct(150) && !CameraCamConfig.isSizePct(49) && !CameraCamConfig.isSizePct(151))
        assertEquals(20, CameraCamConfig.SIZE_POSITIONS)
        (0..CameraCamConfig.SIZE_POSITIONS).forEach { pos ->
            val pct = CameraCamConfig.sizeAt(pos)
            assertTrue(CameraCamConfig.isSizePct(pct))
            assertEquals(pos, CameraCamConfig.sizePosition(pct), "khứ hồi nấc $pos")
        }
        assertEquals(10, CameraCamConfig.sizePosition(100))
        assertEquals(0, CameraCamConfig.sizePosition(-5), "lạ ⇒ kẹp")
        assertEquals(20, CameraCamConfig.sizePosition(999))
    }

    @Test fun `vi tri keo tha - khu hoi, sai thi ve goc mac dinh, khong kep im`() {
        val p = CameraCamConfig.place(250, 900)
        assertEquals("250,900", p.encode())
        assertEquals(p, CameraCamConfig.parsePlace(p.encode()))
        assertEquals(p, CameraCamConfig.parsePlace(" 250 , 900 "))
        assertEquals(CameraCamConfig.Place(0, 1000), CameraCamConfig.place(-7, 4000), "dựng = kẹp")
        listOf(null, "", "250", "a,b", "1,2,3", "-1,5", "5,1001", "250;900")
            .forEach { assertNull(CameraCamConfig.parsePlace(it), "«$it» ⇒ theo góc mặc định") }
    }

    @Test fun `hinh va kieu rieng - AUTO va gia tri la theo chung`() {
        val g = CameraSignalPolicy.SHAPE_ROUND
        assertEquals(g, CameraCamConfig.effectiveShape(null, g), "khoá vắng ⇒ y 2.92")
        assertEquals(g, CameraCamConfig.effectiveShape(CameraCamConfig.FOLLOW, g))
        assertEquals(g, CameraCamConfig.effectiveShape("TRIANGLE", g), "rác ⇒ theo chung")
        assertEquals(CameraSignalPolicy.SHAPE_RECT, CameraCamConfig.effectiveShape(CameraSignalPolicy.SHAPE_RECT, g))
        val m = CameraViewMode.WIDE
        assertEquals(m, CameraCamConfig.effectiveProjection(null, m))
        assertEquals(m, CameraCamConfig.effectiveProjection(CameraCamConfig.FOLLOW, m))
        assertEquals(m, CameraCamConfig.effectiveProjection("SPHERE", m))
        assertEquals(CameraViewMode.FISHEYE, CameraCamConfig.effectiveProjection(CameraViewMode.FISHEYE, m))
        assertEquals(CameraCamConfig.FOLLOW, CameraCamConfig.shapeChoice("rác"))
        assertEquals(CameraCamConfig.FOLLOW, CameraCamConfig.projectionChoice(null))
        assertEquals(CameraViewMode.STRAIGHT, CameraCamConfig.projectionChoice(CameraViewMode.STRAIGHT))
        assertTrue(CameraCamConfig.isShapeChoice(CameraCamConfig.FOLLOW) && CameraCamConfig.isProjectionChoice(CameraCamConfig.FOLLOW))
        assertFalse(CameraSignalPolicy.isShape(CameraCamConfig.FOLLOW) || CameraViewMode.isMode(CameraCamConfig.FOLLOW),
            "`AUTO` không được trùng một mã hình/kiểu thật")
    }

    /** wave 2B · D2 — `prefs_set` một khoá camera ⇒ áp lại ĐÚNG camera ấy nếu đang hiện: khoá → camera đủ 28, khoá khác ⇒ null. */
    @Test fun `khoa nao cua camera nao - du 28 khoa, khoa chung khong thuoc camera nao`() {
        CameraWhich.ALL.forEach { w ->
            (CameraCamConfig.profileKeys(w) + CameraCamConfig.deviceKeys(w)).forEach { assertEquals(w, CameraCamConfig.cameraOf(it), it) }
        }
        assertEquals(CameraCamConfig.ALL_KEYS.size, CameraCamConfig.ALL_KEYS.count { CameraCamConfig.cameraOf(it) != null })
        assertEquals(CameraWhich.LEFT, CameraCamConfig.cameraOf("camera_pos_left"), "khoá 2.35 cũ của camera gương")
        listOf("camera_projection", "camera_zoom", "camera_shape", "camera_signal_enabled", "camera_xy_", "")
            .forEach { assertNull(CameraCamConfig.cameraOf(it), "«$it» không thuộc riêng camera nào") }
    }
}
