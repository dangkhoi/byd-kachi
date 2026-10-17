package com.byd.clusternav.launcher.voice

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.launcher.DiagRingFile
import java.io.File

/**
 * ═══ WAKE-WEAK-UNIT (3.1.14) — phần CHẠM HỆ THỐNG của nhịp "Hey Kachi" theo tải máy (luật thuần: `:core` `VoiceWakePacer.kt`) ═══
 *
 * Ba việc, mỗi việc một lối vào:
 *  • [VoiceLoadPacing.atLowest] — mọi lượt dựng recognizer chạy ở nice 19. [ĐO AOSP r47 + kernel 4.19, doc §3b] luồng ORT được
 *    `pthread_create` trong lúc dựng phiên (`EigenNonBlockingThreadPool.h:901`, `posix/env.cc:162-178`) và thừa kế nice của luồng
 *    dựng (`sched/core.c:2306`). Trước bản này, lượt dựng đầu do phiên lệnh (`KachiListen`, nice 0) kích thì luồng ORT ở nice 0
 *    vĩnh viễn; nay luôn 19, bất kể ai kích.
 *  • [VoiceLoadPacing.awaitCalm] — lượt nạp NỀN chờ máy rảnh ([VoiceLoadDefer]); người bấm mic không đi qua đây.
 *  • [VoiceWakePacing] — bộ nghe hỏi tải mỗi giây trên CHÍNH luồng nghe ([VoiceSchedProbe]), hạ ngân sách khi bận / máy yếu,
 *    ghi một dòng trạng thái mỗi phút vào `filesDir/diag/wake-pacing.log` (màn Chẩn đoán đọc, `PerfDiag`).
 */
internal object VoiceLoadPacing {
    private const val TAG = "WakePace"

    /** Lượt chờ gần nhất (ms) + kết quả, `-1` = chưa chờ lượt nào. Chỉ để nhật ký/dòng trạng thái. */
    @Volatile var lastDeferMs = -1L
        private set
    @Volatile var lastDeferGo = "-"
        private set

    /**
     * Chạy [block] với luồng gọi ở `THREAD_PRIORITY_LOWEST` (nice 19), trả lại mức cũ ở `finally` (`init.rc:271 setrlimit nice
     * 40 40` cho phép nâng lại). Lỗi đặt/trả mức ⇒ một dòng log, [block] vẫn chạy.
     */
    fun <T> atLowest(block: () -> T): T {
        val tid = Process.myTid()
        val old = runCatching { Process.getThreadPriority(tid) }.getOrNull()
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST) }.onFailure { Log.w(TAG, "không hạ được ưu tiên lượt nạp", it) }
        try {
            return block()
        } finally {
            if (old != null && old != Process.THREAD_PRIORITY_LOWEST) {
                runCatching { Process.setThreadPriority(old) }.onFailure { Log.w(TAG, "không trả được ưu tiên $old", it) }
            }
        }
    }

    /**
     * Chặn luồng NỀN gọi tới khi máy rảnh liên tục [VoiceLoadDefer.QUIET_MS] hoặc đã chờ [VoiceLoadDefer.MAX_WAIT_MS]. Bị ngắt ⇒
     * trả ngay (giữ cờ ngắt). [ready] đúng giữa chừng (soát Pass 15: người bấm mic nạp ngay — không qua đây — hoặc lượt chờ kia
     * ở `:wake` nạp xong) ⇒ [VoiceLoadDefer.Go.READY] ngay: trước đó bộ nghe còn đứng chờ tới 120 s dù mô hình đã có.
     */
    fun awaitCalm(why: String, ready: () -> Boolean = { VoiceEngine.loaded() }): VoiceLoadDefer.Go {
        val probe = VoiceSchedProbe()
        val backoff = VoiceWakeBackoff()
        val defer = VoiceLoadDefer()
        val t0 = SystemClock.elapsedRealtime()
        var go = VoiceLoadDefer.Go.WAIT
        while (go == VoiceLoadDefer.Go.WAIT) {
            if (ready()) { go = VoiceLoadDefer.Go.READY; break }
            val d = try { probe.napSample(VoiceLoadDefer.STEP_MS) } catch (e: InterruptedException) {
                Thread.currentThread().interrupt(); Log.i(TAG, "nạp nền ($why): bị ngắt khi đang chờ"); return VoiceLoadDefer.Go.TIMEOUT
            }
            val busy = backoff.sample(d)
            go = defer.step(SystemClock.elapsedRealtime() - t0, if (backoff.lastDelayMs == null) null else busy)
        }
        lastDeferMs = SystemClock.elapsedRealtime() - t0
        lastDeferGo = go.name
        Log.i(TAG, "nạp nền ($why): chờ $lastDeferMs ms · $go · nguồn=${probe.mode} · trễ cuối=${backoff.lastDelayMs?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "-"} ms")
        return go
    }
}

