package com.byd.clusternav

import android.content.Context
import com.byd.clusternav.modules.hal.BydHal

/**
 * REGISTRY lệnh HAL "đặt sẵn" cho probe on-car ([HalProbeReceiver]).
 *
 * ⚠ **vehicleTest-ONLY**: file này nằm ở source set `app/src/vehicleTest/` nên CHỈ được biên dịch/đóng gói vào
 * build type `vehicleTest` (debuggable, dùng chẩn đoán trên xe). Release/debug KHÔNG có class này (xác minh
 * bằng aapt2 trên release APK — không thấy receiver/preset nào). Đây là bề mặt test/ghi-thiết-bị, luật dự án
 * giữ nó tách khỏi release.
 *
 * ── Cách THÊM 1 preset (đúng 1 dòng vào [REGISTRY]) ─────────────────────────────────────────────
 *   "<tên-lệnh>" to named(BydHal.<DEVICE>, "<methodName>", arg0, arg1, …)   // gọi method-TÊN int×N
 *   "<tên-lệnh>" to fid(BydHal.<DEVICE>, 0x<featureId>, <value>)            // ghi RAW feature-id (kiểu T10 cũ)
 * `<DEVICE>` là hằng FQN trong [BydHal]: SETTING | AC | BODYWORK | INSTRUMENT | PM2P5 | SPEED | STATISTIC.
 * Không cần sửa gì khác — [HalProbeReceiver] tự tra [REGISTRY] theo `--es cmd <tên-lệnh>`.
 */
object HalProbePresets {

    /** Khoá device (đối số `--es device`) → FQN [BydHal]. null nếu khoá lạ. */
    fun fqnFor(key: String?): String? = when (key?.trim()?.uppercase()) {
        "SETTING" -> BydHal.SETTING
        "AC" -> BydHal.AC
        "BODYWORK" -> BydHal.BODYWORK
        "INSTRUMENT" -> BydHal.INSTRUMENT
        "PM2P5" -> BydHal.PM2P5
        "SPEED" -> BydHal.SPEED
        "STATISTIC" -> BydHal.STATISTIC
        else -> null
    }

    /**
     * tên-lệnh → hàm bắn (nhận appContext, trả tóm tắt rc). `linkedMapOf` giữ THỨ TỰ khai báo cho help/log.
     * Seed = các lệnh test hay dùng nhất (ghế / cửa sổ / cốp / lọc bụi / nav-screen). Mở rộng: xem KDoc lớp.
     */
    val REGISTRY: Map<String, (Context) -> String> = linkedMapOf(
        "seat-cool-driver" to named(BydHal.SETTING, "setSeatVentilatingState", 1, 3),
        "seat-off-driver" to named(BydHal.SETTING, "setSeatVentilatingState", 1, 1),
        "window-open-lf" to named(BydHal.BODYWORK, "setBodyWindowCtrlState", 1, 1),
        "window-close-lf" to named(BydHal.BODYWORK, "setBodyWindowCtrlState", 1, 0),
        "trunk-open" to named(BydHal.BODYWORK, "setHetchDoorStatus", 1),
        "trunk-close" to named(BydHal.BODYWORK, "setHetchDoorStatus", 2),
        "pm25-on" to named(BydHal.AC, "setAutoCleanAirState", 1),
        "nav-screen-on" to fid(BydHal.SETTING, 0x4C10E015, BydHal.NAV_SCREEN_MODE_ON),
    )

    /** Danh sách tên preset (cho help/log khi gõ sai `cmd`). */
    fun names(): String = REGISTRY.keys.joinToString(", ")

    /** Bắn 1 preset theo tên → tóm tắt rc; null nếu tên không có trong [REGISTRY]. Gọi từ thread nền. */
    fun fire(appCtx: Context, cmd: String): String? = REGISTRY[cmd]?.invoke(appCtx)

    /** Factory: gọi method-TÊN int×N trên device [fqn] (degrade-safe qua [BydHal.callNamedInt]). */
    private fun named(fqn: String, method: String, vararg args: Int): (Context) -> String = { app ->
        val dev = BydHal.device(fqn, BydHal.systemBypassContext(), BydHal.bypass(app))
        if (dev == null) "device null (off-car / no HAL)" else BydHal.callNamedInt(dev, method, *args)
    }

    /** Factory: ghi RAW feature-id (kiểu T10 cũ) qua [BydHal.setInt] (degrade-safe). */
    private fun fid(fqn: String, id: Int, value: Int): (Context) -> String = { app ->
        val dev = BydHal.device(fqn, BydHal.systemBypassContext(), BydHal.bypass(app))
        if (dev == null) "device null (off-car / no HAL)"
        else runCatching { "rc=${BydHal.setInt(dev, id, value)}" }.getOrElse { BydHal.root(it) }
    }
}
