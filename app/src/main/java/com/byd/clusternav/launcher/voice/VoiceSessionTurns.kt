package com.byd.clusternav.launcher.voice

import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.R
import java.util.concurrent.atomic.AtomicBoolean
import com.byd.clusternav.VOICE_FOLLOW_UP_DEFAULT_MS
import com.byd.clusternav.voiceFollowUpMs

/**
 * ═══ V3 · R8/R9 — CÁC LƯỢT NGHE **NỐI** của một phiên: hỏi lại · hội thoại ═══════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` R8 (*"hỏi lại cho tới khi hiểu"* — owner **D2**) và R9
 * (*"giữ mic 5 s cho câu tiếp"* — owner **D1**).
 *
 * ## Vì sao là **hàm mở rộng của chính [VoiceSession]**, không phải một lớp mới
 * Cùng cách `WorkspacePrefs` tách `WorkspacePrefsProfile.kt` và `ClusterNavBridge` tách `…Cast/Keys.kt`: bề mặt
 * gọi vẫn phẳng, mà mỗi tệp vẫn dưới trần 500 dòng của dự án (CLAUDE.md §4.1).
 *
 * Một lớp riêng ở đây sẽ phải mang **bản sao của trạng thái phiên** (số thế hệ · cờ huỷ · cờ micro đang mở · tấm
 * chữ) hoặc nhận chúng qua tám lambda. Cả hai đều là đường thứ hai tới cùng một trạng thái — mà chính trạng thái
 * ấy là thứ ba lượt soát trước đã phải vá ba lần (`generation` · `capturing` · `confirmOpen`). Hàm mở rộng dùng
 * lại **đúng** các trường đó, nên không có khe nào để lệch.
 */
// ── H2 — NHẬT KÝ LƯỢT NÓI: hai nửa của MỘT mục, ghi ở hai thì ────────────────────────────────

/**
 * ═══ Nửa ĐẦU — tiếng + mọi số đo đã biết NGAY khi nghe xong ══════════════════════════════════════════
 *
 * Gọi ngay sau lượt giải mã, **trước** mọi đường thoát của `runSession`: ca *"nghe ra rỗng"* thoát ở dòng kế tiếp
 * và nó chính là ca đáng nghe lại nhất — một mục nhật ký chỉ ghi khi câu đã hiểu được thì nó ghi đúng những lượt
 * không cần chẩn đoán gì.
 *
 * ## Vì sao ở đây, không ở trong [VoiceCapture]
 * [VoiceCapture] biết tiếng nhưng **không** biết câu cuối cùng (lượt 2 tự do chạy sau nó), không biết mô hình nào
 * đang chọn, không biết phiên có hỏi lại hay không. Ghi ở đó là ghi một nửa rồi phải mở một đường thứ hai để bù
 * nửa kia — đúng thứ [VoiceUtteranceLog.update] đang làm, nhưng từ một chỗ không có gì để bù.
 *
 * Lớp gọi vẫn KHÔNG chặn: [VoiceUtteranceLog.record] chỉ chép khúc PCM rồi đẩy hết sang luồng nền của nó.
 */
internal fun VoiceSession.logHeard(rec: VoiceRecognizer, heard: VoiceCapture.Heard, sentence: String) {
    val meta = VoiceUtteranceLog.Meta(
        heard = heard.text,
        sentence = sentence,
        micSource = heard.micSource,
        micSourceName = VoiceMicSource.sourceName(heard.micSource),
        modelId = runCatching { VoiceModelStore.selected(ctx).id }.getOrDefault(""),
        hotwords = runCatching { rec.hotwordLines() }.getOrDefault(0),
        endpointMs = heard.endpointMs,
        speechMs = heard.speechMs,
        silenceMs = heard.silenceMs,
        endpointFired = heard.endpointFired,
        listenMs = heard.listenMs,
        decodeMs = heard.decodeMs,
    )
    utteranceMeta = meta
    utteranceStamp = runCatching { VoiceUtteranceLog.record(ctx, heard.pcm, heard.samples, meta) }
        .onFailure { Log.w(VoiceSession.TAG, "không ghi được nhật ký lượt nói", it) }
        .getOrNull()
}

