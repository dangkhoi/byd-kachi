package com.byd.clusternav.automation

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.AppContainer
import com.byd.clusternav.Prefs
import com.byd.clusternav.launcher.BydHalGateway
import com.byd.clusternav.launcher.HalBindingTable
import com.byd.clusternav.launcher.automation.RainDefrostCadence
import com.byd.clusternav.launcher.automation.RainDefrostChoice
import com.byd.clusternav.launcher.automation.RainDefrostGlasses
import com.byd.clusternav.launcher.automation.RainGlass
import com.byd.clusternav.launcher.automation.RainGlassIo
import com.byd.clusternav.launcher.automation.RainGlassLast
import com.byd.clusternav.launcher.automation.RainGlassLastResults
import com.byd.clusternav.launcher.automation.RainGlassMemory
import com.byd.clusternav.launcher.automation.RainGlassOutcome
import com.byd.clusternav.rainDefrostChoice
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * ═══ AUTOMATION #1 · MƯA → TỰ SẤY KÍNH · PHẦN CHẠM XE ════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R1 + §V8 (hai kính độc lập). Mọi **luật** nằm ở `:core`
 * ([RainDefrostGlasses] · `RainDefrostOwner` · `RainDefrostPolicy`), kể cả thứ tự đọc/quyết/ghi của một nhịp; lớp
 * này chỉ cài [RainGlassIo] bằng những việc chỉ tầng Android làm được: **đọc lựa chọn**, **đọc cảm biến mưa**,
 * **đọc trạng thái sấy**, **ghi nút sấy**.
 *
 * ## Bốn đường, mỗi đường đi qua hạ tầng ĐÃ CÓ (không mở reflection mới)
 *  1. **Lựa chọn** — [Prefs.rainDefrostChoice] (3 khoá cũ ⇒ `RainDefrostChoice.fromKeys`, spec V8 · D2).
 *  2. **Đọc mưa** — feature-id `SETTING_FRONT_RAIN_WIPER_SPEED` trên `BYDAutoSettingDevice`, qua
 *     [BydHalGateway.featureGet]. Không có datum nào cho nó (nó không phải một ô hiển thị), nên đây là **chỗ
 *     duy nhất** của automation đọc feature-id thô — cùng công thức mà cầu kiểm thử `hal getid` dùng.
 *  3. **Đọc sấy** — `carControl.readState([controlOf] kính)`, tức đi qua `ControlDef.readKey`
 *     (`defrost_front_state` → `BYDAutoAcDevice.getAcDefrostState(1)`, `defrost_rear_state` → `(2)`). Mỗi kính
 *     đọc **chính nó** — V7 chỉ đọc một mỏ neo (`pick.first()`) rồi áp quyết định của nó cho cả hai (spec V8 · K1).
 *     ⚠ **Phải đọc TỪ XE**, không phải một cờ RAM: cờ RAM không thấy người lái bấm nút sấy trên màn xe.
 *  4. **Ghi sấy** — `carControl.toggle([controlOf] kính, on)`, tức đúng cửa mà một cú chạm dùng
 *     (`AC_DEFROST_FRONT_STATE_SET` / `AC_DEFROST_REAR_STATE_SET`). rc của **từng** kính quay về `:core`
 *     (`RainDefrostGlasses.settle`). Không tự dựng lệnh HAL thứ hai: dự án đã trả giá cho đúng việc ấy ở cặp
 *     `lock`/`door` (hai nhãn đối nghịch gửi cùng một byte).
 *
 * ## Ký ức là cờ RAM, và [memory] là ĐÚNG MỘT bản cho cả tiến trình
 * Mỗi kính một `RainDefrostState` trong [RainGlassMemory] (không lưu bền — lý do ở KDoc `RainDefrostState`). Hai
 * bản sao = hai câu trả lời cho *"được phép tắt hộ không"*, và cái sai sẽ tắt một cái sấy người lái đang cần.
 *
 * ## kachi-automation V8.1 — nhịp + tự chẩn đoán (RAM, cùng tiến trình với Cài đặt)
 * [tickIfDue] gác nhịp bằng `RainDefrostCadence` (đầu vòng chạy ngay · đọc lỗi thử lại 60 s ≤ 5 lần · sàn 60 s · 5′)
 * và ghi kết quả từng kính vào `RainGlassLastResults`; màn Cài đặt đọc qua [lastOf] + [nextCheckWallMs] (qua cầu) —
 * không khoá bền mới, không lượt HAL nào từ luồng vẽ.
 *
 * ## Degrade-safe
 * Off-car: `featureGet` → `null` ⇒ `plausible` → `null` ⇒ không đọc kính nào, `Leave`, ký ức không đổi. Mọi lời
 * gọi HAL bọc `runCatching` (hợp đồng [RainGlassIo]: bốn cửa đọc mưa/đọc kính/ghi kính/log không ném; riêng
 * `choice` đọc prefs mà ném thì cả nhịp bỏ, ký ức giữ nguyên) và cả nhịp bọc thêm một lớp ở [tick] ⇒ KHÔNG BAO
 * GIỜ ném ra ngoài (bộ test đầy đủ chạy off-car nên đường này phải không ném).
 */
