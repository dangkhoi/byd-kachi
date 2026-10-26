package com.byd.clusternav.launcher.telegram

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 8 việc owner liệt kê 26/10 (3.1.21 — chỉ dẫn đường + nhạc), mỗi việc một bài. */
class TelegramParserTest {

    private fun p(s: String) = TelegramUpdateParser.parseText(s)
    private val GMAPS = "https://maps.app.goo.gl/XZq7xxaoJYUp7e1e6?g_st=ac"

    @Test fun `1 - link Google Maps tron thi dan, them vietmap thi dan ca hai`() {
        assertEquals(TelegramCommand.Nav(GMAPS), p(GMAPS))
        assertEquals(TelegramCommand.Nav(GMAPS), p("Nacasoo Hill\n$GMAPS"), "tin Chia sẻ của Google Maps có tên + link")
        assertEquals(TelegramCommand.Nav(GMAPS, apps = listOf("gmaps", "vietmap")), p("$GMAPS gmaps vietmap"))
        assertEquals(TelegramCommand.Nav(GMAPS, apps = listOf("vietmap")), p("$GMAPS + vietmap"))
        assertEquals(TelegramCommand.Nav(GMAPS, apps = listOf("gmaps", "vietmap")), p("/go $GMAPS gmaps+vietmap"))
    }

    @Test fun `2 3 - link YouTube va YT Music thanh nhac theo dung app`() {
        val yt = "https://youtu.be/dQw4w9WgXcQ"; val ytm = "https://music.youtube.com/watch?v=abc123"
        assertEquals(TelegramCommand.Music(yt, isQuery = false), p(yt))
        assertEquals(TelegramCommand.Music(ytm, isQuery = false), p("nghe bài này $ytm"))
        assertEquals(TelegramCommand.Music(yt, isQuery = false), p("/play $yt"))
        assertEquals("youtube", MusicLink.appOf(yt)); assertEquals("youtube", MusicLink.appOf("https://www.youtube.com/watch?v=x"))
        assertEquals("ytmusic", MusicLink.appOf(ytm))
    }

    @Test fun `4 - play ten bai la App nhac mac dinh`() {
        assertEquals(TelegramCommand.Music("1 triệu like", isQuery = true, app = null), p("/play 1 triệu like"))
    }

    @Test fun `5 6 - play ten bai kem youtube hoac ytmusic`() {
        assertEquals(TelegramCommand.Music("1 triệu like", true, "youtube"), p("/play 1 triệu like youtube"))
        assertEquals(TelegramCommand.Music("1 triệu like", true, "youtube"), p("/play 1 triệu like + youtube"))
        assertEquals(TelegramCommand.Music("Chúng ta của hiện tại", true, "ytmusic"), p("/play Chúng ta của hiện tại ytmusic"))
        assertEquals(TelegramCommand.Music("Chúng ta của hiện tại", true, "ytmusic"), p("/play Chúng ta của hiện tại +ytm"))
    }

    @Test fun `7 - go dia diem la app mac dinh`() {
        assertEquals(TelegramCommand.Nav("Công ty"), p("/go Công ty"))
        assertEquals(TelegramCommand.Nav("1 Đại Cồ Việt Hà Nội"), p("/go 1 Đại Cồ Việt Hà Nội"))
        assertEquals(TelegramCommand.Nav("21.0285, 105.8542"), p("/go 21.0285, 105.8542"))
    }

    @Test fun `8 - go dia diem kem app, moi cach viet`() {
        listOf("/go Công ty gmaps vietmap", "/go Công ty gmaps+vietmap", "/go Công ty + gmaps + vietmap").forEach {
            assertEquals(TelegramCommand.Nav("Công ty", apps = listOf("gmaps", "vietmap")), p(it), it)
        }
        assertEquals(TelegramCommand.Nav("Công ty", apps = listOf("waze")), p("/go Công ty waze"))
        assertEquals(TelegramCommand.Nav("Waze Café", apps = emptyList()), p("/go Waze Café"), "tên app ở GIỮA/ĐẦU không bị tách")
        assertEquals(TelegramCommand.Nav("Công ty"), p("/go Công ty +"), "dấu + thừa ở cuối bị bỏ")
    }

    @Test fun `khong con lenh dieu khien xe, chu tron khong thanh lenh`() {
        listOf("/ac on", "/trunk open", "/temp 22", "/seat 2", "/readlight on", "/status").forEach {
            assertTrue(p(it) is TelegramCommand.Unknown, "'$it' phải là Unknown (owner 26/10: không đọc ghi gì vào xe)")
        }
        assertTrue(p("ok thanks") is TelegramCommand.Unknown)
        assertEquals(TelegramCommand.ListPlaces, p("/go")); assertEquals(TelegramCommand.ListPlaces, p("/places"))
        assertEquals(TelegramCommand.AskSong, p("/play"))
        assertEquals(TelegramCommand.Menu, p("/start")); assertEquals(TelegramCommand.Cancel, p("/cancel"))
        assertEquals(TelegramCommand.Nav("Công ty"), p("/go@kachi_bot Công ty"), "lệnh trong nhóm có @bot")
    }
}
