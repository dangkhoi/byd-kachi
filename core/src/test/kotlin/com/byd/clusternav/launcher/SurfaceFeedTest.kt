package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 3.1.11 · DOCK-LABELS-310 (phần ảnh cũ) — thanh nút LUÔN giữ ảnh trạng thái xe mới nhất; chỉ việc đổ ô theo tập bày ═════════
 *
 * Bản 3.1.10 đặt cổng EVENT-PATH-SCOPED ở `KachiHomeRender` TRƯỚC `dock.setCarStatus` ⇒ đổi không chạm tập thanh ⇒ thanh không nhận ảnh.
 * Thanh dựng lại ô từ ảnh của chính nó (đổi cấu hình/hồ sơ/chủ đề) ⇒ ô MỚI ra số của lượt chạm cuối. Doc
 * `docs/diagnostics/dock-labels-310-2026-10-16.md` §3.
 *
 * Thử ĐỎ [ĐO 16/10]: cho [SurfaceFeed.offer] chỉ gán `latest` khi `touched` (đúng hình 3.1.10) ⇒ bài 1 ĐỎ (ô bụi mịn thêm vào thanh dựng
 * từ ảnh cũ: `pm25ValueUgm3` = null thay vì 42); bỏ [SurfaceFeed.scope] ⇒ bài 2 ĐỎ.
 */
class SurfaceFeedTest {

    private val base = CarStatus()
    private fun pm25(v: Int) = base.copy(climate = base.climate.copy(pm25ValueUgm3 = v))

    @Test fun `doi KHONG cham tap thanh van giu anh moi - o them sau dung so moi`() {
        val feed = SurfaceFeed(listOf("temp"))   // thanh chỉ có nút nhiệt độ
        assertTrue(feed.offer(base.copy(controls = mapOf("temp" to 22))), "nút nhiệt đổi ⇒ đổ")
        val dusty = pm25(42).copy(controls = mapOf("temp" to 22))
        assertFalse(feed.offer(dusty), "bụi mịn đổi, thanh không bày ⇒ KHÔNG đổ (luật hiệu năng 3.1.10 giữ nguyên)")
        assertSame(dusty, feed.latest, "…nhưng ảnh của thanh PHẢI là ảnh mới nhất")
        // Người dùng thêm ô bụi mịn lên thanh ⇒ `rebuild` dựng ô từ `latest`.
        feed.scope(listOf("temp", "pm25_value"))
        assertEquals(42, feed.latest.climate.pm25ValueUgm3, "ô vừa thêm dựng từ số MỚI, không từ lượt chạm cuối")
    }

    @Test fun `doi cau hinh thi tap bay moi quyet dinh lan do sau`() {
        assertNotNull(CarDataDemand.interestOf(listOf("pm25_value")), "tiền đề: mã telemetry có tập bày")
        val feed = SurfaceFeed(listOf("temp"))
        feed.offer(pm25(1))
        assertFalse(feed.offer(pm25(2)), "chưa bày bụi mịn ⇒ không đổ")
        feed.scope(listOf("pm25_value"))
        assertTrue(feed.offer(pm25(3)), "đã bày bụi mịn ⇒ đổi bụi mịn phải đổ")
        assertFalse(feed.offer(pm25(3)), "không đổi gì ⇒ không đổ")
    }

    @Test fun `ma la thi luon do - fail-open nhu interestOf`() {
        val feed = SurfaceFeed(listOf("ma_khong_ton_tai"))
        assertTrue(feed.offer(pm25(5)), "không biết bề mặt bày gì ⇒ đổ khi có bất kỳ đổi nào")
    }
}
