package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.camera.CameraDemand
import com.byd.clusternav.launcher.camera.CameraWhich
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · CAMERA-ON-DEMAND bằng GIỌNG NÓI — NHIỀU cách nói, *"camera 360"* của xe KHÔNG đổi ═════════════════════
 *
 * Spec `docs/specs/kachi-293-cam.html` R1 · §4.4. Owner 06/10: *"mở cam trái, mở cam phải, chứ không phải ai cũng đọc là
 * mở camera trái đâu nhé, nhiều option nhé"*. Mọi câu dưới đây viết CÓ DẤU như mô hình nhận dạng in ra (không ca nào
 * chỉ có bản không dấu), đo bằng bộ phân tích thật ([VoiceIntentParser.parse]):
 *  1. bốn camera × động từ MỞ (mở · bật · xem · hiện · cho xem · coi · hiển thị …) × tên (cam · camera · máy quay ·
 *     ca-me-ra) × từ nối (bên · phía · đằng · tay) × phía + đồng nghĩa (sau = lùi/đuôi/hậu · trước = đầu) ⇒ `Launcher(cam)`;
 *  2. cùng các trục với động từ TẮT (tắt · đóng · ẩn · thôi · dẹp) ⇒ `Launcher(cam, off = true)`;
 *  3. TẮT không nêu camera (*"tắt cam"* · *"tắt camera"* · *"tắt hết camera"*) ⇒ `Launcher(CAM_OFF)`;
 *  4. **Không cướp câu khác**: *"mở camera"* · *"…camera 360"* · *"…camera toàn cảnh/quanh xe"* vẫn là nút Camera 360;
 *     *"camera sau bẩn quá"* · *"cảm ơn"* · *"tắt cảm biến"* · câu hỏi không thành lệnh camera.
 */
class CameraDemandVoiceTest {

    private fun all(s: String): List<VoiceIntent> = VoiceIntentParser.parse(s)
    private fun one(s: String): VoiceIntent = all(s).single()

    private val opens: Map<CameraWhich, List<String>> = mapOf(
        CameraWhich.REAR to listOf(
            "mở cam sau", "mở camera sau", "bật cam sau", "bật camera sau", "xem cam sau", "xem camera sau",
            "hiện cam sau", "cho xem camera sau", "coi cam sau", "hiển thị camera sau", "xem thử camera sau",
            "mở máy quay sau", "mở ca-me-ra sau", "bật ca mê ra sau", "cam sau", "camera sau", "máy quay sau",
            "cam lùi", "camera lùi", "bật cam đuôi", "xem camera đuôi", "mở camera hậu", "xem cam phía sau",
            "mở camera đằng sau", "bật camera ở phía sau", "mở cái camera sau", "bật cam sau lên", "camera sau xe",
            "cho tôi xem cam sau", "làm ơn mở cam sau nhé", "mở giúp camera sau",
            "open rear camera", "show the back camera", "view rear cam", "turn on reverse camera", "rear camera",
        ),
        CameraWhich.LEFT to listOf(
            "mở cam trái", "mở camera trái", "bật cam trái", "xem cam trái", "coi camera trái", "hiện cam trái",
            "mở cam bên trái", "bật camera bên trái", "cam bên trái", "camera bên tay trái", "cam trái", "camera trái",
            "hiện máy quay bên trái", "mở ca-me-ra trái", "cho coi cam trái", "hiển thị camera bên trái",
            "open left camera", "show left cam", "left camera",
        ),
        CameraWhich.RIGHT to listOf(
            "mở cam phải", "mở camera phải", "bật cam phải", "xem cam phải", "coi camera phải", "hiện cam phải",
            "mở cam bên phải", "bật camera bên phải", "cam bên phải", "camera bên tay phải", "cam phải", "camera phải",
            "mở máy quay phải", "bật ca-me-ra bên phải", "cho xem cam bên phải",
            "open right camera", "show the right camera", "right cam",
        ),
        CameraWhich.FRONT to listOf(
            "mở cam trước", "mở camera trước", "bật cam trước", "xem camera trước", "coi cam trước", "hiện cam trước",
            "cam trước", "camera trước", "cam đầu", "camera đầu xe", "bật cam đầu", "xem camera phía trước",
            "mở camera đằng trước", "mở máy quay trước", "bật ca-me-ra trước", "cho xem cam trước",
            "open front camera", "turn on the front camera", "front cam",
        ),
    )

