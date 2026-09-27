package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VOICE-WRITE-LANE · ba bất biến của làn ghi (spec kachi-276-closing R5) ═════════════════════════════════════
 *
 * Bài THUẦN cho [VoiceWriteLane]; phần *"câu ghép thật đi qua VoiceDispatcher"* ở `:app`
 * (`VoiceWriteLaneDispatchTest`, HAL giả ghi thứ tự + mốc giờ). Ở đây chỉ đo cơ chế: FIFO theo lời gọi lại ·
 * `done` một lần · việc ném không ghim làn.
 */
class VoiceWriteLaneTest {

    @Test
    fun `viec sau chi bat dau khi viec truoc goi done, ke ca khi done den muon`() {
        val lane = VoiceWriteLane()
        val log = ArrayList<String>()
        var release: (() -> Unit)? = null
        lane.submit { done -> log += "A:start"; release = done }      // A "bất đồng bộ": chưa xong
        lane.submit { done -> log += "B:start"; done() }
        assertEquals(listOf("A:start"), log, "B không được chạy khi A chưa báo xong — đó chính là lỗi VOICE-WRITE-LANE")
        assertFalse(lane.idle)
        release!!.invoke()
        assertEquals(listOf("A:start", "B:start"), log)
        assertTrue(lane.idle)
    }

    @Test
    fun `viec dong bo chay ngay tren luong goi - duong don menh de y nguyen`() {
        val lane = VoiceWriteLane()
        var ran = false
        lane.submit { done -> ran = true; done() }
        assertTrue(ran, "làn rỗi ⇒ chạy ngay, không xếp hàng, không luồng")
        assertTrue(lane.idle)
    }

    @Test
    fun `done goi hai lan chi tinh mot`() {
        val lane = VoiceWriteLane()
        val log = ArrayList<String>()
        var releaseA: (() -> Unit)? = null
        lane.submit { done -> log += "A"; releaseA = done }
        lane.submit { done -> log += "B"; /* B chưa xong */ }
        lane.submit { done -> log += "C"; done() }
        releaseA!!.invoke()
        assertEquals(listOf("A", "B"), log, "A xong ⇒ B chạy; C phải chờ B")
        releaseA!!.invoke()                                            // gọi lại `done` của A
        assertEquals(listOf("A", "B"), log, "`done` của A lần hai không được thả làn cho C trong khi B còn dở")
    }

    @Test
    fun `viec nem truoc khi done thi lan tu tha, khong ghim ve sau`() {
        val lane = VoiceWriteLane()
        val log = ArrayList<String>()
        assertThrows(IllegalStateException::class.java) { lane.submit { _ -> error("HAL nổ") } }
        lane.submit { done -> log += "B"; done() }
        assertEquals(listOf("B"), log, "một vế ném không được biến mọi vế sau thành im lặng")
        assertTrue(lane.idle)
    }

    @Test
    fun `nop tu trong done van giu thu tu`() {
        val lane = VoiceWriteLane()
        val log = ArrayList<String>()
        lane.submit { done ->
            log += "A"
            lane.submit { d2 -> log += "A.next"; d2() }   // hình dạng `runFrom` gọi tiếp từ callback
            done()
        }
        assertEquals(listOf("A", "A.next"), log)
        assertTrue(lane.idle)
    }
}
