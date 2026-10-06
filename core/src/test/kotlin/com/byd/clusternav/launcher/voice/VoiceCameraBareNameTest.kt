package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.camera.CameraWhich
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 OQ5 (spec `kachi-293-voice.html` §7 · §9) — TÊN camera trần (không động từ, không phía) KHÔNG phải lệnh.
 *
 * [ĐO host 2026-10-07] câu *"tắt camera"* + nhạc chạy suốt câu: mô hình mất chữ *"tắt"* ⇒ *"CAMERA"* ⇒ bộ phân tích gán động
 * từ ngầm "mở" (nhánh (b') *"cả câu là tên của một việc"*) ⇒ `Control(cam, 1)` — BẬT camera khi người lái xin TẮT. Luật:
 * tên camera không kèm động từ lẫn phía ⇒ `Unknown(NO_VERB)` (hỏi lại, như mọi câu thiếu). Có phía (*"cam trái"*) giữ
 * nguyên hành vi bật/tắt của nhóm CAM; có động từ (*"mở camera"* · *"tắt camera quanh xe"*) không đổi.
 */
class VoiceCameraBareNameTest {

    private fun one(s: String, apps: List<String> = emptyList()) = VoiceIntentParser.parse(s, apps = apps).single()

    @Test
    fun `chu mo hinh in ra o ca OQ5 khong thanh lenh`() {
        // Chuỗi THẬT mô hình in cho WAV "tắt camera" + nhạc (bộ `lenh`, ca p12_duoi_nhac) — app hạ chữ thường trước khi hiểu.
        val heard = "CAMERA".lowercase()
        assertEquals(VoiceIntent.Unknown(VoiceUnknownReason.NO_VERB, heard), one(heard))
        // Máy có app tên "Camera" cũng không mở app, không bật gì.
        assertEquals(VoiceIntent.Unknown(VoiceUnknownReason.NO_VERB, heard), one(heard, apps = listOf("Camera", "Máy ảnh")))
    }

    @Test
    fun `ten camera tran khong dong tu khong phia deu hoi lai`() {
        listOf(
            "camera", "camera nhé", "camera 360 độ", "Camera 360", "camera quanh xe", "camera toàn cảnh", "cam ba sáu mươi",
            "cam", "máy quay",
        ).forEach { s ->
            val i = one(s)
            assertTrue(i is VoiceIntent.Unknown, "«$s» không động từ, không phía ⇒ không được làm gì: $i")
        }
    }

    @Test
    fun `co phia thi van la lenh bat tat cua nhom CAM`() {
        mapOf(
            "camera sau" to CameraWhich.REAR, "cam lùi" to CameraWhich.REAR, "cam trái" to CameraWhich.LEFT,
            "camera bên phải" to CameraWhich.RIGHT, "cam đầu" to CameraWhich.FRONT, "rear camera" to CameraWhich.REAR,
        ).forEach { (s, which) -> assertEquals(VoiceIntent.Launcher(LauncherActions.cameraId(which)), one(s), s) }
    }

    @Test
    fun `co dong tu thi khong doi`() {
        assertEquals(VoiceIntent.Control("cam", 1), one("mở camera"))
        assertEquals(VoiceIntent.Control("cam", 1), one("bật camera 360"))
        assertEquals(VoiceIntent.Control("cam", 0), one("tắt camera quanh xe"))
        assertEquals(VoiceIntent.Launcher(LauncherActions.CAM_OFF), one("tắt camera"))
    }

    @Test
    fun `bareName chi xet phia ngay sau ten`() {
        fun w(s: String) = VoiceLexicon.tokenize(s).map { it.norm }
        assertTrue(VoiceCameraPhrases.bareName(w("cam ba sáu mươi")), "bỏ dấu thì sáu = sau — không được coi là phía")
        assertFalse(VoiceCameraPhrases.bareName(w("camera bên trái")))
        assertFalse(VoiceCameraPhrases.bareName(w("bật camera")), "mở đầu bằng động từ — không phải một tên")
        assertFalse(VoiceCameraPhrases.bareName(w("đèn đọc")))
    }

    @Test
    fun `luat chi ap cho ten camera`() {
        // Phạm vi OQ5: danh từ trần của nút KHÁC giữ hành vi cũ — đổi chúng là quyết định riêng của owner (spec §7 OQ5).
        assertEquals(VoiceIntent.Control("readl", 1), one("đèn đọc"))
    }
}
