package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * ═══ PROFILE-IO-0930 · IO-R1/R2 — tệp hồ sơ: tên không trùng · không ghi đè · liệt kê · đọc đúng MỘT tệp an toàn ═══
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §12.4.1. Chạy trên thư mục tạm thật (`java.io`), không giả.
 * Khoá hai lỗi của #4 (2026-09-24): xuất lại cùng hồ sơ / hai tên "A/B" và "A_B" XOÁ tệp trước; nhập đọc MỌI tệp.
 */
class ProfileFilesTest {

    @TempDir
    lateinit var dir: File

    private val full = ProfileTransfer.Kind.FULL
    private val share = ProfileTransfer.Kind.SHARE
    private fun data(name: String, kind: ProfileTransfer.Kind = full, tail: String = "s|preset|p1") =
        ProfileTransfer.encodeHeader(name, kind) + "\n" + tail

    @Test
    fun `ten tep co dau thoi gian, kieu, hau to so`() {
        assertEquals("Mặc định-20260930-1405.kachi", ProfileFiles.fileName("Mặc định", "20260930-1405", full, 1))
        assertEquals("Mặc định-20260930-1405-share.kachi", ProfileFiles.fileName("Mặc định", "20260930-1405", share, 1))
        assertEquals("A_B-20260930-1405-share-3.kachi", ProfileFiles.fileName("A/B", "20260930-1405", share, 3))
        assertEquals("x-2026.kachi", ProfileFiles.fileName("x", "../2026", full, 1), "dấu thời gian chỉ còn số và '-'")
    }

    @Test
    fun `khu ky tu an toan — khong con dau cham, gach cheo, xuong dong`() {
        listOf("../../etc/passwd", "..", ".hidden", "a\\b", "a\nb", "C:\\x", "tab\there", "/abs").forEach { raw ->
            val base = ProfileFiles.baseName(raw)
            assertTrue(base.none { it == '.' || it == '/' || it == '\\' || it < ' ' }, "`$raw` → `$base`")
            assertTrue(base.isNotBlank())
            assertNotNull(ProfileFiles.safeName(ProfileFiles.fileName(raw, "20260930-1405", full, 1)), raw)
        }
        assertEquals("profile", ProfileFiles.baseName("   "))
        assertEquals("profile", ProfileFiles.baseName(""))
        assertEquals(ProfileFiles.MAX_BASE_CHARS, ProfileFiles.baseName("x".repeat(300)).length)
        // Senior review lượt 1 [P3]: cắt ở giữa một chữ ngoài BMP (𠀀 U+20000, \p{L}) không được để lại nửa cặp surrogate.
        val cut = ProfileFiles.baseName("a".repeat(ProfileFiles.MAX_BASE_CHARS - 1) + "𠀀")
        assertEquals("a".repeat(ProfileFiles.MAX_BASE_CHARS - 1), cut)
    }

    /** Senior review lượt 1 [P3]: liệt kê chạy trên luồng UI — chỉ [ProfileFiles.MAX_LIST_ENTRIES] tệp MỚI NHẤT được mở. */
    @Test
    fun `liet ke co tran, giu tep moi nhat`() {
        val n = ProfileFiles.MAX_LIST_ENTRIES + 3
        (1..n).forEach { i ->
            File(dir, "p$i.kachi").apply { writeText(data("P$i")); setLastModified(1_000_000L + i * 1000L) }
        }
        val list = ProfileFiles.list(dir)
        assertEquals(ProfileFiles.MAX_LIST_ENTRIES, list.size)
        assertEquals("p$n.kachi", list.first().fileName)
        assertEquals("P$n", list.first().header?.name, "tệp được hiện vẫn đọc header")
        assertTrue(list.none { it.fileName == "p1.kachi" || it.fileName == "p3.kachi" }, "ba tệp cũ nhất bị cắt")
    }

    @Test
    fun `xuat lai cung ho so trong cung phut khong ghi de`() {
        val a = ProfileFiles.writeNew(dir, "A", "20260930-1405", full, data("A", tail = "s|preset|first"))!!
        val b = ProfileFiles.writeNew(dir, "A", "20260930-1405", full, data("A", tail = "s|preset|second"))!!
        assertNotEquals(a.name, b.name)
        assertEquals("A-20260930-1405-2.kachi", b.name)
        assertTrue(a.readText().endsWith("first"), "tệp đầu bị ghi đè")
        assertTrue(b.readText().endsWith("second"))
    }

    @Test
    fun `A gach B va A gach duoi B khong de nhau`() {
        val a = ProfileFiles.writeNew(dir, "A/B", "20260930-1405", full, data("A/B"))!!
        val b = ProfileFiles.writeNew(dir, "A_B", "20260930-1405", full, data("A_B"))!!
        assertNotEquals(a, b)
        assertEquals("A/B", ProfileTransfer.parseHeader(a.readLines().first())?.name)
        assertEquals("A_B", ProfileTransfer.parseHeader(b.readLines().first())?.name)
    }

