package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ 2.96 · R5 — lượt dò NỀN không bao giờ đẩy lệnh người dùng/tự chiếu khỏi hàng; mọi lần bỏ đều có dấu ═══
 *
 * [ĐO log xe 07/10 20:48:55] tự chiếu chia đôi: nửa TRÁI VERIFIED, `split right: vn.vietmap.live` được gửi khi nửa TRÁI còn chạy
 * (chờ trong hàng 1 chỗ), rồi nhịp watchdog repin gửi `submit` thường ⇒ `DiscardOldestPolicy` vứt nửa PHẢI IM LẶNG — 0 lệnh
 * `am start` cho VietMap. Bài ở đây khoá tầng executor bằng latch (tất định, không ngủ đoán).
 */
class BoundedCastExecutorBackgroundTest {

    /** Thử ĐỎ: `submitIfIdle` gọi thẳng `submit` (không kiểm rảnh) ⇒ "right" bị bỏ, "repin" chạy thay. */
    @Test
    fun `submitIfIdle khi co thao tac dang chay va dang cho - khong xep, thao tac cho van chay`() {
        val discards = CopyOnWriteArrayList<String>()
        val ex = BoundedCastExecutor(castTimeoutMs = 30_000L, onDiscard = { tag, _ -> discards += tag })
        val leftStarted = CountDownLatch(1)
        val releaseLeft = CountDownLatch(1)
        val rightRan = CountDownLatch(1)
        val repinRan = AtomicBoolean(false)
        ex.submit("cast-left") { leftStarted.countDown(); releaseLeft.await(5, SECONDS) }
        assertTrue(leftStarted.await(5, SECONDS))
        ex.submit("cast-right") { rightRan.countDown() }               // chờ sau nửa TRÁI
        assertFalse(ex.submitIfIdle("repin-watchdog") { repinRan.set(true) }, "bận ⇒ lượt nền không vào hàng")
        releaseLeft.countDown()
        assertTrue(rightRan.await(5, SECONDS), "lệnh chiếu nửa PHẢI phải chạy")
        assertFalse(repinRan.get())
        assertEquals(emptyList<String>(), discards, "không thao tác nào bị bỏ")
        ex.shutdown()
    }

    @Test
    fun `submitIfIdle khi chi co thao tac dang chay (hang trong) - van khong xep`() {
        val ex = BoundedCastExecutor(castTimeoutMs = 30_000L)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        ex.submit("cast-left") { started.countDown(); release.await(5, SECONDS) }
        assertTrue(started.await(5, SECONDS))
        assertFalse(ex.submitIfIdle("repin-watchdog") { }, "đang chạy ⇒ không xếp (lệnh người dùng tới sau không phải tranh chỗ)")
        release.countDown()
        ex.shutdown()
    }

    @Test
    fun `submitIfIdle khi ranh - chay`() {
        val ex = BoundedCastExecutor(castTimeoutMs = 30_000L)
        val ran = CountDownLatch(1)
        assertTrue(ex.submitIfIdle("repin-watchdog") { ran.countDown() })
        assertTrue(ran.await(5, SECONDS))
        ex.shutdown()
    }

    /** Thử ĐỎ: trả lại `ThreadPoolExecutor.DiscardOldestPolicy()` ⇒ không có dòng nào. */
    @Test
    fun `thao tac cho bi day khoi hang - bao tag va ly do`() {
        val discards = CopyOnWriteArrayList<String>()
        val ex = BoundedCastExecutor(castTimeoutMs = 30_000L, onDiscard = { tag, why -> discards += "$tag|$why" })
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cRan = CountDownLatch(1)
        ex.submit("A") { started.countDown(); release.await(5, SECONDS) }
        assertTrue(started.await(5, SECONDS))
        ex.submit("B") { }
        ex.submit("C") { cRan.countDown() }                            // ý định mới nhất thắng — hành vi cũ giữ nguyên
        release.countDown()
        assertTrue(cRan.await(5, SECONDS))
        assertEquals(1, discards.size, "$discards")
        assertTrue(discards[0].startsWith("B|") && discards[0].contains("C"), "nói rõ CÁI GÌ bị bỏ và vì ai: $discards")
        ex.shutdown()
    }

    /** Dừng vẫn ưu tiên (dọn hàng) — nhưng nay có dấu cho từng thao tác bị dọn. */
    @Test
    fun `Dung don hang - bao tung thao tac bi don, Dung van chay`() {
        val discards = CopyOnWriteArrayList<String>()
        val ex = BoundedCastExecutor(castTimeoutMs = 30_000L, onDiscard = { tag, why -> discards += "$tag|$why" })
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        ex.submit("A") {
            started.countDown()
            try { Thread.sleep(20_000L) } catch (e: InterruptedException) { throw e }
        }
        assertTrue(started.await(5, SECONDS))
        ex.submit("cast-right") { }
        ex.submitStop("stop") { stopped.countDown() }
        assertTrue(stopped.await(5, SECONDS))
        assertEquals(1, discards.size, "$discards")
        assertTrue(discards[0].startsWith("cast-right|") && discards[0].contains("stop"), "$discards")
        ex.shutdown()
    }
}
