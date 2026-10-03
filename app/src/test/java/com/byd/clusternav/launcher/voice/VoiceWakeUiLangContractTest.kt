package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ spec `kachi-i18n-zh-th-ms.html` R9 — TẤM CHỮ của phiên `:wake` theo tiếng NGƯỜI DÙNG, không theo locale MÁY ═══════
 *
 * Em của [VoiceWakeIsolationContractTest] (cùng cách kiểm: quét NGUỒN đã bỏ chú thích — `:wake` là tiến trình riêng,
 * dự án không chạy Robolectric, và đây đúng là loại dây một lượt refactor sau có thể gỡ im lặng: compile xanh, chỉ
 * người dùng English trên xe vi-VN thấy gợi ý *"Đang nghe…"* tiếng Việt cạnh câu trả lời tiếng Anh).
 *
 * Khoá năm điều:
 *  1. phiên `:wake` dựng trên [VoiceWakeUiContext] mang tiếng từ ẢNH CHỤP — còn dispatcher vẫn `ctx = app`;
 *  2. lớp bọc CHỈ đè `getResources()` (dịch vụ hệ thống · `bindService` · prefs · tệp đi thẳng `applicationContext`
 *     như trước — CLAUDE.md §4: phạm vi đổi phải tường minh) và dựng locale qua `LangHost.localized`;
 *  3. phiên DÙNG LẠI được làm mới tiếng ở mỗi lượt phát ([VoiceWakeSessions]), hỏng thì không chặn lượt nói;
 *  4. chữ của phiên đi qua `ctx` của phiên (không vòng qua `applicationContext`, không `LayoutInflater` của app);
 *  5. tệp mới giữ đúng luật `:wake`: không ghi `Strings.current`, không `langMode()`, không DI, không `Prefs.set*`.
 */
class VoiceWakeUiLangContractTest {

    private val v = "src/main/java/com/byd/clusternav/launcher/voice/"
    private fun code(f: String): String = SourceRoots.codeOf(v + f)

    private val ui by lazy { code("VoiceWakeUiContext.kt") }

    @Test
    fun `phien wake dung ctx tai nguyen theo tieng tu anh chup, dispatcher van ctx app`() {
        val build = SourceRoots.body(code("VoiceWakeSessionFactory.kt"), "internal fun VoiceWakeService.buildSession(): VoiceSession {")
        assertTrue(
            build.contains("ctx = VoiceWakeUiContext(app, grammar().uiLang),"),
            "phiên `:wake` phải dựng trên VoiceWakeUiContext với tiếng từ CÙNG ảnh chụp (`grammar()`) mà voiceLang đọc",
        )
        assertTrue(
            Regex("""VoiceWiring\.dispatcher\(\s*ctx = app,""").containsMatchIn(build),
            "dispatcher (xe · mở app · geocoder) không đổi đường: vẫn applicationContext",
        )
        assertEquals(1, Regex("""\bctx = VoiceWakeUiContext\(""").findAll(build).count(), "đúng một phiên, một lớp bọc")
    }

    @Test
    fun `lop boc chi de getResources va dung locale qua LangHost`() {
        assertTrue(ui.contains("internal class VoiceWakeUiContext(app: Context, lang: Lang?) : ContextWrapper(app)"),
            "bọc applicationContext bằng ContextWrapper — không thay cả Context bằng createConfigurationContext")
        val overrides = Regex("""override\s+fun\s+(\w+)""").findAll(ui).map { it.groupValues[1] }.toList()
        assertEquals(listOf("getResources"), overrides,
            "CHỈ được đè getResources(): đè thêm (getSystemService, getApplicationContext…) là đổi đường của micro/máy đọc/prefs")
        val resolve = SourceRoots.body(ui, "private fun resourcesFor(lang: Lang?): Resources")
        assertTrue(resolve.contains("LangHost.localized(baseContext, it)"), "một chỗ dựng cấu hình locale (LangHost), không Locale tự chế")
        assertTrue(resolve.contains("?: baseContext"), "ảnh chụp chưa mang tiếng ⇒ tài nguyên gốc = hành vi cũ")
        assertFalse(ui.contains("Locale("), "không dựng Locale ngoài LangHost")
        val refresh = SourceRoots.body(ui, "fun refresh()")
        assertTrue(refresh.contains("VoiceGrammarSnapshotStore.read(baseContext).uiLang"), "làm mới đọc ẢNH CHỤP trên đĩa, mỗi lần")
    }

