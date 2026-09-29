package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceLaunch
import com.byd.clusternav.launcher.voice.VoiceMediaOp
import com.byd.clusternav.launcher.voice.VoiceRiskTable
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * ═══ 2.83 · Lệnh BẮT ĐẦU DẪN tới Google Maps mang `NEW_TASK | CLEAR_TASK` = `0x10008000` — MỌI đường ══════════
 *
 * [ĐO xe 29/09] chỉ `NEW_TASK` ⇒ Google Maps đang dẫn hỏi *"Thoát chế độ đi theo chỉ dẫn?"* và khoá nút *"Có"*;
 * thêm `FLAG_ACTIVITY_CLEAR_TASK` ⇒ dọn phiên cũ, dẫn thẳng đích mới (owner xác nhận). VietMap/Waze CHƯA đo ca này
 * ⇒ giữ `NEW_TASK` trơn (CLAUDE.md §6/§14).
 *
 * Ba lớp khoá, mỗi lớp chặn một cách hỏng khác nhau:
 *  1. **cờ** — [VoiceAppIntents.launchFlags] cho đúng hai con số (hằng nền tảng, `javap` `android.jar`);
 *  2. **hành vi** — dựng [VoiceTargetDispatch] / [VoiceDispatcher] THẬT, chạy từng đường giọng nói (theo tên, theo
 *     địa chỉ không nêu app, toạ độ đã tra, nơi đã lưu) và đọc [VoiceAppIntents.Handoff] thật sự đi ra;
 *  3. **dây nối** — đường dẫn theo lịch (`ScheduledNavApplier`) cần `Context` nên off-car khoá bằng quét nguồn:
 *     nó phải đi qua CÙNG cửa [VoiceAppIntents.destinationHandoff], và không ai ngoài cửa ấy tự dựng lượt giao
 *     điểm đến (gọi `destinationLaunch(` là dấu hiệu của một cửa thứ hai).
 *
 * Điểm đến trong bài là địa danh công cộng (*Bitexco*), không phải địa chỉ thật của ai.
 */
class VoiceNavClearTaskFlagTest {

    private companion object {
        const val GMAPS = "com.google.android.apps.maps"
        const val VIETMAP = "vn.vietmap.live"
        const val WAZE = "com.waze"
        const val YT_MUSIC = "com.google.android.apps.youtube.music"
        const val NEW_TASK_ONLY = 0x10000000
        const val NEW_AND_CLEAR_TASK = 0x10008000
        val BITEXCO = VoiceAppIntents.Coords(10.7717, 106.7043, "Bitexco")
    }

    private val allNav = mapOf("Google Maps" to GMAPS, "VietMap" to VIETMAP, "Waze" to WAZE)

    private fun dispatch(
        sent: MutableList<VoiceAppIntents.Handoff>,
        geocoded: VoiceAppIntents.Coords? = null,
        places: List<SavedPlace> = emptyList(),
    ) = VoiceTargetDispatch(
        state = { HomeUiState(savedPlaces = places) },
        media = { error("bài này không chạm transport nhạc") },
        openApp = { true },
        confirm = { _, y, _ -> y() },
        say = {},
        sendToApp = { h -> sent += h; true },
        geocode = { geocoded },
        mediaPackage = { null },
        onUi = { it() },
        background = { it() },
    )

    private fun flagsOf(h: VoiceAppIntents.Handoff) = VoiceAppIntents.launchFlags(h.clearTask)

