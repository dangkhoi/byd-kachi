package com.byd.clusternav.launcher

import java.io.File
import java.io.IOException

/**
 * ═══ PROFILE-IO-0930 · IO-R1/R2 — TỆP hồ sơ trong thư mục của app: đặt tên · ghi mới · liệt kê · đọc một tệp ═══════════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §12.4.1. Chỉ `java.io` (không `android.*`) ⇒ test off-device
 * bằng thư mục tạm; `ProfileIoStore` (`:app`) chỉ đổi `Context` thành thư mục rồi gọi vào đây.
 *
 * ## Hai lỗi của #4 (2026-09-24) mà lớp này đóng
 *  • **Ghi đè không hỏi**: tên tệp = tên hồ sơ đã khử ⇒ xuất lại cùng hồ sơ, hoặc hai hồ sơ "A/B" và "A_B", ra CÙNG một
 *    tệp và bản sau xoá bản trước. Nay tên mang dấu thời gian + kiểu, và chỗ được giữ bằng [File.createNewFile] (nguyên
 *    tử: `false` khi tệp đã có) ⇒ trùng thì thêm `-2`, `-3`… — không có đường nào mở một tệp đã có để ghi.
 *  • **Nhập mọi tệp**: nay [list] cho người dùng CHỌN, [read] đọc đúng MỘT tệp.
 *
 * Lỗi I/O: bắt [IOException]/[SecurityException] cụ thể và trả `null`/rỗng — chỗ gọi (`:app`) ghi log và nói với người
 * dùng. Không bắt `Exception` trần (CLAUDE.md §4.1).
 */
object ProfileFiles {

    const val EXT = ".kachi"

    /** Cắt tên hồ sơ trong tên tệp — tên tệp Android tối đa 255 BYTE, một chữ tiếng Việt có dấu tới 3 byte UTF-8. */
    const val MAX_BASE_CHARS = 48

    /** Trần số lần thử hậu tố khi trùng tên — vượt ⇒ bỏ cuộc (có gì đó sai với thư mục, không lặp mãi). */
    const val MAX_ATTEMPTS = 99

    /** Trần kích thước tệp đọc vào — tệp hồ sơ thật cỡ vài KB; một tệp lạ cỡ trăm MB không được nạp vào RAM. */
    const val MAX_READ_BYTES = 1L shl 20

    /** Số byte đầu đọc để lấy header cho danh sách. */
    private const val HEADER_PEEK_BYTES = 4096

    private val UNSAFE = Regex("[^\\p{L}\\p{M}\\p{Nd} _-]")
    private val SPACES = Regex("\\s+")
    private val STAMP_JUNK = Regex("[^0-9-]")

    /** Một tệp trong thư mục: tên tệp · lúc sửa (ms) · header nếu đọc được. */
    data class Entry(val fileName: String, val modifiedMs: Long, val header: ProfileTransfer.Header?)

    /** Phần tên hồ sơ trong tên tệp: chỉ chữ/dấu/số/khoảng trắng/`_`/`-` (không `.`, không `/`), rỗng ⇒ `profile`. */
    fun baseName(profileName: String): String =
        ProfileTransfer.clip(profileName.replace(UNSAFE, "_").replace(SPACES, " ").trim(), MAX_BASE_CHARS).trim()
            .ifEmpty { "profile" }

    /** `<tên>-<dấu thời gian>[-share][-n].kachi`; [attempt] 1 = không hậu tố số. */
    fun fileName(profileName: String, stamp: String, kind: ProfileTransfer.Kind, attempt: Int): String {
        val cleanStamp = stamp.replace(STAMP_JUNK, "").take(16)
        return buildString {
            append(baseName(profileName))
            if (cleanStamp.isNotEmpty()) append('-').append(cleanStamp)
            if (kind == ProfileTransfer.Kind.SHARE) append("-share")
            if (attempt > 1) append('-').append(attempt)
            append(EXT)
        }
    }

    /**
     * Tên tệp AN TOÀN để mở trong thư mục hồ sơ, hoặc `null`. Luật chung CLAUDE.md §4.1: lấy `File(x).name` và đòi nó
     * bằng đúng chuỗi vào (không có thành phần thư mục), từ chối rỗng / `..` / dấu chấm đầu, và đòi đuôi [EXT].
     */
    fun safeName(raw: String): String? {
        val name = File(raw).name
        if (name != raw || name.isBlank() || name.startsWith(".") || ".." in name) return null
        if ('/' in name || '\\' in name || !name.endsWith(EXT) || name.length <= EXT.length) return null
        return name
    }

