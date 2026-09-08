package com.byd.clusternav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure off-car test for the RAW notif-capture CSV shape ([NavNotifRawLog.buildRow] + [NavNotifRawLog.HEADER]).
 * No Android / no I/O is exercised — [buildRow] only delegates to [com.byd.clusternav.core.CsvEscape] — so the
 * column contract is verified on the JVM. Guards two invariants a diagnostic drive depends on:
 *  1. the header column count equals the row field count (10) so no column silently drifts, and
 *  2. a field containing a comma AND a quote is RFC-4180 escaped (wrapped + internal quote doubled) so a
 *     quoted comma/ETA never breaks the column alignment of the pullable CSV.
 */
class NavNotifRawLogTest {

    @Test
    fun `header arity is 10 and equals buildRow field count`() {
        val headerCols = NavNotifRawLog.HEADER.split(",")
        assertEquals(10, headerCols.size, "HEADER must declare exactly 10 columns")

        // All fields comma/quote/newline-free → CsvEscape leaves them unquoted, so splitting on "," yields
        // exactly one token per column. This binds the row arity to the header arity.
        val row = NavNotifRawLog.buildRow(
            tMs = 1_724_000_000_000L,
            pkg = "com.waze",
            category = "navigation",
            isNav = true,
            hasDist = false,
            hasLargeIcon = true,
            title = "Turn right",
            text = "500 m",
            subText = "sub",
            bigText = "big",
        )
        val rowFields = row.split(",")
        assertEquals(10, rowFields.size, "buildRow must emit exactly 10 fields")
        assertEquals(headerCols.size, rowFields.size, "row field count must equal header column count")
    }

    @Test
    fun `header names and order match the wired columns exactly`() {
        assertEquals(
            "t_ms,pkg,category,isNav,hasDist,hasLargeIcon,title,text,subText,bigText",
            NavNotifRawLog.HEADER,
        )
    }

    @Test
    fun `escapes a field containing a comma and a quote so columns stay aligned`() {
        val row = NavNotifRawLog.buildRow(
            tMs = 42L,
            pkg = "com.google.android.apps.maps",
            category = "",
            isNav = false,
            hasDist = true,
            hasLargeIcon = false,
            title = "Turn right, then \"go\"",
            text = "",
            subText = "",
            bigText = "",
        )
        // RFC 4180: wrap the whole field in double-quotes and double every internal double-quote.
        //   Turn right, then "go"   →   "Turn right, then ""go"""
        assertTrue(
            row.contains("\"Turn right, then \"\"go\"\"\""),
            "comma+quote title must be quoted with internal quotes doubled — was: $row",
        )
        // The leading fixed columns stay unquoted + intact ahead of the escaped title cell.
        assertTrue(row.startsWith("42,com.google.android.apps.maps,,false,true,false,"))
    }

    @Test
    fun `strips embedded newlines so a record stays on one physical line`() {
        val row = NavNotifRawLog.buildRow(
            tMs = 7L,
            pkg = "com.chisadin.wazemod",
            category = "",
            isNav = false,
            hasDist = false,
            hasLargeIcon = false,
            title = "line1\nline2\r\nline3",
            text = "",
            subText = "",
            bigText = "",
        )
        assertTrue(!row.contains('\n') && !row.contains('\r'), "row must contain no CR/LF — was: $row")
        assertTrue(row.contains("line1 line2 line3"), "CR/LF must collapse to single spaces — was: $row")
    }
}
