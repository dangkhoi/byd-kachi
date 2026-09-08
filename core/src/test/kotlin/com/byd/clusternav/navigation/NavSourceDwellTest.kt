package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * B-II — khoá hành vi chống nhảy nguồn của [NavSourceDwell].
 *
 * Mỗi test nói rõ nó KHOÁ CÁI GÌ (CLAUDE.md §10). Đồng hồ tiêm được nên chuỗi "N nhịp LIÊN TIẾP" được kiểm
 * theo THỜI GIAN thật, không phải theo số lần gọi.
 */
class NavSourceDwellTest {

    private val WAZE = "com.waze"
    private val MOD = "com.chisadin.wazemod"
    private val GMAPS = "com.google.android.apps.maps"
    private val VIETMAP = NavApps.VIETMAP_LIVE
    private val TICK = 800L

    /** Đồng hồ giả: nhích tay từng nhịp. */
    private class Clock(var t: Long = 10_000L) : () -> Long {
        override fun invoke(): Long = t
        fun advance(ms: Long) { t += ms }
    }

    private fun dwell(
        clock: Clock,
        dwellTicks: Int = NavSourceDwell.DWELL_TICKS,
        holderGraceMs: Long = SourceArbiter.STALE_MS,
    ) = NavSourceDwell(
        clock = clock,
        tickPeriodMs = TICK,
        dwellTicks = dwellTicks,
        holderGraceMs = holderGraceMs,
    )

    // ── DWELL ────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * KHOÁ: R1/BOOTSTRAP. Nếu bắt dwell ngay lần đầu thì cụm TRẮNG 2,4 s mở đầu MỖI chuyến — người lái vừa bấm
     * "bắt đầu" mà cụm không có gì là lỗi nặng hơn nhiều so với cú nhảy nguồn đang chữa.
     */
    @Test
    fun `nguon dau tien len NGAY (chua co holder thi khong dwell)`() {
        val c = Clock(); val d = dwell(c)
        val r = d.onTick(candidate = WAZE, holderAllowed = false)
        assertEquals(WAZE, r.source)
        assertTrue(r.switched)
        assertEquals(NavSourceDwell.Reason.BOOTSTRAP, r.reason)
        assertNull(r.blocked)
        assertEquals(WAZE, d.holder)
    }

    /** KHOÁ: yêu cầu chính — ứng viên mới phải thắng 3 nhịp LIÊN TIẾP (≈ 2,4 s) mới tiếp quản. */
    @Test
    fun `ung vien moi phai thang 3 NHIP LIEN TIEP moi tiep quan`() {
        val c = Clock(); val d = dwell(c)
        d.onTick(WAZE, holderAllowed = true)                    // holder = WAZE

        c.advance(TICK)
        val t1 = d.onTick(MOD, holderAllowed = true)
        assertEquals(WAZE, t1.source); assertEquals(MOD, t1.blocked)
        assertEquals(NavSourceDwell.Reason.DWELL, t1.reason); assertEquals(1, t1.streak)
        assertFalse(t1.switched)

        c.advance(TICK)
        val t2 = d.onTick(MOD, holderAllowed = true)
        assertEquals(WAZE, t2.source); assertEquals(2, t2.streak)

        c.advance(TICK)
        val t3 = d.onTick(MOD, holderAllowed = true)
        assertEquals(MOD, t3.source)
        assertTrue(t3.switched)
        assertEquals(NavSourceDwell.Reason.TAKEOVER, t3.reason)
        assertEquals(3, t3.streak)
        assertNull(t3.blocked)
    }

    /**
     * KHOÁ: chữ "LIÊN TIẾP". Cấm cách đếm CỘNG DỒN — 2 nhịp rời rạc + 1 nhịp nữa KHÔNG được tiếp quản, vì
     * holder quay lại giữa chừng là bằng chứng nó vẫn đang dẫn.
     */
    @Test
    fun `holder quay lai giua chung thi chuoi DUT (X,Y,X,Y,Y,Y)`() {
        val c = Clock(); val d = dwell(c)
        d.onTick(WAZE, true)                                     // holder = WAZE (X)
        c.advance(TICK); assertEquals(1, d.onTick(MOD, true).streak)          // Y (1)
        c.advance(TICK); assertEquals(NavSourceDwell.Reason.HOLD, d.onTick(WAZE, true).reason)  // X → đứt
        c.advance(TICK); assertEquals(1, d.onTick(MOD, true).streak)          // Y (1 — KHÔNG phải 2)
        c.advance(TICK); assertEquals(2, d.onTick(MOD, true).streak)
        c.advance(TICK)
        val last = d.onTick(MOD, true)
        assertEquals(MOD, last.source)
        assertEquals(NavSourceDwell.Reason.TAKEOVER, last.reason)
    }

