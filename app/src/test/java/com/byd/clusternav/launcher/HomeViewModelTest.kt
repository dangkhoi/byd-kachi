package com.byd.clusternav.launcher

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Test [HomeViewModel] — NGUỒN SỰ THẬT DUY NHẤT phát `StateFlow<HomeUiState>` một chiều. Dùng Turbine (awaitItem)
 * + coroutines-test (runTest) trên một [FakeWorkspaceRepository] in-memory (không cần Android/SharedPreferences).
 *
 * VM cập nhật state ĐỒNG BỘ + ghi bền ĐỒNG BỘ (không viewModelScope) → không cần `Dispatchers.setMain`; StateFlow
 * phát ngay trên cùng nhịp nên awaitItem tất định. Test này nằm ở :app (chạm khai báo app-only [HomeViewModel]).
 */
class HomeViewModelTest {

    // ── Fake repository in-memory theo hồ sơ (thay WorkspacePrefs/SharedPreferences) ──
    private class FakeWorkspaceRepository(initial: HomeUiState) : WorkspaceRepository {
        var persistCount = 0; private set
        var lastPersisted: HomeUiState? = null; private set
        private val store = HashMap<String, HomeUiState>()
        private val profileList = initial.profiles.toMutableList()
        private var active = initial.activeProfile
        private var theme = initial.themeMode

        init { store[active] = initial }

        /** Gieo trước một hồ sơ có sẵn state (cho test switchProfile). */
        fun seed(profile: String, state: HomeUiState) {
            if (profile !in profileList) profileList.add(profile)
            store[profile] = state
        }

        override fun load(): HomeUiState {
            val base = store[active] ?: HomeUiState(activeProfile = active)
            return base.copy(activeProfile = active, profiles = profileList.toList(), themeMode = theme, embedded = false)
        }

        override fun persist(state: HomeUiState) {
            persistCount++
            lastPersisted = state
            active = state.activeProfile
            theme = state.themeMode
            store[state.activeProfile] = state
        }

        override fun switchProfile(name: String): HomeUiState {
            active = name
            return load()
        }

        override fun addProfile(name: String): HomeUiState {
            if (name !in profileList) profileList.add(name)
            active = name
            store.putIfAbsent(name, HomeUiState(activeProfile = name))
            return load()
        }

        override fun deleteProfile(name: String): HomeUiState {
            if (profileList.size > 1) {
                profileList.remove(name)
                if (active == name) active = profileList.first()
            }
            return load()
        }
    }

    private fun repo(state: HomeUiState = HomeUiState()) = FakeWorkspaceRepository(state)

    @Test fun `initial state nap tu repository`() = runTest {
        val initial = HomeUiState(
            // of(...) tự đệm tới trần ô ⇒ test không phải sửa mỗi lần trần đổi.
            workspace = WorkspaceState.of(LayoutPreset.QUAD, SlotContent.App("com.a")),
            activeProfile = "P1", profiles = listOf("P1"), themeMode = ThemeMode.DAY,
        )
        val vm = HomeViewModel(repo(initial))
        vm.uiState.test {
            val s = awaitItem()
            assertEquals(LayoutPreset.QUAD, s.preset)
            assertEquals(SlotContent.App("com.a"), s.slots[0])
            assertEquals("P1", s.activeProfile)
            assertEquals(ThemeMode.DAY, s.themeMode)
            assertFalse(s.embedded)
        }
    }

    @Test fun `initialEmbedded phan chieu vao state`() = runTest {
        val vm = HomeViewModel(repo(), initialEmbedded = true)
        assertTrue(vm.uiState.value.embedded)
    }

    @Test fun `assignApp cap nhat uiState va persist`() = runTest {
        val fake = repo()
        val vm = HomeViewModel(fake)
        vm.uiState.test {
            assertEquals(SlotContent.Empty, awaitItem().slots[1])       // initial
            vm.assignApp(1, "com.google.android.apps.maps")
            assertEquals(SlotContent.App("com.google.android.apps.maps"), awaitItem().slots[1])
        }
        assertEquals(SlotContent.App("com.google.android.apps.maps"), fake.lastPersisted!!.slots[1])
        assertEquals(1, fake.persistCount)
    }

    @Test fun `setPreset cap nhat va persist`() = runTest {
        val fake = repo()
        val vm = HomeViewModel(fake)
        vm.uiState.test {
            assertEquals(LayoutPreset.THREE, awaitItem().preset)
            vm.setPreset(LayoutPreset.QUAD)
            assertEquals(LayoutPreset.QUAD, awaitItem().preset)
        }
        assertEquals(LayoutPreset.QUAD, fake.lastPersisted!!.preset)
    }

    @Test fun `clearSlot ve trong va persist`() = runTest {
        val fake = repo(HomeUiState(workspace = WorkspaceState().withSlot(0, SlotContent.App("com.x"))))
        val vm = HomeViewModel(fake)
        vm.uiState.test {
            assertEquals(SlotContent.App("com.x"), awaitItem().slots[0])
            vm.clearSlot(0)
            assertEquals(SlotContent.Empty, awaitItem().slots[0])
        }
        assertEquals(SlotContent.Empty, fake.lastPersisted!!.slots[0])
    }

