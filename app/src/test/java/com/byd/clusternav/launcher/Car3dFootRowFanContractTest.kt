package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/** Owner 15/10: nút gió hàng chân xe 3D — bỏ icon quạt (lệch), gió AUTO in "AUTO n" như chip thanh trên. */
class Car3dFootRowFanContractTest {
    private val src = Files.readString(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/Car3dFootRow.kt"))

    @Test
    fun `gio khong con icon quat`() {
        assertFalse(src.contains("\"ic-fan\""), "icon quạt đã bỏ")
        assertTrue(src.contains("stepper(\"fan\", null)"))
    }

    @Test
    fun `gio AUTO in kem muc qua ClimateAuto fanText`() {
        assertTrue(src.contains("ClimateAuto.fanText(fanLevel, fanAutoRaw)"), "AUTO n theo một nguồn với chip")
        assertTrue(src.contains("tile == ClimateAuto.AUTO"), "chỉ đổi khi ô ẩn đang in AUTO")
    }
}
