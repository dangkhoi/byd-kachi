package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LaneSignatureTest {

    /** Dải w×h: mỗi phần tử `grays` = 1 cột màu xám rộng [colW] (0..255). */
    private fun stripe(grays: IntArray, colW: Int, h: Int): PixelFrame {
        val w = grays.size * colW
        val px = IntArray(w * h)
        for (y in 0 until h) for (i in grays.indices) {
            val g = grays[i]
            val c = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            for (dx in 0 until colW) px[y * w + i * colW + dx] = c
        }
        return ArrayPixelFrame(w, h, px)
    }

    @Test
    fun `4 lan - lan SANG recommended, lan MO khong (vi du owner)`() {
        // [mờ, sáng, sáng, mờ] → map bảo đi thẳng: L1 trái mờ, L2+L3 thẳng sáng, L4 phải mờ.
        val f = stripe(intArrayOf(96, 255, 255, 96), colW = 10, h = 20)
        val info = LaneSignature.classify(f, laneCount = 4)
        assertEquals(4, info?.count)
        assertEquals(listOf(false, true, true, false), info?.lanes?.map { it.recommended })
    }

    @Test
    fun `dai qua toi - null (khong co lane-guidance)`() {
        assertNull(LaneSignature.classify(stripe(intArrayOf(5, 8, 6, 4), colW = 10, h = 20), laneCount = 4))
    }

    @Test
    fun `frame null hoac qua nho - null`() {
        assertNull(LaneSignature.classify(null))
        assertNull(LaneSignature.classify(stripe(intArrayOf(255), colW = 2, h = 3)))
    }

    @Test
    fun `detect lane count khi khong truyen - dem cum cot sang`() {
        // 3 cụm sáng ngăn bởi khe tối.
        val f = stripe(intArrayOf(255, 10, 255, 10, 255), colW = 8, h = 16)
        assertEquals(3, LaneSignature.classify(f)?.count)
    }
}
