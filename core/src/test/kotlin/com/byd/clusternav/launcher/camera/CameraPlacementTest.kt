package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * ═══ 2.93 · ĐẶT CỬA SỔ theo vị trí kéo-thả + cỡ riêng camera — hình học THẬT (spec `kachi-293-cam.html` §4.3) ═══════
 *
 * Số màn chính lấy đúng display của Seal ([ĐO logcat 27/09] `vùng=495x495` = 0,5 × 990); khung HAL 5120×960, dải gương
 * 1280×960 (4:3). Bài khoá: (1) mặc định KHÔNG đi đường mới; (2) đường mới ở mặc định trùng phép 2.73 (±1 px làm tròn);
 * (3) cửa sổ không bao giờ ra khỏi vùng cho phép (dưới thanh trên / trong dải cụm), mọi cỡ × mọi vị trí × mọi xoay.
 */
class CameraPlacementTest {

    private val w = 1920
    private val h = 990
    private val strip1 = floatArrayOf(0.25f, 0f, 0.5f, 1f)
    private fun stream(rot: Int = 0, crop: FloatArray? = strip1) = CameraPlacement.Stream(5120, 960, crop, rot)

    /** Phép đặt chỗ 2.73–2.92 của màn chính, chép NGUYÊN VĂN từ `overlayLayoutParams` + `box()` (toạ độ tuyệt đối). */
    private fun legacy(dw: Int, dh: Int, corner: String, s: CameraPlacement.Stream): Pair<Int, Int> {
        val side = (dh * CameraPlacement.SQUARE_RATIO).toInt()
        val x0 = (dw * CameraPlacement.SIDE_MARGIN_RATIO).toInt()
        val y0 = (dh * CameraPlacement.MAIN_TOP_RATIO).toInt()
        val f = CameraOverlayFrame.fit(s.w, s.h, s.crop, s.rotationDeg, side, side)
        val dx = x0 + ((side - f.w) / 2).coerceAtLeast(0)
        val x = if (corner == CameraSignalPolicy.CORNER_TOP_LEFT) dx else dw - dx - f.w
        return x to y0 + ((side - f.h) / 2).coerceAtLeast(0)
    }

    @Test fun `mac dinh khong di duong moi`() {
        assertFalse(CameraPlacement.custom(null, CameraCamConfig.SIZE_DEFAULT), "chưa kéo + 100 % ⇒ đường 2.73–2.92 nguyên văn")
        assertTrue(CameraPlacement.custom(CameraCamConfig.place(500, 500), CameraCamConfig.SIZE_DEFAULT))
        assertTrue(CameraPlacement.custom(null, 105))
    }

    @Test fun `man chinh - goc mac dinh o 100 phan tram trung phep 2 73 sai so lam tron 1 px`() {
        for ((dw, dh) in listOf(1920 to 990, 1920 to 1080, 1280 to 720, 1024 to 600))
            for (corner in listOf(CameraSignalPolicy.CORNER_TOP_LEFT, CameraSignalPolicy.CORNER_TOP_RIGHT))
                for (rot in listOf(0, -90, 90, 180)) for (crop in listOf(strip1, null, floatArrayOf(0.3f, 0.2f, 0.4f, 0.8f))) {
                    val s = stream(rot, crop)
                    val win = CameraPlacement.main(dw, dh, corner, null, CameraCamConfig.SIZE_DEFAULT, s)
                    val (lx, ly) = legacy(dw, dh, corner, s)
                    assertTrue(abs(win.x - lx) <= 1 && abs(win.y - ly) <= 1, "$dw×$dh $corner rot=$rot: ${win.x},${win.y} vs $lx,$ly")
                }
        // Đúng số Seal: dải 4:3 ⇒ 495×371 tại (57,200) góc trái · (1368,200) góc phải.
        val tl = CameraPlacement.main(w, h, CameraSignalPolicy.CORNER_TOP_LEFT, null, 100, stream())
        assertEquals(listOf(57, 200, 495, 371), listOf(tl.x, tl.y, tl.w, tl.h))
        val tr = CameraPlacement.main(w, h, CameraSignalPolicy.CORNER_TOP_RIGHT, null, 100, stream())
        assertEquals(listOf(1368, 200, 495, 371), listOf(tr.x, tr.y, tr.w, tr.h))
    }

