package com.byd.clusternav.modules.clustercast.nowplaying

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.12 — bộ ghi nguồn 11 của 3.1.11 đã gỡ; khoá lại ĐƯỜNG TRẢ LẠI lúc khởi động (chuyển từ `ClusterNowPlayingPlanTest`):
 * xe nào từng có dấu bền thì lần khởi động đầu của 3.1.12 trả đúng nguồn cũ — và không bao giờ ghi mù.
 */
class ClusterNowPlayingLegacyTest {

    private val src = 0x33F00030

    private class FakeSink(var readable: Boolean = true) : ClusterNowPlayingLegacy.Sink {
        val bus = HashMap<Int, Int>()
        val writes = ArrayList<String>()
        var cleared = false
        override fun readInt(id: Int): Int? = if (readable) bus[id] else null
        override fun writeInt(id: Int, value: Int): String { writes += "$id=$value"; bus[id] = value; return "0" }
        override fun clearMarker() { cleared = true }
        override fun log(line: String) = Unit
    }

    @Test fun `dau ben con va nguon van la cua Kachi - tra nguon cu roi don`() {
        val s = FakeSink().apply { bus[src] = 11 }
        ClusterNowPlayingLegacy.restoreAtStartup("$src:26:11", s)
        assertEquals(26, s.bus[src]); assertTrue(s.cleared)
    }

    @Test fun `nguoi khac da ghi nguon - giu cua ho, van don dau`() {
        val s = FakeSink().apply { bus[src] = 6 }
        ClusterNowPlayingLegacy.restoreAtStartup("$src:26:11", s)
        assertEquals(6, s.bus[src]); assertTrue(s.writes.isEmpty()); assertTrue(s.cleared)
    }

    @Test fun `khong doc duoc - khong ghi mu`() {
        val s = FakeSink(readable = false)
        ClusterNowPlayingLegacy.restoreAtStartup("$src:26:11", s)
        assertTrue(s.writes.isEmpty()); assertTrue(s.cleared)
    }

    @Test fun `dau hong - chi don, khong dau - khong lam gi`() {
        val bad = FakeSink()
        ClusterNowPlayingLegacy.restoreAtStartup("rac", bad)
        assertTrue(bad.writes.isEmpty()); assertTrue(bad.cleared)
        val none = FakeSink()
        assertNull(ClusterNowPlayingLegacy.restoreAtStartup(null, none))
        assertTrue(!none.cleared)
        assertNull(ClusterNowPlayingLegacy.decode("$src:300:11"), "nguồn ngoài miền byte ⇒ dấu hỏng")
    }
}
