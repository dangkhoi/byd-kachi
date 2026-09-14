package com.byd.clusternav.launcher.voice

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V1 pha NGHE · GÓI MÔ HÌNH — GHIM SỐ ĐO + CHẶN "ZIP SLIP" + ĐỌC BẢNG KÝ HIỆU ═════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R9 · R10.
 *
 * Ba việc ở đây, và cả ba đều là thứ **hỏng thì không ai thấy**: một hằng sha bị sửa nhầm thì gói giả đi lọt;
 * một đường dẫn `..` trong gói thì tệp của app bị ghi đè; một bảng ký hiệu đọc sai thì ngữ pháp rỗng và bộ nhận
 * dạng nghe ra rác. Không bài nào trong số này cần xe.
 */
class VoiceModelManifestTest {

    // ══ (1) Số đo đã ghim ════════════════════════════════════════════════════════════════════════════════

    /**
     * Hằng ghim phải khớp **đúng** phép đo 2026-09-14 (`curl -L … && shasum -a 256 && unzip -l`).
     *
     * Bài này trông như "kiểm tra hằng bằng hằng", nhưng nó canh đúng một việc: **không ai được sửa số này mà
     * không sửa cả spec**. sha256 là thứ duy nhất đứng giữa người dùng và một gói 32 MB tải từ Internet.
     */
    @Test
    fun `sha256 va kich thuoc dung nhu phep do 2026-09-14`() {
        assertEquals("efe5c8494212110471a79befc48c79da679e5b1fc52a4ffb500222ff86d622e5", VoiceModelManifest.ZIP_SHA256)
        assertEquals(33_656_337L, VoiceModelManifest.ZIP_BYTES)
        assertEquals(53_290_365L, VoiceModelManifest.UNPACKED_BYTES)
        assertEquals(64, VoiceModelManifest.ZIP_SHA256.length, "sha256 phải đủ 64 ký tự hex")
        assertTrue(VoiceModelManifest.ZIP_SHA256.all { it in "0123456789abcdef" }, "sha256 phải là hex thường")
    }

    /** Cả HAI phép kiểm, không bỏ cái nào — xem KDoc [VoiceModelManifest]. */
    @Test
    fun `chi nhan goi dung ca sha lan kich thuoc`() {
        val sha = VoiceModelManifest.ZIP_SHA256
        assertTrue(VoiceModelManifest.matches(sha, VoiceModelManifest.ZIP_BYTES))
        assertTrue(VoiceModelManifest.matches(sha.uppercase(), VoiceModelManifest.ZIP_BYTES), "hex hoa/thường như nhau")
        assertFalse(VoiceModelManifest.matches(sha, VoiceModelManifest.ZIP_BYTES - 1), "gói CỤT phải bị từ chối")
        assertFalse(VoiceModelManifest.matches("0".repeat(64), VoiceModelManifest.ZIP_BYTES), "gói SAI phải bị từ chối")
    }

    /** Nguồn tải: HTTPS, có đường lùi, và URL ghi TRẦN (đọc là thấy địa chỉ thật). */
    @Test
    fun `nguon tai deu la https va co duong lui`() {
        assertTrue(VoiceModelManifest.URLS.size >= 2, "phải có ít nhất một đường lùi")
        VoiceModelManifest.URLS.forEach { u ->
            assertTrue(u.startsWith("https://"), "`$u` không phải HTTPS — mô hình đi qua mạng 4G của xe")
            assertTrue(u.endsWith(".zip"), "`$u` phải trỏ thẳng tới gói nén")
        }
        assertEquals(VoiceModelManifest.URLS.size, VoiceModelManifest.URLS.toSet().size, "URL trùng nhau")
    }

    // ══ (2) "Zip slip" — tên mục trong gói là DỮ LIỆU CỦA NGƯỜI KHÁC ═════════════════════════════════════

