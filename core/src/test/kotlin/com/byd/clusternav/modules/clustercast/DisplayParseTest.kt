package com.byd.clusternav.modules.clustercast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Test [DisplayParse] — parser thuần cho `dumpsys display` / `dumpsys window displays` / `dumpsys window windows`.
 * FIXTURE lấy NGUYÊN VĂN từ dump xe thật 2026-07-21 (BYD Seal, cụm = display 1, fission_bg_xdja…).
 */
class DisplayParseTest {

    /** trích từ docs/diagnostics/carlog-2026-07-21/02-window-displays.txt */
    private val WM_DISPLAYS = """
        WINDOW MANAGER DISPLAY CONTENTS (dumpsys window displays)
          Display: mDisplayId=1
            init=1920x720 320dpi base=1920x720 200dpi cur=1920x720 app=1920x720 rng=720x720-1920x1920
            deferred=false mLayoutNeeded=false
          Display: mDisplayId=0
            init=1920x1080 240dpi cur=1920x1080 app=1920x990 rng=1080x906-1920x1746
            deferred=false mLayoutNeeded=false
    """.trimIndent()

    @Test fun `logicalSize doc dung tung display`() {
        assertEquals(1920 to 720, DisplayParse.logicalSize(WM_DISPLAYS, 1))
        assertEquals(1920 to 1080, DisplayParse.logicalSize(WM_DISPLAYS, 0))
        assertNull(DisplayParse.logicalSize(WM_DISPLAYS, 7))
    }

    /** trích từ docs/diagnostics/carlog-2026-07-21/05-display.txt */
    private val DISPLAY_DUMP = """
        Display Devices: size=2
          DisplayDeviceInfo{"fission_bg_xdjaVirtualSurface": 1920 x 720, density 320
            mBaseDisplayInfo=DisplayInfo{"fission_bg_xdjaVirtualSurface, displayId 1", app 1920 x 720, real 1920 x 720
    """.trimIndent()

    @Test fun `clusterDisplayId nhan ra VD theo ten`() {
        assertEquals(1, DisplayParse.clusterDisplayId(DISPLAY_DUMP))
        assertEquals(-1, DisplayParse.clusterDisplayId("Display 0: tên khác\nDisplay 2: cũng khác"))
    }

    /**
     * ★ MẤU CHỐT lỗi "Android Auto không chỉnh được kích thước": app phớt lờ content inset thì khung cửa sổ
     * vẫn NGUYÊN kích thước display dù đã đặt overscan → [CastShell.overscanVerified] phải phát hiện ra để leo tầng.
     */
    private val WINDOWS = """
        WINDOW MANAGER WINDOWS (dumpsys window windows)
          Window #2 Window{a1b2c3 u0 com.byd.androidauto/com.byd.androidauto.ProjectionActivity}:
            mDisplayId=1 mSession=Session{...}
            mFrame=[0,0][1920,720] last=[0,0][1920,720]
          Window #3 Window{d4e5f6 u0 vn.vietmap.live/vn.vietmap.live.MainActivity}:
            mDisplayId=1 mSession=Session{...}
            mFrame=[320,0][1600,720] last=[320,0][1600,720]
    """.trimIndent()

    @Test fun `appWindowFrame lay dung khung tung app`() {
        assertEquals(listOf(0, 0, 1920, 720), DisplayParse.appWindowFrame(WINDOWS, "com.byd.androidauto")!!.toList())
        assertEquals(listOf(320, 0, 1600, 720), DisplayParse.appWindowFrame(WINDOWS, "vn.vietmap.live")!!.toList())
        assertNull(DisplayParse.appWindowFrame(WINDOWS, "com.khong.co"))
    }

    @Test fun `realSize fallback khi khong doc duoc`() {
        assertEquals(1920 to 720, DisplayParse.realSize("rác", 1))
        assertEquals(800 to 480, DisplayParse.realSize("rác", 1, fw = 800, fh = 480))
    }

