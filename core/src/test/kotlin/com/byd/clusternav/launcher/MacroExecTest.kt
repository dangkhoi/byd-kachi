package com.byd.clusternav.launcher

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ [SOÁT 2026-09-16 · P3] CHỖ CHẠY GÓI LỆNH — daemon, có trần, và KHÔNG BAO GIỜ từ chối ════════════════════
 *
 * Trước bản này mỗi cú chạm ô gói lệnh dựng thẳng một `Thread(…).start()`: luồng **không phải daemon** (một gói
 * đang ngủ giữa hai bước giữ tiến trình sống sau khi launcher đã dọn) và **không có trần** (`beginRun` chỉ chặn
 * CÙNG một mã gói chạy chồng; hai gói khác nhau thì không gì chặn).
 *
 * Ba tính chất bài này khoá, và mỗi cái chặn một kiểu hỏng khác nhau:
 *  1. **daemon** — tiến trình không bị một gói đang ngủ giữ lại;
 *  2. **có trần** — một chuỗi chạm không dựng được một luồng mới mỗi lần;
 *  3. **không từ chối lượt nào** — lượt bị từ chối thì `state.endRun` trong `finally` của nó không chạy ⇒ cờ
 *     chống-bấm-kép kẹt `true` ⇒ **ô chết hẳn**. Xếp hàng thì chậm; từ chối thì hỏng vĩnh viễn.
 */
class MacroExecTest {

    @Test
    fun `chay tren luong daemon, mang ten goi, va tra ten lai sau khi xong`() {
        val done = CountDownLatch(1)
        val daemon = AtomicBoolean(false)
        val name = AtomicReference("")
        val thread = AtomicReference<Thread?>(null)
        MacroExec.submit("roi_xe") {
            val t = Thread.currentThread()
            thread.set(t)
            daemon.set(t.isDaemon)
            name.set(t.name)
            done.countDown()
        }
        assertTrue(done.await(5, TimeUnit.SECONDS), "gói phải được chạy, không bị nuốt")
        assertTrue(daemon.get(), "luồng chạy gói PHẢI là daemon — luồng thường giữ tiến trình sống qua cả lúc dọn")
        assertEquals("macro-roi_xe", name.get(), "nhật ký sự cố phải đọc ra được gói nào đang chạy")
        // Tên trả lại sau khi xong: luồng dùng chung, mang tên gói cũ là nói dối ở lượt `dumpsys` kế tiếp. Chờ có
        // hạn chứ không khẳng định ngay — thân gói đếm xuống chốt TRƯỚC khi khối `finally` đổi tên lại.
        val t = requireNotNull(thread.get())
        val han = System.currentTimeMillis() + 5_000
        while (t.name.startsWith("macro-") && System.currentTimeMillis() < han) Thread.sleep(10)
        assertTrue(!t.name.startsWith("macro-"), "tên phải được trả lại sau lượt chạy, đang là \"${t.name}\"")
    }

    @Test
    fun `nhieu goi cung luc thi XEP HANG — co tran luong, va khong luot nao bi tu choi`() {
        val n = 12
        val done = CountDownLatch(n)
        val live = AtomicInteger(0)
        val peak = AtomicInteger(0)
        // Gom theo ĐỊNH DANH luồng, không theo tên: tên đổi theo từng gói (xem bài trên) nên đếm tên là đếm gói.
        val threads = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
        repeat(n) { i ->
            MacroExec.submit("goi$i") {
                val now = live.incrementAndGet()
                peak.updateAndGet { maxOf(it, now) }
                threads.add(Thread.currentThread().id)   // `threadId()` chỉ có từ Java 19; dự án ở JDK 17
                Thread.sleep(20)
                live.decrementAndGet()
                done.countDown()
            }
        }
        assertTrue(done.await(30, TimeUnit.SECONDS), "MỌI lượt phải chạy — một lượt bị từ chối là một ô chết hẳn")
        assertTrue(peak.get() in 1..2, "trần luồng là 2, đo được ${peak.get()} — một luồng mới mỗi cú chạm là bệnh cũ")
        assertTrue(threads.size <= 2, "$n lượt chỉ được dùng tối đa 2 luồng, dùng ${threads.size}")
    }

