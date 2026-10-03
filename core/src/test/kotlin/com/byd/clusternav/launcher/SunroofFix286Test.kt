package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.byd.clusternav.launcher.voice.partNotOnThisCar

/**
 * ═══ FIX286 · R-SR — CỬA SỔ TRỜI: chuỗi lệnh HAL "vàng" + đường đọc + cổng có-nóc (spec `kachi-286-field-fixes.html`) ═══
 *
 * Nguồn của mọi con số trong tệp: app Cài đặt BYD (`com.byd.vehiclesettings`, [ĐO nguồn OEM, đọc lại 02/10]) —
 * `SunRoofFragment.java:834-836` (`removeMessages(0)` → `setSunRoofState(0)` → `sendEmptyMessageDelayed(0, 200)`),
 * `:858-860` (cùng nhịp với 100), `:1109` (`setSunRoofState(255)` khi message 0 tới), `SunRoofModel.java:113,127`
 * (`getWindowOpenPercent(5)` / `setMoonRoofState(i)`); stub `BYDAutoBodyworkDevice.java:213-216,338-344`.
 *
 * ⚠ Bài này KHÔNG chứng minh xe nhận lệnh — phần đó [CHƯA BIẾT] tới lượt thử OTA của anh em (OC-SR). Nó khoá
 * **đúng chuỗi lệnh app OEM gửi** và đúng các luật ăn theo, để một lượt sửa sau không lặng lẽ quay về 1/2.
 */
class SunroofFix286Test {

    private val roofFqn = HalBindingTable.deviceFqn("BYDAutoBodyworkDevice")

    /** Bộ hẹn GIẢ có đồng hồ tay: [advance] chạy việc tới hạn theo thứ tự giờ — tất định, không ngủ thật. */
    private class ManualScheduler : WriteReleaseScheduler {
        private data class Job(val key: String, val due: Long, val action: () -> Unit)
        private val jobs = mutableListOf<Job>()
        var now = 0L
            private set
        val scheduledKeys = mutableListOf<String>()

        override fun schedule(key: String, delayMs: Long, action: () -> Unit) {
            jobs.removeAll { it.key == key }
            jobs.add(Job(key, now + delayMs, action))
            scheduledKeys.add(key)
        }

        override fun cancel(key: String): Boolean = jobs.removeAll { it.key == key }

        fun advance(ms: Long) {
            val until = now + ms
            while (true) {
                val next = jobs.filter { it.due <= until }.minByOrNull { it.due } ?: break
                jobs.remove(next)
                now = next.due
                next.action()
            }
            now = until
        }
    }

    /** Gateway đếm lượt đọc getter (bản giả dùng chung không đếm) — uỷ quyền mọi thứ khác. */
    private class CountingGateway(private val inner: FakeHalGateway) : HalGateway by inner {
        val getterCalls = mutableListOf<Pair<String, Int?>>()
        override fun getter(deviceFqn: String, method: String, arg: Int?): String? {
            getterCalls += method to arg
            return inner.getter(deviceFqn, method, arg)
        }
    }

    private fun named(gw: FakeHalGateway) = gw.namedCalls.filter { it.method == "setMoonRoofState" }.map { it.args }

