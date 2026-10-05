package com.byd.clusternav.system

import com.byd.clusternav.system.AppPrereqPlan.Marks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Review 2.89 Pass 2 · vietmap-dock-r1-2 — ĐƯỜNG TRẢ LẠI của điều kiện nền (CLAUDE.md §5: state đổi ngoài hệ thống sống qua
 * reboot ⇒ phải có đường trả chạy được cả khi tiến trình đã chết).
 *
 * Lỗi khoá ở đây [SUY đọc mã]: B2 ghi `cmd deviceidle whitelist +<gói>` / `appops set <gói> SYSTEM_ALERT_WINDOW allow` cho gói BẤT
 * KỲ của hồ sơ (app tự chiếu — khoá hồ sơ xuất/nhập được) mà không ghi lại gói nào do CHÍNH Kachi thêm ⇒ đổi app tự chiếu / tắt
 * bóng là gói cũ được miễn mãi. Máy giả dưới đây dùng ĐÚNG định dạng máy ảo (`docs/diagnostics/vm-prereq-emulator-2026-10-05/`):
 * `user,<gói>,<appId>` · `Added:`/`Removed:` · `SYSTEM_ALERT_WINDOW: <mode>; time=…` · `No operations.` + `Default mode: default`.
 */
class AppPrereqRevertTest {

    private val vm = "vn.vietmap.live"
    private val yt = "com.google.android.youtube"

    /** Máy giả nhiều gói: danh sách miễn pin `user` + appop theo gói; mọi lệnh (và mọi lượt ghi dấu) vào [log] theo thứ tự. */
    private class Device(val installed: MutableSet<String>) {
        val exempt = LinkedHashSet<String>()
        val overlay = HashMap<String, String>()
        val log = mutableListOf<String>()
        var failReads = false

        fun sh(cmd: String): String? {
            log += cmd
            val read = cmd == DozeWhitelistRead.READ || cmd.startsWith("appops get")
            if (failReads && read) return null
            return when {
                cmd == DozeWhitelistRead.READ -> exempt.joinToString("") { "user,$it,10146\n" }
                cmd.startsWith("${DozeWhitelistRead.READ} +") -> cmd.substringAfter('+').let { p ->
                    if (p in installed) { exempt += p; "Added: $p" } else "Unknown package: $p"
                }
                cmd.startsWith("${DozeWhitelistRead.READ} -") -> cmd.substringAfter(" -").let { p -> exempt -= p; "Removed: $p" }
                cmd.startsWith("appops get ") -> when (val m = overlay[cmd.split(' ')[2]] ?: "default") {
                    "default" -> "No operations.\nDefault mode: default"
                    else -> "SYSTEM_ALERT_WINDOW: $m; time=+1m ago"
                }
                cmd.startsWith("appops set ") -> cmd.split(' ').let { overlay[it[2]] = it[4]; "" }
                else -> error("lệnh ngoài phạm vi: $cmd")
            }
        }
    }

    /** Dấu trong bộ nhớ; mọi lượt ghi dấu cũng vào [log] để kiểm "dấu TRƯỚC lệnh ghi". */
    private class MemMarks(private val log: MutableList<String>, var writable: Boolean = true) : Marks {
        val sets = HashMap<Prereq, MutableSet<String>>()
        override fun added(p: Prereq): Set<String> = sets[p].orEmpty().toSet()
        override fun mark(p: Prereq, pkg: String): Boolean {
            log += "mark ${p.name} $pkg"
            if (!writable) return false
            sets.getOrPut(p) { LinkedHashSet() } += pkg
            return true
        }
        override fun unmark(p: Prereq, pkg: String): Boolean {
            log += "unmark ${p.name} $pkg"
            sets[p]?.remove(pkg)
            return true
        }
    }

    private fun facts(dev: Device, bubble: Boolean = false, autoCast: List<String> = emptyList()) =
        AppPrereqPlan.Facts(vm, vm in dev.installed, bubble, badgeOn = false, vietMapCastDefault = false, autoCastPkgs = autoCast) {
            it in dev.installed
        }

    @Test
    fun `chua mien - ghi DAU truoc roi moi ghi dieu kien`() {
        val dev = Device(mutableSetOf(vm, yt))
        val marks = MemMarks(dev.log)
        val t = AppPrereqPlan.targets(facts(dev, autoCast = listOf(yt))).single { it.pkg == yt }
        val r = AppPrereqPlan.ensure(t, dev::sh, marks = marks)
        assertTrue(r.ok, r.describe())
        val mark = dev.log.indexOf("mark DOZE_EXEMPT $yt")
        val add = dev.log.indexOf("${DozeWhitelistRead.READ} +$yt")
        assertTrue(mark in 0 until add, "dấu phải ghi TRƯỚC lệnh +gói: ${dev.log}")
        assertEquals(setOf(yt), marks.added(Prereq.DOZE_EXEMPT))
    }