object RainDefrostApplier {

    const val TAG = "KachiRainDefrost"

    /**
     * Tên HẰNG của feature-id đọc cảm biến mưa — bind theo **tên**, không dán số.
     *
     * [ĐO xe 2026-09-20] id là `1196425250`, nhưng `BYDAutoFeatureIds` gán giá trị trong `static {}` theo
     * `isCanFD`/`isToyota` (R11) ⇒ con số chỉ đúng cho một cấu hình. Số đo được ghi ở [MEASURED_ID] để đối chiếu
     * và làm đường lùi khi ROM không có bảng tên.
     */
    const val RAIN_CONST = "BYDAutoFeatureIds.Setting.SETTING_FRONT_RAIN_WIPER_SPEED"

    /** [ĐO xe 2026-09-20] giá trị của [RAIN_CONST] trên xe owner — đường lùi khi tra tên thất bại. */
    const val MEASURED_ID = 1196425250

    /** Device phơi cảm biến mưa — [ĐO] `docs/diagnostics/oncar-1.84-session-2026-09-20.md` §1. */
    const val RAIN_DEVICE = "android.hardware.bydauto.setting.BYDAutoSettingDevice"

    /** Mã nút sấy TRƯỚC trong `ControlRegistry` (đường đọc + đường ghi đều tra từ đây). */
    const val CTL_FRONT = "defrost"

    /**
     * Mã nút sấy SAU (kính sau + gương chiếu hậu trên xe này).
     *
     * ⚠ V7 (owner 2026-09-25) **đảo một quyết định của 1.85**: R1.3 chốt *"bật cả hai, không chỉ kính lái"*, nay
     * người dùng CHỌN — trước · sau · cả hai. Lý do: hai cái sấy có hai chi phí khác nhau (kính sau + gương ăn
     * điện liên tục) và owner muốn dùng riêng được từng cái. kachi-automation V8 (owner 2026-09-30) đi nốt nửa còn
     * lại: hai lựa chọn **độc lập** — mỗi kính tự đọc, tự quyết, tự ghi, tự giữ chủ quyền ([controlOf]).
     */
    const val CTL_REAR = "defrost_rear"

    /** Ký ức từng kính — xem KDoc lớp. Luồng nền của [AutomationService] ghi, luồng vẽ (Cài đặt) [forget]. */
    private val memory = RainGlassMemory()

    /** Số nhịp — có mặt trong mọi dòng log của nhịp (D7: `LogLineThrottle` không bao giờ gộp hai nhịp). */
    private val seq = AtomicLong()

    /** Số lượt đổi lựa chọn — cùng lẽ [seq] cho dòng *"đổi lựa chọn"*. */
    private val changes = AtomicLong()

    /** R-V8.5 — lựa chọn vừa đổi ⇒ vòng của [AutomationService] chạy nhịp mưa ở lượt thức kế (≤ 60 s). */
    private val due = AtomicBoolean(false)

    /**
     * kachi-automation V8.1 · R-V8.8 — nhịp mưa: đầu vòng chạy NGAY, đọc lỗi thử lại 60 s (≤ 5 lần), sàn 60 s, còn lại
     * 5′ theo thời gian trôi. Một bản cho cả tiến trình (spec V8.1 · D14): dòng tình trạng cần giờ nhịp kế, và sàn phải
     * giữ cả khi vòng dựng lại. Chu kỳ lấy từ ĐÚNG hằng của động cơ (không dán số thứ hai).
     */
    private val cadence = RainDefrostCadence(
        periodMs = AutomationService.TICK_MS * AutomationService.RAIN_EVERY_TICKS,
        retryMs = AutomationService.TICK_MS,
    )

