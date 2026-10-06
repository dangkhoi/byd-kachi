package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraDemand.Op
import com.byd.clusternav.launcher.camera.CameraDemand.Outcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 wave 2B · KẾT QUẢ một lệnh camera + luật SÁNG của ô (spec `docs/specs/kachi-293-cam.html` §10 D1 · OQ4 · OQ6) ═══
 *
 * Khoá ba thứ mà tầng `:app` (cầu `:wake` → chính, ô thanh nút/widget) đọc từ `:core`:
 *  1. [CameraDemand.outcome] — điều THẬT SỰ xảy ra, để câu trả lời giọng nói không ✓ giả (D1): *"tắt camera"* trần khi
 *     KHÔNG có gì mở phải ra [Outcome.NOTHING_TO_CLOSE] (rơi về Camera 360), không phải "đã tắt";
 *  2. [CameraDemand.withoutMain] + mã `resultCode` — tiến trình chính chết thì lệnh TẮT = không có gì để tắt (camera theo
 *     yêu cầu chỉ sống trong RAM ở đó), lệnh MỞ = không tới được; mã khởi đầu của bên gửi (0) đọc ra [Outcome.UNREACHABLE];
 *  3. [CameraDemand.isOn] — ô sáng ⇔ chạm là TẮT (CHÍNH luật toggle [CameraDemand.next]); camera bị xi-nhan che ⇒ KHÔNG sáng.
 */
class CameraDemandOutcomeTest {

    private val all = CameraWhich.ALL

    /** Kết quả tính ĐÚNG trên lượt áp thật của máy trạng thái — không bảng tay (một nguồn sự thật với [CameraDemand.next]). */
    private fun run(op: Op, current: CameraWhich?, visible: CameraWhich? = current): Outcome =
        CameraDemand.outcome(op, current, CameraDemand.next(current, op, visible))

    @Test fun `tat camera tran - co camera mo thi DA TAT, khong co thi KHONG CO GI DE TAT`() {
        all.forEach { assertEquals(Outcome.CLOSED, run(Op.CloseAll, it), "đang mở $it") }
        assertEquals(Outcome.NOTHING_TO_CLOSE, run(Op.CloseAll, null), "không camera nào mở ⇒ đường Camera 360 (D1)")
        // Camera theo yêu cầu đang mở mà bị camera xi-nhan che: TẮT vẫn tắt đúng nó (không phải "không có gì").
        assertEquals(Outcome.CLOSED, run(Op.CloseAll, CameraWhich.REAR, visible = CameraWhich.LEFT))
    }

    @Test fun `tat dung mot camera - chi camera ay moi la DA TAT`() {
        all.forEach { w ->
            assertEquals(Outcome.CLOSED, run(Op.Close(w), w), "tắt $w khi $w đang mở")
            assertEquals(Outcome.NOTHING_TO_CLOSE, run(Op.Close(w), null), "tắt $w khi không gì mở")
            all.filter { it != w }.forEach { other ->
                assertEquals(Outcome.NOTHING_TO_CLOSE, run(Op.Close(w), other), "tắt $w khi $other mở — không đụng $other")
            }
        }
    }

    @Test fun `bat tat va mo - noi dung chieu da xay ra`() {
        all.forEach { w ->
            assertEquals(Outcome.OPENED, run(Op.Toggle(w), null), "chưa gì ⇒ mở $w")
            assertEquals(Outcome.CLOSED, run(Op.Toggle(w), w), "đang mở + đang hiện ⇒ nói lại lần hai = tắt")
            all.filter { it != w }.forEach { o -> assertEquals(Outcome.OPENED, run(Op.Toggle(w), o), "$o ⇒ thay bằng $w") }
            assertEquals(Outcome.OPENED, run(Op.Open(w), w), "mở chắc chắn")
            assertEquals(Outcome.OPENED, run(Op.Open(w), null))
        }
        // Bị camera xi-nhan che ⇒ chạm/nói là ĐƯA LÊN, không phải tắt (cùng luật toggle 2.93).
        assertEquals(Outcome.OPENED, run(Op.Toggle(CameraWhich.REAR), CameraWhich.REAR, visible = CameraWhich.LEFT))
    }

    @Test fun `tien trinh chinh khong song - tat la khong co gi, mo la khong toi duoc`() {
        all.forEach { w ->
            assertEquals(Outcome.NOTHING_TO_CLOSE, CameraDemand.withoutMain(Op.Close(w)))
            assertEquals(Outcome.UNREACHABLE, CameraDemand.withoutMain(Op.Toggle(w)))
            assertEquals(Outcome.UNREACHABLE, CameraDemand.withoutMain(Op.Open(w)))
        }
        assertEquals(Outcome.NOTHING_TO_CLOSE, CameraDemand.withoutMain(Op.CloseAll), "*tắt camera* ⇒ Camera 360 như ≤ 2.92")
    }

    @Test fun `ma resultCode - khu hoi, ma khoi dau la KHONG TOI DUOC, ma la khong doan`() {
        Outcome.entries.forEach { assertEquals(it, CameraDemand.outcomeOf(it.code), "khứ hồi $it") }
        assertEquals(Outcome.entries.size, Outcome.entries.map { it.code }.toSet().size, "mã không trùng")
        assertEquals(0, Outcome.UNREACHABLE.code, "mã khởi đầu bên gửi = không receiver nào trả lời")
        listOf(-1, 4, 99, Int.MIN_VALUE).forEach { assertEquals(Outcome.UNREACHABLE, CameraDemand.outcomeOf(it), "mã lạ $it") }
    }

    @Test fun `o sang khi cham la TAT - bi xi nhan che thi khong sang`() {
        all.forEach { w ->
            assertTrue(CameraDemand.isOn(w, demanded = w, showing = w))
            assertFalse(CameraDemand.isOn(w, demanded = null, showing = null))
            assertFalse(CameraDemand.isOn(w, demanded = null, showing = w), "camera xi-nhan $w đang hiện — không phải theo yêu cầu")
            all.filter { it != w }.forEach { o ->
                assertFalse(CameraDemand.isOn(w, demanded = w, showing = o), "$w bị $o che ⇒ chạm là đưa lên ⇒ không sáng")
                assertFalse(CameraDemand.isOn(w, demanded = o, showing = o))
            }
        }
        // Một luật: sáng ⇔ toggle ra null (chạm là tắt).
        val opts = listOf<CameraWhich?>(null) + all
        all.forEach { w -> opts.forEach { d -> opts.forEach { s ->
            assertEquals(CameraDemand.next(d, Op.Toggle(w), s) == null, CameraDemand.isOn(w, d, s), "$w d=$d s=$s")
        } } }
    }
}
