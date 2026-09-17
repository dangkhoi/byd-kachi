package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [SlotLiveness] — luật kết luận "app trong ô đã chết" (H2·2). Khoá ba cái bẫy:
 *  • kết luận khi app **chưa kịp mở** (nhãn "app đã đóng" hiện ngay lần dùng đầu);
 *  • kết luận vì **một** nhịp hụt (shell timeout / dump cắt);
 *  • báo **nhiều lần** cho cùng một cái chết (mỗi lần báo là một lần dựng thẻ + đổi view).
 */
class SlotLivenessTest {

    @Test
    fun `chua tung thay song thi khong bao giờ ket luan chet`() {
        val l = SlotLiveness()
        repeat(50) { assertFalse(l.observe(alive = false), "app đang mở (chưa thấy task) ⇒ không được kết luận") }
    }

    @Test
    fun `mot nhip hut don le khong phai cai chet`() {
        val l = SlotLiveness()
        assertFalse(l.observe(alive = true))
        assertFalse(l.observe(alive = false), "hụt 1 nhịp ⇒ chưa kết luận")
        assertFalse(l.observe(alive = true), "sống lại ⇒ bộ đếm về 0")
        assertFalse(l.observe(alive = false), "lại hụt nhịp đầu tiên ⇒ vẫn chưa kết luận")
    }

    @Test
    fun `hut du hai nhip lien tiep thi bao chet — va chi bao DUNG MOT lan`() {
        val l = SlotLiveness()
        l.observe(alive = true)
        assertFalse(l.observe(alive = false))
        assertTrue(l.observe(alive = false), "2 nhịp × 5 s im lặng ⇒ app đã đóng")
        repeat(10) { assertFalse(l.observe(alive = false), "đã báo rồi ⇒ không báo lại") }
    }

    /**
     * Mở lại một ô = **một bản mới** của lớp này (`SlotLiveProbe.watch` dựng `Sub` mới), KHÔNG phải bản cũ được
     * bật lại. Bài này khoá đúng hành vi ấy: bản mới phải quay về luật *"chưa từng thấy sống ⇒ không kết luận"*,
     * và chu kỳ mới vẫn báo được cái chết thứ hai.
     */
    @Test
    fun `mo lai thi bat dau chu ky do moi`() {
        val first = SlotLiveness()
        first.observe(alive = true); first.observe(alive = false)
        assertTrue(first.observe(alive = false))

        val reopened = SlotLiveness()

        assertFalse(reopened.observe(alive = false))
        assertFalse(reopened.observe(alive = false), "vẫn đang mở ⇒ im lặng, không báo chết")
        assertFalse(reopened.observe(alive = true))
        assertFalse(reopened.observe(alive = false))
        assertTrue(reopened.observe(alive = false), "chu kỳ mới đủ 2 nhịp hụt ⇒ báo chết lần nữa")
    }

    @Test
    fun `nguong hut cau hinh duoc`() {
        val l = SlotLiveness(missesToDie = 3)
        l.observe(alive = true)
        assertFalse(l.observe(alive = false))
        assertFalse(l.observe(alive = false))
        assertTrue(l.observe(alive = false))
    }
}

/**
 * K8 (1.70) — lùi nhịp `am stack list` khi kết quả đứng yên; đổi là về 5 s ngay.
 * [ĐO xe 2026-09-17] 12 lệnh/phút cho cùng một câu trả lời.
 */
class SlotLivenessBackoffTest {
    @org.junit.jupiter.api.Test
    fun `lui nhip 5 - 10 - 15 s roi ket tran`() {
        org.junit.jupiter.api.Assertions.assertEquals(5_000L, SlotLiveness.probePeriodMs(0))
        org.junit.jupiter.api.Assertions.assertEquals(5_000L, SlotLiveness.probePeriodMs(1))
        org.junit.jupiter.api.Assertions.assertEquals(10_000L, SlotLiveness.probePeriodMs(2))
        org.junit.jupiter.api.Assertions.assertEquals(15_000L, SlotLiveness.probePeriodMs(3))
        org.junit.jupiter.api.Assertions.assertEquals(15_000L, SlotLiveness.probePeriodMs(50))
        org.junit.jupiter.api.Assertions.assertEquals(SlotLiveness.PROBE_PERIOD_MAX_MS, SlotLiveness.probePeriodMs(9))
    }
}
