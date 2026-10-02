package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.ProfileScope
import com.byd.clusternav.launcher.SettingsCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá CỔNG CHUYẾN LÊN XE (spec shortcuts-autostart R2.2 · R2.3 · C5): đúng một lượt mỗi lần nổ máy THẬT.
 *
 * Mốc thời gian dựng theo đúng chuỗi đã đo trên xe 29/09 (KDoc [TripGate]): BYD giết Kachi lúc tắt máy ⇒ tiến trình
 * dựng lại lúc màn TẮT ⇒ lớp 1 claim `a11y_tat_may_elapsed` ⇒ màn bật (mở xe) ⇒ chuỗi SẴN ⇒ chuyến.
 */
class TripGateTest {

    private val boot = "n39"

    @Test
    fun `khoa may - BOOT_COUNT doc duoc thi dung no, khong thi phut bat may theo gio tuong`() {
        assertEquals("n39", TripGate.bootKey(39, wallNow = 1_000_000_000L, elapsedNow = 5_000L))
        // Không đọc được ⇒ (giờ tường − elapsed) theo phút: 1_000_000_000 − 400_000 = 999_600_000 ms ⇒ phút 16660.
        assertEquals("w16660", TripGate.bootKey(null, wallNow = 1_000_000_000L, elapsedNow = 400_000L))
        assertEquals("w16660", TripGate.bootKey(-1, wallNow = 1_000_000_000L, elapsedNow = 400_000L))
    }

    @Test
    fun `id chuyen - claim tat may cua lan khoi dong nay, hoac b khi chua co claim`() {
        assertEquals("n39.t5000", TripGate.tripId(boot, tatMayAt = 5_000L, elapsedNow = 60_000L))
        assertEquals("n39.b", TripGate.tripId(boot, tatMayAt = -1L, elapsedNow = 60_000L))
        // Mốc LỚN hơn "bây giờ" = của đời máy trước (elapsedRealtime về 0 khi khởi động lại) ⇒ không tính.
        assertEquals("n39.b", TripGate.tripId(boot, tatMayAt = 900_000L, elapsedNow = 60_000L))
    }

    @Test
    fun `ba lan tat may kieu BYD roi bat man - dung ba luot, tat bat man khong giet thi khong co luot`() {
        var ledger: TripGate.Ledger? = null
        var runs = 0
        // Mỗi phần tử: (claim tắt-máy hiện có, lúc màn bật, lúc chuỗi SẴN gọi chuyến) — elapsedRealtime một đời máy.
        val wakes = listOf(
            Triple(10_000L, 40_000L, 41_000L),      // lần nổ máy 1
            Triple(10_000L, 300_000L, 301_000L),    // tắt/bật màn giữa đường, KHÔNG bị giết ⇒ claim cũ
            Triple(500_000L, 530_000L, 531_000L),   // lần nổ máy 2
            Triple(900_000L, 930_000L, 931_000L),   // lần nổ máy 3
        )
        wakes.forEach { (claim, wake, now) ->
            val trip = TripGate.tripId(boot, claim, now)
            when (val d = TripGate.decide(ledger, trip, firstWakeAt = wake, now = now)) {
                is TripGate.Decision.Run -> { runs++; ledger = d.claim.copy(phase = TripGate.Phase.FIRED) }
                is TripGate.Decision.Done -> assertEquals(TripGate.Skip.ALREADY_FIRED, d.why)
                is TripGate.Decision.Close -> error("không được đóng: $d")
            }
        }
        assertEquals(3, runs, "ba lần nổ máy thật = ba lượt; tắt/bật màn không kèm giết = 0")
    }

    @Test
    fun `nang cap APK giua chuyen - tien trinh moi cung id, so da FIRED - khong chay`() {
        val trip = TripGate.tripId(boot, 10_000L, 120_000L)
        val fired = TripGate.Ledger(trip, TripGate.Phase.FIRED, 1)
        // Tiến trình mới bật lúc màn SÁNG (MY_PACKAGE_REPLACED) ⇒ không có claim mới ⇒ cùng id.
        val again = TripGate.tripId(boot, 10_000L, 400_000L)
        assertEquals(trip, again)
        assertEquals(TripGate.Decision.Done(TripGate.Skip.ALREADY_FIRED), TripGate.decide(fired, again, 380_000L, 400_000L))
    }

    @Test
    fun `bi giet giua chuyen - CLAIMED chua FIRED - tien trinh moi chay tiep dung mot lan`() {
        val trip = "n39.t10000"
        val claimed = TripGate.Ledger(trip, TripGate.Phase.CLAIMED, 1)
        val d = TripGate.decide(claimed, trip, firstWakeAt = 50_000L, now = 52_000L)
        assertEquals(TripGate.Decision.Run(TripGate.Ledger(trip, TripGate.Phase.CLAIMED, 2)), d)
        // Lần thứ ba (cũng bị giết giữa chừng) ⇒ đóng GAVE_UP, không chạy nữa.
        val third = TripGate.decide(TripGate.Ledger(trip, TripGate.Phase.CLAIMED, 2), trip, 60_000L, 61_000L)
        assertEquals(TripGate.Decision.Close(TripGate.Ledger(trip, TripGate.Phase.FIRED, 2), TripGate.Code.GAVE_UP), third)
    }

