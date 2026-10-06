package com.byd.clusternav.launcher.voice

import java.text.Normalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 VOICE-ALT-LABEL-HOTWORD — bài canh nhãn app VIẾT CHỮ VIỆT vào hotword (spec `kachi-293-voice.html` §7 OQ1 + §9).
 *
 * Khoá đúng những gì ma trận host 2026-10-06 đã quyết: chỉ nhãn chữ Việt (nhãn chữ Anh [ĐO] kéo *"mở ghi chú"* thành
 * `OpenApp(Video)` — mở nhầm app); luật đơn điệu theo LỚP (tĩnh ⊆ + tên đã dạy ⊆ + nhãn); không dạng đọc, không tên đã
 * dạy (tên gõ không bias — OQ3); nhãn nối dài cách gọi app tĩnh / nhãn khác không bias (bẫy `w10`); trần
 * [SherpaLabelHotwords.MAX_LABELS]; không nhãn ⇒ tệp y nguyên từng byte.
 */
class SherpaLabelHotwordsTest {

    private val base = SherpaBiasing.hotwordsFile()
    private fun lines(f: String) = f.lines().filter { it.isNotBlank() }
    private val baseLines = lines(base).toSet()
    private fun t(pkg: String, a: String, src: TaughtSource = TaughtSource.SPEECH) = TaughtName(pkg, src, a, "x")
    private fun file(vararg labels: String) = SherpaBiasing.hotwordsFile(labels = labels.toList())

    @Test
    fun `khong nhan thi tep y nguyen tung byte, ca khi co ten da day`() {
        assertEquals(base, SherpaBiasing.hotwordsFile(labels = emptyList()))
        val taught = listOf(t("com.example.flix", "nep leag"))
        assertEquals(SherpaBiasing.hotwordsFile(taught = taught), SherpaBiasing.hotwordsFile(taught = taught, labels = emptyList()))
    }

    @Test
    fun `nhan chu Viet sinh MO DUA BAT va tep moi la tap cha cua tep tinh`() {
        val l = lines(file("Máy ảnh", "Danh bạ", "Ghi âm", "Thư viện"))
        assertTrue(l.containsAll(baseLines), "tệp có nhãn phải ⊇ tệp tĩnh")
        listOf("MỞ MÁY ẢNH", "ĐƯA MÁY ẢNH", "BẬT MÁY ẢNH", "MỞ DANH BẠ", "MỞ GHI ÂM", "ĐƯA THƯ VIỆN")
            .forEach { assertTrue(it in l, "thiếu «$it»") }
        assertFalse("MÁY ẢNH" in l, "nhãn trần không đứng một mình (tên app đứng đầu câu nuốt mệnh đề ô)")
        assertTrue(l.none { !it.contains(' ') }, "không dòng một từ")
        assertTrue(l.none { it.any(Char::isDigit) }, "không chữ số")
        assertTrue(l.all { it == it.uppercase() }, "HOA")
        val set = l.toSet()
        assertTrue(l.none { x -> set.any { y -> y != x && y.startsWith("$x ") } }, "không dòng nào là tiền tố dòng khác")
    }

    @Test
    fun `nhan chu Anh, chu Han, chu khong dau khong vao hotword`() {
        // [ĐO host 2026-10-06] thêm nhãn chữ Anh: "mở ghi chú" (app không cài) ⇒ "MỞ VIDEO" ⇒ OpenApp(Video) — 3/9 lượt khó nghe.
        assertEquals(base, file("Settings", "Video", "YouTube", "Bluetooth", "Chrome", "导航", "May anh"))
        assertFalse(SherpaLabelHotwords.isVietnamese("Radio"))
        assertFalse(SherpaLabelHotwords.isVietnamese("导航"))
        assertFalse(SherpaLabelHotwords.isVietnamese("Bản đồ 导航"), "lẫn chữ Hán ⇒ không có token")
        assertFalse(SherpaLabelHotwords.isVietnamese("123"))
        assertTrue(SherpaLabelHotwords.isVietnamese("Cửa hàng Play"), "nhãn chữ Việt có lẫn chữ Latin vẫn là nhãn Việt")
        assertTrue(SherpaLabelHotwords.isVietnamese("ĐIỆN THOẠI"))
        val nfd = Normalizer.normalize("Máy ảnh", Normalizer.Form.NFD)
        assertTrue(SherpaLabelHotwords.isVietnamese(nfd), "chữ tổ hợp NFD vẫn nhận ra")
        assertTrue("MỞ MÁY ẢNH" in lines(file(nfd)), "NFD được chuẩn hoá trước khi sinh dòng (không vỡ thành MÁ Y …)")
    }