    /**
     * KHOÁ: 3 nhịp RẢI RÁC trong một phút (nav app bắn event thưa) KHÔNG phải dwell. Chuỗi phải liên tục theo
     * THỜI GIAN — dùng đồng hồ tiêm để kiểm, không đếm suông số lần gọi.
     */
    @Test
    fun `hai nhip cach nhau qua 2 chu ky tick thi chuoi DUT`() {
        val c = Clock(); val d = dwell(c)
        d.onTick(WAZE, true)
        c.advance(TICK); assertEquals(1, d.onTick(MOD, true).streak)
        c.advance(TICK); assertEquals(2, d.onTick(MOD, true).streak)
        c.advance(2 * TICK + 1)                                  // khoảng trống > 2 chu kỳ
        val gap = d.onTick(MOD, true)
        assertEquals(1, gap.streak)                              // làm lại từ đầu
        assertEquals(WAZE, gap.source)
    }

    // ── GUARD CAM-KẾT-RẼ ─────────────────────────────────────────────────────────────────────────────────

    /**
     * KHOÁ: chặn đổi nguồn khi đã cam kết vào khúc rẽ (R5 thắng R6).
     *
     * holderGraceMs đặt rất lớn CÓ CHỦ Ý: test này khoá RIÊNG guard, không lẫn với trần cứng R4 (đã có test
     * riêng bên dưới) — 10 nhịp × 800 ms = 8 s vốn đã vượt grace mặc định 6 s.
     */
    @Test
    fun `guard - holder duoi 150m thi KHONG doi nguon du challenger thang 10 nhip`() {
        val c = Clock(); val d = dwell(c, holderGraceMs = 600_000L)
        d.onTick(WAZE, true)
        repeat(10) {
            c.advance(TICK)
            val r = d.onTick(MOD, holderAllowed = true, holderTurnMeters = 120)
            assertEquals(WAZE, r.source, "đang trong khúc rẽ thì cấm đổi nguồn")
            assertEquals(NavSourceDwell.Reason.COMMITTED, r.reason)
            assertEquals(MOD, r.blocked)
        }
        assertEquals(WAZE, d.holder)
    }

    /**
     * KHOÁ: cấm đổi nguồn TỨC THÌ ngay giây thoát khúc rẽ (đúng lúc lệnh rẽ KẾ hiện ra). Guard reset streak,
     * nên qua rẽ rồi challenger vẫn phải làm lại đủ 3 nhịp.
     */
    @Test
    fun `guard reset streak - qua re roi challenger van phai lam lai 3 nhip`() {
        val c = Clock(); val d = dwell(c, holderGraceMs = 600_000L)
        d.onTick(WAZE, true)
        repeat(5) { c.advance(TICK); d.onTick(MOD, true, holderTurnMeters = 80) }   // bị chặn 5 nhịp
        c.advance(TICK)
        val a = d.onTick(MOD, true, holderTurnMeters = 800)      // đã qua rẽ, cự ly vọt lên
        assertEquals(1, a.streak, "streak phải làm lại từ 1, không cộng dồn 5 nhịp bị chặn")
        assertEquals(WAZE, a.source)
        c.advance(TICK); assertEquals(2, d.onTick(MOD, true, holderTurnMeters = 800).streak)
        c.advance(TICK)
        assertEquals(MOD, d.onTick(MOD, true, holderTurnMeters = 800).source)
    }

    /** KHOÁ: guard KHÔNG vĩnh viễn — cự ly vọt lên qua ngưỡng thì nguồn lại đổi được. */
    @Test
    fun `guard NHA khi cu ly vot len qua nguong (800m)`() {
        val c = Clock(); val d = dwell(c, holderGraceMs = 600_000L)
        d.onTick(WAZE, true)
        c.advance(TICK); assertEquals(NavSourceDwell.Reason.COMMITTED, d.onTick(MOD, true, holderTurnMeters = 100).reason)
        repeat(3) { c.advance(TICK); d.onTick(MOD, true, holderTurnMeters = 800) }
        assertEquals(MOD, d.holder)
    }

