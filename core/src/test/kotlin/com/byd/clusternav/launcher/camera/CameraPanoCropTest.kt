package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraSignalPolicy.CamView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ R8-A · VÙNG CẮT trong ảnh pano + CỠ CỬA SỔ theo bề rộng/hình khung — bằng SỐ, off-car ══════════════════════
 *
 * Spec `docs/specs/kachi-274-ux-voice-camera.html` R8 · RE `diagnostics/electro-camera-RE-2026-09-26.md` §2.1, §5
 * K10, §6.1. Ba nhóm, ba loại bằng chứng:
 *
 *  1. **Mặc định trùng 2.73 từng chữ số** — hai rect `0.25…0.35` / `0.65…0.75` ghim bằng literal, sai số `0`. Đây là
 *     bài quan trọng nhất của cả lượt đổi: mọi thứ mới đứng sau chip, nên nếu bài này xanh thì xe sáng mai vẫn thấy
 *     đúng khung của 2.73 kể cả khi chưa ai chạm Cài đặt (CLAUDE.md §6).
 *  2. **Hình học suy ra đúng** cho mọi (dải × bề rộng × bên × hình khung) — kể cả hai dải `0`/`3` chưa ai nhìn.
 *  3. **Cửa sổ tự đúng tỉ lệ** — cùng [CameraOverlayFrame] của 2.73, không sửa một dòng nào, nhận crop rộng hơn thì
 *     tự cho khung khác; và bốn góc crop vẫn phủ **kín** bốn góc cửa sổ sau khi xoay (không mép đen).
 */
class CameraPanoCropTest {

    private val C = CameraPanoCrop
    private val P = CameraSignalPolicy

    /** Ô vuông cho phép của overlay hôm nay (`SQUARE_RATIO` × chiều cao màn 720 ⇒ 360). */
    private val area = 360

    private fun crop(
        view: CamView = CamView.MIRROR_LEFT,
        left: Boolean = true,
        strip: Int = C.defaultStrip(left),
        span: String = P.defaultSpan(),
        shape: String = P.defaultShape(),
        pct: Int = P.CIRCLE_PCT_DEFAULT,
    ) = C.cropFor(view, left = left, strip = strip, span = span, shape = shape, circlePct = pct)

    private fun assertRect(expect: FloatArray, actual: FloatArray?, delta: Float = 0f) {
        assertNotNull(actual, "crop không được null ở ca này")
        assertEquals(4, actual!!.size)
        expect.indices.forEach { assertEquals(expect[it], actual[it], delta, "phần tử $it của rect") }
    }

    // ══ (1) MẶC ĐỊNH = 2.73 ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Hai rect đang chạy hiện trường, **literal**, sai số `0f`.
     *
     * Ghim luôn cả `CamView.crop` gốc: nếu ai đó đổi hằng trong enum mà không đổi công thức (hay ngược lại) thì hai
     * nguồn lệch nhau, và bài này là chỗ duy nhất kẹp chúng lại.
     */
    @Test fun `mac dinh trung 2 rect cua 2 73`() {
        assertRect(floatArrayOf(0.25f, 0f, 0.35f, 1f), crop(CamView.MIRROR_LEFT, left = true))
        assertRect(floatArrayOf(0.65f, 0f, 0.75f, 1f), crop(CamView.MIRROR_RIGHT, left = false, strip = C.defaultStrip(false)))
        // …và đúng bằng hằng trong enum (thứ 2.73 truyền thẳng vào overlay).
        assertRect(CamView.MIRROR_LEFT.crop!!, crop(CamView.MIRROR_LEFT, left = true))
        assertRect(CamView.MIRROR_RIGHT.crop!!, crop(CamView.MIRROR_RIGHT, left = false, strip = 2))
    }

    /**
     * Pref RÁC / chưa có (xe nâng cấp từ 2.73: cả bốn khoá đều vắng) ⇒ **vẫn** đúng hai rect cũ.
     *
     * Đây là ca "di cư": không có khoá nào trên đĩa thì chỗ đọc trả mặc định `:core`, mà mặc định `:core` phải dẫn về
     * đúng hình đang chạy. Giá trị lạ đi qua `prefs_set` cũng rơi về đây (không ném, không hiện một khung thứ ba).
     */
    @Test fun `pref rac hoac vang thi ve dung rect cu`() {
        assertRect(
            floatArrayOf(0.25f, 0f, 0.35f, 1f),
            C.cropFor(CamView.MIRROR_LEFT, left = true, strip = 99, span = "WIDE?", shape = "CIRCLE?", circlePct = 999),
        )
        assertRect(
            floatArrayOf(0.65f, 0f, 0.75f, 1f),
            C.cropFor(CamView.MIRROR_RIGHT, left = false, strip = -7, span = "", shape = "", circlePct = 0),
        )
    }

