package com.byd.clusternav.launcher.camera

import android.util.Log
import com.byd.clusternav.launcher.HalEventWire
import com.byd.clusternav.launcher.LampLive
import java.io.BufferedReader
import java.io.OutputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

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
 * Mất kết nối ⇒ **báo `(false,false)`** nếu lúc đó còn bên đang bật ([announceOffIfDropped] — không có nó thì
 * overlay camera treo tới khi tắt máy), rồi nối lại với backoff [BACKOFF_START_MS] → [BACKOFF_CAP_MS] (helper có
 * thể chưa lên, hoặc vừa bị ROM giết). Backoff có trần để một helper chết hẳn không thành vòng quay 100% CPU.
 *
 * BG-15 (2026-09-25): thất bại liên tiếp ≥ [IDLE_AFTER_FAILURES] lần (máy ảo / xe không helper) ⇒ trần lùi lên
 * [BACKOFF_CAP_IDLE_MS] — KHÔNG dừng hẳn (helper lên muộn vẫn nối được), chỉ thưa đi. Nối được ⇒ đặt lại từ đầu.
 *
 * 3.01 TURN/LAMPLIVE (owner 13/10): client này cũng là NGUỒN DUY NHẤT của [LampLive] — bảng Đèn + xe 3D của HOME đọc đèn thật
 * từ đó (xi-nhan: getter `getLightStatus(4/5)` luôn trả 0 [ĐO xe 09-25]). Nối được ⇒ phát hai bên TẮT (helper gửi ảnh chụp ngay
 * sau) · mỗi lần một đèn ĐỔI ⇒ phát · đứt dây / [stop] ⇒ `clear()` (bảng lùi về số đọc của `CarStatus`). Ngoài xi-nhan, dòng
 * `light.onLightOn/Off` với `type` khác (1/2/3/6/7) và `light.dayRunning` cập nhật các đèn còn lại ([applyLine]); [onTurn] CHỈ
 * được gọi khi xi-nhan đổi (camera không bị đánh thức vì đèn cốt). Ai giữ client chạy: `HalSignalHub` (đếm nhu cầu camera +
 * bảng Đèn) — không socket thứ hai, không ai khác gọi [start].
 *
 * @param live hộp phát lại trạng thái cho HOME — test truyền hộp riêng.
 * @param sleeper ngủ giữa hai lần nối lại — test thay bằng đồng hồ giả (không chờ thật).
 * @param onLinkLost gọi (trên luồng đọc) khi một kết nối ĐÃ thành công rồi đứt trong lúc còn chạy — `HalSignalHub` dùng để
 *   khởi lại helper nếu nó chết (ROM giết `app_process`); [stop] chủ động KHÔNG gọi.
 */
