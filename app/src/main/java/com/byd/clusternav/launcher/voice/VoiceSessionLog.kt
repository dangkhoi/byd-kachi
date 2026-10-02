package com.byd.clusternav.launcher.voice

import android.util.Log

/**
 * ═══ H2 · NHẬT KÝ LƯỢT NÓI — hai nửa của MỘT mục, ghi ở hai thì ══════════════════════════════════════════════
 *
 * Tách khỏi `VoiceSessionTurns.kt` ở **2.75** vì trần 500 dòng (CLAUDE.md §4.1 · bài canh
 * `VoiceListenWiringContractTest.pha NGHE khong day tep nao qua tran 500 dong`) — và tách **theo vai**: tệp kia
 * trả lời *"phiên còn nghe tiếp không, và nghe thế nào"*, hai hàm dưới đây chỉ trả lời *"lượt vừa rồi được ghi lại
 * ở đâu"*. Hai vai ấy không dùng chung dòng trạng thái nào ngoài [VoiceSession.utteranceStamp] /
 * [VoiceSession.utteranceMeta], nên đường cắt không sinh ra bản sao nào (đúng lẽ đã ghi ở KDoc `VoiceSessionTurns`).
 *
 * **Pure move** — thân hai hàm nguyên văn, không đổi một assert nào của các bài canh đang đọc chúng.
 */


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
internal fun VoiceSession.logDone(
    intents: List<VoiceIntent>,
    replies: List<String>,
    clarify: Boolean = clarifyRound > 0,
) {
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
                clarify = clarify,
                followUp = followUps > 0,
            ),
        )
    }.onFailure { Log.w(VoiceSession.TAG, "không cập nhật được nhật ký lượt nói", it) }
}

/**
 * ═══ Nửa SAU của lượt dừng ở **HỎI LẠI** hoặc **BỎ CUỘC** — hai lối thoát của `execute` không đi qua `settle()` ═════
 *
 * [ĐO máy ảo 02/10, mục `20261002-172417-715` · `20261002-172621-101`] logcat có *"hỏi lại (lượt 1)"* mà tệp JSON của
 * lượt ấy giữ `decision`/`replies` RỖNG: `execute` thoát ở nhánh hỏi lại TRƯỚC `logDone`, và lượt trả lời (nếu có chữ)
 * tự mở mốc MỚI ở [logHeard] ⇒ không ai còn ghi nửa sau cho mốc cũ. Đúng những lượt cần chẩn đoán nhất (*"vì sao Kachi
 * hỏi lại?"*) lại là những lượt nhật ký câm.
 *
 * Ghi câu Kachi THẬT SỰ nói ([line]: câu hỏi lại / câu bỏ cuộc), cờ `clarify` = true. Mốc vẫn bị xoá như [logDone]:
 * lượt trả lời là một mục riêng, có tiếng riêng.
 */
internal fun VoiceSession.logAsked(intents: List<VoiceIntent>, line: String) =
    logDone(intents, listOf(line), clarify = true)
