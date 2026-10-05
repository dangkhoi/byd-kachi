package com.byd.clusternav.system

import com.byd.clusternav.system.AppPrereqPlan.Role
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.89 · B2 VM-PREREQ-TRUTH — khoá PHẠM VI (bảng lý do → điều kiện, không `if gói`) và lượt "đọc → áp phần thiếu → đọc
 * lại" thay cho hai cờ một-lần. Vỏ shell giả trả đúng đầu ra máy ảo (`docs/diagnostics/vm-prereq-emulator-2026-10-05/`)
 * và đổi trạng thái khi nhận lệnh ghi — như máy thật.
 */
class AppPrereqPlanTest {

    private val vm = "vn.vietmap.live"
    private val aa = "com.byd.androidauto"

    private fun fixture(name: String): String {
        val root = System.getProperty("clusternav.root") ?: error("clusternav.root chưa set — xem core/build.gradle.kts")
        return File(root, "docs/diagnostics/vm-prereq-emulator-2026-10-05/$name").readText()
    }

    private fun facts(
        bubble: Boolean = false,
        badge: Boolean = false,
        castDefault: Boolean = false,
        autoCast: List<String> = emptyList(),
        installed: Set<String> = setOf(vm, aa),
    ) = AppPrereqPlan.Facts(vm, vm in installed, bubble, badge, castDefault, autoCast) { it in installed }

    /** Máy giả: danh sách miễn pin + appop SYSTEM_ALERT_WINDOW; ghi lại mọi lệnh theo thứ tự. */
    private class Device(var exempt: Boolean, var overlay: String, val installed: Set<String> = setOf("vn.vietmap.live")) {
        val log = mutableListOf<String>()
        var failReads = false

        fun sh(cmd: String): String? {
            log += cmd
            if (failReads && !cmd.contains('+') && !cmd.startsWith("appops set")) return null
            return when {
                cmd == DozeWhitelistRead.READ -> buildString {
                    appendLine("system-excidle,com.android.shell,2000")
                    appendLine("system,com.android.shell,2000")
                    if (exempt) appendLine("user,vn.vietmap.live,10146")
                }
                cmd.startsWith("${DozeWhitelistRead.READ} +") -> {
                    val pkg = cmd.substringAfter('+')
                    if (pkg in installed) { exempt = true; "Added: $pkg" } else "Unknown package: $pkg"
                }
                cmd.startsWith("appops get") -> "SYSTEM_ALERT_WINDOW: $overlay; time=+1m ago"
                cmd.startsWith("appops set") -> { overlay = cmd.substringAfterLast(' '); "" }
                else -> error("lệnh ngoài phạm vi: $cmd")
            }
        }
    }