/**
 * ═══ Nửa SAU — ý định + câu trả lời, thứ chỉ biết được sau khi đã thi hành ════════════════════════════
 *
 * Ghi đè **đúng tệp JSON** của mốc đã tạo ở [logHeard]; tệp WAV không bị đụng tới.
 *
 * ⚠ Mốc bị **xoá khỏi phiên** ngay sau khi dùng. Một phiên có thể chạy nhiều lượt `execute` (hỏi lại · hội thoại),
 * và chỉ lượt ĐẦU có tiếng được giữ (`keepPcm = true` — các lượt nối cố ý không giữ, xem KDoc [listenOnce]). Không
 * xoá thì câu trả lời của lượt thứ ba sẽ được ghi đè lên mục mô tả **khúc tiếng của lượt thứ nhất** — một tệp JSON
 * nói về một tệp WAV khác, tức đúng loại dữ liệu sai mà không ai phát hiện khi đọc lại ba tháng sau.
 *
 * `followUp` = *"phiên này ĐÃ nối ít nhất một lượt"*, không phải *"lượt này là lượt nối"*: bộ đếm tăng ở
 * [followUp] sau khi câu trả lời đã đọc xong, nên tại đây nó còn là số của các lượt TRƯỚC — và đó đúng là thứ
 * đáng biết khi nghe lại (*"khúc này nằm ở giữa một cuộc hội thoại"*).
 */
internal fun VoiceSession.logDone(intents: List<VoiceIntent>, replies: List<String>) {
    val stamp = utteranceStamp ?: return
    utteranceStamp = null
    val base = utteranceMeta ?: VoiceUtteranceLog.Meta()
    runCatching {
        VoiceUtteranceLog.update(
            ctx, stamp,
            base.copy(
                intents = intents.map { it::class.simpleName.orEmpty() },
                decision = lastDecision,
                replies = ArrayList(replies),
                clarify = clarifyRound > 0,
                followUp = followUps > 0,
            ),
        )
    }.onFailure { Log.w(VoiceSession.TAG, "không cập nhật được nhật ký lượt nói", it) }
}

// ── V3 · R8 — hỏi lại tới khi hiểu ───────────────────────────────────────────────────────────

/**
 * Câu hỏi cho lượt này, hoặc `null` khi **không nên hỏi**.
 *
 * Chỉ hỏi khi **cả câu** là một [VoiceIntent.Unknown]. Câu ghép có một vế hiểu được thì vế ấy đã chạy và
 * `VoiceReply` đã nói ra vế bị bỏ ([VoiceUnknownReason.DROPPED_CLAUSE]) — chen một câu hỏi vào đó là hỏi về
 * một việc trong khi một việc khác vừa xảy ra, và người lái không biết câu trả lời của mình thuộc về cái nào.
 */
internal fun VoiceSession.clarifyAsk(intents: List<VoiceIntent>): VoiceClarify.Ask? {
    val only = intents.singleOrNull() as? VoiceIntent.Unknown ?: return null
    return VoiceClarify.ask(only, clarifyRound)
}

/**
 * ═══ [SOÁT Pass 1 · P2] HẾT TRẦN HỎI ⇒ **bỏ cuộc lịch sự**, và đó là một câu KHÁC ═══════════════════
 *
 * Trả `true` khi đã nói câu bỏ cuộc (chỗ gọi dừng, không thi hành gì nữa).
 *
 * ## Vì sao không để nó rơi về câu *"không hiểu"* thường
 * [VoiceClarify.ask] trả `null` ở **hai** ca khác hẳn nhau: *"hỏi cũng không giúp gì"* (câu rỗng, từ vựng mở…)
 * và *"đã hỏi đủ [VoiceClarify.MAX_ROUNDS] lượt"*. Ca đầu thì câu trả lời thường của `VoiceReply.unknown` là
 * đúng. Ca sau thì người lái vừa nói **ba** lần và nghe **ba** câu cùng nghĩa — đúng chỗ [VoiceClarify.giveUp]
 * sinh ra để nêu một ví dụ có thật (*"thử nói «bật đèn đọc»"*). Phân biệt bằng cách hỏi lại chính `ask` với
 * `round = 0`: **luật ở `:core`**, không chép một bản sao của bảng lý do vào đây (CLAUDE.md §7).
 *
 * Đặt [VoiceSession.clarifyRound] về 0 ngay: phiên có thể còn chạy tiếp (hội thoại), và lượt sau phải được hỏi
 * lại từ đầu chứ không kế thừa trần của câu vừa bỏ.
 *
 * ## ⚠ [ĐO xe 2026-09-18 §B] Cùng bệnh *"overlay tắt giữa câu"*, ở một đường KHÁC
 * Đường này cũng **đọc** một câu, mà hẹn đóng của nó đặt **trước** lượt đọc bằng [VoiceSession.LINGER_MS] = 2,5 s
 * — trong khi [VoiceClarify.giveUp] dài ~40 ký tự ([SUY] ≈ 2,6 s ở nhịp đọc [ĐO host] ~65 ms/ký tự, và dưới load
 * 14 thì lâu hơn nữa) ⇒ tấm chữ đi trước khi loa nói hết, đúng thứ owner báo. Bản vá §B chỉ chữa `execute`; ở đây
 * dùng **cùng** lưới ([VoiceSpeakBudget]) và **cùng** mốc rút ([VoiceSession.LINGER_MS] sau khi đọc xong — khuôn
 * của nhánh `flushed` trong `execute`). [my] có mặt chỉ để mốc đọc-xong về muộn không rút tấm chữ của phiên KHÁC.
 */
