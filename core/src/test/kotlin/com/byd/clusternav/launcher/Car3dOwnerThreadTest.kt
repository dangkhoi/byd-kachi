package com.byd.clusternav.launcher

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.14 PERF-SL6-3113 R3 — luồng sở hữu Filament. Khoá: (1) mọi việc chạy trên CÙNG MỘT luồng (Engine.create/shutdown + JobSystem
 * adopt đòi đúng luồng — [ĐO source v1.77.3] `Engine.cpp:606`, `JobSystem.cpp:420`); (2) lỗi của việc không giết luồng (luồng mới ⇒
 * Engine cũ mồ côi); (3) gọi lồng từ chính luồng sở hữu không tự khoá.
 */
class Car3dOwnerThreadTest {

    @Test
    fun `moi viec chay tren dung mot luong ten car3d-gl - khong phai luong goi`() {
        val gl = Car3dOwnerThread()
        val seen = HashSet<Thread>()
        val done = CountDownLatch(3)
        repeat(3) { gl.post { synchronized(seen) { seen += Thread.currentThread() }; done.countDown() } }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        val viaCall = gl.call { Thread.currentThread() }
        assertEquals(setOf(viaCall), seen, "post và call cùng một luồng")
        assertEquals(Car3dOwnerThread.NAME, viaCall.name)
        assertFalse(viaCall === Thread.currentThread(), "không chạy trên luồng gọi (luồng chính)")
        assertFalse(gl.isOwner()); assertTrue(gl.call { gl.isOwner() })
        assertThrows(IllegalStateException::class.java) { gl.check() }
        gl.call { gl.check() }
    }

    @Test
    fun `loi cua viec khong giet luong - luong sau van la luong cu`() {
        val errors = AtomicReference<Throwable?>()
        val gl = Car3dOwnerThread { errors.set(it) }
        val before = gl.call { Thread.currentThread() }
        gl.post { throw IllegalStateException("dựng hỏng") }
        val e = assertThrows(IllegalArgumentException::class.java) { gl.call<Unit> { throw IllegalArgumentException("truy vấn hỏng") } }
        assertEquals("truy vấn hỏng", e.message, "call ném lại NGUYÊN lỗi (bên gọi phân loại FAULT)")
        assertSame(before, gl.call { Thread.currentThread() }, "luồng sống tiếp — Engine trên đó vẫn huỷ được")
        assertEquals("dựng hỏng", errors.get()?.message, "lỗi việc post đi vào onError")
    }

    @Test
    fun `goi long tu luong so huu chay ngay - khong tu khoa`() {
        val gl = Car3dOwnerThread()
        assertEquals(42, gl.call { gl.call { 42 } })
    }
}
