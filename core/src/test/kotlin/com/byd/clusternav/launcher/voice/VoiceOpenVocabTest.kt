package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * ═══ V1.1 · PHÉP GHÉP HAI LƯỢT — bài canh cho *"lượt 2 chỉ chạy sau cụm kích hoạt"* ═══════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R16**. Chuỗi trong bài này là **hình dạng thật** mà Vosk trả về:
 * bản ngữ pháp thay mọi thứ ngoài tập đóng bằng `[unk]` (một mục cho mỗi từ), còn bản tự do trả chữ thường.
 *
 * ## Vì sao phép ghép phải kiểm được off-car
 * Nó quyết định **chuỗi nào đi xuống bộ phân tích**, tức quyết định xe làm gì. Một lỗi cắt ở đây không hiện ra
 * như một lỗi: nó hiện ra như *"máy nghe nhầm"* — thứ không ai lần ngược được về một dòng mã.
 */
class VoiceOpenVocabTest {

    // ══ (1) Điều kiện chạy lượt 2 — HAI vế ═══════════════════════════════════════════════════════════════

    /** Có `[unk]` **và** đứng sau một cụm kích hoạt ⇒ chạy. */
    @Test
    fun `chay luot 2 khi co cum kich hoat roi toi unk`() {
        assertEquals(listOf("phat", "bai"), VoiceOpenVocab.triggerOf("phát bài [unk] [unk]"))
        assertEquals(listOf("dan", "duong", "toi"), VoiceOpenVocab.triggerOf("dẫn đường tới [unk] [unk] [unk]"))
        assertEquals(listOf("play"), VoiceOpenVocab.triggerOf("play [unk]"))
    }

    /**
     * **[ĐO] 2026-09-14 — ca đã bác bỏ thiết kế đầu tiên.**
     *
     * Câu *"phát bài Diễm Xưa bằng YouTube Music"* qua WAV probe trên máy ảo cho ra
     * `"phát lài diếm sơ bằng mu yt"` — **không một `[unk]` nào**, dù cả phần đuôi đều ngoài tập đóng. Bản đầu
     * gate theo `[unk]` ⇒ lượt 2 **không bao giờ chạy** trên xe, trong khi mọi bài kiểm dựng tay vẫn xanh.
     * Bài này giữ cho lỗi đó không quay lại.
     */
    @Test
    fun `chay luot 2 ngay ca khi ban ngu phap khong co unk nao`() {
        assertEquals(
            listOf("phat", "bai"),
            VoiceOpenVocab.triggerOf("phát bài diếm sơ bằng mu yt"),
            "[ĐO] máy ảo: bộ giải mã ép phần lạ thành từ gần giống, KHÔNG ra [unk]",
        )
    }

    /**
     * KHÔNG có cụm kích hoạt, hoặc **không còn gì đứng sau nó** ⇒ không chạy.
     *
     * Vế này giữ cho mọi câu lệnh xe không tốn một lượt giải mã nào — và quan trọng hơn, giữ cho một chuỗi tự do
     * (kém chính xác) không bao giờ len vào một câu đã hiểu trọn vẹn bằng tập đóng.
     */
    @Test
    fun `khong chay luot 2 khi khong co cum kich hoat hoac khong con gi dung sau`() {
        listOf(
            "bật đèn đọc", "tạm dừng", "đặt nhiệt độ hai mươi hai", "mở khoá cửa",   // không có cụm kích hoạt
            "phát nhạc", "dẫn đường tới", "phát bài",                                 // có cụm, nhưng hết câu
        ).forEach {
            assertNull(VoiceOpenVocab.triggerOf(it), "«$it» không có đuôi nào mà vẫn đòi chạy lượt 2")
        }
    }

