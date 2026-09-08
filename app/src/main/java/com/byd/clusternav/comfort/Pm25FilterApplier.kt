package com.byd.clusternav.comfort

import android.content.Context
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.modules.hal.BydHal

/**
 * BẬT/TẮT LỌC BỤI MỊN (PM2.5) TỰ ĐỘNG trên HAL — "tự bật lọc, không hiện popup gì hết" (owner). Đi ĐƯỜNG
 * REFLECTION SẴN CÓ của ClusterNav ([BydHal.device]) như [SeatComfortApplier], nhưng các method GHI nằm trên
 * `BYDAutoAcDevice` và **CHỈ có trên ROM xe, KHÔNG có trong SDK jar** ⇒ gọi bằng `getMethod(...).invoke(...)`,
 * MỖI lời gọi bọc `runCatching` riêng (ROM thiếu method phải no-op, KHÔNG crash).
 *
 * ── Cơ chế (RE ground-truth + sửa on-car 2026-09-06) ────────────────────────────────────────────
 *  • BẬT  → `setAutoCleanAirState(1)` (đặt chế độ lọc, proven on-car rc=0) RỒI best-effort
 *    `enablePurificationFunctionPrompt(0)` (tắt POPUP nhắc lọc — trim owner TỪ CHỐI, xem ⚠). Nếu KHI ĐÓ mức
 *    đọc được ≥ [Pm25Filter.HEAVY] → `setQuickCleanAirState(1)` (lọc-ngay). RỒI khởi [startPollLoop]: poll mỗi
 *    [POLL_INTERVAL_MS], khi bụi ≥ ngưỡng → `setQuickCleanAirState(1)` — VÌ on-car 2026-09-08 owner báo
 *    `setAutoCleanAirState` MỘT MÌNH KHÔNG tự lọc khi bụi tăng (popup hiện mà không lọc); quick-clean mới lọc thật.
 *  • TẮT  → `setAutoCleanAirState(0)` (đường chính) + best-effort `enablePurificationFunctionPrompt(1)` (khôi phục popup).
 *  • ĐỌC  → `BYDAutoPM2p5Device.getPM2p5Level()[0]` (device 1008) cho hiển thị; off-car → [Pm25Filter.INVALID].
 *
 * ⚠ `enablePurificationFunctionPrompt` là **BEST-EFFORT**: on-car (2026-09-06) trả `rc=-2147482645` (sentinel
 *   TỪ CHỐI, KHÁC `NOT_PROVISIONED`) và KHÔNG xuất hiện trong app OEM `com.byd.airconditioning` (không có bằng
 *   chứng arg đúng) ⇒ trim này không hỗ trợ. Nó KHÔNG BAO GIỜ được chặn đường `setAutoCleanAirState` đang chạy
 *   — nên gọi SAU đường chính + log **DEBUG** (không phải lỗi). Lọc vẫn chạy; chỉ popup nhắc lọc có thể còn hiện.
 *
 * ── Vòng đời ─────────────────────────────────────────────────────────────────────────────────────
 *  • [applyOnStart] — gọi lúc mở app (MainActivity.onCreate) và boot nền (BootSetupService). Công tắc TẮT ⇒
 *    no-op. BẬT ⇒ chạy NỀN, ngủ ~5 s (khớp SeatComfortApplier: chờ cabin/HAL sẵn sàng) rồi bật lọc + poll.
 *  • [enable] (bật lọc + khởi poll) / [disable] (tắt lọc + dừng poll) — cho công tắc UI (không delay, chạy nền).
 *  • [cleanNow] — nút "Lọc ngay": bắn lọc-ngay MỘT LẦN, KHÔNG gate công tắc (chủ động lọc bất kể auto).
 *
 * ── An toàn (degrade-safe) ───────────────────────────────────────────────────────────────────────
 * Toàn bộ bọc `runCatching`; off-car / không có HAL → `device()` trả null → log rồi return, KHÔNG ném. Bộ test
 * đầy đủ chạy off-car nên đường này PHẢI không ném. Mỗi thao tác có [Log] tag "Pm25Filter" (method/arg/rc) +
 * mức đọc được, để owner xác minh trên xe bằng logcat.
 */
object Pm25FilterApplier {

