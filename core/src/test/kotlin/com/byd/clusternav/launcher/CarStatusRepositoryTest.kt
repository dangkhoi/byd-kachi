package com.byd.clusternav.launcher

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** W1b/R8: [CarStatusRepository] 2 nhịp → StateFlow, cadence nhanh>chậm, stop huỷ sạch, degrade-safe. */
@OptIn(ExperimentalCoroutinesApi::class)
class CarStatusRepositoryTest {

    private class FakeReader : CarStatusReader {
        var fast = 0
        var slow = 0
        override fun readFast(prev: CarStatus): CarStatus {
            fast++; return prev.copy(drivetrain = prev.drivetrain.copy(speedKmh = 60))
        }
        override fun readSlow(prev: CarStatus): CarStatus {
            slow++; return prev.copy(energy = prev.energy.copy(soc = 80))
        }
    }

    @Test fun `start emits both cadences immediately into StateFlow`() = runTest {
        val repo = CarStatusRepository(FakeReader(), backgroundScope, fastMs = 100, slowMs = 1000)
        assertEquals(CarStatus(), repo.status.value)   // initial (mọi field null)
        repo.start()
        runCurrent()
        assertEquals(60, repo.status.value.drivetrain.speedKmh)
        assertEquals(80, repo.status.value.energy.soc)
    }

    @Test fun `fast cadence ticks more often than slow`() = runTest {
        val reader = FakeReader()
        val repo = CarStatusRepository(reader, backgroundScope, fastMs = 100, slowMs = 1000)
        repo.start(); runCurrent()
        advanceTimeBy(350); runCurrent()               // t=0,100,200,300 → 4 nhịp nhanh; chậm mới 1
        assertTrue(reader.fast >= 4, "fast=${reader.fast}")
        assertEquals(1, reader.slow)
    }

    @Test fun `stop cancels both poll jobs`() = runTest {
        val reader = FakeReader()
        val repo = CarStatusRepository(reader, backgroundScope, fastMs = 100, slowMs = 1000)
        repo.start(); runCurrent()
        val fastAfterStart = reader.fast
        repo.stop()
        advanceTimeBy(2000); runCurrent()
        assertEquals(fastAfterStart, reader.fast, "không tick thêm sau stop")
    }

    /**
     * H1 (PERF 2026-09-16) — khoá cổng nhịp NHANH. Bài này canh **hai** chiều, vì chỉ một chiều là chưa đủ:
     *  • màn không cần datum nhanh ⇒ KHÔNG đọc (đây là 77 % tải HAL của app);
     *  • nhu cầu đổi ⇒ vòng nhanh **tự sống lại**, không cần ai đánh thức nó (nếu không, kéo ô Tốc độ lên màn
     *    thì số đứng im vĩnh viễn — một lỗi hiệu năng biến thành lỗi chức năng).
     */
    @Test fun `nhip nhanh ngung khi man khong can, song lai khi can`() = runTest {
        val reader = object : CarStatusReader {
            var fast = 0
            var slow = 0
            var needed = false
            override fun readFast(prev: CarStatus): CarStatus { fast++; return prev }
            override fun readSlow(prev: CarStatus): CarStatus { slow++; return prev }
            override fun fastNeeded(): Boolean = needed
        }
        val repo = CarStatusRepository(reader, backgroundScope, fastMs = 100, slowMs = 1000)
        repo.start(); runCurrent()
        advanceTimeBy(3_500); runCurrent()
        assertEquals(0, reader.fast, "màn không bày datum nhanh nào mà vẫn đọc")
        assertTrue(reader.slow >= 3, "nhịp chậm phải vẫn chạy bình thường (slow=${reader.slow})")

        reader.needed = true
        advanceTimeBy(1_100); runCurrent()   // trong vòng MỘT nhịp chậm là phải tỉnh lại
        assertTrue(reader.fast > 0, "nhu cầu đổi mà vòng nhanh không tự sống lại")
        repo.stop()
    }

