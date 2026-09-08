package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Khoá tầng KÊNH của [SourceArbiter] (B3 R6): data-channel > image. Ảnh (screen-capture) là FALLBACK — chỉ
 * lên khi kênh data của CÙNG app đã im (stale). Ngữ nghĩa STALE giữ nguyên [SourceArbiter.STALE_MS].
 */
class SourceArbiterChannelTest {

    @BeforeEach
    fun reset() = SourceArbiter.clear()

    private val WAZE = "com.waze"

    @Test
    fun `data tuoi thi CHAN anh (data thang)`() {
        assertTrue(SourceArbiter.shouldFeed(WAZE, NavSourceMode.AUTO, 1_000L, NavChannel.DATA))
        // 500 ms sau, data còn tươi → frame ảnh bị bỏ.
        assertFalse(SourceArbiter.shouldFeed(WAZE, NavSourceMode.AUTO, 1_500L, NavChannel.IMAGE))
    }

    @Test
    fun `data im (stale) thi anh duoc len`() {
        SourceArbiter.shouldFeed(WAZE, NavSourceMode.AUTO, 1_000L, NavChannel.DATA)
        // 7 s sau > STALE 6 s → data cũ → ảnh lên.
        assertTrue(SourceArbiter.shouldFeed(WAZE, NavSourceMode.AUTO, 8_000L, NavChannel.IMAGE))
        assertTrue(SourceArbiter.isFresh(8_000L))
        // ảnh giờ giữ nguồn.
        org.junit.jupiter.api.Assertions.assertEquals(WAZE, SourceArbiter.activeSource)
    }

    @Test
    fun `khong co data thi anh duoc len ngay (image-only source)`() {
        assertTrue(SourceArbiter.shouldFeed("vn.vietmap.live", NavSourceMode.AUTO, 100L, NavChannel.IMAGE))
    }

    @Test
    fun `isDataFresh — bien STALE (bao gom) roi het han`() {
        SourceArbiter.shouldFeed(WAZE, NavSourceMode.AUTO, 1_000L, NavChannel.DATA)
        assertTrue(SourceArbiter.isDataFresh(WAZE, 1_000L + SourceArbiter.STALE_MS))       // == biên: còn tươi
        assertFalse(SourceArbiter.isDataFresh(WAZE, 1_000L + SourceArbiter.STALE_MS + 1))  // qua biên: hết
    }

    /**
     * B3.50 — LỆCH ĐỒNG HỒ: mốc DATA ở TƯƠNG LAI (now < mốc, do NTP/GPS kéo đồng hồ nhảy LÙI) KHÔNG được
     * coi là tươi. Phép so cũ `now - t <= STALE_MS` với hiệu ÂM ⇒ true ⇒ chặn kênh ẢNH của chính gói đó
     * (kênh sống DUY NHẤT của VietMap sau B3.44) tới khi đồng hồ đuổi kịp. Cận dưới 0 khép cửa đó.
     */
    @Test
    fun `B3_50 — moc DATA o tuong lai (dong ho nhay lui) coi la STALE, khong chan anh oan`() {
        // Ghi mốc DATA ở t=10_000; hỏi ở now=5_000 (đồng hồ vừa lùi 5 s) ⇒ hiệu = -5_000.
        SourceArbiter.shouldFeed(WAZE, NavSourceMode.AUTO, 10_000L, NavChannel.DATA)
        assertFalse(SourceArbiter.isDataFresh(WAZE, 5_000L), "mốc tương lai ⇒ STALE, không phải 'tươi'")
        // ⇒ kênh ẢNH của chính gói đó KHÔNG bị chặn oan (data 'tươi' giả sẽ chặn nó).
        assertTrue(
            SourceArbiter.shouldFeed(WAZE, NavSourceMode.AUTO, 5_000L, NavChannel.IMAGE),
            "isDataFresh giả 'tươi' sẽ chặn ảnh; guard clock-skew phải cho ảnh lên",
        )
    }

    @Test
    fun `clear reset trang thai data (anh lai duoc len)`() {
        SourceArbiter.shouldFeed(WAZE, NavSourceMode.AUTO, 1_000L, NavChannel.DATA)
        assertFalse(SourceArbiter.shouldFeed(WAZE, NavSourceMode.AUTO, 1_200L, NavChannel.IMAGE))
        SourceArbiter.clear()
        assertFalse(SourceArbiter.isDataFresh(WAZE, 1_300L))
        assertTrue(SourceArbiter.shouldFeed(WAZE, NavSourceMode.AUTO, 1_300L, NavChannel.IMAGE))
    }

    @Test
    fun `tuong thich nguoc — goi 3 tham so van la DATA (hanh vi cu)`() {
        assertTrue(SourceArbiter.shouldFeed(WAZE, NavSourceMode.AUTO, 1_000L))   // mặc định DATA
        assertTrue(SourceArbiter.isDataFresh(WAZE, 1_050L))                      // đã ghi mốc data
    }

    @Test
    fun `image bi chan KHONG chiem khoa nguon (activeSeen giu nguyen)`() {
        SourceArbiter.shouldFeed(WAZE, NavSourceMode.AUTO, 1_000L, NavChannel.DATA)
        SourceArbiter.shouldFeed(WAZE, NavSourceMode.AUTO, 1_500L, NavChannel.IMAGE)  // bị chặn
        // data vẫn được coi tươi tính từ 1_000L, không phải 1_500L (ảnh không cập nhật mốc nguồn).
        assertTrue(SourceArbiter.isFresh(1_000L + SourceArbiter.STALE_MS))
        assertFalse(SourceArbiter.isFresh(1_000L + SourceArbiter.STALE_MS + 1))
    }
}
