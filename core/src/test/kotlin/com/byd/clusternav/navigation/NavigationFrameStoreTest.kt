package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Kho khung dẫn đường: thứ tự và nguồn phải nhất quán, vì đây là chỗ duy nhất giữ trạng thái phiên.
 *
 * 2026-07-27: soát scope phát hiện Navigation + HUD có 2.473 dòng mà chỉ 42 bài kiểm, trong khi Cluster
 * Cast có 419. `NavigationFrameStore` là một trong những chỗ gần như chưa ai kiểm dù nó quyết định khung
 * nào được ghi và khung nào bị từ chối — một khung lùi thứ tự lọt vào là số trên cụm nhảy về quá khứ.
 */
class NavigationFrameStoreTest {

    private class MemoryPersistence : NavigationFramePersistence {
        var stored: StoredNavigationSession? = null
        var saves = 0
        var clears = 0
        override fun load(): StoredNavigationSession? = stored
        override fun save(session: StoredNavigationSession) { stored = session; saves++ }
        override fun clear() { stored = null; clears++ }
    }

    private val gmaps = NavigationSourceIdentity("com.google.android.apps.maps", "Maps")
    private val vietmap = NavigationSourceIdentity("vn.vietmap.live", "VietMap")

    private fun session(startedAt: Long = 1_000, frame: NavigationFrame? = null) =
        StoredNavigationSession("s-1", gmaps, startedAt, frame)

    private fun frame(
        sequence: Long,
        at: Long,
        sessionId: String = "s-1",
        source: NavigationSourceIdentity = gmaps,
    ) = NavigationFrame(sessionId, source, sequence, at, NavigationFrameContent(1, "Rẽ phải", 120, "Lê Duẩn", null, null, null, null))

    @Test
    fun `phien moi khong duoc bat dau bang mot khung nhap san`() {
        val store = PersistentNavigationFrameStore(MemoryPersistence())
        val ex = assertThrows(IllegalArgumentException::class.java) {
            store.start(session(frame = frame(1, 1_500)))
        }
        assertTrue(ex.message!!.contains("imported frame"))
    }

    @Test
    fun `khong co phien thi khong ghi duoc khung`() {
        val store = PersistentNavigationFrameStore(MemoryPersistence())
        assertThrows(IllegalStateException::class.java) { store.append(frame(1, 1_500)) }
    }

    @Test
    fun `khung phai tang thu tu`() {
        val store = PersistentNavigationFrameStore(MemoryPersistence())
        store.start(session())
        store.append(frame(5, 2_000))
        listOf(5L, 4L, 1L).forEach { sequence ->
            val ex = assertThrows(IllegalArgumentException::class.java) { store.append(frame(sequence, 3_000)) }
            assertTrue(ex.message!!.contains("sequence must increase"), "chấp nhận sequence $sequence")
        }
        assertEquals(5L, store.snapshot()!!.latestFrame!!.sequence, "khung bị từ chối không được ghi đè")
    }

    @Test
    fun `thoi gian khung khong duoc lui`() {
        val store = PersistentNavigationFrameStore(MemoryPersistence())
        store.start(session(startedAt = 1_000))
        store.append(frame(1, 2_000))
        val ex = assertThrows(IllegalArgumentException::class.java) { store.append(frame(2, 1_999)) }
        assertTrue(ex.message!!.contains("must not move backwards"))
    }

    @Test
    fun `khung cua phien khac hoac nguon khac bi tu choi`() {
        val store = PersistentNavigationFrameStore(MemoryPersistence())
        store.start(session())
        assertThrows(IllegalArgumentException::class.java) { store.append(frame(1, 2_000, sessionId = "s-2")) }
        assertThrows(IllegalArgumentException::class.java) { store.append(frame(1, 2_000, source = vietmap)) }
        assertNull(store.snapshot()!!.latestFrame, "không khung nào được ghi")
    }

    @Test
    fun `moi lan ghi thanh cong deu ben hoa`() {
        val persistence = MemoryPersistence()
        val store = PersistentNavigationFrameStore(persistence)
        store.start(session())
        store.append(frame(1, 2_000))
        store.append(frame(2, 3_000))
        assertEquals(3, persistence.saves, "một lần start và hai lần append")
        assertEquals(2L, persistence.stored!!.latestFrame!!.sequence)
    }

    @Test
    fun `khoi phuc doc lai dung phien da luu`() {
        val persistence = MemoryPersistence()
        persistence.stored = session(startedAt = 1_000, frame = frame(7, 5_000))
        val store = PersistentNavigationFrameStore(persistence)
        assertEquals(7L, store.rehydrate()!!.latestFrame!!.sequence)
        assertEquals(7L, store.snapshot()!!.latestFrame!!.sequence, "khôi phục phải nạp cả trạng thái trong bộ nhớ")
    }

    @Test
    fun `khoi phuc tu choi phien co khung som hon chinh phien`() {
        val persistence = MemoryPersistence()
        persistence.stored = session(startedAt = 9_000, frame = frame(1, 1_000))
        val store = PersistentNavigationFrameStore(persistence)
        assertThrows(IllegalArgumentException::class.java) { store.rehydrate() }
    }

    @Test
    fun `xoa lam sach ca bo nho va ban luu`() {
        val persistence = MemoryPersistence()
        val store = PersistentNavigationFrameStore(persistence)
        store.start(session())
        store.clear()
        assertNull(store.snapshot())
        assertNull(persistence.stored)
        assertEquals(1, persistence.clears)
    }
}