    // ══ SR1 + SR2 · chuỗi lệnh vàng ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `mo noc gui 100 roi NHA 255 dung 200 ms sau, cung method cung device`() {
        val gw = FakeHalGateway(namedRc = 0L)
        val clock = ManualScheduler()
        val table = HalBindingTable(gw, clock)
        assertEquals(0L, table.write("sunroof", 1))
        assertEquals(listOf(listOf(100)), named(gw), "lệnh đầu = MOONROOF_OPEN 100, chưa nhả")
        clock.advance(199)
        assertEquals(listOf(listOf(100)), named(gw), "199 ms: chưa tới giờ nhả")
        clock.advance(1)
        assertEquals(listOf(listOf(100), listOf(255)), named(gw), "đúng 200 ms: nhả MOONROOF_INVALID 255")
        assertTrue(gw.namedCalls.all { it.fqn == roofFqn }, "nhả đi CÙNG device với lệnh")
        clock.advance(10_000)
        assertEquals(2, named(gw).size, "nhả đúng MỘT lần")
    }

    @Test
    fun `dong noc gui 0 roi nha 255`() {
        val gw = FakeHalGateway(namedRc = 0L)
        val clock = ManualScheduler()
        HalBindingTable(gw, clock).write("sunroof", 0)
        clock.advance(200)
        assertEquals(listOf(listOf(0), listOf(255)), named(gw))
    }

    @Test
    fun `mo roi dong trong 150 ms thi luot nha cua lenh mo bi HUY - chi mot luot 255`() {
        // OEM `removeMessages(0)` trước MỖI lệnh: lượt nhả đang chờ của lệnh trước không bao giờ tới bus.
        val gw = FakeHalGateway(namedRc = 0L)
        val clock = ManualScheduler()
        val table = HalBindingTable(gw, clock)
        table.write("sunroof", 1)
        clock.advance(150)
        table.write("sunroof", 0)
        clock.advance(5_000)
        assertEquals(listOf(listOf(100), listOf(0), listOf(255)), named(gw))
    }

    @Test
    fun `HUY luot nha dang cho TRUOC khi gui lenh moi (OEM removeMessages truoc setSunRoofState)`() {
        // Thứ tự là cả điểm: huỷ SAU khi gửi thì lượt nhả cũ có thể chen vào ngay sau lệnh mới và xoá nó trên bus.
        val events = mutableListOf<String>()
        val inner = ManualScheduler()
        val clock = object : WriteReleaseScheduler by inner {
            override fun cancel(key: String): Boolean = inner.cancel(key).also { events += "cancel:$key=$it" }
        }
        val gw = object : HalGateway by FakeHalGateway() {
            override fun namedInt(deviceFqn: String, method: String, args: IntArray): Long? {
                events += "send:${args.toList()}"
                return 0L
            }
        }
        val table = HalBindingTable(gw, clock)
        table.write("sunroof", 1)
        inner.advance(150)
        table.write("sunroof", 0)
        inner.advance(200)
        assertEquals(
            listOf("cancel:sunroof=false", "send:[100]", "cancel:sunroof=true", "send:[0]", "send:[255]"),
            events,
        )
    }

    @Test
    fun `lenh hop le roi lenh HONG trong 200 ms - yeu cau cu van duoc nha`() {
        // Lượt nhả của lệnh hợp lệ đã bị huỷ ở bước ②; lệnh mới hỏng ⇒ vẫn phải có MỘT lượt 255 sau nó, nếu không
        // yêu cầu 100 bị giữ trên bus không ai nhả.
        var rc: Long? = 0L
        val sends = mutableListOf<List<Int>>()
        val gw = object : HalGateway by FakeHalGateway() {
            override fun namedInt(deviceFqn: String, method: String, args: IntArray): Long? {
                sends += args.toList()
                return if (args.single() == 255) 0L else rc
            }
        }
        val clock = ManualScheduler()
        val table = HalBindingTable(gw, clock)
        table.write("sunroof", 1)
        clock.advance(100)
        rc = null
        table.write("sunroof", 0)
        clock.advance(199)
        assertEquals(listOf(listOf(100), listOf(0)), sends, "chưa tới 200 ms tính từ lệnh SAU")
        clock.advance(1)
        assertEquals(listOf(listOf(100), listOf(0), listOf(255)), sends)
    }

    /**
     * [Senior review FIX286 Pass 1 · P2] Bộ hẹn thật chỉ huỷ được việc CÒN trong hàng đợi: lượt nhả đã rời hàng đợi
     * (luồng bộ hẹn đã nhận việc) thì `cancel` trả `false`. Bộ hẹn giả dưới đây mô phỏng đúng ca đó — mọi việc "đã rời
     * hàng đợi" ngay khi hẹn, `cancel` không gỡ được gì, việc chạy khi bài gọi. Lệnh mới tới trong khe ấy thì lượt nhả cũ
     * KHÔNG được gửi 255 ngay sau lệnh mới (nhả luôn yêu cầu vừa gửi) — chỉ lượt nhả của lệnh mới được đi.
     */
    @Test
    fun `luot nha DA roi hang doi khi lenh moi toi - khong gui 255 ngay sau lenh moi`() {
        class Taken : WriteReleaseScheduler {
            val jobs = mutableListOf<() -> Unit>()
            override fun schedule(key: String, delayMs: Long, action: () -> Unit) { jobs += action }
            override fun cancel(key: String): Boolean = false
        }
        // Ca 1 — lệnh mới tới TRƯỚC khi lượt nhả cũ kịp gửi ⇒ lượt cũ bỏ.
        run {
            val gw = FakeHalGateway(namedRc = 0L)
            val clock = Taken()
            val table = HalBindingTable(gw, clock)
            table.write("sunroof", 1)
            table.write("sunroof", 0)
            clock.jobs[0]()   // lượt nhả của lệnh MỞ tới muộn, sau lệnh ĐÓNG
            assertEquals(listOf(listOf(100), listOf(0)), named(gw), "lượt nhả cũ không được nhả lệnh ĐÓNG vừa gửi")
            clock.jobs[1]()
            assertEquals(listOf(listOf(100), listOf(0), listOf(255)), named(gw), "chỉ lượt nhả của lệnh mới đi")
        }
        // Ca 2 — lượt nhả cũ gửi XONG trước lệnh mới ⇒ đúng thứ tự OEM 100 → 255 → 0 → 255.
        run {
            val gw = FakeHalGateway(namedRc = 0L)
            val clock = Taken()
            val table = HalBindingTable(gw, clock)
            table.write("sunroof", 1)
            clock.jobs[0]()
            table.write("sunroof", 0)
            clock.jobs[1]()
            assertEquals(listOf(listOf(100), listOf(255), listOf(0), listOf(255)), named(gw))
        }
    }

    @Test
    fun `lenh hong (rc null hoac sentinel) thi KHONG nha`() {
        listOf(null, HalBindingTable.SENTINEL_NOT_PROVISIONED, HalBindingTable.SENTINEL_INVALID).forEach { rc ->
            val gw = FakeHalGateway(namedRc = rc)
            val clock = ManualScheduler()
            HalBindingTable(gw, clock).write("sunroof", 1)
            clock.advance(5_000)
            assertEquals(listOf(listOf(100)), named(gw), "rc=$rc: không có gì trên bus để nhả")
        }
    }

    @Test
    fun `nut khong khai release thi khong bao gio bi nha`() {
        val gw = FakeHalGateway(namedRc = 0L)
        val clock = ManualScheduler()
        val table = HalBindingTable(gw, clock)
        table.write("win_lf", 1)
        table.write("wireless_charge", 1)
        clock.advance(5_000)
        assertEquals(2, gw.namedCalls.size, "đúng hai lượt — không lượt nhả nào")
        assertTrue(clock.scheduledKeys.isEmpty(), "không nút nào khác hẹn gì")
        assertEquals(listOf("sunroof"), ControlRegistry.ALL.filter { it.release != null }.map { it.id })
    }

    // ══ SR3 · đường đọc ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `nut noc doc getWindowOpenPercent(5) - 0 dong, 37 mo, ngoai 0-100 la null`() {
        fun read(raw: String?): Int? {
            val gw = FakeHalGateway(gettersByArg = mapOf("getWindowOpenPercent" to mapOf(5 to raw, 1 to "98")))
            return HalBindingTable(gw).readState("sunroof").also {
                assertEquals(5, gw.getterArgs["getWindowOpenPercent"], "nóc = windowId 5 (SunRoofModel.java:113)")
            }
        }
        assertEquals(0, read("0"))
        assertEquals(37, read("37"))
        assertEquals(100, read("100"))
        listOf("255", "65535", "-1", "101").forEach { assertNull(read(it), "$it không phải phần trăm") }
        assertNull(read(null), "off-car ⇒ null (⚠), không phải 0")
    }

    @Test
    fun `nut noc KHONG con doc getSunroofState (enum chua ro)`() {
        // Mồi getSunroofState = 4 (SUNROOF_CLOSE của bảng enum thứ hai): nếu đường đọc còn đi qua getter này thì nóc
        // ĐÓNG sẽ đọc thành "đang mở" — đúng gốc C của spec.
        val gw = FakeHalGateway(getters = mapOf("getSunroofState" to "4"), gettersByArg = mapOf("getWindowOpenPercent" to mapOf(5 to "0")))
        assertEquals(0, HalBindingTable(gw).readState("sunroof"))
        val path = readPathOf("sunroof")!!
        assertEquals("BYDAutoBodyworkDevice.getWindowOpenPercent", path.key)
        assertEquals(HalReadTables.SUNROOF_WINDOW_ID, path.arg)
    }

    @Test
    fun `kinh lai van doc windowId 1 - phep muon cua noc khong lam lech kinh`() {
        val gw = FakeHalGateway(gettersByArg = mapOf("getWindowOpenPercent" to mapOf(1 to "98", 5 to "0")))
        assertEquals(98, HalBindingTable(gw).readState("win_lf"))
        assertEquals(1, gw.getterArgs["getWindowOpenPercent"])
    }

    @Test
    fun `nut kinh doc so rac ngoai 0-100 thi la null - con o hien thi datum giu nguyen`() {
        val gw = FakeHalGateway(gettersByArg = mapOf("getWindowOpenPercent" to mapOf(1 to "255")))
        val table = HalBindingTable(gw)
        assertNull(table.readState("win_lf"), "nút: 255 không phải phần trăm")
        assertEquals(255, table.readInt("window_lf"), "ô datum: KHÔNG đổi một byte (phạm vi cố ý hẹp — KDoc percentReadingValid)")
    }

    @Test
    fun `nut muon getter VUNG KHAC khong phai nut cua datum ay`() {
        assertFalse(CapabilityDots.readsDatumItself(ControlRegistry.byId("sunroof")!!), "nóc đọc windowId 5 ≠ kính lái 1")
        assertTrue(CapabilityDots.readsDatumItself(ControlRegistry.byId("win_lf")!!))
        assertTrue(CapabilityDots.readsDatumItself(ControlRegistry.byId("temp")!!), "ghi đè TRÙNG tham số datum ⇒ vẫn là nút của nó")
    }

    @Test
    fun `chip Kinh lai van la cua nut win_lf, khong phai cua noc`() {
        assertEquals(ControlRegistry.byId("win_lf")!!.icon, CapabilityDots.iconOverride("window_lf"))
        // `temp` ghi đè readArg TRÙNG tham số của datum ⇒ vẫn là nút của `inside_temp` như trước.
        assertEquals(ControlRegistry.byId("temp")!!.icon, CapabilityDots.iconOverride("inside_temp"))
    }

    // ══ SR4 · cổng có-nóc ═════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `cau hinh 0 hoac 2 thi tu choi, 0 luot ghi HAL, nut bao khong co tren xe`() {
        listOf(0, 2).forEach { cfg ->
            val gw = FakeHalGateway(namedRc = 0L, getters = mapOf("getMoonRoofConfig" to "$cfg"))
            val table = HalBindingTable(gw, ManualScheduler())
            assertNull(table.write("sunroof", 1), "cfg=$cfg ⇒ từ chối")
            assertTrue(gw.namedCalls.isEmpty(), "cfg=$cfg ⇒ KHÔNG một lượt namedInt nào")
            assertTrue(table.featureAbsentOnCar("sunroof"))
            assertFalse(CarControlAdapter(table).wiredOnThisCar("sunroof"), "giọng nói nói 'xe này không có'")
            assertTrue(CarControlAdapter(table).partAbsentOnThisCar("sunroof"), "và đúng câu 'xe tự báo không có bộ phận'")
        }
        val c = com.byd.clusternav.launcher.voice.VoiceIntent.Control("sunroof", 1)
        assertTrue(
            com.byd.clusternav.launcher.voice.VoiceReply.partNotOnThisCar(c).contains("xe này không có bộ phận này"),
        )
    }

    @Test
    fun `cau hinh 1, 3, so la, sentinel, doc hong thi cho qua (fail-open)`() {
        listOf("1", "3", "65535", "int=-2147482648", null).forEach { cfg ->
            val gw = FakeHalGateway(namedRc = 0L, getters = mapOf("getMoonRoofConfig" to cfg))
            val table = HalBindingTable(gw)
            assertEquals(0L, table.write("sunroof", 1), "cfg=$cfg ⇒ ghi như 2.85")
            assertEquals(listOf(listOf(100)), named(gw))
            assertFalse(table.featureAbsentOnCar("sunroof"), "cfg=$cfg ⇒ không khai vắng")
            assertFalse(CarControlAdapter(table).partAbsentOnThisCar("sunroof"))
        }
    }

    @Test
    fun `cau hinh doc DUOC thi chi doc mot lan moi tien trinh, doc hong thi hoi lai`() {
        val ok = CountingGateway(FakeHalGateway(namedRc = 0L, getters = mapOf("getMoonRoofConfig" to "1")))
        val t1 = HalBindingTable(ok)
        repeat(3) { t1.write("sunroof", 1) }
        assertEquals(1, ok.getterCalls.count { it.first == "getMoonRoofConfig" }, "cấu hình xe không đổi lúc chạy")
        val bad = CountingGateway(FakeHalGateway(namedRc = 0L))
        val t2 = HalBindingTable(bad)
        repeat(2) { t2.write("sunroof", 1) }
        assertEquals(2, bad.getterCalls.count { it.first == "getMoonRoofConfig" }, "đọc hỏng không được cất thành 'có'")
    }

    /** Gateway đổi được giá trị `getMoonRoofConfig` giữa hai lệnh — mô phỏng getter trả mặc định trước khi khung báo. */
    private class ConfigGateway(val inner: FakeHalGateway, var config: String?) : HalGateway by inner {
        override fun getter(deviceFqn: String, method: String, arg: Int?): String? =
            if (method == "getMoonRoofConfig") config else inner.getter(deviceFqn, method, arg)
    }

    /**
     * [Senior review FIX286 Pass 3 · P3] mã "vắng" KHÔNG được cất: một lượt đọc ra 0 lúc vừa nổ máy (getter trả mặc định
     * trước khi khung báo cấu hình — [ĐOÁN]) mà cất cả tiến trình là khoá nóc cả chuyến, và lượt thử OC-SR ra kết luận sai.
     * Câu trả lời "CÓ" thì vẫn cất (bài `cau hinh doc DUOC thi chi doc mot lan …` giữ nguyên).
     */
    @Test
    fun `cau hinh noi VANG thi lenh sau doc lai, noi CO thi cat ca tien trinh`() {
        val gw = ConfigGateway(FakeHalGateway(namedRc = 0L), "0")
        val table = HalBindingTable(gw)
        assertNull(table.write("sunroof", 1), "lượt đầu đọc 0 ⇒ từ chối")
        assertTrue(named(gw.inner).isEmpty(), "từ chối = 0 lượt namedInt")
        assertTrue(table.featureAbsentOnCar("sunroof"))
        gw.config = "1"
        assertFalse(table.featureAbsentOnCar("sunroof"), "đọc lại ⇒ xe nay nói CÓ")
        assertEquals(0L, table.write("sunroof", 1), "lệnh sau đi qua — không bị một lượt đọc 0 khoá cả chuyến")
        assertEquals(listOf(listOf(100)), named(gw.inner))
        gw.config = "0"
        assertEquals(0L, table.write("sunroof", 0), "đã đọc được CÓ ⇒ cất cả tiến trình (cấu hình xe không đổi lúc chạy)")
        assertEquals(listOf(listOf(100), listOf(0)), named(gw.inner))
    }

    @Test
    fun `cong co-mat la DU LIEU cua dong, khong nut nao khac co`() {
        assertEquals(listOf("sunroof"), ControlRegistry.ALL.filter { it.presence != null }.map { it.id })
        val p = ControlRegistry.byId("sunroof")!!.presence!!
        assertEquals(setOf(0, 2), p.absentWhen, "CONFIG_NONE 0 · CONFIG_SUNSHADE_PANEL 2 (stub :213-216)")
        assertTrue(HalBindingTable.routeOf(p.getter) is BindingRoute.NamedMethod)
        ControlRegistry.ALL.flatMap { it.probe }.forEach {
            assertTrue(HalBindingTable.routeOf(it.getter) is BindingRoute.NamedMethod, "${it.getter} phải là getter named-method")
        }
    }

    // ══ SR6 · nhật ký ═════════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `nhat ky - anh chup 7 getter mot lan, dong ctl co rc tho + doc truoc, dong nha, dong doc sau 3 s`() {
        val lines = mutableListOf<String>()
        val gw = FakeHalGateway(
            namedRc = 0L,
            getters = mapOf(
                "getMoonRoofConfig" to "1", "getSunroofInitState" to "1", "getWindowPermitState" to "0",
                "getSunroofCloseNotice" to "0", "getSunroofState" to "4", "getSunroofPosition" to "2",
            ),
            gettersByArg = mapOf("getWindowOpenPercent" to mapOf(5 to "0")),
        )
        val clock = ManualScheduler()
        val table = HalBindingTable(gw, clock) { lines += it }
        table.write("sunroof", 1)
        assertEquals(1, lines.size, "ảnh chụp KHÔNG chạy trên luồng đang ghi (luồng chính khi đi giọng nói/cầu)")
        clock.advance(3_000)
        table.write("sunroof", 0)
        assertEquals(
            "ctl #1 id=sunroof m=named:setMoonRoofState args=[100] rc=0 before=0 why=cmd cancel=0 release=255@200ms",
            lines[0],
        )
        assertEquals(
            "ctl-probe id=sunroof getMoonRoofConfig=1 getSunroofInitState=1 getWindowPermitState=0 " +
                "getSunroofCloseNotice=0 getWindowOpenPercent(5)=0 getSunroofState=4 getSunroofPosition=2",
            lines[1],
        )
        assertEquals(
            "ctl #2 id=sunroof m=named:setMoonRoofState args=[255] rc=0 before=- why=release#1 cancel=0 release=-",
            lines[2],
        )
        assertEquals("ctl-after #1 id=sunroof after=0", lines[3])
        assertEquals(1, lines.count { it.startsWith(CtlWriteJournal.TAG_PROBE) }, "ảnh chụp MỘT lần mỗi tiến trình")
        assertTrue(lines[4].startsWith("ctl #3 id=sunroof m=named:setMoonRoofState args=[0] rc=0 before=0 why=cmd cancel=0"))
    }

    @Test
    fun `nhat ky - cong tu choi van de lai dong, kem anh chup giai thich vi sao`() {
        val lines = mutableListOf<String>()
        val gw = FakeHalGateway(namedRc = 0L, getters = mapOf("getMoonRoofConfig" to "0"))
        val clock = ManualScheduler()
        HalBindingTable(gw, clock) { lines += it }.write("sunroof", 1)
        clock.advance(0)
        assertEquals(
            "ctl #1 id=sunroof m=named:setMoonRoofState args=[100] rc=- before=- why=cmd cancel=0 release=- gate=absent:0",
            lines[0],
        )
        assertTrue(lines[1].startsWith("ctl-probe id=sunroof getMoonRoofConfig=0 "), "ảnh chụp hẹn TRƯỚC cổng: $lines")
    }

    @Test
    fun `may ao khong co HAL - dung MOT dong ctl, khong hen nha, khong doc sau`() {
        val lines = mutableListOf<String>()
        val clock = ManualScheduler()
        HalBindingTable(FakeHalGateway(), clock) { lines += it }.write("sunroof", 1)
        clock.advance(10_000)
        assertEquals(1, lines.count { it.startsWith("ctl #") }, "V-SR: đúng một dòng ctl mỗi lệnh")
        assertTrue(lines.any { it.startsWith("ctl #1 ") && it.contains(" rc=- ") && it.contains(" release=-") })
        assertTrue(lines.none { it.startsWith(CtlWriteJournal.TAG_AFTER) })
    }

    @Test
    fun `khong co nguoi nghe nhat ky thi so luot HAL y nhu 2_85`() {
        // Một lệnh nút đọc được (kính) không nhật ký ⇒ 0 lượt getter (không đọc trước/sau, không ảnh chụp).
        val gw = CountingGateway(FakeHalGateway(namedRc = 0L))
        HalBindingTable(gw).write("win_lf", 1)
        assertTrue(gw.getterCalls.isEmpty(), "không người nghe ⇒ không đọc thêm: ${gw.getterCalls}")
    }

    @Test
    fun `luot doc chan doan nem thi lenh van di, dong ghi ten loi`() {
        val lines = mutableListOf<String>()
        val throwing = object : HalGateway by FakeHalGateway(namedRc = 0L) {
            override fun getter(deviceFqn: String, method: String, arg: Int?): String? =
                if (method == "getWindowOpenPercent") throw IllegalStateException("reflection") else null
        }
        assertEquals(0L, HalBindingTable(throwing, ManualScheduler()) { lines += it }.write("win_lf", 1))
        assertTrue(lines.any { it.contains(" before=!IllegalStateException ") }, "không nuốt im: $lines")
    }
}