    @Test
    fun `phien dung lai duoc lam moi tieng moi luot phat, hong khong chan luot noi`() {
        val mark = SourceRoots.body(code("VoiceWakeSessions.kt"), "private fun mark(")
        assertTrue(mark.contains("if (a.reused) (a.session.ctx as? VoiceWakeUiContext)"), "chỉ phiên DÙNG LẠI cần đọc lại (phiên mới vừa đọc)")
        assertTrue(mark.contains("runCatching { ui.refresh() }"), "lỗi làm mới chữ không được giết lượt nói của người lái")
        assertTrue(mark.indexOf("ui.refresh()") < mark.indexOf("return a"), "làm mới TRƯỚC khi trả phiên cho `start()`")
        // Cả ba đường phát phiên của `:wake` đi qua `mark` (acquire · preempt) — không đường nào bỏ qua lượt làm mới.
        val sessions = code("VoiceWakeSessions.kt")
        assertTrue(SourceRoots.body(sessions, "fun acquire(").contains("mark("), "acquire phải đi qua mark")
        assertTrue(SourceRoots.body(sessions, "fun preempt(").contains("mark("), "preempt phải đi qua mark")
    }

    @Test
    fun `chu cua phien di qua ctx cua phien`() {
        val files = listOf("VoiceSession.kt", "VoiceSessionTurns.kt", "VoiceSessionListen.kt", "VoiceOverlay.kt")
        files.forEach { f ->
            val src = code(f)
            assertFalse(Regex("""applicationContext\s*\.\s*(getString|resources|getText)""").containsMatchIn(src),
                "$f đọc chữ qua applicationContext — vòng qua lớp bọc, tấm chữ `:wake` rơi về locale máy")
            assertFalse(src.contains("LayoutInflater"), "$f dùng LayoutInflater — inflater của app context bỏ qua lớp bọc")
        }
        assertTrue(code("VoiceSession.kt").contains("VoiceOverlay(ctx)"), "tấm chữ dựng bằng ctx của phiên (lớp bọc ở `:wake`)")
        val turns = code("VoiceSessionTurns.kt")
        assertTrue(turns.contains("ctx.getString(R.string.kachi_voice_confirm_yes)"), "nút Đồng ý đọc qua ctx của phiên")
        assertTrue(turns.contains("ctx.getString(R.string.kachi_voice_open_settings)"), "nút Mở Cài đặt đọc qua ctx của phiên")
    }

    @Test
    fun `tep moi giu luat cua wake`() {
        assertFalse(Regex("""Strings\.current\s*=(?!=)""").containsMatchIn(ui), "chỉ LangHost.wrap được ghi Strings.current")
        assertFalse(ui.contains("langMode("), "langMode() TỰ GHI khi migrate — cấm ở `:wake`")
        listOf("AppContainer", "ShellTransport", "WindowCommandDispatcher", "NavRepository", "SimpleCastRuntime", "WorkspacePrefs")
            .forEach { assertFalse(ui.contains(it), "VoiceWakeUiContext chạm `$it` — `:wake` không dựng đồ thị/prefs của launcher") }
        assertFalse(Regex("""Prefs\.set\w*""").containsMatchIn(ui), "`:wake` không ghi clusternav_prefs")
        listOf("VoiceWakeUiContext.kt", "VoiceWakeSessions.kt", "VoiceWakeSessionFactory.kt").forEach { f ->
            val n = SourceRoots.text(v + f).lines().size
            assertTrue(n <= 500, "$f dài $n dòng — trần 500 (CLAUDE.md §4.1)")
        }
    }
}