    const val TAG = "Pm25Filter"

    /** Trễ ~5 s sau khi start (khớp [SeatComfortApplier.START_DELAY_MS]) để cabin/HAL sẵn sàng trước khi ghi. */
    const val START_DELAY_MS = 5_000L

    /**
     * Chu kỳ POLL mức bụi khi công tắc BẬT. Sinh ra vì on-car (2026-09-08) owner báo: bụi lên tới mức xe hiện
     * popup nhắc lọc NHƯNG KHÔNG tự lọc — tức `setAutoCleanAirState(1)` một mình KHÔNG chủ động lọc trên trim
     * này. Vòng poll đọc mức mỗi [POLL_INTERVAL_MS] và khi ≥ ngưỡng ([Pm25Filter.isDirty]) thì bắn lọc-ngay
     * (`setQuickCleanAirState(1)`) — thứ THẬT SỰ lọc. ClusterNav chạy nền liên tục nên vòng sống theo process.
     */
    const val POLL_INTERVAL_MS = 45_000L

    /**
     * Guard vòng poll. [polling] = "đang có vòng poll của THẾ HỆ HIỆN TẠI" (chặn khởi trùng khi enable/
     * applyOnStart gọi nhiều lần). [pollGeneration] = DANH TÍNH vòng: mỗi lần khởi ([startPollLoop]) hoặc dừng
     * ([disable]) đều ++ dưới khoá; một thread poll CHỈ sống khi thế hệ của nó còn == [pollGeneration]. Nhờ token
     * thế hệ, race enable→disable→enable (disable trong lúc thread đang NGỦ [POLL_INTERVAL_MS] rồi enable lại)
     * KHÔNG để thread cũ sống sót cạnh thread mới, và `finally` của thread cũ KHÔNG xoá cờ của thread mới.
     * (Chỉ dùng `@Volatile private var polling` như trước thì thread cũ đọc thấy cờ do thread mới bật ⇒ 2 vòng.)
     */
    @Volatile private var polling = false
    @Volatile private var pollGeneration = 0

    /** Gọi lúc mở app / boot nền. Công tắc TẮT ⇒ no-op. BẬT ⇒ bật lọc sau ~5 s trên thread nền. */
    fun applyOnStart(ctx: Context) {
        if (!Prefs.pm25FilterEnabled(ctx)) return
        val app = ctx.applicationContext
        Thread({
            runCatching { Thread.sleep(START_DELAY_MS) }
            enableNow(app)
        }, "pm25-filter-start").start()
    }

    /** Công tắc BẬT: bật lọc NGAY (nền, không delay). Công tắc phải đã BẬT trong Prefs (guard trong [enableNow]). */
    fun enable(ctx: Context) {
        val app = ctx.applicationContext
        Thread({ enableNow(app) }, "pm25-filter-enable").start()
    }

    /** Công tắc TẮT: tắt lọc + khôi phục popup NGAY (nền). KHÔNG gate theo Prefs (pref vừa bị đặt về false). */
    fun disable(ctx: Context) {
        synchronized(this) {
            pollGeneration++     // vô hiệu hoá vòng hiện tại: thread poll đang chạy sẽ thoát ở lần kiểm kế tiếp
            polling = false      // cho phép enable sau khởi một vòng MỚI (thế hệ mới)
        }
        val app = ctx.applicationContext
        Thread({ disableNow(app) }, "pm25-filter-disable").start()
    }

    /**
     * Nút "Lọc ngay" (UI): bắn lọc-ngay MỘT LẦN ([quickClean] → `setQuickCleanAirState(1)`) NGAY, KHÔNG delay,
     * KHÔNG gate theo công tắc auto — owner muốn chủ động lọc bất kể auto đang bật hay tắt. Nền, degrade-safe.
     */
    fun cleanNow(ctx: Context) {
        val app = ctx.applicationContext
        Thread({ quickClean(app) }, "pm25-clean-now").start()
    }

