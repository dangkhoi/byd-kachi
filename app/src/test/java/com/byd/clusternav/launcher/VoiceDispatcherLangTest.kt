package com.byd.clusternav.launcher

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ spec `kachi-i18n-zh-th-ms.html` R6 (T2) — CẦU GIỌNG NÓI trả lời bằng tiếng GIỌNG NÓI ══════════════════════════
 *
 * Hai hình dạng của sản phẩm, dựng bằng CHÍNH [VoiceDispatcher] (tầng Android thay bằng lambda thuần):
 *  • **tiến trình chính** — không truyền `lang` ⇒ mặc định `voiceLangOf(Strings.current)`: màn ZH/TH/MS nói tiếng Việt
 *    y byte bản VI, màn EN nói tiếng Anh y byte bản EN (hành vi cũ của người dùng English giữ nguyên);
 *  • **`:wake`** — ở đó `Strings.current` luôn là VI mặc định (chỉ `LangHost.wrap` ghi), nên `lang` PHẢI đến từ ảnh
 *    chụp ngữ pháp: người dùng English bấm phím vô-lăng nay nghe tiếng Anh như nút mic (trước bản này: tiếng Việt).
 *
 * Câu nói phủ đủ các nhánh không cần cổng xe: mở app · bố cục (bề mặt không nối ⇒ câu *"chưa đổi được"*) · đọc số
 * liệu (chưa có số ⇒ nhãn + *"chưa đọc được"*) · đổi hồ sơ (nhãn *"Mặc định"* được dịch) · không hiểu · tạm biệt.
 */
class VoiceDispatcherLangTest {

    @AfterEach
    fun restore() { Strings.current = Lang.VI }

    private val sentences = listOf(
        "mở youtube", "mở youtube vào ô số hai", "bố cục hai cột", "xem pin", "đổi sang hồ sơ mặc định",
        "kính hay cửa", "tạm biệt",
    )

    /** Mọi lời đáp của [sentences] với màn = [ui]; [lang] = `null` ⇒ để mặc định (đúng tiến trình chính). */
    private fun replies(ui: Lang, lang: Lang? = null): List<String> {
        Strings.current = ui
        val said = ArrayList<String>()
        val common = Common(said)
        val d = if (lang == null) common.build() else common.build(lang)
        sentences.forEach { s -> d.execute(d.preview(s)) }
        Strings.current = Lang.VI
        return said
    }

    /** Bộ lambda thuần — hai hàm dựng chỉ khác đúng tham số `lang` (để mặc định thật sự được kiểm). */
    private class Common(val said: MutableList<String>) {
        private val st = HomeUiState(profiles = listOf(HomeUiState.DEFAULT_PROFILE, "Vợ"))

        fun build(): VoiceDispatcher = VoiceDispatcher(
            control = { error("không chạm nút xe") }, state = { st }, media = { error("không chạm nhạc") },
            appsByLabel = { mapOf("YouTube" to YT) }, openApp = { true }, openAppList = {}, openSettings = {},
            onSwitchProfile = {}, onListen = {}, confirm = { _, y, _ -> y() }, say = { said += it },
            assignAppToSlot = { _, _ -> true }, sendToApp = { false }, geocode = { null }, mediaPackage = { null },
            background = { it() }, onUi = { it() },
        )

        fun build(lang: Lang): VoiceDispatcher = VoiceDispatcher(
            control = { error("không chạm nút xe") }, state = { st }, media = { error("không chạm nhạc") },
            appsByLabel = { mapOf("YouTube" to YT) }, openApp = { true }, openAppList = {}, openSettings = {},
            onSwitchProfile = {}, onListen = {}, confirm = { _, y, _ -> y() }, say = { said += it },
            assignAppToSlot = { _, _ -> true }, sendToApp = { false }, geocode = { null }, mediaPackage = { null },
            background = { it() }, onUi = { it() }, lang = lang,
        )
    }

    @Test
    fun `tien trinh chinh - man ZH TH MS noi tieng Viet, man EN noi tieng Anh, y byte ban cu`() {
        val vi = replies(Lang.VI)
        val en = replies(Lang.EN)
        assertEquals(sentences.size, vi.size, "mỗi câu một lời đáp: $vi")
        assertTrue(vi != en, "bộ câu không phân biệt VI/EN — bài thử vô nghĩa")
        Lang.entries.forEach { ui ->
            assertEquals(if (ui == Lang.EN) en else vi, replies(ui), "màn $ui")
        }
    }

    @Test
    fun `wake - lang tuong minh thang Strings current mac dinh VI`() {
        val en = replies(Lang.EN)
        val vi = replies(Lang.VI)
        // `:wake`: Strings.current = VI (không ai ghi), tiếng từ ảnh chụp.
        assertEquals(en, replies(Lang.VI, lang = Lang.EN), "người dùng English qua phím vô-lăng phải nghe tiếng Anh")
        assertEquals(vi, replies(Lang.VI, lang = Lang.VI))
        // Và ngược lại: màn đang là tiếng nào cũng không lọt vào câu khi `lang` đã được truyền.
        Lang.entries.forEach { ui -> assertEquals(vi, replies(ui, lang = Lang.VI), "màn $ui, lang VI") }
    }

    @Test
    fun `cau tra loi cua cau la cau that, khong rong`() {
        val vi = replies(Lang.VI)
        assertTrue(vi.none { it.isBlank() }, "$vi")
        assertTrue(vi.any { it.startsWith("✓ Mở ứng dụng YouTube") }, "giàn phải thật sự chạy nhánh mở app: $vi")
        assertTrue(replies(Lang.ZH).any { it.startsWith("✓ Mở ứng dụng YouTube") }, "màn ZH vẫn nói tiếng Việt")
    }

    private companion object {
        const val YT = "com.google.android.youtube"
    }
}