    /** kachi-automation V8.1 · R-V8.7 — kết quả nhịp gần nhất TỪNG kính, cho dòng tình trạng ở Cài đặt (RAM, D10). */
    private val lastResults = RainGlassLastResults()

    /** Mã nút `ControlRegistry` của một kính — đọc VÀ ghi của một làn đều tra ở đây (một kính, một mã). */
    fun controlOf(glass: RainGlass): String = when (glass) {
        RainGlass.FRONT -> CTL_FRONT
        RainGlass.REAR -> CTL_REAR
    }

    /** Lựa chọn HIỆU LỰC (V8) — cổng duy nhất, `AutomationService.anyEnabled` cũng hỏi đây. */
    fun choice(app: Context): RainDefrostChoice = Prefs.rainDefrostChoice(app)

    /** Quên ký ức của MỘT kính — người dùng vừa đổi ô đó (V8 · D4/D8: không ghi xe, kính kia không bị chạm). */
    fun forget(glass: RainGlass) = memory.forget(glass)

    /**
     * Quên cả hai — động cơ tắt hẳn (`AutomationService.sync`, nhánh không còn việc).
     *
     * Không có bước này thì: tắt automation lúc đang mưa (sấy do nó bật, `owned = true`) → bật lại khi trời đã
     * khô → nhịp đầu tiên thấy `owned` còn `true` và **tắt** cái sấy mà người lái có thể đã tự bật giữa lúc ấy.
     */
    fun forgetAll() = memory.forgetAll()

    /** Xin một nhịp mưa sớm (R-V8.5) — cầu gọi sau khi đổi lựa chọn. */
    fun requestSoon() = due.set(true)

    /** Vòng gọi mỗi lượt thức: `true` một lần cho mỗi [requestSoon] (đọc-và-xoá nguyên tử). */
    fun consumeDue(): Boolean = due.getAndSet(false)

    /** Một dòng log cho mỗi lượt đổi lựa chọn (R-V8.6) — dựng ở `:core` để có test. */
    fun logChoice(after: RainDefrostChoice, glass: RainGlass) {
        Log.i(TAG, RainDefrostGlasses.choiceLine(changes.incrementAndGet(), after, glass))
    }

    /** Vòng của [AutomationService] vừa bắt đầu ⇒ nhịp mưa kế là NGAY (R-V8.8; [ĐO git] `79be642` bắt chờ uptime 5′). */
    fun loopStarted() = cadence.loopStarted()

    /**
     * Mỗi lượt thức của vòng gọi đây, KHÔNG điều kiện. Tới nhịp ⇒ chạy [tick], ghi kết quả từng kính cho dòng tình
     * trạng, báo nhịp biết đọc có đủ không (đọc lỗi ⇒ thử lại 60 s, có trần).
     *
     * [consumeDue] là **đối số** của `shouldRun` ⇒ cờ được đọc-và-xoá MỖI lượt, không có đường tắt `||` nào bỏ qua nó;
     * yêu cầu chưa qua sàn 60 s thì [RainDefrostCadence] GIỮ tới lượt kế (R-V8.5 · D14).
     *
     * @param nowMs `elapsedRealtime()` của lượt thức — đồng hồ đơn điệu, nhịp không đếm lượt thức (bất biến `79be642`).
     * @return `true` nếu nhịp mưa đã chạy ở lượt này.
     */
    fun tickIfDue(ctx: Context, nowMs: Long): Boolean {
        if (!cadence.shouldRun(nowMs, consumeDue())) return false
        val outcomes = tick(ctx)
        lastResults.record(System.currentTimeMillis(), outcomes)
        cadence.ran(nowMs, RainDefrostCadence.halReady(outcomes))
        return true
    }

    /** Kết quả nhịp gần nhất của ĐÚNG [glass] (`null` = chưa kiểm lần nào từ lúc tiến trình sống) — RAM, không HAL. */
    fun lastOf(glass: RainGlass): RainGlassLast? = lastResults.of(glass)