    /**
     * Đọc mức PM2.5 hiện tại = `getPM2p5Level()[0]` (device 1008) qua reflection. Off-car / không có HAL /
     * mảng rỗng → [Pm25Filter.INVALID] (0). KHÔNG ném. GỌI TỪ THREAD NỀN (reflection + HAL, không dùng ở main).
     */
    fun readLevel(ctx: Context): Int = runCatching {
        val app = ctx.applicationContext
        val dev = BydHal.device(BydHal.PM2P5, BydHal.systemBypassContext(), BydHal.bypass(app))
            ?: return@runCatching Pm25Filter.INVALID
        val arr = dev.javaClass.getMethod("getPM2p5Level").invoke(dev) as? IntArray
        arr?.getOrNull(0) ?: Pm25Filter.INVALID
    }.getOrDefault(Pm25Filter.INVALID)

    /** Bật lọc thật lên HAL (nền của [enable]/[applyOnStart]). Toàn bộ degrade-safe. */
    private fun enableNow(app: Context) {
        runCatching {
            if (!Prefs.pm25FilterEnabled(app)) return   // owner tắt trong lúc chờ delay
            val acDev = BydHal.device(BydHal.AC, BydHal.systemBypassContext(), BydHal.bypass(app))
            if (acDev == null) {
                Log.i(TAG, "AcDevice null (off-car / no HAL) — bỏ bật lọc")
                return@runCatching
            }
            acInt(acDev, "setAutoCleanAirState", 1)               // ĐƯỜNG CHÍNH: bật lọc-liên-tục (proven on-car rc=0)
            acInt(acDev, "enablePurificationFunctionPrompt", 0, bestEffort = true)   // best-effort tắt popup (trim có thể không hỗ trợ)
            val level = readLevel(app)
            Log.i(TAG, "read level=$level (${Pm25Filter.levelLabelEn(level)})")
            if (Pm25Filter.isDirty(level)) acInt(acDev, "setQuickCleanAirState", 1)   // đang bẩn → lọc ngay
            Log.i(TAG, "bật lọc PM2.5 xong (autoClean=1, prompt=best-effort)")
        }.onFailure { Log.w(TAG, "bật lọc thất bại (degrade-safe, bỏ qua)", it) }
        startPollLoop(app)   // duy trì tự-lọc: poll định kỳ, bụi ≥ ngưỡng → lọc-ngay (bug on-car 2026-09-08)
    }

    /** Tắt lọc + khôi phục popup (nền của [disable]). Toàn bộ degrade-safe. */
    private fun disableNow(app: Context) {
        runCatching {
            val acDev = BydHal.device(BydHal.AC, BydHal.systemBypassContext(), BydHal.bypass(app))
            if (acDev == null) {
                Log.i(TAG, "AcDevice null (off-car / no HAL) — bỏ tắt lọc")
                return@runCatching
            }
            acInt(acDev, "setAutoCleanAirState", 0)               // ĐƯỜNG CHÍNH: tắt lọc-liên-tục
            acInt(acDev, "enablePurificationFunctionPrompt", 1, bestEffort = true)   // best-effort khôi phục popup mặc định
            Log.i(TAG, "tắt lọc PM2.5 xong (autoClean=0, prompt=best-effort)")
        }.onFailure { Log.w(TAG, "tắt lọc thất bại (degrade-safe, bỏ qua)", it) }
    }

    /**
     * Bắn `setQuickCleanAirState(1)` (lọc-ngay CHỦ ĐỘNG) lên AC device — thứ THẬT SỰ lọc trên trim owner (khác
     * `setAutoCleanAirState` chỉ đặt chế độ, on-car 2026-09-08 không tự lọc). Dùng bởi nút "Lọc ngay" ([cleanNow])
     * và vòng [startPollLoop]. GỌI TỪ THREAD NỀN. Degrade-safe: off-car / không HAL → no-op, KHÔNG ném.
     */
    private fun quickClean(app: Context) {
        runCatching {
            val acDev = BydHal.device(BydHal.AC, BydHal.systemBypassContext(), BydHal.bypass(app))
            if (acDev == null) {
                Log.i(TAG, "AcDevice null (off-car / no HAL) — bỏ lọc-ngay")
                return@runCatching
            }
            acInt(acDev, "setQuickCleanAirState", 1)
            Log.i(TAG, "lọc-ngay: setQuickCleanAirState(1) gửi xong")
        }.onFailure { Log.w(TAG, "lọc-ngay thất bại (degrade-safe, bỏ qua)", it) }
    }