    @Test
    fun `da mien tu truoc - KHONG danh dau (khong bao gio bi tra)`() {
        val dev = Device(mutableSetOf(vm, yt)).apply { exempt += yt }
        val marks = MemMarks(dev.log)
        val t = AppPrereqPlan.targets(facts(dev, autoCast = listOf(yt))).single { it.pkg == yt }
        AppPrereqPlan.ensure(t, dev::sh, marks = marks)
        assertTrue(marks.added(Prereq.DOZE_EXEMPT).isEmpty(), "gói đã miễn trước khi Kachi chạm vào không có dấu")
        assertFalse(dev.log.any { it.startsWith("mark") })
    }

    @Test
    fun `khong ghi duoc dau - KHONG ghi dieu kien (khong co duong tra thi khong doi)`() {
        val dev = Device(mutableSetOf(vm, yt))
        val marks = MemMarks(dev.log, writable = false)
        val t = AppPrereqPlan.targets(facts(dev, autoCast = listOf(yt))).single { it.pkg == yt }
        val r = AppPrereqPlan.ensure(t, dev::sh, marks = marks)
        assertFalse(dev.log.any { it.contains(" +") }, "không lệnh ghi nào: ${dev.log}")
        assertEquals(emptyList<Prereq>(), r.applied)
        assertFalse(r.ok)
    }

    @Test
    fun `app tu chieu doi sang app khac - goi cu duoc TRA mien pin, doc lai xac nhan roi moi bo dau`() {
        val dev = Device(mutableSetOf(vm, yt))
        val marks = MemMarks(dev.log)
        AppPrereqPlan.ensureAll(AppPrereqPlan.targets(facts(dev, autoCast = listOf(yt))), dev::sh, marks)
        assertTrue(yt in dev.exempt)
        // Người lái bỏ YouTube khỏi tự chiếu ⇒ lượt sẵn sàng kế.
        val f = facts(dev)
        assertEquals(listOf(Prereq.DOZE_EXEMPT to yt), AppPrereqPlan.stale(f, marks))
        val mark = dev.log.size
        val r = AppPrereqPlan.revertStale(f, marks, dev::sh).single()
        assertEquals(listOf("${DozeWhitelistRead.READ} -$yt", DozeWhitelistRead.READ, "unmark DOZE_EXEMPT $yt"), dev.log.drop(mark))
        assertTrue(r.cleared && r.after == Truth.NO, r.describe())
        assertFalse(yt in dev.exempt)
        assertTrue(marks.added(Prereq.DOZE_EXEMPT).isEmpty())
    }

    @Test
    fun `tat bong - tra quyen ve noi cua VietMap, GIU mien pin (luot autostart luon can no)`() {
        val dev = Device(mutableSetOf(vm))
        val marks = MemMarks(dev.log)
        AppPrereqPlan.ensureAll(AppPrereqPlan.targets(facts(dev, bubble = true)), dev::sh, marks)
        assertEquals("allow", dev.overlay[vm])
        assertEquals(setOf(vm), marks.added(Prereq.OVERLAY))
        val f = facts(dev, bubble = false)
        assertEquals(listOf(Prereq.OVERLAY to vm), AppPrereqPlan.stale(f, marks), "miễn pin của VietMap KHÔNG rời phạm vi")
        val r = AppPrereqPlan.revertStale(f, marks, dev::sh).single()
        assertTrue(dev.log.contains("appops set $vm SYSTEM_ALERT_WINDOW default"), dev.log.toString())
        assertTrue(r.cleared, r.describe())
        assertEquals("default", dev.overlay[vm])
        assertTrue(vm in dev.exempt, "không lật miễn pin qua lại mỗi lần nổ máy")
    }

    @Test
    fun `doc lai hong sau lenh tra - GIU dau, luot sau lam lai`() {
        val dev = Device(mutableSetOf(vm, yt))
        val marks = MemMarks(dev.log)
        AppPrereqPlan.ensureAll(AppPrereqPlan.targets(facts(dev, autoCast = listOf(yt))), dev::sh, marks)
        dev.failReads = true
        val r = AppPrereqPlan.revertStale(facts(dev), marks, dev::sh).single()
        assertEquals(Truth.UNKNOWN, r.after)
        assertFalse(r.cleared)
        assertEquals(setOf(yt), marks.added(Prereq.DOZE_EXEMPT))
    }

