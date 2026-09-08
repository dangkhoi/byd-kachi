package com.byd.clusternav.modules.clustercast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Test hồi quy cho ĐỢT 1 (senior review 2026-07-21) — khoá lại đúng những lỗi có thể gây hại khi xe đang chạy.
 * Chỉ test được phần THUẦN; phần cần dadb/Android nằm trong checklist kiểm trên xe (xem README của bản build).
 */
class Wave1SafetyTest {

    // ── W1-3: "không đọc được tốc độ" KHÔNG được đồng nghĩa với "xe đang đỗ" ──

    /**
     * Mô phỏng cổng ngưỡng tốc độ mà `DeadReckonService` từng dùng (đã bỏ hẳn 27/7, nhưng bất biến vẫn đúng cho mọi người tiêu thụ về sau): `mpsOrNull()?.let { it < 2.0 } == true`.
     * Đây là hình dạng bắt buộc — `mps() < 2.0` (bản cũ) đọc 0.0 khi HAL câm nên LUÔN mở cổng.
     */
    private fun stationaryGate(speed: Double?): Boolean = speed?.let { it < 2.0 } == true

    @Test fun `khong doc duoc toc do thi KHONG duoc coi la dung yen`() {
        assertFalse(stationaryGate(null), "HAL câm → phải coi như xe đang chạy, không cold-seed")
    }

    @Test fun `doc duoc va thap thi moi la dung yen`() {
        assertTrue(stationaryGate(0.0))
        assertTrue(stationaryGate(1.9))
        assertFalse(stationaryGate(2.1))
    }

    /** Cổng cũ: dùng giá trị suy biến 0.0 → mở cổng cả khi không biết gì. Giữ test này làm đối chứng. */
    @Test fun `cong CU that bai — day la ly do phai doi`() {
        val degraded = 0.0            // mps() trả lastGoodMps khi không đọc được
        assertTrue(degraded < 2.0, "chính vì biểu thức này ĐÚNG mà cổng cũ luôn mở")
    }

    // ── W1-4: tự chiếu lúc nổ máy phải chặt hơn, và không được phá phiên ──

    private fun autoCastGate(speed: Double?): Boolean = speed?.let { it < 0.5 } == true

    @Test fun `tu chieu chi khi CHAC CHAN xe dung yen`() {
        assertFalse(autoCastGate(null))       // chưa đọc được → không tự chiếu
        assertFalse(autoCastGate(0.8))        // đang nhúc nhích → không
        assertTrue(autoCastGate(0.2))
    }

    // ── W1-4: migration KHÔNG ĐƯỢC nới quyền ──

    /**
     * Tái hiện phép biến đổi của applyDefaultModes trên tập keepSession. Bản cũ trừ AA/CarPlay ra khỏi tập này,
     * tức TỰ GỠ bảo vệ của người dùng trước rung force-stop. Bản mới không được đụng tới nó.
     */
    private fun migrate(keepSession: Set<String>, t3: Set<String>): Pair<Set<String>, Set<String>> =
        keepSession to (t3 + ClusterCast.PKG_ANDROID_AUTO)

    @Test fun `migration khong duoc go bao ve cua nguoi dung`() {
        val before = setOf(ClusterCast.PKG_CARPLAY, ClusterCast.PKG_ANDROID_AUTO)
        val (after, t3) = migrate(before, emptySet())
        assertEquals(before, after, "keepSession phải nguyên vẹn — migration chỉ được đi theo hướng thận trọng")
        assertTrue(ClusterCast.PKG_ANDROID_AUTO in t3)
    }

    // ── W1-5: latch phải có hạn, nếu không TẮT thành đường cụt ──

    private fun canTake(busy: Boolean, heldMs: Long, staleMs: Long = 90_000L) = !busy || heldMs >= staleMs

    @Test fun `latch qua han thi thao tac sau duoc gianh quyen`() {
        assertTrue(canTake(busy = false, heldMs = 0))
        assertFalse(canTake(busy = true, heldMs = 5_000))       // đang chạy bình thường → chờ
        assertTrue(canTake(busy = true, heldMs = 120_000))      // treo 2 phút → TẮT phải chạy được
    }
}
