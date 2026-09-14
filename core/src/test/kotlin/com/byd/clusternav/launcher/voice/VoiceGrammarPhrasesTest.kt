package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.TelemetryRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V1 pha NGHE · NGỮ PHÁP DỰNG TỪ **TỪ ĐIỂN THẬT CỦA MÔ HÌNH** ═════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R10 · R14.
 *
 * ## Vì sao bài này phải chạy trên tệp từ điển THẬT, không phải một danh sách bịa
 * CLAUDE.md §10: *"mỗi lỗi hiện trường đã root-cause → một test hồi quy dựng từ dump thật"*. Ở đây "dump thật" là
 * bảng ký hiệu **rút ra từ chính `graph/Gr.fst`** của `vosk-model-small-vn-0.4` (19.529 mục,
 * `core/src/test/resources/voice/…words.txt`, sinh 2026-09-14 bằng [VoskWordList] trên gói đã băm sha256).
 *
 * Một danh sách bịa sẽ **luôn xanh**: ta tự viết vào đó đúng những từ ta cần. Còn câu hỏi thật là *"mô hình 32 MB
 * này có nghe nổi danh mục 65 nút + 123 datum của Kachi không"* — và câu trả lời chỉ có ở tệp thật. [ĐO] nó
 * KHÔNG phủ hết: bài `so cum bi loai duoc ghi lai` chốt con số bị loại, nên ngày ai đó đổi mô hình hoặc thêm một
 * nhãn ngoài từ điển thì con số đổi và bài đỏ — thay vì một câu lệnh im lặng không bao giờ nghe ra được.
 */
class VoiceGrammarPhrasesTest {

    private val vocabulary: Set<String> by lazy {
        val res = javaClass.getResourceAsStream("/voice/vosk-model-small-vn-0.4.words.txt")
        requireNotNull(res) { "thiếu tệp từ điển mẫu — xem KDoc lớp" }
        res.bufferedReader().readLines().filter { it.isNotBlank() }.toSet()
    }

    private val set: VoicePhraseSet by lazy { VoiceGrammar.phrases(vocabulary) }

    // ══ (1) Tiền đề về chính tệp từ điển ═════════════════════════════════════════════════════════════════

    /**
     * Tiền đề của mọi bài dưới: mô hình dùng chữ **CÓ DẤU**, và có sẵn mục `[unk]`.
     *
     * Nếu một ngày tiền đề này sai (đổi sang mô hình không dấu), toàn bộ thiết kế "tra ngược qua từ điển" của
     * [VoicePhrases] mất lý do tồn tại — nên nó phải đỏ ở đây, không phải hỏng âm thầm trên xe.
     */
    @Test
    fun `tu dien mo hinh la chu CO DAU va co muc unk`() {
        assertEquals(19_529, vocabulary.size, "từ điển mẫu phải đúng bảng đã rút ra 2026-09-14")
        assertTrue(VoicePhrases.UNK in vocabulary, "thiếu `[unk]` ⇒ mọi tiếng động bị ép thành một câu lệnh")
        listOf("đèn", "bật", "tắt", "nhiệt", "độ", "mở", "khoá", "cửa", "kính", "pin").forEach {
            assertTrue(it in vocabulary, "mô hình phải có chữ có dấu `$it`")
        }
    }

    // ══ (2) Nội dung ngữ pháp ════════════════════════════════════════════════════════════════════════════

    /** Mọi mục là chữ thường, không rỗng, không trùng — điều kiện để đếm được và để LM không bị thiên lệch. */
    @Test
    fun `moi muc deu la chu thuong, khong rong, khong trung`() {
        assertEquals(set.entries.size, set.entries.toSet().size, "mục trùng ⇒ LM đếm hai lần cùng một cụm")
        set.entries.forEach { e ->
            assertTrue(e.isNotBlank(), "mục rỗng")
            assertEquals(e.lowercase(), e, "mục `$e` còn chữ hoa — từ điển mô hình là chữ thường")
        }
    }

    /** `[unk]` phải có mặt, và ở CUỐI (thứ tự khai là hợp đồng của [VoicePhraseSet]). */
    @Test
    fun `co muc unk va no dung cuoi`() {
        assertEquals(VoicePhrases.UNK, set.entries.last())
        assertEquals(1, set.entries.count { it == VoicePhrases.UNK })
    }

    /**
     * **MỌI mục ngữ pháp phải nằm trong từ điển mô hình** — bài quan trọng nhất của lớp này.
     *
     * [ĐO] `vosk-api/src/recognizer.cc:340-347`: từ nào không có trong từ điển bị **bỏ lặng lẽ** (chỉ một dòng
     * `KALDI_WARN` trong log native). Nên một mục lọt lưới không làm gì đỏ — nó chỉ biến cụm ta khai thành một
     * cụm **khác**, rồi khớp với câu ta không định cho khớp. Đây là cái bẫy mà cả lớp [VoicePhrases] sinh ra để
     * đóng, nên nó phải có một bài canh trực tiếp.
     */
    @Test
    fun `moi tu trong moi muc deu co trong tu dien mo hinh`() {
        set.entries.filter { it != VoicePhrases.UNK }.forEach { entry ->
            entry.split(' ').forEach { w ->
                assertTrue(w in vocabulary, "mục `$entry` chứa từ `$w` mà mô hình không có ⇒ Vosk sẽ bỏ nó im lặng")
            }
        }
    }

