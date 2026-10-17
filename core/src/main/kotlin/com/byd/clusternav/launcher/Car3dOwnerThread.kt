package com.byd.clusternav.launcher

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask

/**
 * ═══ 3.1.14 `PERF-SL6-3113` R3 — MỘT luồng sở hữu Filament (Engine · gltfio) cho widget xe 3D ═══════════════════════════════════
 *
 * [ĐO log Seal 17/10, 3.1.11] mỗi lượt dựng 3D = `Engine.create` + gltfio `createAsset`/`loadResources` (GLB 29,5 MB) 788–1509 ms TRÊN
 * LUỒNG CHÍNH ⇒ bỏ 62–139 khung. [ĐO máy ảo 2 lõi 17/10] `createAsset` ≈ ⅓ lượt dựng ⇒ chia nhỏ theo khung trên luồng chính không đủ
 * (một lời gọi native không chia được) ⇒ dời CẢ Engine sang một luồng riêng.
 *
 * Luật luồng của Filament [ĐO source v1.77.3]: luồng gọi `Engine.create` thành `mMainThreadId` + được `JobSystem.adopt()`
 * (`filament/src/details/Engine.cpp:307,341`); `Engine::shutdown` kiểm đúng luồng đó (`:606`); `loadResources` → `computeTangents` →
 * `JobSystem::runAndWait` ném "This thread has not been adopted" ở luồng lạ (`libs/utils/src/JobSystem.cpp:420`,
 * `libs/gltfio/src/ResourceLoader.cpp:1124`); shutdown `emancipate()` (`Engine.cpp:756`) + `ThreadMap` theo từng JobSystem ⇒ một
 * luồng sống lâu dựng/huỷ được nhiều Engine nối tiếp. Vì thế MỌI lời gọi Filament đi qua ĐÚNG MỘT luồng này: dựng/huỷ [post]
 * (luồng chính không chờ), truy vấn/vẽ sau khi dựng xong [call] (luồng chính chờ — luồng riêng đang rảnh nên chỉ tốn một bước nhảy).
 *
 * Luồng KHÔNG BAO GIỜ chết vì lỗi của việc: [post] bắt mọi `Throwable` → [onError]; [call] trả lỗi về cho bên gọi (luồng chết giữa
 * chừng ⇒ executor đẻ luồng mới ⇒ Engine cũ mồ côi, không huỷ được). Thuần JVM — `Car3dOwnerThreadTest`.
 */
class Car3dOwnerThread(name: String = NAME, private val onError: (Throwable) -> Unit = {}) {

    @Volatile private var owner: Thread? = null
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, name).apply { isDaemon = true; owner = this } }

    /** Đang chạy trên luồng sở hữu. */
    fun isOwner(): Boolean = Thread.currentThread() === owner

    /** Ném nếu không ở luồng sở hữu — chặn cứng ở tầng thi hành (gọi Filament từ luồng lạ = sai im lặng hoặc abort native). */
    fun check() = check(isOwner()) { "Filament phải chạy trên luồng ${NAME}, đang ở ${Thread.currentThread().name}" }

    /** Việc không chờ (dựng · huỷ). Lỗi ⇒ [onError], luồng sống tiếp. */
    fun post(block: () -> Unit) {
        exec.execute { try { block() } catch (t: Throwable) { onError(t) } }
    }

    /** Chạy [block] trên luồng sở hữu và chờ kết quả; đang ở luồng sở hữu ⇒ chạy ngay (không tự khoá). Lỗi của [block] ném lại nguyên dạng. */
    fun <T> call(block: () -> T): T {
        if (isOwner()) return block()
        val task = FutureTask(block)
        exec.execute(task)
        return try { task.get() } catch (e: ExecutionException) { throw e.cause ?: e }
    }

    companion object {
        const val NAME = "car3d-gl"
    }
}
