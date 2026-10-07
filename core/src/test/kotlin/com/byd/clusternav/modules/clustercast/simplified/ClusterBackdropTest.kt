package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** 2.94 · CLUSTER-BACKDROP-DAY — tối luôn đen (không bao giờ nền sáng trên cụm tối); sáng ⇒ màu nền ngày của đúng theme cụm. */
class ClusterBackdropTest {

    @Test
    fun `he thong toi thi luon den, moi kieu cum`() {
        listOf(CastStyle.RECT, CastStyle.CURVED, null).forEach {
            assertEquals(ClusterBackdrop.NIGHT, ClusterBackdrop.color(systemNight = true, frame = it))
        }
    }

    @Test
    fun `he thong sang thi lay mau nen ngay cua theme cum`() {
        assertEquals(0xFFB5C6D4.toInt(), ClusterBackdrop.color(systemNight = false, frame = CastStyle.RECT))
        assertEquals(0xFFC0CFDB.toInt(), ClusterBackdrop.color(systemNight = false, frame = CastStyle.CURVED))
        assertEquals(ClusterBackdrop.DAY_CURVED, ClusterBackdrop.color(systemNight = false, frame = null), "chưa rõ kiểu ⇒ Bo tròn")
    }
}
