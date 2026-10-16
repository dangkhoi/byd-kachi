package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 3.1.10 · VOICE-MEDIA-VIDEO-WORD + VOICE-QUESTION-TAIL-GI — `docs/diagnostics/voice-oncar-2026-10-16.md` §1c ═══════════════════
 *
 * Cùng họ với [VoiceCarLog1016Test] (câu *"&lt;từ-nhạc&gt; &lt;tên&gt; trên &lt;app nhạc&gt;"* không động từ): (a) *"video"* là một từ chỉ loại
 * như *"nhạc"*; (b) câu HỎI có chữ *"gì"* không được thành lệnh phát/tìm. Tên bài/video ở đây là TỔNG HỢP (chỉ giữ hình dạng câu).
 * Mỗi ca nói nó khoá gì (CLAUDE.md §10).
 */
class VoiceVideoWhatQuestionTest {

    @AfterEach fun resetLang() { Strings.current = Lang.VI }

    private val apps = listOf("YouTube", "Spotify", "VietMap")

    private fun parse(s: String) = VoiceIntentParser.parse(s, apps = apps)

    private fun isMediaAction(i: VoiceIntent) =
        i is VoiceIntent.Media && (i.op == VoiceMediaOp.QUERY || i.op == VoiceMediaOp.PLAY)

    // ══ (a) "video" = từ chỉ loại ═══════════════════════════════════════════════════════════════════════════════════════════

    /** Red-proof: gỡ `"video"` khỏi [VoiceSynonyms.MEDIA_WORDS] ⇒ ba câu đầu `NO_VERB`, câu thứ tư QUERY "video". */
    @Test fun `video la tu chi loai nhu nhac - khong dong tu, co dong tu, phat tiep`() {
        mapOf(
            "video Mưa Phố Cũ trên youtube" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Mưa Phố Cũ", VoiceAppTargets.YOUTUBE),
            "mở video Mưa Phố Cũ trên youtube" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Mưa Phố Cũ", VoiceAppTargets.YOUTUBE),
            "video trên youtube" to VoiceIntent.Media(VoiceMediaOp.PLAY, app = VoiceAppTargets.YOUTUBE),
            "phát video" to VoiceIntent.Media(VoiceMediaOp.PLAY),
            "tắt video" to VoiceIntent.Media(VoiceMediaOp.PAUSE),
            "dừng video" to VoiceIntent.Media(VoiceMediaOp.PAUSE),
        ).forEach { (s, want) -> assertEquals(listOf(want), parse(s), "câu: \"$s\"") }
    }

    /** Không cướp ý định khác: thiếu app nhạc ⇒ vẫn hỏi lại; câu điều khiển xe / camera / app không đụng tới. */
    @Test fun `video khong cuop y dinh khac`() {
        listOf("video to quá", "video Mưa Phố Cũ", "video Mưa Phố Cũ trên vietmap").forEach { s ->
            val got = parse(s)
            assertFalse(got.any(::isMediaAction), "câu: \"$s\" ra $got — không có app nhạc thì không được phát")
        }
        // Câu có động từ của họ khác giữ đúng họ của nó.
        assertTrue(parse("mở youtube").single() is VoiceIntent.OpenApp, "${parse("mở youtube")}")
        assertTrue(parse("tắt camera").none(::isMediaAction), "${parse("tắt camera")}")
        assertEquals(parse("phát tiếp nhạc"), parse("phát tiếp video"), "phát tiếp: cùng một ý định với \"nhạc\"")
    }

    // ══ (b) chữ hỏi "gì" ═════════════════════════════════════════════════════════════════════════════════════════════════════

    /** Red-proof: bỏ `?: whatAt(t)` ở [VoiceQuestion.askAt] ⇒ hai câu đầu ra `Media(QUERY "gì đang phát")`. */
    @Test fun `cau hoi co chu gi khong thanh lenh phat hay tim`() {
        listOf(
            "nhạc gì đang phát trên youtube",
            "bài gì đây",
            "video gì đang phát trên youtube",
            "bài hát gì vậy",
            "đang phát bài gì",
            "kachi ơi nhạc gì đang phát trên youtube",
        ).forEach { s ->
            val got = parse(s)
            assertFalse(got.any(::isMediaAction), "câu hỏi \"$s\" ra $got — câu hỏi chỉ được trả lời, không làm")
            assertFalse(got.any(VoiceQuestion::writesToCar), "câu hỏi \"$s\" ra lệnh GHI: $got")
        }
        assertTrue(VoiceQuestion.isQuestion(VoiceLexicon.tokenize("nhạc gì đang phát trên youtube")))
    }

    /** Tên bài có chữ *"gì"* KHÔNG bị coi là câu hỏi — có động từ, hoặc chữ của tên chen giữa từ chỉ loại và *"gì"*. */
    @Test fun `ten bai co chu gi van phat`() {
        mapOf(
            "mở bài Em Làm Gì Đó trên youtube" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Em Làm Gì Đó", VoiceAppTargets.YOUTUBE),
            "phát bài Anh Nói Gì" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Anh Nói Gì"),
            "nhạc Em Làm Gì Đó trên youtube" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Em Làm Gì Đó", VoiceAppTargets.YOUTUBE),
            "video Hỏi Gì Đây trên youtube" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Hỏi Gì Đây", VoiceAppTargets.YOUTUBE),
        ).forEach { (s, want) -> assertEquals(listOf(want), parse(s), "câu: \"$s\"") }
    }

    /** Câu xe có *"gì"* ở cuối đi đường ĐỌC/hỏi lại, không bao giờ ra lệnh ghi; câu lệnh xe thường không đổi. */
    @Test fun `cau xe co gi o cuoi khong ghi, lenh xe thuong khong doi`() {
        listOf("cửa sổ trời là gì", "chế độ lái là gì").forEach { s ->
            assertFalse(parse(s).any(VoiceQuestion::writesToCar), "câu hỏi \"$s\" ra lệnh GHI: ${parse(s)}")
        }
        assertTrue(parse("bật đèn đọc").single() is VoiceIntent.Control, "${parse("bật đèn đọc")}")
    }
}
