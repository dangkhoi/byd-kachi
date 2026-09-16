package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V2 pha NGHE · HOTWORDS — KHOÁ QUY TẮC CHUẨN HOÁ (HOA + CÓ DẤU + LỌC) ════════════════════════════════════
 *
 * Khoá đúng cái làm biasing hỏng-im-lặng: hotword thường/không dấu ⇒ native bỏ; token có chữ số (tên app/số ô)
 * ⇒ mô hình VN không phát ra được ⇒ biasing vô nghĩa. [ĐO] `docs/diagnostics/vn-stt-sherpa-emulator-eval-*`.
 */
class SherpaHotwordsTest {

    @Test
    fun `uppercases and keeps Vietnamese accents`() {
        assertEquals("BẬT ĐÈN ĐỌC SÁCH", SherpaHotwords.normalize("bật đèn đọc sách"))
        assertEquals("ÂM LƯỢNG", SherpaHotwords.normalize("Âm lượng"))
        assertEquals("PIN", SherpaHotwords.normalize("pin"))
    }

    @Test
    fun `collapses internal whitespace and trims`() {
        assertEquals("DẪN ĐƯỜNG", SherpaHotwords.normalize("  dẫn    đường  "))
    }

    @Test
    fun `rejects empty or too short, drops digit tokens, keeps the rest`() {
        assertNull(SherpaHotwords.normalize("a"), "một ký tự ⇒ bỏ")
        assertNull(SherpaHotwords.normalize("   "), "rỗng ⇒ bỏ")
        assertNull(SherpaHotwords.normalize("360"), "chỉ có chữ số ⇒ không còn gì để bias")
        // [ĐO] emulator-voice-e2e-2026-09-15 §3 L3: luật cũ (`else -> return null`) vứt CẢ cụm khi gặp một dấu
        // câu ⇒ 50/187 nhãn không bao giờ thành hotword. Nay dấu câu chỉ là NGẮT TỪ, chữ số bỏ theo TOKEN.
        assertEquals("NHIỆT ĐỘ", SherpaHotwords.normalize("nhiệt-độ"), "gạch nối ⇒ ngắt từ, không giết cụm")
        assertEquals("Ô SỐ", SherpaHotwords.normalize("ô số 2"), "bỏ token số, giữ phần chữ")
        assertEquals("BỤI MỊN", SherpaHotwords.normalize("Bụi mịn PM2.5"))
    }

    @Test
    fun `alternative separators split one label into several hotwords`() {
        assertEquals(listOf("KHOÁ", "MỞ KHOÁ"), SherpaHotwords.phrasesOf("Khoá / mở khoá"))
        assertEquals(listOf("PIN", "SOC"), SherpaHotwords.phrasesOf("Pin (SOC)"))
        assertEquals(listOf("KÍNH TRƯỚC TRÁI"), SherpaHotwords.phrasesOf("Kính trước-trái"))
        assertEquals(emptyList<String>(), SherpaHotwords.phrasesOf("2,5"))
    }

    @Test
    fun `file content is one hotword per line, deduped, order-stable`() {
        val out = SherpaHotwords.fileContent(listOf("tắt", "Tắt", "bật đèn", "xem pin", "pin"))
        val lines = out.trimEnd().split("\n")
        assertEquals(listOf("TẮT", "BẬT ĐÈN", "XEM PIN", "PIN"), lines)
        assertTrue(out.endsWith("\n"), "tệp hotwords phải kết bằng newline")
    }

    @Test
    fun `empty or all-invalid input yields empty file`() {
        assertEquals("", SherpaHotwords.fileContent(emptyList()))
        assertEquals("", SherpaHotwords.fileContent(listOf("2", "!", "x")))
    }

    // ── Tệp CỤM (spec `kachi-voice-hotword-phrases.html` R2) — hai luật lọc của bản ship ──────────────

    @Test
    fun `phraseFile chi giu cum, bo dong mot tu, van giu cum ASCII`() {
        val out = SherpaHotwords.phraseFile(listOf("pin", "xem pin", "Tắt", "tắt đèn đọc", "xem pin"))
        // `XEM PIN` không có chữ nào mang dấu — luật "bỏ dòng ASCII thuần" của spec bản nháp đã làm rụng đúng
        // câu được đo nhiều nhất, nên nó KHÔNG được quay lại (§R2b).
        assertEquals(listOf("XEM PIN", "TẮT ĐÈN ĐỌC"), out.trimEnd().split("\n"))
        assertTrue(out.endsWith("\n"))
        assertEquals("", SherpaHotwords.phraseFile(listOf("pin", "tắt", "2,5")), "không cụm nào ⇒ tệp rỗng")
        assertTrue(SherpaHotwords.isPhrase("XEM PIN"))
        assertFalse(SherpaHotwords.isPhrase("PIN"))
    }

