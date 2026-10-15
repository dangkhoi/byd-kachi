package com.byd.clusternav.launcher

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bộ đọc JSON tối thiểu cho phần JSON của GLB do [Car3dGlb] ghi (test — `:core` không kéo thư viện JSON). Đối tượng ⇒ `Map`,
 * mảng ⇒ `List`, số ⇒ `Double`, chuỗi ⇒ `String`, `true/false/null`.
 */
object Car3dGlbJson {

    /** JSON của tệp [glb] (khối đầu, sau tiêu đề 12 B + đầu khối 8 B). */
    fun of(glb: ByteArray): Map<String, Any?> {
        val len = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN).getInt(12)
        @Suppress("UNCHECKED_CAST")
        return parse(String(glb, 20, len, Charsets.UTF_8).trim()) as Map<String, Any?>
    }

    fun parse(s: String): Any? = P(s).run { value().also { ws(); check(i == s.length) { "dư ký tự ở $i" } } }

    private class P(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            return when (val c = s[i]) {
                '{' -> obj(); '[' -> arr(); '"' -> str()
                't' -> { i += 4; true }; 'f' -> { i += 5; false }; 'n' -> { i += 4; null }
                else -> if (c == '-' || c.isDigit()) num() else error("ký tự lạ '$c' ở $i")
            }
        }
        fun obj(): Map<String, Any?> {
            i++; val m = LinkedHashMap<String, Any?>(); ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws(); val k = str(); ws(); check(s[i++] == ':'); m[k] = value(); ws()
                if (s[i++] == '}') return m
            }
        }
        fun arr(): List<Any?> {
            i++; val l = ArrayList<Any?>(); ws()
            if (s[i] == ']') { i++; return l }
            while (true) { l += value(); ws(); if (s[i++] == ']') return l }
        }
        fun str(): String {
            check(s[i++] == '"'); val b = StringBuilder()
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (val e = s[i++]) {
                        'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        'n' -> b.append('\n'); 't' -> b.append('\t')
                        else -> b.append(e)
                    }
                } else b.append(s[i++])
            }
            i++; return b.toString()
        }
        fun num(): Double {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(st, i).toDouble()
        }
    }
}
