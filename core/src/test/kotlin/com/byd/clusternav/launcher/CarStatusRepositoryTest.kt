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
}