    /**
     * Giờ (đồng hồ tường) của nhịp mưa kế, `null` nếu không biết — chỉ để HIỆN (R-V8.7). Tính cả cờ [due] vòng chưa
     * đọc tới (đọc, KHÔNG xoá): màn Cài đặt hỏi ngay sau cú chạm, lúc cờ còn nằm đây.
     */
    fun nextCheckWallMs(): Long? {
        val elapsed = SystemClock.elapsedRealtime()
        return cadence.nextDueMs(elapsed, due.get())?.let { RainDefrostCadence.toWallMs(it, elapsed, System.currentTimeMillis()) }
    }

    /**
     * MỘT nhịp đánh giá. GỌI TỪ THREAD NỀN. Toàn bộ trình tự (chụp ký ức → lựa chọn → đọc mưa một lần → mỗi kính
     * một làn) ở [RainDefrostGlasses.tick]; ở đây chỉ cài I/O.
     *
     * @return một kết quả cho mỗi kính đã chọn (rỗng = không chọn gì / nhịp lỗi) — cho nhật ký/chẩn đoán.
     */
    fun tick(ctx: Context): List<RainGlassOutcome> {
        val app = ctx.applicationContext
        return runCatching { RainDefrostGlasses.tick(seq.incrementAndGet(), memory, HalIo(app)) }.getOrElse {
            Log.w(TAG, "nhịp mưa→sấy lỗi (degrade-safe, bỏ qua)", it)
            emptyList()
        }
    }

    /** Cài [RainGlassIo] bằng prefs + HAL. Đọc và ghi của một kính cùng đi qua [controlOf] của CHÍNH kính đó. */
    private class HalIo(private val app: Context) : RainGlassIo {
        override fun choice(): RainDefrostChoice = RainDefrostApplier.choice(app)
        override fun readRain(): Int? = RainDefrostApplier.readRain(app)
        override fun readGlass(glass: RainGlass): Boolean? = readDefrost(app, controlOf(glass))
        override fun writeGlass(glass: RainGlass, on: Boolean): Boolean = write(app, controlOf(glass), on)
        override fun log(line: String) {
            Log.i(TAG, line)
        }
    }

    /**
     * Đọc cảm biến mưa → số thô, `null` nếu không đọc được / sentinel.
     *
     * Tra id theo **tên hằng** trước (`featureIdByName`), lùi về [MEASURED_ID] khi ROM không có bảng tên — đường
     * lùi ấy là lý do con số vẫn phải có mặt trong mã.
     */
    private fun readRain(app: Context): Int? = runCatching {
        val gw = BydHalGateway(app)
        val id = gw.featureIdByName(RAIN_CONST) ?: MEASURED_ID
        val raw = gw.featureGet(RAIN_DEVICE, id) ?: return@runCatching null
        // Sentinel (`NOT_PROVISIONED` / `INVALID`) là một con số THẬT nếu chỉ `toInt()` — và nó lớn hơn ngưỡng
        // mưa, tức nó sẽ bật sấy giữa trời nắng. Lọc bằng chính bộ lọc của `:core`, không tự so tay.
        if (HalBindingTable.rawIsSentinel(raw)) {
            Log.d(TAG, "mưa: sentinel ($raw) — feature không có trên trim này")
            return@runCatching null
        }
        HalBindingTable.coerceInt(raw)
    }.getOrNull()

    /** Nút sấy [controlId] đang bật? Đọc **từ xe** qua `readKey` của nút. `null` = chưa đọc được. */
    private fun readDefrost(app: Context, controlId: String): Boolean? = runCatching {
        AppContainer.get(app).carControl.readState(controlId)?.let { it != 0 }
    }.getOrNull()

    /**
     * Ghi MỘT nút sấy. `false` = lệnh không tới xe (không có cổng điều khiển, HAL ném, hoặc rc hỏng) — `:core`
     * nhả chủ quyền của đúng kính này (`RainDefrostGlasses.settle`). Kết quả vào dòng log của làn (`ghi=ok|hỏng`).
     */
    private fun write(app: Context, controlId: String, on: Boolean): Boolean {
        val control = runCatching { AppContainer.get(app).carControl }.getOrNull() ?: run {
            Log.i(TAG, "không có cổng điều khiển (off-car) — bỏ ghi $controlId")
            return false
        }
        return runCatching { control.toggle(controlId, on) }.getOrDefault(false)
    }
}
