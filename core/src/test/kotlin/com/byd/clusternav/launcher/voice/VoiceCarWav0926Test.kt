package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ BA BỆNH CỦA BUỔI XE 26/09 — khoá bằng ĐÚNG chuỗi mà mô hình đã in ra ════════════════════════════════════
 *
 * Nguồn: 30 bản thu thật của xe (`kachi-voice-20260926-185812-365.zip`, **cục bộ, không commit**) phát lại off-car
 * qua `cmd wav` trên máy ảo API 29 với **cùng** mô hình `zipformer-vi-int8-2025-04-20`. Chuỗi trong bài này là
 * chuỗi **mô hình in ra**, chép nguyên văn — không phải câu người ta nói. Đo chi tiết:
 * `docs/diagnostics/offcar-2026-09-26/voice-tail-fuzzy-phonetic.md`.
 *
 * Ba họ:
 *  • VOICE-SLOT-TAIL-CUT — vế *"vào ô số N"* mất ở tầng NGHE (chữa bằng hotword, [VoiceSlotPhrases]); ở tầng CHỮ
 *    thì câu đầy đủ **vốn đã** hiểu đúng, và bài `cau day du van ra dung o` khoá điều đó để bản vá NGHE không
 *    lặng lẽ kéo tầng chữ theo.
 *  • VOICE-APP-NAME-FUZZY — tên app bị bóp méo ([VoiceLastResort]).
 *  • VOICE-PROFILE-NAME-PHONETIC — tên hồ sơ rụng/bóp méo ([VoiceProfileNames] · [VoiceClarify]).
 */
class VoiceCarWav0926Test {

    private val apps = listOf("YouTube", "YouTube Music", "Cài đặt", "Bản đồ")
    private val profiles = listOf("Mặc định", "Mặc định 2")

    private fun one(text: String, apps: List<String> = this.apps, profiles: List<String> = emptyList()) =
        VoiceIntentParser.parseOne(text, profiles = profiles, apps = apps)

    // ── VOICE-SLOT-TAIL-CUT · tầng CHỮ vốn đã đúng ───────────────────────────────────────────────

    @Test
    fun `cau day du van ra dung o`() {
        // [ĐO máy ảo 2026-09-26] khi mô hình nghe đủ (`…-184821`, `…-183051`) thì bộ phân tích ra đúng ô ⇒ bệnh
        // nằm ở tầng NGHE, không ở tầng chữ. Bài này là cái chốt để bản vá hotword không được đổi tầng chữ.
        assertEquals(VoiceIntent.OpenApp("YouTube", 2), one("mở youtube vào ô số hai"))
        assertEquals(VoiceIntent.OpenApp("YouTube", 1), one("mở youtube vào ô số một"))
        val vm = one("mở vietmap vào ô số hai")
        assertEquals("VietMap", (vm as VoiceIntent.OpenApp).appName)
        assertEquals(2, vm.slot)
    }

    // ── VOICE-APP-NAME-FUZZY ─────────────────────────────────────────────────────────────────────

    @Test
    fun `ten app bi ASR bop meo van ra dung app va dung o`() {
        // [ĐO xe 2026-09-26 · log KachiVoiceSession] ba chuỗi thật; hai chuỗi dưới trước bản này ra NO_OBJECT/MISMATCH.
        val yt = one("mở youtubex vào ô hai")
        assertEquals("YouTube", (yt as VoiceIntent.OpenApp).appName, "youtubex lệch 1 ký tự đuôi ⇒ YouTube")
        assertEquals(2, yt.slot, "mệnh đề ô KHÔNG được bị nuốt vào tên app")

        val vm = one("đặt vietp vào ô số một")
        assertEquals("VietMap", (vm as VoiceIntent.OpenApp).appName, "vietp lệch 2 ký tự ⇒ VietMap")
        assertEquals(1, vm.slot)
    }

    @Test
    fun `khop mo KHONG duoc doan khi hai app khac nhan cung gan`() {
        // Hai app khác nhãn, cùng neo tiền tố, cùng lệch ≤ 2 ⇒ nhập nhằng ⇒ KHÔNG chọn (rơi về không hiểu ⇒ hỏi lại).
        val got = one("mở netfliy", apps = listOf("Netflix", "Netflax"))
        assertTrue(got is VoiceIntent.Unknown, "nhập nhằng mà vẫn mở một app là mở NHẦM app: $got")
    }

