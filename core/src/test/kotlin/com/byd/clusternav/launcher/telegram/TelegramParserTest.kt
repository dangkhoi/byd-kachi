package com.byd.clusternav.launcher.telegram

import com.byd.clusternav.launcher.SavedPlace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TelegramParserTest {

    private val isCtl: (String) -> Boolean = { it in setOf("ac_auto", "defrost", "fan", "temp", "pm25") }

    @Test
    fun `go voi ten noi`() {
        assertEquals(TelegramCommand.Nav("Office"), TelegramUpdateParser.parseText("/go Office", isCtl))
    }

    @Test
    fun `go trong ⇒ ListPlaces`() {
        assertEquals(TelegramCommand.ListPlaces, TelegramUpdateParser.parseText("/go", isCtl))
    }

    @Test
    fun `go voi toa do giu nguyen chuoi (app tu tra)`() {
        assertEquals(TelegramCommand.Nav("21.0, 105.8"), TelegramUpdateParser.parseText("/go 21.0, 105.8", isCtl))
    }

    @Test
    fun `chu tran khong dau slash ⇒ Unknown (khong auto-go)`() {
        // P1-7: một tin nhắn lạc tay ("ok thanks") KHÔNG được biến thành điểm đến chờ.
        val c = TelegramUpdateParser.parseText("Bến Thành", isCtl)
        assertTrue(c is TelegramCommand.Unknown)
    }

    @Test
    fun `play voi link`() {
        assertEquals(TelegramCommand.Music("https://youtu.be/abc"), TelegramUpdateParser.parseText("/play https://youtu.be/abc", isCtl))
    }

    @Test
    fun `go chon app alias o cuoi`() {
        assertEquals(TelegramCommand.Nav("Office", apps = listOf("vietmap")),
            TelegramUpdateParser.parseText("/go Office vietmap", isCtl))
        assertEquals(TelegramCommand.Nav("Công ty", apps = listOf("gmaps", "vietmap")),
            TelegramUpdateParser.parseText("/go Công ty gmaps+vietmap", isCtl))
    }

    @Test
    fun `go token cuoi khong phai alias ⇒ la ten noi`() {
        // "bến" không phải alias ⇒ cả cụm là tên nơi, KHÔNG nuốt mất từ cuối.
        assertEquals(TelegramCommand.Nav("Chợ Bến Thành"), TelegramUpdateParser.parseText("/go Chợ Bến Thành", isCtl))
    }

    @Test
    fun `play ten bai ⇒ isQuery`() {
        assertEquals(TelegramCommand.Music("Chúng ta của hiện tại", isQuery = true),
            TelegramUpdateParser.parseText("/play Chúng ta của hiện tại", isCtl))
    }

    @Test
    fun `play link music youtube ⇒ KHONG query (app tu chon theo url)`() {
        assertEquals(TelegramCommand.Music("https://music.youtube.com/watch?v=x", isQuery = false),
            TelegramUpdateParser.parseText("/play https://music.youtube.com/watch?v=x", isCtl))
    }

    @Test
    fun `cancel va help`() {
        assertEquals(TelegramCommand.Cancel, TelegramUpdateParser.parseText("/cancel", isCtl))
        assertEquals(TelegramCommand.Help, TelegramUpdateParser.parseText("/help", isCtl))
    }

    @Test
    fun `dieu khien xe map alias + gia tri`() {
        assertEquals(TelegramCommand.Control("ac_auto", 1), TelegramUpdateParser.parseText("/ac on", isCtl))
        assertEquals(TelegramCommand.Control("defrost", null), TelegramUpdateParser.parseText("/defrost", isCtl))
        assertEquals(TelegramCommand.Control("fan", 3), TelegramUpdateParser.parseText("/fan 3", isCtl))
        assertEquals(TelegramCommand.Control("temp", 22), TelegramUpdateParser.parseText("/temp 22", isCtl))
    }

    @Test
    fun `id control la ⇒ Unknown (khong bia lenh xe)`() {
        val c = TelegramUpdateParser.parseText("/nitro boost", isCtl)
        assertTrue(c is TelegramCommand.Unknown)
    }

    @Test
    fun `share location ⇒ Nav co toa do`() {
        val c = TelegramUpdateParser.parseLocation(21.0045, 105.8412) as TelegramCommand.Nav
        assertEquals("21.0045, 105.8412", c.coords)
    }

    @Test
    fun `callback nav co app list`() {
        val c = TelegramUpdateParser.parseCallback("nav:Office:gmaps+vietmap") as TelegramCommand.Nav
        assertEquals("Office", c.placeOrQuery)
        assertEquals(listOf("gmaps", "vietmap"), c.apps)
    }

    @Test
    fun `config isAuthorized chi chu xe`() {
        val cfg = TelegramConfig(botToken = "t", allowedChatId = 123, enabled = true)
        assertTrue(cfg.isAuthorized(123))
        assertFalse(cfg.isAuthorized(999))
        assertFalse(cfg.isAuthorized(null))
        assertTrue(cfg.ready)
        // chưa cấu hình ⇒ không ai được
        assertFalse(TelegramConfig().isAuthorized(0))
        assertFalse(TelegramConfig().ready)
    }

    @Test
    fun `keyboard tu so dia chi dung index + callback navi giai index`() {
        val places = listOf(SavedPlace("Of\"fice", "addr"), SavedPlace("Nhà", "addr2"))
        val kb = TelegramInlineKeyboard.placesKeyboard(places)
        assertTrue(kb.contains("navi:0") && kb.contains("navi:1"), "callback dùng index (P2-17), không tên")
        assertTrue(kb.contains("\\\"")) // tên có " vẫn được escape ở nhãn
        assertEquals("", TelegramInlineKeyboard.placesKeyboard(emptyList()))
        // callback navi:1 ⇒ Nav("#1") để :app giải index
        assertEquals(TelegramCommand.Nav("#1"), TelegramUpdateParser.parseCallback("navi:1"))
    }
}