    /** View KHÔNG phải dải pano (không có crop dựng sẵn) ⇒ nguyên khung, y 2.73 — trừ hình tròn (ca riêng dưới). */
    @Test fun `view khong pano giu nguyen khung`() {
        listOf(CamView.FRONT_LEFT, CamView.REAR_LEFT, CamView.LEFT_FRONT).forEach {
            assertNull(crop(it), "${it.name} không có crop dựng sẵn ⇒ phải giữ nguyên khung")
            assertNull(crop(it, span = P.SPAN_STRIP), "${it.name}: bề rộng không được tự sinh crop cho view nguyên khung")
        }
    }

    // ══ (2) HÌNH HỌC ═════════════════════════════════════════════════════════════════════════════════════════

    /** Trọn dải: 4 dải × 0.25, liền nhau, phủ kín `[0,1]`, cao trọn khung — khớp shader Electro (`x·0.25 + offset`). */
    @Test fun `tron dai la 4 dai 0 25 lien nhau`() {
        val rects = C.STRIPS_ALL.map { C.stripCrop(it) }
        assertEquals(4, rects.size)
        rects.forEachIndexed { i, r ->
            assertEquals(i * 0.25f, r[0], 0f, "dải $i bắt đầu ở ${i * 0.25f}")
            assertEquals((i + 1) * 0.25f, r[2], 0f, "dải $i kết ở ${(i + 1) * 0.25f}")
            assertEquals(0f, r[1], 0f); assertEquals(1f, r[3], 0f)
            assertEquals(0.25f, r[2] - r[0], 1e-6f, "mỗi dải rộng đúng 25 %")
        }
        assertEquals(0f, rects.first()[0], 0f); assertEquals(1f, rects.last()[2], 0f)
        // Hai dải mà 2.73 đang dùng: vệt hẹp NẰM TRONG dải của nó (đây là mệnh đề mà cả phương án A dựa lên).
        listOf(true to 1, false to 2).forEach { (left, s) ->
            val n = C.narrowCrop(s, left)
            val st = C.stripCrop(s)
            assertTrue(n[0] >= st[0] && n[2] <= st[2], "vệt hẹp của bên ${if (left) "trái" else "phải"} phải nằm trong dải $s")
        }
    }

    /** Vệt hẹp: rộng đúng 0.10 ở mọi dải; neo ĐẦU dải khi trái, CUỐI dải khi phải (đối xứng gương quanh 0.5). */
    @Test fun `vet hep rong 0 10 neo theo ben o moi dai`() {
        C.STRIPS_ALL.forEach { s ->
            val l = C.narrowCrop(s, left = true)
            val r = C.narrowCrop(s, left = false)
            assertEquals(0.10f, l[2] - l[0], 1e-6f, "dải $s trái rộng 0.10")
            assertEquals(0.10f, r[2] - r[0], 1e-6f, "dải $s phải rộng 0.10")
            assertEquals((s * 0.25).toFloat(), l[0], 1e-6f, "trái neo ĐẦU dải $s")
            assertEquals(((s + 1) * 0.25).toFloat(), r[2], 1e-6f, "phải neo CUỐI dải $s")
        }
        // Đối xứng gương của 2.73: dải 1 trái ↔ dải 2 phải.
        val l1 = C.narrowCrop(1, left = true)
        val r2 = C.narrowCrop(2, left = false)
        assertEquals(1f - l1[0], r2[2], 1e-6f); assertEquals(1f - l1[2], r2[0], 1e-6f)
    }

    /** Chip dải đổi được sang cả hai dải CHƯA AI NHÌN (0 và 3) — đúng việc owner cần làm trên xe sáng mai. */
    @Test fun `doi chi so dai duoc sang ca dai 0 va 3`() {
        assertRect(floatArrayOf(0f, 0f, 0.10f, 1f), crop(strip = 0))
        assertRect(floatArrayOf(0.75f, 0f, 0.85f, 1f), crop(strip = 3))
        assertRect(floatArrayOf(0.75f, 0f, 1f, 1f), crop(strip = 3, span = P.SPAN_STRIP))
        assertRect(floatArrayOf(0.90f, 0f, 1f, 1f), crop(left = false, strip = 3))
    }

