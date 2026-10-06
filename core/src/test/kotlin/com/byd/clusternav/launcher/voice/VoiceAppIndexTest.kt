package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.91 VOICE-APP-NAMES · C3 — bảng gọi app của phiên, bốn tầng (spec §4.6): nhãn thật &gt; tên đã dạy &gt; dạng suy từ
 * nhãn (nhãn locale thứ hai · dạng đọc) &gt; khớp mờ. Mỗi bài khoá một luật; gói `com.example.*` là bộ thử.
 */
class VoiceAppIndexTest {

    private val flix = "com.example.flix"
    private val maps = "com.example.maps"
    private fun t(pkg: String, a: String, src: TaughtSource = TaughtSource.SPEECH) = TaughtName(pkg, src, a, "x")

    @Test
    fun `ten da day song co id la nhan that va khong vao bang khoa`() {
        val idx = VoiceAppIndex.build(listOf("Netflix" to flix), setOf(flix), listOf(t(flix, "nep leag")))
        val a = idx.aliases.single()
        assertEquals("Netflix", a.labelKey)
        assertEquals(listOf("nep", "leag"), a.words)
        assertEquals(flix, a.pkg)
        assertNull(a.targetKey)
        assertFalse("nep leag" in idx.keys, "tên đã dạy KHÔNG thành khoá khi gói đã có nhãn")
        assertTrue(idx.shadowed.isEmpty())
    }

    @Test
    fun `nhan that cua app KHAC che ten da day`() {
        val idx = VoiceAppIndex.build(
            listOf("Netflix" to flix, "Maps" to maps), setOf(flix, maps), listOf(t(flix, "maps")),
        )
        assertTrue(idx.aliases.isEmpty())
        assertEquals(listOf("maps"), idx.shadowed.map { it.accented })
        assertEquals(maps, idx.keys["Maps"], "nhãn thật không bao giờ bị đè")
    }

    @Test
    fun `ten da day de dang doc doan cua app khac`() {
        // Dạng đọc giả lập: nhãn "Foo" sinh "nep leag" — tên đã dạy của flix phải thắng, dạng đọc bị bỏ.
        val spoken = { l: String -> if (l == "Foo") listOf("nep leag") else emptyList() }
        val idx = VoiceAppIndex.build(
            listOf("Netflix" to flix, "Foo" to maps), setOf(flix, maps), listOf(t(flix, "nép leag")), spoken = spoken,
        )
        assertFalse("nep leag" in idx.keys)
        assertEquals(flix, idx.aliases.single().pkg)
    }

    @Test
    fun `nhan locale thu hai la dang suy putIfAbsent va bi ten da day de`() {
        val idx = VoiceAppIndex.build(
            listOf("Maps" to maps), setOf(maps), emptyList(), alt = listOf("Bản đồ" to maps, "Maps" to flix),
        )
        assertEquals(maps, idx.keys["Bản đồ"])
        assertEquals(maps, idx.keys["Maps"], "nhãn phụ không đè nhãn thật")
        val idx2 = VoiceAppIndex.build(
            listOf("Maps" to maps, "Netflix" to flix), setOf(maps, flix), listOf(t(flix, "bản đồ")),
            alt = listOf("Bản đồ" to maps),
        )
        assertFalse("Bản đồ" in idx2.keys)
        assertEquals(flix, idx2.aliases.single().pkg)
    }

    @Test
    fun `goi thua khu trung nhan duoc goi bang ten da day`() {
        val loser = "com.example.files2"
        val idx = VoiceAppIndex.build(listOf("Files" to "com.example.files"), setOf("com.example.files", loser), listOf(t(loser, "tệp hai")))
        assertEquals(loser, idx.keys["tệp hai"])
        assertEquals("tệp hai", idx.aliases.single().labelKey)
    }

    @Test
    fun `goi thua khu trung khong duoc lay nhan that cua app khac lam khoa`() {
        // Gói thua chỉ có đường gọi bằng tên đã dạy — nhưng tên ấy trùng nhãn thật của app KHÁC thì không được thành khoá
        // (khoá thứ hai trỏ chữ "maps" sang gói thua là nhập nhằng im lặng với nhãn "Maps").
        val loser = "com.example.files2"
        val idx = VoiceAppIndex.build(
            listOf("Files" to "com.example.files", "Maps" to maps), setOf("com.example.files", loser, maps), listOf(t(loser, "maps")),
        )
        assertTrue(idx.keys.none { it.value == loser }, "${idx.keys}")
        assertEquals(listOf("maps"), idx.shadowed.map { it.accented })
    }

