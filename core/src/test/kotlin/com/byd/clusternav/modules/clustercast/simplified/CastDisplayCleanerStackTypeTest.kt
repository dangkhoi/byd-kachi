package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá lượt dọn VD cụm theo **LOẠI STACK** (CLAUDE.md §4 câu 3) — senior review bản gộp 2.84 (2026-09-30).
 *
 * Vì sao có bài này: V-CLUSTER thêm một chỗ gọi `cleanDisplay` KHÔNG người trông — `closeOrphanProjection`, chạy lúc tiến
 * trình dựng lại ([ĐO 29/09] BYD giết Kachi mỗi lần tắt máy, dựng lại ~0,3 s). Trước bản vá, `tasksToClean` chọn task chỉ
 * bằng TÊN GÓI (bỏ `com.android.*`/launcher3/systemui/CarPlay/placeholder), còn `findTargetStackOnDisplay0` lấy stack
 * đầu tiên id > 0 mà không nhìn loại. Hai lỗ, cả hai dựng được từ dump xe THẬT:
 *  1. Stack HOME của chính Kachi mang gói `com.byd.launcher` (không phải `com.android.*`) — [ĐO dump 29/09] stack 0
 *     `mActivityType=home` chứa bốn task `KachiHome`. Stack đó mà nằm trên VD thì bộ lọc gói cho nó qua ⇒ task home bị
 *     `am stack move-task` bê sang display 0 — đúng vùng cấm §4 (bài học đơ Dudu).
 *  2. Dump `camera-under-pip-derived`: stack PIP của Google Maps (id 54, `mWindowingMode=pinned`) đứng TRƯỚC mọi stack
 *     standard của display 0 ⇒ đích cũ = 54 ⇒ app vừa dọn khỏi cụm bị nhét vào cửa sổ PiP trên màn chính.
 *
 * Fixture: `core/src/test/resources/diagnostics/am-stack-list-oncar-2026-09-29-*.txt` NGUYÊN VĂN (KDoc
 * `ForceStopReturnHomeTest`). Ca "home trên VD" là bản DẪN XUẤT: chép nguyên khối stack 0 của dump thật, chỉ đổi
 * `Stack id=0`→`60` và `displayId=0`→`1` — [CHƯA BIẾT] VD cụm của BYD có bao giờ mang stack home không (VD
 * `FLAG_OWN_CONTENT_ONLY`, không cờ system decorations — nhiều khả năng KHÔNG), nên bài này khoá lằn ranh ở tầng thi hành
 * thay vì tin giả định đó (CLAUDE.md §5).
 */
class CastDisplayCleanerStackTypeTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/am-stack-list-oncar-2026-09-29-$name.txt")
            ?.bufferedReader()?.readText()
            ?: error("thiếu fixture core/src/test/resources/diagnostics/am-stack-list-oncar-2026-09-29-$name.txt")

    /** Khối stack home (id 0, display 0) của dump thật, dời sang VD [vd] với id 60 — xem KDoc lớp. */
    private fun homeBlockOn(vd: Int, dump: String): String {
        val lines = dump.lines()
        val start = lines.indexOfFirst { it.startsWith("Stack id=0 ") }
        check(start >= 0) { "dump không còn stack 0 — fixture đổi?" }
        val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("Stack id=") } ?: lines.size
        val block = lines.subList(start, end).joinToString("\n")
        check("mActivityType=home" in block) { "stack 0 của dump không còn là home — fixture đổi?" }
        return block.replaceFirst("Stack id=0 ", "Stack id=60 ").replaceFirst("displayId=0 ", "displayId=$vd ")
    }

    /** Một stack standard trên VD mang app THẬT cần dọn (dạng dòng của dump xe). */
    private fun vietmapOn(vd: Int) = """
        |Stack id=61 bounds=[0,0][1920,720] displayId=$vd userId=0
        | configuration={1.0 winConfig={ mBounds=Rect(0, 0 - 1920, 720) mWindowingMode=freeform mDisplayWindowingMode=freeform mActivityType=standard mAlwaysOnTop=undefined mRotation=ROTATION_0} s.1}
        |  taskId=70: vn.vietmap.live/vn.vietmap.live.MainActivity bounds=[0,0][1920,720] userId=0 visible=true topActivity=ComponentInfo{vn.vietmap.live/vn.vietmap.live.MainActivity}
        |""".trimMargin()

    @Test
    fun `task cua stack HOME tren VD khong bao gio bi chon de be, du goi khong phai com_android`() {
        val dump = fixture("fullscreen-app-top")
        val out = homeBlockOn(1, dump) + "\n" + vietmapOn(1) + "\n" + dump
        val picked = CastStackParser.tasksToClean(out, 1).map { it.taskId }
        assertEquals(listOf(70), picked, "chỉ app thường (VietMap) được bê; bốn task KachiHome (home) KHÔNG: $picked")
    }

    @Test
    fun `stack recents va assistant tren VD cung la vung cam`() {
        val out = """
            |Stack id=62 bounds=[0,0][1920,720] displayId=2 userId=0
            | configuration={1.0 winConfig={ mWindowingMode=fullscreen mDisplayWindowingMode=fullscreen mActivityType=recents mAlwaysOnTop=undefined} s.1}
            |  taskId=80: com.oem.recents/com.oem.recents.RecentsActivity bounds=[0,0][1920,720] userId=0 visible=true
            |Stack id=63 bounds=[0,0][1920,720] displayId=2 userId=0
            | configuration={1.0 winConfig={ mWindowingMode=fullscreen mDisplayWindowingMode=fullscreen mActivityType=assistant mAlwaysOnTop=undefined} s.1}
            |  taskId=81: com.oem.voice/com.oem.voice.Assist bounds=[0,0][1920,720] userId=0 visible=true
            |""".trimMargin() + "\n" + vietmapOn(2)
        assertEquals(listOf(70), CastStackParser.tasksToClean(out, 2).map { it.taskId })
    }

    /** Hành vi cũ GIỮ: task trong stack pinned (loại standard) vẫn được bê ở mức TASK — lý do [ĐO AOSP] ở KDoc hàm. */
    @Test
    fun `task cua stack pinned tren VD van duoc be o muc task nhu truoc`() {
        val out = """
            |Stack id=64 bounds=[0,0][640,360] displayId=1 userId=0
            | configuration={1.0 winConfig={ mWindowingMode=pinned mDisplayWindowingMode=freeform mActivityType=standard mAlwaysOnTop=on} s.1}
            |  taskId=90: com.google.android.apps.maps/com.google.android.maps.MapsActivity bounds=[0,0][640,360] userId=0 visible=true
            |""".trimMargin()
        assertEquals(listOf(90), CastStackParser.tasksToClean(out, 1).map { it.taskId })
    }

    @Test
    fun `dich tren display 0 bo qua stack pinned dung truoc (dump dan xuat camera-under-pip)`() {
        // Trước bản vá: 54 (PiP của Google Maps). Nay: 53 — stack standard fullscreen đầu tiên (camera lùi của dump).
        assertEquals(53, CastStackParser.findTargetStackOnDisplay0(fixture("camera-under-pip-derived")))
    }

    @Test
    fun `dich tren display 0 cua dump thuong khong doi (stack standard dau tien id lon hon 0)`() {
        assertEquals(53, CastStackParser.findTargetStackOnDisplay0(fixture("fullscreen-app-top")))
        // Home (stack 0) đứng TRƯỚC stack standard 26 trên display 0 ⇒ bỏ home, lấy 26.
        assertEquals(26, CastStackParser.findTargetStackOnDisplay0(fixture("stuck-home-top")))
    }

    @Test
    fun `display 0 chi con home thi khong co dich (khong bao gio tra stack home)`() {
        // Khối home của dump thật, đặt lại id = 5 (vẫn display 0): chỉ lọc "id > 0" như bản cũ sẽ nhận nhầm nó làm đích.
        val homeOnly = homeBlockOn(0, fixture("fullscreen-app-top")).replaceFirst("Stack id=60 ", "Stack id=5 ")
        assertEquals(null, CastStackParser.findTargetStackOnDisplay0(homeOnly))
    }

    /** Đường thi hành thật (`CastDisplayCleaner`, chỗ `closeOrphanProjection` gọi): chỉ MỘT lệnh bê, đúng task, đúng đích. */
    @Test
    fun `cleanDisplay chi be task standard vao stack standard, 0 lenh cho task home`() {
        val dump = fixture("camera-under-pip-derived")
        val out = homeBlockOn(1, dump) + "\n" + vietmapOn(1) + "\n" + dump
        val history = mutableListOf<String>()
        val shell = object : SimpleCastShell {
            override fun execute(command: String): ShellResult {
                history += command
                return ShellResult(0, if (command == "am stack list") out else "", "")
            }
        }
        CastDisplayCleaner.cleanDisplay(shell, 1, sleepMs = {})
        val moves = history.filter { it.startsWith("am stack move-task") }
        assertEquals(listOf("am stack move-task 70 53 true"), moves, "lệnh bê: $history")
        listOf(4, 24, 37, 52).forEach { home ->
            assertTrue(history.none { it.startsWith("am stack move-task $home ") }, "task home $home bị bê: $history")
        }
        assertTrue(history.none { it.contains("com.android.settings") }, "có đích standard ⇒ không mở Settings: $history")
    }
}
