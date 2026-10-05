package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.modules.clustercast.CastDisplayFixtures2026_09_15
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CLUSTER-THEME-SAFE (2.89, P0) — bộ THI HÀNH cổng theme với một kênh shell kịch bản: khoá CHUỖI LỆNH thật (đọc → gỡ →
 * đọc lại → quyết) và việc KHÔNG có lệnh ghi nào khi phải bỏ theme. Cụm = display 2 (fixture nguyên văn 15/09).
 *
 * B1a (ĐỔI GHIM có lý do): mặc định mọi hồ sơ `themeOnVacantVd = false` ⇒ có màn ảo cụm là `VD_PRESENT`, 0 lệnh đọc thêm
 * (bài `B1a - …`). Các bài mức B (gỡ ClusterBlack, FOREIGN, đọc lại…) chạy với cờ BẬT ([guard]) — luật giữ cho ngày V4b xanh.
 */
class ClusterThemeGuardTest {

    private val self = CastDisplayFixtures2026_09_15.LAUNCHER_PKG
    private val black = "$self/com.byd.clusternav.modules.clustercast.ClusterBlackActivity"

    private val home = """
        Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0
         configuration={1.0 winConfig={ mWindowingMode=fullscreen mDisplayWindowingMode=fullscreen mActivityType=home} s.38}
          taskId=4: com.byd.launcher/com.byd.clusternav.launcher.KachiHome bounds=[0,0][1920,1080] userId=0 visible=true
    """.trimIndent()

    private fun onCluster(stackId: Int, taskId: Int, comp: String) = """
        Stack id=$stackId bounds=[0,0][1920,720] displayId=2 userId=0
         configuration={1.0 winConfig={ mWindowingMode=freeform mDisplayWindowingMode=fullscreen mActivityType=standard} s.3}
          taskId=$taskId: $comp bounds=[0,0][1920,720] userId=0 visible=true
    """.trimIndent()

    /** Kênh shell kịch bản: bản đọc theo lệnh; `am stack remove <id>` gỡ đúng khối stack đó khỏi bản đọc (nếu [removeWorks]). */
    private inner class Script(var stacks: String, var detect: String? = CastDisplayFixtures2026_09_15.DETECT_OUT_WITH_SLOT) :
        SimpleCastShell {
        val calls = mutableListOf<String>()
        var removeWorks = true
        var windowsFail = false

        override fun execute(command: String): ShellResult {
            calls += command
            return when {
                command == ClusterDisplayResolver.DETECT_CMD ->
                    detect?.let { ShellResult(0, it, "") } ?: ShellResult(1, "", "dumpsys lỗi")
                command == ClusterThemeGuard.STACK_CMD -> ShellResult(0, stacks, "")
                command == ClusterThemeGuard.WINDOWS_CMD ->
                    if (windowsFail) ShellResult(1, "", "lỗi") else ShellResult(0, windowsOf(stacks), "")
                command.startsWith("am stack remove ") -> {
                    val id = command.removePrefix("am stack remove ").trim()
                    if (removeWorks) stacks = dropStack(stacks, id)
                    ShellResult(0, "", "")
                }
                else -> ShellResult(0, "", "")
            }
        }

        private fun dropStack(out: String, id: String): String {
            val keep = ArrayList<String>()
            var skipping = false
            for (line in out.lines()) {
                if (line.trimStart().startsWith("Stack id=")) skipping = line.trimStart().startsWith("Stack id=$id ")
                if (!skipping) keep += line
            }
            return keep.joinToString("\n")
        }

        /** Một cửa sổ mỗi task, cùng display (dạng `Window #N Window{… u0 comp}:` + `mDisplayId=` thật). */
        private fun windowsOf(out: String): String =
            com.byd.clusternav.modules.clustercast.StackParse.parse(out).withIndex().joinToString("\n") { (i, e) ->
                "  Window #$i Window{a$i u0 ${e.comp}}:\n    mDisplayId=${e.displayId} stackId=${e.stackId} mSession=Session{0 0:u0a1}"
            }
    }

    /** Mức B (cờ `themeOnVacantVd` BẬT) — luật A1 đầy đủ. */
    private fun guard(sh: Script, store: ThemeLedger.Store = ThemeLedger.InMemory()) =
        ClusterThemeGuard(sh, self, sleepMs = {}, store = store, vacantVdAllowed = { true })

