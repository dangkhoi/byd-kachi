package com.byd.clusternav.comfort

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Device-free unit tests for [Pm25Filter] (pure :core model). Locks the level constants, the
 * "dirty ≥ threshold" boundary, and the bilingual label mapping (INVALID/unknown → "—").
 *
 * Red-green: e.g. changing `isDirty` to `>` (excluding the boundary) makes the `5=true` case RED;
 * mapping INVALID to anything but "—" makes the label case RED.
 */
class Pm25FilterTest {

    @Test fun `level constants match RE table INVALID-0 to SERIOUS-6`() {
        assertEquals(0, Pm25Filter.INVALID)
        assertEquals(1, Pm25Filter.EXCELLENT)
        assertEquals(2, Pm25Filter.GOOD)
        assertEquals(3, Pm25Filter.LOW_GRADE)
        assertEquals(4, Pm25Filter.MIDDLE)
        assertEquals(5, Pm25Filter.HEAVY)
        assertEquals(6, Pm25Filter.SERIOUS)
        assertEquals(Pm25Filter.HEAVY, Pm25Filter.DEFAULT_THRESHOLD)
    }

    @Test fun `isDirty is true only at or above HEAVY threshold by default`() {
        assertFalse(Pm25Filter.isDirty(0))   // INVALID → not dirty
        assertFalse(Pm25Filter.isDirty(4))   // MIDDLE → below HEAVY → not dirty
        assertTrue(Pm25Filter.isDirty(5))    // HEAVY → dirty (boundary inclusive)
        assertTrue(Pm25Filter.isDirty(6))    // SERIOUS → dirty
        // Every clean level is not dirty.
        assertFalse(Pm25Filter.isDirty(Pm25Filter.EXCELLENT))
        assertFalse(Pm25Filter.isDirty(Pm25Filter.GOOD))
        assertFalse(Pm25Filter.isDirty(Pm25Filter.LOW_GRADE))
    }

    @Test fun `isDirty honours a custom threshold and is degrade-safe out of range`() {
        // Lower the threshold to MIDDLE(4): 4 becomes dirty, 3 stays clean.
        assertTrue(Pm25Filter.isDirty(4, threshold = Pm25Filter.MIDDLE))
        assertFalse(Pm25Filter.isDirty(3, threshold = Pm25Filter.MIDDLE))
        // Out-of-range values never throw and are not dirty.
        assertFalse(Pm25Filter.isDirty(-1))
        assertFalse(Pm25Filter.isDirty(99))
    }

    @Test fun `levelLabelVi maps each level and INVALID-unknown to dash`() {
        assertEquals("—", Pm25Filter.levelLabelVi(Pm25Filter.INVALID))
        assertEquals("Rất tốt", Pm25Filter.levelLabelVi(Pm25Filter.EXCELLENT))
        assertEquals("Tốt", Pm25Filter.levelLabelVi(Pm25Filter.GOOD))
        assertEquals("Khá", Pm25Filter.levelLabelVi(Pm25Filter.LOW_GRADE))
        assertEquals("Trung bình", Pm25Filter.levelLabelVi(Pm25Filter.MIDDLE))
        assertEquals("Nặng", Pm25Filter.levelLabelVi(Pm25Filter.HEAVY))
        assertEquals("Nghiêm trọng", Pm25Filter.levelLabelVi(Pm25Filter.SERIOUS))
        assertEquals("—", Pm25Filter.levelLabelVi(-1))   // unknown → dash
        assertEquals("—", Pm25Filter.levelLabelVi(99))   // unknown → dash
    }

    @Test fun `levelLabelEn maps each level and INVALID-unknown to dash`() {
        assertEquals("—", Pm25Filter.levelLabelEn(Pm25Filter.INVALID))
        assertEquals("Excellent", Pm25Filter.levelLabelEn(Pm25Filter.EXCELLENT))
        assertEquals("Good", Pm25Filter.levelLabelEn(Pm25Filter.GOOD))
        assertEquals("Low grade", Pm25Filter.levelLabelEn(Pm25Filter.LOW_GRADE))
        assertEquals("Moderate", Pm25Filter.levelLabelEn(Pm25Filter.MIDDLE))
        assertEquals("Heavy", Pm25Filter.levelLabelEn(Pm25Filter.HEAVY))
        assertEquals("Serious", Pm25Filter.levelLabelEn(Pm25Filter.SERIOUS))
        assertEquals("—", Pm25Filter.levelLabelEn(7))    // unknown → dash
    }
}