    /**
     * `[unk]` lẻ, KHÔNG đứng sau cụm kích hoạt ⇒ **không chạy**.
     *
     * Một tiếng ho, một câu người ngồi sau nói, một mẩu nhạc — đều có thể ra `[unk]`. Không có vế này thì mỗi
     * tiếng động lạ là một lượt giải mã tự do 19.529 từ chạy không công.
     */
    @Test
    fun `khong chay luot 2 khi unk khong dung sau cum kich hoat`() {
        listOf("[unk]", "[unk] [unk]", "bật đèn đọc [unk]", "[unk] bật đèn", "mở khoá cửa [unk]").forEach {
            assertNull(VoiceOpenVocab.triggerOf(it), "«$it» không có cụm kích hoạt mà vẫn đòi chạy lượt 2")
        }
    }

    // ══ (2) Ghép ═════════════════════════════════════════════════════════════════════════════════════════

    /** Ca chuẩn: đuôi lấy từ bản tự do, đầu câu giữ nguyên bản ngữ pháp. */
    @Test
    fun `ghep dau ngu phap voi duoi tu do`() {
        val m = VoiceOpenVocab.merge("phát bài [unk] [unk]", "phát bài diễm xưa")
        assertEquals("phát bài diễm xưa", m.text)
        assertEquals("diễm xưa", m.tail)
        assertNotNull(m.trigger)
    }

    @Test
    fun `ghep cau dan duong`() {
        val m = VoiceOpenVocab.merge("dẫn đường tới [unk] [unk] [unk]", "dẫn đường tới chợ bến thành")
        assertEquals("dẫn đường tới chợ bến thành", m.text)
        assertEquals("chợ bến thành", m.tail)
    }

    /**
     * Phần **sau** `[unk]` của bản ngữ pháp (*"bằng google map"*) được đặt lại ở cuối.
     *
     * Đó là phần vẫn thuộc tập đóng nên chính xác hơn hẳn bản tự do — bản tự do đọc tên thương hiệu rất tệ. Nhờ
     * vậy `VoiceIntentParser` vẫn cắt được app đích.
     */
    @Test
    fun `giu lai phan chon app da nghe bang ngu phap`() {
        val m = VoiceOpenVocab.merge(
            "dẫn đường tới [unk] [unk] [unk] bằng google map",
            "dẫn đường tới chợ bến thành bằng gu gồ mát",
        )
        assertEquals("dẫn đường tới chợ bến thành bằng google map", m.text)
        val nav = VoiceIntentParser.parseOne(m.text) as VoiceIntent.Nav
        assertEquals("chợ bến thành", nav.query)
        assertEquals(VoiceAppTargets.GMAPS, nav.app)
    }

    /** Đuôi tự do không có cụm đánh dấu nào ⇒ nối thẳng phần ngữ pháp vào cuối, không mất chữ. */
    @Test
    fun `noi thang khi duoi tu do khong chua cum danh dau`() {
        val m = VoiceOpenVocab.merge("phát bài [unk] bằng youtube music", "phát bài hạ trắng")
        assertEquals("phát bài hạ trắng bằng youtube music", m.text)
    }

    /**
     * Phần sau cụm kích hoạt **không khớp đích nào** ⇒ bỏ hẳn, không dán vào tên bài.
     *
     * [ĐO] máy ảo trả `"bằng mu yt"` cho *"bằng YouTube Music"*. Giữ lại mấy chữ ấy chỉ làm bẩn tên bài mà không
     * chọn đúng app nào — thà để tầng thi hành tự chọn app (phiên nhạc đang chạy).
     */
    @Test
    fun `bo phan chon app khi no khong khop dich nao`() {
        val m = VoiceOpenVocab.merge("phát bài diếm sơ bằng mu yt", "phát bài diễm xưa")
        assertEquals("phát bài diễm xưa", m.text)
    }

