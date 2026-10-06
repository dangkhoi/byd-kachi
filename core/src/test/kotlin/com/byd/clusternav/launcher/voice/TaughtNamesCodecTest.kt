package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.91 VOICE-APP-NAMES · C1 — định dạng lưu `voice_app_names` (spec §4.5). Khoá: vòng tròn mã hoá, giải mã KHÔNG ném,
 * phiên bản lạ ⇒ CHỈ ĐỌC (không ghi đè dữ liệu của bản Kachi mới hơn), khử ký tự ngăn, trần 4/app (3 giọng + 1 gõ) ·
 * 120/hồ sơ, cột chuẩn hoá luôn TÍNH LẠI. Gói `com.example.*` là bộ thử, không phải app thật.
 */
class TaughtNamesCodecTest {

    private fun n(pkg: String, accented: String, src: TaughtSource = TaughtSource.SPEECH, label: String = "App") =
        TaughtName(pkg, src, accented, label)

    @Test
    fun `vong tron ma hoa giu nguyen moi cot ke ca dau tieng Viet`() {
        val list = listOf(
            n("com.example.flix", "nep leag", label = "Netflix"),
            n("com.example.flix", "nét lích", label = "Netflix"),
            n("com.example.files", "quản lý tệp", TaughtSource.TYPED, "Files"),
        )
        val raw = TaughtNamesCodec.encode(list)
        assertTrue(raw.startsWith("kachi-names\tv1\n"), raw)
        assertTrue(raw.contains("com.example.flix\tS\tnét lích\tnet lich\tNetflix\n"), raw)
        val d = TaughtNamesCodec.decode(raw)
        assertEquals(list, d.names)
        assertFalse(d.readOnly)
        assertEquals(null, d.problem)
    }

    @Test
    fun `rong hoac null la danh sach rong co the ghi`() {
        listOf(null, "", "  \n").forEach { raw ->
            val d = TaughtNamesCodec.decode(raw)
            assertTrue(d.names.isEmpty())
            assertFalse(d.readOnly, "«$raw» không phải dữ liệu lạ — được ghi")
        }
    }

    @Test
    fun `header hoac phien ban la thi chi doc khong ghi de`() {
        val newer = TaughtNamesCodec.decode("kachi-names\tv2\ncom.example.flix\tS\tnep leag\tnep leag\tNetflix\n")
        assertTrue(newer.names.isEmpty())
        assertTrue(newer.readOnly, "dữ liệu của bản Kachi MỚI hơn không được bản này ghi đè")
        assertNotNull(newer.problem)
        val garbage = TaughtNamesCodec.decode("rac rac\nfoo")
        assertTrue(garbage.readOnly)
        // Lý do chỉ nói độ dài — không nhắc lại chữ người dùng (R-nf3).
        assertFalse(garbage.problem!!.contains("rac"))
    }

    @Test
    fun `dong hong bi bo dong con lai giu`() {
        val raw = "kachi-names\tv1\n" +
            "com.example.flix\tS\tnep leag\tnep leag\tNetflix\n" +
            "khong-phai-goi\tS\tabc def\tabc def\tX\n" +   // gói hỏng
            "com.example.a\tQ\tabc def\tabc def\tX\n" +     // nguồn lạ
            "com.example.b\tS\t\t\tX\n" +                   // tên rỗng
            "com.example.c\tS\tthiếu cột\n" +               // thiếu cột
            "com.example.flix\tS\tnep leag\tnep leag\tNetflix\n"   // trùng
        val d = TaughtNamesCodec.decode(raw)
        assertEquals(listOf("nep leag"), d.names.map { it.accented })
        assertNotNull(d.problem)
        assertFalse(d.problem!!.contains("abc"), "lý do không in chữ người dùng: ${d.problem}")
    }

