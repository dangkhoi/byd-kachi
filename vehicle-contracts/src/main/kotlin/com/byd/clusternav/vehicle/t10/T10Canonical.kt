package com.byd.clusternav.vehicle.t10

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Collections
import java.util.LinkedHashMap

/** Values accepted by the compact, NFC-normalized T10 canonical JSON codec. */
sealed interface T10JsonValue

internal data object T10JsonNull : T10JsonValue
internal data class T10JsonBoolean(val value: Boolean) : T10JsonValue
internal data class T10JsonInteger(val value: Long) : T10JsonValue
internal data class T10JsonText(val value: String) : T10JsonValue
internal class T10JsonArray(values: List<T10JsonValue>) : T10JsonValue {
    val values: List<T10JsonValue> = immutableList(values)
    override fun equals(other: Any?) = other is T10JsonArray && values == other.values
    override fun hashCode() = values.hashCode()
}
internal class T10JsonObject(fields: Map<String, T10JsonValue>) : T10JsonValue {
    val fields: Map<String, T10JsonValue> = Collections.unmodifiableMap(LinkedHashMap(fields))
    override fun equals(other: Any?) = other is T10JsonObject && fields == other.fields
    override fun hashCode() = fields.hashCode()
}

/** Lowercase SHA-256 value. Construction is fail-closed. */
class Sha256 private constructor(val value: String) : Comparable<Sha256> {
    override fun compareTo(other: Sha256) = value.compareTo(other.value)
    override fun equals(other: Any?) = other is Sha256 && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = value

    companion object {
        private val PATTERN = Regex("^[0-9a-f]{64}$")
        fun parse(value: String): Sha256 {
            require(PATTERN.matches(value)) { "SHA-256 must be exactly 64 lowercase hexadecimal characters" }
            return Sha256(value)
        }
    }
}

/** Strict JSON codec: malformed UTF-8 and every non-canonical byte representation are rejected. */
object T10Canonical {
    private const val HEX = "0123456789abcdef"

    fun parse(bytes: ByteArray): T10JsonValue {
        require(bytes.size < 3 || !(bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte())) {
            "UTF-8 BOM is forbidden"
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (error: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("invalid UTF-8", error)
        }
        val value = JsonParser(text).parseDocument()
        val canonical = render(value)
        require(bytes.contentEquals(canonical)) { "JSON bytes are not the canonical T10 representation" }
        return value
    }

    fun render(value: T10JsonValue): ByteArray = renderText(value).toByteArray(StandardCharsets.UTF_8)
    fun renderText(value: T10JsonValue): String = buildString { appendCanonical(value) }
    fun sha256(bytes: ByteArray): Sha256 = Sha256.parse(
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            buildString(2) {
                append(HEX[(byte.toInt() ushr 4) and 15])
                append(HEX[byte.toInt() and 15])
            }
        },
    )
    fun sha256(value: T10JsonValue): Sha256 = sha256(render(value))

    fun nullValue(): T10JsonValue = T10JsonNull
    fun boolean(value: Boolean): T10JsonValue = T10JsonBoolean(value)
    fun integer(value: Long): T10JsonValue = T10JsonInteger(value)
    fun text(value: String): T10JsonValue = T10JsonText(normalize(value))
    fun array(values: Iterable<T10JsonValue>): T10JsonValue = T10JsonArray(values.toList())
    fun obj(vararg fields: Pair<String, T10JsonValue>): T10JsonValue {
        val map = LinkedHashMap<String, T10JsonValue>()
        fields.forEach { (rawKey, value) ->
            val key = normalize(rawKey)
            require(key.all { it.code in 0x20..0x7e }) { "canonical object keys must be ASCII" }
            require(!map.containsKey(key)) { "duplicate canonical object key: $key" }
            map[key] = value
        }
        return T10JsonObject(map)
    }

    private fun StringBuilder.appendCanonical(value: T10JsonValue) {
        when (value) {
            T10JsonNull -> append("null")
            is T10JsonBoolean -> append(if (value.value) "true" else "false")
            is T10JsonInteger -> append(value.value)
            is T10JsonText -> appendQuoted(value.value)
            is T10JsonArray -> value.values.joinTo(this, prefix = "[", postfix = "]", separator = ",") {
                renderText(it)
            }
            is T10JsonObject -> value.fields.entries.sortedBy { it.key }.joinTo(
                this, prefix = "{", postfix = "}", separator = ",",
            ) { (key, fieldValue) ->
                buildString { appendQuoted(key); append(':'); appendCanonical(fieldValue) }
            }
        }
    }