    @Test fun `man chinh - co phong vung vuong, khung dung ti le anh`() {
        val big = CameraPlacement.main(w, h, CameraSignalPolicy.CORNER_TOP_LEFT, null, 150, stream())
        assertEquals(743, big.w, "150 % × 495 = 742,5 ⇒ 743 (làm tròn lên), khung 4:3 bề ngang quyết")
        assertEquals(557, big.h)
        assertEquals(57, big.x, "to ra vẫn dính góc trái với lề 3 %")
        val small = CameraPlacement.main(w, h, CameraSignalPolicy.CORNER_TOP_RIGHT, null, 50, stream())
        assertEquals(248, small.w)
        assertEquals(w - 57, small.x + small.w, "nhỏ lại vẫn dính góc phải với lề 3 %")
    }

    @Test fun `khong bao gio ra khoi vung cho phep - moi co, moi vi tri, moi xoay, hai display`() {
        val band = CameraClusterBand.band(1920, 720, ClusterBandSpec.SEAL_DL3)
        val main = CameraPlacement.mainRegion(w, h)
        assertEquals((h * CameraPlacement.MAIN_TOP_RATIO).toInt(), main.y0, "không đè thanh trên (2.35 R2)")
        val places = listOf(null) + listOf(0, 137, 500, 863, 1000).flatMap { x -> listOf(0, 500, 1000).map { y -> CameraCamConfig.place(x, y) } }
        for (size in listOf(50, 75, 100, 125, 150)) for (p in places) for (rot in listOf(0, -90, 90, 180))
            for (corner in listOf(CameraSignalPolicy.CORNER_TOP_LEFT, CameraSignalPolicy.CORNER_TOP_RIGHT)) {
                val m = CameraPlacement.main(w, h, corner, p, size, stream(rot))
                assertTrue(m.x >= main.x0 && m.y >= main.y0 && m.x + m.w <= main.x1 && m.y + m.h <= main.y1, "màn chính $size $p $rot: $m")
                assertTrue(m.w >= 1 && m.h >= 1)
                for (round in listOf(false, true)) {
                    val c = CameraPlacement.cluster(band, round, corner, p, size, stream(rot))
                    assertTrue(c.x >= band.x0 && c.y >= band.y0 && c.x + c.w <= band.x1 && c.y + c.h <= band.y1, "cụm $size $p $rot $round: $c")
                    if (round) assertEquals(c.w, c.h, "tròn ⇒ cửa sổ vuông (oval của chữ nhật là elip)")
                }
            }
    }

    @Test fun `vi tri keo tha - tam dung cho, mep thi dinh mep`() {
        val region = CameraPlacement.mainRegion(w, h)
        val mid = CameraPlacement.main(w, h, CameraSignalPolicy.CORNER_TOP_LEFT, CameraCamConfig.place(500, 500), 100, stream())
        assertTrue(abs(mid.cx - (region.x0 + region.w / 2)) <= 1 && abs(mid.cy - (region.y0 + region.h / 2)) <= 1, "$mid")
        val corner = CameraPlacement.main(w, h, CameraSignalPolicy.CORNER_TOP_RIGHT, CameraCamConfig.place(0, 0), 100, stream())
        assertEquals(region.x0, corner.x, "kéo ra mép ⇒ dính mép, không lòi")
        assertEquals(region.y0, corner.y)
        val far = CameraPlacement.main(w, h, CameraSignalPolicy.CORNER_TOP_LEFT, CameraCamConfig.place(1000, 1000), 100, stream())
        assertEquals(region.x1, far.x + far.w)
        assertEquals(region.y1, far.y + far.h)
    }

