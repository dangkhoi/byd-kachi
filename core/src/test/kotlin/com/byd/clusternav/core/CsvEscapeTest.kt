package com.byd.clusternav.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * CSV escaping shared by the telemetry writers (NavNotifLog / NavAccessLog). A raw GMaps notification or
 * road name with a comma / quote / newline must not shift columns or break rows when the CSV is pulled off
 * the car and parsed — so we assert the RFC 4180 quoting rules exactly.
 */
class CsvEscapeTest {

    @Test
    fun `plain field is unchanged`() {
        assertEquals("Turn right", CsvEscape.field("Turn right"))
        assertEquals("250 m", CsvEscape.field("250 m"))
    }

    @Test
    fun `null and empty become empty`() {
        assertEquals("", CsvEscape.field(null))
        assertEquals("", CsvEscape.field(""))
    }

    @Test
    fun `comma forces quoting`() {
        assertEquals("\"Turn right, then left\"", CsvEscape.field("Turn right, then left"))
    }

    @Test
    fun `embedded double-quote is doubled and wrapped`() {
        // Nguyễn "Huệ" street  ->  "Nguyễn ""Huệ"" street"
        assertEquals("\"Nguyễn \"\"Huệ\"\" street\"", CsvEscape.field("Nguyễn \"Huệ\" street"))
    }

    @Test
    fun `newline and carriage return force quoting`() {
        assertEquals("\"a\nb\"", CsvEscape.field("a\nb"))
        assertEquals("\"a\rb\"", CsvEscape.field("a\rb"))
    }

    @Test
    fun `row escapes each field and joins with commas`() {
        val row = CsvEscape.row(listOf("123", "com.google.android.apps.maps", "In 500 m, turn right", null))
        assertEquals("123,com.google.android.apps.maps,\"In 500 m, turn right\",", row)
    }

    @Test
    fun `row with a quoted field containing comma round-trips column count`() {
        // 4 logical fields -> exactly 3 unquoted commas as separators (the comma inside field 2 is quoted).
        val row = CsvEscape.row(listOf("t", "a,b", "c", "d"))
        assertEquals("t,\"a,b\",c,d", row)
    }
}
