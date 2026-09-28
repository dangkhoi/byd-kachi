package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import com.byd.clusternav.launcher.camera.CameraSignalPolicy.CamView
import org.junit.jupiter.api.Test

/**
 * CAM-SL6-RIGHT (2026-09-28). Owner chọn một số camera khác hai góc Gương trên Sealion 6: CÓ hình, nhưng ra
 * NGUYÊN KHUNG ghép 4 dải. Gốc: [CameraPanoCrop.cropFor] mở đầu bằng `view.crop`, mà chỉ hai góc Gương mang
 * sẵn rect ⇒ sáu góc kia rơi vào nhánh nguyên khung với MỌI tổ hợp dải/bề rộng/hình khung.
 *
 * Bố cục dải đã đo GIỐNG NHAU trên hai đời xe — [ĐO Seal 27/09] dải 1 = gương trái, dải 2 = gương phải;
 * [ĐO SL6 28/09, ảnh khung thô owner] thứ tự `sau · trái · phải · trước` ⇒ 0 = sau, 1 = trái, 2 = phải,
 * 3 = trước. Nên [CameraPanoCrop.defaultStrip] đúng cho cả hai, không rẽ nhánh theo dòng xe (CLAUDE.md §7).
 */
class CameraPanoSourceTest {

    private val C = CameraPanoCrop
    private val P = CameraSignalPolicy
    private val mirrorL = CamView.MIRROR_LEFT
    private val other = CamView.LEFT_FRONT   // một trong sáu góc không mang rect

    private fun crop(view: CamView, left: Boolean, pano: Int?, span: String = P.SPAN_STRIP) =
        C.cropFor(view, left = left, strip = C.defaultStrip(left), span = span,
            shape = P.SHAPE_RECT, circlePct = P.CIRCLE_PCT_DEFAULT, panoStrip = pano)

    // ─── Seal KHÔNG được đổi một pixel nào (CLAUDE.md §6) ────────────────────

    @Test
    fun `hai goc Guong khong bao gio di qua duong moi`() {
        listOf(CamView.MIRROR_LEFT to true, CamView.MIRROR_RIGHT to false).forEach { (v, left) ->
            listOf(C.PANO_AUTO, C.PANO_NONE, "0", "3", null).forEach { mode ->
                assertNull(
                    C.panoStripFor(v, mode, left),
                    "${v.name} mang sẵn rect ⇒ PHẢI trả null dù người lái chọn gì: đường của nó đang chạy tốt " +
                        "ngoài hiện trường, không được đụng",
                )
            }
        }
    }

    @Test
    fun `vung cat cua hai goc Guong giu NGUYEN`() {
        val before = floatArrayOf(0.25f, 0f, 0.35f, 1f)
        assertArrayEquals(before, crop(mirrorL, left = true, pano = null, span = P.SPAN_NARROW), 1e-6f,
            "vệt hẹp của 2.73 cho gương trái")
        // kể cả khi có ai đó truyền panoStrip vào, rect dựng sẵn vẫn thắng
        assertArrayEquals(before, crop(mirrorL, left = true, pano = 3, span = P.SPAN_NARROW), 1e-6f,
            "view.crop phải thắng panoStrip — không có đường nào đổi được hành vi Seal")
    }

    // ─── Sáu góc còn lại: nay cắt được ───────────────────────────────────────

    @Test
    fun `truoc day SAU goc kia ra nguyen khung voi MOI to hop`() {
        listOf(P.SPAN_STRIP, P.SPAN_NARROW).forEach { span ->
            listOf(P.SHAPE_RECT, P.SHAPE_ROUND).forEach { shape ->
                assertNull(
                    C.cropFor(other, left = true, strip = 1, span = span, shape = shape,
                        circlePct = P.CIRCLE_PCT_DEFAULT, panoStrip = null),
                    "không coi là ảnh ghép ⇒ vẫn nguyên khung y như trước (đường thoát cho camera ĐƠN)",
                )
            }
        }
    }

    @Test
    fun `tu dong cat dung dai theo ben`() {
        assertEquals(1, C.panoStripFor(other, C.PANO_AUTO, left = true), "trái ⇒ dải 1 [ĐO cả Seal lẫn SL6]")
        assertEquals(2, C.panoStripFor(other, C.PANO_AUTO, left = false), "phải ⇒ dải 2")
        assertArrayEquals(floatArrayOf(0.25f, 0f, 0.50f, 1f), crop(other, true, 1), 1e-6f, "dải 1 = [0,25 … 0,50]")
        assertArrayEquals(floatArrayOf(0.50f, 0f, 0.75f, 1f), crop(other, false, 2), 1e-6f, "dải 2 = [0,50 … 0,75]")
    }

    @Test
    fun `ep mot dai va duong thoat nguyen khung`() {
        assertEquals(3, C.panoStripFor(other, "3", left = true), "ép dải 3 (trước)")
        assertEquals(0, C.panoStripFor(other, "0", left = false), "ép dải 0 (sau)")
        assertNull(C.panoStripFor(other, C.PANO_NONE, left = true), "nguyên khung = đường thoát cho camera đơn")
        assertEquals(1, C.panoStripFor(other, "rác", left = true), "giá trị lạ ⇒ lùi về theo bên, không ném")
        assertEquals(2, C.panoStripFor(other, null, left = false), "chưa chọn ⇒ theo bên")
        assertEquals(2, C.panoStripFor(other, "9", left = false), "ngoài dải ⇒ lùi về theo bên")
    }

    // ─── BẤT BIẾN chống lệch pha: cắt · cỡ nguồn · tâm quang phải đi cùng nhau ─

    @Test
    fun `co nguon PHAI di kem khi da cat dai`() {
        assertEquals(5120, C.streamW(other, 1), "thiếu cỡ nguồn ⇒ cửa sổ không biết tỉ lệ ⇒ khung bẹt bị kéo bẹp")
        assertEquals(960, C.streamH(other, 1))
        assertEquals(0, C.streamW(other, null), "không cắt ⇒ giữ gợi ý của chính góc (ở đây là 0, y như cũ)")
        assertEquals(5120, C.streamW(mirrorL, null), "hai góc Gương vẫn có gợi ý sẵn của chúng")
    }

    @Test
    fun `tam quang PHAI nam TRONG vung cat, moi dai`() {
        (0..3).forEach { s ->
            val rect = crop(other, left = s < 2, pano = s)
            assertNotNull(rect, "dải $s phải có vùng cắt")
            val centre = CameraGlUniforms.sourceCentre(rect, s)[0]
            assertTrue(
                centre > rect!![0] && centre < rect[2],
                "dải $s: tâm quang $centre rơi ngoài vùng cắt [${rect[0]} … ${rect[2]}] ⇒ trục quang ra mép " +
                    "dải ⇒ hình cong lệch. Đây đúng cái bẫy lượt phản biện 28/09 chỉ ra.",
            )
        }
    }

    @Test
    fun `khong cat thi tam quang ve giua khung nhu cu`() {
        assertArrayEquals(floatArrayOf(0.5f, 0.5f), CameraGlUniforms.sourceCentre(null, 1), 1e-6f)
    }
}