    /**
     * KHOÁ: degrade-safe CỐT LÕI — không biết cự ly thì KHÔNG chặn. Nếu "không biết" mà chặn thì một nguồn đã
     * chết (không còn phơi cự ly) sẽ giam khoá mãi mãi (CLAUDE.md §3, bài học `sats >= 4`).
     */
    @Test
    fun `holderTurnMeters = -1 (khong biet) thi guard KHONG chan`() {
        val c = Clock(); val d = dwell(c)
        d.onTick(WAZE, true)
        repeat(3) { c.advance(TICK); d.onTick(MOD, true, holderTurnMeters = NavSourceDwell.NO_METERS) }
        assertEquals(MOD, d.holder)
    }

    // ── ĐƯỜNG NHƯỜNG NGAY ────────────────────────────────────────────────────────────────────────────────

    /** KHOÁ: lựa chọn PREFER_* TƯỜNG MINH của user > mọi lớp làm trễ (bỏ qua CẢ dwell LẪN guard). */
    @Test
    fun `holderAllowed=false (user doi PREFER) thi nhuong NGAY, bo qua ca dwell lan guard`() {
        val c = Clock(); val d = dwell(c)
        d.onTick(WAZE, true)
        c.advance(TICK)
        val r = d.onTick(GMAPS, holderAllowed = false, holderTurnMeters = 50)   // đang trong khúc rẽ
        assertEquals(GMAPS, r.source)
        assertTrue(r.switched)
        assertEquals(NavSourceDwell.Reason.MODE, r.reason)
    }

    /**
     * KHOÁ HỒI QUY (08-22 vòng 1): R4 ("holder đã tắt") chỉ được bắn khi holder THẬT SỰ MẤT CỬA SỔ, chứ không
     * phải khi holder đơn thuần THUA BẦU CỬ nhiều nhịp liền.
     *
     * Bug: `holderSeenAt` chỉ được làm tươi ở R2 (`candidate == prev`) và trong `adopt()` ⇒ nó đo "lần cuối
     * holder THẮNG bầu cử". Khi một app khác đọc được view-id và thắng MỌI nhịp, mốc đóng băng ⇒ sau
     * `holderGraceMs` (6 s) R4 TAKEOVER dù cửa sổ holder vẫn hiện, vẫn đang dẫn, và R5 đang báo COMMITTED
     * (còn 100 m tới rẽ). Vì R4 đứng TRƯỚC R5, trần bảo vệ thật của cả hai lớp chỉ còn 6 s — tức đổi nguồn
     * ĐÚNG GIÂY vào cua, đúng cái nguy hiểm nhất mà B-II sinh ra để chặn.
     */
    @Test
    fun `holder CON CUA SO thi R4 khong duoc duoi no du thua bau cu lien tuc`() {
        val c = Clock(); val d = dwell(c)
        d.onTick(WAZE, true, holderPresent = false)                 // holder = WAZE
        // MOD thắng bầu cử mọi nhịp trong 20 s, nhưng cửa sổ WAZE vẫn còn và WAZE còn 100 m tới rẽ (R5).
        repeat(25) {
            c.advance(TICK)
            val r = d.onTick(MOD, holderAllowed = true, holderPresent = true, holderTurnMeters = 100)
            assertEquals(NavSourceDwell.Reason.COMMITTED, r.reason, "nhịp $it: guard cam-kết-rẽ phải giữ")
            assertEquals(WAZE, r.source)
        }
        assertEquals(WAZE, d.holder, "holder còn cửa sổ + còn trong khúc rẽ ⇒ KHÔNG được đổi nguồn")
    }

    /**
     * KHOÁ: mặt kia của cùng một luật — mốc "còn thấy" chỉ tươi khi caller ĐO ĐƯỢC sự hiện diện. Cửa sổ holder
     * biến mất (holderPresent=false) thì R4 vẫn phải nhả đúng sau `holderGraceMs`, kể cả đang trong khúc rẽ.
     * Mặc định `holderPresent = false` là degrade-safe: caller không đo được thì KHÔNG kéo dài quyền giữ.
     */
    @Test
    fun `holder MAT CUA SO thi R4 van nha dung sau holderGraceMs`() {
        val c = Clock(); val d = dwell(c)
        d.onTick(WAZE, true, holderPresent = true)
        c.advance(SourceArbiter.STALE_MS + 1)
        val r = d.onTick(MOD, holderAllowed = true, holderPresent = false, holderTurnMeters = 100)
        assertEquals(MOD, r.source)
        assertEquals(NavSourceDwell.Reason.TAKEOVER, r.reason)
    }

