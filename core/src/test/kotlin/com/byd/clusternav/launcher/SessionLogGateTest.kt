package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá luật của [SessionLogGate] (LOG-VOLUME 3.1.10). Dòng mẫu đúng khuôn `logcat -v time` của tệp usage thật
 * (diagnostics `log-volume-2026-10-16.md`), nội dung đã ẩn danh (tên đường → `w1 w2`).
 *
 * Mỗi bài là một cách bản vá này hoá thành có hại: nuốt một đổi trạng thái, cắt giữa vết gọi, đổi hành vi dòng bằng
 * chứng mức I (`KachiCtl` ctl/ctl-after, `KachiHalEvent`), hoặc bỏ dòng không để lại số đếm.
 */
class SessionLogGateTest {

    private fun line(sev: Char, at: String, tag: String, msg: String, pid: Int = 29591) =
        "10-16 $at $sev/$tag($pid): $msg"

    @Test
    fun `F A khong bao gio bi bo`() {
        val g = SessionLogGate()
        listOf('F', 'A').forEach { sev ->
            val l = line(sev, "10:32:10.000", "libc", "Fatal signal 11")
            val again = line(sev, "10:32:10.500", "libc", "Fatal signal 11")
            assertEquals(l, g.offer(l, 0))
            assertEquals(again, g.offer(again, 500), "mức $sev lặp y nguyên VẪN ghi")
        }
    }

    /** E/FrameEvents lặp y nguyên ~500 lần/chuyến [ĐO 16/10]: bản đầu ghi ngay, bản sao thành số đếm. */
    @Test
    fun `W E lap y nguyen thanh so dem, ban dau van ghi ngay`() {
        val g = SessionLogGate()
        val e = { at: String -> line('E', at, "FrameEvents", "updateAcquireFence: Did not find frame.", 0) }
        assertEquals(e("09:52:08.417"), g.offer(e("09:52:08.417"), 0))
        assertNull(g.offer(e("09:52:09.100"), 700))
        assertNull(g.offer(e("09:52:12.000"), 3_600))
        assertEquals(e("09:52:18.417") + "   [+2 lặp]", g.offer(e("09:52:18.417"), 10_000))
        // Lỗi KHÁC chữ đi qua ngay dù cùng tag.
        val other = line('E', "09:52:18.500", "FrameEvents", "updateAcquireFence: something else", 0)
        assertEquals(other, g.offer(other, 10_083))
    }

    /** Dòng bằng chứng mức I giữ ĐÚNG hành vi của bộ tiết chế 3.1.9: nguyên văn, 10 s, chuỗi mới qua ngay. */
    @Test
    fun `muc I giu nguyen hanh vi cu - ctl voi so khac nhau deu qua`() {
        val g = SessionLogGate()
        val old = LogLineThrottle()
        val lines = listOf(
            line('I', "10:01:39.965", "KachiCtl", "pid=29591 ctl #2 id=temp m=named:setAcTemperature args=[0,23,0,1] rc=0 before=24 why=cmd"),
            line('I', "10:01:40.400", "KachiCtl", "pid=29591 ctl #3 id=temp m=named:setAcTemperature args=[0,22,0,1] rc=0 before=23 why=cmd"),
            line('I', "10:01:42.968", "KachiCtl", "pid=29591 ctl-after #2 id=temp after=23"),
            line('I', "10:01:43.400", "KachiCtl", "pid=29591 ctl-after #3 id=temp after=22"),
            line('I', "10:01:44.000", "KachiHalSignal", "xi-nhan trái=false phải=true"),
            line('I', "10:01:45.000", "KachiHalSignal", "xi-nhan trái=false phải=true"),
        )
        lines.forEachIndexed { i, l ->
            val now = i * 1_000L
            val before = old.suppressedBefore(l.substring(SessionLogGate.SEVERITY_COL), now)?.let { l + LogLineThrottle.repeatSuffix(it) }
            assertEquals(before, g.offer(l, now), "mức I phải giống hệt 3.1.9: $l")
        }
    }

    /** Cự ly dẫn đường đếm lùi mỗi giây = số đo: một dòng / 10 s, ghi giá trị HIỆN TẠI kèm số dòng đã gộp. */
    @Test
    fun `D so do gop trong 10 s, ghi gia tri hien tai kem so dem`() {
        val g = SessionLogGate()
        val d = { at: String, v: Int -> line('D', at, "BYDAutoInstrumentDevice", "sendSimpleGuidanceInfo: the simple type is 2, and front compress diatance is $v") }
        assertEquals(d("10:32:10.000", 120), g.offer(d("10:32:10.000", 120), 0))
        assertNull(g.offer(d("10:32:11.000", 110), 1_000))
        assertNull(g.offer(d("10:32:12.000", 100), 2_000))
        assertEquals(d("10:32:20.000", 30) + "   [trước đó: +2 tương tự]", g.offer(d("10:32:20.000", 30), 10_000))
    }

