package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.ClusterProfile
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * 3.1 · R6 — DÂY NỐI phía `:app` của hàng "Dịch nội dung" (spec `kachi-301-cast-steps-dialog-ia.html` R6). Cơ chế + phép đo chạy
 * thật ở `:core` (`CastContentShiftTest`, `CastContentShifterTest`, `CastContentShiftCoordinatorTest`); bài này hỏi: màn có THẬT
 * SỰ chỉ hiện hàng khi đo được, có dùng chung chip Bước (5 px để tới −20 chính xác — ca Waze của owner 13/10), có nghe kết cục
 * không, và khác biệt đời xe có nằm ở `ClusterProfile` không (không `if` theo tên gói).
 */
class SettingsCastShiftWiringContractTest {

    private fun launcher(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")
    private val row by lazy { launcher("SettingsCastShiftRow.kt") }
    private val block by lazy { launcher("SettingsSectionsCastGeometry.kt") }
    private val bridge by lazy { launcher("ClusterNavBridgeContentShift.kt") }

    @Test
    fun `hang chi dung khi cau noi duoc - an khi khong do duoc hoac app khong nhan`() {
        val add = SourceRoots.body(row, "fun addTo(holder: LinearLayout, t: CastGeometryTarget)")
        assertTrue(add.indexOf("if (!bridge.contentShiftAvailable(t)) return") in 0 until add.indexOf("rows.stepperRow("), add)
        val avail = SourceRoots.body(bridge, "fun ClusterNavBridge.contentShiftAvailable(target: CastGeometryTarget): Boolean")
        listOf("target.side != null", "CastingFull", "isResizable", "CastStyle.RECT", "contentShiftSupported", "contentShiftVerdict(target.pkg) != false")
            .forEach { assertTrue(avail.contains(it), "thiếu điều kiện '$it': $avail") }
        assertFalse(Regex(""""(com|vn)\.[a-z]""").containsMatchIn(bridge + row), "không có tên gói trong mã hàng dịch (CLAUDE.md §7)")
    }

    @Test
    fun `dung chung chip Buoc cua khoi Khung, nhan giu lap, a11y`() {
        assertTrue(block.contains("SettingsCastShiftRow(context, rows, deps, { step }, { rebuild() })"), "bước = chip Bước 5/20/50")
        assertTrue(block.contains("shiftRow.addTo(holder, target)"))
        assertTrue(row.contains("SettingsRepeatTouch.attachToStepper(s.view, minus, plus)"))
        assertTrue(row.contains("contentDescription = context.getString(R.string.kshift_a11y)"))
        val nudge = SourceRoots.body(row, "private fun nudge(sign: Int)")
        assertTrue(nudge.contains("CastContentShift.clamp(now + sign * step())"), nudge)
        assertTrue(nudge.indexOf("stepper?.setValue(px(next))") in 0 until nudge.indexOf("bridge.setContentShift(t, next)"), "hiện TRƯỚC khi gửi")
    }

    @Test
    fun `nghe ket cuc - khong nhan thi dung lai khoi (hang an), dong bo theo geometryKey`() {
        assertTrue(block.contains("bridge.observeContentShift { _, r -> v.post { if (v.isAttachedToWindow) shiftRow.onOutcome(r) } }"))
        assertTrue(SourceRoots.body(block, "fun sync()").contains("shiftRow.sync()"))
        val out = SourceRoots.body(row, "fun onOutcome(result: CastShiftResult)")
        assertTrue(out.contains("CastShiftResult.INEFFECTIVE") && out.contains("rebuild()"), out)
    }

    @Test
    fun `doi xe - DiLink 5 khong co wm overscan, DiLink 3 co (API chan them luc chay)`() {
        assertFalse(ClusterProfile.DL5.supportsOverscan)
        assertTrue(ClusterProfile.SEAL_DL3.supportsOverscan)
        assertTrue(ClusterProfile.SEAL_DL3.projectionRecipe(sdkInt = 29).supportsOverscan)
        assertFalse(ClusterProfile.SEAL_DL3.projectionRecipe(sdkInt = 31).supportsOverscan, "Android 12 đã gỡ lệnh")
        assertFalse(ClusterProfile.DL5.projectionRecipe(sdkInt = 29).supportsOverscan)
    }

    @Test
    fun `chu cua hang co du 5 ngon ngu, cung tap khoa`() {
        val sets = listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms").map { d ->
            val f = SourceRoots.path("src/main/res/$d/strings_kachi_shift301.xml")
            assertTrue(Files.exists(f), "thiếu $f")
            Regex("""name="(kshift_[a-z0-9_]+)"""").findAll(Files.readString(f)).map { it.groupValues[1] }.toSet()
        }
        assertEquals(5, sets.first().size)
        sets.forEach { assertEquals(sets.first(), it) }
    }
}