    private val closes: Map<CameraWhich, List<String>> = mapOf(
        CameraWhich.REAR to listOf(
            "tắt cam sau", "tắt camera sau", "đóng camera sau", "ẩn cam sau", "thôi xem camera sau", "thôi cam lùi",
            "tắt camera lùi", "tắt cam đuôi", "tắt máy quay sau", "tắt ca-me-ra sau", "dẹp cam phía sau", "tắt cam sau đi",
            "close rear camera", "turn off the back camera", "hide rear cam",
        ),
        CameraWhich.LEFT to listOf(
            "tắt cam trái", "tắt camera trái", "đóng cam bên trái", "ẩn camera trái", "thôi xem cam trái",
            "tắt máy quay trái", "tắt cam bên tay trái", "turn off left camera", "close the left camera",
        ),
        CameraWhich.RIGHT to listOf(
            "tắt cam phải", "tắt camera phải", "đóng cam bên phải", "ẩn camera phải", "thôi coi cam phải",
            "tắt ca-me-ra phải", "dẹp camera bên phải", "hide right camera",
        ),
        CameraWhich.FRONT to listOf(
            "tắt cam trước", "tắt camera trước", "đóng camera đầu", "ẩn cam phía trước", "tắt máy quay trước",
            "thôi xem camera đầu xe", "turn off front camera",
        ),
    )

    @Test fun `moi cach noi MO tung camera`() {
        opens.forEach { (w, phrases) ->
            phrases.forEach { p -> assertEquals(VoiceIntent.Launcher(LauncherActions.cameraId(w)), one(p), p) }
        }
        assertTrue(opens.values.sumOf { it.size } >= 80, "bộ câu phải phủ rộng (owner: *nhiều option*)")
    }

    @Test fun `moi cach noi TAT tung camera`() {
        closes.forEach { (w, phrases) ->
            phrases.forEach { p -> assertEquals(VoiceIntent.Launcher(LauncherActions.cameraId(w), off = true), one(p), p) }
        }
    }

    @Test fun `tat cam khong neu camera nao thi tat camera dang mo`() {
        listOf(
            "tắt cam", "tắt camera", "đóng camera", "ẩn camera", "thôi xem camera", "thôi camera", "tắt hết camera",
            "tắt tất cả camera", "đóng máy quay", "tắt ca-me-ra", "dẹp cam", "tắt cam đi", "tắt cái camera",
            "turn off the camera", "close camera", "hide the cam",
        ).forEach { p -> assertEquals(VoiceIntent.Launcher(LauncherActions.CAM_OFF), one(p), p) }
    }

    /** Camera 360 của xe giữ nguyên cách gọi có số / có tên riêng; *"mở camera"* trần vẫn là nó (không nêu camera nào). */
    @Test fun `camera 360 cua xe khong doi`() {
        assertEquals(VoiceIntent.Control("cam", 1), one("mở camera"))
        assertEquals(VoiceIntent.Control("cam", 1), one("mở camera 360"))
        assertEquals(VoiceIntent.Control("cam", 0), one("tắt camera 360"))
        assertEquals(VoiceIntent.Control("cam", 1), one("bật camera toàn cảnh"))
        assertEquals(VoiceIntent.Control("cam", 0), one("tắt camera quanh xe"))
        assertEquals(VoiceIntent.Control("cam", 0), one("tắt camera toàn cảnh"))
    }

    @Test fun `khong phai lenh camera thi khong mo camera`() {
        listOf(
            "camera sau bẩn quá", "camera sau mờ", "mở trái", "camera sau có bật không", "tắt cảm biến",
            "camera sau đang mở hay tắt",
        ).forEach { p ->
            all(p).forEach { i ->
                assertFalse(i is VoiceIntent.Launcher && LauncherActions.isCamera(i.id), "«$p» không được thành lệnh camera: $i")
            }
        }
        assertEquals(VoiceIntent.EndSession, one("cảm ơn"), "*cảm ơn* vẫn là câu kết thúc (bỏ dấu = *cam on*)")
        assertNull(VoiceCameraPhrases.parse(VoiceLexicon.tokenize("thôi lấy gió ngoài")), "*thôi* chỉ là động từ TẮT trong câu camera")
        // Soát senior 2.93 [P3] — thứ tự *phía rồi tên* chỉ của tiếng Anh; tiếng Việt ngược thứ tự là câu khác nghĩa.
        listOf("trái cam", "trước camera", "sau camera", "đầu cam", "mở trái cam").forEach {
            assertNull(VoiceCameraPhrases.parse(VoiceLexicon.tokenize(it)), "«$it» không phải câu camera")
        }
    }

    @Test fun `cau ghep - camera cung mot lenh khac`() {
        val got = all("mở cam sau và bật điều hòa")
        assertEquals(2, got.size, "$got")
        assertEquals(VoiceIntent.Launcher(LauncherActions.CAM_REAR), got[0])
        assertFalse(got[1] is VoiceIntent.Unknown, "${got[1]}")
    }

