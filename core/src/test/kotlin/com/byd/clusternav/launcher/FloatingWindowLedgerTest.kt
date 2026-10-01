package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá DẤU BỀN "Kachi đã mở thành cửa sổ nổi" (PROFILE-SWITCH-SLOTS R-B4): trần 16 bỏ cũ nhất, chỉ nhận tên gói hợp lệ
 * (cùng mẫu [ShellAppLauncher.PKG]), gộp trùng, đọc hỏng/sửa tay không làm phình hay sai.
 */
class FloatingWindowLedgerTest {

    /** Kho giả: giữ đúng một chuỗi như SharedPreferences, đếm số lần ghi. */
    private class Mem(var value: String? = null, var ok: Boolean = true) : FloatingWindowLedger.Store {
        var writes = 0
        override fun read(): String? = value
        override fun write(value: String): Boolean { writes++; if (ok) this.value = value; return ok }
    }

    @Test
    fun `ghi theo thu tu, mo lai thi len cuoi, khong trung`() {
        val m = Mem(); val l = FloatingWindowLedger(m)
        assertTrue(l.markOpened("vn.vietmap.live"))
        assertTrue(l.markOpened("com.google.android.youtube"))
        assertTrue(l.markOpened("vn.vietmap.live"))
        assertEquals(listOf("com.google.android.youtube", "vn.vietmap.live"), l.opened())
        assertEquals("com.google.android.youtube,vn.vietmap.live", m.value)
    }

    @Test
    fun `tran 16 - vuot thi bo goi cu nhat`() {
        val l = FloatingWindowLedger(Mem())
        (1..20).forEach { l.markOpened("com.app$it") }
        val o = l.opened()
        assertEquals(FloatingWindowLedger.CAP, o.size)
        assertEquals("com.app5", o.first()); assertEquals("com.app20", o.last())
    }

    @Test
    fun `ten goi sai bi tu choi va khong ghi gi`() {
        val m = Mem(); val l = FloatingWindowLedger(m)
        listOf("", "com.foo;rm -rf /", "\$(id)", "a b", "com.foo,com.bar", ".com", "1abc").forEach {
            assertFalse(l.markOpened(it), "phải từ chối '$it'")
        }
        assertEquals(0, m.writes)
        assertEquals(emptyList<String>(), l.opened())
    }

    @Test
    fun `doc chuoi bi sua tay - bo ten sai, gop trung, toi da 16 moi nhat`() {
        val raw = (listOf("bad name", "com.a", "", "com.b", "com.a") + (1..20).map { "com.n$it" }).joinToString(",")
        val o = FloatingWindowLedger.decode(raw)
        assertEquals(16, o.size)
        assertEquals("com.n20", o.last())
        assertFalse(o.contains("bad name"))
        assertEquals(o.distinct(), o)
        assertEquals(emptyList<String>(), FloatingWindowLedger.decode(null))
        assertEquals(emptyList<String>(), FloatingWindowLedger.decode("  "))
    }

    @Test
    fun `quen - chi ghi khi co thay doi`() {
        val m = Mem("com.a,com.b,com.c"); val l = FloatingWindowLedger(m)
        l.forget(setOf("com.zzz")); assertEquals(0, m.writes)
        l.forget(emptySet()); assertEquals(0, m.writes)
        l.forget(setOf("com.b", "com.c")); assertEquals(1, m.writes)
        assertEquals(listOf("com.a"), l.opened())
    }

    @Test
    fun `luu ben hong thi bao false de cho goi ghi log`() {
        val l = FloatingWindowLedger(Mem(ok = false))
        assertFalse(l.markOpened("com.a"))
    }
}
