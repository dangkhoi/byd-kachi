package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.camera.CameraDemand
import com.byd.clusternav.launcher.camera.CameraDemand.Outcome
import com.byd.clusternav.launcher.voice.VoiceCameraTurn.Answer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 wave 2B · VOICE-CAM-OFF-360-FALLBACK — một vế camera NÓI ĐÚNG việc đã xảy ra (spec §10 D1 [P1] · OQ6) ═══════
 *
 * Lỗi khoá ở đây (senior review Pass 1, [P1] chặn phát hành): *"tắt camera"* trần, KHÔNG có camera theo yêu cầu nào mở ⇒
 * bản Pass 1 không làm gì mà vẫn đọc *"✓ Tắt camera"* trong khi Camera 360 của xe còn bật. Câu trả lời ≤ 2.92 của chính câu
 * ấy là nút Camera 360 TẮT — bài [tat camera tran khong co gi de tat - chay lai thanh Camera 360 nhu 2_92] ghim ý định
 * thay thế BẰNG bộ phân tích thật (cùng ý định của *"tắt camera 360"*), không bằng một hằng chép tay.
 */
class VoiceCameraTurnTest {

    private val off = VoiceIntent.Launcher(LauncherActions.CAM_OFF)
    private val rear = VoiceIntent.Launcher(LauncherActions.CAM_REAR)
    private val rearOff = VoiceIntent.Launcher(LauncherActions.CAM_REAR, off = true)

    private fun say(a: Answer): String = (a as Answer.Say).line

    @Test fun `tat camera tran khong co gi de tat - chay lai thanh Camera 360 nhu 2_92`() {
        val a = VoiceCameraTurn.answer(off, CameraDemand.Op.CloseAll, Outcome.NOTHING_TO_CLOSE, Lang.VI)
        val legacy = VoiceIntentParser.parse("tắt camera 360").single()
        assertEquals(Answer.Rerun(legacy), a, "≤ 2.92 *tắt camera* = nút Camera 360 TẮT — đúng ý định của *tắt camera 360*")
        assertEquals(VoiceIntent.Control("cam", 0), legacy, "tiền đề: *tắt camera 360* là nút Camera 360")
        val fb = requireNotNull(LauncherActions.offFallback(LauncherActions.CAM_OFF))
        assertEquals(ControlKind.TOGGLE, ControlRegistry.byId(fb)?.kind, "đích thay thế phải là một NÚT XE có thật (bật/tắt)")
        // Chỉ *Tắt camera* có đường thay thế — câu nêu một camera cụ thể không bao giờ chạm Camera 360.
        LauncherActions.placeable.filter { it.id != LauncherActions.CAM_OFF }.forEach {
            assertEquals(null, LauncherActions.offFallback(it.id), "${it.id} không có đường thay thế")
        }
    }

    @Test fun `tat camera tran co camera dang mo - noi DA TAT, khong cham Camera 360`() {
        assertEquals("✓ Đã tắt camera", say(VoiceCameraTurn.answer(off, CameraDemand.Op.CloseAll, Outcome.CLOSED, Lang.VI)))
        assertEquals("✓ Camera off", say(VoiceCameraTurn.answer(off, CameraDemand.Op.CloseAll, Outcome.CLOSED, Lang.EN)))
    }

    @Test fun `cau MO noi dung chieu da xay ra - khong chi ten`() {
        val toggle = CameraDemand.Op.Toggle(com.byd.clusternav.launcher.camera.CameraWhich.REAR)
        assertEquals("✓ Đã mở camera sau", say(VoiceCameraTurn.answer(rear, toggle, Outcome.OPENED, Lang.VI)))
        assertEquals("✓ Đã tắt camera sau", say(VoiceCameraTurn.answer(rear, toggle, Outcome.CLOSED, Lang.VI)), "nói lại lần hai ⇒ tắt")
        assertEquals("✓ Open rear camera", say(VoiceCameraTurn.answer(rear, toggle, Outcome.OPENED, Lang.EN)))
        // Đọc lên thành câu tiếng Việt thật (Pass 1 đọc *"Đã camera sau"*).
        assertEquals("Đã mở camera sau", VoiceFeedbackPhrase.merge(listOf("✓ Đã mở camera sau"), Lang.VI))
        assertEquals("Đã tắt camera sau", VoiceFeedbackPhrase.merge(listOf("✓ Đã tắt camera sau"), Lang.VI))
    }

