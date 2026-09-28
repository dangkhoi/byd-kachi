package com.byd.clusternav.modules.clustercast

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá cổng an toàn của nấc chữa đắt nhất (A11Y-BIND-STUCK, 2026-09-28): chỉ được tự giết tiến trình launcher
 * khi KHÔNG có app khách nào đang hiện ở chỗ mà cú giết làm hỏng — màn chính, hoặc một Ô (màn ảo do CHÍNH
 * launcher tạo, chết theo tiến trình).
 *
 * ## ⚠ Bài này đã ĐỔI KẾT LUẬN ở lượt soát senior 2026-09-28
 * Bản đầu khoá luật "display ≥ 1 là cụm nên không tính" và vì thế **khẳng định là AN TOÀN đúng cái ca đã sinh
 * ra mảng đen**: fixture "sạch" của nó đặt Google Maps ở `displayId=1` rồi assert cổng MỞ. Hai phép đo trong
 * repo bác bỏ tiền đề đó:
 *  • [ĐO xe 2026-09-15] (`DisplayParse.ownedVirtualDisplayIds` KDoc · `ClusterDisplayResolver` KDoc) sau reboot
 *    **display 1 = `kachi-slot-0`** — màn ảo của CHÍNH launcher; cụm là **display 2**
 *    (`fission_bg_xdjaVirtualSurface`, chủ `com.xdja.containerservice`).
 *  • [ĐO xe 2026-09-18] (`.kiro/steering/project-context.md`) Google Maps chạy trong một Ô Kachi nằm ở
 *    **display 5**.
 * ⇒ app khách trên display ≥ 1 thường là app trong Ô của mình, tức ĐÚNG ca cần chặn. Cụm được miễn vì màn ảo
 * cụm KHÔNG do launcher tạo (không có trong tập ô) — miễn theo chủ sở hữu thật, không theo id đoán trước.
 */
class StackParseGuestVisibleTest {

    private val self = "com.byd.launcher"

    /** Ô của launcher trên xe: [ĐO 2026-09-15] `kachi-slot-0` = display 1; [ĐO 2026-09-18] ô chứa GMaps = display 5. */
    private val ownSlots = setOf(1, 5)

    /** Nguyên văn xe 2026-09-28: YouTube mồ côi trên màn chính, bản đồ vẫn sống trên cụm (display 2). */
    private val dumpDirty = """
        Stack id=97 bounds=[0,0][1920,720] displayId=2 userId=0
          taskId=90: com.google.android.apps.maps/com.google.android.maps.MapsActivity bounds=[20,60][1920,560] userId=0 visible=true
        Stack id=96 bounds=[0,0][1920,720] displayId=2 userId=0
          taskId=97: com.byd.launcher/com.byd.clusternav.modules.clustercast.ClusterBlackActivity bounds=[0,0][1920,720] userId=0 visible=true
        Stack id=95 bounds=[0,0][1920,1080] displayId=0 userId=0
          taskId=96: com.google.android.youtube/com.google.android.youtube.app.honeycomb.Shell bounds=[37,136][1883,1043] userId=0 visible=true
        Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0
          taskId=11: com.byd.launcher/com.byd.clusternav.launcher.KachiHome bounds=[0,0][1920,1080] userId=0 visible=true
    """.trimIndent()

    /** Màn chính chỉ còn nhà; cụm (display 2) vẫn chiếu bản đồ — đúng hình dạng lúc xe vừa thức dậy. */
    private val dumpClean = """
        Stack id=97 bounds=[0,0][1920,720] displayId=2 userId=0
          taskId=90: com.google.android.apps.maps/com.google.android.maps.MapsActivity bounds=[20,60][1920,560] userId=0 visible=true
        Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0
          taskId=11: com.byd.launcher/com.byd.clusternav.launcher.KachiHome bounds=[0,0][1920,1080] userId=0 visible=true
        Stack id=92 bounds=[0,0][1920,1080] displayId=0 userId=0
          taskId=92: vn.vietmap.live/vn.vietmap.live.MainActivity bounds=[0,0][1920,1080] userId=0 visible=false
    """.trimIndent()

    /** Ô của launcher đang host Google Maps trên màn ảo `kachi-slot-0` (display 1) — ca sinh mảng đen. */
    private val dumpGuestInSlot = """
        Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0
          taskId=11: com.byd.launcher/com.byd.clusternav.launcher.KachiHome bounds=[0,0][1920,1080] userId=0 visible=true
        Stack id=88 bounds=[0,0][960,540] displayId=1 userId=0
          taskId=88: com.google.android.apps.maps/com.google.android.maps.MapsActivity bounds=[0,0][960,540] userId=0 visible=true
    """.trimIndent()

    @Test
    fun `app nguoi khac dang hien tren man chinh thi CONG DONG`() {
        assertFalse(
            StackParse.noGuestAppVisible(StackParse.parse(dumpDirty), self, ownSlots),
            "YouTube đang hiện trên màn chính ⇒ KHÔNG được tự giết launcher (sẽ thành mảng đen phủ kín nhà)",
        )
    }

    @Test
    fun `app khach trong O cua minh thi CONG DONG`() {
        assertFalse(
            StackParse.noGuestAppVisible(StackParse.parse(dumpGuestInSlot), self, ownSlots),
            "[ĐO xe 2026-09-28] màn ảo của Ô chết theo tiến trình ⇒ cửa sổ app khách rơi lại màn chính, đen kịt. " +
                "[ĐO xe 2026-09-15] display 1 là `kachi-slot-0` của CHÍNH launcher, KHÔNG phải cụm",
        )
    }

    @Test
    fun `man chinh chi con nha thi CONG MO, cum khong tinh`() {
        assertTrue(
            StackParse.noGuestAppVisible(StackParse.parse(dumpClean), self, ownSlots),
            "bản đồ trên CỤM (display 2, màn ảo của com.xdja.containerservice) không chết theo ta ⇒ không tính",
        )
    }

    @Test
    fun `app nguoi khac dang AN thi khong tinh`() {
        val entries = StackParse.parse(dumpClean)
        assertTrue(entries.any { it.pkg == "vn.vietmap.live" && !it.visible }, "fixture có app ẩn")
        assertTrue(StackParse.noGuestAppVisible(entries, self, ownSlots), "task ẩn không phủ lên gì cả")
    }

    @Test
    fun `khong doc duoc thi CONG DONG`() {
        assertFalse(
            StackParse.noGuestAppVisible(emptyList(), self, ownSlots),
            "không đọc nổi `am stack list` ⇒ không khẳng định được là sạch ⇒ tuyệt đối không giết tiến trình",
        )
        assertFalse(
            StackParse.noGuestAppVisible(StackParse.parse(dumpClean), self, null),
            "không đọc nổi `dumpsys display` ⇒ không biết Ô nào là của mình ⇒ cổng ĐÓNG (fail-closed lần hai)",
        )
        assertFalse(
            StackParse.noGuestAppVisible(StackParse.parse(dumpClean), "", ownSlots),
            "không biết gói của chính mình ⇒ mọi app đều 'của người khác' ⇒ cổng ĐÓNG",
        )
    }
}
