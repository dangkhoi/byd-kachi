package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.91 VOICE-APP-NAMES · C6 — bài canh hotword của tên đã dạy (spec §4.8 + Pass 1). Khoá: tệp mới là TẬP CHA của tệp
 * tĩnh (hai đường rụng `dropPrefixes` + `dropAppNameLeading` đều được tính), không dòng một từ, không dòng là tiền tố
 * dòng khác, HOA không chữ số, tên trần không đứng đầu dòng, tên gõ không bias, tên nối dài tên app khác không bias.
 */
class SherpaTaughtHotwordsTest {

    private val base = SherpaBiasing.hotwordsFile()
    private val baseLines = base.lines().filter { it.isNotBlank() }.toSet()
    private fun t(pkg: String, a: String, src: TaughtSource = TaughtSource.SPEECH) = TaughtName(pkg, src, a, "x")
    private fun file(vararg n: TaughtName) = SherpaBiasing.hotwordsFile(taught = n.toList())
    private fun lines(f: String) = f.lines().filter { it.isNotBlank() }

    @Test
    fun `khong co ten da day thi tep y nguyen`() {
        assertEquals(base, SherpaBiasing.hotwordsFile(taught = emptyList()))
    }

    @Test
    fun `ten giong sinh MO DUA BAT va tep moi la tap cha`() {
        val f = file(t("com.example.flix", "nep leag"), t("com.example.files", "quản lý tệp"))
        val l = lines(f)
        assertTrue(l.containsAll(baseLines), "tệp mới phải ⊇ tệp tĩnh")
        listOf("MỞ NEP LEAG", "ĐƯA NEP LEAG", "BẬT NEP LEAG", "MỞ QUẢN LÝ TỆP").forEach { assertTrue(it in l, "thiếu «$it»") }
        assertFalse("NEP LEAG" in l, "tên trần không đứng một mình")
        assertTrue(l.none { !it.contains(' ') }, "không dòng một từ")
        assertTrue(l.none { it.any(Char::isDigit) }, "không chữ số")
        assertTrue(l.all { it == it.uppercase() }, "HOA")
        val set = l.toSet()
        assertTrue(l.none { x -> set.any { y -> y != x && y.startsWith("$x ") } }, "không dòng nào là tiền tố dòng khác")
    }

    @Test
    fun `ten go khong vao hotword`() {
        assertEquals(base, file(t("com.example.files", "quản lý tệp", TaughtSource.TYPED)))
    }

    @Test
    fun `ten lam mot dong tinh thanh tien to thi bi loai duong dropPrefixes`() {
        // Lấy một dòng tĩnh `MỞ <X>` thật ⇒ tên `<X> ĐẸP` sinh `MỞ <X> ĐẸP` ⇒ dòng tĩnh thành tiền tố ⇒ phải bị loại.
        val static = baseLines.first { it.startsWith("MỞ ") && it.split(' ').size >= 3 }
        val name = static.removePrefix("MỞ ").lowercase() + " đẹp"
        val p = SherpaTaughtHotwords.plan(base, listOf(t("com.example.a", name)))
        assertTrue(p.accepted.isEmpty(), "«$name» phải bị loại khỏi bias")
        assertTrue(static in p.excluded.single().killedLines, "${p.excluded}")
        assertEquals(base, file(t("com.example.a", name)))
    }

    @Test
    fun `ten trung chu mo dau mot dong tinh thi bi loai duong dropAppNameLeading`() {
        // Một dòng tĩnh KHÔNG mở đầu bằng động từ (vd cụm sổ địa chỉ / nhãn ≥ 2 từ) — tên = hai chữ đầu của nó.
        val verbHeads = SherpaSpokenWords.VERBS.values.flatten().map { it.substringBefore(' ').uppercase() }.toSet()
        val line = baseLines.first { it.split(' ').size >= 3 && it.substringBefore(' ') !in verbHeads }
        val name = line.split(' ').take(2).joinToString(" ").lowercase()
        val p = SherpaTaughtHotwords.plan(base, listOf(t("com.example.a", name)))
        assertTrue(p.accepted.isEmpty(), "«$name» sẽ xoá «$line» qua appNames")
        assertTrue(line in p.excluded.single().killedLines)
    }

    @Test
    fun `ten noi dai ten da day cua app khac khong bias ten ngan van bias`() {
        val p = SherpaTaughtHotwords.plan(base, listOf(t("com.example.a", "tóp tóp"), t("com.example.b", "tóp tóp xịn")))
        assertEquals(listOf("tóp tóp"), p.accepted.map { it.accented })
        assertTrue(p.excluded.single().extendsOther)
        // Cùng một app nối dài chính nó ⇒ vô hại (cùng đích), cả hai giữ.
        val same = SherpaTaughtHotwords.plan(base, listOf(t("com.example.a", "tóp tóp"), t("com.example.a", "tóp tóp xịn")))
        assertEquals(2, same.accepted.size)
    }

    @Test
    fun `trang toi da 120 ten van la tap cha`() {
        val many = (0 until TaughtNamesCodec.MAX_PER_PROFILE).map { i -> t("com.example.p$i", "tên thử ${('a' + i % 26)}${('a' + i / 26)}") }
        val l = lines(SherpaBiasing.hotwordsFile(taught = many))
        assertTrue(l.containsAll(baseLines))
        assertTrue(l.size > baseLines.size)
    }
}
