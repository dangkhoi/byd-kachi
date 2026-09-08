package com.byd.clusternav.modules.clustercast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá bài học lỗi hiện trường 22/07 (xe Seal, bản 0.47).
 *
 * Fixture lấy NGUYÊN VĂN từ `docs/diagnostics` / log do app tự chụp:
 *  - `diag-0722-074736.txt` — block `Display: mDisplayId=1` chứa ĐỒNG THỜI stack 9 (CarPlay) và stack 39 (Vietmap)
 *  - `diag-0722-073807.txt` — `am stack list` cùng thời điểm chỉ có stack trên display 0, KHÔNG có stack 9
 *
 * Cái test này khoá: máy có thể rơi vào trạng thái WM thấy stack trên cụm mà AM không thấy. Ở trạng thái đó
 * mọi lệnh `am`/`wm` đều vô nghĩa, và ClusterNav PHẢI dừng thay vì bắn thêm lệnh.
 */
class WmParseTest {

    /** Nguyên văn từ diag-0722-074736.txt (đã cắt bớt dòng không liên quan, giữ đúng thụt lề và chuỗi). */
    private val WM_HAI_STACK_TREN_CUM = """
  Display: mDisplayId=1
    init=1920x720 320dpi base=1920x720 130dpi cur=1920x720 app=1920x720 rng=720x720-1920x1920
  Application tokens in top down Z order:
    mStackId=9
      taskId=13
        appTokens=[AppWindowToken{f99bcce token=Token{94e3dc9 ActivityRecord{f4e89d0 u0 com.byd.carplay.ui/.VideoActivity t13}}}]
    mStackId=39
      taskId=6
        appTokens=[AppWindowToken{318bbf5 token=Token{3d0b52c ActivityRecord{8ea84df u0 vn.vietmap.live/.MainActivity t6}}}]
  Display: mDisplayId=0
    init=1920x1080 240dpi cur=1920x1080 app=1920x990 rng=1080x906-1920x1746
  Application tokens in top down Z order:
    mStackId=2
      taskId=4
        appTokens=[AppWindowToken{aaaaaaa token=Token{bbbbbbb ActivityRecord{ccccccc u0 com.byd.filemanager/.view.MainActivity t4}}}]
""".trimIndent()

    /** Nguyên văn `am stack list` từ diag-0722-073807.txt — KHÔNG có stack nào trên display 1. */
    private val AM_CHI_CO_MAN_GIUA = """
Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0
  taskId=0: com.android.launcher3/.Launcher bounds=[0,0][1920,1080] userId=0 visible=true topActivity=ComponentInfo{com.android.launcher3/com.android.launcher3.Launcher}
Stack id=2 bounds=[0,0][1920,1080] displayId=0 userId=0
  taskId=4: com.byd.filemanager/.view.MainActivity bounds=[0,0][1920,1080] userId=0 visible=true topActivity=ComponentInfo{com.byd.filemanager/com.byd.filemanager.view.MainActivity}
""".trimIndent()

    @Test
    fun `doc duoc ca hai stack tren cum`() {
        val ids = WmParse.stackIdsOn(WM_HAI_STACK_TREN_CUM, 1)
        assertEquals(setOf(9, 39), ids, "WM thấy CẢ HAI stack trên cụm")
        val pkgs = WmParse.pkgsOn(WM_HAI_STACK_TREN_CUM, 1)
        assertTrue(pkgs.contains("com.byd.carplay.ui"), "phải nhận ra CarPlay")
        assertTrue(pkgs.contains("vn.vietmap.live"), "phải nhận ra Vietmap")
    }

    @Test
    fun `khong lan sang display khac`() {
        val ids = WmParse.stackIdsOn(WM_HAI_STACK_TREN_CUM, 0)
        assertEquals(setOf(2), ids, "stack của màn giữa không được lẫn vào cụm và ngược lại")
        assertFalse(WmParse.pkgsOn(WM_HAI_STACK_TREN_CUM, 1).contains("com.byd.filemanager"))
    }

    @Test
    fun `phat hien duoc stack mo coi WM co ma AM khong`() {
        val am = StackParse.parse(AM_CHI_CO_MAN_GIUA)
        val orphans = WmParse.orphanStacksOn(WM_HAI_STACK_TREN_CUM, am, 1)
        assertEquals(setOf(9, 39), orphans, "cả hai stack trên cụm đều mồ côi với AM")
        assertTrue(WmParse.orphanStacksOn(WM_HAI_STACK_TREN_CUM, am, 1).isNotEmpty(), "phải kết luận máy đang lệch WM↔AM")
    }