    /** Số MỘT chữ số = trạng thái: đổi là ghi ngay, kể cả đổi VỀ giá trị cũ (lỗ A → B → A của bộ nguyên văn). */
    @Test
    fun `D doi trang thai ghi ngay, ke ca quay ve gia tri cu`() {
        val g = SessionLogGate()
        val s = { at: String, v: Int -> line('D', at, "BYDAutoSettingDevice", "getSeatVentilatingState: The state of 1 seat ventilating is $v") }
        assertEquals(s("07:51:19.000", 3), g.offer(s("07:51:19.000", 3), 0))
        assertNull(g.offer(s("07:51:20.000", 3), 1_000), "lặp y nguyên ⇒ gộp")
        assertEquals(s("07:51:21.000", 1) + "   [trước đó: +1 lặp]", g.offer(s("07:51:21.000", 1), 2_000))
        assertEquals(s("07:51:22.000", 3), g.offer(s("07:51:22.000", 3), 3_000), "3 → 1 → 3 trong 3 s: lần về 3 phải hiện")
        // Chữ đổi (false → true) cũng là trạng thái.
        val b = { at: String, v: Boolean -> line('D', at, "SomeHal", "door open=$v") }
        assertEquals(b("07:51:23.000", false), g.offer(b("07:51:23.000", false), 4_000))
        assertEquals(b("07:51:23.500", true), g.offer(b("07:51:23.500", true), 4_500))
    }

    /** Id ≥ 8 chữ số (kể cả hex toàn chữ số) là danh tính, không phải số đo: hai feature khác nhau không bị gộp. */
    @Test
    fun `D id dai khong bi coi la so do`() {
        val g = SessionLogGate()
        val a = line('D', "10:32:10.000", "AbsBYDAutoDevice", "set featureID is 43010018 intValue is 120")
        val b = line('D', "10:32:10.100", "AbsBYDAutoDevice", "set featureID is 43020018 intValue is 120")
        val hex = line('D', "10:32:10.200", "AbsBYDAutoDevice", "set featureID is 0x0950000F intValue is 0")
        assertEquals(a, g.offer(a, 0))
        assertEquals(b, g.offer(b, 100))
        assertEquals(hex, g.offer(hex, 200))
        assertEquals("set featureID is 43f01018 intValue is #", SessionLogGate.template("set featureID is 43f01018 intValue is 120"))
        assertEquals("speed is #", SessionLogGate.template("speed is 47.5"))
        assertEquals("speed is # and is 3 id 43010018", SessionLogGate.measure("speed is 47.5 and is 3 id 43010018"))
    }

    /**
     * Vòng poll nhiều danh tính (`getDoorState area is: 1..6` mỗi 3 s [ĐO 16/10]) ⇒ mỗi danh tính một dòng / 10 s, đúng
     * bằng luật 3.1.9 — nhưng hai thân luân phiên (A ↔ B) KHÔNG bị coi là vòng: có thể là trạng thái lật thật.
     */
    @Test
    fun `D vong poll nhieu danh tinh gop 10 s, hai than luan phien van ghi`() {
        val g = SessionLogGate()
        val door = { a: Int -> line('D', "10:37:42.772", "BYDAutoBodyworkDevice", "getDoorState area is: $a") }
        listOf(1, 2, 3, 4, 6).forEach { assertEquals(door(it), g.offer(door(it), 0), "lượt đầu: mọi cửa ghi") }
        listOf(1, 2, 3, 4, 6).forEach { assertNull(g.offer(door(it), 3_000), "lượt 3 s sau: vòng poll ⇒ gộp") }
        assertEquals(door(1) + "   [trước đó: +5 lặp]", g.offer(door(1), 10_000), "hết 10 s ⇒ ghi lại, kèm số đã gộp")
        val flip = { v: Int -> line('D', "10:37:50.000", "SomeHal", "mode is $v") }
        assertEquals(flip(1), g.offer(flip(1), 20_000))
        assertEquals(flip(2), g.offer(flip(2), 21_000))
        assertEquals(flip(1), g.offer(flip(1), 22_000), "A ↔ B: hai thân không phải vòng poll ⇒ vẫn ghi")
        assertEquals(flip(2), g.offer(flip(2), 23_000))
    }

    @Test
    fun `D lap y nguyen gop 60 s roi ghi lai kem so lap`() {
        val g = SessionLogGate()
        val l = { at: String -> line('D', at, "BYDAutoBodyworkDevice", "getDoorState state is: 0") }
        assertEquals(l("10:37:00.000"), g.offer(l("10:37:00.000"), 0))
        (1..59).forEach { assertNull(g.offer(l("10:37:00.000"), it * 1_000L)) }
        assertEquals(l("10:38:00.000") + "   [+59 lặp]", g.offer(l("10:38:00.000"), 60_000))
    }