    /** Mặc định B1a (cờ TẮT). */
    private fun guardB1a(sh: Script, store: ThemeLedger.Store = ThemeLedger.InMemory()) =
        ClusterThemeGuard(sh, self, sleepMs = {}, store = store)
    private fun writes(sh: Script) = sh.calls.filter { it.startsWith("am stack remove") }

    @Test
    fun `man ao cum trong - cho gui, chi lenh DOC`() {
        val sh = Script(home)
        assertEquals(ThemeVerdict.SEND, guard(sh).admit(30))
        assertEquals(listOf(ClusterDisplayResolver.DETECT_CMD, ClusterThemeGuard.STACK_CMD, ClusterThemeGuard.WINDOWS_CMD), sh.calls)
    }

    @Test
    fun `chi ClusterBlack - go dung stack cua no, doc lai thay trong, roi moi cho gui`() {
        val sh = Script(home + "\n" + onCluster(57, 58, black))
        assertEquals(ThemeVerdict.SEND, guard(sh).admit(30))
        assertEquals(listOf("am stack remove 57"), writes(sh))
        val removeAt = sh.calls.indexOf("am stack remove 57")
        assertTrue(sh.calls.drop(removeAt + 1).contains(ClusterThemeGuard.STACK_CMD), "phải ĐỌC LẠI sau lệnh gỡ: ${sh.calls}")
    }

    @Test
    fun `go xong ma doc lai van thay ClusterBlack - KHONG gui`() {
        val sh = Script(home + "\n" + onCluster(57, 58, black)).apply { removeWorks = false }
        // Màn ảo cụm (display 2) có TỪ TRƯỚC ⇒ bỏ theme, lượt mở đi tiếp (safety-1: không dừng khi theme cụm đã biết).
        assertEquals(ThemeVerdict.SKIP_KNOWN, guard(sh).admit(30))
        assertEquals(listOf("am stack remove 57"), writes(sh), "chỉ gỡ đúng một lần, không vòng gỡ lại")
        assertEquals(1 + ClusterThemeGuard.SETTLE_READS, sh.calls.count { it == ClusterThemeGuard.STACK_CMD }, "đọc lại đủ trần rồi thôi")
    }

    @Test
    fun `app la tren man ao cum - BO theme, KHONG mot lenh ghi nao (ke ca khong go ClusterBlack)`() {
        val sh = Script(home + "\n" + onCluster(57, 58, black) + "\n" +
            onCluster(60, 61, "com.google.android.apps.maps/com.google.android.maps.MapsActivity"))
        val g = guard(sh)
        assertEquals(ThemeVerdict.SKIP_KNOWN, g.admit(30))
        assertEquals(emptyList<String>(), writes(sh))
        assertTrue(g.lastVerdict!!.contains("FOREIGN"), g.lastVerdict)
    }

    /** Sổ không giữ gì — bài dấu RAM thuần (khoảng 15 s của sổ khoá riêng ở bài `B1a - TOO_SOON`). */
    private val noLedger = object : ThemeLedger.Store {
        override fun read(): String? = null
        override fun write(value: String): Boolean = true
    }

    @Test
    fun `da gui cung theme, man ao khong doi - BO lan sau, khong lenh ghi`() {
        val sh = Script(home)
        val g = guard(sh, noLedger)
        g.beginOpen()
        assertEquals(ThemeVerdict.SEND, g.admit(30)); g.sent(30); g.bindVd(2)
        sh.stacks = home + "\n" + onCluster(57, 58, black)      // ClusterBlack nằm lại sau tắt chiếu
        sh.calls.clear()
        assertEquals(ThemeVerdict.SKIP_KNOWN, g.admit(30), "đã gửi 30, cụm vẫn là display 2 ⇒ không gửi lại")
        assertEquals(emptyList<String>(), writes(sh), "bỏ theme thì không gỡ ClusterBlack")
        assertEquals(ThemeVerdict.SEND, g.admit(31), "theme KHÁC ⇒ xét lại bằng bản đọc (gỡ ClusterBlack rồi gửi)")
    }

    @Test
    fun `luot mo hong truoc khi bindVd - dau khong duoc chot`() {
        val sh = Script(home)
        val g = guard(sh, noLedger)
        g.beginOpen(); g.sent(30)          // gửi xong nhưng lượt mở hỏng (không dò ra màn ảo)
        g.beginOpen()                      // lượt mở sau
        g.bindVd(2)
        assertEquals(ThemeVerdict.SEND, g.admit(30), "không có dấu đã chốt ⇒ xét bằng bản đọc (trống ⇒ gửi)")
    }

