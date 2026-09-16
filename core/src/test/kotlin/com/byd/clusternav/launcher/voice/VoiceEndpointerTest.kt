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
    fun `nguong la max(3x nen, 120) — san tuyet doi giu ca ca micro gan cam`() {
        // Cabin ồn: nền 200 ⇒ ngưỡng 600 (bám theo nền).
        val ồn = VoiceEndpointer().also { feed(it, 200, 2) }
        assertEquals(600, ồn.threshold())
        // [ĐO xe 09:14] micro gần câm: nền 2 ⇒ 3×2 = 6, nhưng SÀN kéo lên 120 — nếu không thì mọi tiếng lạo xạo
        // rms 8 đã thành "có tiếng" và câu bị chốt trước khi người lái mở miệng.
        val câm = VoiceEndpointer().also { feed(it, 2, 2) }
        assertEquals(VoiceEndpointer.ABS_FLOOR, câm.threshold())
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

    @Test
    fun `on lien tuc thi KHONG BAO GIO chot — tran cung cua phien van la duong thoat`() {
        val ep = VoiceEndpointer()
        feed(ep, 500, 2)                              // nền 500 ⇒ ngưỡng 1500
        // Cabin ồn nhưng dưới ngưỡng: không bao giờ vào SPEAKING ⇒ không bao giờ ENDED.
        assertEquals(VoiceEndpointer.Phase.WAITING, feed(ep, 1400, 60))
        // Ồn TRÊN ngưỡng liên tục: vào SPEAKING nhưng không có quãng im nào ⇒ cũng không ENDED.
        val ep2 = VoiceEndpointer().also { feed(it, 500, 2) }
        assertEquals(VoiceEndpointer.Phase.SPEAKING, feed(ep2, 5000, 60))
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
