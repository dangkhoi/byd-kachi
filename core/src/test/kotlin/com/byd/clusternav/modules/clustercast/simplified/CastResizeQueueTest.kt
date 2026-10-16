package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 3.1 · R2/R3 — hàng GỘP lệnh −/+ khung (spec `kachi-301-cast-steps-dialog-ia.html` §4.1, §6.1 T1).
 *
 * Khoá lỗi hiện trường 13/10 (owner: *"−/+ khung chiếu cụm nhảy loạn"*): mỗi cú bấm từng là một việc riêng trên executor và một
 * `am task resize` riêng; nhấn giữ là hàng chục lệnh. Bài thuần dùng executor giả (danh sách việc, chạy tay); bài cuối dùng
 * coordinator thật với executor bị chiếm (đúng ca "đang có lệnh chạy") và đếm lệnh shell thật.
 */
class CastResizeQueueTest {

    private val full = { r: Int -> CastResizeRequest(null, CastBounds(0, 0, r, 720)) }

    private class Harness(var result: CastResizeResult = CastResizeResult.APPLIED, staleMs: Long = 15_000L) {
        val jobs = mutableListOf<Pair<String, () -> Unit>>()
        val ran = mutableListOf<CastResizeRequest>()
        val outcomes = CopyOnWriteArrayList<CastResizeOutcome>()
        var now = 0L
        var accept = true
        val queue = CastResizeQueue(
            submit = { tag, body -> if (accept) jobs += tag to body; accept },
            run = { ran += it; result },
            staleMs = staleMs,
            nowMs = { now },
        ).also { q -> q.addListener { outcomes += it } }

        fun runAll() {
            while (jobs.isNotEmpty()) jobs.removeAt(0).second()
        }
    }

    @Test
    fun `ba yeu cau khi dang ban chi xep MOT viec xa va ap khung cuoi`() {
        val h = Harness()
        h.queue.offer(full(1900))
        h.queue.offer(full(1880))
        h.queue.offer(full(1860))
        assertEquals(1, h.jobs.size, "một việc xả cho cả chuỗi bấm — không đẩy việc khác ra khỏi hàng theo từng cú")
        assertTrue(h.queue.busy(null))
        h.runAll()
        assertEquals(listOf(full(1860)), h.ran, "chỉ khung CUỐI được áp")
        assertEquals(listOf(CastResizeOutcome(full(1860), CastResizeResult.APPLIED)), h.outcomes.toList())
        assertFalse(h.queue.busy(null), "xong ⇒ rảnh, màn được đồng bộ về sự thật")
    }

    @Test
    fun `hai o khac nhau khong nuot nhau`() {
        val h = Harness()
        val left = CastResizeRequest(ClusterSlotSide.LEFT, CastBounds(0, 0, 500, 720))
        val right = CastResizeRequest(ClusterSlotSide.RIGHT, CastBounds(600, 0, 1920, 720))
        h.queue.offer(left)
        h.queue.offer(right)
        h.runAll()
        assertEquals(listOf(left, right), h.ran)
    }

    @Test
    fun `yeu cau toi khi viec xa dang chay xep dung mot viec moi`() {
        val jobs = mutableListOf<() -> Unit>()
        val ran = mutableListOf<CastResizeRequest>()
        lateinit var q: CastResizeQueue
        q = CastResizeQueue({ _, body -> jobs += body; true }, { req ->
            ran += req
            if (ran.size == 1) {
                q.offer(full(1700)); q.offer(full(1600))      // bấm tiếp trong lúc lệnh 1 đang chạy
                assertEquals(1, jobs.size, "đúng MỘT việc xả mới cho các cú bấm tới sau")
            }
            CastResizeResult.APPLIED
        }, 15_000L)
        q.offer(full(1800))
        while (jobs.isNotEmpty()) jobs.removeAt(0)()
        assertEquals(listOf(full(1800), full(1600)), ran, "lệnh đang chạy giữ khung của nó; chuỗi sau gộp về khung cuối")
        assertFalse(q.busy(null))
    }

