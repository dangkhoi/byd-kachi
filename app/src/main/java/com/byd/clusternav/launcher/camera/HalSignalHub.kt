package com.byd.clusternav.launcher.camera

import android.util.Log
import com.byd.clusternav.launcher.HalSignalDemand
import com.byd.clusternav.launcher.LampLive
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * ═══ 3.01 LAMPLIVE — CHỦ DUY NHẤT của vòng đời HAL helper + client socket 19322 ══════════════════════════════════════════════
 *
 * Owner 13/10: *"nối luôn đi, đâu cần camera xi nhan bật mới sáng, tự nối lấy tín hiệu được mà"*. Trước lượt này camera xi-nhan tự
 * giữ một [HalSignalClient] + tự gọi [HalHelperLauncher.ensure]; nay MỘT hub (một mỗi tiến trình, `AppContainer.halSignal`) giữ
 * ĐÚNG MỘT client và là chỗ DUY NHẤT gọi `ensure`. Ai cần socket thì [want] theo nguồn ([HalSignalDemand.Source]):
 *  - `CAMERA` — `CameraSignalController` (công tắc camera xi-nhan, như cũ);
 *  - `LAMP_WIDGET` — `LampSignalDemand.Watch` của `AppContainer` (màn chính bày đèn + màn xe sáng).
 * Đếm/ân hạn ở [HalSignalDemand] (`:core`, test bằng đồng hồ giả): nguồn đầu tiên ⇒ `ensure` + `start` MỘT lần; hết nguồn ⇒ `stop`
 * sau [graceMs] (60 s).
 *
 * ── LUỒNG ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
 * Mọi việc chặn (`ensure` = dò cổng 400 ms; lần đầu/lệch bản: `wc`/đẩy jar/`nohup app_process` qua `ShellTransport`) chạy trên MỘT
 * luồng daemon `kachi-hal-hub` (thay luồng `kachi-camera-hal` cũ của controller — không thêm luồng). [want] KHÔNG BAO GIỜ chặn ⇒
 * gọi được từ luồng chính / luồng IO của vòng poll. Executor một luồng ⇒ start/stop không đảo nhau. Rỗi > [KEEP_ALIVE_S] ⇒ luồng
 * tự thu (camera tắt + không bảng Đèn ⇒ 0 luồng của hub).
 *
 * ── HÀNG ĐỢI SHELL ────────────────────────────────────────────────────────────────────────────────────────────────────────────
 * Lệnh shell của `ensure` đi mức `MutationPriority.BACKGROUND` (rút SAU mọi lệnh đặt-cửa-sổ/cast đã xếp) — xem `HalHelperLauncher`.
 * Lần `ensure` thứ hai trở đi trong cùng tiến trình (helper còn sống) = 0 lệnh shell (`verified && probe()`).
 *
 * ── HELPER CHẾT (ROM giết `app_process`) ──────────────────────────────────────────────────────────────────────────────────────
 * Client báo [HalSignalClient] `onLinkLost` (link ĐÃ nối rồi đứt) ⇒ hub gọi lại `ensure` (khởi lại helper) — tối đa một lần mỗi
 * [healMinIntervalMs] để helper chết ngay khi lên không thành vòng shell. Trước lượt này không có đường này: helper chết giữa chuyến
 * ⇒ client lùi tới 60 s/lần mãi mà không ai khởi lại. Tiến trình APP chết ⇒ nhu cầu RAM mất theo (đúng: không ai cần); helper
 * `nohup` sống tiếp — y như trước (camera xi-nhan); tiến trình mới `ensure` lại thấy cổng mở ⇒ dùng lại, không đẩy jar.
 *
 * @param ensure bảo đảm helper phục vụ cổng — prod `HalHelperLauncher.ensure(app)`; test đếm lời gọi.
 * @param clientFactory dựng client DUY NHẤT với móc `onLinkLost` — test trỏ cổng giả.
 */
