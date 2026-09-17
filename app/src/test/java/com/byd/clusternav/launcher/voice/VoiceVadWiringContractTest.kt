package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.testbridge.TestBridgeCommands
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ Silero VAD — BÀI CANH DÂY NỐI (CLAUDE.md §8: compile xanh ≠ code chạy) ══════════════════════════════════
 *
 * Bằng chứng: `docs/diagnostics/voice-stream-eval-2026-09-16.md` §5 · §6 · §8.
 *
 * Cái dễ hỏng **im lặng** ở bản này không phải VAD — mà là **phép cắt**. VAD có thể chạy, chốt đúng đoạn, in
 * đúng nhật ký, và cửa sổ vẫn được nạp **nguyên** vào bộ giải mã vì một lời gọi `finalResult()` không tham số
 * còn sót lại. Lúc ấy độ trễ vẫn giảm (nên trông như đã xong) mà **độ chính xác thì không** — đúng thứ §6 nói là
 * phần đáng giá nhất. Nên bài này canh chặt nhất ở đúng chỗ đó.
 */
class VoiceVadWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)

    private val capture by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceCapture.kt") }
    private val vad by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceVad.kt") }
    private val turn by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceTurnEndpoint.kt") }
    private val probe by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWavProbe.kt") }
    private val rec by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceRecognizer.kt") }

    // ══ (a) VAD là đường CHÍNH, RMS là đường LÙI ═════════════════════════════════════════════════════════

    @Test
    fun `VAD la duong chinh, RMS o lai lam duong lui`() {
        assertTrue(turn.contains("VoiceVad.open(ctx)"), "phải thử VAD trước")
        assertTrue(
            turn.contains("vad?.let { return it.accept(pcm, n) }"),
            "có VAD thì bộ RMS KHÔNG được chạy — hai bộ ngắt câu cùng lúc là hai câu trả lời cho một câu hỏi",
        )
        assertTrue(turn.contains("rms?.accept("), "không có VAD thì bộ RMS phải thật sự được gọi")
        // Đường lùi phải còn sống: gỡ nó đi là bỏ mất thứ duy nhất chạy khi ONNX từ chối.
        assertTrue(
            SourceRoots.exists("src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceEndpointer.kt"),
            "bộ RMS phải còn — một đường phục hồi phải ít phụ thuộc hơn đường nó phục hồi cho",
        )
        // Chỗ gọi cố ý tắt ngắt câu (`endpointer = null`) thì KHÔNG được dựng VAD sau lưng họ.
        assertTrue(
            turn.contains("if (rmsFallback == null) return VoiceTurnEndpoint(null, null)"),
            "`endpointer = null` nghĩa là tắt hẳn ngắt câu — không được lén bật VAD",
        )
    }

    /** Vòng đọc micro **không được biết** đang chạy đường nào — nếu không, đường lùi sẽ rữa vì ít được chạy. */
    @Test
    fun `vong doc micro khong tu re nhanh VAD hay RMS`() {
        assertFalse(capture.contains("VoiceVad."), "VoiceCapture không được gọi thẳng VAD")
        assertFalse(
            capture.contains("VoiceEndpointer.Phase"),
            "VoiceCapture không được đọc pha của bộ RMS — nó hỏi [VoiceTurnEndpoint], không rẽ nhánh",
        )
        assertTrue(capture.contains("VoiceTurnEndpoint.open(ctx, endpointer)"), "phải đi qua bề mặt chung")
    }

    // ══ (b) PHÉP CẮT — phần đáng giá nhất, canh chặt nhất ════════════════════════════════════════════════

    @Test
    fun `cua so bi CAT truoc khi giai ma, khong con duong nap nguyen cua so`() {
        val body = SourceRoots.body(capture, "private fun listenGranted(")
        assertTrue(body.contains("val trim = ep.trimSamples(fed)"), "phải tính điểm cắt")
        assertTrue(body.contains("rec.finalResult(trim)"), "và phải giải mã ĐÚNG phần đã cắt")
        assertFalse(
            Regex("""rec\.finalResult\(\s*\)""").containsMatchIn(body),
            "còn một lời gọi `finalResult()` không tham số ⇒ cửa sổ vẫn nạp NGUYÊN vào mô hình; độ trễ giảm " +
                "nhưng độ chính xác thì không, và đó là phần đáng giá nhất của §6",
        )
        // Điểm cắt phải được tính TRƯỚC khi giải mã, không phải sau (một thứ tự sai ở đây là no-op im lặng).
        assertTrue(
            body.indexOf("val trim = ep.trimSamples(fed)") < body.indexOf("rec.finalResult(trim)"),
            "phải cắt rồi mới giải mã",
        )
        // ⚠ [SOÁT 1.69 · P2] Cánh cửa THỨ HAI, thứ bài cũ không canh: nhánh `if (rec.accept(...))` trong vòng
        // đọc micro. Hôm nay nó không chạy (bộ gom offline luôn trả `false`), nhưng nó từng gọi `rec.result()`
        // — đúng đường nạp NGUYÊN cửa sổ. Ngày ai đó đổi sang một bộ nhận dạng streaming thì cửa ấy mở lại
        // im lặng: độ trễ vẫn tốt, chỉ độ chính xác tụt. Khoá bằng cách xoá hẳn `result()` khỏi bề mặt.
        assertFalse(body.contains("rec.result()"), "nhánh `accept()` cũng phải giải mã khúc ĐÃ cắt")
        assertFalse(
            Regex("""fun result\(\)""").containsMatchIn(rec),
            "`VoiceRecognizer.result()` giải mã nguyên phần đã gom ⇒ gỡ hẳn, không để ai gọi nhầm",
        )
    }

    @Test
    fun `bo giai ma nhan gioi han va khong doc ra ngoai vung da gom`() {
        assertTrue(rec.contains("fun finalResult(limitSamples: Int)"), "phải có đường giải mã có giới hạn")
        val fn = SourceRoots.body(rec, "fun finalResult(limitSamples: Int)")
        assertTrue(fn.contains("minOf(filled"), "phải kẹp theo phần ĐÃ gom — không đọc rác ngoài vùng")
        assertTrue(fn.contains("maxOf(0"), "giới hạn âm phải thành 0, không thành một chỉ số âm")
    }

    /** Đường LÙI cố ý **không cắt**: cắt theo một con số [ĐO] là sai hệ thống trên xe là tự cắt mất câu nói. */
    @Test
    fun `duong lui nap nguyen cua so, khong mang theo phep cat chua do`() {
        val fn = SourceRoots.body(turn, "fun trimSamples(")
        assertTrue(fn.contains("?: windowSamples"), "không có VAD ⇒ nguyên cửa sổ, giữ đúng hành vi 1.68")
    }

    // ══ (c) Lượt không có tiếng ⇒ không giải mã (nối lại vào phán quyết của VAD) ═════════════════════════

    @Test
    fun `luot khong co tieng theo VAD thi khong giai ma`() {
        val body = SourceRoots.body(capture, "private fun listenGranted(")
        // 1.70 — cổng nay hỏi [VoiceSilenceGate.skipDecode] (`:core` thuần): nó bỏ giải mã khi `!sawSpeech` VÀ
        // (lượt nối tự tuyên bố `decodeOnlyIfSpeech` HOẶC đường VAD ở lượt chính). Đường VAD đủ tin để kết luận
        // "không có tiếng"; đường RMS thì không (giữ giải mã rồi nói *"Không nghe rõ"*).
        assertTrue(body.contains("VoiceSilenceGate.skipDecode("), "cổng phải hỏi bề mặt chung `:core`")
        assertTrue(body.contains("ep.route"), "cổng phải xét đường ngắt câu (VAD vs RMS)")
        assertTrue(body.contains("ep.sawSpeech()"), "cổng phải hỏi \"đã nghe thấy tiếng chưa\"")
        assertTrue(turn.contains("vad?.sawSpeech() ?: rms?.sawSpeech()"), "VAD trả lời trước, RMS lùi sau")
        // Chạm trần mà đoạn còn mở ⇒ phải `flush`, nếu không "nói dài" bị coi là "không ai nói".
        assertTrue(body.contains("ep.flush()"), "phải chốt nốt đoạn đang mở khi chạm trần")
        assertTrue(vad.contains("fun flush()"), "VAD phải phơi ra `flush`")
        assertTrue(
            body.indexOf("ep.flush()") < body.indexOf("VoiceSilenceGate.skipDecode("),
            "`flush` phải chạy TRƯỚC cổng bỏ-giải-mã — không thì câu dài bị bỏ như câu im",
        )
    }

    // ══ (d) Nhật ký: ba con số, vài dòng một lượt ═══════════════════════════════════════════════════════

    @Test
    fun `nhat ky in ba con so cua phep cat`() {
        listOf("tieng_bat_dau=", "tieng_dut=", "con_lai=").forEach {
            assertTrue(capture.contains(it), "dòng `cắt:` thiếu nhãn `$it`")
        }
        assertTrue(capture.contains("duong=\${ep.route}"), "phải nói lượt này chạy bằng VAD hay RMS")
        // Không có dòng nào theo KHỐI (5 khối/giây) — ngân sách log [ĐO xe 1.68] 9,8 KB/phút.
        assertFalse(vad.contains("Log.i("), "lớp VAD không được ghi nhật ký theo khối; tóm tắt do chỗ gọi in")
    }

    // ══ (e) Gói mô hình: asset trong APK, không qua đường tải ═══════════════════════════════════════════

    /**
     * Tệp phải **thật sự có mặt**, đúng cỡ và đúng sha256 — bài này là thứ duy nhất phát hiện được một lượt
     * `git add` thiếu tệp nhị phân (nó không hiện trong diff mà người ta đọc).
     */
    @Test
    fun `mo hinh VAD la asset trong APK, dung co va dung sha256`() {
        val f = SourceRoots.path("src/main/assets/voice/silero_vad.onnx")
        assertTrue(Files.isRegularFile(f), "thiếu asset silero_vad.onnx")
        val bytes = Files.readAllBytes(f)
        assertEquals(643_854, bytes.size, "cỡ tệp lệch — nghi tệp bị thay")
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(
            "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6", sha,
            "sha256 lệch — nguồn: github.com/k2-fsa/sherpa-onnx releases/asr-models/silero_vad.onnx (MIT)",
        )
        // Nạp THẲNG từ asset (không chép ra filesDir, không tải): đó là thứ làm nó có mặt trên xe không internet.
        assertTrue(vad.contains("""model = ASSET_NAME"""), "phải trỏ asset")
        assertTrue(vad.contains("""Vad(app.assets, config)"""), "phải truyền AssetManager thật")
        assertEquals("voice/silero_vad.onnx", VoiceVad.ASSET_NAME, "tên asset phải khớp chỗ tệp thật nằm")
        // Và KHÔNG được mọc một đường tải thứ hai cho nó.
        assertFalse(vad.contains("VoiceModelStore"), "gói 0,64 MB đi theo APK, không qua đường tải")
        assertFalse(vad.contains("HttpConn"), "tệp VAD không được chạm mạng")
    }

    // ══ (f) Ba núm ẩn ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `ba num VAD co mac dinh da do va di qua cong prefs_set`() {
        listOf("voice_vad_threshold", "voice_vad_min_speech_ms", "voice_vad_min_silence_ms").forEach {
            assertTrue(it in TestBridgeCommands.WRITABLE_PREFS_KEYS, "khoá $it chưa vào danh sách trắng")
        }
        val prefsSet = code("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgePrefsSet.kt")
        listOf("voice_vad_threshold", "voice_vad_min_speech_ms", "voice_vad_min_silence_ms").forEach {
            assertTrue(prefsSet.contains("\"$it\" ->"), "khoá $it có trong danh sách trắng mà KHÔNG ai thi hành")
        }
        // Mặc định phải LÀ hằng của `:core`, không phải số chép tay (cùng luật với bốn núm H5).
        val prefs = code("src/main/java/com/byd/clusternav/PrefsVoiceV3.kt")
        assertTrue(prefs.contains("K_VOICE_VAD_THRESHOLD, VoiceVadTrim.THRESHOLD"))
        assertTrue(prefs.contains("K_VOICE_VAD_MIN_SPEECH_MS, VoiceVadTrim.MIN_SPEECH_MS"))
        assertTrue(prefs.contains("K_VOICE_VAD_MIN_SILENCE_MS, VoiceVadTrim.MIN_SILENCE_MS"))
        // Đổi ms → giây đúng MỘT chỗ; rải rác là chỗ một bên nhân 1000 còn bên kia thì không.
        assertEquals(
            2, Regex("""VoiceVadTrim\.msToSeconds\(""").findAll(vad).count(),
            "đúng hai phép đổi (min_silence · min_speech), cả hai ở cùng một chỗ",
        )
    }

    // ══ (g) Đường đo WAV phải cắt CÙNG cách, nếu không nó thôi nói về phiên thật ════════════════════════

    @Test
    fun `duong do WAV cat cung mot phep cat voi phien nghe that`() {
        assertTrue(probe.contains("private fun trimSamples("), "đường đo phải có phép cắt")
        assertTrue(probe.contains("it.headTrimSamples(n)"), "và phải là CÙNG chế độ `head`")
        assertTrue(probe.contains("VoiceCapture.CHUNK_SAMPLES"), "nạp theo đúng nhịp khối của micro")
        assertTrue(probe.contains("it.flush()"), "tệp hết ⇒ phải chốt nốt đoạn cuối")
        // Cả HAI lượt giải mã (ngữ pháp + tự do) phải dùng cùng độ dài đã cắt.
        assertEquals(
            2, Regex("""decodeAll\(pcm\.first, trimmed\)""").findAll(probe).count(),
            "cả lượt 1 lẫn lượt 2 phải giải mã đúng khúc đã cắt — lệch nhau là hai phép đo nói về hai khúc khác nhau",
        )
        assertFalse(
            Regex("""decodeAll\(pcm\.first, pcm\.second\)""").containsMatchIn(probe),
            "còn một lời gọi nạp NGUYÊN tệp ⇒ ba ca đuôi im lặng (w26/w27/w28) không chứng minh được gì",
        )
        // Không dựng được VAD ⇒ trả nguyên độ dài, không im lặng đổi kết quả bằng một phép cắt không đo được.
        assertTrue(probe.contains("?: return n"), "thiếu VAD ⇒ nguyên tệp")
    }
}