    @Test fun `cum - goc mac dinh dinh dau dai, giua theo chieu cao`() {
        val band = CameraClusterBand.band(1920, 720, ClusterBandSpec.SEAL_DL3)
        val l = CameraPlacement.cluster(band, false, CameraSignalPolicy.CORNER_TOP_LEFT, null, 80, stream())
        assertEquals(band.x0, l.x)
        val r = CameraPlacement.cluster(band, false, CameraSignalPolicy.CORNER_TOP_RIGHT, null, 80, stream())
        assertEquals(band.x1, r.x + r.w)
        assertTrue(abs((l.y + l.h / 2) - (band.y0 + band.h / 2)) <= 1)
        val full = CameraPlacement.cluster(band, false, CameraSignalPolicy.CORNER_TOP_LEFT, null, 100, stream())
        assertEquals(band.h, full.h, "100 % ⇒ cao trọn dải như đường 2.79 (dải 4:3 ≤ nửa dải)")
    }

    /**
     * Ô kéo-thả ở Cài đặt vẽ bằng [CameraPlacement.Model] — phải là CÙNG phép mà overlay dùng (không bản sao thứ hai), kéo
     * giữ nguyên cỡ và không lòi khỏi vùng, và chỗ lưu khứ hồi về đúng cửa sổ (thả tay rồi mở lại thấy y chỗ cũ).
     */
    @Test fun `mo hinh o keo tha - cung phep voi overlay, keo giu co, luu khu hoi`() {
        val band = CameraClusterBand.band(1920, 720, ClusterBandSpec.SEAL_DL3)
        val models = listOf(
            CameraPlacement.Model(w, h, false, CameraPlacement.mainRegion(w, h), false, CameraSignalPolicy.CORNER_TOP_RIGHT, stream()),
            CameraPlacement.Model(1920, 720, true, band, false, CameraSignalPolicy.CORNER_TOP_LEFT, stream()),
            CameraPlacement.Model(1920, 720, true, band, true, CameraSignalPolicy.CORNER_TOP_RIGHT, stream(rot = 90)),
        )
        for (m in models) for (size in listOf(50, 100, 150)) {
            val p = CameraCamConfig.place(300, 600)
            val expected = if (m.onCluster) {
                CameraPlacement.cluster(m.region, m.round, m.corner, p, size, m.stream)
            } else {
                CameraPlacement.main(m.displayW, m.displayH, m.corner, p, size, m.stream)
            }
            assertEquals(expected, m.window(p, size), "ô kéo-thả phải vẽ ĐÚNG cửa sổ overlay sẽ dựng: $m $size")
            val start = m.window(null, size)
            for ((cx, cy) in listOf(0 to 0, m.region.x0 + m.region.w / 2 to m.region.y0 + m.region.h / 2, 99999 to 99999)) {
                val moved = m.dragTo(start, cx, cy)
                assertEquals(start.w to start.h, moved.w to moved.h, "kéo không đổi cỡ")
                val r = m.region
                assertTrue(moved.x >= r.x0 && moved.y >= r.y0 && moved.x + moved.w <= r.x1 && moved.y + moved.h <= r.y1, "$moved")
            }
            // Thả ở giữa vùng ⇒ lưu ⇒ đọc lại đúng cửa sổ (±1 px làm tròn phần nghìn).
            val mid = m.dragTo(start, m.region.x0 + m.region.w / 2, m.region.y0 + m.region.h / 2)
            val back = m.window(m.placeOf(mid), size)
            assertTrue(abs(back.x - mid.x) <= 1 && abs(back.y - mid.y) <= 1, "$mid → ${m.placeOf(mid)} → $back")
        }
    }

    @Test fun `phan nghin va toa do tuyet doi khu hoi`() {
        val region = CameraPlacement.mainRegion(w, h)
        for (x in listOf(0, 1, 333, 500, 999, 1000)) for (y in listOf(0, 250, 1000)) {
            val p = CameraCamConfig.place(x, y)
            val (cx, cy) = CameraPlacement.centreOf(region, p)
            val back = CameraPlacement.placeOf(region, cx, cy)
            assertTrue(abs(back.x - x) <= 1 && abs(back.y - y) <= 1, "$p → ($cx,$cy) → $back")
        }
        assertEquals(CameraCamConfig.Place(0, 0), CameraPlacement.placeOf(region, -500, -500), "kẹp")
        assertEquals(CameraCamConfig.Place(1000, 1000), CameraPlacement.placeOf(region, 99999, 99999))
    }
}