    @Test
    fun `nhan lam rung mot dong cua tep truoc bi loai ca hai duong`() {
        // (1) dropPrefixes — lấy một dòng tĩnh `MỞ <X>` thật ⇒ nhãn `<X> đẹp` biến nó thành tiền tố ⇒ phải loại.
        val prefixed = baseLines.first { it.startsWith("MỞ ") && it.split(' ').size >= 3 && SherpaLabelHotwords.isVietnamese(it) }
        assertEquals(base, file(prefixed.removePrefix("MỞ ").lowercase() + " đẹp"), "«$prefixed» phải còn nguyên")
        // (2) dropAppNameLeading — một dòng tĩnh không mở đầu bằng động từ; nhãn = hai chữ đầu của nó (bẫy "Cài đặt").
        val verbHeads = SherpaSpokenWords.VERBS.values.flatten().map { it.substringBefore(' ').uppercase() }.toSet()
        val line = baseLines.first {
            it.split(' ').size >= 3 && it.substringBefore(' ') !in verbHeads && SherpaLabelHotwords.isVietnamese(it)
        }
        assertEquals(base, file(line.split(' ').take(2).joinToString(" ").lowercase()), "«$line» phải còn nguyên")
    }

    @Test
    fun `nhan noi dai cach goi app cua bang tinh khong bias - bay w10`() {
        val alias = SherpaPhraseHotwords.appNames().first { SherpaLabelHotwords.isVietnamese(it) }
        val longer = "$alias số"
        assertTrue(SherpaLabelHotwords.names(listOf(longer)).isEmpty(), "«$longer» nối dài cách gọi «$alias» của app khác")
        assertEquals(1, SherpaLabelHotwords.names(listOf("Máy ảnh")).size)
        assertEquals(base, file(longer))
    }

    @Test
    fun `nhan noi dai nhan khac khong bias, nhan ngan van bias`() {
        // [ĐO lượt sinh 2026-10-06] "Lịch âm" nối dài "Lịch" ⇒ loại (khuôn extendsAnotherApp giữa hai app).
        val l = lines(file("Lịch", "Lịch âm"))
        assertTrue("MỞ LỊCH" in l)
        assertFalse(l.any { it.endsWith("LỊCH ÂM") }, "«Lịch âm» không được bias")
    }

    @Test
    fun `ten da day di truoc - nhan khong lam mat dong nao cua ten da day`() {
        val taught = listOf(t("com.example.rec", "ghi âm cuộc gọi"), t("com.example.tok", "tóp tóp"))
        val withTaught = lines(SherpaBiasing.hotwordsFile(taught = taught))
        val both = lines(SherpaBiasing.hotwordsFile(taught = taught, labels = listOf("Ghi âm", "Máy ảnh", "Tóp tóp xịn")))
        assertTrue(both.containsAll(withTaught), "lớp nhãn không được làm rụng dòng của lớp tên đã dạy")
        assertTrue("MỞ GHI ÂM CUỘC GỌI" in both)
        assertTrue("MỞ MÁY ẢNH" in both)
        // Nhãn nối dài một tên đã dạy ⇒ `MỞ TÓP TÓP` sẽ thành tiền tố ⇒ nhãn bị loại (killedBy trên tệp CÓ tên đã dạy).
        assertFalse(both.any { it.endsWith("TÓP TÓP XỊN") })
    }

