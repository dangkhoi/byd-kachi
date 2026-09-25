package com.byd.clusternav.launcher.camera

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

/**
 * ═══ NGHE XI-NHAN TỪ HELPER HAL QUA SOCKET 127.0.0.1 ═════════════════════════════════════════════════════════
 *
 * Một luồng daemon nối `127.0.0.1:19322` ([HalHelperLauncher.PORT]), đọc từng DÒNG JSON và gọi [onTurn] mỗi khi
 * trạng thái đổi. Nguồn dữ liệu là helper chạy dưới uid shell — xem [HalHelperLauncher] về lý do phải đi đường
 * vòng này (uid app bị `SecurityException BYDAUTO_LIGHT_GET`).
 *
 * ── HỢP ĐỒNG DÂY ────────────────────────────────────────────────────────────────────────────────────────────
 * Mỗi dòng: `{"topic":"light.onLightOn","type":4}` — `type` **4=trái · 5=phải**, topic `light.onLightOn` /
 * `light.onLightOff`. Helper gửi **ảnh chụp** hai bên ngay khi nối được, nên client không phải chờ lần nháy kế
 * tiếp mới biết trạng thái.
 *
 * ── REALTIME, KHÔNG POLL ────────────────────────────────────────────────────────────────────────────────────
 * [onTurn] được gọi trên **luồng đọc**, không phải main thread — bên nhận tự chuyển luồng nếu cần chạm View.
 * Gọi ngay khi có dòng mới (đó là lý do dùng socket thay vì poll: bật xi-nhan là camera lên).
 *
 * Mất kết nối ⇒ nối lại với backoff [BACKOFF_START_MS] → [BACKOFF_CAP_MS] (helper có thể chưa lên, hoặc vừa bị
 * ROM giết). Backoff có trần để một helper chết hẳn không thành vòng quay 100% CPU.
 */
class HalSignalClient(private val port: Int = HalHelperLauncher.PORT) {

    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var socket: Socket? = null

    @Volatile private var left = false
    @Volatile private var right = false

    /**
     * Bắt đầu nghe. Gọi lại khi đang chạy = no-op (không dựng luồng thứ hai).
     *
     * @param onTurn `(trái, phải)` — gọi trên luồng đọc, mỗi lần một bên ĐỔI.
     */
    @Synchronized
    fun start(onTurn: (Boolean, Boolean) -> Unit) {
        if (running) return
        running = true
        left = false
        right = false
        thread = Thread({ loop(onTurn) }, "KachiHalSignal").apply {
            isDaemon = true
            start()
        }
    }

    /** Dừng nghe. Đóng socket để bẻ `readLine()` đang chặn — nếu không, luồng sống tới khi xe tắt máy. */
    @Synchronized
    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
        thread?.interrupt()
        thread = null
    }

    private fun loop(onTurn: (Boolean, Boolean) -> Unit) {
        var backoff = BACKOFF_START_MS
        while (running) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS)
                    s.tcpNoDelay = true
                    socket = s
                    backoff = BACKOFF_START_MS   // nối được ⇒ đặt lại backoff
                    Log.i(TAG, "đã nối 127.0.0.1:$port")
                    read(s, onTurn)
                }
            } catch (t: Throwable) {
                if (running) Log.d(TAG, "mất kết nối ($t), thử lại sau ${backoff}ms")
            } finally {
                socket = null
            }
            if (!running) break
            try {
                Thread.sleep(backoff)
            } catch (e: InterruptedException) {
                return   // stop() gọi interrupt — thoát, không nuốt rồi chạy tiếp
            }
            backoff = (backoff * 2).coerceAtMost(BACKOFF_CAP_MS)
        }
        Log.i(TAG, "dừng nghe")
    }

    private fun read(s: Socket, onTurn: (Boolean, Boolean) -> Unit) {
        val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        while (running) {
            val line = reader.readLine() ?: break   // null = helper đóng đầu bên kia
            val parsed = parseLine(line)
            if (parsed == null) {
                Log.d(TAG, "bỏ dòng lạ: $line")
                continue
            }
            val (topic, type) = parsed
            val on = when (topic) {
                TOPIC_ON -> true
                TOPIC_OFF -> false
                else -> {
                    Log.d(TAG, "bỏ topic lạ: $topic")
                    continue
                }
            }
            val changed = when (type) {
                TYPE_LEFT -> (left != on).also { left = on }
                TYPE_RIGHT -> (right != on).also { right = on }
                else -> false
            }
            if (changed) {
                Log.i(TAG, "xi-nhan trái=$left phải=$right")
                runCatching { onTurn(left, right) }
                    .onFailure { Log.w(TAG, "onTurn ném: $it") }   // bên nhận ném KHÔNG được giết luồng đọc
            }
        }
    }

    companion object {
        private const val TAG = "KachiHalSignal"

        const val TOPIC_ON = "light.onLightOn"
        const val TOPIC_OFF = "light.onLightOff"
        const val TYPE_LEFT = 4
        const val TYPE_RIGHT = 5

        private const val CONNECT_TIMEOUT_MS = 800
        private const val BACKOFF_START_MS = 1_000L
        private const val BACKOFF_CAP_MS = 8_000L

        /**
         * Hai phép tìm RỜI thay vì một khuôn cả dòng: tách ra thì thêm trường mới vào giao thức (hoặc đổi thứ
         * tự trường) KHÔNG làm client mù — một khuôn `^\{...\}$` sẽ trả `null` cho mọi dòng và tính năng chết
         * im lặng.
         */
        private val TOPIC_RE = Regex("\"topic\"\\s*:\\s*\"([^\"]*)\"")
        private val TYPE_RE = Regex("\"type\"\\s*:\\s*(-?\\d+)")

        /**
         * Bóc `(topic, type)` từ một dòng giao thức. THUẦN — kiểm được off-car, không cần socket.
         *
         * @return `null` nếu dòng không phải JSON có đủ cả `topic` (không rỗng) lẫn `type` là số.
         */
        fun parseLine(line: String?): Pair<String, Int>? {
            val s = line ?: return null
            if (!s.contains('{')) return null
            val topic = TOPIC_RE.find(s)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() } ?: return null
            val type = TYPE_RE.find(s)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            return topic to type
        }
    }
}
