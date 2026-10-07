package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.94 · CLUSTER-BACKDROP-DAY (spec `kachi-294-plan.html` R2) — dây nối phía `:app`; luật màu thuần ở `ClusterBackdropTest`.
 *
 * Khoá: nền màn chiếu đọc sáng/tối của HỆ THỐNG xe (`Resources.getSystem()`), KHÔNG đọc cấu hình của chính Activity — Activity
 * này bọc `ThemeMode.wrap`, tức lựa chọn Sáng/Tối riêng của Kachi; cụm theo xe [ĐO 07/10] nên đọc nhầm nguồn là nền sáng trên
 * cụm tối khi người lái ép Kachi Sáng lúc đêm. Màu đi qua MỘT luật (`ClusterBackdrop.color`), kiểu khung lấy từ phiên chiếu.
 */
class ClusterBackdropWiringContractTest {

    private val black = SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/ClusterBlackActivity.kt")

    @Test
    fun `nen doc sang toi cua HE THONG, mau qua mot luat thuan`() {
        val paint = SourceRoots.body(black, "private fun paintBackdrop(")
        assertTrue(paint.contains("Resources.getSystem().configuration.uiMode"), "phải đọc uiMode của hệ thống xe")
        assertTrue(paint.contains("ClusterBackdrop.color(night, SimpleCastRuntime.castFrame())"), "màu qua luật thuần + kiểu khung phiên")
        assertTrue(paint.contains("view.setBackgroundColor(color)"))
        assertFalse(black.contains("Color.BLACK"), "không còn đen cứng")
        assertFalse(black.contains("resources.configuration"), "không đọc cấu hình đã bọc ThemeMode của Activity")
        assertTrue(SourceRoots.body(black, "override fun onCreate(").contains("paintBackdrop(view,"), "onCreate phải tô nền")
    }

    /** Review 2.94 Pass 1 · r2-backdrop: `singleInstance` — `am start` vào thực thể còn sống ⇒ tô lại theo phiên vừa ghim. */
    @Test
    fun `mo lai vao thuc the con song thi to lai theo phien moi`() {
        assertTrue(SourceRoots.body(black, "override fun onNewIntent(").contains("paintBackdrop(it,"), "onNewIntent phải tô lại")
    }

    @Test
    fun `kieu khung doc tu phien, khong dung coordinator`() {
        val rt = SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt")
        assertTrue(rt.contains("fun castFrame(): CastStyle? = runCatching { instance?.castSession?.frame }.getOrNull()"), rt.take(0))
    }
}
