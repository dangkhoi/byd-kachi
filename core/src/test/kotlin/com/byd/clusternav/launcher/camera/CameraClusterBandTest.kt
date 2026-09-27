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

    // ══ (2b) MÉP NGOÀI CONG (2.77) — bảng đo được, cửa sổ trượt ra tới kính, mặt nạ không bao giờ vượt kính ═══════

    /** Bảng Seal: 9 mẫu, mọi mẫu nằm trong `0..left`, điểm xa nhất 18 (hôm nay tường thẳng 140 ⇒ nới 122 px). */
    @Test fun `bang mep cong Seal nam trong khoang cho phep`() {
        val e = seal.leftEdge
        assertEquals(9, e.size, "9 mẫu, bước 53 px: y = 136, 189, …, 560")
        assertTrue(e.all { it in 0..seal.left }, "mép cong chỉ nới RA, không bao giờ ăn vào trong: $e")
        assertEquals(18, e.min(), "điểm xa nhất của kính (y ≈ 210) [ĐO 27/09]")
        assertEquals(42, e.first()); assertEquals(89, e.last())
        // Đường "<": đi ra ở nửa trên rồi vào lại ở nửa dưới — KHÔNG đơn điệu, đó là lý do mô hình là BẢNG.
        assertTrue(e[0] > e[1] && e.drop(2).zipWithNext().all { (a, b) -> a <= b }, "ra rồi vào: $e")
        // Hai mẫu cạnh nhau không bao giờ nhảy quá 1/3 bước dọc ⇒ đường cắt không có bậc thang nhìn thấy được.
        assertTrue(e.zipWithNext().all { (a, b) -> kotlin.math.abs(b - a) <= 53 / 3 + 12 }, "bước quá gắt: $e")
    }

    /** Co giãn theo trục ngang + kẹp vào `0..band.x0`; hồ sơ chưa đo ⇒ rỗng ⇒ mọi phép rơi về tường thẳng 2.76. */
    @Test fun `mep cong co gian va thoai ve tuong thang khi chua do`() {
        assertEquals(seal.leftEdge, B.leftEdge(band, seal, 1920), "đúng cỡ tham chiếu ⇒ nguyên bảng")
        val wide = ClusterBandSpec(3840, 720, 280, 136, 3560, 560, 24, listOf(84, 36, 38, 62, 82, 100, 122, 148, 178))
        assertEquals(listOf(84, 36, 38, 62, 82, 100, 122, 148, 178), B.leftEdge(B.band(3840, 720, wide), wide, 3840))
        assertEquals(seal.leftEdge, B.leftEdge(band, seal, 0), "chưa đo display ⇒ không co giãn, không ném")
        val plain = ClusterBandSpec(1920, 720, 140, 136, 1780, 560, 24)
        assertTrue(B.leftEdge(band, plain, 1920).isEmpty(), "đời cụm chưa đo đường cong ⇒ bảng rỗng")
        assertEquals(band.x0, B.leftEdgeAt(band, emptyList(), 300), "bảng rỗng ⇒ tường thẳng — hành vi 2.76")
        // Display hẹp hơn tham chiếu: bảng co lại và vẫn ≤ mép trái của dải đã co.
        val small = B.band(960, 360, seal)
        assertTrue(B.leftEdge(small, seal, 960).all { it in 0..small.x0 })
    }

    /** Nội suy: đúng bảng ở các hàng mẫu, kẹp ngoài dải, và không bao giờ ra ngoài `0..band.x0`. */
    @Test fun `noi suy mep cong dung o hang mau va kep ngoai dai`() {
        val e = B.leftEdge(band, seal, 1920)
        for ((i, x) in e.withIndex()) assertEquals(x, B.leftEdgeAt(band, e, band.y0 + band.h * i / (e.size - 1)), "mẫu $i")
        assertEquals(e.first(), B.leftEdgeAt(band, e, band.y0 - 500), "trên đỉnh dải ⇒ kẹp về mẫu đầu")
        assertEquals(e.last(), B.leftEdgeAt(band, e, band.y1 + 500), "dưới đáy dải ⇒ kẹp về mẫu cuối")
        assertEquals(30, B.leftEdgeAt(band, e, 162), "giữa mẫu 0 (42) và mẫu 1 (18) ⇒ 42 − 24/2")
        for (y in band.y0..band.y1) assertTrue(B.leftEdgeAt(band, e, y) in 0..band.x0, "hàng $y")
    }

    /**
     * ═══ Cửa sổ ôm kính ở CẢ HAI mép, và MẶT NẠ không cắt một pixel video nào ════════════════════════════════════
     *
     * Owner 27/09 chiều: *"shape nó không theo cạnh trái cong của cụm"*; 27/09 tối, sau khi mép trái đã ôm:
     * *"xi nhan trái bám nhưng cắt rát quá, bị mất nhiều"* và *"bên phải không bám, còn thừa 1 khoảng"*. Bài này
     * khoá cả hai bài học: (1) cửa sổ đứng ở điểm **TRONG CÙNG** của kính trên đúng dải hàng nó chiếm ⇒ mặt nạ không
     * cắt một pixel nào ở bất kỳ hàng nào (`maskLeftAt == p.x`, `maskRightAt == p.x + p.w`); (2) bề rộng/cao và vị
     * trí dọc **không đổi một px** so với bản tường thẳng — nới bề rộng là kéo giãn ảnh, đúng thứ `fit` tránh.
     */
    @Test fun `cua so cong om kinh hai ben, mat na khong cat pixel nao`() {
        val e = B.leftEdge(band, seal, 1920)
        val er = B.rightEdge(band, seal, 1920)
        val crops = listOf(null, CameraPanoCrop.stripCrop(1), CameraPanoCrop.narrowCrop(1, true), CameraPanoCrop.squareCrop(0.375, 0.1875, 100))
        for (atLeft in listOf(true, false)) for (crop in crops) for (rot in listOf(0, 90, -90, 180)) for (known in listOf(true, false)) {
            val w = if (known) 5120 else 0
            val h = if (known) 960 else 0
            val p = B.place(band, atLeft, w, h, crop, rot, seal, 720, leftEdge = e, rightEdge = er)
            val flat = B.place(band, atLeft, w, h, crop, rot, seal, 720)
            val lbl = "atLeft=$atLeft crop=${crop?.toList()} rot=$rot known=$known"
            assertEquals(flat.w to flat.h, p.w to p.h, "$lbl: bề rộng/cao KHÔNG đổi so với 2.76 (không kéo giãn ảnh)")
            assertEquals(flat.y, p.y, "$lbl: chiều dọc không đổi ⇒ không bao giờ chạm thanh trên/dưới")
            assertTrue(B.insideBand(p), "$lbl: $p ra ngoài dải")
            if (atLeft) {
                // ⚠ 2.78 — bản 2.77 khoá `p.x == 18` (*"trượt ra tới điểm XA NHẤT của kính"*). Luật ấy SAI và
                // [ĐO xe 27/09 tối] bác bỏ: bề rộng `w` do `CameraOverlayFrame.fit` quyết theo tỉ lệ nguồn, nên
                // trượt ra ngoài KHÔNG lấy thêm pixel ảnh nào — nó chỉ đẩy mép trái cửa sổ ra chỗ kính không sáng
                // rồi mặt nạ cắt đúng phần ấy khỏi VIDEO (owner: *"bám nhưng cắt rát quá, bị mất nhiều"* — mất tới
                // 71/318 px ở hàng đáy). Nay khoá bằng BẤT BIẾN thật, mạnh hơn một con số: cửa sổ đặt ở điểm TRONG
                // CÙNG trên đúng dải hàng nó chiếm ⇒ mặt nạ không cắt một pixel nào (vòng `for` ngay dưới kiểm mọi hàng).
                assertEquals(B.innermostLeft(band, e, p.y, p.h), p.x, "$lbl: đầu trái đặt ở điểm TRONG CÙNG của kính")
                assertEquals(e, p.leftEdge, "$lbl: Placement mang bảng để `:app` dựng mặt nạ")
            } else {
                // ⚠ 2.78 — 2.77 khoá `p.x == flat.x` (*"mép phải bị cột icon ADAS 1798 chặn, không phải kính"*).
                // [ĐO xe 27/09 tối] owner bác: *"bên phải không bám, còn thừa 1 khoảng"*, và log cùng lượt
                // `cong=1462/1462/1462` cho thấy bên phải không có mô hình cong nào. Nay mép PHẢI cửa sổ (`x + w`)
                // đứng ở điểm TRONG CÙNG của kính phải — `min` của bảng trên dải hàng ấy, ngược hướng với bên trái.
                assertEquals(B.innermostRight(band, er, p.y, p.h) - p.w, p.x, "$lbl: đầu phải đặt ở điểm TRONG CÙNG")
                assertEquals(er, p.rightEdge, "$lbl: Placement mang bảng PHẢI để `:app` dựng mặt nạ")
                assertTrue(p.leftEdge.isEmpty(), "$lbl: cửa sổ chỉ mang bảng của bên nó đứng")
                assertTrue(p.x + p.w > flat.x + flat.w, "$lbl: phải nới ra được so với tường thẳng ${band.x1}")
                assertFalse(p.atLeft)
            }
            // Mép có mực ở MỌI hàng của cửa sổ: bằng đúng mép cửa sổ (mặt nạ không cắt), và không vượt kính.
            for (y in p.y until p.y + p.h) {
                val left = B.maskLeftAt(p, y)
                val right = B.maskRightAt(p, y)
                if (atLeft) assertEquals(p.x, left, "$lbl y=$y: mặt nạ CẮT mất ${left - p.x} px video — luật owner 27/09 \"không để mất video\"")
                else assertEquals(p.x + p.w, right, "$lbl y=$y: mặt nạ CẮT mất ${p.x + p.w - right} px video bên phải")
                assertTrue(left >= B.leftEdgeAt(band, p.leftEdge, y), "$lbl y=$y: mực ở $left, kính ở ${B.leftEdgeAt(band, p.leftEdge, y)}")
                assertTrue(right <= B.rightEdgeAt(band, p.rightEdge, y), "$lbl y=$y: mực ở $right, kính ở ${B.rightEdgeAt(band, p.rightEdge, y)}")
                assertTrue(left in 0..1920 && p.x + p.w <= 1920, "$lbl y=$y: ra ngoài display")
            }
        }
        // Con số owner sẽ thấy, sau luật 2.78 *"không để mất video"*. Cửa sổ đứng ở điểm TRONG CÙNG của kính
        // trên dải hàng nó chiếm, nên mép có mực **bằng nhau ở mọi hàng** — không hàng nào bị mặt nạ ăn vào.
        // (2.77 cho 42/18/41/89 tức ăn tới 71 px ở đáy; 2.76 là 140 ở mọi hàng, tức hụt tới 122 px so với kính.)
        val p = B.place(band, true, 5120, 960, CameraPanoCrop.stripCrop(1), 0, seal, 720, leftEdge = e, rightEdge = er)
        val inner = B.innermostLeft(band, e, p.y, p.h)
        for (y in listOf(136, 189, 348, 560).filter { it in p.y until p.y + p.h }) {
            assertEquals(inner, B.maskLeftAt(p, y), "hàng $y: mặt nạ phải KHÔNG cắt video")
        }
        assertEquals(89, inner, "điểm trong cùng của kính trái trên dải hàng trọn dải [ĐO 27/09]")
        assertTrue(inner < band.x0, "vẫn nới ra ngoài tường thẳng 2.76 (${band.x0}) được ${band.x0 - inner} px")
        // Bên PHẢI, cùng cửa sổ trọn dải: điểm trong cùng của kính phải = 1800 ⇒ nới 20 px (ít hơn bên trái 51 px, và
        // đó là SỰ THẬT của miếng kính chứ không phải thiếu sót: mép phải vào lại tới 1800 ở hàng đáy, doc §12).
        val pr = B.place(band, false, 5120, 960, CameraPanoCrop.stripCrop(2), 0, seal, 720, leftEdge = e, rightEdge = er)
        assertEquals(1800, B.innermostRight(band, er, pr.y, pr.h))
        assertEquals(1800, pr.x + pr.w); assertEquals(20, pr.x + pr.w - band.x1)
        for (y in listOf(136, 242, 348, 559).filter { it in pr.y until pr.y + pr.h }) {
            assertEquals(1800, B.maskRightAt(pr, y), "hàng $y: mặt nạ phải KHÔNG cắt video bên phải")
        }
        // Cửa sổ THẤP hơn dải (nguồn rất rộng ⇒ `fit` cắt chiều cao): chỉ những hàng nó CHIẾM được tính ⇒ nó được
        // phép ra xa hơn, và mặt nạ vẫn không cắt (bất biến trên đúng dải hàng, không phải trên cả dải).
        val low = B.place(band, false, 5120, 300, null, 0, seal, 720, leftEdge = e, rightEdge = er)
        assertTrue(low.h < band.h, "cửa sổ này phải thấp hơn dải để bài có nghĩa (${low.h} < ${band.h})")
        assertEquals(B.innermostRight(band, er, low.y, low.h), low.x + low.w)
        assertTrue(low.x + low.w > 1800, "nằm giữa dải ⇒ kính rộng hơn ⇒ ra xa hơn cửa sổ trọn dải")
        for (y in low.y until low.y + low.h) assertEquals(low.x + low.w, B.maskRightAt(low, y), "hàng $y")
    }

    /** Bảng mép PHẢI Seal: 9 mẫu trong `right..refW`, hình dạng ")", điểm TRONG CÙNG 1800 ≈ cột icon ADAS 1798. */
    @Test fun `bang mep phai Seal nam trong khoang cho phep`() {
        val e = seal.rightEdge
        assertEquals(9, e.size, "9 mẫu, cùng các hàng y = 136, 189, …, 560 với bảng trái")
        assertTrue(e.all { it in seal.right..seal.refW }, "mép phải chỉ nới RA ⇒ x LỚN hơn tường thẳng: $e")
        assertEquals(1876, e.max(), "kính phải xa nhất quanh y ≈ 240 [ĐO 27/09 tối, doc §12]")
        assertEquals(1833, e.first()); assertEquals(1800, e.last())
        // Đường ")": ra ở nửa trên rồi vào lại ở nửa dưới — soi gương của đường "<" bên trái, cũng KHÔNG đơn điệu.
        assertTrue(e[0] < e[1] && e.drop(2).zipWithNext().all { (a, b) -> a >= b }, "ra rồi vào: $e")
        assertTrue(e.zipWithNext().all { (a, b) -> kotlin.math.abs(b - a) <= 53 / 3 + 22 }, "bước quá gắt: $e")
        // Điểm TRONG CÙNG = 1800, trùng cột icon hệ thống 1798 (F7) **trong sai số ±8 px của chính phép đo** ⇒ cửa
        // sổ trọn dải nới được 20 px mà không thật sự leo vào cột biển-30/ADAS. 🚗 thấy đè ⇒ kẹp bảng về 1798.
        assertEquals(1800, e.min()); assertEquals(20, e.min() - seal.right)
        assertTrue(e.min() >= 1798 - 8, "trong sai số của cột icon 1798")
    }

    /** Co giãn theo trục ngang + kẹp vào `band.x1..displayW`; chưa đo ⇒ rỗng ⇒ tường thẳng `band.x1`. */
    @Test fun `mep phai co gian va thoai ve tuong thang khi chua do`() {
        assertEquals(seal.rightEdge, B.rightEdge(band, seal, 1920), "đúng cỡ tham chiếu ⇒ nguyên bảng")
        assertEquals(seal.rightEdge, B.rightEdge(band, seal, 0), "chưa đo display ⇒ không co giãn, không ném")
        val wide = ClusterBandSpec(3840, 720, 280, 136, 3560, 560, 24, rightEdge = seal.rightEdge.map { it * 2 })
        assertEquals(seal.rightEdge.map { it * 2 }, B.rightEdge(B.band(3840, 720, wide), wide, 3840))
        val plain = ClusterBandSpec(1920, 720, 140, 136, 1780, 560, 24)
        assertTrue(B.rightEdge(band, plain, 1920).isEmpty(), "đời cụm chưa đo mép phải ⇒ bảng rỗng")
        assertEquals(band.x1, B.rightEdgeAt(band, emptyList(), 300), "bảng rỗng ⇒ tường thẳng — hành vi 2.77")
        val small = B.band(960, 360, seal)
        assertTrue(B.rightEdge(small, seal, 960).all { it in small.x1..960 }, "display hẹp: kẹp trong dải..display")
    }

    /** Nội suy mép phải: đúng bảng ở hàng mẫu, kẹp ngoài dải, không bao giờ ra ngoài `band.x1..displayW`. */
    @Test fun `noi suy mep phai dung o hang mau va kep ngoai dai`() {
        val e = B.rightEdge(band, seal, 1920)
        for ((i, x) in e.withIndex()) assertEquals(x, B.rightEdgeAt(band, e, band.y0 + band.h * i / (e.size - 1)), "mẫu $i")
        assertEquals(e.first(), B.rightEdgeAt(band, e, band.y0 - 500), "trên đỉnh dải ⇒ kẹp về mẫu đầu")
        assertEquals(e.last(), B.rightEdgeAt(band, e, band.y1 + 500), "dưới đáy dải ⇒ kẹp về mẫu cuối")
        assertEquals(1852, B.rightEdgeAt(band, e, 162), "giữa mẫu 0 (1833) và mẫu 1 (1871)")
        for (y in band.y0..band.y1) assertTrue(B.rightEdgeAt(band, e, y) in band.x1..1920, "hàng $y")
        // Điểm trong cùng trên một dải hàng NGẮN nằm giữa dải: `min` chỉ xét hàng bị chiếm (đối xứng `innermostLeft`).
        assertEquals(B.rightEdgeAt(band, e, 348), B.innermostRight(band, e, 242, 107), "dải hàng 242…348: min ở đáy")
        assertEquals(band.x1, B.innermostRight(band, emptyList(), 242, 107), "bảng rỗng ⇒ tường thẳng")
    }

    /**
     * Đời cụm CHƯA ĐO ([ClusterBandSpec.SEAL_DL3_NO_CURVE], mặc định của `ClusterProfile.band`) ⇒ **tường thẳng cả
     * hai bên**, tức đúng hành vi 2.76. Bảng cắt điểm ảnh theo kính của MỘT đời cụm nên không được dùng chung
     * (CLAUDE.md §7) — bài này khoá cả mép phải, không chỉ mép trái.
     */
    @Test fun `doi cum chua do van ra tuong thang hai ben`() {
        val plain = ClusterBandSpec.SEAL_DL3_NO_CURVE
        assertTrue(plain.leftEdge.isEmpty() && plain.rightEdge.isEmpty(), "chưa đo ⇒ rỗng cả hai bảng")
        val b = B.band(1920, 720, plain)
        val el = B.leftEdge(b, plain, 1920)
        val er = B.rightEdge(b, plain, 1920)
        for (atLeft in listOf(true, false)) {
            val p = B.place(b, atLeft, 5120, 960, CameraPanoCrop.stripCrop(1), 0, plain, 720, leftEdge = el, rightEdge = er)
            assertEquals(if (atLeft) b.x0 else b.x1 - p.w, p.x, "tường thẳng 2.76 ở cả hai bên")
            assertTrue(b.contains(p.rect), "tường thẳng ⇒ cửa sổ nằm trọn TRONG dải")
            assertEquals(p.x, B.maskLeftAt(p, p.y + 1)); assertEquals(p.x + p.w, B.maskRightAt(p, p.y + 1))
            assertTrue(p.leftEdge.isEmpty() && p.rightEdge.isEmpty(), "không có gì để cắt ⇒ `glassMask` trả null")
        }
        assertEquals(b.x0, B.innermostLeft(b, el, b.y0, b.h)); assertEquals(b.x1, B.innermostRight(b, er, b.y0, b.h))
    }

    /**
     * [ĐO owner 27/09 tối] *"phần cạnh bên phải thêm tý blur ra ngoài cho nó smooth, ko là 1 vạch thẳng nhìn nó như
     * sẹo, ngược lại cho bên phải cũng thế"* ⇒ dải mờ ở mép TRONG, bề rộng = bán kính bo (co theo display), kẹp ≤ w/4.
     */
    @Test fun `dai mo nam o mep trong va bang ban kinh bo`() {
        val l = place(true)
        val r = place(false)
        assertTrue(l.atLeft && !r.atLeft, "Placement biết mình ở bên nào ⇒ `:app` biết mép nào phải mờ")
        assertEquals(24, B.fadePx(l)); assertEquals(24, B.fadePx(r), "cùng bề rộng hai bên (đối xứng)")
        assertEquals(l.radiusPx, B.fadePx(l), "một số duy nhất cho bo góc và dải mờ — không thêm hằng mới")
        assertEquals(10, B.fadePx(l.copy(w = 40)), "cửa sổ hẹp ⇒ kẹp ≤ w/4, không mờ mất một phần tư ảnh")
        assertEquals(0, B.fadePx(l.copy(w = 4)), "cửa sổ suy biến ⇒ không mờ (thà cứng còn hơn mất ảnh)")
        assertEquals(27, B.fadePx(l.copy(radiusPx = B.radius(seal, 800, 565, 424))), "co theo display như mọi số khác")
    }

    /**
     * [ĐO owner 27/09 tối] *"tròn và chữ nhật thì canh đều, cân đối 2 bên trái phải cả trên cụm"* — và từ 2.78 hai
     * hình ấy KHÔNG còn bị thăng sang *theo cụm* nữa nên đây là đường thật của user chọn chữ nhật/tròn.
     *
     * Bài đo đúng công thức của `:app` (`CameraOverlayView.box` + `layoutParams`): bên trái `LayoutParams.x` là độ
     * lệch kể từ mép TRÁI display, bên phải kể từ mép PHẢI (`gravity = END`) ⇒ phải quy về toạ độ tuyệt đối rồi mới
     * so khoảng hở. Hai hình này không dùng mặt nạ cong, không dùng dải mờ — chỉ cần đặt đều.
     */
    @Test fun `chu nhat va tron tren cum can doi hai ben`() {
        val displayW = 1920
        val side = (720 * 0.50f).toInt()
        val l = B.boxIn(band, side, atLeft = true)
        val r = B.boxIn(band, side, atLeft = false)
        assertEquals(l.w to l.h, r.w to r.h, "hai vùng cùng cỡ")
        assertEquals(l.x0 - band.x0, band.x1 - r.x1, "vùng hai bên soi gương quanh tâm dải")
        assertEquals(l.y0, r.y0, "cùng hàng trên")
        val crops = listOf(null, CameraPanoCrop.stripCrop(1), CameraPanoCrop.narrowCrop(1, true), CameraPanoCrop.squareCrop(0.375, 0.1875, 100))
        for (crop in crops) for (rot in listOf(0, 90, -90, 180)) for (known in listOf(true, false)) {
            val w = if (known) 5120 else 0
            val h = if (known) 960 else 0
            val f = CameraOverlayFrame.fit(w, h, crop, rot, l.w, l.h)
            val lbl = "crop=${crop?.toList()} rot=$rot known=$known"
            // Trái: x = độ lệch từ mép trái. Phải: x = độ lệch từ mép PHẢI display ⇒ mép phải cửa sổ ở displayW − x.
            val leftAbs = l.x0 + ((l.w - f.w) / 2).coerceAtLeast(0)
            val offRight = (displayW - r.x1).coerceAtLeast(0) + ((r.w - f.w) / 2).coerceAtLeast(0)
            val rightAbs = displayW - offRight
            assertEquals(leftAbs - band.x0, band.x1 - rightAbs, "$lbl: khoảng hở hai bên phải BẰNG NHAU")
            assertTrue(band.contains(CameraClusterBand.Rect(leftAbs, l.y0, leftAbs + f.w, l.y0 + f.h)), "$lbl: trái ra ngoài dải")
            assertTrue(band.contains(CameraClusterBand.Rect(rightAbs - f.w, r.y0, rightAbs, r.y0 + f.h)), "$lbl: phải ra ngoài dải")
        }
    }

    /** Hồ sơ vô lý về đường cong (mẫu nằm phải tường thẳng, hoặc chỉ 1 mẫu) bị từ chối lúc dựng. */
    @Test fun `spec mep cong vo ly bi tu choi`() {
        assertThrows<IllegalArgumentException> { ClusterBandSpec(1920, 720, 140, 136, 1780, 560, 24, listOf(42, 141)) }
        assertThrows<IllegalArgumentException> { ClusterBandSpec(1920, 720, 140, 136, 1780, 560, 24, listOf(-1, 40)) }
        assertThrows<IllegalArgumentException> { ClusterBandSpec(1920, 720, 140, 136, 1780, 560, 24, listOf(42)) }
    }

    // ══ (3) THOÁI trên màn chính + (4) crop không đổi ═══════════════════════════════════════════════════════════

    /**
     * "Theo cụm" trên màn chính ⇒ CHỮ NHẬT (cửa sổ 2.73), KHÔNG tròn — và **KHÔNG ÉP HÌNH TRÊN CỤM**.
     *
     * ⚠ 2.78 đảo một luật của 2.77. 2.77 **thăng** `RECT → CLUSTER` khi ở trên cụm, dựa vào lời chê *"bé tý, bo các
     * góc tròn, không hề theo hình cụm gì cả"*. [ĐO owner 27/09 tối] chốt ngược: *"khi chiếu camera lên cụm, user vẫn
     * có thể chọn chữ nhật/tròn/theo cụm nhé, không ép"* ⇒ ba lựa chọn còn nguyên ba; lời chê *"bé tý"* được chữa
     * bằng phép kẹp `boxIn` (bài `chu nhat va tron tren cum can doi hai ben`), không phải bằng đổi hình sau lưng user.
     */
    @Test fun `theo cum thoai ve chu nhat tren man chinh, nhung KHONG ep hinh tren cum`() {
        val P = CameraSignalPolicy
        assertEquals(P.SHAPE_RECT, B.effectiveShape(B.SHAPE_CLUSTER, onCluster = false))
        assertEquals(B.SHAPE_CLUSTER, B.effectiveShape(B.SHAPE_CLUSTER, onCluster = true))
        assertEquals(P.SHAPE_RECT, B.effectiveShape(P.SHAPE_RECT, onCluster = true), "chữ nhật trên cụm VẪN là chữ nhật")
        assertEquals(P.SHAPE_RECT, B.effectiveShape(P.SHAPE_RECT, onCluster = false), "màn chính không đổi một byte")
        assertEquals(P.SHAPE_ROUND, B.effectiveShape(P.SHAPE_ROUND, onCluster = true), "TRÒN là chọn về NỘI DUNG ⇒ giữ")
        for (s in listOf(P.SHAPE_ROUND, "rác")) for (c in listOf(true, false)) assertEquals(s, B.effectiveShape(s, c))
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
