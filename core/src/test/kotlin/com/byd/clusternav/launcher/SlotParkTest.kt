package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Ô 7 — ĐỖ ẨN (2.89-thử1 · spec `kachi-287-look-and-keys.html` §4.6d): phần THUẦN.
 *
 * Khoá ba điều mà bản thử dựa vào:
 *  1. [ParkLedger] — trần 3, đỗ app thứ 4 ⇒ nhả đúng bản CŨ NHẤT; đỗ lại cùng gói thay bản cũ (một app một chỗ đỗ); nhận lại
 *     là LẤY RA (một màn ảo một chủ).
 *  2. [SlotParkPlan.leave] — chỉ ĐỖ khi app cũ còn được dùng tiếp (app khác vào ô · chính nó sang ô khác); xoá ô / thành
 *     widget / cùng app dựng lại ⇒ NHẢ như hôm nay (CLAUDE.md §6). [SlotParkPlan.parkable] — lượt mở dở / app chết / toàn màn
 *     ⇒ không đỗ (đỗ màn ảo trống = khung đen vĩnh viễn khi nhận lại).
 *  3. [SlotParkPlan.claim] — 2.91 · F2: nhận lại vào ô khác cỡ ⇒ ĐỔI CỠ màn ảo theo ô (cùng display), không còn khung viền đen
 *     ([ĐO máy ảo QA 05/10] 1129×610 trong ô 1129×804).
 */
class SlotParkTest {

    private fun app(p: String) = SlotContent.App(p)
    private val widget = SlotContent.Widget(listOf("w_clock"))