    private fun StringBuilder.appendQuoted(raw: String) {
        val value = normalize(raw)
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) {
                    append("\\u00")
                    append(HEX[(char.code ushr 4) and 15])
                    append(HEX[char.code and 15])
                } else append(char)
            }
        }
        append('"')
    }

    internal fun normalize(value: String): String {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char.isHighSurrogate() -> {
                    require(index + 1 < value.length && value[index + 1].isLowSurrogate()) { "lone UTF-16 surrogate" }
                    index += 2
                }
                char.isLowSurrogate() -> throw IllegalArgumentException("lone UTF-16 surrogate")
                else -> index++
            }
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC)
    }
}

private class JsonParser(private val text: String) {
    private var index = 0

    fun parseDocument(): T10JsonValue {
        skipWhitespace()
        require(index < text.length) { "empty JSON document" }
        val value = parseValue()
        skipWhitespace()
        require(index == text.length) { "trailing JSON content" }
        return value
    }

    private fun parseValue(): T10JsonValue {
        skipWhitespace()
        require(index < text.length) { "unexpected end of JSON" }
        return when (text[index]) {
            'n' -> literal("null", T10JsonNull)
            't' -> literal("true", T10JsonBoolean(true))
            'f' -> literal("false", T10JsonBoolean(false))
            '"' -> T10JsonText(parseString())
            '[' -> parseArray()
            '{' -> parseObject()
            '-', in '0'..'9' -> parseInteger()
            else -> throw IllegalArgumentException("unexpected JSON token at offset $index")
        }
    }

    private fun parseArray(): T10JsonValue {
        expect('[')
        val values = ArrayList<T10JsonValue>()
        skipWhitespace()
        if (consume(']')) return T10JsonArray(values)
        while (true) {
            values += parseValue()
            skipWhitespace()
            if (consume(']')) return T10JsonArray(values)
            expect(',')
        }
    }

    private fun parseObject(): T10JsonValue {
        expect('{')
        val fields = LinkedHashMap<String, T10JsonValue>()
        skipWhitespace()
        if (consume('}')) return T10JsonObject(fields)
        while (true) {
            skipWhitespace()
            require(index < text.length && text[index] == '"') { "object key must be a string" }
            val key = parseString()
            require(key.all { it.code in 0x20..0x7e }) { "object keys must be ASCII" }
            require(!fields.containsKey(key)) { "duplicate object key: $key" }
            skipWhitespace()
            expect(':')
            fields[key] = parseValue()
            skipWhitespace()
            if (consume('}')) return T10JsonObject(fields)
            expect(',')
        }
    }

    private fun parseInteger(): T10JsonValue {
        val start = index
        if (text[index] == '-') index++
        require(index < text.length) { "incomplete integer" }
        if (text[index] == '0') {
            index++
            require(index == text.length || text[index] !in '0'..'9') { "leading zero is forbidden" }
        } else {
            require(text[index] in '1'..'9') { "invalid integer" }
            while (index < text.length && text[index] in '0'..'9') index++
        }
        if (index < text.length && text[index] in charArrayOf('.', 'e', 'E')) {
            throw IllegalArgumentException("floating point and exponent numbers are forbidden")
        }
        val token = text.substring(start, index)
        val value = token.toLongOrNull() ?: throw IllegalArgumentException("integer is outside signed 64-bit range")
        return T10JsonInteger(value)
    }

    private fun parseString(): String {
        expect('"')
        val result = StringBuilder()
        while (index < text.length) {
            val char = text[index++]
            when {
                char == '"' -> return T10Canonical.normalize(result.toString())
                char == '\\' -> appendEscape(result)
                char.code < 0x20 -> throw IllegalArgumentException("unescaped control character")
                char.isHighSurrogate() -> {
                    require(index < text.length && text[index].isLowSurrogate()) { "lone UTF-16 surrogate" }
                    result.append(char).append(text[index++])
                }
                char.isLowSurrogate() -> throw IllegalArgumentException("lone UTF-16 surrogate")
                else -> result.append(char)
            }
        }
        throw IllegalArgumentException("unterminated JSON string")
    }

    private fun appendEscape(result: StringBuilder) {
        require(index < text.length) { "unterminated JSON escape" }
        when (val escaped = text[index++]) {
            '"', '\\', '/' -> result.append(escaped)
            'b' -> result.append('\b')
            'f' -> result.append('\u000c')
            'n' -> result.append('\n')
            'r' -> result.append('\r')
            't' -> result.append('\t')
            'u' -> {
                val first = readHexCodeUnit()
                when {
                    first.isHighSurrogate() -> {
                        require(index + 1 < text.length && text[index] == '\\' && text[index + 1] == 'u') {
                            "high surrogate must be followed by an escaped low surrogate"
                        }
                        index += 2
                        val second = readHexCodeUnit()
                        require(second.isLowSurrogate()) { "invalid UTF-16 surrogate pair" }
                        result.append(first).append(second)
                    }
                    first.isLowSurrogate() -> throw IllegalArgumentException("lone low surrogate")
                    else -> result.append(first)
                }
            }
            else -> throw IllegalArgumentException("invalid JSON escape")
        }
    }