internal fun VoiceSession.clarifyGaveUp(intents: List<VoiceIntent>, my: Int): Boolean {
    if (clarifyRound < VoiceClarify.MAX_ROUNDS) return false
    val only = intents.singleOrNull() as? VoiceIntent.Unknown ?: return false
    if (VoiceClarify.ask(only, 0) == null) return false
    clarifyRound = 0
    val line = VoiceClarify.giveUp()
    VoiceChime.error()   // R1 voice-ux: earcon "chưa hiểu"
    overlay?.render(R.string.kachi_voice_heard, line)
    scheduleClose(VoiceSpeakBudget.estimateMs(listOf(line), VoiceSession.SPEAK_SAFETY_MS))
    speakLines(listOf(line)) { post { if (!stale(my)) scheduleClose(VoiceSession.LINGER_MS) } }
    return true
}

/**
 * Hỏi một câu ngắn rồi **nghe tiếp**, và ghép câu trả lời với ngữ cảnh đã có.
 *
 * Trần [VoiceClarify.MAX_ROUNDS] nằm trong chính [VoiceClarify.ask] (`null` khi hết lượt), nên vòng này
 * không thể chạy mãi: mỗi lượt tăng [clarifyRound], và lượt thứ ba luôn rơi vào [VoiceClarify.giveUp].
 */
internal fun VoiceSession.askAgain(ask: VoiceClarify.Ask, my: Int) {
    clarifyRound++
    go(VoiceTurnPhase.CLARIFYING)   // B1: EXECUTING → CLARIFYING (sẽ về LISTENING khi mở lượt nghe)
    overlay?.render(R.string.kachi_voice_confirm_title, ask.question)
    scheduleClose(VoiceSession.CLARIFY_LISTEN_MS + VoiceSession.LINGER_MS)
    // ⚠⚠ [ĐO xe 2026-09-20 §5] CHỐT MỘT-LƯỢT, cùng khuôn `listening` của [askAloudThenListen]. Hợp đồng
    // [VoiceSpeaker.speak] là *"luôn gọi onDone, KỂ CẢ khi trả false"* ⇒ máy đọc chưa nối được
    // (`TextToSpeech: not bound to TTS engine`, nhật ký xe) thì onDone chạy NGAY **và** `spoke` = false, nên bản
    // cũ gọi [listenAgain] HAI lần: lượt hai bị `VoiceSingleFlight` chối mic ⇒ chuỗi rỗng ⇒ [endsConversation] ⇒
    // đóng tấm chữ sau LINGER 2,5 s **giữa** lượt nghe thứ nhất = *"overlay vẽ rồi biến mất"*. Cũng hết tiêu
    // hai suất `followUps` cho một lượt hỏi.
    val opened = AtomicBoolean(false)
    fun open() { if (!micOpen() && opened.compareAndSet(false, true)) listenAgain(ask, my) }
    // Đọc câu hỏi XONG rồi mới mở micro (cùng hazard [askAloudThenListen]: mic mở lúc loa nói ⇒ Kachi nghe chính
    // mình). Khác ở chỗ đây **luôn** đọc nếu có giọng: owner chỉ chốt không đọc *câu hỏi XÁC NHẬN*.
    val spoke = if (speakReplies()) {
        runCatching { speaker.speak(ask.question) { post { open() } } }
            .onFailure { Log.w(VoiceSession.TAG, "không đọc được câu hỏi lại", it) }
            .getOrDefault(false)
    } else {
        false
    }
    // Không đọc được ⇒ vẫn PHẢI mở mic (owner: *"không được dừng khi chưa xong việc"*).
    if (!spoke) open()
}

