package com.byd.clusternav.launcher.telegram

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 3.1.20 — bàn phím nút dưới ô gõ + lời nhắc gõ tên bài / điểm đến (owner 26/10). */
class TelegramReplyKeyboardTest {

    private val isCtl: (String) -> Boolean = { it in setOf("readl", "trunk", "ac_auto") }

    @Test fun `moi nut ban phim mo dung mot bang`() {
        val want = mapOf(TelegramReplyKeyboard.GO to "nav", TelegramReplyKeyboard.MUSIC to "music", TelegramReplyKeyboard.AC to "ac",
            TelegramReplyKeyboard.SEAT to "seat", TelegramReplyKeyboard.MORE to "misc", TelegramReplyKeyboard.STATUS to "menu")
        want.forEach { (label, domain) ->
            assertEquals(TelegramCommand.Board(domain), TelegramUpdateParser.parseText(label, isCtl), label)
        }
        assertTrue(TelegramUpdateParser.parseText("ok thanks", isCtl) is TelegramCommand.Unknown, "chữ lạc tay vẫn không thành lệnh")
    }

    @Test fun `ban phim du 6 nut, luon hien, gon`() {
        val m = TelegramReplyKeyboard.markup()
        val labels = Regex("\"text\":\"([^\"]+)\"").findAll(m).map { it.groupValues[1] }.toList()
        assertEquals(6, labels.size)
        labels.forEach { assertTrue(TelegramReplyKeyboard.boardOf(it) != null, "nút '$it' phải dịch được") }
        assertTrue(m.startsWith("{\"keyboard\":[[") && m.contains("\"is_persistent\":true") && m.contains("\"resize_keyboard\":true"))
        assertEquals(m.count { it == '[' }, m.count { it == ']' }); assertEquals(m.count { it == '{' }, m.count { it == '}' })
    }

    @Test fun `tra loi loi nhac ten bai thanh play, loi nhac diem den thanh go`() {
        assertEquals(TelegramCommand.Music("1 triệu like", isQuery = true),
            TelegramUpdateParser.parseText("1 triệu like", isCtl, TelegramReplyKeyboard.SONG_PROMPT))
        assertEquals(TelegramCommand.Music("https://youtu.be/x", isQuery = false),
            TelegramUpdateParser.parseText("https://youtu.be/x", isCtl, TelegramReplyKeyboard.SONG_PROMPT))
        assertEquals(TelegramCommand.Nav("Công ty"), TelegramUpdateParser.parseText("Công ty", isCtl, TelegramReplyKeyboard.DEST_PROMPT))
        assertEquals(TelegramCommand.Nav("Công ty", apps = listOf("waze")),
            TelegramUpdateParser.parseText("Công ty waze", isCtl, TelegramReplyKeyboard.DEST_PROMPT))
    }

    @Test fun `tra loi tin khac hoac go lenh thi khong bi dich`() {
        assertTrue(TelegramUpdateParser.parseText("hello", isCtl, "some other message") is TelegramCommand.Unknown)
        assertEquals(TelegramCommand.Menu, TelegramUpdateParser.parseText("/menu", isCtl, TelegramReplyKeyboard.SONG_PROMPT), "lệnh / thắng")
        assertNull(TelegramReplyKeyboard.answerOf("  ", TelegramReplyKeyboard.SONG_PROMPT))
    }

    @Test fun `force reply hop le, goi y khong qua 64 ky tu`() {
        val m = TelegramReplyKeyboard.forceReply("x".repeat(100))
        assertTrue(m.contains("\"force_reply\":true"))
        assertEquals(64, Regex("\"input_field_placeholder\":\"([^\"]*)\"").find(m)!!.groupValues[1].length)
    }

    @Test fun `bang nhac co nut Song, bang dan duong co nut New - callback hop le`() {
        val music = TelegramMenu.music(7, true)
        assertTrue(music.contains("\"🔎 Song\""))
        assertEquals(TelegramCallback(7, "music", "song"), cbOf(music, "🔎 Song"))
        val nav = TelegramMenu.nav(7, emptyList(), 0)
        assertEquals(TelegramCallback(7, "nav", "new"), cbOf(nav, "➕ New destination"))
    }

    @Test fun `den doc va loc bui co CA hai nut On va Off du trang thai khong doc duoc`() {
        // Owner 26/10: "mở đèn đọc được mà không tắt được" — readl không có đường đọc ⇒ trạng thái null.
        listOf(null, true, false).forEach { st ->
            val misc = TelegramMenu.misc(3, st, st)
            assertEquals(TelegramCallback(3, "misc", "readl", "on"), cbOfPrefix(misc, "💡 Read light On"), "readl on, state=$st")
            assertEquals(TelegramCallback(3, "misc", "readl", "off"), cbOfPrefix(misc, "💡 Off"), "readl off, state=$st")
            assertEquals(TelegramCallback(3, "misc", "pm25", "off"), cbOfPrefix(misc, "🌫 Off"), "pm25 off, state=$st")
            val ac = TelegramMenu.ac(3, null, null, null, st)
            assertEquals(TelegramCallback(3, "ac", "defrost", "off"), cbOfPrefix(ac, "🌬 Off"), "defrost off, state=$st")
        }
    }

    private val BTN = Regex("\\{\"text\":\"([^\"]+)\",\"callback_data\":\"([^\"]+)\"\\}")
    private fun cbOfPrefix(markup: String, prefix: String): TelegramCallback? =
        BTN.findAll(markup).first { it.groupValues[1].startsWith(prefix) }.groupValues[2].let { TelegramCallback.parse(it) }
    private fun cbOf(markup: String, text: String): TelegramCallback? =
        BTN.findAll(markup).first { it.groupValues[1] == text }.groupValues[2].let { TelegramCallback.parse(it) }
}
