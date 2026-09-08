package com.byd.clusternav.offcar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NaviInfoSchemaTest {
    @Test
    fun `NaviInfo declares exactly 18 navigation fields with road and no speed limit`() {
        val schema = FirmwareEvidenceCatalog.naviInfo

        assertEquals(18, schema.declaredFieldCount)
        assertEquals(18, schema.fields.size)
        assertEquals(246, schema.startObjectLine)
        assertTrue(schema.fields.containsAll(listOf("nextRouteName", "nextNextRouteName")))
        assertFalse(schema.hasSpeedLimit)
        assertTrue(schema.fields.none {
            it.contains("speed", ignoreCase = true) || it.contains("limit", ignoreCase = true)
        })
    }
}