    @Test
    fun `bang khoa cua phien - chi nhan chu Viet, khong dang doc, khong ten da day`() {
        val labels = listOf("Máy ảnh" to "com.example.cam", "YouTube" to "com.example.yt", "Danh bạ" to "com.example.contacts")
        val alt = listOf("Camera" to "com.example.cam", "Ảnh" to "com.example.photos")   // nhãn phụ: en của máy vi, vi của app khác
        val taught = listOf(t("com.example.files", "quản lý tệp", TaughtSource.TYPED))
        val installed = setOf("com.example.cam", "com.example.yt", "com.example.contacts", "com.example.photos", "com.example.files")
        val keys = VoiceAppIndex.build(labels, installed, taught, alt).keys.keys
        // Tiền đề: bảng khoá THẬT có cả dạng đọc chữ Việt (YouTube ⇒ "du túp") lẫn tên gõ — không thì bài này rỗng nghĩa.
        // Dạng đọc bị loại CÓ ĐO (2026-10-07, VOICE-PHONETIC-LABEL-HOTWORD: −11 ý định ở tên đọc kiểu Anh, có ca mất mệnh đề ô).
        assertTrue("du túp" in keys && "quản lý tệp" in keys, "$keys")
        assertEquals(listOf("Máy ảnh", "Danh bạ", "Ảnh"), SherpaLabelHotwords.labels(keys, taught))
    }

    @Test
    fun `duong nhanh cua plan cho cung dong rung, cung thu tu nhu duong duyet`() {
        // 2.93 — `plan` tra chỉ mục đầu-dòng; `killedBy` công khai (TeachGuard) vẫn duyệt cả tệp. Hai đường phải trùng hệt.
        val names = listOf("cài đặt", "kính trước", "đèn", "ghế", "điều hòa", "sức khỏe", "camera lùi", "áp suất lốp", "máy ảnh")
            .map { t("com.example.${it.hashCode()}", it) }
        val p = SherpaTaughtHotwords.plan(base, names)
        assertTrue(p.excluded.size >= 5, "tiền đề: phải có tên bị loại để so (${p.excluded})")
        // So TẬP (thứ tự dòng đi theo thứ tự duyệt của tập truyền vào — plan dùng HashSet riêng của nó).
        p.excluded.forEach { e ->
            val linear = SherpaTaughtHotwords.killedBy(e.accented, baseLines)
            assertEquals(linear.size, e.killedLines.size, e.accented)
            assertEquals(linear.toSet(), e.killedLines.toSet(), e.accented)
        }
    }

    @Test
    fun `leadsWith tuong duong phep so cu tren du lieu that`() {
        val heads = SherpaPhraseHotwords.appNames().flatMap { SherpaHotwords.phrasesOf(it) }.toSet()
        val probes = baseLines + heads + heads.map { "$it X" } + heads.map { "MỞ $it" } + listOf("", " A", "A  B")
        probes.forEach { line ->
            assertEquals(heads.any { line == it || line.startsWith("$it ") }, SherpaHotwords.leadsWith(line, heads), "«$line»")
        }
    }

    @Test
    fun `tran MAX_LABELS va khu trung theo chu bo dau`() {
        // ⚠ Không dùng "Ứng dụng …": `MỞ ỨNG DỤNG` là dòng tĩnh ⇒ mọi nhãn ấy làm nó thành tiền tố ⇒ bị loại hết (đúng luật).
        val many = (0 until SherpaLabelHotwords.MAX_LABELS + 30).map { i -> "Tên thử ${('a' + i % 26)}${('a' + i / 26)}" }
        assertEquals(SherpaLabelHotwords.MAX_LABELS, SherpaLabelHotwords.labels(many, emptyList()).size)
        assertEquals(SherpaLabelHotwords.MAX_LABELS, SherpaLabelHotwords.names(many).size)
        assertEquals(listOf("Máy ảnh"), SherpaLabelHotwords.labels(listOf("Máy ảnh", "máy ảnh", "May anh"), emptyList()))
        val l = lines(SherpaBiasing.hotwordsFile(labels = many))
        assertTrue(l.containsAll(baseLines))
        assertTrue(l.size - baseLines.size in 1..3 * SherpaLabelHotwords.MAX_LABELS, "≤ 3 dòng (MỞ/ĐƯA/BẬT) mỗi nhãn")
    }
}