class HalSignalClient(
    private val port: Int = HalHelperLauncher.PORT,
    private val live: LampLive = LampLive.shared,
    // `onLinkLost` đứng TRƯỚC `sleeper`: bài test cũ truyền `sleeper` bằng lambda đuôi — đổi chỗ thì lambda đuôi âm thầm thành móc khác.
    private val onLinkLost: () -> Unit = {},
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) {

    /** Số lần đã gọi `connect` (test đếm; không dùng cho logic). */
    internal val connectAttempts = AtomicInteger(0)

    @Volatile private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var socket: Socket? = null

    @Volatile private var left = false
    @Volatile private var right = false

    /**
     * 3.1.6 HAL-EVENTS — dòng `hal.*` của helper (chào · kết quả đăng ký · sự kiện) giao cho [onHal] trên luồng đọc; mất dây / [stop]
     * ⇒ [onHalDown]. Gán bởi `HalSignalHub`. Client không hiểu gì về datum: chỉ chuyển thư.
     */
    @Volatile var onHal: ((HalEventWire.Msg) -> Unit)? = null
    @Volatile var onHalDown: (() -> Unit)? = null

    /** Dòng `sub …` đang muốn (gửi lại mỗi lần nối). Helper bản cũ không đọc gì từ client ⇒ dòng nằm yên trong bộ đệm, vô hại. */
    @Volatile private var subscription: String = "sub"
    @Volatile private var out: OutputStream? = null
    private val writeLock = Any()

    /** Thay tập xin của client này; đang nối ⇒ gửi ngay. Chặn (ghi socket) — gọi từ luồng nền, không từ luồng chính. */
    fun subscribe(line: String) {
        subscription = line
        send(line)
    }

    private fun send(line: String) {
        synchronized(writeLock) {
            val o = out ?: return
            runCatching { o.write((line + "\n").toByteArray(Charsets.UTF_8)); o.flush() }.onFailure { logD("gửi '$line' hỏng: $it") }
        }
    }

    /** Trạng thái đèn theo dây của lần nối hiện tại (chỉ luồng đọc ghi). */
    @Volatile private var lamps = LampLive.Lamps(left = false, right = false)

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
        lamps = LampLive.Lamps(left = false, right = false)
        // Gán [thread] TRƯỚC khi start(): luồng đọc so `currentThread() === thread` để biết mình còn là luồng hiện hành.
        val t = Thread({ loop(onTurn) }, "KachiHalSignal").apply { isDaemon = true }
        thread = t
        t.start()
    }

    /** Dừng nghe. Đóng socket để bẻ `readLine()` đang chặn — nếu không, luồng sống tới khi xe tắt máy. */
    @Synchronized
    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
        thread?.interrupt()
        thread = null
        live.clear()
        runCatching { onHalDown?.invoke() }
    }

    /**
     * Luồng gọi còn là luồng đọc HIỆN HÀNH không. [SOÁT Pass 2 · 13/10] Trước đây vòng đọc chỉ hỏi cờ chung [running]: `stop()` rồi
     * `start()` nhanh (hub nhả rồi xin lại) bật [running] về `true` ⇒ luồng CŨ (đang kẹt trong `onTurn`/`readLine`/`connect`) thấy
     * "vẫn chạy" ⇒ ghi đè [socket] của luồng mới về `null` (lần `stop()` sau không bẻ được `readLine` của luồng mới), báo `onLinkLost`
     * giả (hub khởi lại helper vô cớ), rồi — nếu cờ ngắt đã bị ai đó nuốt — NỐI LẠI và phát vào [LampLive] SONG SONG với luồng mới.
     * Gắn với danh tính luồng thì luồng cũ tự thoát ở lần hỏi kế, không ghi gì nữa.
     */
    private fun current(): Boolean = running && Thread.currentThread() === thread

    /** Gán [socket] của lần nối này — dưới khoá của [start]/[stop], chỉ khi còn hiện hành (để [stop] bẻ được cả `connect`). */
    @Synchronized
    private fun claim(s: Socket): Boolean = current().also { if (it) socket = s }

    /** Nhả [socket] nếu nó vẫn là của lần nối này (không xoá socket của luồng mới). */
    @Synchronized
    private fun unclaim(s: Socket) {
        if (socket === s) {
            socket = null
            synchronized(writeLock) { out = null }
            runCatching { onHalDown?.invoke() }
        }
    }

    /**
     * Ghi trạng thái mới + phát vào [LampLive] — dưới khoá của [start]/[stop] và CHỈ khi còn hiện hành ⇒ không bao giờ có hai luồng
     * đọc cùng phát, và không phát đè sau [stop]. @return `false` nếu luồng đã bị thay (bên gọi thoát).
     */
    @Synchronized
    private fun commit(next: LampLive.Lamps): Boolean {
        if (!current()) return false
        lamps = next
        left = next.left
        right = next.right
        live.publish(next)
        return true
    }

    /** Không nối = không biết ⇒ bảng Đèn lùi về số đọc CarStatus — chỉ luồng hiện hành ([stop] đã tự xoá). */
    @Synchronized
    private fun forget() { if (current()) live.clear() }

    private fun loop(onTurn: (Boolean, Boolean) -> Unit) {
        var backoff = BACKOFF_START_MS
        var failures = 0
        while (current()) {
            var linked = false
            val s = Socket()
            try {
                connectAttempts.incrementAndGet()
                s.use {
                    if (!claim(s)) return@use
                    s.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS)
                    s.tcpNoDelay = true
                    backoff = BACKOFF_START_MS   // nối được ⇒ đặt lại backoff
                    failures = 0
                    // Nối được = biết: hai bên TẮT tới khi ảnh chụp của helper nói khác; đèn khác CHƯA biết (null ⇒ getter).
                    if (!commit(LampLive.Lamps(left = left, right = right))) return@use
                    linked = true
                    logI("đã nối 127.0.0.1:$port")
                    synchronized(writeLock) { out = s.getOutputStream() }
                    send(subscription)   // 3.1.6 HAL-EVENTS — tập xin hiện hành (helper mới mất phần của client cũ khi dây đứt)
                    read(s, onTurn)
                }
            } catch (t: Throwable) {
                if (current()) logD("mất kết nối ($t), thử lại sau ${backoff}ms")
            } finally {
                unclaim(s)
                announceOffIfDropped(onTurn)
                forget()
                if (linked && current()) runCatching { onLinkLost() }.onFailure { logW("onLinkLost ném: $it") }
            }
            if (!current()) break
            failures++
            try {
                sleeper(backoff)
            } catch (e: InterruptedException) {
                return   // stop() gọi interrupt — thoát, không nuốt rồi chạy tiếp
            }
            backoff = nextBackoffMs(backoff, failures)
        }
        logI("dừng nghe")
    }

    /**
     * ĐỨT DÂY giữa lúc một bên đang bật ⇒ báo TẮT cả hai bên.
     *
     * [SOÁT Pass 2 · 2026-09-26] Từ 2.70 `CameraHold` giữ camera theo **trạng thái** (`leftOn`/`rightOn`) tới khi
     * thấy một sự kiện OFF — đúng cho helper báo trạng thái ([ĐO xe 2026-09-26]: một `trái=true`, 4,2 s sau một
     * `trái=false`). Nhưng nếu helper **chết giữa lúc ON** (ROM giết, `app_process` bị kill) thì sự kiện OFF
     * KHÔNG BAO GIỜ tới: `CameraHold` không hẹn hết hạn cho bên đang ON ⇒ overlay camera **treo trên màn tới khi
     * tắt máy**. Đây đúng loại lỗi CLAUDE.md §5 cấm: state đổi ra ngoài phải có đường trả lại chạy được cả khi
     * nguồn tin đã chết.
     *
     * ⇒ Mỗi lần vòng nối mất kết nối (readLine trả null / ném), coi như không còn biết gì: đặt hai cờ về false và
     * báo `(false,false)`. Nối lại được thì helper gửi **ảnh chụp** hai bên (hợp đồng dây ở KDoc lớp) ⇒ bên nào
     * còn bật sẽ ON lại sau ≤ backoff (1 s cho lần đứt đầu). `stop()` chủ động (công tắc TẮT) thì KHÔNG báo —
     * `running` đã false và `CameraSignalController.tick()` tự đóng overlay theo pref.
     */
    private fun announceOffIfDropped(onTurn: (Boolean, Boolean) -> Unit) {
        if (!current() || (!left && !right)) return
        left = false
        right = false
        logI("đứt dây giữa lúc xi-nhan đang bật ⇒ báo TẮT cả hai bên (tránh treo overlay)")
        runCatching { onTurn(false, false) }.onFailure { logW("onTurn ném: $it") }
    }

    private fun read(s: Socket, onTurn: (Boolean, Boolean) -> Unit) {
        val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        while (current()) {
            val line = reader.readLine() ?: break   // null = helper đóng đầu bên kia
            val msg = HalEventWire.parse(line)   // 3.1.6 HAL-EVENTS — không phải dòng đèn
            if (msg != null) {
                if (current()) runCatching { onHal?.invoke(msg) }.onFailure { logW("onHal ném: $it") }
                continue
            }
            val parsed = parseLine(line)
            if (parsed == null) {
                logD("bỏ dòng lạ: $line")
                continue
            }
            val (topic, type) = parsed
            val next = applyLine(lamps, topic, type)
            if (next == null) {
                logD("bỏ dòng lạ: $topic/$type")
                continue
            }
            if (next == lamps) continue
            val changed = next.left != left || next.right != right
            if (!commit(next)) break   // luồng đã bị thay sau stop()→start() — không phát, không gọi onTurn cũ
            if (changed) {
                logI("xi-nhan trái=$left phải=$right")
                runCatching { onTurn(left, right) }
                    .onFailure { logW("onTurn ném: $it") }   // bên nhận ném KHÔNG được giết luồng đọc
            } else {
                logI("đèn $next")   // đèn khác xi-nhan đổi — hiếm (bật/tắt cốt, pha…), là bằng chứng buổi xe (CLAUDE.md §11)
            }
        }
    }

    // Log bọc runCatching: luồng này chạy được trong test JVM thuần (android.jar stub ném "Stub!" ở mọi Log.*) —
    // test backoff/đếm luồng dùng socket THẬT trên cổng không lắng nghe, không cần Robolectric. Chỉ nuốt lỗi của Log.
    private fun logI(msg: String) { runCatching { Log.i(TAG, msg) } }
    private fun logD(msg: String) { runCatching { Log.d(TAG, msg) } }
    private fun logW(msg: String) { runCatching { Log.w(TAG, msg) } }

    companion object {
        private const val TAG = "KachiHalSignal"

        const val TOPIC_ON = "light.onLightOn"
        const val TOPIC_OFF = "light.onLightOff"

        /** 3.01 LAMPLIVE — `onDayRunningLightStateChanged(v)` chuyển nguyên `v`: 1=BẬT · 2=TẮT (`DAY_RUNNING_LIGHT_ON/OFF`). */
        const val TOPIC_DRL = "light.dayRunning"
        const val TYPE_LEFT = LampLive.TYPE_LEFT
        const val TYPE_RIGHT = LampLive.TYPE_RIGHT

        /**
         * Một dòng → trạng thái đèn mới. THUẦN, test off-car. `null` = dòng không hiểu (topic lạ · `type` ngoài thang · DRL giá trị
         * lạ như 0=INVALID/3=UNDEFINED) — bỏ, KHÔNG đoán. `type` hợp lệ mà không đổi gì ⇒ trả đối tượng bằng [cur].
         */
        fun applyLine(cur: LampLive.Lamps, topic: String, type: Int): LampLive.Lamps? = when (topic) {
            TOPIC_ON, TOPIC_OFF -> cur.withType(type, topic == TOPIC_ON).takeIf { type in LampLive.TYPE_SIDE..LampLive.TYPE_REAR_FOG }
            TOPIC_DRL -> when (type) { 1 -> cur.copy(drl = true); 2 -> cur.copy(drl = false); else -> null }
            else -> null
        }

        private const val CONNECT_TIMEOUT_MS = 800
        const val BACKOFF_START_MS = 1_000L
        const val BACKOFF_CAP_MS = 8_000L

        /** Sau ngần này lần thất bại LIÊN TIẾP (≈39 s với 1→8 s) thì coi helper vắng — thưa nhịp thử. */
        const val IDLE_AFTER_FAILURES = 8

        /** Trần khi helper vắng: 60 s — vẫn thử, không vĩnh viễn. */
        const val BACKOFF_CAP_IDLE_MS = 60_000L

        /**
         * Backoff kế tiếp — THUẦN, test off-car. Nhân đôi tới trần; trần = [BACKOFF_CAP_MS] khi mới thất bại vài
         * lần (helper đang lên / vừa bị giết — cần nối lại nhanh), = [BACKOFF_CAP_IDLE_MS] khi đã thất bại
         * ≥ [IDLE_AFTER_FAILURES] lần liên tiếp.
         */
        fun nextBackoffMs(currentMs: Long, consecutiveFailures: Int): Long {
            val cap = if (consecutiveFailures >= IDLE_AFTER_FAILURES) BACKOFF_CAP_IDLE_MS else BACKOFF_CAP_MS
            return (currentMs * 2).coerceAtMost(cap)
        }

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
