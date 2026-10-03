package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * ═══ 2.87 · R-FL2 — trí nhớ "lệnh cuối Kachi đã gửi" ([ControlLastSent]) ════════════════════════════════════════
 *
 * Khoá ba điều mà phím Đảo dựa vào:
 *  1. **Mặc định khi tiến trình bật** = tắt/đóng (0), NGOẠI TRỪ TOGGLE `onByDefault` = 1 — đúng hình mà ô đã vẽ từ
 *     trước 2.87, không thì ô và phím lệch nhau ngay lần bấm đầu. Sinh từ registry, không ghim nút cụ thể.
 *  2. **Ghi đè, không cộng dồn** — bảng mang lệnh CUỐI.
 *  3. **An toàn đa luồng** (cơ bản): ô ghi từ luồng chính, giọng nói/phím/gói lệnh từ luồng nền.
 * Phần *"chỉ ghi khi thành công"* nằm ở tầng thi hành (`VoiceControlDispatch.finish(ok)`) ⇒ khoá bằng bài chạy thật ở
 * `:app` (`KeyCtlSafetyTest`); ở `:core` có bản giả cùng luật trong `KeyCtlPlanTest`.
 */
class ControlLastSentTest {

    @Test
    fun `mac dinh tat dong, tru TOGGLE onByDefault, ma la cung 0`() {
        val m = ControlLastSent()
        ControlRegistry.ALL.forEach { d ->
            val want = if (d.kind == ControlKind.TOGGLE && d.onByDefault) 1 else 0
            assertEquals(want, m.index(d.id), "${d.id}: mặc định khi tiến trình vừa bật")
            assertEquals(want, ControlLastSent.startIndex(d.id))
        }
        assertTrue(ControlRegistry.ALL.any { it.onByDefault }, "phải còn ít nhất một nút onByDefault để bài trên có nghĩa")
        assertEquals(0, m.index("no_such_ctl"), "mã lạ ⇒ 0, không sập")
    }

    @Test
    fun `ghi de la lenh cuoi, bang rieng khong lan sang nhau`() {
        val a = ControlLastSent()
        val b = ControlLastSent()
        a.record("trunk", 1)
        assertEquals(1, a.index("trunk"))
        a.record("trunk", 0)
        assertEquals(0, a.index("trunk"), "lệnh CUỐI, không phải lệnh đầu")
        a.record("sunshade", 2)
        assertEquals(2, a.index("sunshade"))
        a.record("pm25", 0)
        assertEquals(0, a.index("pm25"), "đã gửi TẮT thì mặc định onByDefault không còn tác dụng")
        assertEquals(0, b.index("trunk"))
        assertEquals(1, b.index("pm25"), "bảng riêng của bài kiểm không chạm bảng khác")
        assertSame(ControlLastSent.shared, ControlLastSent.shared, "một bảng cho cả tiến trình")
    }

    /** Nhiều luồng ghi/đọc cùng lúc: không ngoại lệ, mỗi mã giữ một giá trị THẬT đã ghi, mã riêng không mất. */
    @Test
    fun `an toan da luong co ban`() {
        val m = ControlLastSent()
        val threads = 8
        val rounds = 2_000
        val pool = Executors.newFixedThreadPool(threads)
        val go = CountDownLatch(1)
        val errors = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        repeat(threads) { n ->
            pool.execute {
                runCatching {
                    go.await()
                    repeat(rounds) { r ->
                        m.record("shared_ctl", n)              // tranh chấp trên CÙNG một mã
                        m.record("own_$n", r)                  // mã riêng từng luồng
                        val seen = m.index("shared_ctl")
                        check(seen in 0 until threads) { "đọc ra giá trị không ai ghi: $seen" }
                    }
                }.onFailure { errors += it }
            }
        }
        go.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "luồng kiểm không xong trong 30 s")
        assertTrue(errors.isEmpty(), "ngoại lệ khi đa luồng: ${errors.firstOrNull()}")
        assertTrue(m.index("shared_ctl") in 0 until threads)
        repeat(threads) { n -> assertEquals(rounds - 1, m.index("own_$n"), "mã riêng của luồng $n phải giữ lệnh cuối") }
    }
}
