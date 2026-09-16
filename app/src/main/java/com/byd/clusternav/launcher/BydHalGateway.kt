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

    /**
     * Ghi named-method + **ghi trộm** câu chữ THẬT vào [HalWriteProbe] cho cầu kiểm thử (spec §9).
     *
     * `callNamedInt` trả `"rc=<v>"` khi gọi được, hoặc chuỗi ngoại lệ ([BydHal.root]) khi ROM thiếu method / HAL
     * chặn — chính chuỗi này bị [parseRc] nuốt (trả null) nên phải chụp TRƯỚC. Off-car (`device()` null) ghi
     * `"off_car"`. Đây là quan sát bị động trên cùng đường ghi, KHÔNG mở đường thứ hai (xem KDoc [HalWriteProbe]).
     */
    override fun namedInt(deviceFqn: String, method: String, args: IntArray): Long? = runCatching {
        val dev = device(deviceFqn) ?: run { HalWriteProbe.record(deviceFqn, method, OFF_CAR); return null }
        val raw = BydHal.callNamedInt(dev, method, *args)
        HalWriteProbe.record(deviceFqn, method, raw)
        parseRc(raw)
    }.getOrNull()

    /**
     * Đọc feature-id qua `get(int[], Class)` 2-arg [ĐO `AbsBYDAutoDevice.java:84`; OpenBYD `CarControlImpl.java:239-240`]
     * — toàn bộ cơ chế (dò method, sentinel `INVAILD_INT`, rút `int=/float=`) nằm ở [BydHal.readFeature] để test
     * off-car; đây chỉ resolve device. null = off-car / device không có get / HAL từ chối / giá trị sentinel.
     */
    override fun featureGet(deviceFqn: String, id: Int): String? = runCatching {
        val dev = device(deviceFqn) ?: return null
        BydHal.readFeature(dev, id)
    }.getOrNull()

    /** Ghi feature-id + ghi trộm kết quả vào [HalWriteProbe] (bắt cả `rc=…` lẫn ngoại lệ "no permission …"). */
    override fun featureSet(deviceFqn: String, id: Int, value: Int): Long? = runCatching {
        val label = "0x%08x".format(id)
        val dev = device(deviceFqn) ?: run { HalWriteProbe.record(deviceFqn, label, OFF_CAR); return null }
        val raw = runCatching { "rc=${BydHal.setInt(dev, id, value)}" }.getOrElse { BydHal.root(it) }
        HalWriteProbe.record(deviceFqn, label, raw)
        parseRc(raw)
    }.getOrNull()

    // Car-setting: đường ghi/đọc setting BYD chưa proven trên trim → để null (grab-list §9). Off-car null anyway.
    override fun settingGet(key: String): String? = null
    override fun settingSet(key: String, value: Int): Long? {
        HalWriteProbe.record("", "setting:$key", SETTING_UNSUPPORTED)
        return null
    }

    override fun localGet(target: String, method: String, arg: Int?): String? = runCatching {
        when (target) {
            "AudioManager" -> when (method) {
                "getStreamVolume" -> audio()?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toString()
                else -> null
            }
            // GPS (gps_lat/lon/altitude/heading): HAL BYD KHÔNG có getter toạ độ (`BYDAutoLocationDevice` chỉ setter —
            // RE 2026-09-15). Đường duy nhất là Android LocationManager, nhưng `DeadReckonRetirementTest` PIN "manifest
            // không xin quyền location nào" — quyết định an toàn sau sự cố suýt ghim GPS toàn xe (CLAUDE.md §3). ⇒ KHÔNG
            // wire ở đây; 4 mục GPS = BLOCKED-BY-DESIGN, mở lại là quyết định của owner (ghi ở spec §9).
            else -> null
        }
    }.getOrNull()

    override fun localSet(target: String, method: String, args: IntArray): Boolean {
        val ok = runCatching {
            when (target) {
                "AudioManager" -> when (method) {
                    "setStreamVolume" -> {
                        val am = audio() ?: return@runCatching false
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, args.firstOrNull() ?: 0, 0)
                        true
                    }
                    else -> false
                }
                // AutoContainer = cast/HUD → do SimpleCastRuntime sở hữu, KHÔNG wire ở đây.
                else -> false
            }
        }.getOrDefault(false)
        HalWriteProbe.record(target, method, if (ok) "rc=0" else OFF_CAR)
        return ok
    }

    /**
     * V3 · R11(a) — tên hằng → số, tra trên framework của chính xe. Uỷ quyền thẳng xuống [BydFeatureIds] (cache +
     * log-một-lần nằm ở đó, cùng một chỗ với phép tra device).
     */
    override fun featureIdByName(constName: String): Int? = BydFeatureIds.idByName(constName)

    /** V3 · R11(b) — id → device chứa nó, theo `BYDAutoDeviceFeaturesMap` của xe. `null` ⇒ chỗ gọi lùi về đoán. */
    override fun deviceForFeature(featureId: Int): String? = BydFeatureIds.deviceFqnForFeature(featureId)

    private fun audio(): AudioManager? =
        runCatching { app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()

    /** BydHal.callNamedInt trả "rc=<v>" (kể cả rc lỗi) hoặc chuỗi lỗi (không có "rc="). → Long? */
    private fun parseRc(s: String): Long? =
        if (s.startsWith("rc=")) s.removePrefix("rc=").trim().toLongOrNull() else null

    private companion object {
        /** [HalWriteProbe] ghi khi `device()` null (emulator / ngoài xe) — cầu kiểm thử đọc thành `hal_line`. */
        const val OFF_CAR = "off_car"

        /** Đường car-setting chưa proven trên trim (grab-list §9). */
        const val SETTING_UNSUPPORTED = "setting_unsupported"
    }
}