    @Test
    fun `goi da go - bo dau, 0 lenh (ROM tu xoa khi go app)`() {
        val dev = Device(mutableSetOf(vm, yt))
        val marks = MemMarks(dev.log)
        AppPrereqPlan.ensureAll(AppPrereqPlan.targets(facts(dev, autoCast = listOf(yt))), dev::sh, marks)
        dev.installed -= yt
        val mark = dev.log.size
        val r = AppPrereqPlan.revertStale(facts(dev, autoCast = listOf(yt)), marks, dev::sh).single()
        assertTrue(r.notInstalled && r.cleared, r.describe())
        assertEquals(listOf("unmark DOZE_EXEMPT $yt"), dev.log.drop(mark), "không lệnh shell nào cho gói đã gỡ")
    }

    @Test
    fun `dau con trong pham vi - khong tra gi, ten goi la trong dau - bo dau, khong bao gio vao shell`() {
        val dev = Device(mutableSetOf(vm, yt))
        val marks = MemMarks(dev.log)
        AppPrereqPlan.ensureAll(AppPrereqPlan.targets(facts(dev, autoCast = listOf(yt))), dev::sh, marks)
        assertTrue(AppPrereqPlan.stale(facts(dev, autoCast = listOf(yt)), marks).isEmpty())
        marks.sets.getOrPut(Prereq.DOZE_EXEMPT) { LinkedHashSet() } += "x; reboot"
        val mark = dev.log.size
        val r = AppPrereqPlan.revertStale(facts(dev, autoCast = listOf(yt)), marks, dev::sh).single()
        assertEquals("x; reboot", r.pkg)
        assertEquals(listOf("unmark DOZE_EXEMPT x; reboot"), dev.log.drop(mark))
    }

    @Test
    fun `khoa dau va lenh tra - dung mot lenh, dung mot goi`() {
        assertEquals("prereq_added_doze", AppPrereqPlan.markKey(Prereq.DOZE_EXEMPT))
        assertEquals("prereq_added_overlay", AppPrereqPlan.markKey(Prereq.OVERLAY))
        assertEquals("cmd deviceidle whitelist -$vm", AppPrereqPlan.revertCommand(Prereq.DOZE_EXEMPT, vm))
        assertEquals("appops set $vm SYSTEM_ALERT_WINDOW default", AppPrereqPlan.revertCommand(Prereq.OVERLAY, vm))
    }

    /**
     * Review 2.89 Pass 3 · vietmap-dock-r2-2 — hai hồ sơ: A bật bóng, B tắt. Lượt SẴN dưới B TRẢ appop vẽ nổi (dấu của Kachi), đổi sang
     * A thì lượt kế (nay chạy ngay ở lượt đổi hồ sơ — `reapplyAll` › `AppPrereqs.onReady`) CẤP LẠI và đánh dấu lại. Thử ĐỎ: bỏ
     * OVERLAY khỏi `Role.BUBBLE`.
     */
    @Test
    fun `Pass 3 - ho so B tat bong tra ve noi, doi sang A bat bong cap lai o luot ke`() {
        val dev = Device(mutableSetOf(vm))
        val marks = MemMarks(dev.log)
        val a = facts(dev, bubble = true)
        val b = facts(dev, bubble = false)
        val first = AppPrereqPlan.ensureAll(AppPrereqPlan.targets(a), dev::sh, marks).single()
        assertTrue(first.ok, first.describe())
        assertEquals("allow", dev.overlay[vm])
        // Lượt SẴN dưới hồ sơ B: appop vẽ nổi rời phạm vi ⇒ trả, đọc lại xác nhận rồi mới bỏ dấu.
        val reverted = AppPrereqPlan.revertStale(b, marks, dev::sh)
        assertEquals(listOf(Prereq.OVERLAY to vm), reverted.map { it.prereq to it.pkg })
        assertEquals("default", dev.overlay[vm])
        assertFalse(vm in marks.added(Prereq.OVERLAY))
        // Đổi sang A: lượt kế cấp lại (thiếu ⇒ ghi dấu TRƯỚC rồi ghi điều kiện), không còn gì để trả.
        assertTrue(AppPrereqPlan.stale(a, marks).isEmpty())
        val again = AppPrereqPlan.ensureAll(AppPrereqPlan.targets(a), dev::sh, marks).single()
        assertTrue(again.ok && Prereq.OVERLAY in again.applied, again.describe())
        assertEquals("allow", dev.overlay[vm])
        assertTrue(vm in marks.added(Prereq.OVERLAY))
    }
}
