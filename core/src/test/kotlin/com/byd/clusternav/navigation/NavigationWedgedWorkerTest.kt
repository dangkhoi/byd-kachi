package com.byd.clusternav.navigation

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ca đã làm treo Cluster Cast, đem sang kiểm đường ra Navigation.
 *
 * Sự cố 2026-07-26: `ThreadPoolExecutor` cỡ cố định + `task.get(timeout)` + `cancel(true)` KHÔNG ngắt được
 * I/O nghẽn; hai worker kẹt là hàng đầy và mọi việc sau đó bị từ chối vĩnh viễn, không có đường ra.
 *
 * Đường ra Navigation khác ở hai điểm quan trọng và bài kiểm này ghim lại: `submit` KHÔNG chặn luồng gọi,
 * và hàng đầy thì báo FAULT có lý do đọc được (nên `RETRY_*` được cấp). Nhưng nó vẫn còn một giới hạn thật:
 * luồng giao duy nhất mà kẹt ở I/O bỏ qua interrupt thì **thử lại không thể thành công** — cần biết rõ để
 * không hứa với người dùng điều làm không được.
 */
class NavigationWedgedWorkerTest {

    private fun frame(sequence: Long) = NavigationFrame(
        "s-1", NavigationSourceIdentity("com.example.maps"), sequence, 1_000L,
        NavigationFrameContent(1, "Rẽ phải", 100, "Đường Ví Dụ", null, null, null, null),
    )

    @Test
    fun `luong giao ket khong lam dong bang luong goi`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val lane = ClusterLaneAdapter(
            NavigationFrameDelivery {
                entered.countDown()
                // Bỏ qua interrupt, đúng như socket/Binder nghẽn trên xe.
                while (release.count > 0L) runCatching { Thread.sleep(5) }
            },
            OutputAdapterConfig(queueCapacity = 2, deliveryDeadlineMs = 50),
            initiallyEnabled = true,
        )
        try {
            assertEquals(OutputSubmission.ACCEPTED, lane.submit(frame(1)))
            assertTrue(entered.await(1, TimeUnit.SECONDS), "chưa vào được phần giao")

            // Điểm sống còn: luồng gọi vẫn trả về ngay, không chờ luồng đang kẹt.
            val startedAt = System.nanoTime()
            lane.submit(frame(2))
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            assertTrue(elapsedMs < 500, "submit chặn ${elapsedMs} ms — luồng vẽ sẽ đóng băng")
        } finally {
            release.countDown()
            lane.close()
        }
    }

    @Test
    fun `hang day thi bao loi co ly do doc duoc, khong im lang`() {
        val release = CountDownLatch(1)
        val lane = ClusterLaneAdapter(
            NavigationFrameDelivery { while (release.count > 0L) runCatching { Thread.sleep(5) } },
            OutputAdapterConfig(queueCapacity = 1, deliveryDeadlineMs = 50),
            initiallyEnabled = true,
        )
        try {
            // 1 đang chạy, 1 nằm hàng, các frame sau bị từ chối.
            lane.submit(frame(1))
            lane.submit(frame(2))
            val rejected = (3..6).map { lane.submit(frame(it.toLong())) }
            assertTrue(
                rejected.any { it == OutputSubmission.REJECTED_QUEUE_FULL },
                "hàng đầy mà không báo gì: $rejected",
            )
            val status = lane.health().status
            assertTrue(
                status is NavigationOutputStatus.FAULT,
                "phải thành FAULT để UI cấp được RETRY, đang là $status",
            )
            assertEquals(
                NavigationOutputFailureReason.QUEUE_SATURATED,
                (status as NavigationOutputStatus.FAULT).reason,
                "lý do phải nói đúng chuyện gì xảy ra",
            )
        } finally {
            release.countDown()
            lane.close()
        }
    }

    @Test
    fun `dong adapter khong treo du luong giao dang ket`() {
        // Nếu close() chờ luồng kẹt thì tắt tính năng cũng đóng băng — đúng kiểu ngõ cụt cần tránh.
        val release = CountDownLatch(1)
        val lane = ClusterLaneAdapter(
            NavigationFrameDelivery { while (release.count > 0L) runCatching { Thread.sleep(5) } },
            OutputAdapterConfig(queueCapacity = 1, deliveryDeadlineMs = 50),
            initiallyEnabled = true,
        )
        lane.submit(frame(1))
        val startedAt = System.nanoTime()
        lane.close()
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        release.countDown()
        assertTrue(elapsedMs < 1_000, "close() mất ${elapsedMs} ms trong khi luồng giao đang kẹt")
    }
}
