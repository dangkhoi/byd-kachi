package com.byd.clusternav.launcher.camera

import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.76 L7 — LẬT GƯƠNG (`camera_mirror_left/right`) — MỘT phép cho CẢ HAI đường vẽ, đo bằng số off-car ═════════
 *
 * Research `research-side-camera-orientation-2026-09-27.md` §6.2: tay gương của ảnh HAL **[CHƯA BIẾT]** (CAM-M1 chưa
 * đo) ⇒ pref mặc định TẮT, người lái bật khi ảnh ngược tay so với gương kính. Bài này khoá **cơ chế**, không đoán tay
 * gương:
 *  1. đường GL: `flipH` ⇒ với CÙNG một điểm ra, điểm nguồn lấy mẫu **phản chiếu qua tâm crop** theo x, y không đổi;
 *  2. lật ở KHÔNG GIAN NGUỒN ⇒ hợp với xoay/dịch như một camera lắp ngược: `rot(mirror(src))`, **không** phải
 *     `mirror(rot(src))` — hai thứ ấy KHÁC nhau ở ±90 (`rot∘mirror = mirror∘rot⁻¹`), bằng nhau ở 0/180;
 *  3. đường TV ([CameraOverlayTransform.matrix] bước 1b) làm đúng phép ấy: mép trái crop ra mép PHẢI khung;
 *  4. GL và TV là **nghịch đảo** của nhau cho cùng một điểm ở mọi góc xoay, kể cả khung không vuông;
 *  5. cờ tắt ⇒ ma trận **y hệt** trước L7 (CLAUDE.md §6), và `describe()` nói `lật=`.
 */
class CameraMirrorTest {

