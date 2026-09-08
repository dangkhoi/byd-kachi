package com.byd.clusternav.navigation.screencapture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Khoá TẦNG 0 của B3.27 (08-22): resolver phải giữ lại **ô cửa sổ THẬT** ([AppLocation.windowRect]) và
 * **dpi THẬT** ([AppLocation.densityDpi]) — hai thứ trước đây bị vứt đi dù đã parse.
 *
 * VÌ SAO CẦN: khi cast lên cụm, người dùng chỉnh được dpi / kích thước / vị trí cửa sổ và cast một hay hai
 * app (`CastShell`: `wm size`, `wm density`, `am task resize`, chia đôi). Vùng quét glyph phải neo vào Ô NÀY;
 * neo vào display là trượt ngay khi bố cục đổi. Cả hai giá trị đến MIỄN PHÍ từ output `am stack list` mà
 * nhịp chụp vốn đã lấy — không tốn thêm round-trip shell nào.
 */
class CaptureLocationWindowRectTest {

    private fun realApi34Fixture(): String =
        javaClass.getResource("/diagnostics/am-stack-list-api34-cluster-1920x720-d240.txt")!!.readText()

    @Test
    fun `output THAT cua API 34 (RootTask) — lay duoc windowRect va dpi`() {
        val loc = CaptureLocationResolver.resolve(
            amOut = realApi34Fixture(),
            pkg = "com.chisadin.wazemod",
            navFresh = true,
            foregroundHint = false,
        )
        assertEquals(CropRect(0, 0, 1920, 720), loc.windowRect)
        assertEquals(240, loc.densityDpi)
        assertEquals(true, loc.isFullscreen)
    }

    /**
     * HỒI QUY cho lỗi tìm ra 08-22: regex cũ chỉ chấp nhận `Stack id=` (Android 10 trên xe), trong khi
     * Android 12+/API 34 in `RootTask id=`. Không khớp dòng nào ⇒ resolver rơi hết về nhánh "không thấy
     * task" ⇒ windowRect null + dpi 0 ⇒ tầng glyph mất chỗ neo. Lỗi IM LẶNG (pipeline vẫn chạy nhờ nhánh
     * dự phòng) nên phải có test canh.
     */
    @Test
    fun `chap nhan CA HAI dang dau khoi — Stack (A10 tren xe) va RootTask (A12+)`() {
        val a10 = """
            Stack id=10 bounds=[0,0][1920,720] displayId=0 userId=0
             configuration={1.0 [en_US] 240dpi land finger}
              taskId=33: com.chisadin.wazemod/com.waze.MainActivity bounds=[0,0][1920,720] visible=true
        """.trimIndent()
        val loc = CaptureLocationResolver.resolve(a10, "com.chisadin.wazemod", true, false)
        assertEquals(CropRect(0, 0, 1920, 720), loc.windowRect)
        assertEquals(240, loc.densityDpi)
    }

    /** Cast HAI app chia đôi: nav app ở nửa PHẢI → windowRect phải là nửa phải, không phải cả display. */
    @Test
    fun `cast 2 app — nua PHAI cho windowRect dung nua phai`() {
        val split = """
            RootTask id=5 bounds=[960,0][1920,720] displayId=1 userId=0
             configuration={1.0 [en_US] 320dpi land finger}
              taskId=51: com.chisadin.wazemod/com.waze.MainActivity bounds=[960,0][1920,720] visible=true
            RootTask id=6 bounds=[0,0][960,720] displayId=1 userId=0
              taskId=52: vn.vietmap.live/.MainActivity bounds=[0,0][960,720] visible=true
        """.trimIndent()
        val loc = CaptureLocationResolver.resolve(split, "com.chisadin.wazemod", true, false)
        assertEquals(CropRect(960, 0, 1920, 720), loc.windowRect)
        assertEquals(CaptureSlotSide.RIGHT, loc.slotSide)
        assertEquals(320, loc.densityDpi)
        assertEquals(false, loc.isFullscreen)
    }

    /** Cast HAI app chia đôi: nav app ở nửa TRÁI. */
    @Test
    fun `cast 2 app — nua TRAI cho windowRect dung nua trai`() {
        val split = """
            RootTask id=6 bounds=[0,0][960,720] displayId=1 userId=0
             configuration={1.0 [en_US] 240dpi land finger}
              taskId=52: com.chisadin.wazemod/com.waze.MainActivity bounds=[0,0][960,720] visible=true
            RootTask id=5 bounds=[960,0][1920,720] displayId=1 userId=0
              taskId=51: vn.vietmap.live/.MainActivity bounds=[960,0][1920,720] visible=true
        """.trimIndent()
        val loc = CaptureLocationResolver.resolve(split, "com.chisadin.wazemod", true, false)
        assertEquals(CropRect(0, 0, 960, 720), loc.windowRect)
        assertEquals(CaptureSlotSide.LEFT, loc.slotSide)
    }

    /**
     * Người dùng dời/thu nhỏ cửa sổ cast (`am task resize`): bounds của DÒNG TASK khác bounds của stack →
     * phải lấy theo dòng task, vì đó mới là ô app thật sự vẽ vào.
     */
    @Test
    fun `cua so bi dat lai kich thuoc — uu tien bounds cua dong TASK`() {
        val resized = """
            RootTask id=9 bounds=[0,0][1920,720] displayId=1 userId=0
             configuration={1.0 [en_US] 200dpi land finger}
              taskId=90: com.chisadin.wazemod/com.waze.MainActivity bounds=[120,40][1400,660] visible=true
        """.trimIndent()
        val loc = CaptureLocationResolver.resolve(resized, "com.chisadin.wazemod", true, false)
        assertEquals(CropRect(120, 40, 1400, 660), loc.windowRect)
        assertEquals(200, loc.densityDpi)
    }

    /** Không thấy task → windowRect null (caller dùng cả khung), dpi 0 → caller rơi về mặc định. */
    @Test
    fun `khong thay task thi windowRect null va dpi 0`() {
        val loc = CaptureLocationResolver.resolve("", "com.chisadin.wazemod", true, true)
        assertNull(loc.windowRect)
        assertEquals(0, loc.densityDpi)
        assertEquals(DisplayGeometry.DENSITY_DEFAULT, DisplayGeometry(100, 100).effectiveDensityDpi)
    }

    /** dpi đọc được phải là dpi của ĐÚNG khối chứa task, không phải khối cuối cùng trong output. */
    @Test
    fun `dpi lay theo khoi chua task, khong phai khoi cuoi`() {
        val two = """
            RootTask id=1 bounds=[0,0][1920,720] displayId=1 userId=0
             configuration={1.0 [en_US] 160dpi land finger}
              taskId=11: com.chisadin.wazemod/com.waze.MainActivity bounds=[0,0][1920,720] visible=true
            RootTask id=2 bounds=[0,0][1920,1080] displayId=0 userId=0
             configuration={1.0 [en_US] 480dpi land finger}
              taskId=22: com.other.app/.Main bounds=[0,0][1920,1080] visible=true
        """.trimIndent()
        val loc = CaptureLocationResolver.resolve(two, "com.chisadin.wazemod", true, false)
        assertEquals(160, loc.densityDpi)
        assertNotNull(loc.windowRect)
    }
}