    /** KHOÁ: trần cứng thời gian chặn — app nav bị TẮT lúc còn 100 m tới rẽ không được giam nguồn. */
    @Test
    fun `holder vang mat qua holderGraceMs thi nhuong ngay ke ca dang trong guard`() {
        val c = Clock(); val d = dwell(c)
        d.onTick(WAZE, true)
        c.advance(SourceArbiter.STALE_MS + 1)
        val r = d.onTick(MOD, holderAllowed = true, holderTurnMeters = 100)
        assertEquals(MOD, r.source)
        assertEquals(NavSourceDwell.Reason.TAKEOVER, r.reason)
    }

    /** KHOÁ: R0 — không có cửa sổ nav nào thì KHÔNG bầu bừa, nhưng phải có đường NHẢ. */
    @Test
    fun `candidate=null - giu holder, roi NHA sau holderGraceMs`() {
        val c = Clock(); val d = dwell(c)
        d.onTick(WAZE, true)
        c.advance(TICK)
        val hold = d.onTick(candidate = null, holderAllowed = true)
        assertEquals(WAZE, hold.source); assertEquals(NavSourceDwell.Reason.HOLD, hold.reason)

        c.advance(SourceArbiter.STALE_MS + 1)
        val rel = d.onTick(candidate = null, holderAllowed = true)
        assertNull(rel.source)
        assertEquals(NavSourceDwell.Reason.RELEASED, rel.reason)
        assertNull(d.holder)

        // Không holder + không candidate = IDLE (không được nhả lần hai / không được crash).
        c.advance(TICK)
        assertEquals(NavSourceDwell.Reason.IDLE, d.onTick(null, false).reason)
    }

    // ── NGƯỠNG CAM-KẾT-RẼ ────────────────────────────────────────────────────────────────────────────────

    /**
     * KHOÁ: degrade-safe của hàm ngưỡng khi HAL tốc độ không đọc được — "không đọc được ≠ đứng yên", phải rơi
     * về SÀN chứ không về 0 (0 = guard tắt hẳn).
     */
    @Test
    fun `commitMeters - san 150m khi rate be hon hoac bang 0 hoac NaN hoac vo cuc`() {
        assertEquals(150, NavSourceDwell.commitMeters(0.0))
        assertEquals(150, NavSourceDwell.commitMeters(-5.0))
        assertEquals(150, NavSourceDwell.commitMeters(Double.NaN))
        assertEquals(150, NavSourceDwell.commitMeters(Double.POSITIVE_INFINITY))
        assertEquals(150, NavSourceDwell.commitMeters(10.0))     // 10 × 8 = 80 < sàn → sàn
    }

    /** KHOÁ: phần động TÁI DÙNG closingRate thật sự mở rộng ngưỡng (cao tốc), không phải hằng số chết. */
    @Test
    fun `commitMeters - 30 mps ra 240m, chan o 200m (cao toc)`() {
        assertEquals(240, NavSourceDwell.commitMeters(30.0))
        val c = Clock(); val d = dwell(c, holderGraceMs = 600_000L)
        d.onTick(WAZE, true)
        c.advance(TICK)
        val r = d.onTick(MOD, true, holderTurnMeters = 200, closingRateMps = 30.0)
        assertEquals(NavSourceDwell.Reason.COMMITTED, r.reason, "200 m ≤ 240 m ⇒ đã cam kết ở tốc độ cao tốc")
        assertEquals(240, r.commitMeters)
    }

    // ── THỨ TỰ DÒ ────────────────────────────────────────────────────────────────────────────────────────

    /** KHOÁ: thứ tự dò là cái CHO guard lấy được cự ly tươi của holder; đảo thứ tự này là guard mù. */
    @Test
    fun `probeOrder - holder duoc do TRUOC, phan con lai GIU nguyen thu tu hang`() {
        val c = Clock(); val d = dwell(c)
        assertEquals(listOf(MOD, WAZE), d.probeOrder(listOf(MOD, WAZE)), "chưa có holder → giữ nguyên hạng")

        d.onTick(WAZE, true)                                     // holder = WAZE (hạng chót)
        assertEquals(listOf(WAZE, MOD, GMAPS), d.probeOrder(listOf(MOD, GMAPS, WAZE)))
        // holder không còn trong danh sách → giữ nguyên hạng, KHÔNG bịa thêm phần tử.
        assertEquals(listOf(MOD, GMAPS), d.probeOrder(listOf(MOD, GMAPS)))
        // không trùng lặp, không mất phần tử.
        val out = d.probeOrder(listOf(MOD, WAZE, GMAPS))
        assertEquals(out.size, out.distinct().size)
        assertEquals(setOf(MOD, WAZE, GMAPS), out.toSet())
    }

