package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.CarStatus
import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.LayoutPreset
import com.byd.clusternav.launcher.MacroResult
import com.byd.clusternav.launcher.MacroStepResult
import com.byd.clusternav.launcher.ProfileNames
import com.byd.clusternav.launcher.Strings
import com.byd.clusternav.launcher.TelemetryReadout
import com.byd.clusternav.launcher.TelemetryRegistry
import com.byd.clusternav.launcher.voiceLangOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ spec `kachi-i18n-zh-th-ms.html` R6 · T2 — CÂU NÓI theo tiếng GIỌNG NÓI, không theo tiếng màn ════════════════
 *
 * Luật: `voiceLangOf(ui)` = EN nếu giao diện EN, còn lại VI — giọng đọc offline duy nhất là Piper tiếng Việt, bộ nhận
 * dạng chỉ có gói tiếng Việt. Mọi hàm dựng câu NÓI/tấm chữ của giọng nói nhận `lang` tường minh; đường nói truyền
 * `voiceLangOf(giao diện)`.
 *
 * ## Hai tính chất được khoá, mỗi cái chặn một họ lỗi
 *  1. **`lang` tường minh THẮNG `Strings.current`** — dựng cùng một câu với cùng `lang` dưới MỌI giá trị của
 *     [Strings.current] phải ra y từng byte. Một lời gọi bên trong quên truyền `lang` (đọc mặc định `Strings.current`)
 *     làm câu đổi theo màn ⇒ đỏ ngay. Đây là bài bắt được lỗi thật: bảng zh/th/ms hôm nay còn RỖNG nên chữ zh lùi về
 *     tiếng Anh — vẫn khác tiếng Việt, nên phép so vẫn cắn (không cần bảng dịch thật).
 *  2. **Giao diện ZH/TH/MS ⇒ câu nói tiếng Việt y byte; giao diện EN ⇒ tiếng Anh y byte** như bản VI/EN cũ, và không
 *     một chữ Hán/Thái nào lọt vào câu đưa cho giọng Việt.
 *
 * Mỗi bài tự trả [Strings.current] về VI (`var` toàn cục rò giữa các bài là họ lỗi "chạy riêng xanh, chạy gói đỏ").
 */
class VoiceSpokenLangTest {

    @AfterEach
    fun restore() { Strings.current = Lang.VI }

    private fun <T> under(ui: Lang, block: () -> T): T {
        val prev = Strings.current
        Strings.current = ui
        try { return block() } finally { Strings.current = prev }
    }

    // ── Bộ mẫu: MỌI ý định mà một phiên nói có thể sinh ra (dựng từ bộ đăng ký, không chép tay) ──────────────────

    private val intents: List<VoiceIntent> by lazy {
        buildList {
            ControlRegistry.ALL.forEach { c ->
                add(VoiceIntent.Control(c.id, 1))
                add(VoiceIntent.Control(c.id, 0))
                add(VoiceIntent.Control(c.id, null, 1))
                add(VoiceIntent.Control(c.id, null, -2))
                if (c.kind == ControlKind.COVER || c.kind == ControlKind.SELECT) add(VoiceIntent.Control(c.id, 2))
            }
            TelemetryRegistry.ALL.forEach { add(VoiceIntent.Read(it.id)) }
            ActionMacros.ALL.forEach { add(VoiceIntent.Macro(it.id)) }
            LauncherActions.ALL.forEach { add(VoiceIntent.Launcher(it.id)) }
            add(VoiceIntent.Profile(HomeUiState.DEFAULT_PROFILE))
            add(VoiceIntent.Profile("Vợ"))
            add(VoiceIntent.Nav("chợ Bến Thành", VoiceAppTargets.GMAPS))
            add(VoiceIntent.NavigateSaved(VoicePlaces.HOME))
            add(VoiceIntent.NavigateSaved(VoicePlaces.WORK, VoiceAppTargets.VIETMAP))
            VoiceMediaOp.entries.forEach { add(VoiceIntent.Media(it, "Diễm Xưa", VoiceAppTargets.YT_MUSIC)) }
            add(VoiceIntent.OpenApp("YouTube", 2))
            LayoutPreset.entries.forEach { add(VoiceIntent.Layout(it)) }
            add(VoiceIntent.EndSession)
            VoiceUnknownReason.entries.forEach { add(VoiceIntent.Unknown(it, "kính hay cửa")) }
            add(VoiceIntent.Unknown(VoiceUnknownReason.FEATURE_GONE, VoiceFeatureGone.ALL.first().words.joinToString(" ")))
        }
    }

    private val target: VoiceAppTarget get() = VoiceAppTargets.byKey(VoiceAppTargets.GMAPS)!!

