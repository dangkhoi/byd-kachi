package com.byd.clusternav.launcher.voice

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

/**
 * ═══ V1 pha NGHE · ĐỌC TỪ ĐIỂN CỦA MÔ HÌNH RA KHỎI `graph/Gr.fst` ═════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R10**. Thuần Kotlin (`:core`, cấm `android.*`; `java.io` là JVM,
 * cùng lệ với `java.text.Normalizer` mà [VoiceLexicon] đang dùng) ⇒ kiểm off-car bằng chính tệp thật.
 *
 * ## Vì sao phải có lớp này — một sự thật ĐÃ ĐO, không phải phòng xa
 * [ĐO] 2026-09-14, giải nén `vosk-model-small-vn-0.4.zip` (33.656.337 byte, sha256 `efe5c849…`): gói có **14 tệp
 * và KHÔNG có `graph/words.txt`**. Đó là ca mà `vosk-api/src/model.cc` đã lường trước — sau khi nạp `HCLr.fst` +
 * `Gr.fst`, nó thử `word_syms_ = g_fst_->OutputSymbols()` TRƯỚC rồi mới đọc tệp `words.txt` nếu bảng còn rỗng:
 *
 * ```cpp
 * } else if (g_fst_ && g_fst_->OutputSymbols()) {   // model.cc:306-307
 *     word_syms_ = g_fst_->OutputSymbols();
 * }
 * ```
 *
 * [ĐO] `Gr.fst` của mô hình này **có nhúng** bảng ký hiệu (`flags = 3` ⇒ có cả isymbols lẫn osymbols; bảng tên
 * `exp/chain/tdnn/lgraph/words.txt`, **19.529 mục**). Nên mô hình chạy được, chỉ là từ điển nằm TRONG tệp nhị
 * phân chứ không nằm cạnh nó.
 *
 * ## Vì sao Kachi cần từ điển đó, chứ không phó mặc cho Vosk
 * [ĐO] `vosk-api/src/recognizer.cc:340-347` — khi dựng ngữ pháp, từ nào không có trong từ điển bị **bỏ lặng lẽ**:
 *
 * ```cpp
 * int32 id = model_->word_syms_->Find(token);
 * if (id == kNoSymbol) {
 *     KALDI_WARN << "Ignoring word missing in vocabulary: '" << token << "'";
 * } else { sentence.push_back(id); }
 * ```
 *
 * Tức cụm *"bật đèn đọc"* mà mô hình không có chữ *"đọc"* sẽ lặng lẽ thành *"bật đèn"* — một mục ngữ pháp **khác
 * hẳn** cái ta khai, và nó sẽ khớp với những câu ta không định cho khớp. Đọc được từ điển thì Kachi **tự đo** cụm
 * nào dùng được, bỏ hẳn cụm không dùng được, và nói ra con số (xem [VoicePhrases]). Đúng CLAUDE.md §7: khác biệt
 * giữa các mô hình lộ ra qua **đo đạc**, không qua một danh sách viết cứng.
 *
 * ## Định dạng — OpenFst `FstHeader` + `SymbolTable`, đọc y như `fst/fst.h` / `fst/symbol-table.h`
 * Mọi số là **little-endian**; chuỗi là `int32 độ dài` + bấy nhiêu byte UTF-8.
 *
 * ```
 * int32   magic = 0x7EB2FDD6          // kFileVersion magic của FstHeader
 * string  fsttype        ("ngram")
 * string  arctype        ("standard")
 * int32   version        int32 flags  // bit0 = có isymbols, bit1 = có osymbols
 * uint64  properties · int64 start · int64 numstates · int64 numarcs
 * [SymbolTable isymbols]  nếu flags & 1
 * [SymbolTable osymbols]  nếu flags & 2
 * ```
 * và mỗi `SymbolTable` là `int32 magic · string name · int64 available_key · int64 size · size × (string, int64)`.
 * [ĐO] trên `Gr.fst` của mô hình VN: `magic = 0x7EB2FDD6 · fsttype "ngram" · arctype "standard" · flags 3` rồi
 * hai bảng ký hiệu, mỗi bảng mở đầu bằng `0x7EB2FB74`.
 *
 * ⚠ Chỉ đọc **phần đầu** tệp: bảng ký hiệu nằm ngay sau header, nên với `Gr.fst` 25 MB ta chỉ đụng ~300 KB đầu
 * rồi đóng luồng. Đây là lý do hàm nhận [InputStream] chứ không nhận `File` — nó **không bao giờ** phải đọc hết.
 */
object VoskWordList {

    /** `kFileVersion` magic của `FstHeader` (OpenFst `fst/fst.h`). */
    private const val FST_MAGIC = 0x7EB2FDD6

