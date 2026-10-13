package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R22·R — khoá mặt nạ kính hông trên ảnh xe mặc định ([CarArtPanes]). Owner 13/10: thanh xanh lệch ~15 px khỏi kính
 * thật = "thô". Khoá: mảnh nằm trong khung ảnh và trong thân (không ra ngoài mép đục ~11 %/89 %), đối xứng trái/phải, kính
 * trước nằm trên kính sau (trụ B ở giữa), cùng dải dọc với neo cửa [CarLayout], tâm chấm trạng thái nằm trên dải kính.
 */
class CarArtPanesTest {

    private val doors = listOf(CarPart.DOOR_LF, CarPart.DOOR_RF, CarPart.DOOR_LR, CarPart.DOOR_RR)

    @Test
    fun `manh kinh nam trong than xe, trong khung anh`() {
        doors.forEach { d ->
            CarArtPanes.pane(d)!!.forEach {
                assertTrue(it.x in 0.11f..0.89f && it.y in 0.3f..0.8f, "$d: điểm ngoài vùng kính hông: $it")
            }
        }
    }

    @Test
    fun `trai phai doi xung, truoc tren sau, tru B o giua`() {
        listOf(CarPart.DOOR_LF to CarPart.DOOR_RF, CarPart.DOOR_LR to CarPart.DOOR_RR).forEach { (l, r) ->
            CarArtPanes.pane(l)!!.zip(CarArtPanes.pane(r)!!).forEach { (a, b) ->
                assertEquals(a.x, 677f / CarArtPanes.ART_W - b.x, 1e-4f); assertEquals(a.y, b.y, 1e-4f)
            }
        }
        val frontBottom = CarArtPanes.pane(CarPart.DOOR_LF)!!.maxOf { it.y }
        val rearTop = CarArtPanes.pane(CarPart.DOOR_LR)!!.minOf { it.y }
        assertTrue(frontBottom < rearTop, "trụ B phải tách kính trước/sau")
        assertTrue(CarArtPanes.paneCenter(CarPart.DOOR_LF)!!.x < 0.5f && CarArtPanes.paneCenter(CarPart.DOOR_RF)!!.x > 0.5f)
        // Cùng dải dọc với neo cửa dùng chung (bảng cửa): tâm kính lệch neo < 0,08 chiều dài xe.
        doors.forEach { d ->
            assertTrue(kotlin.math.abs(CarArtPanes.paneCenter(d)!!.y - CarLayout.part(d).y) < 0.08f, "$d lệch neo cửa")
        }
    }

    /** 3.01 DOT (owner 13/10 "vẽ chấm tròn thôi"): ranh "kính hạ" đã gỡ — tâm chấm phải nằm TRÊN dải kính đo được. */
    @Test
    fun `tam cham nam tren dai kinh, vat khong kinh thi khong co manh`() {
        doors.forEach { d ->
            val q = CarArtPanes.pane(d)!!
            val c = CarArtPanes.paneCenter(d)!!
            assertTrue(c.x in q.minOf { it.x }..q.maxOf { it.x } && c.y in q.minOf { it.y }..q.maxOf { it.y }, "$d: tâm ngoài kính")
            assertEquals(c, CarDots.center(d, CarArtCatalog.DEFAULT))
        }
        assertNull(CarArtPanes.pane(CarPart.TAILGATE))
        assertNull(CarArtPanes.paneCenter(CarPart.SUNROOF))
    }
}
