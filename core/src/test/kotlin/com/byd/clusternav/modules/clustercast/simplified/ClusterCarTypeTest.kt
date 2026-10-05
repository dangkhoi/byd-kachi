package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * CLUSTER-THEME-SAFE B1a — đọc `persist.sys.car.type` (bare từ `getprop <key>` hoặc dòng của `getprop` đầy đủ).
 *
 * Fixture: 4 dòng trích NGUYÊN VĂN `docs/diagnostics/carlog-kachi-20260914-2044/00-getprop.txt:332-338` (Seal 14/09) — bỏ
 * các dòng chứa định danh xe (VIN, tệp thu CAN); thư mục `carlog-*` bị gitignore vì chính các dòng đó.
 */
class ClusterCarTypeTest {

    private val getpropSeal2026_09_14 = """
        |[persist.sys.byd.theme]: [100000010]
        |[persist.sys.camera_support_mark]: [0]
        |[persist.sys.car.type]: [138]
        |[persist.sys.cloud.support_flag]: [1]
    """.trimMargin()

    @Test
    fun `getprop day du cua Seal 14-09 - 138`() {
        assertEquals("138", ClusterCarType.parse(getpropSeal2026_09_14))
    }

    @Test
    fun `getprop mot khoa - so tran`() {
        assertEquals("138", ClusterCarType.parse("138\n"))
        assertEquals("162", ClusterCarType.parse("  162  "))
    }

    @Test
    fun `rong, khong phai so, qua dai, khoa khac - null (xe la, khong doan)`() {
        for (bad in listOf(null, "", "\n", "abc", "1234567", "[persist.sys.byd.theme]: [100000010]", "13 8")) {
            assertNull(ClusterCarType.parse(bad), "'$bad'")
        }
        assertEquals("getprop persist.sys.car.type", ClusterCarType.CMD)
    }
}
