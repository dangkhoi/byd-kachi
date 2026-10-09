package com.byd.clusternav.launcher.escape

import com.byd.clusternav.launcher.escape.EscapeReport.Scope
import com.byd.clusternav.system.inputd.InputWireProtocol
import com.byd.clusternav.system.inputd.TouchFrame
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * 2.98 · R18 — dây Kachi ↔ daemon. Khoá: bảng khứ hồi đúng; dữ liệu lạ từ kênh bị bỏ (gói sai dạng, màn ảo < 1, api lạ — daemon
 * KHÔNG phản chiếu tên hàm đọc từ mạng); dòng báo cáo khứ hồi; khung điều khiển không lẫn khung chạm.
 */
class EscapeReturnWireTest {

    @Test
    fun `bang khu hoi`() {
        val c = EscapeReturnConfig(EscapeReturnApi.ANDROID_10_R47, mapOf(13 to "com.waze", 12 to "vn.vietmap.live"))
        val text = EscapeReturnWire.encodeConfig(c)
        assertEquals("kachi-esc 1\napi a10r47\nvd 12 vn.vietmap.live\nvd 13 com.waze\n", text)
        assertEquals(c, EscapeReturnWire.decodeConfig(text))
        assertEquals(EscapeReturnConfig.OFF, EscapeReturnWire.decodeConfig(EscapeReturnWire.encodeConfig(EscapeReturnConfig.OFF)))
    }

    @Test
    fun `du lieu la tu kenh bi bo`() {
        assertNull(EscapeReturnWire.decodeConfig("api a10r47\nvd 13 com.waze"), "thiếu đầu")
        val c = EscapeReturnWire.decodeConfig("kachi-esc 1\napi getAllStackInfos;rm\nvd 0 com.waze\nvd -3 a.b\nvd 13 com.waze;reboot\nvd x a.b\nvd 14 a.b c\nvd 15 vn.vietmap.live")!!
        assertNull(c.api, "api chỉ tra theo id đã biết")
        assertEquals(mapOf(15 to "vn.vietmap.live"), c.slots)
        val many = "kachi-esc 1\n" + (1..40).joinToString("\n") { "vd $it p$it.app" }
        assertEquals(EscapeReturnWire.MAX_SLOTS, EscapeReturnWire.decodeConfig(many)!!.slots.size)
    }

    @Test
    fun `bao cao khu hoi`() {
        val all = listOf(
            EscapeReport.Ready("a10r47"),
            EscapeReport.Off("client-gone"),
            EscapeReport.Moved("com.waze", 432, 38, 13, 13),
            EscapeReport.Skipped("com.google.android.gm", 440, 13, "NOT_SLOT_PKG"),
            EscapeReport.Skipped(null, 440, 13, "NOT_SLOT_PKG"),
            EscapeReport.Tripped(Scope.PKG, "com.waze", "rate>=10/60s"),
            EscapeReport.Tripped(Scope.PERSIST, "com.waze", "NullPointerException:Attempt_to_invoke"),
            EscapeReport.Tripped(Scope.ALL, null, "register:SecurityException"),
        )
        for (r in all) assertEquals(r, EscapeReturnWire.parseReport(EscapeReturnWire.encodeReport(r)), "$r")
        assertEquals("esc trip persist com.waze NullPointerException:Attempt_to_invoke_virtual",
            EscapeReturnWire.encodeReport(EscapeReport.Tripped(Scope.PERSIST, "com.waze", "NullPointerException:Attempt to invoke virtual")))
        assertNull(EscapeReturnWire.parseReport("[Kachi/InputDaemon] start tcp"))
        assertNull(EscapeReturnWire.parseReport("esc moved com.waze x 38 13 1"))
    }

    @Test
    fun `khung dieu khien khong lan khung cham`() {
        val body = EscapeReturnWire.encodeConfig(EscapeReturnConfig(EscapeReturnApi.ANDROID_10_R47, mapOf(13 to "com.waze"))).toByteArray()
        val frame = InputWireProtocol.controlFrame(body)
        val header = frame.copyOfRange(0, InputWireProtocol.FRAME_BYTES)
        assertNull(InputWireProtocol.decode(header), "đầu khung điều khiển không phải khung chạm")
        assertEquals(body.size, InputWireProtocol.controlLength(header))
        assertArrayEquals(body, frame.copyOfRange(InputWireProtocol.FRAME_BYTES, frame.size))
        assertNull(InputWireProtocol.controlLength(InputWireProtocol.encode(TouchFrame(13, 0, 1, 2))), "khung chạm không phải khung điều khiển")
        val tooBig = header.copyOf().also { it[2] = 0x7f }
        assertNull(InputWireProtocol.controlLength(tooBig), "độ dài vượt trần ⇒ rác")
        assertThrows<IllegalArgumentException> { InputWireProtocol.controlFrame(ByteArray(InputWireProtocol.MAX_CONTROL_BYTES + 1)) }
        assertTrue(EscapeReturnApi.byId("a10r47") === EscapeReturnApi.ANDROID_10_R47)
        assertNull(EscapeReturnApi.byId("a12"), "A12 chưa đo ⇒ không có bảng")
    }
}