    @Test fun `swapSlots doi cho 2 o va persist`() = runTest {
        val start = WorkspaceState()
            .withSlot(0, SlotContent.App("com.a"))
            .withSlot(1, SlotContent.Widget("w_energy"))
        val fake = repo(HomeUiState(workspace = start))
        val vm = HomeViewModel(fake)
        vm.uiState.test {
            awaitItem()
            vm.swapSlots(0, 1)
            val s = awaitItem()
            assertEquals(SlotContent.Widget(listOf("w_energy")), s.slots[0])
            assertEquals(SlotContent.App("com.a"), s.slots[1])
        }
        assertEquals(SlotContent.App("com.a"), fake.lastPersisted!!.slots[1])
    }

    @Test fun `assignWidgets nhieu widget va xoa ve trong`() = runTest {
        val fake = repo()
        val vm = HomeViewModel(fake)
        vm.uiState.test {
            awaitItem()
            vm.assignWidgets(2, listOf("w_energy", "w_pm25"))
            assertEquals(SlotContent.Widget(listOf("w_energy", "w_pm25")), awaitItem().slots[2])
            vm.assignWidgets(2, emptyList())
            assertEquals(SlotContent.Empty, awaitItem().slots[2])
        }
        assertEquals(SlotContent.Empty, fake.lastPersisted!!.slots[2])
        assertEquals(2, fake.persistCount)
    }

    @Test fun `switchProfile nap lai workspace cua ho so khac`() = runTest {
        val fake = repo(HomeUiState(
            workspace = WorkspaceState(preset = LayoutPreset.ONE), activeProfile = "P1", profiles = listOf("P1", "P2"),
        ))
        fake.seed("P2", HomeUiState(workspace = WorkspaceState(preset = LayoutPreset.QUAD), activeProfile = "P2"))
        val vm = HomeViewModel(fake)
        vm.uiState.test {
            assertEquals(LayoutPreset.ONE, awaitItem().preset)          // P1
            vm.switchProfile("P2")
            val s = awaitItem()
            assertEquals(LayoutPreset.QUAD, s.preset)                   // P2 reloaded
            assertEquals("P2", s.activeProfile)
        }
    }

    @Test fun `addProfile tao ho so moi va kich hoat`() = runTest {
        val fake = repo(HomeUiState(activeProfile = "P1", profiles = listOf("P1")))
        val vm = HomeViewModel(fake)
        vm.uiState.test {
            awaitItem()
            vm.addProfile("Đường trường")
            val s = awaitItem()
            assertEquals("Đường trường", s.activeProfile)
            assertTrue(s.profiles.contains("Đường trường"))
        }
    }

    @Test fun `setThemeMode cap nhat va persist`() = runTest {
        val fake = repo()
        val vm = HomeViewModel(fake)
        vm.uiState.test {
            assertEquals(ThemeMode.NIGHT, awaitItem().themeMode)
            vm.setThemeMode(ThemeMode.DAY)
            assertEquals(ThemeMode.DAY, awaitItem().themeMode)
        }
        assertEquals(ThemeMode.DAY, fake.lastPersisted!!.themeMode)
    }

    @Test fun `setEmbedded chi runtime khong persist`() = runTest {
        val fake = repo()
        val vm = HomeViewModel(fake)
        vm.uiState.test {
            assertFalse(awaitItem().embedded)
            vm.setEmbedded(true)
            assertTrue(awaitItem().embedded)
        }
        assertEquals(0, fake.persistCount)   // setEmbedded KHÔNG ghi bền
    }

    @Test fun `setCarStatus bom trang thai xe live vao state, KHONG persist`() = runTest {
        val fake = repo()
        val vm = HomeViewModel(fake)
        vm.uiState.test {
            assertEquals(CarStatus(), awaitItem().carStatus)                       // initial rỗng ("—")
            vm.setCarStatus(CarStatus(energy = CarStatus.Energy(soc = 77)))
            assertEquals(77, awaitItem().carStatus.energy.soc)                      // live cập nhật
        }
        assertEquals(0, fake.persistCount)   // trạng thái xe LIVE KHÔNG ghi bền
    }

    @Test fun `cycleDockEdge xoay vien va persist`() = runTest {
        val fake = repo()
        val vm = HomeViewModel(fake)
        vm.uiState.test {
            assertEquals(DockEdge.BOTTOM, awaitItem().dock.edge)
            vm.cycleDockEdge()
            assertEquals(DockEdge.LEFT, awaitItem().dock.edge)
        }
        assertEquals(DockEdge.LEFT, fake.lastPersisted!!.dock.edge)
    }

    @Test fun `toggleDock bat tat control va persist`() = runTest {
        val fake = repo()
        val vm = HomeViewModel(fake)
        vm.uiState.test {
            val enabled0 = awaitItem().dock.enabled
            assertFalse(enabled0.contains("defrost"))                    // "defrost" mặc định TẮT
            vm.toggleDock("defrost", true)
            assertTrue(awaitItem().dock.enabled.contains("defrost"))
        }
        assertTrue(fake.lastPersisted!!.dock.enabled.contains("defrost"))
    }
}
