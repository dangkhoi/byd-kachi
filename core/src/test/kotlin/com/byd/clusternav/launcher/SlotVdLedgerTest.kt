package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [SlotVdLedger] — **bộ đếm màn ảo** của H2·1, khoá đúng cái [ĐO] 2026-09-14 trên `emulator-5554`:
 * `dumpsys display` có **4** thiết bị `kachi-slot-*` cho **2** ô App, vì màn Kachi đời trước (cờ `f` = đang kết
 * thúc) chưa tháo view nên màn ảo của nó chưa được nhả, trong khi màn Kachi mới đã tạo màn ảo của nó.
 *
 * Bất biến mà bài này canh: **số màn ảo sống ≤ số ô**, kể cả khi hai chủ thay nhau dựng lại ô nhiều lần.
 */
class SlotVdLedgerTest {

    private fun ledger() = SlotVdLedger<String>()

    @Test
    fun `chu moi nhan o thi man ao cu cua CHINH o do bi tra ve de giai phong`() {
        val l = ledger()
        assertTrue(l.adopt("wsA", 0, "vd-a0", "A0").isEmpty(), "ô trống ⇒ không có gì phải nhả")

        val stale = l.adopt("wsB", 0, "vd-b0", "B0")

        assertEquals(listOf("vd-a0"), stale.map { it.name }, "chủ mới nhận ô 0 ⇒ màn ảo của chủ CŨ phải được nhả")
        assertEquals(listOf("vd-b0"), l.live().map { it.name }, "ô 0 chỉ còn đúng một màn ảo sống")
    }

    @Test
    fun `hai man Kachi cung song voi 2 o thi van chi con 2 man ao — dung ca do 4-cho-2`() {
        val l = ledger()
        // Màn Kachi đời 1 dựng 2 ô.
        l.adopt("ws1", 0, "vd-1-0", "h10")
        l.adopt("ws1", 1, "vd-1-1", "h11")
        // Màn Kachi đời 2 dựng 2 ô trong khi đời 1 CHƯA tháo view (đúng ca đã đo).
        val s0 = l.adopt("ws2", 0, "vd-2-0", "h20")
        val s1 = l.adopt("ws2", 1, "vd-2-1", "h21")

        assertEquals(listOf("vd-1-0"), s0.map { it.name })
        assertEquals(listOf("vd-1-1"), s1.map { it.name })
        assertEquals(2, l.live().size, "2 ô ⇒ tối đa 2 màn ảo sống (trước H2 là 4)")
    }

    @Test
    fun `nha hai lan la khong lam gi — idempotent`() {
        val l = ledger()
        l.adopt("ws", 2, "vd", "h")
        assertEquals("vd", l.release("ws", 2)?.name)
        assertNull(l.release("ws", 2), "nhả lần hai ⇒ không trả handle nào (không double-release)")
        assertTrue(l.live().isEmpty())
    }

    @Test
    fun `dang ky lai CUNG mot man ao khong tu dem chinh no di giai phong`() {
        val l = ledger()
        l.adopt("ws", 1, "vd-x", "h")
        val again = l.adopt("ws", 1, "vd-x", "h")
        assertTrue(again.isEmpty(), "cùng tên VD ⇒ chỉ là đăng ký lại, KHÔNG được giải phóng chính nó")
        assertEquals(1, l.live().size)
    }

    @Test
    fun `releaseOwner chi nha o cua CHINH minh — khong cuop o ma chu khac da nhan`() {
        val l = ledger()
        l.adopt("cu", 0, "vd-cu-0", "a")
        l.adopt("cu", 1, "vd-cu-1", "b")
        l.adopt("moi", 0, "vd-moi-0", "c")   // chủ mới giành ô 0 (ô 0 của "cu" đã bị nhả ở đây)

        val freed = l.releaseOwner("cu")

        assertEquals(listOf("vd-cu-1"), freed.map { it.name }, "chỉ còn ô 1 là của chủ cũ")
        assertEquals(listOf("vd-moi-0"), l.live().map { it.name }, "ô 0 của chủ mới KHÔNG được đụng tới")
    }

    @Test
    fun `dung lai o nhieu lan khong lam so man ao tang`() {
        val l = ledger()
        repeat(20) { gen ->
            l.release("ws", 0)                       // WorkspaceView nhả trước khi tháo view
            l.adopt("ws", 0, "vd-$gen", "h$gen")
            assertEquals(1, l.live().size, "sau lượt dựng lại thứ $gen vẫn phải đúng 1 màn ảo")
        }
    }
}
