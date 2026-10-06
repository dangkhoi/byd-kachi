package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.launcher.ProfileScope
import com.byd.clusternav.launcher.ProfileScopeCluster
import com.byd.clusternav.modules.clustercast.simplified.BubbleOldModMemo
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 wave 2A · VM-BUBBLE-OLDMOD-MEMO — DÂY NỐI phía `:app` của sổ "bản mod đang cài không ẩn bóng" ═════════════════
 *
 * Luật + cổng thuần chạy thật ở `ClusterThemeOldModMemoTest` (`:core`). Bài này khoá phần chỉ Android có (không Robolectric ⇒
 * canh mã đã bỏ chú thích): (1) dấu cài đặt = `longVersionCode` + `lastUpdateTime` của ĐÚNG gói bản mod (đổi ở mọi lần cài —
 * KDoc [BubbleOldModMemo]); (2) sổ nằm ở tệp `clustercast` phạm vi XE, ghi `commit()` đồng bộ; (3) khoá khai phạm vi XE (đổi hồ
 * sơ không kéo sổ theo); (4) bộ thi hành DUY NHẤT của cổng theme (`ClusterLayerExecutor`) là nơi nối.
 */
class BubbleOldModMemoWiringContractTest {

    private val layers by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/ClusterLayerExecutor.kt") }
    private val bubble by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/VmBubbleVisibility.kt") }

    @Test
    fun `dau cai dat doc tu PackageInfo cua ban mod - ma phien ban va lan cai`() {
        val fn = SourceRoots.body(bubble, "fun installToken(ctx: Context): String? =")
        assertTrue(fn.contains("PackageQueries.packageInfo(ctx.applicationContext.packageManager, VIETMAP_PKG)"), fn)
        assertTrue(fn.contains("BubbleOldModMemo.token(it.longVersionCode, it.lastUpdateTime)"), fn)
        assertFalse(fn.contains("it.versionCode"), "versionCode (int) deprecated từ API 28 — dùng longVersionCode")
    }

    @Test
    fun `bo thi hanh cong theme noi so - tep clustercast, commit dong bo`() {
        assertTrue(layers.contains("override fun bubbleInstallToken(): String? = VmBubbleVisibility.installToken(app)"))
        assertTrue(layers.contains("override fun oldModMemo(): String? = memoPrefs().getString(BubbleOldModMemo.KEY, null)"))
        val write = SourceRoots.body(layers, "override fun writeOldModMemo(value: String?): Boolean {")
        assertTrue(write.contains("e.remove(BubbleOldModMemo.KEY)") && write.contains("e.putString(BubbleOldModMemo.KEY, value)"))
        assertTrue(write.contains("return e.commit()"), "lượt mở kế phải đọc đúng thứ vừa ghi (không apply() nền)")
        assertTrue(layers.contains("app.getSharedPreferences(ClusterProfile.PREF, Context.MODE_PRIVATE)"))
    }

    @Test
    fun `khoa so khai pham vi XE`() {
        assertEquals("cluster_bubble_old_mod", BubbleOldModMemo.KEY)
        assertTrue(BubbleOldModMemo.KEY in ProfileScopeCluster.DEVICE_KEYS)
        assertEquals(ProfileScope.Scope.DEVICE, ProfileScope.scopeOf(BubbleOldModMemo.KEY))
    }
}
