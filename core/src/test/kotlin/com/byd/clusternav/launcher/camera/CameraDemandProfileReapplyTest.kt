package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraDemand.Op
import com.byd.clusternav.launcher.camera.CameraDemand.ProfileReapply
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 wave 2C · CAM-D6-SAME-CAMERA-EDGE — đổi hồ sơ khi xi-nhan giữ ĐÚNG camera theo yêu cầu (spec `kachi-293-wave2c.html` R5) ═══
 *
 * Khoá luật `:core` mà `CameraSignalController.reapplyIfDemandShowing` / `endBlinker` thi hành:
 *  • không bao giờ dựng lại (dỡ + mở) camera mà xi-nhan đang giữ giữa lúc rẽ — dỡ là chớp đen đúng camera điểm mù;
 *  • hai nguồn TRÙNG camera ⇒ HẸN, và lời hẹn được thi hành đúng MỘT lần lúc xi-nhan nhả (cùng camera ⇒ `show` không tự dựng
 *    lại) — bản wave 2B để khung giữ cấu hình hồ sơ CŨ tới lượt mở kế (spec 293-cam §9 "giới hạn đã biết");
 *  • mọi ca khác giữ ĐÚNG luật D6 của wave 2B (bài tương đương quét mọi tổ hợp).
 */
class CameraDemandProfileReapplyTest {

    private val left = CameraWhich.LEFT
    private val right = CameraWhich.RIGHT
    private val rear = CameraWhich.REAR

    @Test fun `bang luat - ngay, hen luc nha, de yen`() {
        assertEquals(ProfileReapply.NONE, CameraDemand.profileReapply(null, left, null), "không gì hiện ⇒ không bật khung bất ngờ")
        assertEquals(ProfileReapply.NONE, CameraDemand.profileReapply(left, null, left), "chỉ camera xi-nhan ⇒ để yên (D6)")
        assertEquals(ProfileReapply.NONE, CameraDemand.profileReapply(left, rear, left),
            "camera theo yêu cầu đang bị xi-nhan che ⇒ để yên; lúc nhả `show` MỞ nó bằng cấu hình mới")
        assertEquals(ProfileReapply.NOW, CameraDemand.profileReapply(null, rear, rear), "không xi-nhan ⇒ dựng lại ngay (D6)")
        assertEquals(ProfileReapply.NOW, CameraDemand.profileReapply(left, right, right),
            "camera theo yêu cầu (mới hơn) đè xi-nhan bên kia ⇒ dựng lại ngay: không chạm camera của xi-nhan đang giữ")
        assertEquals(ProfileReapply.AT_RELEASE, CameraDemand.profileReapply(left, left, left),
            "TRÙNG camera ⇒ không dỡ giữa lúc rẽ, hẹn tới lúc nhả")
    }

    /**
     * Bài TƯƠNG ĐƯƠNG: quét mọi tổ hợp (xi-nhan × theo yêu cầu × mới-hơn), khung đang hiện = luật *"mới nhất thắng"*. Ngoài ca
     * TRÙNG camera, luật mới ra ĐÚNG như D6 wave 2B (`od.current != null && (od.newer || không xi-nhan)`); ca TRÙNG luôn hẹn —
     * và không ca nào dựng lại NGAY đúng camera xi-nhan đang giữ.
     */
    @Test fun `ngoai ca trung camera giu dung luat D6, khong bao gio dung lai camera xi nhan dang giu`() {
        val cams = listOf<CameraWhich?>(null) + CameraWhich.ALL
        var same = 0
        for (blinker in listOf(null, left, right)) for (demand in cams) for (newer in listOf(true, false)) {
            val showing = CameraDemand.shown(blinker, demand, newer)
            val got = CameraDemand.profileReapply(blinker, demand, showing)
            val d6 = demand != null && (newer || blinker == null)
            val tag = "xi-nhan=$blinker theo-yêu-cầu=$demand mới-hơn=$newer đang-hiện=$showing"
            if (blinker != null && blinker == demand) {
                same++
                assertEquals(ProfileReapply.AT_RELEASE, got, "TRÙNG camera ⇒ hẹn: $tag")
            } else {
                assertEquals(d6, got == ProfileReapply.NOW, "ngoài ca trùng phải giữ đúng D6 wave 2B: $tag")
                if (!d6) assertEquals(ProfileReapply.NONE, got, tag)
            }
            if (blinker != null && showing == blinker) assertNotEquals(ProfileReapply.NOW, got, "dỡ camera điểm mù giữa lúc rẽ: $tag")
        }
        assertEquals(4, same, "quét phải đi qua cả hai bên × hai trạng thái mới-hơn của ca trùng")
    }

    /** Vòng đời lời hẹn trên CHÍNH trạng thái controller dùng — đúng chuỗi sự kiện của ca owner gặp. */
    @Test fun `loi hen thi hanh dung mot lan luc nha, phien moi xoa hen`() {
        val od = CameraDemandState()
        od.apply(Op.Toggle(left))                       // bấm/nói "camera trái" ⇒ camera trái theo yêu cầu
        od.newer = false                                // xi-nhan trái BẬT sau đó (controller hạ cờ mới-hơn)
        val showing = od.shown(left)
        assertEquals(left, showing)
        assertFalse(od.onProfileSwitched(left, showing), "đổi hồ sơ giữa lúc rẽ ⇒ KHÔNG dựng lại ngay")
        assertTrue(od.reapplyAtRelease, "…mà hẹn tới lúc nhả")
        assertTrue(od.takeReapplyAtRelease(), "xi-nhan nhả ⇒ thi hành lời hẹn")
        assertFalse(od.takeReapplyAtRelease(), "đúng MỘT lần — lượt nhả sau không dựng lại vô cớ")

        assertFalse(od.onProfileSwitched(left, left))   // hẹn lần nữa…
        od.sessionOpened()                               // …nhưng một phiên MỚI dựng trước lúc nhả (đổi bên · xem thử · áp lại)
        assertFalse(od.takeReapplyAtRelease(), "phiên mới đã đọc cấu hình tươi ⇒ không dựng lại lần hai")

        // Camera theo yêu cầu khác (đè xi-nhan) ⇒ ngay, không hẹn; không gì hiện ⇒ không gì cả.
        val od2 = CameraDemandState()
        od2.apply(Op.Toggle(right))
        assertTrue(od2.onProfileSwitched(left, od2.shown(left)), "camera phải (mới hơn) đè xi-nhan trái ⇒ dựng lại ngay")
        assertFalse(od2.reapplyAtRelease)
        assertFalse(CameraDemandState().onProfileSwitched(null, null))
    }
}