    @Test
    fun `dropPrefixes bo dong la tien to THEO TU, khong bo dong chi giong dau chuoi`() {
        val kept = SherpaHotwords.dropPrefixes(
            listOf("CHẾ ĐỘ", "CHẾ ĐỘ LÁI", "CHẾ ĐỘ LÁI THỂ THAO", "CHẾ ĐỘI", "MỞ CỬA SỔ"),
        )
        // Chuỗi bắc cầu: cả hai cụm ngắn đều rụng. `CHẾ ĐỘI` chỉ giống ký tự đầu, KHÔNG phải tiền tố theo từ.
        assertEquals(listOf("CHẾ ĐỘ LÁI THỂ THAO", "CHẾ ĐỘI", "MỞ CỬA SỔ"), kept)
        assertEquals(emptyList<String>(), SherpaHotwords.dropPrefixes(emptyList()))
    }

    @Test
    fun `dropPrefixes khong duoc dua vao viec dau cach la ky tu nho nhat`() {
        // Bản đầu chỉ nhìn ĐÚNG MỘT phần tử kế tiếp sau khi xếp — đúng nhờ một tính chất của chỗ gọi (normalize
        // chỉ sinh chữ cái + dấu cách). Hàm công khai thì không được đúng nhờ giả định nằm ở file khác: một
        // chuỗi có ký tự < dấu cách (vd TAB khi ai đó đổ thẳng dòng TSV vào) chen vào giữa là luật câm lặng.
        val kept = SherpaHotwords.dropPrefixes(listOf("MỞ CỬA", "MỞ CỬA\tGHI CHÚ", "MỞ CỬA SỔ"))
        assertEquals(listOf("MỞ CỬA\tGHI CHÚ", "MỞ CỬA SỔ"), kept)
        // Trùng lặp ở đầu vào không được che mất luật.
        assertEquals(
            listOf("MỞ CỬA SỔ", "MỞ CỬA SỔ"),
            SherpaHotwords.dropPrefixes(listOf("MỞ CỬA", "MỞ CỬA", "MỞ CỬA SỔ", "MỞ CỬA SỔ")),
        )
    }

    /**
     * ═══ H3 · BẪY `YOUTUBE MUSIC` — dòng mở đầu bằng một TÊN APP nuốt mất phần đuôi ══════════════════════════
     *
     * Cùng cơ chế nguồn với luật tiền tố (sherpa-onnx `csrc/context-graph.cc` `ForwardOneStep`: khớp trọn một
     * hotword ⇒ đồ thị về gốc), nhưng [dropPrefixes] **không** bắt được ca này: *"YOUTUBE MUSIC"* chỉ là tiền tố
     * của một dòng khác khi dòng ấy có mặt trong tệp, mà *"vào ô số hai"* thì không bao giờ là hotword (số ô bị
     * luật bỏ-token-chữ-số loại từ đầu). Kết quả: câu *"mở youtube music vào ô số hai"* mất mệnh đề ô — tức app
     * mở toàn màn thay vì vào đúng ô người ta nói.
     */
    @Test
    fun `dropAppNameLeading bo dong mo dau bang ten app, giu dong co dong tu dung truoc`() {
        val apps = listOf("YouTube", "YouTube Music", "gu gồ máp")
        val kept = SherpaHotwords.dropAppNameLeading(
            listOf("YOUTUBE MUSIC", "MỞ YOUTUBE MUSIC", "YOUTUBE NHẠC", "GU GỒ MÁP", "MỞ GU GỒ MÁP", "MỞ CỬA SỔ"),
            apps,
        )
        assertEquals(listOf("MỞ YOUTUBE MUSIC", "MỞ GU GỒ MÁP", "MỞ CỬA SỔ"), kept)
        // Danh sách rỗng ⇒ không đụng gì (tên app do CHỖ GỌI cấp, tệp này không biết app nào — CLAUDE.md §7).
        assertEquals(listOf("YOUTUBE MUSIC"), SherpaHotwords.dropAppNameLeading(listOf("YOUTUBE MUSIC"), emptyList()))
        // Chỉ bỏ khi trùng **trọn từ**: *"YOUTUBER VIỆT"* không mở đầu bằng tên app nào.
        assertEquals(
            listOf("YOUTUBER VIỆT"),
            SherpaHotwords.dropAppNameLeading(listOf("YOUTUBER VIỆT"), listOf("YouTube")),
        )
    }

    @Test
    fun `phraseFile ap ca hai bo loc — ten app dung tran khong con la mot dong`() {
        val out = SherpaHotwords.phraseFile(
            listOf("gu gồ máp", "mở gu gồ máp", "xem pin"),
            listOf("gu gồ máp"),
        )
        assertEquals(listOf("MỞ GU GỒ MÁP", "XEM PIN"), out.trimEnd().split("\n"))
    }

    @Test
    fun `english app names are dropped so parser not the bias handles them`() {
        // "youtube" là Latin thường model VN không phát ra ⇒ vẫn giữ (chữ cái) NHƯNG không nên gây lỗi;
        // ở đây chỉ kiểm nó không làm ném và được viết hoa — quyết định giữ/bỏ tên app nằm ở tầng wiring.
        assertEquals("YOUTUBE", SherpaHotwords.normalize("youtube"))
        assertFalse(SherpaHotwords.fileContent(listOf("youtube music")).isBlank())
    }
}
