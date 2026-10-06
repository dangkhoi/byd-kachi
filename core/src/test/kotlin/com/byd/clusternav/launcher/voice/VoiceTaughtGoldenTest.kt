package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.91 VOICE-APP-NAMES · R6 AC — test VÀNG: 30 tên đã dạy kiểu ASR tiếng Việt in cho tên app ngoại ⇒ 0 câu mẫu của
 * [VoiceCommandCatalog] đổi ý định, và mọi tên qua cổng đều gọi đúng app. Chuỗi là [ĐOÁN] hình dạng mô hình VN in cho
 * tên tiếng Anh (cùng khuôn *"nep leag"* đã đo 27/09) — chốt bằng E1 trên xe; gói `com.example.*` là bộ thử.
 */
class VoiceTaughtGoldenTest {

    private val names = listOf(
        "nep leag", "nét phích", "ti tóc", "tóp tóp", "gờ ráp", "sô pi", "la da đa", "ti ki", "phây búc", "mét xen giơ",
        "gi meo", "gu gồ đrai", "đrốp bốc", "ca nva", "zi gô", "bi gô", "đê vên", "xi nê ma", "phim plút",
        "ka ra ô kê", "tờ ri pi", "wê xin", "ô tô xờ", "đi zi ni", "phai lờ", "lốp búc", "pát pát", "xờ cai", "ô pờ ra",
        "tê lê gờ ram",
    )
    private val labels = names.indices.map { "App$it" to "com.example.app$it" }
    private val keys = VoiceAppIndex.build(labels, labels.map { it.second }.toSet(), emptyList()).keys
    private val profiles = listOf("Mặc định", "Vợ")
    private val places = listOf("Nhà", "Công ty")

    @Test
    fun `ba muoi ten that khong lam doi cau mau nao`() {
        var taught = emptyList<TaughtName>()
        names.forEachIndexed { i, n ->
            val ctx = TeachContext(keys, taught, profiles, places)
            val v = TeachGuard.check(ctx, "com.example.app$i", n, TaughtSource.SPEECH)
            assertTrue(v.level != TeachGuard.Level.BLOCK, "«$n» bị chặn: $v")
            taught = (TaughtNames.add(taught, TaughtName("com.example.app$i", TaughtSource.SPEECH, n, "App$i")) as TaughtNames.Added).names
        }
        val before = TeachContext(keys, emptyList(), profiles, places)
        val after = TeachContext(keys, taught, profiles, places)
        assertEquals(30, after.aliases.size)
        val phrases = VoiceCommandCatalog.groups(profiles, keys.keys.toList(), places).flatMap { g -> g.examples.map { it.phrase } }
        val changed = phrases.filter { before.parse(it) != after.parse(it) }
        assertEquals(emptyList<String>(), changed, "câu mẫu đổi ý định khi có 30 tên đã dạy")
        names.forEachIndexed { i, n ->
            assertEquals(VoiceIntent.OpenApp("App$i"), after.parse("mở $n").single(), "«mở $n»")
        }
        // Hotword: tệp có 30 tên vẫn ⊇ tệp tĩnh.
        val base = SherpaBiasing.hotwordsFile(places, profiles).lines().filter { it.isNotBlank() }
        val withNames = SherpaBiasing.hotwordsFile(places, profiles, taught).lines().filter { it.isNotBlank() }
        assertTrue(withNames.containsAll(base))
    }
}