    private fun readHexCodeUnit(): Char {
        require(index + 4 <= text.length) { "short Unicode escape" }
        val token = text.substring(index, index + 4)
        require(token.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "invalid Unicode escape" }
        index += 4
        return token.toInt(16).toChar()
    }

    private fun <T : T10JsonValue> literal(token: String, value: T): T {
        require(text.startsWith(token, index)) { "invalid JSON literal" }
        index += token.length
        return value
    }

    private fun skipWhitespace() {
        while (index < text.length && text[index] in charArrayOf(' ', '\t', '\r', '\n')) index++
    }

    private fun consume(expected: Char): Boolean {
        if (index < text.length && text[index] == expected) { index++; return true }
        return false
    }

    private fun expect(expected: Char) {
        require(consume(expected)) { "expected '$expected' at offset $index" }
    }
}

/** A normalized path that cannot escape or ambiguously address a repository root. */
class RepoRelativePath private constructor(val value: String) {
    private val segments = value.split('/')

    fun resolveExistingNoFollow(repositoryRoot: Path): Path {
        val candidate = checkedCandidate(repositoryRoot, requireExistingParent = true)
        require(Files.isRegularFile(candidate, NOFOLLOW_LINKS)) { "repository input must be a regular non-symlink file" }
        return candidate
    }

    fun readBytesNoFollow(repositoryRoot: Path): ByteArray =
        Files.newInputStream(resolveExistingNoFollow(repositoryRoot), StandardOpenOption.READ, NOFOLLOW_LINKS).use { it.readBytes() }

    fun resolveForCreateNoFollow(repositoryRoot: Path): Path = checkedCandidate(repositoryRoot, requireExistingParent = true)

    private fun checkedCandidate(repositoryRoot: Path, requireExistingParent: Boolean): Path {
        val root = repositoryRoot.toAbsolutePath().normalize()
        require(Files.isDirectory(root, NOFOLLOW_LINKS) && !Files.isSymbolicLink(root)) { "repository root must be a real directory" }
        var current = root
        segments.forEachIndexed { position, segment ->
            current = current.resolve(segment)
            val exists = Files.exists(current, NOFOLLOW_LINKS)
            if (exists) require(!Files.isSymbolicLink(current)) { "repository path must not traverse a symlink" }
            if (requireExistingParent && position < segments.lastIndex) {
                require(exists && Files.isDirectory(current, NOFOLLOW_LINKS)) { "repository path parent does not exist" }
            }
        }
        require(current.normalize().startsWith(root)) { "repository path escaped its root" }
        return current
    }

    override fun equals(other: Any?) = other is RepoRelativePath && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = value

    companion object {
        private val SAFE = Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]*(/[A-Za-z0-9_][A-Za-z0-9_.-]*)*$")
        fun parse(value: String): RepoRelativePath {
            require(value.isNotEmpty() && value.length <= 240) { "repository path length is invalid" }
            require(SAFE.matches(value)) { "repository path contains an unsafe or ambiguous segment" }
            require('\\' !in value && "//" !in value && !value.startsWith('/') && !value.endsWith('/')) {
                "repository path has ambiguous separators"
            }
            require(value.split('/').none { it == "." || it == ".." || it.startsWith('.') }) {
                "dot-prefixed and traversal segments are forbidden"
            }
            return RepoRelativePath(value)
        }
    }
}

internal fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

internal fun T10JsonValue.objectFields(label: String): Map<String, T10JsonValue> =
    (this as? T10JsonObject)?.fields ?: throw IllegalArgumentException("$label must be an object")

internal fun T10JsonValue.arrayValues(label: String): List<T10JsonValue> =
    (this as? T10JsonArray)?.values ?: throw IllegalArgumentException("$label must be an array")

internal fun T10JsonValue.stringValue(label: String): String =
    (this as? T10JsonText)?.value ?: throw IllegalArgumentException("$label must be a string")

internal fun T10JsonValue.longValue(label: String): Long =
    (this as? T10JsonInteger)?.value ?: throw IllegalArgumentException("$label must be an integer")

internal fun T10JsonValue.booleanValue(label: String): Boolean =
    (this as? T10JsonBoolean)?.value ?: throw IllegalArgumentException("$label must be a boolean")

internal fun T10JsonValue.isNull() = this === T10JsonNull

internal fun Map<String, T10JsonValue>.requireExactKeys(label: String, vararg keys: String) {
    val expected = keys.toSet()
    require(this.keys == expected) {
        val unknown = this.keys - expected
        val missing = expected - this.keys
        "$label keys mismatch; unknown=$unknown missing=$missing"
    }
}