    // ══ (1) sổ ô 7 ═══════════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `tran 3 - do app thu 4 nha dung ban cu nhat, theo thu tu do`() {
        val l = ParkLedger<String>()
        assertTrue(l.park("a", "vdA").isEmpty())
        assertTrue(l.park("b", "vdB").isEmpty())
        assertTrue(l.park("c", "vdC").isEmpty())
        assertEquals(listOf(ParkLedger.Evicted("a", "vdA", ParkLedger.Why.CAP)), l.park("d", "vdD"))
        assertEquals(listOf("b", "c", "d"), l.pkgs())
        assertEquals(listOf(ParkLedger.Evicted("b", "vdB", ParkLedger.Why.CAP)), l.park("e", "vdE"))
        assertEquals(listOf("c", "d", "e"), l.pkgs())
        assertEquals(3, ParkLedger.CAP, "trần bản thử = 3 (owner chốt ở §4.6d OQ)")
    }

    @Test
    fun `do lai cung goi - ban cu bi nha, ban moi xuong cuoi, khong tinh vao tran`() {
        val l = ParkLedger<String>()
        l.park("a", "vdA1"); l.park("b", "vdB"); l.park("c", "vdC")
        assertEquals(listOf(ParkLedger.Evicted("a", "vdA1", ParkLedger.Why.SAME_PKG)), l.park("a", "vdA2"))
        assertEquals(listOf("b", "c", "a"), l.pkgs(), "đỗ lại = mới nhất ⇒ bị nhả SAU CÙNG")
        assertEquals("vdA2", l.take("a"))
    }

    @Test
    fun `nhan lai la LAY RA - lan hai null, cho trong khong bi nha nham`() {
        val l = ParkLedger<String>()
        l.park("a", "vdA"); l.park("b", "vdB")
        assertTrue(l.has("a"))
        assertEquals("vdA", l.take("a"))
        assertNull(l.take("a"), "một màn ảo một chủ — lấy ra rồi thì không còn")
        assertFalse(l.has("a"))
        l.park("c", "vdC"); l.park("d", "vdD")
        assertEquals(listOf("b", "c", "d"), l.pkgs(), "chỗ trống do lấy ra ⇒ đỗ thêm KHÔNG nhả ai")
        assertNull(l.take("x"))
    }

    /**
     * Soát 2.97 R5 Pass 1 [P1] — ĐỔI GHIM có lý do: bản được che chắn là bản TẠM (nhận lại ngay sau lượt) ⇒ KHÔNG tính vào trần.
     * Trước: `entries.size > cap` tính cả bản che chắn ⇒ ở đây `maps` bị nhả dù sau khi youtube nhận lại sổ chỉ còn 3 = trần.
     */
    @Test
    fun `che chan - app sap nhan lai cung luot khong bi tran nha, va khong tinh vao tran`() {
        // Đỗ [youtube, maps, spotify] (youtube CŨ NHẤT); đặt youtube vào ô đang có zalo ⇒ zalo đỗ TRƯỚC, youtube nhận lại SAU.
        val l = ParkLedger<String>()
        l.park("youtube", "1"); l.park("maps", "2"); l.park("spotify", "3")
        val out = l.park("zalo", "4", protect = SlotParkPlan.shown(listOf(app("youtube"), widget, SlotContent.Empty)))
        assertTrue(out.isEmpty(), "ngoài bản che chắn sổ chỉ có 3 = trần ⇒ không nhả ai: $out")
        assertEquals("1", l.take("youtube"), "app người dùng vừa gọi vẫn nhận lại được — không relaunch, nhạc không mất")
        assertEquals(listOf("maps", "spotify", "zalo"), l.pkgs())
        // Che chắn một bản mà sổ ngoài nó VẪN vượt trần ⇒ nhả cũ nhất KHÔNG được che chắn (che chắn không phải miễn trần cho người khác).
        val m = ParkLedger<String>()
        m.park("youtube", "1"); m.park("maps", "2"); m.park("spotify", "3")
        m.park("zalo", "4", protect = setOf("youtube"))
        assertEquals(listOf(ParkLedger.Evicted("maps", "2", ParkLedger.Why.CAP)), m.park("tiktok", "5", protect = setOf("youtube")))
        assertEquals(listOf("youtube", "spotify", "zalo", "tiktok"), m.pkgs(), "youtube tạm, 3 bản thật = trần")
    }

    /**
     * 2.97 · R5 (c) — dựng lại TẤT CẢ ô: mọi app còn hiện đỗ CÙNG LÚC (đều che chắn) rồi ô mới nhận lại từng app. Ô 7 đang đầy
     * (3 app thật) KHÔNG được mất ai chỉ vì sổ tạm vượt trần. Thử ĐỎ: trả `while (entries.size > cap …)` ⇒ a, b bị nhả.
     */
    @Test
    fun `R5 - dung lai ca voi o 7 day - app that o o 7 khong bi nha vi so tam`() {
        val l = ParkLedger<String>()
        l.park("a", "1"); l.park("b", "2"); l.park("c", "3")
        val next = listOf(app("yt"), app("maps"), widget, SlotContent.Empty)
        val protect = SlotParkPlan.shown(next)
        assertTrue(l.park("yt", "4", protect).isEmpty())
        assertTrue(l.park("maps", "5", protect).isEmpty())
        assertEquals(listOf("a", "b", "c", "yt", "maps"), l.pkgs(), "vượt trần TẠM")
        assertEquals("4", l.take("yt")); assertEquals("5", l.take("maps"))
        assertEquals(listOf("a", "b", "c"), l.pkgs(), "ô 7 y nguyên sau lượt dựng lại")
        // Lượt đỗ thật kế tiếp (không che chắn) đưa về trần như cũ.
        assertEquals(listOf("a"), l.park("d", "6").map { it.pkg })
    }

    @Test
    fun `che chan het - vuot tran TAM, luot do ke tiep dua ve tran`() {
        val l = ParkLedger<String>(cap = 1)
        l.park("a", "1")
        assertTrue(l.park("b", "2", protect = setOf("a")).isEmpty())
        assertEquals(listOf("a", "b"), l.pkgs())
        assertEquals(listOf("a", "b"), l.park("c", "3").map { it.pkg })
        assertEquals(listOf("c"), l.pkgs())
    }

    @Test
    fun `goi hien trong bo cuc moi - chi o App`() {
        assertEquals(setOf("a", "b"), SlotParkPlan.shown(listOf(app("a"), widget, SlotContent.Empty, app("b"))))
        assertTrue(SlotParkPlan.shown(emptyList()).isEmpty())
    }

    @Test
    fun `tran tuy chinh - ban vua do khong bao gio bi nha`() {
        val one = ParkLedger<String>(cap = 1)
        assertTrue(one.park("a", "1").isEmpty())
        assertEquals(listOf("a"), one.park("b", "2").map { it.pkg })
        assertEquals(listOf("b"), one.pkgs())
        assertThrows<IllegalArgumentException> { ParkLedger<String>(cap = 0) }
    }

    // ══ (2) đỗ hay nhả ════════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `app khac vao o - do app cu (dat tam, loi tat, giong noi, ngan keo)`() {
        val next = listOf(app("yt.music"), SlotContent.Empty)
        assertEquals(SlotParkPlan.Leave.PARK, SlotParkPlan.leave(app("youtube"), app("yt.music"), next, 0))
    }

    @Test
    fun `chinh app sang o khac (keo-tha, mot-app-mot-o) - do de o moi nhan lai`() {
        // ô 0: YouTube → trống (YouTube sang ô 1) — đỗ để ô 1 nhận lại ĐÚNG màn ảo, không force-stop rồi mở lại.
        val next = listOf(SlotContent.Empty, app("youtube"))
        assertEquals(SlotParkPlan.Leave.PARK, SlotParkPlan.leave(app("youtube"), SlotContent.Empty, next, 0))
        // kéo-thả đổi chỗ hai app: cả hai ô đều đỗ.
        val swapped = listOf(app("maps"), app("youtube"))
        assertEquals(SlotParkPlan.Leave.PARK, SlotParkPlan.leave(app("youtube"), app("maps"), swapped, 0))
        assertEquals(SlotParkPlan.Leave.PARK, SlotParkPlan.leave(app("maps"), app("youtube"), swapped, 1))
    }

    @Test
    fun `xoa o, thanh widget, cung app dung lai, o khong phai app - nha nhu hom nay`() {
        val cleared = listOf(SlotContent.Empty, app("maps"))
        assertEquals(SlotParkPlan.Leave.RELEASE, SlotParkPlan.leave(app("youtube"), SlotContent.Empty, cleared, 0))
        assertEquals(SlotParkPlan.Leave.RELEASE, SlotParkPlan.leave(app("youtube"), widget, listOf(widget), 0))
        assertEquals(SlotParkPlan.Leave.RELEASE, SlotParkPlan.leave(app("youtube"), app("youtube"), listOf(app("youtube")), 0),
            "cùng app dựng lại (kênh vừa đổi) — đường hôm nay")
        assertEquals(SlotParkPlan.Leave.RELEASE, SlotParkPlan.leave(widget, app("youtube"), listOf(app("youtube")), 0))
        assertEquals(SlotParkPlan.Leave.RELEASE, SlotParkPlan.leave(SlotContent.Empty, app("youtube"), listOf(app("youtube")), 0))
        // cùng app ở CHÍNH ô này trong bố cục mới không tính là "sang ô khác".
        assertEquals(SlotParkPlan.Leave.RELEASE, SlotParkPlan.leave(app("youtube"), SlotContent.Empty, listOf(SlotContent.Empty), 0))
    }

    @Test
    fun `host do duoc - chi khi luot mo da xong va app con o man ao`() {
        fun p(
            released: Boolean = false, launched: Boolean = true, hasVd: Boolean = true, pkg: String? = "youtube",
            dead: Boolean = false, detached: Boolean = false, watching: Boolean = true,
        ) = SlotParkPlan.parkable(released, launched, hasVd, pkg, dead, detached, watching)
        assertTrue(p())
        assertFalse(p(released = true), "host đã nhả")
        assertFalse(p(launched = false), "chưa từng ra lệnh mở")
        assertFalse(p(hasVd = false), "chưa có màn ảo")
        assertFalse(p(pkg = null), "host đã thả app (luật hoàn ô)")
        assertFalse(p(pkg = ""))
        assertFalse(p(dead = true), "nhịp đo đã báo app rời màn ảo")
        assertFalse(p(detached = true), "app đang toàn màn ở display 0 — task không còn trên màn ảo")
        assertFalse(p(watching = false), "lượt mở còn dở — đỗ màn ảo trống ⇒ khung đen khi nhận lại")
    }

    // ══ (3b) nhận lại một màn ảo đỗ mà app đã RỜI nó lúc đang đỗ ═══════════════════════════════════════════════════

    @Test
    fun `nhan lai - app con tren man ao thi do nhu thuong, chet sau do la cai chet thuong`() {
        val l = SlotLiveness(adopted = true)
        assertFalse(l.observe(alive = true))
        assertFalse(l.observe(alive = false), "một nhịp hụt đơn lẻ không phải cái chết")
        assertTrue(l.observe(alive = false))
        assertFalse(l.missing, "đã thấy sống ⇒ đây là app CHẾT (luật hoàn ô), không phải màn ảo trống")
    }

    @Test
    fun `nhan lai - chua tung thay app, MOT nhip doc duoc vang app la man ao TRONG (PARK-2b, mo nhu duong thuong)`() {
        // PARK-2b: nhịp hụt chỉ được nạp khi bản `am stack list` ĐỌC ĐƯỢC (`SlotLiveProbe`: đọc hỏng ⇒ bỏ qua với ô nhận lại),
        // và màn ảo đã lấy ra không có lượt mở nào đang dở ⇒ một lần vắng là kết luận. Hai nhịp × nhịp đang lùi = 10–20 s ô đen.
        assertEquals(1, SlotLiveness.ADOPTED_MISSES)
        val l = SlotLiveness(adopted = true)
        assertTrue(l.observe(alive = false))
        assertTrue(l.missing)
        repeat(5) { assertFalse(l.observe(alive = false), "báo đúng MỘT lần") }
    }

    @Test
    fun `nhan lai - nguong mot nhip chi cho ket luan TRONG, khong ha nguong chet cua o thuong`() {
        val adopted = SlotLiveness(adopted = true, missesToDie = 3)
        assertFalse(adopted.observe(alive = true))
        assertFalse(adopted.observe(alive = false)); assertFalse(adopted.observe(alive = false))
        assertTrue(adopted.observe(alive = false), "đã thấy sống ⇒ chết cần đủ missesToDie nhịp như mọi ô")
        assertFalse(adopted.missing)
        val plain = SlotLiveness()
        assertFalse(plain.observe(alive = true))
        assertFalse(plain.observe(alive = false), "ô thường: một nhịp hụt vẫn không phải cái chết")
        assertTrue(plain.observe(alive = false))
    }

    @Test
    fun `peek - xem ban do KHONG lay ra (vdOf chi doc, nhan lai vao o moi la take)`() {
        val l = ParkLedger<String>()
        l.park("youtube", "vd")
        assertEquals("vd", l.peek("youtube"))
        assertEquals("vd", l.peek("youtube"), "xem bao nhiêu lần cũng không gỡ")
        assertTrue(l.has("youtube"))
        assertEquals("vd", l.take("youtube"))
        assertNull(l.peek("youtube"))
        assertNull(l.peek("x"))
    }

    @Test
    fun `khong phai nhan lai - luat 1 giu nguyen (chua thay song thi khong ket luan)`() {
        val l = SlotLiveness()
        repeat(20) { assertFalse(l.observe(alive = false)) }
        assertFalse(l.missing)
    }

    // ══ (4) hai hàm chuyển nguyên thân từ `VdAppHost` sang `FreeformLaunch` (trần 500 dòng) — hành vi giữ nguyên ═══════

    @Test
    fun `resolveComponent - cung chuoi lenh, dong CUOI co dau gach va ten goi, kenh nem thi null`() {
        val sent = ArrayList<String>()
        val out = "priority=0 preferredOrder=0\ncom.google.android.youtube/com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity\n"
        val comp = FreeformLaunch.resolveComponent("com.google.android.youtube") { sent += it; out }
        assertEquals("com.google.android.youtube/com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity", comp)
        assertEquals(listOf(FreeformLaunch.resolveCmd("com.google.android.youtube")), sent)
        assertNull(FreeformLaunch.resolveComponent("x.y") { "No activity found" })
        assertNull(FreeformLaunch.resolveComponent("x.y") { throw java.io.IOException("dadb") })
    }

    @Test
    fun `appRunning - pidof rong la chua len, kenh nem la chua len`() {
        assertTrue(FreeformLaunch.appRunning("a.b") { cmd -> if (cmd == "pidof a.b") "4242\n" else "" })
        assertFalse(FreeformLaunch.appRunning("a.b") { "  \n" })
        assertFalse(FreeformLaunch.appRunning("a.b") { throw java.io.IOException("dadb") })
    }

    // ── 2.97 · R5 (thay whole-r2-2 của 2.89) — đổi hồ sơ: app hồ sơ mới vẫn hiện ⇒ ĐỖ, không thì nhả ───────────────────────────

    /**
     * Khoá lỗi [ĐO log SL6 08/10 15:16:37]: hai hồ sơ cùng có YouTube ở ô 0, đổi hồ sơ ⇒ nhả + force-stop + mở lại ⇒ YouTube đang hát về
     * trang chủ. Nay: app hồ sơ mới vẫn hiện (cùng ô hoặc ô khác) ⇒ ĐỖ để ô mới nhận lại; không hiện ⇒ nhả (không còn ai nằm ẩn ở ô 7
     * mà không có ô quay về). Đặt tạm / lối tắt / ⇄ (không cờ) giữ nguyên luật cũ. Thử ĐỎ: trả lại `if (profileSwitch) return RELEASE`.
     */
    @Test
    fun `R5 - doi ho so - app con hien thi do, khong hien thi nha`() {
        val yt = "com.google.android.youtube"
        assertEquals(SlotParkPlan.Leave.PARK,
            SlotParkPlan.leave(app(yt), app(yt), listOf(app(yt), app("b.c")), 0, profileSwitch = true), "cùng ô, cùng app")
        assertEquals(SlotParkPlan.Leave.PARK,
            SlotParkPlan.leave(app(yt), app("vn.vietmap.live"), listOf(app("vn.vietmap.live"), app(yt)), 0, profileSwitch = true), "sang ô khác")
        assertEquals(SlotParkPlan.Leave.RELEASE,
            SlotParkPlan.leave(app("com.google.android.apps.youtube.music"), app("vn.vietmap.live"), listOf(app("vn.vietmap.live"), app("b.c")), 0,
                profileSwitch = true), "hồ sơ mới không có ⇒ nhả")
        assertEquals(SlotParkPlan.Leave.RELEASE,
            SlotParkPlan.leave(SlotContent.Empty, app(yt), listOf(app(yt)), 0, profileSwitch = true), "ô cũ không có app")
        assertEquals(SlotParkPlan.Leave.PARK,
            SlotParkPlan.leave(app("com.google.android.apps.youtube.music"), app("vn.vietmap.live"), listOf(app("vn.vietmap.live"), app("b.c")), 0),
            "đặt tạm / ⇄: như cũ")
    }

    /** R5 (c) — dựng lại TẤT CẢ ô (hồ sơ khác bố cục): app còn trong bố cục mới ⇒ đỗ; không còn / không gói ⇒ nhả. */
    @Test
    fun `R5 - dung lai ca - giu app con trong bo cuc moi`() {
        val next = listOf(SlotContent.Empty, app("com.google.android.youtube"))
        assertEquals(SlotParkPlan.Leave.PARK, SlotParkPlan.keepOnRebuild("com.google.android.youtube", next))
        assertEquals(SlotParkPlan.Leave.RELEASE, SlotParkPlan.keepOnRebuild("vn.vietmap.live", next))
        assertEquals(SlotParkPlan.Leave.RELEASE, SlotParkPlan.keepOnRebuild(null, next))
    }

    // ── 2.91 · F2 — bảng quyết lượt NHẬN LẠI (thay PARK-1 ghim cỡ + khung viền) ─────────────────────────────────────────────

    /**
     * Khoá lỗi [ĐO máy ảo QA 05/10]: app đỗ ở ô 1129×610 mở lại vào ô 1129×804 hiện 1129×610 giữa viền đen, góc trong vuông. Nay:
     * có bản đỗ ⇒ LUÔN gắn (không bao giờ chờ / ghim), khác cỡ ⇒ đổi cỡ màn ảo theo ô; không bản đỗ ⇒ đường thường.
     * Thử ĐỎ: trả `ATTACH` cho ca khác cỡ (màn ảo giữ cỡ cũ ⇒ lại viền), hoặc `GOLDEN` khi có bản đỗ (tạo màn ảo mới ⇒ relaunch).
     */
    @Test
    fun `F2 - bang quyet nhan lai - khac co thi doi co man ao theo o, khong vien`() {
        fun c(pw: Int?, ph: Int?, w: Int, h: Int) = SlotParkPlan.claim(pw, ph, w, h)
        assertEquals(SlotParkPlan.ClaimStep.ATTACH_RESIZE, c(1129, 610, 1129, 804), "ca QA 05/10: cao 610 → 804")
        assertEquals(SlotParkPlan.ClaimStep.ATTACH_RESIZE, c(1872, 956, 936, 956), "ô hẹp hơn")
        assertEquals(SlotParkPlan.ClaimStep.ATTACH_RESIZE, c(1920, 720, 1129, 804), "màn ảo ẩn của chuyến (cỡ display 0) vào ô")
        assertEquals(SlotParkPlan.ClaimStep.ATTACH, c(1129, 804, 1129, 804), "cùng cỡ ⇒ chỉ gắn, không một lượt đổi cấu hình nào")
        assertEquals(SlotParkPlan.ClaimStep.ATTACH, c(1129, 804, 0, 804), "cỡ ô hỏng ⇒ gắn, không đổi cỡ về 0")
        assertEquals(SlotParkPlan.ClaimStep.GOLDEN, c(null, null, 1129, 804), "không đỗ ⇒ đường thường")
        assertEquals(SlotParkPlan.ClaimStep.GOLDEN, c(1129, null, 1129, 804))
        assertEquals(3, SlotParkPlan.ClaimStep.values().size, "không còn bước chờ / ghim / bỏ khung")
    }

    /**
     * 2.91 · F2b — khoá quyết định điều phối 06/10 (nhạc của app đỗ chạy tiếp): nhận lại vào ô khác cỡ chỉ đổi CỠ, GIỮ mật độ. Ca QA
     * 05/10: ô 1129×610 (nền 200 dpi ⇒ 162 dpi) mở lại vào ô 1129×804 (⇒ 200 dpi) — không giữ thì lượt nhận lại đổi 162 → 200, và
     * [ĐO nguồn r47 `ActivityRecord.java:3377`] đổi mật độ luôn dựng lại activity không khai `density`. Màn ảo MỞ MỚI: theo ô như cũ.
     * Thử ĐỎ: trả `null` cho `keep = true` (lại đổi mật độ khi nhận lại), hoặc trả [currentDpi] khi `keep = false` (đường golden đổi).
     */
    @Test
    fun `F2b - nhan lai giu mat do man ao do, man ao mo moi theo o`() {
        assertEquals(162, SlotDensity.forTablet(610, 200), "tiền đề ca QA: ô cạnh ngắn 610 ⇒ 162 dpi")
        assertEquals(200, SlotDensity.forTablet(804, 200), "ô cạnh ngắn 804 ⇒ 200 dpi — khác 162")
        assertEquals(162, SlotParkPlan.resizeDensity(keep = true, currentDpi = 162), "nhận lại: GIỮ 162, không thành 200")
        assertEquals(240, SlotParkPlan.resizeDensity(keep = true, currentDpi = 240), "màn ảo ẩn của chuyến (mật độ display 0): giữ")
        assertNull(SlotParkPlan.resizeDensity(keep = false, currentDpi = 162), "không phải nhận lại ⇒ đường thường: mật độ theo ô")
        assertNull(SlotParkPlan.resizeDensity(keep = true, currentDpi = 0), "không biết mật độ ⇒ đường thường, không bao giờ đặt 0")
        assertNull(SlotParkPlan.resizeDensity(keep = true, currentDpi = -1))
    }
}
