package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Unit test THUẦN (off-device) cho NavParse — giá trị đi thẳng lên cụm nên phải bám sát. */
class NavParseTest {

    @Test fun `parseMeters đọc m và km, dấu phẩy hay chấm`() {
        assertEquals(250, NavParse.parseMeters("250 m"))
        assertEquals(500, NavParse.parseMeters("500m"))
        assertEquals(1200, NavParse.parseMeters("1.2 km"))
        assertEquals(1200, NavParse.parseMeters("1,2 km"))
        assertEquals(2000, NavParse.parseMeters("2 km"))
    }

    @Test fun `parseMeters trả -1 khi không có cự ly`() {
        assertEquals(-1, NavParse.parseMeters("Nguyễn Huệ"))
        assertEquals(-1, NavParse.parseMeters(""))
    }

    @Test fun `quantizeDisplay bước theo độ xa`() {
        assertEquals(1200, NavParse.quantizeDisplay(1234))   // >=1km: round 100 (1234→1200)
        assertEquals(350, NavParse.quantizeDisplay(347))     // 300..999: round 50 (347→350)
        assertEquals(550, NavParse.quantizeDisplay(525))     // 300..999: round 50 (525→550; bậc 25m cũ giữ 525)
        assertEquals(500, NavParse.quantizeDisplay(475))     // 300..999: round 50 (475→500)
        assertEquals(160, NavParse.quantizeDisplay(156))     // 100..299: round 10 (156→160)
        assertEquals(50, NavParse.quantizeDisplay(47))       // <100: round 10 (J2 1.16 floor→round)
        assertEquals(-1, NavParse.quantizeDisplay(-1))       // âm giữ nguyên
    }

    @Test fun `parseEta rút cự ly còn lại và giây`() {
        val (dis, sec) = NavParse.parseEta("10:32 · 5.2 km · 8 phút")
        assertEquals(5200, dis)
        assertEquals(8 * 60, sec)
    }

    @Test fun `parseEta có giờ cộng phút`() {
        val (_, sec) = NavParse.parseEta("2 giờ 5 phút")
        assertEquals(2 * 3600 + 5 * 60, sec)
    }

    @Test fun `parseEta thiếu thời gian trả -1`() {
        val (dis, sec) = NavParse.parseEta("5.2 km")
        assertEquals(5200, dis)
        assertEquals(-1, sec)
    }

    @Test fun `formatMeters khớp DashCast`() {
        assertEquals("1.5 km", NavParse.formatMeters(1500))
        assertEquals("500 m", NavParse.formatMeters(500))
    }

    @Test fun `formatSeconds giờ và phút`() {
        assertEquals("8 min", NavParse.formatSeconds(480))
        assertEquals("1h 2m", NavParse.formatSeconds(3720))
    }

    @Test fun `formatRemainTimeCn ra token tiếng Trung parseTime đọc được`() {
        assertEquals("8分", NavParse.formatRemainTimeCn(480))
        assertEquals("1时2分", NavParse.formatRemainTimeCn(3720))
        assertEquals("-1", NavParse.formatRemainTimeCn(-1))
    }

    @Test fun `extractArrivalClock chỉ nhận giờ hợp lệ`() {
        assertEquals("10:32", NavParse.extractArrivalClock("10:32 · 5.2 km"))
        assertNull(NavParse.extractArrivalClock("25:99 sai giờ"))
        assertNull(NavParse.extractArrivalClock("không có giờ"))
    }

    @Test fun `formatEtaCn bọc đúng khung 预计到达`() {
        assertEquals("预计今天10:32到达", NavParse.formatEtaCn("10:32"))
    }

