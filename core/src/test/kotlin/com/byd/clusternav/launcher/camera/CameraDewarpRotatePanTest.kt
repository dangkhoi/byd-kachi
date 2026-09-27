package com.byd.clusternav.launcher.camera

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.75 — XOAY ⇔ NẮN, và DỊCH CỬA SỔ: hai thứ owner nhìn thấy trên xe, đo bằng số off-car ════════════════════
 *
 * Tách khỏi [CameraDewarpTest] vì trần 500 dòng của `CLAUDE.md` §4.1, và vì hai nhóm ca này trả lời **cùng một**
 * câu hỏi hiện trường ngày 27/09 (Seal, `camera_span = STRIP`, dải 1, `F 55 % · K 100 % · S 130 %`):
 *
 *  1. *"Khung ↺90 trông cong hơn khung rot 0 — có phải phép nắn bị xoay sai thứ tự không?"*
 *     **KHÔNG** [ĐO]: hai ảnh chụp từ xe (`gl-left-rot0-f65.png` 1280×960 và `gl-left-f65.png` 960×1280) lệch nhau
 *     **trung bình 3,1/255** (p95 = 9) sau khi xoay lại một ảnh ⇒ ↺90 vốn đã **đúng bằng** ảnh rot 0 đã xoay.
 *     Hai bài đầu khoá lại kết luận ấy để lần sau không ai đi lại vòng chẩn đoán này.
 *  2. *"Vậy làm sao dịch khung ra sau mà không làm cong?"* ⇒ `uPan` — xem KDoc [CameraDewarp.panLocal]. Ba đường
 *     owner đã thử trên xe: `scale` 140–145 % (**bác** — *"nặng"*), `cx −10 %` (**bác** — hết thẳng), dịch cửa sổ
 *     (**nhận**). Bài `dich giu duong thang, doi tam quang thi lam cong` là con số đứng sau ba chữ ấy.
 *
 * Ô **KHÔNG vuông** (1280×960 và 512×960) mới là phép thử thật: với ô vuông mọi cách viết sai đều trùng đáp án.
 */
class CameraDewarpRotatePanTest {

    private companion object {
        /** Dải pano thật `1280×960` (RE §2.1). */
        const val ASPECT_43 = 1280f / 960f

        /**
         * Bộ số owner **duyệt trên xe 27/09** (Seal, gương trái, `camera_span = STRIP`, dải 1, `amount = 100`):
         * `camera_dewarp_focal = 55` ⇒ `F = 0,4523 × 0,55 = 0,2488`; `k = 100` ⇒ `K = 0,4523`; `scale = 130`.
         * Tâm `(0,5, 0,5)` vì trọn dải ⇒ tâm dải rơi đúng giữa ô.
         */
        val CAR = DewarpParams(amount = 1f, focal = 0.2488f, k = 0.452335f, scale = 1.30f)

        /** Vùng cắt trọn dải 1 của ảnh `5120×960` — đúng `uSrcRect` mà xe ghi vào `logcat` ngày 27/09. */
        val RECT: FloatArray get() = CameraDewarp.srcRect(floatArrayOf(0.25f, 0f, 0.5f, 1f))
    }

    // ── (k) XOAY ⇔ NẮN — ±90 phải đúng bằng "ảnh rot 0 đã xoay" (xe 27/09) ─────────────────────────────────────