    @Test
    fun `lenh hong bao FAILED va nha o`() {
        val h = Harness(result = CastResizeResult.FAILED)
        h.queue.offer(full(1900))
        h.runAll()
        assertEquals(CastResizeResult.FAILED, h.outcomes.single().result)
        assertFalse(h.queue.busy(null))
    }

    @Test
    fun `than ap nem thi thanh FAILED, khong nuot im`() {
        val logs = mutableListOf<String>()
        val jobs = mutableListOf<() -> Unit>()
        val outcomes = mutableListOf<CastResizeOutcome>()
        val q = CastResizeQueue({ _, b -> jobs += b; true }, { throw IllegalStateException("boom") }, 15_000L, log = { logs += it })
        q.addListener { outcomes += it }
        q.offer(full(1900))
        jobs.single()()
        assertEquals(CastResizeResult.FAILED, outcomes.single().result)
        assertTrue(logs.any { it.contains("IllegalStateException") }, "$logs")
        assertFalse(q.busy(null))
    }

    @Test
    fun `viec xa bi executor bo khoi hang thi moi yeu cau thanh DROPPED va hang khong ket`() {
        val h = Harness()
        h.queue.offer(full(1900))
        h.jobs.clear()                                   // executor vứt việc xả (DiscardOldest / Dừng dọn hàng)
        h.queue.dropped("density")                       // thẻ khác ⇒ không liên quan
        assertTrue(h.queue.busy(null))
        h.queue.dropped(CastResizeQueue.TAG)
        assertEquals(CastResizeResult.DROPPED, h.outcomes.single().result)
        assertFalse(h.queue.busy(null))
        h.queue.offer(full(1800))
        assertEquals(1, h.jobs.size, "sau khi bị bỏ, yêu cầu mới phải xếp được việc xả mới")
    }

    @Test
    fun `viec xa cho qua han coi nhu da chet va duoc xep lai`() {
        val h = Harness(staleMs = 1_000L)
        h.queue.offer(full(1900))
        h.jobs.clear()                                   // executor huỷ vì hết hạn mà onDiscard không tới — lưới staleMs
        h.now = 500L
        h.queue.offer(full(1880))
        assertEquals(0, h.jobs.size, "chưa quá hạn ⇒ vẫn tin việc xả cũ")
        h.now = 1_600L
        h.queue.offer(full(1860))
        assertEquals(1, h.jobs.size, "quá hạn ⇒ xếp lại")
        h.runAll()
        assertEquals(listOf(full(1860)), h.ran)
    }

    @Test
    fun `executor da tat thi yeu cau DROPPED`() {
        val h = Harness()
        h.accept = false
        assertFalse(h.queue.offer(full(1900)))
        assertEquals(CastResizeResult.DROPPED, h.outcomes.single().result)
        assertFalse(h.queue.busy(null))
    }
}

/**
 * Cùng hàng gộp, đi qua coordinator THẬT: executor bị chiếm (đúng ca "đang có lệnh chạy") ⇒ 3 lần `resizeActiveTarget` = MỘT
 * `am task resize` mang khung cuối, bản ghim nhận khung đó. Thử ĐỎ: trả `resizeActiveTarget` về `executor.submit("resize") {…}`
 * ⇒ 3 lệnh (hàng 1 chỗ bỏ lệnh giữa ⇒ 2) — bài đỏ.
 */
class CastResizeCoalesceCoordinatorTest : SimpleCastCoordinatorHarness() {

