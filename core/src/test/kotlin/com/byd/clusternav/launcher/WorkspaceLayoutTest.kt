package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá hình học layout engine (pure :core) — khớp prototype kachi-workspace.html.
 * Đổi công thức chia ô mà lệch → test ĐỎ. Chạy off-car/JVM.
 */
class WorkspaceLayoutTest {
    private val W = 1280
    private val H = 720
    private val G = 12

    @Test fun `slot count khop tung preset`() {
        assertEquals(1, WorkspaceLayout.slots(LayoutPreset.ONE, W, H, G).size)
        assertEquals(2, WorkspaceLayout.slots(LayoutPreset.TWO_COL, W, H, G).size)
        assertEquals(2, WorkspaceLayout.slots(LayoutPreset.TWO_ROW, W, H, G).size)
        assertEquals(3, WorkspaceLayout.slots(LayoutPreset.THREE, W, H, G).size)
        assertEquals(4, WorkspaceLayout.slots(LayoutPreset.QUAD, W, H, G).size)
    }

    @Test fun `ONE lap day ca vung`() {
        val s = WorkspaceLayout.slots(LayoutPreset.ONE, W, H, G).single()
        assertEquals(0, s.left); assertEquals(0, s.top); assertEquals(W, s.right); assertEquals(H, s.bottom)
    }

    @Test fun `TWO_COL chia doi be ngang co gap`() {
        val (l, r) = WorkspaceLayout.slots(LayoutPreset.TWO_COL, W, H, G)
        assertEquals(0, l.left); assertEquals(H, l.bottom)
        assertEquals(l.right + G, r.left)
        assertEquals(W, r.right)
        assertEquals(l.width, r.width)
    }

    @Test fun `TWO_ROW chia doi be doc co gap`() {
        val (t, b) = WorkspaceLayout.slots(LayoutPreset.TWO_ROW, W, H, G)
        assertEquals(0, t.top); assertEquals(W, t.right)
        assertEquals(t.bottom + G, b.top)
        assertEquals(H, b.bottom)
        assertEquals(t.height, b.height)
    }

    @Test fun `THREE trai to cao full + phai chia tren duoi`() {
        val s = WorkspaceLayout.slots(LayoutPreset.THREE, W, H, G)
        val left = s[0]; val topR = s[1]; val botR = s[2]
        assertEquals(H, left.height)                 // trái spans full height
        assertTrue(left.width > topR.width)          // cột trái rộng hơn (1.55:1)
        assertEquals(left.right + G, topR.left)      // cột phải bắt đầu sau gap
        assertEquals(topR.left, botR.left)           // 2 ô phải thẳng cột
        assertEquals(topR.bottom + G, botR.top)      // dưới nằm sau trên + gap
        assertEquals(W, topR.right); assertEquals(W, botR.right)
    }

    @Test fun `QUAD lat 2x2 bang nhau`() {
        val s = WorkspaceLayout.slots(LayoutPreset.QUAD, W, H, G)
        assertEquals(4, s.size)
        assertEquals(s[0].width, s[3].width); assertEquals(s[0].height, s[3].height)
        assertEquals(0, s[0].left); assertEquals(0, s[0].top)
        assertEquals(W, s[3].right); assertEquals(H, s[3].bottom)
        assertEquals(s[0].right + G, s[1].left)      // gap ngang
        assertEquals(s[0].bottom + G, s[2].top)      // gap dọc
    }

    @Test fun `moi o nam trong bien va co kich thuoc duong`() {
        for (p in LayoutPreset.values()) {
            for (s in WorkspaceLayout.slots(p, W, H, G)) {
                assertTrue(s.left >= 0 && s.top >= 0 && s.right <= W && s.bottom <= H, "$p ô ${s.index} trong biên")
                assertTrue(s.width > 0 && s.height > 0, "$p ô ${s.index} kích thước dương")
            }
        }
    }

    @Test fun `gap 0 lap kin khong khe`() {
        val s = WorkspaceLayout.slots(LayoutPreset.QUAD, W, H, 0)
        assertEquals(s[0].right, s[1].left)
        assertEquals(s[0].bottom, s[2].top)
    }
}
