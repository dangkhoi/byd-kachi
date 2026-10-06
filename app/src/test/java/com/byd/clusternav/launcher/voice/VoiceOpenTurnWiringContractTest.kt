package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voiceSources
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VOICE-OPEN-TURN · DÂY NỐI — bật ở đúng một chỗ, và chỗ ấy phục vụ **cả hai** lối vào ════════════════════
 *
 * Backlog `OQ9`. Quyết định + phép ghép + hai con số thời gian đã có bài kiểm riêng ở `:core`
 * (`VoiceOpenTurnTest` · `VoiceOpenTurnCasesTest`, dựng từ chuỗi thật của xe 26/09). Bài này canh đúng ba thứ mà
 * một bài `:core` **không** thấy được, và cả ba đều là bẫy đã xảy ra thật trong dự án:
 *
 *  1. **Hàm mới không có chỗ gọi** (CLAUDE.md §8 — `CastShell.evictVd` compile sạch mà chưa chạy lần nào). Ở đây
 *     `VoiceOpenTurnArm` có bốn lời gọi phải tồn tại trong vòng đọc micro, không phải một.
 *  2. **Bật cho một lối vào, quên lối kia.** Nút mic và *"Hey Kachi"* (`:wake`) là hai tiến trình khác nhau; nhiều
 *     bản vá trước đã chỉ chạy ở một bên (2.68 nút mic không đi `:wake`, 2.69 relay ô). Bài này chứng minh bằng
 *     **cấu trúc**: cả hai dựng cùng một `VoiceSession`, và `openTurn = true` nằm ở đúng một chỗ mà cả hai chạy qua.
 *  3. **Số trôi sang `:app`.** Hai con số (cửa sổ ghép · trần cứng) phải chỉ tồn tại ở `:core` để bài kiểm và tài
 *     liệu nói về cùng một giá trị.
 */
class VoiceOpenTurnWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)

    private val capture by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceCapture.kt") }
    private val arm by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceOpenTurnArm.kt") }
    private val listen by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionListen.kt") }
    private val turns by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt") }
    private val endpoint by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceTurnEndpoint.kt") }
    private val probe by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWavProbe.kt") }

    // ══ (a) Bật ở ĐÚNG những lượt nhận một CÂU CỦA NGƯỜI LÁI, và chỉ ở đó ════════════════════════════════

    /**
     * ## ⚠ 2.75 — bài canh này ĐỔI kỳ vọng, và đây là phép đo bác bỏ kỳ vọng cũ
     * Bản 2.74 chốt *"chỉ lượt nghe CHÍNH bật open-turn"* với lý do *"ba lượt nối chỉ nhận một vế ngắn đã biết
     * hình dạng"*. [ĐO xe 2026-09-27 10:30–10:37] bác đúng vế ấy: trong 22 lượt owner nói *"mở &lt;app&gt; vào ô
     * số N"*, **15 lượt đi qua `listenOnce`** — vì R9 giữ micro 5 giây sau mỗi câu trả lời, nên câu thứ hai trở đi
     * của một phiên **luôn** là một lượt nối, và ở đó người lái nói một câu lệnh đầy đủ. Hậu quả đo được:
     * 10:43:11 nghe ra *"mở vietmap vào ô số"* — một vế DỞ mà `VoiceOpenTurn.isOpen` nhận ra ngay ở tầng chữ — mà
     * lượt ấy **không có** dòng `noi-tiep:` nào, tức không ai chờ vế sau.
     *
     * Kỳ vọng mới: bật ở **hai** thân lượt nhận một câu của người lái (chính + nối), **không** bật ở cổng XÁC NHẬN
     * (`listenForConfirm` chỉ bắt *"đồng ý"/"huỷ"* — ở đó một vế dở là vô nghĩa, và im lặng đã có sẵn nghĩa KHÔNG).
     */
    @Test
    fun `chi cac luot nhan MOT CAU cua nguoi lai bat open-turn`() {
        val on = voiceSources().filter { (_, src) -> src.contains("openTurn = true") }.map { it.first }.sorted()
        // 2.91 VOICE-APP-NAMES (spec §4.4) — + `VoiceTeachSession.kt` CÓ CHỦ Ý: lượt DẠY phải nghe bằng ĐÚNG tham số của
        // lượt chính (người dùng nói cả câu "mở <tên>" như lúc lái; mẫu thu bằng cấu hình khác sẽ in ra chữ khác lúc dùng).
        assertEquals(
            listOf("VoiceSessionListen.kt", "VoiceSessionTurns.kt", "VoiceTeachSession.kt"), on,
            "`openTurn = true` chỉ được ở lượt nghe CHÍNH và lượt NỐI; thấy: $on",
        )
        assertTrue(
            capture.contains("openTurn: Boolean = false"),
            "mặc định phải là TẮT — mọi chỗ gọi không tự tuyên bố thì chạy y hệt bản trước",
        )
        // ⚠ Mốc là thân biểu thức của `listenOnce` (`): String = runCatching {`, duy nhất trong tệp): đưa chữ ký
        // `private fun VoiceSession.listenOnce(` vào [SourceRoots.body] thì nó dừng ngay ở dòng `val` đầu tiên
        // (luật "thân biểu thức tới khai báo kế tiếp") ⇒ vùng quét rỗng, tức một bài canh GIẢ.
        assertTrue(
            SourceRoots.body(turns, "): String = runCatching {").contains("openTurn = true"),
            "lượt NỐI (hỏi lại + hội thoại) phải bật — 15/22 lượt câu-có-ô của buổi xe 27/09 đi qua đây",
        )
        assertFalse(
            SourceRoots.body(turns, "private fun VoiceSession.listenForConfirm(").contains("openTurn"),
            "cổng XÁC NHẬN không bật: nó chỉ bắt *đồng ý/huỷ*, một vế dở ở đó không có nghĩa gì",
        )
    }

    /**
     * Cả **nút mic** và **"Hey Kachi"** đi qua đúng lượt nghe vừa bật — chứng minh bằng cấu trúc, không bằng một
     * câu trong tài liệu: hai lối vào dựng cùng một `VoiceSession`, và chỉ có MỘT thân lượt nghe chính.
     */
    @Test
    fun `ca nut mic va Hey Kachi deu di qua luot nghe do`() {
        val home = code("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt")
        val wake = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeSessionFactory.kt")
        assertTrue(home.contains("VoiceSession("), "nút mic của màn chính phải dựng một VoiceSession")
        assertTrue(wake.contains("VoiceSession("), "đường `:wake` phải dựng CÙNG lớp phiên, không một đường nghe thứ hai")
        assertTrue(listen.contains("internal fun VoiceSession.runListen("), "thân lượt nghe chính phải là của VoiceSession")
        assertEquals(
            1, Regex("""runListen\(""").findAll(code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt")).count(),
            "chỉ MỘT chỗ khởi động lượt nghe chính — hai chỗ là hai đường có thể lệch cấu hình",
        )
    }

    // ══ (b) Hàm mới CÓ chỗ gọi thật — bốn lời, trong đúng vòng đọc micro ═════════════════════════════════

    @Test
    fun `bo giu luot duoc noi that vao vong doc micro`() {
        val body = SourceRoots.body(capture, "private fun listenGranted(")
        listOf("VoiceOpenTurnArm(rec, ep)", "arm.arm(fed)", "arm.stopReading(stop)", "arm?.result(fed)")
            .forEach { assertTrue(body.contains(it), "vòng đọc micro thiếu lời gọi `$it` — hàm mới không có chỗ gọi") }
        // Giữ lượt phải xảy ra ĐÚNG tại điểm ngắt câu, không ở một nhánh nào khác.
        val stop = body.indexOf("if (stop) {")
        assertTrue(stop in 0 until body.indexOf("if (arm != null && arm.arm(fed)) continue"), "phải giữ lượt ở nhánh ngắt câu")
        // Và phải giữ SAU khi `ended` đã đặt: một lượt có giữ vẫn là một lượt "điểm ngắt đã nổ".
        assertTrue(
            body.indexOf("ended = true") in 0 until body.indexOf("if (arm != null && arm.arm(fed)) continue"),
            "`ended` phải đặt trước khi giữ lượt — nếu không lượt có giữ bị coi là lượt chạm trần",
        )
        // Đoạn vế sau còn mở lúc thoát vòng ⇒ phải `flush`, nếu không vế sau mất trắng (và `trim` cũng không thấy nó).
        assertTrue(body.contains("if (!ended || arm?.armed == true) ep.flush()"), "phải chốt nốt đoạn của vế sau")
    }

    @Test
    fun `quyet dinh va phep ghep o core, khong lam lai o app`() {
        assertTrue(arm.contains("VoiceOpenTurn.isOpen("), "câu hỏi \"còn dở không\" phải hỏi bề mặt thuần `:core`")
        // R6 (2.76): phép ghép + luật *"vế đủ mà có vế sau thì có nhận không"* là MỘT hàm `:core` (`attach`), không
        // phải `join` gọi thẳng — nếu không thì nhánh vế-đủ có thể mọc lại một lần `return` sớm trước `tailRange`.
        assertTrue(arm.contains("VoiceOpenTurn.attach("), "ghép/giữ phải hỏi `VoiceOpenTurn.attach` ở `:core`")
        assertFalse(arm.contains("VoiceOpenTurn.join("), "`:app` không được gọi `join` thẳng — bỏ qua cổng `refine` của vế đủ")
        val result = SourceRoots.body(arm, "fun result(fedSamples: Int): Outcome?")
        assertTrue(
            result.indexOf("ep.tailRange(") in 0 until result.indexOf("VoiceOpenTurn.attach("),
            "[ĐO xe 27/09 10:42:46] vế sau đã thu phải được hỏi TRƯỚC khi quyết định giữ/ghép — không `return` sớm vì vế trước đủ",
        )
        assertFalse(
            result.contains("if (!VoiceOpenTurn.isOpen(head)) return"),
            "2.75 vứt vế sau đã thu bằng đúng dòng này; R6 cấm nó quay lại",
        )
        // [P2 · SOÁT Opus 2026-09-27] Lượt giải mã vế sau nằm trên ĐƯỜNG TỚI HÀNH ĐỘNG (1,3–2 s [ĐO xe] cho một
        // lượt) ⇒ chỉ chạy khi `attach` còn có thể nhận vế trước. Phép hỏi ở `:core` (`mayAttach`), đặt SAU `tailRange`
        // và TRƯỚC `rangeResult`: đặt trước `tailRange` là mọc lại đúng cái `return` sớm mà R6 cấm.
        assertTrue(
            result.indexOf("ep.tailRange(") in 0 until result.indexOf("VoiceOpenTurn.mayAttach("),
            "cổng rẻ phải đứng SAU khi đã hỏi vế sau có thật hay không",
        )
        assertTrue(
            result.indexOf("VoiceOpenTurn.mayAttach(") in 0 until result.indexOf("rec.rangeResult("),
            "và TRƯỚC lượt giải mã — nếu không thì cổng chẳng tiết kiệm được gì",
        )
        // Không có bảng vế dở thứ hai ở `:app` — đó là cách một bản vá tự tách khỏi ngữ pháp (CLAUDE.md §7).
        listOf("vao o", "vào ô", "ho so", "hồ sơ").forEach {
            assertFalse(arm.contains("\"$it"), "`$it` viết cứng ở `:app` ⇒ bảng vế dở đã có hai bản")
        }
        // Hai con số chỉ sống ở `:core`.
        assertTrue(arm.contains("VoiceOpenTurn.OPEN_JOIN_WINDOW_MS"), "cửa sổ ghép phải đọc từ `:core`")
        assertTrue(arm.contains("VoiceOpenTurn.OPEN_MAX_EXTRA_MS"), "trần cứng phải đọc từ `:core`")
        listOf("1_200", "1200", "1_500", "1500").forEach {
            assertFalse(arm.contains(it), "số $it viết cứng ở `:app` ⇒ tài liệu và mã có thể nói hai giá trị")
        }
    }

    // ══ (c) Những thứ KHÔNG được đổi ════════════════════════════════════════════════════════════════════

    @Test
    fun `khong doi mac dinh VAD, khong doi tran 8 giay, khong them tieng noi`() {
        assertEquals(600, VoiceVadTrim.MIN_SILENCE_MS, "mặc định VAD 600 ms KHÔNG được đổi (OQ9 rẻ hơn chính vì thế)")
        assertTrue(
            code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt").contains("MAX_LISTEN_MS = 8_000L"),
            "trần cứng 8 s của một phiên phải nguyên",
        )
        // Phản hồi lúc chờ = chữ đã có trên tấm chữ (đường `onPartial`), KHÔNG một câu đọc mới nào.
        assertTrue(arm.contains("onPartial(head)"), "chữ vế trước phải hiện ra trong lúc chờ")
        // ⚠ Không cấm chữ `speak` trần: `ep.speaking()` (Silero *"đang có tiếng không"*) là cơ chế của pha chờ.
        listOf("speaker.", "VoiceSpeaker", "TextToSpeech", "VoiceChime", "tone(").forEach {
            assertFalse(arm.contains(it), "pha chờ không được phát thêm tiếng gì (`$it`)")
        }
    }

    /** Đường LÙI (RMS) **không** giữ lượt: nó gần như không bao giờ chốt câu nên ở đó không có gì để nối thêm. */
    @Test
    fun `duong lui RMS giu dung hanh vi cu`() {
        assertTrue(arm.contains("!ep.openTurnReady()"), "phải từ chối giữ lượt khi không phải đường VAD")
        assertTrue(endpoint.contains("fun openTurnReady(): Boolean = vad != null"), "chỉ đường VAD mới giữ được lượt")
        assertTrue(endpoint.contains("fun segmentCount(): Int = vad?.segmentCount() ?: 0"), "đường lùi không có đoạn nào")
        assertTrue(endpoint.contains("fun speaking(): Boolean = vad?.speaking() ?: false"), "đường lùi không trả lời được")
    }

    /** Đường đo WAV phải đi **cùng hai pha** với phiên thật, nếu không nó thôi nói về phiên thật. */
    @Test
    fun `duong do WAV di cung hai pha`() {
        assertTrue(probe.contains("VoiceOpenTurn.isOpen("), "đường đo phải hỏi đúng câu hỏi mà phiên thật hỏi")
        assertTrue(probe.contains("VoiceOpenTurn.attach("), "và ghép/giữ bằng đúng luật ấy (R6: kể cả vế đủ)")
        assertFalse(probe.contains("VoiceOpenTurn.join("), "đường đo không được gọi `join` thẳng — nó sẽ lệch phiên thật")
        assertTrue(probe.contains("headText ="), "kết quả đo phải phơi vế TRƯỚC")
        assertTrue(probe.contains("tailText ="), "và vế SAU — một phép đo trộn hai vế vào một dòng là mất thứ cần đo")
        val bridge = code("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeWav.kt")
        listOf("\"head\" to probe.headText", "\"tail\" to probe.tailText", "\"open_head\" to probe.openHead")
            .forEach { assertTrue(bridge.contains(it), "cầu kiểm thử `wav` thiếu cột `$it`") }
    }
}