    @Test
    fun `cum ngan va cau lenh xe KHONG bi bien thanh ten app`() {
        // Sàn 5 ký tự: *"wazi"* không được thành Waze.
        assertTrue(one("mở wazi", apps = listOf("Waze")) is VoiceIntent.Unknown)
        // Câu lệnh xe thật vẫn là lệnh xe — đường mờ chỉ chạy khi KHÔNG cách hiểu nào có nghĩa.
        assertTrue(one("mở cửa sổ trời") is VoiceIntent.Control)
        assertTrue(one("mở kính lái") is VoiceIntent.Control)
        assertTrue(one("bật đèn đọc") is VoiceIntent.Control)
        // Điểm đến vẫn nguyên văn, không bị cắt thành một cái tên app.
        val nav = one("dẫn đường đến công ty một")
        assertTrue(nav is VoiceIntent.Nav && nav.query.contains("công ty"), "điểm đến bị đổi: $nav")
    }

    // ── VOICE-PROFILE-NAME-PHONETIC ──────────────────────────────────────────────────────────────

    @Test
    fun `ten ho so rung mot tu van doi dung ho so`() {
        // [ĐO xe 2026-09-26] *"chuyển sang hồ sơ định"* (rụng chữ *"Mặc"*) — trước bản này ra MISMATCH.
        assertEquals(VoiceIntent.Profile("Mặc định"), one("chuyển sang hồ sơ định", profiles = profiles))
        assertEquals(VoiceIntent.Profile("Mặc định"), one("đổi sang hồ sơ định", profiles = profiles))
    }

    @Test
    fun `ten ho so nhap nhang hoac khong co cum danh dau thi KHONG doi`() {
        // *"mặc"* là tiền tố của CẢ HAI hồ sơ ⇒ nhập nhằng ⇒ không đổi (đổi hồ sơ tài xế là việc không được đoán).
        assertTrue(one("chuyển sang hồ sơ mặc", profiles = profiles) !is VoiceIntent.Profile)
        // Không có cụm đánh dấu *"hồ sơ"* ⇒ đường mờ không chạy.
        assertTrue(one("chuyển sang định", profiles = profiles) !is VoiceIntent.Profile)
        // Câu lệnh xe không bị đường này đụng tới.
        assertTrue(one("mở cửa sổ trời", profiles = profiles) is VoiceIntent.Control)
    }

    @Test
    fun `thieu han ten ho so thi HOI LAI, khong tra cau khong hieu`() {
        // [ĐO xe 2026-09-26] 8/8 lượt mô hình in ra đúng hai chuỗi này (mất tên hồ sơ *"Test"*).
        val terms = VoiceGrammar.terms(profiles = profiles, apps = apps)
        listOf("chuyển sang hồ sơ", "đổi sang hồ sơ").forEach { text ->
            val got = one(text, profiles = profiles)
            assertTrue(got is VoiceIntent.Unknown, "$text ⇒ $got")
            val unknown = got as VoiceIntent.Unknown
            val ask = requireNotNull(VoiceClarify.ask(unknown, round = 0, terms = terms)) { "$text phải hỏi lại tên hồ sơ" }
            assertTrue(ask.question.contains("Mặc định"), "câu hỏi phải nêu hồ sơ có thật: ${ask.question}")
            assertTrue(ask.carry.isNotEmpty(), "phải mang theo vế đã hiểu để ghép với câu trả lời")
            // Ghép lại phải phân tích được ⇒ lượt hỏi thật sự dẫn tới đâu đó.
            val joined = VoiceClarify.combine(ask.carry, "Mặc định 2")
            assertEquals(VoiceIntent.Profile("Mặc định 2"), one(joined, profiles = profiles), "ghép: «$joined»")
        }
    }

    @Test
    fun `mot ho so thi khong hoi — khong co gi de chon`() {
        val terms = VoiceGrammar.terms(profiles = listOf("Mặc định"), apps = apps)
        val got = one("chuyển sang hồ sơ", profiles = listOf("Mặc định")) as VoiceIntent.Unknown
        val ask = VoiceClarify.ask(got, round = 0, terms = terms)
        assertTrue(ask == null || !ask.question.contains("Mặc định hay"), "một hồ sơ thì không có gì để hỏi: $ask")
    }
}