    /**
     * ⚠ Bài khoá **thứ tự xoay/nắn**, dựng từ đúng bộ số owner duyệt trên xe 27/09 (Seal, `camera_span = STRIP`).
     *
     * Trên xe, khung ↺90 *trông* cong hơn khung rot 0 ⇒ nghi phép nắn bị xoay sai thứ tự (hoặc lấy tỉ lệ **cửa sổ**
     * thay vì tỉ lệ ô NGUỒN). Phép đo chốt lại: hai ảnh chụp từ xe khác nhau **trung bình 3,1/255** sau khi xoay lại
     * ⇒ đường xoay vốn đã đúng, chỗ cong là chuyện của tham số ống kính chứ không của góc xoay
     * (`camera-dewarp-gl.md` §"Xe 27/09"). Bài này là cái khoá để lần sau không phải đi lại vòng chẩn đoán ấy.
     *
     * Ô **KHÔNG vuông** (1280×960 và 512×960) mới là phép thử thật: với ô vuông mọi cách viết sai đều trùng đáp án.
     */
    @Test
    fun `xoay 90 chi la anh rot 0 da xoay`() {
        val rect = RECT
        for ((w, h) in listOf(1280f to 960f, 512f to 960f)) {
            val aspect = DewarpParams.aspectOf(w, h)
            for (rot in listOf(-90, 90, 180)) {
                for (i in 0..8) for (j in 0..8) {
                    val u = i / 8f
                    val v = j / 8f
                    val got = CameraDewarp.sample(u, v, rot, CAR, aspect, rect)
                    val (ru, rv) = CameraDewarp.rotateDstToLocal(u, v, rot)
                    val want = CameraDewarp.sample(ru, rv, 0, CAR, aspect, rect)
                    val tag = "${w.toInt()}x${h.toInt()} rot=$rot ($u,$v)"
                    assertEquals(want == null, got == null, "$tag: một bên đen một bên không")
                    if (want == null) continue
                    assertEquals(want.first.toDouble(), got!!.first.toDouble(), 1e-4, "$tag trục u")
                    assertEquals(want.second.toDouble(), got.second.toDouble(), 1e-4, "$tag trục v")
                }
            }
        }
    }

    /**
     * Và phép thử **không tautology** của cùng một điều: một đường thẳng của thế giới phải **thẳng ở MỌI góc xoay**.
     *
     * Khác bài trên ở chỗ nó không so hàm với chính nó: nó đi từ mô hình **chụp** ([CameraDewarp.idealEquidistantSource])
     * qua [CameraDewarp.forwardSrcToDst] rồi **gỡ xoay**, nên một ngày nào đó ai đó đảo `uAspect` theo góc xoay hay
     * dời phép xoay xuống sau phép nắn thì chính bài này đỏ, dù bài trên vẫn xanh.
     */
    @Test
    fun `duong thang van thang o MOI goc xoay`() {
        for ((w, h) in listOf(1280f to 960f, 512f to 960f)) {
            val aspect = DewarpParams.aspectOf(w, h)
            val p = DewarpParams.derive(rectWidthPx = w, rectHeightPx = h, imageCircleDiameterPx = 960f)
            for (rot in listOf(0, -90, 90, 180)) {
                for (sceneY in listOf(0f, 0.35f, -0.8f)) {
                    val pts = (-10..10).map { n ->
                        val (su, sv) = CameraDewarp.idealEquidistantSource(n / 10f, sceneY, p, aspect)
                        val local = CameraDewarp.forwardSrcToDst(su, sv, p, aspect)
                            ?: error("diem thuoc tam nhin ma khong nghich dao duoc: n=$n")
                        // Gỡ xoay = xoay ngược: `rotateDstToLocal(·, −rot)` là nghịch đảo của `rotateDstToLocal(·, rot)`.
                        CameraDewarp.rotateDstToLocal(local.first, local.second, -rot)
                    }
                    val lech = maxResidual(pts)
                    assertTrue(lech < 1e-3, "${w.toInt()}x${h.toInt()} rot=$rot y=$sceneY bi cong: lech $lech")
                }
            }
        }
    }

    // ── (l) DỊCH CỬA SỔ (`uPan`) — dời khung mà KHÔNG dời trục quang ───────────────────────────────────────────

    @Test
    fun `dich 0 khong doi mot pixel nao`() {
        val rect = RECT
        for (rot in listOf(0, -90, 90, 180)) for (i in 0..4) for (j in 0..4) {
            val u = i / 4f
            val v = j / 4f
            val base = CameraDewarp.sample(u, v, rot, CAR, ASPECT_43, rect)
            val panned = CameraDewarp.sample(u, v, rot, CAR.copy(panX = 0f, panY = 0f), ASPECT_43, rect)
            assertEquals(base, panned, "dich 0 phai la phep dong nhat (rot=$rot, $u,$v)")
        }
    }

