package com.byd.clusternav.offcar

/**
 * ═══ Hai bộ đọc JSON/schema thu nhỏ của [LegacyBaselineIdentityTest] — tách THUẦN (584 dòng → trần 500) ═══
 *
 * L6-debt 2026-09-27: thân lớp giữ nguyên byte, chỉ `private class` → `internal class` (cùng package, không tên mới). Chúng là
 * công cụ kiểm của bài canh schema 2020-12, không phải mã sản phẩm; bài canh vẫn nằm trọn trong `LegacyBaselineIdentityTest`
 * để cổng `GATE-X-O1` chạy đúng lớp. Tệp KHÔNG trong `SOURCE_SEAL_INPUT` (xem ghi chú ở `ExpansionTransportFenceFixtures`).
 */
internal class SchemaSubset(root: Map<*, *>) {
    private val defs = root["\$defs"] as Map<*, *>

    fun accepts(definition: String, value: Any?): Boolean = accepts(defs[definition] as Map<*, *>, value)

    @Suppress("UNCHECKED_CAST")
    fun accepts(schema: Map<*, *>, value: Any?): Boolean {
        val ref = schema["\$ref"] as? String
        if (ref != null && !accepts(defs[ref.removePrefix("#/\$defs/")] as Map<*, *>, value)) return false
        val all = schema["allOf"] as? List<Map<*, *>>
        if (all != null && !all.all { accepts(it, value) }) return false
        val one = schema["oneOf"] as? List<Map<*, *>>
        if (one != null && one.count { accepts(it, value) } != 1) return false
        val not = schema["not"] as? Map<*, *>
        if (not != null && accepts(not, value)) return false
        if (schema.containsKey("const") && schema["const"] != value) return false
        val enum = schema["enum"] as? List<*>
        if (enum != null && value !in enum) return false
        if (!typeMatches(schema["type"] as? String, value)) return false
        when (value) {
            is Map<*, *> -> {
                val required = schema["required"] as? List<*> ?: emptyList<Any?>()
                if (required.any { !value.containsKey(it) }) return false
                val properties = schema["properties"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
                if (properties.any { (key, child) -> value.containsKey(key) &&
                        !accepts(child as Map<*, *>, value[key]) }) return false
                if (schema["additionalProperties"] == false && value.keys.any { it !in properties }) return false
            }
            is List<*> -> {
                if (!within(value.size, schema["minItems"], schema["maxItems"])) return false
                if (schema["uniqueItems"] == true && value.distinct().size != value.size) return false
                (schema["items"] as? Map<*, *>)?.let { if (!value.all { item -> accepts(it, item) }) return false }
                (schema["prefixItems"] as? List<Map<*, *>>)?.forEachIndexed { index, item ->
                    if (index < value.size && !accepts(item, value[index])) return false
                }
                (schema["contains"] as? Map<*, *>)?.let { contains ->
                    val count = value.count { accepts(contains, it) }
                    if (!within(count, schema["minContains"] ?: 1L, schema["maxContains"])) return false
                }
            }
            is String -> {
                if (!within(value.length, schema["minLength"], schema["maxLength"])) return false
                val pattern = schema["pattern"] as? String
                if (pattern != null && !Regex(pattern).containsMatchIn(value)) return false
            }
            is Number -> {
                val number = value.toLong()
                if ((schema["minimum"] as? Number)?.toLong()?.let { number < it } == true) return false
                if ((schema["maximum"] as? Number)?.toLong()?.let { number > it } == true) return false
            }
        }
        return true
    }

    private fun within(value: Int, minimum: Any?, maximum: Any?): Boolean =
        (minimum as? Number)?.toInt()?.let { value >= it } != false &&
            (maximum as? Number)?.toInt()?.let { value <= it } != false

    private fun typeMatches(type: String?, value: Any?): Boolean = type == null || when (type) {
        "array" -> value is List<*>; "boolean" -> value is Boolean; "integer" -> value is Number
        "null" -> value == null; "object" -> value is Map<*, *>; "string" -> value is String; else -> false
    }
}

internal class MiniJsonReader(private val source: String) {
    private var index = 0

    fun read(): Any? {
        val value = value()
        require(index == source.length) { "trailing JSON at $index" }
        return value
    }

    private fun value(): Any? = when (source.getOrNull(index)) {
        '{' -> objectValue()
        '[' -> arrayValue()
        '"' -> stringValue()
        't' -> literal("true", true)
        'f' -> literal("false", false)
        'n' -> literal("null", null)
        '-', in '0'..'9' -> numberValue()
        else -> error("invalid JSON value at $index")
    }

    private fun objectValue(): Map<String, Any?> {
        expect('{')
        val result = linkedMapOf<String, Any?>()
        if (take('}')) return result
        while (true) {
            val key = stringValue()
            expect(':')
            require(!result.containsKey(key)) { "duplicate key $key" }
            result[key] = value()
            if (take('}')) return result
            expect(',')
        }
    }

    private fun arrayValue(): List<Any?> {
        expect('[')
        val result = mutableListOf<Any?>()
        if (take(']')) return result
        while (true) {
            result += value()
            if (take(']')) return result
            expect(',')
        }
    }

    private fun stringValue(): String {
        expect('"')
        return buildString {
            while (true) {
                val char = source.getOrNull(index++) ?: error("unterminated string")
                when (char) {
                    '"' -> return@buildString
                    '\\' -> append(escape())
                    else -> { require(char.code >= 0x20) { "unescaped control" }; append(char) }
                }
            }
        }
    }

    private fun escape(): Char = when (val escaped = source.getOrNull(index++) ?: error("unterminated escape")) {
        '"', '\\', '/' -> escaped
        'b' -> '\b'
        'f' -> '\u000c'
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'u' -> source.substring(index, index + 4).also { index += 4 }.toInt(16).toChar()
        else -> error("invalid escape $escaped")
    }

    private fun numberValue(): Long {
        val start = index
        if (take('-')) Unit
        require(source.getOrNull(index) in '0'..'9')
        if (source[index] == '0') index++ else while (source.getOrNull(index) in '0'..'9') index++
        require(source.getOrNull(index) !in listOf('.', 'e', 'E')) { "non-integer number" }
        return source.substring(start, index).toLong()
    }

    private fun <T> literal(text: String, result: T): T {
        require(source.startsWith(text, index))
        index += text.length
        return result
    }

    private fun expect(char: Char) { require(take(char)) { "expected $char at $index" } }
    private fun take(char: Char): Boolean = if (source.getOrNull(index) == char) { index++; true } else false
}