    /**
     * KHOÁ hồi quy 08-23 vòng 2b — **KHÔNG dò một pkg hai lần trong CÙNG một nhịp**.
     *
     * `NavWindowPicker.rank` trả MỘT mục cho mỗi CỬA SỔ và `.distinct()` của nó chạy trên
     * `Pick(pkg, bounds, displayId)`, nên cùng một app có cửa sổ trên display 0 VÀ display 1 (đúng trạng thái
     * ĐANG CHIẾU của app này) vẫn ra hai mục. Đo thật trước sửa (probe 08-23):
     * `probeOrder(["com.waze","vn.vietmap.live","com.waze"])` → `[vietmap, waze, waze]`.
     *
     * Vòng `for (pkg in order)` của `resolveNavWindowRegardlessOfFocus` chỉ `break` khi ĐỌC ĐƯỢC, nên một pkg
     * đọc không được bị dò lại nguyên lần nữa — với app đi đường content-desc (VietMap: đi cây tới
     * `MAX_VISIT_NODES` node, mỗi `getChild` là một lượt binder) đó là GẤP ĐÔI ngân sách đi cây mỗi 800 ms
     * trên luồng a11y (main) của đầu xe, đúng thứ trần `MAX_VISIT_NODES` vừa được thêm để chặn.
     */
    @Test
    fun `probeOrder - KHU TRUNG pkg (cung app hai display luc dang chieu)`() {
        val c = Clock(); val d = dwell(c)

        // Chưa có holder: vẫn phải khử trùng, và GIỮ lần gặp ĐẦU (hạng diện tích cao nhất).
        assertEquals(listOf(WAZE, GMAPS), d.probeOrder(listOf(WAZE, WAZE, GMAPS)))
        assertEquals(listOf(WAZE, GMAPS), d.probeOrder(listOf(WAZE, GMAPS, WAZE)))

        d.onTick(VIETMAP, true)                                  // holder = VietMap
        assertEquals(VIETMAP, d.holder)
        // Ca đo thật: holder lên đầu MỘT lần, phần còn lại cũng chỉ một lần mỗi pkg.
        assertEquals(listOf(VIETMAP, WAZE), d.probeOrder(listOf(WAZE, VIETMAP, WAZE)))
        // Chính holder bị lặp (VietMap trên cả hai display) cũng chỉ ra một mục.
        assertEquals(listOf(VIETMAP, GMAPS), d.probeOrder(listOf(VIETMAP, GMAPS, VIETMAP)))
    }


    // ── HỒI QUY 08-23 vòng 2: R5 KHÔNG ĐƯỢC GIAM NGUỒN VĨNH VIỄN ─────────────────────────────────────────