    /** Mọi câu NÓI của một ý định ở ngôn ngữ [l] — đúng các hàm mà `VoiceDispatcher`/`VoiceSession` gọi. */
    private fun spoken(i: VoiceIntent, l: Lang): List<String> = buildList {
        add(VoiceReply.preview(i, l))
        add(VoiceReply.done(i, l))
        add(VoiceReply.doneConfirmed(i, l))
        add(VoiceReply.failed(i, lang = l))
        add(VoiceReply.busy(i, l))
        add(VoiceReply.cancelled(i, 2, l))
        add(VoiceReply.confirmQuestion(i, l))
        add(VoiceReply.notWhileMoving(i, l))
        add(VoiceReply.notOnThisCar(i, l))
        add(VoiceReply.partNotOnThisCar(i, l))
        add(VoiceReply.noNavApp(i, l))
        add(VoiceReply.navOpenedWithoutDestination(i, l))
        add(VoiceReply.noMediaSession(i, l))
        add(VoiceReply.openVocabMedia(i, l))
        add(VoiceReply.slotOutOfRange(i, 3, l))
        add(VoiceReply.appNotInstalled(i, VoiceAppTargets.GMAPS, l))
        add(VoiceReply.musicAppOpened(i, target, l))
        add(VoiceReply.noMusicApp(i, l))
        add(VoiceReply.handedOver(i, target, lang = l))
        add(VoiceReply.handedOver(i, target, autoplay = true, lang = l))
        add(VoiceReply.resolving(i, l))
        add(VoiceReply.searchingMusic(i, l))
        add(VoiceReply.confirmPlace(i, target, "Chợ Bến Thành", l))
        add(VoiceReply.navOpenedNoHandover(i, target, l))
        add(VoiceReply.navNoPlace(i, target, l))
        add(VoiceReply.cannotOpen(i, l))
        add(VoiceReply.layoutNotHere(i, l))
        add(VoiceReply.placeNotSaved(i, VoicePlaces.displayLabel(VoicePlaces.HOME, l), l))
        add(VoiceReply.placeNeedsCoords(i, target, l))
        VoiceRiskTable.reason(i, l)?.let(::add)
        if (i is VoiceIntent.Control) add(VoiceReply.doneActual(i, 17, l))
        if (i is VoiceIntent.Unknown) VoiceClarify.ask(i, 0, lang = l)?.let { add(it.question) }
    }

    /** Câu không gắn với một ý định: chào · không nghe rõ · hỏi lại · nhãn dùng chung · đọc số liệu · gói lệnh. */
    private fun spokenMisc(l: Lang): List<String> = buildList {
        add(VoiceReply.bye(l))
        add(VoiceReply.nothingHeard(l))
        add(VoiceClarify.vague(l))
        add(VoiceClarify.giveUp(l))
        add(VoiceReply.noReading(TelemetryRegistry.ALL.first().labelIn(l), l))
        ControlRegistry.ALL.forEach { add(VoiceReply.autoLevel(it.id, l)) }
        VoiceFeatureGone.ALL.forEach { add(VoiceFeatureGone.reply(it, l)) }
        add(ProfileNames.display(HomeUiState.DEFAULT_PROFILE, l))
        LayoutPreset.entries.forEach { add(it.labelIn(l)) }
        val macro = ActionMacros.ALL.first()
        MacroResult(macro.id, macro.steps.map { MacroStepResult(it.controlId, false) }).notice(macro.labelIn(l), l)?.let(::add)
        MacroResult(macro.id, macro.steps.mapIndexed { n, s -> MacroStepResult(s.controlId, n == 0) }).notice(macro.labelIn(l), l)?.let(::add)
        val car = CarStatus(
            lights = CarStatus.Lights(lowBeam = true),
            body = CarStatus.Body(doorLfOpen = true, sunroofOpen = false),
        )
        TelemetryRegistry.ALL.forEach { spec -> TelemetryReadout.of(spec.id, car, l)?.let { add(it.label + ": " + it.displayWithUnit()) } }
        // Gộp câu đọc: CÙNG ngôn ngữ với các dòng (KDoc `VoiceFeedbackPhrase.merge`).
        val done = VoiceReply.doneActual(VoiceIntent.Control("temp", 24), 23, l)
        add(VoiceFeedbackPhrase.merge(listOf(done), l).orEmpty())
        add(VoiceFeedbackPhrase.merge(listOf(done, VoiceReply.done(VoiceIntent.Control("readl", 1), l)), l).orEmpty())
        add(VoiceFeedbackPhrase.merge(listOf(VoiceReply.failed(VoiceIntent.Control("trunk", 1), lang = l), done), l).orEmpty())
        add(VoiceFeedbackPhrase.merge(List(14) { VoiceReply.done(VoiceIntent.Control("readl", 1), l) }, l).orEmpty())
    }