    @Test
    fun `AM co ma WM chua co thi KHONG bao dong`() {
        // stack vừa tạo chưa kịp có cửa sổ là chuyện bình thường — báo động ở đây chỉ tạo cảnh báo giả
        val am = StackParse.parse(
            """
            Stack id=77 bounds=[0,0][1920,720] displayId=1 userId=0
              taskId=90: vn.vietmap.live/.MainActivity bounds=[0,0][1920,720] userId=0 visible=true topActivity=ComponentInfo{vn.vietmap.live/vn.vietmap.live.MainActivity}
            """.trimIndent()
        )
        val wmRong = "  Display: mDisplayId=1\n  Application tokens in top down Z order:\n"
        assertTrue(WmParse.orphanStacksOn(wmRong, am, 1).isEmpty(), "AM có mà WM chưa có thì không phải trạng thái hỏng")
    }

    @Test
    fun `truy van la thuan - guard nam o tang ra quyet dinh`() {
        val am = StackParse.parse(AM_CHI_CO_MAN_GIUA)
        // đọc thì đọc thật, kể cả display 0 (cần cho việc dọn cửa sổ nổi kẹt trên màn giữa)
        assertTrue(WmParse.stackIdsOn(WM_HAI_STACK_TREN_CUM, 0).isNotEmpty(), "hỏi display 0 phải trả lời thật")
        assertTrue(WmParse.stackIdsOn(WM_HAI_STACK_TREN_CUM, -1).isEmpty(), "display không tồn tại thì rỗng")
        // nhưng KẾT LUẬN "máy hỏng" thì chỉ được phát ra cho display cụm hợp lệ
        assertTrue(WmParse.orphanStacksOn(WM_HAI_STACK_TREN_CUM, am, -1).isEmpty(), "vd=-1 → không kết luận gì")
        assertTrue(WmParse.orphanStacksOn(WM_HAI_STACK_TREN_CUM, am, 0).isEmpty(), "display 0 không phải việc của ta")
    }

    // ── tập display của cụm: khoá lỗi Dudu launcher chiếm mất id 1 (log SL6 22/07) ──

    @Test
    fun `SL6 - Dudu chiem id 1 thi cum phai la 2`() {
        val dump = """
            DisplayDeviceInfo{"launcher-split": uniqueId="virtual:com.dudu.autoui,10075,launcher-split,0", 1284 x 1080
            DisplayDeviceInfo{"fission_bg_xdjaVirtualSurface": uniqueId="virtual:com.xdja.containerservice,1000,fission_bg_xdjaVirtualSurface,0", 1920 x 720
            Display 0:
              mBaseDisplayInfo=DisplayInfo{"Màn hình tích hợp, displayId 0", app 1920 x 1080
            Display 1:
              mBaseDisplayInfo=DisplayInfo{"launcher-split, displayId 1", app 1284 x 1080
            Display 2:
              mBaseDisplayInfo=DisplayInfo{"fission_bg_xdjaVirtualSurface, displayId 2", app 1920 x 720
        """.trimIndent()
        val ids = WmParse.clusterDisplayIds(dump)
        assertEquals(setOf(2), ids, "chỉ display MANG TÊN cụm mới được coi là cụm")
        assertFalse(ids.contains(1), "display RIÊNG của launcher tuyệt đối không được đụng vào")
    }

    @Test
    fun `Seal - khong co Dudu thi cum la 1`() {
        val dump = """
            Display 0:
              mBaseDisplayInfo=DisplayInfo{"Màn hình tích hợp, displayId 0", app 1920 x 1080
            Display 1:
              mBaseDisplayInfo=DisplayInfo{"fission_bg_xdjaVirtualSurface, displayId 1", app 1920 x 720
        """.trimIndent()
        assertEquals(setOf(1), WmParse.clusterDisplayIds(dump))
    }

    @Test
    fun `VD tai tao thi giu ca id cu lan moi`() {
        // opcode 16 tái tạo VD → id mới; dump còn cả hai. Lọc theo TẬP mới an toàn, lọc theo một số là hụt.
        val dump = """
            Display 1:
              mBaseDisplayInfo=DisplayInfo{"fission_bg_xdjaVirtualSurface, displayId 1", app 1920 x 720
            Display 3:
              mBaseDisplayInfo=DisplayInfo{"fission_bg_xdjaVirtualSurface_0, displayId 3", app 1920 x 720
        """.trimIndent()
        assertEquals(setOf(1, 3), WmParse.clusterDisplayIds(dump))
    }

