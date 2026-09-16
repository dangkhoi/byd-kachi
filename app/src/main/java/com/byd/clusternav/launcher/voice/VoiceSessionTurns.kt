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
 */
internal fun VoiceSession.clarifyGaveUp(intents: List<VoiceIntent>): Boolean {
    if (clarifyRound < VoiceClarify.MAX_ROUNDS) return false
    val only = intents.singleOrNull() as? VoiceIntent.Unknown ?: return false
    if (VoiceClarify.ask(only, 0) == null) return false
    clarifyRound = 0
    val line = VoiceClarify.giveUp()
    overlay?.render(R.string.kachi_voice_heard, line)
    scheduleClose(VoiceSession.LINGER_MS)
    speakLines(listOf(line))
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
    overlay?.render(R.string.kachi_voice_confirm_title, ask.question)
    scheduleClose(VoiceSession.CLARIFY_LISTEN_MS + VoiceSession.LINGER_MS)
    // Đọc câu hỏi XONG rồi mới mở micro — cùng hazard với [askAloudThenListen]: micro mở trong lúc loa nói
    // thì Kachi nghe chính mình. Khác ở chỗ đây **luôn** đọc (nếu có giọng): owner chốt không đọc *câu hỏi
    // XÁC NHẬN* (một cổng an toàn), còn đây là một câu hội thoại — chính thứ **D2** xin.
    val spoke = if (speakReplies()) {
        runCatching { speaker.speak(ask.question) { post { listenAgain(ask, my) } } }
            .onFailure { Log.w(VoiceSession.TAG, "không đọc được câu hỏi lại", it) }
            .getOrDefault(false)
    } else {
        false
    }
    if (!spoke) listenAgain(ask, my)
}

private fun VoiceSession.listenAgain(ask: VoiceClarify.Ask, my: Int) {
    if (cancelled.get() || stale(my)) return
    background {
        val heard = listenOnce(my, VoiceSession.CLARIFY_LISTEN_MS, beep = false, hint = R.string.kachi_voice_listening)
        post {
            if (cancelled.get() || stale(my)) { closeIfMine(my); return@post }
            val joined = VoiceClarify.combine(ask.carry, heard)
            if (heard.isBlank()) {
                overlay?.render(R.string.kachi_voice_heard, VoiceClarify.giveUp())
                scheduleClose(VoiceSession.LINGER_MS)
            } else {
                execute(joined)
            }
        }
    }
}

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
    overlay?.render(R.string.kachi_voice_follow_up, "")
    scheduleClose(window.toLong() + VoiceSession.LINGER_MS)
    background {
        val heard = listenOnce(my, window.toLong(), beep = false, hint = R.string.kachi_voice_follow_up)
        post {
            if (cancelled.get() || stale(my)) { closeIfMine(my); return@post }
            // Hết giờ mà không ai nói ⇒ **đóng êm**: không câu báo lỗi, không tiếng gì. Đây là trạng thái
            // THƯỜNG của hội thoại (người ta nói xong một việc rồi thôi), không phải một lỗi để kể.
            if (heard.isBlank()) close() else execute(heard)
        }
    }
}

/**
 * Một lượt nghe ngắn dùng chung cho hỏi-lại và hội thoại. **CHẶN** ⇒ chỗ gọi đưa vào [background].
 *
 * Không giữ PCM: cả hai đường đều nhận câu **ngắn** trong tập đóng; lượt giải mã tự do (`VoiceFreeTail`) chỉ
 * cần cho tên bài/điểm đến, và một câu như vậy sẽ được nói ở một phiên đầy đủ chứ không phải trong 5 giây nối.
 */
private fun VoiceSession.listenOnce(my: Int, maxMs: Long, beep: Boolean, hint: Int): String = runCatching {
    val labels = appsByLabel()
    VoiceRecognizer.open(ctx, profiles(), labels.keys.toList(), labels.values.toSet(), places())?.use {
        whileCapturing {
            capture.listen(it, maxMs, { cancelled.get() || stale(my) }, keepPcm = false, beep = beep) { partial ->
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
                capture.listen(it, VoiceSession.CONFIRM_LISTEN_MS, { cancelled.get() || answered.get() || stale(my) }) { partial ->
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
