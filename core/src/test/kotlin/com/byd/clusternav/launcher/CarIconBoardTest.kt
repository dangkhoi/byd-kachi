package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R24 — owner 13/10 *"cái widget đèn cũng làm vậy luôn, có hình xe và chấm tròn hay icon đèn xung quanh, đúng vị trí đèn,
 * nhấn tắt, nhấn mở"*. Khoá: (1) điểm đèn là DỮ LIỆU theo ảnh, đúng phía/đầu-đuôi trên CẢ HAI ảnh đã đo; (2) mọi nút đèn đều
 * lên hình (không mất nút), ô chỉ-xem không điểm bị bỏ; (3) điểm không chồng, không tràn (không vừa ⇒ lùi lưới icon);
 * (4) trạng thái bật/tắt/chưa biết. Chủ cú chạm: [CarIconBoardTouchTest] (3.01 TAPFIX).
 */
class CarIconBoardTest {

    private val lights = GroupIconBoard.of(GroupBoard.of(CapabilityGroups.LIGHTS, CarStatus()))!!
    private val arts = listOf(CarArtCatalog.DEFAULT, CarArtCatalog.SEAL_TOP)

    private fun at(art: CarArt, icon: String) = art.spot(icon)!!

    @Test fun `diem den dung cho tren ca hai anh`() {
        arts.forEach { a ->
            val k = a.key
            assertTrue(at(a, "ic-car-front-lowbeam").let { it.x < 0.5f && it.y < 0.15f }, "$k: đèn cốt = đèn trước-TRÁI")
            assertTrue(at(a, "ic-car-front-highbeam").let { it.x > 0.5f && it.y < 0.15f }, "$k: đèn pha = đèn trước-PHẢI")
            assertTrue(at(a, "ic-car-front-fog").y < at(a, "ic-car-front-drl").y, "$k: sương mù trước ở mũi, trước đèn ban ngày")
            assertTrue(at(a, "ic-car-front-turn-l").x < 0.15f && at(a, "ic-car-front-turn-r").x > 0.85f, "$k: xi-nhan trên gương hai bên")
            assertTrue(at(a, "ic-car-rear-fog").y > 0.9f, "$k: sương mù sau ở đuôi")
            assertTrue(at(a, "ic-car-front-sidelight").let { it.x < 0.5f && it.y > 0.85f }, "$k: đèn hông = đèn hậu trái")
            assertTrue(at(a, "ic-readlight").let { kotlin.math.abs(it.x - 0.5f) < 0.05f && it.y in 0.2f..0.5f }, "$k: đèn đọc giữa khoang trước")
            listOf("ic-car-front-lowbeam", "ic-car-rear-fog", "ic-readlight").forEach { i ->
                at(a, i).let { assertTrue(it.x in 0f..1f && it.y in 0f..1f, "$k/$i ngoài khung: $it") }
            }
        }
    }

    @Test fun `moi nut den len hinh, o chi-xem khong diem bi bo`() {
        arts.forEach { a ->
            val spots = CarIconBoard.place(lights, a)!!
            val acts = lights.mapNotNull { it.action?.id }.toSet()
            assertEquals(acts, spots.mapNotNull { it.tile.action?.id }.toSet(), "${a.key}: mất nút")
            assertTrue(spots.none { it.tile.icon == "ic-car-front-headlight-mode" }, "chế độ đèn pha không phải một bóng ⇒ không điểm")
            assertEquals(lights.size - 1, spots.size)
        }
        // Ô có NÚT mà ảnh không có điểm ⇒ không dựng hình (lùi lưới), không lặng lẽ bỏ nút.
        val orphan = lights + IconTile("ic-x-unknown", "X", null, GroupActionCell("x", "X", "ic-x-unknown", false))
        assertNull(CarIconBoard.place(orphan, CarArtCatalog.DEFAULT))
    }

    @Test fun `vua cho - diem khong chong, khong tran mep`() {
        val pts = listOf(30f to 30f, 80f to 30f)
        assertTrue(CarIconBoard.fits(pts, 40f, 4f, 200f, 200f))
        assertFalse(CarIconBoard.fits(pts, 48f, 4f, 200f, 200f), "khe < 4 px ⇒ chồng")
        assertFalse(CarIconBoard.fits(listOf(5f to 50f), 20f, 0f, 100f, 100f), "điểm tràn mép view")
    }

    /**
     * [ĐO máy ảo 13/10] ô Đèn của bố cục "Kính lớn · Chuyến đi · Đèn": thân 267×329 px, mật độ 1,5 ⇒ điểm 20 dp = 30 px, khe
     * `Sp.XS` 6 px, đích chạm 72 px. Khung ảnh tính như `CarIconBoardView.frameIn` (lớn nhất, chừa một điểm, canh giữa). Lượt
     * đầu ĐỎ thật trên máy ảo (điểm ban ngày ở mép ca-pô chồng điểm sương mù ở mũi ⇒ rơi về lưới) — bài này khoá chỗ đã dời.
     */
    @Test fun `hinh xe vua o Den that tren ca hai anh`() {
        val w = 267; val h = 329; val disc = 30f
        arts.forEach { a ->
            val s = minOf((w - disc) / a.aspect, h - disc); val fw = s * a.aspect
            val spots = CarIconBoard.place(lights, a)!!
            val pts = spots.map { CarIconBoard.centerIn(it, (w - fw) / 2f, (h - s) / 2f, fw, s) }
            assertTrue(CarIconBoard.fits(pts, disc, 6f, w.toFloat(), h.toFloat()), "${a.key}: điểm chồng ở ô Đèn thật")
        }
    }

    @Test fun `trang thai diem - so doc, lenh vua gui, chua biet`() {
        val on = GroupCell("light_drl", "DRL", "Bật", "", GroupTone.ACTIVE, "ic-car-front-drl", true, false)
        val off = on.copy(tone = GroupTone.NEUTRAL)
        assertEquals(CarDots.Kind.OPEN, CarIconBoard.kind(on, null))
        assertEquals(CarDots.Kind.CLOSED, CarIconBoard.kind(off, null))
        assertEquals(CarDots.Kind.UNKNOWN, CarIconBoard.kind(on.copy(available = false), null), "chưa đọc ⇒ vòng đứt nét")
        assertEquals(CarDots.Kind.UNKNOWN, CarIconBoard.kind(null, null), "đèn đọc chưa gửi gì ⇒ chưa biết, không giả 'tắt'")
        assertEquals(CarDots.Kind.OPEN, CarIconBoard.kind(off, true), "vừa chạm BẬT thắng số đọc trễ một nhịp")
        assertEquals(CarDots.Kind.CLOSED, CarIconBoard.kind(null, false))
        assertNotNull(lights.firstOrNull { it.action?.id == "readl" && it.cell == null }, "đèn đọc là ô chỉ-có-nút")
    }
}
