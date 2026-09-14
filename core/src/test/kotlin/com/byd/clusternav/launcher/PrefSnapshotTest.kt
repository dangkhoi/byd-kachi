package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ S4 · R5 — CHỤP–ÁP: vòng tròn phải giữ **kiểu**, dữ liệu hỏng **không được ném** ══════════════════════════
 *
 * Cái đắt nhất bài này chặn: `SharedPreferences` phân biệt kiểu ở tầng ĐỌC, và `getBoolean` trên một giá trị ghi
 * bằng `putString` thì **ném** `ClassCastException`. Chỗ đọc là dịch vụ đang chạy trên xe, nên một lượt chụp–áp làm
 * mất kiểu không hỏng lúc đổi hồ sơ mà hỏng **trên đường**, với vết crash không trỏ về nguyên nhân.
 */
class PrefSnapshotTest {

    // ── 1 · vòng tròn giữ đúng kiểu ─────────────────────────────────────────────────────────────

    @Test
    fun `vong tron giu du sau kieu ma SharedPreferences biet`() {
        val src = mapOf<String, Any?>(
            "cast_enabled" to true,
            "badge_size_dp" to 120,
            "seen_at" to 1_725_000_000_000L,
            "badge_center_x" to 0.42f,
            "autostart_package" to "com.byd.androidauto",
            "voicekey_bindings" to linkedSetOf("a", "b", "c"),
            "da_xoa" to null,
        )
        val back = PrefSnapshot.decode(PrefSnapshot.encode(src))
        assertEquals(src.keys, back.keys)
        src.forEach { (k, v) ->
            assertEquals(v, back[k], "khoá $k")
            if (v != null) assertEquals(v.javaClass, back[k]!!.javaClass, "KIỂU của $k phải giữ nguyên")
        }
    }

    /**
     * ⚠ `Int` vs `Long` vs `Float` phải phân biệt được — đây đúng chỗ mà JSON (nếu dùng) sẽ nuốt mất, và cũng là
     * chỗ mà `getInt` trên một `Long` sẽ ném trên xe.
     */
    @Test
    fun `so nguyen KHONG bi doc nham thanh Long hay Float`() {
        val back = PrefSnapshot.decode(PrefSnapshot.encode(mapOf("a" to 1, "b" to 1L, "c" to 1f)))
        assertTrue(back["a"] is Int)
        assertTrue(back["b"] is Long)
        assertTrue(back["c"] is Float)
    }

    /** Khoá **có mặt với giá trị null** khác hẳn khoá **vắng mặt** — lượt áp cần biết để XOÁ khoá ở hồ sơ mới. */
    @Test
    fun `khoa co gia tri null van con trong ban do sau khi giai ma`() {
        val back = PrefSnapshot.decode(PrefSnapshot.encode(mapOf("x" to null)))
        assertTrue("x" in back, "mất mục null ⇒ lượt áp để nguyên giá trị của hồ sơ CŨ, im lặng")
        assertNull(back["x"])
    }

    @Test
    fun `ban do rong ra chuoi rong va nguoc lai`() {
        assertEquals("", PrefSnapshot.encode(emptyMap()))
        assertEquals(emptyMap<String, Any?>(), PrefSnapshot.decode(""))
        assertEquals(emptyMap<String, Any?>(), PrefSnapshot.decode(null))
    }

    @Test
    fun `chup hai lan cung noi dung ra CUNG mot chuoi`() {
        val a = PrefSnapshot.encode(mapOf("b" to 1, "a" to 2))
        val b = PrefSnapshot.encode(mapOf("a" to 2, "b" to 1))
        assertEquals(a, b, "thứ tự lặp của Map không được làm chuỗi đổi — không thì 'có gì đổi không' luôn là 'có'")
    }

    // ── 2 · ký tự phân tách: bài học mất-cảnh-im-lặng ────────────────────────────────────────────

