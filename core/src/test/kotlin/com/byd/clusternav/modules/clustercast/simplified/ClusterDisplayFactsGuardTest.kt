package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Soát 3.1 Pass 2 (R26) — [CastGeometryController.displayFacts] chỉ ĐỌC khi display cụm đã xác minh (`≥ 1`), đọc ĐÚNG MỘT lần
 * mỗi (phiên × display), và [CastGeometryController.knownFacts] chỉ trả bản của display SỐNG hiện tại.
 *
 * Vì sao: `displayId` của coordinator có thể còn `0`/`-1` trước lượt dò (CLAUDE.md §4 câu 1: `vd < 1` ⇒ không làm gì). Không
 * có cổng này, `am stack list` sẽ bị đọc theo display 0 (màn chính) và `ClusterDisplayFacts.sanitize` làm sạch `wmSize` của
 * app theo kích MÀN CHÍNH — sai màn. Bản đọc hụt cũng không được cache cho id thật.
 */
class ClusterDisplayFactsGuardTest {

    private class Shell(private val answers: Map<String, ShellResult>) : SimpleCastShell {
        val history = mutableListOf<String>()
        override fun execute(command: String): ShellResult {
            history += command
            return answers[command] ?: ShellResult(0, "", "")
        }
    }

    private val stackList = """
        Stack id=5 bounds=[0,0][1920,720] displayId=2 userId=0
         configuration={1.0 ?mcc?mnc [vi_VN] ldltr sw300dp w800dp h300dp 240dpi lrg long land car -touch qwerty/v/h -nav/h winConfig={ mBounds=Rect(0, 0 - 1920, 720) mAppBounds=Rect(0, 0 - 1920, 720) mWindowingMode=freeform mDisplayWindowingMode=freeform mActivityType=standard} s.1}
          taskId=14: com.waze bounds=[0,60][1920,520]
    """.trimIndent()

    @Test
    fun `display chua xac minh - 0 lenh doc, khong cache, knownFacts null`() {
        val shell = Shell(emptyMap())
        var live = 0
        val ctl = CastGeometryController(shell, FakePrefs(), { live }) {}
        val facts = ctl.displayFacts()
        assertEquals(ClusterDisplayFacts.Facts(0, null, null), facts, "chưa xác minh ⇒ chưa biết, không đoán")
        assertEquals(emptyList<String>(), shell.history, "không một lệnh đọc nào trên display chưa xác minh")
        assertNull(ctl.knownFacts)
        // Dò xong ⇒ lượt sau đọc THẬT trên id sống (bản hụt của id 0 không được cache sang).
        live = 2
        val shell2 = Shell(mapOf("am stack list" to ShellResult(0, stackList, ""), "wm size -d 2" to ShellResult(0, "Physical size: 1920x720\n", "")))
        val ctl2 = CastGeometryController(shell2, FakePrefs(), { live }) {}
        assertEquals(ClusterDisplayFacts.Facts(2, true, 1920 to 720), ctl2.displayFacts())
        assertEquals(listOf("am stack list", "wm size -d 2"), shell2.history)
    }

    @Test
    fun `doc mot lan moi display - doi display thi knownFacts null va doc lai`() {
        var live = 2
        val shell = Shell(mapOf("am stack list" to ShellResult(0, stackList, ""), "wm size -d 2" to ShellResult(0, "Physical size: 1920x720\n", "")))
        val ctl = CastGeometryController(shell, FakePrefs(), { live }) {}
        ctl.displayFacts(); ctl.displayFacts()
        assertEquals(2, shell.history.size, "lượt hai: 0 lệnh (cache theo display)")
        assertEquals(true, ctl.knownFacts?.freeform)
        live = 3
        assertNull(ctl.knownFacts, "display đổi ⇒ bản cũ không còn là của màn sống")
        ctl.displayFacts()
        assertEquals(listOf("am stack list", "wm size -d 2", "am stack list", "wm size -d 3"), shell.history)
        assertNull(ctl.knownFacts?.freeform, "display 3 không có stack trong bản đọc ⇒ chưa biết (giữ hành vi cũ)")
    }
}
