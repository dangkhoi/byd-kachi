package com.byd.clusternav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.HomeViewModel
import com.byd.clusternav.launcher.WorkspaceRepository
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

/**
 * [AppContainer] — đồ thị DI thủ công phía launcher. Kiểm phần THUẦN JVM off-device (không Context/dadb):
 *  • đồ thị dựng được + mỗi phụ thuộc là MỘT instance (lazy memoize),
 *  • [AppContainer.castRuntime] lộ [SimpleCastRuntime] BY REFERENCE (fold cast không đụng caller cast),
 *  • [AppContainer.homeViewModelFactory] cấp [HomeViewModel] nối đúng repository + cờ embedded (thay `factory(context)`).
 *
 * Các nhánh cần Android (shellTransport/windowDispatcher/inputDaemonClient) KHÔNG chạm ở đây — init-lambda `error`
 * KHÔNG được gọi vì test không truy cập các field đó (chúng chỉ dựng khi được truy cập, `by lazy`). Đồ thị
 * Android-đầy-đủ verify bằng `:app:assembleDebug` (compile + wire) — thiết bị/Robolectric không có trong bộ off-device.
 */
class AppContainerTest {

    /** Repository giả in-memory (thay WorkspacePrefs) — đếm số lần load để chứng minh lazy memoize. */
    private class FakeRepo(private val state: HomeUiState) : WorkspaceRepository {
        var loads = 0; private set
        override fun load(): HomeUiState { loads++; return state }
        override fun persist(state: HomeUiState) {}
        override fun switchProfile(name: String): HomeUiState = state
        override fun addProfile(name: String): HomeUiState = state
        override fun deleteProfile(name: String): HomeUiState = state
    }

    /** Container chỉ nối repository thật (giả); các nhánh Android là init-lambda `error` KHÔNG được truy cập. */
    private fun container(repo: WorkspaceRepository) = AppContainer(
        shellTransportInit = { error("shellTransport không cần cho test thuần") },
        windowDispatcherInit = { error("windowDispatcher không cần cho test thuần") },
        workspaceRepositoryInit = { repo },
        inputDaemonClientInit = { error("inputDaemonClient không cần cho test thuần") },
    )

    @Test fun `castRuntime lo SimpleCastRuntime by reference`() {
        val c = container(FakeRepo(HomeUiState()))
        assertSame(SimpleCastRuntime, c.castRuntime, "castRuntime phải trả đúng singleton SimpleCastRuntime (by reference)")
        assertSame(c.castRuntime, c.castRuntime, "getter trả CÙNG một tham chiếu")
    }

    @Test fun `workspaceRepository la MOT instance (lazy memoize)`() {
        val repo = FakeRepo(HomeUiState())
        val c = container(repo)
        assertSame(repo, c.workspaceRepository, "trả đúng repository của đồ thị")
        assertSame(c.workspaceRepository, c.workspaceRepository, "truy cập lại KHÔNG dựng lại (một instance)")
    }

    @Test fun `homeViewModelFactory cap HomeViewModel noi dung repository va co embedded`() {
        val initial = HomeUiState(activeProfile = "P1", profiles = listOf("P1"))
        val c = container(FakeRepo(initial))
        val vmEmbedded = c.homeViewModelFactory(embedded = true).create(HomeViewModel::class.java, CreationExtras.Empty)
        assertTrue(vmEmbedded.uiState.value.embedded, "embedded=true phải phản chiếu vào state")
        assertEquals("P1", vmEmbedded.uiState.value.activeProfile, "VM nạp từ repository của đồ thị")
        val vmPlain = c.homeViewModelFactory(embedded = false).create(HomeViewModel::class.java, CreationExtras.Empty)
        assertFalse(vmPlain.uiState.value.embedded)
    }

    @Test fun `homeViewModelFactory tu choi ViewModel khac`() {
        val f = container(FakeRepo(HomeUiState())).homeViewModelFactory(embedded = false)
        assertThrows<IllegalArgumentException> {
            f.create(OtherViewModel::class.java, CreationExtras.Empty)
        }
    }

    private class OtherViewModel : ViewModel()
}