    @Test
    fun `tep nhap mang ten sai hinh dang thi bo dong do`() {
        // Tệp hồ sơ NHẬP (FULL) là dữ liệu ngoài: tên quá dài / quá nhiều từ không vào từ vựng + hotword (cùng trần TeachSample).
        val raw = "kachi-names\tv1\n" +
            "com.example.flix\tS\tnep leag\tnep leag\tNetflix\n" +
            "com.example.a\tS\t${"x".repeat(TeachSample.MAX_CHARS + 1)}\tx\tX\n" +
            "com.example.b\tT\tmột hai ba bốn năm\tmot hai ba bon nam\tY\n" +
            "com.example.c\tS\tab\tab\tZ\n" +            // 2 chữ cái: dưới sàn tuyệt đối
            "com.example.d\tS\tcơm\tcom\tW\n"            // 3 chữ cái: đã qua lần dạy (≥ 2 lượt) ⇒ giữ
        val d = TaughtNamesCodec.decode(raw)
        assertEquals(listOf("nep leag", "cơm"), d.names.map { it.accented })
        assertNotNull(d.problem)
        assertFalse(d.problem!!.contains("xxxx"), "lý do không in chữ người dùng: ${d.problem}")
    }

    @Test
    fun `cot chuan hoa luon tinh lai tu dang co dau`() {
        val d = TaughtNamesCodec.decode("kachi-names\tv1\ncom.example.flix\tS\tNét Lích\tSAI\tNetflix\n")
        assertEquals("nét lích", d.names.single().accented)
        assertEquals("net lich", d.names.single().norm)
        assertNotNull(d.problem)
    }

    @Test
    fun `khu ky tu ngan cot va xuong dong o cua vao`() {
        val r = TaughtNames.add(emptyList(), n("com.example.flix", "nep\tleag\nxx", label = "Net\nflix"))
        val list = (r as TaughtNames.Added).names
        val raw = TaughtNamesCodec.encode(list)
        assertEquals(2, raw.trimEnd('\n').split('\n').size, "một tên = đúng một dòng: $raw")
        assertEquals(list, TaughtNamesCodec.decode(raw).names)
    }

    @Test
    fun `tran bon ten moi app ba giong mot go va tran ho so`() {
        var list = emptyList<TaughtName>()
        listOf("aaaa", "bbbb", "cccc").forEach { list = (TaughtNames.add(list, n("com.example.a", it)) as TaughtNames.Added).names }
        assertEquals(TaughtNames.Why.FULL_SOURCE, (TaughtNames.add(list, n("com.example.a", "dddd")) as TaughtNames.Refused).why)
        list = (TaughtNames.add(list, n("com.example.a", "eeee", TaughtSource.TYPED)) as TaughtNames.Added).names
        assertEquals(TaughtNames.Why.FULL_APP, (TaughtNames.add(list, n("com.example.a", "ffff", TaughtSource.TYPED)) as TaughtNames.Refused).why)
        assertEquals(TaughtNames.Why.DUPLICATE, (TaughtNames.add(list, n("com.example.a", "AAAA")) as TaughtNames.Refused).why)

        var big = emptyList<TaughtName>()
        repeat(TaughtNamesCodec.MAX_PER_PROFILE) { i -> big = (TaughtNames.add(big, n("com.example.p$i", "ten so $i")) as TaughtNames.Added).names }
        assertEquals(TaughtNames.Why.FULL_PROFILE, (TaughtNames.add(big, n("com.example.z", "ten moi")) as TaughtNames.Refused).why)
        // Giải mã cũng giữ đúng trần: không tự xoá để lấy chỗ, chỉ bỏ phần vượt.
        val over = TaughtNamesCodec.encode(big) + "com.example.z\tS\tten moi\tten moi\tZ\n"
        assertEquals(TaughtNamesCodec.MAX_PER_PROFILE, TaughtNamesCodec.decode(over).names.size)
    }

    @Test
    fun `xoa mot ten va xoa ca app`() {
        val list = listOf(n("com.example.a", "aaaa"), n("com.example.a", "bbbb"), n("com.example.b", "cccc"))
        assertEquals(listOf("bbbb", "cccc"), TaughtNames.remove(list, "com.example.a", "aaaa").map { it.accented })
        assertEquals(listOf("cccc"), TaughtNames.removeApp(list, "com.example.a").map { it.accented })
    }
}
