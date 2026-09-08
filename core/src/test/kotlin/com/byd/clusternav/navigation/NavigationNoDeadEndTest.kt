package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Bản Navigation của phép quét ngõ cụt đã dùng cho Cluster Cast.
 *
 * Ngày 2026-07-27, Cast lộ ba lỗi cùng một họ: một trạng thái không còn hành động nào, một phép ghi cục bộ
 * bị khoá theo trạng thái xe, và một tập phép bị thu về đúng `STOP` trong lúc chờ. Navigation chưa gây hậu
 * quả thấy được, nhưng **chưa gây không có nghĩa là đúng** — nên quét luôn.
 *
 * Bài kiểm này cũng đã bắt được hai lỗi thật ngay khi viết: `RECONNECT_SOURCE` khai trong enum mà không
 * nơi nào cấp (nguồn mất cập nhật thì màn hình không có nút nào), và `disabledReasons` luôn rỗng nên không
 * phép mờ nào giải thích được vì sao.
 */
class NavigationNoDeadEndTest {

    private val source = NavigationSourceIdentity("com.example.maps", "Example Maps")
    private val content = NavigationFrameContent(2, "Rẽ phải", 250, "Đường Ví Dụ", null, null, null, null)

    private class MemoryPersistence : NavigationFramePersistence {
        var stored: StoredNavigationSession? = null
        override fun load() = stored
        override fun save(session: StoredNavigationSession) { stored = session }
        override fun clear() { stored = null }
    }

    private class Port(override val target: NavigationOutputTarget) : NavigationOutputPort {
        var health = NavigationOutputHealth(target, false, NavigationOutputStatus.OFF, 0, null, null, null, null)
        override fun setEnabled(enabled: Boolean) { health = health.copy(enabled = enabled) }
        override fun submit(frame: NavigationFrame) = OutputSubmission.ACCEPTED
        override fun markDisplayVerified(sequence: Long, observedAtEpochMs: Long) = true
        override fun markStale() = Unit
        override fun recordFault(reason: NavigationOutputFailureReason, detail: String?) {
            health = health.copy(status = NavigationOutputStatus.FAULT(reason, detail))
        }
        override fun stopSession() = Unit
        override fun health() = health
        override fun close() = Unit
    }

    private fun coordinator(now: Long, lane: Port, hud: Port, staleAfterMs: Long = 100) =
        NavigationSessionCoordinator(
            PersistentNavigationFrameStore(MemoryPersistence()), lane, hud,
            nowEpochMs = { now }, nextSessionId = { "nav-1" }, staleAfterMs = staleAfterMs,
        )

    @Test
    fun `moi to hop trang thai deu con hanh dong va deu giai thich duoc`() {
        var checked = 0
        val stuck = mutableListOf<String>()
        val unexplained = mutableListOf<String>()
        NavigationPermission.entries.forEach { permission ->
            listOf(false, true).forEach { withSession ->
                listOf(false, true).forEach { laneEnabled ->
                    listOf(false, true).forEach { laneFault ->
                        listOf(false, true).forEach { stale ->
                            checked++
                            val lane = Port(NavigationOutputTarget.CLUSTER_LANE)
                            val hud = Port(NavigationOutputTarget.HUD)
                            val nav = coordinator(now = 10_000, lane = lane, hud = hud)
                            nav.setPermission(permission)
                            if (withSession && permission == NavigationPermission.GRANTED) {
                                nav.startSession(source)
                                nav.acceptFrame(source, content)
                            }
                            if (laneEnabled) lane.setEnabled(true)
                            if (laneFault) lane.recordFault(NavigationOutputFailureReason.EXECUTOR_REJECTED, null)
                            if (stale) nav.setPermission(permission)
                            val state = nav.snapshot()
                            val label = "perm=$permission session=$withSession laneOn=$laneEnabled " +
                                "fault=$laneFault stale=$stale"
                            if (state.allowedActions.isEmpty()) stuck += label
                            val missing = NavigationAction.entries
                                .filterNot { it in state.allowedActions || it in state.disabledReasons }
                            if (missing.isNotEmpty()) unexplained += "$label → $missing"
                        }
                    }
                }
            }
        }
        assertEquals(48, checked, "quét phải vét cạn các chiều đã khai")
        assertTrue(stuck.isEmpty(), "trạng thái không còn hành động nào: ${stuck.take(4)}")
        assertTrue(unexplained.isEmpty(), "phép vừa không được cấp vừa không có lý do: ${unexplained.take(4)}")
    }

    @Test
    fun `phep duoc cap va phep bi tu choi khong bao gio giao nhau`() {
        val lane = Port(NavigationOutputTarget.CLUSTER_LANE)
        val hud = Port(NavigationOutputTarget.HUD)
        val nav = coordinator(now = 10_000, lane = lane, hud = hud)
        nav.setPermission(NavigationPermission.GRANTED)
        nav.startSession(source)
        val state = nav.snapshot()
        val overlap = state.allowedActions.intersect(state.disabledReasons.keys)
        assertTrue(overlap.isEmpty(), "vừa cho vừa từ chối: $overlap")
    }

    @Test
    fun `nguon khong con tuoi thi luon co duong noi lai`() {
        // Đây là ca đã bị bỏ quên: notification ngừng cập nhật, phiên vẫn còn, mà màn hình không có nút nào
        // ngoài Dừng. Người dùng chỉ còn cách tắt bật lại cả tính năng.
        val lane = Port(NavigationOutputTarget.CLUSTER_LANE)
        val hud = Port(NavigationOutputTarget.HUD)
        val nav = NavigationSessionCoordinator(
            PersistentNavigationFrameStore(MemoryPersistence()), lane, hud,
            nowEpochMs = { 10_000 }, nextSessionId = { "nav-1" }, staleAfterMs = 100,
        )
        nav.setPermission(NavigationPermission.GRANTED)
        nav.startSession(source)
        val state = nav.snapshot()
        assertTrue(
            state.source.freshness !is NavigationFreshness.Fresh,
            "phiên chưa có khung nào thì không thể coi là tươi",
        )
        assertTrue(
            NavigationAction.RECONNECT_SOURCE in state.allowedActions,
            "nguồn không tươi mà không có đường nối lại",
        )
    }

    @Test
    fun `nguon tuoi thi noi lai bi tu choi kem ly do doc duoc`() {
        val lane = Port(NavigationOutputTarget.CLUSTER_LANE)
        val hud = Port(NavigationOutputTarget.HUD)
        val nav = coordinator(now = 10_000, lane = lane, hud = hud, staleAfterMs = 100_000)
        nav.setPermission(NavigationPermission.GRANTED)
        nav.startSession(source)
        nav.acceptFrame(source, content)
        val state = nav.snapshot()
        assertEquals(
            NavigationActionDisabledReason.SOURCE_IS_FRESH,
            state.disabledReasons[NavigationAction.RECONNECT_SOURCE],
        )
    }
}
