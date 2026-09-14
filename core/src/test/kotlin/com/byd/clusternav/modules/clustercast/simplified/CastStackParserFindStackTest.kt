package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * X2 — [CastStackParser.findStackIdForPkg] khoá đường R2 move-stack: sau khi `am start --display <VD>` bị
 * Permission Denial, app bị bỏ lại trên display 0; ta phải tìm ĐÚNG stack id của nó để
 * `am display move-stack <stackId> <VD>` (bypass ActivityStarter — proven ClusterCast.placeLadder R2).
 */
class CastStackParserFindStackTest {

    private val stackList = """
        Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0
          taskId=1: com.android.launcher3/com.android.launcher3.Launcher visible=true
        Stack id=7 bounds=[0,0][1920,720] displayId=0 userId=0
          taskId=42: vn.vietmap.live/vn.vietmap.live.MainActivity visible=true
        Stack id=9 bounds=[0,0][1920,720] displayId=2 userId=0
          taskId=88: com.byd.clusternav2/com.byd.clusternav.modules.clustercast.ClusterBlackActivity visible=true
    """.trimIndent()

    @Test
    fun `finds the stack id hosting the package on display 0`() {
        assertEquals(7, CastStackParser.findStackIdForPkg(stackList, "vn.vietmap.live", 0))
    }

    @Test
    fun `does not match the same package name on a different display`() {
        // vietmap is on display 0, not 2 → asking for display 2 must return null (no cross-display bleed).
        assertNull(CastStackParser.findStackIdForPkg(stackList, "vn.vietmap.live", 2))
    }

    @Test
    fun `returns null when the package is absent`() {
        assertNull(CastStackParser.findStackIdForPkg(stackList, "com.not.here", 0))
    }

    @Test
    fun `exact package match — a prefix does not match`() {
        // "vn.vietmap" must NOT match task "vn.vietmap.live/..." (prefix guard).
        assertNull(CastStackParser.findStackIdForPkg(stackList, "vn.vietmap", 0))
    }
}
