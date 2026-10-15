package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * FAN-STEP 3.1.7 — `refresh` của ô STEP so số xe với HÌNH đang vẽ ([StepTileSync]), không với bảng dùng chung mà phím/giọng nói
 * ghi. Gỡ dây (quay lại `v != state.value(def)`) ⇒ "2" kẹt sau khi lăn con lăn lên 3 (owner 15/10). Xem StepTileSyncTest.
 */
class StepTileSyncWiringContractTest {
    private val src = Files.readString(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt"))

    @Test
    fun `refresh o STEP dung StepTileSync`() {
        assertTrue(src.contains("StepTileSync.needsRedraw(drawn, drawnAuto, v, a)"), "refresh phải so với hình đang vẽ")
        assertFalse(src.contains("if (v != state.value(def) || a != auto)"), "luật cũ so với bảng dùng chung ⇒ kẹt số")
    }

    @Test
    fun `moi lan ve deu ghi lai hinh`() {
        assertTrue(src.contains("fun draw(v: Int) { drawn = v; drawnAuto = auto;"), "cú chạm vẽ ⇒ ghi hình")
        assertTrue(src.contains("auto = a; drawn = v; drawnAuto = a"), "lượt đọc vẽ ⇒ ghi hình")
    }
}
