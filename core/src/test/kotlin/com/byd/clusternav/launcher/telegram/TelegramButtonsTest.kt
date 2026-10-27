package com.byd.clusternav.launcher.telegram

import com.byd.clusternav.launcher.SavedPlace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Callback + bảng nơi đã lưu (3.1.21 — bảng nút DUY NHẤT còn lại). */
class TelegramButtonsTest {

    private fun place(name: String) = SavedPlace(name, name, null, null)
    private val BTN = Regex("\\{\"text\":\"([^\"]+)\",\"callback_data\":\"([^\"]+)\"\\}")
    private fun buttons(m: String) = BTN.findAll(m).map { it.groupValues[1] to TelegramCallback.parse(it.groupValues[2]) }.toList()

    @Test fun `callback round-trip va chi nhan domain nav`() {
        val c = TelegramCallback(7, "nav", "go", "3")
        assertEquals(c, TelegramCallback.parse(c.encode()))
        assertEquals(setOf("nav"), TelegramCallback.DOMAINS)
        listOf("v1:ac:temp:24", "v1:seat:d:cool1", "v1:misc:trunk:on", "v1:menu:main:").forEach {
            assertNull(TelegramCallback.parse(it), "nút điều khiển xe cũ '$it' không còn được nhận (owner 26/10)")
        }
        assertNull(TelegramCallback.parse("vX:nav:go:1"))
        assertNull(TelegramCallback.parse("v1:nav:go:" + "x".repeat(70)))
    }

    @Test fun `bang noi da luu - ten tieng Viet chi o nhan, callback la chi so, luon co New`() {
        val m = TelegramMenu.places(4, listOf(place("Nhà"), place("Công ty Đông Á"), place("Gym")))
        val b = buttons(m)
        assertEquals(listOf("Nhà", "Công ty Đông Á", "Gym", "➕ New destination"), b.map { it.first })
        assertEquals(TelegramCallback(4, "nav", "go", TelegramMenu.placeId(1, place("Công ty Đông Á"))), b[1].second)
        assertEquals(TelegramCallback(4, "nav", "new"), b.last().second)
        assertFalse(b.any { it.second?.id?.contains("Đông") == true })
    }

    @Test fun `phan trang 8 noi - trang sau co More`() {
        val all = (1..11).map { place("P$it") }
        val p0 = buttons(TelegramMenu.places(1, all, 0))
        assertTrue(p0.any { it.first == "⬇️ More (3)" && it.second == TelegramCallback(1, "nav", "page", "1") })
        assertEquals(8, p0.count { it.second?.action == "go" })
        val p1 = buttons(TelegramMenu.places(1, all, 1))
        assertEquals(listOf(8, 9, 10), p1.filter { it.second?.action == "go" }.map { TelegramMenu.placeIndex(it.second!!.id, all) })
        assertTrue(p1.none { it.first.startsWith("⬇️") })
    }

    @Test fun `so dia chi doi sau khi gui bang - nut cu khong dan nham noi khac`() {
        val before = listOf(place("Nhà"), place("Công ty"))
        val id = TelegramMenu.placeId(1, before[1])
        assertEquals(1, TelegramMenu.placeIndex(id, before))
        assertEquals(null, TelegramMenu.placeIndex(id, listOf(place("Nhà"), place("Gym"))), "chỉ số 1 nay là Gym ⇒ từ chối")
        assertEquals(null, TelegramMenu.placeIndex(id, listOf(place("Nhà"))), "mất nơi ⇒ từ chối")
        assertEquals(null, TelegramMenu.placeIndex("rác", before))
    }

    @Test fun `so trong - van co New`() {
        assertEquals(listOf("➕ New destination"), buttons(TelegramMenu.places(1, emptyList())).map { it.first })
    }

    @Test fun `noi ghim len dau co dau ghim, callback van la chi so trong so`() {
        val all = listOf(place("Nhà"), place("Công ty"), place("Gym"), place("Mẹ"))
        val b = buttons(TelegramMenu.places(2, all, 0, pinned = listOf("Gym", "Không còn", "Nhà")))
        assertEquals(listOf("📌 Gym", "📌 Nhà", "Công ty", "Mẹ", "➕ New destination"), b.map { it.first })
        assertEquals(2, TelegramMenu.placeIndex(b[0].second!!.id, all), "Gym vẫn trỏ đúng chỉ số 2 trong sổ")
    }

    @Test fun `ghim toi da 3 noi, bam lai thi bo ghim`() {
        val three = listOf("A", "B", "C")
        assertEquals(three, TelegramMenu.togglePin(three, "D"), "đầy 3 ⇒ không thêm")
        assertEquals(listOf("A", "C"), TelegramMenu.togglePin(three, "B"))
        assertEquals(listOf("A", "B"), TelegramMenu.togglePin(listOf("A"), "B"))
    }

    @Test fun `bang chon app - Google Maps, VietMap, ca hai, sao o app mac dinh`() {
        val id = TelegramMenu.placeId(1, place("Công ty"))
        val b = buttons(TelegramMenu.appPicker(5, id, "vietmap", isPinned = false, canPin = true))
        assertEquals(listOf("🗺 Google Maps", "🧭 VietMap ⭐", "🗺+🧭 Both", "📌 Pin to top", "⬅️ Back"), b.map { it.first })
        assertEquals(id to listOf("gmaps"), TelegramMenu.appChoice(b[0].second!!.id))
        assertEquals(id to listOf("vietmap"), TelegramMenu.appChoice(b[1].second!!.id))
        assertEquals(id to listOf("gmaps", "vietmap"), TelegramMenu.appChoice(b[2].second!!.id))
        assertEquals(TelegramCallback(5, "nav", "pin", id), b[3].second)
        assertTrue(buttons(TelegramMenu.appPicker(5, id, "gmaps", isPinned = true, canPin = false)).any { it.first == "📍 Unpin" })
        assertFalse(buttons(TelegramMenu.appPicker(5, id, "gmaps", isPinned = false, canPin = false)).any { it.first.startsWith("📌") })
        assertNull(TelegramMenu.appChoice("$id:x"))
        assertNull(TelegramMenu.appChoice(":g"))
    }
}