    /**
     * ★ Bằng chứng quyết định vụ "DPI không work": dump xe cho density = 200 đúng bằng MẶC ĐỊNH của app,
     * trong khi cụm gốc là 320. Nếu có một lần bấm DPI nào ăn thì con số phải là 210/190… chứ không thể tròn 200.
     */
    @Test fun `density doc duoc dang-ap va goc`() {
        assertEquals(200 to 320, DisplayParse.density(WM_DISPLAYS, 1))   // đã bị ép: base 200, gốc 320
        assertEquals(240 to 240, DisplayParse.density(WM_DISPLAYS, 0))   // chưa ép: không có base → đang == gốc
        assertNull(DisplayParse.density(WM_DISPLAYS, 9))
    }

    // ── Regression 2026-09-15: cast rơi display 1 (slot launcher) thay vì 2 (cụm) — spec kachi-hal187 R1/R2 ──

    /** (a) Parser trên output THẬT của DETECT_CMD → 2, không phải 1 (lỗi là timing, không phải parser). */
    @Test fun `clusterDisplayId tren grep that 2026-09-15 ra 2`() {
        assertEquals(2, DisplayParse.clusterDisplayId(CastDisplayFixtures2026_09_15.DETECT_OUT))
        // Thêm dòng slot-VD (grep mở rộng `virtual:`) không đổi kết quả.
        assertEquals(2, DisplayParse.clusterDisplayId(CastDisplayFixtures2026_09_15.DETECT_OUT_WITH_SLOT))
        // Chưa có fission (trước khi mở projection) → -1, KHÔNG bịa.
        assertEquals(-1, DisplayParse.clusterDisplayId(CastDisplayFixtures2026_09_15.DETECT_OUT_SLOT_ONLY))
    }

    /** R2: nhận diện VD do chính launcher sở hữu (owner/uniqueId `virtual:<pkg>,`) — qua cả 2 đường (a)/(b). */
    @Test fun `ownedVirtualDisplayIds nhan dien slot-VD cua launcher`() {
        val pkg = CastDisplayFixtures2026_09_15.LAUNCHER_PKG
        // (a) dòng DisplayInfo có sẵn displayId + uniqueId owner.
        assertEquals(setOf(1), DisplayParse.ownedVirtualDisplayIds(CastDisplayFixtures2026_09_15.DETECT_OUT_WITH_SLOT, pkg))
        // (b) dump đầy đủ: header `Display N:` + `mPrimaryDisplayDevice=<name>` với name của device owner=launcher.
        val fullDump = """
            |${CastDisplayFixtures2026_09_15.SLOT_DEVICE_LINE}
            |  Display 0:
            |    mPrimaryDisplayDevice=Built-in Screen
            |  Display 1:
            |    mPrimaryDisplayDevice=kachi-slot-0-1789473433259
            |  Display 2:
            |    mPrimaryDisplayDevice=fission_bg_xdjaVirtualSurface
            |""".trimMargin()
        assertEquals(setOf(1), DisplayParse.ownedVirtualDisplayIds(fullDump, pkg))
        assertTrue(DisplayParse.isOwnedVirtualDisplay(fullDump, 1, pkg))
        assertFalse(DisplayParse.isOwnedVirtualDisplay(fullDump, 2, pkg), "cụm fission không thuộc launcher")
        // Gói khác / rỗng → không nhận gì (guard theo tham số, không hardcode tên gói).
        assertEquals(emptySet<Int>(), DisplayParse.ownedVirtualDisplayIds(fullDump, "com.other.app"))
        assertEquals(emptySet<Int>(), DisplayParse.ownedVirtualDisplayIds(fullDump, ""))
        // Không có slot nào → rỗng.
        assertEquals(emptySet<Int>(), DisplayParse.ownedVirtualDisplayIds(CastDisplayFixtures2026_09_15.DETECT_OUT, pkg))
    }
}
