package com.byd.clusternav.navigation

import com.byd.clusternav.navigation.VietMapBubbleWait.Outcome
import com.byd.clusternav.navigation.VietMapBubbleWait.Step
import com.byd.clusternav.navigation.VietMapBubbleWait.Tick
import com.byd.clusternav.navigation.VietMapBubbleWait.Top
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.89 · B2 — khoá luật chờ bóng VietMap. Lỗi xe khoá ở đây (owner 05/10): *"có lúc mạng chậm nó đứng ở đó, hạ xuống thì
 * bóng không lên"* — 2.88 hết 25 s là hạ nền dù VietMap còn ở màn chờ. Mỗi bài dựng một chuỗi nhịp như máy thật (500 ms).
 */
class VietMapBubbleWaitTest {

    private val vm = "vn.vietmap.live"
    private val main = Top(vm, ".MainActivity")
    private val kachi = Top("com.byd.launcher", "com.byd.clusternav.launcher.KachiHome")

    private fun fixture(name: String): String {
        val root = System.getProperty("clusternav.root") ?: error("clusternav.root chưa set — xem core/build.gradle.kts")
        return File(root, "docs/diagnostics/vm-prereq-emulator-2026-10-05/$name").readText()
    }

    /** Chạy luật trên chuỗi nhịp [ticks] (nhịp i ở thời điểm (i+1)·500 ms); nhịp cuối lặp lại tới khi có kết luận. */
    private fun run(ticks: List<Tick>): Pair<Outcome, Long> {
        var state = VietMapBubbleWait.State()
        var i = 0
        while (true) {
            val t = (i + 1) * VietMapBubbleWait.POLL_INTERVAL_MS
            val tick = ticks[minOf(i, ticks.lastIndex)]
            when (val step = VietMapBubbleWait.next(state, tick, nowMs = 1_000_000 + t, elapsedMs = t, pkg = vm)) {
                is Step.Wait -> state = step.state
                is Step.Done -> return step.outcome to t
            }
            i++
            check(t <= 2 * VietMapBubbleWait.TIMEOUT_MS) { "luật không bao giờ kết luận" }
        }
    }

