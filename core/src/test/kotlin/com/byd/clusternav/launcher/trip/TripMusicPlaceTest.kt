package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.behind.BehindHomePlan
import com.byd.clusternav.launcher.behind.BehindHomeSequence
import com.byd.clusternav.modules.clustercast.StackParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ A2 · TRIP-MUSIC-IN-SLOT — app nhạc Ở ĐÂU thì giao lệnh phát Ở ĐÓ ([TripMusicPlace]) ═══════════════════════════════════
 *
 * Owner 05/10 (xe 2.88): *"youtube nằm ở khung 1 nên nó tự mở chứ có cần phải setting mở app khi nổ máy đâu?"* — Cài đặt ghi
 * *"hết hạn chờ – chuyến này không mở app"*. Khoá: app nhạc ở ô ⇒ chờ CHÍNH ô đó sống (nhịp đo ô, hoặc đọc thẳng
 * `am stack list` — fixture `tm2-in-slot` NGUYÊN VĂN máy ảo 02/10: VietMap trên màn ảo ô 173), có trần, không quá hạn chuyến;
 * app ngoài ô ⇒ ô 7 trước, đường cũ chỉ khi ô 7 không chạy được.
 */
class TripMusicPlaceTest {

    private fun text(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/am-stack-list-emulator-$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture $name")

    /** Quyết định là GENERIC theo gói (CLAUDE.md §7) — dùng đúng app có trong fixture nguyên văn. */
    private val app = "vn.vietmap.live"
    private val inSlot by lazy { StackParse.parse(text("2026-10-02-tm2-in-slot")) }
    private fun stage(alive: Boolean, vd: Int = 173, pkg: String = app, slot: Int = 0) = BehindHomePlan.Stage(slot, vd, pkg, 1L, alive)

    @Test
    fun `cho cua app - o tu bang o cua bo cuc dang hien, host cung o va cung goi`() {
        val st = stage(alive = true, slot = 1)
        assertEquals(TripMusicPlace.Where(1, st), TripMusicPlace.where(app, mapOf(app to 1), listOf(stage(true, 200, "x.y", 0), st)))
        assertEquals(TripMusicPlace.Where(1, null), TripMusicPlace.where(app, mapOf(app to 1), listOf(stage(true, slot = 0))), "host ô KHÁC không tính")
        assertEquals(TripMusicPlace.Where(null, null), TripMusicPlace.where(app, emptyMap(), listOf(st)), "không ở ô nào")
    }

    @Test
    fun `mot nhip cho o - roi bo cuc, host chua mo, nhip do da thay song, hoac doc thang thay task tren DUNG man ao`() {
        val p = TripMusicPlace
        assertEquals(TripMusicPlace.Slot.LEFT, p.slot(app, TripMusicPlace.Where(null, null), inSlot))
        assertEquals(TripMusicPlace.Slot.WAIT, p.slot(app, TripMusicPlace.Where(0, null), inSlot), "host chưa có màn ảo ⇒ chờ")
        assertEquals(TripMusicPlace.Slot.ALIVE, p.slot(app, TripMusicPlace.Where(0, stage(alive = true)), null), "nhịp đo ô đã thấy sống")
        assertEquals(TripMusicPlace.Slot.ALIVE, p.slot(app, TripMusicPlace.Where(0, stage(alive = false)), inSlot), "đọc thẳng: task 2613 trên màn ảo 173")
        assertEquals(TripMusicPlace.Slot.WAIT, p.slot(app, TripMusicPlace.Where(0, stage(alive = false, vd = 174)), inSlot), "task ở màn ảo KHÁC không tính")
        assertEquals(TripMusicPlace.Slot.WAIT, p.slot(app, TripMusicPlace.Where(0, stage(alive = false)), null), "đọc hỏng ≠ sống")
        assertEquals(TripMusicPlace.Slot.WAIT, p.slot(app, TripMusicPlace.Where(0, stage(alive = true, pkg = "x.y")), inSlot), "host đang mở app khác")
    }

    private class Clock { var t = 1_000L }

    @Test
    fun `cho o - song thi tra o + man ao, doc thang chi moi DIRECT_READ_EVERY nhip, khong qua moc dung`() {
        val c = Clock()
        var reads = 0
        var direct = 0
        val w = TripMusicPlace.await(
            app, 0, until = c.t + 60_000, now = { c.t }, sleep = { c.t += it },
            read = { reads++; TripMusicPlace.Where(0, if (reads < 2) null else stage(alive = false)) },
            stacks = { direct++; if (direct < 2) emptyList() else inSlot },
        )
        assertTrue(w is TripMusicPlace.Waited.Alive, w.toString())
        w as TripMusicPlace.Waited.Alive
        assertEquals(0, w.slot); assertEquals(173, w.vd)
        assertEquals(2, direct, "đọc thẳng ở nhịp 3 và 6 — không mỗi giây")
        assertEquals(5 * TripMusicPlace.SLOT_POLL_MS, w.ms)
    }

    @Test
    fun `cho o - het tran thi Timeout dung o, roi bo cuc thi Left, o doi thi bao`() {
        val c = Clock()
        val t = TripMusicPlace.await(app, 2, TripMusicPlace.until(c.t, c.t + 7_000), { c.t }, { c.t += it }, { TripMusicPlace.Where(2, null) }, { null })
        assertEquals(TripMusicPlace.Waited.Timeout(2, 7_000), t, "hạn chuyến (7 s) gần hơn trần ô (90 s) ⇒ dừng ở hạn chuyến")
        val c2 = Clock()
        var n = 0
        val left = TripMusicPlace.await(app, 0, c2.t + 60_000, { c2.t }, { c2.t += it }, { n++; TripMusicPlace.Where(if (n < 3) 0 else null, null) }, { null })
        assertEquals(TripMusicPlace.Waited.Left(2_000), left)
        val seen = ArrayList<Int>()
        val moved = TripMusicPlace.await(app, 0, c2.t + 60_000, { c2.t }, { c2.t += it }, { TripMusicPlace.Where(3, stage(true, slot = 3)) }, { null }, seen::add)
        assertEquals(listOf(3), seen, "người lái kéo app sang ô 4 ⇒ Cài đặt nói ô mới")
        assertEquals(3, (moved as TripMusicPlace.Waited.Alive).slot)
    }

    @Test
    fun `cho o - man chinh khong tra loi thi cho tiep, khong ket luan roi bo cuc`() {
        val c = Clock()
        var n = 0
        val w = TripMusicPlace.await(app, 1, c.t + 60_000, { c.t }, { c.t += it }, { n++; if (n < 4) null else TripMusicPlace.Where(1, stage(true, slot = 1)) }, { null })
        assertTrue(w is TripMusicPlace.Waited.Alive, w.toString())
    }

    @Test
    fun `moc dung - tran 90 s, khong qua han chuyen`() {
        assertEquals(1_000L + TripMusicPlace.SLOT_WAIT_MS, TripMusicPlace.until(1_000L, 1_000_000L))
        assertEquals(5_000L, TripMusicPlace.until(1_000L, 5_000L))
    }

    @Test
    fun `ngoai o - o 7 truoc, duong cu CHI khi o 7 khong chay duoc`() {
        val yes = setOf(BehindHomeSequence.Result.NO_STAGE, BehindHomeSequence.Result.X_NOT_STAGED)
        BehindHomeSequence.Result.entries.forEach { r ->
            assertEquals(r in yes, TripMusicPlace.fallBack(r), "$r")
        }
        assertFalse(TripMusicPlace.fallBack(BehindHomeSequence.Result.UNREAD), "đọc hỏng ⇒ không thêm lệnh trên một bản đọc không có")
        assertFalse(TripMusicPlace.fallBack(BehindHomeSequence.Result.PARKED))
    }

    @Test
    fun `VIEW - app o o 7 di CHINH man ao do, app o o van uu tien o, ngoai ca hai thi cho dan dung`() {
        assertEquals(TripMusicPlan.ViewRoute.Parked(301), TripMusicPlan.viewRoute(false, null, 301))
        assertEquals(TripMusicPlan.ViewRoute.Slot(173), TripMusicPlan.viewRoute(true, 173, 301), "ở ô ⇒ ô thắng")
        assertEquals(TripMusicPlan.ViewRoute.SlotNotReady, TripMusicPlan.viewRoute(true, null, 301), "ở ô mà ô chưa sẵn ⇒ 0 lệnh, không sang ô 7")
        assertEquals(TripMusicPlan.ViewRoute.Stage, TripMusicPlan.viewRoute(false, null, null))
        assertEquals(TripMusicPlan.ViewRoute.Stage, TripMusicPlan.viewRoute(false, null, 0), "màn ảo không hợp lệ ⇒ không nhắm")
        assertNull((TripMusicPlan.viewRoute(false, 173, null) as? TripMusicPlan.ViewRoute.Slot), "không ở ô ⇒ không bao giờ Slot")
    }

    /**
     * Review 2.89 Pass 1 · behaviour-1 — màn chính không trả lời trong hạn (`where()` = `null`) KHÔNG phải "ngoài ô": YouTube ở
     * ô 1 mà luồng chính bận > 2 s lúc HOME_STEADY ⇒ bản cũ đi ô 7 + BEHIND-HOME, giành đúng app đang mở trong ô (A2-R1 cấm).
     */
    @Test
    fun `entrySlot - null tu man chinh dung o cua anh chup, chi Where(slot=null) that moi la ngoai o`() {
        assertEquals(0, TripMusicPlace.entrySlot(null, 0), "màn chưa trả lời ⇒ ô của ảnh chụp ⇒ đường CHỜ ô, không ô 7")
        assertNull(TripMusicPlace.entrySlot(TripMusicPlace.Where(null, null), 0), "đọc MỚI nói không ở ô ⇒ ngoài ô")
        assertEquals(2, TripMusicPlace.entrySlot(TripMusicPlace.Where(2, null), 0), "đọc MỚI thắng ảnh chụp")
        assertNull(TripMusicPlace.entrySlot(null, null), "không ai biết ⇒ như cũ (ngoài ô)")
    }

    @Test
    fun `entrySlot null roi await - doc lai van null thi CHO (khong ra Left), timeout o dung o`() {
        var t = 0L
        val slot0 = TripMusicPlace.entrySlot(null, 0)!!
        val w = TripMusicPlace.await("com.google.android.youtube", slot0, until = 3_000L, now = { t }, sleep = { t += it },
            read = { null }, stacks = { error("không đọc thẳng khi chưa có màn ảo") })
        assertEquals(TripMusicPlace.Waited.Timeout(0, 3_000L), w)
    }
}
