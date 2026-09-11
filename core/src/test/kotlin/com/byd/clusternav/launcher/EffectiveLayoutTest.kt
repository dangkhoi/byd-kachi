package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá **nguồn duy nhất** cho câu "bao nhiêu ô và ô ở đâu" (P9 bước 2).
 *
 * Test quan trọng nhất ở đây là nhóm **lùi an toàn**: một bố cục tự vẽ không dùng được phải lùi về bố cục sẵn, vì
 * hậu quả của việc không lùi là **màn hình trống hoặc app nằm lệch khỏi ô** — người dùng không tự sửa được.
 */
class EffectiveLayoutTest {

    private val twoCol = GridLayout(listOf(GridFrame(0, 0, 6, 6), GridFrame(6, 0, 6, 6)))

    @Test fun `chua ve gi thi dung bo cuc san`() {
        assertEquals(3, EffectiveLayout.slotCount(LayoutPreset.THREE, null))
        assertEquals(4, EffectiveLayout.slotCount(LayoutPreset.QUAD, GridLayout(emptyList())))
        assertEquals(
            WorkspaceLayout.slots(LayoutPreset.QUAD, 1920, 1080, 10),
            EffectiveLayout.rects(LayoutPreset.QUAD, null, 1920, 1080, 10),
        )
    }

    @Test fun `ve roi thi bo cuc tu ve THANG bo cuc san`() {
        assertEquals(2, EffectiveLayout.slotCount(LayoutPreset.QUAD, twoCol))
        val r = EffectiveLayout.rects(LayoutPreset.QUAD, twoCol, 1920, 1080, 0)
        assertEquals(2, r.size)
        assertEquals(0, r[0].left); assertEquals(960, r[0].right)
        assertEquals(960, r[1].left); assertEquals(1920, r[1].right)
    }

    // ── LÙI AN TOÀN — ba ca ─────────────────────────────────────────────────────────────────────

    @Test fun `bo cuc DE NHAU thi lui ve bo cuc san`() {
        val overlap = GridLayout(listOf(GridFrame(0, 0, 8, 6), GridFrame(4, 0, 8, 6)))
        assertFalse(EffectiveLayout.usable(overlap))
        assertEquals(3, EffectiveLayout.slotCount(LayoutPreset.THREE, overlap),
            "bố cục đè nhau phải lùi về bố cục sẵn, KHÔNG hiện ra thứ hỏng")
    }

    @Test fun `NHIEU KHUNG HON tran o thi lui ve bo cuc san`() {
        // Ca này có thật khi bản sau nới trần rồi người dùng hạ cấp bản.
        val six = GridLayout((0 until 6).map { GridFrame(it * 2, 0, 2, 3) })
        assertTrue(six.valid, "sáu khung này tự nó hợp lệ")
        assertFalse(EffectiveLayout.usable(six, cap = 4), "nhưng vượt trần ô thì không dùng được")
        assertEquals(2, EffectiveLayout.slotCount(LayoutPreset.TWO_COL, six, cap = 4))
    }

    @Test fun `bo cuc ra ngoai luoi thi lui ve bo cuc san`() {
        val out = GridLayout(listOf(GridFrame(10, 0, 6, 6)))
        assertEquals(1, EffectiveLayout.slotCount(LayoutPreset.ONE, out))
        assertEquals(
            WorkspaceLayout.slots(LayoutPreset.ONE, 1920, 1080, 0),
            EffectiveLayout.rects(LayoutPreset.ONE, out, 1920, 1080, 0),
        )
    }

    // ── Nói lý do, không im lặng ────────────────────────────────────────────────────────────────

    @Test fun `noi RO ly do bo cuc tu ve bi bo qua`() {
        assertNull(EffectiveLayout.ignoredReason(null), "chưa vẽ gì thì không phải 'bị bỏ qua'")
        assertNull(EffectiveLayout.ignoredReason(twoCol), "dùng được thì không có lý do")
        val six = GridLayout((0 until 6).map { GridFrame(it * 2, 0, 2, 3) })
        assertNotNull(EffectiveLayout.ignoredReason(six, cap = 4))
        assertTrue(EffectiveLayout.ignoredReason(six, cap = 4)!!.contains("6"), "phải nói con số thật")
        val overlap = GridLayout(listOf(GridFrame(0, 0, 8, 6), GridFrame(4, 0, 8, 6)))
        assertTrue(EffectiveLayout.ignoredReason(overlap)!!.contains("đè"), "phải nói đè nhau")
    }

    // ── Tương đương với bố cục sẵn (khoá cùng phép chứng minh của bước nền) ──────────────────────

    @Test fun `bo cuc san dung ra khung DUNG TUNG PIXEL qua nguon duy nhat`() {
        // Cùng phép chứng minh của bước nền, nhưng qua đường MỚI: nếu nguồn duy nhất làm lệch một pixel thì
        // đổi sang nó là đánh cược.
        val sizes = listOf(1920 to 1080, 1280 to 720, 1024 to 600, 2560 to 1440, 800 to 480)
        val gaps = listOf(0, 1, 8, 10, 24)
        var checked = 0
        WorkspaceGrid.presetsRepresentable().forEach { preset ->
            val grid = WorkspaceGrid.fromPreset(preset)!!
            sizes.forEach { (w, h) ->
                gaps.forEach { gap ->
                    assertEquals(
                        WorkspaceLayout.slots(preset, w, h, gap),
                        EffectiveLayout.rects(preset, grid, w, h, gap, cap = 4),
                        "lệch ở $preset ${w}x$h khe $gap",
                    )
                    checked++
                }
            }
        }
        assertEquals(WorkspaceGrid.presetsRepresentable().size * sizes.size * gaps.size, checked)
        assertTrue(checked >= 100, "phải quét ít nhất 100 tổ hợp, đang $checked")
    }
}
