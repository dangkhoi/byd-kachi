package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · R6-D — khoá lời hứa của [ShellLogGate] (docs/diagnostics/perf-inventory-2026-10-08.md §4 D):
 *  1. chỉ lệnh trong danh sách CHO PHÉP chỉ-đọc được tiết chế — lệnh đổi trạng thái (bằng chứng pháp y) luôn log đủ;
 *  2. kết quả ĐỔI ⇒ ghi ngay; 3. HỎNG ⇒ ghi mọi lượt; 4. nhịp sống 10 phút; 5. số lượt đã lược đi kèm dòng ghi;
 *  6. bộ nhớ có trần mà không biến thành dòng mất.
 */
class ShellLogGateTest {

    private val list = "Stack id=1 bounds=[0,0][1920,720] displayId=0\n  taskId=5: com.byd.launcher/.Home visible=true"

    @Test
    fun `danh sach cho phep chi-doc, lenh doi trang thai khong qua cong`() {
        listOf(
            "am stack list", "dumpsys display", "dumpsys accessibility", "appops get vn.vietmap.live PICTURE_IN_PICTURE",
            "settings get secure enabled_accessibility_services", "getprop persist.sys.car.type",
        ).forEach { assertTrue(ShellLogGate.isReadOnly(it), it) }
        listOf(
            "am start --display 2 -n a.b/.C", "am force-stop a.b", "am task resize 5 0 0 10 10", "wm size 1920x720 -d 2",
            "wm overscan 0,0,0,0 -d 2", "wm density reset -d 2", "settings put global x 1", "service call SurfaceFlinger 1008",
            "appops set a.b PICTURE_IN_PICTURE allow", "setprop a b", "cmd package resolve-activity a.b",
            "am stack listx", "dumpsysx display", "",
            // nối lệnh: không còn là MỘT lệnh chỉ-đọc
            "am stack list; am force-stop a.b", "dumpsys display && wm size reset", "dumpsys display | grep x",
            "settings get secure x > /data/local/tmp/y", "getprop \$(rm -rf x)",
        ).forEach { assertFalse(ShellLogGate.isReadOnly(it), "'$it' phải log đầy đủ") }
    }

    /**
     * Soát Fable 2.98 Pass 1 [P2] — `dumpsys` không phải một họ chỉ-đọc: dịch vụ nhận lệnh con GHI qua `dump()` phải log đầy đủ dù
     * Kachi hôm nay chưa gọi (cổng không được có khả năng nuốt một lệnh đổi trạng thái — CLAUDE.md §4 danh sách CHO PHÉP).
     */
    @Test
    fun `dumpsys chi-doc theo danh sach dich vu, lenh con ghi khong qua cong`() {
        listOf(
            "dumpsys display", "dumpsys window displays", "dumpsys window windows", "dumpsys window -d 2",
            "dumpsys activity", "dumpsys activity activities", "dumpsys activity services vn.vietmap.live", "dumpsys activity -a",
            "dumpsys accessibility", "dumpsys package com.byd.launcher", "dumpsys notification --noredact", "dumpsys appwidget",
            "dumpsys meminfo com.byd.launcher", "dumpsys input", "dumpsys power", "dumpsys alarm", "dumpsys role", "dumpsys dropbox --print",
        ).forEach { assertTrue(ShellLogGate.isReadOnly(it), it) }
        listOf(
            "dumpsys window tracing start", "dumpsys window tracing stop", "dumpsys deviceidle whitelist +a.b", "dumpsys deviceidle",
            "dumpsys battery unplug", "dumpsys battery set level 5", "dumpsys gfxinfo a.b reset", "dumpsys batterystats --reset",
            "dumpsys car_service enable-feature x", "dumpsys car_service", "dumpsys SurfaceFlinger --latency-clear", "dumpsys SurfaceFlinger",
            "dumpsys activity service a.b/.S reset", "dumpsys activity service a.b/.S", "dumpsys procstats --reset", "dumpsys magicwindow",
            "dumpsys", "dumpsys ",
        ).forEach { assertFalse(ShellLogGate.isReadOnly(it), "'$it' phải log đầy đủ") }
        assertTrue("activities" in ShellLogGate.DUMPSYS_ACTIVITY_PAGES && "service" !in ShellLogGate.DUMPSYS_ACTIVITY_PAGES)
        listOf("deviceidle", "battery", "batterystats", "gfxinfo", "car_service", "procstats").forEach {
            assertFalse(it in ShellLogGate.DUMPSYS_SERVICES, "$it có lệnh con ghi — không được vào danh sách")
        }
    }

