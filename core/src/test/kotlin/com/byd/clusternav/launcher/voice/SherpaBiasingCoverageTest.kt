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
 */
class SherpaBiasingCoverageTest {

    private val hotwords: Set<String> =
        SherpaBiasing.hotwordsFile().trimEnd().split("\n").filter { it.isNotBlank() }.toSet()

    /** Mọi nhãn tiếng Việt của 4 bộ đăng ký — đúng tập mà `VoicePhrases`/`VoiceGrammar` cũng phủ. */
    private fun allLabels(): List<String> =
        ControlRegistry.ALL.map { it.label } +
            TelemetryRegistry.ALL.map { it.label } +
            ActionMacros.ALL.map { it.label } +
            LauncherActions.ALL.map { it.label }

    @Test
    fun `moi nhan trong danh muc deu sinh it nhat mot hotword`() {
        val lost = allLabels().filter { SherpaHotwords.phrasesOf(it).isEmpty() }
        assertEquals(emptyList<String>(), lost, "nhãn không ra được hotword nào ⇒ nút/datum đó không được bias")
    }

    @Test
    fun `hotword cua moi nhan deu co mat trong tep hotwords`() {
        val missing = allLabels().flatMap { label ->
            SherpaHotwords.phrasesOf(label).filterNot { it in hotwords }.map { "$label ⇒ $it" }
        }
        assertEquals(emptyList<String>(), missing, "nhãn sinh được hotword mà tệp lại không có ⇒ hụt ở tầng ghép")
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
    fun `ba cum nghe sai trong luot E2E deu co trong tap hotword`() {
        // [ĐO] emulator-voice-e2e-2026-09-15 §2 T2 w04/w09/w12 — đúng ba cụm đã làm câu lệnh ra Unknown.
        listOf("PIN", "KÍNH", "KÍNH TRƯỚC TRÁI", "DỪNG", "NHẠC", "MỞ KHOÁ", "ĐIỀU HOÀ", "CỬA SỔ", "XEM")
            .forEach { assertTrue(it in hotwords, "thiếu hotword «$it» — xem §3 L3 của lượt đo") }
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
    fun `dang co dau cua dong tu va cach noi doi thuong deu vao duoc tep hotwords`() {
        val missing = SherpaSpokenWords.ALL
            .flatMap { SherpaHotwords.phrasesOf(it) }
            .filterNot { it in hotwords }
        assertEquals(emptyList<String>(), missing, "khai dạng có dấu mà tệp hotwords không có ⇒ hụt ở SherpaBiasing")
    }
}