    @Test
    fun `khoi dong lai that - claim cu nam lot trong 0 now van ra chuyen moi nho khoa may`() {
        val before = TripGate.Ledger("n38.t70000", TripGate.Phase.FIRED, 1)
        // Đời máy mới: claim cũ 70 000 ≤ now 90 000 ⇒ "trông như của lần này", nhưng khoá máy đã đổi n38 → n39.
        val trip = TripGate.tripId("n39", tatMayAt = 70_000L, elapsedNow = 90_000L)
        assertTrue(TripGate.decide(before, trip, 80_000L, 90_000L) is TripGate.Decision.Run)
    }

    @Test
    fun `het han chuyen - qua 180 s tu lan thuc dau - dong EXPIRED, khong lenh`() {
        val trip = "n39.t1000"
        val d = TripGate.decide(null, trip, firstWakeAt = 10_000L, now = 10_000L + TripGate.TRIP_DEADLINE_MS + 1)
        assertEquals(TripGate.Decision.Close(TripGate.Ledger(trip, TripGate.Phase.FIRED, 1), TripGate.Code.EXPIRED), d)
        assertTrue(TripGate.decide(null, trip, 10_000L, 10_000L + TripGate.TRIP_DEADLINE_MS) is TripGate.Decision.Run)
        assertFalse(TripGate.withinDeadline(10_000L, 10_000L + TripGate.TRIP_DEADLINE_MS + 1))
        assertTrue(TripGate.withinDeadline(10_000L, 10_000L + TripGate.TRIP_DEADLINE_MS))
    }

    @Test
    fun `chua co moc thuc - khong chay`() {
        assertEquals(TripGate.Decision.Done(TripGate.Skip.NO_WAKE), TripGate.decide(null, "n39.b", -1L, 5_000L))
        assertFalse(TripGate.withinDeadline(-1L, 5_000L))
    }

    @Test
    fun `cho khoi dong - BOOT_COMPLETED cua dung lan nay, hoac du 20 s tu luc thuc`() {
        assertTrue(TripGate.bootReady("n39", "n39", firstWakeAt = 1_000L, now = 2_000L))
        assertFalse(TripGate.bootReady("n38", "n39", firstWakeAt = 1_000L, now = 2_000L), "khoá của đời máy trước")
        assertFalse(TripGate.bootReady(null, "n39", 1_000L, 1_000L + TripGate.BOOT_WAIT_MS - 1))
        assertTrue(TripGate.bootReady(null, "n39", 1_000L, 1_000L + TripGate.BOOT_WAIT_MS))
    }

    @Test
    fun `ma hoa so - khu hoi, chuoi hong thanh null`() {
        val l = TripGate.Ledger("n39.t5000", TripGate.Phase.CLAIMED, 2)
        assertEquals("v=1;trip=n39.t5000;phase=CLAIMED;tries=2", TripGate.encode(l))
        assertEquals(l, TripGate.decode(TripGate.encode(l)))
        listOf(null, "", "rác", "v=2;trip=a;phase=FIRED;tries=1", "v=1;trip=a b;phase=FIRED;tries=1", "v=1;trip=x;phase=NOPE;tries=1", "v=1;trip=x;phase=FIRED;tries=-1")
            .forEach { assertNull(TripGate.decode(it), "phải là null: $it") }
    }

    @Test
    fun `ket qua - khu hoi, chi tiet bi loc ve ASCII an toan`() {
        val r = TripGate.Result("n39.b", TripGate.Code.RAN, 1_700_000_000_000L, "com.waze:bg-MOVED | nhạc;x=1")
        val back = TripGate.decodeResult(TripGate.encodeResult(r))!!
        assertEquals(TripGate.Code.RAN, back.code)
        assertEquals("com.waze:bg-MOVED | nhcx1", back.detail, "dấu ; và = không được lọt vào sổ (sẽ vỡ trường)")
        assertNull(TripGate.decodeResult("v=1;trip=n39.b;code=WHAT;at=1"))
    }

    @Test
    fun `ba khoa theo xe duoc khai o ProfileScope DEVICE va SettingsCatalog NOT_SETTINGS`() {
        listOf(TripGate.KEY_LEDGER, TripGate.KEY_LAST, TripGate.KEY_BOOT_SEEN).forEach { k ->
            assertEquals(ProfileScope.Scope.DEVICE, ProfileScope.scopeOf(k), k)
            assertTrue(k in SettingsCatalog.NOT_SETTINGS, k)
        }
    }
}
