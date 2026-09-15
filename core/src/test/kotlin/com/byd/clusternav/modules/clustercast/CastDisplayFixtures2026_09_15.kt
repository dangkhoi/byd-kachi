package com.byd.clusternav.modules.clustercast

/**
 * Fixture NGUYÊN VĂN từ xe DiLink3.0 (Sealion 6) 2026-09-15, sau reboot, lúc cast bị rơi vào display 1
 * (spec `docs/specs/kachi-hal187-cast-remediation.html` §2(A), §4.1, R1/R2). Dùng chung cho test parser,
 * resolver và coordinator — KHÔNG sửa chuỗi (CLAUDE §10: fixture lấy từ dump thật).
 *
 * - display 1 = `kachi-slot-0-…` — VirtualDisplay của CHÍNH launcher (`owner com.byd.launcher (uid 10138)`, 1872×748)
 * - display 2 = `fission_bg_xdjaVirtualSurface` — cụm thật (`owner com.xdja.containerservice`, 1920×720)
 */
object CastDisplayFixtures2026_09_15 {

    /** applicationId của launcher trên xe (khớp `owner com.byd.launcher` trong dump). */
    const val LAUNCHER_PKG = "com.byd.launcher"

    /** Output thật của `ClusterDisplayResolver.DETECT_CMD` cũ (`grep -iE 'Display [0-9]+:|fission|xdja'`). */
    val DETECT_OUT: String = """
        |  DisplayDeviceInfo{"fission_bg_xdjaVirtualSurface": uniqueId="virtual:com.xdja.containerservice,1000,fission_bg_xdjaVirtualSurface,0", 1920 x 720, modeId 3, defaultModeId 3, supportedModes [{id=3, width=1920, height=720, fps=60.0}], colorMode 0, supportedColorModes [0], HdrCapabilities null, density 320, 320.0 x 320.0 dpi, appVsyncOff 0, presDeadline 16666666, touch NONE, rotation 0, type VIRTUAL, state ON, owner com.xdja.containerservice (uid 1000), FLAG_PRESENTATION, FLAG_OWN_CONTENT_ONLY}
        |    mUniqueId=virtual:com.xdja.containerservice,1000,fission_bg_xdjaVirtualSurface,0
        |  Display 0:
        |  Display 1:
        |  Display 2:
        |    mPrimaryDisplayDevice=fission_bg_xdjaVirtualSurface
        |    mBaseDisplayInfo=DisplayInfo{"fission_bg_xdjaVirtualSurface, displayId 2", uniqueId "virtual:com.xdja.containerservice,1000,fission_bg_xdjaVirtualSurface,0", app 1920 x 720, real 1920 x 720, ...}
        |""".trimMargin()

    /** Dòng slot-VD của launcher trong `dumpsys display` đầy đủ (device + logical display 1). */
    const val SLOT_DEVICE_LINE: String =
        """  DisplayDeviceInfo{"kachi-slot-0-1789473433259": uniqueId="virtual:com.byd.launcher,10138,kachi-slot-0-1789473433259,0", 1872 x 748, ... type VIRTUAL, state ON, owner com.byd.launcher (uid 10138), FLAG_PRIVATE, FLAG_NEVER_BLANK, FLAG_OWN_CONTENT_ONLY}"""
    const val SLOT_LOGICAL_LINE: String =
        """    mBaseDisplayInfo=DisplayInfo{"kachi-slot-0-1789473433259, displayId 1", uniqueId "virtual:com.byd.launcher,10138,kachi-slot-0-1789473433259,0", app 1872 x 748, ...}"""

    /**
     * Output kỳ vọng của `DETECT_CMD` mới (thêm `virtual:` vào grep): các dòng trên + dòng uniqueId của slot-VD.
     * Thứ tự giữ đúng cấu trúc `dumpsys display` (Display Devices trước, Logical Displays sau).
     */
    val DETECT_OUT_WITH_SLOT: String = """
        |$SLOT_DEVICE_LINE
        |    mUniqueId=virtual:com.byd.launcher,10138,kachi-slot-0-1789473433259,0
        |  DisplayDeviceInfo{"fission_bg_xdjaVirtualSurface": uniqueId="virtual:com.xdja.containerservice,1000,fission_bg_xdjaVirtualSurface,0", 1920 x 720, modeId 3, defaultModeId 3, supportedModes [{id=3, width=1920, height=720, fps=60.0}], colorMode 0, supportedColorModes [0], HdrCapabilities null, density 320, 320.0 x 320.0 dpi, appVsyncOff 0, presDeadline 16666666, touch NONE, rotation 0, type VIRTUAL, state ON, owner com.xdja.containerservice (uid 1000), FLAG_PRESENTATION, FLAG_OWN_CONTENT_ONLY}
        |    mUniqueId=virtual:com.xdja.containerservice,1000,fission_bg_xdjaVirtualSurface,0
        |  Display 0:
        |  Display 1:
        |$SLOT_LOGICAL_LINE
        |  Display 2:
        |    mPrimaryDisplayDevice=fission_bg_xdjaVirtualSurface
        |    mBaseDisplayInfo=DisplayInfo{"fission_bg_xdjaVirtualSurface, displayId 2", uniqueId "virtual:com.xdja.containerservice,1000,fission_bg_xdjaVirtualSurface,0", app 1920 x 720, real 1920 x 720, ...}
        |""".trimMargin()

    /** Cảnh sau reboot TRƯỚC khi AutoContainer mở projection: chỉ có màn giữa + slot của launcher, chưa có fission. */
    val DETECT_OUT_SLOT_ONLY: String = """
        |$SLOT_DEVICE_LINE
        |    mUniqueId=virtual:com.byd.launcher,10138,kachi-slot-0-1789473433259,0
        |  Display 0:
        |  Display 1:
        |$SLOT_LOGICAL_LINE
        |""".trimMargin()
}
