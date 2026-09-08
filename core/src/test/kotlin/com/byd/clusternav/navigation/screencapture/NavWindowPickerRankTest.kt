package com.byd.clusternav.navigation.screencapture

import com.byd.clusternav.navigation.NavApps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá [NavWindowPicker.rank] — danh sách ứng viên cửa sổ nav, dùng cho ca **cài đồng thời Waze zin +
 * WazeMod** (owner 08-22: chỉ MỘT bản dẫn tại một thời điểm, bản kia nằm im).
 *
 * Điểm cốt lõi: xếp hạng chỉ là PHỎNG ĐOÁN. Bằng chứng "đang dẫn" là đọc được `navBarDistance`, nên caller
 * phải thử lần lượt — vì thế thứ tự phải **tất định**, không phụ thuộc thứ tự hệ thống trả window.
 */
class NavWindowPickerRankTest {

    private fun win(pkg: String, w: Int, h: Int, focused: Boolean = false, display: Int = 0) =
        NavWindowPicker.WinInfo(pkg, NavWindowPicker.TYPE_APPLICATION, CropRect(0, 0, w, h), display, focused)

    private val zin = "com.waze"
    private val mod = "com.chisadin.wazemod"

    @Test
    fun `xep hang theo dien tich, cua so to hon truoc`() {
        val r = NavWindowPicker.rank(listOf(win(mod, 960, 720), win(zin, 1920, 1080)), (NavApps.GMAPS + NavApps.WAZE + NavApps.VIETMAP))
        assertEquals(listOf(zin, mod), r.map { it.pkg })
    }

    /**
     * HAI cửa sổ BẰNG NHAU (ca thật: cả hai bản Waze cùng full-screen). Kết quả phải TẤT ĐỊNH bất kể hệ
     * thống trả về theo thứ tự nào — nếu không, mỗi nhịp enum có thể ra một app khác ⇒ cự ly nhảy qua nhảy lại.
     */
    @Test
    fun `hoa dien tich thi thu tu VAN tat dinh`() {
        val a = NavWindowPicker.rank(listOf(win(mod, 1920, 1080), win(zin, 1920, 1080)), (NavApps.GMAPS + NavApps.WAZE + NavApps.VIETMAP))
        val b = NavWindowPicker.rank(listOf(win(zin, 1920, 1080), win(mod, 1920, 1080)), (NavApps.GMAPS + NavApps.WAZE + NavApps.VIETMAP))
        assertEquals(a.map { it.pkg }, b.map { it.pkg }, "đảo thứ tự đầu vào mà kết quả đổi = không tất định")
    }

    /** Cửa sổ đang focus thắng khi diện tích bằng nhau. */
    @Test
    fun `bang dien tich thi cua so dang focus thang`() {
        val r = NavWindowPicker.rank(
            listOf(win(zin, 1920, 1080, focused = false), win(mod, 1920, 1080, focused = true)),
            (NavApps.GMAPS + NavApps.WAZE + NavApps.VIETMAP),
        )
        assertEquals(mod, r.first().pkg)
    }

    /** Trả về MỌI ứng viên, không chỉ một — để caller thử tiếp khi ứng viên đầu không đọc được gì. */
    @Test
    fun `tra ve MOI ung vien nav`() {
        val r = NavWindowPicker.rank(
            listOf(win(zin, 1920, 1080), win(mod, 960, 720), win(NavApps.VIETMAP_LIVE, 800, 600)),
            (NavApps.GMAPS + NavApps.WAZE + NavApps.VIETMAP),
        )
        assertEquals(3, r.size)
        assertTrue(r.map { it.pkg }.containsAll(listOf(zin, mod, NavApps.VIETMAP_LIVE)))
    }

    /** App ngoài roster bị loại. */
    @Test
    fun `app ngoai roster bi loai`() {
        val r = NavWindowPicker.rank(listOf(win("com.some.other", 1920, 1080), win(mod, 100, 100)), (NavApps.GMAPS + NavApps.WAZE + NavApps.VIETMAP))
        assertEquals(listOf(mod), r.map { it.pkg })
    }

    /**
     * B3.58 FIX 2a — ĐỌC BẤT KỂ DISPLAY. Cửa sổ nav bị CHIẾU LÊN CỤM (displayId=1) và KHÔNG focus vẫn phải
     * được chọn: đường đọc TEXT a11y (cự-ly/đường/ETA → NavViewIdSource → HUD) phải chạy dù app ở cụm hay màn
     * chính. `rank` KHÔNG được lọc/hạ bậc theo display; Pick phải GIỮ displayId=1 để transport biết chụp cụm.
     *
     * Khoá logic đọc display-agnostic OFF-CAR. Việc chụp pixel thật của display phụ là PHỤ THUỘC XE (emulator
     * không host/chụp được display phụ — B3.26); ở đây chỉ khoá "cửa sổ cụm vẫn được nhận diện + xếp hạng".
     */
    @Test
    fun `nav window tren CUM (display 1) khong focus VAN duoc chon (B3_58 FIX 2a)`() {
        val r = NavWindowPicker.rank(
            listOf(win(NavApps.VIETMAP_LIVE, 1920, 720, focused = false, display = 1)),
            (NavApps.GMAPS + NavApps.WAZE + NavApps.VIETMAP),
        )
        assertEquals(1, r.size)
        assertEquals(NavApps.VIETMAP_LIVE, r.first().pkg)
        assertEquals(1, r.first().displayId, "Pick phải giữ displayId=1 (cụm) — đường đọc display-agnostic")
    }

    /**
     * B3.58 FIX 2a — cửa sổ cụm cạnh tranh với cửa sổ nav màn chính: xếp hạng theo DIỆN TÍCH (không theo
     * display), và cả hai đều được trả về để caller thử lần lượt. Một cửa sổ trên cụm KHÔNG bị loại chỉ vì
     * nó không nằm trên display 0.
     */
    @Test
    fun `nav window cum va man chinh — ca hai deu duoc xep hang (khong loc theo display)`() {
        val r = NavWindowPicker.rank(
            listOf(
                win(NavApps.VIETMAP_LIVE, 1920, 720, focused = false, display = 1),   // cụm
                win(zin, 1200, 600, focused = true, display = 0),                       // màn chính, nhỏ hơn
            ),
            (NavApps.GMAPS + NavApps.WAZE + NavApps.VIETMAP),
        )
        assertEquals(2, r.size, "cả hai cửa sổ nav (cụm + chính) đều phải có mặt")
        // VietMap trên cụm to hơn ⇒ đứng trước dù không focus và ở display khác 0.
        assertEquals(NavApps.VIETMAP_LIVE, r.first().pkg)
        assertEquals(1, r.first().displayId)
    }
}