    /**
     * **Mọi nút · datum · gói lệnh · hành động launcher phải NÓI được** — ít nhất một mục ngữ pháp phủ nhãn của
     * nó (hoặc một từ đồng nghĩa).
     *
     * Đây là bản pha-nghe của bài độ phủ ở [VoiceGrammarCoverageTest]: gõ được mà không nói được thì tính năng
     * chỉ xong một nửa, và nửa thiếu ấy im lặng.
     */
    @Test
    fun `moi kha nang deu co it nhat mot cum noi duoc`() {
        val entryWords = set.entries.map { it.split(' ').map(VoiceLexicon::deaccent) }.toSet()
        fun sayable(labels: List<String>): Boolean = labels.any { l ->
            val want = VoiceLexicon.tokenize(l).map { it.norm }
            want.isNotEmpty() && entryWords.any { it == want }
        }
        ControlRegistry.ALL.forEach { c ->
            val labels = listOfNotNull(c.label, c.labelEn, c.short, c.shortEn) + VoiceSynonyms.CONTROL[c.id].orEmpty()
            assertTrue(sayable(labels), "nút `${c.id}` (${c.label}) không có cụm nào nói được")
        }
        TelemetryRegistry.ALL.forEach { t ->
            val labels = listOfNotNull(t.label, t.labelEn, t.short, t.shortEn) + VoiceSynonyms.TELEMETRY[t.id].orEmpty()
            assertTrue(sayable(labels), "datum `${t.id}` (${t.label}) không có cụm nào nói được")
        }
        ActionMacros.ALL.forEach { m ->
            assertTrue(sayable(listOfNotNull(m.label, m.labelEn)), "gói `${m.id}` không có cụm nào nói được")
        }
        LauncherActions.ALL.forEach { a ->
            assertTrue(sayable(listOfNotNull(a.label, a.labelEn)), "hành động `${a.id}` không có cụm nào nói được")
        }
    }

    /**
     * **MỌI Ý NGHĨA động từ phải nói được ít nhất một cách** — và liên từ · số · từ xác nhận phải có mặt.
     *
     * ## Vì sao canh theo Ý NGHĨA ([VoiceVerb]) chứ không theo từng dòng trong bảng
     * [ĐO] mô hình là **mô hình tiếng Việt**: `navigate` · `switch` · `increase` · `show` · `previous` không có
     * trong từ điển 19.529 mục, và sẽ không bao giờ có. Đòi từng dòng của [VoiceGrammar.VERBS] nói được là đòi
     * một mô hình tiếng Việt biết tiếng Anh — bài canh sẽ đỏ mãi mà không ai sửa được, tức nó vô dụng.
     *
     * Thứ **thật sự** phải giữ là: không ý nghĩa nào bị câm. `UP` còn `tăng`, `SWITCH` còn `chuyển`/`đổi`, nên
     * mọi việc vẫn ra lệnh được bằng tiếng Việt. Ngày ai đó xoá dòng `tăng` và chỉ để `increase`, bài này đỏ.
     *
     * ⚠ So theo dạng **bỏ dấu**: ngữ pháp mang chữ có dấu của mô hình (`bật`), còn bảng động từ mang chữ đã bỏ
     * dấu (`bat`) — đó chính là chỗ nối mà [VoicePhrases] bắc, nên nó phải được canh ở đây.
     */
    @Test
    fun `moi y nghia dong tu noi duoc, lien tu va so va tu xac nhan deu co mat`() {
        val deaccented = set.entries.flatMap { it.split(' ') }.map(VoiceLexicon::deaccent).toSet()
        val covered = VoiceGrammar.VERBS.filter { (words, _) -> words.all { it in deaccented } }.map { it.second }
        VoiceVerb.values().forEach { v ->
            assertTrue(v in covered, "không cách nào NÓI ra động từ $v — mọi câu dùng nó thành câm")
        }
        VoiceIntentParser.CONNECTORS
            .filter { it == "va" || it == "roi" }   // liên từ tiếng Việt; `and`/`then` là của bàn phím
            .forEach { assertTrue(it in deaccented, "liên từ `$it` không nói được ⇒ mất câu ghép") }
        listOf("mot", "hai", "ba", "bon", "nam", "muoi", "ham", "toi", "da").forEach {
            assertTrue(it in deaccented, "từ số `$it` không nói được ⇒ mất mọi câu đặt giá trị")
        }
        (VoiceLexicon.CONFIRM_YES + VoiceLexicon.CONFIRM_NO)
            .filter { phrase -> phrase.none { it == "ok" || it == "yes" || it == "no" || it == "confirm" || it == "cancel" } }
            .forEach { phrase ->
                phrase.forEach { w -> assertTrue(w in deaccented, "từ xác nhận `$w` không nói được") }
            }
    }