/**
 * Độ trễ hàng đợi chạy của LUỒNG GỌI (dựng trên chính luồng sẽ đo — `tid` chốt lúc dựng). `/proc/self/task/<tid>/schedstat` đọc được
 * ([ĐO AOSP r47 `public/domain.te:27` `r_dir_file(domain, self)`]; [ĐO máy ảo] kernel có `CONFIG_SCHED_INFO`) ⇒ chế độ `schedstat`;
 * không ⇒ `oversleep` (chỉ [napSample] có số). Đọc một tệp ~vài chục µs, không luồng, không nhịp mới.
 */
internal class VoiceSchedProbe {
    private val file = File("/proc/self/task/${Process.myTid()}/schedstat")
    private val delay = VoiceSchedDelay()
    private val over = VoiceOversleep()
    private val schedstat: Boolean = read() != null

    val mode: String get() = if (schedstat) "schedstat" else "oversleep"

    private fun read(): Pair<Long, Long>? = runCatching { VoiceSchedDelay.parse(file.readText()) }.getOrNull()

    /**
     * Ngủ [ms] rồi trả ms CHỜ CPU của đúng lượt thức dậy ấy: mốc `schedstat` ngay trước `sleep`, hiệu ngay sau (không có
     * `schedstat` ⇒ ms ngủ quá). Phải là lượt thức do HẸN GIỜ: [ĐO máy ảo 17/10, doc §3b] lượt thức do luồng `AudioRecord`
     * (nice −16) đánh thức thì chiếm ngay lõi mà luồng ấy vừa nhả: chờ TB 0,03 ms rảnh → 0,11–0,20 ms khi 2/2 lõi bận — quá nhỏ để
     * phân biệt; lượt thức hẹn giờ: 0,02–0,78 → 2,7–3,7 ms.
     */
    fun napSample(ms: Long): Double? {
        if (schedstat) read()?.let { delay.sample(it.first, it.second, dirty = true) }
        val t0 = SystemClock.elapsedRealtime()
        Thread.sleep(ms)
        val t1 = SystemClock.elapsedRealtime()
        return if (schedstat) read()?.let { delay.sample(it.first, it.second) } else over.sample(ms, t1 - t0)
    }
}

/**
 * Nhịp của bộ nghe — dựng TRÊN luồng `kachi-wake` (đầu `runOuter`). [tick] mỗi giây (cùng nhịp đọc tải cũ), [minute] mỗi phút.
 * Chi phí: một lần đọc `schedstat` mỗi giây; một dòng tệp mỗi phút. 0 luồng, 0 nhịp mới.
 */
internal class VoiceWakePacing(private val ctx: Context) {
    private val probe = VoiceSchedProbe()
    private val backoff = VoiceWakeBackoff()
    private val cores = Runtime.getRuntime().availableProcessors()
    private val ramMb: Long
    private val lowRam: Boolean
    private var heavyDecodeMs: Long? = null
    private var lastCpu = Process.getElapsedCpuTime()
    private var lastWall = SystemClock.elapsedRealtime()
    private var lastDecodes = 0L
    private var lastCharged = 0L

    init {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        ramMb = mi.totalMem shr 20
        lowRam = am?.isLowRamDevice == true
        Log.i(TAG, "nhịp wake: nguồn tải=${probe.mode} · lõi=$cores · ramMB=$ramMb · lowRam=$lowRam · trần=${deviceDuty()}" +
            (VoiceWakeDuty.lowEndReason(cores, ramMb, lowRam, null)?.let { " (máy yếu: $it)" } ?: ""))
    }

    private fun deviceDuty(): Double = VoiceWakeDuty.forDevice(cores, ramMb, lowRam, heavyDecodeMs)

    /**
     * Mỗi giây: một mẫu tải (ngủ [PROBE_MS] trên luồng nghe — đệm micro 1 s nên không mất mẫu) → bận/rảnh → trần ngân sách của
     * [ctl]. Đổi trạng thái thì ghi một dòng (hiếm).
     */
    fun tick(ctl: VoiceWakeController, nowMs: Long) {
        val was = backoff.busy
        val d = try { probe.napSample(PROBE_MS) } catch (e: InterruptedException) { Thread.currentThread().interrupt(); null }
        val busy = backoff.sample(d)
        if (busy != was) Log.i(TAG, "máy ${if (busy) "BẬN" else "rảnh"} (trễ ${backoff.lastDelayMs?.let { String.format(java.util.Locale.US, "%.1f", it) }} ms/lượt thức) — trần ${VoiceWakeDuty.limits(deviceDuty(), busy, 0.0).first}")
        ctl.pace(deviceDuty(), busy, nowMs)
    }

