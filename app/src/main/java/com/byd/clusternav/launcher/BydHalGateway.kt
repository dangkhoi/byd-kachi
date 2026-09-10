package com.byd.clusternav.launcher

import android.content.Context
import android.media.AudioManager
import com.byd.clusternav.modules.hal.BydHal

/**
 * [HalGateway] THẬT — bọc hạ tầng reflection SẴN CÓ [BydHal] (TÁI DÙNG, KHÔNG mở reflection mới) + local Android
 * (AudioManager). Đúng công thức proven ở [com.byd.clusternav.comfort.SeatComfortApplier] /
 * [com.byd.clusternav.body.BodyworkControl]: `device(FQN, systemBypassContext, bypass(app))` → `callGetter` /
 * `callNamedInt` / `setInt` / `tryGet`.
 *
 * Degrade-safe (R9): off-car → `systemBypassContext()` null + `device()` null (BydHal tự bọc runCatching) ⇒ mọi
 * đường trả null/false ⇒ UI "—". KHÔNG BAO GIỜ ném ra ngoài. KHÔNG gate an toàn.
 *
 * ⚠ **Cast (AutoContainer) KHÔNG wire ở đây** — cluster-cast do `SimpleCastRuntime` sở hữu (ràng buộc: không đụng
 * logic cast). [localSet] cho `AutoContainer` trả false (no-op). ⚠ **car-setting** (`settingGet/settingSet`) trả
 * null (đường setting chưa proven — grab-list §9); off-car null nên vô hại.
 */
class BydHalGateway(context: Context) : HalGateway {

    private val app: Context = context.applicationContext

    /** Resolve device BYDAuto qua reflection proven (system-ctx rồi app-ctx, đều bọc bypass). null nếu off-car. */
    private fun device(fqn: String): Any? =
        runCatching { BydHal.device(fqn, BydHal.systemBypassContext(), BydHal.bypass(app)) }.getOrNull()

    override fun getter(deviceFqn: String, method: String, arg: Int?): String? =
        runCatching { device(deviceFqn)?.let { BydHal.callGetter(it, method, arg) } }.getOrNull()

    override fun namedInt(deviceFqn: String, method: String, args: IntArray): Long? =
        runCatching { device(deviceFqn)?.let { parseRc(BydHal.callNamedInt(it, method, *args)) } }.getOrNull()

    override fun featureGet(deviceFqn: String, id: Int): String? = runCatching {
        val dev = device(deviceFqn) ?: return null
        if (!BydHal.hasSyncGet(dev)) return null
        BydHal.readValue(BydHal.tryGet(dev, id))
    }.getOrNull()

    override fun featureSet(deviceFqn: String, id: Int, value: Int): Long? = runCatching {
        val dev = device(deviceFqn) ?: return null
        (BydHal.setInt(dev, id, value) as? Int)?.toLong()
    }.getOrNull()

    // Car-setting: đường ghi/đọc setting BYD chưa proven trên trim → để null (grab-list §9). Off-car null anyway.
    override fun settingGet(key: String): String? = null
    override fun settingSet(key: String, value: Int): Long? = null

    override fun localGet(target: String, method: String, arg: Int?): String? = runCatching {
        when (target) {
            "AudioManager" -> when (method) {
                "getStreamVolume" -> audio()?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toString()
                else -> null
            }
            else -> null
        }
    }.getOrNull()

    override fun localSet(target: String, method: String, args: IntArray): Boolean = runCatching {
        when (target) {
            "AudioManager" -> when (method) {
                "setStreamVolume" -> {
                    val am = audio() ?: return false
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, args.firstOrNull() ?: 0, 0)
                    true
                }
                else -> false
            }
            // AutoContainer = cast/HUD → do SimpleCastRuntime sở hữu, KHÔNG wire ở đây.
            else -> false
        }
    }.getOrDefault(false)

    private fun audio(): AudioManager? =
        runCatching { app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()

    /** BydHal.callNamedInt trả "rc=<v>" (kể cả rc lỗi) hoặc chuỗi lỗi (không có "rc="). → Long? */
    private fun parseRc(s: String): Long? =
        if (s.startsWith("rc=")) s.removePrefix("rc=").trim().toLongOrNull() else null
}