private fun VoiceSession.listenAgain(ask: VoiceClarify.Ask, my: Int) {
    if (cancelled.get() || stale(my)) return
    // ═══ [P0-1c] Lượt HỎI LẠI cũng là một lần mở micro ⇒ cũng phải tiêu một suất của trần phiên ═════════
    // [ĐO xe 2026-09-16] 7 phiên thật sự bắt đầu mà có **309** lượt mở micro, dù `MAX_FOLLOW_UPS` = 5 đã tồn tại.
    // [SUY, đọc mã] một trong hai chỗ rò là đây: đường hỏi-lại mở micro nhưng **không** đi qua `followUps`, nên
    // nó là một hạn mức thứ hai chạy song song — và `execute` đặt `clarifyRound = 0` mỗi lượt đi lọt, nên hạn
    // mức ấy tự nạp lại. Đếm chung một quỹ thì cả hai đường cộng lại vẫn không vượt được trần của phiên.
    if (followUps >= VoiceSession.MAX_FOLLOW_UPS) {
        Log.i(VoiceSession.TAG, "hết quỹ lượt nối của phiên — không hỏi lại nữa")
        overlay?.render(R.string.kachi_voice_heard, VoiceClarify.giveUp())
        scheduleClose(VoiceSession.LINGER_MS)
        return
    }
    followUps++
    go(VoiceTurnPhase.LISTENING)   // B1: CLARIFYING → LISTENING (cổng canOpenMic mở đúng ở đây)
    background {
        val heard = listenOnce(
            my, VoiceSession.CLARIFY_LISTEN_MS, beep = false,
            hint = R.string.kachi_voice_listening, label = VoiceCapture.LABEL_CLARIFY,
        )
        post {
            if (cancelled.get() || stale(my)) { closeIfMine(my); return@post }
            val joined = VoiceClarify.combine(ask.carry, heard)
            // [P0-1b] Rỗng **hoặc chỉ toàn từ đệm** ⇒ coi như không có câu trả lời. Xem KDoc [endsConversation].
            if (endsConversation(heard)) {
                overlay?.render(R.string.kachi_voice_heard, VoiceClarify.giveUp())
                scheduleClose(VoiceSession.LINGER_MS)
            } else {
                execute(joined)
            }
        }
    }
}

/**
 * ═══ [P0-1b] Chuỗi này có KẾT THÚC cuộc hội thoại không ═══════════════════════════════════════════════
 *
 * `true` khi lượt nghe không mang một lượt nói thật nào: rỗng, hoặc **chỉ toàn từ đệm**.
 *
 * ## Vì sao "chỉ toàn từ đệm" phải bằng "im lặng"
 * [ĐO xe 2026-09-16] trong 12 phút, mô hình trả về `"ừ"` **102 lần** và `"ừm"` **102 lần** — gần như toàn bộ
 * ~250 lượt giải mã — trong lúc micro chỉ nghe thấy nền (rms 30–36). Đó là **ảo giác** của mô hình offline khi
 * đưa vào gần như im lặng, không phải người lái nói. Mỗi chuỗi như thế mà được coi là một lượt nói sẽ: không
 * `isBlank()`, nên vòng hội thoại mở lại micro; parse ra `Unknown`, nên Kachi nói *"không hiểu"*; rồi mở lại.
 * Đó chính là vòng lặp đã làm ô YouTube của owner không cuộn nổi.
 *
 * ## ⚠ Vì sao hàm này KHÔNG được dùng ở lượt nghe XÁC NHẬN
 * Một tiếng *"ừ"* đứng một mình là câu **ĐỒNG Ý hợp lệ** khi đang có hộp xác nhận chờ. `listenForConfirm` hỏi
 * [VoiceLexicon.confirmAnswer] và chỉ hỏi nó — đảo thứ tự hai phép hỏi là bịt mất cổng an toàn quan trọng nhất
 * của cả tính năng. KDoc [VoiceLexicon.isFillerOnly] ghi đúng ràng buộc này ở phía `:core`.
 *
 * Luật nằm ở `:core` ([VoiceLexicon.isFillerOnly], dựng trên tập `FILLERS` đã có) — ở đây chỉ là chỗ gọi, để
 * bảng từ đệm không có bản sao thứ hai (CLAUDE.md §4.1).
 */
internal fun endsConversation(heard: String): Boolean =
    heard.isBlank() || VoiceLexicon.isFillerOnly(heard)

// ── V3 · R9 — HỘI THOẠI: giữ micro mở sau khi trả lời xong ───────────────────────────────────

/**
 * ═══ Giữ micro mở [Prefs.voiceFollowUpMs] sau khi đã trả lời xong (owner **D1** 2026-09-16) ══════════
 *
 * ## Bốn cổng, và mỗi cổng đóng một ca hỏng khác nhau
 *  1. **[pending]** — còn vế đang tra mạng ⇒ không mở (xem chỗ gọi).
 *  2. **[confirmOpen]** — đang có hộp xác nhận ⇒ lượt nghe của nó đã sở hữu micro; mở thêm một
 *     `AudioRecord` thứ hai là đúng hazard đã ghi ở KDoc [cancel]. [SOÁT Pass 1 · P2] đi kèm
 *     [VoiceSession.micOpen]: `confirmOpen` bắt ca *"sắp mở"*, `micOpen` bắt ca **đang mở thật** — hai cờ, hai
 *     khoảnh khắc (xem KDoc [VoiceSession.confirmOpen]), và chỉ có cái thứ hai chặn được một lượt nghe nối còn
 *     đang chạy khi câu trả lời trước về (`speakLines` bỏ qua lượt đọc ⇒ `onDone` bắn NGAY).
 *  3. **[VoiceSession.MAX_FOLLOW_UPS]** — người lái bỏ đi giữa chừng thì micro không được nối vô hạn. 5 lượt × 5 giây là
 *     trần cứng của một phiên, kể cả khi cabin ồn làm mỗi lượt nghe ra một chuỗi rác.
 *  4. **[cancelled]/[stale]** — một cú chạm huỷ, hoặc một phiên mới, cắt vòng ngay.
 *
 * Không bíp đầu lượt ([beep] = false): tiếng bíp là mốc *"tôi bắt đầu nghe"* cho một phiên do người dùng mở;
 * ở đây micro chỉ **chưa đóng**, và một tiếng bíp sau mỗi câu trả lời là thứ làm người ta tắt tính năng.
 */