    private companion object {
        /** Vùng gương TRÁI của 2.73 (`x[0,25..0,35]`, cao trọn khung) — crop KHÔNG đối xứng quanh giữa ảnh. */
        val CROP = floatArrayOf(0.25f, 0f, 0.35f, 1f)
        const val EPS = 1e-4f
        val GRID = listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 1f)
    }

    /** Bộ uniform KHÔNG nắn (`amount 0`) — để phép lấy mẫu là affine và số so được chính xác. */
    private fun uniforms(rot: Int, mirror: Boolean, panXPct: Int = 0, panYPct: Int = 0) = CameraGlUniforms.of(
        crop = CROP, srcCentreX = 0.375f, srcCentreY = 0.5f, streamW = 5120, streamH = 960,
        rotationDeg = rot, flipH = mirror, amountPct = 0, panXPct = panXPct, panYPct = panYPct,
    )

    /** Toạ độ ẢNH (y đi xuống) của điểm nguồn mà pixel ra `(u,v)` lấy — đổi trục t của texture về y ảnh. */
    private fun src(g: CameraGlUniforms, u: Float, v: Float): Pair<Float, Float> =
        requireNotNull(srcOrNull(g, u, v)) { "($u,$v) rơi ngoài ô" }

    /**
     * Như [src] nhưng trả `null` khi điểm ra **không lấy được mẫu** — chuyện BÌNH THƯỜNG khi có dịch cửa sổ: `pan`
     * đẩy ô lấy mẫu ra khỏi `[0,1]²` và cả bộ vẽ lẫn shader đều trả **đen đặc** ở đó (KDoc `CameraDewarp.mapDstToSrc`).
     * Bài dưới bỏ qua những điểm ấy thay vì ném — nhưng ĐẾM số điểm đã so, để một ngày nào đó mọi điểm cùng rơi ra
     * ngoài thì bài **đỏ** chứ không lặng lẽ xanh với 0 phép so.
     */
    private fun srcOrNull(g: CameraGlUniforms, u: Float, v: Float): Pair<Float, Float>? =
        CameraDewarp.sample(u, v, g.rotationDeg.toInt(), g.dewarp, g.aspect, g.srcRect)?.let { it.first to (1f - it.second) }

    // ── 1. GL: lật = phản chiếu điểm nguồn qua tâm crop theo x ─────────────────────────────────────────────────

    @Test
    fun `GL - cung mot diem ra, lat guong lay mau o diem phan chieu qua tam crop theo x`() {
        val plain = uniforms(0, mirror = false)
        val flipped = uniforms(0, mirror = true)
        assertFalse(plain.mirror); assertTrue(flipped.mirror)
        val axis = CROP[0] + CROP[2]   // x + x' = x0 + x1 ⇔ phản chiếu quanh tâm crop
        for (u in GRID) for (v in GRID) {
            val (x, y) = src(plain, u, v)
            val (mx, my) = src(flipped, u, v)
            assertEquals(axis - x, mx, EPS, "u=$u: x phải phản chiếu quanh tâm crop")
            assertEquals(y, my, EPS, "v=$v: y KHÔNG đổi khi lật ngang")
        }
        // Mép trái crop hiện ở mép PHẢI khung: u = 1 ⇒ x = x0.
        assertEquals(CROP[0], src(flipped, 1f, 0.5f).first, EPS)
        assertEquals(CROP[2], src(flipped, 0f, 0.5f).first, EPS)
    }

    // ── 1b. GL + NẮN THẬT (amount 100): lật gương là ẢNH SOI của bản không lật, không phải một mảnh khác ────────

    /**
     * ═══ [P1 · SOÁT Opus 2026-09-27] Lật phải lật cả TÂM NẮN — bài này đỏ trên bản đầu của 2.76 ═════════════════
     *
     * Bài 1 ở trên dùng `amount 0` nên phép lấy mẫu là affine; ở đó đẳng thức *"phản chiếu quanh tâm crop"* đúng với
     * **mọi** `uCenter`, tức nó không nhìn thấy `uCenter` chút nào. Đúng chỗ ấy có một lỗi thật:
     * [CameraGlUniforms.of] dựng rect đã lật (`uSrcRect.z < 0`) nhưng lấy tâm quang từ crop **chưa** lật, trong khi
     * shader nắn quanh `uCenter` ở không gian local **trước** khi áp `uSrcRect` ⇒ trục nắn nằm ở `c` thay vì `1 − c`.
     * Với crop gương hẹp (`narrowCrop` — `c = 1,25`) đó là lệch **1,5 lần bề ngang ô**: ảnh trông đã lật, nhưng một
     * bên thẳng và bên kia còng — đúng triệu chứng owner [ĐO]-bác cho `cx = −10 %` ngày 27/09.
     *
     * Bất biến đo được (định nghĩa của *"ảnh soi gương"*): với nắn BẬT, điểm nguồn của bản lật tại pixel ra `u` phải
     * bằng điểm nguồn của bản KHÔNG lật tại `1 − u`, `v` giữ nguyên. Đẳng thức này đúng nhờ phép nắn đối xứng quanh
     * tâm: `w_{1−c}(u) = 1 − w_c(1 − u)`. Nó **vô hình** ở `span = STRIP` (`c = 0,5`) nên bài phải dùng crop HẸP.
     */
    @Test
    fun `GL co nan thi lat guong la anh soi cua ban khong lat, o crop hep`() {
        val narrow = CameraPanoCrop.narrowCrop(1, left = true)
        assertEquals(0.25f, narrow[0], EPS); assertEquals(0.35f, narrow[2], EPS)
        // Tâm quang của ô: 1,25 khi không lật ⇒ −0,25 khi lật (trục quang ở local `1 − c`).
        assertEquals(1.25f, CameraDewarp.centerInCrop(0.375f, 0.5f, narrow).first, EPS)
        assertEquals(-0.25f, CameraDewarp.centerInCrop(0.375f, 0.5f, narrow, flipH = true).first, EPS)
        for (crop in listOf(narrow, CameraPanoCrop.stripCrop(1))) {
            val plain = warped(crop, mirror = false)
            val flipped = warped(crop, mirror = true)
            var compared = 0
            for (u in GRID) for (v in GRID) {
                val a = srcOrNull(plain, 1f - u, v) ?: continue
                val b = srcOrNull(flipped, u, v) ?: continue
                compared++
                assertEquals(a.first, b.first, EPS, "crop=${crop.toList()} u=$u v=$v: x của bản lật = x của bản không lật tại 1−u")
                assertEquals(a.second, b.second, EPS, "y không đổi khi lật ngang")
            }
            assertTrue(compared >= 9, "chỉ so được $compared điểm — bài mất hiệu lực")
        }
    }

    /** Bộ uniform CÓ nắn (đúng bộ số owner duyệt 27/09: amount 100), crop tuỳ ca — xem bài 1b. */
    private fun warped(crop: FloatArray, mirror: Boolean) = CameraGlUniforms.of(
        crop = crop, srcCentreX = 0.375f, srcCentreY = 0.5f, streamW = 5120, streamH = 960,
        rotationDeg = 0, flipH = mirror, amountPct = 100,
    )

    // ── 2. Hợp với xoay + dịch: rot(mirror(src)), KHÔNG phải mirror(rot(src)) ────────────────────────────────

    /**
     * Với mọi góc và mọi dịch cửa sổ: điểm nguồn của bản lật = phản chiếu của bản không lật **tại cùng pixel ra** —
     * tức lật nằm ở nguồn, sau xoay và dịch đã nhìn vào nó. Và phép *"lật màn hình rồi mới xoay"* (`u → 1−u` trên
     * pixel ra) cho **kết quả khác** ở ±90 — đó là cái mà `scaleX = −1` trên lớp video sẽ làm, và là lý do không dùng nó.
     */
    @Test
    fun `lat hop voi xoay va dich - lat o nguon, va khac lat man hinh o goc 90`() {
        val axis = CROP[0] + CROP[2]
        for (rot in listOf(0, -90, 90, 180)) for ((px, py) in listOf(0 to 0, -20 to 0, 15 to -10)) {
            val plain = uniforms(rot, mirror = false, panXPct = px, panYPct = py)
            val flipped = uniforms(rot, mirror = true, panXPct = px, panYPct = py)
            var compared = 0
            var screenMirrorDiffers = false
            for (u in GRID.drop(1).dropLast(1)) for (v in GRID.drop(1).dropLast(1)) {
                // `pan` đẩy một phần ô ra ngoài `[0,1]²` ⇒ điểm ấy là ĐEN ở cả hai bản, không có gì để so.
                val a = srcOrNull(plain, u, v) ?: continue
                val b = srcOrNull(flipped, u, v) ?: continue
                compared++
                assertEquals(axis - a.first, b.first, EPS, "rot=$rot pan=($px,$py) u=$u v=$v")
                assertEquals(a.second, b.second, EPS)
                // "Lật màn hình" = lấy mẫu bản KHÔNG lật tại pixel ra đã phản chiếu (1−u, v) — thứ `scaleX = −1` làm.
                val c = srcOrNull(plain, 1f - u, v)
                if (c == null || abs(c.first - b.first) > EPS || abs(c.second - b.second) > EPS) screenMirrorDiffers = true
            }
            assertTrue(compared >= 9, "rot=$rot pan=($px,$py): chỉ so được $compared điểm — bài mất hiệu lực")
            // Lật-NGUỒN ≠ lật-MÀN-HÌNH ở hai ca, và cả hai đều là lý do không dùng `scaleX = −1`:
            //  • xoay ±90 — `rot∘mirror = mirror∘rot⁻¹` (đo ở đây, không suy);
            //  • dịch cửa sổ theo x ≠ 0 — lật màn hình lật luôn CHIỀU dịch, nên khung trôi sang phía ngược lại.
            // Với `amount 0` phép ánh xạ là đồng nhất nên đẳng thức chốt được chính xác: hai đường bằng nhau ⇔
            // `map(1−u+px) + map(u+px) = 1` ⇔ `px = 0`.
            val quarter = CameraOverlayFrame.quarterTurn(rot)
            assertEquals(quarter || px != 0, screenMirrorDiffers,
                "rot=$rot pan=($px,$py): lật-nguồn ≠ lật-màn-hình đúng khi và chỉ khi xoay ±90 HOẶC dịch ngang ≠ 0")
        }
    }

    // ── 3. TV: ma trận bước 1b — lật sau crop, trước xoay ──────────────────────────────────────────────────────

    @Test
    fun `TV - mep trai crop ra mep PHAI khung, y khong doi, va co tat thi ma tran y het truoc L7`() {
        val vw = 192; val vh = 360
        val m = requireNotNull(CameraOverlayTransform.matrix(vw, vh, CROP, 0, mirror = true))
        val left = CameraOverlayTransform.mapSource(m, vw, vh, CROP[0], 0f)
        val right = CameraOverlayTransform.mapSource(m, vw, vh, CROP[2], 1f)
        assertEquals(vw.toFloat(), left[0], 1e-2f, "mép TRÁI crop ⇒ mép PHẢI khung"); assertEquals(0f, left[1], 1e-2f)
        assertEquals(0f, right[0], 1e-2f, "mép PHẢI crop ⇒ mép TRÁI khung"); assertEquals(vh.toFloat(), right[1], 1e-2f)
        // Lật mà không crop, không xoay ⇒ vẫn cần ma trận (không phải `null`).
        assertNotNull(CameraOverlayTransform.matrix(360, 360, null, 0, mirror = true))
        // Cờ tắt ⇒ ĐÚNG ma trận cũ, từng số — đường đã chạy hiện trường không đổi (CLAUDE.md §6).
        for (rot in listOf(0, -90, 90, 180)) for (crop in listOf(CROP, null)) {
            val old = CameraOverlayTransform.matrix(vw, vh, crop, rot)
            val new = CameraOverlayTransform.matrix(vw, vh, crop, rot, mirror = false)
            if (old == null) assertEquals(null, new) else assertArrayEquals(old, new!!, 0f)
        }
    }

    // ── 4. GL ↔ TV: nghịch đảo của nhau, mọi góc, khung vuông lẫn không vuông ────────────────────────────────

    /**
     * GL đi **ra → nguồn** ([CameraDewarp.sample]); TV đi **nguồn → ra** ([CameraOverlayTransform.mapSource]). Ghép hai
     * chiều phải về đúng pixel ra ban đầu — nếu một đường lật trước xoay và đường kia lật sau, ca ±90 đỏ ngay.
     */
    @Test
    fun `GL va TV la nghich dao cua nhau khi lat, o moi goc xoay va ca khung khong vuong`() {
        for ((vw, vh) in listOf(360 to 360, 192 to 360, 360 to 192)) for (rot in listOf(0, -90, 90, 180)) {
            val g = uniforms(rot, mirror = true)
            val m = requireNotNull(CameraOverlayTransform.matrix(vw, vh, CROP, rot, mirror = true))
            for (u in GRID) for (v in GRID) {
                val (sx, sy) = src(g, u, v)
                val p = CameraOverlayTransform.mapSource(m, vw, vh, sx, sy)
                assertEquals(u * vw, p[0], 0.05f, "${vw}x$vh rot=$rot u=$u v=$v: x ra")
                assertEquals(v * vh, p[1], 0.05f, "${vw}x$vh rot=$rot u=$u v=$v: y ra")
            }
        }
    }

    // ── 5. Nhật ký nói `lật=`; passthrough (chụp thô) không bao giờ lật ─────────────────────────────────────

    @Test
    fun `describe in lat, va luot chup tho khong lat`() {
        assertTrue("lật=true" in uniforms(0, mirror = true).describe())
        assertTrue("lật=false" in uniforms(0, mirror = false).describe())
        assertFalse(CameraGlUniforms.passthrough().mirror, "khung THÔ là thứ HAL đưa — không lật")
        // Dấu của `w` là dấu duy nhất đọc được thành "lật": `textureT` chỉ đụng y/h (KDoc CameraGlUniforms).
        assertTrue(uniforms(0, mirror = true).srcRect[2] < 0f && uniforms(0, mirror = false).srcRect[2] > 0f)
    }
}
