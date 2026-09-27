package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraSignalPolicy.CamView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * ═══ R4 · DẢI GIỮA CỦA CỤM — hình học bằng SỐ, off-car ═══════════════════════════════════════════════════════════
 *
 * Số Seal khoá từ phép đo 27/09 (`docs/diagnostics/offcar-2026-09-27/camera-cluster-band.md` §2): mép dưới thanh
 * trên ≈ 130–140, chữ thanh dưới từ ≈ 567, viền kính trái ≈ 121 (đáy) / phải ≈ 1819 (đáy), cột icon hệ thống từ
 * x ≈ 1798. Bài (1) ghim rằng dải nằm **ngoài** mọi vùng đo được đó; (2)–(4) ghim phép đặt và phép thoái.
 */
class CameraClusterBandTest {

    private val B = CameraClusterBand
    private val seal = ClusterBandSpec.SEAL_DL3
    private val band = B.band(1920, 720, seal)

    // ══ (1) DẢI Seal nằm trong 1920×720 và ngoài mọi vùng hệ thống đã đo ═══════════════════════════════════════

    /** [ĐO 27/09] thanh trên tới ≈ 140, thanh dưới từ ≈ 567, kính trái ≈ 121, kính phải ≈ 1819, icon phải từ ≈ 1798. */
    @Test fun `dai Seal nam ngoai thanh tren duoi va vien kinh da do`() {
        assertEquals(CameraClusterBand.Rect(140, 136, 1780, 560), band)
        // [P2 · SOÁT Opus 2026-09-27] Lời nhắc cũ (*"thanh trên đo 130–140 ⇒ không sớm hơn 136"*) tự phản: nếu thanh
        // trên tới 140 thì 136 còn SỚM hơn 4 px. Cái thật sự khoá `136` là một phép đo khác: hàng `y = 136` của
        // framebuffer là hàng ĐẦU TIÊN nhìn thấy trọn (mép trên thanh tìm kiếm gmaps, doc §2) — không phải một lề.
        assertTrue(band.y0 >= 136, "136 = hàng đầu tiên thấy trọn (gmaps, §2); mép dưới thanh trên 130–140 là số khác")
        assertTrue(band.y1 <= 560, "nội dung tới 559 còn thấy; chữ thanh dưới từ 567 ⇒ dải kết thúc ≤ 560")
        assertTrue(band.x0 >= 121 + 15, "viền kính trái ở đáy ≈ 121 (±8) ⇒ lề ≥ 15 px")
        assertTrue(band.x1 <= 1798 - 15, "cột icon hệ thống từ ≈ 1798 (biển 30 / ADAS) ⇒ lề ≥ 15 px")
        assertTrue(CameraClusterBand.Rect(0, 0, 1920, 720).contains(band))
        assertEquals(1640, band.w); assertEquals(424, band.h)
    }

    /** Display khác cỡ tham chiếu (SL6 cụm 1920×800) ⇒ co giãn theo trục, vẫn nằm trong display; chưa đo ⇒ số gốc. */
    @Test fun `dai co gian theo display va khong bao gio ra ngoai`() {
        val sl6 = B.band(1920, 800, seal)
        assertEquals(CameraClusterBand.Rect(140, 151, 1780, 622), sl6, "dọc ×800/720, ngang giữ nguyên")
        assertTrue(CameraClusterBand.Rect(0, 0, 1920, 800).contains(sl6))
        assertEquals(CameraClusterBand.Rect(140, 136, 1780, 560), B.band(0, 0, seal), "chưa đo display ⇒ đúng số tham chiếu")
        val tiny = B.band(200, 100, seal)
        assertTrue(CameraClusterBand.Rect(0, 0, 200, 100).contains(tiny) && tiny.w > 0 && tiny.h > 0, "display nhỏ: kẹp, không suy biến")
    }

    /** Hồ sơ vô lý (dải ra ngoài cỡ tham chiếu, trái ≥ phải) bị từ chối ngay lúc dựng — không đợi tới lúc vẽ. */
    @Test fun `spec vo ly bi tu choi`() {
        assertThrows<IllegalArgumentException> { ClusterBandSpec(1920, 720, 1780, 136, 140, 560, 24) }
        assertThrows<IllegalArgumentException> { ClusterBandSpec(1920, 720, 0, 0, 1921, 720, 24) }
        assertThrows<IllegalArgumentException> { ClusterBandSpec(1920, 720, 0, 600, 1920, 560, 24) }
    }

    // ══ (2) ĐẶT cửa sổ ở đầu trái/phải, cao trọn dải, đúng tỉ lệ, luôn TRONG dải ═════════════════════════════

    private fun place(left: Boolean, crop: FloatArray? = CameraPanoCrop.stripCrop(if (left) 1 else 2), rot: Int = 0, w: Int = 5120, h: Int = 960) =
        B.place(band, atLeft = left, streamW = w, streamH = h, crop = crop, rotationDeg = rot, spec = seal, displayH = 720)