    /**
     * [SOÁT P1-1 · 2026-09-16] `refreshNow` là đường TƯƠI cho câu hỏi bằng giọng: nó phải đọc **ngay trên luồng
     * của chỗ gọi** (người ta đang đợi câu trả lời, nhịp chậm còn 10 s nữa) và trả về đúng ảnh vừa đọc.
     *
     * Canh cả nhánh **hỏng**: `readFast`/`readSlow` ném thì giữ ảnh cũ và KHÔNG ném ra ngoài — một câu hỏi bằng
     * giọng không được phép làm sập luồng đang xử lý câu nói.
     */
    @Test fun `refreshNow doc ngay tren luong goi va khong nem`() = runTest {
        val reader = object : CarStatusReader {
            var fast = 0
            var slow = 0
            var boom = false
            override fun readFast(prev: CarStatus): CarStatus {
                fast++; if (boom) throw RuntimeException("fast boom")
                return prev.copy(drivetrain = prev.drivetrain.copy(speedKmh = 42))
            }
            override fun readSlow(prev: CarStatus): CarStatus {
                slow++; return prev.copy(energy = prev.energy.copy(soc = 77))
            }
        }
        val repo = CarStatusRepository(reader, backgroundScope, fastMs = 1_000, slowMs = 10_000)
        // KHÔNG `start()`: chứng minh hàm này tự đọc, không phải chờ một nhịp poll nào.
        val snap = repo.refreshNow()
        assertEquals(1, reader.fast); assertEquals(1, reader.slow)
        assertEquals(42, snap.drivetrain.speedKmh, "phải trả về ảnh VỪA đọc, không phải ảnh cũ")
        assertEquals(77, snap.energy.soc)
        assertEquals(snap, repo.status.value, "ảnh trả về phải chính là ảnh đã phát ra cho màn hình")

        reader.boom = true
        val kept = repo.refreshNow()
        assertEquals(77, kept.energy.soc, "đọc hỏng ⇒ giữ ảnh cũ, không ném và không xoá trắng")
    }

    @Test fun `degrade-safe when reader throws (keeps previous state, no crash)`() = runTest {
        val throwing = object : CarStatusReader {
            override fun readFast(prev: CarStatus) = throw RuntimeException("fast boom")
            override fun readSlow(prev: CarStatus) = throw RuntimeException("slow boom")
        }
        val repo = CarStatusRepository(throwing, backgroundScope, fastMs = 100, slowMs = 1000)
        repo.start(); runCurrent()
        assertEquals(CarStatus(), repo.status.value)   // giữ nguyên default, KHÔNG crash
        repo.stop()
    }

    /**
     * 2.98 · R6-C — khoá lỗi [ĐO log xe 08/10]: màn chính RESUMED qua đêm ⇒ `HAL đọc=385/phút` ~9,6 h (pin xe). Màn TẮT ⇒ hai vòng
     * KHÔNG đọc; màn sáng lại + [CarStatusRepository.wake] ⇒ đọc NGAY (không chờ hết hạn ngủ). Thử ĐỎ: bỏ dòng `if (!awake())`.
     */
    @Test fun `R6-C man tat khong doc HAL, sang lai doc ngay`() = runTest {
        val reader = FakeReader()
        var on = false
        val repo = CarStatusRepository(reader, backgroundScope, fastMs = 1_000, slowMs = 10_000, awake = { on }, idleMs = 60_000)
        repo.start(); runCurrent()
        advanceTimeBy(30 * 60_000L); runCurrent()          // 30 phút màn tắt
        assertEquals(0, reader.fast, "màn tắt: không đọc nhanh")
        assertEquals(0, reader.slow, "màn tắt: không đọc chậm")
        on = true; repo.wake(); runCurrent()                // màn sáng
        assertTrue(reader.fast >= 1 && reader.slow >= 1, "sáng lại đọc ngay: fast=${reader.fast} slow=${reader.slow}")
    }