    // ── Dòng TOP-RESUMED ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `doc dung dong TONG, khong lay dong cua tung stack hay tung man`() {
        // [ĐO máy ảo 05/10] Kachi resumed ở display 0 VÀ VietMap ở màn ảo 3; dòng tổng = bên có tiêu điểm (VietMap).
        val grep = fixture("12-resumed-grep.txt")
        assertEquals(Top(vm, ".MainActivity"), VietMapBubbleWait.topResumed(grep))
        // Bỏ dòng tổng ⇒ không suy từ dòng stack/màn (dòng đó không nói ai ở trước) ⇒ CHƯA BIẾT.
        val noGlobal = grep.lines().filterNot { it.startsWith("  ResumedActivity: ") }.joinToString("\n")
        assertNull(VietMapBubbleWait.topResumed(noGlobal))
        assertNull(VietMapBubbleWait.topResumed(null))
        assertNull(VietMapBubbleWait.topResumed(""))
    }

    @Test
    fun `dong tong la app khac - tra dung goi`() {
        val line = "  ResumedActivity: ActivityRecord{5b5d6ea u0 com.byd.launcher/com.byd.clusternav.launcher.KachiHome t4881}"
        assertEquals(kachi, VietMapBubbleWait.topResumed(line))
    }

    // ── Luật ──────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `mang nhanh - MainActivity + service bong giu du 2,5 s thi HA NEN`() {
        val ticks = List(3) { Tick(kachi, false) } + Tick(main, false) + List(10) { Tick(main, true) }
        val (o, t) = run(ticks)
        assertEquals(Outcome.READY, o)
        assertTrue(o.background)
        assertTrue(t < 10_000, "đường nhanh vẫn thoát sớm: $t ms")
    }

    @Test
    fun `mang cham - con o man cho qua 25 s (moc 2_88) VAN CHO, toi 60 s thi KHONG ha`() {
        // Màn chờ nằm TRONG MainActivity [ĐO manifest] ⇒ chỉ thiếu service bóng.
        val ticks = List(5) { Tick(kachi, false) } + List(200) { Tick(main, false) }
        val (o, t) = run(ticks)
        assertEquals(Outcome.STILL_SPLASH, o)
        assertFalse(o.background, "hạ nền lúc còn ở màn chờ = đúng bệnh owner tả")
        assertEquals(VietMapBubbleWait.TIMEOUT_MS, t)
        assertTrue(t > 25_000)
    }

    @Test
    fun `mang cham nhung qua man cho o giay 40 - van HA NEN dung luc`() {
        val ticks = List(80) { Tick(main, false) } + List(20) { Tick(main, true) }
        val (o, t) = run(ticks)
        assertEquals(Outcome.READY, o)
        assertTrue(t in 40_000L..46_000L, "$t")
    }

    @Test
    fun `nguoi dung chuyen app - dung ngay, khong gui HOME`() {
        val ticks = List(4) { Tick(main, false) } + Tick(Top("com.google.android.youtube", ".Main"), false)
        val (o, t) = run(ticks)
        assertEquals(Outcome.USER_LEFT, o)
        assertFalse(o.background)
        assertEquals(2_500L, t)
    }

    @Test
    fun `chua thay VietMap len thi app khac o tren KHONG phai nguoi dung roi (lenh mo dang toi)`() {
        val ticks = List(3) { Tick(kachi, false) } + List(10) { Tick(main, true) }
        assertEquals(Outcome.READY, run(ticks).first)
    }

    @Test
    fun `doc hong giua chung - khong ket luan nguoi dung roi, giu activity cuoi`() {
        val ticks = List(3) { Tick(main, false) } + List(3) { Tick(null, false) } + List(10) { Tick(main, true) }
        assertEquals(Outcome.READY, run(ticks).first)
    }

    @Test
    fun `service bong mat giua chung - dem lai 2,5 s`() {
        val ticks = List(3) { Tick(main, true) } + Tick(main, false) + List(4) { Tick(main, true) } + List(10) { Tick(main, true) }
        val (o, t) = run(ticks)
        assertEquals(Outcome.READY, o)
        // Không đếm lại thì đã READY ở 3 000 ms (500 + 2 500); đếm lại từ nhịp 2 500 ms ⇒ 5 000 ms.
        assertEquals(5_000L, t, "phải đếm lại sau nhịp mất service")
    }

    @Test
    fun `VietMap khong bao gio len tren cung - het han, KHONG ha gi`() {
        val (o, t) = run(listOf(Tick(kachi, false)))
        assertEquals(Outcome.NEVER_FOREGROUND, o)
        assertFalse(o.background)
        assertEquals(VietMapBubbleWait.TIMEOUT_MS, t)
    }

    @Test
    fun `activity khac cua VietMap (vd dang nhap) + service - chua phai san sang`() {
        val ticks = List(200) { Tick(Top(vm, "io.flutter.plugins.urllauncher.WebViewActivity"), true) }
        assertEquals(Outcome.STILL_SPLASH, run(ticks).first)
    }

    // ── Review 2.89 Pass 2 · vietmap-dock-r1-1 — quyết theo DISPLAY 0 ─────────────────────────────────────────────────────

    /** Dump theo màn: VietMap trên display 0, tiêu điểm (dòng tổng) ở màn [focusVd] với [focusComp] — dạng fixture 13. */
    private fun perDisplay(focusVd: Int, focusComp: String, d0: String = "vn.vietmap.live/.MainActivity") = """
        Display #$focusVd (activities from top to bottom):
            mResumedActivity: ActivityRecord{aaa1111 u0 $focusComp t9001}
         ResumedActivity:ActivityRecord{aaa1111 u0 $focusComp t9001}
        Display #0 (activities from top to bottom):
            mResumedActivity: ActivityRecord{bbb2222 u0 $d0 t9002}
         ResumedActivity:ActivityRecord{bbb2222 u0 $d0 t9002}
          ResumedActivity: ActivityRecord{aaa1111 u0 $focusComp t9001}
    """.trimIndent()

    @Test
    fun `fixture 13 - dong tong la VietMap o man ao 3, display 0 la Kachi - doc dung display 0`() {
        // [ĐO máy ảo 05/10] dòng tổng = bên GIỮ TIÊU ĐIỂM (VietMap ở ô Kachi), không phải thứ trước mặt người lái.
        val dump = fixture("13-resumed-per-display.txt")
        assertEquals(main, VietMapBubbleWait.topResumed(dump), "tiền đề: dòng tổng nói VietMap")
        assertEquals(kachi, VietMapBubbleWait.topOnDefaultDisplay(dump))
    }

    @Test
    fun `tieu diem sang man khac trong luc cho (o 7, ClusterBlack len cum) - KHONG phai nguoi dung roi`() {
        val black = "com.byd.launcher/com.byd.clusternav.modules.clustercast.ClusterBlackActivity"
        val dump = perDisplay(4, black)
        assertEquals("com.byd.launcher", VietMapBubbleWait.topResumed(dump)?.pkg, "tiền đề: dòng tổng đã rời VietMap")
        val top = VietMapBubbleWait.topOnDefaultDisplay(dump)
        assertEquals(main, top)
        // Chuỗi nhịp như máy thật: VietMap lên, tiêu điểm sang cụm giữa chừng, service bóng chạy ⇒ vẫn READY (không USER_LEFT).
        val ticks = List(3) { Tick(main, false) } + List(10) { Tick(top, true) }
        assertEquals(Outcome.READY, run(ticks).first)
    }

    @Test
    fun `A12 - dong Resumed theo vung trong khoi Display 0 (r34 RootWindowContainer 3652)`() {
        val dump = """
            Display #0 (activities from top to bottom):
              Resumed activities in task display areas (from top to bottom):
                Resumed: ActivityRecord{ccc3333 u0 vn.vietmap.live/.MainActivity t12}
            Display #7 (activities from top to bottom):
                Resumed: ActivityRecord{ddd4444 u0 com.google.android.youtube/.Main t13}
              ResumedActivity: ActivityRecord{ddd4444 u0 com.google.android.youtube/.Main t13}
        """.trimIndent()
        assertEquals(main, VietMapBubbleWait.topOnDefaultDisplay(dump))
    }

    @Test
    fun `khong doc duoc khoi display 0 - roi ve dong tong, tru khi dong tong o man khac`() {
        val globalOnly = "  ResumedActivity: ActivityRecord{e694363 u0 vn.vietmap.live/.MainActivity t2038}"
        assertEquals(main, VietMapBubbleWait.topOnDefaultDisplay(globalOnly), "định dạng lạ ⇒ hành vi cũ")
        val elsewhere = """
            Display #3 (activities from top to bottom):
                mResumedActivity: ActivityRecord{fff5555 u0 com.google.android.youtube/.Main t20}
            Display #0 (activities from top to bottom):
              ResumedActivity: ActivityRecord{fff5555 u0 com.google.android.youtube/.Main t20}
        """.trimIndent()
        assertNull(VietMapBubbleWait.topOnDefaultDisplay(elsewhere), "dòng tổng ở màn khác ⇒ CHƯA BIẾT, không bao giờ 'đã rời'")
        assertNull(VietMapBubbleWait.topOnDefaultDisplay(null))
        assertNull(VietMapBubbleWait.topOnDefaultDisplay(""))
    }

    @Test
    fun `lenh doc - tap tren cua lenh cu, co tieu de Display va dong A12`() {
        val cmd = VietMapBubbleWait.PER_DISPLAY_GREP
        assertTrue(cmd.startsWith("dumpsys activity activities | grep -E '"), cmd)
        listOf("^Display #", "ResumedActivity", "Resumed: ").forEach { assertTrue(it in cmd, "thiếu '$it': $cmd") }
    }

    // ── Review 2.89 Pass 2 · whole-r1-1 — chuyến lên xe còn chờ màn nhà ───────────────────────────────────────────────────

    @Test
    fun `STILL_SPLASH chi ha khi la luot no may VA chuyen len xe con cho`() {
        assertTrue(VietMapBubbleWait.backgroundAfter(Outcome.STILL_SPLASH, bootPath = true, tripPending = true))
        assertFalse(VietMapBubbleWait.backgroundAfter(Outcome.STILL_SPLASH, bootPath = true, tripPending = false), "B2 giữ nguyên")
        assertFalse(VietMapBubbleWait.backgroundAfter(Outcome.STILL_SPLASH, bootPath = false, tripPending = true), "mở app ⇒ không")
        assertTrue(VietMapBubbleWait.backgroundAfter(Outcome.READY, bootPath = false, tripPending = false))
        assertFalse(VietMapBubbleWait.backgroundAfter(Outcome.USER_LEFT, bootPath = true, tripPending = true), "không giật người dùng")
        assertFalse(VietMapBubbleWait.backgroundAfter(Outcome.NEVER_FOREGROUND, bootPath = true, tripPending = true))
    }

    @Test
    fun `hang so - tran 60 s, nhip 500 ms, doc lai sau 3 s`() {
        assertEquals(60_000L, VietMapBubbleWait.TIMEOUT_MS)
        assertEquals(500L, VietMapBubbleWait.POLL_INTERVAL_MS)
        assertEquals(2_500L, VietMapBubbleWait.SETTLE_MS)
        assertEquals(3_000L, VietMapBubbleWait.RECHECK_AFTER_MS)
        // Chỉ READY mới hạ nền.
        assertEquals(listOf(Outcome.READY), Outcome.entries.filter { it.background })
    }

    // ── Review 2.89 Pass 3 · vietmap-dock-r2-1 — hộp "IVI không hỗ trợ" lúc nổ máy khi miễn pin chưa chứng minh ─────────────────

    /** Khối display 0 khi hộp CarSetting đè VietMap (dòng theo màn A10 — một dấu cách, không cách sau `:`; r47 `RootActivityContainer.java:2385-2386`). */
    private val dialogOnTop = """
        Display #0 (activities from top to bottom):
            mResumedActivity: ActivityRecord{a1b2c3d u0 com.byd.carsettings/com.byd.systemsettings.unsupport.UnsupportActivity t41}
         ResumedActivity:ActivityRecord{a1b2c3d u0 com.byd.carsettings/com.byd.systemsettings.unsupport.UnsupportActivity t41}
          ResumedActivity: ActivityRecord{a1b2c3d u0 com.byd.carsettings/com.byd.systemsettings.unsupport.UnsupportActivity t41}
    """.trimIndent()

    /**
     * Miễn pin chưa chứng minh ⇒ hộp đè display 0 là chuyện ĐÃ BIẾT: lượt nổ máy + chuyến còn chờ ⇒ USER_LEFT / NEVER_FOREGROUND cũng
     * về HOME (HOME đóng luôn hộp — `UnsupportActivity.onStop` › `finish`). Miễn pin CÓ ⇒ như cũ (không giật người dùng). Thử ĐỎ: trả
     * `mayGoHome` về `outcome == STILL_SPLASH`.
     */
    @Test
    fun `Pass 3 - hop IVI de display 0 luc no may - ve HOME khi mien pin chua chung minh`() {
        val dialog = VietMapBubbleWait.topOnDefaultDisplay(dialogOnTop)
        assertEquals(Top("com.byd.carsettings", "com.byd.systemsettings.unsupport.UnsupportActivity"), dialog)
        val left = run(List(2) { Tick(main, false) } + listOf(Tick(dialog, false))).first
        assertEquals(Outcome.USER_LEFT, left)
        assertTrue(VietMapBubbleWait.backgroundAfter(left, bootPath = true, tripPending = true, dialogExpected = true))
        assertFalse(VietMapBubbleWait.backgroundAfter(left, bootPath = true, tripPending = true, dialogExpected = false), "miễn pin CÓ ⇒ như cũ")
        assertFalse(VietMapBubbleWait.backgroundAfter(left, bootPath = false, tripPending = true, dialogExpected = true), "mở app ⇒ không")
        assertFalse(VietMapBubbleWait.backgroundAfter(left, bootPath = true, tripPending = false, dialogExpected = true), "chuyến không chờ ⇒ không")
        val never = run(listOf(Tick(dialog, false))).first             // hộp đã lên ở nhịp đầu ⇒ chưa từng thấy VietMap
        assertEquals(Outcome.NEVER_FOREGROUND, never)
        assertTrue(VietMapBubbleWait.backgroundAfter(never, bootPath = true, tripPending = true, dialogExpected = true))
        assertTrue(VietMapBubbleWait.mayGoHome(Outcome.STILL_SPLASH, dialogExpected = false), "whole-r1-1 giữ nguyên")
        assertFalse(VietMapBubbleWait.mayGoHome(Outcome.READY, dialogExpected = true), "READY tự hạ — không qua cổng chuyến")
    }

    // ── Review 2.89 Pass 3 · vietmap-dock-r2-3 — service bóng đã chạy TRƯỚC lượt mở ─────────────────────────────────────────────

    /**
     * Dạng `dumpsys activity services <gói>` theo NGUỒN (chưa có dump xe — 🚗 chụp ở R3-V3): `  * ServiceRecord{… u0 gói/.Lớp}`
     * (r47 `ActiveServices.java:4102-4108`, `ServiceRecord.java:962-971`) rồi các dòng thụt 4 của `ServiceRecord.dump`
     * (`:403-412` — `createTime=` · `lastActivity=` theo `TimeUtils.formatDuration`).
     */
    private fun services(bubbleAgo: String, otherAgo: String = "-120ms") = """
        ACTIVITY MANAGER SERVICES (dumpsys activity services)
          User 0 active services:
          * ServiceRecord{9d1c0a2 u0 vn.vietmap.live/com.google.firebase.sessions.SessionLifecycleService}
            intent={cmp=vn.vietmap.live/com.google.firebase.sessions.SessionLifecycleService}
            createTime=-6m1s4ms startingBgTimeout=--
            lastActivity=$otherAgo restartTime=-6m1s4ms createdFromFg=true
          * ServiceRecord{3c2a1b5 u0 vn.vietmap.live/.VMBluetoothService}
            intent={cmp=vn.vietmap.live/.VMBluetoothService}
            isForeground=true foregroundId=1001 foregroundNoti=Notification(channel=vm)
            createTime=-5m30s123ms startingBgTimeout=--
            lastActivity=$bubbleAgo restartTime=-5m30s123ms createdFromFg=true
    """.trimIndent()

    @Test
    fun `Pass 3 - doc lastActivity dung record, dung dinh dang TimeUtils`() {
        val svc = "VMBluetoothService"
        assertEquals(2_345L, VietMapBubbleWait.serviceLastActivityAgoMs(services("-2s345ms"), svc), "không lấy nhầm record firebase")
        assertEquals(330_123L, VietMapBubbleWait.serviceLastActivityAgoMs(services("-5m30s123ms"), svc))
        assertEquals(3_723_004L, VietMapBubbleWait.serviceLastActivityAgoMs(services("-1h2m3s4ms"), svc))
        assertEquals(90_000_007L, VietMapBubbleWait.serviceLastActivityAgoMs(services("-1d1h0m0s7ms"), svc))
        assertEquals(0L, VietMapBubbleWait.serviceLastActivityAgoMs(services("0"), svc))
        assertEquals(45L, VietMapBubbleWait.serviceLastActivityAgoMs(services("-45ms"), svc))
        assertNull(VietMapBubbleWait.serviceLastActivityAgoMs(services("--"), svc), "chưa đặt")
        assertNull(VietMapBubbleWait.serviceLastActivityAgoMs(services("+3s0ms"), svc), "tương lai ⇒ không tin")
        assertNull(VietMapBubbleWait.serviceLastActivityAgoMs("  * ServiceRecord{1 u0 vn.vietmap.live/.VMBluetoothService}", svc))
        assertNull(VietMapBubbleWait.serviceLastActivityAgoMs(null, svc))
    }

    /**
     * Back khỏi VietMap (FGS còn sống) rồi bật bóng ⇒ service CÓ trong dump trước khi Dart của lượt mới gọi gì: chỉ nhận khi
     * `lastActivity` mới hơn lượt mở. Thử ĐỎ: bỏ nhánh `runningBeforeLaunch == false` (đường nhanh cũ chết) hoặc bỏ so mốc.
     */
    @Test
    fun `Pass 3 - service da chay truoc luot mo chi la bang chung khi lastActivity moi hon luot mo`() {
        assertTrue(VietMapBubbleWait.freshBubble(true, runningBeforeLaunch = false, lastActivityAgoMs = null, sinceLaunchMs = 3_000), "đường cũ")
        assertFalse(VietMapBubbleWait.freshBubble(true, runningBeforeLaunch = true, lastActivityAgoMs = 330_123, sinceLaunchMs = 2_500),
            "service cũ, Dart mới chưa gọi ⇒ còn màn chờ")
        assertTrue(VietMapBubbleWait.freshBubble(true, runningBeforeLaunch = true, lastActivityAgoMs = 900, sinceLaunchMs = 4_000),
            "Dart gọi startVMBluetoothService ở lượt này ⇒ mốc mới")
        assertFalse(VietMapBubbleWait.freshBubble(true, runningBeforeLaunch = null, lastActivityAgoMs = null, sinceLaunchMs = 4_000),
            "đọc trước hỏng + không có mốc ⇒ không nhận")
        assertFalse(VietMapBubbleWait.freshBubble(false, runningBeforeLaunch = false, lastActivityAgoMs = 1, sinceLaunchMs = 4_000))
        // Chuỗi nhịp như máy thật: bóng "có mặt" từ đầu nhưng mốc cũ ⇒ không READY tới khi mốc mới xuất hiện.
        val stale = Tick(main, VietMapBubbleWait.freshBubble(true, true, 330_123, 2_500))
        val fresh = Tick(main, VietMapBubbleWait.freshBubble(true, true, 200, 9_000))
        val (outcome, at) = run(List(16) { stale } + List(10) { fresh })
        assertEquals(Outcome.READY, outcome)
        assertTrue(at > 8_000, "không READY trên màn chờ ở 2,5 s (bệnh owner 05/10): $at")
    }
}
