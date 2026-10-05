package com.byd.clusternav.launcher.trip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ A2 · 2.89 — chuyến chỉ chờ thứ nó CẦN, và Cài đặt gọi tên được thứ nó đã chờ ════════════════════════════════════════
 *
 * Owner 05/10 (xe 2.88, YouTube ở ô 1 + *Tự mở nhạc* = YouTube): Cài đặt *"hết hạn chờ – chuyến này không mở app"*. [ĐO mã 2.88]
 * `TripPlan.waitFor` chờ `SLOTS` (có ô app mà chưa ô nào đo thấy sống) cho MỌI cấu hình, tới hết hạn 180 s. Khoá:
 *  (3) cấu hình không có app *Chạy nền* ngoài ô ⇒ không chờ `SLOTS`; có thì chờ tối đa [TripPlan.SLOTS_GIVE_UP_MS];
 *  (4) sổ kết quả mang mốc chờ (`w=`) + ô của bước nhạc — bản ghi cũ vẫn đọc được.
 */
class TripWaitA2Test {

    private val yt = "com.google.android.youtube"

    @Test
    fun `cho o song - chi khi can o dan dung, va co tran tinh tu lan thuc`() {
        // Cấu hình CHỈ nhạc (ca xe 05/10): 4 ô app, chưa ô nào đo thấy sống ⇒ trước 2.89 = SLOTS tới hết hạn; nay qua thẳng.
        assertEquals(TripPlan.Wait.HOME_STEADY, TripPlan.waitFor(true, true, 4, 0, 0, needsStage = false))
        assertNull(TripPlan.waitFor(true, true, 4, 0, 3, needsStage = false))
        // Có app Chạy nền ngoài ô ⇒ vẫn chờ ô sống (đường đã đo, CLAUDE.md §6) …
        assertEquals(TripPlan.Wait.SLOTS, TripPlan.waitFor(true, true, 4, 0, 3, needsStage = true, sinceWakeMs = 30_000))
        // … nhưng không quá trần: sau đó các bước chạy nền đi đường cuối (màn ảo ẩn) thay vì cả chuyến EXPIRED.
        assertNull(TripPlan.waitFor(true, true, 4, 0, 3, needsStage = true, sinceWakeMs = TripPlan.SLOTS_GIVE_UP_MS))
        assertTrue(TripPlan.SLOTS_GIVE_UP_MS < TripGate.TRIP_DEADLINE_MS, "trần chờ ô phải chừa thời gian cho các bước")
        // Thứ tự cũ giữ nguyên.
        assertEquals(TripPlan.Wait.BOOT, TripPlan.waitFor(false, false, 4, 0, 0, needsStage = false))
        assertEquals(TripPlan.Wait.HOME_SCREEN, TripPlan.waitFor(true, false, 4, 0, 0, needsStage = false))
    }

    @Test
    fun `can o dan dung - co app Chay nen NGOAI o, khong tinh app Mo binh thuong hay app da o o`() {
        val music = TripMusic(TripMusicMode.YOUTUBE)
        assertFalse(TripPlan.needsStage(TripConfig(music = music), setOf(yt)), "chỉ nhạc ⇒ không")
        assertFalse(TripPlan.needsStage(TripConfig(listOf(TripApp("com.waze", background = false)), music), emptySet()), "Mở bình thường ⇒ không")
        assertFalse(TripPlan.needsStage(TripConfig(listOf(TripApp("com.waze", background = true))), setOf("com.waze")), "app nền đã ở ô ⇒ IN_SLOT")
        assertTrue(TripPlan.needsStage(TripConfig(listOf(TripApp("com.waze", background = true))), setOf(yt)))
        assertFalse(TripPlan.needsStage(TripConfig(listOf(TripApp("com.waze", background = true))), emptySet(), skip = setOf("com.waze")),
            "app hệ thống / chưa cài ⇒ bước 0 lệnh, không cần chỗ dàn dựng")
    }