    /**
     * Dịch là **tịnh tiến ĐÚNG bằng `pan`** trong ô chưa xoay: khung ra dịch đi, ảnh **không** đổi hình.
     *
     * Đo bằng cách đối chiếu hai lượt: điểm `(u, v)` với `pan` phải lấy đúng pixel nguồn mà điểm `(u + pan)` lấy khi
     * `pan = 0`. Đó chính là định nghĩa *"dịch cửa sổ"*, và nó khác hẳn `centerX` (dưới đây).
     */
    @Test
    fun `dich cua so la tinh tien dung bang pan, khong doi hinh`() {
        val rect = RECT
        for ((px, py) in listOf(0.10f to 0f, -0.10f to 0f, 0f to 0.25f, -0.5f to 0.5f)) {
            val p = CAR.copy(panX = px, panY = py)
            for (i in 1..5) for (j in 1..5) {
                val u = i / 6f
                val v = j / 6f
                val got = CameraDewarp.sample(u, v, 0, p, ASPECT_43, rect) ?: continue
                val want = CameraDewarp.sample(u + px, v + py, 0, CAR, ASPECT_43, rect) ?: continue
                assertEquals(want.first.toDouble(), got.first.toDouble(), 1e-5, "pan=($px,$py) tai ($u,$v)")
                assertEquals(want.second.toDouble(), got.second.toDouble(), 1e-5, "pan=($px,$py) tai ($u,$v)")
            }
        }
    }

    /**
     * **Cái giá trị nhất của núm này**: dịch giữ ảnh **THẲNG**, còn dời tâm quang thì KHÔNG.
     *
     * [ĐO] xe 27/09 — owner bác `cx −10 %` (*"hết thẳng"*) và `scale 140–145 %` (*"nặng"*), xin đúng một phép dịch.
     * Bài này là con số đứng sau lời ấy: cùng `−10 %`, đi bằng `pan` thì độ lệch giữ nguyên mức của `pan = 0`, đi
     * bằng `centerX` thì lệch gấp **hàng chục lần**.
     */
    @Test
    fun `dich giu duong thang, doi tam quang thi lam cong`() {
        val aspect = ASPECT_43
        val p0 = DewarpParams.derive(rectWidthPx = 1280f, rectHeightPx = 960f, imageCircleDiameterPx = 960f)
        fun lechCuaKhung(p: DewarpParams, shift: Float): Double {
            // Đường thẳng của thế giới ⇒ ảnh nguồn ⇒ nắn về khung ⇒ trừ đi phép dịch để so trên cùng một hệ.
            val pts = (-10..10).map { n ->
                val (su, sv) = CameraDewarp.idealEquidistantSource(n / 10f, 0.35f, p0, aspect)
                val d = CameraDewarp.forwardSrcToDst(su, sv, p, aspect) ?: error("khong nghich dao duoc n=$n")
                (d.first - shift) to d.second
            }
            return maxResidual(pts)
        }
        val thang = lechCuaKhung(p0, 0f)
        val byPan = lechCuaKhung(p0, -0.10f)            // `pan` không vào `forwardSrcToDst` ⇒ chỉ là một phép trừ
        val byCentre = lechCuaKhung(p0.copy(centerX = p0.centerX - 0.10f), 0f)
        assertEquals(thang, byPan, 1e-9, "dich cua so KHONG doi hinh — chi truot khung")
        assertTrue(byCentre > thang * 20, "doi tam quang phai lam CONG han: $byCentre so voi $thang")
    }

    @Test
    fun `dich duoc kep ve nua o va chiu duoc NaN`() {
        assertEquals(DewarpParams.MAX_PAN, DewarpParams(panX = 9f).clamped().panX, 1e-6f)
        assertEquals(DewarpParams.MIN_PAN, DewarpParams(panY = -9f).clamped().panY, 1e-6f)
        assertEquals(DewarpParams.DEFAULT_PAN, DewarpParams(panX = Float.NaN).clamped().panX, 1e-6f)
        assertEquals(DewarpParams.DEFAULT_PAN, DewarpParams(panY = Float.POSITIVE_INFINITY).clamped().panY, 1e-6f)
        // Miền `%` của prefs phải nằm TRỌN trong miền của mô hình (nếu không, núm kéo tới biên rồi bị kẹp im lặng).
        assertEquals(DewarpParams.MIN_PAN.toDouble(), CameraDewarpPrefs.PAN_MIN / 100.0, 1e-9)
        assertEquals(DewarpParams.MAX_PAN.toDouble(), CameraDewarpPrefs.PAN_MAX / 100.0, 1e-9)
    }

