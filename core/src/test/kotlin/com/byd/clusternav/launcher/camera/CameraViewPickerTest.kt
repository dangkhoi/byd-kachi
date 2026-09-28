package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá khối *Nếu camera không hiện* (CAM-SL6-RIGHT, 2026-09-28). Owner trên Sealion 6: *"không mở được cam
 * phải (cam trái ok - khi xinhan ấy) - nên đề xuất cho chọn lại cam trong setting"*.
 *
 * Mỗi [CameraSignalPolicy.CamView] mang HAI số: lệnh xuất hình cho HAL và camera id. Cặp đúng khác nhau theo
 * đời xe ([ĐO ảnh owner] fisheye 4-in-1 = id 1 trên Seal, id 0 trên SL6) và chưa đo hết, nên theo CLAUDE.md §7
 * không được rải `if` theo tên dòng xe — để người lái dò.
 */
class CameraViewPickerTest {

    @Test
    fun `tap chon phu kin moi goc nhin`() {
        assertEquals(
            CameraSignalPolicy.CamView.entries.toList(),
            CameraSignalPolicy.VIEWS_ALL,
            "thiếu một góc trong tập chọn = người lái không dò tới được góc đó trên xe",
        )
        assertTrue(CameraSignalPolicy.VIEWS_ALL.size >= 8, "có ít nhất 8 góc")
    }

    @Test
    fun `doc ten goc chiu hoa thuong va khoang trang`() {
        assertEquals(CameraSignalPolicy.CamView.MIRROR_LEFT, CameraSignalPolicy.viewOf("MIRROR_LEFT"))
        assertEquals(CameraSignalPolicy.CamView.MIRROR_LEFT, CameraSignalPolicy.viewOf("  mirror_left "))
        assertEquals(CameraSignalPolicy.CamView.REAR_RIGHT, CameraSignalPolicy.viewOf("Rear_Right"))
    }

    @Test
    fun `ten la KHONG duoc nhan`() {
        assertNull(CameraSignalPolicy.viewOf(null), "chưa chọn")
        assertNull(CameraSignalPolicy.viewOf(""), "rỗng")
        assertNull(CameraSignalPolicy.viewOf("   "), "toàn khoảng trắng")
        assertNull(CameraSignalPolicy.viewOf("CAM_PHAI"), "tên không có thật")
        assertFalse(CameraSignalPolicy.isView("CAM_PHAI"), "isView phải khớp viewOf")
        assertTrue(CameraSignalPolicy.isView("front_right"))
    }

    @Test
    fun `mac dinh KHONG doi - xe khong cham Cai dat thi y nhu cu`() {
        assertEquals(
            CameraSignalPolicy.CamView.MIRROR_LEFT,
            CameraSignalPolicy.defaultView(CameraSignalPolicy.Turn.LEFT),
            "CLAUDE.md §6: đường mới xuống cuối, không đổi hành vi đang chạy tốt ngoài hiện trường",
        )
        assertEquals(
            CameraSignalPolicy.CamView.MIRROR_RIGHT,
            CameraSignalPolicy.defaultView(CameraSignalPolicy.Turn.RIGHT),
        )
    }

    @Test
    fun `moi goc mang du hai so de mo duoc camera`() {
        CameraSignalPolicy.VIEWS_ALL.forEach { v ->
            assertTrue(v.outputState > 0, "${v.name}: thiếu lệnh xuất hình ⇒ chọn vào là màn trống")
            assertTrue(v.cameraId >= 0, "${v.name}: camera id âm")
            assertTrue(v.labelVi.isNotBlank() && v.labelEn.isNotBlank(), "${v.name}: thiếu nhãn")
        }
    }

    @Test
    fun `hai goc mac dinh dung chung camera, chi khac lenh xuat hinh`() {
        val l = CameraSignalPolicy.CamView.MIRROR_LEFT
        val r = CameraSignalPolicy.CamView.MIRROR_RIGHT
        assertEquals(l.cameraId, r.cameraId, "cùng luồng fisheye 4-in-1")
        assertFalse(l.outputState == r.outputState,
            "khác lệnh xuất hình — [SUY] đây mới là chỗ SL6 nghẽn bên phải, vì cùng camera mà chỉ một bên lên")
    }
}