    // ── khoá các lỗi BÁO ĐỘNG GIẢ mà senior review 22/07 tìm ra ──

    @Test
    fun `am stack list rong thi KHONG duoc ket luan mo coi`() {
        // shell hụt trả chuỗi rỗng — hay xảy ra đúng lúc cắm CarPlay (đầu xe bận nhất).
        // Coi rỗng là "AM không thấy gì" sẽ khoá người dùng khỏi tính năng bằng một câu báo động sai.
        val am = StackParse.parse("")
        assertTrue(am.isEmpty(), "tiền đề: dump rỗng thì không có entry nào")
        assertTrue(WmParse.orphanStacksOn(WM_HAI_STACK_TREN_CUM, am, 1).isEmpty(),
            "không có dữ liệu thì phải nói CHƯA BIẾT, không được kết luận")
    }

    @Test
    fun `stack rong khong task thi khong phai mo coi`() {
        // StackParse chỉ sinh entry khi có `taskId=` → stack rỗng KHÔNG BAO GIỜ xuất hiện phía AM.
        // So hai tập lệch luật nhau là tự tạo mồ côi giả.
        val wm = """
  Display: mDisplayId=1
  Application tokens in top down Z order:
    mStackId=77
    mStackId=39
      taskId=6
        appTokens=[AppWindowToken{318bbf5 token=Token{3d0b52c ActivityRecord{8ea84df u0 vn.vietmap.live/.MainActivity t6}}}]
""".trimIndent()
        val am = StackParse.parse(
            "Stack id=39 bounds=[0,0][1920,720] displayId=1 userId=0\n" +
                "  taskId=6: vn.vietmap.live/.MainActivity bounds=[0,0][1920,720] userId=0 visible=true topActivity=ComponentInfo{vn.vietmap.live/vn.vietmap.live.MainActivity}"
        )
        assertTrue(WmParse.orphanStacksOn(wm, am, 1).isEmpty(), "stack 77 rỗng không được tính là mồ côi")
    }

    @Test
    fun `stack dang cho go mDeferRemoval khong phai mo coi`() {
        // WM giữ stack chờ animation xong là cơ chế HỢP LỆ khiến WM sống lâu hơn AM.
        val wm = """
  Display: mDisplayId=1
  Application tokens in top down Z order:
    mStackId=9
    mDeferRemoval=true
      taskId=13
        appTokens=[AppWindowToken{f99bcce token=Token{94e3dc9 ActivityRecord{f4e89d0 u0 com.byd.carplay.ui/.VideoActivity t13}}}]
    mStackId=39
      taskId=6
        appTokens=[AppWindowToken{318bbf5 token=Token{3d0b52c ActivityRecord{8ea84df u0 vn.vietmap.live/.MainActivity t6}}}]
""".trimIndent()
        val am = StackParse.parse(
            "Stack id=39 bounds=[0,0][1920,720] displayId=1 userId=0\n" +
                "  taskId=6: vn.vietmap.live/.MainActivity bounds=[0,0][1920,720] userId=0 visible=true topActivity=ComponentInfo{vn.vietmap.live/vn.vietmap.live.MainActivity}"
        )
        assertTrue(WmParse.orphanStacksOn(wm, am, 1).isEmpty(), "stack đang chờ gỡ không phải hỏng")
    }

    @Test
    fun `van bat duoc mo coi THAT khi du dieu kien`() {
        // đối chứng: bỏ hết lọc mới mà vẫn phải bắt được ca thật của diag-0722-073807
        val am = StackParse.parse(AM_CHI_CO_MAN_GIUA)
        assertEquals(setOf(9, 39), WmParse.orphanStacksOn(WM_HAI_STACK_TREN_CUM, am, 1),
            "ca hỏng thật vẫn phải bắt được — lọc thêm không được làm mù hẳn")
    }