    /**
     * Soát Fable 2.98 Pass 2 — ba lối lách của Pass 1 đã đóng:
     *  1. tuỳ chọn đứng TRƯỚC lệnh con (`dumpsys activity -a write`) từng lọt vì chỉ nhìn token thứ hai;
     *  2. `dumpsys package write` (PMS ghi `packages.xml`) từng được coi là chỉ-đọc;
     *  3. nháy / escape / glob (`'write'`, `wri\te`, `wr*`) qua mặt so token nguyên văn — shell bóc nháy trước khi dịch vụ nhận.
     * Kèm: khoảng trắng thừa / tab / tuỳ chọn hợp lệ vẫn chỉ-đọc, và tiền tố neo ĐẦU chuỗi (không khớp giữa câu).
     */
    @Test
    fun `tuy chon truoc lenh con, package write, nhay-escape-glob deu log day du`() {
        listOf(
            "dumpsys activity -a write", "dumpsys activity --proto track-associations", "dumpsys activity -p a.b untrack-associations",
            "dumpsys activity write", "dumpsys package write", "dumpsys package -f write", "dumpsys package 'write'",
            "dumpsys package \"write\"", "dumpsys package wri\\te", "dumpsys package wr*", "dumpsys package wr?te", "dumpsys package {write,x}",
            "dumpsys package [w]rite", "dumpsys window -a tracing start", "dumpsys activity -a service a.b/.S reset",
            // neo đầu chuỗi: lệnh chỉ-đọc nằm GIỮA câu không làm cả câu thành chỉ-đọc
            "echo dumpsys display", "sh -c dumpsys display", "cmd dumpsys display", "x am stack list",
            // tab giữa `dumpsys` và dịch vụ: tiền tố đòi đúng một dấu cách ⇒ không khớp ⇒ log đầy đủ (bảo thủ, không sai)
            "dumpsys\tdisplay",
        ).forEach { assertFalse(ShellLogGate.isReadOnly(it), "'$it' phải log đầy đủ") }
        listOf(
            "  dumpsys display  ", "dumpsys  display", "dumpsys display --proto", "dumpsys activity -a activities",
            "dumpsys activity --proto", "dumpsys package com.byd.launcher", "dumpsys package -f com.byd.launcher",
            "dumpsys window -d 2 displays", "am stack list ", " settings get secure x",
        ).forEach { assertTrue(ShellLogGate.isReadOnly(it), "'$it' là chỉ-đọc, được tiết chế") }
        assertTrue(ShellLogGate.DUMPSYS_WRITE_TOKENS.containsAll(setOf("write", "track-associations", "untrack-associations", "tracing")))
    }

    @Test
    fun `ket qua giong het bi luoc, ket qua DOI ghi ngay kem so luot da luoc`() {
        val g = ShellLogGate()
        assertTrue(g.decide("am stack list", 0, list, "", 0).log, "lần đầu luôn ghi")
        repeat(10) { i -> assertFalse(g.decide("am stack list", 0, list, "", 4_000L * (i + 1)).log) }
        val d = g.decide("am stack list", 0, "$list\n  taskId=9: vn.vietmap.live/.Main", "", 44_001)
        assertTrue(d.log && d.changed)
        assertEquals(10, d.suppressed)
        assertEquals(" [+10 lượt giống hệt đã lược]", ShellLogGate.suffix(d))
    }

    @Test
    fun `HONG thi ghi moi luot, khong bao gio nuot dong loi`() {
        val g = ShellLogGate()
        g.decide("am stack list", 0, list, "", 0)
        repeat(5) { i ->
            val d = g.decide("am stack list", 1, "", "Error: binder died", 1_000L + i)
            assertTrue(d.log, "lượt hỏng #$i phải ghi")
        }
    }

    @Test
    fun `nhip song 10 phut, dong ho lui thi ghi`() {
        val g = ShellLogGate()
        g.decide("dumpsys display", 0, "x", "", 1_000)
        assertFalse(g.decide("dumpsys display", 0, "x", "", 1_000 + ShellLogGate.HEARTBEAT_MS - 1).log)
        val beat = g.decide("dumpsys display", 0, "x", "", 1_000 + ShellLogGate.HEARTBEAT_MS)
        assertTrue(beat.log && !beat.changed)
        assertEquals(" [không đổi] [+1 lượt giống hệt đã lược]", ShellLogGate.suffix(beat))
        assertTrue(g.decide("dumpsys display", 0, "x", "", 10).log, "giờ lùi ⇒ ghi, không khoá vĩnh viễn")
    }

    @Test
    fun `tran LRU day khoa ra thi lan sau GHI`() {
        val g = ShellLogGate(maxKeys = 2)
        g.decide("dumpsys a", 0, "x", "", 0)
        g.decide("dumpsys b", 0, "x", "", 0)
        g.decide("dumpsys c", 0, "x", "", 0)   // đẩy "dumpsys a"
        assertTrue(g.decide("dumpsys a", 0, "x", "", 1).log)
    }

    /** Ca thật log xe 26/09: am stack list mỗi 4 s suốt 37 phút, stack không đổi ⇒ còn ≤ 1 dòng ghi mỗi 10 phút. */
    @Test
    fun `37 phut do 4 s khong doi chi con nhip song`() {
        val g = ShellLogGate()
        var logged = 0
        var t = 0L
        while (t < 37L * 60_000L) { if (g.decide("am stack list", 0, list, "", t).log) logged++; t += 4_000 }
        assertEquals(4, logged, "lần đầu + 3 nhịp sống (10/20/30 phút) thay vì 555 lượt × 2 dòng")
    }

    @Test
    fun `van tay phan biet ma thoat, stdout, stderr`() {
        val a = ShellLogGate.fingerprint(0, "x", "")
        assertFalse(a == ShellLogGate.fingerprint(1, "x", ""))
        assertFalse(a == ShellLogGate.fingerprint(0, "", "x"))
        assertEquals(a, ShellLogGate.fingerprint(0, "x", ""))
    }
}
