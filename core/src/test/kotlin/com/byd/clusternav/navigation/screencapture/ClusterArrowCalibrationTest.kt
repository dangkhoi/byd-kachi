package com.byd.clusternav.navigation.screencapture

import com.byd.clusternav.navigation.ArrayPixelFrame
import com.byd.clusternav.navigation.ManeuverSignature
import com.byd.clusternav.navigation.PixelFrame
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * KHOÁ BÀI HỌC B3.23/B3.6 (đo THẬT 2026-08-21, emulator ở ĐÚNG kích thước CỤM 1920×720, WazeMod đang dẫn):
 *
 * Fixture là ẢNH CHỤP NGUYÊN VĂN màn hình 1920×720 lúc Waze dẫn "750 m · Trần Hưng Đạo" với glyph mũi tên
 * RẼ PHẢI ở banner trên-trái (§10: fixture lấy nguyên văn từ dump thật, không dựng tay).
 *
 * Test khoá HAI điều, cả hai đều là lỗi CÓ THẬT:
 *
 *  1. [rect 960×720 cũ áp lên cụm 1920×720 ⇒ CÂM] — trước bản vá, bảng [CaptureCalibration] chỉ có entry
 *     `(ARROW, 960, 720)` (đo trên emulator 960×720) nên ở cụm THẬT (1920×720) rơi về seed OpenBYD 182×80,
 *     chữ ký ~4 bit < MIN_SIG_BITS ⇒ classify null ⇒ **kênh mũi tên câm trên xe**. Không ai phát hiện vì
 *     off-car chưa từng chạy ở 1920×720.
 *
 *  2. [crop TIGHT bbox-mực cũng SAI] — B3.9 kết luận "crop sát glyph" từ quan sát 960×720, nhưng 38 template
 *     ([com.byd.clusternav.navigation.ManeuverRegistry]) KHÔNG phải bbox-mực: đo cả 38 mục thì mực luôn kết
 *     thúc ở hàng 13/15 (row1=13 cho 38/38) — tức template là **khung vẽ có lề**, không phải mực tight. Crop
 *     tight làm glyph lấp đầy 15×15 ⇒ lệch template ⇒ Hamming 44 > 18 ⇒ null.
 */
class ClusterArrowCalibrationTest {

    private companion object {
        /**
         * Hai fixture 1920×1080 lưu ở dạng **dải trên 1920×320** (pixel NGUYÊN VĂN, chỉ cắt bớt phần dưới là
         * bản đồ, không đụng gì tới banner) — giữ repo nhẹ. Toạ độ KHÔNG đổi: khung mũi tên nằm ở y 50..163
         * nên nằm trọn trong dải; dải cao 320 để rect seed OpenBYD (đáy y=298) cũng nằm trọn → crop trong test giống HỆT trên khung đầy đủ. Tra rect vẫn dùng [DisplayGeometry] 1920×1080 THẬT vì bảng khoá theo kích
         * thước MÀN, không theo kích thước fixture.
         */
        const val STRIP_H = 320
    }

    private fun frame(): PixelFrame = frameOf("/diagnostics/waze-nav-cluster-1920x720-2026-08-21.png", 1920, 720)

    private fun frameOf(res: String, w: Int, h: Int): PixelFrame {
        val url = javaClass.getResource(res)
        assertNotNull(url, "thiếu fixture $res")
        val img = ImageIO.read(url)
        assertEquals(w, img.width, "fixture sai chiều rộng")
        assertEquals(h, img.height, "fixture sai chiều cao")
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return ArrayPixelFrame(img.width, img.height, px)
    }

    private fun crop(src: PixelFrame, r: CropRect): PixelFrame {
        val all = src.argb()!!
        val w = r.right - r.left
        val h = r.bottom - r.top
        val out = IntArray(w * h)
        for (y in 0 until h) {
            System.arraycopy(all, (r.top + y) * src.width + r.left, out, y * w, w)
        }
        return ArrayPixelFrame(w, h, out)
    }

    @Test
    fun `cum 1920x720 co entry rect rieng, khong roi ve seed OpenBYD`() {
        val geom = DisplayGeometry(1920, 720)
        val r = CaptureCalibration.fixedBounds(CaptureTarget.ARROW, geom)
        assertEquals(CaptureCalibration.WAZE_ARROW_BANNER_D240, r, "cụm phải dùng rect đo thật @1920×720")
    }

    @Test
    fun `rect cum 1920x720 classify dung mui ten re PHAI cua Waze`() {
        val f = frame()
        val r = CaptureCalibration.fixedBounds(CaptureTarget.ARROW, DisplayGeometry(1920, 720))!!
        val m = ManeuverSignature.classifyDetailed(crop(f, r))
        assertEquals("maneuver_turn_normal_right", m.name, "khung mũi tên cụm phải khớp glyph rẽ phải")
        assertNotNull(m.amap, "khớp được thì phải ra mã AMAP để đẩy lên cụm/HUD")
    }