internal fun VoiceSession.followUp(my: Int, pending: Boolean) {
    if (pending || cancelled.get() || stale(my) || confirmOpen.get() || micOpen()) return
    val window = runCatching { Prefs.voiceFollowUpMs(ctx) }.getOrDefault(VOICE_FOLLOW_UP_DEFAULT_MS)
    if (window <= 0 || followUps >= VoiceSession.MAX_FOLLOW_UPS) return
    followUps++
    go(VoiceTurnPhase.FOLLOW_UP); go(VoiceTurnPhase.LISTENING)   // B1: EXECUTING → FOLLOW_UP → LISTENING
    overlay?.render(R.string.kachi_voice_follow_up, "")
    scheduleClose(window.toLong() + VoiceSession.LINGER_MS)
    background {
        val heard = listenOnce(
            my, window.toLong(), beep = false,
            hint = R.string.kachi_voice_follow_up, label = VoiceCapture.LABEL_FOLLOW_UP,
        )
        post {
            if (cancelled.get() || stale(my)) { closeIfMine(my); return@post }
            // Hết giờ mà không ai nói ⇒ **đóng êm**: không câu báo lỗi, không tiếng gì. Đây là trạng thái
            // THƯỜNG của hội thoại (người ta nói xong một việc rồi thôi), không phải một lỗi để kể.
            // [P0-1b] *"Chỉ toàn từ đệm"* đi CÙNG nhánh với rỗng — xem KDoc [endsConversation]: đó là ảo giác
            // của mô hình khi nghe im lặng, và coi nó là một lượt nói chính là nhiên liệu của vòng lặp.
            if (endsConversation(heard)) {
                if (heard.isNotBlank()) Log.i(VoiceSession.TAG, "lượt nối chỉ có từ đệm (\"$heard\") — đóng êm")
                close()
            } else {
                execute(heard)
            }
        }
    }
}

/**
 * Một lượt nghe ngắn dùng chung cho hỏi-lại và hội thoại. **CHẶN** ⇒ chỗ gọi đưa vào [background].
 *
 * Không giữ PCM: cả hai đường đều nhận câu **ngắn** trong tập đóng; lượt giải mã tự do (`VoiceFreeTail`) chỉ
 * cần cho tên bài/điểm đến, và một câu như vậy sẽ được nói ở một phiên đầy đủ chứ không phải trong 5 giây nối.
 */
private fun VoiceSession.listenOnce(
    my: Int,
    maxMs: Long,
    beep: Boolean,
    hint: Int,
    label: String,
): String = runCatching {
    val labels = appsByLabel()
    VoiceRecognizer.open(ctx, profiles(), labels.keys.toList(), labels.values.toSet(), places())?.use {
        whileCapturing {
            post { if (!stale(my)) overlay?.setPhase(true) }   // R3: lượt nối → waveform sống lại
            capture.listen(
                it, maxMs, { cancelled.get() || stale(my) }, keepPcm = false, beep = beep,
                // [P0-1a] Lượt NỐI bỏ giải mã khi không có tiếng (im lặng là THƯỜNG; giải mã 1,3–2s chỉ để mô hình bịa "ừ" = nuôi loop) — xem `decodeOnlyIfSpeech`.
                decodeOnlyIfSpeech = true, label = label,
                onLevel = { rms -> post { if (!stale(my)) overlay?.level(rms) } },
            ) { partial ->
                post { if (!stale(my)) overlay?.render(hint, partial) }
            }.text
        }
    }.orEmpty()
}.onFailure { Log.w(VoiceSession.TAG, "lượt nghe nối hỏng", it) }.getOrDefault("")

// ── Lượt nghe của CỔNG XÁC NHẬN (OQ4 + "đồng ý/huỷ") — cùng vai: một lượt nghe NỐI của phiên ──

