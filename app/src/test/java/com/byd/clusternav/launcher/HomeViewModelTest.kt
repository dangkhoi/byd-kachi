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
            // `autostart` nạp từ chỗ giữ RIÊNG (không theo hồ sơ) — mô phỏng đúng `PrefsWorkspaceRepository.load()`,
            // nơi cờ này đọc từ `prefs.launcherAutostart()` chứ không từ bộ khoá của hồ sơ.
            return base.copy(
                activeProfile = active, profiles = profileList.toList(), themeMode = theme, embedded = false,
                autostart = autostartStore,
                // P7/P6: sổ cảnh cũng ở chỗ giữ RIÊNG (khoá riêng, không qua persist) — mô phỏng đúng nơi lưu thật.
                scenes = sceneStore,
            )
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

        /**
         * S1·T4 — cờ "tự mở khi nổ máy". CHUNG mọi hồ sơ (không nằm trong bộ khoá theo hồ sơ) nên nó KHÔNG đi qua
         * [persist]; bản giả phải giữ riêng, đúng như nơi lưu thật.
         */
        var autostartStore: Boolean = true; private set

        override fun autostart(): Boolean = autostartStore

        override fun setAutostart(on: Boolean) { autostartStore = on }

        /**
         * P7/P6 — sổ cảnh. Giữ riêng như [autostartStore] vì nó nằm ở **khoá riêng** (không đi qua [persist]), đúng
         * như nơi lưu thật: quên nửa "ghi bền" của một intent cảnh sẽ làm cảnh biến mất khi mở lại, im lặng.
         */
        var sceneStore: SceneBook = SceneBook.EMPTY; private set
        var sceneWrites = 0; private set
        var layoutWrites = 0; private set
        var lastLayout: GridLayout? = null; private set

        override fun sceneBook(): SceneBook = sceneStore

        override fun setSceneBook(book: SceneBook) { sceneStore = book; sceneWrites++ }

        override fun setGridLayout(layout: GridLayout?) { lastLayout = layout; layoutWrites++ }
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

    /**
     * Đặt THẲNG một viền — đường DUY NHẤT còn lại sau khi bỏ pill "Thanh" khỏi thanh trên.
     *
     * ⚠ Bài `cycleDockEdge xoay vien va persist` cũ đã **bỏ** cùng lúc với `HomeViewModel.cycleDockEdge()`: hành vi
     * xoay vòng không còn tồn tại nên bài canh nó chỉ còn canh mã chết. Nhưng phép khẳng định mà nó mang (đổi viền
     * thì state đổi **và** được ghi bền) thì vẫn cần — và [ĐO] `setDockEdge` trước đó **không có bài nào** canh, nên
     * xoá thẳng bài cũ là mất phép kiểm. Chuyển sang đây.
     */
    @Test fun `setDockEdge dat thang mot vien va persist`() = runTest {
        val fake = repo()
        val vm = HomeViewModel(fake)
        vm.uiState.test {
            assertEquals(DockEdge.BOTTOM, awaitItem().dock.edge)
            vm.setDockEdge(DockEdge.RIGHT)                       // bấm "Phải" phải ra "Phải", không phải viền kế tiếp
            assertEquals(DockEdge.RIGHT, awaitItem().dock.edge)
        }
        assertEquals(DockEdge.RIGHT, fake.lastPersisted!!.dock.edge)
    }

    /**
     * T6 · R-UI (m) — `setDockConfig` thay `toggleDock`: bộ chọn trả về một TẬP nên phép đổi phải có **cả hai
     * chiều**. Bài này kiểm đúng chiều mà cổng cũ không diễn tả được: bỏ tích một mã ⇒ nó phải RỜI thanh.
     */
    @Test fun `setDockConfig dat ca cau hinh, ca hai chieu, va persist`() = runTest {
        val fake = repo()
        val vm = HomeViewModel(fake)
        vm.uiState.test {
            val dock0 = awaitItem().dock
            assertFalse(dock0.enabled.contains("defrost"))               // "defrost" mặc định TẮT
            // Chiều BẬT: thêm "defrost" vào tập người dùng vừa chốt.
            vm.setDockConfig(DockSelection.apply(dock0, dock0.enabled.toSet() + "defrost"))
            val dock1 = awaitItem().dock
            assertTrue(dock1.enabled.contains("defrost"))
            // Chiều TẮT: bỏ nó khỏi tập ⇒ phải rời thanh (cổng `toggleDock` cũ không có cách nào bắt hụt việc này).
            vm.setDockConfig(DockSelection.apply(dock1, dock1.enabled.toSet() - "defrost"))
            assertFalse(awaitItem().dock.enabled.contains("defrost"))
        }
        assertFalse(fake.lastPersisted!!.dock.enabled.contains("defrost"))
    }

    /**
     * S1·T4 / R3 — **tự mở khi nổ máy**: đổi được + lưu bền.
     *
     * [SOÁT S1] Bài này bổ sung phần các bài canh dây nối KHÔNG kiểm được: chúng chỉ soi *hình dạng* mã nguồn, nên
     * một bản `setAutostart` chỉ đổi state mà quên gọi cổng dữ liệu (hoặc ngược lại) vẫn qua được. Ở đây kiểm HÀNH VI
     * ở cả hai đầu, và kiểm luôn rằng cờ này **không** đi qua `persist` (nó chung mọi hồ sơ, không thuộc bộ khoá theo
     * hồ sơ) — nếu ai đó chuyển nó vào `persist` thì đổi hồ sơ sẽ ghi đè cờ của cả máy.
     */
    @Test fun `setAutostart doi state va ghi ben qua cong du lieu`() = runTest {
        // Mặc định của MODEL phải khớp mặc định của nơi lưu (`getBoolean(..., true)`) và của cổng dữ liệu
        // (`fun autostart(): Boolean = true`): ba mặc định lệch nhau thì ô tick nói sai ngay lần mở đầu, trước cả khi
        // có gì được ghi. Đọc THẲNG model ở đây — đọc qua bản giả thì chỉ kiểm mặc định của bản giả.
        assertTrue(HomeUiState().autostart, "mặc định của model phải BẬT — launcher nên tự sẵn sàng")
        val fake = repo()
        val vm = HomeViewModel(fake)
        vm.uiState.test {
            assertTrue(awaitItem().autostart)
            vm.setAutostart(false)
            assertFalse(awaitItem().autostart)
        }
        assertFalse(fake.autostartStore, "phải ghi bền qua cổng dữ liệu, không chỉ đổi state")
        assertEquals(0, fake.persistCount, "cờ chung cả máy KHÔNG được đi qua persist theo hồ sơ")
    }

    /** Nạp lại (đổi hồ sơ) phải mang theo cờ đang lưu — mở lại màn Cài đặt là thấy đúng giá trị. */
    @Test fun `autostart nap lai dung gia tri da luu`() = runTest {
        val fake = repo()
        val vm = HomeViewModel(fake)
        vm.setAutostart(false)
        val vm2 = HomeViewModel(fake)
        assertFalse(vm2.uiState.value.autostart, "lượt nạp mới phải thấy cờ đã lưu, không về mặc định")
    }

    // ── P7 + P6 · CẢNH — hành vi thật của bốn intent ─────────────────────────────────────────────

    private fun liveState() = HomeUiState(
        workspace = WorkspaceState.of(LayoutPreset.QUAD, SlotContent.App("com.waze"), SlotContent.Widget("w_board")),
        dock = DockConfig(DockEdge.RIGHT, listOf("lock")),
        customLayout = GridLayout(listOf(GridFrame(0, 0, 6, 6), GridFrame(6, 0, 6, 6))),
    )

    @Test fun `saveScene chup trang thai dang dung va ghi ben`() = runTest {
        val fake = repo(liveState())
        val vm = HomeViewModel(fake)
        vm.saveScene("Đi làm")
        val scene = vm.uiState.value.scenes.byName("Đi làm")
        assertEquals(LayoutPreset.QUAD, scene!!.preset)
        assertEquals(SlotContent.App("com.waze"), scene.slots[0])
        assertEquals(DockEdge.RIGHT, scene.dock.edge)
        assertEquals("0,0,6,6;6,0,6,6", scene.gridLayout, "phải chụp cả bố cục tự vẽ")
        assertEquals(1, fake.sceneWrites, "state và ghi bền phải đi trong MỘT lượt")
        assertEquals(scene, fake.sceneStore.byName("Đi làm"), "và phải ghi qua cổng dữ liệu")
    }

    @Test fun `applyScene dua bo cuc, o va thanh nut ve dung canh`() = runTest {
        val fake = repo(liveState())
        val vm = HomeViewModel(fake)
        vm.saveScene("Gốc")
        // Đổi sang thứ khác hẳn, rồi gọi lại cảnh.
        vm.setPreset(LayoutPreset.ONE)
        vm.assignWidgets(0, listOf("w_pm25"))
        vm.setDockEdge(DockEdge.TOP)
        vm.setCustomLayout(null)
        vm.applyScene(vm.uiState.value.scenes.byName("Gốc")!!.id)
        val s = vm.uiState.value
        assertEquals(LayoutPreset.QUAD, s.preset)
        assertEquals(SlotContent.App("com.waze"), s.slots[0])
        assertEquals(DockEdge.RIGHT, s.dock.edge)
        assertEquals(listOf("lock"), s.dock.enabled)
        assertEquals(GridLayout(listOf(GridFrame(0, 0, 6, 6), GridFrame(6, 0, 6, 6))), s.customLayout)
        assertEquals(s.customLayout, fake.lastLayout, "bố cục nằm ở khoá RIÊNG ⇒ phải ghi riêng, không qua persist")
    }

    /**
     * ⚠ **R4 (C5)**: gọi cảnh là **MỘT** lượt state đổi, không phải ba. Ba lượt nghĩa là ba lần render với trạng thái
     * trung gian, và một trạng thái trung gian có số ô khác là đủ để bộ quyết định trả "dựng lại tất cả" ⇒ app đang
     * chiếu trong ô bị nhả/gắn lại.
     */
    @Test fun `applyScene chi phat DUNG MOT lan state doi`() = runTest {
        val fake = repo(liveState())
        val vm = HomeViewModel(fake)
        vm.saveScene("Gốc")
        val id = vm.uiState.value.scenes.byName("Gốc")!!.id
        vm.setPreset(LayoutPreset.ONE)
        vm.uiState.test {
            awaitItem()                       // trạng thái hiện tại
            vm.applyScene(id)
            val applied = awaitItem()
            assertEquals(LayoutPreset.QUAD, applied.preset)
            assertEquals(DockEdge.RIGHT, applied.dock.edge, "bố cục VÀ thanh nút phải về trong CÙNG một lượt phát")
            expectNoEvents()                  // không có lượt phát thứ hai
        }
    }

    @Test fun `applyScene voi ma la thi khong lam gi`() = runTest {
        val fake = repo(liveState())
        val vm = HomeViewModel(fake)
        val before = vm.uiState.value
        vm.applyScene("s7")
        assertEquals(before, vm.uiState.value)
        assertEquals(0, fake.layoutWrites, "mã lạ không được ghi gì")
    }

    @Test fun `setBootScene va deleteScene ghi ben, xoa thi dau tu bo`() = runTest {
        val fake = repo(liveState())
        val vm = HomeViewModel(fake)
        vm.saveScene("A")
        val id = vm.uiState.value.scenes.byName("A")!!.id
        vm.setBootScene(id)
        assertEquals(id, vm.uiState.value.scenes.bootSceneId)
        assertEquals(id, fake.sceneStore.bootSceneId, "dấu nổ máy phải lưu bền, không thì mở lại là mất")
        vm.deleteScene(id)
        assertTrue(vm.uiState.value.scenes.scenes.isEmpty())
        assertEquals(null, fake.sceneStore.bootSceneId, "xoá cảnh đang là cảnh nổ máy ⇒ dấu tự bỏ")
    }

    @Test fun `renameScene doi ten va ghi ben`() = runTest {
        val fake = repo(liveState())
        val vm = HomeViewModel(fake)
        vm.saveScene("Cũ")
        val id = vm.uiState.value.scenes.byName("Cũ")!!.id
        vm.renameScene(id, "Mới")
        assertEquals("Mới", vm.uiState.value.scenes.byId(id)!!.name)
        assertEquals("Mới", fake.sceneStore.byId(id)!!.name)
    }

    /** Sổ cảnh nằm ở khoá riêng ⇒ lượt nạp mới phải thấy nó (mở lại app là cảnh còn đó). */
    @Test fun `so canh nap lai dung gia tri da luu`() = runTest {
        val fake = repo(liveState())
        HomeViewModel(fake).saveScene("Đi xa")
        assertEquals("Đi xa", HomeViewModel(fake).uiState.value.scenes.scenes.single().name)
    }
}
