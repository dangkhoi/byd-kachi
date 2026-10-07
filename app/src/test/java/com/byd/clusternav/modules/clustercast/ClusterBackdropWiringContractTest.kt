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

    /**
     * 2.96 · CLUSTER-BACKDROP-NIGHT-LIVE — ROM BYD không dựng lại Activity khi đổi uiMode [ĐO dexdump `services.jar` xe 07/10] ⇒ nền
     * phải tô lại trong `onConfigurationChanged`, và manifest phải khai `uiMode` (không khai thì A10 không gọi hàm này trong lượt
     * đổi cấu hình hệ thống). Thử ĐỎ: bỏ `configChanges` hoặc bỏ lời gọi tô lại.
     */
    @Test
    fun `doi sang toi khi dang chieu - to lai ngay, khong cho dung lai Activity`() {
        assertTrue(SourceRoots.body(black, "override fun onConfigurationChanged(").contains("paintBackdrop(it,"), "phải tô lại khi đổi cấu hình")
        val manifest = SourceRoots.codeOf("src/main/AndroidManifest.xml")
        val decl = manifest.substringAfter("android:name=\".modules.clustercast.ClusterBlackActivity\"").substringBefore("/>")
        assertTrue(decl.contains("android:configChanges=\"uiMode\""), decl)
    }

    @Test
    fun `kieu khung doc tu phien, khong dung coordinator`() {
        val rt = SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt")
        assertTrue(rt.contains("fun castFrame(): CastStyle? = runCatching { instance?.castSession?.frame }.getOrNull()"), rt.take(0))
    }
}
