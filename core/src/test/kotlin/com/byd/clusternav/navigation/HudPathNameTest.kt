package com.byd.clusternav.navigation

import com.byd.clusternav.navigation.HudPathNameGate.Channel
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * R9 (2.98) — khoá nhịp ghi tên đường lên HUD/cụm theo AmapService OEM [ĐO nguồn fw 2606 AmapService.java:120-145]:
 * chỉ real push, chỉ khi tên ĐỔI, ≤255 byte UTF-16LE nguyên văn. Mô phỏng đúng vòng `shouldSend → ghi → onResult`
 * mà `BydHalContentPush.pushPathName` / khối SDK của `BydHal.writeNavFrame` chạy.
 */
class HudPathNameTest {

    /** Mô phỏng một lần `pushPathName`: trả số lần ghi HAL thực sự (rc do [rc] quyết). */
    private fun HudPathNameGate.push(road: String, keepAlive: Boolean = false, rc: String = "0"): Int {
        var writes = 0
        for (ch in listOf(Channel.DOMESTIC, Channel.OVERSEA)) {
            if (shouldSend(ch, road, keepAlive)) { writes++; onResult(ch, road, rc) }
        }
        return writes
    }

    // Khoá: tên giống hệt hai lần ⇒ chỉ ghi lần đầu (OEM so với tên trước, Kachi cũ ghi lại mỗi khung).
    @Test fun `cung ten hai lan chi ghi mot lan`() {
        val g = HudPathNameGate()
        assertEquals(2, g.push("Nguyễn Hữu Cảnh"))
        assertEquals(0, g.push("Nguyễn Hữu Cảnh"))
    }

    // Khoá: keep-alive (~4/s) KHÔNG bao giờ ghi tên — kể cả khi tên chưa từng gửi.
    @Test fun `keep-alive khong ghi ten`() {
        val g = HudPathNameGate()
        assertEquals(0, g.push("Láng", keepAlive = true))
        assertEquals(2, g.push("Láng"))
        assertEquals(0, g.push("Láng", keepAlive = true))
    }

    // Khoá: tên đổi ⇒ ghi lại.
    @Test fun `ten doi thi ghi`() {
        val g = HudPathNameGate()
        g.push("Láng")
        assertEquals(2, g.push("Trần Duy Hưng"))
    }

    // Khoá: clearNavFrame (status=4) xả bộ nhớ ⇒ lần dẫn sau CÙNG tên vẫn gửi lại (không mất tên sau khi tắt-mở dẫn).
    @Test fun `reset sau clear thi cung ten gui lai`() {
        val g = HudPathNameGate()
        g.push("Láng")
        g.reset()
        assertEquals(2, g.push("Láng"))
    }

    // Khoá: HAL chưa nhận (rc≠0 / skip / lỗi) ⇒ KHÔNG nhớ ⇒ real push sau thử lại; dedupe RIÊNG từng kênh
    // (oversea bị từ chối không chặn domestic và ngược lại).
    @Test fun `chi nho khi rc bang 0 va theo tung kenh`() {
        val g = HudPathNameGate()
        assertTrue(g.shouldSend(Channel.DOMESTIC, "Láng", false)); g.onResult(Channel.DOMESTIC, "Láng", "0")
        assertTrue(g.shouldSend(Channel.OVERSEA, "Láng", false)); g.onResult(Channel.OVERSEA, "Láng", "skip")
        assertFalse(g.shouldSend(Channel.DOMESTIC, "Láng", false))
        assertTrue(g.shouldSend(Channel.OVERSEA, "Láng", false))
        g.onResult(Channel.SDK, "Láng", null)                       // SDK ném/skip ⇒ sdkRc null
        assertTrue(g.shouldSend(Channel.SDK, "Láng", false))
        assertFalse(HudPathName.accepted("-2147482648"))
        assertFalse(HudPathName.accepted("SecurityException: no permission"))
    }

    // Khoá: lần đầu tên rỗng ⇒ không ghi; tên rỗng SAU khi đã hiện tên ⇒ ghi chuỗi rỗng (xoá tên cũ, như "đổi").
    @Test fun `ten rong`() {
        val g = HudPathNameGate()
        assertEquals(0, g.push(""))
        g.push("Láng")
        assertEquals(2, g.push(""))
    }

    // Khoá ngân sách: tên ngắn GIỮ NGUYÊN (bỏ trần 20 code-unit cũ — OEM gửi đủ độ dài), giữ dấu tiếng Việt.
    @Test fun `ten tieng viet dai van giu nguyen trong 255 byte`() {
        val road = "Đường Nguyễn Văn Linh nối Khu đô thị Phú Mỹ Hưng – Quận 7"   // > 20 unit, có dấu
        assertTrue(road.length in 21..127)
        assertEquals(road, HudPathName.fit(road))
        assertArrayEquals(road.toByteArray(Charsets.UTF_16LE), HudPathName.encode(road))
        assertTrue(HudPathName.encode(road).size <= HudPathName.MAX_BYTES)
    }

    // Khoá trần: >255 byte ⇒ cắt còn 127 unit (254 byte), không BOM.
    @Test fun `ten qua dai cat ve 127 unit`() {
        val road = "ễ".repeat(200)
        val bytes = HudPathName.encode(road)
        assertEquals(254, bytes.size)
        assertEquals(HudPathName.MAX_UNITS, HudPathName.fit(road).length)
        assertFalse(bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())   // không BOM
    }

    // Khoá: không bao giờ tách đôi cặp surrogate ở biên cắt (nửa high surrogate cuối ⇒ bỏ cả ký tự).
    @Test fun `khong tach cap surrogate`() {
        val road = "a".repeat(126) + "🚗" + "b"     // 🚗 chiếm unit 126..127 ⇒ biên 127 rơi giữa cặp
        val out = HudPathName.fit(road)
        assertEquals(126, out.length)
        assertFalse(Character.isHighSurrogate(out.last()))
        assertTrue(HudPathName.encode(road).size <= HudPathName.MAX_BYTES)
        val whole = "a".repeat(125) + "🚗" + "b"    // cặp nằm trọn trong 127 ⇒ giữ
        assertEquals("a".repeat(125) + "🚗", HudPathName.fit(whole))
    }
}