    @Test
    fun `moc cho - khu hoi, SLOT_APP mang goi + o, chuoi la thanh null`() {
        TripPlan.Wait.values().filter { it != TripPlan.Wait.SLOT_APP }.forEach { w ->
            assertEquals(TripWaitMark(w), TripWaitMark.decode(TripWaitMark.encode(TripWaitMark(w))))
        }
        val m = TripWaitMark.slotApp(yt, 0)!!
        assertEquals("SLOT_APP:$yt:0", TripWaitMark.encode(m))
        assertEquals(m, TripWaitMark.decode("SLOT_APP:$yt:0"))
        listOf(null, "", "NOPE", "SLOT_APP", "SLOT_APP:$yt", "SLOT_APP:$yt:x", "SLOT_APP:$yt:99", "SLOT_APP:a;b:0", "HOME_STEADY:1")
            .forEach { assertNull(TripWaitMark.decode(it), "$it") }
        assertNull(TripWaitMark.slotApp("x y", 0)); assertNull(TripWaitMark.slotApp(yt, -1))
    }

    @Test
    fun `so ket qua - truong w= khu hoi, ban ghi cu khong co w= van doc duoc, khong dau ngan lac cho`() {
        val r = TripGate.Result("n39.b", TripGate.Code.EXPIRED, 1_700_000_000_000, "expired waiting HOME_STEADY", wait = TripWaitMark(TripPlan.Wait.HOME_STEADY))
        val enc = TripGate.encodeResult(r)
        assertTrue(enc.endsWith(";w=HOME_STEADY"), enc)
        assertEquals(r, TripGate.decodeResult(enc))
        val old = "v=1;trip=n39.b;code=EXPIRED;at=1;d=expired waiting SLOTS"
        assertNull(TripGate.decodeResult(old)!!.wait, "bản 2.88 không có w= ⇒ câu chung như trước")
        val slot = r.copy(wait = TripWaitMark.slotApp(yt, 2))
        assertEquals(slot, TripGate.decodeResult(TripGate.encodeResult(slot)))
        assertEquals(1, TripGate.encodeResult(slot).count { it == '=' } - TripGate.encodeResult(r.copy(wait = null)).count { it == '=' })
    }

    @Test
    fun `buoc nhac mang o - truong thu tu, ban ghi ba truong cu van doc, o la thi bo muc`() {
        val steps = listOf(
            TripStep(yt, TripStepKind.MUSIC, TripStepCode.PLAYING, slot = 0),
            TripStep("com.waze", TripStepKind.BACKGROUND, TripStepCode.PARKED),
            TripStep(yt, TripStepKind.MUSIC, TripStepCode.SLOT_WAIT, slot = 5),
        )
        val enc = TripOutcome.encode(steps)
        assertEquals("$yt:M:PLAYING:0,com.waze:B:PARKED,$yt:M:SLOT_WAIT:5", enc)
        assertEquals(steps, TripOutcome.decode(enc))
        assertEquals(listOf(TripStep(yt, TripStepKind.MUSIC, TripStepCode.SENT)), TripOutcome.decode("$yt:M:SENT"), "3 trường (≤ 2.88)")
        assertEquals(emptyList<TripStep>(), TripOutcome.decode("$yt:M:SENT:x,$yt:M:SENT:0:1,$yt:M:SENT:-1"), "trường ô lạ ⇒ bỏ mục")
    }

    @Test
    fun `ma moi - cho o qua tran la khong lam gi, do o 7 la dat`() {
        assertEquals(TripStepCode.Result.NOOP, TripStepCode.SLOT_WAIT.result)
        assertEquals(TripStepCode.Result.OK, TripStepCode.PARKED.result)
        assertEquals(TripGate.Code.NOOP, TripOutcome.tripCode(listOf(TripStep(yt, TripStepKind.MUSIC, TripStepCode.SLOT_WAIT, 0))))
        assertEquals(TripGate.Code.RAN, TripOutcome.tripCode(listOf(TripStep("com.waze", TripStepKind.BACKGROUND, TripStepCode.PARKED))))
    }
}