    @Test
    fun `app da go thi ten mo coi khong song khong bi che`() {
        val idx = VoiceAppIndex.build(listOf("Netflix" to flix), setOf(flix), listOf(t("com.example.gone", "gân gân")))
        assertTrue(idx.aliases.isEmpty())
        assertTrue(idx.shadowed.isEmpty(), "mồ côi ≠ bị che — trang hiện ở nhóm App đã gỡ")
    }

    @Test
    fun `hai goi cung mot ten da day thi khong goi nao duoc`() {
        val idx = VoiceAppIndex.build(
            listOf("Netflix" to flix, "Maps" to maps), setOf(flix, maps), listOf(t(flix, "phim hay"), t(maps, "phim hay")),
        )
        assertTrue(idx.aliases.isEmpty())
        assertEquals(2, idx.shadowed.size)
    }

    @Test
    fun `ma dich cua goi thuoc bang dich tra tu bang khong viet cung`() {
        val spotify = VoiceAppTargets.byKey(VoiceAppTargets.SPOTIFY)!!.packages.first()
        val idx = VoiceAppIndex.build(listOf("Spotify" to spotify), setOf(spotify), listOf(t(spotify, "sờ pốt phai")))
        assertEquals(VoiceAppTargets.SPOTIFY, idx.aliases.single().targetKey)
    }

    @Test
    fun `build va aliasesOf luon ra cung ket qua`() {
        val loser = "com.example.files2"
        val labels = listOf("Netflix" to flix, "Maps" to maps, "Files" to "com.example.files")
        val taught = listOf(
            t(flix, "nep leag"), t(flix, "nét lích", TaughtSource.TYPED), t(maps, "netflix"), t(loser, "tệp hai"),
            t("com.example.gone", "gân gân"), t(maps, "bờ đồ"), t(flix, "bờ đồ"),
        )
        val installed = setOf(flix, maps, "com.example.files", loser)
        val idx = VoiceAppIndex.build(labels, installed, taught, alt = listOf("Bản đồ" to maps))
        assertEquals(idx.aliases, VoiceAppIndex.aliasesOf(idx.keys, taught))
        assertEquals(setOf("nep leag", "nét lích", "tệp hai"), idx.aliases.map { it.accented }.toSet())
    }

    @Test
    fun `ten bias hotword cung luat song voi tu vung ke ca goi thua khu trung`() {
        // Senior review 2.91 Pass 1 (P3): bản đầu lọc theo "chuẩn hoá không trùng KHOÁ nào" ⇒ loại nhầm tên của gói THUA khử
        // trùng (khoá của gói ấy CHÍNH LÀ tên đã dạy), và vẫn bias tên hai gói cùng mang (từ vựng đã bỏ). Nay = aliasesOf + giọng.
        val loser = "com.example.files2"
        val labels = listOf("Netflix" to flix, "Maps" to maps, "Files" to "com.example.files")
        val taught = listOf(
            t(flix, "nep leag"), t(flix, "nét lích", TaughtSource.TYPED), t(loser, "tệp hai"),
            t(flix, "maps"), t(maps, "phim hay"), t(flix, "phim hay"), t("com.example.gone", "gân gân"),
        )
        val idx = VoiceAppIndex.build(labels, setOf(flix, maps, "com.example.files", loser), taught)
        val got = VoiceAppIndex.hotwordNames(idx.keys.keys, idx.keys.values.toSet(), taught).map { it.accented }
        assertEquals(listOf("nep leag", "tệp hai"), got, "chỉ tên GIỌNG còn sống trong từ vựng: ${idx.aliases}")
        assertEquals(
            idx.aliases.filter { it.source == TaughtSource.SPEECH }.map { it.accented }, got,
            "bias = đúng tập tên giọng mà parser nhận (một luật sống cho hai tầng)",
        )
    }

    @Test
    fun `khong co ten da day thi bang khoa y nguyen ban cu`() {
        val labels = listOf("YouTube" to "com.google.android.youtube", "Cài đặt" to "com.android.settings")
        val old = LinkedHashMap<String, String>().apply {
            labels.forEach { (l, p) -> put(l, p) }
            labels.forEach { (l, p) -> VoiceAppPhonetics.spokenForms(l).forEach { putIfAbsent(it, p) } }
        }
        assertEquals(old, VoiceAppIndex.build(labels, labels.map { it.second }.toSet(), emptyList()).keys)
    }
}
