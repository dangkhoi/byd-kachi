package com.byd.clusternav.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [AppLocationRegistry] — place/remove/query + bất biến MỘT-VỊ-TRÍ (đặt lên cụm xóa khỏi ô cũ) + [isCastable].
 * Thuần JVM.
 */
class AppLocationRegistryTest {

    private val main = DisplayOwnershipRegistry.MAIN_DISPLAY // 0
    private val cast = DisplayOwnershipRegistry.CAST_DISPLAY // 1

    @Test
    fun `place then locationOf returns the placed location`() {
        val r = AppLocationRegistry()
        r.place("com.foo", main, slot = 2)
        assertEquals(AppLocation("com.foo", main, 2), r.locationOf("com.foo"))
    }

    @Test
    fun `remove clears the location and is idempotent`() {
        val r = AppLocationRegistry()
        r.place("com.foo", main, 0)
        r.remove("com.foo")
        assertNull(r.locationOf("com.foo"))
        r.remove("com.foo") // idempotent — no throw
        assertNull(r.locationOf("com.foo"))
    }

    @Test
    fun `onDisplay lists apps on that display ordered by slot`() {
        val r = AppLocationRegistry()
        r.place("com.b", main, 1)
        r.place("com.a", main, 0)
        r.place("com.onCast", cast, null)
        assertEquals(listOf("com.a", "com.b"), r.onDisplay(main).map { it.pkg })
        assertEquals(listOf("com.onCast"), r.onDisplay(cast).map { it.pkg })
    }

    // ─────────── bất biến MỘT-VỊ-TRÍ ───────────

    @Test
    fun `placing a pkg on the cast display removes it from its launcher slot`() {
        val r = AppLocationRegistry()
        r.place("com.foo", main, slot = 3)
        assertEquals(3, r.locationOf("com.foo")?.slot)
        assertTrue(r.onDisplay(main).any { it.pkg == "com.foo" })

        // chiếu lên cụm → bất biến MỘT-VỊ-TRÍ DI CHUYỂN nó, KHÔNG nhân đôi
        r.place("com.foo", cast, slot = null)

        val loc = r.locationOf("com.foo")!!
        assertEquals(cast, loc.displayId)
        assertNull(loc.slot, "không có ô trên cụm")
        assertFalse(r.onDisplay(main).any { it.pkg == "com.foo" }, "không được kẹt lại ô cũ")
        assertEquals(1, r.all().count { it.pkg == "com.foo" }, "đúng MỘT vị trí mỗi pkg")
    }

    @Test
    fun `moving a pkg from cast back to a slot removes it from the cast display`() {
        val r = AppLocationRegistry()
        r.place("com.foo", cast, null)
        r.place("com.foo", main, slot = 1)
        assertFalse(r.onDisplay(cast).any { it.pkg == "com.foo" }, "phải rời cụm khi về ô")
        assertEquals(1, r.locationOf("com.foo")?.slot)
    }

    @Test
    fun `isCastable is true in a slot or unknown, false once on the cast display`() {
        val r = AppLocationRegistry()
        assertTrue(r.isCastable("com.unknown"), "app chưa đặt vẫn có thể cast")
        r.place("com.foo", main, slot = 0)
        assertTrue(r.isCastable("com.foo"), "app trong ô launcher là castable")
        r.place("com.foo", cast, null)
        assertFalse(r.isCastable("com.foo"), "app đã trên cụm thì không cast lại")
    }

    @Test
    fun `a custom cast display id is honored by isCastable`() {
        val r = AppLocationRegistry(castDisplayId = 5)
        r.place("com.foo", 5, null)
        assertFalse(r.isCastable("com.foo"))
        r.place("com.foo", 0, slot = 0)
        assertTrue(r.isCastable("com.foo"))
    }
}