    private fun all(l: Lang): List<String> = intents.flatMap { spoken(it, l) } + spokenMisc(l)

    // ══ (1) `lang` tường minh THẮNG `Strings.current` ════════════════════════════════════════════════════════

    @Test
    fun `cau noi chi phu thuoc lang truyen vao, khong phu thuoc Strings current`() {
        listOf(Lang.VI, Lang.EN).forEach { l ->
            val ref = under(l) { all(l) }
            Lang.entries.filter { it != l }.forEach { ui ->
                val got = under(ui) { all(l) }
                val diff = ref.indices.filter { ref[it] != got[it] }.take(8).map { "«${ref[it]}» ≠ «${got[it]}»" }
                assertEquals(
                    emptyList<String>(), diff,
                    "lang=$l nhưng màn=$ui làm câu đổi — có lời gọi bên trong đọc Strings.current thay vì lang",
                )
            }
        }
    }

    // ══ (2) Mỗi tiếng giao diện ⇒ đúng tiếng giọng nói, y byte bản VI/EN cũ ═══════════════════════════════════

    @Test
    fun `moi tieng giao dien noi tieng Viet, rieng EN noi tieng Anh — y byte ban cu`() {
        val vi = under(Lang.VI) { all(Lang.VI) }
        val en = under(Lang.EN) { all(Lang.EN) }
        assertTrue(vi != en, "bộ mẫu không phân biệt VI/EN — bài thử vô nghĩa")
        Lang.entries.forEach { ui ->
            // Đúng phép mà tiến trình chính làm: màn = ui, phiên nói truyền voiceLangOf(Strings.current).
            val got = under(ui) { all(voiceLangOf(Strings.current)) }
            val want = if (ui == Lang.EN) en else vi
            assertEquals(want, got, "giao diện $ui phải nói ${if (ui == Lang.EN) "EN" else "VI"} y byte")
            val leaked = got.filter { s -> s.codePoints().anyMatch { cp -> scriptOf(cp) in FOREIGN } }
            assertEquals(emptyList<String>(), leaked, "giao diện $ui: chữ Hán/Thái lọt vào câu nói của giọng Việt/Anh")
        }
    }

    @Test
    fun `voiceLangOf la luy dang va chi co hai gia tri`() {
        Lang.entries.forEach { ui ->
            val v = voiceLangOf(ui)
            assertEquals(if (ui == Lang.EN) Lang.EN else Lang.VI, v, "$ui")
            assertEquals(v, voiceLangOf(v), "voiceLangOf(voiceLangOf(x)) phải = voiceLangOf(x)")
            assertEquals(v, ui.voice)
        }
    }

    /** Lời dẫn *"Đã "* của phép gộp phải cùng tiếng với dòng — không thì *"Đã đã gửi…"* / *"Done: Done: …"* quay lại. */
    @Test
    fun `gop cau doc khong lap loi dan o ca hai tieng`() {
        listOf(Lang.VI to "Đã đã", Lang.EN to "Done: Done:").forEach { (l, twice) ->
            Lang.entries.forEach { ui ->
                val line = VoiceReply.doneActual(VoiceIntent.Control("temp", 24), 23, l)
                val merged = under(ui) { VoiceFeedbackPhrase.merge(listOf(line, line), l).orEmpty() }
                assertTrue(!merged.contains(twice, ignoreCase = true), "màn=$ui lang=$l: «$merged»")
            }
        }
    }

    /** R5/OQ3 — câu NO_VERB không dạy câu tiếng Anh mà ASR tiếng Việt không nghe ra; VI y byte bản cũ. */
    @Test
    fun `cau NO_VERB chi vi du tieng Viet o moi tieng`() {
        val u = VoiceIntent.Unknown(VoiceUnknownReason.NO_VERB, "")
        assertEquals("Chưa rõ cần làm gì — thử \"bật…\", \"mở…\", \"xem…\"", VoiceReply.unknown(u, Lang.VI))
        Lang.entries.forEach { l ->
            val s = VoiceReply.unknown(u, l)
            assertTrue(s.contains("\"bật…\""), "$l: phải nêu ví dụ tiếng Việt «bật…»: $s")
            assertTrue(!s.contains("turn on", ignoreCase = true), "$l: không dạy «turn on…» (ASR không nghe ra): $s")
        }
    }

    private companion object {
        val FOREIGN = setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.THAI)
        fun scriptOf(cp: Int): Character.UnicodeScript = Character.UnicodeScript.of(cp)
    }
}