/**
 * ═══ OQ4 · ĐỌC câu hỏi XONG rồi mới mở micro — **TẮT SẴN**, bật bằng `voice_ask_aloud` ═══════════
 *
 * ## ⚠ Hôm nay đường này KHÔNG chạy (owner chốt 2026-09-16)
 * Owner chốt *"không đọc câu hỏi xác nhận, chỉ đọc phản hồi sau lệnh"* ⇒ mã ở lại sau công tắc mặc định
 * **tắt** ([askAloud]) chứ không bị gỡ: thứ bị bác là **hành vi mặc định**, không phải cơ chế — gỡ thì lần
 * sau muốn thử lại phải dựng lại cả hợp đồng ba vế. Phần dưới mô tả đường khi công tắc BẬT.
 *
 * ## Vì sao không mở micro ngay như 1.65
 * [speakLines] có cổng `confirmOpen` để **không** đọc gì trong lúc hộp xác nhận mở — tức tới 1.65 câu hỏi
 * *"Mở khoá cửa?"* chỉ **hiện chữ**. Người lái đang nhìn đường thì không đọc được nó, nên cổng an toàn quan
 * trọng nhất của cả tính năng lại là cổng duy nhất câm. Đọc nó lên thì phải trả lời được câu *"khi nào mở
 * micro"*: mở ngay là Kachi nghe chính mình đọc câu hỏi (KDoc [speakLines] đã tả đúng hazard đó).
 *
 * ## Ba tính chất
 *  1. **Đọc xong mới nghe** — [VoiceSpeaker.speak] báo mốc xong; mốc ấy có thể tới từ luồng của engine đọc
 *     nên nó được đẩy về luồng vẽ bằng [post] trước khi chạm tới trạng thái phiên.
 *  2. **Hạn cứng [VoiceSession.ASK_ALOUD_CAP_MS]** — một engine chết giữa chừng không được phép giữ cổng an toàn đóng
 *     mãi. Hết hạn thì vẫn mở micro (đúng luật CLAUDE.md §3: *"không gate một đường phục hồi bằng dữ liệu mà
 *     chỉ chính đường đó mới làm mới được"*). `AtomicBoolean` giữ đúng MỘT lượt nghe dù cả hai đường cùng về.
 *  3. **Người lái bấm nút trước** ⇒ [answered] đã `true` ⇒ không mở micro nữa, và câu đang đọc bị cắt —
 *     nói nốt một câu hỏi vừa được trả lời là mô tả một việc đã xong.
 *
 * Không đọc (công tắc OQ4 tắt — mặc định · tắt công tắc R4 · máy không có giọng) ⇒ **y như 1.65**: mở micro
 * ngay.
 */
internal fun VoiceSession.askAloudThenListen(question: String, answered: AtomicBoolean, my: Int) {
    val listening = AtomicBoolean(false)
    fun openMic() {
        if (!listening.compareAndSet(false, true)) return
        post {
            // ⚠ [SOÁT Pass 4 · P2] [cancelled] phải đứng cạnh [answered]/[stale]: [cancel] cắt câu đang đọc
            // TRƯỚC khi post `answerConfirm(false)`, mà cắt câu là một mốc *"đọc xong"* ⇒ nó rơi đúng khe
            // [answered] còn `false` và thế hệ chưa đổi (huỷ KHÔNG tăng thế hệ, khác [stop]) ⇒ một cú chạm
            // huỷ vẫn mở micro + kêu bíp, chồng lên phiên người lái bấm ngay sau đó.
            if (cancelled.get() || answered.get() || stale(my)) return@post
            // Hẹn giờ đóng đặt ở ĐÂY, không ở đầu [confirm]: tấm chữ phải sống đủ *cả* lượt đọc lẫn lượt
            // nghe. Đặt trước khi đọc thì một câu hỏi 4 giây ăn hết trần và tấm chữ biến mất giữa lượt nghe.
            scheduleClose(VoiceSession.CONFIRM_LISTEN_MS + VoiceSession.LINGER_MS)
            go(VoiceTurnPhase.LISTENING)   // B1: CONFIRMING → LISTENING (mở lượt nghe "đồng ý/huỷ")
            background { listenForConfirm(answered, my) }
        }
    }
    // ⚠ Cổng THỨ NHẤT là công tắc riêng của OQ4, **mặc định tắt** (owner 2026-09-16: chỉ đọc phản hồi sau
    // lệnh). Tắt ⇒ y hệt 1.65: mở micro ngay, không lượt đọc nào chen giữa câu hỏi và micro.
    if (!askAloud() || !speakReplies() || !speaker.available()) { openMic(); return }
    val spoken = runCatching { speaker.speak(question) { openMic() } }
        .onFailure { Log.w(VoiceSession.TAG, "không đọc được câu hỏi xác nhận", it) }
        .getOrDefault(false)
    if (!spoken) { openMic(); return }
    // Tấm chữ phải sống qua cả lượt đọc; hẹn giờ thật sẽ được đặt lại trong [openMic].
    scheduleClose(VoiceSession.ASK_ALOUD_CAP_MS + VoiceSession.CONFIRM_LISTEN_MS + VoiceSession.LINGER_MS)
    ui.postDelayed({
        if (listening.get()) return@postDelayed
        // ⚠ `${…}` chứ không `$VoiceSession.ASK_ALOUD_CAP_MS`: dạng sau in ra **đối tượng companion** rồi nối
        // chuỗi ".ASK_ALOUD_CAP_MS" — một dòng nhật ký vô nghĩa đúng ở chỗ cần đọc con số ([SOÁT Pass 1 · P3]).
        Log.i(VoiceSession.TAG, "câu hỏi xác nhận chưa báo đọc xong sau ${VoiceSession.ASK_ALOUD_CAP_MS} ms — mở micro theo hạn")
        openMic()
    }, VoiceSession.ASK_ALOUD_CAP_MS)
}