    /**
     * `logcat -v time` in đủ đầu dòng trên MỌI dòng của bản ghi nhiều dòng [ĐO tệp usage 09-24]. Ngoại lệ thứ hai cùng
     * câu trong 10 s: đầu bản ghi bị giữ, rồi được THẢ RA khi dòng vết gọi tới — bản ghi không bao giờ bị cắt giữa.
     */
    @Test
    fun `vet goi nhieu dong khong bao gio bi cat giua`() {
        val g = SessionLogGate()
        val at1 = "09:06:40.376"
        val head1 = line('W', at1, "KachiVoiceGeo", "không tra cứu được \"w1\"", 16672)
        val exc1 = line('W', at1, "KachiVoiceGeo", "java.net.SocketTimeoutException: timeout", 16672)
        val fr1 = line('W', at1, "KachiVoiceGeo", "\tat libcore.io.IoBridge.connect(IoBridge.java:135)", 16672)
        assertEquals(head1, g.offer(head1, 0))
        assertEquals(exc1, g.offer(exc1, 0))
        assertEquals(fr1, g.offer(fr1, 0))
        val at2 = "09:06:45.000"
        val head2 = line('W', at2, "KachiVoiceGeo", "không tra cứu được \"w1\"", 16672)
        val exc2 = line('W', at2, "KachiVoiceGeo", "java.net.SocketTimeoutException: timeout", 16672)
        val fr2 = line('W', at2, "KachiVoiceGeo", "\tat libcore.io.IoBridge.connect(IoBridge.java:135)", 16672)
        assertNull(g.offer(head2, 4_624), "đầu bản ghi lặp y nguyên ⇒ tạm giữ")
        assertEquals(head2 + "\n" + exc2, g.offer(exc2, 4_624), "dòng tiếp cùng đầu dòng sau W ⇒ thả đầu bản ghi ra, đúng thứ tự")
        assertEquals(fr2, g.offer(fr2, 4_624))
        // Mức D/I: dòng `\tat`/`Caused by:` cùng đầu dòng cũng kéo dòng vừa giữ ra.
        val d1 = line('I', "09:07:00.000", "KachiVoiceGeo", "java.io.IOException: Service not Available")
        val d2 = line('I', "09:07:00.000", "KachiVoiceGeo", "\tat android.location.Geocoder.getFromLocationName(Geocoder.java:178)")
        assertEquals(d1, g.offer(d1, 20_000))
        val d1b = d1.replace("09:07:00.000", "09:07:03.000"); val d2b = d2.replace("09:07:00.000", "09:07:03.000")
        assertNull(g.offer(d1b, 23_000))
        assertEquals(d1b + "\n" + d2b, g.offer(d2b, 23_000))
        // Và số đếm được hoàn lại: lần ghi kế của câu ấy KHÔNG kể dòng đã thả ra là "đã bỏ".
        val d1c = d1.replace("09:07:00.000", "09:07:31.000")
        assertEquals(d1c, g.offer(d1c, 51_000))
    }

    /** Hai lệnh `Log.d` riêng trong cùng 1 ms (đầu dòng trùng) KHÔNG phải một bản ghi: vẫn gộp bình thường. */
    @Test
    fun `cung mili giay o muc D khong bi coi la ban ghi nhieu dong`() {
        val g = SessionLogGate()
        val a = line('D', "10:37:42.772", "BYDAutoBodyworkDevice", "getDoorState state is: 0")
        assertEquals(a, g.offer(a, 0))
        assertNull(g.offer(a, 1))
        assertNull(g.offer(a, 2))
    }

    @Test
    fun `dong lech khuon di thang, dong ho lui khong khoa dong mai`() {
        val g = SessionLogGate()
        assertEquals("", g.offer("", 0))
        assertEquals("--------- beginning of main", g.offer("--------- beginning of main", 0))
        assertEquals("--------- beginning of main", g.offer("--------- beginning of main", 1))
        val l = line('D', "10:00:00.000", "X", "v is 1")
        assertEquals(l, g.offer(l, 100_000))
        assertTrue(g.offer(l, 50_000) != null, "đồng hồ lùi ⇒ coi như hết cửa sổ")
    }

    @Test
    fun `bang khoa co tran`() {
        val g = SessionLogGate(maxKeys = 2)
        val a = line('I', "10:00:00.000", "X", "a"); val b = line('I', "10:00:00.000", "X", "b"); val c = line('I', "10:00:00.000", "X", "c")
        g.offer(a, 0); g.offer(b, 0); g.offer(c, 0)
        assertEquals(a, g.offer(a, 1), "khoá bị đẩy ra = 'chưa từng thấy' ⇒ GHI, không bao giờ thành dòng mất")
    }
}