    /** Dải gương trọn (1280×960 = 4:3, ĐỨNG theo F1 [ĐO]) ⇒ 565×424 dính đầu trái (xi-nhan trái) / đầu phải (phải). */
    @Test fun `guong tron 4 3 dinh dau trai hoac phai cua dai`() {
        val l = place(true)
        assertEquals(140, l.x, "đầu TRÁI dải"); assertEquals(136, l.y)
        assertEquals(565, l.w, "424 × 1280/960 = 565,3"); assertEquals(424, l.h, "cao trọn dải")
        assertTrue(l.streamKnown)
        val r = place(false)
        assertEquals(1780, r.x + r.w, "đầu PHẢI dải: mép phải cửa sổ = mép phải dải")
        assertEquals(l.w to l.h, r.w to r.h, "hai bên cùng cỡ")
        // Mép phía xe hướng vào trong: gương trái ở đầu trái ⇒ mép phải (thân xe) về phía giữa; đối xứng bên phải.
        assertTrue(l.x + l.w < r.x, "hai cửa sổ không bao giờ chạm nhau (mỗi bên ≤ nửa dải)")
    }

    /** Mọi tổ hợp (bên × bề rộng × xoay × chưa biết cỡ) ⇒ cửa sổ nằm TRONG dải, không 0 px, không quá nửa dải. */
    @Test fun `moi to hop deu nam trong dai`() {
        val crops = listOf(null, CameraPanoCrop.stripCrop(1), CameraPanoCrop.narrowCrop(1, true), CameraPanoCrop.squareCrop(0.375, 0.1875, 100))
        for (left in listOf(true, false)) for (crop in crops) for (rot in listOf(0, 90, -90, 180)) for (known in listOf(true, false)) {
            val p = if (known) place(left, crop, rot) else place(left, crop, rot, w = 0, h = 0)
            assertTrue(band.contains(p.rect), "left=$left crop=${crop?.toList()} rot=$rot known=$known ⇒ ${p.rect} ra ngoài $band")
            assertTrue(p.w in 1..band.w / 2 && p.h in 1..band.h, "cỡ ${p.w}x${p.h}")
            assertEquals(known, p.streamKnown)
            assertTrue(p.radiusPx in 0..minOf(p.w, p.h) / 2, "bo góc không quá nửa cạnh ngắn")
        }
        // Xoay ±90 ⇒ 3:4 ⇒ 318×424; chưa biết cỡ ⇒ ô vuông 424 (đúng khuôn `CameraOverlayFrame.fit`).
        assertEquals(318 to 424, place(true, rot = -90).let { it.w to it.h })
        assertEquals(424 to 424, place(true, w = 0, h = 0).let { it.w to it.h })
    }

    /**
     * ═══ [P1 · SOÁT Opus 2026-09-27] Hình **CHỮ NHẬT/TRÒN** trên cụm cũng phải nằm trong dải ═══════════════════════
     *
     * Bệnh nó khoá: tới 2.76 chỉ hình *theo cụm* đi qua [CameraClusterBand.place]; hình mặc định (CHỮ NHẬT) trên cụm
     * vẫn dùng vùng vuông đo bằng % chiều cao display ⇒ bắt đầu ở `y = 43` trong khi thanh trên hệ thống phủ tới
     * `y ≈ 136` ([ĐO] doc §2 F6) ⇒ **93/360 px bị che** — và bản 2.76 làm nặng hơn 2.75 (77/495) vì đã sửa metrics
     * sang đúng display cụm. Cửa sổ thật được [CameraOverlayFrame.fit] **căn giữa** trong vùng này (đúng công thức
     * `CameraOverlayView.layoutParams`), nên bài đo luôn cả phép căn giữa ấy.
     */
    @Test fun `vung vuong 2 73 tren cum bi kep vao dai o ca hai goc`() {
        val side = (720 * 0.50f).toInt()   // `CameraOverlayView.SQUARE_RATIO` — cạnh vùng cho phép của đường 2.73
        assertEquals(CameraClusterBand.Rect(140, 136, 500, 496), B.boxIn(band, side, atLeft = true))
        assertEquals(CameraClusterBand.Rect(1420, 136, 1780, 496), B.boxIn(band, side, atLeft = false))
        val crops = listOf(null, CameraPanoCrop.stripCrop(1), CameraPanoCrop.narrowCrop(1, true), CameraPanoCrop.squareCrop(0.375, 0.1875, 100))
        for (atLeft in listOf(true, false)) for (crop in crops) for (rot in listOf(0, 90, -90, 180)) for (known in listOf(true, false)) {
            val area = B.boxIn(band, side, atLeft)
            val f = CameraOverlayFrame.fit(if (known) 5120 else 0, if (known) 960 else 0, crop, rot, area.w, area.h)
            // Căn giữa vùng — y hệt `CameraOverlayView.layoutParams` (x/y ở đó là độ lệch từ góc `gravity`).
            val x = area.x0 + ((area.w - f.w) / 2).coerceAtLeast(0)
            val y = area.y0 + ((area.h - f.h) / 2).coerceAtLeast(0)
            val win = CameraClusterBand.Rect(x, y, x + f.w, y + f.h)
            assertTrue(band.contains(win), "atLeft=$atLeft crop=${crop?.toList()} rot=$rot known=$known ⇒ $win ra ngoài $band")
        }
        // Dải thấp hơn vùng vuông ⇒ cạnh bị dải cắt, không bao giờ tràn; dải hẹp cũng vậy.
        val low = CameraClusterBand.Rect(140, 136, 1780, 300)
        assertTrue(low.contains(B.boxIn(low, side, atLeft = true)) && low.contains(B.boxIn(low, side, atLeft = false)))
        val narrow = CameraClusterBand.Rect(100, 0, 220, 720)
        assertTrue(narrow.contains(B.boxIn(narrow, side, atLeft = false)), "dải hẹp hơn cạnh ⇒ cạnh = bề rộng dải")
        // Vùng này KHÁC hẳn hình *theo cụm* (cao trọn dải, rộng tới nửa dải) — hai hình vẫn phân biệt được trên xe.
        assertTrue(B.boxIn(band, side, atLeft = true).h < place(true).h)
    }

