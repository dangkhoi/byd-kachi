package com.byd.clusternav.launcher.telegram

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 3.1.20 — bàn phím nút dưới ô gõ + lời nhắc gõ tên bài / điểm đến (owner 26/10). */
class TelegramReplyKeyboardTest {

    @Test fun `3 nut ban phim - Go mo so dia chi, Music hoi ten bai, Help`() {
        assertEquals(TelegramCommand.ListPlaces, TelegramUpdateParser.parseText(TelegramReplyKeyboard.GO))
        assertEquals(TelegramCommand.AskSong, TelegramUpdateParser.parseText(TelegramReplyKeyboard.MUSIC))
        assertEquals(TelegramCommand.Help, TelegramUpdateParser.parseText(TelegramReplyKeyboard.HELP))
        val m = TelegramReplyKeyboard.markup()
        val labels = Regex("\"text\":\"([^\"]+)\"").findAll(m).map { it.groupValues[1] }.toList()
        assertEquals(listOf(TelegramReplyKeyboard.GO, TelegramReplyKeyboard.MUSIC, TelegramReplyKeyboard.HELP), labels)
        assertTrue(m.contains("\"is_persistent\":true") && m.contains("\"resize_keyboard\":true"))
        assertEquals(m.count { it == '{' }, m.count { it == '}' })
    }

    @Test fun `tra loi loi nhac thanh play hoac go, kem duoc app`() {
        val song = TelegramReplyKeyboard.SONG_PROMPT; val dest = TelegramReplyKeyboard.DEST_PROMPT
        assertEquals(TelegramCommand.Music("1 triệu like", true, null), TelegramUpdateParser.parseText("1 triệu like", song))
        assertEquals(TelegramCommand.Music("1 triệu like", true, "ytmusic"), TelegramUpdateParser.parseText("1 triệu like ytmusic", song))
        assertEquals(TelegramCommand.Music("https://youtu.be/x", false), TelegramUpdateParser.parseText("https://youtu.be/x", song))
        assertEquals(TelegramCommand.Nav("Công ty", apps = listOf("vietmap")), TelegramUpdateParser.parseText("Công ty vietmap", dest))
        assertTrue(TelegramUpdateParser.parseText("hello", "some other message") is TelegramCommand.Unknown)
        assertEquals(TelegramCommand.Menu, TelegramUpdateParser.parseText("/menu", song), "lệnh / thắng")
        assertNull(TelegramReplyKeyboard.answerOf("  ", song))
    }

    @Test fun `force reply hop le, goi y khong qua 64 ky tu`() {
        val m = TelegramReplyKeyboard.forceReply("x".repeat(100))
        assertTrue(m.contains("\"force_reply\":true"))
        assertEquals(64, Regex("\"input_field_placeholder\":\"([^\"]*)\"").find(m)!!.groupValues[1].length)
    }
}