    /**
     * Vòng POLL định kỳ khi công tắc BẬT: mỗi [POLL_INTERVAL_MS] đọc mức PM2.5, nếu ≥ ngưỡng ([Pm25Filter.isDirty])
     * thì [quickClean]. Vá bug on-car 2026-09-08 (bụi lên, xe hiện popup mà `setAutoCleanAirState` KHÔNG tự lọc).
     * Guard [polling] + [pollGeneration] (đồng bộ hoá) đảm bảo CHỈ 1 vòng của thế hệ hiện tại, kể cả khi
     * enable/applyOnStart gọi nhiều lần HOẶC race enable↔disable liên tiếp. Tự dừng khi công tắc TẮT
     * ([disable] ++thế hệ) hoặc pref về false. Degrade-safe: đọc/ghi lỗi không ném, không có HAL thì
     * [readLevel] trả INVALID ⇒ không bao giờ "bẩn" ⇒ vòng chỉ ngủ, vô hại (off-car).
     */
    private fun startPollLoop(app: Context) {
        if (!Prefs.pm25FilterEnabled(app)) return
        val myGen: Int
        synchronized(this) {
            if (polling) return
            polling = true
            myGen = ++pollGeneration          // danh tính vòng NÀY
        }
        Thread({
            try {
                while (myGen == pollGeneration && Prefs.pm25FilterEnabled(app)) {
                    runCatching { Thread.sleep(POLL_INTERVAL_MS) }
                    // Thoát nếu bị TẮT (disable ++thế hệ) hoặc pref về false — KHÔNG đọc `polling` (thread mới
                    // có thể vừa bật lại cờ đó cho thế hệ khác); danh tính thế hệ mới là điều kiện đúng.
                    if (myGen != pollGeneration || !Prefs.pm25FilterEnabled(app)) break
                    val level = readLevel(app)
                    if (Pm25Filter.isDirty(level)) {
                        Log.i(TAG, "poll: mức=$level (${Pm25Filter.levelLabelEn(level)}) ≥ ngưỡng → lọc-ngay")
                        quickClean(app)
                    } else {
                        Log.d(TAG, "poll: mức=$level (${Pm25Filter.levelLabelEn(level)}) < ngưỡng → bỏ qua")
                    }
                }
            } finally {
                // CHỈ thế hệ HIỆN TẠI mới được nhả cờ — tránh `finally` của thread cũ xoá cờ của thread mới.
                synchronized(this) { if (myGen == pollGeneration) polling = false }
            }
        }, "pm25-filter-poll").start()
    }

    /**
     * Gọi 1 method int-arg trên AC device qua REFLECTION (`getMethod(name, int).invoke(dev, arg)`), bọc
     * `runCatching` RIÊNG: ROM thiếu method / HAL từ chối → log rồi bỏ qua, KHÔNG ném. Method GHI này KHÔNG có
     * trong SDK jar nên KHÔNG gọi biên dịch được — chỉ reflection.
     *
     * [bestEffort] = true cho lời gọi KHÔNG-thiết-yếu (vd `enablePurificationFunctionPrompt` tắt popup): trim
     * không hỗ trợ → rc sentinel từ chối (on-car 2026-09-06: `-2147482645`). Đó KHÔNG PHẢI lỗi và KHÔNG được
     * chặn đường lọc chính ([enableNow] gọi `setAutoCleanAirState` TRƯỚC) — nên log ở mức **DEBUG** (mặc định
     * logcat không hiện), tránh làm owner tưởng lọc hỏng. best-effort=false ⇒ log INFO như cũ (đường chính).
     */
    private fun acInt(acDev: Any, method: String, arg: Int, bestEffort: Boolean = false) {
        val rc = runCatching {
            acDev.javaClass.getMethod(method, Int::class.javaPrimitiveType).invoke(acDev, arg)
        }.getOrElse { BydHal.root(it) }
        if (bestEffort) {
            Log.d(TAG, "$method($arg) rc=$rc (best-effort; trim không hỗ trợ ⇒ bỏ qua, KHÔNG ảnh hưởng lọc)")
        } else {
            Log.i(TAG, "$method($arg) rc=$rc")
        }
    }
}
