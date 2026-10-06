package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá DẤU BỀN "Kachi đã mở thành cửa sổ nổi" (PROFILE-SWITCH-SLOTS R-B4): trần 16 bỏ cũ nhất, chỉ nhận tên gói hợp lệ
 * (cùng mẫu [ShellAppLauncher.PKG]), gộp trùng, đọc hỏng/sửa tay không làm phình hay sai.
 *
 * 2.93 wave 2C · SLOT-DEAD-FREEFORM-REST (spec `kachi-293-wave2c.html` R2): lượt GHI `markOpened` (+ `add`) gỡ — 0 chỗ gọi sản
 * phẩm [ĐO grep 07/10]. Sổ chỉ còn ĐỌC (dấu do bản ≤ 2.92 để lại) + QUÊN; bốn bài từng dựng dấu bằng `markOpened` nay dựng
 * dấu bằng CHÍNH chuỗi bền bản cũ đã ghi (thứ thật sự nằm trên xe sau nâng cấp) — cùng các tính chất (thứ tự · trần 16 ·
 * tên sai · lưu bền hỏng) giữ nguyên ý; một bài mới khoá việc gỡ.
 */
class FloatingWindowLedgerTest {

    /** Kho giả: giữ đúng một chuỗi như SharedPreferences, đếm số lần ghi. */
    private class Mem(var value: String? = null, var ok: Boolean = true) : FloatingWindowLedger.Store {
        var writes = 0
        override fun read(): String? = value
        override fun write(value: String): Boolean { writes++; if (ok) this.value = value; return ok }
    }

    @Test
    fun `doc theo thu tu cu truoc moi sau, mo lai thi lan cuoi thang, khong trung`() {
        // Bản ≤ 2.92 mở VietMap, YouTube rồi VietMap lần nữa ⇒ chuỗi bền có thể mang cả hai lần (bản tay/cũ hơn).
        val m = Mem("vn.vietmap.live,com.google.android.youtube,vn.vietmap.live"); val l = FloatingWindowLedger(m)
        assertEquals(listOf("com.google.android.youtube", "vn.vietmap.live"), l.opened())
        assertEquals(0, m.writes, "ĐỌC không ghi gì")
    }

    @Test
    fun `tran 16 - doc chuoi dai thi giu 16 goi moi nhat`() {
        val l = FloatingWindowLedger(Mem((1..20).joinToString(",") { "com.app$it" }))
        val o = l.opened()
        assertEquals(FloatingWindowLedger.CAP, o.size)
        assertEquals("com.app5", o.first()); assertEquals("com.app20", o.last())
    }

    @Test
    fun `ten goi sai trong chuoi ben bi bo, khong vao ke hoach don`() {
        val bad = listOf("", "com.foo;rm -rf /", "\$(id)", "a b", ".com", "1abc")
        val m = Mem((bad + "com.ok").joinToString(",")); val l = FloatingWindowLedger(m)
        assertEquals(listOf("com.ok"), l.opened(), "chỉ tên gói hợp lệ đi tiếp tới lệnh `am stack remove`")
        assertEquals(0, m.writes)
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
    fun `luu ben hong khi quen thi dau cu con nguyen de luot don sau thu lai`() {
        val m = Mem("com.a,com.b", ok = false); val l = FloatingWindowLedger(m)
        l.forget(setOf("com.a"))
        assertEquals(1, m.writes, "đã thử ghi đúng một lần")
        assertEquals(listOf("com.a", "com.b"), l.opened(), "ghi hỏng ⇒ không mất dấu (lượt dọn sau còn thấy com.a)")
    }

    /** SLOT-DEAD-FREEFORM-REST — sổ không còn đường GHI mới; thêm lại = cần spec + chỗ gọi, không phải khối chết. */
    @Test
    fun `so chi con doc va quen - khong con duong ghi moi`() {
        val methods = FloatingWindowLedger::class.java.methods.map { it.name }.toSet()
        assertFalse("markOpened" in methods, "markOpened là khối chết đã gỡ (0 chỗ gọi sản phẩm)")
        assertTrue("opened" in methods && "forget" in methods, "hai việc còn lại của sổ: đọc để dọn + quên sau khi đóng")
        val companion = FloatingWindowLedger.Companion::class.java.methods.map { it.name }.toSet()
        assertFalse("add" in companion, "hàm phụ `add` chỉ phục vụ markOpened — gỡ cùng")
    }
}