    /** Ghi [data] vào một tệp MỚI trong [dir]; trả tệp đã ghi, `null` khi hỏng. Không bao giờ ghi đè. */
    fun writeNew(dir: File, profileName: String, stamp: String, kind: ProfileTransfer.Kind, data: String): File? {
        for (attempt in 1..MAX_ATTEMPTS) {
            // Phòng thủ hai lớp: [fileName] đã khử ký tự, nhưng tên đi qua ĐÚNG cổng của lượt đọc ([safeName]) trước khi
            // chạm đĩa — một bản sửa [baseName] sau này lỡ để lọt `/`/`..` thì hỏng ở đây, không ghi ra ngoài thư mục.
            val name = safeName(fileName(profileName, stamp, kind, attempt)) ?: return null
            val f = File(dir, name)
            val created = try {
                f.createNewFile()
            } catch (e: IOException) {
                return null
            } catch (e: SecurityException) {
                return null
            }
            if (!created) continue
            return try {
                f.writeText(data)
                f
            } catch (e: IOException) {
                f.delete()   // tệp dở không được nằm lại để lượt nhập sau nạp nửa hồ sơ
                null
            }
        }
        return null
    }

    /**
     * Trần số tệp trong hộp chọn. Senior review PROFILE-IO-0930 lượt 1 [P3]: liệt kê chạy trên luồng UI và mỗi tệp tốn
     * một lần mở để đọc header; mỗi lần xuất nay là một tệp MỚI nên thư mục chỉ có lớn dần — không trần thì số lần mở tệp
     * trên luồng UI không có đáy. Tệp cũ hơn vẫn nằm nguyên trên đĩa, chỉ không hiện.
     */
    const val MAX_LIST_ENTRIES = 200

    /**
     * Tệp hồ sơ trong [dir], MỚI NHẤT TRƯỚC (cùng lúc ⇒ theo tên, ngược), tối đa [MAX_LIST_ENTRIES]. Không đọc được thư
     * mục ⇒ rỗng. Sắp và cắt TRƯỚC khi mở tệp: chỉ tệp được hiện mới tốn một lần đọc header.
     */
    fun list(dir: File): List<Entry> {
        val files = try {
            dir.listFiles()
        } catch (e: SecurityException) {
            null
        } ?: return emptyList()
        return newestFirst(
            files.filter { it.isFile && safeName(it.name) != null }.map { Entry(it.name, it.lastModified(), null) },
        ).take(MAX_LIST_ENTRIES).map { it.copy(header = peekHeader(File(dir, it.fileName))) }
    }

    fun newestFirst(entries: List<Entry>): List<Entry> =
        entries.sortedWith(compareByDescending<Entry> { it.modifiedMs }.thenByDescending { it.fileName })

    /** Nội dung của ĐÚNG một tệp [fileName] trong [dir]; `null` khi tên không an toàn, không có, quá lớn, đọc hỏng. */
    fun read(dir: File, fileName: String): String? {
        val name = safeName(fileName) ?: return null
        val f = File(dir, name)
        return try {
            if (!f.isFile || f.length() > MAX_READ_BYTES) null else readCapped(f, MAX_READ_BYTES)
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Đọc [f] nhưng KHÔNG quá [max] byte; vượt ⇒ `null` (không nạp phần còn lại).
     *
     * Senior review PROFILE-IO-0930 Pass 4 [P3]: cổng `length()` ở [read] là một lần `stat` RỜI lượt đọc — thư mục nằm ở
     * bộ nhớ ngoài (USB/MTP/app khác ghi được), tệp lớn lên giữa hai bước thì `readText()` nạp trọn vào RAM, đúng thứ
     * [MAX_READ_BYTES] hứa chặn. Trần nay nằm ở chính vòng đọc. Giải mã UTF-8 như `readText()` (ký tự hỏng ⇒ thay thế).
     */
    internal fun readCapped(f: File, max: Long): String? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        f.inputStream().use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > max) return null
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private fun peekHeader(f: File): ProfileTransfer.Header? = try {
        val buf = ByteArray(HEADER_PEEK_BYTES)
        val n = f.inputStream().use { it.read(buf) }
        if (n <= 0) null else ProfileTransfer.parseHeader(String(buf, 0, n, Charsets.UTF_8).substringBefore('\n'))
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }
}