    @Test
    fun `rect 960x720 cu ap len cum 1920x720 thi CAM (hoi quy B3_23)`() {
        val f = frame()
        val m = ManeuverSignature.classifyDetailed(crop(f, CaptureCalibration.WAZE_ARROW_WAZEMOD_960x720))
        assertNull(m.amap, "rect 960×720 áp nhầm lên cụm phải KHÔNG cho ra mã (đây là lỗi câm on-car)")
    }

    @Test
    fun `seed OpenBYD ap len cum 1920x720 thi CAM`() {
        val f = frame()
        val m = ManeuverSignature.classifyDetailed(crop(f, CaptureCalibration.WAZE_ARROW_OPENBYD))
        assertNull(m.amap, "seed OpenBYD ở geometry cụm phải KHÔNG cho ra mã")
    }

    // ── Màn CHÍNH 1920×1080 (đo thật 2026-08-22, cùng WazeMod dẫn, cùng density 240) ────────────────────
    // Khoá phát hiện: banner Waze neo theo dp ⇒ CÙNG rect phục vụ cả cụm lẫn màn chính. Nếu ai đó tách đôi
    // hằng số hoặc chỉnh một bên, test này gãy.

    @Test
    fun `man chinh 1920x1080 dung CUNG rect voi cum (banner Waze neo theo dp)`() {
        assertEquals(
            CaptureCalibration.fixedBounds(CaptureTarget.ARROW, DisplayGeometry(1920, 720)),
            CaptureCalibration.fixedBounds(CaptureTarget.ARROW, DisplayGeometry(1920, 1080)),
            "cùng density thì khung banner Waze không đổi theo chiều cao màn",
        )
    }

    @Test
    fun `rect man chinh 1920x1080 classify dung mui ten re PHAI`() {
        val f = frameOf("/diagnostics/waze-nav-main-1920x1080-2026-08-22.png", 1920, STRIP_H)
        val r = CaptureCalibration.fixedBounds(CaptureTarget.ARROW, DisplayGeometry(1920, 1080))!!
        val m = ManeuverSignature.classifyDetailed(crop(f, r))
        assertEquals("maneuver_turn_normal_right", m.name, "khung mũi tên màn chính phải khớp glyph rẽ phải")
        assertNotNull(m.amap, "khớp được thì phải ra mã AMAP")
    }

    @Test
    fun `seed OpenBYD ap len man chinh 1920x1080 thi CAM (hoi quy CASE 1_2)`() {
        val f = frameOf("/diagnostics/waze-nav-main-1920x1080-2026-08-22.png", 1920, STRIP_H)
        val m = ManeuverSignature.classifyDetailed(crop(f, CaptureCalibration.WAZE_ARROW_OPENBYD))
        assertNull(m.amap, "trước bản vá, CASE 1/2 rơi seed này và câm")
    }

    // ── GIỚI HẠN ĐÃ BIẾT: banner Waze layout 1 DÒNG ──────────────────────────────────────────────────────
    // Đo thật 2026-08-22: khi tên đường ngắn, Waze rút banner còn MỘT dòng ("140 m Quang Trung") — hộp thấp
    // hơn (93px thay vì 150px) và glyph nhỏ hơn + dịch lên (ink 46×53 tại (72,56) thay vì 64×66 tại (83,73)).
    // Rect cố định KHÔNG phủ được ca này.
    //
    // Test khoá HÀNH VI SUY GIẢM chứ không khoá "đúng": phải trả **null** (im lặng), TUYỆT ĐỐI không được ra
    // một hướng rẽ SAI — sai hướng trên cụm/HUD nguy hiểm hơn hẳn không có gì. `MIN_SIG_BITS` + `MAX_HAMMING`
    // là hai chốt giữ tính chất này; nếu ai nới hai ngưỡng đó, test này gãy trước khi lên xe.
    @Test
    fun `banner 1 dong chua phu duoc thi IM LANG, khong ra huong SAI`() {
        val f = frameOf("/diagnostics/waze-nav-main-1920x1080-compact-2026-08-22.png", 1920, STRIP_H)
        val r = CaptureCalibration.fixedBounds(CaptureTarget.ARROW, DisplayGeometry(1920, 1080))!!
        val m = ManeuverSignature.classifyDetailed(crop(f, r))
        assertNull(m.amap, "banner 1 dòng: phải im lặng, không được đoán bừa một hướng")
    }
}
