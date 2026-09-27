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

    /** [ĐO 27/09] thanh trên tới **131**, thanh dưới từ ≈ 567, kính trái ≈ 121, kính phải ≈ 1819, icon phải ≈ 1798. */
    @Test fun `dai Seal nam ngoai thanh tren duoi va vien kinh da do`() {
        assertEquals(CameraClusterBand.Rect(140, 132, 1780, 560), band)
        // ⚠ 2.79 hạ mốc trên 136 → 132 (owner: *"vẫn còn dư phía trên top, nên kéo lên thêm 1 tý"*). `136` của §2 là
        // một **mốc nội dung** (mép trên thanh tìm kiếm gmaps — hàng đầu tiên chắc chắn thấy trọn), KHÔNG phải mép
        // của thanh hệ thống. Mép thanh đo thẳng ở doc §13: nắn ba ảnh cụm về không gian framebuffer rồi đọc đường
        // kẻ phân cách — `cum-2` (sai số 3,8 px) cho đáy đường kẻ ở **131**, `cum-0` 122–126, `cum-1` ~128. Lấy ước
        // lượng SÂU NHẤT rồi xuống một hàng ⇒ 132. Bài này khoá đúng lý lẽ ấy: không sớm hơn 132, và không muộn hơn
        // 136 (muộn hơn = trả lại chỗ owner vừa đòi).
        assertTrue(band.y0 in 132..136, "132 = hàng đầu tiên dưới đáy đường kẻ thanh trên (131, doc §13)")
        assertTrue(band.y1 <= 560, "nội dung tới 559 còn thấy; chữ thanh dưới từ 567 ⇒ dải kết thúc ≤ 560")
        assertTrue(band.x0 >= 121 + 15, "viền kính trái ở đáy ≈ 121 (±8) ⇒ lề ≥ 15 px")
        assertTrue(band.x1 <= 1798 - 15, "cột icon hệ thống từ ≈ 1798 (biển 30 / ADAS) ⇒ lề ≥ 15 px")
        assertTrue(CameraClusterBand.Rect(0, 0, 1920, 720).contains(band))
        assertEquals(1640, band.w); assertEquals(428, band.h, "424 + 4 px lấy lại được ở mép trên")
    }

    /** Display khác cỡ tham chiếu (SL6 cụm 1920×800) ⇒ co giãn theo trục, vẫn nằm trong display; chưa đo ⇒ số gốc. */
    @Test fun `dai co gian theo display va khong bao gio ra ngoai`() {
        val sl6 = B.band(1920, 800, seal)
        assertEquals(CameraClusterBand.Rect(140, 147, 1780, 622), sl6, "dọc ×800/720, ngang giữ nguyên")
        assertTrue(CameraClusterBand.Rect(0, 0, 1920, 800).contains(sl6))
        assertEquals(CameraClusterBand.Rect(140, 132, 1780, 560), B.band(0, 0, seal), "chưa đo display ⇒ đúng số tham chiếu")
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

    /** Dải gương trọn (1280×960 = 4:3, ĐỨNG theo F1 [ĐO]) ⇒ 571×428 dính đầu trái (xi-nhan trái) / đầu phải (phải). */
    @Test fun `guong tron 4 3 dinh dau trai hoac phai cua dai`() {
        val l = place(true)
        assertEquals(140, l.x, "đầu TRÁI dải"); assertEquals(132, l.y)
        assertEquals(571, l.w, "428 × 1280/960 = 570,7"); assertEquals(428, l.h, "cao trọn dải")
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
        // Xoay ±90 ⇒ 3:4 ⇒ 321×428; chưa biết cỡ ⇒ ô vuông 428 (đúng khuôn `CameraOverlayFrame.tall`).
        assertEquals(321 to 428, place(true, rot = -90).let { it.w to it.h })
        assertEquals(428 to 428, place(true, w = 0, h = 0).let { it.w to it.h })
    }

    /**
     * ═══ 2.79 · BA HÌNH trên cụm đều **CAO TRỌN DẢI**, ở mọi góc xoay, mọi crop, biết hay chưa biết cỡ nguồn ═══════
     *
     * [ĐO owner 27/09 tối]: *"lưu ý là cả xoay và không xoay, thì chiều cao cần tối đa nhé, chỉ xử lý cho phần cụm
     * nhé"*; và ba câu trước đó: chữ nhật *"đang bé"*, tròn *"đang thu ngắn quá"*. Tới 2.78 chữ nhật/tròn đi qua một
     * ô VUÔNG đo bằng **% chiều cao display** (`0,50 × 720 = 360`) rồi mới kẹp vào dải ⇒ thấp hơn dải 68 px và hẹp
     * hơn tới 2,5 lần. Ô ấy đã hết chủ; bài này khoá cái thay nó.
     *
     * Bất biến khoá cho CẢ BA hình: `h == band.h` · `y == band.y0` · nằm trong dải (hoặc trong đường cong đã đo với
     * hình *theo cụm*) · không bao giờ chạm thanh trên/dưới ([P1] 2.76).
     */
    @Test fun `ba hinh tren cum deu cao tron dai o moi goc xoay`() {
        val P = CameraSignalPolicy
        val e = B.leftEdge(band, seal, 1920)
        val er = B.rightEdge(band, seal, 1920)
        val crops = listOf(null, CameraPanoCrop.stripCrop(1), CameraPanoCrop.narrowCrop(1, true), CameraPanoCrop.squareCrop(0.375, 0.1875, 100))
        for (shape in listOf(P.SHAPE_RECT, P.SHAPE_ROUND, B.SHAPE_CLUSTER)) {
            for (atLeft in listOf(true, false)) for (crop in crops) for (rot in listOf(0, 90, -90, 180)) for (known in listOf(true, false)) {
                val p = B.place(
                    band, atLeft, if (known) 5120 else 0, if (known) 960 else 0, crop, rot, seal, 720,
                    shape = shape, leftEdge = e, rightEdge = er,
                )
                val lbl = "hình=$shape atLeft=$atLeft crop=${crop?.toList()} rot=$rot known=$known"
                assertEquals(band.h, p.h, "$lbl: chiều cao PHẢI trọn dải — owner \"chiều cao cần tối đa\"")
                assertEquals(band.y0, p.y, "$lbl: dính mép trên dải ⇒ không thừa chỗ phía trên")
                assertEquals(band.y1, p.y + p.h, "$lbl: chạm đúng mép dưới dải, không quá")
                assertTrue(B.insideBand(p), "$lbl: $p ra ngoài dải/đường cong")
                assertTrue(p.y >= band.y0 && p.y + p.h <= band.y1, "$lbl: KHÔNG được đè thanh trên/dưới của cụm")
                assertTrue(p.w >= 1 && p.radiusPx in 0..minOf(p.w, p.h) / 2, "$lbl: cỡ ${p.w}x${p.h}")
                assertEquals(known, p.streamKnown, lbl)
                // TRÒN: cửa sổ phải VUÔNG (đường kính = chiều cao dải), nếu không thì outline oval ra hình ELIP.
                if (shape == P.SHAPE_ROUND) assertEquals(band.h, p.w, "$lbl: đường kính = chiều cao dải")
                // Chữ nhật/tròn: KHÔNG mặt nạ cong, KHÔNG dải mờ — hai thứ đó là của riêng hình *theo cụm*.
                if (!B.isCluster(shape)) {
                    assertTrue(p.leftEdge.isEmpty() && p.rightEdge.isEmpty(), "$lbl: không mặt nạ cong")
                    assertEquals(0, B.fadePx(p), "$lbl: không dải mờ")
                    assertEquals(if (atLeft) band.x0 else band.x1 - p.w, p.x, "$lbl: dính mép dải")
                }
            }
        }
        // Số owner sẽ thấy trên xe (gương 4:3, xi-nhan trái). Chữ nhật 2.78: 360×270 = 97 200 px².
        val rect = B.place(band, true, 5120, 960, CameraPanoCrop.stripCrop(1), 0, seal, 720, shape = P.SHAPE_RECT, leftEdge = e, rightEdge = er)
        assertEquals(571 to 428, rect.w to rect.h, "chữ nhật: cao trọn dải, rộng theo tỉ lệ ảnh")
        assertEquals(100, B.zoomPct(rect), "chữ nhật KHÔNG phóng ⇒ không mất một px tầm nhìn nào")
        assertEquals(0, B.lossPct(rect))
        val round = B.place(band, true, 5120, 960, CameraPanoCrop.squareCrop(0.375, 0.1875, 100), 0, seal, 720, shape = P.SHAPE_ROUND)
        assertEquals(428 to 428, round.w to round.h, "tròn: đường kính = chiều cao dải (2.78 là 360)")
        assertEquals(100, B.zoomPct(round), "crop tròn đã vuông ⇒ lấp kín mà không phóng")
        assertEquals(140, round.x); assertEquals(band.x1 - 428, B.place(band, false, 5120, 960, CameraPanoCrop.squareCrop(0.625, 0.1875, 100), 0, seal, 720, shape = P.SHAPE_ROUND).x)
    }

    /**
     * Cân đối hai bên: khoảng hở của cửa sổ TRÁI so với mép trái dải = khoảng hở của cửa sổ PHẢI so với mép phải.
     *
     * [ĐO owner 27/09 tối] *"tròn và chữ nhật thì canh đều, cân đối 2 bên trái phải cả trên cụm"*. Từ 2.79 phép đặt
     * nằm trọn ở `:core` ([place]) nên bài đo thẳng trên `Placement`, không phải dựng lại công thức của `:app`.
     */
    @Test fun `chu nhat va tron tren cum can doi hai ben`() {
        val P = CameraSignalPolicy
        val crops = listOf(null, CameraPanoCrop.stripCrop(1), CameraPanoCrop.narrowCrop(1, true), CameraPanoCrop.squareCrop(0.375, 0.1875, 100))
        for (shape in listOf(P.SHAPE_RECT, P.SHAPE_ROUND)) for (crop in crops) for (rot in listOf(0, 90, -90, 180)) for (known in listOf(true, false)) {
            val w = if (known) 5120 else 0
            val h = if (known) 960 else 0
            val l = B.place(band, true, w, h, crop, rot, seal, 720, shape = shape)
            val r = B.place(band, false, w, h, crop, rot, seal, 720, shape = shape)
            val lbl = "hình=$shape crop=${crop?.toList()} rot=$rot known=$known"
            assertEquals(l.w to l.h, r.w to r.h, "$lbl: hai bên cùng cỡ")
            assertEquals(l.x - band.x0, band.x1 - (r.x + r.w), "$lbl: khoảng hở hai bên phải BẰNG NHAU")
            assertEquals(l.y, r.y, "$lbl: cùng hàng trên")
            assertTrue(band.contains(l.rect) && band.contains(r.rect), "$lbl: ra ngoài dải")
            assertTrue(l.x + l.w <= r.x, "$lbl: hai cửa sổ không chạm nhau")
        }
    }

    /** Bán kính bo co theo chiều cao display (24 ở 720 ⇒ 27 ở 800) và kẹp theo cửa sổ. */
    @Test fun `ban kinh co theo display va kep theo cua so`() {
        assertEquals(24, B.radius(seal, 720, 571, 428))
        assertEquals(27, B.radius(seal, 800, 571, 428))
        assertEquals(5, B.radius(seal, 720, 10, 300), "cửa sổ 10 px rộng ⇒ bán kính ≤ 5")
        assertEquals(24, B.radius(seal, 0, 571, 428), "chưa đo display ⇒ số gốc")
    }

    // ══ (2b) MÉP NGOÀI CONG (2.77) — bảng đo được, cửa sổ trượt ra tới kính, mặt nạ không bao giờ vượt kính ═══════

    /** Bảng Seal: 9 mẫu, mọi mẫu nằm trong `0..left`, điểm xa nhất 18 (hôm nay tường thẳng 140 ⇒ nới 122 px). */
    @Test fun `bang mep cong Seal nam trong khoang cho phep`() {
        val e = seal.leftEdge
        assertEquals(9, e.size, "9 mẫu, bước 53,5 px: y = 132, 185, …, 560")
        assertTrue(e.all { it in 0..seal.left }, "mép cong chỉ nới RA, không bao giờ ăn vào trong: $e")
        assertEquals(19, e.min(), "điểm xa nhất của kính (y ≈ 210) [ĐO 27/09, dựng lại ở mốc 132]")
        assertEquals(46, e.first()); assertEquals(89, e.last())
        // Đường "<": đi ra ở nửa trên rồi vào lại ở nửa dưới — KHÔNG đơn điệu, đó là lý do mô hình là BẢNG.
        assertTrue(e[0] > e[1] && e.drop(2).zipWithNext().all { (a, b) -> a <= b }, "ra rồi vào: $e")
        // Hai mẫu cạnh nhau không bao giờ nhảy quá 1/3 bước dọc ⇒ đường cắt không có bậc thang nhìn thấy được.
        assertTrue(e.zipWithNext().all { (a, b) -> kotlin.math.abs(b - a) <= 53 / 3 + 12 }, "bước quá gắt: $e")
        assertEquals(70, e.max() - e.min(), "bề PHẦN LẤN: cửa sổ *theo cụm* rộng thêm đúng ngần này (doc §13)")
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
        assertEquals(31, B.leftEdgeAt(band, e, 162), "giữa mẫu 0 (46) và mẫu 1 (19)")
        for (y in band.y0..band.y1) assertTrue(B.leftEdgeAt(band, e, y) in 0..band.x0, "hàng $y")
    }

    /**
     * ═══ 2.79 · *THEO CỤM* — mép BÁM ĐƯỜNG CONG và KHÔNG còn khe đen ở bất kỳ hàng nào ═══════════════════════════
     *
     * ⚠ Bài này **đảo** bất biến của 2.78, và lý do là một phép đo bằng MẮT owner, 27/09 tối:
     * *"viền trái đâu có bám theo cụm hả, chỉ là 1 đường thẳng thôi mà, khác gì chữ nhật đâu"*.
     *
     * 2.78 đặt cửa sổ ở điểm **TRONG CÙNG** của kính để mặt nạ không cắt một pixel video nào — bài cũ khoá đúng điều
     * ấy (`maskLeftAt == p.x` ở mọi hàng). Nhưng đó chính là **định nghĩa của một đường thẳng đứng**: nếu mặt nạ
     * không bao giờ cắt thì mép hiện ra không bao giờ cong. Hai lời hứa ấy loại trừ nhau; owner chọn cái nào thì đã
     * nói rõ: *"theo cụm chấp nhận mất 20%, làm bo theo cụm cho đẹp"*.
     *
     * 2.79 vì thế đảo: cửa sổ trải từ điểm **NGOÀI CÙNG** của đường cong tới đúng mép trong cũ, và ảnh được **phóng
     * để lấp** ([CameraOverlayFrame.cover] — cắt rìa nguồn, KHÔNG kéo giãn). Ba bất biến mới:
     *  1. `maskLeftAt(p, y)` **bằng đúng đường cong** ở mọi hàng (không còn bằng `p.x`) ⇒ mép thật sự cong;
     *  2. cửa sổ phủ tới đường cong ở mọi hàng ⇒ **không còn khe đen** giữa video và viền kính;
     *  3. lớp video phủ kín cửa sổ ở cả hai trục và **đúng tỉ lệ ảnh** ⇒ không kéo giãn; phần mất = `1 − 1/phóng`.
     */
    @Test fun `theo cum bam duong cong va khong con khe den o hang nao`() {
        val e = B.leftEdge(band, seal, 1920)
        val er = B.rightEdge(band, seal, 1920)
        val crops = listOf(null, CameraPanoCrop.stripCrop(1), CameraPanoCrop.narrowCrop(1, true), CameraPanoCrop.squareCrop(0.375, 0.1875, 100))
        for (atLeft in listOf(true, false)) for (crop in crops) for (rot in listOf(0, 90, -90, 180)) {
            val p = B.place(band, atLeft, 5120, 960, crop, rot, seal, 720, shape = B.SHAPE_CLUSTER, leftEdge = e, rightEdge = er)
            val flat = B.place(band, atLeft, 5120, 960, crop, rot, seal, 720, shape = CameraSignalPolicy.SHAPE_RECT)
            val lbl = "atLeft=$atLeft crop=${crop?.toList()} rot=$rot"
            assertEquals(flat.h, p.h, "$lbl: chiều cao vẫn trọn dải")
            assertEquals(flat.y, p.y, "$lbl: chiều dọc không đổi ⇒ không bao giờ chạm thanh trên/dưới")
            assertTrue(B.insideBand(p), "$lbl: $p ra ngoài đường cong đã đo")
            assertTrue(p.w > flat.w, "$lbl: phải RỘNG hơn hình chữ nhật đúng bằng phần lấn ra của kính")
            // (1)+(2) từng hàng: mực trùng ĐÚNG đường cong, và cửa sổ phủ tới đó ⇒ không khe đen.
            var khac = 0
            for (y in p.y until p.y + p.h) {
                if (atLeft) {
                    assertEquals(B.leftEdgeAt(band, e, y), B.maskLeftAt(p, y), "$lbl y=$y: mực KHÔNG bám đường cong")
                    assertTrue(p.x <= B.leftEdgeAt(band, e, y), "$lbl y=$y: cửa sổ hụt ⇒ còn khe đen tới viền kính")
                    if (B.maskLeftAt(p, y) != p.x) khac++
                } else {
                    assertEquals(B.rightEdgeAt(band, er, y), B.maskRightAt(p, y), "$lbl y=$y: mực KHÔNG bám đường cong")
                    assertTrue(p.x + p.w >= B.rightEdgeAt(band, er, y), "$lbl y=$y: cửa sổ hụt ⇒ còn khe đen")
                    if (B.maskRightAt(p, y) != p.x + p.w) khac++
                }
                assertTrue(B.maskLeftAt(p, y) in 0..1920 && p.x + p.w <= 1920, "$lbl y=$y: ra ngoài display")
            }
            assertTrue(khac > p.h / 2, "$lbl: chỉ $khac/${p.h} hàng khác mép cửa sổ ⇒ mép vẫn là ĐƯỜNG THẲNG (bệnh 2.78)")
            // (3) lớp video phủ kín cửa sổ, đúng tỉ lệ ảnh (so bằng nhân chéo — không có phép chia nào sai số).
            assertTrue(p.layerW >= p.w && p.layerH >= p.h, "$lbl: lớp video ${p.layerW}x${p.layerH} KHÔNG phủ kín cửa sổ")
            val f = CameraOverlayFrame.cover(5120, 960, crop, rot, p.w, p.h)
            assertEquals(f.w to f.h, p.layerW to p.layerH, "$lbl: lớp phải đúng phép cover của `:core`")
            // Phần tầm nhìn mất là một CÔNG THỨC, không phải một con số: `phần lấn / bề rộng cửa sổ`. Ghim công thức
            // ⇒ ai đổi bảng mép cong hay đổi dải thì con số tự đúng theo, không ai phải nhớ sửa bài này.
            // Ngoại lệ DUY NHẤT: nguồn rộng hơn nửa dải (nguyên khung pano 5120×960 = 5,33:1 — KHÔNG phải view
            // gương) thì chính lời *"chiều cao tối đa"* buộc cắt bề ngang, `tall` kẹp `w` về nửa dải rồi `cover` cắt
            // phần dư ⇒ mất nhiều hơn hẳn. Ghi ra bằng số thay vì im lặng hạ chiều cao xuống.
            val lan = if (atLeft) B.innermostLeft(band, e, p.y, p.h) - p.x else (p.x + p.w) - B.innermostRight(band, er, p.y, p.h)
            val rongHonDai = flat.w >= band.w / 2
            assertTrue(B.zoom(p) > 1.0, "$lbl: phải có phóng, nếu không thì mép không thể cong")
            if (!rongHonDai) {
                assertEquals(lan, p.w - flat.w, "$lbl: rộng thêm ĐÚNG phần lấn ra của kính, không hơn")
                assertTrue(kotlin.math.abs(Math.round(100.0 * lan / p.w).toInt() - B.lossPct(p)) <= 1,
                    "$lbl: mất ${B.lossPct(p)}% ≠ phần lấn $lan / bề rộng ${p.w}")
            } else {
                assertTrue(B.lossPct(p) > 20, "$lbl: nguồn 5,33:1 rộng hơn nửa dải 1,91:1 ⇒ mất nhiều là ĐÚNG hình học")
            }
            assertEquals(if (atLeft) e else emptyList(), p.leftEdge, "$lbl: chỉ mang bảng của bên mình")
            assertEquals(if (atLeft) emptyList() else er, p.rightEdge, lbl)
        }
        // Con số owner sẽ thấy trên xe (gương 4:3, dải Seal, cụm 1920×720).
        val l = B.place(band, true, 5120, 960, CameraPanoCrop.stripCrop(1), 0, seal, 720, shape = B.SHAPE_CLUSTER, leftEdge = e, rightEdge = er)
        assertEquals(19, l.x, "mép trái = điểm NGOÀI CÙNG của kính (2.78 đứng ở 89 ⇒ mép thẳng)")
        assertEquals(641, l.w, "571 (đúng tỉ lệ) + 70 (phần lấn của kính)"); assertEquals(428, l.h)
        assertEquals(641 to 481, l.layerW to l.layerH, "lớp video phóng theo CHIỀU CAO để lấp bề rộng mới")
        assertEquals(112, B.zoomPct(l)); assertEquals(11, B.lossPct(l), "mất 11 % tầm nhìn — trong hạn 20 % owner duyệt")
        assertEquals(46, B.maskLeftAt(l, 132)); assertEquals(19, B.maskLeftAt(l, 185)); assertEquals(89, B.maskLeftAt(l, 559))
        // Dòng log buổi xe (`CameraOverlayView.geometry` in nguyên chuỗi này) — khoá luôn, vì runbook 🚗 đọc nó.
        assertEquals("hình=CLUSTER cửa=641x428 tại=19,132 lớp=641x481 phóng=112% mất=11%", l.describe())
        // …và ba số `cong=` mà runbook hứa: ĐỈNH / GIỮA / ĐÁY cửa sổ, phải KHÁC nhau (2.78 là ba số bằng nhau).
        assertEquals(listOf(46, 41, 89), listOf(l.y, l.y + l.h / 2, l.y + l.h - 1).map { B.outerInkAt(l, it) })
        val r = B.place(band, false, 5120, 960, CameraPanoCrop.stripCrop(2), 0, seal, 720, shape = B.SHAPE_CLUSTER, leftEdge = e, rightEdge = er)
        assertEquals(1876, r.x + r.w, "mép phải = điểm ngoài cùng của kính phải"); assertEquals(647, r.w)
        assertEquals(12, B.lossPct(r))
        assertEquals(1829, B.maskRightAt(r, 132)); assertEquals(1876, B.maskRightAt(r, 239)); assertEquals(1800, B.maskRightAt(r, 559))
        assertEquals(listOf(1829, 1866, 1800), listOf(r.y, r.y + r.h / 2, r.y + r.h - 1).map { B.outerInkAt(r, it) })
        // Xoay ±90: khung hẹp hơn ⇒ cùng phần lấn ấy chiếm tỉ lệ lớn hơn ⇒ mất nhiều hơn, vẫn trong hạn 20 %.
        val rot = B.place(band, true, 5120, 960, CameraPanoCrop.stripCrop(1), -90, seal, 720, shape = B.SHAPE_CLUSTER, leftEdge = e, rightEdge = er)
        assertEquals(391 to 428, rot.w to rot.h); assertEquals(18, B.lossPct(rot))
    }

    /** Bảng mép PHẢI Seal: 9 mẫu trong `right..refW`, hình dạng ")", điểm TRONG CÙNG 1800 ≈ cột icon ADAS 1798. */
    @Test fun `bang mep phai Seal nam trong khoang cho phep`() {
        val e = seal.rightEdge
        assertEquals(9, e.size, "9 mẫu, cùng các hàng y = 132, 185, …, 560 với bảng trái")
        assertTrue(e.all { it in seal.right..seal.refW }, "mép phải chỉ nới RA ⇒ x LỚN hơn tường thẳng: $e")
        assertEquals(1876, e.max(), "kính phải xa nhất quanh y ≈ 240 [ĐO 27/09 tối, doc §12]")
        assertEquals(1829, e.first()); assertEquals(1800, e.last())
        // Đường ")": ra ở nửa trên rồi vào lại ở nửa dưới — soi gương của đường "<" bên trái, cũng KHÔNG đơn điệu.
        assertTrue(e[0] < e[1] && e.drop(2).zipWithNext().all { (a, b) -> a >= b }, "ra rồi vào: $e")
        assertTrue(e.zipWithNext().all { (a, b) -> kotlin.math.abs(b - a) <= 53 / 3 + 25 }, "bước quá gắt: $e")
        assertEquals(76, e.max() - e.min(), "phần lấn bên phải — lớn hơn bên trái 6 px, đó là sự thật miếng kính")
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
        assertEquals(1851, B.rightEdgeAt(band, e, 162), "giữa mẫu 0 (1829) và mẫu 1 (1869)")
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
        assertEquals(27, B.fadePx(l.copy(radiusPx = B.radius(seal, 800, 571, 428))), "co theo display như mọi số khác")
        // 2.79 — dải mờ là của RIÊNG hình *theo cụm*: chữ nhật/tròn có mép thẳng nằm sát mép dải, mờ ở đó là mờ vô cớ.
        val P = CameraSignalPolicy
        for (shape in listOf(P.SHAPE_RECT, P.SHAPE_ROUND)) {
            assertEquals(0, B.fadePx(l.copy(shape = shape)), "hình $shape không có dải mờ")
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
     * bằng phép đặt mới của 2.79 (bài `ba hinh tren cum deu cao tron dai o moi goc xoay`), không phải bằng đổi hình.
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
