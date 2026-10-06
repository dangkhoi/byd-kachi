package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.Strings
import com.byd.clusternav.launcher.camera.CameraDemand
import com.byd.clusternav.launcher.camera.CameraDemand.Outcome
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ 2.93 wave 2B · VOICE-CAM-OFF-360-FALLBACK — MỘT vế camera theo yêu cầu: hỏi controller, NÓI ĐÚNG việc đã xảy ra ═══
 *
 * Spec `docs/specs/kachi-293-cam.html` §10 Pass 1 D1 (senior review, [P1] chặn phát hành) + OQ6. Lỗi nó chữa [ĐO mã Pass 1]:
 * *"tắt camera"* trần từ 2.93 là `Launcher(CAM_OFF)` ⇒ `CloseAll`; khi KHÔNG có camera theo yêu cầu nào mở, lệnh ấy không làm
 * gì mà lời đáp vẫn *"✓ Tắt camera"*, trong khi Camera 360 của xe (người lái mở bằng *"mở camera"*) vẫn bật — một ✓ giả
 * theo đúng mắt người lái (R1.8). ≤ 2.92 câu ấy TẮT Camera 360.
 *
 * ## Luồng — controller trả KẾT QUẢ, không trả `Boolean`
 * [run] gửi lệnh qua [fire] (tầng `:app` `CameraDemandDispatch.fireForResult`: tiến trình chính gọi thẳng; `:wake` dùng
 * broadcast CÓ THỨ TỰ + `resultCode`) rồi CHỜ [Outcome] — vế này giữ `next` của làn ghi (`VoiceWriteLane`) tới khi biết:
 *  • [Outcome.OPENED] / [Outcome.CLOSED] ⇒ nói *"✓ Mở …"* / *"✓ Tắt …"* — từ wave 2B câu MỞ nói ĐÚNG chiều (Pass 1 chỉ nói
 *    tên vì `:wake` không biết camera nào đang hiện; đọc lên thành *"Đã camera sau"*);
 *  • [Outcome.NOTHING_TO_CLOSE] ⇒ việc có [LauncherActions.offFallback] (*"tắt camera"* trần) thì CHẠY LẠI vế thành nút xe
 *    ấy ([Answer.Rerun] — đường nút xe sẵn có: cổng hỏi lại + làn ghi + đọc lại, cùng câu trả lời ≤ 2.92); việc khác
 *    (*"tắt cam sau"* khi camera sau không mở) ⇒ nói thật là nó đang không mở, không ✓;
 *  • [Outcome.UNREACHABLE] ⇒ *"✗ … — chưa liên lạc được màn hình chính"*, KHÔNG rơi về Camera 360 (không biết camera theo
 *    yêu cầu có đang mở hay không — đoán là có thể tắt nhầm thứ người lái đang xem).
 *
 * ## Vì sao THUẦN và ở `:core`
 * Mọi thứ chạm Android (broadcast, `Looper`) nằm sau ba lambda; phần còn lại là một quyết định + một luật *"gọi tiếp đúng
 * một lần"* — kiểm được off-car bằng lambda giả (`VoiceCameraTurnTest`), và `VoiceDispatcher` (`:app`, sát trần 500 dòng)
 * chỉ thêm đúng một lời gọi.
 */
object VoiceCameraTurn {

    /** Việc phải làm sau khi biết kết quả: nói một dòng rồi đi tiếp, hoặc chạy lại vế thành một ý định khác. */
    sealed interface Answer {
        /** Nói [line] rồi gọi vế kế tiếp. */
        data class Say(val line: String) : Answer

        /** Chạy lại CHÍNH vế này thành [intent] qua đường thường của cầu (cổng hỏi lại + làn ghi) — vế mới tự gọi tiếp. */
        data class Rerun(val intent: VoiceIntent) : Answer
    }

    /**
     * Lời đáp cho vế [i] (lệnh [op]) khi controller báo [outcome] — theo tiếng GIỌNG NÓI [lang] (EN/VI, `voiceLangOf`).
     *
     * *"Tắt camera"* ([LauncherActions.CAM_OFF]) — nhãn đã là câu lệnh nên không ghép động từ; camera cụ thể ghép *"Mở "* /
     * *"Tắt "* đúng chiều ĐÃ xảy ra. Lời đáp lỗi dùng động từ của CÂU NÓI (mở/tắt) — không biết kết quả thì nói việc chưa
     * làm được, không bịa chiều.
     */
    fun answer(i: VoiceIntent.Launcher, op: CameraDemand.Op, outcome: Outcome, lang: Lang): Answer {
        val name = VoiceReply.labelOf(i.id, lang)
        val opening = op is CameraDemand.Op.Toggle || op is CameraDemand.Op.Open
        fun act(open: Boolean): String = if (op == CameraDemand.Op.CloseAll) name
        else (if (open) Strings.t("Mở ", "Open ", lang) else Strings.t("Tắt ", "Turn off ", lang)) + name
        return when (outcome) {
            Outcome.OPENED -> Answer.Say("✓ " + act(open = true))
            Outcome.CLOSED -> Answer.Say("✓ " + act(open = false))
            Outcome.NOTHING_TO_CLOSE -> LauncherActions.offFallback(i.id)?.let { Answer.Rerun(VoiceIntent.Control(it, 0)) }
                ?: Answer.Say(
                    if (op == CameraDemand.Op.CloseAll) Strings.t("Không có camera nào đang mở", "No camera is open", lang)
                    else Strings.fIn(lang, "{0} đang không mở", "{0} is not open", name),
                )
            Outcome.UNREACHABLE -> Answer.Say(
                "✗ " + act(opening) + " — " +
                    Strings.t("chưa liên lạc được màn hình chính", "couldn't reach the home screen", lang),
            )
        }
    }

    /**
     * Thi hành MỘT vế camera; [next] (vế kế tiếp của làn ghi) được gọi ĐÚNG MỘT LẦN — hoặc giao cho [rerun] (vế thay thế
     * tự gọi tiếp). Mã việc không phải camera ⇒ báo hỏng ngay (không gửi gì).
     *
     * @param fire gửi lệnh tới controller; gọi lại kết quả ĐÚNG một lần ở luồng nào cũng được (bản thật: luồng chính).
     *   Gọi lại lần hai (kết quả về muộn sau hạn chờ) ⇒ bỏ — câu đã nói rồi không rút lại được, và làn ghi đã đi tiếp.
     * @param onUi đẩy phần NÓI + đi tiếp về luồng vẽ (cùng lẽ `VoiceDispatcher.onUi`).
     */
    @Suppress("LongParameterList")
    fun run(
        i: VoiceIntent.Launcher,
        lang: Lang,
        fire: (CameraDemand.Op, (Outcome) -> Unit) -> Unit,
        onUi: (() -> Unit) -> Unit,
        say: (String) -> Unit,
        next: () -> Unit,
        rerun: (VoiceIntent) -> Unit,
    ) {
        val op = LauncherActions.cameraOp(i.id, i.off)
        if (op == null) {
            say(VoiceReply.failed(i, lang = lang))
            next()
            return
        }
        val once = AtomicBoolean(false)
        fire(op) { outcome ->
            if (!once.compareAndSet(false, true)) return@fire
            onUi {
                when (val a = answer(i, op, outcome, lang)) {
                    is Answer.Say -> { say(a.line); next() }
                    is Answer.Rerun -> rerun(a.intent)
                }
            }
        }
    }
}