    @Test fun `viec khong co trang thai giu nguyen nghia - mo cai dat van la Launcher thuong`() {
        assertEquals(VoiceIntent.Launcher(LauncherActions.SETTINGS), one("mở cài đặt"))
        assertFalse(LauncherActions.switchable(LauncherActions.SETTINGS))
        LauncherActions.ALL.filter { LauncherActions.cameraOf(it.id) != null }.forEach { assertTrue(it.switchable, it.id) }
    }

    /** Câu MỞ là bật/tắt theo thứ đang hiện (`:wake` không biết) ⇒ chỉ nói TÊN; câu TẮT nói *"Tắt …"*. */
    @Test fun `cau tra loi noi dung viec khong hua sai`() {
        assertEquals("Camera sau", VoiceReply.preview(VoiceIntent.Launcher(LauncherActions.CAM_REAR), Lang.VI))
        assertEquals("Rear camera", VoiceReply.preview(VoiceIntent.Launcher(LauncherActions.CAM_REAR), Lang.EN))
        assertEquals("Tắt Camera sau", VoiceReply.preview(VoiceIntent.Launcher(LauncherActions.CAM_REAR, off = true), Lang.VI))
        assertEquals("Turn off Rear camera", VoiceReply.preview(VoiceIntent.Launcher(LauncherActions.CAM_REAR, off = true), Lang.EN))
        assertEquals("Tắt camera", VoiceReply.preview(VoiceIntent.Launcher(LauncherActions.CAM_OFF), Lang.VI))
        assertEquals("Camera off", VoiceReply.preview(VoiceIntent.Launcher(LauncherActions.CAM_OFF), Lang.EN))
        assertEquals("Mở Cài đặt", VoiceReply.preview(VoiceIntent.Launcher(LauncherActions.SETTINGS), Lang.VI), "việc khác giữ *Mở*")
        assertEquals("Launcher(launcher_cam_left tắt)", VoiceDecision.describe(listOf(VoiceIntent.Launcher(LauncherActions.CAM_LEFT, off = true))))
    }

    /** Một chỗ dịch câu nói → lệnh camera (tầng thi hành không tự rẽ nhánh theo mã). */
    @Test fun `lenh camera cua mot cau noi`() {
        CameraWhich.ALL.forEach { w ->
            val id = LauncherActions.cameraId(w)
            assertEquals(CameraDemand.Op.Toggle(w), LauncherActions.cameraOp(id, off = false), "câu MỞ = bật/tắt")
            assertEquals(CameraDemand.Op.Close(w), LauncherActions.cameraOp(id, off = true))
        }
        assertEquals(CameraDemand.Op.CloseAll, LauncherActions.cameraOp(LauncherActions.CAM_OFF, off = false))
        assertNull(LauncherActions.cameraOp(LauncherActions.SETTINGS, off = false))
    }

    /** Hotword: cụm CÓ DẤU, viết HOA trong tệp; dòng nào bị luật tiền tố bỏ thì phải là tiền tố của một dòng còn lại. */
    @Test fun `hotword co du cach goi camera, co dau, viet hoa`() {
        val file = SherpaHotwords.phraseFile(SherpaPhraseHotwords.phrases()).lines().filter { it.isNotBlank() }.toSet()
        listOf("CAM TRÁI", "MỞ CAM BÊN TRÁI", "BẬT CAM PHẢI", "XEM CAMERA LÙI", "TẮT CAM ĐUÔI", "CAMERA PHÍA SAU",
            "ĐÓNG CAMERA TRƯỚC", "MỞ CAM ĐẦU", "TẮT CAMERA SAU").forEach { assertTrue(it in file, "thiếu hotword «$it»") }
        VoiceCameraPhrases.hotwordPhrases().forEach { p ->
            val hw = requireNotNull(SherpaHotwords.normalize(p))
            assertEquals(hw.uppercase(), hw, "hotword phải viết HOA")
            assertTrue(' ' in hw, "không dòng một từ: «$hw»")
            assertTrue(hw in file || file.any { it.startsWith("$hw ") }, "cụm «$hw» không vào tệp mà cũng không là tiền tố")
        }
        // Nguồn hotword là dạng CÓ DẤU (không có dòng chỉ-không-dấu cho chữ tiếng Việt).
        assertTrue(VoiceCameraPhrases.hotwordPhrases().filter { "trái" in it || "phải" in it || "lùi" in it }.size >= 20)
        assertTrue(VoiceCameraPhrases.hotwordPhrases().none { " trai" in it || " phai" in it || " lui" in it }, "dòng không dấu")
    }
}
