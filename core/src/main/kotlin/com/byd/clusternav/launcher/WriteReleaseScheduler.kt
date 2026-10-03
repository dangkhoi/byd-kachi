package com.byd.clusternav.launcher

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * FIX286 · SR2 — lệnh **nhả** của một nút ([ControlDef.release]): sau một lệnh ghi có rc hợp lệ, chờ [delayMs] rồi
 * gửi [value] qua **cùng** đường ghi (cùng method / feature) của nút đó.
 *
 * Nguồn của khuôn này (dòng duy nhất khai nó hôm nay là `sunroof`): [ĐO nguồn OEM] `SunRoofFragment.java:834-836`
 * (`removeMessages(0)` → `setSunRoofState(…)` → `sendEmptyMessageDelayed(0, 200)`) và `:1109` (`setSunRoofState(255)`).
 */
data class WriteRelease(val value: Int, val delayMs: Long)

/**
 * ═══ FIX286 · SR2 — BỘ HẸN CÓ KHOÁ cho lệnh nhả (và lượt đọc-sau của nhật ký `ctl`) ═══════════════════════════
 *
 * Hợp đồng: một [schedule] với khoá K **thay** việc đang chờ của K (việc cũ không bao giờ chạy); [cancel] huỷ việc
 * đang chờ của K và nói thật nó có huỷ được gì không. Đó đúng hai động tác của app OEM — `sendEmptyMessageDelayed`
 * và `removeMessages` trên một `Handler` — nhưng là một **giao diện tiêm vào** để `HalBindingTable` (`:core` thuần)
 * kiểm được off-car bằng đồng hồ giả, không ngủ thật.
 *
 * ## Vì sao mặc định là [NONE] chứ không phải bản thật
 * `HalBindingTable` được dựng ở nhiều bài kiểm với gateway giả trả rc hợp lệ; nếu mặc định là một luồng thật thì
 * mọi bài ghi `sunroof` sẽ có thêm một lượt `namedInt(255)` chạy **lệch giờ** trên luồng khác ⇒ bài kiểm đếm lượt
 * gọi thành ngẫu nhiên. Chỉ đồ thị tiến trình (`AppContainer`) tiêm [Jvm] — và bài canh nối dây khoá chỗ tiêm đó
 * (CLAUDE.md §8: hàm viết cẩn thận mà không ai gọi là hàm chết).
 */
interface WriteReleaseScheduler {

    /** Hẹn [action] sau [delayMs]; việc đang chờ cùng [key] bị thay (không chạy). */
    fun schedule(key: String, delayMs: Long, action: () -> Unit)

    /** Huỷ việc đang chờ của [key]. `true` ⇔ có một việc CHƯA chạy vừa bị huỷ (nó sẽ không bao giờ chạy). */
    fun cancel(key: String): Boolean

    companion object {
        /** Không hẹn gì cả — mặc định cho mọi chỗ dựng không phải đồ thị tiến trình (xem KDoc giao diện). */
        val NONE: WriteReleaseScheduler = object : WriteReleaseScheduler {
            override fun schedule(key: String, delayMs: Long, action: () -> Unit) = Unit
            override fun cancel(key: String): Boolean = false
        }
    }

    /**
     * Bản thật, thuần JVM: MỘT luồng daemon, tự tắt khi rỗi (không nuôi luồng lúc xe đứng yên — cùng lẽ
     * `MacroExec`), `removeOnCancelPolicy` để việc đã huỷ không nằm lại trong hàng đợi.
     *
     * ## Huỷ đúng kể cả khi `Future.cancel` tới muộn
     * Mỗi lượt hẹn mang một [Token] riêng, và thân việc chỉ chạy khi nó **tự gỡ được chính token của mình** khỏi
     * bảng (`remove(key, token)` — phép so-và-gỡ nguyên tử của `ConcurrentHashMap`). [cancel] và [schedule] đè lên
     * cũng gỡ token ấy ⇒ giữa *"việc bắt đầu chạy"* và *"có người huỷ"* chỉ đúng MỘT bên thắng: hoặc việc chạy và
     * [cancel] trả `false`, hoặc [cancel] trả `true` và việc không bao giờ đụng tới HAL. Không có ca *"báo đã huỷ mà
     * 255 vẫn đi"*.
     *
     * @param onError một việc ném (đường HAL đi qua reflection) — chỗ dựng ghi nhật ký; việc kế vẫn chạy.
     */
    class Jvm(
        threadName: String = "kachi-hal-release",
        private val onError: (String, RuntimeException) -> Unit = { _, _ -> },
    ) : WriteReleaseScheduler {

        private class Token {
            @Volatile var future: ScheduledFuture<*>? = null
        }

        private val pending = ConcurrentHashMap<String, Token>()

        private val exec = ScheduledThreadPoolExecutor(
            1,
            ThreadFactory { r -> Thread(r, threadName).apply { isDaemon = true } },
        ).apply {
            removeOnCancelPolicy = true
            setKeepAliveTime(IDLE_SECONDS, TimeUnit.SECONDS)
            allowCoreThreadTimeOut(true)
        }

        override fun schedule(key: String, delayMs: Long, action: () -> Unit) {
            val token = Token()
            pending.put(key, token)?.future?.cancel(false)
            token.future = exec.schedule({
                if (pending.remove(key, token)) {
                    try {
                        action()
                    } catch (e: RuntimeException) {
                        onError(key, e)
                    }
                }
            }, delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        }

        override fun cancel(key: String): Boolean {
            val token = pending.remove(key) ?: return false
            token.future?.cancel(false)
            return true
        }

        private companion object {
            /** Luồng rỗi sống thêm ngần này rồi tự tắt. */
            const val IDLE_SECONDS = 30L
        }
    }
}
