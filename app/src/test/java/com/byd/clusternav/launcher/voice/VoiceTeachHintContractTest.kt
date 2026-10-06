package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 wave 2A · VOICE review note (d) — câu dẫn hộp dạy tên KHỚP các dòng gợi ý từng lượt (5 thứ tiếng) ═════════════════
 *
 * Senior review VOICE (spec `kachi-293-voice.html` §10, ghi chú (d)): `kachi_vn_dialog_hint` vẫn dặn *"nói “mở …” 2–3 lần"* ngay
 * trên dòng gợi ý của lượt CÓ Ô (VOICE-TEACH-CONTEXT: lượt [TeachSample.SLOT_TAKE] là câu *"đưa … vào ô số hai"*) — hai chỉ dẫn
 * trái nhau trên cùng một hộp. Câu dẫn nay: theo dòng gợi ý từng lượt, lượt 1 *"mở …"*, lượt 2 kèm ô, ít nhất
 * [TeachSample.MIN_SPOKEN_TAKES] lượt (hộp bật Lưu từ lượt đó). Câu mẫu để NÓI giữ tiếng Việt trong “…” ở mọi tiếng
 * (`LauncherI18nLocalesContractTest.SPOKEN_VI_QUOTES`).
 */
class VoiceTeachHintContractTest {

    private val dirs = listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms")

    private fun hint(dir: String, key: String = "kachi_vn_dialog_hint"): String {
        val xml = SourceRoots.text("src/main/res/$dir/strings_kachi.xml")
        return Regex("""<string name="$key">([^<]+)</string>""").find(xml)?.groupValues?.get(1)
            ?: error("$dir: thiếu $key")
    }

    /** Hộp dạy ([com.byd.clusternav.launcher.SettingsVoiceNamesDialog]) hiện ĐÚNG câu dẫn này, kèm nhãn app làm `%1$s`. */
    @Test
    fun `hop day hien cau dan voi nhan app`() {
        val dialog = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SettingsVoiceNamesDialog.kt")
        assertTrue("rows.note(context.getString(R.string.kachi_vn_dialog_hint, label))" in dialog, "SettingsVoiceNamesDialog phải hiện câu dẫn")
    }

    @Test
    fun `cau dan khong con 2-3 lan mo, noi dung so luot toi thieu va luot co o`() {
        assertEquals(2, TeachSample.SLOT_TAKE, "câu dẫn nói 'lần 2 kèm ô' — đổi lượt có ô thì sửa câu ở 5 tiếng")
        dirs.forEach { d ->
            val h = hint(d)
            assertTrue("2–3" !in h && "2-3" !in h, "$d: còn chỉ dẫn cũ '2–3 lần' trái dòng gợi ý lượt có ô: «$h»")
            assertTrue("“mở %1\$s”" in h, "$d: câu mẫu lượt 1 phải là câu tiếng Việt «mở …»: «$h»")
            assertTrue("${TeachSample.MIN_SPOKEN_TAKES}" in h, "$d: phải nói số lượt tối thiểu ${TeachSample.MIN_SPOKEN_TAKES}: «$h»")
        }
    }

    /**
     * Senior review wave 2A Pass 1 [P3] — câu ghi chú của TRANG dạy tên (`kachi_vn_page_note`, `SettingsVoiceNamesPage`, ngay trước
     * khi mở hộp) còn dặn *"nói “mở &lt;tên&gt;” 2–3 lần"* — cùng chỉ dẫn cũ trái lượt CÓ Ô mà hộp đã sửa. Nay cùng giọng với câu
     * dẫn hộp ở 5 thứ tiếng; câu để NÓI vẫn là tiếng Việt trong “…”.
     */
    @Test
    fun `ghi chu trang day ten cung bo chi dan cu 2-3 lan`() {
        val page = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SettingsVoiceNamesPage.kt")
        assertTrue("rows.note(context.getString(R.string.kachi_vn_page_note))" in page, "SettingsVoiceNamesPage phải hiện ghi chú")
        dirs.forEach { d ->
            val n = hint(d, "kachi_vn_page_note")
            assertTrue("2–3" !in n && "2-3" !in n, "$d: ghi chú trang còn chỉ dẫn cũ '2–3 lần': «$n»")
            assertTrue("“mở &lt;tên&gt;”" in n, "$d: câu mẫu lượt 1 phải là câu tiếng Việt «mở …»: «$n»")
        }
    }
}