    @Test
    fun `doc hong (stack, cua so, display) - DUNG luot mo, khong doan`() {
        // Review 2.89 Pass 1 · safety-1 — ĐỔI GHIM có lý do: trước là `false` (= bỏ theme, vẫn 16/35). Đọc hỏng ở lượt mở đầu
        // sau nổ máy ⇒ cụm ở theme GỐC ⇒ 16/35 chiếu trong theme gốc (Seal 10.25": mất km/h [ĐO 05/10]) ⇒ nay DỪNG.
        assertEquals(ThemeVerdict.ABORT, guard(Script("")).admit(30), "am stack list rỗng = đọc hỏng")
        assertEquals(ThemeVerdict.ABORT, guard(Script(home).apply { windowsFail = true }).admit(30))
        assertEquals(ThemeVerdict.ABORT, guard(Script(home, detect = null)).admit(30))
        assertEquals(ThemeVerdict.ABORT, guard(Script(home, detect = "  khong co tieu de display nao")).admit(30), "thiếu 'Display 0:' = bản đọc hỏng")
    }

    @Test
    fun `tat chieu - go ClusterBlack cua Kachi khoi man ao cum, khong dung app khac`() {
        val maps = "com.google.android.apps.maps/com.google.android.maps.MapsActivity"
        val sh = Script(home + "\n" + onCluster(57, 58, black) + "\n" + onCluster(60, 61, maps))
        assertEquals(1, guard(sh).removePlaceholder("test"))
        assertEquals(listOf("am stack remove 57"), writes(sh))
        val none = Script(home)
        assertEquals(0, guard(none).removePlaceholder("test"))
        assertEquals(emptyList<String>(), writes(none))
    }

    @Test
    fun `man ao cua chinh Kachi (o kachi-slot) khong bao gio la cum - ClusterBlack lac o do khong bi go`() {
        // display 1 = kachi-slot-0 (fixture 15/09); dời placeholder sang display 1 ⇒ ngoài tập cụm ⇒ không đụng.
        val onSlot = onCluster(57, 58, black).replace("displayId=2", "displayId=1")
        val sh = Script(home + "\n" + onSlot)
        assertEquals(0, guard(sh).removePlaceholder("test"))
        assertEquals(ThemeVerdict.SEND, guard(sh).admit(30), "cụm (display 2) trống ⇒ gửi; màn ảo ô của Kachi không tính")
        assertEquals(emptyList<String>(), writes(sh))
    }

    // ── Review 2.89 Pass 1 · safety-5 — ClusterBlack của CHÍNH tiến trình gỡ trong tiến trình (không `am stack remove`) ──────

    /** Cổng "trong tiến trình" giả: nhận [owned] task id; gỡ ⇒ xoá stack khỏi bản đọc của kịch bản (như finishAndRemoveTask). */
    private fun own(sh: Script, owned: Set<Int>, stackOf: Map<Int, Int>, asked: MutableList<Set<Int>>) =
        ClusterThemeGuard.OwnPlaceholder { ids ->
            asked += ids
            val got = ids.intersect(owned)
            got.forEach { t -> stackOf[t]?.let { st -> sh.stacks = sh.stacks.lines().let { ls -> dropBlock(ls, st) } } }
            got
        }

    private fun dropBlock(lines: List<String>, stackId: Int): String {
        val keep = ArrayList<String>()
        var skipping = false
        for (line in lines) {
            if (line.trimStart().startsWith("Stack id=")) skipping = line.trimStart().startsWith("Stack id=$stackId ")
            if (!skipping) keep += line
        }
        return keep.joinToString("\n")
    }

    @Test
    fun `ClusterBlack cua tien trinh dang song - go TRONG tien trinh, 0 lenh am stack remove`() {
        val sh = Script(home + "\n" + onCluster(57, 58, black))
        val asked = mutableListOf<Set<Int>>()
        val g = ClusterThemeGuard(sh, self, own(sh, setOf(58), mapOf(58 to 57), asked), sleepMs = {}, vacantVdAllowed = { true })
        assertEquals(ThemeVerdict.SEND, g.admit(30))
        assertEquals(listOf(setOf(58)), asked, "hỏi đúng task của stack đã qua rào")
        assertEquals(emptyList<String>(), writes(sh), "không `am stack remove` (killProcess = true giết `:tts`)")
        assertTrue(sh.calls.count { it == ClusterThemeGuard.STACK_CMD } >= 2, "vẫn đọc lại sau lượt gỡ: ${sh.calls}")
    }

