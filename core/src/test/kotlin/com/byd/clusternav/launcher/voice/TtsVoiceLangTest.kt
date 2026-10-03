package com.byd.clusternav.launcher.voice

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ Soát 2.87 · voice P2 — máy đọc hệ thống của phiên `:wake` DÙNG LẠI phải theo tiếng của TỪNG câu ══════════════
 *
 * [ĐO mã] Trước bản vá: `AndroidTtsSpeaker.configure` đọc tiếng giọng nói MỘT lần lúc `onInit`; phiên WAKE/HOLD sống
 * suốt đời tiến trình `:wake` ⇒ đổi English → Tiếng Việt thì câu tiếng Việt vẫn đọc bằng giọng Anh, và
 * `VoiceSpeakerRouter.probe` vẫn chọn đường đọc theo số `isLanguageAvailable` của tiếng cũ. Bài này lật tiếng của
 * "ảnh chụp" giữa hai lượt và đòi engine được hỏi + đặt đúng Locale mới, số trạng thái là của tiếng mới. Dây nối phía
 * `:app` (mọi cửa đọc/chọn đi qua [TtsVoiceLang.sync]) ở `VoiceLangWiringContractTest`.
 */
class TtsVoiceLangTest {

    /** Engine giả: ghi mọi lượt hỏi/đặt; [voices] = Locale nào có giọng (0), còn lại thiếu dữ liệu (−1). */
    private class FakeEngine(val voices: Set<Locale>) : TtsVoiceLang.Port {
        val asked = mutableListOf<Locale>()
        val set = mutableListOf<Locale>()
        override fun isLanguageAvailable(locale: Locale): Int { asked += locale; return if (locale in voices) 0 else -1 }
        override fun setLanguage(locale: Locale) { set += locale }
    }

    private val vi = Locale.forLanguageTag("vi")

    @Test
    fun `doi tieng giua hai cau thi hoi va dat lai dung Locale moi`() {
        var want = Locale.ENGLISH                            // ảnh chụp ngữ pháp lúc dựng phiên: giao diện English
        val voice = TtsVoiceLang { want }
        val engine = FakeEngine(setOf(Locale.ENGLISH, vi))

        val first = voice.sync(engine)
        assertTrue(first.changed && first.usable)
        assertEquals(listOf(Locale.ENGLISH), engine.set)

        val again = voice.sync(engine)                       // cùng tiếng ⇒ một phép so, không gọi engine
        assertFalse(again.changed)
        assertEquals(1, engine.asked.size, "cùng tiếng không được hỏi lại engine mỗi câu")

        want = vi                                            // người dùng đổi sang Tiếng Việt — phiên `:wake` vẫn là phiên cũ
        val now = voice.sync(engine)
        assertTrue(now.changed && now.usable)
        assertEquals(listOf(Locale.ENGLISH, vi), engine.set, "câu tiếng Việt phải đọc bằng giọng Việt")
        assertEquals(vi, now.locale)
        assertEquals(0, voice.status())
    }

    @Test
    fun `tieng moi khong co giong thi khong dat, so trang thai la cua tieng moi`() {
        var want = vi
        val voice = TtsVoiceLang { want }
        val engine = FakeEngine(setOf(vi))                   // chỉ có giọng Việt
        assertTrue(voice.sync(engine).usable)
        want = Locale.ENGLISH
        val r = voice.sync(engine)
        assertFalse(r.usable, "không có giọng Anh ⇒ bộ chọn phải thấy −1 (sang Piper/im lặng), không phải 0 của tiếng cũ")
        assertEquals(-1, voice.status())
        assertEquals(listOf(vi), engine.set, "không setLanguage cho tiếng thiếu dữ liệu")
        want = vi
        assertTrue(voice.sync(engine).usable, "đổi lại thì dùng được lại — không kẹt ở trạng thái hỏng")
    }

    @Test
    fun `engine nem hoac lambda hong thi khong sap`() {
        val dead = object : TtsVoiceLang.Port {
            override fun isLanguageAvailable(locale: Locale): Int = error("engine chết")
            override fun setLanguage(locale: Locale) = error("engine chết")
        }
        val r = TtsVoiceLang { vi }.sync(dead)
        assertNull(r.status)
        assertFalse(r.usable)
        val broken = TtsVoiceLang { error("ảnh chụp hỏng") }.sync(FakeEngine(setOf(vi)))
        assertFalse(broken.changed || broken.usable, "lambda hỏng ⇒ không đoán tiếng, không đặt gì")
        assertNull(broken.locale)
    }

    /**
     * Soát vòng 2 [P3] — `LANG_NOT_SUPPORTED` (−2) lúc engine đang nối lại (AOSP r47 `TextToSpeech.java:2300-2321`: chưa nối ⇒
     * errorResult) KHÔNG được nhớ như đáp án cuối. Bản cũ: lượt sau cùng Locale trả −2 mãi (đường Android chết tới lần đổi
     * tiếng sau). Nay: trong [TtsVoiceLang.RECHECK_MS] chỉ một phép so; quá nhịp ⇒ hỏi lại, engine đã nối ⇒ dùng được + đặt tiếng.
     */
    @Test
    fun `loi tam cua engine khong bi nho vinh vien - hoi lai sau nhip`() {
        var clock = 1_000L
        val answers = ArrayDeque(listOf(-2, 0))
        val asked = mutableListOf<Locale>()
        val set = mutableListOf<Locale>()
        val flaky = object : TtsVoiceLang.Port {
            override fun isLanguageAvailable(locale: Locale): Int { asked += locale; return answers.removeFirstOrNull() ?: 0 }
            override fun setLanguage(locale: Locale) { set += locale }
        }
        val voice = TtsVoiceLang(nowMs = { clock }) { vi }
        val first = voice.sync(flaky)
        assertFalse(first.usable, "engine đang nối lại ⇒ lượt này chưa dùng được")
        assertTrue(set.isEmpty())
        clock += TtsVoiceLang.RECHECK_MS - 1
        assertFalse(voice.sync(flaky).usable)
        assertEquals(1, asked.size, "trong nhịp: một phép so, không gọi engine mỗi câu")
        clock += 1
        val again = voice.sync(flaky)
        assertTrue(again.usable, "quá nhịp ⇒ hỏi lại, engine đã nối ⇒ dùng được (bản cũ: −2 mãi)")
        assertTrue(again.changed, "số đổi ⇒ chỗ gọi ghi log")
        assertEquals(listOf(vi), set, "dùng được thì đặt tiếng")
        assertEquals(0, voice.status())
        clock += 60_000L
        assertFalse(voice.sync(flaky).changed)
        assertEquals(2, asked.size, "số dùng được là đáp án cuối — không hỏi lại nữa")
    }

    @Test
    fun `tieng that su khong co giong - hoi lai toi da moi nhip, khong lap log`() {
        var clock = 0L
        val engine = FakeEngine(emptySet())                   // máy không có giọng nào ⇒ −1 mãi
        val voice = TtsVoiceLang(nowMs = { clock }) { vi }
        assertTrue(voice.sync(engine).changed)
        repeat(20) { clock += 100; voice.sync(engine) }       // 2 s nhiều câu ⇒ không hỏi thêm
        assertEquals(1, engine.asked.size)
        clock += TtsVoiceLang.RECHECK_MS
        val r = voice.sync(engine)
        assertEquals(2, engine.asked.size, "quá nhịp ⇒ hỏi lại đúng một lần")
        assertFalse(r.changed, "vẫn −1 ⇒ không phải 'đổi' (không lặp dòng log)")
        assertFalse(r.usable)
    }
}