    /**
     * ═══ P1-main (2026-09-25) · LÀN TUẦN TỰ cho ô đơn — cùng pool, KHÔNG song song, KHÔNG đổi thứ tự ════════════
     * Pool có 2 luồng: nộp thẳng `pool.execute` thì hai cú bấm liên tiếp cùng một nút có thể chạy chéo ⇒ HAL nhận
     * `tắt` trước `bật`. Bài này nộp 40 lượt có ngủ ngẫu nhiên vào MỘT làn, một lượt giữa chừng ném: thứ tự phải y
     * nguyên, đỉnh song song = 1, và lượt ném không nuốt lượt kế.
     */
    @Test
    fun `submitSerial — cung mot lan thi dung thu tu, khong song song, luot nem khong chan luot ke`() {
        val n = 40
        val done = CountDownLatch(n)
        val order = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val live = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val rnd = java.util.Random(7)
        repeat(n) { i ->
            val nap = rnd.nextInt(4).toLong()
            MacroExec.submitSerial("lan-test") {
                val now = live.incrementAndGet()
                peak.updateAndGet { maxOf(it, now) }
                try {
                    Thread.sleep(nap)
                    order += i
                    done.countDown()
                    if (i == 3) throw IllegalStateException("lượt $i ném — lượt sau vẫn phải chạy")
                } finally {
                    live.decrementAndGet()
                }
            }
        }
        assertTrue(done.await(30, TimeUnit.SECONDS), "MỌI lượt phải chạy — lượt ném không được nuốt lượt kế")
        assertEquals((0 until n).toList(), order.toList(), "thứ tự nộp phải = thứ tự chạy trong một làn")
        assertEquals(1, peak.get(), "trong MỘT làn không bao giờ có hai lượt chạy song song (đo được ${peak.get()})")
    }

    /**
     * [SOÁT Pass 1 · 2026-09-25 · P2] Lượt DỌN đuôi làn phải đăng ký **ngoài** `lanes.compute { … }`.
     *
     * [ĐO JDK 17] `body` xong trước khi luồng gọi kịp `whenComplete` (ghi HAL off-car trả `false` tức thì) ⇒
     * hành động chạy **ngay trên luồng gọi**; nếu lúc đó còn ở trong `compute` thì `lanes.remove` là *sửa map đang
     * compute* ⇒ `IllegalStateException: Recursive update` (`ConcurrentHashMap.replaceNode:1167`) — bị
     * `CompletableFuture` nuốt vào future dẫn xuất không ai đọc, nên đuôi làn không bao giờ được dọn và lỗi chìm
     * hẳn. Hành vi ấy phụ thuộc lịch luồng nên không khoá được bằng phép đo; khoá bằng CẤU TRÚC nguồn (CLAUDE.md §8).
     */
    @Test
    fun `don duoi lan dang ky NGOAI compute (khong sua map trong compute)`() {
        val src = com.byd.clusternav.testsupport.SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/MacroExec.kt")
        val fn = com.byd.clusternav.testsupport.SourceRoots.body(src, "fun submitSerial(")
        val compute = com.byd.clusternav.testsupport.SourceRoots.body(fn, "lanes.compute(lane) { _, prev ->")
        assertTrue(fn.contains("lanes.remove(lane, next)"), "vẫn phải dọn đuôi làn khi nó chạy xong")
        assertTrue(
            !compute.contains("lanes.remove") && !compute.contains("whenComplete"),
            "KHÔNG được sửa `lanes` (remove/whenComplete) bên trong lambda của `lanes.compute` — ConcurrentHashMap cấm",
        )
    }

    /** Làn tuần tự dùng CHUNG pool với gói lệnh: luồng daemon, tên = tên làn, trần vẫn 2. */
    @Test
    fun `submitSerial chay tren luong daemon cua pool, mang ten lan`() {
        val done = CountDownLatch(1)
        val daemon = AtomicBoolean(false)
        val name = AtomicReference("")
        MacroExec.submitSerial("lan-x") { daemon.set(Thread.currentThread().isDaemon); name.set(Thread.currentThread().name); done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertTrue(daemon.get(), "làn ô đơn không được giữ tiến trình sống — daemon như gói lệnh")
        assertEquals("lan-x", name.get())
    }
}
