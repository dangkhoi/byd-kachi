package com.byd.clusternav.launcher.voice

import android.util.Log
import com.byd.clusternav.R

/**
 * ═══ MỘT LƯỢT NGHE CHÍNH của phiên — micro → chữ → bộ phân tích ═══════════════════════════════════════════════
 *
 * Tách khỏi `VoiceSession.kt` ở **VOICE-OPEN-TURN (2026-09-26)** vì trần 500 dòng (CLAUDE.md §4.1 ·
 * `VoiceListenWiringContractTest`): tệp kia đã 499/500 và OQ9 cần thêm một tham số vào đúng lời gọi
 * `capture.listen` nằm trong hàm này. **Pure move** — thân hàm nguyên văn, chỉ đổi ba thứ cơ học: `private fun
 * runSession` ⇒ hàm mở rộng [runListen], `TAG` ⇒ `VoiceSession.TAG`, và **một** tham số mới `openTurn = true`.
 *
 * ## Vì sao là hàm mở rộng, không phải lớp mới
 * Cùng lẽ đã ghi ở KDoc `VoiceSessionTurns.kt`: một lớp riêng phải mang bản sao của trạng thái phiên (số thế hệ ·
 * cờ huỷ · tấm chữ · micro đang mở) hoặc nhận chúng qua một nắm lambda — hai đường tới cùng một trạng thái, đúng
 * thứ đã phải vá ba lần. Hàm mở rộng dùng lại **đúng** các trường ấy.
 *
 * ## `openTurn = true` chỉ ở ĐÂY — và vì sao thế là đủ cho **cả hai** lối vào
 * Nút mic của màn chính và đường *"Hey Kachi"* (`:wake`) **không** có hai đường nghe: cả hai dựng một
 * [VoiceSession] (`KachiHomeWiring.voiceSession` · `VoiceWakeSessionFactory.buildSession`) và cùng chạy đúng hàm
 * này. Nên một chỗ bật là hai lối vào có — và `VoiceOpenTurnWiringContractTest` khoá đúng vế đó, thay vì tin vào
 * một câu trong tài liệu.
 *
 * Ba lượt NỐI (hội thoại · hỏi lại · xác nhận, ở `VoiceSessionTurns.kt`) **không** bật: chúng đã có vòng hỏi-đáp
 * riêng (R8/R9), không giữ PCM, và câu trả lời ở đó là một vế ngắn đã biết trước hình dạng.
 */
@Suppress("ReturnCount", "LongMethod")
internal fun VoiceSession.runListen(my: Int) {
    val tStart = System.currentTimeMillis()
    try {
        if (!capture.hasPermission()) { fail(my, R.string.kachi_voice_no_mic, openSettingsAction = true); return }
        if (!VoiceModelStore.isReady(ctx)) { fail(my, R.string.kachi_voice_no_model, openSettingsAction = true); return }

        val labels = appsByLabel()
        val rec = VoiceRecognizer.open(ctx, profiles(), labels.keys.toList(), labels.values.toSet(), places())
        if (rec == null) { fail(my, R.string.kachi_voice_engine_failed, openSettingsAction = true); return }

        rec.use {
            // Huỷ trong lúc đang dựng bộ nhận dạng (vài trăm ms đầu) ⇒ **không mở micro nữa**. Thiếu dòng
            // này thì một cú chạm huỷ vẫn cho ra một tiếng bíp + một lượt mở micro rồi tắt ngay.
            if (cancelled.get() || stale(my)) { closeIfMine(my); return }
            post { if (!stale(my)) overlay?.render(R.string.kachi_voice_listening, "") }
            // `whileCapturing` KHÔNG nhận tham số ⇒ `it` bên trong vẫn là recognizer của `rec.use` (một
            // lambda không tham số không dựng `it` riêng, nên không che `it` của lambda ngoài).
            // 1.70 [ĐO xe 2026-09-17] mốc "bấm phím → micro mở" từng mất **1,5 s** ở lượt đầu; in ra để lượt
            // xe sau biết phần nào (nhãn app · dựng recognizer · tấm chữ) ăn thời gian đó.
            Log.i(VoiceEngine.TIMING_TAG, "sẵn sàng nghe sau ${System.currentTimeMillis() - tStart} ms kể từ lúc bấm")
            val heard = whileCapturing {
                capture.listen(
                    it, VoiceSession.MAX_LISTEN_MS, cancelled::get, keepPcm = true,
                    onLevel = { rms -> post { if (!stale(my)) overlay?.level(rms) } },
                    openTurn = true,
                ) { partial ->
                    post { if (!stale(my)) overlay?.render(R.string.kachi_voice_listening, partial) }
                }
            }
            if (cancelled.get() || stale(my)) { closeIfMine(my); return }
            post { if (!stale(my)) overlay?.setPhase(false) }   // R3: hết nghe → waveform đứng yên (đang hiểu)
            Log.i(VoiceSession.TAG, "lượt 1 (ngữ pháp) nghe được: \"${heard.text}\"")
            // LƯỢT 2 — chỉ chạy khi lượt 1 có cụm MỞ TỪ VỰNG; xem KDoc [VoiceFreeTail] và [VoiceOpenVocab].
            val sentence = VoiceFreeTail.decode(ctx, heard)
            // H2 — ghi tiếng + số đo NGAY, trước mọi đường thoát dưới đây: ca *"nghe ra rỗng"* chính là ca
            // đáng nghe lại nhất, và nó thoát ở dòng sau. Ghi chạy trên luồng nền (xem [VoiceUtteranceLog]).
            logHeard(it, heard, sentence)
            if (cancelled.get() || stale(my)) { closeIfMine(my); return }
            if (sentence.isBlank()) {
                // 1.70 — im lặng ở lượt chính được NÓI RA (chữ + giọng), không chỉ hiện chữ: [ĐO xe
                // 2026-09-17] owner không nhìn màn khi lái, và một phiên kết thúc câm là *"không làm được gì"*.
                Log.i(VoiceSession.TAG, "quyết định: \"\" ⇒ không nghe thấy tiếng nào (không giải mã)")
                fail(my, R.string.kachi_voice_nothing_heard, openSettingsAction = false)
                post { if (!stale(my)) speakLines(listOf(ctx.getString(R.string.kachi_voice_nothing_heard))) }
                return
            }
            post { if (!stale(my)) execute(sentence) }
        }
    } catch (t: Throwable) {
        // Một launcher KHÔNG được chết vì tính năng phụ: mã native của Kaldi có thể ném `Error`.
        Log.e(VoiceSession.TAG, "phiên nghe hỏng", t)
        fail(my, R.string.kachi_voice_engine_failed, openSettingsAction = false)
    }
}