    @Test
    fun `ClusterBlack mo coi (khong the hien song) - van am stack remove nhu cu`() {
        val sh = Script(home + "\n" + onCluster(57, 58, black))
        val asked = mutableListOf<Set<Int>>()
        val g = ClusterThemeGuard(sh, self, own(sh, emptySet(), emptyMap(), asked), sleepMs = {}, vacantVdAllowed = { true })
        assertEquals(ThemeVerdict.SEND, g.admit(30))
        assertEquals(listOf(setOf(58)), asked)
        assertEquals(listOf("am stack remove 57"), writes(sh))
    }

    @Test
    fun `tat chieu - go placeholder trong tien trinh, khong lenh ghi shell`() {
        val maps = "com.google.android.apps.maps/com.google.android.maps.MapsActivity"
        val sh = Script(home + "\n" + onCluster(57, 58, black) + "\n" + onCluster(60, 61, maps))
        val asked = mutableListOf<Set<Int>>()
        val g = ClusterThemeGuard(sh, self, own(sh, setOf(58), mapOf(58 to 57), asked), sleepMs = {}, vacantVdAllowed = { true })
        assertEquals(1, g.removePlaceholder("test"))
        assertEquals(listOf(setOf(58)), asked, "chỉ hỏi task của ClusterBlack, không bao giờ task của app khác")
        assertEquals(emptyList<String>(), writes(sh))
    }

    /**
     * B1a — ĐỔI GHIM có lý do (trước: đọc cửa sổ hỏng ⇒ DỪNG): chưa có màn ảo cụm thì không lớp nào nằm trên nó ⇒ stack/cửa
     * sổ không được đọc (luật không dùng) ⇒ một lượt đọc cửa sổ hỏng không còn chặn được lần mở đầu sau nổ máy [ĐO F2].
     */
    @Test
    fun `chua co man ao cum (lan mo dau sau no may) - GUI, chi doc display`() {
        for (g in listOf(guard(Script(home, detect = CastDisplayFixtures2026_09_15.DETECT_OUT_SLOT_ONLY).apply { windowsFail = true }),
            guardB1a(Script(home, detect = CastDisplayFixtures2026_09_15.DETECT_OUT_SLOT_ONLY)))) {
            assertEquals(ThemeVerdict.SEND, g.admit(30))
        }
        val sh = Script(home, detect = CastDisplayFixtures2026_09_15.DETECT_OUT_SLOT_ONLY)
        guardB1a(sh).admit(30)
        assertEquals(listOf(ClusterDisplayResolver.DETECT_CMD), sh.calls)
    }

    // ── B1a · cờ themeOnVacantVd TẮT (mặc định mọi hồ sơ) ─────────────────────────────────────────────────────────────

    @Test
    fun `B1a - man ao cum co ma TRONG - VD_PRESENT, chi doc display, 0 lenh ghi`() {
        val sh = Script(home)
        val g = guardB1a(sh)
        assertEquals(ThemeVerdict.SKIP_KNOWN, g.admit(30))
        assertEquals(listOf(ClusterDisplayResolver.DETECT_CMD), sh.calls, "cờ tắt ⇒ không đọc stack/cửa sổ")
        assertTrue(g.lastVerdict!!.contains("VD_PRESENT"), g.lastVerdict)
    }

    @Test
    fun `B1a - man ao cum chi con ClusterBlack - KHONG go (go khong con mo khoa theme)`() {
        val sh = Script(home + "\n" + onCluster(57, 58, black))
        assertEquals(ThemeVerdict.SKIP_KNOWN, guardB1a(sh).admit(30))
        assertEquals(emptyList<String>(), writes(sh))
    }

    @Test
    fun `B1a - doc display hong - ABORT (UNREADABLE)`() {
        val g = guardB1a(Script(home, detect = null))
        assertEquals(ThemeVerdict.ABORT, g.admit(30))
        assertTrue(g.lastVerdict!!.contains("UNREADABLE"), g.lastVerdict)
    }

    @Test
    fun `B1a - TOO_SOON - lan doi theme truoc chua du 15 s - chua co man ao thi ABORT, khong ngu cho`() {
        val clock = ThemeLedger.Clock { ThemeLedger.Now(elapsedMs = 100_000, boot = 7, processStartMs = 50_000) }
        val store = ThemeLedger.InMemory(ThemeLedger.encode(ThemeLedger.Entry(31, ThemeLedger.State.OK, 97_000, 7)))
        val sh = Script(home, detect = CastDisplayFixtures2026_09_15.DETECT_OUT_SLOT_ONLY)
        var slept = 0L
        val g = ClusterThemeGuard(sh, self, sleepMs = { slept += it }, store = store, clock = clock)
        assertEquals(ThemeVerdict.ABORT, g.admit(30))
        assertTrue(g.lastVerdict!!.contains("TOO_SOON"), g.lastVerdict)
        assertEquals(0L, slept, "không ngủ chờ — executor có hạn cứng")
    }