    @Test
    fun `tep co san cung ten khong bi cham`() {
        val squat = File(dir, "A-20260930-1405.kachi").apply { writeText("OLD") }
        val f = ProfileFiles.writeNew(dir, "A", "20260930-1405", full, data("A"))!!
        assertEquals("OLD", squat.readText())
        assertNotEquals(squat, f)
    }

    @Test
    fun `het cho thu thi bo cuoc, khong ghi de`() {
        (1..ProfileFiles.MAX_ATTEMPTS).forEach {
            File(dir, ProfileFiles.fileName("A", "s", full, it)).writeText("OLD$it")
        }
        assertNull(ProfileFiles.writeNew(dir, "A", "s", full, data("A")))
        assertEquals("OLD1", File(dir, ProfileFiles.fileName("A", "s", full, 1)).readText())
    }

    @Test
    fun `liet ke moi nhat truoc, doc header, bo tep la`() {
        val old = ProfileFiles.writeNew(dir, "Cũ", "20260101-0000", full, data("Cũ"))!!.apply { setLastModified(1_000_000L) }
        val mid = File(dir, "legacy.kachi").apply { writeText("kachi-profile\tv1\tĐời cũ\ns|preset|p"); setLastModified(2_000_000L) }
        val new = ProfileFiles.writeNew(dir, "Mới", "20260930-1405", share, data("Mới", share))!!.apply { setLastModified(3_000_000L) }
        File(dir, "junk.kachi").apply { writeText("not a profile"); setLastModified(1_500_000L) }
        File(dir, ".hidden.kachi").writeText(data("H"))
        File(dir, "note.txt").writeText(data("T"))
        File(dir, "sub.kachi").mkdir()

        val list = ProfileFiles.list(dir)
        assertEquals(listOf(new.name, mid.name, "junk.kachi", old.name), list.map { it.fileName })
        assertEquals(ProfileTransfer.Header("Mới", share), list[0].header)
        assertEquals(ProfileTransfer.Header("Đời cũ", full), list[1].header, "tệp v1 cũ ba trường = đầy đủ")
        assertNull(list[2].header, "tệp hỏng vẫn liệt kê (người dùng thấy nó) nhưng không có header")
    }

    @Test
    fun `thu muc khong co thi danh sach rong`() {
        assertEquals(emptyList<ProfileFiles.Entry>(), ProfileFiles.list(File(dir, "missing")))
    }

    @Test
    fun `doc dung MOT tep an toan trong thu muc`() {
        // Thư mục hồ sơ là thư mục CON của @TempDir: tệp "bên ngoài" nằm trong @TempDir (được dọn), không rơi vào
        // thư mục tạm chung của JVM (lượt 2 [P3] — bản trước ghi `outside.kachi` vào `dir.parentFile`, không ai dọn).
        val profiles = File(dir, "profiles").apply { mkdir() }
        val f = ProfileFiles.writeNew(profiles, "A", "20260930-1405", full, data("A"))!!
        ProfileFiles.writeNew(profiles, "B", "20260930-1405", full, data("B"))
        assertEquals(data("A"), ProfileFiles.read(profiles, f.name))
        File(dir, "outside.kachi").writeText(data("X"))
        File(profiles, "sub").mkdir()
        File(profiles, "sub/in.kachi").writeText(data("X"))
        File(profiles, ".h.kachi").writeText(data("X"))
        listOf(
            "../outside.kachi", "sub/in.kachi", ".h.kachi", "", "..", "A.txt", ".kachi", "missing.kachi",
            File(profiles, f.name).absolutePath, "a..b.kachi",
        ).forEach { assertNull(ProfileFiles.read(profiles, it), "`$it` không được đọc") }
    }

    @Test
    fun `tep qua lon khong nap vao RAM`() {
        val big = File(dir, "big.kachi")
        big.outputStream().use { o -> o.write(ByteArray((ProfileFiles.MAX_READ_BYTES + 1).toInt())) }
        assertNull(ProfileFiles.read(dir, "big.kachi"))
    }

    /**
     * Pass 4 [P3] — trần nằm ở CHÍNH vòng đọc, không chỉ ở lần `stat` trước đó (tệp ở bộ nhớ ngoài lớn lên giữa hai bước
     * thì `readText()` nạp trọn). Gọi thẳng [ProfileFiles.readCapped] với trần nhỏ để vượt trần mà `length()` không chặn.
     */
    @Test
    fun `vong doc tu chan tran, khong dua vao lan stat truoc do`() {
        val f = File(dir, "grow.kachi").apply { writeText("Mặc định\n0123456789") }
        val size = f.length()
        assertEquals("Mặc định\n0123456789", ProfileFiles.readCapped(f, size), "đúng trần ⇒ đọc đủ, giải mã UTF-8")
        assertNull(ProfileFiles.readCapped(f, size - 1), "vượt trần một byte ⇒ không nạp")
        val big = File(dir, "big2.kachi").apply { outputStream().use { o -> o.write(ByteArray(3 * 8192 + 5)) } }
        assertNull(ProfileFiles.readCapped(big, 2L * 8192), "trần giữa các khối đọc vẫn chặn")
    }
}
