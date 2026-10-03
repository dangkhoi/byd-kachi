package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ spec `kachi-i18n-zh-th-ms.html` R6 · §4.4 — tiếng giao diện đi sang `:wake` qua ẢNH CHỤP NGỮ PHÁP ══════════════
 *
 * `:wake` không có `Strings.current` (chỉ `LangHost.wrap` ở tiến trình chính ghi) và không được gọi
 * `WorkspacePrefs.langMode()` (tự ghi khi migrate) ⇒ tệp ảnh chụp là đường DUY NHẤT. Bài này khoá: round-trip đủ năm
 * tiếng · tệp cũ (≤ 2.86, không có bản ghi `lang`) ⇒ VI (đúng hành vi `:wake` trước bản này) · mã lạ ⇒ bỏ bản ghi,
 * không đoán · bản ghi mới không đổi một byte của các bản ghi cũ.
 */
class VoiceGrammarSnapshotLangTest {

    private val base = VoiceGrammarSnapshot(
        profiles = listOf("Mặc định", "Vợ"),
        activeProfile = "Vợ",
        writtenAtMs = 1_758_900_000_000L,
        wake = VoiceWakePrefs(wakeSwitch = true, keyHold = false, confirmIds = setOf("c:sunroof"), navDefault = "gmaps", musicDefault = ""),
    )

    @Test
    fun `round-trip du nam tieng, va tieng giong noi suy dung`() {
        Lang.entries.forEach { ui ->
            val d = VoiceGrammarSnapshot.decode(base.copy(uiLang = ui).encode())
            assertNull(d.problem, "$ui: ${d.problem}")
            assertEquals(base.copy(uiLang = ui), d.snapshot, "$ui: round-trip lệch")
            assertEquals(if (ui == Lang.EN) Lang.EN else Lang.VI, d.snapshot.voiceLang, "$ui: tiếng giọng nói")
        }
    }

    @Test
    fun `tep cu khong co ban ghi lang thi giong noi la VI va tai nguyen giu locale may`() {
        val old = base.encode()   // uiLang = null ⇒ không ghi dòng `lang` — đúng dạng tệp của bản ≤ 2.86
        assertTrue(old.lines().none { it.startsWith("lang\t") }, "uiLang null không được ghi bản ghi rỗng")
        val d = VoiceGrammarSnapshot.decode(old)
        assertNull(d.problem)
        assertNull(d.snapshot.uiLang, "tệp cũ ⇒ không biết tiếng ⇒ `:wake` giữ locale máy cho tài nguyên")
        assertEquals(Lang.VI, d.snapshot.voiceLang, "tệp cũ ⇒ giọng nói VI (hành vi `:wake` trước bản này)")
        assertEquals(Lang.VI, VoiceGrammarSnapshot.EMPTY.voiceLang, "chưa có ảnh chụp ⇒ VI")
    }

    @Test
    fun `ma ngon ngu la thi bo ban ghi, khong doan, va bao ly do`() {
        listOf("xx", "EN", "", "zh-CN").forEach { bad ->
            val raw = base.encode() + "lang\t$bad\n"
            val d = VoiceGrammarSnapshot.decode(raw)
            assertNull(d.snapshot.uiLang, "mã «$bad» không được đoán thành một tiếng")
            assertNotNull(d.problem, "mã «$bad» phải để lại lý do cho nhật ký")
            assertEquals(base.profiles, d.snapshot.profiles, "một bản ghi hỏng không được làm mất phần còn lại")
        }
    }

    /** Bản ghi mới đứng CUỐI và chỉ thêm đúng một dòng: các bản ghi cũ không đổi một byte (khuôn VK4). */
    @Test
    fun `ban ghi lang chi them mot dong, khong doi ban ghi cu`() {
        val without = base.encode()
        val with = base.copy(uiLang = Lang.TH).encode()
        assertEquals(without + "lang\tth\n", with)
    }
}