    @Test
    fun `ba lan bam khi executor ban ra mot lenh am task resize mang khung cuoi`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastFull("com.test.app", AppType.NORMAL))
        awaitTrue { coordinator.state is SimpleCastState.CastingFull && coordinator.executor.isIdle }
        val outcomes = CopyOnWriteArrayList<CastResizeOutcome>()
        coordinator.resizeQueue.addListener { outcomes += it }
        val gate = CountDownLatch(1)
        coordinator.executor.submit("busy") { gate.await(2, TimeUnit.SECONDS) }
        val mark = shell.history.size

        coordinator.resizeActiveTarget(0, 20, 1920, 720)
        coordinator.resizeActiveTarget(0, 40, 1920, 720)
        coordinator.resizeActiveTarget(0, 60, 1920, 720)
        gate.countDown()
        Thread.sleep(50)
        awaitTrue { coordinator.executor.isIdle }

        val resizes = shell.history.drop(mark).filter { it.startsWith("am task resize") }
        assertEquals(1, resizes.size, "một lệnh cho cả chuỗi bấm: $resizes")
        assertTrue(resizes.single().endsWith(" 0 60 1920 720"), "khung CUỐI: $resizes")
        assertEquals(CastBounds(0, 60, 1920, 720), (coordinator.state as SimpleCastState.CastingFull).pinned?.bounds)
        assertEquals(CastResizeResult.APPLIED, outcomes.single().result, "R2: màn nhận đúng MỘT kết cục cho chuỗi bấm")
    }

    /**
     * Hàng 1 chỗ + bỏ-cũ-nhất của executor cũ đã tự "gộp" chuỗi bấm của MỘT ô khi đang bận ([ĐO] bài trên vẫn ra 1 lệnh khi gỡ
     * hàng gộp — chỉ đỏ ở kết cục R2). Ca nó làm HỎNG: chia đôi, chỉnh nửa trái rồi nửa phải trong lúc executor bận ⇒ lệnh nửa
     * PHẢI đẩy lệnh nửa TRÁI khỏi hàng, nửa trái mất im lặng. Hàng gộp giữ một ô chờ cho MỖI nửa. Thử ĐỎ: gỡ hàng gộp ⇒ 1 lệnh.
     */
    @Test
    fun `chia doi - chinh hai nua khi executor ban, khong nua nao bi nuot`() {
        prefs.setSplitRatioLeftPercent(50)
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left != null && coordinator.executor.isIdle }
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.right", ClusterSlotSide.RIGHT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.right != null && coordinator.executor.isIdle }
        val gate = CountDownLatch(1)
        coordinator.executor.submit("busy") { gate.await(2, TimeUnit.SECONDS) }
        val mark = shell.history.size

        coordinator.resizeActiveSlot(ClusterSlotSide.LEFT, 0, 0, 900, 700)
        coordinator.resizeActiveSlot(ClusterSlotSide.RIGHT, 980, 0, 1920, 700)
        gate.countDown()
        Thread.sleep(50)
        awaitTrue { coordinator.executor.isIdle }

        val resizes = shell.history.drop(mark).filter { it.startsWith("am task resize") }
        assertEquals(2, resizes.size, "mỗi nửa một lệnh: $resizes")
        val split = coordinator.state as SimpleCastState.CastingSplit
        assertEquals(CastBounds(0, 0, 900, 700), split.left?.pinned?.bounds)
        assertEquals(CastBounds(980, 0, 1920, 700), split.right?.pinned?.bounds)
    }

    @Test
    fun `lenh hong bao FAILED ve man, ban ghim giu nguyen`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastFull("com.test.app", AppType.NORMAL))
        awaitTrue { coordinator.state is SimpleCastState.CastingFull && coordinator.executor.isIdle }
        val before = (coordinator.state as SimpleCastState.CastingFull).pinned
        val outcomes = CopyOnWriteArrayList<CastResizeOutcome>()
        coordinator.resizeQueue.addListener { outcomes += it }
        shell.failCommands.add("am task resize")
        shell.failCommands.add("wm size ")              // tầng 2 (Bo tròn) cũng hỏng ⇒ resizeFull = false
        coordinator.resizeActiveTarget(0, 20, 1920, 720)
        awaitTrue { outcomes.isNotEmpty() }
        assertEquals(CastResizeResult.FAILED, outcomes.single().result)
        assertEquals(before, (coordinator.state as SimpleCastState.CastingFull).pinned, "hỏng ⇒ sự thật không đổi")
    }
}