    // ── Phạm vi ────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `bong bat - VietMap can mien pin VA ve noi`() {
        val t = AppPrereqPlan.targets(facts(bubble = true)).single()
        assertEquals(vm, t.pkg)
        assertEquals(setOf(Prereq.DOZE_EXEMPT, Prereq.OVERLAY), t.needs)
        assertEquals(setOf(Role.BUBBLE), t.roles)
    }

    @Test
    fun `chi bien toc do - chi mien pin, khong cap ve noi`() {
        assertEquals(setOf(Prereq.DOZE_EXEMPT), AppPrereqPlan.targets(facts(badge = true)).single().needs)
    }

    @Test
    fun `khong cong tac nao - moc san sang khong dung VietMap`() {
        assertTrue(AppPrereqPlan.targets(facts()).isEmpty())
    }

    @Test
    fun `luot VietMapAutostart - mien pin ca khi tat bong va bien (nguoi dung tu mo VietMap)`() {
        val t = AppPrereqPlan.targets(facts(), listOf(vm to Role.AUTOSTART_PASS)).single()
        assertEquals(setOf(Prereq.DOZE_EXEMPT), t.needs)
        assertEquals(setOf(Role.AUTOSTART_PASS), t.roles)
    }

    @Test
    fun `app tu chieu cua ho so vao pham vi - gop ly do theo goi`() {
        val ts = AppPrereqPlan.targets(facts(bubble = true, badge = true, autoCast = listOf(aa, vm)))
        assertEquals(listOf(vm, aa), ts.map { it.pkg })
        assertEquals(setOf(Role.BUBBLE, Role.SPEED_BADGE, Role.AUTO_CAST), ts[0].roles)
        assertEquals(setOf(Prereq.DOZE_EXEMPT), ts[1].needs)
    }

    @Test
    fun `goi chua cai hoac ten goi doc bi loai truoc khi thanh lenh`() {
        val ts = AppPrereqPlan.targets(
            facts(bubble = true, autoCast = listOf("com.example.gone", "x; reboot", " ")),
            listOf(vm to Role.CAST_OPEN),
        )
        assertEquals(listOf(vm), ts.map { it.pkg })
        assertTrue(AppPrereqPlan.targets(facts(bubble = true, installed = emptySet())).isEmpty(), "VietMap chưa cài ⇒ không gì")
    }

    // ── Đọc → áp phần thiếu → đọc lại ───────────────────────────────────────────────────────────────────────

    private fun target(vararg needs: Prereq) = AppPrereqPlan.Target(vm, needs.toSet(), setOf(Role.BUBBLE))

    @Test
    fun `da du - 0 lenh ghi`() {
        val d = Device(exempt = true, overlay = "allow")
        val r = AppPrereqPlan.ensure(target(Prereq.DOZE_EXEMPT, Prereq.OVERLAY), d::sh)
        assertTrue(r.ok)
        assertTrue(r.applied.isEmpty())
        assertEquals(listOf(DozeWhitelistRead.READ, "appops get $vm SYSTEM_ALERT_WINDOW"), d.log)
    }

    @Test
    fun `ca hien truong - cai lai VietMap mat mien pin - them dung mot lan roi doc lai`() {
        val d = Device(exempt = false, overlay = "allow")
        val r = AppPrereqPlan.ensure(target(Prereq.DOZE_EXEMPT), d::sh)
        assertEquals(listOf(DozeWhitelistRead.READ, "cmd deviceidle whitelist +$vm", DozeWhitelistRead.READ), d.log)
        assertEquals(listOf(Prereq.DOZE_EXEMPT), r.applied)
        assertEquals(DozeWhitelistRead.Entry.NONE, r.before.doze)
        assertEquals(DozeWhitelistRead.Entry.USER, r.after.doze)
        assertTrue(r.ok, r.describe())
    }

    @Test
    fun `ve noi bi reset (default) - cap allow roi doc lai`() {
        val d = Device(exempt = true, overlay = "default")
        val r = AppPrereqPlan.ensure(target(Prereq.DOZE_EXEMPT, Prereq.OVERLAY), d::sh)
        assertEquals(listOf(Prereq.OVERLAY), r.applied)
        assertTrue("appops set $vm SYSTEM_ALERT_WINDOW allow" in d.log)
        assertEquals("allow", r.after.overlayMode)
        assertTrue(r.ok)
    }

    @Test
    fun `doc hong - KHONG ghi gi (khong chung minh duoc thieu)`() {
        val d = Device(exempt = false, overlay = "default").apply { failReads = true }
        val r = AppPrereqPlan.ensure(target(Prereq.DOZE_EXEMPT, Prereq.OVERLAY), d::sh)
        assertTrue(r.applied.isEmpty())
        assertTrue(d.log.none { it.contains('+') || it.startsWith("appops set") }, d.log.toString())
        assertFalse(r.ok)
        assertEquals(listOf(Prereq.DOZE_EXEMPT, Prereq.OVERLAY), r.unmet)
    }

    @Test
    fun `lenh ghi duoc chap nhan ma doc lai van thieu - ket qua noi THIEU, khong tin lenh da gui`() {
        val d = object {
            val log = mutableListOf<String>()
            fun sh(cmd: String): String = cmd.also { log += it }.let {
                if (it == DozeWhitelistRead.READ) fixture("03-whitelist-vietmap-absent.txt") else fixture("04-add-vietmap.txt")
            }
        }
        val r = AppPrereqPlan.ensure(target(Prereq.DOZE_EXEMPT), d::sh)
        assertEquals(listOf(Prereq.DOZE_EXEMPT), r.applied)
        assertFalse(r.ok)
        assertTrue(r.describe().contains("THIẾU DOZE_EXEMPT"), r.describe())
    }

    @Test
    fun `goi chua cai - ROM tra Unknown package - ket qua noi dung`() {
        val gone = AppPrereqPlan.Target("com.example.kachi.notinstalled", setOf(Prereq.DOZE_EXEMPT), setOf(Role.AUTO_CAST))
        val d = Device(exempt = false, overlay = "default", installed = emptySet())
        val r = AppPrereqPlan.ensure(gone, d::sh)
        assertTrue(r.notInstalled)
        assertFalse(r.ok)
    }

    @Test
    fun `nhieu goi - mot lan doc danh sach cho luot truoc`() {
        val d = Device(exempt = true, overlay = "allow", installed = setOf(vm, aa))
        val rs = AppPrereqPlan.ensureAll(
            listOf(target(Prereq.DOZE_EXEMPT), AppPrereqPlan.Target(aa, setOf(Prereq.DOZE_EXEMPT), setOf(Role.AUTO_CAST))),
            d::sh,
        )
        assertEquals(2, rs.size)
        assertTrue(rs[0].ok)
        // aa vắng ⇒ thêm + đọc lại; lượt "trước" dùng chung MỘT lần đọc.
        assertEquals(
            listOf(DozeWhitelistRead.READ, "cmd deviceidle whitelist +$aa", DozeWhitelistRead.READ),
            d.log,
        )
    }

    @Test
    fun `quyet thuan - chi dieu kien NO moi ghi`() {
        val s = AppPrereqPlan.State(DozeWhitelistRead.Entry.EXCEPT_IDLE_ONLY, null, overlayRead = true)
        assertEquals(listOf(Prereq.DOZE_EXEMPT), AppPrereqPlan.missing(setOf(Prereq.DOZE_EXEMPT, Prereq.OVERLAY), s))
        val unknown = AppPrereqPlan.State(DozeWhitelistRead.Entry.UNKNOWN, "default", overlayRead = true)
        assertEquals(listOf(Prereq.OVERLAY), AppPrereqPlan.missing(setOf(Prereq.DOZE_EXEMPT, Prereq.OVERLAY), unknown))
    }
}
