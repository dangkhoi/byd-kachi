package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * 3.01 R23 — khoá hình học đăng ký theo ẢNH ([CarArt]/[CarArtCatalog]). Khoá: (1) phép cắt + xoay đưa ảnh BYD đầu-xe-quay-TRÁI
 * về hệ chuẩn hoá đầu-xe-quay-LÊN của [CarLayout] (sườn trái xe ở mép dưới ảnh gốc ⇒ x nhỏ); (2) mặt nạ kính Seal nằm trong
 * khung, đối xứng, trụ B tách trước/sau, ranh kính hạ đi từ mép trước; (3) tên ảnh BYD dựng đúng công thức CarSettings
 * (`common_top_view_img_6125f_ivi_0x%x`) và chỉ ảnh ĐÃ ĐO mới được chọn.
 */
class CarArtTest {

    private val doors = listOf(CarPart.DOOR_LF, CarPart.DOOR_RF, CarPart.DOOR_LR, CarPart.DOOR_RR)
    private val seal = CarArtCatalog.SEAL_TOP

    @Test
    fun `xoay thuan dua dau xe len tren, suon trai xe sang trai`() {
        // Mũi xe (x gốc 42, giữa thân) ⇒ đỉnh khung; đuôi (x 801) ⇒ đáy; sườn trái (y gốc 426, mép dưới ảnh) ⇒ mép TRÁI.
        assertEquals(0.008f, seal.norm(42f, 277f).y, 0.005f)
        assertEquals(0.99f, seal.norm(801f, 277f).y, 0.01f)
        assertTrue(seal.norm(400f, 426f).x < 0.1f && seal.norm(400f, 128f).x > 0.9f)
        // Bốn góc vùng cắt ⇒ bốn góc khung chuẩn hoá.
        assertEquals(CarAnchor(0f, 0f), seal.norm(36f, 454f))
        assertEquals(CarAnchor(1f, 1f), seal.norm(807f, 101f))
        assertEquals(353f / 771f, seal.aspect, 1e-5f)
        // Ảnh mặc định không xoay, không cắt ⇒ tỉ lệ cũ.
        assertEquals(CarArtPanes.ASPECT, CarArtCatalog.DEFAULT.aspect, 1e-6f)
    }

    @Test
    fun `kinh Seal trong khung, doi xung, tru B o giua, cung vung neo cua`() {
        doors.forEach { d ->
            val q = seal.pane(d)!!
            q.forEach { assertTrue(it.x in 0f..1f && it.y in 0.2f..0.8f, "$d ngoài vùng kính: $it") }
            val c = CarArtPanes.paneCenter(d, seal)!!
            assertTrue(abs(c.y - seal.part(d).y) < 0.05f, "$d: tâm kính lệch neo cửa")
            assertTrue((c.x < 0.5f) == (d == CarPart.DOOR_LF || d == CarPart.DOOR_LR), "$d sai phía")
        }
        val axis = (454f - 554.5f / 2f) / 353f
        listOf(CarPart.DOOR_LF to CarPart.DOOR_RF, CarPart.DOOR_LR to CarPart.DOOR_RR).forEach { (l, r) ->
            seal.pane(l)!!.zip(seal.pane(r)!!).forEach { (a, b) ->
                assertEquals(2 * axis, a.x + b.x, 1e-4f); assertEquals(a.y, b.y, 1e-5f)
            }
        }
        assertTrue(seal.pane(CarPart.DOOR_LF)!!.maxOf { it.y } < seal.pane(CarPart.DOOR_LR)!!.minOf { it.y })
        // 3.01 DOT (owner 13/10): ranh "kính hạ" đã gỡ — chấm Seal ở tâm mảnh kính, nằm trên dải kính đo được.
        doors.forEach { d ->
            val q = seal.pane(d)!!
            val c = CarDots.center(d, seal)
            assertTrue(c.x in q.minOf { it.x }..q.maxOf { it.x } && c.y in q.minOf { it.y }..q.maxOf { it.y }, "$d: chấm ngoài kính")
        }
        assertNull(seal.pane(CarPart.TAILGATE))
    }

    @Test
    fun `neo banh va bo phan trong khung, truoc tren sau, chua do thi lui neo chung`() {
        listOf(seal, CarArtCatalog.DEFAULT).forEach { art ->
            (CarPart.values().map { art.part(it) } + TyreCorner.values().map { art.wheel(it) }).forEach {
                assertTrue(it.x in 0f..1f && it.y in 0f..1f, "${art.key}: neo ngoài khung $it")
            }
            assertTrue(art.wheel(TyreCorner.FRONT_LEFT).y < art.wheel(TyreCorner.REAR_LEFT).y)
            assertTrue(art.wheel(TyreCorner.FRONT_LEFT).x < 0.5f && art.wheel(TyreCorner.FRONT_RIGHT).x > 0.5f)
            assertTrue(art.part(CarPart.DOOR_LF).y < art.part(CarPart.DOOR_LR).y)
        }
        // Ảnh mặc định không khai neo riêng ⇒ đúng neo chung (bảng lốp/cửa trên ảnh mặc định không đổi).
        assertEquals(CarLayout.wheel(TyreCorner.REAR_RIGHT), CarArtCatalog.DEFAULT.wheel(TyreCorner.REAR_RIGHT))
        assertEquals(CarLayout.part(CarPart.DOOR_RF), CarArtCatalog.DEFAULT.part(CarPart.DOOR_RF))
        // Mặt nạ mặc định qua CarArt = số đo R22·R cũ (điểm đầu kính trước trái 110,458).
        assertEquals(CarAnchor(110f / 678f, 458f / 1397f), CarArtCatalog.DEFAULT.pane(CarPart.DOOR_LF)!!.first())
    }

    @Test
    fun `ten anh BYD theo cong thuc CarSettings, chi anh da do moi duoc chon`() {
        assertEquals("common_top_view_img_6125f_ivi_0x8a", CarArtCatalog.oemTopViewName("138"))
        assertEquals("common_top_view_img_6125f_ivi_0x161", CarArtCatalog.oemTopViewName(" 353 "))
        assertNull(CarArtCatalog.oemTopViewName(null))
        assertNull(CarArtCatalog.oemTopViewName("abc"))
        assertNull(CarArtCatalog.oemTopViewName("0"))
        assertSame(seal, CarArtCatalog.byKey(CarArtCatalog.oemTopViewName("138")))
        assertNull(CarArtCatalog.byKey(CarArtCatalog.oemTopViewName("353")), "ảnh chưa đo không được dùng")
    }
}