    /** Bán kính bo co theo chiều cao display (24 ở 720 ⇒ 27 ở 800) và kẹp theo cửa sổ. */
    @Test fun `ban kinh co theo display va kep theo cua so`() {
        assertEquals(24, B.radius(seal, 720, 565, 424))
        assertEquals(27, B.radius(seal, 800, 565, 424))
        assertEquals(5, B.radius(seal, 720, 10, 300), "cửa sổ 10 px rộng ⇒ bán kính ≤ 5")
        assertEquals(24, B.radius(seal, 0, 565, 424), "chưa đo display ⇒ số gốc")
    }

    // ══ (3) THOÁI trên màn chính + (4) crop không đổi ═══════════════════════════════════════════════════════════

    /** "Theo cụm" trên màn chính ⇒ CHỮ NHẬT (cửa sổ 2.73), KHÔNG tròn; trên cụm giữ; hình khác không bị chạm. */
    @Test fun `theo cum thoai ve chu nhat tren man chinh`() {
        val P = CameraSignalPolicy
        assertEquals(P.SHAPE_RECT, B.effectiveShape(B.SHAPE_CLUSTER, onCluster = false))
        assertEquals(B.SHAPE_CLUSTER, B.effectiveShape(B.SHAPE_CLUSTER, onCluster = true))
        for (s in listOf(P.SHAPE_RECT, P.SHAPE_ROUND, "rác")) for (c in listOf(true, false)) assertEquals(s, B.effectiveShape(s, c))
        assertTrue(B.isCluster(B.SHAPE_CLUSTER)); assertFalse(B.isCluster(P.SHAPE_ROUND))
        // Một nguồn mã: giá trị lưu bền nằm trong SHAPES của `:core` ⇒ pref/chip/prefs_set đều nhận.
        assertTrue(P.isShape(B.SHAPE_CLUSTER), "CLUSTER phải là mã hợp lệ của camera_shape")
        assertEquals(P.SHAPE_CLUSTER, B.SHAPE_CLUSTER)
    }

    /** Crop của *theo cụm* == crop chữ nhật ở cả hai bên, cả ba bề rộng — hình này chỉ đổi CỬA SỔ. */
    @Test fun `crop theo cum trung crop chu nhat`() {
        val P = CameraSignalPolicy
        for (left in listOf(true, false)) for (span in P.SPANS) {
            val view = if (left) CamView.MIRROR_LEFT else CamView.MIRROR_RIGHT
            val strip = CameraPanoCrop.defaultStrip(left)
            val rect = CameraPanoCrop.cropFor(view, left, strip, span, P.SHAPE_RECT, P.CIRCLE_PCT_DEFAULT)
            val clus = CameraPanoCrop.cropFor(view, left, strip, span, B.SHAPE_CLUSTER, P.CIRCLE_PCT_DEFAULT)
            assertEquals(rect?.toList(), clus?.toList(), "left=$left span=$span")
        }
        // …và không phải tròn (tròn cắt ô vuông ⇒ rect khác hẳn).
        val round = CameraPanoCrop.cropFor(CamView.MIRROR_LEFT, true, 1, P.SPAN_STRIP, P.SHAPE_ROUND, 100)
        val clus = CameraPanoCrop.cropFor(CamView.MIRROR_LEFT, true, 1, P.SPAN_STRIP, B.SHAPE_CLUSTER, 100)
        assertFalse(round!!.toList() == clus!!.toList())
    }
}
