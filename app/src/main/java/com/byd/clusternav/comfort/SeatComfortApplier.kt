package com.byd.clusternav.comfort

import android.content.Context
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.modules.hal.BydHal

/**
 * Áp mức làm-mát/sưởi ghế lên HAL qua **`BYDAutoSettingDevice`** ([BydHal.SETTING]) bằng method-TÊN
 * `setSeatVentilatingState(seatID, state)` / `setSeatHeatingState(seatID, state)` — đi ĐƯỜNG REFLECTION
 * SẴN CÓ của ClusterNav ([BydHal.device] + [BydHal.callNamedInt]) như [com.byd.clusternav.body.BodyworkControl].
 *
 * ⚠ SỬA on-car 2026-09-06 (`docs/diagnostics/seat-vietmaploop-oncar-2026-09-06.md`): bản trước ghi raw
 * feature-id (họ `0x431010xx`) qua `BYDAutoAcDevice.set(int[], ev)` → HAL trả `NOT_PROVISIONED` (rc=-2147482648)
 * NGAY CẢ ghế trước. Đường đúng (OEM `com.byd.airconditioning` dùng) = SETTING device + method-tên + seatID
 * **1-based** (1..4) + state **1=Tắt/2=Mức1/3=Mức2**. Đường AC + raw-id + `HAL_VALUE` đã bỏ hẳn.
 *
 * ── Vòng đời ─────────────────────────────────────────────────────────────────────────────────────
 *  • [applyOnStart] — gọi lúc mở app (MainActivity.onCreate) và lúc boot nền (BootSetupService). Nếu công
 *    tắc ghế TẮT → no-op. Nếu BẬT → chạy NỀN, ngủ ~5 s (chờ HAL/cabin sẵn sàng sau khởi động) rồi ghi từng
 *    ghế có mức ≠ Tắt. Chỉ ghi method của mode đang chọn (mát ↔ sưởi loại trừ nhau ở MCU).
 *  • [applyNow] — áp lại MỌI ghế có mức ≠ Tắt cho mode hiện tại (dùng khi ĐỔI CHẾ ĐỘ mát↔sưởi).
 *  • [applySeat] — chạm 1 ghế: ghi NGAY mức mới cho CHÍNH ghế đó, **kể cả Tắt** (state=1). Sửa bug on-car v1.33
 *    (chỉnh ghế về Tắt không tắt được vì đường bulk bỏ qua mức Tắt).
 *
 * ── An toàn (degrade-safe) ───────────────────────────────────────────────────────────────────────
 * Toàn bộ bọc `runCatching`; [BydHal.callNamedInt] cũng KHÔNG BAO GIỜ ném (ROM thiếu method / HAL từ chối →
 * trả chuỗi rc). Off-car / không có HAL → `device()` trả null → log rồi return. Bộ test đầy đủ chạy off-car
 * nên đường này PHẢI không ném. Mỗi lần ghi có [Log] tag "SeatComfort" (method/seatId/state/rc) để owner xác
 * minh trên xe bằng `logcat -s SeatComfort`.
 */
object SeatComfortApplier {

    const val TAG = "SeatComfort"

    /** Trễ ~5 s sau khi start (khớp app tham chiếu) để cabin/HAL sẵn sàng trước khi ghi. */
    const val START_DELAY_MS = 5_000L

    /** Gọi lúc mở app / boot nền. Công tắc TẮT ⇒ no-op. BẬT ⇒ ghi ghế sau ~5 s trên thread nền. */
    fun applyOnStart(ctx: Context) = launch(ctx, START_DELAY_MS, "seat-comfort-start")

    /** Áp lại TẤT CẢ ghế có mức ≠ Tắt cho mode hiện tại (dùng khi ĐỔI CHẾ ĐỘ mát↔sưởi). Công tắc TẮT ⇒ no-op. */
    fun applyNow(ctx: Context) = launch(ctx, 0L, "seat-comfort-now")

    /**
     * Chạm 1 ghế trên sơ đồ (đổi mức) ⇒ ghi NGAY mức mới cho CHÍNH ghế đó, **kể cả Tắt** (state=1), trên thread
     * nền, degrade-safe. Công tắc TẮT ⇒ no-op.
     *
     * ⚠ SỬA on-car v1.33→v1.34: trước đây chạm ghế gọi [applyNow] → [apply] (đường BULK) mà đường bulk `continue`
     * qua mọi ghế mức Tắt (đúng cho apply-on-start: KHÔNG cưỡng bức tắt mọi ghế lúc khởi động) ⇒ chỉnh 1 ghế về
     * **Tắt KHÔNG BAO GIỜ ghi HAL** ⇒ ghế không tắt được (mức 1/2 chạy rc=0 nhưng Tắt vô tác dụng). Đường chạm nay
     * ghi ĐÚNG 1 ghế với `state = stateForLevel(level)` (0→1 Tắt · 1→2 · 2→3), KHÔNG bỏ qua Tắt.
     */
    fun applySeat(ctx: Context, seatIndex: Int, level: Int) {
        if (!Prefs.seatComfortEnabled(ctx)) return
        val app = ctx.applicationContext
        Thread({
            runCatching {
                if (!Prefs.seatComfortEnabled(app)) return@runCatching      // owner tắt ngay sau khi chạm
                val mode = currentMode(app)
                val method = SeatComfort.methodFor(mode)
                val dev = BydHal.device(BydHal.SETTING, BydHal.systemBypassContext(), BydHal.bypass(app))
                if (dev == null) {
                    Log.i(TAG, "SettingDevice null (off-car / no HAL) — bỏ ghi ghế $seatIndex, mode=$mode")
                    return@runCatching
                }
                val seatId = SeatComfort.seatId(seatIndex)               // 0-based UI → 1-based HAL
                val state = SeatComfort.stateForLevel(level)             // 0→1(Tắt) · 1→2 · 2→3 — KHÔNG bỏ Tắt
                val rc = BydHal.callNamedInt(dev, method, seatId, state)
                Log.i(TAG, "ghi ghế (chạm): $method(seatId=$seatId, state=$state) [seatIndex=$seatIndex level=$level mode=$mode] $rc")
            }.onFailure { Log.w(TAG, "ghi 1 ghế thất bại (degrade-safe, bỏ qua)", it) }
        }, "seat-comfort-one").start()
    }

