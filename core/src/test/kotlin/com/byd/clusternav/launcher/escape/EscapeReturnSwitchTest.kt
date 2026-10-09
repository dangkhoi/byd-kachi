package com.byd.clusternav.launcher.escape

import com.byd.clusternav.launcher.ProfileScope
import com.byd.clusternav.launcher.SettingsCatalog
import com.byd.clusternav.launcher.SettingsGroup
import com.byd.clusternav.launcher.escape.EscapeReturnSwitch.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 2.98 · R18 — công tắc "Kéo app thoát ô về lại ô (thử nghiệm)". Khoá quyết định owner 10/10: OTA 2.98 với R18 TẮT mặc định;
 * TẮT ⇒ bảng gửi daemon là [EscapeReturnConfig.OFF] (daemon không đăng ký bộ nghe ⇒ đường 2.93); cầu chì bền THẮNG công tắc.
 */
class EscapeReturnSwitchTest {

    private val api = EscapeReturnApi.ANDROID_10_R47
    private val slots = mapOf(5 to "com.waze")

    @Test
    fun `mac dinh TAT cho 2_98`() {
        assertFalse(EscapeReturnSwitch.DEFAULT_ON, "owner 10/10: OTA với R18 tắt mặc định — đổi ở bản sau khi xe đã chứng minh")
    }

    @Test
    fun `TAT thi bang gui daemon la OFF - khong api, khong lo danh sach o`() {
        val s = EscapeReturnSwitch.resolve(switchOn = false, profileApi = api, sdkInt = 29, tripStored = null, versionCode = 201)
        assertEquals(Status.Off, s)
        val cfg = EscapeReturnSwitch.config(s, slots)
        assertEquals(EscapeReturnConfig.OFF, cfg)
        // Dây: bảng TẮT mã hoá ra đúng một dòng đầu ⇒ daemon `decodeConfig` → api null → gỡ/không đăng ký bộ nghe.
        assertEquals("${EscapeReturnWire.HEADER}\n", EscapeReturnWire.encodeConfig(cfg))
        assertNull(EscapeReturnWire.decodeConfig(EscapeReturnWire.encodeConfig(cfg))!!.api)
    }

    @Test
    fun `BAT thi bang mang api va o`() {
        val s = EscapeReturnSwitch.resolve(true, api, 29, null, 201)
        assertEquals(Status.On(api), s)
        assertEquals(EscapeReturnConfig(api, slots), EscapeReturnSwitch.config(s, slots))
    }

    @Test
    fun `cau chi ben thang cong tac, ban cai sau thu lai`() {
        val stored = EscapeReturnBreaker.persistValue(201, "NullPointerException:createTaskSnapshot")
        val s = EscapeReturnSwitch.resolve(true, api, 29, stored, 201)
        assertEquals(Status.Tripped("NullPointerException:createTaskSnapshot"), s)
        assertEquals(EscapeReturnConfig.OFF, EscapeReturnSwitch.config(s, slots))
        assertEquals(Status.On(api), EscapeReturnSwitch.resolve(true, api, 29, stored, 202), "bản cài khác ⇒ thử lại một lần")
        // Dấu hỏng không phải bằng chứng (khớp `persistActive`).
        assertEquals(Status.On(api), EscapeReturnSwitch.resolve(true, api, 29, "rác", 201))
        assertEquals("?", EscapeReturnSwitch.tripWhy("201|", 201))
        assertEquals("?", EscapeReturnSwitch.tripWhy("201", 201))
    }

    @Test
    fun `may khong ho tro thi cong tac khong co tac dung`() {
        assertEquals(Status.Unsupported, EscapeReturnSwitch.resolve(true, null, 31, null, 201), "DiLink 5: ClusterProfile.escapeReturn = null")
        assertEquals(Status.Unsupported, EscapeReturnSwitch.resolve(true, api, 31, null, 201), "sai đời API")
        assertEquals(EscapeReturnConfig.OFF, EscapeReturnSwitch.config(Status.Unsupported, slots))
    }

    @Test
    fun `khoa cong tac theo XE, co muc Cai dat o nhom Man hinh chinh`() {
        assertEquals(ProfileScope.Scope.DEVICE, ProfileScope.scopeOf(EscapeReturnSwitch.PREF_KEY))
        assertEquals(ProfileScope.Scope.DEVICE, ProfileScope.scopeOf("kachi_escape_return_trip"), "cùng phạm vi với cầu chì bền")
        assertEquals(SettingsGroup.HOME, SettingsCatalog.groupOf(EscapeReturnSwitch.PREF_KEY))
        assertEquals("home_escape_return", SettingsCatalog.entryOf(EscapeReturnSwitch.PREF_KEY)?.id)
    }
}
