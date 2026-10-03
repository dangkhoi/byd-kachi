package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ FIX286 · SR5 — giọng nói MỞ nóc hỏi xác nhận MẶC ĐỊNH, ĐÓNG không hỏi (owner 03/10 *"1 ok, nên xác nhận"*) ═══
 *
 * Vì sao có: 2.86 làm nút nóc CHẠY THẬT (100/0 thay 1/2). Một lần nghe nhầm thành *"mở cửa sổ trời"* nay mở nóc
 * thật — kể cả khi xe đang chạy (nóc cố ý không bị chặn theo tốc độ). Đây là ngoại lệ CÓ TÊN của quyết định
 * 2026-09-16 *"mặc định không hỏi gì"*: mọi dòng luật khác vẫn mặc định KHÔNG hỏi.
 */
class VoiceConfirmDefault286Test {

    private val sunroofId = VoiceRiskTable.PREFIX_CONTROL + "sunroof"
    private val trunkId = VoiceRiskTable.PREFIX_CONTROL + "trunk"

    @Test
    fun `tap mac dinh dung mot ma - mo cua so troi`() {
        assertEquals(setOf(sunroofId), VoiceRiskTable.defaultIds())
        assertEquals(
            listOf("sunroof"), VoiceRiskTable.CONTROL_RULES.filter { it.askByDefault }.map { it.controlId },
            "ngoại lệ có tên — dòng thứ hai bật mặc định phải có quyết định owner riêng",
        )
    }

    @Test
    fun `chua ai luu thi tap hieu luc la mac dinh - mo noc hoi, dong noc khong`() {
        val eff = VoiceRiskTable.effectiveIds(stored = null, chosenSinceDefaults = false)
        assertEquals(VoiceRisk.CONFIRM, VoiceRiskTable.of(VoiceIntent.Control("sunroof", 1), eff))
        assertEquals(VoiceRisk.NORMAL, VoiceRiskTable.of(VoiceIntent.Control("sunroof", 0), eff))
        assertEquals(VoiceRisk.NORMAL, VoiceRiskTable.of(VoiceIntent.Control("trunk", 1), eff), "cốp vẫn KHÔNG hỏi mặc định")
        assertEquals(VoiceRisk.NORMAL, VoiceRiskTable.of(VoiceIntent.Control("windows_all", 1), eff))
    }

    @Test
    fun `tap luu TRUOC 2_86 (chua co moc) duoc cong mac dinh mot lan`() {
        // Người dùng 2.85 đã tích "cốp" (hoặc bỏ tích hết) — lúc đó chưa ai được hỏi về nóc.
        assertEquals(setOf(trunkId, sunroofId), VoiceRiskTable.effectiveIds(setOf(trunkId), chosenSinceDefaults = false))
        assertEquals(setOf(sunroofId), VoiceRiskTable.effectiveIds(emptySet(), chosenSinceDefaults = false))
    }

    @Test
    fun `luu SAU 2_86 (co moc) la lua chon that - bo tich noc thi khong hoi`() {
        val eff = VoiceRiskTable.effectiveIds(setOf(trunkId), chosenSinceDefaults = true)
        assertEquals(setOf(trunkId), eff)
        assertEquals(VoiceRisk.NORMAL, VoiceRiskTable.of(VoiceIntent.Control("sunroof", 1), eff))
        assertEquals(emptySet<String>(), VoiceRiskTable.effectiveIds(emptySet(), chosenSinceDefaults = true))
    }

    @Test
    fun `ve cua luat - windows_all muc 2 (mo nua) van hoi, dong khong hoi`() {
        // `HalWriteArgs` đổi mọi primary > 0 của `windows_all` về cùng một hướng HẠ kính ⇒ mức 2 cũng phải hỏi.
        val all = setOf(VoiceRiskTable.PREFIX_CONTROL + "windows_all")
        assertEquals(VoiceRisk.CONFIRM, VoiceRiskTable.of(VoiceIntent.Control("windows_all", 2), all))
        assertEquals(VoiceRisk.NORMAL, VoiceRiskTable.of(VoiceIntent.Control("windows_all", 0), all))
        assertEquals(VoiceRisk.NORMAL, VoiceRiskTable.of(VoiceIntent.Control("trunk", 0), setOf(trunkId)), "đóng cốp: không hỏi (OQ3)")
    }

    @Test
    fun `luat matches - ba ca`() {
        val r = VoiceRiskTable.CONTROL_RULES.first { it.controlId == "sunroof" }
        assertTrue(r.matches(1)); assertTrue(r.matches(100)); assertTrue(r.matches(null))
        assertFalse(r.matches(0))
        assertTrue(r.copy(value = null).matches(0), "dòng không khai vế ⇒ mọi lệnh đều hỏi")
    }
}