/** Lượt nghe thứ hai, **chỉ** để lấy một câu trả lời có/không. */
private fun VoiceSession.listenForConfirm(answered: AtomicBoolean, my: Int) {
    val heard = runCatching {
        val labels = appsByLabel()
        VoiceRecognizer.open(ctx, profiles(), labels.keys.toList(), labels.values.toSet())?.use {
            whileCapturing {
                // `keepPcm = false`: lượt này chỉ bắt *"đồng ý"/"huỷ"* — không bao giờ cần lượt giải mã thứ hai.
                // ⚠ `decodeOnlyIfSpeech = true` nhưng **KHÔNG** dùng [endsConversation] ở dưới: im lặng ở cổng
                // xác nhận đã có sẵn nghĩa đúng (= KHÔNG, xem KDoc `confirm`), còn một tiếng *"ừ"* có tiếng thật
                // thì vẫn phải tới được [VoiceLexicon.confirmAnswer] — nó là câu ĐỒNG ý hợp lệ duy nhất ở đây.
                capture.listen(
                    it, VoiceSession.CONFIRM_LISTEN_MS, { cancelled.get() || answered.get() || stale(my) },
                    decodeOnlyIfSpeech = true, label = VoiceCapture.LABEL_CONFIRM,
                ) { partial ->
                    post { if (!stale(my)) overlay?.render(R.string.kachi_voice_confirm_title, partial) }
                }.text
            }
        }.orEmpty()
    }.onFailure { Log.w(VoiceSession.TAG, "lượt nghe xác nhận hỏng", it) }.getOrDefault("")
    val answer = VoiceLexicon.confirmAnswer(heard)
    Log.i(VoiceSession.TAG, "câu trả lời xác nhận: \"$heard\" ⇒ $answer")
    // `null` (không phải câu trả lời) cũng là KHÔNG — xem KDoc [confirm].
    post { if (!stale(my)) answerConfirm(answer == true) }
}

// ── CỔNG XÁC NHẬN: hỏi lại trước khi bắn (tách khỏi VoiceSession theo VAI, trần 500 dòng) ─────

/**
 * Hỏi lại trước khi bắn.
 *
 * ## Vì sao mặc định là KHÔNG, và vì sao chỉ nhận đúng hai câu
 * Cổng này tồn tại vì *"mở khoá cửa"* nghe nhầm một lần là xe mở khoá giữa bãi đỗ. Một lượt nghe ngắn chỉ
 * để bắt *"đồng ý"* / *"huỷ"* ([VoiceLexicon.confirmAnswer] — cố ý không nhận `ừ`/`vâng`, xem KDoc ở đó):
 * nghe được gì khác, hết giờ, hay người dùng bỏ đi ⇒ **[onNo]**. Im lặng không bao giờ được hiểu là đồng ý.
 *
 * Nút bấm vẫn còn nguyên cho người không muốn nói lần thứ hai — hai đường, một quyết định, và cả hai đều
 * gọi đúng một trong hai lambda đúng một lần ([answerConfirm] giữ điều đó).
 */
internal fun VoiceSession.confirm(question: String, onYes: () -> Unit, onNo: () -> Unit) {
    val answered = AtomicBoolean(false)
    confirmOpen.set(true)
    go(VoiceTurnPhase.CONFIRMING)   // B1: EXECUTING → CONFIRMING
    pendingConfirm = { yes ->
        if (answered.compareAndSet(false, true)) {
            pendingConfirm = {}
            confirmOpen.set(false)
            // OQ4 — người lái bấm nút **trong lúc câu hỏi còn đang đọc** ⇒ cắt câu ngay. Đọc nốt một câu hỏi
            // vừa được trả lời là mô tả một việc đã xong, và tệ hơn: nó chồng lên câu kết quả ngay sau đó.
            runCatching { speaker.stop() }
            if (yes) { go(VoiceTurnPhase.EXECUTING); onYes() } else onNo()   // B1: CONFIRMING → EXECUTING nếu đồng ý
            scheduleClose(VoiceSession.LINGER_MS)
        }
    }
    overlay?.render(
        R.string.kachi_voice_confirm_title,
        question,
        ctx.getString(R.string.kachi_voice_confirm_yes),
    ) { answerConfirm(true) }
    askAloudThenListen(question, answered, generationNow())
}

internal fun VoiceSession.answerConfirm(yes: Boolean) = pendingConfirm(yes)