class HalSignalHub(
    private val ensure: () -> Boolean,
    graceMs: Long = HalSignalDemand.GRACE_MS,
    private val healMinIntervalMs: Long = HEAL_MIN_INTERVAL_MS,
    private val live: LampLive = LampLive.shared,
    clientFactory: (onLinkLost: () -> Unit) -> HalSignalClient = { lost -> HalSignalClient(onLinkLost = lost) },
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val exec = ScheduledThreadPoolExecutor(1) { r -> Thread(r, THREAD).apply { isDaemon = true } }.apply {
        setKeepAliveTime(KEEP_ALIVE_S, TimeUnit.SECONDS)
        allowCoreThreadTimeOut(true)
        removeOnCancelPolicy = true
    }

    private val client: HalSignalClient = clientFactory { exec.execute { heal() } }

    /** Chỉ luồng hub đọc/ghi. */
    private var running = false
    private var lastEnsureMs = Long.MIN_VALUE / 2

    /** Lần khởi-lại-helper đang HOÃN vì trần tần suất (tối đa một) — chỉ luồng hub đọc/ghi. */
    private var healLater: ScheduledFuture<*>? = null

    /** Số lần đã gọi [ensure] (test/chẩn đoán). */
    @Volatile var ensureCalls = 0
        private set

    private val demand = HalSignalDemand(
        graceMs = graceMs,
        schedule = { ms, task -> exec.schedule({ task() }, ms, TimeUnit.MILLISECONDS).let { f -> { f.cancel(false); Unit } } },
        acquire = { exec.execute { up() } },
        release = { exec.execute { down() } },
    )

    /**
     * Người nhận xi-nhan ĐỔI (camera). Gán khi socket đã chạy vì bảng Đèn ⇒ phát lại NGAY trạng thái đang biết nếu có bên đang
     * bật (không thì camera bật lúc đang gạt cần sẽ chờ tới lần đổi kế — trước đây `start` mới của client gửi ảnh chụp thay).
     */
    @Volatile var onTurn: ((Boolean, Boolean) -> Unit)? = null
        set(value) {
            field = value
            val now = live.state.value ?: return
            if (value != null && (now.left || now.right)) runCatching { value(now.left, now.right) }
        }

    /** [source] cần (true) / thôi cần (false) socket. Không chặn; gọi từ luồng nào cũng được. */
    fun want(source: HalSignalDemand.Source, on: Boolean) = demand.set(source, on)

    /** Việc nền nhỏ cùng luồng hub (vd `cam_sort` của camera) — không dựng luồng riêng. */
    fun background(task: () -> Unit) = exec.execute { runCatching(task).onFailure { logW("việc nền ném: $it") } }

    /** Đang giữ helper + client (kể cả trong ân hạn). */
    val isActive: Boolean get() = demand.isActive

    /** Tập nguồn đang cần (chẩn đoán). */
    val sources: Set<HalSignalDemand.Source> get() = demand.sources

    private fun up() {
        running = true
        logI("giữ helper + socket (nguồn=${demand.sources})")
        callEnsure()
        runCatching { client.start { l, r -> onTurn?.invoke(l, r) } }.onFailure { logW("client.start ném: $it") }
    }

    private fun down() {
        running = false
        healLater?.cancel(false)
        healLater = null
        logI("nhả socket (hết nguồn sau ân hạn)")
        runCatching { client.stop() }.onFailure { logW("client.stop ném: $it") }
    }

    /**
     * Link đã nối rồi đứt (helper chết?) ⇒ khởi lại helper, có trần tần suất.
     *
     * [SOÁT Pass 2 · 13/10] Trong trần ⇒ HOÃN tới hết trần, không BỎ. Trước đây lời gọi trong trần bị vứt: helper chết < 30 s sau
     * lần `ensure` trước (vừa khởi đã bị ROM giết, hoặc chết lần hai ngay sau lần chữa) ⇒ client không bao giờ nối được nữa ⇒
     * `onLinkLost` (chỉ báo khi một link ĐÃ nối rồi đứt) không bao giờ tới lại ⇒ helper chết luôn tới khi nhu cầu tắt-bật lại, im
     * lặng. Nhiều lần đứt trong trần gộp vào MỘT lần hoãn ⇒ vẫn ≤ 1 `ensure` / [healMinIntervalMs].
     */
    private fun heal() {
        if (!running) return
        val wait = lastEnsureMs + healMinIntervalMs - clock()
        if (wait > 0) {
            if (healLater == null) {
                healLater = exec.schedule({ healLater = null; heal() }, wait, TimeUnit.MILLISECONDS)
            }
            return
        }
        logI("link đứt ⇒ khởi lại helper")
        callEnsure()
    }

    private fun callEnsure() {
        lastEnsureMs = clock()
        ensureCalls++
        runCatching { ensure() }
            .onSuccess { up -> if (!up) logW("ensure: helper KHÔNG phục vụ cổng — client vẫn thử nối theo backoff") }
            .onFailure { logW("ensure ném: $it") }
    }

    // Log bọc runCatching: hub chạy được trong test JVM thuần (android.jar stub ném "Stub!") — cùng lệ [HalSignalClient].
    private fun logI(msg: String) { runCatching { Log.i(TAG, msg) } }
    private fun logW(msg: String) { runCatching { Log.w(TAG, msg) } }

    companion object {
        private const val TAG = "KachiHalHub"
        const val THREAD = "kachi-hal-hub"

        /** Luồng hub rỗi quá ngần này ⇒ tự thu (không còn nhu cầu, không hẹn nhả nào đang chờ). */
        const val KEEP_ALIVE_S = 10L

        /** Hai lần khởi-lại-helper (vì link đứt) cách nhau tối thiểu 30 s. */
        const val HEAL_MIN_INTERVAL_MS = 30_000L
    }
}
