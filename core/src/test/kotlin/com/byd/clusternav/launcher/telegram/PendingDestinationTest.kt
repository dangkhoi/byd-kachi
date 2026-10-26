package com.byd.clusternav.launcher.telegram

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingDestinationTest {

    @Test
    fun `round-trip giu dung truong`() {
        val p = PendingDestinations.of("Office", "Keangnam Landmark 72", listOf("gmaps", "vietmap"), "21.0045, 105.8412")!!
        val line = PendingDestinations.encode(p)
        val back = PendingDestinations.decode(line)!!
        assertEquals("Office", back.place.name)
        assertEquals("Keangnam Landmark 72", back.place.query)
        assertEquals(21.0045, back.place.lat)
        assertEquals(105.8412, back.place.lng)
        assertEquals(listOf("gmaps", "vietmap"), back.apps)
    }

    @Test
    fun `khong toa do van hop le`() {
        val p = PendingDestinations.of("Nhà", "123 Nguyễn Trãi", listOf("gmaps"))!!
        assertNull(p.place.lat)
        assertTrue(!p.place.hasCoords)
        // round-trip: hai trường toạ độ rỗng
        val back = PendingDestinations.decode(PendingDestinations.encode(p))!!
        assertNull(back.place.lat)
        assertEquals(listOf("gmaps"), back.apps)
    }

    @Test
    fun `thieu ten hoac dia chi hoac app ⇒ null`() {
        assertNull(PendingDestinations.of("", "địa chỉ", listOf("gmaps")))
        assertNull(PendingDestinations.of("Nhà", "", listOf("gmaps")))
        assertNull(PendingDestinations.of("Nhà", "địa chỉ", emptyList()))
        assertNull(PendingDestinations.of("Nhà", "địa chỉ", listOf("", "  ")))
    }

    @Test
    fun `ky tu ngan bi khu o cua vao — khong mat ban ghi`() {
        // tên + địa chỉ chứa '|', app chứa '+' và '|'
        val p = PendingDestinations.of("Of|fice", "Keang|nam 72", listOf("gm+aps", "viet|map"))!!
        val line = PendingDestinations.encode(p)
        // dòng vẫn đúng 5 trường ⇒ decode không mất bản ghi
        assertEquals(4, line.count { it == '|' })
        val back = PendingDestinations.decode(line)
        assertTrue(back != null, "decode phải ra điểm đến, không mất bản ghi vì '|' trong chữ người dùng")
    }

    @Test
    fun `decode dong hong ⇒ null`() {
        assertNull(PendingDestinations.decode(null))
        assertNull(PendingDestinations.decode(""))
        assertNull(PendingDestinations.decode("chỉ|ba|trường")) // sai số trường
        assertNull(PendingDestinations.decode("Nhà||||gmaps")) // địa chỉ rỗng
    }

    @Test
    fun `nua cap toa do ⇒ pending khong toa do`() {
        val back = PendingDestinations.decode("Nhà|123 NT|21.0|   |gmaps")!!
        assertNull(back.place.lat, "chỉ có lat, thiếu lng ⇒ coi như không toạ độ")
    }
}