    @Test
    fun `B1a - so theme - sending ghi pending, sent doi DUNG muc do thanh ok (giu moc gio gui)`() {
        val store = ThemeLedger.InMemory()
        val g = guardB1a(Script(home), store)
        g.sending(30)
        val pending = ThemeLedger.decode(store.read())!!
        assertEquals(ThemeLedger.State.PENDING, pending.state)
        g.sent(30)
        val ok = ThemeLedger.decode(store.read())!!
        assertEquals(pending.copy(state = ThemeLedger.State.OK), ok)
        assertEquals(ok, g.ledger())
    }

    /** Review 2.89 Pass 2 · cluster-r1-5 — ghi `pending` hỏng ⇒ `sending` trả `false` (bên gọi KHÔNG gửi), sổ giữ nguyên. */
    @Test
    fun `Pass 2 - so khong ghi duoc - sending tra false, so giu nguyen muc cu`() {
        val old = ThemeLedger.encode(ThemeLedger.Entry(31, ThemeLedger.State.OK, 5, 7))
        val broken = object : ThemeLedger.Store {
            override fun read(): String? = old
            override fun write(value: String): Boolean = false
        }
        val g = guardB1a(Script(home), broken)
        assertEquals(false, g.sending(30))
        assertEquals(ThemeLedger.decode(old), g.ledger(), "sổ không đổi")
        assertEquals(null, g.remainingGapMs(), "không gửi gì ⇒ không có mốc RAM mới")
    }

    /**
     * Review 2.89 Pass 2 · cluster-r1-5 — khoảng 15 s giữ được TRONG tiến trình kể cả khi sổ đọc lại hỏng sau một lần gửi: mốc
     * RAM ([ClusterThemeGuard.remainingGapMs] lấy mốc muộn hơn). Thử ĐỎ: bỏ `lastSend` khỏi `remainingGapMs`.
     */
    @Test
    fun `Pass 2 - so doc hong sau khi gui - moc RAM van giu khoang 15 s (TOO_SOON)`() {
        var t = 100_000L
        val clock = ThemeLedger.Clock { ThemeLedger.Now(elapsedMs = t, boot = 7, processStartMs = 50_000) }
        val forgetful = object : ThemeLedger.Store {
            override fun read(): String? = null            // đọc hỏng / bị xoá sau khi gửi
            override fun write(value: String): Boolean = true
        }
        val sh = Script(home, detect = CastDisplayFixtures2026_09_15.DETECT_OUT_SLOT_ONLY)
        val g = ClusterThemeGuard(sh, self, sleepMs = {}, store = forgetful, clock = clock)
        assertEquals(true, g.sending(30))
        g.sent(30)
        t += 5_000
        assertEquals(10_000L, g.remainingGapMs())
        assertEquals(ThemeVerdict.ABORT, g.admit(30), "chưa có màn ảo + TOO_SOON ⇒ ABORT")
        assertTrue(g.lastVerdict!!.contains("TOO_SOON"), g.lastVerdict)
        t += 10_000
        assertEquals(null, g.remainingGapMs(), "đủ 15 s ⇒ được")
        assertEquals(ThemeVerdict.SEND, g.admit(30))
    }

    @Test
    fun `inspect - muc B chi ClusterBlack - bao RemovePlaceholder nhung KHONG go, KHONG ghi so`() {
        val sh = Script(home + "\n" + onCluster(57, 58, black))
        val store = ThemeLedger.InMemory()
        val asked = mutableListOf<Set<Int>>()
        val g = ClusterThemeGuard(sh, self, own(sh, setOf(58), mapOf(58 to 57), asked), sleepMs = {}, store = store, vacantVdAllowed = { true })
        val i = g.inspect(30)
        assertEquals(ClusterThemePlan.Decision.RemovePlaceholder(listOf(57)), i.decision)
        assertEquals(emptyList<String>(), writes(sh))
        assertEquals(emptyList<Set<Int>>(), asked, "không gỡ cả trong tiến trình")
        assertEquals(null, store.read())
        assertEquals(null, g.lastVerdict, "inspect không đổi lượt quyết của đường chạy")
    }
}