    /** Dịch phải ăn **cả khi độ nắn = 0** — owner vẫn được kéo khung ở đường thô (khớp shader: `uPan` ngoài khối `if`). */
    @Test
    fun `dich an ca khi do nan 0`() {
        val rect = RECT
        val tat = DewarpParams(amount = 0f, panX = 0.10f)
        val got = CameraDewarp.sample(0.5f, 0.5f, 0, tat, ASPECT_43, rect)!!
        val want = CameraDewarp.applySrcRect(0.6f, 0.5f, rect)
        assertEquals(want.first.toDouble(), got.first.toDouble(), 1e-6, "amount = 0 ⇒ chi con phep dich")
        assertEquals(want.second.toDouble(), got.second.toDouble(), 1e-6)
    }

    /**
     * ═══ MỘT núm `camera_dewarp_pan_x`, HAI bên ⇒ hai DẤU — [ĐO khung thô xe 27/09 09:58] ════════════════════
     *
     * Khung `camera_frame` `5120×960` của buổi xe cho thấy hai camera gương là **ảnh soi gương của nhau**: thân xe
     * của chính mình nằm ở mép **PHẢI** ô gương trái (dải 1) và mép **TRÁI** ô gương phải (dải 2). Tương quan chuẩn
     * hoá trên đúng vùng thân xe: lật ngang **0,715** · không lật **0,152** (nền: hai nửa cùng một ô 0,550).
     *
     * ⇒ Trục `+x` của ô trỏ về hai phía **ngược nhau**. Cộng thẳng một pref vào cả hai bên (đường 2.75 trước lượt
     * soát này) làm owner xin *"kéo ra sau"* mà chỉ một bên đi ra sau, bên kia đi ra trước — **không có lời báo
     * nào**, vì cả hai khung vẫn thẳng và vẫn ra hình. Bài này khoá đúng vế đó: cùng một pref, hai dấu ngược.
     */
    @Test
    fun `mot pref pan_x cho hai dau nguoc nhau o hai guong`() {
        fun panOf(pct: Int, left: Boolean): Float = CameraGlUniforms.of(
            crop = floatArrayOf(0.25f, 0f, 0.5f, 1f), srcCentreX = 0.375f, srcCentreY = 0.5f,
            streamW = 5120, streamH = 960, rotationDeg = 0,
            panXPct = pct, panXSign = CameraDewarpPrefs.panXSign(left),
        ).panX
        // Gương TRÁI giữ nguyên nghĩa đã dùng trên xe (CLAUDE.md §6): pref âm ⇒ `panX` âm.
        assertEquals(-0.20f, panOf(-20, left = true), 1e-6f, "guong TRAI: giu nguyen dau cua pref")
        // Gương PHẢI là ô soi gương ⇒ **cùng một** pref phải cho dấu ngược lại để đi cùng một phía của XE.
        assertEquals(0.20f, panOf(-20, left = false), 1e-6f, "guong PHAI: dau phai lat, neu khong hai ben di nguoc nhau")
        assertEquals(1, CameraDewarpPrefs.panXSign(left = true))
        assertEquals(-1, CameraDewarpPrefs.panXSign(left = false))
        // Chỉ trục x lật (hai camera soi gương quanh trục DỌC của xe) — `pan_y` phải giống nhau ở hai bên.
        fun panYOf(left: Boolean): Float = CameraGlUniforms.of(
            crop = floatArrayOf(0.25f, 0f, 0.5f, 1f), srcCentreX = 0.375f, srcCentreY = 0.5f,
            streamW = 5120, streamH = 960, rotationDeg = 0,
            panYPct = -20, panXSign = CameraDewarpPrefs.panXSign(left),
        ).panY
        assertEquals(panYOf(left = true), panYOf(left = false), 1e-6f, "pan_y KHONG duoc lat theo ben")
        // `0` là `0` ở cả hai bên ⇒ xe không chạm núm thì hai đường vẫn trùng nhau từng bit (mặc định 2.74).
        assertEquals(0f, panOf(0, left = false), 1e-6f)
    }

    /** Lệch vuông góc lớn nhất so với đường qua điểm đầu và điểm cuối — cùng phép đo với [CameraDewarpTest]. */
    private fun maxResidual(pts: List<Pair<Float, Float>>): Double {
        val (x0, y0) = pts.first()
        val (x1, y1) = pts.last()
        val dx = (x1 - x0).toDouble()
        val dy = (y1 - y0).toDouble()
        val len = hypot(dx, dy)
        return pts.maxOf { (x, y) -> abs(dx * (y - y0) - dy * (x - x0)) / len }
    }
}