    /** Bản tự do nhắc lại cụm kích hoạt ⇒ lấy lần **cuối** (cái tên nằm sau lần nhắc sau). */
    @Test
    fun `lay lan xuat hien cuoi cua cum kich hoat`() {
        val m = VoiceOpenVocab.merge("đi tới [unk] [unk]", "đi tới ừm đi tới chợ bến thành")
        assertEquals("chợ bến thành", m.tail)
    }

    // ══ (3) Lùi an toàn — mọi ca hỏng đều về bản ngữ pháp, không về rỗng ══════════════════════════════════

    /** Lượt 2 không nghe ra gì ⇒ giữ bản ngữ pháp (đã bỏ `[unk]`), và `trigger` = `null` để chỗ gọi biết. */
    @Test
    fun `lui ve ban ngu phap khi luot 2 rong`() {
        val m = VoiceOpenVocab.merge("phát bài [unk] [unk]", "")
        assertEquals("phát bài", m.text)
        assertNull(m.trigger)
        assertEquals("", m.tail)
    }

    /** Lượt 2 nghe ra chữ nhưng **không tìm thấy cụm kích hoạt** ⇒ không đoán điểm cắt, lùi về bản ngữ pháp. */
    @Test
    fun `lui ve ban ngu phap khi khong tim thay cum kich hoat trong ban tu do`() {
        val m = VoiceOpenVocab.merge("phát bài [unk] [unk]", "một hai ba bốn")
        assertEquals("phát bài", m.text)
        assertNull(m.trigger)
    }

    /** Câu lệnh xe đi qua nguyên vẹn — phép ghép không được đụng vào chúng. */
    @Test
    fun `cau lenh xe di qua nguyen ven`() {
        val m = VoiceOpenVocab.merge("bật đèn đọc", "bật đèn độc lập")
        assertEquals("bật đèn đọc", m.text)
        assertNull(m.trigger)
    }

    @Test
    fun `bo unk va nen khoang trang`() {
        assertEquals("phát bài", VoiceOpenVocab.stripUnk("phát bài [unk] [unk]"))
        assertEquals("", VoiceOpenVocab.stripUnk("[unk] [unk]"))
        assertEquals("a b", VoiceOpenVocab.stripUnk("a [unk] b"))
    }

    // ══ (4) Bảng cụm kích hoạt ═══════════════════════════════════════════════════════════════════════════

    /**
     * Mọi cụm kích hoạt viết **không dấu** và **kết thúc bằng một từ chỉ loại**.
     *
     * Vế thứ hai là ràng buộc thiết kế, không phải hình thức: chỉ cụm kết thúc bằng *"bài / nhạc / tới / đến"*
     * (hoặc `play`/`to`) mới chắc chắn còn một cái TÊN đứng sau. Thả một động từ trần vào bảng là bật lượt tự do
     * cho mọi câu dùng động từ ấy — xem KDoc [VoiceOpenVocab.TRIGGERS].
     */
    @Test
    fun `cum kich hoat khong dau va ket thuc bang tu chi loai`() {
        val tails = setOf("bai", "nhac", "toi", "den", "play", "to")
        VoiceOpenVocab.TRIGGERS.forEach { t ->
            t.forEach { w -> assertEquals(VoiceLexicon.deaccent(w), w, "cụm `$t` còn dấu ở từ `$w`") }
            assertEquals(true, t.last() in tails, "cụm `$t` kết thúc bằng `${t.last()}` — không phải từ chỉ loại")
        }
    }

    /** Cụm dài đứng trước cụm ngắn ⇒ *"dẫn đường tới"* thắng *"đường tới"*, không cắt nhầm điểm bắt đầu đuôi. */
    @Test
    fun `cum dai duoc xet truoc cum ngan`() {
        val sizes = VoiceOpenVocab.TRIGGERS.map { it.size }
        assertEquals(sizes.sortedDescending(), sizes, "bảng phải sắp dài → ngắn")
        assertEquals(listOf("dan", "duong", "toi"), VoiceOpenVocab.triggerOf("dẫn đường tới [unk]"))
    }
}
