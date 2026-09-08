package com.byd.clusternav.offcar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModernPropertyGatewayContractTest {
    @Test
    fun `modern generic property API is preferred as contract data`() {
        val contract = ModernPropertyGatewayContract.contract

        assertEquals("getPropertyConfigs", contract.configMethod)
        assertEquals("getCarProperty", contract.readMethod)
        assertEquals("setCarProperty", contract.writeMethod)
        assertEquals("setProperty", contract.deprecatedFallbackMethod)
        assertNotEquals(contract.writeMethod, contract.deprecatedFallbackMethod)
        assertTrue(contract.modernFirst)
        assertTrue(contract.fallbackOnlyWhenModernUnavailable)
        assertEquals(EvidenceLevel.CONCRETE_SET_CALL_SITE, FirmwareEvidenceCatalog.byId.getValue("S6").level)
    }
}