    /**
     * Hình TRÒN = ô vuông `960×960` giữa dải (tỉ lệ nguồn 960/5120 = 0.1875) — **bỏ qua** bề rộng, vì nó tự là một bề
     * rộng thứ ba (0.1875, nằm giữa 0.10 và 0.25).
     */
    @Test fun `hinh tron lay o vuong giua dai`() {
        val expect1 = floatArrayOf(0.28125f, 0f, 0.46875f, 1f)   // tâm dải 1 = 0.375; nửa cạnh = 0.09375
        assertRect(expect1, crop(shape = P.SHAPE_ROUND))
        assertRect(expect1, crop(shape = P.SHAPE_ROUND, span = P.SPAN_STRIP), delta = 0f)
        assertRect(floatArrayOf(0.53125f, 0f, 0.71875f, 1f), crop(CamView.MIRROR_RIGHT, left = false, strip = 2, shape = P.SHAPE_ROUND))
        // Ô vuông THẬT theo pixel: 0.1875 × 5120 = 960 = chiều cao ảnh.
        val r = crop(shape = P.SHAPE_ROUND)!!
        assertEquals(960f, (r[2] - r[0]) * 5120f, 1e-2f, "cạnh ngang = 960 px")
        assertEquals(960f, (r[3] - r[1]) * 960f, 1e-2f, "cạnh dọc = 960 px")
        // …và nó nằm gọn trong dải (0.25 rộng hơn 0.1875).
        val st = C.stripCrop(1)
        assertTrue(r[0] > st[0] && r[2] < st[2], "ô vuông phải nằm trong dải 1")
    }

    /**
     * `camera_circle_scale` co ô vuông quanh tâm dải — núm để owner chữa cái [ĐOÁN] *"đường kính vòng ảnh = chiều cao
     * dải"* ngay trên xe. Trần 100 vì ở 100 cạnh đã bằng trọn chiều cao ảnh (không còn pixel để giãn).
     */
    @Test fun `circle scale co o vuong quanh tam dai`() {
        val r = crop(shape = P.SHAPE_ROUND, pct = 50)!!
        assertEquals(0.375f, (r[0] + r[2]) / 2f, 1e-6f, "tâm ngang phải giữ tâm dải")
        assertEquals(0.5f, (r[1] + r[3]) / 2f, 1e-6f, "tâm dọc phải giữ giữa khung")
        assertEquals(480f, (r[2] - r[0]) * 5120f, 1e-2f, "50 % ⇒ cạnh 480 px")
        assertEquals(480f, (r[3] - r[1]) * 960f, 1e-2f, "50 % ⇒ cạnh 480 px ở cả hai trục (vẫn VUÔNG)")
        assertFalse(P.isCirclePct(P.CIRCLE_PCT_MAX + 1), "quá 100 % không có pixel nào để lấy ⇒ phải bị từ chối")
        assertFalse(P.isCirclePct(P.CIRCLE_PCT_MIN - 1))
        assertTrue(P.isCirclePct(P.CIRCLE_PCT_DEFAULT))
    }

    /** Hình tròn trên view NGUYÊN KHUNG: chưa biết tỉ lệ nguồn ⇒ vẫn nguyên khung (cửa sổ đã vuông ⇒ bo vẫn ra tròn). */
    @Test fun `hinh tron tren view nguyen khung khong doan ti le`() {
        assertNull(crop(CamView.FRONT_LEFT, shape = P.SHAPE_ROUND), "hint 0 ⇒ KHÔNG đoán tỉ lệ")
        // Có tỉ lệ thì cắt vuông giữa khung — chứng minh bằng công thức thuần (không có CamView nào như vậy hôm nay).
        val sq = C.squareCrop(cx = 0.5, ratio = 960.0 / 5120.0, pct = 100)
        assertEquals(0.5f, (sq[0] + sq[2]) / 2f, 1e-6f)
        assertEquals(0.1875f, sq[2] - sq[0], 1e-6f)
    }

    /** Mọi rect trả về đều nằm trong `[0,1]` và không suy biến — kể cả ca dải biên + ô vuông. */
    @Test fun `moi rect nam trong 0 1 va khong suy bien`() {
        val all = mutableListOf<FloatArray>()
        listOf(true, false).forEach { left ->
            C.STRIPS_ALL.forEach { s ->
                P.SPANS.forEach { sp ->
                    P.SHAPES.forEach { sh ->
                        listOf(P.CIRCLE_PCT_MIN, P.CIRCLE_PCT_DEFAULT).forEach { pct ->
                            all += crop(CamView.MIRROR_LEFT, left, s, sp, sh, pct)!!
                        }
                    }
                }
            }
        }
        assertEquals(2 * 4 * 2 * 2 * 2, all.size)
        all.forEach { r ->
            r.forEach { assertTrue(it in 0f..1f, "toạ độ ${r.joinToString()} ra ngoài ảnh") }
            assertTrue(r[2] - r[0] >= CameraOverlayTransform.MIN_SPAN, "bề rộng suy biến: ${r.joinToString()}")
            assertTrue(r[3] - r[1] >= CameraOverlayTransform.MIN_SPAN, "bề cao suy biến: ${r.joinToString()}")
        }
    }

