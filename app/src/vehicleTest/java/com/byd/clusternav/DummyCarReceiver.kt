package com.byd.clusternav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.byd.clusternav.launcher.CarStatus
import com.byd.clusternav.launcher.CarStatusTap
import com.byd.clusternav.launcher.ControlDef
import com.byd.clusternav.launcher.CtlWriteTap
import com.byd.clusternav.launcher.LampLive

/**
 * ═══ 3.01 R20 — SỐ GIẢ cho máy ảo để soát canh lề/co giãn widget (vehicleTest-ONLY) ═══════════════════════════════════
 *
 * Owner 13/10: *"thử dummy số lên đi để fix"*. Off-car mọi ô là `"—"`, nên ô hẹp/lệch chỉ lộ ra với số THẬT dài. Receiver
 * này cắm [CarStatusTap] bằng một bộ số thực tế (dài nhất thường gặp: `45678 km`, `123.4 km`, `18.2`, `1.42 h`…).
 *
 * ⚠ Chỉ nằm ở source set `app/src/vehicleTest/` + manifest của nó ⇒ bản release/debug KHÔNG có (bài canh
 * `DummyCarTapContractTest`). Đè lên MỌI trường kể cả trên xe thật nếu ai bắn lệnh — vì vậy chỉ dùng trên máy ảo; tắt bằng
 * `--ez on false`, hoặc tự hết khi tiến trình chết (cờ nằm trong RAM).
 *
 * `adb shell am broadcast -a com.byd.clusternav.vehicletest.DUMMY_CAR -p <pkg> --ez on true|false [--ei speed 0] [--es gear P] [--ez turn_l true] [--ez turn_r true]`
 * POLISH2 (owner 13/10 *"đèn ban ngày không tắt được"* trên máy ảo): xe giả NHẠI lệnh ghi ([CtlWriteTap], móc ở đường ghi duy
 * nhất `ControlWriteFlow.write`) — đèn pha/ban ngày, từng kính + cả cụm (mở hẳn/hé/đóng theo [ControlDef.verbLevel]) đổi trạng
 * thái giả như xe thật đổi.
 * 3.1.6 · R32 — CỐP giả (`tailgate_status` + số nút `trunk`): `[--es trunk open|closed|unknown]` (mặc định đóng). Lệnh mở/đóng của
 * Kachi được nhại với ĐỘ TRỄ như [ĐO logcat xe 14/10]: mở ⇒ đọc 1 sau [TRUNK_OPEN_MS], đóng ⇒ đọc 0 sau [TRUNK_CLOSE_MS] (chốt khoá).
 * Thử ca "đóng bằng nút vật lý" (không qua Kachi): `--ez trunk_only true --es trunk closed` — CHỈ đổi cốp, giữ mọi số giả khác.
 *   `[--es doors lf,rr]` `[--ez sunshade true]` (có rèm che nắng — mặc định KHÔNG) (cửa MỞ; mặc định đóng hết) `[--es lights low,high,drl,ffog,rfog,side]` (đèn SÁNG; vắng ⇒ bộ mặc định).
 */
class DummyCarReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        if (intent.getBooleanExtra("trunk_only", false)) {   // R32 — "nút vật lý trên cốp": chỉ đổi cốp, không reset gì khác
            trunk = trunkOf(intent.getStringExtra("trunk")); Log.i(TAG, "dummy trunk (vật lý) ⇒ $trunk"); CarStatusTap.set(::fill); return
        }
        val on = intent.getBooleanExtra("on", true)
        // 3.01 cổng chạm cốp (owner 13/10): `--ei speed 0` = xe đứng · `--es gear P|D` — để thử chặn/cho mở cốp trên máy ảo.
        speed = intent.getIntExtra("speed", DEFAULT_SPEED)
        gear = intent.getStringExtra("gear") ?: "D"
        // 3.01 POLISH (owner 13/10 *"đèn xinhan có nhấp nháy theo xe không?"*) — bật xi-nhan giả để thử nháy tại chỗ trên bảng Đèn.
        val turnL = intent.getBooleanExtra("turn_l", false)
        val turnR = intent.getBooleanExtra("turn_r", false)
        // 3.01 R25 (owner 13/10 "cửa mở nó có mở ra không") — mở cửa / đổi đèn giả để thử biến thể cửa-mở + đèn sáng của xe 3D.
        doors = intent.getStringExtra("doors")?.split(',')?.map { it.trim() }?.toSet() ?: emptySet()
        // Đồ tuỳ chọn (rèm che nắng): KHÔNG bịa mặc định — xe thật không có thì hàng/hình rèm không được hiện (owner 13/10).
        sunshade = intent.getBooleanExtra("sunshade", false)
        lamps = intent.getStringExtra("lights")?.split(',')?.map { it.trim() }?.toSet()
        windows = DEFAULT_WINDOWS.toMutableMap()
        trunk = trunkOf(intent.getStringExtra("trunk") ?: "closed")
        CarStatusTap.set(if (on) ::fill else null)
        CtlWriteTap.set(if (on) ::echo else null)
        // 3.01 TURN: xi-nhan giả đi ĐÚNG đường của xi-nhan thật (hộp socket `LampLive`), không qua `fill` — để máy ảo
        // thử chính dây bảng Đèn dùng trên xe. Tắt số giả ⇒ xoá (như đứt socket ⇒ lùi về số đọc).
        if (on) LampLive.shared.publish(turnL, turnR) else LampLive.shared.clear()
        Log.i(TAG, "dummy car status on=$on speed=$speed gear=$gear turn_l=$turnL turn_r=$turnR doors=$doors trunk=$trunk lights=${lamps ?: "default"} sunshade=$sunshade")
    }

    companion object {
        const val ACTION = "com.byd.clusternav.vehicletest.DUMMY_CAR"
        private const val TAG = "DummyCar"
        private const val DEFAULT_SPEED = 112
        @Volatile private var speed = DEFAULT_SPEED
        @Volatile private var gear = "D"
        @Volatile private var doors: Set<String> = emptySet()
        @Volatile private var lamps: Set<String>? = null
        @Volatile private var sunshade = false
        /** R32 — cốp giả: `true` mở · `false` đóng · `null` = đọc không ra (255 trên xe). */
        @Volatile private var trunk: Boolean? = false
        private fun trunkOf(s: String?): Boolean? = when (s) { "open" -> true; "unknown" -> null; else -> false }
        /** [ĐO logcat xe 14/10] mở: sự kiện `0x2940001A = 1` +283 ms · đóng: `= 0` +5,8 s sau lệnh (chốt khoá). */
        private const val TRUNK_OPEN_MS = 300L
        private const val TRUNK_CLOSE_MS = 5_800L
        private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
        private fun lit(k: String, def: Boolean) = lamps?.contains(k) ?: def
        /** Mức kính giả theo vị trí (%), mặc định một bộ lệch nhau để soát chấm; lệnh kính ghi đè. */
        private val DEFAULT_WINDOWS = mapOf("lf" to 0, "rf" to 35, "lr" to 100, "rr" to 0)
        @Volatile private var windows: Map<String, Int> = DEFAULT_WINDOWS
        /** Đèn lệnh nhại được → khoá trong `--es lights` (cùng tên trường giả ở [fill]). */
        private val LAMP_KEYS = mapOf("headl" to "high", "drl" to "drl")   // `headl` đi cặp ô đọc `light_high_beam` (cùng icon — GroupIconBoard)
        // Đèn vị trí mặc định TẮT (POLISH2): trên Seal đèn 7/8 sáng theo DRL HOẶC vị trí (bảng đèn AVM) — vị trí bật sẵn thì tắt DRL
        // trên máy ảo không thấy gì đổi (owner 13/10 "không tắt được"). Muốn thử vị trí: `--es lights low,drl,side`.
        private val DEFAULT_LIT = setOf("low", "drl")

        /**
         * Nhại MỘT lệnh ghi [def] với tham số chính [primary] (TOGGLE 1 = bật/mở · 0 = tắt/đóng; BUTTON đóng-hết = 1 với
         * `verbLevel` 0). Kính: hậu tố vị trí ⇒ kính đó, không hậu tố ⇒ cả bốn; mức = `verbLevel` khi bật, 0 khi tắt.
         */
        fun echo(def: ControlDef, primary: Int) {
            val on = primary > 0
            val lamp = LAMP_KEYS[def.id]
            when {
                lamp != null -> lamps = (lamps ?: DEFAULT_LIT).let { if (on) it + lamp else it - lamp }
                def.id.startsWith("win") -> {
                    val level = if (on) def.verbLevel ?: 100 else 0
                    val pos = def.id.substringAfterLast('_').takeIf { it in DEFAULT_WINDOWS }
                    windows = windows.mapValues { (k, v) -> if (pos == null || k == pos) level else v }
                }
                // 3.01 R28 — ghế (SELECT: chỉ số mức) + nhiệt/gió/lấy gió (STEP mức · TOGGLE 0/1) của hàng chân + icon ghế xe 3D.
                def.id in SEATS -> seats = seats + (def.id to primary)
                def.id in CLIMATE -> climate = climate + (def.id to primary)
                def.id == "trunk" -> {   // R32 — nhại cốp với độ trễ đo được (đóng đọc 0 chỉ khi chốt khoá)
                    main.postDelayed({ trunk = on; Log.i(TAG, "nhại trunk ⇒ $on (trễ)"); CarStatusTap.set(::fill) }, if (on) TRUNK_OPEN_MS else TRUNK_CLOSE_MS)
                    return
                }
                else -> return
            }
            Log.i(TAG, "nhại ${def.id} [$primary] ⇒ lights=$lamps windows=$windows")
            CarStatusTap.set(::fill)   // đẩy lại một lượt đổ số
        }

        /** R28 — mức ghế giả (chỉ số lựa chọn) · số nút khí hậu giả (`readState` theo mã nút). Ghế lái đang mát mức 1. */
        @Volatile private var seats: Map<String, Int> = mapOf("seatc" to 1, "seath" to 0, "seatc_r" to 0, "seath_r" to 0)
        @Volatile private var climate: Map<String, Int> = mapOf("temp" to 22, "fan" to 4, "recirc" to 0, "ac_auto" to 0)
        private val SEATS = setOf("seatc", "seath", "seatc_r", "seath_r")
        private val CLIMATE = setOf("temp", "fan", "recirc", "ac_auto")
        private fun raw(id: String): Int? = seats[id]?.let { com.byd.clusternav.launcher.ControlLevels.rawForLevel(id, it) }

        /** Bộ số giả — giữ nguyên trường không nêu (đọc được thì thôi). */
        fun fill(s: CarStatus): CarStatus = s.copy(
            energy = s.energy.copy(
                soc = 78, evRangeKm = 412, fuelRangeKm = 635, odometerKm = 45678, motorPowerKw = 125, sohPct = 97,
                fuelPct = 64, tripKm = 123.4, tripHours = 1.42, consumption50 = 18.2, volt12v = 13.8,
            ),
            drivetrain = s.drivetrain.copy(speedKmh = speed, gear = gear),
            climate = s.climate.copy(
                pm25Level = 2, pm25ValueUgm3 = 35, pm25OutsideUgm3 = 58, pm25Online = true, cabinTempC = 26,
                outsideTempC = 33, acOn = true, fanLevel = 4, recircOn = false, anionOn = true, setTempC = 22,
                defrostFrontOn = false, defrostRearOn = true,
                seatVentRaw = raw("seatc"), seatHeatRaw = raw("seath"), seatVentRRaw = raw("seatc_r"), seatHeatRRaw = raw("seath_r"),
            ),
            controls = (s.controls + climate).let { c -> trunk?.let { c + ("trunk" to if (it) 1 else 0) } ?: (c - "trunk") },
            tyres = s.tyres.copy(
                pFlKpa = 245.0, pFrKpa = 248.0, pRlKpa = 239.0, pRrKpa = 251.0, tFlC = 38, tFrC = 39, tRlC = 36, tRrC = 37,
            ),
            body = s.body.copy(
                windowLfPct = windows["lf"], windowRfPct = windows["rf"], windowLrPct = windows["lr"], windowRrPct = windows["rr"], doorLfOpen = "lf" in doors, doorRfOpen = "rf" in doors,
                doorLrOpen = "lr" in doors, doorRrOpen = "rr" in doors, tailgateOpen = trunk, sunshadePct = if (sunshade) 50 else s.body.sunshadePct, sunroofOpen = false,
            ),
            lights = s.lights.copy(
                lowBeam = lit("low", true), highBeam = lit("high", false), frontFog = lit("ffog", false), drl = lit("drl", true),
                headlightMode = 1, rearFog = lit("rfog", false), sideLight = lit("side", false),
            ),
        )
    }
}
