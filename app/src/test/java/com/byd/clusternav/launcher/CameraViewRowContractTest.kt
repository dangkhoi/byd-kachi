package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.camera.CameraSignalPolicy
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá ĐƯỜNG DÂY của khối *Nếu camera không hiện* (CAM-SL6-RIGHT, 2026-09-28).
 *
 * Bài học khoá lại: chú thích trong `CameraSignalController` đã viết như thể người lái *"chọn id 0 trong Cài
 * đặt"* trên Sealion 6 — trong khi màn Cài đặt CHƯA BAO GIỜ có hàng ấy. Tài liệu đi trước mã suốt nhiều bản,
 * và owner ngồi trên xe thì không có gì để bấm. Bài này đỏ nếu hàng đó lại biến mất.
 */
class CameraViewRowContractTest {

    private val settings = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SettingsSectionsCamera.kt")
    private val bridge = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeAutomation.kt")
    private val controller = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/camera/CameraSignalController.kt")
    private val vi = SourceRoots.codeOf("src/main/res/values/strings_kachi.xml")
    private val en = SourceRoots.codeOf("src/main/res/values-en/strings_kachi.xml")

    @Test
    fun `man Cai dat co du hai hang chon goc nhin`() {
        assertTrue(settings.contains("bridge.cameraViewLeft()"), "thiếu hàng xi-nhan trái")
        assertTrue(settings.contains("bridge.cameraViewRight()"), "thiếu hàng xi-nhan phải")
        assertTrue(settings.contains("bridge.setCameraView(left = true"), "hàng trái phải ghi được")
        assertTrue(settings.contains("bridge.setCameraView(left = false"), "hàng phải phải ghi được")
    }

    @Test
    fun `nhan la SO, khong phan ten camera`() {
        assertTrue(
            settings.contains("VIEWS_ALL.mapIndexed { i, v -> v.name to (i + 1).toString() }"),
            "owner 2026-09-28: *\"mình cũng đâu có biết là nó cam nào đâu mà phán cho người ta\"* — tên kiểu " +
                "*Trước-trái* là SUY từ tên hằng, chưa một lần đối chiếu camera thật ⇒ đặt tên là chỉ đường sai",
        )
        listOf("mirror_left", "front_right", "rear_left", "right_front").forEach {
            assertFalse(
                vi.contains("kachi_camera_view_$it"),
                "nhãn tên camera '$it' phải bị gỡ khỏi chuỗi — nếu còn là có đường quay lại phán bừa",
            )
        }
    }

    /**
     * Số hiện trên chip lấy theo VỊ TRÍ trong enum, còn giá trị lưu là TÊN hằng. Nếu ai đó chèn/đảo một giá trị
     * thì con số người lái đã báo về (*"số 3 chạy trên SL6"*) sẽ âm thầm trỏ sang góc khác — kiểu sai không ai
     * phát hiện được cho tới lần lên xe sau. Bài này khoá thứ tự lại.
     */
    @Test
    fun `thu tu goc bi KHOA - so bao ve khong duoc troi nghia`() {
        assertEquals(
            listOf(
                "MIRROR_LEFT", "MIRROR_RIGHT", "FRONT_LEFT", "FRONT_RIGHT",
                "REAR_LEFT", "REAR_RIGHT", "LEFT_FRONT", "RIGHT_FRONT",
            ),
            CameraSignalPolicy.VIEWS_ALL.map { it.name },
            "đổi thứ tự ⇒ mọi con số đã ghi trong nhật ký hiện trường trỏ sai; muốn thêm góc thì THÊM VÀO CUỐI",
        )
    }

    @Test
    fun `khong tra tai nguyen bang ten`() {
        assertTrue(
            !settings.contains("getIdentifier("),
            "tra tài nguyên bằng tên sống sót qua compile nhưng chết lặng khi rút gọn mã ⇒ chip trống trên bản phát hành",
        )
    }

    @Test
    fun `chon xong phai XEM THU ngay`() {
        val fn = bridge.substringAfter("fun ClusterNavBridge.setCameraView(").substringBefore("\n}")
        assertTrue(fn.contains("previewSide(left)"),
            "không xem thử ngay thì người lái phải ra đường bật xi-nhan mới biết chọn đúng chưa — đúng cái vòng " +
                "mò mà CLAUDE.md §15 cấm")
    }

    @Test
    fun `bo dieu khien PHAI doc pref, khong dung cung mac dinh`() {
        assertTrue(
            controller.contains("CameraSignalPolicy.viewOf(Prefs.cameraView(appCtx"),
            "chọn trong Cài đặt mà bộ điều khiển không đọc thì hàng đó chỉ để trang trí",
        )
        assertTrue(
            controller.contains("?: CameraSignalPolicy.defaultView(turn)"),
            "vẫn phải lùi về mặc định cũ: xe không chạm Cài đặt thì không đổi một pixel nào (CLAUDE.md §6)",
        )
    }

    @Test
    fun `du chu ca hai ngon ngu`() {
        listOf("kachi_camera_view_sub", "kachi_camera_view_row_left", "kachi_camera_view_row_right").forEach {
            assertTrue(vi.contains("\"$it\""), "thiếu chuỗi tiếng Việt: $it")
            assertTrue(en.contains("\"$it\""), "thiếu chuỗi tiếng Anh: $it")
        }
        assertTrue(
            vi.contains("Chưa biết số nào là camera nào"),
            "câu tiêu đề phải nói THẬT là mình chưa biết, thay vì bày ra tên camera nghe như đã chắc",
        )
    }
}
