package com.byd.clusternav.core

/**
 * RFC 4180 CSV field escaping, shared by the telemetry writers ([com.byd.clusternav.NavNotifLog],
 * [com.byd.clusternav.NavAccessLog]) so the raw GMaps notification / accessibility strings can carry
 * commas, quotes and newlines without corrupting the pullable CSV.
 *
 * Pure (no Android) so it lives in :core and is unit-tested off-car — the two :app writers both delegate
 * here instead of each re-implementing the naive `replace(',', ' ')` that [com.byd.clusternav.NavDistanceLog]
 * used (which silently mangled road names containing a comma and never handled embedded quotes/newlines).
 */
object CsvEscape {

    /**
     * Escape a single CSV field: if it contains a comma, double-quote, CR or LF, wrap the whole field in
     * double-quotes and double every embedded double-quote; otherwise return it unchanged. Null → empty.
     */
    fun field(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    /** Escape each field and join with commas into one CSV row (no trailing newline). */
    fun row(fields: List<String?>): String = fields.joinToString(",") { field(it) }
}
