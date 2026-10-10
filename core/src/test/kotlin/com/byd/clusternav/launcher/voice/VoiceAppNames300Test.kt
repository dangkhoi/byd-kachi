package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 3.0 R3 `VOICE-TEACH-WAZE` · `VOICE-EN-APPNAME-HOTWORD-MEASURE` — tên app Anh theo chuỗi mô hình THẬT in ra ═════════════════
 *
 * Spec `docs/specs/kachi-300-plan.html` R3 · số đo `docs/diagnostics/voice-300-r3-appnames-2026-10-10.md`.
 * Tầng CHỮ: [ĐO xe 10/10] *"dẫn đường về nhà bằng Waze"* ⇒ sherpa *"… bằng way"* ⇒ `Nav(về nhà bằng way)` (nuốt cả đuôi).
 * Tầng NGHE: [ĐO host TTS, 324 WAV] thêm `MỞ/ĐƯA/BẬT WAZE · GOOGLE MAPS · VIETMAP` = 16 lên · 0 tụt.
 */
class VoiceAppNames300Test {

    private val apps = listOf("Google Maps", "VietMap", "Waze", "Spotify")
    private fun parse(s: String, places: List<String> = emptyList()) = VoiceIntentParser.parse(s, apps = apps, places = places)

    @Test
    fun `log xe 10-10 - ve nha bang way ⇒ Waze, diem den sach`() {
        assertEquals(listOf(VoiceIntent.NavigateSaved("nhà", VoiceAppTargets.WAZE)), parse("dẫn đường về nhà bằng way", listOf("nhà")))
        assertEquals(listOf(VoiceIntent.Nav("chợ bến thành", VoiceAppTargets.WAZE)), parse("dẫn đường đến chợ bến thành bằng ways"))
    }

    @Test
    fun `cau kiem doc am Viet - bang google map viet map va quay ⇒ ca ba app`() {
        // [ĐO host] dạng mô hình in cho câu kiểm đọc âm Việt ở 175/220/250 — đúng chuỗi harness WAV dùng.
        assertEquals(
            listOf(VoiceAppTargets.GMAPS, VoiceAppTargets.VIETMAP, VoiceAppTargets.WAZE).map { VoiceIntent.Nav("chợ bến thành", it) },
            parse("dẫn đường đến chợ bến thành bằng google map việt map và quây"),
        )
    }

    @Test
    fun `mo app bang chuoi mo hinh in - ways, vietmap line, spoty file`() {
        assertEquals(listOf(VoiceIntent.OpenApp("Waze", 1, VoiceAppTargets.WAZE)), parse("mở ways vào ô một"))
        val vm = parse("mở vietmap line").single() as VoiceIntent.OpenApp
        assertEquals("VietMap", vm.appName)
        val vm2 = parse("đưa vietmap lai vào ô hai").single() as VoiceIntent.OpenApp
        assertEquals("VietMap" to 2, vm2.appName to vm2.slot)
        assertEquals(VoiceIntent.Media::class, parse("phát nhạc bằng spoty file").single()::class)
        assertEquals(VoiceAppTargets.SPOTIFY, (parse("phát nhạc bằng spoty file").single() as VoiceIntent.Media).app)
    }

    @Test
    fun `tep hotword co MO WAZE, MO GOOGLE MAPS, MO VIETMAP song qua luat tien to`() {
        val lines = SherpaBiasing.hotwordsFile().trimEnd().split("\n").toSet()
        listOf("MỞ WAZE", "MỞ GOOGLE MAPS", "MỞ VIETMAP").forEach { assertTrue(it in lines, "thiếu dòng $it") }
        assertTrue(lines.none { it.startsWith("MỞ VIETMAP L") }, "dòng VIETMAP LIVE/LAY làm MỞ VIETMAP thành tiền tố — cấm")
        // Spotify/Zing/way: bias 0 lên [ĐO host] ⇒ không vào tệp.
        assertFalse(lines.any { it.endsWith(" WAY") || it.endsWith(" SPOTIFY") }, "tên bias vô ích không vào tệp")
    }
}
