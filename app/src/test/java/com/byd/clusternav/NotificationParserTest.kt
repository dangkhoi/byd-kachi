package com.byd.clusternav

import com.byd.clusternav.navigation.NavParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit test THUẦN cho NotificationParser (arrow=null → không cần Android runtime). GMaps vs field-đảo. */
class NotificationParserTest {

    @Test fun `GMaps layout - cự ly ở title, đường ở text`() {
        val s = NotificationParser.parse("com.google.android.apps.maps", "250 m", "Nguyễn Huệ", "", "", null, 3)!!
        assertTrue(s.active)
        assertEquals("250 m", s.distance)
        assertEquals("Nguyễn Huệ", s.road)
        assertEquals(3, s.maneuverIcon)
        assertEquals(250, NavParse.parseMeters(s.distance))
    }

    @Test fun `field-đảo (VietMap) - cự ly ở text, đường ở title`() {
        val s = NotificationParser.parse("vn.vietmap.app", "Nguyễn Huệ", "300 m", "", "", null)!!
        assertEquals("300 m", s.distance)
        assertEquals("Nguyễn Huệ", s.road)
    }

    @Test fun `title lẫn text rỗng → null`() {
        assertNull(NotificationParser.parse("pkg", "", "", "", "", null))
    }

    @Test fun `cự ly dấu phẩy chuẩn hoá về chấm`() {
        val s = NotificationParser.parse("com.google.android.apps.maps", "1,2 km", "Xa lộ HN", "", "", null)!!
        assertEquals("1.2 km", s.distance)
        assertEquals(1200, NavParse.parseMeters(s.distance))
    }
}
