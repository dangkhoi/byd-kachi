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

    // ══ (2b) V1.1 — TÊN APP ĐÍCH ════════════════════════════════════════════════════════════════════════

    /**
     * Tên app đích chỉ vào ngữ pháp **khi app ấy có trên máy** — cùng luật với danh sách app đã cài.
     *
     * Khai *"spotify"* trên một chiếc xe không cài Spotify là mở thêm một đường cho bộ giải mã nghe nhầm vào một
     * app không tồn tại, mà không đổi lại được gì: câu ấy rồi cũng chỉ nhận được câu trả lời *"chưa cài"*.
     */
    @Test
    fun `ten app dich chi vao ngu phap khi app do co tren may`() {
        val none = VoiceGrammar.phrases(vocabulary)
        assertFalse(none.entries.any { VoiceLexicon.deaccent(it) == "youtube music" },
            "chưa khai app nào cài mà tên app đích đã vào ngữ pháp")

        val withYt = VoiceGrammar.phrases(
            vocabulary,
            apps = listOf("YouTube Music"),
            installed = setOf("com.google.android.apps.youtube.music"),
        )
        assertTrue(withYt.entries.any { VoiceLexicon.deaccent(it) == "youtube music" },
            "app đã cài mà tên nó vẫn không nói được")
    }

    /**
     * **[ĐO] mô hình tiếng Việt KHÔNG có mọi tên thương hiệu** — con số này là một phép đo, không phải một lỗi.
     *
     * Từ điển 19.529 mục có `youtube` · `music` · `google` · `map` · `yt` nhưng **không** có `maps` · `waze` ·
     * `spotify` · `zing` · `mp3` · `vietmap`. Hệ quả đã chấp nhận và ghi ở §9 spec: Spotify và Zing MP3 **gõ
     * được mà chưa nói được**; Google Maps · Waze · VietMap thì có cách nói thay thế nên vẫn nói được.
     *
     * Ngày đổi mô hình, bài này đỏ và người đọc biết ngay phải xem lại chỗ nào.
     */
    @Test
    fun `do lai nhung ten app ma mo hinh khong doc noi`() {
        val all = VoiceAppTargets.ALL.map { it.packages.first() }.toSet()
        val set = VoiceGrammar.phrases(vocabulary, installed = all)
        val sayable = set.entries.map { VoiceLexicon.deaccent(it) }.toSet()

        listOf(VoiceAppTargets.YT_MUSIC, VoiceAppTargets.YOUTUBE, VoiceAppTargets.GMAPS,
            VoiceAppTargets.WAZE, VoiceAppTargets.VIETMAP).forEach { key ->
            val target = VoiceAppTargets.byKey(key)!!
            assertTrue(
                target.spoken.any { it in sayable },
                "app `$key` không có cách nói nào mô hình đọc nổi — thêm một cách gọi thuần Việt vào VoiceSynonyms.APP_TARGETS",
            )
        }
        // ═══ H3 (2026-09-16) — **tin tốt đã tới**: Spotify và Zing MP3 NAY nói được ═════════════════════════
        //
        // Tới 1.68 hai app này nằm ở vế `assertFalse` của chính bài này, kèm ghi chú *"chấp nhận: gõ được mà chưa
        // nói được, vì không có âm Việt nào tự nhiên"*. Kết luận ấy đã sai ở chỗ *"không có"*: `apps.tsv` (người
        // soạn tay) ghi **sờ pô ti phai** · **pô ti phai** · **ding mờ pê ba**, và [ĐO] tra lại đúng tệp từ điển
        // 19.529 mục thì mọi từ trong chúng đều có mặt. Nên nay MỌI app đích phải nói được — không còn ngoại lệ,
        // và ngày một app mới vào bảng mà quên khai cách đọc thì bài này đỏ.
        VoiceAppTargets.ALL.forEach { target ->
            assertTrue(
                target.spoken.any { it in sayable },
                "app `${target.key}` không có cách nói nào mô hình đọc nổi — thêm cách đọc âm Việt vào " +
                    "VoiceSynonyms.APP_TARGETS (xem scripts/voice/data/apps.tsv)",
            )
        }
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
        /**
         * [ĐO] 2026-09-14: 330 cụm nhiều từ dựng được từ nhãn + từ đồng nghĩa.
         *
         * [ĐO] 2026-09-16 · L7 *"bố cục bằng giọng nói"*: **330 → 338 (+8)** = đúng 8 cách nói của
         * [VoiceLayouts.SPOKEN] (*"bố cục"*, 5 bố cục, *"đổi bố cục"*, *"chuyển bố cục"*). Cả 8 đều giữ được ⇒
         * mô hình có đủ mọi từ trong chúng; không cụm nào rơi vào [EXPECTED_PHRASES_DROPPED].
         */
        // [ĐO] 2026-09-16 · V3 R10 *"kính lái"*: **338 → 342 (+4)** = đúng 4 cách nói mới của `window`
        // (`kinh lai` · `cua kinh lai` · `kinh tai xe` · `cua so lai`), cả bốn đều có dạng có dấu ở
        // [SherpaSpokenWords] nên không cụm nào bị loại.
        //
        // [ĐO] 2026-09-16 · (N) ADAS-PURGE: **342 → 305 (−37)** — owner gỡ toàn bộ ADAS/an toàn khỏi launcher
        // (10 nút + 17 datum + 4 cách nói `itac`/`avh`), nên mọi cụm nhiều từ dựng từ nhãn của chúng rụng theo.
        //
        // [ĐO off-car 2026-09-16 · H3/H4] **305 → 379 (+74)** = đúng 74 cách gọi mới thêm vào [VoiceSynonyms]
        // (giọng Nam từ `scripts/voice/data/nouns.tsv` + `"đang đọc sách"` [ĐO xe] + vài cách nói đời thường).
        // Đếm bằng máy trên CHÍNH tệp từ điển của bài này: cả 74 cụm đều nhiều từ và **mọi từ đều tra được**
        // (không có dạng bỏ dấu nào vắng mặt) ⇒ không cụm nào rơi sang [EXPECTED_PHRASES_DROPPED].
        // ⚠ Tên app đích KHÔNG nằm trong con số này: `set` gọi với `installed` rỗng (xem [EXPECTED_ENTRIES]).
        // [ĐO off-car 2026-09-16 · lượt soát của cha] **379 → 378 (−1)**: gỡ `"khoa cua xe"` khỏi `lock` vì nó
        // nuốt cụm ĐÃ ĐO `MỞ KHÓA CỬA` qua luật tiền tố — lý do đầy đủ ở chỗ khai trong `VoiceSynonyms.kt`.
        // [ĐO off-car 2026-09-16 · H1 T2] **378 → 390 (+12)** = cụm nhiều từ dựng từ nhãn + nhãn ngắn của SÁU
        // datum mới (*"Mức ghế mát"*, *"Ghế mát"*, *"Trạng thái sấy trước"*, *"Sấy trước"*, *"Chế độ điều hòa"*,
        // *"Âm lượng giải trí"*…). Con số đếm bằng máy trên chính tệp từ điển của bài, không chép tay.
        // [ĐO off-car 2026-09-17 · (V) FEATURE-FILTER] **390 → 357 (−33)** = cụm nhiều từ dựng từ nhãn + nhãn
        // ngắn + từ đồng nghĩa của 19 mã owner chấm NO (cụm sạc, chế độ lái, gập gương, chế độ drift, chìa
        // Bluetooth, trạng thái nguồn MCU…). Số đọc từ **actual** của chính bài này.
        // [ĐO off-car 2026-09-17 · voice-number-read] **357 → 359 (+2)** = hai cách nói nhiều từ mới cho câu
        // HỎI về nhiệt AC (`inside_temp ← "nhiệt độ"`, `"điều hòa bao nhiêu độ"`) — thêm để «nhiệt độ đang bao
        // nhiêu»/«máy lạnh bao nhiêu độ» map đúng datum thay vì rỗng/media_vol (log xe 81 lượt). Đọc từ **actual**.
        // [ĐO off-car 2026-09-18 · D2 log xe] **361 → 362 (+1)** = đúng MỘT cách nói mới của datum `ac_wind`:
        // *"quạt điều hòa"*. Ba cụm còn lại của dòng ấy (*"quạt gió"* · *"mức gió"* · *"tốc độ quạt"*) đã có sẵn
        // ở nút `fan`, và `distinct()` của `VoiceGrammar.terms` gộp chúng ⇒ chỉ cộng 1.
        // [ĐO off-car 2026-09-20 · 1.85] **364 → 372 (+8)**: −2 cụm của `hood` (*"nắp ca pô"* · *"nắp máy"* —
        // mã đã xoá) +4 cụm khoá trẻ em hai bên (*"khóa trẻ em"* · *"…bên trái"* · *"…bên phải"* ·
        // *"khóa con nít bên phải"*) +1 *"gió auto"* (`ac_auto`) +5 từ nhãn mới nhiều từ (*"Gió tự động"* ·
        // *"Khóa trẻ em trái/phải"* · *"Chế độ gió"* + nhãn ngắn). Số đọc từ **actual** của chính bài này.
        // [ĐO off-car 2026-09-20 · UX-OVERHAUL WP8] **372 → 308 (−64)**: 37 mã purge kéo theo cả cách nói riêng
        // của chúng (10 dòng `VoiceSynonyms` + nhãn nhiều từ của 29 datum + 8 nút) và 5 cụm của nhóm `g_ambient`.
        // Số đọc từ **actual** của chính bài này, như mọi lượt trước.
        const val EXPECTED_PHRASES_KEPT = 308
        // [ĐO off-car 2026-09-18 · log xe 53 phiên] **359 → 361 (+2)** = hai cách nói NHIỀU TỪ mới cho nhiên liệu
        // (`fuel_pct ← "nhien lieu"` · `"muc nhien lieu"`). Cách nói thứ ba (`"xang"`) là MỘT từ nên không vào con
        // số này — nó chỉ nở thêm ở [EXPECTED_ENTRIES]. Thêm để «chỉ số xăng» / «xăng còn bao nhiêu» (cả hai ra
        // Unknown trong log) map đúng `fuel_pct`. Đọc từ **actual** của chính bài này.

        /**
         * [ĐO] 269 cụm bị loại — **gần như toàn bộ là nhãn tiếng ANH** (*"Reading light"*, *"Tyre FL"*…), cộng
         * vài nhãn Việt mang chữ viết tắt/chữ số đã có cách gọi thuần Việt đi kèm (*"Bụi mịn PM2.5"* vẫn nói
         * được qua *"nồng độ bụi mịn"*). Đây là hạn chế **của mô hình tiếng Việt**, không phải lỗi của Kachi:
         * nói lệnh bằng tiếng Anh trên xe này thì không nghe ra. Gõ vẫn được (tầng chữ không đụng tới mô hình).
         *
         * [ĐO] 2026-09-15 · T7 "nút mở 50%": **269 → 274 (+5)** = đúng 5 nhãn tiếng Anh *"Half"* thêm vào
         * `win_lf/rf/lr/rr` + `sunshade` (bị loại như mọi nhãn Anh). Cụm giữ (330) KHÔNG đổi (*"Nửa"* là từ
         * đơn, không tạo cụm nhiều từ). Tổng mục đổi — xem [EXPECTED_ENTRIES].
         *
         * [ĐO] 2026-09-16 · (N) ADAS-PURGE: **274 → 236 (−38)** — phần lớn là nhãn tiếng Anh của các mục ADAS
         * vừa xoá (*"Blind spot front-left"*, *"Rear cross-traffic alert"*…).
         */
        //
        // [ĐO off-car 2026-09-16 · H3/H4] **KHÔNG đổi** — cả 74 cách gọi mới đều tra được trọn vẹn (xem
        // [EXPECTED_PHRASES_KEPT]). Đây là hệ quả của luật nhập: cách gọi giọng Nam là **tiếng Việt đời thường**,
        // đúng thứ mô hình VN phủ tốt nhất — khác hẳn nhãn tiếng Anh vốn chiếm gần trọn con số dưới.
        // [ĐO off-car 2026-09-16 · H1 T2] **236 → 244 (+8)** = tám cụm tiếng ANH của sáu datum mới
        // (*"Seat ventilation level"*, *"Front defrost state"*, *"Media volume"*…) — đúng họ đã chiếm gần trọn
        // con số này: mô hình tiếng Việt không nghe ra nhãn tiếng Anh. Bản tiếng Việt của cả sáu đều GIỮ được.
        // [ĐO off-car 2026-09-17 · (V) FEATURE-FILTER] **244 → 216 (−28)** = nhãn tiếng ANH của 19 mã owner
        //   chấm NO (*"Charge power"*, *"Drive mode"*, *"Fold mirrors on lock"*, *"Bluetooth key"*…).
        // [ĐO off-car 2026-09-18 · kính 50%] **216 → 217 (+1)** = nhãn EN *"Half"* của `windows_all` (nay khai mức Nửa).
        // [ĐO off-car 2026-09-20 · ghế 2 mức] **217 → 227 (+10)** = args của `seatc`/`seath` nay là SELECT
        //   (Tắt/Mức 1/Mức 2 + Off/Level 1/Level 2): nhãn EN *"Level 1/2"* + nhãn VI *"Mức 1/2"* mang **chữ số**
        //   ⇒ không bias được (rơi DROPPED như mọi nhãn Anh/chữ-số). Parse vẫn chạy: `selectIndex` khớp nhãn HOẶC
        //   số nói thẳng ("ghế mát mức 2"→index 2). `trunk`→COVER không đổi con số (không có args).
        // [ĐO off-car 2026-09-20 · 1.85] **229 → 233 (+4)** = nhãn EN của bốn mã mới/đổi mang chữ Anh nên không
        //   bias được cho mô hình VN (đúng như mọi nhãn EN khác): *"Auto fan"* · *"Child lock left/right"* ·
        //   *"Fan mode"* (+ nhãn ngắn *"Fan auto"*), trừ đi hai cụm EN của `hood` (*"Bonnet"* là một từ nên
        //   không nằm ở con số CỤM). Số đọc từ **actual** của chính bài này.
        // [ĐO off-car 2026-09-20 · WP8] **233 → 193 (−40)**: cụm bị loại cũng teo theo 37 mã purge.
        const val EXPECTED_PHRASES_DROPPED = 193

        /**
         * [ĐO] tổng mục ngữ pháp = 330 cụm + từ đơn (mọi cách viết thanh điệu) + `[unk]`.
         *
         * 2026-09-14 · V1.1: **2037 → 2058** (+21). Toàn bộ phần thêm là **từ đơn**: 8 cách viết của động từ
         * mới *"đưa"* (*"đưa YouTube vào ô 2"*), cộng hai bảng mới —
         * [VoiceLexicon.SLOT_WORDS] (*"ô · số · thứ · vào · slot · in · into"*) và
         * [VoiceLexicon.BY_APP_MARKERS] (*"bằng · trên · với · qua · dùng · with · on · using"*), nở theo thanh
         * điệu. (*"dùng"* thêm 2026-09-18 và cộng **0** mục: chữ `dung` đã có sẵn từ bảng động từ.)
         * Số **cụm** không đổi: tên app đích chỉ vào ngữ pháp khi app ấy **có trên máy** (`installed`), mà bài
         * này cố ý gọi với danh sách rỗng — xem `ten app dich chi vao ngu phap khi app do co tren may`.
         */
        // [ĐO] 2026-09-15 · T7: **2058 → 2063 (+5)**. Từ đơn MỚI *"nửa"* (arg của 4 kính + rèm) nở theo thanh điệu
        // — cùng cơ chế +21 của *"đưa"* ở V1.1. (Lần đo đầu chỉ lộ assertion "cụm loại"; mục này lộ ở lần đo 2 —
        // đúng lý do bài khoá CẢ BA con số.)
        //
        // [ĐO] 2026-09-16 · L7: **2063 → 2102 (+39)** = 8 **cụm** bố cục ([EXPECTED_PHRASES_KEPT]) + 31 **từ đơn**
        // mới nở theo thanh điệu từ [VoiceLayouts.WORDS] (*"bố"*, *"cục"*, *"cột"*, *"hàng"*, *"dòng"*, *"chọn"*,
        // *"thành"*… — phần lớn động từ/đơn vị đã có sẵn nên không cộng thêm). Số **cụm loại** KHÔNG đổi: mô hình
        // có đủ mọi từ của cả 8 cách nói.
        //
        // [ĐO] 2026-09-16 · C1 (owner chốt: cổng xác nhận nhận mọi từ đồng nghĩa *"có"*): **2102 → 2110 (+8)**.
        // Không có **cụm** nào mới (hai cụm `đúng rồi`/`làm đi` gồm toàn từ đã có ⇒ [EXPECTED_PHRASES_KEPT] giữ
        // nguyên); +8 là **từ đơn** mới của [VoiceLexicon.CONFIRM_YES] nở theo thanh điệu (`u` · `vang` · `co` ·
        // `duoc` · `dung` · `roi` · `lam` · `di` — mỗi từ chỉ cộng phần biến thể chưa ai khai).
        //
        // [ĐO] 2026-09-16 · V3 R10 (*"kính lái"*): **2110 → 2114 (+4)** = đúng 4 **cụm** mới của `window`
        // ([EXPECTED_PHRASES_KEPT] 338 → 342); KHÔNG có từ đơn nào mới — `kinh`/`lai`/`cua`/`so`/`tai`/`xe`
        // đều đã có sẵn trong từ vựng, nên phần nở theo thanh điệu không cộng thêm gì.
        //
        // [ĐO] 2026-09-16 · (N) ADAS-PURGE: **2114 → 1985 (−129)** = 37 cụm giữ + 38 cụm loại rụng theo registry,
        // phần còn lại là **từ đơn** chỉ xuất hiện trong nhãn ADAS (*"mù"*, *"làn"*, *"thắt"*, *"ESP"*…).
        //
        // ⚠⚠ [CHƯA ĐO · 2026-09-16 · H3/H4] Con số dưới là con số **CŨ** và nó sẽ đỏ ở lượt chạy đầu.
        //
        // Hai vế kia tính được off-car vì chúng chỉ phụ thuộc *"cụm mới có tra được không"* — đếm thẳng trên tệp
        // từ điển là ra. Vế này thì không: nó còn cộng **từ đơn nở theo thanh điệu**, mà phần nở ấy phụ thuộc
        // *"từ đó đã có ai khai chưa"* — tức phụ thuộc toàn bộ tập singles cũ (1679 mục), thứ chỉ dựng được khi
        // chạy thật. Đoán một con số ở đây là đúng thứ CLAUDE.md §2 cấm.
        //
        // ⇒ Chạy `:core:test --tests '*VoiceGrammarPhrasesTest*'`, đọc **actual** trong câu báo lỗi, dán vào đây
        // kèm một dòng giải thích phần chênh (cụm mới + từ đơn mới: `kiếng` · `chốt` · `thùng` · `nít` · `xếp` ·
        // `hãm` · `khung` · `hơi` · `bánh` · `nữa` … và hai tiếng đệm mới `u`/`um` của [VoiceLexicon.FILLERS]).
        // [ĐO off-car 2026-09-16 · lượt chạy thật của cha] **1985 → 2192 (+207)**. Phần chênh = 73 cụm nhiều
        // từ mới (378 − 305) **cộng** các từ ĐƠN chưa ai khai, nở theo họ thanh điệu: `kiếng` · `chốt` ·
        // `thùng` · `nít` · `xếp` · `hãm` · `khung` · `hơi` · `bánh` · `nữa` … và hai tiếng đệm `ừ`/`ừm` mới
        // thêm vào [VoiceLexicon.FILLERS] (P0 vòng lặp hội thoại). Con số đọc từ **actual** của chính bài này,
        // không phải một phép đoán — đúng cách KDoc trên đã dặn.
        // [ĐO off-car 2026-09-16 · H1 T2 · lượt chạy thật] **2192 → 2212 (+20)**: 12 cụm nhiều từ của sáu datum
        // mới (390 − 378) cộng các từ ĐƠN lần đầu xuất hiện trong nhãn của chúng (`mức` đã có, nhưng `lượng` ·
        // `giải` · `trí` · `trạng` · `thái` … thì chưa), nở theo họ thanh điệu như mọi từ đơn khác. Số đọc từ
        // **actual** của chính bài này, không phải phép đoán.
        // [ĐO off-car 2026-09-17 · (V) FEATURE-FILTER · lượt chạy thật] **2212 → 2097 (−115)** = 33 cụm giữ +
        // 28 cụm loại rụng theo registry, phần còn lại là **từ đơn** chỉ xuất hiện trong nhãn/cách gọi của 19 mã
        // owner chấm NO (*"drift"*, *"gương"*, *"chìa"*, *"sạc"* các biến thể chưa ai khai ở chỗ khác…).
        // Số đọc từ **actual** của chính bài này, không phải phép đoán — đúng cách KDoc trên đã dặn.
        // [ĐO off-car 2026-09-17 · voice-number-read] **2097 → 2099 (+2)** = 2 cách nói mới cho câu hỏi nhiệt AC
        // (`inside_temp`) — cùng 2 cụm với [EXPECTED_PHRASES_KEPT].
        // [ĐO off-car 2026-09-18 · D2] **2109 → 2110 (+1)** = đúng mục CỤM mới *"quạt điều hòa"* (xem
        // [EXPECTED_PHRASES_KEPT]). KHÔNG có từ đơn nào mới: `quạt` · `điều` · `hòa` đều đã có trong từ vựng, nên
        // phần nở theo thanh điệu không cộng thêm gì. Cụm đánh dấu `dung` (*"dùng &lt;app&gt;"*) cũng cộng **0** —
        // [ĐO] chữ ấy đã có sẵn ở bảng động từ (*"dừng"* = PAUSE) và ở [VoiceLexicon.CONFIRM_YES].
        // [ĐO off-car 2026-09-20 · 1.85] **2112 → 2117 (+5)**: các cụm/từ ĐƠN lần đầu xuất hiện, nở theo họ thanh
        // điệu — `trẻ` · `em` (khoá trẻ em hai bên) và `auto` đứng một mình (cách nói *"gió auto"*), trừ đi phần
        // của `hood` (`nắp` · `ca` · `pô` · `máy` — phần lớn đã có sẵn ở từ vựng khác nên rụng ít). Số đọc từ
        // **actual** của chính bài này, không phải phép đoán.
        // [ĐO off-car 2026-09-20 · WP8] **2117 → 1904 (−213)**: 37 mã purge kéo theo mọi mục hotword của chúng.
        const val EXPECTED_ENTRIES = 1904
        // [ĐO off-car 2026-09-18 · log xe 53 phiên] **2099 → 2109 (+10)** = 2 cụm nhiều từ của nhiên liệu
        // ([EXPECTED_PHRASES_KEPT] 359 → 361) **cộng** các từ ĐƠN lần đầu xuất hiện, nở theo họ thanh điệu:
        // `xăng` đứng một mình (cách nói mới của `fuel_pct`) và `nhiên` · `liệu`. Số đọc từ **actual** của chính
        // bài này, không phải phép đoán — đúng cách KDoc trên đã dặn.
    }
}