    @Test fun `tat dung camera khong mo - noi that, khong dau tich, khong Camera 360`() {
        val close = CameraDemand.Op.Close(com.byd.clusternav.launcher.camera.CameraWhich.REAR)
        val a = VoiceCameraTurn.answer(rearOff, close, Outcome.NOTHING_TO_CLOSE, Lang.VI)
        assertEquals("Camera sau đang không mở", say(a))
        assertEquals("Rear camera is not open", say(VoiceCameraTurn.answer(rearOff, close, Outcome.NOTHING_TO_CLOSE, Lang.EN)))
        assertEquals("✓ Đã tắt camera sau", say(VoiceCameraTurn.answer(rearOff, close, Outcome.CLOSED, Lang.VI)))
    }

    @Test fun `khong toi duoc controller - noi chua lam duoc, KHONG roi ve Camera 360`() {
        val a = VoiceCameraTurn.answer(off, CameraDemand.Op.CloseAll, Outcome.UNREACHABLE, Lang.VI)
        assertEquals("✗ Tắt camera — chưa liên lạc được màn hình chính", say(a), "không biết có camera nào mở ⇒ không đoán")
        val toggle = CameraDemand.Op.Toggle(com.byd.clusternav.launcher.camera.CameraWhich.REAR)
        assertEquals("✗ Mở camera sau — chưa liên lạc được màn hình chính", say(VoiceCameraTurn.answer(rear, toggle, Outcome.UNREACHABLE, Lang.VI)))
        assertEquals("✗ Open rear camera — couldn't reach the home screen", say(VoiceCameraTurn.answer(rear, toggle, Outcome.UNREACHABLE, Lang.EN)))
        assertTrue(VoiceFeedbackPhrase.merge(listOf(say(a)), Lang.VI)!!.startsWith("Chưa tắt camera"), "đọc lên là câu HỎNG")
    }

    /** [VoiceCameraTurn.run]: `next` đúng một lần, sau kết quả; kết quả về hai lần ⇒ lần sau bỏ; mã lạ ⇒ báo hỏng, không gửi. */
    @Test fun `run - di tiep dung mot lan, ket qua lan hai bi bo, thay the thi giao cho rerun`() {
        val said = ArrayList<String>()
        var nexts = 0
        val reruns = ArrayList<VoiceIntent>()
        var pending: ((Outcome) -> Unit)? = null
        VoiceCameraTurn.run(rear, Lang.VI, fire = { _, cb -> pending = cb }, onUi = { it() }, say = { said += it },
            next = { nexts++ }, rerun = { reruns += it })
        assertEquals(0, nexts, "chưa có kết quả ⇒ làn ghi CHỜ (vế sau chưa chạy)")
        pending!!(Outcome.OPENED)
        pending!!(Outcome.CLOSED)   // về muộn sau hạn chờ ⇒ bỏ
        assertEquals(listOf("✓ Đã mở camera sau"), said)
        assertEquals(1, nexts)

        said.clear(); nexts = 0
        VoiceCameraTurn.run(off, Lang.VI, fire = { _, cb -> cb(Outcome.NOTHING_TO_CLOSE) }, onUi = { it() }, say = { said += it },
            next = { nexts++ }, rerun = { reruns += it })
        assertEquals(listOf<VoiceIntent>(VoiceIntent.Control("cam", 0)), reruns, "thay thế đi đường nút xe của cầu")
        assertEquals(0, nexts, "vế thay thế TỰ gọi tiếp — gọi ở đây nữa là vế sau chạy hai lần")
        assertTrue(said.isEmpty(), "câu trả lời là của đường nút xe (≤ 2.92), không phải của camera")

        said.clear(); nexts = 0
        var fired = false
        VoiceCameraTurn.run(VoiceIntent.Launcher(LauncherActions.SETTINGS), Lang.VI, fire = { _, _ -> fired = true },
            onUi = { it() }, say = { said += it }, next = { nexts++ }, rerun = { reruns += it })
        assertEquals(false, fired, "không phải việc camera ⇒ không gửi gì")
        assertEquals(1, nexts)
        assertTrue(said.single().startsWith("✗ "), "${said.single()}")
    }
}