    /**
     * R6-C · soát Pass 4 [P2] — [CarStatusRepository.wake] rơi vào KHE giữa lúc vòng hỏi `awake()` (trả `false`, binder vài ms trên
     * `Dispatchers.IO`) và lúc vòng bắt đầu chờ ⇒ KHÔNG được mất. Mô phỏng: cổng màn trả `false` một lần rồi tự bật màn + gọi `wake()`
     * ngay trong lượt hỏi ấy. Thử ĐỎ: quay về `MutableSharedFlow(extraBufferCapacity = 1)` + `first()` ⇒ vòng nhanh ngủ oan 60 s.
     */
    @Test fun `R6-C wake roi vao khe giua hoi man va bat dau cho khong mat`() = runTest {
        val reader = FakeReader()
        var on = false
        lateinit var repo: CarStatusRepository
        repo = CarStatusRepository(reader, backgroundScope, fastMs = 1_000, slowMs = 10_000, idleMs = 60_000,
            awake = { if (on) true else { on = true; repo.wake(); false } })
        repo.start(); runCurrent()
        advanceTimeBy(1_500); runCurrent()                  // < idleMs rất xa
        assertTrue(reader.fast >= 1, "vòng nhanh phải đọc ngay, không ngủ tới idleMs: fast=${reader.fast}")
        assertTrue(reader.slow >= 1, "slow=${reader.slow}")
    }

    /** R6-C — lỡ tín hiệu màn sáng (không gọi wake) ⇒ vẫn tự đọc lại trong một hạn ngủ, không câm vĩnh viễn. */
    @Test fun `R6-C lo tin hieu man sang van tu doc lai trong mot han ngu`() = runTest {
        val reader = FakeReader()
        var on = false
        val repo = CarStatusRepository(reader, backgroundScope, fastMs = 1_000, slowMs = 10_000, awake = { on }, idleMs = 60_000)
        repo.start(); runCurrent()
        on = true
        advanceTimeBy(60_001); runCurrent()
        assertTrue(reader.slow >= 1, "slow=${reader.slow}")
    }

    /** R6-C · soát Pass 5 [P3] — màn SÁNG (xe chạy): vòng nhanh không hỏi màn mỗi giây; 60 s ≈ 6 + 6 lượt hỏi (không phải ~66). */
    @Test fun `R6-C man sang khong hoi man moi giay`() = runTest {
        val reader = FakeReader()
        var asks = 0
        val repo = CarStatusRepository(reader, backgroundScope, fastMs = 1_000, slowMs = 10_000, awake = { asks++; true }, idleMs = 60_000)
        repo.start(); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertTrue(reader.fast >= 55, "vẫn đọc nhanh mỗi giây: fast=${reader.fast}")
        assertTrue(asks <= 16, "hỏi màn quá dày: $asks")
    }

    /**
     * R6-C · soát Pass 6 — mặt trái của bộ tiết chế: màn ĐANG SÁNG (xe chạy, đọc nhanh mỗi giây) rồi TẮT giữa chừng ⇒ vòng nhanh
     * phải NGỪNG đọc trong ≤ một nhịp chậm (`recheckEvery` nhịp), không đọc suốt đêm. Thử ĐỎ: bỏ `% recheckEvery` (ticks không
     * bao giờ quay về 0 ⇒ chỉ hỏi màn đúng một lần lúc khởi) — bài `man sang khong hoi man moi giay` KHÔNG bắt được vì chỉ có trần.
     */
    @Test fun `R6-C man dang sang roi tat giua chung thi vong nhanh ngung trong mot nhip cham`() = runTest {
        val reader = FakeReader()
        var on = true
        val repo = CarStatusRepository(reader, backgroundScope, fastMs = 1_000, slowMs = 10_000, awake = { on }, idleMs = 60_000)
        repo.start(); runCurrent()
        advanceTimeBy(25_000); runCurrent()                 // 25 s xe chạy, màn sáng
        assertTrue(reader.fast >= 24, "fast=${reader.fast}")
        on = false                                          // màn tắt tại t=25 s (không có wake)
        advanceTimeBy(10_000); runCurrent()                 // ≤ slowMs sau: vòng nhanh đã tới nhịp hỏi lại và ngủ
        val fastAtOff = reader.fast
        val slowAtOff = reader.slow
        advanceTimeBy(30 * 60_000L); runCurrent()           // 30 phút màn tắt
        assertEquals(fastAtOff, reader.fast, "màn tắt mà vòng nhanh vẫn đọc")
        assertEquals(slowAtOff, reader.slow, "màn tắt mà vòng chậm vẫn đọc")
        on = true; repo.wake(); runCurrent()                // sáng lại ⇒ đọc ngay, ticks không nuốt lượt đọc
        assertTrue(reader.fast > fastAtOff && reader.slow > slowAtOff, "fast=${reader.fast} slow=${reader.slow}")
    }
}
