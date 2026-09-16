package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ [P0 xe 2026-09-16] BÀI CANH VÒNG LẶP HỘI THOẠI TỰ NUÔI ══════════════════════════════════════════════════
 *
 * ## Ca hỏng, bằng số đo ([ĐO] `voice-1.68-real.txt`, 12 phút trên xe owner)
 * **309** lượt mở micro từ **7** phiên thật, **không** dòng `"đã có một phiên nghe đang chạy"` nào (người lái
 * không bấm lại lần nào). **189/300** lượt báo `tieng_bat_dau=-1` — chưa từng nghe thấy tiếng — và **187** trong
 * số đó chạy hết trần 8,4 s. Mô hình trả `"ừ"` **102** lần và `"ừm"` **102** lần: đó là **ảo giác** khi đưa vào
 * gần như im lặng, và mỗi chuỗi như thế lại được coi là một lượt nói ⇒ mở lại micro. Hậu quả người dùng thấy:
 * ô YouTube không cuộn nổi.
 *
 * ## Năm chốt, và mỗi chốt chặn một khúc khác nhau của vòng
 * Không chốt nào một mình đủ, nên bài này canh **cả năm** — một chốt bị gỡ là vòng lặp có đường quay lại:
 *  (a) không giải mã lượt không có tiếng · (b) kết quả toàn từ đệm = hết chuyện ·
 *  (c) mọi lần mở lại micro đều tiêu một suất của trần phiên · (d) một micro tại một thời điểm ·
 *  (e) cầu chì theo phút — lớp cuối, không cần biết nguyên nhân.
 */
class VoiceLoopGuardWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)

    private val capture by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceCapture.kt") }
    private val turns by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt") }
    private val endpointer by lazy { code("src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceEndpointer.kt") }
    private val vad by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceVad.kt") }

    // ── (a) Không giải mã một lượt chưa bao giờ nghe thấy tiếng ──────────────────────────────────

    @Test
    fun `luot khong co tieng thi KHONG giai ma`() {
        val body = SourceRoots.body(capture, "private fun listenGranted(")
        val guard = body.indexOf("decodeOnlyIfSpeech && !ep.sawSpeech()")
        // ⚠ 1.69 vòng hai: `finalResult()` KHÔNG ĐỐI SỐ đã bị **xoá** — sau khi có phép cắt đuôi, nó không
        // còn chỗ gọi nào và để lại là mời người sau nạp cả cửa sổ vào mô hình lần nữa (đúng bẫy §6 của
        // `voice-stream-eval-2026-09-16.md`). Soi theo lời gọi CÓ đối số.
        //
        // ⚠ [SOÁT 1.69 · P2] Phải soi **đúng** lời gọi `(trim)`, không phải `rec.finalResult(` bất kỳ: từ lượt
        // soát này nhánh CHẾT `if (rec.accept(...))` cũng giải mã khúc đã cắt (trước đó nó gọi `rec.result()` —
        // đường nạp nguyên cửa sổ), và nhánh ấy nằm **trên** cổng bỏ-giải-mã trong thân hàm. Soi chuỗi chung
        // thì `indexOf` bắt được nhánh chết và bài này đỏ oan, trong khi cổng vẫn đứng đúng chỗ của nó.
        val decode = body.indexOf("rec.finalResult(trim)")
        assertTrue(guard >= 0, "thiếu cổng bỏ-giải-mã — đây là chỗ cắt ~200 lượt giải mã/12 phút")
        assertTrue(guard < decode, "cổng phải đứng TRƯỚC lượt giải mã, không phải sau")
        // Câu hỏi "đã nghe thấy tiếng chưa" do bộ ngắt câu trả lời (VAD hoặc RMS), không suy lại ở vòng đọc.
        assertTrue(endpointer.contains("fun sawSpeech()"), "`:core` (đường lùi RMS) phải phơi ra phép hỏi ấy")
        val turn = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceTurnEndpoint.kt")
        assertTrue(turn.contains("fun sawSpeech()"), "bề mặt chung phải phơi ra phép hỏi ấy")
        // ⚠ Siết lại ở 1.69 vòng hai: cấm **suy lại CỔNG** từ mốc giờ, chứ không cấm nhắc tới mốc giờ. Vòng
        // nghe vẫn phải đọc `speechStartMs()`/`speechEndMs()` để điền `Heard` và để ghi nhật ký — đó là **dữ
        // liệu**, không phải một luật thứ hai. Thứ phải cấm là viết lại chính phép quyết định (`… >= 0` /
        // `… < 0` / `… != -1`) ở đây, vì lúc ấy sẽ có hai định nghĩa của *"đã nghe thấy tiếng chưa"* và chúng
        // sẽ lệch. Bản cũ cấm cả chuỗi `speechStartMs`, nên nó bắt oan đúng cái cách dùng hợp lệ.
        listOf(">= 0", "> -1", "!= -1", "< 0").forEach { cmp ->
            assertFalse(
                capture.contains("speechStartMs()$cmp") || capture.contains("speechStartMs() $cmp"),
                "suy lại cổng từ `speechStartMs() $cmp` — hỏi `sawSpeech()` đi, đừng dựng luật thứ hai",
            )
        }
    }

    /** Mọi lượt NỐI phải bật cổng ấy; lượt CHÍNH thì không (người lái vừa bấm, im lặng ở đó cần được nói ra). */
    @Test
    fun `moi luot noi bat cong bo-giai-ma, luot chinh thi khong`() {
        assertEquals(
            2, Regex("""decodeOnlyIfSpeech = true""").findAll(turns).count(),
            "phải bật ở CẢ HAI chỗ mở micro nối: `listenOnce` (hội thoại + hỏi lại) và `listenForConfirm`",
        )
        val session = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt")
        assertFalse(
            session.contains("decodeOnlyIfSpeech"),
            "lượt CHÍNH phải giữ mặc định false — im lặng ở đó là câu trả lời cần nói ra, không phải lượt bỏ qua",
        )
    }

    // ── (b) Kết quả toàn từ đệm kết thúc hội thoại ───────────────────────────────────────────────

    @Test
    fun `chuoi toan tu dem ket thuc hoi thoai, va luat nam o core`() {
        assertTrue(turns.contains("fun endsConversation("), "phải có một phép hỏi DUY NHẤT cho cả hai đường nối")
        assertTrue(
            turns.contains("VoiceLexicon.isFillerOnly(heard)"),
            "phải dùng luật của `:core` (dựng trên tập FILLERS đã có), không chép bảng từ đệm thứ hai",
        )
        // Cả hai đường nối phải đi qua nó.
        assertEquals(
            2, Regex("""endsConversation\(heard\)""").findAll(turns).count(),
            "cả `followUp` (hội thoại) lẫn `listenAgain` (hỏi lại) đều phải hỏi — bỏ sót một là vòng lặp còn đường",
        )
        val lexicon = code("src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceLexicon.kt")
        assertTrue(lexicon.contains("fun isFillerOnly("), "`:core` phải giữ luật này")
    }

    /**
     * ⚠⚠ Cổng XÁC NHẬN **không** được dùng phép hỏi ấy.
     *
     * Một tiếng *"ừ"* đứng một mình là câu ĐỒNG Ý hợp lệ khi đang có hộp xác nhận chờ. Gọi `isFillerOnly` ở đó
     * là bịt mất cổng an toàn quan trọng nhất của cả tính năng — KDoc `VoiceLexicon.isFillerOnly` cảnh báo đúng
     * điều này, và bài đây là lưới an toàn cho nó.
     */
    @Test
    fun `cong xac nhan van nhan mot tieng u la DONG Y`() {
        val body = SourceRoots.body(turns, "private fun VoiceSession.listenForConfirm(")
        assertTrue(body.contains("VoiceLexicon.confirmAnswer(heard)"), "phải hỏi confirmAnswer")
        assertFalse(body.contains("isFillerOnly"), "KHÔNG được lọc từ đệm ở cổng xác nhận")
        assertFalse(body.contains("endsConversation"), "KHÔNG được dùng phép hỏi của hội thoại ở đây")
    }

    // ── (c) Mọi lần mở lại micro tiêu một suất của trần phiên ────────────────────────────────────

    @Test
    fun `duong hoi lai cung tieu mot suat cua tran phien`() {
        val body = SourceRoots.body(turns, "private fun VoiceSession.listenAgain(")
        assertTrue(
            body.contains("followUps >= VoiceSession.MAX_FOLLOW_UPS"),
            "đường hỏi-lại phải kiểm CHUNG trần với hội thoại — hai quỹ song song là hai lần trần",
        )
        assertTrue(body.contains("followUps++"), "và phải tiêu một suất, không chỉ đọc")
        // Hai đường mở micro nối ⇒ đúng hai chỗ tăng bộ đếm.
        assertEquals(
            2, Regex("""followUps\+\+""").findAll(turns).count(),
            "đúng hai chỗ tăng: `followUp` và `listenAgain`",
        )
    }

    // ── (d)+(e) Chốt một-micro và cầu chì, đặt ở tầng THI HÀNH ───────────────────────────────────

    @Test
    fun `chot mot-micro xin TRUOC khi mo AudioRecord va nha trong finally`() {
        val listen = SourceRoots.body(capture, "    fun listen(")
        val acquire = listen.indexOf("VoiceSingleFlight.acquire(label)")
        assertTrue(acquire >= 0, "phải xin chốt trong `listen`")
        assertTrue(listen.contains("finally {") && listen.contains("VoiceSingleFlight.release()"), "phải nhả trong finally")
        // Chốt phải đứng trước cả `openRecord` — nó nằm trong `listenGranted`, tức sau lời gọi có chốt.
        assertTrue(
            listen.indexOf("listenGranted(") > acquire,
            "phải cầm chốt RỒI mới vào thân lượt nghe (nơi `openRecord` mở AudioRecord)",
        )
        assertFalse(
            SourceRoots.body(capture, "private fun listenGranted(").contains("VoiceSingleFlight.acquire("),
            "không được xin chốt hai lần",
        )
        // Cả hai nhánh bị chắn phải ghi đúng MỘT dòng nhật ký rồi rút — không ném, không quay vòng bận.
        assertTrue(listen.contains("Grant.Busy"), "phải xử lý nhánh bị lượt khác giữ")
        assertTrue(listen.contains("Grant.Fused"), "phải xử lý nhánh cầu chì")
        assertEquals(
            2, Regex("""Log\.w\(""").findAll(listen).count(),
            "mỗi nhánh bị chắn ĐÚNG một dòng nhật ký — im lặng thì không ai biết, nhiều dòng thì lại là nguồn tải log",
        )
    }

    /** Chốt phải là chỗ CUỐI trước phần cứng ⇒ không ai được mở `AudioRecord` ngoài đường đã cầm chốt. */
    @Test
    fun `khong duong nao mo AudioRecord ma khong qua chot`() {
        assertEquals(
            1, Regex("""AudioRecord\(""").findAll(capture).count(),
            "chỉ ĐÚNG một chỗ dựng AudioRecord trong cả tệp (trong `openRecord`)",
        )
        val open = SourceRoots.body(capture, "private fun openRecord()")
        assertTrue(open.contains("AudioRecord("), "và nó nằm trong `openRecord`, thứ chỉ `listenGranted` gọi")
        assertEquals(
            1, Regex("""openRecord\(\)""").findAll(SourceRoots.body(capture, "private fun listenGranted(")).count(),
            "chỉ thân-đã-có-chốt được gọi `openRecord`",
        )
    }

    // ── [P0-2] Tiếng bíp không được rơi vào cửa sổ đo nền ────────────────────────────────────────

    @Test
    fun `bip dau luot cho toi khi nen da chot`() {
        val body = SourceRoots.body(capture, "private fun listenGranted(")
        assertTrue(body.contains("var beepPending"), "tiếng bíp phải được HOÃN, không phát ngay khi mở micro")
        assertEquals(
            2, Regex("""beepPending && !ep\.floorWindowOpen\(\)""").findAll(body).count(),
            "cả hai chỗ bíp phải hỏi CÙNG một cổng — bíp trong cửa sổ đo nền là nguồn của nền 531/602/888/4317",
        )
        val turn = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceTurnEndpoint.kt")
        assertTrue(
            turn.contains("vad == null && rms?.phase == VoiceEndpointer.Phase.FLOOR"),
            "chỉ đường LÙI mới có cửa sổ đo nền; đường VAD phải bíp ngay như trước [P0-2]",
        )
    }

    @Test
    fun `bip cuoi khong bam sang luot noi ke tiep`() {
        assertTrue(capture.contains("tailBeep: Boolean"), "đường đóng mic phải biết có được bíp cuối hay không")
        assertTrue(capture.contains("if (tailBeep) tone("), "và phải thật sự gác tiếng bíp ấy")
        assertTrue(
            capture.contains("closeRecord(record, focus, tailBeep = beep)"),
            "lượt nào không bíp đầu thì cũng không bíp cuối — lượt nối mở micro chỉ ~50 ms sau khi đóng, " +
                "mà [ĐO] tiếng bíp cuối mất tới 4955 ms để phát xong",
        )
    }

    /** Trần nền + hệ số mới phải giữ ngưỡng **dưới** giọng yếu nhất đo được trên xe (rms 206). */
    @Test
    fun `nguong toi da van nam duoi giong yeu nhat do duoc`() {
        val worst = maxOf(VoiceEndpointer.FLOOR_CAP * VoiceEndpointer.FLOOR_FACTOR, VoiceEndpointer.ABS_FLOOR)
        assertTrue(worst < 206, "ngưỡng lớn nhất có thể ($worst) phải dưới giọng yếu nhất đo được (rms 206)")
        // Và vẫn phải trên mức im thật cao nhất quan sát được (66) — nếu không thì mọi lượt im đều bị giải mã.
        assertTrue(VoiceEndpointer.ABS_FLOOR > 66, "sàn phải trên mức im thật cá biệt (66)")
        assertTrue(VoiceEndpointer.FLOOR_CAP > 66, "trần nền phải trên mức im thật cá biệt (66)")
    }
}
