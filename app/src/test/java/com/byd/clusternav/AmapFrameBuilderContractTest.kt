package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AmapFrameBuilderContractTest {
    private val source = SourceRoots.text("src/main/java/com/byd/clusternav/AmapFrameBuilder.kt")
    private val guidance = source.substring(
        source.indexOf("fun buildGuidanceFrame"),
        source.indexOf("fun buildStateFrame"),
    )
    private val state = source.substring(source.indexOf("fun buildStateFrame"))

    @Test
    fun `guidance keeps the exact AmapService keys and proven primitive shapes`() {
        val keys = Regex("putExtra\\(\"([^\"]+)\"").findAll(guidance).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf(
                "KEY_TYPE", "TYPE", "EXTRA_STATE", "EXTRA_IS_FOREGROUND", "IS_BYD_MAP",
                "IS_BYD_BAIDU_MAP", "NEW_ICON", "ROUNG_ABOUT_NUM", "SEG_REMAIN_DIS",
                "SEG_REMAIN_DIS_AUTO", "NEXT_ROAD_NAME", "ROUTE_REMAIN_DIS",
                "ROUTE_REMAIN_TIME", "ROUTE_REMAIN_DIS_AUTO", "ROUTE_REMAIN_TIME_AUTO",
                "ROUTE_REMAIN_TIME_STRING", "ETA_TEXT",
            ),
            keys,
        )
        assertTrue(guidance.contains("putExtra(\"KEY_TYPE\", KEY_TYPE_GUIDE)"))
        assertTrue(guidance.contains("putExtra(\"TYPE\", TYPE_ACTIVE)"))
        assertTrue(guidance.contains("putExtra(\"IS_BYD_MAP\", byd)"))
        assertTrue(guidance.contains("putExtra(\"IS_BYD_BAIDU_MAP\", false)"))
        assertTrue(guidance.contains("putExtra(\"SEG_REMAIN_DIS\", -1)"))
        assertTrue(guidance.contains("putExtra(\"SEG_REMAIN_DIS_AUTO\", \"\")"))
        assertFalse(guidance.contains("speedLimit"), "speed sign must not be encoded in NaviInfo extras")
    }

    @Test
    fun `stop frame remains 10019 state 9 with ordered true then false ownership in broadcaster`() {
        assertTrue(source.contains("const val KEY_TYPE_STATE = 10019"))
        assertTrue(source.contains("const val STATE_STOP = 9"))
        assertEquals(
            setOf("KEY_TYPE", "EXTRA_STATE", "IS_BYD_MAP"),
            Regex("putExtra\\(\"([^\"]+)\"").findAll(state).map { it.groupValues[1] }.toSet(),
        )
        val broadcaster = SourceRoots.text("src/main/java/com/byd/clusternav/ClusterBroadcaster.kt")
        val reset = broadcaster.substring(
            broadcaster.indexOf("private fun sendResetFrames"),
            broadcaster.indexOf("private fun send(ctx"),
        )
        val trueIndex = reset.indexOf("STATE_STOP, true")
        val falseIndex = reset.indexOf("STATE_STOP, false")
        assertTrue(trueIndex >= 0 && falseIndex > trueIndex, "reset must remain true then false")
    }

    @Test
    fun `marquee off abbreviates the road name via fitRoadName while marquee on still scrolls`() {
        val broadcaster = SourceRoots.text("src/main/java/com/byd/clusternav/ClusterBroadcaster.kt")
        val sendFrame = broadcaster.substring(
            broadcaster.indexOf("private fun sendFrame"),
            broadcaster.indexOf("fun emitHud"),
        )
        // Road selection branches on the marquee pref.
        assertTrue(sendFrame.contains("if (Prefs.marquee(ctx))"), "sendFrame branches on the marquee pref")
        // marquee ON → still scrolls a window of the cleaned FULL name (unchanged behaviour).
        assertTrue(
            sendFrame.contains("NavFormat.roadWindow(lastCleanRoad, tick, NavFormat.ROAD_MAX_UNITS)"),
            "marquee ON keeps scrolling the road window",
        )
        // marquee OFF → sends the ABBREVIATED name (NavFormat.fitRoadName, e.g. 'Trần Trọng Kim' → 'T.T.Kim'),
        // NOT the raw full cleaned name (which let the cluster firmware hard-cut it).
        assertTrue(
            sendFrame.contains("} else NavFormat.fitRoadName(lastCleanRoad)"),
            "marquee OFF must abbreviate via NavFormat.fitRoadName",
        )
        assertFalse(
            Regex("""}\s*else\s+lastCleanRoad\b""").containsMatchIn(sendFrame),
            "marquee OFF must no longer send the raw full cleaned name",
        )
    }
}
