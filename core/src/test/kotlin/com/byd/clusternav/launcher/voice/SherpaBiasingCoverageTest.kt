package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.TelemetryRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V2 pha NGHE · ĐỘ PHỦ HOTWORDS — KHOÁ LỖ *"một dấu câu nuốt 50 nhãn"* ════════════════════════════════════
 *
 * Bài canh này sinh ra từ một lỗi ĐÃ ĐO, không phải phòng xa:
 * `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 **L3** — 25 câu WAV thật trên máy ảo với
 * `biasing=true`, mô hình `zipformer-vi-2025-04-20`:
 *  • `xem pin` → nghe *"xem tin"* ⇒ `Unknown` (nhãn *"Pin (SOC)"* bị dấu ngoặc giết cả cụm);
 *  • `mở kính trước trái` → *"mở kín trước trái"* ⇒ `Unknown` (nhãn *"Kính trước-trái"*, gạch nối);
 *  • `dừng nhạc` → *"rừng nhạc"* ⇒ `Unknown` (động từ chưa bao giờ là nguồn hotword).
 *
 * Đếm được lúc đó: **12/64** nhãn `ControlRegistry` + **38/123** nhãn `TelemetryRegistry` = **50 nhãn** không
 * bao giờ ra hotword — và cái thiếu ấy **im lặng**: tệp hotwords vẫn được ghi, engine vẫn báo `biasing=true`.
 * Nên chỗ khoá phải là một phép đếm bằng máy, không phải một lời hứa trong KDoc.
 *
 * ## Vòng 2 (2026-09-16) — tệp hotwords nay là **CỤM**, không từ rời
 * [ĐO] host, ba ma trận (spec `kachi-voice-hotword-phrases.html`): có `PIN`/`DỪNG` rời trong tệp vẫn nghe sai;
 * thêm 39 động từ rời vào tệp toàn cụm kéo 21/25 → 17/25. Nên bài canh đổi hình: mỗi nhãn phải nằm **trong** ít
 * nhất một cụm (không phải "có mặt như một dòng"), và tệp **không được** có dòng một từ hay dòng ASCII thuần.
 */
class SherpaBiasingCoverageTest {

    private val lines: List<String> =
        SherpaBiasing.hotwordsFile().trimEnd().split("\n").filter { it.isNotBlank() }
    private val hotwords: Set<String> = lines.toSet()

    /** Mọi nhãn tiếng Việt của 4 bộ đăng ký — đúng tập mà `VoicePhrases`/`VoiceGrammar` cũng phủ. */
    private fun allLabels(): List<String> =
        ControlRegistry.ALL.map { it.label } +
            TelemetryRegistry.ALL.map { it.label } +
            ActionMacros.ALL.map { it.label } +
            LauncherActions.ALL.map { it.label }

    /** `needle` là một dãy từ nguyên vẹn bên trong `line` (ranh giới từ, không phải chuỗi con tuỳ ý). */
    private fun containsWords(line: String, needle: String): Boolean =
        line == needle || line.startsWith("$needle ") || line.endsWith(" $needle") || line.contains(" $needle ")

    @Test
    fun `moi nhan trong danh muc deu sinh it nhat mot hotword`() {
        val lost = allLabels().filter { SherpaHotwords.phrasesOf(it).isEmpty() }
        assertEquals(emptyList<String>(), lost, "nhãn không ra được hotword nào ⇒ nút/datum đó không được bias")
    }

    @Test
    fun `moi nhan tieng Viet deu nam trong it nhat mot cum cua tep hotwords`() {
        // Nhãn ASCII thuần (*"Camera 360"* ⇒ `CAMERA`, *"SOC"*) vẫn phải có mặt qua cụm có động từ VN (*"MỞ CAMERA"*);
        // nhãn nào cả cụm lẫn nhãn đều ASCII (không có từ tiếng Việt nào) thì cố ý không bias — parser chữ lo.
        val missing = allLabels().filter { label ->
            val parts = SherpaHotwords.phrasesOf(label)
            parts.none { p -> lines.any { containsWords(it, p) } }
        }
        assertEquals(emptyList<String>(), missing, "nhãn không nằm trong cụm nào ⇒ nút/datum đó không được bias")
    }

    @Test
    fun `tep hotwords khong co dong mot tu va khong co nhan tieng Anh`() {
        val singles = lines.filterNot { ' ' in it }
        assertEquals(emptyList<String>(), singles, "[ĐO] từ rời chặn cụm dài + cộng điểm đường sai — cấm")
        // Nhãn EN (`labelEn`/`shortEn`/`argsEn`) không được lọt: mô hình VN không phát token ấy, dòng chỉ chiếm chỗ
        // (283/623 dòng ở 1.64). KHÔNG canh bằng "ASCII thuần" — *"XEM PIN"* cũng ASCII thuần mà là câu VN.
        val en = ControlRegistry.ALL.flatMap { listOfNotNull(it.labelEn, it.shortEn) + it.argsEn } +
            TelemetryRegistry.ALL.flatMap { listOfNotNull(it.labelEn, it.shortEn) } +
            ActionMacros.ALL.mapNotNull { it.labelEn } + LauncherActions.ALL.mapNotNull { it.labelEn }
        val leaked = en.flatMap { SherpaHotwords.phrasesOf(it) }.filter { ' ' in it && it in hotwords }
        assertEquals(emptyList<String>(), leaked, "nhãn tiếng Anh lọt vào tệp hotwords")
    }

    @Test
    fun `dong tu roi va cach noi doi thuong roi KHONG con la dong rieng`() {
        // Chính hai bảng từng được đổ RỜI vào tệp (1.64) — nay chỉ được xuất hiện BÊN TRONG cụm.
        (SherpaSpokenWords.VERBS.values.flatten() + SherpaSpokenWords.ACCENTED.values)
            .map { it.uppercase() }.filterNot { ' ' in it }
            .forEach { assertTrue(it !in hotwords, "từ rời «$it» lọt vào tệp hotwords") }
    }

    @Test
    fun `khong dong nao la tien to theo tu cua dong khac`() {
        // [ĐO] host 09-16: `chế độ lái thể thao` → "CHẾ ĐỘ LÁI" khi tệp còn dòng tiền tố; bỏ ⇒ đúng (KDoc dropPrefixes).
        val set = hotwords
        val prefixes = lines.filter { l -> set.any { it != l && it.startsWith("$l ") } }
        assertEquals(emptyList<String>(), prefixes, "dòng tiền tố chặn cụm dài hơn nó")
        // ⚠ (V) FEATURE-FILTER 2026-09-17: ca ĐÃ ĐO cũ là cặp «CHẾ ĐỘ LÁI THỂ THAO» / «CHẾ ĐỘ LÁI» của nút
        // `drive_mode` — nút đó đã gỡ theo lệnh owner. Cặp thay thế cùng CƠ CHẾ (nhãn nút SELECT + nhãn lựa
        // chọn) và vẫn còn sống: `headlight_mode` + lựa chọn *Auto*.
        // ⚠ 1.90 2026-09-21: cặp `headlight_mode` + lựa chọn *Auto* cũng hết (nút đã xoá). Mốc thay thế cùng CƠ CHẾ
        // (nhãn nút SELECT + nhãn lựa chọn) và còn sống: `seatc` ("Ghế mát") + lựa chọn *Mức 1*.
        // ⚠ 1.94 2026-09-22: nhãn ghế đổi "Ghế mát" → "Mát ghế lái" (owner). [ĐO] tệp sinh ra `MÁT GHẾ LÁI MỨC`
        // (SELECT args, bỏ token số), tiền tố `MÁT GHẾ LÁI` phải rụng.
        assertTrue("MÁT GHẾ LÁI MỨC" in set); assertTrue("MÁT GHẾ LÁI" !in set)
    }

    @Test
    fun `tep hotwords sinh ra on dinh thu tu`() {
        assertEquals(SherpaBiasing.hotwordsFile(), SherpaBiasing.hotwordsFile(), "hai lượt sinh phải giống hệt để diff được")
    }

    /** T4 của spec: dump tệp thật để chạy lại ma trận host trên ĐÚNG tệp Kotlin sinh (không tái dựng bằng Python). */
    @Test
    fun `dump tep hotwords that ra build de do tren host`() {
        val dir = java.io.File(System.getProperty("user.dir"), "build/hotwords")
        dir.mkdirs()
        java.io.File(dir, "hotwords-phrases.txt").writeText(SherpaBiasing.hotwordsFile())
        java.io.File(dir, "hotwords-phrases-with-places.txt").writeText(SherpaBiasing.hotwordsFile(listOf("Nhà", "Công ty")))
        assertTrue(lines.size in 300..3000, "tệp ${lines.size} dòng — ngoài dải đã đo (756–1440 dòng cụm ổn)")
    }

    @Test
    fun `dau cau tach nhan thanh nhieu hotword, khong giet ca cum`() {
        assertEquals(listOf("KHOÁ", "MỞ KHOÁ"), SherpaHotwords.phrasesOf("Khoá / mở khoá"))
        assertEquals(listOf("PIN", "SOC"), SherpaHotwords.phrasesOf("Pin (SOC)"))
        assertEquals(listOf("MỞ CỬA", "ĐÈN ĐỌC"), SherpaHotwords.phrasesOf("Mở cửa + đèn đọc"))
        // Gạch nối/chấm nằm TRONG một cách gọi ⇒ chỉ ngắt từ, không tách hotword.
        assertEquals(listOf("KÍNH TRƯỚC TRÁI"), SherpaHotwords.phrasesOf("Kính trước-trái"))
        assertEquals(listOf("ÁP LỐP TRƯỚC TRÁI"), SherpaHotwords.phrasesOf("Áp lốp trước-trái"))
        // Chữ số bỏ theo TOKEN, phần chữ vẫn dùng được.
        assertEquals(listOf("BỤI MỊN"), SherpaHotwords.phrasesOf("Bụi mịn PM2.5"))
        assertEquals(listOf("ẮC QUY"), SherpaHotwords.phrasesOf("Ắc-quy 12V"))
        assertEquals(listOf("CAMERA"), SherpaHotwords.phrasesOf("Camera 360"))
    }

    @Test
    fun `bon cau nghe sai trong luot E2E deu la cum trong tap hotword`() {
        // [ĐO] emulator-voice-e2e-2026-09-15 §2 T2 w04/w09/w12/w13 — đúng bốn CỤM mà ma trận host 09-16 chốt là
        // được sửa khi (và chỉ khi) chúng đứng thành cụm, không có từ rời bên cạnh.
        // `BẬT ĐIỀU HOÀ` cố ý KHÔNG có: nó là tiền tố của `BẬT ĐIỀU HOÀ TỰ ĐỘNG` (luật dropPrefixes) — `BẬT MÁY LẠNH` thay.
        // `MỞ CỬA SỔ` cũng là tiền tố (`MỞ CỬA SỔ NÓC`) ⇒ `MỞ CÁC CỬA SỔ` thay.
        // ⚠ 1.90 2026-09-21: «TĂNG ÂM LƯỢNG» rời danh sách vì nút `vol` bị owner xoá ⇒ không còn nhãn/cách nói nào
        // sinh ra cụm ấy. Ca ĐÃ ĐO w12 vì thế không còn đối tượng; năm cụm còn lại vẫn là năm ca đo thật.
        listOf("XEM PIN", "DỪNG NHẠC", "MỞ KÍNH TRƯỚC TRÁI", "BẬT MÁY LẠNH", "MỞ CÁC CỬA SỔ")
            .forEach { assertTrue(it in hotwords, "thiếu cụm «$it» — xem spec kachi-voice-hotword-phrases") }
        // ⚠ 1.94 2026-09-22: «MỞ KHOÁ CỬA» rời bài vì nút `door` (mở khoá cửa) đã gỡ (NOT_PROVISIONED, owner "bỏ
        // hẳn") ⇒ không còn nhãn/cách nói nào sinh ra cụm ấy. Năm cụm trên vẫn là ca đo thật.
    }

    /**
     * ═══ CHỖ ĐẶT DẤU THANH — `KHOÁ → KHÓA` · `HOÀ → HÒA` · `KHOẺ → KHỎE`, chỉ ở **âm tiết MỞ** ═══════════════
     *
     * [ĐO] `docs/diagnostics/voice-mishear-2026-09-16.md` §8: soát tệp `hotwords-phrases.txt` (1757 dòng) thấy
     * **54 dòng** viết kiểu CŨ, và `tokens.txt` của mô hình **không có** `KHOÁ`/`HOÀ`/`KHOẺ` mà chỉ có dạng mới.
     *
     * ⚠⚠ Nói thẳng con số để không ai bán nhầm bản vá này: **KHÔNG đổi độ chính xác**. Cùng §1 của tài liệu ấy,
     * `hotwords-tonefix.txt` cho **đúng** 935/1899 như `hotwords-phrases.txt` — tức giả thuyết *"sai chỗ đặt dấu
     * ⇒ hotword vô hiệu"* đã **bị bác** (BPE vẫn ghép được từ mảnh). Đây là bản vá **chính tả**: viết đúng kiểu
     * mà từ điển mô hình dùng, để lần sau không ai phải soát lại.
     *
     * ⚠ `HOÀN` · `NGOÀI` · `TOÀN` · `THOÁNG` là âm tiết **ĐÓNG** (còn phụ âm cuối) — ở đó cả hai quy ước đều đặt
     * dấu trên `à`/`á`, nên chúng KHÔNG được đụng tới. Bài này canh đúng ranh giới ấy.
     */
    @Test
    fun `luat dat dau chi ap cho am tiet MO, khong dung vao HOAN NGOAI TOAN`() {
        val oldStyle = Regex("(khoá|hoà|khoẻ)(?![a-zà-ỹ])", RegexOption.IGNORE_CASE)
        SherpaSpokenWords.ACCENTED.values.forEach { v ->
            assertTrue(oldStyle.find(v) == null, "«$v» còn viết kiểu cũ ở âm tiết MỞ — đổi sang khóa/hòa/khỏe")
        }
        // Chốt ngược: âm tiết ĐÓNG vẫn phải giữ nguyên, nếu không bản vá đã đi quá tay.
        assertEquals("tuần hoàn trong", SherpaSpokenWords.ACCENTED["tuan hoan trong"], "HOÀN là âm tiết đóng")
        listOf("hoàn", "ngoài", "toàn", "thoáng").forEach {
            assertTrue(oldStyle.find(it) == null, "«$it» là âm tiết ĐÓNG — luật không được đụng vào")
        }
    }

    /**
     * ═══ [SOÁT 1.69 · P3] …và luật ấy phải áp cho **NHÃN BỘ ĐĂNG KÝ**, không chỉ cho bảng [ACCENTED] ══════
     *
     * Bài trên quét `SherpaSpokenWords.ACCENTED`. Nhưng bảng hotword **sinh ra từ nhãn** của bốn bộ đăng ký —
     * nên một nhãn mới viết `Khoá xe` sẽ đi thẳng vào tệp hotword mà bài trên không thấy gì. [ĐO 2026-09-17]
     * nhãn hôm nay đã sạch (lượt sửa chính tả của 1.69 quét cả registry), nhưng *"sạch hôm nay"* không phải
     * một bài canh — và đây đúng là loại lỗi **im lặng**: hotword vẫn sinh ra, biasing vẫn bật, chỉ là cụm ấy
     * không bao giờ kéo được câu nào về. Ghi trong bộ nhớ dự án `kachi-voice-hotwords-accented`.
     *
     * Quét cả VI lẫn EN của cả bốn bộ; `labelEn`/nhãn Anh không chứa dấu nên chúng vô hại với biểu thức này.
     */
    @Test
    fun `nhan bo dang ky cung theo luat dat dau moi`() {
        val oldStyle = Regex("(khoá|hoà|khoẻ)(?![a-zà-ỹ])", RegexOption.IGNORE_CASE)
        val labels = buildList<Pair<String, String>> {
            ControlRegistry.ALL.forEach { add(it.id to it.label); add(it.id to it.short.orEmpty()) }
            TelemetryRegistry.ALL.forEach { add(it.id to it.label); add(it.id to it.short.orEmpty()) }
            ActionMacros.ALL.forEach { add(it.id to it.label) }
            LauncherActions.ALL.forEach { add(it.id to it.label) }
        }
        labels.forEach { (id, label) ->
            assertTrue(
                oldStyle.find(label) == null,
                "nhãn «$label» của `$id` viết kiểu cũ ở âm tiết MỞ — đổi sang khóa/hòa/khỏe, nếu không cụm " +
                    "hotword sinh từ nhãn này sẽ không bao giờ kéo được câu nào về (im lặng, không ai đỏ)",
            )
        }
    }

    /**
     * Cảnh báo (KHÔNG đỏ) — mỗi TỪ của mỗi hotword nên mã hoá được bằng token nguyên vẹn của `tokens.txt`.
     *
     * Đây là phép đo mà brief đòi, nhưng nó **không thể** là một bài đỏ: `tokens.txt` nằm trong gói mô hình
     * (~80 MB) mà repo cố ý không giữ, và máy CI/host không có xe để rút ra. Nên bài này tự **bỏ qua** kèm một
     * dòng nói rõ vì sao — im lặng xanh mới là thứ nguy hiểm. Đặt tệp vào `core/src/test/resources/voice/
     * sherpa-tokens.txt` là nó tự chạy.
     */
    @Test
    fun `canh bao chu khong do — hotword ma hoa duoc bang token nguyen ven`() {
        val res = javaClass.classLoader.getResourceAsStream("voice/sherpa-tokens.txt")
        if (res == null) {
            println("[BỎ QUA] không có `voice/sherpa-tokens.txt` off-car ⇒ chưa soát được chỗ đặt dấu so với mô hình")
            return
        }
        val tokens = res.bufferedReader().readLines()
            .mapNotNull { it.trim().substringBefore(' ').removePrefix("▁").takeIf(String::isNotEmpty) }
            .map { it.uppercase() }.toSet()
        val unseen = lines.flatMap { it.split(' ') }.distinct().filterNot { it in tokens }
        if (unseen.isNotEmpty()) println("[CẢNH BÁO] ${unseen.size} từ không phải token nguyên vẹn: ${unseen.take(20)}")
    }

    @Test
    fun `hotword luon HOA co dau va khong mang chu so`() {
        hotwords.forEach { hw ->
            assertEquals(hw.uppercase(), hw, "hotword phải viết HOA — chữ thường bị native bỏ")
            assertTrue(hw.none { it.isDigit() }, "hotword còn chữ số: «$hw»")
            assertTrue(hw.none { !it.isLetter() && it != ' ' }, "hotword còn ký tự lạ: «$hw»")
            assertTrue(hw.length >= 2, "hotword quá ngắn: «$hw»")
        }
    }

    // ── [SherpaSpokenWords] không được lệch khỏi từ vựng thật ────────────────────────────────────

    /**
     * ⚠ H3 (2026-09-16) — [VoiceSynonyms.APP_TARGETS] nay **nằm trong** phép canh này.
     *
     * Trước đó nó là bảng duy nhất của [VoiceSynonyms] không bị soi, và chỗ hụt ấy không phải lý thuyết: bảy app
     * đích có 14 cách nói mà **không cách nào** có dạng có dấu ⇒ không cách nào vào tệp hotword, trong khi [ĐO]
     * `voice-mishear-2026-09-16.md` §3 nói loại ý định `app` là loại tệ nhất bảng (12,8 %). Đưa nó vào đây nghĩa
     * là: thêm một cách gọi app mà quên khai dạng đọc ⇒ bài này ĐỎ, không im lặng.
     */
    private fun synonymPhrases(): List<String> =
        VoiceSynonyms.CONTROL.values.flatten() +
            VoiceSynonyms.TELEMETRY.values.flatten() +
            VoiceSynonyms.APP_TARGETS.values.flatten() +
            VoiceSynonyms.MEDIA_WORDS +
            VoiceSynonyms.NAV_WORDS

    @Test
    fun `moi cum trong VoiceSynonyms deu co dang co dau hoac duoc khai la khong co`() {
        val undeclared = synonymPhrases().distinct()
            .filterNot { it in SherpaSpokenWords.ACCENTED || it in SherpaSpokenWords.NO_VI_FORM }
        assertEquals(
            emptyList<String>(),
            undeclared,
            "thêm cách nói mà quên dạng có dấu ⇒ cụm đó KHÔNG được bias, và sự thiếu ấy im lặng",
        )
    }

    @Test
    fun `dang co dau phai bo dau ra dung khoa cua no`() {
        SherpaSpokenWords.ACCENTED.forEach { (plain, accented) ->
            assertEquals(
                plain,
                VoiceLexicon.deaccent(accented),
                "«$accented» không phải bản CÓ DẤU của «$plain» — bảng này chỉ được viết lại dấu, không được đẻ cách nói mới",
            )
        }
    }

    @Test
    fun `khong co muc chet trong SherpaSpokenWords`() {
        val declared = synonymPhrases().toSet()
        val dead = (SherpaSpokenWords.ACCENTED.keys + SherpaSpokenWords.NO_VI_FORM).filterNot { it in declared }
        assertEquals(emptyList<String>(), dead, "cụm không còn trong VoiceSynonyms ⇒ phải xoá khỏi bảng có dấu")
    }

    @Test
    fun `moi dong tu deu co it nhat mot dang co dau, va dang do la cum da khai`() {
        val byVerb = VoiceGrammar.VERBS.groupBy({ it.second }, { it.first.joinToString(" ") })
        VoiceVerb.entries.forEach { v ->
            val spoken = SherpaSpokenWords.VERBS[v].orEmpty()
            assertTrue(spoken.isNotEmpty(), "động từ $v chưa có dạng có dấu ⇒ không bao giờ được bias")
            spoken.forEach { form ->
                assertTrue(
                    VoiceLexicon.deaccent(form) in byVerb[v].orEmpty(),
                    "«$form» không phải một cụm đã khai cho $v trong VoiceGrammar.VERBS",
                )
            }
        }
    }

    @Test
    fun `dang co dau cua dong tu va cach noi doi thuong deu nam TRONG mot cum cua tep hotwords`() {
        // Vòng 2: không còn là "có mặt như một dòng" (từ rời bị cấm) mà là "nằm trong ít nhất một cụm".
        //
        // ⚠ 1.69 — trừ ra **đúng** những cách gọi app mà [SherpaPhraseHotwords.notBiasedAppNames] nói là cố ý
        // không bias (bí danh nối dài bí danh của một app KHÁC: *"youtube nhạc"* ⊃ *"youtube"*, *"bản đồ Việt"* ⊃
        // *"bản đồ"*). [ĐO máy ảo] để chúng vào tệp làm ca `w10` (*"đưa YouTube vào ô số hai"*) tụt xuống
        // *"đưa youtube"* — mất mệnh đề ô. Lý do đầy đủ ở KDoc hàm ấy.
        //
        // Trừ bằng **danh sách máy sinh**, không phải ba chuỗi viết tay: nếu mai có ai gỡ luật đó thì danh sách
        // rỗng và bài này lại đòi đủ như cũ; nếu có thêm một bí danh cùng hình dạng thì nó tự được tha. Cái bài
        // này vẫn canh được là *"quên khai"* — thứ duy nhất nó sinh ra để bắt.
        val deliberate = SherpaPhraseHotwords.notBiasedAppNames()
            .flatMap { SherpaHotwords.phrasesOf(it) }
            .toSet()
        val missing = SherpaSpokenWords.ALL
            .flatMap { SherpaHotwords.phrasesOf(it) }
            .filterNot { it in deliberate }
            .filterNot { p -> lines.any { containsWords(it, p) } }
        assertEquals(emptyList<String>(), missing, "khai dạng có dấu mà không cụm nào mang nó ⇒ hụt ở SherpaPhraseHotwords")
        // Chiều ngược lại: danh sách trừ không được phép rữa thành một tấm chăn. Mỗi mục của nó phải THẬT SỰ
        // vắng khỏi tệp — còn ở trong tệp mà vẫn nằm trong danh sách trừ nghĩa là luật đã đổi mà danh sách thì không.
        val stillPresent = deliberate.filter { p -> lines.any { containsWords(it, p) } }
        assertEquals(
            emptyList<String>(), stillPresent,
            "cụm khai là 'cố ý không bias' mà vẫn nằm trong tệp hotword ⇒ danh sách trừ đang nói dối",
        )
    }
}