    /**
     * ⚠⚠ Giá trị THẬT chứa ký tự ngăn cấu trúc là ca **có thật**, không phải giả định: `voicekey_bindings` lưu JSON
     * (`[{"k":24,"t":"..."}]`) và tên gói/nhãn nút do người dùng đặt có thể chứa bất cứ gì. Bản không escape sẽ làm
     * bản ghi ra sai số trường ⇒ **mất cấu hình trong im lặng** — đúng thứ đã đo được ở chuỗi lưu của cảnh.
     */
    @Test
    fun `gia tri chua ky tu ngan cau truc van ve nguyen ven`() {
        val src = mapOf<String, Any?>(
            "json" to """[{"k":24,"t":"lên|xuống"}]""",
            "xuong dong" to "dòng 1\ndòng 2\r\nhết",
            "dau phay" to "a,b,c",
            "gach cheo" to """C:\Users\x\n không phải xuống dòng""",
            "tap" to setOf("a,b", "c|d", "e\nf"),
        )
        assertEquals(src, PrefSnapshot.decode(PrefSnapshot.encode(src)))
    }

    /**
     * ⚠ Lỗi kinh điển của escape viết tay: `\\` không đi đầu lúc mã hoá ⇒ giá trị chứa đúng hai ký tự `\` + `n` đi
     * ra rồi về thành một dấu xuống dòng THẬT ⇒ vỡ cấu trúc bản ghi.
     */
    @Test
    fun `chuoi chua dung hai ky tu gach cheo va n KHONG bien thanh xuong dong`() {
        val src = mapOf<String, Any?>("k" to """a\nb""")
        val back = PrefSnapshot.decode(PrefSnapshot.encode(src))
        assertEquals("""a\nb""", back["k"])
        assertTrue('\n' !in (back["k"] as String))
    }

    @Test
    fun `khoa chua ky tu ngan cau truc cung ve nguyen ven`() {
        val src = mapOf<String, Any?>("khoá|lạ" to 1, "khoá\ndòng" to "x")
        assertEquals(src, PrefSnapshot.decode(PrefSnapshot.encode(src)))
    }

    @Test
    fun `tap rong khac tap mot phan tu rong`() {
        assertEquals(emptySet<String>(), PrefSnapshot.decode(PrefSnapshot.encode(mapOf("s" to emptySet<String>())))["s"])
        assertEquals(setOf(""), PrefSnapshot.decode(PrefSnapshot.encode(mapOf("s" to setOf("")))) ["s"])
    }

    // ── 3 · dữ liệu hỏng: tự chữa, KHÔNG ném (nguồn là đĩa, sửa tay được) ────────────────────────

    @Test
    fun `du lieu hong thi bo DONG do, giu cac dong con lai, khong nem`() {
        val raw = listOf(
            "rác",                       // thiếu trường
            "b|ok|true",
            "z|kieu_la|1",               // thẻ kiểu không ai biết
            "i|khong_phai_so|abc",       // số không đọc được
            "b|khong_phai_bool|có",
            "|khoa_khong_the|1",         // thẻ kiểu rỗng
            "s||chuỗi không khoá",       // khoá rỗng
            "s|con_lai|xong",
        ).joinToString("\n")
        val back = PrefSnapshot.decode(raw)
        assertEquals(mapOf<String, Any?>("ok" to true, "con_lai" to "xong"), back)
    }

    @Test
    fun `chuoi rac hoan toan KHONG nem`() {
        listOf("rác", "|||||", "\n\n", "b|", "   ", "\u0000").forEach {
            assertTrue(PrefSnapshot.decode(it).size <= 1, "vào: $it")
        }
    }

    /** Kiểu mà `SharedPreferences` không biết (vd `Double`) ⇒ **bỏ**, không ném: dữ liệu đó đã hỏng từ trước. */
    @Test
    fun `kieu la bi bo chu khong lam sap ca luot doi ho so`() {
        val back = PrefSnapshot.decode(PrefSnapshot.encode(mapOf("d" to 1.5, "ok" to 1)))
        assertEquals(mapOf<String, Any?>("ok" to 1), back)
    }
}
