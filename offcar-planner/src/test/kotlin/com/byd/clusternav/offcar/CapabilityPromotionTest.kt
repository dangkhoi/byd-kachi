package com.byd.clusternav.offcar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CapabilityPromotionTest {
    @Test
    fun `incomplete corpus never promotes unknown capability to unsupported`() {
        val result = CapabilityPromoter.resolve(
            CapabilityPromotionInput(
                corpusComplete = false,
                requiredArtifactsAvailable = false,
                exactMatrixExhausted = true,
                negativeFieldRows = 99,
                positiveFieldProof = false,
            ),
        )

        assertEquals(CapabilityState.UNKNOWN, result)
        assertEquals(CapabilityState.UNAVAILABLE, CapabilityPromoter.unavailableBranch())
    }

    @Test
    fun `unsupported requires complete corpus exact matrix exhaustion and negative field evidence`() {
        val unsupported = CapabilityPromoter.resolve(
            CapabilityPromotionInput(true, true, true, 4, false),
        )
        val stillUnknown = CapabilityPromoter.resolve(
            CapabilityPromotionInput(true, true, false, 4, false),
        )

        assertEquals(CapabilityState.UNSUPPORTED, unsupported)
        assertEquals(CapabilityState.UNKNOWN, stillUnknown)
    }

    @Test
    fun `positive exact field proof is the only field-proven promotion`() {
        assertEquals(
            CapabilityState.FIELD_PROVEN,
            CapabilityPromoter.resolve(CapabilityPromotionInput(false, false, false, 0, true)),
        )
        assertEquals(CapabilityState.UNKNOWN, CapabilityPromoter.resolve(
            CapabilityPromotionInput(true, true, true, 0, false),
        ))
    }
}
