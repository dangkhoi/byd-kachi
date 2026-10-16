package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.modules.clustercast.nowplaying.ClusterNowPlayingIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 3.1.11 · CLUSTER-NOW-PLAYING — số liệu tên-bài-lên-cụm nằm ở HỒ SƠ đời xe (CLAUDE.md §7): chỉ Seal DL3 (fw 2606, đã đọc
 * firmware cụm) có; đời khác/xe lạ ⇒ `null` ⇒ tính năng báo "chưa hỗ trợ". Override (chuỗi share) không mang/xoá được nó.
 */
class ClusterProfileNowPlayingTest {

    @Test fun `Seal DL3 mang id fw2606`() {
        val np = ClusterProfile.SEAL_DL3.nowPlaying
        assertEquals(ClusterNowPlayingIds.FW2606, np)
        assertEquals(0x33F00030, np!!.sourceId)
        assertEquals(0x43FB1008, np.titleId)
        assertEquals(0x43E0000A, np.stateId)
        assertEquals(11, np.overrideSource)
    }

    @Test fun `doi khac va xe la khong co`() {
        assertNull(ClusterProfile.DL5.nowPlaying)
        assertNull(ClusterProfile.GENERIC_FALLBACK.nowPlaying)
        assertNull(ClusterProfile.nowPlayingFor("dilink5"))
    }

    @Test fun `parse override va doi theo ma xe giu dung so lieu theo id`() {
        val seal = ClusterProfile.parse(ClusterProfile.SEAL_DL3.export())!!
        assertEquals(ClusterNowPlayingIds.FW2606, seal.nowPlaying)
        assertEquals(ClusterNowPlayingIds.FW2606, ClusterProfile.SEAL_DL3.forCarType("138").nowPlaying)
        assertNull(ClusterProfile.parse(ClusterProfile.GENERIC_FALLBACK.export())!!.nowPlaying)
        assertEquals(ClusterNowPlayingIds.FW2606, ClusterProfile.detectSeed("BYD AUTO", "BYD", "BYD", "", "138").nowPlaying)
    }
}
