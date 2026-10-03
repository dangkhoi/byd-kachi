package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    // ══ 2.87 · SOÁT vòng 1 · P2 — CẦU `:wake` → chính (hợp đồng thuần; dây Android ở `ControlSentRelayWiringContractTest`) ══

    /**
     * Hai "tiến trình" = hai bảng, nối đúng như bản chạy: `:wake` [ControlLastSent.forwardTo] → (broadcast) →
     * [ControlLastSent.absorb] ở tiến trình chính. Ca thật bị khoá: nói *"mở cốp"* bằng phím vô-lăng (phiên `:wake`) ⇒ bảng
     * mà ô + phím Đảo của tiến trình chính đọc PHẢI thấy "mở" ⇒ phím Đảo kế tiếp ĐÓNG (trước bản này: MỞ lần nữa).
     */
    @Test
    fun `cau wake sang chinh - moi luot ghi o wake toi bang chinh, khong vong nguoc`() {
        val wake = ControlLastSent()
        val main = ControlLastSent()
        val wire = mutableListOf<Pair<String, Int>>()
        wake.forwardTo { id, i -> wire += id to i; main.absorb(id, i) }
        wake.record("trunk", 1)                                   // "mở cốp" thành công trong `:wake`
        assertEquals(1, main.index("trunk"), "tiến trình chính thấy lệnh của `:wake`")
        assertEquals(KeyCtlPlan.Outcome.Run(com.byd.clusternav.launcher.voice.VoiceIntent.Control("trunk", 0), KeyCtlPlan.Basis.MEMORY),
            KeyCtlPlan.of(ControlRegistry.byId("trunk")!!, KeyCtlTarget("trunk", KeyCtlAction.FLIP), memory = main, speedKmh = { 0 }) { null },
            "phím Đảo ở tiến trình chính ⇒ ĐÓNG (không MỞ lần nữa)")
        wake.record("readl", 1); wake.record("seatc", 2)
        assertEquals(1, main.index("readl")); assertEquals(2, main.index("seatc"))
        // Tiến trình chính ghi (ô/phím) KHÔNG chuyển đi đâu: chỉ `:wake` nối cầu; và absorb không chuyển tiếp lần nữa.
        main.record("trunk", 0)
        assertEquals(1, wake.index("trunk"), "không có chiều ngược")
        main.forwardTo { id, i -> wire += ("main:$id") to i }
        main.absorb("trunk", 1)
        assertEquals(listOf("trunk" to 1, "readl" to 1, "seatc" to 2), wire, "absorb KHÔNG chuyển tiếp ⇒ không thể có vòng")
        wake.forwardTo(null)
        wake.record("trunk", 0)
        assertEquals(1, main.index("trunk"), "gỡ cầu ⇒ không chuyển nữa")
    }

    /** Đầu nhận kiểm hợp lệ: chỉ nút CÒN trong registry, đúng kiểu bảng mang, chỉ số không âm. */
    @Test
    fun `cau chi nhan dong hop le`() {
        val main = ControlLastSent()
        assertFalse(main.absorb(null, 1), "thiếu mã")
        assertFalse(main.absorb("no_such_ctl", 1), "mã không còn trong registry")
        assertFalse(main.absorb("fan", 3), "STEP không dùng bảng này")
        assertFalse(main.absorb("trunk", -1), "chỉ số âm = extra thiếu")
        assertEquals(0, main.index("trunk"), "dòng hỏng không đổi gì")
        assertTrue(main.absorb("sunshade", 2), "COVER mức Nửa")
        assertEquals(2, main.index("sunshade"))
        ControlRegistry.ALL.filter { it.kind == ControlKind.TOGGLE || it.kind == ControlKind.COVER || it.kind == ControlKind.SELECT }
            .forEach { assertTrue(ControlLastSent.relayable(it.id, 0), "${it.id} phải qua được cầu") }
    }

    /**
     * Soát vòng 2 [P3] — dòng từ `:wake` ĐỔI bảng ⇒ [ControlLastSent.relayed] tăng (màn chính thu nó để vẽ lại ô NGAY, không
     * chờ trạng thái xe đổi). Dòng trùng chỉ số đang nhớ (kể cả mặc định lúc bật) / hỏng / ghi trong tiến trình ([record]) ⇒
     * không tăng (không vẽ lại thừa; ô tự vẽ lượt chạm của nó).
     */
    @Test
    fun `dong tu wake doi bang thi bao ve lai, dong trung hoac hong thi khong`() {
        val main = ControlLastSent()
        assertEquals(0L, main.relayed.value)
        assertTrue(main.absorb("trunk", 0), "hợp lệ")
        assertEquals(0L, main.relayed.value, "trùng mặc định lúc bật (đóng) ⇒ hình không đổi ⇒ không vẽ lại")
        assertTrue(main.absorb("trunk", 1))
        assertEquals(1L, main.relayed.value, "mở cốp qua `:wake` ⇒ ô phải vẽ lại")
        assertTrue(main.absorb("trunk", 1))
        assertEquals(1L, main.relayed.value, "lặp lại cùng chỉ số ⇒ không")
        assertFalse(main.absorb("fan", 3))
        assertEquals(1L, main.relayed.value, "dòng hỏng ⇒ không")
        main.record("trunk", 0)
        assertEquals(1L, main.relayed.value, "ghi trong tiến trình không đi đường này")
        assertTrue(main.absorb("trunk", 1))
        assertEquals(2L, main.relayed.value, "đổi lại ⇒ báo lại")
    }
}
