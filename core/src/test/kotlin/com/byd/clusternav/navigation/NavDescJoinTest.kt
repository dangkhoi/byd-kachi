package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the VietMap/Waze content-description join that fills the `text` telemetry column when `event.text` is
 * empty. The Android node-tree walk lives in :app (no unit test); this pins the pure flatten/dedupe/join so
 * a multi-line description stays one CSV cell and the distinct-in-order contract can't drift.
 */
class NavDescJoinTest {

    @Test
    fun `joins distinct descriptions in order with a pipe separator`() {
        assertEquals("A | B | C", NavDescJoin.join(listOf("A", "B", "C")))
    }

    @Test
    fun `flattens newlines inside a description to a single space`() {
        assertEquals("0 km/h 60", NavDescJoin.join(listOf("0\nkm/h\n60")))
    }

    @Test
    fun `collapses CRLF and repeated newlines to one space`() {
        assertEquals("a b", NavDescJoin.join(listOf("a\r\n\nb")))
    }

    @Test
    fun `dedupes keeping the first occurrence order`() {
        assertEquals("A | B", NavDescJoin.join(listOf("A", "B", "A")))
    }

    @Test
    fun `dedupes entries that differ only by newline vs space after flattening`() {
        // Flatten happens BEFORE dedupe, so "A\nB" and "A B" collapse to the same cell.
        assertEquals("A B", NavDescJoin.join(listOf("A\nB", "A B")))
    }

    @Test
    fun `drops blank and whitespace-only entries`() {
        assertEquals("X", NavDescJoin.join(listOf("", "   ", "\n", "X")))
    }

    @Test
    fun `empty input yields empty string`() {
        assertEquals("", NavDescJoin.join(emptyList()))
    }

    @Test
    fun `real vietmap subtree descriptions join into one nav telemetry string`() {
        val descs = listOf(
            "Sau đó (122m)\n50m Trần Trọng Kim",
            "0\nkm/h\n60",
            "18:21\n197m\nNhà (Park 3...)",
        )
        assertEquals(
            "Sau đó (122m) 50m Trần Trọng Kim | 0 km/h 60 | 18:21 197m Nhà (Park 3...)",
            NavDescJoin.join(descs),
        )
    }
}