    /**
     * CLAUDE.md §4.1: *"user input → file path → LUÔN kiểm, reject `..`"*.
     *
     * Mỗi dòng dưới đây là một hình dạng tấn công có thật trong họ lỗi "zip slip": leo thư mục, đường tuyệt đối,
     * dấu gạch ngược của zip trên Windows, và ổ đĩa `C:`. Nếu một dòng nào lọt, gói tải về ghi đè được
     * `shared_prefs` của chính app — tức đổi được cấu hình xe của người dùng từ xa.
     */
    @Test
    fun `tu choi moi duong dan leo ra ngoai thu muc mo hinh`() {
        listOf(
            "../../../../data/data/com.byd.launcher/shared_prefs/x.xml",
            "vosk-model-small-vn-0.4/../../evil.so",
            "/etc/passwd",
            "C:/Windows/system32/x.dll",
            "vosk-model-small-vn-0.4\\am\\final.mdl",
            "vosk-model-small-vn-0.4/./am/final.mdl",
            "",
            "   ",
        ).forEach { assertNull(VoiceModelManifest.safeEntryPath(it), "phải TỪ CHỐI `$it`") }
    }

    /** Mục hợp lệ: bỏ tiền tố thư mục gốc, giữ nguyên phần còn lại; thư mục (kết thúc `/`) không phải tệp. */
    @Test
    fun `nhan dung muc hop le va bo tien to thu muc goc`() {
        assertEquals("am/final.mdl", VoiceModelManifest.safeEntryPath("vosk-model-small-vn-0.4/am/final.mdl"))
        assertEquals("graph/phones/word_boundary.int",
            VoiceModelManifest.safeEntryPath("vosk-model-small-vn-0.4/graph/phones/word_boundary.int"))
        assertEquals("README", VoiceModelManifest.safeEntryPath("vosk-model-small-vn-0.4/README"))
        assertNull(VoiceModelManifest.safeEntryPath("vosk-model-small-vn-0.4/am/"), "thư mục không phải tệp")
    }

    /** Mọi tệp bắt buộc phải là một đường dẫn an toàn — nếu không thì chính bản kê của ta tự chặn mình. */
    @Test
    fun `moi tep bat buoc deu la duong dan an toan`() {
        assertTrue(VoiceModelManifest.GRAPH_FST in VoiceModelManifest.REQUIRED_FILES)
        (VoiceModelManifest.REQUIRED_FILES + VoiceModelManifest.WORDS_FILE).forEach {
            assertEquals(it, VoiceModelManifest.safeEntryPath("${VoiceModelManifest.ID}/$it"))
        }
    }

    // ══ (3) Đọc bảng ký hiệu nhúng trong FST ═════════════════════════════════════════════════════════════

    /**
     * Dựng một tệp FST **đúng định dạng OpenFst** rồi đọc lại — vòng khép kín, không cần tệp 25 MB.
     *
     * Cố ý có **cả hai** bảng (isymbols + osymbols) và chúng KHÁC nhau: đó là ca mà [VoskWordList] phải đọc qua
     * bảng đầu rồi mới lấy bảng sau. Đọc nhầm bảng thì ngữ pháp mang danh sách âm vị thay vì danh sách từ — và
     * không gì báo lỗi, bộ nhận dạng chỉ đơn giản là không nghe ra gì.
     */
    @Test
    fun `doc dung bang ky hieu DAU RA khi tep co ca hai bang`() {
        val fst = fakeFst(isymbols = listOf("<eps>", "a", "b"), osymbols = listOf("<eps>", "đèn", "bật", "[unk]"))
        assertEquals(
            listOf("<eps>", "đèn", "bật", "[unk]"),
            VoskWordList.readOutputSymbols(ByteArrayInputStream(fst)),
        )
    }

    /** Không có bảng đầu ra ⇒ danh sách RỖNG, không phải ngoại lệ: chỗ gọi tự lùi về `graph/words.txt`. */
    @Test
    fun `khong co bang dau ra thi tra danh sach rong`() {
        val fst = fakeFst(isymbols = listOf("<eps>", "a"), osymbols = null)
        assertTrue(VoskWordList.readOutputSymbols(ByteArrayInputStream(fst)).isEmpty())
    }

