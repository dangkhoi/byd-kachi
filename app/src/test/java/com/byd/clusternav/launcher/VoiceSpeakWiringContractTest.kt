package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V1 · DÂY NỐI CỦA ĐƯỜNG LỆNH · vai TẦNG NÓI (spec `kachi-voice-feedback.html` R2 · R4 · OQ4 · T8) ═══════════════
 *
 * Tách từ [VoiceCommandWiringContractTest] (DEBT-500-TEST, 2026-09-26; nguyên văn, không nới assert). Bốn bài: cổng ra
 * TIẾNG chỉ mở ở đúng một tệp · đọc xong câu hỏi mới mở micro (và hết hạn vẫn mở) · hai công tắc đọc phản hồi gác thật ·
 * gói giọng đọc đi chung đường cài với gói nghe. `voiceSources()` dùng chung khai ở [VoiceListenWiringContractTest].
 */
class VoiceSpeakWiringContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    /**
     * **Cổng ra TIẾNG chỉ được mở ở ĐÚNG MỘT TỆP** — spec `docs/specs/kachi-voice-feedback.html` R2.
     *
     * Bài này thay chỗ cho lệnh cấm `TextToSpeech` cũ, và nó **chặt hơn** chứ không lỏng hơn. Lệnh cấm cũ trả
     * lời câu hỏi *"có đọc không"*; câu hỏi ấy owner đã trả lời rồi. Câu hỏi còn lại — và là câu dễ hỏng hơn —
     * là *"đọc bằng mấy đường"*: hai tệp cùng dựng một `TextToSpeech` nghĩa là hai kết nối dịch vụ, hai lần xin
     * tiêu điểm âm thanh, và hai câu phát chồng lên nhau mà **không tệp nào biết tệp kia tồn tại**. Đúng họ lỗi
     * *"đường thứ hai"* mà KDoc `VoiceDispatcher`/`VoiceWiring` dựng ra để chặn.
     *
     * ⇒ Mọi tệp `Voice*` phải đi qua giao diện `VoiceSpeaker`; chỉ `AndroidTtsSpeaker.kt` (không mang tiền tố
     * `Voice`, cố ý) được chạm thẳng API nền tảng. Tên lớp trong KDoc **không** bị chặn — chặn cả tên là chặn
     * nhầm đúng chỗ đang giải thích luật.
     */
    @Test
    fun `chi tiep tieng chi duoc mo o DUNG MOT TEP`() {
        val banned = listOf("TextToSpeech(", "import android.speech.tts")
        voiceSources().forEach { (name, src) ->
            banned.forEach { token ->
                assertFalse(
                    src.contains(token),
                    "$name chạm thẳng máy đọc của nền tảng (`$token`) — phải đi qua `VoiceSpeaker`; " +
                        "đường ra tiếng chỉ được dựng ở `AndroidTtsSpeaker.kt`",
                )
            }
        }
        // Chốt ngược: tệp được phép phải THẬT SỰ còn đó và còn gác ngưỡng ngôn ngữ. Thiếu vế này thì ngày ai đó
        // xoá `AndroidTtsSpeaker.kt`, bài trên vẫn xanh trong khi tính năng đã chết.
        val speaker = code("src/main/java/com/byd/clusternav/launcher/voice/AndroidTtsSpeaker.kt")
        assertTrue(speaker.contains("isLanguageAvailable"), "phải hỏi nền tảng có giọng vi-VN không, không đoán")
        assertTrue(
            speaker.contains("VoiceSpeakerSelector.LANG_AVAILABLE"),
            "ngưỡng phải đọc từ luật thuần (kiểm off-car), không viết một con số trần ở tầng Android",
        )
    }

    /**
     * **OQ4 (voice pha 2) — ĐỌC XONG câu hỏi rồi mới mở micro, và hạn 4 giây phải là một lưới an toàn THẬT.**
     *
     * Hai tính chất, mỗi cái chặn một cách hỏng ngược nhau:
     *  • mở micro **trong** `onDone` — không thì Kachi nghe chính mình đọc câu hỏi (hazard đã ghi ở KDoc
     *    `VoiceSession.speakLines`), và cổng *"đồng ý"* nhận nhầm một lần là một cánh cửa mở giữa bãi đỗ;
     *  • vẫn mở micro khi **hết hạn** — CLAUDE.md §3: *"không gate một đường phục hồi bằng dữ liệu mà chỉ chính
     *    đường đó mới làm mới được"*. Engine đọc chết giữa chừng không được phép khoá cổng an toàn vĩnh viễn.
     */
    @Test
    fun `cau hoi xac nhan doc xong moi mo micro, va het han thi van mo`() {
        // ⚠ 1.66 — các lượt nghe NỐI của một phiên (hỏi lại R8 · hội thoại R9 · cổng xác nhận) đã rời sang
        // `VoiceSessionTurns.kt` dưới dạng hàm mở rộng của chính [VoiceSession] (trần 500 dòng, CLAUDE.md §4.1).
        val session = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt")
        val turns = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt")
        val fn = SourceRoots.body(turns, "internal fun VoiceSession.askAloudThenListen(")
        assertTrue(fn.contains("speaker.speak(question) { openMic() }"),
            "micro phải mở TRONG mốc 'đọc xong' của chính câu hỏi, không phải ngay sau khi xếp câu")
        assertTrue(fn.contains("ASK_ALOUD_CAP_MS"),
            "phải có hạn cứng — engine đọc treo không được khoá cổng xác nhận vĩnh viễn")
        assertTrue(fn.contains("!speakReplies() || !speaker.available()"),
            "tắt công tắc R4 / máy không có giọng ⇒ mở micro NGAY như 1.65, không chờ gì")
        assertTrue(fn.contains("listening.compareAndSet(false, true)") || fn.contains("!listening.compareAndSet"),
            "hai đường (đọc xong · hết hạn) có thể cùng về ⇒ phải chốt để chỉ mở ĐÚNG MỘT lượt nghe")
        // 1.70 — `confirm` tách sang `VoiceSessionTurns.kt` (extension VoiceSession) theo VAI, trần 500 dòng.
        val turnsSrc = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt")
        assertTrue(SourceRoots.body(turnsSrc, "internal fun VoiceSession.confirm(").contains("askAloudThenListen("),
            "cổng xác nhận phải đi qua đường đọc-rồi-nghe; gọi thẳng listenForConfirm là bỏ qua OQ4")
    }

    /**
     * **R4 (voice pha 2) — hai công tắc của đường ra tiếng phải THẬT SỰ gác, và gác đúng chỗ.**
     *
     * *"Đọc phản hồi"* tắt mà vẫn tổng hợp giọng là tốn hàng trăm ms CPU trên đầu xe cho một câu không ai nghe;
     * *"Ưu tiên giọng offline"* phải là **lambda** (đọc lại mỗi câu) chứ không phải một giá trị chụp lúc dựng
     * phiên — chụp một lần thì bật công tắc xong phải khởi động lại launcher mới thấy tác dụng.
     */
    @Test
    fun `hai cong tac doc phan hoi gac that`() {
        val session = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt")
        assertTrue(session.contains("preferOffline = { Prefs.voicePreferOffline(ctx) }"),
            "công tắc 'ưu tiên giọng offline' phải truyền dạng lambda để đọc lại ở MỖI câu")
        // ⚠ 1.66: [speakLines] nay phải gọi `onDone` ở MỌI đường thoát (hội thoại R9 treo trên mốc đó), nên cổng
        // không còn là một `return` trần. Thứ phải canh vẫn y nguyên — **THỨ TỰ**: công tắc chặn TRƯỚC phép gộp.
        // 1.70: speakLines tách sang VoiceSessionTurns (extension) theo VAI, trần 500 dòng.
        val turns = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt")
        val body = SourceRoots.body(turns, "internal fun VoiceSession.speakLines(")
        val gate = body.indexOf("!speakReplies()")
        val merge = body.indexOf("VoiceFeedbackPhrase.merge(")
        assertTrue(gate in 0 until merge, "tắt 'Đọc phản hồi' phải chặn TRƯỚC khi gộp/tổng hợp câu, không phải sau")
        assertTrue(body.contains("onDone()"), "mọi đường thoát của speakLines phải gọi onDone — hội thoại chờ mốc đó")
        val settings = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelSettings.kt")
        assertTrue(settings.contains("deps.bridge.setVoiceSpeakReplies(") &&
            settings.contains("deps.bridge.setVoicePreferOffline("),
            "hai công tắc phải có một hàng THẬT trong Cài đặt — khoá không ai chạm tới được là khoá chết")
    }

    /**
     * **T8 (voice pha 2) — gói giọng ĐỌC đi qua CHÍNH đường cài của gói NGHE, không phải một bản sao.**
     *
     * Chép `VoiceModelStore` thành `VoiceTtsStore` là nhân đôi bốn tính chất tinh tế (staging · băm trong lúc
     * tải · hỏng thì xoá · từ chối gói chưa ghim), và bản sao sẽ lệch ở đúng lần ai đó vá một tính chất.
     */
    @Test
    fun `goi giong doc dung chung duong cai voi goi nghe`() {
        val settings = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelSettings.kt")
        assertTrue(settings.contains("VoiceModelStore.install(context, ttsPack)"),
            "gói ĐỌC phải gọi đúng `VoiceModelStore.install`, không dựng đường cài thứ hai")
        assertTrue(settings.contains("VoiceModelStore.remove(context, ttsPack)"), "phải có đường GỠ cho gói đọc")
        assertTrue(settings.contains("SherpaTtsCatalog.PIPER_VI_VAIS1000"),
            "gói đọc phải lấy từ danh mục `:core`, không viết cứng đường dẫn/URL ở tầng vẽ")
        val store = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelStore.kt")
        assertTrue(store.contains("pack: VoicePack"), "đường cài phải nhận hợp đồng chung VoicePack")
        assertTrue(store.contains("target.parentFile?.mkdirs()"),
            "gói đọc mang cây thư mục nhiều tầng ⇒ phải tạo thư mục cha, không thì FileNotFoundException")
    }
}
