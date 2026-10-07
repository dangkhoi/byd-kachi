package com.byd.clusternav.launcher.camera

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 2.94 QA F1 — dây nối của luật `CameraViewMode.forcesFullAmount` (luật thuần ở `CameraWideFullAmountTest`). */
class CameraWideFullAmountWiringContractTest {

    @Test
    fun `luot dung GL ep nan du khi Thang rong quy ve Nan thang, khong ghi khoa`() {
        val ctl = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/camera/CameraSignalController.kt")
        assertTrue(ctl.contains("fullAmount = CameraViewMode.forcesFullAmount(s.asked, mode)"))
        val prefs = SourceRoots.codeOf("src/main/java/com/byd/clusternav/PrefsCameraDewarp.kt")
        assertTrue(prefs.contains("knobs = cameraViewKnobs(ctx).let { if (fullAmount) it.copy(amountPct = CameraDewarpPrefs.AMOUNT_MAX) else it }"))
    }
}
