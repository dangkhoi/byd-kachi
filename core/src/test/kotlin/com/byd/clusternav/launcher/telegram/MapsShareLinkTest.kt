package com.byd.clusternav.launcher.telegram

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 3.1.20 — link chia sẻ Google Maps ⇒ điểm đến. Mẫu URL là [ĐO 26/10] từ link owner gửi (mã rút gọn bỏ bớt). */
class MapsShareLinkTest {

    // Đích chuyển hướng THẬT của maps.app.goo.gl/XZq7… (rút phần tham số theo dõi).
    private val placeUrl = "https://www.google.com/maps/place/Nacasoo+Hill,+Th%C3%B4n+2,+Nam+Ban+L%C3%A2m+H%C3%A0,+L%C3%A2m+%C4%90%E1%BB%93ng+670000/" +
        "data=!4m2!3m1!1s0x31716b9d95090f33:0xad7c038fdd4d0026!18m1!1e1?utm_source=mstt_1&entry=gps&g_st=ac"

    @Test fun `trang place khong toa do - lay ten va dia chi cho Google Maps tra`() {
        val t = MapsShareLink.parse(placeUrl)!!
        assertEquals("Nacasoo Hill", t.name)
        assertEquals("Nacasoo Hill, Thôn 2, Nam Ban Lâm Hà, Lâm Đồng 670000", t.query)
        assertNull(t.coords, "URL không có toạ độ ⇒ KHÔNG bịa")
    }

    @Test fun `ghim 3d 4d thang tam khung, tham so q so dung duoc`() {
        val t = MapsShareLink.parse("https://www.google.com/maps/place/Cho+Ben+Thanh/@10.77,106.69,17z/data=!3d10.772461!4d106.698055")!!
        assertEquals("10.772461, 106.698055", t.coords, "ghim nơi thắng tâm khung @")
        assertEquals("Cho Ben Thanh", t.name)
        assertEquals("21.0285, 105.8542", MapsShareLink.parse("https://maps.google.com/?q=21.0285,105.8542")!!.coords)
        assertEquals("1 Đại Cồ Việt", MapsShareLink.parse("https://www.google.com/maps/search/?api=1&query=1+%C4%90%E1%BA%A1i+C%E1%BB%93+Vi%E1%BB%87t")!!.query)
    }

    @Test fun `nhan ra link trong tin chia se, bo link khac`() {
        val share = "Nacasoo Hill\nhttps://maps.app.goo.gl/XZq7xxaoJYUp7e1e6?g_st=ac"
        assertEquals("https://maps.app.goo.gl/XZq7xxaoJYUp7e1e6?g_st=ac", MapsShareLink.find(share))
        assertTrue(MapsShareLink.isShort(MapsShareLink.find(share)!!))
        assertNull(MapsShareLink.find("https://youtube.com/watch?v=abc"))
        assertNull(MapsShareLink.find("https://evil.example/maps.app.goo.gl/x"), "chỉ đúng host Google")
    }

    @Test fun `dan link tron hoac go go link deu thanh dan duong, play link van la nhac`() {
        val link = "https://maps.app.goo.gl/XZq7xxaoJYUp7e1e6?g_st=ac"
        assertEquals(TelegramCommand.Nav(link), TelegramUpdateParser.parseText("Nacasoo Hill\n$link"))
        assertEquals(TelegramCommand.Nav(link), TelegramUpdateParser.parseText("/go $link"))
        assertTrue(TelegramUpdateParser.parseText("/play https://youtu.be/x") is TelegramCommand.Music)
    }
}
