package com.byd.clusternav.launcher.escape

import com.byd.clusternav.launcher.ProfileScope
import com.byd.clusternav.launcher.SettingsCatalog
import com.byd.clusternav.launcher.SettingsGroup
import com.byd.clusternav.launcher.escape.EscapeReturnSwitch.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 2.98 · R18 — công tắc "Kéo app thoát ô về lại ô (thử nghiệm)". 2.98 OTA với R18 TẮT mặc định (owner 10/10, chưa đo xe);
 * 2.99 MẶC ĐỊNH BẬT sau bằng chứng xe 10/10 (Waze `esc moved` ×2, không NPE — `oncar-seal-2026-10-10-r18-sla-can.md` §1).
 * TẮT ⇒ bảng gửi daemon là [EscapeReturnConfig.OFF] (daemon không đăng ký bộ nghe ⇒ đường 2.93); cầu chì bền THẮNG công tắc
 * — kể cả khi mặc định bật; máy không hỗ trợ (DiLink 5) ⇒ Unsupported dù mặc định bật.
 */
class EscapeReturnSwitchTest {

    private val api = EscapeReturnApi.ANDROID_10_R47
    private val slots = mapOf(5 to "com.waze")

    @Test
    fun `mac dinh BAT tu 2_99 - sau bang chung xe 10-10, van la cong tac sau cau chi va cong may ho tro`() {
        assertTrue(EscapeReturnSwitch.DEFAULT_ON, "2.99: Waze xe 10/10 sạch ⇒ bật mặc định (oncar-seal-2026-10-10 §1)")
        // Mặc định bật KHÔNG qua mặt cầu chì bền và cổng máy-hỗ-trợ: cùng `resolve`, cùng kết luận OFF cho daemon.
        val stored = EscapeReturnBreaker.persistValue(202, "NullPointerException:createTaskSnapshot")
        assertEquals(EscapeReturnConfig.OFF, EscapeReturnSwitch.config(EscapeReturnSwitch.resolve(EscapeReturnSwitch.DEFAULT_ON, api, 29, stored, 202), slots))
        assertEquals(Status.Unsupported, EscapeReturnSwitch.resolve(EscapeReturnSwitch.DEFAULT_ON, null, 31, null, 202), "DiLink 5 vẫn tắt")
        assertEquals(Status.On(api), EscapeReturnSwitch.resolve(EscapeReturnSwitch.DEFAULT_ON, api, 29, null, 202))
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
