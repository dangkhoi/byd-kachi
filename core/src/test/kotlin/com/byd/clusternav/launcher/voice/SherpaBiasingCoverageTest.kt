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
        assertTrue("CHẾ ĐỘ LÁI THỂ THAO" in set); assertTrue("CHẾ ĐỘ LÁI" !in set)
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
        listOf("XEM PIN", "DỪNG NHẠC", "MỞ KÍNH TRƯỚC TRÁI", "TĂNG ÂM LƯỢNG", "MỞ KHOÁ CỬA", "BẬT MÁY LẠNH", "MỞ CÁC CỬA SỔ")
            .forEach { assertTrue(it in hotwords, "thiếu cụm «$it» — xem spec kachi-voice-hotword-phrases") }
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

    private fun synonymPhrases(): List<String> =
        VoiceSynonyms.CONTROL.values.flatten() +
            VoiceSynonyms.TELEMETRY.values.flatten() +
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
        val missing = SherpaSpokenWords.ALL
            .flatMap { SherpaHotwords.phrasesOf(it) }
            .filterNot { p -> lines.any { containsWords(it, p) } }
        assertEquals(emptyList<String>(), missing, "khai dạng có dấu mà không cụm nào mang nó ⇒ hụt ở SherpaPhraseHotwords")
    }
}
