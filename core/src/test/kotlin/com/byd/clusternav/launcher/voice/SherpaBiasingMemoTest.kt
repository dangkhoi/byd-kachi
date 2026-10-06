package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * 2.93 soát giọng Pass 2 (P3 b) — [SherpaBiasing.hotwordsFile] nhớ tệp của lượt trước theo bộ đầu vào.
 *
 * Khoá ba điều: (1) cùng bộ đầu vào (kể cả list KHÁC đối tượng nhưng bằng nhau) ⇒ trả lại đúng tệp đã dựng, không dựng
 * lại; (2) đổi BẤT KỲ vế nào (sổ địa chỉ · tên hồ sơ · tên đã dạy · nhãn) ⇒ dựng lại, ra đúng tệp của phép dựng không
 * nhớ; (3) người gọi đổi list SAU lượt gọi thì lượt sau vẫn thấy đầu vào mới (khoá là bản chép, không phải list gốc).
 * Ô nhớ là trạng thái TOÀN CỤC của tiến trình: bài dựa vào việc JUnit chạy tuần tự trong một JVM (dự án không bật chạy
 * song song của Jupiter) — bật nó thì lớp này cần `@Isolated`.
 */
class SherpaBiasingMemoTest {

    private fun t(pkg: String, a: String) = TaughtName(pkg, TaughtSource.SPEECH, a, "x")
    private fun fresh(places: List<String> = emptyList(), profiles: List<String> = emptyList(),
                      taught: List<TaughtName> = emptyList(), labels: List<String> = emptyList()) =
        SherpaBiasing.build(places, profiles, taught, labels)

    @Test
    fun `cung bo dau vao thi tra lai tep da dung, ke ca list khac doi tuong`() {
        val a = SherpaBiasing.hotwordsFile(listOf("Nhà"), listOf("Mặc định"), listOf(t("com.example.rec", "ghi âm")), listOf("Máy tính"))
        val b = SherpaBiasing.hotwordsFile(listOf("Nhà"), listOf("Mặc định"), listOf(t("com.example.rec", "ghi âm")), listOf("Máy tính"))
        assertSame(a, b, "lượt sau cùng đầu vào không được dựng lại")
        assertEquals(fresh(listOf("Nhà"), listOf("Mặc định"), listOf(t("com.example.rec", "ghi âm")), listOf("Máy tính")), a)
    }

    /**
     * Soát Pass 4 (P2): bản trước gọi bốn ca LIỀN NHAU ⇒ ca sau khác ca trước ở HAI vế, nên khoá bỏ sót vế đang xét vẫn trượt
     * nhớ — [ĐO đột biến] khoá bỏ tên hồ sơ hoặc tên đã dạy: cả lớp bản trước 3/3 xanh. Nay mỗi ca đứng ngay sau lượt TĨNH:
     * lượt ca chỉ khác đúng một vế ⇒ khoá bỏ sót vế ấy là trúng nhớ, trả tệp tĩnh, bài đỏ (đủ bốn vế đều đỏ).
     */
    @Test
    fun `doi bat ky ve nao thi dung lai dung tep moi`() {
        val base = fresh()
        val cases: List<Triple<String, () -> String, String>> = listOf(
            Triple("sổ địa chỉ", { SherpaBiasing.hotwordsFile(places = listOf("Công ty")) }, fresh(places = listOf("Công ty"))),
            Triple("tên hồ sơ", { SherpaBiasing.hotwordsFile(profiles = listOf("Test")) }, fresh(profiles = listOf("Test"))),
            Triple("tên đã dạy", { SherpaBiasing.hotwordsFile(taught = listOf(t("com.example.flix", "nep leag"))) },
                fresh(taught = listOf(t("com.example.flix", "nep leag")))),
            Triple("nhãn", { SherpaBiasing.hotwordsFile(labels = listOf("Ghi âm")) }, fresh(labels = listOf("Ghi âm"))),
        )
        cases.forEach { (what, memo, want) ->
            assertNotEquals(base, want, "$what: đầu vào này không đổi tệp ⇒ bài không phân biệt được trúng/trượt nhớ")
            assertEquals(base, SherpaBiasing.hotwordsFile(), "ô nhớ giữ tệp TĨNH ngay trước ca")
            assertEquals(want, memo(), "$what: tệp nhớ phải bằng phép dựng mới — khoá nhớ bỏ sót vế này?")
        }
        assertEquals(base, SherpaBiasing.hotwordsFile(), "quay về đầu vào rỗng ⇒ đúng tệp tĩnh")
    }

    @Test
    fun `nguoi goi doi list sau luot goi thi luot sau van thay dau vao moi`() {
        val labels = mutableListOf("Ghi âm")
        val first = SherpaBiasing.hotwordsFile(labels = labels)
        labels += "Máy tính"
        val second = SherpaBiasing.hotwordsFile(labels = labels)
        val want = fresh(labels = listOf("Ghi âm", "Máy tính"))
        // Soát Pass 4 (P3): nhãn thêm mà không đổi tệp thì bài xanh cả khi khoá giữ list GỐC ⇒ chốt điều kiện trước.
        assertNotEquals(fresh(labels = listOf("Ghi âm")), want, "nhãn thêm phải đổi tệp, không thì bài không gác được gì")
        assertEquals(fresh(labels = listOf("Ghi âm")), first)
        assertEquals(want, second, "khoá nhớ phải là bản chép, không phải list gốc")
    }
}
