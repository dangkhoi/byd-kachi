package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.87 · R-AH3 — công tắc "Tự ẩn nút ⇄" theo HỒ SƠ: kiểm HÀNH VI của [HomeViewModel] (bài canh dây nối chỉ soi hình dạng
 * mã, nên một `setSlotHeadAutoHide` chỉ đổi state mà quên ghi — hoặc ngược lại — vẫn qua được bài kia).
 */
class SlotHeadAutoHideSettingTest {

    /** Bản giả theo hồ sơ: cờ nằm ở KHOÁ RIÊNG mỗi hồ sơ (như `<hồ sơ>__swap_button_autohide`), không qua `persist`. */
    private class Repo : WorkspaceRepository {
        var active = HomeUiState.DEFAULT_PROFILE
        val flags = HashMap<String, Boolean>()
        var persistCount = 0
        override fun load() = HomeUiState(activeProfile = active, slotHeadAutoHide = flags[active] ?: true)
        override fun persist(state: HomeUiState) { persistCount++ }
        override fun switchProfile(name: String): HomeUiState { active = name; return load() }
        override fun addProfile(name: String) = switchProfile(name)
        override fun deleteProfile(name: String) = load()
        override fun slotHeadAutoHide() = flags[active] ?: true
        override fun setSlotHeadAutoHide(on: Boolean) { flags[active] = on }
    }

    @Test
    fun `mac dinh BAT o ca ba tang`() {
        assertTrue(HomeUiState().slotHeadAutoHide, "model: mặc định BẬT (owner muốn ẩn cho đẹp)")
        assertTrue(object : WorkspaceRepository {
            override fun load() = HomeUiState()
            override fun persist(state: HomeUiState) {}
            override fun switchProfile(name: String) = HomeUiState()
            override fun addProfile(name: String) = HomeUiState()
            override fun deleteProfile(name: String) = HomeUiState()
        }.slotHeadAutoHide(), "cổng dữ liệu: thân mặc định khớp nơi lưu (vắng khoá = bật)")
    }

    @Test
    fun `doi state va ghi ben trong mot luot, khong qua persist`() {
        val repo = Repo()
        val vm = HomeViewModel(repo)
        assertTrue(vm.uiState.value.slotHeadAutoHide)
        vm.setSlotHeadAutoHide(false)
        assertFalse(vm.uiState.value.slotHeadAutoHide, "state đổi ngay ⇒ render áp lại ⇄")
        assertEquals(false, repo.flags[HomeUiState.DEFAULT_PROFILE], "phải ghi bền qua cổng dữ liệu")
        assertEquals(0, repo.persistCount, "khoá riêng — không đi qua persist")
        assertFalse(HomeViewModel(repo).uiState.value.slotHeadAutoHide, "mở lại app thấy đúng cờ đã lưu")
    }

    @Test
    fun `doi ho so thi co doi theo ho so`() {
        val repo = Repo()
        val vm = HomeViewModel(repo)
        vm.setSlotHeadAutoHide(false)
        vm.switchProfile("B")
        assertTrue(vm.uiState.value.slotHeadAutoHide, "hồ sơ B chưa chỉnh ⇒ mặc định BẬT, không mượn cờ của hồ sơ trước")
        vm.switchProfile(HomeUiState.DEFAULT_PROFILE)
        assertFalse(vm.uiState.value.slotHeadAutoHide, "quay lại hồ sơ cũ ⇒ cờ của nó")
    }
}