    /**
     * ═══ BÀI HỒI QUY — ĐỌC **BYTE THẬT** CỦA `graph/Gr.fst` ══════════════════════════════════════════════════
     *
     * ## Lỗi nó khoá lại (CLAUDE.md §10)
     * [ĐO] 2026-09-14, máy ảo: mô hình tải xong, sha256 khớp, giải nén đủ 13 tệp — rồi `VoiceModelStore` báo
     * **`bảng ký hiệu sai magic=0x7eb2fb74`**. Hằng `SYMBOL_TABLE_MAGIC` của bản đầu là một con số **đoán**
     * (`0x7EB2FDB4`, đổi chỗ hai nibble của magic FST).
     *
     * Điều đáng sợ không phải là đoán sai — mà là **cả ba bài kiểm ở trên vẫn XANH**: chúng dựng tệp FST giả
     * bằng chính cái hằng sai ấy, nên chúng chứng minh *"mã khớp với chính nó"* chứ không phải *"mã khớp với
     * OpenFst"*. Một vòng khép kín tự chứng minh mình đúng.
     *
     * ⇒ Bài này đọc **65.536 byte đầu cắt nguyên văn** từ `graph/Gr.fst` của gói đã băm sha256. Nó không đọc hết
     * bảng (19.529 mục không nằm trong 64 KB) nên kết thúc bằng *"tệp FST cụt"* — và **đó chính là điều phải
     * khẳng định**: cụt là do hết byte, KHÔNG phải do magic sai. Đoán sai một hằng ⇒ câu lỗi đổi ⇒ đỏ.
     */
    @Test
    fun `doc duoc phan dau cua tep Gr fst THAT`() {
        val bytes = javaClass.getResourceAsStream("/voice/vosk-model-small-vn-0.4.Gr.fst.head")
            ?.readBytes() ?: error("thiếu mẫu byte thật — xem KDoc bài này")
        assertEquals(65_536, bytes.size, "mẫu phải đúng 64 KiB đầu của Gr.fst")
        val err = assertThrows(IOException::class.java) {
            VoskWordList.readOutputSymbols(ByteArrayInputStream(bytes))
        }
        assertEquals(
            "tệp FST cụt giữa chừng", err.message,
            "phải đi hết được header + magic của bảng ký hiệu rồi mới hết byte. Câu lỗi khác (nhất là `bảng ký " +
                "hiệu sai magic=…`) nghĩa là một hằng định dạng đang SAI — đúng lỗi 2026-09-14",
        )
    }

    /** Tệp lạ / cụt ⇒ NÉM, không trả rỗng: rỗng nghĩa là "mô hình không có từ nào", một lời nói dối. */
    @Test
    fun `tep khong phai FST hoac cut giua chung thi nem`() {
        assertThrows(IOException::class.java) {
            VoskWordList.readOutputSymbols(ByteArrayInputStream("khong phai FST".toByteArray()))
        }
        val cut = fakeFst(isymbols = null, osymbols = listOf("<eps>", "đèn")).copyOfRange(0, 40)
        assertThrows(IOException::class.java) { VoskWordList.readOutputSymbols(ByteArrayInputStream(cut)) }
    }

    // ── Dựng tệp FST giả, đúng khuôn `fst/fst.h` + `fst/symbol-table.h` ──────────────────────────

    private fun fakeFst(isymbols: List<String>?, osymbols: List<String>?): ByteArray {
        val out = ByteArrayOutputStream()
        fun i32(v: Int) { for (k in 0 until 4) out.write((v ushr (8 * k)) and 0xFF) }
        fun i64(v: Long) { for (k in 0 until 8) out.write(((v ushr (8 * k)) and 0xFF).toInt()) }
        fun str(s: String) { val b = s.toByteArray(Charsets.UTF_8); i32(b.size); out.write(b) }
        fun table(syms: List<String>) {
            // Magic THẬT của OpenFst `SymbolTable` — đọc ra từ chính `Gr.fst` (xem bài `doc duoc phan dau…`).
            i32(0x7EB2FB74); str("test/words.txt"); i64(syms.size.toLong()); i64(syms.size.toLong())
            syms.forEachIndexed { k, s -> str(s); i64(k.toLong()) }
        }
        i32(0x7EB2FDD6); str("ngram"); str("standard"); i32(4)
        i32((if (isymbols != null) 1 else 0) or (if (osymbols != null) 2 else 0))
        i64(0L); i64(0L); i64(0L); i64(0L)          // properties · start · numstates · numarcs
        isymbols?.let { table(it) }
        osymbols?.let { table(it) }
        return out.toByteArray()
    }
}