    private fun launch(ctx: Context, delayMs: Long, threadName: String) {
        if (!Prefs.seatComfortEnabled(ctx)) return
        val app = ctx.applicationContext
        Thread({
            if (delayMs > 0) runCatching { Thread.sleep(delayMs) }
            apply(app)
        }, threadName).start()
    }

    /** Chế độ ghế hiện tại (mát ↔ sưởi) đọc từ pref; giá trị lạ ⇒ mặc định COOL (an toàn). */
    private fun currentMode(app: Context): SeatComfort.SeatMode =
        if (Prefs.seatComfortMode(app) == SeatComfort.SeatMode.HEAT.ordinal) SeatComfort.SeatMode.HEAT
        else SeatComfort.SeatMode.COOL

    /**
     * Ghi thật lên HAL — đường BULK cho [applyOnStart]/[applyNow]. Chỉ gọi từ thread nền của [launch]. Toàn bộ
     * degrade-safe. **CỐ Ý bỏ qua ghế mức Tắt** (không cưỡng bức tắt mọi ghế lúc khởi động / đổi mode); việc ghi
     * mức Tắt cho MỘT ghế do người dùng chạm là của [applySeat].
     */
    private fun apply(app: Context) {
        runCatching {
            if (!Prefs.seatComfortEnabled(app)) return   // owner tắt trong lúc chờ delay
            val mode = currentMode(app)
            val method = SeatComfort.methodFor(mode)
            val seats = SeatComfort.seatsForModel(isHanModel(app))
            val dev = BydHal.device(BydHal.SETTING, BydHal.systemBypassContext(), BydHal.bypass(app))
            if (dev == null) {
                Log.i(TAG, "SettingDevice null (off-car / no HAL) — bỏ áp ghế, mode=$mode seats=$seats")
                return@runCatching
            }
            var applied = 0
            for (seat in seats) {
                val level = Prefs.seatComfortLevel(app, seat)
                if (level == SeatComfort.LEVEL_OFF) continue          // apply-on-start chỉ BẬT ghế có mức
                val seatId = SeatComfort.seatId(seat)                 // 0-based UI → 1-based HAL
                val state = SeatComfort.stateForLevel(level)          // 0/1/2 → 1/2/3
                val rc = BydHal.callNamedInt(dev, method, seatId, state)
                Log.i(TAG, "ghi ghế: $method(seatId=$seatId, state=$state) [seatIndex=$seat mode=$mode] $rc")
                applied++
            }
            Log.i(TAG, "áp xong: $applied ghế, mode=$mode, seats=$seats")
        }.onFailure { Log.w(TAG, "áp ghế thất bại (degrade-safe, bỏ qua)", it) }
    }

    /** Số ghế theo mẫu xe (2 = Seal / 4 = Han). Off-car / không rõ → 2. Cho UI hiện đúng 2 hay 4 ghế. */
    fun detectSeatCount(ctx: Context): Int = SeatComfort.seatCountForModel(isHanModel(ctx))

    /**
     * Best-effort: xe có phải Han (4 ghế tiện-nghi) không? Thử theo thứ tự, gộp mọi manh mối rồi so [SeatComfort.isHanModel]:
     *  1) BYD HAL statistic device — vài getter mẫu-xe (nếu ROM có);
     *  2) `android.os.SystemProperties.get` vài khoá mẫu/sản-phẩm;
     *  3) `android.os.Build.MODEL`.
     * KHÔNG chặn, KHÔNG ném (mọi bước bọc runCatching). Không manh mối nào chứa "han" ⇒ false ⇒ mặc định
     * Seal (2 ghế) — off-car luôn trả 2.
     */
    fun isHanModel(ctx: Context): Boolean {
        val clues = mutableListOf<String>()
        // 1) BYD HAL statistic/vehicle-type getters (probe tên khả dĩ; ROM không có → bỏ).
        runCatching {
            val dev = BydHal.device(BydHal.STATISTIC, BydHal.systemBypassContext(), BydHal.bypass(ctx.applicationContext))
            if (dev != null) {
                for (g in listOf("getVehicleType", "getCarType", "getVehicleModel", "getCarModel", "getModel")) {
                    BydHal.callGetter(dev, g)?.let { clues.add(it) }
                }
            }
        }
        // 2) System properties (khoá mẫu/sản-phẩm khả dĩ trên IVI BYD).
        for (k in listOf(
            "ro.product.model", "ro.product.name", "ro.product.device",
            "persist.sys.vehicle.model", "ro.byd.vehicle.type", "ro.byd.product.model",
        )) {
            systemProp(k)?.let { clues.add(it) }
        }
        // 3) Build.MODEL.
        runCatching { android.os.Build.MODEL?.let { clues.add(it) } }
        return clues.any { SeatComfort.isHanModel(it) }
    }

    private fun systemProp(key: String): String? = runCatching {
        val c = Class.forName("android.os.SystemProperties")
        (c.getMethod("get", String::class.java).invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