// ── B1 (1.70) — CỔNG CHUYỂN PHA qua máy trạng thái :core (tách khỏi VoiceSession theo VAI, trần 500) ────

/**
 * Chuyển pha qua máy `:core` ([VoiceTurnMachine]). Trả `true` nếu chuyển hợp lệ (đã đặt pha mới); `false` + log
 * nếu KHÔNG hợp lệ (giữ nguyên pha — một chuyển sai là dấu hiệu lỗi luồng, phải thấy trong nhật ký, không nuốt).
 *
 * Bài canh `VoiceTurnPhaseWiringContractTest` khoá việc mọi mốc vòng đời đi qua đúng cổng này.
 */
internal fun VoiceSession.go(to: VoiceTurnPhase): Boolean {
    val from = phase.get()
    val next = VoiceTurnMachine.next(from, to)
    if (next == null) { Log.w(VoiceSession.TAG, "pha: chuyển KHÔNG hợp lệ $from ⇒ $to (giữ nguyên)"); return false }
    phase.set(next)
    return true
}

// ── Báo lỗi phiên (tách khỏi VoiceSession theo VAI, trần 500 dòng) ────────────────────────────

/** Báo lỗi phiên trên tấm chữ (thiếu quyền micro / chưa tải mô hình / engine hỏng) rồi hẹn đóng. */
internal fun VoiceSession.fail(my: Int, msgRes: Int, openSettingsAction: Boolean) = post {
    if (stale(my)) return@post
    overlay?.render(
        msgRes,
        "",
        if (openSettingsAction) ctx.getString(R.string.kachi_voice_open_settings) else null,
    ) { close(); openPermissions() }
    scheduleClose(if (openSettingsAction) VoiceSession.FAIL_LINGER_MS else VoiceSession.LINGER_MS)
}

// ── ĐỌC XONG câu trả lời ⇒ quyết tấm chữ sống tiếp bao lâu (fix "overlay tắt giữa câu", owner 2026-09-17) ──

/**
 * Gọi ở mốc TTS **đã đọc xong** câu trả lời (onDone của [VoiceSession.speakLines]). Tách khỏi `execute` để mốc
 * "đọc xong" tường minh: [ĐO owner 2026-09-17] tấm chữ từng biến giữa câu vì hẹn đóng theo hằng số ngay lúc
 * execute; nay nó chỉ bắt đầu đếm nán SAU khi loa đã đọc hết, nên người lái nghe/nhìn trọn câu rồi tấm chữ mới đi.
 */
internal fun VoiceSession.onReplyDone(my: Int, pending: Boolean, endSession: Boolean = false) {
    if (cancelled.get() || stale(my)) { closeIfMine(my); return }
    if (endSession) { scheduleClose(VoiceSession.LINGER_MS); return }   // Req2: câu kết thúc ⇒ đóng nhanh, không hội thoại nối.
    scheduleClose(if (pending) VoiceSession.NETWORK_WAIT_MS else VoiceSession.LINGER_MS)   // nán LINGER; vế tra mạng chờ ~20s.
    // Mở hội thoại nếu được — nếu mở, nó dời hẹn đóng ra xa hơn (window + LINGER).
    followUp(my, pending)
}

// ── ĐƯỜNG NÓI: gom N dòng → đọc MỘT câu (tách khỏi VoiceSession theo VAI, trần 500 dòng) ─────────

/**
 * Đọc thành tiếng câu trả lời/xác nhận — R1 + R3 của spec `kachi-voice-feedback.html`.
 *
 * Ba cổng chặn (thứ tự là HỢP ĐỒNG, bài canh `VoiceCommandWiringContractTest`): mic đang mở ([micOpen] — nói
 * lúc mic mở là Kachi nghe chính mình) · hộp xác nhận đang mở · công tắc *"Đọc phản hồi"* phải chặn **TRƯỚC**
 * phép gộp câu (một lượt Piper tốn hàng trăm ms CPU cho câu không ai nghe). [onDone] chạy ở MỌI đường thoát —
 * kể cả khi không đọc gì (hội thoại/đóng-overlay treo trên chính mốc này; một cổng chờ mốc không bao giờ về là
 * cổng chết im). Không có giọng ⇒ degrade, không ném (tấm chữ + âm báo vẫn như 1.63).
 */
internal fun VoiceSession.speakLines(lines: List<String>, onDone: () -> Unit = {}) {
    val sentence = when {
        lines.isEmpty() || micOpen() || confirmOpen.get() -> null
        !speakReplies() -> null
        else -> VoiceFeedbackPhrase.merge(lines)
    }
    if (sentence == null) { onDone(); return }
    runCatching { speaker.speak(sentence) { onDone() } }
        .onFailure { Log.w(VoiceSession.TAG, "không đọc được câu trả lời", it); onDone() }
}