    /**
     * Danh sách động (hồ sơ · app đã cài) đi vào ngữ pháp, và **không** kéo theo từ lạ.
     *
     * Tên app là dữ liệu của hệ thống, có thể chứa chữ mô hình không biết (*"VTV Go"*, *"Zalo"*). Kỳ vọng đúng:
     * cụm ấy bị **loại cả cụm** chứ không lọt vào nửa vời — xem [VoicePhrases].
     */
    @Test
    fun `ho so va app di vao ngu phap, tu la thi bi loai ca cum`() {
        val withDyn = VoiceGrammar.phrases(vocabulary, profiles = listOf("Vợ"), apps = listOf("Bản đồ", "Qzz Xyq"))
        assertTrue(withDyn.entries.any { VoiceLexicon.deaccent(it) == "vo" }, "tên hồ sơ phải nói được")
        assertTrue(withDyn.entries.any { VoiceLexicon.deaccent(it) == "ban do" }, "tên app phải nói được")
        assertTrue(withDyn.phrasesDropped.contains("Qzz Xyq"), "app có chữ ngoài từ điển phải bị loại CẢ cụm")
        assertFalse(withDyn.entries.any { it.contains("qzz") }, "không được để nửa cụm lọt vào")
    }

    // ══ (3) Con số — khoá lại phép đo 2026-09-14 ═════════════════════════════════════════════════════════

    /**
     * **SỐ CỤM GIỮ / LOẠI — con số này là kết quả đo, không phải một hằng đẹp.**
     *
     * Nó đổi khi (a) thêm/bớt một dòng registry, (b) sửa [VoiceSynonyms], hoặc (c) đổi mô hình. Cả ba đều là
     * việc **đáng phải xem lại bằng mắt**: (a)/(b) thì kiểm xem nhãn mới có nói được không, (c) thì kiểm lại toàn
     * bộ độ phủ. Vì thế bài này khoá con số chứ không khoá một bất đẳng thức lỏng lẻo — đỏ ở đây là *"đọc lại
     * §9 của spec rồi cập nhật con số"*, không phải *"sửa cho xanh"*.
     *
     * [ĐO] 2026-09-14 — xem `docs/specs/kachi-voice-command.html` §9.
     */
    @Test
    fun `so cum giu va loai dung nhu phep do 2026-09-14`() {
        assertEquals(EXPECTED_PHRASES_KEPT, set.phrasesKept, "số cụm nhiều từ dựng được đã đổi — xem KDoc")
        assertEquals(EXPECTED_PHRASES_DROPPED, set.phrasesDropped.size, "số cụm bị loại đã đổi — xem KDoc")
        assertEquals(EXPECTED_ENTRIES, set.entries.size, "tổng số mục ngữ pháp đã đổi — xem KDoc")
    }

    /** JSON phải là một mảng chuỗi hợp lệ, và phải thoát dấu nháy. */
    @Test
    fun `json la mang chuoi va thoat dau nhay`() {
        val json = set.json()
        assertTrue(json.startsWith("[\"") && json.endsWith("\"]"), "phải là mảng JSON")
        assertTrue(json.contains("\"[unk]\""), "phải mang `[unk]`")
        val odd = VoicePhraseSet(listOf("a\"b\\c"), 0, emptyList(), emptyList())
        assertEquals("[\"a\\\"b\\\\c\"]", odd.json())
    }

    private companion object {
        /** [ĐO] 2026-09-14: 330 cụm nhiều từ dựng được từ nhãn + từ đồng nghĩa. */
        const val EXPECTED_PHRASES_KEPT = 330

        /**
         * [ĐO] 269 cụm bị loại — **gần như toàn bộ là nhãn tiếng ANH** (*"Reading light"*, *"Tyre FL"*…), cộng
         * vài nhãn Việt mang chữ viết tắt/chữ số đã có cách gọi thuần Việt đi kèm (*"Bụi mịn PM2.5"* vẫn nói
         * được qua *"nồng độ bụi mịn"*). Đây là hạn chế **của mô hình tiếng Việt**, không phải lỗi của Kachi:
         * nói lệnh bằng tiếng Anh trên xe này thì không nghe ra. Gõ vẫn được (tầng chữ không đụng tới mô hình).
         */
        const val EXPECTED_PHRASES_DROPPED = 269

        /** [ĐO] tổng mục ngữ pháp = 330 cụm + từ đơn (mọi cách viết thanh điệu) + `[unk]`. */
        const val EXPECTED_ENTRIES = 2037
    }
}