    /**
     * Magic của `SymbolTable` (OpenFst `fst/symbol-table.h`, `kSymbolTableMagicNumber = 2125658996`).
     *
     * ⚠⚠ **[ĐO] trên tệp thật 2026-09-14, sau khi bản đầu ghi SAI số này.** Bản đầu đoán `0x7EB2FDB4` (đổi chỗ
     * hai nibble của magic FST ở trên) và **mọi bài kiểm vẫn XANH**, vì bài kiểm tự dựng tệp FST giả bằng ĐÚNG
     * cái hằng sai ấy — một vòng khép kín tự chứng minh mình đúng. Nó chỉ lộ ra khi mô hình thật tải xong trên
     * máy ảo và Vosk báo `bảng ký hiệu sai magic=0x7eb2fb74`.
     *
     * Bài học đã khoá lại bằng `VoiceModelManifestTest.doc duoc phan dau cua tep Gr fst THAT`: bài đó đọc **byte
     * thật** cắt ra từ chính `graph/Gr.fst`, nên một hằng đoán sai không thể xanh được nữa (CLAUDE.md §10).
     */
    private const val SYMBOL_TABLE_MAGIC = 0x7EB2FB74

    /**
     * Trần độ dài một chuỗi và số mục một bảng.
     *
     * Không phải nghi ngờ Vosk: tệp này **tải từ mạng về** ([VoiceModelManifest]), nên một gói hỏng/bị cắt giữa
     * chừng sẽ cho ra `size` = vài tỉ và ta cấp phát tới chết. Tin băm sha256 rồi vẫn kiểm ở đây, vì lớp này có
     * thể được gọi với một tệp bất kỳ (bài kiểm, mô hình khác về sau).
     */
    private const val MAX_STRING = 4096
    private const val MAX_SYMBOLS = 5_000_000

    /**
     * Đọc bảng **ký hiệu ĐẦU RA** (= danh sách từ) nhúng trong một tệp FST.
     *
     * @return danh sách từ theo đúng thứ tự khai trong bảng; **rỗng** nếu tệp không nhúng bảng đầu ra (mô hình
     *   kiểu khác — chỗ gọi tự lùi về `graph/words.txt`, xem [VoiceModelManifest.WORDS_FILE]).
     * @throws IOException tệp không phải FST, hoặc hỏng giữa chừng.
     */
    @Throws(IOException::class)
    fun readOutputSymbols(input: InputStream): List<String> {
        val s = DataInputStream(input.buffered(1 shl 16))
        val magic = s.readIntLe()
        if (magic != FST_MAGIC) throw IOException("không phải tệp FST (magic=0x${magic.toUInt().toString(16)})")
        s.readStringLe()                     // fsttype
        s.readStringLe()                     // arctype
        s.readIntLe()                        // version
        val flags = s.readIntLe()
        s.skipExactly(8 + 8 + 8 + 8)         // properties · start · numstates · numarcs
        // isymbols phải ĐỌC QUA, không được bỏ qua bằng skip: mục của nó dài không cố định.
        if (flags and 0x1 != 0) s.readSymbolTable()
        if (flags and 0x2 == 0) return emptyList()
        return s.readSymbolTable()
    }

    /** Một bảng ký hiệu. Trả danh sách từ (bỏ khoá — Kachi chỉ cần biết *từ nào có mặt*). */
    @Throws(IOException::class)
    private fun DataInputStream.readSymbolTable(): List<String> {
        val magic = readIntLe()
        if (magic != SYMBOL_TABLE_MAGIC) throw IOException("bảng ký hiệu sai magic=0x${magic.toUInt().toString(16)}")
        readStringLe()                       // tên bảng (vd "exp/chain/tdnn/lgraph/words.txt")
        readLongLe()                         // available_key
        val size = readLongLe()
        if (size < 0 || size > MAX_SYMBOLS) throw IOException("bảng ký hiệu khai $size mục — tệp hỏng?")
        val out = ArrayList<String>(size.toInt().coerceAtMost(1 shl 16))
        repeat(size.toInt()) {
            out.add(readStringLe())
            readLongLe()                     // khoá
        }
        return out
    }

    @Throws(IOException::class)
    private fun DataInputStream.readIntLe(): Int =
        (readUByte()) or (readUByte() shl 8) or (readUByte() shl 16) or (readUByte() shl 24)

    @Throws(IOException::class)
    private fun DataInputStream.readLongLe(): Long {
        var v = 0L
        for (i in 0 until 8) v = v or (readUByte().toLong() shl (8 * i))
        return v
    }

    /** Một byte không dấu; `-1` (hết luồng) là **lỗi**, không phải giá trị. */
    @Throws(IOException::class)
    private fun DataInputStream.readUByte(): Int {
        val b = read()
        if (b < 0) throw IOException("tệp FST cụt giữa chừng")
        return b
    }

    @Throws(IOException::class)
    private fun DataInputStream.readStringLe(): String {
        val n = readIntLe()
        if (n < 0 || n > MAX_STRING) throw IOException("chuỗi khai $n byte — tệp hỏng?")
        val buf = ByteArray(n)
        readFully(buf)
        return String(buf, Charsets.UTF_8)
    }

    /** `skip` được phép trả ít hơn yêu cầu ⇒ lặp cho đủ, thiếu là tệp cụt. */
    @Throws(IOException::class)
    private fun DataInputStream.skipExactly(n: Long) {
        var left = n
        while (left > 0) {
            val got = skip(left)
            if (got <= 0) throw IOException("tệp FST cụt ở phần header")
            left -= got
        }
    }
}
