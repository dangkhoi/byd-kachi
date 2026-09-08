package com.byd.clusternav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.byd.clusternav.modules.hal.BydHal

/**
 * PROBE HAL ON-CAR — bắn lệnh HAL nhanh qua adb để RE + kiểm tra trên xe, KHÔNG cần rebuild/mở app.
 *
 * ⚠ **vehicleTest-ONLY**: đăng ký trong `app/src/vehicleTest/AndroidManifest.xml` và class nằm ở source set
 * `app/src/vehicleTest/` → CHỈ có trong build type `vehicleTest` (debuggable). Release/debug KHÔNG export
 * receiver này (xác minh bằng aapt2 trên release APK). Đây là bề mặt ghi-thiết-bị, giữ tách khỏi release.
 *
 * Action: `com.byd.clusternav.vehicletest.HAL_PROBE`. BA CHẾ ĐỘ (ưu tiên theo thứ tự):
 *  1) **PRESET**  — `--es cmd <tên>`  → tra [HalProbePresets.REGISTRY] rồi bắn (vd `window-open-lf`).
 *  2) **NAMED**   — `--es device <KEY> --es method <tên> [--ei a0 .. --ei a3 ..]`
 *                   → resolve device rồi [BydHal.callNamedInt] method int×N (số arg = số a0..a3 liên tiếp có).
 *  3) **RAW FID** — `--es device <KEY> --es fid 0x<id> --ei val <v>` → [BydHal.setInt] (ghi raw feature-id).
 * `<KEY>` = SETTING | AC | BODYWORK | INSTRUMENT | PM2P5 | SPEED | STATISTIC (xem [HalProbePresets.fqnFor]).
 *
 * Chạy trên THREAD NỀN (HAL + reflection off main); mọi thứ bọc `runCatching` → KHÔNG BAO GIỜ crash. Kết quả
 * ra `logcat -s HalProbe`. Xem `docs/diagnostics/oncar-hal-probe.md`.
 */
class HalProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val app = context.applicationContext

        // Snapshot extras trên main thread; HAL/reflection làm ở nền.
        val cmd = intent.getStringExtra("cmd")
        val device = intent.getStringExtra("device")
        val method = intent.getStringExtra("method")
        val fid = intent.getStringExtra("fid")
            ?: if (intent.hasExtra("fid")) intent.getIntExtra("fid", 0).toString() else null
        val value = if (intent.hasExtra("val")) intent.getIntExtra("val", 0) else null
        // Gom a0..a3 LIÊN TIẾP có mặt → đúng arity cho getMethod.
        val args = buildList {
            for (k in ARG_KEYS) if (intent.hasExtra(k)) add(intent.getIntExtra(k, 0)) else break
        }.toIntArray()

        val pending = goAsync()
        Thread({
            try {
                dispatch(app, cmd, device, method, fid, value, args)
            } catch (t: Throwable) {
                Log.w(TAG, "probe crashed (degrade-safe, bỏ qua)", t)
            } finally {
                pending.finish()
            }
        }, "hal-probe").start()
    }

    private fun dispatch(
        app: Context, cmd: String?, device: String?, method: String?, fid: String?, value: Int?, args: IntArray,
    ) {
        when {
            !cmd.isNullOrBlank() -> {
                val rc = HalProbePresets.fire(app, cmd)
                if (rc == null) Log.w(TAG, "preset không rõ '$cmd' — có: ${HalProbePresets.names()}")
                else Log.i(TAG, "preset=$cmd $rc")
            }
            !device.isNullOrBlank() && !method.isNullOrBlank() -> fireNamed(app, device, method, args)
            !device.isNullOrBlank() && fid != null -> fireFid(app, device, fid, value)
            else -> Log.w(TAG, "thiếu tham số: cần `cmd`, HOẶC `device`+`method`(+a0..a3), HOẶC `device`+`fid`+`val`")
        }
    }

    private fun fireNamed(app: Context, device: String, method: String, args: IntArray) {
        val fqn = HalProbePresets.fqnFor(device)
        if (fqn == null) { Log.w(TAG, "device không rõ '$device' — có: $DEVICE_KEYS"); return }
        val dev = BydHal.device(fqn, BydHal.systemBypassContext(), BydHal.bypass(app))
        if (dev == null) { Log.i(TAG, "named device=$device null (off-car / no HAL)"); return }
        val rc = BydHal.callNamedInt(dev, method, *args)
        Log.i(TAG, "named device=$device method=$method args=[${args.joinToString()}] $rc")
    }

    private fun fireFid(app: Context, device: String, fid: String, value: Int?) {
        val fqn = HalProbePresets.fqnFor(device)
        if (fqn == null) { Log.w(TAG, "device không rõ '$device' — có: $DEVICE_KEYS"); return }
        val id = parseFid(fid)
        if (id == null) { Log.w(TAG, "fid không parse được '$fid' (dùng 0x… hoặc số thập phân)"); return }
        val dev = BydHal.device(fqn, BydHal.systemBypassContext(), BydHal.bypass(app))
        if (dev == null) { Log.i(TAG, "fid device=$device null (off-car / no HAL)"); return }
        val rc = runCatching { "rc=${BydHal.setInt(dev, id, value ?: 0)}" }.getOrElse { BydHal.root(it) }
        Log.i(TAG, "fid device=$device fid=0x${Integer.toHexString(id)} val=${value ?: 0} $rc")
    }

    /** Parse "0x…" / "#…" / "0…"(octal) / thập phân → Int (truncate 32-bit). null nếu không parse được. */
    private fun parseFid(s: String): Int? = runCatching { java.lang.Long.decode(s.trim()).toInt() }.getOrNull()

    companion object {
        const val ACTION = "com.byd.clusternav.vehicletest.HAL_PROBE"
        private const val TAG = "HalProbe"
        private val ARG_KEYS = listOf("a0", "a1", "a2", "a3")
        private const val DEVICE_KEYS = "SETTING|AC|BODYWORK|INSTRUMENT|PM2P5|SPEED|STATISTIC"
    }
}
