package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.modules.clustercast.simplified.CastEnableDeferral.AtStart
import com.byd.clusternav.modules.clustercast.simplified.CastEnableDeferral.OnApply
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V-CLUSTER · VC-R7 — `cast_enabled` theo hồ sơ, HOÃN áp: bảng quyết định đủ ca ═══════════════════════════
 *
 * Spec §11.4.3 / §11.5 C4 / §11.6 V-8. Bất biến khoá ở đây: **không nhánh nào của lượt đổi hồ sơ đụng khoá sống** —
 * lý do của OQ2 cũ (mọi cổng đọc LIVE ⇒ cụm hai chủ) vẫn đúng, chỉ kết luận đổi.
 */
class CastEnableDeferralTest {

    private val values = listOf<Any?>(null, true, false)

    @Test
    fun `anh chup ghi lua chon cua ho so = ban cho neu co, khong thi gia tri song`() {
        assertEquals(null, CastEnableDeferral.desiredForSnapshot(null, null))
        assertEquals(true, CastEnableDeferral.desiredForSnapshot(true, null))
        assertEquals(false, CastEnableDeferral.desiredForSnapshot(true, false), "bản chờ = lựa chọn của hồ sơ đang dùng")
        assertEquals(true, CastEnableDeferral.desiredForSnapshot(false, true))
        assertEquals(true, CastEnableDeferral.desiredForSnapshot(true, "rác"), "bản chờ sai kiểu ⇒ bỏ qua, lấy giá trị sống")
    }

    /** Bảng 3×3 (sống × muốn): bằng hiệu lực ⇒ xoá chờ; khác ⇒ ghi chờ. `null` = mặc định TẮT ở cả hai phía. */
    @Test
    fun `luot ap bang 3x3 — khong bao gio tra ve lenh ghi khoa song`() {
        values.forEach { live ->
            values.forEach { desired ->
                val effective = live as? Boolean ?: false
                val want = desired as? Boolean ?: false
                val expected = if (want == effective) OnApply.ClearPending else OnApply.SetPending(want)
                assertEquals(expected, CastEnableDeferral.onApply(live, desired), "sống=$live muốn=$desired")
            }
        }
    }

    @Test
    fun `gia tri sai kieu trong anh chup bi bo, khong nem`() {
        val d = CastEnableDeferral.onApply(true, "false")
        assertTrue(d is OnApply.Drop, "chuỗi 'false' không phải Boolean — ghi nó là ClassCastException trên đường: $d")
        assertTrue(CastEnableDeferral.onApply(true, 0) is OnApply.Drop)
    }

    /** V-8: sống BẬT, hồ sơ B TẮT ⇒ chờ = false; khởi động lại ⇒ chốt TẮT + phải dọn projection mồ côi. */
    @Test
    fun `chot luc khoi dong — BAT sang TAT moi phai don projection mo coi`() {
        assertEquals(AtStart.NoPending, CastEnableDeferral.onProcessStart(true, null))
        assertEquals(AtStart.Commit(on = false, closeOrphan = true), CastEnableDeferral.onProcessStart(true, false))
        assertEquals(AtStart.Commit(on = true, closeOrphan = false), CastEnableDeferral.onProcessStart(false, true))
        assertEquals(AtStart.Commit(on = true, closeOrphan = false), CastEnableDeferral.onProcessStart(null, true))
        assertEquals(
            AtStart.Commit(on = false, closeOrphan = false), CastEnableDeferral.onProcessStart(null, false),
            "vắng = mặc định TẮT ⇒ chốt TẮT không có gì để dọn",
        )
        assertTrue(CastEnableDeferral.onProcessStart(true, "false") is AtStart.Discard, "chờ sai kiểu ⇒ xoá, không đổi khoá sống")
    }

    @Test
    fun `ten khoa va mac dinh khop voi tang luu tru`() {
        assertEquals("cast_enabled", CastEnableDeferral.LIVE_KEY)
        assertEquals("cast_enabled_pending", CastEnableDeferral.PENDING_KEY)
        assertEquals(false, CastEnableDeferral.DEFAULT, "owner 2026-08-11: mặc định TẮT (nav-only)")
    }
}