    // ══ (3) CỬA SỔ TỰ ĐÚNG TỈ LỆ (2.73 [CameraOverlayFrame], KHÔNG sửa) ══════════════════════════════════════

    private fun fit(crop: FloatArray?, rot: Int) =
        CameraOverlayFrame.fit(streamW = 5120, streamH = 960, crop = crop, rotationDeg = rot, areaW = area, areaH = area)

    /**
     * Bảng cỡ cửa sổ cho **cả ba** bề rộng × 4 góc xoay — số tính tay, không lấy từ code.
     *
     * • HẸP `512×960` ⇒ dọc `192×360`, xoay ±90 ⇒ ngang `360×192` (đúng ví dụ trong KDoc [CameraOverlayFrame]).
     * • DẢI `1280×960` ⇒ ngang `360×270`, xoay ±90 ⇒ **dọc `270×360`** — tức "một dải đầy xoay ±90 cho khung dọc"
     *   xảy ra **tự động**, không ai phải đổi cỡ cửa sổ.
     * • TRÒN `960×960` ⇒ **vuông `360×360` ở mọi góc** (xoay hình vuông vẫn là hình vuông ⇒ vòng bo vẫn là vòng tròn).
     */
    @Test fun `co cua so dung ti le cho 3 be rong x 4 goc xoay`() {
        val narrow = crop()!!
        val strip = crop(span = P.SPAN_STRIP)!!
        val round = crop(shape = P.SHAPE_ROUND)!!
        listOf(0, 180).forEach { rot ->
            assertEquals(192 to 360, fit(narrow, rot).let { it.w to it.h }, "HẸP rot=$rot")
            assertEquals(360 to 270, fit(strip, rot).let { it.w to it.h }, "DẢI rot=$rot")
            assertEquals(360 to 360, fit(round, rot).let { it.w to it.h }, "TRÒN rot=$rot")
        }
        listOf(-90, 90, 270).forEach { rot ->
            assertEquals(360 to 192, fit(narrow, rot).let { it.w to it.h }, "HẸP rot=$rot")
            assertEquals(270 to 360, fit(strip, rot).let { it.w to it.h }, "DẢI rot=$rot")
            assertEquals(360 to 360, fit(round, rot).let { it.w to it.h }, "TRÒN rot=$rot")
        }
        assertTrue(fit(strip, 0).streamKnown && fit(round, 90).streamKnown, "có gợi ý 5120×960 ⇒ cửa sổ phải nhận là đã biết")
    }

    /**
     * Sau crop + xoay, **bốn góc crop đi đúng bốn góc cửa sổ** ⇒ không một pixel đen nào — cho cả ba bề rộng và cả
     * bốn góc xoay mà `CameraSignalPolicy.rotationDegrees` sinh ra.
     *
     * Đây là phép kiểm *"đường cũ vẫn đúng với crop mới"*: ma trận của 2.73 ([CameraOverlayTransform]) không đổi một
     * dòng, nên chỗ duy nhất có thể sai là cặp (crop mới, cửa sổ mới) — và nó được kiểm ở đây bằng số.
     */
    @Test fun `bon goc crop phu kin cua so o moi be rong va moi goc xoay`() {
        val crops = mapOf(
            "HẸP" to crop()!!,
            "DẢI" to crop(span = P.SPAN_STRIP)!!,
            "TRÒN" to crop(shape = P.SHAPE_ROUND)!!,
        )
        crops.forEach { (name, c) ->
            listOf(-90, 0, 90, 180).forEach { rot ->
                val f = fit(c, rot)
                val m = CameraOverlayTransform.matrix(f.w, f.h, c, rot)
                val got = listOf(c[0] to c[1], c[2] to c[1], c[0] to c[3], c[2] to c[3]).map { (u, v) ->
                    val p = CameraOverlayTransform.mapSource(m, f.w, f.h, u, v)
                    Math.round(p[0]) to Math.round(p[1])
                }.toSet()
                val want = setOf(0 to 0, f.w to 0, 0 to f.h, f.w to f.h)
                assertEquals(want, got, "$name rot=$rot: bốn góc crop phải phủ đúng bốn góc cửa sổ ${f.w}x${f.h}")
            }
        }
    }
}
