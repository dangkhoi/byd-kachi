package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.modules.clustercast.ClusterOverlayDisplay.Candidate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * 2.90 · R10 — display của badge tốc độ / camera trên cụm ([ClusterOverlayDisplay.pick]).
 *
 * Khoá từ dữ liệu thật:
 *  • 06/10 `dumpsys display` (Seal, Kachi 2.89): display 0 · 4 = `fission_bg_xdjaVirtualSurface` (`FLAG_PRESENTATION`) · 8 =
 *    `kachi-slot-0-1791245523172` (`FLAG_PRIVATE`); log `SpeedBadgeOverlay: overlay initialized for display 4 (1920x720)`.
 *  • 06/10 F4: sau khi đổi theme màn ảo cụm thành 9 (`am start --display 9 … ClusterBlackActivity` trong log 08:12).
 *  • 15/09 + máy ảo 05/10: sau khởi động nguội display 1 = `kachi-slot-0` (VD ô của Kachi) — bản cũ `getDisplay(1)` TRƯỚC ⇒ badge lên ô
 *    màn chính. Thử ĐỎ: đổi [ClusterOverlayDisplay.pick] về "id 1 nếu có".
 */
class ClusterOverlayDisplayTest {

    private val main = Candidate(0, "Màn hình tích hợp", isPrivate = false, isPresentation = false)
    private fun fission(id: Int) = Candidate(id, "fission_bg_xdjaVirtualSurface", isPrivate = false, isPresentation = true)
    private fun slot(id: Int) = Candidate(id, "kachi-slot-0-1791245523172", isPrivate = true, isPresentation = false)

    @Test
    fun `xe 06-10 - 0, 4 fission, 8 kachi-slot - chon 4`() {
        assertEquals(4, ClusterOverlayDisplay.pick(listOf(main, fission(4), slot(8)), castLiveId = null))
        assertEquals(4, ClusterOverlayDisplay.pick(listOf(main, fission(4), slot(8)), castLiveId = 4))
    }

    @Test
    fun `display 1 la o kachi-slot (khoi dong nguoi) - KHONG BAO GIO chon 1`() {
        val picked = ClusterOverlayDisplay.pick(listOf(main, slot(1), fission(2)), castLiveId = null)
        assertEquals(2, picked)
        assertNotEquals(1, ClusterOverlayDisplay.pick(listOf(main, slot(1)), castLiveId = 1), "id công bố mà là VD riêng tư ⇒ bỏ")
        assertEquals(-1, ClusterOverlayDisplay.pick(listOf(main, slot(1)), castLiveId = null), "chỉ có ô của Kachi ⇒ không gắn đâu")
    }

    @Test
    fun `man ao dung lai 4 sang 9 - ca hai con trong danh sach - chon 9`() {
        assertEquals(9, ClusterOverlayDisplay.pick(listOf(main, fission(4), slot(8), fission(9)), castLiveId = null))
        assertEquals(9, ClusterOverlayDisplay.pick(listOf(main, fission(4), fission(9)), castLiveId = 9))
        assertEquals(9, ClusterOverlayDisplay.pick(listOf(main, slot(8), fission(9)), castLiveId = 4), "id công bố đã mất ⇒ luật tên")
    }

    @Test
    fun `id cum song cong bo duoc uu tien khi con trong danh sach`() {
        val dl5 = listOf(main, Candidate(2, "fission_bg_XDJAScreenProjection_0", false, true), Candidate(3, "fission_bg_XDJAScreenProjection_1", false, true))
        assertEquals(2, ClusterOverlayDisplay.pick(dl5, castLiveId = null), "DL5 tên khác nhau ⇒ giữ màn đầu như DisplayParse")
        assertEquals(3, ClusterOverlayDisplay.pick(dl5, castLiveId = 3))
    }

    @Test
    fun `may ao - khong co fission - display PRESENTATION khong rieng tu`() {
        val emu = listOf(main, Candidate(2, "Overlay #1", isPrivate = false, isPresentation = true))
        assertEquals(2, ClusterOverlayDisplay.pick(emu, castLiveId = null))
        assertEquals(-1, ClusterOverlayDisplay.pick(listOf(main), castLiveId = null))
    }
}