    // ══ (1) CỜ ══════════════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `co dung hai con so cua nen tang`() {
        assertEquals(NEW_AND_CLEAR_TASK, VoiceAppIntents.launchFlags(clearTask = true))
        assertEquals(NEW_TASK_ONLY, VoiceAppIntents.launchFlags(clearTask = false), "NEW_TASK phải LUÔN có")
    }

    // ══ (2) HÀNH VI — từng đường giọng nói ══════════════════════════════════════════════════════════════════

    @Test
    fun `giong noi THEO TEN Google Maps mang CLEAR_TASK`() {
        val sent = ArrayList<VoiceAppIntents.Handoff>()
        dispatch(sent).runNav(VoiceIntent.Nav("Bitexco", VoiceAppTargets.GMAPS), allNav)
        val h = sent.single()
        assertEquals(GMAPS, h.pkg)
        assertTrue((h.launch as VoiceLaunch.Uri).template.startsWith("google.navigation:q="))
        assertEquals(NEW_AND_CLEAR_TASK, flagsOf(h), "[ĐO xe 29/09] thiếu CLEAR_TASK ⇒ hộp 'Thoát chế độ…' khoá nút")
    }

    /** Câu không nêu app ⇒ thứ tự ưu tiên chọn Google Maps ⇒ vẫn phải mang cờ (cờ theo ĐÍCH, không theo câu). */
    @Test
    fun `giong noi THEO DIA CHI khong neu app van mang CLEAR_TASK`() {
        val sent = ArrayList<VoiceAppIntents.Handoff>()
        dispatch(sent).runNav(VoiceIntent.Nav("Bitexco", null), allNav)
        assertEquals(GMAPS, sent.single().pkg)
        assertEquals(NEW_AND_CLEAR_TASK, flagsOf(sent.single()))
    }

    /** Đi hết đường phân tích câu thật (cổng hỏi-lại tự đồng ý) — không chỉ gọi thẳng vào nửa dispatch. */
    @Test
    fun `cau noi day du qua VoiceDispatcher toi Google Maps mang CLEAR_TASK`() {
        val sent = ArrayList<VoiceAppIntents.Handoff>()
        val yes = ArrayList<() -> Unit>()
        val d = VoiceDispatcher(
            control = { error("bài này không chạm nút xe") },
            state = { HomeUiState() },
            media = { error("bài này không chạm transport nhạc") },
            appsByLabel = { mapOf("Bản đồ" to GMAPS) },
            openApp = { true },
            openAppList = {},
            openSettings = {},
            onSwitchProfile = {},
            onListen = {},
            confirm = { _, y, _ -> yes += y },
            confirmIds = { VoiceRiskTable.askableIds().toSet() },
            say = {},
            assignAppToSlot = { _, _ -> true },
            sendToApp = { h -> sent += h; true },
            geocode = { null },
            mediaPackage = { null },
            background = { it() },
            onUi = { it() },
        )
        d.submit("dẫn đường tới Bitexco bằng google maps")
        while (yes.isNotEmpty()) yes.removeAt(yes.size - 1).invoke()
        assertEquals(GMAPS, sent.single().pkg)
        assertEquals(NEW_AND_CLEAR_TASK, flagsOf(sent.single()))
    }

    @Test
    fun `noi DA LUU giao Google Maps mang CLEAR_TASK`() {
        val sent = ArrayList<VoiceAppIntents.Handoff>()
        val places = listOf(SavedPlace("Toà nhà", "Bitexco", BITEXCO.lat, BITEXCO.lng))
        dispatch(sent, places = places).runNavSaved(VoiceIntent.NavigateSaved("Toà nhà", VoiceAppTargets.GMAPS), allNav)
        assertEquals(GMAPS, sent.single().pkg)
        assertEquals(NEW_AND_CLEAR_TASK, flagsOf(sent.single()))
    }

    /** VietMap đi đường TOẠ ĐỘ đã tra — `singleTask`, chưa đo ca CLEAR_TASK ⇒ giữ `NEW_TASK` trơn. */
    @Test
    fun `VietMap theo TOA DO KHONG mang CLEAR_TASK`() {
        val sent = ArrayList<VoiceAppIntents.Handoff>()
        dispatch(sent, geocoded = BITEXCO).runNav(VoiceIntent.Nav("Bitexco", VoiceAppTargets.VIETMAP), allNav)
        val h = sent.single()
        assertEquals(VIETMAP, h.pkg)
        assertEquals(BITEXCO, h.coords)
        assertEquals(NEW_TASK_ONLY, flagsOf(h), "VietMap chưa đo CLEAR_TASK — đổi đường đang chạy là lỗi §6")
    }

    @Test
    fun `VietMap noi DA LUU KHONG mang CLEAR_TASK`() {
        val sent = ArrayList<VoiceAppIntents.Handoff>()
        val places = listOf(SavedPlace("Toà nhà", "Bitexco", BITEXCO.lat, BITEXCO.lng))
        dispatch(sent, places = places).runNavSaved(VoiceIntent.NavigateSaved("Toà nhà", VoiceAppTargets.VIETMAP), allNav)
        assertEquals(VIETMAP, sent.single().pkg)
        assertEquals(NEW_TASK_ONLY, flagsOf(sent.single()))
    }

    @Test
    fun `Waze KHONG mang CLEAR_TASK`() {
        val sent = ArrayList<VoiceAppIntents.Handoff>()
        dispatch(sent).runNav(VoiceIntent.Nav("Bitexco", VoiceAppTargets.WAZE), allNav)
        assertEquals(WAZE, sent.single().pkg)
        assertEquals(NEW_TASK_ONLY, flagsOf(sent.single()))
    }

    /** Cửa chung với nhạc (`deliver`) không được làm cờ dẫn đường rò sang app nhạc. */
    @Test
    fun `app nhac KHONG mang CLEAR_TASK`() {
        val sent = ArrayList<VoiceAppIntents.Handoff>()
        dispatch(sent).runMedia(
            VoiceIntent.Media(VoiceMediaOp.QUERY, "Diễm Xưa", VoiceAppTargets.YT_MUSIC),
            mapOf("YouTube Music" to YT_MUSIC),
        )
        assertEquals(YT_MUSIC, sent.single().pkg)
        assertEquals(NEW_TASK_ONLY, flagsOf(sent.single()))
    }

    @Test
    fun `cua duy nhat lay co tu du lieu cua dich`() {
        VoiceAppTargets.NAV.forEach { t ->
            val h = VoiceAppIntents.destinationHandoff(t, t.packages.first(), "Bitexco", BITEXCO)
            assertEquals(t.clearTaskOnNav, h?.clearTask, "${t.key}: Handoff phải mang ĐÚNG cờ của đích")
        }
    }

    // ══ (3) DÂY NỐI — lịch dẫn đường + không có cửa thứ hai ═══════════════════════════════════════════════════

    private fun code(rel: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$rel")

    @Test
    fun `dan theo LICH di qua cung cua destinationHandoff`() {
        val applier = code("automation/ScheduledNavApplier.kt")
        val launchOne = SourceRoots.body(applier, "private fun launchOne(")
        assertTrue("VoiceAppIntents.destinationHandoff(target, pkg, place.query, coords)" in launchOne)
        assertTrue("VoiceAppIntents.send(app, handoff)" in launchOne)
        assertFalse("VoiceAppIntents.Handoff(" in applier, "lịch tự dựng Handoff ⇒ cờ của đích có thể lệch voice")
    }

    @Test
    fun `giong noi di qua cung cua destinationHandoff`() {
        val deliver = SourceRoots.body(code("launcher/VoiceTargetDispatch.kt"), "private fun deliver(")
        assertTrue("VoiceAppIntents.destinationHandoff(target, pkg, query, coords)" in deliver)
    }

    /**
     * `send` chuyển cờ cho đường CHÍNH; đường dự phòng KHÔNG mang cờ (chưa đo — spec 2.83 §4.6, senior review 2 [P3]);
     * `build` chỉ dựng cờ qua [VoiceAppIntents.launchFlags].
     */
    @Test
    fun `send va build chuyen co toi Intent`() {
        val intents = code("launcher/voice/VoiceAppIntents.kt")
        val send = SourceRoots.body(intents, "fun send(")
        assertTrue("build(h.launch, h.pkg, h.query, h.coords, h.clearTask)" in send)
        assertTrue("build(h.fallback, h.pkg, h.query, h.coords)" in send, "dự phòng dựng bằng cờ mặc định (NEW_TASK trơn)")
        assertEquals(1, Regex("h\\.clearTask").findAll(send).count(), "cờ CLEAR_TASK chỉ đi với đường chính")
        // `build` là hàm một-biểu-thức kết bằng `}?.addFlags(…)` — dòng ấy nằm ngoài vùng `body()` đọc được, nên
        // khoá bằng: đúng MỘT lời `addFlags(` trong tệp, và nó nhận `launchFlags(clearTask)`.
        assertEquals(1, Regex("addFlags\\(").findAll(intents).count(), "một chỗ gắn cờ duy nhất")
        assertTrue("}?.addFlags(launchFlags(clearTask))" in intents)
        val flagsFn = SourceRoots.body(intents, "fun launchFlags(")
        assertEquals(
            Regex("FLAG_ACTIVITY_").findAll(intents).count(),
            Regex("FLAG_ACTIVITY_").findAll(flagsFn).count(),
            "cờ dựng ở MỘT chỗ (launchFlags), không rải nơi khác trong tệp",
        )
        val handoff = SourceRoots.body(intents, "fun destinationHandoff(")
        assertTrue("clearTask = target.clearTaskOnNav" in handoff)
    }

    /** Ai gọi `destinationLaunch(` ở `:app` là đang tự dựng lượt giao điểm đến — tức một cửa thứ hai. */
    @Test
    fun `khong co cua thu hai dung luot giao diem den`() {
        val appRoot = SourceRoots.moduleSourceRoots().first { it.endsWith("app/src/main/java") }
        val callers = Files.walk(appRoot).use { s -> s.filter { it.extension == "kt" }.toList() }
            .filter { f -> ".destinationLaunch(" in SourceRoots.codeOf("src/main/java/${appRoot.relativize(f)}") }
            .map { it.name }
        assertEquals(listOf("VoiceAppIntents.kt"), callers)
    }
}