    /**
     * KHOÁ [P1] — **khoá chết việc bầu nguồn**. Ca hỏng tất định (suy từ source, chưa đo trên xe):
     *
     *  1. VietMap được [com.byd.clusternav.VietMapAutostart] tự bật lúc boot ⇒ nó là ứng viên ĐẦU TIÊN ⇒ R1
     *     BOOTSTRAP nhận nó làm holder ngay.
     *  2. Cửa sổ nó nằm NỀN, banner đọng ở `"0m Lý Thường Kiệt"` (0 là giá trị HỢP LỆ theo
     *     `VietMapDescParser.Reading.turnMeters`), và `NavWindowPicker.rank` KHÔNG lọc cửa sổ nền ⇒
     *     `holderPresent = true` mỗi nhịp ⇒ `holderSeenAt` luôn tươi ⇒ **R4 không bao giờ bắn**.
     *  3. AUTO ⇒ `holderAllowed = true` ⇒ **R3 không bắn**.
     *  4. `0 in 0..commit` ⇒ **R5 bắn mãi mãi** ⇒ Waze/GMaps đang dẫn THẬT không bao giờ lên cụm; không có
     *     lối thoát nào ngoài kill VietMap.
     *
     * Đây đúng bẫy CLAUDE.md §3 ("không gate một đường phục hồi bằng dữ liệu mà chỉ chính đường đó mới làm
     * mới được"): holder tự làm tươi con số giam chính nó. Sau sửa, R5 có TRẦN CỨNG
     * [NavSourceDwell.COMMIT_MAX_MS] cho một con số ĐÓNG BĂNG, hết trần thì rơi xuống R6 (vẫn phải dwell đủ).
     */
    @Test
    fun `R5 - cu ly DONG BANG khong duoc giam nguon vinh vien`() {
        val c = Clock(); val d = dwell(c)
        d.onTick(VIETMAP, true)                                  // R1 BOOTSTRAP → holder = VietMap
        var lastReason = NavSourceDwell.Reason.BOOTSTRAP
        var takeoverAt = -1L
        val t0 = c.t
        repeat(120) {                                            // 120 × 800 ms = 96 s
            c.advance(TICK)
            val r = d.onTick(WAZE, holderAllowed = true, holderPresent = true, holderTurnMeters = 0)
            lastReason = r.reason
            if (r.source == WAZE && takeoverAt < 0) takeoverAt = c.t - t0
        }
        assertEquals(WAZE, d.holder, "app đang dẫn thật PHẢI tiếp quản được — trước sửa là khoá chết")
        assertEquals(NavSourceDwell.Reason.HOLD, lastReason)
        assertTrue(
            takeoverAt in NavSourceDwell.COMMIT_MAX_MS..(NavSourceDwell.COMMIT_MAX_MS + 10 * TICK),
            "phải giam ĐỦ trần rồi mới nhả (nhả sớm = mất guard cam-kết-rẽ), thực tế ${takeoverAt}ms",
        )
    }

    /**
     * MẶT KIA của cùng bất biến — trần KHÔNG được rút ngắn một khúc rẽ THẬT. Cự ly đếm ngược (150→0) làm tươi
     * mốc cam-kết mỗi lần con số ĐỔI, nên guard giữ suốt cả khúc rẽ dù tổng thời gian vượt [NavSourceDwell.COMMIT_MAX_MS].
     */
    @Test
    fun `R5 - cu ly DANG GIAM van duoc guard suot ca khuc re`() {
        val c = Clock(); val d = dwell(c)
        d.onTick(VIETMAP, true)
        // 150 → 0 m, mỗi nhịp giảm 1 m: 151 nhịp × 800 ms = 120,8 s ≫ COMMIT_MAX_MS (30 s).
        for (m in 150 downTo 0) {
            c.advance(TICK)
            val r = d.onTick(WAZE, holderAllowed = true, holderPresent = true, holderTurnMeters = m)
            assertEquals(NavSourceDwell.Reason.COMMITTED, r.reason, "còn ${m}m tới rẽ mà đã nhả guard")
            assertEquals(VIETMAP, r.source)
        }
    }

    /** Ra khỏi dải cam-kết rồi VÀO lại (khúc rẽ KẾ) phải được một cửa sổ guard MỚI, không mang mốc cũ sang. */
    @Test
    fun `R5 - roi khoi dai cam-ket thi moc duoc dat lai`() {
        val c = Clock(); val d = dwell(c)
        d.onTick(VIETMAP, true)
        repeat(50) { c.advance(TICK); d.onTick(WAZE, true, holderPresent = true, holderTurnMeters = 0) }  // tiêu hết trần
        c.advance(TICK)
        d.onTick(WAZE, true, holderPresent = true, holderTurnMeters = 900)   // ra khỏi dải → mốc xoá
        // ⚠ holder có thể đã đổi ở vòng trên; lấy lại holder hiện tại làm mốc so sánh.
        val holderNow = d.holder!!
        c.advance(TICK)
        val r = d.onTick(if (holderNow == WAZE) VIETMAP else WAZE, true, holderPresent = true, holderTurnMeters = 40)
        assertEquals(NavSourceDwell.Reason.COMMITTED, r.reason, "khúc rẽ KẾ phải có guard mới")
    }

    /** KHOÁ: reset() bắt đầu phiên SẠCH — nguồn đầu tiên sau reset lại là BOOTSTRAP, không phải TAKEOVER. */
    @Test
    fun `reset - phien moi bat dau sach`() {
        val c = Clock(); val d = dwell(c)
        d.onTick(WAZE, true)
        d.reset()
        assertNull(d.holder)
        assertEquals(NavSourceDwell.Reason.BOOTSTRAP, d.onTick(MOD, true).reason)
    }
}
