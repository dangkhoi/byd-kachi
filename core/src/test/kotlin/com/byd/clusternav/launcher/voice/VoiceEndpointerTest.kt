package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V3 · R2 — NGẮT CÂU khi người ta ngừng nói ═══════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` R2. Bài này khoá lại **hai lỗi hiện trường đối nghịch nhau**:
 *  • ca 09:10 [ĐO xe 2026-09-16] — nghe **hết trần 8,4 s** cho một câu đã nói xong từ lâu;
 *  • ca 09:14 [ĐO xe] — micro gần câm (**rms 30–50** cả phiên) ⇒ một ngưỡng tuyệt đối thấp sẽ coi tiếng ồn nền
 *    là giọng người và chốt ngay khi chưa ai nói.
 *
 * Mọi số ở đây là **rms giả**, không có tệp âm thanh nào — đúng ranh giới `:core` (thuần, kiểm off-car).
 */
class VoiceEndpointerTest {

    private val chunk = 200   // ms mỗi khối, đúng `VoiceCapture.CHUNK_SAMPLES` (16 kHz / 5)

    /** Đẩy [n] khối cùng mức [rms] vào bộ ngắt; trả pha cuối cùng. */
    private fun feed(ep: VoiceEndpointer, rms: Int, n: Int): VoiceEndpointer.Phase {
        var p = ep.phase
        repeat(n) { p = ep.accept(rms, chunk) }
        return p
    }

    @Test
    fun `do nen 300 ms roi moi xet — khoi dau tien khong bao gio chot cau`() {
        val ep = VoiceEndpointer()
        assertEquals(VoiceEndpointer.Phase.FLOOR, ep.phase)
        assertEquals(-1, ep.threshold(), "chưa đo xong nền thì chưa có ngưỡng")
        // 300 ms = 2 khối 200 ms (khối thứ hai vượt mốc ⇒ chốt nền).
        ep.accept(40, chunk)
        assertEquals(VoiceEndpointer.Phase.FLOOR, ep.phase)
        ep.accept(60, chunk)
        assertEquals(VoiceEndpointer.Phase.WAITING, ep.phase)
        assertEquals(50, ep.floor, "nền = trung vị của cửa sổ đầu (40, 60)")
    }

    @Test
    fun `nguong la max(2x nen da kep, 120) — tran nen chan ca cua so do bi nhiem`() {
        // Cabin ồn vừa: nền 60 ⇒ 2×60 = 120 = đúng sàn.
        val ồn = VoiceEndpointer().also { feed(it, 60, 2) }
        assertEquals(120, ồn.threshold())
        // Nền 200 bị TRẦN kéo về 90 ⇒ ngưỡng 180, không phải 400. Đây là thứ giữ ngưỡng ở dưới giọng thật.
        val to = VoiceEndpointer().also { feed(it, 200, 2) }
        assertEquals(VoiceEndpointer.FLOOR_CAP, to.floor, "nền đo được phải bị kẹp NGAY lúc chốt, để nhật ký không nói dối")
        assertEquals(180, to.threshold())
        // [ĐO xe 09:14] micro gần câm: nền 2 ⇒ 2×2 = 4, nhưng SÀN kéo lên 120 — nếu không thì mọi tiếng lạo xạo
        // rms 8 đã thành "có tiếng" và câu bị chốt trước khi người lái mở miệng.
        val câm = VoiceEndpointer().also { feed(it, 2, 2) }
        assertEquals(VoiceEndpointer.ABS_FLOOR, câm.threshold())
    }

    // ══ [P0-2] BA VỆT ĐO THẬT từ `voice-1.68-real.txt` (12 phút trên xe owner) ════════════════════════════
    //
    // Ba bài dưới đây KHÔNG phải ca nghĩ ra: mỗi con số lấy nguyên từ bản ghi đã làm owner phải chịu 189/300
    // lượt nghe điếc. Chúng là lưới an toàn cho đúng hai đầu của phép đánh đổi ở KDoc lớp — nới trần nền lên là
    // bài (i) đỏ, siết hệ số xuống là bài (ii) đỏ.

    /**
     * (i) **Nền nhiễm 531 + giọng thật rms 206 ⇒ PHẢI nghe thấy.**
     *
     * Đây đúng ca đã hỏng trên xe: bản cũ cho `nen=531 nguong=1593`, giọng 206 không bao giờ vượt nổi, lượt nghe
     * chạy trọn 8,4 s rồi mô hình bịa ra một tiếng `"ừ"`.
     */
    @Test
    fun `P0 nen nhiem 531 van nghe duoc giong rms 206`() {
        val ep = VoiceEndpointer()
        feed(ep, 531, 2)
        assertEquals(VoiceEndpointer.FLOOR_CAP, ep.floor, "nền nhiễm phải bị kẹp")
        assertTrue(ep.threshold() < 206, "ngưỡng ${ep.threshold()} phải nằm DƯỚI giọng yếu nhất đo được (206)")
        assertEquals(VoiceEndpointer.Phase.WAITING, feed(ep, 206, 1), "200 ms tiếng — chưa đủ 400")
        assertEquals(VoiceEndpointer.Phase.SPEAKING, feed(ep, 206, 1), "đủ 400 ms tiếng ⇒ PHẢI nhận ra")
        assertTrue(ep.sawSpeech(), "lượt này phải được đánh dấu là CÓ tiếng: ${ep.summary()}")
        assertEquals(VoiceEndpointer.Phase.ENDED, feed(ep, 34, 4), "im 800 ms ⇒ chốt")
    }

    /** (ii) **Nền 36 + im thật 30–36 ⇒ KHÔNG được coi là tiếng** (nếu không, mọi lượt im đều bị giải mã). */
    @Test
    fun `P0 nen 36 va im that 30-36 thi khong bao gio thay tieng`() {
        val ep = VoiceEndpointer()
        feed(ep, 36, 2)
        assertEquals(VoiceEndpointer.ABS_FLOOR, ep.threshold(), "nền thấp ⇒ sàn tuyệt đối cầm ngưỡng")
        // 12 giây im ở đúng dải đo được trên xe (30..36, kể cả lượt cá biệt 66).
        listOf(30, 33, 36, 31, 66, 34).forEach { feed(ep, it, 10) }
        assertEquals(VoiceEndpointer.Phase.WAITING, ep.phase)
        assertTrue(!ep.sawSpeech(), "im thật KHÔNG được tính là tiếng — đây là cổng chặn ~200 lượt giải mã/12 phút")
        assertEquals(-1, ep.speechStartMs)
    }

    /** (iii) **Nền sạch 30 + giọng 627 rồi im ⇒ chốt ĐÚNG lúc**, không sớm không muộn. */
    @Test
    fun `P0 nen 30 giong 627 roi im thi chot dung moc`() {
        val ep = VoiceEndpointer()
        feed(ep, 30, 2)                                   // 400 ms đo nền
        assertEquals(VoiceEndpointer.ABS_FLOOR, ep.threshold())
        feed(ep, 627, 2)                                  // 400 ms tiếng ⇒ SPEAKING
        assertEquals(VoiceEndpointer.Phase.SPEAKING, ep.phase)
        assertEquals(VoiceEndpointer.Phase.SPEAKING, feed(ep, 32, 3), "600 ms im — chưa đủ 800")
        assertEquals(VoiceEndpointer.Phase.ENDED, feed(ep, 32, 1), "800 ms im ⇒ CHỐT")
        // Chốt ở 400 (nền) + 400 (tiếng) + 800 (im) = 1600 ms, thay vì ăn trọn trần 8400 ms như bản cũ.
        assertEquals(1_600, ep.elapsedMs, ep.summary())
        assertEquals(400, ep.voicedMs)
        assertEquals(800, ep.silenceMs)
    }

    @Test
    fun `co tieng 400 ms roi im 800 ms thi CHOT — day la ca 8,4 giay tren xe`() {
        val ep = VoiceEndpointer()
        feed(ep, 50, 2)                               // nền 50 ⇒ ngưỡng 150
        assertEquals(VoiceEndpointer.Phase.WAITING, feed(ep, 900, 1), "một khối 200 ms chưa đủ 400 ms tiếng")
        assertEquals(VoiceEndpointer.Phase.SPEAKING, feed(ep, 900, 1), "đủ 400 ms ⇒ từ đây im là chốt được")
        assertEquals(VoiceEndpointer.Phase.SPEAKING, feed(ep, 10, 3), "600 ms im — chưa đủ 800")
        assertEquals(VoiceEndpointer.Phase.ENDED, feed(ep, 10, 1), "800 ms im ⇒ CHỐT")
        // Mốc giờ phải đọc được trong nhật ký — đó là thứ duy nhất chốt được "3,1 giây đi đâu" ở lượt xe sau.
        assertTrue(ep.speechStartMs >= 0 && ep.speechEndMs > ep.speechStartMs, ep.summary())
        assertTrue(ep.summary().contains("chot="), ep.summary())
    }

    @Test
    fun `im TRUOC khi co tieng thi KHONG chot — nguoi lai bam roi moi hit hoi`() {
        val ep = VoiceEndpointer()
        feed(ep, 50, 2)
        // 3 giây im ngay sau khi mở micro: không được chốt, vì chưa ai nói gì.
        assertEquals(VoiceEndpointer.Phase.WAITING, feed(ep, 10, 15))
        // Rồi mới nói — vẫn bắt được đầy đủ.
        assertEquals(VoiceEndpointer.Phase.SPEAKING, feed(ep, 900, 2))
        assertEquals(VoiceEndpointer.Phase.ENDED, feed(ep, 10, 4))
    }

    @Test
    fun `mot tieng cach ngan KHONG chot cau — phai du 400 ms tieng cong don`() {
        val ep = VoiceEndpointer()
        feed(ep, 50, 2)
        feed(ep, 900, 1)                              // 200 ms tiếng
        assertEquals(VoiceEndpointer.Phase.WAITING, feed(ep, 10, 10), "mới 200 ms tiếng ⇒ im bao lâu cũng không chốt")
        feed(ep, 900, 1)                              // cộng dồn đủ 400 ms
        assertEquals(VoiceEndpointer.Phase.SPEAKING, ep.phase)
    }

    /**
     * Ồn liên tục ⇒ không bao giờ chốt; trần cứng của phiên vẫn là đường thoát.
     *
     * ⚠ Đổi sau [P0-2]: với trần nền 90 thì một cabin ồn **thật sự** (rms 1400) nay vượt ngưỡng 180 ⇒ nó vào
     * `SPEAKING` thay vì ở lại `WAITING`. Đó là **đánh đổi đã chọn**, không phải hồi quy — xem KDoc lớp, mục
     * *"bộ này nay thiên về nghe thấy"*: nhận nhầm ồn thành tiếng chỉ tốn một lượt giải mã, còn không bao giờ
     * nhận ra tiếng thì mất trọn 8,4 s, và [ĐO] cho thấy ca sau xảy ra ở 63% số lượt. Tính chất **thật sự** phải
     * giữ vẫn nguyên: ồn liên tục **không có quãng im nào** ⇒ không bao giờ `ENDED`.
     */
    @Test
    fun `on lien tuc thi KHONG BAO GIO chot — tran cung cua phien van la duong thoat`() {
        val ep = VoiceEndpointer()
        feed(ep, 500, 2)                              // nền 500 → kẹp 90 ⇒ ngưỡng 180
        assertEquals(VoiceEndpointer.Phase.SPEAKING, feed(ep, 1400, 60), "ồn trên ngưỡng ⇒ vào SPEAKING")
        assertTrue(ep.phase != VoiceEndpointer.Phase.ENDED, "không có quãng im nào ⇒ KHÔNG được chốt")
        // Ồn DƯỚI ngưỡng liên tục: không bao giờ vào SPEAKING ⇒ cũng không bao giờ ENDED.
        val ep2 = VoiceEndpointer().also { feed(it, 20, 2) }
        assertEquals(VoiceEndpointer.Phase.WAITING, feed(ep2, 100, 60))
    }

    /**
     * [SOÁT 2026-09-16 · P3] **Hợp đồng với chỗ gọi**: pha WAITING KHÔNG có đường tự chốt.
     *
     * Bài trên (`on lien tuc…`) chạm vào tính chất này như một hệ quả phụ; bài này khoá nó **đích danh** vì nó là
     * một **phụ thuộc ngầm** giữa hai tầng (xem khối ⚠⚠ ở KDoc [VoiceEndpointer]): lượt không có tiếng nào thì
     * `silenceMs` không nhúc nhích, [VoiceEndpointer.sawSpeech] vẫn `false`, và `ENDED` **không bao giờ tới** ⇒
     * ai đóng phiên dựa vào `ENDED` phải tự có trần. Chỗ gọi thật đang giữ đúng nghĩa vụ đó ở hai lớp —
     * `VoiceCapture.kt:203,214` (trần `maxMs` = `VoiceSession.MAX_LISTEN_MS` 8 s) và `VoiceCapture.kt:273`
     * (không có tiếng ⇒ không giải mã).
     *
     * Bài này đỏ ở đúng hai thay đổi đáng sợ: ai đó cho WAITING tự chốt (⇒ cắt lời người vừa bấm mic rồi hít
     * hơi), hoặc ai đó cho khối im cộng vào `silenceMs` từ trước khi có tiếng (⇒ cùng hậu quả, đường vòng).
     */
    @Test
    fun `im mai thi dung o WAITING — tran phai do CHO GOI giu, khong phai bo nay`() {
        val ep = VoiceEndpointer()
        feed(ep, 40, 2)                                  // nền 40 ⇒ ngưỡng = max(80, 120) = 120
        assertEquals(VoiceEndpointer.Phase.WAITING, ep.phase)
        // 200 khối × 200 ms = 40 giây im — gấp năm lần trần một phiên thật (8 s).
        assertEquals(VoiceEndpointer.Phase.WAITING, feed(ep, 10, 200), "im thì KHÔNG được tự chốt")
        assertEquals(0, ep.silenceMs, "im ở pha WAITING KHÔNG cộng vào quãng im — chỉ SPEAKING mới cộng")
        assertEquals(0, ep.voicedMs)
        assertEquals(-1, ep.speechStartMs)
        assertTrue(!ep.sawSpeech(), "chưa từng có tiếng ⇒ chỗ gọi phải bỏ lượt giải mã (VoiceCapture.kt:273)")
    }

    @Test
    fun `da ENDED thi khong doi pha nua — chot cau la mot chieu`() {
        val ep = VoiceEndpointer()
        feed(ep, 50, 2); feed(ep, 900, 2); feed(ep, 10, 4)
        assertEquals(VoiceEndpointer.Phase.ENDED, ep.phase)
        val at = ep.elapsedMs
        assertEquals(VoiceEndpointer.Phase.ENDED, feed(ep, 5000, 10))
        assertEquals(at, ep.elapsedMs, "sau khi chốt thì không đếm thêm mili-giây nào")
    }
}
