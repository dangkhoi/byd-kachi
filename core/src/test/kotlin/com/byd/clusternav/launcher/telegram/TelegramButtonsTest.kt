package com.byd.clusternav.launcher.telegram

import com.byd.clusternav.launcher.SavedPlace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class TelegramButtonsTest {

    private fun place(name: String) = SavedPlace(name, name, null, null)

    // ---- TelegramCallback ----

    @Test fun `callback round-trip`() {
        val c = TelegramCallback(7, "ac", "temp", "24")
        assertEquals(c, TelegramCallback.parse(c.encode()))
    }

    @Test fun `callback id rong`() {
        val c = TelegramCallback(1, "menu", "main")
        assertEquals(c, TelegramCallback.parse(c.encode()))
    }

    @Test fun `callback id chua dau hai cham (also vietmap index)`() {
        // id = "vietmap:3" — split limit 4 nên dấu ':' trong id KHÔNG bị cắt.
        val c = TelegramCallback(2, "nav", "also", "vietmap:3")
        assertEquals(c, TelegramCallback.parse(c.encode()))
    }

    @Test fun `callback domain la ⇒ null`() {
        assertNull(TelegramCallback.parse("v1:bogus:x:y"))
    }

    @Test fun `callback nonce khong phai so ⇒ null`() {
        assertNull(TelegramCallback.parse("vX:ac:temp:24"))
    }

    @Test fun `callback khong bat dau v ⇒ null`() {
        assertNull(TelegramCallback.parse("1:ac:temp:24"))
    }

    @Test fun `callback vuot 64 byte ⇒ null khi parse`() {
        val tooLong = "v1:nav:go:" + "x".repeat(70)
        assertTrue(tooLong.toByteArray(Charsets.UTF_8).size > TelegramCallback.MAX_BYTES)
        assertNull(TelegramCallback.parse(tooLong))
    }

    @Test fun `ten dia diem tieng Viet KHONG vao callback (dung index)`() {
        // Tên 40 ký tự tiếng Việt — nếu nhét vào callback sẽ vỡ 64 byte. Menu phải dùng index.
        val longVi = "Trường mầm non Hoa Sen Quận 7 Thành phố HCM"
        val markup = TelegramMenu.nav(1, listOf(place(longVi)), page = 0)
        // callback cho nơi đầu phải là index "0", không chứa tên.
        assertTrue(markup.contains("\"callback_data\":\"v1:nav:go:0\""), markup)
        // tên KHÔNG xuất hiện trong BẤT KỲ callback_data nào (chỉ ở nhãn "text").
        val callbacks = Regex("\"callback_data\":\"([^\"]*)\"").findAll(markup).map { it.groupValues[1] }.toList()
        assertTrue(callbacks.none { it.contains("Hoa Sen") }, "tên KHÔNG được vào callback_data: $callbacks")
        // nhưng NHÃN thì vẫn hiện tên.
        assertTrue(markup.contains("Trường mầm non"), markup)
    }

    // ---- TelegramStatusLine ----

    @Test fun `status null ⇒ gach ngang, khong bia`() {
        val s = TelegramStatusLine.Snapshot(
            carLinked = true, gpsOk = null, acAuto = null, tempC = null, fan = null,
            seatDriver = null, seatPassenger = null, pendingPlace = null, nowPlaying = null, updatedHhmm = "14:30",
        )
        val t = TelegramStatusLine.render(s)
        assertTrue(t.contains("AUTO —"), t)           // acAuto null → "—"
        assertTrue(t.contains("fan —"), t)
        assertTrue(t.contains("GPS —"), t)
        assertTrue(t.contains("No destination"), t)
        assertTrue(t.contains("car linked"), t)
    }

    @Test fun `status co so ⇒ hien dung`() {
        val s = TelegramStatusLine.Snapshot(
            carLinked = true, gpsOk = true, acAuto = true, tempC = 24, fan = 4,
            seatDriver = "cool 1", seatPassenger = "off", pendingPlace = "Office",
            nowPlaying = "Song — Artist", updatedHhmm = "14:32",
        )
        val t = TelegramStatusLine.render(s)
        assertTrue(t.contains("AUTO ON · 24° · fan 4"), t)
        assertTrue(t.contains("Driver: cool 1 · Passenger: off"), t)
        assertTrue(t.contains("To: Office · GPS ✓"), t)
        assertTrue(t.contains("♪ Song — Artist"), t)
    }

    @Test fun `status xe khong noi ⇒ bao ro`() {
        val s = TelegramStatusLine.Snapshot(false, null, null, null, null, null, null, null, null, "00:00")
        assertTrue(TelegramStatusLine.render(s).contains("car NOT linked"))
    }

    // ---- TelegramMenu ----

    @Test fun `menu chinh co hang nhanh 2 ghim + macro`() {
        val markup = TelegramMenu.main(5, listOf(place("Nhà"), place("Công ty")))
        assertTrue(markup.contains("📌 Nhà"), markup)
        assertTrue(markup.contains("📌 Công ty"), markup)
        assertTrue(markup.contains("Cool 24°"), markup)
        assertTrue(markup.contains("v5:ac:macro:cool24"), markup)
    }

    @Test fun `menu chinh toi da 2 ghim`() {
        val markup = TelegramMenu.main(1, listOf(place("A"), place("B"), place("C")))
        assertTrue(markup.contains("📌 A") && markup.contains("📌 B"))
        assertFalse(markup.contains("📌 C"), "chỉ ghim 2 nơi đầu")
    }

    @Test fun `ac preset la lenh tuyet doi (set-to-X)`() {
        val markup = TelegramMenu.ac(3, autoOn = true, tempC = 22, fan = 4, defrostOn = false)
        // nút 24° mang id "24" (đặt tuyệt đối), KHÔNG phải "+1"/"up"
        assertTrue(markup.contains("v3:ac:temp:24"), markup)
        assertTrue(markup.contains("22° ✓"), "nhiệt hiện tại có dấu ✓")
        assertTrue(markup.contains("Defrost"), markup)
        assertFalse(markup.contains("PM2.5"), "PM2.5 chuyển sang bảng Khác (QD-22)")
    }

    @Test fun `ac nut gia tri giua la noop (nut chet)`() {
        val markup = TelegramMenu.ac(1, null, 24, null, null)
        assertTrue(markup.contains("v1:ac:noop"), "nút giá trị giữa −/+ là nút chết")
        assertTrue(markup.contains("v1:ac:tempdn") && markup.contains("v1:ac:tempup"))
    }

    @Test fun `seat bay phang 4 muc + tat ca hai ghe`() {
        val markup = TelegramMenu.seat(2, driver = "cool1", passenger = null)
        assertTrue(markup.contains("v2:seat:d:cool1") && markup.contains("v2:seat:d:heat2"))
        assertTrue(markup.contains("v2:seat:d:off") && markup.contains("v2:seat:p:off"))
        assertTrue(markup.contains("Cool 1 ✓"), "mức hiện tại của ghế lái có ✓")
    }

    @Test fun `music transport doi nhan theo playing`() {
        assertTrue(TelegramMenu.music(1, playing = true).contains("⏸ Pause"))
        assertTrue(TelegramMenu.music(1, playing = false).contains("▶️ Play"))
        assertTrue(TelegramMenu.music(1, true).contains("v1:music:prev") && TelegramMenu.music(1, true).contains("v1:music:next"))
    }

    @Test fun `nav phan trang 6 noi + xem them`() {
        val places = (1..15).map { place("P$it") }
        val p0 = TelegramMenu.nav(1, places, page = 0)
        assertTrue(p0.contains("P1") && p0.contains("P6"))
        assertFalse(p0.contains("\"text\":\"P7\""), "trang 0 chỉ 6 nơi")
        assertTrue(p0.contains("More (9)"), "còn 9 nơi")
        val p1 = TelegramMenu.nav(1, places, page = 1)
        assertTrue(p1.contains("v1:nav:go:6"), "trang 1 bắt đầu từ index 6")
    }

    @Test fun `nav xac nhan co nut Huy (chi o day)`() {
        val markup = TelegramMenu.navConfirm(1, 3)
        assertTrue(markup.contains("Cancel destination"), markup)
        assertTrue(markup.contains("v1:nav:also:vietmap:3") && markup.contains("v1:nav:also:waze:3"))
        // menu chính KHÔNG có nút Huỷ
        assertFalse(TelegramMenu.main(1, emptyList()).contains("Cancel"), "menu chính không có Huỷ")
    }
}
