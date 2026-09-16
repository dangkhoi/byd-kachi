package com.byd.clusternav.launcher.voice

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ [SOÁT 2026-09-16 · P3] NHỊP ≤ 1 YÊU CẦU/GIÂY phải chịu được HAI luồng cùng lúc ══════════════════════════
 *
 * Bản trước giữ mốc trong một `@Volatile Long` rồi **đọc → ngủ → ghi**: `@Volatile` cho *thấy giá trị mới nhất*,
 * KHÔNG cho *nguyên tử*, nên hai lượt nền (một vế dẫn đường + một lượt hỏi lại, hoặc hai lượt gõ liên tiếp)
 * cùng đọc `wait <= 0` rồi cùng bắn **trong một giây**. Điều khoản Nominatim: vượt nhịp ⇒ **403 cho cả IP**, tức
 * hỏng cho **mọi lượt sau** chứ không riêng lượt vi phạm — một lỗi mà thử tay gần như không bao giờ thấy.
 *
 * Bài này chỉ chạm [VoiceGeocoder.claimSlot] (thuần, nhận đồng hồ làm tham số) ⇒ **không một byte nào ra mạng**,
 * và không bài nào phải ngủ một giây thật.
 */
class VoiceGeocoderThrottleTest {

    /** Cùng con số với `MIN_GAP_MS` (private) — điều khoản máy chủ, không phải một núm chỉnh. */
    private val gap = 1_000L

    /**
     * Mốc giả cho lượt gọi, **đúng bằng chỗ trống kế tiếp của hàng**.
     *
     * `VoiceGeocoder` là `object` ⇒ hàng chỗ dùng chung cho cả lớp bài kiểm, và một mốc "tương lai xa" đoán bừa
     * thì vẫn có thể rơi **trước** chỗ mà bài chạy trước đã giành ⇒ bài đỏ vì thứ tự chạy chứ không vì mã sai
     * (đúng lỗi bản đầu của bài này mắc phải). Nên không đoán: giành **một chỗ** ở tương lai xa rồi lấy chính chỗ
     * ấy + [gap] — sau lượt đó, trạng thái của hàng **đúng bằng** con số trả về, biết chắc chắn, mọi thứ tự chạy.
     */
    private fun freshNow(): Long = VoiceGeocoder.claimSlot(System.currentTimeMillis() + 86_400_000L) + gap

    @Test
    fun `hai luot lien tiep cach nhau dung mot giay`() {
        val now = freshNow()
        val first = VoiceGeocoder.claimSlot(now)
        val second = VoiceGeocoder.claimSlot(now)
        assertEquals(now, first, "lượt đầu sau một quãng nghỉ dài ⇒ bắn ngay, không chờ vô cớ")
        assertTrue(second - first >= gap, "lượt thứ hai phải lùi ít nhất một giây, ra: ${second - first} ms")
    }

    @Test
    fun `luot toi sau mot quang nghi dai KHONG bi no don cho`() {
        VoiceGeocoder.claimSlot(freshNow())
        val muon = freshNow()
        assertEquals(muon, VoiceGeocoder.claimSlot(muon), "nghỉ lâu rồi thì lượt sau bắn ngay, không trả nợ quá khứ")
    }

    @Test
    fun `tam luong cung gianh cho — KHONG hai luot nao roi vao cung mot giay`() {
        val threads = 8
        val now = freshNow()
        val slots = CopyOnWriteArrayList<Long>()
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) {
            Thread {
                start.await()
                slots.add(VoiceGeocoder.claimSlot(now))
                done.countDown()
            }.apply { isDaemon = true }.start()
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS), "tám luồng phải xong — treo ở đây nghĩa là vòng CAS không thoát")

        val sorted = slots.sorted()
        assertEquals(threads, sorted.toSet().size, "mỗi lượt phải giành một chỗ RIÊNG, ra: $sorted")
        sorted.zipWithNext().forEach { (a, b) ->
            assertTrue(b - a >= gap, "hai chỗ liền nhau cách ${b - a} ms — dưới một giây là đúng cái bị Nominatim chặn")
        }
    }
}