    @Test
    fun `khong tinh nham app o mFocusedApp ngoai vung token`() {
        // Nguyên văn từ diag-0722-074736: mFocusedApp nằm ở block DisplayPolicy, KHÔNG phải stack trên cụm.
        val wm = """
  Display: mDisplayId=1
  Application tokens in top down Z order:
    mStackId=39
      taskId=6
        appTokens=[AppWindowToken{318bbf5 token=Token{3d0b52c ActivityRecord{8ea84df u0 vn.vietmap.live/.MainActivity t6}}}]
  DisplayPolicy    mCarDockEnablesAccelerometer=true
    mFocusedApp=Token{46c0edc ActivityRecord{1b9774f u0 com.byd.carplay.ui/.VideoActivity t-1 f}}
""".trimIndent()
        val pkgs = WmParse.pkgsOn(wm, 1)
        assertTrue(pkgs.contains("vn.vietmap.live"), "app THẬT trên cụm phải còn")
        assertFalse(pkgs.contains("com.byd.carplay.ui"), "mFocusedApp ngoài vùng token KHÔNG được tính là ở trên cụm")
    }

    // ── isVisibleOn: OCCLUDE-VERIFY (T7 · D5 U3) — khoá cả nhánh mặc-định-an-toàn ──

    /** Dựng 1 block token trên display [vd] cho [pkg] với hậu tố isVisible tuỳ chọn (mô phỏng dump thật/fake). */
    private fun tokenBlock(vd: Int, pkg: String, task: Int, visSuffix: String) = """
  Display: mDisplayId=$vd
  Application tokens in top down Z order:
    mStackId=$task
      taskId=$task
        appTokens=[AppWindowToken{a1 token=Token{a2 ActivityRecord{a3 u0 $pkg/.Main t$task}}}]$visSuffix
    DockedStackDividerController
""".trimIndent()

    @Test
    fun `isVisibleOn - token isVisible true tren vd`() {
        val wm = tokenBlock(1, "vn.vietmap.live", 6, " isVisible=true")
        assertTrue(WmParse.isVisibleOn(wm, "vn.vietmap.live", 1), "isVisible=true → visible")
    }

    @Test
    fun `isVisibleOn - token isVisible false la occluded`() {
        val wm = tokenBlock(1, "vn.vietmap.live", 6, " isVisible=false")
        assertFalse(WmParse.isVisibleOn(wm, "vn.vietmap.live", 1), "isVisible=false → occluded (không visible)")
    }

    @Test
    fun `isVisibleOn - pkg vang mat khoi vd la false`() {
        val wm = tokenBlock(1, "app.other", 6, " isVisible=true")
        assertFalse(WmParse.isVisibleOn(wm, "vn.vietmap.live", 1), "pkg không có token trên vd → false (vắng mặt/occluded hẳn)")
    }

    @Test
    fun `isVisibleOn - MAC DINH AN TOAN khi token co mat ma khong doc duoc co (format la)`() {
        // Đời xe/dump khác KHÔNG có `isVisible=` → PHẢI coi như VISIBLE (true) để caller KHÔNG gentle-move
        // một task chưa-chắc-vô-hình (chống tái tạo orphan v0.67). Đây là mặc-định-an-toàn của occlude-verify.
        val wm = tokenBlock(1, "vn.vietmap.live", 6, "")   // KHÔNG có isVisible=
        assertTrue(WmParse.isVisibleOn(wm, "vn.vietmap.live", 1),
            "token có mặt trên vd nhưng KHÔNG đọc được cờ → mặc định VISIBLE (an toàn, KHÔNG gentle-move)")
    }

    @Test
    fun `isVisibleOn - vd khong hop le la false`() {
        val wm = tokenBlock(1, "vn.vietmap.live", 6, " isVisible=true")
        assertFalse(WmParse.isVisibleOn(wm, "vn.vietmap.live", 0), "vd<1 → false")
    }

    @Test
    fun `isVisibleOn - target phu old (occlusion) - target visible old invisible`() {
        // 2 token trên VD: target isVisible=true (trên đỉnh), old isVisible=false (bị phủ).
        val wm = """
  Display: mDisplayId=1
  Application tokens in top down Z order:
    mStackId=90
      taskId=90
        appTokens=[AppWindowToken{a1 token=Token{a2 ActivityRecord{a3 u0 app.revanced.android.apps.maps/.Main t90}}}] isVisible=true
    mStackId=6
      taskId=6
        appTokens=[AppWindowToken{b1 token=Token{b2 ActivityRecord{b3 u0 vn.vietmap.live/.Main t6}}}] isVisible=false
    DockedStackDividerController
""".trimIndent()
        assertTrue(WmParse.isVisibleOn(wm, "app.revanced.android.apps.maps", 1), "target trên đỉnh → visible")
        assertFalse(WmParse.isVisibleOn(wm, "vn.vietmap.live", 1), "old bị target phủ → invisible → gentle-return an toàn")
    }
}