    /**
     * Mỗi phút: đoạn chữ cho dòng `ngan_sach` + một dòng `wake-pacing.log`. Giá một lượt giải mã của phút này (≥ 3 lượt) quá
     * [VoiceWakeDuty.HEAVY_DECODE_MS] ⇒ máy này giải mã nặng, giữ trần máy yếu tới hết tiến trình.
     */
    fun minute(ctl: VoiceWakeController, decodes: Long): String {
        val now = SystemClock.elapsedRealtime(); val cpu = Process.getElapsedCpuTime()
        val pct = if (now > lastWall) (cpu - lastCpu) * 100 / (now - lastWall) else 0L
        val dDec = decodes - lastDecodes; val dCharged = ctl.chargedCpuMs() - lastCharged
        if (dDec >= 3 && heavyDecodeMs == null) {
            val mean = dCharged / dDec
            if (mean > VoiceWakeDuty.HEAVY_DECODE_MS) { heavyDecodeMs = mean; Log.w(TAG, "giải mã nặng: $mean ms CPU/lượt — trần ${deviceDuty()}") }
        }
        lastCpu = cpu; lastWall = now; lastDecodes = decodes; lastCharged = ctl.chargedCpuMs()
        val frag = "cpu_wake=$pct% tai_he=${if (backoff.busy) "ban" else "ranh"} tre_ms=${backoff.lastDelayMs?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "-"}" +
            " lan_ban=${backoff.busySpells} tran=${ctl.budgetDuty()} nguon=${probe.mode}" +
            " nap_ms=${VoiceEngine.lastBuildMs()} cho_nap_ms=${VoiceLoadPacing.lastDeferMs}(${VoiceLoadPacing.lastDeferGo})"
        STATUS.append(ctx.applicationContext, "${java.time.LocalTime.now().withNano(0)} $frag")
        return frag
    }

    companion object {
        private const val TAG = "WakePace"

        /** Giấc ngủ đo tải mỗi giây trên luồng nghe (một lượt thức do hẹn giờ). */
        private const val PROBE_MS = 2L

        /** `filesDir/diag/wake-pacing.log` — 30 dòng (30 phút) cuối. Màn Chẩn đoán đọc dòng cuối ([lastStatus]). */
        const val STATUS_NAME = "wake-pacing.log"
        private val STATUS = DiagRingFile(STATUS_NAME, 30, TAG)

        /** Dòng trạng thái gần nhất `:wake` đã ghi (tiến trình nào đọc cũng được — tệp chung), `null` = chưa có. */
        fun lastStatus(ctx: Context): String? = STATUS.read(ctx).lastOrNull()
    }
}

/**
 * Nguồn tải cho [VoiceLoadGuard] — xem KDoc [VoiceWakeListener] (dời khỏi tệp đó, trần 500 dòng, 3.1.14). Chỉ luồng `kachi-wake` chạm (không cần khoá).
 *
 * Hai chế độ, chốt ở [probe]:
 *  • `LOADAVG` — `/proc/loadavg` đọc được (đời ROM khác) ⇒ `load1` như cũ, guard mặc định.
 *  • `SELF_CPU` — bị chặn ⇒ [VoiceSelfCpuMeter] trên `Process.getElapsedCpuTime()` ⇒ "số lõi tiến trình này
 *    đang dùng", guard [VoiceLoadGuard.forSelfCpu]. Không còn ngoại lệ/`avc: denied` nào mỗi giây.
 */
internal class VoiceLoadSource {
    private enum class Mode { LOADAVG, SELF_CPU }
    private var mode = Mode.SELF_CPU
    private val meter = VoiceSelfCpuMeter()
    private val nproc = Runtime.getRuntime().availableProcessors()

    /** Dò một lần; trả guard đúng thang. Ghi W đúng MỘT dòng khi `/proc/loadavg` không đọc được. */
    fun probe(): VoiceLoadGuard {
        val first = runCatching { VoiceLoadGuard.parseLoadavg(File(LOADAVG).readText()) }
        mode = if (first.isSuccess && first.getOrNull() != null) Mode.LOADAVG else Mode.SELF_CPU
        return if (mode == Mode.LOADAVG) {
            Log.i(TAG, "nguồn tải: /proc/loadavg (load1=${first.getOrNull()})")
            VoiceLoadGuard()
        } else {
            Log.w(
                TAG,
                "nguồn tải: /proc/loadavg không đọc được (${first.exceptionOrNull()?.javaClass?.simpleName ?: "rác"}" +
                    ") — chuyển sang CPU của chính tiến trình, ngưỡng theo $nproc lõi",
            )
            VoiceLoadGuard.forSelfCpu(nproc)
        }
    }

    fun read(nowMs: Long): Double = when (mode) {
        // Đọc được lúc dò mà hỏng giữa chừng (hiếm) ⇒ 0.0 như cũ, KHÔNG đổi chế độ giữa vòng (guard đã theo thang này).
        Mode.LOADAVG -> runCatching { VoiceLoadGuard.parseLoadavg(File(LOADAVG).readText()) }.getOrNull() ?: 0.0
        Mode.SELF_CPU -> meter.sample(Process.getElapsedCpuTime(), nowMs)
    }

    private companion object {
        const val TAG = "WakeListen"
        const val LOADAVG = "/proc/loadavg"
    }
}