    /**
     * KHOÁ §6 — [NavParse.extractArrivalClock] nuôi đường notification GMaps đang chạy ngoài hiện trường.
     * Hàm 24 h mới KHÔNG được đụng nó: bốn ca này là hợp đồng cũ, phải giữ y hệt.
     */
    @Test fun `extractArrivalClock 12h KHONG doi - duong GMaps giu nguyen`() {
        assertEquals("5:50", NavParse.extractArrivalClock("5:50 PM"))   // vẫn MẤT PM — cố ý, không sửa ở đây
        assertEquals("18:21", NavParse.extractArrivalClock("18:21"))
        assertEquals("0:04", NavParse.extractArrivalClock("00:04"))
        assertNull(NavParse.extractArrivalClock("25:99 sai giờ"))
    }

    /**
     * KHOÁ lỗi HAI MIỀN ĐỒNG HỒ trong MỘT ô holder (`NavViewIdSource.Reading.arrivalClock`).
     *
     * Waze phơi `lblArrivalTime='5:50 PM'` (chuỗi ĐÃ ĐO — KDoc `NavViewIdSource`), VietMap phơi `"00:04"`.
     * Trước sửa, producer Waze ghi THÔ ⇒ đo được ba kết cục hạ nguồn, cả ba đều hỏng:
     *   • `extractArrivalClock("5:50 PM")` = "5:50"  → sai 12 TIẾNG
     *   • `NavigationFrame.init` require `\d{1,2}:\d{2}` → "5:50 PM" **NÉM**
     *   • `BydHal` §ETA_H `split(":")` → [5, null] → giờ sai + phút rụng im lặng
     * Test này khoá **phép đổi miền** (THUẦN, off-car). Việc producer có THẬT SỰ gọi nó không thì `:app`
     * khoá bằng quét source: `NavSourceDwellWiringContractTest.producer view-id CHUAN HOA dong ho ve 24h…`
     * (hàm này chạm `AccessibilityNodeInfo` nên không chạy được trên android.jar stub của JVM).
     */
    @Test fun `extractArrivalClock24 doi AM PM sang 24h`() {
        assertEquals("17:50", NavParse.extractArrivalClock24("5:50 PM"))
        assertEquals("5:50", NavParse.extractArrivalClock24("5:50 AM"))
        assertEquals("0:05", NavParse.extractArrivalClock24("12:05 AM"))   // nửa đêm, KHÔNG phải 12:05
        assertEquals("12:05", NavParse.extractArrivalClock24("12:05 PM"))  // trưa, giữ 12
        assertEquals("23:59", NavParse.extractArrivalClock24("11:59 PM"))
        assertEquals("17:50", NavParse.extractArrivalClock24("5:50pm"))    // không khoảng trắng
        assertEquals("17:50", NavParse.extractArrivalClock24("5:50 p.m."))  // dạng có dấu chấm
        assertEquals("17:50", NavParse.extractArrivalClock24("Đến nơi 5:50 PM · 3,2 km"))
    }

    /** Không hậu tố ⇒ đã là 24 h: hàm mới là SIÊU TẬP, trả y hệt bản cũ (gồm cả ca VietMap đã đo). */
    @Test fun `extractArrivalClock24 giu nguyen chuoi 24h khong hau to`() {
        assertEquals("18:21", NavParse.extractArrivalClock24("18:21"))
        assertEquals("0:04", NavParse.extractArrivalClock24("00:04"))       // node (c) VietMap ĐÃ ĐO
        assertEquals("10:32", NavParse.extractArrivalClock24("10:32 · 5.2 km"))
    }

    /** Degrade-safe: mơ hồ/vô nghĩa ⇒ null, TUYỆT ĐỐI không đoán ra một giờ sai. */
    @Test fun `extractArrivalClock24 im lang khi vo nghia`() {
        assertNull(NavParse.extractArrivalClock24("18:21 PM"))   // 24 h mà lại có hậu tố ⇒ không đoán
        assertNull(NavParse.extractArrivalClock24("0:30 PM"))    // giờ 0 không tồn tại trong 12 h
        assertNull(NavParse.extractArrivalClock24("13:00 AM"))
        assertNull(NavParse.extractArrivalClock24("25:99 sai giờ"))
        assertNull(NavParse.extractArrivalClock24("không có giờ"))
        assertNull(NavParse.extractArrivalClock24(""))
    }
}
